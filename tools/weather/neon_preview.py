"""Paints a concept of the city's neon at night: a wet street in the endless night, a few faded
signs throwing coloured glow across the puddles. A look test before any neon goes in game.

python3 tools/weather/neon_preview.py OUT.png [day]
"""
import random
import sys

import numpy as np
from PIL import Image, ImageFilter

W, H = 1600, 900
B = 24                      # one block on screen
HORIZON = 560               # where the facades meet the street
rnd = random.Random(7)

FONT = {
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "I": ["111", "010", "010", "010", "010", "010", "111"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
}

# muted, a little faded: the city lost power years ago and only some tubes still run
MAGENTA = (225, 70, 170)
CYAN = (70, 200, 225)
TEAL = (60, 220, 160)
AMBER = (240, 160, 60)
RED = (230, 60, 60)
LAVENDER = (190, 150, 255)


def noise_tex(w, h, base, var, seed):
    r = np.random.default_rng(seed)
    n = r.normal(0, var, (h // 4 + 1, w // 4 + 1, 1))
    n = np.kron(n, np.ones((4, 4, 1)))[:h, :w]
    return np.clip(np.array(base, float) + n, 0, 255)


def main(out, day):
    sky_top = (26, 30, 38) if day else (12, 14, 19)
    sky_bot = (44, 49, 58) if day else (22, 25, 31)
    img = np.zeros((H, W, 3), float)
    for y in range(H):
        t = y / H
        img[y] = np.array(sky_top) * (1 - t) + np.array(sky_bot) * t
    glow = np.zeros((H, W, 3), float)       # light the signs throw, before blur
    core = np.zeros((H, W, 1), float)       # where the tubes themselves are

    # far towers: flat silhouettes in the haze, a handful of dead-still windows lit
    x = 0
    while x < W:
        w = rnd.randint(3, 7) * B
        h = rnd.randint(9, 17) * B
        shade = 30 + rnd.randint(0, 8) + (10 if day else 0)
        img[HORIZON - 120 - h + 200:HORIZON, x:x + w] = (shade, shade + 3, shade + 8)
        for _ in range(rnd.randint(0, 3)):
            wx = x + rnd.randrange(0, max(1, w - 8), 8)
            wy = rnd.randrange(HORIZON - h + 80, HORIZON - 40)
            img[wy:wy + 6, wx:wx + 5] = (90, 80, 60)
        x += w + rnd.randint(0, 2) * B

    # near facades: weathered brick, concrete and faded paint, block by block
    facades = [(0, 8, 14, (70, 46, 40)), (8, 7, 11, (64, 68, 66)), (15, 9, 16, (58, 52, 62)),
               (24, 7, 10, (78, 64, 48)), (31, 9, 13, (52, 58, 60)), (40, 10, 15, (66, 50, 46)),
               (50, 8, 12, (60, 62, 70)), (58, 9, 14, (72, 56, 44))]
    for bx, bw, bh, col in facades:
        x0, x1, y0 = bx * B, min(W, (bx + bw) * B), HORIZON - bh * B
        dark = 0.42 if day else 0.30
        tex = noise_tex(x1 - x0, HORIZON - y0, [c * dark for c in col], 4, bx)
        img[y0:HORIZON, x0:x1] = tex
        # block seams, so it reads as Minecraft
        for gx in range(x0, x1, B):
            img[y0:HORIZON, gx] *= 0.85
        for gy in range(y0, HORIZON, B):
            img[gy, x0:x1] *= 0.85
        img[y0:y0 + 6, x0:x1] *= 0.6        # cornice shadow
        # windows: most broken or boarded, a few dim lamps inside
        for fy in range(y0 + B, HORIZON - 3 * B, 2 * B):
            for fx in range(x0 + B // 2, x1 - B, 2 * B):
                r = rnd.random()
                if r < 0.5:
                    img[fy:fy + B, fx:fx + B] = (8, 9, 11)
                elif r < 0.8:
                    img[fy:fy + B, fx:fx + B] = (20, 16, 13)
                    for k in range(3):
                        img[fy + 3 + k * 7:fy + 7 + k * 7, fx:fx + B] = (30, 24, 18)
                elif r < 0.95:
                    img[fy:fy + B, fx:fx + B] = (24, 28, 34)
                else:
                    img[fy:fy + B, fx:fx + B] = (80, 58, 34)
                    glow[fy:fy + B, fx:fx + B] += (30, 20, 8)
        # dark shopfront
        img[HORIZON - 3 * B:HORIZON, x0 + B // 2:x1 - B // 2] = (10, 11, 13)

    def tube(x, y, w, h, col, strength=1.0):
        c = np.array(col, float)
        img[y:y + h, x:x + w] = np.minimum(255, (c * 0.8 + 55) * strength + 30 * (1 - strength))
        core[y:y + h, x:x + w] = 1
        glow[y:y + h, x:x + w] += c * 3.0 * strength

    def word(text, x, y, s, col, dead=(), flicker=()):
        for i, ch in enumerate(text):
            rows = FONT[ch]
            for ry, row in enumerate(rows):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        px, py = x + rx * s, y + ry * s
                        if i in dead:
                            img[py:py + s - 1, px:px + s - 1] = (40, 38, 42)   # dead glass tube
                        else:
                            tube(px, py, s - 1, s - 1, col, 0.45 if i in flicker else 1.0)
            x += (len(rows[0]) + 1) * s
        return x

    def frame(x, y, w, h):
        img[y:y + h, x:x + w] = (22, 22, 26)
        img[y:y + 2, x:x + w] = (40, 40, 46)

    # MOTEL, hanging off the corner with an arrow; the E is dead
    frame(140, 300, 132, 200)
    for i, ch in enumerate("MOTEL"):
        word(ch, 175, 312 + i * 37, 6, MAGENTA, dead=(0,) if ch == "E" else ())
    for k in range(8):
        tube(280 + k * 9, 478 - k * 5, 8, 5, MAGENTA)
    tube(344, 438, 5, 14, MAGENTA)
    tube(334, 438, 15, 5, MAGENTA)
    # pharmacy cross
    frame(420, 380, 70, 70)
    tube(447, 390, 16, 50, TEAL)
    tube(430, 407, 50, 16, TEAL)
    # DINER over a shopfront, one letter flickering
    word("DINER", 600, HORIZON - 3 * B - 52, 7, CYAN, flicker=(3,))
    # under-awning strip of lavender (the pearlescent froglight look)
    tube(380, HORIZON - 3 * B - 6, 260, 4, LAVENDER, 0.7)
    # BAR in amber
    word("BAR", 990, HORIZON - 3 * B - 50, 7, AMBER)
    # OPEN, half dead
    word("OPEN", 1190, HORIZON - 2 * B, 4, RED, dead=(1, 2))
    # VACANCY blade in lavender, one letter out
    frame(1350, 250, 46, 250)
    for i, ch in enumerate("VACANCY"):
        word(ch, 1362, 262 + i * 34, 4, LAVENDER, dead=(0,) if i == 3 else ())

    # street: wet black asphalt, a kerb, puddles
    for yy in range(HORIZON, H):
        t = (yy - HORIZON) / (H - HORIZON)
        img[yy] = np.array((16, 18, 22) if not day else (24, 27, 32)) * (1 - 0.3 * t)
    img[HORIZON:HORIZON + 10] = (34, 35, 38)
    img[HORIZON + 10:HORIZON + 13] = (12, 12, 14)

    # glow: soft halo round every lit tube
    g = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8))
    halo = (np.array(g.filter(ImageFilter.GaussianBlur(6)), float) * 1.6
            + np.array(g.filter(ImageFilter.GaussianBlur(24)), float) * 2.2
            + np.array(g.filter(ImageFilter.GaussianBlur(80)), float) * 3.5)
    lit = img + halo * (1 - 0.75 * core) * (0.8 if day else 1.0)

    # reflections: the lit facade mirrored into the wet street, smeared down the way water does
    above = lit[HORIZON - (H - HORIZON):HORIZON][::-1]
    refl = Image.fromarray(np.clip(above, 0, 255).astype(np.uint8))
    refl = np.array(refl.filter(ImageFilter.GaussianBlur((3, 10))), float)
    hl = Image.fromarray(np.clip(halo[HORIZON - (H - HORIZON):HORIZON][::-1] * 1.5, 0, 255).astype(np.uint8))
    streak = np.array(hl.filter(ImageFilter.GaussianBlur((4, 30))), float)
    rng = np.random.default_rng(3)
    wet = np.clip(rng.normal(0.6, 0.3, ((H - HORIZON) // 12 + 1, W // 40 + 1)), 0.1, 1)
    wet = np.array(Image.fromarray((wet * 255).astype(np.uint8)).resize((W, H - HORIZON), Image.BICUBIC), float)[..., None] / 255
    fade = np.linspace(1, 0.45, H - HORIZON)[:, None, None]
    lit[HORIZON + 13:] += ((refl * 0.35 + streak * 0.7) * wet * fade)[13:]

    # rain: the new thin streaks, faint enough to see through
    rain = np.zeros((H, W), float)
    for _ in range(900):
        x0, y0 = rnd.randrange(W), rnd.randrange(-40, H)
        ln = rnd.randint(14, 40)
        a = rnd.uniform(12, 34)
        for i in range(ln):
            yy, xx = y0 + i, x0 + i // 9
            if 0 <= yy < H and 0 <= xx < W:
                rain[yy, xx] = max(rain[yy, xx], a * i / ln)
    lit += rain[..., None] * np.array([0.8, 0.85, 0.95]) + halo * (rain[..., None] / 40) * 0.8

    # a little haze in the distance
    hz = np.clip(1 - np.abs(np.arange(H) - HORIZON) / 260, 0, 1)[:, None, None] * 0.18
    lit = lit * (1 - hz) + np.array(sky_bot) * 1.3 * hz
    Image.fromarray(np.clip(lit, 0, 255).astype(np.uint8)).save(out, quality=92)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1], len(sys.argv) > 2 and sys.argv[2] == "day")
