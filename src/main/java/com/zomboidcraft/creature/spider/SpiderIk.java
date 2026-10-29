package com.zomboidcraft.creature.spider;

import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fits the animation to where the Harvestman really is, on top of its clips (SpiderAnimator): every foot finds the
 * surface under where the clip puts it (a stair, a slope, a wall or ceiling it's holding, a ledge) and the leg bends to
 * put its point there, still lifting and swinging as the clip does. Where there's nothing to stand on that far out (a
 * beam, a ledge, the top of a wall) or the clip's foot would be in a wall (a corridor), it draws that foot in toward its
 * body until it finds footing, so it perches narrow where it must. Its body rides the average of its feet and tilts
 * with the ground; under a low ceiling or through a doorway it sinks and folds forward at the waist; with a leg gone it
 * leans toward that side; a slipping foot lurches it; and its head turns to what it's listening to. Model space
 * throughout (+y off the surface it holds, +z ahead, +x its left). Server and client both run it.
 */
final class SpiderIk {
    /** How far above and below the clip's foot it looks for a surface. */
    private static final float ABOVE = 1.05f, BELOW = 1.5f;
    /** The most it sinks its body, and how far it folds forward at the waist beyond that. */
    private static final float SINK = 0.55f, FOLD = 0.95f;
    /** How far in it tries a foot that's found nothing (as a share of how far out the clip has it). */
    private static final float[] IN = {1f, 0.8f, 0.64f, 0.5f, 0.38f};

    private final SpiderModel model;
    /** Each foot's ground (how far the surface under it is above the clip's floor) and how far in it's drawn, eased. */
    private final float[] ground = new float[4], spread = {1, 1, 1, 1};
    private float lift, pitch, roll, look, stumble;
    private int stumbleLeg, stumblesSeen = -1;
    private final Vector3f lookAt = new Vector3f();
    /** Where it found footing for each foot this time. */
    private final float[] want = new float[4], wantSpread = {1, 1, 1, 1};

    SpiderIk(SpiderModel model) {
        this.model = model;
    }

