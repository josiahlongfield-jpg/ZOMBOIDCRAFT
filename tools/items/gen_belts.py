"""Belt item models for ZomboidCraft's quick slots: a plain leather belt, a carpenter's tool belt and a police duty belt,
built at body scale (one model unit is one skin pixel) so the same model sits round a survivor's waist and, scaled up,
in the inventory. Painted with the bag tooling (tools/items/gen_bags.py).

References: refs/items/belts (the duty belt's basketweave, leather pouches with brass snaps, cuff case, key ring and
radio holder are from 01-community-affairs-duty-belt and 03-nablc-police-officer).

Writes models/item/gear/<name>.json, textures/item/gear/<name>.png and items/<name>.json.

    python3 tools/items/gen_belts.py
"""
import sys
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
sys.path.insert(0, str(ROOT / 'tools/items'))
import model as M  # noqa: E402
import paint as P  # noqa: E402
import gen_bags as B  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'

# The belt runs round the body's waist: the torso is 8 px wide and 4 deep, so the strap's inside is just that.
HALF_X, HALF_Z = 4.0, 2.0
STRAP = 0.3


# ================================================================== materials


def basketweave(base='#141414'):
    """A duty belt's moulded basketweave: little crossed blocks pressed into black leather."""
    def fn(f):
        c = P.plain(f, base, 0.1)
        u, v = f.uv[..., 0], f.uv[..., 1]
        cell = ((np.floor(u * 2.6) + np.floor(v * 2.6)) % 2)
        grain = np.where(cell > 0, np.sin(u * 2.6 * np.pi * 3) ** 2, np.sin(v * 2.6 * np.pi * 3) ** 2)
        c = c * (0.82 + grain * 0.3)[..., None]
        rim = np.clip(f.edge / 0.12, 0, 1)
        return c * (0.7 + 0.3 * rim)[..., None] + 0.015
    return fn


def strap_leather(base, stitch='#c9b58c', holes=False):
    """Belt leather: a stitch line just inside each long edge, darker cut edges and, on the tail, a row of holes."""
    def fn(f):
        c = P.leather(f, base)
        rim = np.clip(f.edge / 0.1, 0, 1)
        c = c * (0.72 + 0.28 * rim)[..., None]
        if abs(f.n[1]) < 0.5:
            along = f.uv[..., 0]
            st = (np.abs(f.edge - 0.12) < 0.035) & ((np.floor(along * 5) % 2) == 0)
            c = np.where(st[..., None], P.col(stitch) * 0.8, c)
            if holes:
                mid = np.abs(f.uv[..., 1] - (f.uv[..., 1].max() + f.uv[..., 1].min()) / 2) < 0.12
                hole = mid & (np.abs((along % 0.75) - 0.37) < 0.11)
                c = np.where(hole[..., None], c * 0.25, c)
        return c
    return fn


def snap(f):
    """A brass press stud on a pouch flap."""
    return P.brass(f) * 1.05


def pouch_leather(base='#151515', gloss=0.18):
    """Moulded black leather pouches, polished on the corners where they rub."""
    def fn(f):
        c = P.leather(f, base)
        rub = np.clip(1 - f.edge / 0.25, 0, 1) * gloss
        c = c + rub[..., None] * 0.35
        if abs(f.n[1]) < 0.5:
            st = np.abs(f.edge - 0.1) < 0.03
            c = np.where(st[..., None], c * 0.6, c)
        return c
    return fn


def canvas_pouch(base):
    return B.fabric(base, weave=0.2, mottle=0.18, seam=0.15)


def inside(f):
    """The dark mouth of an open pouch."""
    return P.plain(f, '#0b0a09', 0.2)


# ================================================================== builders
# Built round the origin, the buckle at +z (the front), the strap's bottom at y = 0.


def ring(m, h, mat):
    x, z, t = HALF_X, HALF_Z, STRAP
    m.box([-x - t, 0, z], [x + t, h, z + t], mat)              # front
    m.box([-x - t, 0, -z - t], [x + t, h, -z], mat)            # back
    m.box([x, 0, -z], [x + t, h, z], mat)                      # right side
    m.box([-x - t, 0, -z], [-x, h, z], mat)                    # left side


