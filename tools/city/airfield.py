"""The af_ kit: the airfield's ground and its furniture for Grayhaven International (Josiah,
2026-10-29: "Resume work and begin the airport"), drawn to the FAA's markings and lights, a
block a metre: world/county/Airport lays it out.

The ground (cubes 14 high like the streets', rotate=axis where a mark runs one way; the mark runs
along the texture's vertical, so along z as drawn):

- af_rwy / af_rwy_rubber / af_rwy_tar: the runway's grooved asphalt, the touchdown zone black with
  tyre rubber, a run of tar snakes over the cracks;
- af_paint / af_paint_rubber: the white of a runway mark, a block of it (the threshold's piano keys,
  the numbers, the touchdown bars, the aiming points, the centreline's dashes); the same rubbered;
- af_paint_y / af_paint_y_half: blast pad yellow, a whole block or the half of one cut corner to
  corner (rotate=facing: the paint on the north-west half) for the chevrons' slanted edges;
- af_edge: the runway's side stripe, a 90 cm white band down the west of the block;
- af_twy / af_twy_cl / af_twy_edge: the taxiway's older asphalt, its 15 cm yellow centreline, its
  double yellow edge lines down the west;
- af_hold: the hold-short bars across a taxiway: two solid yellow lines, two dashed (rotate=axis:
  drawn across z, the solid side to the north, the runway side);
- af_apron / af_apron_j / af_apron_jj: the apron's concrete in five-metre slabs, a joint down the
  west edge, joints down the west and the north;
- af_apron_lead: the apron's yellow lead-in line to a gate.

The furniture (rotate=facing):

- af_light_edge / af_light_twy / af_light_thr: an elevated light on its frangible stem, the runway's
  clear lens, the taxiway's blue, the threshold's green to the north and red to the south;
  af_light_broken: one snapped off its stem by a tug;
- af_als_mast / af_als_bar: the approach lights' mast, and the bar of five lamps across its top;
- af_papi: a PAPI unit, its three lenses to the north, on its legs;
- af_sign_*: the guidance signs on their frangible legs, the face to the north and the back plain:
  af_sign_hold_<0..1> "27-9" white on red, af_sign_loc_<a|b> a yellow letter on black,
  af_sign_dir_<a|b> a black letter and arrow on yellow, af_sign_dist_<1..9> a white number on black
  (the thousands of feet left), af_sign_end the sign's end post;
- af_windsock_mast / af_windsock: the mast and its frame, the striped sock hung slack off it.

    python3 tools/city/airfield.py      rewrites the af_ entries in blocks.json

photo_textures.py draws the textures (airfield.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import stencil  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None

WHITE, YELLOW, RED, BLACK = (206, 204, 196), (214, 168, 40), (170, 34, 30), (26, 26, 26)
METAL, GLASS = "st_galv", "lamp_glass"


# ---------------------------------------------------------------- the ground

def asphalt(seed, bright=1.15, grooved=True):
    """Runway asphalt: grey and open-textured, grooved across every inch or so for the rain (the
    taxiways and the pads not)."""
    t = NS.photo("Asphalt026C", span=2, offset=(0.6, 0.1), sat=0.5, bright=bright, wet=0.5, seed=seed)
    for y in range(0, N, 3) if grooved else ():
        t.c[y] *= 0.86
        t.h[y] -= 0.04
    return t


def rubber(t, amount=0.75):
    """Tyre rubber laid down where the wheels touch: black streaks along the runway."""
    n = P.fbm(t.rng, ((2, 1.0), (4, 0.6), (8, 0.3)))
    along = np.repeat(n[:, :1] * 0.4 + n[:1, :] * 0.6, 1, axis=0)
    streak = np.clip(0.55 + 0.6 * along + 0.25 * P.fbm(t.rng, ((16, 1.0),)), 0, 1)
    t.c *= (1 - amount * streak)[..., None]
    t.smooth[:] = np.clip(t.smooth + 0.15 * streak, 0, 1)
    return t


def tar(t):
    """Tar snakes: crack sealant poured in wandering black bands, glossy."""
    yy, xx = np.mgrid[0:N, 0:N]
    for k in range(3):
        a = t.rng.uniform(0, np.pi)
        off = t.rng.uniform(-20, 20)
        wob = 4 * np.sin(yy / t.rng.uniform(6, 12) + k)
        d = np.abs((xx - N / 2) * np.cos(a) + (yy - N / 2) * np.sin(a) - off + wob)
        m = d < 1.6
        t.c[m] = (18, 18, 18)
        t.smooth[m] = 0.7
        t.h[m] += 0.02
    return t


def paint(t, mask, col, wear=0.3):
    """Airfield paint over the ground: thick, beaded, flaking where the ploughs scraped it."""
    keep = P.fbm(t.rng, ((8, 1), (16, 0.6), (32, 0.4))) > -1 + wear * 1.2
    m = mask & keep
    base = t.c.mean(axis=2) / max(t.c.mean(), 1)
    t.c[m] = (np.array(col, np.float32) * np.clip(0.75 + 0.25 * base, 0.6, 1.1)[..., None])[m]
    t.smooth[m] = 0.4
    t.h[m] += 0.03
    return t


def full(n=N):
    return np.ones((n, n), bool)


def apron(seed, joints=""):
    """The apron's concrete: five-metre slabs, the sawn joints sealed black, oil drips and rust."""
    t = NS.photo("Concrete035", span=2, sat=0.4, bright=0.82, wet=0.3, seed=seed)
    t.grime(0.25, (60, 56, 50))
    for k in range(3):
        F.disc(t, t.rng.uniform(8, N - 8), t.rng.uniform(8, N - 8), t.rng.uniform(2, 5), (40, 38, 34), h=0.45)
    if "w" in joints:
        t.c[:, :2] = (30, 30, 30)
        t.h[:, :2] = 0.1
    if "n" in joints:
        t.c[:2, :] = (30, 30, 30)
        t.h[:2, :] = 0.1
    return t