    /**
     * @param place model space to the entity's position (rotation and offset)
     * @param origin the entity's position place is relative to
     * @param drop how far below its standing height its body is held (a low ceiling, a doorway)
     */
    void apply(HarvestmanEntity e, SpiderAnimator a, Level level, Matrix4f place, Vec3 origin, float drop, float dt) {
        Quaternionf[] q = a.q;
        Vector3f[] p = a.p;
        boolean dead = e.pose() == HarvestmanEntity.DEAD;
        int lost = e.lost();

        // where the clip puts each foot, and the surface under it (drawn in where there's none that far out)
        Vector3f[] clip = new Vector3f[4];
        Matrix4f toModel = new Matrix4f(place).invert();
        boolean feel = !dead && !a.airborne();
        Vector3f centre = new Vector3f(p[model.pelvis].x, 0, p[model.pelvis].z);
        for (int l = 0; l < 4; l++) {
            clip[l] = tipOf(a, l);
            if (!feel || (lost >> l & 1) != 0) {
                want[l] = 0;
                wantSpread[l] = 1;
                continue;
            }
            boolean found = false;
            for (float s : IN) {
                float x = centre.x + (clip[l].x - centre.x) * s, z = centre.z + (clip[l].z - centre.z) * s;
                float g = feel(e, level, place, toModel, origin, x, z);
                if (!Float.isNaN(g)) {
                    want[l] = g;
                    wantSpread[l] = s;
                    found = true;
                    break;
                }
            }
            if (!found) {
                want[l] = 0;
                wantSpread[l] = 1;
            }
        }
        float kg = 1 - (float) Math.exp(-dt * 9), kw = 1 - (float) Math.exp(-dt * 4);
        for (int l = 0; l < 4; l++) {
            ground[l] = Mth.lerp(kg, ground[l], want[l]);
            spread[l] = Mth.lerp(kw, spread[l], wantSpread[l]);
        }

        // the body over its feet: risen or sunk with them, tilted to them, and leaning off any leg it's lost
        float sum = 0, n = 0;
        for (int l = 0; l < 4; l++) {
            if ((lost >> l & 1) == 0) {
                sum += ground[l];
                n++;
            }
        }
        float avg = n > 0 ? sum / n : 0;
        float front = (ground[0] + ground[2]) / 2, rear = (ground[1] + ground[3]) / 2;
        float left = (ground[0] + ground[1]) / 2, right = (ground[2] + ground[3]) / 2;
        float pitchWant = dead ? 0 : Mth.clamp(-(float) Math.atan2(front - rear, 2.7f), -0.4f, 0.4f) * 0.75f;
        float rollWant = dead ? 0 : Mth.clamp((float) Math.atan2(left - right, 2.4f), -0.35f, 0.35f) * 0.7f;
        for (int l = 0; l < 4 && !dead; l++) {
            if ((lost >> l & 1) != 0) {
                rollWant += l < 2 ? -0.11f : 0.11f;
                pitchWant += (l & 1) == 0 ? 0.09f : -0.05f;
            }
        }
        drop = dead ? 0 : Mth.clamp(drop, 0, SINK + 1.2f);
        float ks = 1 - (float) Math.exp(-dt * 5);
        lift = Mth.lerp(ks, lift, dead ? 0 : avg * 0.8f - Math.min(drop, SINK));
        pitch = Mth.lerp(ks, pitch, pitchWant);
        roll = Mth.lerp(ks, roll, rollWant);
        float fold = Mth.clamp((drop - SINK) / 0.8f, 0, 1) * FOLD;

        // a slipping foot: it lurches down toward that side
        if (stumblesSeen >= 0 && e.stumbles() != stumblesSeen) {
            stumble = 1;
            stumbleLeg = e.stumbles() & 3;
        }
        stumblesSeen = e.stumbles();
        stumble = Math.max(0, stumble - dt * 3);
        float sk = (float) Math.sin(stumble * Math.PI);
        float lurchX = (stumbleLeg < 2 ? 0.08f : -0.08f) * sk;

        Vector3f pelvis = new Vector3f(p[model.pelvis]);
        Quaternionf tilt = new Quaternionf().rotateZ(roll + (stumbleLeg < 2 ? -0.18f : 0.18f) * sk).rotateX(pitch);
        for (int i = 0; i < q.length; i++) {
            turnAbout(q, p, i, pelvis, tilt);
            p[i].add(lurchX, lift - 0.15f * sk, 0);
        }
        if (fold > 1e-3f && model.spine >= 0) {
            Quaternionf bow = new Quaternionf().rotateX(fold);
            Vector3f at = new Vector3f(p[model.spine]);
            for (int i : model.below[model.spine]) {
                turnAbout(q, p, i, at, bow);
            }
        }

        // each leg bent to put its point on its surface, lifting off it as the clip does
        if (!dead) {
            for (int l = 0; l < 4; l++) {
                if ((lost >> l & 1) != 0) {
                    continue;
                }
                float s = spread[l];
                Vector3f target = new Vector3f(centre.x + (clip[l].x - centre.x) * s, clip[l].y + ground[l], centre.z + (clip[l].z - centre.z) * s);
                reach(a, l, target);
            }
        }

        // its head to what it's listening to
        Vec3 heard = e.lookingAt();
        look = Mth.lerp(1 - (float) Math.exp(-dt * 4), look, heard != null && !dead ? 1 : 0);
        if (heard != null) {
            Vec3 h = heard.subtract(origin);
            lookAt.set(toModel.transformPosition(new Vector3f((float) h.x, (float) h.y, (float) h.z)));
        }
        if (look > 0.01f && model.neck >= 0 && model.head >= 0) {
            turnHead(a);
        }
    }

