package com.zomboidcraft.client.combat;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * A player model of the first-person gun arms' own. The arms are queued now and drawn later, so they mustn't share
 * their parts with the player's model, which the third-person body (and a Zomboid survivor drawn over it) moves and
 * hides in between.
 */
public final class ArmModels {
    static PlayerModel wide, slim;

    private ArmModels() {
    }

    /** Baked again with each reload of the renderers. */
    public static void bake(EntityRendererProvider.Context context) {
        wide = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }
}
