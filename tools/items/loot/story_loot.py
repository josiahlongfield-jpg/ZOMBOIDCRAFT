"""Loot tables for LANDSCAPE's world stories (Zomboid's road, zone and house stories): one
data/zomboidcraft/loot_table/shop/story_<name>.json per scene, for the crates tagged loot=story_<name>.

Each table is a few pools: SURE items always there (min-max count), then ROLLS picks from the weighted
pool. Every id is checked against the registered loot items and the ids already in the loot tables, so a
bad id fails here and never reaches the server (a bad id in a loot table stops it).

    python3 tools/items/loot/story_loot.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TABLES = ROOT / 'src/main/resources/data/zomboidcraft/loot_table'
SPECS = ROOT / 'src/main/java/com/zomboidcraft/loot/items/LootSpecs.java'

# name: (sure [(id, min, max)], rolls (min, max), pool [(id, weight, min, max)])
STORIES = {
    'ambulance_crash': ([('first_aid_kit', 1, 1)], (3, 5), [
        ('bandage', 6, 1, 3), ('sterile_bandage', 4, 1, 2), ('splint', 3, 1, 1), ('painkillers', 4, 1, 1),
        ('gauze_pads', 4, 1, 3), ('medical_tape', 3, 1, 1), ('antiseptic_wipes', 3, 1, 2), ('tourniquet', 2, 1, 1),
        ('surgical_gloves', 3, 1, 2), ('medical_scissors', 2, 1, 1), ('strong_painkillers', 1, 1, 1),
        ('hemostatic_gauze', 1, 1, 1), ('crutches', 1, 1, 1)]),
    'bandit_road': ([('suitcase', 1, 1)], (3, 5), [
        ('cash_bundle', 3, 1, 3), ('wallet', 3, 1, 1), ('chips_bag', 4, 1, 1), ('granola_bar', 4, 1, 2),
        ('water_bottle', 4, 1, 2), ('denim_jacket', 2, 1, 1), ('leather_jacket', 1, 1, 1), ('work_pants', 2, 1, 1),
        ('county_map', 2, 1, 1), ('snack_cakes', 3, 1, 1), ('chocolate_bar', 3, 1, 1)]),
    'burnt_car': ([], (0, 1), [
        ('scrap_metal', 4, 1, 2), ('lighter', 2, 1, 1), ('cash_bundle', 1, 1, 1)]),
    'car_crash': ([], (2, 3), [
        ('wallet', 3, 1, 1), ('cash_bundle', 2, 1, 2), ('chips_bag', 3, 1, 1), ('water_bottle', 3, 1, 1),
        ('street_map', 2, 1, 1), ('painkillers', 2, 1, 1), ('sunglasses', 2, 1, 1), ('lighter', 2, 1, 1),
        ('magazine', 2, 1, 1)]),
    'car_crash_corpse': ([], (2, 4), [
        ('wallet', 3, 1, 1), ('street_map', 3, 1, 1), ('county_map', 2, 1, 1), ('chips_bag', 3, 1, 1),
        ('candy_box', 2, 1, 1), ('granola_bar', 3, 1, 2), ('water_bottle', 3, 1, 1), ('lighter', 2, 1, 1),
        ('house_key', 2, 1, 1), ('painkillers', 1, 1, 1)]),
    'changing_tire': ([('car_jack', 1, 1), ('tire_iron', 1, 1)], (1, 2), [
        ('spare_tire', 3, 1, 1), ('work_gloves', 3, 1, 1), ('flashlight', 2, 1, 1), ('road_flare', 2, 1, 2),
        ('duct_tape', 2, 1, 1), ('water_bottle', 2, 1, 1)]),
    'construction_site': ([], (3, 5), [
        ('sledgehammer', 2, 1, 1), ('shovel', 3, 1, 1), ('crowbar', 2, 1, 1), ('hard_hat', 4, 1, 1),
        ('work_gloves', 4, 1, 1), ('rope', 3, 1, 1), ('hi_vis_vest', 3, 1, 1), ('traffic_cone', 2, 1, 2),
        ('hammer', 3, 1, 1), ('nails', 3, 4, 12), ('tape_measure', 2, 1, 1), ('work_boots', 1, 1, 1),
        ('coffee_thermos', 2, 1, 1), ('tarp', 2, 1, 1)]),
    'crash_horde': ([], (1, 2), [
        ('wallet', 3, 1, 1), ('cash_bundle', 2, 1, 2), ('water_bottle', 3, 1, 1), ('chips_bag', 2, 1, 1),
        ('painkillers', 2, 1, 1), ('lighter', 2, 1, 1)]),
    'flipped_crash': ([], (2, 3), [
        ('wallet', 3, 1, 1), ('sunglasses', 2, 1, 1), ('lighter', 2, 1, 1), ('magazine', 2, 1, 1),
        ('chips_bag', 3, 1, 1), ('water_bottle', 3, 1, 1), ('painkillers', 2, 1, 1), ('house_key', 2, 1, 1)]),
    'police_blockade': ([('road_flare', 2, 4)], (3, 5), [
        ('flashlight', 3, 1, 1), ('big_flashlight', 2, 1, 1), ('two_way_radio', 3, 1, 1), ('pistol_ammo', 4, 6, 15),
        ('shotgun_shell', 3, 4, 10), ('handcuffs', 2, 1, 1), ('zip_ties', 2, 1, 3), ('police_cap', 2, 1, 1),
        ('whistle', 2, 1, 1), ('traffic_cone', 2, 1, 2), ('bandage', 2, 1, 2)]),
    'police_blockade_shooting': ([('pistol_ammo', 6, 18)], (3, 5), [
        ('shotgun_shell', 4, 4, 12), ('pistol', 2, 1, 1), ('pistol_magazine', 3, 1, 1), ('police_vest', 2, 1, 1),
        ('road_flare', 3, 1, 3), ('two_way_radio', 2, 1, 1), ('flashlight', 2, 1, 1), ('shotgun', 1, 1, 1),
        ('tourniquet', 2, 1, 1), ('bandage', 3, 1, 2), ('handcuffs', 1, 1, 1)]),
    'trailer_crash': ([], (4, 6), [
        ('carrot_seeds', 3, 1, 2), ('corn_seeds', 3, 1, 2), ('beet_seeds', 2, 1, 2), ('lettuce_seeds', 2, 1, 2),
        ('seed_potatoes', 2, 1, 3), ('fertilizer', 2, 1, 1), ('shovel', 2, 1, 1), ('hammer', 2, 1, 1),
        ('rope', 3, 1, 1), ('tarp', 3, 1, 1), ('canned_beans', 3, 1, 3), ('canned_soup', 3, 1, 3),
        ('toolbox', 1, 1, 1), ('duffel_bag', 1, 1, 1), ('work_gloves', 2, 1, 1)]),
    'utility_vehicle': ([('mag_generators', 1, 1)], (3, 5), [
        ('screwdriver', 3, 1, 1), ('pliers', 3, 1, 1), ('wire_cutters', 3, 1, 1), ('electrical_tape', 4, 1, 2),
        ('wire_spool', 3, 1, 1), ('electronics_scrap', 4, 1, 3), ('generator_parts', 1, 1, 1),
        ('hard_hat', 2, 1, 1), ('hi_vis_vest', 2, 1, 1), ('work_gloves', 3, 1, 1), ('flashlight', 2, 1, 1),
        ('skillbook_electrical', 1, 1, 1), ('extension_cord', 2, 1, 1)]),
}


# registered outside the loot roster (ModItems: the throwables; the clothing thread's wearables)
EXTRA = {'road_flare'}
CLOTHES = {p.stem for p in (ROOT / 'src/main/resources/assets/zomboidcraft/items').glob('*.json')}


# whole sets kept together: every piece, every time (a museum's armour case, the crusader kit's resting place)
SETS = {
    'crusader_kit': ['crusader_great_helm', 'crusader_plate', 'crusader_tabard', 'crusader_pauldrons',
                     'crusader_cloak', 'crusader_greaves', 'crusader_gauntlets', 'crusader_sabatons'],
    'museum_armour': ['gothic_sallet', 'gothic_cuirass', 'gothic_arm_harness', 'gothic_leg_harness',
                      'gothic_gauntlets', 'gothic_sabatons'],
}


def known():
    ids = set(EXTRA) | set(re.findall(r'new LootSpec\("([a-z0-9_]+)", "[a-z_]+", Rarity\.\w+, \d+, [\d.]+, \d+, true', SPECS.read_text()))
    for f in TABLES.rglob('*.json'):
        if f.name.startswith('story_'):
            continue
        ids |= set(re.findall(r'"zomboidcraft:([a-z0-9_]+)"', f.read_text()))
    return ids


def entry(i, w, lo, hi):
    e = {'type': 'minecraft:item', 'name': f'zomboidcraft:{i}', 'weight': w}
    if hi > 1:
        e['functions'] = [{'function': 'minecraft:set_count',
                           'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi} if hi > lo else lo}]
    return e


def main():
    ok = known()
    bad = sorted({i for s, r, p in STORIES.values() for i in [x[0] for x in s] + [x[0] for x in p]} - ok)
    if bad:
        raise SystemExit(f'unknown ids: {bad}')
    bad = sorted({i for v in SETS.values() for i in v} - ok - CLOTHES)
    if bad:
        raise SystemExit(f'unknown ids: {bad}')
    for name, pieces in SETS.items():
        out = TABLES / 'shop' / f'{name}.json'
        out.write_text(json.dumps({'type': 'minecraft:chest', 'pools': [
            {'rolls': 1, 'entries': [entry(i, 1, 1, 1)]} for i in pieces]}, indent=2) + '\n')
    for name, (sure, rolls, pool) in STORIES.items():
        pools = [{'rolls': 1, 'entries': [entry(i, 1, lo, hi)]} for i, lo, hi in sure]
        if rolls[1] > 0:
            pools.append({'rolls': {'type': 'minecraft:uniform', 'min': rolls[0], 'max': rolls[1]},
                          'entries': [entry(*p) for p in pool]})
        out = TABLES / 'shop' / f'story_{name}.json'
        out.write_text(json.dumps({'type': 'minecraft:chest', 'pools': pools}, indent=2) + '\n')
    print(f'{len(STORIES)} story tables, {len(SETS)} sets')


if __name__ == '__main__':
    main()
