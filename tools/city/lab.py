"""Ridgeview Labs: the rl_ kit for the defence lab in the Black Pine Mountains and the breach under it
(world/county/Ridgeview*.java).

From the lab photos in refs/lab (underground levels, BSL-4 suits and airlocks, cleanrooms, decon
showers, lab benches, server rooms, animal holding). The levels under the hill are poured concrete
and shotcrete with a big ventilation duct along the crown and cable trays down the walls, lit by a
line of caged lamps. The labs are white wall panels over grey epoxy floors, stainless glove boxes
with their white gauntlets, blue positive-pressure suits hanging from coiled yellow air hoses, the
orange pull rods of the emergency showers, hazard stripes at every door.

At the bottom, the breach hall: the ring the programme built, a vertical torus of blue-grey magnet
casing over copper windings with bolted flange collars, fed by cooling lines and cable, its hole
choked with a poured concrete plug that has cracked, red heat glowing in the cracks. Beside the
specimen hall's tanks of murky fluid: one whole, one cracked and drained.

Blocks (the base model faces north; anything on a wall has its back at z = 16 and faces the room):
- surfaces: rl_panel, rl_panel_b (grimy), rl_panel_crack, rl_epoxy, rl_epoxy_b, rl_epoxy_crack,
  rl_shotcrete, rl_shotcrete_b;
- the ring, standing in the x-y plane, three blocks deep in z: rl_ring (a cell wholly in it) and
  rl_ring_{o,i}{base}_{turn}_{depth} (a cell its outer or inner rim crosses: a slab turned to the rim's
  bearing and set on it, as the rd_ kit does round silos), rl_ringc* the same with a flange collar;
  ring_layout() places them and writes data/zomboidcraft/ridgeview/ring.txt for the builder;
- the plug in its hole: rl_plug, rl_plug_{h,v,a,b} (a crack across the block), _glow (heat in it,
  light), _surge (the same at full light, swapped in during a surge); plug.txt lays them out;
  rl_rubble (broken concrete at its foot);
- services: rl_pipe (two cooling lines, axis), rl_pipe_valve, rl_cable (bundle on the floor, axis),
  rl_duct (crown duct, axis), rl_beacon (red warning beacon, flickers);
- the hall's desks: rl_console, rl_console_on;
- the specimen tanks (built three wide, three tall): rl_tank_plinth, rl_tank_glass, rl_tank_body
  (fluid with a dark shape in it), rl_tank_post, rl_tank_cap, rl_tank_broken, rl_tank_empty,
  rl_spill (decal);
- the labs: rl_glovebox, rl_glovebox_top, rl_suit, rl_suit_top (a box-built suit two high), rl_eyewash, rl_hazard (floor strip,
  axis), rl_files (scattered paper, decal), rl_boxes (archive boxes);
- signs (two by one, back at z = 16): rl_sgn_KEY_0..1.

    python3 tools/city/lab.py          rewrites the rl_ entries in blocks.json and the ring's and the
                                       plug's layouts in data/zomboidcraft/ridgeview

photo_textures.py draws the textures (lab.register()).
"""
import json
import math
import random
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
from furniture import area, disc, edges, sunk  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
WHITE_PANEL = (214, 216, 212)
EPOXY = (118, 122, 124)
CASING = (78, 92, 104)
COPPER = (168, 96, 52)
HAZARD_Y = (214, 168, 30)
GLOW = (255, 70, 24)
FLUID = (92, 118, 70)
SUIT_BLUE = (52, 104, 168)
HOSE_Y = (222, 182, 36)

# signs: text, field, ink
SIGNS = {
    "ring": ("RING HALL", (150, 26, 26), (250, 246, 236)),
    "specimen": ("SPECIMEN HOLDING", (26, 40, 70), (236, 236, 228)),
    "bsl4": ("BIOSAFETY LEVEL 4", (214, 168, 30), (16, 16, 16)),
    "decon": ("DECONTAMINATION", (26, 40, 70), (236, 236, 228)),
    "threshold": ("PROJECT THRESHOLD", (40, 42, 44), (220, 222, 216)),
    "records": ("RECORDS", (26, 40, 70), (236, 236, 228)),
    "plant": ("PLANT ROOM B3", (60, 60, 64), (236, 236, 228)),
    "ridgeview": ("RIDGEVIEW LABORATORIES", (232, 230, 222), (30, 40, 60)),
    "evac": ("BREACH - EVACUATE", (180, 26, 30), (255, 255, 255)),
}


# ---------------------------------------------------------------- surfaces

def panel(seed, grime=0.0, crack=0):
    """Two white wall panels a block, the seam down the middle and along the top, scuffed low."""
    t = P.Tex(seed, WHITE_PANEL)
    t.vary(0.04)
    t.smooth[:] = 0.45
    for x in (0, 32):
        area(t, x, 0, x + 1, N, mul=0.72, dh=-0.2)
    area(t, 0, 0, N, 1, mul=0.75, dh=-0.2)
    for x in (4, 28, 36, 60):          # the screw caps down each panel edge
        for y in (6, 30, 54):
            disc(t, x, y, 0.8, mul=0.8)
    yy = np.arange(N)[:, None] / N
    t.mix(np.clip((yy - 0.75) * 3, 0, 1) * np.clip(P.fbm(t.rng) + 0.6, 0, 1) * 0.5, (150, 146, 136), 0.6)
    if grime:
        t.grime(grime, (70, 62, 52))
        t.streaks(grime * 0.6)
    if crack:
        t.crack(crack, 0.3, (60, 58, 54))
    return t


