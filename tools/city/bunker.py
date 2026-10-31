#!/usr/bin/env python3
"""The bunker kit: every block the hidden bunkers are built from, so none of them needs a vanilla
block. Board-marked concrete and riveted steel, stencilled signs, caged lamps that wake when the
generator does, pipes and cable trays, steel doors, a two-by-three blast door with its locking
wheel, the forest hatch and its concrete collar, climbable ladders; the rooms (steel bunks, lockers,
mess tables, the field kitchen, decontamination showers, the radio desk and map table, the med bay);
the storage hall (pallet racks, ration and ammo crates, water drums, jerry cans) and the armoury
(gun racks, pegboards, mesh cages); the generator, air filter, fuel tank and switchboard; story
pieces; the cargo ship's insides; the tower shelter's server racks; the civil-defence shelter's
signs and biscuit tins.

    python3 tools/city/bunker.py        rewrites the bunk_, ship_ and container_in entries in blocks.json

photo_textures.py draws the textures (bunker.register()). Pieces follow furniture.py's rule: the
base model faces north with its back (z = 16) against the wall, so facing is the way a piece looks
into the room; axis pieces run along z. Wall plates are decor (no collision).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SANS = F.SANS
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
PREFIXES = ("bunk_", "ship_", "container_in", "container_floor", "container_welded", "garage_ramp", "park_")

OLIVE = (82, 88, 62)
GREY = (112, 118, 114)
AMBER = (214, 168, 44)
STENCIL_RED = (168, 36, 28)
WHITE_PAINT = (214, 210, 196)
RUST = (120, 62, 34)
BRASS = F.BRASS


# ---------------------------------------------------------------- helpers

def clear(seed):
    """A fully transparent texture to paint a decal or cut-out on."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    return t


def solid(t, mask, col, h=None, vary=0.0):
    """Paints col where mask is set and makes it opaque."""
    m = mask > 0.5
    c = np.array(col, np.float32)
    if vary:
        c = c * (1 + vary * P.fbm(t.rng, ((8, 1.0), (16, 0.5))))[..., None]
        t.c[m] = c[m]
    else:
        t.c[m] = c
    t.alpha[m] = 255
    if h is not None:
        t.h[m] = h
    return m


def fit_text(text, font, size, w, h, seed):
    """Lettering as big as asked, but squeezed narrower (stencil letters run tall) and then
    shrunk until the longest line fits across w with a margin."""
    f = ImageFont.truetype(font, size * 4)
    need = max(f.getlength(line) for line in text.split("|")) / 4 + 2
    room = w - 6
    sq = 1.0 if need <= room else max(0.62, room / need)
    if need * sq > room:
        size = max(6, int(size * room / (need * sq)))
    ww = int(round(w / sq))
    m = F.text_mask(text, font, size, ww, h, seed=seed)
    if ww != w:
        m = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize((w, h), Image.BOX), np.float32) / 255
    return m


def stencil(t, text, col, size, x=0, y=0, w=N, h=N, chip=0.25, font=SANS, amount=1.0, opaque=False):
    """Spray-stencilled letters: a little uneven, chipped where the paint has flaked."""
    m = fit_text(text, font, size, w, h, int(t.rng.integers(1 << 30)))
    full = np.zeros((N, N), np.float32)
    x0, y0, x1, y1 = max(0, x), max(0, y), min(N, x + w), min(N, y + h)
    if x1 > x0 and y1 > y0:  # the part of the lettering that falls on this block
        full[y0:y1, x0:x1] = m[y0 - y:y1 - y, x0 - x:x1 - x]
    flake = P.fbm(t.rng, ((8, 1.0), (16, 0.6), (32, 0.4))) > (0.55 - chip)
    full = full * (~flake)
    hard = full > 0.45
    shade = 1 + 0.12 * P.fbm(t.rng, ((4, 1.0), (16, 0.4)))
    c = np.array(col, np.float32) * shade[..., None]
    t.c[hard] = t.c[hard] * (1 - amount) + c[hard] * amount
    t.h[hard] += 0.02
    if opaque:
        t.alpha[hard] = 255
    return hard


def painted(seed, rgb, material="PaintedMetal001", contrast=1.0, smooth=0.35, span=1.0):
    t = F.mat(material, rgb, contrast, seed=seed, span=span, smooth=smooth)
    t.f0[:] = 20
    t.vary(0.06)
    t.grime(0.12)
    return t


def chips(t, amount, under=(120, 118, 112), cells=((8, 1.0), (16, 0.7), (32, 0.4))):
    """Paint chipped off the edges and in patches, showing what is underneath."""
    n = P.fbm(t.rng, cells)
    m = n > (0.62 - amount)
    t.c[m] = np.array(under, np.float32) * (0.85 + 0.3 * t.rng.random((int(m.sum()), 1)))
    t.h[m] -= 0.04
    return m


def rust_runs(t, n, amount=0.6, top=0):
    """Rust weeping down from points along a line (rivets, a seam, a bolt)."""
    rng = t.rng
    for _ in range(n):
        x = int(rng.uniform(0, N))
        y0 = top + int(rng.uniform(0, 8))
        ln = int(rng.uniform(6, 30))
        for i in range(ln):
            a = amount * (1 - i / ln)
            for dx in (0, 1):
                xx = (x + dx) % N
                yy = min(N - 1, y0 + i)
                t.c[yy, xx] = t.c[yy, xx] * (1 - a) + np.array(RUST, np.float32) * a


def rivets(t, xs, ys, col=None, r=1):
    for x in xs:
        for y in ys:
            c = col if col is not None else t.c[int(y) % N, int(x) % N] * 1.05
            F.area(t, x - r, y - r, x + r, y + r, c, h=1.0)
            F.area(t, x - r, y - r, x, y, mul=1.3)
            F.area(t, x, y + r, x + r + 1, y + r + 1, mul=0.55)


def wheel(t, cx, cy, r, ring, col, spokes=3, hub=None, ang=0.4):
    """A hand wheel seen face on: rim, spokes, hub; alpha set where it is solid."""
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - cx, yy - cy)
    m = (d <= r) & (d > r - ring)
    for k in range(spokes):
        a = ang + k * 2 * np.pi / spokes
        px, py = xx - cx, yy - cy
        along = px * np.cos(a) + py * np.sin(a)
        across = np.abs(-px * np.sin(a) + py * np.cos(a))
        m |= (across < 1.1) & (along > 0) & (along < r)
    m |= d <= (hub or ring + 1)
    solid(t, m.astype(np.float32), col, h=1.0, vary=0.06)
    lit = m & (yy < cy)
    t.c[lit] *= 1.15
    return m


def grid_mask(step, w=1, off=0):
    yy, xx = np.mgrid[0:N, 0:N]
    return (((xx + off) % step) < w) | (((yy + off) % step) < w)


def light(t, mask, amount=1.0):
    t.emit[mask > 0.5] = amount


def label(t, x0, y0, x1, y1, text=None, size=6, col=(226, 220, 200), ink=(30, 28, 24)):
    F.area(t, x0, y0, x1, y1, col, h=0.85)
    F.edges(t, x0, y0, x1, y1, 1, light=1.05, dark=0.7, dh=0.02)
    if text:
        m = F.text_mask(text, SANS, size, x1 - x0, y1 - y0, seed=x0 * 7 + y0)
        F.paste(t, m, x0, y0, ink)


# ---------------------------------------------------------------- walls and floors

def concrete(seed, damp=False):
    """Poured concrete with the formwork's board marks, its tie holes, a few runs of rust."""
    t = F.mat("Concrete034", (138, 136, 128), 0.8, span=2, seed=seed, smooth=0.12)
    rng = t.rng
    for y0 in range(0, N, 16):
        grain = P.tnoise(rng, 8)[0] * 0.05 + P.tnoise(rng, 32)[0] * 0.03
        t.c[y0:y0 + 16] *= (1 + grain)[None, :, None]
        for _ in range(3):
            gy = y0 + int(rng.integers(2, 15))
            F.area(t, 0, gy, N, gy + 1, mul=0.95, dh=-0.02)
        F.area(t, 0, y0, N, y0 + 1, mul=0.78, dh=-0.12)  # the joint between boards
        F.area(t, 0, y0 + 1, N, y0 + 2, mul=1.06)
    for x, y in ((14, 8), (46, 8), (14, 40), (46, 40)):
        F.disc(t, x, y, 2.4, mul=0.82, dh=-0.1)
        F.disc(t, x, y, 1.3, (44, 42, 38), dh=-0.3)
        if rng.random() < 0.5:
            F.drips(t, x - 1, x + 1, y + 2, 1, (6, 14), col=(118, 84, 56))
    if damp:
        wet = np.clip((np.arange(N) - 44) / 14, 0, 1)[:, None] * (0.7 + 0.3 * P.fbm(rng, ((8, 1.0), (16, 0.5))))
        t.c *= (1 - 0.35 * wet)[..., None]
        t.smooth = np.maximum(t.smooth, wet * 0.5)
        crust = (np.abs(np.arange(N) - 44)[:, None] < 3) & (rng.random((N, N)) < 0.35)
        t.c[crust] = t.c[crust] * 0.3 + np.array((206, 204, 192), np.float32) * 0.7
    return F.wear(t, 0.2, 0)


def dado(seed, damp=False):
    """The lower wall: institutional green gloss over concrete, a dark line along its top, scuffed
    by boots and trolleys, flaking where the damp gets in."""
    t = painted(seed, (92, 112, 94), "painted_concrete_02", 0.7, 0.5, span=2)
    F.area(t, 0, 0, N, 3, (40, 46, 40), smooth=0.5)
    F.area(t, 0, 3, N, 4, mul=0.8)
    rng = t.rng
    for _ in range(6):  # scuffs from boots and trolleys
        x, y = rng.uniform(0, N), rng.uniform(44, 62)
        F.area(t, x, y, x + rng.uniform(3, 10), y + 1, mul=0.7)
    chips(t, 0.08 if not damp else 0.12, (132, 130, 122))
    if damp:
        rise = np.clip((np.arange(N) - 30) / 30, 0, 1)[:, None]
        mould = np.clip((P.fbm(rng, ((8, 1.0), (16, 0.8), (32, 0.5))) - 0.35) * 3, 0, 1) * rise
        t.c = t.c * (1 - 0.5 * mould[..., None]) + np.array((30, 40, 28), np.float32) * 0.5 * mould[..., None]
    return F.wear(t, 0.25, 4)


def paint(seed, flake=False):
    """The upper wall: cream paint over concrete, gone dull, peeling where it's worst."""
    t = painted(seed, (190, 184, 160), "painted_concrete_02", 0.4, 0.3, span=2)
    t.streaks(0.25, 0.9)
    chips(t, 0.07 if flake else 0.03, (128, 126, 118))
    return F.wear(t, 0.2, 0)


def plate(seed, rgb=(104, 112, 106), rust=False):
    """Riveted steel plate: a seam across and up the middle, rivets along both, a dull coat."""
    t = painted(seed, rgb, "MetalPlates013", 1.0, 0.35)
    for s in (0, 32):
        F.area(t, 0, s, N, s + 1, mul=0.55, dh=-0.2)
        F.area(t, s, 0, s + 1, N, mul=0.55, dh=-0.2)
    rivets(t, range(4, N, 8), (4, 28, 36, 60))
    rivets(t, (4, 28, 36, 60), range(12, N, 16))
    if rust:
        rust_runs(t, 8, 0.35)
        r = F.mat("rusty_painted_metal", (96, 70, 52), 0.8, seed=seed + 1)
        m = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4))) * 1.6 - 0.55, 0, 0.75)
        t.c = t.c * (1 - m[..., None]) + r.c * m[..., None]
    return F.wear(t, 0.25, 6)


def floor(seed, line=False, drain=False):
    """Painted concrete floor, worn back to bare concrete along the paths, oil and scuffs."""
    t = painted(seed, (100, 108, 102), "concrete_floor_painted", 0.45, 0.4, span=1)
    worn = np.clip(P.fbm(t.rng, ((2, 1.0), (4, 0.6), (16, 0.3))) * 2.0 - 0.2, 0, 1)
    t.c = t.c * (1 - worn[..., None] * 0.3) + np.array((132, 128, 118), np.float32) * worn[..., None] * 0.3
    t.smooth = t.smooth * (1 - worn * 0.6)
    if line:
        F.area(t, 26, 0, 38, N, AMBER, smooth=0.45)
        chips(t, 0.12, (126, 122, 112))
    if drain:
        F.stain(t, 32, 32, 18, (70, 64, 52), 0.45)
        F.area(t, 22, 22, 42, 42, (52, 50, 46), h=0.6, smooth=0.5, f0=200)
        for k in range(24, 42, 3):
            F.area(t, k, 23, k + 1, 41, (20, 20, 18), dh=-0.3)
        F.edges(t, 22, 22, 42, 42, 1)
    if t.rng.random() < 0.5:
        patches(t, 0.3, (60, 54, 44), 0.15, 0.4)
    return F.wear(t, 0.3, 10)


def tile(seed):
    """Square white tiles in grey grout, grimy in the joints, a few cracked or gone."""
    t = F.plain(seed, (210, 208, 198), 0.03, 0.75)
    t.f0[:] = 30
    rng = t.rng
    for k in range(0, N, 16):
        F.area(t, 0, k, N, k + 1, (120, 118, 108), h=0.6, smooth=0.1)
        F.area(t, k, 0, k + 1, N, (120, 118, 108), h=0.6, smooth=0.1)
    for gy in range(0, N, 16):
        for gx in range(0, N, 16):
            t.c[gy + 1:gy + 16, gx + 1:gx + 16] *= rng.uniform(0.94, 1.04)
            r = rng.random()
            if r < 0.08:
                F.area(t, gx + 1, gy + 1, gx + 16, gy + 16, (118, 114, 104), h=0.5, smooth=0.1)
            elif r < 0.2:
                t.crack(1, 0.2)
    t.grime(0.25, (90, 84, 70))
    return t


def walkway(seed):
    """Steel bar grating you can see through, rust in the corners."""
    t = clear(seed)
    bars = np.zeros((N, N), np.float32)
    bars[:, ::4] = 1
    bars[::16, :] = 1
    bars[1::16, :] = 1
    t.c[:] = (96, 94, 88)
    solid(t, bars, (100, 98, 92), h=1.0, vary=0.15)
    t.f0[:] = 160
    t.smooth[:] = 0.35
    t.speckle(0.08, RUST, 0.6)
    return t


def cage_mesh(seed):
    """Welded mesh panel on an angle-iron frame, rust bloomed at the welds."""
    t = clear(seed)
    m = grid_mask(5, 1).astype(np.float32)
    m[:, :3] = 1
    m[:, -3:] = 1
    m[:3, :] = 1
    m[-3:, :] = 1
    solid(t, m, (92, 94, 90), h=1.0, vary=0.12)
    t.f0[:] = 120
    yy, xx = np.mgrid[0:N, 0:N]
    welds = ((xx % 5) == 0) & ((yy % 5) == 0) & (t.rng.random((N, N)) < 0.5)
    t.c[welds] = RUST
    return t


# ---------------------------------------------------------------- signs

def stencil_sign(seed, text, col=AMBER, size=12, arrow=0):
    """Letters sprayed through a stencil straight onto the wall; an arrow under them if asked."""
    t = clear(seed)
    t.c[:] = col
    hard = stencil(t, text, col, size, 0, 4 if arrow else 0, N, 40 if arrow else N, chip=0.1, opaque=True)
    t.alpha[~hard] = 0
    if arrow:
        yy, xx = np.mgrid[0:N, 0:N]
        x = xx if arrow > 0 else N - 1 - xx
        shaft = (np.abs(yy - 52) < 3) & (x > 8) & (x < 44)
        head = (x >= 40) & (x < 56) & (np.abs(yy - 52) < (56 - x) * 0.7)
        m = (shaft | head) & ~(P.fbm(t.rng, ((16, 1.0), (32, 0.5))) > 0.45)
        solid(t, m.astype(np.float32), col, vary=0.1)
    t.smooth[:] = 0.3
    return t


def shelter_sign(seed, part):
    """The old civil-defence plate: yellow enamel on steel, the black roundel with its three
    yellow triangles on one half, FALLOUT SHELTER on the other; rust from every screw."""
    t = F.plain(seed, (218, 176, 36), 0.05, 0.55)
    t.f0[:] = 40
    if part == "a":
        F.disc(t, 32, 32, 26, (22, 20, 18))
        yy, xx = np.mgrid[0:N, 0:N]
        for k in range(3):
            a = -np.pi / 2 + k * 2 * np.pi / 3
            cx, cy = 32 + np.cos(a) * 12, 32 + np.sin(a) * 12
            # an inward-pointing triangle: its tip at the centre
            px, py = xx - cx, yy - cy
            along = -(px * np.cos(a) + py * np.sin(a))
            across = np.abs(-px * np.sin(a) + py * np.cos(a))
            tri = (along > -8) & (along < 11) & (across < (11 - along) * 0.62)
            t.c[tri] = (218, 176, 36)
    else:
        m = F.text_mask("FALLOUT|SHELTER", SANS, 13, N, 44, seed=seed)
        F.paste(t, m, 0, 4, (22, 20, 18))
        m = F.text_mask("CAPACITY 240", SANS, 6, N, 12, seed=seed + 1)
        F.paste(t, m, 0, 48, (22, 20, 18))
    F.area(t, 0, 0, N, 2, mul=0.6)
    F.area(t, 0, N - 2, N, N, mul=0.6)
    F.area(t, 0, 0, 2, N, mul=0.6)
    F.area(t, N - 2, 0, N, N, mul=0.6)
    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        F.knob(t, x, y, (150, 146, 136))
    rust_runs(t, 6, 0.55)
    chips(t, 0.06, (90, 70, 50))
    return F.wear(t, 0.3, 4)


def keyswitch(seed):
    """The freight lift's key panel: brushed steel, a key switch for a floor its buttons skip."""
    t = F.steel(seed, (160, 162, 160))
    F.edges(t, 2, 2, 62, 62, 2)
    F.disc(t, 32, 24, 8, (40, 40, 38), h=0.95)
    F.disc(t, 32, 24, 6, (190, 160, 70), smooth=0.8, f0=230)
    F.area(t, 31, 18, 33, 30, (30, 28, 26))
    m = F.text_mask("SERVICE", SANS, 7, N, 12, seed=seed)
    F.paste(t, m, 0, 36, (40, 40, 38))
    m = F.text_mask("B4  B5", MONO, 8, N, 12, seed=seed + 1)
    F.paste(t, m, 0, 48, (40, 40, 38))
    F.area(t, 36, 48, 54, 60, (30, 30, 28))  # the tape over B5
    F.area(t, 36, 48, 54, 60, mul=(4.6, 4.0, 2.8))
    return F.wear(t, 0.3, 8)


def keypad(seed):
    """A blast door's code pad: a grey steel box on the wall, the green readout, twelve worn
    rubber keys in four rows, a red lamp and a green one over them, a hood of grime round its edge."""
    t = F.steel(seed, (128, 130, 128))
    F.edges(t, 2, 2, 62, 62, 2)
    F.area(t, 10, 6, 54, 16, (24, 34, 22))  # the readout's window
    F.area(t, 12, 8, 52, 14, (60, 96, 52))
    t.emit[8:14, 12:52] = 0.25
    for i, x in enumerate((44, 50)):
        F.disc(t, x, 21, 2, ((150, 30, 26), (40, 120, 40))[i], smooth=0.9, f0=60)
    for r in range(4):
        for c in range(3):
            x0, y0 = 13 + c * 14, 26 + r * 9
            F.area(t, x0, y0, x0 + 11, y0 + 7, (36, 36, 34))
            F.area(t, x0 + 1, y0 + 1, x0 + 10, y0 + 6, (62, 62, 58))
            # the digits most pressed are worn shiny: 4, 1, 7, 0, 2 (the radio's code)
            if (r, c) in ((1, 0), (0, 0), (2, 0), (3, 1), (0, 1)):
                F.area(t, x0 + 2, y0 + 2, x0 + 9, y0 + 5, mul=1.3)
    F.area(t, 0, 60, N, N, mul=0.6)
    rust_runs(t, 3, 0.3)
    return F.wear(t, 0.35, 6)


