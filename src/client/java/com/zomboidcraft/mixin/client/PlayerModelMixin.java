package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.combat.HoldPoses;
import com.zomboidcraft.client.survivor.Survivors;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    /** A survivor drawn with this model last time hid its parts: they come back before it's posed for anyone else. */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
    private void zomboidcraft$showAgain(AvatarRenderState state, CallbackInfo ci) {
        Survivors.hide((PlayerModel) (Object) this, false);
    }

    /**
     * After vanilla has posed the player, put their arms round the weapon they're holding. A player drawn as a Zomboid
     * survivor is posed first (the vanilla shoulders go onto hers), and after the holds her hands take the weapon.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void zomboidcraft$weaponHold(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        boolean survivor = Survivors.before(model, state);
        HoldPoses.apply(model, state);
        com.zomboidcraft.client.cars.CarHands.pose(model, state);
        if (survivor) Survivors.after(model, state);
    }
}
