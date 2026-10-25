package com.zomboidcraft.client.hud;

import com.zomboidcraft.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Matrix3x2fStack;

/**
 * A scuffed digital watch in the top-right corner, there while you have one on you: the time in 7-segment digits,
 * the weekday and the day count. Its backlight comes on in the dark. Drawn in the watch's own 100 x 74 units.
 */
final class WatchHud {
    private static final float WIDTH = 56, SCALE = WIDTH / 100f;
    private static final int TOP = 6, RIGHT = 8;
    private static final String[] WEEKDAYS = {"su", "mo", "tu", "we", "th", "fr", "sa"};

    /** Draws the watch if you carry one; returns where the moodles below it start. */
    static int render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative() || !wearing(player)) return TOP + 2;
        long clock = mc.level.getDefaultClockTime();
        int dayTicks = (int) Math.floorMod(clock, 24000L);
        int minutes = ((dayTicks / 1000 + 6) % 24) * 60 + (dayTicks % 1000) * 60 / 1000;
        long day = Math.floorDiv(clock, 24000L) + 1;
        boolean lit = mc.level.getMaxLocalRawBrightness(player.blockPosition()) < 6;
        int seg = lit ? 0xFF0F2A2A : 0xFF1D2618;
        int ghost = lit ? 0x1A0F2A2A : 0x141D2618;

        float x = g.guiWidth() - RIGHT - WIDTH;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, TOP);
        pose.scale(SCALE, SCALE);
        Hud.sprite(g, Hud.id(lit ? "watch/base_lit" : "watch/base"), 0, 0, 100, 74, 0xFFFFFFFF);
        // the day starts on a Friday, as Zomboid's first day does
        Hud.sprite(g, Hud.id("watch/" + WEEKDAYS[(int) Math.floorMod(day - 1 + 5, 7L)]), 22, 25.9f, 7, 5, seg);
        String number = String.valueOf(day);
        float nx = 78 - 2.6f * number.length();
        for (int i = 0; i < number.length(); i++) {
            Hud.sprite(g, Hud.id("watch/num_" + number.charAt(i)), nx + 2.6f * i, 25.9f, 2.6f, 5, seg);
        }
        Hud.sprite(g, Hud.id("watch/day"), nx - 8.4f, 25.9f, 8, 5, seg);
        // HH:MM, each digit over a faint 8 the way an LCD shows its unlit segments
        int[] digits = {minutes / 600, minutes / 60 % 10, minutes % 60 / 10, minutes % 10};
        float[] at = {0, 11, 26, 37};
        for (int i = 0; i < 4; i++) {
            float dx = 23 + at[i] * 1.1f - 0.55f, dy = 32.5f - 0.55f;
            Hud.sprite(g, Hud.id("watch/seg_8"), dx, dy, 11, 18.7f, ghost);
            Hud.sprite(g, Hud.id("watch/seg_" + digits[i]), dx, dy, 11, 18.7f, seg);
        }
        for (float cy : new float[]{4, 10}) {
            pose.pushMatrix();
            pose.translate(23 + 22.2f * 1.1f, 32.5f + cy * 1.1f);
            pose.scale(2f, 2f);
            g.fill(0, 0, 1, 1, seg);
            pose.popMatrix();
        }
        Hud.sprite(g, Hud.id("watch/glass"), 18, 23, 64, 32, 0xFFFFFFFF);
        pose.popMatrix();
        return Math.round(TOP + 74 * SCALE + 6);
    }

    /** For now a watch anywhere on you counts: in your hands, on your belt or in a pocket. */
    private static boolean wearing(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).is(ModItems.DIGITAL_WATCH)) return true;
        }
        return inventory.getItem(Inventory.SLOT_OFFHAND).is(ModItems.DIGITAL_WATCH);
    }

    private WatchHud() {
    }
}
