"""Grayhaven Mall: the ml_ kit for the island's enclosed mall (world/county/Mall.java and its parts).

No photos of it in the library yet (city/mall-* were queued): this follows what the big American
enclosed malls of the late seventies and the eighties are known for. Outside, tan ribbed precast
panels over a brown brick base, the anchors' and the mall's names in channel letters, the pylon by
the street with the tenants on it. Inside, the shops' bronze fronts with their names lit over them
in every colour, the vinyl tile floors and the acoustic ceilings in the shops, the walkways across
the well with their white soffits and downlights, the sloped glass of the roof in dark frames, the
directory with YOU ARE HERE, the banners hung from the roof for a sale that never ended. The last
days left their own: letters fallen, a sign half lit on the generator, FOR LEASE in a window.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- floors and ceilings: ml_vct, ml_vct_b (vinyl tile), ml_slab, ml_slab_b (vinyl over, the
  acoustic ceiling under), ml_ceiling, ml_ceiling_stain, ml_walk, ml_walk_on (terrazzo over, the
  soffit and its downlights under, the bronze fascia round);
- the roof: ml_roof_slope, ml_roof_slope_broken (glass rising toward facing, 45 degrees);
- outside: ml_precast, ml_precast_b, ml_precast_band, ml_brick, ml_brick_b;
- the shop signs: ml_band (the sign band), ml_sign_KEY_0..3(_on) (four by one, channel letters);
- the names: ml_name_KEY_0..(_on): mall (GRAYHAVEN MALL, twelve by two), harlans and bellmont
  (the anchors, twelve by three), cinema (six by two), food (FOOD COURT, six by one);
- the pylon: ml_pylon_0..23(_on) (six by four, row 0 the top), its back ml_pylon_back;
- the rest: ml_dir_0..3(_on) (the directory, two by two), ml_banner_A_0..2 (hung, row 0 the top),
  ml_mannequin_lo, ml_mannequin_hi, ml_mannequin_fallen.

    python3 tools/city/mall.py        rewrites the ml_ entries in blocks.json

photo_textures.py draws the textures (mall.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import transit  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
ITALIC = "/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf"
SCRIPT = "/usr/share/fonts/truetype/freefont/FreeSerifBoldItalic.ttf"
SERIF = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
MONO = "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-ExtraBold.ttf"
BRONZE = (78, 58, 40)
TAN = (196, 176, 146)

# the shops' names over their fronts, one to a trade (MallShop.Trade): text, font, lit colour
BRANDS = {
    "fashion": ("Marlowe", SCRIPT, (255, 70, 150)),
    "jeans": ("BLUE CANYON", BLACK, (60, 140, 255)),
    "shoes": ("SHOE BARN", BLACK, (255, 60, 50)),
    "sports": ("RIDGELINE SPORTS", ITALIC, (70, 220, 90)),
    "electronics": ("VOLTAGE", MONO, (40, 230, 255)),
    "records": ("GROOVE RECORDS", ITALIC, (190, 90, 255)),
    "toys": ("TOY CHEST", BLACK, (255, 210, 40)),
    "books": ("Paperleaf Books", SERIF, (255, 236, 200)),
    "jewelry": ("GOLDMARK", SERIF, (255, 196, 80)),
    "pharmacy": ("HAVEN DRUG", BLACK, (255, 50, 60)),
    "candy": ("Sweet Tooth", SCRIPT, (255, 120, 200)),
    "arcade": ("PIXEL PALACE", MONO, (255, 60, 255)),
    "video": ("VIDEO VAULT", ITALIC, (60, 120, 255)),
    "salon": ("Shear Elegance", SCRIPT, (255, 150, 170)),
    "travel": ("HORIZON TRAVEL", BOLD, (255, 140, 40)),
    "thrift": ("SECOND CHANCE", BOLD, (120, 230, 120)),
    "lease": ("FOR LEASE", BOLD, (240, 240, 230)),
}
# the big names: text, font, cols, rows, lit colour
NAMES = {
    "mall": ("GRAYHAVEN MALL", BLACK, 12, 2, (255, 236, 200)),
    "harlans": ("Harlan's", SCRIPT, 12, 3, (255, 60, 60)),
    "bellmont": ("BELLMONT", SERIF, 12, 3, (90, 170, 255)),
    "cinema": ("CINEMA 4", BLACK, 6, 2, (255, 70, 60)),
    "food": ("FOOD COURT", BLACK, 6, 1, (255, 190, 60)),
}


# ---------------------------------------------------------------- floors, ceilings, walls

def vct(seed, worn=False):
    """Vinyl composition tile, the cheap floor of every shop: foot squares (four to a block) in
    cream and putty, the odd grey one, the fleck through them, scuffed and waxed dull."""
    t = F.plain(seed, (204, 196, 176), 0.03, 0.5)
    rng = t.rng
    cols = [(206, 198, 178), (186, 178, 158), (196, 190, 172), (150, 146, 136)]
    for i in range(2):
        for j in range(2):
            c = cols[rng.integers(0, 3) if rng.random() > 0.1 else 3]
            area(t, i * 32, j * 32, i * 32 + 32, j * 32 + 32, col=c)
    t.vary(0.04)
    t.speckle(0.08, (120, 112, 96), 0.35)
    t.speckle(0.04, (232, 228, 214), 0.3)
    for k in (0, 32):
        area(t, k, 0, k + 1, N, mul=0.82, dh=-0.05)
        area(t, 0, k, N, k + 1, mul=0.82, dh=-0.05)
    for _ in range(6 if worn else 2):
        x, y = rng.integers(0, N, 2)
        area(t, x, y, x + rng.integers(4, 14), y + 1, col=(60, 54, 46))
    if worn:
        t.grime(0.35)
        t.crack(1, 0.2, col=(70, 64, 54))
        x, y = rng.integers(0, 32, 2)
        area(t, x, y, x + 22, y + 18, col=(120, 116, 108), dh=-0.15)  # a tile lifted out, the black glue under it
        area(t, x + 2, y + 2, x + 20, y + 16, col=(40, 36, 30))
    else:
        t.grime(0.12)
    return t


def ceiling(seed, stain=False):
    """Two-by-two acoustic tiles in their white T-bar grid, fissured; a brown ring where the roof leaked."""
    t = F.plain(seed, (222, 220, 210), 0.02, 0.15)
    t.speckle(0.18, (176, 172, 160), 0.4, -0.05)
    for _ in range(40):
        x, y = t.rng.integers(0, N, 2)
        area(t, x, y, x + t.rng.integers(2, 5), y + 1, col=(170, 166, 154), dh=-0.08)
    for k in (0, 31):
        area(t, k, 0, k + 2, N, col=(236, 236, 232), h=0.95, smooth=0.5)
        area(t, 0, k, N, k + 2, col=(236, 236, 232), h=0.95, smooth=0.5)
    if stain:
        yy, xx = np.mgrid[0:N, 0:N]
        r = np.hypot(xx - 40, yy - 22)
        ring = np.clip(1 - np.abs(r - 14) / 3, 0, 1) + np.clip(1 - r / 14, 0, 1) * 0.35
        t.mix(ring, (150, 116, 70), 0.7)
        area(t, 33, 33, 62, 62, col=(70, 66, 60), h=0.3)  # a tile fallen out, the dark over it
    t.grime(0.08)
    return t


def soffit(seed, lit):
    """The walkway's underside: smooth white plaster, a round downlight in a chrome ring."""
    t = F.plain(seed, (226, 224, 216), 0.03, 0.3)
    t.streaks(0.15, 0.85)
    disc(t, 32, 32, 9, col=(200, 202, 204), h=0.9, smooth=0.8, f0=180)
    disc(t, 32, 32, 7, col=(255, 246, 220) if lit else (150, 150, 146), h=0.6, smooth=0.9)
    if lit:
        yy, xx = np.mgrid[0:N, 0:N]
        m = np.clip(1 - np.hypot(xx - 32, yy - 32) / 7.5, 0, 1)
        t.emit[:] = np.maximum(t.emit, (m > 0) * 1.0)
    t.grime(0.15)
    return t