def warning_plate(seed):
    """The plate on the wire and by every door the army didn't want you through: white enamel,
    WARNING in white on a red band across the top, the words under it in black; rust at the bolts."""
    t = F.plain(seed, (226, 224, 214), 0.04, 0.6)
    t.f0[:] = 40
    F.area(t, 0, 0, N, 18, (168, 34, 30))
    m = F.text_mask("WARNING", SANS, 11, N, 18, seed=seed)
    F.paste(t, m, 0, 0, (240, 236, 226))
    m = F.text_mask("RESTRICTED|AREA", SANS, 9, N, 22, seed=seed + 1)
    F.paste(t, m, 0, 20, (30, 28, 26))
    m = F.text_mask("AUTHORIZED|PERSONNEL ONLY", SANS, 6, N, 18, seed=seed + 2)
    F.paste(t, m, 0, 43, (30, 28, 26))
    F.edges(t, 1, 1, 63, 63, 1)
    for x, y in ((4, 4), (59, 4), (4, 59), (59, 59)):
        F.knob(t, x, y, (150, 146, 136))
    rust_runs(t, 5, 0.5)
    chips(t, 0.05, (90, 70, 50))
    return F.wear(t, 0.3, 4)


# ---------------------------------------------------------------- lights and services

def cage_wire(seed):
    t = clear(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    m = ((xx % 12) < 2) | ((yy % 16) < 2)
    solid(t, m.astype(np.float32), (46, 46, 42), h=1.0, vary=0.1)
    t.f0[:] = 150
    return t


def glass(seed, rgb, on):
    t = F.plain(seed, rgb, 0.04, 0.85)
    t.f0[:] = 40
    yy, xx = np.mgrid[0:N, 0:N]
    glow = np.clip(1 - np.hypot(xx - 32, yy - 32) / 46, 0, 1)
    if on:
        t.c = np.clip(t.c * (0.85 + 0.4 * glow[..., None]), 0, 255)
        t.emit[:] = 0.6 + 0.4 * glow
    else:
        t.c *= 0.45
        t.speckle(0.03, (20, 18, 16), 0.7)  # dead flies
    return t


def tube(seed, on):
    """A fluorescent fitting's prismatic diffuser, lit or dead and fly-specked."""
    t = F.plain(seed, (226, 228, 222) if on else (150, 150, 144), 0.03, 0.7)
    for x in range(0, N, 3):
        F.area(t, x, 0, x + 1, N, mul=0.92)
    if on:
        t.emit[:] = 1.0
    else:
        t.speckle(0.05, (30, 28, 24), 0.7)
    return t


def pipe(seed, rgb):
    """A painted service pipe: dull and chipped, a white flow band with its arrow."""
    t = painted(seed, rgb, "PaintedMetal012", 1.0, 0.35)
    yy = np.arange(N)[:, None]
    t.c *= (0.75 + 0.45 * np.sin(yy / N * np.pi * 8) ** 2)[..., None] * 0.9 + 0.1
    F.area(t, 40, 0, 46, N, (210, 206, 194))
    for y in range(4, N, 8):
        F.area(t, 41, y, 45, y + 2, (30, 28, 26))
    chips(t, 0.06, (110, 104, 96))
    rust_runs(t, 3, 0.4)
    return F.wear(t, 0.3, 4)


def cables(seed):
    t = F.plain(seed, (34, 34, 32), 0.05, 0.45)
    rng = t.rng
    x = 0
    while x < N:
        w = int(rng.integers(3, 7))
        col = [(30, 30, 28), (52, 52, 50), (40, 38, 36), (170, 90, 30), (60, 64, 70)][rng.integers(5)]
        F.area(t, x, 0, x + w, N, col, h=0.9, smooth=0.5)
        F.area(t, x, 0, x + 1, N, mul=1.4)
        F.area(t, x + w - 1, 0, x + w, N, mul=0.5, dh=-0.2)
        x += w
    t.grime(0.3, (80, 74, 60))
    return t


def grille(seed):
    t = F.mat("Metal038", (150, 152, 148), 0.8, seed=seed, smooth=0.4)
    t.f0[:] = 200
    for y in range(4, N - 4, 6):
        F.area(t, 4, y, 60, y + 3, (36, 36, 34), dh=-0.3)
        F.area(t, 4, y + 3, 60, y + 4, mul=1.3, dh=0.1)
    F.edges(t, 1, 1, 63, 63, 2)
    t.grime(0.35, (60, 54, 42))
    return t


def fusebox(seed):
    t = painted(seed, (128, 134, 130), "PaintedMetal001", 0.9, 0.35)
    F.edges(t, 1, 1, 63, 63, 2)
    F.gap(t, 4, 4, 60, 60)
    for row in (14, 30):
        F.area(t, 10, row, 54, row + 10, (40, 40, 38), h=0.6)
        for x in range(12, 52, 5):
            on = t.rng.random() < 0.7
            F.area(t, x, row + 2, x + 3, row + 8, (18, 18, 16), h=0.9)
            F.area(t, x, row + (2 if on else 5), x + 3, row + (5 if on else 8), (210, 206, 196), h=1.0)
    yy, xx = np.mgrid[0:N, 0:N]
    tri = (yy > 44) & (yy < 58) & (np.abs(xx - 32) < (yy - 44) * 0.6)
    t.c[tri] = (220, 178, 30)
    bolt = tri & (np.abs((xx - 32) + (yy - 51) * 0.5) < 1.2)
    t.c[bolt] = (20, 20, 18)
    label(t, 40, 47, 58, 54, None)
    return F.wear(t, 0.3, 6)


def field_phone(seed):
    """The field telephone: an olive box, its black handset in the cradle, a crank, a tag."""
    t = painted(seed, OLIVE, "PaintedMetal001", 0.9, 0.3)
    F.edges(t, 1, 1, 63, 63, 2)
    F.area(t, 8, 8, 56, 20, (24, 24, 22), h=1.0, smooth=0.6)  # the handset
    F.area(t, 6, 6, 16, 22, (24, 24, 22), h=1.0, smooth=0.6)
    F.area(t, 48, 6, 58, 22, (24, 24, 22), h=1.0, smooth=0.6)
    F.area(t, 8, 8, 56, 10, mul=1.5)
    F.disc(t, 48, 42, 8, (60, 64, 46), h=0.95)
    F.area(t, 47, 34, 49, 42, (40, 40, 36))
    F.disc(t, 48, 34, 2, (30, 30, 28), h=1.0)
    label(t, 10, 32, 34, 40, "TEL", 6)
    label(t, 10, 46, 34, 54, "LINE 2", 5)
    return F.wear(t, 0.3, 6)


def extinguisher(seed, front):
    t = F.plain(seed, (176, 30, 24), 0.05, 0.6)
    t.f0[:] = 30
    xx = np.arange(N)[None, :]
    t.c *= (0.7 + 0.5 * np.sin(xx / N * np.pi))[..., None]
    if front:
        label(t, 12, 20, 52, 48, "FIRE|EXT", 8)
    return F.wear(t, 0.25, 6)


def rad_meter(seed):
    """A civil-defence survey meter on its wall bracket: yellow case, the dial, a range knob."""
    t = F.plain(seed, (210, 168, 40), 0.05, 0.5)
    F.edges(t, 1, 1, 63, 63, 2)
    F.disc(t, 24, 30, 14, (30, 30, 28))
    F.disc(t, 24, 30, 12, (226, 222, 206))
    for k in range(9):
        a = np.pi * (1.1 + k * 0.1)
        F.area(t, 24 + np.cos(a) * 10, 30 + np.sin(a) * 10, 24 + np.cos(a) * 10 + 1, 30 + np.sin(a) * 10 + 1,
               (30, 30, 28))
    for i in range(10):
        F.area(t, 24 - i, 30 - i * 0.3, 25 - i, 31 - i * 0.3, (180, 30, 24))
    F.disc(t, 50, 24, 5, (30, 30, 28), h=1.0)
    F.disc(t, 50, 44, 5, (30, 30, 28), h=1.0)
    m = F.text_mask("RADIOLOGICAL", SANS, 5, N, 10, seed=seed)
    F.paste(t, m, 0, 50, (30, 30, 28))
    return F.wear(t, 0.3, 4)


def clock(seed):
    """A school clock stopped where the power died."""
    t = clear(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 31.5, yy - 31.5)
    solid(t, (d < 31).astype(np.float32), (36, 36, 34), h=1.0)
    solid(t, (d < 27).astype(np.float32), (224, 220, 206), h=0.8)
    t.alpha[d >= 31] = 0
    for k in range(12):
        a = k * np.pi / 6
        F.area(t, 31 + np.cos(a) * 23, 31 + np.sin(a) * 23, 33 + np.cos(a) * 23, 33 + np.sin(a) * 23, (30, 30, 28))
    for frac, ln, w in ((8.3 / 12, 14, 2), (17 / 60, 21, 1)):
        a = frac * 2 * np.pi - np.pi / 2
        for i in range(ln):
            F.area(t, 31 + np.cos(a) * i, 31 + np.sin(a) * i, 31 + np.cos(a) * i + w, 31 + np.sin(a) * i + w, (24, 24, 22))
    F.stain(t, 40, 40, 10, (150, 130, 90), 0.3)
    return t


def first_aid(seed):
    t = F.enamel(seed, (214, 212, 202))
    F.edges(t, 1, 1, 63, 63, 2)
    F.area(t, 26, 10, 38, 38, (40, 130, 70))
    F.area(t, 18, 18, 46, 30, (40, 130, 70))
    m = F.text_mask("FIRST AID", SANS, 8, N, 14, seed=seed)
    F.paste(t, m, 0, 42, (40, 130, 70))
    F.area(t, 28, 58, 36, 61, (120, 120, 116), f0=200)
    return F.wear(t, 0.3, 6)


# ---------------------------------------------------------------- doors

def steel_door(seed, top, rgb=(96, 108, 100), sign=None):
    """A painted steel door: wired glass up top, the push plate and kick plate below."""
    t = painted(seed, rgb, "Metal021", 1.0, 0.4)
    F.edges(t, 0, 0, N, N, 2)
    if top:
        F.area(t, 20, 12, 44, 40, (40, 46, 48), h=0.7, smooth=0.85, f0=40)
        F.area(t, 20, 12, 44, 40, mul=(0.9, 1.0, 1.05))
        wire = grid_mask(5, 1, 2)
        sub = np.zeros((N, N), bool)
        sub[12:40, 20:44] = True
        t.c[wire & sub] *= 1.6
        F.edges(t, 18, 10, 46, 42, 2)
        if sign:
            label(t, 12, 48, 52, 58, sign, 6, (200, 196, 180))
    else:
        F.area(t, 6, 44, 58, 62, (150, 152, 148), h=0.9, f0=200, smooth=0.5)
        F.edges(t, 6, 44, 58, 62, 1)
        F.bar(t, 44, 2, 52, 6)
        F.fingers(t, 48, 4, 8)
    rust_runs(t, 4 if top else 6, 0.35)
    chips(t, 0.05, (80, 76, 70))
    return F.wear(t, 0.3, 8)


def cage_door(seed, top):
    t = cage_mesh(seed)
    if not top:
        F.area(t, 50, 0, 58, 10, (60, 62, 58), alpha=255, h=1.0)
        F.area(t, 52, 3, 56, 7, (180, 150, 60), alpha=255, f0=200)
    return t


def watertight_door(seed, top):
    """A ship's watertight door: white over grey, a rounded frame, dog levers down the edge."""
    t = painted(seed, (204, 202, 192), "PaintedMetal001", 0.9, 0.45)
    yy, xx = np.mgrid[0:N, 0:N]
    if top:
        inside = (xx > 6) & (xx < 58) & (yy > 6)
        corner = np.hypot(np.clip(np.abs(xx - 32) - 14, 0, None), np.clip(18 - yy, 0, None)) < 12
        F.area(t, 0, 0, N, N, mul=0.82)
        t.c[inside & corner] /= 0.82
        stencil(t, "KEEP|CLOSED", (30, 30, 28), 8, 12, 20, 40, 28, chip=0.1)
    else:
        F.area(t, 0, 0, 6, N, mul=0.82)
        F.area(t, 58, 0, N, N, mul=0.82)
        F.area(t, 0, 54, N, N, mul=0.82)
    for y in ((14, 40) if top else (10, 36)):
        F.area(t, 50, y, 60, y + 4, (60, 60, 58), h=1.0, f0=180)
        F.area(t, 56, y - 2, 60, y + 6, (60, 60, 58), h=1.0, f0=180)
    if not top:
        F.area(t, 26, 0, 30, 18, (176, 30, 24), h=1.0)
    rust_runs(t, 5, 0.35)
    return F.wear(t, 0.3, 6)


class Big:
    """A drawing several blocks across, cut into one texture per block."""

    def __init__(self, seed, cols, rows, rgb):
        self.cols, self.rows = cols, rows
        self.parts = [[None] * cols for _ in range(rows)]
        self.seed = seed
        self.rgb = rgb

    def part(self, col, row, draw):
        t = painted(self.seed + row * 10 + col, self.rgb, "Metal019", 1.0, 0.35)
        draw(t, col * N, row * N)
        return t


def blast(seed, col, row):
    """One block of the blast door, two wide and three tall. Drawn across the whole door so the
    parts meet: a riveted border band, the hinges on the left, locking dogs round the edge, the
    wheel's shadow ring at the seam of the middle row, hazard stripes at the foot, a stencil."""
    W, H = 2 * N, 3 * N

    def draw(t, ox, oy):
        yy, xx = np.mgrid[0:N, 0:N]
        X, Y = xx + ox, yy + oy
        band = (X < 10) | (X >= W - 10) | (Y < 10) | (Y >= H - 10)
        t.c[band] *= 0.8
        t.h[band] = 0.95
        edge = ((X == 10) | (X == W - 11) | (Y == 10) | (Y == H - 11)) & ~((X < 10) | (X >= W - 10) | (Y < 10) | (Y >= H - 10))
        t.c[edge] *= 0.5
        for k in range(6, max(W, H), 10):
            for (px, py) in ((5, k), (W - 6, k), (k, 5), (k, H - 6)):
                if px < W and py < H:
                    lx, ly = px - ox, py - oy
                    if 0 <= lx < N and 0 <= ly < N:
                        rivets(t, (lx,), (ly,))
        for hy in (30, 96, 162):  # hinges, on the viewer's left
            lx, ly = 0 - ox, hy - oy
            F.area(t, lx, ly - 8, lx + 8, ly + 8, (60, 62, 58), h=1.0, f0=150)
            F.area(t, lx, ly - 8, lx + 8, ly - 6, mul=1.3)
        for dy in (24, 72, 120, 168):  # locking dogs on the right edge
            lx, ly = W - 9 - ox, dy - oy
            F.area(t, lx, ly - 3, lx + 9, ly + 3, (70, 72, 66), h=1.0, f0=150)
        d = np.hypot(X - W / 2, Y - H / 2)
        ring = (d < 30) & (d > 24)
        t.c[ring] *= 0.7
        stripes = (Y > H - 30) & (Y < H - 12) & (X > 12) & (X < W - 12)
        on = (((X + Y) // 8) % 2) == 0
        t.c[stripes & on] = (200, 160, 30)
        t.c[stripes & ~on] = (26, 26, 24)
        if row == 0:
            stencil(t, "SHELTER", (210, 206, 194), 11, 12 - ox, 18, 104, 20, chip=0.2)
        rust_runs(t, 6, 0.5)
        chips(t, 0.06, (84, 80, 72))

    t = Big(seed, 2, 3, (92, 102, 94)).part(col, row, draw)
    return F.wear(t, 0.3, 10)


def hatch(seed):
    """The forest hatch: olive paint over diamond plate gone to rust, a raised frame, a grab bar,
    the hinge along the top, a few leaves."""
    t = painted(seed, (84, 90, 62), "PaintedMetal001", 1.0, 0.3)
    dp = F.mat("DiamondPlate006C", None, 1.0, seed=seed + 1)
    inner = np.zeros((N, N), bool)
    inner[6:58, 6:58] = True
    t.h[inner] = dp.h[inner] * 0.6 + 0.3
    t.c[inner] *= (0.85 + 0.3 * dp.h[inner])[..., None]
    r = F.mat("rusty_metal", (110, 76, 52), 1.0, seed=seed + 2)
    m = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4))) * 1.8 - 0.6, 0, 0.85)
    t.c = t.c * (1 - m[..., None]) + r.c * m[..., None]
    F.edges(t, 0, 0, N, N, 4)
    F.edges(t, 6, 6, 58, 58, 1, light=0.7, dark=1.2)
    F.bar(t, 22, 46, 42, 50, (70, 72, 64))
    F.area(t, 6, 2, 58, 6, (58, 60, 50), h=1.0)
    rust_runs(t, 6, 0.3, top=6)
    rng = t.rng
    for _ in range(10):
        x, y = rng.uniform(0, N, 2)
        F.disc(t, x, y, rng.uniform(1.2, 2.6), [(120, 80, 40), (90, 70, 40), (70, 60, 36), (130, 100, 50)][rng.integers(4)], h=1.0)
    t.grime(0.3, (40, 34, 24))
    return t


def wood_hatch(seed):
    """A house's cellar hatch: old planks gone grey at the ends and each its own shade, dirt in
    the seams, strap hinges along the top edge, an iron ring to lift it by, scuffed and
    trodden-in where feet cross it."""
    t = F.mat("Wood089", (112, 88, 62), 1.1, seed=seed, smooth=0.2)
    rng = t.rng
    for x0 in range(0, N, 16):
        F.area(t, x0, 0, x0 + 16, N, mul=rng.uniform(0.82, 1.08))
    for x in (15, 31, 47):
        F.gap(t, x, 0, x + 1, N, dark=0.55)
    F.edges(t, 0, 0, N, N, 2)
    for x0 in (6, 42):
        F.area(t, x0, 3, x0 + 16, 7, (56, 50, 44), h=1.0, f0=180)
        F.disc(t, x0 + 3, 5, 1.2, (96, 84, 70), h=1.0)
        F.area(t, x0 + 9, 7, x0 + 14, 11, mul=(0.86, 0.74, 0.66))  # rust run off the strap
    F.disc(t, 32, 51, 4.5, (58, 54, 48), h=1.0, f0=200, ring=1.4)
    F.area(t, 30, 44, 34, 48, (58, 54, 48), h=1.0, f0=200)
    F.area(t, 12, 20, 52, 42, mul=1.14, smooth=0.3)
    for _ in range(3):
        F.disc(t, rng.uniform(16, 48), rng.uniform(18, 44), rng.uniform(4, 7), mul=0.78, soft=5)
    return F.wear(t, 0.5, 14)


def collar_top(seed):
    """The concrete collar round the hatch: cracked, mossed, mostly under leaf litter."""
    t = F.mat("Concrete031", (124, 122, 112), 1.0, seed=seed, smooth=0.15)
    t.crack(3, 0.25)
    moss = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4))) * 2.5 - 0.2, 0, 1)
    t.c = t.c * (1 - moss[..., None]) + np.array((62, 78, 40), np.float32) * moss[..., None]
    rng = t.rng
    for _ in range(40):
        x, y = rng.uniform(0, N, 2)
        F.disc(t, x, y, rng.uniform(1.5, 3), [(118, 76, 34), (96, 66, 34), (140, 100, 46)][rng.integers(3)], h=1.0)
    return t


def rusty(seed, rgb=(110, 74, 50), base=(104, 108, 102)):
    """Painted steel gone mostly to rust: the paint holds on in patches."""
    t = painted(seed, base, "PaintedMetal001", 1.0, 0.3)
    r = F.mat("rusty_painted_metal", rgb, 0.9, seed=seed + 1, smooth=0.2)
    m = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4))) * 2.0 - 0.45, 0, 1)
    t.c = t.c * (1 - m[..., None]) + r.c * m[..., None]
    t.smooth = t.smooth * (1 - m * 0.7)
    t.f0[:] = 60
    return t


def cable_post(seed):
    t = F.mat("Concrete031", (150, 146, 136), 1.0, seed=seed, smooth=0.2)
    F.area(t, 0, 0, N, 22, (200, 96, 30), smooth=0.4)
    chips(t, 0.1, (140, 136, 126))
    m = F.text_mask("CABLE", SANS, 9, N, 14, seed=seed)
    F.paste(t, m, 0, 4, (24, 22, 20))
    t.grime(0.4, (60, 54, 36))
    return t


# ---------------------------------------------------------------- the rooms

def blanket(seed):
    t = F.mat("Fabric023", (104, 104, 96), 1.2, seed=seed, smooth=0.05)
    for y in (8, 12, 52, 56):
        F.area(t, 0, y, N, y + 2, (52, 50, 46))
    F.stain(t, t.rng.uniform(10, 54), t.rng.uniform(20, 44), 9, (80, 66, 46), 0.35)
    return t


