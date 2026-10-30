"""The ah_ kit: Grayhaven International's buildings round the field (Josiah, 2026-10-30: "you can build
on and around that"), after refs/airport/arff-fire-station and refs/airport/hangars: the fire
station's bay doors and its lettering, the hangars' sliding doors and their track.
world/county/AirportGrounds sets them out. Everything faces north (rotate=facing) unless said:

- the fire station's sectional doors, a panel in the wall's north face: ah_sect_<c> (two sections
  of ribbed steel a block), ah_sect_<c>_glass (the row of windows), ah_sect_<c>_dent (a section
  stove in where something hit it) for c in white and red; ah_sect_head (the door run up: the
  sections stacked under the ceiling on their tracks, set in the top row of the opening);
- ah_arff_<0..11>: GRAYHAVEN AIRPORT FIRE RESCUE in white on the red fascia over the bays, a panel
  against the wall south of it, tile 0 at the viewer's left; ah_bay_<1..5>: a bay's number in its
  white disc; ah_cargo_<0..9> (GRAYHAVEN AIR CARGO, white on blue) and ah_fuel_<0..9> (AVIATION FUEL,
  red on cream), the same kind of panel along the cargo shed and the fuel farm's pump house;
- the hangar's sliding doors, a leaf a block thick hung from the head: ah_leaf (ribbed grey steel
  with its rails), ah_leaf_band (the bottom row, the yellow and black band along it), ah_leaf_edge
  (the leaf's end: the rubber seal, the pull handle), ah_leaf_num_<n>_<0..8> (the hangar's number
  stencilled across three by three leaves, n 1 to 4); ah_track: the door's rail let into the
  floor, along x (rotate=axis);
- the maintenance hangar's gear: ah_scaf (a bay of galvanised tube scaffold, its brace on the north
  face), ah_scaf_deck (the same with its boards at the top, to stand on), ah_scaf_rail (the guard
  rail and toe board along the deck's north edge, set on the deck block above); ah_engine_0 /
  ah_engine_1 (a spare engine on its yellow transport stand, the intake to the north, two blocks).

    python3 tools/city/airside.py          rewrites the ah_ entries in blocks.json

photo_textures.py draws the textures (airside.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
from furniture import area  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
STEEL, BLACK_METAL, RUBBER = "st_galv", "ext_black_metal", "home_rubber"
RED, WHITE = (176, 34, 30), (228, 228, 222)
DOORS = {"white": WHITE, "red": RED}
HANGARS = 4


# ---------------------------------------------------------------- textures

def painted(seed, rgb, grime=0.25, rust=0.0, smooth=0.45):
    t = F.plain(seed, rgb, 0.04, smooth)
    t.f0[:] = 30
    t.streaks(0.2, 0.85)
    t.grime(grime, (60, 54, 44))
    if rust:
        t.speckle(rust, (120, 66, 36), 0.7, -0.05)
    return t


def sections(t, glass=False, dent=False):
    """Two sections a block, each with its fine ribs and the joint between; the windows along the top
    one when glass, a stove-in dent across the bottom one when dent."""
    for y0 in (0, 32):
        area(t, 0, y0, N, y0 + 2, mul=(0.6, 0.6, 0.6), dh=-0.4)
        area(t, 0, y0 + 2, N, y0 + 3, mul=(1.15, 1.15, 1.15), dh=0.1)
        for y in range(y0 + 7, y0 + 32, 6):
            area(t, 0, y, N, y + 1, mul=(0.9, 0.9, 0.9), dh=-0.1)
    if glass:
        for x0 in (4, 36):
            area(t, x0, 6, x0 + 24, 26, col=(70, 70, 72), dh=0.1)
            area(t, x0 + 2, 8, x0 + 22, 24, col=(46, 58, 66), h=0.6, smooth=0.9, f0=80)
            area(t, x0 + 3, 9, x0 + 9, 13, mul=(1.5, 1.6, 1.7))
    if dent:
        yy, xx = np.mgrid[0:N, 0:N]
        d = np.exp(-(((xx - 30) / 16.0) ** 2 + ((yy - 46) / 8.0) ** 2))
        t.h -= d * 0.5
        t.c *= (1 - d * 0.35)[..., None]
        t.speckle(0.04, (130, 130, 128), 0.6)
    return t


def sect(seed, rgb, glass=False, dent=False):
    t = painted(seed, rgb, 0.3, 0.01)
    return sections(t, glass, dent)


def leaf(seed, band=False):
    """The hangar door's skin: grey steel in ribs every eight, the rails across, rust run down from
    the bolts; the yellow and black band along the bottom when band."""
    t = painted(seed, (132, 138, 140), 0.35, 0.02)
    for x in range(0, N, 8):
        area(t, x, 0, x + 2, N, mul=(0.75, 0.75, 0.75), dh=-0.3)
        area(t, x + 2, 0, x + 3, N, mul=(1.12, 1.12, 1.12), dh=0.1)
    area(t, 0, 30, N, 34, mul=(0.85, 0.85, 0.85), dh=0.2)
    for x in range(4, N, 16):
        area(t, x, 31, x + 2, 33, col=(70, 70, 70))
        area(t, x, 33, x + 2, 44, mul=(1.0, 0.82, 0.7))
    if band:
        yy, xx = np.mgrid[48:N, 0:N]
        black = ((xx + yy) // 8) % 2 == 0
        t.c[48:, :] = np.where(black[..., None], np.array((30, 30, 30), np.float32), np.array((214, 170, 36), np.float32))
        t.grime(0.2, (60, 54, 44))
    return t


def leaf_edge(seed):
    t = leaf(seed)
    area(t, 54, 0, N, N, col=(26, 26, 26), dh=0.2, smooth=0.2)
    area(t, 40, 24, 46, 40, col=(150, 150, 146), dh=0.4, smooth=0.6, f0=120)
    return t


def track(seed):
    t = P.Tex(seed, (128, 126, 120))
    t.vary(0.08)
    t.speckle(0.06, (90, 88, 84), 0.4)
    area(t, 0, 26, N, 38, col=(70, 70, 70), dh=-0.2)
    area(t, 0, 30, N, 34, col=(150, 146, 140), h=0.85, smooth=0.6, f0=120)
    t.grime(0.25, (50, 44, 36))
    return t


@lru_cache(maxsize=None)
def arff_pic():
    """The station's name in white on the red fascia, one row of twelve."""
    p = Pic(12, 1, RED, 31700)
    W = p.W
    text = "GRAYHAVEN AIRPORT  FIRE · RESCUE"
    size = civic.fit(text, BOLD, 36, W - 40)
    p.rect(0, 0, W, 3, fill=(230, 230, 224))
    p.rect(0, N - 3, W, N, fill=(230, 230, 224))
    p.text(W / 2, 16, text, BOLD, size, (240, 240, 236), anchor="ma")
    return p


