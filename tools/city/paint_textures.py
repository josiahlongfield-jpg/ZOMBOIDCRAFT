#!/usr/bin/env python3
"""Paints the city block textures: 64x64, tileable, with LabPBR shine (_s) and normal (_n) maps.

Every material is painted from layered tileable noise plus a few hand-placed details
(joints, cracks, rain streaks, rust), into albedo, height, smoothness, F0, porosity and
emission. The normal map is derived from the height. Run from the repo root:

    python3 tools/city/paint_textures.py OUT_DIR [name ...]

LabPBR as in tools/gen_hd_skins.py: _n is RG normal (DirectX), B = AO, A = height;
_s is R = smoothness, G = F0 (230+ metal), B = porosity (0-64), A = emission (255 = none).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

N = 64
TEXTURES = {}


def texture(fn):
    TEXTURES[fn.__name__] = fn
    return fn


RUDE = ("CUNT", "FUCK", "SHIT", "PISS", "COCK", "DICK", "TWAT", "SLUT", "WHORE", "FAG", "NIGG", "RAPE",
        "CUM", "TIT", "ASS", "PORN", "SEX", "NAZI", "KKK", "POO", "BUTT", "BOOB", "WANK", "PRICK")


def rude_gaps(text, gone):
    """True when dropping the letters at the indices in gone would leave a rude word to be read in
    what remains (the gaps closed up or not) that the whole text didn't already have."""
    whole = text.upper()
    kept = "".join(ch for i, ch in enumerate(whole) if i not in gone)
    spaced = "".join(" " if i in gone else ch for i, ch in enumerate(whole))
    for seen in (kept, kept.replace(" ", ""), spaced):
        for word in RUDE:
            if word in seen and word not in whole and word not in whole.replace(" ", ""):
                return True
    return False


# ---------------------------------------------------------------- noise and helpers

def drawn_along(boxes):
    """Which way a model runs as drawn: "x", "z" or None (by the boxes that span the block)."""
    fx = sum(1 for b in boxes if b[0] <= 0.5 and b[3] >= 15.5 and b[5] - b[2] < 15)
    fz = sum(1 for b in boxes if b[2] <= 0.5 and b[5] >= 15.5 and b[3] - b[0] < 15)
    return "x" if fx > fz else "z" if fz > fx else None


TURN_FACES = {"north": "west", "south": "east", "east": "north", "west": "south"}


def along_z(boxes):
    """RULE: a rotate="axis" model is drawn running along z (axis=z shows it as drawn, axis=x
    turns it). One drawn along x comes back turned a quarter so a row placed with axis = the
    row's direction joins up instead of standing as stubs across it."""
    if drawn_along(boxes) != "x":
        return boxes
    out = []
    for b in boxes:
        x0, y0, z0, x1, y1, z1 = b[:6]
        tex = b[6] if len(b) > 6 else None
        if isinstance(tex, dict):
            tex = {TURN_FACES.get(k, k): v for k, v in tex.items()}
        nb = [z0, y0, 16 - x1, z1, y1, 16 - x0]
        out.append(nb + ([tex] if len(b) > 6 else []) + list(b[7:]))
    return out


def tnoise(rng, cells, size=N):
    """Tileable value noise in [-1, 1]: a random periodic grid, bilinearly interpolated."""
    g = rng.random((cells, cells)) * 2 - 1
    t = np.arange(size) * cells / size
    i0 = np.floor(t).astype(int) % cells
    i1 = (i0 + 1) % cells
    f = t - np.floor(t)
    f = f * f * (3 - 2 * f)
    rows = g[i0][:, i0] * (1 - f)[None, :] + g[i0][:, i1] * f[None, :]
    rows1 = g[i1][:, i0] * (1 - f)[None, :] + g[i1][:, i1] * f[None, :]
    return rows * (1 - f)[:, None] + rows1 * f[:, None]


def fbm(rng, octaves=((4, 1.0), (8, 0.5), (16, 0.25), (32, 0.12))):
    total = sum(a for _, a in octaves)
    return sum(tnoise(rng, c) * a for c, a in octaves) / total


def blur(a, r=1):
    out = a.copy()
    for _ in range(r):
        out = (out + np.roll(out, 1, 0) + np.roll(out, -1, 0) + np.roll(out, 1, 1) + np.roll(out, -1, 1)) / 5
    return out


