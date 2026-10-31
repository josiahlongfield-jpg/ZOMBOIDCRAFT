"""The rv_ kit: what Zomboid's road stories leave on the county's roads (world/county/RoadStories),
drawn from the photos in refs/stories/road (police-blockade, police-blockade-shooting,
changing-tire, construction-site, burnt-car, bandit-road, utility-vehicle and the crashes).

- rv_flare / rv_flare_out: a road flare lying on the asphalt, burning red, or burnt down to ash;
- rv_jack: a scissor jack left wound part way up, its crank rod out to one side (searchable: the
  jack and lug wrench, loot=story_changing_tire); rv_lug_wrench: a four-way wheel brace;
- rv_skid (along z, rotate=axis) and rv_fluid: rubber laid down by locked wheels, and a pool of
  oil and coolant run out of a wreck; rv_debris: a torn-off bumper end, a smashed headlamp, bits;
- rv_trunk / rv_trunk_crown: a felled tree across the road (along z, rotate=axis), its cut butt
  and its dead crown of branches;
- rv_sign_work: the folding diamond "ROAD WORK AHEAD" sign on its stand; rv_closed /
  rv_closed_sign: a striped type III barricade, the second with "ROAD CLOSED" across its top;
- rv_spool: a wooden cable drum on its side (rotate=axis); rv_tape: police line strung at waist
  height (rotate=facing, drawn across x);
- rv_case: a police officer's black hard case on the ground (searchable,
  loot=story_police_blockade); rv_tin: a toolbox burnt black with the car it came out of
  (searchable, loot=story_burnt_car).

    python3 tools/city/roadstory.py      rewrites the rv_ entries in blocks.json

photo_textures.py draws the textures (roadstory.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import blob_mask  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips, stencil  # noqa: E402

N = P.N
HERE = Path(__file__).parent
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
ORANGE, WHITE, BLACK, YELLOW = (232, 110, 20), (238, 238, 232), (22, 22, 22), (240, 200, 30)
STEEL, GALV, RUST, BARK = "painted_steel", "st_galv", "rust", "tree_dead_bark"


# ---------------------------------------------------------------- textures

def flare(seed, glow):
    """The flare's red waxed-paper tube (refs: police-blockade), or its burning head."""
    if glow:
        t = F.plain(seed, (255, 120, 80), 0.1, 0.6)
        t.mix(blob_mask(t.rng, 32, 32, 18, rough=0.6), (255, 236, 200), 0.8)
        t.emit[:] = 1.0
        return t
    t = F.plain(seed, (178, 26, 22), 0.08, 0.35)
    t.c[:, 28:36] = t.c[:, 28:36] * 0.6 + np.array(WHITE, np.float32) * 0.4   # the printed band
    t.grime(0.3, (60, 30, 20))
    return t


def ash(seed):
    t = F.plain(seed, (120, 116, 110), 0.2, 0.1)
    t.speckle(0.2, (40, 38, 36), 0.6)
    return t


def jack(seed):
    """The jack's pressed steel, black paint worn back to rust on the edges (refs: changing-tire)."""
    t = F.plain(seed, (46, 46, 48), 0.06, 0.3)
    chips(t, 0.12, under=(120, 70, 40))
    weather(t, rust=0.3, streak=0.2, grime=0.3)
    return t


def skid(seed):
    """Two bands of rubber laid down along z by locked wheels, broken up where the tyre bounced."""
    t = F.plain(seed, (26, 26, 26), 0.1, 0.15)
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    wob = np.sin(yy / N * np.pi * 2 + t.rng.uniform(0, 6)) * 1.5
    for cx in (12, 52):
        band = np.abs(xx - cx - wob) < 6 + np.sin(yy * 0.4) * 0.6
        fade = P.fbm(t.rng, ((8, 1.0), (16, 0.5))) > -0.35
        t.alpha = np.maximum(t.alpha, np.where(band & fade, 200, 0))
    t.alpha = np.where(t.alpha > 100, 255, 0).astype(np.float32)
    return t


