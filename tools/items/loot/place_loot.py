"""Puts the live loot waves (1: food, drink, medical, hygiene; 2: kitchen kit, tools, materials, electrical, light,
camping and farm, car parts) into the shop, home and car loot tables by the roster's place
tags, in place of the vanilla stand-ins (potions, bread, cookies, golden apples...). Everything else in a table,
including other threads' entries, is left as it is. Safe to run again: it takes out its own entries first.

    python3 tools/items/loot/place_loot.py [--dry]

Edits the JSON in place (never run tools/city/shop_loot.py to do this: it rewrites every table and wipes other
threads' hand-added loot).
"""
import argparse
import json
from pathlib import Path

import gen_java

ROOT = Path(__file__).resolve().parents[3]
TABLES = ROOT / 'src/main/resources/data/zomboidcraft/loot_table'
NS = 'zomboidcraft'

# table -> the place tags it stocks (tag: weight multiplier)
WHERE = {
    'car/ambulance': {'amb': 2, 'hosp': 1}, 'car/bus': {'kids': 1, 'school': 1, 'bus': 1}, 'car/fire': {'fire': 1, 'firetruck': 1},
    'car/glovebox': {'glove': 1}, 'car/police': {'police': 1, 'glove': 1, 'pcar': 1}, 'car/swat': {'mil': 1},
    'car/trunk': {'trunk': 2, 'camp': 1, 'auto': 1}, 'car/work': {'worktruck': 2, 'build': 1, 'hard': 1},
    'shop/basement_food': {'bunker': 2, 'pantry': 1}, 'shop/basement_medicine': {'bunker': 1, 'bath': 1},
    'shop/basement_supplies': {'bunker': 1, 'camp': 1}, 'shop/drinks': {'conv': 1}, 'shop/farm': {'farm': 1, 'vet': 1},
    'shop/fire': {'fire': 1}, 'shop/grocery': {'groc': 1}, 'shop/home_bathroom': {'bath': 1},
    'shop/home_cupboard': {'pantry': 1}, 'shop/home_desk': {'desk': 1}, 'shop/home_freezer': {'freez': 1},
    'shop/home_fridge': {'fridge': 1}, 'shop/home_garage': {'garage': 1, 'auto': 1}, 'shop/home_laundry': {'laund': 1},
    'shop/home_medicine': {'bath': 1}, 'shop/home_nightstand': {'night': 1}, 'shop/home_pantry': {'pantry': 1},
    'shop/home_sink': {'laund': 1, 'garage': 1}, 'shop/home_trash': {'trash': 1}, 'shop/home_toys': {'kids': 1, 'toyshop': 1},
    'shop/home_boxes': {'attic': 1}, 'shop/home_dresser': {'bed': 1}, 'shop/home_wardrobe': {'bed': 1},
    'shop/kitchen': {'diner': 1}, 'shop/laundromat': {'laund': 1}, 'shop/liquor': {'liq': 1},
    'shop/office': {'office': 1}, 'shop/pharmacy': {'pharm': 1}, 'shop/police': {'police': 1},
    'shop/school': {'school': 1}, 'shop/school_gym': {'gym': 1, 'stadium': 1}, 'shop/scraps': {'trash': 1},
    'shop/storage': {'attic': 1, 'factory': 1, 'dock': 1},
    'shop/hardware': {'hard': 2, 'build': 1}, 'shop/home_tools': {'garage': 1, 'hard': 1},
    'shop/pawn': {'pawn': 2, 'elec': 1, 'jewel': 1},
    'shop/home_books': {'books': 2, 'desk': 1}, 'shop/bank': {'bank': 1}, 'shop/till': {'till': 1},
    'shop/cash': {'bank': 1, 'till': 1}, 'shop/mail': {'post': 1}, 'shop/guns': {'gun': 1}, 'shop/theatre_candy': {'candy': 1}, 'shop/thrift': {'thrift': 1}, 'shop/clothes': {'cloth': 1},
}
# things a table stocks that no place tag says (an oven holds what was cooking; sinks keep the cleaning things)
EXTRA = {
    'shop/home_oven': ['leftovers', 'pie_slice', 'frozen_pizza', 'frozen_dinner'],
    'shop/home_sink': ['bleach', 'dish_soap', 'sponge', 'trash_bags'],
    'shop/home_drawer': ['can_opener', 'safety_pins', 'painkillers', 'adhesive_bandages'],
    'shop/home_cupboard': ['can_opener'],
    'shop/mail': ['fancy_chocolates'],
    'shop/pawn': ['portable_tv'], 'shop/home_boxes': ['church_candle'], 'shop/home_garage': ['radiator_hose'],
    'car/work': ['tie_down_chain'], 'shop/clothes': ['thread_spool'], 'shop/guns': ['paracord'],
    # the hotel penthouse terrace grill and outdoor kitchen, and the nightclub DJ booth (floor fit-out thread)
    'shop/grill': ['charcoal', 'lighter_fluid', 'matches', 'lighter', 'spatula', 'cast_iron_skillet', 'aluminium_foil',
                   'paper_towels', 'hot_dogs', 'steak', 'canned_beer', 'bottled_beer'],
    'shop/dj': ['cassette_tape', 'mixtape', 'headphones', 'champagne', 'vodka', 'cigarettes', 'cash_bundle', 'glow_stick',
                'lighter'],
}
# the vanilla stand-ins the live waves replace, wherever they are. Vanilla things that still do something our items
# don't yet (bucket, shears, seeds, saddle, book and quill, name tag, music discs, totems, paintings, bones) stay until
# ours work; torches, lanterns, compasses, clocks and flint and steel went once ours lit, struck and told the time
REPLACED = {f'minecraft:{n}' for n in (
    'potion', 'honey_bottle', 'milk_bucket', 'cookie', 'bread', 'dried_kelp', 'sugar', 'apple', 'egg', 'carrot',
    'potato', 'baked_potato', 'pumpkin_pie', 'sweet_berries', 'melon_slice', 'beetroot', 'cooked_beef',
    'cooked_porkchop', 'cooked_chicken', 'cooked_mutton', 'beef', 'porkchop', 'chicken', 'mutton', 'cod', 'salmon',
    'mushroom_stew', 'beetroot_soup', 'rabbit_stew', 'cake', 'cocoa_beans', 'golden_apple', 'golden_carrot',
    'enchanted_golden_apple', 'rotten_flesh', 'sponge',
    'string', 'stick', 'oak_planks', 'coal', 'lead', 'candle', 'bowl',
    'paper', 'book', 'gold_nugget', 'iron_nugget', 'emerald', 'diamond', 'gold_ingot', 'amethyst_shard', 'ink_sac',
    'red_dye', 'blue_dye', 'yellow_dye', 'slime_ball',
    'torch', 'lantern', 'soul_lantern', 'soul_torch', 'compass', 'clock', 'flint_and_steel',
    # no purpose here: fantasy, vanilla-only mechanics, building blocks dropped in as loot (Josiah 2026-10-29)
    'totem_of_undying', 'name_tag', 'saddle', 'goat_horn', 'bone_meal', 'snowball', 'ice', 'flower_pot', 'red_carpet',
    'iron_bars', 'oak_trapdoor', 'ladder', 'chest', 'item_frame',
    # ours do these: bottles, rags/sheets/thread, maps, notebooks, seeds and produce, buckets, shears, binoculars...
    'glass_bottle', 'white_wool', 'light_gray_wool', 'gray_wool', 'blue_wool', 'brown_wool', 'black_wool', 'map',
    'writable_book', 'wheat', 'wheat_seeds', 'pumpkin_seeds', 'melon_seeds', 'beetroot_seeds', 'water_bucket',
    'bucket', 'shears', 'spyglass', 'bone', 'painting', 'leather', 'shield', 'gunpowder')}
