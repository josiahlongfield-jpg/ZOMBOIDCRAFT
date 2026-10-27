"""The gas station: the gs_ kit for the roadside stops (world/county/GasStation.java).

No photos of one in the library yet: this follows what the early-nineties rural stations of the
upper South are known for. Two made-up brands share the modern build: CARDINAL (red on white,
charcoal pinstripes) and BLUEGRASS (navy with a green stripe). The canopy over the islands, its
fascia banded in the brand's colours with the name across the front, the soffit of aluminium
planks with square lamps let into it, the clad columns on yellow guards; the multi-product
dispensers on their curbed islands, the squeegee buckets, the bollards; the price sign on its
pylon by the road; the food mart behind with its glass front, the ice merchandiser, the propane
cage, the payphone and the newspaper boxes outside, and inside the counter with the cigarette wall,
the lottery case, the roller grill, coffee and fountain, the cooler wall, the chip racks and the
motor oil. The old service station is VALLEY: white enamel walls with a red band, the bays with
their lift, the round sign on its pole, the old pumps with globes.

Blocks (tile 0 is always the viewer's left):
- canopy: gs_fascia_{c,g}, gs_fascia_{c,g}_name_0..5(_on), gs_deck, gs_deck_light(_on,_smash),
  gs_deck_hole, gs_column_{c,g}, gs_column_{c,g}_band, gs_column_base;
- islands: gs_island, gs_island_nose, gs_squeegee, gs_island_bin, gs_bollard, gs_pump_{c,g}_{lo,hi}
  (+ _hi_on, _lo_smash, _hi_smash), gs_oldpump_{lo,hi}(_rust), gs_oldpump_hi_smash;
- sign: gs_price_{c,g}_0..14(_on) (three by five), gs_pylon_{c,g};
- forecourt: gs_slab, gs_slab_oil, gs_slab_crack, gs_slab_j (facing), gs_slab_fill, gs_vent_lo, gs_vent_hi;
- outside the mart: gs_store_{c,g}_0..7(_on), gs_front_lo, gs_front_hi, gs_front_poster_0..5,
  gs_front_broken_lo, gs_front_broken_hi, gs_ice_0..3, gs_propane_l, gs_propane_r, gs_payphone,
  gs_news_0..2, gs_air, gs_vac;
- the mart: gs_vct, gs_vct_b, gs_vct_mud, gs_frp, gs_frp_{c,g}, gs_cool_{full,half,empty,smash}_{lo,hi}(_on),
  gs_cool_head_0..3, gs_counter_{c,g}, gs_counter_{c,g}_candy, gs_lotto, gs_impulse, gs_cig_lo,
  gs_cig_hi, gs_fs_cab, gs_roller(_on), gs_coffee(_on), gs_fountain, gs_slush(_on), gs_cups,
  gs_menu_0..2, gs_chips, gs_chips_low, gs_oil, gs_oil_low, gs_mags, gs_freezer, gs_bread, gs_mirror,
  gs_sign_{restrooms,card,prepay,hours,office}, gs_poster_0..5, gs_neon_beer(_on), gs_neon_lotto(_on);
- the old station: gs_enamel, gs_enamel_rust, gs_enamel_band, gs_valley_0..5, gs_bay_0..3, gs_bay_floor,
  gs_lift_post, gs_lift_arms, gs_tirechanger, gs_balancer, gs_compressor_lo, gs_compressor_hi,
  gs_tirerack, gs_oilcans, gs_hosereel, gs_valley_sign_0..3.

    python3 tools/city/gasstation.py        rewrites the gs_ entries in blocks.json

photo_textures.py draws the textures (gasstation.register()). The base model faces north: anything
on a wall has its back at z = 16. A face only shows the part of its texture under its box.
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import school  # noqa: E402
from furniture import area, disc, edges  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
SANS = school.SANS
COND = school.COND
SERIF = school.SERIF
HAND = school.HAND
OBLIQUE = school.OBLIQUE
WHITE = (238, 236, 228)
CHARCOAL = (40, 40, 44)
YELLOW = (226, 186, 40)
LCD = (120, 230, 120)
LED = (255, 40, 30)
BRANDS = {
    # name, the store's name, main colour, second colour, the price rows
    "c": dict(name="CARDINAL", store="CARDINAL FOOD MART", main=(176, 30, 36), second=CHARCOAL,
              rows=(("UNLEADED", "1.07"), ("SUPER UNL", "1.23"), ("DIESEL", "1.13"))),
    "g": dict(name="BLUEGRASS", store="BLUEGRASS MARKET", main=(28, 62, 128), second=(48, 140, 70),
              rows=(("REGULAR", "1.05"), ("PREMIUM", "1.21"), ("KEROSENE", "1.39"))),
}
VALLEY = dict(main=(184, 34, 30), second=(30, 92, 60), cream=(236, 230, 212))


def glow(t, mask, col, amount=1.0, emit=0.9):
    """Lights a mask: the colour laid on, the emission set, a soft halo round it."""
    m = np.clip(mask, 0, 1)
    halo = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2)), np.float32) / 255
    t.c = t.c * (1 - halo[..., None] * 0.5 * amount) + np.array(col, np.float32) * halo[..., None] * 0.5 * amount
    t.mix(m, col, amount)
    t.emit[:] = np.maximum(t.emit, m * emit)
    return t


def lit_where(t, test, emit=0.85):
    """Emission wherever test (a boolean mask on the colours) holds."""
    t.emit[:] = np.maximum(t.emit, test.astype(np.float32) * emit)
    return t


def weather(t, rust=0.0, streak=0.6, grime=0.25, dents=0):
    """The years on painted metal: rain streaks from the top, grime, a few dents, rust at the seams."""
    rng = t.rng
    for _ in range(dents):
        x, y = rng.uniform(6, N - 6, 2)
        r = rng.uniform(2.5, 6)
        disc(t, x, y, r, mul=0.9, dh=-0.25, soft=0.6)
        disc(t, x - r * 0.3, y - r * 0.3, r * 0.4, mul=1.08)
    if rust:
        # rust where water sits: along the bottom edge and up the seams, a thin bleed under some
        for _ in range(int(rust * 5)):
            if rng.random() < 0.6:
                x, y = rng.uniform(0, N), rng.uniform(N - 7, N - 1)
            else:
                x, y = rng.choice([rng.uniform(0, 3), rng.uniform(N - 3, N)]), rng.uniform(2, N - 4)
            m = F.blob_mask(rng, x, y, rng.uniform(0.7, 2.0), rough=0.8)
            t.mix(m, (112, 66, 38), 0.55)
            t.smooth -= m * 0.3
            if y < N - 10 and rng.random() < 0.5:
                m2 = np.zeros((N, N), np.float32)
                xi, yi = int(x) % N, int(y)
                m2[yi:min(N, yi + int(rng.uniform(5, 16))), xi:xi + 1] = 0.3
                t.mix(P.blur(m2, 1), (130, 76, 42), 0.5)
        if rust >= 0.8:
            yy = np.arange(N, dtype=np.float32)[:, None]
            band = np.clip((yy - (N - 12)) / 12, 0, 1) * (0.5 + 0.5 * P.fbm(rng))
            t.mix(np.clip(band, 0, 1) * 0.35, (110, 70, 44))
    t.streaks(streak, 0.7)
    t.grime(grime)
    return t


def seg7(p, x, y, w, h, ch, on, off=None, slant=0.12, th=None):
    """A seven-segment digit at (x, y), w by h texels: the lit segments in on, the dark ones in off."""
    segs = {"0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc", "5": "afgcd", "6": "afgedc", "7": "abc",
            "8": "abcdefg", "9": "abcdfg", " ": "", "-": "g"}[ch]
    th = th or max(0.8, w * 0.18)
    h2 = h / 2

    def sk(px, py):
        return (px + (h - (py - y)) * slant, py)

    bars = {"a": ((x + th, y), (x + w - th, y + th)), "g": ((x + th, y + h2 - th / 2), (x + w - th, y + h2 + th / 2)),
            "d": ((x + th, y + h - th), (x + w - th, y + h)), "f": ((x, y + th * 0.6), (x + th, y + h2 - th * 0.4)),
            "b": ((x + w - th, y + th * 0.6), (x + w, y + h2 - th * 0.4)), "e": ((x, y + h2 + th * 0.4), (x + th, y + h - th * 0.6)),
            "c": ((x + w - th, y + h2 + th * 0.4), (x + w, y + h - th * 0.6))}
    for s, ((x0, y0), (x1, y1)) in bars.items():
        col = on if s in segs else off
        if col is None:
            continue
        p.poly([sk(x0, y0), sk(x1, y0), sk(x1, y1), sk(x0, y1)], fill=col)


# ---------------------------------------------------------------- brand marks

def emblem(p, cx, cy, r, b, ground=None):
    """The brand's mark in a disc of radius r: CARDINAL's crested bird's head, BLUEGRASS's three blades."""
    B = BRANDS[b]
    if b == "c":
        p.ellipse(cx - r, cy - r, cx + r, cy + r, fill=WHITE)
        p.ellipse(cx - r * 0.82, cy - r * 0.82, cx + r * 0.82, cy + r * 0.82, fill=B["main"])
        # the head: a round crown, the crest swept back, the black mask round the beak
        s = r / 10
        p.poly([(cx - 6 * s, cy + 1 * s), (cx - 2 * s, cy - 7 * s), (cx + 1 * s, cy - 3 * s), (cx + 5 * s, cy - 2 * s),
                (cx + 7 * s, cy + 1 * s), (cx + 3 * s, cy + 6 * s), (cx - 4 * s, cy + 6 * s)], fill=WHITE)
        p.poly([(cx + 3 * s, cy - 1 * s), (cx + 8.5 * s, cy + 1.5 * s), (cx + 3.5 * s, cy + 3 * s)], fill=YELLOW)
        p.ellipse(cx + 0.6 * s, cy - 1.2 * s, cx + 2.4 * s, cy + 0.6 * s, fill=CHARCOAL)
    else:
        p.ellipse(cx - r, cy - r, cx + r, cy + r, fill=WHITE)
        p.ellipse(cx - r * 0.82, cy - r * 0.82, cx + r * 0.82, cy + r * 0.82, fill=B["main"])
        s = r / 10
        for k, (dx, lean, hgt) in enumerate(((-3.5, -3, 11), (0, 1, 14), (3.5, 4, 10))):
            base = cy + 6 * s
            p.poly([(cx + (dx - 1.4) * s, base), (cx + (dx + lean) * s, base - hgt * s), (cx + (dx + 1.4) * s, base)],
                   fill=B["second"] if k != 1 else (110, 196, 90))
        p.rect(cx - 6 * s, cy + 6 * s, cx + 6 * s, cy + 7 * s, fill=WHITE)


# ---------------------------------------------------------------- the canopy

def fascia_base(p, b, y0, y1):
    """The fascia's band on a picture between rows y0 and y1: white, the brand stripe, pinstripes."""
    B = BRANDS[b]
    h = y1 - y0
    p.rect(0, y0, p.W, y1, fill=WHITE)
    p.rect(0, y0, p.W, y0 + 3, fill=(170, 172, 170))
    p.rect(0, y1 - 3, p.W, y1, fill=(150, 152, 150))
    if b == "c":
        p.rect(0, y0 + h * 0.34, p.W, y0 + h * 0.70, fill=B["main"])
        p.rect(0, y0 + h * 0.74, p.W, y0 + h * 0.79, fill=B["second"])
        p.rect(0, y0 + h * 0.25, p.W, y0 + h * 0.29, fill=B["second"])
    else:
        p.rect(0, y0 + h * 0.30, p.W, y0 + h * 0.66, fill=B["main"])
        p.rect(0, y0 + h * 0.66, p.W, y0 + h * 0.76, fill=B["second"])


@lru_cache(maxsize=None)
def fascia_pic(b, named, lit):
    """The canopy's fascia, six blocks across (or one plain): the bands, and the name panel in the middle."""
    cols = 6 if named else 1
    B = BRANDS[b]
    p = Pic(cols, 1, WHITE, 6000 + ord(b) * 3 + named * 2 + lit)
    fascia_base(p, b, 0, N)
    if named:
        # the panel: the brand colour edge to edge across the middle four blocks, the mark and the name in white
        x0, x1 = N * 0.6, p.W - N * 0.6
        p.rect(x0, 6, x1, N - 6, fill=B["main"])
        p.rect(x0 + 2, 8, x1 - 2, N - 8, outline=WHITE, width=1.2)
        emblem(p, x0 + 30, N / 2, 21, b)
        size = civic.fit(B["name"], BLACK, 44, x1 - x0 - 80)
        p.text((x0 + 60 + x1) / 2, N / 2 - size * 0.62, B["name"], BLACK, size, WHITE, anchor="ma")
    return p


def fascia(b, i=None, lit=False):
    named = i is not None
    t = fascia_pic(b, named, lit).tile(i or 0, 6010 + ord(b) * 7 + (i or 0) * 2 + lit, smooth=0.55, grime=0.12)
    t.f0[:] = 40
    for x in (0, N - 1):
        area(t, x, 0, x + 1, N, mul=0.7, dh=-0.2)  # the panel seams
    if lit and named:
        white = (t.c.min(-1) > 200) & (np.arange(N)[:, None] > 8) & (np.arange(N)[:, None] < N - 8)
        if 0 < i < 5:
            lit_where(t, white, 0.8)
    weather(t, rust=0.4, streak=0.75, grime=0.3, dents=1)
    return t


