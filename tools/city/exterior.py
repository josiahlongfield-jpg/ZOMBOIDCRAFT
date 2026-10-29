#!/usr/bin/env python3
"""The outsides of the county's houses: shingled roofs in four-course steps with their gutters,
fascia and ridge caps, standing-seam tin and slate; double-hung sash windows in their casings
with sills (whole, lit, broken, empty, boarded over from outside), louvred shutters, panelled
front doors, porch posts, railings, boards and haint-blue ceilings, rockers and swings, the
chimney's crown and the stove's flue, the TV aerial, the meter, the air conditioner, the bins,
the house number and the doormat, and the notices the end left on the doors.

    python3 tools/city/exterior.py        rewrites the ext_ entries in blocks.json

photo_textures.py draws the textures (exterior.register()). Pieces follow furniture.py's rule:
the base model faces north, which for anything on a wall is the way out from it (its back,
z = 16, against the wall). A roof step faces the way the roof rises: its tall side is north,
and it comes down in four 4/16 steps to the south, one shingle course a step, so a run of them
one block up and one in makes a 45 degree roof with real courses. Under each step (one block
down) an _under piece fills the roof's thickness; the eave piece carries the fascia and gutter.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import yard  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()

# roof kinds: how each is painted and its colour
ROOFS = {
    "charcoal": ("shingle", (66, 66, 68)),
    "grey": ("shingle", (118, 116, 108)),
    "brown": ("shingle", (92, 70, 54)),
    "green": ("shingle", (70, 86, 72)),
    "red": ("shingle", (112, 60, 48)),
    "slate": ("slate", (74, 78, 86)),
    "tin_red": ("tin", (128, 52, 42)),
    "tin_green": ("tin", (64, 96, 72)),
    "tin_galv": ("tin", (146, 148, 144)),
}
SHUTTERS = {"black": (34, 36, 36), "green": (40, 64, 46), "maroon": (92, 36, 36), "blue": (64, 82, 100)}
DOORS = {"red": (126, 36, 30), "green": (36, 70, 50), "navy": (36, 50, 80), "white": (208, 204, 192),
         "black": (34, 34, 34), "oak": None}
WHITE = (212, 208, 196)
HAINT = (146, 188, 192)


# ---------------------------------------------------------------- painting helpers

def paint(seed, rgb, peel=0.25, grain=True, vertical=False):
    """Old exterior paint on wood: brush grain, dirt, flakes gone to the grey wood under it."""
    t = F.plain(seed, rgb, 0.035, 0.35)
    if grain:
        g = P.tnoise(t.rng, 32) * 0.5 + P.tnoise(t.rng, 16) * 0.5
        lines = np.sin(np.arange(N) * 1.9 + t.rng.uniform(0, 6))[:, None] * 0.02
        g = (g * 0.04 + lines)
        if vertical:
            g = g.T
        t.c *= (1 + g)[..., None]
    if peel:
        n = P.fbm(t.rng, ((8, 1.0), (16, 0.6), (32, 0.4)))
        m = np.clip((n - (0.62 - peel)) * 6, 0, 1)
        wood = np.array((112, 104, 92), np.float32) * (0.85 + 0.3 * (P.fbm(t.rng) * 0.5 + 0.5))[..., None]
        t.c = t.c * (1 - m[..., None]) + wood * m[..., None]
        edge = np.clip(m * (1 - m) * 4, 0, 1)
        t.c *= (1 - edge * 0.25)[..., None]
        t.h -= m * 0.08
        t.smooth = t.smooth * (1 - m * 0.5)
    t.grime(0.22)
    t.streaks(0.5, 0.82)
    return t


def granules(seed, rgb):
    """Asphalt shingle surface: mineral granules, a blend of tones."""
    t = F.plain(seed, rgb, 0.08, 0.15)
    t.speckle(0.18, tuple(min(255, v * 1.35 + 10) for v in rgb), 0.35)
    t.speckle(0.18, tuple(v * 0.55 for v in rgb), 0.4)
    t.h += P.tnoise(t.rng, 32) * 0.05
    t.poro[:] = 50
    return t


def tabs(t, band, offset, width, slot=0.32, shade=(0.86, 1.12)):
    """One course's tabs across a band of rows: each a slightly different tone, the cut-outs dark."""
    y0, y1 = band * 16, band * 16 + 16
    x = offset % width - width
    while x < N:
        a, b = int(round(x)), int(round(x + width))
        k = t.rng.uniform(*shade)
        for xx in range(a, b):
            t.c[y0:y1, xx % N] *= k
        for xx in (a, a + 1):
            m = 0.4 if xx == a + 1 else slot
            t.c[y0 + 3:y1, xx % N] *= m
            t.h[y0 + 3:y1, xx % N] -= 0.2
        x += width


def algae(t, amount):
    """The black streaks Gloeocapsa leaves down an old roof, heaviest under the ridge."""
    cols = np.clip(P.tnoise(t.rng, 16)[0] * 0.5 + 0.5, 0, 1) ** 2
    cols = np.where(t.rng.random(N) < 0.25, cols + 0.5, cols)
    s = P.blur(np.clip(cols[None, :] * np.ones((N, 1)), 0, 1) * amount, 1)
    t.c *= (1 - 0.55 * s)[..., None]


