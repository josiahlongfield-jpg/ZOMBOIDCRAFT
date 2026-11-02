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
        return drawPlain(pose, out, light, player, eye, forward, up, scale, rightHand, leftHand, aiming);
    }

    private static boolean drawPlain(PoseStack pose, SubmitNodeCollector out, int light, Player player, Vector3f eye, Vector3f forward,
                                     Vector3f up, float scale, Vector3f rightHand, Vector3f leftHand, boolean aiming) {
        Survivors.Outfit o = player == null ? null : Survivors.outfit(player);
        if (o == null) return false;
        SurvivorPack pack = o.pack();
        int n = pack.skin.boneNames.length;
        float[] pos = new float[n * 3], turn = new float[n * 4];
        String kind = Survivors.kind(player.getMainHandItem());
        String clip = !aiming ? "idle" : kind.equals("pistol") ? "pistol_aim" : "rifle_aim";
        stand(pack, clip, pos, turn);

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
        reachFor(pack, pos, turn, want);

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

    /** Her pose from a clip, stood still in it (rate 0), straight into it with no fade from the last. */
    private static void stand(SurvivorPack pack, String clip, float[] pos, float[] turn) {
        if (animPack != pack || !clip.equals(animClip)) {
            animPack = pack;
            animClip = clip;
            upper = Survivors.upperBody(pack);
            ANIM.base(pack, clip, 0);
            ANIM.advance(pack, 10);
        }
        ANIM.pose(pack, upper, pos, turn);
    }

    /** How big a pistol of ours is drawn at its real size (its display scale). */
    public static final float PISTOL_REAL = 0.434f;
    /** A pistol's hands sit this far (metres) lower on the grip than its middle, so the slide and sights show over them. */
    private static final float PISTOL_DROP = 0.03f;
    /**
     * Zomboid's pistol hold has both hands flat together, fingers up; each is turned this far (degrees) about its palm
     * so the fingers wrap round the grip, the off hand's over the shooting hand's, the way a pistol is really held.
     */
    private static final float PISTOL_WRAP = 70f;
    /** Whether this player's first-person gun is held as a survivor's pistol. */
    public static boolean pistolHeld(Player player) {
        return shown(player) && Survivors.kind(player.getMainHandItem()).equals("pistol");
    }

    /**
     * Her own aim hold (Zomboid's rifle_aim, or pistol_aim with a pistol), life size, seen from her eyes: where her
     * shooting palm is (x right, y up, -z ahead, metres) and how far apart her palms are. RifleView sizes and places the
     * gun to this, so the gun is drawn at her size rather than her arms at the gun's. Null if she isn't shown.
     */
    public static float[] hold(Player player) {
        Survivors.Outfit o = player == null ? null : Survivors.outfit(player);
        if (o == null) return null;
        SurvivorPack pack = o.pack();
        if (pack.hand[0] < 0 || pack.hand[1] < 0 || pack.head < 0) return null;
        int n = pack.skin.boneNames.length;
        float[] pos = new float[n * 3], turn = new float[n * 4];
        stand(pack, pistolHeld(player) ? "pistol_aim" : "rifle_aim", pos, turn);
        Vector3f palmR = palm(pack, pos, turn, 0), palmL = palm(pack, pos, turn, 1);
        int head = pack.head;
        Vector3f eyes = new Vector3f(pos[head * 3], pos[head * 3 + 1] + 0.09f, pos[head * 3 + 2] + 0.09f);
        // her front along -z, her up +y, her left -x
        Vector3f r = new Vector3f(palmR).sub(eyes);
        return new float[] {-r.x, r.y, -r.z, palmR.distance(palmL)};
    }

    /**
     * Her arms on a gun in first person (RifleView's hand frame): Zomboid's own aim hold, the one she has in third
     * person, carried whole onto the gun. RifleView draws the gun at her size and where her hold puts it (see
     * {@link #hold}), so her arms come out life size here and nothing is stretched or bent to fit. Her shooting palm
     * closes on the grip; the other hand is on the handguard by the hold itself, or goes where the view sends it.
     * Only her forearms and hands are drawn.
     * <p>
     * eye is the eye in the hand frame; rightHand/leftHand the grips (null left hand: not drawn); the elbows are
     * the vanilla arms' and go unused; gun the gun's turn (barrel -z, sights +y); gunScale its display scale;
     * span how far apart the gun has its grip and handguard (hand-frame blocks); guard, if given, where her front palm holds
     * the handguard (hand frame): her hold is laid along grip-to-handguard and sized to it.
     */
    public static boolean drawGun(PoseStack pose, SubmitNodeCollector out, int light, Player player, Vector3f eye,
                                  Vector3f rightHand, Vector3f leftHand, Vector3f rightElbow, Vector3f leftElbow,
                                  Quaternionf gun, float gunScale, float span, Vector3f guard) {
        Survivors.Outfit o = player == null ? null : Survivors.outfit(player);
        if (o == null || rightHand == null) return false;
        SurvivorPack pack = o.pack();
        if (pack.hand[0] < 0 || pack.hand[1] < 0 || pack.fore[0] < 0 || pack.fore[1] < 0) return false;
        int n = pack.skin.boneNames.length;
        float[] pos = new float[n * 3], turn = new float[n * 4];
        boolean pistol = Survivors.kind(player.getMainHandItem()).equals("pistol");
        stand(pack, pistol ? "pistol_aim" : "rifle_aim", pos, turn);
        Vector3f palmR = palm(pack, pos, turn, 0), palmL = palm(pack, pos, turn, 1);
        // her size: the gun's grip-to-handguard as far as her palms are apart in the hold (a pistol: as much bigger
        // than life as it's drawn)
        float apart = palmR.distance(palmL);
        // (a long gun with its handguard given: as far apart as its grip and handguard, which RifleView made her size)
        if (guard != null && !pistol) span = new Vector3f(guard).distance(rightHand);
        float k = pistol || apart < 0.05f || span <= 0 ? gunScale / PISTOL_REAL : span / apart;
        int head = pack.head;
        Vector3f eyes = new Vector3f(pos[head * 3], pos[head * 3 + 1] + 0.09f, pos[head * 3 + 2] + 0.09f);
        // her front along -z, her up +y, her left -x
        Matrix4f place = new Matrix4f().translation(eye).mul(new Matrix4f().set(new Matrix3f(new Vector3f(-1, 0, 0),
                new Vector3f(0, 1, 0), new Vector3f(0, 0, -1)))).scale(k).translate(-eyes.x, -eyes.y, -eyes.z);
        Matrix4f back = new Matrix4f(place).invert();
        Vector3f grip = back.transformPosition(new Vector3f(rightHand));
        Vector3f other = leftHand == null ? null : back.transformPosition(new Vector3f(leftHand));
        Vector3f barrel = back.transformDirection(gun.transform(new Vector3f(0, 0, -1))).normalize();
        Vector3f sights = back.transformDirection(gun.transform(new Vector3f(0, 1, 0))).normalize();

        // the hold carried onto the gun: the clip's own (unseen) gun runs from her shooting palm toward her other palm,
        // or straight ahead for a pistol, sights up
        // (a long gun: her palms' line onto its grip-to-handguard line, so both hands land on it)
        Quaternionf thisGun = gunFrame(guard != null && !pistol
                ? back.transformPosition(new Vector3f(guard)).sub(grip) : barrel, sights);
        Quaternionf clipGun = gunFrame(pistol ? new Vector3f(0, 0, 1) : new Vector3f(palmL).sub(palmR), new Vector3f(0, 1, 0));
        if (thisGun == null || clipGun == null) return false;
        Quaternionf onto = new Quaternionf(thisGun).mul(clipGun.conjugate()).normalize();
        Vector3f at = new Vector3f(grip);
        if (pistol) at.sub(new Vector3f(sights).mul(PISTOL_DROP));
        Vector3f p = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < n; b++) {
            p.set(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]).sub(palmR);
            onto.transform(p).add(at);
            pos[b * 3] = p.x;
            pos[b * 3 + 1] = p.y;
            pos[b * 3 + 2] = p.z;
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            new Quaternionf(onto).mul(q, q).normalize();
            turn[b * 4] = q.x;
            turn[b * 4 + 1] = q.y;
            turn[b * 4 + 2] = q.z;
            turn[b * 4 + 3] = q.w;
        }
        if (other != null) {
            // the other hand: the nearest of the gun's hold to its palm (a stretch of handguard along the barrel, or
            // the one point the view gives, a magazine or the handle), reached for only when the palm isn't on it
            Vector3f along = new Vector3f(barrel).mul(pistol ? 0.01f : 0.08f);
            Vector3f a0 = new Vector3f(other).sub(along), seg = new Vector3f(along).mul(2);
            Vector3f pl = palm(pack, pos, turn, 1);
            float t = Math.max(0, Math.min(1, new Vector3f(pl).sub(a0).dot(seg) / seg.lengthSquared()));
            Vector3f to = new Vector3f(a0).add(new Vector3f(seg).mul(t));
            if (pl.distance(to) > 0.03f) Survivors.reachPoint(pack, pos, turn, 1, to);
        }
        if (pistol) {
            // fingers wrapped round the grip rather than flat together pointing up
            for (int s = 0; s < (other != null ? 2 : 1); s++) {
                int h = pack.hand[s];
                Vector3f pc = palm(pack, pos, turn, s);
                Vector3f normal = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3])
                        .transform(new Vector3f(0, 0, 1));
                turnAbout(pack, pos, turn, h, pc, new Quaternionf().rotationAxis(
                        (float) Math.toRadians(s == 0 ? PISTOL_WRAP : -PISTOL_WRAP), normal.normalize()));
            }
        }
        Matrix4f[] skin = Survivors.skinOf(pack, pos, turn);
        int[][] cut = forearms(pack, other != null);
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
                g -> cut[index(pack, g)],
                buffers[buffer], light, OverlayTexture.NO_OVERLAY, pose, out);
        pose.popPose();
        return true;
    }

    /** Turns a bone and everything below it about a point (her space). */
    private static void turnAbout(SurvivorPack pack, float[] pos, float[] turn, int bone, Vector3f pivot, Quaternionf r) {
        Vector3f d = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < pos.length / 3; b++) {
            boolean under = false;
            for (int p = b; p >= 0 && !under; p = pack.skin.parent[p]) under = p == bone;
            if (!under) continue;
            d.set(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]).sub(pivot);
            r.transform(d).add(pivot);
            pos[b * 3] = d.x;
            pos[b * 3 + 1] = d.y;
            pos[b * 3 + 2] = d.z;
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            new Quaternionf(r).mul(q, q).normalize();
            turn[b * 4] = q.x;
            turn[b * 4 + 1] = q.y;
            turn[b * 4 + 2] = q.z;
            turn[b * 4 + 3] = q.w;
        }
    }

    private static int index(SurvivorPack pack, NpcSkin.Group g) {
        NpcSkin.Group[] all = pack.skin.groups;
        for (int i = 0; i < all.length; i++) if (all[i] == g) return i;
        return 0;
    }

    private static final java.util.Map<SurvivorPack, int[][][]> FOREARMS = new java.util.WeakHashMap<>();

    /**
     * Each piece's triangles that are all forearm, hand and fingers (both arms, or the right only): what first person
     * shows of her. Cutting the rest away (rather than drawing it in to the shoulders) leaves no strips across the view.
     */
    private static int[][] forearms(SurvivorPack pack, boolean both) {
        int[][][] cached = FOREARMS.computeIfAbsent(pack, p -> new int[2][][]);
        int which = both ? 1 : 0;
        if (cached[which] != null) return cached[which];
        boolean[] keep = new boolean[pack.skin.boneNames.length];
        for (int s = 0; s < (both ? 2 : 1); s++) {
            for (String part : new String[]{"Forearm", "Hand", "Finger0", "Finger1", "Finger01", "Finger11"}) {
                int b = pack.skin.bone("Bip01_" + (s == 0 ? "R" : "L") + "_" + part);
                if (b >= 0) keep[b] = true;
            }
        }
        NpcSkin.Group[] groups = pack.skin.groups;
        int[][] out = new int[groups.length][];
        for (int i = 0; i < groups.length; i++) {
            NpcSkin.Group g = groups[i];
            float[] on = new float[g.vertices];
            for (int v = 0; v < g.vertices; v++) {
                for (int j = 0; j < 4; j++) {
                    int b = g.joint[v * 4 + j] & 0xFF;
                    if (b < keep.length && keep[b]) on[v] += g.weight[v * 4 + j];
                }
            }
            int[] t = g.tris, kept = new int[t.length];
            int c = 0;
            for (int x = 0; x + 2 < t.length; x += 3) {
                if (on[t[x]] < 0.5f || on[t[x + 1]] < 0.5f || on[t[x + 2]] < 0.5f) continue;
                kept[c++] = t[x];
                kept[c++] = t[x + 1];
                kept[c++] = t[x + 2];
            }
            out[i] = java.util.Arrays.copyOf(kept, c);
        }
        cached[which] = out;
        return out;
    }

    /** The middle of her palm this frame (her space). */
    private static Vector3f palm(SurvivorPack pack, float[] pos, float[] turn, int s) {
        int h = pack.hand[s];
        return new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]).transform(new Vector3f(pack.fist[s]))
                .add(pos[h * 3], pos[h * 3 + 1], pos[h * 3 + 2]);
    }

    /** Hands with nothing gun-shaped in them (a wheel, a map): each brought to its point from the clip's pose. */
    private static void reachFor(SurvivorPack pack, float[] pos, float[] turn, Vector3f[] want) {
        int n = pack.skin.boneNames.length;
        // where each wrist has to be for the middle of her palm to land on its grip
        Vector3f[] wristAt = new Vector3f[2];
        for (int s = 0; s < 2; s++) {
            int h = pack.hand[s];
            if (want[s] == null || h < 0) continue;
            Quaternionf q = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
            wristAt[s] = new Vector3f(want[s]).sub(q.transform(new Vector3f(pack.fist[s])));
        }
        // where a wrist is out of her reach, she steps in (only her arms show, so the body just moves), keeping a bend
        // in the elbow; a few rounds so that bringing one shoulder in doesn't leave the other short
        for (int round = 0; round < 3; round++) {
            for (int s = 0; s < 2; s++) {
                if (wristAt[s] == null || pack.upper[s] < 0 || pack.fore[s] < 0 || pack.hand[s] < 0) continue;
                int sh = pack.upper[s], el = pack.fore[s], wr = pack.hand[s];
                Vector3f shoulder = new Vector3f(pos[sh * 3], pos[sh * 3 + 1], pos[sh * 3 + 2]);
                float reachLen = shoulder.distance(pos[el * 3], pos[el * 3 + 1], pos[el * 3 + 2])
                        + new Vector3f(pos[el * 3], pos[el * 3 + 1], pos[el * 3 + 2]).distance(pos[wr * 3], pos[wr * 3 + 1], pos[wr * 3 + 2]);
                float d = shoulder.distance(wristAt[s]);
                if (d <= reachLen * 0.78f) continue;
                Vector3f step = new Vector3f(wristAt[s]).sub(shoulder).mul(1 - reachLen * 0.78f / d);
                for (int b = 0; b < n; b++) {
                    pos[b * 3] += step.x;
                    pos[b * 3 + 1] += step.y;
                    pos[b * 3 + 2] += step.z;
                }
            }
        }
        for (int s = 0; s < 2; s++) {
            if (want[s] == null || pack.upper[s] < 0 || pack.fore[s] < 0 || pack.hand[s] < 0) continue;
            int h = pack.hand[s];
            // the clip's own wrist: the fist's middle brought onto the grip (twice, as the reach turns the hand)
            for (int k = 0; k < 2; k++) {
                Quaternionf now = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
                Survivors.reach(pack, pos, turn, s, new Vector3f(want[s]).sub(now.transform(new Vector3f(pack.fist[s]))));
            }
        }
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
