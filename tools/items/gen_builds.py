"""Base building, wave 1: the materials (metal sheet, tin can, plastic drum, tarp, empty sandbag) as item models, and
what the Build page puts in the world (a metal sheet over a window, a tin-can tripwire, a rain barrel at each fill
level, a sandbag wall) as block models with their blockstates. Built from boxes and painted per face with the weapon
tooling (tools/weapons/model.py, paint.py).

References (text research; image hosts are blocked here): a 55 US gal HDPE drum is about 88 cm tall and 58 cm across,
blue, with two rolling hoops and two screw bungs on a closed top; a poly tarp is blue woven polyethylene with brass
grommets every 50 cm or so along a hemmed, rope-reinforced edge; a sandbag is a woven polypropylene sack about 35 x
65 cm, khaki or white, laid filled in running bond with the tied end tucked under; a food can is about 7.5 cm across
and 11 cm tall, tinplate with rolled rims and beads pressed round its side; a tin-can tripwire is twine at ankle height
between two stakes with cans hung on it; a sheet over a window is flat galvanised steel screwed to the frame round its
edge.

    python3 tools/items/gen_builds.py
"""
import json
import sys
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
sys.path.insert(0, str(ROOT / 'tools/items'))
import model as M  # noqa: E402
import paint as P  # noqa: E402
from gen_bags import worn_look  # noqa: E402
from gen_cases import held_display, merge_lang  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
DATA = ROOT / 'src/main/resources/data'
NS = 'zomboidcraft'
C = np.array([8.0, 8.0, 8.0])


# ================================================================== materials


def galv_sheet(f):
    """Flat galvanised steel: spangle, a dull bloom, rust creeping in from the cut edges, screw heads near the rim."""
    c = P.galvanised(f)
    c = P.rust(f, c, 0.25 + 0.4 * np.clip(0.6 - f.edge, 0, 1).mean(), seed=501)
    return c


def screw(f):
    return P.steel(f, 0.5, spread=0.1)


def drum(f):
    """Blue HDPE: a slightly waxy, mottled plastic, scuffed pale where it's been rolled."""
    c = P.plain(f, '#1f5aa6', 0.08)
    c = c * (1 + (f.noise(0.8, 511, 3) - 0.5) * 0.18)[..., None]
    scuff = f.noise(3.0, 512, 2) > 0.78
    return np.where(scuff[..., None], P.mix(c, P.col('#6f98c9'), 0.4), c)


def bung(f):
    return P.plain(f, '#d9d6cc', 0.06)


def tarp(f):
    """Blue woven polyethylene: a fine cross weave and creases."""
    c = P.plain(f, '#2a62b5', 0.06)
    u, v = f.uv[..., 0] * 8, f.uv[..., 1] * 8
    weave = ((np.floor(u * 2) + np.floor(v * 2)) % 2) * 2 - 1
    c = c * (1 + weave * 0.04)[..., None]
    crease = np.abs(np.sin(f.noise(0.5, 521, 2) * 12)) < 0.12
    return np.where(crease[..., None], c * 0.82, c)


def grommet(f):
    return P.brass(f)


def rope(f):
    c = P.plain(f, '#d8d2bf', 0.1)
    twist = np.sin(f.along() * 12) > 0.3
    return np.where(twist[..., None], c * 0.82, c)


def sack(base='#b9a77c'):
    """Woven polypropylene sack: a coarse basket weave, darker seams, grime low down."""
    def fn(f):
        c = P.plain(f, base, 0.08)
        u, v = f.uv[..., 0] * 8, f.uv[..., 1] * 8
        weave = ((np.floor(u * 1.5) + np.floor(v * 1.5)) % 2) * 2 - 1
        c = c * (1 + weave * 0.07)[..., None]
        seam = f.edge < 0.18
        c = np.where(seam[..., None], c * 0.8, c)
        dirt = np.clip((0.6 - (f.Y - f.box['lo'][1])) * 1.2, 0, 1) * (f.noise(1.5, 531, 2) > 0.4)
        return P.mix(c, P.col('#5b4a33'), dirt * 0.45)
    return fn


def tie(f):
    return P.plain(f, '#9c8a62', 0.1)


def tin(f):
    """Tinplate: bright, with beads pressed round it, dulled and spotted with rust."""
    c = P.steel(f, 0.68, tint='#b8bcbd', brushed=False, spread=0.1)
    if abs(f.n[1]) < 0.5:
        bead = (np.sin(f.Y * 7.0) > 0.6)
        c = np.where(bead[..., None], c * 0.82, c)
    return P.rust(f, c, 0.3, seed=541)


