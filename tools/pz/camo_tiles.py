"""The camo patterns (tools/items/gen_clothes.py CAMOS) as flat pictures, for a Zomboid survivor's clothes: a camo
piece takes the pattern laid over its own Zomboid texture's light and shade (client/survivor/SurvivorTextures). Our
own art, nothing of Zomboid's. Writes src/main/resources/assets/zomboidcraft/textures/survivor/camo/<name>.png.

    python3 tools/pz/camo_tiles.py
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/items'))
import gen_clothes as C  # noqa: E402

SIZE = 256
SPAN = 26.0  # model pixels across the picture: a Zomboid garment's texture covers about that much cloth


class Flat:
    def __init__(self):
        v, u = np.mgrid[0:SIZE, 0:SIZE] / SIZE * SPAN
        self.P = np.dstack([u, v, np.zeros_like(u)])
        self.shape = u.shape
        self.rng = np.random.default_rng(7)


def main():
    out = ROOT / 'src/main/resources/assets/zomboidcraft/textures/survivor/camo'
    out.mkdir(parents=True, exist_ok=True)
    for name, (ground, pattern) in C.CAMOS.items():
        f = Flat()
        c = np.broadcast_to(C.P.col(ground), (*f.shape, 3)).copy()
        if pattern:
            c = pattern(f, c)
        img = Image.fromarray((np.clip(c, 0, 1) * 255).astype('uint8'), 'RGB')
        img.save(out / f'{name}.png', optimize=True)
        print(name)


if __name__ == '__main__':
    main()
