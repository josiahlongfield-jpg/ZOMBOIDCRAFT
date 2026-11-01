package com.zomboidcraft.client.survivor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;

/**
 * Draws a player as their Zomboid survivor (see {@link Survivors}) where Minecraft's hidden model stands: the body in
 * its composed skin, the hair and beard showing now, tinted, and Zomboid's clothes for what they wear
 * ({@link SurvivorClothes}). Turned back out of the vanilla model's space (its
 * flip, its 0.9375 scale and its origin up at the hips) into the survivor's own metres, feet on the ground. Vanilla's
 * tip-over on death is taken back off: Zomboid's own fall plays instead.
 */
public final class SurvivorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public SurvivorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new SurvivorLayer(avatar));
                com.zomboidcraft.client.combat.ArmModels.bake(context);
            }
        });
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Survivors.Drawn d = Survivors.take(state);
        if (d == null || d.skin == null || state.isInvisible) return;
        pose.pushPose();
        pose.translate(0, 1.501f, 0);
        pose.scale(1 / 0.9375f, 1 / 0.9375f, 1 / 0.9375f);
        pose.scale(-1, -1, 1);
        if (d.deathTurn != 0) pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(-d.deathTurn)));
        pose.mulPose(new Matrix4f().rotationY((float) Math.PI));
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        NpcSkinDraw.submitWith(d.pack.skin, d.skin,
                g -> g.piece.equals("body") || g.piece.equals(d.hairPiece) || g.piece.equals(d.beardPiece) || d.clothes.containsKey(g.piece),
                g -> g.piece.equals("body") ? d.body : g.piece.equals(d.hairPiece) ? d.hairTex
                        : g.piece.equals(d.beardPiece) ? d.beardTex : d.clothes.get(g.piece),
                Survivors.buffers(d), light, overlay, pose, collector);
        pose.popPose();
    }
}
