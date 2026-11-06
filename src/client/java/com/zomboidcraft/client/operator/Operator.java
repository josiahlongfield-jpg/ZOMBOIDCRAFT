package com.zomboidcraft.client.operator;

import com.zomboidcraft.client.npc.NpcSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The Tarkov operator (Josiah 2026-11-06: "a singular player model with the kitted out m4a1"): every player is drawn as
 * one Tarkov operator, in third person from config/zomboidcraft/npc/operator.zsk (body, head, helmet and rig, the rifle
 * on its own bone) with Tarkov's own idle, walk and run, and in first person from operator_m4a1.zsk ({@link OperatorView}).
 * Both files are made on his PC from his own Tarkov install and never go in the repo or the jar: without them every
 * player is drawn as before.
 */
public final class Operator {
    private Operator() {
    }

    public static final String MODEL = "operator";
    /** The rifle the operator's files are made for: its local gun id (config/zomboidcraft/guns/tarkov_m4a1.json). */
    public static final String GUN = "tarkov_m4a1";

    /** The third-person model, or null while it loads or when this PC hasn't got it. */
    public static NpcSkin skin() {
        return NpcSkin.get(MODEL);
    }

    /** Whether this player is drawn as the operator. */
    public static boolean active(Player player) {
        return player != null && !player.isSpectator() && skin() != null;
    }

    public static boolean active(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(state.id) instanceof Player p && active(p);
    }

    /** The vanilla figure is posed: hide all of it (held items too, since the operator carries the rifle himself). */
    public static boolean hideVanilla(PlayerModel model, AvatarRenderState state) {
        if (!active(state)) return false;
        com.zomboidcraft.client.survivor.Survivors.hideAll(model);
        return true;
    }

    /** Whether this render state was drawn as the operator this frame (the clothing layers step aside). */
    public static boolean drawn(AvatarRenderState state) {
        return active(state);
    }

    // ------------------------------------------------------------------ clips

    /**
     * One clip's pose at a time (seconds), in model space: positions and turns per bone. Looped clips wrap; held ones
     * stop on their last frame; a ping-pong one runs back and forth (Tarkov's aimed idle doesn't end where it starts).
     */
    static void sample(NpcSkin.Clip c, float t, int mode, float[] pos, float[] turn) {
        float len = c.seconds();
        float u;
        if (len <= 0) {
            u = 0;
        } else if (mode == LOOP) {
            u = ((t % len) + len) % len;
        } else if (mode == PINGPONG) {
            float p = ((t % (2 * len)) + 2 * len) % (2 * len);
            u = p <= len ? p : 2 * len - p;
        } else {
            u = Math.max(0, Math.min(len, t));
        }
        float f = u * c.fps();
        int i = Math.min(c.frames() - 1, (int) f), j = Math.min(c.frames() - 1, i + 1);
        float w = f - i;
        int n = Math.min(c.bones(), pos.length / 3);
        for (int b = 0; b < n; b++) {
            int a = (i * c.bones() + b), z = (j * c.bones() + b);
            for (int k = 0; k < 3; k++) {
                pos[b * 3 + k] = c.at()[a * 3 + k] + (c.at()[z * 3 + k] - c.at()[a * 3 + k]) * w;
            }
            nlerp(c.turn(), a * 4, c.turn(), z * 4, w, turn, b * 4);
        }
    }

    static final int LOOP = 0, HOLD = 1, PINGPONG = 2;

    /** Into (pos, turn), the blend toward (pos2, turn2) by w (0 keeps the first). */
    static void blend(float[] pos, float[] turn, float[] pos2, float[] turn2, float w) {
        if (w <= 0) return;
        if (w >= 1) {
            System.arraycopy(pos2, 0, pos, 0, pos.length);
            System.arraycopy(turn2, 0, turn, 0, turn.length);
            return;
        }
        for (int i = 0; i < pos.length; i++) pos[i] += (pos2[i] - pos[i]) * w;
        for (int b = 0; b < turn.length / 4; b++) nlerp(turn, b * 4, turn2, b * 4, w, turn, b * 4);
    }

