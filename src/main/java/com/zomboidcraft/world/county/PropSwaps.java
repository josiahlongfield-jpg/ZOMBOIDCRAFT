package com.zomboidcraft.world.county;

import java.util.Map;

/**
 * Written by tools/props/props.py: the pack props that stand in for built-in blocks. Each entry is
 * the object's group (its first block, so a fridge and its top pick alike) and then one stand-in
 * per variant; {@link Cover} keeps the built-in block as one more variant.
 */
final class PropSwaps {
    static final Map<String, String[]> TABLE = Map.ofEntries(
        Map.entry("filing_cabinet", new String[] {"filing_cabinet", "pk_file_cabinet"}),
        Map.entry("home_armchair_blue", new String[] {"home_armchair_blue", "pk_armchair", "pk_armchair_green"}),
        Map.entry("home_armchair_brown", new String[] {"home_armchair_brown", "pk_armchair", "pk_armchair_green"}),
        Map.entry("home_armchair_floral", new String[] {"home_armchair_floral", "pk_armchair", "pk_armchair_green"}),
        Map.entry("home_coffee_maker", new String[] {"home_coffee_maker", "pk_coffee"}),
        Map.entry("home_dishwasher_oak", new String[] {"home_dishwasher_oak", "pk_dishwasher"}),
        Map.entry("home_dishwasher_walnut", new String[] {"home_dishwasher_walnut", "pk_dishwasher"}),
        Map.entry("home_dishwasher_white", new String[] {"home_dishwasher_white", "pk_dishwasher"}),
        Map.entry("home_fridge", new String[] {"home_fridge", "pk_fridge"}),
        Map.entry("home_fridge_top", new String[] {"home_fridge", "pk_fridge_010"}),
        Map.entry("home_microwave", new String[] {"home_microwave", "pk_microwave"}),
        Map.entry("home_stove", new String[] {"home_stove", "pk_cooker"}),
        Map.entry("home_toaster", new String[] {"home_toaster", "pk_toaster"}),
        Map.entry("home_tv", new String[] {"home_tv", "pk_tv_crt", "pk_tv_wood"}),
        Map.entry("store_vending", new String[] {"store_vending", "pk_vending"}),
        Map.entry("store_vending_top", new String[] {"store_vending", "pk_vending_010"}));

    private PropSwaps() {
    }
}