def soffit(seed, kind="plain"):
    """The canopy's underside: white aluminium planks across, the joints, water stains; or a square
    lamp let in (dead, lit, smashed), or a hole where the planks came down onto the joists."""
    t = F.plain(seed, (222, 222, 216), 0.03, 0.5)
    t.f0[:] = 45
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, mul=0.8, dh=-0.15)
        area(t, 0, y + 1, N, y + 2, mul=1.04)
    rng = t.rng
    for _ in range(2):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(6, 14), (150, 130, 96), 0.35)
    if kind in ("lamp", "lamp_on", "smash"):
        area(t, 14, 14, 50, 50, (180, 182, 180), h=0.6)
        area(t, 16, 16, 48, 48, (120, 122, 120), h=0.4)
        if kind == "smash":
            area(t, 18, 18, 46, 46, (20, 20, 22), h=0.1, smooth=0.1)
            for k in range(6):
                x, y = rng.uniform(18, 46, 2)
                p = np.zeros((N, N), np.float32)
                p[int(y):int(y) + int(rng.integers(2, 5)), int(x):int(x) + int(rng.integers(3, 8))] = 1
                t.mix(p, (190, 196, 196), 0.8)
            area(t, 30, 18, 31, 42, (40, 40, 40))
            area(t, 34, 18, 35, 36, (150, 40, 30))
        else:
            # the prismatic lens: a fine grid of little pyramids
            yy, xx = np.mgrid[0:N, 0:N]
            lens = ((xx >= 18) & (xx < 46) & (yy >= 18) & (yy < 46)).astype(np.float32)
            prism = (((xx // 2) + (yy // 2)) % 2).astype(np.float32)
            if kind == "lamp_on":
                t.mix(lens, (255, 246, 226), 1.0)
                t.mix(lens * prism, (236, 226, 206), 0.5)
                t.emit[:] = np.maximum(t.emit, lens * 1.0)
                halo = P.blur(lens, 3)
                t.c = t.c * (1 - halo[..., None] * 0.2) + np.array((255, 240, 210), np.float32) * halo[..., None] * 0.2
            else:
                t.mix(lens, (170, 166, 150), 1.0)
                t.mix(lens * prism, (140, 136, 120), 0.6)
                F.stain(t, 32, 40, 6, (90, 80, 50), 0.5)
                t.mix(lens * (rng.random((N, N)) < 0.06), (40, 34, 26), 0.8)  # dead flies in the lens
    elif kind == "hole":
        m = F.blob_mask(rng, 32, 32, 22, rough=0.6, wrap=False)
        t.mix(m, (16, 16, 18))
        t.h -= m * 0.8
        for x in (12, 40):
            area(t, x, 0, x + 6, N, (70, 70, 72), h=0.3)
            t.mix(m * 0, (0, 0, 0))
        t.mix(m * (rng.random((N, N)) < 0.03), (120, 80, 40), 0.6)
    weather(t, rust=0.3, streak=0.0, grime=0.25)
    return t


def canopy_roof(seed):
    """The canopy's top: a white membrane gone grey, ponding stains, leaves and grit in the low spots."""
    t = F.plain(seed, (190, 190, 184), 0.06, 0.2)
    rng = t.rng
    for _ in range(3):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(8, 20), (110, 106, 96), 0.5)
    t.speckle(0.05, (90, 86, 80), 0.4)
    for _ in range(10):
        x, y = rng.uniform(0, N, 2)
        disc(t, x, y, rng.uniform(1, 2), (120, 90, 50), h=0.6)
    t.grime(0.3)
    return t


def column(b, band=False):
    """The column cladding: brushed aluminium in vertical panels, the brand colour round it at the band."""
    t = F.plain(6100 + ord(b) + band, (196, 198, 198), 0.02, 0.6)
    t.f0[:] = 120
    for x in (0, 31, 32, 63):
        area(t, x, 0, x + 1, N, mul=0.75, dh=-0.2)
    for _ in range(40):
        x = t.rng.integers(0, N)
        area(t, x, 0, x + 1, N, mul=float(t.rng.uniform(0.96, 1.04)))
    if band:
        B = BRANDS[b]
        area(t, 0, 22, N, 46, B["main"], smooth=0.55)
        area(t, 0, 46, N, 50, B["second"], smooth=0.55)
        area(t, 0, 22, N, 23, mul=1.2)
    weather(t, rust=0.2, streak=0.5, grime=0.25, dents=2)
    return t


def guard(seed):
    """The column's guard: a steel sleeve filled with concrete, yellow, black tape bands, scuffed by bumpers."""
    t = F.plain(seed, YELLOW, 0.05, 0.45)
    for y in (10, 30):
        area(t, 0, y, N, y + 6, (30, 30, 30), smooth=0.3)
    rng = t.rng
    for _ in range(14):
        x, y = rng.uniform(0, N), rng.uniform(34, N)
        area(t, x, y, x + rng.uniform(3, 14), y + rng.uniform(1, 3), (110, 110, 106))
    for _ in range(5):
        x, y = rng.uniform(0, N), rng.uniform(36, N - 4)
        area(t, x, y, x + rng.uniform(4, 9), y + 2, (40, 40, 44))  # tyre black
    weather(t, rust=0.6, streak=0.4, grime=0.35, dents=2)
    return t


# ---------------------------------------------------------------- the islands

def island_top(seed, nose=False):
    """The island's top: concrete, the yellow paint along both edges (round the nose at the end)."""
    t = F.plain(seed, (168, 166, 158), 0.06, 0.2)
    t.speckle(0.1, (120, 118, 110), 0.4)
    yy, xx = np.mgrid[0:N, 0:N]
    if nose:
        d = np.hypot(xx - 0, yy - 32)
        m = ((d > 26) & (d < 33) & (xx < 34)).astype(np.float32)
        out = (d >= 33).astype(np.float32)
        t.mix(m, YELLOW, 0.9)
        t.mix(out, (60, 60, 60), 0.0)
    else:
        m = ((yy < 7) | (yy > N - 8)).astype(np.float32)
        t.mix(m, YELLOW, 0.9)
    t.mix(m * (t.rng.random((N, N)) < 0.25), (150, 148, 140), 0.9)  # paint worn through
    for _ in range(3):
        F.stain(t, t.rng.uniform(0, N), t.rng.uniform(10, 54), t.rng.uniform(4, 10), (60, 56, 50), 0.5)
    t.grime(0.25)
    return t


def island_side(seed):
    t = F.plain(seed, YELLOW, 0.06, 0.3)
    t.mix((t.rng.random((N, N)) < 0.22).astype(np.float32), (150, 148, 140), 0.9)
    area(t, 0, 48, N, N, (150, 148, 140))
    t.grime(0.4)
    return t


@lru_cache(maxsize=None)
def pump_pic(b, smash):
    """The dispenser's face, one block across and two high: the head (brand, the sale and gallons
    windows, the three grades with their buttons), the nozzles in their boots, the brand door."""
    B = BRANDS[b]
    p = Pic(1, 2, (180, 182, 182), 6200 + ord(b) * 2 + smash)
    W, H = p.W, p.H
    body = (196, 198, 196)
    p.rect(0, 0, W, H, fill=body)
    # the head: brand cap, the window panel
    p.rect(0, 0, W, 12, fill=B["main"])
    size = civic.fit(B["name"], BLACK, 8, W - 6)
    p.text(W / 2, 2.5, B["name"], BLACK, size, WHITE, anchor="ma")
    p.rect(3, 14, W - 3, 56, fill=(52, 54, 56))
    for k, label in enumerate(("SALE", "GALLONS")):
        y = 17 + k * 12
        p.rect(6, y, W - 6, y + 9, fill=(26, 40, 28))
        p.text(7, y + 1, label, SANS, 2.2, (170, 180, 170))
    rows = B["rows"]
    for k, (name, price) in enumerate(rows):
        y = 42 + k * 4.4
        p.text(6, y, name, SANS, 2.2, (220, 220, 210))
        p.ellipse(W - 13, y, W - 9.5, y + 3.2, fill=((236, 196, 40), (60, 120, 200), (200, 50, 40))[k])
    p.rect(3, 58, W - 3, 66, fill=B["second"])
    p.text(W / 2, 59.5, "PLEASE PAY CASHIER FIRST", SANS, 2.4, WHITE, anchor="ma")
    # the boots and nozzles (black handles, a coloured scuff guard each)
    for k, x in enumerate((8, 26, 44)):
        p.rect(x, 72, x + 12, 92, fill=(70, 72, 74))
        p.rect(x + 2, 74, x + 10, 98, fill=(26, 26, 28))
        p.rect(x + 2, 80, x + 10, 84, fill=((236, 196, 40), (60, 120, 200), (200, 50, 40))[k])
        p.rect(x + 4, 98, x + 8, 104, fill=(150, 150, 150))
    # the door: the brand colour, the stripe, louvres, the key lock
    p.rect(2, 106, W - 2, H - 4, fill=B["main"])
    p.rect(2, 112, W - 2, 115, fill=B["second"] if b == "c" else WHITE)
    for y in range(118, 124, 2):
        p.rect(10, y, W - 10, y + 1, fill=mixc(B["main"], (0, 0, 0), 0.4))
    p.ellipse(W - 10, 108, W - 7, 111, fill=(200, 200, 190))
    p.rect(0, H - 4, W, H, fill=(40, 40, 40))
    if smash:
        p.rect(6, 17, W - 6, 38, fill=(10, 12, 12))
        rng = np.random.default_rng(3)
        for _ in range(9):
            x0, y0 = rng.uniform(6, W - 6), rng.uniform(14, 56)
            p.line([(x0, y0), (x0 + rng.uniform(-12, 12), y0 + rng.uniform(-10, 10))], (200, 210, 210), 0.4)
        p.rect(26, 72, 38, 104, fill=(70, 72, 74))  # a nozzle gone
    return p


def pump(b, part, on=False, smash=False):
    t = pump_pic(b, smash).tile(0 if part == "hi" else 1, 6210 + ord(b) * 5 + (part == "lo") * 2 + on + smash * 3, smooth=0.5, grime=0.15)
    t.f0[:] = 50
    if part == "hi" and on and not smash:
        yy, xx = np.mgrid[0:N, 0:N]
        for k in range(2):
            y = 17 + k * 12
            win = ((xx >= 6) & (xx < N - 6) & (yy >= y) & (yy < y + 9)).astype(np.float32)
            t.mix(win, (40, 70, 44), 1.0)
            t.emit[:] = np.maximum(t.emit, win * 0.6)
        # the readings, still showing the last sale
        p = Pic(1, 1, (0, 0, 0, 0), 1)
        for k, digits in enumerate(("12.40", "11.592")):
            y = 18.5 + k * 12
            x = N - 8 - len(digits.replace(".", "")) * 4.4
            for ch in digits:
                if ch == ".":
                    p.rect(x - 1.2, y + 5.5, x - 0.4, y + 6.4, fill=LCD)
                    continue
                seg7(p, x, y, 3.2, 6, ch, LCD, slant=0.1, th=0.7)
                x += 4.4
        a = np.asarray(p.im.resize((N, N), Image.BOX), np.float32)
        m = a[..., 3] / 255
        t.mix(m, LCD, 1.0)
        t.emit[:] = np.maximum(t.emit, m)
    weather(t, rust=0.5 if not smash else 1.2, streak=0.5, grime=0.3, dents=1 + smash * 3)
    return t


def pump_side(b):
    """The dispenser's end: painted steel, the vents, a hose hook and the brand stripe."""
    B = BRANDS[b]
    t = F.plain(6230 + ord(b), (190, 192, 190), 0.03, 0.5)
    area(t, 0, 0, N, 8, B["main"])
    for y in range(14, 30, 3):
        area(t, 16, y, 48, y + 1, mul=0.6, dh=-0.2)
    area(t, 0, 50, N, 54, B["second"] if b == "c" else B["main"])
    area(t, 28, 34, 36, 40, (60, 60, 60), h=0.9)
    weather(t, rust=0.5, streak=0.6, grime=0.3, dents=2)
    return t


@lru_cache(maxsize=None)
def oldpump_pic(rust):
    """The old VALLEY pump, a block across and two high: the dial window with its drums, the price
    card, the chrome bezel, the red body, the name across the door."""
    V = VALLEY
    body = (168, 56, 46) if rust else V["main"]
    p = Pic(1, 2, body, 6300 + rust)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, fill=body)
    p.rect(4, 10, W - 4, 60, fill=(210, 212, 210))
    p.rect(7, 13, W - 7, 57, fill=V["cream"])
    for k, label in enumerate(("TOTAL SALE", "GALLONS")):
        y = 16 + k * 16
        p.text(W / 2, y, label, SANS, 2.6, (40, 40, 40), anchor="ma")
        x = 14
        for ch in ("03.48" if k == 0 else "011.6"):
            if ch == ".":
                p.rect(x - 1, y + 10, x, y + 11, fill=(20, 20, 20))
                continue
            p.rect(x, y + 4, x + 6, y + 12, fill=(20, 20, 20))
            p.text(x + 3, y + 4.5, ch, SANS, 5.5, WHITE, anchor="ma")
            x += 7.5
    p.rect(14, 49, W - 14, 55, fill=WHITE)
    p.text(W / 2, 49.6, "29.9 PER GAL", SANS, 3.2, (30, 30, 30), anchor="ma")
    p.rect(0, 64, W, 68, fill=(200, 200, 198))
    p.rect(8, 76, W - 8, 96, fill=V["cream"])
    size = civic.fit("VALLEY", SERIF, 10, W - 18)
    p.text(W / 2, 79, "VALLEY", SERIF, size, V["main"], anchor="ma")
    p.rect(8, 100, W - 8, 103, fill=V["second"])
    for y in range(108, 120, 3):
        p.rect(12, y, W - 12, y + 1.4, fill=mixc(body, (0, 0, 0), 0.4))
    p.rect(0, H - 5, W, H, fill=(40, 36, 34))
    return p


def oldpump(part, rust=False):
    t = oldpump_pic(rust).tile(0 if part == "hi" else 1, 6310 + (part == "lo") + rust * 2, smooth=0.6, grime=0.2)
    t.f0[:] = 40
    weather(t, rust=1.5 if rust else 0.5, streak=0.7, grime=0.4 if rust else 0.25, dents=2 if rust else 1)
    if rust:
        t.c = t.c * 0.85 + np.array((150, 120, 100), np.float32) * 0.15
    return t


def globe(seed, broken=False):
    """The pump's globe: milk glass, the red band with VALLEY on it."""
    t = F.plain(seed, (236, 232, 222), 0.03, 0.85)
    t.f0[:] = 60
    area(t, 0, 20, N, 44, VALLEY["main"], smooth=0.8)
    m = F.text_mask("VALLEY", SERIF, 10, N, 24, seed=1)
    F.paste(t, m, 0, 20, VALLEY["cream"], 1.0)
    if broken:
        m = F.blob_mask(t.rng, 40, 10, 20, rough=0.7, wrap=False)
        t.mix(m, (30, 30, 30))
    t.grime(0.3)
    return t


def old_side(rust=False):
    t = F.plain(6320 + rust, (168, 56, 46) if rust else VALLEY["main"], 0.04, 0.6)
    area(t, 0, 62, N, 66, (200, 200, 198))
    disc(t, 32, 30, 6, (60, 60, 60), h=0.9)
    weather(t, rust=1.5 if rust else 0.5, streak=0.6, grime=0.3, dents=1)
    return t


def squeegee_box(seed):
    t = F.plain(seed, (60, 64, 70), 0.04, 0.4)
    area(t, 8, 8, 56, 30, (230, 230, 226))
    m = F.text_mask("PAPER|TOWELS", SANS, 6, 48, 22, seed=1)
    F.paste(t, m, 8, 8, (40, 60, 120))
    weather(t, rust=0.6, streak=0.5, grime=0.3)
    return t


# ---------------------------------------------------------------- the price sign

@lru_cache(maxsize=None)
def price_pic(b, lit):
    """The price sign, three blocks across and five high: the brand panel over three rows of prices
    in red LEDs (or dead, the black flip numbers left)."""
    B = BRANDS[b]
    p = Pic(3, 5, (190, 192, 190), 6400 + ord(b) * 2 + lit)
    W, H = p.W, p.H
    p.rect(0, 0, W, 2 * N, fill=B["main"])
    p.rect(4, 4, W - 4, 2 * N - 4, outline=WHITE, width=2)
    emblem(p, 40, N, 30, b)
    size = civic.fit(B["name"], BLACK, 40, W - 90)
    p.text(W / 2 + 32, N - size * 0.62, B["name"], BLACK, size, WHITE, anchor="ma")
    for k, (name, price) in enumerate(B["rows"]):
        y0 = (2 + k) * N
        p.rect(0, y0, W, y0 + N, fill=(30, 30, 32))
        p.rect(3, y0 + 3, W - 3, y0 + N - 3, fill=WHITE)
        p.rect(3, y0 + 3, 80, y0 + N - 3, fill=WHITE)
        size = civic.fit(name, COND, 18, 72)
        p.text(42, y0 + N / 2 - size * 0.62, name, COND, size, (20, 20, 22), anchor="ma")
        p.rect(84, y0 + 6, W - 6, y0 + N - 6, fill=(18, 16, 16))
        x = 92
        digits = price.replace(".", "")
        for j, ch in enumerate(digits):
            if lit:
                seg7(p, x, y0 + 12, 18, 38, ch, LED, (40, 20, 18), th=3.6)
            else:
                p.rect(x, y0 + 12, x + 18, y0 + 50, fill=(232, 230, 220))
                p.line([(x, y0 + 31), (x + 18, y0 + 31)], (150, 150, 150), 0.6)
                p.text(x + 9, y0 + 13, ch if (k + j) % 4 else " ", BLACK, 30, (20, 20, 20), anchor="ma")
            if j == 0:
                p.ellipse(x + 21, y0 + 46, x + 25, y0 + 50, fill=LED if lit else (220, 220, 210))
                x += 6
            x += 24
        # the nine tenths, small and high
        seg7(p, x + 2, y0 + 12, 9, 17, "9", LED if lit else (60, 50, 46), (40, 20, 18), th=2)
        p.line([(x + 1, y0 + 32), (x + 14, y0 + 32)], LED if lit else (60, 50, 46), 1.2)
        for j, ch in enumerate("10"):
            seg7(p, x + 1 + j * 7, y0 + 34, 5, 11, ch, LED if lit else (60, 50, 46), None, th=1.3)
    return p


def price(b, i, lit):
    t = price_pic(b, lit).tile(i, 6410 + ord(b) * 31 + i * 2 + lit, smooth=0.6, grime=0.15)
    t.f0[:] = 40
    if lit:
        r = (t.c[..., 0] > 180) & (t.c[..., 1] < 110)
        lit_where(t, r, 1.0)
        if i < 6:
            w = t.c.min(-1) > 200
            lit_where(t, w, 0.7)
    weather(t, rust=0.3, streak=0.6, grime=0.25)
    return t


def pylon(b):
    t = F.plain(6430 + ord(b), BRANDS[b]["second"] if b == "c" else BRANDS[b]["main"], 0.04, 0.4)
    weather(t, rust=0.8, streak=0.6, grime=0.3, dents=1)
    return t


# ---------------------------------------------------------------- the forecourt

def slab(seed, kind="plain"):
    """The forecourt's concrete: broom-finished, oil dripped where the cars stood, cracks, the saw cut."""
    t = F.plain(seed, (150, 148, 142), 0.07, 0.18)
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.97 + 0.04 * np.sin(yy * 1.7 + P.tnoise(rng, 8) * 3))[..., None]
    t.speckle(0.12, (118, 116, 110), 0.35)
    for _ in range(2):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(4, 12), (96, 92, 86), 0.3)
    if kind == "oil":
        for _ in range(3):
            m = F.blob_mask(rng, rng.uniform(16, 48), rng.uniform(16, 48), rng.uniform(5, 13), rough=0.5)
            t.mix(m, (36, 34, 32), 0.75)
            t.smooth += m * 0.4
            t.f0[m > 0.5] = 60
        t.mix(((rng.random((N, N)) < 0.03)).astype(np.float32), (40, 38, 36), 0.6)
    elif kind == "crack":
        t.crack(2, 0.35, (50, 48, 44))
        t.mix((rng.random((N, N)) < 0.02).astype(np.float32), (70, 90, 50), 0.7)
    elif kind == "joint":
        area(t, 0, 0, N, 2, (60, 58, 54), dh=-0.4)
        t.mix(((yy < 3) & (rng.random((N, N)) < 0.15)).astype(np.float32), (70, 90, 50), 0.8)
    elif kind == "fill":
        for k, (x, col) in enumerate(((12, (200, 50, 40)), (32, (236, 236, 228)), (52, (230, 190, 40)))):
            disc(t, x, 32, 8.5, (90, 92, 94), h=0.7, smooth=0.5)
            disc(t, x, 32, 7, col, h=0.75, smooth=0.5)
            disc(t, x, 32, 2, (70, 70, 70))
            area(t, x - 6, 31, x + 6, 33, mul=0.8)
        t.grime(0.2)
    t.grime(0.25)
    return t


