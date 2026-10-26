package com.zomboidcraft.client.gear;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.combat.CombatNet;
import com.zomboidcraft.gear.Headgear;
import com.zomboidcraft.gear.NightVisionItem;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * The view through night vision goggles flipped down. The look (phosphor colour, grain, halos round lights, the round
 * edge of the tube) is a screen effect, one per generation in post_effect/nvg_*.json, drawn after the world and the
 * hands and under the HUD. The settings it can't get from its file (the tube's size, a dying battery's flicker) come
 * from a tiny texture we keep up to date ({@link #PARAMS}).
 *
 * If the effect can't be drawn (a shader pack that hides it, or nightVision.tubeEffect off), a plain tint and the
 * tube's edge are drawn over the screen instead. A dead battery shows nothing through the tube.
 */
public final class NightVisionClient {
    public static final KeyMapping FLIP = new KeyMapping("key.zomboidcraft.nvg", InputConstants.KEY_N, CombatClient.CATEGORY);

    /** Where the screen effect finds its settings: the effect pass adds "textures/effect/" and ".png". */
    private static final Identifier PARAMS = ZomboidCraft.id("textures/effect/nvg_params.png");
    private static final Identifier TUBE = ZomboidCraft.id("textures/gui/nvg_tube.png");
    private static final Identifier TUBE_MONO = ZomboidCraft.id("textures/gui/nvg_tube_mono.png");

    private static @Nullable DynamicTexture params;
    private static int paramsWritten;
    /** Frames the effect was asked for but the game didn't draw it: past a moment, the tint takes over. */
    private static int missedFrames;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(FLIP);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (FLIP.consumeClick()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.NVG));
                }
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ZomboidCraft.id("night_vision"), NightVisionClient::hud);
    }

    // ---- what the wearer sees ----

    /** The goggles over the local player's eyes, flipped down, or null. */
    private static Headgear.@Nullable Mounted down(@Nullable LocalPlayer player) {
        if (player == null || player.isSpectator()) {
            return null;
        }
        Headgear.Mounted m = Headgear.mounted(player.getItemBySlot(EquipmentSlot.HEAD));
        return m != null && !m.up() ? m : null;
    }

    private static NightVisionItem.@Nullable Generation generation(Headgear.Mounted m) {
        return m.goggles().getItem() instanceof NightVisionItem nv ? nv.generation() : null;
    }

    /** Whether the battery is in its last stretch, when the tube flickers. */
    private static boolean dying(Headgear.Mounted m) {
        return ZcConfig.get().nightVision.batteries && Headgear.chargeFraction(m.goggles()) < 0.08f;
    }

    /** The screen effect to draw this frame, or null. */
    private static @Nullable Identifier effect() {
        Minecraft mc = Minecraft.getInstance();
        Headgear.Mounted m = down(mc.player);
        if (m == null || params == null || !ZcConfig.get().nightVision.tubeEffect || !mc.options.getCameraType().isFirstPerson()
                || !Headgear.hasPower(m.goggles())) {
            return null;
        }
        NightVisionItem.Generation gen = generation(m);
        return gen == null ? null : ZomboidCraft.id(gen.key);
    }

    /** Called as the game picks this frame's screen effects: adds the tube's. */
    public static List<Identifier> withEffect(List<Identifier> requested) {
        Identifier ours = effect();
        if (ours == null || requested.contains(ours)) {
            return requested;
        }
        List<Identifier> out = new ArrayList<>(requested);
        out.add(ours);
        return out;
    }

    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (params == null) {
            params = new DynamicTexture(() -> "ZomboidCraft night vision", 4, 4, true);
            mc.getTextureManager().register(PARAMS, params);
            paramsWritten = 0;
            writeParams(ZcConfig.get().nightVision, false);
        }
        Headgear.Mounted m = down(mc.player);
        if (m == null || !mc.options.getCameraType().isFirstPerson()) {
            missedFrames = 0;
            return;
        }
        ZcConfig.NightVision cfg = ZcConfig.get().nightVision;
        writeParams(cfg, dying(m));
        int w = g.guiWidth(), h = g.guiHeight();
        NightVisionItem.Generation gen = generation(m);
        boolean mono = gen == NightVisionItem.Generation.GEN1;
        if (!Headgear.hasPower(m.goggles())) {
            // nothing comes through a dead tube; the other eye still sees round a monocular
            g.fill(0, 0, w, h, mono ? 0x99000000 : 0xF2000000);
            return;
        }
        Identifier ours = effect();
        if (ours != null) {
            missedFrames = mc.gameRenderer.getAppliedPostEffects().contains(ours) ? 0 : missedFrames + 1;
            if (missedFrames < 20) {
                return;
            }
        }
        tint(g, w, h, gen, mono, m);
    }

    /** The plain look: the phosphor's colour washed over the screen and the tube's round edge. */
    private static void tint(GuiGraphicsExtractor g, int w, int h, NightVisionItem.@Nullable Generation gen, boolean mono,
                             Headgear.Mounted m) {
        float flicker = 1f;
        if (dying(m)) {
            double t = System.currentTimeMillis() / 1000.0;
            double k = Math.sin(t * 17) + Math.sin(t * 5.3) * 0.8 + Math.sin(t * 41) * 0.4;
            flicker = k < -1.0 ? 0.3f : 0.8f;
        }
        int rgb = gen == NightVisionItem.Generation.WHITE ? 0xC8D2E6 : gen == NightVisionItem.Generation.GEN1 ? 0x7CFF52 : 0x5CFF84;
        int alpha = Math.round((gen == NightVisionItem.Generation.WHITE ? 0.30f : 0.42f) * 255 / flicker);
        g.fill(0, 0, w, h, Math.min(255, alpha) << 24 | rgb);
        double size = Math.max(0.5, Math.min(1.2, ZcConfig.get().nightVision.tubeSize)) * (mono ? 0.87 : 1.0);
        int d = (int) Math.round(h * size);
        int x = (w - d) / 2, y = (h - d) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, mono ? TUBE_MONO : TUBE, x, y, 0, 0, d, d, d, d);
        int outside = mono ? 0xE0000000 : 0xFF000000;
        g.fill(0, 0, w, Math.max(0, y), outside);
        g.fill(0, y + d, w, h, outside);
        g.fill(0, Math.max(0, y), Math.max(0, x), Math.min(h, y + d), outside);
        g.fill(x + d, Math.max(0, y), w, Math.min(h, y + d), outside);
    }

    /** Keeps the effect's settings texture up to date: the tube's size, a dying battery, how bright the tube is. */
    private static void writeParams(ZcConfig.NightVision cfg, boolean dying) {
        if (params == null) {
            return;
        }
        int tube = (int) Math.round(Math.max(0.5, Math.min(1.2, cfg.tubeSize)) / 1.5 * 255);
        int bright = (int) Math.round(Math.max(0.2, Math.min(2.0, cfg.brightness)) / 2.0 * 255);
        int argb = 0xFF000000 | tube << 16 | (dying ? 0x40 : 0xFF) << 8 | bright;
        if (argb == paramsWritten) {
            return;
        }
        NativeImage image = params.getPixels();
        if (image == null) {
            return;
        }
        for (int py = 0; py < 4; py++) {
            for (int px = 0; px < 4; px++) {
                image.setPixel(px, py, argb);
            }
        }
        params.upload();
        paramsWritten = argb;
    }

    private NightVisionClient() {
    }
}
