package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Treatment;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The skills page: every skill grouped down the left with its ten level squares, and on the right what the chosen one
 * does for you, level by level. The numbers are the ones the game uses; keep them in step with the code that reads
 * each skill (Guns, CombatEvents, Stamina, Treatment, Injuries, Repairs, HammerItem, SurvivalEvents, Sight).
 */
final class SkillsPage {
    /** A step a skill gives: at a level (0 for every level) and what it does. */
    private record Perk(int at, String text) {
    }

    private record Info(Skill skill, String group, String train, List<Perk> perks, IntFunction<String> now) {
    }

    private static final List<Info> SKILLS = List.of(
            new Info(Skill.AIMING, "Combat", "Fire guns. A hit teaches more than a miss.", List.of(
                    new Perk(0, "5% tighter spread, from the hip and aimed"),
                    new Perk(5, "Steadier on the move: walking widens your spread far less"),
                    new Perk(10, "Half the spread of a beginner")),
                    l -> "Right now: " + 5 * l + "% tighter spread than a beginner" + (l >= 5 ? ", steady on the move." : ".")),
            new Info(Skill.BLUNT, "Combat", "Hit zombies with bats, pipes, hammers and the like.", melee(), SkillsPage::meleeNow),
            new Info(Skill.BLADE, "Combat", "Hit zombies with knives, machetes, axes and swords.", melee(), SkillsPage::meleeNow),
            new Info(Skill.FIRST_AID, "Survival", "Bandage wounds and splint breaks.", List.of(
                    new Perk(0, "Quicker hands, and bound wounds heal 5% faster"),
                    new Perk(5, "One bandage binds two wounds"),
                    new Perk(8, "Splints set breaks a third faster")),
                    l -> "Right now: bandaging takes " + secs(Treatment.ticks(Treatment.Kind.BANDAGE, l)) + ", bound wounds heal "
                            + 5 * l + "% faster."),
            new Info(Skill.MAINTENANCE, "Survival", "Repair weapons at the bench.", List.of(
                    new Perk(0, "A repair gives back 60% of what the kit could at level 0"),
                    new Perk(0, "6% more back from every repair, each level"),
                    new Perk(10, "120%: better than the kit promises")),
                    l -> "Right now: repairs give back " + Math.round((0.6f + 0.06f * l) * 100) + "%."),
            new Info(Skill.CARPENTRY, "Survival", "Board up windows and doors with a hammer, planks and nails.", List.of(
                    new Perk(0, "5% chance to save the nails on every board, and quicker hammering"),
                    new Perk(4, "Reinforced barricades take more hits"),
                    new Perk(8, "Heavy barricades")),
                    l -> "Right now: " + (l >= 8 ? "heavy" : l >= 4 ? "reinforced" : "plain") + " barricades, " + 5 * l
                            + "% of nails saved, " + secs(Math.max(30, 60 - 3 * l)) + " a board."),
            new Info(Skill.SPRINTING, "Agility", "Run. Long sprints teach the most.", List.of(
                    new Perk(0, "A tenth less tiring each level"),
                    new Perk(10, "Only 1.5 times as tiring as walking, down from 2.5")),
                    l -> String.format(Locale.ROOT, "Right now: sprinting is %.1f times as tiring as walking.", 2.5 - 0.1 * l)),
            new Info(Skill.SNEAKING, "Agility", "Move near zombies without being noticed. Crouching counts.", List.of(
                    new Perk(0, "Zombies see you 3% less far"),
                    new Perk(0, "Footsteps carry 0.3 blocks less far"),
                    new Perk(10, "Seen 30% less far, footsteps 3 blocks quieter")),
                    l -> String.format(Locale.ROOT, "Right now: seen %d%% less far, footsteps %.1f blocks quieter.", 3 * l, 0.3 * l)));

    private static final String[] GROUPS = {"Combat", "Survival", "Agility"};

    private final KitScreen screen;
    private Skill selected = Skill.AIMING;

    SkillsPage(KitScreen screen) {
        this.screen = screen;
    }

    private static List<Perk> melee() {
        return List.of(new Perk(0, "Hits 6% harder and 4% more likely to knock a zombie down"),
                new Perk(5, "Practised swings cost a fifth less endurance"),
                new Perk(10, "Hits 60% harder, knockdowns 40% more likely"));
    }

    private static String meleeNow(int l) {
        return "Right now: hits " + 6 * l + "% harder, knockdowns " + 4 * l + "% more likely"
                + (l >= 5 ? ", swings cost less." : ".");
    }

    private static String secs(int ticks) {
        float s = ticks / 20f;
        return (s == Math.round(s) ? String.valueOf(Math.round(s)) : String.format(Locale.ROOT, "%.1f", s)) + " s";
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        KitDraw.heading(g, font, "Skills", x + 37, y + 33, 262, false);
        KitDraw.textRight(g, font, "Lost when you die, like everything else", x + 299, y + 41, KitDraw.FAINT, 0.8f);
        int ry = y + 52;
        for (String group : GROUPS) {
            KitDraw.caps(g, font, group, x + 42, ry, KitDraw.FAINT, 0.6f);
            ry += 8;
            for (Info info : SKILLS) {
                if (!info.group().equals(group)) continue;
                row(g, font, player, info, x + 37, ry, 262, mx, my);
                ry += 29;
            }
            ry += 3;
        }
        detail(g, font, player, info(selected), x + 323, y + 33, 256);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Select", hx, hy);
        hx += KitDraw.hint(g, font, "Q/E", "Pages", hx, hy);
        KitDraw.hint(g, font, "I", "Close", hx, hy);
        KitDraw.textRight(g, font, "Every number here is the real effect in game", x + 579, hy + 1, KitDraw.FAINT, 0.9f);
    }

