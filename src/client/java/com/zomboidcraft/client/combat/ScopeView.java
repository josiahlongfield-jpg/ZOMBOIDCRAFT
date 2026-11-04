package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Looking through a magnified scope: only the lens is zoomed, the world around it stays at its own size with a mild
 * blur (Josiah 2026-11-02/03, option 4 of the lens preview).
 *
 * Shader packs (Iris + Photon) can't draw the world twice in one frame, so the two views take turns: two frames in
 * three are drawn zoomed for the lens, the third at the normal field of view for the world around it. Each finished
 * frame is copied aside after the shader pack is done with it, and the HUD lays the last world view under the last
 * lens view. look.scopeView "mix" is the fallback he picked first: up to 4x the lens is the middle of the normal view
 * blown up, from 6x the whole view is zoomed and blurred outside the lens. "picture" is the old full-screen eyepiece.
 * With a shader pack on, pip turns into mix, and look.scopeShaders picks between two looks (Josiah 2026-11-04, a test
 * of both): "crop" (A) keeps the world live at its own size and the lens is the middle of the finished picture blown
 * up, copied at the HUD after the pack is done; "ring" (B) zooms the whole view and darkens the world round the lens.
 */
public final class ScopeView {
    private static final Identifier RING = ZomboidCraft.id("dynamic/scope_ring");
    private static long frame;
    private static boolean lensFrame;
    private static @Nullable TextureTarget outer;
    private static @Nullable TextureTarget lens;
    private static boolean outerReady;
    private static boolean lensReady;
    private static boolean ringMade;
    /** The copy of the finished picture failed with this shader pack; the dark ring stands in for the session. */
    private static boolean cropFailed;
    /** The game's main picture (it lives in the GameRenderer), copied from for A at the HUD. */
    private static @Nullable RenderTarget mainTarget;

    private enum Mode { PIP, MIX, PICTURE }

    private static Mode mode() {
        String m = ZcConfig.get().look.scopeView;
        if ("picture".equalsIgnoreCase(m)) {
            return Mode.PICTURE;
        }
        // a shader pack (Photon) can't take turns: it blends each frame into the next (its exposure and smoothing), and
        // the kept frames don't show over its picture, so the raw zoomed and normal frames flicker (Josiah 2026-11-03)
        return "mix".equalsIgnoreCase(m) || RailLights.shaderPackOn() ? Mode.MIX : Mode.PIP;
    }

    /** With a shader pack the whole view zooms at every power and the lens is drawn from it 1:1, so nothing rests on the HUD's copy. */
    private static boolean zoomAll(double m) {
        return RailLights.shaderPackOn() ? !cropWithShaders() : m >= 6;
    }

    /** With a shader pack: the live view with the middle blown up in the lens (A), rather than the dark ring (B). */
    private static boolean cropWithShaders() {
        return !cropFailed && !"ring".equalsIgnoreCase(ZcConfig.get().look.scopeShaders);
    }

    /** The scope style key: swaps A and B, keeps the choice, and says which is on. */
    public static String switchStyle() {
        ZcConfig.Look look = ZcConfig.get().look;
        boolean toRing = !"ring".equalsIgnoreCase(look.scopeShaders);
        look.scopeShaders = toRing ? "ring" : "crop";
        cropFailed = false;
        ZcConfig.save();
        String name = toRing ? "Scope B: dark scope ring, whole view zoomed" : "Scope A: live view, zoomed centre in the lens";
        return RailLights.shaderPackOn() ? name : name + " (used with a shader pack on; without one the lens is true picture-in-picture)";
    }

