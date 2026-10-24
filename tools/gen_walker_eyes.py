#!/usr/bin/env python3
"""Glowing red eye masks for the walker skins (look.glowingEyes).

Finds the white eyes painted on each skin's face and writes a matching texture that is clear
everywhere else, with a red glow over the eyes: hot in the middle, darker at the rim. The game
draws it full-bright over the skin, so the eyes burn red in the dark. A skin missing an eye
gets one glowing eye.

    python3 tools/gen_walker_eyes.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

ENTITY = Path(__file__).resolve().parents[1] / "src/main/resources/assets/zomboidcraft/textures/entity"
HOT = np.array([255, 38, 22], np.float32)
RIM = np.array([168, 4, 2], np.float32)
# outfits still on the generated skins until their painted art arrives
PROCEDURAL = {"jogger", "patient", "security", "survivor"}


def mask(skin, procedural):
    rgba = np.asarray(skin.convert("RGBA"), np.float32)
    s = skin.width // 64
    out = np.zeros_like(rgba)
    # the face: front of the head, rows between the brow and the mouth
    y0, y1, x0, x1 = 8 * s + 2 * s, 8 * s + int(5.5 * s), 8 * s, 16 * s
    face = rgba[y0:y1, x0:x1, :3]
    lo, hi = face.min(-1), face.max(-1)
    if procedural:
        # gen_hd_skins.py paints milky eyes as ellipses at (8.5, 14.5) and (22.5, 14.5) of the
        # 32px face; a lost eye is a dark hole there, so it stays dark
        yy, xx = np.mgrid[y0 - 8 * s:y1 - 8 * s, 0:8 * s] + 0.5
        k = s / 4
        eye = np.zeros(lo.shape, bool)
        for cx in (8.5, 22.5):
            eye |= ((xx - cx * k) / (2.6 * k)) ** 2 + ((yy - 14.5 * k) / (1.5 * k)) ** 2 <= 1
        eye &= lo > 70
    else:
        # the painted skins have white eyes: start from the whitest pixels, then take in the
        # greyer whites around them, so the whole eye glows and not just its brightest row
        pale = (hi - lo < 70)
        eye = (lo > max(100, 0.78 * lo.max(), np.median(lo) + 40)) & pale
        for _ in range(3):
            grown = np.pad(eye, 1)
            grown = grown[:-2, 1:-1] | grown[2:, 1:-1] | grown[1:-1, :-2] | grown[1:-1, 2:]
            eye |= grown & pale & (lo > max(110, 0.55 * lo.max()))
    if not eye.any():
        return None
    # inner pixels (all four neighbours are eye too) get the hot colour
    pad = np.pad(eye, 1)
    inner = eye & pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
    if not inner.any():  # tiny 64px eyes: brightest pixels are the hot ones
        inner = eye & (lo >= np.percentile(lo[eye], 50))
    col = np.where(inner[..., None], HOT, RIM)
    region = out[y0:y1, x0:x1]
    region[..., :3] = np.where(eye[..., None], col, 0)
    region[..., 3] = np.where(eye, 255, 0)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def shrink(m, size):
    """The 64px skins come from the HD ones, so their eyes do too: a texel glows if half its block does."""
    a = np.asarray(m, np.float32)
    k = a.shape[0] // size
    blocks = a.reshape(size, k, size, k, 4)
    on = blocks[..., 3] > 0
    share = on.mean((1, 3))
    rgb = (blocks[..., :3] * on[..., None]).sum((1, 3)) / np.maximum(on.sum((1, 3)), 1)[..., None]
    out = np.zeros((size, size, 4), np.float32)
    out[..., :3] = rgb
    out[..., 3] = np.where(share >= 0.4, 255, 0)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def main():
    for d in ("walker_eyes_hd", "walker_eyes"):
        (ENTITY / d).mkdir(exist_ok=True)
    count = 0
    for p in sorted((ENTITY / "walker_hd").glob("*.png")):
        if p.stem.endswith(("_n", "_s")):
            continue
        m = mask(Image.open(p), p.stem.split("_")[0] in PROCEDURAL)
        if m is None:
            print("no eyes found:", p.name)
            continue
        m.save(ENTITY / "walker_eyes_hd" / p.name, optimize=True)
        if p.stem.endswith("_0"):
            shrink(m, 64).save(ENTITY / "walker_eyes" / (p.stem[:-2] + ".png"), optimize=True)
        count += 1
    print("eye masks:", count)


if __name__ == "__main__":
    main()
