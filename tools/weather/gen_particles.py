"""Writes the weather particle textures: soft mist puffs for the fog, and scraps of paper,
leaves and plastic for the wind to blow down the street.

python3 tools/weather/gen_particles.py
"""
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/zomboidcraft/textures/particle"


def value_noise(rng, n, cells):
    g = rng.random((cells + 1, cells + 1)).astype(np.float32)
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
    return np.asarray(img, np.float32) / 255


def mist(seed, n=64):
    """A soft, uneven puff: fades to nothing at the rim so the quad's edge never shows."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.sqrt(x * x + (y * 1.15) ** 2)
    fall = np.clip(1 - r, 0, 1) ** 1.8
    tex = 0.55 * value_noise(rng, n, 3) + 0.3 * value_noise(rng, n, 6) + 0.15 * value_noise(rng, n, 12)
    a = np.clip(fall * (0.35 + 0.9 * tex), 0, 1)
    rgba = np.zeros((n, n, 4), np.uint8)
    rgba[..., :3] = 235
    rgba[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(rgba).filter(ImageFilter.GaussianBlur(1))


def scrap(kind, seed, n=16):
    """A bit of litter, 16px: newspaper, a dead leaf, a plastic bag shred, a receipt."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "paper":
        pts = [(2 + rng.random() * 2, 3 + rng.random() * 2), (13 - rng.random() * 2, 2 + rng.random() * 2),
               (14 - rng.random() * 2, 12 - rng.random() * 2), (3 + rng.random() * 2, 13 - rng.random() * 2)]
        base = rng.choice([(196, 190, 172), (176, 170, 150), (206, 200, 186)])
        d.polygon(pts, fill=base + (255,))
        for row in range(5, 12, 2):  # columns of print
            d.line([(5, row), (11, row)], fill=(110, 106, 98, 255))
    elif kind == "leaf":
        col = rng.choice([(96, 70, 38), (120, 84, 40), (78, 60, 34), (104, 96, 44)])
        d.ellipse([3, 5, 13, 11], fill=col + (255,))
        d.line([(2, 8), (14, 8)], fill=tuple(c // 2 for c in col) + (255,))
    elif kind == "plastic":
        col = rng.choice([(200, 204, 206), (60, 62, 66), (150, 40, 36)])
        pts = [(rng.randint(1, 5), rng.randint(1, 6)), (rng.randint(10, 15), rng.randint(2, 7)),
               (rng.randint(9, 14), rng.randint(10, 15)), (rng.randint(2, 6), rng.randint(9, 14))]
        d.polygon(pts, fill=col + (200,))
    else:  # receipt
        d.rectangle([6, 2, 10, 14], fill=(214, 210, 198, 255))
        for row in range(4, 13, 2):
            d.line([(7, row), (9, row)], fill=(130, 126, 120, 255))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i in range(4):
        mist(100 + i).save(OUT / f"mist_{i}.png")
    i = 0
    for kind in ("paper", "paper", "leaf", "leaf", "plastic", "receipt"):
        scrap(kind, 200 + i).save(OUT / f"debris_{i}.png")
        i += 1
    print("wrote", OUT)


if __name__ == "__main__":
    main()