    /** The held scope's magnification while looking through it in first person, or 0. */
    public static double magnification() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson() || !player.isUsingItem()) {
            return 0;
        }
        ItemStack gun = player.getUseItem();
        if (!GunType.isGun(gun) || !Attachments.scoped(gun) || CombatClient.reloading(gun)) {
            return 0;
        }
        double z = Attachments.aimZoom(gun);
        return z > 0 && z < 1 ? 1 / z : 0;
    }

    /** This view takes over the scope (the field of view, the HUD) rather than the old full-screen picture. */
    public static boolean active() {
        return mode() != Mode.PICTURE && magnification() > 1;
    }

    /** At the start of each frame: whose turn it is. */
    public static void beginFrame() {
        frame++;
        if (!active() || mode() != Mode.PIP) {
            outerReady = lensReady = lensFrame = false;
            return;
        }
        // the world around the lens first, then the lens, then two lens frames to every world one
        lensFrame = outerReady && (!lensReady || frame % 3 != 0);
    }

    /** The camera's field of view for this frame, in degrees, given what it would have been. */
    public static float fov(float fov) {
        double m = magnification();
        if (m <= 1) {
            return fov;
        }
        boolean zoomed = switch (mode()) {
            case PIP -> lensFrame;
            case MIX -> zoomAll(m);
            case PICTURE -> false;
        };
        if (!zoomed) {
            return fov;
        }
        // the middle of the picture magnified exactly m times
        double half = Math.toRadians(fov) / 2;
        return (float) Math.toDegrees(2 * Math.atan(Math.tan(half) / m));
    }

    /** After the frame is finished (shader pack included), before the HUD: keep it for the lens or the world view. */
    public static void capture(GameRenderer renderer, RenderTarget main) {
        mainTarget = main;
        if (!active() || RailLights.shaderPackOn()) {
            return;
        }
        try {
            if (mode() == Mode.PIP && !lensFrame) {
                // the world around the lens: blurred once, kept until its next turn
                renderer.processBlurEffect();
                outer = copy(outer, main, "ZomboidCraft scope world");
                outerReady = true;
            } else {
                lens = copy(lens, main, "ZomboidCraft scope lens");
                lensReady = true;
                if (mode() == Mode.MIX) {
                    renderer.processBlurEffect();
                }
            }
        } catch (RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Scope view: couldn't keep the frame ({}), back to the old scope picture", e.toString());
            ZcConfig.get().look.scopeView = "picture";
        }
    }

    private static TextureTarget copy(@Nullable TextureTarget into, RenderTarget main, String label) {
        if (into == null) {
            GpuTexture depth = main.getDepthTexture();
            into = new TextureTarget(label, main.width, main.height, main.getColorTexture().getFormat(),
                    depth == null ? null : depth.getFormat());
        } else if (into.width != main.width || into.height != main.height) {
            into.resize(main.width, main.height);
        }
        into.copyColorFrom(main);
        return into;
    }

    /** The HUD: the world view under the lens, the lens, its reticle and the eyepiece ring. */
    public static void draw(GuiGraphicsExtractor g) {
        double m = magnification();
        Mode mode = mode();
        if (m > 1 && RailLights.shaderPackOn() && cropWithShaders()) {
            // A: the pack has finished the frame by the time the HUD is drawn, so the lens is copied from it here
            try {
                if (mainTarget == null) {
                    throw new IllegalStateException("no main picture yet");
                }
                lens = copy(lens, mainTarget, "ZomboidCraft scope lens");
                lensReady = true;
            } catch (RuntimeException e) {
                ZomboidCraft.LOGGER.warn("Scope view: couldn't copy the shader pack's picture ({}), using the dark ring", e.toString());
                cropFailed = true;
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                            "Scope A didn't work with this shader pack, showing B (the dark ring) instead"));
                }
            }
        }
        if (m > 1 && RailLights.shaderPackOn() && !cropWithShaders()) {
            // B: the whole view is zoomed already, so the lens is the view itself, the world round it darkened like a
            // scope tube (Josiah 2026-11-04)
            int w = g.guiWidth(), h = g.guiHeight();
            double size = lensSize();
            int r = (int) Math.round(size * h / 2), cx = w / 2, cy = h / 2, shade = 0xEA050505;
            g.fill(0, 0, w, Math.max(0, cy - r), shade);
            g.fill(0, Math.min(h, cy + r), w, h, shade);
            for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++) {
                double dy = y + 0.5 - cy;
                int half = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
                g.fill(0, y, cx - half, y + 1, shade);
                g.fill(cx + half, y, w, y + 1, shade);
            }
            reticle(g, cx, cy, r);
            ring(g, cx, cy, r);
            return;
        }
        if (m <= 1 || lens == null || !lensReady) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        boolean flip = ZcConfig.get().look.scopeFlip;
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (mode == Mode.PIP && outer != null && outerReady) {
            g.blit(outer.getColorTextureView(), linear, 0, 0, w, h, 0, 1, flip ? 1 : 0, flip ? 0 : 1);
        }
        g.fill(0, 0, w, h, 0x30000000);     // the world outside a touch darker, the eye on the lens

        double size = lensSize();
        int r = (int) Math.round(size * h / 2), cx = w / 2, cy = h / 2;
        // a magnified view drawn 1:1 from a zoomed frame, or the middle of a normal one blown up
        double crop = mode == Mode.MIX && !zoomAll(m) ? m : 1;
        GpuTextureView view = lens.getColorTextureView();
        int step = Math.max(1, r / 90);
        for (int y = cy - r; y < cy + r; y += step) {
            double dy = y + step / 2.0 - cy;
            int half = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
            if (half <= 0) {
                continue;
            }
            int x0 = cx - half, x1 = cx + half, y1 = Math.min(cy + r, y + step);
            float u0 = (float) ((cx + (x0 - cx) / crop) / w), u1 = (float) ((cx + (x1 - cx) / crop) / w);
            float v0 = (float) ((cy + (y - cy) / crop) / h), v1 = (float) ((cy + (y1 - cy) / crop) / h);
            if (flip) {
                v0 = 1 - v0;
                v1 = 1 - v1;
            }
            lensBlit(g, view, linear, x0, y, x1, y1, u0, u1, v0, v1);
        }
        reticle(g, cx, cy, r);
        ring(g, cx, cy, r);
    }

    /**
     * The lens picture is drawn without blending: a shader pack can leave the picture's alpha at nothing, and a
     * blended copy of it would never show (what hid the lens under Photon, 2026-11-03).
     */
    private static void lensBlit(GuiGraphicsExtractor g, GpuTextureView view, GpuSampler sampler, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        ((com.zomboidcraft.mixin.client.GuiGraphicsBlitInvoker) g).zomboidcraft$innerBlit(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
                view, sampler, x0, y0, x1, y1, u0, u1, v0, v1, -1);
    }

    /** The lens's share of the screen's height: a sight of Josiah's pack its eyepiece's real size, else the setting. */
    private static double lensSize() {
        double own = LocalAttachments.lens;
        return Math.max(0.15, Math.min(0.95, own > 0 ? own : ZcConfig.get().look.scopeLens));
    }

    /** The scope's own reticle (a sight of Josiah's pack), or a plain duplex: thick posts from the edge, a fine cross. */
    private static void reticle(GuiGraphicsExtractor g, int cx, int cy, int r) {
        Identifier own = LocalAttachments.reticle;
        if (own != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, own, cx - r, cy - r, 0, 0, r * 2, r * 2, r * 2, r * 2);
            return;
        }
        int ink = 0xF0080808, fine = Math.max(1, r / 200), thick = Math.max(2, r / 45), in = r * 2 / 5;
        g.fill(cx - in, cy - fine / 2, cx + in, cy - fine / 2 + fine, ink);
        g.fill(cx - fine / 2, cy - in, cx - fine / 2 + fine, cy + in, ink);
        g.fill(cx - r, cy - thick / 2, cx - in, cy - thick / 2 + thick, ink);
        g.fill(cx + in, cy - thick / 2, cx + r, cy - thick / 2 + thick, ink);
        g.fill(cx - thick / 2, cy + in, cx - thick / 2 + thick, cy + r, ink);
        g.fill(cx - thick / 2, cy - r, cx - thick / 2 + thick, cy - in, ink);
    }

    /** The eyepiece's dark rim and the shadow just inside it, over the stair-stepped edge of the lens. */
    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r) {
        if (!ringMade) {
            ringMade = true;
            int n = 512;
            NativeImage img = new NativeImage(n, n, false);
            double c = (n - 1) / 2.0, lensR = n / 2.0 / 1.16;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double d = Math.hypot(x - c, y - c);
                    int argb;
                    if (d > n / 2.0) {
                        argb = 0;
                    } else if (d > lensR * 1.06) {
                        int shade = d > n / 2.0 - 3 ? 0x05 : 0x1D;
                        argb = 0xFF000000 | shade << 16 | (shade + 2) << 8 | (shade + 4);
                    } else if (d > lensR * 0.985) {
                        argb = 0xFF34373B;
                    } else {
                        // the eye-relief shadow: the lens darkens toward its rim
                        double t = Math.max(0, (d / lensR - 0.78) / 0.205);
                        argb = (int) (Math.min(1, t) * 0xB0) << 24;
                    }
                    img.setPixel(x, y, argb);
                }
            }
            Minecraft.getInstance().getTextureManager().register(RING, new DynamicTexture(() -> "ZomboidCraft scope ring", img));
        }
        int outerR = (int) Math.round(r * 1.16);
        g.blit(RenderPipelines.GUI_TEXTURED, RING, cx - outerR, cy - outerR, 0, 0, outerR * 2, outerR * 2, outerR * 2, outerR * 2);
    }

    private ScopeView() {
    }
}
