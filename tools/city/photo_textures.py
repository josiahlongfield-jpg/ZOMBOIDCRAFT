#!/usr/bin/env python3
"""Builds the city block textures from CC0 photo materials (ambientCG), graded for a wet, rotting
city, with the painted details from paint_textures.py (joints, lane paint, seams) laid on top.

Each material is cropped (a block shows 1/span of the photo), made tileable, scaled to 64x64
and colour-graded. Its displacement becomes the height (so the normal map comes from the same
code as the painted ones), its roughness becomes LabPBR smoothness, and low spots on streets
hold rain. Textures without a photo source (glass, lamps, plants, tyres) stay painted.

    python3 tools/city/photo_textures.py CC0_DIR OUT_DIR [name ...]

CC0_DIR holds one folder per material, as downloaded (e.g. Bricks085/Bricks085_1K-PNG_Color.png).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402

N = P.N
CC0 = None
ALT = False  # when set, crops come from another part of the photo (the "_b" variants)


# Poly Haven names its maps differently from ambientCG
POLYHAVEN = {"Color": "diff", "Displacement": "disp", "Roughness": "rough", "NormalGL": "nor_gl",
             "AmbientOcclusion": "ao", "Opacity": "alpha", "Metalness": "metal"}


def load(material, kind, mode="RGB"):
    files = list((CC0 / material).glob(f"*_{kind}.png")) or list((CC0 / material).glob(f"*_{POLYHAVEN[kind]}_1k.png"))
    return Image.open(files[0]).convert(mode) if files else None


def seamless(a):
    """Blend a crop with itself shifted by half, so its edges wrap without a seam."""
    s = a.shape[0]
    b = np.roll(np.roll(a, s // 2, 0), s // 2, 1)
    tent = 1 - np.abs(np.arange(s) - (s - 1) / 2) / (s / 2)
    tent = np.clip(tent * 1.6 - 0.3, 0, 1)
    m = np.outer(tent, tent)
    m = m[..., None] if a.ndim == 3 else m
    return a * m + b * (1 - m)


def crop(img, span, offset):
    if img is None:
        return None
    a = np.asarray(img, np.float32)
    if ALT:
        offset = ((offset[0] + 0.5) % 1, (offset[1] + 0.45) % 1)
    if span <= 1:
        # the whole photo already tiles; the alternate shows it shifted
        return np.roll(np.roll(a, a.shape[0] // 2, 0), a.shape[1] // 3, 1) if ALT else a
    s = int(a.shape[0] / span)
    y0 = int(offset[1] * (a.shape[0] - s))
    x0 = int(offset[0] * (a.shape[1] - s))
    return seamless(a[y0:y0 + s, x0:x0 + s])


def shrink(a):
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    return np.asarray(img.resize((N, N), Image.BOX), np.float32)


def photo(material, span=1.0, offset=(0.2, 0.3), sat=0.75, bright=0.88, tint=(1.0, 1.0, 1.0),
          height=1.0, wet=0.0, metal=False, seed=0):
    """A Tex from a photo material, graded toward the city's damp, desaturated look."""
    t = P.Tex(seed, (0, 0, 0))
    c = shrink(crop(load(material, "Color"), span, offset))
    lum = c @ np.array([0.299, 0.587, 0.114], np.float32)
    c = lum[..., None] + (c - lum[..., None]) * sat
    c = c * bright * np.array(tint, np.float32)
    t.c = c
    disp = load(material, "Displacement", "L")
    if disp is not None:
        d = shrink(crop(disp, span, offset)) / 255
        d = (d - d.mean()) * height
        t.h = np.clip(0.75 + d * 0.5, 0, 1)
    rough = load(material, "Roughness", "L")
    if rough is not None:
        t.smooth = 1 - shrink(crop(rough, span, offset)) / 255
    if metal:
        mt = load(material, "Metalness", "L")
        m = shrink(crop(mt, span, offset)) / 255 if mt is not None else np.ones((N, N), np.float32)
        t.f0 = np.where(m > 0.5, 230, 10).astype(np.float32)
        t.poro[:] = 0
    if wet > 0:
        # rain pools in the low spots
        low = np.clip((np.percentile(t.h, 45) - t.h) * 12 + 0.3, 0, 1)
        t.smooth = np.maximum(t.smooth, wet * low)
        t.c *= (1 - 0.18 * wet * low)[..., None]
        t.poro = t.poro * (1 - low * wet)
    return t


def details(t, painted, strength=1.0):
    """Lays the painted texture's small details (joints, seams, cracks) over a photo base."""
    p = painted.c @ np.array([0.299, 0.587, 0.114], np.float32)
    broad = P.blur(p, 6)
    ratio = np.clip(p / np.maximum(broad, 1), 0.3, 1.6)
    t.c *= (1 + (ratio - 1) * strength)[..., None]
    hp = painted.h - P.blur(painted.h, 6)
    t.h = np.clip(t.h + hp * strength, 0, 1)
    return t


def grimy(t, amount):
    t.grime(amount)
    return t


def rain_streaks(t, amount=0.5):
    t.streaks(amount, 0.8)
    return t


