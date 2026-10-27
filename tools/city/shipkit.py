#!/usr/bin/env python3
"""The ship fit-out kit: what fills the inside of the carrier and the cargo ships, so a compartment
reads as a ship's and not a steel box. The overheads (pipe runs with their bands, the firemain,
cable trays, vent trunks, sprinkler lines and the deep frames across them), what hangs on the
bulkheads (stiffeners, valves, junction boxes, speakers, sound-powered phones, fire stations,
damage control lockers, escape breathing sets) and the lighting a warship carries: caged
fluorescent fittings, red night lights, battery battle lanterns, caged globes, hangar dome lamps,
floodlights and the green escape signs. Lights come lit, dead, or on a dying circuit (Flicker).

    python3 tools/city/shipkit.py        rewrites the fit_ entries in blocks.json

photo_textures.py draws the textures (shipkit.register()). Pieces follow furniture.py's rule:
the base model faces north with its back (z = 16) against what it hangs on; overhead pieces sit in
the top of their block (y 11 to 16) and run along z when they take an axis.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import bunker as B  # noqa: E402
import vessel as V  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = V.BOLD
PREFIXES = ("fit_",)

GREY = (126, 130, 132)
YELLOW = (204, 166, 40)
RED = (164, 36, 30)
GREEN = (34, 150, 74)


# ---------------------------------------------------------------- painters

def steel(seed, rgb, rough=0.35):
    return V.steel_paint(seed, rgb, rough)


def lens(seed, rgb, on):
    """A lamp's glass: bright in the middle when lit, grey and fly-specked when not."""
    return B.glass(seed, rgb, on)


def dome(seed):
    """A hangar lamp's shade: grey enamel outside, chipped at the rim."""
    t = F.enamel(seed, (92, 98, 102))
    F.area(t, 0, 56, N, N, (150, 150, 146), f0=180)
    B.chips(t, 0.08, (120, 116, 108))
    return F.wear(t, 0.3, 3)


def exit_sign(seed):
    """An escape sign: white letters and an arrow on green, faintly lit from inside."""
    t = F.plain(seed, GREEN, 0.03, 0.8)
    F.area(t, 0, 0, N, 2, (230, 230, 226))
    F.area(t, 0, 62, N, N, (230, 230, 226))
    m = B.fit_text("ESCAPE", BOLD, 14, N, 30, seed)
    full = np.zeros((N, N), np.float32)
    full[6:36, :] = m
    t.c[full > 0.45] = np.array((238, 240, 232), np.float32)
    yy, xx = np.mgrid[0:N, 0:N]
    arrow = ((np.abs(yy - 47) <= 3) & (xx >= 14) & (xx <= 44)) | ((xx >= 40) & (xx <= 52) & (np.abs(yy - 47) <= 52 - xx))
    t.c[arrow] = np.array((238, 240, 232), np.float32)
    t.emit[:] = 0.55
    return t


def hose(seed):
    """Canvas fire hose, faded red, laid in flat coils."""
    t = F.plain(seed, (150, 40, 32), 0.08, 0.2)
    yy = np.arange(N)[:, None]
    t.c *= (0.8 + 0.3 * np.abs(np.sin(yy / N * np.pi * 6)))[..., None]
    t.speckle(0.04, (110, 100, 90), 0.4)
    return F.wear(t, 0.3, 0)


def locker(seed, text):
    """A damage control locker's door: grey with louvres at the top, red stencil, a padlock hasp."""
    t = steel(seed, (128, 132, 132), 0.4)
    for y in range(4, 16, 3):
        F.area(t, 10, y, 54, y + 1, mul=0.55, dh=-0.1)
    B.stencil(t, text, (176, 34, 28), 11, 4, 20, 56, 26, chip=0.15, font=BOLD)
    F.area(t, 0, 0, 2, N, mul=0.7)
    F.area(t, 62, 0, N, N, mul=0.7)
    F.area(t, 50, 46, 56, 54, (60, 60, 58), f0=150)
    B.rust_runs(t, 3, 0.35, 56)
    return F.wear(t, 0.3, 4)


def canister(seed):
    """An escape breathing set's yellow canister with its black band and label."""
    t = steel(seed, YELLOW, 0.3)
    F.area(t, 0, 10, N, 16, (30, 30, 28))
    B.stencil(t, "EEBD", (24, 24, 22), 12, 4, 24, 56, 22, chip=0.05, font=BOLD)
    return F.wear(t, 0.25, 3)


def grille(seed):
    """A speaker's face: grey pressed steel with rows of slots."""
    t = steel(seed, (150, 152, 150), 0.4)
    for y in range(10, 54, 6):
        F.area(t, 10, y, 54, y + 3, (36, 36, 34), dh=-0.2)
    return F.wear(t, 0.25, 2)


def phone(seed):
    """A sound-powered phone box: the handset in its cradle, the circuit stencilled on the lid."""
    t = steel(seed, (140, 144, 144), 0.4)
    F.area(t, 14, 18, 50, 28, (30, 30, 28), dh=0.2)
    F.area(t, 12, 16, 18, 30, (30, 30, 28), dh=0.2)
    F.area(t, 46, 16, 52, 30, (30, 30, 28), dh=0.2)
    F.area(t, 22, 38, 42, 54, (44, 44, 42), dh=-0.1)
    B.stencil(t, "JZ", (24, 24, 22), 10, 4, 2, 56, 14, chip=0.1, font=BOLD)
    return F.wear(t, 0.3, 3)


def jbox(seed):
    """A junction box: grey, four bolts, a white label plate."""
    t = steel(seed, (136, 140, 140), 0.4)
    for x, y in ((6, 6), (54, 6), (6, 54), (54, 54)):
        F.area(t, x, y, x + 4, y + 4, (90, 90, 88), dh=0.15)
    F.area(t, 16, 24, 48, 36, (226, 222, 210))
    B.stencil(t, "1-110-2", (30, 30, 28), 7, 16, 24, 32, 12, chip=0.02, font=BOLD)
    return F.wear(t, 0.3, 3)


def flood_body(seed):
    t = steel(seed, (70, 74, 76), 0.45)
    for x in range(4, N, 8):
        F.area(t, x, 0, x + 2, N, mul=0.7, dh=0.15)
    return F.wear(t, 0.3, 3)


def yellow(seed):
    """Support gear's yellow enamel: smooth, chipped to grey at the edges, grimy low down."""
    t = F.enamel(seed, YELLOW)
    B.chips(t, 0.05, (96, 96, 92))
    return F.wear(t, 0.3, 4)


def gse(seed):
    """Flight deck support gear's yellow: louvres down one side, NO STEP, oil and scuffs."""
    t = F.enamel(seed, YELLOW)
    B.chips(t, 0.05, (96, 96, 92))
    for y in range(8, 30, 4):
        F.area(t, 8, y, 30, y + 2, (60, 52, 20), dh=-0.15)
    B.stencil(t, "NO STEP", (24, 24, 22), 10, 6, 38, 52, 14, chip=0.15, font=BOLD)
    F.area(t, 0, 58, N, N, (70, 62, 40))
    B.rust_runs(t, 4, 0.3, 50)
    return F.wear(t, 0.4, 5)


def bottle(seed):
    """A wheeled fire bottle: red with a white band and its stencil."""
    t = steel(seed, RED, 0.3)
    F.area(t, 0, 8, N, 16, (226, 222, 210))
    B.stencil(t, "HALON", (226, 222, 210), 10, 4, 28, 56, 16, chip=0.1, font=BOLD)
    return F.wear(t, 0.3, 3)


def enamel_steel(seed, rgb):
    """Frames and beams in the bulkheads' glossy off-white enamel, a weld line along them, a little rust."""
    t = F.enamel(seed, rgb)
    t.smooth[:] = 0.7
    F.area(t, 0, 31, N, 33, mul=0.9, dh=0.05)
    B.rust_runs(t, 2, 0.25)
    return F.wear(t, 0.15, 3, specks=0.002)


BEIGE = (198, 188, 158)


def beige(seed):
    """The berthing's beige enamel (racks, lockers, cabinets), scuffed at the edges."""
    t = F.enamel(seed, BEIGE)
    B.chips(t, 0.03, (120, 116, 104))
    return F.wear(t, 0.18, 4, specks=0.003)


def pan_front(seed):
    """A rack's storage pan: the lid seam, a red latch, a card holder for the name."""
    t = beige(seed)
    F.area(t, 0, 0, N, 2, mul=0.8, dh=0.1)
    F.area(t, 0, 20, N, 22, mul=0.7, dh=-0.1)
    F.area(t, 28, 30, 38, 38, (176, 36, 30), dh=0.2)
    F.area(t, 8, 34, 22, 44, (226, 222, 210), dh=0.1)
    F.area(t, 42, 34, 56, 44, (226, 222, 210), dh=0.1)
    return t


def locker_door(seed):
    """A tall locker's door: vents at the top, a chrome handle, the edges darker."""
    t = beige(seed)
    for y in range(6, 18, 3):
        F.area(t, 14, y, 50, y + 1, (60, 58, 52), dh=-0.15)
    F.area(t, 50, 28, 54, 42, (190, 192, 192), dh=0.25, f0=180)
    F.area(t, 0, 0, 2, N, mul=0.8)
    F.area(t, 62, 0, N, N, mul=0.8)
    return t


def stainless(seed):
    """Brushed stainless steel: the galley, the sick bay's sinks and trolleys."""
    t = F.plain(seed, (178, 180, 180), 0.03, 0.7)
    rng = t.rng
    for y in range(N):
        t.c[y] *= 0.94 + 0.08 * rng.random()
    t.f0[:] = 170
    return F.wear(t, 0.12, 6, specks=0.002)


def oven_front(seed):
    """A stainless deck oven: two doors with their windows, the knobs along the top."""
    t = stainless(seed)
    for y0 in (14, 40):
        F.area(t, 6, y0, 58, y0 + 22, mul=0.9, dh=0.15)
        F.area(t, 14, y0 + 5, 50, y0 + 14, (34, 30, 26), dh=-0.1)
        F.area(t, 10, y0 + 1, 54, y0 + 3, (200, 202, 202), dh=0.3)
    for x in range(8, 58, 10):
        F.disc(t, x + 2, 7, 2.5, (40, 40, 40), dh=0.3)
    return t


def dispenser_front(seed):
    """A drinks dispenser on the serving line: three nozzles under their coloured labels."""
    t = stainless(seed)
    for i, col in enumerate(((176, 40, 34), (220, 140, 40), (110, 60, 140))):
        x = 6 + i * 19
        F.area(t, x, 10, x + 14, 22, col)
        F.area(t, x + 4, 30, x + 10, 40, (40, 40, 40), dh=0.3)
        F.area(t, x + 2, 52, x + 12, 56, (60, 60, 60), dh=-0.1)
    return t


def cabinet_front(seed):
    """A sick bay cabinet: two beige doors, their handles, a red cross on a white square."""
    t = beige(seed)
    F.area(t, 31, 0, 33, N, mul=0.7, dh=-0.1)
    F.area(t, 26, 26, 28, 38, (190, 192, 192), dh=0.25, f0=180)
    F.area(t, 36, 26, 38, 38, (190, 192, 192), dh=0.25, f0=180)
    F.area(t, 40, 8, 56, 24, (236, 234, 226))
    F.area(t, 46, 10, 50, 22, (190, 30, 30))
    F.area(t, 42, 14, 54, 18, (190, 30, 30))
    return t


def litter(seed):
    """A Stokes litter stowed on the bulkhead: orange wire basket, a grid of rods."""
    t = B.clear(seed)
    col = (214, 98, 30)
    for k in list(range(0, N, 8)) + [N - 2]:
        F.area(t, k, 0, k + 2, N, col, alpha=255)
        t.alpha[:, k:k + 2] = 255
    for k in (0, 31, N - 3):
        F.area(t, 0, k, N, k + 3, col, alpha=255)
        t.alpha[k:k + 3, :] = 255
    return t


def vinyl(seed, rgb=(116, 36, 42)):
    t = F.plain(seed, rgb, 0.06, 0.45)
    return F.wear(t, 0.2, 2)


def deck_line(seed):
    """A painted yellow line on the hangar deck, scuffed through to the non-skid by tyres and boots."""
    t = B.clear(seed)
    F.area(t, 26, 0, 38, N, (214, 172, 44), alpha=255)
    t.alpha[:, 26:38] = 255
    rng = t.rng
    for _ in range(40):
        x, y = rng.integers(26, 38), rng.integers(0, N)
        t.alpha[y:y + rng.integers(1, 4), x:x + rng.integers(1, 3)] = 0
    return t


def lagging(seed):
    """Lagged pipe: off-white canvas over the insulation, banded with colour, the seams stitched, grimy."""
    t = F.plain(seed, (214, 210, 196), 0.05, 0.2)
    for y in range(0, N, 8):
        F.area(t, 0, y, N, y + 1, mul=0.86, dh=-0.1)
    F.area(t, 0, 24, N, 30, (36, 92, 160))
    F.area(t, 0, 30, N, 32, (230, 226, 210))
    return F.wear(t, 0.35, 2)