def fascia(seed):
    """Dark bronze anodised aluminium, the fronts' and walkways' trim: a reveal line along it."""
    t = transit.steel(seed, BRONZE, rust=0.0, chips=0.3)
    area(t, 0, 30, N, 33, mul=0.6, dh=-0.1)
    area(t, 0, 33, N, 34, mul=1.3)
    t.smooth[:] = 0.55
    t.f0[:] = 200
    return t


def band(seed):
    """The sign band over a shop front: flat black panel, the bronze cap along its top."""
    t = F.plain(seed, (34, 32, 30), 0.03, 0.4)
    area(t, 0, 0, N, 4, col=BRONZE, smooth=0.55, f0=200)
    area(t, 0, N - 3, N, N, col=BRONZE, smooth=0.55, f0=200)
    t.streaks(0.3, 0.8)
    return t


def precast(seed, kind="rib", stain=False):
    """The outside: tan precast concrete in ribs, a smooth band, brown brick under it all."""
    if kind == "brick":
        t = F.plain(seed, (118, 82, 58), 0.05, 0.2)
        for row in range(8):
            y0 = row * 8
            off = 8 if row % 2 else 0
            for k in range(4):
                x0 = (k * 16 + off) % N
                c = np.array((118, 82, 58)) * (0.85 + 0.3 * t.rng.random())
                area(t, x0, y0, x0 + 15, y0 + 7, col=tuple(c))
                if x0 + 15 > N:
                    area(t, 0, y0, x0 + 15 - N, y0 + 7, col=tuple(c))
            area(t, 0, y0 + 7, N, y0 + 8, col=(150, 140, 124), dh=-0.12)
        for k in range(4):
            for row in range(8):
                x = (k * 16 + (8 if row % 2 else 0) + 15) % N
                area(t, x, row * 8, x + 1, row * 8 + 8, col=(150, 140, 124), dh=-0.12)
        t.vary(0.06)
    else:
        t = F.plain(seed, TAN, 0.05, 0.15)
        t.speckle(0.2, (170, 152, 124), 0.3)
        t.speckle(0.05, (226, 212, 186), 0.3)
        if kind == "rib":
            for x0 in range(0, N, 16):
                area(t, x0 + 2, 0, x0 + 12, N, dh=0.15, mul=1.05)
                area(t, x0 + 12, 0, x0 + 13, N, mul=0.75, dh=-0.1)
                area(t, x0 + 1, 0, x0 + 2, N, mul=0.88)
        else:
            area(t, 0, 0, N, 2, mul=0.8, dh=-0.1)
            area(t, 0, N - 2, N, N, mul=0.8, dh=-0.1)
    t.streaks(0.5 if stain else 0.25, 0.7)
    t.grime(0.25 if stain else 0.1)
    return t