# ---------------------------------------------------------------- the mart's outside

@lru_cache(maxsize=None)
def store_pic(b, lit):
    """The mart's fascia, eight blocks across: the canopy's bands with the store's name on them."""
    B = BRANDS[b]
    p = Pic(8, 1, WHITE, 6500 + ord(b) * 2 + lit)
    fascia_base(p, b, 0, N)
    x0, x1 = N * 1.2, p.W - N * 1.2
    p.rect(x0, 5, x1, N - 5, fill=WHITE)
    p.rect(x0 + 2, 7, x1 - 2, N - 7, outline=B["main"], width=1.2)
    emblem(p, x0 + 28, N / 2, 20, b)
    size = civic.fit(B["store"], BLACK, 38, x1 - x0 - 72)
    p.text((x0 + 56 + x1) / 2, N / 2 - size * 0.62, B["store"], BLACK, size, B["main"], anchor="ma")
    return p


def store(b, i, lit):
    t = store_pic(b, lit).tile(i, 6510 + ord(b) * 17 + i * 2 + lit, smooth=0.55, grime=0.12)
    t.f0[:] = 40
    if lit and 1 <= i <= 6:
        lit_where(t, (t.c[..., 0] > 120) & (np.abs(t.c[..., 0] - t.c[..., 2]) > 60) | (t.c.min(-1) > 225), 0.75)
    area(t, 0, 0, 1, N, mul=0.7)
    weather(t, rust=0.4, streak=0.75, grime=0.3, dents=1)
    return t


POSTERS = ["beer", "lotto", "soda", "coffee", "missing", "notice"]


@lru_cache(maxsize=None)
def poster_pic(n):
    """A window poster, a block square, taped inside the glass."""
    kind = POSTERS[n]
    p = Pic(1, 1, (0, 0, 0, 0), 6600 + n)
    W = p.W
    if kind == "beer":
        p.rect(6, 6, W - 6, W - 6, fill=(20, 40, 80))
        p.text(W / 2, 9, "ICE COLD", BLACK, 6, (240, 240, 240), anchor="ma")
        p.rect(24, 18, 40, 50, fill=(140, 90, 30))
        p.rect(28, 13, 36, 19, fill=(140, 90, 30))
        p.rect(24, 28, 40, 38, fill=(230, 220, 190))
        p.text(32, 30, "RIVER|BEND", SANS, 2.6, (150, 30, 30), anchor="ma")
        p.text(W / 2, 51, "$4.99 6PK", BLACK, 5, (240, 200, 60), anchor="ma")
    elif kind == "lotto":
        p.rect(6, 6, W - 6, W - 6, fill=(240, 200, 40))
        p.text(W / 2, 9, "COMMONWEALTH", SANS, 3.4, (30, 60, 140), anchor="ma")
        p.text(W / 2, 16, "LOTTO", BLACK, 10, (200, 30, 30), anchor="ma")
        p.text(W / 2, 32, "JACKPOT", SANS, 4, (30, 30, 30), anchor="ma")
        p.text(W / 2, 39, "$4 MILLION", BLACK, 7, (30, 60, 140), anchor="ma")
    elif kind == "soda":
        p.rect(6, 6, W - 6, W - 6, fill=(200, 30, 36))
        p.text(W / 2, 9, "2 LITER", BLACK, 7, WHITE, anchor="ma")
        p.text(W / 2, 22, "2 FOR", BLACK, 7, (250, 230, 120), anchor="ma")
        p.text(W / 2, 34, "$3", BLACK, 16, WHITE, anchor="ma")
    elif kind == "coffee":
        p.rect(6, 6, W - 6, W - 6, fill=(80, 46, 26))
        p.ellipse(22, 22, 42, 30, fill=(230, 226, 214))
        p.rect(22, 26, 42, 46, fill=(230, 226, 214))
        p.rect(24, 24, 40, 28, fill=(60, 30, 16))
        p.text(W / 2, 9, "FRESH HOT", BLACK, 5, (240, 220, 180), anchor="ma")
        p.text(W / 2, 48, "COFFEE 59c", BLACK, 5.5, (240, 220, 180), anchor="ma")
    elif kind == "missing":
        p.rect(8, 6, W - 8, W - 4, fill=(236, 234, 226))
        p.text(W / 2, 8, "MISSING", BLACK, 8, (20, 20, 20), anchor="ma")
        p.rect(18, 19, 46, 41, fill=(150, 150, 150))
        p.ellipse(26, 22, 38, 34, fill=(110, 110, 110))
        p.rect(23, 32, 41, 41, fill=(110, 110, 110))
        p.text(W / 2, 43, "DANA R. AGE 9|LAST SEEN 7/9", SANS, 2.8, (40, 40, 40), anchor="ma")
    else:
        p.rect(8, 6, W - 8, W - 4, fill=(236, 234, 226))
        p.text(W / 2, 8, "NOTICE", BLACK, 7, (170, 20, 20), anchor="ma")
        p.text(W / 2, 18, "BY ORDER OF THE|GOVERNOR ALL|RETAIL SALES OF|FUEL SUSPENDED|REPORT TO|SHELTERS", SANS, 3.2,
               (20, 20, 20), anchor="ma")
    return p


def storefront(seed, part, poster=None, broken=False):
    """The mart's glass front: bronze aluminium frame, the glass (dark, a reflection across it), a
    poster taped inside; broken: holes knocked through, cracks round them."""
    t = F.plain(seed, (40, 46, 50), 0.02, 0.9)
    t.f0[:] = 90
    t.alpha[:] = 140
    yy, xx = np.mgrid[0:N, 0:N]
    t.c += (np.clip(1 - np.abs(xx + yy * 0.6 - 50) / 18, 0, 1) * 40)[..., None]
    frame = (xx < 3) | (xx >= N - 3)
    if part == "lo":
        frame |= yy >= N - 8
    else:
        frame |= yy < 4
    if poster is not None:
        pic = poster_pic(poster).shrunk()
        a = pic[..., 3:4] / 255
        sl = (slice(4, N - 4) if part == "lo" else slice(10, N), slice(4, N - 4))
        sub = pic[4:N - 4, 4:N - 4] if part == "lo" else pic[0:N - 10, 4:N - 4]
        aa = sub[..., 3] / 255
        t.c[sl] = t.c[sl] * (1 - aa[..., None]) + sub[..., :3] * aa[..., None] * 0.9
        t.alpha[sl] = np.maximum(t.alpha[sl], aa * 255)
        t.smooth[sl] = np.where(aa > 0.5, 0.4, t.smooth[sl])
        del a
    if broken:
        rng = t.rng
        hole = np.zeros((N, N), np.float32)
        for _ in range(2):
            hole = np.maximum(hole, F.blob_mask(rng, rng.uniform(14, 50), rng.uniform(14, 50), rng.uniform(10, 18), rough=0.8, wrap=False))
        t.alpha[:] = np.where(hole > 0.5, 0, t.alpha)
        ring = np.clip((hole > 0.15) & (hole <= 0.5), 0, 1).astype(np.float32)
        t.mix(ring, (200, 210, 210), 0.7)
        t.alpha[:] = np.maximum(t.alpha, ring * 200)
    t.c[frame] = (92, 76, 58)
    t.alpha[frame] = 255
    t.smooth[frame] = 0.5
    t.f0[frame] = 120
    t.grime(0.15)
    return t