def arff(i):
    return arff_pic().tile(i, 31720 + i, smooth=0.45, grime=0.25)


# the other buildings' fascia names: key -> (text, tiles, ground, letters)
BANDS = {"cargo": ("GRAYHAVEN  AIR  CARGO", 10, (30, 62, 120), (240, 240, 236)),
         "fuel": ("AVIATION  FUEL  \u00b7  NO  SMOKING", 10, (232, 228, 214), (168, 30, 28))}


@lru_cache(maxsize=None)
def band_pic(k):
    text, n, bg, ink = BANDS[k]
    p = Pic(n, 1, bg, 31600 + n)
    W = p.W
    size = civic.fit(text, BOLD, 36, W - 40)
    p.text(W / 2, 16, text, BOLD, size, ink, anchor="ma")
    return p


def band(k, i):
    return band_pic(k).tile(i, 31620 + i + 20 * list(BANDS).index(k), smooth=0.45, grime=0.25)


@lru_cache(maxsize=None)
def bay_pic(n):
    p = Pic(1, 1, RED, 31750 + n)
    p.ellipse(8, 8, N - 8, N - 8, fill=(236, 236, 230))
    p.text(N / 2, 12, str(n), BOLD, 34, (150, 26, 24), anchor="ma")
    return p


def bay(n):
    return bay_pic(n).tile(0, 31760 + n, smooth=0.45, grime=0.2)


