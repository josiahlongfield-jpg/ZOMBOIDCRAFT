package com.zomboidcraft.zombie;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Our own sampling profiler, for when the frame rate sinks and nothing says why ({@code /zc profile [seconds]}).
 * A background thread looks at what the render thread, the server thread and the chunk/worldgen workers are doing
 * every few milliseconds, then writes a report to the game folder: where each thread spends its time by mod, by
 * our own classes, and by method, plus garbage collection and memory over the same window. Nothing is changed in the
 * game; the sampling costs a little while it runs.
 */
public final class Profiler {
    private static final long GAP_NANOS = 3_000_000L;
    private static volatile boolean running;
    /** Lit blocks that change their light (each one relights and redraws the chunks round it), counted while sampling. */
    public static final java.util.concurrent.atomic.LongAdder FLICKERS = new java.util.concurrent.atomic.LongAdder(),
            SIGNALS = new java.util.concurrent.atomic.LongAdder(),
            /** What the client was sent (ClientPacketListenerMixin): each one makes Sodium rebuild and re-upload a section. */
            CLIENT_BLOCKS = new java.util.concurrent.atomic.LongAdder(), CLIENT_SECTIONS = new java.util.concurrent.atomic.LongAdder(),
            CLIENT_LIGHT = new java.util.concurrent.atomic.LongAdder(), CLIENT_CHUNKS = new java.util.concurrent.atomic.LongAdder();

    /** How Sodium sends chunk meshes to the graphics card (SodiumUploadMixin); null until Sodium asks. */
    public static volatile String uploadPath;
    /** What GlBufferStorageMixin did when the graphics device was made; null if it never ran. */
    public static volatile String bufferStorageFix;
    /** Frames drawn so far; the client bumps it at the start of each frame (GpuTimer.frameStart). */
    public static volatile long frameNo;

    /** On while a profile runs: the client's GpuTimer times each part of the frame on the graphics card. */
    public static volatile boolean gpuTiming;
    /** The client's GPU timer report (GpuTimer), or null on a dedicated server. */
    public static volatile java.util.function.Supplier<String> gpuReport;

    /** Who owns a frame, by package: the first match wins, so put the narrower names first. */
    private static final String[][] OWNERS = {
            {"com.zomboidcraft.", "ZomboidCraft"},
            {"net.caffeinemc.", "Sodium"},
            {"me.jellysquid.", "Sodium"},
            {"net.irisshaders.", "Iris"},
            {"net.coderbot.", "Iris"},
            {"pigcart.particlerain", "Particle Rain"},
            {"me.pepperbell.continuity", "Continuity"},
            {"link.e4mc", "e4mc"},
            {"net.fabricmc.", "Fabric API"},
            {"org.lwjgl.", "LWJGL (OpenGL, GLFW)"},
            {"com.mojang.blaze3d.", "Minecraft render system"},
            {"net.minecraft.client.renderer.", "Minecraft rendering"},
            {"net.minecraft.client.", "Minecraft client"},
            {"net.minecraft.world.level.levelgen.", "Minecraft worldgen"},
            {"net.minecraft.world.level.chunk.", "Minecraft chunks"},
            {"net.minecraft.world.entity.", "Minecraft entities"},
            {"net.minecraft.server.level.", "Minecraft chunk loading"},
            {"net.minecraft.", "Minecraft other"},
            {"com.mojang.", "Mojang libraries"},
            {"io.netty.", "Networking"},
            {"java.", "Java"},
            {"jdk.", "Java"},
            {"sun.", "Java"},
    };

    /** What one watched thread did: counts per owner, per our class, per method on top, per method anywhere. */
    private static final class Tally {
        final String name;
        int samples, idle, blocked;
        /** Where it waited on a lock (synchronized), by our deepest frame or the top one. */
        final Map<String, Integer> locks = new HashMap<>();
        final Map<String, Integer> owner = new HashMap<>(), ours = new HashMap<>(), self = new HashMap<>(),
                inclusive = new HashMap<>(), oursSelf = new HashMap<>();

        Tally(String name) {
            this.name = name;
        }