@lru_cache(maxsize=None)
def ice_pic():
    """The ice merchandiser, two blocks by two: white enamel, ICE in blue letters, frost round the doors."""
    p = Pic(2, 2, (232, 234, 232), 6700)
    W, H = p.W, p.H
    p.rect(0, 0, W, 26, fill=(30, 80, 160))
    p.text(W / 2, 3, "PARTY ICE", BLACK, 18, WHITE, anchor="ma")
    p.rect(6, 30, W / 2 - 2, H - 10, outline=(170, 172, 172), width=2)
    p.rect(W / 2 + 2, 30, W - 6, H - 10, outline=(170, 172, 172), width=2)
    size = civic.fit("ICE", BLACK, 70, W / 2 - 14)
    for x0 in (6, W / 2 + 2):
        p.text(x0 + (W / 2 - 8) / 2, 44, "ICE", BLACK, size, (40, 110, 200), anchor="ma")
    p.rect(W / 2 - 6, 66, W / 2 - 3, 80, fill=(150, 150, 150))
    p.rect(W / 2 + 3, 66, W / 2 + 6, 80, fill=(150, 150, 150))
    p.rect(W / 2 - 5, 74, W / 2 + 5, 79, fill=(90, 90, 96))  # the padlock hasp
    p.rect(0, H - 10, W, H, fill=(60, 62, 64))
    p.text(W / 2, 100, "SELF SERVE  PAY INSIDE", SANS, 5, (30, 80, 160), anchor="ma")
    return p


def ice(i):
    t = ice_pic().tile(i, 6710 + i, smooth=0.6, grime=0.2)
    rng = t.rng
    t.mix((rng.random((N, N)) < 0.02).astype(np.float32), (250, 250, 255), 0.6)
    weather(t, rust=1.0, streak=0.6, grime=0.3, dents=2)
    return t


def cage(seed):
    """Expanded steel mesh, galvanised: diamonds of wire and the holes between."""
    t = F.plain(seed, (150, 152, 150), 0.05, 0.5)
    yy, xx = np.mgrid[0:N, 0:N]
    u, v = (xx + yy * 0.5) % 8, (xx - yy * 0.5) % 8
    wire = (u < 1.6) | (v < 1.6)
    t.alpha[:] = wire.astype(np.float32) * 255
    area(t, 0, 0, N, 3, (120, 122, 120), alpha=255)
    area(t, 0, N - 3, N, N, (120, 122, 120), alpha=255)
    area(t, 0, 0, 3, N, (120, 122, 120), alpha=255)
    area(t, N - 3, 0, N, N, (120, 122, 120), alpha=255)
    weather(t, rust=0.8, streak=0.3, grime=0.2)
    return t


def plate(seed, text, bg, ink, font=SANS, size=8):
    """A sign plate: text on a coloured ground, a thin border, rivets, grime."""
    t = F.plain(seed, bg, 0.03, 0.5)
    m = F.text_mask(text, font, civic.fit(text, font, size, N - 6), N - 6, N - 8, seed=seed)
    F.paste(t, m, 3, 4, ink)
    area(t, 0, 0, N, 1, mul=0.7)
    area(t, 0, N - 1, N, N, mul=0.7)
    for x, y in ((3, 3), (N - 4, 3), (3, N - 4), (N - 4, N - 4)):
        disc(t, x, y, 0.8, (150, 150, 150))
    weather(t, rust=0.4, streak=0.4, grime=0.25)
    return t


def phone_face(seed):
    """The payphone: stainless, the keypad, the coin slot and return, the handset on its hook."""
    t = F.plain(seed, (170, 174, 176), 0.03, 0.75)
    t.f0[:] = 140
    area(t, 6, 6, 58, 16, (30, 70, 150))
    m = F.text_mask("TELEPHONE", SANS, 6, 52, 10, seed=2)
    F.paste(t, m, 6, 6, WHITE)
    for r in range(4):
        for c in range(3):
            x, y = 26 + c * 7, 22 + r * 7
            area(t, x, y, x + 5, y + 5, (60, 62, 64), h=0.9)
            area(t, x + 1, y + 1, x + 4, y + 4, (200, 200, 196), h=1.0)
    area(t, 50, 22, 54, 30, (30, 30, 30), h=0.2)
    area(t, 48, 46, 56, 52, (40, 40, 40), h=0.2)
    area(t, 8, 20, 18, 56, (26, 26, 28), h=0.95, smooth=0.5)  # the handset
    area(t, 10, 22, 16, 28, (40, 40, 42))
    area(t, 10, 48, 16, 54, (40, 40, 42))
    weather(t, rust=0.2, streak=0.6, grime=0.3, dents=1)
    t.speckle(0.03, (60, 60, 60), 0.4)
    return t


NEWS = [("GRAYHAVEN|COURIER", (40, 80, 160), "FLU CASES|CLIMB"), ("COUNTY|HERALD", (190, 40, 40), "GUARD|CALLED UP"),
        ("TRADER", (230, 190, 40), "FREE|TAKE ONE")]


def news_face(n):
    name, col, headline = NEWS[n]
    t = F.plain(6800 + n, col, 0.05, 0.5)
    area(t, 4, 4, 60, 18, (236, 234, 226))
    m = F.text_mask(name, BLACK, 6, 56, 14, seed=n)
    F.paste(t, m, 4, 4, (20, 20, 20))
    area(t, 6, 22, 58, 46, (40, 44, 48), smooth=0.9)
    area(t, 9, 24, 55, 46, (220, 216, 200))
    m = F.text_mask(headline, BLACK, 7, 44, 18, seed=n + 1)
    F.paste(t, m, 10, 25, (20, 20, 20))
    area(t, 26, 50, 38, 56, (150, 150, 150), h=0.9)
    area(t, 30, 51, 34, 53, (30, 30, 30))
    weather(t, rust=0.7, streak=0.6, grime=0.3, dents=2)
    return t


def air_face(seed):
    t = F.plain(seed, (190, 36, 30), 0.05, 0.5)
    area(t, 6, 6, 58, 22, (236, 234, 226))
    m = F.text_mask("AIR", BLACK, 14, 52, 16, seed=1)
    F.paste(t, m, 6, 6, (190, 36, 30))
    disc(t, 20, 36, 9, (220, 220, 214), h=0.9)
    disc(t, 20, 36, 7.5, (240, 240, 236))
    area(t, 19.5, 30, 20.5, 36, (200, 30, 30))
    m = F.text_mask("50c", BLACK, 9, 24, 12, seed=2)
    F.paste(t, m, 34, 28, WHITE)
    area(t, 40, 44, 50, 52, (40, 40, 40), h=0.3)
    weather(t, rust=0.8, streak=0.6, grime=0.35, dents=1)
    return t


def vac_face(seed):
    t = F.plain(seed, (232, 190, 40), 0.05, 0.5)
    area(t, 6, 6, 58, 22, (30, 30, 30))
    m = F.text_mask("VACUUM", BLACK, 9, 52, 16, seed=3)
    F.paste(t, m, 6, 6, (232, 190, 40))
    m = F.text_mask("75c|4 MIN", SANS, 6, 40, 16, seed=4)
    F.paste(t, m, 12, 26, (30, 30, 30))
    area(t, 26, 46, 38, 54, (40, 40, 40), h=0.3)
    weather(t, rust=0.8, streak=0.6, grime=0.35, dents=2)
    return t


# ---------------------------------------------------------------- inside the mart

def vct(seed, kind="plain"):
    """Vinyl tile, twelve inches: off-white with grey chips, every fourth one the accent blue; mud."""
    t = F.plain(seed, (218, 214, 202), 0.03, 0.55)
    rng = t.rng
    t.speckle(0.18, (170, 168, 160), 0.3)
    t.speckle(0.05, (120, 118, 110), 0.3)
    for k in (0, 32):
        area(t, 0, k, N, k + 1, mul=0.85, dh=-0.1)
        area(t, k, 0, k + 1, N, mul=0.85, dh=-0.1)
    if kind == "b":
        area(t, 1, 1, 32, 32, (120, 138, 160))
        t.speckle(0.04, (90, 100, 120), 0.3)
    elif kind == "mud":
        for _ in range(5):
            x, y = rng.uniform(0, N, 2)
            m = F.blob_mask(rng, x, y, rng.uniform(4, 9), rough=0.6)
            t.mix(m, (96, 76, 52), 0.6)
        for k in range(4):
            x, y = 10 + k * 13, 40 - k * 8
            area(t, x, y, x + 6, y + 12, (80, 64, 46))
    t.grime(0.2)
    return t


def frp(seed, b=None):
    """Fibreglass panel up the wall: cream, pebbled, the brand's stripe along its top on some."""
    t = F.plain(seed, (226, 220, 200), 0.03, 0.35)
    t.speckle(0.4, (210, 204, 186), 0.3, height=0.15)
    area(t, 0, 0, N, 2, (200, 200, 196), smooth=0.6)
    if b:
        B = BRANDS[b]
        area(t, 0, 2, N, 10, B["main"], smooth=0.5)
        area(t, 0, 10, N, 12, B["second"], smooth=0.5)
    for _ in range(6):
        x, y = t.rng.uniform(0, N), t.rng.uniform(40, N)
        area(t, x, y, x + t.rng.uniform(4, 14), y + 1, (60, 56, 50))  # cart scuffs
    t.grime(0.25)
    return t


GOODS = [(200, 30, 30), (230, 230, 226), (40, 90, 180), (240, 200, 40), (60, 140, 70), (230, 120, 30), (120, 60, 140),
         (180, 140, 60), (30, 30, 30), (220, 210, 170)]


def cooler(state, part, lit):
    """A reach-in cooler door, one block of a two-high bank: the chrome frame, the glass with its
    reflection, the shelves behind it stocked (or picked over, or bare, or the glass smashed)."""
    p = Pic(1, 1, (30, 34, 36), 6900 + "full half empty smash".split().index(state) * 4 + (part == "hi") * 2 + lit)
    rng = np.random.default_rng(6950 + (part == "hi") * 7 + "full half empty smash".split().index(state))
    W = p.W
    shelves = (14, 34, 54) if part == "hi" else (14, 36)
    p.rect(0, 0, W, W, fill=(52, 58, 62) if lit else (26, 28, 30))
    if part == "hi":
        p.rect(0, 0, W, 6, fill=(200, 200, 200))
    else:
        p.rect(0, W - 10, W, W, fill=(40, 40, 42))
        for x in range(4, W - 4, 3):
            p.rect(x, W - 8, x + 1.5, W - 2, fill=(20, 20, 20))
    keep = {"full": 0.95, "half": 0.45, "empty": 0.06, "smash": 0.3}[state]
    for base in shelves:
        p.rect(4, base, W - 4, base + 1.5, fill=(170, 172, 170))
        x = 6.0
        while x < W - 9:
            kind = rng.random()
            w = 4.5 if kind < 0.5 else 6 if kind < 0.8 else 9
            if rng.random() < keep:
                col = GOODS[rng.integers(len(GOODS))]
                if kind < 0.5:  # cans, stacked two high
                    for k in range(2):
                        p.rect(x, base - 6 * (k + 1), x + 3.6, base - 6 * k - 0.4, fill=col)
                        p.rect(x + 0.4, base - 6 * (k + 1), x + 3.2, base - 6 * (k + 1) + 0.8, fill=(190, 190, 190))
                elif kind < 0.8:  # bottles
                    p.rect(x, base - 12, x + 4.6, base, fill=mixc(col, (40, 40, 40), 0.3))
                    p.rect(x + 1.4, base - 15, x + 3.2, base - 12, fill=mixc(col, (40, 40, 40), 0.3))
                    p.rect(x, base - 8, x + 4.6, base - 5, fill=(236, 232, 220))
                else:  # a case of beer
                    p.rect(x, base - 9, x + 8, base, fill=col)
                    p.rect(x, base - 6, x + 8, base - 4, fill=(236, 232, 220))
            x += w + 0.6
    # the frame, the handle
    p.rect(0, 0, 3, W, fill=(186, 188, 188))
    p.rect(W - 3, 0, W, W, fill=(186, 188, 188))
    p.rect(W - 9, 4 if part == "lo" else 20, W - 6, W if part == "lo" else W, fill=(210, 212, 212))
    t = p.tile(0, 6960 + (part == "hi") * 3 + lit, smooth=0.85, grime=0.1)
    t.f0[:] = 70
    yy, xx = np.mgrid[0:N, 0:N]
    t.mix((np.abs(xx - yy * 0.7 - 24) < 3).astype(np.float32), (240, 244, 244), 0.18)
    if lit:
        strip = np.zeros((N, N), np.float32)
        for base in shelves:
            strip[max(0, base - 18):base, 4:N - 4] = 0.35
        t.emit[:] = np.maximum(t.emit, strip)
        t.c *= 1.15
    if state == "smash":
        hole = F.blob_mask(t.rng, 30, 30, 18, rough=0.9, wrap=False)
        crack = np.zeros((N, N), np.float32)
        for a in np.linspace(0, np.pi * 2, 9)[:-1]:
            for r in range(16, 30):
                x, y = int(30 + np.cos(a) * r), int(30 + np.sin(a) * r)
                if 0 <= x < N and 0 <= y < N:
                    crack[y, x] = 1
        t.mix(crack, (220, 228, 228), 0.8)
        t.mix(hole * 0.0, (0, 0, 0))
    return t