def car(colour, seed):
    """Weathered car paint: the white paint of a rust-streaked panel, recoloured."""
    t = photo("Metal041B", span=1, sat=1.0, bright=1.0, metal=False, seed=seed)
    t.c = np.roll(t.c, seed * 13, axis=1)
    lum = t.c.mean(axis=2, keepdims=True)
    sat = t.c.max(axis=2, keepdims=True) - t.c.min(axis=2, keepdims=True)
    paint = np.clip(1.6 - sat / 45, 0, 1)  # grey paint is recoloured; only deep rust keeps its colour
    t.c = t.c * (1 - paint) + (lum / 200) * np.array(colour, np.float32) * paint
    t.c *= 0.85
    t.smooth = 0.25 + 0.3 * paint[..., 0]
    t.f0[:] = 40
    return t


def lane(seed, *stripes, wear=0.35):
    t = photo("Asphalt026C", span=2, offset=(0.3, 0.2), sat=0.6, bright=0.85, wet=0.7, seed=seed)
    base = t.c.copy()
    for x0, x1, col in stripes:
        P.paint_line(t, x0, x1, col, wear)
    # worn paint: the asphalt's grain and grime show through
    grain = base.mean(axis=2) / max(base.mean(), 1)
    t.c = t.c * np.clip(0.55 + 0.45 * grain, 0.4, 1.2)[..., None] * 0.85
    return t


YELLOW, WHITE = (160, 128, 40), (168, 168, 162)

PHOTO = {
    # streets
    "asphalt": lambda: photo("Asphalt026C", span=2, offset=(0.3, 0.2), sat=0.6, bright=0.85, wet=0.7),
    "asphalt_line_yellow": lambda: lane(2, (22, 29, YELLOW), (35, 42, YELLOW)),
    "asphalt_line_white": lambda: lane(3, (28, 36, WHITE)),
    "crosswalk": lambda: lane(4, (6, 58, WHITE), wear=0.6),
    "sidewalk": lambda: details(photo("Concrete035", span=2, wet=0.4), P.sidewalk(), 0.55),
    "sidewalk_cracked": lambda: details(photo("Asphalt024C", span=2, sat=0.5, wet=0.4), P.sidewalk_cracked(), 0.45),
    "curb": lambda: details(photo("Concrete036", span=2, bright=0.8), P.curb(), 0.6),
    "jersey_barrier": lambda: rain_streaks(photo("Concrete035", span=2, offset=(0.6, 0.5), bright=0.8)),
    "rubble": lambda: photo("Ground028", span=2, bright=0.8, height=1.4),
    "moss": lambda: photo("Moss002", span=2, sat=0.45, bright=0.5, tint=(0.95, 1.0, 0.8)),
    # walls
    "concrete_panel": lambda: rain_streaks(details(photo("Concrete036", span=1.5, bright=0.85), P.concrete_panel(), 0.8)),
    "concrete_dark": lambda: photo("Concrete033", span=2, bright=0.9),
    "floor_concrete": lambda: photo("Concrete047A", span=2, sat=0.6, bright=0.85),
    "brick_red": lambda: rain_streaks(photo("Bricks085", span=2, sat=0.8, bright=0.82, height=1.2), 0.4),
    "brick_dark": lambda: rain_streaks(photo("Bricks097", span=2, sat=0.75, bright=0.8, height=1.2), 0.4),
    "stone_trim": lambda: rain_streaks(details(photo("Concrete044D", span=2, sat=0.5, bright=1.05, tint=(1.04, 1.0, 0.94)), P.stone_trim(), 0.8)),
    "stair_concrete": lambda: details(photo("Concrete044D", span=2, offset=(0.6, 0.6)), P.stair_concrete(), 1.0),
    "steel_mullion": lambda: details(photo("Metal024", span=2, sat=0.5, bright=0.45, metal=True), P.steel_mullion(), 0.6),
    "drywall": lambda: photo("Plaster007", span=1.5, sat=0.6, bright=0.85),
    "roof_gravel": lambda: photo("Gravel043", span=2, sat=0.6, bright=0.8, wet=0.5),
    # inside
    "office_carpet": lambda: photo("Carpet012", span=2, sat=0.5, bright=1.0),
    "ceiling_tile": lambda: grimy(photo("OfficeCeiling001", span=1, sat=0.5, bright=0.72, tint=(1.0, 0.98, 0.92)), 0.25),
    "marble_white": lambda: photo("Tiles141", span=1, sat=0.6, bright=0.85),
    "marble_dark": lambda: photo("Tiles140", span=1, sat=0.6, bright=0.85),
    "wood_panel": lambda: photo("WoodSiding008", span=1, sat=0.7, bright=0.75),
    "cubicle_fabric": lambda: photo("Fabric028", span=2, sat=0.6, bright=0.8),
    "elevator_door": lambda: details(photo("Metal024", span=2, sat=0.4, bright=0.75, metal=True), P.elevator_door(), 1.0),
    # metal and wood
    "painted_steel": lambda: photo("MetalPlates013", span=2, sat=0.6, bright=0.55, metal=True),
    "streetlight_metal": lambda: photo("Metal021", span=2, sat=0.4, bright=0.45, metal=True),
    "rust": lambda: photo("Rust009", span=2, sat=0.8, bright=0.8),
    "hvac_grille": lambda: details(photo("CorrugatedSteel009", span=2, sat=0.5, bright=0.75, metal=True), P.hvac_grille(), 0.5),
    "train_body": lambda: rain_streaks(details(photo("CorrugatedSteel007A", span=2, sat=0.35, bright=0.7), P.train_body(), 0.4), 0.6),
    "boards": lambda: photo("Planks038", span=1, sat=0.6, bright=0.75),
    "tank_wood": lambda: details(photo("WoodSiding011", span=1, sat=0.7, bright=0.75), P.tank_wood(), 0.3),
    # cars
    "car_red": lambda: car((150, 34, 28), 30),
    "car_white": lambda: car((200, 198, 190), 31),
    "car_blue": lambda: car((40, 60, 100), 32),
    "car_green": lambda: car((44, 70, 50), 33),
    "car_grey": lambda: car((110, 112, 112), 34),
    "car_burnt": lambda: photo("Metal053C", span=2, sat=0.5, bright=0.35, tint=(1.0, 0.9, 0.85)),
}


