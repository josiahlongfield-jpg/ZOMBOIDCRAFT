package com.zomboidcraft.client.operator;

import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.combat.LocalGuns;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Tarkov's own left-hand hold on a Tarkov gun's fitted foregrip or handguard (GripPose: a hand skeleton saved in the
 * part's prefab, exported per gun into its guns/ID.json "holds"; Josiah 2026-11-07: "when a grip is looted in game
 * it's actually used by the player model"). While the clip has the left hand on the gun, near the hold, the arm
 * reaches it (two-bone IK about the shoulder and elbow), the palm takes the hold's turn and each finger its own; as a
 * reload takes the hand away it lets go. Everything is in the first-person model's space, as the clips are baked.
 */
final class GripHold {
    private GripHold() {
    }

    private static final String UPPER = "Base HumanLUpperarm", ELBOW = "Base HumanLForearm1", PALM = "Base HumanLPalm";
    private static final String[] ARM = {UPPER, ELBOW, "Base HumanLForearm2", "Base HumanLForearm3", PALM};
    /** Fully on the hold within this of it; let go beyond the far one (metres, the animated palm to the hold). */
    private static final float NEAR = 0.09f, FAR = 0.20f;

    /** One model's left arm, by bone index. */
    private static final class Rig {
        NpcSkin skin;
        int upper, elbow, palm;
        boolean[] arm, forearm, hand;
        /** The finger bones, each after its parent, with their parents and names. */
        int[] digit, parent;
        String[] name;
        float[] localPos, localTurn;
    }

    private static Rig rig;

    private static Rig rig(NpcSkin skin) {
        if (rig != null && rig.skin == skin) return rig;
        Rig r = new Rig();
        r.skin = skin;
        r.upper = skin.bone(UPPER);
        r.elbow = skin.bone(ELBOW);
        r.palm = skin.bone(PALM);
        int n = skin.boneNames.length;
        r.arm = new boolean[n];
        r.forearm = new boolean[n];
        r.hand = new boolean[n];
        java.util.List<int[]> fingers = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int f = 1; f <= 5; f++) {
            int up = r.palm;
            for (int j = 1; j <= 3; j++) {
                String nm = "Base HumanLDigit" + f + j;
                int b = skin.bone(nm);
                if (b < 0 || up < 0) break;
                fingers.add(new int[]{b, up});
                names.add(nm);
                up = b;
            }
        }
        r.digit = new int[fingers.size()];
        r.parent = new int[fingers.size()];
        r.name = names.toArray(new String[0]);
        for (int i = 0; i < r.digit.length; i++) {
            r.digit[i] = fingers.get(i)[0];
            r.parent[i] = fingers.get(i)[1];
        }
        r.localPos = new float[r.digit.length * 3];
        r.localTurn = new float[r.digit.length * 4];
        for (int a = 0; a < ARM.length; a++) {
            int b = skin.bone(ARM[a]);
            if (b < 0) continue;
            r.arm[b] = true;
            if (a >= 1) r.forearm[b] = true;
            if (a >= 4) r.hand[b] = true;
        }
        for (int b : r.digit) {
            r.arm[b] = r.forearm[b] = r.hand[b] = true;
        }
        rig = r;
        return r;
    }

    /** The hold for the parts built on: the foregrip's, else the handguard's; null when it has none here. */
    static LocalGuns.Hold of(LocalGuns.Spec spec) {
        if (spec == null || spec.holds == null) return null;
        LocalGuns.Hold h = spec.grip == null ? null : spec.holds.get(spec.grip);
        if (h == null && spec.guard != null) h = spec.holds.get(spec.guard);
        return h;
    }

    /**
     * Puts the left hand on the gun's hold, as far as the clip has it there; returns how far on (0 to 1).
     */
    static float apply(NpcSkin skin, LocalGuns.Spec spec, float[] pos, float[] turn) {
        LocalGuns.Hold hold = of(spec);
        if (hold == null || hold.palm == null || hold.palm.length < 7) return 0;
        String bone = spec.grip != null && spec.holds.get(spec.grip) == hold ? spec.grip : spec.guard;
        int part = skin.bone(bone);
        Rig r = rig(skin);
        if (part < 0 || r.upper < 0 || r.elbow < 0 || r.palm < 0) return 0;

        // the hold where its part is now (it moves with it: the 870's pump)
        Vector3f tp = new Vector3f();
        Quaternionf tq = new Quaternionf();
        on(pos, turn, part, hold.palm, tp, tq);
        Vector3f pp = at(pos, r.palm);
        float d = pp.distance(tp);
        LocalGuns.Hold g = spec.guard != null && !spec.guard.equals(bone) ? spec.holds.get(spec.guard) : null;
        int gb = g == null ? -1 : skin.bone(spec.guard);
        if (gb >= 0 && g.palm != null && g.palm.length >= 7) {
            Vector3f gp = new Vector3f();
            on(pos, turn, gb, g.palm, gp, new Quaternionf());
            d = Math.min(d, pp.distance(gp));
        }
        float w = Math.max(0, Math.min(1, 1 - (d - NEAR) / (FAR - NEAR)));
        if (w <= 0) return 0;
        float k = Operator.smooth(w);
        Vector3f target = new Vector3f(pp).lerp(tp, k);

        // the fingers' own turns and places from their parents, before the arm moves
        for (int i = 0; i < r.digit.length; i++) {
            int b = r.digit[i], p = r.parent[i];
            Quaternionf pq = rot(turn, p).conjugate();
            Vector3f lo = pq.transform(at(pos, b).sub(at(pos, p)));
            Quaternionf lq = pq.mul(rot(turn, b), new Quaternionf());
            put(r.localPos, i, lo);
            put(r.localTurn, i, lq);
        }

        // the arm: the elbow swung so the wrist reaches, then the forearm pointed at it
        Vector3f s = at(pos, r.upper), e = at(pos, r.elbow), wr = at(pos, r.palm);
        float a = e.distance(s), b = wr.distance(e);
        float dist = target.distance(s);
        if (a > 1e-4f && b > 1e-4f && dist > 1e-4f) {
            float reach = Math.min(dist, a + b - 1e-4f);
            Vector3f u = new Vector3f(target).sub(s).normalize();
            Vector3f pole = new Vector3f(e).sub(s);
            Vector3f pv = new Vector3f(pole).sub(new Vector3f(u).mul(pole.dot(u)));
            if (pv.lengthSquared() > 1e-10f) {
                pv.normalize();
                float cos = (a * a + reach * reach - b * b) / (2 * a * reach);
                cos = Math.max(-1, Math.min(1, cos));
                float sin = (float) Math.sqrt(Math.max(0, 1 - cos * cos));
                Vector3f e2 = new Vector3f(s).add(new Vector3f(u).mul(a * cos)).add(pv.mul(a * sin));
                Quaternionf r1 = new Quaternionf().rotationTo(new Vector3f(e).sub(s), new Vector3f(e2).sub(s));
                Operator.rotate(r.arm, pos, turn, s, r1);
            }
            Vector3f e3 = at(pos, r.elbow), w3 = at(pos, r.palm);
            Quaternionf r2 = new Quaternionf().rotationTo(new Vector3f(w3).sub(e3), new Vector3f(target).sub(e3));
            Operator.rotate(r.forearm, pos, turn, e3, r2);
        }

        // the palm turned onto the hold's
        Quaternionf q0 = rot(turn, r.palm);
        Quaternionf qk = new Quaternionf(q0).slerp(tq, k);
        Quaternionf delta = new Quaternionf(qk).mul(new Quaternionf(q0).conjugate()).normalize();
        Operator.rotate(r.hand, pos, turn, at(pos, r.palm), delta);

        // and each finger its own turn on the hold, rebuilt out from the palm
        for (int i = 0; i < r.digit.length; i++) {
            int bn = r.digit[i], p = r.parent[i];
            Quaternionf lq = new Quaternionf(r.localTurn[i * 4], r.localTurn[i * 4 + 1], r.localTurn[i * 4 + 2],
                    r.localTurn[i * 4 + 3]);
            float[] f = hold.fingers == null ? null : hold.fingers.get(r.name[i]);
            if (f != null && f.length >= 4) lq.slerp(new Quaternionf(f[0], f[1], f[2], f[3]), k);
            Quaternionf pq = rot(turn, p);
            Vector3f lo = new Vector3f(r.localPos[i * 3], r.localPos[i * 3 + 1], r.localPos[i * 3 + 2]);
            Vector3f np = pq.transform(lo).add(at(pos, p));
            Quaternionf nq = new Quaternionf(pq).mul(lq).normalize();
            pos[bn * 3] = np.x;
            pos[bn * 3 + 1] = np.y;
            pos[bn * 3 + 2] = np.z;
            turn[bn * 4] = nq.x;
            turn[bn * 4 + 1] = nq.y;
            turn[bn * 4 + 2] = nq.z;
            turn[bn * 4 + 3] = nq.w;
        }
        return w;
    }

    /** A hold's palm (metres and turn on its part) where the part's bone now is. */
    private static void on(float[] pos, float[] turn, int bone, float[] palm, Vector3f outPos, Quaternionf outTurn) {
        Quaternionf q = rot(turn, bone);
        q.transform(new Vector3f(palm[0], palm[1], palm[2]), outPos).add(at(pos, bone));
        q.mul(new Quaternionf(palm[3], palm[4], palm[5], palm[6]), outTurn).normalize();
    }

    private static Vector3f at(float[] pos, int b) {
        return new Vector3f(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]);
    }

    private static Quaternionf rot(float[] turn, int b) {
        return new Quaternionf(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
    }

    private static void put(float[] a, int i, Vector3f v) {
        a[i * 3] = v.x;
        a[i * 3 + 1] = v.y;
        a[i * 3 + 2] = v.z;
    }

    private static void put(float[] a, int i, Quaternionf q) {
        a[i * 4] = q.x;
        a[i * 4 + 1] = q.y;
        a[i * 4 + 2] = q.z;
        a[i * 4 + 3] = q.w;
    }
}
