package com.zomboidcraft.client.npc;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Plays a skinned companion's own clips (NpcSkin): breathing idle, walk, run, a rifle walk and aim, a shot's kick and her
 * death, picked from what she's doing each frame and cross-faded a quarter second. Walks and runs play as fast as she's
 * really moving. On top: her head turned toward what she looks at, her jaw moving while she talks, and blinks.
 * <p>
 * What she's busy with (NpcWork's actions: sleeping, sitting, kneeling, hammering...) plays its own clip when the model
 * has one ("sleep", "sit", "kneel"...), and her mood picks a mood idle ("sad", "nervous", "happy", "tired") when it has
 * those; until then she sleeps in her fall's last pose and works from her idle. Either way her mood shows in her body:
 * low or tired she slumps, head down; frightened her shoulders come up, she hunches and her eyes dart about; in good
 * spirits she stands a little taller.
 * One per companion, kept on her (NpcEntity.clientAnim); the posed bones come back as skin matrices (pose * inverse bind).
 */
final class NpcSkinAnimator {
    /** What she's doing this frame. Speed is metres a second over the ground. */
    static final class Input {
        float now, speed, aim, headYaw, headPitch, dead;
        boolean gun, talking;
        int shots, id;
        /** NpcEntity.action() and moodBits(): fear, morale, tiredness, pain at 4 bits each. */
        int action, mood;

        float mood(int k) {
            return ((mood >> (4 * k)) & 15) / 15f;
        }
    }

    private static final float FADE = 0.25f;
    /** The speeds the clips were made at, roughly (m/s), and where a walk becomes a run. */
    private static final float WALK_SPEED = 1.3f, RUN_SPEED = 3.6f, RUN_FROM = 2.6f, MOVING = 0.35f;
    private static final float JAW_OPEN = 13f, LID_SHUT = 31f;

    private String cur = "idle", prev;
    private float curT, prevT, fade = 1, last = -1, fireT = 99, jaw, nextBlink = 2, blinkAt = -10;
    private int shotsSeen = -1;
    final Object[] buffers = new Object[64];

