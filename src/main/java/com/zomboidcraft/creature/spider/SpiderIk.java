package com.zomboidcraft.creature.spider;

import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
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
 * leans toward that side; a slipping foot lurches it; and its head turns to what it's listening to. Coming to a face
 * across its way (a wall, or the ceiling from a wall) it rears and reaches its front feet on to it before its body
 * turns, its torso leads every turn and its tail trails it, and a foot with nothing under it hooks round the edge on to
 * the face below, so it pours itself from one surface to the next instead of swinging round whole. Drawn in to hide
 * (HarvestmanEntity.tuck) its feet come in under it, its body sinks, its torso curls down and its tail tucks under.
 * Model space throughout (+y off the surface it holds, +z ahead, +x its left). Server and client both run it.
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
    /** How tight it's drawn in, and how far it's reaching on to a face ahead of it (and where that face is), eased. */
    private float tuck, ahead, aheadZ = 3;
    /** How its body is turning (model space, radians a second), eased: its torso leads the turn and its tail trails. */
    private final Vector3f spin = new Vector3f();
    private final Quaternionf lastTurn = new Quaternionf();
    private boolean turnSeen;
    /** Feet hooked round an edge where there's nothing under them: how far, where to, and where they're going. */
    private final float[] hook = new float[4];
    private final Vector3f[] hookAt = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f[] hookWant = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final boolean[] hooking = new boolean[4];

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

        // how it's drawn in, and how its body's turning (from how it lies now against how it lay last time)
        tuck = Mth.lerp(1 - (float) Math.exp(-dt * 4), tuck, dead ? 0 : e.tuck());
        Quaternionf lies = place.getNormalizedRotation(new Quaternionf());
        Vector3f spinWant = new Vector3f();
        if (turnSeen && dt > 1e-4f && !dead) {
            Quaternionf d = new Quaternionf(lastTurn).conjugate().mul(lies);
            if (d.w < 0) {
                d.set(-d.x, -d.y, -d.z, -d.w);
            }
            AxisAngle4f aa = new AxisAngle4f(d);
            if (Float.isFinite(aa.angle) && aa.angle > 1e-5f) {
                spinWant.set(aa.x, aa.y, aa.z).normalize().mul(aa.angle / dt);
                if (spinWant.length() > 4) {
                    spinWant.normalize(4);
                }
            }
        }
        lastTurn.set(lies);
        turnSeen = true;
        spin.lerp(spinWant, 1 - (float) Math.exp(-dt * 6));

        // a face across its way, a wall it's coming to (or the ceiling as it climbs one): it rears to it and puts its
        // front feet up on it before its body turns
        float aheadWant = 0;
        if (feel) {
            Vector3f from = place.transformPosition(new Vector3f(0, 0.9f, 0)), to = place.transformPosition(new Vector3f(0, 0.9f, 3));
            BlockHitResult r = level.clip(new ClipContext(origin.add(from.x, from.y, from.z), origin.add(to.x, to.y, to.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            if (r.getType() != HitResult.Type.MISS) {
                Vector3f n = toModel.transformDirection(new Vector3f(r.getDirection().getStepX(), r.getDirection().getStepY(), r.getDirection().getStepZ()));
                if (n.z < -0.5f) {
                    Vec3 h = r.getLocation().subtract(origin);
                    aheadZ = toModel.transformPosition(new Vector3f((float) h.x, (float) h.y, (float) h.z)).z;
                    aheadWant = Mth.clamp((2.9f - aheadZ) / 1.5f, 0, 1);
                }
            }
        }
        ahead = Mth.lerp(1 - (float) Math.exp(-dt * 5), ahead, aheadWant * (1 - tuck));
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
            hooking[l] = false;
            if (!found) {
                want[l] = 0;
                wantSpread[l] = 1;
                // nothing under it out there: it hooks the foot round the edge on to the face below
                Vector3f round = hookFace(e, level, place, toModel, origin, clip[l], centre);
                if (round != null) {
                    hookWant[l].set(round);
                    hooking[l] = true;
                }
            }
        }
        float kg = 1 - (float) Math.exp(-dt * 9), kw = 1 - (float) Math.exp(-dt * 4);
        for (int l = 0; l < 4; l++) {
            ground[l] = Mth.lerp(kg, ground[l], want[l]);
            spread[l] = Mth.lerp(kw, spread[l], wantSpread[l]);
            if (hooking[l]) {
                if (hook[l] < 0.05f) {
                    hookAt[l].set(hookWant[l]);
                }
                hookAt[l].lerp(hookWant[l], kg);
            }
            hook[l] = Mth.lerp(kw, hook[l], hooking[l] ? 1 : 0);
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
        float pitchWant = dead ? 0 : Mth.clamp(-(float) Math.atan2(front - rear, 2.7f), -0.4f, 0.4f) * 0.75f - 0.3f * ahead;
        float rollWant = dead ? 0 : Mth.clamp((float) Math.atan2(left - right, 2.4f), -0.35f, 0.35f) * 0.7f;
        for (int l = 0; l < 4 && !dead; l++) {
            if ((lost >> l & 1) != 0) {
                rollWant += l < 2 ? -0.11f : 0.11f;
                pitchWant += (l & 1) == 0 ? 0.09f : -0.05f;
            }
        }
        drop = dead ? 0 : Mth.clamp(drop, 0, SINK + 1.2f);
        float ks = 1 - (float) Math.exp(-dt * 5);
        lift = Mth.lerp(ks, lift, dead ? 0 : avg * 0.8f - Math.min(drop, SINK) - 0.7f * tuck);
        pitch = Mth.lerp(ks, pitch, pitchWant);
        roll = Mth.lerp(ks, roll, rollWant);
        float fold = Mth.clamp((drop - SINK) / 0.8f, 0, 1) * FOLD + 0.9f * tuck;

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
        // its torso leads: rearing to a face ahead, and on into whatever turn its body is making
        if (!dead && model.spine >= 0) {
            Quaternionf lead = new Quaternionf().rotateX(-0.6f * ahead).premul(spun(spin, 0.2f, 0.5f));
            Vector3f at = new Vector3f(p[model.spine]);
            turnAbout(q, p, model.spine, at, lead);
            for (int i : model.below[model.spine]) {
                turnAbout(q, p, i, at, lead);
            }
        }
        // and its tail trails the turn (and tucks under it, drawn in)
        if (!dead && model.tail >= 0) {
            Quaternionf trail = new Quaternionf().rotateX(-0.8f * tuck).premul(spun(spin, -0.25f, 0.5f));
            Vector3f at = new Vector3f(p[model.tail]);
            turnAbout(q, p, model.tail, at, trail);
            for (int i : model.below[model.tail]) {
                turnAbout(q, p, i, at, trail);
            }
        }

        // each leg bent to put its point on its surface, lifting off it as the clip does
        if (!dead) {
            for (int l = 0; l < 4; l++) {
                if ((lost >> l & 1) != 0) {
                    continue;
                }
                float s = spread[l] * (1 - 0.5f * tuck);
                Vector3f target = new Vector3f(centre.x + (clip[l].x - centre.x) * s, clip[l].y + ground[l], centre.z + (clip[l].z - centre.z) * s);
                if (hook[l] > 0.01f) {
                    target.lerp(hookAt[l], hook[l]);
                }
                // its front feet up on the face ahead, higher the closer it's come
                if (ahead > 0.01f && (l & 1) == 0) {
                    Vector3f up = new Vector3f(target.x * 0.85f, 0.35f + 1.1f * ahead, aheadZ - 0.06f);
                    target.lerp(up, Math.min(1, ahead * 1.4f));
                }
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

    /**
     * Where a foot with nothing under it can hook on round the edge it's past: felt for back toward its body, just
     * below the surface it holds, on a face looking out the way the foot is; model space, or null.
     */
    private static Vector3f hookFace(HarvestmanEntity e, Level level, Matrix4f place, Matrix4f toModel, Vec3 origin, Vector3f foot, Vector3f centre) {
        Vector3f out = new Vector3f(foot.x - centre.x, 0, foot.z - centre.z);
        if (out.lengthSquared() < 0.04f) {
            return null;
        }
        Vector3f from = place.transformPosition(new Vector3f(foot.x, -0.35f, foot.z));
        Vector3f to = place.transformPosition(new Vector3f(centre.x + (foot.x - centre.x) * 0.2f, -0.35f, centre.z + (foot.z - centre.z) * 0.2f));
        Vec3 a = origin.add(from.x, from.y, from.z);
        BlockPos at = BlockPos.containing(a);
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
            return null;
        }
        BlockHitResult r = level.clip(new ClipContext(a, origin.add(to.x, to.y, to.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        if (r.getType() == HitResult.Type.MISS) {
            return null;
        }
        Vector3f n = toModel.transformDirection(new Vector3f(r.getDirection().getStepX(), r.getDirection().getStepY(), r.getDirection().getStepZ()));
        if (n.dot(out.normalize()) < 0.5f) {
            return null;
        }
        Vec3 h = r.getLocation().subtract(origin);
        return toModel.transformPosition(new Vector3f((float) h.x, (float) h.y, (float) h.z)).add(n.mul(0.04f)).add(0, -0.25f, 0);
    }

    /** A turn of so many seconds of a spin (radians a second about its axis), no more than an angle. */
    private static Quaternionf spun(Vector3f spin, float seconds, float most) {
        float angle = spin.length() * seconds;
        if (Math.abs(angle) < 1e-4f) {
            return new Quaternionf();
        }
        angle = Math.signum(angle) * Math.min(Math.abs(angle), most);
        return new Quaternionf().fromAxisAngleRad(new Vector3f(spin).normalize(), angle);
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
