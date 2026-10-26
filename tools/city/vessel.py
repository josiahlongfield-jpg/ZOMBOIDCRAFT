#!/usr/bin/env python3
"""The ship kit: every block the big ships in the ports are built from, so none of them needs a
vanilla block. Hull plating in five paints (with its weld seams, butts, rust runs from the
scuppers and fender scrapes), the red antifouling below the boot top with weed at the old
waterline; decks, hatch covers and their coamings; the white house with its cabin and bridge
windows; funnels, the line's emblems and the soot; the ship's name in two-block letters, draft
marks, the load line and the thruster mark; rails, bollards, fairleads, winches, mooring ropes,
anchor chain and the anchors in their hawses; masts, yards, radar, lights and horns; enclosed
lifeboats, davits and life-raft canisters; lashing bridges; pipes and valves for the tankers;
the holds' tank top and corrugated bulkheads, and the false bulkhead in the engine room.

    python3 tools/city/vessel.py        rewrites the vessel_ entries in blocks.json

photo_textures.py draws the textures (vessel.register()). Pieces follow furniture.py's rule:
the base model faces north with its back (z = 16) against what it hangs on, so a decal's facing
is the way it looks, away from its wall; axis pieces run along z.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import bunker as B  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
PREFIXES = ("vessel_",)

HULLS = {"black": (32, 34, 36), "navy": (28, 42, 70), "green": (36, 62, 50), "maroon": (76, 28, 26),
         "grey": (98, 104, 106)}
HULL_KINDS = ("plain", "seam", "butt", "rust", "scrape")
ANTIFOUL = (118, 42, 34)
WHITE = (210, 208, 198)
RUST = B.RUST
FUNNELS = {"red": (168, 40, 32), "blue": (34, 62, 118), "black": (30, 30, 32), "ochre": (198, 150, 52),
           "green": (42, 92, 62)}
DECKS = {"green": (72, 98, 76), "grey": (112, 116, 114), "red": (130, 54, 42)}
YELLOW = (206, 170, 48)
PRIMER = (126, 58, 40)
LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


# ---------------------------------------------------------------- paint

def touch_ups(t, rgb, amount=1.0):
    """Ships are painted over in patches between voyages, never quite the same shade twice."""
    B.patches(t, 0.5 * amount, tuple(min(255, c * 1.13) for c in rgb), 0.1, 0.4)
    B.patches(t, 0.35 * amount, tuple(c * 0.84 for c in rgb), 0.2, 0.45)


def hull_plate(seed, rgb, kind="plain"):
    """Hull plating: thick coats touched up in patches, the weld of a seam (along the top) or a
    butt (down the left), rust weeping from above, or scraped through to the primer by fenders."""
    t = B.painted(seed, rgb, "PaintedMetal004", 0.8, 0.4)
    touch_ups(t, rgb)
    if kind == "seam":
        F.area(t, 0, 0, N, 2, mul=0.76, dh=0.1)
        F.area(t, 0, 2, N, 3, mul=1.18, dh=0.05)
    elif kind == "butt":
        F.area(t, 0, 0, 2, N, mul=0.76, dh=0.1)
        F.area(t, 2, 0, 3, N, mul=1.18, dh=0.05)
    elif kind == "rust":
        B.runs(t, 3, RUST, 0.6, (48, 64), (2.0, 4.5), wet=0.12)
        B.rust_runs(t, 6, 0.55)
    elif kind == "scrape":
        rng = t.rng
        for _ in range(5):
            y, x0 = rng.uniform(6, N - 6), rng.uniform(-24, N)
            ln, w = rng.uniform(10, 40), rng.uniform(0.6, 1.8)
            col = np.array((104, 58, 38) if rng.random() < 0.7 else (132, 112, 92), np.float32)
            F.area(t, x0, y, x0 + ln, y + w, col * rng.uniform(0.7, 1.0), dh=-0.04)
            F.area(t, x0 + ln * 0.2, y + w, x0 + ln * 0.7, y + w + 0.8, mul=0.85)
        B.rust_runs(t, 4, 0.45, 24)
    t.smooth = np.clip(t.smooth, 0, 0.5)
    return F.wear(t, 0.15, 3)


def bottom(seed, kind="plain"):
    """The red antifouling an empty ship shows: weed and barnacles where the water used to be."""
    t = hull_plate(seed, ANTIFOUL, "seam" if kind == "seam" else "plain")
    if kind == "weed":
        y = np.arange(N)[:, None] / N
        g = np.clip(1.1 - y * 1.7 + P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.5, 0, 1)
        t.mix(g * 0.85, (56, 68, 40), 1.0)
        t.speckle(0.015, (170, 166, 150), 0.5, 0.05)
        t.smooth = np.maximum(t.smooth, g * 0.5)
    return t


def deck(seed, rgb, kind="plain"):
    """Deck plate: gritty non-slip paint, rust blooming through, scuffed pale where people walk;
    'walk' has the yellow lines of a walkway down both edges."""
    t = B.painted(seed, rgb, "PaintedMetal002", 0.9, 0.3)
    t.speckle(0.3, tuple(c * 0.78 for c in rgb), 0.35, 0.02)
    B.patches(t, 0.5, (114, 70, 46), 0.25, 0.5, tide=0.3)
    B.patches(t, 0.25, (156, 152, 140), 0.3, 0.5)
    F.area(t, 0, 0, N, 1, mul=0.75, dh=0.06)
    if kind == "walk":
        for x0 in (3, 56):
            F.area(t, x0, 0, x0 + 5, N, YELLOW)
        B.chips(t, 0.12, (90, 92, 86))
    return F.wear(t, 0.2, 4)


def hatch(seed, rgb):
    """A hatch cover's top: the joint between panels, drain gutter, the bolts of its cleats."""
    t = deck(seed, rgb)
    F.area(t, 0, 0, N, 3, mul=0.62, dh=-0.12)
    F.area(t, 0, 3, N, 4, mul=1.2, dh=0.05)
    F.area(t, 0, 30, N, 33, mul=0.85, dh=0.04)
    B.rivets(t, (6, 22, 42, 58), (8,), r=1.5)
    B.rust_runs(t, 3, 0.4, 4)
    return t