def front(z=HALF_Z + STRAP):
    return z


def build_leather(m):
    h = 1.2
    ring(m, h, 'strap')
    z = front()
    m.box([-2.6, 0.05, z], [-0.75, h - 0.05, z + 0.18], 'tail')          # the tail end lies over the strap
    m.box([-0.85, -0.25, z], [0.85, h + 0.25, z + 0.12], 'buckle')       # buckle frame
    m.box([-0.55, 0.05, z + 0.12], [0.55, h - 0.05, z + 0.16], 'tail')   # strap showing through the frame
    m.box([-0.15, 0.45, z + 0.12], [0.6, 0.75, z + 0.28], 'buckle')      # prong
    for x in (-3.0, 2.2):                                                # keepers
        m.box([x, -0.08, z - 0.02], [x + 0.35, h + 0.08, z + 0.24], 'strap')


def build_tool(m):
    h = 1.4
    ring(m, h, 'strap')
    z = front()
    m.box([-0.9, -0.2, z], [0.9, h + 0.2, z + 0.16], 'buckle')
    m.box([-0.6, 0.1, z + 0.16], [0.6, h - 0.1, z + 0.2], 'strap')
    # two nail pouches hanging off the front corners, open at the top, a smaller pocket stitched on each
    for sgn in (-1, 1):
        x0, x1 = (1.4, 4.2) if sgn > 0 else (-4.2, -1.4)
        m.box([x0, -2.6, z + 0.05], [x1, h - 0.1, z + 1.3], 'pouch')
        m.box([x0 + 0.15, h - 0.15, z + 0.2], [x1 - 0.15, h - 0.1, z + 1.15], 'mouth')
        m.box([x0 + 0.3, -2.2, z + 1.3], [x1 - 0.3, -0.6, z + 1.65], 'pocket')
        m.box([x0 + 0.2, -0.7, z + 1.3], [x1 - 0.2, -0.55, z + 1.7], 'pocket')       # the pocket's welted top
        for rx in (x0 + 0.35, x1 - 0.55):                                           # copper rivets at its corners
            m.box([rx, -0.75, z + 1.65], [rx + 0.2, -0.55, z + 1.75], 'rivet')
    # the hammer loop on the right hip, and a tape measure clipped on the left
    x = HALF_X + STRAP
    m.box([x, -1.0, -0.4], [x + 0.2, h + 0.1, 0.4], 'buckle')
    m.box([x, -1.0, -0.6], [x + 0.25, -0.75, 0.6], 'buckle')
    m.box([-x - 1.3, -0.3, -0.7], [-x, 1.2, 0.7], 'tape')
    m.box([-x - 0.08, 0.2, -0.3], [-x, h + 0.15, 0.3], 'buckle')