def ticking(seed, stained=True):
    """A striped mattress cover, flattened and stained."""
    t = F.mat("Fabric022", (196, 192, 178), 1.0, seed=seed, smooth=0.1)
    for x in range(0, N, 8):
        F.area(t, x, 0, x + 2, N, (70, 86, 120))
    if stained:
        F.stain(t, t.rng.uniform(10, 54), t.rng.uniform(10, 54), 14, (120, 96, 56), 0.4)
    return t


def footlocker(seed):
    t = painted(seed, OLIVE, "PaintedMetal001", 1.0, 0.3)
    F.edges(t, 0, 0, N, N, 2)
    F.area(t, 0, 14, N, 16, mul=0.6)
    F.area(t, 26, 16, 38, 28, (60, 60, 56), h=1.0, f0=180)
    F.area(t, 30, 22, 34, 32, (180, 150, 60), h=1.0, f0=220)
    stencil(t, "PVT. HALE|R. 4471", (210, 206, 190), 7, 0, 32, N, 26, chip=0.15)
    return F.wear(t, 0.3, 8)


def locker(seed, top):
    """A grey steel wall locker: louvres at top and bottom, the handle, a name card slot."""
    t = painted(seed, (118, 126, 124), "PaintedMetal001", 1.0, 0.35)
    F.edges(t, 0, 0, N, N, 2)
    F.gap(t, 31, 0, 33, N)
    for x0 in (4, 36):
        rng_y = range(6, 22, 4) if top else range(44, 60, 4)
        for y in rng_y:
            F.area(t, x0 + 4, y, x0 + 20, y + 2, (30, 30, 28), dh=-0.3)
            F.area(t, x0 + 4, y + 2, x0 + 20, y + 3, mul=1.3)
        if top:
            label(t, x0 + 6, 30, x0 + 18, 36, None)
        else:
            F.area(t, x0 + 20, 6, x0 + 24, 20, (60, 62, 60), h=1.0, f0=200)
    if not top and t.rng.random() < 0.5:
        F.area(t, 8, 26, 20, 38, mul=0.7, dh=-0.2)  # a dent
    return F.wear(t, 0.3, 10)


def locker_open(seed):
    """The locker with one door wrenched off: an empty shelf, a coat hanger, a sock."""
    t = locker(seed, False)
    F.area(t, 2, 2, 31, N - 2, (34, 34, 32), h=0.2)
    F.area(t, 2, 18, 31, 20, (96, 100, 98), h=0.5)
    F.area(t, 2, 20, 31, 21, (20, 20, 18))
    F.area(t, 14, 4, 16, 9, (150, 150, 146), h=0.4)
    F.area(t, 8, 9, 22, 10, (150, 150, 146), h=0.4)
    F.area(t, 6, 52, 14, 56, (120, 110, 96), h=0.3)
    return t


def range_front(seed):
    t = F.steel(seed)
    F.edges(t, 0, 0, N, N, 1)
    for x0 in (3, 33):
        F.area(t, x0, 18, x0 + 28, 58, mul=0.9)
        F.gap(t, x0, 18, x0 + 28, 58)
        F.bar(t, x0 + 4, 22, x0 + 24, 25)
    for x in range(8, 60, 10):
        F.disc(t, x, 8, 3, (30, 30, 28), h=1.0)
    t.grime(0.45, (70, 50, 30))
    return t


def range_top(seed):
    t = F.steel(seed, (130, 130, 128))
    for cx, cy in ((16, 16), (48, 16), (16, 48), (48, 48)):
        F.disc(t, cx, cy, 13, (30, 28, 26), h=0.9)
        F.disc(t, cx, cy, 13, ring=2, mul=1.5)
        for a in range(0, 360, 45):
            r = np.radians(a)
            F.area(t, cx + np.cos(r) * 4, cy + np.sin(r) * 4, cx + np.cos(r) * 11 + 1, cy + np.sin(r) * 11 + 1, (60, 58, 54))
    t.grime(0.5, (60, 40, 20))
    return t


def counter_front(seed):
    """A steel prep counter's open shelf under the top: stacked pans, cans, a sack."""
    t = F.steel(seed)
    F.area(t, 2, 10, 62, 60, (34, 32, 30), h=0.5)
    F.area(t, 2, 34, 62, 36, (150, 152, 150), h=0.9, f0=200)
    rng = t.rng
    for y1 in (34, 60):
        x = 4
        while x < 58:
            w = int(rng.integers(5, 10))
            h = int(rng.integers(8, 20))
            col = [(170, 170, 166), (176, 36, 30), (60, 120, 60), (220, 170, 40), (190, 180, 150)][rng.integers(5)]
            F.area(t, x, y1 - h, x + w, y1, col, h=0.85)
            F.area(t, x, y1 - h, x + 1, y1, mul=1.3)
            x += w + int(rng.integers(0, 3))
    return F.wear(t, 0.35, 6)


def hazmat(seed, top):
    """A yellow chemical suit hung on its hook: the hood and visor, the arms, the boots."""
    t = clear(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    if top:
        body = (np.abs(xx - 32) < 16) & (yy > 22)
        hood = np.hypot(xx - 32, yy - 16) < 12
        arms = ((np.abs(xx - 12) < 5) | (np.abs(xx - 52) < 5)) & (yy > 26)
        m = body | hood | arms
        solid(t, m.astype(np.float32), (214, 180, 40), h=0.9, vary=0.12)
        visor = (np.abs(xx - 32) < 8) & (np.abs(yy - 16) < 5)
        t.c[visor] = (60, 70, 76)
        t.smooth[visor] = 0.9
        F.area(t, 30, 0, 34, 6, (60, 60, 58), alpha=255)
        t.alpha[0:6, 30:34] = 255
    else:
        legs = ((np.abs(xx - 24) < 7) | (np.abs(xx - 40) < 7)) & (yy < 50)
        hips = (np.abs(xx - 32) < 16) & (yy < 10)
        boots = ((np.abs(xx - 24) < 8) | (np.abs(xx - 40) < 8)) & (yy >= 50) & (yy < 62)
        solid(t, (legs | hips).astype(np.float32), (214, 180, 40), h=0.9, vary=0.12)
        solid(t, boots.astype(np.float32), (30, 30, 28), h=0.9)
        gloves = ((np.abs(xx - 12) < 5) | (np.abs(xx - 52) < 5)) & (yy < 10)
        solid(t, gloves.astype(np.float32), (30, 30, 28), h=0.9)
    folds = (P.fbm(t.rng, ((8, 1.0), (16, 0.5))) > 0.3) & (t.alpha > 0)
    t.c[folds] *= 0.85
    return t


def desk_front(seed):
    """A steel office desk in grey-green: a pedestal of drawers, the knee hole, the modesty panel."""
    t = painted(seed, (110, 118, 104), "PaintedMetal001", 1.0, 0.35)
    F.edges(t, 0, 0, N, N, 1)
    F.area(t, 2, 10, 34, N, (30, 30, 28), h=0.4)
    for y0, y1 in ((10, 26), (26, 42), (42, N - 2)):
        F.gap(t, 36, y0, 62, y1)
        F.bar(t, 44, y0 + 4, 54, y0 + 6, (170, 170, 166))
    F.gap(t, 2, 2, 62, 10)
    return F.wear(t, 0.3, 10)


def desk_top(seed):
    t = painted(seed, (90, 98, 90), "PaintedMetal001", 1.0, 0.3)
    F.area(t, 10, 14, 50, 46, (60, 70, 50), h=0.8)  # the blotter
    F.area(t, 14, 20, 30, 40, (220, 214, 196))
    F.area(t, 32, 22, 46, 38, (214, 208, 188))
    for y in range(24, 38, 3):
        F.area(t, 16, y, 28, y + 1, (60, 60, 70))
    F.stain(t, 50, 50, 5, (80, 60, 40), 0.5)  # a mug ring
    return F.wear(t, 0.3, 10)


def radio_front(seed):
    """A military set: olive face, two meters, a band dial, rows of switches, a data plate."""
    t = painted(seed, (72, 80, 58), "PaintedMetal001", 1.0, 0.3)
    F.edges(t, 0, 0, N, N, 2)
    for cx in (14, 34):
        F.area(t, cx - 8, 6, cx + 8, 18, (24, 24, 22))
        F.area(t, cx - 6, 8, cx + 6, 16, (210, 200, 160), h=0.8)
        F.area(t, cx - 2, 9, cx - 1, 15, (30, 30, 28))
    F.area(t, 44, 6, 60, 18, (30, 30, 28))
    F.area(t, 46, 9, 58, 15, (180, 120, 40))
    for x in range(8, 60, 9):
        F.disc(t, x, 30, 3.5, (30, 30, 28), h=1.0)
        F.area(t, x - 0.5, 26, x + 0.5, 30, (200, 196, 180))
    for x in range(8, 60, 6):
        F.area(t, x, 42, x + 2, 48, (190, 190, 184), h=1.0, f0=220)
    label(t, 8, 52, 40, 60, "SET 2", 6, (180, 176, 160))
    return F.wear(t, 0.3, 8)


def county_map(seed, wall=False):
    """A survey map of the county, pinned and marked up: grid, roads, the river, red circles."""
    t = F.mat("Paper001", (214, 206, 180), 1.0, seed=seed, smooth=0.1)
    rng = t.rng
    green = np.clip(P.fbm(rng, ((4, 1.0), (8, 0.5))) * 2, 0, 1)
    t.c = t.c * (1 - green[..., None] * 0.35) + np.array((120, 150, 100), np.float32) * green[..., None] * 0.35
    for k in range(0, N, 8):
        F.area(t, k, 0, k + 1, N, mul=0.9)
        F.area(t, 0, k, N, k + 1, mul=0.9)
    xs = np.linspace(0, N, 40)
    ys = 30 + 14 * np.sin(xs / 9 + rng.uniform(0, 6))
    for x, y in zip(xs, ys):
        F.area(t, x, y, x + 2, y + 2, (60, 100, 160))
    for _ in range(4):
        x0, y0 = rng.uniform(0, N, 2)
        ang = rng.uniform(0, np.pi)
        for i in range(60):
            F.area(t, x0 + np.cos(ang) * i, y0 + np.sin(ang) * i, x0 + np.cos(ang) * i + 1, y0 + np.sin(ang) * i + 1,
                   (170, 60, 40))
    F.disc(t, rng.uniform(16, 48), rng.uniform(16, 48), 9, ring=1.5, col=(200, 30, 24))
    for _ in range(5):
        F.disc(t, rng.uniform(4, 60), rng.uniform(4, 60), 1.2, (200, 30, 24) if rng.random() < 0.6 else (30, 60, 180), h=1.0)
    if wall:
        for x, y in ((6, 50), (42, 6)):
            F.area(t, x, y, x + 16, y + 10, (236, 226, 120))
            for k in range(3):
                F.area(t, x + 2, y + 2 + k * 3, x + 14, y + 3 + k * 3, (60, 60, 70))
    t.grime(0.25, (120, 100, 60))
    return t


def medcab(seed):
    """A white cabinet with glass doors: shelves of bottles, boxes and dressings."""
    t = F.enamel(seed, (214, 214, 206))
    F.edges(t, 0, 0, N, N, 2)
    F.area(t, 4, 4, 60, 60, (60, 66, 66), h=0.6, smooth=0.9)
    rng = t.rng
    for y1 in (20, 38, 58):
        F.area(t, 4, y1, 60, y1 + 2, (200, 200, 194))
        x = 6
        while x < 56:
            w = int(rng.integers(3, 8))
            h = int(rng.integers(6, 15))
            col = [(120, 70, 30), (220, 218, 210), (60, 110, 150), (200, 60, 50), (230, 230, 220)][rng.integers(5)]
            F.area(t, x, y1 - h, x + w, y1, col, h=0.8)
            x += w + int(rng.integers(1, 4))
    F.gap(t, 31, 4, 33, 60)
    F.area(t, 6, 4, 10, 60, mul=1.25)
    return F.wear(t, 0.3, 6)


def iv_bag(seed):
    t = F.plain(seed, (200, 210, 206), 0.03, 0.85)
    F.area(t, 0, 20, N, N, (170, 190, 160))
    label(t, 12, 8, 52, 18, "SALINE", 6)
    return t


def white_steel(seed):
    t = F.enamel(seed, (206, 206, 198))
    chips(t, 0.06, (90, 84, 76))
    return F.wear(t, 0.25, 6)


# ---------------------------------------------------------------- storage and armoury

def rack_paint(seed, rgb, holes):
    t = F.plain(seed, rgb, 0.05, 0.45)
    t.f0[:] = 30
    if holes:
        for y in range(2, N, 6):
            F.area(t, 24, y, 40, y + 3, (20, 20, 18), dh=-0.3)
    return F.wear(t, 0.3, 8)


def pallet(seed):
    t = F.wood(seed, "pine")
    for x0, x1 in ((0, 10), (27, 37), (54, N)):
        F.area(t, x0, 0, x1, N, mul=0.85)
    F.area(t, 10, 20, 27, N, (20, 18, 16))
    F.area(t, 37, 20, 54, N, (20, 18, 16))
    return F.wear(t, 0.35, 6)


def load(seed, kind):
    """What sits on a pallet rack's deck, seen from the aisle."""
    t = F.plain(seed, (30, 28, 26), 0.05, 0.2)
    rng = t.rng
    if kind == "boxes":
        for y0 in (4, 34):
            x = 2
            while x < 60:
                w = int(rng.integers(14, 22))
                F.area(t, x, y0, min(x + w, 62), y0 + 28, (176, 142, 96), h=0.9)
                F.edges(t, x, y0, min(x + w, 62), y0 + 28, 1)
                F.area(t, x, y0 + 12, min(x + w, 62), y0 + 15, (150, 120, 80))
                txt = ["RATIONS", "WATER", "MRE", "BATT", "MED"][rng.integers(5)]
                m = F.text_mask(txt, SANS, 6, min(w, 62 - x), 10, seed=x + y0)
                F.paste(t, m, x, y0 + 16, (40, 32, 24))
                x += w + 1
        t.c[:, :, :] = t.c * (0.92 + 0.08 * (grid_mask(7, 1)[..., None]))  # shrink-wrap sheen
        t.smooth[:] = 0.55
    elif kind == "drums":
        for x0 in (2, 22, 42):
            col = [(48, 70, 120), (70, 80, 56), (48, 70, 120)][rng.integers(3)]
            F.area(t, x0, 2, x0 + 20, 62, col, h=0.9, smooth=0.4)
            for y in (16, 46):
                F.area(t, x0, y, x0 + 20, y + 2, mul=0.6, dh=0.05)
            F.area(t, x0, 2, x0 + 3, 62, mul=0.7)
            F.area(t, x0 + 17, 2, x0 + 20, 62, mul=0.7)
            F.area(t, x0 + 5, 2, x0 + 8, 62, mul=1.3)
    else:  # crates
        for y0 in (2, 34):
            for x0 in (2, 32):
                F.area(t, x0, y0, x0 + 29, y0 + 29, OLIVE, h=0.9)
                F.edges(t, x0, y0, x0 + 29, y0 + 29, 2)
                m = F.text_mask(["AMMO", "TOOLS", "PARTS", "FUSES"][rng.integers(4)], SANS, 7, 29, 12, seed=x0 + y0)
                F.paste(t, m, x0, y0 + 9, (220, 200, 120))
    return F.wear(t, 0.3, 6)


def crate_stencil(seed, text, base=None, ink=(30, 28, 24), size=9):
    if base is None:
        t = F.mat("Wood089", (150, 120, 84), 1.1, seed=seed, smooth=0.2)
        for y in (0, 21, 42, 63):
            F.area(t, 0, y, N, y + 1, (40, 30, 20), dh=-0.3)
    else:
        t = painted(seed, base, "PaintedMetal001", 1.0, 0.3)
        F.edges(t, 0, 0, N, N, 3)
    F.area(t, 0, 0, 4, N, mul=0.75)
    F.area(t, N - 4, 0, N, N, mul=0.75)
    stencil(t, text, ink, size, 4, 10, 56, 44, chip=0.2)
    return F.wear(t, 0.3, 6)


def drum(seed, rgb, text):
    t = F.plain(seed, rgb, 0.05, 0.4)
    t.f0[:] = 40
    for y in (8, 30, 52):
        F.area(t, 0, y, N, y + 3, mul=0.65, dh=0.1)
        F.area(t, 0, y, N, y + 1, mul=1.5)
    xx = np.arange(N)[None, :]
    t.c *= (0.75 + 0.35 * np.sin(xx / N * np.pi * 2) ** 2)[..., None]
    if text:
        stencil(t, text, (226, 222, 208), 8, 0, 14, N, 34, chip=0.2)
    rust_runs(t, 4, 0.4)
    return F.wear(t, 0.3, 6)


def drum_top(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.4)
    F.disc(t, 32, 32, 30, ring=3, mul=0.6)
    F.disc(t, 20, 20, 4, (60, 60, 58), h=1.0)
    F.disc(t, 44, 44, 3, (60, 60, 58), h=1.0)
    return F.wear(t, 0.3, 4)


def jerry(seed, rgb):
    t = painted(seed, rgb, "PaintedMetal001", 1.0, 0.35)
    F.edges(t, 0, 0, N, N, 2)
    yy, xx = np.mgrid[0:N, 0:N]
    for d in (xx - yy, xx + yy - N):
        m = (np.abs(d) < 3) & (xx > 6) & (xx < 58) & (yy > 6) & (yy < 58)
        t.h[m] = 0.95
        t.c[m] *= 1.12
    return F.wear(t, 0.3, 8)


def rifles(seed):
    """Four long guns standing in the rack, seen side on: dark receivers, some wooden stocks."""
    t = clear(seed)
    rng = t.rng
    for i, x in enumerate((7, 23, 39, 55)):
        if rng.random() < 0.15:
            continue  # an empty slot
        wood = rng.random() < 0.5
        stock = (110, 70, 40) if wood else (34, 34, 32)
        F.area(t, x - 3, 40, x + 3, 62, stock, alpha=255, h=0.9)  # the butt
        F.area(t, x - 2, 22, x + 2, 40, (40, 40, 38), alpha=255, h=0.9)  # receiver
        F.area(t, x - 1, 2, x + 1, 22, (30, 30, 30), alpha=255, h=0.9, f0=200)  # barrel
        F.area(t, x + 2, 28, x + 6, 36, (36, 36, 34), alpha=255)  # magazine
        if wood:
            F.area(t, x - 2, 12, x + 2, 24, stock, alpha=255)
        F.area(t, x - 1, 22, x, 62, mul=1.4)
    return t


def pegboard(seed):
    """Brown pegboard with marker outlines where the tools were; a few pistols still hung."""
    t = F.plain(seed, (130, 100, 70), 0.06, 0.15)
    for y in range(2, N, 5):
        for x in range(2, N, 5):
            F.area(t, x, y, x + 1, y + 1, (40, 30, 22), dh=-0.3)
    rng = t.rng
    for (x, y) in ((6, 8), (34, 8), (6, 36), (34, 36)):
        if rng.random() < 0.55:
            F.area(t, x, y, x + 18, y + 6, (34, 34, 32), h=1.0)
            F.area(t, x, y + 6, x + 6, y + 18, (34, 34, 32), h=1.0)
        else:
            F.area(t, x, y, x + 18, y + 1, (20, 20, 20))
            F.area(t, x, y + 6, x + 1, y + 18, (20, 20, 20))
            F.area(t, x + 6, y + 6, x + 7, y + 18, (20, 20, 20))
            F.area(t, x + 18, y, x + 19, y + 6, (20, 20, 20))
    return F.wear(t, 0.3, 6)


# ---------------------------------------------------------------- machinery

def skid(seed):
    t = F.plain(seed, (190, 150, 36), 0.05, 0.4)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) // 8) % 2 == 0] = (30, 30, 28)
    return F.wear(t, 0.4, 8)