class Tex:
    def __init__(self, seed, base):
        self.rng = np.random.default_rng(seed)
        self.c = np.zeros((N, N, 3), np.float32) + np.array(base, np.float32)
        self.h = np.full((N, N), 0.75, np.float32)  # 0..1
        self.smooth = np.full((N, N), 0.2, np.float32)  # 0..1 perceptual
        self.f0 = np.full((N, N), 10.0, np.float32)
        self.poro = np.full((N, N), 40.0, np.float32)
        self.emit = np.zeros((N, N), np.float32)  # 0..1
        self.alpha = np.full((N, N), 255.0, np.float32)

    # colour helpers
    def vary(self, amount, cells=((4, 1.0), (8, 0.5), (16, 0.3), (32, 0.2)), tint=None):
        n = fbm(self.rng, cells)
        self.c *= (1 + amount * n)[..., None]
        if tint is not None:
            self.c += np.array(tint, np.float32)[None, None] * n[..., None]
        return n

    def speckle(self, chance, col, strength=0.5, height=0.0):
        m = self.rng.random((N, N)) < chance
        self.c[m] = self.c[m] * (1 - strength) + np.array(col, np.float32) * strength
        self.h[m] += height
        return m

    def mix(self, mask, col, strength=1.0):
        w = np.clip(mask, 0, 1)[..., None] * strength
        self.c = self.c * (1 - w) + np.array(col, np.float32) * w

    def streaks(self, amount, dark=0.75):
        """Rain streaks running down from the top."""
        cols = np.clip(tnoise(self.rng, 16)[0] * 0.5 + 0.5, 0, 1) ** 3
        cols = np.where(self.rng.random(N) < 0.35, cols + 0.4, cols)
        length = self.rng.random(N)
        y = np.arange(N)[:, None] / N
        s = cols[None, :] * np.clip(1 - y / np.maximum(length[None, :], 0.2), 0, 1)
        s = blur(s * amount, 1)
        self.c *= (1 - (1 - dark) * np.clip(s, 0, 1))[..., None]
        self.poro += 10 * s

    def grime(self, amount, col=(40, 36, 30)):
        g = np.clip(fbm(self.rng) * 1.5 + 0.2, 0, 1) * amount
        self.mix(g, col, 0.6)
        self.smooth -= g * 0.1

    def crack(self, n, depth=0.25, col=(20, 20, 20)):
        for _ in range(n):
            x, y = self.rng.uniform(0, N, 2)
            ang = self.rng.uniform(0, np.pi * 2)
            for _ in range(int(self.rng.uniform(10, 40))):
                xi, yi = int(x) % N, int(y) % N
                self.c[yi, xi] = self.c[yi, xi] * 0.4 + np.array(col) * 0.6
                self.h[yi, xi] -= depth
                ang += self.rng.normal(0, 0.5)
                x += np.cos(ang)
                y += np.sin(ang)

    def rect(self, x0, y0, x1, y1, col=None, h=None, smooth=None):
        if col is not None:
            self.c[y0:y1, x0:x1] = col
        if h is not None:
            self.h[y0:y1, x0:x1] = h
        if smooth is not None:
            self.smooth[y0:y1, x0:x1] = smooth

    def frame(self, w, col, h=0.95, smooth=0.5, f0=None):
        m = np.zeros((N, N), bool)
        m[:w], m[-w:], m[:, :w], m[:, -w:] = True, True, True, True
        self.c[m] = col
        self.h[m] = h
        self.smooth[m] = smooth
        if f0 is not None:
            self.f0[m] = f0
        return m

    # output
    def albedo(self):
        rgba = np.dstack([np.clip(self.c, 0, 255), self.alpha])
        return Image.fromarray(rgba.astype(np.uint8), "RGBA")

    def specular(self):
        s = np.zeros((N, N, 4), np.float32)
        s[..., 0] = np.clip(self.smooth, 0, 1) * 255
        s[..., 1] = np.clip(self.f0, 0, 255)
        s[..., 2] = np.clip(self.poro, 0, 64)
        sss = getattr(self, "sss", None)
        if sss is not None:
            # LabPBR keeps 65-255 of the blue channel for subsurface scattering: leaves and grass glow through
            s[..., 2] = np.where(sss > 0, 65 + np.clip(sss, 0, 1) * 190, s[..., 2])
        s[..., 3] = np.where(self.emit > 0, np.clip(self.emit * 254, 1, 254), 255)
        return Image.fromarray(s.astype(np.uint8), "RGBA")

    def normal(self, strength=3.0, gl=False):
        h = np.clip(self.h, 0, 1)
        dx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * strength
        dy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * strength
        nz = np.ones_like(h)
        length = np.sqrt(dx * dx + dy * dy + nz * nz)
        nx, ny, nz = -dx / length, -dy / length, nz / length
        ao = np.clip(1 - np.maximum(blur(h, 2) - h, 0) * 3, 0.4, 1)
        out = np.zeros((N, N, 4), np.float32)
        out[..., 0] = (nx * 0.5 + 0.5) * 255
        out[..., 1] = ((-ny if gl else ny) * 0.5 + 0.5) * 255
        out[..., 2] = (nz * 0.5 + 0.5) * 255 if gl else ao * 255
        out[..., 3] = 255 if gl else np.clip(h * 254 + 1, 1, 255)
        return Image.fromarray(out.astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- streets

def asphalt_base(seed, wet=0.3):
    t = Tex(seed, (52, 52, 55))
    t.vary(0.18)
    t.speckle(0.18, (90, 88, 86), 0.5, 0.05)
    t.speckle(0.10, (25, 25, 27), 0.5, -0.05)
    t.h += fbm(t.rng) * 0.08
    t.crack(2, 0.2)
    t.smooth[:] = 0.25 + wet * np.clip(fbm(t.rng, ((4, 1), (8, 0.5))) + 0.2, 0, 1)
    t.poro[:] = 55
    return t


@texture
def asphalt():
    return asphalt_base(1)


def paint_line(t, x0, x1, col, wear=0.35):
    keep = fbm(t.rng, ((8, 1), (16, 0.6), (32, 0.4))) > -1 + wear * 2 * 0.6
    m = np.zeros((N, N), bool)
    m[:, x0:x1] = True
    m &= keep
    t.c[m] = t.c[m] * 0.25 + np.array(col) * 0.75
    t.smooth[m] = 0.45
    t.h[m] += 0.03
    t.poro[m] = 20


@texture
def asphalt_line_yellow():
    """Double yellow centre line, running along the texture's vertical axis."""
    t = asphalt_base(2)
    paint_line(t, 22, 29, (196, 160, 40))
    paint_line(t, 35, 42, (196, 160, 40))
    return t


@texture
def asphalt_line_white():
    """Dashed white lane line (the dash fills the block; gaps are plain asphalt blocks)."""
    t = asphalt_base(3)
    paint_line(t, 28, 36, (210, 210, 205))
    return t


@texture
def crosswalk():
    t = asphalt_base(4)
    paint_line(t, 6, 58, (215, 215, 208), wear=0.45)
    return t


@texture
def sidewalk():
    """Concrete paving slabs, two by two per block, with joints and stains."""
    t = Tex(5, (150, 148, 142))
    t.vary(0.10)
    t.speckle(0.1, (120, 118, 112), 0.4)
    for k in (0, 32):
        t.rect(k, 0, k + 1, N, (95, 93, 90), 0.55)
        t.rect(0, k, N, k + 1, (95, 93, 90), 0.55)
    # each slab a slightly different shade and tilt
    for sx in (0, 32):
        for sy in (0, 32):
            t.c[sy + 1:sy + 32, sx + 1:sx + 32] *= t.rng.uniform(0.92, 1.06)
            t.h[sy + 1:sy + 32, sx + 1:sx + 32] += t.rng.uniform(-0.04, 0.04)
    t.grime(0.4)
    t.speckle(0.004, (60, 58, 55), 0.8)  # chewing gum
    t.crack(1, 0.15)
    t.smooth[:] = 0.22
    t.poro[:] = 45
    return t


@texture
def curb():
    t = Tex(6, (160, 158, 152))
    t.vary(0.08)
    t.rect(0, 0, N, 6, (180, 178, 172), 0.85)  # rounded top edge
    t.rect(0, 6, N, 8, (110, 108, 104), 0.7)
    t.grime(0.5)
    t.streaks(0.6)
    t.smooth[:] = 0.25
    return t


# ---------------------------------------------------------------- facades

@texture
def concrete_panel():
    """Precast facade panel: one per block, faint seams, rain streaks and soot."""
    t = Tex(7, (156, 154, 148))
    t.vary(0.10, tint=(4, 3, 0))
    t.speckle(0.06, (128, 126, 120), 0.5, -0.03)
    t.frame(1, (110, 108, 104), 0.6, 0.15)
    t.streaks(1.0, 0.7)
    t.grime(0.35)
    t.smooth[:] = 0.18
    t.poro[:] = 50
    return t


@texture
def concrete_dark():
    t = Tex(8, (96, 96, 96))
    t.vary(0.14)
    t.speckle(0.08, (70, 70, 70), 0.5, -0.04)
    t.streaks(1.2, 0.65)
    t.grime(0.4, (30, 34, 28))
    t.crack(1)
    t.smooth[:] = 0.16
    t.poro[:] = 52
    return t


@texture
def floor_concrete():
    t = Tex(9, (120, 119, 115))
    t.vary(0.12)
    t.speckle(0.05, (95, 94, 90), 0.4)
    t.grime(0.3)
    t.crack(1, 0.12)
    t.smooth[:] = 0.35
    t.poro[:] = 30
    return t


def bricks(seed, base, mortar, rows=8, per_row=4, soot=0.4):
    t = Tex(seed, base)
    bh, bw = N // rows, N // per_row
    for r in range(rows):
        off = (bw // 2) * (r % 2)
        for b in range(per_row + 1):
            x0 = b * bw - off
            shade = t.rng.uniform(0.82, 1.12)
            tint = t.rng.normal(0, 6, 3)
            for x in range(x0, x0 + bw):
                t.c[r * bh:(r + 1) * bh, x % N] = (t.c[r * bh:(r + 1) * bh, x % N] * shade + tint)
    t.vary(0.10)
    t.speckle(0.08, np.array(base) * 0.7, 0.5, -0.03)
    for r in range(rows):  # mortar joints
        t.rect(0, r * bh, N, r * bh + 1, mortar, 0.55)
        off = (bw // 2) * (r % 2)
        for b in range(per_row + 1):
            x = (b * bw - off) % N
            t.rect(x, r * bh, x + 1, (r + 1) * bh, mortar, 0.55)
    t.streaks(0.8, 0.7)
    t.grime(soot, (28, 24, 22))
    t.smooth[:] = 0.15
    t.poro[:] = 55
    return t


@texture
def brick_red():
    return bricks(10, (128, 60, 46), (110, 104, 96))


@texture
def brick_dark():
    return bricks(11, (70, 52, 46), (88, 84, 78), soot=0.55)


@texture
def stone_trim():
    """Limestone cornice and window sills."""
    t = Tex(12, (170, 164, 150))
    t.vary(0.08)
    t.rect(0, 0, N, 3, (196, 190, 176), 0.9)
    t.rect(0, 3, N, 5, (120, 116, 106), 0.6)
    t.rect(0, 58, N, 60, (120, 116, 106), 0.6)
    t.streaks(1.0, 0.65)
    t.grime(0.3)
    t.smooth[:] = 0.2
    return t


@texture
def steel_mullion():
    t = Tex(13, (52, 56, 60))
    t.vary(0.06)
    t.rect(0, 0, N, N, None, 0.8)
    for x in (8, 56):
        t.rect(x, 0, x + 1, N, (36, 38, 42), 0.7)
    t.streaks(0.6, 0.8)
    t.smooth[:] = 0.55
    t.f0[:] = 231  # iron
    t.poro[:] = 0
    return t


def glass(seed, inside, lit=0.0, broken=False):
    """Window: dark tinted glass with a thin metal frame. The pane is translucent."""
    t = Tex(seed, inside)
    y = np.arange(N)[:, None] / N
    t.c *= (0.85 + 0.3 * (1 - y))[..., None]  # sky reflection fades downward
    t.vary(0.04, ((2, 1.0), (4, 0.5)))
    t.alpha[:] = 150 if not lit else 235
    t.smooth[:] = 0.96
    t.f0[:] = 20
    t.poro[:] = 0
    t.h[:] = 0.9
    if lit:
        t.emit[:] = lit * np.clip(0.75 + 0.25 * fbm(t.rng, ((2, 1.0), (4, 0.5))), 0, 1)
    if broken:
        # a jagged hole with cracks radiating out
        cx, cy = t.rng.uniform(20, 44, 2)
        yy, xx = np.mgrid[0:N, 0:N]
        ang = np.arctan2(yy - cy, xx - cx)
        r = 10 + 8 * np.sin(ang * 5 + t.rng.uniform(0, 6)) + 4 * np.sin(ang * 11)
        hole = np.hypot(xx - cx, yy - cy) < r
        t.alpha[hole] = 0
        for k in range(7):
            a = t.rng.uniform(0, np.pi * 2)
            for s in range(int(r.mean()), 40):
                px, py = int(cx + np.cos(a) * s), int(cy + np.sin(a) * s)
                if 0 <= px < N and 0 <= py < N:
                    t.c[py, px] = (190, 196, 200)
                    t.alpha[py, px] = 220
    m = t.frame(3, (44, 46, 50), 1.0, 0.5, 231)
    t.alpha[m] = 255
    t.emit[m] = 0
    m2 = np.zeros((N, N), bool)
    m2[3:5, 3:-3] = True
    t.c[m2] *= 0.6  # frame shadow on the glass
    return t


@texture
def window():
    return glass(14, (54, 66, 78))


@texture
def window_lit():
    return glass(15, (232, 186, 120), lit=0.85)


@texture
def window_broken():
    return glass(16, (54, 66, 78), broken=True)


# ---------------------------------------------------------------- interiors

@texture
def office_carpet():
    t = Tex(17, (74, 80, 92))
    t.vary(0.10)
    for k in (0, 32):  # carpet tiles laid in alternating directions
        t.rect(k, 0, k + 1, N, (60, 64, 74))
        t.rect(0, k, N, k + 1, (60, 64, 74))
    for sx in (0, 32):
        for sy in (0, 32):
            tile = t.c[sy + 1:sy + 32, sx + 1:sx + 32]
            stripes = (np.arange(31) % 2 == 0)
            if (sx + sy) % 64 == 0:
                tile[:, stripes] *= 0.93
            else:
                tile[stripes, :] *= 0.93
    t.speckle(0.15, (95, 100, 110), 0.3)
    t.grime(0.3, (40, 36, 34))
    t.h += fbm(t.rng, ((32, 1.0),)) * 0.05
    t.smooth[:] = 0.05
    t.poro[:] = 64
    return t


@texture
def ceiling_tile():
    t = Tex(18, (206, 204, 196))
    t.vary(0.04)
    t.speckle(0.25, (175, 172, 165), 0.5, -0.03)  # acoustic pits
    for k in (0, 32):
        t.rect(k, 0, k + 2, N, (150, 150, 148), 0.6, 0.5)
        t.rect(0, k, N, k + 2, (150, 150, 148), 0.6, 0.5)
    # one stained, sagging tile
    m = np.zeros((N, N))
    yy, xx = np.mgrid[0:N, 0:N]
    m = np.clip(1 - np.hypot(xx - 46, yy - 46) / 14, 0, 1) * (fbm(t.rng) > -0.2)
    t.mix(m, (150, 128, 90), 0.6)
    t.smooth[:] = 0.08
    return t


@texture
def ceiling_light():
    t = Tex(19, (240, 238, 228))
    t.vary(0.02)
    t.frame(4, (170, 172, 176), 0.95, 0.5, 231)
    for k in (21, 42):
        t.rect(4, k, 60, k + 1, (200, 200, 196))
    t.emit[4:-4, 4:-4] = 0.9
    t.smooth[:] = 0.6
    return t


@texture
def ceiling_light_off():
    t = ceiling_light()
    t.c[4:-4, 4:-4] *= 0.72
    t.emit[:] = 0
    return t


@texture
def grate():
    """Fire escape and catwalk floor: steel bars with gaps, cut out."""
    t = Tex(44, (60, 64, 60))
    t.vary(0.08)
    gaps = (np.arange(N) % 8 >= 5)[None, :] & (np.arange(N) % 32 != 0)[:, None]
    t.alpha[:] = np.where(gaps, 0, 255)
    chips = fbm(t.rng, ((16, 1.0),)) > 0.4
    t.c[chips] = (104, 64, 40)
    t.smooth[:] = 0.4
    t.f0[:] = 231
    return t


@texture
def red_light():
    t = Tex(45, (230, 40, 30))
    t.vary(0.05)
    t.emit[:] = 1.0
    t.smooth[:] = 0.9
    return t


@texture
def awning():
    """Torn shop awning in faded green and cream stripes."""
    t = Tex(46, (60, 90, 70))
    stripes = (np.arange(N) // 8) % 2 == 1
    t.c[:, stripes] = (190, 182, 160)
    t.vary(0.1)
    t.grime(0.5)
    t.streaks(0.8)
    t.smooth[:] = 0.1
    return t


@texture
def drywall():
    t = Tex(20, (196, 192, 182))
    t.vary(0.05, tint=(2, 1, 0))
    t.h += fbm(t.rng, ((16, 1.0), (32, 0.5))) * 0.03
    t.rect(0, 58, N, N, (90, 86, 80), 0.85, 0.4)  # skirting at the bottom
    t.grime(0.25, (90, 80, 70))
    t.speckle(0.01, (120, 115, 105), 0.5)
    t.smooth[:] = 0.2
    return t


@texture
def marble_white():
    t = Tex(21, (214, 210, 202))
    n = fbm(t.rng, ((2, 1.0), (4, 0.6), (8, 0.4), (16, 0.2)))
    veins = np.exp(-np.abs(np.sin(n * 9 + np.arange(N)[None, :] * 0.07)) * 10)
    t.mix(veins, (130, 128, 124), 0.55)
    t.rect(0, 0, N, 1, (170, 168, 162), 0.7)
    t.rect(0, 0, 1, N, (170, 168, 162), 0.7)
    t.smooth[:] = 0.85
    t.poro[:] = 5
    return t


@texture
def marble_dark():
    t = Tex(22, (48, 50, 54))
    n = fbm(t.rng, ((2, 1.0), (4, 0.6), (8, 0.4), (16, 0.2)))
    veins = np.exp(-np.abs(np.sin(n * 8 + np.arange(N)[:, None] * 0.05)) * 12)
    t.mix(veins, (150, 148, 140), 0.5)
    t.rect(0, 0, N, 1, (30, 30, 32), 0.7)
    t.rect(0, 0, 1, N, (30, 30, 32), 0.7)
    t.smooth[:] = 0.85
    t.poro[:] = 5
    return t


@texture
def wood_panel():
    """Walnut wall panelling for the lobby and the reception desk."""
    t = Tex(23, (92, 60, 40))
    grain = np.sin(np.arange(N)[:, None] * 0.0 + np.arange(N)[None, :] * 0.6 + fbm(t.rng) * 6)
    t.c *= (1 + 0.08 * grain)[..., None]
    t.vary(0.06)
    for x in (0, 16, 32, 48):
        t.rect(x, 0, x + 1, N, (50, 32, 22), 0.6)
    t.smooth[:] = 0.55
    t.poro[:] = 10
    return t


@texture
def stair_concrete():
    t = Tex(24, (128, 126, 120))
    t.vary(0.1)
    t.rect(0, 0, N, 6, (176, 150, 60), 0.85)  # worn yellow nosing
    t.speckle(0.05, (100, 98, 94), 0.4)
    t.grime(0.4)
    t.smooth[:] = 0.3
    return t


@texture
def painted_steel():
    """Railings, stair stringers, fire doors."""
    t = Tex(25, (56, 62, 58))
    t.vary(0.05)
    chips = fbm(t.rng, ((16, 1.0), (32, 0.6))) > 0.45
    t.c[chips] = (110, 70, 44)
    t.smooth[:] = np.where(chips, 0.2, 0.5)
    t.f0[:] = np.where(chips, 231, 10)
    return t


@texture
def elevator_door():
    t = Tex(26, (150, 152, 156))
    x = np.arange(N)[None, :]
    t.c *= (1 + 0.04 * np.sin(x * 2.1 + np.arange(N)[:, None] * 0.0))[..., None]  # brushed
    t.rect(31, 0, 33, N, (60, 62, 66), 0.6)
    t.frame(2, (90, 92, 96), 0.9)
    t.speckle(0.02, (100, 70, 60), 0.4)  # a few rust or blood flecks
    t.smooth[:] = 0.7
    t.f0[:] = 231
    return t


@texture
def desk_laminate():
    t = Tex(27, (168, 150, 120))
    grain = np.sin(np.arange(N)[None, :] * 0.25 + fbm(t.rng) * 3)
    t.c *= (1 + 0.05 * grain)[..., None]
    t.vary(0.04)
    t.smooth[:] = 0.5
    return t


@texture
def cubicle_fabric():
    t = Tex(28, (110, 114, 118))
    weave = ((np.arange(N)[:, None] + np.arange(N)[None, :]) % 2) * 0.06
    t.c *= (1 - weave)[..., None]
    t.vary(0.06)
    t.frame(2, (150, 152, 156), 0.9, 0.5, 231)
    t.smooth[:] = 0.05
    t.poro[:] = 64
    return t


# ---------------------------------------------------------------- ruin and wrecks

@texture
def rust():
    t = Tex(29, (110, 62, 36))
    n = t.vary(0.25, tint=(10, -4, -8))
    t.speckle(0.2, (70, 38, 22), 0.6, -0.05)
    t.speckle(0.08, (150, 90, 50), 0.5, 0.04)
    t.h += n * 0.1
    t.smooth[:] = 0.15
    t.poro[:] = 30
    return t


def car_paint(seed, col):
    t = Tex(seed, col)
    t.vary(0.06)
    r = fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.4))) > 0.38
    t.c[r] = t.c[r] * 0.2 + np.array((105, 60, 36)) * 0.8  # rust patches
    t.h[r] -= 0.05
    t.grime(0.4, (50, 44, 36))
    t.smooth[:] = np.where(r, 0.15, 0.6)
    t.f0[:] = np.where(r, 10, 230)
    return t


@texture
def car_red():
    return car_paint(30, (120, 30, 26))


@texture
def car_white():
    return car_paint(31, (190, 190, 184))


@texture
def car_blue():
    return car_paint(32, (40, 60, 92))


@texture
def tire():
    t = Tex(33, (30, 30, 32))
    t.vary(0.08)
    for y in range(0, N, 6):
        t.rect(0, y, N, y + 2, (20, 20, 22), 0.6)
    t.rect(20, 20, 44, 44, (120, 122, 126), 0.9, 0.6)  # hub
    t.smooth[:] = 0.2
    return t


@texture
def rubble():
    t = Tex(34, (120, 118, 112))
    n = t.vary(0.25)
    lumps = np.clip(fbm(t.rng, ((8, 1.0), (16, 0.5))) * 2, -1, 1)
    t.h += lumps * 0.2
    t.c *= (1 + lumps * 0.2)[..., None]
    t.speckle(0.05, (128, 70, 50), 0.6)  # brick bits
    t.speckle(0.05, (40, 40, 40), 0.5, -0.05)
    t.smooth[:] = 0.15
    t.poro[:] = 60
    return t


@texture
def moss():
    t = Tex(35, (62, 82, 40))
    t.vary(0.25, tint=(6, 10, -4))
    t.speckle(0.2, (90, 110, 50), 0.5, 0.05)
    t.h += fbm(t.rng) * 0.15
    t.smooth[:] = 0.1
    t.poro[:] = 120  # subsurface scattering, LabPBR 65+
    return t


@texture
def roof_gravel():
    t = Tex(36, (90, 88, 84))
    t.vary(0.12)
    t.speckle(0.4, (120, 116, 110), 0.5, 0.06)
    t.speckle(0.3, (60, 58, 56), 0.5, -0.06)
    t.smooth[:] = 0.12
    t.poro[:] = 64
    return t


@texture
def hvac_grille():
    t = Tex(37, (140, 142, 140))
    for y in range(4, N - 4, 4):
        t.rect(4, y, N - 4, y + 2, (60, 62, 62), 0.6)
    t.frame(3, (120, 122, 120), 0.95)
    t.streaks(1.0, 0.7)
    t.smooth[:] = 0.4
    t.f0[:] = 231
    return t


@texture
def jersey_barrier():
    t = Tex(38, (170, 166, 156))
    t.vary(0.08)
    t.streaks(1.0, 0.7)
    t.grime(0.4)
    for x in range(0, N, 16):  # hazard stripes on the top band
        t.rect(x, 0, x + 8, 8, (200, 160, 40))
        t.rect(x + 8, 0, x + 16, 8, (30, 30, 30))
    t.smooth[:] = 0.2
    return t


@texture
def streetlight_metal():
    t = Tex(39, (40, 44, 42))
    t.vary(0.08)
    chips = fbm(t.rng, ((16, 1.0), (32, 0.6))) > 0.5
    t.c[chips] = (96, 60, 36)
    t.smooth[:] = 0.4
    t.f0[:] = np.where(chips, 10, 231)
    return t


@texture
def lamp_glass():
    t = Tex(40, (255, 214, 150))
    t.vary(0.03)
    t.emit[:] = 1.0
    t.smooth[:] = 0.9
    return t


@texture
def chainlink():
    t = Tex(41, (130, 134, 136))
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    m = ((xx + yy) % 16 < 2) | ((xx - yy) % 16 < 2)
    t.alpha[m] = 255
    t.frame(2, (90, 94, 96), 0.9)
    t.alpha[:2], t.alpha[-2:] = 255, 255
    t.smooth[:] = 0.5
    t.f0[:] = 231
    return t


@texture
def weeds():
    """Grass and weeds growing through cracks: a cross model, cut out."""
    t = Tex(42, (70, 92, 44))
    t.alpha[:] = 0
    for _ in range(14):
        x = int(t.rng.uniform(4, 60))
        h = int(t.rng.uniform(20, 60))
        lean = t.rng.uniform(-0.4, 0.4)
        shade = t.rng.uniform(0.7, 1.2)
        for y in range(h):
            px = int(x + lean * y) % N
            for w in (0, 1):
                t.c[N - 1 - y, (px + w) % N] = np.array((70, 92, 44)) * shade * (0.8 + 0.4 * y / h)
                t.alpha[N - 1 - y, (px + w) % N] = 255
    t.smooth[:] = 0.15
    t.poro[:] = 140
    return t


@texture
def vines():
    """Ivy hanging on walls: a thin overlay on a block face, cut out."""
    t = Tex(43, (52, 76, 34))
    t.vary(0.25, tint=(4, 8, -2))
    cover = fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.5))) > -0.05
    leaves = t.rng.random((N, N)) < 0.7
    t.alpha[:] = np.where(cover & leaves, 255, 0)
    t.speckle(0.2, (80, 104, 48), 0.5)
    t.smooth[:] = 0.15
    t.poro[:] = 140
    return t


