package com.zomboidcraft.loot;

import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.GearContainer;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.mixin.CraftingMenuAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The field kit with the world around you: everything the player's own inventory menu has (in the same slot order,
 * so the screen lays both out the same way), then the floor and each searchable container in reach as tabs.
 * Carried slots, bags and the 2x2 grid are the player's own containers, shared with their inventory menu.
 */
public class NearbyMenu extends AbstractContainerMenu implements Carry.Bags {
    public static final int TAKE_ALL = 100;
    public static int benchButton(int gunSlot, com.zomboidcraft.combat.Attachments.Slot socket) {
        return com.zomboidcraft.combat.Bench.cursorButton(gunSlot, socket);
    }

    private final Player player;
    private final CraftingContainer craft;
    private final ResultContainer result;
    private final List<Nearby.Source> sources;
    private final List<Container> containers;
    private final List<Container> opened = new ArrayList<>();
    /** Start of the gear slots, and of each source's slots (one past the end last). */
    public final int firstGear;
    private final int[] sourceStart;
    /** The tab showing on the right, and the one shift-clicks send carried things to when it's a container. */
    public int selected;

    /** Client side: empty stand-ins the server fills through the usual menu sync. */
    public NearbyMenu(int id, Inventory inventory, Nearby.Opening opening) {
        this(id, inventory, opening.sources(), standIns(opening.sources()), opening.selected());
    }

    private static List<Container> standIns(List<Nearby.Source> sources) {
        List<Container> list = new ArrayList<>();
        for (Nearby.Source source : sources) {
            list.add(new SimpleContainer(source.size()));
        }
        return list;
    }

    NearbyMenu(int id, Inventory inventory, List<Nearby.Source> sources, List<Container> containers, int selected) {
        super(Nearby.MENU, id);
        this.player = inventory.player;
        this.sources = List.copyOf(sources);
        this.containers = List.copyOf(containers);
        this.selected = selected;
        InventoryMenu own = player.inventoryMenu;
        craft = own.getCraftSlots();
        result = ((CraftingMenuAccessor) own).zomboidcraft$resultSlots();

        // 0-45 exactly as InventoryMenu: result, 2x2, armour, pockets, belt, off hand
        addSlot(new ResultSlot(player, craft, result, 0, 0, 0));
        for (int i = 0; i < 4; i++) {
            addSlot(new Slot(craft, i, 0, 0));
        }
        EquipmentSlot[] armour = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Identifier[] icons = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
        for (int i = 0; i < 4; i++) {
            addSlot(new ArmorSlot(inventory, player, armour[i], 39 - i, 0, 0, icons[i]));
        }
        addStandardInventorySlots(inventory, 0, 0);
        addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, 0, 0) {
            @Override
            public void setByPlayer(ItemStack stack, ItemStack previous) {
                player.onEquipItem(EquipmentSlot.OFFHAND, previous, stack);
                super.setByPlayer(stack, previous);
            }

            @Override
            public Identifier getNoItemIcon() {
                return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
        });

        // gear and bags, sharing the inventory menu's bag containers
        firstGear = slots.size();
        GearContainer gear = new GearContainer(player);
        for (int g = 0; g < GearStore.SIZE; g++) {
            addSlot(new CarrySlots.GearSlot(gear, g, player));
        }
        for (BagContainer bag : zomboidcraft$bags()) {
            for (int i = 0; i < bag.getContainerSize(); i++) {
                addSlot(new CarrySlots.BagSlot(bag, i, player));
            }
        }