def moss(t, amount, seed):
    """Moss cushions in the cut-outs and along the top of each course, where it stays damp."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    m = np.zeros((N, N), np.float32)
    for _ in range(int(18 * amount)):
        cx, cy = rng.uniform(0, N), rng.integers(0, 4) * 16 + rng.uniform(0, 5)
        r = rng.uniform(1.5, 4.5)
        m = np.maximum(m, F.blob_mask(rng, cx, cy, r, rough=0.7) * rng.uniform(0.5, 0.9))
    n = P.fbm(rng, ((16, 1.0), (32, 0.6)))
    col = np.array((58, 66, 34), np.float32) * (0.7 + 0.5 * (n * 0.5 + 0.5))[..., None]
    t.c = t.c * (1 - m[..., None]) + col * m[..., None]
    t.h += m * 0.15
    t.smooth *= 1 - m * 0.5
    return t


def roof_tex(kind, part, seed, variant=None):
    """A roof's tread (the top of each step) or riser (its face), four courses a block."""
    style, rgb = ROOFS[kind]
    rng = np.random.default_rng(seed)
    if style == "shingle":
        t = granules(seed, rgb)
        width = N / 3
        for band in range(4):
            tabs(t, band, band * width / 2 + rng.uniform(-2, 2), width)
        algae(t, 0.55 if kind in ("grey", "green") else 0.4)
    elif style == "slate":
        t = F.plain(seed, rgb, 0.05, 0.45)
        t.h += P.tnoise(t.rng, 32) * 0.04
        for band in range(4):
            tabs(t, band, band * 8 + rng.uniform(-1, 1), 16, slot=0.25, shade=(0.78, 1.18))
        t.speckle(0.02, (150, 150, 140), 0.3)  # lichen
    else:
        t = F.plain(seed, rgb, 0.04, 0.5)
        t.f0[:] = 60 if kind == "tin_galv" else 30
        for x in range(0, N, 16):  # the standing seams
            F.area(t, x, 0, x + 1, N, mul=1.35, dh=0.3)
            F.area(t, x + 1, 0, x + 3, N, mul=1.12, dh=0.3)
            F.area(t, x + 3, 0, x + 4, N, mul=0.6)
        rust = 0.55 if kind == "tin_galv" else 0.25
        n = P.fbm(rng, ((4, 1.0), (8, 0.7), (16, 0.5), (32, 0.3)))
        m = np.clip((n - (0.55 - rust)) * 4, 0, 1)
        col = np.array((110, 60, 34), np.float32) * (0.8 + 0.4 * (P.fbm(rng) * 0.5 + 0.5))[..., None]
        t.c = t.c * (1 - m[..., None]) + col * m[..., None]
        t.smooth *= 1 - m * 0.6
        t.f0 = t.f0 * (1 - m) + 10 * m
        t.streaks(0.7, 0.75)
    for band in range(4):
        y0 = band * 16
        if part == "tread":
            F.area(t, 0, y0, N, y0 + 2, mul=0.72)  # in the shadow of the course above
            F.area(t, 0, y0 + 15, N, y0 + 16, mul=1.12)
        else:
            F.area(t, 0, y0, N, y0 + 1, mul=1.1)
            F.area(t, 0, y0 + 13, N, y0 + 16, mul=0.78)
            F.area(t, 0, y0 + 15, N, y0 + 16, mul=0.55)  # the butt's shadow line
    if variant == "moss":
        moss(t, 1.0, seed + 7)
    elif variant == "patch" and style == "tin":
        # rusted through: ragged holes with a rust rim
        for _ in range(3):
            m = F.blob_mask(rng, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(3, 6), rough=0.8)
            t.mix(np.clip(m * 2, 0, 1), (96, 50, 28), 1.0)
            t.mix(np.clip(m * 2 - 1, 0, 1), (14, 10, 8), 1.0)
            t.h -= np.clip(m * 2 - 1, 0, 1) * 0.5
    elif variant == "patch":
        # blown-off shingles: tar paper under them, nail heads, a hole into the decking
        for _ in range(3):
            band = int(rng.integers(0, 4))
            x0 = int(rng.uniform(0, N - 20))
            w = int(rng.uniform(10, 22))
            F.area(t, x0, band * 16 + 1, x0 + w, band * 16 + 16, (30, 30, 30), h=0.6, smooth=0.25)
            for nx in range(x0 + 3, x0 + w - 2, 6):
                F.area(t, nx, band * 16 + 9, nx + 1, band * 16 + 10, (120, 118, 110), h=0.8)
        cx, cy = rng.uniform(12, 52), rng.integers(1, 3) * 16 + 8
        m = F.blob_mask(rng, cx, cy, 6, rough=0.7)
        t.mix(np.clip(m * 2, 0, 1), (118, 92, 60), 1.0)  # the decking showing
        t.mix(np.clip(m * 2.5 - 1.4, 0, 1), (16, 12, 10), 1.0)  # rotted through
        t.h -= m * 0.3
    return t


def tarp(part, seed):
    """A blue tarp nailed over a leak, creased and faded, its grommets and a strap of lath."""
    t = F.plain(seed, (48, 92, 150), 0.06, 0.55)
    n = P.tnoise(t.rng, 8) * 0.6 + P.tnoise(t.rng, 16) * 0.4
    t.c *= (1 + n * 0.18)[..., None]
    for _ in range(9):  # creases
        y = int(t.rng.uniform(0, N))
        F.area(t, 0, y, N, y + 1, mul=1.25)
        F.area(t, 0, y + 1, N, y + 2, mul=0.75)
    t.grime(0.25)
    t.streaks(0.4, 0.8)
    if part == "tread":
        F.area(t, 0, 28, N, 33, (122, 104, 78), h=0.9)  # a lath batten holding it down
        for x in range(6, N, 16):
            F.area(t, x, 30, x + 1, 31, (60, 60, 60))
    return t


def rake(profile, side, seed):
    """A roof edge seen end-on: a white barge board four steps deep along the slope, the
    shadowed underside of the overhang below it."""
    t = paint(seed, WHITE, 0.2)
    shade = F.plain(seed + 1, (70, 66, 60), 0.05, 0.2)
    m = np.zeros((N, N), bool)
    for k, height in enumerate(profile):
        cols = slice(16 * k, 16 * k + 16) if side == "w" else slice(N - 16 * k - 16, N - 16 * k)
        top = (16 - height) * 4
        m[top + 16:, cols] = True
        F.area(t, cols.start, top, cols.stop, top + 1, mul=1.12)
        F.area(t, cols.start, top + 15, cols.stop, top + 16, mul=0.7)
    t.c[m] = shade.c[m]
    t.h[m] = 0.6
    return t


def soffit(seed):
    """Vented aluminium soffit panels, dirty and stained by the gutter above."""
    t = F.plain(seed, (196, 194, 184), 0.03, 0.45)
    for y in range(0, N, 8):
        F.area(t, 0, y, N, y + 1, mul=0.75, dh=-0.1)
    for y in range(3, N, 16):
        for x in range(2, N, 3):
            F.area(t, x, y, x + 1, y + 2, (40, 40, 40))
    t.grime(0.35)
    F.stain(t, 20, 40, 14, (120, 100, 70), 0.3)
    return t


def gutter(seed):
    """A white K-style gutter's face: its ogee curves as bands, dents, a rust run, moss at the lip."""
    t = F.plain(seed, (206, 204, 196), 0.03, 0.55)
    t.f0[:] = 40
    for y, k in ((0, 1.2), (1, 1.1), (6, 0.8), (7, 1.15), (14, 0.85), (24, 1.1), (40, 0.88)):
        F.area(t, 0, y, N, y + 1 if y < 40 else y + 4, mul=k)
    t.grime(0.3)
    t.streaks(0.9, 0.7)
    for _ in range(3):
        x = int(t.rng.uniform(0, N))
        F.area(t, x, 0, x + 3, N, mul=0.85)
    return t


