package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The main menu's background (Josiah 2026-11-09 01:00: "a slide show of landmarks with smooth transitions"): every
 * picture in {@code config/zomboidcraft/menu-backgrounds/} (jpg or png, in name order), each held for a while with a
 * slow drift across it, then crossfaded into the next. Screenshots dropped in there join the show the next time the
 * menu opens. Pictures are read and sized off the render thread; at most two are on the graphics card at once.
 */
final class MenuSlides {
    static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/menu-backgrounds");
    private static final float HOLD = 9f, FADE = 1.6f, OVERSCAN = 1.08f;

    private final List<Path> files;
    private final Identifier[] ids = new Identifier[2];
    private int made;
    /** Per graphics slot: its size, and which picture it holds (for the drift direction); null/-1 when empty. */
    private final int[][] sizes = new int[2][];
    private final int[] holds = {-1, -1};
    private int cur, curIndex = -1, wanted = -1, ww, wh;
    private float t;
    private @Nullable CompletableFuture<BufferedImage> reading;
    private final java.util.Set<Integer> bad = new java.util.HashSet<>();

    MenuSlides() {
        List<Path> found = List.of();
        try (var s = Files.list(DIR)) {
            found = s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(p) && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"));
            }).sorted().toList();
        } catch (Exception ignored) {
            // no folder yet: the kit's own picture stands in
        }
        files = found;
    }

    boolean any() {
        return !files.isEmpty();
    }

    /** Draws the show over the whole window; false while no picture is ready yet. */
    boolean draw(GuiGraphicsExtractor g, int ww, int wh, float dt, int tint) {
        if (files.isEmpty()) return false;
        if (ww != this.ww || wh != this.wh) {           // new window size: start again at the right size
            this.ww = ww;
            this.wh = wh;
            sizes[0] = sizes[1] = null;
            holds[0] = holds[1] = -1;
            reading = null;
            wanted = -1;
            curIndex = -1;
        }
        if (curIndex < 0) {                              // the first picture
            int got = fetch(true);
            if (got < 0) return false;
            curIndex = got;
            cur = 0;
            t = 0;
        }
        t += dt;
        float fade = 0;
        if (files.size() > 1 && t > HOLD - FADE - 1.5f) {
            int other = 1 - cur;
            if (holds[other] < 0) fetch(false);              // read the next one ahead (or wait for it)
            if (t > HOLD) {
                if (holds[other] < 0) t = HOLD;          // not ready yet: hold this one a little longer
                else fade = Math.min(1, (t - HOLD) / FADE);
            }
        }
        drift(g, cur, t, 1 - smooth(fade), tint);
        if (fade > 0) drift(g, 1 - cur, t - HOLD, smooth(fade), tint);
        if (fade >= 1) {
            cur = 1 - cur;
            curIndex = holds[cur];
            t -= HOLD;
            holds[1 - cur] = -1;
            sizes[1 - cur] = null;
        }
        return true;
    }

    /**
     * Reads the next picture (after the one on screen, or the first) into the free slot. Returns the picture's index
     * once it's on the graphics card, -2 while it's still being read, -1 if there's none left that can be read.
     */
    private int fetch(boolean first) {
        int slot = first ? 0 : 1 - cur;
        if (wanted < 0) {
            int from = first ? 0 : curIndex + 1, pick = -1;
            for (int i = 0; i < files.size() && pick < 0; i++) {
                int c = (from + i) % files.size();
                if (!bad.contains(c) && (first || c != curIndex)) pick = c;
            }
            if (pick < 0) return -1;
            wanted = pick;
            Path file = files.get(pick);
            int w = Math.round(ww * OVERSCAN), h = Math.round(wh * OVERSCAN);
            reading = CompletableFuture.supplyAsync(() -> read(file, w, h));
        }
        if (reading == null || !reading.isDone()) return -2;
        BufferedImage img = reading.getNow(null);
        int index = wanted;
        reading = null;
        wanted = -1;
        if (img == null) {
            bad.add(index);          // unreadable: the next call tries the one after
            return -2;
        }
        upload(img, slot);
        holds[slot] = index;
        return index;
    }

    /** One picture, drifting slowly from one side of its overscan to the other over its time on screen. */
    private void drift(GuiGraphicsExtractor g, int slot, float time, float alpha, int tint) {
        int[] s = sizes[slot];
        if (s == null || ids[slot] == null || alpha <= 0.004f) return;
        float k = Math.max(0, Math.min(1, time / (HOLD + FADE)));
        boolean right = holds[slot] % 2 == 0;
        float u = (s[0] - ww) * (right ? k : 1 - k), v = (s[1] - wh) * 0.5f;
        int a = Math.round(alpha * 255);
        int colour = (a << 24) | (tint & 0xFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, ids[slot], 0, 0, u, v, ww, wh, s[0], s[1], colour);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private void upload(BufferedImage img, int slot) {
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) out.setPixel(x, y, img.getRGB(x, y) | 0xFF000000);
        }
        // a fresh id each time, the old picture let go a few frames later (a draw queued this frame may still use it)
        Identifier id = ZomboidCraft.id("menu_slide/" + (++made));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Endless Night menu slide", out));
        if (ids[slot] != null) HudKit.retire(ids[slot]);
        ids[slot] = id;
        sizes[slot] = new int[] {img.getWidth(), img.getHeight()};
    }

    /** The picture cropped to fill w x h (centred), resized smoothly. */
    private static @Nullable BufferedImage read(Path file, int w, int h) {
        try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) return null;
            float k = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            int cw = Math.round(w / k), ch = Math.round(h / k);
            int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
            BufferedImage cur = src.getSubimage(Math.max(0, cx), Math.max(0, cy), Math.min(cw, src.getWidth()), Math.min(ch, src.getHeight()));
            while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) cur = scale(cur, cur.getWidth() / 2, cur.getHeight() / 2);
            return scale(cur, w, h);
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read menu background {}", file, e);
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }
}
