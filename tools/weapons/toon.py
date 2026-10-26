"""The character art style on guns and their parts (Josiah, 2026-10-26): chunky painted blocks where each part
reads as one piece, a thin dark outline only round the outside of each part, and edges chipped through the
finish more the more worn the gun is.

A part is a run of touching boxes of one material (plus the materials FAMILY folds into it). Where a box's face
carries straight on into another box of the same part, or into one standing up off it, that edge is a joint:
it gets no outline, no bevel and no edge wear, so the part flows. Everywhere else an edge is the outside of
the part and gets all three.
"""
import numpy as np

import model as M
import paint as P

INK = P.col('#0b0b0d')
WIDTH = 0.05            # the outline, in model units (a sixteenth of a block), never under one texel
REACH = 0.4             # how far in from an edge the edge effects can reach; joints are only looked for this close
DEPTH = 0.03            # how far past an edge (and under the face) to look for the next box of the part
STEP = 0.08             # steps down to the next box of the part up to this deep are joints too (a shaped stock's slices)
# materials painted as part of another one's surface (engraving on a can, a housing's markings)
FAMILY = {'mark': 'anod', 'can_mark': 'can', 'bezel_r': 'housing', 'gcheck': 'stock'}
# lit or see-through things: no outline, no chips
PLAIN = {'dot', 'flash', 'lens', 'lens_green', 'lamp', 'reticle', 'glass', 'bore'}
INK_MIN = 4            # texels a face needs across before it gets an outline
LIT, SHADE = 0.06, 0.15  # how much lighter a top is painted, and how much darker an underside
LIFT = 0.55             # near-black finishes painted up to charcoal (this much lighter at black, none from DARK up)
DARK = 0.3
CHIP_DEPTH = (0.02, 0.06)   # how far in from the outline chips reach, clean and at full wear
CHIPS = (0.05, 0.25)        # the share of that strip chipped, clean and at full wear
CHIP_MIN = 0.3              # faces narrower than this (rail teeth, chamfers) don't chip: they'd be all flecks


def family(mat):
    return FAMILY.get(mat, mat)


def world_corners(p):
    return np.array(M.corners(p))


def pieces(parts):
    """Union-find over touching boxes of one material family: returns a piece number per box."""
    n = len(parts)
    up = list(range(n))

    def find(i):
        while up[i] != i:
            up[i] = up[up[i]]
            i = up[i]
        return i

    boxes = []
    for p in parts:
        c = world_corners(p)
        boxes.append((c.min(0) - 0.02, c.max(0) + 0.02))
    for i in range(n):
        for j in range(i + 1, n):
            if family(parts[i]['mat']) != family(parts[j]['mat']):
                continue
            (a0, a1), (b0, b1) = boxes[i], boxes[j]
            if np.all(a0 <= b1) and np.all(b0 <= a1):
                up[find(i)] = find(j)
    return [find(i) for i in range(n)]


def _to_world(p, pts):
    if not p['rot']:
        return pts
    R = M.rot_matrix(p['rot']['axis'], p['rot']['angle'])
    o = np.asarray(p['rot']['origin'], float)
    return (pts - o) @ R.T + o


def _inside(q, pts, tol=0.004):
    if q['rot']:
        R = M.rot_matrix(q['rot']['axis'], q['rot']['angle'])
        o = np.asarray(q['rot']['origin'], float)
        pts = (pts - o) @ R + o
    return np.all((pts >= q['lo'] - tol) & (pts <= q['hi'] + tol), axis=-1)


def sides(p, name, px):
    """The face's four edges, laid out like model.face_grid: for each, every pixel's distance to it and the
    outward direction across it (in the box's own, unturned frame)."""
    ax, sgn, ua, ud, va, vd = M.FACES[name]
    lo, hi = p['lo'], p['hi']
    lu, lv = hi[ua] - lo[ua], hi[va] - lo[va]
    W, H = max(1, int(round(lu * px))), max(1, int(round(lv * px)))
    uu, vv = np.meshgrid((np.arange(W) + 0.5) / W, (np.arange(H) + 0.5) / H)
    eu, ev = np.eye(3)[ua], np.eye(3)[va]
    su = 1.0 if ud > 0 else -1.0
    sv = 1.0 if vd > 0 else -1.0
    return [(uu * lu, -su * eu), ((1 - uu) * lu, su * eu), (vv * lv, -sv * ev), ((1 - vv) * lv, sv * ev)]