def epoxy(seed, wear=0.2, crack=0):
    """Grey poured epoxy: a dull sheen, scuffed paths, a few chips back to the concrete."""
    t = P.Tex(seed, EPOXY)
    t.vary(0.05)
    t.smooth[:] = 0.55
    t.f0[:] = 20
    t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.1, 0, 1) * wear, (96, 98, 98), 0.8)
    t.smooth -= np.clip(P.fbm(t.rng), 0, 1) * wear * 0.6
    for _ in range(int(6 * wear) + 1):
        x, y = t.rng.uniform(0, N, 2)
        disc(t, x, y, t.rng.uniform(0.8, 2.2), col=(150, 146, 136), dh=-0.15, smooth=0.1)
    if crack:
        t.crack(crack, 0.35, (40, 40, 40))
    return t


def shotcrete(seed, wet=0.0):
    """Sprayed concrete on the tunnel walls: lumpy, grey-brown, damp where the rock seeps."""
    t = P.Tex(seed, (126, 120, 110))
    n = t.vary(0.18, ((4, 1.0), (8, 0.7), (16, 0.5), (32, 0.4)))
    t.h[:] = 0.6 + n * 0.3
    t.speckle(0.08, (92, 88, 80), 0.5, -0.05)
    t.speckle(0.04, (160, 156, 146), 0.4, 0.05)
    if wet:
        t.streaks(wet, 0.6)
        t.mix(np.clip(P.fbm(t.rng) * 1.5, 0, 1) * wet, (70, 74, 62), 0.4)
    return t


# ---------------------------------------------------------------- the ring

def ring_side(seed):
    """The ring's casing: blue-grey enamel in bolted plates, worn back at the edges, a stencilled tag."""
    t = P.Tex(seed, CASING)
    t.vary(0.05)
    t.smooth[:] = 0.5
    t.f0[:] = 40
    for x0 in (0, 32):
        edges(t, x0, 0, x0 + 32, N, 1)
        for y in (3, 29, 35, 61):
            for x in (x0 + 3, x0 + 29):
                disc(t, x, y, 1.1, col=(140, 146, 150), dh=0.2, smooth=0.7, f0=230)
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.6, 0, 1), (120, 92, 70), 0.5)       # worn to primer
    area(t, 8, 22, 24, 26, col=(226, 222, 206))                                  # the stencilled segment tag
    for x in range(9, 23, 3):
        area(t, x, 23, x + 2, 25, col=(30, 30, 30))
    t.grime(0.2)
    return t


def ring_front(seed):
    """The ring's flat faces: blue-grey casing in big plates, a raised rib across each, bolts,
    scorched and streaked where the heat came out round the hole."""
    t = P.Tex(seed, (70, 84, 96))
    t.vary(0.06)
    t.smooth[:] = 0.45
    t.f0[:] = 40
    edges(t, 0, 0, N, N, 1)
    area(t, 0, 28, N, 36, col=(92, 106, 116), dh=0.2)
    edges(t, 0, 28, N, 36, 1)
    for x in range(6, N, 13):
        for y in (32, 6, 58):
            disc(t, x, y, 1.3, col=(150, 154, 156), dh=0.25, smooth=0.7, f0=230)
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.5, 0, 1), (120, 92, 70), 0.45)
    t.streaks(0.5, 0.7)
    t.grime(0.3, (30, 26, 24))
    return t


def ring_face(seed):
    """The ring's face: copper windings in bands under a steel clamp, the coil ends showing."""
    t = P.Tex(seed, COPPER)
    t.smooth[:] = 0.6
    t.f0[:] = 236
    for y in range(N):
        t.c[y] *= 0.82 + 0.18 * np.cos(y * np.pi / 2) ** 2
        t.h[y] = 0.7 + 0.1 * np.cos(y * np.pi / 2) ** 2
    t.vary(0.08)
    for y0 in (0, 30):
        area(t, 0, y0, N, y0 + 4, col=(150, 152, 150), h=0.95, smooth=0.5, f0=230)
        for x in range(4, N, 12):
            disc(t, x, y0 + 2, 1.0, col=(90, 92, 92), dh=0.1)
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.5, 0, 1), (70, 110, 90), 0.5)       # verdigris
    t.grime(0.15)
    return t


def plug(seed, glow=0.0, run=None):
    """Poured concrete in lifts, the formwork lines across it, stained by the heat behind. With run,
    a fracture right across the block (h, v, a rising, b falling) to meet its neighbours' at the edge
    middles; with glow, red heat in it and scorched round it."""
    t = P.Tex(seed, (150, 146, 138))
    t.vary(0.1)
    for y in (16, 48):
        area(t, 0, y, N, y + 1, mul=0.8, dh=-0.1)
    t.speckle(0.05, (110, 106, 100), 0.5, -0.05)
    t.grime(0.3, (60, 56, 50))
    t.crack(2, 0.15, (90, 86, 80))
    if run:
        a, b = {"h": ((0, 32), (64, 32)), "v": ((32, 0), (32, 64)), "a": ((0, 64), (64, 0)), "b": ((0, 0), (64, 64))}[run]
        n = 64
        xs = np.linspace(a[0], b[0], n)
        ys = np.linspace(a[1], b[1], n)
        jit = np.cumsum(t.rng.normal(0, 0.9, n))
        jit -= np.linspace(jit[0], jit[-1], n)                 # meet the edges where the neighbours do
        dx, dy = b[0] - a[0], b[1] - a[1]
        nx, ny = -dy / math.hypot(dx, dy), dx / math.hypot(dx, dy)
        m = np.zeros((N, N), np.float32)
        for x, y, j in zip(xs, ys, jit):
            px, py = x + nx * j, y + ny * j
            for w in (-1, 0, 1):
                xi, yi = int(px + nx * w * 0.6) % N, int(py + ny * w * 0.6) % N
                m[yi, xi] = 1.0
        t.h[m > 0] -= 0.45
        t.c[m > 0] = (30, 26, 24)
        if glow:
            soft = P.blur(m, 1)
            t.mix(np.clip(P.blur(m, 4) * 5, 0, 1), (46, 26, 22), 0.55)        # scorched round it
            t.mix(np.clip(soft * 2.5, 0, 1), GLOW, glow)
            t.c[m > 0] = (255, 170, 110)
            t.emit = np.maximum(t.emit, np.clip(soft * 3, 0, 1) * glow)
    return t