@lru_cache(maxsize=None)
def num_pic(n):
    """A hangar's number stencilled in white across three by three leaves, the paint flaking."""
    p = Pic(3, 3, (0, 0, 0, 0), 31800 + n)
    W = p.W
    p.text(W / 2, 4, str(n), BOLD, 168, (232, 232, 226), anchor="ma")
    return p


def leaf_num(n, i):
    t = leaf(31810 + n * 10 + i)
    a = num_pic(n).shrunk()
    cx, cy = (i % 3) * N, (i // 3) * N
    ink = a[cy:cy + N, cx:cx + N, 3:4] / 255.0
    ink = ink * (t.rng.random((N, N, 1)) > 0.12)
    t.c = t.c * (1 - ink) + a[cy:cy + N, cx:cx + N, :3] * ink
    return t


def boards(seed):
    """Scaffold boards: grey-brown planks along x, their ends banded yellow, boot-scuffed."""
    t = F.plain(seed, (128, 112, 88), 0.08, 0.25)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=(0.55, 0.55, 0.55), dh=-0.3)
    area(t, 0, 0, 5, N, col=(206, 168, 40))
    area(t, N - 5, 0, N, N, col=(206, 168, 40))
    t.grime(0.35, (60, 54, 44))
    return t


def cowl(seed):
    """The bare engine's cowl: brushed metal in panels, the fasteners in rows, stencils."""
    t = F.plain(seed, (176, 178, 180), 0.04, 0.6)
    t.f0[:] = 120
    for x in range(0, N, 21):
        area(t, x, 0, x + 1, N, mul=(0.6, 0.6, 0.6), dh=-0.2)
        for y in range(3, N, 6):
            area(t, x + 2, y, x + 3, y + 1, col=(110, 110, 110))
    area(t, 26, 40, 50, 44, col=(200, 40, 30))
    t.grime(0.2, (50, 46, 40))
    return t


def textures():
    out = {
        "ah_boards": lambda: boards(31906),
        "ah_cowl": lambda: cowl(31907),
        "ah_yellow": lambda: painted(31908, (220, 176, 34), 0.35, 0.03),
        "ah_leaf": lambda: leaf(31901),
        "ah_leaf_band": lambda: leaf(31902, True),
        "ah_leaf_edge": lambda: leaf_edge(31903),
        "ah_track": lambda: track(31904),
        "ah_sect_back": lambda: sections(painted(31905, (200, 200, 196), 0.2)),
    }
    for k, (c, rgb) in enumerate(DOORS.items()):
        out[f"ah_sect_{c}"] = lambda rgb=rgb, k=k: sect(31910 + k * 4, rgb)
        out[f"ah_sect_{c}_glass"] = lambda rgb=rgb, k=k: sect(31911 + k * 4, rgb, glass=True)
        out[f"ah_sect_{c}_dent"] = lambda rgb=rgb, k=k: sect(31912 + k * 4, rgb, dent=True)
    for i in range(12):
        out[f"ah_arff_{i}"] = lambda i=i: arff(i)
    for n in range(1, 6):
        out[f"ah_bay_{n}"] = lambda n=n: bay(n)
    for n in range(1, HANGARS + 1):
        for i in range(9):
            out[f"ah_leaf_num_{n}_{i}"] = lambda n=n, i=i: leaf_num(n, i)
    for k, (_, n, _, _) in BANDS.items():
        for i in range(n):
            out[f"ah_{k}_{i}"] = lambda k=k, i=i: band(k, i)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def faces(tex, side=None, **over):
    """Every face tex; side= the four sides, and those named (up=, north=, ...) their own."""
    f = {d: tex for d in ("north", "south", "east", "west", "up", "down")}
    if side:
        f.update({d: side for d in ("north", "south", "east", "west")})
    f.update({k: v for k, v in over.items() if v})
    return f


def sect_panel(face):
    """A door panel in the wall's north face, the back of the sections and the side tracks inside."""
    return [[0, 0, 0, 16, 16, 2, faces(face, south="ah_sect_back")],
            [0, 0, 2, 1, 16, 3.5, STEEL], [15, 0, 2, 16, 16, 3.5, STEEL]]


