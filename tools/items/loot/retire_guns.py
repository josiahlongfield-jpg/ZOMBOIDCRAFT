"""Takes our pre-Brita guns, attachments, plain magazines and dead rounds out of every loot table (Josiah 2026-11-02).

The Heavy Sniper comes out too: combat/GunLoot puts it (and the Brita guns) in by Arsenal's places instead. Idempotent:
run it again after anyone regenerates a table. Ids mirror combat/Retired.java.
"""
import glob, json, os, sys

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'src', 'main', 'resources', 'data', 'zomboidcraft', 'loot_table')
GUNS = "assault_rifle pistol shotgun hunting_rifle double_barrel revolver rimfire smg sawn_off magnum lever_action crossbow battle_rifle pocket_pistol machine_pistol suppressed_rifle precision_rifle grenade_launcher".split()
ATT = "red_dot_sight holo_sight mini_red_dot compact_red_dot scope_4x combat_scope rifle_scope pistol_scope precision_scope hunting_scope crossbow_scope magnifier foregrip angled_grip stubby_grip skeleton_grip tactical_grip underbarrel_launcher weapon_light compact_light laser_sight rifle_suppressor pistol_suppressor rifle_stock".split()
MAGS = "rifle_magazine pistol_magazine hunting_rifle_magazine rimfire_magazine smg_magazine battle_rifle_magazine pocket_pistol_magazine machine_pistol_magazine precision_rifle_magazine".split()
ROUNDS = ["lever_ammo", "grenade_round"]
GONE = {f"zomboidcraft:{i}" for i in GUNS + ATT + MAGS + ROUNDS + ["heavy_sniper"]}


def strip(entries):
    out, n = [], 0
    for e in entries:
        if e.get('name') in GONE:
            n += 1
            continue
        if 'children' in e:
            e['children'], k = strip(e['children'])
            n += k
            if not e['children']:
                continue
        out.append(e)
    return out, n


def main():
    total = 0
    for f in sorted(glob.glob(os.path.join(ROOT, '**', '*.json'), recursive=True)):
        d = json.load(open(f))
        n = 0
        for pool in d.get('pools', []):
            pool['entries'], k = strip(pool.get('entries', []))
            n += k
            if not pool['entries']:
                pool['entries'] = [{'type': 'minecraft:empty', 'weight': 1}]
        if n:
            with open(f, 'w') as fh:
                json.dump(d, fh, indent=2)
                fh.write('\n')
            print(f'{os.path.relpath(f, ROOT)}: -{n}')
            total += n
    print('removed', total, 'entries')


if __name__ == '__main__':
    main()
