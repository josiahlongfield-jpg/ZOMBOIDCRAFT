"""Loot tables for LANDSCAPE's auto shops (tools/city/autoshop.py): one
data/zomboidcraft/loot_table/shop/auto_<name>.json per fitting tagged loot=auto_<name>.

The contract (LANDSCAPE, 2026-10-31): class suffixes are VEHICLES' kinds (_standard, _sport, _heavy);
auto_{brakes,suspension,exhaust,wheels}_<kind> hold that class's parts; auto_tools, auto_electric,
auto_fluids, auto_books, auto_outfit, auto_special, auto_scrap (worn parts, 20-60%) and auto_military
(the motor pools). The key board (auto_keyboard) has no table: VEHICLES' CarKeys fills it with the keys
of the cars parked outside, and car_key is never loot.

A car part carries its id, class and (for worn ones) condition as custom data, the way VEHICLES'
Mechanics.held reads it: {car_part:'<id>', kind:'<class>', condition:<n>}. A part with no condition
is new in its box (Mechanics reads it as good order). Universal parts (battery, bulbs, radio...) fit
any class, so they go out as standard.

Every id is checked against the registered loot items; a part whose item hasn't been made yet is left
out and listed (its models wait on the REFERENCES photos), so the tables fill as the parts land. A bad
id never reaches the server (a bad id in a loot table stops it).

    python3 tools/items/loot/auto_loot.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import story_loot as S  # noqa: E402

KINDS = ('standard', 'sport', 'heavy')
# the parts VEHICLES' CarParts names, by the shelf that stocks them
SHELF = {
    'brakes': ['brake_pads', 'brake_fluid'],
    'suspension': ['suspension', 'steering_rack'],
    'exhaust': ['exhaust', 'fuel_pump', 'fuel_tank'],
    'wheels': ['car_tire', 'wheel_rim'],
}
# heavier shops stock more of the big stuff; sport shops carry fewer of each
ROLLS = {'standard': (2, 4), 'sport': (1, 3), 'heavy': (2, 5)}
PARTS = {'engine_block', 'spark_plugs', 'car_battery', 'alternator', 'starter_motor', 'radiator', 'fan_belt',
         'motor_oil', 'gearbox', 'fuel_tank', 'fuel_pump', 'exhaust', 'steering_rack', 'brake_fluid',
         'brake_pads', 'suspension', 'car_tire', 'wheel_rim', 'headlight', 'tail_light', 'car_door',
         'car_bonnet', 'boot_lid', 'car_horn', 'car_radio', 'car_seat', 'glovebox'}
# bottles, not pulled parts: never in the scrap bins
FLUIDS = {'motor_oil', 'brake_fluid'}
# Mechanics.universal: fits any class
UNIVERSAL = {'car_battery', 'spark_plugs', 'motor_oil', 'brake_fluid', 'headlight', 'tail_light', 'car_horn',
             'car_radio', 'fan_belt'}

# name: (sure [(id, min, max)], rolls (min, max), pool [(id, weight, min, max)])
TABLES = {
    'tools': ([], (2, 4), [
        ('socket_set', 4, 1, 1), ('screwdriver', 4, 1, 1), ('screwdriver_set', 2, 1, 1), ('adjustable_wrench', 4, 1, 1),
        ('tire_iron', 3, 1, 1), ('car_jack', 2, 1, 1), ('mechanic_toolbox', 1, 1, 1), ('work_gloves', 4, 1, 2),
        ('jumper_cables', 2, 1, 1), ('duct_tape', 3, 1, 1), ('rubber_hose', 2, 1, 1), ('fuel_siphon', 2, 1, 1),
        ('welding_mask', 1, 1, 1), ('welding_rods', 1, 1, 2), ('tow_strap', 1, 1, 1), ('flashlight', 2, 1, 1)]),
    'electric': ([], (2, 4), [
        ('car_battery', 3, 1, 1), ('headlight_bulb', 5, 1, 3), ('spark_plugs', 4, 1, 4), ('jumper_cables', 3, 1, 1),
        ('electrical_tape', 4, 1, 2), ('wire_spool', 2, 1, 1), ('electronics_scrap', 2, 1, 2),
        ('headlight', 2, 1, 1), ('tail_light', 2, 1, 1), ('car_horn', 2, 1, 1), ('car_radio', 1, 1, 1),
        ('alternator', 1, 1, 1), ('starter_motor', 1, 1, 1)]),
    'fluids': ([], (2, 5), [
        ('motor_oil', 6, 1, 4), ('antifreeze', 4, 1, 2), ('brake_fluid', 4, 1, 2), ('oil_filter', 4, 1, 2),
        ('hydraulic_fluid', 2, 1, 1), ('car_wax', 2, 1, 1), ('gas_can', 2, 1, 1), ('diesel_can', 1, 1, 1),
        ('radiator_hose', 3, 1, 1), ('fan_belt', 3, 1, 2), ('wiper_blades', 3, 1, 1)]),
    'books': ([], (1, 2), [
        ('mag_mechanics', 4, 1, 1), ('skillbook_mechanics', 2, 1, 1), ('auto_manual_standard', 2, 1, 1),
        ('auto_manual_heavy', 1, 1, 1), ('auto_manual_sport', 1, 1, 1), ('road_atlas', 2, 1, 1),
        ('county_map', 2, 1, 1)]),
    'outfit': ([], (1, 3), [
        ('mechanic_coveralls', 5, 1, 1), ('work_gloves', 4, 1, 1), ('work_boots', 2, 1, 1), ('welding_mask', 1, 1, 1)]),
    'special': ([], (1, 3), [
        ('mag_mechanics', 3, 1, 1), ('auto_manual_standard', 2, 1, 1), ('auto_manual_heavy', 1, 1, 1),
        ('auto_manual_sport', 1, 1, 1), ('cash_bundle', 3, 1, 2), ('coffee_thermos', 2, 1, 1),
        ('pen', 3, 1, 1), ('notebook', 3, 1, 1), ('flashlight', 2, 1, 1), ('county_map', 2, 1, 1)]),
    'military': ([], (2, 4), [
        ('socket_set', 3, 1, 1), ('adjustable_wrench', 3, 1, 1), ('tire_iron', 2, 1, 1), ('car_jack', 2, 1, 1),
        ('mechanic_toolbox', 1, 1, 1), ('work_gloves', 3, 1, 1), ('hydraulic_fluid', 3, 1, 2),
        ('tie_down_chain', 3, 1, 1), ('wheel_chock', 3, 1, 2), ('diesel_can', 2, 1, 1), ('motor_oil', 3, 1, 2),
        ('auto_manual_heavy', 1, 1, 1), ('field_wire_spool', 1, 1, 1)]),
}


def part(i, w, lo, hi, kind, condition=None):
    e = S.entry(i, w, lo, hi)
    tag = f"{{car_part:'{i}',kind:'{'standard' if i in UNIVERSAL else kind}'"
    tag += f',condition:{condition}}}' if condition is not None else '}'
    e.setdefault('functions', []).append({'function': 'minecraft:set_custom_data', 'tag': tag})
    return e


def item(i, w, lo, hi, kind='standard'):
    return part(i, w, lo, hi, kind) if i in PARTS else S.entry(i, w, lo, hi)


def write(name, pools):
    out = S.TABLES / 'shop' / f'auto_{name}.json'
    out.write_text(json.dumps({'type': 'minecraft:chest', 'pools': pools}, indent=2) + '\n')


def main():
    ok = S.known()
    waiting = set()

    def have(i):
        if i in ok:
            return True
        if i in PARTS or i.startswith('auto_manual_') or i == 'skillbook_mechanics':
            waiting.add(i)
            return False
        raise SystemExit(f'unknown id: {i}')

    n = 0
    for name, (sure, rolls, pool) in TABLES.items():
        pools = [{'rolls': 1, 'entries': [item(i, 1, lo, hi)]} for i, lo, hi in sure if have(i)]
        entries = [item(*p) for p in pool if have(p[0])]
        if entries:
            pools.append({'rolls': {'type': 'minecraft:uniform', 'min': rolls[0], 'max': rolls[1]}, 'entries': entries})
        write(name, pools)
        n += 1
    for shelf, ids in SHELF.items():
        for kind in KINDS:
            # wheels are bulky: one or two of each; the small stuff comes in boxes of a few
            entries = [part(i, 4, 1, 1 if shelf == 'wheels' else 2, kind) for i in ids if have(i)]
            lo, hi = ROLLS[kind]
            write(f'{shelf}_{kind}', [{'rolls': {'type': 'minecraft:uniform', 'min': lo, 'max': hi},
                                       'entries': entries}] if entries else [])
            n += 1
    # the core-return bin and the scrapyard's parts bins: pulled parts of every class, worn 20-60%
    scrap = [part(i, 1, 1, 1, kind, c) for i in sorted(PARTS - FLUIDS) if have(i) for kind in KINDS
             for c in (20, 30, 40, 50, 60)]
    scrap.append(S.entry('scrap_metal', len(scrap) // 3 or 4, 1, 3))
    write('scrap', [{'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 3}, 'entries': scrap}])
    n += 1
    print(f'{n} auto tables; waiting on items: {" ".join(sorted(waiting)) or "none"}')


if __name__ == '__main__':
    main()
