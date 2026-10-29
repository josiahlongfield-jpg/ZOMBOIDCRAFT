#!/usr/bin/env python3
"""Packs the Warthog (the four-wheeled scout with a three-barrel minigun on the back) into the mod.

    python3 tools/cars/warthog.py BAKE_DIR [--preview OUT_DIR]

BAKE_DIR is what tools/cars/warthog_bake.py wrote from the source model (low.npz, atlas.png). The source
is a game rip Josiah gave the project for his own copy of the mod: it never goes in this repository, only
the packed, scrambled warthog.zcar does.

The vehicle file is the cars' format (see pack.py) at version 4, which adds after the wheels:
    int turret parts, then per part: byte kind (0 turns about the upright axis, 1 tilts, 2 spins),
        float pivot x,y,z (m), float axis x,y,z, mesh
Each part also carries the ones before it: the gun tilts on the post as the post turns, and the barrels
spin in the gun. The model's own mesh is the hull, the four wheels the cars' wheels.

Looks: usable (the bake as it is), abandoned (dust, streaks, droppings), wrecked (scorched, holed, glass
half gone) and burnt (charred, keeping the bake's shading so the detail still reads, no glass).
"""
import argparse
import io
import json
import os
import struct
import sys
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pack import CELL, VARIANTS, heights, java_hash, scramble  # noqa: E402
from aircraft import texels, weather  # noqa: E402
from wreck import fbm, sstep  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
ID = 'warthog'

# Blender's world space to the cars' model space: the source faces (1, -1, 0) with its left toward (1, 1, 0),
# and its tyres sit 0.157 m below the ground
FWD = np.array([1, -1, 0]) / np.sqrt(2)
LEFT = np.array([1, 1, 0]) / np.sqrt(2)
LIFT = 0.157

# measured on the full model (tyre treads, the post's mounting ring, its top, the barrel cluster)
WHEELS = [((0.970, 0.507, 1.689), True), ((-0.962, 0.507, 1.689), True),
          ((0.970, 0.507, -1.538), False), ((-0.962, 0.507, -1.538), False)]
WHEEL_R = 0.507
YAW = ((0.0, 1.16, -1.57), (0, 1, 0))
PITCH = ((0.0, 2.04, -0.90), (1, 0, 0))
SPIN = ((0.0, 2.29, -0.071), (0, 0.0763, 0.9971))
# the barrels' ends, 0.30 m on along the spin axis
MUZZLE = tuple(np.array(SPIN[0]) + 0.30 * np.array(SPIN[1]))

# eyes: the driver (seated against the wheel by CarModels), the passenger beside them, the gunner standing
# in the well behind the gun
EYES = [(0.43, 1.62, -0.15), (-0.43, 1.62, -0.15), (0.0, 2.62, -2.08)]
# the steering wheel's rim: centre, the normal toward the driver, radius
STEERING = (0.426, 1.361, 0.389, 0.0, 0.223, -0.975, 0.168)

COCKPIT = ('_cp', 'mottled', 'digital_gauge', 'carbon_fiber3')
TYRE = 'bump_plastic5'


def to_game(P):
    return np.stack([P @ LEFT, P[..., 2] + LIFT, P @ FWD], -1)


def load(bake):
    z = np.load(os.path.join(bake, 'low.npz'))
    P = to_game(z['P'].astype(float))
    UV = z['UV'].astype(float).copy()
    UV[..., 1] = 1 - UV[..., 1]
    part = z['part'].astype(int)
    mat = z['mat'].astype(str)
    if 'facing' in z.files:
        # 1 seen from the front only, 2 from the back only, 3 both: turn the back-only ones, twin the both
        facing = z['facing'].astype(int)
        # nobody ever sees either side of these (inside the hull); the moving parts keep theirs
        keep = (facing > 0) | ((part >= 1) & (part <= 8))
        P, UV, part, mat, facing = P[keep], UV[keep], part[keep], mat[keep], facing[keep]
        flip = facing == 2
        P[flip] = P[flip][:, ::-1]
        UV[flip] = UV[flip][:, ::-1]
        twin = np.where(facing == 3)[0]
        P = np.concatenate([P, P[twin][:, ::-1]])
        UV = np.concatenate([UV, UV[twin][:, ::-1]])
        part = np.concatenate([part, part[twin]])
        mat = np.concatenate([mat, mat[twin]])
    return P, UV, part, mat