def build_duty(m):
    h = 1.6
    ring(m, h, 'strap')
    z = front()
    m.box([-0.95, -0.1, z], [0.95, h + 0.1, z + 0.22], 'clip')           # side-release duty buckle
    m.box([-0.2, 0.3, z + 0.22], [0.2, h - 0.3, z + 0.3], 'clip')
    for x in (-3.2, 3.0):                                                # belt keepers with their snaps
        m.box([x, -0.12, z - 0.02], [x + 0.55, h + 0.12, z + 0.2], 'keeper')
        m.box([x + 0.17, h / 2 - 0.12, z + 0.2], [x + 0.38, h / 2 + 0.12, z + 0.26], 'snap')
    # double magazine pouch left of the buckle
    m.box([-3.0, -0.3, z + 0.05], [-1.3, h + 0.4, z + 0.85], 'pouch')
    for x in (-3.0, -2.15):
        m.box([x + 0.05, h + 0.1, z + 0.05], [x + 0.8, h + 0.55, z + 0.9], 'pouch')
        m.box([x + 0.32, h - 0.15, z + 0.85], [x + 0.53, h + 0.15, z + 0.95], 'snap')
    # handcuff case at the back, its flap closed with a brass stud, and the key ring hanging under it
    zb = -HALF_Z - STRAP
    m.box([-0.2, -0.5, zb - 1.0], [1.5, h + 0.3, zb], 'pouch')
    m.box([-0.25, h - 0.3, zb - 1.05], [1.55, h + 0.45, zb + 0.02], 'pouch')
    m.box([0.55, h - 0.25, zb - 1.12], [0.8, h, zb - 1.04], 'snap')
    for a in range(8):                                                   # key ring
        t = a / 8 * 2 * np.pi
        cx, cy = -0.9 + np.cos(t) * 0.55, -0.6 + np.sin(t) * 0.55
        m.box([cx - 0.09, cy - 0.09, zb - 0.25], [cx + 0.09, cy + 0.09, zb - 0.07], 'ring')
    m.box([-0.98, -0.1, zb - 0.22], [-0.82, h - 0.2, zb - 0.06], 'ring')
    # multitool pouch, worn brown, beside the cuffs
    m.box([1.9, -0.6, zb - 0.7], [2.7, h + 0.2, zb], 'tan')
    m.box([2.2, h - 0.35, zb - 0.76], [2.4, h - 0.15, zb - 0.7], 'snap')
    # glove pouch and radio holder on the left hip, the radio's aerial poking up
    x = -HALF_X - STRAP
    m.box([x - 0.85, -0.4, 0.4], [x, h + 0.3, 1.8], 'pouch')
    m.box([x - 0.95, -1.8, -1.4], [x, h + 0.2, 0.1], 'radio_holder')
    m.box([x - 0.85, -1.6, -1.25], [x - 0.05, h + 0.6, -0.05], 'radio')
    m.box([x - 0.55, h + 0.6, -0.85], [x - 0.3, h + 2.2, -0.6], 'radio')
    m.box([x - 0.9, 0.2, -1.0], [x - 0.84, 0.8, -0.6], 'snap')
    # flashlight ring behind the right hip (the holster rides in front of it, on its own)
    x = HALF_X + STRAP
    m.box([x, h - 0.4, -1.6], [x + 0.6, h, -0.9], 'clip')
    m.rod(-1.5, h + 0.3, 0.3, 'torch', cx=x + 0.3, cz=-1.25)


BELTS = [
    ('leather_belt', build_leather, 0.45, 0.08, dict(
        strap=strap_leather('#4b2c19'), tail=strap_leather('#4b2c19', holes=True), buckle=lambda f: P.brass(f))),
    ('tool_belt', build_tool, 0.65, 0.12, dict(
        strap=strap_leather('#7a4a24'), buckle=lambda f: P.steel(f, 0.5), pouch=strap_leather('#8a5a2e', stitch='#e0cfa0'),
        pocket=strap_leather('#6e4420', stitch='#e0cfa0'), mouth=inside, rivet=lambda f: P.brass(f),
        tape=lambda f: P.plain(f, '#d8b21e', 0.15))),
    ('duty_belt', build_duty, 0.5, 0.1, dict(
        strap=basketweave(), clip=lambda f: P.polymer(f, '#121212'), keeper=basketweave('#181818'), snap=snap,
        pouch=pouch_leather(), tan=strap_leather('#4a2a16', stitch='#2a1a0e'), ring=lambda f: P.steel(f, 0.45),
        radio_holder=pouch_leather('#101010', 0.1), radio=lambda f: P.polymer(f, '#1d1e20'),
        torch=lambda f: P.anodised(f, '#1a1b1d'))),
]


def display(m):
    disp = B.display(m)
    # seen from a little above, so the loop reads as a belt
    gui_rot = (38, -30, 0)
    s, t = M.fit(m, M.R_xyz(*gui_rot), 15.0)
    disp['gui'] = M.transform(gui_rot, t, s)
    return disp


def main():
    for name, build, wear, blood, mats in BELTS:
        m = M.Model(name)
        build(m)
        # the waist centre sits at the middle of the 16-unit frame, so worn it lines up with the body by translation alone
        m.shift(B.C - np.array([0, 0.8, 0]))
        m.tidy()
        seed = zlib.crc32(name.encode()) % 1000
        patches = M.paint_model(m, B.worn_look(wear, blood, **mats), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/gear/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        M.dump(M.model_json(m, uvs, f'{NS}:item/gear/{name}', display(m), gui_light='side'),
               ASSETS / f'models/item/gear/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gear/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