def gen_side(seed, part):
    """The generator, side on. a: the diesel, its cylinder block, filters, belt and the radiator's
    grille; b: the alternator's ribbed drum and the control box's gauges and key."""
    t = painted(seed, (54, 86, 64), "PaintedMetal001", 1.0, 0.4)
    if part == "a":
        F.area(t, 2, 4, 20, 60, (40, 40, 38), h=0.6)
        for y in range(6, 58, 3):
            F.area(t, 3, y, 19, y + 1, (70, 70, 66))
        for x0 in (26, 38, 50):
            F.area(t, x0, 6, x0 + 9, 22, (60, 92, 70), h=1.0)
            F.edges(t, x0, 6, x0 + 9, 22, 1)
        F.disc(t, 32, 42, 6, (180, 150, 40), h=1.0)
        F.disc(t, 50, 42, 7, (40, 40, 38), h=1.0)
        F.area(t, 32, 36, 50, 38, (24, 24, 22))
        F.area(t, 32, 47, 50, 49, (24, 24, 22))
    else:
        for x in range(2, 34, 4):
            F.area(t, x, 8, x + 2, 56, mul=0.7, dh=-0.1)
        F.area(t, 38, 4, 62, 60, (34, 34, 32), h=0.9)
        for cx, cy in ((44, 14), (56, 14)):
            F.disc(t, cx, cy, 5, (220, 214, 196))
            F.area(t, cx - 0.5, cy - 4, cx + 0.5, cy, (180, 30, 24))
        F.area(t, 42, 26, 58, 32, (90, 30, 26), h=1.0)
        F.area(t, 42, 36, 58, 42, (40, 110, 50), h=1.0)
        m = F.text_mask("STOP|START", SANS, 5, 16, 18, seed=seed)
        F.paste(t, m, 42, 24, (220, 216, 200))
        F.disc(t, 50, 52, 3, (180, 150, 70), h=1.0, f0=220)
    t.grime(0.45, (40, 30, 20))
    return F.wear(t, 0.3, 8)


def filter_front(seed):
    """The air filtration unit: olive box, the big hand crank for when the power fails, the airflow
    gauge, a data plate and a louvred intake."""
    t = painted(seed, (86, 94, 72), "PaintedMetal001", 1.0, 0.35)
    F.edges(t, 0, 0, N, N, 2)
    F.disc(t, 22, 24, 13, ring=2, col=(40, 40, 38))
    F.area(t, 21, 11, 23, 24, (40, 40, 38))
    F.disc(t, 22, 24, 3, (40, 40, 38), h=1.0)
    F.area(t, 33, 22, 38, 26, (176, 30, 24), h=1.0)
    F.disc(t, 48, 18, 7, (220, 214, 196))
    F.area(t, 47.5, 12, 48.5, 18, (30, 30, 28))
    label(t, 38, 30, 60, 38, "AIRFLOW", 5)
    for y in range(44, 60, 3):
        F.area(t, 6, y, 58, y + 1, (30, 30, 28), dh=-0.2)
    return F.wear(t, 0.3, 8)


def tank(seed):
    t = drum(seed, (122, 54, 36), "DIESEL")
    F.area(t, 54, 6, 58, 58, (60, 60, 58), h=1.0)
    F.area(t, 55, 30, 57, 58, (150, 110, 40), smooth=0.8)
    return t


def fan(seed):
    t = painted(seed, (110, 116, 110), "PaintedMetal001", 1.0, 0.35)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 32, yy - 32)
    F.area(t, 0, 0, N, N, mul=1.0)
    inner = d < 28
    t.c[inner] = (26, 26, 24)
    ang = np.arctan2(yy - 32, xx - 32)
    blades = inner & (np.sin(3 * ang + d / 12) > 0.35) & (d > 5)
    t.c[blades] = (130, 134, 128)
    t.h[blades] = 0.9
    rings = inner & (np.abs((d % 6) - 3) < 0.6)
    t.c[rings] = (70, 72, 68)
    t.h[rings] = 1.0
    F.disc(t, 32, 32, 5, (60, 60, 58), h=1.0)
    t.grime(0.5, (50, 44, 36))
    return t


def switchboard(seed, top):
    t = painted(seed, (120, 126, 122), "PaintedMetal001", 1.0, 0.35)
    F.edges(t, 0, 0, N, N, 2)
    F.gap(t, 31, 0, 33, N)
    if top:
        for cx in (16, 48):
            F.disc(t, cx, 18, 9, (226, 220, 204))
            F.disc(t, cx, 18, 9, ring=1.5, col=(30, 30, 28))
            F.area(t, cx - 0.5, 11, cx + 0.5, 18, (30, 30, 28))
        for x in (10, 42):
            label(t, x, 34, x + 14, 40, None)
        yy, xx = np.mgrid[0:N, 0:N]
        tri = (yy > 44) & (yy < 58) & (np.abs(xx - 32) < (yy - 44) * 0.6)
        t.c[tri] = (220, 178, 30)
    else:
        for x0 in (8, 40):
            F.area(t, x0, 10, x0 + 16, 34, (40, 40, 38), h=0.7)
            F.area(t, x0 + 6, 6, x0 + 10, 26, (176, 30, 24), h=1.0)
            F.area(t, x0 + 2, 6, x0 + 14, 9, (176, 30, 24), h=1.0)
        F.area(t, 6, 44, 58, 58, mul=0.8)
        for y in range(46, 58, 3):
            F.area(t, 8, y, 56, y + 1, (30, 30, 28), dh=-0.2)
    return F.wear(t, 0.3, 10)


# ---------------------------------------------------------------- story pieces

def body_bag(seed):
    t = F.plain(seed, (28, 30, 30), 0.08, 0.55)
    t.f0[:] = 30
    folds = P.fbm(t.rng, ((4, 1.0), (8, 0.8), (16, 0.4)))
    t.c *= (1 + folds * 0.35)[..., None]
    t.h += folds * 0.1
    F.area(t, 31, 0, 33, N, (120, 120, 116), h=1.0, f0=200)
    for y in range(0, N, 3):
        F.area(t, 30, y, 34, y + 1, (70, 70, 66))
    F.area(t, 6, 10, 10, 54, (44, 46, 46), h=0.9)
    F.area(t, 54, 10, 58, 54, (44, 46, 46), h=0.9)
    F.blood(t, t.rng.uniform(10, 54), t.rng.uniform(40, 60), 6, 0.6)
    return t


def casings(seed):
    t = clear(seed)
    rng = t.rng
    for _ in range(26):
        x, y = rng.uniform(2, 62, 2)
        ang = rng.uniform(0, np.pi)
        for i in range(5):
            xi, yi = int(x + np.cos(ang) * i * 0.8), int(y + np.sin(ang) * i * 0.8)
            if 0 <= xi < N - 1 and 0 <= yi < N - 1:
                t.c[yi:yi + 2, xi:xi + 2] = (190, 150, 70) if i < 4 else (120, 90, 50)
                t.alpha[yi:yi + 2, xi:xi + 2] = 255
                t.f0[yi:yi + 2, xi:xi + 2] = 220
                t.smooth[yi:yi + 2, xi:xi + 2] = 0.75
    return t


def wire(seed):
    t = F.plain(seed, (150, 146, 136), 0.05, 0.5)
    t.f0[:] = 200
    return t


# ---------------------------------------------------------------- ship

def ship_wall(seed, rgb, frames=True):
    """A ship's painted steel wall: weld seams and stiffeners showing through the coats."""
    t = painted(seed, rgb, "PaintedMetal001", 0.9, 0.45)
    for y in (0, 32):
        F.area(t, 0, y, N, y + 1, mul=0.75, dh=0.06)
        F.area(t, 0, y + 1, N, y + 2, mul=1.1, dh=0.06)
    if frames:
        F.area(t, 30, 0, 34, N, mul=0.9, dh=0.08)
        F.area(t, 30, 0, 31, N, mul=1.2)
    rust_runs(t, 5, 0.4)
    return F.wear(t, 0.25, 6)


def hold_wall(seed):
    """The inside of a cargo hold: red-oxide primer, frames every block, rust and scrape marks."""
    t = F.mat("rusty_painted_metal", (126, 58, 40), 1.1, seed=seed, smooth=0.25)
    F.area(t, 26, 0, 38, N, mul=0.8, dh=0.15)
    F.area(t, 26, 0, 28, N, mul=1.3)
    F.area(t, 36, 0, 38, N, mul=0.5)
    rng = t.rng
    for _ in range(8):
        y = rng.uniform(0, N)
        F.area(t, 0, y, N, y + 1, (150, 140, 130))
    rust_runs(t, 10, 0.6)
    return t


def porthole(seed):
    t = ship_wall(seed, (208, 206, 196), frames=False)
    F.disc(t, 32, 32, 22, (80, 82, 78), h=1.0, f0=150)
    F.disc(t, 32, 32, 22, ring=2, mul=1.3)
    F.disc(t, 32, 32, 17, (40, 52, 60), h=0.7, smooth=0.95, f0=40)
    F.disc(t, 26, 26, 5, mul=1.5)
    for a in range(0, 360, 60):
        r = np.radians(a)
        F.disc(t, 32 + np.cos(r) * 20, 32 + np.sin(r) * 20, 1.6, (150, 120, 60), h=1.0, f0=200)
    rust_runs(t, 3, 0.5, 52)
    return t


def console(seed, top):
    t = painted(seed, (150, 160, 158), "PaintedMetal001", 1.0, 0.45)
    if top:
        for x0 in (4, 24, 44):
            F.area(t, x0, 6, x0 + 16, 22, (24, 30, 32), smooth=0.9)
            F.disc(t, x0 + 8, 40, 6, (226, 222, 210))
            F.area(t, x0 + 7.5, 35, x0 + 8.5, 40, (30, 30, 28))
        for x in range(6, 60, 6):
            F.area(t, x, 52, x + 3, 56, [(40, 140, 60), (180, 40, 30), (210, 180, 40)][x % 3], h=1.0)
    else:
        F.edges(t, 0, 0, N, N, 2)
        F.gap(t, 8, 10, 56, 58)
        F.bar(t, 28, 30, 36, 32)
    return F.wear(t, 0.3, 8)


def telegraph(seed):
    """The engine order telegraph's face: brass rim, the dial's sectors, the handle."""
    t = clear(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 32, yy - 32)
    solid(t, (d < 30).astype(np.float32), BRASS, h=1.0)
    t.f0[d < 30] = 230
    t.smooth[d < 30] = 0.7
    face = d < 24
    t.c[face] = (226, 220, 200)
    ang = np.arctan2(yy - 32, xx - 32)
    for k in range(-4, 5):
        line = face & (np.abs(ang - k * 0.33 + np.pi / 2) < 0.02) & (d > 8)
        t.c[line] = (30, 30, 28)
    lower = face & (yy > 32)
    t.c[lower] = (176, 40, 30)
    for i in range(22):
        F.area(t, 32 + i * 0.3, 32 - i, 34 + i * 0.3, 33 - i, (30, 30, 28), alpha=255)
    return t


def lifebuoy(seed):
    t = clear(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 32, yy - 32)
    ring = (d < 30) & (d > 16)
    solid(t, ring.astype(np.float32), (220, 90, 30), h=0.95, vary=0.1)
    ang = np.degrees(np.arctan2(yy - 32, xx - 32)) % 90
    t.c[ring & (ang < 18)] = (220, 216, 204)
    rope = (np.abs(d - 23) < 1) & ring
    t.c[rope] = (190, 180, 150)
    stencil(t, "MERIDIAN", (30, 30, 28), 5, 0, 0, N, 12, chip=0.05)
    t.alpha[(t.alpha < 255)] = 0
    return t


def engine(seed, part):
    """A slow diesel's flank in the engine room's grey-green, oily. lo: the crankcase, a big
    door per cylinder (a boss in the middle, bolts all round) over the bedplate. hi: the cylinder
    block (its bottom half shows), the fuel rail and the indicator cocks. top: the rocker covers."""
    t = painted(seed, (98, 114, 104), "PaintedMetal001", 1.0, 0.45)
    yy, xx = np.mgrid[0:N, 0:N]
    if part == "lo":
        for x0 in (3, 35):
            F.area(t, x0, 8, x0 + 26, 50, mul=0.92, dh=0.15)
            F.edges(t, x0, 8, x0 + 26, 50, 2)
            cx = x0 + 13
            for a in np.linspace(0, 2 * np.pi, 10, endpoint=False):
                F.disc(t, cx + np.cos(a) * 11, 29 + np.sin(a) * 17, 1.3, (58, 62, 56), h=1.0)
            F.disc(t, cx, 29, 5, mul=1.12, h=1.0)
            F.disc(t, cx, 29, 2, (50, 52, 48), h=1.0)
            F.area(t, cx - 8, 28, cx + 8, 30, mul=0.8)  # the dog bar across the door
        F.area(t, 0, 54, N, N, (46, 48, 44), dh=-0.1)
        F.area(t, 0, 54, N, 55, mul=1.4)
        for bx in range(4, N, 10):
            F.disc(t, bx, 59, 1.6, (70, 70, 64), h=1.0)
        F.drips(t, 6, 58, 50, 4, (4, 10), col=(26, 24, 20))
    elif part == "hi":
        for x0 in (2, 34):
            F.area(t, x0, 34, x0 + 28, 62, mul=0.9, dh=0.1)
            F.edges(t, x0, 34, x0 + 28, 62, 2)
            for bx in (x0 + 3, x0 + 25):
                for by in (37, 59):
                    F.disc(t, bx, by, 1.4, (58, 60, 56), h=1.0)
            F.area(t, x0 + 11, 40, x0 + 17, 48, (170, 150, 90), h=1.0, f0=200)  # the indicator cock
            F.area(t, x0 + 13, 48, x0 + 15, 56, (70, 72, 66), h=1.0)
        F.area(t, 0, 33, N, 35, (150, 120, 70), h=1.0, f0=200)  # the fuel rail
        F.area(t, 0, 33, N, 34, mul=1.3)
        F.area(t, 0, 0, N, 32, (84, 98, 90))
        for x0 in (4, 36):
            F.area(t, x0, 4, x0 + 24, 30, (90, 104, 96), h=1.0)
            F.edges(t, x0, 4, x0 + 24, 30, 2)
    else:  # top: the rocker covers seen from above
        for x0 in (4, 36):
            F.area(t, x0, 6, x0 + 24, 58, (90, 104, 96), h=1.0)
            F.edges(t, x0, 6, x0 + 24, 58, 2)
            F.disc(t, x0 + 12, 32, 3, (60, 62, 58), h=1.0)
    t.grime(0.3, (30, 26, 18))
    rust_runs(t, 3, 0.2)
    return t


def container_inside(seed):
    """Inside a shipping box: the corrugated walls' paint scraped by forklift tines."""
    t = F.mat("CorrugatedSteel006A", (150, 140, 126), 0.9, seed=seed, smooth=0.3)
    xx = np.arange(N)[None, :]
    t.c *= (0.8 + 0.3 * (np.sin(xx / N * np.pi * 8) > 0))[..., None]
    t.h = np.clip(0.6 + 0.3 * (np.sin(np.arange(N) / N * np.pi * 8) > 0)[None, :].astype(np.float32), 0, 1).repeat(N, 0)
    rng = t.rng
    for _ in range(10):
        y = rng.uniform(30, 62)
        x = rng.uniform(0, 40)
        F.area(t, x, y, x + rng.uniform(8, 24), y + 1, (180, 176, 168))
    rust_runs(t, 6, 0.5)
    return t


def container_floor(seed):
    t = F.mat("Wood089", (120, 86, 56), 1.1, seed=seed, smooth=0.2)
    for x in (0, 16, 32, 48):
        F.area(t, x, 0, x + 1, N, (30, 22, 16), dh=-0.3)
    for x in range(8, N, 16):
        for y in (8, 40):
            F.disc(t, x, y, 1.4, (60, 60, 58), h=1.0)
    t.grime(0.4, (40, 30, 20))
    F.stain(t, t.rng.uniform(0, N), t.rng.uniform(0, N), 12, (30, 24, 18), 0.5)
    return t


def container_welded(seed):
    """A red box with fresh weld beads round a cut panel and a vent cut into it: the clue."""
    t = F.mat("CorrugatedSteel006A", (150, 46, 34), 1.0, seed=seed, smooth=0.3)
    xx = np.arange(N)[None, :]
    t.c *= (0.82 + 0.28 * (np.sin(xx / N * np.pi * 8) > 0))[..., None]
    for (x0, y0, x1, y1) in ((10, 8, 54, 9), (10, 50, 54, 51), (10, 8, 11, 51), (53, 8, 54, 51)):
        F.area(t, x0 - 1, y0 - 1, x1 + 1, y1 + 1, (70, 64, 60), h=1.0)
        F.area(t, x0, y0, x1, y1, (200, 190, 170), h=1.0, f0=200)
    F.area(t, 24, 18, 40, 30, (20, 20, 18), h=0.4)
    for y in range(19, 30, 3):
        F.area(t, 24, y, 40, y + 1, (110, 104, 96), h=0.9)
    t.speckle(0.02, (230, 220, 200), 0.5)
    return F.wear(t, 0.2, 6)


# ---------------------------------------------------------------- the tower shelter and the shelter

def server(seed, top):
    """A dead server rack: black doors, drive caddies, patch panels, cables spilling out."""
    t = F.plain(seed, (30, 32, 34), 0.04, 0.45)
    t.f0[:] = 120
    F.edges(t, 0, 0, N, N, 2)
    rng = t.rng
    for y in range(4 if top else 2, 60, 6):
        kind = rng.random()
        F.area(t, 6, y, 58, y + 5, (44, 46, 50))
        F.area(t, 6, y, 58, y + 1, mul=1.4)
        if kind < 0.5:
            for x in range(8, 56, 4):
                F.area(t, x, y + 1, x + 3, y + 4, (70, 74, 80))
                F.area(t, x + 1, y + 2, x + 2, y + 3, (40, 120, 60) if rng.random() < 0.1 else (20, 20, 20))
        else:
            for x in range(8, 56, 2):
                F.area(t, x, y + 2, x + 1, y + 4, (14, 14, 14))
    if not top:
        for i in range(6):
            x = rng.uniform(8, 56)
            col = [(40, 90, 180), (220, 200, 40), (40, 40, 40)][rng.integers(3)]
            for y in range(int(rng.uniform(30, 50)), 62):
                F.area(t, x, y, x + 1, y + 1, col)
                x += rng.uniform(-0.5, 0.5)
    return t


def tins(seed):
    """A carton of emergency biscuit tins, stencilled, the cardboard soft with damp."""
    t = F.mat("Cardboard001", (170, 138, 96), 1.1, seed=seed, smooth=0.1)
    F.area(t, 0, 0, N, 8, mul=0.85)
    F.area(t, 0, 30, N, 33, (150, 120, 80))
    stencil(t, "EMERGENCY|BISCUITS", (40, 36, 30), 8, 0, 6, N, 26, chip=0.05)
    stencil(t, "24 TINS", (40, 36, 30), 7, 0, 36, N, 14, chip=0.05)
    yy, xx = np.mgrid[0:N, 0:N]
    tri = (yy > 50) & (yy < 62) & (np.abs(xx - 32) < (62 - yy) * 0.6)
    t.c[tri] = (40, 36, 30)
    F.stain(t, t.rng.uniform(10, 54), 58, 14, (110, 86, 54), 0.45)
    return t


# ---------------------------------------------------------------- weathering
# Second and third versions of every wall, floor and ceiling finish, each one worn its own way,
# so no two stretches of wall repeat; the bunker picks between them with noise. Then the decals
# and leavings of the people who sat it out down here.

def runs(t, n, col=(74, 60, 42), amount=0.5, length=(24, 64), width=(1.5, 4.5), top=0, wet=0.5, tide=True):
    """Water and rust running down a wall from the ceiling or a pipe: widest where it starts,
    wandering and thinning as it goes, a darker tide line along its edges; fresh runs shine."""
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    m = np.zeros((N, N), np.float32)
    for _ in range(n):
        x0 = rng.uniform(0, N)
        ln = rng.uniform(*length)
        w0 = rng.uniform(*width)
        wob = P.tnoise(rng, 4)[:, 0] * 2.5
        y = yy - top
        f = np.clip(1 - y / ln, 0, 1)
        cx = x0 + wob[yy.astype(int)]
        d = np.abs((xx - cx + N / 2) % N - N / 2)
        w = w0 * (0.35 + 0.65 * f)
        m = np.maximum(m, np.clip((w - d) / 1.2, 0, 1) * (y >= 0) * f ** 0.6)
    t.mix(m * amount, col, 1.0)
    if tide:
        edge = np.clip(m * (1 - m) * 4, 0, 1)
        t.mix(edge * amount * 0.6, tuple(c * 0.6 for c in col), 1.0)
    if wet:
        t.smooth = np.maximum(t.smooth, m * wet)
    return m


def patch_mask(rng, lo, hi, octaves=((2, 1.0), (4, 0.7), (8, 0.35), (16, 0.15))):
    """A soft patch mask that tiles: noise over lo fading in to full at hi. Round blobs repeat
    block after block down a wall and read as a row of spots; these join up into one surface."""
    m = np.clip((P.fbm(rng, octaves) - lo) / (hi - lo), 0, 1)
    return m * m * (3 - 2 * m)