        void add(Thread.State state, StackTraceElement[] stack) {
            samples++;
            if (stack.length == 0 || state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                idle++;
                return;
            }
            if (state == Thread.State.BLOCKED) {
                // waiting its turn for a lock: not work, but worth knowing where
                idle++;
                blocked++;
                StackTraceElement at = stack[0];
                for (StackTraceElement f : stack) {
                    if (f.getClassName().startsWith("com.zomboidcraft.")) {
                        at = f;
                        break;
                    }
                }
                locks.merge(method(at) + "  (line " + at.getLineNumber() + ")", 1, Integer::sum);
                return;
            }
            String top = method(stack[0]);
            self.merge(top, 1, Integer::sum);
            // the time is put on the deepest frame that names a mod, so Minecraft calling into Sodium counts as Sodium
            String who = null;
            for (StackTraceElement f : stack) {
                String o = owner(f.getClassName());
                if (o.equals("ZomboidCraft") || o.equals("Sodium") || o.equals("Iris") || o.equals("Particle Rain")
                        || o.equals("Continuity") || o.equals("e4mc")) {
                    who = o;
                    break;
                }
                if (who == null && !o.equals("Java") && !o.equals("LWJGL (OpenGL, GLFW)")) {
                    who = o;
                }
            }
            if (who == null) {
                who = owner(stack[0].getClassName());
            }
            owner.merge(who, 1, Integer::sum);
            Set<String> seen = new HashSet<>(), seenOurs = new HashSet<>();
            boolean firstOurs = true;
            for (StackTraceElement f : stack) {
                String m = method(f);
                if (seen.add(m)) {
                    inclusive.merge(m, 1, Integer::sum);
                }
                if (f.getClassName().startsWith("com.zomboidcraft.")) {
                    String c = f.getClassName().substring("com.zomboidcraft.".length());
                    int dollar = c.indexOf('$');
                    if (dollar > 0) {
                        c = c.substring(0, dollar);
                    }
                    if (seenOurs.add(c)) {
                        ours.merge(c, 1, Integer::sum);
                    }
                    if (firstOurs) {
                        oursSelf.merge(m + "  (line " + f.getLineNumber() + ")", 1, Integer::sum);
                        firstOurs = false;
                    }
                }
            }
        }
    }

    /** What a render-thread sample was doing, short: our deepest line (if any) and the top frame. */
    private static String hitchKey(StackTraceElement[] stack) {
        String top = method(stack[0]);
        String mod = null;
        for (StackTraceElement f : stack) {
            if (f.getClassName().startsWith("com.zomboidcraft.")) {
                return method(f) + " (line " + f.getLineNumber() + ")  <- top: " + top;
            }
            String o = owner(f.getClassName());
            if (mod == null && (o.equals("Sodium") || o.equals("Iris") || o.equals("Particle Rain"))) {
                mod = o + " " + method(f);
            }
        }
        return (mod != null ? mod : "") + "  <- top: " + top + (stack.length > 1 ? " <- " + method(stack[1]) : "")
                + (stack.length > 2 ? " <- " + method(stack[2]) : "");
    }

    /**
     * Freezes: frames the render thread spent 150 ms or more on, longest first, with what it was doing in them. The
     * averages above hide a quarter-second stall among thousands of normal frames; this shows them one by one.
     */
    private static String hitches(List<Long> frames, List<Long> times, List<String> keys) {
        StringBuilder r = new StringBuilder("\n==================== Freezes (frames of 150 ms or more) ====================\n");
        if (frames.isEmpty() || frames.get(0).equals(frames.get(frames.size() - 1))) {
            return r.append("No frame count from the client (dedicated server or no frames drawn).\n").toString();
        }
        record Hitch(long frame, long ms, Map<String, Integer> keys) {}
        List<Hitch> list = new ArrayList<>();
        Map<String, Integer> all = new HashMap<>();
        int i = 0;
        while (i < frames.size()) {
            int j = i;
            while (j < frames.size() && frames.get(j).equals(frames.get(i))) {
                j++;
            }
            long ms = (times.get(j - 1) - times.get(i)) / 1_000_000L;
            if (ms >= 150 && i > 0 && j < frames.size()) {
                Map<String, Integer> k = new HashMap<>();
                for (int n = i; n < j; n++) {
                    k.merge(keys.get(n), 1, Integer::sum);
                    all.merge(keys.get(n), 1, Integer::sum);
                }
                list.add(new Hitch(frames.get(i), ms, k));
            }
            i = j;
        }
        long total = list.stream().mapToLong(Hitch::ms).sum();
        r.append(String.format("%d freezes, %.1f s frozen in all%n", list.size(), total / 1000.0));
        if (list.isEmpty()) {
            return r.toString();
        }
        int samples = all.values().stream().mapToInt(Integer::intValue).sum();
        r.append("-- Across all freezes, what the render thread was doing\n");
        for (Map.Entry<String, Integer> e : top(all, 15)) {
            r.append(String.format("%6.2f%%  %s%n", 100.0 * e.getValue() / samples, e.getKey()));
        }
        list.sort((a, b) -> Long.compare(b.ms, a.ms));
        for (Hitch h : list.subList(0, Math.min(8, list.size()))) {
            int n = h.keys.values().stream().mapToInt(Integer::intValue).sum();
            r.append(String.format("-- Freeze of about %d ms (frame %d)%n", h.ms, h.frame));
            for (Map.Entry<String, Integer> e : top(h.keys, 5)) {
                r.append(String.format("%6.1f%%  %s%n", 100.0 * e.getValue() / n, e.getKey()));
            }
        }
        return r.toString();
    }

