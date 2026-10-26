"""Writes the loot tables for car boots and the backs of vans, rolled the first time someone looks.

    python3 tools/cars/car_loot.py

data/zomboidcraft/loot_table/car/<name>.json, in the same format as the shop tables
(tools/city/shop_loot.py). CarProp.lootTable() picks the table from the car's model: most boots
are thin, while police, SWAT and ambulance vehicles are rarer and carry better gear.
"""
import json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/city"))
from shop_loot import entry, worn, rounds, potion, WATER, ZC

OUT = ROOT / "src/main/resources/data/zomboidcraft/loot_table/car"

TABLES = {
    # an ordinary boot: whatever people had with them when they ran
    "trunk": ((1, 4), [
        ("potion", 6, 1, 2, WATER), ("bread", 4, 1, 2), ("cookie", 3, 1, 4), ("dried_kelp", 3, 2, 6),
        (f"{ZC}:bandage", 3, 1, 2), ("leather_chestplate", 2, 1, 1, worn()), ("leather_boots", 2, 1, 1, worn()),
        ("white_wool", 2, 1, 2), ("string", 3, 1, 4), ("paper", 4, 1, 4), ("book", 2, 1, 1), ("map", 1, 1, 1),
        ("bucket", 2, 1, 1), ("torch", 3, 1, 4), ("lead", 1, 1, 1), ("compass", 1, 1, 1), (f"{ZC}:digital_watch", 2, 1, 1),
        (f"{ZC}:pipe_wrench", 2, 1, 1, worn(0, .5)), (f"{ZC}:crowbar", 2, 1, 1, worn(0, .5)), (f"{ZC}:hammer", 1, 1, 1),
        (f"{ZC}:golf_club", 2, 1, 1, worn()), (f"{ZC}:baseball_bat", 1, 1, 1, worn()), ("-", 12, 1, 1)]),
    # vans, pickups and trucks: a tradesman's gear
    "work": ((2, 5), [
        (f"{ZC}:nails", 8, 4, 16), ("oak_planks", 6, 2, 10), (f"{ZC}:hammer", 3, 1, 1), (f"{ZC}:crowbar", 3, 1, 1),
        (f"{ZC}:pipe_wrench", 3, 1, 1), (f"{ZC}:shovel", 2, 1, 1), (f"{ZC}:sledgehammer", 1, 1, 1, worn(0, .5)),
        ("iron_axe", 1, 1, 1, worn(0, .5)), ("iron_pickaxe", 1, 1, 1, worn()), ("string", 3, 1, 5), ("ladder", 2, 1, 3),
        ("bucket", 2, 1, 1), ("iron_nugget", 3, 2, 8), ("torch", 3, 1, 6), ("chest", 1, 1, 1), ("-", 8, 1, 1)]),
    # a patrol car's boot: some ammo, a spare sidearm, a vest, first aid
    "police": ((2, 4), [
        (f"{ZC}:rifle_ammo", 8, 4, 16), (f"{ZC}:rifle_magazine", 3, 1, 1), (f"{ZC}:rifle_magazine", 2, 1, 1, rounds(12)),
        (f"{ZC}:pistol_ammo", 8, 4, 16), (f"{ZC}:pistol_magazine", 3, 1, 1), (f"{ZC}:pistol_magazine", 2, 1, 1, rounds(15)),
        (f"{ZC}:pistol", 2, 1, 1, worn(.1, .6)), (f"{ZC}:shotgun", 3, 1, 1, worn(.1, .6)), (f"{ZC}:shotgun_shell", 6, 4, 12),
        (f"{ZC}:revolver", 1, 1, 1, worn(.2, .7)), (f"{ZC}:revolver_ammo", 3, 4, 12),
        ("iron_chestplate", 2, 1, 1, worn(.2, .7)), ("iron_helmet", 2, 1, 1, worn(.2, .7)), (f"{ZC}:bandage", 5, 1, 3),
        ("shield", 2, 1, 1, worn()), ("lead", 2, 1, 1), ("paper", 4, 1, 3), ("compass", 1, 1, 1), (f"{ZC}:digital_watch", 2, 1, 1),
        ("potion", 3, 1, 1, WATER), (f"{ZC}:pistol_case", 1, 1, 1), (f"{ZC}:ammo_can", 1, 1, 1), ("-", 6, 1, 1)]),
    # the back of a SWAT van: rare, and the best gear on the street
    "swat": ((3, 6), [
        (f"{ZC}:assault_rifle", 3, 1, 1, worn(0, .4)), (f"{ZC}:rifle_magazine", 6, 1, 2, rounds(30)),
        (f"{ZC}:rifle_ammo", 10, 10, 30), (f"{ZC}:pistol", 2, 1, 1, worn(0, .4)), (f"{ZC}:pistol_magazine", 3, 1, 2, rounds(15)),
        (f"{ZC}:pistol_suppressor", 1, 1, 1), (f"{ZC}:shotgun", 2, 1, 1, worn(0, .4)), (f"{ZC}:shotgun_shell", 5, 6, 16),
        ("iron_chestplate", 4, 1, 1, worn(0, .4)), ("iron_helmet", 4, 1, 1, worn(0, .4)),
        ("iron_leggings", 3, 1, 1, worn(0, .4)), ("iron_boots", 3, 1, 1, worn(0, .4)), ("shield", 3, 1, 1),
        (f"{ZC}:bandage", 4, 1, 3), ("potion", 2, 1, 1, potion("healing")), (f"{ZC}:crowbar", 2, 1, 1),
        (f"{ZC}:sledgehammer", 1, 1, 1), ("spyglass", 1, 1, 1), (f"{ZC}:rifle_case", 2, 1, 1), (f"{ZC}:ammo_can", 3, 1, 1)]),
    # the back of an ambulance: medicine, rare and plenty of it
    "ambulance": ((3, 7), [
        (f"{ZC}:bandage", 14, 2, 6), (f"{ZC}:splint", 5, 1, 2), ("potion", 5, 1, 2, potion("healing")), ("potion", 3, 1, 1, potion("regeneration")),
        ("potion", 4, 1, 3, WATER), ("honey_bottle", 3, 1, 2), ("milk_bucket", 2, 1, 1), ("golden_carrot", 2, 1, 2),
        ("golden_apple", 1, 1, 1), ("white_wool", 3, 1, 3), ("paper", 2, 1, 3), ("glass_bottle", 2, 1, 3)]),
    # fire engine lockers
    "fire": ((2, 5), [
        (f"{ZC}:fire_axe", 4, 1, 1, worn(0, .4)), (f"{ZC}:crowbar", 3, 1, 1), (f"{ZC}:sledgehammer", 2, 1, 1),
        ("iron_helmet", 3, 1, 1, worn()), ("leather_chestplate", 3, 1, 1, worn()), ("bucket", 3, 1, 1),
        ("water_bucket", 2, 1, 1), ("ladder", 3, 1, 4), (f"{ZC}:bandage", 3, 1, 2), ("-", 4, 1, 1)]),
    # buses: luggage racks and lost property
    "bus": ((1, 4), [
        ("paper", 6, 1, 4), ("book", 3, 1, 2), ("bread", 3, 1, 2), ("cookie", 3, 1, 3), ("potion", 4, 1, 1, WATER),
        ("leather_helmet", 2, 1, 1, worn()), ("leather_chestplate", 2, 1, 1, worn()), ("white_wool", 2, 1, 2),
        ("string", 2, 1, 3), (f"{ZC}:baseball_bat", 1, 1, 1, worn()), ("-", 12, 1, 1)]),
}

