package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.reach.ReachSkyState;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Reach world's region skies (see ReachSkies): the sky and fog colours, sky light and stars the game reads for
 * the camera come from the region you're in, blended at the borders.
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class ReachSkyMixin {
    @Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", at = @At("RETURN"),
            cancellable = true)
    private void zomboidcraft$reachSky(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
        Object value = ReachSkyState.override(attribute, cir.getReturnValue());
        if (value != null) {
            cir.setReturnValue(value);
        }
    }
}