@lru_cache(maxsize=None)
def cool_head_pic():
    p = Pic(4, 1, (30, 30, 34), 6970)
    p.rect(0, 8, p.W, N - 8, fill=(200, 30, 36))
    text = "ICE COLD BEER  .  SODA  .  DAIRY"
    size = civic.fit(text, BLACK, 26, p.W - 24)
    p.text(p.W / 2, N / 2 - size * 0.62, text, BLACK, size, WHITE, anchor="ma")
    return p


def cool_head(i):
    t = cool_head_pic().tile(i, 6975 + i, smooth=0.5, grime=0.15)
    lit_where(t, t.c.min(-1) > 200, 0.6)
    return t


def laminate(seed, rgb=(200, 194, 180)):
    t = F.plain(seed, rgb, 0.03, 0.6)
    t.speckle(0.25, mixc(rgb, (0, 0, 0), 0.2), 0.3)
    t.grime(0.2)
    return t


def counter_front(b, candy=False):
    """The counter's front: a laminate panel, the brand stripes along the kick; or the candy rack hung on it."""
    B = BRANDS[b]
    t = F.plain(7000 + ord(b) * 2 + candy, (210, 206, 196), 0.03, 0.55)
    area(t, 0, 50, N, N, B["main"])
    area(t, 0, 46, N, 50, B["second"])
    area(t, 0, 0, N, 3, (150, 150, 146))
    if candy:
        rng = t.rng
        for row, y in enumerate((8, 22, 36)):
            area(t, 3, y + 10, N - 3, y + 12, (150, 152, 150))
            x = 4
            while x < N - 6:
                col = GOODS[rng.integers(len(GOODS))]
                if rng.random() < 0.7:
                    for k in range(3):
                        area(t, x, y + 8 - k * 2.6, x + 6, y + 10 - k * 2.6, col if k % 2 == 0 else mixc(col, WHITE, 0.3))
                x += 7
    weather(t, rust=0.0, streak=0.2, grime=0.35, dents=1)
    return t


def cig_wall(seed, lo):
    """The tobacco wall behind the counter: rows of packs in their slots, gaps where they were taken;
    the drawer cabinet below with WE CARD on it."""
    if lo:
        t = F.plain(seed, (90, 64, 44), 0.04, 0.5)
        for y in (6, 26, 46):
            area(t, 4, y, N - 4, y + 16, (110, 80, 54))
            edges(t, 4, y, N - 4, y + 16)
            area(t, 26, y + 6, 38, y + 8, (190, 190, 180))
        area(t, 10, 8, 54, 20, (240, 236, 220))
        m = F.text_mask("WE CARD", BLACK, 7, 44, 12, seed=1)
        F.paste(t, m, 10, 8, (190, 30, 30))
        t.grime(0.3)
        return t
    t = F.plain(seed, (36, 30, 26), 0.03, 0.4)
    rng = t.rng
    packs = [(236, 236, 230), (200, 30, 30), (40, 70, 150), (60, 140, 80), (220, 180, 50), (230, 120, 40), (30, 30, 30),
             (150, 120, 90)]
    for row, y in enumerate(range(4, N - 4, 12)):
        area(t, 2, y + 10, N - 2, y + 12, (150, 150, 146))
        for x in range(3, N - 6, 6):
            if rng.random() < 0.35:
                continue
            col = packs[rng.integers(len(packs))]
            area(t, x, y + 1, x + 5, y + 10, col, h=0.8)
            area(t, x, y + 1, x + 5, y + 4, mixc(col, WHITE, 0.6) if col != (236, 236, 230) else (200, 30, 30))
            area(t, x + 1, y + 6, x + 4, y + 7, (240, 240, 236))
    t.grime(0.2)
    return t


def lotto_face(seed):
    t = F.plain(seed, (60, 140, 190), 0.03, 0.85)
    t.f0[:] = 70
    rng = t.rng
    for k, x in enumerate(range(4, N - 4, 10)):
        col = GOODS[(k * 3) % len(GOODS)]
        area(t, x, 10, x + 8, 50, col, h=0.8)
        for y in range(12, 48, 6):
            area(t, x, y, x + 8, y + 0.6, mul=0.7)
    area(t, 0, 0, N, 8, (240, 200, 40))
    m = F.text_mask("SCRATCH-OFFS", SANS, 6, N, 8, seed=2)
    F.paste(t, m, 0, 0, (30, 60, 140))
    t.mix((rng.random((N, N)) < 0.01).astype(np.float32), (255, 255, 255), 0.4)
    return t


def roller_face(seed, on):
    """The roller grill: chrome rollers under the sneeze guard, the hot dogs gone grey and shrivelled."""
    t = F.plain(seed, (180, 182, 182), 0.03, 0.8)
    t.f0[:] = 140
    for k, y in enumerate(range(6, N - 6, 6)):
        area(t, 2, y, N - 2, y + 4, (200, 202, 202))
        area(t, 2, y + 3, N - 2, y + 4, (120, 120, 120))
        if k % 2 == 0:
            for x in range(6, N - 14, 16):
                area(t, x, y, x + 12, y + 3, (110, 80, 66) if not on else (170, 90, 60))
    if on:
        t.emit[:] = np.maximum(t.emit, 0.12)
        t.c = t.c * 0.8 + np.array((255, 140, 60), np.float32) * 0.2
    t.grime(0.3)
    return t


def brewer_face(seed, on):
    t = F.plain(seed, (40, 40, 42), 0.03, 0.6)
    for x in (6, 26, 46):
        area(t, x, 8, x + 12, 20, (60, 60, 60))
        area(t, x + 1, 30, x + 11, 52, (90, 90, 96), smooth=0.9)
        area(t, x + 1, 40, x + 11, 52, (40, 24, 14), smooth=0.9)
        area(t, x + 3, 26, x + 9, 30, (200, 120, 40) if x != 26 else (40, 120, 60))
        if on:
            area(t, x + 2, 54, x + 10, 57, (255, 60, 30))
    if on:
        lit_where(t, (t.c[..., 0] > 200) & (t.c[..., 1] < 90), 1.0)
    t.grime(0.3)
    return t


def fountain_face(seed):
    t = F.plain(seed, (40, 40, 44), 0.03, 0.6)
    labels = [(200, 30, 30), (236, 236, 230), (60, 160, 70), (120, 60, 30), (240, 140, 40), (40, 40, 40)]
    for k, col in enumerate(labels):
        x = 4 + k * 10
        area(t, x, 6, x + 8, 22, col)
        area(t, x + 2, 24, x + 6, 34, (180, 180, 180))
        area(t, x + 3, 34, x + 5, 38, (90, 90, 90))
    area(t, 0, 44, N, 50, (150, 150, 150))
    for x in range(2, N - 2, 3):
        area(t, x, 50, x + 1.5, 58, (60, 60, 60))
    t.grime(0.3)
    return t


def slush_face(seed, on):
    t = F.plain(seed, (200, 200, 198), 0.03, 0.6)
    for x, col in ((4, (200, 30, 40)), (34, (40, 90, 210))):
        area(t, x, 6, x + 26, 40, mixc(col, (40, 40, 40), 0.3) if not on else col, smooth=0.9)
        area(t, x + 2, 8, x + 24, 14, (230, 236, 240), smooth=0.9)
        area(t, x + 8, 44, x + 18, 52, (60, 60, 60))
    if on:
        t.emit[:] = np.maximum(t.emit, ((t.c[..., 2] > 180) | (t.c[..., 0] > 180)).astype(np.float32) * 0.4)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def menu_pic():
    p = Pic(3, 1, (24, 24, 26), 7100)
    W = p.W
    items = [("HOT DOGS", "2 / $1.50", (190, 90, 50)), ("COFFEE", "ANY SIZE 59c", (90, 50, 30)), ("FOUNTAIN", "32 OZ 79c", (200, 30, 30))]
    for k, (name, price, col) in enumerate(items):
        x = k * N
        p.rect(x + 4, 4, x + N - 4, 34, fill=col)
        p.text(x + N / 2, 8, name, BLACK, 7, WHITE, anchor="ma")
        p.text(x + N / 2, 38, price, BLACK, 6.5, (250, 220, 90), anchor="ma")
    return p


def menu(i):
    t = menu_pic().tile(i, 7105 + i, smooth=0.6, grime=0.15)
    lit_where(t, t.c.max(-1) > 120, 0.45)
    return t


def rack_face(seed, kind, low=False):
    """A wire rack of goods seen side on: chips bags, oil quarts and jugs, magazines, bread loaves."""
    t = F.plain(seed, (60, 62, 64), 0.03, 0.4)
    t.alpha[:] = 0
    rng = t.rng
    keep = 0.3 if low else 0.9
    if kind == "chips":
        for y in (2, 18, 34, 50):
            area(t, 1, y + 13, N - 1, y + 14, (150, 152, 150), alpha=255)
            for x in range(2, N - 10, 10):
                if rng.random() < keep:
                    col = GOODS[rng.integers(len(GOODS))]
                    area(t, x, y, x + 9, y + 13, col, h=0.8, alpha=255)
                    area(t, x + 1, y + 4, x + 8, y + 7, mixc(col, WHITE, 0.6), alpha=255)
                    area(t, x, y, x + 9, y + 1, mul=0.7)
    elif kind == "oil":
        for y in (4, 26, 48):
            area(t, 1, y + 14, N - 1, y + 16, (150, 152, 150), alpha=255)
            x = 2
            while x < N - 8:
                if rng.random() < keep:
                    if rng.random() < 0.7:  # quarts
                        col = ((230, 190, 40), (40, 40, 40), (200, 30, 30), (30, 70, 150))[rng.integers(4)]
                        area(t, x, y + 2, x + 5, y + 14, col, alpha=255)
                        area(t, x + 1.5, y, x + 3.5, y + 2, (30, 30, 30), alpha=255)
                        area(t, x, y + 6, x + 5, y + 10, (236, 236, 230), alpha=255)
                        x += 6
                    else:  # antifreeze, washer fluid
                        col = ((60, 190, 70), (60, 120, 230))[rng.integers(2)]
                        area(t, x, y, x + 9, y + 14, col, alpha=255)
                        area(t, x + 6, y - 2, x + 8, y, (240, 200, 40), alpha=255)
                        x += 10
                else:
                    x += 6
    elif kind == "mags":
        for y in (4, 24, 44):
            area(t, 1, y + 16, N - 1, y + 18, (150, 120, 80), alpha=255)
            for x in range(2, N - 12, 13):
                if rng.random() < keep:
                    col = GOODS[rng.integers(len(GOODS))]
                    area(t, x, y, x + 12, y + 16, col, alpha=255)
                    area(t, x + 1, y + 1, x + 11, y + 4, (240, 240, 236), alpha=255)
                    area(t, x + 2, y + 6, x + 10, y + 14, mixc(col, (0, 0, 0), 0.3), alpha=255)
    elif kind == "bread":
        for y in (6, 28, 50):
            area(t, 1, y + 10, N - 1, y + 12, (150, 152, 150), alpha=255)
            for x in range(2, N - 14, 15):
                if rng.random() < keep:
                    area(t, x, y, x + 14, y + 10, (236, 230, 210), alpha=255)
                    area(t, x + 2, y + 2, x + 12, y + 8, (200, 150, 80), alpha=255)
                    area(t, x + 4, y + 3, x + 10, y + 6, (60, 100, 190), alpha=255)
    t.grime(0.15)
    return t


def freezer_top(seed):
    t = F.plain(seed, (190, 200, 206), 0.03, 0.9)
    t.f0[:] = 70
    rng = t.rng
    for x in range(4, N - 8, 9):
        for y in range(6, N - 10, 12):
            if rng.random() < 0.5:
                col = GOODS[rng.integers(len(GOODS))]
                area(t, x, y, x + 8, y + 10, mixc(col, (200, 210, 220), 0.4))
    t.mix((rng.random((N, N)) < 0.08).astype(np.float32), (240, 244, 248), 0.5)
    area(t, 0, 31, N, 33, (200, 200, 200))
    t.grime(0.2)
    return t


def neon(seed, kind, on):
    """A window neon on its black frame: COLD BEER in red script over a blue rule, or LOTTO in green."""
    t = F.plain(seed, (20, 20, 22), 0.02, 0.3)
    t.alpha[:] = 0
    text, col = ("COLD|BEER", (255, 50, 50)) if kind == "beer" else ("LOTTO", (60, 255, 110))
    m = F.text_mask(text, OBLIQUE, 13 if kind == "beer" else 15, N, N - 8, seed=seed)
    tube = np.zeros((N, N), np.float32)
    tube[1:N - 7] = (m > 0.35)
    lit = col if on else mixc(col, (60, 60, 60), 0.6)
    t.mix(tube, lit)
    t.alpha[:] = np.maximum(t.alpha, tube * 255)
    if kind == "beer":
        area(t, 8, N - 8, N - 8, N - 6, (60, 120, 255) if on else (60, 70, 90), alpha=255)
    area(t, 2, N - 3, N - 2, N - 1, (30, 30, 30), alpha=255)
    if on:
        t.emit[:] = np.maximum(t.emit, (t.alpha > 0) * (t.c.max(-1) > 120))
    return t


