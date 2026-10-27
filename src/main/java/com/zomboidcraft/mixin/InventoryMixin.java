package com.zomboidcraft.mixin;

import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.QuickSlots;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    /** What add() is placing right now, so the free-slot search can keep it out of quick slots it doesn't fit. */
    @Unique
    private ItemStack zomboidcraft$adding;

    // Unlocked slots (belt, pockets) come before locked ones, so a locked answer means there's no room. With the quick
    // slots on, something picked up goes to a pocket first; pouches and the hip only take it once bags are full too.
    @Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$noLockedFreeSlot(CallbackInfoReturnable<Integer> cir) {
        int slot = cir.getReturnValue();
        if (QuickSlots.on(player) && Inventory.isHotbarSlot(slot)) {
            Inventory self = (Inventory) (Object) this;
            ItemStack adding = zomboidcraft$adding;
            slot = -1;
            for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE && slot < 0; i++) {
                if (self.getItem(i).isEmpty() && !Carry.locked(player, i)) slot = i;
            }
            for (int i = 0; adding == null && slot < 0 && i < Inventory.getSelectionSize(); i++) {
                if (self.getItem(i).isEmpty() && QuickSlots.open(player, i)) slot = i;
            }
            cir.setReturnValue(slot);
        } else if (Carry.locked(player, slot)) {
            cir.setReturnValue(-1);
        }
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$noLockedMerge(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int slot = cir.getReturnValue();
        if (Carry.locked(player, slot) || Inventory.isHotbarSlot(slot) && !QuickSlots.fits(player, slot, stack)) {
            cir.setReturnValue(-1);
        }
    }

    /**
     * Pickups: a bag, belt, holster, sheath or watch goes straight on; a weapon goes to its quick slot or a bag, never a
     * pocket; anything else is Minecraft's own search (pockets, see above).
     */
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$routePickup(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        zomboidcraft$adding = null;
        if (slot == -1 && Carry.wear(player, stack)) {
            cir.setReturnValue(true);
            return;
        }
        if (!player.level().isClientSide() && !stack.isEmpty() && Carry.weapon(stack) && (slot == -1 || Carry.pocket(player, slot))
                && !player.hasInfiniteMaterials()) {
            cir.setReturnValue(Carry.takeWeapon(player, stack));
            return;
        }
        zomboidcraft$adding = slot == -1 ? stack : null;
    }

    /** What didn't fit spills into worn bags, then a free pouch or hip slot. */
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$overflowIntoBags(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        zomboidcraft$adding = null;
        if (slot == -1 && !stack.isEmpty() && !player.level().isClientSide()
                && (Carry.stow(player, stack) | Carry.quick(player, stack))) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Your own client skips quick slots you don't have: scrolling steps over them, and a number key for one does
     * nothing. The server takes whatever slot the client settles on.
     */
    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$skipClosedSlots(int slot, CallbackInfo ci) {
        Inventory self = (Inventory) (Object) this;
        if (!player.level().isClientSide() || !Inventory.isHotbarSlot(slot) || QuickSlots.open(player, slot)) {
            return;
        }
        ci.cancel();
        int size = Inventory.getSelectionSize();
        int current = self.getSelectedSlot();
        int step = slot == Math.floorMod(current + 1, size) ? 1 : slot == Math.floorMod(current - 1, size) ? -1 : 0;
        if (step == 0) {
            return;
        }
        for (int i = 1; i < size; i++) {
            int next = Math.floorMod(slot + step * i, size);
            if (QuickSlots.open(player, next)) {
                self.setSelectedSlot(next);
                return;
            }
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
