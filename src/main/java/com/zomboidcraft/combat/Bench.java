package com.zomboidcraft.combat;

import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.Treatment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Weapon bench work on a menu laid out like the player's inventory menu (0-45 as Minecraft's, then gear and bags):
 * fitting a part from the cursor or straight from wherever you carry it, and taking one off into your bags. Both the
 * field kit's menu and the plain inventory menu (the kit screen's) send their bench clicks here as menu buttons.
 */
public final class Bench {
    /** The field kit's cursor clicks: BENCH + gun slot * 8 + socket. */
    public static final int BENCH = 1000;
    /** Take a part off into your bags: TAKE_OFF + gun slot * 8 + socket. */
    public static final int TAKE_OFF = 500_000;
    /** Fit a part straight from a carried slot: FIT + (part slot * 256 + gun slot) * 8 + socket. */
    public static final int FIT = 1_000_000;

    /** Mend a weapon with what's in another carried slot: REPAIR + material slot * 256 + weapon slot. */
    public static final int REPAIR = 100_000_000;

    /** See to a wound from the health page: TREAT + treatment * 8 + body part. */
    public static final int TREAT = 200_000_000;

    /** Where the gear slots start in both menus: right after Minecraft's off hand. */
    static final int FIRST_GEAR = InventoryMenu.SHIELD_SLOT + 1;

    public static int cursorButton(int gunSlot, Attachments.Slot socket) {
        return BENCH + gunSlot * 8 + socket.ordinal();
    }

    public static int takeOffButton(int gunSlot, Attachments.Slot socket) {
        return TAKE_OFF + gunSlot * 8 + socket.ordinal();
    }

    public static int repairButton(int materialSlot, int weaponSlot) {
        return REPAIR + materialSlot * 256 + weaponSlot;
    }

    public static int treatButton(Treatment.Kind kind, Body.Region region) {
        return TREAT + kind.ordinal() * 8 + region.ordinal();
    }

    public static int fitButton(int partSlot, int gunSlot, Attachments.Slot socket) {
        return FIT + (partSlot * 256 + gunSlot) * 8 + socket.ordinal();
    }

    /**
     * Handles a bench button, or returns false for any other id. {@code carriedEnd} is one past the last slot that
     * is the player's own (the field kit's nearby slots come after it).
     */
    public static boolean click(AbstractContainerMenu menu, Player player, int id, int carriedEnd) {
        var sockets = Attachments.Slot.values();
        if (id >= TREAT) {
            int n = id - TREAT;
            if (n / 8 >= Treatment.Kind.values().length || n % 8 >= Body.Region.values().length) return false;
            // the server runs it as a timed action; the client waits to hear how the body changed
            if (player instanceof ServerPlayer serverPlayer) {
                Treatment.start(serverPlayer, Treatment.Kind.values()[n / 8], Body.Region.values()[n % 8]);
            }
            return true;
        }
        if (id >= REPAIR) {
            int n = id - REPAIR;
            int weapon = n % 256, material = n / 256;
            return carried(menu, weapon, carriedEnd) && carried(menu, material, carriedEnd) && Repairs.repair(menu, player, weapon, material);
        }
        if (id >= FIT) {
            int n = id - FIT;
            int socket = n % 8;
            int gun = n / 8 % 256;
            int part = n / 8 / 256;
            return socket < sockets.length && fit(menu, player, gun, sockets[socket], part, carriedEnd);
        }
        if (id >= TAKE_OFF) {
            int n = id - TAKE_OFF;
            return n % 8 < sockets.length && takeOff(menu, player, n / 8, sockets[n % 8], carriedEnd);
        }
        if (id >= BENCH) {
            int n = id - BENCH;
            return n % 8 < sockets.length && cursor(menu, player, n / 8, sockets[n % 8], carriedEnd);
        }
        return false;
    }

    /** Whether the slot is one of the player's own carried slots: pockets, belt, off hand, gear or a bag. */
    private static boolean carried(AbstractContainerMenu menu, int slot, int carriedEnd) {
        return slot >= InventoryMenu.INV_SLOT_START && slot < Math.min(carriedEnd, menu.slots.size());
    }