    private static String owner(String cls) {
        for (String[] o : OWNERS) {
            if (cls.startsWith(o[0])) {
                return o[1];
            }
        }
        return cls.contains(".") ? cls.substring(0, cls.lastIndexOf('.')) : cls;
    }

    private static String method(StackTraceElement f) {
        String c = f.getClassName();
        return c.substring(c.lastIndexOf('.') + 1) + "." + f.getMethodName();
    }

    /** Which bucket a thread falls in, or null to leave it out. */
    private static String watch(Thread t) {
        String n = t.getName();
        if (n.equals("Render thread")) {
            return "Render thread (your frame rate)";
        }
        if (n.equals("Server thread")) {
            return "Server thread (ticks: zombies, chunks, world)";
        }
        if (n.startsWith("Worker-Main") || n.startsWith("Worker-")) {
            return "Worldgen and chunk workers (all together)";
        }
        if (n.contains("Chunk Render") || n.contains("Chunk Builder") || n.startsWith("Sodium")) {
            return "Sodium chunk builders (all together)";
        }
        if (n.startsWith("ZC ") || n.startsWith("zomboidcraft") || n.startsWith("ZomboidCraft")) {
            return "ZomboidCraft background threads (all together)";
        }
        return null;
    }

    public static boolean running() {
        return running;
    }

