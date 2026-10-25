package com.zomboidcraft.client.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.Weapons;
import com.zomboidcraft.combat.WeaponSpecs;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How survivors carry and aim their weapons as other players see them: a rifle at the low ready or
 * shouldered, the big melee weapons in both hands. The gun arm takes a fixed angle (the weapon's item
 * model is fitted to it, see tools/weapons/poses.py) turned by what the player is doing: looking about
 * while aiming, walking, swinging. The off arm then reaches for the weapon's support line (magazine well
 * and handguard, the end of a handle) wherever that left it, so both hands stay on the weapon.
 *
 * All the working is done for a right-handed player and mirrored for left-handed ones.
 */
public final class HoldPoses {
    /** Bags carried in the hand (the inventory screen's duffels) leave one hand free at most. */
    private static final TagKey<Item> HAND_CARRIED = TagKey.create(Registries.ITEM, ZomboidCraft.id("hand_carried"));
    private static final float HAND_X = 1f;

    private HoldPoses() {
    }

    public static void apply(PlayerModel model, AvatarRenderState state) {
        if (!ZcConfig.get().combat.weaponHolds || state.isVisuallySwimming || state.isFallFlying
                || state.hasPose(Pose.SLEEPING) || state.hasPose(Pose.SPIN_ATTACK)) {
            return;
        }
        boolean left = state.mainArm == HumanoidArm.LEFT;
        ItemStack stack = left ? state.leftHandItemStack : state.rightHandItemStack;
        ItemStack off = left ? state.rightHandItemStack : state.leftHandItemStack;
        if (stack == null || stack.isEmpty() || (off != null && off.is(HAND_CARRIED))) {
            return;
        }

        Matrix3f gun;
        float[] support;
        float mirror = left ? -1f : 1f;
        float headX = model.head.xRot, headY = model.head.yRot * mirror;
        if (stack.getItem() instanceof RifleItem) {
            boolean aiming = state.isUsingItem && state.useItemHand == InteractionHand.MAIN_HAND;
            if (aiming) {
                // the shouldered rifle follows the eyes
                gun = rot(headX, headY, 0).mul(rot(HoldPoseData.RIFLE_AIM));
                support = HoldPoseData.RIFLE_AIM_SUPPORT;
            } else {
                gun = rot(bob(state), 0, 0).mul(rot(HoldPoseData.RIFLE_READY));
                support = HoldPoseData.RIFLE_READY_SUPPORT;
            }
        } else {
            WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
            support = spec == null ? null : HoldPoseData.MELEE_SUPPORT.get(spec.name());
            if (support == null) {
                return;     // one-handed weapons keep vanilla's hold
            }
            float t = state.swingAnimation;
            gun = t > 0 ? swing(t) : rot(bob(state), 0, 0).mul(rot(HoldPoseData.MELEE_READY));
        }

        // vanilla's attack twist turns the torso and carries the shoulders round with it
        float twist = model.body.yRot * mirror;
        if (twist != 0) {
            gun = rot(0, twist, 0).mul(gun);
        }
        ModelPart gunArm = left ? model.leftArm : model.rightArm;
        ModelPart offArm = left ? model.rightArm : model.leftArm;
        Vector3f gunPivot = new Vector3f(gunArm.x * mirror, gunArm.y, gunArm.z);
        Vector3f offPivot = new Vector3f(offArm.x * mirror, offArm.y, offArm.z);

        Vector3f e = gun.getEulerAnglesZYX(new Vector3f());
        gunArm.setRotation(e.x, e.y * mirror, e.z * mirror);

        Vector3f p0 = gun.transform(new Vector3f(support[0], support[1], support[2])).add(gunPivot);
        Vector3f p1 = gun.transform(new Vector3f(support[3], support[4], support[5])).add(gunPivot);
        Vector3f target = supportTarget(offPivot, p0, p1);
        float[] a = aim(offPivot, target, HAND_X);
        offArm.setRotation(a[0], a[1] * mirror, 0);
    }

    /** The swing: wind up over the right shoulder, sweep through to low on the left, come back to the ready. */
    private static Matrix3f swing(float t) {
        Matrix3f ready = rot(HoldPoseData.MELEE_READY);
        Matrix3f windup = rot(HoldPoseData.MELEE_WINDUP);
        Matrix3f strike = rot(HoldPoseData.MELEE_STRIKE);
        if (t < 0.35f) {
            return slerp(ready, windup, ease(t / 0.35f));
        }
        if (t < 0.6f) {
            return slerp(windup, strike, ease((t - 0.35f) / 0.25f));
        }
        return slerp(strike, ready, ease((t - 0.6f) / 0.4f));
    }

    /** A gentle sway as you walk and breathe, the same for both arms so the hands stay together. */
    private static float bob(AvatarRenderState state) {
        return Mth.cos(state.walkAnimationPos * 0.6662f) * 0.06f * Math.min(1f, state.walkAnimationSpeed)
                + Mth.sin(state.ageInTicks * 0.067f) * 0.02f;
    }

    /**
     * Arm angles (x, y; z = 0) that put the hand's centre line, handX off the arm's turning axis, through
     * the target. Same as poses.aim_arm.
     */
    static float[] aim(Vector3f pivot, Vector3f target, float handX) {
        float dx = target.x - pivot.x, dy = target.y - pivot.y, dz = target.z - pivot.z;
        float rxz2 = dx * dx + dz * dz;
        float a = -(float) Math.sqrt(Math.max(rxz2 - handX * handX, 1e-6f));
        return new float[] {(float) Math.atan2(a, dy), (float) (Math.atan2(a, handX) - Math.atan2(dz, dx))};
    }

    /** The point on p0-p1 whose distance from the shoulder is nearest the arm's reach. */
    static Vector3f supportTarget(Vector3f pivot, Vector3f p0, Vector3f p1) {
        Vector3f best = p0;
        float bestErr = Float.MAX_VALUE;
        for (int i = 0; i <= 32; i++) {
            Vector3f p = new Vector3f(p0).lerp(p1, i / 32f);
            float err = Math.abs(p.distance(pivot) - HoldPoseData.REACH);
            if (err < bestErr) {
                bestErr = err;
                best = p;
            }
        }
        return best;
    }

    private static Matrix3f rot(float[] xyz) {
        return rot(xyz[0], xyz[1], xyz[2]);
    }

    /** ModelPart's turn: z, then y, then x (applied to a point x first). */
    private static Matrix3f rot(float x, float y, float z) {
        return new Matrix3f().rotationZYX(z, y, x);
    }

    private static Matrix3f slerp(Matrix3f a, Matrix3f b, float t) {
        Quaternionf qa = new Quaternionf().setFromNormalized(a);
        Quaternionf qb = new Quaternionf().setFromNormalized(b);
        return new Matrix3f().set(qa.slerp(qb, t));
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }
}
