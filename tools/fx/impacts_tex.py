"""Paints textures/effect/impacts.png: five 32px cells side by side for client.combat.ImpactFx.

0, 1  bullet holes: a dark hole in a ring of chipped, scorched surface (drawn over any block, so greys)
2, 3  blood splashes: an uneven pool with streaks and droplets thrown out one way (+x is "along the shot")
4     solid blood, for the flying drops
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


def main():
    out = Image.new('RGBA', (N * 5, N), (0, 0, 0, 0))
    out.paste(cell_hole(1), (0, 0))
    out.paste(cell_hole(2), (N, 0))
    out.paste(cell_splash(3), (N * 2, 0))
    out.paste(cell_splash(4), (N * 3, 0))
    out.paste(Image.new('RGBA', (N, N), (92, 8, 8, 255)), (N * 4, 0))
    out = out.resize((N * 10, N * 2), Image.NEAREST)
    out.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
