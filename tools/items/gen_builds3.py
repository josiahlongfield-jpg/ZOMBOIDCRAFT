"""Base building, wave 2a: the wood pieces the Build page puts up (a stud wall frame, a boarded wall at three states of
damage, a window frame, a door frame, a plank door, a post, floorboards at four stages of sag) as block models with their
blockstates, block loot and names. Built from boxes, one per board or stud, and painted per face like gen_builds2.py.

References: refs/building/wood/ (REFERENCES, 2026-10-31). stud-wall-framing/04 (a house in framing: pale fresh studs on
a sole plate under a doubled top plate, braces let in at 45 degrees), plank-doors/05 (a board door: vertical planks,
ledges across), floor-joists-beams/01 (boards laid across joists, the joists' ends showing at the rim),
door-window-framing (heavy jambs, a sill that sticks out past the wall, a header over the opening). A survival build is
rough: boards weathered grey-brown and uneven, nail heads in rows over each stud, fresh lumber where it's just been cut.

    python3 tools/items/gen_builds3.py
"""
import json
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
from gen_cases import merge_lang  # noqa: E402

ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
DATA = ROOT / 'src/main/resources/data'
NS = 'zomboidcraft'


# ================================================================== materials


def fresh(f):
    """New-cut spruce studs: pale yellow-pink, faint grain, the odd knot."""
    c = P.wood(f, light='#dcc08f', dark='#b8935f', ring=1.8, lacquer=0.03)
    knot = (f.noise(0.9, 711, 2) > 0.74) & ~f.end_face()
    return np.where(knot[..., None], c * 0.72, c)


def board(f):
    """Weathered pine boards: grey-brown, darker where water ran down them."""
    c = P.wood(f, light='#9d8461', dark='#695236', ring=2.2, lacquer=0.02)
    streak = f.noise(0.5, 712, 3, (4, 0.3, 4)) > 0.6
    c = np.where(streak[..., None], c * 0.82, c)
    return c * (1 + (f.noise(0.25, 713, 2) - 0.5) * 0.18)[..., None]


def timber(f):
    """A sawn 6x6 post: darker, rougher than the studs."""
    return P.wood(f, light='#8c6e4a', dark='#5a442d', ring=2.6, lacquer=0.02)


def cavity(f):
    """Inside a wall where boards have been torn off: dark, the studs paler in it."""
    c = P.plain(f, '#2e261d', 0.1)
    stud = (np.abs(f.X - 0.75) < 0.75) | (np.abs(f.X - 8) < 0.75) | (np.abs(f.X - 15.25) < 0.75)
    side = abs(f.n[2]) > 0.5
    return np.where((stud & side)[..., None], P.col('#b0905f') * 0.8, c)


def nail(f):
    return P.steel(f, 0.4, spread=0.1)


def galv(f):
    return P.galvanised(f)


WOOD = dict(fresh=fresh, board=board, timber=timber, cavity=cavity, nail=nail, galv=galv)


# ================================================================== the pieces (block space, a wall running along x)

STUDS = (0.0, 7.25, 14.5)       # left edge of each stud, 1.5 wide


def build_frame(m):
    """Sole plate, doubled top plate, three studs, a brace let in across them."""
    m.box([0, 0, 6.2], [16, 1.5, 9.8], 'fresh')
    m.box([0, 13.0, 6.2], [16, 14.5, 9.8], 'fresh')
    m.box([0, 14.5, 6.2], [16, 16, 9.8], 'fresh')
    for x in STUDS:
        m.box([x, 1.5, 6.2], [x + 1.5, 13.0, 9.8], 'fresh')
    m.seg((1.5, 2.2), (13.0, 12.6), 1.4, 0.7, 'fresh', z=9.85, over=0.2)
    for x in STUDS:                                                     # nails through the plates into each stud
        for y in (0.75, 15.25):
            m.box([x + 0.55, y - 0.2, 9.8], [x + 0.95, y + 0.2, 9.95], 'nail')


