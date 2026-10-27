"""World loot item models, built from boxes and painted in the character style through the weapon tooling
(tools/weapons/kit.py: toon outline round each part's outside, chips, lit tops). Items of one category share one
texture sheet (performance thread: no per-item 2048s; one 512-1024 sheet per category).

Sizes are real: one model unit is an inch. Each build stands on y = 0, front toward +z.

    python3 tools/items/loot/gen_loot.py [--only REGEX] [--out DIR]

--out writes a preview set (models + sheets + a manifest for the viewer) instead of the mod's assets.
"""
import argparse
import json
import re
import sys
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
import model as M  # noqa: E402
import paint as P  # noqa: E402
import kit  # noqa: E402

NS = 'zomboidcraft'
WEAR = 0.3          # found in the world: handled, a little scuffed, never battered


# ================================================================== painting helpers


def ring(f, cx=0.0, cz=0.0):
    """Around a round part: angle 0..1 and whether the face is a side (not a lid)."""
    a = (np.arctan2(f.Z - cz, f.X - cx) / (2 * np.pi)) % 1.0
    return a, abs(f.n[1]) < 0.5


def lines(f, u, v, rows, x0, x1, h=0.06, seed=0):
    """Rows of small print: broken dashes between u x0..x1 at the heights in rows (v)."""
    ink = np.zeros(f.shape, bool)
    for r in rows:
        band = (np.abs(v - r) < h / 2) & (u > x0) & (u < x1)
        gaps = np.sin(u * 61.0 + r * 17 + seed) + np.sin(u * 23.0 + seed * 3) > -0.6
        ink |= band & gaps
    return ink


def tin(f):
    """Tinplate: bright, faintly blue steel with rolled beads."""
    c = P.steel(f, 0.7, tint='#b9c0c8', spread=0.1)
    beads = (np.sin(f.Y * 9.0) > 0.55) & (abs(f.n[1]) < 0.5)
    return np.where(beads[..., None], c * 0.82, c)


def can_label(base, band, art, art2):
    """A paper label round a food can: a coloured field, a pale window band with the product drawn in it, the
    name in a block across the top of the window and small print round the back. Height is in inches over the
    label's own span."""
    def fn(f):
        a, side = ring(f)
        c = P.plain(f, base, 0.08)
        if not side:
            return c
        lo, hi = f.box['lo'][1], f.box['hi'][1]
        v = (f.Y - lo) / max(hi - lo, 1e-6)
        front = np.abs(((a - 0.25 + 0.5) % 1.0) - 0.5)         # 0 at the front (+z)
        win = (v > 0.18) & (v < 0.72) & (front < 0.2)
        c = np.where(win[..., None], P.plain(f, band, 0.05), c)
        # the product: a bowl of it drawn in the lower window, a heap of round pieces in a dark sauce
        fx, fy = (a - 0.25) * 9.4, (v - 0.33) * (hi - lo)
        bowl = (fx / 1.0) ** 2 + (fy / 0.55) ** 2 < 1
        pieces = bowl & (np.sin(fx * 9) * np.sin(fy * 11) > 0.1)
        c = np.where(bowl[..., None], P.col(art2), c)
        c = np.where(pieces[..., None], P.col(art), c)
        name = (v > 0.55) & (v < 0.68) & (front < 0.16)
        c = np.where(name[..., None], P.mix(c, P.col(base), 0.9) * 0.55, c)
        stripe = (np.abs(v - 0.82) < 0.04) | (np.abs(v - 0.1) < 0.03)
        c = np.where(stripe[..., None], P.col(band) * 0.9, c)
        back = lines(f, a, v, (0.35, 0.45, 0.55), 0.62, 0.88, 0.05)
        c = np.where(back[..., None], c * 0.45, c)
        # a torn corner and a water stain
        stain = f.noise(1.2, 73, 3) > 0.74
        return np.where(stain[..., None], P.mix(c, P.col('#7a6a48'), 0.35), c)
    return fn


def printed(colour, gloss=0.04):
    def fn(f):
        return P.plain(f, colour, 0.06) * (1 + gloss * (f.noise(0.6, 81, 2) - 0.5))[..., None]
    return fn