def coaming(seed, rgb):
    """The hatch coaming's side: stiffeners every half block, a hatch number stencilled big."""
    t = B.painted(seed, rgb, "PaintedMetal001", 0.9, 0.35)
    touch_ups(t, rgb, 0.7)
    for x in (16, 48):
        F.area(t, x - 2, 0, x + 2, N, mul=0.82, dh=0.12)
        F.area(t, x - 2, 0, x - 1, N, mul=1.25)
    B.runs(t, 2, RUST, 0.5, (30, 60), (1.5, 3.0))
    return F.wear(t, 0.2, 3)


def house(seed, kind="plain"):
    """The house's white plating: weld seams, rust runs from every window and scupper, soot
    blown down off the funnel."""
    t = B.painted(seed, WHITE, "PaintedMetal001", 0.85, 0.42)
    touch_ups(t, WHITE, 0.6)
    if kind == "seam":
        F.area(t, 0, 0, N, 2, mul=0.82, dh=0.1)
        F.area(t, 0, 2, N, 3, mul=1.08, dh=0.05)
    if kind in ("rust", "seam"):
        B.runs(t, 4 if kind == "rust" else 1, RUST, 0.6, (40, 64), (1.5, 4.0), wet=0.1)
        B.rust_runs(t, 6 if kind == "rust" else 2, 0.5)
    if kind == "soot":
        B.patches(t, 0.7, (52, 50, 48), 0.0, 0.45)
    return F.wear(t, 0.18, 3)


def glass_pane(t, x0, y0, x1, y1, broken=False):
    """Dark ship's glass with the sky's streak across it; broken, the dark behind and a crack web."""
    F.area(t, x0, y0, x1, y1, (30, 38, 44), h=0.6, smooth=0.95, f0=40)
    yy, xx = np.mgrid[0:N, 0:N]
    streak = (np.abs((xx - x0) - (yy - y0) * 0.8 - (x1 - x0) * 0.3) < 4) & (xx >= x0) & (xx < x1) & (yy >= y0) & (yy < y1)
    t.c[streak] = t.c[streak] * 0.6 + np.array((96, 110, 120), np.float32) * 0.4
    salt = B.patch_mask(t.rng, 0.15, 0.5, ((8, 1.0), (16, 0.6)))
    box = (xx >= x0) & (xx < x1) & (yy >= y0) & (yy < y1)
    t.mix(salt * box * 0.35, (150, 150, 140), 1.0)
    if broken:
        hole = F.blob_mask(t.rng, (x0 + x1) / 2 + t.rng.uniform(-6, 6), (y0 + y1) / 2, (x1 - x0) * 0.3, 0.5, wrap=False)
        hole = hole * box
        t.c[hole > 0.5] = (8, 8, 10)
        t.smooth[hole > 0.5] = 0.1
        keep = t.c.copy(), t.h.copy()
        t.crack(8, 0.05, (190, 196, 200))
        t.c[~box], t.h[~box] = keep[0][~box], keep[1][~box]


def cabin_window(seed, broken=False):
    t = house(seed, "plain")
    x0, y0, x1, y1 = 12, 14, 52, 44
    F.area(t, x0 - 3, y0 - 3, x1 + 3, y1 + 3, (172, 172, 166), h=0.95, smooth=0.5, f0=200)
    F.area(t, x0 - 3, y1, x1 + 3, y1 + 3, mul=0.7)
    glass_pane(t, x0, y0, x1, y1, broken)
    B.runs(t, 2, RUST, 0.5, (14, 20), (1.2, 2.5), top=y1 + 3)
    return t


def bridge_window(seed, broken=False):
    """The wheelhouse's tall glass: frame top and bottom, a thin mullion, the wiper's arc."""
    t = house(seed, "plain")
    F.area(t, 0, 0, N, 6, (178, 178, 172), h=0.95, smooth=0.5, f0=200)
    F.area(t, 0, 58, N, N, (178, 178, 172), h=0.95, smooth=0.5, f0=200)
    glass_pane(t, 2, 6, N, 58, broken)
    F.area(t, 0, 6, 2, 58, (150, 150, 146), h=0.95, f0=200)
    yy, xx = np.mgrid[0:N, 0:N]
    arc = np.abs(np.hypot(xx - 33, yy - 60) - 26) < 1.2
    t.c[arc & (yy < 58)] = t.c[arc & (yy < 58)] * 0.5 + 60
    F.area(t, 32, 34, 34, 58, (24, 24, 26))
    return t


