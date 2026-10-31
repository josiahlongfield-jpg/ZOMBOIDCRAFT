"""The fr_ kit: Foundry Row's own pieces (Josiah, 2026-10-30: the coast and industry places,
Foundry Row last), after refs/industry: the riveted furnace jackets and stoves gone to rust
(blast-furnace, hot-stoves-dust-catcher: Carrie, Sloss, Duisburg), the cast house floor, its sand
and runners and the iron skulls left in them (blast-furnace-cast-house, casting-floor), the coke
ovens' sooty brick faces and their doors (coke-ovens, railway-coke-quench), the ore, coke and slag
in their heaps (ore-yard-bridge, slag-heap), ingot moulds and steel in the yard (ingot-moulds-yard),
the bar mill's roller tables and roll stands (rolling-mill, mill-hall-crane), the skip car, slag pot
and torpedo car (skip-hoist-stockhouse, ladles-hot-metal-cars), the wash house baskets on their
chains (locker-room-washhouse), the time card rack (mill-offices-timeclock), and the works' signs.
world/county/Foundry*.java puts them together.

- fr_rust / fr_rust_seam / fr_blue / fr_firebrick / fr_slag / fr_skull / fr_castsand / fr_ovenface:
  whole blocks (none);
- fr_ore, fr_coke, fr_cinder (+ _1.._3 layers): a heap's stuff, as ports.py's bulk_;
- fr_runner: a sand runner on the cast floor with the iron gone hard in it (axis);
- fr_tuyere: a blowpipe down from the bustle main (facing);
- fr_tap / fr_tap_lit: a furnace's iron notch, plugged, or still glowing (facing);
- fr_oven_door: a coke oven door between its buckstays (facing);
- fr_ingot (none), fr_slabs / fr_billets / fr_coil / fr_rollers (axis), fr_mould (none);
- fr_rollstand (facing), fr_skip (facing), fr_slagpot (none), fr_torp_top / fr_torp_bot (facing);
- fr_basket (none), fr_cardrack (facing);
- signs fr_sgn_KEY_0..1, sprays fr_spray_KEY_i, the company's name fr_name_0..23 (12 by 2).

    python3 tools/city/foundry.py      rewrites the fr_ entries in blocks.json

photo_textures.py draws the textures (foundry.register()).
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
import ports  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = B.BOLD
SPRAY = B.SPRAY
INK = (30, 28, 26)
WHITE = (240, 238, 230)
CREAM = (228, 218, 190)
RED = (170, 34, 32)
NAVY = (34, 52, 72)
YELLOW = (232, 190, 34)
GREEN = (30, 70, 52)
NAME = "CALDER STEEL"


# ---------------------------------------------------------------- textures

def rust_plate(seed, seam):
    """A furnace jacket's plate gone to rust: orange-brown, scaled, darker in the laps; a row of
    rivets along the seam."""
    t = F.mat("Rust009", (122, 70, 44), 1.1, span=2, seed=seed)
    t.vary(0.12, tint=(14, 6, 0))
    if seam:
        area(t, 0, N // 2 - 3, N, N // 2 + 3, mul=(0.8, 0.78, 0.76), dh=0.3)
        for x in range(4, N, 10):
            disc(t, x, N // 2, 2.2, mul=(0.7, 0.66, 0.62), dh=0.4)
            disc(t, x - 0.6, N // 2 - 0.6, 0.8, mul=(1.25, 1.2, 1.1))
    t.streaks(0.6, 0.72)
    t.grime(0.2, (54, 34, 24))
    return t


def blue_steel(seed):
    """Steelwork painted a grey blue once (Duisburg-Nord): the paint chalked off, rust through it."""
    t = F.mat("PaintedMetal004", (96, 116, 130), 0.4, span=2, seed=seed)
    ports.rust(t, 0.22)
    t.streaks(0.7, 0.75)
    t.grime(0.25, (60, 50, 40))
    return t


def firebrick(seed):
    """Yellow firebrick, the joints tight, scorched black where the heat came through."""
    t = F.mat("Bricks101", (196, 168, 112), 0.8, span=2, seed=seed)
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.5, 0, 1) * 0.7, (40, 34, 30))
    return t


def slag(seed):
    """Blast furnace slag: grey-black, glassy in places, rusty where iron came with it."""
    t = F.mat("Rock036", (72, 70, 70), 1.2, span=2, seed=seed)
    t.speckle(0.04, (150, 150, 156), 0.6, 0.1)
    t.mix(np.clip(P.fbm(t.rng) * 1.4 - 0.6, 0, 1) * 0.5, (110, 64, 40))
    return t


def ore(seed):
    """Hematite ore: red-brown lumps and fines, staining everything it touches."""
    t = F.mat("Gravel023", (128, 58, 40), 1.3, span=2, seed=seed)
    t.speckle(0.03, (60, 30, 24), 0.6)
    return t


def coke(seed):
    """Furnace coke: silver-black, porous lumps, the fines between."""
    t = F.mat("Gravel023", (52, 52, 54), 1.4, span=2, seed=seed)
    t.speckle(0.05, (150, 150, 150), 0.5, 0.1)
    t.smooth[:] = 0.35
    return t


def cinder(seed):
    """Cinder and slag spoil on a dump: grey, the odd rusty lump, weeds' seeds blown in."""
    t = F.mat("Ground037", (96, 92, 88), 1.1, span=2, seed=seed)
    t.speckle(0.04, (120, 70, 50), 0.5)
    return t


