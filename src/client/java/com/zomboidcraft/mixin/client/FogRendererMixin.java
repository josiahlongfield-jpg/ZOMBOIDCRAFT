package com.zomboidcraft.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zomboid's sight traits on the fog: Short Sighted pulls the far fog in (the world past it a blur), Eagle Eyed pushes
 * it out as far as the loaded world allows.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void zomboidcraft$sightTraits(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                          CallbackInfoReturnable<FogData> cir) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (com.zomboidcraft.client.reach.ReachFar.active()) {
            reachFog(camera, player, cir.getReturnValue());
            return;
        }
        float scale = com.zomboidcraft.client.kit.SightTraits.fogScale(player);
        if (scale == 1f) return;
        FogData fog = cir.getReturnValue();
        fog.renderDistanceStart *= scale;
        fog.renderDistanceEnd *= scale;
        if (scale < 1f) {
            fog.environmentalStart *= scale;
            fog.environmentalEnd *= scale;
        } else {
            // eagle eyes see further through rain and mist too, never past the world that's loaded
            fog.environmentalStart = Math.min(fog.environmentalStart * scale, renderDistance * 16f);
            fog.environmentalEnd = Math.min(fog.environmentalEnd * scale, renderDistance * 16f);
            fog.renderDistanceStart = Math.min(fog.renderDistanceStart, renderDistance * 16f);
            fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, renderDistance * 16f);
        }
    }

    /**
     * The Reach world's far haze: the edge-of-render-distance fog moves out to the far drawing distance so far
     * skylines and ships fade instead of being cut off. Water, lava, snow and blindness keep their own short fog.
     */
    private static void reachFog(Camera camera, net.minecraft.world.entity.player.Player player, FogData fog) {
        float start = com.zomboidcraft.client.reach.ReachFar.FOG_START;
        float end = com.zomboidcraft.client.reach.ReachFar.FOG_END;
        fog.renderDistanceStart = Math.max(fog.renderDistanceStart, start);
        fog.renderDistanceEnd = Math.max(fog.renderDistanceEnd, end);
        // the region's own fog (thick over stormy Exodus, light over Sword Base), thinning as you climb
        var sky = com.zomboidcraft.client.reach.ReachSkyState.current();
        if (sky != null) {
            start = sky.fogStart;
            end = sky.fogEnd;
        }
        var fluid = camera.getFluidInCamera();
        boolean open = fluid == net.minecraft.world.level.material.FogType.ATMOSPHERIC
                || fluid == net.minecraft.world.level.material.FogType.NONE;
        if (open && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && !player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)) {
            // the region's fog is the weather here (its storms and smoke are written into it)
            fog.environmentalStart = sky != null ? start : Math.max(fog.environmentalStart, start);
            fog.environmentalEnd = sky != null ? end : Math.max(fog.environmentalEnd, end);
        }
    }
}