# ---------------------------------------------------------------- wear and damage variants
# Each wall and ground material gets several weathered versions; the city's decay pass mixes
# them block by block so no two stretches of wall look the same.

def moss_layer():
    return photo("Moss002", span=2, offset=(0.55, 0.15), sat=0.45, bright=0.5, tint=(0.95, 1.0, 0.8))


def v_alt(base):
    global ALT
    ALT = True
    try:
        return PHOTO[base]()
    finally:
        ALT = False


def v_crack(base):
    """Settlement cracks: dark, soft-edged and branching, with a little spalling along them."""
    t = PHOTO[base]()
    before_c, before_h = t.c.copy(), t.h.copy()
    rng = np.random.default_rng(hash(base) % 1000 + 1)
    line = np.zeros((N, N), np.float32)

    def run(x, y, ang, length, depth):
        for i in range(length):
            line[int(y) % N, int(x) % N] = max(line[int(y) % N, int(x) % N], depth)
            ang += rng.normal(0, 0.16)
            x += np.cos(ang)
            y += np.sin(ang)
            if depth > 0.5 and rng.random() < 0.04:
                run(x, y, ang + rng.choice((-1, 1)) * rng.uniform(0.5, 1.1), int(length * 0.4), depth * 0.6)

    for _ in range(3):
        run(*rng.uniform(0, N, 2), rng.uniform(0.9, 2.2), int(rng.uniform(30, 60)), 1.0)
    soft = np.clip(P.blur(line, 1) * 1.6, 0, 1)
    t.c = before_c * (1 - 0.75 * np.maximum(line, soft * 0.55))[..., None]
    t.h = before_h - soft * 0.25
    return t


def v_moss(base):
    """Moss creeping over the top edge and in patches, darkest where it is wettest."""
    t = PHOTO[base]()
    m = moss_layer()
    rng = np.random.default_rng(hash(base) % 1000 + 2)
    y = np.arange(N)[:, None] / N
    n = P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.4), (32, 0.25)))
    mask = np.clip((n + 0.55 - y * 0.9) * 3, 0, 1)
    mask = np.maximum(mask, np.clip((n - 0.15) * 4, 0, 1) * 0.8)
    t.c = t.c * (1 - mask[..., None]) + m.c * mask[..., None]
    t.h = t.h + mask * 0.08
    t.smooth = t.smooth * (1 - mask) + 0.1 * mask
    t.poro = np.maximum(t.poro, mask * 60)
    return t


def v_soot(base):
    """Smoke from a fire in the window below: black soot fanning up the wall."""
    t = PHOTO[base]()
    rng = np.random.default_rng(hash(base) % 1000 + 3)
    y = np.arange(N)[:, None] / N
    x = np.arange(N)[None, :] / N
    fan = np.clip(1.2 - np.abs(x - 0.5) * (1.6 - y * 1.2) * 2.2, 0, 1) * np.clip(0.25 + y, 0, 1)
    n = P.fbm(rng) * 0.5 + 0.5
    mask = np.clip(fan * (0.6 + 0.8 * n), 0, 1)
    t.c = t.c * (1 - 0.82 * mask[..., None]) + np.array((14, 12, 11), np.float32) * 0.82 * mask[..., None]
    t.smooth = t.smooth * (1 - mask * 0.5)
    return t


def v_stain(base):
    """Rust and rain running down from above."""
    t = PHOTO[base]()
    t.rng = np.random.default_rng(hash(base) % 1000 + 4)
    before = t.c.copy()
    t.streaks(1.4, 0.55)
    dark = 1 - t.c.mean(axis=2) / np.maximum(before.mean(axis=2), 1)
    t.c += np.clip(dark, 0, 1)[..., None] * np.array((30, 12, -6), np.float32)
    return t


