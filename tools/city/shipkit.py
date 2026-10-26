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
        "fit_chart": lambda: chart(8113), "fit_drawers": lambda: drawers(8114), "fit_leather": lambda: vinyl(8115, (64, 44, 32)),
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

    add("fit_line", [[0, 0, 0, 16, 0.25, 16, "fit_line"]], "Deck Line", rotate="axis")

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