def mirror_dome(seed):
    t = F.plain(seed, (170, 176, 180), 0.04, 0.95)
    t.f0[:] = 200
    yy, xx = np.mgrid[0:N, 0:N]
    t.c += (np.clip(1 - np.hypot(xx - 24, yy - 22) / 20, 0, 1) * 60)[..., None]
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the old station

def enamel(seed, kind="plain"):
    """Porcelain enamel panels, white, the joints raised; rust blooming where the enamel chipped; or
    the red band with its green pinstripe for the fascia."""
    t = F.plain(seed, VALLEY["cream"], 0.03, 0.75)
    t.f0[:] = 60
    for x in (0, 32):
        area(t, x, 0, x + 1, N, mul=0.75, dh=0.15)
        area(t, x + 1, 0, x + 2, N, mul=1.06)
    area(t, 0, 0, N, 1, mul=0.75)
    if kind == "band":
        area(t, 0, 8, N, 48, VALLEY["main"], smooth=0.75)
        area(t, 0, 50, N, 54, VALLEY["second"], smooth=0.75)
    rng = t.rng
    n = 3 if kind != "rust" else 9
    for _ in range(n):
        x, y = rng.uniform(0, N, 2)
        m = F.blob_mask(rng, x, y, rng.uniform(1.5, 4 if kind != "rust" else 7), rough=0.7)
        t.mix(m, (40, 34, 30), 0.9)
        t.mix(P.blur(m, 2) * (1 - m), (140, 80, 40), 0.6)
        t.smooth -= m * 0.5
    weather(t, rust=1.0 if kind == "rust" else 0.3, streak=0.7, grime=0.3)
    return t


@lru_cache(maxsize=None)
def valley_pic():
    p = Pic(6, 1, VALLEY["cream"], 7200)
    p.rect(0, 8, p.W, 48, fill=VALLEY["main"])
    p.rect(0, 50, p.W, 54, fill=VALLEY["second"])
    p.text(p.W / 2, 10, "VALLEY  SERVICE", SERIF, 26, VALLEY["cream"], anchor="ma")
    return p


def valley(i):
    t = valley_pic().tile(i, 7205 + i, smooth=0.7, grime=0.15)
    t.f0[:] = 60
    weather(t, rust=0.8, streak=0.7, grime=0.3)
    return t


@lru_cache(maxsize=None)
def valley_sign_pic():
    """The round sign on its pole, two blocks across: red disc, cream ring, VALLEY across, a chevron."""
    p = Pic(2, 2, (0, 0, 0, 0), 7210)
    W = p.W
    p.ellipse(2, 2, W - 2, W - 2, fill=VALLEY["cream"])
    p.ellipse(8, 8, W - 8, W - 8, fill=VALLEY["main"])
    p.rect(6, W / 2 - 18, W - 6, W / 2 + 18, fill=VALLEY["cream"])
    p.text(W / 2, W / 2 - 15, "VALLEY", SERIF, 24, VALLEY["main"], anchor="ma")
    p.poly([(W / 2 - 22, 30), (W / 2, 50), (W / 2 + 22, 30), (W / 2 + 14, 30), (W / 2, 41), (W / 2 - 14, 30)], fill=VALLEY["cream"])
    p.text(W / 2, W - 40, "GASOLINE", SANS, 8, VALLEY["cream"], anchor="ma")
    return p


def valley_sign(i):
    t = valley_sign_pic().tile(i, 7215 + i, smooth=0.75, grime=0.2)
    t.f0[:] = 60
    a = t.alpha.copy()
    weather(t, rust=1.2, streak=0.6, grime=0.3, dents=2)
    t.alpha[:] = a
    return t


def bay_door(row):
    """The bay's sectional door, white steel, one row of windows: tile row 0 the top section."""
    t = F.plain(7220 + row, (214, 214, 206), 0.03, 0.5)
    area(t, 0, 0, N, 2, mul=0.7, dh=-0.2)
    for x in range(0, N, 32):
        area(t, x + 3, 8, x + 29, 28, mul=0.95, dh=0.12)
        area(t, x + 3, 36, x + 29, 56, mul=0.95, dh=0.12)
    if row == 1:
        for x in range(0, N, 32):
            area(t, x + 5, 10, x + 27, 26, (40, 46, 50), smooth=0.9)
            if t.rng.random() < 0.5:
                area(t, x + 7, 12, x + 25, 24, (30, 26, 22))  # cardboard behind the glass
    if row == 3:
        area(t, 0, 58, N, N, (40, 40, 40))
    weather(t, rust=0.8, streak=0.7, grime=0.35, dents=2)
    return t


def bay_floor(seed):
    t = slab(seed, "oil")
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.9)
    return t


def machine(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.5)
    weather(t, rust=0.8, streak=0.4, grime=0.4, dents=2)
    return t


