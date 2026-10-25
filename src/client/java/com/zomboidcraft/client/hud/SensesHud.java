package com.zomboidcraft.client.hud;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.Sight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Two short meters beside your figure: how far your last noise carried (the server says, so rain and wind count),
 * and how far a zombie could see you now, from the light where you stand and whether you're crouched. They come up
 * when you move, crouch or make a noise, and fade when you stand still.
 */
final class SensesHud {
    /** Earshot in blocks at which each of the eight noise segments lights. */
    private static final double[] NOISE_STEPS = {0.5, 3, 6, 10, 15, 25, 45, 70};
    private static final int SEGS = 8;

    private static final Hud.Fade fade = new Hud.Fade();
    private static int heardId;
    private static double heard;
    private static long heardAt;

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Noise.Heard h = player.getAttached(Noise.HEARD);
        long now = System.currentTimeMillis();
        if (h != null && h.id() != heardId) {
            heardId = h.id();
            heard = h.tenths() / 10.0;
            heardAt = now;
        }
        // a noise hangs for a moment, then dies away; footsteps keep it topped up
        float age = (now - heardAt) / 1000f;
        double noise = age < 0.7f ? heard : heard * Math.max(0, 1 - (age - 0.7f) / 1.3f);
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.002;
        float a = fade.update(Hud.peek || player.isCrouching() || moving || noise > 0.5);
        if (a <= 0) return;

        ZcConfig.Senses senses = ZcConfig.get().senses;
        double seen = Sight.range(player, senses);
        Font font = mc.font;
        int x = 66, y = g.guiHeight() - 40;
        int lit = 0;
        for (double step : NOISE_STEPS) if (noise >= step) lit++;
        String word = noise < 0.5 ? "Silent" : (player.isSprinting() ? "Running" : noise > 30 ? "Gunshot" : noise <= 6 ? "Footsteps"
                : noise <= 13 ? "Noisy" : "Loud") + " · " + Math.round(noise) + " blocks";
        int noiseColour = lit >= 7 ? Hud.RED : lit >= 5 ? Hud.AMBER : Hud.BONE;
        row(g, font, "Noise", lit, word, noiseColour, x, y, a, true);

        int seenLit = (int) Math.ceil(SEGS * Math.min(1, seen / senses.sightRange) - 0.001);
        double light = player.level().getMaxLocalRawBrightness(player.blockPosition()) / 15.0;
        String how = (light < 0.35 ? "Dark" : light < 0.7 ? "Dim" : "Lit") + (player.isCrouching() ? ", crouched" : "")
                + " · " + Math.round(seen) + " blocks";
        row(g, font, "Seen", seenLit, how, Hud.BONE, x, y + 13, a, false);
    }

    private static void row(GuiGraphicsExtractor g, Font font, String label, int lit, String word, int colour,
                            int x, int y, float a, boolean hot) {
        Hud.caps(g, font, label, x, y + 1, Hud.alpha(Hud.BONE_DIM, a), 0.8f, 0.65f);
        int sx = x + 24;
        for (int i = 0; i < SEGS; i++) {
            int c = i >= lit ? 0x29E9E1CB : hot && i >= 6 ? Hud.RED : hot && i >= 4 ? Hud.AMBER : Hud.BONE;
            g.fill(sx + i * 5, y, sx + i * 5 + 4, y + 6, Hud.alpha(c, a));
        }
        Hud.caps(g, font, word, sx + SEGS * 5 + 4, y + 1, Hud.alpha(colour, a), 0.8f, 0.65f);
    }

    private SensesHud() {
    }
}