def funnel(seed, rgb, soot=False):
    t = B.painted(seed, rgb, "PaintedMetal001", 0.85, 0.4)
    touch_ups(t, rgb, 0.5)
    if soot:
        y = np.arange(N)[:, None] / N
        g = np.clip(1.25 - y * 1.1 + P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.4, 0, 1)
        t.mix(g, (24, 22, 22), 1.0)
        t.smooth -= g * 0.2
    B.rust_runs(t, 3, 0.35)
    return F.wear(t, 0.15, 2)


def emblem(seed, kind):
    """A line's mark on its funnel, in white: a star, a diamond, or a ring round a bar."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = xx - 31.5, yy - 31.5
    if kind == "star":
        from PIL import ImageDraw
        img = Image.new("L", (256, 256), 0)
        pts = [(128 + np.sin(a) * (110 if i % 2 == 0 else 44), 132 - np.cos(a) * (110 if i % 2 == 0 else 44))
               for i, a in enumerate(np.arange(10) * np.pi / 5)]
        ImageDraw.Draw(img).polygon(pts, fill=255)
        m = np.asarray(img.resize((N, N), Image.BOX), np.float32) > 128
    elif kind == "diamond":
        m = (np.abs(dx) / 24 + np.abs(dy) / 28) < 1
        m &= ~((np.abs(dx) / 13 + np.abs(dy) / 16) < 1)
    else:
        r = np.hypot(dx, dy)
        m = ((r < 27) & (r > 20)) | ((np.abs(dy) < 4) & (np.abs(dx) < 27))
    B.solid(t, m.astype(np.float32) * (P.fbm(t.rng, ((16, 1.0), (32, 0.5))) > -0.55), WHITE, vary=0.06)
    t.smooth[:] = 0.4
    return t


def letter_masks():
    """Every capital as a two-block-tall white mask, squeezed narrow like the names on bows."""
    out = {}
    for ch in LETTERS:
        m = F.text_mask(ch, BOLD, 118, 104, 128, seed=ord(ch))
        img = Image.fromarray((m * 255).astype(np.uint8)).resize((58, 128), Image.BOX)
        full = np.zeros((128, N), np.float32)
        full[:, 3:61] = np.asarray(img, np.float32) / 255
        out[ch] = full
    return out


_LETTERS = {}


def name_letter(seed, ch, half):
    """Half of one letter of a ship's name, painted white, chipped and rust-stained."""
    if not _LETTERS:
        _LETTERS.update(letter_masks())
    m = _LETTERS[ch][:N] if half == "top" else _LETTERS[ch][N:]
    t = B.clear(seed)
    keep = P.fbm(t.rng, ((8, 1.0), (16, 0.6), (32, 0.4))) > -0.62
    B.solid(t, (m > 0.45) * keep, (222, 220, 210), vary=0.08)
    t.smooth[:] = 0.35
    return t


def painted_text(seed, text, size, col=WHITE, x=0, y=0, w=N, h=N):
    t = B.clear(seed)
    t.c[:] = col
    hard = B.stencil(t, text, col, size, x, y, w, h, chip=0.12, font=BOLD, opaque=True)
    t.alpha[~hard] = 0
    t.smooth[:] = 0.35
    return t


def draft_mark(seed, metres):
    """A draft mark: the metres in white with the tick of each half metre beside them."""
    t = painted_text(seed, f"{metres}M", 20, x=0, y=18, w=48, h=28)
    for y in (4, 36):
        B.solid(t, np.pad(np.ones((3, 10)), ((y, N - y - 3), (50, 4))), WHITE)
    return t


def plimsoll(seed):
    """The load line mark: the ring and bar, the letters either side, the ladder of lines."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 22, yy - 34)
    m = ((r < 15) & (r > 12)) | ((np.abs(yy - 34) < 1.6) & (np.abs(xx - 22) < 20))
    m |= (np.abs(xx - 50) < 1.6) & (yy > 12) & (yy < 50)
    for y in (13, 24, 34, 44):
        m |= (np.abs(yy - y) < 1.4) & (xx > 50) & (xx < 62)
    B.solid(t, m.astype(np.float32), WHITE, vary=0.05)
    t2 = painted_text(seed + 1, "L R", 9, x=4, y=16, w=36, h=12)
    hard = t2.alpha > 0
    t.c[hard], t.alpha[hard] = t2.c[hard], 255
    t.smooth[:] = 0.35
    return t


def thruster(seed):
    """The bow thruster warning: a ring with a cross through it."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = xx - 31.5, yy - 31.5
    r = np.hypot(dx, dy)
    m = ((r < 26) & (r > 21)) | ((np.abs(dx - dy) < 3.2) | (np.abs(dx + dy) < 3.2)) & (r < 22)
    B.solid(t, m.astype(np.float32), WHITE, vary=0.05)
    t.smooth[:] = 0.35
    return t