def v_holes(base):
    """Bricks knocked out of the wall: a ragged cavity showing the dark inner brick behind."""
    t = PHOTO[base]()
    rng = np.random.default_rng(hash(base) % 1000 + 5)
    yy, xx = np.mgrid[0:N, 0:N]
    mask = np.zeros((N, N), np.float32)
    for _ in range(rng.integers(1, 3)):
        cx, cy = rng.uniform(14, 50, 2)
        # stepped like brick courses: missing whole and half bricks
        for row in range(-3, 4):
            y0 = int(cy + row * 4)
            half = rng.uniform(4, 12) * (1 - abs(row) / 4.5)
            x0 = int(cx - half + rng.uniform(-3, 3))
            x1 = int(cx + half + rng.uniform(-3, 3))
            if half > 1:
                mask[max(0, y0):max(0, y0 + 4), max(0, x0):max(0, x1)] = 1
    inner = np.roll(t.c, 17, axis=1) * 0.32 + 6
    rim = np.clip(P.blur(mask, 1) - mask, 0, 1)
    t.c = t.c * (1 - mask[..., None]) + inner * mask[..., None]
    t.c *= (1 - 0.5 * np.clip(rim * 3, 0, 1))[..., None]
    t.h = t.h * (1 - mask) + (t.h * 0.3) * mask
    return t


def v_patch(base):
    """A rectangle of newer, blacker tar where the road was patched before the end."""
    t = PHOTO[base]()
    rng = np.random.default_rng(hash(base) % 1000 + 6)
    x0, y0 = rng.integers(4, 24, 2)
    x1, y1 = x0 + rng.integers(24, 38), y0 + rng.integers(20, 36)
    m = np.zeros((N, N), np.float32)
    m[y0:y1, x0:x1] = 1
    t.c = t.c * (1 - 0.45 * m[..., None])
    t.smooth = np.maximum(t.smooth, m * 0.55)
    t.h = t.h + m * 0.02
    edge = np.clip(P.blur(m, 1) - m, 0, 1) + np.clip(m - P.blur(m, 1), 0, 1)
    t.c *= (1 - 0.4 * np.clip(edge * 3, 0, 1))[..., None]
    return t


KINDS = {"b": v_alt, "crack": v_crack, "moss": v_moss, "soot": v_soot, "stain": v_stain,
         "holes": v_holes, "patch": v_patch}
VARIANTS = {
    "brick_red": ["b", "crack", "moss", "soot", "stain", "holes"],
    "brick_dark": ["b", "crack", "moss", "soot", "stain", "holes"],
    "concrete_panel": ["b", "crack", "moss", "soot", "stain"],
    "concrete_dark": ["b", "crack", "moss", "soot", "stain"],
    "stone_trim": ["b", "crack", "moss", "soot"],
    "asphalt": ["b", "crack", "patch", "moss"],
    "sidewalk": ["b", "moss", "stain"],
    "drywall": ["b", "stain", "soot"],
    "floor_concrete": ["b", "crack", "moss"],
    "roof_gravel": ["b", "moss"],
}


def variant_names():
    return [f"{b}_{k}" for b, kinds in VARIANTS.items() for k in kinds]


# ---------------------------------------------------------------- dressing: plants, litter, damage
# Painted for now; swapped for photo materials as they arrive in the CC0 folder.

def cutout(seed, base):
    t = P.Tex(seed, base)
    t.alpha[:] = 0
    return t


def blade(t, x, h, lean, col, width=1, top=N - 1):
    """One curving blade from the bottom up; lean is how far its tip bends sideways, in texels."""
    last = None
    for y in range(h):
        px = int(round(x + lean * (y / max(h, 1)) ** 2))
        shade = 0.7 + 0.5 * y / h
        lo, hi = (px, px) if last is None else (min(px, last), max(px, last))
        for cx in range(lo, hi + width):  # no gaps where the blade bends fast
            if 0 <= cx < N:
                t.c[top - y, cx] = np.array(col, np.float32) * shade
                t.alpha[top - y, cx] = 255
        last = px
    return last, top - h + 1


def grass_tuft():
    """Low, dense tufts of dead and living grass for road cracks and ledges."""
    t = cutout(60, (80, 90, 50))
    for _ in range(46):
        col = (70, 80, 46) if t.rng.random() < 0.5 else (108, 96, 64)
        blade(t, t.rng.uniform(4, 60), int(t.rng.uniform(12, 38)), t.rng.uniform(-7, 7), col, 2)
    t.smooth[:] = 0.1
    t.poro[:] = 64
    return t


def weeds_tall():
    """Tall dry weeds and thistles, knee high."""
    t = cutout(61, (100, 96, 60))
    tips = []
    for _ in range(22):
        col = (96, 86, 56) if t.rng.random() < 0.6 else (62, 72, 42)
        tips.append(blade(t, t.rng.uniform(4, 60), int(t.rng.uniform(30, 63)), t.rng.uniform(-9, 9), col,
                          1 + (t.rng.random() < 0.4)))
    for x, y in tips[:6]:  # seed heads on the tallest stalks
        x, y = min(max(x - 1, 0), N - 3), max(y - 2, 0)
        t.c[y:y + 3, x:x + 3] = (88, 74, 52)
        t.alpha[y:y + 3, x:x + 3] = 255
    t.smooth[:] = 0.1
    t.poro[:] = 64
    return t


