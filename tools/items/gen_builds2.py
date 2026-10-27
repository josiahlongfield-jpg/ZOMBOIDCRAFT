"""Base building, wave 2: the alarm clock and the seed packets as item models, and what the Build page puts in the world
(an alarm clock set down, a planter box with its crops at each stage, a spiked log wall) as block models with their
blockstates and block loot. Built from boxes and painted per face with the weapon tooling, like gen_builds.py.

References (text research; image hosts are blocked here): a twin-bell alarm clock is about 11 cm across and 15 cm tall
with its bells, a round enamelled case (red, cream or black) on two splayed feet, a white dial with bold black numerals
and a seconds hand, two chromed bells on top struck by a hammer between them, and a carry loop over the bells; quartz
ones run on one AA. A seed packet is a paper envelope about 8 x 12 cm: a coloured band at the top with the crop's
name, a painting of the crop filling the middle, sowing notes in small print at the bottom. A raised planter is 2x6
boards on edge nailed to corner posts, about 25 cm deep, filled to a few cm from the rim with dark earth that goes
almost black when wet. Carrots show fine, feathery fronds and orange shoulders at the soil when ready; potatoes grow
into a bushy clump of oval leaves that yellows when they're ready to lift; beets have broad, glossy, red-veined leaves
on red stems with the round red root half out of the soil. A palisade is logs about 15-20 cm thick set side by side,
their tops cut to points with an axe (pale, faceted, fresh wood against grey-brown bark), bound with rope or a rail.

    python3 tools/items/gen_builds2.py
"""
import json
import math
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


def enamel(base='#a3261f'):
    """Stove enamel on a pressed steel case: glossy, chipped to dark steel at the rims."""
    def fn(f):
        c = P.plain(f, base, 0.05)
        c = c * (1 + (f.noise(1.0, 601, 2) - 0.5) * 0.12)[..., None]
        chip = (f.edge < 0.25) & (f.noise(4.0, 602, 2) > 0.62)
        return np.where(chip[..., None], P.col('#2d2b29'), c)
    return fn


def chrome(f):
    return P.chrome(f)


def dial(f):
    """A white dial with twelve bold hour marks round it and a printed ring, seen from the front (-z)."""
    c = P.plain(f, '#ece6d6', 0.03)
    if abs(f.n[2]) < 0.5:
        return c * 0.92
    cx = (f.box['lo'][0] + f.box['hi'][0]) / 2
    cy = (f.box['lo'][1] + f.box['hi'][1]) / 2
    dx, dy = f.X - cx, f.Y - cy
    r = np.hypot(dx, dy)
    R = f.box.get('round', (f.box['hi'][0] - f.box['lo'][0]) / 2)
    ang = (np.degrees(np.arctan2(dy, dx)) + 360) % 30
    hour = ((ang < 4) | (ang > 26)) & (r > R * 0.68) & (r < R * 0.9)
    ring = np.abs(r - R * 0.93) < R * 0.025
    mark = hour | ring
    return np.where(mark[..., None], P.col('#191817'), c)


def hand(f):
    return P.plain(f, '#141414', 0.03)


def red_hand(f):
    return P.plain(f, '#b3241c', 0.03)


def paper(f):
    c = P.plain(f, '#e6dcc3', 0.06)
    crease = np.abs(f.noise(0.6, 611, 2) - 0.5) < 0.02
    return np.where(crease[..., None], c * 0.86, c)


