"""The dm_ kit: Black Pine Dam's own pieces (Josiah, 2026-10-30: the northwest military zone, the
dam holding its reservoir up above the valley), after refs/dam: the concrete gravity dam and its
pour lines (concrete-arch-dam, Alum Creek), the gated spillway's piers, leaves and hoists
(spillway), the intake towers' trash racks (intake-towers), the crest road's parapets and lamps
(dam-crest-road), the inspection gallery (inspection-gallery) and the powerhouse's penstocks and
valves (powerhouse-turbine-hall). world/county/Dam.java puts them together.

- dm_conc(_stain, _moss, _wet): mass concrete, the horizontal lift lines of each pour, rust and
  lime bleeding from the joints, green where the spray reaches, black where it's always wet;
- dm_parapet: the crest's low parapet wall on the cell's north edge, its coping (facing);
- dm_gate / dm_gate_top: a spillway gate's steel leaf in the plane along x (axis), ribbed, the
  lifting chains up the top one; dm_hoist: the hoist on its deck, motor, gearbox and drum;
- dm_rack: an intake's trash rack, flat bars on edge in the plane (axis);
- dm_penstock: a riveted steel pipe along x or z (axis); dm_valve: its butterfly valve and wheel;
- dm_gauge: the staff gauge painted on the face, feet and tenths (a plate, facing);
- dm_floats: the boat barrier's floats on their cable, sitting on the water (axis);
- signs: dm_sgn_KEY_0..1; the name on the face dm_name_0..7; graffiti dm_spray_KEY_i.

    python3 tools/city/dam.py          rewrites the dm_ entries in blocks.json

photo_textures.py draws the textures (dam.register()).
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
import broadcast as B  # noqa: E402
from furniture import area, disc, drips  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD, STENCIL, SPRAY = B.BOLD, B.STENCIL, B.SPRAY
OLD = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
CONC = (140, 137, 129)
INK = (30, 28, 26)
WHITE = (240, 238, 230)
RED = (170, 32, 28)
NAVY = (30, 44, 68)
YELLOW = (226, 186, 40)
GATE = (88, 108, 96)


# ---------------------------------------------------------------- textures

def conc(seed, kind=""):
    """Mass concrete: a lift line every block where one pour sat on the last, the formwork's
    board marks faint between, lime and rust bled down from the joints; green where the spray
    keeps it damp, nearly black where the water always runs."""
    t = F.plain(seed, CONC, 0.06, 0.12)
    for y in (0, 31):
        area(t, 0, y, N, y + 1, mul=(0.82, 0.82, 0.8), dh=-0.2)
    for x in range(0, N, 21):
        area(t, x, 0, x + 1, N, mul=(0.95, 0.95, 0.95), dh=-0.05)
    t.mix(np.clip(P.fbm(t.rng) * 1.3, 0, 1) * 0.3, (150, 146, 136))
    t.streaks(0.7 if kind in ("stain", "wet") else 0.35, 0.7)
    if kind == "stain":
        for _ in range(5):
            x = int(t.rng.integers(2, 60))
            drips(t, x, x + 2, 1, 1, (12, 40), col=(132, 96, 66))
        t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.3, 0, 1) * 0.3, (214, 212, 202))
        t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.4, 0, 1) * 0.5, (96, 92, 84))
    if kind == "moss":
        t.mix(np.clip(P.fbm(t.rng) + 0.35, 0, 1) * 0.65, (78, 100, 56))
    if kind == "wet":
        t.mix(np.clip(P.fbm(t.rng) + 0.6, 0, 1) * 0.75, (52, 56, 50))
        t.smooth[:] = 0.7
    t.crack(2, 0.2, (96, 92, 84))
    return t


def gate_steel(seed, rust=False):
    """The gates' green paint over steel, rivet lines, rust weeping from every one."""
    t = F.plain(seed, GATE, 0.05, 0.4)
    t.f0[:] = 45
    for y in range(4, N, 15):
        for x in range(3, N, 6):
            disc(t, x, y, 0.9, col=(70, 86, 76), dh=0.3)
    m = np.clip(P.fbm(t.rng) * 2 - (1.0 if rust else 1.5), 0, 1)
    t.mix(m * 0.8, (122, 66, 36))
    t.grime(0.3, (50, 48, 40))
    return t