# an entry another thread customised (a named note, a marked map, a set count of something special) is theirs, not a
# stand-in, even when it's a stand-in item
CUSTOM = {'minecraft:set_components', 'minecraft:set_name', 'minecraft:set_lore', 'minecraft:set_custom_data',
          'minecraft:exploration_map', 'minecraft:copy_components'}
# vanilla weapons and helmets the mod already has its own of: the entry keeps its weight and rolls, as ours
SWAP = {f'minecraft:{a}': f'{NS}:{b}' for a, b in (
    ('iron_sword', 'machete'), ('iron_axe', 'fire_axe'), ('iron_pickaxe', 'crowbar'), ('stone_hoe', 'shovel'),
    ('iron_hoe', 'shovel'), ('bow', 'crossbow'), ('crossbow', 'crossbow'), ('arrow', 'crossbow_bolt'),
    ('iron_helmet', 'combat_helmet'), ('golden_helmet', 'combat_helmet'),
    # clothes from the clothing thread (batch 1) and the plate carrier take the armour stand-ins' places
    ('leather_chestplate', 'denim_jacket'), ('leather_leggings', 'jeans'), ('leather_boots', 'work_boots'),
    ('leather_helmet', 'baseball_cap'), ('iron_chestplate', 'plate_carrier'), ('diamond_chestplate', 'plate_carrier'),
    ('iron_leggings', 'work_pants'), ('iron_boots', 'work_boots'))}
