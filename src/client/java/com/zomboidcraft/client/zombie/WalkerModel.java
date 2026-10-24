package com.zomboidcraft.client.zombie;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A human body that moves like one of Zomboid's dead: head lolling to one side, short
 * dragging steps, arms hanging and swaying until it has a target, then reaching for it.
 */
public class WalkerModel extends HumanoidModel<WalkerRenderState> {
    public WalkerModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(WalkerRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        float phase = (state.variant & 0xFF) / 40f;
        float side = (state.variant & 0x100) == 0 ? 1f : -1f;
        float walk = state.walkAnimationPos;
        float speed = Math.min(1f, state.walkAnimationSpeed);

        head.xRot += 0.15f;
        head.zRot += 0.22f * side + Mth.sin(time * 0.05f + phase) * 0.06f;

        // A stiff, dragging gait with a sway in the shoulders.
        rightLeg.xRot *= side > 0 ? 0.55f : 0.8f;
        leftLeg.xRot *= side > 0 ? 0.8f : 0.55f;
        body.zRot = Mth.sin(walk * 0.6662f) * 0.07f * speed;

        if (state.isAggressive) {
            AnimationUtils.animateZombieArms(leftArm, rightArm, true, state);
            rightArm.xRot += Mth.sin(time * 0.13f + phase) * 0.08f;
            leftArm.xRot += Mth.cos(time * 0.11f + phase) * 0.08f;
        } else {
            rightArm.xRot = 0.1f + Mth.cos(walk * 0.6662f + Mth.PI) * 0.3f * speed;
            leftArm.xRot = -0.05f + Mth.cos(walk * 0.6662f) * 0.25f * speed;
            rightArm.yRot = 0f;
            leftArm.yRot = 0f;
            rightArm.zRot = 0.08f + Mth.sin(time * 0.06f + phase) * 0.03f;
            leftArm.zRot = -0.08f - Mth.sin(time * 0.05f + phase) * 0.03f;
        }
    }
}