@texture
def car_green():
    return car_paint(44, (48, 76, 52))


@texture
def car_grey():
    return car_paint(45, (96, 98, 100))


@texture
def car_burnt():
    """Burnt-out shell: soot black over rust, no paint left."""
    t = rust()
    t.rng = np.random.default_rng(46)
    soot = np.clip(fbm(t.rng) * 1.2 + 0.6, 0, 1)
    t.mix(soot, (22, 20, 19), 0.85)
    t.smooth[:] = 0.08
    return t


@texture
def boards():
    """Planks nailed across a window: dark room showing through the gaps."""
    t = Tex(47, (20, 22, 24))
    t.h[:] = 0.3
    for y0, y1, tilt in ((2, 14, 0.12), (20, 32, -0.08), (36, 48, 0.05), (52, 62, -0.1)):
        for x in range(N):
            off = int(tilt * (x - 32))
            ya, yb = max(0, y0 + off), min(N, y1 + off)
            shade = 1 + 0.08 * np.sin(x * 0.7 + y0)
            t.c[ya:yb, x] = np.array((108, 84, 58)) * shade
            t.h[ya:yb, x] = 0.95
            t.c[ya, x] *= 0.7
        for nx in (6, 57):  # nail heads
            ny = (y0 + y1) // 2 + int(tilt * (nx - 32))
            t.c[ny:ny + 2, nx:nx + 2] = (60, 56, 52)
    grain = fbm(t.rng, ((2, 1.0), (32, 0.6), (64, 0.4)))
    planks = t.h > 0.9
    t.c[planks] *= (1 + 0.15 * grain[planks])[..., None]
    t.streaks(0.8, 0.75)
    t.smooth[:] = np.where(planks, 0.12, 0.3)
    t.poro[:] = 50
    return t


