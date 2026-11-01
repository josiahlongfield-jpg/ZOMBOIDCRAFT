package com.zomboidcraft.client.survivor;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The textures a survivor is drawn with, made from the pack's pieces the way Zomboid layers them: the skin (pack, tone,
 * build, hairy or not), then the face, body hair and stubble (tinted to the hair colour), face paint, eye make-up and
 * lipstick, and last the black bra and briefs (women) or Tomb's grey briefs (men), so nothing ever covers them. Hair and
 * beards are Zomboid's white hair textures tinted the same way. Each is made on a worker thread the first time it's
 * needed and kept (the last few dozen) for everyone who looks the same.
 */
public final class SurvivorTextures {
    /** Grooming's colours, as Zomboid-like hair shades. */
    public static int rgb(String colour) {
        return switch (colour == null ? "" : colour) {
            case "black" -> 0x1C1A19;
            case "brown" -> 0x5A3D2A;
            case "auburn" -> 0x7A3420;
            case "ginger" -> 0xB0582C;
            case "blonde" -> 0xD6B77A;
            case "platinum" -> 0xE6DCC3;
            case "grey" -> 0x8C8A86;
            case "white" -> 0xDDDAD4;
            default -> 0x3B2A20;   // dark brown
        };
    }

    private static final int KEEP = 40;
    private static int made;
    private static final Map<String, CompletableFuture<NativeImage>> MAKING = new LinkedHashMap<>();
    private static final Map<String, Identifier> MADE = new LinkedHashMap<>(16, 0.75f, true);

    /** One layer: a texture key in the pack, and a tint (0xRRGGBB, or -1 for none). */
    public record Layer(String key, int tint) {
    }

    /** The body's texture for these layers (the first opaque), or null while it's being made. Render thread. */
    public static Identifier body(SurvivorPack pack, List<Layer> layers) {
        StringBuilder sb = new StringBuilder(pack.sex);
        for (Layer l : layers) sb.append('|').append(l.key()).append('#').append(Integer.toHexString(l.tint()));
        return get(pack, sb.toString(), layers);
    }

    /** A hair or beard texture tinted to the hair colour. */
    public static Identifier hair(SurvivorPack pack, String key, String colour) {
        int tint = rgb(colour);
        return get(pack, pack.sex + "|" + key + "#" + Integer.toHexString(tint), List.of(new Layer(key, tint)));
    }

    private static Identifier get(SurvivorPack pack, String name, List<Layer> layers) {
        Identifier id = MADE.get(name);
        if (id != null) return id;
        CompletableFuture<NativeImage> f = MAKING.computeIfAbsent(name, n -> {
            // the bytes are read here, on the render thread; the work happens on a worker
            List<byte[]> data = new ArrayList<>();
            for (Layer l : layers) data.add(pack.skin.raw(l.key()));
            return CompletableFuture.supplyAsync(() -> compose(data, layers));
        });
        if (!f.isDone()) return null;
        MAKING.remove(name);
        NativeImage image = f.getNow(null);
        if (image == null) return null;
        id = ZomboidCraft.id("dynamic/survivor/" + Integer.toHexString(name.hashCode()) + "_" + made++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft survivor " + name, image));
        MADE.put(name, id);
        while (MADE.size() > KEEP) {
            Map.Entry<String, Identifier> old = MADE.entrySet().iterator().next();
            MADE.remove(old.getKey());
            Minecraft.getInstance().getTextureManager().release(old.getValue());
        }
        return id;
    }

    /** Layers laid over the first, each scaled to its size; a tint multiplies a layer's colour. */
    private static NativeImage compose(List<byte[]> data, List<Layer> layers) {
        try {
            int[] base = null;
            int w = 0, h = 0;
            for (int i = 0; i < data.size(); i++) {
                byte[] bytes = data.get(i);
                if (bytes == null) continue;
                try (NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes))) {
                    int tint = layers.get(i).tint();
                    if (base == null) {
                        w = img.getWidth();
                        h = img.getHeight();
                        base = new int[w * h];
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) base[y * w + x] = tinted(img.getPixel(x, y), tint);
                        }
                    } else {
                        over(base, w, h, img, tint);
                    }
                }
            }
            if (base == null) return null;
            NativeImage out = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) out.setPixel(x, y, base[y * w + x]);
            }
            return out;
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.error("Couldn't make a survivor texture", e);
            return null;
        }
    }

    private static int tinted(int argb, int tint) {
        if (tint < 0) return argb;
        int r = (argb >> 16 & 255) * (tint >> 16 & 255) / 255, g = (argb >> 8 & 255) * (tint >> 8 & 255) / 255,
                b = (argb & 255) * (tint & 255) / 255;
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The layer sampled (bilinear) at the base's size and laid over it by its alpha. */
    private static void over(int[] base, int w, int h, NativeImage img, int tint) {
        int sw = img.getWidth(), sh = img.getHeight();
        boolean same = sw == w && sh == h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int src = same ? img.getPixel(x, y) : sample(img, (x + 0.5f) * sw / w - 0.5f, (y + 0.5f) * sh / h - 0.5f);
                int a = src >>> 24;
                if (a == 0) continue;
                src = tinted(src, tint);
                int d = base[y * w + x];
                int r = ((src >> 16 & 255) * a + (d >> 16 & 255) * (255 - a)) / 255;
                int g = ((src >> 8 & 255) * a + (d >> 8 & 255) * (255 - a)) / 255;
                int b = ((src & 255) * a + (d & 255) * (255 - a)) / 255;
                int da = d >>> 24, oa = a + da * (255 - a) / 255;
                base[y * w + x] = oa << 24 | r << 16 | g << 8 | b;
            }
        }
    }

    private static int sample(NativeImage img, float fx, float fy) {
        int sw = img.getWidth(), sh = img.getHeight();
        int x0 = Math.max(0, Math.min(sw - 1, (int) Math.floor(fx))), y0 = Math.max(0, Math.min(sh - 1, (int) Math.floor(fy)));
        int x1 = Math.min(sw - 1, x0 + 1), y1 = Math.min(sh - 1, y0 + 1);
        float tx = Math.max(0, Math.min(1, fx - x0)), ty = Math.max(0, Math.min(1, fy - y0));
        int c00 = img.getPixel(x0, y0), c10 = img.getPixel(x1, y0), c01 = img.getPixel(x0, y1), c11 = img.getPixel(x1, y1);
        int out = 0;
        for (int s = 0; s < 32; s += 8) {
            float top = (c00 >>> s & 255) * (1 - tx) + (c10 >>> s & 255) * tx;
            float bottom = (c01 >>> s & 255) * (1 - tx) + (c11 >>> s & 255) * tx;
            out |= Math.round(top * (1 - ty) + bottom * ty) << s;
        }
        return out;
    }

    private SurvivorTextures() {
    }
}
