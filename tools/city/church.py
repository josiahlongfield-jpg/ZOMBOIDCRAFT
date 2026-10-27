#!/usr/bin/env python3
"""The churches' kit, after the photos in refs/town/church-exterior and church-interior: the white
clapboard church with its red doors and needle spire, the Marion Christian Church (red brick, a
corner tower, white-framed pointed windows, the quilt square on the tower), Sacred Heart at
Bellevue (buff rock-faced ashlar, the clock tower and its green copper), and inside the First
Baptist at St. Marys (varnished pews, beadboard, the lettered chancel arch, schoolhouse globes),
St. Stanislaus Kostka at Bay City (the white and gold reredos, blue ribs, red carpet) and
St. Matthew's at Paisley (dark pews, hanging ring lights, banners).

- stained glass in pointed (and round) lancets, one block wide and two to four high: jewel
  medallions or amber diamond quarries, whole, lit from inside by candles, or broken; the glass
  sits back in the wall behind an outer face of brick, stone or white trim; belfry lancets of
  louvres; three-by-three rose windows;
- the spire: tapering octagon pieces over a broach for a three-wide tower (white shingle, slate or
  copper) and the gilt cross on top;
- the rock-faced buff ashlar wall and its weathered versions, the rubble plinth, the barn quilt
  square, date stones;
- inside: beadboard wainscot, dark roof timbers, pale and dark pews (with hymnals, with kneelers,
  broken), the altar and its cloths, candlesticks, the wall cross, pulpit, lectern, communion
  rail, hymn board, the lettered band, pipe organ and console, font, votive stands and the red
  sanctuary lamp (both glow red when lit), schoolhouse pendants and ring chandeliers, statues,
  stations, banners, the confessional, the reredos and its pinnacles, camp cots for the refuge;
- the churchyard: headstones in granite and marble (fictional names), obelisks, crosses, open
  graves.

    python3 tools/city/church.py        rewrites the ch_ entries in blocks.json

photo_textures.py draws the textures (church.register()); world/county/Church.java builds with
them. As in the other kits the base model faces north: anything on a wall has its back at z = 16.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import exterior as E  # noqa: E402
from furniture import area, disc, edges, text_mask  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

WHITE = "ext_trim_white"
STONE = "stone_trim"
# the wall a window sits in, and what frames it: white-painted wood in brick (Marion), dressed
# stone in ashlar (Bellevue), white trim in clapboard
WALLS = {"red": ("brick_red", WHITE), "ashlar": ("ch_ashlar", STONE), "white": ("siding_white", WHITE)}
PALS = ("jewel", "amber")
STATES = ("", "_lit", "_broken")
ROSE_WALLS = ("red", "ashlar")
SPIRES = {"white": "ch_shingle_white", "slate": "ch_shingle_slate", "copper": "cv_copper_seam"}

RUBY, GOLD, BLUE, GREEN, PURPLE, PEARL = (150, 24, 36), (214, 158, 46), (30, 56, 140), (36, 118, 64), (98, 40, 120), (222, 214, 184)
LEAD = (26, 26, 28)


# ---------------------------------------------------------------- small helpers

def grid():
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    return yy, xx


def erode(m, r):
    """Shrinks a mask by r texels (eight-neighbour steps, no scipy here)."""
    out = m.copy()
    for _ in range(r):
        o = out.copy()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                s = np.zeros_like(out)
                ys0, ys1 = max(0, dy), N + min(0, dy)
                xs0, xs1 = max(0, dx), N + min(0, dx)
                s[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx] = out[ys0:ys1, xs0:xs1]
                o &= s
        out = o
    return out


def dilate(m, r):
    return ~erode(~m, r)


def edge_of(ids):
    """Where a piece id changes to the right or below: the lead between two pieces."""
    e = np.zeros(ids.shape, bool)
    e[:, :-1] |= ids[:, :-1] != ids[:, 1:]
    e[:-1, :] |= ids[:-1, :] != ids[1:, :]
    return e


def voronoi(rng, n, box=(0, 0, N, N)):
    x0, y0, x1, y1 = box
    yy, xx = grid()
    pts = np.stack([rng.uniform(x0, x1, n), rng.uniform(y0, y1, n)], 1)
    d = (xx[..., None] - pts[:, 0]) ** 2 + (yy[..., None] - pts[:, 1]) ** 2
    return np.argmin(d, axis=2)


def opening(part, shape):
    """The window's opening in a tile: between the jambs (x 4..60), above the sill (lo), under the
    pointed or round head (hi)."""
    yy, xx = grid()
    m = (xx >= 4) & (xx < 60)
    if part == "lo":
        m &= yy < 56
    elif part == "hi":
        if shape == "round":
            m &= (yy >= 60) | (np.hypot(xx - 31.5, yy - 60) < 28)
        else:
            m &= (yy >= 60) | ((np.hypot(xx - 60, yy - 60) < 56) & (np.hypot(xx - 4, yy - 60) < 56))
    return m


# ---------------------------------------------------------------- stained glass

def glass(pal, part, shape, state, seed):
    """One tile of a lancet's glass, leaded, translucent; transparent outside the opening (the
    outer face of the wall covers it). Saddle bars where tiles meet; lit glass glows by its own
    colour; broken glass has whole pieces gone."""
    t = P.Tex(seed, (0, 0, 0))
    rng = t.rng
    yy, xx = grid()
    op = opening(part, shape)
    inner = erode(op, 5)
    border = op & ~inner
    ids = np.zeros((N, N), np.int32)
    col = np.zeros((N, N, 3), np.float32)
    lead = np.zeros((N, N), bool)
    if pal == "louvre":
        s = E.louvres(seed, (88, 96, 86))
        t.c[:] = s.c
        t.h[:] = s.h
        t.alpha[:] = np.where(op, 255, 0)
        t.smooth[:] = 0.25
        t.c[~op] = 0
        dark = op & (((yy.astype(int)) % 4) == 3)
        t.c[dark] = (18, 18, 18)
        return F.wear(t, 0.3, 0)
    # the border band: short pieces round the edge, two colours alternating
    if part == "hi":
        ang = np.degrees(np.arctan2(xx - 31.5, 60 - yy))
        bid = np.where(yy >= 60, 100 + (xx > 32) * 10 + (yy // 9).astype(int), np.floor((ang + 90) / 15).astype(int))
    else:
        bid = (yy // 9).astype(np.int32) + (xx > 32) * 50
    bcols = (RUBY, BLUE) if pal == "amber" else (RUBY, GOLD)
    for k in np.unique(bid[border]):
        m = border & (bid == k)
        col[m] = bcols[int(k) % 2]
    ids[border] = 1000 + bid[border]
    field = inner
    if pal == "jewel":
        # deep blue ground in irregular pieces, now and then purple or green
        v = voronoi(rng, 14, (4, 0, 60, N))
        ids[field] = v[field]
        for k in np.unique(v[field]):
            m = field & (v == k)
            r = rng.random()
            col[m] = PURPLE if r < 0.1 else GREEN if r < 0.18 else BLUE
        # the medallion: a gold ring round ruby, a pearl quatrefoil in it
        cy = 26 if part == "lo" else 34 if part == "hi" and shape == "round" else 38 if part == "hi" else 32
        r = np.hypot(xx - 31.5, yy - cy)
        ring = field & (r < 15) & (r >= 11.5)
        mid = field & (r < 11.5)
        ids[ring] = 2000 + (np.degrees(np.arctan2(xx - 31.5, yy - cy)) // 45).astype(int)[ring]
        col[ring] = GOLD
        ids[mid] = 3000
        col[mid] = RUBY
        quat = np.zeros((N, N), bool)
        for dx, dy in ((0, -4.5), (0, 4.5), (-4.5, 0), (4.5, 0)):
            quat |= np.hypot(xx - 31.5 - dx, yy - cy - dy) < 4
        quat &= mid
        ids[quat] = 3001
        col[quat] = PEARL
        if part == "hi" and shape != "round":
            # a trefoil up in the point
            tr = field & (np.hypot(xx - 31.5, yy - 19) < 5)
            ids[tr] = 4000
            col[tr] = GREEN
    else:
        # amber cathedral glass in diamond quarries, a gold rosette in the middle
        u = ((xx - 4 + yy) // 12).astype(int)
        w = ((xx - 4 - yy + 64) // 12).astype(int)
        q = u * 64 + w
        ids[field] = q[field]
        choices = [(228, 212, 168)] * 4 + [(212, 164, 80)] * 2 + [(172, 192, 136), (210, 156, 146)]
        for k in np.unique(q[field]):
            m = field & (q == k)
            col[m] = choices[int(rng.integers(0, len(choices)))]
        if part != "lo":
            cy = 32 if part == "mid" else 40
            r = np.hypot(xx - 31.5, yy - cy)
            ro = field & (r < 6)
            ids[ro] = 5000
            col[ro] = GOLD
            c = field & (r < 2.5)
            ids[c] = 5001
            col[c] = RUBY
    lead = edge_of(np.where(op, ids, -1)) & dilate(op, 1)
    # saddle bars across where one tile meets the next
    bars = op & ((yy < 1) | (yy >= N - 1))
    if part == "hi":
        bars &= yy >= N - 1
    if part == "lo":
        bars &= yy < 1
    lead |= bars
    # glass: mottled, seedy, darker toward the dirty bottom of each light
    t.c = col * (0.88 + 0.24 * (P.fbm(rng, ((8, 1.0), (16, 0.6), (32, 0.4))) * 0.5 + 0.5))[..., None]
    pieces = np.where(op & ~lead, ids, -1)
    for k in np.unique(pieces[pieces >= 0]):
        t.c[pieces == k] *= rng.uniform(0.86, 1.12)
    t.c *= (1.02 - 0.12 * (yy / N))[..., None]
    t.speckle(0.01, (240, 236, 220), 0.25)
    t.c[lead] = LEAD
    t.alpha[:] = np.where(lead, 255, np.where(op, 196, 0))
    t.smooth[:] = np.where(lead, 0.35, 0.86)
    t.f0[:] = 30
    t.h[:] = np.where(lead, 0.8, 0.55)
    t.c[~op] = 0
    if state == "_lit":
        lum = t.c @ np.array([0.299, 0.587, 0.114], np.float32)
        t.emit[:] = np.where(op & ~lead, np.clip(0.3 + lum / 255 * 0.6, 0, 0.85), 0)
        t.c[op & ~lead] = np.clip(t.c[op & ~lead] * 1.25 + 12, 0, 255)
    elif state == "_broken":
        gone = np.zeros((N, N), bool)
        for k in np.unique(pieces[pieces >= 0]):
            if rng.random() < 0.3:
                gone |= pieces == k
        bent = dilate(gone, 1) & lead & (rng.random((N, N)) < 0.5)
        t.alpha[gone | bent] = 0
    return t


def outer(wall, part, shape, seed):
    """The outer face of a window block: the wall's own brick or stone (or siding) round the head,
    the frame (white wood, or dressed stone) round the opening, the sill; the opening itself see-through."""
    wname, fname = WALLS[wall]
    t = NS.build(wname)
    t.rng = np.random.default_rng(seed)
    fr = NS.build(fname)
    yy, xx = grid()
    op = opening(part, shape)
    jamb = dilate(op, 4) & ~op
    if part != "hi":
        jamb = (xx < 4) | (xx >= 60)
    t.c[jamb] = fr.c[jamb]
    t.h[jamb] = 0.9
    edges_m = dilate(op, 1) & jamb
    t.c[edges_m] *= 0.7
    if part == "hi" and wall != "white":
        # a ring of headers over the frame (soldier bricks, or voussoirs) with their joints
        ring = dilate(op, 9) & ~dilate(op, 4)
        ang = np.degrees(np.arctan2(xx - 31.5, 60 - yy))
        joints = ring & (np.abs(((ang + 90) % 9) - 4.5) > 4.0)
        t.c[ring] *= 0.92 if wall == "red" else 1.05
        t.c[joints] *= 0.6
        t.h[ring] = 0.95
    if part == "lo":
        s = (yy >= 56)
        t.c[s] = fr.c[s] * 1.04
        area(t, 0, 56, N, 58, mul=1.15, dh=0.1)
        area(t, 0, 62, N, N, mul=0.7)
    t.alpha[:] = np.where(op, 0, 255)
    t.streaks(0.5, 0.8)
    return t


def rose(wall, state, seed, n=4):
    """A rose window n (four) blocks across: a stone ring, twelve petals of ruby and blue tipped gold
    round a gold-ringed centre with a pearl quatrefoil, leaded; set flush in brick or ashlar.
    Returns n * n tiles, row by row from the top-left as seen from outside."""
    S = N * n
    q = n / 3  # drawn for three blocks across
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    cx = cy = S / 2 - 0.5
    r = np.hypot(xx - cx, yy - cy)
    ang = (np.degrees(np.arctan2(xx - cx, cy - yy)) + 360) % 360
    R = 84 * q
    glass_m = r < R
    ring = (r >= R) & (r < R + 10 * q)
    wname = WALLS[wall][0]
    tiles = []
    ids = np.full((S, S), -1, np.int32)
    col = np.zeros((S, S, 3), np.float32)
    sector = (ang // 30).astype(int)
    within = (ang % 30) - 15
    # centre: gold ring, ruby, pearl quatrefoil
    centre = r < 22 * q
    ids[centre] = 1
    col[centre] = RUBY
    cring = (r >= 22 * q) & (r < 28 * q)
    ids[cring] = 2 + (ang // 30).astype(int)[cring]
    col[cring] = GOLD
    quat = np.zeros((S, S), bool)
    for dx, dy in ((0, -8), (0, 8), (-8, 0), (8, 0)):
        quat |= np.hypot(xx - cx - dx * q, yy - cy - dy * q) < 7 * q
    quat &= centre
    ids[quat] = 20
    col[quat] = PEARL
    # petals: each sector a pointed lobe from r 28 to R-6, blue or ruby, gold at the tip, blue ground between
    rel = (r - 28 * q) / (R - 34 * q)
    lobe = (r >= 28 * q) & (r < R - 6 * q) & (np.abs(within) < 13 * np.sin(np.clip(rel, 0, 1) * np.pi) + 1)
    ground = glass_m & ~centre & ~cring & ~lobe
    v = (rng.random(12) < 0.5)
    for k in range(12):
        m = lobe & (sector == k)
        tip = m & (rel > 0.72)
        ids[m & ~tip] = 100 + k
        col[m & ~tip] = RUBY if (k % 2) else BLUE if not v[k] else PURPLE
        ids[tip] = 200 + k
        col[tip] = GOLD
        g = ground & (sector == k)
        ids[g] = 300 + k * 2 + (r[g] > (R - 6 * q)).astype(int)
        col[g] = BLUE if (k % 2) else GREEN
    lead = np.zeros((S, S), bool)
    e = np.where(glass_m, ids, -1)
    lead[:, :-1] |= e[:, :-1] != e[:, 1:]
    lead[:-1, :] |= e[:-1, :] != e[1:, :]
    lead &= r < R + 1
    gone = np.zeros((S, S), bool)
    if state == "_broken":
        for k in np.unique(ids[glass_m]):
            if rng.random() < 0.3:
                gone |= (ids == k) & glass_m & ~lead
    mott = 0.9 + 0.2 * rng.random((S, S))
    for j in range(n):
        for i in range(n):
            t = NS.build(wname)
            t.rng = np.random.default_rng(seed + j * n + i)
            sl = (slice(j * N, (j + 1) * N), slice(i * N, (i + 1) * N))
            g = glass_m[sl]
            rg = ring[sl]
            s = NS.build(STONE)
            t.c[rg] = s.c[rg]
            jn = rg & (np.abs((ang[sl] % 15) - 7.5) > 7.0)
            t.c[jn] *= 0.6
            t.h[rg] = 0.95
            gc = col[sl] * mott[sl][..., None]
            t.c[g] = gc[g]
            ld = lead[sl]
            t.c[ld] = LEAD
            t.alpha[:] = np.where(g & ~ld, 196, 255)
            t.alpha[gone[sl]] = 0
            t.smooth[g] = 0.86
            t.f0[g] = 30
            t.h[g] = 0.55
            if state == "_lit":
                lum = t.c @ np.array([0.299, 0.587, 0.114], np.float32)
                t.emit[:] = np.where(g & ~ld, np.clip(0.3 + lum / 255 * 0.6, 0, 0.85), 0)
            t.streaks(0.4, 0.85)
            tiles.append(t)
    return tiles


_ROSES = {}


def rose_tiles(wall, state):
    if (wall, state) not in _ROSES:
        _ROSES[(wall, state)] = rose(wall, state, 9490)
    return _ROSES[(wall, state)]


# ---------------------------------------------------------------- walls, roofs, outside

def ashlar(seed):
    """Buff rock-faced sandstone laid in courses, three to a block (Sacred Heart, Bellevue): each
    stone its own shade, its face pitched rough and proud (lit on top, falling away to the
    joints), thin pale joints between; the grain of real rock laid over it all."""
    t = P.Tex(seed, (172, 154, 114))
    rng = t.rng
    yy, xx = grid()
    ids = np.zeros((N, N), np.int32)
    centre = np.zeros((N, N, 2), np.float32)
    rows = (0, 21, 43, N)
    k = 0
    for r in range(3):
        widths = []
        while sum(widths) < N - 22:
            widths.append(float(rng.uniform(20, 40)))
        widths.append(N - sum(widths))
        x = rng.uniform(0, N)
        for w in widths:
            for wrap in (0, -N, N):
                m = (yy >= rows[r]) & (yy < rows[r + 1]) & (xx >= x + wrap) & (xx < x + w + wrap)
                ids[m] = k
                centre[m] = (x + wrap + w / 2, (rows[r] + rows[r + 1]) / 2)
            x += w
            k += 1
    rough = P.fbm(rng, ((8, 1.0), (16, 0.7), (32, 0.5)))
    for s_ in np.unique(ids):
        m = ids == s_
        t.c[m] *= rng.uniform(0.84, 1.12)
        t.c[m] += np.array((rng.uniform(-2, 9), rng.uniform(-3, 4), rng.uniform(-8, 2)), np.float32)
    # the pitched face: high in the middle, falling off toward the joints, lit from above
    dx = np.abs(xx - centre[..., 0]) / 18
    dy = np.abs(yy - centre[..., 1]) / 10
    bulge = np.clip(1 - np.maximum(dx, dy) ** 3, 0, 1)
    t.h[:] = 0.5 + 0.3 * bulge + 0.15 * rough
    t.c *= (0.8 + 0.22 * bulge + 0.18 * rough)[..., None]
    up = np.roll(t.h, 1, 0) - t.h
    t.c *= (1 - np.clip(up * 1.5, -0.15, 0.25))[..., None]
    joint = edge_of(ids)
    t.c[joint] = (188, 176, 150)
    t.h[joint] = 0.3
    under = np.roll(joint, 1, 0) & ~joint
    t.c[under] *= 0.72
    g = NS.photo("Rock036", span=2, sat=0.3, bright=1.0, seed=seed)
    lum = g.c @ np.array([0.299, 0.587, 0.114], np.float32)
    t.c *= (0.7 + 0.6 * lum / max(float(lum.mean()), 1))[..., None] ** 0.8
    t.smooth[:] = 0.1
    t.streaks(0.55, 0.78)
    return t


def rubble(seed):
    t = NS.photo("mossy_rock", span=1.5, sat=0.4, bright=0.85, height=1.4, seed=seed)
    t.streaks(0.4, 0.8)
    return t


def shingle(seed, kind):
    if kind == "white":
        t = NS.photo("roof_slates_02", span=1.2, sat=0.2, bright=1.2, seed=seed)
        F.tone(t, (206, 204, 196), 0.9)
        # paint gone in patches to the grey wood under it
        m = np.clip((P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4))) - 0.45) * 4, 0, 1)
        t.c = t.c * (1 - m[..., None]) + np.array((112, 106, 96), np.float32) * m[..., None]
    else:
        t = NS.photo("roof_slates_03", span=1.2, sat=0.3, bright=0.8, seed=seed)
        F.tone(t, (68, 74, 84), 1.1)
    t.streaks(0.6, 0.75)
    return t


def quilt(seed):
    """The quilt square on the Marion tower: a cross on a star in red, gold, blue and green, painted
    on boards and weathered. Four tiles, two by two."""
    S = N * 2
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    c = np.zeros((S, S, 3), np.float32) + (226, 222, 210)
    u, v = (xx - S / 2) / (S / 2), (yy - S / 2) / (S / 2)
    star = (np.abs(u) + np.abs(v)) < 0.95
    c[star] = (196, 40, 36)
    tri = star & (np.abs(u) < 0.45) & (np.abs(v) < 0.45)
    c[tri & (u * v > 0)] = (232, 182, 40)
    c[tri & (u * v <= 0)] = (40, 86, 160)
    kite = star & ((np.abs(u) > 0.62) | (np.abs(v) > 0.62))
    c[kite] = (52, 124, 70)
    cross = ((np.abs(u) < 0.08) & (np.abs(v) < 0.62)) | ((np.abs(v + 0.15) < 0.08) & (np.abs(u) < 0.4))
    c[cross] = (24, 22, 22)
    frame = (np.abs(u) > 0.94) | (np.abs(v) > 0.94)
    c[frame] = (40, 38, 36)
    out = []
    for j in range(2):
        for i in range(2):
            t = P.Tex(seed + j * 2 + i, (0, 0, 0))
            t.c = c[j * N:(j + 1) * N, i * N:(i + 1) * N].copy()
            t.vary(0.06)
            for x in range(0, N, 16):
                area(t, x, 0, x + 1, N, mul=0.7, dh=-0.1)  # the boards under the paint
            F.wear(t, 0.3, 1)
            t.streaks(0.5, 0.8)
            out.append(t)
    return out


def fit(text, font, size, w):
    """The biggest size up to `size` at which every line of text fits w texels."""
    from PIL import ImageFont
    while size > 5:
        f = ImageFont.truetype(font, size)
        if max(f.getlength(line) for line in text.split("|")) <= w:
            break
        size -= 1
    return size


def datestone(seed, text):
    t = NS.build(STONE)
    t.rng = np.random.default_rng(seed)
    m = text_mask(text, SERIF, fit(text, SERIF, 15 if text.count("|") < 2 else 11, 50), 54, 40, seed=seed, spacing=1.05)
    cut = np.zeros((N, N), np.float32)
    cut[12:52, 5:59] = m
    t.c *= (1 - 0.55 * cut)[..., None]
    t.h -= 0.35 * cut
    edges(t, 2, 2, N - 2, N - 2, w=1, light=1.12, dark=0.7)
    t.streaks(0.7, 0.75)
    return t


# ---------------------------------------------------------------- inside

def beadboard(seed):
    """Varnished beadboard wainscot under a cap rail (First Baptist, St. Marys)."""
    t = F.wood(seed, "pine", rot=True)
    F.tone(t, (164, 112, 60), 1.1)
    for x in range(0, N, 8):
        area(t, x, 10, x + 1, N, mul=0.45, dh=-0.25)
        area(t, x + 1, 10, x + 2, N, mul=1.2, dh=0.1)
    area(t, 0, 0, N, 8, mul=0.8, dh=0.15)
    area(t, 0, 8, N, 10, mul=0.5, dh=-0.2)
    area(t, 0, 0, N, 1, mul=1.25)
    t.smooth[:] = 0.55
    area(t, 0, N - 4, N, N, mul=0.6)
    return F.wear(t, 0.25, 2)


def hymn_board(seed):
    t = F.wood(seed, "dark")
    F.tone(t, (70, 44, 28))
    edges(t, 2, 2, N - 2, N - 2, w=2)
    m = text_mask("HYMNS", SERIF, 9, 50, 10, seed=seed)
    F.paste(t, m, 7, 5, (210, 172, 90), h=0.9)
    rng = np.random.default_rng(seed)
    for k in range(3):
        y = 18 + k * 14
        area(t, 8, y, 56, y + 11, (22, 20, 20), h=0.8)
        n = str(int(rng.integers(12, 520)))
        m = text_mask(n, SANS, 9, 40, 10, seed=seed + k)
        F.paste(t, m, 12, y + 1, (226, 222, 210), h=0.9)
    return F.wear(t, 0.25, 1)


def lettering(seed, w, text):
    """The band across the chancel wall: deep red serifs on cream between gold lines, one tile a block."""
    T = {}
    for i in range(w):
        def tile(i=i):
            t = F.plain(seed + i, (214, 204, 178), 0.04, 0.3)
            m = text_mask(text, SERIF, 30, N * w - 30, 40, seed=seed, spacing=1.0)
            full = np.zeros((N, N * w), np.float32)
            full[12:52, 15:15 + m.shape[1]] = m
            part = full[:, i * N:(i + 1) * N]
            F.paste(t, part, 0, 0, (112, 26, 30), h=0.8)
            area(t, 0, 4, N, 7, (186, 146, 66), h=0.9, smooth=0.6, f0=180)
            area(t, 0, 57, N, 60, (186, 146, 66), h=0.9, smooth=0.6, f0=180)
            t.grime(0.15)
            return t
        T[f"ch_text_{w}_{i}"] = tile
    return T


def linen(seed):
    t = F.plain(seed, (232, 228, 214), 0.03, 0.2)
    x = np.arange(N, dtype=np.float32)
    t.c *= (1 + 0.06 * np.sin(x / 3.1))[None, :, None]
    for x0 in range(0, N, 8):
        disc(t, x0 + 4, N - 3, 3, mul=0.86)  # the lace edge
    t.grime(0.25)
    return t


def frontal(seed, rgb):
    """An altar frontal or a lectern's hanging: coloured cloth, a gilt cross, the gold fringe."""
    t = F.plain(seed, rgb, 0.05, 0.25)
    x = np.arange(N, dtype=np.float32)
    t.c *= (1 + 0.05 * np.sin(x / 2.6))[None, :, None]
    area(t, 29, 14, 35, 48, GOLD, h=0.9, smooth=0.6, f0=150)
    area(t, 22, 22, 42, 28, GOLD, h=0.9, smooth=0.6, f0=150)
    area(t, 0, N - 6, N, N, GOLD, h=0.8, f0=120)
    for xx in range(0, N, 3):
        area(t, xx, N - 6, xx + 1, N, mul=0.6)
    t.grime(0.2)
    return t


