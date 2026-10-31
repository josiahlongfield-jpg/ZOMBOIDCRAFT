package com.zomboidcraft.creature.spider;

import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Plays the Harvestman's own animations from what its entity is doing, one per Harvestman (client only). Underneath,
 * its moving: standing (a calm idle, now and then another one; a risen, alert one while it listens to something), its
 * walk forward, back and to either side and its run, blended by which way and how fast its body is really going, their
 * feet kept in step and played as fast as the ground goes past; turning on the spot when it swings round without
 * walking; dragging something it always walks it on ahead (SpiderIk closes its arms round it and bends it over it to
 * feed). Over that, one move played through and faded out: a strike when it
 * rears to lunge, its rage whenever it rages at someone, a flinch when it's hit, being thrown back
 * when it's shot in the eyes, and its death, held at the end.
 */
public final class SpiderAnimator {
    /** How fast the feet go back along the ground in the walk and the run (metres a second), and each one's stride. */
    private static final float WALK_SPEED = 0.80f, RUN_SPEED = 3.03f;
    private static final float WALK_STRIDE = WALK_SPEED * 4f / 3f, RUN_STRIDE = RUN_SPEED * 2f / 3f;
    private static final String[] CALM = {"Idle_2", "Idle_5", "Idle_8"};

    private final SpiderModel model;
    /** Seconds since the frame before (what SpiderIk eases by). */
    float dt;
    /** Its own, so the server's posing never draws on the entity's (its mind's). */
    private final java.util.Random rand = new java.util.Random();
    private float time;
    private Vec3 lastAt;
    private Vector3f lastFwd;
    /** Its speed over the ground in its own frame (x its left, z ahead), and how fast it's turning (+ to its left). */
    private final Vector3f vel = new Vector3f();
    private float yawRate;
    private float phase;
    /** What each base clip is weighted now (eased toward what's wanted). */
    private float wIdle = 1, wWalk, wBack, wLeft, wRight, wRun, wTurnL, wTurnR, wFall;
    /** How fast it's going along its up (falling, dropping off a ceiling, is fast and down). */
    private float climb;
    private float hz = 0.6f;
    private boolean walking, turning;
    private String idle = "Idle_1", idleOld;
    private float idleT, idleOldT, idleFade = 1, turnT;
    /** The move played over the top. */
    private String act;
    private float actT, actRate = 1, actPeak = 1;
    private boolean actHold;
    private int flinchSeen = -1, recoilSeen = -1, rageSeen = -1;
    private float rearWas;
    private int poseWas = -1;
    private boolean hitAlt;
    /** The last frame posed (so the shadow pass doesn't pose it again), and each bone's skinning matrix from it. */
    private float posedAt = Float.NaN;
    public final Matrix4f[] skin;
    /** The pose this frame: each bone's turn and position in model space (SpiderIk works on these before bake). */
    final Quaternionf[] q;
    public final Vector3f[] p;
    private final float[] sum;

    public SpiderAnimator(SpiderModel model) {
        this.model = model;
        int n = model.bones();
        skin = new Matrix4f[n];
        q = new Quaternionf[n];
        p = new Vector3f[n];
        sum = new float[n];
        for (int i = 0; i < n; i++) {
            skin[i] = new Matrix4f();
            q[i] = new Quaternionf();
            p[i] = new Vector3f();
        }
    }