def sect_head():
    """The door run up: the sections lying flat under the ceiling on their rails, the drum at the front."""
    out = [[0, 13, 0, 16, 16, 3, STEEL]]
    for z in range(3, 16, 4):
        out.append([0.5, 13.2, z, 15.5, 14, z + 4, faces("ah_sect_back", down="ah_sect_white")])
    out += [[0, 14, 0, 1, 15, 16, STEEL], [15, 14, 0, 16, 15, 16, STEEL]]
    return out


def fascia(face):
    """A panel against the wall behind it (the block's south side), its face to the north."""
    return [[0, 0, 14.5, 16, 16, 16, faces(BLACK_METAL, north=face)]]


def leaf_piece(face):
    """A leaf of the hangar door, a block thick, its rollers' guide along the top."""
    return [[0, 0, 2, 16, 16, 14, faces(face, south=face, up=STEEL, down=STEEL, east=STEEL, west=STEEL)]]


def leaf_end():
    return [[0, 0, 2, 16, 16, 14, faces("ah_leaf_edge", south="ah_leaf_edge", up=STEEL, down=STEEL, east=RUBBER, west=STEEL)],
            [16, 4, 6, 16.1, 10, 10, STEEL]]


def track_piece():
    return [[0, 0, 0, 16, 16, 16, faces("tx_concrete", up="ah_track")]]


def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


def posts():
    t = 1.2
    return [[0, 0, 0, t, 16, t, STEEL], [16 - t, 0, 0, 16, 16, t, STEEL],
            [0, 0, 16 - t, t, 16, 16, STEEL], [16 - t, 0, 16 - t, 16, 16, 16, STEEL]]


def scaf(deck=False):
    """A bay of tube scaffold: the four standards, the ledgers along x at the top, the transoms along
    z, the brace across the north face; the boards on top when deck."""
    out = posts()
    out += [[0, 14.6, 0, 16, 15.6, 1, STEEL], [0, 14.6, 15, 16, 15.6, 16, STEEL],
            [0, 14.6, 0, 1, 15.6, 16, STEEL], [15, 14.6, 0, 16, 15.6, 16, STEEL]]
    out.append([-3, 7.5, 0.1, 19, 8.5, 1.1, STEEL, None, turn("z", 45, (8, 8, 0.6))])
    if deck:
        out.append([0.2, 15.6, 0.2, 15.8, 16, 15.8, faces("ah_boards", side=STEEL)])
    return out


def scaf_rail():
    """The guard rail on the deck's north edge: the standards carried up, the rails, the toe board."""
    return [[0, 0, 0, 1.2, 16, 1.2, STEEL], [14.8, 0, 0, 16, 16, 1.2, STEEL],
            [0, 14.5, 0, 16, 15.5, 1, STEEL], [0, 8, 0, 16, 9, 1, STEEL],
            [0, 0, 0.2, 16, 2.5, 1.2, faces("ah_yellow")]]