@texture
def tank_wood():
    """Old rooftop water tank: vertical staves held by iron hoops."""
    t = Tex(48, (92, 70, 50))
    for x in range(0, N, 8):
        t.rect(x, 0, x + 1, N, (54, 40, 30), 0.6)
    t.vary(0.15, ((2, 1.0), (32, 0.5), (64, 0.3)))
    for y in (10, 40):
        t.rect(0, y, N, y + 3, (60, 44, 34), 0.95, 0.3)
        t.f0[y:y + 3] = 231
    t.streaks(1.0, 0.65)
    t.smooth[:] = 0.1
    return t


@texture
def train_body():
    t = Tex(49, (150, 152, 150))
    for x in range(0, N, 4):
        t.rect(x, 0, x + 1, N, (120, 122, 120), 0.7)  # fluting
    t.rect(0, 40, N, 46, (130, 40, 36))  # livery stripe
    t.vary(0.06)
    t.streaks(1.2, 0.6)
    t.grime(0.5)
    t.smooth[:] = 0.5
    t.f0[:] = 231
    return t


@texture
def barbed_wire():
    t = Tex(50, (110, 104, 98))
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    for phase in (0, 32):
        coil = np.abs(yy - (32 + 18 * np.sin((xx + phase) * np.pi / 16))) < 1.2
        t.alpha[coil] = 255
    for x in range(4, N, 12):  # barbs
        t.alpha[30:35, x:x + 2] = 255
    t.smooth[:] = 0.4
    t.f0[:] = 231
    return t