def boards(m, z0, z1, y0, y1, out, seed, gaps=()):
    """Horizontal boards between y0 and y1 on one face, nailed to the studs; gaps = (board, x0, x1) torn out."""
    rng = np.random.default_rng(seed)
    n = int(round((y1 - y0) / 4.0))
    h = (y1 - y0) / n
    for i in range(n):
        lap = rng.uniform(0, 0.25) * out                               # each board sits a touch proud of the last
        a, b = y0 + i * h, y0 + (i + 1) * h - 0.12
        spans = [(0.0, 16.0)]
        for gi, gx0, gx1 in gaps:
            if gi == i:
                spans = [(s0, s1) for s0, s1 in [(0.0, gx0), (gx1, 16.0)] if s1 - s0 > 0.4]
        for s0, s1 in spans:
            m.box([s0, a, z0 + min(0, lap)], [s1, b, z1 + max(0, lap)], 'board')
            for x in STUDS:
                nx = x + 0.75
                if s0 + 0.3 < nx < s1 - 0.3:
                    for ny in (a + h * 0.3, a + h * 0.7):
                        zf = z1 + max(0, lap) if out > 0 else z0 + min(0, lap)
                        m.box([nx - 0.18, ny - 0.18, min(zf, zf + 0.12 * out)], [nx + 0.18, ny + 0.18, max(zf, zf + 0.12 * out)], 'nail')


def build_wall(damage):
    """A frame boarded both sides. Damage 1 splits a board, 2 tears pieces off both faces to the dark inside."""
    def build(m):
        m.box([0, 0, 1.5], [16, 16, 14.5], 'cavity')
        front = [] if damage == 0 else [(1, 3.5, 7.0)] if damage == 1 else [(1, 2.0, 9.5), (2, 5.0, 12.5)]
        back = [] if damage < 2 else [(2, 9.0, 14.0)]
        boards(m, 0.0, 1.5, 0, 16, -1, 51 + damage, front)
        boards(m, 14.5, 16.0, 0, 16, 1, 61 + damage, back)
        if damage == 2:                                                 # a split board hanging off one nail
            m.seg((9.5, 5.4), (12.5, 3.2), 1.6, 0.7, 'board', z=-0.5, over=0.0)
    return build


def build_window(m):
    """A boarded wall up to the sill, the sill sticking out both sides, jambs and a header round the opening."""
    m.box([0, 0, 1.5], [16, 4.5, 14.5], 'cavity')
    boards(m, 0.0, 1.5, 0, 4.5, -1, 71)
    boards(m, 14.5, 16.0, 0, 4.5, 1, 72)
    m.box([0, 4.5, -0.8], [16, 5.5, 16.8], 'board')                  # sill
    m.box([0, 12.6, 0], [16, 16, 16], 'fresh')                        # header
    for x0, x1 in ((0, 2), (14, 16)):                                 # jambs
        m.box([x0, 5.5, 0], [x1, 12.6, 16], 'fresh')
    for z0, z1 in ((-0.4, 0), (16, 16.4)):                            # trim boards round the opening, both faces
        m.box([2, 12.0, z0], [14, 12.6, z1], 'board')
        m.box([1.4, 5.5, z0], [2.0, 12.6, z1], 'board')
        m.box([14.0, 5.5, z0], [14.6, 12.6, z1], 'board')
    for x in (1.0, 15.0):
        for z in (-0.95, 16.8):
            m.box([x - 0.18, 4.8, min(z, z + 0.15)], [x + 0.18, 5.2, max(z, z + 0.15)], 'nail')


def build_doorframe(upper):
    def build(m):
        for x0, x1 in ((0, 2.5), (13.5, 16)):
            m.box([x0, 0, 0], [x1, 12.0 if upper else 16, 16], 'fresh')
            if upper:
                m.box([x0, 12.0, 0], [x1, 16, 16], 'fresh')
        for z0, z1 in ((-0.4, 0), (16, 16.4)):                         # casing round the opening
            m.box([2.5, 0, z0], [3.2, 16 if not upper else 11.4, z1], 'board')
            m.box([12.8, 0, z0], [13.5, 16 if not upper else 11.4, z1], 'board')
            if upper:
                m.box([2.5, 11.4, z0], [13.5, 12.0, z1], 'board')
        if upper:
            m.box([2.5, 12.0, 0], [13.5, 16, 16], 'fresh')              # header over the opening
            for x in (4.0, 8.0, 12.0):
                for z in (-0.15, 16.0):
                    m.box([x - 0.18, 13.8, z], [x + 0.18, 14.2, z + 0.15], 'nail')
    return build