def ivy():
    """Dense ivy for hanging curtains: leaves in clumps, stems showing through."""
    t = cutout(62, (46, 70, 32))
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.8), (16, 0.6)))
    leaves = (n > -0.2) & (t.rng.random((N, N)) < 0.82)
    t.alpha[:] = np.where(leaves, 255, 0)
    t.vary(0.3, tint=(6, 10, -3))
    for _ in range(140):  # individual leaves, lighter on top
        x, y = int(t.rng.uniform(0, N - 3)), int(t.rng.uniform(0, N - 3))
        t.c[y:y + 2, x:x + 3] = np.array((70, 98, 44)) * t.rng.uniform(0.7, 1.15)
        t.c[y + 2, x:x + 3] = (30, 44, 22)
        t.alpha[y:y + 3, x:x + 3] = 255
        t.h[y:y + 2, x:x + 3] = 0.95
    for x in t.rng.integers(0, N, 5):  # woody stems
        t.c[:, x] = (64, 52, 40)
        t.alpha[:, x] = np.maximum(t.alpha[:, x], (t.rng.random(N) < 0.6) * 255)
    t.smooth[:] = 0.25
    t.poro[:] = 64
    return t


def bark():
    t = P.Tex(63, (58, 50, 44))
    t.vary(0.18)
    for x in range(0, N, 3):
        t.rect(x, 0, x + 1, N, None, 0.55)
    ridges = np.clip(P.blur(t.rng.random((N, N)) < 0.15, 1) * 3, 0, 1)
    t.c *= (1 - 0.35 * ridges[..., None])
    t.h -= ridges * 0.2
    t.grime(0.4, (40, 48, 30))
    t.smooth[:] = 0.15
    t.poro[:] = 50
    return t


def bark_top():
    t = P.Tex(64, (92, 76, 58))
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - 31.5, yy - 31.5)
    t.c *= (1 + 0.12 * np.sin(r * 1.1))[..., None]
    t.c[r > 26] = (58, 50, 44)
    t.vary(0.1)
    return t


def leaves_dark():
    """Dark, ragged canopy leaves with gaps, cut out."""
    t = cutout(65, (42, 60, 30))
    clumps = P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.5))) > -0.25
    t.alpha[:] = np.where(clumps & (t.rng.random((N, N)) < 0.78), 255, 0)
    t.vary(0.35, tint=(8, 10, -2))
    t.speckle(0.12, (96, 92, 50), 0.6)  # leaves turning yellow
    t.speckle(0.06, (26, 34, 20), 0.7)
    t.smooth[:] = 0.3
    t.poro[:] = 64
    return t


def rebar():
    """Rusted reinforcing bars bent out of broken concrete: a cross model, cut out."""
    t = cutout(66, (110, 60, 36))
    for _ in range(5):
        x = t.rng.uniform(8, 56)
        lean = t.rng.uniform(-0.5, 0.5)
        bend = t.rng.uniform(-14, 14)
        h = int(t.rng.uniform(30, 63))
        for y in range(h):
            px = int(x + lean * y + bend * max(0, y - h * 0.6) ** 2 / (h * h * 0.16 + 1)) % N
            col = np.array((118, 62, 34)) * t.rng.uniform(0.7, 1.1)
            t.c[N - 1 - y, px:px + 2] = col
            t.alpha[N - 1 - y, px:px + 2] = 255
    t.smooth[:] = 0.3
    t.f0[:] = 231
    return t


def trash_bag():
    t = P.Tex(67, (24, 25, 27))
    t.vary(0.25, ((8, 1.0), (16, 0.8), (32, 0.5)))
    folds = P.fbm(t.rng, ((8, 1.0), (16, 0.8)))
    t.c *= (1 + 0.5 * np.clip(folds, 0, 1))[..., None]
    t.h += folds * 0.2
    t.smooth[:] = 0.75 + 0.2 * np.clip(folds, 0, 1)
    t.poro[:] = 0
    return t


def paper():
    """Loose sheets and newspaper scattered flat: a floor decal, cut out."""
    t = cutout(68, (200, 196, 182))
    for _ in range(7):
        cx, cy = t.rng.uniform(6, 58, 2)
        w, h = t.rng.uniform(6, 13), t.rng.uniform(8, 15)
        ang = t.rng.uniform(0, np.pi)
        yy, xx = np.mgrid[0:N, 0:N]
        u = (xx - cx) * np.cos(ang) + (yy - cy) * np.sin(ang)
        v = -(xx - cx) * np.sin(ang) + (yy - cy) * np.cos(ang)
        m = (np.abs(u) < w / 2) & (np.abs(v) < h / 2)
        tone = np.array((190, 186, 170)) * t.rng.uniform(0.6, 1.0)
        t.c[m] = tone
        lines = m & ((v.astype(int) % 3) == 0) & (np.abs(u) < w / 2 - 1)
        t.c[lines] = tone * 0.6  # print
        t.alpha[m] = 255
    t.grime(0.6, (90, 80, 60))
    t.smooth[:] = 0.2
    return t


def glass_shards():
    t = cutout(69, (150, 170, 180))
    for _ in range(40):
        x, y = t.rng.integers(1, N - 3, 2)
        s = t.rng.integers(1, 3)
        t.c[y:y + s, x:x + s + 1] = np.array((150, 172, 184)) * t.rng.uniform(0.6, 1.3)
        t.alpha[y:y + s, x:x + s + 1] = 220
    t.smooth[:] = 0.98
    t.f0[:] = 20
    return t