def label(seed, text, col=(226, 222, 210), ink=(24, 24, 22)):
    """An engraved plate screwed to the bulkhead."""
    t = B.clear(seed)
    F.area(t, 4, 20, 60, 44, col, alpha=255)
    t.alpha[20:44, 4:60] = 255
    B.stencil(t, text, ink, 10, 6, 22, 52, 20, chip=0.03, font=BOLD)
    return t


# ---- the island and the machinery spaces

def line(t, x0, y0, x1, y1, col, w=1):
    """A thin straight line: a needle, a course pencilled on a chart, a mimic diagram's pipe."""
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for k in range(n + 1):
        x, y = int(round(x0 + (x1 - x0) * k / n)), int(round(y0 + (y1 - y0) * k / n))
        F.area(t, x, y, x + w, y + w, col)


def gauge(t, cx, cy, r, rng):
    """A round gauge: black bezel, white face and ticks; most needles dropped to zero, a red arc on some."""
    F.disc(t, cx, cy, r, (34, 34, 32), dh=0.25, f0=160, smooth=0.7)
    F.disc(t, cx, cy, r - 1.3, (234, 232, 222), smooth=0.85)
    for k in range(9):
        a = np.radians(135 + k * 33.75)
        F.area(t, int(round(cx + (r - 2.3) * np.cos(a))), int(round(cy + (r - 2.3) * np.sin(a))),
               int(round(cx + (r - 2.3) * np.cos(a))) + 1, int(round(cy + (r - 2.3) * np.sin(a))) + 1, (30, 30, 28))
    if rng.random() < 0.35:
        for k in range(5):
            a = np.radians(345 + k * 12)
            x, y = int(round(cx + (r - 2.3) * np.cos(a))), int(round(cy + (r - 2.3) * np.sin(a)))
            F.area(t, x, y, x + 1, y + 1, (190, 40, 34))
    a = np.radians(135 + (0 if rng.random() < 0.7 else rng.uniform(20, 250)))
    line(t, cx, cy, cx + (r - 2.6) * np.cos(a), cy + (r - 2.6) * np.sin(a), (40, 40, 38))
    F.disc(t, cx, cy, 1, (30, 30, 28))


def board(seed):
    """A gauge board's off-white enamel, the edges rolled darker."""
    t = F.enamel(seed, (222, 222, 214))
    t.smooth[:] = 0.6
    for a in ((0, 0, N, 2), (0, 62, N, N)):
        F.area(t, *a, mul=0.8, dh=0.1)
    for a in ((0, 0, 2, N), (62, 0, N, N)):
        F.area(t, *a, mul=0.85)
    return t


def gauges(seed):
    """The upper half of a gauge board: big gauges, a row of small ones, square meters, blue name plates."""
    t = board(seed)
    rng = t.rng
    for x in (12, 32, 52):
        gauge(t, x, 12, 8.5, rng)
        F.area(t, x - 6, 22, x + 6, 25, (40, 84, 160))
    for x in (8, 20, 32, 44, 56):
        gauge(t, x, 33, 5.2, rng)
    for x in (5, 25, 45):
        F.area(t, x, 42, x + 14, 54, (30, 30, 28), dh=0.2)
        F.area(t, x + 1.5, 43.5, x + 12.5, 52.5, (232, 230, 220), smooth=0.85)
        line(t, x + 7, 51, x + 3 + rng.integers(0, 8), 45, (40, 40, 38))
        F.area(t, x + 2, 56, x + 12, 59, (40, 84, 160))
    return F.wear(t, 0.2, 3, specks=0.004)


def throttle(seed):
    """The lower half of a gauge board: the mimic of the steam lines, toggles, lamps and their plates."""
    t = board(seed)
    rng = t.rng
    for y, col in ((6, (180, 40, 34)), (13, (40, 84, 160))):
        F.area(t, 4, y, 60, y + 1, col)
    line(t, 20, 6, 20, 26, (180, 40, 34))
    line(t, 44, 13, 44, 26, (40, 84, 160))
    for x, y in ((12, 6), (30, 6), (52, 13), (20, 20), (44, 22)):
        F.area(t, x - 2, y - 2, x + 2, y + 3, (30, 30, 28))
    for x in range(5, 60, 8):
        F.area(t, x, 32, x + 5, 38, (70, 72, 72), dh=0.2)
        F.area(t, x + 2, 29 if rng.random() < 0.5 else 36, x + 3, 33 if rng.random() < 0.5 else 40, (200, 200, 196), dh=0.4)
        F.disc(t, x + 2.5, 44, 1.6, (150, 40, 30) if rng.random() < 0.5 else (40, 110, 50), dh=0.3, smooth=0.9)
    for x in (4, 24, 44):
        F.area(t, x, 50, x + 16, 54, (40, 84, 160))
    return F.wear(t, 0.3, 4, specks=0.004)


def wheel_tex(seed, col):
    """A valve's hand wheel cut out on clear: rim, five spokes, the hub."""
    t = B.clear(seed)
    B.wheel(t, 32, 32, 30, 5, col, spokes=5, hub=6)
    t.f0[t.alpha > 0] = 60
    return t


def bronze(seed):
    """Valve bodies and bonnets: cast bronze, green where it weeps."""
    t = F.plain(seed, (150, 108, 58), 0.08, 0.55)
    t.f0[:] = 200
    t.speckle(0.03, (70, 120, 96), 0.6)
    return F.wear(t, 0.3, 2)


def plate(seed):
    """Diamond deck plate: raised bars crossing in a grid, worn bright on top, oily in the low spots."""
    t = F.plain(seed, (96, 98, 96), 0.04, 0.45)
    t.f0[:] = 120
    for y in range(0, N, 8):
        for x in range(0, N, 8):
            if (x // 8 + y // 8) % 2:
                F.area(t, x + 1, y + 3, x + 7, y + 5, mul=1.45, dh=0.35)
                F.area(t, x + 1, y + 5, x + 7, y + 6, mul=0.7)
            else:
                F.area(t, x + 3, y + 1, x + 5, y + 7, mul=1.45, dh=0.35)
                F.area(t, x + 5, y + 1, x + 6, y + 7, mul=0.7)
    n = P.fbm(t.rng, ((16, 1.0), (32, 0.5)))
    t.c[n > 0.45] *= 0.72
    return F.wear(t, 0.15, 3, specks=0.003)


def burner(seed):
    """A boiler front: three burner registers in their bronze rings, the fire-row doors cold and sooted."""
    t = F.plain(seed, (70, 72, 72), 0.06, 0.35)
    for x in (12, 32, 52):
        F.disc(t, x, 26, 8, (150, 108, 58), dh=0.3, f0=200)
        F.disc(t, x, 26, 6, (24, 22, 20), dh=-0.2)
        F.disc(t, x, 26, 2, (110, 112, 110), dh=0.4)
        F.area(t, x - 1, 36, x + 1, 46, (150, 108, 58), dh=0.3)
    F.area(t, 0, 0, N, 6, mul=0.7)
    F.area(t, 0, 52, N, N, (54, 48, 40))
    B.rust_runs(t, 4, 0.4, 30)
    return F.wear(t, 0.5, 4, col=(20, 18, 16))


def console_face(seed):
    """A bridge console's panel: rows of lit-push buttons, a label strip, toggles."""
    t = F.enamel(seed, (88, 94, 96))
    t.smooth[:] = 0.5
    rng = t.rng
    cols = ((190, 40, 34), (40, 150, 70), (214, 168, 40), (226, 226, 218), (54, 56, 56))
    for y in range(8, 40, 8):
        for x in range(6, 58, 7):
            F.area(t, x, y, x + 5, y + 5, cols[rng.integers(len(cols))], dh=0.25, smooth=0.8)
            F.area(t, x, y + 5, x + 5, y + 6, mul=0.5)
    F.area(t, 4, 44, 60, 49, (30, 30, 30))
    for x in range(6, 60, 6):
        F.area(t, x, 45, x + 3, 48, (200, 200, 196))
    for x in range(8, 60, 10):
        F.area(t, x, 53, x + 2, 60, (200, 200, 196), dh=0.3)
    return F.wear(t, 0.25, 4, specks=0.003)


def console_top(seed):
    """The console's desk: a keyboard, the trackball, a clipboard left on it."""
    t = F.enamel(seed, (72, 78, 80))
    for y in range(6, 30, 6):
        for x in range(4, 44, 5):
            F.area(t, x, y, x + 4, y + 4, (150, 154, 152), dh=0.2)
    F.disc(t, 52, 16, 5, (30, 30, 30), dh=0.4, smooth=0.9)
    F.area(t, 18, 38, 46, 62, (230, 226, 210))
    F.area(t, 28, 36, 36, 40, (170, 172, 170), dh=0.3)
    for y in range(44, 60, 4):
        F.area(t, 21, y, 43, y + 1, (60, 60, 70))
    return F.wear(t, 0.25, 4)


def screen(seed, on):
    """A console display: lit, a radar picture and rows of amber data; dead, dark glass and a reflection."""
    t = F.plain(seed, (14, 20, 18), 0.04, 0.9)
    t.f0[:] = 40
    rng = t.rng
    if on:
        g = (90, 220, 120)
        for r in (6, 12, 18):
            F.disc(t, 22, 32, r, g, ring=1)
        line(t, 22, 32, 36, 22, g)
        for _ in range(8):
            F.disc(t, rng.integers(8, 36), rng.integers(16, 48), 1, (200, 255, 200))
        for y in range(10, 56, 5):
            F.area(t, 44, y, 44 + rng.integers(6, 16), y + 2, (230, 176, 70))
        bright = t.c.sum(-1) > 200
        t.emit[:] = 0.2
        t.emit[bright] = 1.0
    else:
        yy, xx = np.mgrid[0:N, 0:N]
        band = np.abs((xx + yy) - 52) < 6
        t.c[band] *= 2.2
        t.speckle(0.03, (90, 90, 86), 0.5)
    F.area(t, 0, 0, N, 3, (40, 42, 42))
    F.area(t, 0, 61, N, N, (40, 42, 42))
    t.emit[:3] = 0
    t.emit[61:] = 0
    return t


def chart(seed):
    """A sea chart on the chart table: land, the shoals in blue, soundings, the pencilled course and its fixes."""
    t = F.plain(seed, (222, 224, 212), 0.03, 0.25)
    n = P.fbm(t.rng, ((16, 1.0), (32, 0.5)))
    t.c[n > 0.2] = (226, 212, 164)
    t.c[(n > -0.05) & (n <= 0.2)] = (190, 212, 228)
    t.c[np.abs(n + 0.05) < 0.015] = (90, 120, 150)
    for k in range(0, N, 16):
        F.area(t, k, 0, k + 1, N, mul=0.85)
        F.area(t, 0, k, N, k + 1, mul=0.85)
    F.disc(t, 48, 46, 9, (170, 40, 140), ring=1)
    line(t, 6, 58, 40, 10, (50, 50, 54))
    for x, y in ((14, 47), (23, 34), (32, 21)):
        F.disc(t, x, y, 2, (50, 50, 54), ring=1)
    return F.wear(t, 0.15, 2, specks=0.004)


def drawers(seed):
    """A chart table's flat drawers: wide and shallow, a brass pull on each."""
    t = F.enamel(seed, (150, 154, 150))
    for y in range(0, N, 10):
        F.area(t, 0, y, N, y + 1, mul=0.6, dh=-0.1)
        F.area(t, 26, y + 4, 38, y + 6, (180, 150, 80), dh=0.3, f0=200)
    return F.wear(t, 0.2, 3)



def louvre(seed):
    """A vent's louvre panel in the hull: a haze grey frame, the slats in shadow, rust in the bottom corners."""
    t = F.plain(seed, (118, 124, 128), 0.04, 0.4)
    for y in range(8, 56, 6):
        F.area(t, 6, y, 58, y + 3, (34, 36, 38), dh=-0.2)
        F.area(t, 6, y + 3, 58, y + 4, mul=1.2, dh=0.1)
    F.area(t, 0, 0, N, 4, mul=0.8, dh=0.1)
    F.area(t, 0, 60, N, N, mul=0.8, dh=0.1)
    B.rust_runs(t, 3, 0.4, 50)
    return F.wear(t, 0.2, 2)


def hatch(seed):
    """A watertight door in her side: the raised frame, six dogs round it, the frame number stencilled over it."""
    t = F.plain(seed, (118, 124, 128), 0.04, 0.4)
    F.area(t, 6, 4, 58, 62, mul=0.86, dh=-0.05)
    F.area(t, 8, 6, 56, 60, mul=1.08, dh=0.1)
    for x, y in ((6, 14), (6, 32), (6, 50), (56, 14), (56, 32), (56, 50)):
        F.area(t, x - 2, y - 1, x + 2, y + 2, (70, 72, 72), dh=0.3)
    B.rust_runs(t, 2, 0.35, 40)
    return F.wear(t, 0.25, 2)

# ---- officers' country: the wardroom, the staterooms, the captain's cabin, the officers' head
# (from the wardroom, stateroom and head photos in refs/ships/carrier-officer-spaces)

CHERRY = (124, 56, 36)
LAMINATE = (178, 140, 96)


def laminate(seed, rgb=LAMINATE):
    """A joiner bulkhead's wood-grain laminate, grain upright, a seam at each panel's edge, scuffed low down."""
    t = F.wood(seed, "maple", rot=True)
    F.tone(t, rgb, 0.8)
    t.smooth[:] = 0.5
    for x in (0, 31):
        F.area(t, x, 0, x + 1, N, mul=0.62, dh=-0.2)
    F.area(t, 0, 58, N, N, mul=0.84)
    return F.wear(t, 0.2, 5, specks=0.003)


def cherry(seed):
    """The wardroom's cherry veneer on the pillars: deep red-brown under the lacquer, a moulding at the top."""
    t = F.wood(seed, "walnut", rot=True)
    F.tone(t, CHERRY, 1.1)
    t.smooth[:] = 0.7
    t.f0[:] = 30
    F.area(t, 0, 0, N, 3, mul=1.2, dh=0.2)
    F.area(t, 0, 3, N, 4, mul=0.6, dh=-0.1)
    for x in (0, 63):
        F.area(t, x, 0, x + 1, N, mul=0.7)
    return F.wear(t, 0.12, 4, specks=0.002)


def wall_cream(seed):
    """The wardroom's walls: cream-painted joiner panels, a seam each half block, a little grubby."""
    t = F.enamel(seed, (214, 200, 166))
    t.smooth[:] = 0.4
    F.area(t, 31, 0, 33, N, mul=0.85, dh=-0.15)
    return F.wear(t, 0.15, 2, specks=0.003)


def wainscot(seed):
    """Below the chair rail: cherry panelling, the rail along the top, a kick strip, chairs' knocks."""
    t = cherry(seed)
    F.area(t, 0, 0, N, 5, mul=1.25, dh=0.3)
    F.area(t, 0, 5, N, 6, mul=0.5, dh=-0.2)
    for x0 in (3, 35):
        F.sunk(t, x0, 12, x0 + 26, 54)
    F.area(t, 0, 58, N, N, (40, 34, 30), dh=0.1)
    return F.wear(t, 0.15, 6)


def ceiling_tile(seed):
    """Drop ceiling, two tiles a side to a block: fissured off-white fibre in white T-bars, a leak's brown ring."""
    t = F.plain(seed, (222, 220, 210), 0.03, 0.1)
    rng = t.rng
    for _ in range(260):
        x, y = int(rng.integers(0, N)), int(rng.integers(0, N))
        F.area(t, x, y, x + int(rng.integers(1, 4)), y + 1, mul=0.82, dh=-0.1)
    for k in (0, 32):
        F.area(t, k, 0, k + 2, N, (238, 238, 234), dh=0.3, smooth=0.6)
        F.area(t, 0, k, N, k + 2, (238, 238, 234), dh=0.3, smooth=0.6)
    if rng.random() < 0.6:
        F.stain(t, rng.uniform(6, 58), rng.uniform(6, 58), rng.uniform(5, 9), (150, 118, 66), 0.45)
    return F.wear(t, 0.16, 0, specks=0.004)


def troffer(seed, on):
    """A fluorescent troffer's prismatic lens: lit, an even glare; dead, grey, flies dead in one corner."""
    t = F.plain(seed, (240, 244, 240) if on else (172, 174, 168), 0.02, 0.6)
    for y in range(2, 62, 3):
        for x in range(2 + (y // 3) % 2, 62, 3):
            F.area(t, x, y, x + 1, y + 1, mul=0.88 if on else 0.8)
    if on:
        t.emit[:] = 1.0
    else:
        t.speckle(0.02, (40, 36, 30), 0.6)
        F.area(t, 2, 2, 14, 10, mul=0.75)
    return t


def tile_cream(seed):
    """The wardroom's floor: big glossy cream tiles, four to a block, grey grout, scuffed dull in the walkways."""
    t = F.plain(seed, (204, 184, 140), 0.035, 0.78)
    t.f0[:] = 40
    rng = t.rng
    for i in range(2):
        for j in range(2):
            F.area(t, i * 32, j * 32, i * 32 + 32, j * 32 + 32, mul=0.95 + 0.08 * rng.random())
    for k in (0, 32):
        F.area(t, k, 0, k + 1, N, (150, 138, 116), dh=-0.25, smooth=0.2)
        F.area(t, 0, k, N, k + 1, (150, 138, 116), dh=-0.25, smooth=0.2)
    yy, xx = np.mgrid[0:N, 0:N]
    t.smooth -= np.clip(P.fbm(rng) * 0.6, 0, 0.4)
    return F.wear(t, 0.22, 6, specks=0.004)


def carpet(seed):
    """Stateroom carpet tile: dark navy loop pile flecked with grey, the tiles' seams."""
    t = F.mat("Carpet003", (46, 52, 76), 0.6, seed=seed, smooth=0.05)
    t.speckle(0.05, (110, 116, 130), 0.4)
    F.area(t, 0, 0, N, 1, mul=0.7, dh=-0.1)
    F.area(t, 0, 0, 1, N, mul=0.7, dh=-0.1)
    return F.wear(t, 0.3, 0)


def linen(seed, soiled=False):
    """A white tablecloth: a fine weave, the creases it was folded in, a coffee ring; soiled, wine and worse."""
    t = F.plain(seed, (232, 230, 222), 0.02, 0.15)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.97 + 0.03 * ((xx + yy) % 2))[..., None]
    for k in (16, 48):
        F.area(t, k, 0, k + 1, N, mul=0.9, dh=-0.05)
        F.area(t, 0, k, N, k + 1, mul=0.92, dh=-0.05)
    rng = t.rng
    F.disc(t, rng.uniform(12, 52), rng.uniform(12, 52), 4, mul=0.8, ring=1)
    if soiled:
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), 6, (120, 30, 40), 0.6)
        F.blood(t, rng.uniform(0, N), rng.uniform(0, N), 5, 0.7)
    return F.wear(t, 0.25 if soiled else 0.15, 0, specks=0.004)


