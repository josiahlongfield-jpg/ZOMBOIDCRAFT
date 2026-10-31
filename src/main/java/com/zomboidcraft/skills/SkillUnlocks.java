package com.zomboidcraft.skills;

import com.zomboidcraft.world.cars.CarParts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What each skill level opens, for the level-up card and the Skills page (PZ review section 2, Josiah 2026-10-31).
 * Systems that gate something on a level add a line here with {@link #add}; Mechanics reads its parts straight from
 * CarParts. A level nothing is gated on gets the line Zomboid would give: what gets faster, safer or surer.
 */
public final class SkillUnlocks {
    private static final Map<Skill, Map<Integer, List<String>>> LINES = new EnumMap<>(Skill.class);

    /** Something a system opens at this level, in a few words ("Hotwire a car"). */
    public static void add(Skill skill, int level, String line) {
        LINES.computeIfAbsent(skill, s -> new java.util.HashMap<>()).computeIfAbsent(level, l -> new ArrayList<>()).add(line);
    }

    /** What reaching this level opens; never empty. */
    public static List<String> at(Skill skill, int level) {
        List<String> out = new ArrayList<>(opens(skill, level));
        if (out.isEmpty()) out.add(better(skill));
        return out;
    }

    /** The first level above this one that opens something, as "Mechanics 5: ...", or null at the top. */
    public static String next(Skill skill, int level) {
        for (int l = level + 1; l <= Skill.MAX_LEVEL; l++) {
            List<String> o = opens(skill, l);
            if (!o.isEmpty()) return skill.displayName() + " " + l + ": " + o.get(0);
        }
        return level < Skill.MAX_LEVEL ? skill.displayName() + " " + (level + 1) + ": " + better(skill).toLowerCase(Locale.ROOT) : null;
    }

    private static List<String> opens(Skill skill, int level) {
        List<String> out = new ArrayList<>(LINES.getOrDefault(skill, Map.of()).getOrDefault(level, List.of()));
        if (skill == Skill.MECHANICS) {
            List<String> parts = new ArrayList<>();
            for (CarParts.Part p : CarParts.Part.values()) {
                if (p.level == level) parts.add(p.title.toLowerCase(Locale.ROOT));
            }
            if (!parts.isEmpty()) out.add(0, "Take off and fit: " + String.join(", ", parts));
        }
        return out;
    }

    /** Zomboid's own kind of level: the same jobs, done better. */
    private static String better(Skill skill) {
        return switch (skill) {
            case AIMING -> "Steadier aim and a better chance to hit";
            case SPRINTING -> "Run further before you tire";
            case SNEAKING -> "Quieter steps, harder to spot";
            case CARPENTRY -> "Build faster, and what you build is sturdier";
            case FIRST_AID -> "Treat wounds faster and better";
            case BLUNT, BLADE -> "Swing harder and tire less";
            case MAINTENANCE -> "Weapons wear more slowly and repair better";
            case MASONRY -> "Break brick and concrete faster";
            case METALWORKING -> "Cut steel faster, with less fuel";
            case PILOTING -> "Steadier hands and softer landings";
            case TAILORING -> "Stitches that hold, sewn quicker";
            case MECHANICS -> "Parts come off and go on more surely";
            default -> "Faster and surer at it";
        };
    }

    private SkillUnlocks() {
    }
}