def tyre_side(seed):
    t = F.plain(seed, (30, 30, 32), 0.05, 0.2)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.9 + 0.15 * (((xx + yy) // 3) % 2))[..., None]
    t.grime(0.2)
    return t


def oilcans_face(seed):
    t = F.plain(seed, (90, 70, 50), 0.04, 0.4)
    rng = t.rng
    for row, y in enumerate((4, 24, 44)):
        area(t, 0, y + 16, N, y + 18, (120, 90, 60))
        for x in range(2, N - 8, 8):
            if rng.random() < 0.7:
                col = ((200, 40, 30), (230, 190, 40), (30, 70, 150), (40, 120, 60))[rng.integers(4)]
                area(t, x, y + 4, x + 7, y + 16, col, h=0.85)
                area(t, x, y + 4, x + 7, y + 5, (190, 190, 190))
                area(t, x + 1, y + 8, x + 6, y + 12, (236, 230, 210))
    t.grime(0.35)
    return t


# ---------------------------------------------------------------- the registry

def textures():
    T = {}
    for b in BRANDS:
        T[f"gs_fascia_{b}"] = lambda b=b: fascia(b)
        for i in range(6):
            T[f"gs_fascia_{b}_name_{i}"] = lambda b=b, i=i: fascia(b, i)
            T[f"gs_fascia_{b}_name_{i}_on"] = lambda b=b, i=i: fascia(b, i, True)
        T[f"gs_column_{b}"] = lambda b=b: column(b)
        T[f"gs_column_{b}_band"] = lambda b=b: column(b, True)
        for part in ("lo", "hi"):
            T[f"gs_pump_{b}_{part}"] = lambda b=b, part=part: pump(b, part)
            T[f"gs_pump_{b}_{part}_smash"] = lambda b=b, part=part: pump(b, part, smash=True)
        T[f"gs_pump_{b}_hi_on"] = lambda b=b: pump(b, "hi", on=True)
        T[f"gs_pump_{b}_side"] = lambda b=b: pump_side(b)
        for i in range(15):
            T[f"gs_price_{b}_{i}"] = lambda b=b, i=i: price(b, i, False)
            T[f"gs_price_{b}_{i}_on"] = lambda b=b, i=i: price(b, i, True)
        T[f"gs_pylon_{b}"] = lambda b=b: pylon(b)
        for i in range(8):
            T[f"gs_store_{b}_{i}"] = lambda b=b, i=i: store(b, i, False)
            T[f"gs_store_{b}_{i}_on"] = lambda b=b, i=i: store(b, i, True)
        T[f"gs_frp_{b}"] = lambda b=b: frp(7020 + ord(b), b)
        T[f"gs_counter_{b}"] = lambda b=b: counter_front(b)
        T[f"gs_counter_{b}_candy"] = lambda b=b: counter_front(b, True)
    T["gs_soffit"] = lambda: soffit(6050)
    T["gs_soffit_lamp"] = lambda: soffit(6051, "lamp")
    T["gs_soffit_lamp_on"] = lambda: soffit(6052, "lamp_on")
    T["gs_soffit_smash"] = lambda: soffit(6053, "smash")
    T["gs_soffit_hole"] = lambda: soffit(6054, "hole")
    T["gs_canopy_roof"] = lambda: canopy_roof(6055)
    T["gs_guard"] = lambda: guard(6056)
    T["gs_island_top"] = lambda: island_top(6150)
    T["gs_island_nose"] = lambda: island_top(6151, nose=True)
    T["gs_island_side"] = lambda: island_side(6152)
    T["gs_squeegee"] = lambda: squeegee_box(6153)
    for part in ("lo", "hi"):
        T[f"gs_oldpump_{part}"] = lambda part=part: oldpump(part)
        T[f"gs_oldpump_{part}_rust"] = lambda part=part: oldpump(part, True)
    T["gs_oldpump_side"] = lambda: old_side()
    T["gs_oldpump_side_rust"] = lambda: old_side(True)
    T["gs_globe"] = lambda: globe(6330)
    T["gs_globe_broken"] = lambda: globe(6331, True)
    for kind in ("plain", "oil", "crack", "joint", "fill"):
        T["gs_slab" if kind == "plain" else f"gs_slab_{kind}"] = lambda kind=kind: slab(6440 + len(kind), kind)
    for n in range(len(POSTERS)):
        T[f"gs_front_poster_{n}"] = lambda n=n: storefront(6620 + n, "lo", poster=n)
        T[f"gs_poster_{n}"] = lambda n=n: poster_pic(n).tile(0, 6640 + n, smooth=0.4, grime=0.15)
    T["gs_front_lo"] = lambda: storefront(6610, "lo")
    T["gs_front_hi"] = lambda: storefront(6611, "hi")
    T["gs_front_broken_lo"] = lambda: storefront(6612, "lo", broken=True)
    T["gs_front_broken_hi"] = lambda: storefront(6613, "hi", broken=True)
    for i in range(4):
        T[f"gs_ice_{i}"] = lambda i=i: ice(i)
    T["gs_ice_side"] = lambda: weather(F.plain(6720, (232, 234, 232), 0.03, 0.6), rust=1.0, streak=0.6, grime=0.3, dents=2)
    T["gs_cage"] = lambda: cage(6730)
    T["gs_propane_sign"] = lambda: plate(6731, "PROPANE|EXCHANGE", (30, 70, 150), WHITE)
    T["gs_phone"] = lambda: phone_face(6740)
    T["gs_steel"] = lambda: weather(F.plain(6741, (170, 174, 176), 0.03, 0.7), rust=0.3, streak=0.6, grime=0.3, dents=1)
    for n in range(len(NEWS)):
        T[f"gs_news_{n}"] = lambda n=n: news_face(n)
        T[f"gs_news_body_{n}"] = lambda n=n: weather(F.plain(6810 + n, NEWS[n][1], 0.05, 0.5), rust=0.8, streak=0.6, grime=0.3, dents=1)
    T["gs_air"] = lambda: air_face(6820)
    T["gs_air_body"] = lambda: weather(F.plain(6821, (190, 36, 30), 0.05, 0.5), rust=0.8, streak=0.6, grime=0.3, dents=1)
    T["gs_vac"] = lambda: vac_face(6822)
    T["gs_vac_body"] = lambda: weather(F.plain(6823, (232, 190, 40), 0.05, 0.5), rust=0.8, streak=0.6, grime=0.3, dents=1)
    T["gs_vct"] = lambda: vct(6830)
    T["gs_vct_b"] = lambda: vct(6831, "b")
    T["gs_vct_mud"] = lambda: vct(6832, "mud")
    T["gs_frp"] = lambda: frp(6833)
    for state in ("full", "half", "empty", "smash"):
        for part in ("lo", "hi"):
            T[f"gs_cool_{state}_{part}"] = lambda state=state, part=part: cooler(state, part, False)
            T[f"gs_cool_{state}_{part}_on"] = lambda state=state, part=part: cooler(state, part, True)
    for i in range(4):
        T[f"gs_cool_head_{i}"] = lambda i=i: cool_head(i)
    T["gs_laminate"] = lambda: laminate(6990)
    T["gs_cig_lo"] = lambda: cig_wall(7030, True)
    T["gs_cig_hi"] = lambda: cig_wall(7031, False)
    T["gs_lotto"] = lambda: lotto_face(7032)
    T["gs_roller"] = lambda: roller_face(7040, False)
    T["gs_roller_on"] = lambda: roller_face(7041, True)
    T["gs_brewer"] = lambda: brewer_face(7042, False)
    T["gs_brewer_on"] = lambda: brewer_face(7043, True)
    T["gs_fountain"] = lambda: fountain_face(7044)
    T["gs_slush"] = lambda: slush_face(7045, False)
    T["gs_slush_on"] = lambda: slush_face(7046, True)
    for i in range(3):
        T[f"gs_menu_{i}"] = lambda i=i: menu(i)
    for kind in ("chips", "oil", "mags", "bread"):
        T[f"gs_rack_{kind}"] = lambda kind=kind: rack_face(7050 + len(kind), kind)
        T[f"gs_rack_{kind}_low"] = lambda kind=kind: rack_face(7060 + len(kind), kind, True)
    T["gs_freezer_top"] = lambda: freezer_top(7070)
    for k, (text, bg, ink) in {"restrooms": ("RESTROOMS|-->", (30, 70, 150), WHITE), "card": ("WE CARD|UNDER 21|NO SALE", WHITE, (190, 30, 30)),
                               "prepay": ("PLEASE PAY|BEFORE|PUMPING", (240, 200, 40), (20, 20, 20)), "hours": ("OPEN|24 HOURS", (190, 30, 30), WHITE),
                               "office": ("OFFICE|EMPLOYEES|ONLY", (40, 40, 44), WHITE)}.items():
        T[f"gs_sign_{k}"] = lambda k=k, text=text, bg=bg, ink=ink: plate(7080 + len(k), text, bg, ink)
    T["gs_neon_beer"] = lambda: neon(7090, "beer", False)
    T["gs_neon_beer_on"] = lambda: neon(7091, "beer", True)
    T["gs_neon_lotto"] = lambda: neon(7092, "lotto", False)
    T["gs_neon_lotto_on"] = lambda: neon(7093, "lotto", True)
    T["gs_mirror"] = lambda: mirror_dome(7095)
    T["gs_enamel"] = lambda: enamel(7230)
    T["gs_enamel_rust"] = lambda: enamel(7231, "rust")
    T["gs_enamel_band"] = lambda: enamel(7232, "band")
    for i in range(6):
        T[f"gs_valley_{i}"] = lambda i=i: valley(i)
    for i in range(4):
        T[f"gs_valley_sign_{i}"] = lambda i=i: valley_sign(i)
        T[f"gs_bay_{i}"] = lambda i=i: bay_door(i)
    T["gs_bay_floor"] = lambda: bay_floor(7240)
    T["gs_lift_red"] = lambda: machine(7241, (180, 40, 30))
    T["gs_machine_grey"] = lambda: machine(7242, (110, 116, 120))
    T["gs_machine_blue"] = lambda: machine(7243, (40, 70, 130))
    T["gs_tyre"] = lambda: tyre_side(7244)
    T["gs_oilcans"] = lambda: oilcans_face(7245)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
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

    black, chrome, rubber, steel, glass = "home_plastic_black", "home_chrome", "home_rubber", "gs_steel", "sc_glass"

    def cube(front, side, up=None, down=None, south=None):
        face = {"north": front, "side": side, "up": up or side, "down": down or side}
        if south:
            face["south"] = south
        return [[0, 0, 0, 16, 16, 16, face]]

    # ---- the canopy
    deck = {"up": "gs_canopy_roof", "down": "gs_soffit", "side": "gs_soffit"}
    for b in BRANDS:
        f = f"gs_fascia_{b}"
        add(f"gs_fascia_{b}", None, "Canopy", "METAL", rotate=None, tex={"side": f, "up": "gs_canopy_roof", "down": "gs_soffit"})
        for i in range(6):
            for s in ("", "_on"):
                add(f"gs_fascia_{b}_name_{i}{s}", cube(f"gs_fascia_{b}_name_{i}{s}", f, "gs_canopy_roof", "gs_soffit"), "Canopy", "METAL",
                    **({"light": 10} if s and i in (1, 4) else {}))
        add(f"gs_column_{b}", [[4, 0, 4, 12, 16, 12, f"gs_column_{b}"]], "Column", "METAL", rotate=None)
        add(f"gs_column_{b}_band", [[4, 0, 4, 12, 16, 12, f"gs_column_{b}_band"]], "Column", "METAL", rotate=None)
    add("gs_deck", None, "Canopy", "METAL", rotate=None, tex=deck)
    add("gs_deck_light", None, "Canopy", "METAL", rotate=None, tex=dict(deck, down="gs_soffit_lamp"))
    add("gs_deck_light_on", None, "Canopy", "METAL", rotate=None, tex=dict(deck, down="gs_soffit_lamp_on"), light=15)
    add("gs_deck_light_smash", [[0, 0, 0, 16, 16, 16, dict(deck, down="gs_soffit_smash")], [7.5, -6, 8, 8.5, 0, 9, black],
                                [6, -8, 7, 10, -6, 9, "gs_soffit_lamp"]], "Canopy", "METAL", rotate=None)
    add("gs_deck_hole", [[0, 12, 0, 16, 16, 16, dict(deck, down="gs_soffit_hole")], [0, 0, 5, 16, 12, 7, "painted_steel"],
                         [0, 0, 11, 16, 12, 13, "painted_steel"], [2, -10, 1, 6, 0, 1.6, "gs_soffit"]], "Canopy", "METAL", rotate=None)
    # ---- the islands (each piece carries the island's raised top, three pixels; the island runs along x)
    isl = [[0, 0, 0, 16, 3, 16, {"up": "gs_island_top", "side": "gs_island_side", "down": "floor_concrete"}]]
    for b in BRANDS:
        add(f"gs_column_base_{b}", isl + [[3, 3, 3, 13, 12, 13, "gs_guard"], [4, 12, 4, 12, 16, 12, f"gs_column_{b}"]], "Column", "METAL")
    add("gs_island", isl, "Pump Island", "STONE")
    add("gs_island_nose", [[0, 0, 2, 12, 3, 14, {"up": "gs_island_nose", "side": "gs_island_side", "down": "floor_concrete"}],
                           [12, 0, 4, 14, 3, 12, "gs_island_side"], [14, 0, 6, 15, 3, 10, "gs_island_side"],
                           [4, 3, 6, 8, 15, 10, "gs_guard"]], "Pump Island", "STONE")
    add("gs_squeegee", isl + [[7, 3, 7, 9, 18, 9, steel], [4, 18, 6, 12, 26, 10, "gs_squeegee"], [3, 3, 3, 9, 10, 9, "fit_yellow"],
                              [4.5, 9, 4.5, 7.5, 10, 7.5, "ext_glass_dark"], [6.5, 9, 5.5, 7.5, 16, 6.5, black]], "Squeegee Bucket", "METAL")
    add("gs_island_bin", isl + [[3, 3, 3, 13, 15, 13, steel], [2.5, 15, 2.5, 13.5, 17, 13.5, black]], "Trash Can", "METAL", loot="scraps")
    add("gs_bollard", [[5.5, 0, 5.5, 10.5, 14, 10.5, "gs_guard"], [6, 14, 6, 10, 15, 10, "gs_guard"]], "Bollard", "METAL", rotate=None)
    for b in BRANDS:
        side, face = f"gs_pump_{b}_side", "gs_pump_{b}_{p}"
        for suffix in ("", "_smash"):
            lo = f"gs_pump_{b}_lo{suffix}"
            hose = [] if suffix else [[2.5, 9, 3.4, 4, 16, 4.4, rubber], [2.5, 9, 11.6, 4, 16, 12.6, rubber]]
            add(lo, isl + [[1, 3, 4, 15, 16, 12, {"north": lo, "south": lo, "side": side, "up": side, "down": side}],
                           [0.5, 3, 3.6, 15.5, 4, 12.4, black]] + hose, "Gas Pump", "METAL")
            hi = f"gs_pump_{b}_hi{suffix}"
            add(hi, [[1, 0, 4, 15, 14, 12, {"north": hi, "south": hi, "side": side, "up": side, "down": side}],
                     [0.5, 14, 3.5, 15.5, 16, 12.5, side], [2.5, 0, 3.4, 4, 14, 4.4, rubber], [2.5, 0, 11.6, 4, 14, 12.6, rubber]],
                "Gas Pump", "METAL")
        hi = f"gs_pump_{b}_hi_on"
        add(hi, [[1, 0, 4, 15, 14, 12, {"north": hi, "south": hi, "side": side, "up": side, "down": side}],
                 [0.5, 14, 3.5, 15.5, 16, 12.5, side], [2.5, 0, 3.4, 4, 14, 4.4, rubber], [2.5, 0, 11.6, 4, 14, 12.6, rubber]],
            "Gas Pump", "METAL", light=4)
    for suffix in ("", "_rust"):
        side = f"gs_oldpump_side{suffix}"
        lo = f"gs_oldpump_lo{suffix}"
        add(lo, isl + [[3, 3, 4, 13, 16, 12, {"north": lo, "south": lo, "side": side, "up": side, "down": side}],
                       [2.5, 3, 3.5, 13.5, 4.5, 12.5, chrome]], "Old Gas Pump", "METAL")
        hi = f"gs_oldpump_hi{suffix}"
        g = "gs_globe" if not suffix else "gs_globe_broken"
        add(hi, [[3, 0, 4, 13, 12, 12, {"north": hi, "south": hi, "side": side, "up": side, "down": side}],
                 [2.5, 12, 3.5, 13.5, 13, 12.5, chrome], [5.5, 13, 6.5, 10.5, 16, 9.5, g], [4.5, 14, 5.5, 11.5, 15.5, 10.5, g],
                 [13, 4, 7, 14, 9, 9, black], [13, 0, 7.5, 13.6, 4, 8.5, rubber]], "Old Gas Pump", "METAL")
    add("gs_oldpump_hi_smash", [[3, 0, 4, 13, 12, 12, {"north": "gs_oldpump_hi_rust", "south": "gs_oldpump_hi_rust", "side": "gs_oldpump_side_rust",
                                                         "up": "gs_oldpump_side_rust", "down": "gs_oldpump_side_rust"}],
                                [2.5, 12, 3.5, 13.5, 13, 12.5, chrome], [7, 13, 7, 9, 14, 9, chrome]], "Old Gas Pump", "METAL")

    # ---- the price sign: the front shows tile i, the back the tile that sits there seen from behind
    for b in BRANDS:
        for i in range(15):
            r, c = divmod(i, 3)
            back = r * 3 + (2 - c)
            for s in ("", "_on"):
                face = {"north": f"gs_price_{b}_{i}{s}", "south": f"gs_price_{b}_{back}{s}", "side": "gs_steel", "up": "gs_steel", "down": "gs_steel"}
                add(f"gs_price_{b}_{i}{s}", [[0, 0, 5, 16, 16, 11, face]], "Price Sign", "METAL",
                    **({"light": 9} if s and c == 1 and r in (0, 3) else {}))
        add(f"gs_pylon_{b}", [[4, 0, 4, 12, 16, 12, f"gs_pylon_{b}"]], "Sign Post", "METAL", rotate=None)

    # ---- the forecourt
    for k in ("gs_slab", "gs_slab_oil", "gs_slab_crack", "gs_slab_fill"):
        add(k, None, "Concrete", "STONE", rotate=None, tex={"up": k, "side": "floor_concrete", "down": "floor_concrete"})
    add("gs_slab_j", cube("floor_concrete", "floor_concrete", up="gs_slab_joint"), "Concrete", "STONE")
    galv = "ext_galv"
    add("gs_vent_lo", [[3, 0, 6, 5, 16, 8, galv], [7, 0, 6, 9, 16, 8, galv], [11, 0, 6, 13, 16, 8, galv], [2, 2, 5, 14, 3, 9, galv]],
        "Vent Pipes", "METAL")
    add("gs_vent_hi", [[3, 0, 6, 5, 10, 8, galv], [7, 0, 6, 9, 12, 8, galv], [11, 0, 6, 13, 9, 8, galv], [2.5, 10, 5.5, 5.5, 12, 8.5, galv],
                       [6.5, 12, 5.5, 9.5, 14, 8.5, galv], [10.5, 9, 5.5, 13.5, 11, 8.5, galv]], "Vent Pipes", "METAL")

    # ---- the mart outside
    for b in BRANDS:
        f = f"gs_fascia_{b}"
        for i in range(8):
            for s in ("", "_on"):
                add(f"gs_store_{b}_{i}{s}", cube(f"gs_store_{b}_{i}{s}", f, "gs_canopy_roof", "gs_soffit"), "Store Sign", "METAL",
                    **({"light": 9} if s and i in (2, 5) else {}))
    frame = "pt_alu"
    for name in ["gs_front_lo", "gs_front_hi", "gs_front_broken_lo", "gs_front_broken_hi"] + [f"gs_front_poster_{n}" for n in range(len(POSTERS))]:
        add(name, [[0, 0, 7, 16, 16, 9, {"north": name, "south": name, "side": frame, "up": frame, "down": frame}]], "Storefront", "GLASS",
            rotate="facing", render="translucent")
    for i in range(4):
        r, c = divmod(i, 2)
        boxes = [[0, 0, 1, 16, 16, 16, {"north": f"gs_ice_{i}", "side": "gs_ice_side", "up": "gs_ice_side", "down": "gs_ice_side"}]]
        if r == 0:
            boxes = [[0, 0, 1, 16, 15, 16, {"north": f"gs_ice_{i}", "side": "gs_ice_side", "up": "gs_ice_side", "down": "gs_ice_side"}],
                     [0, 15, 0.5, 16, 16, 16, "gs_ice_side"]]
        add(f"gs_ice_{i}", boxes, "Ice Chest", "METAL", loot="drinks" if r == 1 else None)
    tank = "propane"
    for side in ("l", "r"):
        x0, x1 = (0, 16) if side == "l" else (0, 16)
        wall_x = [[0, 0, 0, 1, 16, 16, "gs_cage"]] if side == "l" else [[15, 0, 0, 16, 16, 16, "gs_cage"]]
        add(f"gs_propane_{side}", [[x0, 0, 0, 16, 0.5, 16, "ext_galv"], [x0, 0, 0, x1, 16, 1, "gs_cage"], [x0, 0, 15, x1, 16, 16, "gs_cage"],
                                   [x0, 15.5, 0, x1, 16, 16, "ext_galv"], [2, 10.5, -0.6, 14, 15, 0, "gs_propane_sign"]] + wall_x
            + [[2.5, 0.5, 3, 7.5, 9, 8, tank], [8.5, 0.5, 3, 13.5, 9, 8, tank], [5, 0.5, 9, 10, 9, 14, tank],
               [4, 9, 4.5, 6, 10.5, 6.5, steel], [10, 9, 4.5, 12, 10.5, 6.5, steel]],
            "Propane Cage", "METAL", render="cutout")
    add("gs_payphone", [[3, 0, 8, 13, 12, 16, {"north": "gs_phone", "side": steel, "up": steel, "down": steel}],
                        [2, 12, 6, 14, 14, 16, steel], [1.5, 0, 6, 2.5, 13, 16, steel], [13.5, 0, 6, 14.5, 13, 16, steel],
                        [5, 4, 7, 6, 8, 8, rubber]], "Payphone", "METAL", decor=True)
    add("gs_payphone_post", [[7, 0, 12, 9, 16, 14, steel], [6, 0, 11, 10, 1, 15, steel]], "Payphone", "METAL")
    for n in range(len(NEWS)):
        body = f"gs_news_body_{n}"
        add(f"gs_news_{n}", [[3, 0, 5, 13, 2, 13, black], [3.5, 2, 4.5, 12.5, 14, 13, {"north": f"gs_news_{n}", "side": body, "up": body, "down": body}],
                             [3.5, 14, 5, 12.5, 14.5, 13, body]], "Newspaper Box", "METAL", loot="mail" if n < 2 else None)
    add("gs_air", [[7, 0, 7, 9, 7, 9, steel], [4, 7, 5, 12, 16, 11, {"north": "gs_air", "side": "gs_air_body", "up": "gs_air_body", "down": "gs_air_body"}],
                   [12, 9, 6, 14, 13, 10, rubber], [3, 2, 7, 4, 9, 8, rubber]], "Air Machine", "METAL")
    add("gs_vac", [[4, 0, 4, 12, 16, 12, {"north": "gs_vac", "side": "gs_vac_body", "up": "gs_vac_body", "down": "gs_vac_body"}],
                   [12, 6, 7, 14, 15, 9, rubber], [13, 0, 7, 14.5, 6, 9, rubber]], "Vacuum", "METAL")

    # ---- the mart's walls: split-face block outside, the panel or the drywall inside (facing in)
    for b in BRANDS:
        add(f"gs_wall_{b}", cube(f"gs_frp_{b}", "sh_cmu_tan"), "Block Wall", "STONE")
    add("gs_wall_frp", cube("gs_frp", "sh_cmu_tan"), "Block Wall", "STONE")
    add("gs_wall_dry", cube("drywall", "sh_cmu_tan"), "Block Wall", "STONE")
    add("gs_wall_enamel", cube("drywall_b", "gs_enamel"), "Enamel Panel", "METAL")

    # ---- inside the mart
    for k in ("gs_vct", "gs_vct_b", "gs_vct_mud"):
        add(k, None, "Floor Tile", "STONE", rotate=None, tex={"up": k, "side": "floor_concrete", "down": "floor_concrete"})
    for k in ["gs_frp"] + [f"gs_frp_{b}" for b in BRANDS]:
        add(k, None, "Wall Panel", "STONE", rotate=None, tex={"all": k})
    for state in ("full", "half", "empty", "smash"):
        for part in ("lo", "hi"):
            for s in ("", "_on"):
                name = f"gs_cool_{state}_{part}{s}"
                add(name, cube(name, "store_appliance", "store_appliance", "store_appliance"), "Cooler", "GLASS",
                    loot="drinks" if part == "lo" and state != "empty" else None, **({"light": 8} if s and part == "hi" else {}))
    for i in range(4):
        add(f"gs_cool_head_{i}", cube(f"gs_cool_head_{i}", "store_appliance"), "Sign", "METAL", light=6 if i in (1, 2) else None)
    for b in BRANDS:
        top = "gs_laminate"
        add(f"gs_counter_{b}", [[0, 0, 1, 16, 15, 16, {"north": f"gs_counter_{b}", "side": f"gs_counter_{b}", "up": top}],
                                [0, 15, 0, 16, 16, 16, top]], "Counter", "WOOD", loot="storage")
        add(f"gs_counter_{b}_candy", [[0, 0, 1, 16, 15, 16, {"north": f"gs_counter_{b}_candy", "side": f"gs_counter_{b}", "up": top}],
                                      [0, 15, 0, 16, 16, 16, top]], "Counter", "WOOD", loot="theatre_candy")
    add("gs_lotto", [[2, 0, 4, 14, 9, 12, {"north": "gs_lotto", "south": "gs_lotto", "side": glass, "up": glass, "down": black}],
                     [1.5, 0, 3.5, 14.5, 0.6, 12.5, black]], "Lottery Case", "GLASS", decor=True, loot="till")
    add("gs_impulse", [[1, 0, 3, 6, 4, 8, "gs_rack_chips"], [1, 4, 3, 6, 6, 8, "gs_laminate"], [8, 0, 4, 12, 7, 8, "gs_rack_oil"],
                       [8.5, 7, 4.5, 11.5, 8, 7.5, black], [11, 0, 9, 15, 3, 13, "gs_counter_c_candy"], [3, 0, 10, 7, 5, 14, glass]],
        "Counter Display", "WOOL", decor=True, loot="theatre_candy")
    add("gs_cig_lo", cube("gs_cig_lo", "home_wood_dark", "gs_laminate"), "Tobacco Cabinet", "WOOD", loot="till")
    add("gs_cig_hi", [[0, 0, 4, 16, 16, 16, {"north": "gs_cig_hi", "side": "home_wood_dark", "up": "home_wood_dark", "down": "home_wood_dark"}]],
        "Tobacco Rack", "WOOD")
    fs = {"north": "fit_stainless", "side": "fit_stainless", "up": "fit_stainless"}
    add("gs_fs_cab", [[0, 0, 1, 16, 15, 16, {"north": "gs_counter_c", "side": "fit_stainless", "up": "fit_stainless"}], [0, 15, 0, 16, 16, 16, fs]],
        "Food Counter", "METAL", loot="kitchen")
    for s in ("", "_on"):
        add(f"gs_roller{s}", [[1, 0, 3, 15, 4, 13, {"north": steel, "side": steel, "up": f"gs_roller{s}"}], [1, 4, 3, 15, 10, 3.6, glass],
                              [1, 9.4, 3, 15, 10, 13, glass]], "Roller Grill", "METAL", **({"light": 5} if s else {}))
        add(f"gs_coffee{s}", [[1, 0, 7, 15, 14, 14, {"north": f"gs_brewer{s}", "side": black, "up": black}], [1, 14, 7, 15, 15, 15, black],
                              [2, 0, 3, 6, 5, 7, glass], [10, 0, 3, 14, 5, 7, glass]], "Coffee Maker", "METAL", **({"light": 3} if s else {}))
        add(f"gs_slush{s}", [[1, 0, 4, 15, 16, 14, {"north": f"gs_slush{s}", "side": "fit_stainless", "up": "fit_stainless"}]], "Slush Machine", "METAL",
            **({"light": 6} if s else {}))
    add("gs_fountain", [[1, 0, 5, 15, 16, 15, {"north": "gs_fountain", "side": black, "up": black}]], "Soda Fountain", "METAL", loot="drinks")
    add("gs_cups", [[1, 0, 9, 4, 14, 12, "ext_alu"], [5, 0, 9, 8, 12, 12, "ext_alu"], [9, 0, 9, 12, 10, 12, "ext_alu"],
                    [12.5, 0, 6, 15, 6, 12, black], [1.5, 14, 9.5, 3.5, 15, 11.5, "home_white"]], "Cups", "WOOL", decor=True)
    for i in range(3):
        add(f"gs_menu_{i}", [[0, 2, 14.5, 16, 14, 16, f"gs_menu_{i}"]], "Menu Board", "METAL", decor=True, light=5 if i == 1 else None)
    for kind, loot in (("chips", "theatre_candy"), ("oil", "hardware"), ("mags", "home_books"), ("bread", "grocery")):
        for low in ("", "_low"):
            rack = f"gs_rack_{kind}{low}"
            face = {"north": rack, "south": rack, "side": "home_steel", "up": "home_steel", "down": "home_steel"}
            add(f"gs_{kind}{low}", [[1, 0, 2, 15, 15, 14, face], [0.5, 0, 1.5, 1.5, 16, 14.5, "home_steel"], [14.5, 0, 1.5, 15.5, 16, 14.5, "home_steel"]],
                "Rack", "METAL", loot=loot if not low else None, render="cutout")
    add("gs_freezer", [[0, 0, 1, 16, 12, 15, {"north": "gs_ice_side", "side": "gs_ice_side", "up": "gs_freezer_top"}],
                       [0, 12, 1, 16, 12.5, 15, glass]], "Ice Cream Freezer", "METAL", loot="drinks")
    add("gs_mirror", [[3, 4, 12, 13, 14, 16, "gs_mirror"], [7, 12, 15, 9, 16, 16, steel]], "Security Mirror", "GLASS", decor=True)
    for k in ("restrooms", "card", "prepay", "hours", "office"):
        add(f"gs_sign_{k}", [[2, 4, 15.4, 14, 12, 16, f"gs_sign_{k}"]], "Sign", "METAL", decor=True)
    for n in range(len(POSTERS)):
        add(f"gs_poster_{n}", [[1, 1, 15.6, 15, 15, 16, f"gs_poster_{n}"]], "Poster", "WOOL", decor=True, render="cutout")
    for kind in ("beer", "lotto"):
        for s in ("", "_on"):
            add(f"gs_neon_{kind}{s}", [[0, 0, 0, 16, 16, 0.5, f"gs_neon_{kind}{s}"]], "Neon Sign", "GLASS", decor=True, render="cutout",
                **({"light": 7} if s else {}))

    # ---- the old station
    for k in ("gs_enamel", "gs_enamel_rust", "gs_enamel_band"):
        add(k, None, "Enamel Panel", "METAL", rotate=None, tex={"all": k})
    for i in range(6):
        add(f"gs_valley_{i}", cube(f"gs_valley_{i}", "gs_enamel_band", "gs_enamel"), "Sign", "METAL")
    for i in range(4):
        r, c = divmod(i, 2)
        back = r * 2 + (1 - c)
        add(f"gs_valley_sign_{i}", [[0, 0, 7, 16, 16, 9, {"north": f"gs_valley_sign_{i}", "south": f"gs_valley_sign_{back}", "side": "gs_enamel",
                                                         "up": "gs_enamel", "down": "gs_enamel"}]], "Sign", "METAL", render="cutout")
        add(f"gs_bay_{i}", [[0, 0, 6, 16, 16, 9, {"north": f"gs_bay_{i}", "south": f"gs_bay_{i}", "side": "gs_enamel", "up": "gs_enamel",
                                                 "down": "gs_enamel"}]], "Bay Door", "METAL")
    add("gs_bay_floor", None, "Concrete", "STONE", rotate=None, tex={"up": "gs_bay_floor", "side": "floor_concrete", "down": "floor_concrete"})
    add("gs_lift_post", [[5, 0, 5, 11, 1, 11, "gs_lift_red"], [6, 1, 6, 10, 16, 10, chrome]], "Car Lift", "METAL", rotate=None)
    add("gs_lift_arms", [[5.5, 0, 5.5, 10.5, 2, 10.5, "gs_lift_red"], [-6, 1, 6.5, 22, 2.5, 9.5, "gs_lift_red"],
                         [6.5, 1, -6, 9.5, 2.5, 22, "gs_lift_red"], [-6, 2.5, 6, -3, 3.5, 10, rubber], [19, 2.5, 6, 22, 3.5, 10, rubber]],
        "Car Lift", "METAL")
    add("gs_tirechanger", [[3, 0, 3, 13, 9, 13, "gs_lift_red"], [2, 9, 2, 14, 10, 14, steel], [6, 10, 6, 10, 12, 10, steel],
                           [11, 9, 7, 13, 16, 9, "gs_lift_red"], [9, 14, 7, 11, 15.5, 9, steel], [1, 0, 5, 3, 1, 7, black]],
        "Tire Changer", "METAL")
    add("gs_balancer", [[3, 0, 4, 13, 8, 12, "gs_machine_blue"], [13, 6, 6.5, 15, 8, 9.5, steel], [13, 3, 3, 16, 13, 13, "gs_tyre"],
                        [4, 8, 9, 12, 14, 12, "gs_machine_blue"]], "Wheel Balancer", "METAL")
    add("gs_compressor_lo", [[3, 0, 3, 13, 16, 13, "gs_machine_grey"], [2, 0, 2, 4, 2, 4, black], [12, 0, 12, 14, 2, 14, black]],
        "Air Compressor", "METAL")
    add("gs_compressor_hi", [[3, 0, 3, 13, 4, 13, "gs_machine_grey"], [4, 4, 5, 10, 10, 11, "gs_machine_blue"], [10, 5, 6, 14, 9, 10, steel],
                             [11, 4, 4, 13, 8, 6, black]], "Air Compressor", "METAL")
    t = "gs_tyre"
    add("gs_tirerack", [[0, 0, 6, 1, 16, 10, steel], [15, 0, 6, 16, 16, 10, steel], [0, 7, 6, 16, 8, 10, steel], [0, 15, 6, 16, 16, 10, steel],
                        [1.5, 0, 3, 5, 7, 13, t], [6, 0, 3, 9.5, 7, 13, t], [10.5, 0, 3, 14, 7, 13, t], [2, 8, 3, 5.5, 15, 13, t],
                        [9, 8, 3, 12.5, 15, 13, t]], "Tire Rack", "METAL")
    add("gs_oilcans", [[0, 0, 6, 16, 16, 16, {"north": "gs_oilcans", "side": "home_wood_dark", "up": "home_wood_dark", "down": "home_wood_dark"}]],
        "Oil Display", "METAL", loot="hardware")
    add("gs_hosereel", [[4, 4, 12, 12, 12, 16, "gs_lift_red"], [3, 3, 10, 13, 13, 12, rubber], [7, 0, 11, 9, 4, 12, rubber]], "Hose Reel",
        "METAL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("gs_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} gas station blocks in {path}")


if __name__ == "__main__":
    main()
