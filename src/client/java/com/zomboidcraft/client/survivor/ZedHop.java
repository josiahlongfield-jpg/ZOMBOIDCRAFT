package com.zomboidcraft.client.survivor;

import com.zomboidcraft.combat.Dismember;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A zombie with one leg left hops on it (Josiah 2026-11-02: it lay down and got up again every step).
 *
 * <p>Zomboid has no hop clip (its one-legged lurch, Zombie_WalkStumble, is a trip that ends on the floor), so the hop is
 * laid over Zomboid's own standing clip, on Zomboid's skeleton: the body leans over the leg it has, sinks onto it as the
 * knee takes its weight, pushes off and comes down a stride on, the foot planted while the body passes over it and
 * swung under it in the air; the arms come up for balance in the air, and a thigh stump lifts with the hop. The kept leg
 * is set by two-bone reach (hip, knee, foot) so the foot stays on the ground and never slides.
 *
 * <p>Works on the bone places and turns ZedBodies' animator gives (model space, metres, +z ahead).
 */
final class ZedHop {
    /** One zombie's hop. */
    static final class State {
        float phase, weight, moving;
    }

    /** How far it gets in one hop (m), how long the foot is down in each, how high it rises and how deep it sinks. */
    private static final float STRIDE = 0.42f, STANCE = 0.55f, RISE = 0.075f, SINK = 0.085f;

    private record Bones(int pelvis, int spine, int[] thigh, int[] calf, int[] foot, int[] toe, int[] arm) {
    }

    private static final Map<SurvivorPack, Bones> BONES = new java.util.WeakHashMap<>();

    private static Bones bones(SurvivorPack p) {
        return BONES.computeIfAbsent(p, k -> new Bones(k.skin.bone("Bip01_Pelvis"), k.skin.bone("Bip01_Spine"),
                new int[] {k.skin.bone("Bip01_L_Thigh"), k.skin.bone("Bip01_R_Thigh")},
                new int[] {k.skin.bone("Bip01_L_Calf"), k.skin.bone("Bip01_R_Calf")},
                new int[] {k.skin.bone("Bip01_L_Foot"), k.skin.bone("Bip01_R_Foot")},
                new int[] {k.skin.bone("Bip01_L_Toe0"), k.skin.bone("Bip01_R_Toe0")},
                new int[] {k.skin.bone("Bip01_L_UpperArm"), k.skin.bone("Bip01_R_UpperArm")}));
    }

    /** The side it still stands on: 0 left, 1 right, or -1 if it has both legs or none. */
    static int kept(int cuts) {
        boolean r = (cuts & Dismember.RIGHT_LEG) != 0, l = (cuts & Dismember.LEFT_LEG) != 0;
        return r == l ? -1 : r ? 0 : 1;
    }

    /**
     * Lays the hop over this frame's pose. on: it's on its foot (not lying, climbing, beating on a door or dead);
     * speed: how fast it's going (blocks a second); dt: seconds since the last frame.
     */
    static void apply(SurvivorPack pack, State s, float[] pos, float[] turn, int cuts, boolean on, float speed, float dt) {
        int side = kept(cuts);
        s.weight = ease(s.weight, on && side >= 0 ? 1 : 0, dt * 6);
        s.moving = ease(s.moving, speed > 0.15f ? 1 : 0, dt * 5);
        if (s.weight < 0.001f || side < 0) return;
        Bones b = bones(pack);
        if (b.pelvis < 0 || b.spine < 0 || b.thigh[side] < 0 || b.calf[side] < 0 || b.foot[side] < 0) return;
        // a hop a stride long at the pace it's going, never so quick it buzzes or so slow it hangs in the air
        float rate = Math.max(0.9f, Math.min(2.4f, speed / STRIDE));
        s.phase = (s.phase + dt * rate * s.moving) % 1f;
        float w = s.weight, m = s.moving * w;
        float ph = s.phase, stance = ph < STANCE ? ph / STANCE : -1, air = ph < STANCE ? -1 : (ph - STANCE) / (1 - STANCE);
        float sink = stance >= 0 ? (float) Math.sin(Math.PI * stance) : 0;
        float rise = air >= 0 ? 4 * air * (1 - air) : 0;
        float stride = Math.min(STRIDE, speed / rate) * m;

        int hip = b.thigh[side], knee = b.calf[side], foot = b.foot[side], toe = b.toe[side];
        Vector3f foot0 = at(pos, foot), toe0 = toe < 0 ? null : at(pos, toe);
        Quaternionf footTurn = rot(turn, foot), toeTurn = toe < 0 ? null : rot(turn, toe);
        Vector3f pelvis0 = at(pos, b.pelvis);

        // 1. it leans out over the leg it has, and forward into the hop, more as it sinks onto the knee
        float lean = (float) Math.toRadians(8 + 10 * m + 8 * sink * m) * w;
        float roll = (float) Math.toRadians(side == 0 ? -7 : 7) * w;
        turnAbout(pack, pos, turn, b.spine, pelvis0, new Quaternionf().rotateX(lean).rotateZ(roll));
        // 2. the whole body over the foot: sideways onto it, down onto the knee, up off the ground in the air
        float over = (foot0.x - pelvis0.x) * 0.6f * w;
        float dy = (RISE * rise - SINK * sink * (0.4f + 0.6f * m)) * w;
        for (int i = 0; i < pos.length / 3; i++) {
            pos[i * 3] += over;
            pos[i * 3 + 1] += dy;
        }
        // 3. the arms come up and out for balance in the air
        for (int k = 0; k < 2; k++) {
            int arm = b.arm[k];
            if (arm < 0) continue;
            float lift = (float) Math.toRadians(14 * rise + 6 * sink) * m;
            turnAbout(pack, pos, turn, arm, at(pos, arm), new Quaternionf().rotateZ(k == 0 ? lift : -lift).rotateX(-lift * 0.6f));
        }
        // 4. a thigh stump lifts a little with each hop instead of hanging stiff
        int lost = b.thigh[1 - side];
        if (lost >= 0 && (cuts & (side == 0 ? Dismember.HIP_R : Dismember.HIP_L)) == 0) {
            float swing = (float) Math.toRadians(-(20 + 15 * rise)) * w;
            turnAbout(pack, pos, turn, lost, at(pos, lost), new Quaternionf().rotateX(swing));
        }
        // 5. the foot: under the hip, planted while the body goes over it, swung forward under it in the air
        Vector3f h = at(pos, hip);
        float z;
        if (stance >= 0) {
            z = stride * (0.5f - stance);
        } else {
            float k = air * air * (3 - 2 * air);
            z = stride * (-0.5f + k);
        }
        Vector3f target = new Vector3f(lerp(foot0.x, h.x, 0.85f * w), foot0.y + (RISE * rise + 0.05f * rise * m) * w,
                lerp(foot0.z, h.z + 0.04f + z, w));
        reach(pack, pos, turn, hip, knee, foot, target);
        // the foot keeps its own turn (flat on the ground) and its toe comes with it
        put(turn, foot, footTurn);
        if (toe >= 0) {
            Vector3f off = toe0.sub(foot0, new Vector3f());
            put(pos, toe, at(pos, foot).add(off));
            put(turn, toe, toeTurn);
        }
    }

