package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.building.DigRules;
import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.SkillBooks;
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
            new Info(Skill.AIMING, "Firearm", "Fire guns. A hit teaches more than a miss.", List.of(
                    new Perk(0, "5% tighter spread, from the hip and aimed"),
                    new Perk(5, "Steadier on the move: walking widens your spread far less"),
                    new Perk(10, "Half the spread of a beginner")),
                    l -> "Right now: " + 5 * l + "% tighter spread than a beginner" + (l >= 5 ? ", steady on the move." : ".")),
            new Info(Skill.BLUNT, "Combat", "Hit zombies with bats, pipes, hammers and the like.", melee(), SkillsPage::meleeNow),
            new Info(Skill.BLADE, "Combat", "Hit zombies with knives, machetes, axes and swords.", melee(), SkillsPage::meleeNow),
            new Info(Skill.FIRST_AID, "Survivalist", "Bandage wounds and splint breaks.", List.of(
                    new Perk(0, "Quicker hands, and bound wounds heal 5% faster"),
                    new Perk(5, "One bandage binds two wounds"),
                    new Perk(8, "Splints set breaks a third faster")),
                    l -> "Right now: bandaging takes " + secs(Treatment.ticks(Treatment.Kind.BANDAGE, l)) + ", bound wounds heal "
                            + 5 * l + "% faster."),
            new Info(Skill.MAINTENANCE, "Combat", "Repair weapons at the bench.", List.of(
                    new Perk(0, "A repair gives back 60% of what the kit could at level 0"),
                    new Perk(0, "6% more back from every repair, each level"),
                    new Perk(10, "120%: better than the kit promises")),
                    l -> "Right now: repairs give back " + Math.round((0.6f + 0.06f * l) * 100) + "%."),
            new Info(Skill.CARPENTRY, "Crafting", "Board up windows and doors, and strip wooden furniture with a hammer, crowbar or axe.", List.of(
                    new Perk(0, "5% chance to save the nails on every board, and quicker hammering"),
                    new Perk(DigRules.Tier.WOOD.level(), "Knock through wooden walls and floors"),
                    new Perk(4, "Reinforced barricades take more hits"),
                    new Perk(8, "Heavy barricades")),
                    l -> "Right now: " + (l >= 8 ? "heavy" : l >= 4 ? "reinforced" : "plain") + " barricades, " + 5 * l
                            + "% of nails saved, " + secs(Math.max(30, 60 - 3 * l)) + " a board" + wall(DigRules.Tier.WOOD, l)),
            new Info(Skill.MASONRY, "Crafting", "Break up steps, kerbs and other stonework with a sledgehammer. It leaves rubble.", wallPerks(DigRules.Tier.MASONRY),
                    l -> "Right now:" + wall(DigRules.Tier.MASONRY, l).substring(1)),
            new Info(Skill.METALWORKING, "Crafting", "Cut up metal furniture and fittings with a propane torch. It leaves scrap metal.", wallPerks(DigRules.Tier.METAL),
                    l -> "Right now:" + wall(DigRules.Tier.METAL, l).substring(1)),
            new Info(Skill.MECHANICS, "Crafting", "Take cars apart and put them back together. Every part you fit or pull teaches it, and Laine's Auto Manuals "
                    + "teach you each class of car (standard, commercial, performance).", List.of(
                    new Perk(0, "Fluids, the battery, tyres on a jack, headlights"),
                    new Perk(1, "Spark plugs, horn, tail lights; you can tell which part won't let it start"),
                    new Perk(2, "Fan belt, bonnet and boot lid"),
                    new Perk(3, "Oil changes, brake pads, doors"),
                    new Perk(4, "Radiator, alternator, starter motor, wheel rims"),
                    new Perk(5, "Suspension, exhaust, steering rack"),
                    new Perk(6, "Fuel tank and pump, brake fluid"),
                    new Perk(7, "Gearbox"),
                    new Perk(8, "Engine rebuilds and swaps")),
                    l -> "Right now: you can work on parts up to level " + l + "."),
            new Info(Skill.PILOTING, "Survivalist", "Fly the jets and the helicopter. Time in the air and clean landings teach it; "
                    + "reading a flight manual gets you off the ground in the first place.", List.of(
                    new Perk(0, "Shaky hands: the controls are slow and twitchy"),
                    new Perk(0, "Each level steadies the controls and lets you land a little harder"),
                    new Perk(10, "Full control authority, no wobble")),
                    l -> "Right now: " + Math.round(65 + 3.5 * l) + "% control authority, touch down at up to "
                            + String.format(Locale.ROOT, "%.1f", 7 + 0.3 * l) + " m/s."),
            new Info(Skill.TAILORING, "Crafting", "Patch holes in your clothes, and rip old ones into strips. Needs a sewing kit, "
                    + "thread and something to sew on.", List.of(
                    new Perk(0, "A quarter of patches pull loose at first"),
                    new Perk(0, "Each level, fewer pull loose and sewing goes 5% quicker"),
                    new Perk(10, "Stitches always hold, and a patch takes half the time")),
                    l -> "Right now: " + Math.round(Math.max(0, 25 - 2.5f * l)) + "% of patches pull loose, "
                            + secs(Math.round(100 * (1 - 0.05f * l))) + " a patch."),
            new Info(Skill.SPRINTING, "Agility", "Run. Long sprints teach the most.", List.of(
                    new Perk(0, "A tenth less tiring each level"),
                    new Perk(10, "Only 1.5 times as tiring as walking, down from 2.5")),
                    l -> String.format(Locale.ROOT, "Right now: sprinting is %.1f times as tiring as walking.", 2.5 - 0.1 * l)),
            new Info(Skill.SNEAKING, "Agility", "Move near zombies without being noticed. Crouching counts.", List.of(
                    new Perk(0, "Zombies see you 3% less far"),
                    new Perk(0, "Footsteps carry 0.3 blocks less far"),
                    new Perk(10, "Seen 30% less far, footsteps 3 blocks quieter")),
                    l -> String.format(Locale.ROOT, "Right now: seen %d%% less far, footsteps %.1f blocks quieter.", 3 * l, 0.3 * l)),
            new Info(Skill.FITNESS, "Passive", "Everyone starts at 5. Running, fighting and exercise raise it; starving "
                    + "or lying idle for days wears it down again.", List.of(
                    new Perk(0, "Endurance comes back 6% quicker per level above 5, and 6% slower per level below")),
                    l -> String.format(Locale.ROOT, "Right now: endurance comes back %d%% %s than an average body.",
                            Math.abs(6 * (l - 5)), l >= 5 ? "quicker" : "slower")),
            new Info(Skill.STRENGTH, "Passive", "Everyone starts at 5. Carrying a heavy load and fighting in melee raise "
                    + "it; starving wears it down again.", List.of(
                    new Perk(0, "Carry 0.6 kg more per level above 5 (less below)"),
                    new Perk(0, "Melee hits 4% harder per level above 5 (weaker below)")),
                    l -> String.format(Locale.ROOT, "Right now: %+.1f kg carry limit, melee %+d%%.", 0.6 * (l - 5), 4 * (l - 5))),
            soon(Skill.LIGHTFOOTED, "Agility", "Walk without making noise and stepping on things that give you away."),
            soon(Skill.NIMBLE, "Agility", "Move faster while aiming and fighting."),
            soon(Skill.RELOADING, "Firearm", "Load magazines and rounds faster, and rack a jam clear quicker."),
            soon(Skill.ELECTRICAL, "Crafting", "Wire generators, lights and alarms, and hotwire cars."),
            soon(Skill.COOKING, "Crafting", "Cook dishes on stoves, campfires and barbecues; better cooks make more filling food."),
            soon(Skill.CARVING, "Crafting", "Carve wood and bone into handles, spears and tools."),
            soon(Skill.KNAPPING, "Crafting", "Chip stone into blades, axe heads and arrowheads."),
            soon(Skill.POTTERY, "Crafting", "Shape and fire clay into pots, jars and bowls."),
            soon(Skill.GLASSMAKING, "Crafting", "Melt and blow glass into jars and bottles."),
            soon(Skill.BLACKSMITHING, "Crafting", "Forge iron at an anvil into tools and parts."),
            soon(Skill.AGRICULTURE, "Farming", "Grow crops: see their health and grow time, and harvest more."),
            soon(Skill.ANIMAL_CARE, "Farming", "Keep farm animals fed, calm and healthy; milk, shear and breed them."),
            soon(Skill.BUTCHERING, "Farming", "Butcher carcasses for more meat, hide, bone and fat."),
            soon(Skill.FISHING, "Survivalist", "Catch fish with a rod or a net; more bites, fewer snapped lines."),
            soon(Skill.FORAGING, "Survivalist", "Spot berries, mushrooms, herbs and useful junk from further away."),
            soon(Skill.TRAPPING, "Survivalist", "Set snares and cage traps that catch more often."),
            soon(Skill.TRACKING, "Survivalist", "Read animal tracks and droppings to hunt them down."));

    /** A Zomboid skill whose system is still being built: shown, with XP, so nothing is hidden. */
    private static Info soon(Skill skill, String group, String what) {
        return new Info(skill, group, what, List.of(new Perk(0, "Its actions arrive in a later survival update")),
                l -> "Not trained by anything yet: the actions that teach it are still being built.");
    }

    private static final String[] GROUPS = {"Passive", "Agility", "Combat", "Firearm", "Crafting", "Farming", "Survivalist"};
    private static final int ROW = 22, PITCH = 24;

    private final KitScreen<?> screen;
    private Skill selected = Skill.AIMING;
    /** How far the list is scrolled, in pixels. */
    private int scroll, listHeight;

    SkillsPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    private static List<Perk> melee() {
        return List.of(new Perk(0, "Hits 6% harder and 4% more likely to knock a zombie down"),
                new Perk(5, "Practised swings cost a fifth less endurance"),
                new Perk(10, "Hits 60% harder, knockdowns 40% more likely"));
    }

    private static List<Perk> wallPerks(DigRules.Tier tier) {
        return List.of(new Perk(0, "Walls you can break come down 12% quicker"),
                new Perk(tier.level(), "Knock through " + (tier == DigRules.Tier.METAL ? "steel walls and plating" : "brick, block and concrete walls")
                        + " with " + tier.tools()));
    }

    /** What the skill does to walls, as the rest of a "Right now" sentence: starts with a comma. */
    private static String wall(DigRules.Tier tier, int l) {
        ZcConfig.Digging d = ZcConfig.get().digging;
        int need = tier.level();
        if (!d.rules) return ", and any wall breaks the old way.";
        if (l < need) return ", too green to knock a wall through (level " + need + " with " + tier.tools() + ").";
        double s = (tier == DigRules.Tier.WOOD ? d.woodSeconds : tier == DigRules.Tier.MASONRY ? d.masonrySeconds : d.metalSeconds)
                / (1 + 0.12 * (l - need));
        return String.format(Locale.ROOT, ", a wall comes down in %.1f s with %s.", s, tier.tools());
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
        // the whole Zomboid list is taller than the page: it scrolls, and only rows wholly inside the frame draw
        int top = y + 52, bottom = y + KitScreen.H - 20;
        scroll = Math.max(0, Math.min(scroll, listHeight - (bottom - top)));
        int ry = top - scroll;
        for (String group : GROUPS) {
            if (ry >= top && ry + 8 <= bottom) KitDraw.caps(g, font, group, x + 42, ry, KitDraw.FAINT, 0.6f);
            ry += 8;
            for (Info info : SKILLS) {
                if (!info.group().equals(group)) continue;
                if (ry >= top && ry + ROW <= bottom) row(g, font, player, info, x + 37, ry, 262, mx, my);
                ry += PITCH;
            }
            ry += 1;
        }
        listHeight = ry + scroll - top;
        if (listHeight > bottom - top) {
            int track = bottom - top, thumb = Math.max(16, track * track / listHeight);
            int ty = top + (track - thumb) * scroll / Math.max(1, listHeight - track);
            g.fill(x + 302, top, x + 303, bottom, 0x1AE8E4DA);
            g.fill(x + 301, ty, x + 304, ty + thumb, 0x80E8E4DA);
        }
        detail(g, font, player, info(selected), x + 323, y + 33, 256);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Select", hx, hy);
        hx += KitDraw.hint(g, font, "Wheel", "Scroll", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
        KitDraw.textRight(g, font, "Every number here is the real effect in game", x + 579, hy + 1, KitDraw.FAINT, 0.9f);
    }

    boolean scrolled(double mx, double my, double sy) {
        scroll -= (int) Math.signum(sy) * PITCH;
        if (scroll < 0) scroll = 0;
        return true;
    }

    private static Info info(Skill skill) {
        for (Info i : SKILLS) if (i.skill() == skill) return i;
        return SKILLS.get(0);
    }

    private void row(GuiGraphicsExtractor g, Font font, LocalPlayer player, Info info, int x, int y, int w, int mx, int my) {
        Skill skill = info.skill();
        int xp = Skills.xp(player, skill), level = Skill.levelForXp(xp);
        boolean on = skill == selected, over = screen.hovered(x, y, w, ROW, mx, my);
        if (on) g.fill(x, y, x + w, y + ROW, 0x14E8E4DA);
        KitDraw.frame(g, x, y, w, ROW, on ? 0x80E8E4DA : over ? 0x59E8E4DA : 0x00000000);
        KitDraw.caps(g, font, skill.displayName(), x + 5, y + 4, KitDraw.BRIGHT, 0.95f);
        float part = part(xp, level);
        for (int i = 0; i < Skill.MAX_LEVEL; i++) {
            int sx = x + 92 + i * 15, sy = y + 5;
            if (i < level) {
                g.fill(sx, sy, sx + 13, sy + 5, KitDraw.TEXT);
            } else {
                KitDraw.frame(g, sx, sy, 13, 5, 0x59E8E4DA);
                if (i == level && part > 0) g.fill(sx, sy, sx + Math.max(1, Math.round(13 * part)), sy + 5, 0xB3E8E4DA);
            }
        }
        KitDraw.capsRight(g, font, String.valueOf(level), x + w - 6, y + 3, KitDraw.BRIGHT, 1.1f);
        String next = level >= Skill.MAX_LEVEL ? "Mastered" : (Skill.xpForLevel(level + 1) - xp) + " xp to level " + (level + 1);
        int boost = SkillBooks.boost(player, skill);
        if (boost > 1) next += "  \u00b7  book x" + boost;
        KitDraw.text(g, font, next, x + 5, y + 14, KitDraw.DIM, 0.75f);
        screen.hot(x, y, w, ROW, () -> {
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
        if (level < Skill.MAX_LEVEL) {
            int vol = SkillBooks.volume(level);
            boolean read = SkillBooks.read(player, skill, vol);
            String book = read ? "Book Vol " + vol + " read: x" + SkillBooks.multiplier(vol) + " xp up to level " + SkillBooks.lastLevel(vol)
                    : "Book Vol " + vol + " not read: find it to earn x" + SkillBooks.multiplier(vol) + " xp";
            KitDraw.text(g, font, book, bx, cy + 22, read ? KitDraw.LINK : KitDraw.DIM, 0.75f);
        }
        cy += 38;

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