def gutter_top(seed):
    """Inside the gutter: black muck, rotted leaves, a tennis ball."""
    t = F.plain(seed, (34, 30, 24), 0.1, 0.6)
    rng = np.random.default_rng(seed)
    for _ in range(40):
        x, y = rng.uniform(0, N, 2)
        col = rng.choice([(96, 70, 34), (120, 84, 40), (70, 56, 30), (84, 92, 44)])
        F.disc(t, x, y, rng.uniform(1.5, 3.5), col, h=0.85)
    F.area(t, 0, 0, N, 3, (206, 204, 196), h=1.0)
    F.area(t, 0, N - 3, N, N, (206, 204, 196), h=1.0)
    return t


def roof_deck(seed):
    """The roof from the attic: OSB decking between the rafters."""
    t = F.mat("Chipboard004", (150, 124, 88), 1.0, seed=seed)
    for x in (0, 32):
        F.area(t, x, 0, x + 6, N, (112, 84, 56), h=1.0)
        F.area(t, x + 6, 0, x + 7, N, mul=0.6)
    t.grime(0.3)
    return t


def ridge_cap(kind, seed):
    t = roof_tex(kind, "riser", seed)
    for x in range(0, N, 11):  # the overlapping cap pieces
        F.area(t, x, 0, x + 1, N, mul=0.5)
        F.area(t, x + 1, 0, x + 2, N, mul=1.15)
    return t


def porch_ceiling(seed):
    """Beadboard painted haint blue, the way Kentucky porches are: to fool wasps and haints."""
    t = paint(seed, HAINT, 0.18, vertical=False)
    for y in range(0, N, 8):
        F.area(t, 0, y, N, y + 1, mul=0.62, dh=-0.15)
        F.area(t, 0, y + 4, N, y + 5, mul=0.85, dh=-0.05)
    F.stain(t, 40, 20, 12, (120, 110, 80), 0.35)
    # a wasp nest's papery stub and cobwebs in one corner
    F.disc(t, 10, 52, 3, (150, 140, 120), h=1.0)
    return t


def porch_boards(seed):
    """Grey porch paint on tongue-and-groove boards, worn through to the wood where people walked."""
    t = paint(seed, (124, 126, 122), 0.4, vertical=True)
    for x in range(0, N, 11):
        F.area(t, x, 0, x + 1, N, mul=0.45, dh=-0.2)
    for y in (int(t.rng.uniform(0, N)), int(t.rng.uniform(0, N))):
        for x in range(0, N, 11):
            F.area(t, x + 1, y, x + 11, y + 1, mul=0.7, dh=-0.1)
    F.stain(t, 30, 30, 18, (90, 80, 64), 0.2, tide=False)
    return t


def louvres(seed, rgb):
    """A shutter's louvres: a slat every four texels, lit along its top, shadowed under it."""
    t = paint(seed, rgb, 0.15)
    for y in range(0, N, 4):
        F.area(t, 0, y, N, y + 1, mul=1.3, dh=0.1)
        F.area(t, 0, y + 3, N, y + 4, mul=0.4, dh=-0.2)
    return t


def door_tex(colour, half, seed):
    """A six-panel front door; the top half has four small lites above its panels."""
    rgb = DOORS[colour]
    if rgb is None:
        t = F.wood(seed, "oak", rot=True)
    else:
        t = paint(seed, rgb, 0.12, vertical=True)
    t.frame(2, tuple(v * 0.8 for v in t.c.mean((0, 1))), 0.9)
    if half == "top":
        for x0, x1 in ((7, 30), (34, 57)):
            F.gap(t, x0, 28, x1, N + 1)
            F.edges(t, x0 + 1, 29, x1 - 1, N, light=1.12, dark=0.7)
            F.sunk(t, x0 + 4, 33, x1 - 4, N)
        for i, x in enumerate((7, 20, 34, 47)):  # the lites
            F.area(t, x, 6, x + 10, 22, (150, 160, 166), h=0.5, smooth=0.95)
            t.alpha[6:22, x:x + 10] = 70
            F.area(t, x, 6, x + 10, 7, mul=0.6)
        t.grime(0.2)
    else:
        for x0, x1 in ((7, 30), (34, 57)):
            F.gap(t, x0, -1, x1, 40)
            F.edges(t, x0 + 1, 0, x1 - 1, 39, light=1.12, dark=0.7)
            F.sunk(t, x0 + 4, 0, x1 - 4, 34)
        F.area(t, 2, 54, 62, 62, (170, 140, 80), h=1.0, smooth=0.7, f0=230)  # brass kick plate
        F.area(t, 2, 54, 62, 55, mul=1.3)
        F.knob(t, 54, 6, r=2)
        F.area(t, 53, 0, 56, 2, (172, 140, 78), h=1.0, smooth=0.7, f0=230)  # the deadbolt
        F.fingers(t, 54, 6, 9, 0.3)
        t.grime(0.25)
        F.area(t, 0, 58, N, N, mul=0.75)  # kicked and scuffed
    return t


def numbers(text, seed):
    """Brass house numbers screwed to a painted board."""
    t = paint(seed, (44, 44, 42), 0.1)
    m = F.text_mask(text, F.SANS, 16, 56, 26, seed=seed)
    h, w = m.shape
    F.paste(t, m, 4, 19, (186, 150, 82), 1.0, h=1.0, smooth=0.7)
    t.frame(2, (180, 176, 164), 0.95)
    return t


def doormat(seed, bloody=False):
    t = F.plain(seed, (110, 84, 52), 0.1, 0.1)
    for y in range(0, N, 2):
        F.area(t, 0, y, N, y + 1, mul=0.82)
    m = F.text_mask("WELCOME", F.SANS, 8, 52, 14, seed=seed)
    F.paste(t, m, 6, 25, (40, 30, 20), 0.85)
    t.frame(3, (60, 44, 30), 0.9)
    t.grime(0.4)
    if bloody:
        F.blood(t, 44, 46, 9, 0.7)
    return t


def notice(seed):
    """The notice nailed to the door: quarantine, by order of the Guard."""
    t = F.plain(seed, (222, 216, 196), 0.05, 0.2)
    F.area(t, 0, 0, N, 12, (170, 30, 26))
    m = F.text_mask("QUARANTINE", F.SANS, 7, 60, 10, seed=seed)
    F.paste(t, m, 2, 1, (240, 236, 226), 1.0)
    for y, text in ((16, "NO ENTRY"), (28, "BY ORDER OF"), (38, "KY NATL GUARD"), (52, "7/11/93")):
        m = F.text_mask(text, F.SANS, 6, 58, 9, seed=seed + y)
        F.paste(t, m, 3, y, (30, 28, 26), 0.9)
    F.stain(t, 50, 50, 10, (150, 120, 70), 0.4)
    t.streaks(0.5, 0.8)
    return t


