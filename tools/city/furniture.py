#!/usr/bin/env python3
"""Home furniture for the county's houses, after Project Zomboid's interiors: kitchen runs of base
and wall cabinets in three finishes (honey oak, white laminate, seventies walnut) with a sink, a
coil-top stove, a fridge papered in magnets and drawings, a dishwasher and what's left on the
counter; three-piece sofas, armchairs, a CRT on its stand, bookcases, lamps, rugs, radiators and
the things on the walls; beds, dressers, wardrobes, desks, toy chests and laundry; toilets, tubs,
vanities and medicine cabinets; the garage's workbench, tool chest, shelving, freezer and boxes;
and what the end left behind: bodies under sheets, blood, candles, notes and spray-painted
warnings. Floors carry the ceiling of the room below on their underside; walls come papered,
tiled or panelled, each with a worn variant. Everything is a few years past cared for.

Every cupboard, drawer, fridge and shelf is a searchable fitting. Its default table is one of
the home_ tables in tools/city/shop_loot.py, picked by what the container is, the way Zomboid
picks loot by room and container.

    python3 tools/city/furniture.py        rewrites the home_ entries in blocks.json

photo_textures.py draws the textures (furniture.register()). Pieces face north: the front is the
north side and the back (z = 16) stands against the wall, so a piece's facing is the way it
looks into the room. Wall-hung decor (pictures, notes, curtains, blood) follows the signs
instead: its north side is against the wall and it faces the wall. Pieces of more than one
block: sofas and tubs run along x (_l at the low x when facing north), beds along z (_head at the
back), and fridges, wardrobes, bookcases, gun cabinets and water heaters stack (_top above).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
LUM = np.array([0.299, 0.587, 0.114], np.float32)
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SCRAWL = "/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf"

BRASS = (172, 140, 78)
STEEL = (168, 170, 170)
CHROME = (206, 208, 210)
BLOOD = (78, 10, 8)


# ---------------------------------------------------------------- painting helpers

def tone(t, rgb, contrast=1.0):
    """Recolours a texture to a mean colour, keeping its light and dark detail."""
    lum = t.c @ LUM
    rel = (lum / max(float(lum.mean()), 1.0) - 1) * contrast + 1
    t.c = np.clip(rel[..., None] * np.array(rgb, np.float32), 0, 255)
    return t


def mat(material, rgb=None, contrast=1.0, rot=False, span=1.0, offset=(0.2, 0.3), seed=0, smooth=None):
    """A photo material, turned a quarter (grain upright) and recoloured if asked."""
    t = NS.photo(material, span=span, offset=offset, sat=0.85, bright=1.0, seed=seed)
    if rot:
        t.c, t.h, t.smooth = (np.ascontiguousarray(np.rot90(a)) for a in (t.c, t.h, t.smooth))
    if rgb is not None:
        tone(t, rgb, contrast)
    if smooth is not None:
        t.smooth = np.clip(t.smooth * 0.4 + smooth * 0.6, 0, 1)
    return t


def plain(seed, rgb, vary=0.05, smooth=0.3):
    t = P.Tex(seed, rgb)
    t.vary(vary)
    t.h += P.fbm(t.rng, ((16, 1.0), (32, 0.5))) * 0.02
    t.smooth[:] = smooth
    return t


def area(t, x0, y0, x1, y1, col=None, mul=None, h=None, dh=None, smooth=None, f0=None, alpha=None):
    x0, y0, x1, y1 = (int(round(v)) for v in (max(0, x0), max(0, y0), min(N, x1), min(N, y1)))
    if x1 <= x0 or y1 <= y0:
        return
    s = (slice(y0, y1), slice(x0, x1))
    if col is not None:
        t.c[s] = col
    if mul is not None:
        t.c[s] *= np.array(mul, np.float32)
    if h is not None:
        t.h[s] = h
    if dh is not None:
        t.h[s] += dh
    if smooth is not None:
        t.smooth[s] = smooth
    if f0 is not None:
        t.f0[s] = f0
    if alpha is not None:
        t.alpha[s] = alpha


def edges(t, x0, y0, x1, y1, w=1, light=1.18, dark=0.62, dh=0.08):
    """Raised edges: lit along the top and left, in shadow along the bottom and right."""
    area(t, x0, y0, x1, y0 + w, mul=light, dh=dh)
    area(t, x0, y0 + w, x0 + w, y1, mul=light, dh=dh)
    area(t, x0, y1 - w, x1, y1, mul=dark, dh=dh)
    area(t, x1 - w, y0 + w, x1, y1 - w, mul=dark, dh=dh)


def sunk(t, x0, y0, x1, y1, w=1):
    edges(t, x0, y0, x1, y1, w, light=0.6, dark=1.14, dh=-0.12)


def gap(t, x0, y0, x1, y1, dark=0.3):
    """A door's or drawer's gap: a dark line all round."""
    for a, b, c, d in ((x0, y0, x1, y0 + 1), (x0, y1 - 1, x1, y1), (x0, y0, x0 + 1, y1), (x1 - 1, y0, x1, y1)):
        area(t, a, b, c, d, mul=dark, dh=-0.25)


def disc(t, cx, cy, r, col=None, mul=None, h=None, dh=None, smooth=None, f0=None, alpha=None, ring=None, soft=0.0):
    """A filled circle (or a ring of the given width), wrapping round the edges."""
    yy, xx = np.mgrid[0:N, 0:N]
    dx = (xx - cx + N / 2) % N - N / 2
    dy = (yy - cy + N / 2) % N - N / 2
    d = np.hypot(dx, dy)
    m = d <= r if ring is None else (d <= r) & (d > r - ring)
    if soft:
        w = np.clip((r - d) / soft, 0, 1) * (1 if ring is None else 1)
    else:
        w = m.astype(np.float32)
    w = w[..., None]
    if col is not None:
        t.c = t.c * (1 - w) + np.array(col, np.float32) * w
    if mul is not None:
        t.c = t.c * (1 - w) + t.c * np.array(mul, np.float32) * w
    if h is not None:
        t.h[m] = h
    if dh is not None:
        t.h[m] += dh
    if smooth is not None:
        t.smooth[m] = smooth
    if f0 is not None:
        t.f0[m] = f0
    if alpha is not None:
        t.alpha[m] = alpha
    return m


def knob(t, x, y, col=BRASS, r=1):
    area(t, x - r, y - r, x + r, y + r, col, h=1.0, smooth=0.75, f0=230)
    area(t, x - r, y - r, x, y, mul=1.35)
    area(t, x, y + r, x + r + 1, y + r + 1, mul=0.55)  # its shadow


def bar(t, x0, y0, x1, y1, col=STEEL):
    area(t, x0, y0, x1, y1, col, h=1.0, smooth=0.7, f0=230)
    if x1 - x0 >= y1 - y0:
        area(t, x0, y0, x1, y0 + 1, mul=1.3)
        area(t, x0, y1, x1, y1 + 1, mul=0.5)
    else:
        area(t, x0, y0, x0 + 1, y1, mul=1.3)
        area(t, x1, y0 + 1, x1 + 1, y1 + 1, mul=0.5)


def ring_pull(t, x, y, col=BRASS):
    """A brass ring pull hanging from a back plate."""
    area(t, x - 1, y - 2, x + 1, y, col, h=1.0, smooth=0.7, f0=230)
    for dx, dy in ((-2, 0), (-2, 1), (-2, 2), (1, 0), (1, 1), (1, 2), (-1, 3), (0, 3)):
        area(t, x + dx, y + dy, x + dx + 1, y + dy + 1, col, h=1.0, smooth=0.7, f0=230)
    area(t, x - 1, y + 4, x + 1, y + 5, mul=0.6)


def fingers(t, x, y, r=5, amount=0.28):
    """Years of hands round a handle."""
    yy, xx = np.mgrid[0:N, 0:N]
    m = np.clip(1 - np.hypot(xx - x, (yy - y) * 0.8) / r, 0, 1) * amount
    m *= 0.5 + 0.5 * np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) + 0.5, 0, 1)
    t.c *= (1 - m)[..., None]


def wear(t, grime=0.3, scratches=0, col=(48, 42, 34), specks=0.01):
    t.grime(grime, col)
    rng = t.rng
    for _ in range(scratches):
        x, y = rng.uniform(0, N, 2)
        ang = rng.uniform(0, np.pi)
        for i in range(int(rng.integers(3, 11))):
            xi, yi = int(x + np.cos(ang) * i) % N, int(y + np.sin(ang) * i) % N
            t.c[yi, xi] = np.clip(t.c[yi, xi] * 1.06 + 3, 0, 255)
    if specks:
        t.speckle(specks, (30, 26, 22), 0.35)
    return t


def blob_mask(rng, cx, cy, r, rough=0.45, wrap=True):
    """A ragged round mask, 0..1."""
    yy, xx = np.mgrid[0:N, 0:N]
    dx, dy = xx - cx, yy - cy
    if wrap:
        dx = (dx + N / 2) % N - N / 2
        dy = (dy + N / 2) % N - N / 2
    n = P.fbm(rng, ((8, 1.0), (16, 0.6), (32, 0.3)))
    d = np.hypot(dx, dy) / max(r, 0.5) + n * rough
    return np.clip((1 - d) * 3, 0, 1)


def stain(t, x, y, r, col=(110, 82, 48), amount=0.45, tide=True):
    """A dried water stain with a darker tide line."""
    m = blob_mask(t.rng, x, y, r)
    t.mix(m * amount, col, 1.0)
    if tide:
        edge = np.clip(m * (1 - m) * 4, 0, 1)
        t.mix(edge * amount, (col[0] * 0.6, col[1] * 0.55, col[2] * 0.5), 1.0)


def blood(t, x, y, r, amount=0.9, glossy=True, wrap=True):
    """Dried blood: near black at the heart, rust brown at the edges."""
    m = blob_mask(t.rng, x, y, r, rough=0.6, wrap=wrap)
    heart = np.clip(m * 1.6 - 0.6, 0, 1)
    t.mix(m * amount, (96, 22, 16), 1.0)
    t.mix(heart * amount, BLOOD, 1.0)
    if glossy:
        t.smooth = np.maximum(t.smooth, heart * 0.55)
    return m


def drips(t, x0, x1, y0, n, length=(6, 24), col=BLOOD, width=1):
    rng = t.rng
    for _ in range(n):
        x = int(rng.uniform(x0, x1))
        ln = int(rng.uniform(*length))
        for i in range(ln):
            w = width + (1 if i > ln - 3 else 0)
            area(t, x, y0 + i, x + w, y0 + i + 1, col)


def text_mask(text, font, size, w, h, angle=0.0, jitter=0.0, seed=0, ss=4, align="center", spacing=1.0):
    """White-on-black lettering at ss times size, shrunk to w x h; lines split on '|'."""
    rng = np.random.default_rng(seed)
    big = Image.new("L", (w * ss, h * ss), 0)
    d = ImageDraw.Draw(big)
    f = ImageFont.truetype(font, size * ss)
    lines = text.split("|")
    lh = int(size * ss * 1.05 * spacing)
    y = (h * ss - lh * len(lines)) // 2
    for line in lines:
        tw = d.textlength(line, font=f)
        x = (w * ss - tw) / 2 if align == "center" else ss * 2
        for ch in line:
            cw = d.textlength(ch, font=f)
            dy = rng.normal(0, jitter * ss) if jitter else 0
            layer = Image.new("L", (int(cw) + size * ss, lh + size * ss), 0)
            ImageDraw.Draw(layer).text((size * ss // 2, size * ss // 4), ch, fill=255, font=f)
            if jitter:
                layer = layer.rotate(rng.normal(0, jitter * 9), resample=Image.BICUBIC)
            big.paste(255, (int(x - size * ss // 2), int(y + dy - size * ss // 4)), layer)
            x += cw
        y += lh
    if angle:
        big = big.rotate(angle, resample=Image.BICUBIC)
    return np.asarray(big.resize((w, h), Image.BOX), np.float32) / 255


def paste(t, mask, x, y, col, amount=1.0, h=None, smooth=None):
    mh, mw = mask.shape
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(N, x + mw), min(N, y + mh)
    if x1 <= x0 or y1 <= y0:
        return
    m = mask[y0 - y:y1 - y, x0 - x:x1 - x] * amount
    s = (slice(y0, y1), slice(x0, x1))
    t.c[s] = t.c[s] * (1 - m[..., None]) + np.array(col, np.float32) * m[..., None]
    if h is not None:
        t.h[s] = t.h[s] * (1 - m) + h * m
    if smooth is not None:
        t.smooth[s] = t.smooth[s] * (1 - m) + smooth * m


def periodic(rng, cells):
    """Tileable noise, -1..1."""
    return P.tnoise(rng, cells)


# ---------------------------------------------------------------- materials

FINISH = {
    # wood (or none for a painted laminate), colour, door style, handles, countertop colour
    "oak": ("Wood049", (164, 118, 70), "raised", "knob", (192, 174, 142)),
    "white": (None, (208, 204, 190), "slab", "bar", (146, 146, 142)),
    "walnut": ("wood_cabinet_worn_long", (98, 64, 40), "grooved", "ring", (184, 146, 62)),
}


def finish(f, seed, rot=True):
    material, rgb = FINISH[f][:2]
    if material:
        t = mat(material, rgb, 1.15, rot=rot, seed=seed, smooth=0.35)
    else:
        t = plain(seed, rgb, 0.035, 0.45)
    return t


def wood(seed, kind="oak", rot=False):
    spec = {"oak": ("Wood049", (158, 112, 66)), "dark": ("Wood051", (74, 50, 34)), "pine": ("Wood089", (170, 140, 98)),
            "walnut": ("wood_cabinet_worn_long", (96, 62, 38)), "maple": ("kitchen_wood", (170, 136, 96))}[kind]
    return mat(spec[0], spec[1], 1.15, rot=rot, seed=seed, smooth=0.3)


def enamel(seed, rgb=(222, 218, 202)):
    t = plain(seed, rgb, 0.03, 0.62)
    t.f0[:] = 30
    return t


def handle(t, f, x, y, horizontal=False):
    kind = FINISH[f][3]
    fingers(t, x, y)
    if kind == "knob":
        knob(t, x, y)
    elif kind == "bar":
        if horizontal:
            bar(t, x - 6, y, x + 6, y + 1)
        else:
            bar(t, x, y - 6, x + 1, y + 6)
    else:
        ring_pull(t, x, y)


def door(t, f, x0, y0, x1, y1, hx=None, hy=None, horizontal=False):
    """A cabinet door or drawer front in the finish's style, with its handle."""
    style = FINISH[f][2]
    gap(t, x0, y0, x1, y1)
    if style == "raised":
        edges(t, x0 + 1, y0 + 1, x1 - 1, y1 - 1)
        if y1 - y0 > 14 and x1 - x0 > 12:
            sunk(t, x0 + 4, y0 + 4, x1 - 4, y1 - 4)
            edges(t, x0 + 5, y0 + 5, x1 - 5, y1 - 5, light=1.1, dark=0.8)
    elif style == "slab":
        edges(t, x0 + 1, y0 + 1, x1 - 1, y1 - 1, light=1.1, dark=0.78)
    else:
        edges(t, x0 + 1, y0 + 1, x1 - 1, y1 - 1, light=1.08, dark=0.72)
        if x1 - x0 > 12:
            for gx in (x0 + (x1 - x0) // 3, x0 + 2 * (x1 - x0) // 3):
                area(t, gx, y0 + 3, gx + 1, y1 - 3, mul=0.45, dh=-0.2)
    if hx is not None:
        handle(t, f, hx, hy, horizontal)


# ---------------------------------------------------------------- kitchen

def cab_front(f, kind, seed):
    """Base and wall cabinet fronts. Base fronts fill rows 8-60 (the plate under the worktop),
    wall cabinets rows 0-52."""
    t = finish(f, seed)
    if kind == "doors":
        area(t, 0, 8, N, 9, mul=0.4)
        door(t, f, 1, 9, 63, 21, 32, 14, True)
        door(t, f, 1, 22, 32, 60, 27, 27)
        door(t, f, 32, 22, 63, 60, 37, 27)
    elif kind == "drawers":
        area(t, 0, 8, N, 9, mul=0.4)
        for y0, y1 in ((9, 23), (23, 41), (41, 60)):
            door(t, f, 1, y0, 63, y1, 32, y0 + 5, True)
    elif kind == "sink":
        area(t, 0, 8, N, 9, mul=0.4)
        door(t, f, 1, 9, 63, 21)  # a false front under the basin
        door(t, f, 1, 22, 32, 60, 27, 27)
        door(t, f, 32, 22, 63, 60, 37, 27)
        stain(t, 30, 58, 10, amount=0.35)  # the leak under the sink
    else:  # wall cabinet
        door(t, f, 1, 1, 32, 52, 27, 46)
        door(t, f, 32, 1, 63, 52, 37, 46)
        area(t, 0, 50, N, 52, mul=0.7)
    return wear(t, 0.32, 10)


def worktop(f, seed):
    """Laminate worktop: speckled, ringed by mugs, scored by knives, a darker rolled front edge."""
    t = plain(seed, FINISH[f][4], 0.04, 0.42)
    t.speckle(0.12, np.array(FINISH[f][4]) * 0.75, 0.6)
    t.speckle(0.04, np.array(FINISH[f][4]) * 1.15, 0.6)
    rng = t.rng
    for _ in range(4):
        disc(t, rng.uniform(4, 60), rng.uniform(10, 60), rng.uniform(3, 5), mul=0.82, ring=1)
    for _ in range(14):
        x, y = rng.uniform(0, N, 2)
        area(t, x, y, x + rng.integers(3, 9), y + 1, mul=0.82)
    stain(t, rng.uniform(10, 54), rng.uniform(20, 54), 7, amount=0.25)
    area(t, 0, 0, N, 2, mul=0.72, dh=0.1)  # rolled front edge
    area(t, 0, 2, N, 3, mul=1.12)
    return wear(t, 0.28, 0)


def basin(seed):
    """A steel sink: drain, a plate and a mug left to soak, scum."""
    t = mat("Metal009", (176, 178, 178), 0.8, seed=seed, smooth=0.6)
    t.f0[:] = 230
    disc(t, 32, 32, 3, (30, 30, 30), h=0.4)
    disc(t, 32, 32, 3, (140, 140, 140), ring=1)
    disc(t, 23, 38, 8, (214, 210, 196), h=0.85, smooth=0.8, f0=30)
    disc(t, 23, 38, 6, mul=0.92, ring=1)
    disc(t, 21, 40, 3, (120, 92, 50))  # dried-on food
    disc(t, 42, 24, 4, (60, 90, 120), h=0.9, smooth=0.6, f0=30)  # mug
    disc(t, 42, 24, 3, (38, 26, 18))
    t.grime(0.4, (70, 64, 50))
    return t


def steel(seed, rgb=(170, 172, 172)):
    t = mat("Metal009", rgb, 0.8, seed=seed, smooth=0.55)
    t.f0[:] = 230
    return wear(t, 0.25, 6)


def chrome(seed):
    t = plain(seed, CHROME, 0.06, 0.85)
    t.f0[:] = 230
    t.c *= (0.75 + 0.35 * np.linspace(1, 0, N)[:, None, None])
    return wear(t, 0.2, 0)


def stove_front(seed):
    t = enamel(seed)
    area(t, 0, 8, N, 10, mul=0.5)
    gap(t, 1, 10, 63, 50)
    edges(t, 2, 11, 62, 49, light=1.06, dark=0.8)
    area(t, 12, 20, 52, 40, (34, 32, 30), h=0.6, smooth=0.9, f0=40)  # oven window
    edges(t, 12, 20, 52, 40, light=0.7, dark=1.2)
    t.mix(np.clip(np.linspace(0.4, 0, N)[None, :] * (np.arange(N)[:, None] < 30), 0, 1), (90, 90, 88), 0.4)
    area(t, 14, 33, 50, 39, (52, 40, 28))  # grease baked on the glass
    bar(t, 8, 13, 56, 15, CHROME)
    fingers(t, 32, 14, 9, 0.2)
    gap(t, 1, 51, 63, 62)
    bar(t, 24, 55, 40, 56, CHROME)
    area(t, 0, 62, N, N, (30, 28, 26))
    for x in (6, 58):
        drips(t, x - 3, x + 3, 50, 2, (3, 10), (120, 100, 70))
    return wear(t, 0.38, 8)


def stove_top(seed):
    """Four coil burners over chrome drip pans, years of boil-overs."""
    t = enamel(seed)
    for cx, cy in ((17, 22), (47, 22), (17, 48), (47, 48)):
        disc(t, cx, cy, 11, (196, 196, 194), h=0.6, smooth=0.75, f0=230)
        disc(t, cx, cy, 11, mul=0.7, ring=1)
        blob = blob_mask(t.rng, cx + t.rng.uniform(-3, 3), cy + t.rng.uniform(-3, 3), 9)
        t.mix(blob * 0.75, (70, 48, 26), 1.0)  # burnt-on crud
        for r in (3, 5.5, 8):
            disc(t, cx, cy, r, (36, 34, 32), h=0.95, smooth=0.3, f0=230, ring=1.5)
        area(t, cx - 1, cy - 9, cx + 1, cy, (36, 34, 32), h=0.95)
    for _ in range(5):
        stain(t, t.rng.uniform(4, 60), t.rng.uniform(4, 60), t.rng.uniform(3, 6), (120, 84, 40), 0.4)
    return wear(t, 0.3, 4)


def stove_panel(seed):
    """The back panel: clock, four knobs, a timer. Its face is rows 0-16 (the panel stands
    above the block, so its UVs are shifted down to the top of the texture)."""
    t = enamel(seed, (214, 208, 190))
    area(t, 0, 0, N, 16, (196, 190, 172))
    area(t, 26, 3, 38, 13, (30, 30, 28), smooth=0.9)
    area(t, 28, 5, 36, 11, (200, 196, 184))
    area(t, 31, 6, 32, 9, (20, 20, 20))
    for x in (6, 15, 46, 55):
        disc(t, x, 8, 3, (40, 38, 36), h=1.0, smooth=0.5)
        area(t, x, 5, x + 1, 8, (220, 216, 200))
    return wear(t, 0.35, 0)


def fridge(seed, top):
    """The fridge door, papered over: magnets, a kid's drawing, a photo, a list, a calendar."""
    t = enamel(seed, (222, 212, 186))
    rng = t.rng
    if top:
        gap(t, 4, 12, 60, 40)  # the freezer door, a step above the main door
        area(t, 4, 40, 60, 42, mul=0.35)
        bar(t, 52, 22, 54, 36, (196, 186, 160))
        bar(t, 52, 46, 54, N, (196, 186, 160))
        area(t, 4, 12, 60, 13, mul=1.1)
        # a takeout menu and a photo held by magnets
        area(t, 12, 18, 30, 34, (232, 226, 200))
        for y in range(21, 33, 3):
            area(t, 14, y, 14 + rng.integers(8, 15), y + 1, (60, 60, 70))
        area(t, 34, 46, 46, 58, (238, 236, 230))
        area(t, 35, 47, 45, 55, (110, 120, 96))
        area(t, 37, 50, 40, 55, (176, 140, 110))
        area(t, 41, 49, 43, 55, (70, 60, 50))
    else:
        bar(t, 52, 0, 54, 26, (196, 186, 160))
        # a child's crayon drawing: a house, a sun, three people
        area(t, 10, 6, 34, 26, (240, 238, 228))
        area(t, 14, 15, 24, 23, (196, 60, 40))
        for i in range(6):
            area(t, 13 + i, 15 - i // 2, 25 - i, 16 - i // 2, (160, 40, 30))
        disc(t, 29, 10, 2.5, (230, 190, 40))
        for x in (26, 29, 32):
            area(t, x, 18, x + 1, 23, (40, 60, 160))
            disc(t, x + 0.5, 17, 1, (40, 60, 160))
        # a shopping list, a school photo, a calendar page
        area(t, 36, 30, 48, 50, (236, 230, 196))
        for y in range(33, 49, 3):
            area(t, 38, y, 38 + rng.integers(4, 9), y + 1, (40, 50, 110))
        area(t, 10, 34, 22, 48, (236, 236, 232))
        area(t, 11, 35, 21, 45, (90, 110, 150))
        disc(t, 16, 39, 2.5, (200, 160, 130))
        area(t, 14, 42, 18, 45, (180, 40, 40))
    for _ in range(9):
        x, y = rng.uniform(6, 50), rng.uniform(4, 60)
        col = [(200, 40, 40), (40, 120, 200), (240, 200, 40), (60, 160, 70), (230, 120, 30)][rng.integers(5)]
        area(t, x, y, x + 3, y + 3, col, h=1.0, smooth=0.6)
    area(t, 2, 0, 4, N, mul=0.8)
    area(t, 60, 0, 62, N, mul=0.8)
    fingers(t, 53, 6 if not top else 56, 7, 0.25)
    return wear(t, 0.32, 6)


def dishwasher(seed):
    t = enamel(seed, (214, 206, 182))
    area(t, 0, 8, N, 9, mul=0.4)
    area(t, 1, 9, 63, 19, (120, 96, 70))  # woodgrain control strip
    t.c[9:19, 1:63] *= (0.9 + 0.1 * np.sin(np.arange(62) * 0.9))[None, :, None]
    for x in (8, 14, 20, 26):
        area(t, x, 12, x + 4, 15, (220, 214, 196), h=0.95)
    disc(t, 52, 14, 3, (40, 38, 36), h=1.0)
    gap(t, 1, 19, 63, 60)
    edges(t, 2, 20, 62, 59, light=1.08, dark=0.82)
    bar(t, 12, 22, 52, 24, CHROME)
    fingers(t, 32, 23, 10, 0.2)
    area(t, 8, 30, 20, 34, (60, 60, 64))  # the maker's badge
    return wear(t, 0.32, 6)


def microwave(seed):
    """Front plate, rows 40-64: dark window with its mesh, a keypad, a digital clock gone dark."""
    t = plain(seed, (190, 182, 164), 0.04, 0.5)
    area(t, 9, 42, 40, 62, (28, 28, 26), smooth=0.9)
    for y in range(43, 62, 2):
        area(t, 9, y, 40, y + 1, mul=1.4)
    area(t, 42, 42, 56, 46, (16, 20, 16))
    for r in range(3):
        for c in range(3):
            area(t, 43 + c * 4, 48 + r * 4, 46 + c * 4, 51 + r * 4, (220, 216, 204), h=0.95)
    return wear(t, 0.35, 0)


def toaster(seed):
    """Chrome; the slots sit on the top face (rows 33-41), the lever on the side."""
    t = chrome(seed)
    for y in (33, 39):
        area(t, 20, y, 44, y + 2, (20, 18, 16), h=0.3)
    area(t, 26, 52, 30, 54, (30, 28, 26))
    t.grime(0.35, (90, 70, 40))
    return t


def plates(seed):
    """Top of a stack of dirty plates (a plate rim round the middle), and the stacked edges."""
    t = plain(seed, (220, 216, 204), 0.03, 0.75)
    disc(t, 26, 26, 13, mul=0.85, ring=1.5)
    disc(t, 26, 26, 9, mul=0.94, ring=1)
    for _ in range(3):
        m = blob_mask(t.rng, t.rng.uniform(18, 34), t.rng.uniform(18, 34), 3)
        t.mix(m, [(120, 70, 30), (150, 120, 60), (90, 40, 20)][t.rng.integers(3)], 0.8)
    for y in range(52, N, 2):
        area(t, 0, y, N, y + 1, mul=0.8)
    return wear(t, 0.2, 0)


def mug(seed, rgb):
    t = plain(seed, rgb, 0.04, 0.7)
    disc(t, 22, 50, 4, (40, 26, 16))  # the dregs, seen from above
    return wear(t, 0.2, 0)


def table_top(seed, kind="oak"):
    t = wood(seed, kind)
    rng = t.rng
    for _ in range(5):
        disc(t, rng.uniform(4, 60), rng.uniform(4, 60), rng.uniform(3, 5), mul=0.8, ring=1)
    for _ in range(3):
        stain(t, rng.uniform(4, 60), rng.uniform(4, 60), rng.uniform(3, 7), (60, 40, 24), 0.35)
    return wear(t, 0.3, 18)


def chair_wood(seed):
    return wear(wood(seed, "oak", rot=True), 0.3, 10)


def trash_lid(seed):
    t = plain(seed, (194, 184, 156), 0.05, 0.45)
    t.grime(0.45, (80, 70, 50))
    return t


# ---------------------------------------------------------------- living room

FABRICS = {"floral": ("Fabric028", (156, 128, 104)), "brown": ("Fabric030", (104, 76, 50)),
           "blue": ("Fabric022", (62, 80, 108))}


def fabric(kind, seed, cushion=False):
    material, rgb = FABRICS[kind]
    t = mat(material, rgb, 1.3, seed=seed, smooth=0.12)
    rng = t.rng
    if kind == "floral":
        # faded roses and leaves on a beige ground, repeating every 16 px
        for gy in range(0, N, 16):
            for gx in range(0, N, 16):
                ox = 8 if (gy // 16) % 2 else 0
                cx, cy = (gx + ox + rng.uniform(-2, 2)) % N, gy + 8 + rng.uniform(-2, 2)
                for dx, dy in ((-3, 2), (3, 1), (0, -3)):
                    disc(t, cx + dx * 1.3, cy + dy * 1.3, 2.5, (78, 110, 70))
                disc(t, cx, cy, 3.5, (160, 70, 70))
                disc(t, cx, cy, 2, (120, 40, 44))
                disc(t, cx - 1, cy - 1, 1, (196, 120, 110))
        tone(t, rgb, 1.0)
        t.c = t.c * 0.75 + np.array(rgb, np.float32) * 0.25 + t.c.mean() * 0.0
    elif kind == "brown":
        t.c *= (0.85 + 0.15 * (np.arange(N) % 3 != 0))[None, :, None]  # corduroy wales
        t.h += 0.06 * (np.arange(N) % 3 != 0)[None, :]
    if cushion:
        area(t, 0, 0, N, 2, mul=0.62, dh=-0.2)  # piping and the cushion's seams
        area(t, 0, N - 2, N, N, mul=0.62, dh=-0.2)
        area(t, 0, 0, 2, N, mul=0.7)
        area(t, N - 2, 0, N, N, mul=0.7)
        yy, xx = np.mgrid[0:N, 0:N]
        t.c *= (0.9 + 0.12 * np.sin(np.pi * xx / N) * np.sin(np.pi * yy / N))[..., None]
    for _ in range(2):
        stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(4, 9), (60, 48, 30), 0.3)
    return wear(t, 0.32, 0, specks=0.02)


def coffee_table(seed):
    """Dark wood top: magazines, a mug ring, an ashtray full of butts. Keeps rows 34-40 clear
    (the edge)."""
    t = wood(seed, "dark")
    rng = t.rng
    area(t, 10, 10, 28, 30, (180, 60, 50), smooth=0.5)
    area(t, 12, 12, 26, 18, (232, 226, 210))
    area(t, 16, 13, 30, 31, (40, 90, 140), smooth=0.5)
    area(t, 18, 15, 28, 21, (230, 200, 60))
    disc(t, 46, 20, 6, (120, 120, 116), h=0.9, smooth=0.6)
    disc(t, 46, 20, 4, (60, 58, 54))
    for _ in range(5):
        a = rng.uniform(0, 6.28)
        area(t, 46 + np.cos(a) * 2, 20 + np.sin(a) * 2, 48 + np.cos(a) * 2, 21 + np.sin(a) * 2, (220, 210, 180))
    disc(t, 42, 48, 4, mul=0.75, ring=1)
    area(t, 12, 44, 30, 54, (210, 206, 190))  # a TV guide
    area(t, 14, 46, 28, 48, (180, 30, 30))
    return wear(t, 0.32, 14)


def tv_case(seed):
    t = plain(seed, (44, 40, 36), 0.06, 0.45)
    # woodgrain veneer on the sides of an eighties set
    t.c[:, :] *= (0.92 + 0.08 * np.sin(np.arange(N) * 0.7 + P.fbm(t.rng, ((4, 1.0),)) * 3))[..., None]
    area(t, 0, 0, N, 4, mul=0.8)
    return wear(t, 0.3, 6)


def tv_screen(seed, smashed=False):
    """The screen's face (rows 2-34): a curved, dusty CRT, or one put through."""
    t = plain(seed, (34, 40, 38), 0.03, 0.92)
    t.f0[:] = 40
    yy, xx = np.mgrid[0:N, 0:N]
    curve = np.clip(1 - (((xx - 32) / 30) ** 2 + ((yy - 18) / 18) ** 2), 0, 1)
    t.c *= (0.7 + 0.5 * curve)[..., None]
    glint = np.clip(1 - np.hypot(xx - 20, (yy - 9) * 1.6) / 8, 0, 1)
    t.c += (glint * 60)[..., None]
    t.grime(0.25, (90, 90, 84))
    if smashed:
        hole = blob_mask(t.rng, 36, 20, 8, rough=0.7, wrap=False)
        t.mix(hole, (8, 8, 8), 1.0)
        t.h -= hole * 0.5
        for ang in t.rng.uniform(0, 6.28, 12):
            for i in range(8, 30):
                x, y = int(36 + np.cos(ang) * i), int(20 + np.sin(ang) * i * 0.7)
                if 0 <= x < N and 0 <= y < N:
                    t.c[y, x] = (170, 176, 172)
    # the bezel round the tube
    area(t, 0, 0, N, 2, (52, 48, 44))
    area(t, 0, 34, N, 36, (52, 48, 44))
    return t


def books(seed, junk=False):
    """Shelves of spines between the boards (books in rows 4-20, 24-40, 44-60), picked over:
    gaps, a stack lying flat, a fallen row. junk=True fills a garage shelf instead: paint cans,
    jars of screws, oil, boxes."""
    t = plain(seed, (34, 28, 22), 0.05, 0.2)
    rng = t.rng
    for y0, y1 in ((4, 20), (24, 40), (44, 60)):
        x = 4
        while x < 60:
            if rng.random() < 0.12:
                x += rng.integers(3, 9)  # gap
                continue
            if junk:
                kind = rng.integers(4)
                w = int(rng.integers(5, 10))
                h = int(rng.integers(7, y1 - y0))
                col = [(150, 150, 146), (176, 120, 50), (60, 90, 140), (170, 150, 110)][kind]
                area(t, x, y1 - h, x + w, y1, col, h=0.9, smooth=0.4)
                if kind == 0:  # a paint can, drips down the side
                    area(t, x, y1 - h, x + w, y1 - h + 2, (196, 196, 190))
                    drips(t, x, x + w, y1 - h + 2, 2, (2, 5), [(160, 40, 40), (60, 120, 60), (220, 220, 210)][rng.integers(3)])
                elif kind == 1:  # a jar of screws
                    t.c[y1 - h:y1, x:x + w] *= 0.8 + 0.4 * rng.random((h, w, 1))
                elif kind == 3:  # a cardboard box with marker on it
                    area(t, x + 1, y1 - h + 2, x + w - 1, y1 - h + 3, (40, 36, 30))
                x += w + int(rng.integers(0, 3))
                continue
            if rng.random() < 0.08:  # a stack lying flat
                for i in range(int(rng.integers(2, 5))):
                    col = np.array([rng.integers(40, 170), rng.integers(30, 120), rng.integers(20, 100)], np.float32)
                    area(t, x, y1 - 2 * (i + 1), x + 12, y1 - 2 * i, col, h=0.9)
                x += 13
                continue
            w = int(rng.integers(2, 5))
            h = int(rng.integers(10, y1 - y0 + 1))
            base = np.array([[120, 36, 30], [40, 60, 100], [40, 80, 50], [150, 120, 70], [70, 50, 40],
                             [180, 170, 150], [30, 30, 34], [110, 70, 110]][rng.integers(8)], np.float32)
            col = base * rng.uniform(0.7, 1.1)
            area(t, x, y1 - h, x + w, y1, col, h=0.88, smooth=0.25)
            area(t, x, y1 - h, x + 1, y1, mul=1.2)
            if rng.random() < 0.6:
                area(t, x, y1 - h + 2, x + w, y1 - h + 3, (190, 160, 80))  # gilt band
            x += w
    return wear(t, 0.35, 0, (60, 56, 48))


def lampshade(seed):
    t = plain(seed, (214, 196, 156), 0.06, 0.1)
    t.c *= (0.9 + 0.1 * np.sin(np.arange(N) * 1.6))[None, :, None]  # pleats
    t.grime(0.4, (120, 96, 50))
    stain(t, 40, 30, 5, (130, 100, 50), 0.3)
    return t


def rug(seed, ground, accent, border, edge=None):
    """A worn Persian rug: medallions that tile, a field of small motifs, bald patches. edge
    adds the border: "edge" along the north side, "corner" along north and west, fringed."""
    t = mat("Carpet003", ground, 0.6, seed=seed, smooth=0.05)
    yy, xx = np.mgrid[0:N, 0:N]
    u, v = np.cos(2 * np.pi * xx / N), np.cos(2 * np.pi * yy / N)
    medal = (np.abs(xx - 32) + np.abs(yy - 32)) < 18
    t.mix(medal * 0.9, accent, 1.0)
    t.mix(((np.abs(xx - 32) + np.abs(yy - 32)) < 10) * 0.9, border, 1.0)
    t.mix(((np.abs(xx - 32) + np.abs(yy - 32)) < 5) * 0.9, ground, 1.0)
    corners = (np.minimum(xx, N - xx) + np.minimum(yy, N - yy)) < 12
    t.mix(corners * 0.9, border, 1.0)
    dots = ((xx % 8 == 4) & (yy % 8 == 4)) & ~medal & ~corners
    t.mix(dots * 0.8, accent, 1.0)
    bald = np.clip(P.fbm(t.rng) * 2 - 0.4 + 0.2 * u * v, 0, 1)
    t.mix(bald * 0.5, (150, 140, 120), 1.0)
    if edge:
        sides = [(slice(0, 12), slice(0, N))] + ([(slice(0, N), slice(0, 12))] if edge == "corner" else [])
        for sy, sx in sides:
            t.c[sy, sx] = np.array(border, np.float32) * (0.85 + 0.15 * P.fbm(t.rng)[sy, sx, None])
        area(t, 0, 4, N, 6, accent)
        area(t, 0, 0, N, 2, (214, 204, 180))  # the fringe
        if edge == "corner":
            area(t, 4, 4, 6, N, accent)
            area(t, 0, 0, 2, N, (214, 204, 180))
    return wear(t, 0.3, 0)


RUGS = {"red": ((120, 40, 36), (40, 50, 80), (196, 170, 120)), "blue": ((50, 62, 92), (150, 50, 44), (200, 184, 140))}


def radiator(seed):
    t = plain(seed, (206, 198, 176), 0.05, 0.35)
    t.f0[:] = 230
    m = np.clip(P.fbm(t.rng) * 2 - 0.6, 0, 1)
    t.mix(m, (110, 70, 40), 0.8)  # paint flaking off the iron
    return wear(t, 0.35, 0)


def picture(kind, seed):
    t = plain(seed, (40, 32, 24), 0.05, 0.3)
    rng = t.rng
    if kind == "lake":
        # an oil landscape: sky, mountains, a lake; frame 8-56 x 12-44
        x0, y0, x1, y1 = 8, 12, 56, 44
        for y in range(y0, y1):
            f = (y - y0) / (y1 - y0)
            area(t, x0, y, x1, y + 1, (int(140 - 60 * f), int(160 - 50 * f), int(170 - 30 * f)))
        ridge = 26 + 5 * np.sin(np.arange(N) * 0.25) + 3 * np.sin(np.arange(N) * 0.7)
        for x in range(x0, x1):
            area(t, x, int(ridge[x]), x + 1, 34, (78, 84, 92))
            area(t, x, int(ridge[x]), x + 1, int(ridge[x]) + 2, (200, 204, 206))
        area(t, x0, 34, x1, 38, (54, 76, 48))
        area(t, x0, 38, x1, y1, (70, 96, 118))
        frame_col = (150, 116, 54)
    elif kind == "family":
        x0, y0, x1, y1 = 14, 10, 50, 44
        area(t, x0, y0, x1, y1, (150, 140, 120))
        for i, (cx, hgt) in enumerate(((20, 18), (28, 20), (36, 13), (43, 11))):
            area(t, cx - 3, y1 - hgt, cx + 3, y1, [(60, 70, 110), (110, 40, 40), (180, 160, 60), (60, 110, 70)][i])
            disc(t, cx, y1 - hgt - 3, 3, (206, 170, 140))
        t.c[y0:y1, x0:x1] = t.c[y0:y1, x0:x1] * 0.7 + 50  # faded
        for i in range(14):  # the glass cracked across them
            x, y = int(24 + i * 1.5), int(14 + i * 2)
            area(t, x, y, x + 1, y + 1, (230, 230, 226))
        frame_col = (36, 30, 26)
    else:  # cross-stitch: HOME SWEET HOME
        x0, y0, x1, y1 = 10, 12, 54, 44
        area(t, x0, y0, x1, y1, (220, 210, 186))
        m = text_mask("HOME|SWEET|HOME", SANS, 7, x1 - x0, y1 - y0 - 6, seed=seed)
        paste(t, (m > 0.4).astype(np.float32), x0, y0 + 3, (150, 40, 50))
        for cx in (14, 50):
            disc(t, cx, 16, 2, (180, 50, 60))
            disc(t, cx, 40, 2, (60, 120, 70))
        frame_col = (120, 84, 50)
    area(t, x0 - 3, y0 - 3, x1 + 3, y0, frame_col, h=1.0, smooth=0.5)
    area(t, x0 - 3, y1, x1 + 3, y1 + 3, frame_col, h=1.0, smooth=0.5)
    area(t, x0 - 3, y0, x0, y1, frame_col, h=1.0, smooth=0.5)
    area(t, x1, y0, x1 + 3, y1, frame_col, h=1.0, smooth=0.5)
    edges(t, x0 - 3, y0 - 3, x1 + 3, y1 + 3, light=1.2, dark=0.6)
    return wear(t, 0.25, 0)


def mirror(seed, cracked=False):
    t = plain(seed, (150, 156, 160), 0.04, 0.96)
    t.f0[:] = 230
    yy, xx = np.mgrid[0:N, 0:N]
    t.c += (np.clip(1 - np.abs(xx - yy - 10) / 10, 0, 1) * 40)[..., None]
    for _ in range(6):  # smudges
        m = blob_mask(t.rng, t.rng.uniform(0, N), t.rng.uniform(0, N), 5)
        t.smooth -= m * 0.4
        t.c *= (1 - m * 0.12)[..., None]
    if cracked:
        for ang in t.rng.uniform(0, 6.28, 9):
            for i in range(2, 40):
                x, y = int(30 + np.cos(ang) * i), int(26 + np.sin(ang) * i)
                if 0 <= x < N and 0 <= y < N:
                    t.c[y, x] = (210, 214, 214)
                    t.h[y, x] -= 0.3
    t.grime(0.25, (60, 56, 50))
    return t


def calendar(seed):
    """July 1993, every day crossed off until the ninth."""
    t = plain(seed, (232, 228, 214), 0.03, 0.15)
    area(t, 20, 12, 44, 30, (96, 124, 150))  # the picture: a red barn under a blue sky
    area(t, 20, 24, 44, 30, (90, 116, 60))
    area(t, 26, 19, 36, 26, (150, 40, 34))
    m = text_mask("JULY 1993", SANS, 5, 24, 5, seed=seed)
    paste(t, m, 20, 31, (40, 40, 44))
    for r in range(5):
        for c in range(7):
            x, y = 21 + c * 3, 37 + r * 3
            area(t, x, y, x + 2, y + 2, (190, 186, 172))
            if r * 7 + c < 11:
                area(t, x, y, x + 2, y + 1, (180, 30, 30))
                area(t, x + 1, y + 1, x + 2, y + 2, (180, 30, 30))
    return wear(t, 0.25, 0)


def phone(seed):
    t = plain(seed, (200, 186, 150), 0.05, 0.45)
    disc(t, 32, 30, 6, (220, 210, 186), ring=2)
    for a in np.linspace(0, 5, 10):
        area(t, 32 + np.cos(a) * 4, 30 + np.sin(a) * 4, 33 + np.cos(a) * 4, 31 + np.sin(a) * 4, (40, 36, 30))
    return wear(t, 0.35, 0)


def curtains(seed, rgb, part="bottom"):
    """Floor-length drapes in two blocks, pulled to the sides and moth-eaten; open between them.
    The top block hangs from the rail; the bottom one is gathered at the tie-backs and flares out."""
    t = mat("Fabric028", rgb, 1.2, seed=seed, smooth=0.1)
    xx = np.arange(N)
    t.c *= (0.7 + 0.3 * np.abs(np.sin(xx * 0.55)))[None, :, None]  # folds
    t.alpha[:] = 0
    for y in range(N):
        if part == "top":
            w = 22 - int(6 * y / N)
        else:
            w = 14 + int(3 * abs(y - 8) / 8) if y < 16 else 17 + int(7 * (y - 16) / (N - 16))
        t.alpha[y, :w] = 255
        t.alpha[y, N - w:] = 255
    if part == "top":
        area(t, 0, 0, N, 2, (60, 56, 50), alpha=255)  # the rail
        for x in range(2, N, 6):
            area(t, x, 2, x + 1, 3, (90, 86, 76), alpha=255)  # the rings
    else:
        area(t, 0, 6, 15, 9, (190, 160, 80))
        area(t, N - 15, 6, N, 9, (190, 160, 80))
    for _ in range(5):
        x, y = t.rng.uniform(0, 14) if t.rng.random() < 0.5 else t.rng.uniform(50, 64), t.rng.uniform(4, N)
        disc(t, x, y, t.rng.uniform(0.6, 1.6), alpha=0)
    t.grime(0.35, (60, 50, 40))
    return t


CURTAINS = {"gold": (170, 140, 64), "rose": (150, 96, 96), "green": (88, 112, 80)}


def blinds(seed):
    t = plain(seed, (208, 200, 178), 0.04, 0.3)
    t.alpha[:] = 0
    rng = t.rng
    for y in range(2, N, 4):
        if rng.random() < 0.12:
            continue
        sag = int(rng.integers(0, 3)) if rng.random() < 0.25 else 0
        x0 = int(rng.integers(0, 20)) if rng.random() < 0.15 else 0
        for x in range(x0, N):
            yy = y + (sag if 20 < x < 44 else 0)
            if yy + 2 < N:
                t.alpha[yy:yy + 2, x] = 255
                t.c[yy, x] *= 1.08
                t.c[yy + 1, x] *= 0.8
    area(t, 0, 0, N, 2, (180, 172, 150), alpha=255)
    area(t, 12, 0, 13, N, (210, 206, 190), alpha=255)  # the cords
    area(t, 50, 0, 51, N, (210, 206, 190), alpha=255)
    t.grime(0.4, (70, 66, 56))
    return t


# ---------------------------------------------------------------- bedroom

QUILTS = {"quilt": None, "blue": (70, 90, 130), "rose": (176, 120, 124), "bloody": (196, 190, 176)}


def duvet(kind, seed):
    if kind == "quilt":
        t = mat("Fabric028", (170, 150, 120), 0.8, seed=seed, smooth=0.1)
        rng = t.rng
        cols = [(150, 60, 50), (60, 90, 130), (200, 180, 120), (90, 120, 80), (170, 120, 80), (120, 80, 110)]
        for gy in range(0, N, 8):
            for gx in range(0, N, 8):
                area(t, gx, gy, gx + 8, gy + 8, mul=np.array(cols[rng.integers(len(cols))]) / 150)
        for g in range(0, N, 8):  # stitching
            area(t, g, 0, g + 1, N, (230, 220, 200))
            area(t, 0, g, N, g + 1, (230, 220, 200))
    else:
        t = mat("Fabric030", QUILTS[kind], 1.0, seed=seed, smooth=0.1)
        for g in range(0, N, 16):
            area(t, g, 0, g + 1, N, mul=0.82, dh=-0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    t.h += 0.08 * np.sin(np.pi * (xx % 8) / 8) * np.sin(np.pi * (yy % 8) / 8)  # quilted puff
    t.c *= (0.92 + 0.12 * np.sin(np.pi * (xx % 8) / 8) * np.sin(np.pi * (yy % 8) / 8))[..., None]
    t.c *= (0.9 + 0.12 * P.fbm(t.rng, ((4, 1.0), (8, 0.5))))[..., None]  # rumpled
    if kind == "bloody":
        blood(t, 30, 20, 16, 0.95)
        blood(t, 44, 44, 7, 0.8)
    return wear(t, 0.3, 0, specks=0.02)


def mattress(seed, stained=False):
    t = plain(seed, (214, 206, 186), 0.04, 0.1)
    for x in range(0, N, 6):
        area(t, x, 0, x + 2, N, (150, 160, 176))  # ticking stripes
    if stained:
        for _ in range(3):
            stain(t, t.rng.uniform(0, N), t.rng.uniform(0, N), t.rng.uniform(6, 12), (150, 120, 60), 0.45)
        blood(t, 30, 34, 12, 0.8)
    return wear(t, 0.35, 0)


def pillow(seed):
    t = plain(seed, (222, 218, 204), 0.04, 0.1)
    t.grime(0.3, (150, 130, 90))
    stain(t, 30, 30, 8, (170, 150, 100), 0.3)
    return t


def dresser_front(seed):
    """Three rows of two drawers with brass bail pulls, rows 16-64 (the front stands 0-12)."""
    t = wood(seed, "walnut", rot=True)
    for r, (y0, y1) in enumerate(((17, 31), (32, 47), (48, 63))):
        for x0, x1 in ((1, 32), (32, 63)):
            gap(t, x0, y0, x1, y1)
            edges(t, x0 + 1, y0 + 1, x1 - 1, y1 - 1, light=1.12, dark=0.7)
            cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
            area(t, cx - 4, cy - 1, cx + 4, cy, BRASS, h=1.0, smooth=0.7, f0=230)
            area(t, cx - 4, cy, cx - 3, cy + 2, BRASS, h=1.0)
            area(t, cx + 3, cy, cx + 4, cy + 2, BRASS, h=1.0)
            area(t, cx - 3, cy + 2, cx + 3, cy + 3, BRASS, h=1.0)
            fingers(t, cx, cy, 6, 0.2)
    if seed % 2:
        area(t, 34, 33, 62, 46, (18, 14, 10))  # a drawer left hanging open
    return wear(t, 0.3, 12)


def wardrobe_front(seed, top):
    t = wood(seed, "walnut", rot=True)
    if top:
        for x0, x1 in ((1, 32), (32, 63)):
            gap(t, x0, 4, x1, N + 1)
            edges(t, x0 + 1, 5, x1 - 1, N, light=1.12, dark=0.7)
            sunk(t, x0 + 5, 12, x1 - 5, N)
        area(t, 0, 0, N, 4, mul=0.8)
    else:
        for x0, x1 in ((1, 32), (32, 63)):
            gap(t, x0, -1, x1, 48)
            edges(t, x0 + 1, 0, x1 - 1, 47, light=1.12, dark=0.7)
            sunk(t, x0 + 5, 0, x1 - 5, 40)
        knob(t, 29, 6)
        knob(t, 35, 6)
        fingers(t, 32, 6, 8, 0.25)
        gap(t, 1, 49, 63, 62)
        area(t, 30, 54, 34, 56, BRASS, h=1.0, smooth=0.7, f0=230)
        area(t, 0, 62, N, N, mul=0.5)
    return wear(t, 0.3, 10)


def desk_front(seed):
    """The pedestal's three drawers, columns 0-24, rows 16-64."""
    t = wood(seed, "oak", rot=True)
    for y0, y1 in ((17, 31), (32, 47), (48, 63)):
        gap(t, 1, y0, 23, y1)
        edges(t, 2, y0 + 1, 22, y1 - 1, light=1.12, dark=0.7)
        bar(t, 8, (y0 + y1) // 2 - 1, 16, (y0 + y1) // 2, BRASS)
    return wear(t, 0.3, 10)


def nightstand_front(seed):
    """A drawer over an open shelf with a paperback in it; columns 8-56, rows 24-64."""
    t = wood(seed, "walnut", rot=True)
    gap(t, 9, 25, 55, 38)
    edges(t, 10, 26, 54, 37, light=1.12, dark=0.7)
    knob(t, 32, 31)
    area(t, 10, 40, 54, 62, (22, 16, 12))
    area(t, 14, 52, 30, 62, (160, 50, 40), h=0.9)
    area(t, 30, 58, 50, 62, (50, 70, 100), h=0.9)
    return wear(t, 0.3, 8)


def pc_case(seed):
    t = plain(seed, (196, 186, 158), 0.05, 0.4)  # yellowed beige plastic
    for y in range(4, 20, 2):
        area(t, 44, y, 58, y + 1, mul=0.6)
    return wear(t, 0.32, 0)


def pc_screen(seed):
    t = tv_screen(seed)
    return t


def keyboard(seed):
    t = plain(seed, (200, 192, 168), 0.05, 0.4)
    for r in range(5):
        for c in range(14):
            x, y = 13 + c * 3, 8 + r * 2
            area(t, x, y, x + 2, y + 1, (226, 220, 200), h=0.95)
    t.grime(0.45, (90, 80, 60))
    return t


def toy_chest(seed):
    t = mat("PaintedWood001", (110, 150, 180), 0.8, seed=seed, smooth=0.3)
    for x, col, ch in ((10, (200, 50, 50), "A"), (28, (230, 190, 50), "B"), (46, (60, 150, 80), "C")):
        area(t, x, 34, x + 10, 44, col)
        m = text_mask(ch, SANS, 8, 10, 10, seed=seed)
        paste(t, m, x, 34, (240, 236, 220))
    return wear(t, 0.35, 12)


def clothes_heap(seed):
    t = mat("Fabric030", (120, 110, 100), 0.6, seed=seed, smooth=0.1)
    rng = t.rng
    cols = [(60, 70, 110), (150, 50, 50), (210, 206, 196), (60, 60, 60), (110, 130, 90), (180, 150, 100)]
    for _ in range(14):
        m = blob_mask(rng, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(6, 13), rough=0.8)
        t.mix(m, cols[rng.integers(len(cols))], 0.85)
    t.h += P.fbm(rng) * 0.2
    return wear(t, 0.3, 0)


# ---------------------------------------------------------------- bathroom

def ceramic(seed, rgb=(222, 222, 214)):
    t = plain(seed, rgb, 0.025, 0.82)
    t.f0[:] = 40
    return wear(t, 0.22, 0, (110, 100, 70))


def water_dirty(seed):
    t = plain(seed, (96, 86, 58), 0.08, 0.95)
    disc(t, 32, 32, 30, (150, 140, 110), ring=4, soft=3)
    t.grime(0.4, (60, 50, 30))
    return t


def tub_bottom(seed):
    t = ceramic(seed)
    t.mix(np.clip(P.fbm(t.rng) + 0.3, 0, 1) * 0.5, (150, 130, 90), 1.0)  # the ring and scum
    drips(t, 6, 10, 0, 3, (10, 30), (120, 70, 40), 2)  # rust from the tap
    return t


def vanity_top(seed):
    """Cultured marble: cream, faint veins."""
    t = mat("marble_01", (214, 204, 180), 0.6, seed=seed, smooth=0.7)
    t.f0[:] = 40
    return wear(t, 0.28, 0)


def medicine_mirror(seed):
    t = mirror(seed, cracked=seed % 2 == 1)
    area(t, 12, 8, 52, 10, CHROME, h=1.0)
    area(t, 12, 54, 52, 56, CHROME, h=1.0)
    area(t, 12, 8, 14, 56, CHROME, h=1.0)
    area(t, 50, 8, 52, 56, CHROME, h=1.0)
    return t


def towel(seed):
    t = mat("Fabric030", (150, 170, 176), 0.9, seed=seed, smooth=0.05)
    area(t, 12, 20, 52, 22, (120, 140, 150))
    area(t, 10, 14, 54, 16, CHROME, h=1.0, smooth=0.8, f0=230)  # the rail
    t.grime(0.35, (120, 100, 70))
    return t


def shower_curtain(seed):
    t = plain(seed, (190, 208, 214), 0.05, 0.5)
    t.alpha[:] = 255
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.82 + 0.18 * np.abs(np.sin(xx * 0.4)))[..., None]
    for gx in range(4, N, 10):
        for gy in range(10, N, 12):
            disc(t, gx + (gy // 12 % 2) * 5, gy, 2, (90, 140, 170))  # little fish? dots
    mold = np.clip((yy - 40) / 24, 0, 1) * np.clip(P.fbm(t.rng) + 0.6, 0, 1)
    t.mix(mold, (60, 66, 46), 0.8)
    for x in range(2, N, 8):
        area(t, x, 0, x + 3, 3, CHROME)
    area(t, 0, 0, N, 1, alpha=0)
    for _ in range(3):
        disc(t, t.rng.uniform(0, N), t.rng.uniform(10, 60), t.rng.uniform(1, 3), alpha=0)  # torn
    return t


# ---------------------------------------------------------------- garage and utility

def heater(seed, top=False):
    t = plain(seed, (200, 196, 180), 0.05, 0.4)
    t.f0[:] = 230
    if top:
        return wear(t, 0.35, 0)
    area(t, 22, 20, 42, 34, (220, 200, 80))
    for y in range(23, 33, 3):
        area(t, 24, y, 24 + t.rng.integers(8, 16), y + 1, (40, 40, 40))
    m = np.clip((np.arange(N)[:, None] - 44) / 20, 0, 1) * np.clip(P.fbm(t.rng) + 0.5, 0, 1)
    t.mix(m, (110, 64, 34), 0.9)
    drips(t, 0, N, 40, 4, (6, 20), (120, 70, 40), 1)
    return wear(t, 0.35, 0)


def copper(seed):
    t = plain(seed, (160, 96, 60), 0.08, 0.5)
    t.f0[:] = 234
    m = np.clip(P.fbm(t.rng) * 2 - 0.2, 0, 1)
    t.mix(m, (80, 130, 110), 0.6)  # verdigris
    return t


def bench_top(seed):
    t = mat("old_planks_02", (130, 104, 76), 1.0, seed=seed, smooth=0.2)
    for _ in range(4):
        stain(t, t.rng.uniform(0, N), t.rng.uniform(0, N), t.rng.uniform(4, 9), (30, 26, 20), 0.6, tide=False)
    return wear(t, 0.4, 20)


def red_steel(seed):
    t = mat("PaintedMetal004", (150, 34, 30), 1.0, seed=seed, smooth=0.45)
    t.f0[:] = 230
    return wear(t, 0.3, 14)


def toolchest_front(seed):
    t = red_steel(seed)
    for y0, y1 in ((10, 18), (18, 26), (26, 34), (34, 44), (44, 56)):
        gap(t, 5, y0, 59, y1, 0.4)
        bar(t, 12, y0 + 2, 52, y0 + 3, CHROME)
    area(t, 4, 56, 60, 60, (40, 40, 40))
    return t


def freezer_front(seed):
    t = enamel(seed)
    area(t, 0, 8, N, 10, mul=0.5)
    bar(t, 22, 12, 42, 14, CHROME)
    area(t, 6, 16, 18, 20, (40, 80, 150))
    drips(t, 0, N, 50, 3, (4, 14), (110, 70, 40), 1)
    return wear(t, 0.38, 10)


def cardboard(seed):
    t = mat("Cardboard001", (164, 128, 86), 1.0, seed=seed, smooth=0.1)
    area(t, 28, 0, 36, N, (190, 170, 120), smooth=0.5)  # tape
    for word, y in (("KITCHEN", 20), ("XMAS", 44)):
        m = text_mask(word, SCRAWL, 6, 26, 8, angle=-4, jitter=0.4, seed=seed + y)
        paste(t, m, 2 if y == 20 else 38, y, (30, 28, 30))
    stain(t, 50, 56, 8, (90, 70, 40), 0.4)
    return wear(t, 0.3, 0)


def gun_glass(seed, top):
    """A gun cabinet's glass doors with two rifles and a shotgun stood inside."""
    t = wood(seed, "dark", rot=True)
    gx0, gx1 = 6, 58
    gy0, gy1 = (8, N) if top else (0, 44)
    area(t, gx0, gy0, gx1, gy1, (110, 22, 26), smooth=0.2)  # red felt behind the guns
    t.c[gy0:gy1, gx0:gx1] *= (0.8 + 0.2 * P.fbm(t.rng)[gy0:gy1, gx0:gx1, None])
    for x in (14, 26, 38, 50):
        if top:
            area(t, x, 10, x + 2, N, (52, 52, 56), f0=230, smooth=0.6)  # barrels
            area(t, x - 1, 44, x + 3, N, (140, 90, 50))  # fore-ends
            area(t, x + 2, 26, x + 4, 34, (40, 40, 44))  # a scope
        else:
            area(t, x - 2, 6, x + 4, 40, (150, 96, 52))  # stocks
            area(t, x - 2, 6, x + 4, 8, (40, 30, 24))  # butt pads
            area(t, x, 0, x + 3, 6, (120, 80, 44))
    if not top:
        area(t, gx0, 0, gx1, 2, (52, 52, 56))
    area(t, 31, gy0, 33, gy1, (60, 40, 28))
    yy, xx = np.mgrid[0:N, 0:N]
    glare = np.clip(1 - np.abs(xx + yy * 0.6 - 40) / 6, 0, 1) * ((xx >= gx0) & (xx < gx1) & (yy >= gy0) & (yy < gy1))
    t.c += (glare * 50)[..., None]
    t.smooth = np.where((xx >= gx0) & (xx < gx1) & (yy >= gy0) & (yy < gy1), 0.9, t.smooth)
    if not top:
        area(t, 28, 50, 36, 54, BRASS, h=1.0, smooth=0.7, f0=230)
        gap(t, 6, 47, 58, 60)
    return wear(t, 0.3, 6)


# ---------------------------------------------------------------- what the end left behind

def sheet_blood(seed):
    t = plain(seed, (210, 206, 194), 0.04, 0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.88 + 0.14 * np.sin(xx * 0.3 + np.sin(yy * 0.15) * 2))[..., None]
    blood(t, 30, 44, 14, 0.9, glossy=False)
    blood(t, 22, 14, 6, 0.7, glossy=False)
    t.grime(0.3, (120, 110, 90))
    return t


def blood_pool(seed):
    t = plain(seed, (70, 10, 8), 0.1, 0.8)
    t.alpha[:] = 0
    m = blob_mask(t.rng, 32, 34, 22, rough=0.7, wrap=False)
    for _ in range(6):
        m = np.maximum(m, blob_mask(t.rng, t.rng.uniform(8, 56), t.rng.uniform(8, 56), t.rng.uniform(2, 6), wrap=False))
    t.alpha = np.where(m > 0.15, 255, 0).astype(np.float32)
    heart = np.clip(m * 1.4 - 0.4, 0, 1)
    t.c = t.c * (1 - heart[..., None] * 0.5)
    t.mix(1 - m, (110, 40, 26), 0.6)
    t.smooth = 0.3 + heart * 0.6
    return t


def blood_drag(seed):
    """Something dragged across the floor, north to south: a broad smear streaked by fingers and
    cloth, breaking up where the blood ran low. Tiles along z."""
    t = plain(seed, (90, 18, 12), 0.1, 0.6)
    rng = t.rng
    xx = np.arange(N)[None, :]
    streaks = np.zeros(N, np.float32)
    for _ in range(9):
        streaks += np.clip(1 - np.abs(np.arange(N) - rng.uniform(16, 48)) / rng.uniform(1, 3), 0, 1) * rng.uniform(0.3, 0.8)
    body = np.clip(1 - np.abs(np.arange(N) - 32) / 15, 0, 1)
    a = np.clip(body * 0.75 + streaks * 0.5, 0, 1)[None, :] * np.ones((N, 1))
    breakup = P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.3)))
    a = a * np.clip(breakup * 1.5 + 0.9, 0, 1)
    t.alpha = np.where(a > 0.3, 255, 0).astype(np.float32)
    t.c *= (0.7 + 0.5 * a)[..., None]
    t.smooth = 0.3 + a * 0.4
    return t


def blood_hand(seed):
    """Bloody handprints and a long smear, on a wall (decal on the north side)."""
    t = plain(seed, (84, 14, 10), 0.08, 0.6)
    t.alpha[:] = 0
    rng = t.rng
    for hx, hy in ((14, 20), (40, 30), (28, 46)):
        m = np.zeros((N, N), np.float32)
        yy, xx = np.mgrid[0:N, 0:N]
        m = np.maximum(m, np.clip(1 - np.hypot((xx - hx) / 5, (yy - hy) / 6), 0, 1))
        for i, dx in enumerate((-4, -1.5, 1.5, 4)):
            fx, fy = hx + dx, hy - 6 - (1 if i in (1, 2) else 0)
            m = np.maximum(m, np.clip(1 - np.hypot((xx - fx) / 1.3, (yy - fy) / 4.5), 0, 1))
        m = np.maximum(m, np.clip(1 - np.hypot((xx - hx - 6) / 1.5, (yy - hy + 1) / 3), 0, 1))
        smear = np.clip(1 - np.abs(xx - hx) / 5, 0, 1) * ((yy > hy) & (yy < hy + rng.uniform(6, 18)))
        m = np.maximum(m, smear * 0.7)
        t.alpha = np.maximum(t.alpha, (m > 0.2) * 255.0)
    drips(t, 8, 56, 26, 8, (4, 20), (84, 14, 10))
    return t


def floor_stain(seed, col, r=18):
    """A dried spill or water mark, as a decal on the floor."""
    t = plain(seed, col, 0.1, 0.25)
    m = blob_mask(t.rng, 32, 32, r, rough=0.7, wrap=False)
    t.alpha = np.where(m > 0.1, np.clip(m * 400, 0, 200), 0).astype(np.float32)
    t.alpha = np.where(t.alpha > 60, 255, 0).astype(np.float32)
    edge = np.clip(m * (1 - m) * 4, 0, 1)
    t.c *= (1 - edge * 0.35)[..., None]
    return t


def candles(seed):
    t = plain(seed, (222, 212, 180), 0.05, 0.4)
    drips(t, 0, N, 0, 10, (10, 40), (236, 228, 200), 2)
    area(t, 0, 0, N, 4, (40, 30, 20))  # burnt wicks seen from above near the edge
    return t


NOTES = {
    "stadium": ("GONE TO|STADIUM|- J", (30, 30, 34)),
    "basement": ("DONT GO|IN THE|BASEMENT", (150, 20, 20)),
    "bitten": ("BIT.|LAYING|DOWN.|SORRY", (30, 30, 34)),
    "mom": ("SHES NOT|MOM|ANYMORE", (150, 20, 20)),
}


def note(kind, seed):
    """A sheet of paper taped to the wall (the decal's south face, columns 12-52, rows 8-56)."""
    text, ink = NOTES[kind]
    t = plain(seed, (40, 36, 30), 0.0, 0.1)
    t.alpha[:] = 0
    area(t, 12, 8, 52, 56, (226, 222, 204), alpha=255)
    for y in range(14, 56, 6):
        area(t, 13, y, 51, y + 1, (170, 190, 210))  # ruled lines
    area(t, 26, 6, 38, 11, (200, 196, 160), alpha=255, smooth=0.6)  # tape
    m = text_mask(text, SCRAWL, 8, 38, 44, angle=3, jitter=0.6, seed=seed, spacing=1.1)
    paste(t, m, 13, 11, ink)
    stain(t, 40, 46, 5, (120, 90, 50), 0.3)
    t.grime(0.2, (120, 100, 70))
    return t


SPRAY = {"dead": ("DEAD", "INSIDE", (176, 26, 22)), "help": ("HELP", "US", (190, 170, 40))}


def spray(kind, part, seed):
    """Spray paint across two blocks of wall: big shaky letters, overspray, runs."""
    a, b, col = SPRAY[kind]
    word = (a, b)[part]
    t = plain(seed, col, 0.08, 0.3)
    t.alpha[:] = 0
    m = text_mask(word, SANS, 22 if len(word) <= 4 else 15, N, 40, angle=-3 + 6 * part, jitter=0.5, seed=seed)
    soft = P.blur(m, 1)
    t.alpha[12:52] = np.where(m > 0.35, 255, np.where(soft > 0.12, 140, 0))
    t.c[12:52] *= (1 - (1 - m) * 0.4)[..., None]
    rng = t.rng
    for _ in range(9):  # runs
        ys, xs = np.nonzero(m > 0.6)
        if len(xs):
            i = rng.integers(len(xs))
            x, y = xs[i], ys[i] + 12
            ln = int(rng.uniform(4, 14))
            t.alpha[y:y + ln, x] = 255
    return t


def x_code(seed):
    """The search mark crews left on doors: an X, the date, the team, how many dead."""
    t = plain(seed, (210, 110, 30), 0.08, 0.3)
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    for s in (1, -1):
        d = np.abs((xx - 32) - s * (yy - 32))
        t.alpha = np.maximum(t.alpha, ((d < 2.2) & (np.abs(xx - 32) < 26)) * 255.0)
    for text, (x, y) in (("7/14", (18, 4)), ("NG3", (40, 26)), ("2", (28, 48)), ("0", (4, 26))):
        m = text_mask(text, SANS, 7, 22 if len(text) > 1 else 10, 10, jitter=0.3, seed=seed + x)
        h, w = m.shape
        t.alpha[y:y + h, x:x + w] = np.maximum(t.alpha[y:y + h, x:x + w], (m > 0.35) * 255)
    return t


def pills(seed):
    t = plain(seed, (230, 226, 214), 0.03, 0.5)
    t.alpha[:] = 0
    area(t, 20, 24, 34, 32, (200, 110, 30), alpha=255, smooth=0.7)  # the bottle on its side
    area(t, 34, 25, 37, 31, (236, 236, 230), alpha=255)
    for _ in range(16):
        x, y = t.rng.uniform(10, 54), t.rng.uniform(14, 50)
        area(t, x, y, x + 2, y + 1, (238, 236, 228), alpha=255)
    return t


# ---------------------------------------------------------------- floors, ceilings, walls

CARPETS = {"beige": ("Carpet001", (170, 150, 120)), "blue": ("Carpet012", (70, 84, 110)),
           "brown": ("dirty_carpet", (96, 74, 52)), "green": ("Carpet001", (90, 104, 76)),
           "rose": ("Carpet001", (150, 112, 108))}


def carpet(kind, seed):
    material, rgb = CARPETS[kind]
    t = mat(material, rgb, 0.9, seed=seed, smooth=0.04)
    rng = t.rng
    t.speckle(0.006, (60, 54, 44), 0.4)
    return wear(t, 0.2, 0, specks=0.002)


def floor_wood(seed, kind):
    if kind == "oak":
        # strip boards along u, 8 texels wide, each its own shade, end joints staggered
        t = mat("laminate_floor", (150, 112, 70), 1.0, seed=seed, smooth=0.4)
        rng = t.rng
        for y0 in range(0, N, 8):
            cut = int(rng.integers(8, N - 8))
            for a, b in ((0, cut), (cut, N)):
                area(t, a, y0, b, y0 + 8, mul=float(rng.uniform(0.86, 1.12)))
            area(t, 0, y0, N, y0 + 1, mul=0.55, dh=-0.2)
            area(t, cut, y0, cut + 1, y0 + 8, mul=0.6, dh=-0.2)
    else:
        t = mat("WoodFloor026", (120, 76, 46), 1.0, seed=seed, smooth=0.35)
    return wear(t, 0.25, 10, specks=0.003)


def lino(seed, kind):
    if kind == "seventies":
        t = mat("old_linoleum_flooring_01", (176, 130, 70), 1.0, seed=seed, smooth=0.45)
    else:
        t = plain(seed, (190, 180, 160), 0.04, 0.45)
        for gx in range(0, N, 16):
            for gy in range(0, N, 16):
                if (gx + gy) // 16 % 2:
                    area(t, gx, gy, gx + 16, gy + 16, (150, 60, 50))
        t.speckle(0.08, (90, 80, 70), 0.3)
    return wear(t, 0.28, 8)


def tile_floor(seed):
    """Small white hexagon tiles with dark grout gone grey."""
    t = plain(seed, (224, 222, 214), 0.03, 0.7)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    s = 4.0
    q = (xx * np.sqrt(3) / 3 - yy / 3) / s
    r = (yy * 2 / 3) / s
    cx, cz = q, r
    cy = -cx - cz
    rx, ry, rz = np.round(cx), np.round(cy), np.round(cz)
    dx, dy, dz = np.abs(rx - cx), np.abs(ry - cy), np.abs(rz - cz)
    edge = np.maximum(np.maximum(dx, dy), dz) > 0.40
    t.mix(edge.astype(np.float32), (110, 106, 96), 0.8)
    t.h -= edge * 0.15
    t.grime(0.3, (80, 76, 60))
    return t


def ceiling(seed):
    """Popcorn ceiling, the underside of every upper floor: yellowed, water-stained."""
    t = plain(seed, (218, 214, 202), 0.03, 0.05)
    t.speckle(0.25, (190, 186, 176), 0.5, 0.05)
    t.h += P.fbm(t.rng, ((16, 1.0), (32, 1.0))) * 0.08
    return t


def floor_edge(seed):
    t = plain(seed, (196, 190, 176), 0.04, 0.2)
    area(t, 0, 0, N, 6, (120, 90, 60))
    return wear(t, 0.3, 0)


PAPERS = {"stripe": ((150, 160, 130), (120, 132, 104)), "floral": ((206, 190, 160), (160, 90, 90)),
          "damask": ((150, 128, 84), (178, 154, 100)), "cream": ((206, 198, 172), None),
          "sage": ((150, 162, 136), None), "blue": ((138, 154, 170), None)}


def wallpaper(kind, seed, worn=False):
    ground, ink = PAPERS[kind]
    t = mat("beige_wall_001", ground, 0.3 if ink is None else 0.5, seed=seed, smooth=0.12)
    yy, xx = np.mgrid[0:N, 0:N]
    if kind == "cream":
        # a faint pinstripe pair, so it reads as paper rather than bare plaster
        t.mix((((xx % 12) == 3) | ((xx % 12) == 5)).astype(np.float32), (180, 166, 134), 0.45)
    elif kind == "sage":
        # a small trellis of diamonds
        d = (np.abs((xx % 16) - 8) + np.abs((yy % 16) - 8)) == 6
        t.mix(d.astype(np.float32), (124, 138, 110), 0.55)
    elif kind == "blue":
        dots = (((xx + 4 * ((yy // 8) % 2)) % 8) == 2) & ((yy % 8) == 3)
        t.mix(dots.astype(np.float32), (214, 214, 200), 0.7)
    elif kind == "stripe":
        t.mix(((xx % 16) < 7).astype(np.float32), ink, 0.8)
        t.mix(((xx % 16) == 10).astype(np.float32), (190, 176, 120), 0.7)
    elif kind == "floral":
        for gy in range(0, N, 16):
            for gx in range(0, N, 16):
                cx, cy = gx + (8 if (gy // 16) % 2 else 0), gy + 8
                for dx, dy in ((0, -3), (3, 0), (0, 3), (-3, 0)):
                    disc(t, (cx + dx) % N, cy + dy, 1.6, ink)
                disc(t, cx % N, cy, 1.2, (200, 170, 80))
                area(t, (cx + 3) % N, cy + 3, (cx + 3) % N + 1, cy + 7, (100, 120, 80))
    elif kind == "damask":
        u = np.abs(((xx + 8) % 32) - 16) / 16
        v = ((yy + 4) % 32) / 32
        motif = (np.abs(u - 0.25 * np.sin(v * np.pi * 2) ** 2) < 0.12) & (v > 0.1) & (v < 0.9)
        motif |= (np.hypot(u * 16, (v - 0.5) * 32) < 3)
        t.mix(motif.astype(np.float32), ink, 0.9)
        t.smooth = np.where(motif, 0.35, t.smooth)
    if worn:
        rng = t.rng
        # water down from the ceiling, the paper peeling back to plaster, mould low down
        for _ in range(2):
            x = rng.uniform(0, N)
            m = np.clip(1 - np.abs(xx - x) / rng.uniform(6, 12), 0, 1) * np.clip(1 - yy / rng.uniform(30, 64), 0, 1)
            t.mix(m * 0.6, (120, 96, 56), 1.0)
        peel = np.clip(P.fbm(rng) * 3 - 1.2, 0, 1)
        t.mix(peel, (196, 190, 172), 1.0)
        t.h -= peel * 0.1
        edge = np.clip(peel * (1 - peel) * 4, 0, 1)
        t.mix(edge, (90, 80, 64), 0.8)
        mould = np.clip((yy - 36) / 28, 0, 1) * np.clip(P.fbm(rng, ((16, 1.0), (32, 0.6))) * 2 + 0.1, 0, 1)
        t.mix(mould, (40, 46, 34), 0.75)
    return wear(t, 0.25 if worn else 0.12, 0)


TILES = {"pink": (214, 170, 168), "teal": (130, 178, 166), "white": (222, 220, 210)}


def wall_tile(kind, seed, worn=False):
    t = plain(seed, TILES[kind], 0.03, 0.78)
    t.f0[:] = 40
    rng = t.rng
    for gy in range(0, N, 8):
        for gx in range(0, N, 8):
            area(t, gx + 1, gy + 1, gx + 8, gy + 8, mul=rng.uniform(0.95, 1.05))
            area(t, gx + 1, gy + 1, gx + 8, gy + 2, mul=1.08)
    grout = (np.arange(N) % 8 == 0)
    t.mix(np.maximum(grout[None, :], grout[:, None]).astype(np.float32), (176, 170, 156), 1.0)
    t.h -= np.maximum(grout[None, :], grout[:, None]) * 0.15
    if worn:
        for _ in range(3):  # a tile gone, the adhesive behind it
            gx, gy = rng.integers(0, 8) * 8, rng.integers(0, 8) * 8
            area(t, gx, gy, gx + 8, gy + 8, (120, 116, 104), h=0.5, smooth=0.1)
        t.crack(3, 0.2, (60, 56, 50))
        mould = np.maximum(grout[None, :], grout[:, None]) * np.clip(P.fbm(rng) + 0.3, 0, 1)
        t.mix(mould, (34, 40, 30), 0.8)
    return wear(t, 0.22, 0)


def panelling(seed, worn=False):
    """Seventies wood-look panelling, grooved every quarter block."""
    t = mat("Wood051", (96, 66, 42), 1.1, rot=True, seed=seed, smooth=0.4)
    for x in range(0, N, 16):
        area(t, x, 0, x + 1, N, mul=0.35, dh=-0.25)
    if worn:
        stain(t, 30, 10, 18, (60, 50, 30), 0.4)
        m = np.clip((np.arange(N)[:, None] - 44) / 20, 0, 1) * np.clip(P.fbm(t.rng) + 0.4, 0, 1)
        t.mix(m, (60, 56, 40), 0.6)
    return wear(t, 0.25, 8)


def wainscot(seed):
    """Lower wall: painted board panelling under a chair rail."""
    t = plain(seed, (200, 194, 176), 0.04, 0.4)
    area(t, 0, 0, N, 5, (210, 204, 186), h=1.0)
    edges(t, 0, 0, N, 5)
    for x0 in range(0, N, 32):
        sunk(t, x0 + 3, 9, x0 + 29, 56)
    area(t, 0, 58, N, N, (180, 174, 156), h=0.9)
    edges(t, 0, 58, N, N)
    return wear(t, 0.35, 10)


def door_leaf(seed, kind, top):
    """Interior six-panel doors in white paint or stained oak (vanilla's door model)."""
    t = plain(seed, (214, 210, 196), 0.03, 0.4) if kind == "white" else wood(seed, "oak", rot=True)
    if top:
        sunk(t, 9, 6, 29, 22)
        sunk(t, 35, 6, 55, 22)
        sunk(t, 9, 28, 29, N)
        sunk(t, 35, 28, 55, N)
    else:
        sunk(t, 9, 0, 29, 26)
        sunk(t, 35, 0, 55, 26)
        sunk(t, 9, 34, 29, 58)
        sunk(t, 35, 34, 55, 58)
        disc(t, 52, 4, 2.5, BRASS, h=1.0, smooth=0.7, f0=230)
        fingers(t, 52, 4, 8, 0.3)
    return wear(t, 0.3, 8)


def stair_tread(seed, kind):
    if kind == "carpet":
        t = wood(seed, "dark")
        rug_strip = carpet("rose", seed + 1)
        t.c[:, 10:54] = rug_strip.c[:, 10:54]
        t.smooth[:, 10:54] = 0.04
        area(t, 10, 0, 54, N, mul=1.0)
        for x in (10, 53):
            area(t, x, 0, x + 1, N, BRASS)
        return t
    return wear(wood(seed, "oak"), 0.35, 12)


# ---------------------------------------------------------------- the lived-in layer
# hearths, the piano, plants, the coat rack by the door, and what sits on every surface

def fire_brick(seed, soot=False):
    """Old red brick round a fireplace, blacked above the opening by years of fires."""
    t = mat("Bricks075B", (150, 78, 58), 1.1, seed=seed, span=0.5, smooth=0.15)
    if soot:
        yy, xx = np.mgrid[0:N, 0:N]
        m = np.clip(1 - np.abs(xx - 32) / 26, 0, 1) * np.clip((yy - 4) / 30, 0, 1) * np.clip((60 - yy) / 12, 0, 1)
        t.mix(m * 0.75, (28, 24, 22), 1.0)
    return wear(t, 0.3, 0, specks=0.004)


def soot(seed):
    t = plain(seed, (26, 24, 22), 0.15, 0.05)
    t.speckle(0.08, (96, 92, 86), 0.6)
    t.f0[:] = 20
    return t


def hearth(seed):
    """Slate tiles under the fire."""
    t = mat("Tiles008", (78, 80, 84), 1.0, seed=seed, smooth=0.4)
    for x in range(0, N, 16):
        area(t, x, 0, x + 1, N, mul=0.55, dh=-0.2)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.55, dh=-0.2)
    t.speckle(0.05, (140, 136, 128), 0.5)
    return wear(t, 0.35, 0)


def charred(seed):
    t = mat("Bark001", (58, 44, 34), 1.3, seed=seed, smooth=0.1)
    m = np.clip(P.fbm(t.rng) * 2.5 - 0.3, 0, 1)
    t.mix(m, (22, 20, 20), 0.9)
    t.speckle(0.04, (150, 144, 136), 0.7)
    return t


def cast_iron(seed):
    t = plain(seed, (40, 40, 42), 0.08, 0.35)
    t.f0[:] = 120
    m = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6))) * 2 - 0.9, 0, 1)
    t.mix(m, (96, 52, 30), 0.6)  # rust where the kettle boiled over
    return wear(t, 0.2, 4)


def stove_door(seed):
    """Front plate rows 26-48: a mica window with dead embers behind it, a cast border, the latch."""
    t = cast_iron(seed)
    area(t, 20, 28, 44, 44, (30, 20, 14), smooth=0.8, f0=40)
    for _ in range(6):
        disc(t, t.rng.uniform(22, 42), t.rng.uniform(38, 43), t.rng.uniform(1, 2), (120, 60, 30))
    edges(t, 18, 26, 46, 46, light=1.3, dark=0.5)
    bar(t, 12, 34, 16, 37, (90, 88, 86))
    return t


def piano_wood(seed):
    t = wood(seed, "dark")
    tone(t, (48, 30, 22), 0.6)
    t.smooth[:] = 0.7
    t.f0[:] = 40
    return wear(t, 0.25, 12)


def piano_keys(seed):
    """Up face of the keyboard (rows 16-36): white keys, the black keys at the back (rows 26-31);
    the front face (rows 32-38) shows the white keys' ends."""
    t = plain(seed, (226, 218, 196), 0.03, 0.6)
    for x in range(0, N, 3):
        area(t, x, 0, x + 1, N, mul=0.7, dh=-0.1)
    pattern = (1, 1, 0, 1, 1, 1, 0)  # black keys between C-D, D-E, F-G, G-A, A-B
    for i in range(N // 3):
        if pattern[i % 7]:
            area(t, i * 3 + 2, 24, i * 3 + 4, 31, (24, 22, 20), h=1.0, smooth=0.75)
    # a few keys yellowed, one missing its ivory
    for _ in range(4):
        k = int(t.rng.integers(0, N // 3))
        area(t, k * 3 + 1, 31, k * 3 + 3, 40, mul=(0.92, 0.86, 0.7))
    area(t, 30, 31, 32, 40, (90, 70, 50))
    return wear(t, 0.2, 0)


def terracotta(seed):
    t = plain(seed, (168, 92, 60), 0.08, 0.2)
    area(t, 0, 0, N, 8, mul=1.12)  # the rim
    area(t, 0, 8, N, 9, mul=0.6)
    m = np.clip(P.fbm(t.rng) * 2 - 0.4, 0, 1) * np.clip((np.mgrid[0:N, 0:N][0] - 30) / 30, 0, 1)
    t.mix(m, (200, 196, 180), 0.5)  # white salt bloom from overwatering, long ago
    return wear(t, 0.3, 0)


def foliage(seed, dead=False):
    """A mass of leaves: overlapping leaf shapes in a few greens over deep shadow, or the
    brown curl of a plant nobody watered."""
    t = plain(seed, (24, 40, 20) if not dead else (52, 40, 26), 0.1, 0.25)
    rng = t.rng
    greens = [(58, 104, 46), (76, 124, 54), (44, 86, 40), (96, 132, 60)]
    browns = [(118, 92, 52), (96, 70, 40), (140, 116, 70), (78, 60, 36)]
    yy, xx = np.mgrid[0:N, 0:N]
    for _ in range(140):
        cx, cy = rng.uniform(0, N, 2)
        a = rng.uniform(0, np.pi)
        ln, wd = rng.uniform(3, 6), rng.uniform(1.2, 2.2)
        dx = (xx - cx + N / 2) % N - N / 2
        dy = (yy - cy + N / 2) % N - N / 2
        u = dx * np.cos(a) + dy * np.sin(a)
        v = -dx * np.sin(a) + dy * np.cos(a)
        m = (u / ln) ** 2 + (v / wd) ** 2 <= 1
        col = np.array((browns if dead else greens)[int(rng.integers(4))], np.float32) * rng.uniform(0.85, 1.15)
        t.c[m] = col
        t.h[m] = rng.uniform(0.3, 1.0)
    if not dead:
        m = np.clip(P.fbm(rng) * 2 - 0.5, 0, 1)
        t.mix(m, (140, 128, 60), 0.35)  # yellowing
    return t


def leather(seed, rgb=(92, 58, 36)):
    t = mat("Leather009", rgb, 1.0, seed=seed, smooth=0.45)
    return wear(t, 0.3, 4)


def radio(seed):
    """Front plate, viewer's left to right (cols 12-52, rows 40-64): speaker cloth, the tuning
    dial with its stations, two bakelite knobs."""
    t = plain(seed, (96, 62, 38), 0.05, 0.5)
    area(t, 13, 42, 32, 62, (150, 128, 96), smooth=0.1)
    for x in range(13, 32, 2):
        area(t, x, 42, x + 1, 62, mul=0.8)
    area(t, 34, 42, 51, 52, (214, 196, 150), smooth=0.8, f0=40)
    for x in range(35, 51, 3):
        area(t, x, 47, x + 1, 50, (60, 40, 30))
    area(t, 41, 43, 42, 51, (170, 30, 20))  # the needle
    for x in (37, 47):
        disc(t, x, 57, 2.4, (30, 24, 20), h=1.0, smooth=0.7)
    edges(t, 12, 40, 52, 64, light=1.2, dark=0.6)
    return wear(t, 0.3, 4)


def canister(seed):
    """Three kitchen canisters (viewer's left to right: cols 8-20, 24-38, 42-56), cream enamel
    with a red band and a label each."""
    t = enamel(seed, (226, 214, 184))
    area(t, 0, 38, N, 41, (170, 40, 34))
    for x0, x1, ink in ((8, 20, (60, 40, 30)), (24, 38, (40, 60, 90)), (42, 56, (90, 30, 30))):
        area(t, x0 + 1, 46, x1 - 1, 55, (238, 232, 214))
        for y in (48, 51):
            area(t, x0 + 2, y, x1 - 2, y + 1, ink)
    return wear(t, 0.3, 4)


CANS = [(176, 36, 30), (60, 120, 60), (220, 170, 40), (40, 70, 140), (200, 200, 190)]


def can(seed):
    """Tin cans: every few columns a different label, a rim of bare tin top and bottom."""
    t = plain(seed, (190, 190, 186), 0.04, 0.6)
    t.f0[:] = 200
    for i, x in enumerate(range(0, N, 12)):
        col = CANS[i % len(CANS)]
        area(t, x, 0, x + 12, N, col, smooth=0.3, f0=30)
        area(t, x + 2, 0, x + 10, N, mul=1.12)
        if i % 3 == 0:
            area(t, x + 3, 0, x + 9, N, (236, 232, 220))  # a white panel on the soup
    for y in range(0, N, 18):
        area(t, 0, y, N, y + 2, (170, 170, 166), smooth=0.7, f0=200)
    return wear(t, 0.25, 0)


def bottle(seed, rgb):
    t = plain(seed, rgb, 0.05, 0.9)
    t.f0[:] = 40
    for x in range(0, N, 8):
        area(t, x + 1, 0, x + 2, N, mul=1.6)  # the glint down each bottle
    area(t, 0, 44, N, 56, (214, 200, 160), smooth=0.2, f0=4)  # the labels
    area(t, 0, 48, N, 50, (130, 40, 30))
    return wear(t, 0.25, 0)


def book_spines(seed):
    """A stack of books and magazines from the side: a band per book, pages showing at one end."""
    t = plain(seed, (160, 140, 110), 0.03, 0.3)
    rng = t.rng
    cols = [(120, 36, 30), (36, 60, 100), (50, 90, 60), (200, 180, 120), (70, 50, 40), (180, 170, 160)]
    y = 0
    while y < N:
        hgt = int(rng.integers(4, 9))
        area(t, 0, y, N, y + hgt, cols[int(rng.integers(len(cols)))])
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.2)
        area(t, 54, y + 1, N, y + hgt, (222, 214, 192))  # the pages
        y += hgt
    return wear(t, 0.3, 0)


def fur(seed, rgb=(138, 98, 58)):
    t = plain(seed, rgb, 0.1, 0.05)
    t.speckle(0.25, (rgb[0] * 0.7, rgb[1] * 0.7, rgb[2] * 0.7), 0.6)
    t.speckle(0.15, (min(255, rgb[0] * 1.3), min(255, rgb[1] * 1.3), min(255, rgb[2] * 1.3)), 0.5)
    return wear(t, 0.3, 0)


def ironing_cover(seed):
    t = plain(seed, (200, 196, 180), 0.04, 0.1)
    for gy in range(0, N, 8):
        for gx in range(0, N, 8):
            disc(t, gx + (4 if (gy // 8) % 2 else 0), gy + 4, 1.4, (150, 80, 90))
    stain(t, 30, 30, 9, (120, 80, 40), 0.5)  # an iron left face down
    return wear(t, 0.3, 0)


def fan_grille(seed):
    t = plain(seed, (70, 72, 76), 0.04, 0.5)
    for r in (6, 11, 16, 21, 26, 31):
        disc(t, 32, 32, r, (190, 192, 194), ring=1, smooth=0.7, f0=200)
    for k in range(8):
        a = k * np.pi / 4
        for i in range(30):
            x, y = int(32 + np.cos(a) * i), int(32 + np.sin(a) * i)
            area(t, x, y, x + 1, y + 1, (190, 192, 194))
    disc(t, 32, 32, 4, (220, 220, 214))
    return wear(t, 0.35, 0)


def bin_bag(seed):
    t = plain(seed, (22, 22, 24), 0.1, 0.75)
    t.f0[:] = 30
    rng = t.rng
    for _ in range(14):  # creases catching the light
        x, y = rng.uniform(0, N, 2)
        a = rng.uniform(0, np.pi)
        for i in range(int(rng.integers(6, 16))):
            xi, yi = int(x + np.cos(a) * i) % N, int(y + np.sin(a) * i) % N
            t.c[yi, xi] = (70, 70, 74)
    return t


def coat(seed, rgb):
    t = mat("Fabric023", rgb, 1.2, seed=seed, smooth=0.1)
    for x in (30, 31):
        area(t, x, 0, x + 1, N, mul=0.6)  # the front opening
    for y in range(10, 60, 12):
        disc(t, 28, y, 1.2, (40, 30, 24))
    return wear(t, 0.3, 0)


# ---------------------------------------------------------------- the texture table

def textures():
    T = {}
    for i, f in enumerate(FINISH):
        T[f"home_{f}"] = lambda f=f, i=i: wear(finish(f, 900 + i, rot=False), 0.3, 6)
        T[f"home_cab_{f}"] = lambda f=f, i=i: cab_front(f, "doors", 910 + i)
        T[f"home_drw_{f}"] = lambda f=f, i=i: cab_front(f, "drawers", 920 + i)
        T[f"home_sinkcab_{f}"] = lambda f=f, i=i: cab_front(f, "sink", 930 + i)
        T[f"home_wcab_{f}"] = lambda f=f, i=i: cab_front(f, "wall", 940 + i)
        T[f"home_top_{f}"] = lambda f=f, i=i: worktop(f, 950 + i)
    T.update({
        "home_basin": lambda: basin(960), "home_steel": lambda: steel(961), "home_chrome": lambda: chrome(962),
        "home_enamel": lambda: wear(enamel(963), 0.3, 4), "home_almond": lambda: wear(enamel(964, (222, 212, 186)), 0.3, 4),
        "home_stove_front": lambda: stove_front(965), "home_stove_top": lambda: stove_top(966),
        "home_stove_panel": lambda: stove_panel(967), "home_fridge_door": lambda: fridge(968, False),
        "home_fridge_door_top": lambda: fridge(969, True), "home_dishwasher": lambda: dishwasher(970),
        "home_microwave": lambda: microwave(971), "home_toaster": lambda: toaster(972),
        "home_plates": lambda: plates(973), "home_mug": lambda: mug(974, (176, 60, 50)),
        "home_table_top": lambda: table_top(975), "home_chair_wood": lambda: chair_wood(976),
        "home_plastic_beige": lambda: trash_lid(977), "home_plastic_black": lambda: wear(plain(978, (34, 32, 30), 0.05, 0.5), 0.3, 0),
        "home_wood_dark": lambda: wear(wood(979, "dark"), 0.3, 8), "home_wood_oak": lambda: wear(wood(980, "oak"), 0.3, 8),
        "home_wood_walnut": lambda: wear(wood(981, "walnut"), 0.3, 8),
        "home_coffee_table": lambda: coffee_table(982), "home_tv_case": lambda: tv_case(983),
        "home_tv_screen": lambda: tv_screen(984), "home_tv_smashed": lambda: tv_screen(985, True),
        "home_books": lambda: books(986), "home_shelf_junk": lambda: books(987, junk=True),
        "home_shade": lambda: lampshade(988),
        "home_rug_red": lambda: rug(989, *RUGS["red"]), "home_rug_blue": lambda: rug(990, *RUGS["blue"]),
        "home_rug_red_edge": lambda: rug(989, *RUGS["red"], "edge"), "home_rug_blue_edge": lambda: rug(990, *RUGS["blue"], "edge"),
        "home_rug_red_corner": lambda: rug(989, *RUGS["red"], "corner"),
        "home_rug_blue_corner": lambda: rug(990, *RUGS["blue"], "corner"),
        "home_stain_brown": lambda: floor_stain(1057, (84, 64, 40)), "home_stain_dark": lambda: floor_stain(1058, (44, 38, 30), 14),
        "home_radiator": lambda: radiator(991),
        "home_picture_lake": lambda: picture("lake", 992), "home_picture_family": lambda: picture("family", 993),
        "home_picture_stitch": lambda: picture("stitch", 994), "home_mirror": lambda: mirror(995),
        "home_calendar": lambda: calendar(996), "home_phone": lambda: phone(997),
        **{f"home_curtains_{c}{part}": (lambda c=c, part=part: curtains(seed, CURTAINS[c], part.strip("_") or "bottom"))
           for seed, c in ((998, "gold"), (999, "rose"), (1003, "green")) for part in ("", "_top")},
        "home_blinds": lambda: blinds(1000),
        "home_mattress": lambda: mattress(1001), "home_mattress_stained": lambda: mattress(1002, True),
        "home_pillow": lambda: pillow(1003), "home_sheet": lambda: wear(plain(1004, (226, 222, 210), 0.04, 0.1), 0.25, 0),
        "home_dresser": lambda: dresser_front(1005), "home_wardrobe": lambda: wardrobe_front(1006, False),
        "home_wardrobe_top": lambda: wardrobe_front(1007, True), "home_desk": lambda: desk_front(1008),
        "home_nightstand": lambda: nightstand_front(1009), "home_pc_case": lambda: pc_case(1010),
        "home_pc_screen": lambda: pc_screen(1011), "home_keyboard": lambda: keyboard(1012),
        "home_toy_chest": lambda: toy_chest(1013), "home_wicker": lambda: wear(mat("Wicker010B", (150, 120, 80), 1.0, seed=1014, smooth=0.2), 0.3, 0),
        "home_clothes": lambda: clothes_heap(1015),
        "home_ceramic": lambda: ceramic(1016), "home_toilet_water": lambda: water_dirty(1017),
        "home_tub_bottom": lambda: tub_bottom(1018), "home_vanity_top": lambda: vanity_top(1019),
        "home_medicine": lambda: medicine_mirror(1020), "home_towel": lambda: towel(1021),
        "home_shower_curtain": lambda: shower_curtain(1022),
        "home_heater": lambda: heater(1023), "home_heater_top": lambda: heater(1059, True), "home_copper": lambda: copper(1024), "home_bench_top": lambda: bench_top(1025),
        "home_red_steel": lambda: red_steel(1026), "home_toolchest": lambda: toolchest_front(1027),
        "home_freezer": lambda: freezer_front(1028), "home_cardboard": lambda: cardboard(1029),
        "home_rubber": lambda: wear(plain(1030, (34, 32, 30), 0.08, 0.2), 0.2, 0),
        "home_gun_glass": lambda: gun_glass(1031, False), "home_gun_glass_top": lambda: gun_glass(1032, True),
        "home_sheet_blood": lambda: sheet_blood(1033), "home_blood_pool": lambda: blood_pool(1034),
        "home_blood_drag": lambda: blood_drag(1035), "home_blood_hand": lambda: blood_hand(1036),
        "home_wax": lambda: candles(1037), "home_x_code": lambda: x_code(1038), "home_pills": lambda: pills(1039),
        "home_ceiling": lambda: ceiling(1040), "home_floor_edge": lambda: floor_edge(1041),
        "home_floor_oak": lambda: floor_wood(1042, "oak"), "home_floor_parquet": lambda: floor_wood(1043, "parquet"),
        "home_floor_lino": lambda: lino(1044, "seventies"), "home_floor_lino_check": lambda: lino(1045, "check"),
        "home_floor_tile": lambda: tile_floor(1046),
        "home_wainscot": lambda: wainscot(1047),
        "home_panelling": lambda: panelling(1048), "home_panelling_worn": lambda: panelling(1049, True),
        "home_door_white_top": lambda: door_leaf(1050, "white", True), "home_door_white_bottom": lambda: door_leaf(1051, "white", False),
        "home_door_oak_top": lambda: door_leaf(1052, "oak", True), "home_door_oak_bottom": lambda: door_leaf(1053, "oak", False),
        "home_stairs_oak": lambda: stair_tread(1054, "oak"), "home_stairs_carpet": lambda: stair_tread(1055, "carpet"),
        "home_fire_brick": lambda: fire_brick(1200), "home_fire_brick_soot": lambda: fire_brick(1201, True),
        "home_soot": lambda: soot(1202), "home_hearth": lambda: hearth(1203), "home_charred": lambda: charred(1204),
        "home_cast_iron": lambda: cast_iron(1205), "home_stove_door": lambda: stove_door(1206),
        "home_piano_wood": lambda: piano_wood(1207), "home_piano_keys": lambda: piano_keys(1208),
        "home_terracotta": lambda: terracotta(1209), "home_foliage": lambda: foliage(1210),
        "home_foliage_dead": lambda: foliage(1211, True), "home_leather": lambda: leather(1212),
        "home_radio": lambda: radio(1213), "home_canister": lambda: canister(1214), "home_can": lambda: can(1215),
        "home_bottle_green": lambda: bottle(1216, (40, 90, 48)), "home_bottle_brown": lambda: bottle(1217, (96, 56, 24)),
        "home_book_spines": lambda: book_spines(1218), "home_fur": lambda: fur(1219),
        "home_ironing": lambda: ironing_cover(1220), "home_fan": lambda: fan_grille(1221),
        "home_bin_bag": lambda: bin_bag(1222), "home_coat_tan": lambda: coat(1223, (150, 120, 80)),
        "home_coat_navy": lambda: coat(1224, (40, 48, 70)),
    })
    for i, k in enumerate(FABRICS):
        T[f"home_fab_{k}"] = lambda k=k, i=i: fabric(k, 1100 + i)
        T[f"home_fab_{k}_cushion"] = lambda k=k, i=i: fabric(k, 1110 + i, cushion=True)
    for i, k in enumerate(QUILTS):
        T[f"home_duvet_{k}"] = lambda k=k, i=i: duvet(k, 1120 + i)
    for i, k in enumerate(NOTES):
        T[f"home_note_{k}"] = lambda k=k, i=i: note(k, 1130 + i)
    for i, k in enumerate(SPRAY):
        for part in (0, 1):
            T[f"home_spray_{k}_{part}"] = lambda k=k, i=i, part=part: spray(k, part, 1140 + i * 2 + part)
    for i, k in enumerate(CARPETS):
        T[f"home_carpet_{k}"] = lambda k=k, i=i: carpet(k, 1150 + i)
    for i, k in enumerate(PAPERS):
        T[f"home_paper_{k}"] = lambda k=k, i=i: wallpaper(k, 1160 + i)
        T[f"home_paper_{k}_worn"] = lambda k=k, i=i: wallpaper(k, 1170 + i, worn=True)
    for i, k in enumerate(TILES):
        T[f"home_tile_{k}"] = lambda k=k, i=i: wall_tile(k, 1180 + i)
        T[f"home_tile_{k}_worn"] = lambda k=k, i=i: wall_tile(k, 1190 + i, worn=True)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(_extra_textures(textures()))


def carafe(seed):
    t = plain(seed, (52, 34, 22), 0.05, 0.9)
    t.f0[:] = 40
    area(t, 0, 0, N, 28, (150, 156, 156), smooth=0.95)  # the empty glass above the coffee
    area(t, 8, 0, 11, N, mul=1.4)
    return t


def _extra_textures(T):
    T["home_carafe"] = lambda: carafe(1056)
    return T


# ---------------------------------------------------------------- the blocks

def legs(x0, z0, x1, z1, h, tex, w=1.5):
    return [[x0, 0, z0, x0 + w, h, z0 + w, tex], [x1 - w, 0, z0, x1, h, z0 + w, tex],
            [x0, 0, z1 - w, x0 + w, h, z1, tex], [x1 - w, 0, z1 - w, x1, h, z1, tex]]


def basin_boxes(wall, bottom, top):
    """A worktop with a basin let into it, the cabinet's walls lining the hole."""
    return [[0, 10, 1, 16, 14, 3, wall], [0, 10, 13, 16, 14, 16, wall], [0, 10, 3, 3, 14, 13, wall],
            [13, 10, 3, 16, 14, 13, wall], [3, 10, 3, 13, 10.5, 13, bottom],
            [0, 14, 0, 16, 16, 3, top], [0, 14, 13, 16, 16, 16, top], [0, 14, 3, 3, 16, 13, top], [13, 14, 3, 16, 16, 13, top],
            [7.5, 16, 13.5, 8.5, 20, 14.5, "home_chrome"], [7.5, 19, 9.5, 8.5, 20, 14.5, "home_chrome"],
            [5, 16, 13.6, 6.5, 17, 15, "home_chrome"], [9.5, 16, 13.6, 11, 17, 15, "home_chrome"]]


CHAIR = [[2.5, 7, 2.5, 13.5, 8, 13.5], [2.5, 0, 2.5, 4, 7, 4], [12, 0, 2.5, 13.5, 7, 4], [2.5, 0, 12, 4, 7, 13.5],
         [12, 0, 12, 13.5, 7, 13.5], [2.5, 8, 12, 4, 17, 13.5], [12, 8, 12, 13.5, 17, 13.5],
         [4, 11, 12.5, 12, 12, 13], [4, 14, 12.5, 12, 16.5, 13]]


def tipped(boxes):
    """A chair knocked onto its side: what was height now runs along x."""
    out = []
    for x0, y0, z0, x1, y1, z1 in boxes:
        out.append([round(y0 * 16 / 17, 2), x0 - 2.5, z0, round(y1 * 16 / 17, 2), x1 - 2.5, z1])
    return out


def sofa(k, part):
    fab, cush = f"home_fab_{k}", f"home_fab_{k}_cushion"
    a = 3 if part in ("l", "chair") else 0
    b = 13 if part in ("r", "chair") else 16
    out = [[0, 1.5, 1, 16, 5, 16, fab], [0, 5, 12, 16, 15, 16, fab],
           [a, 5, 1, b, 8, 12, cush], [a, 8, 10, b, 14, 12, cush]]
    if a:
        out += [[0, 5, 1, 3, 10, 16, fab], [1, 0, 2, 2.5, 1.5, 3.5, "home_wood_dark"], [1, 0, 13.5, 2.5, 1.5, 15, "home_wood_dark"]]
    if b < 16:
        out += [[13, 5, 1, 16, 10, 16, fab], [13.5, 0, 2, 15, 1.5, 3.5, "home_wood_dark"], [13.5, 0, 13.5, 15, 1.5, 15, "home_wood_dark"]]
    return out


def bed(q, part):
    duvet = f"home_duvet_{q}"
    frame = "home_wood_dark"
    if part == "head":
        return [[0, 1, 0, 16, 3.5, 14.5, frame], [0, 3.5, 0, 16, 8, 14.5, "home_mattress"],
                [0, 0, 14.5, 16, 15, 16, frame], [2, 8, 9.5, 14, 10.5, 14, "home_pillow"],
                [0, 8, 0, 16, 9, 8, duvet], [0, 8, 7.5, 16, 9.3, 9.5, "home_sheet"],
                [0.5, 0, 12.5, 2, 1, 14, frame], [14, 0, 12.5, 15.5, 1, 14, frame]]
    return [[0, 1, 1.5, 16, 3.5, 16, frame], [0, 3.5, 1.5, 16, 8, 16, "home_mattress"], [0, 8, 1.5, 16, 9, 16, duvet],
            [0, 4, 1.2, 16, 9, 1.5, duvet], [0, 0, 0, 16, 7, 1.2, frame]]


def shelving(frame, plane, top=False):
    out = [[0, 0, 4, 1, 16, 16, frame], [15, 0, 4, 16, 16, 16, frame], [1, 0, 5, 15, 16, 15, plane],
           [1, 0, 15, 15, 16, 16, frame]]
    out += [[1, y, 4, 15, y + 1, 15, frame] for y in (0, 5, 10)]
    out.append([0, 15, 3 if top else 4, 16, 16, 16, frame])
    return out


def blocks():
    out = {}

    def add(name, boxes, title, sound, loot=None, rotate="facing", decor=False, render=None):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if loot:
            spec["loot"] = loot
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        out[name] = spec

    kick = [0, 0, 2, 16, 1, 16, "home_plastic_black"]
    for f in FINISH:
        body, top = f"home_{f}", f"home_top_{f}"
        add(f"home_counter_{f}", [[0, 1, 1, 16, 14, 16, body], kick, [0, 1, 0.5, 16, 14, 1, f"home_cab_{f}"],
                                  [0, 14, 0, 16, 16, 16, top]], "Kitchen Cupboard", "WOOD", "home_cupboard")
        add(f"home_drawers_{f}", [[0, 1, 1, 16, 14, 16, body], kick, [0, 1, 0.5, 16, 14, 1, f"home_drw_{f}"],
                                  [0, 14, 0, 16, 16, 16, top]], "Kitchen Drawers", "WOOD", "home_drawer")
        add(f"home_sink_{f}", [[0, 1, 1, 16, 10, 16, body], kick, [0, 1, 0.5, 16, 14, 1, f"home_sinkcab_{f}"]]
            + basin_boxes("home_steel", "home_basin", top), "Under the Sink", "WOOD", "home_sink")
        add(f"home_wallcab_{f}", [[0, 3, 9.5, 16, 16, 16, body], [0, 3, 9, 16, 16, 9.5, f"home_wcab_{f}"]],
            "Kitchen Cabinet", "WOOD", "home_pantry")
        add(f"home_dishwasher_{f}", [[0, 1, 1, 16, 14, 16, "home_enamel"], kick, [0, 1, 0.5, 16, 14, 1, "home_dishwasher"],
                                     [0, 14, 0, 16, 16, 16, top]], "Dishwasher", "METAL", "home_cupboard")
    add("home_stove", [[0, 0, 1, 16, 14, 16, "home_enamel"], [0, 0, 0.5, 16, 14, 1, "home_stove_front"],
                       [0, 14, 0, 16, 15, 16, "home_stove_top"], [0, 15, 14.5, 16, 19, 16, "home_stove_panel"]],
        "Stove", "METAL", "home_oven")
    add("home_fridge", [[1, 0, 1, 15, 16, 16, "home_almond"], [1, 0, 0.5, 15, 16, 1, "home_fridge_door"]],
        "Fridge", "METAL", "home_fridge")
    add("home_fridge_top", [[1, 0, 1, 15, 13, 16, "home_almond"], [1, 0, 0.5, 15, 13, 1, "home_fridge_door_top"]],
        "Freezer", "METAL", "home_freezer")
    add("home_microwave", [[2, 0, 5, 14, 6, 15, "home_almond"], [2, 0, 4.5, 14, 6, 5, "home_microwave"]], "Microwave", "METAL")
    add("home_toaster", [[4, 0, 7, 12, 5, 12, "home_toaster"], [12, 2, 9, 12.5, 3, 10, "home_plastic_black"]], "Toaster", "METAL")
    add("home_coffee_maker", [[4, 0, 6, 12, 1, 14, "home_plastic_black"], [4, 1, 11, 12, 10, 14, "home_plastic_black"],
                              [4, 8, 6, 12, 10, 11, "home_plastic_black"], [5.5, 1, 6.5, 10.5, 6, 10.5, "home_carafe"]],
        "Coffee Maker", "METAL")
    add("home_dishes", [[2, 0, 2, 9, 2, 9, "home_plates"], [9, 0, 8, 14, 2.5, 13, "home_plates"],
                        [3, 0, 10.5, 6, 3, 13.5, "home_mug"], [10, 0, 3, 13, 3, 6, "home_mug"]], "Dirty Dishes", "GLASS")
    add("home_trash_can", [[4, 0, 4, 12, 11, 12, "home_plastic_beige"], [3.5, 11, 3.5, 12.5, 12, 12.5, "home_plastic_beige"]],
        "Trash Can", "METAL", "home_trash", rotate=None)
    add("home_table", [[0, 12, 0, 16, 13.5, 16, "home_table_top"]] + legs(1, 1, 15, 15, 12, "home_wood_oak"),
        "Kitchen Table", "WOOD", rotate=None)
    add("home_chair", [b + ["home_chair_wood"] for b in CHAIR], "Chair", "WOOD")
    add("home_chair_tipped", [b + ["home_chair_wood"] for b in tipped(CHAIR)], "Knocked-over Chair", "WOOD")

    for k in FABRICS:
        for part in ("l", "m", "r"):
            add(f"home_sofa_{k}_{part}", sofa(k, part), "Sofa", "WOOL")
        add(f"home_armchair_{k}", sofa(k, "chair"), "Armchair", "WOOL")
    add("home_coffee_table", [[1, 6, 2, 15, 7.5, 14, "home_coffee_table"], [2, 1.5, 3, 14, 2.5, 13, "home_wood_dark"]]
        + legs(1.5, 2.5, 14.5, 13.5, 6, "home_wood_dark"), "Coffee Table", "WOOD")
    for name, screen in (("home_tv", "home_tv_screen"), ("home_tv_smashed", "home_tv_smashed")):
        add(name, [[1, 0, 3, 15, 6, 14, "home_wood_dark"], [2, 6, 4, 14, 17, 15, "home_tv_case"],
                   [3, 7.5, 3.5, 13, 15.5, 4, screen], [5.5, 17, 9, 6, 23, 9.5, "home_chrome"], [10, 17, 9, 10.5, 22, 9.5, "home_chrome"]],
            "Television", "METAL")
    add("home_bookcase", shelving("home_wood_walnut", "home_books"), "Bookcase", "WOOD", "home_books")
    add("home_bookcase_top", shelving("home_wood_walnut", "home_books", True), "Bookcase", "WOOD", "home_books")
    add("home_nightstand", [[2, 0, 2, 14, 10, 14, "home_wood_walnut"], [2, 0, 1.5, 14, 10, 2, "home_nightstand"],
                            [6, 10, 6, 10, 11, 10, "home_ceramic"], [7.5, 11, 7.5, 8.5, 15, 8.5, "home_chrome"],
                            [5, 15, 5, 11, 20, 11, "home_shade"]], "Nightstand", "WOOD", "home_nightstand")
    add("home_floor_lamp", [[5, 0, 5, 11, 1, 11, "home_chrome"], [7.5, 1, 7.5, 8.5, 22, 8.5, "home_chrome"],
                            [4.5, 21, 4.5, 11.5, 28, 11.5, "home_shade"]], "Floor Lamp", "METAL", rotate=None)
    for c in RUGS:
        add(f"home_rug_{c}", [[0, 0, 0, 16, 1, 16, f"home_rug_{c}"]], "Rug", "WOOL", rotate=None, decor=True)
        add(f"home_rug_{c}_edge", [[0, 0, 0, 16, 1, 16, f"home_rug_{c}_edge"]], "Rug", "WOOL", decor=True)
        add(f"home_rug_{c}_corner", [[0, 0, 0, 16, 1, 16, f"home_rug_{c}_corner"]], "Rug", "WOOL", decor=True)
    for c in ("brown", "dark"):
        add(f"home_stain_{c}", [[0, 0, 0, 16, 0.3, 16, f"home_stain_{c}"]], "Stain", "WOOL", decor=True)
    add("home_radiator", [[1, 1, 9, 15, 2, 13, "home_radiator"], [1, 11, 9, 15, 12, 13, "home_radiator"],
                          [2, 0, 10, 3, 1, 12, "home_radiator"], [13, 0, 10, 14, 1, 12, "home_radiator"]]
        + [[1.5 + 2 * i, 2, 9.5, 2.5 + 2 * i, 11, 12.5, "home_radiator"] for i in range(7)], "Radiator", "METAL")
    # wall-hung decor: north side against the wall, facing it
    add("home_picture_lake", [[1.25, 4.25, 0, 14.75, 13.75, 1, "home_picture_lake"]], "Painting", "WOOD", decor=True)
    add("home_picture_family", [[2.75, 4.25, 0, 13.25, 14.25, 1, "home_picture_family"]], "Family Photo", "WOOD", decor=True)
    add("home_picture_stitch", [[1.75, 4.25, 0, 14.25, 13.75, 1, "home_picture_stitch"]], "Cross-stitch", "WOOD", decor=True)
    add("home_mirror", [[3, 2, 0, 13, 15, 0.5, "home_mirror"], [2.5, 1.5, 0, 13.5, 2, 0.8, "home_wood_dark"],
                        [2.5, 15, 0, 13.5, 15.5, 0.8, "home_wood_dark"], [2.5, 2, 0, 3, 15, 0.8, "home_wood_dark"],
                        [13, 2, 0, 13.5, 15, 0.8, "home_wood_dark"]], "Mirror", "GLASS", decor=True)
    add("home_calendar", [[5, 3, 0, 11, 13, 0.3, "home_calendar"]], "Calendar", "WOOL", decor=True)
    add("home_phone", [[6, 7, 0, 10, 12, 1.5, "home_phone"], [6.5, 8, 1.5, 9.5, 11.5, 2.3, "home_phone"],
                       [7.5, 2, 0.8, 8.5, 8, 1.6, "home_rubber"]], "Telephone", "METAL", decor=True)
    for c in CURTAINS:
        add(f"home_curtains_{c}", [[0, 0, 15, 16, 16, 15.5, f"home_curtains_{c}"]], "Curtains", "WOOL", decor=True)
        add(f"home_curtains_{c}_top", [[0, 0, 15, 16, 16, 15.5, f"home_curtains_{c}_top"]], "Curtains", "WOOL", decor=True)
    add("home_blinds", [[0, 0, 1, 16, 16, 1.5, "home_blinds"]], "Blinds", "WOOL", decor=True)

    for q in QUILTS:
        add(f"home_bed_{q}_head", bed(q, "head"), "Bed", "WOOL")
        add(f"home_bed_{q}_foot", bed(q, "foot"), "Bed", "WOOL")
    add("home_dresser", [[0, 0, 2, 16, 12, 16, "home_wood_walnut"], [0, 0, 1.5, 16, 12, 2, "home_dresser"],
                         [0, 12, 1, 16, 13.5, 16, "home_wood_walnut"], [2, 13.5, 9, 6, 16, 12, "home_wood_dark"],
                         [10, 13.5, 10, 11, 15.5, 11, "home_chrome"], [12, 13.5, 9.5, 13, 16.5, 10.5, "home_ceramic"]],
        "Dresser", "WOOD", "home_dresser")
    add("home_wardrobe", [[0, 0, 2, 16, 16, 16, "home_wood_walnut"], [0, 0, 1.5, 16, 16, 2, "home_wardrobe"]],
        "Wardrobe", "WOOD", "home_wardrobe")
    add("home_wardrobe_top", [[0, 0, 2, 16, 14, 16, "home_wood_walnut"], [0, 0, 1.5, 16, 14, 2, "home_wardrobe_top"],
                              [0, 14, 1, 16, 15.5, 16, "home_wood_walnut"]], "Wardrobe", "WOOD", "home_wardrobe")
    desk = [[0, 12, 1, 16, 13.5, 16, "home_wood_oak"], [10, 0, 2, 16, 12, 16, "home_wood_oak"],
            [10, 0, 1.5, 16, 12, 2, "home_desk"], [0, 0, 2, 1.5, 12, 16, "home_wood_oak"], [1.5, 4, 14, 10, 12, 15, "home_wood_oak"]]
    add("home_desk", desk, "Desk", "WOOD", "home_desk")
    add("home_desk_pc", desk + [[3, 13.5, 7, 12, 22, 15, "home_pc_case"], [4, 15, 6.5, 11, 21, 7, "home_pc_screen"],
                                [3, 13.5, 2, 11, 14.2, 5, "home_keyboard"]], "Computer Desk", "WOOD", "home_desk")
    add("home_toy_chest", [[1, 0, 3, 15, 9, 13, "home_toy_chest"], [0.5, 9, 2.5, 15.5, 10, 13.5, "home_toy_chest"]],
        "Toy Chest", "WOOD", "home_toys")
    add("home_laundry", [[3, 0, 3, 13, 8, 13, "home_wicker"], [3.5, 8, 3.5, 12.5, 9.5, 12.5, "home_clothes"]],
        "Laundry Basket", "WOOL", "home_laundry", rotate=None)
    add("home_clothes_pile", [[1, 0, 2, 14, 2, 13, "home_clothes"], [3, 2, 4, 10, 3, 10, "home_clothes"],
                              [9, 0, 10, 15, 1.2, 15, "home_clothes"]], "Clothes", "WOOL", rotate=None, decor=True)

    add("home_toilet", [[5.5, 0, 4, 10.5, 5, 11, "home_ceramic"], [4.5, 5, 1.5, 11.5, 8, 4, "home_ceramic"],
                        [4.5, 5, 9, 11.5, 8, 11, "home_ceramic"], [4.5, 5, 4, 6, 8, 9, "home_ceramic"],
                        [10, 5, 4, 11.5, 8, 9, "home_ceramic"], [6, 5, 4, 10, 6.5, 9, "home_toilet_water"],
                        [4, 5, 11, 12, 14, 15, "home_ceramic"], [3.8, 14, 10.8, 12.2, 14.8, 15.2, "home_ceramic"],
                        [4.5, 8, 10, 11.5, 15, 10.8, "home_plastic_beige"], [11.5, 12, 12, 12.5, 12.5, 13, "home_chrome"]],
        "Toilet", "STONE")
    for part in ("l", "r"):
        end = [0, 2, 2, 2, 9, 14, "home_ceramic"] if part == "l" else [14, 2, 2, 16, 9, 14, "home_ceramic"]
        tub = [[0, 0, 0, 16, 9, 2, "home_ceramic"], [0, 0, 14, 16, 9, 16, "home_ceramic"], [0, 0, 2, 16, 2, 14, "home_tub_bottom"], end]
        if part == "l":
            tub += [[2, 7, 7.5, 4, 8, 8.5, "home_chrome"], [2, 9, 5.5, 3, 10.5, 6.5, "home_chrome"], [2, 9, 9.5, 3, 10.5, 10.5, "home_chrome"]]
        add(f"home_tub_{part}", tub, "Bathtub", "STONE")
    add("home_shower_curtain", [[0, 0, 1, 16, 15.5, 1.5, "home_shower_curtain"], [0, 15.5, 0.8, 16, 16, 1.7, "home_chrome"]],
        "Shower Curtain", "WOOL", decor=True)
    add("home_vanity", [[0, 1, 1, 16, 10, 16, "home_white"], kick, [0, 1, 0.5, 16, 14, 1, "home_sinkcab_white"]]
        + basin_boxes("home_ceramic", "home_ceramic", "home_vanity_top"), "Bathroom Cabinet", "WOOD", "home_bathroom")
    add("home_medicine", [[3, 2, 13, 13, 14, 16, "home_steel"], [3, 2, 12.5, 13, 14, 13, "home_medicine"]],
        "Medicine Cabinet", "GLASS", "home_medicine")
    add("home_towel", [[3, 3, 0, 13, 13, 0.8, "home_towel"], [2.5, 12, 0, 13.5, 12.5, 1.2, "home_chrome"]],
        "Towel", "WOOL", decor=True)

    heater = [[3, 0, 3, 13, 16, 13, "home_heater"], [2, 1, 4, 14, 16, 12, "home_heater"], [4, 1, 2, 12, 16, 14, "home_heater"]]
    add("home_water_heater", heater, "Water Heater", "METAL", rotate=None)
    add("home_water_heater_top", [[b[0], b[1], b[2], b[3], 10, b[5], "home_heater_top"] for b in heater]
        + [[3.5, 10, 3.5, 12.5, 11, 12.5, "home_heater_top"], [5, 11, 7, 6, 16, 8, "home_copper"],
           [10, 11, 7, 11, 16, 8, "home_copper"], [6.5, 11, 6.5, 9.5, 16, 9.5, "home_steel"]],
        "Water Heater", "METAL", rotate=None)
    add("home_workbench", [[0, 11, 1, 16, 13, 16, "home_bench_top"], [1, 3, 2, 15, 4, 15, "home_bench_top"],
                           [11, 13, 0.5, 15, 15, 4, "home_steel"], [12, 13, 0, 14, 14.5, 0.5, "home_steel"],
                           [2, 4, 4, 7, 8, 9, "home_cardboard"], [9, 4, 5, 13, 7, 10, "home_red_steel"]]
        + legs(0.5, 1.5, 15.5, 15.5, 11, "home_wood_oak"), "Workbench", "WOOD", "home_garage")
    add("home_tool_chest", [[1, 1, 3, 15, 14, 14, "home_red_steel"], [1, 1, 2.5, 15, 14, 3, "home_toolchest"],
                            [0.5, 14, 2.5, 15.5, 15, 14.5, "home_rubber"]]
        + [[x, 0, z, x + 1.5, 1, z + 1.5, "home_rubber"] for x in (2, 12.5) for z in (4, 11.5)], "Tool Chest", "METAL", "home_tools")
    add("home_garage_shelf", shelving("home_steel", "home_shelf_junk"), "Garage Shelf", "METAL", "home_garage")
    add("home_mower", [[3, 1, 2, 13, 5, 12, "home_red_steel"], [5, 5, 4, 11, 9, 10, "home_steel"],
                       [2, 0, 2.5, 3, 2.5, 5, "home_rubber"], [13, 0, 2.5, 14, 2.5, 5, "home_rubber"],
                       [2, 0, 9, 3, 2.5, 11.5, "home_rubber"], [13, 0, 9, 14, 2.5, 11.5, "home_rubber"],
                       [4, 5, 12, 5, 13, 13, "home_steel"], [11, 5, 12, 12, 13, 13, "home_steel"], [4, 12, 12, 12, 13, 13, "home_steel"]],
        "Lawnmower", "METAL")
    add("home_freezer", [[0, 0, 2, 16, 13, 16, "home_enamel"], [0, 13, 1.5, 16, 14, 16, "home_enamel"],
                         [0, 0, 1.5, 16, 13, 2, "home_freezer"]], "Chest Freezer", "METAL", "home_freezer")
    add("home_boxes", [[1, 0, 1, 10, 7, 9, "home_cardboard"], [6, 0, 8, 15, 8, 15, "home_cardboard"],
                       [2, 7, 2, 9, 12, 8, "home_cardboard"]], "Boxes", "WOOD", "home_boxes", rotate=None)
    add("home_gun_cabinet", [[0, 0, 4, 16, 16, 16, "home_wood_dark"], [0, 0, 3.5, 16, 16, 4, "home_gun_glass"]],
        "Gun Cabinet", "WOOD", "home_guns")
    add("home_gun_cabinet_top", [[0, 0, 4, 16, 14, 16, "home_wood_dark"], [0, 0, 3.5, 16, 14, 4, "home_gun_glass_top"],
                                 [0, 14, 3, 16, 15.5, 16, "home_wood_dark"]], "Gun Cabinet", "WOOD")

    add("home_corpse_head", [[3, 0, 1, 13, 2.5, 16, "home_sheet_blood"], [3.5, 2.5, 1, 12.5, 4, 9, "home_sheet_blood"],
                             [5.5, 2.5, 10, 10.5, 5, 15, "home_sheet_blood"]], "Body", "WOOL")
    add("home_corpse_foot", [[3.5, 0, 0, 12.5, 2.5, 15, "home_sheet_blood"], [4, 2.5, 0.5, 7, 5, 3, "home_sheet_blood"],
                             [9, 2.5, 0.5, 12, 5, 3, "home_sheet_blood"]], "Body", "WOOL")
    add("home_blood_pool", [[0, 0, 0, 16, 0.3, 16, "home_blood_pool"]], "Blood", "WOOL", rotate=None, decor=True)
    add("home_blood_drag", [[0, 0, 0, 16, 0.3, 16, "home_blood_drag"]], "Blood", "WOOL", decor=True)
    add("home_blood_hand", [[0, 0, 0, 16, 16, 0.3, "home_blood_hand"]], "Blood", "WOOL", decor=True)
    add("home_pills", [[0, 0, 0, 16, 0.3, 16, "home_pills"]], "Spilled Pills", "GLASS", decor=True)
    add("home_mattress_floor", [[1, 0, 0, 15, 2.5, 16, "home_mattress_stained"], [2, 2.5, 5, 14, 3, 15, "home_duvet_blue"]],
        "Mattress", "WOOL")
    add("home_candles", [[5, 0, 6, 6.5, 3, 7.5, "home_wax"], [7, 0, 8, 8, 5, 9, "home_wax"], [9, 0, 6, 10.5, 2, 7.5, "home_wax"],
                         [10, 0, 9.5, 11, 4, 10.5, "home_wax"], [4, 0, 5, 12, 0.3, 11, "home_wax"]],
        "Candles", "WOOL", rotate=None, decor=True)
    for k in NOTES:
        add(f"home_note_{k}", [[3, 2, 0, 13, 14, 0.3, f"home_note_{k}"]], "Note", "WOOL", decor=True)
    for k in SPRAY:
        for part in (0, 1):
            add(f"home_spray_{k}_{part}", [[0, 0, 0, 16, 16, 0.3, f"home_spray_{k}_{part}"]], "Spray Paint", "WOOL", decor=True)
    add("home_x_code", [[0, 0, 0, 16, 16, 0.3, "home_x_code"]], "Search Mark", "WOOL", decor=True)

    # ---- the lived-in layer
    brick, sooty = "home_fire_brick", "home_fire_brick_soot"
    add("home_fireplace", [[0, 0, 7, 3, 12, 16, brick], [13, 0, 7, 16, 12, 16, brick], [3, 8, 7, 13, 12, 16, sooty],
                           [3, 0, 14, 13, 8, 16, "home_soot"], [3, 0, 7, 13, 0.5, 14, "home_soot"],
                           [4, 0.5, 9, 12, 2.5, 11, "home_charred"], [5, 0.5, 10.5, 11, 2, 12.5, "home_charred"],
                           [0, 0, 2.5, 16, 0.8, 7, "home_hearth"], [0, 12, 5, 16, 13.5, 16, "home_wood_walnut"],
                           [1, 13.5, 8, 15, 16, 16, brick], [6.5, 13.5, 5.5, 9.5, 16.5, 7.5, "home_wood_dark"],
                           [7.2, 14.2, 5.4, 8.8, 15.8, 5.5, "home_ceramic"], [2, 13.5, 6, 3, 16, 7, "home_chrome"],
                           [13, 13.5, 6, 14, 16, 7, "home_chrome"]], "Fireplace", "STONE")
    add("home_chimney", [[1, 0, 8, 15, 16, 16, brick]], "Chimney Breast", "STONE")
    add("home_wood_stove", [[1, 0, 1, 15, 0.5, 15, "home_hearth"], [3, 2, 4, 13, 11, 14, "home_cast_iron"],
                            [4, 4, 3.5, 12, 9.5, 4, "home_stove_door"], [2.5, 11, 3.5, 13.5, 12, 14.5, "home_cast_iron"],
                            [6.5, 12, 8, 9.5, 16, 11, "home_cast_iron"]] + legs(3.5, 4.5, 12.5, 13.5, 2, "home_cast_iron", 1),
        "Wood Stove", "METAL")
    add("home_stove_pipe", [[6.5, 0, 8, 9.5, 16, 11, "home_cast_iron"]], "Stove Pipe", "METAL")
    for part in ("l", "r"):
        cheek = [0, 0, 4, 1, 9, 9, "home_piano_wood"] if part == "l" else [15, 0, 4, 16, 9, 9, "home_piano_wood"]
        add(f"home_piano_{part}", [[0, 0, 9, 16, 15, 16, "home_piano_wood"], [0, 15, 8.5, 16, 16, 16, "home_piano_wood"],
                                   [0, 6.5, 4, 16, 8, 9, "home_piano_keys"], [0, 0, 8.5, 16, 6.5, 9, "home_piano_wood"],
                                   [0, 8, 8.5, 16, 13, 9, "home_piano_wood"], cheek,
                                   [2 if part == "l" else 0, 8, 7, 16 if part == "l" else 14, 12, 7.5, "home_piano_wood"]],
            "Piano", "WOOD")
    pot = [[5, 0, 5, 11, 5, 11, "home_terracotta"], [4.5, 4, 4.5, 11.5, 5.5, 11.5, "home_terracotta"]]
    add("home_plant", pot + [[3, 5.5, 3, 13, 11, 13, "home_foliage"], [5, 11, 4, 11, 15, 10, "home_foliage"],
                             [6, 9, 11, 10, 13, 14, "home_foliage"]], "Houseplant", "GRASS", rotate=None)
    add("home_plant_dead", pot + [[7.5, 5.5, 7.5, 8.5, 12, 8.5, "home_foliage_dead"], [5, 8, 6, 8, 9, 10, "home_foliage_dead"],
                                  [8, 6, 7, 12, 7, 11, "home_foliage_dead"], [4, 5.5, 4, 12, 6, 12, "home_foliage_dead"]],
        "Dead Houseplant", "GRASS", rotate=None)
    add("home_coat_rack", [[5, 0, 5, 11, 1, 11, "home_wood_dark"], [7.5, 1, 7.5, 8.5, 28, 8.5, "home_wood_dark"],
                           [5, 26, 7.5, 11, 27, 8.5, "home_wood_dark"], [7.5, 26, 5, 8.5, 27, 11, "home_wood_dark"],
                           [4, 12, 8.5, 9.5, 26, 11.5, "home_coat_tan"], [8, 15, 4.5, 12.5, 26, 7.5, "home_coat_navy"],
                           [5, 26, 5, 10, 28.5, 10, "home_fab_brown"]], "Coat Rack", "WOOD")
    add("home_shoes", [[2, 0, 3, 4.5, 2.5, 9, "home_leather"], [5, 0, 3.5, 7.5, 2.5, 9.5, "home_leather"],
                       [9, 0, 8, 11, 2, 13, "home_rubber"], [11.5, 0, 7, 13.5, 2, 12, "home_rubber"]],
        "Shoes", "WOOL", decor=True)
    radio_b = [[3, 0, 6.5, 13, 6, 11, "home_wood_walnut"], [3, 0, 6, 13, 6, 6.5, "home_radio"],
               [10, 6, 8, 10.5, 12, 8.5, "home_chrome"]]
    lamp_b = [[6, 0, 6, 10, 1, 10, "home_ceramic"], [6.5, 1, 6.5, 9.5, 6, 9.5, "home_ceramic"],
              [7.5, 6, 7.5, 8.5, 8, 8.5, "home_chrome"], [4.5, 8, 4.5, 11.5, 13, 11.5, "home_shade"]]
    books_b = [[3, 0, 4, 13, 2, 12, "home_book_spines"], [4, 2, 3.5, 12.5, 4, 11, "home_book_spines"],
               [3.5, 4, 5, 12, 5.5, 12.5, "home_book_spines"], [10, 0, 1, 15, 0.5, 4, "home_book_spines"]]
    bottles_b = [[3, 0, 4, 5, 7, 6, "home_bottle_green"], [3.5, 7, 4.5, 4.5, 10, 5.5, "home_bottle_green"],
                 [7, 0, 7, 9, 6, 9, "home_bottle_brown"], [7.5, 6, 7.5, 8.5, 8.5, 8.5, "home_bottle_brown"],
                 [9, 0, 2, 15, 2, 4, "home_bottle_green"], [15, 0.5, 2.5, 16, 1.5, 3.5, "home_bottle_green"]]
    cans_b = []
    for x, z, y in ((2, 4, 0), (6, 4, 0), (10, 4, 0), (4, 5, 4.5), (8, 5, 4.5), (5, 10, 0)):
        cans_b += [[x, y, z, x + 3.5, y + 4.2, z + 3.5, "home_can"], [x, y + 4.2, z, x + 3.5, y + 4.5, z + 3.5, "home_steel"]]
    dishes_b = out["home_dishes"]["boxes"]
    surface = {"home_radio": (radio_b, "Radio", "METAL"), "home_lamp_table": (lamp_b, "Table Lamp", "METAL"),
               "home_books_stack": (books_b, "Books", "WOOD"), "home_bottles": (bottles_b, "Bottles", "GLASS"),
               "home_cans": (cans_b, "Canned Food", "METAL")}
    for name, (bx, title, sound) in surface.items():
        add(name, bx, title, sound, decor=True)
    # the same things sat on a table, desk or dresser top (13.5) rather than a worktop (16)
    for name, (bx, title, sound) in list(surface.items()) + [("home_dishes", (dishes_b, "Dirty Dishes", "GLASS"))]:
        add(name + "_low", [b[:1] + [b[1] - 2.5] + b[2:4] + [b[4] - 2.5] + b[5:] for b in bx], title, sound, decor=True)
    add("home_jars", [[2, 0, 5, 5.5, 7, 8.5, "home_canister"], [6.5, 0, 5, 10, 6, 8.5, "home_canister"],
                      [11, 0, 5, 14, 5, 8.5, "home_canister"], [2, 7, 5, 5.5, 7.6, 8.5, "home_steel"],
                      [6.5, 6, 5, 10, 6.6, 8.5, "home_steel"], [11, 5, 5, 14, 5.6, 8.5, "home_steel"]], "Canisters", "METAL",
        decor=True)
    add("home_crib", [[0, 0, 0, 1.5, 15, 1.5, "home_white"], [14.5, 0, 0, 16, 15, 1.5, "home_white"],
                      [0, 0, 14.5, 1.5, 15, 16, "home_white"], [14.5, 0, 14.5, 16, 15, 16, "home_white"],
                      [1.5, 13, 0.3, 14.5, 14, 1.2, "home_white"], [1.5, 13, 14.8, 14.5, 14, 15.7, "home_white"],
                      [0.3, 13, 1.5, 1.2, 14, 14.5, "home_white"], [14.8, 13, 1.5, 15.7, 14, 14.5, "home_white"],
                      [1.5, 3, 1.5, 14.5, 5, 14.5, "home_mattress"], [3, 5, 3, 11, 5.6, 11, "home_duvet_blue"],
                      [10, 5, 9, 13, 8, 12, "home_fur"]]
        + [[x, 3, 0.4, x + 0.8, 13, 1.1, "home_white"] for x in (3, 5.5, 8, 10.5, 13)]
        + [[x, 3, 14.9, x + 0.8, 13, 15.6, "home_white"] for x in (3, 5.5, 8, 10.5, 13)]
        + [[0.4, 3, z, 1.1, 13, z + 0.8, "home_white"] for z in (4, 7.5, 11)]
        + [[14.9, 3, z, 15.6, 13, z + 0.8, "home_white"] for z in (4, 7.5, 11)], "Crib", "WOOD")
    add("home_teddy", [[5, 0, 6, 11, 6, 11, "home_fur"], [6, 6, 6.5, 10, 10, 10.5, "home_fur"],
                       [5.5, 9, 8, 7, 11, 9, "home_fur"], [9, 9, 8, 10.5, 11, 9, "home_fur"],
                       [3.5, 2, 7, 5, 5, 9, "home_fur"], [11, 2, 7, 12.5, 5, 9, "home_fur"],
                       [5.5, 0, 4, 7.5, 2, 6, "home_fur"], [8.5, 0, 4, 10.5, 2, 6, "home_fur"],
                       [7.2, 7.5, 6.2, 8.8, 8.5, 6.5, "home_plastic_black"]], "Teddy Bear", "WOOL", decor=True)
    add("home_ironing_board", [[0, 10, 6, 16, 11, 10.5, "home_ironing"], [11, 11, 7, 14.5, 13, 9.5, "home_chrome"],
                               [3, 0, 7.5, 4, 10, 8.5, "home_steel"], [12, 0, 7.5, 13, 10, 8.5, "home_steel"],
                               [3, 4.5, 7.5, 13, 5.5, 8.5, "home_steel"]], "Ironing Board", "METAL")
    add("home_vacuum", [[4, 0, 3, 12, 3, 10, "home_red_steel"], [5.5, 3, 8, 10.5, 15, 11, "home_fab_blue"],
                        [7.5, 15, 9, 8.5, 24, 10, "home_chrome"], [6, 23, 9, 10, 24, 10, "home_plastic_black"],
                        [4.5, 0, 2.5, 11.5, 1, 3, "home_rubber"]], "Vacuum Cleaner", "METAL")
    add("home_fan", [[5, 0, 5, 11, 1, 11, "home_plastic_black"], [7.5, 1, 7.5, 8.5, 17, 8.5, "home_chrome"],
                     [4, 17, 6.5, 12, 25, 7.5, "home_fan"], [5, 18, 7.5, 11, 24, 9, "home_plastic_black"]], "Fan", "METAL")
    add("home_bin_bags", [[1, 0, 2, 9, 7, 10, "home_bin_bag"], [7, 0, 6, 15, 6, 14, "home_bin_bag"],
                          [4, 6, 4, 10, 10, 9, "home_bin_bag"], [4.5, 7, 9, 6, 9, 10.5, "home_bin_bag"]],
        "Garbage Bags", "WOOL", "home_trash", rotate=None)

    # floors carry the ceiling of the room below; walls are papered, tiled or panelled
    floors = {f"carpet_{k}": f"home_carpet_{k}" for k in CARPETS}
    floors.update({"oak": "home_floor_oak", "parquet": "home_floor_parquet", "lino": "home_floor_lino",
                   "lino_check": "home_floor_lino_check", "tile": "home_floor_tile"})
    for k, t in floors.items():
        out[f"home_floor_{k}"] = {"tex": {"up": t, "down": "home_ceiling", "side": "home_floor_edge"},
                                  "title": "Floor", "sound": "WOOL" if "carpet" in k else "WOOD"}
    for k in PAPERS:
        out[f"home_wall_{k}"] = {"tex": f"home_paper_{k}", "title": "Wallpaper", "sound": "STONE"}
        out[f"home_wall_{k}_worn"] = {"tex": f"home_paper_{k}_worn", "title": "Wallpaper", "sound": "STONE"}
    for k in TILES:
        out[f"home_wall_tile_{k}"] = {"tex": f"home_tile_{k}", "title": "Wall Tiles", "sound": "STONE"}
        out[f"home_wall_tile_{k}_worn"] = {"tex": f"home_tile_{k}_worn", "title": "Wall Tiles", "sound": "STONE"}
    out["home_wall_panelling"] = {"tex": "home_panelling", "title": "Panelling", "sound": "WOOD"}
    out["home_wall_panelling_worn"] = {"tex": "home_panelling_worn", "title": "Panelling", "sound": "WOOD"}
    out["home_wall_wainscot"] = {"tex": {"side": "home_wainscot", "up": "home_floor_edge", "down": "home_floor_edge"},
                                 "title": "Wainscot", "sound": "WOOD"}
    for k in ("white", "oak"):
        out[f"home_door_{k}"] = {"door": "wood", "tex": {"top": f"home_door_{k}_top", "bottom": f"home_door_{k}_bottom"},
                                 "title": "Door"}
    for k in ("oak", "carpet"):
        out[f"home_stairs_{k}"] = {"model": "boxes", "rotate": "facing", "title": "Stairs", "sound": "WOOD",
                                   "boxes": [[0, 0, 0, 16, 8, 16, f"home_stairs_{k}"], [0, 8, 0, 16, 16, 8, f"home_stairs_{k}"]]}
    out["home_banister"] = {"model": "boxes", "rotate": "axis", "title": "Banister", "sound": "WOOD", "boxes": [
        [7, 14, 0, 9, 15.5, 16, "home_wood_dark"], [7.25, 0, 0, 8.75, 1, 16, "home_wood_dark"]]
        + [[7.5, 1, z, 8.5, 14, z + 1, "home_wood_dark"] for z in (1.5, 5.5, 9.5, 13.5)]}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("home_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    lines = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in kept.items())
    rest = {k: v for k, v in data.items() if k != "blocks"}
    head = "".join(f'  {json.dumps(k)}: {json.dumps(v)},\n' for k, v in rest.items())
    path.write_text("{\n" + head + '  "blocks": {\n' + lines + "\n  }\n}\n")
    print(f"{len(made)} home blocks in {path}")


if __name__ == "__main__":
    main()