    /**
     * Brings it up to now and poses it; false if it was already posed this frame.
     *
     * @param at where it's drawn this frame
     * @param turn how it lies (model to world)
     * @param frameKey this frame (tick plus partial tick)
     * @param dt seconds since the last one
     */
    public boolean update(HarvestmanEntity e, Vec3 at, Quaternionf turn, float frameKey, float dt) {
        if (frameKey == posedAt) {
            return false;
        }
        posedAt = frameKey;
        dt = Math.min(dt, 0.1f);
        this.dt = dt;
        time += dt;
        Vector3f fwd = turn.transform(new Vector3f(0, 0, 1));
        Vector3f up = turn.transform(new Vector3f(0, 1, 0));
        if (lastAt != null && dt > 0) {
            Vec3 d = at.subtract(lastAt).scale(1 / dt);
            Vector3f local = new Quaternionf(turn).conjugate().transform(new Vector3f((float) d.x, (float) d.y, (float) d.z));
            local.y = 0;
            // eased well, so a body that shuffles or stops and starts doesn't flicker between its walk and its idle
            float k = 1 - (float) Math.exp(-dt * 4);
            vel.lerp(local, k);
            float rise = (float) (d.x * up.x + d.y * up.y + d.z * up.z);
            climb = Mth.lerp(1 - (float) Math.exp(-dt * 10), climb, rise);
            float swing = (float) Math.atan2(new Vector3f(lastFwd).cross(fwd).dot(up), lastFwd.dot(fwd)) / dt;
            yawRate = Mth.lerp(1 - (float) Math.exp(-dt * 3), yawRate, swing);
        }
        lastAt = at;
        lastFwd = fwd;

        int pose = e.pose();
        boolean dead = pose == HarvestmanEntity.DEAD, feeding = pose == HarvestmanEntity.FEED;
        triggers(e, pose);

        // what's wanted underneath
        float speed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        // it walks once it's really going somewhere; under that it stands (its idles shift its weight themselves),
        // and once walking it keeps on down to a slower pace, so it doesn't flick between the two
        walking = speed > (walking ? 0.12f : 0.3f);
        float move = dead || feeding || !walking ? 0 : smooth(0.12f, 0.45f, speed);
        float run = smooth(1.4f, 2.6f, speed);
        float c = speed > 1e-3f ? vel.z / speed : 1, s = speed > 1e-3f ? vel.x / speed : 0;
        // which way: mostly one clip (the walks' feet fall at different times, so an even mix shuffles)
        float f = (float) Math.pow(Math.max(0, c), 4), b = (float) Math.pow(Math.max(0, -c), 4);
        float l = (float) Math.pow(Math.max(0, s), 4), r = (float) Math.pow(Math.max(0, -s), 4);
        if (pose == HarvestmanEntity.HOLD) {
            // dragging something it walks it on ahead of it (it goes the way it faces), never backing or sidling
            f = 1;
            b = l = r = 0;
        }
        float all = Math.max(1e-4f, f + b + l + r);
        f /= all;
        b /= all;
        l /= all;
        r /= all;
        turning = Math.abs(yawRate) > (turning ? 0.45f : 0.8f);
        float spin = dead || feeding || !turning ? 0 : (1 - move) * smooth(0.45f, 1.2f, Math.abs(yawRate));
        float still = 1 - move - spin;
        float k = 1 - (float) Math.exp(-dt * 4);
        wWalk = Mth.lerp(k, wWalk, move * f * (1 - run));
        wRun = Mth.lerp(k, wRun, move * f * run);
        wBack = Mth.lerp(k, wBack, move * b);
        wLeft = Mth.lerp(k, wLeft, move * l);
        wRight = Mth.lerp(k, wRight, move * r);
        wTurnL = Mth.lerp(k, wTurnL, yawRate > 0 ? spin : 0);
        wTurnR = Mth.lerp(k, wTurnR, yawRate > 0 ? 0 : spin);
        // feeding it stands over what it feeds on (SpiderIk bends it down to it), breathing in its idle
        wIdle = Mth.lerp(k, wIdle, feeding ? 1 : Math.max(0, still));
        // dropping through the air: legs drawn in as for its leap
        boolean falling = !dead && climb < -3.5f;
        wFall = Mth.lerp(k, wFall, falling ? 1 : 0);

        // the feet in step with the ground: one stride a cycle, the walk's or the run's
        float stride = Mth.lerp(run, WALK_STRIDE, RUN_STRIDE);
        float hzWant = move > 0.01f ? Mth.clamp(speed / stride, 0.35f, 2.4f) : 0.6f;
        hz = Mth.lerp(1 - (float) Math.exp(-dt * 3), hz, hzWant);
        phase = (phase + dt * hz) % 1f;
        turnT += dt * Mth.clamp(Math.abs(yawRate) / 1.2f, 0.6f, 2f);
        idleT += dt;
        idleOldT += dt;
        idleFade = Math.min(1, idleFade + dt / 0.5f);
        SpiderModel.Clip idleClip = model.clips.get(idle);
        if (idleClip != null && idleT >= idleClip.seconds()) {
            boolean alert = e.lookingAt() != null;
            String next = alert ? "Idle_9" : rand.nextFloat() < 0.65f ? "Idle_1" : CALM[rand.nextInt(CALM.length)];
            if (next.equals(idle)) {
                idleT -= idleClip.seconds();
            } else {
                idleOld = idle;
                idleOldT = idleT;
                idle = next;
                idleT = 0;
                idleFade = 0;
            }
        }

        // underneath, blended
        for (int i = 0; i < sum.length; i++) {
            q[i].set(0, 0, 0, 0);
            p[i].zero();
            sum[i] = 0;
        }
        add(idle, idleT, true, wIdle * idleFade);
        if (idleOld != null && idleFade < 1) {
            add(idleOld, idleOldT, true, wIdle * (1 - idleFade));
        }
        addPhase("Walk_1", wWalk);
        addPhase("Run_1", wRun);
        addPhase("Walk_back_1", wBack);
        addPhase("Walk_left", wLeft);
        addPhase("Walk_Right", wRight);
        add("Turn_Left", turnT, true, wTurnL);
        add("Turn_Right", turnT, true, wTurnR);
        SpiderModel.Clip jump = model.clips.get("Jump_1");
        if (jump != null && wFall > 1e-3f) {
            add(jump, jump.seconds() * 0.5f, wFall);
        }
        for (int i = 0; i < sum.length; i++) {
            if (sum[i] < 1e-4f) {
                add("Idle_1", idleT, true, 1);
                break;
            }
        }
        for (int i = 0; i < sum.length; i++) {
            q[i].normalize();
            p[i].div(sum[i]);
        }
        // the move over the top
        if (act != null) {
            SpiderModel.Clip clip = model.clips.get(act);
            if (clip == null) {
                act = null;
            } else {
                actT += dt * actRate;
                float len = clip.seconds();
                if (!actHold && actT >= len) {
                    act = null;
                } else {
                    float w = actPeak * Math.min(1, actT / 0.15f) * (actHold ? 1 : Math.min(1, (len - actT) / 0.3f));
                    blendOver(clip, Math.min(actT, len), w);
                }
            }
        }

        return true;
    }