def banner(seed, rgb, symbol):
    """A church banner two blocks long: the rod, the cloth, a white symbol, a fringe; two tiles."""
    S = N * 2
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:S, 0:N].astype(np.float32)
    c = np.zeros((S, N, 3), np.float32) + np.array(rgb, np.float32)
    c *= (1 + 0.05 * np.sin(xx / 2.8))[..., None]
    alpha = np.full((S, N), 255.0, np.float32)
    alpha[(yy > S - 18) & (np.abs(xx - 31.5) * 2 > (S - yy))] = 0  # the swallowtail point
    c[:5] = (176, 140, 64)
    if symbol == "cross":
        m = ((np.abs(xx - 31.5) < 4) & (yy > 26) & (yy < 104)) | ((np.abs(yy - 50) < 4) & (np.abs(xx - 31.5) < 18))
    else:
        # a dove coming down: the body, two wings, the tail
        m = (np.hypot((xx - 31.5) / 7, (yy - 56) / 13) < 1) | (np.hypot((xx - 18) / 13, (yy - 46) / 5) < 1) \
            | (np.hypot((xx - 45) / 13, (yy - 46) / 5) < 1) | ((np.abs(xx - 31.5) < 5) & (yy > 30) & (yy < 44))
    c[m] = (232, 228, 214)
    fr = (yy > S - 26) & (alpha > 0) & ((xx.astype(int) % 3) == 0)
    c[fr] *= 0.7
    out = []
    for j in range(2):
        t = P.Tex(seed + j, (0, 0, 0))
        t.c = c[j * N:(j + 1) * N].copy()
        t.alpha = alpha[j * N:(j + 1) * N].copy()
        t.smooth[:] = 0.2
        t.grime(0.2)
        out.append(t)
    return out


