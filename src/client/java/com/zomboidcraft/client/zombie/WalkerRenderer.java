package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieLook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.client.zombie.body.Gibs;
import com.zomboidcraft.client.zombie.body.ZBodyAnimator;
import com.zomboidcraft.client.zombie.body.ZBodyLayer;
import com.zomboidcraft.client.zombie.body.ZBodyMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Draws every walker (zombie, husk, zombie villager): the new Zomboid-style body with its worn clothes
 * and movements where the outfit has one, otherwise the older dressed, slumped model.
 */
public class WalkerRenderer extends HumanoidMobRenderer<Zombie, WalkerRenderState, WalkerModel> {
    private final ZBodyAnimator animator = new ZBodyAnimator();
    private final ZBodyAnimator.Facts facts = new ZBodyAnimator.Facts();
    /** Each outfit's new-body skin, clothes atlas and eye mask. */
    private static final java.util.Map<String, Identifier[]> BODY_TEXTURES = new java.util.HashMap<>();

    public WalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new WalkerModel(context.bakeLayer(WalkerModel.LAYER)), 0.5f);
        // held items and head blocks sit on the old model's parts, so only it shows them
        layers.replaceAll(layer -> new OldBodyOnly(this, layer));
        addLayer(new WalkerAddonLayer(this, new WalkerAddonModel(context.bakeLayer(WalkerAddonModel.LAYER))));
        addLayer(new WalkerEyesLayer(this));
        addLayer(new ZBodyLayer(this));
        addLayer(new com.zomboidcraft.client.zombie.body.BoltsLayer(this));
    }

    private static final class OldBodyOnly extends RenderLayer<WalkerRenderState, WalkerModel> {
        private final RenderLayer<WalkerRenderState, WalkerModel> layer;

        OldBodyOnly(RenderLayerParent<WalkerRenderState, WalkerModel> parent, RenderLayer<WalkerRenderState, WalkerModel> layer) {
            super(parent);
            this.layer = layer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                WalkerRenderState state, float yRot, float xRot) {
            if (state.zbody == null) {
                layer.submit(poseStack, collector, light, state, yRot, xRot);
            }
        }
    }

    @Override
    public WalkerRenderState createRenderState() {
        return new WalkerRenderState();
    }

    @Override
    public void extractRenderState(Zombie zombie, WalkerRenderState state, float partialTick) {
        super.extractRenderState(zombie, state, partialTick);
        state.isAggressive = zombie.isAggressive();
        state.outfit = ZombieLook.outfit(zombie);
        state.missingLimb = ZombieLook.missingLimb(zombie);
        int limbs = Dismember.limbs(zombie);
        state.cuts = limbs & (Dismember.CUTS | Dismember.HEAD_GONE);
        state.bolts = zombie.getAttachedOrElse(com.zomboidcraft.combat.StuckBolts.BOLTS, java.util.List.of());
        ZcConfig.Look look = ZcConfig.get().look;
        state.zbody = look.newZombies ? ZBodyMesh.get(state.outfit) : null;
        if (state.zbody != null) {
            // the old model's skins aren't drawn under the new body, so don't build their names every frame
            Identifier[] tex = BODY_TEXTURES.computeIfAbsent(state.outfit, o -> new Identifier[] {
                    ZomboidCraft.id("textures/entity/zbody/skin/" + o + ".png"),
                    ZomboidCraft.id("textures/entity/zbody/kit/" + o + ".png"),
                    ZomboidCraft.id("textures/entity/zbody/eyes/" + o + ".png")});
            state.texture = tex[0];
            state.addonTexture = null;
            state.eyesTexture = null;
        } else {
            String name = state.outfit + "_" + ZombieLook.skin(zombie);
            String hd = name + "_" + ZombieLook.damage(zombie) + ".png";
            state.texture = look.hdSkins
                    ? ZomboidCraft.id("textures/entity/walker_hd/" + hd)
                    : ZomboidCraft.id("textures/entity/walker/" + name + ".png");
            state.addonTexture = !look.outfitPieces || !WalkerAddonParts.PIECES.containsKey(state.outfit) ? null
                    : look.hdSkins
                    ? ZomboidCraft.id("textures/entity/walker_addons_hd/" + hd)
                    : ZomboidCraft.id("textures/entity/walker_addons/" + name + ".png");
            state.eyesTexture = !look.glowingEyes ? null
                    : look.hdSkins
                    ? ZomboidCraft.id("textures/entity/walker_eyes_hd/" + hd)
                    : ZomboidCraft.id("textures/entity/walker_eyes/" + name + ".png");
        }
        state.variant = (int) zombie.getUUID().getLeastSignificantBits();
        state.crawler = Downed.isCrawler(zombie);
        boolean low = zombie.hasPose(Pose.SWIMMING);
        state.legless = low && Downed.isLegless(zombie);
        if (state.zbody != null) {
            Identifier[] tex = BODY_TEXTURES.get(state.outfit);
            state.zskin = tex[0];
            state.zkit = tex[1];
            state.zeyes = look.glowingEyes ? tex[2] : null;
            facts.age = state.ageInTicks;
            facts.walkPos = state.walkAnimationPos;
            facts.walkSpeed = state.walkAnimationSpeed;
            facts.headYaw = state.yRot;
            facts.variant = state.variant;
            facts.aggressive = state.isAggressive;
            facts.low = low;
            facts.crawler = state.crawler;
            facts.legless = state.legless;
            facts.cuts = state.cuts;
            int newly = animator.animate(zombie, facts, partialTick, state.zpose);
            if (newly != 0) {
                // the limb flies off in the pose it was in the moment before
                Gibs.spawn(state.zbody, state.zskin, state.zkit, ZBodyLayer.pose(state.zbody, animator.before, state.legless),
                        newly, state.cuts & ~newly | ZBodyLayer.missingCuts(state.missingLimb), limbs,
                        state.x, state.y, state.z, state.bodyRot, state.scale, state.variant);
            }
        }
    }

    /** The new body draws itself in its own layer; the old model stays hidden under it. */
    @Override
    protected RenderType getRenderType(WalkerRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        return state.zbody != null ? null : super.getRenderType(state, bodyVisible, translucent, glowing);
    }

    /**
     * Knocked down or crawling (the swimming pose): tip the old body over as the pose blends in,
     * face down and head first for a crawler, flat on its back for one knocked off its feet.
     * The new body lies down through its own movements.
     */
    @Override
    protected void setupRotations(WalkerRenderState state, PoseStack poseStack, float bodyRot, float scale) {
        super.setupRotations(state, poseStack, bodyRot, scale);
        float lie = state.swimAmount;
        if (lie > 0f && state.zbody == null) {
            poseStack.rotateDegrees(Axis.XP, lie * (state.crawler ? -90f : 90f));
            poseStack.translate(0f, -lie, lie * (state.crawler ? 0.3f : -0.3f));
        }
    }

    @Override
    public Identifier getTextureLocation(WalkerRenderState state) {
        return state.texture;
    }
}