@texture
def sidewalk_cracked():
    t = sidewalk()
    t.rng = np.random.default_rng(51)
    t.crack(8, 0.3, (30, 30, 30))
    weed = t.rng.random((N, N)) < 0.02
    t.c[weed] = (70, 92, 44)
    return t



@texture
def glass_spandrel():
    """Opaque dark glass over the floor slabs of a curtain wall."""
    t = Tex(52, (34, 40, 48))
    y = np.arange(N)[:, None] / N
    t.c *= (0.8 + 0.4 * (1 - y))[..., None]
    t.vary(0.05, ((2, 1.0), (4, 0.5)))
    t.frame(3, (44, 46, 50), 1.0, 0.5, 231)
    t.streaks(0.8, 0.7)
    t.smooth[:] = 0.9
    t.f0[:] = 40
    t.poro[:] = 0
    return t


def main():
    args = sys.argv[1:]
    out = Path(args[0] if args else "build/city_textures")
    out.mkdir(parents=True, exist_ok=True)
    names = args[1:] or list(TEXTURES)
    for name in names:
        t = TEXTURES[name]()
        t.albedo().save(out / f"{name}.png")
        t.specular().save(out / f"{name}_s.png")
        t.normal().save(out / f"{name}_n.png")
        t.normal(gl=True).save(out / f"{name}_nrm_gl.png")
    print(f"wrote {len(names)} textures to {out}")


if __name__ == "__main__":
    main()
