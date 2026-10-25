"""Gun case and ammo can item models for ZomboidCraft: a long hard rifle case, a hard pistol case and an M2A1 .50 cal
ammo can, built from boxes and painted per face with the weapon tooling (tools/weapons/model.py, paint.py), the way
tools/items/gen_bags.py builds the bags.

Writes models/item/case/<name>.json, textures/item/case/<name>.png and items/<name>.json.

    python3 tools/items/gen_cases.py
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
from gen_bags import VANILLA, display, worn_look  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
C = np.array([8.0, 8.0, 8.0])


# ================================================================== materials


def shell(base='#1b1c1e'):
    """A moulded polymer case shell: a fine pebble texture and a few pale scuffs. No edge wear: the shell is several
    overlapping boxes, and a rim round each of their faces read as a grid of seams."""
    def fn(f):
        c = np.ones(f.shape + (3,)) * P.col(base)
        c = c * (1 + (P.white(f.shape, f.rng) - 0.5) * 0.14 + (f.noise(9.0, 301, 2) - 0.5) * 0.18)[..., None]
        scuff = f.noise(1.6, 302, 3) > 0.74
        return np.where(scuff[..., None], P.mix(c, P.col('#4d4f52'), 0.3), c)
    return fn


def label(f):
    # a paper ID card behind a clear window: a few ruled lines and a scrawled name
    c = P.plain(f, '#d8d4c4', 0.06)
    v = f.uv[..., 1]
    ruled = ((v * 5.0) % 1) < 0.08
    c = np.where(ruled[..., None], c * 0.8, c)
    ink = (np.sin(f.uv[..., 0] * 40 + np.sin(v * 9) * 3) > 0.6) & (((v * 5.0) % 1) > 0.35) & (((v * 5.0) % 1) < 0.65)
    return np.where(ink[..., None], P.col('#2b3f73'), c)


def od(colour='#4a5130'):
    """Olive drab enamel over steel, chipped here and there where it's been knocked about."""
    def fn(f):
        return P.paint(f, colour, steel_base=0.4, chips=0.12, gloss=0.08)
    return fn


# a 5-high stencil alphabet, 3 wide but for the M: just what an ammo can says
FONT = {'C': ('111', '100', '100', '100', '111'), 'A': ('010', '101', '111', '101', '101'),
        'L': ('100', '100', '100', '100', '111'), '.': ('0', '0', '0', '0', '1'),
        '5': ('111', '100', '111', '001', '111'), '0': ('111', '101', '101', '101', '111'),
        '1': ('010', '110', '010', '010', '111'), '-': ('000', '000', '111', '000', '000'),
        'M': ('10001', '11011', '10101', '10001', '10001'), '2': ('111', '001', '111', '100', '111'),
        ' ': ('00', '00', '00', '00', '00')}


def stencil(lines=('CAL .50', '1-M2A1'), colour='#c9b44a', scale=2):
    """Yellow stencilled lettering on the olive drab, on the face toward +z, worn through in places."""
    base = od()

    def fn(f):
        c = base(f)
        if f.n[2] < 0.5:
            return c
        u, v = f.uv[..., 0] * M.PX, f.uv[..., 1] * M.PX            # texels, v down from the top
        W, H = float(u.max()) + 0.5, float(v.max()) + 0.5
        line_h = 5 * scale
        y0 = (H - (len(lines) * line_h + (len(lines) - 1) * scale)) / 2
        ink = np.zeros(f.shape, bool)
        for li, text in enumerate(lines):
            widths = [len(FONT[ch][0]) for ch in text]
            x0 = (W - (sum(widths) + len(text) - 1) * scale) / 2
            gx = np.floor((u - x0) / scale).astype(int)
            gy = np.floor((v - y0 - li * (line_h + scale)) / scale).astype(int)
            at = 0
            for ch, w in zip(text, widths):
                for r, row in enumerate(FONT[ch]):
                    for col, bit in enumerate(row):
                        if bit == '1':
                            ink |= (gx == at + col) & (gy == r)
                at += w + 1
        worn = f.noise(3.0, 311, 2) > 0.8
        return np.where((ink & ~worn)[..., None], P.mix(c, P.col(colour), 0.85), c)
    return fn


LATCH = lambda f: P.polymer(f, '#2a2b2e', 0.06)          # noqa: E731
STEEL = lambda f: P.steel(f, 0.4, spread=0.1)            # noqa: E731
RUBBER = lambda f: P.rubber(f, '#121212', 0.2)           # noqa: E731
GASKET = lambda f: P.plain(f, '#101010', 0.05)           # noqa: E731


