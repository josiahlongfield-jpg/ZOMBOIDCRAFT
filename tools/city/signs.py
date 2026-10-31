#!/usr/bin/env python3
"""Shop signs for the city: made-up brands on lightbox fascias, painted boards and loose metal
letters, plus neon blade signs that hang off the walk-ups and OPEN signs in shop windows.

Each sign is one block tall and several wide (blades: one wide, a letter per block), drawn
whole and cut into 64px slices, one block per slice. Every sign is old: faded, streaked by
rain, smashed in places, letters missing or hanging; neon comes lit and dead.

    python3 tools/city/signs.py            rewrites the sign entries in blocks.json

photo_textures.py draws the slices (signs.textures()); gen_blocks.py and mesh.py read the
blocks from blocks.json like any other. Slice 0 is the left end as you read it.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402

N = P.N
SS = 4  # supersampling for lettering
HERE = Path(__file__).parent
FONTS = {
    "sans": "/usr/share/fonts/opentype/inter/InterDisplay-ExtraBold.otf",
    "black": "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf",
    "serif": "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf",
    "tube": "/usr/share/fonts/opentype/inter/Inter-Regular.otf",
}

# id, style, text, face colour, letter colour, width in blocks, font
FASCIA = [
    ("medline", "box", "MEDLINE PHARMACY", (214, 216, 208), (168, 30, 34), 5, "sans"),
    ("stopnsave", "box", "STOP N SAVE", (226, 178, 46), (34, 32, 30), 4, "black"),
    ("suds", "box", "SUDS LAUNDROMAT", (44, 98, 150), (232, 232, 226), 5, "sans"),
    ("patriot", "box", "PATRIOT GUNS", (30, 38, 64), (196, 44, 40), 4, "black"),
    ("cash", "box", "CHECKS CASHED", (232, 230, 216), (30, 96, 46), 4, "black"),
    ("joes", "painted", "BIG JOE'S DINER", (116, 34, 30), (226, 208, 166), 5, "serif"),
    ("luckypawn", "painted", "LUCKY PAWN & GOLD", (28, 44, 34), (204, 166, 72), 5, "serif"),
    ("tonys", "painted", "TONY'S PIZZA", (34, 86, 44), (234, 228, 210), 4, "serif"),
    ("thrift", "painted", "SECOND CHANCE THRIFT", (82, 58, 98), (230, 210, 118), 6, "serif"),
    ("riverside", "channel", "RIVERSIDE LIQUOR", None, (176, 44, 34), 5, "black"),
    ("kessler", "channel", "KESSLER HARDWARE", None, (214, 208, 190), 5, "sans"),
    # out in the county: the quarantine, roadside stops, farms, the school, the port
    ("quarantine", "box", "QUARANTINE ZONE", (214, 180, 40), (24, 24, 22), 5, "black"),
    ("restricted", "box", "RESTRICTED AREA - KEEP OUT", (178, 36, 32), (236, 232, 220), 7, "black"),
    ("armydepot", "box", "US ARMY SUPPLY DEPOT", (58, 68, 46), (230, 226, 210), 6, "black"),
    ("motel", "painted", "SLEEPY PINES MOTEL", (40, 70, 58), (230, 214, 160), 5, "serif"),
    ("fuel", "box", "GAS - FOOD - BAIT", (196, 42, 34), (236, 232, 222), 5, "black"),
    ("route9", "painted", "ROUTE 9 DINER", (150, 40, 36), (236, 222, 180), 4, "serif"),
    ("feed", "painted", "KNOX FEED & SEED", (38, 72, 44), (228, 214, 170), 5, "serif"),
    ("school", "channel", "KNOX COUNTY HIGH SCHOOL", None, (210, 206, 190), 7, "sans"),
    ("church", "painted", "FIRST BAPTIST CHURCH", (226, 222, 208), (40, 42, 60), 5, "serif"),
    ("terminal", "channel", "LOUISVILLE MARINE TERMINAL", None, (190, 196, 200), 7, "sans"),
    ("bait", "painted", "BAIT & TACKLE", (56, 88, 108), (236, 228, 200), 4, "serif"),
    ("grain", "painted", "RIVERBEND GRAIN CO.", (120, 36, 30), (234, 226, 206), 5, "serif"),
    ("storage", "box", "COUNTY SELF STORAGE", (226, 186, 52), (30, 30, 28), 5, "black"),
    ("shadyacres", "painted", "SHADY ACRES MOBILE HOMES", (64, 88, 52), (232, 224, 190), 6, "serif"),
    ("sheriff", "box", "COUNTY SHERIFF", (30, 40, 70), (230, 226, 210), 4, "black"),
    # the small towns: main street shops, the courthouse square, the mill
    ("grocery", "box", "GROCERY", (172, 32, 28), (240, 234, 222), 4, "black"),
    ("bank", "channel", "FIRST NATIONAL BANK", None, (204, 194, 164), 6, "serif"),
    ("postoffice", "channel", "U.S. POST OFFICE", None, (200, 196, 184), 5, "sans"),
    ("courthouse", "channel", "KNOX COUNTY COURTHOUSE", None, (210, 204, 188), 7, "serif"),
    ("library", "channel", "PUBLIC LIBRARY", None, (204, 198, 182), 5, "serif"),
    ("firedept", "box", "FIRE DEPT.", (150, 32, 28), (238, 232, 216), 4, "black"),
    ("rialto", "box", "RIALTO", (232, 200, 96), (128, 26, 22), 3, "black"),
    ("hotel", "painted", "COMMERCIAL HOTEL", (44, 52, 70), (226, 210, 160), 5, "serif"),
    ("tavern", "painted", "RUSTY NAIL TAVERN", (64, 38, 28), (224, 196, 140), 5, "serif"),
    ("barber", "painted", "BARBER SHOP", (232, 228, 216), (150, 30, 30), 4, "serif"),
    ("drugstore", "box", "CORNER DRUG STORE", (222, 220, 208), (36, 70, 130), 5, "sans"),
    ("cafe", "painted", "MAIN ST. CAFE", (34, 70, 76), (232, 220, 180), 4, "serif"),
    ("dime", "painted", "BEN'S 5 & 10", (150, 36, 32), (236, 210, 120), 4, "serif"),
    ("furniture", "painted", "HOME FURNITURE", (72, 56, 40), (230, 214, 170), 5, "serif"),
    ("autoparts", "box", "AUTO PARTS", (36, 60, 120), (238, 200, 60), 4, "black"),
    ("townhall", "channel", "TOWN HALL", None, (212, 206, 188), 4, "serif"),
    ("mill", "painted", "KNOX TEXTILE MILLS", (120, 36, 30), (232, 222, 196), 6, "serif"),
    ("company", "painted", "COMPANY STORE", (52, 64, 48), (230, 216, 170), 5, "serif"),
    ("elementary", "channel", "KNOX ELEMENTARY SCHOOL", None, (212, 208, 192), 7, "sans"),
    ("methodist", "painted", "UNITED METHODIST CHURCH", (232, 228, 216), (60, 40, 30), 6, "serif"),
    ("stmary", "channel", "ST. MARY'S CHURCH", None, (204, 198, 182), 5, "serif"),
    ("generalstore", "painted", "GENERAL STORE", (226, 216, 186), (110, 30, 26), 5, "serif"),
    ("antiques", "painted", "ANTIQUES", (40, 54, 70), (230, 214, 160), 3, "serif"),
    ("realty", "box", "KNOX REALTY", (232, 230, 220), (30, 90, 60), 4, "sans"),
    ("vfw", "box", "VFW POST 1186", (32, 46, 90), (230, 224, 206), 4, "black"),
    # the farms: the name on the barn or the gate, the stand by the road, the field signs
    ("hollowcreek", "painted", "HOLLOW CREEK FARM", (120, 36, 30), (234, 226, 206), 6, "serif"),
    ("dairy", "painted", "MAPLE RIDGE DAIRY", (232, 228, 216), (40, 64, 44), 6, "serif"),
    ("ranch", "painted", "BAR K RANCH", (72, 52, 36), (232, 214, 170), 4, "serif"),
    ("orchard", "painted", "MILLER ORCHARDS", (52, 80, 46), (236, 226, 190), 5, "serif"),
    ("apples", "painted", "U-PICK APPLES", (150, 36, 30), (236, 226, 200), 5, "serif"),
    ("produce", "painted", "FRESH PRODUCE", (52, 80, 46), (236, 226, 190), 5, "serif"),
    ("eggs", "painted", "FARM EGGS", (232, 228, 216), (150, 40, 30), 4, "serif"),
    ("trespass", "box", "NO TRESPASSING", (196, 42, 34), (236, 232, 222), 4, "black"),
    ("seedcorn", "box", "KNOX HYBRID SEED", (238, 214, 60), (30, 70, 40), 5, "black"),
    # the big city: the hospital, police headquarters, the subway, the garage, the aid station
    ("emergency", "box", "EMERGENCY", (238, 236, 230), (188, 26, 28), 5, "black"),
    ("mercy", "channel", "MERCY GENERAL HOSPITAL", None, (228, 230, 224), 7, "sans"),
    ("police", "box", "POLICE", (24, 42, 104), (240, 240, 234), 4, "black"),
    ("precinct", "channel", "CENTRAL PRECINCT", None, (214, 210, 196), 6, "serif"),
    ("subway", "box", "SUBWAY", (30, 90, 54), (240, 240, 230), 3, "black"),
    ("central", "painted", "CENTRAL STATION", (26, 28, 32), (228, 226, 216), 5, "sans"),
    ("parking", "box", "PUBLIC PARKING", (28, 64, 150), (242, 242, 236), 5, "black"),
    ("aid", "box", "FEMA AID STATION", (30, 50, 92), (236, 232, 220), 5, "black"),
    ("hardhat", "painted", "HARD HAT AREA - KEEP OUT", (232, 188, 40), (26, 26, 24), 6, "black"),
    # Seabright's church, where Father Ruiz keeps his ham radio (AUDIO's broadcasts)
    ("ourlady", "channel", "OUR LADY OF THE SEA", None, (204, 198, 182), 6, "serif"),
]
# hanging neon, a letter per block: id, letters, tube colour. Colours keep to the three a
# froglight can throw under Photon (the city's Signs/Boards put the matching one over a lit
# blade): pink-violet (pearlescent), green (verdant), amber (ochre)
BLADES = [
    ("hotel", "HOTEL", (255, 80, 170)),
    ("bar", "BAR", (190, 110, 255)),
    ("pawn", "PAWN", (255, 176, 60)),
    ("diner", "DINER", (255, 90, 190)),
    ("motel", "MOTEL", (255, 80, 170)),
    ("eat", "EAT", (255, 170, 60)),
    ("rialto", "RIALTO", (255, 196, 80)),
    ("drugs", "DRUGS", (90, 230, 130)),
    ("cafe", "CAFE", (255, 120, 60)),
    ("guns", "GUNS", (255, 150, 50)),
    ("wash", "WASH", (110, 235, 160)),
    ("liquor", "LIQUOR", (200, 100, 255)),
    ("pizza", "PIZZA", (255, 160, 60)),
    ("cash", "CASH", (100, 235, 120)),
]
OPEN_TUBE, OPEN_RIM = (255, 60, 50), (60, 130, 255)


# ---------------------------------------------------------------- helpers

def noise(rng, w, h, cells):
    """Smooth value noise in [-1, 1], w x h, cells across the width."""
    g = rng.random((max(2, int(cells * h / w) + 2), cells + 2)).astype(np.float32)
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w + w // cells * 2, h + w // cells * 2), Image.BICUBIC)
    a = np.asarray(img, np.float32)[w // cells: w // cells + h, w // cells: w // cells + w] / 127.5 - 1
    return a


def fbm(rng, w, h, octaves=((4, 1.0), (8, 0.5), (16, 0.25), (48, 0.12))):
    total = sum(a for _, a in octaves)
    return sum(noise(rng, w, h, c * w // N // 2 + c) * a for c, a in octaves) / total


def font_for(kind, size):
    return ImageFont.truetype(FONTS[kind], size)


def lettering(text, kind, w, h, fill=0.62, pad=0.06, spacing=0.06):
    """A mask (h x w, 0..1) of the text fitted into the board, plus each glyph's box."""
    W, H = w * SS, h * SS
    size = int(H * fill * 1.35)
    while True:
        f = font_for(kind, size)
        widths = [f.getlength(ch) for ch in text]
        gap = size * spacing
        total = sum(widths) + gap * (len(text) - 1)
        asc, desc = f.getmetrics()
        if total <= W * (1 - 2 * pad) and f.getbbox("H")[3] - f.getbbox("H")[1] <= H * fill:
            break
        size -= 2
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    cap = f.getbbox("H")
    y = (H - (cap[3] - cap[1])) / 2 - cap[1]
    x = (W - total) / 2
    boxes = []
    for ch, cw in zip(text, widths):
        d.text((x, y), ch, font=f, fill=255)
        boxes.append((ch, x / SS, (x + cw) / SS))
        x += cw + gap
    mask = np.asarray(img.resize((w, h), Image.BOX), np.float32) / 255
    return mask, boxes