    private static ItemStack gun(AbstractContainerMenu menu, int gunSlot, Attachments.Slot socket, int carriedEnd) {
        if (!carried(menu, gunSlot, carriedEnd)) return ItemStack.EMPTY;
        ItemStack gun = menu.slots.get(gunSlot).getItem();
        return Attachments.slots(gun).contains(socket) ? gun : ItemStack.EMPTY;
    }

    /**
     * The field kit's way: holding a part that goes there fits it (whatever was there comes back to the cursor, or
     * to you if the cursor still holds more parts); with nothing held, the fitted part comes off onto the cursor.
     */
    private static boolean cursor(AbstractContainerMenu menu, Player player, int gunSlot, Attachments.Slot socket, int carriedEnd) {
        ItemStack gun = gun(menu, gunSlot, socket, carriedEnd);
        if (gun.isEmpty()) return false;
        ItemStack held = menu.getCarried();
        boolean fitting = !held.isEmpty();
        if (fitting) {
            if (Attachments.slotOf(held) != socket) return false;
            ItemStack old = Attachments.mount(gun, held);
            if (old == null) return false;
            held.shrink(1);
            if (!old.isEmpty()) {
                if (held.isEmpty()) {
                    menu.setCarried(old);
                } else {
                    give(player, old);
                }
            }
        } else {
            ItemStack off = Attachments.unmount(gun, socket);
            if (off.isEmpty()) return false;
            menu.setCarried(off);
        }
        done(menu, player, gunSlot, socket, fitting);
        return true;
    }

    /** Fits one part from a carried slot; whatever it replaces goes back where the part came from, or into your bags. */
    private static boolean fit(AbstractContainerMenu menu, Player player, int gunSlot, Attachments.Slot socket, int partSlot,
                               int carriedEnd) {
        if (partSlot == gunSlot || !carried(menu, partSlot, carriedEnd)) return false;
        ItemStack gun = gun(menu, gunSlot, socket, carriedEnd);
        if (gun.isEmpty()) return false;
        Slot from = menu.slots.get(partSlot);
        ItemStack part = from.getItem();
        if (part.isEmpty() || Attachments.slotOf(part) != socket || !from.mayPickup(player)) return false;
        ItemStack old = Attachments.mount(gun, part);
        if (old == null) return false;
        part.shrink(1);
        if (part.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        if (!old.isEmpty()) {
            if (!from.hasItem() && from.mayPlace(old)) {
                from.setByPlayer(old);
            } else {
                stow(menu, player, old, carriedEnd);
            }
        }
        done(menu, player, gunSlot, socket, true);
        return true;
    }

    /** Takes a fitted part off into your bags, then pockets, then belt; dropped at your feet if there's no room. */
    private static boolean takeOff(AbstractContainerMenu menu, Player player, int gunSlot, Attachments.Slot socket, int carriedEnd) {
        ItemStack gun = gun(menu, gunSlot, socket, carriedEnd);
        if (gun.isEmpty()) return false;
        ItemStack off = Attachments.unmount(gun, socket);
        if (off.isEmpty()) return false;
        stow(menu, player, off, carriedEnd);
        done(menu, player, gunSlot, socket, false);
        return true;
    }

    private static void stow(AbstractContainerMenu menu, Player player, ItemStack stack, int carriedEnd) {
        int end = Math.min(carriedEnd, menu.slots.size());
        CarrySlots.move(menu, stack, FIRST_GEAR + GearStore.SIZE, end);
        CarrySlots.move(menu, stack, InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END);
        CarrySlots.move(menu, stack, InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END);
        if (!stack.isEmpty()) give(player, stack);
    }

    private static void give(Player player, ItemStack stack) {
        if (player.getInventory().add(stack) && stack.isEmpty()) return;
        if (!player.level().isClientSide()) {
            player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack));
        }
    }

    private static void done(AbstractContainerMenu menu, Player player, int gunSlot, Attachments.Slot socket, boolean fitting) {
        menu.slots.get(gunSlot).setChanged();
        SoundEvent sound = socket == Attachments.Slot.MAGAZINE
                ? (fitting ? ModSounds.RIFLE_MAG_IN : ModSounds.RIFLE_MAG_OUT)
                : SoundEvents.ARMOR_EQUIP_IRON.value();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    private Bench() {
    }
}