def staff_gauge(seed):
    """A staff gauge on the face: white enamel, black ticks every tenth, the foot numbers in red."""
    t = F.plain(seed, (232, 230, 222), 0.03, 0.5)
    for y in range(0, N, 6):
        area(t, 34 if y % 30 else 24, y, 44, y + 2, col=INK)
    area(t, 44, 0, 46, N, col=INK)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(16, 16, str(1300 + seed % 7), BOLD, 9, RED, anchor="mm")
    a = p.shrunk()
    m = a[..., 3] > 60
    t.c[m] = a[..., :3][m]
    t.grime(0.35, (70, 66, 56))
    return t


SIGNS = {
    "danger": ("DANGER|SPILLWAY", RED, WHITE),
    "swim": ("NO SWIMMING|STRONG CURRENTS", YELLOW, INK),
    "crest": ("DAM CREST|NO STOPPING", NAVY, WHITE),
    "power": ("POWERHOUSE|AUTHORIZED ONLY", NAVY, WHITE),
    "gallery": ("INSPECTION|GALLERY", NAVY, WHITE),
    "intake": ("INTAKE TOWER", NAVY, WHITE),
    "lift": ("DAM ELEVATOR", NAVY, WHITE),
    "lookout": ("OVERLOOK", (30, 90, 60), WHITE),
    "elev": ("RESERVOIR|ELEV 1306", WHITE, INK),
    "boats": ("BOATS KEEP|OUT", YELLOW, INK),
    "unit1": ("UNIT 1", NAVY, WHITE),
    "unit2": ("UNIT 2", NAVY, WHITE),
    "keeper": ("DAM TENDER", NAVY, WHITE),
}
SPRAYS = {
    "gates": ("OPEN THE GATES", 4),
    "drink": ("DONT DRINK IT", 4),
    "lights": ("LIGHTS STILL ON", 4),
}


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 52400 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 14, W - 1, H - 14, fill=field)
    p.rect(1, 14, W - 1, H - 14, outline=mixc(field, (0, 0, 0), 0.3), width=1)
    lines = text.split("|")
    font = STENCIL if field in (YELLOW, WHITE) else BOLD
    size = civic.fit(text, font, 12 if len(lines) == 1 else 9, W - 12)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.15
        p.text(W / 2, y, line, font, size, ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 52450 + i + len(key) * 11, smooth=0.5, grime=0.25)


@lru_cache(maxsize=None)
def name_pic():
    """The name cast into the face below the crest: raised letters, eight blocks wide."""
    p = Pic(8, 1, (0, 0, 0, 0), 52500)
    W, H = p.W, p.H
    p.text(W / 2, H / 2, "BLACK PINE DAM  1961", OLD, civic.fit("BLACK PINE DAM  1961", OLD, 30, W - 20),
           (150, 146, 134), anchor="mm")
    return p


