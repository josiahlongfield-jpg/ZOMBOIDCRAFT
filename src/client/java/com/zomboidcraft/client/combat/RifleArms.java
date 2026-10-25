package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The player's own arms on a held gun in first person, cut from their own player model so they wear their own
 * skin and sleeves, wide or slim. Each forearm runs from where its hand holds back toward the elbow, long enough
 * to leave the screen, with the palm turned the way that hand grips.
 */
final class RifleArms {
    /** The hand-camera's half field of view (the game draws hands at 70 degrees whatever the FOV setting). */
    private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35));
    /** The arm's length in model pixels from its grip point to the shoulder end, and how far the fist goes past it. */
    private static final float REACH = 10.5f, FIST = 1.5f, HAND_END = 10f;

    static void draw(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, AvatarRenderState state,
                     RifleView.Hand right, RifleView.Hand left, float width) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof AvatarRenderer<?> avatar) || state.skin == null) {
            return;
        }
        PlayerModel model = avatar.getModel();
        Identifier skin = state.skin.body().texturePath();
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        Matrix4f frame = new Matrix4f(pose.last().pose());
        arm(pose, out, light, skin, model.rightArm, model.rightSleeve, state.showRightSleeve, slim ? -0.5f : -1f,
                right, width, frame, aspect);
        if (left != null) {
            arm(pose, out, light, skin, model.leftArm, model.leftSleeve, state.showLeftSleeve, slim ? 0.5f : 1f,
                    left, width, frame, aspect);
        }
    }

    private static void arm(PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, ModelPart arm,
                            ModelPart sleeve, boolean sleeveShown, float centreX, RifleView.Hand hand, float width,
                            Matrix4f frame, float aspect) {
        Vector3f at = new Vector3f(hand.at());
        Vector3f back = new Vector3f(hand.elbow()).normalize();
        float length = onScreen(frame, at, back, aspect);
        if (length <= 0) {
            return;     // the hand is behind the eye (the red dot and holo sit that close)
        }
        // the arm's own axes: y runs from the shoulder to the hand, x toward the palm side
        Vector3f y = new Vector3f(back).negate();
        Vector3f x = new Vector3f(hand.palm());
        x.sub(new Vector3f(y).mul(x.dot(y)));
        if (x.lengthSquared() < 1e-4f) {
            x.set(1, 0, 0).sub(new Vector3f(y).mul(y.x));
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        float stretch = Mth.clamp(length * 16f / REACH, width, width * 2.6f);

        arm.resetPose();
        arm.x = arm.y = arm.z = 0;
        arm.xRot = arm.yRot = arm.zRot = 0;
        arm.visible = true;
        sleeve.visible = sleeveShown;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.rotate(new Quaternionf().setFromNormalized(new Matrix3f(x, y, z)));
        pose.scale(width, stretch, width);
        // the arm is stretched to reach off screen, but the fist past the grip point keeps its own size
        pose.translate(-centreX / 16f, -(HAND_END - FIST * width / stretch) / 16f, 0);
        out.submitModelPart(arm, pose, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }

    /**
     * How far back from the hand the forearm must reach to leave the screen (blocks), plus a little; 0 if the hand
     * itself is behind the eye. Walks back along the arm in the hand camera until it is off the edge or behind.
     */
    private static float onScreen(Matrix4f frame, Vector3f at, Vector3f back, float aspect) {
        Vector3f c = frame.transformPosition(new Vector3f(at));
        if (c.z > -0.06f) {
            return 0;
        }
        float length = 0;
        while (length < 1.6f) {
            length += 0.03f;
            c = frame.transformPosition(new Vector3f(back).mul(length).add(at));
            if (c.z > -0.05f || Math.abs(c.y / (-c.z * TAN_HALF_FOV)) > 1.04f
                    || Math.abs(c.x / (-c.z * TAN_HALF_FOV * aspect)) > 1.04f) {
                break;
            }
        }
        return length + 0.12f;
    }

    private RifleArms() {
    }
}