def skull(seed):
    """Iron gone solid where it was left: dark grey, the skin crazed and blued, oxide at the edges."""
    t = F.mat("Metal041B", (70, 70, 74), 1.0, span=2, seed=seed)
    t.crack(8, 0.2, (30, 30, 34))
    t.mix(np.clip(P.fbm(t.rng) * 1.3 - 0.4, 0, 1) * 0.4, (64, 70, 92))
    ports.rust(t, 0.1)
    return t


def castsand(seed):
    """The cast house floor's sand: brown-grey, trodden, iron spatter and slag beads in it."""
    t = F.mat("Ground037", (122, 104, 86), 0.9, span=2, seed=seed)
    t.speckle(0.02, (60, 58, 60), 0.7, 0.05)
    t.speckle(0.01, (150, 80, 40), 0.5)
    return t


def ovenface(seed):
    """A coke oven battery's end: brick black with soot and tar, the steel buckstays rusty."""
    t = F.mat("Bricks101", (70, 52, 44), 0.9, span=2, seed=seed)
    t.grime(0.7, (24, 22, 20))
    t.streaks(1.0, 0.6)
    return t


def oven_door(seed):
    """A coke oven door: a cast plate, its luting cracked white round the edge, the latch bar."""
    t = rust_plate(seed, False)
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.3, 0, 1) * 0.6, (34, 30, 28))
    area(t, 0, 0, N, 3, col=(200, 196, 186), dh=0.2)
    area(t, 0, N - 3, N, N, col=(200, 196, 186), dh=0.2)
    area(t, 0, N // 2 - 2, N, N // 2 + 2, col=(54, 48, 44), dh=0.4)
    return t


def ingot(seed):
    """A steel ingot: dark mill scale, rust bloomed on it where it stood out."""
    t = F.mat("Metal041B", (58, 56, 56), 1.0, span=2, seed=seed)
    ports.rust(t, 0.25)
    return t


def coil_end(seed):
    """A coil's end: the wraps of strip in rings, oiled grey."""
    t = F.plain(seed, (120, 122, 126), 0.04, 0.6)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - N / 2 + 0.5, yy - N / 2 + 0.5)
    t.c *= (0.88 + 0.12 * np.cos(r * 1.7))[..., None]
    t.c[r < N * 0.16] = (30, 30, 32)
    ports.rust(t, 0.05)
    return t


def roller(seed):
    """A roller table's rolls: polished where the steel ran, rust at the ends."""
    t = F.plain(seed, (110, 110, 114), 0.04, 0.7)
    for y in range(0, N, N // 4):
        area(t, 0, y, N, y + 2, mul=(0.5, 0.5, 0.5), dh=-0.3)
        area(t, 0, y + 3, N, y + 5, mul=(1.3, 1.3, 1.3))
    ports.rust(t, 0.12)
    return t


def tap_glow(seed):
    """Iron glowing in a plugged notch: orange through the cracks in the clay."""
    t = F.plain(seed, (40, 30, 26), 0.08, 0.2)
    t.crack(14, 0.1, (255, 140, 40))
    m = (t.c[..., 0] > 150).astype(np.float32)
    t.emit = np.maximum(t.emit, P.blur(m, 1) * 2)
    return t


def basket(seed):
    """A wash house basket: wire mesh, a man's clothes still in it, the boots on top."""
    t = F.plain(seed, (90, 92, 96), 0.05, 0.4)
    for x in range(0, N, 4):
        area(t, x, 0, x + 1, N, mul=(0.6, 0.6, 0.6))
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=(0.6, 0.6, 0.6))
    rng = t.rng
    for _ in range(3):
        c = [(70, 80, 110), (90, 70, 50), (130, 120, 110), (60, 70, 60)][int(rng.integers(4))]
        y = int(rng.integers(4, N - 10))
        area(t, 2, y, N - 2, y + int(rng.integers(6, 12)), col=c)
    return t


