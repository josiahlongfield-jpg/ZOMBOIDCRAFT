"""Ground cover blocks from Josiah's Grass Groundcover Pack 01.

    python3 tools/nature/groundcover.py PACK_DIR [--sheet OUT.png]

PACK_DIR holds the pack's TEXTURES folder (T_GroundCover_01_d.tga and _opacity.tga). The pack's
own cards are cut from its atlas: grass clumps and saplings become crossed sprites a block tall,
tall grass and reeds two blocks tall (a lower and an upper block), clover, leaves and dry twigs
flat litter on the ground. Each is graded to the county's colours, green and dry, and written to
tools/nature/tex as gc_*.png with its block in tools/city/blocks.json; the originals stay out of
the repo. CountyGen swaps vanilla grass, ferns and dry grass for these (see county/Cover.java).
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX_OUT = ROOT / "tools/nature/tex"
BLOCKS = ROOT / "tools/city/blocks.json"
N = 64

# the atlas is 4096 square; boxes are in a 1024 view of it (x0, y0, x1, y1)
CLUMPS = [  # short grass: height in blocks
    ((200, 20, 310, 240), 0.78), ((330, 40, 418, 235), 0.74), ((425, 15, 560, 225), 0.7),
    ((565, 10, 700, 245), 0.8), ((690, 10, 905, 245), 0.84), ((900, 10, 1022, 245), 0.8),
    ((255, 255, 475, 478), 0.86), ((478, 250, 612, 482), 0.82), ((615, 255, 795, 482), 0.84),
    ((805, 250, 1010, 495), 0.86), ((500, 490, 712, 632), 0.55), ((735, 468, 872, 612), 0.6),
]
TALL = [  # two blocks high: plumed grass and reeds
    ((0, 0, 215, 445), 1.7), ((640, 605, 862, 1022), 1.45), ((858, 595, 1022, 1022), 1.5),
]
SPRIGS = [((562, 648, 628, 830), 0.7), ((588, 835, 662, 1022), 0.72), ((878, 500, 1022, 580), 0.45)]
FLAT = {  # litter lying on the ground
    "clover": [(160, 690, 272, 752), (320, 700, 445, 862), (450, 750, 582, 892)],
    "leaves": [(10, 450, 240, 690), (230, 440, 340, 640), (370, 470, 500, 620)],
    "twigs": [(95, 750, 340, 1022), (350, 860, 600, 1018), (0, 600, 140, 800)],
}
GREEN = [(112, 134, 62), (122, 142, 68), (102, 124, 58)]
DRY = [(184, 160, 102), (162, 142, 86)]
TWIG = (112, 96, 74)


def load(pack):
    tex = Path(pack) / "TEXTURES"
    rgb = np.array(Image.open(tex / "T_GroundCover_01_d.tga").convert("RGB"), np.float32)
    a = np.array(Image.open(tex / "T_GroundCover_01_opacity.tga").convert("L"), np.float32)
    return np.dstack([rgb, a])


def crop(atlas, box):
    x0, y0, x1, y1 = (v * 4 for v in box)
    c = atlas[y0:y1, x0:x1]
    ys, xs = np.nonzero(c[..., 3] > 100)
    return c[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def shrink(img, w, h):
    """Premultiplied resize, so the card's background colour never bleeds into the edges."""
    a = img[..., 3:4] / 255
    pre = np.dstack([img[..., :3] * a, img[..., 3:4]])
    out = np.array(Image.fromarray(pre[..., :3].astype(np.uint8)).resize((w, h), Image.BOX), np.float32)
    al = np.array(Image.fromarray(pre[..., 3].astype(np.uint8)).resize((w, h), Image.BOX), np.float32)
    rgb = out / np.maximum(al[..., None] / 255, 0.05)
    return np.dstack([np.clip(rgb, 0, 255), al])


def grade(img, colour, rng):
    """Channel gains to the target colour keep the card's own hues (seed heads, red leaf tips);
    every blade is lightened or darkened a little so a clump isn't one flat tone."""
    m = img[..., 3] > 0
    if not m.any():
        return img
    out = img.copy()
    # half way to grey first: gains on a strongly tinted card push odd hues (pink seed heads) too far
    lum = out[..., :3] @ np.array([0.299, 0.587, 0.114], np.float32)
    out[..., :3] = lum[..., None] + (out[..., :3] - lum[..., None]) * 0.5
    mean = out[m][:, :3].mean(0)
    out[..., :3] *= np.array(colour, np.float32) / np.maximum(mean, 1)
    out[..., :3] *= rng.uniform(0.92, 1.06)
    lum = out[..., :3] @ np.array([0.299, 0.587, 0.114], np.float32)
    out[..., :3] = lum[..., None] + (out[..., :3] - lum[..., None]) * 0.85
    out[..., :3] = np.clip(out[..., :3], 0, 255)
    out[..., 3] = np.where(img[..., 3] > 110, 255, 0)
    out[out[..., 3] == 0, :3] = 0
    return out


