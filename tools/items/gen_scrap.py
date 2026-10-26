"""What a knocked-through wall leaves, and the torch that cuts steel: rubble, scrap metal and a propane torch, built
from boxes and painted per face with the weapon tooling (tools/weapons/model.py, paint.py).

Writes models/item/scrap/<name>.json, textures/item/scrap/<name>.png and items/<name>.json.

    python3 tools/items/gen_scrap.py
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
from gen_bags import worn_look  # noqa: E402
from gen_cases import held_display, merge_lang  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'
C = np.array([8.0, 8.0, 8.0])


# ================================================================== materials


def brick(f):
    """Fired clay: a red that varies brick to brick, dark flecks, a pale crust of old mortar along the edges."""
    c = P.plain(f, '#8e3b26', 0.2)
    c = P.mix(c, P.col('#6b2a1b'), np.clip((f.noise(0.9, 401, 3) - 0.45) * 2.5, 0, 1) * 0.6)
    c = np.where((P.white(f.shape, f.rng) > 0.94)[..., None], c * 0.55, c)
    crust = (f.edge < 0.22) & (f.noise(2.2, 402, 2) > 0.62)
    return np.where(crust[..., None], P.mix(c, P.col('#b9b2a2'), 0.75), c)


def concrete(f):
    """Grey poured concrete with gravel showing on the broken faces and a few rust-stained streaks."""
    c = P.plain(f, '#8c8a84', 0.14)
    stones = f.noise(5.0, 411, 2)
    c = np.where((stones > 0.66)[..., None], P.mix(c, P.col('#5e5a52'), 0.6), c)
    c = np.where((stones < 0.22)[..., None], P.mix(c, P.col('#b3afa5'), 0.5), c)
    stain = f.noise(0.7, 412, 3, (1, 0.2, 1)) > 0.72
    return np.where(stain[..., None], P.mix(c, P.col('#6e4a2c'), 0.35), c)


def rebar(f):
    c = P.steel(f, 0.32, tint='#6f6a64', brushed=False, spread=0.1)
    ribs = (np.sin(f.along() * 9.0) > 0.4)
    c = np.where(ribs[..., None], c * 0.75, c)
    return P.rust(f, c, 0.75, seed=413)


def mortar(f):
    return P.plain(f, '#a9a393', 0.16)


def sheet_paint(colour):
    """A scrap of painted steel sheet, chipped and rusting at the torn edges."""
    def fn(f):
        return P.rust(f, P.paint(f, colour, steel_base=0.38, chips=0.9), 0.45, seed=421)
    return fn


def galv(f):
    return P.rust(f, P.galvanised(f), 0.2, seed=422)


def rusty(f):
    return P.rust(f, P.steel(f, 0.4, spread=0.12), 0.85, seed=423)


def cylinder(f):
    """The propane cylinder: blue enamel, a plain white band where a label was, scuffed bare at the rims."""
    c = P.paint(f, '#1f4f9a', steel_base=0.5, chips=0.04, gloss=0.08)
    y = f.Y
    band = (y > 2.4) & (y < 5.2) & (np.abs(f.n[1]) < 0.5)
    lab = P.plain(f, '#d9d6cc', 0.06)
    lines = (((y - 2.4) * 2.2) % 1 < 0.12) & (y > 3.0) & (y < 4.7)
    lab = np.where(lines[..., None], lab * 0.7, lab)
    return np.where(band[..., None], lab, c)


def knob(f):
    return P.polymer(f, '#d8a617', 0.06)


def nozzle(f):
    return P.steel(f, 0.62, tint='#a7a29a', spread=0.14)


def burnt(f):
    """The burner tip, blued and blackened from the flame."""
    c = P.steel(f, 0.5, tint='#8a8f9a', spread=0.12)
    heat = np.clip((f.Y - 12.0) / 3.0, 0, 1)
    return P.mix(c, P.col('#2c2a33'), heat * 0.7 + (f.noise(3.0, 431, 2) - 0.5) * 0.2)


# ================================================================== builds
# Each sits on y = 0, about a hand across.


def build_rubble(m):
    # a double handful of broken wall: half bricks with mortar still on, lumps of concrete, a stub of rebar
    m.box([-3.6, 0, -2.0], [0.4, 1.9, 0.0], 'brick', rot=dict(axis='y', angle=22.5, origin=[-1.6, 0, -1.0]))
    m.box([-3.6, 1.9, -2.0], [-1.0, 2.25, 0.0], 'mortar', rot=dict(axis='y', angle=22.5, origin=[-1.6, 0, -1.0]))
    m.box([0.2, 0, 0.4], [3.4, 1.8, 2.2], 'brick', rot=dict(axis='y', angle=-22.5, origin=[1.8, 0, 1.3]))
    m.box([-2.0, 1.5, -0.6], [1.0, 3.0, 1.0], 'brick', rot=dict(axis='z', angle=22.5, origin=[-0.5, 2.2, 0.2]))
    m.box([0.6, 0, -2.8], [3.6, 2.6, -0.6], 'concrete', rot=dict(axis='y', angle=45, origin=[2.1, 0, -1.7]))
    m.box([1.2, 2.4, -2.4], [2.8, 3.3, -1.0], 'concrete')
    m.box([-2.8, 0, 0.6], [-0.6, 1.3, 2.8], 'concrete', rot=dict(axis='y', angle=22.5, origin=[-1.7, 0, 1.7]))
    m.box([-1.0, 0, -0.6], [0.6, 0.8, 0.8], 'concrete')
    m.seg((1.4, 2.0), (4.6, 4.6), 0.4, 0.4, 'rebar', z=-1.7)
    for x, z, s in ((-3.9, 2.4, 0.6), (3.7, 2.6, 0.5), (-0.4, -3.0, 0.7), (2.4, 2.9, 0.4)):    # grit
        m.box([x, 0, z], [x + s, s * 0.7, z + s], 'mortar')


def build_scrap(m):
    # a few cut and bent pieces: a painted panel off a car or cabinet, a galvanised strip, a rusted angle bracket, a
    # length of pipe
    m.box([-4.2, 0, -3.0], [2.2, 0.3, 1.6], 'paint', rot=dict(axis='y', angle=22.5, origin=[-1.0, 0, -0.7]))
    m.box([1.8, 0, -3.0], [2.1, 2.6, 1.6], 'paint', rot=dict(axis='y', angle=22.5, origin=[-1.0, 0, -0.7]))    # bent up
    m.box([-3.6, 0.3, 0.6], [3.8, 0.55, 2.2], 'galv', rot=dict(axis='y', angle=-22.5, origin=[0.1, 0.3, 1.4]))
    for x in (-2.4, -0.6, 1.2, 3.0):
        m.box([x, 0.55, 1.05], [x + 0.35, 0.62, 1.4], 'galv', rot=dict(axis='y', angle=-22.5, origin=[0.1, 0.3, 1.4]))
    m.box([-1.6, 0.55, -1.4], [1.6, 0.85, -0.2], 'rusty', rot=dict(axis='y', angle=45, origin=[0.0, 0.55, -0.8]))
    m.box([-1.6, 0.55, -1.4], [-1.3, 2.2, -0.2], 'rusty', rot=dict(axis='y', angle=45, origin=[0.0, 0.55, -0.8]))
    m.rod(-2.6, 2.6, 0.55, 'pipe', cx=0.9, cz=-2.2, axis=0)
    m.box([-2.8, 0.3, -2.75], [-2.6, 1.4, -1.65], 'pipe')                                    # its torn end


def build_torch(m):
    # a hand torch on a 400 g cylinder: about 3.3" across and 8" tall, at an inch a unit; the brass regulator with its
    # yellow knob screwed on top, and the burner tube cranked forward
    m.rod(0.0, 7.2, 1.65, 'cylinder')
    m.rod(7.2, 7.8, 1.2, 'cylinder')                                                         # the shoulder
    m.rod(-0.25, 0.3, 1.55, 'rim')                                                           # the foot ring
    m.rod(7.8, 8.3, 0.55, 'brass')                                                           # the valve stem
    m.box([-0.75, 8.3, -0.75], [0.75, 9.9, 0.75], 'brass')                                   # regulator body
    m.rod(0.75, 1.55, 0.5, 'knob', cx=9.1, cz=0.0, axis=0)                                   # the knob on its side
    for z in (-0.52, 0.52):                                                                  # the knob's ridges
        m.box([0.8, 8.7, z - 0.05], [1.5, 9.5, z + 0.05], 'knob')
    m.box([-0.35, 9.9, -0.35], [0.35, 10.5, 0.35], 'nozzle')                                 # the burner's elbow
    m.seg((0.0, 10.2), (4.6, 13.8), 0.5, 0.5, 'nozzle')                                    # the burner tube
    m.seg((4.3, 13.5), (5.7, 14.6), 0.9, 0.9, 'burnt')                                     # the flared tip


SCRAPS = [
    ('rubble', build_rubble, 0.6, 0.0, 9.0, dict(brick=brick, concrete=concrete, rebar=rebar, mortar=mortar)),
    ('scrap_metal', build_scrap, 0.7, 0.0, 9.0, dict(paint=sheet_paint('#5a6b4a'), galv=galv, rusty=rusty,
                                                      pipe=lambda f: P.rust(f, P.steel(f, 0.36, spread=0.1), 0.5))),
    ('propane_torch', build_torch, 0.3, 0.0, 10.0, dict(cylinder=cylinder, rim=lambda f: P.steel(f, 0.45, spread=0.1),
                                                        brass=P.brass, knob=knob, nozzle=nozzle, burnt=burnt)),
]

NAMES = {'rubble': 'Rubble', 'scrap_metal': 'Scrap Metal', 'propane_torch': 'Propane Torch'}


def main():
    merge_lang({f'item.{NS}.{k}': v for k, v in NAMES.items()})
    for name, build, wear, blood, held, mats in SCRAPS:
        m = M.Model(name)
        build(m)
        lo, hi = m.bounds()
        m.shift(C - (lo + hi) / 2)
        m.tidy()
        seed = zlib.crc32(name.encode()) % 1000
        patches = M.paint_model(m, worn_look(wear, blood, **mats), wear, seed)
        img, uvs, T = M.pack(patches)
        tex = ASSETS / f'textures/item/scrap/{name}.png'
        tex.parent.mkdir(parents=True, exist_ok=True)
        M.save_png(img, tex)
        M.dump(M.model_json(m, uvs, f'{NS}:item/scrap/{name}', held_display(m, held), gui_light='side'),
               ASSETS / f'models/item/scrap/{name}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/scrap/{name}'}}, ASSETS / f'items/{name}.json')
        print(f'{name}: {len(m.parts)} boxes, {T}px')


if __name__ == '__main__':
    main()