def name_tile(i):
    return name_pic().tile(i, 52510 + i, smooth=0.2, grime=0.4)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 52600 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (200, 24, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 52610 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def textures():
    T = {
        "dm_conc": lambda: conc(52000),
        "dm_conc_stain": lambda: conc(52001, "stain"),
        "dm_conc_moss": lambda: conc(52002, "moss"),
        "dm_conc_wet": lambda: conc(52003, "wet"),
        "dm_gate": lambda: gate_steel(52004),
        "dm_gate_rust": lambda: gate_steel(52005, True),
        "dm_gauge": lambda: staff_gauge(52006),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"dm_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for i in range(8):
        T[f"dm_name_{i}"] = lambda i=i: name_tile(i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"dm_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

STEEL, GALV, BLACK, YEL = "painted_steel", "st_galv", "home_plastic_black", "hazard_stripes"


def parapet():
    """The parapet on the cell's north edge: the wall, the coping lipped over both faces."""
    return [[0, 0, 0, 16, 11, 5, "dm_conc"], [0, 11, -0.5, 16, 13, 5.5, "dm_conc_stain"]]


def gate(top):
    """A gate leaf in the plane z = 8 along x: the skin plate, the ribs and girders behind it, the
    seals at its sides; up the top one the two lifting chains."""
    b = [[0, 0, 7, 16, 16, 9, "dm_gate"]]
    b += [[0, y, 9, 16, y + 1.6, 10.6, "dm_gate_rust"] for y in (2, 8, 14)]
    b += [[x, 0, 9, 1.4 + x, 16, 11, "dm_gate"] for x in (1, 7.3, 13.6)]
    if top:
        b += [[x, 0, 6, x + 1.2, 16, 7, STEEL] for x in (3, 11.8)]
    return b


def hoist():
    """The gate hoist on the pier deck: the motor, the gearbox, the chain drum across, the guard."""
    return [[1, 0, 2, 15, 1, 14, STEEL], [2, 1, 3, 6, 6, 8, "dm_gate"], [6, 1, 4, 10, 7, 9, STEEL],
            [1, 5, 9, 15, 10, 13, "dm_gate_rust"], [0.5, 4, 8.5, 1.5, 11, 13.5, STEEL], [14.5, 4, 8.5, 15.5, 11, 13.5, STEEL],
            [10, 1, 3, 14, 3, 6, YEL]]


def rack():
    """A trash rack: flat bars on edge every two pixels in the plane x = 8, the cross ties."""
    b = [[7, 0, z, 9, 16, z + 0.8, GALV] for z in np.arange(0.6, 16, 2.2)]
    b += [[6.6, y, 0, 9.4, y + 1.2, 16, STEEL] for y in (1, 8, 14.8)]
    return b


def penstock():
    """A riveted pipe along x, about a block across: an octagon of plates, the flanged joint."""
    return [[0, 2, 4, 16, 14, 12, "dm_gate_rust"], [0, 4, 2, 16, 12, 14, "dm_gate_rust"],
            [0, 3, 3, 16, 13, 13, "dm_gate"], [7, 1.4, 1.4, 9, 14.6, 14.6, STEEL]]


def valve():
    """The butterfly valve in its pipe, the operator on top and its handwheel."""
    return penstock() + [[5, 14, 5, 11, 16, 11, STEEL]]


def floats():
    """The boat barrier: orange floats on the cable along x, low on the water."""
    b = [[0, 3, 7.6, 16, 3.6, 8.4, BLACK]]
    b += [[x, 1, 5.5, x + 5, 5, 10.5, "st_cone"] for x in (0.5, 10.5)]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis":
            # RULE: axis models run along z as drawn; axis= names the way a row of them runs
            if P.drawn_along(boxes) == "x" and "shape" in extra:
                extra["shape"] = [[b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]] for b in extra["shape"]]
            boxes = P.along_z(boxes)
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    for n in ("dm_conc", "dm_conc_stain", "dm_conc_moss", "dm_conc_wet"):
        add(n, None, "Dam Concrete", "STONE", rotate=None, tex=n)
    add("dm_parapet", parapet(), "Parapet", "STONE", shape=[[0, 0, 0, 16, 13, 5.5]])
    add("dm_gate", gate(False), "Spillway Gate", "METAL", rotate="axis", shape=[[0, 0, 7, 16, 16, 11]])
    add("dm_gate_top", gate(True), "Spillway Gate", "METAL", rotate="axis", render="cutout", shape=[[0, 0, 6, 16, 16, 11]])
    add("dm_hoist", hoist(), "Gate Hoist", "METAL", shape=[[1, 0, 2, 15, 11, 14]])
    add("dm_rack", rack(), "Trash Rack", "METAL", rotate="axis", render="cutout", shape=[[6.6, 0, 0, 9.4, 16, 16]])
    add("dm_penstock", penstock(), "Penstock", "METAL", rotate="axis", shape=[[0, 2, 2, 16, 14, 14]])
    add("dm_valve", valve(), "Penstock Valve", "METAL", rotate="axis", shape=[[0, 2, 2, 16, 16, 14]])
    add("dm_gauge", plate("dm_gauge"), "Staff Gauge", "METAL", decor=True)
    add("dm_floats", floats(), "Boat Barrier", "WOOL", rotate="axis", decor=True)
    for key in SIGNS:
        for i in range(2):
            add(f"dm_sgn_{key}_{i}", plate(f"dm_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for i in range(8):
        add(f"dm_name_{i}", plate(f"dm_name_{i}", 14.6), "Cast Lettering", "STONE", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"dm_spray_{key}_{i}", plate(f"dm_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("dm_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} dam blocks in {path}")


if __name__ == "__main__":
    main()
