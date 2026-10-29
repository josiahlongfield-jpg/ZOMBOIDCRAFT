"""The bd_ kit: what dresses the county's bigger buildings (Bldg.java) so a plain shell reads like the
town photos (refs/town/main-street-exterior, refs/city/mall-exterior-parking): stone or precast
coping on the parapet instead of a full block, brick chimney stacks on the party walls with their
pots, rooftop units, mushroom vents, a roof hatch, drains, and stone lintels over the windows.

- bd_coping / bd_coping_pc: stone or precast coping along a parapet, overhanging both faces with a
  drip; _corner where two runs meet (never turned);
- bd_chimney_{red,dark,buff} / bd_chimney_top_{...}: a stack on the parapet, its corbelled top course
  and two clay pots;
- bd_hvac_a / bd_hvac_b: a packaged rooftop unit two blocks long on its curb rails (a: the condenser
  end, coil guards and the fan on top; b: the supply end, service doors and the rain hood), the pair
  running along +x as drawn;
- bd_vent: a galvanised mushroom exhaust on its curb; bd_hatch: the roof hatch on its kerb;
  bd_drain: the roof drain's dome strainer; bd_lintel: a stone lintel over a window, outside the
  wall like the sill.

    python3 tools/city/dress.py          rewrites the bd_ entries in blocks.json

photo_textures.py draws the textures (dress.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent


# ---------------------------------------------------------------- textures

def sheet(seed, rgb, seams=True):
    """Painted sheet steel on a rooftop: panel seams and their screws, chalked paint, rust weeping
    from the screws, pigeon mess."""
    t = F.plain(seed, rgb, 0.03, 0.4)
    t.f0[:] = 30
    if seams:
        for x in (0, 31, 32):
            area(t, x, 0, x + 1, N, col=tuple(int(c * 0.62) for c in rgb), dh=-0.3)
        for x in (3, 28, 35, 60):
            for y in range(4, N, 14):
                disc(t, x, y, 0.8, col=(90, 90, 86), f0=80)
                if t.rng.random() < 0.35:
                    area(t, x, y, x + 1, y + int(t.rng.integers(6, 18)), col=(120, 70, 40), dh=-0.05)
    t.streaks(0.3, 0.85)
    t.grime(0.25, (70, 66, 56))
    t.speckle(0.006, (230, 230, 220), 0.6)
    return t


def coil(seed):
    """The condenser coil behind its louvred guard: slats every four pixels, the fins dark between."""
    t = sheet(seed, (176, 172, 158), seams=False)
    for y in range(0, N, 4):
        area(t, 2, y, N - 2, y + 2, col=(36, 38, 36), dh=-0.5, smooth=0.2)
        area(t, 2, y + 2, N - 2, y + 3, col=(150, 146, 134), dh=0.2)
    area(t, 0, 0, 2, N, col=(150, 146, 134))
    area(t, N - 2, 0, N, N, col=(150, 146, 134))
    t.grime(0.3, (60, 58, 50))
    return t


def fan(seed):
    """The fan guard from above: rings of wire, spokes, the blades dark under it, the motor hub."""
    t = sheet(seed, (176, 172, 158), seams=False)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 31.5, yy - 31.5)
    a = np.arctan2(yy - 31.5, xx - 31.5)
    hole = r < 28
    t.c[hole] = (28, 30, 30)
    t.h[hole] = -0.6
    blade = hole & (np.sin(a * 3 + r * 0.06) > 0.55) & (r > 7)
    t.c[blade] = (70, 72, 70)
    ring = hole & ((r.astype(int) % 4) == 0)
    t.c[ring] = (120, 120, 114)
    t.h[ring] = 0.2
    spoke = hole & (np.abs(np.sin(a * 4)) < 0.06)
    t.c[spoke] = (130, 130, 124)
    disc(t, 31.5, 31.5, 6, col=(60, 62, 60), dh=0.3)
    disc(t, 31.5, 31.5, 2.5, col=(110, 110, 104), f0=90)
    rim = (r >= 28) & (r < 30.5)
    t.c[rim] = (120, 118, 108)
    t.grime(0.2, (60, 58, 50))
    return t


def service(seed):
    """A service door: the panel, two quarter-turn latches, the faded rating plate, a warning label."""
    t = sheet(seed, (176, 172, 158))
    area(t, 6, 6, 58, 58, col=(160, 156, 142), dh=0.1)
    area(t, 6, 6, 58, 7, col=(110, 108, 98), dh=-0.3)
    area(t, 6, 57, 58, 58, col=(110, 108, 98), dh=-0.3)
    for y in (16, 46):
        disc(t, 52, y, 2.2, col=(40, 40, 40), dh=0.4)
        area(t, 51, y - 3, 53, y + 3, col=(70, 70, 66), dh=0.5)
    area(t, 12, 12, 30, 22, col=(196, 196, 186), dh=0.05)
    for y in range(14, 21, 2):
        area(t, 14, y, 28, y + 1, col=(120, 120, 112))
    area(t, 12, 28, 26, 36, col=(212, 168, 40))
    area(t, 13, 29, 25, 31, col=(30, 30, 30))
    t.grime(0.3, (66, 62, 52))
    return t


def pot(seed):
    """A clay chimney pot: terracotta, sooted black toward the top, lichen low down."""
    t = F.plain(seed, (166, 86, 56), 0.06, 0.2)
    yy = np.mgrid[0:N, 0:N][0].astype(np.float32)
    t.mix(np.clip((24 - yy) / 24.0, 0, 1) * 0.8, (36, 32, 30))
    for y in (10, 11, 40):
        area(t, 0, y, N, y + 1, col=(110, 56, 38), dh=-0.2)
    t.speckle(0.03, (140, 150, 110), 0.5)
    t.grime(0.2, (60, 46, 40))
    return t


def tar(seed):
    """Roofing felt round a flashing: black, blistered, the gravel stuck in it."""
    t = F.plain(seed, (40, 40, 40), 0.05, 0.3)
    t.speckle(0.08, (110, 104, 96), 0.3, 0.15)
    t.speckle(0.02, (70, 70, 66), 0.3, 0.3)
    return t


def textures():
    return {
        "bd_sheet": lambda: sheet(51000, (178, 174, 160)),
        "bd_sheet_plain": lambda: sheet(51001, (176, 172, 158), seams=False),
        "bd_coil": lambda: coil(51002),
        "bd_fan": lambda: fan(51003),
        "bd_service": lambda: service(51004),
        "bd_pot": lambda: pot(51005),
        "bd_tar": lambda: tar(51006),
        "bd_rail": lambda: sheet(51007, (120, 122, 118), seams=False),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

STONE, PRECAST, GALV, LID = "stone_trim", "st_precast_plain", "ext_galv", "ext_galv_lid"
BRICKS = {"red": "brick_red", "dark": "brick_dark", "buff": "brick_buff"}


def coping(tex, corner=False):
    """3 px of stone on the parapet, a pixel proud of each face with the drip groove under it,
    weathered to a rounded top."""
    x0, x1 = (-1, 17) if corner else (0, 16)
    return [[x0, 0, -1, x1, 2.5, 17, tex],
            [x0 + (0.5 if corner else 0), 2.5, 0, x1 - (0.5 if corner else 0), 3.5, 16, tex],
            [x0, 0, -1, x1, 0.6, -0.4, "concrete_dark"],
            [x0, 0, 16.4, x1, 0.6, 17, "concrete_dark"]]


def chimney(brick, top):
    """A stack 12 px square on the parapet: flashing tar at its foot; at the top, the corbel course
    stepping out, the cement flaunching and two pots."""
    if not top:
        return [[2, 0, 2, 14, 16, 14, brick],
                [1.5, 0, 1.5, 14.5, 1.5, 14.5, "bd_tar"]]
    return [[2, 0, 2, 14, 6, 14, brick],
            [1, 6, 1, 15, 8, 15, brick],
            [1.5, 8, 1.5, 14.5, 9.5, 14.5, {"up": "concrete_dark", "side": brick, "down": brick}],
            [3, 9.5, 3, 7, 15, 7, "bd_pot"], [3.5, 15, 3.5, 6.5, 15.5, 6.5, "bd_pot"],
            [8.5, 9.5, 8.5, 12.5, 13.5, 12.5, "bd_pot"], [9, 13.5, 9, 12, 14, 12, "bd_pot"],
            [6, 9.5, 2, 10, 10.5, 14, "concrete_dark"]]


def hvac(end):
    """Half of a packaged rooftop unit, two blocks long along x: on galvanised curb rails, its casing
    of seamed sheet, the corner posts proud."""
    case = {"north": "bd_sheet", "south": "bd_sheet", "east": "bd_sheet", "west": "bd_sheet",
            "up": "bd_sheet_plain", "down": "bd_rail"}
    b = [[0, 0, 1, 16, 2, 3, "bd_rail"], [0, 0, 13, 16, 2, 15, "bd_rail"],
         [0, 2, 1.5, 16, 13, 14.5, case]]
    if end == "a":
        # the condenser end: louvred coil guards down both long sides and the end, the fan on top
        b += [[1, 3, 1, 15, 12, 1.5, {"north": "bd_coil", "side": "bd_sheet"}],
              [1, 3, 14.5, 15, 12, 15, {"south": "bd_coil", "side": "bd_sheet"}],
              [-0.5, 3, 2.5, 0, 12, 13.5, {"west": "bd_coil", "side": "bd_sheet"}],
              [2, 13, 2.5, 14, 14, 13.5, {"up": "bd_fan", "side": "bd_sheet_plain", "down": "bd_sheet"}],
              [7, 14, 7, 9, 14.6, 9, "ext_galv"],
              [-0.5, 2, 1, 1, 13.5, 2.5, "bd_sheet_plain"], [-0.5, 2, 13.5, 1, 13.5, 15, "bd_sheet_plain"]]
    else:
        # the supply end: two service doors, the economiser's rain hood, the power whip into the curb
        b += [[2, 4, 1, 8, 12, 1.5, {"north": "bd_service", "side": "bd_sheet"}],
              [9, 4, 1, 15, 12, 1.5, {"north": "bd_service", "side": "bd_sheet"}],
              [15, 6, 3, 18, 12.5, 13, {"east": "bd_coil", "up": "bd_sheet_plain", "side": "bd_sheet"}],
              [15, 12.5, 2.5, 18.5, 13.2, 13.5, "bd_sheet_plain"],
              [15, 2, 1, 16.5, 13.5, 2.5, "bd_sheet_plain"], [15, 2, 13.5, 16.5, 13.5, 15, "bd_sheet_plain"],
              [11, 0, 15, 12.5, 7, 16.5, "home_plastic_black"], [11, 7, 14.5, 12.5, 8.5, 16.5, "home_plastic_black"],
              [0, 13, 1.5, 16, 13.4, 14.5, "bd_sheet_plain"]]
    return b


def vent():
    """A mushroom exhaust: the curb flashed into the felt, the throat, the hood and its bird screen."""
    return [[3, 0, 3, 13, 3, 13, {"up": "bd_tar", "side": "concrete_dark", "down": "concrete_dark"}],
            [4.5, 3, 4.5, 11.5, 8, 11.5, GALV],
            [5, 8, 5, 11, 10, 11, "bd_coil"],
            [2, 10, 2, 14, 11.5, 14, GALV], [3, 11.5, 3, 13, 12.5, 13, GALV], [5, 12.5, 5, 11, 13, 11, GALV]]


def hatch():
    """The roof hatch: a kerb, the galvanised lid on its hinges with the hold-open arm and handle."""
    return [[1, 0, 1, 15, 4, 15, {"up": "bd_tar", "side": "concrete_dark", "down": "concrete_dark"}],
            [0.5, 4, 0.5, 15.5, 5.5, 15.5, LID],
            [1, 5.5, 1, 15, 6, 15, LID],
            [2, 4.5, 15.5, 5, 6, 16.2, GALV], [11, 4.5, 15.5, 14, 6, 16.2, GALV],
            [6, 6, 1.5, 10, 7, 2.5, GALV],
            [13.5, 2, 0.2, 14.5, 5, 1, GALV]]


def drain():
    """The roof drain: its clamping ring in the felt and the domed strainer."""
    return [[4, 0, 4, 12, 0.6, 12, "bd_tar"], [5, 0.6, 5, 11, 1.4, 11, "bunk_rust_pipe"],
            [6, 1.4, 6, 10, 3, 10, "bd_coil"], [7, 3, 7, 9, 3.6, 9, "bunk_rust_pipe"]]


def lintel():
    """A stone lintel just over a window head, outside the wall: a pixel wider each side, the
    keystone proud in the middle."""
    return [[-1, 0, 13, 17, 3, 16, STONE], [6.5, -0.5, 12, 9.5, 3.5, 13, STONE]]


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

    add("bd_coping", coping(STONE), "Stone Coping", "STONE")
    add("bd_coping_corner", coping(STONE, True), "Stone Coping", "STONE", rotate=None)
    add("bd_coping_pc", coping(PRECAST), "Precast Coping", "STONE")
    add("bd_coping_pc_corner", coping(PRECAST, True), "Precast Coping", "STONE", rotate=None)
    for k, brick in BRICKS.items():
        add(f"bd_chimney_{k}", chimney(brick, False), "Chimney Stack", "STONE", rotate=None)
        add(f"bd_chimney_top_{k}", chimney(brick, True), "Chimney Stack", "STONE", rotate=None)
    add("bd_hvac_a", hvac("a"), "Rooftop Unit", "METAL")
    add("bd_hvac_b", hvac("b"), "Rooftop Unit", "METAL")
    add("bd_vent", vent(), "Roof Vent", "METAL", rotate=None)
    add("bd_hatch", hatch(), "Roof Hatch", "METAL")
    add("bd_drain", drain(), "Roof Drain", "METAL", rotate=None, decor=True)
    add("bd_lintel", lintel(), "Lintel", "STONE", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    # keep everyone else's order; new pieces go on the end
    for k in [k for k in blocks_ if k.startswith("bd_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} dressing blocks in {path}")


if __name__ == "__main__":
    main()
