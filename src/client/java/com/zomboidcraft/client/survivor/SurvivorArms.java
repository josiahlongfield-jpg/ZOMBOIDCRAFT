package com.zomboidcraft.client.survivor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A survivor's own arms in first person, wherever the game would otherwise draw Minecraft's: her body (her sex's
 * pack, her skin, tone, build and clothes) posed from Zomboid's clip, set with her eyes at the eye, each hand brought
 * to its grip, and everything but the arms drawn in to the shoulders so only her arms and sleeves show.
 */
public final class SurvivorArms {
    private static final SurvivorAnimator ANIM = new SurvivorAnimator();
    private static SurvivorPack animPack;
    private static String animClip;
    private static Object[][] buffers = new Object[2][];
    private static int buffer;
    private static SurvivorPack buffersPack;
    private static boolean[] upper;

    private SurvivorArms() {
    }

    /** Whether this player's first-person arms are a survivor's (else the caller draws the usual ones). */
    public static boolean shown(Player player) {
        return player != null && Survivors.outfit(player) != null;
    }

    /**
     * Draws her arms in the frame of pose: her eyes at eye looking along forward with up up (that frame's units are
     * blocks, scaled by scale), each hand gripping at its point. A null hand is not drawn. Returns false (nothing
     * drawn) when this player isn't drawn as a survivor.
     */
    public static boolean draw(PoseStack pose, SubmitNodeCollector out, int light, Player player, Vector3f eye, Vector3f forward,
                               Vector3f up, float scale, Vector3f rightHand, Vector3f leftHand, boolean aiming) {
        Survivors.Outfit o = player == null ? null : Survivors.outfit(player);
        if (o == null) return false;
        SurvivorPack pack = o.pack();
        int n = pack.skin.boneNames.length;
        float[] pos = new float[n * 3], turn = new float[n * 4];
        String clip = aiming ? "rifle_aim" : "idle";
        if (animPack != pack || !clip.equals(animClip)) {
            animPack = pack;
            animClip = clip;
            upper = Survivors.upperBody(pack);
            ANIM.base(pack, clip, 0);
            ANIM.advance(pack, 10);   // straight into it: the clip stands still (rate 0), the fade from the last one done
        }
        ANIM.pose(pack, upper, pos, turn);

        // her frame into the caller's: eyes on the eye, her front along forward, her up along up, her left to the left
        Vector3f f = new Vector3f(forward).normalize();
        Vector3f u = new Vector3f(up).sub(new Vector3f(f).mul(up.dot(f))).normalize();
        Vector3f left = new Vector3f(u).cross(f);
        int head = pack.head;
        Vector3f eyes = new Vector3f(pos[head * 3], pos[head * 3 + 1] + 0.09f, pos[head * 3 + 2] + 0.09f);
        Matrix4f place = new Matrix4f().translation(eye).mul(new Matrix4f().set(new Matrix3f(left, u, f))).scale(scale)
                .translate(-eyes.x, -eyes.y, -eyes.z);
        Matrix4f back = new Matrix4f(place).invert();

        Vector3f[] want = {rightHand == null ? null : back.transformPosition(new Vector3f(rightHand)),
                leftHand == null ? null : back.transformPosition(new Vector3f(leftHand))};
        // the leading hand first: if it can't reach, she steps in (only her arms show, so the body just moves up)
        int first = want[0] != null ? 0 : 1;
        for (int k = 0; k < 2; k++) {
            int s = k == 0 ? first : 1 - first;
            if (want[s] == null || pack.upper[s] < 0 || pack.fore[s] < 0 || pack.hand[s] < 0) continue;
            int sh = pack.upper[s], el = pack.fore[s], wr = pack.hand[s];
            Vector3f shoulder = new Vector3f(pos[sh * 3], pos[sh * 3 + 1], pos[sh * 3 + 2]);
            float reachLen = shoulder.distance(pos[el * 3], pos[el * 3 + 1], pos[el * 3 + 2])
                    + new Vector3f(pos[el * 3], pos[el * 3 + 1], pos[el * 3 + 2]).distance(pos[wr * 3], pos[wr * 3 + 1], pos[wr * 3 + 2])
                    + pack.grip[s].length();
            float d = shoulder.distance(want[s]);
            if (k == 0 && d > reachLen * 0.95f) {
                Vector3f step = new Vector3f(want[s]).sub(shoulder).mul(1 - reachLen * 0.95f / d);
                for (int b = 0; b < n; b++) {
                    pos[b * 3] += step.x;
                    pos[b * 3 + 1] += step.y;
                    pos[b * 3 + 2] += step.z;
                }
            }
            Survivors.reachPoint(pack, pos, turn, s, want[s]);
        }

        Matrix4f[] skin = Survivors.skinOf(pack, pos, turn);
        // all but the arms drawn in to the nearer shoulder, so nothing else of her shows
        boolean[] keep = new boolean[n];
        for (int s = 0; s < 2; s++) {
            if (want[s] == null) continue;
            for (String part : new String[]{"UpperArm", "Forearm", "Hand", "Finger0", "Finger1", "Finger01", "Finger11"}) {
                int b = pack.skin.bone("Bip01_" + (s == 0 ? "R" : "L") + "_" + part);
                if (b >= 0) keep[b] = true;
            }
        }
        for (int b = 0; b < n; b++) {
            if (keep[b]) continue;
            int s = nearer(pack, pos, b);
            int root = pack.upper[s];
            if (root < 0) continue;
            skin[b] = new Matrix4f().zero().m30(pos[root * 3]).m31(pos[root * 3 + 1]).m32(pos[root * 3 + 2]).m33(1);
        }

        if (buffersPack != pack) {
            buffersPack = pack;
            buffers = new Object[2][];
        }
        buffer = 1 - buffer;
        if (buffers[buffer] == null) buffers[buffer] = new Object[pack.skin.groups.length * 2];
        pose.pushPose();
        pose.mulPose(place);
        NpcSkinDraw.submitWith(pack.skin, skin,
                g -> g.piece.equals("body") || o.clothes().containsKey(g.piece),
                g -> g.piece.equals("body") ? o.body() : o.clothes().get(g.piece),
                buffers[buffer], light, OverlayTexture.NO_OVERLAY, pose, out);
        pose.popPose();
        return true;
    }