def station(seed, numeral):
    """A station of the cross: a carved plaster scene, a cross over figures, in an oak frame, its numeral below."""
    t = F.plain(seed, (204, 198, 184), 0.04, 0.3)
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    fig = np.zeros((N, N), bool)
    for k in range(3):
        fx = 16 + k * 14 + rng.uniform(-2, 2)
        fig |= (np.hypot((xx - fx) / 4, (yy - 34) / 9) < 1) | (np.hypot(xx - fx, yy - 22) < 3.2)
    fig |= ((np.abs(xx - 44) < 1.5) & (yy > 10) & (yy < 40)) | ((np.abs(yy - 16) < 1.5) & (np.abs(xx - 44) < 7))
    t.c[fig] *= 1.12
    t.h[fig] = 1.0
    sh = np.roll(fig, 1, 1) & ~fig
    t.c[sh] *= 0.7
    area(t, 0, 0, N, 5, (92, 58, 34), h=1.0)
    area(t, 0, N - 13, N, N, (92, 58, 34), h=1.0)
    area(t, 0, 0, 5, N, (92, 58, 34), h=1.0)
    area(t, N - 5, 0, N, N, (92, 58, 34), h=1.0)
    m = text_mask(numeral, SERIF, 9, 30, 9, seed=seed)
    F.paste(t, m, 17, N - 11, (214, 178, 96))
    return F.wear(t, 0.3, 1)


