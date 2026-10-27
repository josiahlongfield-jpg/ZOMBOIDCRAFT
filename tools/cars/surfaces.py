#!/usr/bin/env python3
"""The flyable jets' moving control surfaces: which triangles of the shipped airframe (the .zcar's
usable body) make up each surface, and the hinge each one turns about. The game (AirSurfaces.java)
swings them with the stick, the rudder, the speed and the angle of attack, the way each real jet's
flight control system does:

    jet_corvid (the F-22)       all-moving stabilators, flaperons and ailerons, twin rudders,
                                leading-edge flaps, and the upper and lower flaps of the two
                                thrust-vectoring nozzles. Each is its own object in the bought model.
    jet_gyrfalcon (the Su-27)   all-moving stabilators, flaperons, twin rudders. The bought model
                                has no separate leading-edge flaps or airbrake to move. Its surfaces
                                are found as loose pieces of the decimated airframe inside a box
                                round each (left side; the right side mirrors it).

    python3 tools/cars/surfaces.py [SRC]   (SRC: the unpacked jets.zip, needed for the F-22's names)

Writes cars/<id>.surfaces.json: the body's solid triangle count it was made for, and per surface its kind, side (+1 left, -1 right), pivot and unit hinge
axis in model space (a positive turn about it moves the surface's trailing edge down, or a rudder's
to the left), and its triangles as runs (start, count) into the body mesh's solid triangles. Only
indices and hinge lines: nothing of the bought model itself, so it isn't scrambled.
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import zcar  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars')

# kinds: stab (all-moving tail, pivots spanwise across the body), te (trailing-edge surface: hinge at its
# front), le (leading-edge flap: hinge at its back), rudder (hinge at its front, up the fin), nozzle (a
# nozzle flap: hinge at its front, across the body)
CORVID = [
    ('stab', ('Elevator_L', 'Elevator_R')),
    ('flaperon', ('Flap_L', 'Flap_R')),
    ('aileron', ('Aileron_L', 'Aileron_R')),
    ('rudder', ('Rudder_L', 'Rudder_R')),
    ('lef', ('Slat_L', 'Slat_R')),
    ('nozzle_lower', ('Engine01_L', 'Engine01_R')),
    ('nozzle_upper', ('Engine02_L', 'Engine02_R')),
]
HINGE = {'stab': 'stab', 'flaperon': 'te', 'aileron': 'te', 'rudder': 'rudder', 'lef': 'le', 'nozzle_lower': 'nozzle',
         'nozzle_upper': 'nozzle'}

# the Su-27's: left-side boxes (x0, y0, z0, x1, y1, z1) holding the loose piece, smallest pieces dropped
GYRFALCON = [
    ('stab', (1.75, 1.8, -9.25, 4.8, 2.5, -5.6), 40),
    ('flaperon', (2.05, 2.4, -5.8, 5.25, 2.7, -3.55), 40),
    ('rudder', (1.98, 2.4, -7.5, 2.2, 4.8, -6.25), 30),
]


def body(model):
    """The usable state's solid triangles, in the order the game reads them."""
    s = zcar.read(model)['usable']
    return s['T'][~s['glass']]


def pieces(T):
    """Each triangle's loose piece: triangles sharing a corner (to the millimetre) are one piece."""
    V = np.round(T * 1000).astype(np.int64).reshape(-1, 3)
    _, inv = np.unique(V, axis=0, return_inverse=True)
    inv = inv.reshape(-1, 3)
    parent = np.arange(inv.max() + 1)

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for a, b, c in inv:
        ra = find(a)
        parent[find(b)] = ra
        parent[find(c)] = ra
    return np.array([find(i) for i in inv[:, 0]])


