package com.zomboidcraft.client.survivor;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
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

    /** Pixels kept made (about 256 MB): bodies are a megapixel, garments a sixteenth of that. */
    /** Pixels of made textures kept (a horde of Zomboid zombies near and mid range, their corpses, the survivors). */
    // 128M pixels (512 MB): a 600-zombie horde's shared looks (8 an outfit, at 256 far and 512 for the nearest) need
    // about 110M; at 64M they were thrown away and made again nonstop (551 in 30 s, Josiah's profile 2026-11-02).
    // The card has room for it since the item and block sheets were cut
    private static final long BUDGET = 128L << 20;

    public static int madeCount() {
        return made;
    }

    /** For /zc profile: how many textures have been made so far, and how many MB are kept on the card now. */
    public static String stats() {
        return String.format("Zomboid body textures: %d made since start, %d kept, %d MB on the card (cap %d MB)", made, MADE.size(),
                kept * 4 >> 20, BUDGET * 4 >> 20);
    }
    private static long kept;
    private static final Map<String, Integer> SIZE = new java.util.HashMap<>();
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();
    private static int made;
    private static final Map<String, CompletableFuture<NativeImage>> MAKING = new LinkedHashMap<>();
    private static final Map<String, Identifier> MADE = new LinkedHashMap<>(16, 0.75f, true);

    /** Laid over what's under it by its alpha. */
    public static final int OVER = 0;
    /** A mask: clears what's under it where it's white (skin a garment hides). */
    public static final int CUT = 1;
    /** One of our camo patterns (key: its name), laid over what's under it in that layer's own light and shade. */
    public static final int CAMO = 2;
    /** Remembers the picture as made so far (its key is unused): what a REVEAL layer brings back. */
    public static final int KEEP = 3;
    /** Where the mask is white, the picture goes back to how it was at the KEEP layer (a torn hole showing skin). */
    public static final int REVEAL = 4;

    /** One layer: a texture key in the pack, a tint (0xRRGGBB, or -1 for none), and how it's laid on. */
    /**
     * strength: 0-255, how much of an OVER layer goes on (a zombie's blood and grime, thinner or thicker), or for CUT and
     * REVEAL how far a graded mask opens (a zombie's tears, small or wide). 255 is the whole layer, as before.
     */
    public record Layer(String key, int tint, int mode, int strength) {
        public Layer(String key, int tint) {
            this(key, tint, OVER);
        }

        public Layer(String key, int tint, int mode) {
            this(key, tint, mode, 255);
        }

        String tag() {
            return mode + key + '#' + Integer.toHexString(tint) + (strength < 255 ? "*" + strength : "");
        }
    }

    /** The body's texture for these layers (the first opaque), or null while it's being made. Render thread. */
    public static Identifier body(SurvivorPack pack, List<Layer> layers) {
        StringBuilder sb = new StringBuilder(pack.sex);
        for (Layer l : layers) sb.append('|').append(l.tag());
        return get(pack, sb.toString(), layers);
    }

    /**
     * As {@link #body(SurvivorPack, List)}, made at size by size whatever the first layer's size (the many zombies' looks
     * at 512 rather than their garments' 1024, so a crowd of them fits in the textures kept).
     */
    public static Identifier body(SurvivorPack pack, List<Layer> layers, int size) {
        return get(pack, sized(pack, layers, size), layers, size);
    }

    /** As {@link #body(SurvivorPack, List, int)} if it's been made already, else null (and nothing is started). */
    public static Identifier peek(SurvivorPack pack, List<Layer> layers, int size) {
        return MADE.get(sized(pack, layers, size));
    }

    private static String sized(SurvivorPack pack, List<Layer> layers, int size) {
        StringBuilder sb = new StringBuilder(pack.sex).append('@').append(size);
        for (Layer l : layers) sb.append('|').append(l.tag());
        return sb.toString();
    }

    /** A garment's texture: tinted, and in a camo pattern if it has one ("" for none). */
    public static Identifier cloth(SurvivorPack pack, String key, int tint, String camo) {
        List<Layer> layers = camo.isEmpty() ? List.of(new Layer(key, tint)) : List.of(new Layer(key, tint), new Layer(camo, -1, CAMO));
        return body(pack, layers);
    }

    /** A hair or beard texture tinted to the hair colour. */
    public static Identifier hair(SurvivorPack pack, String key, String colour) {
        int tint = rgb(colour);
        return get(pack, pack.sex + "|" + key + "#" + Integer.toHexString(tint), List.of(new Layer(key, tint)));
    }

    private static Identifier get(SurvivorPack pack, String name, List<Layer> layers) {
        return get(pack, name, layers, 0);
    }

    private static Identifier get(SurvivorPack pack, String name, List<Layer> layers, int size) {
        Identifier id = MADE.get(name);
        if (id != null || FAILED.contains(name)) return id;
        CompletableFuture<NativeImage> f = MAKING.computeIfAbsent(name, n -> {
            // the bytes are read here, on the render thread; the work happens on a worker
            List<byte[]> data = new ArrayList<>();
            for (Layer l : layers) data.add(l.mode() == CAMO ? camo(l.key()) : pack.skin.raw(l.key()));
            return CompletableFuture.supplyAsync(() -> compose(data, layers, size));
        });
        if (!f.isDone()) return null;
        MAKING.remove(name);
        NativeImage image = f.getNow(null);
        if (image == null) {
            FAILED.add(name);
            return null;
        }
        id = ZomboidCraft.id("dynamic/survivor/" + Integer.toHexString(name.hashCode()) + "_" + made++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft survivor " + name, image));
        MADE.put(name, id);
        int px = image.getWidth() * image.getHeight();
        SIZE.put(name, px);
        kept += px;
        while (kept > BUDGET && MADE.size() > 1) {
            Map.Entry<String, Identifier> old = MADE.entrySet().iterator().next();
            MADE.remove(old.getKey());
            Integer was = SIZE.remove(old.getKey());
            kept -= was == null ? 0 : was;
            Minecraft.getInstance().getTextureManager().release(old.getValue());
        }
        return id;
    }

    /** Layers laid over the first, each scaled to its size; a tint multiplies a layer's colour. */
    private static NativeImage compose(List<byte[]> data, List<Layer> layers, int size) {
        try {
            int[] base = null, laid = null;   // laid: the alpha of the last layer laid on, which a camo layer paints
            int[] kept = null;
            int w = 0, h = 0;
            for (int i = 0; i < data.size(); i++) {
                if (layers.get(i).mode() == KEEP) {
                    if (base != null) kept = base.clone();
                    continue;
                }
                byte[] bytes = data.get(i);
                if (bytes == null) continue;
                try (NativeImage img = com.zomboidcraft.client.npc.NpcSkin.decode(bytes)) {
                    int tint = layers.get(i).tint();
                    if (base == null) {
                        int sw = img.getWidth(), sh = img.getHeight();
                        w = size > 0 ? size : sw;
                        h = size > 0 ? size * sh / sw : sh;
                        base = new int[w * h];
                        laid = new int[w * h];
                        boolean same = sw == w && sh == h;
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                int src = same ? img.getPixel(x, y) : sample(img, (x + 0.5f) * sw / w - 0.5f, (y + 0.5f) * sh / h - 0.5f);
                                base[y * w + x] = tinted(src, tint);
                                laid[y * w + x] = base[y * w + x] >>> 24;
                            }
                        }
                    } else if (layers.get(i).mode() == CUT) {
                        cut(base, w, h, img, layers.get(i).strength());
                    } else if (layers.get(i).mode() == REVEAL) {
                        if (kept != null) reveal(base, kept, w, h, img, layers.get(i).strength());
                    } else if (layers.get(i).mode() == CAMO) {
                        camo(base, laid, w, h, img);
                    } else {
                        over(base, laid, w, h, img, tint, layers.get(i).strength());
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
    private static void over(int[] base, int[] laid, int w, int h, NativeImage img, int tint, int strength) {
        int sw = img.getWidth(), sh = img.getHeight();
        boolean same = sw == w && sh == h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int src = same ? img.getPixel(x, y) : sample(img, (x + 0.5f) * sw / w - 0.5f, (y + 0.5f) * sh / h - 0.5f);
                int a = (src >>> 24) * strength / 255;
                laid[y * w + x] = a;
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

    private static final Map<String, byte[]> CAMOS = new java.util.HashMap<>();

    /** A camo pattern's picture (assets/zomboidcraft/textures/survivor/camo, tools/pz/camo_tiles.py). */
    private static byte[] camo(String name) {
        return CAMOS.computeIfAbsent(name, n -> {
            try (java.io.InputStream in = Minecraft.getInstance().getResourceManager()
                    .open(ZomboidCraft.id("textures/survivor/camo/" + n + ".png"))) {
                return in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** Puts back what was kept where the mask is white. */
    private static void reveal(int[] base, int[] kept, int w, int h, NativeImage mask, int strength) {
        int sw = mask.getWidth(), sh = mask.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int m = mask.getPixel(Math.min(sw - 1, x * sw / w), Math.min(sh - 1, y * sh / h));
                if (Math.min(m >>> 24, m & 255) > 382 - strength) base[y * w + x] = kept[y * w + x];
            }
        }
    }

    /**
     * Clears the base where the mask is white. A weaker cut needs whiter: a grey-graded mask (a zombie's tears) opens
     * small at low strength and wider at full; a plain black-and-white one is the same at any.
     */
    private static void cut(int[] base, int w, int h, NativeImage mask, int strength) {
        int sw = mask.getWidth(), sh = mask.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int m = mask.getPixel(Math.min(sw - 1, x * sw / w), Math.min(sh - 1, y * sh / h));
                // a grey mask reads its red; one with alpha reads that
                int v = Math.min(m >>> 24, m & 255);
                if (v > 382 - strength) base[y * w + x] &= 0x00FFFFFF;
            }
        }
    }

    /**
     * The pattern in place of the base's colour, kept to the base's light and shade: its brightness well smoothed (so a
     * pattern Zomboid painted in doesn't show through) against its average, which keeps the broad shading.
     */
    private static void camo(int[] base, int[] laid, int w, int h, NativeImage pattern) {
        float[] lum = new float[w * h];
        double sum = 0;
        int n = 0;
        for (int i = 0; i < w * h; i++) {
            int c = base[i];
            lum[i] = ((c >> 16 & 255) * 0.299f + (c >> 8 & 255) * 0.587f + (c & 255) * 0.114f) / 255f;
            if (laid[i] > 127) {
                sum += lum[i];
                n++;
            }
        }
        float mean = n == 0 ? 0.5f : (float) (sum / n);
        int r = Math.max(2, w / 24);
        float[] soft = blur(blur(lum, w, h, r, true), w, h, r, false);
        int pw = pattern.getWidth(), ph = pattern.getHeight();
        // the pattern three times across a garment's texture (six across the body's, which covers all of her): about
        // our garments' blotch size on Zomboid's
        int rep = w >= 512 ? 6 : 3;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x, d = base[i], a = laid[i];
                if (a == 0) continue;
                float k = Math.max(0.55f, Math.min(1.25f, soft[i] / Math.max(0.05f, mean)));
                int p = pattern.getPixel(x * pw * rep / w % pw, y * ph * rep / h % ph);
                int pr = Math.min(255, (int) ((p >> 16 & 255) * k)), pg = Math.min(255, (int) ((p >> 8 & 255) * k)),
                        pb = Math.min(255, (int) ((p & 255) * k));
                pr = (pr * a + (d >> 16 & 255) * (255 - a)) / 255;
                pg = (pg * a + (d >> 8 & 255) * (255 - a)) / 255;
                pb = (pb * a + (d & 255) * (255 - a)) / 255;
                base[i] = d & 0xFF000000 | pr << 16 | pg << 8 | pb;
            }
        }
    }

    private static float[] blur(float[] in, int w, int h, int r, boolean across) {
        float[] out = new float[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0;
                int c = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = across ? x + k : x, yy = across ? y : y + k;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    s += in[yy * w + xx];
                    c++;
                }
                out[y * w + x] = s / c;
            }
        }
        return out;
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
