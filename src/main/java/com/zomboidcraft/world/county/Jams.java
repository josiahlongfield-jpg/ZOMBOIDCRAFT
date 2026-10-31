package com.zomboidcraft.world.county;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Zomboid's Traffic Jam setting on the county's open roads: the queues the evacuation left where it stopped
 * moving. Every highway and county road is cut into stretches of {@link #STRETCH} blocks, and each stretch
 * rolls for a jam from the road's seed alone, so every chunk agrees on where one is and places just the cars
 * whose middles fall inside it. Jams come thickest near the towns at a road's ends and on the bridges, where
 * the crowds bunched up; out in the country they are few.
 * <p>
 * A jam is one of two shapes. Most often the outbound side backed up nose to tail behind a crash at its head,
 * one lane or the whole carriageway, the car at the front wrecked or burnt and the queue behind it abandoned
 * with doors open. Sometimes a pile-up: cars from both sides slewed across the lanes round a burnt core.
 * Every car goes in as "kind@variant#jam" so the chunk writer can leave them all out when the sandbox's
 * Traffic Jam switch (world.trafficJams, VEHICLES' wiring) is off. Places keep their own traffic (the toll
 * plaza, the interchange and the rest area have theirs), so a jam never runs into a place.
 */
final class Jams {
    /** The length of road each jam roll covers. */
    static final double STRETCH = 128;
    /** The towns' outskirts, where the queues were longest. */
    private static final double TOWN_REACH = 320;
    /** The models a jam is made of: mostly cars, some vans and pickups, the odd truck. */
    private static final String MODELS = "car,van,truck";

    private Jams() {
    }

    /** One car of a jam: centre, yaw, and how it is left. */
    interface Out {
        void car(double x, double z, float yaw, String variant, long seed);
    }

    /**
     * The jam cars of every road in the list whose middles fall in the 16x16 chunk at (bx, bz), each passed to
     * out with its yaw and variant; the caller stands it on the road and leaves out what lands off the asphalt.
     */
    static void place(County county, List<Road> roads, int bx, int bz, Out out) {
        for (Road r : roads) {
            if (r.kind == Road.Kind.TRACK || r.xs.length < 2) {
                continue;
            }
            double length = (r.xs.length - 1) * (double) Road.STEP;
            int first = (int) Math.floor(Math.max(0, along(r, bx - 8, bz - 8, bx + 24, bz + 24, true)) / STRETCH);
            int last = (int) Math.floor(Math.max(0, along(r, bx - 8, bz - 8, bx + 24, bz + 24, false)) / STRETCH);
            for (int w = first; w <= last; w++) {
                stretch(county, r, w, length, bx, bz, out);
            }
        }
    }

    /** The first (or last) distance along r at which its line passes within the box, or -1 if it never does. */
    static double along(Road r, int x0, int z0, int x1, int z1, boolean first) {
        double found = -1;
        for (int i = 0; i < r.xs.length; i++) {
            if (r.xs[i] >= x0 - Road.STEP && r.xs[i] <= x1 + Road.STEP && r.zs[i] >= z0 - Road.STEP && r.zs[i] <= z1 + Road.STEP) {
                found = i * (double) Road.STEP;
                if (first) {
                    return Math.max(0, found - Road.STEP);
                }
            }
        }
        return found < 0 ? -1 : found + Road.STEP;
    }

    /** A stretch's jam as rolled: how long, where its tail end starts, and the roll's dice for the rest. */
    private record Roll(double jam, double head, SplittableRandom rng) {
    }

    /** Stretch w of r's jam, or null when it rolled none. */
    private static Roll roll(County county, Road r, int w, double length) {
        double a0 = w * STRETCH, a1 = Math.min(length, a0 + STRETCH);
        if (a1 - a0 < 40) {
            return null;
        }
        SplittableRandom rng = new SplittableRandom(Noise.hash(r.seed ^ 0x7A3L, w, 911));
        double fromEnd = Math.min((a0 + a1) / 2, length - (a0 + a1) / 2);
        double chance = r.kind == Road.Kind.HIGHWAY ? 0.16 : 0.08;
        if (fromEnd < TOWN_REACH) {
            chance *= 1 + 2.5 * (1 - fromEnd / TOWN_REACH);
        }
        boolean bridge = bridged(county, r, a0, a1);
        if (bridge) {
            chance = Math.max(chance, 0.55);
        }
        if (rng.nextDouble() >= chance) {
            return null;
        }
        double jam = 22 + rng.nextDouble() * (bridge ? 60 : 44);
        double head = a0 + 6 + rng.nextDouble() * Math.max(1, a1 - a0 - jam - 12);
        return new Roll(jam, head, rng);
    }

    /**
     * Whether a jam lies on r anywhere between a0 and a1 along it (with a car's length to spare), so the road
     * stories ({@link RoadStories}) keep clear of them.
     */
    static boolean jammed(County county, Road r, double a0, double a1) {
        if (r.kind == Road.Kind.TRACK || r.xs.length < 2) {
            return false;
        }
        double length = (r.xs.length - 1) * (double) Road.STEP;
        for (int w = (int) Math.floor(Math.max(0, a0) / STRETCH); w <= (int) Math.floor(Math.max(0, a1) / STRETCH); w++) {
            Roll roll = roll(county, r, w, length);
            if (roll != null && roll.head - 10 < a1 && roll.head + roll.jam + 10 > a0) {
                return true;
            }
        }
        return false;
    }

    private static void stretch(County county, Road r, int w, double length, int bx, int bz, Out out) {
        Roll roll = roll(county, r, w, length);
        if (roll == null) {
            return;
        }
        SplittableRandom rng = roll.rng;
        double jam = roll.jam, head = roll.head;
        // which way the queue was headed: mostly away from the nearer town, the way out (forward: along the road)
        boolean forward = (head < length / 2) == rng.nextDouble() < 0.8;
        double[] lanes = r.kind == Road.Kind.HIGHWAY ? new double[] {1.75, 5.25} : new double[] {2};
        if (rng.nextDouble() < (r.kind == Road.Kind.HIGHWAY ? 0.25 : 0.2)) {
            pileUp(r, head, jam, lanes, rng, bx, bz, out);
            return;
        }
        // the queue: its head (the crash) at the far end in its own direction of travel, nose to tail back from it
        int sign = forward ? 1 : -1;
        boolean whole = rng.nextDouble() < 0.55;
        int only = lanes.length > 1 && rng.nextBoolean() ? 1 : 0;
        double crashAt = forward ? head + jam : head;
        for (int li = 0; li < lanes.length; li++) {
            if (!whole && li != only) {
                continue;
            }
            double off = lanes[li] * sign;
            double at = rng.nextDouble() * 3;
            int k = 0;
            while (at < jam) {
                double s = forward ? crashAt - at : crashAt + at;
                double lateral = off + (rng.nextDouble() - 0.5) * 0.6;
                float skew = (float) rng.nextGaussian() * 5f;
                if (rng.nextDouble() < 0.12) {
                    skew += rng.nextBoolean() ? 22 : -22;
                }
                String variant;
                if (k == 0) {
                    variant = rng.nextDouble() < 0.4 ? "burnt" : "wrecked";
                    skew += (float) (rng.nextBoolean() ? 1 : -1) * (20 + rng.nextDouble() * 40);
                } else {
                    double v = rng.nextDouble();
                    variant = k == 1 && v < 0.6 ? "wrecked" : v < 0.08 ? "burnt" : v < 0.26 ? "wrecked" : "abandoned";
                }
                emit(r, s, lateral, sign > 0 ? 0 : 180, skew, variant, rng.nextLong(), bx, bz, out);
                at += 6.2 + rng.nextDouble() * 1.8 + (rng.nextDouble() < 0.1 ? 7 + rng.nextDouble() * 12 : 0);
                k++;
            }
        }
    }

    /** A pile-up: both sides' cars slewed across the lanes round a burnt core, the crowd behind them left anyhow. */
    private static void pileUp(Road r, double head, double jam, double[] lanes, SplittableRandom rng, int bx, int bz, Out out) {
        double mid = head + jam / 2, spread = Math.min(jam, 30) / 2;
        int n = 4 + rng.nextInt(lanes.length * 3);
        for (int k = 0; k < n; k++) {
            double s = mid + (rng.nextDouble() * 2 - 1) * spread;
            double near = Math.abs(s - mid) / spread;
            int side = rng.nextBoolean() ? 1 : -1;
            double lateral = side * lanes[rng.nextInt(lanes.length)] + (rng.nextDouble() - 0.5) * 2;
            float skew = (float) ((rng.nextDouble() * 2 - 1) * (near < 0.4 ? 80 : 35));
            String variant = near < 0.3 ? (rng.nextDouble() < 0.6 ? "burnt" : "wrecked") : rng.nextDouble() < 0.4 ? "wrecked" : "abandoned";
            emit(r, s, lateral, side > 0 ? 0 : 180, skew, variant, rng.nextLong(), bx, bz, out);
        }
    }

    /** One car at distance s along r, that far off its centre line, if its middle falls in the chunk. */
    private static void emit(Road r, double s, double lateral, float turn, float skew, String variant, long seed, int bx, int bz, Out out) {
        double[] q = point(r, s);
        if (q == null) {
            return;
        }
        // right of travel is (-dz, dx), the same side Road.Hit's offset counts positive
        double x = q[0] - q[3] * lateral, z = q[1] + q[2] * lateral;
        if (x < bx || x >= bx + 16 || z < bz || z >= bz + 16) {
            return;
        }
        out.car(x, z, Hwy.yaw(q[2], q[3]) + turn + skew, variant, seed);
    }

    /** {x, z, dx, dz} at distance s along r, or null off its ends. */
    static double[] point(Road r, double s) {
        if (s < 0) {
            return null;
        }
        int i = (int) Math.floor(s / Road.STEP);
        if (i + 1 >= r.xs.length) {
            return null;
        }
        double t = s / Road.STEP - i;
        double vx = r.xs[i + 1] - r.xs[i], vz = r.zs[i + 1] - r.zs[i], len = Math.hypot(vx, vz);
        if (len == 0) {
            return null;
        }
        return new double[] {r.xs[i] + vx * t, r.zs[i] + vz * t, vx / len, vz / len};
    }

    /** Whether the road crosses water or a gully on a bridge somewhere in [a0, a1] (as CountyGen decides it). */
    static boolean bridged(County county, Road r, double a0, double a1) {
        for (double s = a0; s < a1; s += 16) {
            double[] q = point(r, s);
            if (q == null) {
                continue;
            }
            int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[1]);
            List<Place> near = county.placesNear(x, z, x, z, County.SKIRT);
            double natural = county.ground(x, z, near);
            int i = Math.min(r.ys.length - 1, (int) Math.round(s / Road.STEP));
            if (county.terrain.wet(x, z, natural) || natural < r.ys[i] - 4) {
                return true;
            }
        }
        return false;
    }

    /** "car,van,truck@variant#jam": what the chunk writer is handed for a jam car. */
    static String models(String variant) {
        return MODELS + "@" + variant + "#jam";
    }
}
