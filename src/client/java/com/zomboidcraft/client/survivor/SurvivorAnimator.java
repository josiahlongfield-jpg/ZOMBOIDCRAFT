package com.zomboidcraft.client.survivor;

import com.zomboidcraft.client.npc.NpcSkin;
import java.util.HashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Plays Zomboid's moves on one survivor: a base clip (standing, walking, running, sneaking, carrying a weapon, sitting
 * in a car...) that crossfades into the next over a fifth of a second, stepping in time with how fast they're really
 * moving, and over it an action played once (a swing, a punch, eating, an idle fidget, falling dead) on the upper body
 * or the whole of them. Everything is blended bone by bone in each bone's own frame, then put together down the
 * skeleton, so mixing a swing's arms with a walk's legs keeps the spine whole.
 */
final class SurvivorAnimator {
    private static final float FADE = 0.2f;

    private String base = "idle", was;
    private float baseT, wasT, fade = 1, rate = 1, wasRate = 1;
    private boolean wasLoop = true;
    private String action;
    private float actionT, actionRate = 1, actionWeight;
    private boolean actionUpper, actionHold;
    /** Seconds stood still with nothing in hand (Zomboid plays a fidget now and then). */
    float still;
    float lastAge = Float.NaN;
    private static final Map<String, Float> NATURAL = new HashMap<>();

    /** Moving clips keep their step when one gives way to another (walk into run), so the feet don't skip. */
    void base(SurvivorPack pack, String clip, float rate) {
        if (pack.skin.clips.get(clip) == null) clip = pack.skin.clips.containsKey("idle") ? "idle" : base;
        if (clip.equals(base)) {
            this.rate = rate;
            return;
        }
        NpcSkin.Clip from = pack.skin.clips.get(base), to = pack.skin.clips.get(clip);
        was = base;
        wasT = baseT;
        wasRate = this.rate;
        wasLoop = true;
        this.rate = rate;
        fade = 0;
        float phase = from == null ? 0 : baseT / Math.max(1e-3f, from.seconds());
        base = clip;
        baseT = moving(was) && moving(clip) && to != null ? phase * to.seconds() : 0;
    }

    /** Straight into a clip at a time (seconds), nothing blended in: a still picture of it (ZedBodies.farPose). */
    void set(SurvivorPack pack, String clip, float t) {
        base = pack.skin.clips.containsKey(clip) ? clip : "idle";
        baseT = t;
        rate = 1;
        was = null;
        fade = 1;
    }

    /** Plays a clip once over the base: the upper body only, or all of her; held on its last frame if asked. */
    void act(SurvivorPack pack, String clip, float rate, boolean upper, boolean hold) {
        if (pack.skin.clips.get(clip) == null) return;
        if (clip.equals(action)) {
            actionRate = rate;
            return;
        }
        action = clip;
        actionT = 0;
        actionRate = rate;
        actionUpper = upper;
        actionHold = hold;
    }

    /** The action stops (eased out). */
    void stopAction(SurvivorPack pack) {
        actionHold = false;
        NpcSkin.Clip c = action == null ? null : pack.skin.clips.get(action);
        if (c != null) actionT = Math.max(actionT, c.seconds() - FADE * actionRate);
    }

    String base() {
        return base;
    }

    String action() {
        return action;
    }

    boolean busy() {
        return action != null;
    }

    void advance(SurvivorPack pack, float dt) {
        baseT += dt * rate;
        if (was != null) {
            wasT += dt * wasRate;
            fade = Math.min(1, fade + dt / FADE);
            if (fade >= 1) was = null;
        }
        if (action != null) {
            NpcSkin.Clip c = pack.skin.clips.get(action);
            float len = c == null ? 0 : c.seconds() / Math.max(0.05f, actionRate);
            actionT += dt * actionRate;
            float left = c == null ? 0 : c.seconds() - actionT;
            if (actionHold) {
                actionT = Math.min(actionT, c == null ? 0 : c.seconds());
                actionWeight = Math.min(1, actionWeight + dt / FADE);
            } else if (left <= 0) {
                action = null;
                actionWeight = 0;
            } else {
                actionWeight = Math.min(1, Math.min(actionT / FADE, left * actionRate / FADE));
                if (len < FADE * 2) actionWeight = 1;
            }
        }
    }