def column(x0, x1):
    m = np.zeros((N, N), bool)
    m[:, x0:x1] = True
    return m


def row(y0, y1, dash=False):
    m = np.zeros((N, N), bool)
    m[y0:y1, :] = True
    if dash:
        m[:, N // 2:] = False
    return m


def half():
    yy, xx = np.mgrid[0:N, 0:N]
    return xx + yy < N


def textures():
    out = {
        "af_rwy": lambda: asphalt(49510),
        "af_rwy_rubber": lambda: rubber(asphalt(49511)),
        "af_rwy_tar": lambda: tar(asphalt(49512)),
        "af_paint": lambda: paint(asphalt(49513), full(), WHITE),
        "af_paint_rubber": lambda: rubber(paint(asphalt(49514), full(), WHITE), 0.55),
        "af_paint_y": lambda: paint(asphalt(49515, 1.25, False), full(), YELLOW),
        "af_paint_y_half": lambda: paint(asphalt(49516, 1.25, False), half(), YELLOW),
        "af_edge": lambda: paint(asphalt(49517), column(0, 58), WHITE),
        "af_twy": lambda: asphalt(49518, 1.25, False),
        "af_twy_cl": lambda: paint(asphalt(49519, 1.25, False), column(27, 37), YELLOW),
        "af_twy_edge": lambda: paint(asphalt(49520, 1.25, False), column(2, 12) | column(22, 32), YELLOW),
        "af_hold": lambda: paint(asphalt(49521, 1.25, False), row(3, 12) | row(21, 30) | row(39, 48, True) | row(55, 64, True), YELLOW),
        "af_apron": lambda: apron(49522),
        "af_apron_j": lambda: apron(49523, "w"),
        "af_apron_jj": lambda: apron(49524, "wn"),
        "af_apron_lead": lambda: paint(apron(49525), column(27, 37), YELLOW),
    }
    out.update(sign_textures())
    out["af_sock"] = lambda: sock(49530)
    for k, col, seed in (("blue", (40, 70, 150), 49535), ("green", (40, 120, 70), 49536), ("red", (150, 40, 36), 49537)):
        out[f"af_lens_{k}"] = lambda col=col, seed=seed: lens(seed, col)
    return out


def lens(seed, col):
    """A light's coloured glass dome, dusty."""
    t = F.plain(seed, col, 0.04, 0.85)
    t.f0[:] = 40
    t.grime(0.2, (60, 60, 56))
    return t


# ---------------------------------------------------------------- the signs

SIGNS = {
    # name: (text, letters, ground, framed)
    "hold_0": ("27", WHITE, RED, True), "hold_1": ("-9", WHITE, RED, True),
    "loc_a": ("A", YELLOW, BLACK, True), "loc_b": ("B", YELLOW, BLACK, True),
    "loc_a1": ("A1", YELLOW, BLACK, True), "loc_a2": ("A2", YELLOW, BLACK, True), "loc_a3": ("A3", YELLOW, BLACK, True),
    "loc_a4": ("A4", YELLOW, BLACK, True), "loc_a5": ("A5", YELLOW, BLACK, True),
    "dir_a": ("A", BLACK, YELLOW, False), "dir_b": ("B", BLACK, YELLOW, False),
}
for _n in range(1, 10):
    SIGNS[f"dist_{_n}"] = (str(_n), WHITE, BLACK, False)


def sign(seed, text, ink, ground, framed, arrow=False):
    """A sign's face: the lit panel's paint, the lettering in the FAA's heavy face, sun-faded."""
    t = F.plain(seed, ground, 0.04, 0.4)
    if framed:
        t.frame(3, ink, h=0.6)
    w = N - 20 if arrow else N - 12
    stencil(t, text, ink, 44, x=6, y=6, w=w, h=N - 12, chip=0.05)
    if arrow:
        yy, xx = np.mgrid[0:N, 0:N]
        head = (xx > N - 16) & (np.abs(yy - N / 2) < (N - 4 - xx) * 0.9)
        shaft = (xx > N - 22) & (xx <= N - 12) & (np.abs(yy - N / 2) < 3)
        m = head | shaft
        t.c[m] = ink
    weather(t, rust=0.0, streak=0.5, grime=0.25)
    return t


def sign_textures():
    out = {}
    for i, (k, (text, ink, ground, framed)) in enumerate(SIGNS.items()):
        out[f"af_sign_{k}"] = lambda text=text, ink=ink, ground=ground, framed=framed, i=i, k=k: sign(
            49540 + i, text, ink, ground, framed, k.startswith("dir"))
    out["af_sign_back"] = lambda: F.plain(49539, (40, 40, 38), 0.05, 0.4)
    return out


def sock(seed):
    """The windsock: orange and white hoops, the nylon faded and grubby, a tear."""
    t = F.plain(seed, (222, 110, 36), 0.05, 0.25)
    for y in range(N // 5, N, 2 * N // 5):
        t.rect(0, y, N, y + N // 5, col=(220, 216, 206), h=0.5)
    t.grime(0.35, (90, 80, 70))
    return t


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the furniture

def light(lens):
    """An elevated light: the base can bolted to its pad, the frangible coupling, the stem, the
    lens dome; lens is a texture or a face dict."""
    return [[5.5, 0, 5.5, 10.5, 1.5, 10.5, METAL], [6.3, 1.5, 6.3, 9.7, 3, 9.7, "ext_black_metal"],
            [7.3, 3, 7.3, 8.7, 9, 8.7, METAL], [6.4, 9, 6.4, 9.6, 9.8, 9.6, METAL],
            [6.6, 9.8, 6.6, 9.4, 12.4, 9.4, lens], [7.2, 12.4, 7.2, 8.8, 13, 8.8, "ext_black_metal"]]


def broken_light():
    return [[5.5, 0, 5.5, 10.5, 1.5, 10.5, METAL], [6.3, 1.5, 6.3, 9.7, 2.6, 9.7, "ext_black_metal"],
            [7.3, 0.2, 9, 8.7, 1.6, 15, METAL], [6.6, 0, 15, 9.4, 2.8, 16, "lamp_glass"]]


def als_mast():
    """A lattice mast for the approach lights: four legs braced, a ladder up one face."""
    b = []
    for x, z in ((5, 5), (10, 5), (5, 10), (10, 10)):
        b.append([x, 0, z, x + 1, 16, z + 1, METAL])
    for y in (2, 8, 14):
        b += [[5, y, 5, 11, y + 0.6, 6, METAL], [5, y, 10, 11, y + 0.6, 11, METAL],
              [5, y, 6, 6, y + 0.6, 10, METAL], [10, y, 6, 11, y + 0.6, 10, METAL]]
    return b


def als_bar():
    """The bar of five lamps across the mast's top, aimed down the approach (north)."""
    b = [[0, 2, 7, 16, 3.2, 9, METAL], [7, 0, 7, 9, 2, 9, METAL]]
    for x in (0.5, 3.6, 6.7, 9.8, 12.9):
        b += [[x, 3.2, 6, x + 2.6, 6, 9.4, "ext_black_metal"],
              [x + 0.3, 3.5, 5.6, x + 2.3, 5.7, 6, {"north": "lamp_glass", "side": "ext_black_metal", "up": "ext_black_metal",
                                                    "down": "ext_black_metal"}]]
    return b


def papi():
    box = {"north": "ext_black_metal", "side": "ext_alu", "up": "ext_alu", "down": "ext_alu"}
    b = [[1, 4, 3, 15, 11, 14, box], [1, 11, 2.5, 15, 11.6, 14.5, "ext_alu"]]
    for x in (2.5, 7, 11.5):
        b.append([x, 6, 2.6, x + 2, 9, 3, "lamp_glass"])
    for x, z in ((2, 5), (13, 5), (2, 12), (13, 12)):
        b.append([x, 0, z, x + 1, 4, z + 1, METAL])
    return b


def sign_panel(face):
    """A tile of sign, a metre wide, the face to the north on its two frangible legs."""
    tex = {"north": face, "south": "af_sign_back", "side": "af_sign_back", "up": "af_sign_back", "down": "af_sign_back"}
    return [[0, 4, 6, 16, 15, 10, tex], [2, 0, 7.4, 3, 4, 8.6, METAL], [13, 0, 7.4, 14, 4, 8.6, METAL],
            [1.6, 0, 7, 3.4, 0.8, 9, "concrete_dark"], [12.6, 0, 7, 14.4, 0.8, 9, "concrete_dark"]]


def sign_end():
    return [[0, 4, 6, 1.2, 15, 10, "af_sign_back"]]


def windsock_mast():
    return [[6.5, 0, 6.5, 9.5, 1, 9.5, "concrete_dark"], [7.3, 1, 7.3, 8.7, 16, 8.7, METAL]]


def windsock():
    """The mast's head: the swivel and its arm, the hoop, the sock hung slack down out of it to the
    north, tapering, in three lengths."""
    s = "af_sock"
    b = [[7.3, 0, 7.3, 8.7, 10.5, 8.7, METAL], [7.5, 9.5, 2, 8.5, 10.5, 8, METAL],
         [4, 13, 1, 12, 13.6, 2, METAL], [4, 6, 1, 12, 6.6, 2, METAL], [4, 6, 1, 4.6, 13.6, 2, METAL],
         [11.4, 6, 1, 12, 13.6, 2, METAL]]
    droop = {"axis": "x", "angle": -45, "origin": [8, 9.8, 1.5]}
    for q in ([4.4, 6.4, -4, 11.6, 13.2, 1], [5, 7, -9, 11, 12.6, -4], [5.8, 7.8, -13, 10.2, 11.8, -9]):
        b.append(q + [s, None, droop])
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="METAL", cutout=True, light=0):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape}
        if rotate:
            spec["rotate"] = rotate
        if cutout:
            spec["render"] = "cutout"
        if light:
            spec["light"] = light
            spec["decor"] = True
        out[name] = spec

    def ground(name, tex, title, rotate=None):
        out[name] = {"tex": tex, "title": title, "sound": "STONE", "height": 14}
        if rotate:
            out[name]["rotate"] = rotate

    for n in ("af_rwy", "af_rwy_rubber", "af_rwy_tar", "af_paint", "af_paint_rubber", "af_paint_y"):
        ground(n, n, "Runway", "axis" if n in ("af_rwy_rubber", "af_paint_rubber") else None)
    ground("af_paint_y_half", "af_paint_y_half", "Runway", "facing")
    ground("af_edge", "af_edge", "Runway", "facing")
    ground("af_twy", "af_twy", "Taxiway")
    ground("af_twy_cl", "af_twy_cl", "Taxiway", "axis")
    ground("af_twy_edge", "af_twy_edge", "Taxiway", "facing")
    ground("af_hold", "af_hold", "Taxiway", "facing")
    ground("af_apron", "af_apron", "Apron")
    ground("af_apron_j", "af_apron_j", "Apron", "facing")
    ground("af_apron_jj", "af_apron_jj", "Apron", "facing")
    ground("af_apron_lead", "af_apron_lead", "Apron", "axis")

    lens = {"side": "lamp_glass", "up": "ext_black_metal", "down": "ext_black_metal"}
    blue = {"side": "af_lens_blue", "up": "ext_black_metal", "down": "ext_black_metal"}
    thr = {"north": "af_lens_green", "south": "af_lens_red", "side": "af_lens_green", "up": "ext_black_metal", "down": "ext_black_metal"}
    small = [[5.5, 0, 5.5, 10.5, 13, 10.5]]
    add("af_light_edge", light(lens), "Runway Light", small)
    add("af_light_twy", light(blue), "Taxiway Light", small)
    add("af_light_thr", light(thr), "Threshold Light", small)
    add("af_light_broken", broken_light(), "Runway Light", [[5.5, 0, 5.5, 10.5, 3, 16]])
    add("af_als_mast", als_mast(), "Approach Light Mast", [[5, 0, 5, 11, 16, 11]], rotate=None)
    add("af_als_bar", als_bar(), "Approach Lights", [[0, 0, 5.6, 16, 6, 9.4]])
    add("af_papi", papi(), "PAPI", [[1, 0, 2.5, 15, 11.6, 14.5]])
    for k in SIGNS:
        add(f"af_sign_{k}", sign_panel(f"af_sign_{k}"), "Airfield Sign", [[0, 0, 6, 16, 15, 10]])
    add("af_sign_end", sign_end(), "Airfield Sign", [[0, 4, 6, 1.2, 15, 10]])
    add("af_windsock_mast", windsock_mast(), "Windsock", [[6.5, 0, 6.5, 9.5, 16, 9.5]], rotate=None)
    add("af_windsock", windsock(), "Windsock", [[4, 0, 0, 12, 14, 9]], sound="WOOL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    clash = [k for k in data["blocks"] if k.startswith("af_")]
    made = blocks()
    stray = [k for k in clash if k not in made]
    if stray:
        sys.exit(f"af_ prefix already used by {stray[:5]}")
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("af_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} airfield blocks in {path}")


if __name__ == "__main__":
    main()