def blood_splat():
    t = cutout(70, (70, 8, 6))
    yy, xx = np.mgrid[0:N, 0:N]
    cx, cy = t.rng.uniform(24, 40, 2)
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.8), (16, 0.5)))
    r = np.hypot(xx - cx, yy - cy) / 22
    m = r + n * 0.5 < 0.75
    for _ in range(20):  # spatter
        x, y = (np.array((cx, cy)) + t.rng.normal(0, 14, 2)).astype(int) % N
        m[max(0, y - 1):y + 1, max(0, x - 1):x + 1] = True
    t.c[:] = (64, 8, 6)
    t.vary(0.3)
    t.alpha[m] = 235
    t.smooth[:] = 0.6
    return t


def banner_skull():
    """A torn red cloth banner with a white skull, hung by survivors or raiders."""
    t = P.Tex(71, (110, 22, 18))
    t.vary(0.15)
    skull = [
        "....XXXXXX....",
        "..XXXXXXXXXX..",
        ".XXXXXXXXXXXX.",
        ".XXX..XX..XXX.",
        ".XX....X...XX.",
        ".XXX..XXX.XXX.",
        "..XXXX..XXXX..",
        "...XXXXXXXX...",
        "...X.X.X.X.X..",
        "....XXXXXXX...",
    ]
    sc = 3
    ox, oy = (N - len(skull[0]) * sc) // 2, 14
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            if ch == "X":
                t.c[oy + j * sc:oy + (j + 1) * sc, ox + i * sc:ox + (i + 1) * sc] = (214, 206, 190)
    t.grime(0.7, (40, 20, 14))
    t.streaks(1.0, 0.6)
    t.alpha[:] = 255
    rng = t.rng
    for x in range(N):  # ragged bottom edge and holes
        cut = int(rng.uniform(0, 7))
        t.alpha[N - cut:, x] = 0
    holes = P.fbm(rng, ((8, 1.0), (16, 0.6))) > 0.55
    t.alpha[holes] = 0
    t.smooth[:] = 0.1
    t.poro[:] = 64
    return t


def frame_wood():
    return photo("PaintedWood005", span=2, sat=0.5, bright=0.7)


def crate_wood():
    t = photo("Planks039", span=1, sat=0.55, bright=0.6)
    t.frame(4, (70, 58, 44), 0.9, 0.2)
    return t


def barrel_rust():
    t = photo("Rust009", span=2, sat=0.75, bright=0.6)
    for y in (6, 7, 56, 57, 31, 32):
        t.rect(0, y, N, y + 1, (52, 40, 32), 0.95)
    return t


def sandbag():
    t = photo("Fabric028", span=2, sat=0.3, bright=0.9, tint=(1.15, 1.02, 0.78))
    for y in range(0, N, 16):
        t.rect(0, y, N, y + 1, (60, 54, 40), 0.5)
    t.grime(0.5)
    return t


def scorch():
    """Black burn on a floor or road where a fire went out."""
    t = cutout(72, (14, 12, 11))
    yy, xx = np.mgrid[0:N, 0:N]
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.4)))
    r = np.hypot(xx - 32, yy - 32) / 30 + n * 0.4
    t.alpha[:] = np.clip((1 - r) * 400, 0, 230)
    t.c[:] = (16, 14, 12)
    t.vary(0.4)
    t.smooth[:] = 0.05
    return t


EXTRA = {f.__name__: f for f in (grass_tuft, weeds_tall, ivy, bark, bark_top, leaves_dark, rebar,
                                  trash_bag, paper, glass_shards, blood_splat, banner_skull,
                                  frame_wood, crate_wood, barrel_rust, sandbag, scorch)}


# ---------------------------------------------------------------- second batch of photo materials
# Real damage, moss, rust and plants from photos replace the painted versions where one exists.

def sprites(material, scale):
    """Cut the separate leaves or blades out of an atlas: (rgba, alpha) arrays, scaled down."""
    from scipy import ndimage
    col = np.asarray(load(material, "Color"), np.float32)
    alpha = np.asarray(load(material, "Opacity", "L"), np.float32)
    labels, n = ndimage.label(alpha > 40)
    out = []
    for sl in ndimage.find_objects(labels):
        h, w = sl[0].stop - sl[0].start, sl[1].stop - sl[1].start
        if h < 24 or w < 8:
            continue
        rgba = np.dstack([col[sl], alpha[sl]]).clip(0, 255).astype(np.uint8)
        img = Image.fromarray(rgba, "RGBA")
        size = (max(2, int(w * scale)), max(2, int(h * scale)))
        out.append(img.resize(size, Image.BOX))
    return out


