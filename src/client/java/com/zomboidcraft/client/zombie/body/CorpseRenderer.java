package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Deaths;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.zombie.Corpse;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Draws a dead zombie's body (zombie.Corpse) in the pose its fall ended in (ZDeath works it out from the death, as it
 * did for the fall), in its clothes and with its stumps; dark-eyed. Hidden while the zombie it was is still falling
 * in its place. Far off it's drawn as the baked body, as far-off zombies are.
 */
public class CorpseRenderer extends EntityRenderer<Corpse, CorpseRenderer.State> {
    public static class State extends EntityRenderState {
        ZBodyMesh mesh, low;
        Identifier skin, kit, lowSkin;
        final ZMove.Pose pose = new ZMove.Pose();
        int cuts, missingLimb, variant;
        boolean legless, hidden;
        float yaw, size = 1f;
    }

    private final ZDeath death = new ZDeath();
    private final ZBodyDraw draw = new ZBodyDraw();
    /** Each body's fall, worked out once. */
    private final Int2ObjectOpenHashMap<ZDeath.Fall> falls = new Int2ObjectOpenHashMap<>();
    private long frames;

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(Corpse corpse, float partialTick) {
        // it lies up to two blocks off its feet, any way round
        return corpse.getBoundingBox().inflate(2.2, 0.6, 2.2);
    }

    @Override
    public void extractRenderState(Corpse corpse, State state, float partialTick) {
        super.extractRenderState(corpse, state, partialTick);
        String outfit = corpse.outfit();
        state.mesh = ZBodyMesh.get(outfit);
        int from = corpse.from();
        Entity zombie = from < 0 ? null : corpse.level().getEntity(from);
        state.hidden = state.mesh == null || zombie != null && !zombie.isRemoved();
        if (state.mesh == null) {
            return;
        }
        state.low = ZcConfig.get().performance.bakedZombies ? ZBodyMesh.get(outfit + "_low") : null;
        state.skin = ZomboidCraft.id("textures/entity/zbody/skin/" + outfit + ".png");
        state.kit = ZomboidCraft.id("textures/entity/zbody/kit/" + outfit + ".png");
        state.lowSkin = ZomboidCraft.id("textures/entity/zbody/low/" + outfit + ".png");
        state.cuts = corpse.limbs();
        state.missingLimb = corpse.missingLimb();
        state.legless = corpse.legless();
        state.variant = corpse.variant();
        state.size = corpse.size();
        long d = corpse.death();
        state.yaw = Deaths.yaw(d);
        ZDeath.Fall fall = falls.computeIfAbsent(corpse.getId(), id -> new ZDeath.Fall());
        fall.set(Deaths.kind(d), Deaths.heading(d), Deaths.force(d), Deaths.part(d), Deaths.seed(d),
                (state.cuts & Dismember.RIGHT_LEG) != 0, state.legless, ZBodyLayer.stumps(state.cuts, state.missingLimb));
        death.rest(state.pose, fall, state.mesh);
        if (++frames % 2400 == 0) {
            falls.keySet().removeIf(id -> corpse.level().getEntity(id) == null);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.hidden) {
            return;
        }
        poseStack.pushPose();
        // as a living zombie is drawn: turned to its yaw, the model flipped upright, its feet on the ground
        poseStack.scale(state.size, state.size, state.size);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - state.yaw));
        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(0f, -1.501f, 0f);
        // lying flat, so it gets less detail sooner than one standing
        double distSq = state.distanceToCameraSq;
        int lod = Math.max(ZDetail.level(distSq), distSq < 10 * 10 ? ZDetail.FULL : distSq < 24 * 24 ? ZDetail.MID : ZDetail.LOW);
        draw.draw(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.mesh, state.low, state.skin,
                state.kit, state.lowSkin, null, state.pose, state.cuts, state.missingLimb, state.legless, state.variant, lod);
        poseStack.popPose();
    }
}
