"""The ts_ kit: the traffic signals over a town's main crossing, which the county had none of (refs/town
courthouse 01: the three-lamp heads hung from a galvanised mast arm over the street; main-street
exterior: the poles on the sidewalk corners). The heads still run their cycle (green, a short amber,
red; blocks/Signal times it off the world clock), so the models come dark and lit. world/county/
TownSite.signals sets them out.

- ts_pole / ts_pole_base: the galvanised pole, round as the kit can make it; its cast base on the
  sidewalk with the handhole to the north (rotate=facing);
- ts_pole_ped: the pole with the pedestrian head bolted to its north side, the hand over the
  walking man (rotate=facing);
- ts_pole_arm: the top of the pole, its cap, the mast arm leaving it to the north with the tie rod
  up to the pole (rotate=facing);
- ts_arm: a length of mast arm, low in the block so a head hangs off it into the block below,
  rotate=axis (drawn along z); ts_arm_end: the arm's capped end, running north from the south edge;
- ts_arm_sign_{main,court,elm,oak}: the arm with the green street-name blade clamped on top,
  rotate=axis;
- ts_head: the three-lamp head in traffic yellow on its black backplate, the visors over the dead
  lenses, lenses to the north, hung from the arm above (rotate=facing); ts_head_swing: the same
  hanging off one bracket, swung out (rotate=facing);
- ts_head_down: a head lying on the road where it fell, the lenses up and one smashed, drawn along z
  (rotate=facing);
- ts_flasher: the four-way head on the span wire over a country crossroads, three lenses on each
  face up each road, the wire running corner to corner (rotate=facing: north and east give the two
  diagonals); ts_span: the span wire alone, corner to corner (rotate=facing);

ts_head, ts_head_swing and ts_flasher carry "signal": their lit models (one lens lit: g, y, r; the
four-way by its north-south and east-west faces: gr, yr, rr, rg, ry) and, for each facing, the model
each of Signal's six phases shows (0 dark, 1 north-south green, 2 north-south amber, 3 all red,
4 east-west green, 5 east-west amber). A head facing north or south faces the traffic along z.
- ts_cabinet: the controller cabinet on its concrete plinth, the door to the north hanging open on
  the racks of relays (rotate=facing).

    python3 tools/city/signals.py      rewrites the ts_ entries in blocks.json

photo_textures.py draws the textures (signals.register()).
"""
import json
import numpy as np
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips, stencil  # noqa: E402

N = P.N
HERE = Path(__file__).parent
GALV, BLACK, WIRE, CONC = "st_galv", "ext_black_metal", "tire", "concrete_dark"
YELLOW = (206, 164, 38)
# the lenses' centres up the head's face, in 16ths, and their radius
LENS_Y, LENS_R = (12.4, 7.9, 3.4), 1.9
SIGNS = {"main": "MAIN ST", "court": "COURT ST", "elm": "ELM ST", "oak": "OAK AVE"}


# ---------------------------------------------------------------- textures

def housing(seed):
    """Traffic yellow over cast aluminium: chalked by the sun, chipped to the grey metal at the
    edges, streaked from the bolts, a dusting of road grime."""
    t = F.plain(seed, YELLOW, 0.05, 0.35)
    chips(t, 0.08, under=(150, 150, 146))
    weather(t, rust=0.0, streak=0.7, grime=0.35)
    return t


def lens(t, cx, cy, rgb, smashed=False):
    """One dead lens: the black ring of its door, the tinted glass dark with the bulb out, the
    prism rings, a highlight; smashed, the glass gone to the dark bulb socket."""
    r = LENS_R * 4
    disc(t, cx, cy, r + 3, col=(26, 26, 24), dh=0.25, f0=40)
    if smashed:
        disc(t, cx, cy, r, col=(16, 15, 14), dh=-0.5)
        disc(t, cx, cy, 2.5, col=(120, 120, 112), f0=120)
        for a, b in ((-4, -3), (3, -5), (5, 3), (-5, 4)):
            disc(t, cx + a, cy + b, 1.6, col=tuple(int(c * 0.8) for c in rgb), f0=160)
        return
    disc(t, cx, cy, r, col=rgb, dh=0.3, smooth=0.95, f0=180)
    for k in (2.5, 5):
        disc(t, cx, cy, r - k, mul=1.12, ring=1)
    disc(t, cx - r * 0.35, cy - r * 0.35, 1.8, col=tuple(min(255, c + 90) for c in rgb), smooth=1.0)