    private static Info info(Skill skill) {
        for (Info i : SKILLS) if (i.skill() == skill) return i;
        return SKILLS.get(0);
    }

    private void row(GuiGraphicsExtractor g, Font font, LocalPlayer player, Info info, int x, int y, int w, int mx, int my) {
        Skill skill = info.skill();
        int xp = Skills.xp(player, skill), level = Skill.levelForXp(xp);
        boolean on = skill == selected, over = screen.hovered(x, y, w, 27, mx, my);
        if (on) g.fill(x, y, x + w, y + 27, 0x14E8E4DA);
        KitDraw.frame(g, x, y, w, 27, on ? 0x80E8E4DA : over ? 0x59E8E4DA : 0x00000000);
        KitDraw.caps(g, font, skill.displayName(), x + 5, y + 5, KitDraw.BRIGHT, 0.95f);
        float part = part(xp, level);
        for (int i = 0; i < Skill.MAX_LEVEL; i++) {
            int sx = x + 92 + i * 15, sy = y + 6;
            if (i < level) {
                g.fill(sx, sy, sx + 13, sy + 5, KitDraw.TEXT);
            } else {
                KitDraw.frame(g, sx, sy, 13, 5, 0x59E8E4DA);
                if (i == level && part > 0) g.fill(sx, sy, sx + Math.max(1, Math.round(13 * part)), sy + 5, 0xB3E8E4DA);
            }
        }
        KitDraw.capsRight(g, font, String.valueOf(level), x + w - 6, y + 4, KitDraw.BRIGHT, 1.1f);
        String next = level >= Skill.MAX_LEVEL ? "Mastered" : (Skill.xpForLevel(level + 1) - xp) + " xp to level " + (level + 1);
        KitDraw.text(g, font, next, x + 5, y + 17, KitDraw.DIM, 0.75f);
        screen.hot(x, y, w, 27, () -> {
            if (selected != skill) UiSounds.play(UiSounds.CLICK);
            selected = skill;
        });
    }

    private static float part(int xp, int level) {
        if (level >= Skill.MAX_LEVEL) return 1;
        int lo = Skill.xpForLevel(level), hi = Skill.xpForLevel(level + 1);
        return (xp - lo) / (float) (hi - lo);
    }

    private void detail(GuiGraphicsExtractor g, Font font, LocalPlayer player, Info info, int x, int y, int w) {
        Skill skill = info.skill();
        int xp = Skills.xp(player, skill), level = Skill.levelForXp(xp);
        KitDraw.heading(g, font, skill.displayName(), x, y, w, true);
        int py = y + 9, ph = 250;
        KitDraw.panel(g, x, py, w, ph);
        int cx = x + 9, cy = py + 9;
        KitDraw.caps(g, font, String.valueOf(level), cx, cy, KitDraw.BRIGHT, 3f);
        int bx = cx + 34, bw = w - 18 - 34;
        KitDraw.caps(g, font, "Level " + level + " of 10", bx, cy + 6, KitDraw.TEXT, 0.8f);
        String count = level >= Skill.MAX_LEVEL ? xp + " xp" : xp + " / " + Skill.xpForLevel(level + 1) + " xp";
        KitDraw.textRight(g, font, count, bx + bw, cy + 5, KitDraw.DIM, 0.8f);
        KitDraw.bar(g, bx, cy + 16, bw, 2, part(xp, level), Float.NaN);
        cy += 32;

        KitDraw.caps(g, font, "How it grows", cx, cy, KitDraw.FAINT, 0.65f);
        cy += 8;
        cy += KitDraw.wrap(g, font, info.train(), cx, cy, w - 18, KitDraw.TEXT) + 6;

        KitDraw.caps(g, font, "What it gives", cx, cy, KitDraw.FAINT, 0.65f);
        cy += 9;
        for (Perk p : info.perks()) {
            boolean got = p.at() <= level;
            String at = p.at() == 0 ? "Each level" : "Level " + p.at();
            KitDraw.caps(g, font, at, cx, cy + 1, got ? KitDraw.LINK : KitDraw.FAINT, 0.7f);
            int h = KitDraw.wrap(g, font, p.text(), cx + 50, cy, w - 18 - 50, got ? KitDraw.TEXT : KitDraw.FAINT);
            cy += Math.max(10, h) + 2;
            g.fill(cx, cy, x + w - 9, cy + 1, 0x1AE8E4DA);
            cy += 4;
        }
        cy += 2;
        KitDraw.wrap(g, font, info.now().apply(level), cx, cy, w - 18, KitDraw.DIM);
    }
}