def scatter(t, pieces, count, rng, rotate=True, grade=(0.8, 0.85), y_range=(0, N), wrap=True, anchor_bottom=False):
    """Pastes leaves/blades at random over a tile (wrapping across the edges so it tiles)."""
    canvas = Image.new("RGBA", (N * 3, N * 3), (0, 0, 0, 0))
    for _ in range(count):
        img = pieces[rng.integers(len(pieces))]
        if rotate:
            img = img.rotate(rng.uniform(0, 360), resample=Image.NEAREST, expand=True)
        x = int(rng.uniform(0, N))
        y = int(rng.uniform(*y_range)) - (img.height if anchor_bottom else img.height // 2)
        for ox in ((-N, 0, N) if wrap else (0,)):
            for oy in ((-N, 0, N) if wrap and not anchor_bottom else (0,)):
                canvas.alpha_composite(img, (N + x + ox - img.width // 2, N + y + oy))
    a = np.asarray(canvas, np.float32)[N:2 * N, N:2 * N]
    sat, bright = grade
    c = a[..., :3]
    lum = c.mean(axis=2, keepdims=True)
    c = (lum + (c - lum) * sat) * bright
    m = a[..., 3] > 100
    t.c[m] = c[m]
    t.alpha = np.where(m, 255, 0).astype(np.float32)
    t.h[m] = 0.9
    return t


def ivy_photo():
    rng = np.random.default_rng(81)
    t = cutout(81, (40, 60, 30))
    leaves = sprites("LeafSet017", 0.06)
    for x in rng.integers(0, N, 4):  # woody stems under the leaves
        for y in range(N):
            xx = int(x + 3 * np.sin(y * 0.15 + x)) % N
            t.c[y, xx] = (60, 50, 38)
            t.alpha[y, xx] = 255
    stems = t.c.copy(), t.alpha.copy()
    scatter(t, leaves, 42, rng, grade=(1.1, 0.48))
    keep = t.alpha == 0
    t.c[keep], t.alpha[keep] = stems[0][keep], stems[1][keep]
    t.smooth[:] = 0.35
    t.poro[:] = 64
    return t


def leaves_photo():
    rng = np.random.default_rng(82)
    t = cutout(82, (40, 60, 30))
    scatter(t, sprites("LeafSet029", 0.05), 120, rng, grade=(0.6, 0.42))
    t.smooth[:] = 0.3
    t.poro[:] = 64
    return t


def leaf_litter():
    rng = np.random.default_rng(83)
    t = cutout(83, (90, 60, 30))
    scatter(t, sprites("LeafSet030", 0.045), 40, rng, grade=(0.55, 0.6))
    return t


def blades(material, seed, count, scale, y_range, grade):
    rng = np.random.default_rng(seed)
    t = cutout(seed, (80, 90, 50))
    scatter(t, sprites(material, scale), count, rng, rotate=False, grade=grade, y_range=y_range,
            wrap=False, anchor_bottom=True)
    t.smooth[:] = 0.1
    t.poro[:] = 64
    return t


def overlay(t, material, kind="Opacity", col=(40, 30, 22), strength=0.7, span=1, offset=(0.2, 0.3)):
    """Darkens a texture through a decal's opacity (leaks, cracks)."""
    a = load(material, kind, "L")
    if a is None:  # leak decals have no opacity map: the stain is the dark part of the colour
        lum = np.asarray(load(material, "Color", "L"), np.float32)
        a = Image.fromarray(np.clip((lum.max() - lum) / max(np.ptp(lum), 1) * 255, 0, 255).astype(np.uint8))
    m = shrink(np.dstack([crop(a, span, offset)] * 3))[..., 0] / 255
    t.c = t.c * (1 - strength * m[..., None]) + np.array(col, np.float32) * strength * m[..., None]
    return t


def graffiti(i):
    """One tag from the graffiti atlas, centred on a transparent wall decal."""
    from scipy import ndimage
    col = np.asarray(load("GraffitiSet001", "Color"), np.float32)
    alpha = np.asarray(load("GraffitiSet001", "Opacity", "L"), np.float32)
    labels, n = ndimage.label(ndimage.binary_dilation(alpha > 40, iterations=6))
    boxes = sorted((sl for sl in ndimage.find_objects(labels) if (sl[1].stop - sl[1].start) > 80),
                   key=lambda sl: -(sl[0].stop - sl[0].start) * (sl[1].stop - sl[1].start))
    sl = boxes[i % len(boxes)]
    rgba = Image.fromarray(np.dstack([col[sl], alpha[sl]]).clip(0, 255).astype(np.uint8), "RGBA")
    rgba.thumbnail((N - 4, N - 4), Image.BOX)
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    canvas.alpha_composite(rgba, ((N - rgba.width) // 2, N - rgba.height - 6))
    a = np.asarray(canvas, np.float32)
    t = cutout(90 + i, (0, 0, 0))
    m = a[..., 3] > 90
    c = a[..., :3] * 0.72  # faded by years of rain
    t.c[m] = c[m]
    t.alpha = np.where(m, 255, 0).astype(np.float32)
    t.smooth[:] = 0.3
    return t


OVERRIDE = {
    # ground and walls
    "rubble": lambda: photo("rubble", span=2, sat=0.6, bright=0.75, height=1.5),
    "office_carpet": lambda: photo("dirty_carpet", span=2, sat=0.6, bright=1.1),
    "painted_steel": lambda: photo("rusty_painted_metal", span=2, sat=0.55, bright=0.5, metal=True),
    "hvac_grille": lambda: details(photo("rusty_corrugated_iron", span=2, sat=0.5, bright=0.7, metal=True), P.hvac_grille(), 0.4),
    "boards": lambda: photo("wood_peeling_paint_weathered", span=1, sat=0.55, bright=0.75),
    "grate": None,  # keeps its painted cut-out holes
    "bark": lambda: photo("bark_willow_02", span=2, sat=0.6, bright=0.7),
    "trash_bag": lambda: photo("Plastic012B", span=2, sat=0.4, bright=1.1),
    "sandbag": lambda: photo("hessian_230", span=2, sat=0.55, bright=0.75),
    "barrel_rust": lambda: photo("green_metal_rust", span=2, sat=0.55, bright=0.65),
    "ivy": ivy_photo,
    "leaves_dark": leaves_photo,
    # better damage from photos
    "brick_red_holes": lambda: photo("broken_brick_wall", span=2, sat=0.7, bright=0.75, height=1.4),
    "brick_dark_moss": lambda: photo("mossy_brick", span=2, sat=0.7, bright=0.9, height=1.2),
    "concrete_panel_crack": lambda: photo("cracked_concrete_wall", span=2, sat=0.5, bright=0.8, height=1.2),
    "concrete_panel_moss": lambda: photo("worn_mossy_plasterwall", span=2, sat=0.6, bright=0.8),
    "concrete_dark_moss": lambda: photo("concrete_moss", span=2, sat=0.5, bright=0.6),
    "concrete_panel_stain": lambda: overlay(photo("dirty_concrete", span=2, sat=0.5, bright=0.8), "Leaking012A", strength=0.5),
    "concrete_dark_crack": lambda: photo("damaged_concrete_floor", span=2, sat=0.5, bright=0.8),
    "floor_concrete_crack": lambda: photo("damaged_concrete_floor", span=2, offset=(0.6, 0.6), sat=0.5, bright=0.9),
    "sidewalk_moss": lambda: photo("overgrown_concrete_pavers", span=2, sat=0.5, bright=0.7, wet=0.4),
    "asphalt_crack": lambda: overlay(PHOTO["asphalt"](), "AsphaltDamageSet001", col=(70, 70, 68), strength=0.55, span=2, offset=(0.1, 0.1)),
    "drywall_stain": lambda: overlay(photo("Plaster007", span=1.5, sat=0.6, bright=0.85), "Leaking019C", strength=0.55),
    # new materials
    "drywall_peel": lambda: photo("damaged_plaster", span=2, sat=0.6, bright=0.8),
    "drywall_brick": lambda: photo("red_brick_plaster_patch_02", span=2, sat=0.6, bright=0.75),
    "wallpaper_rotten": lambda: photo("decrepit_wallpaper", span=1.5, sat=0.6, bright=0.8),
    "shutter": lambda: photo("rusted_shutter", span=2, sat=0.6, bright=0.7, metal=True),
    "dumpster": lambda: photo("green_metal_rust", span=2, offset=(0.6, 0.1), sat=0.6, bright=0.55),
    "cardboard": lambda: photo("Cardboard001", span=2, sat=0.55, bright=0.65),
    "dirt_withered": lambda: photo("withered_grass", span=2, sat=0.55, bright=0.6),
    "burnt_ground": lambda: photo("burned_ground_01", span=2, sat=0.5, bright=0.6),
    "leaf_litter": leaf_litter,
    **{f"graffiti_{i}": (lambda i=i: graffiti(i)) for i in range(8)},
}


def match_tone(t, base_name, amount=0.85):
    """Pulls a variant's average colour and contrast toward its base, so neighbouring blocks
    differ in detail rather than in brightness (which reads as a checkerboard)."""
    ref = build(base_name)
    for k in range(3):
        c, r = t.c[..., k], ref.c[..., k]
        target = (c - c.mean()) * (r.std() / max(c.std(), 1e-3)) ** amount + r.mean()
        t.c[..., k] = c * (1 - amount) + target * amount
    return t


def build(name):
    """The texture for a name: from a photo when it has one, otherwise painted."""
    for base, kinds in VARIANTS.items():
        for k in ("b", "crack", "stain"):
            if name == f"{base}_{k}" and k in kinds:
                t = OVERRIDE[name]() if OVERRIDE.get(name) else KINDS[k](base)
                return match_tone(t, base, 0.9 if k == "b" else 0.6)
    if OVERRIDE.get(name):
        return OVERRIDE[name]()
    if name in PHOTO:
        return PHOTO[name]()
    for base, kinds in VARIANTS.items():
        for k in kinds:
            if name == f"{base}_{k}":
                return KINDS[k](base)
    if name in EXTRA:
        return EXTRA[name]()
    return P.TEXTURES[name]()


def main():
    global CC0
    args = sys.argv[1:]
    CC0, out = Path(args[0]), Path(args[1])
    out.mkdir(parents=True, exist_ok=True)
    names = args[2:] or list(dict.fromkeys(list(P.TEXTURES) + variant_names() + list(EXTRA)
                                            + [k for k, v in OVERRIDE.items() if v]))
    for name in names:
        t = build(name)
        t.albedo().save(out / f"{name}.png")
        t.specular().save(out / f"{name}_s.png")
        t.normal().save(out / f"{name}_n.png")
        t.normal(gl=True).save(out / f"{name}_nrm_gl.png")
    print(f"wrote {len(names)} textures to {out} ({sum(n in PHOTO for n in names)} from photos)")


if __name__ == "__main__":
    main()