# the clothing thread's garments that are registered with worn models: placed by their roster places like ours
WORN_READY = {'t_shirt', 'flannel_shirt', 'hoodie', 'denim_jacket', 'parka', 'jeans', 'work_pants', 'sneakers',
              'work_boots', 'baseball_cap', 'beanie', 'work_gloves', 'sunglasses', 'bandana'} | set('''
    trucker_cap cowboy_hat bucket_hat hard_hat bike_helmet football_helmet hockey_mask motorcycle_helmet
    police_cap fire_helmet riot_helmet gas_mask welding_goggles aviator_sunglasses reading_glasses balaclava
    surgical_mask_worn cranial_helmet pilot_helmet band_tshirt tank_top dress_shirt polo_shirt hawaiian_shirt
    sweater sweatshirt turtleneck scrubs_top dress wedding_dress leather_jacket bomber_jacket
    varsity_jacket windbreaker raincoat trench_coat suit_jacket lab_coat hi_vis_vest
    cargo_pants camo_pants slacks sweatpants shorts skirt overalls scrubs_pants
    leather_chaps running_shoes dress_shoes cowboy_boots hiking_boots rubber_boots combat_boots slippers
    leather_gloves winter_gloves rubber_gloves tactical_gloves scarf necktie
    flight_deck_jersey police_jacket fire_coat field_jacket ghillie_suit ww2_flight_jacket fur_coat bespoke_suit
    poncho flak_vest police_vest survival_vest fire_gloves fire_pants g_suit
    purse briefcase gym_bag doctors_bag plastic_bag paper_bag suitcase lunchbox cooler mail_bag tool_bag
    ammo_pouch_belt
    chest_rig load_bearing_vest medic_vest bandolier heavy_plate_carrier high_cut_helmet steel_pot_helmet tanker_helmet
    boonie_hat patrol_cap beret ballistic_glasses ballistic_goggles shemagh neck_gaiter elbow_pads knee_pads field_shirt
    combat_trousers tactical_boots denim_strips leather_strips
    fur_ushanka watch_cap cbrn_mask cbrn_suit combat_shirt fleece_liner wet_weather_jacket flight_coverall sniper_smock arctic_parka snow_overwhites jungle_boots desert_boots arctic_boots flight_gloves shooting_gloves arctic_mittens riot_arm_guards riot_leg_guards wool_commando_sweater shooting_earmuffs chainsaw_chaps beekeeper_suit cut_resistant_gloves shin_guards gaiters football_shoulder_pads hockey_shoulder_pads hockey_neck_guard leather_neck_guard padded_football_pants hockey_pants catchers_vest hockey_gloves chainmail_butcher_glove chainmail_sleeve athletic_cup'''.split())  # clothing batches 2 to 5, then the UI thread's 12 bags
KEEP_ON_FARM = set()   # the farm's produce is ours now too
# tables whose main business is the live waves' stock: it gets at least this share of the main pool, not only the share
# the stand-ins happened to have
MIN_SHARE = {'shop/hardware': 0.7, 'shop/home_tools': 0.6, 'shop/home_garage': 0.5, 'car/work': 0.5,
             'car/trunk': 0.3, 'shop/storage': 0.4, 'shop/farm': 0.4, 'shop/kitchen': 0.4, 'shop/pawn': 0.3}
# worn gear the UI thread registers (ModItems/Weights, not the roster): table -> (id, rarity, weight)
GEAR = {
    'shop/clothes': [('leather_belt', 'C', 6)], 'shop/home_wardrobe': [('leather_belt', 'C', 4)],
    'shop/home_dresser': [('leather_belt', 'C', 4)], 'shop/thrift': [('leather_belt', 'C', 4)],
    'shop/laundromat': [('leather_belt', 'C', 2)],
    'shop/hardware': [('tool_belt', 'C', 6)], 'shop/home_garage': [('tool_belt', 'S', 3), ('machete_sheath', 'R', 1)],
    'shop/home_tools': [('tool_belt', 'S', 4)], 'car/work': [('tool_belt', 'C', 4)],
    'shop/police': [('duty_belt', 'C', 6), ('hip_holster', 'S', 4), ('shoulder_holster', 'R', 2)],
    'car/police': [('duty_belt', 'S', 3), ('hip_holster', 'S', 2)],
    'car/swat': [('duty_belt', 'S', 3), ('thigh_holster', 'R', 2)],
    'shop/guns': [('hip_holster', 'S', 4), ('shoulder_holster', 'S', 3), ('thigh_holster', 'R', 1)],
    'shop/home_guns': [('hip_holster', 'S', 3), ('shoulder_holster', 'R', 1)],
    'shop/basement_guns': [('hip_holster', 'S', 2), ('thigh_holster', 'R', 2)],
    'shop/pawn': [('shoulder_holster', 'R', 1), ('knife_sheath', 'S', 1)],
    'shop/kitchen': [('knife_sheath', 'R', 1)], 'shop/basement_supplies': [('knife_sheath', 'S', 2)],
    'car/trunk': [('knife_sheath', 'R', 1)],
    'shop/farm': [('machete_sheath', 'S', 3)], 'shop/storage': [('machete_sheath', 'R', 1)],
}
# worn gear still waiting on its model (it would show as the missing texture): kept out until the UI thread says
GEAR_NO_MODEL = set()   # all of UI's worn gear has body models now (2026-10-29)
# roster rows the worn gear above stands in for
NOT_PLACED = {'hunting_knife_sheath'}
RW = {'C': 4, 'S': 2, 'R': 1, 'P': 1, 'G': 1}