def packet(crop):
    """The front of a seed packet (+z): a coloured band at the top, the crop painted in the middle, small print below."""
    band = {'carrot': '#2f6a3a', 'potato': '#7a4a22', 'beet': '#5d1f3d'}[crop]

    def fn(f):
        c = paper(f)
        if f.n[2] < 0.5:
            return c
        lo, hi = f.box['lo'], f.box['hi']
        u = (f.X - lo[0]) / (hi[0] - lo[0])
        v = (f.Y - lo[1]) / (hi[1] - lo[1])          # 0 at the bottom
        c = np.where((v > 0.8)[..., None], P.col(band), c)
        letters = (v > 0.85) & (v < 0.93) & (u > 0.14) & (u < 0.86) & (np.sin(u * 60) > 0.1)
        c = np.where(letters[..., None], P.col('#efe7d2'), c)
        print_ = (v < 0.17) & (v > 0.05) & (u > 0.12) & (u < 0.88) & (np.sin(v * 110) > 0.55) & (np.sin(u * 47) > -0.6)
        c = np.where(print_[..., None], P.col('#58524a'), c)
        x, y = u - 0.5, v - 0.48
        if crop == 'carrot':
            body = (np.abs(x) < (y + 0.28) * 0.32) & (y > -0.28) & (y < 0.16)
            tops = (y > 0.16) & (y < 0.32) & (np.abs(np.sin(x * 40)) > 0.5) & (np.abs(x) < 0.16)
            c = np.where(body[..., None], P.col('#e2701d'), c)
            c = np.where(tops[..., None], P.col('#3e8a35'), c)
        elif crop == 'potato':
            for px, py, s in ((-0.13, -0.05, 0.16), (0.12, 0.02, 0.14), (0.0, 0.18, 0.11)):
                spud = ((x - px) / s) ** 2 + ((y - py) / (s * 0.7)) ** 2 < 1
                c = np.where(spud[..., None], P.col('#b98b52'), c)
                eye = spud & (f.noise(9.0, 612, 1) > 0.78)
                c = np.where(eye[..., None], P.col('#7a5631'), c)
        else:
            bulb = x ** 2 + (y + 0.05) ** 2 < 0.17 ** 2
            leaf = (y > 0.1) & (y < 0.34) & (np.abs(x - np.sin(y * 12) * 0.06) < 0.07)
            c = np.where(leaf[..., None], P.col('#3a7a34'), c)
            c = np.where(bulb[..., None], P.col('#7d1c32'), c)
        return c
    return fn


def plank(f):
    """Weathered softwood 2x6: grey-brown, the boards' seams dark."""
    c = P.wood(f, light='#a58a63', dark='#6e5638', ring=2.0)
    seam = np.abs(((f.Y + 0.01) % 5.0) - 0.0) < 0.35
    return np.where(seam[..., None], c * 0.62, c)


def post(f):
    return P.wood(f, light='#8f7552', dark='#5c4630', ring=2.4)


def soil(wet):
    def fn(f):
        base = '#3a2a1d' if wet else '#6b5238'
        c = P.plain(f, base, 0.16)
        clod = f.noise(2.6, 621, 2) > 0.62
        c = np.where(clod[..., None], c * 0.78, c)
        grit = f.noise(9.0, 622, 1) > 0.8
        return np.where(grit[..., None], P.mix(c, P.col('#9a8a74'), 0.35 if not wet else 0.12), c)
    return fn


def nail(f):
    return P.steel(f, 0.42, spread=0.1)


def leaf(base):
    def fn(f):
        c = P.plain(f, base, 0.1)
        vein = np.abs(f.noise(2.0, 631, 2) - 0.5) < 0.04
        return np.where(vein[..., None], c * 1.18, c)
    return fn


def veined(f):
    """Beet leaves: dark glossy green with red veins."""
    c = P.plain(f, '#2f5f2b', 0.08)
    vein = np.abs(f.noise(1.6, 641, 2) - 0.5) < 0.05
    return np.where(vein[..., None], P.col('#8a1f33'), c)


def stem_red(f):
    return P.plain(f, '#8f2236', 0.08)


def carrot_root(f):
    c = P.plain(f, '#e0701f', 0.08)
    ring = np.sin(f.Y * 9) > 0.7
    return np.where(ring[..., None], c * 0.85, c)


def potato_skin(f):
    c = P.plain(f, '#b98b52', 0.12)
    eye = f.noise(6.0, 651, 1) > 0.8
    return np.where(eye[..., None], P.col('#6e4c2a'), c)


