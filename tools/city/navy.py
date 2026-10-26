#!/usr/bin/env python3
"""The navy kit: what the aircraft carrier at the naval wharf is built from, so none of it needs
a vanilla block. Haze-grey hull plating with its seams, butts, rust and fender scrapes and the
white of the hull number; the flight deck's dark non-skid with its tie-down pad eyes, tyre
rubber, oil and soot, the painted lines, the catapult tracks and their shuttles, arresting
wires, jet blast deflectors, the elevators' hazard edges and the landing lens; the island's
windows, phased-array faces, lattice mast and radar; the guns and launchers (visual only); the
deck gear (tow tractors, bomb skids, missiles, chocks, tool boxes, hose reels); the hangar's
floor, bulkheads, roller and fire-division doors, lamps and spare engines; and below decks the
tiled passageways, berthing racks, mess tables, ready-room seats, magazines and turbines, with
the placards and stencils that tell you where you are.

    python3 tools/city/navy.py        rewrites the navy_ entries in blocks.json

photo_textures.py draws the textures (navy.register()). Pieces follow furniture.py's rule:
the base model faces north with its back (z = 16) against what it hangs on, so a decal's facing
is the way it looks, away from its wall; axis pieces run along z.
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
PREFIXES = ("navy_",)

HAZE = (118, 124, 128)
DECK = (60, 62, 64)
HANGAR = (92, 96, 98)
WHITE = (222, 220, 212)
YELLOW = (218, 178, 38)
RED = (172, 38, 30)
GREEN = (52, 128, 70)
OD = (78, 84, 56)
GUNMETAL = (52, 54, 56)
RUST = B.RUST


# ---------------------------------------------------------------- the hull and the deck

def hull(seed, kind="plain", rgb=HAZE):
    return V.hull_plate(seed, rgb, kind)


def nonskid(seed, rgb=DECK, pad=False):
    """The flight deck's non-skid: grit rolled into dark paint, worn smooth and pale where the
    jets taxi; a tie-down pad eye (a star cut in a steel disc) at the corner of every block."""
    t = F.mat("Asphalt025C", rgb, 1.2, seed=seed, smooth=0.12)
    t.speckle(0.25, tuple(c * 1.25 for c in rgb), 0.3, 0.02)
    B.patches(t, 0.35, tuple(c * 1.18 for c in rgb), 0.25, 0.55)
    if pad:
        yy, xx = np.mgrid[0:N, 0:N]
        dx, dy = (xx + N / 2) % N - N / 2, (yy + N / 2) % N - N / 2
        r = np.hypot(dx, dy)
        disc = r < 7
        t.c[disc] = np.array((112, 114, 112), np.float32)
        t.h[disc] = 0.8
        t.f0[disc] = 160
        t.smooth[disc] = 0.45
        ang = np.arctan2(dy, dx)
        star = disc & (r < 5.5) & ((np.abs(np.sin(ang * 3)) < 0.35) | (r < 1.5))
        t.c[star] = (18, 18, 18)
        t.h[star] = 0.2
    return F.wear(t, 0.15, 2)


def flight(seed, kind="plain"):
    t = nonskid(seed, DECK, pad=kind == "plain")
    rng = t.rng
    if kind == "tyre":
        # black rubber laid down in arcs by the tyres, and the scuffed path they wore
        for _ in range(5):
            y0 = rng.uniform(-10, N)
            k = rng.uniform(-0.25, 0.25)
            for x in range(N):
                y = int(y0 + k * x + 3 * np.sin(x / 9)) % N
                F.area(t, x, y, x + 1, y + rng.uniform(2, 5), (26, 26, 28), dh=-0.02)
    elif kind == "oil":
        B.patches(t, 0.6, (24, 24, 26), 0.25, 0.5, tide=0.3, wet=0.8)
        B.patches(t, 0.3, (64, 52, 38), 0.35, 0.6, wet=0.6)
    elif kind == "soot":
        y = np.arange(N)[:, None] / N
        g = np.clip(0.9 + P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.3))) * 0.6 - y * 0.2, 0, 1)
        t.mix(g * 0.75, (20, 19, 18), 1.0)
    return t


def deck_line(seed, rgb, width=16, base=DECK):
    """A painted line along the deck (down the texture), chipped and greyed by the grit."""
    t = nonskid(seed, base)
    x0 = (N - width) // 2
    F.area(t, x0, 0, x0 + width, N, rgb, dh=0.02)
    B.patches(t, 0.4, tuple(c * 0.72 for c in rgb), 0.3, 0.6)
    t.speckle(0.15, DECK, 0.4)
    return t


def deck_paint(seed, rgb=WHITE):
    """A block of deck paint for the big markings: the hull number and the landing area's edges."""
    t = nonskid(seed, rgb)
    B.patches(t, 0.5, tuple(c * 0.78 for c in rgb), 0.3, 0.6)
    B.patches(t, 0.25, DECK, 0.4, 0.62)
    return t


