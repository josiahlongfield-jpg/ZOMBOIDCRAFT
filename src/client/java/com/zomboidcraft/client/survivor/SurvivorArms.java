package com.zomboidcraft.client.survivor;

import com.mojang.blaze3d.vertex.PoseStack;
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
        return draw(pose, out, light, player, eye, forward, up, scale, rightHand, leftHand, null, null, aiming);
    }

    /**
     * The same, for a gun held in both hands (elbow and palm are Minecraft's arm hints, kept for callers; her hands
     * take Zomboid's own grip from the clip instead, turned onto the gun between rightHand and leftHand).
     */
    public static boolean draw(PoseStack pose, SubmitNodeCollector out, int light, Player player, Vector3f eye, Vector3f forward,
                               Vector3f up, float scale, Vector3f rightHand, Vector3f leftHand, Vector3f[] elbow,
                               Vector3f[] palm, boolean aiming) {
        Survivors.Outfit o = player == null ? null : Survivors.outfit(player);
        if (o == null) return false;
        SurvivorPack pack = o.pack();
        int n = pack.skin.boneNames.length;
        float[] pos = new float[n * 3], turn = new float[n * 4];
        String kind = Survivors.kind(player.getMainHandItem());
        String clip = !aiming ? "idle" : kind.equals("pistol") ? "pistol_aim" : "rifle_aim";
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
        // each hand's turn (her space): Zomboid's own grip from the clip, carried over to where this gun really lies.
        // The clip holds an unseen long gun from her shooting hand to her other hand; the gun here runs from its grip
        // to its foregrip. Turning the clip's gun onto this one turns both her hands with it, so they keep Zomboid's
        // hold (fingers round the grip, the other hand under the handguard). A pistol is held as the clip holds it.
        Quaternionf[] hold = new Quaternionf[2];
        if (want[0] != null && want[1] != null && !kind.equals("pistol")) {
            Vector3f upHers = back.transformDirection(new Vector3f(up));
            Quaternionf clipGun = gunFrame(new Vector3f(Survivors.gripAt(pack, pos, turn, 1)).sub(Survivors.gripAt(pack, pos, turn, 0)),
                    new Vector3f(0, 1, 0));
            Quaternionf thisGun = gunFrame(new Vector3f(want[1]).sub(want[0]), upHers);
            if (clipGun != null && thisGun != null) {
                Quaternionf onto = new Quaternionf(thisGun).mul(clipGun.conjugate());
                for (int s = 0; s < 2; s++) {
                    int h = pack.hand[s];
                    if (h < 0) continue;
                    hold[s] = new Quaternionf(onto).mul(new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3])).normalize();
                }
            }
        }
        // where the grips are out of her reach, she steps in (only her arms show, so the body just moves); a few
        // rounds so that bringing one shoulder in doesn't leave the other short
        for (int round = 0; round < 3; round++) {
            for (int s = 0; s < 2; s++) {
                if (want[s] == null || pack.upper[s] < 0 || pack.fore[s] < 0 || pack.hand[s] < 0) continue;
                int sh = pack.upper[s], el = pack.fore[s], wr = pack.hand[s];
                Vector3f shoulder = new Vector3f(pos[sh * 3], pos[sh * 3 + 1], pos[sh * 3 + 2]);
                float reachLen = shoulder.distance(pos[el * 3], pos[el * 3 + 1], pos[el * 3 + 2])
                        + new Vector3f(pos[el * 3], pos[el * 3 + 1], pos[el * 3 + 2]).distance(pos[wr * 3], pos[wr * 3 + 1], pos[wr * 3 + 2])
                        + pack.grip[s].length();
                float d = shoulder.distance(want[s]);
                if (d <= reachLen * 0.93f) continue;
                Vector3f step = new Vector3f(want[s]).sub(shoulder).mul(1 - reachLen * 0.93f / d);
                for (int b = 0; b < n; b++) {
                    pos[b * 3] += step.x;
                    pos[b * 3 + 1] += step.y;
                    pos[b * 3 + 2] += step.z;
                }
            }
        }
        for (int s = 0; s < 2; s++) {
            if (want[s] == null || pack.upper[s] < 0 || pack.fore[s] < 0 || pack.hand[s] < 0) continue;
            if (hold[s] == null) {
                Survivors.reachPoint(pack, pos, turn, s, want[s]);
                continue;
            }
            int el = pack.fore[s], h = pack.hand[s];
            // the wrist where the turned hand's grip lands on the point, the elbow bent as the clip bends it
            Vector3f wrist = new Vector3f(want[s]).sub(hold[s].transform(new Vector3f(pack.grip[s])));
            Survivors.reach(pack, pos, turn, s, wrist);
            // half the wrist's roll taken by the forearm, so it doesn't wring
            Vector3f axis = new Vector3f(pos[h * 3] - pos[el * 3], pos[h * 3 + 1] - pos[el * 3 + 1], pos[h * 3 + 2] - pos[el * 3 + 2]).normalize();
            Quaternionf now = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
            Quaternionf change = new Quaternionf(hold[s]).mul(now.conjugate());
            float along = change.x * axis.x + change.y * axis.y + change.z * axis.z;
            Quaternionf roll = new Quaternionf(axis.x * along, axis.y * along, axis.z * along, change.w);
            if (roll.lengthSquared() > 1e-8f) {
                roll.normalize();
                Survivors.rotateBelow(pack, pos, turn, el, new Quaternionf().slerp(roll, 0.5f));
            }
            now.set(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
            Survivors.rotateBelow(pack, pos, turn, h, new Quaternionf(hold[s]).mul(now.conjugate()).normalize());
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

    /** A gun's turn from the way it points and the way up is (null if they're the same line). */
    private static Quaternionf gunFrame(Vector3f along, Vector3f up) {
        if (along.lengthSquared() < 1e-8f) return null;
        Vector3f a = new Vector3f(along).normalize();
        Vector3f u = new Vector3f(up).sub(new Vector3f(a).mul(up.dot(a)));
        if (u.lengthSquared() < 1e-6f) return null;
        u.normalize();
        Vector3f side = new Vector3f(a).cross(u);
        return new Quaternionf().setFromNormalized(new Matrix3f(a, u, side));
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
     * Minecraft's own first-person arm: none at all for an empty hand, her arm (its hand where Minecraft's fist is)
     * for a map.
     * pose is the vanilla arm's frame. Returns whether hers was drawn.
     */
    public static boolean vanillaHand(PoseStack pose, SubmitNodeCollector out, int light, HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        // an empty hand: Zomboid never holds a bare arm up in front of her, so nothing is drawn (and no Minecraft arm)
        if (player.getMainHandItem().isEmpty()) return shown(player);
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