def keys(seed):
    """Two manuals of an organ console seen from above and front, stop knobs over them."""
    t = F.wood(seed, "dark")
    for y0 in (24, 42):
        area(t, 4, y0, 60, y0 + 12, (226, 220, 200), h=0.8, smooth=0.6)
        for x in range(4, 60, 3):
            area(t, x, y0, x + 1, y0 + 12, (60, 56, 50))
        for x in range(5, 58, 7):
            area(t, x, y0, x + 2, y0 + 7, (24, 22, 22), h=0.95)
    for k, x in enumerate(range(8, 58, 6)):
        disc(t, x, 10, 2.2, (226, 220, 200) if k % 3 else (40, 30, 30), h=1.0)
        disc(t, x, 17, 2.2, (226, 220, 200) if k % 2 else (120, 30, 30), h=1.0)
    return F.wear(t, 0.3, 1)


def curtain(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.25)
    x = np.arange(N, dtype=np.float32)
    t.c *= (1 + 0.18 * np.sin(x / 2.2))[None, :, None]
    t.h += (0.1 * np.sin(x / 2.2))[None, :]
    t.grime(0.3)
    return t


def stone_face(seed, text, base):
    t = NS.build(base) if base != "ch_marble" else marble(seed)
    t.rng = np.random.default_rng(seed)
    m = text_mask(text, SERIF, fit(text, SERIF, 10, 48), 52, 44, seed=seed, spacing=1.15)
    cut = np.zeros((N, N), np.float32)
    cut[10:54, 6:58] = m
    t.c *= (1 - 0.5 * cut)[..., None]
    t.h -= 0.3 * cut
    # lichen and moss creeping up from the ground
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.4)))
    yy, _ = grid()
    lich = np.clip((n + yy / N * 0.9 - 0.75) * 3, 0, 1)
    t.c = t.c * (1 - lich[..., None] * 0.6) + np.array((88, 104, 58), np.float32) * lich[..., None] * 0.6
    t.speckle(0.02, (196, 190, 120), 0.4)
    t.streaks(0.6, 0.7)
    return t


def marble(seed):
    t = NS.photo("Marble012", span=1, sat=0.3, bright=1.05, tint=(1.03, 1.0, 0.95), seed=seed)
    t.grime(0.25)
    return t


def granite(seed):
    t = NS.photo("granite_tile", span=1, sat=0.3, bright=0.9, seed=seed)
    F.tone(t, (118, 116, 112), 1.2)
    t.smooth[:] = 0.5
    return t


GRAVES = ["MARTHA A.|HARLAN|1858 - 1931", "JAS. T.|PRUETT|1866 - 1918", "INFANT|SON|1902", "OUR MOTHER|ELLEN SLADE|1871 - 1940",
          "W. R.|CALDER|1849 - 1913", "AT REST|NORA BEAL|1880 - 1959"]


