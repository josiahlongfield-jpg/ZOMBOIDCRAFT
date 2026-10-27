import com.zomboidcraft.client.zombie.body.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CompletableFuture;

/**
 * Fits a capsule table for every pose the server tells apart (BodyTrack picks them as ZBodyAnimator picks the
 * movement) and writes BodyCaps.java, each table with how well it fits against the one it replaces: of the rays that
 * touch the drawn body, how many a capsule catches, how many on the right part, and how many a capsule catches that
 * miss the body. Fitted on four outfits, scored on four others.
 *
 *   java FitAll OUT.java [POSE...]    (see run.sh)
 */
public class FitAll {
    record State(String name, int mode, int cuts, boolean legless, boolean reach, float a0, float a1) {}

    static final String[] FIT_OUTFITS = {"civilian", "soldier", "firefighter", "survivor"};
    static final String[] TEST_OUTFITS = {"police", "construction", "jogger", "doctor"};

    static List<HitFit.Body> samples(String[] outfits, State s, int perOutfit, float offset) {
        ZMove move = new ZMove();
        List<HitFit.Body> out = new ArrayList<>();
        int j = 0;
        for (String outfit : outfits) {
            ZBodyMesh mesh = ZBodyMesh.get(outfit);
            for (int i = 0; i < perOutfit; i++, j++) {
                float f = (j + offset) / (float) (perOutfit * outfits.length);
                ZMove.Input in = input(s, f);
                float[] w = new float[ZMove.MODES];
                w[s.mode()] = 1f;
                ZMove.Pose p = new ZMove.Pose();
                move.pose(p, w, in);
                out.add(HitFit.body(mesh, p, s.cuts(), s.legless()));
            }
        }
        return out;
    }

    static ZMove.Input input(State s, float f) {
        ZMove.Input in = new ZMove.Input();
        in.t = 0.3f + f * 4f;
        in.gait = f * (float) Math.PI * 2f;
        in.phase = 0.7f + f * 3f;
        in.look = s.mode() == ZMove.ALERT || s.mode() == ZMove.CRAWL_ALERT ? (float) Math.sin(f * 6.283f) * 0.9f : 0f;
        in.attack = s.a0() + f * (s.a1() - s.a0());
        in.legless = s.legless();
        in.goneRight = (s.cuts() & 80) != 0;
        in.reach = s.reach();
        in.hop = f;
        return in;
    }

