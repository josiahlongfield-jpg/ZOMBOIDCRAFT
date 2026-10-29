package com.zomboidcraft.client.hud;

import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * A Harvestman has you: the screen's edge throbs red (faster once it feeds), SEIZED shakes under the crosshair, and how
 * to get loose; each jump you make flashes the prompt.
 */
final class HeldHud {
    private static final Hud.Fade fade = new Hud.Fade();
    private static float beat, flash;
    private static boolean jumpWas;

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        HarvestmanEntity h = player.getVehicle() instanceof HarvestmanEntity e ? e : null;
        boolean feeding = h != null && h.pose() == HarvestmanEntity.FEED;
        float a = fade.update(h != null);
        boolean jump = mc.options.keyJump.isDown();
        if (h != null && jump && !jumpWas) flash = 1;
        jumpWas = jump;
        flash = Math.max(0, flash - Hud.dt * 5);
        if (a <= 0) return;
        // the throb: a beat every 0.8 s held, every 0.45 s as it drinks
        float period = feeding ? .45f : .8f;
        beat = (beat + Hud.dt / period) % 1f;
        float throb = (float) Math.pow(1 - beat, 2.2);
        Hud.sprite(g, Hud.id("blood"), 0, 0, g.guiWidth(), g.guiHeight(), Hud.alpha(0xFFFF3020, a * (.45f + .55f * throb)));
        if (h == null) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 16;
        float shake = ((player.tickCount / 2) % 2 == 0 ? 1 : -1) * (feeding ? 1.2f : .7f);
        String word = feeding ? "Feeding" : "Seized";
        float w = Hud.capsWidth(font, word, 3.5f, 1.6f);
        Hud.caps(g, font, word, cx - w / 2 + shake, y - shake * .6f, Hud.alpha(0xFFE04A32, a), 3.5f, 1.6f);
        String how = "Mash jump to break free";
        float hw = Hud.capsWidth(font, how, 1.2f, .8f);
        int howColour = flash > 0 ? 0xFFFFFFFF : Hud.BONE_DIM;
        Hud.caps(g, font, how, cx - hw / 2, y + 16 - flash * 1.5f, Hud.alpha(howColour, a), 1.2f, .8f);
    }

    private HeldHud() {
    }
}
