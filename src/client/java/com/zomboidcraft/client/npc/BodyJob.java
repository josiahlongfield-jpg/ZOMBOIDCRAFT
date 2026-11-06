package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * One skinned body's drawing work, done on a worker thread while the rest of the frame is still being gathered: each of
 * its meshes moved by its bones, then written out as finished vertices (FastVerts) in the layout the draw buffers took
 * last frame. When the frame is drawn the render thread only copies them in. With a horde that work was a third of
 * the render thread, all of it on one of Josiah's 16 cores (2026-11-03, 600 zombies at 12 FPS).
 * <p>
 * If the buffer's layout has changed since (a shader pack switched), the vertices are written afresh from the skinned
 * meshes, which are still good. performance.parallelBodies turns it off.
 */
final class BodyJob implements Runnable {
    /** Workers: half the cores, leaving the rest to Minecraft, Sodium's chunk builders and the server. */
    private static final ForkJoinPool POOL = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            pool -> {
                var t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("ZomboidCraft body " + t.getPoolIndex());
                t.setDaemon(true);
                return t;
            }, null, false);

    private static boolean warned;

    /** One mesh of the body. */
    record Part(NpcSkin.Group group, int[] quads, float[] pos, float[] normal, boolean back) {
    }

    private final float[] bones;
    private final List<Part> parts = new ArrayList<>(8);
    private final Matrix4f pose;
    private final Matrix3f normal;
    private final int light, overlay;
    private final FastVerts.Layout layout;
    private final int[] ids;
    private int[] offset;
    private long mem;
    private final AtomicInteger left = new AtomicInteger();
    private ForkJoinTask<?> task;
    private volatile boolean written;
    final long frame;

    BodyJob(float[] bones, PoseStack.Pose at, int light, int overlay, long frame) {
        this.bones = bones;
        this.pose = new Matrix4f(at.pose());
        this.normal = new Matrix3f(at.normal());
        this.light = light;
        this.overlay = overlay;
        this.layout = FastVerts.guess();
        this.ids = FastVerts.lastIds;
        this.frame = frame;
    }

    /** Adds a mesh; returns its index for emit. */
    int add(Part p) {
        parts.add(p);
        left.incrementAndGet();
        return parts.size() - 1;
    }

    private boolean started;

    void start() {
        if (task != null || parts.isEmpty()) return;
        started = true;
        task = POOL.submit(this);
    }

    @Override
    public void run() {
        try {
            for (Part p : parts) {
                if (bones != null) NpcSkinDraw.skin(p.group(), bones, p.pos(), p.normal());
            }
            FastVerts.Layout l = layout;
            if (l == null) return;
            offset = new int[parts.size() + 1];
            for (int i = 0; i < parts.size(); i++) {
                Part p = parts.get(i);
                offset[i + 1] = offset[i] + FastVerts.bytes(l, p.quads().length, p.back());
            }
            if (offset[parts.size()] == 0) return;
            mem = MemoryUtil.nmemAlloc(offset[parts.size()]);
            if (mem == 0) return;
            for (int i = 0; i < parts.size(); i++) {
                Part p = parts.get(i);
                FastVerts.fill(mem + offset[i], l, pose, normal, p.quads(), 0, p.quads().length, p.back(), p.pos(),
                        p.normal(), p.group().uv, light, overlay, ids[0], ids[1], ids[2]);
            }
            written = true;
        } catch (Throwable t) {
            written = false;
            if (!warned) {
                warned = true;
                ZomboidCraft.LOGGER.error("Body drawing on a worker failed; written on the render thread instead", t);
            }
        }
    }

    /** Draws mesh i into the consumer (render thread, when the frame is drawn). */
    void emit(int i, PoseStack.Pose at, VertexConsumer out) {
        Part p = parts.get(i);
        if (!started) {
            // drawn straight away, before the job was started (a collector that draws as it's given things): done here
            if (bones != null) NpcSkinDraw.skin(p.group(), bones, p.pos(), p.normal());
            NpcSkinDraw.drawSkinned(p.group(), p.pos(), p.normal(), p.back(), at != null ? at : own(), out, light, overlay);
            left.decrementAndGet();
            return;
        }
        task.join();
        try {
            if (!(written && FastVerts.copy(out, layout, mem + offset[i], offset[i + 1] - offset[i], ids))) {
                NpcSkinDraw.drawSkinned(p.group(), p.pos(), p.normal(), p.back(), at != null ? at : own(), out, light, overlay);
            }
        } finally {
            if (left.decrementAndGet() == 0) free();
        }
    }

    /** Its own pose, for drawing the old way when it was handed in with others (a batch's pose is the first one's). */
    private PoseStack.Pose own() {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(pose);
        ps.last().normal().set(normal);
        return ps.last();
    }

    // ------------------------------------------------------------------ one submit per texture

    /**
     * Every body mesh with the same texture handed to the same collector this frame, drawn by one submit: a horde
     * submitted thousands of little ones, and sorting them into their render types was 14% of the frame (Minecraft
     * comparing render types, 2026-11-03 profile). The meshes still each carry their own pose.
     */
    static final class Batch {
        private final List<BodyJob> jobs = new ArrayList<>();
        private final it.unimi.dsi.fastutil.ints.IntArrayList index = new it.unimi.dsi.fastutil.ints.IntArrayList();
        private boolean emitted;

        void emit(VertexConsumer out) {
            emitted = true;
            for (int k = 0; k < jobs.size(); k++) jobs.get(k).emit(index.getInt(k), null, out);
        }
    }

    private static final java.util.Map<Object, java.util.Map<Object, Batch>> BATCHES = new java.util.IdentityHashMap<>();
    private static long batchFrame = Long.MIN_VALUE;

    /**
     * The batch to add mesh i of job to (render thread); null when one was opened, and the caller must hand it to the
     * collector with the given submit.
     */
    static Batch batch(Object collector, Object key, long frame, java.util.function.Consumer<Batch> submit, BodyJob job, int i) {
        if (frame != batchFrame) {
            batchFrame = frame;
            BATCHES.clear();
        }
        java.util.Map<Object, Batch> byKey = BATCHES.computeIfAbsent(collector, k -> new java.util.HashMap<>());
        Batch b = byKey.get(key);
        if (b == null || b.emitted) {
            b = new Batch();
            byKey.put(key, b);
            submit.accept(b);
        }
        b.jobs.add(job);
        b.index.add(i);
        return b;
    }

    /** A job whose meshes weren't all drawn (a frame cut short): waited for and let go. */
    void abandon() {
        if (task != null) task.join();
        written = false;
        free();
    }

    boolean done() {
        return left.get() <= 0;
    }

    private void free() {
        long m = mem;
        mem = 0;
        if (m != 0) MemoryUtil.nmemFree(m);
    }

    // ------------------------------------------------------------------ jobs still out

    private static final List<BodyJob> LIVE = new ArrayList<>();
    private static long sweptFrame = Long.MIN_VALUE;

    /** Keeps track of a started job; once a frame, lets go of ones from two frames back that were never drawn. */
    static void track(BodyJob job) {
        if (!job.started) return;
        if (job.frame != sweptFrame) {
            sweptFrame = job.frame;
            LIVE.removeIf(j -> {
                if (j.done()) return true;
                if (j.frame < job.frame - 1) {
                    j.abandon();
                    return true;
                }
                return false;
            });
        }
        LIVE.add(job);
    }
}
