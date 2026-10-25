package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.combat.HoldPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    /** After vanilla has posed the player, put their arms round the weapon they're holding. */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void zomboidcraft$weaponHold(AvatarRenderState state, CallbackInfo ci) {
        HoldPoses.apply((PlayerModel) (Object) this, state);
    }
}
