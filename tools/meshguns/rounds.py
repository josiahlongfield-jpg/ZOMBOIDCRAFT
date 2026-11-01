"""The ammo items as single rounds (Josiah 2026-11-01: no cartons at all; every calibre is Arsenal's own round on his
PC, toon-painted, and our own round in the shared jar for everyone else).

Ours are turned from each cartridge's real measurements (mm): a profile from the base to the tip as (z, radius,
paint) rings, spun 24 ways round and painted from a small palette, then packed like the bought meshes (pack.py, box
fit onto the old carton model so the item keeps its display transforms) as meshes/ammo/<item>.zmesh, and each item
definition draws it (skin.centred).

    python3 tools/meshguns/rounds.py                    our rounds into the mod, and the item definitions
    python3 tools/meshguns/rounds.py --arsenal MEDIA --brita MEDIA --dest DIR
                                                        Arsenal's rounds (and Brita's rocket) packed under the same ids
                                                        into DIR/ammo/<item>.zmesh, for config/zomboidcraft/meshes on
                                                        Josiah's PC (MeshGuns reads a mesh there before the mod's own).
                                                        Never into the repo: the packs aren't ours to share.
"""
import json, math, os, shutil, subprocess, sys, tempfile
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pack
import skin

BRASS, NICKEL, COPPER, LEAD, HULL = '#b88a3c', '#b7b7ae', '#a85a32', '#8a8c8e', '#a8262a'
SPECIAL = {'primer': '#c9a03e', 'cup': '#2a201a', 'crimp': '#7e1b1e'}


def ogive(z0, length, r0, r_tip, flat=0.0, n=6):
    """A round nose (pointed when r_tip is near 0) from radius r0 at z0, flat-nosed if flat > 0."""
    out = []
    for i in range(1, n + 1):
        t = i / n
        r = r_tip + (r0 - r_tip) * math.cos(t * math.pi / 2) ** 0.9
        out.append((z0 + length * math.sin(t * math.pi / 2) ** 1.2, max(r, flat)))
    return out


def cartridge(case, oal, body, rim, neck=None, shoulder=None, bullet='round', bcol=COPPER, tip=None,
              case_col=BRASS, rimmed=False, flat=0.0):
    """case and overall lengths, body, rim and neck diameters; shoulder: where a bottleneck starts (from the base)."""
    rb, rr, rn = body / 2, rim / 2, (neck or body) / 2
    p = [(0, 0, 'primer'), (0, rr * 0.45, 'primer'), (0, rr, case_col)]
    if rimmed:
        p += [(rr * 0.12, rr, case_col), (rr * 0.12, rb, case_col)]
    else:       # a rimless case's extractor groove
        p += [(0.9, rr, case_col), (1.2, rb * 0.82, case_col), (1.9, rb * 0.82, case_col), (2.3, rb, case_col)]
    if shoulder:
        p += [(shoulder, rb * 0.97, case_col), (shoulder + (rb - rn) * 1.2, rn, case_col)]
    p += [(case, rn, case_col)]
    bl = oal - case
    if bullet == 'round':
        p += [(case + 0.01, rn * 0.98, bcol)] + [(z, r, bcol) for z, r in ogive(case + bl * 0.25, bl * 0.75, rn * 0.98, 0.0, flat)]
    elif bullet == 'spitzer':
        p += [(case + 0.01, rn * 0.98, bcol), (case + bl * 0.2, rn * 0.98, bcol)]
        tipz = case + bl * 0.8
        p += [(z, r, tip if tip and z > tipz else bcol) for z, r in ogive(case + bl * 0.2, bl * 0.8, rn * 0.98, 0.15, 0, 7)]
    elif bullet == 'swc':       # semi-wadcutter: a step then a flat-topped cone
        p += [(case + 0.01, rn * 0.99, bcol), (case + bl * 0.25, rn * 0.99, bcol), (case + bl * 0.3, rn * 0.78, bcol),
              (oal - 0.2, rn * 0.62, bcol), (oal, rn * 0.55, bcol), (oal, 0, bcol)]
    elif bullet == 'hollow':    # hollow point: a soft rim round a dark cup
        p += [(case + 0.01, rn * 0.98, bcol)] + [(z, r, bcol) for z, r in ogive(case + bl * 0.2, bl * 0.8, rn * 0.98, rn * 0.45, rn * 0.45, 5)]
        p += [(oal, rn * 0.3, 'cup'), (oal - bl * 0.25, 0, 'cup')]
    return p