def label(f):
    """What's left of a paper label: faded red with a pale band."""
    c = P.plain(f, '#8f3a2e', 0.12)
    band = np.abs(f.Y - (f.box['lo'][1] + f.box['hi'][1]) / 2) < 0.25
    c = np.where(band[..., None], P.col('#d8cfb4'), c)
    torn = f.noise(2.0, 542, 2) > 0.7
    return np.where(torn[..., None], tin(f), c)


def twine(f):
    return P.plain(f, '#c2ad7c', 0.12)


def stake(f):
    return P.wood(f, light='#a7845a', dark='#6d4f30')


def earth(f):
    return P.plain(f, '#5a4632', 0.2)


def water(f):
    c = P.plain(f, '#2f4f5c', 0.06)
    return c * (1 + (f.noise(1.2, 551, 2) - 0.5) * 0.2)[..., None]


# ================================================================== item builds (centred, about a hand across)


def build_metal_sheet(m):
    # a flat sheet leaning: 14 x 10 x 0.3, screw holes near the corners
    m.box([-7, 0, -0.15], [7, 10, 0.15], 'sheet')
    for x in (-6.2, 6.0):
        for y in (0.8, 8.9):
            m.box([x, y, 0.15], [x + 0.3, y + 0.3, 0.22], 'screw')


def build_tin_can(m):
    m.rod(0.0, 7.0, 2.6, 'tin')
    m.rod(1.6, 5.4, 2.62, 'label')
    m.rod(7.0, 7.3, 2.75, 'tin')                                     # the rolled rim
    m.rod(-0.3, 0.0, 2.75, 'tin')
    m.box([-1.2, 7.3, -2.0], [1.4, 7.45, 0.4], 'tin', rot=dict(axis='x', angle=-45, origin=[0, 7.3, -2.0]))  # the lid


def build_drum(m):
    m.rod(0.0, 14.0, 5.0, 'drum')
    for y in (4.4, 9.4):                                             # the rolling hoops
        m.rod(y, y + 0.7, 5.2, 'drum')
    m.rod(13.9, 14.3, 5.15, 'drum')                                  # the chime round the top
    for x in (-2.6, 2.6):                                            # two bungs
        m.rod(14.0, 14.45, 0.7, 'bung', cx=x, cz=-1.0)


def build_tarp(m):
    # folded in a bundle and tied
    m.box([-6, 0, -4], [6, 3.2, 4], 'tarp')
    m.box([-5.6, 3.2, -3.6], [5.6, 3.8, 3.6], 'tarp')
    m.box([-6.05, 0.6, -4.05], [-5.6, 2.6, 4.05], 'tarp')           # the folded edge
    for x in (-3.5, 3.5):
        m.box([x - 0.25, -0.1, -4.1], [x + 0.25, 3.9, 4.1], 'rope')
    for z in (-3.2, 0.0, 3.2):
        m.box([5.9, 1.4, z - 0.25], [6.15, 1.9, z + 0.25], 'grommet')


def build_sandbag_empty(m):
    # a folded empty sack
    m.box([-6, 0, -3.5], [6, 0.6, 3.5], 'sack')
    m.box([-6, 0.6, -3.5], [1, 1.1, 3.5], 'sack')
    m.box([4.0, 0.6, -0.4], [6.6, 0.9, 0.4], 'tie')


ITEMS = [
    ('metal_sheet', build_metal_sheet, 0.4, 9.0, dict(sheet=galv_sheet, screw=screw)),
    ('tin_can', build_tin_can, 0.4, 6.0, dict(tin=tin, label=label)),
    ('plastic_drum', build_drum, 0.3, 11.0, dict(drum=drum, bung=bung)),
    ('tarp', build_tarp, 0.3, 9.0, dict(tarp=tarp, rope=rope, grommet=grommet)),
    ('empty_sandbag', build_sandbag_empty, 0.3, 9.0, dict(sack=sack(), tie=tie)),
]

NAMES = {'metal_sheet': 'Metal Sheet', 'tin_can': 'Tin Can', 'plastic_drum': 'Plastic Drum', 'tarp': 'Tarp',
         'empty_sandbag': 'Empty Sandbag'}


# ================================================================== block builds (in block space, 0..16, facing north)