def cardboard(f):
    c = P.plain(f, '#a98b62', 0.1)
    flutes = (np.sin(f.uv[..., 0] * 40) > 0.7)
    return np.where(flutes[..., None], c * 0.94, c)


def glass(tint, liquid=None, level=1.0):
    """Thick bottle glass: the tint, a bright streak where it catches the light, the liquid darker below its line."""
    def fn(f):
        c = P.plain(f, tint, 0.05)
        lo, hi = f.box['lo'][1], f.box['hi'][1]
        if liquid is not None:
            full = f.Y < lo + (hi - lo) * level
            c = np.where(full[..., None], P.mix(c, P.col(liquid), 0.7), c)
        u = f.uv[..., 0] / max(f.uv[..., 0].max(), 1e-6)
        glint = (np.abs(u - 0.25) < 0.06) & (abs(f.n[1]) < 0.5)
        return np.where(glint[..., None], np.minimum(c * 1.8 + 0.12, 1), c)
    return fn


def gold(f):
    c = P.mix(P.col('#b8892e'), P.col('#e7c56a'), f.noise(2.0, 91, 3))
    return c * (1 + (f.noise(9, 92, 1) - 0.5) * 0.08)[..., None]


# ================================================================== builds


def build_canned_beans(m):
    # a 15 oz can: 3" across, 4.4" tall, rolled rims top and bottom, beads pressed into the body under the label
    m.rod(0.0, 0.2, 1.5, 'tin')
    m.rod(0.2, 4.2, 1.45, 'label')
    m.rod(4.2, 4.4, 1.5, 'tin')
    m.rod(4.4, 4.45, 1.25, 'lid')                                 # the lid sits just inside the rim


def build_cola_can(m):
    # 12 oz: 2.6" across, 4.8" tall; the neck tapers in to the lid, a pull-tab lying flat on it
    m.rod(0.0, 0.25, 1.15, 'alu')                                 # the domed foot
    m.rod(0.25, 4.05, 1.3, 'livery')
    m.rod(4.05, 4.45, 1.18, 'livery')                             # the shoulder
    m.rod(4.45, 4.7, 1.05, 'alu')                                 # the neck
    m.rod(4.7, 4.8, 1.1, 'alu')                                   # the rim
    m.box([-0.25, 4.78, -0.55], [0.25, 4.84, 0.45], 'tab')
    m.box([-0.32, 4.78, 0.05], [0.32, 4.84, 0.55], 'tab')


def build_cereal_box(m):
    # 7.5" x 11" x 2.5", the top flaps tucked in but one corner lifted where someone tore it open
    m.box([-3.75, 0, -1.25], [3.75, 11.0, 1.25], 'cereal')
    m.box([-3.75, 11.0, -1.25], [0.2, 11.15, 1.25], 'cereal_top')
    m.box([0.2, 11.0, -1.25], [3.75, 11.6, 1.25], 'cereal_top', rot=dict(axis='z', angle=-22.5, origin=[0.2, 11.0, 0]))


def build_rx_bottle(m):
    # a 2" amber prescription vial with a white push-and-turn cap and a printed label round the front
    m.rod(0.0, 2.4, 0.75, 'amber')
    m.rod(0.6, 1.9, 0.77, 'rx_label')
    m.rod(2.4, 3.05, 0.86, 'cap')
    for a in range(0, 360, 45):                                   # the cap's grip ridges
        x, z = 0.86 * np.cos(np.radians(a)), 0.86 * np.sin(np.radians(a))
        m.box([x - 0.06, 2.45, z - 0.06], [x + 0.06, 3.0, z + 0.06], 'cap')


def build_first_aid_kit(m):
    # a steel wall kit, 10" x 7" x 3": body, a lid with its red cross, two latches and a folding handle
    m.box([-5.0, 0, -1.5], [5.0, 7.0, 0.9], 'kit_body')
    m.box([-5.0, 0, 0.9], [5.0, 7.0, 1.5], 'kit_lid')
    m.box([-1.6, 2.9, 1.5], [1.6, 4.1, 1.56], 'cross')
    m.box([-0.6, 1.9, 1.5], [0.6, 5.1, 1.56], 'cross')
    for x in (-3.6, 3.6):
        m.box([x - 0.5, 6.2, 0.6], [x + 0.5, 6.8, 1.75], 'latch')
    m.box([-1.6, 7.0, -0.25], [-1.3, 7.7, 0.25], 'latch')
    m.box([1.3, 7.0, -0.25], [1.6, 7.7, 0.25], 'latch')
    m.box([-1.6, 7.6, -0.3], [1.6, 7.9, 0.3], 'handle')