def entry(r, w):
    e = {'type': 'minecraft:item', 'name': f'{NS}:{r["id"]}', 'weight': max(1, int(round(w)))}
    if r['rarity'][0] == 'C' and gen_java.stack(r['kg']) >= 8:
        e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}}]
    return e


def stock(table, rows):
    tags = WHERE.get(table, {})
    out = {}
    for r in rows:
        w = sum(pw * tags[t] for t, pw in r['places'] if t in tags)
        if w and r['id'] not in NOT_PLACED:
            out[r['id']] = (r, w * RW[r['rarity'][0]])
    for iid in EXTRA.get(table, ()):
        r = next(x for x in rows if x['id'] == iid)
        out.setdefault(iid, (r, 2))
    for iid, rar, w in GEAR.get(table, ()):
        if iid in GEAR_NO_MODEL:
            continue
        out[iid] = (dict(id=iid, rarity=rar, kg=1.0), w * RW[rar])
    return list(out.values())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry', action='store_true')
    a = ap.parse_args()
    rows = [r for r in gen_java.rows() if r['live'] or r['id'] in WORN_READY]
    mine = {f'{NS}:{r["id"]}' for r in rows} | {f'{NS}:{i}' for g in GEAR.values() for i, _r, _w in g}
    for path in sorted(TABLES.glob('*/*.json')):
        table = f'{path.parent.name}/{path.stem}'
        if path.parent.name not in ('shop', 'car'):
            continue
        data = json.loads(path.read_text())
        pools = data.get('pools', [])
        if not pools:
            continue
        drop = REPLACED - (KEEP_ON_FARM if table == 'shop/farm' else set())
        removed = freed = 0
        for pi, pool in enumerate(pools):
            keep = []
            for e in pool.get('entries', []):
                name = e.get('name')
                if name in SWAP:
                    e['name'] = name = SWAP[name]
                custom = any(fn.get('function') in CUSTOM for fn in e.get('functions', ()))
                if name in mine or (name in drop and not custom):
                    if name in drop:
                        removed += e.get('weight', 1)
                    if pi == 0:
                        freed += e.get('weight', 1)
                    continue
                keep.append(e)
            pool['entries'] = keep
        new = stock(table, rows)
        if not new and not removed:
            continue
        common = [(r, w) for r, w in new if r['rarity'][0] in 'CS']
        rare = [(r, w) for r, w in new if r['rarity'][0] not in 'CS']
        total = sum(w for _r, w in common)
        # the new stock takes the share of the main pool that the stand-ins and our earlier entries had (or the
        # table's MIN_SHARE): the other side is scaled up to match, so running again changes nothing
        main_pool = pools[0]
        old = sum(e.get('weight', 1) for e in main_pool['entries'])
        share = max(freed / (old + freed) if freed else 0, MIN_SHARE.get(table, 0))
        mult = 1
        if share and total and old:
            want = total * (1 - share) / share
            if want > old:
                k = round(want / old)
                for e in main_pool['entries']:
                    e['weight'] = e.get('weight', 1) * k
            else:
                mult = max(1, round(old / want))
        main_pool['entries'] += [entry(r, w * mult) for r, w in common]
        target = pools[1] if len(pools) > 1 else main_pool
        target['entries'] += [entry(r, w) for r, w in rare]
        for pool in pools:
            if not pool['entries']:
                pool['entries'] = [{'type': 'minecraft:empty', 'weight': 1}]
        print(f'{table}: -{removed} weight of stand-ins, +{len(new)} items')
        if not a.dry:
            path.write_text(json.dumps(data, indent=2) + '\n')


if __name__ == '__main__':
    main()
