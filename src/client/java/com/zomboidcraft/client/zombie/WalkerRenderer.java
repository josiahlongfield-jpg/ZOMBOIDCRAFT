package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieLook;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Draws every walker (zombie, husk, zombie villager) as a dressed, slumped human. */
public class WalkerRenderer extends HumanoidMobRenderer<Zombie, WalkerRenderState, WalkerModel> {
    public WalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new WalkerModel(context.bakeLayer(WalkerModel.LAYER)), 0.5f);
        addLayer(new WalkerAddonLayer(this, new WalkerAddonModel(context.bakeLayer(WalkerAddonModel.LAYER))));
        addLayer(new WalkerEyesLayer(this));
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
        ZcConfig.Look look = ZcConfig.get().look;
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
        state.variant = (int) zombie.getUUID().getLeastSignificantBits();
    }

    @Override
    public Identifier getTextureLocation(WalkerRenderState state) {
        return state.texture;
    }
}
