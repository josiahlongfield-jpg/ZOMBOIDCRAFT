package com.zomboidcraft.carry;

/**
 * What a bag holds: its slots, the kilograms it takes before it's full, and how much of its contents' weight
 * it takes off you (a good pack spreads the load). {@code weight} is the empty bag itself.
 */
public record BagSpec(Wear wear, int slots, int columns, double capacity, double reduction, double weight) {
    public static final BagSpec SCHOOL = new BagSpec(Wear.BACK, 8, 4, 7, 0.15, 0.5);
    public static final BagSpec HIKING = new BagSpec(Wear.BACK, 18, 6, 18, 0.35, 1.2);
    public static final BagSpec RUCKSACK = new BagSpec(Wear.BACK, 24, 8, 28, 0.45, 2.2);
    public static final BagSpec DUFFEL = new BagSpec(Wear.HAND, 18, 6, 24, 0.15, 1.0);
    public static final BagSpec PLATE_CARRIER = new BagSpec(Wear.VEST, 6, 6, 5, 0.15, 2.1);
    public static final BagSpec FANNY_PACK = new BagSpec(Wear.WAIST, 3, 3, 2, 0.10, 0.4);

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
}