def textures():
    T = {}
    T["ch_ashlar"] = lambda: ashlar(9400)
    T["ch_rubble"] = lambda: rubble(9401)
    T["ch_shingle_white"] = lambda: shingle(9402, "white")
    T["ch_shingle_slate"] = lambda: shingle(9403, "slate")
    for pal in PALS + ("louvre",):
        for part in ("lo", "mid", "hi", "hi_round"):
            for state in (STATES if pal != "louvre" else ("",)):
                shape = "round" if part == "hi_round" else "pointed"
                p = "hi" if part == "hi_round" else part
                T[f"ch_glass_{pal}_{part}{state}"] = lambda pal=pal, p=p, shape=shape, state=state, k=len(T): glass(pal, p, shape, state, 9410 + k)
    for wall in WALLS:
        for part in ("lo", "mid", "hi") + (("hi_round",) if wall == "ashlar" else ()):
            shape = "round" if part == "hi_round" else "pointed"
            p = "hi" if part == "hi_round" else part
            T[f"ch_frame_{wall}_{part}"] = lambda wall=wall, p=p, shape=shape: outer(wall, p, shape, 9480)
    for wall in ROSE_WALLS:
        for state in STATES:
            for i in range(16):
                T[f"ch_rose_{wall}{state}_{i}"] = lambda wall=wall, state=state, i=i: rose_tiles(wall, state)[i]
    import civic
    for k, part in (("l", 0), ("r", 1)):
        T[f"ch_oculus_red_{k}"] = lambda part=part: civic.oculus("brick_red", 9470, part)
    for i in range(4):
        T[f"ch_quilt_{i}"] = lambda i=i: quilt(9500)[i]
    T["ch_datestone_a"] = lambda: datestone(9504, "ERECTED|A.D. 1889")
    T["ch_datestone_b"] = lambda: datestone(9505, "CHRISTIAN|CHURCH|1902")
    T["ch_beadboard"] = lambda: beadboard(9506)
    T["ch_beam"] = lambda: F.wear(F.tone(F.wood(9507, "dark"), (66, 44, 30), 1.2), 0.2, 1)
    T["ch_pew_pale"] = lambda: F.wear(F.tone(F.wood(9508, "oak"), (184, 140, 88), 1.1), 0.2, 2)
    T["ch_hymnals"] = lambda: F.books(9509)
    T["ch_hymn_board"] = lambda: hymn_board(9510)
    T.update(lettering(9511, 6, "HOLY  HOLY  HOLY"))
    T["ch_linen"] = lambda: linen(9520)
    T["ch_frontal_green"] = lambda: frontal(9521, (36, 92, 50))
    T["ch_frontal_purple"] = lambda: frontal(9522, (82, 36, 100))
    T["ch_marble"] = lambda: marble(9523)
    T["ch_gilt"] = lambda: F.plain(9524, (198, 156, 72), 0.08, 0.65)
    T["ch_candle"] = lambda: F.plain(9525, (232, 226, 200), 0.04, 0.4)

    def flame():
        t = F.plain(9526, (255, 206, 110), 0.05, 0.1)
        t.emit[:] = 1.0
        return t
    T["ch_flame"] = flame

    def redglass(lit):
        t = F.plain(9527 + lit, (150, 18, 22), 0.08, 0.85)
        t.alpha[:] = 210
        if lit:
            t.c = np.clip(t.c * 1.5 + (40, 0, 0), 0, 255)
            t.emit[:] = 0.9
        return t
    T["ch_redglass"] = lambda: redglass(0)
    T["ch_redglass_lit"] = lambda: redglass(1)
    T["ch_plaster_white"] = lambda: F.wear(F.plain(9529, (226, 222, 210), 0.05, 0.35), 0.35, 0)
    T["ch_mantle_blue"] = lambda: F.wear(F.plain(9530, (64, 92, 150), 0.06, 0.3), 0.3, 0)
    T["ch_skin"] = lambda: F.plain(9531, (214, 184, 156), 0.04, 0.3)
    T["ch_station_a"] = lambda: station(9532, "VII")
    T["ch_station_b"] = lambda: station(9533, "XII")
    for k, (rgb, sym) in enumerate((((84, 36, 104), "cross"), ((150, 30, 34), "dove"))):
        for j in range(2):
            T[f"ch_banner_{k}_{j}"] = lambda rgb=rgb, sym=sym, j=j, k=k: banner(9534 + k, rgb, sym)[j]
    T["ch_curtain_red"] = lambda: curtain(9538, (118, 30, 36))
    T["ch_curtain_purple"] = lambda: curtain(9539, (72, 34, 88))

    def pipe():
        t = F.plain(9540, (188, 188, 182), 0.06, 0.6)
        t.f0[:] = 180
        t.speckle(0.04, (150, 150, 146), 0.4)
        x = np.arange(N, dtype=np.float32)
        t.c *= (0.75 + 0.35 * np.sin(np.clip(x / N, 0, 1) * np.pi))[None, :, None]
        return t
    T["ch_pipe"] = pipe
    T["ch_keys"] = lambda: keys(9541)
    T["ch_canvas"] = lambda: F.wear(F.plain(9542, (132, 124, 92), 0.07, 0.15), 0.35, 0)
    T["ch_blanket"] = lambda: F.wear(F.plain(9543, (86, 90, 70), 0.08, 0.1), 0.3, 0)
    T["ch_granite"] = lambda: granite(9544)
    for i, text in enumerate(GRAVES):
        T[f"ch_grave_face_{i}"] = lambda text=text, i=i: stone_face(9550 + i, text, "ch_marble" if i % 2 else STONE)
    T["ch_dirt"] = lambda: NS.photo("farm_soil", span=1, sat=0.6, bright=0.75, seed=9560)
    T["ch_pit"] = lambda: F.plain(9561, (34, 28, 22), 0.15, 0.05)
    return T


def register(ns):
    global NS
    NS = ns
    T = textures()
    ns.PHOTO["ch_ashlar"] = T.pop("ch_ashlar")  # the weathered versions start from PHOTO
    ns.EXTRA.update(T)
    ns.VARIANTS["ch_ashlar"] = ["b", "crack", "moss", "soot"]


# ---------------------------------------------------------------- the blocks

def mirror_x(boxes):
    return [[16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5], *b[6:]] for b in boxes]


def shift(boxes, dx, dz):
    return [[b[0] + dx, b[1], b[2] + dz, b[3] + dx, b[4], b[5] + dz, *b[6:]] for b in boxes]


