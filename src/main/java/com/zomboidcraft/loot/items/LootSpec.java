package com.zomboidcraft.loot.items;

import com.zomboidcraft.combat.rolls.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * One item of the world loot roster (tools/items/loot/roster.py): its Supplies tab, how rare it is, what it's worth in
 * barter (1 = a can of beans), what one weighs, how many stack, whether this mod registers it as a loot item (items
 * that already existed keep their own class but share the numbers), and for food and drink what having it does.
 */
public record LootSpec(String id, String tab, Rarity rarity, int value, double kg, int stack, boolean loot,
                       @Nullable Food food) {

    /**
     * What eating or drinking it does. fresh/rot: days until stale and rotten (0 = keeps); for COLD food those are
     * days in a fridge. left: the item it leaves (an empty can or bottle). alcohol: how strong a drink it is.
     */
    public record Food(int hunger, float saturation, float thirst, float fresh, float rot, int flags,
                       @Nullable String left, int alcohol) {
        public static final int DRINK = 1, COLD = 2, RAW = 4, ALWAYS = 8, CAN = 16;

        public boolean is(int flag) {
            return (flags & flag) != 0;
        }

        public boolean perishes() {
            return rot > 0;
        }
    }
}
