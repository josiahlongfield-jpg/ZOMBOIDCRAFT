"""The hk_ kit: the parts of a house that were still vanilla blocks, built to the landmarks' detail
(Josiah, 2026-10-29: "Everything in this world must be of the same level of detail and effort").
world/county/House.java puts them in.

- hk_cabin_log: a log cabin's wall, two round logs a block with the chinking between them, the
  logs along the wall's row (rotate=axis); hk_cabin_corner: the corner where the walls cross, the
  lower log along x and the upper along z, their sawn ends showing;
- hk_ridge_beam: the hewn ridge beam under an attic's roof, along its row;
- hk_pot: a terracotta pot on its saucer by the front step, what was planted in it long dead;
- the big houses' grounds: hk_pier_cap / hk_pier_lamp (the cast-stone cap and ball, or the dead
  globe lamp, on a gate pier), the dry fountain (hk_basin under it all, hk_basin_rim along each
  side and hk_basin_corner round the north-west corner, facing; hk_fountain_base and
  hk_fountain_top the pedestal and its bowl), the in-ground pool's hk_diving_board and
  hk_pool_ladder (facing = toward the water), the tennis court (hk_court, hk_court_moss,
  hk_court_line along its row, hk_court_cross, hk_tennis_net along its row, hk_net_post) and the
  formal garden's hk_birdbath.

    python3 tools/city/housekit.py      rewrites the hk_ entries in blocks.json

photo_textures.py draws the textures (housekit.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from yard import along_z, turn, white_paint  # noqa: E402

N = P.N
HERE = Path(__file__).parent


# ---------------------------------------------------------------- textures

def log_side(seed):
    """Weathered pine logs: the grain along the log, silvered, dark where the rain runs off."""
    t = F.mat("wood_trunk_wall", (112, 96, 80), 1.2, rot=True, seed=seed)
    t.grime(0.2, (50, 42, 34))
    t.speckle(0.006, (30, 26, 20), 0.6)
    return t


def charred(seed):
    """A log the fire got to: blackened, the surface crazed into alligator checks."""
    t = log_side(seed)
    n = np.clip(P.fbm(t.rng) * 0.8 + 0.75, 0, 1)
    t.mix(n, (26, 22, 20), 0.9)
    t.crack(10, 0.3, (8, 6, 6))
    t.speckle(0.03, (90, 80, 72), 0.4)
    t.smooth[:] = 0.08
    return t


def chink(seed):
    """Lime chinking between the logs, cracked and fallen out in places."""
    t = F.mat("rough_plaster_broken", (196, 190, 176), 0.9, seed=seed)
    t.grime(0.35, (90, 84, 72))
    t.crack(4, 0.2, (60, 54, 46))
    return t


def timber(seed):
    """An adzed beam: dry, dusty, cobwebbed grey on top."""
    t = F.mat("rough_wood", (120, 96, 70), 1.1, seed=seed)
    t.grime(0.25, (70, 66, 60))
    return t


def terracotta(seed):
    t = F.plain(seed, (176, 98, 64), 0.08, 0.15)
    t.grime(0.3, (110, 100, 86))
    yy = np.mgrid[0:N, 0:N][0].astype(np.float32)
    t.mix(np.clip((yy - 40) / 24, 0, 1) * 0.5, (210, 206, 190))  # lime bloom at the foot
    t.speckle(0.01, (90, 60, 40), 0.4)
    return t


def soil(seed):
    t = F.plain(seed, (58, 44, 32), 0.2, 0.05)
    t.speckle(0.05, (110, 96, 74), 0.5, 0.05)
    return t


def stalk(seed):
    t = F.plain(seed, (118, 96, 60), 0.15, 0.1)
    t.speckle(0.08, (70, 56, 38), 0.6)
    return t


def porch_skirt(seed):
    """The side of a porch deck: the white fascia board along the top, the lattice skirting under it
    (slats crossing on the diagonal, dark under the porch behind), mud splashed up its foot."""
    t = white_paint(seed)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    band = N * 0.2
    pitch, w = N / 4, N / 22
    a = np.abs(((xx + yy) % pitch) - pitch / 2) < w
    b = np.abs(((xx - yy) % pitch) - pitch / 2) < w
    slat = (a | b) & (yy > band + 1)
    hole = ~slat & (yy > band + 1)
    t.c[hole] = np.array((24, 22, 20), np.float32) * (0.8 + 0.4 * t.rng.random((int(hole.sum()), 1)))
    t.h[hole] = 0.3
    t.h[b & ~a & (yy > band + 1)] -= 0.06  # the back layer of slats
    t.smooth[hole] = 0.05
    t.c[(yy > band) & (yy <= band + 1)] *= 0.55  # the shadow line under the fascia
    t.mix(np.clip((yy - N * 0.78) / (N * 0.22), 0, 1) * (P.fbm(t.rng) * 0.5 + 0.6), (78, 64, 48), 0.7)
    return t


def court(seed, moss):
    """Acrylic court surfacing gone chalky and pale, the asphalt's cracks coming through it."""
    t = F.mat("Asphalt031", (74, 112, 84), 0.55, seed=seed)
    t.vary(0.06)
    t.crack(6 if moss else 3, 0.25, (40, 46, 38))
    if moss:
        t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.3, 0, 1), (70, 96, 40), 0.8)
        t.speckle(0.03, (110, 130, 60), 0.6, 0.05)
    return t