def beet_root(f):
    c = P.plain(f, '#6f1a2d', 0.08)
    return c * (1 + (f.noise(1.4, 661, 2) - 0.5) * 0.25)[..., None]


def bark(f):
    """Grey-brown bark in long furrows, lichen in places."""
    c = P.plain(f, '#5b4a3a', 0.12)
    a, b = f.across()
    furrow = np.abs(np.sin(np.arctan2(a, b) * 7 + f.noise(0.4, 671, 2) * 4)) > 0.86
    c = np.where(furrow[..., None], c * 0.6, c)
    lichen = f.noise(1.2, 672, 3) > 0.7
    return np.where(lichen[..., None], P.mix(c, P.col('#7d8a62'), 0.45), c)


def cut(f):
    """Fresh axe-cut wood at the points: pale, in flat facets."""
    c = P.plain(f, '#d4b98a', 0.08)
    facet = np.sin(f.Y * 3.1 + f.X * 1.7) > 0.4
    return np.where(facet[..., None], c * 0.86, c)


def rope(f):
    c = P.plain(f, '#b8a77f', 0.1)
    twist = np.sin(f.along() * 10) > 0.3
    return np.where(twist[..., None], c * 0.8, c)


# ================================================================== the clock (shared by the item and the block)


def build_clock(m, cx=0.0, cz=0.0):
    """A twin-bell clock standing on y = 0, its dial to -z, centred on (cx, cz)."""
    y = 0.6
    for x in (-1.9, 1.9):                                           # splayed feet
        m.box([cx + x - 0.45, 0, cz - 0.4], [cx + x + 0.45, y + 0.3, cz + 0.4], 'chrome')
    R = 2.9
    yc = y + R
    m.rod(cz - 1.0, cz + 1.1, R, 'case', cx=cx, cz=yc, axis=2)          # the case, round, seen from the front
    m.rod(cz - 1.25, cz - 1.0, R - 0.25, 'chrome', cx=cx, cz=yc, axis=2)  # the bezel
    m.rod(cz - 1.3, cz - 1.24, 2.35, 'dial', cx=cx, cz=yc, axis=2)
    m.box([cx - 0.12, yc - 0.1, cz - 1.42], [cx + 0.12, yc + 1.7, cz - 1.32], 'hand')          # minute hand
    m.box([cx - 0.12, yc - 0.12, cz - 1.48], [cx + 1.25, yc + 0.12, cz - 1.38], 'hand')        # hour hand
    m.box([cx - 0.05, yc - 1.9, cz - 1.54], [cx + 0.05, yc + 0.2, cz - 1.48], 'red')          # seconds hand
    m.box([cx - 0.3, yc - 0.3, cz - 1.58], [cx + 0.3, yc + 0.3, cz - 1.5], 'chrome')          # the hub
    top = yc + R
    for x in (-1.7, 1.7):                                            # the bells, on posts
        m.box([cx + x * 0.7 - 0.12, top - 0.4, cz - 0.12], [cx + x * 0.7 + 0.12, top + 0.6, cz + 0.12], 'chrome')
        m.rod(top + 0.5, top + 1.5, 1.35, 'chrome', cx=cx + x, cz=cz)
        m.rod(top + 1.5, top + 1.85, 0.8, 'chrome', cx=cx + x, cz=cz)
    m.box([cx - 0.15, top - 0.2, cz - 0.15], [cx + 0.15, top + 1.3, cz + 0.15], 'chrome')       # the hammer
    m.box([cx - 0.4, top + 1.1, cz - 0.4], [cx + 0.4, top + 1.6, cz + 0.4], 'chrome')
    for x in (-2.4, 2.4):                                            # the carry loop over the bells
        m.box([cx + x - 0.15, top + 1.6, cz - 0.15], [cx + x + 0.15, top + 2.6, cz + 0.15], 'chrome')
    m.box([cx - 2.55, top + 2.6, cz - 0.15], [cx + 2.55, top + 2.9, cz + 0.15], 'chrome')
    for x in (-0.9, 0.9):                                            # winding keys on the back
        m.box([cx + x - 0.3, yc - 0.6, cz + 1.1], [cx + x + 0.3, yc + 0.6, cz + 1.5], 'chrome')