# ---------------------------------------------------------------- services

def pipe(seed):
    """Green painted cooling line, a flow band and arrow every so often."""
    t = P.Tex(seed, (64, 96, 70))
    t.vary(0.05)
    t.smooth[:] = 0.45
    for y in range(N):
        t.c[y] *= 0.75 + 0.35 * np.sin(y * np.pi / N)
    area(t, 20, 0, 30, N, col=(220, 210, 180))
    for y in range(4, N, 16):
        area(t, 22, y, 28, y + 3, col=(30, 30, 30))
    t.grime(0.2)
    return t


def cable(seed):
    """A bundle of black and grey power cables, tie-wrapped."""
    t = P.Tex(seed, (30, 30, 32))
    for x in range(0, N, 6):
        col = (30, 30, 32) if (x // 6) % 3 else (70, 72, 74)
        area(t, x, 0, x + 6, N, col=col)
        area(t, x + 4, 0, x + 6, N, mul=0.6, dh=-0.1)
        area(t, x + 1, 0, x + 2, N, mul=1.4, smooth=0.5)
    for y in range(6, N, 20):
        area(t, 0, y, N, y + 2, col=(200, 200, 196), dh=0.1)
    return t


def duct(seed):
    """Spiral-wound galvanised duct."""
    t = P.Tex(seed, (168, 170, 168))
    t.smooth[:] = 0.55
    t.f0[:] = 220
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, mul=0.7, dh=-0.15)
        area(t, 0, y + 1, N, y + 2, mul=1.15, dh=0.1)
    t.vary(0.06)
    t.grime(0.3)
    return t


def hazard(seed):
    """Yellow and black hazard stripes, scuffed."""
    t = P.Tex(seed, HAZARD_Y)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) // 16) % 2 == 1] = (24, 24, 24)
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.3, 0, 1), (110, 108, 100), 0.6)
    t.smooth[:] = 0.4
    return t


def console(seed, on=False):
    """A control desk's sloped panel: switches, dials, a strip of status lamps, two screens."""
    t = P.Tex(seed, (70, 74, 76))
    t.smooth[:] = 0.4
    for x0 in (4, 34):
        area(t, x0, 4, x0 + 26, 26, col=(14, 16, 16), h=0.6, smooth=0.9)
        if on:
            for y in range(7, 24, 3):
                area(t, x0 + 2, y, x0 + int(t.rng.uniform(8, 24)), y + 1, col=(255, 60, 40))
            t.emit[4:26, x0:x0 + 26] = 0.6
        edges(t, x0 - 1, 3, x0 + 27, 27, 1)
    for i, x in enumerate(range(6, N - 4, 6)):
        lit = on and i % 2 == 0
        disc(t, x, 34, 1.4, col=(255, 40, 30) if lit else (80, 30, 26), dh=0.15, smooth=0.9)
        if lit:
            t.emit[33:36, x - 1:x + 2] = 1.0
    for x in range(8, N - 4, 10):
        disc(t, x, 46, 3.2, col=(200, 200, 190), dh=0.2)
        disc(t, x, 46, 3.2, ring=0.8, mul=0.5)
        area(t, x, 44, x + 1, 47, col=(20, 20, 20))
    for x in range(6, N - 4, 4):
        area(t, x, 55, x + 2, 60, col=(30, 30, 30) if x % 8 else (200, 40, 30), dh=0.2)
    t.grime(0.2)
    return t


def screen_dead(seed):
    t = P.Tex(seed, (16, 18, 18))
    t.smooth[:] = 0.9
    t.f0[:] = 40
    t.crack(2, 0.1, (90, 90, 90))
    return t


# ---------------------------------------------------------------- the tanks

def fluid(seed, shape=False):
    """Murky preserving fluid through thick glass: green-amber, lighter at the top, specks floating.
    With shape, a dark mass in it: the thing they kept."""
    t = P.Tex(seed, FLUID)
    t.vary(0.12, tint=(10, 14, 4))
    t.alpha[:] = 170
    t.smooth[:] = 0.95
    t.f0[:] = 25
    yy = np.arange(N)[:, None] / N
    t.c *= (1.15 - 0.3 * yy)[..., None]
    t.speckle(0.02, (180, 190, 140), 0.6)
    if shape:
        m = np.clip(P.fbm(t.rng, ((2, 1.0), (4, 0.6), (8, 0.3))) * 2.2 + 0.4, 0, 1)
        t.mix(m, (14, 16, 14), 0.9)
        t.alpha[:] = np.maximum(t.alpha, m * 245)
    t.emit[:] = 0.06
    return t


def glass_crack(seed):
    """The cracked tank: thick glass with a star of cracks, a crust of dried fluid down it."""
    t = P.Tex(seed, (170, 186, 176))
    t.alpha[:] = 70
    t.smooth[:] = 0.95
    t.f0[:] = 30
    cx, cy = t.rng.uniform(20, 44, 2)
    for k in range(9):
        a = k * 0.7 + t.rng.normal(0, 0.2)
        x, y = cx, cy
        for _ in range(40):
            xi, yi = int(x) % N, int(y) % N
            t.c[yi, xi] = (235, 240, 236)
            t.alpha[yi, xi] = 220
            a += t.rng.normal(0, 0.15)
            x += np.cos(a)
            y += np.sin(a)
    yy = np.arange(N)[:, None] / N
    crust = np.clip(yy * 2 - 1, 0, 1) * np.clip(P.fbm(t.rng) + 0.6, 0, 1)
    t.mix(crust, (96, 100, 60), 0.7)
    t.alpha[:] = np.maximum(t.alpha, crust * 200)
    return t