def build_post(m):
    m.box([5, 0, 5], [11, 16, 11], 'timber')
    for z0, z1 in ((4.75, 5.0), (11.0, 11.25)):                       # a galvanised post base, two straps
        m.box([5.5, 0, z0], [10.5, 3.2, z1], 'galv')
        for x in (6.6, 9.4):
            for y in (1.0, 2.3):
                zz = z0 if z0 < 5 else z1
                m.box([x - 0.16, y - 0.16, zz - 0.1 if z0 < 5 else zz], [x + 0.16, y + 0.16, zz if z0 < 5 else zz + 0.1], 'nail')


def build_floor(sag):
    """Rim joists round a box of joists, boards across the top. It sags in the middle as what held it up goes."""
    def build(m):
        for z0, z1 in ((0, 1.5), (14.5, 16)):
            m.box([0, 0, z0], [16, 13.5, z1], 'fresh')
        for x0, x1 in ((0, 1.5), (14.5, 16)):
            m.box([x0, 0, 1.5], [x1, 13.5, 14.5], 'fresh')
        for x in (5.0, 9.5):
            m.box([x, 0 + sag * 0.5, 1.5], [x + 1.5, 13.5 - sag * 0.4, 14.5], 'fresh')
        w = 16 / 6
        for i in range(6):
            mid = 1 - abs((i + 0.5) / 6 - 0.5) * 2                     # 0 at the rims, 1 in the middle
            drop = sag * 0.55 * mid
            z0, z1 = i * w, (i + 1) * w - 0.15
            if sag >= 2 and i == 3:                                     # a board split along its length
                m.box([0, 13.5 - drop - 0.3, z0], [7.2, 16 - drop - 0.3, z1], 'board')
                m.box([7.8, 13.5 - drop, z0], [16, 16 - drop, z1], 'board')
            else:
                m.box([0, 13.5 - drop, z0], [16, 16 - drop, z1], 'board')
            for x in (0.75, 5.75, 10.25, 15.25):
                m.box([x - 0.17, 16 - drop, (z0 + z1) / 2 - 0.17], [x + 0.17, 16.1 - drop, (z0 + z1) / 2 + 0.17], 'nail')
    return build


# ================================================================== the plank door: flat textures on the vanilla door shape