def fluid(seed):
    """Oil and coolant run out of a wreck: a black pool with a rainbow sheen and a green edge."""
    t = F.plain(seed, (18, 18, 20), 0.1, 0.9)
    m = blob_mask(t.rng, 30, 34, 20, rough=0.7, wrap=False)
    for _ in range(4):
        m = np.maximum(m, blob_mask(t.rng, t.rng.uniform(12, 52), t.rng.uniform(12, 52), t.rng.uniform(3, 7), wrap=False))
    sheen = P.fbm(t.rng, ((8, 1.0), (16, 0.6)))
    t.c += np.stack([np.sin(sheen * 6) * 12, np.sin(sheen * 6 + 2) * 12, np.sin(sheen * 6 + 4) * 12], -1).astype(np.float32) * (m > 0.5)[..., None]
    edge = (m > 0.15) & (m < 0.3)
    t.c[edge] = t.c[edge] * 0.4 + np.array((60, 140, 60), np.float32) * 0.6
    t.alpha = np.where(m > 0.15, 255, 0).astype(np.float32)
    t.smooth[:] = 0.95
    return t


def sign_work(seed):
    """The orange diamond: black border and ROAD WORK AHEAD, scuffed; the corners cut away."""
    from PIL import Image, ImageDraw, ImageFont
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = N / 2
    d.polygon([(c, 0), (N - 1, c), (c, N - 1), (0, c)], fill=BLACK + (255,))
    d.polygon([(c, 3), (N - 4, c), (c, N - 4), (3, c)], fill=ORANGE + (255,))
    f = ImageFont.truetype(SANS, 8)
    for text, y in (("ROAD", 23), ("WORK", 32), ("AHEAD", 41)):
        d.text((c, y), text, font=f, fill=BLACK + (255,), anchor="mm")
    a = np.array(im).astype(np.float32)
    t = F.plain(seed, ORANGE, 0.04, 0.5)
    t.c = a[:, :, :3].copy()
    t.alpha = np.where(a[:, :, 3] > 128, 255, 0).astype(np.float32)
    t.vary(0.05)
    weather(t, rust=0.0, streak=0.4, grime=0.25)
    return t


def stripes(seed):
    """Type III barricade sheeting: orange and white at 45 degrees, scuffed."""
    t = F.plain(seed, ORANGE, 0.04, 0.45)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) // 12) % 2 == 1] = WHITE
    t.vary(0.05)
    t.grime(0.3, (60, 56, 50))
    chips(t, 0.03, under=(190, 190, 186))
    return t


def closed(seed):
    """ROAD CLOSED in black on a white board."""
    t = F.plain(seed, WHITE, 0.03, 0.45)
    stencil(t, "ROAD CLOSED", BLACK, 14, 2, 18, N - 4, 28, chip=0.05)
    t.grime(0.25, (70, 66, 60))
    return t


def tape(seed):
    """Police line: yellow tape, CRIME SCENE DO NOT CROSS in black (refs: police-blockade-shooting)."""
    t = F.plain(seed, YELLOW, 0.04, 0.5)
    stencil(t, "DO NOT CROSS", BLACK, 9, 1, 16, N - 2, 32, chip=0.02)
    t.c[14:16] = t.c[14:16] * 0.7
    t.c[48:50] = t.c[48:50] * 0.7
    t.grime(0.2, (90, 80, 40))
    return t


def cable(seed):
    t = F.plain(seed, (30, 30, 32), 0.05, 0.4)
    for y in range(0, N, 4):
        t.c[y] *= 0.6
    return t


def spool_end(seed):
    """The drum's plywood flange: the grain, the bolt circle, the stencilled maker's number."""
    t = F.mat("weathered_brown_planks", (150, 120, 84), 1.0, seed=seed)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - 31.5, yy - 31.5)
    t.c[(np.abs(r - 18) < 1.2)] *= 0.6
    stencil(t, "PGE 4", (30, 30, 30), 9, 16, 40, 32, 14, chip=0.3)
    return t


def case_lid(seed):
    t = F.plain(seed, (28, 28, 30), 0.04, 0.45)
    stencil(t, "POLICE", WHITE, 13, 2, 22, N - 4, 20, chip=0.2)
    t.grime(0.2, (80, 76, 70))
    return t