# night vision (gear/Headgear): a patrol car's spare batteries, a SWAT team's goggles
TABLES["police"] = (TABLES["police"][0], TABLES["police"][1] + [(f"{ZC}:combat_helmet", 1, 1, 1), (f"{ZC}:aa_battery", 3, 2, 6)])
TABLES["swat"] = (TABLES["swat"][0], TABLES["swat"][1] + [(f"{ZC}:combat_helmet", 3, 1, 1), (f"{ZC}:nvg_gen3", 2, 1, 1),
                                                          (f"{ZC}:aa_battery", 4, 2, 8)])

# a rare find on top
RARE = {
    "trunk": [(f"{ZC}:assault_rifle", 1), (f"{ZC}:pistol", 2), (f"{ZC}:shotgun", 1), (f"{ZC}:double_barrel", 1), (f"{ZC}:revolver", 2),
              (f"{ZC}:hunting_rifle", 1),
              (f"{ZC}:machete", 1), (f"{ZC}:katana", 1), (f"{ZC}:rifle_case", 2), (f"{ZC}:pistol_case", 2), (f"{ZC}:ammo_can", 1)],
    "police": [(f"{ZC}:assault_rifle", 2), (f"{ZC}:nvg_gen3", 1)],
    "swat": [("totem_of_undying", 1), ("diamond_chestplate", 1), (f"{ZC}:nvg_white", 1)],
    "ambulance": [("totem_of_undying", 1), ("enchanted_golden_apple", 1)],
}