def cards(seed):
    """A time card rack: the slots, the cards in them, a name typed on each, the clock's punches."""
    t = F.plain(seed, (110, 112, 104), 0.04, 0.4)
    rng = t.rng
    for y in range(2, N - 4, 8):
        area(t, 1, y + 5, N - 1, y + 7, col=(70, 72, 68), dh=0.3)
        for x in range(2, N - 4, 6):
            if rng.random() < 0.85:
                area(t, x, y, x + 4, y + 6, col=(222, 214, 184) if rng.random() < 0.7 else (200, 170, 120))
                area(t, x + 1, y + 1, x + 3, y + 2, col=(80, 70, 60))
    return t


SIGNS = {
    "gate": ("CALDER STEEL COMPANY|FOUNDRY ROW WORKS", NAVY, CREAM),
    "office": ("GENERAL OFFICE", NAVY, CREAM),
    "employ": ("EMPLOYMENT OFFICE|NO HIRING", NAVY, CREAM),
    "time": ("TIME OFFICE|PUNCH IN - PUNCH OUT", NAVY, CREAM),
    "wash": ("WASH HOUSE|LOCKERS", NAVY, CREAM),
    "furnace1": ("No. 1 FURNACE", WHITE, INK),
    "furnace2": ("No. 2 FURNACE", WHITE, INK),
    "cast": ("CAST HOUSE|AUTHORIZED ONLY", YELLOW, INK),
    "hotmetal": ("DANGER|HOT METAL CROSSING", YELLOW, INK),
    "coke": ("COKE WORKS", NAVY, CREAM),
    "gas": ("DANGER - GAS|NO SMOKING NO FLAME", RED, WHITE),
    "ore": ("ORE YARD", NAVY, CREAM),
    "hearth": ("OPEN HEARTH No. 2", NAVY, CREAM),
    "mill": ("28 INCH BAR MILL", NAVY, CREAM),
    "machine": ("MACHINE SHOP", NAVY, CREAM),
    "blowing": ("BLOWING ENGINE HOUSE", NAVY, CREAM),
    "safety": ("SAFETY FIRST|THINK BEFORE YOU ACT", GREEN, WHITE),
    "days": ("0 DAYS|WITHOUT A LOST TIME ACCIDENT", GREEN, WHITE),
    "hats": ("HARD HATS|REQUIRED BEYOND THIS POINT", YELLOW, INK),
    "crane": ("WATCH FOR|OVERHEAD CRANES", YELLOW, INK),
    "rail": ("RAILROAD CROSSING|STOP LOOK LISTEN", WHITE, INK),
    "slag": ("SLAG DUMP|KEEP OFF", RED, WHITE),
    "store": ("CALDER MERCANTILE CO.|GENERAL STORE", (96, 40, 32), CREAM),
    "union": ("IRON & STEEL WORKERS|LOCAL 1919 HALL", (40, 60, 110), WHITE),
    "tavern": ("THE CAST HOUSE|TAVERN", (40, 30, 24), (226, 180, 90)),
    "row": ("FOUNDRY ROW", GREEN, WHITE),
}

SPRAYS = {
    "lastcast": ("LAST CAST 10-21", 4),
    "hot": ("No. 1 STILL HOT", 3),
    "holding": ("WE HOLD THE CAST HOUSE", 5),
    "food": ("FOOD + WATER AT THE HALL", 5),
    "local": ("1919 NEVER QUIT", 4),
}


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 61200 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 12, W - 1, H - 12, fill=field)
    p.rect(3, 14, W - 3, H - 14, outline=ink, width=1)
    lines = text.split("|")
    size = civic.fit(text, BOLD, 12 if len(lines) == 1 else 9, W - 14)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.15
        p.text(W / 2, y, line, BOLD, size, ink, anchor="mm")
    return p