def engine_piece(front):
    """A spare engine lying along z on its stand: the cowl an octagon, the intake's lip and fan at
    the front (north), the core and the nozzle at the back; the yellow cradle under it."""
    cy, r = 9.5, 6.2
    a = r * 0.4142
    z0, z1 = (1, 16) if front else (0, 12)
    rr = r if front else r * 0.8
    aa = rr * 0.4142
    t = turn("z", 45, (8, cy, 8))
    body = [[8 - rr, cy - aa, z0, 8 + rr, cy + aa, z1, faces("ah_cowl", north="ah_cowl", south="ah_cowl")],
            [8 - aa, cy - rr, z0, 8 + aa, cy + rr, z1, faces("ah_cowl")]]
    out = body + [q + [None, t] for q in body]
    if front:
        out += [[8 - r - 0.3, cy - a - 0.3, 0, 8 + r + 0.3, cy + a + 0.3, 1.2, "home_chrome"],
                [8 - a - 0.3, cy - r - 0.3, 0, 8 + a + 0.3, cy + r + 0.3, 1.2, "home_chrome"],
                [8 - r + 0.8, cy - 1, 1.1, 8 + r - 0.8, cy + 1, 1.3, BLACK_METAL],
                [7, cy - r + 0.8, 1.1, 9, cy + r - 0.8, 1.3, BLACK_METAL],
                [6.8, cy - 1.2, 0.4, 9.2, cy + 1.2, 1.2, "home_chrome"]]
    else:
        out += [[8 - 2.5, cy - 2.5, 12, 8 + 2.5, cy + 2.5, 15.5, BLACK_METAL],
                [8 - 1.2, cy - 1.2, 15.5, 8 + 1.2, cy + 1.2, 16, "home_chrome"]]
    # the cradle: two saddles, the rails, the castors
    for z in ((4, 6), (11, 13)):
        out.append([2, 1.5, z[0], 14, 4.5, z[1], faces("ah_yellow")])
    out += [[1.5, 0.8, 0, 3, 2, 16, faces("ah_yellow")], [13, 0.8, 0, 14.5, 2, 16, faces("ah_yellow")],
            [1.5, 0, 1, 3, 0.8, 3, RUBBER], [13, 0, 1, 14.5, 0.8, 3, RUBBER],
            [1.5, 0, 13, 3, 0.8, 15, RUBBER], [13, 0, 13, 14.5, 0.8, 15, RUBBER]]
    return out


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="METAL", cutout=False, light=0, decor=False):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape}
        if rotate:
            spec["rotate"] = rotate
        if cutout:
            spec["render"] = "cutout"
        if light:
            spec["light"] = light
        if decor:
            spec["decor"] = True
        out[name] = spec

    panel = [[0, 0, 0, 16, 16, 3.5]]
    for c in DOORS:
        add(f"ah_sect_{c}", sect_panel(f"ah_sect_{c}"), "Bay Door", panel)
        add(f"ah_sect_{c}_glass", sect_panel(f"ah_sect_{c}_glass"), "Bay Door", panel)
        add(f"ah_sect_{c}_dent", sect_panel(f"ah_sect_{c}_dent"), "Bay Door", panel)
    add("ah_sect_head", sect_head(), "Bay Door", [[0, 13, 0, 16, 16, 16]])
    for i in range(12):
        add(f"ah_arff_{i}", fascia(f"ah_arff_{i}"), "Fire Station Sign", [[0, 0, 14.5, 16, 16, 16]])
    for n in range(1, 6):
        add(f"ah_bay_{n}", fascia(f"ah_bay_{n}"), "Bay Number", [[0, 0, 14.5, 16, 16, 16]])
    for k, (_, n, _, _) in BANDS.items():
        for i in range(n):
            add(f"ah_{k}_{i}", fascia(f"ah_{k}_{i}"), "Sign", [[0, 0, 14.5, 16, 16, 16]])
    hull = [[0, 0, 2, 16, 16, 14]]
    add("ah_leaf", leaf_piece("ah_leaf"), "Hangar Door", hull)
    add("ah_leaf_band", leaf_piece("ah_leaf_band"), "Hangar Door", hull)
    add("ah_leaf_edge", leaf_end(), "Hangar Door", hull)
    for n in range(1, HANGARS + 1):
        for i in range(9):
            add(f"ah_leaf_num_{n}_{i}", leaf_piece(f"ah_leaf_num_{n}_{i}"), "Hangar Door", hull)
    add("ah_track", track_piece(), "Door Track", [[0, 0, 0, 16, 16, 16]], rotate="axis", sound="STONE")
    add("ah_scaf", scaf(), "Scaffold", [[0, 0, 0, 16, 16, 16]], cutout=True, decor=True)
    add("ah_scaf_deck", scaf(True), "Scaffold", [[0, 14.6, 0, 16, 16, 16]], cutout=True)
    add("ah_scaf_rail", scaf_rail(), "Scaffold Rail", [[0, 0, 0, 16, 16, 1.2]], cutout=True)
    add("ah_engine_0", engine_piece(True), "Spare Engine", [[1.5, 0, 0, 14.5, 16, 16]])
    add("ah_engine_1", engine_piece(False), "Spare Engine", [[1.5, 0, 0, 14.5, 16, 16]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("ah_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} airside blocks in {path}")


if __name__ == "__main__":
    main()