# ================================================================== the cases
# Built standing on a long edge the way you carry them: length along x, the handle on top (+y), the lid facing +z.


def hard_case(m, L, H, T, latches, handle_w, ribs, valve=None, card=None, tabs=()):
    """A moulded hard case L long, H tall and T thick: two shells meeting at a rim, bumpered corners, ribs pressed
    across both faces, latches and a handle on the top edge, hinges along the bottom, feet under it."""
    x0, x1, t = -L / 2, L / 2, T / 2
    m.box([x0 + 0.25, 0.25, -t], [x1 - 0.25, H - 0.25, t], 'shell')                    # the two shells
    m.box([x0, 0.25, -t + 0.25], [x1, H - 0.25, t - 0.25], 'shell')                    # rounded off at the ends
    m.box([x0 + 0.25, 0, -t + 0.25], [x1 - 0.25, H, t - 0.25], 'shell')                # and along top and bottom
    m.box([x0 - 0.12, -0.12, -0.22], [x1 + 0.12, H + 0.12, 0.22], 'rim')               # where the halves meet
    for x in (x0 + 0.1, x1 - 1.3):                                                      # corner bumpers
        for y in (0.1, H - 1.3):
            m.box([x - 0.05, y - 0.05, -t - 0.05], [x + 1.25, y + 1.25, -t + 0.6], 'rim')
            m.box([x - 0.05, y - 0.05, t - 0.6], [x + 1.25, y + 1.25, t + 0.05], 'rim')
    for z in (-t - 0.12, t):                                                            # ribs pressed across the faces
        for y in ribs:
            m.box([x0 + 1.4, y, z], [x1 - 1.4, y + 0.45, z + 0.12], 'shell')
    for x in latches:                                                                   # draw latches over the rim
        m.box([x - 0.7, H - 0.1, -0.75], [x + 0.7, H + 0.35, 0.75], 'latch')
        m.box([x - 0.55, H - 0.9, t - 0.05], [x + 0.55, H + 0.2, t + 0.22], 'latch')    # its lever down the lid
        m.box([x - 0.3, H - 0.75, t + 0.2], [x + 0.3, H - 0.45, t + 0.3], 'steel')      # the pin
    for x in tabs:                                                                      # padlock tabs
        m.box([x - 0.35, H - 0.05, -0.32], [x + 0.35, H + 0.65, 0.32], 'latch')
        m.box([x - 0.12, H + 0.25, -0.34], [x + 0.12, H + 0.45, 0.34], 'hole')
    hw = handle_w / 2                                                                    # the carry handle
    for x in (-hw - 0.2, hw - 0.4):
        m.box([x, H - 0.05, -0.45], [x + 0.6, H + 1.0, 0.45], 'latch')
    m.box([-hw, H + 0.85, -0.5], [hw, H + 1.55, 0.5], 'grip')
    n = max(2, int(L // 5))                                                              # hinges along the bottom
    for i in range(n):
        x = x0 + 1.8 + i * (L - 3.6 - 1.2) / (n - 1)
        m.box([x, -0.25, -0.42], [x + 1.2, 0.2, 0.42], 'latch')
    for x in (x0 + 1.0, x1 - 2.0):                                                      # feet
        m.box([x, -0.4, -t + 0.4], [x + 1.0, 0.0, -t + 1.0], 'grip')
        m.box([x, -0.4, t - 1.0], [x + 1.0, 0.0, t - 0.4], 'grip')
    if valve is not None:                                                                # the pressure relief valve
        vx, vy = valve
        m.box([vx - 0.45, vy - 0.45, t], [vx + 0.45, vy + 0.45, t + 0.14], 'latch')
        m.box([vx - 0.2, vy - 0.2, t + 0.14], [vx + 0.2, vy + 0.2, t + 0.2], 'steel')
    if card is not None:                                                                 # the ID card window
        (cx0, cy0), (cx1, cy1) = card
        m.box([cx0 - 0.1, cy0 - 0.1, t], [cx1 + 0.1, cy1 + 0.1, t + 0.1], 'rim')
        m.box([cx0, cy0, t + 0.1], [cx1, cy1, t + 0.13], 'label')


def build_rifle_case(m):
    # a 44" by 14" by 5" long gun case (a Pelican or Plano sort), at about 1.5" a pixel
    hard_case(m, 30.0, 9.6, 3.4, latches=(-11.5, -6.2, 6.2, 11.5), handle_w=4.6, ribs=(2.0, 3.6, 5.6, 7.2),
              valve=(12.6, 4.6), card=((-13.2, 5.8), (-9.8, 7.6)), tabs=(-8.6, 8.6))


def build_pistol_case(m):
    # a 16" by 12" by 4" handgun case
    hard_case(m, 12.0, 8.6, 3.0, latches=(-3.6, 3.6), handle_w=3.4, ribs=(2.0, 3.4, 5.0, 6.4),
              card=((-4.6, 0.9), (-2.2, 2.1)))


def build_ammo_can(m):
    # an M2A1 .50 cal can: 12" long, 6" wide, 7.5" tall, at 1.2" a pixel; lid hinged on the back, clamp lever on the
    # right end, a folding wire handle on top, stencilled on the front
    m.box([-4.9, 0, -2.4], [4.9, 5.35, 2.4], 'od')
    m.box([-4.6, 0.7, 2.4], [4.6, 4.7, 2.52], 'od')                                     # the pressed panels
    m.box([-4.6, 0.7, -2.52], [4.6, 4.7, -2.4], 'od')
    m.box([-3.6, 1.3, 2.52], [3.6, 4.2, 2.56], 'stencil')                                # CAL .50 / 1-M2A1
    m.box([-4.75, -0.12, -2.25], [4.75, 0.05, 2.25], 'od')                              # the rolled bottom seam
    m.box([-4.95, 5.25, -2.45], [4.95, 5.36, 2.45], 'gasket')                            # the lid's rubber seal
    m.box([-5.0, 5.35, -2.5], [5.0, 6.0, 2.5], 'od_lid')
    m.box([-4.7, 5.95, -2.2], [4.7, 6.12, 2.2], 'od_lid')                                # its raised top
    m.box([-4.5, 5.3, -2.82], [4.5, 5.85, -2.48], 'od')                                  # the hinge down the back
    for x in (-2.5, 2.5):                                                                # the wire handle, folded flat
        m.box([x - 0.25, 6.1, -0.3], [x + 0.25, 6.4, 0.3], 'od')
    m.box([-2.4, 6.18, -0.14], [2.4, 6.34, 0.14], 'wire')
    m.box([4.98, 2.6, -0.95], [5.3, 5.5, 0.95], 'od')                                    # the clamp lever, shut
    m.box([5.25, 5.2, -1.1], [5.55, 6.15, 1.1], 'od')                                    # its cam over the lid
    m.box([4.95, 3.9, -1.25], [5.2, 4.6, 1.25], 'od')                                    # the pivot bracket


CASES = [
    ('rifle_case', build_rifle_case, 0.35, 0.12, dict(
        shell=shell(), rim=shell('#151618'), latch=LATCH, steel=STEEL, grip=RUBBER, hole=GASKET, label=label)),
    ('pistol_case', build_pistol_case, 0.35, 0.12, dict(
        shell=shell('#1d1e20'), rim=shell('#151618'), latch=LATCH, steel=STEEL, grip=RUBBER, hole=GASKET,
        label=label)),
    ('ammo_can', build_ammo_can, 0.3, 0.08, dict(
        od=od(), od_lid=od('#474e2e'), stencil=stencil(), gasket=GASKET,
        wire=lambda f: P.paint(f, '#3f452a', steel_base=0.45, chips=0.8))),
]


NAMES = {'rifle_case': 'Rifle Case', 'pistol_case': 'Pistol Case', 'ammo_can': 'Ammo Can'}
# how big each looks carried (pixels across): nearer true size beside a 32-pixel player than the bags' 10, so a long
# gun's case reads as one
HELD = {'rifle_case': 17.0, 'pistol_case': 8.0, 'ammo_can': 6.5}


def held_display(m, size):
    disp = display(m)
    for ctx, (rot, trans, _scale) in VANILLA.items():
        s, t = M.fit(m, M.R_xyz(*rot), size)
        disp[ctx] = M.transform(rot, np.array(trans) + t, s)
    return disp


def merge_lang(lines):
    path = ASSETS / 'lang/en_us.json'
    data = json.loads(path.read_text())
    data.update(lines)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')


def main():
    merge_lang({f'item.{NS}.{k}': v for k, v in NAMES.items()})
    for name, build, wear, blood, mats in CASES:
        m = M.Model(name)
        build(m)
        lo, hi = m.bounds()
        m.shift(C - (lo + hi) / 2)
        m.tidy()
        seed = zlib.crc32(name.encode()) % 1000
        patches = M.paint_model(m, worn_look(wear, blood, **mats), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/case/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        M.dump(M.model_json(m, uvs, f'{NS}:item/case/{name}', held_display(m, HELD[name]), gui_light='side'),
               ASSETS / f'models/item/case/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/case/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