def door_textures():
    """A ledged and braced board door, 64 x 128: five vertical boards, three ledges, two braces, black strap hinges and a
    thumb latch. Split into the bottom and top textures the door templates take."""
    W, H = 64, 128
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    bw = W / 5
    bi = np.floor(xx / bw)
    grain = P.fbm(xx * 0.08 + bi * 7.3, yy * 0.012, bi, 901, 4)
    t = np.clip(grain * 0.9 + (P.fbm(xx * 0.5, yy * 0.04, bi, 902, 2) - 0.5) * 0.5, 0, 1)
    img = P.mix(P.col('#a08663'), P.col('#64503a'), t)
    tone = 1 + (np.sin(bi * 2.7) * 0.06)
    img = img * tone[..., None]
    seam = (xx % bw) < 0.9
    img = np.where(seam[..., None], img * 0.5, img)

    def ledge(y0, y1):
        m = (yy >= y0) & (yy < y1)
        return m

    ledges = [(10, 20), (58, 68), (108, 118)]
    brace = np.zeros((H, W), bool)
    for (a0, a1), (b0, b1) in zip(ledges[1:], ledges[:-1]):     # braces climb from the hinge side at the bottom
        y_lo, y_hi = b1, a0
        for y in range(int(y_lo), int(y_hi)):
            fx = 4 + (W - 12) * (y_hi - y) / (y_hi - y_lo)
            brace[y, max(0, int(fx) - 4):min(W, int(fx) + 4)] = True
    lmask = np.zeros((H, W), bool)
    for y0, y1 in ledges:
        lmask |= ledge(y0, y1)
    fg = P.fbm(xx * 0.012, yy * 0.08, 3, 903, 4)
    lw = P.mix(P.col('#ae916b'), P.col('#735c42'), np.clip(fg, 0, 1))
    piece = lmask | brace
    img = np.where(piece[..., None], lw, img)
    edge = piece & ~(np.roll(piece, 1, 0) & np.roll(piece, -1, 0) & np.roll(piece, 1, 1) & np.roll(piece, -1, 1))
    img = np.where(edge[..., None], img * 0.55, img)
    # clenched nails in a row across each ledge and down each brace
    for y0, y1 in ledges:
        for x in range(4, W, int(bw)):
            cy = (y0 + y1) // 2
            img[cy - 1:cy + 1, x + 2:x + 4] = P.col('#3a3a3a')
    # black strap hinges on the hinge side, over the top and bottom ledges
    for y0, y1 in (ledges[0], ledges[2]):
        cy = (y0 + y1) / 2
        strap = (np.abs(yy - cy) < 2.2 - xx * 0.02) & (xx < 40)
        img = np.where(strap[..., None], P.col('#1d1c1b') * (1 + (P.fbm(xx * 0.3, yy * 0.3, 1, 904, 2) - 0.5) * 0.4)[..., None], img)
        for x in (4, 14, 26, 36):
            img[int(cy) - 1:int(cy) + 1, x:x + 2] = P.col('#4a4744')
    # thumb latch on the other side
    img[56:72, 54:57] = P.col('#1d1c1b')
    img[62:65, 50:58] = P.col('#2c2a28')
    # grime low down
    low = np.clip((yy - 96) / 32, 0, 1) * P.fbm(xx * 0.1, yy * 0.1, 5, 905, 3)
    img = img * (1 - low * 0.35)[..., None]
    img = np.clip(img, 0, 1)
    return img[64:], img[:64]


def save_rgb(img, path):
    from PIL import Image
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGB').convert('RGBA').save(path)


def door_assets():
    bottom, top = door_textures()
    tex = ASSETS / 'textures/block/building'
    save_rgb(bottom, tex / 'wood_plank_door_bottom.png')
    save_rgb(top, tex / 'wood_plank_door_top.png')
    models = ASSETS / 'models/block/building'
    for half in ('bottom', 'top'):
        for hinge in ('left', 'right'):
            for opn in ('', '_open'):
                name = f'wood_plank_door_{half}_{hinge}{opn}'
                M.dump({'parent': f'minecraft:block/door_{half}_{hinge}{opn}',
                        'textures': {'bottom': f'{NS}:block/building/wood_plank_door_bottom',
                                     'top': f'{NS}:block/building/wood_plank_door_top',
                                     'particle': f'{NS}:block/building/wood_plank_door_bottom'}}, models / f'{name}.json')
    base = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    variants = {}
    for facing, y in base.items():
        for half, h in (('lower', 'bottom'), ('upper', 'top')):
            for hinge in ('left', 'right'):
                for opn in (False, True):
                    ry = (y + (90 if hinge == 'left' else 270)) % 360 if opn else y
                    v = {'model': f'{NS}:block/building/wood_plank_door_{h}_{hinge}{"_open" if opn else ""}'}
                    if ry:
                        v['y'] = ry
                    variants[f'facing={facing},half={half},hinge={hinge},open={"true" if opn else "false"}'] = v
    M.dump({'variants': variants}, ASSETS / 'blockstates/wood_plank_door.json')


# ================================================================== writing it out


