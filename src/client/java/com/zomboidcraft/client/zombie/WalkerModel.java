package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A human body that moves like one of Zomboid's dead: head lolling to one side, short
 * dragging steps, arms hanging and swaying until it has a target, then reaching for it.
 *
 * Unlike the vanilla zombie, the left arm and leg have their own texture (player layout), and
 * the model carries a cap visor, a hat brim and a backpack that each outfit turns on or off.
 * The extra parts are painted in the texture's overlay rows (see tools/gen_zombie_skins.py).
 */
public class WalkerModel extends HumanoidModel<WalkerRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ZomboidCraft.id("walker"), "main");

    private final ModelPart visor;
    private final ModelPart brim;
    private final ModelPart pack;
    private final ModelPart rightStub;
    private final ModelPart rightBone;
    private final ModelPart leftStub;
    private final ModelPart leftBone;

    public WalkerModel(ModelPart root) {
        super(root);
        visor = head.getChild("visor");
        brim = head.getChild("brim");
        pack = body.getChild("pack");
        rightStub = rightArm.getChild("stub");
        rightBone = rightArm.getChild("bone");
        leftStub = leftArm.getChild("stub");
        leftBone = leftArm.getChild("bone");
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(createMesh(), 64, 64);
    }

    /** The body with its gear parts; {@link WalkerAddonModel} builds its pieces onto the same bones. */
    public static MeshDefinition createMesh() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition root = mesh.getRoot();
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48)
                .addBox(-1f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(5f, 2f, 0f));
        // A torn-off forearm: the upper half of the arm (same texture rows) with a bone sticking out.
        PartDefinition rightArm = root.getChild("right_arm");
        rightArm.addOrReplaceChild("stub", CubeListBuilder.create().texOffs(40, 16)
                .addBox(-3f, -2f, -2f, 4f, 6f, 4f), PartPose.ZERO);
        rightArm.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(56, 16)
                .addBox(-2f, 4f, -1f, 2f, 6f, 2f), PartPose.ZERO);
        leftArm.addOrReplaceChild("stub", CubeListBuilder.create().texOffs(32, 48)
                .addBox(-1f, -2f, -2f, 4f, 6f, 4f), PartPose.ZERO);
        leftArm.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(56, 24)
                .addBox(0f, 4f, -1f, 2f, 6f, 2f), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48)
                .addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(1.9f, 12f, 0f));
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4f, -6f, -8f, 8f, 0.5f, 4f), PartPose.ZERO);
        head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-5f, -5.5f, -5f, 10f, 0.5f, 10f), PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("pack", CubeListBuilder.create().texOffs(42, 37)
                .addBox(-4f, 1f, 2f, 8f, 8f, 3f), PartPose.ZERO);
        return mesh;
    }

    /** Shows an arm whole, as a stump with a bone (forearm gone), or not at all. */
    private static void limb(boolean forearmGone, boolean armGone, ModelPart arm, ModelPart stub, ModelPart bone) {
        arm.visible = !armGone;
        arm.skipDraw = forearmGone;
        stub.visible = forearmGone;
        bone.visible = forearmGone;
    }

    @Override
    public void setupAnim(WalkerRenderState state) {
        super.setupAnim(state);
        WalkerGear gear = WalkerGear.of(state.outfit);
        visor.visible = gear.visor();
        brim.visible = gear.brim() > 0;
        brim.xScale = brim.zScale = Math.max(gear.brim(), 0.01f);
        pack.visible = gear.pack();
        limb(state.missingLimb == 1, state.missingLimb == 3, rightArm, rightStub, rightBone);
        limb(state.missingLimb == 2, state.missingLimb == 4, leftArm, leftStub, leftBone);

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