def hinge(T, kind):
    """Pivot and unit axis for a surface's triangles (positive turn: trailing edge down, rudder left)."""
    Q = T.reshape(-1, 3)
    side = 1.0 if Q[:, 0].mean() > 0 else -1.0
    span = np.abs(Q[:, 0])
    if kind == 'rudder':
        lo, hi = Q[Q[:, 1] < Q[:, 1].min() + 0.06], Q[Q[:, 1] > Q[:, 1].max() - 0.06]
        a, b = lo[lo[:, 2].argmax()], hi[hi[:, 2].argmax()]
        pivot, axis = a, b - a
    elif kind in ('stab', 'nozzle'):
        root = Q[span < span.min() + 0.06]
        if kind == 'stab':
            # the spigot sits about 40 % back along the root chord
            z = root[:, 2].max() - 0.4 * (root[:, 2].max() - root[:, 2].min())
        else:
            z = root[:, 2].max()
        near = root[np.abs(root[:, 2] - z) < 0.15]
        pivot = np.array([root[:, 0].mean(), (near if len(near) else root)[:, 1].mean(), z])
        axis = np.array([1.0, 0, 0])
    else:
        inner, outer = Q[span < span.min() + 0.06], Q[span > span.max() - 0.06]
        pick = (lambda E: E[E[:, 2].argmax()]) if kind == 'te' else (lambda E: E[E[:, 2].argmin()])
        a, b = pick(inner), pick(outer)
        # the hinge at mid-thickness: halfway up the surface's end
        for e, E in ((a, inner), (b, outer)):
            e[1] = (E[:, 1].min() + E[:, 1].max()) / 2
        pivot, axis = a, b - a
    axis = axis / np.linalg.norm(axis)
    # the edge that should swing down (or left): the trailing edge, or a leading-edge flap's leading edge
    edge = Q[Q[:, 2].argmax()] if kind == 'le' else Q[Q[:, 2].argmin()]
    move = np.cross(axis, edge - pivot)
    if (kind == 'rudder' and move[0] < 0) or (kind != 'rudder' and move[1] > 0):
        axis = -axis
    return side, pivot, axis


def runs(idx):
    idx = np.sort(idx)
    out, start, prev = [], idx[0], idx[0]
    for i in idx[1:]:
        if i != prev + 1:
            out += [int(start), int(prev - start + 1)]
            start = i
        prev = i
    return out + [int(start), int(prev - start + 1)]


def part(name, kind, T, idx):
    side, pivot, axis = hinge(T[idx], HINGE[kind])
    print(f'  {name:9s} side {side:+.0f}: {len(idx):4d} triangles, pivot {np.round(pivot, 3)}, axis {np.round(axis, 3)}')
    return dict(kind=kind, side=int(side), pivot=[round(float(v), 4) for v in pivot],
                axis=[round(float(v), 5) for v in axis], runs=runs(idx))


def corvid(src):
    """The F-22's surfaces, by the bought model's own object names (matched triangle for triangle)."""
    import aircraft as A
    spec = A.SPECS['corvid']
    m = A.load_obj(os.path.join(src, spec['obj']))
    P = m['P'] * spec.get('scale', 1.0)
    on = np.array(m['onames'] + [''], object)[m['obj']].astype(str)
    lo, hi = P.reshape(-1, 3).min(0), P.reshape(-1, 3).max(0)
    P = P - np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])
    T = body('jet_corvid')
    key = lambda X: map(tuple, np.round(X * 1000).astype(np.int64).sum(1))
    where = {k: i for i, k in enumerate(key(T))}
    parts = []
    for kind, names in CORVID:
        for n in names:
            found = [where.get(k, -1) for k in key(P[on == n])]
            idx = np.array(sorted(set(found) - {-1}))
            assert len(idx) >= 0.95 * len(found), f'{n}: only {len(idx)} of {len(found)} triangles found'
            parts.append(part(n, kind, T, idx))
    return parts


def gyrfalcon():
    """The Su-27's surfaces: the loose pieces of the airframe inside each box, left and right."""
    T = body('jet_gyrfalcon')
    root = pieces(T)
    parts = []
    for kind, box, least in GYRFALCON:
        for side in (1, -1):
            lo, hi = np.array(box[:3]), np.array(box[3:])
            if side < 0:
                lo, hi = np.array([-hi[0], lo[1], lo[2]]), np.array([-lo[0], hi[1], hi[2]])
            idx = []
            for r in np.unique(root):
                m = np.where(root == r)[0]
                X = T[m].reshape(-1, 3)
                if len(m) >= least and (X.min(0) >= lo).all() and (X.max(0) <= hi).all():
                    idx += list(m)
            assert idx, f'no {kind} found on side {side}'
            parts.append(part(f'{kind}_{"l" if side > 0 else "r"}', kind, T, np.array(idx)))
    return parts


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else None
    out = {}
    if src:
        print('jet_corvid')
        out['jet_corvid'] = corvid(src)
    print('jet_gyrfalcon')
    out['jet_gyrfalcon'] = gyrfalcon()
    for model, parts in out.items():
        with open(os.path.join(OUT, model + '.surfaces.json'), 'w') as f:
            json.dump({'triangles': int(len(body(model))), 'parts': parts}, f, separators=(',', ':'))


if __name__ == '__main__':
    main()
