package com.zomboidcraft.creature.spider;

import com.zomboidcraft.creature.HarvestmanBody;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where a shot strikes the Harvestman, from its body as posed this moment (SpiderPose): a tube round every bone, fitted
 * to the mesh it carries (SpiderModel.capsules). Its skull is its eyes (a shot there throws it back), the glowing sac
 * under its tail its core, the rest of its body plate; a leg, an arm or a scythe claw takes the shot itself, and enough
 * of them takes it off. Limbs already gone aren't there to hit.
 */
public final class SpiderHits {
    /** A shot from eye along dir (unit), within range, against the body as posed at origin; null if it misses. */
    public static HarvestmanBody.Strike strike(SpiderPose pose, Vec3 origin, Vec3 eye, Vec3 dir, double range) {
        SpiderModel m = pose.model;
        Matrix4f[] skin = pose.animator.skin;
        HarvestmanBody.Strike best = null;
        double bestRank = Double.POSITIVE_INFINITY;
        Vector3f a = new Vector3f(), b = new Vector3f();
        for (SpiderModel.Capsule c : m.capsules) {
            if (pose.boneGone(c.bone())) {
                continue;
            }
            Matrix4f to = new Matrix4f(pose.place).mul(skin[c.bone()]);
            to.transformPosition(c.a(), a);
            to.transformPosition(c.b(), b);
            Vec3 wa = origin.add(a.x, a.y, a.z), wb = origin.add(b.x, b.y, b.z);
            double t = HarvestmanBody.capsule(eye, dir, wa, wb, c.radius());
            if (t > range) {
                continue;
            }
            // its eyes and core are set in its plates: a shot that reaches them about as soon goes in there
            double rank = c.zone() == SpiderModel.EYES || c.zone() == SpiderModel.CORE ? t - 0.08 : t;
            if (rank < bestRank) {
                bestRank = rank;
                int zone = switch (c.zone()) {
                    case SpiderModel.EYES -> HarvestmanBody.EYES_ZONE;
                    case SpiderModel.CORE -> HarvestmanBody.CORE;
                    case SpiderModel.LEG -> HarvestmanBody.LEG;
                    case SpiderModel.LIMB -> HarvestmanBody.LIMB;
                    default -> HarvestmanBody.HULL;
                };
                int limb = c.zone() == SpiderModel.LIMB ? c.limb() - 4 : c.limb();
                best = new HarvestmanBody.Strike(t, zone, limb, eye.add(dir.scale(t)));
            }
        }
        return best;
    }

    private SpiderHits() {
    }
}