DARK = ((96, 22, 18), (118, 74, 14), (18, 74, 54))


def face(seed, smashed=-1):
    """The head's front: three lenses down the yellow casting, red over amber over green."""
    t = housing(seed)
    for i, (y, rgb) in enumerate(zip(LENS_Y, DARK)):
        lens(t, 32, (16 - y) * 4, rgb, smashed=i == smashed)
    return t


LIT = ((255, 70, 50), (255, 178, 40), (70, 255, 170))


def lit_face(seed, which):
    """The face with one lens lit: the glass bright with the bulb behind it, glowing out to its rim."""
    t = face(seed)
    i = "ryg".index(which)
    cx, cy, r = 32, (16 - LENS_Y[i]) * 4, LENS_R * 4
    yy, xx = np.mgrid[0:N, 0:N]
    d = ((xx - cx) ** 2 + (yy - cy) ** 2) ** 0.5
    m = d <= r
    hot = np.clip(1.15 - d / r * 0.35, 0, 1)[..., None]
    t.c[m] = (np.array(LIT[i], "float32") * hot)[m]
    t.c[m] = np.minimum(t.c[m] + 40 * (d[m] < r * 0.35)[..., None], 255)
    t.emit[m] = 1.0
    return t


def backplate(seed):
    """The louvred black backplate, the yellow reflective strip round its edge (seen from the
    front on the band either side of the head)."""
    t = F.plain(seed, (30, 30, 30), 0.04, 0.3)
    for x in range(12, 53, 4):
        area(t, x, 0, x + 1, N, mul=0.6, dh=-0.2)
    for x0 in (12, 50):
        area(t, x0, 0, x0 + 2, N, col=(214, 186, 40), dh=0.1, f0=60)
    area(t, 12, 0, 52, 2, col=(214, 186, 40), dh=0.1, f0=60)
    area(t, 12, N - 2, 52, N, col=(214, 186, 40), dh=0.1, f0=60)
    weather(t, rust=0.0, streak=0.6, grime=0.4)
    return t


def ped(seed):
    """The pedestrian head's front (x 5..11, y 3..12 of the block): the dead orange hand over the
    dead white walking man behind their egg-crate visors."""
    t = F.plain(seed, (34, 34, 32), 0.04, 0.3)
    for v0 in (17, 38):
        area(t, 22, v0, 42, v0 + 16, col=(14, 14, 14), dh=-0.3)
        for x in range(22, 43, 4):
            area(t, x, v0, x + 1, v0 + 16, col=(46, 46, 44), dh=0.2)
        for y in range(v0, v0 + 17, 4):
            area(t, 22, y, 42, y + 1, col=(46, 46, 44), dh=0.2)
    # the hand
    area(t, 28, 21, 36, 30, col=(110, 62, 24), f0=120)
    for x in (28, 30, 32, 34):
        area(t, x, 19, x + 1.5, 22, col=(110, 62, 24), f0=120)
    # the walking man
    disc(t, 32, 41, 1.6, col=(120, 120, 112), f0=120)
    area(t, 31, 43, 33, 49, col=(120, 120, 112), f0=120)
    area(t, 29, 49, 31, 53, col=(120, 120, 112), f0=120)
    area(t, 33, 49, 35, 53, col=(120, 120, 112), f0=120)
    weather(t, rust=0.0, streak=0.5, grime=0.4)
    return t


