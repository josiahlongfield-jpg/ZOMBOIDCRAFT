"""Our old guns' audio goes with them (Josiah 2026-11-02). Each retired weapon.* event keeps its key with no sounds, so
the code that still names it plays nothing and the game logs no missing event; its ogg files leave the jar unless a
kept event still uses them. Kept: the Heavy Sniper (its shots, and the hunting rifle dry/mag/bolt sounds it borrows),
the rifle mag/dry/load sounds the Warthog turret, the attachment bench and magazine loading use, casings, bullets,
melee, throwing and the minigun."""
import json, os, sys

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'src', 'main', 'resources', 'assets', 'zomboidcraft')
KEEP_PREFIX = ('weapon.heavy_sniper.', 'weapon.minigun.', 'weapon.bullet', 'weapon.throw', 'weapon.melee', 'weapon.casing')
KEEP = {'weapon.hunting_rifle.dry', 'weapon.hunting_rifle.mag_out', 'weapon.hunting_rifle.mag_in', 'weapon.hunting_rifle.bolt',
        'weapon.rifle.dry', 'weapon.rifle.mag_out', 'weapon.rifle.mag_in', 'weapon.rifle.load_round'}


def name(s):
    return s if isinstance(s, str) else s['name']


def main():
    path = os.path.join(ROOT, 'sounds.json')
    d = json.load(open(path))
    gone = [k for k in d if k.startswith('weapon.') and k not in KEEP and not k.startswith(KEEP_PREFIX)]
    used_by_kept = {name(s) for k, v in d.items() if k not in gone for s in v.get('sounds', [])}
    files = set()
    for k in gone:
        files |= {name(s) for s in d[k].get('sounds', [])}
        d[k] = {'sounds': []}
    removed = 0
    for f in sorted(files - used_by_kept):
        p = os.path.join(ROOT, 'sounds', f.split(':', 1)[1] + '.ogg')
        if os.path.exists(p):
            os.remove(p)
            removed += 1
    json.dump(d, open(path, 'w'), indent=2)
    open(path, 'a').write('\n')
    print(len(gone), 'events blanked,', removed, 'oggs removed')
    if '-v' in sys.argv:
        print('\n'.join(gone))


main()