CLOCK = dict(case=enamel(), chrome=chrome, dial=dial, hand=hand, red=red_hand)


def build_packet(m):
    m.box([-3, 0, -0.2], [3, 8.5, 0.2], 'front')
    m.box([-3, 7.6, -0.25], [3, 8.5, -0.2], 'paper')                 # the flap folded over the back


# ================================================================== the planter (block space, 0..16)


def build_planter(m):
    for x0, z0, x1, z1 in ((0, 0, 16, 1.6), (0, 14.4, 16, 16), (0, 1.6, 1.6, 14.4), (14.4, 1.6, 16, 14.4)):
        m.box([x0, 0, z0], [x1, 10, z1], 'plank')
    for x in (0.0, 13.6):                                            # corner posts, a touch proud
        for z in (0.0, 13.6):
            m.box([x - 0.2, 0, z - 0.2], [x + 2.6, 10.6, z + 2.6], 'post')
    for x in (4.5, 11.5):                                            # nail heads on the long sides
        for y in (2.5, 7.5):
            m.box([x, y, -0.25], [x + 0.4, y + 0.4, 0], 'nail')
            m.box([x, y, 16], [x + 0.4, y + 0.4, 16.25], 'nail')
    m.box([1.6, 0, 1.6], [14.4, 8.8, 14.4], 'soil')


SPOTS = [(x, z) for x in (4.0, 8.0, 12.0) for z in (4.0, 8.0, 12.0)]


def build_carrot(stage):
    def build(m):
        h = (1.4, 2.8, 4.4, 5.4)[stage]
        for i, (x, z) in enumerate(SPOTS):
            for k, (dx, dz) in enumerate(((-0.5, 0), (0.5, 0.2), (0, -0.5))):
                hh = h * (0.8 + 0.1 * ((i + k) % 3))
                m.box([x + dx - 0.15, 8.8, z + dz - 0.15], [x + dx + 0.15, 8.8 + hh, z + dz + 0.15], 'leaf')
                if stage >= 1:
                    m.box([x + dx - 0.6, 8.8 + hh * 0.7, z + dz - 0.08], [x + dx + 0.6, 8.8 + hh * 0.95, z + dz + 0.08], 'leaf')
            if stage == 3:
                m.box([x - 0.7, 8.4, z - 0.7], [x + 0.7, 9.3, z + 0.7], 'carrot')
    return build


def build_potato(stage):
    def build(m):
        s = (0.8, 1.6, 2.4, 2.6)[stage]
        for i, (x, z) in enumerate(SPOTS):
            if stage == 0 and i % 2:
                continue
            h = s * 1.5
            m.box([x - 0.2, 8.8, z - 0.2], [x + 0.2, 8.8 + h, z + 0.2], 'stem')
            # a clump of oval leaves: a crown and leaves leaning out on four sides
            m.box([x - s * 0.4, 8.8 + h * 0.6, z - s * 0.4], [x + s * 0.4, 8.8 + h + 0.4, z + s * 0.4], 'leaf')
            for k, (dx, dz) in enumerate(((1, 0.3), (-1, -0.3), (0.3, -1), (-0.3, 1))):
                lift = 0.3 * ((i + k) % 3)
                m.box([x + dx * s * 0.55 - s * 0.32, 8.8 + h * 0.45 + lift, z + dz * s * 0.55 - s * 0.32],
                      [x + dx * s * 0.55 + s * 0.32, 8.8 + h * 0.75 + lift, z + dz * s * 0.55 + s * 0.32], 'leaf')
            if stage == 3 and i % 3 == 0:
                m.box([x + 0.8, 8.5, z - 0.6], [x + 2.0, 9.3, z + 0.4], 'potato')
    return build