    /**
     * Her pose now, each bone's turn (x, y, z, w) and place in her model space, from the clips blended in each bone's
     * own frame. Bones listed in upper take the action when it's on the upper body only.
     */
    void pose(SurvivorPack pack, boolean[] upper, float[] pos, float[] turn) {
        NpcSkin skin = pack.skin;
        int n = skin.boneNames.length;
        float[] lp = new float[n * 3], lq = new float[n * 4];
        local(skin, base, baseT, true, lp, lq);
        if (was != null && fade < 1) {
            float[] wp = new float[n * 3], wq = new float[n * 4];
            local(skin, was, wasT, wasLoop, wp, wq);
            mix(wp, wq, lp, lq, fade, null, lp, lq);
        }
        if (action != null && actionWeight > 0) {
            float[] ap = new float[n * 3], aq = new float[n * 4];
            local(skin, action, actionT, false, ap, aq);
            mix(lp, lq, ap, aq, actionWeight, actionUpper ? upper : null, lp, lq);
        }
        compose(skin, lp, lq, pos, turn);
    }

    private static boolean moving(String clip) {
        return clip != null && (clip.contains("walk") || clip.contains("run") || clip.contains("sprint") || clip.contains("limp")
                || clip.startsWith("strafe"));
    }

    /** A clip at a time, each bone in its parent's frame. */
    private static void local(NpcSkin skin, String name, float t, boolean loop, float[] lp, float[] lq) {
        NpcSkin.Clip c = skin.clips.get(name);
        int n = skin.boneNames.length;
        float[] wp = new float[n * 3], wq = new float[n * 4];
        if (c == null) {
            for (int b = 0; b < n; b++) {
                Vector3f p = skin.inverse[b].invertAffine(new org.joml.Matrix4f()).getTranslation(new Vector3f());
                Quaternionf q = skin.inverse[b].invertAffine(new org.joml.Matrix4f()).getUnnormalizedRotation(new Quaternionf()).normalize();
                wp[b * 3] = p.x;
                wp[b * 3 + 1] = p.y;
                wp[b * 3 + 2] = p.z;
                wq[b * 4] = q.x;
                wq[b * 4 + 1] = q.y;
                wq[b * 4 + 2] = q.z;
                wq[b * 4 + 3] = q.w;
            }
        } else {
            float len = c.seconds();
            float time = loop && len > 0 ? t % len : Math.min(Math.max(0, t), len);
            float f = time * c.fps();
            int a = Math.min(c.frames() - 1, (int) f), bf = Math.min(c.frames() - 1, a + 1);
            float k = f - a;
            Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
            for (int b = 0; b < Math.min(n, c.bones()); b++) {
                int ia = a * c.bones() + b, ib = bf * c.bones() + b;
                qa.set(c.turn()[ia * 4], c.turn()[ia * 4 + 1], c.turn()[ia * 4 + 2], c.turn()[ia * 4 + 3]);
                qb.set(c.turn()[ib * 4], c.turn()[ib * 4 + 1], c.turn()[ib * 4 + 2], c.turn()[ib * 4 + 3]);
                qa.slerp(qb, k);
                wq[b * 4] = qa.x;
                wq[b * 4 + 1] = qa.y;
                wq[b * 4 + 2] = qa.z;
                wq[b * 4 + 3] = qa.w;
                for (int i = 0; i < 3; i++) wp[b * 3 + i] = c.at()[ia * 3 + i] * (1 - k) + c.at()[ib * 3 + i] * k;
            }
        }
        Quaternionf pq = new Quaternionf(), q = new Quaternionf();
        Vector3f d = new Vector3f();
        for (int b = 0; b < n; b++) {
            int p = skin.parent[b];
            q.set(wq[b * 4], wq[b * 4 + 1], wq[b * 4 + 2], wq[b * 4 + 3]);
            d.set(wp[b * 3], wp[b * 3 + 1], wp[b * 3 + 2]);
            if (p >= 0) {
                pq.set(wq[p * 4], wq[p * 4 + 1], wq[p * 4 + 2], wq[p * 4 + 3]).conjugate();
                d.sub(wp[p * 3], wp[p * 3 + 1], wp[p * 3 + 2]);
                pq.transform(d);
                pq.mul(q, q);
            }
            lp[b * 3] = d.x;
            lp[b * 3 + 1] = d.y;
            lp[b * 3 + 2] = d.z;
            lq[b * 4] = q.x;
            lq[b * 4 + 1] = q.y;
            lq[b * 4 + 2] = q.z;
            lq[b * 4 + 3] = q.w;
        }
    }