def patches(t, amount, col, lo=0.05, hi=0.35, tide=0.0, wet=0.0, octaves=((2, 1.0), (4, 0.7), (8, 0.35), (16, 0.15))):
    """Stains, damp or dirt in tileable patches, with an optional darker tide line round them."""
    m = patch_mask(t.rng, lo, hi, octaves)
    t.mix(m * amount, col, 1.0)
    if tide:
        edge = np.clip(m * (1 - m) * 4, 0, 1) ** 2
        t.mix(edge * tide, tuple(c * 0.6 for c in col), 1.0)
    if wet:
        t.smooth = np.maximum(t.smooth, m * wet)
    return m


def rising(t, amount, col=(46, 42, 34), start=36):
    """Dirt and damp creeping up from the floor: heaviest at the foot, in tongues."""
    y = np.clip((np.arange(N) - start) / (N - start), 0, 1)[:, None]
    g = np.clip(y * 1.3 + P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.5 - 0.25, 0, 1) * amount
    t.mix(g, col, 1.0)
    t.smooth -= g * 0.1


def smudges(t, n, col=(52, 46, 38), amount=0.35):
    """Grubby hand marks and shoulder rubs at the height people lean on a wall."""
    band = np.clip(1 - np.abs(np.arange(N) - 30) / 18, 0, 1)[:, None]
    m = patch_mask(t.rng, 0.0, 0.3, ((4, 1.0), (8, 0.8), (16, 0.5)))
    t.mix(m * band * amount * min(1.0, 0.4 + n * 0.1), col, 1.0)


def bloom(t, n, amount=0.7):
    """Efflorescence: white salt crust where water has dried out of the concrete."""
    m = patch_mask(t.rng, 0.36 - 0.04 * n, 0.5, ((4, 1.0), (8, 0.6), (16, 0.3)))
    spots = (t.rng.random((N, N)) < 0.3) * m
    t.mix(spots * amount, (214, 212, 202), 1.0)
    t.h += spots * 0.03


def worn_paint(seed, kind):
    """The cream upper wall gone bad in one of its ways."""
    t = paint(seed, flake=kind == "grime")
    if kind == "b":
        t.grime(0.22, (64, 58, 46))
        runs(t, 2, amount=0.22, wet=0.2)
        smudges(t, 3)
    elif kind == "leak":  # the top row: water through the ceiling
        runs(t, int(t.rng.integers(2, 5)), (96, 72, 44), 0.55, (34, 70), (2, 5.5))
        runs(t, 2, (60, 54, 44), 0.35, (10, 24), (3, 8), wet=0.3)
        m = patch_mask(t.rng, 0.0, 0.3) * np.clip(1.2 - np.arange(N) / 24, 0, 1)[:, None]
        t.mix(m * 0.4, (120, 92, 56), 1.0)
        t.mix(np.clip(m * (1 - m) * 4, 0, 1) ** 2 * 0.35, (72, 52, 30), 1.0)
    elif kind == "grime":
        t.grime(0.45, (54, 48, 40))
        smudges(t, 6, amount=0.45)
        rising(t, 0.35, start=30)
    elif kind == "stain":
        patches(t, 0.28, (128, 98, 60), 0.05, 0.35, tide=0.18, octaves=((4, 1.0), (8, 0.6), (16, 0.3)))
        smudges(t, 2)
    return t


def worn_dado(seed, kind):
    t = dado(seed, damp=kind == "rot")
    rng = t.rng
    if kind == "b":
        rising(t, 0.3)
        for _ in range(8):
            x, y = rng.uniform(0, N), rng.uniform(30, 62)
            F.area(t, x, y, x + rng.uniform(4, 14), y + 1, mul=0.65)
    elif kind == "scuff":  # trolleys, boots and crates dragged past for months
        for _ in range(16):
            x, y = rng.uniform(0, N), rng.uniform(20, 62)
            F.area(t, x, y, x + rng.uniform(6, 20), y + rng.choice((1, 1, 2)), mul=rng.uniform(0.55, 0.8))
        chips(t, 0.16, (132, 130, 122))
        t.crack(2, 0.2)
        rising(t, 0.25)
    elif kind == "rot":
        rising(t, 0.5, (34, 40, 28), start=20)
        runs(t, 2, (70, 60, 40), 0.4, (20, 50))
    return t


def worn_concrete(seed, kind):
    t = concrete(seed)
    if kind == "b":
        bloom(t, 2)
        runs(t, 2, amount=0.3, wet=0.2)
        rising(t, 0.25)
    elif kind == "crack":
        t.crack(3, 0.35, (26, 24, 22))
        runs(t, 2, (62, 56, 46), 0.45, (20, 50))
        bloom(t, 1, 0.5)
    return t


def ceiling(seed, kind):
    """The underside of the slab: water rings, soot over the lamps, rust from the bars."""
    t = concrete(seed)
    rng = t.rng
    if kind == "a":
        patches(t, 0.32, (112, 92, 62), 0.05, 0.35, tide=0.2, octaves=((4, 1.0), (8, 0.6), (16, 0.3)))
    else:
        patches(t, 0.45, (36, 34, 30), 0.0, 0.35)
        t.crack(2, 0.3)
        rust_runs(t, 3, 0.4)
    t.grime(0.25, (50, 46, 40))
    return t


def worn_floor(seed, kind):
    t = floor(seed)
    rng = t.rng
    if kind == "b":
        t.grime(0.3, (66, 60, 50))
        patches(t, 0.15, (150, 146, 134), 0.1, 0.4)  # scrubbed paler where it was mopped
    elif kind == "dirty":  # grit and dried mud tracked in
        t.speckle(0.06, (54, 48, 40), 0.6, -0.02)
        t.speckle(0.03, (150, 144, 132), 0.5, 0.03)
        t.grime(0.35, (70, 60, 46))
        patches(t, 0.3, (64, 52, 40), 0.0, 0.35, octaves=((2, 1.0), (4, 0.8), (16, 0.5), (32, 0.3)))
    elif kind == "crack":
        t.crack(3, 0.35, (24, 22, 20))
        patches(t, 0.3, (70, 66, 58), 0.05, 0.35, wet=0.5)
    elif kind == "stain":  # something leaked here long ago: oil, or worse
        patches(t, 0.4, (40, 30, 24), 0.0, 0.4, wet=0.4, octaves=((4, 1.0), (8, 0.6), (16, 0.3)))
    return t


def worn_tile(seed):
    t = tile(seed)
    rng = t.rng
    for gy in range(0, N, 16):
        for gx in range(0, N, 16):
            if rng.random() < 0.18:  # tile gone, the adhesive combed into the concrete behind
                F.area(t, gx + 1, gy + 1, gx + 16, gy + 16, (112, 106, 94), h=0.45, smooth=0.05)
                for k in range(gy + 3, gy + 16, 3):
                    F.area(t, gx + 2, k, gx + 15, k + 1, mul=0.8)
    t.grime(0.4, (80, 72, 56))
    runs(t, 3, (110, 88, 52), 0.4, (24, 64))
    rising(t, 0.3, (70, 64, 50))
    return t


def worn_plate(seed):
    t = plate(seed)
    rust_runs(t, 12, 0.45)
    t.grime(0.3, (60, 46, 34))
    rising(t, 0.3, (70, 50, 34))
    return t


def worn_ship(seed, rgb):
    t = ship_wall(seed, rgb)
    rust_runs(t, 10, 0.5)
    runs(t, 3, RUST, 0.45, (30, 64), (1.5, 3.5), wet=0.1)
    rising(t, 0.3, (70, 50, 36))
    return t


# decals: cut-outs laid flat on a wall or a floor

def leak_decal(seed):
    """A fresh run of water down the wall from a weeping joint above, glistening."""
    t = clear(seed)
    t.c[:] = (58, 52, 44)
    m = runs(t, int(t.rng.integers(2, 4)), (58, 52, 44), 1.0, (40, 64), (2.5, 5), wet=0.0, tide=False)
    t.alpha = np.clip(m * 200, 0, 200)
    t.smooth[:] = 0.85
    return t


def tally(seed):
    """The days counted in marker on the wall, five-bar gates in rows, a few words above."""
    t = clear(seed)
    t.c[:] = (24, 22, 26)
    rng = t.rng
    m = np.zeros((N, N), np.float32)
    days = int(rng.integers(19, 47))
    x, y = 6, 26
    for d in range(days):
        k = d % 5
        if k < 4:
            x0 = x + k * 3 + rng.uniform(-0.4, 0.4)
            m[int(y):int(y + 9), int(x0):int(x0) + 1] = 1
        else:
            for i in range(14):
                m[int(y + 8 - i * 0.6), int(x - 1 + i)] = 1
            x += 16
            if x > N - 14:
                x, y = 6, y + 12
    words = F.text_mask("NO WORD", F.SCRAWL, 10, 56, 18, angle=-3, jitter=0.4, seed=seed)
    m[4:22, 4:60] = np.maximum(m[4:22, 4:60], words)
    solid(t, m, (24, 22, 26), vary=0.1)
    return t


def scrawl(seed, text, col=(132, 18, 14), size=14):
    """A message painted fast with a brush or a finger, running where it was too wet."""
    t = clear(seed)
    t.c[:] = col
    m = F.text_mask(text, F.SCRAWL, size, N - 4, N - 10, angle=-5, jitter=0.6, seed=seed)
    full = np.zeros((N, N), np.float32)
    full[2:N - 8, 2:N - 2] = m
    solid(t, full, col, vary=0.15)
    rng = t.rng
    ys, xs = np.nonzero(full > 0.5)
    for _ in range(7):  # drips from the bottoms of the strokes
        if len(xs):
            i = rng.integers(len(xs))
            F.drips(t, xs[i], xs[i] + 1, ys[i], 1, (3, 10), col)
            t.alpha[ys[i]:min(N, ys[i] + 10), xs[i]] = np.maximum(t.alpha[ys[i]:min(N, ys[i] + 10), xs[i]],
                                                                (t.c[ys[i]:min(N, ys[i] + 10), xs[i], 0] == col[0]) * 255)
    t.smooth[:] = 0.25
    return t


def bullet_holes(seed):
    """A burst into the wall: holes with the plaster spalled off round them, scorched."""
    t = clear(seed)
    rng = t.rng
    t.c[:] = (40, 38, 34)
    cx, cy = rng.uniform(20, 44), rng.uniform(20, 44)
    for _ in range(int(rng.integers(5, 10))):
        x, y = cx + rng.normal(0, 9), cy + rng.normal(0, 7)
        F.disc(t, x, y, rng.uniform(3.5, 5.5), (150, 146, 136), alpha=255, h=0.6)
        F.disc(t, x, y, 2.4, ring=1.0, mul=0.7)
        F.disc(t, x, y, 1.8, (16, 14, 12), alpha=255, h=0.1)
    return t


def soot_plume(seed):
    """Soot rising up the wall from a fire or a row of candles, blackest where the flames were."""
    t = clear(seed)
    t.c[:] = (22, 20, 18)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    w = 6 + (N - yy) * 0.35
    m = np.clip(1 - np.abs(xx - 32) / w, 0, 1) * np.clip(yy / N + 0.2, 0, 1)
    m *= np.clip(0.6 + P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.6, 0, 1)
    t.alpha = np.clip(m * 230, 0, 230)
    return t


def cobweb(seed):
    """Threads strung out from a corner, grey with dust, a few broken and hanging."""
    t = clear(seed)
    t.c[:] = (176, 172, 164)
    rng = t.rng
    img = Image.new("L", (N * 4, N * 4), 0)
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    ox, oy = rng.uniform(0, N) * 4, 0
    spokes = sorted(rng.uniform(0.15, np.pi - 0.15, 9))
    for a in spokes:
        d.line([(ox, oy), (ox + np.cos(a) * 300, oy + np.sin(a) * 300)], fill=200, width=2)
    for r in np.arange(20, 260, rng.uniform(18, 28)):
        pts = [(ox + np.cos(a) * r * rng.uniform(0.9, 1.1), oy + np.sin(a) * r * rng.uniform(0.9, 1.1)) for a in spokes]
        for p0, p1 in zip(pts, pts[1:]):
            if rng.random() < 0.85:
                d.line([p0, p1], fill=170, width=2)
    m = np.asarray(img.resize((N, N), Image.BOX), np.float32) / 255
    fade = np.clip(1.4 - np.hypot(*np.mgrid[0:N, 0:N] - np.array([[[oy / 4]], [[ox / 4]]])) / 48, 0, 1)
    t.alpha = np.clip(m * 2.2 * fade * 255, 0, 230)
    return t


def puddle(seed):
    """Standing water under a leak: the floor gone dark and mirror smooth, the edge soaking away."""
    t = clear(seed)
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot((xx - 32) / 1.15, yy - 32) / 27 + P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.55
    m = np.clip((1 - d) * 2.5, 0, 1)
    t.c[:] = (40, 42, 42)
    t.alpha = m * 120
    t.smooth[:] = 0.97
    t.f0[:] = 30
    return t


def scorch(seed):
    """Soot fanned across the floor from the blast door where it was cut: black at the seam, brown
    and streaky further in, fading out before the block's edge so it never shows a seam."""
    t = clear(seed)
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    ang = np.arctan2(yy + 6, xx - 32)
    rays = 0.85 + 0.15 * np.sin(ang * 11 + P.fbm(rng, ((4, 1.0), (8, 0.5))) * 8)
    d = np.hypot(xx - 32, (yy + 6) * 0.8) / 58 + P.fbm(rng, ((4, 1.0), (8, 0.7), (16, 0.4))) * 0.3
    m = np.clip((1 - d) * 1.8, 0, 1) * rays
    m *= np.clip(np.minimum(np.minimum(xx, N - 1 - xx), N - 1 - yy) / 6, 0, 1)
    heart = np.clip(m * 1.5 - 0.5, 0, 1)
    t.c[:] = (64, 54, 44)
    t.mix(heart, (20, 18, 16), 1.0)
    t.alpha = np.clip(m * 215, 0, 215)
    t.smooth[:] = 0.1
    return t


def server_side(seed, top=False):
    """The flanks and lid of a server cabinet: powder-coated steel, vent perforations, a dusty top."""
    t = F.plain(seed, (40, 42, 46), 0.05, 0.35)
    t.f0[:] = 110
    rng = t.rng
    if top:
        for y in range(10, 54, 4):
            for x in range(10, 54, 4):
                F.area(t, x, y, x + 2, y + 2, (14, 14, 16), dh=-0.2)
        t.grime(0.5, (92, 88, 80))
    else:
        F.area(t, 2, 0, 3, N, (24, 24, 26), dh=-0.2)
        F.area(t, 61, 0, 62, N, (24, 24, 26), dh=-0.2)
        for y0 in (6, 42):
            for y in range(y0, y0 + 16, 3):
                for x in range(10, 54, 3):
                    F.area(t, x, y, x + 1, y + 1, (12, 12, 14), dh=-0.2)
        F.area(t, 40, 28, 52, 34, (196, 194, 184))  # the asset tag
        F.area(t, 41, 30, 51, 31, (60, 60, 60))
        runs(t, 2, (90, 86, 78), 0.2, (10, 30))
    return F.wear(t, 0.2, 8)


def grit(seed):
    """Grit, crumbs of concrete and dust swept nowhere: the floor nobody has cleaned in months."""
    t = clear(seed)
    rng = t.rng
    dust = np.clip(P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.4))) * 1.4 + 0.1, 0, 1)
    t.c[:] = (118, 112, 100)
    t.alpha = dust * 120
    for _ in range(int(rng.integers(18, 34))):
        x, y = rng.uniform(2, N - 2, 2)
        r = rng.uniform(0.6, 2.2)
        col = (150, 146, 136) if rng.random() < 0.6 else (70, 64, 56)
        F.disc(t, x, y, r, col, alpha=255, h=0.9)
    return t


def bootprints(seed):
    """Muddy prints walking through, along the block, left and right."""
    t = clear(seed)
    rng = t.rng
    t.c[:] = (60, 50, 40)
    for i in range(4):
        x, y = 26 + (i % 2) * 12 + rng.uniform(-1, 1), 4 + i * 16
        sole = F.blob_mask(rng, x, y + 3, 4, rough=0.3, wrap=False)
        heel = F.blob_mask(rng, x, y + 10, 3, rough=0.3, wrap=False)
        m = np.maximum(sole, heel) * (P.fbm(rng, ((16, 1.0), (32, 0.5))) > -0.3)
        t.alpha = np.maximum(t.alpha, np.clip(m * 2, 0, 1) * 200)
    return t


def glowstick(seed, lit=True):
    t = F.plain(seed, (90, 230, 120) if lit else (110, 140, 112), 0.03, 0.8)
    if lit:
        light(t, np.ones((N, N)), 1.0)
    return t


# ---------------------------------------------------------------- the tower's parking levels

LEVEL_PAINT = {"blue": (44, 82, 138), "green": (50, 110, 70), "orange": (202, 114, 34)}
HAZARD = (222, 176, 40)


def park_floor(seed, kind="a"):
    """Power-floated concrete under a tower: smoother and paler than the bunker's, scuffed grey by
    tyres along the aisles, oil dripped where the cars stood, grit swept up against the kerbs."""
    t = F.mat("Concrete034", (132, 130, 124), 0.5, span=2, seed=seed, smooth=0.26)
    rng = t.rng
    t.speckle(0.02, (96, 92, 86), 0.5, -0.01)
    patches(t, 0.16, (100, 96, 90), 0.1, 0.4)
    if kind == "tyre":  # two worn tracks running along x; the block turns them to the aisle
        n = P.fbm(rng, ((4, 1.0), (8, 0.6), (32, 0.3)))
        for y0 in (int(rng.integers(8, 16)), int(rng.integers(40, 48))):
            band = np.clip(1 - np.abs(np.arange(N) - y0 - 4) / 6, 0, 1)[:, None]
            t.mix(band * np.clip(0.45 + n * 0.4, 0, 1), (62, 60, 58), 1.0)
        t.smooth = t.smooth * 0.8
    elif kind == "oil":  # a car stood here for years and wept onto the floor
        yy, xx = np.mgrid[0:N, 0:N]
        cx, cy = 32 + rng.uniform(-6, 6), 32 + rng.uniform(-6, 6)
        d = np.hypot(xx - cx, (yy - cy) * 1.3) / 22 + P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.35
        m = np.clip(1.2 - d, 0, 1) ** 1.5
        t.mix(m * 0.6, (38, 34, 30), 1.0)
        t.mix(np.clip(m * 2 - 1.2, 0, 1) * 0.5, (20, 18, 18), 1.0)
        t.smooth = np.maximum(t.smooth, m * 0.65)
    elif kind == "crack":
        t.crack(3, 0.35, (34, 32, 30))
        patches(t, 0.25, (84, 80, 72), 0.05, 0.35, wet=0.3)
    elif kind == "grit":
        t.speckle(0.08, (70, 64, 56), 0.6, -0.02)
        t.speckle(0.04, (170, 164, 150), 0.5, 0.03)
        patches(t, 0.35, (88, 78, 64), 0.0, 0.35, octaves=((2, 1.0), (4, 0.8), (16, 0.5)))
    return F.wear(t, 0.2, 6)


def park_paint(t, mask, col, amount=0.9):
    """Road paint on concrete: laid thick, worn through by tyres, chipped at its edges."""
    worn = P.fbm(t.rng, ((8, 1.0), (16, 0.7), (32, 0.4))) > 0.55
    m = mask & ~worn
    t.c[m] = t.c[m] * (1 - amount) + np.array(col, np.float32) * amount
    t.smooth[m] = 0.45
    t.h[m] += 0.02
    return m