def hawse(seed):
    """The anchor's hawse: a dark socket in a thick steel rim, rust pouring out below it."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    e = ((xx - 32) / 26) ** 2 + ((yy - 24) / 20) ** 2
    rim = e < 1
    run = B.runs(t, 4, RUST, 1.0, (30, 50), (2.0, 5.0), top=36, wet=0.2)
    t.alpha = np.maximum(t.alpha, np.clip(run * 230, 0, 230))
    B.solid(t, rim.astype(np.float32), (70, 66, 62), h=1.0, vary=0.1)
    hole = e < 0.62
    t.c[hole] = (10, 9, 8)
    t.h[hole] = 0.3
    return t


def scupper(seed):
    """A scupper's drain hole under the deck edge and the long rust run it weeps."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    hole = ((xx - 32) / 7) ** 2 + ((yy - 5) / 4) ** 2 < 1
    B.solid(t, hole.astype(np.float32), (14, 12, 10), h=0.3)
    m = np.zeros((N, N), np.float32)
    rng = t.rng
    for _ in range(3):
        x0 = 32 + rng.uniform(-4, 4)
        wob = P.tnoise(rng, 4)[:, 0] * 2
        w = np.linspace(4, 1, N)
        d = np.abs(xx - x0 - wob[yy.astype(int)])
        m = np.maximum(m, np.clip((w[:, None] - d) / 1.5, 0, 1) * (yy > 6))
    run = m > 0.2
    t.c[run] = np.array(RUST, np.float32) * (0.8 + 0.4 * rng.random((int(run.sum()), 1)))
    t.alpha[run] = np.clip(m[run] * 230, 0, 230)
    return t


def anchor_tex(seed):
    """Cast steel, black paint mostly gone to rust."""
    t = F.mat("rusty_metal", (44, 40, 38), 1.1, seed=seed, smooth=0.25)
    B.patches(t, 0.6, (24, 24, 26), 0.1, 0.4)
    return t


def chain(seed, horizontal=False):
    """Stud-link anchor chain, rusted: flat links and edge-on links in turn."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    if horizontal:
        yy, xx = xx, yy
    m = np.zeros((N, N), bool)
    for c in range(0, N, 32):
        e = ((xx - 32) / 9) ** 2 + ((yy - c - 8) / 12) ** 2
        m |= (e < 1) & (e > 0.42)
        m |= (np.abs(xx - 32) < 6) & (np.abs(yy - c - 8) < 1.5)
        m |= (np.abs(xx - 32) < 2.5) & (np.abs(yy - c - 24) < 10)
    B.solid(t, m.astype(np.float32), (88, 56, 40), h=1.0, vary=0.25)
    t.speckle(0.2, (40, 30, 24), 0.5)
    t.alpha[~m] = 0
    return t


def rope(seed, rgb=(214, 176, 62)):
    """Mooring rope: polypropylene strands twisted round, dirty and furred."""
    t = P.Tex(seed, rgb)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    s = np.sin((xx + yy * 0.9) * 2 * np.pi / 10)
    t.c *= (0.78 + 0.22 * s)[..., None]
    t.h = 0.6 + 0.3 * s
    t.vary(0.12)
    t.grime(0.35)
    t.smooth[:] = 0.2
    return t


def steel_paint(seed, rgb, rough=0.35):
    t = B.painted(seed, rgb, "PaintedMetal002", 0.9, rough)
    B.rust_runs(t, 3, 0.3)
    B.chips(t, 0.08 if sum(rgb) < 400 else 0.02, (110, 70, 50))
    return F.wear(t, 0.2, 3)


def navlight(seed, rgb):
    t = steel_paint(seed, (40, 42, 44))
    F.area(t, 16, 12, 48, 52, rgb, h=0.9, smooth=0.9, f0=40)
    F.area(t, 16, 12, 48, 20, mul=1.4)
    for y in range(20, 52, 8):
        F.area(t, 16, y, 48, y + 1, mul=0.7)
    return t


def gfrp(seed, rgb, windows=False):
    """A lifeboat's glassfibre: glossy, chalky with sun, green where the rain pools."""
    t = F.plain(seed, rgb, 0.06, 0.6)
    t.f0[:] = 30
    B.patches(t, 0.35, tuple(min(255, c * 1.15) for c in rgb), 0.2, 0.5)
    B.patches(t, 0.3, (80, 92, 60), 0.35, 0.6)
    if windows:
        for cx in (18, 46):
            F.disc(t, cx, 26, 8, (22, 28, 32), h=0.6, smooth=0.95, f0=40)
            F.disc(t, cx, 26, 8, ring=2, col=(40, 40, 40))
    return F.wear(t, 0.18, 2)


def liferaft(seed):
    """A life raft's white canister: banded, a red label, green with algae on its north side."""
    t = gfrp(seed, (222, 222, 214))
    for x in (6, 30, 54):
        F.area(t, x, 0, x + 4, N, (46, 46, 48))
    F.area(t, 14, 22, 26, 40, (176, 40, 30))
    return t


def firebox(seed):
    t = steel_paint(seed, (176, 34, 28), 0.45)
    hard = B.stencil(t, "FIRE|HOSE", WHITE, 12, 4, 14, 56, 36, chip=0.1, font=BOLD)
    t.h[hard] += 0.02
    F.area(t, 2, 2, 62, 4, mul=1.2)
    return t