def block_model(build, name, wear, px=6):
    m = M.Model(name)
    build(m)
    old = M.PX
    M.PX = px
    m.tidy()
    seed = zlib.crc32(name.encode()) % 1000
    img, uvs, T = M.pack(M.paint_model(m, worn_look(wear, 0.0, **WOOD), wear, seed))
    M.PX = old
    M.save_png(img, ASSETS / f'textures/block/building/{name}.png')
    out = M.model_json(m, uvs, f'{NS}:block/building/{name}', {}, gui_light='side')
    del out['display']
    out['ambientocclusion'] = False
    M.dump(out, ASSETS / f'models/block/building/{name}.json')
    print(f'{name}: {len(m.parts)} boxes, {T}px')


def model(name):
    return f'{NS}:block/building/{name}'


def axis_states(name, extra=None):
    """axis=x is the model as built (a wall along x); axis=z turns it a quarter."""
    v = {}
    for axis, y in (('x', 0), ('z', 90)):
        if extra:
            for key, mdl in extra:
                v[f'axis={axis},{key}'] = {'model': model(mdl), **({'y': y} if y else {})}
        else:
            v[f'axis={axis}'] = {'model': model(name), **({'y': y} if y else {})}
    return {'variants': v}


NAMES = {'wood_wall_frame': 'Wall Frame', 'wood_wall': 'Wooden Wall', 'wood_window_frame': 'Window Frame',
         'wood_door_frame': 'Door Frame', 'wood_plank_door': 'Plank Door', 'wood_post': 'Wooden Post',
         'wood_floor': 'Wooden Floor'}


def main():
    (ASSETS / 'textures/block/building').mkdir(parents=True, exist_ok=True)
    block_model(build_frame, 'wood_wall_frame', 0.05)
    for d in range(3):
        block_model(build_wall(d), f'wood_wall_{d}', 0.3 + 0.2 * d, px=5)
    block_model(build_window, 'wood_window_frame', 0.25, px=5)
    block_model(build_doorframe(False), 'wood_door_frame_lower', 0.15)
    block_model(build_doorframe(True), 'wood_door_frame_upper', 0.15)
    block_model(build_post, 'wood_post', 0.2)
    for s in range(4):
        block_model(build_floor(s), f'wood_floor_{s}', 0.25 + 0.1 * s, px=5)
    door_assets()

    bs = ASSETS / 'blockstates'
    M.dump(axis_states('wood_wall_frame'), bs / 'wood_wall_frame.json')
    M.dump(axis_states('wood_window_frame'), bs / 'wood_window_frame.json')
    M.dump(axis_states(None, [(f'damage={d}', f'wood_wall_{0 if d < 2 else 1 if d < 5 else 2}') for d in range(8)]),
           bs / 'wood_wall.json')
    M.dump(axis_states(None, [('half=lower', 'wood_door_frame_lower'), ('half=upper', 'wood_door_frame_upper')]),
           bs / 'wood_door_frame.json')
    M.dump({'variants': {'': {'model': model('wood_post')}}}, bs / 'wood_post.json')
    M.dump({'variants': {f'sag={s}': {'model': model(f'wood_floor_{s}')} for s in range(4)}}, bs / 'wood_floor.json')

    # what each gives back taken down: about half the planks that went in (the nails bend)
    drops = {'wood_wall_frame': 1, 'wood_wall': 2, 'wood_window_frame': 1, 'wood_door_frame': 2, 'wood_plank_door': 2,
             'wood_post': 1, 'wood_floor': 1}
    lower_only = {'wood_door_frame', 'wood_plank_door'}
    for block, n in drops.items():
        pool = {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:oak_planks',
                'functions': [{'function': 'minecraft:set_count', 'count': n}]}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]}
        if block in lower_only:
            pool['conditions'].append({'condition': 'minecraft:block_state_property', 'block': f'{NS}:{block}',
                                       'properties': {'half': 'lower'}})
        table = {'type': 'minecraft:block', 'pools': [pool]}
        path = DATA / f'{NS}/loot_table/blocks/{block}.json'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(table, indent=2) + '\n')
    merge_lang({f'block.{NS}.{k}': v for k, v in NAMES.items()})


if __name__ == '__main__':
    main()
