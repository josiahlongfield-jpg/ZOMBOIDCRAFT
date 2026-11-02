package com.zomboidcraft.client.hud;

import com.zomboidcraft.survival.Body;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Health, the whole body at once: a small figure of you in the bottom-left corner (the inventory mirror's), lit red
 * from below as you get hurt, with a word for how you are. It turns its head where you look, at half strength. It
 * stays on screen, or with that setting off keeps out of sight while you're fine. Low health and fresh hurts also put blood at the screen's edge.
 */
final class BodyHud {
    static final int W = 50, H = 84, LEFT = 8, BOTTOM = 14;
    private static final float SHOW = 5f;

    private static final Hud.Fade fade = new Hud.Fade();
    private static float lastHealth = -1, sinceChange = 99;
    /** The figure's head, eased toward where you look: radians the way the inventory's mouse-follow takes them. */
    private static float yaw, pitch;
    private static float pulse = 99;

    /** Starts the heartbeat flush of red at the screen's edge (a hit, or a moodle turning bad). */
    static void beat() {
        pulse = 0;
    }

    static void vignette(GuiGraphicsExtractor g, DeltaTracker delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float f = player.getHealth() / player.getMaxHealth();
        pulse += Hud.dt;
        float low = f < .4f ? (.4f - f) / .4f * .55f : 0;
        float beat = pulse < 1.15f ? envelope(pulse / 1.15f) * .85f : 0;
        float a = Math.min(1, low + beat);
        if (a > 0.01f) Hud.sprite(g, Hud.id("blood"), 0, 0, g.guiWidth(), g.guiHeight(), Hud.alpha(0xFFFFFFFF, a));
    }

    /** A heartbeat: up, a dip, a second smaller beat, then gone. */
    private static float envelope(float t) {
        if (t < .12f) return t / .12f;
        if (t < .28f) return Mth.lerp((t - .12f) / .16f, 1f, .2f);
        if (t < .42f) return Mth.lerp((t - .28f) / .14f, .2f, .75f);
        return Mth.lerp((t - .42f) / .58f, .75f, 0f);
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        float health = player.getHealth(), max = player.getMaxHealth(), f = health / max;
        Body injuries = Body.get(player);
        int wounds = injuries.bleeding();
        Body.Region worst = injuries.worst();
        sinceChange += Hud.dt;
        if (lastHealth >= 0 && Math.abs(health - lastHealth) > 0.01f) {
            if (health < lastHealth) beat();
            sinceChange = 0;
        }
        lastHealth = health;
        float a = fade.update(HudSettings.figureAlways || Hud.peek || sinceChange < SHOW || wounds > 0 || f < .5f
                || worst != null && injuries.part(worst).severity() >= 2);
        if (a <= 0) return;

        // tucks away down and to the left
        float drop = (1 - a) * (1 - a);
        int x = Math.round(LEFT - drop * 20), y = Math.round(g.guiHeight() - BOTTOM - H + drop * (H + 20));
        // the 3D figure of you that stood here is gone from the HUD (Josiah 2026-11-02: redrawing it every frame was an
        // eighth of the game's main thread; the inventory and character pages keep it). The injury word stays
        float cx = x + W / 2f;

        Font font = mc.font;
        // the worst injury names itself; otherwise a word for your health
        String word = worst != null ? Body.describe(worst, injuries.part(worst))
                : f >= .9f ? "Fine" : f >= .6f ? "Bruised" : f >= .35f ? "Hurt" : "Critical";
        int ink = wounds > 0 || f < .35f || worst != null && injuries.part(worst).severity() >= 2 ? 0xFFE07A5A : Hud.BONE_DIM;
        float ww = Hud.capsWidth(font, word, 1.4f, .8f);
        Hud.caps(g, font, word, cx - ww / 2, y + H + 1, Hud.alpha(ink, a), 1.4f, .8f);
    }

    private BodyHud() {
    }
}
