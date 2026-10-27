package com.zomboidcraft.mixin;

import com.zomboidcraft.carry.Carry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pickups put bags straight on, skip the locked pockets (and keep weapons out of all of them) and spill into worn
 * bags; worn gear drops with everything
 * else on death.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    // Unlocked slots (belt, pockets) come before locked ones, so a locked answer means there's no room.
    @Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$noLockedFreeSlot(CallbackInfoReturnable<Integer> cir) {
        if (Carry.locked(player, cir.getReturnValue())) {
            cir.setReturnValue(-1);
        }
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$noLockedMerge(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (Carry.locked(player, cir.getReturnValue())) {
            cir.setReturnValue(-1);
        }
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$wearPickedUpBag(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == -1 && Carry.wear(player, stack)) {
            cir.setReturnValue(true);
        }
    }

    /** A weapon picked up (or put back) goes on the belt, the sling or into a bag, never into a pocket. */
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$weaponsSkipPockets(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide() || stack.isEmpty() || !Carry.weapon(stack) || slot != -1 && !Carry.pocket(player, slot)
                || player.hasInfiniteMaterials()) {
            return;
        }
        cir.setReturnValue(Carry.takeWeapon(player, stack));
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$overflowIntoBags(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == -1 && !stack.isEmpty() && Carry.stow(player, stack)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void zomboidcraft$dropGear(CallbackInfo ci) {
        Carry.gear(player).takeAll(stack -> {
            // scattered round where you fell, like the rest of your things
            ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack);
            dropped.setPickUpDelay(40);
            float speed = player.getRandom().nextFloat() * 0.5f;
            float angle = player.getRandom().nextFloat() * (float) (Math.PI * 2);
            dropped.setDeltaMovement(-Math.sin(angle) * speed, 0.2, Math.cos(angle) * speed);
            player.level().addFreshEntity(dropped);
        });
    }
}
