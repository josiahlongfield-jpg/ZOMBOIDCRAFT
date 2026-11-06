package com.zomboidcraft.building.base;

import com.zomboidcraft.building.Build;
import com.zomboidcraft.building.BuildBlocks;
import com.zomboidcraft.power.Generators;
import com.zomboidcraft.survival.Sleep;
import com.zomboidcraft.world.blocks.LightSwitch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a room's furniture counts as (rooms plan section 2). Almost all of it is already standing in the town, so a
 * room is fitted out by carrying pieces home with the furniture moving. A piece taller than one block counts once: only
 * its head or lower part is read (a bed's _foot, a locker's _top never count).
 */
public enum Fixture {
    BED("bed"), HOSPITAL_BED("hospital bed"), FOOTLOCKER("footlocker"),
    STOVE("stove"), WOOD_STOVE("wood stove"), COUNTER("counter"), FRIDGE("fridge"), SINK("sink"), SHELF("pantry shelf"),
    MEDICAL("first-aid cabinet"), SURGICAL("surgical table"),
    GUN_BENCH("gun bench"), GUN_RACK("gun rack"), AMMO("ammo can or locker"), PARTS_BIN("parts bin"),
    WORKBENCH("workbench"), TOOL_RACK("tool rack"), SAWHORSE("sawhorse"), ANVIL("anvil"), WELDER("welding bench"),
    LIFT("car lift"), TOOL_CART("tool cart"), FUEL_TANK("fuel tank"), DRUM("fuel drum"), GARAGE_DOOR("garage door"),
    RADIO("radio set"), MAST("antenna mast"), MAP_TABLE("map table"),
    GENERATOR("generator"), SWITCHBOARD("switchboard"), WATER_TANK("water tank"), FILTER("water filter"), RAIN_BARREL("rain barrel"),
    PLANTER("planter"), COMPOST("compost bin"), FENCE("fence"),
    LAMP("lamp"), LAMP_LIT("lit lamp"), LIGHT_SWITCH("light switch"), DOOR("door"), WINDOW("window"), SANDBAGS("sandbags");

    public final String label;

    Fixture(String label) {
        this.label = label;
    }

    /** What this block counts as, or null for nothing. */
    public static @Nullable Fixture of(BlockState s) {
        if (s.isAir()) return null;
        if (s.getBlock() instanceof DoorBlock) return DOOR;
        if (Generators.is(s)) return GENERATOR;
        if (s.is(BuildBlocks.PLANTER)) return PLANTER;
        if (s.is(BuildBlocks.RAIN_BARREL)) return RAIN_BARREL;
        if (s.is(BuildBlocks.SANDBAGS)) return SANDBAGS;
        if (s.getBlock() instanceof LightSwitch.Switch) return LIGHT_SWITCH;
        if (s.getBlock() instanceof FenceBlock || s.getBlock() instanceof FenceGateBlock) return FENCE;
        if (LightSwitch.powered(s)) return LAMP_LIT;
        if (LightSwitch.dark(s)) return LAMP;
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (p.matches(".*_(top|foot)(_.*)?")) return null;
        if (p.startsWith("hs_bed_head") || p.equals("bunk_medbed_head") || p.startsWith("hs_gurney") && !p.contains("bag")) return HOSPITAL_BED;
        if (p.contains("footlocker")) return FOOTLOCKER;
        if (p.equals("home_wood_stove")) return WOOD_STOVE;
        if (p.equals("home_stove") || p.equals("store_oven") || p.equals("fit_oven")) return STOVE;
        if (p.contains("fridge") && !p.contains("blood")) return FRIDGE;
        if (p.contains("sink")) return SINK;
        if (p.contains("_counter") && !p.contains("ticket") && !p.contains("judge") && !p.contains("po_counter")) {
            return p.contains("parts") ? PARTS_BIN : COUNTER;
        }
        if (p.equals("home_medicine") || p.equals("cellar_shelf_medical") || p.equals("bunk_st_medical") || p.equals("bunk_first_aid")
                || p.equals("store_shelf_pharmacy") || p.equals("hs_cart_med") || p.equals("hs_cabinet") || p.equals("hs_cabinet_lo")) {
            return MEDICAL;
        }
        if (p.contains("surgical_table") || p.equals("hs_or_table")) return SURGICAL;
        if (p.contains("gun_bench") || p.equals("pw_vise_bench")) return GUN_BENCH;
        if (p.contains("gun_rack") || p.contains("gun_cabinet")) return GUN_RACK;
        if (p.contains("ammo") || p.equals("bunk_locker") || p.equals("sh_locker") || p.equals("fit_dc_locker")) return AMMO;
        if (p.contains("parts_bin")) return PARTS_BIN;
        if (p.contains("shelf") && !p.contains("book") && !p.contains("fallen")) return SHELF;
        if (p.contains("workbench") || p.equals("pw_bench") || p.equals("pw_bench_on")) return WORKBENCH;
        if (p.contains("tool_chest") || p.startsWith("fs_tools") || p.contains("tool_rack") || p.contains("toolbox") || p.equals("pk_metal_rack")) return TOOL_RACK;
        if (p.contains("sawhorse")) return SAWHORSE;
        if (p.contains("anvil")) return ANVIL;
        if (p.contains("welder")) return WELDER;
        if (p.equals("gs_lift_post") || p.equals("of_car_lift") || p.startsWith("as_column_lift")) return LIFT;
        if (p.contains("tool_cart")) return TOOL_CART;
        if (p.equals("fm_fuel_tank") || p.equals("propane_tank") || p.equals("pw_tank")) return FUEL_TANK;
        if (p.equals("as_drum_oil") || p.equals("pw_drum") || p.equals("bunk_rack_drums") || p.equals("wf_drum") || p.startsWith("pk_barrel")) return DRUM;
        if (p.startsWith("garage_door") || p.contains("roller") && !p.contains("navy")) return GARAGE_DOOR;
        if (p.equals("home_radio") || p.equals("home_radio_low") || p.equals("bunk_radio_desk") || p.contains("ham_radio")) return RADIO;
        if (p.contains("_mast") && !p.contains("vessel") && !p.contains("lt_mast") || p.equals("ext_mast")) return MAST;
        if (p.contains("map_table")) return MAP_TABLE;
        if (p.contains("switchboard")) return SWITCHBOARD;
        if (p.equals("water_tank") || p.equals("bunk_tank") || p.equals("bunk_drum_water") || p.equals("sc_tank")) return WATER_TANK;
        if (p.equals("bunk_filter")) return FILTER;
        if (p.contains("planter")) return PLANTER;
        if (p.contains("compost")) return COMPOST;
        if (Build.isWindow(s)) return WINDOW;
        Sleep.Bed bed = Sleep.bedOf(s);
        if (bed != null && bed != Sleep.Bed.FLOOR && !p.contains("sofa") && !p.contains("couch") && !p.contains("armchair")
                && !p.contains("recliner") && !p.contains("lounger") && !p.contains("bodybag")) {
            return BED;
        }
        return null;
    }
}