    /**
     * The surface under a point of the model's floor: its height (model y) if there's something to stand on within
     * reach and the point isn't inside a wall, otherwise NaN.
     */
    private static float feel(HarvestmanEntity e, Level level, Matrix4f place, Matrix4f toModel, Vec3 origin, float x, float z) {
        Vector3f from = place.transformPosition(new Vector3f(x, ABOVE, z));
        Vector3f to = place.transformPosition(new Vector3f(x, -BELOW, z));
        Vec3 a = origin.add(from.x, from.y, from.z), b = origin.add(to.x, to.y, to.z);
        // a foot that would start inside something solid (a corridor's wall): no good
        BlockPos at = BlockPos.containing(a);
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
            return Float.NaN;
        }
        BlockHitResult r = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        if (r.getType() == HitResult.Type.MISS) {
            return Float.NaN;
        }
        Vec3 h = r.getLocation().subtract(origin);
        return toModel.transformPosition(new Vector3f((float) h.x, (float) h.y, (float) h.z)).y;
    }

    /** A leg's point as posed now. */
    private Vector3f tipOf(SpiderAnimator a, int l) {
        int ankle = model.legs[l][2];
        return a.q[ankle].transform(new Vector3f(model.tip[l])).add(a.p[ankle]);
    }

    /** Bends a leg (ankle, then knee, then hip, a few times over) to bring its point to the target. */
    private void reach(SpiderAnimator a, int l, Vector3f target) {
        Quaternionf[] q = a.q;
        Vector3f[] p = a.p;
        for (int pass = 0; pass < 5; pass++) {
            for (int k = 2; k >= 0; k--) {
                int bone = model.legs[l][k];
                Vector3f joint = new Vector3f(p[bone]);
                Vector3f end = tipOf(a, l).sub(joint), want = new Vector3f(target).sub(joint);
                if (end.lengthSquared() < 1e-6f || want.lengthSquared() < 1e-6f) {
                    continue;
                }
                Quaternionf r = new Quaternionf().rotationTo(end.normalize(), want.normalize());
                float angle = r.angle();
                if (angle < 1e-4f) {
                    continue;
                }
                // the hip swings the leg in and out; the knee and ankle do the bending
                float most = k == 0 ? 0.35f : 0.6f;
                if (angle > most) {
                    r = new Quaternionf().slerp(r, most / angle);
                }
                turnAbout(q, p, bone, joint, r);
                for (int i : model.below[bone]) {
                    turnAbout(q, p, i, joint, r);
                }
            }
            if (tipOf(a, l).distanceSquared(target) < 1e-4f) {
                break;
            }
        }
    }

    /** Turns the neck and head (half each) toward what it's looking at, no further than it could. */
    private void turnHead(SpiderAnimator a) {
        int head = model.head, neck = model.neck;
        Quaternionf rest = new Quaternionf(model.bindTurn[head]).conjugate();
        Vector3f facing = new Quaternionf(a.q[head]).mul(rest).transform(new Vector3f(0, 0, 1));
        Vector3f want = new Vector3f(lookAt).sub(a.p[head]);
        if (want.lengthSquared() < 0.25f) {
            return;
        }
        want.normalize();
        Quaternionf r = new Quaternionf().rotationTo(facing, want);
        float angle = r.angle(), most = 1.1f;
        float share = look * (angle > most ? most / angle : 1);
        Quaternionf half = new Quaternionf().slerp(r, share * 0.5f);
        Vector3f at = new Vector3f(a.p[neck]);
        turnAbout(a.q, a.p, neck, at, half);
        for (int i : model.below[neck]) {
            turnAbout(a.q, a.p, i, at, half);
        }
        at.set(a.p[head]);
        turnAbout(a.q, a.p, head, at, half);
        for (int i : model.below[head]) {
            turnAbout(a.q, a.p, i, at, half);
        }
    }

    /** One bone turned by r about a point. */
    private static void turnAbout(Quaternionf[] q, Vector3f[] p, int i, Vector3f about, Quaternionf r) {
        q[i].premul(r).normalize();
        p[i].sub(about);
        r.transform(p[i]);
        p[i].add(about);
    }
}
