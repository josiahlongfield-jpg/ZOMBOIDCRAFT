"""Paints textures/effect/impacts.png: seven 32px cells side by side for client.combat.ImpactFx.

0, 1  bullet holes: a dark hole in a ring of chipped, scorched surface (drawn over any block, so greys)
2, 3  blood splashes: an uneven pool with streaks and droplets thrown out one way (+x is "along the shot")
4     solid blood, for the flying drops
5, 6  bullet holes in glass: a small clean hole in a frosted crush ring, cracks running out from it (car glass too)
Pixel art at 16 px a cell, doubled, so it sits with Minecraft's blocky textures.
Run: python3 tools/fx/impacts_tex.py
"""
import math
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/zomboidcraft/textures/effect/impacts.png'
N = 16


def cell_hole(seed):
    r = random.Random(seed)
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    c = (N - 1) / 2
    chips = [r.uniform(0.75, 1.25) for _ in range(12)]
    for y in range(N):
        for x in range(N):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi) * 12) % 12
            edge = 6.2 * chips[int(a)]
            if d < 1.9:
                img.putpixel((x, y), (12, 10, 9, 255))          # the hole itself
            elif d < 3.0:
                img.putpixel((x, y), (38, 33, 30, 245))          # its scorched lip
            elif d < edge:
                k = r.randint(-10, 10)
                fade = 1 - (d - 3.0) / max(0.1, edge - 3.0)
                img.putpixel((x, y), (92 + k, 86 + k, 80 + k, int(70 + 120 * fade)))   # chipped-off face
    return img


def cell_splash(seed):
    r = random.Random(seed)
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    blobs = [(6.5, 7.5, 4.2)]
    for _ in range(5):
        a = r.uniform(-1.3, 1.3)
        d = r.uniform(1.5, 4.0)
        blobs.append((6.5 + math.cos(a) * d, 7.5 + math.sin(a) * d, r.uniform(1.2, 2.4)))
    for _ in range(7):     # droplets thrown on ahead (+x)
        blobs.append((r.uniform(9.5, 15), r.uniform(2, 13), r.uniform(0.4, 0.9)))
    for _ in range(3):     # a few back the other way
        blobs.append((r.uniform(0.5, 3), r.uniform(3, 12), r.uniform(0.4, 0.7)))
    for y in range(N):
        for x in range(N):
            best = 0.0
            for bx, by, br in blobs:
                best = max(best, 1 - math.hypot(x - bx, y - by) / br)
            if best > 0:
                k = r.randint(-8, 8)
                deep = min(1.0, best * 1.8)
                col = (int(70 + 40 * (1 - deep)) + k, 6 + k // 2, 6 + k // 2)
                img.putpixel((x, y), (*col, int(150 + 95 * deep)))
    return img


def cell_glass(seed):
    """A hole in glass, at 32 px (it's finer than the block marks): a frosted ring and radial cracks with a few rings."""
    r = random.Random(seed)
    M = N * 2
    img = Image.new('RGBA', (M, M), (0, 0, 0, 0))
    c = (M - 1) / 2

    def put(x, y, col):
        if 0 <= x < M and 0 <= y < M:
            old = img.getpixel((x, y))
            if col[3] > old[3]:
                img.putpixel((x, y), col)
    # the spider: cracks running out at uneven angles and lengths, jagged as they go
    for k in range(r.randint(7, 10)):
        a = (k + r.uniform(-0.3, 0.3)) / 9 * 2 * math.pi
        length = r.uniform(8, 15.5)
        x, y = c, c
        step = 0
        while math.hypot(x - c, y - c) < length:
            a += r.uniform(-0.25, 0.25)
            x += math.cos(a) * 0.7
            y += math.sin(a) * 0.7
            step += 1
            fade = 1 - math.hypot(x - c, y - c) / length
            put(int(round(x)), int(round(y)), (235, 240, 242, int(110 + 120 * fade)))
    # broken rings joining the cracks, as glass crazes round a hit
    for ring in (r.uniform(4.5, 6), r.uniform(8.5, 11)):
        for t in range(0, 360, 3):
            if r.random() < 0.45:
                continue
            a = math.radians(t)
            rr = ring + r.uniform(-0.5, 0.5)
            put(int(round(c + math.cos(a) * rr)), int(round(c + math.sin(a) * rr)), (225, 232, 236, 120))
    # the crushed, frosted ring and the hole
    for y in range(M):
        for x in range(M):
            d = math.hypot(x - c, y - c)
            if d < 1.6:
                img.putpixel((x, y), (16, 18, 20, 255))
            elif d < 3.4 + r.uniform(-0.4, 0.4):
                k = r.randint(-12, 12)
                put(x, y, (215 + k, 222 + k, 228 + k, 215))
    return img


def main():
    out = Image.new('RGBA', (N * 7, N), (0, 0, 0, 0))
    out.paste(cell_hole(1), (0, 0))
    out.paste(cell_hole(2), (N, 0))
    out.paste(cell_splash(3), (N * 2, 0))
    out.paste(cell_splash(4), (N * 3, 0))
    out.paste(Image.new('RGBA', (N, N), (92, 8, 8, 255)), (N * 4, 0))
    out = out.resize((N * 14, N * 2), Image.NEAREST)
    out.paste(cell_glass(5), (N * 10, 0))
    out.paste(cell_glass(6), (N * 12, 0))
    out.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