def spill(seed):
    """A dried pool of the tank fluid on the floor: a tide line round it, darker where it lay deep."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - 32, yy - 32) / 30 + P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 0.5
    m = d < 1
    t.c[m] = FLUID
    t.c[m] *= (0.7 + 0.3 * np.clip(d[m], 0, 1))[..., None]
    t.alpha[m] = 210
    t.smooth[m] = 0.85
    rim = (d > 0.9) & m
    t.c[rim] = (70, 80, 40)
    return t


# ---------------------------------------------------------------- the labs

def steel(seed):
    return F.steel(seed, (176, 178, 178))


def glovebox(seed):
    """A class III cabinet's front: a sloped window, two glove ports with their white gauntlets."""
    t = steel(seed)
    area(t, 4, 2, 60, 28, col=(60, 70, 72), h=0.6, smooth=0.95, f0=40)
    edges(t, 3, 1, 61, 29, 1)
    for x in (20, 44):
        disc(t, x, 44, 9, col=(120, 124, 124), dh=0.2, f0=230, smooth=0.7)
        disc(t, x, 44, 7, col=(236, 234, 226), smooth=0.3, f0=10)
        disc(t, x, 44, 7, ring=1, mul=0.7)
        disc(t, x - 2, 42, 2.5, mul=1.08)
    return t


def pvc(seed):
    """A pressure suit's blue PVC hanging slack: long folds down it, a taped seam, the shine of it."""
    t = P.Tex(seed, SUIT_BLUE)
    yy, xx = np.mgrid[0:N, 0:N]
    folds = np.sin(xx * 0.45 + P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 4) * 0.5 + 0.5
    t.c *= (0.72 + 0.4 * folds)[..., None]
    t.h[:] = 0.65 + folds * 0.2
    t.smooth[:] = 0.7
    area(t, 0, 40, N, 43, col=(226, 228, 224))
    t.grime(0.12, (40, 50, 60))
    return t


def visor(seed):
    """The helmet's clear visor: the dark inside through it, a long highlight, the bonded rim."""
    t = P.Tex(seed, SUIT_BLUE)
    yy, xx = np.mgrid[0:N, 0:N]
    v = ((xx - 32) / 26) ** 2 + ((yy - 30) / 22) ** 2 < 1
    t.c[v] = (40, 52, 58)
    t.smooth[v] = 0.97
    t.f0[v] = 40
    hl = v & (np.abs((xx - yy) - 4) < 3) & (xx < 28)
    t.c[hl] = (200, 220, 226)
    rim = (((xx - 32) / 28) ** 2 + ((yy - 30) / 24) ** 2 < 1) & ~v
    t.c[rim] = (210, 212, 206)
    return t


def hose(seed):
    """Coiled yellow air hose."""
    t = P.Tex(seed, HOSE_Y)
    t.smooth[:] = 0.6
    for y in range(0, N, 6):
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.2)
        area(t, 0, y + 1, N, y + 2, mul=1.15)
    return t


def files(seed):
    """Paper across the floor: memos, printouts, a manila folder, a boot print."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    for _ in range(9):
        x, y = t.rng.uniform(0, 52, 2)
        w, h = t.rng.uniform(10, 16), t.rng.uniform(12, 18)
        manila = t.rng.random() < 0.25
        area(t, x, y, x + w, y + h, col=(208, 178, 120) if manila else (226, 224, 214), alpha=255, h=0.8)
        if not manila:
            for ly in np.arange(y + 2, y + h - 2, 2):
                area(t, x + 2, ly, x + w * t.rng.uniform(0.5, 0.9), ly + 0.6, col=(90, 90, 96), alpha=255)
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.4, 0, 1), (120, 110, 90), 0.3)
    return t


def boxes(seed):
    """Archive boxes: brown card, the hand-written labels, a red CLASSIFIED stamp on one."""
    t = P.Tex(seed, (156, 124, 82))
    t.vary(0.06)
    for x0 in (0, 32):
        for y0 in (0, 32):
            edges(t, x0, y0, x0 + 32, y0 + 32, 1)
            area(t, x0 + 6, y0 + 8, x0 + 26, y0 + 18, col=(230, 226, 210))
            for ly in (y0 + 10, y0 + 13, y0 + 16):
                area(t, x0 + 8, ly, x0 + int(t.rng.uniform(14, 24)), ly + 1, col=(40, 40, 60))
            area(t, x0 + 12, y0 + 22, x0 + 20, y0 + 26, mul=0.6, dh=-0.2)       # the hand hole
    area(t, 38, 40, 58, 44, col=(176, 34, 30))
    t.grime(0.2)
    return t


def eyewash(seed):
    t = steel(seed)
    area(t, 0, 0, N, N, col=(214, 92, 24))
    t.vary(0.06)
    t.smooth[:] = 0.5
    return t


# the files' looks (world/files/LabFiles): a clipboard, the army's olive folder, a manila file, a torn note
DOC_LOOKS = {"clip": (120, 84, 50), "army": (86, 96, 58), "study": (206, 174, 112), "note": (226, 222, 206)}
DOC_SETS = {"Ridgeview Labs": "clip", "Quarantine orders": "army", "The Harvestman file": "study", "Survivor notes": "note"}


def doc(seed, look, col):
    """A file lying where someone left it: the cover or board, the top sheet of typing on it, a stamp."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    if look == "note":
        area(t, 14, 10, 50, 54, col=col, alpha=255, h=0.8)
        for x in range(14, 50, 3):                       # the torn top edge
            area(t, x, 10, x + 2, 10 + int(t.rng.integers(0, 4)), alpha=0)
        for y in range(16, 50, 4):
            area(t, 18, y, int(t.rng.uniform(30, 46)), y + 1, col=(40, 40, 70), alpha=255)
    else:
        area(t, 10, 6, 54, 58, col=col, alpha=255, h=0.85)
        edges(t, 10, 6, 54, 58, 1)
        area(t, 14, 12, 50, 54, col=(232, 230, 220), alpha=255, h=0.9)
        for y in range(18, 50, 3):
            area(t, 17, y, int(t.rng.uniform(32, 47)), y + 1, col=(60, 60, 66), alpha=255)
        if look == "clip":
            area(t, 26, 3, 38, 10, col=(176, 178, 178), alpha=255, h=1.0, smooth=0.7, f0=230)
        else:
            area(t, 32, 40, 48, 46, col=(176, 34, 30), alpha=255)    # the stamp
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.5, 0, 1) * (t.alpha > 0), (120, 110, 90), 0.3)
    return t


