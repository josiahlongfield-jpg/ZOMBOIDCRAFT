package com.zomboidcraft.clothing;

import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.GearStore;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The eleven places clothing goes, Zomboid style, head to foot. Four are Minecraft's own armour slots (so a hat or
 * helmet still sits where vanilla, zombies and armour stands expect it); the vest and belt share the gear store's
 * bag-vest and belt places; the rest are clothing layers in the gear store.
 */
public enum ClothingSlot {
    HAT("Hat", EquipmentSlot.HEAD, -1),
    EYES("Eyes", null, GearStore.EYES),
    FACE("Face", null, GearStore.FACE),
    NECK("Neck", null, GearStore.NECK),
    SHIRT("Shirt", null, GearStore.SHIRT),
    JACKET("Jacket", EquipmentSlot.CHEST, -1),
    VEST("Vest", null, GearStore.VEST),
    HANDS("Hands", null, GearStore.HANDS),
    BELT("Belt", null, GearStore.BELT),
    LEGS("Legs", EquipmentSlot.LEGS, -1),
    FEET("Feet", EquipmentSlot.FEET, -1);

    public static final List<ClothingSlot> ALL = List.of(values());

    public final String label;
    /** The vanilla armour slot this is, or null when it lives in the gear store. */
    public final @Nullable EquipmentSlot armour;
    /** The gear store index, or -1 for an armour slot. */
    public final int gear;

    ClothingSlot(String label, @Nullable EquipmentSlot armour, int gear) {
        this.label = label;
        this.armour = armour;
        this.gear = gear;
    }

    /**
     * What's worn here. Right for the server and for your own client (the open menu keeps your gear store in step).
     * For other players on a client, read {@link #shown}.
     */
    public ItemStack worn(Player player) {
        return armour != null ? player.getItemBySlot(armour) : Carry.gear(player).get(gear);
    }

    /** What another player is seen wearing here, from the synced worn list (a plain copy of the item). */
    public ItemStack shown(Player player) {
        if (armour != null) {
            return player.getItemBySlot(armour);
        }
        List<ItemStack> worn = player.getAttachedOrElse(Carry.WORN, List.of());
        return gear < worn.size() ? worn.get(gear) : ItemStack.EMPTY;
    }

    /** This slot's index in the inventory menu, and in every menu here that keeps its order (the field kit's too). */
    public int menuSlot() {
        if (armour != null) {
            return InventoryMenu.ARMOR_SLOT_START + (3 - armour.getIndex());
        }
        return InventoryMenu.SHIELD_SLOT + 1 + gear;
    }
}