def cabinet(seed, inside=False):
    """The controller cabinet: grey enamel on aluminium, the door's seam and its lock, the vent
    louvres, a utility sticker, tags sprayed on it; inside, the racks of relays and the wiring."""
    t = F.plain(seed, (150, 154, 150), 0.05, 0.4)
    if inside:
        t = F.plain(seed, (60, 62, 60), 0.05, 0.3)
        for y in range(8, 56, 12):
            area(t, 8, y, 56, y + 2, col=(160, 160, 156), dh=0.3)
            for x in range(10, 54, 6):
                area(t, x, y + 2, x + 4, y + 10, col=(30, 40, 36), dh=0.4)
                area(t, x + 1, y + 3, x + 2, y + 4, col=(150, 40, 30), f0=100)
        for x in (6, 58):
            area(t, x, 0, x + 1, N, col=(170, 120, 40))
        t.grime(0.4, (40, 36, 30))
        return t
    for x0, x1 in ((8, 9), (55, 56)):
        area(t, x0, 4, x1, N - 4, mul=0.45, dh=-0.3)
    area(t, 8, 4, 56, 5, mul=0.45, dh=-0.3)
    area(t, 50, 30, 53, 36, col=(60, 60, 58), f0=140)
    for y in range(10, 22, 3):
        area(t, 16, y, 48, y + 1, mul=0.5, dh=-0.2)
    area(t, 14, 40, 30, 48, col=(226, 222, 206))
    area(t, 15, 41, 29, 43, col=(40, 70, 140))
    stencil(t, "XK", (40, 120, 60), 10, x=30, y=40, w=24, h=18, chip=0.3)
    weather(t, rust=0.1, streak=0.6, grime=0.4)
    return t


def blade(seed, text):
    """A street-name blade: white letters and border on reflective green, the corners rounded off,
    faded by the sun and dirty along the bottom (painted on y 3..8, z 1..15 of the block)."""
    t = F.plain(seed, (20, 98, 62), 0.04, 0.6)
    area(t, 4, 32, 60, 33, col=(226, 230, 222))
    area(t, 4, 51, 60, 52, col=(226, 230, 222))
    area(t, 4, 32, 5, 52, col=(226, 230, 222))
    area(t, 59, 32, 60, 52, col=(226, 230, 222))
    stencil(t, text, (232, 236, 228), 13, x=7, y=34, w=50, h=16, chip=0.05)
    weather(t, rust=0.05, streak=0.5, grime=0.3)
    return t


def textures():
    out = {"ts_housing": lambda: housing(49700), "ts_face": lambda: face(49701), "ts_face_smash": lambda: face(49702, 1),
           "ts_face_x": lambda: face(49703), "ts_back": lambda: backplate(49704), "ts_ped": lambda: ped(49705),
           "ts_cab": lambda: cabinet(49706), "ts_cab_in": lambda: cabinet(49707, True)}
    for i, (k, text) in enumerate(SIGNS.items()):
        out["ts_blade_" + k] = lambda text=text, i=i: blade(49710 + i, text)
    for i, w in enumerate("gyr"):
        out["ts_face_" + w] = lambda w=w, i=i: lit_face(49720 + i, w)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- models

def pole(y0=0, y1=16):
    """The pole: two boxes crossed so it reads round."""
    return [[6.5, y0, 7, 9.5, y1, 9, GALV], [7, y0, 6.5, 9, y1, 9.5, GALV]]


def pole_base():
    body = {"north": GALV, "side": GALV, "up": GALV, "down": GALV}
    return pole(3, 16) + [[4.5, 0, 4.5, 11.5, 1, 11.5, GALV], [5.2, 1, 5.2, 10.8, 3, 10.8, body],
                          [6.6, 0.8, 4.9, 9.4, 2.4, 5.2, BLACK], [5.8, 3, 5.8, 10.2, 3.6, 10.2, GALV]]


def pole_ped():
    b = pole()
    body = {"north": "ts_ped", "side": "ts_housing", "up": "ts_housing", "down": "ts_housing"}
    b += [[5, 3, 2, 11, 12, 6.2, body],
          [5, 12, 1, 11, 12.5, 6.2, BLACK],
          [7.5, 6, 6.2, 8.5, 9, 7, GALV], [7.2, 5.6, 6.4, 8.8, 9.4, 6.9, GALV]]
    return b


def arm(z0=0, z1=16):
    """The mast arm low in the block, round-ish."""
    return [[7, 0.2, z0, 9, 2.6, z1, GALV], [6.6, 0.6, z0, 9.4, 2.2, z1, GALV]]