def hazard(seed):
    """Yellow and black stripes down the middle of the block: an elevator's edge."""
    t = nonskid(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    band = (xx >= 16) & (xx < 48)
    yel = ((xx + yy) // 12) % 2 == 0
    t.c[band & yel] = np.array(YELLOW, np.float32)
    t.c[band & ~yel] = np.array((24, 24, 22), np.float32)
    B.patches(t, 0.35, (70, 66, 52), 0.35, 0.6)
    return t


def catapult(seed):
    """A catapult track: the slot the shuttle runs in, steel lips either side, the seal strip
    blackened by the steam."""
    t = nonskid(seed)
    F.area(t, 22, 0, 42, N, (122, 124, 120), h=0.95, smooth=0.5, f0=170)
    F.area(t, 27, 0, 37, N, (14, 14, 14), h=0.1)
    for x in (22, 41):
        F.area(t, x, 0, x + 1, N, mul=0.6)
    for y in range(0, N, 16):
        F.area(t, 22, y, 27, y + 2, mul=0.7)
        F.area(t, 37, y, 42, y + 2, mul=0.7)
    B.patches(t, 0.4, (36, 34, 32), 0.3, 0.6)
    return t


def cable(seed):
    """Wire rope: twisted strands, greased."""
    t = P.Tex(seed, (92, 94, 92))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    s = np.sin((xx * 0.8 + yy) * 2 * np.pi / 8)
    t.c *= (0.75 + 0.25 * s)[..., None]
    t.h = 0.6 + 0.3 * s
    t.f0[:] = 170
    t.smooth[:] = 0.55
    t.grime(0.4, (30, 26, 20))
    return t


def jbd(seed):
    """A jet blast deflector's face: water-cooled panels in a grid, scorched and sooted."""
    t = B.painted(seed, (104, 100, 94), "Metal019", 1.0, 0.3)
    for x in range(0, N, 16):
        F.area(t, x, 0, x + 1, N, mul=0.55, dh=0.1)
    for y in range(0, N, 8):
        F.area(t, 0, y, N, y + 1, mul=0.75, dh=0.06)
    yy = np.arange(N)[:, None] / N
    g = np.clip(0.4 + yy * 0.8 + P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 0.4, 0, 1)
    t.mix(g * 0.8, (28, 24, 20), 1.0)
    t.speckle(0.06, (150, 90, 50), 0.6)
    return t


def lens(seed):
    """The landing lens: a column of five cells (amber above, red at the foot) between the
    green datum lights."""
    t = steel(seed, (46, 48, 50))
    for i in range(5):
        col = (220, 150, 40) if i < 4 else (200, 40, 30)
        y = 4 + i * 11
        F.area(t, 24, y, 40, y + 9, col, h=0.9, smooth=0.9, f0=40)
        t.emit[y:y + 9, 24:40] = 0.7
    for x in (4, 46):
        F.area(t, x, 26, x + 14, 32, GREEN, h=0.9, smooth=0.9, f0=40)
        t.emit[26:32, x:x + 14] = 0.7
    return t


def steel(seed, rgb, rough=0.35):
    return V.steel_paint(seed, rgb, rough)


def window(seed, broken=False):
    """The island's bridge glass: a thick grey frame, dark tinted panes, the wiper's arc."""
    t = hull(seed, "plain")
    F.area(t, 0, 0, N, 8, mul=0.9, h=0.95)
    F.area(t, 0, 56, N, N, mul=0.9, h=0.95)
    V.glass_pane(t, 3, 8, N - 1, 56, broken)
    F.area(t, 0, 8, 3, 56, (88, 92, 96), h=0.95, f0=200)
    return t


def array_face(seed):
    """A phased-array radar's flat octagonal face: tiles of elements behind a grey cover."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = np.abs(xx - 31.5), np.abs(yy - 31.5)
    m = (dx < 30) & (dy < 30) & (dx + dy < 42)
    B.solid(t, m.astype(np.float32), (132, 138, 140), h=0.8, vary=0.05)
    tiles = m & (((xx % 6) < 1) | ((yy % 6) < 1))
    t.c[tiles] *= 0.82
    rim = m & ~((dx < 27) & (dy < 27) & (dx + dy < 38))
    t.c[rim] = np.array((96, 100, 104), np.float32)
    t.h[rim] = 1.0
    B.runs(t, 2, (90, 78, 64), 0.4, (20, 40), (1.0, 2.0), top=8)
    t.alpha[~m] = 0
    return t


def lattice(seed, rgb=(150, 154, 156)):
    """A steel lattice: legs at the corners, diagonals between, see-through."""
    t = B.clear(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    m = (xx < 5) | (xx > 58) | (yy < 3) | (np.abs(yy - 32) < 2)
    m |= (np.abs(xx - yy) < 2.5) | (np.abs(xx - (N - yy)) < 2.5)
    B.solid(t, m.astype(np.float32), rgb, h=1.0, vary=0.1)
    t.f0[:] = 150
    t.speckle(0.05, RUST, 0.5)
    t.alpha[~m] = 0
    return t


def radome(seed):
    """Glassfibre radome: chalky white, streaked grey where the rain runs."""
    t = F.plain(seed, (226, 226, 220), 0.04, 0.4)
    B.runs(t, 4, (150, 148, 140), 0.4, (30, 64), (2.0, 5.0))
    return F.wear(t, 0.2, 2)


def ram_face(seed):
    """A launcher's face: rows of round tube ends with blue frangible covers."""
    t = steel(seed, HAZE)
    for row in range(5):
        for col in range(4 + (row % 2 == 0)):
            cx = 9 + col * 12 + (0 if row % 2 == 0 else 6)
            cy = 8 + row * 12
            F.disc(t, cx, cy, 5, (56, 82, 120), h=0.6)
            F.disc(t, cx, cy, 5, ring=1.2, mul=0.6)
    return t


def ssm_face(seed):
    """A box launcher's door: eight cells, each with its hatch, numbers stencilled on."""
    t = steel(seed, HAZE)
    for row in range(2):
        for col in range(4):
            x, y = 2 + col * 15, 4 + row * 30
            F.area(t, x, y, x + 13, y + 26, mul=0.86, dh=0.08)
            F.area(t, x, y, x + 13, y + 1, mul=1.3)
            F.area(t, x + 5, y + 11, x + 8, y + 14, (40, 40, 40))
    return t


def dish(seed):
    """A fire-control dish: white, ribbed, its feed horn black in the middle."""
    t = radome(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 31.5, yy - 31.5)
    for rr in (10, 20):
        ring = np.abs(r - rr) < 0.8
        t.c[ring] *= 0.85
    F.disc(t, 32, 32, 5, (30, 30, 30))
    return t


def bomb(seed):
    """An olive bomb body, the yellow band that says it's live, a stencilled line of lot numbers."""
    t = B.painted(seed, OD, "PaintedMetal004", 0.9, 0.4)
    for y in (8, 52):
        F.area(t, 0, y, N, y + 5, YELLOW)
    B.stencil(t, "MK 84|LOT 112", (220, 210, 170), 8, 4, 18, 56, 28, chip=0.15)
    return F.wear(t, 0.2, 3)


def missile(seed):
    """A missile's white body, a brown band and the stencils down it."""
    t = F.plain(seed, (214, 214, 206), 0.04, 0.45)
    F.area(t, 0, 10, N, 15, (120, 82, 40))
    F.area(t, 0, 42, N, 45, YELLOW)
    B.stencil(t, "WARHEAD|HANDLE WITH CARE", (40, 40, 38), 7, 4, 18, 56, 22, chip=0.1)
    return F.wear(t, 0.2, 2)


def hangar_floor(seed, oil=False):
    t = nonskid(seed, HANGAR)
    if oil:
        B.patches(t, 0.6, (30, 30, 32), 0.25, 0.5, tide=0.3, wet=0.8)
    return t


def bulkhead(seed, rgb=HAZE):
    """The hangar's side: plates with a deep frame every block, cable runs, rust."""
    t = B.painted(seed, rgb, "PaintedMetal001", 0.9, 0.42)
    F.area(t, 26, 0, 38, N, mul=0.82, dh=0.16)
    F.area(t, 26, 0, 28, N, mul=1.25)
    F.area(t, 0, 0, N, 2, mul=0.75, dh=0.08)
    B.runs(t, 3, RUST, 0.45, (30, 64), (1.5, 3.0))
    return F.wear(t, 0.25, 6)


def roller(seed):
    """A roller door's slats, grey, dented, rust at the bottom edge of each."""
    t = B.painted(seed, (150, 154, 152), "CorrugatedSteel006A", 1.0, 0.35)
    for y in range(0, N, 8):
        F.area(t, 0, y, N, y + 1, mul=0.6, dh=0.1)
        F.area(t, 0, y + 6, N, y + 8, mul=0.85)
    B.rust_runs(t, 4, 0.4)
    return F.wear(t, 0.25, 4)


def curtain(seed):
    """A fire-division door: grey slats with the hazard stripes along its foot."""
    t = roller(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    band = yy >= 48
    yel = ((xx + yy) // 10) % 2 == 0
    t.c[band & yel] = np.array(YELLOW, np.float32)
    t.c[band & ~yel] = np.array((26, 26, 24), np.float32)
    return t


def engine_face(seed):
    """The front of a spare engine: the fan's blades round the spinner, in bare metal."""
    t = F.plain(seed, (150, 152, 150), 0.05, 0.6)
    t.f0[:] = 200
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = xx - 31.5, yy - 31.5
    r = np.hypot(dx, dy)
    a = np.arctan2(dy, dx)
    blades = (np.sin(a * 13 + r * 0.12) > 0.2) & (r < 28)
    t.c[blades] *= 0.62
    t.c[r >= 28] = np.array((70, 72, 72), np.float32)
    F.disc(t, 32, 32, 7, (190, 192, 188), h=1.0)
    return t


def wall(seed, rgb=(196, 198, 192), runs=False):
    """An interior bulkhead: pale paint over weld seams, the frame showing every block."""
    t = B.ship_wall(seed, rgb)
    if runs:
        for y in (6, 14):
            F.area(t, 0, y, N, y + 5, (170, 172, 166), dh=0.25)
            F.area(t, 0, y + 4, N, y + 5, mul=0.6)
    return t


def overhead(seed):
    """The deckhead: white paint over the beams, pipes and cable trays running across."""
    t = B.ship_wall(seed, (214, 214, 208), frames=False)
    F.area(t, 0, 28, N, 36, mul=0.85, dh=0.2)
    for x, col in ((8, (120, 140, 160)), (18, (160, 60, 50)), (48, (210, 200, 160))):
        F.area(t, x, 0, x + 5, N, col, dh=0.3)
        F.area(t, x + 4, 0, x + 5, N, mul=0.6)
    return t


def tile(seed, rgb):
    """Vinyl deck tiles: squares, the wax gone grey at the edges, scuffed by boots."""
    t = F.mat("old_linoleum_flooring_01", rgb, 0.9, seed=seed, smooth=0.5)
    for k in (0, 32):
        F.area(t, k, 0, k + 1, N, mul=0.82)
        F.area(t, 0, k, N, k + 1, mul=0.82)
    B.patches(t, 0.3, (60, 58, 52), 0.3, 0.6)
    return F.wear(t, 0.25, 6)


def formica(seed):
    t = F.plain(seed, (168, 176, 182), 0.05, 0.55)
    t.speckle(0.25, (120, 128, 136), 0.3)
    F.area(t, 0, 0, N, 3, (150, 152, 150), f0=200)
    return F.wear(t, 0.2, 5)


def curtain_fabric(seed):
    t = F.mat("Fabric023", (44, 58, 96), 1.2, seed=seed, smooth=0.1)
    for x in range(0, N, 8):
        F.area(t, x, 0, x + 2, N, mul=0.82)
    return F.wear(t, 0.25, 0)


def placard(seed, text):
    """A compartment's number on its black plate: deck, frame, position and use."""
    t = B.clear(seed)
    F.area(t, 6, 18, 58, 46, (24, 24, 24), alpha=255)
    t.alpha[18:46, 6:58] = 255
    B.stencil(t, text, (226, 222, 210), 10, 8, 20, 48, 24, chip=0.04, font=BOLD)
    return t


def toolbox(seed):
    t = steel(seed, (168, 40, 32), 0.45)
    F.area(t, 0, 20, N, 22, mul=0.6)
    F.area(t, 26, 26, 38, 30, (180, 180, 176), f0=200)
    return t


def leather(seed):
    return F.leather(seed, (40, 46, 70))


def screen(seed, rgb):
    """A dead display: dark glass, a reflection, the tiny lamp still green in the corner."""
    t = F.plain(seed, (22, 26, 30), 0.03, 0.9)
    t.f0[:] = 40
    F.area(t, 0, 0, N, 3, (60, 62, 60))
    F.area(t, 0, 61, N, N, (60, 62, 60))
    yy, xx = np.mgrid[0:N, 0:N]
    streak = np.abs(xx - yy * 0.7 - 10) < 5
    t.c[streak] = t.c[streak] * 0.7 + 25
    F.area(t, 56, 56, 59, 59, rgb)
    t.emit[56:59, 56:59] = 0.8
    return t


def whiteboard(seed):
    t = F.plain(seed, (224, 226, 222), 0.03, 0.75)
    F.area(t, 0, 0, N, 3, (150, 150, 146))
    F.area(t, 0, 61, N, N, (150, 150, 146))
    B.stencil(t, "LAUNCH 0600|RECOVERY 0745|CASE I", (40, 60, 120), 8, 6, 8, 52, 40, chip=0.3, font=BOLD)
    B.patches(t, 0.2, (180, 184, 196), 0.4, 0.7)
    return t


def turbine(seed):
    """A turbine's casing: heavy grey castings, flanges bolted round, lagging on the pipes."""
    t = B.painted(seed, (110, 118, 112), "PaintedMetal004", 1.0, 0.4)
    for y in (0, 31):
        F.area(t, 0, y, N, y + 4, mul=0.8, dh=0.2)
    B.rivets(t, tuple(range(4, N, 8)), (2, 33), r=1.2)
    B.patches(t, 0.3, (60, 54, 44), 0.35, 0.6, wet=0.6)
    return F.wear(t, 0.3, 4)


SIGNS = {
    "hangar1": ("HANGAR BAY|1", WHITE), "hangar2": ("HANGAR BAY|2", WHITE), "hangar3": ("HANGAR BAY|3", WHITE),
    "armory": ("ARMORY|AUTHORIZED ONLY", (200, 40, 30)), "sickbay": ("SICK BAY", WHITE),
    "mess": ("MESS DECK", WHITE), "berthing": ("BERTHING", WHITE), "magazine": ("MAGAZINE|NO OPEN FLAME", (200, 40, 30)),
    "flight": ("FLIGHT DECK|EYES AND EARS", YELLOW), "galley": ("GALLEY", WHITE),
    "machinery": ("MAIN|MACHINERY", WHITE), "ready": ("READY ROOM", WHITE), "brig": ("BRIG", WHITE),
    "control": ("FLIGHT|CONTROL", WHITE), "bridge": ("BRIDGE", WHITE), "blast": ("BEWARE|JET BLAST", YELLOW),
    "quarterdeck": ("QUARTERDECK", WHITE), "props": ("BEWARE|PROPS AND|INTAKES", YELLOW),
}
PLACARDS = ("2-120-0-L", "2-142-1-L", "3-88-2-M", "3-160-0-L", "4-112-1-Q", "01-64-2-Q", "03-180-1-L", "2-210-0-A")


def textures():
    T = {}
    s = 7600
    for k in ("plain", "seam", "butt", "rust", "scrape"):
        T["navy_hull" + ("" if k == "plain" else f"_{k}")] = (lambda a, b: lambda: hull(a, b))(s, k)
        s += 1
    T.update({
        "navy_hull_white": lambda: hull(7606, "plain", WHITE),
        "navy_flight": lambda: flight(7607), "navy_flight_tyre": lambda: flight(7608, "tyre"),
        "navy_flight_oil": lambda: flight(7609, "oil"), "navy_flight_soot": lambda: flight(7610, "soot"),
        "navy_flight_bare": lambda: flight(7611, "bare"),
        "navy_fl_white": lambda: deck_line(7612, WHITE), "navy_fl_yellow": lambda: deck_line(7613, YELLOW),
        "navy_fl_red": lambda: deck_line(7614, RED, 24), "navy_fl_paint": lambda: deck_paint(7615),
        "navy_fl_paint_yellow": lambda: deck_paint(7616, YELLOW), "navy_hazard": lambda: hazard(7617),
        "navy_catapult": lambda: catapult(7618), "navy_cable": lambda: cable(7619), "navy_jbd": lambda: jbd(7620),
        "navy_lens": lambda: lens(7621), "navy_steel": lambda: steel(7622, (112, 116, 118)),
        "navy_window": lambda: window(7623), "navy_window_broken": lambda: window(7624, True),
        "navy_array": lambda: array_face(7625), "navy_lattice": lambda: lattice(7626),
        "navy_radome": lambda: radome(7627), "navy_gun": lambda: steel(7628, GUNMETAL, 0.5),
        "navy_ram_face": lambda: ram_face(7629), "navy_ssm_face": lambda: ssm_face(7630), "navy_dish": lambda: dish(7631),
        "navy_od": lambda: steel(7632, OD), "navy_yellow": lambda: steel(7633, YELLOW),
        "navy_tyre": lambda: F.plain(7634, (30, 30, 30), 0.06, 0.25), "navy_bomb": lambda: bomb(7635),
        "navy_missile": lambda: missile(7636), "navy_red": lambda: steel(7637, RED),
        "navy_hangar_floor": lambda: hangar_floor(7638), "navy_hangar_oil": lambda: hangar_floor(7639, True),
        "navy_bulkhead": lambda: bulkhead(7640), "navy_roller": lambda: roller(7641), "navy_curtain": lambda: curtain(7642),
        "navy_tube_on": lambda: B.tube(7643, True), "navy_engine": lambda: engine_face(7644),
        "navy_bare": lambda: F.plain(7645, (150, 152, 150), 0.06, 0.6),
        "navy_wall": lambda: wall(7646), "navy_wall_runs": lambda: wall(7647, runs=True),
        "navy_overhead": lambda: overhead(7648),
        "navy_tile_blue": lambda: tile(7649, (70, 92, 132)), "navy_tile_green": lambda: tile(7650, (84, 118, 92)),
        "navy_tile_grey": lambda: tile(7651, (140, 142, 138)), "navy_tile_red": lambda: tile(7652, (132, 54, 48)),
        "navy_formica": lambda: formica(7653), "navy_curtain_blue": lambda: curtain_fabric(7654),
        "navy_mattress": lambda: B.ticking(7655), "navy_alu": lambda: F.plain(7656, (176, 178, 176), 0.04, 0.55),
        "navy_toolbox": lambda: toolbox(7657), "navy_leather": lambda: leather(7658),
        "navy_screen": lambda: screen(7659, (40, 200, 70)), "navy_whiteboard": lambda: whiteboard(7660),
        "navy_turbine": lambda: turbine(7661), "navy_deck_steel": lambda: steel(7662, (88, 92, 94)),
    })
    for i, (k, (text, col)) in enumerate(SIGNS.items()):
        T[f"navy_st_{k}"] = (lambda a, b, c: lambda: B.stencil_sign(a, b, c, 12))(7700 + i, text, col)
    for i, text in enumerate(PLACARDS):
        T[f"navy_pl_{i}"] = (lambda a, b: lambda: placard(a, b))(7730 + i, text)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound="METAL", rotate="facing", decor=False, light=0, loot=None, climb=False):
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

    def cube(name, tex, title, sound="METAL", rotate=None):
        out[name] = {"tex": tex, "title": title, "sound": sound}
        if rotate:
            out[name]["rotate"] = rotate

    def decal(name, tex, title):
        add(name, [[0, 0, 15.75, 16, 16, 16, tex]], title, "METAL", decor=True)

    # the hull and the island's plating
    for k in ("", "_seam", "_butt", "_rust", "_scrape"):
        cube(f"navy_hull{k}", f"navy_hull{k}", "Hull Plating")
    cube("navy_hull_white", "navy_hull_white", "Hull Plating")
    cube("navy_steel", "navy_steel", "Steel Plate")
    cube("navy_window", {"north": "navy_window", "south": "navy_window", "side": "navy_hull", "up": "navy_hull",
                         "down": "navy_hull"}, "Bridge Window", "GLASS", rotate="facing")
    cube("navy_window_broken", {"north": "navy_window_broken", "south": "navy_window_broken", "side": "navy_hull",
                                "up": "navy_hull", "down": "navy_hull"}, "Bridge Window", "GLASS", rotate="facing")

    # the flight deck: the underside of the deck is the hangar's (or the gallery's) deckhead
    for k in ("", "_tyre", "_oil", "_soot", "_bare"):
        cube(f"navy_flight{k}", {"up": f"navy_flight{k}", "side": "navy_hull", "down": "navy_overhead"}, "Flight Deck")
    for k in ("white", "yellow", "red"):
        cube(f"navy_fl_{k}", {"up": f"navy_fl_{k}", "side": "navy_hull", "down": "navy_overhead"}, "Flight Deck",
             rotate="axis")
    cube("navy_fl_paint", {"up": "navy_fl_paint", "side": "navy_hull", "down": "navy_overhead"}, "Flight Deck")
    cube("navy_fl_paint_yellow", {"up": "navy_fl_paint_yellow", "side": "navy_hull", "down": "navy_overhead"},
         "Flight Deck")
    cube("navy_hazard", {"up": "navy_hazard", "side": "navy_hull", "down": "navy_overhead"}, "Elevator Edge",
         rotate="axis")
    cube("navy_catapult", {"up": "navy_catapult", "side": "navy_hull", "down": "navy_overhead"}, "Catapult Track",
         rotate="axis")
    add("navy_shuttle", [[5, 0, 2, 11, 3, 14, "navy_steel"], [6.5, 3, 4, 9.5, 6, 8, "navy_steel"],
                         [7, 0, 0, 9, 2, 2, "navy_gun"]], "Catapult Shuttle")
    add("navy_wire", [[0, 0, 7, 16, 1.5, 9, "navy_cable"]], "Arresting Wire", rotate="axis", decor=True)
    add("navy_wire_sheave", [[2, 0, 3, 14, 3, 13, "navy_steel"], [0, 1, 7, 16, 2.5, 9, "navy_cable"]],
        "Wire Support", rotate="axis")
    cube("navy_jbd", {"north": "navy_jbd", "south": "navy_steel", "side": "navy_steel", "up": "navy_steel",
                      "down": "navy_steel"}, "Jet Blast Deflector", rotate="facing")
    cube("navy_hangar_floor", {"up": "navy_hangar_floor", "side": "navy_hull", "down": "navy_overhead"}, "Hangar Deck")
    cube("navy_hangar_oil", {"up": "navy_hangar_oil", "side": "navy_hull", "down": "navy_overhead"}, "Hangar Deck")
    add("navy_lens", [[0, 0, 4, 16, 16, 12, {"north": "navy_lens", "side": "navy_steel", "up": "navy_steel",
                                             "down": "navy_steel", "south": "navy_steel"}]],
        "Landing Lens", light=6)
    add("navy_edge_light", [[6, 0, 6, 10, 1, 10, "navy_tube_on"]], "Deck Light", rotate=None, decor=True)
    add("navy_padeye", [[5, 0, 5, 11, 0.5, 11, "navy_steel"]], "Pad Eye", rotate=None, decor=True)

    # the island
    add("navy_array", [[0, 0, 14.5, 16, 16, 16, "navy_array"]], "Radar Array")
    cube("navy_lattice", "navy_lattice", "Lattice Mast")
    add("navy_radar", [[0, 6, 6, 16, 14, 10, "navy_lattice"], [6, 0, 6, 10, 6, 10, "navy_steel"]],
        "Radar Antenna", rotate="axis")
    add("navy_dish", [[2, 2, 0, 14, 14, 3, {"north": "navy_dish", "side": "navy_radome", "up": "navy_radome",
                                             "down": "navy_radome", "south": "navy_steel"}],
                      [6, 0, 3, 10, 6, 13, "navy_steel"], [7, 7, 3, 9, 9, 8, "navy_gun"]], "Fire-Control Dish")

    # the guns and launchers, for show
    add("navy_ciws_base", [[1, 0, 1, 15, 16, 15, "navy_steel"], [0, 14, 0, 16, 16, 16, "navy_steel"]],
        "Gun Mount", rotate=None)
    add("navy_ciws_body", [[2, 0, 3, 14, 10, 15, "navy_steel"], [13, 2, 5, 16, 9, 13, "navy_steel"],
                           [3, 10, 4, 13, 16, 14, "navy_radome"], [5, 3, 0, 11, 9, 3, "navy_gun"]], "Close-In Gun")
    add("navy_ciws_dome", [[3, 0, 4, 13, 8, 14, "navy_radome"], [4, 8, 5, 12, 12, 13, "navy_radome"],
                           [5, 12, 6, 11, 14, 12, "navy_radome"]], "Close-In Gun")
    add("navy_ciws_gun", [[5, 4, 0, 11, 10, 16, "navy_gun"], [4.5, 3.5, 0, 11.5, 10.5, 3, "navy_gun"],
                          [6, 5, 0, 10, 9, 0.5, "navy_tyre"]], "Gun Barrels")
    add("navy_ram", [[1, 3, 0, 15, 15, 12, {"north": "navy_ram_face", "side": "navy_steel", "up": "navy_steel",
                                             "down": "navy_steel", "south": "navy_steel"}],
                     [5, 0, 4, 11, 3, 12, "navy_steel"]], "Missile Launcher")
    add("navy_ssm", [[0, 2, 0, 16, 16, 16, {"north": "navy_ssm_face", "side": "navy_steel", "up": "navy_steel",
                                             "down": "navy_steel", "south": "navy_steel"}],
                     [4, 0, 4, 12, 2, 12, "navy_steel"]], "Missile Launcher")
    add("navy_mg", [[7, 0, 7, 9, 8, 9, "navy_gun"], [6, 8, 3, 10, 11, 13, "navy_gun"], [7.2, 9, 0, 8.8, 10.6, 3, "navy_gun"],
                    [3, 8, 1, 13, 15, 2, "navy_od"], [12, 8, 4, 15, 11, 9, "navy_od"]], "Heavy Machine Gun")
    add("navy_chaff", [[2, 0, 2, 14, 4, 14, "navy_steel"]] +
        [[3 + c * 4, 4, 3 + r * 4, 6 + c * 4, 14, 6 + r * 4, "navy_gun"] for c in range(3) for r in range(2)],
        "Decoy Launcher")

    # deck gear
    add("navy_tractor_front", [[1, 2, 0, 15, 8, 16, "navy_yellow"], [2, 8, 0, 14, 10, 6, "navy_yellow"],
                               [0, 0, 2, 3, 5, 6, "navy_tyre"], [13, 0, 2, 16, 5, 6, "navy_tyre"],
                               [0, 0, 11, 3, 5, 15, "navy_tyre"], [13, 0, 11, 16, 5, 15, "navy_tyre"],
                               [5, 5, -1, 11, 7, 1, "navy_gun"]], "Tow Tractor")
    add("navy_tractor_back", [[1, 2, 0, 15, 8, 14, "navy_yellow"], [3, 8, 1, 13, 10, 7, "navy_leather"],
                              [3, 10, 6, 13, 15, 7, "navy_leather"], [5, 8, 9, 11, 12, 13, "navy_yellow"],
                              [7, 12, 10, 9, 14, 12, "navy_tyre"]], "Tow Tractor")
    add("navy_bomb", [[5, 4, 0, 11, 10, 16, "navy_bomb"], [6, 5, -1, 10, 9, 0, "navy_od"], [6, 6, 16, 10, 8, 17, "navy_od"],
                      [3, 0, 3, 13, 4, 5, "navy_yellow"], [3, 0, 11, 13, 4, 13, "navy_yellow"]], "Bomb", rotate="axis")
    add("navy_missile", [[6.5, 4, 0, 9.5, 7, 16, "navy_missile"], [4, 3.5, 12, 12, 7.5, 13, "navy_missile"],
                         [7.5, 1, 12, 8.5, 10, 13, "navy_missile"], [5, 0, 3, 11, 4, 5, "navy_steel"],
                         [5, 0, 10, 11, 4, 12, "navy_steel"]], "Missile", rotate="axis")
    add("navy_chocks", [[1, 0, 5, 6, 3, 11, "navy_yellow"], [10, 0, 5, 15, 3, 11, "navy_yellow"],
                        [6, 1, 7.5, 10, 2, 8.5, "navy_cable"]], "Wheel Chocks", decor=True)
    add("navy_chains", [[0, 0, 7.5, 16, 0.6, 8.5, "vessel_chain_h"], [7.5, 0, 0, 8.5, 0.6, 16, "vessel_chain_h"]],
        "Tie-Down Chains", rotate=None, decor=True)
    add("navy_toolbox", [[2, 0, 4, 14, 7, 12, "navy_toolbox"], [5, 7, 7, 11, 8, 9, "navy_gun"]], "Tool Box",
        loot="home_tools")
    add("navy_hose_reel", [[2, 0, 6, 14, 2, 10, "navy_red"], [3, 2, 7, 4, 13, 9, "navy_red"], [12, 2, 7, 13, 13, 9, "navy_red"],
                           [4, 4, 4, 12, 12, 12, "navy_tyre"]], "Fuel Hose Reel")
    add("navy_extinguisher", [[3, 0, 3, 13, 10, 13, "navy_red"], [6, 10, 6, 10, 13, 10, "navy_gun"],
                              [1, 0, 2, 15, 1, 14, "navy_gun"]], "Wheeled Extinguisher")

    # the hangar
    cube("navy_bulkhead", "navy_bulkhead", "Bulkhead")
    cube("navy_roller", {"north": "navy_roller", "south": "navy_roller", "side": "navy_steel", "up": "navy_steel",
                         "down": "navy_steel"}, "Hangar Door", rotate="facing")
    cube("navy_curtain", {"north": "navy_curtain", "south": "navy_curtain", "side": "navy_steel", "up": "navy_steel",
                          "down": "navy_steel"}, "Fire Division Door", rotate="facing")
    add("navy_lamp", [[3, 14, 0, 13, 16, 16, "navy_steel"], [4, 13.5, 0.5, 12, 14, 15.5, "navy_tube_on"]],
        "Hangar Light", rotate="axis", decor=True, light=14)
    add("navy_engine", [[1, 3, 0, 15, 15, 16, {"north": "navy_engine", "south": "navy_engine", "side": "navy_bare",
                                                "up": "navy_bare", "down": "navy_bare"}],
                        [2, 0, 2, 14, 3, 14, "navy_yellow"]], "Spare Engine", rotate="axis")

    # below decks
    cube("navy_wall", "navy_wall", "Bulkhead")
    cube("navy_wall_runs", "navy_wall_runs", "Bulkhead")
    cube("navy_overhead", "navy_overhead", "Deckhead")
    for k in ("blue", "green", "grey", "red"):
        cube(f"navy_tile_{k}", {"up": f"navy_tile_{k}", "side": "navy_deck_steel", "down": "navy_overhead"},
             "Deck Tiles")
    cube("navy_deck_steel", {"up": "navy_deck_steel", "side": "navy_deck_steel", "down": "navy_overhead"}, "Steel Deck")
    rack = [[0, 0, 0, 1, 16, 1, "navy_alu"], [15, 0, 0, 16, 16, 1, "navy_alu"], [0, 0, 15, 1, 16, 16, "navy_alu"],
            [15, 0, 15, 16, 16, 16, "navy_alu"], [0, 15.5, 0, 16, 16, 16, "navy_alu"]]
    add("navy_rack_lo", rack + [[0, 1, 0, 16, 2, 16, "navy_alu"], [1, 2, 1, 15, 4, 15, "navy_mattress"],
                                [0, 8, 0, 16, 9, 16, "navy_alu"], [1, 9, 1, 15, 11, 15, "navy_mattress"],
                                [0, 9, 0, 16, 15, 0.5, "navy_curtain_blue"]],
        "Berthing Rack", "METAL", loot="home_wardrobe")
    add("navy_rack_hi", rack + [[0, 0, 0, 16, 1, 16, "navy_alu"], [1, 1, 1, 15, 3, 15, "navy_mattress"],
                                [0, 1, 0, 6, 12, 0.5, "navy_curtain_blue"], [2, 13, 12, 14, 15, 15, "navy_tube_on"]],
        "Berthing Rack", "METAL", loot="home_nightstand")
    add("navy_mess_table", [[0, 10, 1, 16, 11, 15, "navy_formica"], [7, 0, 7, 9, 10, 9, "navy_alu"],
                            [0, 5, -3, 16, 6, 0, "navy_formica"], [0, 5, 16, 16, 6, 19, "navy_formica"],
                            [2, 0, -2, 3, 5, -1, "navy_alu"], [13, 0, 17, 14, 5, 18, "navy_alu"]],
        "Mess Table", rotate="axis")
    add("navy_ready_seat", [[2, 0, 4, 14, 2, 12, "navy_alu"], [2, 4, 2, 14, 7, 13, "navy_leather"],
                            [2, 7, 11, 14, 16, 14, "navy_leather"], [7, 2, 6, 9, 4, 10, "navy_alu"],
                            [1, 6, 3, 2, 9, 12, "navy_alu"], [14, 6, 3, 15, 9, 12, "navy_alu"]], "Ready Room Seat")
    add("navy_screen", [[1, 3, 15, 15, 13, 16, "navy_screen"]], "Display", decor=True)
    add("navy_whiteboard", [[0, 2, 15.5, 16, 14, 16, "navy_whiteboard"]], "Status Board", decor=True)
    add("navy_turbine", [[0, 1, 1, 16, 15, 15, "navy_turbine"], [0, 0, 3, 16, 1, 13, "navy_gun"]], "Turbine",
        rotate="axis")
    add("navy_lamp_wall", [[5, 10, 13, 11, 14, 16, "navy_steel"], [6, 10.5, 12.5, 10, 13.5, 13, "navy_tube_on"]],
        "Bulkhead Light", light=11)

    for k in SIGNS:
        decal(f"navy_st_{k}", f"navy_st_{k}", "Stencil")
    for i in range(len(PLACARDS)):
        decal(f"navy_pl_{i}", f"navy_pl_{i}", "Compartment Placard")
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
    print(f"{len(made)} navy blocks in {path}")


if __name__ == "__main__":
    main()