def line_paint(seed):
    t = F.plain(seed, (222, 224, 214), 0.05, 0.25)
    t.speckle(0.06, (120, 140, 120), 0.5)
    return t


def net(seed):
    """Polyester netting, a square every four texels; the light shows through the rest."""
    t = F.plain(seed, (34, 36, 34), 0.1, 0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    t.alpha[:] = np.where((xx % 4 == 0) | (yy % 4 == 0), 255, 0)
    torn = (np.abs(xx - 40) < 6) & (yy > 30)
    t.alpha[torn & (t.rng.random((N, N)) < 0.8)] = 0
    return t


def tape(seed):
    t = F.mat("hessian_230", (214, 210, 196), 0.6, seed=seed)
    t.grime(0.3, (110, 104, 90))
    return t


def basin(seed):
    """A fountain's bowl left dry: a black tide line, wet leaves rotting in the bottom."""
    t = F.mat("Concrete031", (138, 134, 124), 0.9, seed=seed)
    t.grime(0.5, (52, 50, 44))
    for col in ((112, 70, 36), (140, 98, 44), (74, 52, 32)):
        t.speckle(0.08, col, 0.85, 0.04)
    return t


def board(seed):
    """A fibreglass diving board, the grit strip worn to the white."""
    t = F.plain(seed, (196, 204, 200), 0.05, 0.4)
    t.speckle(0.25, (150, 160, 160), 0.4, 0.02)
    t.grime(0.2, (100, 110, 96))
    return t


def textures():
    return {
        "hk_log": lambda: log_side(49200),
        "hk_chink": lambda: chink(49201),
        "hk_log_soot": lambda: charred(49207),
        "hk_timber": lambda: timber(49202),
        "hk_terracotta": lambda: terracotta(49203),
        "hk_soil": lambda: soil(49204),
        "hk_stalk": lambda: stalk(49205),
        "hk_porch_skirt": lambda: porch_skirt(49206),
        "hk_court": lambda: court(49208, False),
        "hk_court_moss": lambda: court(49209, True),
        "hk_court_paint": lambda: line_paint(49210),
        "hk_net": lambda: net(49211),
        "hk_net_tape": lambda: tape(49212),
        "hk_basin": lambda: basin(49213),
        "hk_board": lambda: board(49214),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

LOG = {"north": "hk_log", "south": "hk_log", "up": "hk_log", "down": "hk_log", "east": "bark_top", "west": "bark_top"}


def log_x(y, x0=0, x1=16):
    """One log along x, its middle at height y + 4: the body and the bulge that rounds it."""
    return [[x0, y + 0.8, 1, x1, y + 7.2, 15, LOG], [x0, y + 2, 0, x1, y + 6, 16, LOG]]


def log_z(y, z0=0, z1=16):
    return [[1, y + 0.8, z0, 15, y + 7.2, z1, {"east": "hk_log", "west": "hk_log", "up": "hk_log", "down": "hk_log",
                                                 "north": "bark_top", "south": "bark_top"}],
            [0, y + 2, z0, 16, y + 6, z1, {"east": "hk_log", "west": "hk_log", "up": "hk_log", "down": "hk_log",
                                           "north": "bark_top", "south": "bark_top"}]]


def cabin_log(soot=False):
    """Two logs along x and the chinking pressed in between, under and over them."""
    b = log_x(0) + log_x(8)
    if soot:
        b = [q[:6] + [{k: ("hk_log_soot" if v == "hk_log" else v) for k, v in q[6].items()}] for q in b]
    for y in (0, 7, 15.2):
        b.append([0, y, 2.2, 16, y + (0.8 if y != 7 else 2), 13.8, "hk_chink"])
    return b


def cabin_corner():
    """The lower log along x, the upper along z, each running through and showing its sawn end."""
    return log_x(0) + log_z(8) + [[1.6, 7, 1.6, 14.4, 9, 14.4, "hk_chink"]]


def ridge_beam():
    """An 8 by 10 inch hewn beam along x, an iron strap where two lengths were scarfed together."""
    return [[0, 5, 5, 16, 14, 11, "hk_timber"],
            [7, 4.6, 4.6, 9, 14.4, 11.4, "ext_galv"]]


def pot():
    """A terracotta pot on its saucer, the rim rolled, the stalks of what grew in it."""
    b = [[4.5, 0, 4.5, 11.5, 0.8, 11.5, "hk_terracotta"],
         [5.5, 0.8, 5.5, 10.5, 6, 10.5, "hk_terracotta"],
         [5, 6, 5, 11, 7.2, 11, "hk_terracotta"],
         [5.8, 6.4, 5.8, 10.2, 6.9, 10.2, "hk_soil"]]
    for x, z, a, ax in ((7, 7, 22.5, "x"), (8.6, 8, -22.5, "z"), (8, 9, 0, "x"), (7.4, 8.6, -45, "x")):
        box = [x, 6.9, z, x + 0.5, 13, z + 0.5, "hk_stalk"]
        if a:
            box += [None, turn(ax, a, (x + 0.25, 6.9, z + 0.25))]
        b.append(box)
    return b


STONE, IRON, GLOBE, CHROME = "stone_trim", "front_iron", "cv_globe", "home_chrome"


def ball(y, r, tex, cx=8, cz=8):
    """A ball finial of radius r from height y: three crossed boxes."""
    return [[cx - r, y + r * 0.3, cz - r * 0.7, cx + r, y + r * 1.7, cz + r * 0.7, tex],
            [cx - r * 0.7, y + r * 0.3, cz - r, cx + r * 0.7, y + r * 1.7, cz + r, tex],
            [cx - r * 0.75, y, cz - r * 0.75, cx + r * 0.75, y + r * 2, cz + r * 0.75, tex]]


def pier_cap(lamp):
    """The cap on a gate pier: an overhanging weathered slab, a plinth, and a stone ball or a lamp."""
    b = [[-1, 0, -1, 17, 2.2, 17, STONE], [0, 2.2, 0, 16, 3, 16, STONE], [2.5, 3, 2.5, 13.5, 4.6, 13.5, STONE]]
    if lamp:
        b += [[6, 4.6, 6, 10, 6, 10, IRON], [7, 6, 7, 9, 7, 9, IRON],
              [4, 7, 5.2, 12, 14, 10.8, GLOBE], [5.2, 7, 4, 10.8, 14, 12, GLOBE],
              [6.5, 14, 6.5, 9.5, 15.2, 9.5, IRON], [7.5, 15.2, 7.5, 8.5, 16.5, 8.5, IRON]]
    else:
        b += [[6.5, 4.6, 6.5, 9.5, 5.4, 9.5, STONE]] + ball(5.4, 3.6, STONE)
    return b


def basin_rim(corner=False):
    """The kerb of a fountain's basin along its north side: the wall, the rounded coping on it."""
    b = [[0, 0, 0, 16, 7, 4.5, STONE], [0, 7, -0.5, 16, 8.4, 5, STONE]]
    if corner:
        b += [[0, 0, 0, 4.5, 7, 16, STONE], [-0.5, 7, 0, 5, 8.4, 16, STONE]]
    return b


def fountain_base():
    """The pedestal: a square plinth, a fluted shaft (four reeds), the collar under the bowl."""
    b = [[2.5, 0, 2.5, 13.5, 2.5, 13.5, STONE], [4, 2.5, 4, 12, 3.5, 12, STONE], [5.5, 3.5, 5.5, 10.5, 14, 10.5, STONE]]
    for x, z in ((5.1, 7.4), (10.1, 7.4), (7.4, 5.1), (7.4, 10.1)):
        b.append([x, 3.5, z, x + 0.8, 14, z + 0.8, STONE])
    b += [[4.5, 14, 4.5, 11.5, 16, 11.5, STONE]]
    return b


def fountain_top():
    """The bowl, wide as the pedestal's cell, its lip over the edge; leaves in it, an urn on top."""
    b = [[4, 0, 4, 12, 1, 12, STONE], [1, 1, 1, 15, 2.5, 15, STONE], [-1, 2.5, -1, 17, 4.6, 17, STONE],
         [0, 4.4, 0, 16, 4.6, 16, "hk_basin"],
         [6.5, 4.6, 6.5, 9.5, 6, 9.5, STONE], [5.5, 6, 5.5, 10.5, 10, 10.5, STONE], [5, 10, 5, 11, 11, 11, STONE],
         [6.8, 11, 6.8, 9.2, 12, 9.2, STONE]]
    return b


def diving_board():
    """A diving board on its stand, the board running out north over the water."""
    b = [[4.5, 0, 9, 11.5, 1, 16, "ext_galv"], [5, 1, 10, 6, 6, 15, "ext_galv"], [10, 1, 10, 11, 6, 15, "ext_galv"],
         [5, 5, 9.5, 11, 6, 15.5, "ext_galv"], [5, 6, -14, 11, 6.8, 16, "hk_board"]]
    return b


def pool_ladder():
    """Two chrome rails arching over the coping and down into the water to the north, three treads."""
    b = []
    for x in (3.5, 11.5):
        b += [[x, 0, 6, x + 1, 11, 7, CHROME], [x, 10, -2.5, x + 1, 11, 7, CHROME], [x, -15, -3.5, x + 1, 11, -2.5, CHROME],
              [x - 0.5, 0, 5.5, x + 1.5, 0.6, 7.5, CHROME]]
    for y in (-3.5, -8.5, -13.5):
        b.append([4.5, y, -4.5, 11.5, y + 0.8, -2, "ext_galv"])
    return b


def court_line(cross=False):
    """Court surfacing with a painted line along x through it (and one along z for a crossing), the
    paint its own strip of the block so it lies flush."""
    paint = {"up": "hk_court_paint", "down": "hk_court", "north": "hk_court", "south": "hk_court",
             "east": "hk_court", "west": "hk_court"}
    if not cross:
        return [[0, 0, 0, 16, 16, 7.3, "hk_court"], [0, 0, 7.3, 16, 16, 8.7, paint], [0, 0, 8.7, 16, 16, 16, "hk_court"]]
    b = [[0, 0, 7.3, 16, 16, 8.7, paint], [7.3, 0, 0, 8.7, 16, 7.3, paint], [7.3, 0, 8.7, 8.7, 16, 16, paint]]
    for x0, x1 in ((0, 7.3), (8.7, 16)):
        for z0, z1 in ((0, 7.3), (8.7, 16)):
            b.append([x0, 0, z0, x1, 16, z1, "hk_court"])
    return b


def tennis_net():
    """The net along x, its top a band of tape, the cable through it; a slash in one panel."""
    return [[0, 0, 7.9, 16, 14, 8.1, "hk_net"], [0, 13, 7.7, 16, 14.6, 8.3, "hk_net_tape"],
            [0, 14.2, 7.85, 16, 14.5, 8.15, "ext_galv"]]


def net_post():
    return [[6.5, 0, 6.5, 9.5, 17, 9.5, "painted_steel"], [6, 16.6, 6, 10, 17.4, 10, "painted_steel"],
            [9.5, 8, 7.5, 12.5, 9, 8.5, "painted_steel"], [12, 6.5, 7.5, 12.6, 9, 8.5, "painted_steel"]]


def birdbath():
    return [[4.5, 0, 4.5, 11.5, 1.5, 11.5, STONE], [6.3, 1.5, 6.3, 9.7, 9, 9.7, STONE], [5.5, 9, 5.5, 10.5, 10, 10.5, STONE],
            [2, 10, 2, 14, 11, 14, STONE], [1.5, 11, 1.5, 14.5, 12.4, 14.5, STONE], [2.5, 12.1, 2.5, 13.5, 12.3, 13.5, "hk_basin"]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate=None, decor=False, **extra):
        if rotate == "axis":
            boxes = along_z(boxes)
            if "shape" in extra:
                extra["shape"] = [q[:6] for q in along_z([list(r) + [None] for r in extra["shape"]])]
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    add("hk_cabin_log", cabin_log(), "Log Wall", "WOOD", rotate="axis")
    add("hk_cabin_log_soot", cabin_log(True), "Log Wall", "WOOD", rotate="axis")
    add("hk_cabin_corner", cabin_corner(), "Log Wall", "WOOD")
    add("hk_ridge_beam", ridge_beam(), "Ridge Beam", "WOOD", rotate="axis", shape=[[0, 5, 5, 16, 14, 11]])
    add("hk_pot", pot(), "Flower Pot", "PACKED_MUD", decor=True, shape=[[4.5, 0, 4.5, 11.5, 7.2, 11.5]])
    add("hk_pier_cap", pier_cap(False), "Pier Cap", "STONE", shape=[[-1, 0, -1, 17, 12, 17]])
    add("hk_pier_lamp", pier_cap(True), "Pier Lamp", "STONE", shape=[[-1, 0, -1, 17, 16, 17]])
    out["hk_basin"] = {"tex": {"up": "hk_basin", "down": "stone_trim", "side": "stone_trim"}, "title": "Fountain Basin", "sound": "STONE"}
    add("hk_basin_rim", basin_rim(), "Fountain Basin", "STONE", rotate="facing", shape=[[0, 0, 0, 16, 8.4, 5]])
    add("hk_basin_corner", basin_rim(True), "Fountain Basin", "STONE", rotate="facing",
        shape=[[0, 0, 0, 16, 8.4, 5], [0, 0, 0, 5, 8.4, 16]])
    add("hk_fountain_base", fountain_base(), "Fountain", "STONE", shape=[[2.5, 0, 2.5, 13.5, 16, 13.5]])
    add("hk_fountain_top", fountain_top(), "Fountain", "STONE", shape=[[1, 0, 1, 15, 12, 15]])
    add("hk_diving_board", diving_board(), "Diving Board", "METAL", rotate="facing", shape=[[4.5, 0, 9, 11.5, 6.8, 16]])
    add("hk_pool_ladder", pool_ladder(), "Pool Ladder", "METAL", rotate="facing", decor=True)
    out["hk_court"] = {"tex": "hk_court", "title": "Court Surface", "sound": "STONE"}
    out["hk_court_moss"] = {"tex": "hk_court_moss", "title": "Court Surface", "sound": "STONE"}
    add("hk_court_line", court_line(), "Court Surface", "STONE", rotate="axis", shape=[[0, 0, 0, 16, 16, 16]])
    add("hk_court_cross", court_line(True), "Court Surface", "STONE", shape=[[0, 0, 0, 16, 16, 16]])
    add("hk_tennis_net", tennis_net(), "Tennis Net", "WOOL", rotate="axis", render="cutout", shape=[[0, 0, 7.7, 16, 14.6, 8.3]])
    add("hk_net_post", net_post(), "Net Post", "METAL", shape=[[6.5, 0, 6.5, 9.5, 17, 9.5]])
    add("hk_birdbath", birdbath(), "Birdbath", "STONE", shape=[[1.5, 0, 1.5, 14.5, 12.4, 14.5]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("hk_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} house kit blocks in {path}")


if __name__ == "__main__":
    main()