def galv(seed, ribs=True):
    """Galvanised steel: a trash can's ribbed side."""
    t = F.plain(seed, (150, 154, 152), 0.06, 0.5)
    t.f0[:] = 230
    t.speckle(0.05, (190, 192, 188), 0.3)
    if ribs:
        for y in (12, 30, 48):
            F.area(t, 0, y, N, y + 2, mul=1.25, dh=0.2)
            F.area(t, 0, y + 2, N, y + 4, mul=0.7)
    t.grime(0.35)
    t.streaks(0.5, 0.7)
    return t


def meter(seed):
    """The electric meter's glass dome, dials and the utility's seal."""
    t = F.plain(seed, (150, 156, 156), 0.04, 0.6)
    F.disc(t, 32, 32, 26, (200, 206, 206), smooth=0.9)
    F.disc(t, 32, 32, 26, ring=2, mul=0.6)
    for x in (16, 26, 38, 48):
        F.disc(t, x, 26, 4, (236, 232, 220))
        F.area(t, x, 23, x + 1, 27, (20, 20, 20))
    F.area(t, 18, 38, 46, 44, (30, 30, 30))
    return t


def ac_grille(seed):
    """The condenser's top: a fan behind a wire guard, leaves caught in it."""
    t = F.plain(seed, (90, 92, 90), 0.04, 0.4)
    F.disc(t, 32, 32, 28, (26, 26, 26))
    for r in range(6, 30, 4):
        F.disc(t, 32, 32, r, ring=1, col=(150, 152, 150))
    for a in range(0, 360, 90):
        x, y = 32 + np.cos(np.radians(a)) * 12, 32 + np.sin(np.radians(a)) * 12
        F.disc(t, x, y, 5, (60, 62, 60))
    rng = np.random.default_rng(seed)
    for _ in range(14):
        F.disc(t, rng.uniform(4, 60), rng.uniform(4, 60), rng.uniform(1.5, 3), (110, 80, 40))
    return t


def ac_fins(seed):
    t = F.plain(seed, (150, 150, 146), 0.04, 0.4)
    for x in range(0, N, 2):
        F.area(t, x, 6, x + 1, N - 6, mul=0.55)
    t.frame(5, (120, 122, 118), 0.95)
    t.grime(0.4)
    t.streaks(0.6, 0.7)
    return t


def flowerbox(seed):
    t = paint(seed, (196, 192, 180), 0.35, vertical=False)
    for y in (2, 14):
        F.area(t, 0, y, N, y + 1, mul=0.6)
    t.streaks(0.9, 0.6)
    return t


def mulch(seed, dead=True):
    t = F.plain(seed, (56, 40, 28), 0.15, 0.1)
    rng = np.random.default_rng(seed)
    for _ in range(26):
        col = (96, 80, 50) if dead else (60, 96, 44)
        F.disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(1, 2.5), col)
    return t


def chimney_crown(seed):
    t = F.mat("concrete_floor_worn_001", (128, 126, 120), 1.2, seed=seed)
    t.grime(0.5, (20, 18, 16))
    return t


def flue_clay(seed):
    t = F.plain(seed, (160, 88, 56), 0.08, 0.2)
    t.grime(0.4, (16, 14, 12))
    F.area(t, 0, 0, N, 20, mul=0.45)  # soot down from the mouth
    return t


def soot_top(seed):
    """The flue's mouth from above: black."""
    t = F.plain(seed, (24, 22, 20), 0.1, 0.1)
    F.disc(t, 32, 32, 18, (8, 8, 8))
    return t


