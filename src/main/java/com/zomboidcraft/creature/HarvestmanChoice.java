package com.zomboidcraft.creature;

/**
 * How a Harvestman that isn't hunting them deals with a player who crosses its path: it weighs how dangerous they are
 * against the chance they give it, with what it's learned of them and of guns and light, and where they are (its den,
 * the heart of its ground, the edge of it). Plain arithmetic on what HarvestmanGoals sees, so it can be tested apart
 * from the game.
 *
 * <ul>
 *   <li>STRIKE: they're in its den (always), or it's starving and they're alone, unaware and in the dark, and they don't
 *   look dangerous. Never in the quiet after a hunt, and never by the one that's hunting someone else.</li>
 *   <li>WARN: on its ground it shows itself, rages and hisses to drive them off, more readily if warnings have worked
 *   on them before, less if they've shot it.</li>
 *   <li>WATCH: it shadows them from the dark at a distance, learning them (one it hasn't seen much it's curious about).</li>
 *   <li>AVOID: armed, lit up, in a group, or someone who's hurt it: it keeps out of their way.</li>
 *   <li>LET_PASS: lying in wait, or tending its den, it stays still and lets them go by.</li>
 * </ul>
 */
public final class HarvestmanChoice {
    public static final int STRIKE = 0, WARN = 1, WATCH = 2, AVOID = 3, LET_PASS = 4;
    public static final String[] NAMES = {"strike", "warn off", "watch", "avoid", "let pass"};

    /** What it knows when it decides. Distances in blocks, the rest 0..1 unless said. */
    public static final class Seen {
        public double dist;
        /** They're in its den (within 12), or the heart of its ground (within 28 of it). */
        public boolean inDen, inCore;
        /** How many others are with them (within 12). */
        public int others;
        /** They're looking its way; they're in light (block light 9+ or carrying one); they hold a gun. */
        public boolean aware, lit, armed;
        /** Their health; its own; legs it's lost (0-4). */
        public double theirHealth = 1, itsHealth = 1;
        public int legsGone;
        /** Its needs, traits and what it's learned. */
        public double hunger, bold = 0.5, wary, curious = 0.4, patience = 0.5, gunNerve, lightNerve;
        /** What it remembers of them: damage they've done it, times it's seen them, warnings heeded and ignored. */
        public double hurtMe;
        public int timesSeen, heeded, ignored;
        /** The quiet after a hunt; it's the hunter (after someone else); it's lying in wait or tending its den. */
        public boolean grace, huntingOther, waiting;
        /** By day. */
        public boolean day;
    }

    /** The choice, and why, said plainly. */
    public record Choice(int what, String why, double[] scores) {
        public String name() {
            return NAMES[what];
        }
    }

    /** How dangerous they look to it, 0..~1.5. */
    public static double threat(Seen s) {
        double t = 0;
        if (s.armed) {
            t += 0.4 * (1 - s.gunNerve);
        }
        t += 0.22 * Math.min(s.others, 3);   // each companion counts
        if (s.lit) {
            t += 0.2 * (1 - s.lightNerve);
        }
        t += 0.35 * Math.min(1, s.hurtMe / 150);
        t += 0.3 * (1 - s.itsHealth) + 0.15 * s.legsGone;
        return t;
    }

    /** How good a chance they give it, 0..1. */
    public static double chance(Seen s) {
        double c = 0;
        if (s.others == 0) {
            c += 0.3;
        }
        if (!s.aware) {
            c += 0.3;
        }
        if (!s.lit) {
            c += 0.2;
        }
        c += 0.15 * (1 - s.theirHealth);
        if (s.dist < 10) {
            c += 0.1;
        }
        return Math.min(1, c);
    }

    /**
     * Its choice. noise is a small random amount per option (0..1 each, scaled down here), so two alike meetings
     * needn't go the same way.
     */
    public static Choice decide(Seen s, double[] noise) {
        double threat = threat(s), chance = chance(s);
        double[] w = new double[5];
        // in its den it fights whoever it is
        if (s.inDen && !s.huntingOther) {
            w[STRIKE] = 3 + s.bold - threat * 0.5;
        } else if (!s.grace && !s.huntingOther && s.hunger >= 0.6 && chance >= 0.6 && s.itsHealth > 0.6) {
            w[STRIKE] = (s.hunger * s.hunger * 1.4 + s.bold * 0.5) * chance - threat * 1.3 - 0.3;
        } else {
            w[STRIKE] = -9;
        }
        double heededRate = (s.heeded + 1.0) / (s.heeded + s.ignored + 2.0);
        if (s.inCore || s.inDen) {
            w[WARN] = 0.9 + 0.6 * heededRate + 0.3 * s.bold - 0.5 * threat - Math.min(1, s.hurtMe / 150) * 0.6;
        } else {
            w[WARN] = 0.15 * s.bold - 0.3 * threat;
        }
        // watching means not being seen: one looking its way it leaves; one passing under where it waits it lets go by
        w[WATCH] = 0.45 * s.curious + 0.35 * (1 - Math.min(1, threat)) + (s.timesSeen < 3 ? 0.3 : 0) - (s.aware ? 0.5 : 0)
                - 0.5 * s.wary + (s.huntingOther ? 0.1 : 0) - (s.waiting ? 0.3 : 0);
        w[AVOID] = threat * 1.2 + s.wary * 0.7 + (s.day ? 0.15 : 0) + (s.aware ? 0.35 : 0) + (s.aware && s.dist < 12 ? 0.2 : 0);
        w[LET_PASS] = s.waiting ? 0.55 + 0.4 * s.patience - (s.aware ? 0.3 : 0) : 0.05;
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < w.length; i++) {
            double v = w[i] + (noise != null && i < noise.length ? (noise[i] - 0.5) * 0.25 : 0);
            if (v > bestScore) {
                bestScore = v;
                best = i;
            }
        }
        return new Choice(best, why(best, s, threat, chance), w);
    }

    private static String why(int what, Seen s, double threat, double chance) {
        return switch (what) {
            case STRIKE -> s.inDen ? "they came into its den" : String.format("starving (%.0f%%), and they're alone, unaware and in the dark", s.hunger * 100);
            case WARN -> s.hurtMe > 0 ? "they're on its ground again" : s.heeded > 0 ? "warning them off worked before" : "they're on its ground";
            case WATCH -> s.timesSeen < 3 ? "it doesn't know them yet" : "learning their ways";
            case AVOID -> s.armed && s.gunNerve < 0.5 ? "they're armed" : s.others > 0 ? "there are too many of them"
                    : s.hurtMe > 0 ? "they've hurt it before" : s.aware ? "they've seen it" : s.lit ? "they're in the light"
                    : threat > 0.5 ? "they're dangerous" : "it's wary";
            default -> "it stays still and lets them go by";
        };
    }

    private HarvestmanChoice() {
    }
}