    /** Poses her for this frame into `skin` (one matrix per bone). */
    void pose(NpcSkin model, Input in, Matrix4f[] skin) {
        float dt = last < 0 ? 0 : Math.max(0, Math.min(0.25f, in.now - last));
        last = in.now;
        if (shotsSeen >= 0 && in.shots != shotsSeen) {
            fireT = 0;
        }
        shotsSeen = in.shots;
        fireT += dt;
        String want = choose(model, in);
        // sleeping before she has a sleep clip: lie in her fall's last pose, dropped straight in
        boolean lying = in.dead <= 0 && in.action == SLEEP && "die".equals(want);
        if (lying && !"die".equals(cur)) {
            prev = cur;
            prevT = curT;
            fade = 0;
            cur = "die";
            curT = seconds(model, "die");
        }
        if (!want.equals(cur)) {
            // a walk into a rifle walk (and back) keeps its step
            boolean keep = cur.endsWith("walk") && want.endsWith("walk");
            float phase = keep ? curT / Math.max(1e-3f, seconds(model, cur)) : 0;
            prev = cur;
            prevT = curT;
            fade = 0;
            cur = want;
            curT = keep ? phase * seconds(model, want) : 0;
        }
        curT += dt * rate(cur, in.speed);
        prevT += dt * rate(prev, in.speed);
        fade = Math.min(1, fade + dt / FADE);

        int n = model.boneNames.length;
        NpcSkin.Clip a = model.clips.get(cur);
        int count = a == null ? 0 : a.bones();
        float[] pos = new float[count * 3], turn = new float[count * 4];
        if (a != null) {
            sample(a, time(a, curT, !cur.equals("die")), pos, turn);
            NpcSkin.Clip b = prev == null ? null : model.clips.get(prev);
            if (b != null && fade < 1 && b.bones() == count) {
                float[] pp = new float[count * 3], pt = new float[count * 4];
                sample(b, time(b, prevT, !prev.equals("die")), pp, pt);
                float k = fade * fade * (3 - 2 * fade);
                blend(pp, pt, pos, turn, k);
                System.arraycopy(pp, 0, pos, 0, pos.length);
                System.arraycopy(pt, 0, turn, 0, turn.length);
            }
        }
        Matrix4f[] world = new Matrix4f[n];
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < count; i++) {
            q.set(turn[i * 4], turn[i * 4 + 1], turn[i * 4 + 2], turn[i * 4 + 3]);
            world[i] = new Matrix4f().translation(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]).rotate(q);
        }
        for (int i = count; i < n; i++) {
            world[i] = new Matrix4f(model.inverse[i]).invert();
        }
        if (lying && a != null) {
            // her fall carries her forward; keep her where she lies down
            int hips = model.bone("mixamorigHips");
            if (hips >= 0 && hips < count) {
                float[] first = new float[count * 3], last = new float[count * 3], t0 = new float[count * 4];
                sample(a, 0, first, t0);
                sample(a, a.frames() - 1, last, t0);
                float dx = last[hips * 3] - first[hips * 3], dz = last[hips * 3 + 2] - first[hips * 3 + 2];
                for (int i = 0; i < count; i++) {
                    world[i].m30(world[i].m30() - dx * fade).m32(world[i].m32() - dz * fade);
                }
            }
        }
        if (in.dead <= 0 && in.aim < 0.5f && in.action != SLEEP) {
            look(model, world, in.headYaw, in.headPitch);
            posture(model, world, in);
        }
        for (int i = 0; i < n; i++) {
            skin[i].set(world[i]).mul(model.inverse[i]);
        }
        face(model, in, dt, skin);
    }

    /** NpcWork's actions, as the client knows them. */
    private static final int SLEEP = 1, SIT = 2, KNEEL = 3, HAMMER = 4, DIG = 5, SEARCH = 6, LOOKOUT = 7, COOKING = 8;

    private static String actionClip(int action) {
        return switch (action) {
            case SLEEP -> "sleep";
            case SIT -> "sit";
            case KNEEL, COOKING -> "kneel";
            case HAMMER -> "hammer";
            case DIG -> "dig";
            case SEARCH -> "search";
            case LOOKOUT -> "lookout";
            default -> null;
        };
    }

    private String choose(NpcSkin model, Input in) {
        String c;
        String act = in.dead <= 0 && in.speed <= MOVING && in.aim <= 0.5f ? actionClip(in.action) : null;
        if (in.dead > 0) {
            c = "die";
        } else if (act != null && model.clips.containsKey(act)) {
            c = act;
        } else if (in.action == SLEEP && in.speed <= MOVING) {
            c = "die";
        } else {
            boolean moving = in.speed > MOVING;
            if (in.aim > 0.5f) {
                c = moving ? "rifle_walk" : fireT < seconds(model, "fire") ? "fire" : "aim";
            } else if (moving) {
                c = in.speed > RUN_FROM ? "run" : in.gun ? "rifle_walk" : "walk";
            } else {
                // a mood idle when she has the clip for it
                float fear = in.mood(0), morale = in.mood(1), tired = in.mood(2);
                String mood = fear > 0.6f ? "nervous" : morale < 0.3f ? "sad" : tired > 0.7f ? "tired" : morale > 0.8f ? "happy" : null;
                c = mood != null && model.clips.containsKey(mood) ? mood : "idle";
            }
        }
        return model.clips.containsKey(c) ? c : model.clips.containsKey("idle") ? "idle" : c;
    }

    private static float rate(String clip, float speed) {
        if (clip == null) {
            return 1;
        }
        return switch (clip) {
            case "walk", "rifle_walk" -> clamp(speed / WALK_SPEED, 0.7f, 1.6f);
            case "run" -> clamp(speed / RUN_SPEED, 0.75f, 1.4f);
            default -> 1;
        };
    }

    private static float seconds(NpcSkin model, String clip) {
        NpcSkin.Clip c = model.clips.get(clip);
        return c == null ? 1 : c.seconds();
    }

    /** A clip's frame (fractional) at this time: looped, or held on its last frame. */
    private static float time(NpcSkin.Clip c, float t, boolean loop) {
        float len = c.seconds();
        if (len <= 0) {
            return 0;
        }
        float s = loop ? ((t % len) + len) % len : Math.min(t, len);
        return s * c.fps();
    }

    private static void sample(NpcSkin.Clip c, float frame, float[] pos, float[] turn) {
        int f0 = Math.min(c.frames() - 1, (int) frame), f1 = Math.min(c.frames() - 1, f0 + 1);
        float k = frame - f0;
        int n = c.bones();
        Quaternionf a = new Quaternionf(), b = new Quaternionf();
        for (int i = 0; i < n; i++) {
            int p0 = (f0 * n + i) * 3, p1 = (f1 * n + i) * 3, q0 = (f0 * n + i) * 4, q1 = (f1 * n + i) * 4;
            for (int j = 0; j < 3; j++) {
                pos[i * 3 + j] = c.at()[p0 + j] + (c.at()[p1 + j] - c.at()[p0 + j]) * k;
            }
            a.set(c.turn()[q0], c.turn()[q0 + 1], c.turn()[q0 + 2], c.turn()[q0 + 3]);
            b.set(c.turn()[q1], c.turn()[q1 + 1], c.turn()[q1 + 2], c.turn()[q1 + 3]);
            a.slerp(b, k);
            turn[i * 4] = a.x;
            turn[i * 4 + 1] = a.y;
            turn[i * 4 + 2] = a.z;
            turn[i * 4 + 3] = a.w;
        }
    }

    /** Moves pose (pp, pt) toward (pos, turn) by k, in place. */
    private static void blend(float[] pp, float[] pt, float[] pos, float[] turn, float k) {
        for (int i = 0; i < pp.length; i++) {
            pp[i] += (pos[i] - pp[i]) * k;
        }
        Quaternionf a = new Quaternionf(), b = new Quaternionf();
        for (int i = 0; i < pt.length; i += 4) {
            a.set(pt[i], pt[i + 1], pt[i + 2], pt[i + 3]);
            b.set(turn[i], turn[i + 1], turn[i + 2], turn[i + 3]);
            a.slerp(b, k);
            pt[i] = a.x;
            pt[i + 1] = a.y;
            pt[i + 2] = a.z;
            pt[i + 3] = a.w;
        }
    }

    /** Turns her neck and head (half each) toward where she looks. */
    private static void look(NpcSkin model, Matrix4f[] world, float yaw, float pitch) {
        int neck = model.bone("mixamorigNeck"), head = model.head;
        if (neck < 0 || head < 0) {
            return;
        }
        for (int[] step : new int[][] {{neck, 0}, {head, 1}}) {
            int b = step[0];
            Vector3f at = world[b].getTranslation(new Vector3f());
            // her yaw is the game's (positive turns her to her right, toward -x), her pitch positive looks down
            Matrix4f turn = new Matrix4f().translation(at)
                    .rotateY((float) Math.toRadians(-yaw * 0.5f))
                    .rotateX((float) Math.toRadians(pitch * 0.4f))
                    .translate(-at.x, -at.y, -at.z);
            for (int i = 0; i < world.length; i++) {
                if (i == b || below(model, i, b)) {
                    world[i] = new Matrix4f(turn).mul(world[i]);
                }
            }
        }
    }

    /**
     * Her mood in her body, laid over whatever clip plays: slumped and head down when she's low, tired or hurting;
     * shoulders up, hunched and glancing about when she's frightened; a little taller when she's in good spirits.
     */
    private void posture(NpcSkin model, Matrix4f[] world, Input in) {
        float fear = in.mood(0), morale = in.mood(1), tired = in.mood(2), pain = in.mood(3);
        float slump = clamp((0.45f - morale) * 1.6f, 0, 0.7f) + tired * 0.35f + pain * 0.35f;
        float proud = clamp((morale - 0.75f) * 4f, 0, 1) * (1 - fear);
        float t = in.now + in.id * 0.61f;
        float bow = slump * 12f + fear * 7f - proud * 4f;
        float headDown = slump * 14f - proud * 4f;
        // a frightened glance every second or so, a tired sway
        float dart = fear > 0.4f ? (float) (Math.sin(t * 1.7) * Math.sin(t * 0.63 + 2) * 30f * fear) : 0;
        float sway = (float) Math.sin(t * 0.8) * 3f * tired;
        float shrug = fear * 9f, forward = slump * 8f + fear * 5f;
        turn(model, world, model.bone("mixamorigSpine1"), new Matrix4f().rotateX(rad(bow * 0.5f)).rotateZ(rad(sway)));
        turn(model, world, model.bone("mixamorigSpine2"), new Matrix4f().rotateX(rad(bow * 0.5f)));
        turn(model, world, model.head, new Matrix4f().rotateX(rad(headDown)).rotateY(rad(dart)));
        turn(model, world, model.bone("mixamorigLeftShoulder"), new Matrix4f().rotateZ(rad(shrug)).rotateY(rad(-forward)));
        turn(model, world, model.bone("mixamorigRightShoulder"), new Matrix4f().rotateZ(rad(-shrug)).rotateY(rad(forward)));
    }

    /** Turns a bone and everything below it by `rot` about the bone's own place (model space). */
    private static void turn(NpcSkin model, Matrix4f[] world, int bone, Matrix4f rot) {
        if (bone < 0 || bone >= world.length || world[bone] == null) {
            return;
        }
        Vector3f at = world[bone].getTranslation(new Vector3f());
        Matrix4f m = new Matrix4f().translation(at).mul(rot).translate(-at.x, -at.y, -at.z);
        for (int i = 0; i < world.length; i++) {
            if (i == bone || below(model, i, bone)) {
                world[i] = new Matrix4f(m).mul(world[i]);
            }
        }
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static boolean below(NpcSkin model, int bone, int of) {
        for (int p = model.parent[bone]; p >= 0; p = model.parent[p]) {
            if (p == of) {
                return true;
            }
        }
        return false;
    }

    /** Her jaw while she talks (an open-close rhythm with a little unevenness), and a blink every few seconds. */
    private void face(NpcSkin model, Input in, float dt, Matrix4f[] skin) {
        if (model.head < 0) {
            return;
        }
        float t = in.now + in.id * 0.37f;
        float target = 0;
        if (in.dead > 0) {
            target = 0.35f;
        } else if (in.talking) {
            float s = Math.abs((float) Math.sin(t * 13.0)) * 0.7f + Math.abs((float) Math.sin(t * 5.3 + 1.1)) * 0.3f;
            target = 0.25f + 0.75f * s;
        }
        jaw += (target - jaw) * Math.min(1, dt * 18);
        float lid;
        if (in.dead > 0) {
            lid = Math.min(1, in.dead * 0.8f);
        } else if (in.action == SLEEP) {
            lid = 1;
        } else {
            if (in.now > nextBlink) {
                blinkAt = in.now;
                // every two to six seconds
                nextBlink = in.now + 2 + (float) ((Math.sin(t * 12.9898) * 43758.5453) % 1 + 1) % 1 * 4;
            }
            float b = (in.now - blinkAt) / 0.16f;
            lid = b >= 0 && b <= 1 ? (float) Math.sin(b * Math.PI) : 0;
        }
        hinge(model, skin, model.jaw, model.jawPivot, jaw * JAW_OPEN);
        hinge(model, skin, model.lidL, model.lidLPivot, lid * LID_SHUT);
        hinge(model, skin, model.lidR, model.lidRPivot, lid * LID_SHUT);
    }

    /** A face bone: the head's skin, then a turn about the bone's pivot (rest pose, model space) on her left-right axis. */
    private static void hinge(NpcSkin model, Matrix4f[] skin, int bone, Vector3f p, float degrees) {
        if (bone < 0) {
            return;
        }
        skin[bone].set(skin[model.head]).translate(p).rotateX((float) Math.toRadians(degrees)).translate(-p.x, -p.y, -p.z);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
