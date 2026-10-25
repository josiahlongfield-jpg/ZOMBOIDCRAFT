package com.zomboidcraft.mixin;

import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.GearContainer;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.carry.Wear;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the player's own inventory menu the field kit's slots: gear (back, vest, waist, sling) and the inside of
 * every worn bag, after Minecraft's 46. Both sides build the same slots, so the usual menu sync carries them.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin implements Carry.Bags {
    @Unique
    private BagContainer[] zomboidcraft$bags = new BagContainer[0];
    @Unique
    private int zomboidcraft$firstExtra = Integer.MAX_VALUE;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void zomboidcraft$addCarrySlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        MenuInvoker menu = (MenuInvoker) this;
        zomboidcraft$firstExtra = self.slots.size();
        GearContainer gear = new GearContainer(owner);
        for (int g = 0; g < GearStore.SIZE; g++) {
            menu.zomboidcraft$addSlot(new CarrySlots.GearSlot(gear, g, owner));
        }
        zomboidcraft$bags = new BagContainer[]{
                new BagContainer(Wear.BACK, () -> gear.getItem(GearStore.BACK)),
                new BagContainer(Wear.VEST, () -> gear.getItem(GearStore.VEST)),
                new BagContainer(Wear.WAIST, () -> gear.getItem(GearStore.WAIST)),
                new BagContainer(Wear.HAND, () -> inventory.getItem(Inventory.SLOT_OFFHAND)),
        };
        for (BagContainer bag : zomboidcraft$bags) {
            for (int i = 0; i < bag.getContainerSize(); i++) {
                menu.zomboidcraft$addSlot(new CarrySlots.BagSlot(bag, i, owner));
            }
        }
    }

    @Override
    public BagContainer[] zomboidcraft$bags() {
        return zomboidcraft$bags;
    }

    /** Shift-click: bags go on, carried things go into bags, and anything from gear or bags comes back to hand. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (index < 0 || index >= self.slots.size()) {
            return;
        }
        Slot slot = self.slots.get(index);
        boolean fromKit = index >= zomboidcraft$firstExtra;
        boolean fromCarried = index >= InventoryMenu.INV_SLOT_START && index < InventoryMenu.USE_ROW_SLOT_END;
        if (!slot.hasItem() || (!fromKit && !fromCarried)) {
            return;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (fromKit) {
            CarrySlots.move(self, stack, InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END);
        } else {
            BagSpec spec = BagItem.spec(stack);
            int gearSlot = spec == null ? -1 : GearStore.slotFor(spec.wear());
            if (gearSlot >= 0) {
                CarrySlots.move(self, stack, zomboidcraft$firstExtra + gearSlot, zomboidcraft$firstExtra + gearSlot + 1);
            }
            if (stack.getCount() == before.getCount()) {
                CarrySlots.move(self, stack, zomboidcraft$firstExtra + GearStore.SIZE, self.slots.size());
            }
            if (stack.getCount() == before.getCount()) {
                return; // no bag took it: Minecraft's own pockets-and-belt shuffle
            }
        }
        if (stack.getCount() == before.getCount()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        cir.setReturnValue(before);
    }
}
