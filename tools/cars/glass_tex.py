"""
The marks on a car's glass (assets/zomboidcraft/textures/cars/glass_marks.png), drawn cut out so shader packs
treat them as solid, which lets them show from inside the car where the see-through glass itself is left out:

    left half (512 x 512)       a sheet of road dust and dried rain specks, stretched over each window
    right half, top row         two bullet holes (256 px each, about 0.24 m across on the glass)
    right half, bottom row      two cracks from a blow (about 0.4 m across)
    the last 4 x 4 pixels       solid, for the edge of a wound-down window's glass

Run: python3 tools/cars/glass_tex.py
"""
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/zomboidcraft/textures/cars/glass_marks.png'
CELL = 256
K = CELL / 128  # the marks were drawn at 128 px: scale to the cell


def put(img, x, y, col, size=1):
    x, y = int(round(x)), int(round(y))
    for dx in range(size):
        for dy in range(size):
            if 0 <= x + dx < img.width and 0 <= y + dy < img.height:
                img.putpixel((x + dx, y + dy), col)


def specks(seed):
    """Dust and dried drops, sparse enough to look through, thicker toward the bottom where the spray reaches."""
    r = random.Random(seed)
    img = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    for _ in range(2000):
        y = 512 * (1 - r.random() ** 1.6)
        x = r.uniform(0, 511)
        g = r.randint(150, 200)
        col = (g, g - 6, g - 16, 255)
        put(img, x, y, col)
        if r.random() < 0.15:
            put(img, x + 1, y, col)
    # dried rain: short runs down the glass, a dot every pixel or two
    for _ in range(30):
        x, y = r.uniform(0, 511), r.uniform(0, 400)
        g = r.randint(170, 210)
        for k in range(r.randint(10, 40)):
            if r.random() < 0.6:
                put(img, x, y, (g, g, g - 8, 255))
            x += r.uniform(-0.4, 0.4)
            y += 1.3
    return img


def hole(seed):
    """A bullet hole: the hole, a frosted crushed ring, radial cracks running out jagged, broken rings between."""
    r = random.Random(seed)
    img = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
    c = (CELL - 1) / 2
    for k in range(r.randint(8, 12)):
        a = (k + r.uniform(-0.35, 0.35)) / 10 * 2 * math.pi
        length = r.uniform(28, 62) * K
        x, y = c, c
        while math.hypot(x - c, y - c) < length:
            a += r.uniform(-0.18, 0.18)
            x += math.cos(a) * 0.8
            y += math.sin(a) * 0.8
            g = r.randint(220, 245)
            put(img, x, y, (g, g + 5 if g < 250 else g, 248, 255), 2)
            # a crack forks now and then
            if r.random() < 0.01:
                fa, fx, fy = a + r.choice((-0.6, 0.6)), x, y
                for _ in range(int(r.randint(6, 16) * K)):
                    fx += math.cos(fa) * 0.8
                    fy += math.sin(fa) * 0.8
                    put(img, fx, fy, (225, 230, 236, 255), 2)
    for ring in (r.uniform(15, 20) * K, r.uniform(28, 36) * K):
        for t in range(0, 360, 1):
            if r.random() < 0.5:
                continue
            a = math.radians(t)
            rr = ring + r.uniform(-1, 1) * K
            put(img, c + math.cos(a) * rr, c + math.sin(a) * rr, (215, 224, 230, 255), 2)
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x - c, y - c)
            if d < (4.5 + r.uniform(-0.6, 0.6)) * K:
                img.putpixel((x, y), (14, 16, 18, 255))
            elif d < (11 + r.uniform(-1.2, 1.2)) * K and r.random() < 0.8:
                g = r.randint(200, 240)
                img.putpixel((x, y), (g, g + 4 if g < 250 else g, g + 8 if g < 246 else g, 255))
    return img


def crack(seed):
    """A blow's crack: no hole, a crazed spot and long cracks running a long way out, with crossing arcs."""
    r = random.Random(seed)
    img = Image.new('RGBA', (CELL, CELL), (0, 0, 0, 0))
    c = (CELL - 1) / 2 + r.uniform(-4, 4) * K
    for k in range(r.randint(6, 9)):
        a = (k + r.uniform(-0.4, 0.4)) / 7.5 * 2 * math.pi
        length = r.uniform(40, 63) * K
        x, y = c, c
        while math.hypot(x - c, y - c) < length:
            a += r.uniform(-0.12, 0.12)
            x += math.cos(a) * 0.8
            y += math.sin(a) * 0.8
            put(img, x, y, (228, 234, 240, 255), 2)
    for ring in (r.uniform(10, 16) * K, r.uniform(24, 32) * K, r.uniform(40, 50) * K):
        start = r.uniform(0, 360)
        for t in range(int(start), int(start) + r.randint(90, 240)):
            a = math.radians(t)
            put(img, c + math.cos(a) * ring, c + math.sin(a) * ring, (218, 226, 232, 255), 2)
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x - c, y - c)
            if d < 7 * K and r.random() < 0.6:
                g = r.randint(205, 240)
                img.putpixel((x, y), (g, g, g + 6 if g < 249 else g, 255))
    return img


def main():
    atlas = Image.new('RGBA', (1024, 512), (0, 0, 0, 0))
    atlas.paste(specks(11), (0, 0))
    atlas.paste(hole(21), (512, 0))
    atlas.paste(hole(22), (768, 0))
    atlas.paste(crack(31), (512, 256))
    atlas.paste(crack(32), (768, 256))
    atlas.paste(Image.new('RGBA', (4, 4), (205, 214, 218, 255)), (1020, 508))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT)
    print(OUT)


if __name__ == '__main__':
    main()