def doc_ids():
    """The files in data/zomboidcraft/files/files.json, as (id, look)."""
    f = HERE.parents[1] / "src/main/resources/data/zomboidcraft/files/files.json"
    return [(d["id"], DOC_SETS.get(d["set"], "note")) for d in json.loads(f.read_text())["files"]]


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 47300 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 14, W - 1, H - 14, fill=field)
    p.rect(3, 16, W - 3, H - 16, outline=tuple(min(255, v + 40) for v in field), width=0.6)
    tx0, tx1 = 8, W - 8
    if key in ("bsl4", "specimen", "evac"):     # the biohazard trefoil on the left
        cx, cy = 18, 32
        for k in range(3):
            a = k * 2 * np.pi / 3 - np.pi / 2
            p.ellipse(cx + 6 * np.cos(a) - 6, cy + 6 * np.sin(a) - 6, cx + 6 * np.cos(a) + 6, cy + 6 * np.sin(a) + 6,
                      outline=ink, width=2)
        p.ellipse(cx - 3, cy - 3, cx + 3, cy + 3, fill=ink)
        tx0 = 32
    size = civic.fit(text, BOLD, 14, tx1 - tx0)
    p.text((tx0 + tx1) / 2, 32, text, BOLD, size, ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 47350 + i + len(key) * 11, smooth=0.6, grime=0.2)


def textures():
    T = {}
    T["rl_panel"] = lambda: panel(47200)
    T["rl_panel_b"] = lambda: panel(47201, grime=0.5)
    T["rl_panel_crack"] = lambda: panel(47202, grime=0.4, crack=5)
    T["rl_epoxy"] = lambda: epoxy(47203)
    T["rl_epoxy_b"] = lambda: epoxy(47204, wear=0.6)
    T["rl_epoxy_crack"] = lambda: epoxy(47205, wear=0.5, crack=6)
    T["rl_shotcrete"] = lambda: shotcrete(47206)
    T["rl_shotcrete_b"] = lambda: shotcrete(47207, wet=0.6)
    T["rl_ring_side"] = lambda: ring_side(47208)
    T["rl_ring_face"] = lambda: ring_face(47209)
    T["rl_ring_front"] = lambda: ring_front(47239)
    T["rl_steel_flange"] = lambda: F.steel(47210, (120, 124, 126))
    T["rl_plug"] = lambda: plug(47211)
    for i, c in enumerate(CRACKS):
        T[f"rl_plug_{c}"] = lambda c=c, i=i: plug(47240 + i, run=c)
        T[f"rl_plug_{c}_glow"] = lambda c=c, i=i: plug(47240 + i, run=c, glow=1.0)
    T["rl_pipe"] = lambda: pipe(47214)
    T["rl_cable"] = lambda: cable(47215)
    T["rl_duct"] = lambda: duct(47216)
    T["rl_hazard"] = lambda: hazard(47217)
    T["rl_console"] = lambda: console(47218)
    T["rl_console_on"] = lambda: console(47218, on=True)
    T["rl_desk"] = lambda: F.steel(47219, (88, 92, 94))
    T["rl_screen_dead"] = lambda: screen_dead(47220)
    T["rl_fluid"] = lambda: fluid(47221)
    T["rl_fluid_body"] = lambda: fluid(47222, shape=True)
    T["rl_glass_crack"] = lambda: glass_crack(47223)
    T["rl_spill"] = lambda: spill(47224)
    T["rl_steel"] = lambda: steel(47225)
    T["rl_glovebox"] = lambda: glovebox(47226)
    T["rl_pvc"] = lambda: pvc(47227)
    T["rl_visor"] = lambda: visor(47228)
    T["rl_boot"] = lambda: F.plain(47236, (32, 32, 36), 0.06, 0.4)
    T["rl_hose"] = lambda: hose(47229)
    T["rl_files"] = lambda: files(47230)
    T["rl_boxes"] = lambda: boxes(47231)
    T["rl_eyewash"] = lambda: eyewash(47232)
    T["rl_beacon_off"] = lambda: F.plain(47233, (120, 24, 20), 0, 0.9)
    T["rl_beacon_on"] = lambda: lit(F.plain(47234, (255, 60, 40), 0, 0.9))
    for key in SIGNS:
        for i in range(2):
            T[f"rl_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for i, (look, col) in enumerate(DOC_LOOKS.items()):
        T[f"rl_doc_{look}"] = lambda look=look, col=col, i=i: doc(47260 + i, look, col)
    return T


def lit(t):
    t.emit[:] = 1.0
    return t


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the ring's geometry

R_OUT, R_IN = 10.5, 7.75     # the ring's outer and inner radius in blocks, round a block's centre
COLLARS = 8                  # bolted flange collars round it


def bearing(dx, dy):
    """The base direction (0 east, 1 up, 2 west, 3 down) and the turn (-22.5..45) nearest a cell's
    radial bearing from the ring's centre."""
    phi = math.degrees(math.atan2(dy, dx)) % 360
    q = round(phi / 22.5) % 16 * 22.5
    for base in range(4):
        r = (q - base * 90 + 180) % 360 - 180
        if r in (-22.5, 0.0, 22.5, 45.0):
            return base, r
    raise ValueError(q)


def ring_layout():
    """Every cell of the ring as (dx, dy, kind, base, turn, depth, collar): kind "cube" for a cell
    wholly in the ring, "o"/"i" for one the outer/inner rim crosses, with the rim's distance in px
    from the cell's centre along the radial (out positive)."""
    cells = []
    n = int(R_OUT) + 2
    for dx in range(-n, n + 1):
        for dy in range(-n, n + 1):
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx)) % (360 / COLLARS)
            collar = min(ang, 360 / COLLARS - ang) * math.pi / 180 * d < 0.6
            base, turn = bearing(dx, dy)
            if abs(d - R_OUT) < 0.75:
                depth = max(-8, min(8, round((R_OUT - d) * 16 / 2) * 2))
                cells.append((dx, dy, "o", base, turn, depth, collar))
            elif abs(d - R_IN) < 0.75:
                depth = max(-8, min(8, round((R_IN - d) * 16 / 2) * 2))
                cells.append((dx, dy, "i", base, turn, depth, collar))
            elif R_IN < d < R_OUT:
                cells.append((dx, dy, "cube", 0, 0, 0, collar))
    return cells