def burnt(seed):
    """A toolbox's steel burnt through: soot black, blistered to rust and white ash in places."""
    t = F.plain(seed, (40, 34, 30), 0.15, 0.1)
    t.mix(np.clip(P.fbm(t.rng) * 1.6, 0, 1), (130, 70, 36), 0.7)
    t.speckle(0.06, (200, 196, 186), 0.6)
    return t


def textures():
    return {
        "rv_flare": lambda: flare(88100, False),
        "rv_flare_glow": lambda: flare(88101, True),
        "rv_ash": lambda: ash(88102),
        "rv_jack": lambda: jack(88103),
        "rv_skid": lambda: skid(88104),
        "rv_fluid": lambda: fluid(88105),
        "rv_work": lambda: sign_work(88106),
        "rv_stripes": lambda: stripes(88107),
        "rv_closed": lambda: closed(88108),
        "rv_tape": lambda: tape(88109),
        "rv_cable": lambda: cable(88110),
        "rv_spool_end": lambda: spool_end(88111),
        "rv_case_lid": lambda: case_lid(88112),
        "rv_burnt": lambda: burnt(88113),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

EDGES = {"up": [0, 0, 16, 1], "down": [0, 0, 16, 1], "east": [0, 0, 1, 16], "west": [0, 0, 1, 16]}
FULL = dict(EDGES, north=[0, 0, 16, 16], south=[0, 0, 16, 16])


def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


def disc_strips(r, cx, cy, step=2.0):
    """Horizontal strips filling a circle of radius r round (cx, cy): (y0, y1, x0, x1)."""
    out = []
    y = cy - r
    while y < cy + r - 0.01:
        y1 = min(cy + r, y + step)
        m = (y + y1) / 2 - cy
        h = math.sqrt(max(0.0, r * r - m * m))
        out.append((round(y, 2), round(y1, 2), round(cx - h, 2), round(cx + h, 2)))
        y = y1
    return out


def flare_model(lit):
    """The tube lying along x, its striker cap off beside it; burning, the head's glow and smoke stain."""
    b = [[3, 0, 7.3, 12, 1.4, 8.7, "rv_flare"], [2.6, 0, 7.2, 3, 1.5, 8.8, "home_plastic_black"],
         [5, 0, 10, 7, 1.2, 11.2, "rv_flare"]]
    if lit:
        b += [[12, 0, 7.1, 13.6, 1.6, 8.9, "rv_flare_glow"], [13.6, 0.2, 7.4, 14.6, 1.3, 8.6, "rv_flare_glow"]]
    else:
        b[0][0] = 7
        b += [[4, 0, 6.6, 7, 0.2, 9.4, "rv_ash"]]
    return b


def jack_model():
    """Base plate, the four arms of the scissor in a diamond, the saddle, the long screw out to the
    side with its crank eye (refs/stories/road/changing-tire)."""
    J = "rv_jack"
    b = [[3, 0, 6, 13, 0.8, 10, J], [6, 6.8, 6.5, 10, 7.6, 9.5, J], [6.8, 7.6, 7, 9.2, 8.2, 9, RUST]]
    # the arms: lower pair rising from the base's ends to the knuckles, upper pair to the saddle
    for x0, ang in ((3.5, 42), (12.5, -42)):
        b.append([x0 - 0.6, 0.8, 6.3, x0 + 0.6, 5.6, 6.9, J, None, turn("z", ang, (x0, 0.8, 6.6))])
        b.append([x0 - 0.6, 0.8, 9.1, x0 + 0.6, 5.6, 9.7, J, None, turn("z", ang, (x0, 0.8, 9.4))])
    b += [[3.2, 3.6, 6.2, 4.6, 4.6, 9.8, J], [11.4, 3.6, 6.2, 12.8, 4.6, 9.8, J],   # the knuckles
          [0.2, 3.8, 7.6, 16, 4.4, 8.4, RUST], [0, 3.4, 7.2, 0.6, 4.8, 8.8, STEEL]]  # the screw and its eye
    return b


def lug_wrench():
    """A four-way wheel brace lying flat, turned on the diagonal, its sockets at the ends."""
    c = "hw_chrome"
    rot = turn("y", 45, (8, 0, 8))
    return [[1, 0, 7.4, 15, 0.9, 8.6, c, None, rot], [7.4, 0, 1, 8.6, 0.9, 15, c, None, rot],
            [0.6, 0, 7, 2.2, 1.4, 9, STEEL, None, rot], [13.8, 0, 7, 15.4, 1.4, 9, STEEL, None, rot]]


def debris():
    """A bumper's end torn off, a smashed headlamp housing, a few bits of trim and glass."""
    return [[1, 0, 2, 9, 2.4, 4, "home_plastic_black", None, turn("y", 22.5, (5, 0, 3))],
            [1, 2.4, 2.4, 8, 3, 3.8, "home_plastic_black", None, turn("y", 22.5, (5, 0, 3))],
            [9, 0, 9, 13, 2.6, 12, "hw_chrome", None, turn("y", -22.5, (11, 0, 10.5))],
            [9.4, 0.4, 8.8, 12.6, 2.2, 9, "glass_shards", None, turn("y", -22.5, (11, 0, 10.5))],
            [3, 0, 11, 5, 0.4, 12, "home_plastic_black"], [12, 0, 4, 13.5, 0.3, 5, "hw_chrome"],
            [0, 0, 0, 16, 0.2, 16, "glass_shards"]]


def trunk(crown):
    """The felled trunk along z: a round log of dead bark; the crown end with its limbs reaching out
    and up, the twigs dead (refs/stories/road/bandit-road)."""
    bark = {"side": BARK, "up": BARK, "down": BARK, "north": "tree_dead_rings", "south": "tree_dead_rings"}
    b = []
    zr = (0, 16) if not crown else (8, 16)
    for y0, y1, x0, x1 in disc_strips(5.5, 8, 5.5):
        b.append([x0, y0, zr[0], x1, y1, zr[1], bark if not crown else BARK])
    if crown:
        b += [[6.5, 2, 0, 8.5, 4, 9, BARK, None, turn("y", -22.5, (7.5, 3, 8))],
              [8, 4, 1, 9.5, 5.5, 9, BARK, None, turn("x", 22.5, (8.7, 4.7, 8))],
              [2, 6, 2, 4, 7.5, 10, BARK, None, turn("y", 22.5, (3, 6.7, 8))],
              [0, 0, 0, 16, 12, 6, "tree_dead_leaves"], [10, 5, 2, 16, 14, 9, "tree_dead_leaves"]]
    else:
        b += [[10.5, 9, 4, 12, 13, 5.5, BARK, None, turn("z", -22.5, (11.2, 9, 4.7))]]   # a snapped limb
    return b


def sign_work_model():
    """The folding stand: two legs splayed fore and aft, the brace, the diamond board on top."""
    return [[1, 3, 7.6, 15, 16, 7.9, "rv_work", FULL],
            [7.4, 0, 6, 8.6, 9, 6.8, GALV, None, turn("x", 15, (8, 0, 6.4))],
            [7.4, 0, 9.2, 8.6, 9, 10, GALV, None, turn("x", -15, (8, 0, 9.6))],
            [7.4, 4, 6.5, 8.6, 4.6, 9.5, GALV], [7, 8, 7.9, 9, 12, 8.4, GALV]]


def closed_model(sign):
    """Type III barricade: white legs on feet, three striped boards; the sign above the top board."""
    S = "rv_stripes"
    b = [[1, 0, 6, 2.6, 13.5, 10, "hw_white"], [13.4, 0, 6, 15, 13.5, 10, "hw_white"],
         [0, 0, 5, 3.6, 0.8, 11, "hw_white"], [12.4, 0, 5, 16, 0.8, 11, "hw_white"]]
    for y in (3, 7, 11):
        b.append([0, y, 7.2, 16, y + 2.4, 8, S])
    if sign:
        b += [[0, 13.6, 7.2, 16, 16, 8, "rv_closed", dict(EDGES, north=[0, 4.5, 16, 11.5], south=[0, 4.5, 16, 11.5])], [1, 13.5, 7.4, 2.6, 13.8, 7.8, "hw_white"]]
    return b


def spool():
    """The cable drum on its side along z: plywood flanges, the cable wound between, a loose end."""
    b = []
    for y0, y1, x0, x1 in disc_strips(7.5, 8, 7.5):
        b.append([x0, y0, 1, x1, y1, 3, {"side": "rv_spool_end", "north": "rv_spool_end", "south": "rv_spool_end"}])
        b.append([x0, y0, 13, x1, y1, 15, {"side": "rv_spool_end", "north": "rv_spool_end", "south": "rv_spool_end"}])
    for y0, y1, x0, x1 in disc_strips(5.6, 8, 7.5):
        b.append([x0, y0, 3, x1, y1, 13, "rv_cable"])
    b.append([7.4, 0, 3, 8.6, 1, 16, "rv_cable"])
    return b


def tape_model():
    """The tape strung across x at waist height, sagging a little to the middle."""
    return [[x0, y0, 7.95, x1, y0 + 2, 8.05, "rv_tape", {f: [x0, 4, x1, 12] for f in ("north", "south")}]
            for x0, x1, y0 in ((0, 5.4, 9.6), (5.4, 10.6, 9.2), (10.6, 16, 9.6))]


def case_model():
    """The hard case on its back: body, the lid's rim, two latches and the handle on the near side."""
    body = {"up": "rv_case_lid", "side": "home_plastic_black", "down": "home_plastic_black"}
    return [[2, 0, 3.5, 14, 3.4, 12.5, body], [1.8, 1.6, 3.3, 14.2, 2.1, 12.7, "home_plastic_black"],
            [4, 1.4, 3, 5.5, 2.6, 3.5, "hw_chrome"], [10.5, 1.4, 3, 12, 2.6, 3.5, "hw_chrome"],
            [6.5, 1, 2.4, 9.5, 1.8, 3, "home_plastic_black"]]


def tin_model():
    """A toolbox burnt with its car: the lid sprung, the handle drooping."""
    return [[3.5, 0, 5, 12.5, 4, 11, "rv_burnt"], [3.5, 4, 5, 12.5, 4.6, 11, "rv_burnt", None, turn("x", -22.5, (8, 4, 5))],
            [7, 4.6, 7.6, 9, 5.4, 8.4, "rv_burnt"], [4, 0, 4, 9, 0.2, 12, "rv_ash"]]


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

    add("rv_flare", flare_model(True), "Road Flare", "WOOL", decor=True, light=10)
    add("rv_flare_out", flare_model(False), "Road Flare", "WOOL", decor=True)
    add("rv_jack", jack_model(), "Car Jack", "METAL", loot="story_changing_tire")
    add("rv_lug_wrench", lug_wrench(), "Wheel Brace", "METAL", rotate=None, decor=True)
    add("rv_skid", [[0, 0, 0, 16, 0.12, 16, "rv_skid"]], "Skid Marks", "STONE", rotate="axis", decor=True, render="cutout")
    add("rv_fluid", [[0, 0, 0, 16, 0.14, 16, "rv_fluid"]], "Spilled Oil", "STONE", rotate=None, decor=True, render="cutout")
    add("rv_debris", debris(), "Car Debris", "METAL", decor=True, render="translucent")
    add("rv_trunk", trunk(False), "Felled Tree", "WOOD", rotate="axis")
    add("rv_trunk_crown", trunk(True), "Felled Tree", "WOOD", rotate="facing", render="cutout")
    add("rv_sign_work", sign_work_model(), "Road Work Sign", "METAL", render="cutout")
    add("rv_closed", closed_model(False), "Barricade", "WOOD")
    add("rv_closed_sign", closed_model(True), "Road Closed", "WOOD")
    add("rv_spool", spool(), "Cable Drum", "WOOD", rotate="axis")
    add("rv_tape", tape_model(), "Police Line", "WOOL", decor=True)
    add("rv_case", case_model(), "Police Case", "METAL", loot="story_police_blockade")
    add("rv_tin", tin_model(), "Burnt Toolbox", "METAL", loot="story_burnt_car")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("rv_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} road story blocks in {path}")


if __name__ == "__main__":
    main()