    private static void nlerp(float[] a, int ai, float[] b, int bi, float w, float[] out, int oi) {
        float ax = a[ai], ay = a[ai + 1], az = a[ai + 2], aw = a[ai + 3];
        float bx = b[bi], by = b[bi + 1], bz = b[bi + 2], bw = b[bi + 3];
        if (ax * bx + ay * by + az * bz + aw * bw < 0) {
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }
        float x = ax + (bx - ax) * w, y = ay + (by - ay) * w, z = az + (bz - az) * w, q = aw + (bw - aw) * w;
        float len = (float) Math.sqrt(x * x + y * y + z * z + q * q);
        if (len < 1e-6f) len = 1;
        out[oi] = x / len;
        out[oi + 1] = y / len;
        out[oi + 2] = z / len;
        out[oi + 3] = q / len;
    }

    /** Skinning matrices for a pose. */
    static Matrix4f[] bones(NpcSkin skin, float[] pos, float[] turn) {
        int n = skin.boneNames.length;
        Matrix4f[] out = new Matrix4f[n];
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < n; b++) {
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            out[b] = new Matrix4f().translationRotate(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2], q).mul(skin.inverse[b]);
        }
        return out;
    }

    /** Turns the bones marked about a point, in model space. */
    static void rotate(boolean[] which, float[] pos, float[] turn, Vector3f about, Quaternionf r) {
        Vector3f d = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < which.length; b++) {
            if (!which[b]) continue;
            d.set(pos[b * 3] - about.x, pos[b * 3 + 1] - about.y, pos[b * 3 + 2] - about.z);
            r.transform(d);
            pos[b * 3] = about.x + d.x;
            pos[b * 3 + 1] = about.y + d.y;
            pos[b * 3 + 2] = about.z + d.z;
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            new Quaternionf(r).mul(q, q).normalize();
            turn[b * 4] = q.x;
            turn[b * 4 + 1] = q.y;
            turn[b * 4 + 2] = q.z;
            turn[b * 4 + 3] = q.w;
        }
    }

    /** Every bone at or below one (and any extra named bones), as a mask. */
    static boolean[] below(NpcSkin skin, String bone, String... extra) {
        boolean[] out = new boolean[skin.boneNames.length];
        int of = skin.bone(bone);
        for (int b = 0; b < out.length; b++) {
            for (int p = b; p >= 0 && of >= 0; p = skin.parent[p]) {
                if (p == of) {
                    out[b] = true;
                    break;
                }
            }
        }
        for (String e : extra) {
            int i = skin.bone(e);
            if (i >= 0) out[i] = true;
        }
        return out;
    }

    // ------------------------------------------------------------------ third person, per player

    static final class Body {
        float age = Float.NaN;
        String clip = "idle", from = null;
        float t, fromT, fade = 1, rate = 1;
        double lastX = Double.NaN, lastZ;
        float speed;
        float[] pos, turn, pos2, turn2;
        final Object[][] buffers = new Object[2][];
        int buffer;
        float submitAge = Float.NaN;
        long seen;
    }

    private static final Map<Integer, Body> BODIES = new HashMap<>();
    private static boolean[] upper;
    private static NpcSkin upperFor;

    /** This frame's skinning matrices for a player drawn as the operator, or null. */
    static Matrix4f[] pose(NpcSkin skin, AvatarRenderState state, Player player) {
        Body e = BODIES.computeIfAbsent(state.id, id -> new Body());
        e.seen = System.currentTimeMillis();
        int n = skin.boneNames.length;
        if (e.pos == null) {
            e.pos = new float[n * 3];
            e.turn = new float[n * 4];
            e.pos2 = new float[n * 3];
            e.turn2 = new float[n * 4];
        }
        float dt = Float.isNaN(e.age) ? 0 : Math.max(0, Math.min(0.25f, (state.ageInTicks - e.age) / 20f));
        e.age = state.ageInTicks;
        // how fast he's going, blocks a tick, from where he's been drawn (works for other players too)
        if (Double.isNaN(e.lastX) || dt > 0) {
            if (!Double.isNaN(e.lastX) && dt > 0) {
                double dx = player.getX() - e.lastX, dz = player.getZ() - e.lastZ;
                float now = (float) (Math.sqrt(dx * dx + dz * dz) / (dt * 20));
                e.speed += (now - e.speed) * Math.min(1, dt * 10);
            }
            e.lastX = player.getX();
            e.lastZ = player.getZ();
        }
        String want;
        float rate;
        if (e.speed < 0.03f || player.isPassenger()) {
            want = "idle";
            rate = 1;
        } else if (player.isSprinting() || e.speed > 0.25f) {
            want = "run";
            rate = Math.max(0.7f, Math.min(1.4f, e.speed / 0.28f));
        } else {
            want = "walk";
            rate = Math.max(0.45f, Math.min(1.3f, e.speed / 0.216f));
        }
        if (!want.equals(e.clip) && skin.clips.containsKey(want)) {
            e.from = e.clip;
            e.fromT = e.t;
            e.clip = want;
            e.t = 0;
            e.fade = 0;
        }
        e.rate = rate;
        e.t += dt * (e.clip.equals("idle") ? 1 : e.rate);
        e.fromT += dt;
        e.fade = Math.min(1, e.fade + dt / 0.2f);
        NpcSkin.Clip now = skin.clips.get(e.clip);
        if (now == null) return null;
        sample(now, e.t, e.clip.equals("idle") ? PINGPONG : LOOP, e.pos, e.turn);
        NpcSkin.Clip was = e.from == null ? null : skin.clips.get(e.from);
        if (was != null && e.fade < 1) {
            sample(was, e.fromT, e.from.equals("idle") ? PINGPONG : LOOP, e.pos2, e.turn2);
            // pos/turn hold the new clip: blend the old one out
            blend(e.pos2, e.turn2, e.pos, e.turn, smooth(e.fade));
            System.arraycopy(e.pos2, 0, e.pos, 0, e.pos.length);
            System.arraycopy(e.turn2, 0, e.turn, 0, e.turn.length);
        }
        // the upper body (and the rifle) follows the look: up and down, and the head's turn off the body
        if (upperFor != skin) {
            upper = below(skin, "Base HumanSpine2", "weapon");
            upperFor = skin;
        }
        int spine = skin.bone("Base HumanSpine2");
        if (spine >= 0 && !state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
            float yaw = Math.max(-75, Math.min(75, state.yRot)), pitch = Math.max(-80, Math.min(80, state.xRot));
            Vector3f about = new Vector3f(e.pos[spine * 3], e.pos[spine * 3 + 1], e.pos[spine * 3 + 2]);
            rotate(upper, e.pos, e.turn, about, new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch)));
        }
        if (BODIES.size() > 64) BODIES.values().removeIf(x -> System.currentTimeMillis() - x.seen > 10_000);
        return bones(skin, e.pos, e.turn);
    }

    static Object[] buffers(AvatarRenderState state, NpcSkin skin) {
        Body e = BODIES.computeIfAbsent(state.id, id -> new Body());
        if (e.submitAge == state.ageInTicks) e.buffer = 1 - e.buffer;
        e.submitAge = state.ageInTicks;
        if (e.buffers[e.buffer] == null || e.buffers[e.buffer].length != skin.groups.length * 2) {
            e.buffers[e.buffer] = new Object[skin.groups.length * 2];
        }
        return e.buffers[e.buffer];
    }

    static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Whether a held stack is the operator's rifle. */
    public static boolean holdsRifle(net.minecraft.world.item.ItemStack stack) {
        return GUN.equals(stack.get(com.zomboidcraft.combat.LocalGuns.ID));
    }
}
