package com.zomboidcraft.client.operator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.combat.RailLights;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a player as the Tarkov operator ({@link Operator}) where the hidden vanilla model stands: body, head, helmet and
 * rig, posed by Tarkov's own clips, and the kitted M4A1 on his rifle bone while he holds it. Turned out of vanilla's
 * model space (its flip, its 0.9375 scale, its origin up at the hips) into his own metres, feet on the ground, as the
 * survivor layer does.
 */
public final class OperatorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public OperatorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new OperatorLayer(avatar));
            }
        });
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(state.id) instanceof Player player) || !Operator.active(player)) return;
        NpcSkin skin = Operator.skin();
        Matrix4f[] bones = Operator.pose(skin, state, player);
        if (bones == null) return;
        boolean rifle = Operator.holdsRifle(player.getMainHandItem());
        if (rifle) rail(skin, bones, player.getMainHandItem(), state);
        pose.pushPose();
        pose.translate(0, 1.501f, 0);
        pose.scale(1 / 0.9375f, 1 / 0.9375f, 1 / 0.9375f);
        pose.scale(-1, -1, 1);
        if (state.deathTime > 0) {
            float turn = Math.min(1, (float) Math.sqrt((state.deathTime - 1) / 20f * 1.6f)) * 90f;
            pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(-turn)));
        }
        pose.mulPose(new Matrix4f().rotationY((float) Math.PI));
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        NpcSkinDraw.submitGroups(skin.groups, bones, 0, g -> !g.piece.equals("gun") || rifle,
                g -> skin.texture(g.texture, Operator.MODEL), Operator.buffers(state, skin), light, overlay, pose, collector);
        pose.popPose();
    }

    /**
     * Where his rifle's laser and light lenses are, for RailLights to shine them from (in the player model's pixels:
     * vanilla's model space, y down, facing -z, the way it reads the posed arms' rifles).
     */
    private static void rail(NpcSkin skin, Matrix4f[] bones, ItemStack gun, AvatarRenderState state) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        int weapon = skin.bone("weapon");
        if (spec == null || spec.rail == null || weapon < 0 || !(Attachments.lightOn(gun) || Attachments.laserOn(gun))) return;
        Vector3f laser = lens(bones[weapon], spec.rail.laser), light = lens(bones[weapon], spec.rail.light);
        if (laser == null) laser = light;
        if (light == null) light = laser;
        if (laser != null) RailLights.seen(state, laser, light);
    }

    private static Vector3f lens(Matrix4f weapon, LocalGuns.Device d) {
        if (d == null || d.third == null) return null;
        // his metres (feet on the ground, facing +z) into vanilla's model pixels, the way submit draws him
        Vector3f m = weapon.transformPosition(new Vector3f(d.third[0], d.third[1], d.third[2]));
        return new Vector3f(m.x / 0.9375f, 1.501f - m.y / 0.9375f, -m.z / 0.9375f).mul(16);
    }
}
