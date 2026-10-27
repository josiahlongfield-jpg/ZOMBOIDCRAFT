package com.zomboidcraft.mixin;

import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pocket limit, in every menu that shows the player's inventory: main slots past the pockets take nothing new,
 * and once empty they disappear. Weapons never go in a pocket. Whatever was already in them can still be taken out.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract int getContainerSlot();

    @Shadow
    public abstract boolean hasItem();

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$lockedPocket(net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof Inventory inventory && (Carry.locked(inventory.player, getContainerSlot())
                || Carry.weapon(stack) && Carry.pocket(inventory.player, getContainerSlot()))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$hideLockedPocket(CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof Inventory inventory && Carry.locked(inventory.player, getContainerSlot())) {
            boolean client = inventory.player.level().isClientSide();
            cir.setReturnValue(hasItem() && (!client || CarrySlots.lockedShows.test((Slot) (Object) this)));
        }
    }
}