def build_cassette_player(m):
    # a pocket cassette player, 4.3" x 3.4" x 1.3": the shell, a smoked window over the tape, a row of piano keys
    # along the top, a volume wheel on the side and a belt clip on the back
    m.box([-2.15, 0, -0.65], [2.15, 3.4, 0.65], 'player')
    m.box([-1.6, 0.55, 0.65], [1.6, 2.45, 0.72], 'window')
    for i, x in enumerate((-1.7, -0.9, -0.1, 0.7)):
        m.box([x, 3.4, -0.4], [x + 0.65, 3.62, 0.4], 'key_orange' if i == 1 else 'keys')
    m.rod(1.4, 2.4, 0.42, 'keys', cx=2.15, cz=0.0, axis=1)        # the volume wheel
    m.box([-1.2, 0.8, -0.85], [1.2, 2.8, -0.65], 'clip')
    m.box([-1.9, 2.75, 0.65], [-0.9, 3.05, 0.7], 'badge')


def build_whiskey(m):
    # a squared fifth: 3.4" x 2.3" body, rounded shoulder, long neck, black screw cap; the label a black panel
    m.box([-1.7, 0, -1.15], [1.7, 6.6, 1.15], 'whiskey')
    m.box([-1.45, 6.6, -0.95], [1.45, 7.2, 0.95], 'whiskey')
    m.rod(7.2, 9.6, 0.5, 'whiskey_neck')
    m.rod(9.6, 10.6, 0.56, 'cap_black')
    m.box([-1.45, 1.3, 1.15], [1.45, 4.9, 1.2], 'whiskey_label')
    m.box([-0.45, 7.6, 0.48], [0.45, 8.5, 0.56], 'whiskey_label')  # the neck band


def build_pocket_watch(m):
    # a 2" hunter case lying open-face up: the case, the crystal over the dial, the crown and bow at 12
    m.rod(0.0, 0.7, 1.0, 'gold', axis=1)
    m.rod(0.7, 0.78, 0.85, 'dial', axis=1)
    m.rod(0.78, 0.84, 0.5, 'crystal', axis=1)
    m.box([-0.18, 0.15, 1.0], [0.18, 0.45, 1.3], 'gold')          # the pendant
    m.rod(-0.27, 0.27, 0.22, 'gold', cx=0.3, cz=1.38, axis=0)   # the crown
    m.box([-0.5, 0.2, 1.35], [-0.35, 0.4, 1.75], 'gold')         # the bow, lying flat
    m.box([0.35, 0.2, 1.35], [0.5, 0.4, 1.75], 'gold')
    m.box([-0.5, 0.2, 1.75], [0.5, 0.4, 1.9], 'gold')


def build_gold_bar(m):
    # a 1 kg cast bar: 4.6" x 2" at the foot, narrower at the top, the stamp panel pressed into the face
    m.box([-2.3, 0, -1.0], [2.3, 0.5, 1.0], 'gold')
    m.box([-2.1, 0.5, -0.85], [2.1, 0.95, 0.85], 'gold')
    m.box([-1.4, 0.95, -0.45], [1.4, 0.99, 0.45], 'stamp')


# ================================================================== looks


def cola_livery(f):
    a, side = ring(f)
    c = P.plain(f, '#a3241f', 0.05)
    if not side:
        return c
    v = f.Y / 4.45
    swoosh = np.abs(v - 0.45 - 0.12 * np.sin(a * 2 * np.pi * 2)) < 0.05
    c = np.where(swoosh[..., None], P.col('#e8e1d4'), c)
    name = (np.abs(v - 0.65) < 0.07) & (np.abs(((a - 0.25 + 0.5) % 1) - 0.5) < 0.17)
    return np.where(name[..., None], P.col('#efe9de'), c)


