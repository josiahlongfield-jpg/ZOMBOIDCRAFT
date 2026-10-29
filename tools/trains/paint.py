"""Paints the trains' texture atlas and the call button's block texture.

python3 tools/trains/paint.py   (writes into src/main/resources/assets/zomboidcraft/textures/)

The atlas is 16 x 16 tiles of 32 px, one tile per block of train surface. TrainAtlas.java names
each tile by (column, row); keep the two in step. Hand-painted noise, ribs, rivets, grime and
graffiti, in the same worn-down palette as the rest of the world.
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'zomboidcraft', 'textures')
T = 32
atlas = Image.new('RGBA', (T * 16, T * 16), (0, 0, 0, 0))
rng = random.Random(42)


def tile(col, row):
    return Image.new('RGBA', (T, T), (0, 0, 0, 255)), (col * T, row * T)


def put(img, col, row):
    atlas.paste(img, (col * T, row * T))


def clamp(v):
    return max(0, min(255, int(v)))


def noise_fill(img, base, amount, seed, streak=0.0):
    r = random.Random(seed)
    px = img.load()
    for y in range(T):
        for x in range(T):
            n = (r.random() - 0.5) * amount
            if streak:
                n += math.sin(x * 1.7 + seed) * streak
            px[x, y] = tuple(clamp(c + n) for c in base) + (255,)


def grime(img, seed, strength=40, bottom=True):
    """Dark streaks running down from the top and dirt gathered at the bottom."""
    r = random.Random(seed)
    px = img.load()
    for _ in range(4):
        x0 = r.randrange(T)
        length = r.randrange(6, T)
        for y in range(length):
            x = x0 + (1 if r.random() < 0.15 else 0)
            if 0 <= x < T:
                c = px[x, y]
                f = 1 - strength / 255 * (1 - y / length)
                px[x, y] = (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3])
    if bottom:
        for y in range(T - 6, T):
            for x in range(T):
                c = px[x, y]
                f = 1 - (y - (T - 6)) / 6 * strength / 255 * (0.6 + 0.4 * r.random())
                px[x, y] = (clamp(c[0] * f), clamp(c[1] * f * 0.98), clamp(c[2] * f * 0.95), c[3])


def rust(img, seed, spots=5):
    r = random.Random(seed)
    d = ImageDraw.Draw(img)
    for _ in range(spots):
        x, y = r.randrange(T), r.randrange(T)
        rad = r.randrange(1, 4)
        col = (clamp(110 + r.randrange(40)), clamp(58 + r.randrange(20)), clamp(28 + r.randrange(10)), 255)
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=col)
        for k in range(r.randrange(3, 10)):
            if y + rad + k < T:
                d.point((x + r.randrange(-1, 2), y + rad + k), fill=(col[0] - 20, col[1] - 15, col[2] - 5, 255))


def graffiti(img, seed):
    r = random.Random(seed)
    d = ImageDraw.Draw(img)
    palette = [(214, 40, 52), (40, 170, 220), (240, 200, 40), (120, 210, 70), (230, 100, 200), (250, 250, 250), (20, 20, 20)]
    fill = r.choice(palette)
    out = r.choice([c for c in palette if c != fill])
    # bubbly letters: a few overlapping blobs with an outline, then a drip or two
    blobs = []
    x = r.randrange(0, 6)
    while x < T - 4:
        w, h = r.randrange(5, 10), r.randrange(8, 15)
        y = r.randrange(6, 14)
        blobs.append((x, y, x + w, y + h))
        x += w - r.randrange(1, 3)
    for b in blobs:
        d.rounded_rectangle([b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1], radius=3, fill=out)
    for b in blobs:
        d.rounded_rectangle(b, radius=3, fill=fill)
        if r.random() < 0.5:
            d.line([(b[0] + 2, b[1] + 3), (b[2] - 2, b[3] - 3)], fill=out, width=1)
    for _ in range(r.randrange(1, 4)):
        bx = r.randrange(2, T - 2)
        by = r.randrange(16, 24)
        d.line([(bx, by), (bx, min(T - 1, by + r.randrange(3, 9)))], fill=fill, width=1)


def ribs(img, light, dark, every=4):
    px = img.load()
    for y in range(T):
        for x in range(T):
            if x % every == 0:
                c = px[x, y]
                px[x, y] = tuple(clamp(v * light) for v in c[:3]) + (255,)
            elif x % every == every - 1:
                c = px[x, y]
                px[x, y] = tuple(clamp(v * dark) for v in c[:3]) + (255,)


def rivets(img, rows=(2, T - 3), every=6, col=(200, 200, 200)):
    px = img.load()
    for y in rows:
        for x in range(2, T, every):
            px[x, y] = col + (255,)
            if x + 1 < T:
                px[x + 1, y + 1 if y + 1 < T else y] = tuple(clamp(c * 0.5) for c in col) + (255,)


# ---------------------------------------------------------------- row 0: subway steel sides
STEEL = (150, 154, 158)
for c in range(16):
    img, _ = tile(c, 0)
    noise_fill(img, STEEL, 18, 100 + c, streak=4)
    ribs(img, 1.15, 0.78)
    if c % 3 == 0:
        rivets(img)
    if 6 <= c <= 11:
        graffiti(img, 200 + c)
    grime(img, 300 + c, 60 if c >= 12 else 30)
    if c >= 12:
        rust(img, 400 + c, 4)
    put(img, c, 0)

# ---------------------------------------------------------------- row 1: railway green sides
GREEN = (46, 74, 58)
for c in range(16):
    img, _ = tile(c, 1)
    noise_fill(img, GREEN, 14, 500 + c)
    # chipped paint showing primer and metal
    r = random.Random(600 + c)
    d = ImageDraw.Draw(img)
    for _ in range(r.randrange(2, 7)):
        x, y = r.randrange(T), r.randrange(T)
        d.rectangle([x, y, x + r.randrange(1, 3), y + r.randrange(1, 2)], fill=(120, 116, 104, 255))
    if c % 4 == 0:
        rivets(img, rows=(1, T - 2), every=5, col=(86, 110, 94))
    if 6 <= c <= 11:
        graffiti(img, 700 + c)
    grime(img, 800 + c, 70 if c >= 12 else 40)
    if c >= 12:
        rust(img, 900 + c, 6)
    put(img, c, 1)

# ---------------------------------------------------------------- row 2: bands, roof, underside
for c in range(16):
    img, _ = tile(c, 2)
    if c < 4:          # subway: steel with a faded red stripe through the middle
        noise_fill(img, STEEL, 16, 1000 + c, streak=4)
        ribs(img, 1.12, 0.8)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 11, T, 20], fill=(150, 34, 36, 255))
        d.line([(0, 10), (T, 10)], fill=(90, 90, 92, 255))
        d.line([(0, 21), (T, 21)], fill=(90, 90, 92, 255))
        noise_fill_band = random.Random(1100 + c)
        px = img.load()
        for y in range(11, 21):
            for x in range(T):
                p = px[x, y]
                n = (noise_fill_band.random() - 0.5) * 24
                px[x, y] = (clamp(p[0] + n), clamp(p[1] + n / 2), clamp(p[2] + n / 2), 255)
        grime(img, 1200 + c, 30, bottom=False)
    elif c < 8:        # railway: cream band with a thin red line
        noise_fill(img, (198, 186, 150), 16, 1300 + c)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 26, T, 28], fill=(130, 40, 32, 255))
        grime(img, 1400 + c, 50, bottom=False)
    elif c < 12:       # roof: weathered grey sheet with seams and vents
        noise_fill(img, (84, 86, 88), 22, 1500 + c)
        d = ImageDraw.Draw(img)
        d.line([(0, 15), (T, 15)], fill=(60, 60, 62, 255))
        if c == 10:
            d.rectangle([8, 6, 23, 25], fill=(56, 58, 60, 255))
            for y in range(8, 24, 3):
                d.line([(9, y), (22, y)], fill=(30, 30, 32, 255))
        rust(img, 1600 + c, 3)
    else:              # underside, skirts, couplers: oily near-black
        noise_fill(img, (36, 34, 32), 14, 1700 + c)
        rust(img, 1800 + c, 2)
    put(img, c, 2)

# ---------------------------------------------------------------- row 3: inside
for c in range(16):
    img, _ = tile(c, 3)
    d = ImageDraw.Draw(img)
    if c < 4:          # cream wall panels, scuffed, the odd scrawl
        noise_fill(img, (196, 188, 166), 12, 2000 + c)
        d.line([(0, 0), (0, T)], fill=(150, 144, 126, 255))
        if c == 2:
            d.line([(6, 18), (12, 14), (17, 19), (24, 13)], fill=(40, 40, 60, 255))
        if c == 3:   # a smear of old blood
            for k in range(30):
                x, y = 10 + rng.randrange(12), 8 + rng.randrange(18)
                d.point((x, y), fill=(96 + rng.randrange(30), 20, 18, 255))
        grime(img, 2100 + c, 30)
    elif c < 8:        # studded rubber floor, worn; two with blood
        noise_fill(img, (58, 60, 64), 12, 2200 + c)
        px = img.load()
        for y in range(3, T, 6):
            for x in range(3, T, 6):
                px[x, y] = (84, 86, 90, 255)
                px[x + 1, y] = (84, 86, 90, 255)
                px[x, y + 1] = (40, 42, 46, 255)
        if c >= 6:
            for k in range(60):
                x, y = 6 + int(rng.gauss(10, 5)), 6 + int(rng.gauss(10, 5))
                if 0 <= x < T and 0 <= y < T:
                    px[x, y] = (88 + rng.randrange(30), 14, 12, 255)
    elif c < 10:       # ceiling panels
        noise_fill(img, (176, 172, 160), 10, 2300 + c)
        d.line([(0, 0), (T, 0)], fill=(130, 126, 116, 255))
    elif c < 12:       # the lit strip: a diffuser with the tube glowing through
        noise_fill(img, (236, 238, 226), 8, 2400 + c)
        d.rectangle([0, 0, T, 3], fill=(170, 170, 160, 255))
        d.rectangle([0, T - 4, T, T], fill=(170, 170, 160, 255))
        if c == 11:  # a dead tube, flickery
            d.rectangle([0, 4, 15, T - 5], fill=(150, 150, 142, 255))
    elif c < 14:       # orange moulded bench seats
        noise_fill(img, (196, 104, 40), 16, 2500 + c)
        for x in range(0, T, 8):
            d.line([(x, 0), (x, T)], fill=(140, 70, 28, 255))
        if c == 13:
            d.line([(4, 5), (14, 20)], fill=(70, 40, 20, 255))
    else:              # blue vinyl seats
        noise_fill(img, (52, 74, 120), 16, 2600 + c)
        for x in range(0, T, 8):
            d.line([(x, 0), (x, T)], fill=(34, 48, 80, 255))
        if c == 15:
            d.rectangle([12, 10, 18, 22], fill=(170, 160, 120, 255))   # foam through a slash
    put(img, c, 3)

# ---------------------------------------------------------------- row 4: doors, glass, front, running gear
def door(col, base, window, seed):
    for half in (0, 1):
        img, _ = tile(col + half, 4)
        noise_fill(img, base, 14, seed + half)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 1, T], fill=tuple(clamp(v * 0.6) for v in base) + (255,))
        d.rectangle([T - 2, 0, T, T], fill=(30, 30, 30, 255))   # the rubber seal where leaves meet
        if half == 1:   # upper half: the window, an open hole (drawn cutout) so you see straight through
            d.rectangle([4, 2, T - 6, T - 5], outline=(40, 40, 40, 255))
            d.rectangle([5, 3, T - 7, T - 6], fill=(0, 0, 0, 0))
        else:
            d.rectangle([5, 8, T - 7, 12], fill=(220, 190, 40, 255))  # a yellow "stand clear" sticker
        grime(img, seed + 10 + half, 40)
        put(img, col + half, 4)


door(0, STEEL, (40, 54, 60, 255), 3000)
door(2, GREEN, (34, 44, 40, 255), 3100)
for c, crack in ((4, False), (5, True)):
    # clear glass: barely tinted, a few specks and a faint grime line along the bottom edge
    img = Image.new('RGBA', (T, T), (190, 215, 220, 22))
    d = ImageDraw.Draw(img)
    r = random.Random(3200 + c)
    for _ in range(14):
        x, y = r.randrange(T), r.randrange(T)
        d.point((x, y), fill=(120, 126, 120, 60))
    for y in range(T - 3, T):
        for x in range(T):
            img.putpixel((x, y), (80, 80, 74, 40 + (y - (T - 3)) * 12))
    if crack:
        cx, cy = 18, 12
        for _ in range(7):
            a = r.random() * math.pi * 2
            ln = r.randrange(6, 16)
            d.line([(cx, cy), (cx + math.cos(a) * ln, cy + math.sin(a) * ln)], fill=(230, 240, 240, 140))
    put(img, c, 4)
# headlight
img, _ = tile(6, 4)
noise_fill(img, (40, 40, 42), 8, 3300)
d = ImageDraw.Draw(img)
d.ellipse([6, 6, 26, 26], fill=(80, 80, 80, 255))
d.ellipse([9, 9, 23, 23], fill=(250, 240, 200, 255))
d.ellipse([12, 12, 17, 17], fill=(255, 255, 255, 255))
put(img, 6, 4)
# destination sign: amber dots on black
img, _ = tile(7, 4)
noise_fill(img, (14, 12, 10), 4, 3400)
px = img.load()
pattern = ["X.X.XX.XXX.", "XXX.X..X...", "X.X.XX.XXX."]
for y in range(10, 22, 2):
    for x in range(2, 30, 2):
        if rng.random() < 0.45:
            px[x, y] = (255, 170, 40, 255)
put(img, 7, 4)
# front panel
img, _ = tile(8, 4)
noise_fill(img, (60, 62, 64), 14, 3500)
d = ImageDraw.Draw(img)
for y in range(0, T, 8):
    d.line([(0, y), (T, y)], fill=(230, 190, 40, 255), width=3)   # hazard chevrons on the nose
grime(img, 3510, 60)
put(img, 8, 4)
# black window frame / rubber
img, _ = tile(9, 4)
noise_fill(img, (24, 24, 24), 8, 3600)
put(img, 9, 4)
# chrome pole
img, _ = tile(10, 4)
for x in range(T):
    v = 140 + 100 * math.cos((x / T - 0.35) * math.pi * 2) ** 2
    for y in range(T):
        img.putpixel((x, y), (clamp(v), clamp(v), clamp(v + 6), 255))
put(img, 10, 4)
# bogie frame
img, _ = tile(11, 4)
noise_fill(img, (44, 40, 36), 18, 3700)
rivets(img, rows=(4, 27), every=5, col=(90, 86, 80))
rust(img, 3710, 5)
put(img, 11, 4)
# wheel
img, _ = tile(12, 4)
img = Image.new('RGBA', (T, T), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.ellipse([1, 1, T - 2, T - 2], fill=(60, 56, 52, 255))
d.ellipse([3, 3, T - 4, T - 4], fill=(110, 106, 100, 255))
d.ellipse([10, 10, T - 11, T - 11], fill=(50, 48, 46, 255))
for k in range(6):
    a = k * math.pi / 3
    d.line([(16 + math.cos(a) * 4, 16 + math.sin(a) * 4), (16 + math.cos(a) * 12, 16 + math.sin(a) * 12)], fill=(70, 66, 62, 255), width=2)
put(img, 12, 4)
# rail steel: shiny top, rusty web
img, _ = tile(13, 4)
noise_fill(img, (96, 70, 52), 20, 3800)
for y in range(0, 10):
    for x in range(T):
        img.putpixel((x, y), (clamp(170 + rng.randrange(-10, 10)), clamp(168 + rng.randrange(-10, 10)), clamp(164 + rng.randrange(-10, 10)), 255))
put(img, 13, 4)
# sleepers: old timber, and concrete
img, _ = tile(14, 4)
noise_fill(img, (70, 52, 36), 20, 3900)
d = ImageDraw.Draw(img)
for y in range(0, T, 5):
    d.line([(0, y + rng.randrange(-1, 2)), (T, y)], fill=(52, 38, 26, 255))
put(img, 14, 4)
img, _ = tile(15, 4)
noise_fill(img, (130, 128, 122), 22, 4000)
grime(img, 4010, 50)
put(img, 15, 4)

# ---------------------------------------------------------------- row 5: OTTO, gangway, ads
img, _ = tile(0, 5)
noise_fill(img, (184, 160, 64), 18, 5000)    # OTTO's yellow enamel, chipped
rust(img, 5010, 6)
rivets(img, rows=(2, 29), every=7, col=(220, 200, 110))
put(img, 0, 5)
img, _ = tile(1, 5)
noise_fill(img, (48, 46, 44), 14, 5100)      # his dark joints and hands
put(img, 1, 5)
for c, talking in ((2, False), (3, True)):   # his face: a black visor with two round eyes (and a speaker grille mouth)
    img, _ = tile(c, 5)
    noise_fill(img, (18, 20, 22), 6, 5200 + c)
    d = ImageDraw.Draw(img)
    for ex in (8, 24):
        d.ellipse([ex - 5, 7, ex + 5, 17], fill=(60, 30, 10, 255))
        d.ellipse([ex - 4, 8, ex + 4, 16], fill=(255, 150, 40, 255))
        d.ellipse([ex - 2, 10, ex + 1, 13], fill=(255, 240, 200, 255))
    if talking:
        for x in range(9, 24, 3):
            h = rng.randrange(2, 6)
            d.rectangle([x, 24 - h, x + 1, 24 + h], fill=(255, 150, 40, 255))
    else:
        d.rectangle([9, 23, 23, 25], fill=(120, 70, 20, 255))
    put(img, c, 5)
img, _ = tile(4, 5)                          # chest panel with status lights
noise_fill(img, (70, 72, 74), 10, 5400)
d = ImageDraw.Draw(img)
for i, col in enumerate([(230, 40, 30), (40, 220, 60), (240, 200, 40), (40, 120, 240)]):
    d.rectangle([4 + i * 7, 6, 8 + i * 7, 10], fill=col + (255,))
d.rectangle([4, 16, 28, 26], fill=(30, 30, 30, 255))
d.text((6, 16), "OTTO", fill=(220, 200, 110, 255))
put(img, 4, 5)
img, _ = tile(5, 5)
noise_fill(img, (130, 90, 50), 30, 5500)
rust(img, 5510, 10)
put(img, 5, 5)
img, _ = tile(6, 5)                          # gangway bellows
noise_fill(img, (30, 30, 30), 10, 5600)
d = ImageDraw.Draw(img)
for x in range(0, T, 4):
    d.line([(x, 0), (x, T)], fill=(60, 60, 60, 255))
put(img, 6, 5)
img, _ = tile(7, 5)                          # warning signs in the car
noise_fill(img, (196, 188, 166), 10, 5700)
d = ImageDraw.Draw(img)
d.rectangle([4, 4, 28, 28], fill=(200, 30, 30, 255))
d.rectangle([7, 7, 25, 25], fill=(240, 240, 240, 255))
d.line([(10, 22), (16, 9), (22, 22), (10, 22)], fill=(20, 20, 20, 255), width=2)
put(img, 7, 5)
ads = [((40, 90, 160), (250, 240, 220), "COLA"), ((20, 20, 20), (240, 200, 40), "WCRW"), ((150, 30, 30), (250, 250, 250), "MERCY"),
       ((40, 120, 60), (250, 250, 240), "PARK")]
for i, (bg, fg, word) in enumerate(ads):    # advertising cards above the windows
    img, _ = tile(8 + i, 5)
    noise_fill(img, bg, 18, 5800 + i)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, T - 1, T - 1], outline=(30, 30, 30, 255))
    d.text((2, 10), word, fill=fg + (255,))
    grime(img, 5900 + i, 40)
    put(img, 8 + i, 5)
img, _ = tile(12, 5)                         # the line map strip
noise_fill(img, (236, 232, 220), 6, 6000)
d = ImageDraw.Draw(img)
d.line([(0, 16), (T, 16)], fill=(200, 40, 40, 255), width=3)
for x in (4, 14, 26):
    d.ellipse([x - 2, 14, x + 2, 18], fill=(250, 250, 250, 255), outline=(30, 30, 30, 255))
put(img, 12, 5)
img, _ = tile(13, 5)                         # stop button: a yellow plate, a big red button, STOP under it (shown whole)
noise_fill(img, (226, 186, 40), 8, 6100)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, T - 1, T - 1], outline=(30, 28, 24, 255), width=2)
d.ellipse([7, 3, 24, 20], fill=(70, 12, 10, 255))
d.ellipse([8, 4, 23, 19], fill=(214, 28, 22, 255))
d.ellipse([11, 6, 17, 11], fill=(250, 110, 96, 255))
for i, rows in enumerate(["###.###..#..###", "#....#..#.#.#.#", "###..#..#.#.###", "..#..#..#.#.#..", "###..#...#..#.."]):
    for j, ch in enumerate(rows):
        if ch == '#':
            d.point((8 + j, 23 + i), fill=(24, 22, 20, 255))
put(img, 13, 5)
img, _ = tile(0, 6)                          # close-doors button: a dark plate, a big green button with the doors-shut arrows, CLOSE under it
noise_fill(img, (52, 56, 58), 8, 6150)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, T - 1, T - 1], outline=(20, 20, 20, 255), width=2)
d.ellipse([7, 3, 24, 20], fill=(10, 50, 18, 255))
d.ellipse([8, 4, 23, 19], fill=(40, 176, 70, 255))
d.ellipse([11, 6, 17, 11], fill=(140, 240, 160, 255))
d.rectangle([15, 7, 16, 16], fill=(16, 30, 18, 255))
d.polygon([(10, 8), (14, 11), (14, 12), (10, 15)], fill=(16, 30, 18, 255))
d.polygon([(21, 8), (17, 11), (17, 12), (21, 15)], fill=(16, 30, 18, 255))
for i, rows in enumerate(["###.#...###.###.###", "#...#...#.#.#...#..", "#...#...#.#.###.###", "#...#...#.#...#.#..", "###.###.###.###.###"]):
    for j, ch in enumerate(rows):
        if ch == '#':
            d.point((6 + j, 23 + i), fill=(230, 232, 226, 255))
put(img, 0, 6)
# ---- track detail (row 6): tie plates, ballast, weeds, third-rail insulators and cover, clips
img, _ = tile(1, 6)                          # tie plate: rusty rolled steel, bolt holes, scale
noise_fill(img, (96, 62, 40), 26, 6300)
d = ImageDraw.Draw(img)
for (x, y) in [(6, 8), (25, 8), (6, 23), (25, 23)]:
    d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(40, 30, 24, 255))
for _ in range(40):
    x, y = rng.randrange(T), rng.randrange(T)
    d.point((x, y), fill=(150, 92, 52, 255))
put(img, 1, 6)
img, _ = tile(2, 6)                          # ballast: crushed grey stone, rust-stained, oil-dark in places
r2 = random.Random(6400)
px = img.load()
for y in range(T):
    for x in range(T):
        px[x, y] = (54, 52, 50, 255)
for _ in range(150):
    cx, cy, rr = r2.randrange(T), r2.randrange(T), r2.randrange(1, 4)
    g = r2.randrange(80, 170)
    tint = r2.random()
    col = (clamp(g + (24 if tint < 0.2 else 0)), clamp(g - (6 if tint < 0.2 else 0)), clamp(g - (16 if tint < 0.2 else 0)), 255)
    ImageDraw.Draw(img).ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=col)
    ImageDraw.Draw(img).point((cx - 1, cy - 1), fill=(clamp(g + 40),) * 3 + (255,))
for _ in range(3):
    cx, cy = r2.randrange(T), r2.randrange(T)
    for k in range(30):
        x, y = cx + r2.randrange(-5, 6), cy + r2.randrange(-4, 5)
        if 0 <= x < T and 0 <= y < T:
            c = px[x, y]
            px[x, y] = (clamp(c[0] * 0.6), clamp(c[1] * 0.58), clamp(c[2] * 0.55), 255)
put(img, 2, 6)
img = Image.new('RGBA', (T, T), (0, 0, 0, 0))  # weeds through the ballast (cut out)
d = ImageDraw.Draw(img)
r3 = random.Random(6500)
for _ in range(16):
    x = r3.randrange(2, T - 2)
    h = r3.randrange(8, 28)
    lean = r3.uniform(-0.35, 0.35)
    g = (r3.randrange(60, 110), r3.randrange(90, 140), r3.randrange(30, 60), 255)
    if r3.random() < 0.3:
        g = (r3.randrange(120, 150), r3.randrange(110, 130), r3.randrange(50, 70), 255)   # dry
    for k in range(h):
        d.point((clamp(x + lean * k) if 0 <= x + lean * k < T else x, T - 1 - k), fill=g)
    if r3.random() < 0.25:
        d.ellipse([x - 2, T - h - 2, x + 2, T - h + 2], fill=(220, 210, 120, 255))
put(img, 3, 6)
img, _ = tile(4, 6)                          # third-rail insulator: glazed ceramic, chipped, sooted
noise_fill(img, (196, 180, 140), 14, 6600)
d = ImageDraw.Draw(img)
for y in range(4, T, 6):
    d.line([(0, y), (T, y)], fill=(120, 108, 84, 255))
grime(img, 6610, 70)
put(img, 4, 6)
img, _ = tile(5, 6)                          # third-rail cover board: faded yellow fibreglass, warning, grime
noise_fill(img, (176, 150, 60), 14, 6700)
d = ImageDraw.Draw(img)
d.rectangle([0, 0, T - 1, 2], fill=(110, 92, 40, 255))
for x in range(0, T, 8):
    d.line([(x, 10), (x + 4, 20)], fill=(30, 28, 24, 255), width=2)
grime(img, 6710, 80)
put(img, 5, 6)
img, _ = tile(6, 6)                          # clips and joint bars: dark oily steel
noise_fill(img, (52, 48, 46), 16, 6800)
d = ImageDraw.Draw(img)
for x in range(4, T, 9):
    d.ellipse([x - 2, 14, x + 2, 18], fill=(90, 80, 72, 255))
put(img, 6, 6)
img, _ = tile(14, 5)                         # hazard stripes
for y in range(T):
    for x in range(T):
        img.putpixel((x, y), (230, 190, 40, 255) if ((x + y) // 6) % 2 == 0 else (24, 24, 24, 255))
grime(img, 6200, 50)
put(img, 14, 5)
img, _ = tile(15, 5)                         # glowing headlight lens (full bright)
img = Image.new('RGBA', (T, T), (255, 246, 210, 255))
put(img, 15, 5)

os.makedirs(os.path.join(ROOT, 'entity', 'train'), exist_ok=True)
# OTTO lives in the lower half of the atlas (tools/trains/otto.py paints him and writes his model)
sys.path.insert(0, os.path.dirname(__file__))
import otto  # noqa: E402
otto.paint_into(atlas)
otto.write_java()
atlas.save(os.path.join(ROOT, 'entity', 'train', 'atlas.png'))

# ---------------------------------------------------------------- the call button (block texture)
# 64 px: the housing's face (yellow, hazard band, CALL TRAIN, a steel ring round the button, a speaker
# grille, screws, scuffs) and the galvanised steel of its back plate, hood and conduit
B = 64
b = Image.new('RGBA', (B, B), (0, 0, 0, 255))
rb = random.Random(7000)
pb = b.load()
for y in range(B):
    for x in range(B):
        n = (rb.random() - 0.5) * 22
        pb[x, y] = (clamp(214 + n), clamp(176 + n), clamp(40 + n * 0.5), 255)
d = ImageDraw.Draw(b)
d.rectangle([0, 0, B - 1, B - 1], outline=(40, 34, 20, 255), width=2)
for y in range(2, 10):
    for x in range(2, B - 2):
        if ((x + y) // 4) % 2 == 0:
            pb[x, y] = (24, 24, 24, 255)
def letters(dr, text, x, y, col):
    font5 = {'C': ["###", "#..", "#..", "#..", "###"], 'A': ["###", "#.#", "###", "#.#", "#.#"], 'L': ["#..", "#..", "#..", "#..", "###"],
             'T': ["###", ".#.", ".#.", ".#.", ".#."], 'R': ["##.", "#.#", "##.", "#.#", "#.#"], 'I': ["###", ".#.", ".#.", ".#.", "###"],
             'N': ["#.#", "###", "###", "###", "#.#"], ' ': ["...", "...", "...", "...", "..."]}
    for ch in text:
        for j, row in enumerate(font5[ch]):
            for i, c in enumerate(row):
                if c == '#':
                    dr.point((x + i, y + j), fill=col)
        x += 4
letters(d, "CALL TRAIN", 13, 13, (24, 22, 18, 255))
d.ellipse([18, 18 + 2, 46, 46 + 2], fill=(70, 70, 72, 255))
d.ellipse([20, 20 + 2, 44, 44 + 2], fill=(150, 150, 154, 255))
d.ellipse([23, 23 + 2, 41, 41 + 2], fill=(40, 10, 10, 255))
for gy in range(52, 60, 3):
    for gx in range(22, 43, 3):
        d.point((gx, gy), fill=(30, 26, 18, 255))
for (sx, sy) in [(5, 14), (58, 14), (5, 58), (58, 58)]:
    d.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=(110, 110, 112, 255))
    d.line([(sx - 1, sy), (sx + 1, sy)], fill=(40, 40, 40, 255))
for _ in range(14):    # scuffs and chips down to the steel
    x0, y0 = rb.randrange(B), rb.randrange(B)
    d.line([(x0, y0), (x0 + rb.randrange(-6, 7), y0 + rb.randrange(-2, 3))], fill=(120, 116, 104, 255))
for x0 in [rb.randrange(B) for _ in range(5)]:    # rust run down from the screws and the top
    for y in range(rb.randrange(10, 40)):
        if 0 <= x0 < B and 10 + y < B:
            c = pb[x0, 10 + y]
            pb[x0, 10 + y] = (clamp(c[0] * 0.75), clamp(c[1] * 0.6), clamp(c[2] * 0.5), 255)
b.save(os.path.join(ROOT, 'block', 'train_call_button.png'))
s = Image.new('RGBA', (32, 32), (0, 0, 0, 255))
noise_fill(s, (108, 112, 114), 22, 7200)
ds = ImageDraw.Draw(s)
for _ in range(20):
    x0, y0 = rng.randrange(32), rng.randrange(32)
    ds.point((x0, y0), fill=(150, 154, 156, 255))
grime(s, 7210, 60)
s.save(os.path.join(ROOT, 'block', 'train_call_button_steel.png'))
r = Image.new('RGBA', (16, 16), (190, 22, 20, 255))
dr = ImageDraw.Draw(r)
dr.ellipse([3, 3, 9, 9], fill=(250, 110, 100, 255))
r.save(os.path.join(ROOT, 'block', 'train_call_button_red.png'))
print('painted')
