"""Holster and sheath item models for ZomboidCraft's quick slots, built at body scale on the same frame as the belts
(tools/items/gen_belts.py: one unit is one skin pixel, the belt's bottom edge at y = 0, the front at +z), so worn they
sit on the hip by the same translation. The handgun or blade in them is drawn separately (WornGearLayer.hip), its grip
standing out of the top.

References: refs/items/belts/01-community-affairs-duty-belt (the black polymer duty holster on the right hip: a
moulded shell over the slide, a hood over the rear sight, a drop off the belt). Shoulder and thigh rigs and the sheaths
are added as their photos land in refs/items/{holsters,sheaths}.

    python3 tools/items/gen_holsters.py
"""
import sys
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
sys.path.insert(0, str(ROOT / 'tools/items'))
import model as M  # noqa: E402
import paint as P  # noqa: E402
import gen_bags as B  # noqa: E402
import gen_belts as G  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
SIDE = -1          # the gun side: holsters ride on -x, which is the survivor's right hip


def moulded(base='#1a1a1b'):
    """Moulded polymer shell: a soft sheen on the faces, edges rubbed lighter, a parting line along the middle."""
    def fn(f):
        c = P.polymer(f, base, 0.07)
        rub = np.clip(1 - f.edge / 0.18, 0, 1)
        c = c + rub[..., None] * 0.05
        if abs(f.n[0]) > 0.5:
            mid = np.abs(f.uv[..., 1] - (f.uv[..., 1].max() + f.uv[..., 1].min()) / 2) < 0.05
            c = np.where(mid[..., None], c * 0.75, c)
        return c
    return fn


def build_hip(m):
    x0 = G.HALF_X + G.STRAP            # outside the belt
    s = SIDE
    def X(a, b):
        return sorted((s * (x0 + a), s * (x0 + b)))
    # belt drop: the plate the shell hangs from, looped over the belt
    xa, xb = X(0.0, 0.25)
    m.box([xa, -1.2, -0.9], [xb, 1.9, 0.9], 'plate')
    xa, xb = X(-0.45, 0.0)
    m.box([xa, 1.6, -0.9], [xb, 1.9, 0.9], 'plate')                     # over the top of the belt
    # the shell over the slide, muzzle down, a little forward of the hip bone
    xa, xb = X(0.25, 1.35)
    m.box([xa, -3.6, -0.55], [xb, 0.9, 0.85], 'shell')
    xa, xb = X(0.35, 1.25)
    m.box([xa, -3.9, -0.45], [xb, -3.6, 0.75], 'shell')                 # rounded toe
    m.box([xa, -1.1, 0.85], [xb, 0.6, 1.35], 'shell')                    # trigger-guard bulge
    # the hood over the rear of the slide, swung down to lock the gun in, and its pivot
    xa, xb = X(0.2, 1.45)
    m.box([xa, 0.7, -0.75], [xb, 1.5, 0.15], 'hood')
    xa, xb = X(1.35, 1.5)
    m.box([xa, 0.2, -0.95], [xb, 0.55, -0.6], 'pivot')
    # release lever on the inner side, under the thumb
    xa, xb = X(0.1, 0.25)
    m.box([xa, 0.1, 0.0], [xb, 0.55, 0.55], 'pivot')


HOLSTERS = [
    ('hip_holster', build_hip, 0.4, 0.05, dict(
        shell=moulded('#262729'), hood=moulded('#1e1f21'), plate=moulded('#2a2b2d'), pivot=lambda f: P.steel(f, 0.4))),
]


def main():
    for name, build, wear, blood, mats in HOLSTERS:
        m = M.Model(name)
        build(m)
        m.shift(B.C - np.array([0, 0.8, 0]))      # the belts' frame, so it hangs at the waist the same way
        m.tidy()
        seed = zlib.crc32(name.encode()) % 1000
        patches = M.paint_model(m, B.worn_look(wear, blood, **mats), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/gear/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        disp = B.display(m)
        gui_rot = (15, 60, 0)
        sc, tr = M.fit(m, M.R_xyz(*gui_rot), 15.0)
        disp['gui'] = M.transform(gui_rot, tr, sc)
        M.dump(M.model_json(m, uvs, f'{NS}:item/gear/{name}', disp, gui_light='side'), ASSETS / f'models/item/gear/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/gear/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