def block_metal_sheet(m):
    # a sheet over the north side, standing a touch proud of the wall, screwed round its edge
    m.box([-0.5, -0.5, 0], [16.5, 16.5, 1.2], 'sheet')
    for x in (0.4, 5.4, 10.6, 15.0):
        for y in (0.4, 15.0):
            m.box([x, y, 1.2], [x + 0.6, y + 0.6, 1.5], 'screw')
    for y in (5.4, 10.6):
        for x in (0.4, 15.0):
            m.box([x, y, 1.2], [x + 0.6, y + 0.6, 1.5], 'screw')


def block_tripwire(m):
    # twine along x at ankle height between two stakes, three cans hung on it
    for x in (0.4, 14.6):
        m.box([x, 0, 7.3], [x + 1.0, 7.0, 8.3], 'stake')
    m.box([0, 4.6, 7.7], [16, 4.9, 8.0], 'twine')
    for x in (4.0, 8.2, 12.0):
        m.box([x - 0.08, 3.2, 7.75], [x + 0.08, 4.6, 7.95], 'twine')
        m.box([x - 1.0, 0.9, 7.0], [x + 1.0, 3.2, 9.0], 'tin')
        m.box([x - 1.1, 3.0, 6.9], [x + 1.1, 3.25, 9.1], 'tin')


def block_rain_barrel(fill):
    def build(m):
        # the drum, two hoops, a tarp stretched over the top and sagging into a funnel, tied with rope
        m.box([2.5, 0, 3.0], [13.5, 13, 13.0], 'drum')
        m.box([3.0, 0, 2.5], [13.0, 13, 13.5], 'drum')
        for y in (3.8, 8.6):
            m.box([2.3, y, 2.8], [13.7, y + 0.7, 13.2], 'drum')
            m.box([2.8, y, 2.3], [13.2, y + 0.7, 13.7], 'drum')
        m.box([2.2, 13, 2.2], [13.8, 13.6, 13.8], 'tarp')            # the tarp over the rim
        m.box([1.8, 11.0, 1.8], [14.2, 13.4, 2.2], 'tarp')           # its skirt hanging down the sides
        m.box([1.8, 11.0, 13.8], [14.2, 13.4, 14.2], 'tarp')
        m.box([1.8, 11.0, 2.2], [2.2, 13.4, 13.8], 'tarp')
        m.box([13.8, 11.0, 2.2], [14.2, 13.4, 13.8], 'tarp')
        m.box([1.7, 11.8, 1.7], [14.3, 12.2, 14.3], 'rope')          # tied round under the rim
        m.box([4.0, 13.6, 4.0], [12.0, 14.6, 12.0], 'tarp_top')      # the funnel, sagging to a hole in the middle
        for x, z in ((2.0, 2.0), (13.8, 2.0), (2.0, 13.8), (13.8, 13.8)):   # grommets at the corners
            m.box([x - 0.2, 12.5, z - 0.2], [x + 0.2, 12.9, z + 0.2], 'grommet')
        if fill > 0:
            m.box([6.6, 14.55, 6.6], [9.4, 14.65, 9.4], 'water')
    return build


def tarp_top(fill):
    """The tarp seen from above: sagging to a dark hole, wet and darker the fuller the barrel."""
    def fn(f):
        c = tarp(f)
        r = np.hypot(f.X - 8, f.Z - 8)
        c = c * np.clip(0.55 + r / 8, 0.55, 1.0)[..., None]
        hole = r < 0.9
        c = np.where(hole[..., None], P.col('#0d1416'), c)
        wet = fill / 8
        return P.mix(c, c * 0.7, wet)
    return fn


def block_sandbags(m):
    # three courses running along x, 10 thick and 12 high, staggered like brickwork, the bags bulging
    for course, y in enumerate((0, 4, 8)):
        off = 0 if course % 2 == 0 else -4
        x = off
        while x < 16:
            x0, x1 = max(0, x + 0.15), min(16, x + 7.85)
            if x1 - x0 > 1:
                m.box([x0, y, 3.4], [x1, y + 3.9, 12.6], 'sack')
                m.box([x0 + 0.5, y + 0.4, 3.1], [x1 - 0.5, y + 3.5, 3.4], 'sack')      # the bulge
                m.box([x0 + 0.5, y + 0.4, 12.6], [x1 - 0.5, y + 3.5, 12.9], 'sack')
            x += 8


BLOCKS = [
    ('metal_sheet_cover', block_metal_sheet, 0.5, dict(sheet=galv_sheet, screw=screw)),
    ('can_tripwire', block_tripwire, 0.5, dict(stake=stake, twine=twine, tin=tin)),
    ('sandbag_wall', block_sandbags, 0.4, dict(sack=sack())),
]
BARREL = dict(drum=drum, tarp=tarp, rope=rope, grommet=grommet, tarp_top=tarp_top(4), water=water)