def park_line(seed, col=WHITE_PAINT):
    t = park_floor(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    park_paint(t, np.abs(xx - 31.5) < 3.5, col)
    return t


def park_arrow(seed):
    """A painted arrow pointing north (up the texture): the way round the level."""
    t = park_floor(seed, "tyre")
    yy, xx = np.mgrid[0:N, 0:N]
    shaft = (np.abs(xx - 31.5) < 4) & (yy > 26) & (yy < 60)
    head = (yy >= 6) & (yy <= 28) & (np.abs(xx - 31.5) < (yy - 6) * 0.75)
    park_paint(t, shaft | head, WHITE_PAINT)
    return t


def park_band(seed, rgb):
    """The lowest row of a parking level's walls: the level's colour up to bumper height and a
    white line over it, scraped by bumpers and blackened by tyres at the foot."""
    t = F.mat("Concrete034", (150, 148, 140), 0.55, span=2, seed=seed, smooth=0.15)
    rng = t.rng
    F.area(t, 0, 22, N, N, rgb, smooth=0.35)
    t.vary(0.05)
    F.area(t, 0, 16, N, 20, WHITE_PAINT, smooth=0.4)
    chips(t, 0.0, (140, 138, 130), cells=((16, 1.0), (32, 0.6)))
    for _ in range(3):  # bumper scrapes along the paint
        x, y = rng.uniform(0, N), rng.uniform(36, 50)
        F.area(t, x, y, x + rng.uniform(5, 14), y + 1, (148, 146, 138), dh=-0.03)
    rising(t, 0.5, (30, 28, 26), start=48)
    return F.wear(t, 0.25, 6)


def park_wall(seed, kind="a"):
    """Precast wall panel over the band: smooth, grey, run with damp from the slab above."""
    t = F.mat("Concrete034", (150, 148, 140), 0.55, span=2, seed=seed, smooth=0.15)
    patches(t, 0.15, (118, 114, 106), 0.1, 0.4)
    if kind == "b":
        runs(t, 3, (86, 78, 64), 0.4, (20, 60), top=0, wet=0.3)
    elif kind == "soot":  # exhaust blackening under the slab
        F.area(t, 0, 0, N, 18, mul=0.75)
        patches(t, 0.4, (40, 38, 36), 0.0, 0.35)
    return F.wear(t, 0.2, 0)


def park_column(seed):
    """A column's foot: yellow and black chevrons to the height of a bumper, gouged by them."""
    t = F.mat("Concrete034", (150, 148, 140), 0.55, span=2, seed=seed, smooth=0.15)
    yy, xx = np.mgrid[0:N, 0:N]
    stripe = ((xx + yy) // 10) % 2 == 0
    band = yy >= 26
    F.area(t, 0, 26, N, N, (24, 24, 24), smooth=0.35)
    park_paint(t, band & stripe, HAZARD, 0.95)
    for _ in range(4):
        x, y = t.rng.uniform(0, N), t.rng.uniform(30, 56)
        F.area(t, x, y, x + t.rng.uniform(4, 12), y + 1.5, (146, 144, 136), dh=-0.05)
    rising(t, 0.4, (30, 28, 26), start=50)
    return F.wear(t, 0.25, 8)


def park_ceiling(seed):
    """The slab's underside over a parking level: smooth soffit with the faint seams of the
    formwork sheets, soot from exhausts, rings where water came through."""
    t = F.mat("Concrete034", (138, 136, 130), 0.45, span=2, seed=seed, smooth=0.1)
    F.area(t, 0, 0, N, 1, mul=0.86, dh=-0.05)
    F.area(t, 0, 0, 1, N, mul=0.9, dh=-0.05)
    t.vary(0.04)
    patches(t, 0.12, (90, 86, 80), 0.1, 0.45, octaves=((2, 1.0), (4, 0.5)))
    t.grime(0.12, (60, 56, 50))
    return t


def wheel_stop(seed):
    """Precast wheel stop: concrete with two yellow bands, chipped by a thousand bumpers."""
    t = F.mat("Concrete034", (156, 154, 146), 0.6, span=1, seed=seed, smooth=0.12)
    yy, xx = np.mgrid[0:N, 0:N]
    park_paint(t, (xx % 32) < 12, HAZARD, 0.9)
    chips(t, 0.12, (150, 148, 140))
    return F.wear(t, 0.35, 8)


def textures():
    T = {
        "bunk_concrete": lambda: concrete(6000), "bunk_concrete_damp": lambda: concrete(6001, True),
        "bunk_dado": lambda: dado(6002), "bunk_dado_damp": lambda: dado(6003, True),
        "bunk_paint": lambda: paint(6004), "bunk_paint_flake": lambda: paint(6005, True),
        "bunk_plate": lambda: plate(6006), "bunk_plate_rust": lambda: plate(6007, rust=True),
        "bunk_floor": lambda: floor(6008), "bunk_floor_line": lambda: floor(6009, line=True),
        "bunk_floor_drain": lambda: floor(6010, drain=True), "bunk_tile": lambda: tile(6011),
        "bunk_walkway": lambda: walkway(6012), "bunk_mesh": lambda: cage_mesh(6013),
        "bunk_shelter_a": lambda: shelter_sign(6014, "a"), "bunk_shelter_b": lambda: shelter_sign(6015, "b"),
        "bunk_keyswitch": lambda: keyswitch(6016),
        "bunk_cage_wire": lambda: cage_wire(6020),
        "bunk_glass_on": lambda: glass(6021, (236, 222, 180), True), "bunk_glass_off": lambda: glass(6022, (200, 196, 180), False),
        "bunk_red_on": lambda: glass(6023, (236, 50, 36), True), "bunk_red_off": lambda: glass(6024, (170, 40, 30), False),
        "bunk_tube_on": lambda: tube(6025, True), "bunk_tube_off": lambda: tube(6026, False),
        "bunk_fitting": lambda: F.enamel(6027, (196, 194, 184)),
        "bunk_pipe_red": lambda: pipe(6030, (150, 42, 32)), "bunk_pipe_blue": lambda: pipe(6031, (52, 80, 118)),
        "bunk_pipe_green": lambda: pipe(6032, (70, 100, 72)), "bunk_pipe_grey": lambda: pipe(6033, (118, 122, 118)),
        "bunk_cables": lambda: cables(6034), "bunk_grille": lambda: grille(6035),
        "bunk_fusebox": lambda: fusebox(6036), "bunk_grey": lambda: painted(6037, (118, 124, 120), "PaintedMetal001", 1.0, 0.35),
        "bunk_phone": lambda: field_phone(6038), "bunk_olive": lambda: painted(6039, OLIVE, "PaintedMetal001", 1.0, 0.3),
        "bunk_ext_front": lambda: extinguisher(6040, True), "bunk_ext": lambda: extinguisher(6041, False),
        "bunk_radmeter": lambda: rad_meter(6042), "bunk_clock": lambda: clock(6043),
        "bunk_firstaid": lambda: first_aid(6044),
        "bunk_door_steel_top": lambda: steel_door(6050, True), "bunk_door_steel_bottom": lambda: steel_door(6051, False),
        "bunk_door_elec_top": lambda: steel_door(6052, True, (120, 124, 116), "ELECTRICAL|B4"),
        "bunk_door_elec_bottom": lambda: steel_door(6053, False, (120, 124, 116)),
        "bunk_door_cage_top": lambda: cage_door(6054, True), "bunk_door_cage_bottom": lambda: cage_door(6055, False),
        "ship_door_top": lambda: watertight_door(6056, True), "ship_door_bottom": lambda: watertight_door(6057, False),
        "bunk_wheel": lambda: painted(6058, (150, 40, 30), "PaintedMetal001", 1.0, 0.4),
        "bunk_hatch": lambda: hatch(6060), "bunk_collar_top": lambda: collar_top(6061),
        "bunk_hatch_wood": lambda: wood_hatch(6065),
        "bunk_rust": lambda: rusty(6062), "bunk_rust_pipe": lambda: rusty(6063, (120, 96, 76)),
        "bunk_cable_post": lambda: cable_post(6064),
        "bunk_blanket": lambda: blanket(6070), "bunk_mattress": lambda: ticking(6071),
        "bunk_footlocker": lambda: footlocker(6072),
        "bunk_locker": lambda: locker(6073, False), "bunk_locker_top": lambda: locker(6074, True),
        "bunk_formica": lambda: F.wear(F.plain(6075, (132, 136, 128), 0.04, 0.5), 0.3, 14),
        "bunk_range_front": lambda: range_front(6076), "bunk_range_top": lambda: range_top(6077),
        "bunk_counter_front": lambda: counter_front(6078),
        "bunk_hazmat": lambda: hazmat(6079, False), "bunk_hazmat_top": lambda: hazmat(6080, True),
        "bunk_desk_front": lambda: desk_front(6081), "bunk_desk_top": lambda: desk_top(6082),
        "bunk_radio_front": lambda: radio_front(6083), "bunk_map_top": lambda: county_map(6084),
        "bunk_map_wall": lambda: county_map(6085, True), "bunk_medcab": lambda: medcab(6086),
        "bunk_ivbag": lambda: iv_bag(6087), "bunk_white_steel": lambda: white_steel(6088),
        "bunk_mattress_med": lambda: F.wear(F.plain(6089, (150, 176, 160), 0.05, 0.6), 0.3, 6),
        "bunk_rack_blue": lambda: rack_paint(6090, (40, 70, 130), True),
        "bunk_rack_orange": lambda: rack_paint(6091, (200, 100, 30), False),
        "bunk_pallet": lambda: pallet(6092),
        "bunk_load_boxes": lambda: load(6093, "boxes"), "bunk_load_drums": lambda: load(6094, "drums"),
        "bunk_load_crates": lambda: load(6095, "crates"),
        "bunk_crate_ration": lambda: crate_stencil(6096, "RATIONS|24 MEALS"),
        "bunk_crate_wood": lambda: crate_stencil(6097, ""),
        "bunk_crate_ammo": lambda: crate_stencil(6098, "CARTRIDGES|1200", OLIVE, (226, 196, 90), 8),
        "bunk_crate_gun": lambda: crate_stencil(6099, "RIFLES|HANDLE WITH CARE", OLIVE, (226, 196, 90), 7),
        "bunk_drum_water": lambda: drum(6100, (60, 84, 120), "DRINKING|WATER"),
        "bunk_drum_top": lambda: drum_top(6101, (60, 84, 120)),
        "bunk_jerry_red": lambda: jerry(6102, (150, 36, 28)), "bunk_jerry_olive": lambda: jerry(6103, OLIVE),
        "bunk_gunrack_board": lambda: F.wood(6104, "dark"), "bunk_rifles": lambda: rifles(6105),
        "bunk_pegboard": lambda: pegboard(6106),
        "bunk_skid": lambda: skid(6110), "bunk_gen_a": lambda: gen_side(6111, "a"),
        "bunk_gen_b": lambda: gen_side(6112, "b"), "bunk_gen_green": lambda: painted(6113, (54, 86, 64), "PaintedMetal001", 1.0, 0.4),
        "bunk_filter_front": lambda: filter_front(6114), "bunk_tank": lambda: tank(6115),
        "bunk_tank_top": lambda: drum_top(6116, (122, 54, 36)), "bunk_fan": lambda: fan(6117),
        "bunk_switch": lambda: switchboard(6118, False), "bunk_switch_top": lambda: switchboard(6119, True),
        "bunk_bodybag": lambda: body_bag(6120), "bunk_casings": lambda: casings(6121), "bunk_wire": lambda: wire(6122),
        "ship_wall_white": lambda: ship_wall(6130, (206, 204, 194)), "ship_wall_grey": lambda: ship_wall(6131, (96, 104, 104)),
        "ship_hold": lambda: hold_wall(6132), "ship_porthole": lambda: porthole(6133),
        "ship_console": lambda: console(6134, False), "ship_console_top": lambda: console(6135, True),
        "ship_telegraph": lambda: telegraph(6136), "ship_lifebuoy": lambda: lifebuoy(6137),
        "ship_engine_lo": lambda: engine(6138, "lo"), "ship_engine_hi": lambda: engine(6139, "hi"),
        "ship_engine_top": lambda: engine(6140, "top"), "ship_engine_green": lambda: painted(6141, (98, 114, 104), "PaintedMetal001", 1.0, 0.45),
        "container_inside": lambda: container_inside(6142), "container_floor": lambda: container_floor(6143),
        "container_welded": lambda: container_welded(6144),
        "bunk_server": lambda: server(6150, False), "bunk_server_top": lambda: server(6151, True),
        "bunk_tins": lambda: tins(6152),
    }
    for col, row in ((c, r) for c in range(2) for r in range(3)):
        T[f"bunk_blast_{'we'[col]}{row}"] = (lambda c, r: lambda: blast(6160, 1 - c, 2 - r))(col, row)
    signs = {"decon": "DECON", "bunks": "BUNKS", "mess": "MESS", "medical": "MEDICAL", "comms": "COMMS",
             "generator": "GENERATOR", "stores": "STORES", "water": "WATER", "b4": "B4", "level": "LEVEL|B5"}
    for i, (k, text) in enumerate(signs.items()):
        size = 30 if len(text) <= 2 else 20 if len(text) <= 5 else 18
        T[f"bunk_st_{k}"] = (lambda tx, s, n: lambda: stencil_sign(6200 + n, tx, AMBER, s))(text, size, i)
    T["bunk_st_armoury"] = lambda: stencil_sign(6220, "ARMOURY", STENCIL_RED, 18)
    T["bunk_st_nosmoking"] = lambda: stencil_sign(6221, "NO|SMOKING", STENCIL_RED, 15)
    T["bunk_st_exit_l"] = lambda: stencil_sign(6222, "EXIT", WHITE_PAINT, 20, arrow=-1)
    T["bunk_st_exit_r"] = lambda: stencil_sign(6223, "EXIT", WHITE_PAINT, 20, arrow=1)
    T["bunk_st_shelter_r"] = lambda: stencil_sign(6224, "SHELTER", AMBER, 18, arrow=1)
    T["bunk_st_shelter_l"] = lambda: stencil_sign(6225, "SHELTER", AMBER, 18, arrow=-1)
    for i in range(1, 4):
        T[f"bunk_st_b{i}"] = (lambda k: lambda: stencil_sign(6225 + k, f"B{k}", AMBER, 30))(i)
    T["bunk_st_clearance"] = lambda: stencil_sign(6229, "CLEARANCE|6'8\"", WHITE_PAINT, 14)
    T["bunk_st_restricted"] = lambda: stencil_sign(6230, "NO|ENTRY", STENCIL_RED, 18)
    T["bunk_keypad"] = lambda: keypad(6231)
    T["bunk_sign_warning"] = lambda: warning_plate(6232)
    worn = {
        "bunk_paint_b": lambda: worn_paint(6300, "b"), "bunk_paint_leak": lambda: worn_paint(6301, "leak"),
        "bunk_paint_grime": lambda: worn_paint(6302, "grime"), "bunk_paint_stain": lambda: worn_paint(6303, "stain"),
        "bunk_dado_b": lambda: worn_dado(6304, "b"), "bunk_dado_scuff": lambda: worn_dado(6305, "scuff"),
        "bunk_dado_rot": lambda: worn_dado(6306, "rot"),
        "bunk_concrete_b": lambda: worn_concrete(6307, "b"), "bunk_concrete_crack": lambda: worn_concrete(6308, "crack"),
        "bunk_ceiling": lambda: ceiling(6309, "a"), "bunk_ceiling_b": lambda: ceiling(6310, "b"),
        "bunk_floor_b": lambda: worn_floor(6311, "b"), "bunk_floor_dirty": lambda: worn_floor(6312, "dirty"),
        "bunk_floor_crack": lambda: worn_floor(6313, "crack"), "bunk_floor_stain": lambda: worn_floor(6314, "stain"),
        "bunk_tile_dirty": lambda: worn_tile(6315), "bunk_plate_b": lambda: worn_plate(6316),
        "ship_wall_grey_b": lambda: worn_ship(6317, (96, 104, 104)),
        "ship_wall_white_b": lambda: worn_ship(6318, (206, 204, 194)),
        "bunk_leak": lambda: leak_decal(6320), "bunk_tally": lambda: tally(6321),
        "bunk_scrawl_a": lambda: scrawl(6322, "DO NOT|OPEN"),
        "bunk_scrawl_b": lambda: scrawl(6323, "SICK BAY|KEEP SHUT", size=11),
        "bunk_scrawl_c": lambda: scrawl(6324, "WATER|BAD", (30, 28, 30)),
        "bunk_bullets": lambda: bullet_holes(6325), "bunk_soot": lambda: soot_plume(6326),
        "bunk_cobweb": lambda: cobweb(6327), "bunk_puddle": lambda: puddle(6328), "bunk_grit": lambda: grit(6329),
        "bunk_bootprints": lambda: bootprints(6330), "bunk_glowstick": lambda: glowstick(6331),
        "bunk_glowstick_dead": lambda: glowstick(6332, False), "bunk_locker_open": lambda: locker_open(6333),
        "bunk_scorch": lambda: scorch(6334), "bunk_server_side": lambda: server_side(6335),
        "bunk_server_lid": lambda: server_side(6336, True),
    }
    T.update(worn)
    park = {
        "park_floor": lambda: park_floor(6400), "park_floor_b": lambda: park_floor(6401),
        "park_floor_tyre": lambda: park_floor(6402, "tyre"), "park_floor_oil": lambda: park_floor(6403, "oil"),
        "park_floor_crack": lambda: park_floor(6404, "crack"), "park_floor_grit": lambda: park_floor(6405, "grit"),
        "park_line": lambda: park_line(6406), "park_line_yellow": lambda: park_line(6407, HAZARD),
        "park_arrow": lambda: park_arrow(6408), "park_wall": lambda: park_wall(6409),
        "park_wall_b": lambda: park_wall(6410, "b"), "park_wall_soot": lambda: park_wall(6411, "soot"),
        "park_column": lambda: park_column(6412), "park_wheelstop": lambda: wheel_stop(6413),
        "park_ceiling": lambda: park_ceiling(6414),
        "park_st_freight": lambda: stencil_sign(6420, "FREIGHT|LIFT", WHITE_PAINT, 16),
        "park_st_noparking": lambda: stencil_sign(6421, "NO|PARKING", HAZARD, 15),
        "park_st_stairs": lambda: stencil_sign(6422, "STAIRS", WHITE_PAINT, 18, arrow=1),
        "park_st_lifts": lambda: stencil_sign(6423, "LIFTS", WHITE_PAINT, 18, arrow=-1),
        "park_st_authorised": lambda: stencil_sign(6424, "AUTHORISED|ONLY", STENCIL_RED, 13),
        "park_st_exit": lambda: stencil_sign(6425, "WAY|OUT", WHITE_PAINT, 18),
    }
    for i, (k, rgb) in enumerate(LEVEL_PAINT.items()):
        park[f"park_band_{k}"] = (lambda s, c: lambda: park_band(s, c))(6430 + i, rgb)
    T.update(park)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def mirror(boxes):
    """The same pieces with x flipped, for the other half of a symmetrical thing."""
    return [[16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5], b[6]] for b in boxes]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, light=0, loot=None, climb=False):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        if loot:
            spec["loot"] = loot
        if climb:
            spec["climb"] = True
        out[name] = spec

    def cube(name, tex, title, sound="STONE", rotate=None):
        out[name] = {"tex": tex, "title": title, "sound": sound}
        if rotate:
            out[name]["rotate"] = rotate

    # walls and floors
    cube("bunk_concrete", "bunk_concrete", "Poured Concrete")
    cube("bunk_concrete_damp", "bunk_concrete_damp", "Damp Concrete")
    cube("bunk_dado", {"side": "bunk_dado", "up": "bunk_concrete", "down": "bunk_concrete"}, "Painted Wall")
    cube("bunk_dado_damp", {"side": "bunk_dado_damp", "up": "bunk_concrete", "down": "bunk_concrete"}, "Painted Wall")
    cube("bunk_paint", {"side": "bunk_paint", "up": "bunk_concrete", "down": "bunk_concrete"}, "Painted Wall")
    cube("bunk_paint_flake", {"side": "bunk_paint_flake", "up": "bunk_concrete", "down": "bunk_concrete"}, "Peeling Wall")
    cube("bunk_plate", "bunk_plate", "Steel Plate", "METAL")
    cube("bunk_plate_rust", "bunk_plate_rust", "Rusted Steel Plate", "METAL")
    cube("bunk_floor", {"up": "bunk_floor", "side": "bunk_concrete", "down": "bunk_concrete"}, "Shelter Floor")
    cube("bunk_floor_line", {"up": "bunk_floor_line", "side": "bunk_concrete", "down": "bunk_concrete"}, "Shelter Floor",
         rotate="axis")
    cube("bunk_floor_drain", {"up": "bunk_floor_drain", "side": "bunk_concrete", "down": "bunk_concrete"}, "Floor Drain")
    cube("bunk_tile", "bunk_tile", "Tiled Wall")
    cube("bunk_collar", {"up": "bunk_collar_top", "side": "bunk_concrete", "down": "bunk_concrete"}, "Concrete Collar")
    out["bunk_walkway"] = {"model": "boxes", "boxes": [[0, 14, 0, 16, 16, 16, "bunk_walkway"]], "title": "Grating",
                           "sound": "METAL"}
    add("bunk_cage", [[7, 0, 0, 9, 16, 16, "bunk_mesh"]], "Mesh Cage", "METAL", rotate="axis")

    # signs, stencilled straight onto the wall, and the shelter plates
    for k in ("decon", "bunks", "mess", "medical", "comms", "generator", "stores", "water", "b4", "level", "armoury",
              "nosmoking", "exit_l", "exit_r", "shelter_l", "shelter_r", "b1", "b2", "b3", "clearance"):
        add(f"bunk_st_{k}", [[0, 0, 15.75, 16, 16, 16, f"bunk_st_{k}"]], "Stencil", "STONE", decor=True)
    for k in ("a", "b"):
        add(f"bunk_shelter_{k}", [[0, 1, 15.5, 16, 15, 16, f"bunk_shelter_{k}"]], "Fallout Shelter Sign", "METAL",
            decor=True)
    add("bunk_keyswitch", [[5, 4, 15.5, 11, 12, 16, "bunk_keyswitch"]], "Key Switch", "METAL", decor=True)
    add("bunk_st_restricted", [[0, 0, 15.75, 16, 16, 16, "bunk_st_restricted"]], "Stencil", "STONE", decor=True)
    # the coded door's pad (world/Keypads): the interact key opens its number pad
    add("bunk_keypad", [[4, 2, 14.5, 12, 14, 16, {"north": "bunk_keypad", "side": "bunk_grey", "up": "bunk_grey",
                                                   "down": "bunk_grey"}]], "Keypad", "METAL")
    add("bunk_sign_warning", [[1, 1, 15.5, 15, 15, 16, "bunk_sign_warning"]], "Warning Sign", "METAL", decor=True)

    # lights: dead until the generator runs
    for lit in (False, True):
        s = "_lit" if lit else ""
        g = "bunk_glass_on" if lit else "bunk_glass_off"
        r = "bunk_red_on" if lit else "bunk_red_off"
        add(f"bunk_lamp{s}", [[5, 4, 15, 11, 12, 16, "bunk_grey"], [6, 5, 12, 10, 11, 15, g],
                              [5.5, 4.5, 11.5, 10.5, 11.5, 15, "bunk_cage_wire"]], "Bulkhead Lamp", "METAL",
            light=12 if lit else 0)
        add(f"bunk_lamp_hang{s}", [[7.5, 12, 7.5, 8.5, 16, 8.5, "bunk_grey"], [5.5, 11, 5.5, 10.5, 12, 10.5, "bunk_grey"],
                                   [6, 7, 6, 10, 11, 10, g], [5.5, 6.5, 5.5, 10.5, 11, 10.5, "bunk_cage_wire"]],
            "Caged Lamp", "METAL", rotate=None, light=13 if lit else 0)
        add(f"bunk_lamp_red{s}", [[5, 9, 15, 11, 15, 16, "bunk_grey"], [6, 10, 12.5, 10, 14, 15, r],
                                  [5.5, 9.5, 12, 10.5, 14.5, 15, "bunk_cage_wire"]], "Emergency Lamp", "METAL",
            light=6 if lit else 0)
        add(f"bunk_tube{s}", [[4, 14, 0, 12, 16, 16, "bunk_fitting"],
                              [4.5, 13.5, 0.5, 11.5, 14, 15.5, "bunk_tube_on" if lit else "bunk_tube_off"]],
            "Strip Light", "METAL", rotate="axis", decor=True, light=14 if lit else 0)

    # pipes, ducts, cables and what hangs on the walls
    pipes = [[0, 11, 12, 16, 14, 15, "bunk_pipe_grey"], [0, 7.5, 13, 16, 9.5, 15, "bunk_pipe_red"],
             [3, 7, 15, 4, 14.5, 16, "bunk_grey"], [12, 7, 15, 13, 14.5, 16, "bunk_grey"]]
    add("bunk_pipes", pipes, "Pipes", "METAL", decor=True)
    add("bunk_pipes_low", [[0, 1, 12.5, 16, 3.5, 15, "bunk_pipe_blue"], [0, 4.5, 13.5, 16, 6, 15, "bunk_pipe_green"],
                           [7, 0, 15, 8, 6.5, 16, "bunk_grey"]], "Pipes", "METAL", decor=True)
    add("bunk_pipes_valve", pipes + [[7, 11.5, 10, 9, 13.5, 12, "bunk_pipe_grey"],
                                     [4, 9.5, 9.5, 12, 15.5, 10, "bunk_wheel"]], "Valve", "METAL", decor=True)
    add("bunk_cable_tray", [[2, 14, 0, 14, 14.5, 16, "ext_galv"], [2, 14.5, 0, 2.5, 16, 16, "ext_galv"],
                            [13.5, 14.5, 0, 14, 16, 16, "ext_galv"], [3, 14.5, 0, 13, 15.5, 16, "bunk_cables"]],
        "Cable Tray", "METAL", rotate="axis", decor=True)
    add("bunk_duct", [[3, 10, 0, 13, 16, 16, "ext_galv"]], "Duct", "METAL", rotate="axis", decor=True)
    add("bunk_duct_vent", [[3, 10, 0, 13, 16, 16, "ext_galv"], [4, 9.9, 4, 12, 10, 12, "bunk_grille"]], "Duct Vent",
        "METAL", rotate="axis", decor=True)
    add("bunk_vent", [[3, 5, 15.5, 13, 13, 16, "bunk_grille"]], "Vent", "METAL", decor=True)
    add("bunk_fusebox", [[3, 4, 13.5, 13, 14, 16, {"north": "bunk_fusebox", "side": "bunk_grey", "up": "bunk_grey",
                                                   "down": "bunk_grey"}]], "Fuse Box", "METAL", decor=True)
    add("bunk_phone", [[5, 7, 13, 11, 12, 16, {"north": "bunk_phone", "side": "bunk_olive", "up": "bunk_olive",
                                              "down": "bunk_olive"}]], "Field Telephone", "METAL", decor=True)
    add("bunk_extinguisher", [[6, 1, 12, 10, 10, 15, {"north": "bunk_ext_front", "side": "bunk_ext", "up": "bunk_ext",
                                                      "down": "bunk_ext"}],
                              [7, 10, 12.5, 9, 12, 14.5, "ext_black_metal"], [5, 6, 15, 11, 8, 16, "bunk_grey"]],
        "Fire Extinguisher", "METAL", decor=True)
    add("bunk_radmeter", [[4, 8, 13.5, 12, 13, 16, {"north": "bunk_radmeter", "side": "bunk_olive", "up": "bunk_olive",
                                                    "down": "bunk_olive"}]], "Radiation Meter", "METAL", decor=True)
    add("bunk_clock", [[4, 8, 15.4, 12, 16, 16, "bunk_clock"]], "Clock", "METAL", decor=True)
    add("bunk_first_aid", [[3, 6, 13.5, 13, 14, 16, {"north": "bunk_firstaid", "side": "bunk_white_steel",
                                                     "up": "bunk_white_steel", "down": "bunk_white_steel"}]],
        "First Aid Box", "METAL", loot="basement_medicine")

    # doors and ways in
    for name, top, bottom, kind, title in (("bunk_door_steel", "bunk_door_steel_top", "bunk_door_steel_bottom", "steel", "Steel Door"),
                                           ("bunk_door_elec", "bunk_door_elec_top", "bunk_door_elec_bottom", "steel", "Electrical Room Door"),
                                           ("bunk_door_cage", "bunk_door_cage_top", "bunk_door_cage_bottom", "steel", "Cage Door"),
                                           ("ship_door", "ship_door_top", "ship_door_bottom", "steel", "Watertight Door")):
        out[name] = {"door": kind, "tex": {"top": top, "bottom": bottom}, "title": title}
    # the blast door, two wide and three tall; w and e name each half's place when the door faces
    # north (the w half shows the right of the drawing, as the wall's outside looks at it)
    ring = [[10, 5.5, 8, 11.5, 10.5, 9.5], [11, 10.5, 8, 12.5, 12.5, 9.5], [12, 12.5, 8, 16, 14, 9.5],
            [11, 3.5, 8, 12.5, 5.5, 9.5], [12, 2, 8, 16, 3.5, 9.5], [11.5, 7.5, 8.5, 16, 8.5, 9.5],
            [14, 6, 7.5, 16, 10, 10]]
    for half in "we":
        for row in range(3):
            tex = f"bunk_blast_{half}{row}"
            boxes = [[0, 0, 10, 16, 16, 16, tex]]
            frame = [0, 0, 8, 2, 16, 16] if half == "w" else [14, 0, 8, 16, 16, 16]
            boxes.append(frame + ["bunk_plate"])
            if row == 2:
                boxes.append([0, 14, 8, 16, 16, 16, "bunk_plate"])
            if row == 1:
                parts = [b + ["bunk_wheel"] for b in ring]
                boxes += parts if half == "w" else mirror(parts)
            add(f"bunk_blast_{half}{row}", boxes, "Blast Door", "METAL")
    # climbable, so the ladder up a shaft carries on through the open hatch and you step out on top
    out["bunk_hatch"] = {"trapdoor": "steel", "tex": "bunk_hatch", "title": "Hatch", "climb": True}
    out["bunk_hatch_wood"] = {"trapdoor": "wood", "sound": "WOOD", "tex": "bunk_hatch_wood", "title": "Cellar Hatch", "climb": True}
    add("bunk_ladder", [[1, 0, 14, 2.5, 16, 16, "bunk_grey"], [13.5, 0, 14, 15, 16, 16, "bunk_grey"]]
        + [[2.5, y, 14.5, 13.5, y + 1, 15.5, "bunk_rust_pipe"] for y in (1, 5, 9, 13)], "Ladder", "METAL", climb=True)
    add("bunk_vent_stack", [[6, 0, 6, 10, 16, 10, "bunk_rust_pipe"]], "Vent Pipe", "METAL", rotate=None)
    add("bunk_vent_cap", [[6, 0, 6, 10, 8, 10, "bunk_rust_pipe"], [3, 8, 3, 13, 10, 13, "bunk_rust_pipe"],
                          [5, 10, 5, 11, 11, 11, "bunk_rust_pipe"]], "Vent Pipe", "METAL", rotate=None)
    add("bunk_cable_post", [[6, 0, 6, 10, 12, 10, {"north": "bunk_cable_post", "side": "bunk_cable_post",
                                                   "up": "bunk_concrete", "down": "bunk_concrete"}]],
        "Cable Marker", "STONE")

    # the forest hatch's clearing: the trees the survivalist felled, cut up and stacked
    bark = {"up": "bark_top", "down": "bark_top", "side": "bark"}
    ends = {"north": "bark_top", "south": "bark_top", "side": "bark"}
    add("bunk_stump", [[2.5, 0, 2.5, 13.5, 9, 13.5, bark], [1, 0, 5, 2.5, 3.5, 10, "bark"], [13.5, 0, 6, 15.5, 2.5, 11, "bark"],
                       [5.5, 0, 0.5, 10.5, 3, 2.5, "bark"], [6, 0, 13.5, 11, 2, 15.5, "bark"],
                       [4, 9, 4, 12, 9.5, 12, "bark_top"]], "Stump", "WOOD")
    add("bunk_log", [[2, 0, 0, 14, 11, 16, ends], [5, 11, 0, 11, 12, 16, "bark"], [0.5, 0, 0, 2, 7, 16, "bark"],
                     [14, 0, 0, 15.5, 7, 16, "bark"]], "Fallen Log", "WOOD", rotate="axis")
    pile = [[0.5, 0, 0.5, 5.5, 5, 15.5], [5.5, 0, 1.5, 10.5, 5, 14.5], [10.5, 0, 0.5, 15.5, 5, 15],
            [3, 5, 1, 8, 10, 15], [8, 5, 0.5, 13, 10, 15.5], [5.5, 10, 1.5, 10.5, 15, 14.5]]
    add("bunk_logpile", [b + [ends] for b in pile], "Log Pile", "WOOD")
    add("bunk_pole", [[6.5, 0, 6.5, 9.5, 16, 9.5, bark]], "Pole", "WOOD", rotate=None)

    # the bunk room: steel bunks two high, a footlocker at each foot, wall lockers
    def bunk(head, top):
        posts_z = (14.5, 16) if head else (0, 1.5)
        h = 14 if top else 16
        b = [[1, 0, posts_z[0], 2.5, h, posts_z[1], "bunk_olive"], [13.5, 0, posts_z[0], 15, h, posts_z[1], "bunk_olive"],
             [1, 3, 0, 2.5, 4.5, 16, "bunk_olive"], [13.5, 3, 0, 15, 4.5, 16, "bunk_olive"],
             [2.5, 4, 0, 13.5, 7, 16, {"up": "bunk_mattress", "side": "bunk_mattress", "down": "bunk_olive"}]]
        if head:
            b += [[2.5, 9, 15, 13.5, 10.5, 16, "bunk_olive"], [3.5, 7, 11, 12.5, 9, 15, "home_pillow"]]
        else:
            b += [[2.5, 7, 1, 13.5, 9, 6, "bunk_blanket"], [2.5, 9, 0, 13.5, 10.5, 1, "bunk_olive"]]
        b += [[2.5, 7, 6 if not head else 0, 13.5, 7.6, 11 if head else 16, "bunk_blanket"]]
        if top:
            b.append([13.5, 9, 0, 15, 10.5, 16, "bunk_olive"])  # the guard rail
        return b

    for head in (True, False):
        for top in (False, True):
            add(f"bunk_bed_{'head' if head else 'foot'}{'_top' if top else ''}", bunk(head, top), "Bunk", "METAL")
    add("bunk_footlocker", [[2, 0, 3, 14, 7, 13, {"north": "bunk_footlocker", "side": "bunk_olive", "up": "bunk_olive",
                                                  "down": "bunk_olive"}]], "Footlocker", "METAL", loot="basement_supplies")
    add("bunk_locker", [[1, 0, 3, 15, 16, 16, {"north": "bunk_locker", "side": "bunk_grey", "up": "bunk_grey",
                                               "down": "bunk_grey"}]], "Wall Locker", "METAL", loot="home_wardrobe")
    add("bunk_locker_top", [[1, 0, 3, 15, 14, 16, {"north": "bunk_locker_top", "side": "bunk_grey", "up": "bunk_grey",
                                                   "down": "bunk_grey"}]], "Wall Locker", "METAL", loot="home_wardrobe")

    # the mess and the kitchen
    add("bunk_mess_table", [[3, 11, 0, 13, 12, 16, {"up": "bunk_formica", "side": "bunk_grey", "down": "bunk_grey"}],
                            [7, 0, 6, 9, 11, 10, "bunk_grey"], [5, 0, 5, 11, 0.5, 11, "bunk_grey"],
                            [0, 4, 7, 16, 5, 9, "bunk_grey"], [0, 5, 2, 2.5, 6, 14, "bunk_formica"],
                            [13.5, 5, 2, 16, 6, 14, "bunk_formica"]], "Mess Table", "METAL", rotate="axis")
    add("bunk_range", [[1, 0, 2, 15, 12, 16, {"north": "bunk_range_front", "up": "bunk_range_top", "side": "home_steel",
                                              "down": "home_steel"}],
                       [3, 12, 4, 9, 16, 10, "home_steel"], [10, 12, 9, 14, 14, 13, "home_steel"],
                       [1, 12, 15, 15, 16, 16, "home_steel"]], "Field Range", "METAL", loot="kitchen")
    add("bunk_sink", [[0, 9, 2, 16, 13, 16, "home_steel"], [1, 11, 3, 15, 13.5, 15, "home_basin"],
                      [1, 0, 3, 2, 9, 4, "home_steel"], [14, 0, 3, 15, 9, 4, "home_steel"],
                      [7, 13, 14, 9, 16, 15, "home_chrome"], [7, 15, 10, 9, 16, 14, "home_chrome"]],
        "Steel Sink", "METAL", loot="home_sink")
    add("bunk_counter", [[0, 0, 2, 16, 13, 16, {"north": "bunk_counter_front", "up": "home_steel", "side": "home_steel",
                                                "down": "home_steel"}]], "Prep Counter", "METAL", loot="basement_food")

    # decontamination
    add("bunk_shower", [[7, 0, 14.5, 9, 15, 16, "home_chrome"], [7, 13, 10, 9, 15, 14.5, "home_chrome"],
                        [5.5, 12, 9, 10.5, 13, 13, "home_chrome"], [5, 6, 13.5, 11, 9, 15, "bunk_wheel"]],
        "Decon Shower", "METAL", decor=True)
    add("bunk_hazmat", [[1, 0, 14, 15, 16, 15, "bunk_hazmat"]], "Hazmat Suit", "WOOL", decor=True)
    add("bunk_hazmat_top", [[1, 0, 14, 15, 16, 15, "bunk_hazmat_top"], [7.5, 15, 14, 8.5, 16, 16, "bunk_grey"]],
        "Hazmat Suit", "WOOL", decor=True)

    # comms, the map room, the med bay
    desk = [[0, 11, 3, 16, 12, 16, {"up": "bunk_desk_top", "side": "bunk_olive", "down": "bunk_olive"}],
            [0, 0, 3, 16, 11, 16, {"north": "bunk_desk_front", "side": "bunk_olive", "up": "bunk_olive", "down": "bunk_olive"}]]
    add("bunk_desk", desk, "Steel Desk", "METAL", loot="office")
    add("bunk_radio_desk", desk + [[2, 12, 8, 12, 16, 15, {"north": "bunk_radio_front", "side": "bunk_olive",
                                                         "up": "bunk_olive", "down": "bunk_olive"}],
                                   [12.5, 12, 5, 14.5, 13, 8, "ext_black_metal"], [13, 13, 6, 14, 15, 7, "ext_black_metal"]],
        "Radio Desk", "METAL", loot="office")
    add("bunk_map_table", [[0, 11, 0, 16, 12, 16, {"up": "bunk_map_top", "side": "home_wood_dark", "down": "home_wood_dark"}],
                           [1, 0, 1, 3, 11, 3, "home_wood_dark"], [13, 0, 1, 15, 11, 3, "home_wood_dark"],
                           [1, 0, 13, 3, 11, 15, "home_wood_dark"], [13, 0, 13, 15, 11, 15, "home_wood_dark"]],
        "Map Table", "WOOD", rotate=None)
    add("bunk_map_wall", [[0, 2, 15.6, 16, 15, 16, "bunk_map_wall"]], "Wall Map", "WOOD", decor=True)
    add("bunk_chair", [[3, 7, 3, 13, 8, 13, "bunk_grey"], [3, 8, 12, 13, 16, 13, "bunk_grey"],
                       [3, 0, 3, 4, 7, 4, "bunk_grey"], [12, 0, 3, 13, 7, 4, "bunk_grey"],
                       [3, 0, 12, 4, 16, 13, "bunk_grey"], [12, 0, 12, 13, 16, 13, "bunk_grey"]],
        "Folding Chair", "METAL")
    add("bunk_chair_down", [[3, 0, 3, 13, 1, 13, "bunk_grey"], [3, 1, 12, 13, 10, 13, "bunk_grey"],
                            [3, 9, 4, 4, 10, 12, "bunk_grey"], [12, 9, 4, 13, 10, 12, "bunk_grey"],
                            [3, 0, 2, 4, 1, 3, "bunk_grey"], [12, 0, 2, 13, 1, 3, "bunk_grey"]], "Folding Chair", "METAL")

    def med_bed(head):
        b = [[2, 0, 1, 3, 6, 2, "bunk_white_steel"], [13, 0, 1, 14, 6, 2, "bunk_white_steel"],
             [2, 0, 14, 3, 6, 15, "bunk_white_steel"], [13, 0, 14, 14, 6, 15, "bunk_white_steel"],
             [2, 5, 0, 14, 6, 16, "bunk_white_steel"], [2.5, 6, 0, 13.5, 8, 16, "bunk_mattress_med"]]
        if head:
            b += [[2, 0, 15, 14, 12, 16, "bunk_white_steel"], [4, 8, 11, 12, 9.5, 15, "home_pillow"]]
        else:
            b += [[2, 0, 0, 14, 9, 1, "bunk_white_steel"], [3, 8, 2, 13, 8.6, 9, "home_sheet"]]
        return b

    add("bunk_medbed_head", med_bed(True), "Hospital Bed", "METAL")
    add("bunk_medbed_foot", med_bed(False), "Hospital Bed", "METAL")
    add("bunk_medcab", [[1, 0, 4, 15, 16, 16, {"north": "bunk_medcab", "side": "bunk_white_steel",
                                               "up": "bunk_white_steel", "down": "bunk_white_steel"}]],
        "Medicine Cabinet", "METAL", loot="basement_medicine")
    add("bunk_iv_stand", [[7.5, 0, 7.5, 8.5, 15, 8.5, "home_chrome"], [5, 0, 7.5, 11, 0.5, 8.5, "home_chrome"],
                          [7.5, 0, 5, 8.5, 0.5, 11, "home_chrome"], [6, 15, 7.6, 10, 15.5, 8.4, "home_chrome"],
                          [6.5, 10, 7.7, 9.5, 14.8, 8.3, "bunk_ivbag"]], "Drip Stand", "METAL", rotate=None)

    # the storage hall: pallet racks two decks to a block, crates, drums, cans
    def rack(kind):
        b = [[0, 0, 0, 1.5, 16, 1.5, "bunk_rack_blue"], [14.5, 0, 0, 16, 16, 1.5, "bunk_rack_blue"],
             [0, 0, 14.5, 1.5, 16, 16, "bunk_rack_blue"], [14.5, 0, 14.5, 16, 16, 16, "bunk_rack_blue"],
             [0, 7, 0, 16, 8.5, 1.5, "bunk_rack_orange"], [0, 7, 14.5, 16, 8.5, 16, "bunk_rack_orange"],
             [1.5, 0, 1.5, 14.5, 1, 14.5, "bunk_pallet"],
             [1.5, 1, 2, 14.5, 7, 14, {"north": f"bunk_load_{kind}", "south": f"bunk_load_{kind}", "side": "bunk_crate_wood",
                                       "up": "bunk_crate_wood", "down": "bunk_pallet"}],
             [1.5, 8.5, 1.5, 14.5, 9.5, 14.5, "bunk_pallet"],
             [1.5, 9.5, 2, 14.5, 15.5, 14, {"north": f"bunk_load_{kind}", "south": f"bunk_load_{kind}",
                                            "side": "bunk_crate_wood", "up": "bunk_crate_wood", "down": "bunk_pallet"}]]
        return b

    for kind, table in (("boxes", "basement_food"), ("drums", "basement_supplies"), ("crates", "storage")):
        add(f"bunk_rack_{kind}", rack(kind), "Pallet Rack", "METAL", loot=table)
    add("bunk_crate_ration", [[1, 0, 1, 15, 12, 15, {"north": "bunk_crate_ration", "side": "bunk_crate_wood",
                                                     "up": "bunk_crate_wood", "down": "bunk_crate_wood"}]],
        "Ration Crate", "WOOD", loot="basement_food")
    add("bunk_crate_ammo", [[1, 0, 2, 15, 8, 14, {"north": "bunk_crate_ammo", "side": "bunk_olive", "up": "bunk_olive",
                                                  "down": "bunk_olive"}],
                            [0, 4, 6, 1, 5, 10, "home_rubber"], [15, 4, 6, 16, 5, 10, "home_rubber"]],
        "Ammo Crate", "WOOD", loot="basement_guns")
    add("bunk_crate_gun", [[0, 0, 3, 16, 7, 13, {"north": "bunk_crate_gun", "side": "bunk_olive", "up": "bunk_olive",
                                                 "down": "bunk_olive"}],
                           [0, 7, 3, 16, 8, 13, "bunk_olive"]], "Weapon Crate", "WOOD", loot="basement_guns")
    add("bunk_tins", [[1, 0, 1, 15, 10, 15, {"north": "bunk_tins", "side": "bunk_tins", "up": "home_cardboard",
                                             "down": "home_cardboard"}]], "Biscuit Tins", "WOOD", loot="basement_food")
    add("bunk_drum_water", [[3, 0, 3, 13, 15, 13, {"side": "bunk_drum_water", "up": "bunk_drum_top", "down": "bunk_drum_top"}],
                            [2.5, 1, 2.5, 13.5, 2, 13.5, "bunk_drum_water"], [2.5, 13, 2.5, 13.5, 14, 13.5, "bunk_drum_water"]],
        "Water Drum", "METAL", rotate=None)
    cans = []
    for x, rgb in ((1, "red"), (6, "olive"), (11, "red")):
        cans += [[x, 0, 5, x + 4, 10, 13, f"bunk_jerry_{rgb}"], [x + 1, 10, 8, x + 3, 11, 12, "bunk_grey"]]
    add("bunk_jerry_cans", cans, "Jerry Cans", "METAL")
    add("bunk_gun_rack", [[0, 2, 15, 16, 15, 16, "bunk_gunrack_board"], [0, 1, 14, 16, 15, 15, "bunk_rifles"],
                          [0, 2, 13.5, 16, 3, 15, "bunk_gunrack_board"], [0, 9, 13.5, 16, 9.8, 15, "bunk_gunrack_board"]],
        "Gun Rack", "WOOD", loot="basement_guns")
    add("bunk_pegboard", [[0, 2, 15.5, 16, 15, 16, "bunk_pegboard"]], "Pegboard", "WOOD", loot="basement_supplies")

    # the generator room
    add("bunk_gen_engine", [[0, 0, 1, 16, 2, 15, "bunk_skid"],
                            [1, 2, 2, 16, 13, 14, {"north": "bunk_gen_a", "south": "bunk_gen_a", "side": "bunk_gen_green",
                                                   "up": "bunk_gen_green", "down": "bunk_gen_green"}],
                            [6, 13, 6, 8, 16, 8, "bunk_rust_pipe"], [3, 13, 4, 12, 14, 12, "bunk_gen_green"]],
        "Generator", "METAL")
    add("bunk_gen_alternator", [[0, 0, 1, 16, 2, 15, "bunk_skid"],
                                [0, 2, 3, 12, 12, 13, {"north": "bunk_gen_b", "south": "bunk_gen_b", "side": "bunk_gen_green",
                                                       "up": "bunk_gen_green", "down": "bunk_gen_green"}],
                                [12, 2, 1, 15, 15, 7, {"north": "bunk_gen_b", "side": "bunk_gen_green", "up": "bunk_gen_green",
                                                       "down": "bunk_gen_green"}]], "Generator", "METAL")
    add("bunk_filter", [[1, 0, 2, 15, 16, 15, {"north": "bunk_filter_front", "side": "bunk_olive", "up": "bunk_olive",
                                               "down": "bunk_olive"}]], "Air Filter", "METAL")
    add("bunk_filter_top", [[2, 0, 3, 14, 10, 14, "bunk_olive"], [5, 10, 5, 11, 16, 11, "ext_galv"]], "Air Filter", "METAL")
    add("bunk_tank", [[2, 0, 2, 14, 16, 14, {"side": "bunk_tank", "up": "bunk_tank_top", "down": "bunk_tank_top"}]],
        "Fuel Tank", "METAL", rotate=None)
    add("bunk_tank_top", [[2, 0, 2, 14, 10, 14, {"side": "bunk_tank", "up": "bunk_tank_top", "down": "bunk_tank_top"}],
                          [7, 10, 7, 9, 12, 9, "ext_black_metal"]], "Fuel Tank", "METAL", rotate=None)
    add("bunk_fan", [[1, 1, 13, 15, 15, 16, {"north": "bunk_fan", "side": "bunk_grey", "up": "bunk_grey",
                                             "down": "bunk_grey"}]], "Wall Fan", "METAL")
    add("bunk_switchboard", [[0, 0, 4, 16, 16, 16, {"north": "bunk_switch", "side": "bunk_grey", "up": "bunk_grey",
                                                    "down": "bunk_grey"}]], "Switchboard", "METAL")
    add("bunk_switchboard_top", [[0, 0, 4, 16, 14, 16, {"north": "bunk_switch_top", "side": "bunk_grey",
                                                        "up": "bunk_grey", "down": "bunk_grey"}]], "Switchboard", "METAL")

    # story pieces
    add("bunk_bodybag", [[3, 0, 0, 13, 3, 16, "bunk_bodybag"], [5, 3, 2, 11, 4, 14, "bunk_bodybag"]], "Body Bag", "WOOL",
        decor=True)
    add("bunk_bodybag_head", [[3, 0, 0, 13, 3.5, 12, "bunk_bodybag"], [4, 3.5, 4, 12, 4.5, 11, "bunk_bodybag"]],
        "Body Bag", "WOOL", decor=True)
    add("bunk_casings", [[0, 0, 0, 16, 0.25, 16, "bunk_casings"]], "Spent Casings", "METAL", rotate=None, decor=True)
    add("bunk_tripwire", [[0, 2, 7.9, 16, 2.2, 8.1, "bunk_wire"], [5, 0.6, 7, 7, 2, 9, "home_can"],
                          [9, 0.4, 7.2, 11, 1.8, 8.8, "home_can"]], "Tin-Can Tripwire", "METAL", rotate="axis", decor=True)

    # the cargo ship's insides
    cube("ship_wall_white", "ship_wall_white", "Bulkhead", "METAL")
    cube("ship_wall_grey", "ship_wall_grey", "Bulkhead", "METAL")
    cube("ship_hold", "ship_hold", "Hold Plating", "METAL")
    cube("ship_porthole", {"north": "ship_porthole", "side": "ship_wall_white"}, "Porthole", "METAL", rotate="facing")
    cube("container_inside", "container_inside", "Container Wall", "METAL")
    cube("container_floor", {"up": "container_floor", "side": "container_inside", "down": "container_inside"},
         "Container Floor", "WOOD")
    cube("container_welded", {"north": "container_welded", "side": "container_red"}, "Welded Container", "METAL",
         rotate="facing")
    add("ship_frame", [[5, 0, 13, 11, 16, 16, "ship_hold"], [3, 0, 12, 13, 16, 13, "ship_hold"]], "Hold Frame", "METAL")
    add("ship_console", [[0, 0, 4, 16, 10, 16, {"north": "ship_console", "side": "bunk_grey", "down": "bunk_grey",
                                                "up": "ship_console_top"}],
                         [0, 10, 7, 16, 12, 16, {"up": "ship_console_top", "side": "bunk_grey", "down": "bunk_grey"}],
                         [0, 12, 10, 16, 14, 16, {"up": "ship_console_top", "side": "bunk_grey", "down": "bunk_grey"}]],
        "Bridge Console", "METAL")
    add("ship_telegraph", [[6, 0, 6, 10, 10, 10, "home_copper"], [4, 10, 6.5, 12, 16, 9.5,
                                                                 {"north": "ship_telegraph", "south": "ship_telegraph",
                                                                  "side": "home_copper", "up": "home_copper",
                                                                  "down": "home_copper"}]],
        "Engine Telegraph", "METAL")
    add("ship_lifebuoy", [[1, 2, 15.4, 15, 16, 16, "ship_lifebuoy"]], "Life Ring", "WOOL", decor=True)
    eng = {"north": "ship_engine_lo", "south": "ship_engine_lo", "side": "ship_engine_green",
           "up": "ship_engine_top", "down": "ship_engine_green"}
    add("ship_engine_lo", [[0, 0, 1, 16, 16, 15, eng]], "Ship's Engine", "METAL")
    hi = dict(eng, north="ship_engine_hi", south="ship_engine_hi")
    add("ship_engine_hi", [[0, 0, 2, 16, 8, 14, hi],
                           [1.5, 8, 3, 7.5, 13, 13, hi], [8.5, 8, 3, 14.5, 13, 13, hi],
                           [0, 9, 13.5, 16, 12, 16, "bunk_rust_pipe"],
                           [0, 6, 1.4, 16, 7, 2, "home_copper"]], "Ship's Engine", "METAL")

    # wear and tear: second and third versions of each finish, and what was left on them
    for k in ("b", "leak", "grime", "stain"):
        cube(f"bunk_paint_{k}", {"side": f"bunk_paint_{k}", "up": "bunk_concrete", "down": "bunk_concrete"}, "Painted Wall")
    for k in ("b", "scuff", "rot"):
        cube(f"bunk_dado_{k}", {"side": f"bunk_dado_{k}", "up": "bunk_concrete", "down": "bunk_concrete"}, "Painted Wall")
    cube("bunk_concrete_b", "bunk_concrete_b", "Poured Concrete")
    cube("bunk_concrete_crack", "bunk_concrete_crack", "Cracked Concrete")
    cube("bunk_ceiling", {"down": "bunk_ceiling", "side": "bunk_concrete", "up": "bunk_concrete"}, "Poured Concrete")
    cube("bunk_ceiling_b", {"down": "bunk_ceiling_b", "side": "bunk_concrete", "up": "bunk_concrete"}, "Poured Concrete")
    for k in ("b", "dirty", "crack", "stain"):
        cube(f"bunk_floor_{k}", {"up": f"bunk_floor_{k}", "side": "bunk_concrete", "down": "bunk_concrete"}, "Shelter Floor")
    cube("bunk_tile_dirty", "bunk_tile_dirty", "Tiled Wall")
    cube("bunk_plate_b", "bunk_plate_b", "Steel Plate", "METAL")
    cube("ship_wall_grey_b", "ship_wall_grey_b", "Bulkhead", "METAL")
    cube("ship_wall_white_b", "ship_wall_white_b", "Bulkhead", "METAL")
    add("bunk_locker_open", [[1, 0, 3, 15, 16, 16, {"north": "bunk_locker_open", "side": "bunk_grey", "up": "bunk_grey",
                                                    "down": "bunk_grey"}]], "Wall Locker", "METAL", loot="home_wardrobe")
    for k in ("leak", "tally", "scrawl_a", "scrawl_b", "scrawl_c", "bullets", "soot"):
        out[f"bunk_{k}"] = {"model": "boxes", "boxes": [[0, 0, 15.8, 16, 16, 16, f"bunk_{k}"]], "title": "Wall",
                            "sound": "STONE", "rotate": "facing", "decor": True, "render": "cutout"}
    out["bunk_cobweb"] = {"model": "boxes", "boxes": [[0, 6, 15.7, 16, 16, 15.8, "bunk_cobweb"],
                                                      [0, 15.8, 6, 16, 15.9, 16, "bunk_cobweb"]],
                          "title": "Cobweb", "sound": "WOOL", "rotate": "facing", "decor": True, "render": "cutout"}
    out["bunk_scorch"] = {"model": "boxes", "boxes": [[0, 0, 0, 16, 0.25, 16, "bunk_scorch"]], "title": "Scorch Mark",
                          "sound": "STONE", "rotate": "facing", "decor": True, "render": "translucent"}
    for k in ("puddle", "grit", "bootprints"):
        out[f"bunk_{k}"] = {"model": "boxes", "boxes": [[0, 0, 0, 16, 0.2, 16, f"bunk_{k}"]], "title": "Floor",
                            "sound": "STONE", "rotate": "facing" if k == "bootprints" else None, "decor": True,
                            "render": "translucent" if k == "puddle" else "cutout"}
        if k != "bootprints":
            del out[f"bunk_{k}"]["rotate"]
    for lit in (True, False):
        g = "bunk_glowstick" if lit else "bunk_glowstick_dead"
        add(g, [[4, 0, 6, 11, 1, 7, g], [6, 0, 9, 7, 1, 14, g]], "Glow Stick", "WOOL", decor=True, light=6 if lit else 0)
    out["garage_ramp_half"] = {"tex": "floor_concrete", "height": 8, "title": "Ramp", "sound": "STONE"}

    # a broken-into bunker's lights: still on a dying circuit, hanging off their cables, or down
    def flicker(name, swap, light):
        out[name]["flicker"] = {"light": light, "on": swap}
        out[name].pop("light", None)

    tube_on = {"bunk_tube_off": "bunk_tube_on"}
    add("bunk_tube_flicker", [[4, 14, 0, 12, 16, 16, "bunk_fitting"], [4.5, 13.5, 0.5, 11.5, 14, 15.5, "bunk_tube_off"]],
        "Strip Light", "METAL", rotate="axis", decor=True)
    flicker("bunk_tube_flicker", tube_on, 9)
    steps = []
    for i, (y0, z0) in enumerate(((13.5, 0), (10, 4), (6.5, 8), (3, 12))):
        steps += [[4, y0, z0, 12, y0 + 2, z0 + 4, "bunk_fitting"], [4.5, y0 - 0.5, z0, 11.5, y0, z0 + 4, "bunk_tube_off"]]
    steps += [[4, 15, 0, 12, 16, 1.5, "bunk_grey"], [7.6, 5, 14.6, 8.4, 16, 15.4, "bunk_cables"]]
    add("bunk_tube_dangle", steps, "Hanging Strip Light", "METAL", rotate="axis", decor=True)
    flicker("bunk_tube_dangle", tube_on, 7)
    add("bunk_lamp_dangle", [[7.6, -2, 7.6, 8.4, 16, 8.4, "bunk_cables"], [5.5, -3, 5.5, 10.5, -2, 10.5, "bunk_grey"],
                             [6, -7, 6, 10, -3, 10, "bunk_glass_off"], [5.5, -7.5, 5.5, 10.5, -3, 10.5, "bunk_cage_wire"]],
        "Hanging Lamp", "METAL", rotate=None, decor=True)
    flicker("bunk_lamp_dangle", {"bunk_glass_off": "bunk_glass_on"}, 8)
    add("bunk_tube_fallen", [[4, 0, 0.5, 12, 2, 15.5, "bunk_fitting"], [5, 2, 2, 11, 2.4, 9, "bunk_tube_off"],
                             [0, 0, 0, 16, 0.2, 16, "glass_shards"]], "Fallen Strip Light", "METAL", rotate="axis",
        decor=True)
    add("bunk_cable_loose", [[7, 2, 7.5, 7.6, 16, 8.1, "bunk_cables"], [8.4, 6, 8, 9, 16, 8.6, "bunk_cables"],
                             [7.6, 2, 7.5, 9, 2.6, 8.1, "bunk_cables"], [6, 11, 6.5, 6.6, 16, 7.1, "bunk_cables"]],
        "Loose Cables", "METAL", rotate=None, decor=True)
    add("bunk_duct_fallen", [[3, 0, 0, 13, 6, 16, "ext_galv"], [4, 6, 1, 12, 6.2, 15, "bunk_grit"]], "Fallen Duct",
        "METAL", rotate="axis")

    # the tower shelter's server hall
    add("bunk_server", [[1, 0, 1, 15, 16, 15, {"north": "bunk_server", "side": "bunk_server_side", "up": "bunk_server_lid",
                                               "down": "bunk_server_lid"}]], "Server Rack", "METAL")
    add("bunk_server_top", [[1, 0, 1, 15, 14, 15, {"north": "bunk_server_top", "side": "bunk_server_side",
                                                   "up": "bunk_server_lid", "down": "bunk_server_lid"}]],
        "Server Rack", "METAL")

    # the tower's parking levels: floors whose undersides are the level below's ceiling
    def slab(name, top, title, rotate=None):
        cube(name, {"up": top, "side": "bunk_concrete", "down": "park_ceiling"}, title, rotate=rotate)

    for k in ("", "_b", "_oil", "_crack", "_grit"):
        slab(f"park_floor{k}", f"park_floor{k}", "Parking Floor")
    slab("park_floor_tyre", "park_floor_tyre", "Parking Floor", "axis")
    slab("park_line", "park_line", "Bay Line", "axis")
    slab("park_line_yellow", "park_line_yellow", "Painted Line", "axis")
    slab("park_arrow", "park_arrow", "Painted Arrow", "facing")
    cube("park_lobby_dark", {"up": "marble_dark", "side": "concrete_dark", "down": "park_ceiling"}, "Lobby Floor")
    cube("park_lobby_white", {"up": "marble_white", "side": "concrete_dark", "down": "park_ceiling"}, "Lobby Floor")
    for k in LEVEL_PAINT:
        cube(f"park_band_{k}", {"side": f"park_band_{k}", "up": "bunk_concrete", "down": "bunk_concrete"}, "Parking Wall")
    for k in ("", "_b", "_soot"):
        cube(f"park_wall{k}", {"side": f"park_wall{k}", "up": "bunk_concrete", "down": "park_ceiling"}, "Parking Wall")
    cube("park_column", {"side": "park_column", "up": "bunk_concrete", "down": "bunk_concrete"}, "Column")
    add("park_wheelstop", [[1, 0, 9, 15, 2.5, 13, "park_wheelstop"]], "Wheel Stop", "STONE")
    for k in ("freight", "noparking", "stairs", "lifts", "authorised", "exit"):
        add(f"park_st_{k}", [[0, 0, 15.75, 16, 16, 16, f"park_st_{k}"]], "Stencil", "STONE", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # the kit keeps its place in the list (block ids are registered in this order); new pieces go
    # after the last of the old ones
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith(PREFIXES) or k in made}
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} bunker blocks in {path}")


if __name__ == "__main__":
    main()
