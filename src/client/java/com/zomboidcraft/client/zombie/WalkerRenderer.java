package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieLook;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Draws every walker (zombie, husk, zombie villager) as a dressed, slumped human. */
public class WalkerRenderer extends HumanoidMobRenderer<Zombie, WalkerRenderState, WalkerModel> {
    public WalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new WalkerModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public WalkerRenderState createRenderState() {
        return new WalkerRenderState();
    }

    @Override
    public void extractRenderState(Zombie zombie, WalkerRenderState state, float partialTick) {
        super.extractRenderState(zombie, state, partialTick);
        state.isAggressive = zombie.isAggressive();
        state.texture = ZomboidCraft.id("textures/entity/walker/" + ZombieLook.textureName(zombie) + ".png");
        state.variant = (int) zombie.getUUID().getLeastSignificantBits();
    }

    @Override
    public Identifier getTextureLocation(WalkerRenderState state) {
        return state.texture;
    }
}