# ---------------------------------------------------------------- signs

def letters(p, text, font, x0, y0, x1, y1, col, lit, size=None):
    """Channel letters across a box of the picture: each letter's face in the brand's colour (dull
    plastic when off), its return a dark edge round it, a few letters gone or hanging."""
    w, h = x1 - x0, y1 - y0
    size = size or civic.fit(text, font, int(h * 0.78), int(w))
    f = p.font(font, size)
    face = tuple(int(v) for v in col) if lit else tuple(int(v * 0.55 + 40) for v in col)
    layer = Image.new("RGBA", p.im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text(((x0 + x1) / 2 * 4, (y0 + y1) / 2 * 4), text, font=f, fill=face + (255,), anchor="mm",
           stroke_width=int(size * 0.35), stroke_fill=(26, 24, 22, 255))
    d.text(((x0 + x1) / 2 * 4, (y0 + y1) / 2 * 4), text, font=f, fill=face + (255,), anchor="mm")
    p.im.alpha_composite(layer)


@lru_cache(maxsize=None)
def brand_pic(key, lit):
    text, font, col = BRANDS[key]
    p = Pic(4, 1, (0, 0, 0, 0), 15100 + len(key) * 7 + lit)
    letters(p, text, font, 4, 6, p.W - 4, p.H - 6, col, lit)
    return p


def lit_tile(p, i, seed, col, lit, edge=0.0):
    """A tile of a letters picture: the faces glowing when lit (the return dark), grime on the rest."""
    t = p.tile(i, seed, smooth=0.5, grime=0.08 if lit else 0.2)
    if lit:
        face = (t.alpha > 128) & (t.c.mean(-1) > 60)
        t.emit[:] = np.maximum(t.emit, face * 1.0)
    return t


def brand(key, i, lit):
    return lit_tile(brand_pic(key, lit), i, 15200 + i + len(key) * 13 + lit, BRANDS[key][2], lit)


@lru_cache(maxsize=None)
def name_pic(key, lit):
    text, font, cols, rows, col = NAMES[key]
    p = Pic(cols, rows, (0, 0, 0, 0), 15300 + len(key) + lit)
    letters(p, text, font, 6, 6, p.W - 6, p.H - 6, col, lit)
    return p


def name(key, i, lit):
    return lit_tile(name_pic(key, lit), i, 15400 + i + len(key) * 17 + lit, NAMES[key][4], lit)


@lru_cache(maxsize=None)
def pylon_pic(lit):
    """The pylon's face, six by four: GRAYHAVEN MALL over a red field, the four big tenants under in their panels."""
    p = Pic(6, 4, (40, 36, 34), 15500 + lit)
    W, H = p.W, p.H
    p.rect(6, 6, W - 6, 2 * N - 4, fill=(150, 36, 34) if lit else (104, 40, 36))
    text = (255, 240, 214) if lit else (196, 188, 170)
    size = civic.fit("GRAYHAVEN", BLACK, 54, W - 30)
    p.text(W / 2, N * 0.55, "GRAYHAVEN", BLACK, size, text, anchor="mm")
    p.text(W / 2, N * 1.42, "M A L L", BOLD, 30, text, anchor="mm")
    rows = [("Harlan's", SCRIPT, (255, 70, 70)), ("BELLMONT", SERIF, (90, 170, 255)),
            ("CINEMA 4", BLACK, (255, 200, 60)), ("120 SHOPS  FOOD COURT", BOLD, (240, 236, 220))]
    for k, (s, font, col) in enumerate(rows):
        y0 = 2 * N + k * N / 2
        p.rect(8, y0 + 3, W - 8, y0 + N / 2 - 3, fill=(236, 232, 220) if lit else (180, 176, 164))
        ink = (30, 28, 26)
        sz = civic.fit(s, font, 22, W - 40)
        p.text(W / 2, y0 + N / 4, s, font, sz, ink if k < 3 else (60, 56, 50), anchor="mm")
        if lit:
            pass
    return p


def pylon(i, lit):
    t = pylon_pic(lit).tile(i, 15510 + i + lit, smooth=0.5, grime=0.2)
    if lit:
        bright = t.c.mean(-1) > 150
        t.emit[:] = np.maximum(t.emit, bright * 0.8)
    t.streaks(0.3, 0.8)
    return t


@lru_cache(maxsize=None)
def dir_pic(lit):
    """The directory: the mall's plan in colour blocks, the red YOU ARE HERE, the listings down the side."""
    p = Pic(2, 2, (30, 30, 34), 15600 + lit)
    W, H = p.W, p.H
    p.rect(4, 4, W - 4, H - 4, fill=(232, 230, 222) if lit else (176, 174, 168))
    p.text(W / 2, 12, "MALL DIRECTORY", BLACK, 9, (40, 40, 46), anchor="mm")
    # the plan: the long concourse, the anchors at its ends, the court, the cinema and the food court off it
    x0, x1, ym = 10, W * 0.66, H * 0.55
    p.rect(x0, ym - 14, x0 + 16, ym + 14, fill=(200, 60, 60))
    p.rect(x1 - 16, ym - 14, x1, ym + 14, fill=(60, 110, 200))
    p.rect(x0 + 16, ym - 5, x1 - 16, ym + 5, fill=(220, 214, 190))
    cx = (x0 + x1) / 2
    p.rect(cx - 8, ym - 10, cx + 8, ym + 10, fill=(240, 236, 220), outline=(120, 116, 100))
    p.rect(cx - 6, ym - 26, cx + 6, ym - 10, fill=(150, 60, 140))
    p.rect(cx - 9, ym + 10, cx + 9, ym + 24, fill=(230, 160, 50))
    for k in range(10):
        xs = x0 + 18 + k * (x1 - x0 - 36) / 10
        p.rect(xs, ym - 12, xs + 4, ym - 6, fill=(120, 170, 120) if k % 3 else (190, 150, 90))
        p.rect(xs, ym + 6, xs + 4, ym + 12, fill=(120, 150, 190) if k % 2 else (190, 120, 130))
    p.ellipse(cx + 10, ym - 3, cx + 16, ym + 3, fill=(230, 30, 30))
    p.text(cx + 13, ym + 30, "YOU ARE HERE", BLACK, 5, (210, 30, 30), anchor="mm")
    for k in range(16):
        y = 24 + k * 5.6
        p.rect(x1 + 6, y, x1 + 8, y + 2, fill=((200, 60, 60), (60, 110, 200), (120, 170, 120), (230, 160, 50))[k % 4])
        p.rect(x1 + 10, y + 0.5, W - 8 - (k * 7) % 14, y + 1.5, fill=(90, 90, 96))
    return p


def directory(i, lit):
    t = dir_pic(lit).tile(i, 15610 + i + lit, smooth=0.7, grime=0.15)
    if lit:
        t.emit[:] = np.maximum(t.emit, (t.c.mean(-1) > 120) * 0.6)
    t.crack(1 if i == 1 else 0, 0.1, col=(200, 204, 206))
    return t


@lru_cache(maxsize=None)
def banner_pic(kind):
    """The banners hung down the concourse: the summer sale in teal and pink, the mall's own in burgundy."""
    if kind == "a":
        p = Pic(1, 3, (32, 156, 160), 15700)
        W, H = p.W, p.H
        p.poly([(0, H * 0.62), (W, H * 0.48), (W, H * 0.58), (0, H * 0.72)], fill=(240, 90, 150))
        p.ellipse(W * 0.2, H * 0.1, W * 0.8, H * 0.1 + W * 0.6, fill=(255, 214, 80))
        p.text(W / 2, H * 0.42, "SUMMER", BLACK, 12, (255, 255, 255), anchor="mm")
        p.text(W / 2, H * 0.82, "SALE", BLACK, 18, (255, 255, 255), anchor="mm")
    else:
        p = Pic(1, 3, (110, 30, 44), 15701)
        W, H = p.W, p.H
        p.rect(4, 4, W - 4, H - 4, outline=(226, 190, 120), width=1.5)
        p.text(W / 2, H * 0.3, "G", SERIF, 40, (226, 190, 120), anchor="mm")
        p.text(W / 2, H * 0.62, "SHOP|DINE|PLAY", BOLD, 8, (240, 230, 210), anchor="mm")
    return p


def banner(kind, i):
    t = banner_pic(kind).tile(i, 15710 + i + ord(kind), smooth=0.2, grime=0.25)
    t.streaks(0.25, 0.8)
    if i == 2:
        # the hem: frayed, a corner torn off
        area(t, 0, N - 4, N, N, mul=0.8)
        t.alpha[N - 10:, : 14] = 0
    return t


def plastic(seed, rgb):
    t = F.plain(seed, rgb, 0.02, 0.6)
    t.f0[:] = 40
    t.grime(0.15)
    return t


def textures():
    T = {}
    T["ml_vct"] = lambda: vct(15000)
    T["ml_vct_b"] = lambda: vct(15001, worn=True)
    T["ml_ceiling"] = lambda: ceiling(15002)
    T["ml_ceiling_stain"] = lambda: ceiling(15003, stain=True)
    T["ml_soffit"] = lambda: soffit(15004, False)
    T["ml_soffit_on"] = lambda: soffit(15005, True)
    T["ml_fascia"] = lambda: fascia(15006)
    T["ml_band"] = lambda: band(15007)
    T["ml_precast"] = lambda: precast(15008)
    T["ml_precast_b"] = lambda: precast(15009, stain=True)
    T["ml_precast_band"] = lambda: precast(15010, "band")
    T["ml_brick"] = lambda: precast(15011, "brick")
    T["ml_brick_b"] = lambda: precast(15012, "brick", stain=True)
    T["ml_skin"] = lambda: plastic(15013, (226, 222, 214))
    T["ml_dress"] = lambda: plastic(15014, (60, 90, 150))
    for key in BRANDS:
        for i in range(4):
            T[f"ml_sign_{key}_{i}"] = lambda key=key, i=i: brand(key, i, False)
            T[f"ml_sign_{key}_{i}_on"] = lambda key=key, i=i: brand(key, i, True)
    for key, (_, _, cols, rows, _) in NAMES.items():
        for i in range(cols * rows):
            T[f"ml_name_{key}_{i}"] = lambda key=key, i=i: name(key, i, False)
            T[f"ml_name_{key}_{i}_on"] = lambda key=key, i=i: name(key, i, True)
    for i in range(24):
        T[f"ml_pylon_{i}"] = lambda i=i: pylon(i, False)
        T[f"ml_pylon_{i}_on"] = lambda i=i: pylon(i, True)
    for i in range(4):
        T[f"ml_dir_{i}"] = lambda i=i: directory(i, False)
        T[f"ml_dir_{i}_on"] = lambda i=i: directory(i, True)
    for kind in ("a", "b"):
        for i in range(3):
            T[f"ml_banner_{kind}_{i}"] = lambda kind=kind, i=i: banner(kind, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, **extra):
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

    steel, chrome = "tx_steel_d", "home_chrome"

    # ---- floors and ceilings
    for name in ("ml_vct", "ml_vct_b"):
        add(name, None, "Vinyl Tile", "STONE", rotate=None, tex={"up": name, "side": "floor_concrete", "down": "floor_concrete"})
    add("ml_slab", None, "Floor", "STONE", rotate=None, tex={"up": "ml_vct", "down": "ml_ceiling", "side": "floor_concrete"})
    add("ml_slab_b", None, "Floor", "STONE", rotate=None, tex={"up": "ml_vct_b", "down": "ml_ceiling_stain", "side": "floor_concrete"})
    for name in ("ml_ceiling", "ml_ceiling_stain"):
        add(name, None, "Ceiling Tile", "STONE", rotate=None, tex={"all": name})
    add("ml_walk", None, "Walkway", "STONE", rotate=None, tex={"up": "tx_terrazzo", "down": "ml_soffit", "side": "ml_fascia"})
    add("ml_walk_on", None, "Walkway", "STONE", rotate=None, tex={"up": "tx_terrazzo", "down": "ml_soffit_on", "side": "ml_fascia"}, light=10)

    # ---- the roof: a pane at 45 degrees from the foot of the block's south side to the top of its north,
    # the frame's bars down its edges; walked on like a stair
    half = 11.3137
    tilt = {"axis": "x", "angle": 45, "origin": [8, 8, 8]}
    for name, glass in (("ml_roof_slope", "tx_glass_roof"), ("ml_roof_slope_broken", "tx_glass_broken")):
        add(name, [[0, 7.5, 8 - half, 16, 8.5, 8 + half, glass, None, tilt],
                   [0, 7, 8 - half, 1.2, 9, 8 + half, steel, None, tilt],
                   [14.8, 7, 8 - half, 16, 9, 8 + half, steel, None, tilt],
                   [0, 0, 15, 16, 1, 16, steel]],
            "Sloped Glass", "GLASS", shape=[[0, 0, 0, 16, 8, 16], [0, 8, 0, 16, 16, 8]], render="translucent")

    # ---- outside
    for name in ("ml_precast", "ml_precast_b", "ml_precast_band", "ml_brick", "ml_brick_b"):
        add(name, None, "Brick" if "brick" in name else "Precast Concrete", "STONE", rotate=None, tex={"all": name})

    # ---- the signs: channel letters on a thin plate of nothing, the band they hang on
    add("ml_band", None, "Sign Band", "METAL", rotate=None, tex={"all": "ml_band"})

    def plate(tex):
        return [[0, 0, 14.5, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    for key in BRANDS:
        for i in range(4):
            add(f"ml_sign_{key}_{i}", plate(f"ml_sign_{key}_{i}"), "Sign", "METAL", decor=True)
            add(f"ml_sign_{key}_{i}_on", plate(f"ml_sign_{key}_{i}_on"), "Sign", "METAL", decor=True,
                **({"light": 10} if i % 2 == 1 else {}))
    for key, (_, _, cols, rows, _) in NAMES.items():
        for i in range(cols * rows):
            add(f"ml_name_{key}_{i}", plate(f"ml_name_{key}_{i}"), "Sign", "METAL", decor=True)
            add(f"ml_name_{key}_{i}_on", plate(f"ml_name_{key}_{i}_on"), "Sign", "METAL", decor=True,
                **({"light": 12} if i % 3 == 1 else {}))
    for i in range(24):
        for suffix in ("", "_on"):
            add(f"ml_pylon_{i}{suffix}", [[0, 0, 4, 16, 16, 16, {"north": f"ml_pylon_{i}{suffix}", "side": "ml_fascia", "south": "ml_fascia"}]],
                "Pylon Sign", "METAL", **({"light": 11} if suffix and i % 4 == 1 else {}))
    add("ml_pylon_back", None, "Pylon Sign", "METAL", rotate=None, tex={"all": "ml_fascia"})

    # ---- the directory on its stand: the board's lower tiles carry the legs
    for i in range(4):
        for suffix in ("", "_on"):
            board = [[0, 0, 6, 16, 16, 8, {"north": f"ml_dir_{i}{suffix}", "side": "ml_fascia", "south": "ml_fascia"}]]
            if i >= 2:
                board = [[0, 6, 6, 16, 16, 8, {"north": f"ml_dir_{i}{suffix}", "side": "ml_fascia", "south": "ml_fascia"}],
                         [7 if i == 2 else 7, 0, 6.5, 9, 6, 7.5, "ml_fascia"]]
            add(f"ml_dir_{i}{suffix}", board, "Mall Directory", "METAL", **({"light": 8} if suffix and i == 1 else {}))

    # ---- the banners, hung from the roof on a rod
    for kind in ("a", "b"):
        for i in range(3):
            tex = f"ml_banner_{kind}_{i}"
            boxes = [[1, 0, 7.8, 15, 16, 8.2, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]
            if i == 0:
                boxes += [[0, 15, 7.4, 16, 16, 8.6, chrome]]
            add(tex, boxes, "Banner", "WOOL", decor=True)

    # ---- the mannequin: the stand and the legs, the body and the head; one knocked flat
    skin, dress = "ml_skin", "ml_dress"
    add("ml_mannequin_lo", [[5, 0, 5, 11, 1, 11, chrome], [7.6, 1, 7.6, 8.4, 4, 8.4, chrome], [6, 4, 7, 7.8, 16, 9, skin], [8.2, 4, 7, 10, 16, 9, skin],
                            [5.5, 12, 6.5, 10.5, 16, 9.5, dress]], "Mannequin", "WOOD")
    add("ml_mannequin_hi", [[5, 0, 6, 11, 10, 10, dress], [3.5, 3, 7, 5, 10, 9, skin], [11, 3, 7, 12.5, 10, 9, skin], [7.3, 10, 7.3, 8.7, 11.5, 8.7, skin],
                            [6, 11.5, 6, 10, 15.5, 10, skin]], "Mannequin", "WOOD")
    add("ml_mannequin_fallen", [[2, 0, 6, 14, 3, 10, dress], [14, 0, 6.5, 16, 3, 9.5, skin], [0, 0, 6.5, 2, 2.5, 9.5, skin], [3, 0, 2, 9, 1.5, 4, skin]],
        "Mannequin", "WOOD", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("ml_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} mall blocks in {path}")


if __name__ == "__main__":
    main()
