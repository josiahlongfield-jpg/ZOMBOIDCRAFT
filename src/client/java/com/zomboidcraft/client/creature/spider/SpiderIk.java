package com.zomboidcraft.client.creature.spider;

import com.zomboidcraft.creature.HarvestmanEntity;
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
 * Fits the animation to where the Harvestman really is, each frame, on top of its clips (SpiderAnimator): every foot
 * finds the surface under where the clip puts it (a stair, a slope, a wall or ceiling it's holding, a ledge) and the leg
 * bends to put its point there, still lifting and swinging as the clip does; its body rides the average of its feet and
 * tilts with the ground (nose up a stair, leaning on a slope); under a low ceiling or through a doorway it sinks and
 * folds forward at the waist; with a leg gone it leans toward that side; a slipping foot lurches it; and its head turns
 * to what it's listening to. Model space throughout (+y off the surface it holds, +z ahead, +x its left).
 */
final class SpiderIk {
    /** How far above and below the clip's foot it looks for a surface. */
    private static final float ABOVE = 1.05f, BELOW = 1.5f;
    /** The most it sinks its body, and how far it folds forward at the waist beyond that. */
    private static final float SINK = 0.55f, FOLD = 0.95f;

    private final SpiderModel model;
    /** Each foot's ground: how far the surface under it is above (+) or below the clip's floor, eased. */
    private final float[] ground = new float[4];
    private final boolean[] onGround = new boolean[4];
    private float lift, pitch, roll, look, stumble;
    private int stumbleLeg, stumblesSeen = -1;
    private final Vector3f lookAt = new Vector3f();

    SpiderIk(SpiderModel model) {
        this.model = model;
    }

    /**
     * @param place model space to the entity's drawn position (rotation and offset)
     * @param drawnAt where the entity's drawn
     */
    void apply(HarvestmanEntity e, SpiderAnimator a, Level level, Matrix4f place, Vec3 drawnAt, float dt) {
        Quaternionf[] q = a.q;
        Vector3f[] p = a.p;
        boolean dead = e.pose() == HarvestmanEntity.DEAD;
        int lost = e.lost();
        float k = 1 - (float) Math.exp(-dt * 14);

        // where the clip puts each foot, and the surface under it
        Vector3f[] clip = new Vector3f[4];
        Matrix4f toModel = new Matrix4f(place).invert();
        boolean feel = !dead && !a.airborne();
        for (int l = 0; l < 4; l++) {
            clip[l] = tipOf(a, l);
            float want = 0;
            boolean hit = false;
            if (feel && (lost >> l & 1) == 0) {
                Vector3f from = place.transformPosition(new Vector3f(clip[l].x, ABOVE, clip[l].z));
                Vector3f to = place.transformPosition(new Vector3f(clip[l].x, -BELOW, clip[l].z));
                BlockHitResult r = level.clip(new ClipContext(drawnAt.add(from.x, from.y, from.z), drawnAt.add(to.x, to.y, to.z),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
                if (r.getType() != HitResult.Type.MISS) {
                    Vec3 h = r.getLocation().subtract(drawnAt);
                    want = toModel.transformPosition(new Vector3f((float) h.x, (float) h.y, (float) h.z)).y;
                    hit = true;
                }
            }
            onGround[l] = hit;
            ground[l] = Mth.lerp(k, ground[l], want);
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
        // under something low: how far it must come down (it holds its body ~2.05 off a wall, 2.5 off a floor)
        float drop = dead ? 0 : Mth.clamp(2.05f - e.lift(), 0, SINK + 1.2f);
        float ks = 1 - (float) Math.exp(-dt * 6);
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
                Vector3f target = new Vector3f(clip[l].x, clip[l].y + ground[l], clip[l].z);
                reach(a, l, target);
            }
        }

        // its head to what it's listening to
        Vec3 heard = e.lookingAt();
        look = Mth.lerp(1 - (float) Math.exp(-dt * 5), look, heard != null && !dead ? 1 : 0);
        if (heard != null) {
            Vec3 h = heard.subtract(drawnAt);
            lookAt.set(toModel.transformPosition(new Vector3f((float) h.x, (float) h.y, (float) h.z)));
        }
        if (look > 0.01f && model.neck >= 0 && model.head >= 0) {
            turnHead(a);
        }
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
        for (int pass = 0; pass < 4; pass++) {
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
                // the hip only nudges; the knee and ankle do the bending
                float most = k == 0 ? 0.25f : 0.6f;
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