TURN_NAMES = {0.0: "0", -22.5: "m22", 22.5: "p22", 45.0: "p45"}


def ring_name(kind, base, turn, depth, collar):
    c = "c" if collar else ""
    if kind == "cube":
        return f"rl_ring{c}"
    return f"rl_ring{c}_{kind}{base}_{TURN_NAMES[turn]}_{depth + 8}"


def ring_piece(kind, base, turn, depth, collar):
    """A rim cell's box: a slab 16 px thick on the ring's side of the rim, turned to its bearing."""
    lo, hi = (depth - 16, depth) if kind == "o" else (depth, depth + 16)
    half = 16.5 / 2 if turn == 0 else 12 if turn == 45 else 11
    if base == 0:
        box = [8 + lo, 8 - half, 0, 8 + hi, 8 + half, 16]
    elif base == 2:
        box = [8 - hi, 8 - half, 0, 8 - lo, 8 + half, 16]
    elif base == 1:
        box = [8 - half, 8 + lo, 0, 8 + half, 8 + hi, 16]
    else:
        box = [8 - half, 8 - hi, 0, 8 + half, 8 - lo, 16]
    box = [round(v, 3) for v in box]
    if collar:
        box[2], box[5] = -2, 18
    return box, ({"axis": "z", "angle": turn, "origin": [8, 8, 8]} if turn else None)


# the plug's cracks: lines across it, each cell a crack running h, v, a (/) or b (\)
CRACKS = ("h", "v", "a", "b")


def plug_layout(seed=11):
    """Every cell inside the inner rim as (dx, dy, crack or None, glow): a few fractures run across
    the plug from a burst a little off its middle, like the face of a dam that has started to go."""
    rng = random.Random(seed)
    cells = {}
    for dx in range(-int(R_IN) - 1, int(R_IN) + 2):
        for dy in range(-int(R_IN) - 1, int(R_IN) + 2):
            if math.hypot(dx, dy) < R_IN - 0.75:
                cells[(dx, dy)] = None
    ox, oy = 1.4, -0.8
    for k in range(7):
        a = k * 2 * math.pi / 7 + rng.uniform(-0.3, 0.3)
        x, y = ox, oy
        for step in range(30):
            a += rng.uniform(-0.35, 0.35)
            x += math.cos(a) * 0.5
            y += math.sin(a) * 0.5
            c = (math.floor(x + 0.5), math.floor(y + 0.5))
            if c not in cells:
                break
            deg = math.degrees(a) % 180
            kind = "h" if deg < 22.5 or deg >= 157.5 else "a" if deg < 67.5 else "v" if deg < 112.5 else "b"
            glow = step < 14 or rng.random() < 0.4
            if cells[c] is None or glow:
                cells[c] = (kind, glow)
    return [(dx, dy) + (v if v else (None, False)) for (dx, dy), v in sorted(cells.items())]


def plug_name(crack, glow):
    if crack is None:
        return "rl_plug"
    return f"rl_plug_{crack}{'_glow' if glow else ''}"


