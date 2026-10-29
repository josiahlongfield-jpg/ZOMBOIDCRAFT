"""Holster and sheath item models for ZomboidCraft's quick slots, built at body scale on the same frame as the belts
(tools/items/gen_belts.py: one unit is one skin pixel, the belt's bottom edge at y = 0, the front at +z), so worn they
sit on the hip by the same translation. The handgun or blade in them is drawn separately (WornGearLayer.hip), its grip
standing out of the top.

References: refs/items/belts/01-community-affairs-duty-belt (the black polymer duty holster on the right hip: a
moulded shell over the slide, a hood over the rear sight, a drop off the belt); refs/items/holsters/07 (the brown
leather shoulder rig: a pouch with a snap strap over the grip, hung from a harness round both shoulders);
holsters/10-11 (western drop holsters tied down on the thigh); sheaths/01-03 (a stitched leather knife sheath with a
snap strap and a belt loop); sheaths/04, 06 (machete sheaths: black nylon, riveted edges, a strap round the handle).

The shoulder rig hangs at the left armpit (+x), the knife and machete on the left hip, and the thigh rig is drawn on
the right leg (WornGearLayer), in the same frame moved down to the thigh.

    python3 tools/items/gen_holsters.py
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
import gen_belts as G  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
SIDE = -1          # the gun side: holsters ride on -x, which is the survivor's right hip


def moulded(base='#1a1a1b'):
    """Moulded polymer shell: a soft sheen on the faces, edges rubbed lighter, a parting line along the middle."""
    def fn(f):
        c = P.polymer(f, base, 0.07)
        rub = np.clip(1 - f.edge / 0.18, 0, 1)
        c = c + rub[..., None] * 0.05
        if abs(f.n[0]) > 0.5:
            mid = np.abs(f.uv[..., 1] - (f.uv[..., 1].max() + f.uv[..., 1].min()) / 2) < 0.05
            c = np.where(mid[..., None], c * 0.75, c)
        return c
    return fn


def build_hip(m):
    x0 = G.HALF_X + G.STRAP            # outside the belt
    s = SIDE
    def X(a, b):
        return sorted((s * (x0 + a), s * (x0 + b)))
    # belt drop: the plate the shell hangs from, looped over the belt
    xa, xb = X(0.0, 0.25)
    m.box([xa, -1.2, -0.9], [xb, 1.9, 0.9], 'plate')
    xa, xb = X(-0.45, 0.0)
    m.box([xa, 1.6, -0.9], [xb, 1.9, 0.9], 'plate')                     # over the top of the belt
    # the shell over the slide, muzzle down, a little forward of the hip bone
    xa, xb = X(0.25, 1.35)
    m.box([xa, -3.6, -0.55], [xb, 0.9, 0.85], 'shell')
    xa, xb = X(0.35, 1.25)
    m.box([xa, -3.9, -0.45], [xb, -3.6, 0.75], 'shell')                 # rounded toe
    m.box([xa, -1.1, 0.85], [xb, 0.6, 1.35], 'shell')                    # trigger-guard bulge
    # the hood over the rear of the slide, swung down to lock the gun in, and its pivot
    xa, xb = X(0.2, 1.45)
    m.box([xa, 0.7, -0.75], [xb, 1.5, 0.15], 'hood')
    xa, xb = X(1.35, 1.5)
    m.box([xa, 0.2, -0.95], [xb, 0.55, -0.6], 'pivot')
    # release lever on the inner side, under the thumb
    xa, xb = X(0.1, 0.25)
    m.box([xa, 0.1, 0.0], [xb, 0.55, 0.55], 'pivot')


def leather_rig(base, stitch='#c9b58c'):
    """Holster leather: oiled, darker at the cut edges, a pale saddle stitch round the faces."""
    def fn(f):
        c = P.leather(f, base)
        rim = np.clip(f.edge / 0.12, 0, 1)
        c = c * (0.7 + 0.3 * rim)[..., None]
        if abs(f.n[1]) < 0.5:
            st = (np.abs(f.edge - 0.1) < 0.03) & ((np.floor((f.uv[..., 0] + f.uv[..., 1]) * 5) % 2) == 0)
            c = np.where(st[..., None], P.col(stitch) * 0.75, c)
        return c
    return fn


def riveted_nylon(base='#18191a'):
    """Black nylon webbing with a row of steel rivets down the seam edge."""
    weave = B.fabric(base, weave=0.25, mottle=0.12, seam=0.2)
    def fn(f):
        c = weave(f)
        if abs(f.n[0]) > 0.5:
            v = f.uv[..., 1]
            riv = (np.abs(f.edge - 0.12) < 0.06) & (np.abs((v % 0.9) - 0.45) < 0.07)
            c = np.where(riv[..., None], P.col('#8a8d90'), c)
        return c
    return fn


def build_shoulder(m):
    # the pouch sits on the left side of the chest under the arm, muzzle down, grip forward and up
    z0 = G.HALF_Z
    m.box([2.2, 2.6, z0], [4.0, 7.6, z0 + 1.1], 'pouch')
    m.box([2.4, 2.3, z0 + 0.1], [3.8, 2.6, z0 + 1.0], 'pouch')                # stitched toe
    m.box([2.0, 7.0, z0 + 1.1], [4.2, 7.4, z0 + 1.25], 'strap')                # retaining strap over the grip
    m.box([2.9, 7.05, z0 + 1.25], [3.3, 7.35, z0 + 1.32], 'snap')
    # harness: up over both shoulders, crossing on the back
    t = 0.18
    for x in (2.6, -3.4):
        m.box([x, 7.6 if x > 0 else 3.6, z0], [x + 0.8, 10.9, z0 + t], 'strap')     # front, up to the shoulder
        m.box([x, 10.9, -z0 - t], [x + 0.8, 10.9 + t, z0 + t], 'strap')            # over the top
    m.box([-3.4, 3.4, z0], [-2.6, 3.8, z0 + 0.3], 'snap')                        # the off-side tie-down
    for i in range(14):                                                           # the X across the back
        a = (i + 0.5) / 14
        y = 10.6 - a * 6.2
        for sgn in (-1, 1):
            x = sgn * (3.0 - a * 6.0)
            m.box([x - 0.4, y - 0.4, -z0 - t], [x + 0.4, y + 0.4, -z0], 'strap')
    m.box([-0.5, 7.0, -z0 - t - 0.08], [0.5, 8.0, -z0 - t + 0.02], 'ring')      # centre ring


def build_thigh(m):
    # hung off the belt on the right hip by a leather drop, the pouch tied round the thigh below it
    s = SIDE
    x0 = G.HALF_X
    def X(a, b):
        return sorted((s * (x0 + a), s * (x0 + b)))
    xa, xb = X(0.0, 0.35)
    m.box([xa, -3.0, -0.6], [xb, 1.6, 0.6], 'drop')
    m.box([*X(-0.35, 0.0)[:1], 1.3, -0.6], [X(-0.35, 0.0)[1], 1.6, 0.6], 'drop')  # over the belt
    xa, xb = X(0.35, 1.45)
    m.box([xa, -8.6, -0.6], [xb, -3.4, 0.8], 'pouch')
    m.box([*X(0.3, 1.5)[:1], -3.6, -0.8], [X(0.3, 1.5)[1], -2.8, 1.0], 'pouch')    # the mouth, flared
    m.box([*X(0.45, 1.35)[:1], -8.9, -0.5], [X(0.45, 1.35)[1], -8.6, 0.7], 'pouch')
    m.box([*X(0.25, 1.55)[:1], -3.2, -0.2], [X(0.25, 1.55)[1], -2.9, 0.2], 'snap')  # hammer thong
    # leg straps round the thigh (the leg is 4 wide, 4 deep, from x 0 to the hip)
    for y in (-5.0, -7.6):
        a, b, t = -x0 - 0.12, 0.12, 0.12
        m.box([a, y, 2.0], [b, y + 0.5, 2.0 + t], 'strap')
        m.box([a, y, -2.0 - t], [b, y + 0.5, -2.0], 'strap')
        m.box([a, y, -2.0], [a + t, y + 0.5, 2.0], 'strap')
        m.box([b - t, y, -2.0], [b, y + 0.5, 2.0], 'strap')
        m.box([*X(0.25, 0.4)[:1], y - 0.1, -0.3], [X(0.25, 0.4)[1], y + 0.6, 0.3], 'buckle')


def build_sheath(m, length, width, top):
    # on the left hip: a loop over the belt, the sheath hanging off it, a strap round the handle
    x0 = G.HALF_X + G.STRAP
    m.box([x0, -0.5, -0.5], [x0 + 0.2, 1.9, 0.5], 'loop')
    m.box([x0 - 0.5, 1.6, -0.5], [x0, 1.9, 0.5], 'loop')
    w = width / 2
    m.box([x0 + 0.2, top - length, -w], [x0 + 1.0, top, w], 'body')
    m.box([x0 + 0.3, top - length - 0.4, -w * 0.6], [x0 + 0.9, top - length, w * 0.5], 'body')   # the point
    m.box([x0 + 0.15, top - 0.35, -w - 0.08], [x0 + 1.05, top, w + 0.08], 'welt')                 # the throat
    m.box([x0 + 0.2, top, -w - 0.1], [x0 + 0.35, top + 1.3, -w + 0.15], 'strap')                 # retaining strap
    m.box([x0 + 0.2, top + 1.15, -w - 0.1], [x0 + 1.1, top + 1.4, w + 0.1], 'strap')
    m.box([x0 + 1.1, top + 1.18, -0.15], [x0 + 1.18, top + 1.37, 0.15], 'snap')


def build_knife(m):
    build_sheath(m, 5.0, 1.3, 0.4)


def build_machete(m):
    build_sheath(m, 7.6, 1.9, 1.1)
    x0 = G.HALF_X + G.STRAP
    m.box([x0 + 0.3, -4.6, -0.95], [x0 + 1.1, -3.4, 0.95], 'welt')       # a sharpener pocket over the face


HOLSTERS = [
    ('hip_holster', build_hip, 0.4, 0.05, dict(
        shell=moulded('#262729'), hood=moulded('#1e1f21'), plate=moulded('#2a2b2d'), pivot=lambda f: P.steel(f, 0.4))),
    ('shoulder_holster', build_shoulder, 0.55, 0.06, dict(
        pouch=leather_rig('#5a3218'), strap=leather_rig('#4a2914'), snap=lambda f: P.brass(f),
        ring=lambda f: P.steel(f, 0.4))),
    ('thigh_holster', build_thigh, 0.6, 0.08, dict(
        pouch=leather_rig('#6b3f1e'), drop=leather_rig('#5c3519'), strap=leather_rig('#4e2c15'),
        snap=lambda f: P.brass(f), buckle=lambda f: P.steel(f, 0.45))),
    ('knife_sheath', build_knife, 0.55, 0.1, dict(
        body=leather_rig('#6a3c1c', '#d8c49a'), welt=leather_rig('#55301a'), loop=leather_rig('#55301a'),
        strap=leather_rig('#55301a'), snap=lambda f: P.steel(f, 0.4))),
    ('machete_sheath', build_machete, 0.6, 0.1, dict(
        body=riveted_nylon(), welt=riveted_nylon('#202122'), loop=riveted_nylon('#141515'),
        strap=riveted_nylon('#141515'), snap=lambda f: P.steel(f, 0.4))),
]


def main():
    for name, build, wear, blood, mats in HOLSTERS:
        m = M.Model(name)
        build(m)
        m.shift(B.C - np.array([0, 0.8, 0]))      # the belts' frame, so it hangs at the waist the same way
        m.tidy()
        seed = zlib.crc32(name.encode()) % 1000
        patches = M.paint_model(m, B.worn_look(wear, blood, **mats), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/gear/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        disp = B.display(m)
        gui_rot = (15, 60, 0)
        sc, tr = M.fit(m, M.R_xyz(*gui_rot), 15.0)
        disp['gui'] = M.transform(gui_rot, tr, sc)
        M.dump(M.model_json(m, uvs, f'{NS}:item/gear/{name}', disp, gui_light='side'), ASSETS / f'models/item/gear/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gear/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