    /** How far into its rage it is, 0..1..0 (its whole skull lights up). */
    public float rage() {
        if (!"Rage_1".equals(act)) {
            return 0;
        }
        SpiderModel.Clip clip = model.clips.get(act);
        float len = clip == null ? 1 : clip.seconds();
        return Math.min(1, actT / 0.4f) * Math.min(1, (len - actT) / 0.6f);
    }

    /** Whether it's in the air (its feet shouldn't look for the ground). */
    public boolean airborne() {
        return wFall > 0.5f;
    }

    /** Each bone's skinning matrix from the pose as it now stands. */
    public void bake() {
        for (int i = 0; i < skin.length; i++) {
            skin[i].translationRotate(p[i].x, p[i].y, p[i].z, q[i]).mul(model.inverse[i]);
        }
    }

    /** Starts a move when its entity does something new. */
    private void triggers(HarvestmanEntity e, int pose) {
        if (pose == HarvestmanEntity.DEAD) {
            if (poseWas != HarvestmanEntity.DEAD) {
                play((e.getId() & 1) == 0 ? "Death_1" : "Death_2", 1, 1, true);
            }
            poseWas = pose;
            return;
        }
        if (poseWas == HarvestmanEntity.DEAD) {
            act = null;
        }
        poseWas = pose;
        // its rage, when it rages at someone (to show it's seen them, before it strikes, hurt, at random)
        if (rageSeen >= 0 && e.rages() != rageSeen) {
            play("Rage_1", 161f / 30 / (HarvestmanEntity.RAGE_TICKS / 20f), 1, false);
        }
        rageSeen = e.rages();
        float rear = e.rear();
        boolean raging = "Rage_1".equals(act);
        if (pose == HarvestmanEntity.HUNT && rear > 0.45f && rearWas <= 0.45f && !actHold && !raging) {
            play(rand.nextBoolean() ? "Attack_6" : "Attack_7", 1.25f, 1, false);
        }
        rearWas = rear;
        if (recoilSeen >= 0 && e.recoils() != recoilSeen) {
            play("Hit_2", 1, 1, false);
        } else if (flinchSeen >= 0 && e.flinches() != flinchSeen && (act == null || act.startsWith("Hit"))) {
            hitAlt = !hitAlt;
            play(hitAlt ? "Hit_1" : "Hit_2", 1.2f, 0.7f, false);
        }
        recoilSeen = e.recoils();
        flinchSeen = e.flinches();
    }

