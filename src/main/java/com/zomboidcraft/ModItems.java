package com.zomboidcraft;

import com.zomboidcraft.barricade.CrowbarItem;
import com.zomboidcraft.barricade.HammerItem;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.CaseItem;
import com.zomboidcraft.combat.GunFinish;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunSlotData;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.MagazineItem;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.Weapons;
import com.zomboidcraft.combat.throwing.ThrowKind;
import com.zomboidcraft.combat.throwing.ThrowableItem;
import com.zomboidcraft.survival.BandageItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;

public final class ModItems {
    private static final List<Item> ALL = new ArrayList<>();

    public static final Item BANDAGE = register("bandage", BandageItem::new, new Item.Properties().stacksTo(16));
    public static final Item SPLINT = register("splint", com.zomboidcraft.survival.SplintItem::new, new Item.Properties().stacksTo(4));
    public static final Item NAILS = register("nails", Item::new, new Item.Properties());
    // Repair supplies for the weapon bench (see combat/Repairs): tape and glue mend melee weapons, oil and parts guns.
    public static final Item DUCT_TAPE = register("duct_tape", Item::new, new Item.Properties().stacksTo(8));
    public static final Item WOOD_GLUE = register("wood_glue", Item::new, new Item.Properties().stacksTo(8));
    public static final Item GUN_OIL = register("gun_oil", Item::new, new Item.Properties().stacksTo(8));
    public static final Item GUN_PARTS = register("gun_parts", Item::new, new Item.Properties().stacksTo(16));
    public static final Item STREET_MAP = register("street_map",
            p -> new com.zomboidcraft.world.map.PaperMapItem(com.zomboidcraft.world.map.PaperMaps.Kind.STREET, p),
            new Item.Properties().stacksTo(1));
    public static final Item ROAD_ATLAS = register("road_atlas",
            p -> new com.zomboidcraft.world.map.PaperMapItem(com.zomboidcraft.world.map.PaperMaps.Kind.ATLAS, p),
            new Item.Properties().stacksTo(1));
    /** A map with a red ring round a hidden bunker (see world/map/BunkerMapItem); rare. */
    public static final Item BUNKER_MAP = register("bunker_map", com.zomboidcraft.world.map.BunkerMapItem::new,
            new Item.Properties().stacksTo(1));
    /** A cheap digital wristwatch: while you have one on you, the HUD shows the time and the day. */
    // What a wall leaves when it's knocked through (building/DigRules), and the torch that cuts steel.
    public static final Item RUBBLE = register("rubble_chunks", Item::new, new Item.Properties().stacksTo(32));
    // What B42's primitive weapons are made from (combat/SpearItem and the recipes): branches off the trees,
    // stones out of rubble and gravel, and a stone knapped sharp against another.
    public static final Item TREE_BRANCH = register("tree_branch", Item::new, new Item.Properties().stacksTo(8));
    public static final Item STONE = register("stone", Item::new, new Item.Properties().stacksTo(16));
    // Handloading (combat/Reloading): spent brass picked up after a fight, and what a bench turns it back into rounds with.
    public static final Item SPENT_9MM = register("spent_9mm", Item::new, new Item.Properties());
    public static final Item SPENT_380 = register("spent_380", Item::new, new Item.Properties());
    public static final Item SPENT_38 = register("spent_38", Item::new, new Item.Properties());
    public static final Item SPENT_44 = register("spent_44", Item::new, new Item.Properties());
    public static final Item SPENT_556 = register("spent_556", Item::new, new Item.Properties());
    public static final Item SPENT_308 = register("spent_308", Item::new, new Item.Properties());
    public static final Item SPENT_3030 = register("spent_3030", Item::new, new Item.Properties());
    public static final Item SPENT_50 = register("spent_50", Item::new, new Item.Properties().stacksTo(32));
    public static final Item SPENT_HULLS = register("spent_hulls", Item::new, new Item.Properties());
    /** A jug of powder: its durability is the charges left in it (one a round, three for a .50). */
    public static final Item SMOKELESS_POWDER = register("smokeless_powder", Item::new, new Item.Properties().durability(100));
    public static final Item PRIMERS = register("primers", Item::new, new Item.Properties().stacksTo(100));
    public static final Item BULLETS_9MM = register("bullets_9mm", Item::new, new Item.Properties().stacksTo(100));
    public static final Item BULLETS_38 = register("bullets_38", Item::new, new Item.Properties().stacksTo(100));
    public static final Item BULLETS_44 = register("bullets_44", Item::new, new Item.Properties().stacksTo(100));
    public static final Item BULLETS_556 = register("bullets_556", Item::new, new Item.Properties().stacksTo(100));
    public static final Item BULLETS_30 = register("bullets_30", Item::new, new Item.Properties().stacksTo(100));
    public static final Item BULLETS_50 = register("bullets_50", Item::new, new Item.Properties().stacksTo(50));
    /** A bag of lead shot: its durability is the shells' worth left in it. */
    public static final Item LEAD_SHOT = register("lead_shot", Item::new, new Item.Properties().durability(40));
    public static final Item GUNSMITH_MAGAZINE = register("gunsmith_magazine",
            p -> new com.zomboidcraft.loot.items.SkillBookItem(p, com.zomboidcraft.skills.Skill.RELOADING),
            new Item.Properties().stacksTo(1));
    public static final Item RELOADING_BENCH = register("reloading_bench",
            p -> new net.minecraft.world.item.BlockItem(com.zomboidcraft.combat.ReloadingBenchBlock.BENCH, p.useBlockDescriptionPrefix()),
            new Item.Properties().stacksTo(1));
    public static final Item SHARP_STONE = register("sharp_stone", Item::new, new Item.Properties().stacksTo(16));
    public static final Item SCRAP_METAL = register("scrap_metal", Item::new, new Item.Properties().stacksTo(32));
    public static final Item PROPANE_TORCH = register("propane_torch", Item::new, new Item.Properties().durability(120));
    // What the Build page builds with (building/Build).
    public static final Item METAL_SHEET = register("metal_sheet", Item::new, new Item.Properties().stacksTo(8));
    public static final Item TIN_CAN = register("tin_can", p -> new ThrowableItem(ThrowKind.CAN, p), new Item.Properties().stacksTo(16));
    public static final Item PLASTIC_DRUM = register("plastic_drum", Item::new, new Item.Properties().stacksTo(1));
    public static final Item TARP = register("tarp", Item::new, new Item.Properties().stacksTo(4));
    public static final Item EMPTY_SANDBAG = register("empty_sandbag", Item::new, new Item.Properties().stacksTo(16));
    /** A wind-up alarm clock: right-click to set how long until it rings, then set it down from the Build page. */
    public static final Item ALARM_CLOCK = register("alarm_clock", com.zomboidcraft.building.AlarmClockItem::new, new Item.Properties().stacksTo(1));
    // Seed packets for a planter box (building/BuildBlocks.Planter).
    public static final Item CARROT_SEEDS = register("carrot_seeds", Item::new, new Item.Properties().stacksTo(16));
    public static final Item SEED_POTATOES = register("seed_potatoes", Item::new, new Item.Properties().stacksTo(16));
    public static final Item BEET_SEEDS = register("beet_seeds", Item::new, new Item.Properties().stacksTo(16));
    public static final Item DIGITAL_WATCH = register("digital_watch", Item::new, new Item.Properties().stacksTo(1));
    // Worn kit that opens quick slots (carry/QuickSlots): belts the hip and pouches, holsters and sheaths their own slot.
    public static final Item LEATHER_BELT = register("leather_belt", Item::new, new Item.Properties().stacksTo(1));
    public static final Item TOOL_BELT = register("tool_belt", Item::new, new Item.Properties().stacksTo(1));
    public static final Item DUTY_BELT = register("duty_belt", Item::new, new Item.Properties().stacksTo(1));
    public static final Item HIP_HOLSTER = register("hip_holster", Item::new, new Item.Properties().stacksTo(1));
    public static final Item SHOULDER_HOLSTER = register("shoulder_holster", Item::new, new Item.Properties().stacksTo(1));
    public static final Item THIGH_HOLSTER = register("thigh_holster", Item::new, new Item.Properties().stacksTo(1));
    public static final Item KNIFE_SHEATH = register("knife_sheath", Item::new, new Item.Properties().stacksTo(1));
    public static final Item MACHETE_SHEATH = register("machete_sheath", Item::new, new Item.Properties().stacksTo(1));
    // Earning an aircraft (world/cars/AirReadiness): the manuals that teach flying, and what makes one airworthy.
    public static final Item ROTARY_WING_MANUAL = manual(com.zomboidcraft.world.cars.AirReadiness.Manual.ROTARY);
    public static final Item JET_FLIGHT_MANUAL = manual(com.zomboidcraft.world.cars.AirReadiness.Manual.JET);
    public static final Item AIRCRAFT_WEAPONS_MANUAL = manual(com.zomboidcraft.world.cars.AirReadiness.Manual.WEAPONS);
    public static final Item JET_FUEL = register("jet_fuel_drum", Item::new, new Item.Properties().stacksTo(1));
    public static final Item AIRCRAFT_BATTERY = register("aircraft_battery", Item::new, new Item.Properties().stacksTo(1));
    public static final Item AIRCRAFT_PARTS = register("aircraft_parts", Item::new, new Item.Properties().stacksTo(8));
    /** One car's key (world/cars/CarKeys): it starts that car and no other. */
    public static final Item CAR_KEY = register("car_key", com.zomboidcraft.world.cars.CarKeyItem::new, new Item.Properties().stacksTo(1));
    // Melee weapons: stats in combat/WeaponSpecs (generated by tools/weapons/weapons.py).
    public static final Item HAMMER = weapon("hammer", HammerItem::new);
    public static final Item KITCHEN_KNIFE = weapon("kitchen_knife", MeleeWeaponItem::new);
    public static final Item FRYING_PAN = weapon("frying_pan", MeleeWeaponItem::new);
    public static final Item CROWBAR = weapon("crowbar", CrowbarItem::new);
    public static final Item PIPE_WRENCH = weapon("pipe_wrench", MeleeWeaponItem::new);
    public static final Item LEAD_PIPE = weapon("lead_pipe", MeleeWeaponItem::new);
    public static final Item GOLF_CLUB = weapon("golf_club", MeleeWeaponItem::new);
    public static final Item HOCKEY_STICK = weapon("hockey_stick", MeleeWeaponItem::new);
    public static final Item BASEBALL_BAT = weapon("baseball_bat", MeleeWeaponItem::new);
    public static final Item SPIKED_BAT = weapon("spiked_bat", MeleeWeaponItem::new);
    public static final Item SHOVEL = weapon("shovel", MeleeWeaponItem::new);
    public static final Item MACHETE = weapon("machete", MeleeWeaponItem::new);
    public static final Item TOMAHAWK = weapon("tomahawk", MeleeWeaponItem::new);
    public static final Item KATANA = weapon("katana", MeleeWeaponItem::new);
    public static final Item FIRE_AXE = weapon("fire_axe", MeleeWeaponItem::new);
    public static final Item SLEDGEHAMMER = weapon("sledgehammer", MeleeWeaponItem::new);
    // B42's primitive weapons (Josiah 2026-10-31)
    public static final Item WOODEN_SPEAR = weapon("wooden_spear", com.zomboidcraft.combat.SpearItem::new);
    public static final Item FIRE_HARDENED_SPEAR = weapon("fire_hardened_spear", com.zomboidcraft.combat.SpearItem::new);
    public static final Item SPEAR_KNIFE = weapon("spear_knife", com.zomboidcraft.combat.SpearItem::new);
    public static final Item STONE_AXE = weapon("stone_axe", MeleeWeaponItem::new);
    public static final Item STONE_HAMMER = weapon("stone_hammer", MeleeWeaponItem::new);
    public static final Item STONE_KNIFE = weapon("stone_knife", MeleeWeaponItem::new);
    public static final Item PLANK_NAILS = weapon("plank_nails", MeleeWeaponItem::new);
    public static final Item CUDGEL_NAILS = weapon("cudgel_nails", MeleeWeaponItem::new);