def shell(length, dia, head):
    r = dia / 2
    return [(0, 0, 'primer'), (0, r * 0.25, 'primer'), (0, r * 1.04, BRASS), (0.9, r * 1.04, BRASS), (0.9, r, BRASS),
            (head, r, BRASS), (head + 0.01, r * 0.99, HULL), (length - 1.5, r * 0.99, HULL), (length, r * 0.8, HULL),
            (length, r * 0.25, 'crimp'), (length - 1.2, 0, 'crimp')]


def grenade40():
    al, band, olive = '#8e9198', '#c9a33a', '#4b5530'
    return [(0, 0, 'primer'), (0, 6, 'primer'), (0, 22, al), (3, 22, al), (3, 20, al), (34, 20, al),
            (34.01, 19.5, band), (40, 19.5, band), (40.01, 19.6, olive), (60, 19.6, olive)] + \
           [(z, r, olive) for z, r in ogive(60, 40, 19.6, 3.5, 3.5, 7)] + [(100, 3.5, band), (101.5, 0, band)]


def rocket():
    """PG-7V as it sits in the launcher's muzzle, the way Brita's pack has it: the thin tail tube, the long cone and
    the warhead, its fuze at the tip."""
    tube, war, fuze = '#3d4430', '#4f5a2c', '#9a8a5a'
    return [(0, 0, tube), (0, 10, tube), (150, 10, tube), (205, 17.5, war), (255, 17.5, war)] + \
           [(z, r, war) for z, r in ogive(255, 72, 17.5, 4.5, 4.5, 6)] + [(327, 4.5, fuze), (336, 4.0, fuze), (336, 0, fuze)]


# item: (its round, Arsenal's mesh and texture (None: Brita's rocket), the old model its display transforms come from)
ROUNDS = {
    'pistol_ammo': (cartridge(19.15, 29.7, 9.93, 9.96), 'Bullets9mm', 'A'),
    'pocket_ammo': (cartridge(17.3, 25.0, 9.5, 9.5), 'Bullets380', 'A'),
    'revolver_ammo': (cartridge(29.3, 39.4, 9.6, 11.2, bullet='swc', bcol=LEAD, rimmed=True), 'Bullets38', 'A'),
    'magnum_ammo': (cartridge(32.6, 40.9, 11.6, 13.1, bullet='hollow', rimmed=True, case_col=NICKEL), 'Bullets44', 'A'),
    'acp45_ammo': (cartridge(22.8, 32.4, 12.0, 12.0), 'Bullets45', 'A'),
    'fn57_ammo': (cartridge(28.9, 40.5, 7.95, 7.8, neck=6.3, shoulder=21.5, bullet='spitzer', tip='#2d3e8a'), 'Bullets57', 'B'),
    'colt45_ammo': (cartridge(32.6, 41.0, 12.2, 12.9, bcol=LEAD, rimmed=True, flat=3.0), 'Bullets45LC', 'A'),
    'sw500_ammo': (cartridge(41.3, 57.0, 13.4, 14.2, bullet='hollow', rimmed=True, case_col=NICKEL), 'Bullets50MAG', 'A'),
    'rimfire_ammo': (cartridge(15.6, 25.4, 5.7, 6.9, bcol=LEAD, rimmed=True, case_col=COPPER), 'Bullets22', 'A'),
    'rifle_ammo': (cartridge(44.7, 57.4, 9.6, 9.6, neck=6.4, shoulder=36.5, bullet='spitzer', tip='#3d6b2c'), '556Bullets', 'A'),
    'hunting_rifle_ammo': (cartridge(51.2, 71.1, 11.9, 12.0, neck=8.7, shoulder=40.0, bullet='spitzer'), '308Bullets', 'A'),
    # .30-30 has no round of Arsenal's: theirs nearest, the rimmed .45-70
    'lever_ammo': (cartridge(51.8, 64.8, 10.7, 12.9, neck=8.4, shoulder=38.0, bcol=LEAD, rimmed=True, flat=2.2), 'Bullets4570', 'A'),
    'heavy_ammo': (cartridge(99.0, 138.0, 20.4, 20.4, neck=14.2, shoulder=77.0, bullet='spitzer', tip='#262626'), '50BMGBullets', 'A'),
    'shotgun_shell': (shell(70.0, 20.5, 12.0), '12gShotgunShells', 'A'),
    'grenade_round': (grenade40(), '40Round', 'A'),
    'pg7v_rocket': (rocket(), None, None),
}
# new items with no carton of their own: their display transforms come from this one's
NEW = {'acp45_ammo', 'fn57_ammo', 'colt45_ammo', 'sw500_ammo', 'pg7v_rocket'}
ASSETS = skin.ASSETS
# a round fills the inventory slot like the carton did, but held or dropped it's the size of a round, not a box
SCALES = {'firstperson_righthand': 0.45, 'firstperson_lefthand': 0.45, 'thirdperson_righthand': 0.55,
          'thirdperson_lefthand': 0.55, 'ground': 0.4}