STAR = ("..#..", "#####", ".###.", ".#.#.", "#...#")


def star_cloth(seed):
    """The wardroom chairs' covers: navy blue cloth scattered with small white stars, as in the photos."""
    t = F.fabric("blue", seed)
    F.tone(t, (30, 38, 70), 1.0)
    for gy in range(0, N, 21):
        for gx in range(0, N, 21):
            ox = 10 if (gy // 21) % 2 else 2
            x0, y0 = (gx + ox) % N, gy + 5
            for r, row in enumerate(STAR):
                for c, ch in enumerate(row):
                    if ch == "#" and y0 + r < N:
                        t.c[y0 + r, (x0 + c) % N] = (226, 226, 230)
    return F.wear(t, 0.2, 0, specks=0.01)


def ship_painting(seed, kind):
    """An oil of a ship at sea in a gilt frame (made-up ships, no real hull numbers): a carrier in a grey
    swell, a destroyer at sunset, two jets over a carrier at dawn."""
    t = F.plain(seed, (40, 32, 24), 0.05, 0.3)
    x0, y0, x1, y1 = 5, 9, 59, 47
    sky = {"a": ((120, 132, 140), (160, 168, 170)), "b": ((200, 120, 80), (236, 186, 120)),
           "c": ((150, 176, 200), (226, 210, 190))}[kind]
    hz = 31
    for y in range(y0, hz):
        f = (y - y0) / (hz - y0)
        F.area(t, x0, y, x1, y + 1, tuple(int(sky[0][i] + (sky[1][i] - sky[0][i]) * f) for i in range(3)))
    sea = (52, 70, 76) if kind != "b" else (70, 60, 70)
    for y in range(hz, y1):
        F.area(t, x0, y, x1, y + 1, tuple(int(c * (1 - 0.3 * (y - hz) / (y1 - hz))) for c in sea))
    rng = t.rng
    for _ in range(40):
        x, y = int(rng.integers(x0, x1 - 3)), int(rng.integers(hz + 2, y1 - 1))
        F.area(t, x, y, x + int(rng.integers(1, 4)), y + 1, (200, 206, 204))
    hull = (78, 82, 86) if kind != "b" else (40, 36, 40)
    if kind in ("a", "c"):
        s = 1.0 if kind == "a" else 0.6
        cx = 32 if kind == "a" else 40
        L = int(22 * s)
        F.area(t, cx - L, hz - 2, cx + L, hz + 1, hull)
        F.area(t, cx - L + 2, hz + 1, cx + L - 1, hz + 2, mul=0.7)
        F.area(t, cx + int(6 * s), hz - int(7 * s), cx + int(10 * s), hz - 2, hull)
        F.area(t, cx + int(7 * s), hz - int(10 * s), cx + int(8 * s), hz - int(7 * s), hull)
        F.area(t, cx - L, hz + 1, cx + L, hz + 2, (220, 220, 214))
    else:
        cx = 30
        F.area(t, cx - 16, hz - 2, cx + 18, hz + 1, hull)
        for k in range(4):
            F.area(t, cx + 18 + k, hz - 2 + k // 2, cx + 19 + k, hz + 1, hull)
        F.area(t, cx - 6, hz - 6, cx + 6, hz - 2, hull)
        F.area(t, cx - 3, hz - 9, cx + 3, hz - 6, hull)
        F.area(t, cx - 1, hz - 13, cx, hz - 9, hull)
        F.area(t, cx + 8, hz - 5, cx + 11, hz - 2, hull)
        F.disc(t, 50, 18, 4, (250, 220, 150))
    if kind == "c":
        for jx, jy in ((16, 16), (24, 20)):
            F.area(t, jx, jy, jx + 7, jy + 1, (50, 54, 60))
            F.area(t, jx + 2, jy - 2, jx + 4, jy + 3, (50, 54, 60))
    gilt = (176, 140, 70)
    F.area(t, x0 - 4, y0 - 4, x1 + 4, y0, gilt, h=1.0, smooth=0.6, f0=200)
    F.area(t, x0 - 4, y1, x1 + 4, y1 + 4, gilt, h=1.0, smooth=0.6, f0=200)
    F.area(t, x0 - 4, y0, x0, y1, gilt, h=1.0, smooth=0.6, f0=200)
    F.area(t, x1, y0, x1 + 4, y1, gilt, h=1.0, smooth=0.6, f0=200)
    F.edges(t, x0 - 4, y0 - 4, x1 + 4, y1 + 4, light=1.25, dark=0.6)
    F.edges(t, x0 - 1, y0 - 1, x1 + 1, y1 + 1, light=0.7, dark=1.2)
    return F.wear(t, 0.2, 0)


def brass(seed):
    t = F.plain(seed, (184, 148, 78), 0.05, 0.8)
    t.f0[:] = 220
    return F.wear(t, 0.2, 0, specks=0.004)


def shade_lit(seed):
    """A cream shade with the bulb on behind it: a warm glow, brightest in the middle."""
    t = F.lampshade(seed)
    t.c = np.clip(t.c * 1.15 + np.array((30, 22, 6), np.float32), 0, 255)
    yy = np.arange(N)[:, None] / N
    t.emit[:] = (0.6 + 0.4 * np.sin(np.pi * yy)) * np.ones((1, N))
    return t


def stall(seed):
    """The officers' head's stall panels: orange laminate, a stainless cap, kicked and scratched."""
    t = F.plain(seed, (206, 86, 40), 0.03, 0.55)
    F.area(t, 0, 0, N, 2, (192, 194, 194), f0=170, smooth=0.7, dh=0.2)
    rng = t.rng
    for _ in range(6):
        x, y = int(rng.integers(4, 56)), int(rng.integers(10, 56))
        F.area(t, x, y, x + int(rng.integers(3, 9)), y + 1, (230, 180, 150))
    return F.wear(t, 0.2, 8, specks=0.003)


def stall_door(seed, top):
    """A stall door: the same orange, a chrome turn latch and an OCCUPIED slot, a coat hook up top."""
    t = stall(seed)
    if top:
        F.area(t, 6, 10, 10, 14, (200, 202, 204), dh=0.3, f0=180)
    else:
        F.area(t, 50, 2, 58, 8, (200, 202, 204), dh=0.3, f0=180)
        F.area(t, 52, 3, 56, 5, (40, 140, 60))
        F.area(t, 0, 56, N, N, mul=0.8)
    return t


def black_top(seed, sink=False):
    """The head's black counter, glossy and water-spotted; with sink, a round stainless bowl let into it."""
    t = F.plain(seed, (26, 26, 28), 0.03, 0.85)
    t.f0[:] = 40
    if sink:
        F.disc(t, 32, 30, 20, (178, 180, 180), f0=170, smooth=0.75)
        F.disc(t, 32, 30, 18, (150, 152, 152), dh=-0.3)
        F.disc(t, 32, 30, 2, (40, 40, 40), dh=-0.2)
        F.disc(t, 32, 30, 20, mul=0.7, ring=1)
    t.speckle(0.01, (150, 150, 150), 0.4)
    return F.wear(t, 0.15, 3)


def blanket_dark(seed):
    """A charcoal wool blanket, the weave showing, pilled and a little dusty."""
    t = F.plain(seed, (48, 50, 56), 0.08, 0.08)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.93 + 0.07 * ((xx // 2 + yy // 2) % 2))[..., None]
    t.speckle(0.04, (90, 92, 98), 0.4)
    F.area(t, 0, 4, N, 6, (70, 72, 80))
    return F.wear(t, 0.25, 0, specks=0.01)


def chessboard(seed):
    """A chess board let into a dark table: maple and walnut squares in a walnut border."""
    t = F.wood(seed, "dark")
    for i in range(8):
        for j in range(8):
            F.area(t, 8 + i * 6, 8 + j * 6, 14 + i * 6, 14 + j * 6, (214, 186, 140) if (i + j) % 2 else (96, 50, 32),
                   smooth=0.6)
    F.edges(t, 7, 7, 57, 57, light=1.2, dark=0.6)
    return F.wear(t, 0.15, 4)


def cabin_door(seed, top):
    """A stateroom door: grained laminate; a black name plate with the compartment number, a card holder
    above it; at the bottom a louvre and a stainless kick plate, the lever handle at hand height."""
    t = laminate(seed, (164, 124, 82))
    if top:
        F.area(t, 18, 20, 46, 30, (24, 24, 26), dh=0.2, smooth=0.7)
        m = B.fit_text(CABINS[seed % len(CABINS)], BOLD, 7, 28, 10, seed)
        F.paste(t, (m > 0.45).astype(np.float32), 18, 20, (230, 230, 226))
        F.area(t, 22, 34, 42, 44, (226, 222, 210), dh=0.1)
        F.edges(t, 21, 33, 43, 45, light=1.2, dark=0.7)
    else:
        F.area(t, 50, 4, 60, 7, (196, 198, 198), dh=0.4, f0=180)
        F.disc(t, 52, 5.5, 2.2, (196, 198, 198), dh=0.4, f0=180)
        for y in range(34, 50, 3):
            F.area(t, 16, y, 48, y + 2, (40, 34, 28), dh=-0.2)
        F.area(t, 0, 54, N, N, (184, 186, 186), f0=170, smooth=0.6, dh=0.1)
    return t


def drawers_lam(seed, rows=2):
    """Built-in drawers in the laminate: the gaps, a recessed finger pull on each."""
    t = laminate(seed, (170, 132, 88))
    h = N // rows
    for r in range(rows):
        F.gap(t, 2, r * h + 1, 62, (r + 1) * h - 1)
        F.area(t, 26, r * h + 6, 38, r * h + 9, (40, 34, 28), dh=-0.3)
    return t


def wardrobe_door(seed):
    """A tall wardrobe locker in the laminate: the door's gap, vents up top, a chrome pull, a combination lock."""
    t = laminate(seed, (170, 132, 88))
    F.gap(t, 2, 1, 62, 63)
    for y in range(6, 16, 3):
        F.area(t, 14, y, 50, y + 1, (40, 34, 28), dh=-0.15)
    F.area(t, 52, 28, 55, 44, (196, 198, 198), dh=0.3, f0=180)
    F.disc(t, 53.5, 50, 3, (40, 40, 42), dh=0.3, smooth=0.8)
    return t


def mirror_cab(seed):
    """A medicine cabinet's mirror door in a stainless frame, a toothpaste smear, the silver going at the edge."""
    t = F.mirror(seed)
    F.area(t, 0, 0, N, 3, (190, 192, 192), f0=180, dh=0.2)
    F.area(t, 0, 61, N, N, (190, 192, 192), f0=180, dh=0.2)
    F.area(t, 0, 0, 3, N, (190, 192, 192), f0=180, dh=0.2)
    F.area(t, 61, 0, N, N, (190, 192, 192), f0=180, dh=0.2)
    F.area(t, 3, 52, 30, 61, mul=0.7)
    return t


def steam_front(seed):
    """The serving line's front: stainless, the louvres of the wells' heaters, a row of control knobs."""
    t = stainless(seed)
    for x in range(6, 58, 13):
        F.disc(t, x + 3, 10, 2.5, (40, 40, 40), dh=0.3)
    for y in range(26, 50, 4):
        F.area(t, 8, y, 56, y + 2, (60, 62, 62), dh=-0.15)
    F.area(t, 0, 58, N, N, mul=0.75)
    return t


def pans(seed):
    """The serving line's wells from above: three stainless pans, what was left in them dried black and green."""
    t = stainless(seed)
    rng = t.rng
    for i, x in enumerate((3, 23, 43)):
        F.area(t, x, 6, x + 18, 58, (120, 122, 122), dh=-0.3)
        F.area(t, x + 2, 8, x + 16, 56, ((70, 50, 30), (60, 70, 40), (90, 70, 40))[i], dh=-0.2, smooth=0.3)
        t.speckle(0.02, (40, 90, 40), 0.5)
        F.edges(t, x, 6, x + 18, 58, light=1.2, dark=0.6)
    return F.wear(t, 0.3, 0)


def crest(seed):
    """The wardroom's plaque: a navy disc in a gilt rim, a fouled anchor and stars, WARDROOM round the top."""
    t = F.plain(seed, (40, 32, 24), 0.05, 0.3)
    F.disc(t, 32, 32, 30, (176, 140, 70), f0=200, smooth=0.6, dh=0.3)
    F.disc(t, 32, 32, 26, (28, 36, 72), smooth=0.5)
    gold = (210, 172, 90)
    F.area(t, 31, 16, 33, 46, gold, dh=0.3)
    F.area(t, 24, 20, 40, 22, gold, dh=0.3)
    for k in range(9):
        F.area(t, 22 + k, 44 - abs(k - 4) // 2, 23 + k, 46 - abs(k - 4) // 2, gold)
        F.area(t, 33 + k, 42 + k // 3, 34 + k, 44 + k // 3, gold)
        F.area(t, 31 - k, 42 + k // 3, 32 - k, 44 + k // 3, gold)
    F.disc(t, 32, 14, 2.5, gold, ring=1)
    for sx in (18, 46):
        F.disc(t, sx, 30, 2, (230, 230, 230))
    return F.wear(t, 0.15, 0)


def mesh_bag(seed):
    """A laundry bag: navy mesh, the clothes showing through, its drawstring."""
    t = F.plain(seed, (60, 64, 80), 0.1, 0.1)
    t.mix(np.clip(P.fbm(t.rng) + 0.3, 0, 1) * 0.5, (150, 140, 110), 1.0)
    yy, xx = np.mgrid[0:N, 0:N]
    grid = ((xx % 4) == 0) | ((yy % 4) == 0)
    t.c[grid] = np.array((34, 38, 56), np.float32)
    return F.wear(t, 0.3, 0)


def desk_papers(seed):
    """A desk flap's top: the blotter, a binder, a stack of message traffic, a coffee ring."""
    t = laminate(seed, (164, 124, 82))
    F.area(t, 6, 6, 44, 34, (40, 70, 60), smooth=0.2)
    F.area(t, 10, 10, 30, 30, (232, 230, 220))
    for y in range(13, 28, 3):
        F.area(t, 12, y, 28, y + 1, (90, 90, 96))
    F.area(t, 46, 8, 60, 36, (30, 50, 110), dh=0.2)
    F.disc(t, 50, 48, 5, mul=0.7, ring=1)
    return F.wear(t, 0.2, 3)


def laundry_light(seed, on):
    """A berth's reading light: a small frosted lens."""
    return lens(seed, (250, 226, 170), on)


SIGNS = {
    "fire": ("FIRE STATION", (176, 34, 28)), "repair": ("REPAIR|LOCKER", (176, 34, 28)),
    "escape": ("ESCAPE|TRUNK", (226, 222, 210)), "hearing": ("HEARING|PROTECTION|REQUIRED", (218, 178, 38)),
    "nosmoking": ("NO|SMOKING", (176, 34, 28)), "keepclosed": ("WATERTIGHT|KEEP CLOSED", (226, 222, 210)),
    "ammo": ("AMMUNITION|HANDLING", (176, 34, 28)), "oxygen": ("OXYGEN|NO OIL", (52, 128, 70)),
    "head": ("HEAD", (226, 222, 210)), "laundry": ("LAUNDRY", (226, 222, 210)),
    "chapel": ("CHAPEL", (226, 222, 210)), "store": ("SHIP'S|STORE", (226, 222, 210)),
    "wardroom": ("WARDROOM", (226, 222, 210)), "chiefs": ("CHIEFS|MESS", (226, 222, 210)),
    "aimd": ("AVIONICS|SHOP", (226, 222, 210)), "post": ("POST|OFFICE", (226, 222, 210)),
}
CABINS = ("03-118-2-L", "03-122-4-L", "03-126-6-L", "03-130-2-L", "03-134-4-L", "03-138-6-L")
LABELS = ("2-96-0-L", "2-118-2-L", "2-150-1-Q", "3-44-0-L", "3-132-2-M", "3-176-1-L", "4-60-0-M", "4-212-2-Q",
          "1-140-0-A", "1-188-1-A", "01-72-0-L", "02-120-1-C", "03-150-0-C", "07-160-0-C", "2-250-0-E", "3-290-1-A")


def textures():
    T = {
        "fit_yellow": lambda: yellow(8000),
        "fit_lens_on": lambda: lens(8001, (246, 236, 206), True), "fit_lens_off": lambda: lens(8002, (214, 208, 190), False),
        "fit_dome": lambda: dome(8003), "fit_exit": lambda: exit_sign(8004), "fit_hose": lambda: hose(8005),
        "fit_dc_locker": lambda: locker(8006, "REPAIR 2|DC LOCKER"), "fit_eebd": lambda: canister(8007),
        "fit_grille": lambda: grille(8008), "fit_phone": lambda: phone(8009), "fit_jbox": lambda: jbox(8010),
        "fit_flood": lambda: flood_body(8011), "fit_grey": lambda: steel(8012, GREY, 0.4),
        "fit_red": lambda: steel(8013, RED, 0.4), "fit_frame": lambda: enamel_steel(8014, (210, 206, 192)),
        "fit_beam": lambda: enamel_steel(8015, (220, 216, 204)),
        "fit_dc_locker_b": lambda: locker(8016, "REPAIR 5|DC LOCKER"),
        "fit_gse": lambda: gse(8017), "fit_bottle": lambda: bottle(8018),
        "fit_tank": lambda: F.wear(F.enamel(8019, (176, 180, 178)), 0.25, 3),
        "fit_lagging": lambda: lagging(8020), "fit_line": lambda: deck_line(8022),
        "fit_beige": lambda: beige(8023), "fit_pan": lambda: pan_front(8024), "fit_locker_door": lambda: locker_door(8025),
        "fit_stainless": lambda: stainless(8026), "fit_oven": lambda: oven_front(8027),
        "fit_dispenser": lambda: dispenser_front(8028), "fit_cabinet": lambda: cabinet_front(8029),
        "fit_litter": lambda: litter(8030), "fit_vinyl": lambda: vinyl(8031), "fit_sodium_on": lambda: lens(8021, (255, 170, 72), True),
        "fit_gauges": lambda: gauges(8100), "fit_throttle": lambda: throttle(8101), "fit_board": lambda: board(8102),
        "fit_wheel_yellow": lambda: wheel_tex(8103, YELLOW), "fit_wheel_steel": lambda: wheel_tex(8104, (78, 80, 80)),
        "fit_wheel_red": lambda: wheel_tex(8105, RED), "fit_bronze": lambda: bronze(8106), "fit_plate": lambda: plate(8107),
        "fit_burner": lambda: burner(8108), "fit_console": lambda: console_face(8109), "fit_console_top": lambda: console_top(8110),
        "fit_screen_on": lambda: screen(8111, True), "fit_screen_off": lambda: screen(8112, False),
        "fit_louvre": lambda: louvre(8116), "fit_hatch": lambda: hatch(8117),
        "fit_chart": lambda: chart(8113), "fit_drawers": lambda: drawers(8114), "fit_leather": lambda: vinyl(8115, (64, 44, 32)),
        "fit_laminate": lambda: laminate(8200), "fit_cherry": lambda: cherry(8201), "fit_ceiling": lambda: ceiling_tile(8202),
        "fit_troffer_on": lambda: troffer(8203, True), "fit_troffer_off": lambda: troffer(8204, False),
        "fit_tile_cream": lambda: tile_cream(8205), "fit_carpet": lambda: carpet(8206), "fit_linen": lambda: linen(8207),
        "fit_linen_soiled": lambda: linen(8208, True), "fit_stars": lambda: star_cloth(8209),
        "fit_paint_a": lambda: ship_painting(8210, "a"), "fit_paint_b": lambda: ship_painting(8211, "b"),
        "fit_paint_c": lambda: ship_painting(8212, "c"), "fit_brass": lambda: brass(8213), "fit_shade_on": lambda: shade_lit(8214),
        "fit_stall": lambda: stall(8215), "fit_stall_door_top": lambda: stall_door(8216, True),
        "fit_stall_door_bottom": lambda: stall_door(8217, False), "fit_black": lambda: black_top(8218),
        "fit_black_sink": lambda: black_top(8219, True), "fit_blanket": lambda: blanket_dark(8220),
        "fit_chessboard": lambda: chessboard(8221), "fit_cabin_door_top": lambda: cabin_door(8222, True),
        "fit_cabin_door_bottom": lambda: cabin_door(8223, False), "fit_drawers_lam": lambda: drawers_lam(8224),
        "fit_wardrobe": lambda: wardrobe_door(8225), "fit_mirror_cab": lambda: mirror_cab(8226),
        "fit_steam_front": lambda: steam_front(8227), "fit_pans": lambda: pans(8228), "fit_crest": lambda: crest(8229),
        "fit_mesh_bag": lambda: mesh_bag(8230), "fit_desk_papers": lambda: desk_papers(8231),
        "fit_reading_on": lambda: lens(8232, (250, 226, 170), True), "fit_reading_off": lambda: lens(8233, (214, 208, 190), False),
        "fit_drawers_lam3": lambda: drawers_lam(8234, 3), "fit_wall_cream": lambda: wall_cream(8235),
        "fit_wainscot": lambda: wainscot(8236),
    }
    for i, (k, (text, col)) in enumerate(SIGNS.items()):
        T[f"fit_st_{k}"] = (lambda a, b, c: lambda: B.stencil_sign(a, b, c, 12))(8040 + i, text, col)
    for i, text in enumerate(LABELS):
        T[f"fit_pl_{i}"] = (lambda a, b: lambda: label(a, b))(8070 + i, text)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound="METAL", rotate="facing", decor=True, light=0, loot=None):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        if loot:
            spec["loot"] = loot
        out[name] = spec

    def flicker(name, swap, light):
        out[name]["flicker"] = {"light": light, "on": swap}
        out[name].pop("light", None)

    def lamp(name, boxes, title, rotate, light, glass, lit, dead, flick=None):
        """name_on (lit), name (dead) and, with flick, name_flicker on a dying circuit."""
        on = [b[:6] + [lit if b[6] == glass else b[6]] for b in boxes]
        off = [b[:6] + [dead if b[6] == glass else b[6]] for b in boxes]
        add(name + "_on", on, title, rotate=rotate, light=light)
        add(name, off, title, rotate=rotate)
        if flick:
            add(name + "_flicker", off, title, rotate=rotate)
            flicker(name + "_flicker", {dead: lit}, flick)

    # ---- the lights
    G = "GLASS"
    # a two-tube fitting a metre long under the deckhead, end caps and three guard straps across the tubes
    lamp("fit_tube", [[4.5, 14.5, 0.5, 11.5, 16, 15.5, "bunk_fitting"], [5.3, 13, 1.5, 7.5, 14.5, 14.5, G],
                      [8.5, 13, 1.5, 10.7, 14.5, 14.5, G], [4.5, 12.8, 0.5, 11.5, 14.5, 1.5, "bunk_fitting"],
                      [4.5, 12.8, 14.5, 11.5, 14.5, 15.5, "bunk_fitting"]]
         + [[4.8, 12.5, z, 11.2, 12.9, z + 0.5, "bunk_grey"] for z in (4.5, 7.75, 11)],
         "Fluorescent Light", "axis", 14, G, "bunk_tube_on", "bunk_tube_off", 12)
    lamp("fit_red", [[6, 14.5, 5, 10, 16, 11, "bunk_grey"], [6.5, 13.2, 5.5, 9.5, 14.5, 10.5, G],
                     [6.2, 12.8, 5.2, 9.8, 14.6, 10.8, "bunk_cage_wire"]],
         "Night Light", None, 6, G, "bunk_red_on", "bunk_red_off")
    lamp("fit_lantern", [[4, 7, 10, 12, 13, 16, "fit_yellow"], [5, 8, 9.5, 11, 12, 10, G],
                         [6, 13, 12, 10, 14.5, 14, "bunk_grey"], [5, 6, 14, 11, 7, 16, "bunk_grey"]],
         "Battle Lantern", "facing", 11, G, "fit_lens_on", "fit_lens_off")
    lamp("fit_globe", [[6, 7, 15, 10, 13, 16, "bunk_grey"], [6.5, 8, 11, 9.5, 12, 15, G],
                       [6, 7.5, 10.5, 10, 12.5, 15.5, "bunk_cage_wire"]],
         "Caged Light", "facing", 12, G, "bunk_glass_on", "bunk_glass_off", 10)
    lamp("fit_globe_down", [[6, 15, 6, 10, 16, 10, "bunk_grey"], [6.5, 11, 6.5, 9.5, 15, 9.5, G],
                            [6, 10.5, 6, 10, 15, 10, "bunk_cage_wire"]],
         "Caged Light", None, 12, G, "bunk_glass_on", "bunk_glass_off", 10)
    # a dome lamp on a stem: the shade stepped out in three rings, the lamp glass under it
    lamp("fit_highbay", [[7.5, 10.5, 7.5, 8.5, 16, 8.5, "bunk_grey"], [6, 9.5, 6, 10, 10.5, 10, "fit_dome"],
                         [4.5, 8, 4.5, 11.5, 9.5, 11.5, "fit_dome"], [3, 6.5, 3, 13, 8, 13, "fit_dome"],
                         [4, 6, 4, 12, 6.5, 12, G]],
         "Hangar Lamp", None, 15, G, "fit_sodium_on", "bunk_glass_off", 13)
    lamp("fit_flood", [[7, 0, 13, 9, 9, 16, "fit_grey"], [3, 6, 5, 13, 14, 12, "fit_flood"],
                       [4, 7, 4.5, 12, 13, 5, G], [2.5, 5.5, 4, 3, 14.5, 5, "fit_grey"],
                       [13, 5.5, 4, 13.5, 14.5, 5, "fit_grey"]],
         "Floodlight", "facing", 15, G, "fit_lens_on", "fit_lens_off")
    add("fit_exit", [[2, 10, 15.4, 14, 14, 16, {"north": "fit_exit", "side": "fit_grey", "up": "fit_grey",
                                                "down": "fit_grey"}]], "Escape Sign", light=3)

    # ---- the overheads
    hang = [[0, 15.5, 7.5, 16, 16, 8.5, "bunk_grey"]]
    add("fit_pipes_a", [[0.5, 11, 0, 5, 15.5, 16, "fit_lagging"], [5, 13, 0, 7.5, 15.5, 16, "bunk_pipe_green"],
                        [9, 11.5, 0, 13.5, 16, 16, "bunk_pipe_grey"]] + hang, "Pipes", rotate="axis")
    add("fit_pipes_b", [[1.5, 11, 0, 6.5, 16, 16, "bunk_pipe_red"], [8, 13, 0, 10.5, 15.5, 16, "bunk_pipe_blue"],
                        [12, 13.5, 0, 14, 15.5, 16, "bunk_pipe_grey"]] + hang, "Firemain", rotate="axis")
    add("fit_pipes_valve", [[1.5, 11, 0, 6.5, 16, 16, "bunk_pipe_red"], [8, 13, 0, 10.5, 15.5, 16, "bunk_pipe_blue"],
                            [2.5, 10, 6, 5.5, 11, 10, "bunk_pipe_grey"], [0, 6, 4, 8, 6.6, 12, "bunk_wheel"],
                            [3.5, 6.6, 7.5, 4.5, 10, 8.5, "bunk_grey"]] + hang, "Firemain Valve", rotate="axis")
    add("fit_tray", [[2, 14, 0, 14, 14.5, 16, "ext_galv"], [2, 14.5, 0, 2.5, 16, 16, "ext_galv"],
                     [13.5, 14.5, 0, 14, 16, 16, "ext_galv"], [3, 14.5, 0, 13, 15.6, 16, "bunk_cables"],
                     [6, 13.2, 3, 7, 14, 16, "bunk_cables"]], "Cable Tray", rotate="axis")
    add("fit_duct", [[2, 11, 0, 14, 16, 16, "ext_galv"], [1.5, 10.5, 7, 14.5, 16, 8, "ext_galv"]], "Vent Trunk",
        rotate="axis")
    add("fit_duct_grille", [[2, 11, 0, 14, 16, 16, "ext_galv"], [4, 10.9, 4, 12, 11, 12, "bunk_grille"]], "Vent Trunk",
        rotate="axis")
    add("fit_sprinkler", [[7, 14.5, 0, 9, 16, 16, "bunk_pipe_red"], [7.5, 13.5, 7.5, 8.5, 14.5, 8.5, "bunk_grey"],
                          [6.5, 13, 6.5, 9.5, 13.5, 9.5, "bunk_grey"]], "Sprinkler Line", rotate="axis")
    # a deep T-section beam across the run (it takes the axis of the run it crosses), and the same beam
    # with fit_pipes_b's pipes passing through its web
    beam = [[0, 9, 7.25, 16, 16, 8.75, "fit_beam"], [0, 8.5, 5, 16, 9, 11, "fit_beam"]]
    add("fit_beam", beam, "Deck Beam", rotate="axis")
    # the hangar's deep girder: an I-section a metre deep across the run it takes the axis of
    add("fit_girder", [[0, 0, 3.5, 16, 1.5, 12.5, "fit_beam"], [0, 1.5, 7, 16, 14.5, 9, "fit_beam"],
                       [0, 14.5, 3.5, 16, 16, 12.5, "fit_beam"]]
        + [[x, 1.5, 6.5, x + 0.5, 14.5, 9.5, "fit_beam"] for x in (3.75, 11.75)], "Girder", rotate="axis")
    for run in ("pipes_a", "pipes_b", "tray", "duct", "sprinkler"):
        name = "fit_beam_pipes" if run == "pipes_b" else f"fit_beam_{run}"
        add(name, beam + [b for b in out[f"fit_{run}"]["boxes"] if b[1] < 15.5 or b[0] > 0], "Deck Beam", rotate="axis")

    # ---- on the bulkheads
    add("fit_frame", [[6.5, 0, 14, 9.5, 16, 16, "fit_frame"], [5, 0, 13, 11, 16, 14, "fit_frame"]], "Frame")
    add("fit_valve", [[6.5, 0, 13, 9.5, 16, 16, "bunk_pipe_red"], [4, 5, 11.5, 12, 13, 12, "bunk_wheel"],
                      [7.5, 8.5, 12, 8.5, 9.5, 13, "bunk_grey"], [6, 9, 12.5, 10, 9.5, 16, "bunk_grey"]], "Valve")
    add("fit_pipe_drop", [[2, 0, 13.5, 4.5, 16, 16, "bunk_pipe_blue"], [6, 0, 14, 8, 16, 16, "bunk_pipe_grey"],
                          [1.5, 7, 13, 8.5, 8, 16, "bunk_grey"]], "Pipes")
    add("fit_jbox", [[4, 6, 14, 12, 12, 16, {"north": "fit_jbox", "side": "fit_grey", "up": "fit_grey", "down": "fit_grey"}],
                     [7.5, 12, 14.5, 8.5, 16, 15.5, "bunk_grey"], [7.5, 0, 14.5, 8.5, 6, 15.5, "bunk_grey"]],
        "Junction Box")
    add("fit_speaker", [[4, 11, 13, 12, 15, 16, {"north": "fit_grille", "side": "fit_grey", "up": "fit_grey",
                                                  "down": "fit_grey"}], [7, 15, 14, 9, 16, 16, "bunk_grey"]],
        "Loudspeaker")
    add("fit_phone", [[4, 6, 13, 12, 13, 16, {"north": "fit_phone", "side": "fit_grey", "up": "fit_grey",
                                               "down": "fit_grey"}], [7.5, 13, 14.5, 8.5, 16, 15.5, "bunk_grey"]],
        "Sound-Powered Phone")
    add("fit_firestation", [[2, 9, 14, 14, 10, 16, "fit_grey"], [3, 3, 11, 13, 9, 15, "fit_hose"],
                            [3.5, 9, 11.5, 12.5, 11, 14.5, "fit_hose"], [12.5, 1, 12, 14, 7, 13.5, "bunk_grey"],
                            [6, 12, 12, 10, 14, 16, "bunk_pipe_red"], [5, 14, 13, 11, 14.6, 15, "bunk_wheel"]],
        "Fire Station")
    add("fit_dc_locker", [[1, 0, 4, 15, 16, 16, {"north": "fit_dc_locker", "side": "fit_grey", "up": "fit_grey",
                                                  "down": "fit_grey"}]], "Repair Locker", decor=False, loot="home_tools")
    add("fit_dc_locker_b", [[1, 0, 4, 15, 16, 16, {"north": "fit_dc_locker_b", "side": "fit_grey", "up": "fit_grey",
                                                    "down": "fit_grey"}]], "Repair Locker", decor=False,
        loot="home_tools")
    add("fit_eebd", [[2, 5, 12, 14, 6, 16, "fit_grey"]] +
        [[2.5 + i * 4, 6, 12.5, 5.5 + i * 4, 13, 15.5, "fit_eebd"] for i in range(3)], "Escape Breathing Sets")

    # ---- hangar gear: the yellow support equipment, fire bottles, stands, tyres, drop tanks
    wheels = lambda y=3.5: [[0.5, 0, 1.5, 2.5, y, 5, "navy_tyre"], [13.5, 0, 1.5, 15.5, y, 5, "navy_tyre"],
                            [0.5, 0, 11, 2.5, y, 14.5, "navy_tyre"], [13.5, 0, 11, 15.5, y, 14.5, "navy_tyre"]]
    add("fit_gse_power", [[1.5, 2, 1, 14.5, 12, 15, {"north": "fit_gse", "south": "fit_gse", "side": "fit_yellow",
                                                       "up": "fit_yellow", "down": "fit_grey"}],
                          [5, 12, 4, 11, 15, 12, "home_rubber"], [4.5, 12, 7.5, 11.5, 15.5, 8.5, "fit_grey"],
                          [7, 2, -1, 9, 3, 1, "fit_grey"]] + wheels(), "Power Cart", decor=False)
    add("fit_gse_air", [[1.5, 2, 1, 14.5, 9, 15, {"north": "fit_gse", "south": "fit_yellow", "side": "fit_gse",
                                                     "up": "fit_yellow", "down": "fit_grey"}],
                        [3, 9, 3, 13, 13, 13, "fit_yellow"], [6, 13, 6, 10, 14, 10, "fit_grey"],
                        [7, 4, -1, 9, 6, 1, "home_rubber"]] + wheels(), "Start Cart", decor=False)
    add("fit_firebottle", [[5, 2, 5, 11, 14, 11, "fit_bottle"], [6.5, 14, 6.5, 9.5, 15.5, 9.5, "fit_grey"],
                           [3, 0, 6, 5, 4, 10, "navy_tyre"], [11, 0, 6, 13, 4, 10, "navy_tyre"],
                           [4, 1.5, 7.5, 12, 2.5, 8.5, "fit_grey"], [5, 12, 11, 11, 13, 14, "fit_grey"],
                           [4.5, 4, 11, 11.5, 9, 13, "home_rubber"]], "Fire Bottle", decor=False)
    add("fit_stand", [[1, 0, 1, 15, 4, 16, "fit_yellow"], [1, 4, 6, 15, 8, 16, "fit_yellow"],
                      [1, 8, 11, 15, 12, 16, "fit_yellow"], [1, 12, 0, 2, 20, 1, "fit_grey"], [14, 12, 0, 15, 20, 1, "fit_grey"],
                      [1, 19, 0, 2, 20, 16, "fit_grey"], [14, 19, 0, 15, 20, 16, "fit_grey"]], "Work Stand", decor=False)
    add("fit_towbar", [[7, 1, 0, 9, 2.5, 16, "fit_yellow"], [5, 0, 10, 7, 3, 14, "navy_tyre"], [9, 0, 10, 11, 3, 14, "navy_tyre"],
                       [6, 0.5, 0, 10, 3, 1.5, "fit_grey"]], "Tow Bar")
    add("fit_chain_rack", [[1, 12, 13, 15, 13, 16, "fit_grey"]]
        + [[x, 2, 14.5, x + 1, 12, 15.5, "vessel_chain_h"] for x in (2.5, 5.5, 8.5, 11.5)], "Chain Rack")
    add("fit_fod", [[4, 0, 4, 12, 10, 12, "fit_yellow"], [3.5, 10, 3.5, 12.5, 11, 12.5, "fit_grey"]], "FOD Can",
        rotate=None, decor=False)
    add("fit_tyres", [[3, 0, 3, 13, 4, 13, "navy_tyre"], [3, 4.2, 3, 13, 8.2, 13, "navy_tyre"],
                      [3.5, 8.4, 3.5, 12.5, 12.4, 12.5, "navy_tyre"], [6, 12.4, 6, 10, 12.6, 10, "fit_grey"]], "Spare Tyres",
        rotate=None, decor=False)
    add("fit_droptank", [[4, 4, 0, 12, 12, 16, "fit_tank"], [2, 0, 3, 14, 4.5, 5, "fit_yellow"],
                         [2, 0, 11, 14, 4.5, 13, "fit_yellow"]], "Drop Tank", rotate="axis", decor=False)
    add("fit_droptank_nose", [[5, 5, 0, 11, 11, 6, "fit_tank"], [4, 4, 6, 12, 12, 16, "fit_tank"],
                              [2, 0, 11, 14, 4.5, 13, "fit_yellow"]], "Drop Tank", rotate="axis", decor=False)

    # ---- berthing: racks three high in beige pans (two blocks tall, two long: a has its end panel at
    # x 0, b at x 16), the tall lockers between them
    def rack(end, lo):
        e = [[0, 0, 0, 1, 16, 14, "fit_beige"]] if end == "a" else [[15, 0, 0, 16, 16, 14, "fit_beige"]]
        pan = lambda y0, y1: [0, y0, 1, 16, y1, 14, {"north": "fit_pan", "side": "fit_beige", "up": "fit_beige",
                                                       "down": "fit_beige"}]
        back = [[0, 0, 14, 16, 16, 16, "fit_beige"]]
        if lo:
            body = [pan(1, 4.5), [0.5, 4.5, 1.5, 15.5, 6, 13.5, "navy_mattress"], pan(13, 16),
                    [6 if end == "a" else 1, 6, 0.5, 15 if end == "a" else 10, 13, 1, "navy_curtain_blue"],
                    [0, 0, 1, 16, 1, 2, "fit_beige"]]
        else:
            body = [[0.5, 0, 1.5, 15.5, 1.5, 13.5, "navy_mattress"], pan(8, 11), [0.5, 11, 1.5, 15.5, 12.5, 13.5, "navy_mattress"],
                    [0, 15, 0, 16, 16, 14, "fit_beige"], [3, 14.2, 11, 13, 15, 13, "navy_tube_on"],
                    [1 if end == "a" else 9, 1.5, 0.5, 7 if end == "a" else 15, 8, 1, "navy_curtain_blue"]]
        return back + e + body

    for end in ("a", "b"):
        add(f"fit_rack_lo_{end}", rack(end, True), "Berthing Rack", decor=False, loot="home_wardrobe")
        add(f"fit_rack_hi_{end}", rack(end, False), "Berthing Rack", decor=False, loot="home_nightstand")
    door = {"north": "fit_locker_door", "side": "fit_beige", "up": "fit_beige", "down": "fit_beige"}
    add("fit_locker_lo", [[1, 0, 2, 15, 16, 16, door]], "Locker", decor=False, loot="home_wardrobe")
    add("fit_locker_hi", [[1, 0, 2, 15, 15, 16, door], [0.5, 15, 1.5, 15.5, 16, 16, "fit_beige"]], "Locker",
        decor=False)

    # ---- the mess decks and the galley
    add("fit_mess_table", [[1.5, 10, 0, 14.5, 11, 16, "navy_formica"], [1.5, 9.5, 0, 14.5, 10, 16, "fit_stainless"],
                           [7, 0, 7, 9, 9.5, 9, "fit_stainless"], [5, 0, 5, 11, 0.5, 11, "fit_stainless"]],
        "Mess Table", rotate="axis", decor=False)
    stool = lambda x, z: [[x + 1.25, 0, z + 1.25, x + 2.75, 6, z + 2.75, "fit_stainless"],
                          [x, 6, z, x + 4, 7.5, z + 4, "fit_vinyl"]]
    add("fit_stools", stool(2, 1) + stool(10, 1), "Mess Stools")
    add("fit_counter_steel", [[0, 13, 0, 16, 14.5, 16, "fit_stainless"], [0, 3, 1, 16, 4, 15, "fit_stainless"],
                              [0.5, 0, 0.5, 2, 13, 2, "fit_stainless"], [14, 0, 0.5, 15.5, 13, 2, "fit_stainless"],
                              [0.5, 0, 14, 2, 13, 15.5, "fit_stainless"], [14, 0, 14, 15.5, 13, 15.5, "fit_stainless"],
                              [2, 4, 3, 14, 7, 13, "bunk_tins"]], "Steel Counter", decor=False)
    add("fit_oven", [[0.5, 0, 1, 15.5, 16, 15, {"north": "fit_oven", "side": "fit_stainless", "up": "fit_stainless",
                                                 "down": "fit_stainless"}]], "Deck Oven", decor=False)
    add("fit_kettle", [[3, 5, 3, 13, 13, 13, "fit_stainless"], [2.5, 12.5, 2.5, 13.5, 14, 13.5, "fit_stainless"],
                       [4, 0, 4, 5, 5, 5, "fit_stainless"], [11, 0, 4, 12, 5, 5, "fit_stainless"],
                       [4, 0, 11, 5, 5, 12, "fit_stainless"], [11, 0, 11, 12, 5, 12, "fit_stainless"],
                       [7, 8, -1, 9, 9, 3, "fit_grey"], [6, 4, 13, 10, 12, 15, "fit_grey"]], "Steam Kettle", decor=False)
    add("fit_dispenser", [[2, 0, 4, 14, 11, 14, {"north": "fit_dispenser", "side": "fit_stainless", "up": "fit_stainless",
                                                    "down": "fit_stainless"}], [3, 0, 1, 13, 1, 4, "fit_grey"]],
        "Drinks Dispenser", decor=False)

    # ---- the sick bay
    lamp("fit_surgical", [[7.5, 10, 7.5, 8.5, 16, 8.5, "fit_grey"], [3, 7, 3, 13, 9.5, 13, "fit_stainless"],
                          [4, 6.5, 4, 12, 7, 12, G]], "Operating Lamp", None, 15, G, "bunk_glass_on", "bunk_glass_off")
    add("fit_litter", [[2, 0, 14.5, 14, 16, 15.5, "fit_litter"], [2, 0, 15.5, 14, 0.5, 16, "fit_grey"]], "Stokes Litter")
    add("fit_cabinet", [[1, 3, 9, 15, 15, 16, {"north": "fit_cabinet", "side": "fit_beige", "up": "fit_beige",
                                               "down": "fit_beige"}]], "Medical Cabinet")
    add("fit_fan", [[7, 8, 13, 9, 10, 16, "fit_grey"], [6, 7, 9, 10, 11, 13, "fit_grey"],
                    [3, 4, 7.5, 13, 14, 9, "bunk_cage_wire"]], "Wall Fan")

    FULL = {f: [0, 0, 16, 16] for f in ("north", "south", "east", "west", "up", "down")}  # a whole wheel on each face
    # ---- the island: consoles with their displays (lit, dead or failing), the high chairs, the chart table
    side = {"side": "bunk_grey", "down": "bunk_grey"}
    lamp("fit_console", [[0, 0, 4, 16, 10, 16, dict(side, north="fit_console", up="fit_console_top")],
                         [0, 10, 8, 16, 11.5, 16, dict(side, up="fit_console_top")],
                         [1, 11.5, 10, 15, 20, 14, "bunk_grey"], [2, 12.5, 9.6, 14, 19, 10, G]],
         "Bridge Console", "facing", 4, G, "fit_screen_on", "fit_screen_off", 4)
    lamp("fit_overhead", [[0, 9, 2, 16, 16, 16, dict(side, north="fit_console", down="fit_console", up="bunk_grey")],
                          [3, 5, 10, 13, 9, 13, "bunk_grey"], [4, 5.5, 9.6, 12, 8.5, 10, G]],
         "Overhead Console", "facing", 3, G, "fit_screen_on", "fit_screen_off", 3)
    add("fit_chair_high", [[4, 0, 4, 12, 0.5, 12, "fit_grey"], [7, 0.5, 7, 9, 11, 9, "fit_grey"],
                           [4, 5, 3, 12, 5.6, 4, "home_chrome"], [7.5, 5, 4, 8.5, 5.6, 7, "home_chrome"],
                           [3, 11, 3, 13, 13, 13, "fit_leather"], [3.5, 13, 12, 12.5, 22, 13.5, "fit_leather"],
                           [4.5, 22, 12, 11.5, 23, 13.5, "fit_leather"], [2, 15, 5, 3.5, 16, 12, "fit_leather"],
                           [12.5, 15, 5, 14, 16, 12, "fit_leather"], [2.5, 13, 10.5, 3, 15, 11.5, "fit_grey"],
                           [13, 13, 10.5, 13.5, 15, 11.5, "fit_grey"]],
        "Bridge Chair", decor=False)
    add("fit_chart_table", [[0, 14, 0, 16, 15, 16, {"up": "fit_chart", "side": "fit_grey", "down": "fit_grey"}],
                            [1, 0, 2, 15, 14, 16, {"north": "fit_drawers", "side": "fit_grey", "up": "fit_grey",
                                                   "down": "fit_grey"}],
                            [3, 15, 4, 11, 15.3, 5.5, "fit_stainless"], [13, 15, 13, 14, 21, 14, "fit_grey"],
                            [10.5, 20, 10, 14.5, 22, 14, "fit_grey"]], "Chart Table", decor=False, loot="office")
    add("fit_pelorus", [[6, 0, 6, 10, 11, 10, "fit_grey"], [4, 11, 4, 12, 13, 12, "fit_bronze"],
                        [5, 13, 5, 11, 13.5, 11, "bunk_glass_off"], [7.5, 13.5, 4, 8.5, 16, 5, "fit_bronze"],
                        [7.5, 13.5, 11, 8.5, 16, 12, "fit_bronze"]], "Gyro Repeater", rotate=None)

    # ---- the machinery spaces: gauge boards with their throttle wheels, fuel valves in yellow, bronze
    # valves on the low runs, lagged risers, boiler fronts, deck plate
    add("fit_gauges", [[0, 0, 13, 16, 16, 16, {"north": "fit_gauges", "side": "fit_board", "up": "fit_board",
                                                "down": "fit_board"}], [0, 15.5, 11, 16, 16, 13, "fit_board"]],
        "Gauge Board")
    add("fit_throttle", [[0, 0, 12, 16, 16, 16, {"north": "fit_throttle", "side": "fit_board", "up": "fit_board",
                                                  "down": "fit_board"}], [0, 9, 9, 16, 10, 12, "fit_board"],
                         [1, 1, 10.25, 9, 9, 10.25, "fit_wheel_steel", FULL], [4.5, 4.5, 10.5, 5.5, 5.5, 12, "bunk_grey"],
                         [10, 3, 10.25, 15, 8, 10.25, "fit_wheel_red", FULL], [12, 5, 10.5, 13, 6, 12, "bunk_grey"]],
        "Throttle Board")
    add("fit_fuel_valves", [[2, 0, 13.5, 4.5, 16, 16, "fit_yellow"], [11, 0, 13.5, 13.5, 16, 16, "fit_yellow"],
                            [2, 11, 14, 13.5, 13, 16, "fit_yellow"], [1.5, 4, 12, 5, 7, 16, "fit_bronze"],
                            [2.5, 4.5, 10, 4, 6.5, 12, "fit_bronze"], [0, 2, 9.75, 6.5, 8.5, 9.75, "fit_wheel_yellow", FULL],
                            [10.5, 7, 12, 14, 10, 16, "fit_bronze"], [11.5, 7.5, 10, 13, 9.5, 12, "fit_bronze"],
                            [9, 5.5, 9.75, 15.5, 12, 9.75, "fit_wheel_yellow", FULL]], "Fuel Valves")
    add("fit_valve_bronze", [[0, 2, 10, 16, 5, 13, "bunk_pipe_grey"], [5, 1, 9, 11, 6, 14, "fit_bronze"],
                             [6.5, 6, 10, 9.5, 11, 13, "fit_bronze"], [7.5, 11, 11, 8.5, 13, 12, "bunk_grey"],
                             [3.5, 13.25, 7, 12.5, 13.25, 16, "fit_wheel_red", FULL]], "Gate Valve")
    add("fit_riser", [[3, 0, 9, 13, 16, 16, "fit_lagging"], [2, 14, 8, 14, 16, 16, "bunk_grey"],
                      [2, 0, 8, 14, 2, 16, "bunk_grey"]], "Lagged Riser")
    add("fit_burner", [[0, 0, 4, 16, 16, 16, {"north": "fit_burner", "side": "bunk_grey", "up": "bunk_grey",
                                               "down": "bunk_grey"}], [2, 9, 2, 14, 10, 4, "fit_bronze"]],
        "Boiler Front", decor=False)
    out["fit_deckplate"] = {"tex": "fit_plate", "title": "Deck Plate", "sound": "METAL"}

    # ---- outside: the bulbs round her number, the signal searchlights, the bridge's visor, the scuppers
    lamp("fit_numlight", [[2.5, 6.5, 14.5, 5.5, 9.5, 16, "fit_grey"], [3, 7, 13, 5, 9, 14.5, G],
                          [10.5, 6.5, 14.5, 13.5, 9.5, 16, "fit_grey"], [11, 7, 13, 13, 9, 14.5, G]],
         "Number Light", "facing", 6, G, "bunk_glass_on", "bunk_glass_off", 6)
    lamp("fit_searchlight", [[6, 0, 6, 10, 6, 10, "fit_grey"], [3, 6, 5, 13, 8, 11, "fit_grey"],
                             [3.5, 7, 3, 12.5, 15, 13, "fit_grey"], [4, 7.5, 2.6, 12, 14.5, 3, G],
                             [7.5, 15, 6, 8.5, 16, 10, "fit_grey"]],
         "Signal Searchlight", "facing", 15, G, "fit_lens_on", "fit_lens_off")
    add("fit_visor", [[0, 13, 4, 16, 14, 16, "navy_hull"], [1, 10, 12, 2, 13, 16, "navy_hull"],
                      [14, 10, 12, 15, 13, 16, "navy_hull"]], "Window Visor")
    add("fit_scupper", [[6, 6, 11, 10, 10, 16, "fit_grey"], [6.5, 6.5, 10.9, 9.5, 9.5, 11, "home_rubber"]], "Scupper")

    add("fit_louvre", [[0, 0, 15.5, 16, 16, 16, "fit_louvre"]], "Vent Louvre")
    add("fit_hatch", [[1, 0, 15, 15, 16, 16, "fit_hatch"]], "Side Door")

    add("fit_line", [[0, 0, 0, 16, 0.25, 16, "fit_line"]], "Deck Line", rotate="axis")

    # ---- officers' country (from the wardroom, stateroom and head photos)
    under = {"side": "navy_deck_steel", "down": "navy_overhead"}
    out["fit_tile_cream"] = {"tex": dict(under, up="fit_tile_cream"), "title": "Tile Floor", "sound": "STONE"}
    out["fit_carpet"] = {"tex": dict(under, up="fit_carpet"), "title": "Carpet", "sound": "WOOL"}
    out["fit_joiner"] = {"tex": "fit_laminate", "title": "Joiner Bulkhead", "sound": "WOOD"}
    out["fit_pillar"] = {"tex": "fit_cherry", "title": "Pillar", "sound": "WOOD"}
    out["fit_door_cabin"] = {"door": "wood", "tex": {"top": "fit_cabin_door_top", "bottom": "fit_cabin_door_bottom"},
                             "title": "Stateroom Door"}
    out["fit_door_stall"] = {"door": "wood", "tex": {"top": "fit_stall_door_top", "bottom": "fit_stall_door_bottom"},
                             "title": "Stall Door"}
    # the lining a stateroom's steel bulkheads get: the laminate on the face into the room, the steel's paint behind
    add("fit_lining", [[0, 0, 0, 16, 16, 16, {"north": "fit_laminate", "side": "navy_wall", "up": "navy_wall",
                                               "down": "navy_wall"}]], "Joiner Bulkhead", sound="WOOD", decor=False)

    for name, face in (("fit_lining_cream", "fit_wall_cream"), ("fit_lining_wainscot", "fit_wainscot")):
        add(name, [[0, 0, 0, 16, 16, 16, {"north": face, "side": "navy_wall", "up": "navy_wall", "down": "navy_wall"}]],
            "Bulkhead", sound="WOOD", decor=False)

    # the drop ceiling, a hand's breadth under the deck beams: tiles in their T-bars, a troffer, a downlight; a
    # tile gone (the pipes over it showing), one down on the floor
    tile = {"down": "fit_ceiling", "side": "fit_ceiling", "up": "bunk_grey"}
    bar = "home_white"
    add("fit_ceiling", [[0, 12, 0, 16, 12.6, 16, tile]], "Ceiling", sound="WOOD", rotate=None)
    add("fit_ceiling_open", [[0, 12, 0, 8, 12.6, 16, tile], [8, 12, 0, 16, 12.6, 8, tile],
                             [8, 12, 8, 16, 12.6, 8.4, bar], [8, 12, 8, 8.4, 12.6, 16, bar], [15.6, 12, 8.4, 16, 12.6, 16, bar],
                             [8.4, 12, 15.6, 15.6, 12.6, 16, bar], [0, 13.5, 10.5, 16, 15.5, 12.5, "bunk_pipe_red"],
                             [0, 14.5, 13, 16, 15.5, 15.5, "bunk_cables"], [11, 9, 12.5, 11.5, 12, 13, "bunk_cables"]],
        "Ceiling", sound="WOOD")
    add("fit_ceiling_fallen", [[1.5, 0, 2.5, 9.5, 0.6, 10.5, tile], [8.5, 0, 6, 14.5, 0.5, 13, tile],
                               [4, 0.6, 7, 7, 0.9, 8, "fit_ceiling"]], "Fallen Ceiling Tile", sound="WOOD")
    rim = [[0, 12, 0, 16, 12.6, 1, bar], [0, 12, 7, 16, 12.6, 8, bar], [0, 12, 1, 1, 12.6, 7, bar],
           [15, 12, 1, 16, 12.6, 7, bar], [0, 12.6, 0, 16, 13.4, 8, "bunk_grey"], [0, 12, 8, 16, 12.6, 16, tile]]
    lamp("fit_troffer", rim + [[1, 12.3, 1, 15, 12.6, 7, G]], "Ceiling Light", "axis", 14, G, "fit_troffer_on",
         "fit_troffer_off", 12)
    lamp("fit_downlight", [[0, 12, 0, 16, 12.6, 6, tile], [0, 12, 10, 16, 12.6, 16, tile], [0, 12, 6, 6, 12.6, 10, tile],
                           [10, 12, 6, 16, 12.6, 10, tile], [6, 11.8, 6, 10, 12.1, 6.4, "fit_brass"],
                           [6, 11.8, 9.6, 10, 12.1, 10, "fit_brass"], [6, 11.8, 6.4, 6.4, 12.1, 9.6, "fit_brass"],
                           [9.6, 11.8, 6.4, 10, 12.1, 9.6, "fit_brass"], [6.4, 12.2, 6.4, 9.6, 12.6, 9.6, G]],
         "Downlight", None, 11, G, "fit_shade_on", "bunk_glass_off", 9)

    # on the bulkheads: the brass sconces over the wardroom's paintings, the paintings, the mess's plaque
    lamp("fit_sconce", [[6.5, 5, 15, 9.5, 10, 16, "fit_brass"], [7.5, 7.5, 12, 8.5, 8.3, 15, "fit_brass"],
                        [7.5, 8.3, 11.5, 8.5, 9.5, 12.5, "fit_brass"], [5, 9.5, 9, 11, 13.5, 15, G],
                        [6, 13.5, 10, 10, 13.8, 14, "fit_brass"]],
         "Wall Sconce", "facing", 11, G, "fit_shade_on", "home_shade", 9)
    for k in "abc":
        add(f"fit_painting_{k}", [[0.5, 2.5, 15, 15.5, 13.5, 16, {"north": f"fit_paint_{k}", "side": "fit_brass",
                                                                   "up": "fit_brass", "down": "fit_brass"}]],
            "Painting", sound="WOOD")
    add("fit_crest", [[2.5, 2.5, 15, 13.5, 13.5, 16, {"north": "fit_crest", "side": "fit_brass", "up": "fit_brass",
                                                       "down": "fit_brass"}]], "Wardroom Plaque", sound="WOOD")

    # the wardroom: round tables under white cloths, the chairs in their star-pattern covers, the serving line
    def table(cloth, things):
        skirt = [[1, 0.6, 3, 15, 12.5, 13, cloth], [3, 0.6, 1, 13, 12.5, 15, cloth], [2, 0.6, 2, 14, 12.5, 14, cloth],
                 [0.7, 11.2, 2.7, 15.3, 12.5, 13.3, cloth], [2.7, 11.2, 0.7, 13.3, 12.5, 15.3, cloth]]
        return skirt + things
    add("fit_round_table", table("fit_linen", [[10, 12.5, 4, 12, 16, 6, "home_carafe"], [4, 12.5, 9, 5.5, 14, 10.5, "home_mug"],
                                               [9, 12.5, 10, 10.5, 14, 11.5, "home_mug"], [6.5, 12.5, 5, 8.5, 13.5, 6, "home_chrome"]]),
        "Wardroom Table", sound="WOOL", decor=False, loot="drinks")
    add("fit_round_table_mess", table("fit_linen_soiled", [[3, 12.5, 4, 8, 12.9, 9, "home_plates"],
                                                           [9, 12.5, 9, 10.5, 13.3, 12, "home_mug"],
                                                           [10, 12.5, 3, 13, 13.6, 6, "home_cardboard"],
                                                           [5, 12.5, 10, 8, 12.8, 13, "home_sheet_blood"]]),
        "Wardroom Table", sound="WOOL", decor=False, loot="basement_medicine")
    legs = [[3.5, 0, 3.5, 4.5, 7, 4.5, "home_wood_dark"], [11.5, 0, 3.5, 12.5, 7, 4.5, "home_wood_dark"],
            [3.5, 0, 11.5, 4.5, 7, 12.5, "home_wood_dark"], [11.5, 0, 11.5, 12.5, 7, 12.5, "home_wood_dark"]]
    add("fit_chair_ward", legs + [[3, 7, 3, 13, 9.5, 13.5, "fit_stars"], [3, 9.5, 12, 13, 21, 14, "fit_stars"],
                                  [3.5, 21, 12.2, 12.5, 21.6, 13.8, "fit_stars"]], "Wardroom Chair", sound="WOOL")
    add("fit_chair_ward_tipped", [[3, 0, 2, 13, 2, 13.5, "fit_stars"], [3, 2, 11.5, 13, 12, 13.5, "fit_stars"],
                                  [3.5, 2, 2.5, 4.5, 3, 9.5, "home_wood_dark"], [11.5, 2, 2.5, 12.5, 3, 9.5, "home_wood_dark"],
                                  [3.5, 9, 13.5, 4.5, 16, 14.5, "home_wood_dark"], [11.5, 9, 13.5, 12.5, 16, 14.5, "home_wood_dark"]],
        "Wardroom Chair", sound="WOOL")
    glass = "bunk_glass_off"
    add("fit_serving", [[0, 0, 2, 16, 13, 16, {"north": "fit_steam_front", "side": "fit_stainless", "up": "fit_pans",
                                               "down": "fit_stainless"}],
                        [0, 11.5, -1, 16, 12.3, 2, "fit_stainless"], [0, 10.5, -1, 16, 11.5, -0.4, "fit_stainless"],
                        [1, 13, 8, 1.6, 20, 8.6, "fit_stainless"], [14.4, 13, 8, 15, 20, 8.6, "fit_stainless"],
                        [0.5, 19.4, 4, 15.5, 19.8, 9, glass], [0.5, 15, 4, 15.5, 19.4, 4.4, glass]],
        "Serving Line", decor=False, loot="kitchen")
    add("fit_urn", [[4.5, -1.5, 4.5, 11.5, 7, 11.5, "fit_stainless"], [5, 7, 5, 11, 8, 11, "fit_stainless"],
                    [7, 8, 7, 9, 9, 9, "home_plastic_black"], [7.5, -0.5, 3, 8.5, 0.5, 4.5, "home_chrome"],
                    [5, -1.5, 3, 11, -1.2, 4.5, "home_chrome"]], "Coffee Urn")

    # the staterooms: two-tier berths in the laminate (the head end against a bulkhead; facing runs head to foot),
    # a reading light in each, drawers under the lower one; the fold-down desk with its shelves, the wardrobe,
    # the little sink and its mirror cabinet
    def berth(lo, head):
        out_ = []
        if lo:
            out_ += [[0.5, 0, 0, 15.5, 4.5, 16, {"east": "fit_drawers_lam", "west": "fit_drawers_lam", "side": "fit_laminate",
                                                  "up": "fit_laminate", "down": "fit_laminate"}],
                     [1, 4.5, 0, 15, 7, 16, {"up": "home_mattress", "side": "home_mattress", "down": "fit_laminate"}]]
            y = 7
        else:
            out_ += [[0.5, 1, 0, 15.5, 3, 16, "fit_laminate"], [1, 3, 0, 15, 5.5, 16, {"up": "home_mattress", "side": "home_mattress",
                                                                                      "down": "fit_laminate"}],
                     [0.2, 5.5, 2, 1, 8.5, 14, "fit_laminate"], [15, 5.5, 2, 15.8, 8.5, 14, "fit_laminate"]]
            y = 5.5
        if head:
            out_ += [[0, 0, 15, 16, 16 if lo else 10, 16, "fit_laminate"], [1, y, 0, 15, y + 0.6, 11, "fit_blanket"],
                     [2, y, 11, 14, y + 2, 15, "home_pillow"], [6, y + 3.5, 14.2, 10, y + 5, 15, G],
                     [5.5, y + 5, 14, 10.5, y + 5.4, 15, "fit_brass"]]
        else:
            out_ += [[0, 0, 0, 16, 16 if lo else 10, 1, "fit_laminate"], [1, y, 1, 15, y + 0.6, 16, "fit_blanket"],
                     [1, y + 0.6, 1, 15, y + 2.2, 5, "fit_blanket"]]
            if lo:
                out_ += [[15.2, 9, 1, 15.5, 16, 9, "navy_curtain_blue"], [15, 15.5, 1, 15.8, 16, 15, "home_chrome"]]
        return out_
    for tier in ("lo", "hi"):
        lamp(f"fit_berth_{tier}_head", berth(tier == "lo", True), "Berth", "facing", 7, G, "fit_reading_on", "fit_reading_off")
        add(f"fit_berth_{tier}_foot", berth(tier == "lo", False), "Berth", sound="WOOD", decor=False)
        out[f"fit_berth_{tier}_head"]["decor"] = out[f"fit_berth_{tier}_head_on"]["decor"] = False
        out[f"fit_berth_{tier}_head"]["loot"] = out[f"fit_berth_{tier}_head_on"]["loot"] = "home_wardrobe" if tier == "lo" else "home_nightstand"
    out["fit_berth_lo_foot"]["loot"] = "clothes"
    add("fit_secretary", [[0.5, 0, 4, 15.5, 11, 16, {"north": "fit_drawers_lam3", "side": "fit_laminate", "up": "fit_laminate",
                                                    "down": "fit_laminate"}],
                          [0.5, 11, 0, 15.5, 12, 16, {"up": "fit_desk_papers", "side": "fit_laminate", "down": "fit_laminate"}],
                          [11, 12, 9, 13, 14.5, 11, "home_mug"], [2, 12, 12, 7, 13, 15, "home_books"]],
        "Desk", sound="WOOD", decor=False, loot="office")
    lamp("fit_shelves", [[0.5, 4, 8, 15.5, 16, 16, {"north": "fit_wardrobe", "side": "fit_laminate", "up": "fit_laminate",
                                                    "down": "fit_laminate"}],
                         [1, 0, 10, 15, 3.6, 15.5, "home_books"], [0.5, 0, 15.5, 15.5, 4, 16, "fit_laminate"],
                         [2, 3.6, 9, 14, 4, 10, G]], "Desk Shelves", "facing", 8, G, "fit_reading_on", "fit_reading_off", 7)
    for k in ("fit_shelves", "fit_shelves_on", "fit_shelves_flicker"):
        out[k]["decor"] = False
        out[k]["loot"] = "home_books"
    add("fit_wardrobe_lo", [[1, 0, 2, 15, 16, 16, {"north": "fit_wardrobe", "side": "fit_laminate", "up": "fit_laminate",
                                                   "down": "fit_laminate"}]], "Wardrobe", sound="WOOD", decor=False,
        loot="home_wardrobe")
    add("fit_wardrobe_hi", [[1, 0, 2, 15, 15, 16, {"north": "fit_drawers_lam", "side": "fit_laminate", "up": "fit_laminate",
                                                   "down": "fit_laminate"}], [0.5, 15, 1.5, 15.5, 16, 16, "fit_laminate"]],
        "Wardrobe", sound="WOOD", decor=False, loot="clothes")
    add("fit_sink_cabin", [[2, 0, 6, 14, 12, 16, {"north": "fit_drawers_lam", "side": "fit_laminate", "up": "fit_laminate",
                                                  "down": "fit_laminate"}],
                           [1.5, 12, 5, 14.5, 13.5, 16, {"up": "fit_black_sink", "side": "fit_black", "down": "fit_black"}],
                           [7.5, 13.5, 13.5, 8.5, 17, 14.5, "home_chrome"], [7.5, 16.2, 10.5, 8.5, 17, 14.5, "home_chrome"]],
        "Sink", sound="WOOD", decor=False, loot="home_bathroom")
    lamp("fit_medcab", [[2.5, 1, 12, 13.5, 12, 16, {"north": "fit_mirror_cab", "side": "fit_laminate", "up": "fit_laminate",
                                                     "down": "fit_laminate"}], [2.5, 12.5, 13, 13.5, 13.5, 16, G]],
         "Medicine Cabinet", "facing", 6, G, "fit_reading_on", "fit_reading_off")
    for k in ("fit_medcab", "fit_medcab_on"):
        out[k]["decor"] = False
        out[k]["loot"] = "home_medicine"
    for k in list(out):
        if k.startswith(("fit_berth", "fit_shelves", "fit_medcab")):
            out[k]["sound"] = "WOOD"
    add("fit_laundry_bag", [[4, 0, 4, 12, 7, 12, "fit_mesh_bag"], [5, 7, 5, 11, 8.5, 11, "fit_mesh_bag"],
                            [7.5, 8.5, 7.5, 8.5, 10, 8.5, "fit_mesh_bag"]], "Laundry Bag", sound="WOOL", loot="clothes")

    # the captain's cabin: the chess table (a war in progress, red coats against blue)
    pieces = []
    red, blue = "home_red_steel", "home_fab_blue"
    for i, (col, row, tall) in enumerate(((0, 0, 1), (1, 0, 0), (3, 0, 2), (4, 0, 2), (6, 0, 0), (0, 1, 0), (2, 1, 0),
                                          (3, 2, 0), (5, 1, 0), (7, 1, 0), (5, 3, 1))):
        x, z = 2 + col * 1.5 + 0.35, 2 + row * 1.5 + 0.35
        pieces.append([x, 12, z, x + 0.8, 13.4 + 0.5 * tall, z + 0.8, red])
    for i, (col, row, tall) in enumerate(((0, 7, 1), (2, 7, 0), (3, 7, 2), (4, 7, 2), (7, 7, 1), (1, 6, 0), (3, 6, 0),
                                          (4, 5, 0), (6, 6, 0), (2, 4, 1))):
        x, z = 2 + col * 1.5 + 0.35, 2 + row * 1.5 + 0.35
        pieces.append([x, 12, z, x + 0.8, 13.4 + 0.5 * tall, z + 0.8, blue])
    add("fit_chess", [[0.5, 11, 0.5, 15.5, 12, 15.5, {"up": "fit_chessboard", "side": "home_wood_dark", "down": "home_wood_dark"}],
                      [6, 1, 6, 10, 11, 10, "home_wood_dark"], [3, 0, 3, 13, 1, 13, "home_wood_dark"]] + pieces,
        "Chess Table", sound="WOOD", decor=False)

    # the officers' head: orange stall panels on stainless posts, the stainless pans, the black counter and its
    # bowls, the long mirror with its soap dispensers
    # a stall is one block: the pan facing its door, the orange panel down its right-hand side on a
    # stainless post at the front; fit_stall_top carries the panel up over it
    pan = [[5.5, 0, 4, 10.5, 5, 11, "fit_stainless"], [4.5, 5, 1.5, 11.5, 7.5, 11, "fit_stainless"],
           [6, 6.5, 3, 10, 7.6, 9, "home_toilet_water"], [4.2, 7.5, 1.2, 11.8, 8, 11, "home_plastic_black"],
           [6.5, 5, 11, 9.5, 9, 14, "fit_stainless"], [7.5, 9, 13, 8.5, 16, 14, "home_chrome"],
           [6, 13, 13, 10, 14, 15.5, "home_chrome"], [2, 7, 14, 3.5, 8.5, 16, "home_chrome"]]
    add("fit_toilet_steel", pan, "Toilet", decor=False)
    add("fit_toilet_stall", pan + [[15, 1.5, 1, 16, 16, 16, "fit_stall"], [14.6, 0, 0, 16, 16, 1, "fit_stainless"],
                                   [15.2, 0, 8, 15.8, 1.5, 9, "fit_stainless"]], "Toilet", decor=False)
    add("fit_stall_top", [[15, 0, 1, 16, 12, 16, "fit_stall"], [14.6, 0, 0, 16, 12.6, 1, "fit_stainless"],
                          [14.8, 12, 1, 16, 12.6, 16, "fit_stainless"]], "Stall Panel", sound="WOOD", decor=False)
    add("fit_vanity_black", [[0, 12, 2, 16, 13.5, 16, {"up": "fit_black_sink", "side": "fit_black", "down": "fit_black"}],
                             [0, 9, 2, 16, 12, 3, "fit_black"], [4, 8, 5, 12, 12, 13, "fit_stainless"],
                             [7.5, 13.5, 13, 8.5, 16, 14, "home_chrome"], [7.5, 15.2, 10, 8.5, 16, 14, "home_chrome"],
                             [0.5, 0, 14, 1.5, 9, 15.5, "fit_stainless"], [14.5, 0, 14, 15.5, 9, 15.5, "fit_stainless"],
                             [6, 0, 9, 10, 8, 10, "bunk_pipe_grey"]], "Washbasin", decor=False, loot="home_bathroom")
    add("fit_mirror_long", [[0, 1, 15.5, 16, 12, 16, "home_mirror"], [0, 12, 15, 16, 12.6, 16, "fit_stainless"],
                            [0, 0.4, 15, 16, 1, 16, "fit_stainless"], [11, 3, 13, 14, 8, 15.5, "fit_stainless"],
                            [12, 2, 13.5, 13, 3, 14.5, "home_chrome"]], "Mirror", sound="GLASS")

    for k in SIGNS:
        add(f"fit_st_{k}", [[0, 0, 15.75, 16, 16, 16, f"fit_st_{k}"]], "Stencil")
    for i in range(len(LABELS)):
        add(f"fit_pl_{i}", [[0, 0, 15.75, 16, 16, 16, f"fit_pl_{i}"]], "Compartment Label")
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
    print(f"{len(made)} fit blocks in {path}")


if __name__ == "__main__":
    main()