    // The guns (see combat/GunType) and what feeds them.
    public static final Item ASSAULT_RIFLE = gun("assault_rifle", GunType.ASSAULT_RIFLE, 1600);
    public static final Item RIFLE_MAGAZINE = magazine("rifle_magazine", GunType.ASSAULT_RIFLE);
    public static final Item RIFLE_AMMO = register("rifle_ammo", Item::new, new Item.Properties().stacksTo(60));
    public static final Item PISTOL = gun("pistol", GunType.PISTOL, 1200);
    public static final Item PISTOL_MAGAZINE = magazine("pistol_magazine", GunType.PISTOL);
    public static final Item PISTOL_AMMO = register("pistol_ammo", Item::new, new Item.Properties().stacksTo(60));
    public static final Item SHOTGUN = gun("shotgun", GunType.SHOTGUN, 1400);
    public static final Item SHOTGUN_SHELL = register("shotgun_shell", Item::new, new Item.Properties().stacksTo(50));
    public static final Item HUNTING_RIFLE = gun("hunting_rifle", GunType.HUNTING_RIFLE, 1800);
    public static final Item HUNTING_RIFLE_MAGAZINE = magazine("hunting_rifle_magazine", GunType.HUNTING_RIFLE);
    public static final Item HUNTING_RIFLE_AMMO = register("hunting_rifle_ammo", Item::new, new Item.Properties().stacksTo(40));
    public static final Item DOUBLE_BARREL = gun("double_barrel", GunType.DOUBLE_BARREL, 1500);
    public static final Item REVOLVER = gun("revolver", GunType.REVOLVER, 1400);
    public static final Item REVOLVER_AMMO = register("revolver_ammo", Item::new, new Item.Properties().stacksTo(60));
    public static final Item RIMFIRE = gun("rimfire", GunType.RIMFIRE, 1500);
    public static final Item RIMFIRE_MAGAZINE = magazine("rimfire_magazine", GunType.RIMFIRE);
    public static final Item RIMFIRE_AMMO = register("rimfire_ammo", Item::new, new Item.Properties().stacksTo(100));
    public static final Item SMG = gun("smg", GunType.SMG, 1500);
    public static final Item SMG_MAGAZINE = magazine("smg_magazine", GunType.SMG);
    public static final Item SAWN_OFF = gun("sawn_off", GunType.SAWN_OFF, 1100);
    public static final Item MAGNUM = gun("magnum", GunType.MAGNUM, 1600);
    public static final Item MAGNUM_AMMO = register("magnum_ammo", Item::new, new Item.Properties().stacksTo(50));
    public static final Item LEVER_ACTION = gun("lever_action", GunType.LEVER_ACTION, 1700);
    public static final Item LEVER_AMMO = register("lever_ammo", Item::new, new Item.Properties().stacksTo(50));
    public static final Item CROSSBOW = gun("crossbow", GunType.CROSSBOW, 1400);
    public static final Item CROSSBOW_BOLT = register("crossbow_bolt", Item::new, new Item.Properties().stacksTo(12));
    // the .308 battle rifle (Josiah 10-29: "slower firing but hard hitting"), fed the hunting rifle's rounds
    public static final Item BATTLE_RIFLE = gun("battle_rifle", GunType.BATTLE_RIFLE, 1700);
    public static final Item BATTLE_RIFLE_MAGAZINE = magazine("battle_rifle_magazine", GunType.BATTLE_RIFLE);
    // the rest of the bought guns (Josiah 2026-10-29: "i want all of the pbr weapons in the game")
    public static final Item POCKET_PISTOL = gun("pocket_pistol", GunType.POCKET_PISTOL, 1000);
    public static final Item POCKET_PISTOL_MAGAZINE = magazine("pocket_pistol_magazine", GunType.POCKET_PISTOL);
    public static final Item POCKET_AMMO = register("pocket_ammo", Item::new, new Item.Properties().stacksTo(60));
    public static final Item MACHINE_PISTOL = gun("machine_pistol", GunType.MACHINE_PISTOL, 1200);
    public static final Item MACHINE_PISTOL_MAGAZINE = magazine("machine_pistol_magazine", GunType.MACHINE_PISTOL);
    public static final Item SUPPRESSED_RIFLE = gun("suppressed_rifle", GunType.SUPPRESSED_RIFLE, 1600);
    public static final Item PRECISION_RIFLE = gun("precision_rifle", GunType.PRECISION_RIFLE, 1800);
    public static final Item PRECISION_RIFLE_MAGAZINE = magazine("precision_rifle_magazine", GunType.PRECISION_RIFLE);
    public static final Item HEAVY_SNIPER = gun("heavy_sniper", GunType.HEAVY_SNIPER, 2000);
    public static final Item HEAVY_SNIPER_MAGAZINE = magazine("heavy_sniper_magazine", GunType.HEAVY_SNIPER);
    public static final Item HEAVY_AMMO = register("heavy_ammo", Item::new, new Item.Properties().stacksTo(20));
    public static final Item GRENADE_LAUNCHER = gun("grenade_launcher", GunType.GRENADE_LAUNCHER, 900);
    public static final Item GRENADE_ROUND = register("grenade_round", Item::new, new Item.Properties().stacksTo(12));

