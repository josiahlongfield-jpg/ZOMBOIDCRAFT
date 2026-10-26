package com.zomboidcraft.mixin;

import com.zomboidcraft.building.DigRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How fast a block mines, on both sides alike so the crack you see is the one the server allows: town walls by tool
 * and skill, and nothing at all under the dig floor (building/DigRules).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateDigMixin {
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$dig(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float progress = DigRules.progress((BlockState) (Object) this, player, level, pos);
        if (!Float.isNaN(progress)) {
            cir.setReturnValue(progress);
        }
    }
}
