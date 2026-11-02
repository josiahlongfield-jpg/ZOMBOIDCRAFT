package com.zomboidcraft.client.perf;

import com.zomboidcraft.zombie.Profiler;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * Graphics-card timers for {@code /zc profile}: how long each part of the frame takes on the card itself, which the
 * sampling profiler can't see (it only sees the CPU waiting). Each part gets a GL timestamp at its start and end;
 * the answers are read back a few frames later, when the card has finished them, so nothing waits. Runs only while a
 * profile is being taken.
 */
public final class GpuTimer {
    /** The parts, in report order. */
    public static final String FRAME = "Whole frame (world, hand, HUD)", WORLD = "World (everything but the HUD)",
            SHADOW = "Photon shadow pass (all of it)", SHADOW_TERRAIN = "  shadow pass: blocks and buildings",
            TERRAIN = "Blocks and buildings, solid (main view)", OBJECTS = "Solid pass, blocks and objects together (main view)",
            DEFERRED = "Photon lighting before glass and water (deferred)", TRANSLUCENT = "Glass, water and see-through blocks",
            COMPOSITE = "Photon after-effects and final pass (composite)";

    private static final String[] ORDER = {FRAME, WORLD, SHADOW, SHADOW_TERRAIN, OBJECTS, TERRAIN, DEFERRED, TRANSLUCENT, COMPOSITE};

    private record Pending(String label, int start, int end, long frame) {}

    private record Slow(long frame, long ns, Map<String, Long> parts) {}

    /** Each frame's parts while they come in, and the slowest whole frames of this profile. */
    private static final Map<Long, Map<String, Long>> perFrame = new java.util.HashMap<>();
    private static final java.util.List<Slow> worst = new java.util.ArrayList<>();
    private static final ArrayDeque<Integer> free = new ArrayDeque<>();
    private static final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private static final Map<String, int[]> open = new LinkedHashMap<>();
    private static final Map<String, long[]> totals = new LinkedHashMap<>();
    private static long frame, framesTimed;
    private static boolean wasOn, broken;
    public static boolean inShadow, afterDeferred;

    private GpuTimer() {
    }

    public static void install() {
        Profiler.gpuReport = GpuTimer::report;
    }

    private static boolean on() {
        return Profiler.gpuTiming && !broken;
    }

    /** At the start of a frame: collect finished answers, reset the pass flags. */
    public static void frameStart() {
        Profiler.frameNo++;
        inShadow = false;
        afterDeferred = false;
        boolean now = on();
        if (now && !wasOn) {
            synchronized (GpuTimer.class) {
                totals.clear();
                framesTimed = 0;
                worst.clear();
                perFrame.clear();
            }
        }
        wasOn = now;
        frame++;
        synchronized (GpuTimer.class) {
            try {
                collect(false);
            } catch (Throwable t) {
                broken = true;
            }
            if (now) {
                framesTimed++;
            }
        }
    }

    public static void begin(String label) {
        if (!on()) {
            return;
        }
        try {
            int q = query();
            GL33C.glQueryCounter(q, GL33C.GL_TIMESTAMP);
            int[] prev = open.put(label, new int[]{q});
            if (prev != null) {
                free.add(prev[0]);
            }
        } catch (Throwable t) {
            broken = true;
        }
    }

    public static void end(String label) {
        int[] s = open.remove(label);
        if (s == null) {
            return;
        }
        if (!on()) {
            free.add(s[0]);
            return;
        }
        try {
            int q = query();
            GL33C.glQueryCounter(q, GL33C.GL_TIMESTAMP);
            pending.add(new Pending(label, s[0], q, frame));
        } catch (Throwable t) {
            broken = true;
        }
    }

    private static int query() {
        Integer q = free.poll();
        return q != null ? q : GL15C.glGenQueries();
    }