def pipe(seed, rgb):
    t = steel_paint(seed, rgb)
    for x in (0, 30):
        F.area(t, x, 0, x + 3, N, mul=0.8, dh=0.1)
    yy = np.arange(N)
    t.c *= (0.85 + 0.25 * np.sin(yy / N * np.pi))[:, None, None]
    return t


def tanktop(seed):
    """The hold's floor over the double bottom: primer plates, the manholes into the tanks,
    oily water lying in the dents."""
    t = F.mat("rusty_painted_metal", PRIMER, 1.1, seed=seed, smooth=0.25)
    F.area(t, 0, 0, N, 1, mul=0.7, dh=0.08)
    F.area(t, 0, 0, 1, N, mul=0.7, dh=0.08)
    B.patches(t, 0.6, (40, 34, 30), 0.3, 0.55, tide=0.4, wet=0.85)
    return t


def bulkhead(seed):
    """A transverse bulkhead's corrugations in red primer, rust down every trough."""
    t = F.mat("CorrugatedSteel006A", PRIMER, 1.2, rot=True, seed=seed, smooth=0.25)
    B.runs(t, 4, (74, 38, 26), 0.5, (30, 64), (1.5, 3.0))
    return t


def net(seed):
    """The safety net slung under a gangway."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    m = (np.abs(((xx + yy) % 16) - 8) < 1.2) | (np.abs(((xx - yy) % 16) - 8) < 1.2)
    B.solid(t, m.astype(np.float32), (196, 196, 186), vary=0.15)
    t.alpha[~m] = 0
    return t


def pilot_ladder(seed):
    t = B.clear(seed)
    B.solid(t, np.pad(np.ones((N, 4)), ((0, 0), (8, 52))), (200, 180, 120), vary=0.15)
    B.solid(t, np.pad(np.ones((N, 4)), ((0, 0), (52, 8))), (200, 180, 120), vary=0.15)
    for y in (6, 38):
        B.solid(t, np.pad(np.ones((6, 48)), ((y, N - y - 6), (8, 8))), (130, 86, 46), vary=0.2)
    return t


def textures():
    T = {}
    s = 7000
    for c, rgb in HULLS.items():
        for k in HULL_KINDS:
            T[f"vessel_hull_{c}" + ("" if k == "plain" else f"_{k}")] = (lambda a, b, d: lambda: hull_plate(a, b, d))(s, rgb, k)
            s += 1
    T.update({
        "vessel_bottom": lambda: bottom(7040), "vessel_bottom_seam": lambda: bottom(7041, "seam"),
        "vessel_bottom_weed": lambda: bottom(7042, "weed"),
        "vessel_house": lambda: house(7043), "vessel_house_seam": lambda: house(7044, "seam"),
        "vessel_house_rust": lambda: house(7045, "rust"), "vessel_house_soot": lambda: house(7046, "soot"),
        "vessel_window": lambda: cabin_window(7047), "vessel_window_broken": lambda: cabin_window(7048, True),
        "vessel_bridge": lambda: bridge_window(7049), "vessel_bridge_broken": lambda: bridge_window(7050, True),
        "vessel_funnel_soot": lambda: funnel(7051, (30, 30, 32), True),
        "vessel_draft_14": lambda: draft_mark(7052, 14), "vessel_draft_16": lambda: draft_mark(7053, 16),
        "vessel_draft_18": lambda: draft_mark(7054, 18), "vessel_draft_20": lambda: draft_mark(7055, 20),
        "vessel_plimsoll": lambda: plimsoll(7056), "vessel_thruster": lambda: thruster(7057),
        "vessel_hawse": lambda: hawse(7058), "vessel_scupper": lambda: scupper(7059),
        "vessel_anchor": lambda: anchor_tex(7060), "vessel_chain": lambda: chain(7061),
        "vessel_chain_h": lambda: chain(7062, True), "vessel_rope": lambda: rope(7063),
        "vessel_rail": lambda: steel_paint(7064, (198, 196, 186)), "vessel_bollard": lambda: steel_paint(7065, (44, 46, 46)),
        "vessel_winch": lambda: steel_paint(7066, (72, 100, 80)), "vessel_yellow": lambda: steel_paint(7067, YELLOW),
        "vessel_mast": lambda: steel_paint(7068, (214, 212, 202)),
        "vessel_lens_red": lambda: navlight(7069, (150, 24, 20)), "vessel_lens_green": lambda: navlight(7070, (26, 120, 60)),
        "vessel_lens_white": lambda: navlight(7071, (200, 200, 190)),
        "vessel_gfrp": lambda: gfrp(7072, (226, 102, 28)), "vessel_gfrp_window": lambda: gfrp(7073, (226, 102, 28), True),
        "vessel_gfrp_white": lambda: gfrp(7074, (222, 222, 214)), "vessel_liferaft": lambda: liferaft(7075),
        "vessel_firebox": lambda: firebox(7076), "vessel_pipe": lambda: pipe(7077, (132, 136, 130)),
        "vessel_valve": lambda: steel_paint(7078, (168, 34, 28), 0.45), "vessel_tanktop": lambda: tanktop(7079),
        "vessel_bulkhead": lambda: bulkhead(7080), "vessel_net": lambda: net(7081),
        "vessel_pilot_ladder": lambda: pilot_ladder(7082),
        "vessel_st_muster": lambda: B.stencil_sign(7083, "MUSTER|STATION", WHITE, 13),
        "vessel_st_lifeboat": lambda: B.stencil_sign(7084, "LIFEBOAT", WHITE, 13),
        "vessel_st_nosmoking": lambda: B.stencil_sign(7085, "NO|SMOKING", (200, 40, 30), 14),
        "vessel_st_engine": lambda: B.stencil_sign(7086, "ENGINE|ROOM", WHITE, 13, arrow=1),
        "vessel_st_gangway": lambda: B.stencil_sign(7087, "GANGWAY", WHITE, 13),
        "vessel_st_stores": lambda: B.stencil_sign(7088, "STORE", WHITE, 15),
    })
    s = 7100
    for c, rgb in FUNNELS.items():
        T[f"vessel_funnel_{c}"] = (lambda a, b: lambda: funnel(a, b))(s, rgb)
        s += 1
    for c, rgb in DECKS.items():
        T[f"vessel_deck_{c}"] = (lambda a, b: lambda: deck(a, b))(s, rgb)
        T[f"vessel_hatch_{c}"] = (lambda a, b: lambda: hatch(a, b))(s + 1, rgb)
        T[f"vessel_coaming_{c}"] = (lambda a, b: lambda: coaming(a, b))(s + 2, rgb)
        s += 3
    T["vessel_deck_walk"] = lambda: deck(7120, DECKS["green"], "walk")
    for i, k in enumerate(("star", "diamond", "ring")):
        T[f"vessel_emblem_{k}"] = (lambda a, b: lambda: emblem(a, b))(7121 + i, k)
    for i, ch in enumerate(LETTERS):
        for j, half in enumerate(("top", "bot")):
            T[f"vessel_name_{ch.lower()}_{half}"] = (lambda a, b, c: lambda: name_letter(a, b, c))(7200 + i * 2 + j, ch, half)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound="METAL", rotate="facing", decor=False, light=0, climb=False):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        if climb:
            spec["climb"] = True
        out[name] = spec

    def cube(name, tex, title, sound="METAL", rotate=None):
        out[name] = {"tex": tex, "title": title, "sound": sound}
        if rotate:
            out[name]["rotate"] = rotate

    def decal(name, tex, title):
        add(name, [[0, 0, 15.75, 16, 16, 16, tex]], title, "METAL", decor=True)

    # the hull, the house and the deck
    for c in HULLS:
        for k in HULL_KINDS:
            n = f"vessel_hull_{c}" + ("" if k == "plain" else f"_{k}")
            cube(n, n, "Hull Plating")
    for n in ("vessel_bottom", "vessel_bottom_seam", "vessel_bottom_weed"):
        cube(n, n, "Hull Plating")
    for n in ("vessel_house", "vessel_house_seam", "vessel_house_rust", "vessel_house_soot"):
        cube(n, n, "Superstructure")
    for n, title in (("vessel_window", "Cabin Window"), ("vessel_window_broken", "Broken Cabin Window"),
                     ("vessel_bridge", "Bridge Window"), ("vessel_bridge_broken", "Broken Bridge Window")):
        cube(n, {"north": n, "south": n, "side": "vessel_house", "up": "vessel_house", "down": "vessel_house"}, title,
             "GLASS", rotate="facing")
    cube("vessel_roof", {"up": "vessel_deck_grey", "side": "vessel_house", "down": "vessel_house"}, "Deck")
    for c in DECKS:
        cube(f"vessel_deck_{c}", {"up": f"vessel_deck_{c}", "side": f"vessel_coaming_{c}", "down": "ship_hold"}, "Deck")
        cube(f"vessel_hatch_{c}", {"up": f"vessel_hatch_{c}", "side": f"vessel_coaming_{c}", "down": "ship_hold"}, "Hatch Cover")
        cube(f"vessel_coaming_{c}", {"side": f"vessel_coaming_{c}", "up": f"vessel_deck_{c}", "down": "ship_hold"},
             "Hatch Coaming")
    cube("vessel_deck_walk", {"up": "vessel_deck_walk", "side": "vessel_coaming_green", "down": "ship_hold"}, "Deck",
         rotate="axis")
    for c in FUNNELS:
        cube(f"vessel_funnel_{c}", f"vessel_funnel_{c}", "Funnel")
    cube("vessel_funnel_soot", "vessel_funnel_soot", "Funnel")
    cube("vessel_tanktop", {"up": "vessel_tanktop", "side": "ship_hold", "down": "ship_hold"}, "Tank Top")
    cube("vessel_bulkhead", "vessel_bulkhead", "Bulkhead")
    cube("vessel_gfrp", "vessel_gfrp", "Lifeboat", "STONE")
    cube("vessel_gfrp_white", "vessel_gfrp_white", "Lifeboat", "STONE")
    cube("vessel_gfrp_window", {"north": "vessel_gfrp_window", "side": "vessel_gfrp", "up": "vessel_gfrp",
                                "down": "vessel_gfrp"}, "Lifeboat", "STONE", rotate="facing")

    # paint on the hull and the funnel
    for k in ("star", "diamond", "ring"):
        decal(f"vessel_emblem_{k}", f"vessel_emblem_{k}", "Funnel Mark")
    for ch in LETTERS:
        for half in ("top", "bot"):
            n = f"vessel_name_{ch.lower()}_{half}"
            decal(n, n, "Ship's Name")
    for n in ("vessel_draft_14", "vessel_draft_16", "vessel_draft_18", "vessel_draft_20"):
        decal(n, n, "Draft Mark")
    decal("vessel_plimsoll", "vessel_plimsoll", "Load Line Mark")
    decal("vessel_thruster", "vessel_thruster", "Thruster Mark")
    decal("vessel_hawse", "vessel_hawse", "Hawse Pipe")
    decal("vessel_scupper", "vessel_scupper", "Scupper")
    for n in ("vessel_st_muster", "vessel_st_lifeboat", "vessel_st_nosmoking", "vessel_st_engine", "vessel_st_gangway",
              "vessel_st_stores"):
        decal(n, n, "Stencil")
    add("vessel_net", [[0, 0, 15.5, 16, 16, 16, "vessel_net"]], "Safety Net", "WOOL", decor=True)
    add("vessel_pilot_ladder", [[0, 0, 15, 16, 16, 16, "vessel_pilot_ladder"]], "Pilot Ladder", "WOOD", climb=True)

    # anchors and their chain
    add("vessel_anchor_shank", [[6, 0, 12, 10, 16, 16, "vessel_anchor"], [5, 13, 11, 11, 16, 16, "vessel_anchor"]],
        "Anchor")
    add("vessel_anchor_crown", [[6, 6, 12, 10, 16, 16, "vessel_anchor"], [1, 4, 12, 15, 8, 16, "vessel_anchor"],
                                [0, 0, 12, 4, 7, 16, "vessel_anchor"], [12, 0, 12, 16, 7, 16, "vessel_anchor"],
                                [2, 7, 12, 4, 14, 16, "vessel_anchor"], [12, 7, 12, 14, 14, 16, "vessel_anchor"]], "Anchor")
    add("vessel_chain", [[7.5, 1, 0, 8.5, 9, 16, "vessel_chain_h"], [4, 4.5, 0, 12, 5.5, 16, "vessel_chain_h"]],
        "Anchor Chain", rotate="axis")
    add("vessel_chain_v", [[7.5, 0, 4, 8.5, 16, 12, "vessel_chain"], [4, 0, 7.5, 12, 16, 8.5, "vessel_chain"]],
        "Anchor Chain", rotate=None)

    # on deck: rails, bitts, fairleads, winches, ropes, vents, lashing bridges
    rail = [[7, 14, 0, 9, 15.5, 16, "vessel_rail"], [7.25, 7, 0, 8.75, 8.5, 16, "vessel_rail"],
            [7, 0, 7, 9, 14, 9, "vessel_rail"]]
    add("vessel_rail", rail, "Ship's Rail", rotate="axis")
    add("vessel_rail_cross", rail + [[0, 14, 7, 16, 15.5, 9, "vessel_rail"], [0, 7, 7.25, 16, 8.5, 8.75, "vessel_rail"]],
        "Ship's Rail", rotate=None)
    add("vessel_bollard", [[1, 0, 3, 15, 2, 13, "vessel_bollard"], [2.5, 2, 5.5, 7.5, 11, 10.5, "vessel_bollard"],
                           [8.5, 2, 5.5, 13.5, 11, 10.5, "vessel_bollard"], [2, 11, 5, 8, 12.5, 11, "vessel_bollard"],
                           [8, 11, 5, 14, 12.5, 11, "vessel_bollard"]], "Bitts", rotate="axis")
    add("vessel_fairlead", [[1, 0, 8, 15, 3, 16, "vessel_bollard"], [2, 3, 10, 5, 12, 14, "vessel_bollard"],
                            [11, 3, 10, 14, 12, 14, "vessel_bollard"], [2, 3.5, 10.5, 14, 6.5, 13.5, "vessel_bollard"]],
        "Fairlead")
    add("vessel_winch", [[1, 0, 1, 15, 2, 15, "vessel_winch"], [2.5, 2, 1, 13.5, 13, 2.5, "vessel_winch"],
                         [2.5, 2, 13.5, 13.5, 13, 15, "vessel_winch"], [3.5, 3, 2.5, 12.5, 12, 13.5, "vessel_rope"]],
        "Mooring Winch", rotate="axis")
    add("vessel_windlass", [[0, 0, 0, 16, 3, 16, "vessel_winch"], [3, 3, 0, 13, 13, 3, "vessel_winch"],
                            [3, 3, 13, 13, 13, 16, "vessel_winch"], [4, 4, 3, 12, 12, 13, "vessel_bollard"],
                            [6, 13, 6, 10, 16, 10, "vessel_valve"]], "Windlass", rotate="axis")
    add("vessel_rope", [[7, 7, 0, 9, 9, 16, "vessel_rope"]], "Mooring Line", "WOOL", rotate="axis", decor=True)
    add("vessel_rope_v", [[7, 0, 7, 9, 16, 9, "vessel_rope"]], "Mooring Line", "WOOL", rotate=None, decor=True)
    add("vessel_vent", [[5, 0, 5, 11, 10, 11, "vessel_mast"], [3, 10, 3, 13, 13, 13, "vessel_mast"],
                        [4, 13, 4, 12, 14, 12, "vessel_mast"]], "Ventilator", rotate=None)
    lash = [[0, 0, 0, 2, 16, 2], [14, 0, 0, 16, 16, 2], [0, 0, 14, 2, 16, 16], [14, 0, 14, 16, 16, 16],
            [0, 7, 0, 16, 8.5, 2], [0, 7, 14, 16, 8.5, 16]]
    add("vessel_lash", [b + ["vessel_yellow"] for b in lash] + [[0, 14.5, 0, 16, 16, 16, "bunk_walkway"]],
        "Lashing Bridge", rotate="axis")
    add("vessel_lash_open", [b + ["vessel_yellow"] for b in lash], "Lashing Bridge", rotate="axis")

    # masts, lights, the radar and the horn
    add("vessel_mast", [[6, 0, 6, 10, 16, 10, "vessel_mast"]], "Mast", rotate=None)
    add("vessel_yard", [[7, 6, 0, 9, 8, 16, "vessel_mast"]], "Yard", rotate="axis")
    add("vessel_radar", [[5, 0, 5, 11, 4, 11, "vessel_bollard"], [6, 4, 0, 10, 7, 16, "vessel_mast"]], "Radar Scanner",
        rotate="axis")
    for c in ("red", "green", "white"):
        add(f"vessel_light_{c}", [[5, 0, 5, 11, 7, 11, {"north": f"vessel_lens_{c}", "south": f"vessel_lens_{c}",
                                                         "side": f"vessel_lens_{c}", "up": "vessel_bollard",
                                                         "down": "vessel_bollard"}]], "Navigation Light")
    add("vessel_horn", [[6, 0, 6, 10, 6, 10, "vessel_bollard"], [5, 6, 2, 11, 11, 14, "vessel_mast"],
                        [4, 5, 0, 12, 12, 2, "vessel_mast"]], "Ship's Horn")
    add("vessel_dome", [[2, 0, 2, 14, 10, 14, "vessel_gfrp_white"], [4, 10, 4, 12, 13, 12, "vessel_gfrp_white"]],
        "Satellite Dome", "STONE", rotate=None)
    add("vessel_antenna", [[7.5, 0, 7.5, 8.5, 16, 8.5, "vessel_bollard"]], "Antenna", rotate=None)
    add("vessel_exhaust", [[2, 0, 2, 7, 16, 7, "vessel_funnel_soot"], [9, 0, 9, 14, 16, 14, "vessel_funnel_soot"],
                           [9, 0, 2, 13, 12, 6, "vessel_funnel_soot"]], "Exhaust Pipes", rotate=None)

    # lifesaving
    add("vessel_davit", [[6, 0, 12, 10, 16, 16, "vessel_mast"], [6, 12, 0, 10, 16, 12, "vessel_mast"],
                         [7.5, 4, 2, 8.5, 12, 3, "vessel_rope"]], "Davit")
    add("vessel_liferaft", [[1, 0, 2, 15, 3, 14, "vessel_rail"], [2, 3, 1, 14, 13, 15, "vessel_liferaft"]], "Life Raft",
        "STONE", rotate="axis")
    add("vessel_firebox", [[3, 3, 12, 13, 15, 16, {"north": "vessel_firebox", "side": "vessel_valve",
                                                   "up": "vessel_valve", "down": "vessel_valve"}]], "Fire Hose Box",
        decor=True)

    # tankers' pipes, the holds, the false bulkhead
    add("vessel_pipe", [[4, 2, 0, 12, 10, 16, "vessel_pipe"], [3, 0, 6, 13, 2, 10, "vessel_bollard"]], "Pipe",
        rotate="axis")
    add("vessel_pipe_valve", [[4, 2, 0, 12, 10, 16, "vessel_pipe"], [3, 1, 6, 13, 11, 10, "vessel_pipe"],
                              [7, 11, 7, 9, 14, 9, "vessel_bollard"], [3, 14, 3, 13, 15, 13, "vessel_valve"]], "Valve",
        rotate="axis")
    add("vessel_manhole", [[2, 0, 2, 14, 0.75, 14, "vessel_bollard"]], "Manhole", decor=True, rotate=None)
    out["vessel_false_door"] = {"door": "steel", "tex": {"top": "vessel_bulkhead", "bottom": "vessel_bulkhead"},
                                "title": "Bulkhead"}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith(PREFIXES)}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    lines = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in kept.items())
    rest = {k: v for k, v in data.items() if k != "blocks"}
    head = "".join(f'  {json.dumps(k)}: {json.dumps(v)},\n' for k, v in rest.items())
    path.write_text("{\n" + head + '  "blocks": {\n' + lines + "\n  }\n}\n")
    print(f"{len(made)} ship blocks in {path}")


if __name__ == "__main__":
    main()
