"""The ct_ and cr_ kits: shipping containers built to the landmarks' detail in place of 12 x 3 x 3 painted
cubes (Josiah, 2026-10-29: "Everything in this world must be of the same level of detail and
effort"). world/county/Boxes.java lays them out; Harbour, BigShip, Camp and the civic yards call it.

A container is three blocks square and len long. Every piece is drawn in its own frame with the
face it shows on the north (z = 0), rotate=facing:

- ct_<c>_side / _side_top / _side_bot: a length of side wall, the corrugations standing proud of
  it (four a block), the top rail with the roof's corrugations over it, or the bottom rail;
- ct_<c>_roof: the roof between the two side walls, its corrugations across the box;
- ct_<c>_end_<row><col> and ct_<c>_door_<row><col>: the closed end and the door end, row b/m/t,
  col 0/1/2 from the low x of the piece's frame: corner posts and castings, the side walls' first
  corrugations round the corner, the doors' four locking bars with their cams, handles and
  keepers, the hinges, the header and the sill, the owner's code stencilled on the left door.

The middle of the box (never seen) stays the plain container_<c> cube.

The cranes' sloping members (Harbour.member): the A-frames, stays, raised booms and lattice jibs
were lines of whole blocks stepping up a staircase. cr_<family>_<k>[_p|_m] is one member in the
y-z plane, k its direction in 11.25 degree steps from +z toward +y (free model angles, 1.21.6 on),
_p1.._p3 / _m1.._m3 three pixels a step off the cell's centre along its left normal, overlapping the
next so a run reads as one straight member (the Ferris wheel's trick, waterfront.py, made finer):

- cr_stay: a tension bar; cr_strut_<red|yellow|white>: a 0.5 m box member; cr_boom_<red|yellow>:
  a raised boom's 0.9 m girder; cr_lattice_yellow: a lattice jib, see-through;
- cr_steel_white: the white cranes' plating, a cube.

    python3 tools/city/ports.py         rewrites the ct_ entries in blocks.json

photo_textures.py draws the textures (ports.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402

N = P.N
HERE = Path(__file__).parent

COLOURS = {"red": (146, 52, 40), "blue": (44, 76, 116), "green": (62, 98, 68), "orange": (190, 102, 40),
           "white": (192, 192, 186), "grey": (108, 112, 114)}
CODES = {"red": "ZCLU 412093", "blue": "GHVU 280415", "green": "MRKU 663710", "orange": "TSLU 509126",
         "white": "CRXU 174382", "grey": "PNSU 931057"}


# ---------------------------------------------------------------- textures

def rust(t, amount):
    """Rust blooming through the paint at the edges and in the troughs: orange-brown, rougher."""
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.5), (32, 0.3)))
    m = np.clip((n - (0.55 - amount)) * 4, 0, 1)
    col = np.array((104, 58, 34), np.float32) * (0.8 + 0.4 * (P.fbm(t.rng) * 0.5 + 0.5))[..., None]
    t.c = t.c * (1 - m[..., None]) + col * m[..., None]
    t.smooth = t.smooth * (1 - m * 0.6)


def paint(seed, rgb):
    """Container paint: a marine enamel gone chalky, rust through it, salt streaks down from the top."""
    t = F.mat("PaintedMetal004", rgb, 0.3, span=3, seed=seed)
    t.vary(0.05)
    rust(t, 0.18)
    t.streaks(0.8, 0.72)
    t.grime(0.15, (60, 54, 46))
    return t


def flat(seed, rgb):
    """The plain container cube's paint (a box's hidden middle, and the odd body built of whole
    blocks): the corrugations painted in light and shade, four a block, salt-streaked."""
    t = paint(seed, rgb)
    x = np.arange(N, dtype=np.float32)
    wave = np.cos((x / N * 4) * 2 * np.pi)
    t.c = t.c * (1 + 0.12 * wave)[None, :, None]
    t.h = np.clip(t.h * 0.5 + 0.5 + 0.45 * wave[None, :], 0, 1)
    return t


def code(seed, rgb, text):
    """The left door's top: the owner's code in white stencil over the paint."""
    t = paint(seed, rgb)
    a, b = text.split()
    for line, y0 in ((a, 8), (b, 28)):
        m = F.text_mask(line, F.SANS, 15, N, 18, seed=seed)
        F.paste(t, m, 0, y0, (226, 226, 218))
    return t


def casting(seed):
    """A corner casting: cast steel, the oval hole the twistlocks go into, worn bright round it."""
    t = F.mat("rusty_metal", (92, 88, 82), 0.9, seed=seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    hole = ((xx - N / 2) / (N * 0.28)) ** 2 + ((yy - N / 2) / (N * 0.16)) ** 2 < 1
    ring = ((xx - N / 2) / (N * 0.36)) ** 2 + ((yy - N / 2) / (N * 0.24)) ** 2 < 1
    t.c[ring & ~hole] = t.c[ring & ~hole] * 0.6 + np.array((150, 146, 140), np.float32) * 0.4
    t.c[hole] = (14, 12, 12)
    t.h[hole] = 0.1
    return t


def white_steel(seed):
    t = F.mat("PaintedMetal004", (206, 206, 200), 0.35, span=3, seed=seed)
    rust(t, 0.15)
    t.streaks(0.9, 0.7)
    return t


def lattice(seed):
    """A jib's lattice face: chords down both edges, diagonals between, the sky through the rest."""
    t = F.mat("PaintedMetal001", (200, 150, 40), 0.4, span=3, seed=seed)
    rust(t, 0.15)
    yy, xx = np.mgrid[0:N, 0:N]
    w = N // 10
    keep = (xx < w) | (xx >= N - w) | (np.abs((xx - yy) % N) < w) | (np.abs((xx + yy) % N) < w) | (yy < w // 2)
    t.alpha[:] = np.where(keep, 255, 0)
    return t


PLATES = {"red": (150, 46, 36), "yellow": (204, 152, 42), "white": (206, 206, 200), "grey": (118, 122, 124)}


def plate(seed, rgb, seam=False):
    """A crane's box-girder plating: marine enamel over steel plate, a stiffener's faint shadow down
    the middle; the seam piece has the butt weld and its bolted splice strap along the top, rust
    weeping from the bolts (one in every few metres of a leg)."""
    t = F.mat("PaintedMetal004", rgb, 0.1 if sum(rgb) > 540 else 0.15, span=3, seed=seed)
    t.vary(0.03)
    rust(t, 0.08)
    dark = tuple(int(v * 0.62) for v in rgb)
    y0 = 0
    if seam:
        t.rect(0, 0, N, 2, col=dark, h=0.2)
        t.rect(0, 2, N, 7, col=tuple(int(v * 1.04) for v in rgb), h=0.85)
        t.rect(0, 7, N, 8, col=dark, h=0.3)
        for x in range(4, N, 8):
            t.rect(x, 3, x + 2, 5, col=(min(255, rgb[0] + 18), min(255, rgb[1] + 18), min(255, rgb[2] + 18)), h=1.0)
            t.rect(x, 5, x + 2, 9 + (x * 7 % 5), col=(110, 62, 36))
        y0 = 8
    t.rect(N // 2 - 1, y0, N // 2 + 1, N, col=tuple(int(v * 0.92) for v in rgb), h=0.6)
    t.streaks(0.7, 0.72)
    t.grime(0.12, (60, 54, 46))
    return t


def oil(seed, rgb, rusty):
    """A fuel tank's shell: plate courses welded edge to edge (a seam across every block and down
    every second), the paint chalked, rust weeping from the seams; the rusty one is mostly rust."""
    t = F.mat("PaintedMetal004", rgb, 0.12, span=3, seed=seed)
    rust(t, 0.35 if rusty else 0.1)
    seam = tuple(int(v * 0.7) for v in rgb)
    t.rect(0, N - 2, N, N, col=seam, h=0.9)
    t.rect(0, N - 4, N, N - 2, col=tuple(int(v * 0.85) for v in rgb), h=0.8)
    t.rect(N // 2 - 1, 0, N // 2 + 1, N - 4, col=seam, h=0.9)
    for x in range(2, N, 9):
        t.rect(x, N - 2, x + 2, N - 2 + 1, col=(120, 64, 36))
    t.streaks(1.0 if rusty else 0.8, 0.66)
    t.grime(0.2, (70, 60, 50))
    return t


def oil_roof(seed):
    """A tank's low cone roof: grey-white sheets lapped round, rust in the laps, bird mess."""
    t = F.mat("PaintedMetal004", (182, 182, 176), 0.15, span=3, seed=seed)
    rust(t, 0.15)
    for y in range(0, N, 16):
        t.rect(0, y, N, y + 1, col=(120, 116, 108), h=0.4)
    t.speckle(0.01, (222, 222, 214), 0.5)
    return t


def slip(seed):
    """A slipformed grain silo: grey concrete, the lifts' faint lines every few inches, rain
    stains down from the top, cracks."""
    t = F.mat("Concrete034", (160, 158, 150), 0.6, seed=seed)
    for y in range(0, N, 11):
        t.rect(0, y, N, y + 1, col=(132, 130, 124), h=0.6)
    t.streaks(1.0, 0.72)
    t.crack(2, 0.25, (60, 58, 54))
    return t


def lh_paint(seed, rgb):
    """A lighthouse's painted render: limewash over the stone courses, flaking, salt-stained."""
    t = F.mat("Concrete034", rgb, 0.35, seed=seed)
    for y in range(0, N, 16):
        t.rect(0, y, N, y + 1, col=tuple(int(v * 0.86) for v in rgb), h=0.6)
    t.streaks(0.9, 0.74)
    t.speckle(0.02, tuple(int(v * 0.7) for v in rgb), 0.4)
    t.grime(0.12, (80, 76, 66))
    return t


def prism(seed, lit):
    """A Fresnel lens: rings of glass prisms, each with its bright edge; lit, the lamp burns through."""
    t = F.plain(seed, (200, 228, 220) if lit else (120, 150, 146), 0.04, 0.95)
    for y in range(0, N, 6):
        t.rect(0, y, N, y + 1, col=(255, 252, 230) if lit else (190, 210, 206), h=1.0)
        t.rect(0, y + 4, N, y + 6, col=(170, 200, 192) if lit else (84, 108, 104), h=0.5)
    t.rect(N // 2 - 6, N // 2 - 6, N // 2 + 6, N // 2 + 6, col=(255, 250, 220) if lit else (150, 176, 170), h=1.0)
    return t


def rock(seed, rgb, weed):
    """Armour stone: quarried granite, grey, lichen high up and weed and barnacles at the tide line."""
    t = F.mat("Rock036", rgb, 1.0, span=2, seed=seed)
    t.grime(0.25, (52, 50, 46))
    if weed:
        n = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.4)))
        m = (n > 0.1)[..., None]
        t.c = np.where(m, t.c * 0.5 + np.array((52, 64, 40), np.float32) * 0.5, t.c)
        t.speckle(0.03, (196, 192, 180), 0.6, 0.3)
    else:
        t.speckle(0.02, (170, 168, 120), 0.4)
    return t


BULK = {"coal": ("Gravel041", (30, 30, 32), "GRAVEL"), "gravel": ("Gravel023", None, "GRAVEL"),
        "sand": ("damp_sand", None, "SAND"), "dirt": ("stony_dirt_path", None, "GRAVEL")}


def bulk(seed, material, rgb):
    """A bulk heap's stuff: coal glinting, washed gravel, damp sand, spoil."""
    t = F.mat(material, rgb, 1.2 if rgb else 1.0, span=2, seed=seed)
    if material == "Gravel041":
        t.speckle(0.03, (120, 124, 130), 0.6, 0.2)
    return t


def textures():
    out = {"ct_casting": lambda: casting(49300), "cr_steel_white": lambda: white_steel(49330),
           "cr_lattice_yellow": lambda: lattice(49331), "tk_oil": lambda: oil(49360, (198, 196, 186), False),
           "tk_oil_rust": lambda: oil(49361, (132, 82, 58), True), "tk_oil_roof": lambda: oil_roof(49362),
           "tk_slip": lambda: slip(49363), "lh_white": lambda: lh_paint(49370, (222, 220, 210)),
           "lh_red": lambda: lh_paint(49371, (164, 46, 38)), "lh_cap": lambda: paint(49372, (40, 44, 42)),
           "lh_prism": lambda: prism(49373, False), "lh_prism_lit": lambda: prism(49374, True),
           "bw_rock": lambda: rock(49380, (108, 106, 100), False), "bw_rock_weed": lambda: rock(49381, (86, 84, 78), True),
           "bw_concrete": lambda: slip(49382)}
    for i, (m, (mat_, rgb, _)) in enumerate(BULK.items()):
        out["bulk_" + m] = lambda mat_=mat_, rgb=rgb, i=i: bulk(49390 + i, mat_, rgb)
    for i, (c, rgb) in enumerate(PLATES.items()):
        out["cr_plate_" + c] = lambda rgb=rgb, i=i: plate(49340 + i, rgb)
        out["cr_plate_" + c + "_seam"] = lambda rgb=rgb, i=i: plate(49340 + i, rgb, True)
    for i, (c, rgb) in enumerate(COLOURS.items()):
        out["ct_" + c] = lambda rgb=rgb, i=i: paint(49310 + i, rgb)
        out["ct_" + c + "_code"] = lambda rgb=rgb, i=i, c=c: code(49320 + i, rgb, CODES[c])
        out["ct_" + c + "_flat"] = lambda rgb=rgb, i=i: flat(49350 + i, rgb)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

PITCH = 4.0  # corrugations: four a block
DEEP = 2.0   # the wall's skin, the ridges standing out of it to the face


def corr_north(tex, y0, y1, a0=0.0, a1=16.0, depth=0.8):
    """Vertical corrugations on the north face from x = a0 to a1: the trough skin and the ridges."""
    b = [[a0, y0, DEEP - depth, a1, y1, DEEP, tex]]
    x = a0 + 0.6
    while x + 2 <= a1:
        b.append([x, y0, DEEP - depth - 1.2, x + 2, y1, DEEP - depth, tex])
        x += PITCH
    return b


def corr_side(tex, side, y0, y1, a0, a1):
    """The same on the west (x = 0) or east (x = 16) face, from z = a0 to a1."""
    b = []
    for box in corr_north(tex, y0, y1, a0, a1):
        x0, yy0, z0, x1, yy1, z1 = box[:6]
        # x along the face becomes z; depth (z) becomes x from the west face, mirrored for the east
        if side == "west":
            b.append([z0, yy0, x0, z1, yy1, x1, tex])
        else:
            b.append([16 - z1, yy0, x0, 16 - z0, yy1, x1, tex])
    return b


def roof(tex, along, x0=0.0, x1=16.0, z0=0.0, z1=16.0):
    """The roof sheet a little under the rails and its shallow corrugations; along = the axis each
    ridge runs along (across the box)."""
    b = [[x0, 14.6, z0, x1, 15.3, z1, tex]]
    if along == "z":
        x = x0 + 0.6
        while x + 2 <= x1:
            b.append([x, 15.3, z0, x + 2, 15.8, z1, tex])
            x += PITCH
    else:
        z = z0 + 0.6
        while z + 2 <= z1:
            b.append([x0, 15.3, z, x1, 15.8, z + 2, tex])
            z += PITCH
    return b


def side(c, row):
    """A length of side wall: the corrugations, the rail at the top or bottom, the roof behind."""
    tex = "ct_" + c
    top, bot = row == "t", row == "b"
    y0, y1 = (2.6 if bot else 0), (13.4 if top else 16)
    b = corr_north(tex, y0, y1)
    if bot:
        b.append([0, 0, 0, 16, 2.6, DEEP, tex])
        b.append([0, 2.6, 0.2, 16, 3.0, DEEP, tex])
    if top:
        b.append([0, 13.4, 0, 16, 16, DEEP, tex])
        b.append([0, 13.0, 0.2, 16, 13.4, DEEP, tex])
        b += roof(tex, "z", z0=DEEP)
    b.append([0, 0, DEEP, 16, 14.6 if top else 16, 16, tex])
    return b


def top(c):
    tex = "ct_" + c
    return [[0, 0, 0, 16, 14.6, 16, tex]] + roof(tex, "z")


LOCK_BARS = {0: (7.0,), 1: (1.0, 15.0), 2: (9.0,)}


def end(c, row, col, door):
    """One of the nine cells across an end. The end face is on the north; the box's sides are the
    west face of col 0 and the east face of col 2."""
    tex = "ct_" + c
    top_, bot = row == "t", row == "b"
    west, east = col == 0, col == 2
    b = []
    # the face: corrugations between the corner posts, or the door leaves
    a0, a1 = (2.6 if west else 0), (13.4 if east else 16)
    y0, y1 = (2.6 if bot else 0), (13.4 if top_ else 16)
    if door:
        face = tex + "_code" if west and top_ else tex
        if col == 1:
            b += [[a0, y0, 0.6, 7.8, y1, DEEP, tex], [8.2, y0, 0.6, a1, y1, DEEP, tex]]
        else:
            b.append([a0, y0, 0.6, a1, y1, DEEP, face, {"north": [0, 0, 16, 16]} if face != tex else None])
        # the leaves' shallow ribs, three a leaf
        for x in ((4.5, 10.5) if west else (3.5, 12.5) if col == 1 else (5.5, 11.5)):
            if not (col == 1 and 7.4 < x < 8.6):
                b.append([x - 0.5, y0, 0.2, x + 0.5, y1, 0.6, tex])
        for x in LOCK_BARS[col]:
            b.append([x - 0.5, 0 if bot else -0.01, -0.9, x + 0.5, 16 if not top_ else 14.2, -0.1, "ext_galv"])
            if row == "m":
                # the handle, swung down against its leaf, and its retainer
                hx = x + (1 if x < 8 or col == 2 else -5)
                b.append([hx, 5.5, -1.6, hx + 4, 6.5, -0.9, "ext_galv"])
                b.append([hx + (3 if x < 8 or col == 2 else 0), 4.6, -1.1, hx + (4 if x < 8 or col == 2 else 1), 7.4, -0.1, "ext_galv"])
            if bot:
                b.append([x - 1.2, 2.6, -1.2, x + 1.2, 4.2, -0.1, "ext_galv"])  # the cam in its keeper
            if top_:
                b.append([x - 1.2, 11.8, -1.2, x + 1.2, 13.4, -0.1, "ext_galv"])
        if west or east:
            hx0 = 2.6 if west else 12.2
            for hy in ((3.5,) if bot else (6.5,) if row == "m" else (10.5,)):
                b.append([hx0, hy, -0.6, hx0 + 1.2, hy + 2.4, 0.6, "ext_galv"])  # a hinge
    else:
        b += corr_north(tex, y0, y1, a0, a1)
    # the corner posts and the box's sides round the corner
    if west:
        b.append([0, 0, 0, 2.6, 16, 2.6, tex])
        b += corr_side(tex, "west", y0, y1, 2.6, 16)
    if east:
        b.append([13.4, 0, 0, 16, 16, 2.6, tex])
        b += corr_side(tex, "east", y0, y1, 2.6, 16)
    # the sill and the header, and the side rails running back from them
    if bot:
        b.append([0, 0, 0, 16, 2.6, DEEP + (0.6 if door else 0), tex])
        if west:
            b.append([0, 0, 0, DEEP, 2.6, 16, tex])
        if east:
            b.append([16 - DEEP, 0, 0, 16, 2.6, 16, tex])
    if top_:
        b.append([0, 13.4, 0, 16, 16, DEEP + (1.0 if door else 0), tex])
        if west:
            b.append([0, 13.4, 0, DEEP, 16, 16, tex])
        if east:
            b.append([16 - DEEP, 13.4, 0, 16, 16, 16, tex])
        b += roof(tex, "x", DEEP if west else 0, 16 - DEEP if east else 16, DEEP + (1.0 if door else 0), 16)
    # the corner castings
    for x0 in ((0,) if west else ()) + ((13.4,) if east else ()):
        for cy in ((0,) if bot else ()) + ((13.6,) if top_ else ()):
            b.append([x0, cy, -0.2, x0 + 2.6, cy + 2.4, 2.6, "ct_casting"])
    # what's behind it all
    b.append([DEEP if west else 0, 0, DEEP + (1.0 if door and top_ else 0), 16 - DEEP if east else 16, 14.6 if top_ else 16, 16, tex])
    return b


SIGN = -1  # a turn about x that takes a model from +z toward +y (checked in the crane preview)


STEPS = 16  # directions over a half turn
OFFSETS = (("", 0.0), ("_p1", 3.0), ("_p2", 6.0), ("_p3", 9.0), ("_m1", -3.0), ("_m2", -6.0), ("_m3", -9.0))


def member(k, half, tex, o=0.0, lo=-6, hi=22):
    """A member in the y-z plane, direction k (11.25 degree steps from +z toward +y), o pixels off
    the cell's centre along its left normal."""
    a = (k % STEPS) * 180 / STEPS
    if a <= 45:
        box = [8 - half, 8 + o - half, lo, 8 + half, 8 + o + half, hi, tex]
        r = a
    elif a < 135:
        box = [8 - half, lo, 8 - o - half, 8 + half, hi, 8 - o + half, tex]
        r = a - 90
    else:
        box = [8 - half, 8 - o - half, lo, 8 + half, 8 - o + half, hi, tex]
        r = a - 180
    return [box + [None, {"axis": "x", "angle": SIGN * r, "origin": [8, 8, 8]}]] if r else [box]


MEMBERS = {"cr_stay": (1.5, "painted_steel"), "cr_strut_red": (4, "cr_plate_red"), "cr_strut_yellow": (4, "cr_plate_yellow"),
           "cr_strut_white": (4, "cr_plate_white"), "cr_boom_red": (7, "cr_plate_red"), "cr_boom_yellow": (7, "cr_plate_yellow"),
           "cr_lattice_yellow": (4, "cr_lattice_yellow")}
# a stay is thin enough that a 3 px step shows: its pieces step by 1.5 px
FINE = (("", 0.0),) + tuple((f"_p{i}", 1.5 * i) for i in range(1, 7)) + tuple((f"_m{i}", -1.5 * i) for i in range(1, 7))


# ---------------------------------------------------------------- crane fittings

GALV = "painted_steel"
RUBBER = "tire"


def rail_boxes(paint):
    """A handrail along the front (north) edge: posts every half block, knee and hand rails, toe board."""
    b = [[0, 1, 0, 16, 2.6, 0.6, paint], [0, 7.4, 0.2, 16, 8.4, 1.2, paint], [0, 14.2, 0, 16, 15.4, 1.4, paint]]
    for x in (0.4, 8.4):
        b.append([x, 1, 0.2, x + 1.2, 14.2, 1.4, paint])
    return b


def walk():
    """A boom walkway: open grating on angle bearers, the handrail on its outer (north) edge."""
    return [[0, 0, 0, 16, 1, 16, "grate"], [0, -1.5, 0.5, 16, 0, 2, GALV], [0, -1.5, 14, 16, 0, 15.5, GALV]] + rail_boxes("cr_plate_yellow")


def cage_ladder():
    """A caged ladder against a leg (the leg behind it, south): stiles and rungs, hoops and straps."""
    b = [[3, 0, 13.6, 4.2, 16, 15, GALV], [11.8, 0, 13.6, 13, 16, 15, GALV]]
    for y in (1.5, 5.5, 9.5, 13.5):
        b.append([4.2, y, 14, 11.8, y + 0.9, 14.8, GALV])
    for y in (3, 11):
        b += [[1.6, y, 2, 14.4, y + 0.8, 2.8, GALV], [1.6, y, 2.8, 2.4, y + 0.8, 14, GALV], [13.6, y, 2.8, 14.4, y + 0.8, 14, GALV]]
    b += [[7.6, 0, 2, 8.4, 16, 2.8, GALV], [1.6, 0, 7.6, 2.4, 16, 8.4, GALV], [13.6, 0, 7.6, 14.4, 16, 8.4, GALV]]
    return b


def wheel(z0):
    """A rail wheel seen side on: an octagon from two crossed boxes, the hub proud of it."""
    c = (8, 3.2, z0 + 3.2)
    return [[5.5, 0, z0, 10.5, 6.4, z0 + 6.4, "deck_steel"],
            [5.5, 0, z0, 10.5, 6.4, z0 + 6.4, "deck_steel", None, {"axis": "x", "angle": 45, "origin": list(c)}],
            [4.8, 2.2, z0 + 2.2, 11.2, 4.2, z0 + 4.2, "painted_steel"]]


def bogie():
    """A gantry's wheel truck along its rail (drawn along z): two wheels under an equaliser beam."""
    return (wheel(0.8) + wheel(8.8) +
            [[3.5, 6.4, 0, 12.5, 12, 16, "cr_plate_yellow"], [2.5, 12, 0, 13.5, 13.4, 16, "cr_plate_yellow"],
             [3, 8, -0.2, 13, 10, 0.4, "hazard_stripes"], [3, 8, 15.6, 13, 10, 16.2, "hazard_stripes"],
             [6.5, 13.4, 6, 9.5, 16, 10, "painted_steel"]])


def spreader():
    """The spreader's beam (drawn along z): the box beam, the telescoping arms' rails either side,
    a cable tray on top."""
    return [[5, 6, 0, 11, 12, 16, "cr_plate_yellow"], [3, 7, 0, 5, 10, 16, "cr_plate_grey"], [11, 7, 0, 13, 10, 16, "cr_plate_grey"],
            [6, 12, 2, 10, 13.5, 14, GALV], [6.4, 13.5, 2, 6.8, 14.5, 14, RUBBER], [9.2, 13.5, 2, 9.6, 14.5, 14, RUBBER]]


def spreader_end():
    """A spreader's end beam (its outer face north): the cross beam, a twistlock housing at each
    corner, the guide flippers swung down, a cable gland on top."""
    b = [[0, 6, 2, 16, 12, 8, "cr_plate_yellow"], [5, 6, 8, 11, 12, 16, "cr_plate_yellow"]]
    for x in (0, 12):
        b += [[x, 3, 1, x + 4, 6, 7, "cr_plate_grey"], [x + 1.5, 1.6, 3, x + 2.5, 3, 4.4, "ct_casting"],
              [x, -4, 0, x + 4, 3, 1, "cr_plate_yellow", None, {"axis": "x", "angle": -22.5, "origin": [x + 2, 3, 1]}]]
    b.append([6.5, 12, 1.6, 9.5, 14, 3, GALV])
    return b


def festoon():
    """Festoon cables (drawn along z) hanging in loops from their trolley under the boom: flat
    rubber cables, three loops deep."""
    b = [[5, 13.5, 4, 11, 16, 12, GALV], [4.4, 12.5, 3, 11.6, 13.5, 13, "cr_plate_grey"]]
    for i, d in enumerate((4, 8, 12)):
        x0 = 5.6 + i * 1.8
        b += [[x0, 12.5 - d, 0, x0 + 1, 12.5 - d + 1.2, 16, RUBBER]]
        b += [[x0, 12.5 - d, 0, x0 + 1, 12.5, 1.2, RUBBER], [x0, 12.5 - d, 14.8, x0 + 1, 12.5, 16, RUBBER]]
    return b


def headblock():
    """The hook's headblock (drawn along z): sheave cheek plates, the sheaves' rims, the hook."""
    return [[4, 6, 2, 5.4, 15, 14, "cr_plate_yellow"], [10.6, 6, 2, 12, 15, 14, "cr_plate_yellow"],
            [5.4, 7, 3, 10.6, 14, 13, GALV], [7.2, 14, 7.2, 8.8, 16, 8.8, GALV],
            [7, 2, 7, 9, 6, 9, "painted_steel"], [6.4, 0, 6, 9.6, 2, 7, "painted_steel"], [6.4, 0, 9, 9.6, 2, 10, "painted_steel"],
            [6.4, 0, 6, 9.6, 1, 10, "painted_steel"]]


def pad():
    """An outrigger's jack: the cylinder down from the beam to a steel float on a timber mat."""
    return [[0, 0, 0, 16, 1.2, 16, "hk_timber"], [2, 1.2, 2, 14, 2.6, 14, "deck_steel"], [6, 2.6, 6, 10, 12, 10, GALV],
            [5, 12, 5, 11, 16, 11, "cr_plate_white"]]


def outrigger():
    """An outrigger beam (drawn along z): the box beam with its hazard-striped end plates."""
    return [[3, 4, 0, 13, 12, 16, "cr_plate_white"], [2.8, 6, 7, 13.2, 10, 9, "hazard_stripes"]]


def pipe(x0, y0, x1, y1, tex, z0=0, z1=16):
    """A pipe along z with an octagon's section: a box and the same box turned 45 degrees."""
    c = [(x0 + x1) / 2, (y0 + y1) / 2, 8]
    return [[x0, y0, z0, x1, y1, z1, tex], [x0, y0, z0, x1, y1, z1, tex, None, {"axis": "z", "angle": 45, "origin": c}]]


def tank_pipe():
    """A tank's outlet main (drawn along z) on its sleeper."""
    return pipe(4.5, 1.5, 11.5, 8.5, "bunk_pipe_red") + [[3, 0, 6, 13, 1, 10, "painted_steel"]]


def tank_valve():
    """The main's gate valve: flanges, the body, the bonnet, the stem and its handwheel."""
    return (pipe(4.5, 1.5, 11.5, 8.5, "bunk_pipe_red") + [[3, 0, 6, 13, 1, 10, "painted_steel"],
            [3.5, 0.5, 5, 12.5, 9.5, 6, "bunk_pipe_red"], [3.5, 0.5, 10, 12.5, 9.5, 11, "bunk_pipe_red"],
            [5, 1, 6, 11, 10.5, 10, "painted_steel"], [6, 10.5, 6.5, 10, 13, 9.5, "painted_steel"],
            [7.5, 13, 7.5, 8.5, 15, 8.5, GALV], [4, 15, 4, 12, 15.8, 12, "bunk_pipe_red"]])


def rack_pipes(kind):
    """A pipe rack's run (drawn along z): pipes side by side on the rack's cross beam."""
    b = [[0, 0, 7, 16, 1.6, 9, "painted_steel"], [0, 1.6, 7.6, 16, 2, 8.4, "painted_steel"]]
    if kind == "a":
        b += pipe(1, 2, 6, 7, "bunk_pipe_red") + pipe(7, 2, 10, 5, "bunk_pipe_grey") + pipe(11, 2, 15.5, 6.5, "fit_lagging")
    else:
        b += pipe(0.5, 2, 4.5, 6, "bunk_pipe_grey") + pipe(5.5, 2, 11.5, 8, "bunk_pipe_red") + pipe(12.5, 2, 15, 4.5, "bunk_pipe_green")
    return b


def armour(kind, tex):
    """Armour stone: the block's fill under two or three boulders tipped at odd angles out of it."""
    def turn(axis, a, o):
        return {"axis": axis, "angle": a, "origin": o}
    base = [[0, 0, 0, 16, 9, 16, tex]]
    if kind == "a":
        return base + [[1, 6, 1, 10, 14, 10, tex, None, turn("y", 22.5, [5.5, 10, 5.5])],
                       [8, 5, 7, 15.5, 12, 15.5, tex, None, turn("x", -22.5, [11.5, 8, 11])]]
    if kind == "b":
        return base + [[2, 5, 6, 14, 13, 14, tex, None, turn("z", 22.5, [8, 9, 10])],
                       [0.5, 7, 0.5, 7, 12, 7, tex, None, turn("y", -22.5, [3.75, 9, 3.75])]]
    return base + [[3, 6, 3, 13, 15, 13, tex, None, turn("y", 45, [8, 10, 8])],
                   [0, 4, 9, 6, 10, 16, tex, None, turn("x", 22.5, [3, 7, 12.5])]]


def tetrapod():
    """A concrete tetrapod tumbled on the slope: the knot and four stubby legs."""
    c = "bw_concrete"
    return [[5, 4, 5, 11, 10, 11, c], [6, 9, 6, 10, 16, 10, c],
            [6, 0, 6, 10, 7, 14, c, None, {"axis": "x", "angle": -45, "origin": [8, 7, 8]}],
            [1, 0, 6, 9, 7, 10, c, None, {"axis": "z", "angle": 45, "origin": [8, 7, 8]}],
            [7, 0, 6, 15, 7, 10, c, None, {"axis": "z", "angle": -45, "origin": [8, 7, 8]}]]


def stand():
    """A boat stand beside a hull laid up (the hull to its north): the tripod's legs splayed, the
    screw post leaning in, its rubber pad on the hull; the chain to its partner across the keel."""
    t = "painted_steel"
    o = [8, 0, 9]
    return [[7.3, 0, 8.3, 8.7, 13, 9.7, t, None, {"axis": "x", "angle": -22.5, "origin": o}],
            [7.4, 0, 8.4, 8.6, 9, 9.6, t, None, {"axis": "z", "angle": 33.75, "origin": o}],
            [7.4, 0, 8.4, 8.6, 9, 9.6, t, None, {"axis": "z", "angle": -33.75, "origin": o}],
            [7.4, 0, 8.4, 8.6, 9, 9.6, t, None, {"axis": "x", "angle": 33.75, "origin": o}],
            [6.5, 12, 1.5, 9.5, 13, 5.5, GALV], [4.5, 12.8, 1, 11.5, 16, 2.4, "wf_rubber", None, {"axis": "x", "angle": 22.5, "origin": [8, 13, 2]}],
            [7.6, 0.4, 0, 8.4, 1, 8, GALV]]


def keel_blocks():
    """Timber keel blocks stacked crosswise under a laid-up hull (drawn along z: the keel's line)."""
    t = "hk_timber"
    return [[1, 0, 3, 15, 4, 13, t], [3, 4, 1, 13, 8, 15, t], [1, 8, 3, 15, 12, 13, t], [5, 12, 2, 11, 13, 14, "wf_rubber"]]


def blocks():
    out = {}

    def add(name, boxes, title):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q] for q in boxes],
                     "title": title, "sound": "METAL", "rotate": "facing", "shape": [[0, 0, 0, 16, 16, 16]]}

    for c in COLOURS:
        add(f"ct_{c}_side", side(c, "m"), "Shipping Container")
        add(f"ct_{c}_side_top", side(c, "t"), "Shipping Container")
        add(f"ct_{c}_side_bot", side(c, "b"), "Shipping Container")
        add(f"ct_{c}_roof", top(c), "Shipping Container")
        for row in "bmt":
            for col in range(3):
                add(f"ct_{c}_end_{row}{col}", end(c, row, col, False), "Shipping Container")
                add(f"ct_{c}_door_{row}{col}", end(c, row, col, True), "Container Doors")
    out["cr_steel_white"] = {"tex": "cr_steel_white", "title": "Steel", "sound": "METAL"}
    for c in COLOURS:  # the plain cube a box's hidden cells (and whole-block bodies) are
        out["container_" + c] = {"tex": "ct_" + c + "_flat", "title": "Shipping Container", "sound": "METAL"}
    for c in PLATES:
        out["cr_plate_" + c] = {"tex": "cr_plate_" + c, "title": "Crane Steel", "sound": "METAL"}
        out["cr_plate_" + c + "_seam"] = {"tex": "cr_plate_" + c + "_seam", "title": "Crane Steel", "sound": "METAL"}

    def fit(name, boxes, title, rotate, shape, cutout=False):
        out[name] = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q] for q in boxes],
                     "title": title, "sound": "METAL", "rotate": rotate, "shape": shape}
        if cutout:
            out[name]["render"] = "cutout"
    fit("cr_walk", walk(), "Boom Walkway", "facing", [[0, 0, 0, 16, 1, 16], [0, 1, 0, 16, 15.4, 1.4]], cutout=True)
    fit("cr_rail", rail_boxes("cr_plate_yellow"), "Handrail", "facing", [[0, 0, 0, 16, 15.4, 1.4]])
    fit("cr_cage_ladder", cage_ladder(), "Caged Ladder", "facing", [[3, 0, 13.6, 13, 16, 15]])
    fit("cr_bogie", bogie(), "Gantry Bogie", "axis", [[2.5, 0, 0, 13.5, 16, 16]])
    fit("cr_spreader", spreader(), "Spreader", "axis", [[3, 6, 0, 13, 14.5, 16]])
    fit("cr_spreader_end", spreader_end(), "Spreader", "facing", [[0, 3, 0, 16, 14, 16]])
    fit("cr_festoon", festoon(), "Festoon Cables", "axis", [[5, 12.5, 3, 11, 16, 13]])
    fit("cr_headblock", headblock(), "Hook Block", "axis", [[4, 0, 2, 12, 16, 14]])
    fit("cr_pad", pad(), "Outrigger Jack", "facing", [[0, 0, 0, 16, 16, 16]])
    fit("cr_outrigger", outrigger(), "Outrigger", "axis", [[3, 4, 0, 13, 12, 16]])
    for k in "abc":
        fit(f"bw_armour_{k}", armour(k, "bw_rock"), "Armour Stone", "facing", [[0, 0, 0, 16, 12, 16]])
        fit(f"bw_armour_{k}_weed", armour(k, "bw_rock_weed"), "Armour Stone", "facing", [[0, 0, 0, 16, 12, 16]])
    for n in ("bw_armour_a", "bw_armour_b", "bw_armour_c", "bw_armour_a_weed", "bw_armour_b_weed", "bw_armour_c_weed"):
        out[n]["sound"] = "STONE"
    fit("bw_tetrapod", tetrapod(), "Tetrapod", "facing", [[1, 0, 1, 15, 16, 15]])
    out["bw_tetrapod"]["sound"] = "STONE"
    out["bw_core"] = {"tex": "bw_rock", "title": "Rubble Core", "sound": "STONE"}
    for m, (_, _, snd) in BULK.items():  # a heap's stuff, and its top a quarter, half or three quarters deep
        out["bulk_" + m] = {"tex": "bulk_" + m, "title": m.title(), "sound": snd}
        for n in (1, 2, 3):
            out[f"bulk_{m}_{n}"] = {"model": "boxes", "boxes": [[0, 0, 0, 16, 4 * n, 16, "bulk_" + m]], "title": m.title(), "sound": snd,
                                  "shape": [[0, 0, 0, 16, 4 * n, 16]]}
    out["bw_crest"] = {"tex": {"up": "bw_concrete", "side": "bw_concrete", "down": "bw_rock"}, "title": "Crown Wall", "sound": "STONE"}
    fit("by_stand", stand(), "Boat Stand", "facing", [[6, 0, 1, 10, 16, 10]])
    fit("by_keel", keel_blocks(), "Keel Blocks", "axis", [[1, 0, 1, 15, 13, 15]])
    out["by_keel"]["sound"] = "WOOD"
    fit("tk_pipe", tank_pipe(), "Pipe", "axis", [[4, 0, 0, 12, 9, 16]])
    fit("tk_valve", tank_valve(), "Gate Valve", "axis", [[3, 0, 0, 13, 15, 16]])
    fit("tk_pipes_a", rack_pipes("a"), "Pipe Rack", "axis", [[0, 0, 0, 16, 8, 16]])
    fit("tk_pipes_b", rack_pipes("b"), "Pipe Rack", "axis", [[0, 0, 0, 16, 8, 16]])
    for fam, (half, tex) in MEMBERS.items():
        for k in range(STEPS):
            for sfx, o in (FINE if fam == "cr_stay" else OFFSETS):
                spec = {"model": "boxes", "boxes": member(k, half, tex, o), "title": "Steel Member", "sound": "METAL",
                        "shape": [[8 - min(half, 4), 8 - min(half, 4), 8 - min(half, 4), 8 + min(half, 4), 8 + min(half, 4), 8 + min(half, 4)]]}
                if "lattice" in fam:
                    spec["render"] = "cutout"
                out[f"{fam}_{k}{sfx}"] = spec
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith(("ct_", "cr_", "tk_", "bw_", "bulk_", "by_")) and k not in {"container_" + c for c in COLOURS}}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} container blocks in {path}")


if __name__ == "__main__":
    main()