def paint(m, name, wear, mats):
    seed = zlib.crc32(name.encode()) % 1000
    patches = M.paint_model(m, worn_look(wear, 0.0, **mats), wear, seed)
    return M.pack(patches)


def facing_states(model):
    return {'variants': {f'facing={f}': {'model': model, **({'y': y} if y else {})}
                         for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}}


def main():
    merge_lang({f'item.{NS}.{k}': v for k, v in NAMES.items()})
    merge_lang({f'block.{NS}.metal_sheet_cover': 'Metal Sheet', f'block.{NS}.can_tripwire': 'Tin-Can Tripwire',
                f'block.{NS}.rain_barrel': 'Rain Barrel', f'block.{NS}.sandbag_wall': 'Sandbag Wall'})
    for name, build, wear, held, mats in ITEMS:
        m = M.Model(name)
        build(m)
        lo, hi = m.bounds()
        m.shift(C - (lo + hi) / 2)
        m.tidy()
        img, uvs, T = paint(m, name, wear, mats)
        (ASSETS / 'textures/item/building').mkdir(parents=True, exist_ok=True)
        M.save_png(img, ASSETS / f'textures/item/building/{name}.png')
        M.dump(M.model_json(m, uvs, f'{NS}:item/building/{name}', held_display(m, held), gui_light='side'),
               ASSETS / f'models/item/building/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/building/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')
    (ASSETS / 'textures/block/building').mkdir(parents=True, exist_ok=True)
    for name, build, wear, mats in BLOCKS:
        m = M.Model(name)
        build(m)
        m.tidy()
        img, uvs, T = paint(m, name, wear, mats)
        M.save_png(img, ASSETS / f'textures/block/building/{name}.png')
        out = M.model_json(m, uvs, f'{NS}:block/building/{name}', {}, gui_light='side')
        del out['display']
        out['ambientocclusion'] = False
        M.dump(out, ASSETS / f'models/block/building/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')
    # the rain barrel: one texture painted with water in the funnel; the empty model leaves the water out
    old = M.PX
    M.PX = 6
    full = M.Model('rain_barrel')
    block_rain_barrel(1)(full)
    full.tidy()
    img, uvs, T = paint(full, 'rain_barrel', 0.35, BARREL)
    M.PX = old
    M.save_png(img, ASSETS / 'textures/block/building/rain_barrel.png')
    for state, keep in (('rain_barrel_empty', len(full.parts) - 1), ('rain_barrel_full', len(full.parts))):
        out = M.model_json(full, {k: v for k, v in uvs.items() if k[0] < keep}, f'{NS}:block/building/rain_barrel', {}, gui_light='side')
        del out['display']
        out['ambientocclusion'] = False
        M.dump(out, ASSETS / f'models/block/building/{state}.json')
    print(f'rain_barrel: {len(full.parts)} boxes, {T}px')
    bs = ASSETS / 'blockstates'
    M.dump(facing_states(f'{NS}:block/building/metal_sheet_cover'), bs / 'metal_sheet_cover.json')
    M.dump(facing_states(f'{NS}:block/building/sandbag_wall'), bs / 'sandbag_wall.json')
    M.dump({'variants': {'axis=x': {'model': f'{NS}:block/building/can_tripwire'},
                         'axis=z': {'model': f'{NS}:block/building/can_tripwire', 'y': 90}}}, bs / 'can_tripwire.json')
    M.dump({'variants': {f'level={n}': {'model': f'{NS}:block/building/rain_barrel_{"full" if n else "empty"}'} for n in range(9)}},
           bs / 'rain_barrel.json')
    # what each gives back when a player takes it down
    drops = {'metal_sheet_cover': [('metal_sheet', 1)], 'can_tripwire': [('tin_can', 3), ('minecraft:string', 1)],
             'rain_barrel': [('plastic_drum', 1), ('tarp', 1)], 'sandbag_wall': [('empty_sandbag', 2)]}
    for block, items in drops.items():
        table = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
                 'name': item if ':' in item else f'{NS}:{item}',
                 'functions': [{'function': 'minecraft:set_count', 'count': n}]}],
                 'conditions': [{'condition': 'minecraft:survives_explosion'}]} for item, n in items]}
        path = DATA / f'{NS}/loot_table/blocks/{block}.json'
        path.write_text(json.dumps(table, indent=2) + '\n')


if __name__ == '__main__':
    main()
