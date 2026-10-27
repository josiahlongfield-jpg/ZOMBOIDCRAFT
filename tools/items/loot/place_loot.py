"""Puts the wave-1 loot (food, drink, medical, hygiene) into the shop, home and car loot tables by the roster's place
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
    'car/ambulance': {'amb': 2, 'hosp': 1}, 'car/bus': {'kids': 1, 'school': 1}, 'car/fire': {'fire': 1},
    'car/glovebox': {'glove': 1}, 'car/police': {'police': 1, 'glove': 1}, 'car/swat': {'mil': 1},
    'car/trunk': {'trunk': 2, 'camp': 1}, 'car/work': {'worktruck': 2, 'build': 1},
    'shop/basement_food': {'bunker': 2, 'pantry': 1}, 'shop/basement_medicine': {'bunker': 1, 'bath': 1},
    'shop/basement_supplies': {'bunker': 1, 'camp': 1}, 'shop/drinks': {'conv': 1}, 'shop/farm': {'farm': 1, 'vet': 1},
    'shop/fire': {'fire': 1}, 'shop/grocery': {'groc': 1}, 'shop/home_bathroom': {'bath': 1},
    'shop/home_cupboard': {'pantry': 1}, 'shop/home_desk': {'desk': 1}, 'shop/home_freezer': {'freez': 1},
    'shop/home_fridge': {'fridge': 1}, 'shop/home_garage': {'garage': 1}, 'shop/home_laundry': {'laund': 1},
    'shop/home_medicine': {'bath': 1}, 'shop/home_nightstand': {'night': 1}, 'shop/home_pantry': {'pantry': 1},
    'shop/home_sink': {'laund': 1, 'garage': 1}, 'shop/home_trash': {'trash': 1}, 'shop/home_toys': {'kids': 1},
    'shop/home_boxes': {'attic': 1}, 'shop/home_dresser': {'bed': 1}, 'shop/home_wardrobe': {'bed': 1},
    'shop/kitchen': {'diner': 1}, 'shop/laundromat': {'laund': 1}, 'shop/liquor': {'liq': 1},
    'shop/office': {'office': 1}, 'shop/pharmacy': {'pharm': 1}, 'shop/police': {'police': 1},
    'shop/school': {'school': 1}, 'shop/school_gym': {'gym': 1}, 'shop/scraps': {'trash': 1},
    'shop/storage': {'attic': 1, 'factory': 1}, 'shop/theatre_candy': {'candy': 1}, 'shop/thrift': {'thrift': 1},
}
# things a table stocks that no place tag says (an oven holds what was cooking; sinks keep the cleaning things)
EXTRA = {
    'shop/home_oven': ['leftovers', 'pie_slice', 'frozen_pizza', 'frozen_dinner'],
    'shop/home_sink': ['bleach', 'dish_soap', 'sponge', 'trash_bags'],
    'shop/home_drawer': ['can_opener', 'safety_pins', 'painkillers', 'adhesive_bandages'],
    'shop/home_cupboard': ['can_opener'],
    'shop/mail': ['fancy_chocolates'],
}
# the vanilla stand-ins wave 1 replaces, wherever they are
REPLACED = {f'minecraft:{n}' for n in (
    'potion', 'honey_bottle', 'milk_bucket', 'cookie', 'bread', 'dried_kelp', 'sugar', 'apple', 'egg', 'carrot',
    'potato', 'baked_potato', 'pumpkin_pie', 'sweet_berries', 'melon_slice', 'beetroot', 'cooked_beef',
    'cooked_porkchop', 'cooked_chicken', 'cooked_mutton', 'beef', 'porkchop', 'chicken', 'mutton', 'cod', 'salmon',
    'mushroom_stew', 'beetroot_soup', 'rabbit_stew', 'cake', 'cocoa_beans', 'golden_apple', 'golden_carrot',
    'enchanted_golden_apple', 'rotten_flesh', 'sponge')}
KEEP_ON_FARM = {'minecraft:potato', 'minecraft:carrot', 'minecraft:beetroot', 'minecraft:egg', 'minecraft:apple'}
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
        if w:
            out[r['id']] = (r, w * RW[r['rarity'][0]])
    for iid in EXTRA.get(table, ()):
        r = next(x for x in rows if x['id'] == iid)
        out.setdefault(iid, (r, 2))
    return list(out.values())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry', action='store_true')
    a = ap.parse_args()
    rows = [r for r in gen_java.rows() if r['wave1']]
    mine = {f'{NS}:{r["id"]}' for r in rows}
    for path in sorted(TABLES.glob('*/*.json')):
        table = f'{path.parent.name}/{path.stem}'
        if path.parent.name not in ('shop', 'car'):
            continue
        data = json.loads(path.read_text())
        pools = data.get('pools', [])
        if not pools:
            continue
        drop = REPLACED - (KEEP_ON_FARM if table == 'shop/farm' else set())
        removed = 0
        for pool in pools:
            keep = []
            for e in pool.get('entries', []):
                name = e.get('name')
                if name in mine or name in drop:
                    if name in drop:
                        removed += e.get('weight', 1)
                    continue
                keep.append(e)
            pool['entries'] = keep
        new = stock(table, rows)
        if not new and not removed:
            continue
        common = [(r, w) for r, w in new if r['rarity'][0] in 'CS']
        rare = [(r, w) for r, w in new if r['rarity'][0] not in 'CS']
        total = sum(w for _r, w in common)
        # the new stock takes the share of the table the stand-ins had: the rest is scaled up to match
        main_pool = pools[0]
        if removed and total > removed:
            k = round(total / removed)
            for e in main_pool['entries']:
                e['weight'] = e.get('weight', 1) * k
        main_pool['entries'] += [entry(r, w) for r, w in common]
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