def cereal(f):
    c = P.plain(f, '#d8a52a', 0.06)
    if f.n[2] < 0.5:                                              # sides and back: nutrition panel, puzzles
        u, v = f.uv[..., 0], f.uv[..., 1]
        panel = (u > 0.4) & (u < 2.1) & (v > 1.0) & (v < 7.0) & (abs(f.n[0]) > 0.5)
        c = np.where(panel[..., None], P.col('#ece4cf'), c)
        return np.where((panel & (((v * 3.0) % 1) < 0.18))[..., None], c * 0.6, c)
    x, y = f.X, f.Y
    bowl = ((x / 2.6) ** 2 + ((y - 3.4) / 1.4) ** 2 < 1) & (y < 3.6)
    flakes = ((x / 2.4) ** 2 + ((y - 3.6) / 0.6) ** 2 < 1) & (f.noise(5, 101, 1) > 0.35)
    c = np.where(bowl[..., None], P.col('#e9e3d6'), c)
    c = np.where(flakes[..., None], P.mix(P.col('#c46a1c'), P.col('#e8b04a'), f.noise(8, 102, 1)), c)
    banner = (y > 7.6) & (y < 9.8)
    c = np.where(banner[..., None], P.col('#a12a22'), c)
    letters = banner & (np.abs(y - 8.7) < 0.55) & (np.sin(x * 5.5) > -0.3) & (np.abs(x) < 3.0)
    c = np.where(letters[..., None], P.col('#f2e7c8'), c)
    star = ((x - 2.4) ** 2 + (y - 5.6) ** 2) < 0.5
    return np.where(star[..., None], P.col('#3a5f9e'), c)


def rx_label(f):
    a, side = ring(f)
    c = P.plain(f, '#f0ece2', 0.04)
    if not side:
        return c
    v = (f.Y - 0.6) / 1.3
    ink = lines(f, a, v, (0.25, 0.45, 0.62), 0.05, 0.45, 0.07, seed=3)
    top = np.abs(v - 0.85) < 0.08
    c = np.where(top[..., None], P.col('#3b5c8c'), c)
    return np.where(ink[..., None], c * 0.4, c)


def kit_body(f):
    return P.paint(f, '#d9d4c6', steel_base=0.5, chips=0.35, gloss=0.06)


def player(f):
    c = P.polymer(f, '#9aa0a3', 0.05)
    lines_ = (np.abs(f.Y - 3.1) < 0.04) & (f.n[2] > 0.5)
    return np.where(lines_[..., None], c * 0.6, c)


def window(f):
    # a smoked window: the cassette behind it with its two reels and a strip of label
    c = P.plain(f, '#2f3133', 0.05)
    x, y = f.X, f.Y
    for cx in (-0.8, 0.8):
        r = np.hypot(x - cx, y - 1.5)
        c = np.where((r < 0.42)[..., None], P.col('#d8d2c2'), c)
        c = np.where((r < 0.18)[..., None], P.col('#1c1c1e'), c)
    lab = (y > 0.65) & (y < 0.95) & (np.abs(x) < 1.3)
    c = np.where(lab[..., None], P.col('#c8452d'), c)
    glint = np.abs((x + y) - 2.4) < 0.08
    return np.where(glint[..., None], c * 1.6 + 0.08, c)


def whiskey_label(f):
    c = P.plain(f, '#141414', 0.05)
    if f.n[2] < 0.5:
        return c
    u, v = f.uv[..., 0], f.uv[..., 1]
    frame = (np.abs(u - 0.15) < 0.04) | (np.abs(u - (u.max() + 0.05 - 0.15)) < 0.04)
    c = np.where(frame[..., None], P.col('#c9a54a'), c)
    name = (np.abs(v - 1.1) < 0.28) & (u > 0.5) & (u < u.max() - 0.4) & (np.sin(u * 9) > -0.4)
    c = np.where(name[..., None], P.col('#e5d7ae'), c)
    small = lines(f, u / max(u.max(), 1e-6), v, (2.0, 2.4, 2.8), 0.2, 0.8, 0.1)
    return np.where(small[..., None], P.col('#a0905e'), c)