def classes(part, mat):
    cls = np.full(len(part), 'body', object)
    cls[np.array([any(k in m for k in COCKPIT) for m in mat])] = 'interior'
    cls[part == 9] = 'glass'
    return cls


def wheel_copies(P, UV, part, mat):
    """The baked front left wheel (part 4) set at each wheel, mirrored for the right side. Part 4 + k."""
    sel = part == 4
    W, U, M = P[sel], UV[sel], mat[sel]
    c0 = np.array(WHEELS[0][0])
    outs = []
    for k, (c, _) in enumerate(WHEELS):
        c = np.array(c)
        q = W - c0
        u = U
        if np.sign(c[0]) != np.sign(c0[0]):
            q = q * np.array([-1, 1, 1])
            q, u = q[:, ::-1], u[:, ::-1]
        outs.append((q + c, u, np.full(len(q), 4 + k), M))
    keep = ~sel
    P = np.concatenate([P[keep]] + [o[0] for o in outs])
    UV = np.concatenate([UV[keep]] + [o[1] for o in outs])
    part = np.concatenate([part[keep]] + [o[2] for o in outs])
    mat = np.concatenate([mat[keep]] + [o[3] for o in outs])
    return P, UV, part, mat


def tone(img):
    """The bake under a white sky is a touch dark in the cab and wells: lift the shadows a little."""
    a = img / 255.0
    return 255 * a ** 0.86


def paint(base, s, state, rng):
    p, n, cls = s['p'], s['n'], s['cls']
    col = base[s['ok']].copy()
    if state == 'burnt':
        lum = col @ np.array([0.3, 0.59, 0.11])
        shade = np.clip(lum / max(np.median(lum), 1), 0.35, 1.8)
        col = weather(p, n, col, cls, state, rng, ()) * shade[:, None] ** 0.7
        # what's left of the tyres is charred rubber, not rusted
        tyre = s['tyre']
        col[tyre] = np.array([30, 28, 27]) * (0.8 + 0.4 * fbm(p[tyre] * 6, 71, 2))[:, None] * shade[tyre, None] ** 0.7
    elif state != 'usable':
        col = weather(p, n, col, cls, state, rng, ())
        # mud thrown up the sills and wheel arches
        mud = sstep(0.9, 0.25, p[:, 1]) * sstep(0.35, 0.7, fbm(p * np.array([3, 6, 3]), 61, 3))
        amt = {'abandoned': 0.55, 'wrecked': 0.45}[state]
        col = col * (1 - (mud * amt)[:, None]) + np.array([92, 80, 62]) * (mud * amt)[:, None]
    glass = cls == 'glass'
    col[glass] = np.array([92, 104, 108]) * (1 + (rng.random((glass.sum(), 1)) - 0.5) * 0.05) * \
        (0.8 if state == 'abandoned' else 1.0)
    img = base.copy()
    img[s['ok']] = col
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def state_keep(cls, state, rng):
    keep = np.ones(len(cls), bool)
    glass = cls == 'glass'
    if state == 'wrecked':
        keep &= ~(glass & (rng.random(len(cls)) < 0.5))
    if state == 'burnt':
        keep &= ~glass
    return keep


def write_mesh(buf, T, UV, glass, centre):
    order = np.concatenate([np.where(~glass)[0], np.where(glass)[0]])
    Q = np.round((T[order] - centre) * 1000).astype('>i2').reshape(-1, 3)
    U = np.round(np.clip(UV[order], 0, 1) * 65535).astype('>u2').reshape(-1, 2)
    buf.write(struct.pack('>ii', int((~glass).sum()), int(glass.sum())))
    buf.write(np.concatenate([Q.view('>u2'), U], 1).astype('>u2').tobytes())