    /**
     * Samples for {@code seconds}, then writes the report and hands {@code done} a short summary for chat (on the
     * sampling thread: hop to the server before touching the game).
     */
    public static boolean start(int seconds, Consumer<String> done) {
        if (running) {
            return false;
        }
        running = true;
        gpuTiming = true;
        Thread sampler = new Thread(() -> {
            try {
                done.accept(run(seconds));
            } catch (Throwable e) {
                done.accept("The profiler failed: " + e);
            } finally {
                running = false;
                gpuTiming = false;
            }
        }, "ZomboidCraft profiler");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.MAX_PRIORITY);
        sampler.start();
        return true;
    }

    private static String run(int seconds) throws IOException {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        long[] gcCount0 = new long[gcs.size()], gcTime0 = new long[gcs.size()];
        for (int i = 0; i < gcs.size(); i++) {
            gcCount0[i] = gcs.get(i).getCollectionCount();
            gcTime0[i] = gcs.get(i).getCollectionTime();
        }
        MemoryUsage heap0 = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long heapPeak = heap0.getUsed();
        long start = System.nanoTime(), end = start + seconds * 1_000_000_000L;
        long late = 0, rounds = 0;
        FLICKERS.reset();
        SIGNALS.reset();
        CLIENT_BLOCKS.reset();
        CLIENT_SECTIONS.reset();
        CLIENT_LIGHT.reset();
        CLIENT_CHUNKS.reset();
        List<Thread> threads = new ArrayList<>();
        List<String> buckets = new ArrayList<>();
        List<Long> hitchFrames = new ArrayList<>(), hitchTimes = new ArrayList<>();
        List<String> hitchKeys = new ArrayList<>();
        long refound = 0;
        while (System.nanoTime() < end) {
            long now = System.nanoTime();
            if (now - refound > 1_000_000_000L) {
                // workers come and go, so the list is looked at again each second
                threads.clear();
                buckets.clear();
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    String b = t == Thread.currentThread() ? null : watch(t);
                    if (b != null) {
                        threads.add(t);
                        buckets.add(b);
                    }
                }
                refound = now;
                heapPeak = Math.max(heapPeak, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            }
            for (int i = 0; i < threads.size(); i++) {
                Thread t = threads.get(i);
                if (!t.isAlive()) {
                    continue;
                }
                Thread.State state = t.getState();
                long frame = frameNo;
                StackTraceElement[] stack = t.getStackTrace();
                tallies.computeIfAbsent(buckets.get(i), Tally::new).add(state, stack);
                if (buckets.get(i).startsWith("Render") && stack.length > 0) {
                    hitchFrames.add(frame);
                    hitchKeys.add(hitchKey(stack));
                    hitchTimes.add(now);
                }
            }
            rounds++;
            long spent = System.nanoTime() - now;
            if (spent > GAP_NANOS) {
                late++;
                continue;
            }
            try {
                Thread.sleep((GAP_NANOS - spent) / 1_000_000L, (int) ((GAP_NANOS - spent) % 1_000_000L));
            } catch (InterruptedException e) {
                break;
            }
        }
        double took = (System.nanoTime() - start) / 1e9;

        StringBuilder r = new StringBuilder();
        r.append("ZomboidCraft profile, ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append(String.format("%nSampled %.1f s, %d rounds (about every %.1f ms; %d ran late)%n", took, rounds,
                        took * 1000 / Math.max(1, rounds), late));
        Runtime rt = Runtime.getRuntime();
        MemoryUsage heap1 = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        r.append(String.format("Memory: %d MB used now, %d MB peak seen, %d MB max allowed (-Xmx), %d cores%n",
                heap1.getUsed() >> 20, Math.max(heapPeak, heap1.getUsed()) >> 20, rt.maxMemory() >> 20,
                rt.availableProcessors()));
        long gcMs = 0;
        for (int i = 0; i < gcs.size(); i++) {
            long n = gcs.get(i).getCollectionCount() - gcCount0[i], ms = gcs.get(i).getCollectionTime() - gcTime0[i];
            gcMs += ms;
            r.append(String.format("Garbage collector %s: %d runs, %d ms%n", gcs.get(i).getName(), n, ms));
        }
        r.append(String.format("Garbage collection took %.1f%% of the time%n", gcMs / 10.0 / Math.max(0.1, took)));
        r.append(String.format("Flickering lamps changed %d times (%.0f a second), traffic signals %d times%n",
                FLICKERS.sum(), FLICKERS.sum() / Math.max(0.1, took), SIGNALS.sum()));
        r.append(String.format("Sent to your client: %d single block changes, %d section block changes, %d light updates, %d chunks loaded%n",
                CLIENT_BLOCKS.sum(), CLIENT_SECTIONS.sum(), CLIENT_LIGHT.sum(), CLIENT_CHUNKS.sum()));
        r.append("NVIDIA buffer storage fix: ").append(bufferStorageFix == null ? "did not run" : bufferStorageFix).append("\n");
        r.append("Sodium chunk uploads: ").append(uploadPath == null ? "not seen (Sodium missing or idle)" : uploadPath).append("\n");
        r.append(hitches(hitchFrames, hitchTimes, hitchKeys));
        java.util.function.Supplier<String> gpu = gpuReport;
        if (gpu != null) {
            r.append(gpu.get());
        }
        r.append("JVM: ").append(System.getProperty("java.vm.name")).append(' ')
                .append(System.getProperty("java.version")).append(", args ")
                .append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append("\n");
        r.append("Reading it: on the render thread, time in LWJGL/blaze3d swap or draw calls is mostly waiting on the"
                + " graphics card (or the shader pack); time in mods or Minecraft code is the CPU.\n");

        StringBuilder chat = new StringBuilder();
        for (Tally t : tallies.values()) {
            int busy = t.samples - t.idle;
            r.append("\n==================== ").append(t.name).append(" ====================\n");
            r.append(String.format("%d samples, %d busy (%.0f%%), %d waiting (%d of them on a lock)%n", t.samples,
                    busy, 100.0 * busy / Math.max(1, t.samples), t.idle, t.blocked));
            if (t.blocked > 0) {
                section(r, "Waiting on a lock, where (share of the lock waits)", t.locks, t.blocked, 15);
            }
            if (busy == 0) {
                continue;
            }
            section(r, "Busy time by mod (the deepest frame that names a mod)", t.owner, busy, 30);
            section(r, "Busy time inside our classes (anywhere on the stack)", t.ours, busy, 40);
            section(r, "Our deepest frame (the line of ours closest to the work)", t.oursSelf, busy, 40);
            section(r, "Busy time by method on top (self)", t.self, busy, 50);
            section(r, "Busy time by method anywhere on the stack (inclusive)", t.inclusive, busy, 80);
            if (t.name.startsWith("Render") || t.name.startsWith("Server")) {
                chat.append(t.name.substring(0, t.name.indexOf(' ', 7))).append(": ");
                top(t.owner, 4).forEach(e -> chat.append(e.getKey()).append(' ')
                        .append(Math.round(100.0 * e.getValue() / busy)).append("%, "));
                chat.setLength(chat.length() - 2);
                chat.append('\n');
            }
        }

        Path dir = FabricLoader.getInstance().getGameDir();
        Path file = dir.resolve("zomboidcraft-profile-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        Files.writeString(file, r.toString());
        return chat + String.format("GC %.1f%% of the time, %d MB peak of %d MB%n", gcMs / 10.0 / Math.max(0.1, took),
                Math.max(heapPeak, heap1.getUsed()) >> 20, rt.maxMemory() >> 20)
                + "Saved " + file.toAbsolutePath();
    }

    private static List<Map.Entry<String, Integer>> top(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        return l.subList(0, Math.min(n, l.size()));
    }

    private static void section(StringBuilder r, String title, Map<String, Integer> m, int busy, int n) {
        r.append("\n-- ").append(title).append('\n');
        for (Map.Entry<String, Integer> e : top(m, n)) {
            r.append(String.format("%6.2f%%  %6d  %s%n", 100.0 * e.getValue() / busy, e.getValue(), e.getKey()));
        }
    }

    private Profiler() {
    }
}
