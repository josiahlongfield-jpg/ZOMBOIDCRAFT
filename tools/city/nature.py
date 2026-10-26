"""Textures for the county's trees and ground cover, made from asset packs Josiah bought by
tools/nature/*.py and kept as 64px PNGs in tools/nature/tex (the packs never enter the repo).

photo_textures.py calls register() with itself, adding each to its EXTRA table: the colours and
cut-out as drawn, a height map from their light and dark for the normal map (flat for leaves
and grass cards, which scatter light through instead).
"""
from pathlib import Path

import numpy as np
from PIL import Image

TEX = Path(__file__).resolve().parents[1] / "nature/tex"


def register(ns):
    P = ns.P

    def load(path):
        def make():
            a = np.array(Image.open(path).convert("RGBA"), np.float32)
            t = P.Tex(len(path.stem), (0, 0, 0))
            t.c = a[..., :3].copy()
            t.alpha = a[..., 3].copy()
            lum = t.c @ np.array([0.299, 0.587, 0.114], np.float32)
            t.h = np.clip(0.35 + lum / 255 * 0.6, 0, 1)
            t.smooth[:] = 0.12 if "leaves" in path.stem or "grass" in path.stem else 0.06
            t.poro[:] = 60
            if path.stem.startswith("gc_") or "leaves" in path.stem:
                # cut-out cards are thin: no bumps (they speckle under shaders), light shows through
                t.h = np.clip(0.45 + lum / 255 * 0.1, 0, 1) if "leaves" in path.stem else np.full_like(lum, 0.5)
                if "twigs" not in path.stem and "dead" not in path.stem:
                    t.sss = np.where(t.alpha > 0, 0.55, 0).astype(np.float32)
            return t
        return make

    for path in sorted(TEX.glob("*.png")):
        ns.EXTRA[path.stem] = load(path)
