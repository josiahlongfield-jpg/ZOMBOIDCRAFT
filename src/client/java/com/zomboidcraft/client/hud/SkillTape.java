package com.zomboidcraft.client.hud;

import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.SkillUnlocks;
import com.zomboidcraft.skills.Skills;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * A skill going up: a card drops in at the top for six seconds (PZ review section 2, Josiah 2026-10-31) with the skill
 * and its level over ten pips, the new one lit amber, then exactly what the level opened and what the next one will.
 * It never takes the mouse or pauses anything.
 */
public final class SkillTape {
    private static final float SHOWS = 6f;
    private static final int W = 230;
    private static final Hud.Fade fade = new Hud.Fade();
    private static final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private static LocalPlayer seen;
    private static Skill skill;
    private static int level;
    private static float since = 99;

    /** Shows the card for a level (the UI test calls it to film one). */
    public static void show(Skill s, int lv) {
        skill = s;
        level = lv;
        since = 0;
    }

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (player != seen) {
            // a new world or a respawn: take the levels as they are without announcing them
            seen = player;
            levels.clear();
        }
        for (Skill s : Skill.values()) {
            int now = Skills.level(player, s);
            Integer before = levels.put(s, now);
            if (before != null && now > before) show(s, now);
        }
        since += Hud.dt;
        float a = fade.update(since < SHOWS);
        if (a <= 0 || skill == null) return;
        Font font = mc.font;
        List<String> opened = SkillUnlocks.at(skill, level);
        String next = SkillUnlocks.next(skill, level);
        int lineH = 10, h = 52 + opened.size() * lineH + (next != null ? 14 : 2);
        int x = (mc.getWindow().getGuiScaledWidth() - W) / 2, y = Math.round(8 - (1 - a) * 10);
        g.fill(x, y, x + W, y + h, Hud.alpha(0xF20C0D0F, a));
        g.outline(x, y, W, h, Hud.alpha(0x2EE8E4DA, a));
        Hud.caps(g, font, "Skill up", x + 10, y + 8, Hud.alpha(Hud.AMBER, a), 1.2f, 0.75f);
        float w = Hud.caps(g, font, skill.displayName(), x + 10, y + 18, Hud.alpha(Hud.BONE, a), 1.6f, 1.25f);
        Hud.caps(g, font, String.valueOf(level), x + 10 + w + 6, y + 18, Hud.alpha(Hud.AMBER, a), 0, 1.25f);
        for (int i = 1; i <= Skill.MAX_LEVEL; i++) {
            int px = x + 10 + (i - 1) * 8, py = y + 34;
            if (i == level) {
                float pop = Math.max(0, 1 - since / 0.6f);
                int grow = Math.round(pop * 2);
                g.fill(px - 1 - grow, py - 1 - grow, px + 6 + grow, py + 6 + grow, Hud.alpha(0x47E0A050, a * (1 - pop)));
                g.fill(px, py, px + 5, py + 5, Hud.alpha(Hud.AMBER, a * (1 - pop * .7f)));
            } else if (i < level) {
                g.fill(px, py, px + 5, py + 5, Hud.alpha(Hud.BONE, a));
            } else {
                g.outline(px, py, 5, 5, Hud.alpha(Hud.BONE_FAINT, a));
            }
        }
        int ly = y + 46;
        Hud.caps(g, font, "Now open", x + 10, ly, Hud.alpha(Hud.BONE_DIM, a), 1.2f, 0.65f);
        ly += 9;
        for (String line : opened) {
            g.fill(x + 12, ly + 3, x + 15, ly + 6, Hud.alpha(0xFF8FC46A, a));
            text(g, font, line, x + 20, ly + 1, Hud.alpha(Hud.BONE, a), 0.8f, W - 30);
            ly += lineH;
        }
        if (next != null) {
            text(g, font, "Next: " + next, x + 10, ly + 4, Hud.alpha(Hud.BONE_FAINT, a), 0.75f, W - 20);
        }
    }

    /** One line of plain text, cut to fit. */
    private static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int colour, float scale, int width) {
        String cut = s;
        while (cut.length() > 4 && font.width(Hud.text(cut)) * scale > width) cut = cut.substring(0, cut.length() - 2);
        if (!cut.equals(s)) cut = cut.substring(0, cut.length() - 1) + "...";
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, Hud.text(cut), 0, 0, colour, false);
        pose.popMatrix();
    }

    private SkillTape() {
    }
}