def octo(y0, y1, r, tex, cx=8.0, cz=8.0):
    a, b = r * 0.92, r * 0.42
    return [[cx - a, y0, cz - b, cx + a, y1, cz + b, tex], [cx - b, y0, cz - a, cx + b, y1, cz + a, tex],
            [cx - r * 0.72, y0, cz - r * 0.72, cx + r * 0.72, y1, cz + r * 0.72, tex]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, render=None, tex=None, light=None, loot=None, **extra):
        if rotate == "axis" and boxes:
            boxes = P.along_z(boxes)
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    # ---- walls
    add("ch_ashlar", None, "Ashlar", "STONE", rotate=None, tex="ch_ashlar")
    for k in ("b", "crack", "moss", "soot"):
        add(f"ch_ashlar_{k}", None, "Ashlar", "STONE", rotate=None, tex=f"ch_ashlar_{k}")
    add("ch_rubble", None, "Rubble Plinth", "STONE", rotate=None, tex="ch_rubble")

    # ---- lancets: an outer face of wall and frame with the opening cut in it, the leaded glass set
    # back in the middle of the wall, an inner face the same; the sill sticks out under the bottom light
    for wall, (wname, fname) in WALLS.items():
        for part in ("lo", "mid", "hi") + (("hi_round",) if wall == "ashlar" else ()):
            fr = f"ch_frame_{wall}_{part}"
            shell = [[0, 0, 0, 16, 16, 1, {"north": fr, "south": fr, "side": fname, "up": fname, "down": fname}],
                     [0, 0, 15, 16, 16, 16, {"north": fr, "south": fr, "side": fname, "up": fname, "down": fname}]]
            if part == "lo":
                shell += [[0.5, 0, -1.5, 15.5, 2, 1, fname], [1, 0, 1, 15, 2, 15, fname]]
            for pal in PALS + ("louvre",):
                for state in (STATES if pal != "louvre" else ("",)):
                    g = f"ch_glass_{pal}_{part}{state}"
                    pane = [[0, 0, 7.5, 16, 16, 8.5, {"north": g, "south": g, "side": g, "up": g, "down": g}]]
                    name = f"ch_win_{wall}_{pal}_{part}{state}"
                    add(name, shell + pane, "Louvred Opening" if pal == "louvre" else "Stained Glass Window",
                        "WOOD" if pal == "louvre" else "GLASS", render="cutout" if pal == "louvre" else "translucent",
                        **({"light": 6} if state == "_lit" else {}))
    for wall in ROSE_WALLS:
        side = WALLS[wall][0]
        for state in STATES:
            for i in range(16):
                tex = f"ch_rose_{wall}{state}_{i}"
                add(tex, None, "Rose Window", "GLASS", render="translucent",
                    tex={"north": tex, "south": tex, "side": side, "up": side, "down": side}, **({"light": 6} if state == "_lit" else {}))

    # ---- the spire: four tapering octagon pieces (k 0 at the bottom), their corner-centred copies,
    # the broach that carries a three-wide tower's square top up into the octagon, the gilt cross
    for m, tex in SPIRES.items():
        for k in range(4):
            boxes = []
            for s in range(4):
                r = 7.2 - (k * 4 + s) * 0.42
                boxes += octo(s * 4, s * 4 + 4, max(r, 0.6), {"side": tex, "up": tex, "down": tex})
            if k == 3:
                boxes += [[7, 16, 7, 9, 18, 9, "ch_gilt"]]
            add(f"ch_spire_{m}_{k}", boxes, "Spire", "STONE" if m != "copper" else "METAL", rotate=None)
            add(f"ch_spire_{m}_{k}_c", shift(boxes, 8, 8), "Spire", "STONE" if m != "copper" else "METAL", rotate=None)
        f = {"up": tex, "side": tex, "down": tex, "north": tex}
        # an edge of the broach: low at the front (north), rising to the spire behind it
        add(f"ch_broach_{m}", [[0, 0, 4 * j, 16, 4 + 4 * j, 4 * j + 4, f] for j in range(4)], "Spire Broach", "STONE")
        # a corner: low to the north and west, rising to the south-east
        add(f"ch_broach_{m}_corner", [[4 * i, 0, 4 * j, 4 * i + 4, 4 + 4 * min(i, j), 4 * j + 4, f] for i in range(4) for j in range(4)],
            "Spire Broach", "STONE")
    cross = [[7, 0, 7, 9, 16, 9, "ch_gilt"], [3.5, 9, 7, 12.5, 11, 9, "ch_gilt"], [6.5, 0, 6.5, 9.5, 2, 9.5, "ch_gilt"]]
    add("ch_cross_finial", cross, "Cross", "METAL", rotate="facing")
    add("ch_cross_finial_c", shift(cross, 8, 8), "Cross", "METAL", rotate=None)

    for k in ("l", "r"):
        add(f"ch_oculus_red_{k}", None, "Round Window", "STONE",
            tex={"north": f"ch_oculus_red_{k}", "side": "brick_red", "up": "brick_red", "down": "brick_red"})

    # ---- outside bits
    for i in range(4):
        add(f"ch_quilt_{i}", [[0, 0, 15, 16, 16, 16.6, {"north": f"ch_quilt_{i}", "side": "ch_beam"}]], "Quilt Square", "WOOD", decor=True)
    for k in ("a", "b"):
        add(f"ch_datestone_{k}", None, "Date Stone", "STONE", tex={"north": f"ch_datestone_{k}", "side": STONE, "up": STONE, "down": STONE})

    # ---- inside: wainscot, timbers, the chancel's step
    add("ch_wainscot", None, "Beadboard", "WOOD", rotate=None, tex={"side": "ch_beadboard", "up": "ch_beam", "down": "ch_beam"})
    add("ch_beam", None, "Roof Timber", "WOOD", rotate=None, tex="ch_beam")
    add("ch_step_oak", [[0, 0, 0, 16, 8, 16, "ch_beam"], [0, 7, -1, 16, 8, 0, "ch_beam"]], "Chancel Step", "WOOD")
    add("ch_floor_oak", None, "Oak Floor", "WOOD", rotate=None, tex={"side": "ch_beam", "up": "floor_boards", "down": "ch_beam"})

    # ---- pews: pale oak like the chapels' (plain, ends, with hymnals in the rack), dark ones with
    # kneelers, and the broken ones
    pew = lambda t: [[0, 6, 4, 16, 8, 14, t], [0, 0, 12, 16, 6, 13, t], [0, 8, 13, 16, 18, 15, t], [0, 13, 15, 16, 14, 17, t]]
    end = lambda t: [[14.5, 0, 3, 16, 18.5, 16, t], [14, 18, 3, 16.5, 19, 16, t]]
    books = [[1.5, 14, 15.2, 4.5, 18, 16.8, {"side": "ch_hymnals", "up": "cv_cushion_red", "down": "ch_hymnals", "north": "ch_hymnals", "south": "ch_hymnals"}],
             [9, 14, 15.2, 12, 17.5, 16.8, {"side": "ch_hymnals", "up": "cv_cushion_red", "down": "ch_hymnals", "north": "ch_hymnals", "south": "ch_hymnals"}]]
    kneel = [[0, 1.5, 0.5, 16, 3.5, 3.5, "cv_cushion_red"], [0.5, 0, 1, 1.5, 2, 3, "cv_pew"], [14.5, 0, 1, 15.5, 2, 3, "cv_pew"]]
    for name, t in (("ch_pew_pale", "ch_pew_pale"), ("ch_pew_dark", "cv_pew")):
        add(name, pew(t), "Pew", "WOOD")
        add(name + "_end", pew(t) + end(t), "Pew", "WOOD")
        add(name + "_end_l", pew(t) + mirror_x(end(t)), "Pew", "WOOD")
        add(name + "_books", pew(t) + books, "Pew", "WOOD", loot="home_books")
        add(name + "_kneel", pew(t) + kneel, "Pew", "WOOD")
        add(name + "_kneel_end", pew(t) + end(t) + kneel, "Pew", "WOOD")
        add(name + "_kneel_end_l", pew(t) + mirror_x(end(t)) + kneel, "Pew", "WOOD")
        # tipped back off its feet, the seat split
        add(name + "_broken", [[0, 0, 2, 16, 2, 14, t], [0, 2, 9, 7, 4, 15, t], [9, 0, 10, 16, 3, 16, t], [0, 0, 0, 16, 1.5, 3, t]],
            "Broken Pew", "WOOD", decor=True)

    # ---- the altar: a table under white linen, the coloured frontal on the middle one; marble for St. Mary's
    for name, body, front in (("ch_altar", "ch_beam", "ch_linen"), ("ch_altar_green", "ch_beam", "ch_frontal_green"),
                              ("ch_altar_purple", "ch_beam", "ch_frontal_purple"), ("ch_altar_marble", "ch_marble", "ch_marble")):
        add(name, [[0, 0, 2, 16, 14, 15, {"side": body, "north": body, "up": body}], [0, 14, 1, 16, 15.5, 15.5, "ch_linen"],
                   [0, 8, 0.5, 16, 15.5, 1, {"north": front, "south": front, "side": "ch_linen"}]], "Altar", "STONE" if body == "ch_marble" else "WOOD")
    for lit in ("", "_lit"):
        stick = [[3, 0, 6, 5, 1, 8, "ch_gilt"], [3.6, 1, 6.4, 4.4, 9, 7.6, "ch_gilt"], [3.4, 9, 6.2, 4.6, 14, 7.8, "ch_candle"],
                 [11, 0, 6, 13, 1, 8, "ch_gilt"], [11.6, 1, 6.4, 12.4, 9, 7.6, "ch_gilt"], [11.4, 9, 6.2, 12.6, 13, 7.8, "ch_candle"]]
        if lit:
            stick += [[3.7, 14, 6.7, 4.3, 15.5, 7.3, "ch_flame"], [11.7, 13, 6.7, 12.3, 14.5, 7.3, "ch_flame"]]
        add(f"ch_candles{lit}", stick, "Candlesticks", "METAL", decor=True, render="cutout", **({"light": 11} if lit else {}))
    add("ch_altar_cross", [[6, 0, 6, 10, 1.5, 10, "ch_gilt"], [7, 1.5, 7, 9, 3, 9, "ch_gilt"], [7.4, 3, 7.4, 8.6, 14, 8.6, "ch_gilt"],
                           [5, 10, 7.4, 11, 11.2, 8.6, "ch_gilt"]], "Altar Cross", "METAL", decor=True)
    # the big wooden cross on the east wall, three blocks high (the foot, the crossing, the head),
    # hung over the line between its cell and the next one east, the middle of an even wall
    add("ch_cross_lo", [[14.5, 0, 14, 17.5, 16, 16, "ch_beam"]], "Cross", "WOOD", decor=True)
    add("ch_cross_mid", [[14.5, 0, 14, 17.5, 16, 16, "ch_beam"], [2, 8, 14, 30, 11, 16, "ch_beam"]], "Cross", "WOOD", decor=True)
    add("ch_cross_hi", [[14.5, 0, 14, 17.5, 8, 16, "ch_beam"]], "Cross", "WOOD", decor=True)
    # pulpit (its panelled drum and, a block up, the desk with the open Bible), lectern with its hanging
    add("ch_pulpit", [[1, 0, 1, 15, 16, 15, {"side": "cv_panel_oak", "up": "cv_oak", "down": "cv_oak"}], [0, 0, 0, 16, 1.5, 16, "cv_oak"]],
        "Pulpit", "WOOD", rotate=None)
    add("ch_pulpit_top", [[1, 0, 1, 15, 4, 15, {"side": "cv_panel_oak", "up": "cv_oak", "down": "cv_oak"}], [0, 4, 0, 16, 5.5, 16, "cv_oak"],
                          [2, 5.5, 1, 14, 8, 7, "cv_oak"], [3, 8, 1.5, 13, 9, 6.5, {"up": "ch_linen", "side": "ch_hymnals"}]],
        "Pulpit", "WOOD")
    add("ch_lectern", [[5, 0, 5, 11, 1.5, 11, "ch_gilt"], [7, 1.5, 7, 9, 12, 9, "ch_gilt"], [2, 12, 5, 14, 14, 12, "cv_oak"],
                       [3, 4, 4.4, 13, 13.5, 5, {"north": "ch_frontal_green", "south": "ch_frontal_green", "side": "ch_frontal_green"}]],
        "Lectern", "WOOD")
    # communion rail: rail on balusters, the red kneeling step on the people's side (north); the gate
    rail = [[0, 13, 5, 16, 15.5, 11, "ch_marble"], [0, 0, 5, 16, 2, 11, "ch_marble"], [0, 0, 0, 16, 3, 4, "cv_cushion_red"]]
    rail += [[x - 1, 2, 7, x + 1, 13, 9, "ch_marble"] for x in (2, 6, 10, 14)]
    add("ch_rail", rail, "Communion Rail", "STONE", rotate="axis")
    add("ch_rail_gate", [[0, 2, 7.5, 16, 12, 8.5, "ch_gilt"], [0, 0, 7, 2, 14, 9, "ch_marble"], [14, 0, 7, 16, 14, 9, "ch_marble"]],
        "Rail Gate", "METAL", rotate="axis", render="cutout")
    add("ch_hymn_board", [[2, 0, 15, 14, 16, 16, {"north": "ch_hymn_board", "side": "ch_beam"}]], "Hymn Board", "WOOD", decor=True)
    for i in range(6):
        add(f"ch_text_6_{i}", [[0, 2, 15.5, 16, 14, 16, f"ch_text_6_{i}"]], "Lettering", "STONE", decor=True)
    # the pipe organ: pipes across a dark case (the lower stage, and the tops in a V), the console
    pipes = [[0, 0, 12, 16, 16, 16, "ch_beam"]]
    for i, x in enumerate((2, 5, 8, 11, 14)):
        pipes += octo(0, 16, 1.4 + (i % 2) * 0.3, "ch_pipe", cx=x, cz=10)
    add("ch_pipes", pipes, "Organ Pipes", "METAL")
    tops = [[0, 0, 12, 16, 6, 16, "ch_beam"], [0, 6, 13, 16, 7, 16, "ch_gilt"]]
    for i, x in enumerate((2, 5, 8, 11, 14)):
        h = 14 - abs(i - 2) * 3
        tops += octo(0, h, 1.4 + (i % 2) * 0.3, "ch_pipe", cx=x, cz=10) + [[x - 1.6, 2, 8.2, x + 1.6, 4, 8.6, "ch_gilt"]]
    add("ch_pipes_top", tops, "Organ Pipes", "METAL")
    add("ch_organ_console", [[0, 0, 6, 16, 12, 16, {"side": "ch_beam", "north": "cv_panel_oak", "up": "ch_keys"}],
                             [0, 12, 10, 16, 18, 16, {"north": "ch_keys", "side": "ch_beam", "up": "ch_beam"}],
                             [1, 0, 0, 15, 1.5, 6, "ch_beam"], [2, 4, 0, 14, 5.5, 4, "cv_oak"]], "Organ Console", "WOOD")
    # the font: an octagon of stone on a stem, an oak lid with a cross handle
    add("ch_font", octo(0, 2, 6.5, "ch_marble") + octo(2, 9, 3, "ch_marble") + octo(9, 14, 7, "ch_marble")
        + octo(14, 15, 6.6, "cv_oak") + [[7.5, 15, 7.5, 8.5, 18, 8.5, "ch_gilt"], [6.5, 16.5, 7.5, 9.5, 17.2, 8.5, "ch_gilt"]],
        "Font", "STONE", rotate=None)
    add("ch_stoup", [[4, 6, 11, 12, 9, 16, "ch_marble"], [5, 4, 12, 11, 6, 16, "ch_marble"], [7, 9, 15, 9, 13, 16, "ch_marble"]],
        "Holy Water Stoup", "STONE", decor=True)
    # votive stands (three tiers of red glass cups) and the sanctuary lamp: both glow red when lit
    for lit in ("", "_lit"):
        g = f"ch_redglass{lit}"
        stand = [[1, 0, 2, 15, 1, 14, "cv_iron"], [1, 0, 2, 2, 10, 3, "cv_iron"], [14, 0, 2, 15, 10, 3, "cv_iron"]]
        for k, (y, z) in enumerate(((3, 10), (6, 7), (9, 4))):
            stand += [[1, y - 0.5, z - 1, 15, y, z + 3, "cv_iron"]]
            stand += [[x, y, z, x + 2, y + 2.5, z + 2, g] for x in (2, 5, 8, 11)]
        add(f"ch_votive{lit}", stand, "Votive Candles", "METAL", render="translucent", **({"light": 9} if lit else {}))
        lamp = [[7.4, 8, 7.4, 8.6, 16, 8.6, "cv_iron"], [5, 4, 5, 11, 8, 11, g], [6, 2, 6, 10, 4, 10, "ch_gilt"],
                [4.5, 7.5, 4.5, 11.5, 8.5, 11.5, "ch_gilt"]]
        add(f"ch_sanct_lamp{lit}", lamp, "Sanctuary Lamp", "METAL", rotate=None, render="translucent", **({"light": 10} if lit else {}))
        # schoolhouse globe on its rod (First Baptist) and the ring chandelier (St. Matthew's)
        add(f"ch_pendant{lit}", [[7.5, 8, 7.5, 8.5, 16, 8.5, "cv_iron"], [6, 7, 6, 10, 8, 10, "cv_brass"]]
            + octo(1, 7, 4.2, f"cv_globe{lit}") + [[6, 0, 6, 10, 1, 10, f"cv_globe{lit}"]],
            "Pendant Light", "GLASS", rotate=None, **({"light": 13} if lit else {}))
        ring = [[7.5, 6, 7.5, 8.5, 16, 8.5, "cv_iron"]]
        ring += [[1, 4, 1, 15, 5, 2, "cv_iron"], [1, 4, 14, 15, 5, 15, "cv_iron"], [1, 4, 2, 2, 5, 14, "cv_iron"], [14, 4, 2, 15, 5, 14, "cv_iron"]]
        for x, z in ((2, 2), (8, 1.5), (14, 2), (14.5, 8), (14, 14), (8, 14.5), (2, 14), (1.5, 8)):
            ring += [[x - 0.8, 5, z - 0.8, x + 0.8, 8, z + 0.8, f"cv_globe{lit}"]]
        add(f"ch_chandelier{lit}", ring, "Chandelier", "METAL", rotate=None, **({"light": 14} if lit else {}))
    # a statue on its plinth two blocks high: robe, mantle, the head and hands
    add("ch_statue", [[3, 0, 3, 13, 4, 13, "ch_marble"], [4, 4, 5, 12, 16, 12, "ch_plaster_white"], [3.5, 9, 4.5, 12.5, 16, 12.5, "ch_mantle_blue"]],
        "Statue", "STONE")
    add("ch_statue_top", [[4.5, 0, 5, 11.5, 6, 11.5, "ch_mantle_blue"], [5, 0, 4.5, 11, 5, 6, "ch_plaster_white"],
                          [6.5, 2, 3.5, 9.5, 4, 5, "ch_skin"], [6, 6, 6, 10, 11, 10, "ch_skin"], [5.5, 9, 5.5, 10.5, 12, 10.5, "ch_mantle_blue"],
                          [4.5, 6.5, 11, 11.5, 13.5, 11.5, "ch_gilt"]], "Statue", "STONE")
    for k in ("a", "b"):
        add(f"ch_station_{k}", [[2, 2, 15, 14, 15, 16, {"north": f"ch_station_{k}", "side": "cv_oak"}]], "Station", "STONE", decor=True)
    for k in range(2):
        add(f"ch_banner_{k}", [[2, 0, 15.2, 14, 16, 15.8, f"ch_banner_{k}_1"]], "Banner", "WOOL", decor=True, render="cutout")
        add(f"ch_banner_{k}_top", [[2, 0, 15.2, 14, 16, 15.8, f"ch_banner_{k}_0"], [1, 14.5, 14.5, 15, 15.5, 16, "ch_gilt"]],
            "Banner", "WOOL", decor=True, render="cutout")
    # the confessional: the priest's door between two curtained bays, a cornice over all three
    add("ch_conf_door", [[0, 0, 2, 16, 16, 16, {"north": "cv_panel_oak", "side": "cv_oak", "up": "cv_oak"}], [6, 9, 1.5, 10, 13, 2, "ch_beam"]],
        "Confessional", "WOOD")
    add("ch_conf_bay", [[0, 0, 2, 1.5, 16, 16, "cv_oak"], [14.5, 0, 2, 16, 16, 16, "cv_oak"], [0, 0, 15, 16, 16, 16, "cv_oak"],
                        [1.5, 1, 3, 14.5, 16, 3.6, "ch_curtain_purple"], [1.5, 0, 9, 14.5, 4, 15, "cv_oak"]], "Confessional", "WOOD")
    add("ch_conf_top", [[0, 0, 2, 16, 4, 16, "cv_oak"], [-0.5, 4, 1, 16.5, 6, 16, "cv_oak"], [6, 6, 8, 10, 10, 10, "ch_gilt"]],
        "Confessional", "WOOD")
    # the reredos behind the high altar: white panels with gilt tracery, pinnacles along its top
    add("ch_reredos", [[0, 0, 12, 16, 16, 16, {"north": "ch_plaster_white", "side": "ch_plaster_white"}],
                       [1, 2, 11, 3, 14, 12, "ch_gilt"], [13, 2, 11, 15, 14, 12, "ch_gilt"], [3, 12, 11, 13, 13, 12, "ch_gilt"],
                       [5, 3, 11.4, 11, 11, 12, "ch_mantle_blue"]], "Reredos", "STONE")
    add("ch_pinnacle", [[3, 0, 9, 13, 3, 16, "ch_plaster_white"], [5, 3, 10.5, 11, 8, 15.5, "ch_plaster_white"],
                        [6.5, 8, 12, 9.5, 13, 14, "ch_plaster_white"], [7.3, 13, 12.6, 8.7, 16, 13.4, "ch_gilt"],
                        [4.5, 5, 10, 5.5, 6, 11, "ch_gilt"], [10.5, 5, 10, 11.5, 6, 11, "ch_gilt"]], "Pinnacle", "STONE", decor=True)
    # the refuge's camp cots: canvas on folding legs, an army blanket thrown on
    add("ch_cot_head", [[1, 0, 0, 2, 6, 16, "cv_iron"], [14, 0, 0, 15, 6, 16, "cv_iron"], [1, 5, 0, 15, 6.5, 16, "ch_canvas"],
                        [3, 6.5, 9, 13, 9, 14.5, "ch_linen"], [1.5, 6.5, 0, 14.5, 8, 8, "ch_blanket"]], "Cot", "WOOL", decor=True)
    add("ch_cot_foot", [[1, 0, 0, 2, 6, 16, "cv_iron"], [14, 0, 0, 15, 6, 16, "cv_iron"], [1, 5, 0, 15, 6.5, 16, "ch_canvas"],
                        [1.5, 6.5, 6, 14.5, 9, 16, "ch_blanket"]], "Cot", "WOOL", decor=True)

    # ---- the churchyard
    for i in range(len(GRAVES)):
        face = f"ch_grave_face_{i}"
        stone = "ch_marble" if i % 2 else STONE
        f = {"north": face, "south": stone, "side": stone, "up": stone, "down": stone}
        if i % 3 == 0:
            body = [[3, 0, 7, 13, 11, 10, f], [3.6, 11, 7, 12.4, 12.5, 10, stone], [5, 12.5, 7, 11, 13.5, 10, stone],
                    [6.5, 13.5, 7, 9.5, 14, 10, stone]]  # round-topped tablet
        elif i % 3 == 1:
            body = [[3, 0, 7, 13, 12, 10, f], [4, 12, 7, 12, 14, 10, stone], [6, 14, 7, 10, 15.5, 10, stone]]  # gothic
        else:
            body = [[2, 0, 6, 14, 8, 11, f], [3, 8, 6.5, 13, 9, 10.5, stone]]  # a low block
        add(f"ch_headstone_{i}", [[1, 0, 5, 15, 1, 12, stone]] + body, "Headstone", "STONE", loot=None)
        # sunk half into the soft ground and gone over backwards a little
        add(f"ch_headstone_{i}_sunk", [[b[0], max(0, b[1] - 3), b[2] + 1.5, b[3], max(1, b[4] - 3), b[5] + 1.5, b[6]] for b in body],
            "Headstone", "STONE")
    add("ch_obelisk", [[2, 0, 2, 14, 4, 14, STONE], [3.5, 4, 3.5, 12.5, 16, 12.5, {"north": "ch_grave_face_0", "side": STONE, "up": STONE}]],
        "Obelisk", "STONE")
    add("ch_obelisk_top", [[4, 0, 4, 12, 10, 12, STONE], [5, 10, 5, 11, 13, 11, STONE], [6.5, 13, 6.5, 9.5, 15, 9.5, STONE]],
        "Obelisk", "STONE", rotate=None)
    add("ch_grave_cross", [[3, 0, 5, 13, 3, 11, STONE], [6.5, 3, 7, 9.5, 16, 10, STONE], [2.5, 10, 7, 13.5, 12.5, 10, STONE]],
        "Stone Cross", "STONE")
    add("ch_grave_mound", [[1, 0, 1, 15, 3, 15, {"up": "ch_dirt", "side": "ch_dirt"}], [3, 3, 3, 13, 4, 13, {"up": "ch_dirt", "side": "ch_dirt"}]],
        "Grave", "GRAVEL", rotate=None, decor=True)
    # dug up from below: the hole, the soil thrown round it, the coffin lid split
    add("ch_grave_open", [[0, 0, 0, 16, 16, 2, "ch_dirt"], [0, 0, 14, 16, 16, 16, "ch_dirt"], [0, 0, 2, 2, 16, 14, "ch_dirt"],
                          [14, 0, 2, 16, 16, 14, "ch_dirt"], [2, 0, 2, 14, 4, 14, {"up": "ch_pit", "side": "ch_pit"}],
                          [3, 4, 4, 9, 5, 12, "ch_beam"], [9.5, 4, 3, 13, 6, 10, "ch_beam"]], "Open Grave", "GRAVEL", rotate=None)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("ch_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} church blocks in {path}")


if __name__ == "__main__":
    main()
