package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The local driver's own arms on the steering wheel in first person, drawn with the car in its own space
 * so the hands sit on the rim whatever the field of view: each arm cut from their player model (their
 * skin and sleeves, wide or slim), running low from the shoulder to a hand gripping the side of the rim at
 * a little above quarter to three, so they stay down by the edges of the view rather than across the windscreen; the hands
 * go round with the wheel as it turns (up to a point, then the wheel slides through them). The arms are
 * drawn at the rider's size (shrunk in a car) and slimmer still, as they're right under the camera.
 */
final class CarArms {
    /** The arm's length in model pixels from the grip to the shoulder end, and how far the fist goes past the grip. */
    private static final float REACH = 10.5f, FIST = 1.5f, HAND_END = 10f;
    /** Where the hands grip: this far round from the top of the wheel, each way, and how far they follow its turn. */
    private static final float GRIP = (float) Math.toRadians(75), FOLLOW = (float) Math.toRadians(80);
    /** How thick the driver's arms are drawn against the rider's own. */
    private static final float SLIM = 0.8f;

    /**
     * Draws the arms. pose is the car's frame (model space); eye is the driver's seat (eyes) in it; wheel is the
     * rim (centre, normal toward the driver, radius); turn is the wheel's turn; hands is {@link CarHands#BOTH} or LEFT.
     */
    static void draw(PoseStack pose, SubmitNodeCollector out, int light, float[] eye, float[] wheel, float turn, int hands) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof AvatarRenderer<?> avatar)) {
            return;
        }
        PlayerSkin skin = player.getSkin();
        PlayerModel model = avatar.getModel();
        Identifier texture = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        Vector3f c = new Vector3f(wheel[0], wheel[1], wheel[2]), n = new Vector3f(wheel[3], wheel[4], wheel[5]).normalize();
        // the wheel's twelve o'clock, and its left (the driver's left: model space +x)
        Vector3f up = new Vector3f(0, 1, 0).sub(new Vector3f(n).mul(n.y)).normalize();
        Vector3f left = new Vector3f(n).cross(up);
        float follow = Math.max(-FOLLOW, Math.min(FOLLOW, turn));
        Vector3f leftHand = rim(c, up, left, wheel[6], GRIP + follow);
        Vector3f rightHand = rim(c, up, left, wheel[6], -GRIP + follow);
        float s = player.getScale();
        Vector3f leftShoulder = new Vector3f(eye[0] + 0.21f * s, eye[1] - 0.36f * s, eye[2] - 0.08f * s);
        Vector3f rightShoulder = new Vector3f(eye[0] - 0.21f * s, eye[1] - 0.36f * s, eye[2] - 0.08f * s);
        arm(pose, out, light, texture, model.leftArm, model.leftSleeve, slim ? 0.5f : 1f, leftHand, leftShoulder, c, SLIM * s);
        if (hands == CarHands.BOTH) {
            arm(pose, out, light, texture, model.rightArm, model.rightSleeve, slim ? -0.5f : -1f, rightHand, rightShoulder, c, SLIM * s);
        }
    }

    /**
     * The pilot's arms on an aircraft's controls: each hand at its grip, the palm toward a point (the grip's
     * far side), shoulders from the seat's eye as for a driver. Either hand may be null.
     */
    static void grips(PoseStack pose, SubmitNodeCollector out, int light, float[] eye, Vector3f leftHand, Vector3f leftPalm,
                      Vector3f rightHand, Vector3f rightPalm) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof AvatarRenderer<?> avatar)) {
            return;
        }
        PlayerSkin skin = player.getSkin();
        PlayerModel model = avatar.getModel();
        Identifier texture = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        if (leftHand != null) {
            arm(pose, out, light, texture, model.leftArm, model.leftSleeve, slim ? 0.5f : 1f, leftHand,
                    new Vector3f(eye[0] + 0.2f, eye[1] - 0.32f, eye[2] - 0.08f), leftPalm, 1f);
        }
        if (rightHand != null) {
            arm(pose, out, light, texture, model.rightArm, model.rightSleeve, slim ? -0.5f : -1f, rightHand,
                    new Vector3f(eye[0] - 0.2f, eye[1] - 0.32f, eye[2] - 0.08f), rightPalm, 1f);
        }
    }

    /** Both arms from given shoulders to given grips (the gunner at a mounted gun, whose shoulders turn with it). */
    static void grips(PoseStack pose, SubmitNodeCollector out, int light, Vector3f leftShoulder, Vector3f leftHand, Vector3f leftPalm,
                      Vector3f rightShoulder, Vector3f rightHand, Vector3f rightPalm) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(mc.getEntityRenderDispatcher().getRenderer(player) instanceof AvatarRenderer<?> avatar)) {
            return;
        }
        PlayerSkin skin = player.getSkin();
        PlayerModel model = avatar.getModel();
        Identifier texture = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        arm(pose, out, light, texture, model.leftArm, model.leftSleeve, slim ? 0.5f : 1f, leftHand, leftShoulder, leftPalm, SLIM);
        arm(pose, out, light, texture, model.rightArm, model.rightSleeve, slim ? -0.5f : -1f, rightHand, rightShoulder, rightPalm, SLIM);
    }

    /** A point on the rim, angle round from the top toward the driver's left. */
    private static Vector3f rim(Vector3f c, Vector3f up, Vector3f left, float r, float angle) {
        return new Vector3f(c).add(new Vector3f(up).mul((float) Math.cos(angle) * r)).add(new Vector3f(left).mul((float) Math.sin(angle) * r));
    }

    private static void arm(PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, ModelPart arm, ModelPart sleeve,
                            float centreX, Vector3f hand, Vector3f shoulder, Vector3f wheel, float thick) {
        Vector3f back = new Vector3f(shoulder).sub(hand);
        float length = back.length();
        back.normalize();
        // the arm's own axes: y runs from the shoulder to the hand, x toward the palm (round the rim, toward the hub)
        Vector3f y = new Vector3f(back).negate();
        Vector3f x = new Vector3f(wheel).sub(hand);
        x.sub(new Vector3f(y).mul(x.dot(y)));
        if (x.lengthSquared() < 1e-6f) {
            x.set(1, 0, 0);
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        float stretch = Math.max(0.6f, (length + 0.1f * thick) * 16f / REACH);
        arm.resetPose();
        arm.x = arm.y = arm.z = 0;
        arm.xRot = arm.yRot = arm.zRot = 0;
        arm.visible = true;
        sleeve.visible = true;
        pose.pushPose();
        pose.translate(hand.x, hand.y, hand.z);
        pose.rotate(new Quaternionf().setFromNormalized(new Matrix3f(x, y, z)));
        pose.scale(thick, stretch, thick);
        pose.translate(-centreX / 16f, -(HAND_END - FIST * thick / stretch) / 16f, 0);
        out.submitModelPart(arm, pose, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }

    private CarArms() {
    }
}
