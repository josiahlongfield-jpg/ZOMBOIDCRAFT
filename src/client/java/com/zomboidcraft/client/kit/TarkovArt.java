package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Tarkov's own inventory art, read from {@code config/zomboidcraft/tarkov-ui/<name>.png} on this computer and never
 * shipped (Josiah 2026-11-07: copy Tarkov's inventory, with its art from his files). Names are short ones: the slot
 * silhouettes (headwear, earpiece, face, eyes, armband, armour, rig, backpack, holster, scabbard, special), cell,
 * slotfill, hslot, hpanel, bg and so on. Anything missing draws as a plain dark stand-in.
 */
final class TarkovArt {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/tarkov-ui");
    private static final Map<String, Identifier> LOADED = new HashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();
    private static final Map<String, Boolean> TRIED = new HashMap<>();

    private TarkovArt() {
    }

    /** Whether that piece of art is on this computer. */
    static boolean has(String name) {
        return texture(name) != null;
    }

    /** Draws the whole picture stretched over the box; false when it isn't there. */
    static boolean draw(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int colour) {
        Identifier id = texture(name);
        if (id == null || w <= 0 || h <= 0) return false;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, w, h, colour);
        return true;
    }

    static boolean draw(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        return draw(g, name, x, y, w, h, 0xFFFFFFFF);
    }

    /** Draws it fitted inside the box, keeping its shape, centred. */
    static boolean fit(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int colour) {
        Identifier id = texture(name);
        if (id == null) return false;
        int[] s = SIZES.get(name);
        float k = Math.min(w / (float) s[0], h / (float) s[1]);
        int dw = Math.max(1, Math.round(s[0] * k)), dh = Math.max(1, Math.round(s[1] * k));
        return draw(g, name, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, colour);
    }

    /** Tiles it at {@code tile} pixels a repeat over the box (Tarkov's grid cell). */
    static boolean tile(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int tile) {
        Identifier id = texture(name);
        if (id == null || tile <= 0) return false;
        for (int ty = 0; ty < h; ty += tile) {
            for (int tx = 0; tx < w; tx += tile) {
                int cw = Math.min(tile, w - tx), ch = Math.min(tile, h - ty);
                g.blit(RenderPipelines.GUI_TEXTURED, id, x + tx, y + ty, 0, 0, cw, ch, tile, tile, 0xFFFFFFFF);
            }
        }
        return true;
    }

    /**
     * A header strip that keeps its slanted right end: the left part stretched, the last {@code tail} pixels of the
     * picture (scaled to the strip's height) drawn as they are.
     */
    static boolean strip(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        Identifier id = texture(name);
        if (id == null) return false;
        int[] s = SIZES.get(name);
        float k = h / (float) s[1];
        int tailSrc = Math.max(1, s[0] / 5), tail = Math.max(1, Math.round(tailSrc * k));
        int body = Math.max(0, w - tail);
        // body: the picture's left part stretched; tail: its last fifth
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, body, h, Math.round(body * s[0] / (float) (s[0] - tailSrc)), h, 0xFFFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, id, x + body, y, (s[0] - tailSrc) * k, 0, tail, h, Math.round(s[0] * k), h, 0xFFFFFFFF);
        return true;
    }

    private static @Nullable Identifier texture(String name) {
        if (TRIED.containsKey(name)) return LOADED.get(name);
        TRIED.put(name, true);
        Path path = DIR.resolve(name + ".png");
        if (!Files.isRegularFile(path)) return null;
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) return null;
            NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    out.setPixel(x, y, img.getRGB(x, y));
                }
            }
            Identifier id = ZomboidCraft.id("tarkov_ui/" + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft Tarkov UI " + name, out));
            LOADED.put(name, id);
            SIZES.put(name, new int[]{img.getWidth(), img.getHeight()});
            return id;
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read Tarkov UI art {}", path, e);
            return null;
        }
    }
}