WEAPON_MODELS = os.path.join(ASSETS, 'models/item/weapon')


# items whose own model is kept for something else: the shotgun's loose shell model is a single shell (it is drawn
# going into the gun), so the shell round sits on a carton of its own like the rest
MODEL = {'shotgun_shell': 'shotgun_shell_box'}


def model_name(item):
    return MODEL.get(item, item)


def base_model(item):
    """The plain model the round is fitted onto and keeps the display transforms of: the old carton (a new item gets
    a copy of the 9mm's)."""
    item = model_name(item)
    path = os.path.join(WEAPON_MODELS, item + '.json')
    if not os.path.exists(path):
        m = json.load(open(os.path.join(WEAPON_MODELS, 'pistol_ammo.json')))
        with open(path, 'w') as f:
            f.write(json.dumps(m, indent=1) + '\n')
    return f'zomboidcraft:item/weapon/{item}'


def lay_along(item, length_axis, tip_positive=True):
    """pack.py axes that turn the source round (its length along source axis length_axis, tip toward + when
    tip_positive) to lie along the base model's longest side, tip toward +: box fitting matches that side to the
    mesh's own extent along it."""
    lo, hi = pack.model_elements(model_name(item))
    a = int(np.argmax(np.asarray(hi) - np.asarray(lo)))
    s = '+' if tip_positive else '-'
    o = '+' if tip_positive else '-'           # two axes turned over together: a half turn, never a mirror
    out = [None] * 3
    out[a] = s + 'xyz'[length_axis]
    out[(a + 1) % 3] = o + 'xyz'[(length_axis + 1) % 3]
    out[(a + 2) % 3] = '+' + 'xyz'[(length_axis + 2) % 3]
    return out


def write_ours(item, profile, work, sides=24):
    """The turned round as an OBJ painted from a palette strip; returns the pack spec."""
    cols = []
    for _, _, c in profile:
        c = SPECIAL.get(c, c)
        if c not in cols:
            cols.append(c)
    cell = 16
    pal = Image.new('RGB', (cell * len(cols), cell))
    for i, c in enumerate(cols):
        pal.paste(tuple(int(c[k:k + 2], 16) for k in (1, 3, 5)), (i * cell, 0, (i + 1) * cell, cell))
    pal.save(os.path.join(work, item + '.png'))
    vs, fs, us = [], [], []
    for (z0, r0, c0), (z1, r1, c1) in zip(profile, profile[1:]):
        ci = cols.index(SPECIAL.get(c1, c1))
        us.append(((ci + 0.5) / len(cols), 0.5))
        uvi = len(us)
        for k in range(sides):
            a0, a1 = 2 * math.pi * k / sides, 2 * math.pi * (k + 1) / sides
            quad = [(z0, r0, a0), (z0, r0, a1), (z1, r1, a1), (z1, r1, a0)]
            base = len(vs)
            vs += [(z, r * math.cos(a), r * math.sin(a)) for z, r, a in quad]
            if r0 > 1e-6:       # wound so the faces look outward (the outline shell is the faces turned inward)
                fs.append((base + 1, base + 2, base + 3, uvi))
            if r1 > 1e-6:
                fs.append((base + 1, base + 3, base + 4, uvi))
    obj = os.path.join(work, item + '.obj')
    with open(obj, 'w') as f:
        f.write(f'mtllib {item}.mtl\nusemtl round\n')
        f.writelines(f'v {x:.4f} {y:.4f} {z:.4f}\n' for x, y, z in vs)
        f.writelines(f'vt {u:.4f} {v:.4f}\n' for u, v in us)
        f.writelines(f'f {a}/{t} {b}/{t} {c}/{t}\n' for a, b, c, t in fs)
    with open(os.path.join(work, item + '.mtl'), 'w') as f:
        f.write(f'newmtl round\nmap_Kd {item}.png\n')
    assert outward(obj) > 0, item
    return {'id': f'ammo/{item}', 'obj': obj, 'textures': [os.path.join(work, item + '.png')],
            'axes': lay_along(item, 0), 'box': model_name(item), 'outline': 0.02, 'texture': 256}


