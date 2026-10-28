package com.zomboidcraft.carry;

import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The extra slots the field kit adds to the player's inventory menu. */
public final class CarrySlots {
    /**
     * Which gear and bag slots the client is showing right now. The field-kit screen sets it, so bags on other tabs
     * don't take clicks; other screens show none of them. The server always treats them as there.
     */
    public static Predicate<Slot> clientShows = slot -> false;

    /**
     * Which locked pocket slots (ones still holding something from before) the client shows. Other screens show
     * them all so nothing is stranded; the field kit keeps them on their own tab.
     */
    public static Predicate<Slot> lockedShows = slot -> true;

    static boolean shown(Slot slot, Player player) {
        return !player.level().isClientSide() || clientShows.test(slot);
    }

    /** Back, vest and waist bags, and the belt, holster, sheath and watch. */
    public static final class GearSlot extends Slot {
        private final Player player;
        public final int gear;

        public GearSlot(Container container, int gear, Player player) {
            super(container, gear, 0, 0);
            this.player = player;
            this.gear = gear;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (gear >= GearStore.BELT) {
                return QuickSlots.gearSlot(stack) == gear;
            }
            BagSpec spec = BagItem.spec(stack);
            return spec != null && GearStore.slotFor(spec.wear()) == gear;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return shown(this, player);
        }
    }

    /** One slot inside a worn bag. Only the bag's own slot count is open, and its weight limit caps what fits. */
    public static final class BagSlot extends Slot {
        private final Player player;
        public final BagContainer bag;

        public BagSlot(BagContainer bag, int index, Player player) {
            super(bag, index, 0, 0);
            this.player = player;
            this.bag = bag;
        }

        public boolean open() {
            return getContainerSlot() < bag.usableSlots();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            BagSpec spec = bag.spec();
            return open() && stack.getItem().canFitInsideContainerItems() && spec != null && spec.holds().test(stack)
                    && getMaxStackSize(stack) > 0;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            BagSpec spec = bag.spec();
            if (spec == null || !open()) {
                return 0;
            }
            double unit = Weights.unit(stack);
            int vanilla = super.getMaxStackSize(stack);
            if (unit <= 0) {
                return vanilla;
            }
            double room = spec.capacity() - bag.contentsWeight(getContainerSlot());
            return Math.max(0, Math.min(vanilla, (int) Math.floor(room / unit + 1e-6)));
        }

        @Override
        public boolean isActive() {
            return open() && shown(this, player);
        }
    }

    /**
     * Like Minecraft's moveItemStackTo over slots [from, to), but every slot's own rules (locks, weight limits) apply
     * to merges too. Tops up matching stacks first, then fills empty slots.
     */
    public static void move(AbstractContainerMenu menu, ItemStack stack, int from, int to) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = from; i < to && !stack.isEmpty(); i++) {
                Slot target = menu.slots.get(i);
                ItemStack there = target.getItem();
                if (!target.mayPlace(stack)) {
                    continue;
                }
                int max = Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
                if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack)) {
                    int add = Math.min(stack.getCount(), max - there.getCount());
                    if (add > 0) {
                        there.grow(add);
                        stack.shrink(add);
                        target.setChanged();
                    }
                } else if (pass == 1 && there.isEmpty()) {
                    int add = Math.min(stack.getCount(), max);
                    if (add > 0) {
                        target.setByPlayer(stack.split(add));
                    }
                }
            }
        }
    }

    /**
     * Moves a weapon into the quick slots that suit it best (holster, sheath, sling, hip, hands), stopping at the one
     * it's already in (pass -1 when it isn't in one). Menus here share the inventory menu's hotbar indices.
     */
    public static void quick(AbstractContainerMenu menu, Player player, ItemStack stack, int current) {
        if (!Carry.weapon(stack) || !QuickSlots.on(player)) {
            return;
        }
        for (int q : QuickSlots.order(stack)) {
            if (q == current || stack.isEmpty()) {
                return;
            }
            int index = net.minecraft.world.inventory.InventoryMenu.USE_ROW_SLOT_START + q;
            move(menu, stack, index, index + 1);
        }
    }

    private CarrySlots() {
    }
}
