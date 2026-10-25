package com.zomboidcraft.client.hud;

import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** A skill going up, top left for a few seconds: its name and level over ten pips, the new one lit amber. */
final class SkillTape {
    private static final float SHOWS = 4.5f;
    private static final Hud.Fade fade = new Hud.Fade();
    private static final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private static LocalPlayer seen;
    private static Skill skill;
    private static int level;
    private static float since = 99;

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
            if (before != null && now > before) {
                skill = s;
                level = now;
                since = 0;
            }
        }
        since += Hud.dt;
        float a = fade.update(since < SHOWS);
        if (a <= 0 || skill == null) return;
        Font font = mc.font;
        float x = 10 - (1 - a) * 5, y = 10;
        float w = Hud.caps(g, font, skill.displayName(), x, y, Hud.alpha(Hud.BONE, a), 1.6f, 1.1f);
        Hud.caps(g, font, String.valueOf(level), x + w + 5, y, Hud.alpha(Hud.AMBER, a), 0, 1.1f);
        for (int i = 1; i <= Skill.MAX_LEVEL; i++) {
            int px = Math.round(x) + (i - 1) * 6, py = Math.round(y) + 13;
            if (i == level) {
                float pop = Math.max(0, 1 - since / 0.6f);
                int grow = Math.round(pop * 2);
                g.fill(px - 1 - grow, py - 1 - grow, px + 6 + grow, py + 6 + grow, Hud.alpha(0x47E0A050, a * (1 - pop)));
                g.fill(px, py, px + 5, py + 5, Hud.alpha(Hud.AMBER, a * (1 - pop * .7f)));
            } else if (i < level) {
                g.fill(px, py, px + 5, py + 5, Hud.alpha(Hud.BONE, a));
            } else {
                g.outline(px, py, 5, 5, Hud.alpha(Hud.BONE_FAINT, a));
                g.fill(px + 1, py + 1, px + 4, py + 4, Hud.alpha(0x40000000, a));
            }
        }
    }

    private SkillTape() {
    }
}
