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
    """A ragged puff of smoke or haze, used by the skyline's smoke plumes.

    Shader packs (Photon) throw away every pixel fainter than about a tenth, so there's no soft
    fade to the rim (it would draw as a hard disc) and no scattered specks (they draw as grain):
    the puff is smooth inside and its outline is torn by noise, so the hard edge reads as a wisp.
    """
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r2 = x * x + (y * 1.3) ** 2
    fall = np.clip(1 - np.sqrt(r2), 0, 1)
    tex = 0.5 * value_noise(rng, n, 3) + 0.3 * value_noise(rng, n, 5) + 0.2 * value_noise(rng, n, 9)
    m = fall * (0.4 + 1.2 * tex)
    a = np.where(m > 0.25, 0.45 + 0.55 * np.clip((m - 0.25) / 0.6, 0, 1), 0)
    rgba = np.zeros((n, n, 4), np.uint8)
    rgba[..., :3] = 235
    rgba[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(rgba)


def ground_fog(seed, n=256):
    """The ground mist's sheet: soft fog that repeats seamlessly, with gaps where it thins.

    Never fainter than about a third where it's there at all (times the sheet's own third, that
    stays over the tenth a shader pack throws away); where it would thin past that it opens
    into a gap instead, so the sheets wisp rather than fade.
    """
    rng = np.random.default_rng(seed)
    f = np.fft.fftfreq(n)
    fx, fy = np.meshgrid(f, f)
    k = np.sqrt(fx * fx + fy * fy)
    k[0, 0] = 1
    spectrum = (rng.normal(size=(n, n)) + 1j * rng.normal(size=(n, n))) / k ** 1.9
    spectrum[0, 0] = 0
    spectrum[k > 0.08] *= 0.25  # mostly broad swirls, a little fine grain
    noise = np.real(np.fft.ifft2(spectrum))
    noise = (noise - noise.mean()) / noise.std()
    v = np.clip((noise + 0.75) / 2.2, 0, 1)  # about four fifths covered
    a = np.where(v > 0, 0.34 + 0.66 * v, 0)
    rgba = np.zeros((n, n, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(rgba)


def glint(seed, n=16):
    """A raindrop caught in lamp light: a short bright streak, solid (shader packs drop faint pixels)."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x = n // 2 - 1 + rng.randint(0, 1)
    top, bottom = rng.randint(0, 3), n - 1 - rng.randint(0, 3)
    d.line([(x, top), (x, bottom)], fill=(255, 255, 255, 255))
    d.point((x, bottom), fill=(255, 255, 255, 255))
    d.line([(x + 1, top + 3), (x + 1, bottom - 2)], fill=(255, 255, 255, 140))
    return img


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
    effect = OUT.parent / "effect"
    effect.mkdir(parents=True, exist_ok=True)
    ground_fog(500).save(effect / "ground_fog.png")
    for i in range(3):
        glint(300 + i).save(OUT / f"glint_{i}.png")
    i = 0
    for kind in ("paper", "paper", "leaf", "leaf", "plastic", "receipt"):
        scrap(kind, 200 + i).save(OUT / f"debris_{i}.png")
        i += 1
    print("wrote", OUT)


if __name__ == "__main__":
    main()
