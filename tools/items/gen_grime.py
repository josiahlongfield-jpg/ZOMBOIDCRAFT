"""Dirt and blood layers for worn clothes (client/clothing/ClothingLayer, client/combat/MeshGuns.overlay).

Four steps of each (clothing/ClothDirt steps), cut-out PNGs laid over a garment by position: 4 texels a pixel across
item pixels -20 to 28, down the texture being down the body part. Each step keeps every mark of the one before and
adds more, so a piece gets dirtier rather than differently dirty. Flat toon tones, a darker rim on blood.

    python3 tools/items/gen_grime.py
"""
from pathlib import Path
import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/zomboidcraft/textures/entity/grime'
N = 192
COVER = [0.18, 0.40, 0.68, 1.0]          # share of the marks each step shows


def blob(canvas, cx, cy, r, rng, col, rim=None):
    yy, xx = np.mgrid[0:N, 0:N]
    ang = np.arctan2(yy - cy, xx - cx)
    wob = 1 + 0.28 * np.sin(ang * rng.integers(3, 7) + rng.uniform(0, 6)) + 0.12 * np.sin(ang * 11 + rng.uniform(0, 6))
    d = np.hypot(xx - cx, yy - cy) / (r * wob)
    if rim is not None:
        canvas[(d <= 1) & (d > 0.72)] = rim
        canvas[d <= 0.72] = col
    else:
        canvas[d <= 1] = col


def dirt():
    rng = np.random.default_rng(42)
    marks = []
    def row():                            # four in ten round the shins and hems (rows 70-100: 6-13 px below a hip, the part models standing 8 px up)
        return rng.uniform(70, 100) if rng.random() < 0.4 else rng.uniform(0, N)
    for _ in range(420):
        marks.append(('speck', rng.uniform(0, N), row(), rng.uniform(0.6, 1.8)))
    for _ in range(60):
        marks.append(('smudge', rng.uniform(0, N), row(), rng.uniform(3, 8)))
    rng.shuffle(marks)
    tones = [(74, 58, 40, 255), (92, 76, 56, 255), (58, 50, 42, 255)]
    for step, share in enumerate(COVER, 1):
        img = np.zeros((N, N, 4), np.uint8)
        r2 = np.random.default_rng(7)
        for kind, x, y, r in marks[:int(len(marks) * share)]:
            blob(img, x, y, r, r2, tones[r2.integers(0, 3)])
        Image.fromarray(img).save(OUT / f'dirt_{step}.png')


def blood():
    rng = np.random.default_rng(1337)
    marks = []
    for _ in range(46):
        marks.append((rng.uniform(0, N), rng.uniform(0, N), rng.uniform(2.5, 7.5), rng.integers(0, 3)))
    for _ in range(140):
        marks.append((rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0.6, 1.6), 0))
    rng.shuffle(marks)
    body, rim = (128, 14, 16, 255), (72, 6, 10, 255)
    for step, share in enumerate(COVER, 1):
        img = np.zeros((N, N, 4), np.uint8)
        r2 = np.random.default_rng(9)
        for x, y, r, drips in marks[:int(len(marks) * share)]:
            for _ in range(drips):           # runs down from the splash
                dx = x + r2.uniform(-r * 0.7, r * 0.7)
                ln = r2.uniform(r * 1.2, r * 3.5)
                w = max(0.8, r * 0.22)
                for t in np.linspace(0, ln, int(ln * 2) + 2):
                    blob(img, dx, y + t, w * (1 - 0.3 * t / ln), r2, rim)
                blob(img, dx, y + ln, w * 1.3, r2, rim)
            blob(img, x, y, r, r2, body, rim if r > 2 else None)
        Image.fromarray(img).save(OUT / f'blood_{step}.png')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    dirt()
    blood()
    print('grime layers in', OUT)
