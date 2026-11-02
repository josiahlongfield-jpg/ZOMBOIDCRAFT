"""Placed decor blocks (com.zomboidcraft.decor.DecorBlock): a block model for every look of every built piece, an ON
model beside it whose glow parts give light_emission 15, and the blockstate that picks them by facing, on and look.

The models are the same builders as the loot items (wave5decor.looks), painted into their own sheets
(textures/block/decor/<sheet>.png). A piece with fewer built looks than the block's 16 repeats them. The item in the
hand is the look-0 loot item model (gen_loot.py / mesh.py).

    python3 tools/items/loot/decor_models.py
"""
import copy
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import gen_loot as G  # noqa: E402
import wave5decor as D  # noqa: E402

kit, M = G.kit, G.M
NS = G.NS
ASSETS = G.ASSETS
LOOKS = 16                       # DecorBlock.LOOKS
FACING = {'south': 0, 'west': 90, 'north': 180, 'east': 270}   # the model's front is +z; FACING is toward the placer
DECOR = Path(__file__).resolve().parents[3] / 'src/main/resources/data/zomboidcraft/decor.json'


# what else lights up when a piece is ON: day-glo posters under a black light (the even looks), sun through stained glass
LIGHTS = {'glows': ('print',), 'coloured_light': ('glasspane',)}


def lit(m, special=None, k=0):
    """The ON copy: every glow part lights itself."""
    extra = LIGHTS.get(special, ()) if special != 'glows' or k % 2 == 0 else ()
    out = copy.copy(m)
    out.parts = [dict(p, light=15) if p.get('mat') in D.GLOW or p.get('mat') in extra else p for p in m.parts]
    return out


def px_for(m):
    """Texels per unit for a placed piece: about half the loot items' density (they all go in the block atlas, a
    hundred pieces with up to sixteen looks each); lettering keeps its own finer '_px'."""
    lo, hi = m.bounds()
    return int(np.clip(round(32.0 / max(hi - lo)), 3, 8))


def main():
    G.px_for = px_for
    specs = json.loads(DECOR.read_text())
    looks = {i: es for i, es in D.looks(G.it).items() if specs.get(i, {}).get('special') != 'hand_truck'}  # a tool, no block
    names, owner = [], {}
    for i, es in looks.items():
        if i not in specs:
            raise SystemExit(f'{i}: not in decor.json')
        for k, e in enumerate(es[:LOOKS]):
            n = f'{i}__{k}'
            G.ITEMS[n] = e
            names.append(n)
            owner[n] = (i, k)
    for si, (gn, models, idx, img, uvs, T) in enumerate(G.sheets(names)):
        sheet = 'decor' if si == 0 else f'decor_{si + 1}'
        tex = f'block/decor/{sheet}'
        kit.save_textures(img, ASSETS / f'textures/{tex}.png')
        for n, m, mi in zip(gn, models, idx):
            lo, hi = m.bounds()
            # standing on the block's floor, centred across it
            shift = np.array([8.0 - (lo[0] + hi[0]) / 2, -lo[1], 8.0 - (lo[2] + hi[2]) / 2])
            i, k = owner[n]
            for suffix, mm in (('', m), ('_on', lit(m, specs[i].get('special'), k))):
                M.dump({'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                        'elements': kit.elements(mm, mi, uvs, shift)}, ASSETS / f'models/block/decor/{n}{suffix}.json')
        print(f'{sheet}: {len(gn)} looks, {T}px')
    for i, es in looks.items():
        built = min(len(es), LOOKS)
        variants = {}
        for face, y in FACING.items():
            for on in ('false', 'true'):
                for look in range(LOOKS):
                    v = {'model': f'{NS}:block/decor/{i}__{look % built}{"_on" if on == "true" else ""}'}
                    if y:
                        v['y'] = y
                    variants[f'facing={face},look={look},on={on}'] = v
        M.dump({'variants': variants}, ASSETS / f'blockstates/decor_{i}.json')
    print(f'{len(looks)} decor blocks, {len(names)} looks')


if __name__ == '__main__':
    main()