def cover(p, name, pos, n, mates, px):
    """For each of the face's edges, which pixels' stretch of that edge is a joint (the part carries on past it).
    A round bar's long edges count as joints too: it reads round from its shading, not from lines down it.
    Returns four (H, W) masks in sides() order."""
    out = []
    near = []
    c = world_corners(p)
    lo, hi = c.min(0) - REACH, c.max(0) + REACH
    for q in mates:
        qc = world_corners(q)
        if np.all(qc.min(0) <= hi) and np.all(lo <= qc.max(0)):
            near.append(q)
    axis = p.get('axis') if p.get('round') else None
    for d, out_dir in sides(p, name, px):
        if axis is not None and abs(out_dir[axis]) < 0.5:
            out.append(np.ones(d.shape, bool))
            continue
        m = np.zeros(d.shape, bool)
        band = d < REACH
        if near and band.any():
            hit = np.zeros(int(band.sum()), bool)
            for under in (DEPTH, STEP):
                pts = _to_world(p, pos[band] + out_dir * (d[band][:, None] + DEPTH) - n * under)
                for q in near:
                    hit |= _inside(q, pts)
            m[band] = hit
        out.append(m)
    return out


def step_face(p, pos, n, mates):
    """A face mostly buried in the next box of its part, so only a sliver shows: the riser of a small step
    between two slices. It gets no outline and no chips, or every slice of a shaped stock would be lined."""
    c = world_corners(p)
    lo, hi = c.min(0) - DEPTH, c.max(0) + DEPTH
    pts = None
    hit = None
    for q in mates:
        qc = world_corners(q)
        if not (np.all(qc.min(0) <= hi) and np.all(lo <= qc.max(0))):
            continue
        if pts is None:
            pts = _to_world(p, (pos + n * DEPTH).reshape(-1, 3))
            hit = np.zeros(len(pts), bool)
        hit |= _inside(q, pts)
    return hit is not None and hit.mean() > 0.5


def outer_edge(p, name, covers, px):
    """Distance to the nearest edge that's the outside of the part (joints don't count)."""
    best = None
    for (d, _o), cov in zip(sides(p, name, px), covers):
        d = np.where(cov, 99.0, d)
        best = d if best is None else np.minimum(best, d)
    return best


def inked(lu, lv, px):
    """Faces too narrow for an outline of their own (pins, buttons, slivers): colour and shading carry them."""
    return min(lu, lv) * px >= INK_MIN


def style(f, rgb, mat, px, up, lu, lv):
    """The part's look on one face: a lit top and a shaded underside (up: the face normal's height in the gun's
    frame), black lifted to charcoal so the outline and chips read on it, chips worn through the finish along
    the part's outside edges (more on a worn gun, enough to break the line on a battered one), then the
    outline. f.edge is already the distance to the part's outside. Returns the colour and where the outline
    went (it gets no sheen)."""
    none = np.zeros(f.shape, bool)
    if mat in PLAIN:
        return rgb, none
    rgb = rgb * (1 + LIT * max(up, 0) - SHADE * max(-up, 0))
    rgb = rgb * (1 + LIFT * np.clip(1 - rgb.mean(-1) / DARK, 0, 1))[..., None]
    w = max(1.0 / px, min(WIDTH, 1.6 / px))
    wear = f.wear
    # chips: small, scattered flecks just inside the outline, in clusters, never a full line
    band = (f.edge >= w * 0.5) & (f.edge < w + CHIP_DEPTH[0] + CHIP_DEPTH[1] * wear)
    speck = f.noise(24.0, 602, 1)
    score = f.noise(6.0, 601, 2) * 0.6 + speck * 0.4
    chip = np.zeros(f.shape, bool)
    if band.any() and min(lu, lv) >= CHIP_MIN:
        chip = band & (score > np.quantile(score[band], 1 - (CHIPS[0] + CHIPS[1] * wear)))
    lum = rgb.mean(-1)
    bare = np.where((lum < 0.3)[..., None], P.mix(rgb, P.col('#8a8e92'), 0.5), np.minimum(rgb * 1.35 + 0.03, 1))
    out = np.where(chip[..., None], bare, rgb)
    if not inked(lu, lv, px):
        return out, none
    line = f.edge < w
    # a battered gun's chips bite through the outline here and there
    line &= ~(chip & (speck > 0.9 - 0.25 * wear))
    return np.where(line[..., None], P.mix(out, INK, 0.85), out), line