    /** Hip, knee and foot set so the foot reaches target, the knee bending forward. */
    private static void reach(SurvivorPack pack, float[] pos, float[] turn, int hip, int knee, int foot, Vector3f target) {
        Vector3f h = at(pos, hip), k = at(pos, knee), f = at(pos, foot);
        float a = k.distance(h), c = f.distance(k);
        Vector3f to = target.sub(h, new Vector3f());
        float d = Math.max(Math.abs(a - c) + 1e-3f, Math.min(a + c - 1e-3f, to.length()));
        Vector3f n = to.normalize();
        Vector3f pole = new Vector3f(0, 0, 1).sub(new Vector3f(n).mul(n.z));
        if (pole.lengthSquared() < 1e-6f) pole.set(0, -1, 0).sub(new Vector3f(n).mul(-n.y));
        pole.normalize();
        float cos = Math.max(-1, Math.min(1, (a * a + d * d - c * c) / (2 * a * d)));
        float sin = (float) Math.sqrt(1 - cos * cos);
        Vector3f knee2 = new Vector3f(h).add(new Vector3f(n).mul(a * cos)).add(new Vector3f(pole).mul(a * sin));
        // the thigh turns its knee onto the new place, carrying everything below
        Quaternionf q1 = new Quaternionf().rotationTo(k.sub(h, new Vector3f()), knee2.sub(h, new Vector3f()));
        turnAbout(pack, pos, turn, hip, h, q1);
        // then the shin turns its foot onto the target
        Vector3f k1 = at(pos, knee), f1 = at(pos, foot);
        Vector3f goal = new Vector3f(h).add(n.mul(d));
        Quaternionf q2 = new Quaternionf().rotationTo(f1.sub(k1, new Vector3f()), goal.sub(k1, new Vector3f()));
        turnAbout(pack, pos, turn, knee, k1, q2);
    }

    /** Turns a bone and everything below it by q about pivot. */
    private static void turnAbout(SurvivorPack pack, float[] pos, float[] turn, int bone, Vector3f pivot, Quaternionf q) {
        int n = pos.length / 3;
        Vector3f v = new Vector3f();
        Quaternionf r = new Quaternionf();
        for (int b = 0; b < n; b++) {
            if (!below(pack, b, bone)) continue;
            v.set(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]).sub(pivot);
            q.transform(v).add(pivot);
            pos[b * 3] = v.x;
            pos[b * 3 + 1] = v.y;
            pos[b * 3 + 2] = v.z;
            r.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            new Quaternionf(q).mul(r, r).normalize();
            put(turn, b, r);
        }
    }

    private static boolean below(SurvivorPack pack, int b, int of) {
        for (int p = b; p >= 0; p = pack.skin.parent[p]) if (p == of) return true;
        return false;
    }

    private static Vector3f at(float[] pos, int b) {
        return new Vector3f(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]);
    }

    private static void put(float[] pos, int b, Vector3f v) {
        pos[b * 3] = v.x;
        pos[b * 3 + 1] = v.y;
        pos[b * 3 + 2] = v.z;
    }

    private static Quaternionf rot(float[] turn, int b) {
        return new Quaternionf(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
    }

    private static void put(float[] turn, int b, Quaternionf q) {
        turn[b * 4] = q.x;
        turn[b * 4 + 1] = q.y;
        turn[b * 4 + 2] = q.z;
        turn[b * 4 + 3] = q.w;
    }

    private static float ease(float v, float to, float k) {
        return v + (to - v) * Math.min(1, k);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private ZedHop() {
    }
}