    private static void collect(boolean all) {
        while (!pending.isEmpty()) {
            Pending p = pending.peek();
            if (!all && frame - p.frame < 3) {
                return;
            }
            if (GL15C.glGetQueryObjecti(p.end, GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) {
                if (!all && frame - p.frame < 20) {
                    return;
                }
            } else {
                long t0 = GL33C.glGetQueryObjecti64(p.start, GL15C.GL_QUERY_RESULT);
                long t1 = GL33C.glGetQueryObjecti64(p.end, GL15C.GL_QUERY_RESULT);
                long[] t = totals.computeIfAbsent(p.label, k -> new long[2]);
                long ns = Math.max(0, t1 - t0);
                t[0] += ns;
                t[1]++;
                Map<String, Long> parts = perFrame.computeIfAbsent(p.frame, k -> new LinkedHashMap<>());
                parts.merge(p.label, ns, Long::sum);
                if (p.label.equals(FRAME)) {
                    // the whole frame is the last part asked for in it: everything else of that frame is in by now
                    perFrame.remove(p.frame);
                    if (ns >= 100_000_000L) {
                        worst.add(new Slow(p.frame, ns, parts));
                        worst.sort((a, b) -> Long.compare(b.ns, a.ns));
                        if (worst.size() > 8) worst.remove(worst.size() - 1);
                    }
                    if (perFrame.size() > 64) perFrame.clear();
                }
            }
            pending.poll();
            free.add(p.start);
            free.add(p.end);
        }
    }

    /** Called from the profiler's thread at the end of a profile; reads the totals gathered on the render thread. */
    private static String report() {
        if (broken) {
            return "Graphics card timers: switched off (the driver refused a timer query)\n";
        }
        Map<String, long[]> snap;
        long frames;
        java.util.List<Slow> slow;
        synchronized (GpuTimer.class) {
            snap = new LinkedHashMap<>();
            totals.forEach((k, v) -> snap.put(k, v.clone()));
            frames = framesTimed;
            slow = new java.util.ArrayList<>(worst);
        }
        if (frames == 0 || snap.isEmpty()) {
            return "Graphics card timers: no frames timed\n";
        }
        StringBuilder r = new StringBuilder("\n==================== Graphics card time per frame (GPU timers) ====================\n");
        r.append(String.format("%d frames timed. Milliseconds on the card per frame, averaged; parts overlap (the shadow and"
                + " lighting lines are inside World, World inside Whole frame).%n", frames));
        long[] whole = snap.get(FRAME);
        double frameMs = whole == null ? 0 : whole[0] / 1e6 / Math.max(1, whole[1]);
        for (String k : ORDER) {
            long[] t = snap.get(k);
            if (t == null) {
                r.append(String.format("  %-58s not seen%n", k));
                continue;
            }
            double perFrame = t[0] / 1e6 / frames;
            r.append(String.format("  %-58s %7.2f ms%s  (%.1f calls a frame)%n", k, perFrame,
                    frameMs > 0 ? String.format("  %4.0f%%", 100 * perFrame / frameMs) : "", t[1] / (double) frames));
        }
        long[] sh = snap.get(SHADOW), st = snap.get(SHADOW_TERRAIN);
        if (sh != null && st != null) {
            r.append(String.format("  %-58s %7.2f ms%n", "  shadow pass: objects and the rest (shadow minus blocks)",
                    (sh[0] - st[0]) / 1e6 / frames));
        }
        if (!slow.isEmpty()) {
            r.append("-- The slowest frames on the card (100 ms or more), part by part\n");
            for (Slow w : slow) {
                r.append(String.format("  frame %d: %.0f ms:", w.frame, w.ns / 1e6));
                for (String k : ORDER) {
                    Long v = w.parts.get(k);
                    if (v != null && !k.equals(FRAME)) {
                        r.append(String.format(" %s %.0f;", k.trim().replaceAll(" \\(.*\\)", ""), v / 1e6));
                    }
                }
                r.append('\n');
            }
        }
        long[] so = snap.get(OBJECTS), te = snap.get(TERRAIN);
        if (so != null && te != null) {
            r.append(String.format("  %-58s %7.2f ms%n", "  objects: cars, people, props, guns (solid pass minus blocks)",
                    (so[0] - te[0]) / 1e6 / frames));
        }
        return r.toString();
    }
}