def build_beet(stage):
    def build(m):
        h = (1.2, 2.4, 3.8, 4.6)[stage]
        for i, (x, z) in enumerate(SPOTS):
            for k, (dx, dz) in enumerate(((-0.9, 0), (0.9, 0), (0, 0.9))):
                m.box([x + dx * 0.4 - 0.12, 8.8, z + dz * 0.4 - 0.12], [x + dx * 0.4 + 0.12, 8.8 + h * 0.6, z + dz * 0.4 + 0.12], 'stem')
                if stage >= 1:
                    w = 0.5 + 0.25 * stage
                    m.box([x + dx - w * 0.5, 8.8 + h * 0.5, z + dz - w * 0.5], [x + dx + w * 0.5, 8.8 + h, z + dz + w * 0.5], 'veined')
            if stage == 3:
                m.box([x - 1.0, 8.5, z - 1.0], [x + 1.0, 9.9, z + 1.0], 'beet')
    return build


CROPS = {
    'carrot': (build_carrot, dict(leaf=leaf('#4a8f3a'), carrot=carrot_root)),
    'potato': (build_potato, dict(leaf=leaf('#3f7a34'), stem=leaf('#4f7d3a'), potato=potato_skin)),
    'beet': (build_beet, dict(veined=veined, stem=stem_red, beet=beet_root)),
}
RIPE_LEAF = {'potato': '#6c7434'}


# ================================================================== the spiked log wall (block space, facing north)


def build_spiked_wall(m):
    for x in (2.7, 8.0, 13.3):
        m.rod(0, 21.0, 2.65, 'bark', cx=x, cz=8.0)
        m.rod(21.0, 23.6, 2.0, 'cut', cx=x, cz=8.0)
        m.rod(23.6, 25.9, 1.3, 'cut', cx=x, cz=8.0)
        m.rod(25.9, 27.8, 0.55, 'cut', cx=x, cz=8.0)
    for y in (5.0, 15.0):                                             # split rails nailed across the inside
        m.box([0, y, 10.65], [16, y + 2.2, 11.9], 'rail')
        for x in (2.7, 8.0, 13.3):
            m.box([x - 0.2, y + 0.9, 11.9], [x + 0.2, y + 1.3, 12.1], 'nail')


WALL = dict(bark=bark, cut=cut, rope=rope, rail=lambda f: P.wood(f, light='#9a7f5a', dark='#5f4a33', ring=2.2), nail=nail)


# ================================================================== writing it out


def paint(m, name, wear, mats):
    seed = zlib.crc32(name.encode()) % 1000
    patches = M.paint_model(m, worn_look(wear, 0.0, **mats), wear, seed)
    return M.pack(patches)


def block_model(m, name, wear, mats, px=None):
    old = M.PX
    if px:
        M.PX = px
    m.tidy()
    img, uvs, T = paint(m, name, wear, mats)
    M.PX = old
    M.save_png(img, ASSETS / f'textures/block/building/{name}.png')
    out = M.model_json(m, uvs, f'{NS}:block/building/{name}', {}, gui_light='side')
    del out['display']
    out['ambientocclusion'] = False
    M.dump(out, ASSETS / f'models/block/building/{name}.json')
    print(f'{name}: {len(m.parts)} boxes, {T}px')


def facing_states(model, extra=()):
    keys = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        keys[f'facing={f}'] = {'model': model, **({'y': y} if y else {})}
    return {'variants': keys}


ITEMS = {'alarm_clock': 'Alarm Clock', 'carrot_seeds': 'Carrot Seeds', 'seed_potatoes': 'Seed Potatoes', 'beet_seeds': 'Beet Seeds'}