class Wide:
    """A Tex-like canvas k blocks wide, cut into Tex slices at the end."""

    def __init__(self, seed, k, base, h=N):
        self.rng = np.random.default_rng(seed)
        self.w, self.hgt = k * N, h
        self.c = np.zeros((h, self.w, 3), np.float32) + np.array(base, np.float32)
        self.h = np.full((h, self.w), 0.75, np.float32)
        self.smooth = np.full((h, self.w), 0.25, np.float32)
        self.f0 = np.full((h, self.w), 10.0, np.float32)
        self.poro = np.full((h, self.w), 30.0, np.float32)
        self.emit = np.zeros((h, self.w), np.float32)
        self.alpha = np.full((h, self.w), 255.0, np.float32)

    def mix(self, mask, col, strength=1.0):
        m = np.clip(mask, 0, 1)[..., None] * strength
        self.c = self.c * (1 - m) + np.array(col, np.float32) * m

    def streaks(self, amount, dark=0.7):
        """Rain runs down from the top edge and from under each letter."""
        cols = np.clip(noise(self.rng, self.w, 1, self.w // 3)[0] * 0.5 + 0.5, 0, 1) ** 3
        cols = np.where(self.rng.random(self.w) < 0.3, cols + 0.4, cols)
        length = self.rng.random(self.w) * 0.9 + 0.1
        y = np.arange(self.hgt)[:, None] / self.hgt
        s = cols[None, :] * np.clip(1 - y / length[None, :], 0, 1)
        s = np.asarray(Image.fromarray((np.clip(s * amount, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255
        self.c *= (1 - (1 - dark) * s)[..., None]
        self.smooth -= 0.1 * s

    def grime(self, amount, col=(36, 32, 28)):
        g = np.clip(fbm(self.rng, self.w, self.hgt) * 1.4 + 0.15, 0, 1) * amount
        # dirt collects along the bottom edge
        g = np.maximum(g, np.linspace(0, 1, self.hgt)[:, None] ** 4 * amount * 1.2)
        self.mix(g, col, 0.65)
        self.smooth -= g * 0.15

    def speckle(self, chance, col, strength=0.5):
        m = self.rng.random((self.hgt, self.w)) < chance
        self.c[m] = self.c[m] * (1 - strength) + np.array(col, np.float32) * strength

    def slices(self, seed):
        out = []
        for i in range(self.w // N):
            t = P.Tex(seed + i, (0, 0, 0))
            sl = slice(i * N, (i + 1) * N)
            t.c = self.c[:, sl].copy()
            t.h = self.h[:, sl].copy()
            t.smooth = np.clip(self.smooth[:, sl], 0, 1)
            t.f0 = self.f0[:, sl].copy()
            t.poro = self.poro[:, sl].copy()
            t.emit = self.emit[:, sl].copy()
            t.alpha = self.alpha[:, sl].copy()
            out.append(t)
        return out


def blob(rng, w, h, cx, cy, r):
    """A ragged hole: a circle pushed in and out by noise."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ang = np.arctan2(yy - cy, xx - cx)
    wob = 1 + 0.35 * np.sin(ang * 3 + rng.uniform(0, 6)) + 0.2 * np.sin(ang * 7 + rng.uniform(0, 6))
    return np.hypot(xx - cx, yy - cy) < r * wob


def cracks(t, rng, cx, cy, n, length, col=(200, 204, 206), mask=None):
    for _ in range(n):
        a = rng.uniform(0, np.pi * 2)
        x, y = cx, cy
        for _ in range(int(length * rng.uniform(0.5, 1.2))):
            xi, yi = int(x), int(y)
            if not (0 <= xi < t.w and 0 <= yi < t.hgt) or (mask is not None and mask[yi, xi]):
                break
            t.c[yi, xi] = t.c[yi, xi] * 0.4 + np.array(col) * 0.6
            t.h[yi, xi] -= 0.15
            a += rng.normal(0, 0.25)
            x += np.cos(a)
            y += np.sin(a)


# ---------------------------------------------------------------- fascia styles

def box_sign(sid, text, face, ink, k, font, seed):
    """Backlit plastic lightbox, long dead: bronze frame, sun-faded face, one panel smashed in."""
    t = Wide(seed, k, face)
    t.c *= np.linspace(1.04, 0.92, N)[:, None, None]  # the face bows and yellows toward the bottom
    t.mix(np.clip(fbm(t.rng, t.w, N) * 0.8 + 0.3, 0, 1), (180, 160, 110), 0.18)
    t.smooth[:] = 0.55
    mask, _ = lettering(text, font, t.w, N, fill=0.5)
    t.mix(mask, ink, 0.86)  # faded but readable
    t.h += mask * 0.06
    # bronze frame top and bottom, end caps at either end
    frame = np.zeros((N, t.w), bool)
    frame[:5], frame[-5:] = True, True
    frame[:, :4], frame[:, -4:] = True, True
    t.c[frame] = (74, 64, 52)
    t.c[frame] *= (0.85 + 0.3 * t.rng.random(frame.sum()))[:, None]
    t.h[frame] = 0.95
    t.smooth[frame], t.f0[frame] = 0.45, 200
    t.c[5, 4:-4] *= 0.55  # shadow under the top rail
    t.streaks(0.9, 0.62)
    t.grime(0.55)
    t.speckle(0.02, (30, 28, 24), 0.5)
    # one panel smashed in: dark inside, a broken fluorescent tube, cracks out to the frame
    x = t.rng.uniform(N * 0.6, t.w - N * 0.6)
    hole = blob(t.rng, t.w, N, x, t.rng.uniform(24, 40), t.rng.uniform(9, 15)) & ~frame
    inside = np.array((22, 21, 20), np.float32)
    t.c[hole] = inside * (0.7 + 0.6 * t.rng.random((hole.sum(), 1)))
    tube = hole & (np.abs(np.mgrid[0:N, 0:t.w][0] - 30) < 2)
    t.c[tube] = (112, 114, 110)
    t.h[hole], t.smooth[hole] = 0.35, 0.1
    cracks(t, t.rng, x, 32, 10, 30, mask=hole)
    return t


def painted_sign(sid, text, face, ink, k, font, seed):
    """Hand-painted board on planks: shadowed serif letters, paint peeling back to grey wood."""
    t = Wide(seed, k, face)
    # planks: three boards with dark joints and grain
    grain = noise(t.rng, t.w, N, t.w // 6) * 0.5 + noise(t.rng, t.w, N, t.w // 2) * 0.5
    t.c *= (1 + 0.07 * grain)[..., None]
    for y in (21, 42):
        t.c[y] *= 0.45
        t.h[y] = 0.5
    t.smooth[:] = 0.3
    mask, _ = lettering(text, font, t.w, N, fill=0.56)
    shadow = np.roll(np.roll(mask, 2, 0), 2, 1)
    t.mix(shadow, (20, 18, 16), 0.7)
    t.mix(mask, ink, 0.9)
    # a thin border line inset from the edge
    border = np.zeros((N, t.w), bool)
    border[4:6, 5:-5] = border[-6:-4, 5:-5] = True
    border[4:-4, 5:7] = border[4:-4, -7:-5] = True
    t.mix(border.astype(np.float32), ink, 0.75)
    # sun and rain fade it all toward chalky grey
    t.mix(np.full((N, t.w), 1.0), (150, 146, 136), 0.22)
    # peeling: patches back to bare weathered wood, edges lifted
    peel = fbm(t.rng, t.w, N, ((6, 1.0), (14, 0.6), (40, 0.3))) > 0.28
    wood = np.array((104, 96, 84), np.float32)
    t.c[peel] = wood * (0.8 + 0.3 * t.rng.random((peel.sum(), 1)))
    t.h[peel] -= 0.12
    edge = peel & ~np.roll(peel, 1, 0)
    t.c[edge] *= 1.3
    t.streaks(0.8, 0.6)
    t.grime(0.5)
    # rust running from the nails at each end
    for x in (8, t.w - 9):
        for y in (10, 52):
            t.c[y - 1:y + 2, x - 1:x + 2] = (60, 40, 30)
            run = np.arange(y + 2, min(N, y + 2 + int(t.rng.uniform(6, 16))))
            t.c[run, x] = t.c[run, x] * 0.5 + np.array((110, 60, 30)) * 0.5
    return t


def channel_sign(sid, text, face, ink, k, font, seed):
    """Loose metal letters bolted to the wall: rusting, one fallen off, one hanging askew."""
    t = Wide(seed, k, (0, 0, 0))
    t.alpha[:] = 0
    mask, boxes = lettering(text, font, t.w, N, fill=0.6)
    letters = [i for i, (ch, _, _) in enumerate(boxes) if ch.strip()]
    gone = {int(t.rng.choice(letters))}
    if P.rude_gaps(text, gone):
        gone = set()
    tilt = int(t.rng.choice([i for i in letters if i not in gone]))
    keep = np.zeros_like(mask)
    for i, (ch, x0, x1) in enumerate(boxes):
        if i in gone:
            continue
        a, b = int(np.floor(x0)) - 1, int(np.ceil(x1)) + 1
        part = np.zeros_like(mask)
        part[:, a:b] = mask[:, a:b]
        if i == tilt:
            # hanging by one bolt: swung round about its top left corner
            img = Image.fromarray((part * 255).astype(np.uint8)).rotate(-14, resample=Image.BILINEAR, center=(a + 2, 18))
            part = np.asarray(img, np.float32) / 255
        keep = np.maximum(keep, part)
    m = keep > 0.45
    t.alpha[m] = 255
    t.c[:] = ink
    # each letter: a lit face and darker return edges, rust creeping in from the bottom
    edge = m & ~(np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
    t.c[edge] *= 0.55
    rust = (fbm(t.rng, t.w, N) + np.linspace(-0.4, 0.4, N)[:, None]) > 0.32
    t.mix((rust & m).astype(np.float32), (112, 62, 34), 0.55)
    t.c *= (0.88 + 0.2 * t.rng.random((N, t.w)))[..., None]
    t.smooth[:], t.f0[:] = 0.35, 180
    t.h[m] = 0.95
    return t


# ---------------------------------------------------------------- neon

def tube_mask(letter, size_px, w, h, weight=None):
    img = Image.new("L", (w * SS, h * SS), 0)
    d = ImageDraw.Draw(img)
    f = font_for("tube", int(size_px * SS * 1.38))
    bb = f.getbbox(letter)
    d.text(((w * SS - (bb[2] - bb[0])) / 2 - bb[0], (h * SS - (bb[3] - bb[1])) / 2 - bb[1]), letter, font=f, fill=255)
    return np.asarray(img.resize((w, h), Image.BOX), np.float32) / 255


def glow(mask, r):
    img = Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8))
    return np.asarray(img.filter(ImageFilter.GaussianBlur(r)), np.float32) / 255


def neon_tubes(t, tubes, colour, lit, rng, halo=True):
    """Glass tubes on the canvas: bright cores and a glow on the board when lit, dull glass when
    dead (a few segments snapped out)."""
    core = tubes > 0.5
    if lit:
        if halo:
            g = glow(tubes, 5) * 0.9
            t.c = t.c + np.array(colour, np.float32) * g[..., None] * 0.55
            t.emit = np.maximum(t.emit, g * 0.35)
        hot = np.array(colour, np.float32) * 0.6 + 255 * 0.4
        t.c[core] = hot
        t.emit[core] = 1.0
        t.smooth[core] = 0.9
    else:
        broken = core & (noise(rng, t.w, t.hgt, 6) > 0.55)
        dull = np.array(colour, np.float32) * 0.35 + 70
        t.c[core & ~broken] = dull
        t.c[core & ~broken] *= (0.8 + 0.4 * rng.random(((core & ~broken).sum(), 1)))
        t.smooth[core] = 0.85
        if t.alpha.min() < 255:
            t.alpha[broken] = 0
    t.h[core] = 0.95


def blade_letter(bid, letters, i, colour, lit, seed):
    """One block of a hanging neon blade: a letter in tube on a dark tin box with a tube border."""
    t = Wide(seed, 1, (40, 40, 44))
    t.c *= (1 + 0.08 * fbm(t.rng, N, N))[..., None]
    t.smooth[:], t.f0[:] = 0.35, 120
    # border trim painted in the tube colour, faded, with rivets
    n = len(letters)
    edge = np.zeros((N, N), bool)
    edge[:, :4] = edge[:, -4:] = True
    if i == 0:
        edge[:4] = True
    if i == n - 1:
        edge[-4:] = True
    t.c[edge] = np.array(colour, np.float32) * 0.35 + 30
    t.h[edge] = 0.95
    for y in range(6, N, 14):
        t.c[y, 1:3] = t.c[y, -3:-1] = (150, 150, 150)
    tubes = tube_mask(letters[i], 40, N, N)
    border = np.zeros((N, N), np.float32)
    border[:, 7:9] = border[:, -9:-7] = 1
    if i == 0:
        border[7:9, 7:-7] = 1
    if i == n - 1:
        border[-9:-7, 7:-7] = 1
    tubes = np.maximum(tubes, border)
    t.streaks(0.7, 0.6)
    t.grime(0.4)
    neon_tubes(t, tubes, colour, lit, t.rng)
    return t.slices(seed)[0]


def open_sign(i, lit, seed):
    """The OPEN sign in the shop window, two blocks wide, hanging on its chains."""
    t = Wide(seed, 2, (30, 30, 30))
    t.alpha[:] = 0
    W = t.w
    mask, _ = lettering("OPEN", "tube", W, N, fill=0.42, pad=0.18, spacing=0.1)
    tubes = mask
    rim = np.zeros((N, W), np.float32)
    rim[14:16, 10:-10] = rim[-16:-14, 10:-10] = 1
    rim[14:-14, 10:12] = rim[14:-14, -12:-10] = 1
    t.alpha[(tubes > 0.5) | (rim > 0.5)] = 255
    # chains up to the window head
    for x in (16, W - 17):
        t.c[0:14, x] = (90, 90, 90)
        t.alpha[0:14:2, x] = 255
    neon_tubes(t, tubes, OPEN_TUBE, lit, t.rng, halo=False)
    neon_tubes(t, rim, OPEN_RIM, lit, t.rng, halo=False)
    return t.slices(seed)[i]


# ---------------------------------------------------------------- registry

STYLES = {"box": box_sign, "painted": painted_sign, "channel": channel_sign}


def _fascia(entry, i):
    sid, style, text, face, ink, k, font = entry
    seed = 4000 + sum(map(ord, sid)) * 7
    return STYLES[style](sid, text, face, ink, k, font, seed).slices(seed)[i]


def textures():
    """Texture name -> builder, for photo_textures.py."""
    out = {}
    for entry in FASCIA:
        for i in range(entry[5]):
            out[f"sign_{entry[0]}_{i}"] = (lambda e=entry, i=i: _fascia(e, i))
    for bid, letters, colour in BLADES:
        for i in range(len(letters)):
            for lit in (True, False):
                seed = 5000 + sum(map(ord, bid)) * 11 + i
                out[f"blade_{bid}_{i}{'' if lit else '_off'}"] = (
                    lambda b=bid, ls=letters, i=i, c=colour, lit=lit, s=seed: blade_letter(b, ls, i, c, lit, s))
    for i in range(2):
        for lit in (True, False):
            out[f"neon_open_{i}{'' if lit else '_off'}"] = (lambda i=i, lit=lit: open_sign(i, lit, 6100))
    return out


def blocks():
    """Block name -> spec, in blocks.json's format. Every sign faces the wall it hangs on (its
    north side against the wall, like graffiti), so its picture faces out."""
    out = {}
    for sid, style, *_rest in FASCIA:
        k = _rest[3]
        depth = 1 if style == "channel" else 2
        for i in range(k):
            name = f"sign_{sid}_{i}"
            spec = {"model": "boxes", "rotate": "facing", "boxes": [[0, 0, 0, 16, 16, depth, name]]}
            if style == "channel":
                spec["render"] = "cutout"
            out[name] = spec
    for bid, letters, _ in BLADES:
        for i in range(len(letters)):
            for lit in (True, False):
                name = f"blade_{bid}_{i}{'' if lit else '_off'}"
                spec = {"model": "boxes", "rotate": "facing", "boxes": [[6.5, 0, 0, 9.5, 16, 16, name]]}
                if lit:
                    spec["light"] = 8
                out[name] = spec
    for i in range(2):
        for lit in (True, False):
            name = f"neon_open_{i}{'' if lit else '_off'}"
            spec = {"model": "boxes", "rotate": "facing", "render": "cutout", "boxes": [[0, 0, 0, 16, 16, 0.5, name]]}
            if lit:
                spec["light"] = 6
            out[name] = spec
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items()
                      if not k.startswith(("sign_", "blade_", "neon_")) or k == "sign_post" or k in made}  # the bare post isn't a board
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(blocks())} sign blocks in {path}")


if __name__ == "__main__":
    main()
