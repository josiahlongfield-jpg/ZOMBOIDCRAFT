"""Writes the loot tables the city's shop fittings roll the first time someone opens them.

    python3 tools/city/shop_loot.py

One table per kind of stock (data/zomboidcraft/loot_table/shop/<name>.json). The generator
(world/city/Shops.java) names the shop's table on each shelf, case and cooler it places; a
fitting placed by hand rolls the default from tools/city/interiors.py. Like Zomboid, a shelf
can come up short: each roll may land on nothing, and some tables add a rare find on top.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/zomboidcraft/loot_table/shop"
ZC = "zomboidcraft"

WATER = {"function": "minecraft:set_potion", "id": "minecraft:water"}


def rounds(n):
    """A magazine with n rounds already loaded."""
    return {"function": "minecraft:set_components", "components": {f"{ZC}:mag_rounds": n}}


def worn(lo=0.25, hi=0.85):
    return {"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def potion(kind):
    return {"function": "minecraft:set_potion", "id": f"minecraft:{kind}"}


# (item, weight, min, max, *functions); "-" is an empty roll
TABLES = {
    "grocery": ((3, 7), [
        ("bread", 10, 1, 3), ("apple", 8, 1, 4), ("carrot", 6, 1, 4), ("potato", 6, 1, 5), ("baked_potato", 3, 1, 2),
        ("cookie", 6, 2, 6), ("melon_slice", 4, 1, 4), ("sweet_berries", 4, 2, 6), ("dried_kelp", 5, 2, 8),
        ("beetroot", 3, 1, 4), ("sugar", 4, 1, 3), ("wheat", 2, 1, 3), ("egg", 3, 1, 4), ("honey_bottle", 2, 1, 1),
        ("pumpkin_pie", 2, 1, 1), ("cooked_chicken", 2, 1, 2), ("milk_bucket", 1, 1, 1), ("potion", 6, 1, 2, WATER),
        ("glass_bottle", 2, 1, 2), ("-", 8, 1, 1)]),
    "pharmacy": ((2, 5), [
        (f"{ZC}:bandage", 12, 1, 3), (f"{ZC}:splint", 4, 1, 1), ("potion", 4, 1, 1, potion("healing")), ("potion", 2, 1, 1, potion("regeneration")),
        ("potion", 6, 1, 2, WATER), ("honey_bottle", 4, 1, 2), ("milk_bucket", 2, 1, 1), ("glass_bottle", 4, 1, 3),
        ("paper", 4, 1, 4), ("sugar", 2, 1, 2), ("golden_carrot", 1, 1, 1), ("-", 8, 1, 1)]),
    "liquor": ((2, 6), [
        ("potion", 10, 1, 2, potion("awkward")), ("potion", 4, 1, 1, potion("strength")), ("potion", 6, 1, 2, WATER),
        ("honey_bottle", 6, 1, 2), ("glass_bottle", 8, 1, 3), ("sweet_berries", 2, 2, 5), ("cookie", 3, 1, 4),
        ("dried_kelp", 3, 2, 6), ("-", 8, 1, 1)]),
    "drinks": ((2, 5), [
        ("potion", 12, 1, 3, WATER), ("honey_bottle", 6, 1, 2), ("milk_bucket", 3, 1, 1), ("melon_slice", 4, 1, 4),
        ("potion", 2, 1, 1, potion("swiftness")), ("glass_bottle", 4, 1, 2), ("-", 8, 1, 1)]),
    "hardware": ((2, 6), [
        (f"{ZC}:nails", 12, 4, 16), (f"{ZC}:hammer", 4, 1, 1), (f"{ZC}:crowbar", 2, 1, 1), ("iron_axe", 1, 1, 1, worn(0, .3)),
        (f"{ZC}:pipe_wrench", 2, 1, 1), (f"{ZC}:lead_pipe", 1, 1, 1), (f"{ZC}:shovel", 2, 1, 1),
        (f"{ZC}:sledgehammer", 1, 1, 1, worn(0, .5)), (f"{ZC}:fire_axe", 1, 1, 1, worn(0, .5)), ("iron_pickaxe", 1, 1, 1), ("shears", 2, 1, 1), ("flint_and_steel", 2, 1, 1),
        ("string", 4, 2, 6), ("iron_nugget", 4, 2, 9), ("torch", 4, 2, 8), ("lantern", 2, 1, 1),
        ("oak_planks", 5, 4, 12), ("stick", 4, 2, 8), ("bucket", 2, 1, 1), ("ladder", 2, 1, 4), ("iron_bars", 2, 1, 4),
        ("oak_trapdoor", 1, 1, 2), (f"{ZC}:duffel_bag", 1, 1, 1), ("-", 6, 1, 1)]),
    "guns": ((2, 5), [
        (f"{ZC}:rifle_ammo", 12, 5, 30), (f"{ZC}:rifle_magazine", 5, 1, 1), (f"{ZC}:rifle_magazine", 2, 1, 1, rounds(12)),
        (f"{ZC}:assault_rifle", 2, 1, 1, worn(.1, .7)), (f"{ZC}:red_dot_sight", 2, 1, 1), (f"{ZC}:holo_sight", 2, 1, 1),
        (f"{ZC}:scope_4x", 1, 1, 1), (f"{ZC}:rifle_suppressor", 1, 1, 1), (f"{ZC}:foregrip", 2, 1, 1),
        (f"{ZC}:weapon_light", 2, 1, 1), (f"{ZC}:laser_sight", 1, 1, 1), (f"{ZC}:rifle_stock", 1, 1, 1),
        (f"{ZC}:pistol", 3, 1, 1, worn(.1, .7)), (f"{ZC}:pistol_magazine", 5, 1, 1), (f"{ZC}:pistol_magazine", 2, 1, 1, rounds(8)),
        (f"{ZC}:pistol_ammo", 12, 5, 30), (f"{ZC}:pistol_suppressor", 1, 1, 1),
        (f"{ZC}:shotgun", 3, 1, 1, worn(.1, .7)), (f"{ZC}:shotgun_shell", 12, 4, 20),
        (f"{ZC}:hunting_rifle", 2, 1, 1, worn(.1, .6)), (f"{ZC}:hunting_rifle_magazine", 3, 1, 1),
        (f"{ZC}:hunting_rifle_magazine", 1, 1, 1, rounds(3)), (f"{ZC}:hunting_rifle_ammo", 8, 3, 15),
        (f"{ZC}:rifle_case", 2, 1, 1), (f"{ZC}:pistol_case", 2, 1, 1), (f"{ZC}:ammo_can", 3, 1, 1),
        ("arrow", 6, 4, 16), ("bow", 2, 1, 1, worn(0, .4)),
        ("crossbow", 2, 1, 1, worn(0, .4)), ("iron_sword", 1, 1, 1, worn()), (f"{ZC}:machete", 2, 1, 1),
        (f"{ZC}:kitchen_knife", 2, 1, 1), ("leather_chestplate", 2, 1, 1, worn()), ("iron_helmet", 1, 1, 1, worn()),
        ("shield", 1, 1, 1, worn()), ("gunpowder", 3, 1, 4), ("string", 3, 1, 4), ("-", 8, 1, 1)]),
    "pawn": ((2, 5), [
        ("gold_nugget", 8, 2, 9), ("gold_ingot", 3, 1, 2), ("emerald", 4, 1, 3), ("diamond", 1, 1, 1),
        (f"{ZC}:digital_watch", 4, 1, 1), ("clock", 1, 1, 1), ("compass", 3, 1, 1), ("spyglass", 2, 1, 1), ("goat_horn", 1, 1, 1),
        ("music_disc_13", 1, 1, 1), ("music_disc_cat", 1, 1, 1), ("music_disc_stal", 1, 1, 1), ("name_tag", 2, 1, 1),
        ("amethyst_shard", 3, 1, 4), ("lead", 2, 1, 1), (f"{ZC}:baseball_bat", 2, 1, 1), (f"{ZC}:katana", 1, 1, 1),
        (f"{ZC}:golf_club", 2, 1, 1, worn(0, .5)), (f"{ZC}:hockey_stick", 1, 1, 1, worn(0, .5)), (f"{ZC}:rifle_ammo", 1, 3, 12), ("iron_sword", 1, 1, 1, worn()),
        (f"{ZC}:pistol", 1, 1, 1, worn(.2, .8)), (f"{ZC}:pistol_ammo", 2, 3, 15),
        (f"{ZC}:shotgun", 1, 1, 1, worn(.2, .8)), (f"{ZC}:shotgun_shell", 2, 2, 10),
        (f"{ZC}:hunting_rifle", 1, 1, 1, worn(.2, .8)), (f"{ZC}:hunting_rifle_ammo", 1, 2, 8),
        (f"{ZC}:rifle_case", 1, 1, 1), (f"{ZC}:pistol_case", 1, 1, 1),
        ("golden_helmet", 1, 1, 1, worn()), ("book", 2, 1, 2), ("-", 8, 1, 1)]),
    "thrift": ((2, 6), [
        ("leather_helmet", 4, 1, 1, worn()), ("leather_chestplate", 4, 1, 1, worn()), ("leather_leggings", 4, 1, 1, worn()),
        ("leather_boots", 4, 1, 1, worn()), ("leather", 4, 1, 3), ("white_wool", 3, 1, 3), ("gray_wool", 3, 1, 3),
        ("brown_wool", 3, 1, 3), ("red_carpet", 2, 1, 2), ("string", 4, 1, 5), ("book", 4, 1, 3), ("painting", 2, 1, 1),
        ("item_frame", 2, 1, 1), ("flower_pot", 2, 1, 1), ("bowl", 2, 1, 3), (f"{ZC}:digital_watch", 2, 1, 1),
        (f"{ZC}:golf_club", 2, 1, 1, worn()), (f"{ZC}:hockey_stick", 2, 1, 1, worn()), (f"{ZC}:baseball_bat", 1, 1, 1, worn()),
        (f"{ZC}:frying_pan", 1, 1, 1, worn()), (f"{ZC}:school_backpack", 2, 1, 1), (f"{ZC}:duffel_bag", 1, 1, 1),
        (f"{ZC}:fanny_pack", 1, 1, 1), ("-", 8, 1, 1)]),
    "clothes": ((1, 4), [
        ("leather_helmet", 3, 1, 1, worn()), ("leather_chestplate", 3, 1, 1, worn()), ("leather_leggings", 3, 1, 1, worn()),
        ("leather_boots", 3, 1, 1, worn()), ("white_wool", 4, 1, 3), ("light_gray_wool", 3, 1, 3), ("blue_wool", 2, 1, 2),
        ("string", 6, 1, 5), ("leather", 3, 1, 2), (f"{ZC}:school_backpack", 2, 1, 1), (f"{ZC}:fanny_pack", 2, 1, 1),
        (f"{ZC}:hiking_backpack", 1, 1, 1), (f"{ZC}:duffel_bag", 1, 1, 1), ("-", 10, 1, 1)]),
    "kitchen": ((2, 6), [
        ("bread", 8, 1, 3), ("cooked_beef", 4, 1, 3), ("cooked_porkchop", 4, 1, 3), ("cooked_chicken", 4, 1, 3),
        ("baked_potato", 5, 1, 4), ("mushroom_stew", 3, 1, 1), ("beetroot_soup", 2, 1, 1), ("pumpkin_pie", 3, 1, 2),
        ("cake", 1, 1, 1), ("cookie", 4, 2, 8), ("sugar", 3, 1, 3), ("egg", 3, 1, 4), ("milk_bucket", 2, 1, 1),
        ("bowl", 4, 1, 4), (f"{ZC}:kitchen_knife", 3, 1, 1), (f"{ZC}:frying_pan", 3, 1, 1), ("coal", 3, 1, 4), ("potion", 4, 1, 2, WATER), ("-", 8, 1, 1)]),
    "till": ((1, 3), [
        ("gold_nugget", 10, 1, 6), ("emerald", 3, 1, 2), ("paper", 8, 1, 6), ("iron_nugget", 4, 1, 4), ("name_tag", 1, 1, 1),
        ("-", 10, 1, 1)]),
    "cash": ((1, 4), [
        ("paper", 12, 2, 10), ("gold_nugget", 8, 1, 6), ("emerald", 3, 1, 2), ("book", 3, 1, 1), ("writable_book", 2, 1, 1),
        ("map", 2, 1, 1), ("ink_sac", 2, 1, 2), ("-", 10, 1, 1)]),
    "laundromat": ((1, 4), [
        ("string", 8, 1, 6), ("white_wool", 6, 1, 3), ("leather", 2, 1, 2), ("paper", 4, 1, 3), ("glass_bottle", 4, 1, 2),
        ("potion", 3, 1, 1, WATER), ("bucket", 1, 1, 1), ("-", 10, 1, 1)]),
    "office": ((1, 4), [
        ("paper", 14, 2, 10), ("book", 5, 1, 2), ("writable_book", 3, 1, 1), ("map", 2, 1, 1), ("ink_sac", 3, 1, 2),
        ("compass", 1, 1, 1), (f"{ZC}:digital_watch", 2, 1, 1), (f"{ZC}:bandage", 2, 1, 1), ("-", 12, 1, 1)]),
    "storage": ((1, 4), [
        (f"{ZC}:nails", 6, 2, 10), ("oak_planks", 5, 2, 8), ("stick", 4, 2, 6), ("string", 4, 1, 4), ("paper", 4, 1, 4),
        ("torch", 3, 1, 4), ("bread", 3, 1, 2), ("potion", 3, 1, 1, WATER), ("bone", 2, 1, 3), ("leather", 2, 1, 2),
        (f"{ZC}:bandage", 2, 1, 2), (f"{ZC}:lead_pipe", 1, 1, 1, worn()), (f"{ZC}:shovel", 1, 1, 1, worn()),
        (f"{ZC}:golf_club", 1, 1, 1, worn()), (f"{ZC}:duffel_bag", 1, 1, 1), (f"{ZC}:hiking_backpack", 1, 1, 1),
        (f"{ZC}:shotgun_shell", 1, 2, 8), (f"{ZC}:ammo_can", 1, 1, 1), ("-", 12, 1, 1)]),
    "farm": ((1, 5), [
        ("wheat_seeds", 8, 2, 12), ("beetroot_seeds", 4, 2, 8), ("pumpkin_seeds", 3, 1, 6), ("melon_seeds", 2, 1, 4),
        ("potato", 5, 1, 6), ("carrot", 5, 1, 6), ("wheat", 4, 2, 8), ("egg", 3, 1, 6), ("apple", 3, 1, 4),
        ("bone_meal", 4, 2, 8), ("bucket", 2, 1, 1), ("shears", 2, 1, 1, worn()), ("lead", 2, 1, 2), ("saddle", 1, 1, 1),
        ("string", 4, 1, 6), ("leather", 3, 1, 3), ("iron_hoe", 1, 1, 1, worn()), ("stone_hoe", 2, 1, 1, worn()),
        ("honey_bottle", 1, 1, 2), (f"{ZC}:nails", 3, 2, 10), (f"{ZC}:shovel", 1, 1, 1, worn()),
        (f"{ZC}:pipe_wrench", 1, 1, 1, worn()), (f"{ZC}:shotgun_shell", 2, 2, 8), (f"{ZC}:hunting_rifle_ammo", 2, 2, 8),
        ("-", 10, 1, 1)]),
    "scraps": ((0, 2), [
        ("paper", 6, 1, 3), ("stick", 4, 1, 3), ("string", 3, 1, 2), ("bone", 2, 1, 2), ("glass_bottle", 3, 1, 1),
        ("rotten_flesh", 2, 1, 2), (f"{ZC}:lead_pipe", 1, 1, 1, worn(.5, .9)), ("-", 14, 1, 1)]),
}

# Houses, per container, after Zomboid's room-and-container lists: a kitchen drawer holds cutlery,
# matches and a torch, the bathroom cabinet pills, the garage tools, a bedside drawer now and
# then a pistol. Many come up empty; food in the fridge has mostly gone off.
CLOTHES = [("leather_helmet", 2, 1, 1, worn()), ("leather_chestplate", 3, 1, 1, worn()),
           ("leather_leggings", 3, 1, 1, worn()), ("leather_boots", 3, 1, 1, worn())]
TABLES.update({
    "home_cupboard": ((1, 4), [
        ("bowl", 6, 1, 4), ("glass_bottle", 5, 1, 3), ("potion", 4, 1, 2, WATER), ("sugar", 3, 1, 2), ("bread", 3, 1, 2),
        ("cookie", 3, 1, 6), ("dried_kelp", 4, 2, 8), ("baked_potato", 2, 1, 3), ("honey_bottle", 2, 1, 1),
        ("bucket", 1, 1, 1), (f"{ZC}:frying_pan", 2, 1, 1), (f"{ZC}:kitchen_knife", 1, 1, 1), ("-", 12, 1, 1)]),
    "home_drawer": ((1, 3), [
        (f"{ZC}:kitchen_knife", 6, 1, 1), ("paper", 6, 1, 4), ("string", 4, 1, 4), ("candle", 3, 1, 3),
        ("flint_and_steel", 2, 1, 1), ("torch", 3, 1, 3), ("shears", 2, 1, 1), ("gold_nugget", 2, 1, 4),
        (f"{ZC}:nails", 3, 2, 8), (f"{ZC}:bandage", 2, 1, 1), ("name_tag", 1, 1, 1), ("-", 12, 1, 1)]),
    "home_sink": ((0, 3), [
        ("bucket", 3, 1, 1), ("glass_bottle", 5, 1, 3), ("potion", 2, 1, 1, WATER), ("sponge", 2, 1, 1),
        ("string", 2, 1, 3), ("paper", 3, 1, 3), (f"{ZC}:pipe_wrench", 2, 1, 1, worn()), (f"{ZC}:lead_pipe", 1, 1, 1, worn()),
        ("-", 10, 1, 1)]),
    "home_pantry": ((1, 5), [
        ("bread", 6, 1, 3), ("cookie", 5, 2, 8), ("sugar", 4, 1, 3), ("dried_kelp", 6, 2, 10), ("honey_bottle", 3, 1, 2),
        ("pumpkin_pie", 2, 1, 1), ("sweet_berries", 3, 2, 6), ("wheat", 3, 1, 4), ("cocoa_beans", 3, 1, 4),
        ("bowl", 3, 1, 3), ("glass_bottle", 3, 1, 2), ("potion", 4, 1, 2, WATER), ("-", 12, 1, 1)]),
    "home_oven": ((0, 2), [
        ("baked_potato", 3, 1, 3), ("cooked_chicken", 2, 1, 1), ("bread", 2, 1, 1), ("pumpkin_pie", 2, 1, 1),
        (f"{ZC}:frying_pan", 4, 1, 1), ("bowl", 2, 1, 2), ("rotten_flesh", 3, 1, 2), ("-", 14, 1, 1)]),
    "home_fridge": ((1, 5), [
        ("milk_bucket", 3, 1, 1), ("egg", 4, 1, 6), ("apple", 4, 1, 4), ("carrot", 3, 1, 4), ("cooked_beef", 2, 1, 2),
        ("cooked_porkchop", 2, 1, 2), ("beef", 2, 1, 3), ("chicken", 2, 1, 2), ("melon_slice", 3, 1, 4),
        ("potion", 5, 1, 3, WATER), ("rotten_flesh", 8, 1, 4), ("-", 8, 1, 1)]),
    "home_freezer": ((1, 4), [
        ("beef", 4, 1, 4), ("porkchop", 4, 1, 4), ("chicken", 4, 1, 3), ("cod", 3, 1, 3), ("salmon", 3, 1, 3),
        ("ice", 2, 1, 4), ("rotten_flesh", 5, 1, 4), ("-", 8, 1, 1)]),
    "home_trash": ((0, 3), [
        ("paper", 6, 1, 4), ("rotten_flesh", 6, 1, 3), ("bone", 4, 1, 3), ("glass_bottle", 4, 1, 2), ("bowl", 2, 1, 1),
        ("string", 2, 1, 2), ("stick", 2, 1, 2), ("-", 14, 1, 1)]),
    "home_books": ((1, 4), [
        ("book", 12, 1, 3), ("writable_book", 3, 1, 1), ("paper", 4, 1, 4), ("map", 2, 1, 1), ("compass", 1, 1, 1),
        ("clock", 1, 1, 1), ("-", 8, 1, 1)]),
    "home_nightstand": ((0, 3), [
        ("book", 5, 1, 1), ("paper", 3, 1, 3), ("candle", 3, 1, 2), ("torch", 3, 1, 2), ("clock", 1, 1, 1),
        (f"{ZC}:digital_watch", 3, 1, 1), (f"{ZC}:bandage", 2, 1, 2), ("potion", 2, 1, 1, potion("healing")),
        ("glass_bottle", 2, 1, 1), ("gold_nugget", 2, 1, 4), (f"{ZC}:pistol", 1, 1, 1, worn(.1, .6)),
        (f"{ZC}:pistol_ammo", 2, 3, 12), ("-", 10, 1, 1)]),
    "home_dresser": ((1, 4), CLOTHES + [
        ("white_wool", 3, 1, 3), ("gray_wool", 2, 1, 2), ("blue_wool", 2, 1, 2), ("string", 3, 1, 4),
        (f"{ZC}:digital_watch", 2, 1, 1), ("gold_nugget", 2, 1, 5), ("emerald", 1, 1, 1), (f"{ZC}:fanny_pack", 1, 1, 1),
        ("-", 8, 1, 1)]),
    "home_wardrobe": ((1, 4), CLOTHES + [
        ("white_wool", 3, 1, 3), ("black_wool", 2, 1, 2), (f"{ZC}:school_backpack", 2, 1, 1), (f"{ZC}:hiking_backpack", 1, 1, 1),
        (f"{ZC}:duffel_bag", 2, 1, 1), (f"{ZC}:baseball_bat", 1, 1, 1, worn(0, .5)), (f"{ZC}:golf_club", 1, 1, 1, worn(0, .5)),
        (f"{ZC}:shotgun", 1, 1, 1, worn(.2, .7)), (f"{ZC}:shotgun_shell", 1, 2, 8), ("-", 8, 1, 1)]),
    "home_desk": ((1, 4), [
        ("paper", 10, 1, 8), ("book", 4, 1, 2), ("writable_book", 3, 1, 1), ("ink_sac", 3, 1, 2), ("map", 2, 1, 1),
        ("compass", 1, 1, 1), (f"{ZC}:digital_watch", 2, 1, 1), ("clock", 1, 1, 1), ("gold_nugget", 1, 1, 3),
        ("-", 10, 1, 1)]),
    "home_toys": ((1, 4), [
        ("book", 4, 1, 2), ("paper", 4, 1, 4), ("string", 3, 1, 3), ("white_wool", 3, 1, 2), ("cookie", 2, 1, 4),
        ("music_disc_cat", 1, 1, 1), ("music_disc_13", 1, 1, 1), ("name_tag", 1, 1, 1), ("red_dye", 2, 1, 3),
        ("blue_dye", 2, 1, 3), ("slime_ball", 2, 1, 2), ("snowball", 2, 2, 6), (f"{ZC}:school_backpack", 2, 1, 1),
        (f"{ZC}:baseball_bat", 1, 1, 1, worn(0, .4)), (f"{ZC}:hockey_stick", 1, 1, 1, worn(0, .4)), ("-", 8, 1, 1)]),
    "home_laundry": ((1, 3), CLOTHES + [
        ("white_wool", 6, 1, 3), ("light_gray_wool", 4, 1, 3), ("string", 4, 1, 3), ("-", 8, 1, 1)]),
    "home_bathroom": ((1, 3), [
        ("paper", 8, 1, 6), (f"{ZC}:bandage", 4, 1, 2), ("potion", 3, 1, 1, WATER), ("glass_bottle", 4, 1, 2),
        ("shears", 2, 1, 1), ("white_wool", 3, 1, 2), ("honey_bottle", 1, 1, 1), ("sponge", 1, 1, 1), ("-", 10, 1, 1)]),
    "home_medicine": ((1, 3), [
        (f"{ZC}:bandage", 10, 1, 3), (f"{ZC}:splint", 3, 1, 1), ("potion", 4, 1, 1, potion("healing")),
        ("potion", 2, 1, 1, potion("regeneration")), ("glass_bottle", 4, 1, 2), ("honey_bottle", 2, 1, 1),
        ("paper", 2, 1, 2), ("-", 8, 1, 1)]),
    "home_garage": ((1, 5), [
        (f"{ZC}:nails", 10, 4, 16), (f"{ZC}:hammer", 3, 1, 1), (f"{ZC}:crowbar", 2, 1, 1), (f"{ZC}:pipe_wrench", 3, 1, 1, worn()),
        (f"{ZC}:lead_pipe", 2, 1, 1, worn()), (f"{ZC}:shovel", 2, 1, 1, worn()), (f"{ZC}:sledgehammer", 1, 1, 1, worn(0, .5)),
        ("string", 4, 1, 6), ("iron_nugget", 4, 2, 9), ("oak_planks", 4, 2, 8), ("stick", 3, 2, 6), ("bucket", 2, 1, 1),
        ("torch", 3, 1, 4), ("flint_and_steel", 2, 1, 1), ("shears", 1, 1, 1), ("lantern", 1, 1, 1), ("ladder", 1, 1, 3),
        ("coal", 2, 1, 4), ("-", 10, 1, 1)]),
    "home_tools": ((1, 4), [
        (f"{ZC}:nails", 8, 4, 16), (f"{ZC}:hammer", 4, 1, 1), (f"{ZC}:pipe_wrench", 4, 1, 1), (f"{ZC}:crowbar", 3, 1, 1),
        ("iron_nugget", 5, 2, 9), ("shears", 2, 1, 1), ("flint_and_steel", 2, 1, 1), ("string", 2, 1, 4),
        (f"{ZC}:lead_pipe", 1, 1, 1), ("-", 8, 1, 1)]),
    "home_boxes": ((1, 4), CLOTHES + [
        ("book", 3, 1, 3), ("paper", 4, 1, 6), ("string", 3, 1, 4), ("white_wool", 2, 1, 2), ("painting", 1, 1, 1),
        ("flower_pot", 1, 1, 1), ("item_frame", 1, 1, 1), ("candle", 2, 1, 3), ("torch", 1, 1, 2),
        (f"{ZC}:golf_club", 1, 1, 1, worn()), (f"{ZC}:hockey_stick", 1, 1, 1, worn()), (f"{ZC}:baseball_bat", 1, 1, 1, worn()),
        (f"{ZC}:duffel_bag", 1, 1, 1), ("music_disc_stal", 1, 1, 1), ("-", 12, 1, 1)]),
    "home_guns": ((1, 4), [
        (f"{ZC}:shotgun", 3, 1, 1, worn(.1, .6)), (f"{ZC}:shotgun_shell", 10, 4, 16), (f"{ZC}:hunting_rifle", 3, 1, 1, worn(.1, .6)),
        (f"{ZC}:hunting_rifle_ammo", 8, 3, 12), (f"{ZC}:hunting_rifle_magazine", 2, 1, 1), (f"{ZC}:pistol", 2, 1, 1, worn(.1, .6)),
        (f"{ZC}:pistol_ammo", 8, 4, 16), (f"{ZC}:pistol_magazine", 3, 1, 1), (f"{ZC}:rifle_ammo", 2, 4, 12),
        ("arrow", 3, 4, 12), ("bow", 1, 1, 1, worn(0, .5)), ("-", 6, 1, 1)]),
})

# a rare find on top of the shelf's own stock
RARE = {
    "grocery": [(f"{ZC}:kitchen_knife", 1), (f"{ZC}:frying_pan", 1)],
    "hardware": [(f"{ZC}:machete", 1), (f"{ZC}:baseball_bat", 1), (f"{ZC}:fire_axe", 1)],
    "pharmacy": [("totem_of_undying", 1)], "guns": [(f"{ZC}:assault_rifle", 2), (f"{ZC}:hunting_rifle", 1), (f"{ZC}:red_dot_sight", 2), (f"{ZC}:scope_4x", 1),
             (f"{ZC}:rifle_suppressor", 1), (f"{ZC}:katana", 1)],
    "pawn": [("diamond", 2), ("golden_apple", 1), (f"{ZC}:assault_rifle", 1), (f"{ZC}:red_dot_sight", 1),
             (f"{ZC}:holo_sight", 1)],
    "storage": [(f"{ZC}:crowbar", 1), (f"{ZC}:hammer", 1), (f"{ZC}:sledgehammer", 1), (f"{ZC}:military_rucksack", 1),
                (f"{ZC}:shotgun", 1), (f"{ZC}:rifle_case", 1), (f"{ZC}:pistol_case", 1)],
    "home_nightstand": [(f"{ZC}:pistol", 1)], "home_garage": [(f"{ZC}:fire_axe", 1), (f"{ZC}:machete", 1)],
    "home_wardrobe": [(f"{ZC}:hunting_rifle", 1), (f"{ZC}:military_rucksack", 1)], "home_toys": [(f"{ZC}:baseball_bat", 1)],
    "home_guns": [(f"{ZC}:assault_rifle", 1), (f"{ZC}:scope_4x", 1)],
    "farm": [(f"{ZC}:fire_axe", 2), (f"{ZC}:machete", 1), ("golden_apple", 1), (f"{ZC}:shotgun", 2),
             (f"{ZC}:hunting_rifle", 2), (f"{ZC}:rifle_case", 2), (f"{ZC}:ammo_can", 1)],
}


def entry(item, weight, lo, hi, *functions):
    if item == "-":
        return {"type": "minecraft:empty", "weight": weight}
    name = item if ":" in item else f"minecraft:{item}"
    fns = list(functions)
    if hi > 1:
        fns.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if fns:
        e["functions"] = fns
    return e


def table(name):
    (lo, hi), entries = TABLES[name]
    pools = [{"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": [entry(*e) for e in entries]}]
    if name in RARE:
        pools.append({"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.03}],
                      "entries": [entry(item, w, 1, 1) for item, w in RARE[name]]})
    return {"type": "minecraft:chest", "pools": pools, "random_sequence": f"{ZC}:shop/{name}"}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    for name in TABLES:
        (OUT / f"{name}.json").write_text(json.dumps(table(name), indent=2) + "\n")
    print(f"{len(TABLES)} shop loot tables in {OUT}")


if __name__ == "__main__":
    main()