    /** a toward b by k (only the masked bones, if a mask is given). */
    private static void mix(float[] ap, float[] aq, float[] bp, float[] bq, float k, boolean[] mask, float[] op, float[] oq) {
        int n = ap.length / 3;
        Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
        for (int b = 0; b < n; b++) {
            float kk = mask == null || mask[b] ? k : 0;
            qa.set(aq[b * 4], aq[b * 4 + 1], aq[b * 4 + 2], aq[b * 4 + 3]);
            qb.set(bq[b * 4], bq[b * 4 + 1], bq[b * 4 + 2], bq[b * 4 + 3]);
            qa.slerp(qb, kk);
            oq[b * 4] = qa.x;
            oq[b * 4 + 1] = qa.y;
            oq[b * 4 + 2] = qa.z;
            oq[b * 4 + 3] = qa.w;
            for (int i = 0; i < 3; i++) op[b * 3 + i] = ap[b * 3 + i] * (1 - kk) + bp[b * 3 + i] * kk;
        }
    }

    /** Down the skeleton (parents come first in the file), each bone's own frame into her model space. */
    private static void compose(NpcSkin skin, float[] lp, float[] lq, float[] pos, float[] turn) {
        int n = skin.boneNames.length;
        Quaternionf pq = new Quaternionf(), q = new Quaternionf();
        Vector3f d = new Vector3f();
        for (int b = 0; b < n; b++) {
            int p = skin.parent[b];
            q.set(lq[b * 4], lq[b * 4 + 1], lq[b * 4 + 2], lq[b * 4 + 3]);
            d.set(lp[b * 3], lp[b * 3 + 1], lp[b * 3 + 2]);
            if (p >= 0 && p < b) {
                pq.set(turn[p * 4], turn[p * 4 + 1], turn[p * 4 + 2], turn[p * 4 + 3]);
                pq.transform(d);
                d.add(pos[p * 3], pos[p * 3 + 1], pos[p * 3 + 2]);
                pq.mul(q, q);
            }
            q.normalize();
            pos[b * 3] = d.x;
            pos[b * 3 + 1] = d.y;
            pos[b * 3 + 2] = d.z;
            turn[b * 4] = q.x;
            turn[b * 4 + 1] = q.y;
            turn[b * 4 + 2] = q.z;
            turn[b * 4 + 3] = q.w;
        }
    }

    /** How fast a moving clip carries her (metres a second), from how far it walks her in one loop. */
    float natural(SurvivorPack pack, String clip) {
        return NATURAL.computeIfAbsent(pack.sex + clip, key -> {
            NpcSkin.Clip c = pack.skin.clips.get(clip);
            int t = pack.translation;
            if (c == null || t < 0 || c.seconds() <= 0) return 0f;
            int last = (c.frames() - 1) * c.bones() + t;
            float dx = c.at()[last * 3] - c.at()[t * 3], dz = c.at()[last * 3 + 2] - c.at()[t * 3 + 2];
            return (float) Math.sqrt(dx * dx + dz * dz) / c.seconds();
        });
    }
}
