package com.zomboidcraft.creature.spider;

import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One Harvestman's body as it is this moment: its clips played (SpiderAnimator), fitted to the world (SpiderIk), stood
 * on the surface under it and turned the way it lies, the limbs it's lost folded away. The client keeps one per
 * Harvestman to draw it; the server keeps one too, posed every tick, so a shot strikes it where it's drawn (SpiderHits).
 */
public final class SpiderPose {
    /** Its body's height off the surface it stands on (HarvestmanEntity.STAND), and under which it's crouching. */
    private static final float STANDS = 2.05f;

    public final SpiderModel model;
    public final SpiderAnimator animator;
    private final SpiderIk ik;
    /** Model space to the entity's position: on the surface under its body, turned the way it lies. */
    public final Matrix4f place = new Matrix4f();
    /** Limbs gone (by SpiderModel's numbering) as last posed, and those just gone with how they were posed then. */
    public int gone = -1;
    public int justGone;
    public final Matrix4f[][] lastSeen = new Matrix4f[SpiderModel.LIMBS][];
    private float height = -1;

    public SpiderPose(SpiderModel model) {
        this.model = model;
        animator = new SpiderAnimator(model);
        ik = new SpiderIk(model);
    }

    /** The limbs it's lost: its legs (lost(), bits 0-3) and the smaller ones (cut(), bits 0-5 as limbs 4-9). */
    public static int gone(HarvestmanEntity e) {
        return e.lost() & 15 | e.cut() << 4;
    }

    /**
     * Brings it up to now; false if it was already posed this frame.
     *
     * @param at where the entity is (drawn, on the client)
     * @param turn how its body lies (model to world)
     */
    public boolean update(HarvestmanEntity e, Vec3 at, Quaternionf turn, float frameKey, float dt) {
        if (!animator.update(e, at, turn, frameKey, dt)) {
            return false;
        }
        dt = animator.dt;
        boolean dead = e.pose() == HarvestmanEntity.DEAD;
        // its feet on the surface under its body: found by feeling straight down from its centre, eased, so the body
        // rides smoothly and doesn't jump as the height it wants changes
        Vector3f up = turn.transform(new Vector3f(0, 1, 0));
        Vec3 centre = at.add(0, HarvestmanEntity.MID, 0);
        float lift = dead ? (float) HarvestmanEntity.MID : e.lift();
        float found = lift;
        if (!dead) {
            Vec3 down = centre.subtract(up.x * (lift + 1.5), up.y * (lift + 1.5), up.z * (lift + 1.5));
            BlockHitResult r = e.level().clip(new ClipContext(centre, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            if (r.getType() != HitResult.Type.MISS) {
                found = (float) r.getLocation().distanceTo(centre);
            }
        }
        height = height < 0 || dead ? found : Mth.lerp(1 - (float) Math.exp(-dt * 6), height, found);
        Vector3f base = new Vector3f(0, (float) HarvestmanEntity.MID, 0).sub(up.mul(height, new Vector3f()));
        place.translation(base).rotate(turn);
        ik.apply(e, animator, e.level(), place, at, STANDS - Math.min(height, lift + 0.2f), dt);
        animator.bake();
        fold(gone(e));
        return true;
    }

    /** Limbs gone: everything past the cut folded into it (the stump's end); just gone, how they were first kept. */
    private void fold(int now) {
        Matrix4f[] bones = animator.skin;
        justGone = 0;
        for (int c = 0; c < SpiderModel.LIMBS; c++) {
            if ((now >> c & 1) == 0) {
                continue;
            }
            int[] cut = model.cutBones[c];
            if (gone >= 0 && (gone >> c & 1) == 0) {
                justGone |= 1 << c;
                Matrix4f[] kept = new Matrix4f[cut.length];
                for (int k = 0; k < cut.length; k++) {
                    kept[k] = new Matrix4f(place).mul(bones[cut[k]]);
                }
                lastSeen[c] = kept;
            }
            Vector3f at = new Vector3f(animator.p[model.cutAt[c]]);
            for (int b : cut) {
                bones[b].zero().m30(at.x).m31(at.y).m32(at.z).m33(1);
            }
        }
        gone = now;
    }

    /** Whether a bone's been shot away. */
    public boolean boneGone(int bone) {
        int c = model.limbOf[bone];
        if (c < 0 || (gone >> c & 1) == 0) {
            return false;
        }
        for (int b : model.cutBones[c]) {
            if (b == bone) {
                return true;
            }
        }
        return false;
    }
}
