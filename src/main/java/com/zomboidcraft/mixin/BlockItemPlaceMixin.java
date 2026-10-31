package com.zomboidcraft.mixin;

import com.zomboidcraft.building.Placing;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla blocks aren't building material in survival; earth only fills the ground back in (building/Placing). */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$place(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!Placing.allowed(context)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