# repair supplies, maps and radios: first added to the tables by hand, kept here so a rerun keeps them
HAND_ADDED = {
    "ambulance": [(f"{ZC}:two_way_radio", 2, 1, 1)],
    "bus": [(f"{ZC}:street_map", 2, 1, 1)],
    "fire": [(f"{ZC}:two_way_radio", 3, 1, 1)],
    "police": [(f"{ZC}:gun_oil", 2, 1, 1), (f"{ZC}:street_map", 2, 1, 1), (f"{ZC}:road_atlas", 1, 1, 1), (f"{ZC}:two_way_radio", 4, 1, 1), (f"{ZC}:comms_headset", 1, 1, 1)],
    "swat": [(f"{ZC}:military_radio", 3, 1, 1), (f"{ZC}:comms_headset", 3, 1, 1)],
    "trunk": [(f"{ZC}:toy_radio", 1, 1, 2), (f"{ZC}:two_way_radio", 1, 1, 1)],
    "work": [(f"{ZC}:duct_tape", 3, 1, 2), (f"{ZC}:street_map", 2, 1, 1), (f"{ZC}:road_atlas", 1, 1, 1), (f"{ZC}:two_way_radio", 3, 1, 2)],
}
for _name, _extra in HAND_ADDED.items():
    TABLES[_name] = (TABLES[_name][0], TABLES[_name][1] + _extra)

# weapons wave one: a .22 or a magnum left in a boot, an SMG in the back of a SWAT van now and then
TABLES["trunk"] = (TABLES["trunk"][0], TABLES["trunk"][1] + [(f"{ZC}:rimfire_ammo", 2, 5, 25), (f"{ZC}:magnum_ammo", 1, 3, 12)])
TABLES["swat"] = (TABLES["swat"][0], TABLES["swat"][1] + [(f"{ZC}:smg", 2, 1, 1, worn(0, .4)), (f"{ZC}:smg_magazine", 3, 1, 2, rounds(30))])
RARE["trunk"] = RARE["trunk"] + [(f"{ZC}:rimfire", 2), (f"{ZC}:magnum", 1), (f"{ZC}:sawn_off", 1)]
RARE["police"] = RARE["police"] + [(f"{ZC}:smg", 1)]
def table(name):
    (lo, hi), entries = TABLES[name]
    pools = [{"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": [entry(*e) for e in entries]}]
    if name in RARE:
        pools.append({"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.04}],
                      "entries": [entry(item, w, 1, 1) for item, w in RARE[name]]})
    return {"type": "minecraft:chest", "pools": pools, "random_sequence": f"{ZC}:car/{name}"}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    for name in TABLES:
        (OUT / f"{name}.json").write_text(json.dumps(table(name), indent=2) + "\n")
    print(f"{len(TABLES)} car loot tables in {OUT}")


if __name__ == "__main__":
    main()