def standing(card, height, rows, shift=0.0):
    """A card standing on the block's floor, `height` blocks tall, on a canvas `rows` blocks high."""
    h = int(round(height * N))
    w = int(round(card.shape[1] * h / card.shape[0]))
    if w > N:
        h, w = int(h * N / w), N
    s = shrink(card, max(w, 1), max(h, 1))
    canvas = np.zeros((rows * N, N, 4), np.float32)
    x0 = int(np.clip((N - w) // 2 + shift * N, 0, N - w))
    canvas[rows * N - h:, x0:x0 + w] = s
    return canvas


def lying(card, rng, fill):
    """Litter seen from above: turned at random and as big as `fill` of the block."""
    im = Image.fromarray(card.astype(np.uint8), "RGBA").rotate(rng.uniform(0, 360), Image.BILINEAR, expand=True)
    c = np.array(im, np.float32)
    ys, xs = np.nonzero(c[..., 3] > 100)
    c = c[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    k = fill * N / max(c.shape[:2])
    w, h = max(1, int(c.shape[1] * k)), max(1, int(c.shape[0] * k))
    s = shrink(c, w, h)
    canvas = np.zeros((N, N, 4), np.float32)
    x0, y0 = (N - w) // 2, (N - h) // 2
    canvas[y0:y0 + h, x0:x0 + w] = s
    return canvas


def save(name, img):
    TEX_OUT.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img.astype(np.uint8), "RGBA").save(TEX_OUT / f"{name}.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pack")
    ap.add_argument("--sheet")
    args = ap.parse_args()
    atlas = load(args.pack)
    rng = np.random.default_rng(42)
    specs, made = {}, []

    def cross(name, img, title):
        save(name, img)
        specs[name] = {"model": "cross", "tex": name, "render": "cutout", "title": title, "sound": "GRASS",
                       "drops": "", "offset": True}
        made.append(name)

    for i, (box, h) in enumerate(CLUMPS):
        card = crop(atlas, box)
        cross(f"gc_grass_{i + 1}", grade(standing(card, h, 1, rng.uniform(-0.12, 0.12)), GREEN[i % 3], rng), "Grass")
        if i % 2 == 0:
            cross(f"gc_grass_dry_{i // 2 + 1}", grade(standing(card, h * 0.9, 1), DRY[i // 2 % 2], rng), "Dry Grass")
    for i, (box, h) in enumerate(TALL):
        card = crop(atlas, box)
        for k, col in (("", GREEN[i % 3]), ("_dry", DRY[0])):
            img = grade(standing(card, h, 2), col, rng)
            title = "Tall Dry Grass" if k else "Tall Grass"
            cross(f"gc_tall{k}_{i + 1}_upper", img[:N], title)
            cross(f"gc_tall{k}_{i + 1}_lower", img[N:], title)
    for i, (box, h) in enumerate(SPRIGS):
        cross(f"gc_sprig_{i + 1}", grade(standing(crop(atlas, box), h, 1), GREEN[(i + 1) % 3], rng), "Seedling")
    for kind, boxes in FLAT.items():
        for i, box in enumerate(boxes):
            name = f"gc_{kind}_{i + 1}"
            col = TWIG if kind == "twigs" else GREEN[i % 3] if kind == "clover" else (96, 88, 48)
            save(name, grade(lying(crop(atlas, box), rng, 0.95 if kind == "twigs" else 0.85), col, rng))
            specs[name] = {"model": "boxes", "render": "cutout", "boxes": [[0, 0, 0, 16, 0.25, 16, name]],
                           "rotate": "facing", "decor": True, "title": {"clover": "Clover", "leaves": "Weeds",
                                                                           "twigs": "Fallen Twigs"}[kind],
                           "sound": "GRASS", "drops": ""}
            made.append(name)

    data = json.loads(BLOCKS.read_text())
    blocks = {k: v for k, v in data["blocks"].items() if not k.startswith("gc_")}
    blocks.update(specs)
    data["blocks"] = blocks
    BLOCKS.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} ground cover blocks")
    if args.sheet:
        tiles = [np.array(Image.open(TEX_OUT / f"{n}.png").convert("RGBA"), np.float32) for n in made]
        cols = 12
        rows = (len(tiles) + cols - 1) // cols
        sheet = np.full((rows * (N + 4), cols * (N + 4), 3), (150, 160, 170), np.float32)
        for k, t in enumerate(tiles):
            y, x = k // cols * (N + 4), k % cols * (N + 4)
            a = t[..., 3:4] / 255
            sheet[y:y + N, x:x + N] = t[..., :3] * a + sheet[y:y + N, x:x + N] * (1 - a)
        Image.fromarray(sheet.astype(np.uint8)).resize((cols * (N + 4) * 3, rows * (N + 4) * 3), Image.NEAREST).save(args.sheet)


if __name__ == "__main__":
    main()
