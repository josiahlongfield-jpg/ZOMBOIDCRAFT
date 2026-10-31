"""The reloading bench and what goes through it (Josiah 2026-10-31: a press found in gun shops, hunters' garages and
police stations; spent brass from every shot; powder, primers and bullet heads as loot).

python3 tools/weapons/reloading.py

From refs/items/reloading: a red cast O-frame single-stage press bolted to the bench's front edge, its ram housing
and steel toggle links under the top and a long lever with a black ball knob; a powder measure with a smoked
hopper, a scale, a loading block of brass and a primer tray on a plain garage bench with jugs and a box on its shelf.
Spent brass and fired hulls as on a range floor. Brands and logos are left off.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import model as M  # noqa: E402
import paint as P  # noqa: E402
import weapons as W  # noqa: E402

NS, ASSETS, C = W.NS, W.ASSETS, W.C

BRASS = lambda f: P.brass(f)
RED = lambda f: P.paint(f, '#a8261e', chips=0.25)
STEEL = lambda f: P.steel(f, 0.6)
BLACK = lambda f: P.polymer(f, '#1b1b1d')


# ---------------------------------------------------------------- the bench (a block)

def build_bench(m):
    wood = 'top'
    m.box([0.4, 13.6, 0.8], [15.6, 15.0, 15.2], wood)                 # the top, two-by plank
    m.box([0.6, 12.2, 1.0], [15.4, 13.6, 1.8], 'frame')               # front apron
    m.box([0.6, 12.2, 14.2], [15.4, 13.6, 15.0], 'frame')             # back apron
    for x in (0.8, 13.8):
        for z in (1.0, 13.6):
            m.box([x, 0.0, z], [x + 1.4, 12.2, z + 1.4], 'frame')       # four-by legs
    m.box([1.4, 3.0, 2.0], [14.6, 3.8, 14.4], 'shelf')                # lower shelf
    m.box([1.4, 3.8, 13.6], [14.6, 4.6, 14.4], 'frame')               # its back lip
    # on the shelf: two powder jugs and a carton of brass
    jug(m, 3.8, 3.8, 9.5, 'jug_black')
    jug(m, 6.6, 3.8, 10.2, 'jug_grey')
    m.box([9.6, 3.8, 6.0], [13.8, 7.2, 12.6], 'card')
    m.box([9.5, 7.0, 5.9], [13.9, 7.4, 12.7], 'card')
    # the press: plate bolted to the top at the front edge, the O-frame, die and lock ring on top
    m.box([2.6, 15.0, 1.2], [8.4, 15.5, 5.0], 'red')
    for x, z in ((3.0, 1.6), (8.0, 1.6), (3.0, 4.6), (8.0, 4.6)):
        m.box([x - 0.35, 15.5, z - 0.35], [x + 0.35, 15.8, z + 0.35], 'steel')
    m.box([3.6, 15.5, 2.0], [7.4, 17.0, 3.6], 'red')                  # frame foot over the ram
    m.box([3.6, 17.0, 2.2], [4.7, 22.0, 3.4], 'red')                  # left pillar
    m.box([6.3, 17.0, 2.2], [7.4, 22.0, 3.4], 'red')                  # right pillar
    m.box([3.6, 22.0, 2.0], [7.4, 23.2, 3.6], 'red')                  # top bar
    m.rod(17.0, 18.0, 0.45, 'steel', cx=5.5, cz=2.8)                  # ram and shell holder
    m.rod(23.2, 26.2, 0.42, 'steel', cx=5.5, cz=2.8)                  # die body
    m.rod(23.4, 24.2, 0.62, 'chrome', cx=5.5, cz=2.8)                 # knurled lock ring
    m.rod(26.2, 26.9, 0.26, 'steel', cx=5.5, cz=2.8)                  # decapping stem
    # under the top: ram housing, toggle links down to the lever's pivot
    m.box([4.6, 10.4, 2.2], [6.4, 13.6, 3.6], 'red')
    m.box([4.4, 9.8, 1.9], [4.7, 11.6, 3.9], 'steel')
    m.box([6.3, 9.8, 1.9], [6.6, 11.6, 3.9], 'steel')
    m.box([4.2, 9.2, 0.9], [7.6, 10.2, 3.0], 'red')                  # lever's yoke
    # the lever: up past the front edge, a ball knob on top
    m.box([7.4, 9.4, 0.7], [8.2, 10.0, 1.3], 'red')
    m.rod(9.4, 23.4, 0.34, 'red', cx=8.1, cz=1.0)
    m.box([7.5, 23.4, 0.4], [8.7, 24.6, 1.6], 'knob')
    m.box([7.7, 23.2, 0.6], [8.5, 24.8, 1.4], 'knob')
    # powder measure on its stand: smoked hopper over a steel body with a drop tube
    m.box([10.0, 15.0, 2.0], [12.6, 15.4, 4.6], 'steel')
    m.rod(15.4, 17.6, 0.3, 'steel', cx=11.3, cz=3.3)
    m.box([10.4, 17.6, 2.6], [12.2, 19.2, 4.0], 'measure')
    m.rod(19.2, 23.0, 0.75, 'hopper', cx=11.3, cz=3.3)
    m.rod(23.0, 23.5, 0.82, 'jug_black', cx=11.3, cz=3.3)
    m.box([12.2, 18.0, 3.0], [13.4, 18.4, 3.6], 'steel')              # its charge arm
    # scale with pan, a loading block of brass and a primer tray further back
    m.box([10.4, 15.0, 7.0], [14.4, 15.8, 10.4], 'scale')
    m.box([11.0, 15.8, 9.0], [12.4, 15.95, 10.0], 'screen')
    m.rod(15.8, 16.2, 0.8, 'chrome', cx=13.2, cz=8.0)
    m.box([1.6, 15.0, 7.6], [6.6, 15.9, 11.0], 'block')
    for i in range(5):
        for j in range(3):
            x, z = 2.2 + i * 0.95, 8.2 + j * 1.0
            m.rod(15.9, 16.9, 0.22, 'brass', cx=x, cz=z)
    m.box([7.2, 15.0, 7.8], [9.6, 15.35, 10.4], 'tray')
    m.box([1.4, 15.0, 12.0], [5.0, 16.4, 14.4], 'card_ammo')          # a box of finished rounds
    m.box([6.0, 15.0, 12.4], [9.8, 15.5, 14.6], 'manual')             # the reloading manual, lying open
    # pegboard up the back with a die shelf, calipers, a funnel and a case trimmer hung on it
    m.box([0.6, 15.0, 15.0], [15.4, 27.0, 15.4], 'peg')
    m.box([0.4, 26.6, 14.8], [15.6, 27.2, 15.6], 'frame')
    m.box([1.4, 21.0, 13.4], [9.4, 21.4, 15.0], 'frame')               # die shelf
    for i, c in enumerate(('die_green', 'die_red', 'die_green', 'die_blue')):
        m.box([1.8 + i * 1.9, 21.4, 13.7], [3.4 + i * 1.9, 22.6, 14.8], c)
    m.box([10.6, 19.0, 14.7], [11.0, 25.0, 15.0], 'steel')             # calipers
    m.box([10.2, 24.0, 14.7], [12.0, 24.4, 15.0], 'steel')
    m.box([12.6, 22.4, 14.4], [14.2, 23.6, 15.0], 'funnel')
    m.box([13.1, 21.4, 14.6], [13.7, 22.4, 15.0], 'funnel')
    m.box([2.0, 16.6, 14.5], [6.0, 18.6, 15.0], 'card_ammo')           # a box of rounds on a hook


def jug(m, x, y, z, mat):
    m.box([x - 1.1, y, z - 1.5], [x + 1.1, y + 3.4, z + 1.5], mat)
    m.box([x - 0.9, y + 3.4, z - 1.3], [x + 0.9, y + 3.8, z + 1.3], mat)
    m.rod(y + 3.8, y + 4.3, 0.4, 'cap', cx=x, cz=z - 0.6)


BENCH_LOOK = {
    'top': lambda f: P.grime(f, P.wood(f, '#b48a5a', '#7d5a36', ring=3.2, lacquer=0.0), 0.5),
    'frame': lambda f: P.grime(f, P.wood(f, '#a47c4e', '#6f4f2e', ring=2.8, lacquer=0.0), 0.4),
    'shelf': lambda f: P.grime(f, P.plain(f, '#8f7552', 0.16), 0.5),
    'red': RED, 'knob': BLACK, 'steel': STEEL, 'chrome': lambda f: P.chrome(f), 'brass': BRASS,
    'jug_black': lambda f: P.polymer(f, '#232325'), 'jug_grey': lambda f: P.polymer(f, '#8c8f8f'),
    'cap': lambda f: P.polymer(f, '#a32a22'),
    'card': lambda f: P.grime(f, P.plain(f, '#9c7d55', 0.14), 0.4),
    'card_ammo': lambda f: W.carton(f, '#5a3b2e'),
    'measure': lambda f: P.paint(f, '#2d3a2e', chips=0.2),
    'hopper': lambda f: P.plain(f, '#5d6a6c', 0.08),
    'scale': lambda f: P.polymer(f, '#2a2c2e'),
    'screen': lambda f: P.plain(f, '#7f9a72', 0.05),
    'block': lambda f: P.polymer(f, '#3b4f6b'),
    'tray': lambda f: P.plain(f, '#b9b2a3', 0.06),
    'manual': lambda f: P.grime(f, P.plain(f, '#d9cfb4', 0.08), 0.3),
    'peg': lambda f: pegboard(f),
    'die_green': lambda f: P.polymer(f, '#2f6b3a'), 'die_red': lambda f: P.polymer(f, '#8f2a22'),
    'die_blue': lambda f: P.polymer(f, '#2a4a7a'), 'funnel': lambda f: P.polymer(f, '#c9c2a8'),
}


def pegboard(f):
    c = P.grime(f, P.plain(f, '#8a6a48', 0.12), 0.4)
    holes = (np.abs((f.X % 1.0) - 0.5) < 0.12) & (np.abs((f.Y % 1.0) - 0.5) < 0.12)
    return np.where(holes[..., None], P.col('#2a2018'), c)
BENCH_PBR = dict(top='raw_wood', frame='raw_wood', shelf='raw_wood', red='painted', knob='polymer', steel='steel',
                 chrome='chrome', brass='brass', jug_black='polymer', jug_grey='polymer', cap='polymer', card='card',
                 card_ammo='card', measure='painted', hopper='polymer', scale='polymer', screen='polymer',
                 block='polymer', tray='polymer', manual='card', peg='raw_wood', die_green='polymer',
                 die_red='polymer', die_blue='polymer', funnel='polymer')


# ---------------------------------------------------------------- what goes through it (items)

# name, title, case diameter, length, neck (0 = straight), colour of the box its makings come in
CASINGS = [
    ('spent_9mm', 'Spent 9mm Casings', 0.62, 1.2, 0.0),
    ('spent_380', 'Spent .380 ACP Casings', 0.62, 1.0, 0.0),
    ('spent_38', 'Spent .38 Special Casings', 0.66, 1.9, 0.0),
    ('spent_44', 'Spent .44 Magnum Casings', 0.78, 2.1, 0.0),
    ('spent_556', 'Spent 5.56mm Casings', 0.66, 2.8, 0.42),
    ('spent_308', 'Spent .308 Casings', 0.82, 3.3, 0.5),
    ('spent_3030', 'Spent .30-30 Casings', 0.74, 3.1, 0.5),
    ('spent_50', 'Spent .50 BMG Casings', 1.3, 5.6, 0.85),
]


def casing(m, x, z, d, L, neck, lying=True):
    """One empty case: rim, body, shoulder and neck (pistol cases are straight). Lying along x, or standing."""
    r = d / 2
    if lying:
        m.rod(x, x + 0.18, r * 1.04, 'brass', cx=r, cz=z, axis=0)
        body = L * (0.72 if neck else 1.0)
        m.rod(x + 0.18, x + body, r, 'brass', cx=r, cz=z, axis=0)
        if neck:
            m.rod(x + body, x + body + L * 0.08, r * 0.78, 'brass', cx=r, cz=z, axis=0)
            m.rod(x + body + L * 0.08, x + L, neck / 2, 'brass', cx=r, cz=z, axis=0)
        m.box([x + L - 0.01, r - (neck or d) / 2 * 0.6, z - (neck or d) / 2 * 0.6],
              [x + L + 0.01, r + (neck or d) / 2 * 0.6, z + (neck or d) / 2 * 0.6], 'mouth')
    else:
        m.rod(0, 0.18, r * 1.04, 'brass', cx=x, cz=z)
        body = L * (0.72 if neck else 1.0)
        m.rod(0.18, body, r, 'brass', cx=x, cz=z)
        if neck:
            m.rod(body, body + L * 0.08, r * 0.78, 'brass', cx=x, cz=z)
            m.rod(body + L * 0.08, L, neck / 2, 'brass', cx=x, cz=z)
        m.box([x - (neck or d) / 2 * 0.6, L - 0.01, z - (neck or d) / 2 * 0.6],
              [x + (neck or d) / 2 * 0.6, L + 0.01, z + (neck or d) / 2 * 0.6], 'mouth')
        m.box([x - r * 0.35, -0.01, z - r * 0.35], [x + r * 0.35, 0.01, z + r * 0.35], 'primer')


def build_casings(d, L, neck):
    def build(m):
        casing(m, -L / 2, 0.0, d, L, neck)
        casing(m, -L / 2 + L * 0.15, d * 1.25, d, L, neck)
        casing(m, -d * 1.4, -d * 1.6, d, L, neck, lying=False)
    return build


def build_hulls(m):
    for x, z, standing in ((-1.6, 0.0, False), (-1.2, 1.15, False), (1.5, -1.4, True)):
        if standing:
            m.rod(0, 0.5, 0.52, 'brass', cx=x, cz=z)
            m.rod(0.5, 3.6, 0.48, 'hull', cx=x, cz=z)
            m.box([x - 0.3, 3.59, z - 0.3], [x + 0.3, 3.61, z + 0.3], 'mouth')
        else:
            m.rod(x, x + 0.5, 0.52, 'brass', cx=0.52, cz=z, axis=0)
            m.rod(x + 0.5, x + 3.6, 0.48, 'hull', cx=0.52, cz=z, axis=0)
            m.box([x + 3.59, 0.22, z - 0.3], [x + 3.61, 0.82, z + 0.3], 'mouth')


def build_powder(m):
    m.box([-1.6, 0.0, -1.1], [1.6, 4.4, 1.1], 'jug')
    m.box([-1.4, 4.4, -0.9], [1.4, 4.9, 0.9], 'jug')
    m.rod(4.9, 5.5, 0.55, 'cap', cx=-0.7, cz=0.0)
    m.box([0.2, 4.9, -0.25], [1.2, 5.6, 0.25], 'jug')                 # the carry handle
    m.box([-1.65, 1.0, -1.15], [1.65, 3.2, 1.15], 'label')


def build_primers(m):
    """A flat box of a hundred, its lid lifted off to one side to show the tray of primers, cup side up."""
    m.box([-2.0, 0.0, -1.5], [2.0, 0.7, 1.5], 'carton')
    m.box([-1.85, 0.7, -1.35], [1.85, 0.72, 1.35], 'tray')
    for i in range(6):
        for j in range(4):
            m.rod(0.72, 0.86, 0.17, 'cup', cx=-1.5 + i * 0.6, cz=-1.0 + j * 0.66)
    m.box([1.0, 0.0, -2.9], [5.0, 0.35, 0.1], 'lid')
    m.box([1.4, 0.35, -2.4], [4.6, 0.37, -0.4], 'label')


def build_heads(r, h, pointed, n=(4, 3)):
    """An open tray of bullet heads standing nose up: short round noses for pistols, long spitzers for rifles."""
    def build(m):
        nx, nz = n
        gap = max(2 * r + 0.25, 0.7)
        w, d = nx * gap / 2 + 0.3, nz * gap / 2 + 0.3
        m.box([-w, 0.0, -d], [w, 0.7, d], 'tray')
        m.box([-w, 0.7, -d], [w, 1.1, -d + 0.2], 'tray')
        m.box([-w, 0.7, d - 0.2], [w, 1.1, d], 'tray')
        for i in range(nx):
            for j in range(nz):
                x, z = -w + 0.3 + gap / 2 + i * gap, -d + 0.3 + gap / 2 + j * gap
                body = h * (0.55 if pointed else 0.7)
                m.rod(0.7, 0.7 + body, r, 'jacket', cx=x, cz=z)
                m.rod(0.7 + body, 0.7 + body + (h - body) * 0.6, r * (0.62 if pointed else 0.8), 'jacket', cx=x, cz=z)
                m.rod(0.7 + body + (h - body) * 0.6, 0.7 + h, r * (0.3 if pointed else 0.5), 'jacket', cx=x, cz=z)
    return build


def build_shot(m):
    m.box([-1.6, 0.0, -1.2], [1.6, 2.6, 1.2], 'bag')
    m.box([-1.2, 2.6, -0.8], [1.2, 3.2, 0.8], 'bag')
    m.box([-0.5, 3.2, -0.4], [0.5, 3.8, 0.4], 'bag')
    m.box([-0.55, 3.2, -0.45], [0.55, 3.45, 0.45], 'tie')
    m.box([-1.65, 0.8, -1.25], [1.65, 1.9, 1.25], 'label')


def build_magazine(m):
    m.box([-2.4, 0.0, -3.1], [2.4, 0.3, 3.1], 'cover')
    m.box([-2.0, 0.3, -2.8], [2.0, 0.32, -1.8], 'title')        # masthead
    m.box([-1.8, 0.3, -1.3], [1.8, 0.32, 2.0], 'art')           # a rifle on a bench, as cover art
    m.box([-1.5, 0.32, 0.2], [1.3, 0.34, 0.45], 'steel')
    m.box([-1.6, 0.32, 0.45], [-0.6, 0.34, 0.9], 'title')
    m.box([-2.0, 0.3, 2.3], [0.4, 0.32, 2.7], 'title')         # issue line


def label(f):
    c = P.plain(f, '#d8cfb8', 0.08)
    band = np.abs(f.Y - 2.1) < 0.35
    return np.where(band[..., None], P.col('#a32a22'), c)


# copper-jacketed, but the .38's plain lead; (radius, height, pointed, rows)
HEADS = [('bullets_9mm', '9mm Bullets', '#b06a3a', (0.3, 0.6, False)),
         ('bullets_38', '.38 Bullets', '#8a8f93', (0.32, 0.75, False)),
         ('bullets_44', '.44 Bullets', '#b06a3a', (0.38, 0.85, False)),
         ('bullets_556', '5.56mm Bullets', '#b06a3a', (0.2, 1.0, True, (5, 3))),
         ('bullets_30', '.30 Bullets', '#b06a3a', (0.27, 1.35, True)),
         ('bullets_50', '.50 Bullets', '#b06a3a', (0.45, 2.4, True, (3, 2)))]


def items():
    out = []
    for name, title, d, L, neck in CASINGS:
        out.append((name, title, build_casings(d, L, neck), {'brass': BRASS, 'mouth': lambda f: P.plain(f, '#2b2116', 0.1),
                    'primer': lambda f: P.plain(f, '#c0c3c4', 0.1)}, dict(brass='brass', mouth='polymer', primer='steel')))
    out.append(('spent_hulls', 'Fired Shotgun Hulls', build_hulls, {'brass': BRASS, 'hull': lambda f: P.polymer(f, '#a3241d'),
                'mouth': lambda f: P.plain(f, '#2b2116', 0.1)}, dict(brass='brass', hull='polymer', mouth='polymer')))
    out.append(('smokeless_powder', 'Smokeless Powder', build_powder, {'jug': lambda f: P.polymer(f, '#232325'),
                'cap': lambda f: P.polymer(f, '#a32a22'), 'label': label}, dict(jug='polymer', cap='polymer', label='card')))
    out.append(('primers', 'Primers', build_primers, {'carton': lambda f: P.grime(f, P.plain(f, '#c9b27a', 0.1), 0.3),
                'lid': lambda f: P.grime(f, P.plain(f, '#7a2a22', 0.1), 0.3), 'tray': lambda f: P.plain(f, '#d8d4c8', 0.05),
                'cup': lambda f: P.steel(f, 0.72, tint='#c9c2a2', spread=0.15), 'label': label},
                dict(carton='card', lid='card', tray='polymer', cup='brass', label='card')))
    for name, title, colour, size in HEADS:
        out.append((name, title, build_heads(*size), {'tray': lambda f: P.polymer(f, '#6d7072'),
                    'jacket': lambda f, c=colour: P.steel(f, 0.62, tint=c, spread=0.2)}, dict(tray='polymer', jacket='brass')))
    out.append(('lead_shot', 'Lead Shot', build_shot, {'bag': lambda f: P.grime(f, P.plain(f, '#cfc6ad', 0.14), 0.4),
                'tie': lambda f: P.plain(f, '#3a2a1e', 0.1), 'label': label}, dict(bag='card', tie='cloth_tape', label='card')))
    out.append(('gunsmith_magazine', 'Gunsmith Monthly', build_magazine, {'cover': lambda f: P.grime(f, P.plain(f, '#2c3a4a', 0.1), 0.3),
                'title': lambda f: P.plain(f, '#d9b23a', 0.05), 'art': lambda f: P.plain(f, '#6b5a44', 0.2), 'steel': STEEL},
                dict(cover='card', title='card', art='card', steel='steel')))
    return out


def write_item(name, build, lk, kinds, seed):
    W.MELEE_PBR[name] = kinds
    m = M.Model(name)
    build(m)
    lo, hi = m.bounds()
    m.shift(C - (lo + hi) / 2)
    m.tidy()
    uvs, T = W.write_texture(m, lk, 0.25, seed, name)
    M.dump(M.model_json(m, uvs, f'{NS}:item/weapon/{name}', W.small_display(m, real=(1.0, 1.7))),
           ASSETS / f'models/item/weapon/{name}.json')
    W.item_def(name, W.ref(name))


def write_bench():
    name = 'reloading_bench'
    W.MELEE_PBR[name] = BENCH_PBR
    m = M.Model(name)
    build_bench(m)
    m.tidy()
    old, W.MELEE_PX = W.MELEE_PX, 8
    try:
        uvs, T = W.write_texture(m, BENCH_LOOK, 0.3, 991, name)
    finally:
        W.MELEE_PX = old
    j = M.model_json(m, uvs, f'{NS}:item/weapon/{name}', W.small_display(m), gui_light='side')
    M.dump(j, ASSETS / f'models/block/{name}.json')
    M.dump({'variants': {f'facing={d}': {'model': f'{NS}:block/{name}', **({'y': y} if y else {})}
                         for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}},
           ASSETS / f'blockstates/{name}.json')
    W.item_def(name, {'type': 'minecraft:model', 'model': f'{NS}:block/{name}'})


def main():
    lang = {}
    write_bench()
    lang[f'block.{NS}.reloading_bench'] = 'Reloading Bench'
    for i, (name, title, build, lk, kinds) in enumerate(items()):
        write_item(name, build, lk, kinds, 1200 + i)
        lang[f'item.{NS}.{name}'] = title
    W.merge_lang(lang)
    print('\n'.join(sorted(lang.values())))


if __name__ == '__main__':
    main()
