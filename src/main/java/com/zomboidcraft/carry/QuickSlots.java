package com.zomboidcraft.carry;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.GunType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The quick slots on keys 1-8, Zomboid style: what you can reach comes from what you wear. Your hands and the sling
 * on your back are always there; a belt adds two hip slots, a duty or tool belt two pouches as well, and a holster
 * and a sheath one slot each for a handgun and a blade. Each slot only takes what would really go there.
 */
public final class QuickSlots {
    public static final int HANDS = 0;
    public static final int HIP = 1;
    public static final int HIP_2 = 2;
    public static final int HOLSTER = 3;
    public static final int SHEATH = 4;
    public static final int BACK = 5;
    public static final int POUCH = 6;
    public static final int POUCH_2 = 7;
    /** The ninth hotbar slot has no place on you. */
    public static final int SPARE = 8;

    private static final String[] LABELS = {"Hands", "Hip", "Hip", "Holster", "Sheath", "Back", "Pouch", "Pouch", "Spare"};

    /** Melee weapons short enough to hang off a belt loop (handguns go on the hip too). */
    public static final TagKey<Item> HIP_WEAPONS = tag("quick/hip_weapons");
    /** Blades a knife sheath takes. */
    public static final TagKey<Item> KNIVES = tag("quick/knife_sheath");
    /** Blades a machete scabbard takes (knives fit too). */
    public static final TagKey<Item> MACHETES = tag("quick/machete_sheath");

    /** Heaviest thing that hangs off the hip, and the heaviest that fits a pouch. */
    private static final double HIP_KG = 1.6, POUCH_KG = 0.6;

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ZomboidCraft.id(path));
    }

    /** Whether the rules are on for this player: creative, and servers that turned them off, keep the plain hotbar. */
    public static boolean on(Player player) {
        return ZcConfig.get().carry.quickSlots && !player.hasInfiniteMaterials();
    }

    public static String label(int slot) {
        return slot >= 0 && slot < LABELS.length ? LABELS[slot] : "";
    }

    // ---------------------------------------------------------------- what you wear

    public static boolean belt(ItemStack stack) {
        return stack.is(ModItems.LEATHER_BELT) || pouches(stack);
    }

    /** Belts with pouches on them. */
    public static boolean pouches(ItemStack stack) {
        return stack.is(ModItems.TOOL_BELT) || stack.is(ModItems.DUTY_BELT);
    }

    public static boolean holster(ItemStack stack) {
        return stack.is(ModItems.HIP_HOLSTER) || stack.is(ModItems.SHOULDER_HOLSTER) || stack.is(ModItems.THIGH_HOLSTER);
    }

    /** A hip holster hangs off a belt; shoulder and thigh rigs have their own straps. */
    public static boolean needsBelt(ItemStack stack) {
        return stack.is(ModItems.HIP_HOLSTER) || sheath(stack);
    }

    public static boolean sheath(ItemStack stack) {
        return stack.is(ModItems.KNIFE_SHEATH) || stack.is(ModItems.MACHETE_SHEATH);
    }

    public static boolean wrist(ItemStack stack) {
        return stack.is(ModItems.DIGITAL_WATCH);
    }

    /** The gear slot a worn piece goes in, or -1. */
    public static int gearSlot(ItemStack stack) {
        if (belt(stack)) return GearStore.BELT;
        if (holster(stack)) return GearStore.HOLSTER;
        if (sheath(stack)) return GearStore.SHEATH;
        if (wrist(stack)) return GearStore.WRIST;
        return -1;
    }

    /** Whether a worn piece is doing its job (a hip holster or a sheath with no belt to hang from isn't). */
    public static boolean working(Player player, int gearSlot) {
        GearStore gear = Carry.gear(player);
        ItemStack piece = gear.get(gearSlot);
        return !piece.isEmpty() && (!needsBelt(piece) || !gear.get(GearStore.BELT).isEmpty());
    }

    // ---------------------------------------------------------------- the slots

    /** Whether a quick slot is there for this player right now. */
    public static boolean open(Player player, int slot) {
        if (!on(player)) {
            return true;
        }
        GearStore gear = Carry.gear(player);
        return switch (slot) {
            case HANDS, BACK -> true;
            case HIP, HIP_2 -> !gear.get(GearStore.BELT).isEmpty();
            case HOLSTER -> working(player, GearStore.HOLSTER);
            case SHEATH -> working(player, GearStore.SHEATH);
            case POUCH, POUCH_2 -> pouches(gear.get(GearStore.BELT));
            default -> false;
        };
    }

    /** Whether this stack could sit in that quick slot, going by the slot alone (see {@link #fits}). */
    public static boolean takes(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty() || !on(player)) {
            return true;
        }
        if (BagItem.spec(stack) != null && slot != HANDS) {
            return false;   // bags are worn or carried in the hands, never hung on a belt
        }
        return switch (slot) {
            case HANDS -> true;
            case HIP, HIP_2 -> handgun(stack) || stack.is(HIP_WEAPONS) || !Carry.weapon(stack) && Weights.unit(stack) <= HIP_KG;
            case HOLSTER -> handgun(stack);
            case SHEATH -> {
                ItemStack sheath = Carry.gear(player).get(GearStore.SHEATH);
                yield stack.is(KNIVES) || sheath.is(ModItems.MACHETE_SHEATH) && stack.is(MACHETES);
            }
            case BACK -> Carry.slings(stack);
            case POUCH, POUCH_2 -> !Carry.weapon(stack) && Weights.unit(stack) <= POUCH_KG;
            default -> false;
        };
    }

    /** Whether this stack can go in that quick slot right now: the slot is open and takes it. */
    public static boolean fits(Player player, int slot, ItemStack stack) {
        return open(player, slot) && takes(player, slot, stack);
    }

    public static boolean handgun(ItemStack stack) {
        GunType type = GunType.of(stack);
        return Carry.weapon(stack) && (type == GunType.PISTOL || type == GunType.REVOLVER || type == GunType.MAGNUM);
    }

    /**
     * Where something goes when it comes to you, best place first: a handgun to the holster, a blade to its sheath, a
     * long gun on the sling, small things to a pouch, then the hip, and your hands last. Weapons only.
     */
    public static int[] order(ItemStack stack) {
        if (handgun(stack)) return new int[]{HOLSTER, HIP, HIP_2, HANDS};
        if (stack.is(KNIVES) || stack.is(MACHETES)) return new int[]{SHEATH, HIP, HIP_2, BACK, HANDS};
        if (stack.is(HIP_WEAPONS)) return new int[]{HIP, HIP_2, BACK, HANDS};
        if (Carry.weapon(stack)) return new int[]{BACK, HANDS};
        return new int[]{POUCH, POUCH_2, HIP, HIP_2};
    }

    /** The first empty quick slot that fits, in {@link #order}, or -1. */
    public static int place(Player player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int slot : order(stack)) {
            if (inventory.getItem(slot).isEmpty() && fits(player, slot, stack)) {
                return slot;
            }
        }
        return -1;
    }

    private QuickSlots() {
    }
}