def pole_arm():
    b = pole(0, 9) + [[6.2, 9, 6.2, 9.8, 10, 9.8, GALV], [7.2, 10, 7.2, 8.8, 11, 8.8, GALV]]
    b += arm(0, 7)
    # the clamp plate where the arm meets the pole, the tie rod up from the arm to the pole
    b += [[6, 0, 6, 10, 3.4, 10, GALV], [7.6, 3, 1.5, 8.4, 3.6, 8.6, GALV, None, {"axis": "x", "angle": -45, "origin": [8, 3, 7]}]]
    return b


def arm_end():
    return arm(1, 16) + [[6.4, 0, 0.6, 9.6, 2.8, 1.4, GALV]]


def arm_sign(key):
    plate = {"east": "ts_blade_" + key, "west": "ts_blade_" + key, "north": GALV, "south": GALV, "up": GALV, "down": GALV}
    return arm() + [[7.7, 3, 1, 8.3, 8, 15, plate], [7.4, 2.6, 3, 8.6, 3.4, 4, GALV], [7.4, 2.6, 12, 8.6, 3.4, 13, GALV]]


def head(rot=None):
    """The head hung from the arm above: casting, backplate, visors, the hanger."""
    body = {"north": "ts_face", "side": "ts_housing", "up": "ts_housing", "down": "ts_housing"}
    back = {"north": "ts_back", "side": BLACK, "up": BLACK, "down": BLACK}
    b = [[5, 0.6, 6, 11, 15, 11, body], [3, 0.2, 11, 13, 15.6, 11.5, back],
         [7.4, 15, 7.6, 8.6, 16, 8.8, GALV], [6.6, 14.6, 7, 9.4, 15.2, 9.4, GALV]]
    for y in LENS_Y:
        top = y + LENS_R + 0.3
        b += [[5.4, top, 3, 10.6, top + 0.4, 6, BLACK],
              [5.4, top - 3.4, 3.6, 5.8, top, 6, BLACK], [10.2, top - 3.4, 3.6, 10.6, top, 6, BLACK]]
    if rot:
        b = [q[:7] + [None, rot] for q in b]
    return b


def head_down():
    """A head lying on its back in the road, the lenses up along z, the middle one smashed."""
    body = {"up": "ts_face_smash", "side": "ts_housing", "north": "ts_housing", "south": "ts_housing", "down": "ts_housing"}
    b = [[5, 0, 0.5, 11, 5, 15, body], [3, 0, 15, 13, 0.5, 15.8, BLACK, None, {"axis": "x", "angle": 22.5, "origin": [8, 0, 15]}]]
    b += [[5.4, 5, 1.2, 10.6, 7.4, 1.6, BLACK], [5.4, 5, 10.2, 10.6, 6.6, 10.6, BLACK],
          [9, 0, 0, 11, 0.4, 0.5, WIRE], [2, 0, 3, 4.5, 0.3, 4.2, "lamp_glass"], [11.5, 0, 8, 13, 0.3, 9.6, "lamp_glass"]]
    return b


def flasher_block():
    """The four-way head on its hanger under the span wire: a three-lamp face up each road (north and
    south "ts_face", east and west "ts_face_x", so the two pairs light apart), the visors over them."""
    body = {"north": "ts_face", "south": "ts_face", "east": "ts_face_x", "west": "ts_face_x",
            "up": "ts_housing", "down": "ts_housing"}
    b = [[5, 0.6, 5, 11, 14.6, 11, body], [7.4, 14.6, 7.4, 8.6, 15.2, 8.6, GALV], [7, 14.8, 7, 9, 15.6, 9, GALV]] + span()
    for y in LENS_Y:
        top = y + LENS_R + 0.3
        for x0, z0, x1, z1 in ((5.4, 3, 10.6, 5), (5.4, 11, 10.6, 13), (3, 5.4, 5, 10.6), (11, 5.4, 13, 10.6)):
            b.append([x0, top, z0, x1, top + 0.4, z1, BLACK])
    return b


FACINGS = ("north", "east", "south", "west")


def colour(ns, phase):
    """The lens a head shows in a phase: ns, it faces the traffic along z."""
    go, slow = (1, 2) if ns else (4, 5)
    return "" if phase == 0 else "g" if phase == go else "y" if phase == slow else "r"