    /** Start: the joints averaged over the samples, with the usual radii (as Caps.java). */
    static float[] start(State s) {
        ZBodyMesh mesh = ZBodyMesh.get("civilian");
        ZMove move = new ZMove();
        int N = 16;
        double[][] acc = new double[13][7];
        double[] radii = {0.15, 0.119, 0.119, 0.108, 0.08, 0.077, 0.08, 0.077, 0.097, 0.085, 0.097, 0.085, 0.15};
        int[] parts = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0};
        for (int i = 0; i < N; i++) {
            ZMove.Input in = input(s, i / (float) N);
            float[] w = new float[ZMove.MODES];
            w[s.mode()] = 1f;
            ZMove.Pose p = new ZMove.Pose();
            move.pose(p, w, in);
            float[] a = HitFit.matrices(mesh, p, s.legless());
            double[][] seg = {
                cat(at(a, 3, 1.4f, 2.4f, 0.3f), at(a, 3, 1.4f, 5.0f, 0.3f)),
                cat(at(a, 1, 1.7f, 2.1f, 0), at(a, 1, 1.7f, 7.3f, 0)),
                cat(at(a, 1, -1.7f, 2.1f, 0), at(a, 1, -1.7f, 7.3f, 0)),
                cat(at(a, 0, -1.6f, 0, 0), at(a, 0, 1.6f, 0, 0)),
                cat(at(a, 4, 0, 0.6f, 0), at(a, 5, 0, 0, 0)),
                cat(at(a, 5, 0, 0, 0), at(a, 6, 0, -2.4f, 0)),
                cat(at(a, 7, 0, 0.6f, 0), at(a, 8, 0, 0, 0)),
                cat(at(a, 8, 0, 0, 0), at(a, 9, 0, -2.4f, 0)),
                cat(at(a, 10, 0, 0, 0), at(a, 11, 0, 0, 0)),
                cat(at(a, 11, 0, 0, 0), at(a, 12, 0, -0.7f, 0.8f)),
                cat(at(a, 13, 0, 0, 0), at(a, 14, 0, 0, 0)),
                cat(at(a, 14, 0, 0, 0), at(a, 15, 0, -0.7f, 0.8f)),
                cat(at(a, 3, -1.4f, 2.4f, 0.3f), at(a, 3, -1.4f, 5.0f, 0.3f)),
            };
            for (int k = 0; k < 13; k++) for (int c = 0; c < 6; c++) acc[k][c] += seg[k][c] / N;
        }
        float[] t = new float[13 * 8];
        for (int k = 0; k < 13; k++) {
            t[k * 8] = parts[k];
            for (int c = 0; c < 6; c++) t[k * 8 + 1 + c] = (float) acc[k][c];
            t[k * 8 + 7] = (float) radii[k];
        }
        return t;
    }

    static double[] at(float[] a, int bone, float x, float y, float z) { return HitFit.tf(a, bone * 16, x, y, z); }
    static double[] cat(double[] a, double[] b) { return new double[] {a[0], a[1], a[2], b[0], b[1], b[2]}; }

    static String java(String name, float[] t) {
        StringBuilder sb = new StringBuilder("    private static final float[] T_" + name + " = {");
        for (int k = 0; k < t.length / 8; k++) {
            sb.append(k == 0 ? "" : ",\n            ").append((int) t[k * 8]);
            for (int c = 1; c < 8; c++) sb.append(String.format(", %.3ff", t[k * 8 + c]).replace("-0.000f", "0.000f"));
        }
        return sb.append("};").toString();
    }

    static final State[] STATES = {
        new State("IDLE", ZMove.IDLE, 0, false, false, 0, 0),
        new State("SHAMBLE", ZMove.SHAMBLE, 0, false, false, 0, 0),
        new State("ALERT", ZMove.ALERT, 0, false, true, 0, 0),
        new State("CHASE", ZMove.CHASE, 0, false, true, 0, 0),
        new State("ATTACK_1", ZMove.ATTACK, 0, false, true, 0f, 0.5f),
        new State("ATTACK_2", ZMove.ATTACK, 0, false, true, 0.5f, 1f),
        new State("ATTACK_3", ZMove.ATTACK, 0, false, true, 1f, 1.5f),
        new State("ATTACK_4", ZMove.ATTACK, 0, false, true, 1.5f, 2f),
        new State("DOWN", ZMove.DOWN, 0, false, false, 0, 0),
        new State("CRAWL_REST", ZMove.CRAWL_REST, 0, false, false, 0, 0),
        new State("CRAWL_SLOW", ZMove.CRAWL_SLOW, 0, false, false, 0, 0),
        new State("CRAWL_FAST", ZMove.CRAWL_FAST, 0, false, true, 0, 0),
        new State("CRAWL_ALERT", ZMove.CRAWL_ALERT, 0, false, true, 0, 0),
        new State("CRAWL_GRAB_1", ZMove.CRAWL_GRAB, 0, false, true, 0f, 1f),
        new State("CRAWL_GRAB_2", ZMove.CRAWL_GRAB, 0, false, true, 1f, 2f),
        new State("HOP", ZMove.HOP, 16, false, false, 0, 0),
        new State("HOP_REACH", ZMove.HOP, 16, false, true, 0, 0),
        new State("STAND1", ZMove.STAND1, 16, false, false, 0, 0),
        new State("STAND1_REACH", ZMove.STAND1, 16, false, true, 0, 0)};

    public static void main(String[] args) throws Exception {
        java.nio.file.Path out = java.nio.file.Path.of(args[0]);
        Set<String> only = new HashSet<>(Arrays.asList(args).subList(1, args.length));
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        List<Future<String>> jobs = new ArrayList<>();
        for (int i = 0; i < STATES.length; i++) {
            State s = STATES[i];
            float[] old = com.zomboidcraft.combat.BodyCaps.TABLES[i];
            if (!only.isEmpty() && !only.contains(s.name())) {
                jobs.add(CompletableFuture.completedFuture(table(s, old, old, null)));
                continue;
            }
            jobs.add(pool.submit(() -> {
                int gone = gone(s.cuts(), s.legless());
                Fit fit = new Fit(samples(FIT_OUTFITS, s, 3, 0f), gone);
                float[] t = fit.fit(start(s));
                List<HitFit.Body> test = samples(TEST_OUTFITS, s, 3, 0.5f);
                String note = "caught, right part, extra: " + HitFit.score(test, List.of(t), gone).brief()
                        + " (was " + HitFit.score(test, List.of(old), gone).brief() + ")";
                System.err.println(s.name() + ": " + note);
                return table(s, t, old, note);
            }));
        }
        StringBuilder tables = new StringBuilder(), names = new StringBuilder();
        for (int i = 0; i < STATES.length; i++) {
            tables.append(jobs.get(i).get()).append("\n");
            names.append(i == 0 ? "" : i % 5 == 0 ? ",\n            " : ", ").append("T_").append(STATES[i].name());
        }
        pool.shutdown();
        String text = HEAD + tables + "    public static final float[][] TABLES = {" + names + "};\n\n    private BodyCaps() {\n    }\n}\n";
        java.nio.file.Files.writeString(out, text);
    }

    /** One table's Java, with its scores (kept from the file when it wasn't refitted). */
    static String table(State s, float[] t, float[] old, String note) throws Exception {
        if (note == null) {
            // not refitted: keep its line from the file as it is
            String file = java.nio.file.Files.readString(java.nio.file.Path.of(
                    System.getProperty("bodycaps", "src/main/java/com/zomboidcraft/combat/BodyCaps.java")));
            int at = file.indexOf("    // " + s.name() + ":");
            note = at < 0 ? "" : file.substring(at + 7 + s.name().length() + 2, file.indexOf('\n', at));
        }
        return "    // " + s.name() + ": " + note + "\n" + java(s.name(), t) + "\n";
    }

    static int gone(int cuts, boolean legless) {
        int[] cutOf = {0, 0, 0, 0, 4, 1, 8, 2, 64, 16, 128, 32};
        int g = 0;
        for (int k = 0; k < 12; k++) {
            int c = cutOf[k];
            int above = c == 1 ? 4 : c == 2 ? 8 : c == 16 ? 64 : c == 32 ? 128 : 0;
            if (c != 0 && (cuts & (c | above)) != 0) g |= 1 << k;
        }
        if (legless) g |= 0xF << 8;
        return g;
    }

    static final String HEAD = """
            package com.zomboidcraft.combat;

            /**
             * The hit shapes of a walker's body in each pose the server tells apart (BodyTrack picks the pose as the
             * client's animator picks the movement): capsules fitted to the drawn body, clothes and all, over the
             * movement's whole cycle and from every side. Each table is 8 floats a capsule: the part (Dismember's
             * numbering: head 0, torso 1 and 2, pelvis 3, then right upper arm, forearm, left upper arm, forearm, right
             * thigh, shin, left thigh, shin), then from (x, y, z) to (x, y, z) and the radius, in blocks: x to the
             * zombie's left, y up from its feet, z ahead of it. The one-leg poses are drawn with the right leg gone.
             *
             * Made by tools/zombies/hitcaps/run.sh: don't edit by hand, rerun it when the body or its movements change.
             * Each table's note: of the shots that touch the drawn body, how many a capsule catches and how many on
             * the right part, and how many shots a capsule catches that miss the body (against all that touch it).
             */
            public final class BodyCaps {
                public static final int IDLE = 0, SHAMBLE = 1, ALERT = 2, CHASE = 3, ATTACK = 4, DOWN = 8, CRAWL_REST = 9,
                        CRAWL_SLOW = 10, CRAWL_FAST = 11, CRAWL_ALERT = 12, CRAWL_GRAB = 13, HOP = 15, HOP_REACH = 16,
                        STAND1 = 17, STAND1_REACH = 18;
                /** ATTACK is four tables, half a second of the swing each, and CRAWL_GRAB two, a second each. */
                public static final int ATTACK_STEPS = 4, CRAWL_GRAB_STEPS = 2;

                /** A pose on one leg, drawn with the right leg gone: a zombie on its right leg is the mirror image. */
                public static boolean oneLeg(int pose) {
                    return pose >= HOP;
                }

            """;
}
