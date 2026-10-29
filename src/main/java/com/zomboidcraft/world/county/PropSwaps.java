package com.zomboidcraft.world.county;

import java.util.Map;

/**
 * Written by tools/props/props.py: the pack props that stand in for built-in blocks. Each entry is
 * the object's group (its first block, so a fridge and its top pick alike) and then one stand-in
 * per variant; {@link Cover} keeps the built-in block as one more variant, unless the group is
 * marked "!" (always swapped).
 */
final class PropSwaps {
    static final Map<String, String[]> TABLE = Map.ofEntries(
        Map.entry("apple_crate", new String[] {"apple_crate", "pk_crate_1", "pk_crate_2"}),
        Map.entry("barrel", new String[] {"barrel", "pk_barrel_1", "pk_barrel_2", "pk_barrel_3"}),
        Map.entry("bunk_crate_ammo", new String[] {"bunk_crate_ammo", "pk_military_crate_old", "pk_military_crate_old_b"}),
        Map.entry("bunk_crate_gun", new String[] {"bunk_crate_gun", "pk_military_crate"}),
        Map.entry("crate", new String[] {"!crate", "pk_crate_1", "pk_crate_2"}),
        Map.entry("desk", new String[] {"desk", "pk_desk_metal"}),
        Map.entry("ext_trash_can", new String[] {"ext_trash_can", "pk_trash_can"}),
        Map.entry("filing_cabinet", new String[] {"filing_cabinet", "pk_file_cabinet"}),
        Map.entry("home_armchair_blue", new String[] {"home_armchair_blue", "pk_armchair", "pk_armchair_green"}),
        Map.entry("home_armchair_brown", new String[] {"home_armchair_brown", "pk_armchair", "pk_armchair_green"}),
        Map.entry("home_armchair_floral", new String[] {"home_armchair_floral", "pk_armchair", "pk_armchair_green"}),
        Map.entry("home_bookcase", new String[] {"home_bookcase", "pk_bookshelf_worn"}),
        Map.entry("home_bookcase_top", new String[] {"home_bookcase", "pk_bookshelf_worn_010"}),
        Map.entry("home_chair", new String[] {"home_chair", "pk_chair_dining", "pk_chair_wooden", "pk_chair_painted", "pk_chair_painted_2"}),
        Map.entry("home_coffee_maker", new String[] {"home_coffee_maker", "pk_coffee"}),
        Map.entry("home_coffee_table", new String[] {"home_coffee_table", "pk_coffee_table", "pk_coffee_table_round"}),
        Map.entry("home_desk", new String[] {"home_desk", "pk_desk_metal"}),
        Map.entry("home_dishwasher_oak", new String[] {"home_dishwasher_oak", "pk_dishwasher"}),
        Map.entry("home_dishwasher_walnut", new String[] {"home_dishwasher_walnut", "pk_dishwasher"}),
        Map.entry("home_dishwasher_white", new String[] {"home_dishwasher_white", "pk_dishwasher"}),
        Map.entry("home_drawers_oak", new String[] {"home_drawers_oak", "pk_cabinet_oak"}),
        Map.entry("home_drawers_walnut", new String[] {"home_drawers_walnut", "pk_cabinet_oak"}),
        Map.entry("home_fridge", new String[] {"home_fridge", "pk_fridge"}),
        Map.entry("home_fridge_top", new String[] {"home_fridge", "pk_fridge_010"}),
        Map.entry("home_garage_shelf", new String[] {"home_garage_shelf", "pk_garage_shelves", "pk_garage_shelves_2", "pk_metal_rack"}),
        Map.entry("home_microwave", new String[] {"home_microwave", "pk_microwave"}),
        Map.entry("home_nightstand", new String[] {"home_nightstand", "pk_nightstand", "pk_nightstand_painted"}),
        Map.entry("home_plant", new String[] {"home_plant", "pk_houseplant"}),
        Map.entry("home_radio", new String[] {"home_radio", "pk_boombox", "pk_cassette_player"}),
        Map.entry("home_stove", new String[] {"home_stove", "pk_cooker"}),
        Map.entry("home_table", new String[] {"home_table", "pk_table_wooden", "pk_table_wooden_2", "pk_table_painted"}),
        Map.entry("home_toaster", new String[] {"home_toaster", "pk_toaster"}),
        Map.entry("home_tool_chest", new String[] {"home_tool_chest", "pk_tool_chest"}),
        Map.entry("home_trash_can", new String[] {"home_trash_can", "pk_trash_can_home"}),
        Map.entry("home_tv", new String[] {"home_tv", "pk_tv_crt", "pk_tv_wood"}),
        Map.entry("hvac", new String[] {"hvac", "pk_aircon", "pk_aircon_clean"}),
        Map.entry("navy_toolbox", new String[] {"navy_toolbox", "pk_toolbox"}),
        Map.entry("planter", new String[] {"planter", "pk_planter"}),
        Map.entry("store_chair", new String[] {"store_chair", "pk_monobloc"}),
        Map.entry("store_counter", new String[] {"!store_counter", "pk_counter_1", "pk_counter_2", "pk_counter_3"}),
        Map.entry("store_register", new String[] {"!store_register", "pk_till_1", "pk_till_2"}),
        Map.entry("store_shelf_empty", new String[] {"!store_shelf_empty", "pk_shelf_empty_1", "pk_shelf_empty_2"}),
        Map.entry("store_shelf_empty_top", new String[] {"!store_shelf_empty", "pk_shelf_empty_1_010", "pk_shelf_empty_2_010"}),
        Map.entry("store_shelf_grocery", new String[] {"!store_shelf_grocery", "pk_shelf_grocery_1", "pk_shelf_grocery_2", "pk_shelf_grocery_3", "pk_shelf_grocery_4", "pk_shelf_grocery_5"}),
        Map.entry("store_shelf_grocery_top", new String[] {"!store_shelf_grocery", "pk_shelf_grocery_1_010", "pk_shelf_grocery_2_010", "pk_shelf_grocery_3_010", "pk_shelf_grocery_4_010", "pk_shelf_grocery_5_010"}),
        Map.entry("store_shelf_liquor", new String[] {"!store_shelf_liquor", "pk_shelf_liquor_1", "pk_shelf_liquor_2", "pk_shelf_liquor_3"}),
        Map.entry("store_shelf_liquor_top", new String[] {"!store_shelf_liquor", "pk_shelf_liquor_1_010", "pk_shelf_liquor_2_010", "pk_shelf_liquor_3_010"}),
        Map.entry("store_shelf_pharmacy", new String[] {"!store_shelf_pharmacy", "pk_shelf_pharmacy_1", "pk_shelf_pharmacy_2", "pk_shelf_pharmacy_3"}),
        Map.entry("store_shelf_pharmacy_top", new String[] {"!store_shelf_pharmacy", "pk_shelf_pharmacy_1_010", "pk_shelf_pharmacy_2_010", "pk_shelf_pharmacy_3_010"}),
        Map.entry("store_stool", new String[] {"store_stool", "pk_stool_1", "pk_stool_2", "pk_bar_chair"}),
        Map.entry("store_vending", new String[] {"store_vending", "pk_vending"}),
        Map.entry("store_vending_top", new String[] {"store_vending", "pk_vending_010"}),
        Map.entry("trade_barber_chair", new String[] {"trade_barber_chair", "pk_barber_chair"}),
        Map.entry("trash_bag", new String[] {"!trash_bag", "pk_trash_bag"}));

    private PropSwaps() {
    }
}