def outward(obj):
    """Turns the OBJ's faces over if they're wound to look inward (a closed mesh's signed volume comes out negative):
    drawn culled, an inward mesh shows only its outline shell, a black silhouette."""
    lines = open(obj).read().splitlines()
    v = np.array([[float(x) for x in l.split()[1:4]] for l in lines if l.startswith('v ')])
    vol = 0.0
    for l in lines:
        if l.startswith('f '):
            i = [int(p.split('/')[0]) - 1 for p in l.split()[1:]]
            for k in range(1, len(i) - 1):
                vol += np.dot(v[i[0]], np.cross(v[i[k]], v[i[k + 1]]))
    if vol < 0:
        out = [('f ' + ' '.join(reversed(l.split()[1:]))) if l.startswith('f ') else l for l in lines]
        open(obj, 'w').write('\n'.join(out) + '\n')
    return vol


def arsenal_spec(item, mesh, tex, arsenal, brita, work, dest):
    """Arsenal's round (or Brita's rocket) converted for packing under our id, its tip toward the muzzle end."""
    if mesh is None:
        src = os.path.join(arsenal, 'models_X/weapons/parts/Standard_Mag_Rocket.x')
        texture = os.path.join(brita, 'textures/weapons/firearm/RPG_7.png')
    else:
        d = os.path.join(arsenal, 'models_X/WorldItems')
        src = next(os.path.join(d, f) for f in os.listdir(d) if f.lower() == mesh.lower() + '.x')
        texture = os.path.join(arsenal, f'textures/WorldItems/Bullets_{tex}.png')
    obj = os.path.join(work, item + '_arsenal.obj')
    subprocess.run(['assimp', 'export', src, obj, '-ptv', '-tri', '-jiv'], check=True, capture_output=True)
    outward(obj)
    v = np.array([[float(x) for x in l.split()[1:4]] for l in open(obj) if l.startswith('v ')])
    ax = int(np.argmax(np.ptp(v, 0)))
    # the narrower end is the tip
    t = (v[:, ax] - v[:, ax].min()) / np.ptp(v[:, ax])
    rad = np.linalg.norm(np.delete(v - v.mean(0), ax, axis=1), axis=1)
    tip_hi = rad[t > 0.9].mean() < rad[t < 0.1].mean()
    return {'id': f'ammo/{item}', 'obj': obj, 'textures': [texture], 'axes': lay_along(item, ax, tip_hi), 'box': model_name(item),
            'outline': 0.02, 'texture': 512, 'dest': os.path.join(dest, 'ammo', item + '.zmesh')}


def pack_spec(spec, work):
    p = os.path.join(work, spec['id'].replace('/', '_') + '.json')
    json.dump(spec, open(p, 'w'))
    pack.main(p)


def write_items():
    """Each ammo item's definition drawing its packed round (weapons.py calls this after writing the old cartons'),
    keeping the carton only as the model its display transforms come from."""
    for item in ROUNDS:
        if not os.path.exists(os.path.join(ASSETS, 'meshes', 'ammo', item + '.zmesh')):
            continue
        path = os.path.join(ASSETS, 'items', item + '.json')
        if not os.path.exists(path):
            json.dump({}, open(path, 'w'))
        base, mesh = base_model(item), f'ammo/{item}'
        cases = [{'when': ctx, 'model': skin.centred(base, mesh, scale=k)} for ctx, k in SCALES.items()]
        skin.write(path, {'type': 'minecraft:select', 'property': 'minecraft:display_context', 'cases': cases,
                          'fallback': skin.centred(base, mesh)})


def main():
    work = tempfile.mkdtemp(prefix='rounds_')
    try:
        if '--arsenal' in sys.argv:
            arsenal = sys.argv[sys.argv.index('--arsenal') + 1]
            brita = sys.argv[sys.argv.index('--brita') + 1]
            dest = sys.argv[sys.argv.index('--dest') + 1]
            for item, (_, mesh, tex) in ROUNDS.items():
                base_model(item)
                pack_spec(arsenal_spec(item, mesh, tex, arsenal, brita, work, dest), work)
            return
        for item, (profile, _, _) in ROUNDS.items():
            base_model(item)
            pack_spec(write_ours(item, profile, work), work)
        write_items()
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    main()