def write_layouts():
    """The ring's and the plug's cells for world/county/Ridgeview.java, one line each: dx dy block."""
    d = HERE.parents[1] / "src/main/resources/data/zomboidcraft/ridgeview"
    d.mkdir(parents=True, exist_ok=True)
    lines = [f"{dx} {dy} {ring_name(k, b, t, dp, c)}" for dx, dy, k, b, t, dp, c in ring_layout()]
    (d / "ring.txt").write_text("# the breach ring, cell offsets from its centre (x right, y up): dx dy block\n"
                                + "\n".join(lines) + "\n")
    lines = [f"{dx} {dy} {plug_name(c, g)}" for dx, dy, c, g in plug_layout()]
    (d / "plug.txt").write_text("# the concrete plug in the ring, cell offsets from its centre: dx dy block\n"
                                + "\n".join(lines) + "\n")


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    def cube(name, title, sound, tex=None, **extra):
        add(name, [[0, 0, 0, 16, 16, 16, tex or name]], title, sound, rotate=None, **extra)

    # surfaces
    for n in ("rl_panel", "rl_panel_b", "rl_panel_crack"):
        cube(n, "Lab Wall Panel", "METAL")
    for n in ("rl_epoxy", "rl_epoxy_b", "rl_epoxy_crack"):
        cube(n, "Epoxy Floor", "STONE")
    for n in ("rl_shotcrete", "rl_shotcrete_b"):
        cube(n, "Shotcrete", "STONE")

    # the ring: casing round the windings; the faces either side (north and south) show the coils
    side, face, col = "rl_ring_side", "rl_ring_face", "rl_steel_flange"
    seen = set()
    for dx, dy, kind, base, turn, depth, collar in ring_layout():
        name = ring_name(kind, base, turn, depth, collar)
        if name in seen:
            continue
        seen.add(name)
        f = col if collar else "rl_ring_front"
        rim = col if collar else face if kind == "i" else side
        tex = {"north": f, "south": f, "side": rim, "up": rim, "down": rim}
        if kind == "cube":
            box = [0, 0, -2, 16, 16, 18] if collar else [0, 0, 0, 16, 16, 16]
            add(name, [box + [tex]], "Ring Collar" if collar else "Ring Casing", "METAL", rotate=None,
                shape=[[0, 0, 0, 16, 16, 16]])
        else:
            box, turned = ring_piece(kind, base, turn, depth, collar)
            boxes = [box + [tex] + ([None, turned] if turned else [])]
            add(name, boxes, "Ring Collar" if collar else "Ring Casing", "METAL", rotate=None,
                shape=[[0, 0, 0, 16, 16, 16]])
            if kind == "i":     # the same with the plug filling the rest of the cell, set back 2 px behind the rim
                add(name + "_p", boxes + [[0, 0, 2, 16, 16, 16, "rl_plug"]], "Ring Casing", "METAL", rotate=None,
                    shape=[[0, 0, 0, 16, 16, 16]])
    cube("rl_plug", "Concrete Plug", "STONE")
    for c in CRACKS:
        cube(f"rl_plug_{c}", "Cracked Concrete Plug", "STONE")
        cube(f"rl_plug_{c}_glow", "Cracked Concrete Plug", "STONE", light=8)
        cube(f"rl_plug_{c}_surge", "Cracked Concrete Plug", "STONE", tex=f"rl_plug_{c}_glow", light=15)
    add("rl_rubble", [[0, 0, 0, 16, 3, 16, "rl_plug"], [2, 3, 3, 9, 7, 10, "rl_plug", None,
                                                       {"axis": "y", "angle": 22.5, "origin": [5, 5, 6]}],
                      [9, 3, 6, 15, 6, 13, "rl_plug_h"], [5, 3, 11, 9, 5, 15, "rl_plug"],
                      [11, 6, 8, 12, 12, 9, "rl_steel_flange", None, {"axis": "z", "angle": 22.5, "origin": [11, 6, 8]}]],
        "Concrete Rubble", "STONE", shape=[[0, 0, 0, 16, 6, 16]])

    # services: lines run along z as drawn
    add("rl_pipe", [[2, 1, 0, 7, 6, 16, "rl_pipe"], [9, 1, 0, 14, 6, 16, "rl_pipe"],
                    [1, 0, 7, 15, 1, 9, "rl_steel_flange"], [1.5, 0, 7, 2.5, 7, 9, "rl_steel_flange"],
                    [13.5, 0, 7, 14.5, 7, 9, "rl_steel_flange"]], "Cooling Lines", "METAL", rotate="axis",
        shape=[[1, 0, 0, 15, 7, 16]])
    add("rl_pipe_valve", [[2, 1, 0, 7, 6, 16, "rl_pipe"], [9, 1, 0, 14, 6, 16, "rl_pipe"],
                          [1.5, 0.5, 5, 7.5, 6.5, 11, "rl_steel_flange"], [4, 6.5, 7.5, 5, 10, 8.5, "rl_steel_flange"],
                          [1, 10, 4, 8, 11, 12, "rl_beacon_off"], [4, 10, 7.5, 5, 11, 8.5, "rl_steel_flange"]],
        "Valve", "METAL", rotate="axis", shape=[[1, 0, 0, 15, 11, 16]])
    add("rl_cable", [[1, 0, 0, 15, 3, 16, {"up": "rl_cable", "side": "rl_cable", "down": "rl_cable"}]], "Cable Bundle",
        "WOOL", rotate="axis", shape=[[1, 0, 0, 15, 3, 16]])
    add("rl_duct", [[1, 1, 0, 15, 15, 16, "rl_duct"], [0, 0, 15, 16, 16, 16, "rl_duct"]], "Ventilation Duct", "METAL",
        rotate="axis", shape=[[1, 1, 0, 15, 15, 16]])
    add("rl_beacon", [[5, 0, 5, 11, 2, 11, "rl_desk"], [6, 2, 6, 10, 7, 10, "rl_beacon_off"]], "Warning Beacon", "METAL",
        rotate=None, flicker={"light": 10, "on": {"rl_beacon_off": "rl_beacon_on"}}, shape=[[5, 0, 5, 11, 7, 11]])

    # the hall's desks: a steel desk with its sloped panel
    for n, tex, lt in (("rl_console", "rl_console", 0), ("rl_console_on", "rl_console_on", 5)):
        extra = {"light": lt} if lt else {}
        add(n, [[0, 0, 4, 16, 9, 16, "rl_desk"],
                [0, 9, 4, 16, 10, 16, "rl_desk"],
                [0.5, 9, 6, 15.5, 11, 15, {"up": tex, "north": "rl_desk", "side": "rl_desk", "down": "rl_desk"},
                 None, {"axis": "x", "angle": 22.5, "origin": [8, 10, 10]}],
                [1, 11, 13, 15, 18, 15, {"north": "rl_screen_dead" if not lt else tex, "side": "rl_desk"}]],
            "Control Desk", "METAL", **extra, shape=[[0, 0, 4, 16, 12, 16]])

    # the tanks: a ring of glass on a steel plinth, a cap of hoses
    cube("rl_tank_plinth", "Tank Plinth", "METAL", tex={"up": "rl_steel", "side": "rl_hazard", "down": "rl_steel"})
    for n, tex in (("rl_tank_glass", "rl_fluid"), ("rl_tank_body", "rl_fluid_body")):
        add(n, [[0, 0, 0, 16, 16, 16, tex]], "Specimen Tank", "GLASS", rotate=None, render="translucent", light=3)
    add("rl_tank_broken", [[0, 0, 0, 16, 16, 16, "rl_glass_crack"]], "Cracked Tank", "GLASS", rotate=None,
        render="translucent")
    add("rl_tank_empty", [[0, 0, 0, 16, 2, 16, "rl_spill"], [0, 0, 0, 16, 16, 16, "rl_glass_crack"]], "Drained Tank",
        "GLASS", rotate=None, render="translucent")
    add("rl_tank_post", [[5, 0, 5, 11, 16, 11, "rl_steel"], [4, 0, 4, 12, 2, 12, "rl_steel_flange"],
                         [4, 14, 4, 12, 16, 12, "rl_steel_flange"]], "Tank Frame", "METAL", rotate=None,
        shape=[[5, 0, 5, 11, 16, 11]])
    add("rl_tank_cap", [[0, 0, 0, 16, 6, 16, {"side": "rl_steel_flange", "up": "rl_steel", "down": "rl_steel"}],
                        [3, 6, 3, 7, 10, 7, "rl_hose"], [9, 6, 9, 13, 12, 13, "rl_hose"],
                        [6, 6, 10, 9, 16, 13, "rl_pipe"]], "Tank Cap", "METAL", rotate=None,
        shape=[[0, 0, 0, 16, 6, 16]])
    add("rl_spill", [[0, 0, 0, 16, 0.2, 16, {"up": "rl_spill", "down": "rl_spill", "side": "rl_spill"}]], "Dried Fluid",
        "WOOL", rotate=None, decor=True, render="translucent", shape=[])

    # the labs
    add("rl_glovebox", [[0, 0, 2, 16, 16, 16, {"north": "rl_steel", "side": "rl_steel", "up": "rl_steel", "down": "rl_steel"}],
                        [1, 1, 1, 7, 15, 2, "rl_desk"], [9, 1, 1, 15, 15, 2, "rl_desk"]],
        "Glove Box", "METAL")
    add("rl_glovebox_top", [[0, 0, 2, 16, 12, 16, {"north": "rl_glovebox", "side": "rl_steel", "up": "rl_steel", "down": "rl_steel"},
                             None, {"axis": "x", "angle": -22.5, "origin": [8, 0, 16]}],
                            [0, 0, 6, 16, 14, 16, "rl_steel"],
                            [6, 14, 8, 10, 16, 12, "rl_duct"]],
        "Glove Box", "METAL", shape=[[0, 0, 2, 16, 14, 16]])
    # the suit hangs slack from its hose against the wall: legs and boots, the body, arms down to the
    # yellow gauntlets, the big helmet with its visor; two blocks, the top under the ceiling hose
    pvc, boot, glove = "rl_pvc", "rl_boot", "rl_hose"
    add("rl_suit", [[4.5, 0, 9, 7.8, 2.5, 13.5, boot], [8.2, 0, 9, 11.5, 2.5, 13.5, boot],
                    [4.5, 2.5, 9.5, 7.8, 10, 13, pvc], [8.2, 2.5, 9.5, 11.5, 10, 13, pvc],
                    [4, 10, 9, 12, 16, 13.5, pvc],
                    [1.5, 8, 10, 4, 10.5, 12.5, glove], [12, 8, 10, 14.5, 10.5, 12.5, glove],
                    [1.8, 10.5, 10, 4, 16, 12.5, pvc], [12, 10.5, 10, 14.2, 16, 12.5, pvc]],
        "Pressure Suit", "WOOL", shape=[[2, 0, 9, 14, 16, 14]])
    add("rl_suit_top", [[3.5, 0, 8.5, 12.5, 6, 14, pvc],
                        [1.8, 0, 10, 4, 4.5, 12.5, pvc], [12, 0, 10, 14.2, 4.5, 12.5, pvc],
                        [2.5, 4, 9.5, 13.5, 6, 13.5, pvc],
                        [4, 6, 8, 12, 14, 14.5, {"north": "rl_visor", "side": pvc, "up": pvc, "down": pvc}],
                        [7, 1.5, 7.6, 9, 3.5, 8.5, "rl_steel_flange"],
                        [7.2, 14, 10.2, 8.8, 16, 11.8, glove]],
        "Pressure Suit", "WOOL", shape=[[2, 0, 8, 14, 14, 14.5]])
    add("rl_eyewash", [[7, 0, 13, 9, 16, 15, "rl_steel"], [3, 13, 8, 13, 14, 15, "rl_eyewash"],
                       [7.5, 6, 12, 8.5, 13, 13, "rl_eyewash"], [5, 12, 9, 11, 13, 13, "rl_eyewash"]],
        "Emergency Shower", "METAL", decor=True)
    add("rl_hazard", [[4, 0, 0, 12, 0.2, 16, {"up": "rl_hazard", "down": "rl_hazard", "side": "rl_hazard"}]],
        "Hazard Stripe", "STONE", rotate="axis", decor=True, shape=[])
    add("rl_files", [[0, 0, 0, 16, 0.25, 16, {"up": "rl_files", "down": "rl_files", "side": "rl_files"}]], "Papers",
        "WOOL", rotate="facing", decor=True, render="cutout", shape=[])
    add("rl_boxes", [[0, 0, 1, 16, 8, 15, "rl_boxes"], [1, 8, 2, 15, 15, 14, "rl_boxes", None,
                                                         {"axis": "y", "angle": 22.5, "origin": [8, 8, 8]}]],
        "Archive Boxes", "WOOD", shape=[[0, 0, 1, 16, 15, 15]])

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    for key in SIGNS:
        for i in range(2):
            add(f"rl_sgn_{key}_{i}", plate(f"rl_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    # the files: one block each (world/files/LabFiles reads the name), lying flat, small enough to step over
    for fid, look in doc_ids():
        tex = f"rl_doc_{look}"
        add(f"rl_doc_{fid}", [[2, 0, 2, 14, 0.6, 14, {"up": tex, "down": tex, "side": tex}]], "Document", "WOOL",
            decor=True, render="cutout", shape=[[2, 0, 2, 14, 1, 14]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("rl_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    write_layouts()
    print(f"{len(made)} lab blocks in {path}")


if __name__ == "__main__":
    main()