def sign(key, i):
    t = sign_pic(key).tile(i, 61250 + i + len(key) * 11, smooth=0.5, grime=0.35)
    return t


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 61400 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key not in ("food", "local") else (236, 236, 228), jitter=0.4, a=235, angle=-2)
    return p


@lru_cache(maxsize=None)
def name_pic():
    """CALDER STEEL in white capitals painted on the bar mill's side, twelve blocks by two."""
    p = Pic(12, 2, (0, 0, 0, 0), 61500)
    p.text(p.W / 2, p.H / 2 + 2, NAME, BOLD, civic.fit(NAME, BOLD, 120, p.W - 30), (232, 228, 214), anchor="mm")
    return p


def name_tile(i):
    t = name_pic().tile(i, 61510 + i, smooth=0.3, grime=0.4)
    return t


def textures():
    T = {
        "fr_rust": lambda: rust_plate(61000, False),
        "fr_rust_seam": lambda: rust_plate(61001, True),
        "fr_blue": lambda: blue_steel(61002),
        "fr_firebrick": lambda: firebrick(61003),
        "fr_slag": lambda: slag(61004),
        "fr_ore": lambda: ore(61005),
        "fr_coke": lambda: coke(61006),
        "fr_cinder": lambda: cinder(61007),
        "fr_skull": lambda: skull(61008),
        "fr_castsand": lambda: castsand(61009),
        "fr_ovenface": lambda: ovenface(61010),
        "fr_oven_door": lambda: oven_door(61011),
        "fr_ingot": lambda: ingot(61012),
        "fr_coil_end": lambda: coil_end(61013),
        "fr_roller": lambda: roller(61014),
        "fr_tap_glow": lambda: tap_glow(61015),
        "fr_basket": lambda: basket(61016),
        "fr_cards": lambda: cards(61017),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"fr_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"fr_spray_{key}_{i}"] = lambda key=key, i=i: spray_pic(key).tile(i, 61450 + i + len(key), smooth=0.3, grime=0.0)
    for i in range(24):
        T[f"fr_name_{i}"] = lambda i=i: name_tile(i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

RUST, SKULL, SAND, STEEL, DARK = "fr_rust", "fr_skull", "fr_castsand", "ext_black_metal", "ext_black_metal"
CLAY = "concrete_dark"


def runner():
    # the sand bed, the trough down its middle (along z), the iron hard in it
    return [[0, 0, 0, 16, 12, 16, SAND], [0, 12, 0, 5, 14, 16, SAND], [11, 12, 0, 16, 14, 16, SAND],
            [5, 9, 0, 11, 11, 16, SKULL]]


def tuyere():
    # down from the bustle (top, back) to the furnace wall (bottom, front): the gooseneck and the blowpipe
    return [[5, 12, 10, 11, 16, 16, RUST], [6, 6, 8, 10, 12, 12, RUST], [6.5, 2, 4, 9.5, 7, 9, RUST],
            [7, 0, 0, 9, 3, 5, STEEL], [5.5, 7, 7, 10.5, 8, 13, STEEL]]


def tap(lit):
    face = "fr_tap_glow" if lit else CLAY
    return [[0, 0, 12, 16, 16, 16, "fr_firebrick"], [4, 2, 11, 12, 8, 12, {"north": face, "south": CLAY, "side": CLAY, "up": CLAY, "down": CLAY}]]


def oven_door_model():
    door = {"north": "fr_oven_door", "south": RUST, "side": RUST, "up": RUST, "down": RUST}
    return [[1, 0, 13, 15, 16, 15.5, door], [0, 0, 15.5, 16, 16, 16, "fr_ovenface"],
            [0, 0, 11, 1.5, 16, 16, RUST], [14.5, 0, 11, 16, 16, 16, RUST], [2, 7, 12, 14, 9, 13, STEEL]]


def ingots():
    out = []
    for i, (x, z) in enumerate(((1, 1), (9, 1), (1, 9), (9, 9))):
        out.append([x, 0, z, x + 6, 14 - i, z + 6, "fr_ingot"])
        out.append([x + 0.5, 14 - i, z + 0.5, x + 5.5, 15 - i, z + 5.5, "fr_ingot"])
    return out


def slabs():
    # three slabs along x with timber spacers
    return [[0, 0, 1, 16, 4, 15, "fr_ingot"], [2, 4, 2, 4, 5, 14, "cp_timber"], [12, 4, 2, 14, 5, 14, "cp_timber"],
            [0, 5, 1, 16, 9, 15, "fr_ingot"], [2, 9, 2, 4, 10, 14, "cp_timber"], [12, 9, 2, 14, 10, 14, "cp_timber"],
            [0, 10, 1, 16, 14, 15, "fr_ingot"]]


def billets():
    out = []
    for row, y in enumerate((0, 5, 10)):
        for k in range(3):
            z = 1.5 + k * 4.5 + (row % 2) * 0.8
            out.append([0, y, z, 16, y + 4.5, z + 4, "fr_ingot"])
    out.append([2, 0, 0.5, 3, 15, 15.5, "cp_timber"])
    return out


def coil():
    # a coil on its side along x in a timber cradle: its ends round-ish in steps
    end = {"east": "fr_coil_end", "west": "fr_coil_end", "side": "pw_tg_plate", "up": "pw_tg_plate", "down": "pw_tg_plate"}
    return [[1, 2, 4, 15, 14, 12, end], [1, 4, 2, 15, 12, 14, end], [1, 3, 3, 15, 13, 13, end],
            [3, 0, 1, 5, 3, 15, "cp_timber"], [11, 0, 1, 13, 3, 15, "cp_timber"]]


def rollers():
    # a roller table along x: the side frames, four rolls across it
    out = [[0, 0, 0, 16, 9, 2, "fr_rust"], [0, 0, 14, 16, 9, 16, "fr_rust"]]
    for x in (1, 5, 9, 13):
        out.append([x, 7, 2, x + 2.5, 9.5, 14, "fr_roller"])
    out.append([0, 0, 2, 16, 1, 14, "fr_slag"])
    return out


def rollstand():
    # the housing posts either side, the two rolls between, the screwdown on top
    return [[0, 0, 4, 4, 16, 12, "fr_blue"], [12, 0, 4, 16, 16, 12, "fr_blue"], [4, 4, 5, 12, 8, 11, "fr_roller"],
            [4, 8.5, 5, 12, 12.5, 11, "fr_roller"], [3, 14, 6, 13, 16, 10, STEEL], [0, 0, 3, 16, 1, 13, "fr_rust"]]


def skip():
    # a skip car: the bucket on its wheels, its bail
    return [[2, 2, 1, 14, 12, 15, RUST], [3, 12, 2, 13, 13, 14, "fr_ore"], [1, 0, 2, 3, 3, 5, STEEL], [13, 0, 2, 15, 3, 5, STEEL],
            [1, 0, 11, 3, 3, 14, STEEL], [13, 0, 11, 15, 3, 14, STEEL], [7, 12, 0, 9, 16, 2, STEEL]]


def slagpot():
    # the pot in its ring on the car's cradle, slag gone solid in it
    return [[0, 0, 0, 16, 3, 16, STEEL], [3, 3, 3, 13, 6, 13, RUST], [1.5, 6, 1.5, 14.5, 12, 14.5, RUST],
            [0.5, 12, 0.5, 15.5, 15, 15.5, RUST], [1.5, 14, 1.5, 14.5, 14.5, 14.5, "fr_slag"]]


def torp(top):
    # the round of a torpedo car's body in its outer quarter, steps of plate
    if top:
        return [[0, 0, 0, 16, 6, 16, RUST], [0, 6, 4, 16, 11, 16, RUST], [0, 11, 9, 16, 14, 16, RUST], [0, 14, 13, 16, 15.5, 16, RUST]]
    return [[0, 10, 0, 16, 16, 16, RUST], [0, 5, 4, 16, 10, 16, RUST], [0, 2, 9, 16, 5, 16, RUST], [0, 0.5, 13, 16, 2, 16, RUST]]


def basket_model():
    return [[7.5, 4, 7.5, 8.5, 16, 8.5, STEEL], [3, 0, 3, 13, 5, 13, "fr_basket"], [5, 5, 5, 11, 6.5, 11, "ext_black_metal"]]


def cardrack():
    return [[0, 0, 14, 16, 16, 16, "fr_cards"]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, **extra):
        if boxes and rotate == "axis":
            # RULE: axis models run along z as drawn; axis= names the way a row of them runs
            if P.drawn_along(boxes) == "x" and "shape" in extra:
                extra["shape"] = [[b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]] for b in extra["shape"]]
            boxes = P.along_z(boxes)
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    for name, title, sound in (("fr_rust", "Rusted Plate", "METAL"), ("fr_rust_seam", "Riveted Plate", "METAL"),
                               ("fr_blue", "Painted Steel", "METAL"), ("fr_firebrick", "Firebrick", "STONE"),
                               ("fr_slag", "Slag", "STONE"), ("fr_skull", "Iron Skull", "METAL"),
                               ("fr_castsand", "Cast House Sand", "SAND"), ("fr_ovenface", "Coke Oven Brick", "STONE")):
        add(name, None, title, sound, rotate=None, tex=name)
    for m, title, snd in (("ore", "Iron Ore", "GRAVEL"), ("coke", "Coke", "GRAVEL"), ("cinder", "Cinder", "GRAVEL")):
        add(f"fr_{m}", None, title, snd, rotate=None, tex=f"fr_{m}")
        for n in (1, 2, 3):
            add(f"fr_{m}_{n}", [[0, 0, 0, 16, 4 * n, 16, f"fr_{m}"]], title, snd, rotate=None, shape=[[0, 0, 0, 16, 4 * n, 16]])
    add("fr_runner", runner(), "Iron Runner", "SAND", rotate="axis", shape=[[0, 0, 0, 16, 14, 16]])
    add("fr_tuyere", tuyere(), "Tuyere", "METAL", decor=True)
    add("fr_tap", tap(False), "Iron Notch", "STONE", shape=[[0, 0, 11, 16, 16, 16]])
    add("fr_tap_lit", tap(True), "Iron Notch", "STONE", light=9, shape=[[0, 0, 11, 16, 16, 16]])
    add("fr_oven_door", oven_door_model(), "Coke Oven Door", "METAL", shape=[[0, 0, 11, 16, 16, 16]])
    add("fr_ingot", ingots(), "Steel Ingots", "METAL", rotate=None, shape=[[1, 0, 1, 15, 15, 15]])
    add("fr_slabs", slabs(), "Steel Slabs", "METAL", rotate="axis", shape=[[0, 0, 1, 16, 14, 15]])
    add("fr_billets", billets(), "Steel Billets", "METAL", rotate="axis", shape=[[0, 0, 1, 16, 15, 15]])
    add("fr_coil", coil(), "Steel Coil", "METAL", rotate="axis", shape=[[1, 0, 1, 15, 14, 15]])
    add("fr_rollers", rollers(), "Roller Table", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 9.5, 16]])
    add("fr_mould", [[3, 0, 3, 13, 16, 13, "fr_ingot"], [2, 0, 2, 14, 2, 14, "fr_ingot"], [5, 15, 5, 11, 16, 11, "fr_skull"]],
        "Ingot Mould", "METAL", rotate=None, shape=[[2, 0, 2, 14, 16, 14]])
    add("fr_rollstand", rollstand(), "Roll Stand", "METAL", shape=[[0, 0, 3, 16, 16, 13]])
    add("fr_skip", skip(), "Skip Car", "METAL", shape=[[1, 0, 1, 15, 13, 15]])
    add("fr_slagpot", slagpot(), "Slag Pot", "METAL", rotate=None, shape=[[0, 0, 0, 16, 15, 16]])
    add("fr_torp_top", torp(True), "Torpedo Car", "METAL", shape=[[0, 0, 0, 16, 14, 16]])
    add("fr_torp_bot", torp(False), "Torpedo Car", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    add("fr_basket", basket_model(), "Wash House Basket", "METAL", rotate=None, decor=True)
    add("fr_cardrack", cardrack(), "Time Card Rack", "METAL", decor=True)
    plate = lambda tex, z0=15.2: [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]
    for key in SIGNS:
        for i in range(2):
            add(f"fr_sgn_{key}_{i}", plate(f"fr_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"fr_spray_{key}_{i}", plate(f"fr_spray_{key}_{i}", 15.8), "Spray Paint", "STONE", decor=True, render="cutout")
    for i in range(24):
        add(f"fr_name_{i}", plate(f"fr_name_{i}", 15.8), "Painted Name", "METAL", decor=True, render="cutout")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("fr_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} foundry blocks in {path}")


if __name__ == "__main__":
    main()