def build(bake, preview=None):
    P, UV, part, mat = load(bake)
    atlas = np.asarray(Image.open(os.path.join(bake, 'atlas.png')).convert('RGB'), float)
    H, W = atlas.shape[:2]
    # texels: only the unique (baked) triangles, before the wheel copies
    print(f'{ID}: {len(P)} triangles, atlas {W}x{H}', flush=True)
    cls0 = classes(part, mat)
    tri, bary = texels(P, UV * np.array([W, H]), W, H)
    ok = tri >= 0
    t, w = tri[ok], bary[ok]
    pts = (P[t] * w[:, :, None]).sum(1)
    nrm = np.cross(P[t, 1] - P[t, 0], P[t, 2] - P[t, 0])
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-12)
    surf = dict(ok=ok, p=pts, n=nrm, cls=cls0[t].astype(str), tyre=np.char.find(mat[t].astype(str), TYRE) >= 0)
    base = tone(atlas)

    P, UV, part, mat = wheel_copies(P, UV, part, mat)
    cls = classes(part, mat)
    glass = cls == 'glass'
    hull = (part == 0) | (part == 9)
    lo, hi = P[hull].reshape(-1, 3).min(0), P[hull].reshape(-1, 3).max(0)
    allp = P.reshape(-1, 3)
    lo, hi = np.minimum(lo, allp.min(0)), np.maximum(hi, allp.max(0))
    centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])

    buf = io.BytesIO()
    buf.write(b'ZCAR')
    buf.write(struct.pack('>ii', 4, len(VARIANTS)))
    for state in VARIANTS:
        rng = np.random.default_rng(java_hash(ID + state) & 0xFFFFF)
        img = paint(base, surf, state, rng)
        keep = state_keep(cls, state, rng)
        jpg = io.BytesIO()
        img.save(jpg, 'JPEG', quality=88, subsampling=0)
        jpg = jpg.getvalue()
        nb = state.encode()
        buf.write(struct.pack('>H', len(nb)))
        buf.write(nb)
        buf.write(struct.pack('>i', len(jpg)))
        buf.write(jpg)
        m = keep & hull
        write_mesh(buf, P[m], UV[m], glass[m], centre)
        buf.write(struct.pack('>i', 0))  # lids
        buf.write(struct.pack('>i', 4))
        for k, (c, steers) in enumerate(WHEELS):
            buf.write(struct.pack('>4fb', *(np.asarray(c) - centre), WHEEL_R, 1 if steers else 0))
            m = keep & (part == 4 + k)
            write_mesh(buf, P[m], UV[m], glass[m], centre)
        buf.write(struct.pack('>i', 3))
        for kind, (pivot, axis) in enumerate((YAW, PITCH, SPIN)):
            buf.write(struct.pack('>b6f', kind, *(np.asarray(pivot) - centre), *axis))
            m = keep & (part == 1 + kind)
            write_mesh(buf, P[m], UV[m], glass[m], centre)
        if preview:
            os.makedirs(preview, exist_ok=True)
            img.save(os.path.join(preview, f'{ID}_{state}.jpg'), quality=90)
        print(f'  {state}: {keep.sum()} triangles, texture {len(jpg) // 1024} KB', flush=True)
    blob = scramble(zlib.compress(buf.getvalue(), 9), ID)
    with open(os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars', ID + '.zcar'), 'wb') as f:
        f.write(blob)
    # collision: the hull and wheels at rest, the gun left out (it swings, and you stand up behind it)
    rest = P[(part != 1) & (part != 2) & (part != 3)] - centre
    glo, nx, nz, grid = heights(rest)
    size = hi - lo

    def rel(p):
        return [round(float(a - b), 3) for a, b in zip(p, centre)]

    print(f'{ID:20s} {len(blob) // 1024:5d} KB  {size[0]:.1f} x {size[1]:.1f} x {size[2]:.1f} m', flush=True)
    row = {'id': ID, 'kind': 'military', 'weight': 1, 'size': [round(float(s), 3) for s in size],
           'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
           'heights': [int(round(h * 100)) for h in grid.ravel()],
           'seats': [[round(float(a - b), 3) for a, b in zip(e, centre)] for e in EYES],
           'wheelbase': round(WHEELS[0][0][2] - WHEELS[2][0][2], 3),
           'steering': [round(float(v - (centre[i] if i < 3 else 0)), 3) for i, v in enumerate(STEERING)],
           'gunner': 2,
           'turret': {'yaw': rel(YAW[0]), 'pitch': rel(PITCH[0]), 'muzzle': rel(MUZZLE),
                      'rest': round(float(np.degrees(np.arctan2(SPIN[1][1], SPIN[1][2]))), 2), 'up': 50, 'down': 22}}
    idx = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars/models.json')
    with open(idx) as f:
        data = json.load(f)
    data['models'] = sorted([m for m in data['models'] if m['id'] != ID] + [row], key=lambda m: m['id'])
    with open(idx, 'w') as f:
        json.dump(data, f, separators=(',', ':'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('bake')
    ap.add_argument('--preview')
    a = ap.parse_args()
    build(a.bake, a.preview)


if __name__ == '__main__':
    main()