def textures():
    T = {}
    for i, kind in enumerate(ROOFS):
        s = 4000 + i * 20
        T[f"ext_{kind}_tread"] = lambda k=kind, s=s: roof_tex(k, "tread", s)
        T[f"ext_{kind}_riser"] = lambda k=kind, s=s: roof_tex(k, "riser", s)
        T[f"ext_{kind}_tread_moss"] = lambda k=kind, s=s: roof_tex(k, "tread", s, "moss")
        T[f"ext_{kind}_riser_moss"] = lambda k=kind, s=s: roof_tex(k, "riser", s, "moss")
        T[f"ext_{kind}_tread_patch"] = lambda k=kind, s=s: roof_tex(k, "tread", s + 3, "patch")
        T[f"ext_{kind}_riser_patch"] = lambda k=kind, s=s: roof_tex(k, "riser", s + 3, "patch")
        T[f"ext_{kind}_cap"] = lambda k=kind, s=s: ridge_cap(k, s + 9)
    T["ext_tarp_tread"] = lambda: tarp("tread", 4300)
    T["ext_tarp_riser"] = lambda: tarp("riser", 4301)
    T["ext_rake_e"] = lambda: rake((16, 12, 8, 4), "e", 4310)
    T["ext_rake_w"] = lambda: rake((16, 12, 8, 4), "w", 4311)
    T["ext_rake_ridge"] = lambda: rake((4, 8, 8, 4), "w", 4312)
    T["ext_soffit"] = lambda: soffit(4313)
    T["ext_gutter"] = lambda: gutter(4314)
    T["ext_gutter_top"] = lambda: gutter_top(4315)
    T["ext_roof_deck"] = lambda: roof_deck(4316)
    # white paintwork from the photo set, the same as the yard's pickets (yard.white_paint)
    T["ext_fascia"] = lambda: yard.white_paint(4317)
    T["ext_trim_white"] = lambda: yard.upright(yard.white_paint(4318))
    T["ext_trim_brown"] = lambda: paint(4319, (78, 58, 42), 0.12, vertical=True)
    T["ext_alu"] = lambda: F.steel(4320, (176, 178, 176))
    T["ext_porch_ceiling"] = lambda: porch_ceiling(4321)
    T["ext_porch_boards"] = lambda: porch_boards(4322)
    T["ext_post"] = lambda: yard.upright(yard.white_paint(4323))
    for i, (c, rgb) in enumerate(SHUTTERS.items()):
        T[f"ext_louvre_{c}"] = lambda rgb=rgb, i=i: louvres(4330 + i, rgb)
        T[f"ext_paint_{c}"] = lambda rgb=rgb, i=i: paint(4335 + i, rgb, 0.15, vertical=True)
    for i, c in enumerate(DOORS):
        T[f"ext_door_{c}_top"] = lambda c=c, i=i: door_tex(c, "top", 4340 + i)
        T[f"ext_door_{c}_bottom"] = lambda c=c, i=i: door_tex(c, "bottom", 4340 + i)
    for i, n in enumerate(("1406", "27", "318", "2209", "51", "760")):
        T[f"ext_number_{i}"] = lambda n=n, i=i: numbers(n, 4360 + i)
    T["ext_doormat"] = lambda: doormat(4370)
    T["ext_doormat_blood"] = lambda: doormat(4386, True)
    T["ext_notice"] = lambda: notice(4371)
    T["ext_galv"] = lambda: galv(4372)
    T["ext_galv_lid"] = lambda: galv(4373, ribs=False)
    T["ext_meter"] = lambda: meter(4374)
    T["ext_ac_grille"] = lambda: ac_grille(4375)
    T["ext_ac_fins"] = lambda: ac_fins(4376)
    T["ext_flowerbox"] = lambda: flowerbox(4377)
    T["ext_mulch_dead"] = lambda: mulch(4378)
    T["ext_crown"] = lambda: chimney_crown(4379)
    T["ext_flue_clay"] = lambda: flue_clay(4380)
    T["ext_soot_top"] = lambda: soot_top(4381)
    T["ext_hose"] = lambda: F.plain(4382, (52, 92, 46), 0.08, 0.5)
    T["ext_black_metal"] = lambda: F.steel(4383, (40, 40, 40))
    T["ext_glass_dark"] = lambda: F.plain(4384, (60, 62, 58), 0.05, 0.9)
    T["ext_cooler"] = lambda: F.plain(4385, (176, 44, 36), 0.04, 0.6)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def steps(top, riser, deck="ext_soffit", rake=("ext_rake_e", "ext_rake_w"), n=4):
    """A roof step's courses, tallest at the north: tread on top, riser facing down the slope."""
    tex = {"up": top, "south": riser, "north": deck, "down": deck, "east": rake[0], "west": rake[1]}
    return [[0, 0, 4 * k, 16, 16 - 4 * k, 4 * k + 4, tex] for k in range(n)]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, render=None, light=0, loot=None):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        if light:
            spec["light"] = light
        if loot:
            spec["loot"] = loot
        out[name] = spec

    # ---- roofs
    gut = {"up": "ext_gutter_top", "side": "ext_gutter", "down": "ext_gutter"}
    fascia = "ext_fascia"
    for kind, (style, _) in ROOFS.items():
        snd = "METAL" if style == "tin" else "STONE"
        tread, riser = f"ext_{kind}_tread", f"ext_{kind}_riser"
        title = {"shingle": "Shingled Roof", "slate": "Slate Roof", "tin": "Tin Roof"}[style]
        add(f"ext_roof_{kind}", steps(tread, riser), title, snd)
        for v in ("moss", "patch"):
            add(f"ext_roof_{kind}_{v}", steps(f"{tread}_{v}", f"{riser}_{v}"), title, snd)
        eave = steps(tread, riser, n=3) + [
            [0, 0, 12, 16, 4, 14, {"up": tread, "side": fascia, "down": "ext_soffit"}],
            [0, 3.5, 12, 16, 4.5, 15.5, {"up": tread, "side": riser, "down": "ext_soffit"}]]
        add(f"ext_roof_{kind}_eave", eave + [[0, 1, 14, 16, 4, 17.5, gut]], title, snd)
        add(f"ext_roof_{kind}_eave_bare", eave, title, snd)
        ridge = {"up": tread, "north": riser, "south": riser, "down": "ext_soffit", "east": "ext_rake_ridge",
                 "west": "ext_rake_ridge"}
        add(f"ext_roof_{kind}_ridge", [[0, 0, 0, 16, 4, 4, ridge], [0, 0, 4, 16, 8, 12, ridge],
                                       [0, 0, 12, 16, 4, 16, ridge], [0, 8, 3.5, 16, 9.5, 12.5, f"ext_{kind}_cap"]],
            title, snd)
        # a hipped roof's outer corner: high to the north and the east, both slopes' courses meeting
        # on the diagonal; the _eave one carries the gutter round both low sides
        hip = []
        for j in range(4):
            for i in range(4):
                h = 16 - 4 * max(j, 3 - i)
                hip.append([4 * i, 0, 4 * j, 4 * i + 4, h, 4 * j + 4,
                            {"up": tread, "south": riser, "west": riser, "north": "ext_soffit",
                             "east": "ext_soffit", "down": "ext_soffit"}])
        add(f"ext_roof_{kind}_hip", hip, title, snd)
        lip = {"up": tread, "side": fascia, "down": "ext_soffit"}
        add(f"ext_roof_{kind}_hip_eave", hip + [
            [0, 3.5, 12, 16, 4.5, 15.5, lip], [0.5, 3.5, 0, 4, 4.5, 12, lip],
            [0, 1, 14, 16, 4, 17.5, gut], [-1.5, 1, 0, 2, 4, 17.5, gut]], title, snd)
        add(f"ext_roof_{kind}_crest",steps(tread, riser) + [[0, 16, 0, 16, 17.5, 3.5, f"ext_{kind}_cap"]], title, snd)
        porch = {"up": tread, "down": "ext_porch_ceiling", "side": fascia}
        add(f"ext_porch_roof_{kind}", [[0, 0, 0, 16, 4, 16, porch]], "Porch Roof", snd)
        add(f"ext_porch_roof_{kind}_edge", [[0, 0, 0, 16, 4, 16, porch], [0, 1, -2.5, 16, 4, 0, gut]], "Porch Roof", snd)
    add("ext_roof_tarp", steps("ext_tarp_tread", "ext_tarp_riser"), "Tarp", "WOOL")
    # the block under an odd-span ridge: the barge boards' apex outside, the ridge beam in the attic
    out["ext_apex"] = {"tex": {"side": "ext_trim_white", "up": "ext_roof_deck", "down": "ext_soffit"},
                       "title": "Barge Board", "sound": "WOOD"}
    add("ext_roof_under", [[0, 16 - 4 * k, 4 * k, 16, 16, 4 * k + 4, "ext_roof_deck"] for k in (1, 2, 3)], "Roof Decking", "WOOD")
    # a length of gutter hanging off the eave by one end
    add("ext_gutter_hanging", [[6, 0, 12, 9, 16, 16, gut], [5, 15, 12, 10, 16, 16, "ext_gutter"]], "Gutter", "METAL")

    # ---- downspouts, against the wall at their back
    pipe = "ext_gutter"
    add("ext_downspout", [[6.5, 0, 13, 9.5, 16, 16, pipe], [6, 8, 15.5, 10, 9, 16, pipe]], "Downspout", "METAL")
    add("ext_downspout_top", [[6.5, 0, 13, 9.5, 6, 16, pipe], [6.5, 4, 9, 9.5, 10, 13.5, pipe],
                              [6.5, 8, 5, 9.5, 14, 9.5, pipe], [6.5, 12, 1, 9.5, 16, 5.5, pipe]], "Downspout", "METAL")
    add("ext_downspout_shoe", [[6.5, 3, 13, 9.5, 16, 16, pipe], [6.5, 0.5, 8, 9.5, 3.5, 16, pipe],
                               [5, 0, 2, 11, 0.6, 12, "ext_crown"]], "Downspout", "METAL")

    # ---- windows: a double-hung sash in the wall, its casing and sill proud of it outside
    def window(trim, glass, lo, grille, boards):
        jamb = [[0, 0, -1, 2.5, 16, 16, trim], [13.5, 0, -1, 16, 16, 16, trim]]
        b = list(jamb)
        if lo:
            b += [[0, 0, -2.5, 16, 1.5, 16.5, trim]]  # the sill, and the stool inside
            sash = [[2.5, 1.5, 7, 13.5, 3.5, 9, trim], [2.5, 1.5, 7, 3.5, 16, 9, trim],
                    [12.5, 1.5, 7, 13.5, 16, 9, trim], [2.5, 14.5, 7, 13.5, 16, 9.5, trim]]
            pane = [3.5, 3.5, 7.75, 12.5, 14.5, 8.25]
        else:
            b += [[0, 13.5, -1.5, 16, 16, 16, trim], [0, 15.5, -2, 16, 16.5, 0, trim]]  # head and drip cap
            sash = [[2.5, 0, 6.5, 13.5, 1.5, 8.5, trim], [2.5, 0, 6.5, 3.5, 13.5, 8.5, trim],
                    [12.5, 0, 6.5, 13.5, 13.5, 8.5, trim], [2.5, 12, 6.5, 13.5, 13.5, 8.5, trim]]
            pane = [3.5, 1.5, 7.25, 12.5, 12, 7.75]
        if glass is not None:
            b += sash
            b.append(pane + [glass])
            if grille == "six":
                z0, z1 = pane[2] - 0.3, pane[5] + 0.3
                b += [[6.2, pane[1], z0, 6.8, pane[4], z1, trim], [9.2, pane[1], z0, 9.8, pane[4], z1, trim],
                      [3.5, (pane[1] + pane[4]) / 2 - 0.3, z0, 12.5, (pane[1] + pane[4]) / 2 + 0.3, z1, trim]]
        if boards:
            ys = (3, 9.5) if lo else (1.5, 8)
            for i, y in enumerate(ys):
                b.append([0.5 + i, y, -2.5, 15.5 - i, y + 3.5, -1, "boards"])
        return b

    for frame, trim in (("white", "ext_trim_white"), ("brown", "ext_trim_brown"), ("alu", "ext_alu")):
        for grille in (("six", "one") if frame != "alu" else ("one",)):
            for state, glass in (("glass", "window"), ("lit", "window_lit"), ("broken", "window_broken"),
                                 ("empty", None), ("boarded", "window")):
                for half in ("lo", "hi"):
                    name = f"ext_win_{frame}_{grille}_{state}_{half}"
                    spec_boxes = window(trim, glass, half == "lo", grille, state == "boarded")
                    add(name, spec_boxes, "Window", "GLASS", render="translucent" if glass else None,
                        light=12 if state == "lit" else 0)

    # ---- shutters beside a window, flat to the wall
    for c in SHUTTERS:
        paint_, louvre = f"ext_paint_{c}", f"ext_louvre_{c}"
        for half in ("lo", "hi"):
            b = [[2, 0, 14.5, 3.5, 16, 16, paint_], [12.5, 0, 14.5, 14, 16, 16, paint_],
                 [3.5, 0, 15, 12.5, 16, 15.8, louvre]]
            b.append([3.5, 0, 14.5, 12.5, 1.5, 16, paint_] if half == "lo" else [3.5, 14.5, 14.5, 12.5, 16, 16, paint_])
            b.append([3.5, 7.5, 14.6, 12.5, 8.5, 16, paint_])
            add(f"ext_shutter_{c}_{half}", b, "Shutter", "WOOD")

    # ---- doors (vanilla door models, our paint)
    for c in DOORS:
        out[f"ext_door_{c}"] = {"door": "wood", "tex": {"top": f"ext_door_{c}_top", "bottom": f"ext_door_{c}_bottom"},
                                "title": "Front Door"}

    # ---- the porch
    post = "ext_post"
    add("ext_post_base", [[4, 0, 4, 12, 3, 12, post], [5, 3, 5, 11, 16, 11, post]], "Porch Post", "WOOD", rotate=None)
    add("ext_post", [[5, 0, 5, 11, 16, 11, post], [4.5, 7, 4.5, 11.5, 8, 11.5, post]], "Porch Post", "WOOD", rotate=None)
    add("ext_post_cap", [[5, 0, 5, 11, 12, 11, post], [4, 12, 4, 12, 14, 12, post], [3, 14, 3, 13, 16, 13, post],
                         [6.5, 13, 0, 9.5, 16, 16, post]], "Porch Post", "WOOD", rotate="axis")
    # the beam along the posts' tops, under the porch roof
    add("ext_beam", [[6, 12, 0, 10, 16, 16, post]], "Porch Beam", "WOOD", rotate="axis")
    rail = [[6.5, 13, 0, 9.5, 15, 16, post], [7, 1.5, 0, 9, 3, 16, post]]
    for z in (1, 5, 9, 13):
        rail.append([7.25, 3, z, 8.75, 13, z + 1.5, post])
    add("ext_rail", rail, "Porch Railing", "WOOD", rotate="axis")
    add("ext_rail_broken", [[7, 1.5, 0, 9, 3, 16, post], [7.25, 3, 1, 8.75, 13, 2.5, post],
                            [6.5, 13, 0, 9.5, 15, 6, post], [7.25, 3, 9, 8.75, 8, 10.5, post]], "Porch Railing", "WOOD",
        rotate="axis")
    out["ext_porch_boards"] = {"tex": {"up": "ext_porch_boards", "down": "ext_porch_boards", "side": "hk_porch_skirt"},
                               "title": "Porch Boards", "sound": "WOOD"}
    # the back porches up a shop row (see Flats.porches): plank stairs, and a rail along one edge of a cell
    # (facing = that edge) so the boards beside it can still be walked
    tread = {"up": "ext_porch_boards", "down": "ext_porch_boards", "side": "ext_fascia"}
    stairs = [[1, 6, 0, 15, 8, 16, tread], [1, 14, 0, 15, 16, 8, tread], [1.5, 0, 14, 14.5, 6, 15.5, "ext_fascia"],
              [1.5, 8, 6, 14.5, 14, 7.5, "ext_fascia"]]
    for x0 in (0, 15):
        stairs += [[x0, 0, 0, x0 + 1, 16, 8, "ext_fascia"], [x0, 0, 8, x0 + 1, 8, 16, "ext_fascia"]]
    add("ext_stairs_wood", stairs, "Porch Stairs", "WOOD")
    edge = [[0, 13, 0, 16, 15, 3, post], [0, 1.5, 0.5, 16, 3, 2.5, post]]
    for x in (1, 5, 9, 13):
        edge.append([x, 3, 0.75, x + 1.5, 13, 2.25, post])
    add("ext_rail_edge", edge, "Porch Railing", "WOOD")
    # the same rail inside, round a walk-up's stairwell, in dark varnished wood
    add("ext_rail_edge_dark", [[b[0], b[1], b[2], b[3], b[4], b[5], "home_wood_dark"] for b in edge], "Banister", "WOOD")
    add("ext_rail_edge_broken", [[0, 1.5, 0.5, 16, 3, 2.5, post], [1, 3, 0.75, 2.5, 13, 2.25, post],
                                 [0, 13, 0, 7, 15, 3, post], [9, 3, 0.75, 10.5, 8, 2.25, post]], "Porch Railing", "WOOD")
    add("ext_porch_light", [[5, 13, 5, 11, 16, 11, "ext_black_metal"], [5.5, 9, 5.5, 10.5, 13, 10.5, "ext_glass_dark"]],
        "Porch Light", "METAL", rotate=None)
    add("ext_porch_light_lit", [[5, 13, 5, 11, 16, 11, "ext_black_metal"], [5.5, 9, 5.5, 10.5, 13, 10.5, "lamp_glass"]],
        "Porch Light", "METAL", rotate=None, light=11)
    wood = "home_wood_dark"
    add("ext_rocker", [[2, 0, 1, 3, 1.5, 15, wood], [13, 0, 1, 14, 1.5, 15, wood], [2, 1.5, 3, 3, 7, 4, wood],
                       [13, 1.5, 3, 14, 7, 4, wood], [2, 1.5, 12, 3, 7, 13, wood], [13, 1.5, 12, 14, 7, 13, wood],
                       [2, 7, 3, 14, 8, 13, wood], [2, 8, 12, 14, 22, 13.5, wood], [2, 10, 3, 3, 11, 13, wood],
                       [13, 10, 3, 14, 11, 13, wood]], "Rocking Chair", "WOOD")
    add("ext_swing_l", [[0, 5, 2, 16, 6, 13, wood], [0, 6, 12, 16, 13, 13.5, wood], [0.5, 6, 2, 1.5, 10, 13, wood],
                        [2, 6, 7, 3, 16, 8, "ext_black_metal"]], "Porch Swing", "WOOD")
    add("ext_swing_r", [[0, 5, 2, 16, 6, 13, wood], [0, 6, 12, 16, 13, 13.5, wood], [14.5, 6, 2, 15.5, 10, 13, wood],
                        [13, 6, 7, 14, 16, 8, "ext_black_metal"]], "Porch Swing", "WOOD")
    add("ext_swing_chain", [[2, 0, 7, 3, 16, 8, "ext_black_metal"], [13, 0, 7, 14, 16, 8, "ext_black_metal"]],
        "Chain", "METAL")
    add("ext_cooler", [[3, 0, 4, 13, 7, 12, "ext_cooler"], [3, 7, 4, 13, 8, 12, "home_white"],
                       [6, 8, 7.5, 10, 9, 8.5, "home_white"]], "Cooler", "WOOL", loot="home_fridge")

    # ---- on the walls
    add("ext_sconce", [[6, 4, 14, 10, 12, 16, "ext_black_metal"], [5.5, 3, 9, 10.5, 11, 14, "ext_glass_dark"],
                       [5, 11, 8.5, 11, 12.5, 14.5, "ext_black_metal"], [5, 2, 8.5, 11, 3, 14.5, "ext_black_metal"]],
        "Porch Light", "METAL")
    add("ext_sconce_lit", [[6, 4, 14, 10, 12, 16, "ext_black_metal"], [5.5, 3, 9, 10.5, 11, 14, "lamp_glass"],
                           [5, 11, 8.5, 11, 12.5, 14.5, "ext_black_metal"], [5, 2, 8.5, 11, 3, 14.5, "ext_black_metal"]],
        "Porch Light", "METAL", light=12)
    for i in range(6):
        add(f"ext_number_{i}", [[1, 4, 15, 15, 10, 16, f"ext_number_{i}"]], "House Number", "WOOD", decor=True)
    add("ext_notice", [[4, 2, 15.7, 12, 13, 16, "ext_notice"]], "Notice", "WOOL", decor=True)
    add("ext_meter", [[5, 4, 13, 11, 12, 16, "ext_meter"], [6, 5, 11, 10, 11, 13, "ext_glass_dark"],
                      [7, 0, 14, 9, 4, 16, "ext_alu"]], "Electric Meter", "METAL")
    add("ext_flowerbox", [[0.5, 9, 10, 15.5, 14, 16, "ext_flowerbox"], [1, 13.5, 10.5, 15, 14, 15.5, "ext_mulch_dead"],
                          [3, 14, 12, 7, 19, 14, "home_foliage_dead"], [9, 14, 11.5, 13, 18, 14.5, "home_foliage_dead"]],
        "Window Box", "WOOD")
    add("ext_gable_vent", [[2, 2, 15, 14, 14, 16, "ext_louvre_black"], [1.5, 1.5, 14.5, 14.5, 2.5, 16, "ext_trim_white"],
                           [1.5, 13.5, 14.5, 14.5, 14.5, 16, "ext_trim_white"], [1.5, 2.5, 14.5, 2.5, 13.5, 16, "ext_trim_white"],
                           [13.5, 2.5, 14.5, 14.5, 13.5, 16, "ext_trim_white"]], "Gable Vent", "WOOD")
    add("ext_frieze", [[0, 11, 14, 16, 16, 16, "ext_fascia"], [0, 10, 14.5, 16, 11, 16, "ext_fascia"]], "Frieze Board", "WOOD")
    add("ext_plinth", [[0, 0, 13, 16, 6, 16, "ext_crown"]], "Foundation", "STONE", decor=False)
    add("ext_plinth_vent", [[0, 0, 13, 4, 6, 16, "ext_crown"], [12, 0, 13, 16, 6, 16, "ext_crown"],
                            [4, 0, 13, 12, 1.5, 16, "ext_crown"], [4, 4.5, 13, 12, 6, 16, "ext_crown"],
                            [4, 1.5, 14.5, 12, 4.5, 15, "ext_louvre_black"]], "Foundation Vent", "STONE")
    add("ext_awning", [[0, 12, 6, 16, 13, 16, "ext_alu"], [0, 9, 5.5, 16, 12, 6.5, "ext_alu"],
                       [1, 9, 6, 2, 12, 16, "ext_alu"], [14, 9, 6, 15, 12, 16, "ext_alu"]], "Door Awning", "METAL")
    add("ext_hood", [[0, 10, 9, 16, 12, 16, "ext_trim_white"], [0, 12, 10, 16, 13, 16, "ext_trim_white"],
                     [1, 4, 13, 3, 10, 16, "ext_trim_white"], [13, 4, 13, 15, 10, 16, "ext_trim_white"]], "Door Hood", "WOOD")

    # ---- the chimney's crown and the wood stove's flue
    add("ext_crown", [[0, 0, 0, 16, 3, 16, "ext_crown"], [4.5, 3, 4.5, 11.5, 8, 11.5,
                                                         {"up": "ext_soot_top", "side": "ext_flue_clay", "down": "ext_flue_clay"}]],
        "Chimney Crown", "STONE", rotate=None)
    add("ext_flue", [[5.5, 0, 5.5, 10.5, 16, 10.5, "ext_alu"]], "Stove Flue", "METAL", rotate=None)
    # a flue standing on a flat roof below it (the trailer's furnace)
    add("ext_flue_low", [[5.5, -12, 5.5, 10.5, 16, 10.5, "ext_alu"], [4.5, -12, 4.5, 11.5, -11, 11.5, "ext_alu"]],
        "Stove Flue", "METAL", rotate=None)
    add("ext_flue_cap", [[5.5, 0, 5.5, 10.5, 8, 10.5, "ext_alu"], [7, 8, 7, 9, 10, 9, "ext_alu"],
                         [3.5, 10, 3.5, 12.5, 11, 12.5, "ext_alu"], [5, 11, 5, 11, 12, 11, "ext_alu"]], "Stove Flue", "METAL",
        rotate=None)
    # the TV aerial, on a mast strapped to the chimney or the gable
    add("ext_mast", [[7.25, 0, 7.25, 8.75, 16, 8.75, "ext_alu"]], "Antenna Mast", "METAL", rotate=None)
    # the mast's foot, reaching down onto the ridge cap below
    add("ext_mast_foot", [[7.25, -7, 7.25, 8.75, 16, 8.75, "ext_alu"], [5, -7, 5, 11, -6, 11, "ext_alu"],
                          [7.5, -6, 4, 8.5, -2, 12, "ext_alu"]], "Antenna Mast", "METAL", rotate=None)
    aerial = [[7.25, 0, 7.25, 8.75, 12, 8.75, "ext_alu"], [7.5, 10, -8, 8.5, 11, 24, "ext_alu"]]
    for z in (-6, -1, 4, 9, 14, 19):
        w = 7 - abs(z - 6) * 0.25
        aerial.append([8 - w, 10.2, z, 8 + w, 10.8, z + 0.6, "ext_alu"])
    add("ext_aerial", aerial, "TV Aerial", "METAL")

    # ---- round the yard
    add("ext_ac", [[1, 0, 1, 15, 13, 15, {"up": "ext_ac_grille", "side": "ext_ac_fins", "down": "ext_ac_fins"}],
                   [0.5, 13, 0.5, 15.5, 14, 15.5, "ext_alu"]], "Air Conditioner", "METAL", rotate=None)
    add("ext_trash_can", [[3, 0, 3, 13, 13, 13, {"up": "ext_galv_lid", "side": "ext_galv", "down": "ext_galv"}],
                          [2.5, 13, 2.5, 13.5, 14, 13.5, "ext_galv_lid"], [7, 14, 6, 9, 15, 10, "ext_galv_lid"]],
        "Trash Can", "METAL", rotate=None, loot="home_trash")
    add("ext_trash_can_tipped", [[0, 0, 3, 13, 10, 13, {"east": "ext_soot_top", "side": "ext_galv", "up": "ext_galv"}],
                                 [13, 0, 4, 16, 1, 12, "home_bin_bag"]], "Trash Can", "METAL", loot="home_trash")
    add("ext_trash_lid", [[2, 0, 2, 13, 1, 13, "ext_galv_lid"]], "Lid", "METAL", decor=True)
    add("ext_doormat", [[1, 0, 3, 15, 0.4, 13, "ext_doormat"]], "Doormat", "WOOL", decor=True)
    add("ext_doormat_blood", [[1, 0, 3, 15, 0.4, 13, "ext_doormat_blood"]], "Doormat", "WOOL", decor=True)
    add("ext_hose", [[3, 0, 11, 13, 10, 15, "ext_hose"], [5, 2, 15, 11, 8, 16, "ext_alu"]], "Hose Reel", "WOOL")
    add("ext_shrub_dead", [[2, 0, 2, 14, 9, 14, "home_foliage_dead"], [4, 9, 4, 12, 12, 12, "home_foliage_dead"]],
        "Dead Shrub", "GRASS", rotate=None, decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # keep everyone's order: rewritten pieces stay where they were, new ones go after their family
    kept, pending = {}, dict(made)
    for k, v in data["blocks"].items():
        if k.startswith("ext_"):
            if k in made:
                kept[k] = pending.pop(k)
            continue
        kept[k] = v
    for k, v in pending.items():
        base = next((b for b in reversed(list(kept)) if b.startswith(k.rsplit("_", 1)[0])), None)
        if base is None:
            kept[k] = v
            continue
        items = list(kept.items())
        i = [a for a, _ in items].index(base) + 1
        kept = dict(items[:i] + [(k, v)] + items[i:])
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} exterior blocks in {path}")


if __name__ == "__main__":
    main()
