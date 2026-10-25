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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * A human body that moves like one of Zomboid's dead: head lolling to one side, short
 * dragging steps on bent knees, arms hanging and swaying until it has a target, then reaching for it.
 *
 * It is built like a Zomboid character rather than a Minecraft one: arms and legs bend at the
 * elbow and knee, the hands are mittens, limbs are slimmer, the torso narrower over longer
 * legs, and a smaller head sits on a neck. Every new block samples
 * the part of the player-layout skin it sits on, so the existing skins, gear and eye masks fit.
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
    protected final ModelPart rightForearm;
    protected final ModelPart leftForearm;
    protected final ModelPart rightShin;
    protected final ModelPart leftShin;
    /** Every box that is part of the body itself (skin-textured), as opposed to gear. */
    protected final List<ModelPart> bodyParts;

    /** Where the joints sit inside the vanilla limb boxes, in model pixels from the limb's top. */
    public static final float ELBOW = 5f, WRIST = 4f, KNEE = 6f;
    /**
     * A Zomboid figure's proportions on Minecraft's boxes: slim arms and legs, a narrower and
     * shorter torso, longer legs, a smaller head on a neck. Height stays 2 blocks.
     */
    private static final float ARM_SLIM = 0.68f, ARM_LONG = 1.1f, LEG_SLIM = 0.85f, TORSO_WIDE = 0.9f,
            TORSO_TALL = 0.92f, HEAD_SIZE = 0.8f, NECK = 1.2f;
    private static final float HIP = 12f * TORSO_TALL, LEG_LONG = (24f - HIP) / 12f, SHOULDER = 4.4f;

    public WalkerModel(ModelPart root) {
        super(root);
        visor = head.getChild("visor");
        brim = head.getChild("brim");
        pack = body.getChild("pack");
        rightStub = rightArm.getChild("stub");
        rightBone = rightArm.getChild("bone");
        leftStub = leftArm.getChild("stub");
        leftBone = leftArm.getChild("bone");
        rightForearm = rightArm.getChild("forearm");
        leftForearm = leftArm.getChild("forearm");
        rightShin = rightLeg.getChild("shin");
        leftShin = leftLeg.getChild("shin");
        List<ModelPart> parts = new ArrayList<>(List.of(head, body, rightArm, leftArm, rightLeg, leftLeg,
                rightForearm, leftForearm, rightForearm.getChild("hand"), leftForearm.getChild("hand"),
                rightShin, leftShin));
        parts.add(body.getChild("neck"));
        bodyParts = List.copyOf(parts);
    }


    public static LayerDefinition createLayer() {
        return LayerDefinition.create(createMesh(), 64, 64);
    }

    /** The body with its gear parts; {@link WalkerAddonModel} builds its pieces onto the same bones. */
    public static MeshDefinition createMesh() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition root = mesh.getRoot();
        // Each limb is three boxes that read the top, middle and bottom rows of the vanilla limb's skin.
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
                .addBox(-3f, -2f, -2f, 4f, ELBOW, 4f), PartPose.offset(-SHOULDER, 2f, 0f));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48)
                .addBox(-1f, -2f, -2f, 4f, ELBOW, 4f), PartPose.offset(SHOULDER, 2f, 0f));
        arm(rightArm, 40, 16, -1f);
        arm(leftArm, 32, 48, 1f);
        // The stump of a torn-off forearm is the upper arm itself, with a bone sticking out.
        rightArm.addOrReplaceChild("stub", CubeListBuilder.create(), PartPose.ZERO);
        rightArm.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(56, 16)
                .addBox(-2f, ELBOW - 2.5f, -1f, 2f, 6f, 2f), PartPose.ZERO);
        leftArm.addOrReplaceChild("stub", CubeListBuilder.create(), PartPose.ZERO);
        leftArm.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(56, 24)
                .addBox(0f, ELBOW - 2.5f, -1f, 2f, 6f, 2f), PartPose.ZERO);
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-2f, 0f, -2f, 4f, KNEE, 4f), PartPose.offset(-1.9f, HIP, 0f));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48)
                .addBox(-2f, 0f, -2f, 4f, KNEE, 4f), PartPose.offset(1.9f, HIP, 0f));
        rightLeg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 16 + (int) KNEE)
                .addBox(-2f, 0f, -2f, 4f, 12f - KNEE, 4f, new CubeDeformation(-0.1f, 0f, -0.1f)), PartPose.offset(0f, KNEE, 0f));
        leftLeg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(16, 48 + (int) KNEE)
                .addBox(-2f, 0f, -2f, 4f, 12f - KNEE, 4f, new CubeDeformation(-0.1f, 0f, -0.1f)), PartPose.offset(0f, KNEE, 0f));
        // a neck, so the smaller head sits up off the shoulders (skin from under the chin)
        root.getChild("body").addOrReplaceChild("neck", CubeListBuilder.create().texOffs(16, 0)
                .addBox(-1.6f, -NECK - 0.3f, -1.5f, 3.2f, NECK + 0.3f, 3f), PartPose.ZERO);
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-4f, -6f, -8f, 8f, 0.5f, 4f), PartPose.ZERO);
        head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-5f, -5.5f, -5f, 10f, 0.5f, 10f), PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("pack", CubeListBuilder.create().texOffs(42, 37)
                .addBox(-4f, 1f, 2f, 8f, 8f, 3f), PartPose.ZERO);
        return mesh;
    }

    /**
     * Forearm and mitten hand below the upper arm. {@code cx} is the limb's centre line. Texture rows: upper arm 0-5,
     * forearm 5-9, hand 9-12 of the vanilla arm, so a painted sleeve, cuff and hand land where they did.
     */
    private static void arm(PartDefinition arm, int u, int v, float cx) {
        int fv = v + (int) ELBOW;  // the front face starts one depth (4) below v
        PartDefinition fore = arm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(u, fv)
                .addBox(-2f, 0f, -2f, 4f, WRIST, 4f, new CubeDeformation(-0.2f, 0f, -0.2f)),
                PartPose.offset(cx, ELBOW - 2f, 0f));
        float handLen = 12f - ELBOW - WRIST;
        fore.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(u, fv + (int) WRIST)
                .addBox(-2f, 0f, -2f, 4f, handLen, 4f, new CubeDeformation(0.3f, 0.1f, -0.55f)),
                PartPose.offset(0f, WRIST, 0f));
    }

    /** Shows an arm whole, as a stump with a bone (forearm gone), or not at all. */
    private static void limb(boolean forearmGone, boolean armGone, ModelPart arm, ModelPart fore, ModelPart stub, ModelPart bone) {
        arm.visible = !armGone;
        fore.visible = !forearmGone;
        stub.visible = false;
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
        limb(state.missingLimb == 1, state.missingLimb == 3, rightArm, rightForearm, rightStub, rightBone);
        limb(state.missingLimb == 2, state.missingLimb == 4, leftArm, leftForearm, leftStub, leftBone);
        for (ModelPart arm : new ModelPart[] {rightArm, leftArm}) {
            arm.xScale = arm.zScale = ARM_SLIM;
            arm.yScale = ARM_LONG;
        }
        for (ModelPart leg : new ModelPart[] {rightLeg, leftLeg}) {
            leg.xScale = leg.zScale = LEG_SLIM;
            leg.yScale = LEG_LONG;
        }
        body.xScale = TORSO_WIDE;
        body.yScale = TORSO_TALL;
        head.xScale = head.yScale = head.zScale = HEAD_SIZE;
        hat.xScale = hat.yScale = hat.zScale = HEAD_SIZE;

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
        // Knees and elbows: always a little bent, the dragging leg barely lifting its foot.
        float step = walk * 0.6662f;
        float lead = side > 0 ? 0.75f : 0.4f, drag = side > 0 ? 0.4f : 0.75f;
        float stride = Math.min(1f, speed * 2.5f);
        rightShin.xRot = 0.15f + Math.max(0f, -Mth.cos(step)) * (lead + 0.2f) * stride;
        leftShin.xRot = 0.15f + Math.max(0f, Mth.cos(step)) * (drag + 0.2f) * stride;
        float grab = Mth.sin(state.swingAnimation * Mth.PI);  // hands close mid-swing
        if (state.isAggressive) {
            rightForearm.xRot = -0.25f - 0.5f * (1f - grab) + Mth.sin(time * 0.17f + phase) * 0.12f;
            leftForearm.xRot = -0.35f - 0.5f * (1f - grab) + Mth.cos(time * 0.15f + phase) * 0.12f;
            rightArm.xRot -= grab * 0.35f;
            leftArm.xRot -= grab * 0.35f;
        } else {
            rightForearm.xRot = -0.3f - Math.max(0f, Mth.cos(step)) * 0.35f * stride;
            leftForearm.xRot = -0.4f - Math.max(0f, -Mth.cos(step)) * 0.35f * stride;
        }
        // the whole body dips as each knee takes the weight, and the head rides up on its neck
        float dip = Math.abs(Mth.sin(step)) * 0.45f * stride;
        for (ModelPart part : new ModelPart[] {body, rightArm, leftArm, rightLeg, leftLeg}) {
            part.y += dip;
        }
        head.y += dip - NECK;
        hat.y = head.y;
        if (state.swimAmount > 0f) {
            lying(state, state.swimAmount, time + phase * 40f, walk, speed);
        }
    }

    /** On the ground. The renderer tips the body; this poses the limbs, blended in by {@code lie}. */
    private void lying(WalkerRenderState state, float lie, float time, float walk, float speed) {
        if (state.crawler) {
            // dragging itself along on its arms, head raised, legs trailing
            float reach = Mth.sin(walk * 0.6662f) * 0.55f * Math.max(speed, 0.15f);
            rightArm.xRot = Mth.lerp(lie, rightArm.xRot, -2.7f + reach);
            leftArm.xRot = Mth.lerp(lie, leftArm.xRot, -2.7f - reach);
            rightArm.zRot = Mth.lerp(lie, rightArm.zRot, 0.25f);
            leftArm.zRot = Mth.lerp(lie, leftArm.zRot, -0.25f);
            head.xRot = Mth.lerp(lie, head.xRot, -1.05f);
            rightLeg.xRot = Mth.lerp(lie, rightLeg.xRot, 0.08f + Mth.sin(time * 0.05f) * 0.05f);
            leftLeg.xRot = Mth.lerp(lie, leftLeg.xRot, -0.04f);
        } else {
            // flat on its back, clawing at the air
            rightArm.xRot = Mth.lerp(lie, rightArm.xRot, -1.35f + Mth.sin(time * 0.17f) * 0.3f);
            leftArm.xRot = Mth.lerp(lie, leftArm.xRot, -1.2f + Mth.cos(time * 0.15f) * 0.3f);
            rightArm.zRot = Mth.lerp(lie, rightArm.zRot, 0.15f);
            leftArm.zRot = Mth.lerp(lie, leftArm.zRot, -0.15f);
            head.xRot = Mth.lerp(lie, head.xRot, -0.25f);
            rightLeg.xRot = Mth.lerp(lie, rightLeg.xRot, -0.3f + Mth.sin(time * 0.11f) * 0.15f);
            leftLeg.xRot = Mth.lerp(lie, leftLeg.xRot, 0.05f);
        }
        if (state.crawler) {
            // one arm pulls while the other reaches ahead
            float pull = (Mth.sin(walk * 0.6662f) + 1f) * 0.5f;
            rightForearm.xRot = Mth.lerp(lie, rightForearm.xRot, -0.9f * (1f - pull));
            leftForearm.xRot = Mth.lerp(lie, leftForearm.xRot, -0.9f * pull);
            rightShin.xRot = Mth.lerp(lie, rightShin.xRot, 0.25f + Mth.sin(time * 0.05f) * 0.08f);
            leftShin.xRot = Mth.lerp(lie, leftShin.xRot, 0.1f);
        } else {
            rightForearm.xRot = Mth.lerp(lie, rightForearm.xRot, -0.6f + Mth.sin(time * 0.21f) * 0.4f);
            leftForearm.xRot = Mth.lerp(lie, leftForearm.xRot, -0.5f + Mth.cos(time * 0.19f) * 0.4f);
            rightShin.xRot = Mth.lerp(lie, rightShin.xRot, 0.5f + Mth.sin(time * 0.11f) * 0.2f);
            leftShin.xRot = Mth.lerp(lie, leftShin.xRot, 0.2f);
        }
        rightArm.yRot = Mth.lerp(lie, rightArm.yRot, 0f);
        leftArm.yRot = Mth.lerp(lie, leftArm.yRot, 0f);
        body.zRot = Mth.lerp(lie, body.zRot, 0f);
    }
}
