package com.zomboidcraft.client.hud;

import com.zomboidcraft.building.base.RoomType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Walking into a counted room of your base: its name and grade in the corner for a few seconds (rooms plan, "You can
 * see it"), for example "Med bay: Equipped", with what the next grade needs under it.
 */
public final class RoomTape {
    private static final float SHOWS = 4.5f;
    private static final Hud.Fade fade = new Hud.Fade();
    private static RoomType type;
    private static RoomType.Grade grade;
    private static String next = "";
    private static float since = 99;

    /** From the server's word (client/building/BaseClient); a type below 0 is leaving the room, which shows nothing. */
    public static void show(int t, int g, String needs) {
        if (t < 0 || t >= RoomType.values().length) return;
        type = RoomType.values()[t];
        grade = RoomType.Grade.values()[Math.max(0, Math.min(g, RoomType.Grade.values().length - 1))];
        next = needs == null ? "" : needs;
        since = 0;
    }

    static void render(GuiGraphicsExtractor g) {
        since += Hud.dt;
        float a = fade.update(since < SHOWS);
        if (a <= 0 || type == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        Font font = mc.font;
        String title = type.label;
        String sub = grade == RoomType.Grade.NONE ? "Not fitted yet" : grade.label;
        String need = next.isEmpty() ? "Fully fitted" : "Next: " + next;
        int w = Math.max(150, Math.round(Math.max(Hud.capsWidth(font, title, 1.3f, 1.15f), font.width(need) * 0.75f)) + 22);
        int h = 40, x = 8 - Math.round((1 - a) * 12), y = mc.getWindow().getGuiScaledHeight() / 2 + 20;
        g.fill(x, y, x + w, y + h, Hud.alpha(0xE60C0D0F, a));
        g.fill(x, y, x + 2, y + h, Hud.alpha(grade == RoomType.Grade.FULL ? 0xFF8FC46A : Hud.AMBER, a));
        Hud.caps(g, font, title, x + 9, y + 6, Hud.alpha(Hud.BONE, a), 1.3f, 1.15f);
        Hud.caps(g, font, sub, x + 9, y + 18, Hud.alpha(grade == RoomType.Grade.NONE ? Hud.BONE_DIM : Hud.AMBER, a), 1.2f, 0.8f);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + 9, y + 29);
        pose.scale(0.75f, 0.75f);
        g.text(font, Hud.text(need), 0, 0, Hud.alpha(Hud.BONE_FAINT | 0x30000000, a), false);
        pose.popMatrix();
    }

    private RoomTape() {
    }
}