        // the floor and each container in reach
        sourceStart = new int[this.containers.size() + 1];
        for (int s = 0; s < this.containers.size(); s++) {
            sourceStart[s] = slots.size();
            Container container = this.containers.get(s);
            for (int i = 0; i < container.getContainerSize(); i++) {
                addSlot(new NearbySlot(container, i, s));
            }
        }
        sourceStart[this.containers.size()] = slots.size();
    }

    /** One slot of the floor or of a container, shown only while its tab is. */
    public final class NearbySlot extends Slot {
        public final int source;

        NearbySlot(Container container, int index, int source) {
            super(container, index, 0, 0);
            this.source = source;
        }

        private boolean reachable() {
            return !(container instanceof Nearby.Reach reach) || reach.inReach(player);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return reachable() && container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return reachable();
        }

        @Override
        public boolean isActive() {
            return !player.level().isClientSide() || selected == source;
        }
    }

    public List<Nearby.Source> sources() {
        return sources;
    }

    public int sourceStart(int source) {
        return sourceStart[source];
    }

    public int sourceEnd(int source) {
        return sourceStart[source + 1];
    }

    @Override
    public BagContainer[] zomboidcraft$bags() {
        return player.inventoryMenu instanceof Carry.Bags bags ? bags.zomboidcraft$bags() : new BagContainer[0];
    }

    private net.minecraft.core.BlockPos usedAt;
    private String usedSound;

    /** The container the player used to get here: its lid comes up, and it sounds again when they're done. */
    void opened(Container container, net.minecraft.core.BlockPos pos, String sound) {
        container.startOpen(player);
        opened.add(container);
        usedAt = pos;
        usedSound = sound;
    }

    @Override
    public void broadcastChanges() {
        for (Container container : containers) {
            if (container instanceof FloorContainer floor) {
                floor.tick();
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < sources.size()) {
            selected = id;
            return true;
        }
        int take = id - TAKE_ALL;
        if (take >= 0 && take < sources.size()) {
            for (int i = sourceStart(take); i < sourceEnd(take); i++) {
                if (slots.get(i).hasItem()) {
                    quickMoveStack(player, i);
                }
            }
            return true;
        }
        return com.zomboidcraft.combat.Bench.click(this, player, id, sourceStart(0));
    }

    private boolean isContainerTab(int source) {
        return source >= 0 && source < sources.size() && !sources.get(source).floor();
    }

    /**
     * Shift-click. Loot comes to you (worn if it's a bag with its place free, else into bags, then pockets and belt);
     * what you carry goes to the open container tab, or otherwise into your bags like the plain inventory.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int carriedStart = InventoryMenu.INV_SLOT_START;
        int carriedEnd = InventoryMenu.SHIELD_SLOT + 1;
        int bagsStart = firstGear + GearStore.SIZE;
        int bagsEnd = sourceStart[0];
        boolean toContainer = isContainerTab(selected);

        if (index == InventoryMenu.RESULT_SLOT) {
            CarrySlots.move(this, stack, bagsStart, bagsEnd);
            CarrySlots.move(this, stack, carriedStart, carriedEnd);
            slot.onQuickCraft(stack, before);
        } else if (index >= sourceStart[0]) {
            toYou(stack, bagsStart, bagsEnd, carriedStart, carriedEnd);
        } else if (index >= firstGear || index < InventoryMenu.INV_SLOT_START || index == InventoryMenu.SHIELD_SLOT) {
            // gear, bag contents, armour, the 2x2 and the off hand
            if (toContainer && index >= firstGear) {
                CarrySlots.move(this, stack, sourceStart(selected), sourceEnd(selected));
            }
            if (stack.getCount() == before.getCount()) {
                if (index >= firstGear + GearStore.SIZE) {
                    CarrySlots.move(this, stack, carriedStart, carriedEnd);
                } else {
                    CarrySlots.move(this, stack, bagsStart, bagsEnd);
                    CarrySlots.move(this, stack, carriedStart, carriedEnd);
                }
            }
        } else {
            // pockets and belt
            if (toContainer) {
                CarrySlots.move(this, stack, sourceStart(selected), sourceEnd(selected));
            }
            if (stack.getCount() == before.getCount()) {
                wearOrStow(stack, bagsStart, bagsEnd);
            }
            if (stack.getCount() == before.getCount()) {
                CarrySlots.move(this, stack, InventoryMenu.ARMOR_SLOT_START, InventoryMenu.ARMOR_SLOT_END);
            }
            if (stack.getCount() == before.getCount()) {
                boolean belt = index >= InventoryMenu.USE_ROW_SLOT_START;
                CarrySlots.move(this, stack, belt ? InventoryMenu.INV_SLOT_START : InventoryMenu.USE_ROW_SLOT_START,
                        belt ? InventoryMenu.INV_SLOT_END : InventoryMenu.USE_ROW_SLOT_END);
            }
        }

        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        if (index == InventoryMenu.RESULT_SLOT && !stack.isEmpty()) {
            // what's left of the craft no longer sits in the result slot: keep it, or drop it if there's no room
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(),
                        player.getX(), player.getEyeY() - 0.3, player.getZ(), stack.copyAndClear()));
            }
        }
        return before;
    }

    private void toYou(ItemStack stack, int bagsStart, int bagsEnd, int carriedStart, int carriedEnd) {
        int count = stack.getCount();
        wearOrStow(stack, bagsStart, bagsEnd);
        if (stack.getCount() == count) {
            CarrySlots.move(this, stack, carriedStart, carriedEnd);
        }
    }

    /** A bag goes on its gear slot when that's free; anything else goes into worn bags. */
    private void wearOrStow(ItemStack stack, int bagsStart, int bagsEnd) {
        BagSpec spec = BagItem.spec(stack);
        int gearSlot = spec == null ? -1 : GearStore.slotFor(spec.wear());
        if (gearSlot >= 0) {
            CarrySlots.move(this, stack, firstGear + gearSlot, firstGear + gearSlot + 1);
        } else {
            CarrySlots.move(this, stack, bagsStart, bagsEnd);
        }
    }

    /** Double-click gathering skips the craft result and tabs that aren't open. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && (!(slot instanceof NearbySlot loot) || loot.source == selected);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (Container container : opened) {
            container.stopOpen(player);
        }
        if (usedAt != null && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            LootSounds.play(level, usedAt, usedSound, true);
        }
        // like closing the inventory: whatever's left on the 2x2 comes back to you
        result.clearContent();
        if (!player.level().isClientSide()) {
            clearContainer(player, craft);
        }
    }
}