def main():
    merge_lang({f'item.{NS}.{k}': v for k, v in ITEMS.items()})
    merge_lang({f'block.{NS}.alarm_clock_decoy': 'Alarm Clock', f'block.{NS}.planter_box': 'Planter Box',
                f'block.{NS}.spiked_log_wall': 'Spiked Log Wall'})
    (ASSETS / 'textures/item/building').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'textures/block/building').mkdir(parents=True, exist_ok=True)
    # items
    items = [('alarm_clock', build_clock, 0.25, 7.0, CLOCK)]
    for crop, item in (('carrot', 'carrot_seeds'), ('potato', 'seed_potatoes'), ('beet', 'beet_seeds')):
        items.append((item, build_packet, 0.2, 6.0, dict(front=packet(crop), paper=paper)))
    for name, build, wear, held, mats in items:
        m = M.Model(name)
        build(m)
        if name == 'alarm_clock':
            # the dial to +z, the side an item shows in the inventory
            for p in m.parts:
                p['lo'][2], p['hi'][2] = -p['hi'][2], -p['lo'][2]
        lo, hi = m.bounds()
        m.shift(C - (lo + hi) / 2)
        m.tidy()
        img, uvs, T = paint(m, name, wear, mats)
        M.save_png(img, ASSETS / f'textures/item/building/{name}.png')
        M.dump(M.model_json(m, uvs, f'{NS}:item/building/{name}', held_display(m, held), gui_light='side'),
               ASSETS / f'models/item/building/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/building/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')
    # the clock set down: the same clock, a little bigger, in the middle of the block
    m = M.Model('alarm_clock_decoy')
    build_clock(m, 8.0, 8.0)
    block_model(m, 'alarm_clock_decoy', 0.3, CLOCK)
    # the planter: dry and wet earth, then each crop at four stages, laid over it
    for wet in (False, True):
        m = M.Model('planter_box')
        build_planter(m)
        mats = dict(plank=plank, post=post, nail=nail, soil=soil(wet))
        block_model(m, f'planter_box_{"wet" if wet else "dry"}', 0.35, mats, px=6)
    for crop, (build, mats) in CROPS.items():
        for stage in range(4):
            m = M.Model(f'{crop}_{stage}')
            build(stage)(m)
            use = dict(mats)
            if stage == 3 and crop in RIPE_LEAF:
                use['leaf'] = leaf(RIPE_LEAF[crop])
            block_model(m, f'planter_{crop}_{stage}', 0.1, use, px=4)
    # the spiked log wall
    m = M.Model('spiked_log_wall')
    build_spiked_wall(m)
    block_model(m, 'spiked_log_wall', 0.4, WALL, px=6)

    bs = ASSETS / 'blockstates'
    M.dump(facing_states(f'{NS}:block/building/alarm_clock_decoy'), bs / 'alarm_clock_decoy.json')
    M.dump(facing_states(f'{NS}:block/building/spiked_log_wall'), bs / 'spiked_log_wall.json')
    parts = [{'when': {'wet': 'false'}, 'apply': {'model': f'{NS}:block/building/planter_box_dry'}},
             {'when': {'wet': 'true'}, 'apply': {'model': f'{NS}:block/building/planter_box_wet'}}]
    for crop in CROPS:
        for stage, ages in enumerate(('0|1', '2|3', '4|5|6', '7')):
            parts.append({'when': {'crop': crop, 'age': ages}, 'apply': {'model': f'{NS}:block/building/planter_{crop}_{stage}'}})
    M.dump({'multipart': parts}, bs / 'planter_box.json')
    # what each gives back when a player takes it down
    drops = {'alarm_clock_decoy': [('alarm_clock', 1)], 'planter_box': [('minecraft:oak_planks', 2), ('minecraft:dirt', 2)],
             'spiked_log_wall': [('minecraft:oak_log', 1)]}
    for block, items in drops.items():
        table = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
                 'name': item if ':' in item else f'{NS}:{item}',
                 'functions': [{'function': 'minecraft:set_count', 'count': n}]}],
                 'conditions': [{'condition': 'minecraft:survives_explosion'}]} for item, n in items]}
        path = DATA / f'{NS}/loot_table/blocks/{block}.json'
        path.write_text(json.dumps(table, indent=2) + '\n')


if __name__ == '__main__':
    main()