def head_signal():
    swap = {w: {"ts_face": "ts_face_" + w} for w in "gyr"}
    states = {f: [colour(f in ("north", "south"), k) for k in range(6)] for f in FACINGS}
    return {"light": 8, "on": swap, "states": states}


def fourway_signal():
    swap = {}
    for a in "gyr":
        for b in "gyr":
            swap[a + b] = {"ts_face": "ts_face_" + a, "ts_face_x": "ts_face_" + b}
    states = {}
    for f in FACINGS:
        ns = f in ("north", "south")
        # the model's north and south faces turn to face along z for north or south, along x otherwise
        states[f] = ["" if k == 0 else colour(ns, k) + colour(not ns, k) for k in range(6)]
    used = {k for v in states.values() for k in v if k}
    return {"light": 8, "on": {k: v for k, v in swap.items() if k in used}, "states": states}


def span():
    """The wire across the cell corner to corner (north-west to south-east as drawn)."""
    return [[7.8, 15, -3.3, 8.2, 15.5, 19.3, WIRE, None, {"axis": "y", "angle": 45, "origin": [8, 15, 8]}]]


def cabinet_block():
    shut = {"north": "ts_cab", "side": "ts_cab", "up": "ts_cab", "down": "ts_cab"}
    b = [[2, 0, 3, 14, 1.5, 13, CONC], [3, 1.5, 5, 13, 15, 12, {"north": "ts_cab_in", "side": "ts_cab", "up": "ts_cab", "down": "ts_cab"}],
         [2.6, 15, 4.6, 13.4, 15.6, 12.4, "ts_cab"]]
    # the door swung open on its hinge at the west edge
    b.append([3, 1.8, 4.4, 12.6, 14.6, 5, shut, None, {"axis": "y", "angle": 45, "origin": [3, 8, 4.7]}])
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate=None, sound="METAL"):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape, "render": "cutout"}
        if rotate:
            spec["rotate"] = rotate
        out[name] = spec

    add("ts_pole", pole(), "Signal Pole", [[6.5, 0, 6.5, 9.5, 16, 9.5]])
    add("ts_pole_base", pole_base(), "Signal Pole", [[4.5, 0, 4.5, 11.5, 16, 11.5]], "facing")
    add("ts_pole_ped", pole_ped(), "Signal Pole", [[5, 0, 2, 11, 16, 10]], "facing")
    add("ts_pole_arm", pole_arm(), "Signal Pole", [[6, 0, 0, 10, 11, 10]], "facing")
    add("ts_arm", arm(), "Mast Arm", [[6.6, 0, 0, 9.4, 2.6, 16]], "axis")
    add("ts_arm_end", arm_end(), "Mast Arm", [[6.4, 0, 0.6, 9.6, 2.8, 16]], "facing")
    for k in SIGNS:
        add("ts_arm_sign_" + k, arm_sign(k), "Street Sign", [[6.6, 0, 0, 9.4, 8, 16]], "axis")
    add("ts_head", head(), "Traffic Signal", [[3, 0, 3, 13, 16, 11.5]], "facing")
    add("ts_head_swing", head({"axis": "x", "angle": 22.5, "origin": [8, 16, 8]}), "Traffic Signal", [[3, 0, 3, 13, 16, 13]], "facing")
    add("ts_head_down", head_down(), "Traffic Signal", [[3, 0, 0, 13, 6, 16]], "facing")
    add("ts_flasher", flasher_block(), "Traffic Signal", [[3, 0.6, 3, 13, 16, 13]], "facing")
    for k in ("ts_head", "ts_head_swing"):
        out[k]["signal"] = head_signal()
    out["ts_flasher"]["signal"] = fourway_signal()
    add("ts_span", span(), "Span Wire", [[0, 15, 0, 16, 15.5, 16]], "facing")
    add("ts_cabinet", cabinet_block(), "Signal Cabinet", [[2, 0, 3, 14, 15.6, 13]], "facing")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("ts_") or k in made}
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} signal blocks in {path}")


if __name__ == "__main__":
    main()