def dial(f):
    c = P.plain(f, '#efe6d0', 0.03)
    x, z = f.X, f.Z
    r = np.hypot(x, z)
    ang = np.arctan2(z, x)
    ticks = (r > 0.68) & (r < 0.8) & (np.abs(((ang / (2 * np.pi)) * 12 + 0.5) % 1 - 0.5) < 0.09)
    c = np.where(ticks[..., None], P.col('#1d1d1f'), c)
    hand1 = (np.abs(x) < 0.04) & (z > 0) & (z < 0.6)
    hand2 = (np.abs(z + x * 0.5) < 0.04) & (x > 0) & (x < 0.45)
    c = np.where((hand1 | hand2)[..., None], P.col('#1d1d1f'), c)
    return np.where((r > 0.8)[..., None], gold(f), c)


def stamp(f):
    c = gold(f) * 0.85
    u, v = f.uv[..., 0], f.uv[..., 1]
    ink = lines(f, u / max(u.max(), 1e-6), v, (0.2, 0.45, 0.7), 0.1, 0.9, 0.1)
    return np.where(ink[..., None], c * 0.7, c)


LOOKS = {
    'canned_beans': dict(tin=tin, lid=tin, label=can_label('#8a2a22', '#efe3c4', '#a0522d', '#6b2f1a')),
    'cola_can': dict(alu=lambda f: P.steel(f, 0.72, tint='#c4c8cc', spread=0.08), livery=cola_livery,
                     tab=lambda f: P.steel(f, 0.78, tint='#c4c8cc', spread=0.05)),
    'cereal_box': dict(cereal=cereal, cereal_top=cereal),
    'strong_painkillers': dict(amber=glass('#b0601a', '#e8d9a8', 0.75), rx_label=rx_label, cap=printed('#ebe8e0')),
    'first_aid_kit': dict(kit_body=kit_body, kit_lid=kit_body, cross=printed('#b3261e'),
                          latch=lambda f: P.steel(f, 0.62, spread=0.1), handle=P.chrome),
    'cassette_player': dict(player=player, window=window, keys=printed('#2a2b2d'), key_orange=printed('#d06a1c'),
                            clip=printed('#3a3c3e'), badge=printed('#c8b07a')),
    'whiskey': dict(whiskey=glass('#6b3a12', '#7a3d0e', 0.85), whiskey_neck=glass('#6b3a12'),
                    cap_black=printed('#151515'), whiskey_label=whiskey_label),
    'pocket_watch': dict(gold=gold, dial=dial, crystal=glass('#d9dde0')),
    'gold_bar': dict(gold=gold, stamp=stamp),
}

BUILDS = {
    'canned_beans': build_canned_beans, 'cola_can': build_cola_can, 'cereal_box': build_cereal_box,
    'strong_painkillers': build_rx_bottle, 'first_aid_kit': build_first_aid_kit,
    'cassette_player': build_cassette_player, 'whiskey': build_whiskey, 'pocket_watch': build_pocket_watch,
    'gold_bar': build_gold_bar,
}


# ================================================================== output


def make(name):
    look = dict(LOOKS[name])
    look['_after'] = [lambda f, c: P.grime(f, c, 0.12 + f.wear * 0.2)]
    return kit.drawn(name, BUILDS[name], look=look, seed=zlib.crc32(name.encode()) % 1000, wear=WEAR)


def sheet(names, px=16):
    """Paints the named items and packs every face into one shared texture. Returns models and uvs."""
    models = [make(n) for n in names]
    patches = kit.paint(models, px=px)
    img, uvs, T = kit.pack(patches)
    return models, img, uvs, T


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='.')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    out = Path(a.out)
    names = [n for n in BUILDS if re.search(a.only, n)]
    models, img, uvs, T = sheet(names)
    kit.save_textures(img, out / 'sheet.png')
    manifest = []
    for mi, (n, m) in enumerate(zip(names, models)):
        lo, hi = m.bounds()
        shift = np.array([8.0, 8.0, 8.0]) - (lo + hi) / 2
        els = kit.elements(m, mi, uvs, shift)
        M.dump({'textures': {'0': 'sheet', 'particle': 'sheet'}, 'elements': els}, out / f'{n}.json')
        manifest.append(dict(name=n, size=(hi - lo).tolist()))
        print(f'{n}: {len(m.parts)} boxes')
    (out / 'manifest.json').write_text(json.dumps(manifest))
    print(f'sheet {T}px for {len(names)} items')


if __name__ == '__main__':
    main()
