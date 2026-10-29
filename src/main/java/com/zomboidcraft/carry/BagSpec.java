package com.zomboidcraft.carry;

import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.MagazineItem;
import com.zomboidcraft.combat.RifleItem;
import net.minecraft.world.item.ItemStack;

/**
 * What a bag holds: its slots, the kilograms it takes before it's full, and how much of its contents' weight
 * it takes off you (a good pack spreads the load). {@code weight} is the empty bag itself; {@code holds} is what
 * goes in it (a gun case takes only gun gear, an ammo can only rounds and magazines).
 */
public record BagSpec(Wear wear, int slots, int columns, double capacity, double reduction, double weight, Holds holds) {
    public static final BagSpec SCHOOL = new BagSpec(Wear.BACK, 8, 4, 7, 0.15, 0.5);
    public static final BagSpec HIKING = new BagSpec(Wear.BACK, 18, 6, 18, 0.35, 1.2);
    public static final BagSpec RUCKSACK = new BagSpec(Wear.BACK, 24, 8, 28, 0.45, 2.2);
    public static final BagSpec DUFFEL = new BagSpec(Wear.HAND, 18, 6, 24, 0.15, 1.0);
    public static final BagSpec PLATE_CARRIER = new BagSpec(Wear.VEST, 6, 6, 5, 0.15, 2.1);
    public static final BagSpec FANNY_PACK = new BagSpec(Wear.WAIST, 3, 3, 2, 0.10, 0.4);
    // carried in the off hand: a long gun's hard case, a handgun's, and a .50 cal ammo can
    public static final BagSpec RIFLE_CASE = new BagSpec(Wear.HAND, 4, 4, 9, 0, 3.2, Holds.GUN_GEAR);
    public static final BagSpec PISTOL_CASE = new BagSpec(Wear.HAND, 3, 3, 3, 0, 1.2, Holds.GUN_GEAR);
    public static final BagSpec AMMO_CAN = new BagSpec(Wear.HAND, 4, 4, 14, 0, 2.3, Holds.AMMO);
    // everyday bags people had on them (refs/items/bags): mostly carried, the mail bag over the shoulder
    public static final BagSpec PURSE = new BagSpec(Wear.HAND, 4, 4, 3, 0, 0.4);
    public static final BagSpec BRIEFCASE = new BagSpec(Wear.HAND, 6, 3, 5, 0, 1.6);
    public static final BagSpec GYM_BAG = new BagSpec(Wear.HAND, 12, 6, 14, 0.1, 0.7);
    public static final BagSpec DOCTORS_BAG = new BagSpec(Wear.HAND, 8, 4, 8, 0.05, 1.3);
    public static final BagSpec PLASTIC_BAG = new BagSpec(Wear.HAND, 4, 4, 4, 0, 0.02);
    public static final BagSpec PAPER_BAG = new BagSpec(Wear.HAND, 4, 4, 5, 0, 0.05);
    public static final BagSpec SUITCASE = new BagSpec(Wear.HAND, 16, 8, 20, 0, 3.0);
    public static final BagSpec LUNCHBOX = new BagSpec(Wear.HAND, 2, 2, 1.5, 0, 0.5);
    public static final BagSpec COOLER = new BagSpec(Wear.HAND, 8, 4, 10, 0, 2.2);
    public static final BagSpec MAIL_BAG = new BagSpec(Wear.BACK, 12, 6, 12, 0.1, 0.9);
    public static final BagSpec TOOL_BAG = new BagSpec(Wear.HAND, 10, 5, 14, 0, 1.4);
    public static final BagSpec AMMO_POUCH_BELT = new BagSpec(Wear.WAIST, 3, 3, 3, 0, 0.5, Holds.AMMO);

    public BagSpec(Wear wear, int slots, int columns, double capacity, double reduction, double weight) {
        this(wear, slots, columns, capacity, reduction, weight, Holds.ANYTHING);
    }

    /** The most slots any bag worn this way can have; the menu always carries this many. */
    public static int maxSlots(Wear wear) {
        return switch (wear) {
            case BACK -> 24;
            case VEST -> 6;
            case WAIST -> 3;
            // anything held in the off hand opens there, so it fits the biggest pack
            case HAND -> 24;
        };
    }

    /** What a bag will take. */
    public enum Holds {
        ANYTHING, GUN_GEAR, AMMO;

        public boolean test(ItemStack stack) {
            return switch (this) {
                case ANYTHING -> true;
                case GUN_GEAR -> stack.getItem() instanceof RifleItem || stack.getItem() instanceof MagazineItem
                        || stack.getItem() instanceof AttachmentItem || isAmmo(stack);
                case AMMO -> stack.getItem() instanceof MagazineItem || isAmmo(stack);
            };
        }

        private static boolean isAmmo(ItemStack stack) {
            for (GunType type : GunType.values()) {
                if (stack.is(type.ammo())) {
                    return true;
                }
            }
            return false;
        }
    }
}
