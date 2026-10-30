package com.zomboidcraft.npc;

import java.util.Locale;
import net.minecraft.util.RandomSource;

/**
 * What a companion is good at, 0..10 each, and getting better with practice: every shot, bandage, meal, repair, search
 * and harvest earns experience in its skill. A new companion starts with a little of most things (0..2) and one she's
 * already handy at (3..4).
 */
public final class NpcSkills {
    public enum Skill {
        AIM("aim"), FIRST_AID("first aid"), COOKING("cooking"), CARPENTRY("carpentry"), SCAVENGING("scavenging"),
        FARMING("farming");

        public final String label;

        Skill(String label) {
            this.label = label;
        }
    }

    public static final int MAX = 10;
    /** Experience for level n is XP_STEP * n squared: 30 for level 1, 750 for 5, 3000 for 10. */
    private static final float XP_STEP = 30f;

    final float[] xp = new float[Skill.values().length];

    void roll(RandomSource random) {
        for (int i = 0; i < xp.length; i++) {
            xp[i] = xpFor(random.nextInt(3));
        }
        xp[random.nextInt(xp.length)] = xpFor(3 + random.nextInt(2));
    }

    static float xpFor(int level) {
        return XP_STEP * level * level;
    }

    public int level(Skill s) {
        return Math.min(MAX, (int) Math.sqrt(xp[s.ordinal()] / XP_STEP));
    }

    /** How far she is toward her next level (0..1). */
    public float progress(Skill s) {
        int l = level(s);
        if (l >= MAX) return 1f;
        return (xp[s.ordinal()] - xpFor(l)) / (xpFor(l + 1) - xpFor(l));
    }

    /** Adds experience; returns the new level if she just reached one, else -1. */
    int practise(Skill s, float amount) {
        int before = level(s);
        xp[s.ordinal()] += amount;
        int after = level(s);
        return after > before ? after : -1;
    }

    void set(Skill s, int level) {
        xp[s.ordinal()] = xpFor(Math.max(0, Math.min(MAX, level)));
    }

    String save() {
        StringBuilder b = new StringBuilder();
        for (float v : xp) b.append(String.format(Locale.ROOT, "%.1f ", v));
        return b.toString().trim();
    }

    boolean load(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            String[] f = s.trim().split(" ");
            for (int i = 0; i < Math.min(f.length, xp.length); i++) xp[i] = Float.parseFloat(f[i]);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