    /** The arm bones a posed gun gives, by name without "Bip01_", then each side's four fingers split from Finger1. */
    public static final String[] POSED = {"R_UpperArm", "R_Forearm", "R_Hand", "R_Finger0", "R_Finger1",
            "L_UpperArm", "L_Forearm", "L_Hand", "L_Finger0", "L_Finger1"};
    private static final String[] FINGERS = {"index", "middle", "ring", "little"};

    /** A pack's meshes with each finger of Finger1 following a bone of its own, worked out once a pack. */
    private record Split(NpcSkin.Group[] groups, NpcSkin.Group[] whole, int[] side) {
    }

    /**
     * Her whole body's meshes with the four fingers split off Finger1 as in {@link #split}: bones n + side * 4 + finger
     * (index, middle, ring, little; side 0 right), for the third-person posed guns.
     */
    static NpcSkin.Group[] wholeSplit(SurvivorPack pack) {
        return SPLITS.computeIfAbsent(pack, SurvivorArms::split).whole();
    }

    private static final java.util.Map<SurvivorPack, Split> SPLITS = new java.util.WeakHashMap<>();
    private static Object[][] posedBuffers = new Object[2][];
    private static int posedBuffer;
    private static SurvivorPack posedPack;

    /**
     * Her arms exactly as posed for a gun (the posing tool, Josiah 2026-11-03): each bone's place and turn (blocks, the
     * frame of pose), by its name in {@link #POSED} and R_Finger1_index etc. for the split fingers. A side without its
     * bones isn't drawn. Returns false (nothing drawn) when this player isn't drawn as a survivor.
     */
    public static boolean drawPosed(PoseStack pose, SubmitNodeCollector out, int light, Player player,
                                    java.util.Map<String, Matrix4f> bones) {
        Survivors.Outfit o = player == null ? null : Survivors.outfit(player);
        if (o == null) return false;
        SurvivorPack pack = o.pack();
        NpcSkin skin = pack.skin;
        int n = skin.boneNames.length;
        Split split = SPLITS.computeIfAbsent(pack, SurvivorArms::split);
        Matrix4f[] m = new Matrix4f[n + 8];
        boolean[] on = new boolean[2];
        Vector3f[] shoulder = new Vector3f[2];
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "R_" : "L_";
            Matrix4f up = bones.get(side + "UpperArm");
            if (up == null) continue;
            on[s] = true;
            shoulder[s] = up.getTranslation(new Vector3f());
            for (String part : new String[]{"UpperArm", "Forearm", "Hand", "Finger0", "Finger1"}) {
                int b = skin.bone("Bip01_" + side + part);
                Matrix4f w = bones.get(side + part);
                if (b >= 0 && w != null) m[b] = new Matrix4f(w).mul(skin.inverse[b]);
            }
            int f1 = skin.bone("Bip01_" + side + "Finger1");
            for (int k = 0; k < 4; k++) {
                Matrix4f w = bones.get(side + "Finger1_" + FINGERS[k]);
                if (w == null) w = bones.get(side + "Finger1");
                m[n + s * 4 + k] = w == null || f1 < 0 ? null : new Matrix4f(w).mul(skin.inverse[f1]);
            }
        }
        if (!on[0] && !on[1]) return false;
        // everything else drawn in to its shoulder (a side not drawn, to the other one), so only her arms show
        for (int b = 0; b < n + 8; b++) {
            if (m[b] != null) continue;
            int s = b < n ? split.side[b] : (b - n) / 4;
            if (!on[s]) s = 1 - s;
            Vector3f at = shoulder[s];
            m[b] = new Matrix4f().zero().m30(at.x).m31(at.y).m32(at.z).m33(1);
        }
        // a side not drawn: its arm drawn in too
        for (int s = 0; s < 2; s++) {
            if (on[s]) continue;
            Vector3f at = shoulder[1 - s];
            for (String part : new String[]{"UpperArm", "Forearm", "Hand", "Finger0", "Finger1"}) {
                int b = skin.bone("Bip01_" + (s == 0 ? "R_" : "L_") + part);
                if (b >= 0) m[b] = new Matrix4f().zero().m30(at.x).m31(at.y).m32(at.z).m33(1);
            }
            for (int k = 0; k < 4; k++) m[n + s * 4 + k] = new Matrix4f().zero().m30(at.x).m31(at.y).m32(at.z).m33(1);
        }
        if (posedPack != pack) {
            posedPack = pack;
            posedBuffers = new Object[2][];
        }
        posedBuffer = 1 - posedBuffer;
        if (posedBuffers[posedBuffer] == null) posedBuffers[posedBuffer] = new Object[split.groups.length * 2];
        NpcSkinDraw.submitGroups(split.groups, m, 0,
                g -> g.piece.equals("body") || o.clothes().containsKey(g.piece),
                g -> g.piece.equals("body") ? o.body() : o.clothes().get(g.piece),
                posedBuffers[posedBuffer], light, OverlayTexture.NO_OVERLAY, pose, out);
        return true;
    }

    /**
     * Zomboid's four fingers are one bone (Finger1); the posing tool splits them by where each vertex lies across the
     * hand (its Finger1-local y: four clusters, the index finger at +y on the right hand, -y on the left) and curls each
     * on its own. The same split here: vertices mostly off Finger1 (under 5%) stay on it.
     */
    private static Split split(SurvivorPack pack) {
        NpcSkin skin = pack.skin;
        int n = skin.boneNames.length;
        int[] f1 = {skin.bone("Bip01_R_Finger1"), skin.bone("Bip01_L_Finger1")};
        // the clusters from her bare body; clothes (gloves) take the nearest
        float[][] centre = new float[2][];
        for (int s = 0; s < 2; s++) {
            if (f1[s] < 0) continue;
            it.unimi.dsi.fastutil.floats.FloatArrayList ys = new it.unimi.dsi.fastutil.floats.FloatArrayList();
            for (NpcSkin.Group g : skin.groups) {
                if (!g.piece.equals("body") || g.level != 0) continue;
                for (int v = 0; v < g.vertices; v++) {
                    float y = fingerY(skin, g, v, f1[s]);
                    if (!Float.isNaN(y)) ys.add(y);
                }
            }
            if (ys.size() < 8) continue;
            float[] a = ys.toFloatArray();
            java.util.Arrays.sort(a);
            float[] c = {a[a.length / 8], a[a.length * 3 / 8], a[a.length * 5 / 8], a[a.length * 7 / 8]};
            for (int it = 0; it < 50; it++) {
                float[] sum = new float[4];
                int[] cnt = new int[4];
                for (float y : a) {
                    int k = nearest(c, y);
                    sum[k] += y;
                    cnt[k]++;
                }
                for (int k = 0; k < 4; k++) if (cnt[k] > 0) c[k] = sum[k] / cnt[k];
            }
            centre[s] = c;
        }
        boolean[] lowerBones = new boolean[256];
        boolean[] armBones = new boolean[256];
        for (String sd : new String[]{"R_", "L_"}) {
            for (String part : new String[]{"UpperArm", "Forearm", "Hand", "Finger0", "Finger1"}) {
                int b = skin.bone("Bip01_" + sd + part);
                if (b >= 0) {
                    armBones[b] = true;
                    lowerBones[b] = !part.equals("UpperArm");
                }
            }
        }
        lowerBones[255] = false;
        armOnly = armBones;
        NpcSkin.Group[] groups = new NpcSkin.Group[skin.groups.length], whole = new NpcSkin.Group[skin.groups.length];
        for (int i = 0; i < groups.length; i++) {
            NpcSkin.Group g = skin.groups[i];
            byte[] j = g.joint.clone();
            for (int v = 0; v < g.vertices; v++) {
                for (int s = 0; s < 2; s++) {
                    if (centre[s] == null) continue;
                    float y = fingerY(skin, g, v, f1[s]);
                    if (Float.isNaN(y)) continue;
                    // the clusters run up y: on the right hand the highest is the index finger, on the left the lowest
                    int k = nearest(centre[s], y);
                    int finger = s == 0 ? 3 - k : k;
                    for (int q = 0; q < 4; q++) {
                        if ((j[v * 4 + q] & 255) == f1[s] && g.weight[v * 4 + q] > 0) j[v * 4 + q] = (byte) (n + s * 4 + finger);
                    }
                }
            }
            whole[i] = g.withJoints(j);
            NpcSkin.Group c = g.withJoints(j);
            // only her forearms, hands and fingers, as the tool draws them: the upper arms would sit by her cheek on
            // the sights (a triangle stays if each corner follows those bones wholly, mostly the forearm or below)
            it.unimi.dsi.fastutil.ints.IntArrayList keep = new it.unimi.dsi.fastutil.ints.IntArrayList();
            for (int t = 0; t + 2 < g.tris.length; t += 3) {
                if (lower(lowerBones, g, g.tris[t]) && lower(lowerBones, g, g.tris[t + 1]) && lower(lowerBones, g, g.tris[t + 2])) {
                    keep.add(g.tris[t]);
                    keep.add(g.tris[t + 1]);
                    keep.add(g.tris[t + 2]);
                }
            }
            c.tris = keep.toIntArray();
            groups[i] = c;
        }
        // which shoulder each bone is nearer at rest, for drawing the rest of her in
        int[] side = new int[n];
        Vector3f r = rest(skin, skin.bone("Bip01_R_UpperArm")), l = rest(skin, skin.bone("Bip01_L_UpperArm"));
        for (int b = 0; b < n; b++) {
            Vector3f p = rest(skin, b);
            side[b] = r == null ? 1 : l == null ? 0 : p.distanceSquared(r) <= p.distanceSquared(l) ? 0 : 1;
        }
        return new Split(groups, whole, side);
    }

    private static boolean[] armOnly;

    /** Whether a vertex follows only her arm bones and mostly one from the forearm down. */
    private static boolean lower(boolean[] lowerBones, NpcSkin.Group g, int v) {
        float arm = 0, best = -1;
        int top = -1;
        for (int q = 0; q < 4; q++) {
            float w = g.weight[v * 4 + q];
            if (w <= 0) continue;
            int b = g.joint[v * 4 + q] & 255;
            if (armOnly[b]) arm += w;
            if (w > best) {
                best = w;
                top = b;
            }
        }
        return arm > 0.99f && top >= 0 && lowerBones[top];
    }

    /** A vertex's y in Finger1's own frame at rest, if it follows that bone by 5% or more; else NaN. */
    private static float fingerY(NpcSkin skin, NpcSkin.Group g, int v, int bone) {
        for (int q = 0; q < 4; q++) {
            if ((g.joint[v * 4 + q] & 255) == bone && g.weight[v * 4 + q] >= 0.05f) {
                return skin.inverse[bone].transformPosition(new Vector3f(g.pos[v * 3], g.pos[v * 3 + 1], g.pos[v * 3 + 2])).y;
            }
        }
        return Float.NaN;
    }

    private static int nearest(float[] c, float y) {
        int best = 0;
        for (int k = 1; k < c.length; k++) if (Math.abs(c[k] - y) < Math.abs(c[best] - y)) best = k;
        return best;
    }

    private static Vector3f rest(NpcSkin skin, int b) {
        return b < 0 ? null : new Matrix4f(skin.inverse[b]).invert().getTranslation(new Vector3f());
    }

    /** Which shoulder a bone is nearer (0 right, 1 left). */
    private static int nearer(SurvivorPack pack, float[] pos, int b) {
        int r = pack.upper[0], l = pack.upper[1];
        if (r < 0) return 1;
        if (l < 0) return 0;
        Vector3f p = new Vector3f(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]);
        return p.distanceSquared(pos[r * 3], pos[r * 3 + 1], pos[r * 3 + 2]) <= p.distanceSquared(pos[l * 3], pos[l * 3 + 1], pos[l * 3 + 2]) ? 0 : 1;
    }

    /**
     * Minecraft's own first-person arm (an empty hand, a map): her arm instead, its hand where Minecraft's fist is.
     * pose is the vanilla arm's frame. Returns whether hers was drawn.
     */
    public static boolean vanillaHand(PoseStack pose, SubmitNodeCollector out, int light, HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        Matrix4f frame = new Matrix4f(pose.last().pose());
        Matrix4f back = new Matrix4f(frame).invert();
        // the eye and the view's axes, in the arm's frame
        Vector3f eye = back.transformPosition(new Vector3f());
        Vector3f forward = back.transformDirection(new Vector3f(0, 0, -1));
        Vector3f up = back.transformDirection(new Vector3f(0, 1, 0));
        float scale = 1 / Math.max(1e-3f, forward.length());
        boolean right = arm == HumanoidArm.RIGHT;
        // vanilla's fist: its arm hangs 12 px from the shoulder pivot (-5 or 5, 2) in this frame, model y down
        Vector3f fist = new Vector3f((right ? -6 : 6) / 16f, 11f / 16f, 0);
        return draw(pose, out, light, player, eye, forward, up, scale, right ? fist : null, right ? null : fist, false);
    }
}