    private void play(String clip, float rate, float peak, boolean hold) {
        act = clip;
        actT = 0;
        actRate = rate;
        actPeak = peak;
        actHold = hold;
    }

    private void addPhase(String clip, float w) {
        SpiderModel.Clip c = model.clips.get(clip);
        if (c != null && w > 1e-3f) {
            add(c, phase * c.seconds(), w);
        }
    }

    private void add(String clip, float t, boolean loop, float w) {
        SpiderModel.Clip c = model.clips.get(clip);
        if (c != null && w > 1e-3f) {
            add(c, loop ? t % c.seconds() : Math.min(t, c.seconds()), w);
        }
    }

    private final Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
    private final Vector3f pa = new Vector3f(), pb = new Vector3f();

    /** Adds a clip at a time into the blend, each bone's turn kept on the same side as what's already there. */
    private void add(SpiderModel.Clip c, float t, float w) {
        for (int i = 0; i < sum.length; i++) {
            sample(c, t, i, qa, pa);
            if (q[i].x * qa.x + q[i].y * qa.y + q[i].z * qa.z + q[i].w * qa.w < 0) {
                w = -w;
            }
            q[i].x += qa.x * w;
            q[i].y += qa.y * w;
            q[i].z += qa.z * w;
            q[i].w += qa.w * w;
            w = Math.abs(w);
            p[i].add(pa.x * w, pa.y * w, pa.z * w);
            sum[i] += w;
        }
    }

    private void blendOver(SpiderModel.Clip c, float t, float w) {
        for (int i = 0; i < sum.length; i++) {
            sample(c, t, i, qa, pa);
            q[i].slerp(qa, w);
            p[i].lerp(pa, w);
        }
    }

    /** One bone of a clip at a time, between its two nearest frames. */
    private void sample(SpiderModel.Clip c, float t, int bone, Quaternionf qOut, Vector3f pOut) {
        float f = t * c.fps();
        int f0 = Mth.clamp((int) f, 0, c.frames() - 1), f1 = Math.min(f0 + 1, c.frames() - 1);
        float u = Mth.clamp(f - f0, 0, 1);
        int n = c.turn().length / c.frames() / 4;
        int q0 = (f0 * n + bone) * 4, q1 = (f1 * n + bone) * 4;
        float[] tq = c.turn(), ta = c.at();
        qOut.set(tq[q0], tq[q0 + 1], tq[q0 + 2], tq[q0 + 3]);
        qb.set(tq[q1], tq[q1 + 1], tq[q1 + 2], tq[q1 + 3]);
        qOut.slerp(qb, u);
        int p0 = (f0 * n + bone) * 3, p1 = (f1 * n + bone) * 3;
        pOut.set(Mth.lerp(u, ta[p0], ta[p1]), Mth.lerp(u, ta[p0 + 1], ta[p1 + 1]), Mth.lerp(u, ta[p0 + 2], ta[p1 + 2]));
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