    // Things to throw (combat/throwing): lures, fire and smoke, and blasts. The tin can and the alarm clock are
    // thrown too (see TIN_CAN and ALARM_CLOCK).
    public static final Item GLASS_BOTTLE = throwable(ThrowKind.BOTTLE, 8);
    public static final Item BRICK = throwable(ThrowKind.BRICK, 8);
    public static final Item FIRECRACKERS = throwable(ThrowKind.FIRECRACKERS, 8);
    public static final Item ROAD_FLARE = throwable(ThrowKind.FLARE, 8);
    public static final Item MOLOTOV = throwable(ThrowKind.MOLOTOV, 4);
    public static final Item SMOKE_GRENADE = throwable(ThrowKind.SMOKE, 4);
    public static final Item FLASHBANG = throwable(ThrowKind.FLASHBANG, 4);
    public static final Item FRAG_GRENADE = throwable(ThrowKind.FRAG, 4);

    private static Item throwable(ThrowKind kind, int stack) {
        return register(kind.id, p -> new ThrowableItem(kind, p), new Item.Properties().stacksTo(stack));
    }

    private static Item gun(String name, GunType type, int durability) {
        return register(name, p -> new RifleItem(type, p),
                new Item.Properties().stacksTo(1).durability(durability).component(GunState.TYPE, GunState.EMPTY_MAG)
                        // aiming slows you to a careful walk rather than vanilla's crawl
                        .component(DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.55f)));
    }

    private static Item manual(com.zomboidcraft.world.cars.AirReadiness.Manual manual) {
        return register(manual.id, p -> new com.zomboidcraft.world.cars.AirReadiness.ManualItem(manual, p), new Item.Properties().stacksTo(1));
    }

    private static Item magazine(String name, GunType type) {
        return register(name, p -> new MagazineItem(type, p), new Item.Properties().stacksTo(1)
                .component(GunState.MAG_ROUNDS, 0).component(DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.6f)));
    }

    // Gun parts: each names its slot; the key the gun's item model switches on comes from GunSlotData.
    public static final Item RED_DOT_SIGHT = part("red_dot_sight", Attachments.Slot.OPTIC);
    public static final Item HOLO_SIGHT = part("holo_sight", Attachments.Slot.OPTIC);
    public static final Item SCOPE_4X = part("scope_4x", Attachments.Slot.OPTIC);
    public static final Item RIFLE_SUPPRESSOR = part("rifle_suppressor", Attachments.Slot.MUZZLE);
    public static final Item PISTOL_SUPPRESSOR = part("pistol_suppressor", Attachments.Slot.MUZZLE);
    public static final Item FOREGRIP = part("foregrip", Attachments.Slot.UNDERBARREL);
    public static final Item UNDERBARREL_LAUNCHER = part("underbarrel_launcher", Attachments.Slot.UNDERBARREL);
    public static final Item WEAPON_LIGHT = part("weapon_light", Attachments.Slot.SIDE_RAIL);
    public static final Item LASER_SIGHT = part("laser_sight", Attachments.Slot.RIGHT_RAIL);
    public static final Item RIFLE_STOCK = part("rifle_stock", Attachments.Slot.STOCK);
    public static final Item MINI_RED_DOT = part("mini_red_dot", Attachments.Slot.OPTIC);
    public static final Item COMPACT_RED_DOT = part("compact_red_dot", Attachments.Slot.OPTIC);
    public static final Item COMBAT_SCOPE = part("combat_scope", Attachments.Slot.OPTIC);
    public static final Item RIFLE_SCOPE = part("rifle_scope", Attachments.Slot.OPTIC);
    public static final Item PISTOL_SCOPE = part("pistol_scope", Attachments.Slot.OPTIC);
    public static final Item PRECISION_SCOPE = part("precision_scope", Attachments.Slot.OPTIC);
    public static final Item HUNTING_SCOPE = part("hunting_scope", Attachments.Slot.OPTIC);
    public static final Item LONG_RANGE_SCOPE = part("long_range_scope", Attachments.Slot.OPTIC);
    public static final Item CROSSBOW_SCOPE = part("crossbow_scope", Attachments.Slot.OPTIC);
    public static final Item MAGNIFIER = part("magnifier", Attachments.Slot.MAGNIFIER);
    public static final Item ANGLED_GRIP = part("angled_grip", Attachments.Slot.UNDERBARREL);
    public static final Item STUBBY_GRIP = part("stubby_grip", Attachments.Slot.UNDERBARREL);
    public static final Item SKELETON_GRIP = part("skeleton_grip", Attachments.Slot.UNDERBARREL);
    public static final Item TACTICAL_GRIP = part("tactical_grip", Attachments.Slot.UNDERBARREL);
    public static final Item COMPACT_LIGHT = part("compact_light", Attachments.Slot.SIDE_RAIL);
    public static final Item SNIPER_SUPPRESSOR = part("sniper_suppressor", Attachments.Slot.MUZZLE);
    public static final Item BIPOD = part("bipod", Attachments.Slot.BIPOD);

    private static Item part(String name, Attachments.Slot slot) {
        String key = GunSlotData.MODEL_KEYS.get(name);
        return register(name, p -> new AttachmentItem(slot, key, p), new Item.Properties().stacksTo(1));
    }

    // Bags: what they hold and weigh in carry/BagSpec.
    public static final Item SCHOOL_BACKPACK = bag("school_backpack", com.zomboidcraft.carry.BagSpec.SCHOOL);
    public static final Item HIKING_BACKPACK = bag("hiking_backpack", com.zomboidcraft.carry.BagSpec.HIKING);
    public static final Item MILITARY_RUCKSACK = bag("military_rucksack", com.zomboidcraft.carry.BagSpec.RUCKSACK);
    public static final Item DUFFEL_BAG = bag("duffel_bag", com.zomboidcraft.carry.BagSpec.DUFFEL);
    public static final Item PLATE_CARRIER = bag("plate_carrier", com.zomboidcraft.carry.BagSpec.PLATE_CARRIER);
    public static final Item CHEST_RIG = bag("chest_rig", com.zomboidcraft.carry.BagSpec.CHEST_RIG);
    public static final Item LOAD_BEARING_VEST = bag("load_bearing_vest", com.zomboidcraft.carry.BagSpec.LOAD_BEARING_VEST);
    public static final Item MEDIC_VEST = bag("medic_vest", com.zomboidcraft.carry.BagSpec.MEDIC_VEST);
    public static final Item BANDOLIER = bag("bandolier", com.zomboidcraft.carry.BagSpec.BANDOLIER);
    public static final Item HEAVY_PLATE_CARRIER = bag("heavy_plate_carrier", com.zomboidcraft.carry.BagSpec.HEAVY_PLATE_CARRIER);
    public static final Item FANNY_PACK = bag("fanny_pack", com.zomboidcraft.carry.BagSpec.FANNY_PACK);
    public static final Item PURSE = bag("purse", com.zomboidcraft.carry.BagSpec.PURSE);
    public static final Item BRIEFCASE = bag("briefcase", com.zomboidcraft.carry.BagSpec.BRIEFCASE);
    public static final Item GYM_BAG = bag("gym_bag", com.zomboidcraft.carry.BagSpec.GYM_BAG);
    public static final Item DOCTORS_BAG = bag("doctors_bag", com.zomboidcraft.carry.BagSpec.DOCTORS_BAG);
    public static final Item PLASTIC_BAG = bag("plastic_bag", com.zomboidcraft.carry.BagSpec.PLASTIC_BAG);
    public static final Item PAPER_BAG = bag("paper_bag", com.zomboidcraft.carry.BagSpec.PAPER_BAG);
    public static final Item SUITCASE = bag("suitcase", com.zomboidcraft.carry.BagSpec.SUITCASE);
    public static final Item LUNCHBOX = bag("lunchbox", com.zomboidcraft.carry.BagSpec.LUNCHBOX);
    public static final Item COOLER = bag("cooler", com.zomboidcraft.carry.BagSpec.COOLER);
    public static final Item MAIL_BAG = bag("mail_bag", com.zomboidcraft.carry.BagSpec.MAIL_BAG);
    public static final Item TOOL_BAG = bag("tool_bag", com.zomboidcraft.carry.BagSpec.TOOL_BAG);
    public static final Item AMMO_POUCH_BELT = bag("ammo_pouch_belt", com.zomboidcraft.carry.BagSpec.AMMO_POUCH_BELT);

    // Gun cases and an ammo can: carried in the off hand, packed when found (combat/CaseItem).
    public static final Item RIFLE_CASE = gunCase("rifle_case", CaseItem.Kind.RIFLE, com.zomboidcraft.carry.BagSpec.RIFLE_CASE);
    public static final Item PISTOL_CASE = gunCase("pistol_case", CaseItem.Kind.PISTOL, com.zomboidcraft.carry.BagSpec.PISTOL_CASE);
    public static final Item AMMO_CAN = gunCase("ammo_can", CaseItem.Kind.AMMO, com.zomboidcraft.carry.BagSpec.AMMO_CAN);

    // Night vision: goggles clip onto a helmet or head mount and run on AA batteries (gear/Headgear).
    public static final Item COMBAT_HELMET = register("combat_helmet", p -> new com.zomboidcraft.gear.HeadGearItem(p, true),
            headgear().attributes(net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                    .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                            new net.minecraft.world.entity.ai.attributes.AttributeModifier(ZomboidCraft.id("helmet_armor"), 2,
                                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                            net.minecraft.world.entity.EquipmentSlotGroup.HEAD)
                    .build()));
    public static final Item NVG_HEAD_MOUNT = register("nvg_head_mount", com.zomboidcraft.gear.HeadGearItem::new, headgear());
    public static final Item NVG_GEN1 = goggles(com.zomboidcraft.gear.NightVisionItem.Generation.GEN1);
    public static final Item NVG_GEN3 = goggles(com.zomboidcraft.gear.NightVisionItem.Generation.GEN3);
    public static final Item NVG_WHITE = goggles(com.zomboidcraft.gear.NightVisionItem.Generation.WHITE);
    public static final Item AA_BATTERY = register("aa_battery", com.zomboidcraft.gear.BatteryItem::new, new Item.Properties().stacksTo(16));

    // Radios that carry players' voices through the voice chat mod, and a headset for the helmet's rails (gear/Radios).
    public static final Item TOY_RADIO = radio(com.zomboidcraft.gear.RadioItem.Type.TOY);
    public static final Item TWO_WAY_RADIO = radio(com.zomboidcraft.gear.RadioItem.Type.TWO_WAY);
    public static final Item MILITARY_RADIO = radio(com.zomboidcraft.gear.RadioItem.Type.MILITARY);
    public static final Item COMMS_HEADSET = register("comms_headset", com.zomboidcraft.gear.HeadsetItem::new, new Item.Properties().stacksTo(1));

    private static Item.Properties headgear() {
        return new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
                net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
                        .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC)
                        .setDamageOnHurt(false).build());
    }

    private static Item goggles(com.zomboidcraft.gear.NightVisionItem.Generation generation) {
        return register(generation.key, p -> new com.zomboidcraft.gear.NightVisionItem(generation, p), new Item.Properties().stacksTo(1));
    }

    private static Item radio(com.zomboidcraft.gear.RadioItem.Type type) {
        return register(type.key, p -> new com.zomboidcraft.gear.RadioItem(type, p), new Item.Properties().stacksTo(1));
    }

    private static Item gunCase(String name, CaseItem.Kind kind, com.zomboidcraft.carry.BagSpec spec) {
        return register(name, properties -> new CaseItem(kind, spec, properties), new Item.Properties());
    }

    private static Item bag(String name, com.zomboidcraft.carry.BagSpec spec) {
        return register(name, properties -> new com.zomboidcraft.carry.BagItem(spec, properties), new Item.Properties());
    }

    private static Item weapon(String name, Function<Item.Properties, Item> factory) {
        return register(name, factory, MeleeWeaponItem.properties(Weapons.byName(name)));
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
        ALL.add(item);
        return item;
    }

    public static void register() {
        ResourceKey<CreativeModeTab> combat = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
        CreativeModeTabEvents.modifyOutputEvent(combat).register(output -> {
            ALL.forEach(output::accept);
            // each gun loaded and ready to try out, a full magazine for it (a box of shells for the shotgun), and each
            // with everything on it
            for (GunType type : GunType.values()) {
                ItemStack gun = new ItemStack(type.item());
                gun.set(GunState.TYPE, GunState.EMPTY_MAG.withRounds(type.capacity()));
                List<GunFinish.Finish> finishes = GunFinish.of(type);
                if (!finishes.isEmpty()) {
                    GunFinish.set(gun, finishes.get(0).key());
                }
                output.accept(gun);
                for (GunFinish.Finish finish : finishes.stream().skip(1).toList()) {
                    ItemStack fine = gun.copy();        // and one in each of its better finishes
                    GunFinish.set(fine, finish.key());
                    output.accept(fine);
                }
                if (type.magazine() == null) {
                    continue;   // its shells are already in the tab (a creative tab only takes single items)
                }
                ItemStack mag = new ItemStack(type.magazine());
                mag.set(GunState.MAG_ROUNDS, type.capacity());
                output.accept(mag);
                ItemStack kitted = gun.copy();
                for (Item part : type == GunType.PISTOL || type == GunType.MACHINE_PISTOL ? List.of(PISTOL_SUPPRESSOR, WEAPON_LIGHT)
                        : type == GunType.RIMFIRE || type == GunType.SMG ? List.of(PISTOL_SUPPRESSOR)
                        : type == GunType.HUNTING_RIFLE || type == GunType.PRECISION_RIFLE || type == GunType.HEAVY_SNIPER
                        ? List.of(LONG_RANGE_SCOPE, SNIPER_SUPPRESSOR, BIPOD)
                        : List.of(HOLO_SIGHT, MAGNIFIER, RIFLE_SUPPRESSOR, ANGLED_GRIP, WEAPON_LIGHT)) {
                    Attachments.mount(kitted, new ItemStack(part));
                }
                if (!ItemStack.matches(kitted, gun)) {      // a gun with no rails or threads has nothing to put on
                    output.accept(kitted);
                }
            }
        });
    }

    private ModItems() {
    }
}
