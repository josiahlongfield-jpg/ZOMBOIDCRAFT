"""Wave 2 item models: kitchen kit, hand tools, materials, electrical, light and fire, camping and farm, car parts.
Same conventions as gen_loot.py (inches, standing on y = 0, front toward +z); table(it) returns the entries.
"""
import numpy as np

import art as A
import paint as P
import shapes as sh


def S(fn, **kw):
    return lambda m: fn(m, **kw)


def lay(m, r):
    """Lays a part built along y on its side (along x), resting on y = 0."""
    for p in m.parts:
        lo, hi = p['lo'].copy(), p['hi'].copy()
        p['lo'] = np.array([lo[1], lo[0], lo[2]])
        p['hi'] = np.array([hi[1], hi[0], hi[2]])
        if 'axis' in p and p['axis'] in (0, 1):
            p['axis'] = 1 - p['axis']
        if p['rot']:
            o = p['rot']['origin']
            p['rot'] = dict(p['rot'], origin=[o[1], o[0], o[2]], axis={'x': 'y', 'y': 'x', 'z': 'z'}[p['rot']['axis']],
                            angle=-p['rot']['angle'] if p['rot']['axis'] == 'z' else p['rot']['angle'])
    lo = min(p['lo'][1] for p in m.parts)
    sh.shift_up(m, r - lo if r is not None else -lo)


# ================================================================== kitchen


def pot(d=8.0, h=5.0, handles='side', lid=True):
    def build(m):
        r = d / 2
        m.rod(0, h, r, 'metal')
        m.rod(h - 0.15, h, r + 0.12, 'metal')
        if lid:
            m.rod(h, h + 0.3, r * 0.98, 'lid')
            m.rod(h + 0.3, h + 0.8, 0.5, 'handle')
        if handles == 'side':
            for s in (-1, 1):
                m.box([s * r - (0.9 if s > 0 else -0.9) * 0 + (0 if s > 0 else -1.0), h - 1.0, -0.9],
                      [s * r + (1.0 if s > 0 else 0), h - 0.6, 0.9], 'handle')
        elif handles == 'long':
            m.box([r, h - 0.8, -0.45], [r + 7.0, h - 0.4, 0.45], 'handle')
    return build


def skillet(m):
    m.rod(0, 1.8, 5.0, 'metal')
    m.box([5.0, 1.1, -0.6], [11.5, 1.7, 0.6], 'metal')
    m.box([-5.9, 1.2, -0.7], [-5.0, 1.7, 0.7], 'metal')


def kettle(m):
    m.rod(0, 6.0, 3.4, 'metal')
    m.rod(6.0, 7.0, 2.4, 'metal')
    m.rod(7.0, 7.3, 0.9, 'handle')
    m.seg((3.0, 3.0), (5.6, 6.2), 0.8, 0.8, 'metal')
    m.box([-1.8, 9.0, -0.4], [1.8, 9.6, 0.4], 'handle')
    for x in (-1.8, 1.4):
        m.box([x, 6.8, -0.35], [x + 0.4, 9.6, 0.35], 'handle')


def mug(m):
    m.rod(0, 3.8, 1.6, 'body')
    m.box([1.6, 0.8, -0.3], [2.6, 1.2, 0.3], 'body')
    m.box([2.2, 1.2, -0.3], [2.6, 3.0, 0.3], 'body')
    m.box([1.6, 2.6, -0.3], [2.6, 3.0, 0.3], 'body')
    m.rod(3.6, 3.62, 1.35, 'food')


def plate(m):
    m.rod(0, 0.3, 4.2, 'body')
    m.rod(0.3, 0.6, 5.0, 'body')
    m.rod(0.6, 0.62, 3.6, 'mark')


def bowl(m):
    m.rod(0, 0.4, 1.6, 'body')
    m.rod(0.4, 1.4, 2.6, 'body')
    m.rod(1.4, 2.4, 3.2, 'body')


def cutlery(m):
    m.box([-3.5, 0, -0.25], [2.0, 0.12, 0.25], 'metal')
    m.box([2.0, 0, -0.55], [3.8, 0.15, 0.55], 'metal')
    m.box([-3.5, 0.12, 0.8], [2.0, 0.24, 1.3], 'metal')
    m.box([2.0, 0.12, 0.6], [3.6, 0.3, 1.5], 'metal')


def stick_tool(length, w, head, head_mat='metal', grip='handle', t=None):
    """A handled tool lying flat: grip, shaft, head (x span, width, thickness)."""
    def build(m):
        tt = t or w * 0.7
        m.box([-length / 2, 0, -w / 2], [-length / 2 + length * 0.4, tt, w / 2], grip)
        m.box([-length / 2 + length * 0.4, tt * 0.25, -w * 0.15], [length / 2 - head[0], tt * 0.6, w * 0.15], head_mat)
        hx, hw, ht = head
        m.box([length / 2 - hx, 0, -hw / 2], [length / 2, ht, hw / 2], head_mat)
    return build


def rolling_pin(m):
    m.rod(-5.0, 5.0, 1.2, 'wood', axis=0)
    for s in (-1, 1):
        m.rod(s * 5.0, s * 7.5, 0.45, 'wood', axis=0) if s > 0 else m.rod(-7.5, -5.0, 0.45, 'wood', axis=0)
    sh.shift_up(m, 1.2)


def cutting_board(m):
    m.box([-6.0, 0, -4.5], [6.0, 0.75, 4.5], 'wood')
    m.box([6.0, 0, -1.2], [8.0, 0.75, 1.2], 'wood')


def canteen(m):
    m.box([-2.9, 0, -1.4], [2.9, 7.0, 1.4], 'metal')
    m.box([-3.0, 0.3, -1.5], [3.0, 6.6, 1.5], 'cloth')
    m.rod(7.0, 7.9, 0.6, 'cap')


def camp_stove(m):
    m.box([-6.5, 0, -4.0], [6.5, 3.2, 4.0], 'body')
    m.box([-6.5, 3.2, -4.0], [6.5, 9.0, -3.7], 'body')
    for x in (-3.2, 3.2):
        m.rod(3.2, 3.5, 1.6, 'metal', cx=x, cz=0.4)
        m.box([x - 1.8, 3.5, 0.3], [x + 1.8, 3.65, 0.5], 'metal')
    m.box([6.5, 1.0, 1.5], [7.2, 2.0, 3.0], 'handle')


def canister(m):
    m.rod(0, 3.6, 2.0, 'label')
    m.rod(3.6, 4.2, 1.4, 'metal')
    m.rod(4.2, 4.6, 0.5, 'metal')


def mess_kit(m):
    m.box([-3.5, 0, -2.4], [3.5, 2.0, 2.4], 'metal')
    m.box([-3.6, 2.0, -2.5], [3.6, 2.3, 2.5], 'metal')
    m.box([3.5, 1.5, -0.4], [8.0, 1.8, 0.4], 'metal')


def foil_roll(colour):
    def build(m):
        m.box([-6.0, 0, -1.0], [6.0, 2.0, 1.0], 'label')
        m.rod(-6.0, -6.1, 0.6, 'core', axis=0)
        sh.shift_up(m, 0)
    return build


def percolator(m):
    m.rod(0, 7.5, 2.4, 'metal')
    m.rod(7.5, 8.2, 1.8, 'metal')
    m.rod(8.2, 9.0, 0.8, 'glass')
    m.seg((2.2, 5.0), (3.6, 7.6), 0.6, 0.6, 'metal')
    m.box([-3.6, 2.0, -0.4], [-2.4, 2.4, 0.4], 'handle')
    m.box([-3.6, 2.0, -0.4], [-3.2, 6.5, 0.4], 'handle')
    m.box([-3.6, 6.1, -0.4], [-2.4, 6.5, 0.4], 'handle')


# ================================================================== hand tools


def screwdriver(m):
    m.rod(-3.5, -0.5, 0.45, 'grip', axis=0)
    m.rod(-0.5, 3.0, 0.12, 'metal', axis=0)
    m.box([3.0, -0.12, -0.05], [3.3, 0.12, 0.05], 'metal')
    sh.shift_up(m, 0.45)


def pliers(cut=False):
    def build(m):
        m.seg((-3.5, 0.0), (0.0, 0.9), 0.5, 0.4, 'grip')
        m.seg((-3.5, 1.9), (0.0, 1.0), 0.5, 0.4, 'grip')
        m.box([-0.2, 0.7, -0.25], [0.6, 1.2, 0.25], 'metal')
        m.seg((0.4, 1.05), (2.4 if not cut else 1.6, 1.0), 0.35, 0.35, 'metal')
        m.seg((0.4, 0.85), (2.4 if not cut else 1.6, 0.9), 0.35, 0.35, 'metal')
        lay_flat(m)
    return build


def lay_flat(m):
    """Turns a part drawn in the x-y plane to lie flat in the x-z plane (on y = 0)."""
    for p in m.parts:
        lo, hi = p['lo'].copy(), p['hi'].copy()
        p['lo'] = np.array([lo[0], lo[2], lo[1]])
        p['hi'] = np.array([hi[0], hi[2], hi[1]])
        if p['rot']:
            o = p['rot']['origin']
            ax = {'z': 'y', 'y': 'z', 'x': 'x'}[p['rot']['axis']]
            p['rot'] = dict(p['rot'], origin=[o[0], o[2], o[1]], axis=ax, angle=-p['rot']['angle'])
        if 'axis' in p:
            p['axis'] = {0: 0, 1: 2, 2: 1}[p['axis']]
    lo = min(p['lo'][1] for p in m.parts)
    sh.shift_up(m, -lo)


def wrench(m):
    m.box([-3.5, 0, -0.35], [2.4, 0.3, 0.35], 'metal')
    m.box([2.4, 0, -0.9], [3.6, 0.35, 0.9], 'metal')
    m.box([3.2, 0, -0.25], [3.8, 0.35, 0.25], 'mark')


def case_box(w, h, d, mat='body'):
    def build(m):
        m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], mat)
        m.box([-w / 2 + 0.3, h * 0.45, d / 2], [w / 2 - 0.3, h * 0.55, d / 2 + 0.05], 'mark')
        for x in (-w * 0.3, w * 0.3):
            m.box([x - 0.4, h * 0.5, d / 2], [x + 0.4, h * 0.8, d / 2 + 0.15], 'metal')
    return build


def tape_measure(m):
    m.box([-1.5, 0, -0.8], [1.5, 3.0, 0.8], 'body')
    m.rod(0.6, 2.4, 1.3, 'body', cx=0.0, cz=0.0, axis=2)
    m.box([1.5, 0.15, -0.5], [1.9, 0.45, 0.5], 'metal')


def spirit_level(m):
    m.box([-12.0, 0, -0.6], [12.0, 2.4, 0.6], 'body')
    m.box([-1.0, 0.8, 0.6], [1.0, 1.6, 0.66], 'glass')
    for x in (-10.5, 10.5):
        m.box([x - 0.6, 0.8, 0.6], [x + 0.6, 1.6, 0.66], 'glass')


def saw(blade_w=4.0, length=24.0, hack=False):
    def build(m):
        if hack:
            m.box([-6.0, 0, -0.15], [6.0, 0.06, 0.15], 'metal')
            m.box([-6.5, 0.0, -0.2], [6.5, 0.4, 0.2], 'body')
            m.box([-6.5, 0.0, -0.2], [-6.0, 3.0, 0.2], 'body')
            m.box([6.0, 0.0, -0.2], [6.5, 3.0, 0.2], 'body')
            m.box([-6.5, 2.6, -0.2], [6.5, 3.0, 0.2], 'body')
            m.box([-9.5, 0.6, -0.4], [-6.5, 2.4, 0.4], 'grip')
            lay_flat(m)
            return
        m.box([-length / 2 + 4.5, 0, -0.05], [length / 2, blade_w, 0.05], 'metal')
        m.box([-length / 2, 0.3, -0.45], [-length / 2 + 4.5, blade_w + 0.6, 0.45], 'wood')
        lay_flat(m)
    return build


def bolt_cutters(m):
    m.seg((-12.0, 0.0), (6.0, 0.6), 0.8, 0.6, 'metal')
    m.seg((-12.0, 2.4), (6.0, 1.2), 0.8, 0.6, 'metal')
    for y in (0.0, 2.4):
        m.box([-12.0, y - 0.5, -0.45], [-6.0, y + 0.5, 0.45], 'grip')
    m.box([6.0, 0.4, -0.5], [9.0, 1.4, 0.5], 'metal')
    lay_flat(m)


def utility_knife(m):
    m.box([-3.0, 0, -0.4], [2.0, 0.6, 0.4], 'body')
    m.seg((2.0, 0.3), (3.0, 0.15), 0.4, 0.06, 'blade')


def whetstone(m):
    m.box([-3.5, 0, -1.0], [3.5, 1.0, 1.0], 'body')
    m.box([-3.5, 1.0, -1.0], [3.5, 1.2, 1.0], 'mark')


def hand_drill(m):
    m.rod(-2.0, 6.0, 0.3, 'metal', axis=0)
    m.rod(6.0, 8.0, 0.12, 'metal', axis=0)
    m.rod(-4.5, -2.0, 0.6, 'wood', axis=0)
    m.rod(-0.5, 0.5, 1.8, 'red', cx=1.5, cz=0.0, axis=2)
    m.box([0.8, 1.5, -0.3], [1.2, 3.2, 0.3], 'wood')
    sh.shift_up(m, 1.8)


def power_drill(m):
    m.box([-3.5, 4.0, -1.1], [3.0, 6.5, 1.1], 'body')
    m.rod(3.0, 4.2, 0.6, 'metal', cx=5.2, cz=0.0, axis=0)
    m.rod(4.2, 6.5, 0.15, 'metal', cx=5.2, cz=0.0, axis=0)
    m.box([-2.2, 0.8, -0.9], [-0.6, 4.0, 0.9], 'grip')
    m.box([-3.2, 0, -1.3], [0.6, 1.2, 1.3], 'label')
    m.box([-0.6, 3.2, -0.25], [-0.2, 3.8, 0.25], 'red')


def circular_saw(m):
    m.box([-5.0, 0, -4.5], [6.0, 0.4, 4.5], 'metal')
    m.box([-3.5, 0.4, -2.2], [4.0, 5.5, -1.6], 'body')
    m.box([-3.0, 0.4, -1.6], [2.0, 4.5, 1.6], 'body')
    m.box([-6.0, 3.5, -0.6], [-2.0, 5.5, 0.6], 'grip')
    m.rod(-1.6, -0.2, 3.4, 'blade', cx=0.5, cz=2.5, axis=2)


def staple_gun(m):
    m.box([-3.5, 0, -0.6], [3.0, 1.2, 0.6], 'body')
    m.box([-3.5, 1.2, -0.5], [2.0, 2.0, 0.5], 'metal')
    m.box([-3.0, 2.0, -0.4], [1.5, 2.6, 0.4], 'grip')


def caulk_gun(m):
    m.rod(-2.0, 7.0, 1.0, 'metal', axis=0)
    m.box([-3.5, -1.0, -0.3], [-2.0, 1.0, 0.3], 'metal')
    m.box([-3.4, -4.0, -0.4], [-2.4, -1.0, 0.4], 'grip')
    m.rod(-6.0, -3.5, 0.15, 'metal', axis=0)
    sh.shift_up(m, 4.0)


def paint_brush(m):
    m.box([-4.5, 0, -0.35], [0.0, 0.5, 0.35], 'wood')
    m.box([0.0, 0, -1.0], [1.0, 0.6, 1.0], 'metal')
    m.box([1.0, 0.05, -1.0], [3.2, 0.55, 1.0], 'bristle')


def paint_can(m):
    m.rod(0, 7.5, 3.3, 'label')
    m.rod(7.5, 7.7, 3.1, 'metal')
    m.box([-3.4, 7.0, -0.15], [-3.2, 9.5, 0.15], 'metal')
    m.box([3.2, 7.0, -0.15], [3.4, 9.5, 0.15], 'metal')
    m.box([-3.4, 9.3, -0.15], [3.4, 9.5, 0.15], 'metal')


def pry_bar(m):
    m.box([-6.0, 0, -0.35], [5.0, 0.4, 0.35], 'metal')
    m.seg((5.0, 0.2), (6.5, 1.4), 0.5, 0.7, 'metal')
    m.box([-7.0, 0, -0.5], [-6.0, 0.25, 0.5], 'metal')


def generator(m):
    for x in (-10.0, 10.0):
        m.box([x - 0.5, 0, -7.0], [x + 0.5, 15.0, -6.0], 'frame')
        m.box([x - 0.5, 0, 6.0], [x + 0.5, 15.0, 7.0], 'frame')
        m.box([x - 0.5, 14.0, -7.0], [x + 0.5, 15.0, 7.0], 'frame')
    m.box([-10.0, 14.0, -0.5], [10.0, 15.0, 0.5], 'frame')
    m.box([-8.0, 9.0, -5.5], [7.0, 14.0, 5.5], 'body')
    m.box([-7.0, 1.0, -5.0], [3.0, 9.0, 5.0], 'metal')
    m.box([3.0, 1.0, -3.0], [9.0, 7.0, 4.0], 'body')
    m.box([4.0, 2.0, 4.0], [8.0, 6.0, 4.3], 'mark')
    m.rod(14.0, 15.2, 1.2, 'cap', cx=-3.0, cz=0.0)


def welding_mask(m):
    m.box([-4.0, 0, -2.5], [4.0, 10.0, 2.5], 'body')
    m.box([-4.5, 0, -1.0], [4.5, 9.0, 2.0], 'body')
    m.box([-2.0, 5.0, 2.5], [2.0, 7.0, 2.6], 'glass')


def welding_rods(m):
    m.box([-7.0, 0, -0.8], [7.0, 1.6, 0.8], 'label')


def lock(m, combo=False):
    m.box([-1.0, 0, -0.5], [1.0, 2.0, 0.5], 'body')
    m.box([-0.9, 2.0, -0.2], [-0.5, 3.4, 0.2], 'metal')
    m.box([0.5, 2.0, -0.2], [0.9, 3.4, 0.2], 'metal')
    m.box([-0.9, 3.0, -0.2], [0.9, 3.4, 0.2], 'metal')
    if combo:
        m.rod(-0.5, 0.6, 0.6, 'mark', cx=0.0, cz=1.0, axis=2)


def lockpicks(m):
    m.box([-2.5, 0, -1.6], [2.5, 0.4, 1.6], 'cloth')
    for i in range(5):
        z = -1.0 + i * 0.5
        m.box([-2.0, 0.4, z - 0.05], [2.2, 0.48, z + 0.05], 'metal')


def multitool(m):
    m.box([-2.0, 0, -0.5], [2.0, 0.55, 0.5], 'metal')
    m.seg((2.0, 0.27), (3.3, 0.27), 0.35, 0.2, 'metal')
    m.box([-2.0, 0.55, -0.45], [2.0, 0.6, 0.45], 'grip')


def pocket_knife(m):
    m.box([-2.0, 0, -0.35], [1.6, 0.5, 0.35], 'grip')
    m.seg((1.6, 0.25), (3.6, 0.3), 0.55, 0.08, 'blade')


def fishing_rod(m):
    m.box([-20.0, 0, -0.2], [20.0, 0.4, 0.2], 'body')
    m.box([-20.0, 0, -0.35], [-14.0, 0.7, 0.35], 'grip')
    m.rod(-13.0, -11.5, 1.0, 'metal', cx=1.3, cz=0.0, axis=0)
    sh.shift_up(m, 0)


def trowel(m):
    m.box([-3.5, 0, -0.4], [0.0, 0.8, 0.4], 'wood')
    m.box([0.0, 0.2, -0.15], [1.0, 0.5, 0.15], 'metal')
    m.box([1.0, 0, -1.2], [5.0, 0.2, 1.2], 'metal')


def shears(m):
    m.seg((-6.0, 0.0), (0.0, 0.8), 0.8, 0.6, 'grip')
    m.seg((-6.0, 2.6), (0.0, 1.4), 0.8, 0.6, 'grip')
    m.box([0.0, 0.6, -0.4], [6.0, 1.0, 0.4], 'blade')
    m.box([0.0, 1.0, -0.4], [6.0, 1.4, 0.4], 'blade')
    lay_flat(m)


# ================================================================== materials


def small_box(w, h, d):
    return S(sh.box, w=w, h=h, d=d)


def rope_coil(m):
    m.rod(0, 1.6, 5.0, 'cloth')
    m.rod(1.6, 1.62, 3.0, 'core')
    m.seg((3.0, 1.6), (6.5, 0.4), 0.8, 0.8, 'cloth')


def chain_len(m):
    for i in range(8):
        x = -8.0 + i * 2.1
        if i % 2:
            m.box([x, 0, -0.15], [x + 2.4, 1.2, 0.15], 'metal')
        else:
            m.box([x, 0.45, -0.6], [x + 2.4, 0.75, 0.6], 'metal')


def spool_big(m):
    m.rod(0, 0.4, 3.0, 'body')
    m.rod(0.4, 3.6, 2.4, 'cloth')
    m.rod(3.6, 4.0, 3.0, 'body')


def timber(length, w, t, mat='wood'):
    def build(m):
        L = min(length, 44.0)
        m.box([-L / 2, 0, -w / 2], [L / 2, t, w / 2], mat)
    return build


def pipe(m):
    m.rod(-18.0, 18.0, 0.6, 'metal', axis=0)
    sh.shift_up(m, 0.6)


def sack_big(m):
    # a filled paper sack lying down: fat middle, pinched sewn ends
    m.box([-7.0, 0, -5.0], [7.0, 5.2, 5.0], 'label')
    m.box([-6.4, 5.2, -4.4], [6.4, 5.9, 4.4], 'label')
    for s in (-1, 1):
        x0, x1 = sorted((s * 7.0, s * 7.9))
        m.box([x0, 0.4, -4.4], [x1, 4.6, 4.4], 'side')
        x0, x1 = sorted((s * 7.9, s * 8.5))
        m.box([x0, 0.9, -4.0], [x1, 4.0, 4.0], 'side')


def roll_flat(d, length, mat='label'):
    def build(m):
        m.rod(-length / 2, length / 2, d / 2, mat, axis=0)
        m.rod(-length / 2 - 0.05, -length / 2, d / 5, 'core', axis=0)
        sh.shift_up(m, d / 2)
    return build


def glass_sheet(m):
    m.box([-12.0, 0, -0.15], [12.0, 18.0, 0.15], 'glass')
    m.box([-12.0, 0, -0.5], [12.0, 1.0, 0.5], 'cardboard')


def wire_roll(m):
    m.rod(0, 10.0, 6.0, 'wire')
    m.rod(10.0, 10.05, 4.0, 'core')


def block(m):
    m.box([-8.0, 0, -4.0], [8.0, 8.0, 4.0], 'stone')
    for x in (-4.0, 4.0):
        m.box([x - 2.6, 8.0, -2.6], [x + 2.6, 8.02, 2.6], 'dark')


def cylinder(m):
    m.rod(0, 14.0, 6.0, 'body')
    m.rod(14.0, 15.5, 4.5, 'body')
    m.rod(15.5, 17.0, 0.8, 'metal')
    m.box([-4.0, 16.0, -0.4], [4.0, 18.5, 0.4], 'body')
    m.box([-6.1, 1.0, -0.1], [6.1, 1.8, 0.1], 'body')


def jerrycan(m):
    m.box([-3.5, 0, -6.5], [3.5, 13.0, 6.5], 'body')
    m.box([-0.6, 13.0, -4.5], [0.6, 14.8, 2.0], 'body')
    m.rod(13.0, 14.6, 0.6, 'cap', cx=0.0, cz=4.6)
    m.box([-3.6, 4.0, -6.6], [3.6, 9.0, 6.6], 'mark')


# ================================================================== electrical


def battery_pack(n, d, h):
    def build(m):
        for i in range(n):
            m.rod(0, h, d / 2, 'label', cx=(i - (n - 1) / 2) * d * 1.02)
            m.rod(h, h + 0.12, d * 0.15, 'metal', cx=(i - (n - 1) / 2) * d * 1.02)
    return build


def nine_volt(m):
    m.box([-0.5, 0, -0.85], [0.5, 1.9, 0.85], 'label')
    m.rod(1.9, 2.1, 0.15, 'metal', cz=-0.4)
    m.box([-0.2, 1.9, 0.2], [0.2, 2.1, 0.6], 'metal')


def car_battery(m):
    m.box([-6.0, 0, -3.5], [6.0, 7.0, 3.5], 'body')
    m.box([-6.0, 7.0, -3.5], [6.0, 7.6, 3.5], 'dark')
    m.rod(7.6, 8.4, 0.5, 'metal', cx=-4.5, cz=-2.0)
    m.rod(7.6, 8.4, 0.5, 'metal', cx=4.5, cz=-2.0)
    m.box([-4.0, 2.0, 3.5], [4.0, 5.0, 3.55], 'label')


def lantern_battery(m):
    m.box([-1.4, 0, -1.4], [1.4, 4.4, 1.4], 'label')
    for x in (-0.7, 0.7):
        m.box([x - 0.05, 4.4, -0.05], [x + 0.05, 5.2, 0.05], 'metal')


def cord_coil(m):
    m.rod(0, 1.2, 4.0, 'body')
    m.rod(1.2, 1.22, 2.6, 'core')
    m.box([3.6, 0, 2.4], [5.2, 1.0, 3.6], 'cap')


def bulb(m):
    m.rod(0, 0.9, 0.45, 'metal')
    m.rod(0.9, 1.6, 0.7, 'glass')
    m.rod(1.6, 3.6, 1.2, 'glass')
    m.rod(3.6, 4.0, 0.8, 'glass')


def jumper_cables(m):
    m.rod(0, 1.4, 4.5, 'red')
    m.rod(1.4, 2.6, 4.2, 'body')
    m.rod(2.6, 2.62, 2.8, 'core')
    m.box([3.0, 2.6, 0], [5.0, 3.4, 1.4], 'red')
    m.box([3.0, 2.6, -1.6], [5.0, 3.4, -0.2], 'body')


def gadget(w, h, d, screen=None, keys=None, antenna=None, grille=None):
    """A box-shaped device: screen (x0,y0,x1,y1), key block, an antenna, a speaker grille, all on the front."""
    def build(m):
        m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'body')
        if screen:
            x0, y0, x1, y1 = screen
            m.box([x0, y0, d / 2], [x1, y1, d / 2 + 0.05], 'glass')
        if keys:
            x0, y0, x1, y1 = keys
            m.box([x0, y0, d / 2], [x1, y1, d / 2 + 0.08], 'keys')
        if grille:
            x0, y0, x1, y1 = grille
            m.box([x0, y0, d / 2], [x1, y1, d / 2 + 0.04], 'grille')
        if antenna:
            x, length = antenna
            m.rod(h, h + length, 0.12 if length > 3 else 0.3, 'metal' if length > 3 else 'dark', cx=x)
    return build


def headphones(m):
    m.arc((0, 2.0), 3.0, 0, 180, 0.5, 0.6, 'body')
    for s in (-1, 1):
        m.box([s * 3.0 - 0.5, 0, -1.2], [s * 3.0 + 0.5, 2.4, 1.2], 'cloth')
    sh.shift_up(m, 0)


def tape(m):
    m.box([-2.0, 0, -1.25], [2.0, 0.45, 1.25], 'body')
    m.box([-1.6, 0.45, -0.9], [1.6, 0.48, 0.3], 'label')
    m.box([-1.0, 0.45, 0.4], [1.0, 0.48, 0.9], 'glass')


def vhs(m):
    m.box([-3.7, 0, -2.0], [3.7, 1.0, 2.0], 'body')
    m.box([-3.0, 1.0, -1.6], [3.0, 1.02, 0.2], 'label')
    m.box([-1.6, 1.0, 0.5], [1.6, 1.02, 1.4], 'glass')


def camera(m, kind='instant'):
    if kind == 'instant':
        m.box([-3.0, 0, -2.0], [3.0, 4.0, 2.0], 'body')
        m.box([-3.0, 4.0, -2.0], [3.0, 4.6, 0.5], 'body')
        m.rod(2.0, 2.6, 1.0, 'dark', cx=0.0, cz=1.6, axis=2)
        m.box([-2.6, 3.4, 2.0], [-1.6, 4.0, 2.1], 'glass')
        m.box([-3.0, 1.0, 2.0], [3.0, 1.3, 2.05], 'red')
    else:
        m.box([-2.8, 0, -1.0], [2.8, 3.0, 1.0], 'body')
        m.box([-2.8, 2.2, -1.0], [2.8, 3.0, 1.0], 'metal')
        m.rod(1.0, 3.0, 1.1, 'dark', cx=0.0, cz=1.4, axis=2)
        m.box([-0.6, 3.0, -0.6], [0.6, 3.6, 0.6], 'dark')


def camcorder(m):
    m.box([-1.6, 0, -5.0], [1.6, 3.4, 5.0], 'body')
    m.rod(5.0, 6.6, 1.2, 'dark', cx=0.0, cz=1.8, axis=2)
    m.box([-0.6, 3.4, -4.5], [0.6, 4.4, -1.0], 'dark')


def film(m):
    m.rod(0, 2.0, 0.6, 'label')
    m.rod(2.0, 2.3, 0.25, 'metal')


def brick_phone(m):
    m.box([-1.1, 0, -0.9], [1.1, 9.0, 0.9], 'body')
    m.box([-0.8, 6.0, 0.9], [0.8, 7.2, 0.95], 'glass')
    m.box([-0.8, 1.0, 0.9], [0.8, 5.5, 1.0], 'keys')
    m.rod(9.0, 11.0, 0.2, 'dark', cx=0.6)


def boombox(m):
    gadget(20.0, 10.0, 5.0, screen=(-3.0, 5.5, 3.0, 8.5), keys=(-3.0, 8.6, 3.0, 9.4), grille=None)(m)
    for x in (-6.5, 6.5):
        m.rod(2.5, 2.6, 3.4, 'grille', cx=x, cz=4.5, axis=2)
    for x in (-8.5, 8.5):
        m.box([x - 0.5, 10.0, -0.5], [x + 0.5, 13.0, 0.5], 'metal')
    m.box([-9.0, 12.4, -0.5], [9.0, 13.0, 0.5], 'metal')


def circuit_board(m):
    m.box([-3.0, 0, -2.0], [3.0, 0.1, 2.0], 'board')
    for x, z in ((-1.5, -0.5), (0.8, 0.6), (1.8, -1.0), (-0.4, 1.2)):
        m.box([x - 0.5, 0.1, z - 0.3], [x + 0.5, 0.35, z + 0.3], 'dark')


def scrap_heap(m):
    m.box([-2.5, 0, -2.0], [2.5, 0.8, 2.0], 'board')
    m.box([-1.6, 0.8, -1.2], [1.0, 1.4, 1.4], 'body')
    m.rod(0.8, 1.6, 0.6, 'metal', cx=1.6, cz=-0.6)


# ================================================================== light & fire


def flashlight(length=7.0, head=1.0, body=0.6):
    def build(m):
        m.rod(-length / 2, length / 2 - 2.0, body, 'body', axis=0)
        m.rod(length / 2 - 2.0, length / 2 - 1.2, (head + body) / 2, 'body', axis=0)
        m.rod(length / 2 - 1.2, length / 2, head, 'body', axis=0)
        m.rod(length / 2, length / 2 + 0.06, head * 0.8, 'lens', axis=0)
        m.box([-0.4, body * 0.6, -0.25], [0.4, body + 0.15, 0.25], 'red')
        sh.shift_up(m, head)
    return build


def headlamp(m):
    m.arc((0, 0), 3.0, 200, 340, 0.8, 0.4, 'cloth')
    m.box([-1.0, -3.2, 0.2], [1.0, -1.8, 1.4], 'body')
    m.box([-0.7, -2.9, 1.4], [0.7, -2.1, 1.45], 'lens')
    lay_flat(m)


def lantern(kind='kerosene'):
    def build(m):
        m.rod(0, 2.0, 2.6, 'body')
        m.rod(2.0, 6.5, 1.8, 'glass')
        for a in range(0, 360, 90):
            x, z = 2.0 * np.cos(np.radians(a)), 2.0 * np.sin(np.radians(a))
            m.box([x - 0.12, 2.0, z - 0.12], [x + 0.12, 6.5, z + 0.12], 'metal')
        m.rod(6.5, 7.6, 2.0, 'body')
        m.rod(7.6, 8.2, 0.8, 'body')
        m.box([-2.4, 8.2, -0.12], [2.4, 8.5, 0.12], 'metal')
        m.box([-2.4, 7.0, -0.12], [-2.1, 8.5, 0.12], 'metal')
        m.box([2.1, 7.0, -0.12], [2.4, 8.5, 0.12], 'metal')
    return build


def candle(d, h):
    def build(m):
        m.rod(0, h, d / 2, 'wax')
        m.box([-0.05, h, -0.05], [0.05, h + 0.4, 0.05], 'dark')
    return build


def matchbox(m):
    m.box([-1.1, 0, -0.75], [1.1, 0.6, 0.75], 'label')
    m.box([1.1, 0.05, -0.75], [1.2, 0.55, 0.75], 'mark')


def lighter(brass=False):
    def build(m):
        if brass:
            m.box([-0.75, 0, -0.45], [0.75, 2.2, 0.45], 'metal')
            m.box([-0.75, 2.2, -0.45], [0.75, 3.0, 0.45], 'metal')
        else:
            m.box([-0.5, 0, -0.35], [0.5, 2.4, 0.35], 'body')
            m.box([-0.5, 2.4, -0.35], [0.5, 2.9, 0.35], 'metal')
            m.box([0.1, 2.9, -0.2], [0.45, 3.1, 0.2], 'dark')
    return build


def flare_gun(m):
    m.box([-1.0, 2.0, -0.6], [5.5, 3.4, 0.6], 'body')
    m.rod(5.5, 6.0, 0.75, 'body', cx=2.7, cz=0.0, axis=0)
    m.box([-1.2, 0, -0.55], [0.6, 2.6, 0.55], 'body')
    m.box([0.6, 1.4, -0.15], [1.4, 2.0, 0.15], 'dark')
    m.box([-1.6, 3.0, -0.2], [-1.0, 3.6, 0.2], 'dark')


def glow_stick(m):
    m.rod(-3.0, 3.0, 0.3, 'lens', axis=0)
    m.rod(3.0, 3.4, 0.2, 'dark', axis=0)
    sh.shift_up(m, 0.3)


def firestarter(m):
    m.box([-1.5, 0, -0.3], [1.0, 0.6, 0.3], 'metal')
    m.box([1.0, 0, -0.35], [2.4, 0.7, 0.35], 'body')


def wood_bundle(m):
    for i, (x, z, y) in enumerate(((-1.6, -1.6, 0), (1.6, -1.6, 0), (0, 1.4, 0), (-0.8, 0, 2.6), (0.9, 0, 2.6))):
        m.box([-7.0, y, z - 1.2 + (0.2 if i % 2 else 0)], [7.0, y + 2.5, z + 1.2], 'wood')
        m.parts[-1]['lo'][0] += x * 0.3
        m.parts[-1]['hi'][0] += x * 0.3


# ================================================================== camping & farm


def tent_bag(m):
    m.rod(-8.0, 8.0, 3.0, 'cloth', axis=0)
    m.box([-8.2, 0.8, -0.3], [-7.8, 5.2, 0.3], 'strap')
    m.box([7.8, 0.8, -0.3], [8.2, 5.2, 0.3], 'strap')
    sh.shift_up(m, 3.0)


def roll_bag(d, length, strap=True):
    def build(m):
        m.rod(-length / 2, length / 2, d / 2, 'cloth', axis=0)
        if strap:
            for x in (-length * 0.3, length * 0.3):
                m.rod(x - 0.4, x + 0.4, d / 2 + 0.1, 'strap', axis=0)
        sh.shift_up(m, d / 2)
    return build


def compass(m):
    m.rod(0, 0.6, 1.4, 'body')
    m.rod(0.6, 0.62, 1.15, 'dial')
    m.box([-0.08, 0.62, -0.9], [0.08, 0.66, 0.9], 'red')
    m.box([-0.25, 0.1, 1.4], [0.25, 0.5, 1.8], 'metal')


def binoculars(m):
    for x in (-1.6, 1.6):
        m.rod(0, 4.5, 1.2, 'body', cx=x)
        m.rod(4.5, 5.6, 0.8, 'dark', cx=x)
        m.rod(-0.05, 0, 1.0, 'lens', cx=x)
    m.box([-0.6, 2.0, -0.5], [0.6, 4.0, 0.5], 'dark')
    lay(m, None)


def bottle_filter(m):
    m.rod(0, 7.0, 1.3, 'body')
    m.rod(7.0, 8.0, 0.8, 'cap')
    m.rod(1.5, 5.5, 1.33, 'label')


def tablets(m):
    m.box([-1.2, 0, -0.4], [1.2, 2.6, 0.4], 'label')


def flat_pack(w, h, d):
    return S(sh.box, w=w, h=h, d=d)


def trap(m):
    m.box([-6.0, 0, -4.0], [6.0, 0.3, 4.0], 'metal')
    for x in (-6.0, 5.8):
        m.box([x, 0.3, -4.0], [x + 0.2, 7.0, 4.0], 'wire')
    for z in (-4.0, 3.8):
        m.box([-6.0, 0.3, z], [6.0, 7.0, z + 0.2], 'wire')
    m.box([-6.0, 7.0, -4.0], [6.0, 7.2, 4.0], 'wire')
    m.box([-1.0, 7.2, -0.3], [1.0, 7.6, 0.3], 'metal')


def snare(m):
    m.rod(0, 0.4, 2.0, 'wire')
    m.rod(0.4, 0.42, 1.4, 'core')


def sheath(m):
    m.box([-1.0, 0, -0.35], [1.0, 8.0, 0.35], 'leather')
    m.box([-1.2, 6.5, -0.45], [1.2, 7.5, 0.45], 'leather')


def decoy(m):
    m.box([-4.5, 0, -2.0], [4.0, 2.8, 2.0], 'body')
    m.box([-3.5, 2.8, -1.6], [2.5, 3.4, 1.6], 'body')
    m.box([2.6, 2.6, -0.9], [4.2, 5.0, 0.9], 'head')
    m.box([4.2, 3.6, -0.4], [5.4, 4.2, 0.4], 'bill')


def seed_packet(m):
    m.box([-1.6, 0, -0.08], [1.6, 4.6, 0.08], 'label')


def watering_can(m):
    m.box([-4.0, 0, -2.6], [4.0, 7.5, 2.6], 'body')
    m.seg((3.5, 2.0), (9.5, 8.0), 0.8, 0.8, 'body')
    m.box([9.0, 7.6, -0.7], [10.2, 8.6, 0.7], 'body')
    m.box([-4.5, 4.0, -0.4], [-4.0, 9.0, 0.4], 'body')
    m.box([-4.5, 8.6, -0.4], [2.0, 9.0, 0.4], 'body')


def pail(m):
    m.rod(0, 10.0, 5.2, 'metal')
    m.rod(9.6, 10.0, 5.5, 'metal')
    m.arc((0, 10.0), 5.2, 0, 180, 0.25, 0.25, 'metal')


def basket(m):
    m.rod(0, 4.0, 4.5, 'wicker')
    m.arc((0, 4.0), 3.6, 0, 180, 0.5, 0.5, 'wicker')


def saddle(m):
    m.box([-8.0, 0, -6.0], [8.0, 4.0, 6.0], 'leather')
    m.box([-6.0, 4.0, -5.0], [6.0, 6.0, 5.0], 'leather')
    m.box([4.0, 6.0, -1.0], [6.5, 9.0, 1.0], 'leather')
    for z in (-6.5, 6.0):
        m.box([-1.0, -3.0 + 3.0, z], [1.0, 4.0, z + 0.5], 'strap')
    sh.shift_up(m, 0)


def horseshoe(m):
    m.arc((0, 0), 2.2, -30, 210, 0.7, 0.35, 'metal')
    lay_flat(m)


# ================================================================== car


def tyre(m):
    m.rod(0, 8.0, 13.0, 'rubber')
    m.rod(8.0, 8.05, 8.0, 'metal')
    m.rod(8.05, 8.4, 2.5, 'metal')
    m.rod(-0.05, 0, 8.0, 'metal')


def lug_wrench(m):
    m.box([-10.0, 0, -0.4], [10.0, 0.8, 0.4], 'metal')
    m.box([-0.4, 0, -10.0], [0.4, 0.8, 10.0], 'metal')
    for x, z in ((-10.0, 0), (10.0, 0), (0, -10.0), (0, 10.0)):
        m.box([x - 0.6, 0, z - 0.6], [x + 0.6, 1.0, z + 0.6], 'metal')


def scissor_jack(m):
    m.box([-6.0, 0, -2.0], [6.0, 0.8, 2.0], 'body')
    m.seg((-5.0, 0.8), (0.0, 4.0), 1.0, 2.6, 'body')
    m.seg((5.0, 0.8), (0.0, 4.0), 1.0, 2.6, 'body')
    m.box([-2.0, 4.0, -1.4], [2.0, 4.8, 1.4], 'body')
    m.rod(-7.0, 7.0, 0.3, 'metal', cx=2.4, cz=0.0, axis=0)


def quart(m):
    m.box([-2.0, 0, -1.4], [2.0, 7.0, 1.4], 'label')
    m.rod(7.0, 8.4, 0.6, 'cap', cx=-1.0)


def plugs(m):
    for x in (-1.2, 0, 1.2):
        m.rod(0, 1.0, 0.35, 'metal', cx=x)
        m.rod(1.0, 2.0, 0.42, 'metal', cx=x)
        m.rod(2.0, 3.6, 0.3, 'white', cx=x)


def belt(m):
    # a rubber loop stood on its edge, ribbed inside
    sh.torus(m, (0, 4.6), 4.3, 0.6, 1.2, 'rubber')


def blades(m):
    for z in (-0.6, 0.6):
        m.box([-10.0, 0, z - 0.3], [10.0, 0.6, z + 0.3], 'rubber')


def hose(m):
    m.seg((-6.0, 1.0), (0.0, 1.0), 1.6, 1.6, 'rubber')
    m.seg((0.0, 1.0), (4.0, 4.0), 1.6, 1.6, 'rubber')
    for x in (-5.8, 3.0):
        m.box([x - 0.2, 0.0, -1.0], [x + 0.2, 2.0, 1.0], 'metal')


def oil_filter(m):
    m.rod(0, 4.5, 1.8, 'label')
    m.rod(4.5, 4.9, 1.6, 'metal')


def strap_coil(m):
    m.rod(0, 2.0, 4.0, 'strap')
    m.rod(2.0, 2.02, 2.0, 'core')
    m.box([3.6, 0.6, -0.8], [5.4, 1.4, 0.8], 'metal')


def siphon(m):
    m.rod(0, 1.0, 4.0, 'glass')
    m.rod(1.0, 1.02, 3.0, 'core')
    m.rod(0, 2.6, 0.8, 'red', cx=4.6)


def chock(m):
    m.box([-4.0, 0, -3.0], [4.0, 1.0, 3.0], 'rubber')
    m.seg((-4.0, 1.0), (3.0, 4.6), 1.6, 6.0, 'rubber')


def table(it):
    WOOD = A.flat('#8d6034', 0.1)
    G = {}
    G.update({
        # ------------------------------------------------------------ kitchen
        'bottle_opener': it(stick_tool(5.0, 0.8, (1.4, 1.4, 0.18), grip='metal', t=0.18), metal=A.steel),
        'cooking_pot': it(pot(8.0, 5.0), handle=A.plastic('#1e1f20'), lid=A.steel),
        'saucepan': it(pot(6.0, 3.5, handles='long'), handle=A.plastic('#1e1f20'), lid=A.steel),
        'kettle': it(kettle, metal=A.chrome, handle=A.plastic('#1e1f20')),
        'mug': it(mug, body='#e8e4da', food=A.flat('#3a2418')),
        'plate': it(plate, body='#efeae0', mark=A.flat('#2a5aa8')),
        'bowl': it(bowl, body='#e8e4da'),
        'cutlery': it(cutlery, metal=A.chrome),
        'rolling_pin': it(rolling_pin, wood=A.flat('#c8a070', 0.08)),
        'spatula': it(stick_tool(11.0, 0.8, (3.5, 2.6, 0.12)), handle=A.plastic('#1e1f20'), metal=A.steel),
        'ladle': it(stick_tool(12.0, 0.7, (3.0, 3.0, 1.2)), handle=A.plastic('#1e1f20'), metal=A.chrome),
        'cutting_board': it(cutting_board, wood=A.flat('#c8a070', 0.08)),
        'tupperware': it(S(sh.tub, d=5.0, h=2.4), '', '#dfe4e2', cap='#3a7ab0'),
        'water_jug_empty': it(sh.jug, '', '#d8e4e8', body='#d8e4e8', cap='#2a6ab0', label=A.plastic('#d8e4e8')),
        'canteen': it(canteen, metal=A.steel, cloth=A.fabric('#5a5a3a'), cap='#2a2a2a'),
        'thermos': it(lambda m: (m.rod(0, 9.5, 1.6, 'body'), m.rod(9.5, 10.4, 1.25, 'metal'), m.rod(10.4, 12.6, 1.66, 'cap')),
                      body='#2f5a3e', cap='#3a3a3a'),
        'camp_stove': it(camp_stove, body='#2f6a3e'),
        'stove_fuel': it(canister, 'FUEL', '#c8402a', band=('#e8e4da')),
        'mess_kit': it(mess_kit, metal=A.alu),
        'aluminium_foil': it(foil_roll('#c8c8c8'), 'FOIL', '#2a5aa8', band='#e8e4da'),
        'plastic_wrap': it(foil_roll('#e0e0e0'), 'WRAP', '#c23a22', band='#e8e4da'),
        'paper_towels': it(S(sh.roll, d=4.8, h=11.0, core=1.6), label=A.paper('#f2efe8')),
        'cast_iron_skillet': it(skillet, metal=lambda f: P.steel(f, 0.25, tint='#3a3a3a', spread=0.06)),
        'coffee_pot': it(percolator, metal=A.chrome, handle=A.plastic('#1e1f20'), tint='#8a5a2a'),
        # ------------------------------------------------------------ hand tools
        'screwdriver': it(screwdriver, grip=A.plastic('#c8402a'), metal=A.chrome),
        'screwdriver_set': it(case_box(8.0, 1.6, 4.0), body='#2a5aa8', mark=A.flat('#e8c040')),
        'pliers': it(pliers(), grip=A.plastic('#c23a22'), metal=A.steel),
        'wire_cutters': it(pliers(cut=True), grip=A.plastic('#e0b020'), metal=A.steel),
        'adjustable_wrench': it(wrench, metal=A.chrome, mark=A.steel),
        'socket_set': it(case_box(14.0, 3.0, 8.0), body='#2a2a2a', mark=A.flat('#c23a22')),
        'tape_measure': it(tape_measure, body='#e0b020'),
        'level': it(spirit_level, body='#e0b020', tint='#8ad04a', liquid='#6ab03a'),
        'handsaw': it(saw(4.0, 24.0), metal=A.steel, wood=WOOD),
        'hacksaw': it(saw(hack=True), body='#2a5aa8', grip=A.plastic('#1e1f20'), metal=A.steel),
        'bolt_cutters': it(bolt_cutters, metal=A.steel, grip=A.plastic('#c23a22')),
        'utility_knife': it(utility_knife, body='#e0b020'),
        'whetstone': it(whetstone, body='#8a8a80', mark=A.flat('#5a5a52')),
        'file_tool': it(stick_tool(10.0, 0.8, (6.0, 0.9, 0.25)), handle=WOOD, metal=A.steel),
        'chisel': it(stick_tool(9.0, 0.9, (4.0, 0.9, 0.3)), handle=WOOD, metal=A.steel),
        'hand_drill': it(hand_drill, metal=A.steel, wood=WOOD, red=A.flat('#a8281f')),
        'power_drill': it(power_drill, 'DRILL', '#1e1f20', band='#e0b020', body='#e0b020', grip=A.plastic('#1e1f20'),
                          red=A.flat('#c23a22')),
        'circular_saw': it(circular_saw, body='#2a6a8a', grip=A.plastic('#1e1f20'), metal=A.steel),
        'staple_gun': it(staple_gun, body='#2a2a2a', metal=A.steel, grip=A.plastic('#2a2a2a')),
        'caulk_gun': it(caulk_gun, metal=A.steel, grip=A.plastic('#2a2a2a')),
        'paint_brush': it(paint_brush, wood=WOOD, metal=A.steel, bristle=A.flat('#2a2a2a')),
        'paint_can': it(paint_can, 'PAINT', '#e8e4da', 'drop', '#3a7ab0', band='#e8e4da'),
        'pry_bar': it(pry_bar, metal=lambda f: P.paint(f, '#2a5aa8', steel_base=0.5, chips=0.5)),
        'toolbox': it(case_box(16.0, 7.0, 7.0), body='#b02a22', mark=A.flat('#8a1a14')),
        'mechanic_toolbox': it(case_box(24.0, 14.0, 12.0), body='#b02a22', mark=A.chrome),
        'portable_generator': it(generator, body='#c89020', frame=A.plastic('#2a2a2a'), metal=A.steel,
                                 mark=A.flat('#2a2a2a'), cap='#2a2a2a'),
        'welding_mask': it(welding_mask, body='#2a2a2a', tint='#2a4a2a'),
        'welding_rods': it(welding_rods, 'RODS', '#3a6a8a', band='#e8e4da'),
        'gas_mask_filter': it(S(sh.can, d=3.4, h=1.6), 'FILTER', '#5a5a3a', band='#e8e0c0'),
        'padlock': it(lock, body='#c8a040', metal=A.chrome),
        'combination_lock': it(lambda m: lock(m, combo=True), body='#2a2a2a', metal=A.chrome, mark=A.chrome),
        'lockpick_set': it(lockpicks, cloth=A.fabric('#2a2a2a'), metal=A.chrome),
        'multitool': it(multitool, metal=A.chrome, grip=A.plastic('#2a2a2a')),
        'pocket_knife': it(pocket_knife, grip=A.plastic('#a8281f')),
        'fishing_rod': it(fishing_rod, body='#2a2a2a', grip=A.flat('#8a6a3a'), metal=A.chrome),
        'tackle_box': it(case_box(12.0, 5.0, 6.0), body='#3a6a3a', mark=A.flat('#2a4a2a')),
        'garden_trowel': it(trowel, wood=A.flat('#3a7a3a'), metal=A.steel),
        'garden_shears': it(shears, grip=A.plastic('#3a7a3a')),
        # ------------------------------------------------------------ materials
        'screws': it(small_box(3.0, 2.0, 1.6), 'SCREWS', '#c8402a', band=CREAM2),
        'hinges': it(small_box(3.5, 4.5, 0.8), 'HINGES', '#2a5aa8', band=CREAM2),
        'electrical_tape': it(S(sh.tape_roll, d=2.4, w=0.8), label=A.plastic('#1e1f20'), core=A.flat('#d8d0c0')),
        'superglue': it(S(sh.tube, length=2.8, w=0.7), 'GLUE', '#e0b020', band='#1e1f20', word_ink='#e0b020', cap='#c23a22'),
        'rope': it(rope_coil, cloth=A.fabric('#c8a870')),
        'paracord': it(rope_coil, cloth=A.fabric('#5a5a3a')),
        'chain': it(chain_len, metal=A.steel),
        'wire_spool': it(spool_big, body='#2a2a2a', cloth=A.flat('#c87a3a')),
        'zip_ties': it(S(sh.bag, w=3.0, h=7.0, d=0.6), 'ZIP TIES', '#e8e4da', band='#2a2a2a', word_ink='#e8e4da'),
        'sandpaper': it(small_box(4.0, 5.0, 0.1), 'GRIT', '#c8402a', band='#d8c8a0'),
        'plank': it(timber(36.0, 5.5, 0.75), wood=WOOD),
        'two_by_four': it(timber(40.0, 3.5, 1.5), wood=A.flat('#c8a070', 0.08)),
        'plywood_sheet': it(timber(44.0, 22.0, 0.75), wood=A.flat('#c8a070', 0.1)),
        'metal_pipe': it(pipe, metal=A.steel),
        'rebar_rod': it(lambda m: (m.rod(-20, 20, 0.3, 'metal', axis=0), sh.shift_up(m, 0.3)),
                        metal=lambda f: P.steel(f, 0.35, tint='#6a4a3a', spread=0.1)),
        'cement_bag': it(sack_big, 'CEMENT', '#c8c0b0', band='#2a5aa8', word_ink='#e8e4da'),
        'plastic_sheeting': it(roll_flat(4.0, 20.0), label=A.plastic('#c8d4d4')),
        'glass_pane': it(glass_sheet, tint='#c8dce0', cardboard=A.cardboard),
        'barbed_wire_roll': it(wire_roll, wire=A.steel, core=A.flat('#2a2a2a')),
        'fence_posts': it(timber(40.0, 4.0, 4.0), wood=WOOD),
        'cinder_block': it(block, stone=A.flat('#8a8a84', 0.12), dark=A.flat('#3a3a38')),
        'propane_cylinder': it(cylinder, body='#e8e4da', metal=A.steel),
        'gas_can': it(jerrycan, body='#b02a22', cap='#2a2a2a', mark=A.flat('#e0b020')),
        # ------------------------------------------------------------ electrical
        'd_battery': it(battery_pack(2, 1.3, 2.4), 'D', '#2a2a2a', band='#c8a040', word_ink='#2a2a2a'),
        'nine_volt': it(nine_volt, '9V', '#2a2a2a', band='#c8402a', word_ink='#e8e4da'),
        'car_battery': it(car_battery, 'BATTERY', '#2a2a2a', band='#c8402a', word_ink='#e8e4da', body='#2a2a2a',
                          dark=A.plastic('#1a1a1a')),
        'lantern_battery': it(lantern_battery, '6V', '#2a2a2a', band='#e07a1a'),
        'extension_cord': it(cord_coil, body='#e07a1a', cap='#2a2a2a'),
        'light_bulb': it(bulb, tint='#f0f0e8', metal=A.alu),
        'fuse_box': it(small_box(2.5, 1.5, 0.6), 'FUSES', '#c8402a', band=CREAM2),
        'jumper_cables': it(jumper_cables, body='#1e1f20', red=A.plastic('#b02a22')),
        'calculator': it(gadget(3.0, 5.2, 0.5, screen=(-1.2, 3.8, 1.2, 4.8), keys=(-1.2, 0.4, 1.2, 3.4)),
                         body='#2a2a2a', tint='#8a9a7a', keys=A.plastic('#d8d0c0')),
        'wall_phone': it(gadget(4.0, 8.0, 3.0, keys=(-1.2, 1.5, 1.2, 4.5)), body='#c8b890', keys=A.plastic('#e8e4da')),
        'answering_machine': it(gadget(6.0, 2.2, 8.0, keys=(-2.5, 0.6, 2.5, 1.6)), body='#2a2a2a', keys=A.plastic('#8a8a8a')),
        'transistor_radio': it(gadget(6.0, 4.0, 1.8, grille=(-2.6, 0.5, 0.6, 3.5), screen=(1.0, 1.0, 2.6, 3.0)),
                               body='#a8281f', grille=A.chrome, tint='#e8e0c0'),
        'cb_radio': it(gadget(7.0, 2.4, 8.0, screen=(-2.8, 1.2, 0.0, 2.0), keys=(0.5, 0.5, 3.0, 2.0)), body='#2a2a2a',
                       tint='#c86a1a', keys=A.chrome),
        'police_scanner': it(gadget(3.0, 7.0, 1.6, screen=(-1.1, 4.6, 1.1, 6.0), keys=(-1.1, 1.0, 1.1, 4.0), antenna=(0.8, 4.0)),
                             body='#2a2a2a', tint='#c86a1a', keys=A.plastic('#5a5a5a')),
        'ham_radio': it(gadget(14.0, 5.0, 11.0, screen=(-5.0, 2.6, 0.0, 4.2), keys=(1.0, 0.8, 6.0, 4.2), grille=(-5.0, 0.6, 0.0, 2.2)),
                        body='#3a3a3a', tint='#c8a040', keys=A.chrome, grille=A.plastic('#1e1f20')),
        'satellite_phone': it(gadget(2.4, 6.0, 1.4, screen=(-0.9, 3.8, 0.9, 5.2), keys=(-0.9, 0.6, 0.9, 3.4), antenna=(0.7, 2.0)),
                              body='#2a2a2a', tint='#7a9a8a', keys=A.plastic('#5a5a5a')),
        'gps_unit': it(gadget(3.0, 6.0, 1.6, screen=(-1.1, 3.0, 1.1, 5.4), keys=(-1.1, 0.6, 1.1, 2.6), antenna=(0.9, 1.4)),
                       body='#4a4a2a', tint='#7a9a8a', keys=A.plastic('#2a2a2a')),
        'cassette_player': it(gadget(4.3, 3.4, 1.3, screen=(-1.6, 0.55, 1.6, 2.45), keys=(-1.7, 2.6, 1.7, 3.2)),
                              body='#9aa0a3', tint='#3a3c3e', keys=A.plastic('#2a2b2d')),
        'headphones': it(headphones, body='#2a2a2a', cloth=A.fabric('#e07a1a')),
        'cassette_tape': it(tape, body='#2a2a2a', label=A.flat('#e8e0c0'), tint='#8a8a8a'),
        'mixtape': it(tape, body='#d8d0c0', label=A.flat('#c23a7a'), tint='#8a8a8a'),
        'boombox': it(boombox, body='#4a4a4a', tint='#2a2a2a', keys=A.chrome, grille=A.plastic('#1e1f20'), metal=A.chrome),
        'vhs_tape': it(vhs, body='#1e1f20', label=A.flat('#e8e4da'), tint='#5a5a5a'),
        'vcr': it(gadget(16.0, 3.5, 12.0, screen=(-6.0, 1.0, 2.0, 2.6), keys=(3.0, 0.8, 7.0, 2.6)), body='#2a2a2a',
                  tint='#3a6a5a', keys=A.chrome),
        'handheld_game': it(gadget(3.5, 5.8, 1.2, screen=(-1.3, 3.0, 1.3, 5.0), keys=(-1.3, 0.8, 1.3, 2.4)),
                            body='#b8b4a8', tint='#8a9a5a', keys=A.plastic('#7a2a4a')),
        'game_cartridge': it(small_box(2.2, 2.6, 0.3), 'GAME', '#8a8a84', band='#2a2a2a', word_ink='#e8e4da'),
        'instant_camera': it(camera, body='#2a2a2a', dark=A.plastic('#1a1a1a'), tint='#5a6a7a', red=A.flat('#c23a22')),
        'film_camera': it(lambda m: camera(m, 'slr'), body='#1e1f20', metal=A.chrome, dark=A.plastic('#1a1a1a')),
        'camcorder': it(camcorder, body='#2a2a2a', dark=A.plastic('#1a1a1a')),
        'film_roll': it(film, 'FILM', '#e0b020', band='#c23a22', word_ink='#e0b020', metal=A.alu),
        'pager': it(gadget(2.2, 3.0, 0.8, screen=(-0.9, 1.6, 0.9, 2.6)), body='#1e1f20', tint='#8a9a7a'),
        'portable_tv': it(gadget(8.0, 7.0, 7.0, screen=(-3.6, 1.6, 1.4, 6.0), keys=(2.0, 1.6, 3.6, 6.0), antenna=(2.6, 6.0)),
                          body='#c8c0b0', tint='#3a4a4a', keys=A.plastic('#2a2a2a')),
        'car_phone': it(gadget(3.0, 2.4, 8.0, keys=(-1.0, 0.6, 1.0, 2.0)), body='#1e1f20', keys=A.plastic('#5a5a5a')),
        'brick_phone': it(brick_phone, body='#2a2a2a', tint='#8a9a7a', keys=A.plastic('#8a8a8a'), dark=A.plastic('#1a1a1a')),
        'generator_parts': it(scrap_heap, body='#c89020', board=A.flat('#3a5a3a'), metal=A.steel),
        'electronics_scrap': it(scrap_heap, body='#2a2a2a', board=A.flat('#2f6a3a'), metal=A.alu),
        'circuit_board': it(circuit_board, board=A.flat('#2f6a3a'), dark=A.plastic('#1a1a1a')),
        # ------------------------------------------------------------ light & fire
        'flashlight': it(flashlight(7.0, 1.0, 0.6), body='#c23a22', lens=A.flat('#f0ecd0'), red=A.plastic('#1e1f20')),
        'big_flashlight': it(flashlight(14.0, 1.2, 0.8), body='#1e1f20', lens=A.flat('#f0ecd0'), red=A.plastic('#5a5a5a')),
        'headlamp': it(headlamp, cloth=A.fabric('#2a2a2a'), body='#c23a22', lens=A.flat('#f0ecd0')),
        'penlight': it(flashlight(5.0, 0.3, 0.28), body='#c8c8c8', lens=A.flat('#f0ecd0'), red=A.plastic('#1e1f20')),
        'lantern_battery_light': it(lantern('battery'), body='#3a6a3a', tint='#e8e4c8', metal=A.plastic('#2a2a2a')),
        'kerosene_lantern': it(lantern(), body='#a8281f', tint='#e8e0b0', liquid='#c8a040', level=0.2),
        'kerosene': it(jerrycan, body='#2a5aa8', cap='#2a2a2a', mark=A.flat('#e8e4da')),
        'candle': it(candle(1.2, 5.0), wax=A.flat('#efe6d0', 0.04), dark=A.flat('#2a2a2a')),
        'church_candle': it(candle(2.6, 9.0), wax=A.flat('#f2ecd8', 0.04), dark=A.flat('#2a2a2a')),
        'matches': it(matchbox, 'MATCHES', '#c23a22', band='#e0c040', mark=A.flat('#5a3a2a')),
        'lighter': it(lighter(), body='#2a6ab0', metal=A.chrome, dark=A.flat('#2a2a2a')),
        'brass_lighter': it(lighter(brass=True), metal=lambda f: P.steel(f, 0.6, tint='#c8a040', spread=0.1)),
        'lighter_fluid': it(S(sh.squeeze, w=2.2, h=5.5, d=1.2), 'FLUID', '#2a5aa8', band='#e0c040', body='#2a5aa8', cap='#c23a22'),
        'flare_gun': it(flare_gun, body='#e07a1a', dark=A.plastic('#1e1f20')),
        'glow_stick': it(glow_stick, lens=A.flat('#8af04a', 0.04), dark=A.plastic('#2a2a2a')),
        'firestarter': it(firestarter, metal=A.steel, body='#e07a1a'),
        'charcoal': it(sack_big, 'CHARCOAL', '#2a2a2a', band='#e07a1a', word_ink='#2a2a2a'),
        'firewood': it(wood_bundle, wood=A.flat('#7a5a3a', 0.12)),
        # ------------------------------------------------------------ camping & farm
        'tent': it(tent_bag, cloth=A.fabric('#3a6a3a'), strap=A.fabric('#1e1f20')),
        'sleeping_bag': it(roll_bag(8.0, 16.0), cloth=A.fabric('#2a5aa8'), strap=A.fabric('#1e1f20')),
        'bedroll': it(roll_bag(6.0, 22.0), cloth=A.fabric('#6a5a3a'), strap=A.fabric('#3a2a1a')),
        'compass': it(compass, body='#c8a040', dial=A.flat('#efe6d0'), red=A.flat('#c23a22'), metal=A.steel),
        'binoculars': it(binoculars, body='#2a2a2a', dark=A.plastic('#1a1a1a'), lens=A.glass('#4a6a8a')),
        'water_filter': it(bottle_filter, 'FILTER', '#2a6ab0', band=CREAM2, body='#c8dce8', cap='#2a6ab0'),
        'water_purification_tabs': it(tablets, 'TABS', '#2a6ab0', band=CREAM2),
        'hand_warmers': it(S(sh.pouch, w=3.0, h=4.0, d=0.4), 'WARM', '#e07a1a', band=CREAM2),
        'emergency_blanket': it(S(sh.pouch, w=3.0, h=4.5, d=0.6), 'BLANKET', '#c8c8c8', band='#c23a22', word_ink='#e8e4da',
                                seal=A.foil('#c8a040')),
        'snare_wire': it(snare, wire=A.steel, core=A.flat('#c8a070')),
        'animal_trap': it(trap, metal=A.steel, wire=A.steel),
        'hunting_knife_sheath': it(sheath, leather=A.flat('#6a4a2a', 0.1)),
        'fishing_lures': it(case_box(6.0, 1.4, 3.5), body='#3a6a3a', mark=A.flat('#e0b020')),
        'bait': it(S(sh.tub, d=3.0, h=2.4), 'BAIT', '#5a3a2a', band='#e0c040', cap='#3a6a3a'),
        'decoy_duck': it(decoy, body='#6a4a2a', head=A.flat('#2f6a3e'), bill=A.flat('#d8a020')),
        'tomato_seeds': it(seed_packet, 'TOMATO', '#efe6d0', 'fruit', '#c8321f'),
        'lettuce_seeds': it(seed_packet, 'LETTUCE', '#efe6d0', 'leaf', '#6aa03a', '#3a7a2a'),
        'corn_seeds': it(seed_packet, 'CORN', '#efe6d0', 'heap', '#e8c23a', '#d9a92a'),
        'fertilizer': it(sack_big, 'FERTILIZER', '#3a7a3a', band='#e8e0c0'),
        'watering_can': it(watering_can, body='#3a7a5a'),
        'feed_sack': it(sack_big, 'FEED', '#d8c8a0', band='#a8281f', word_ink='#e8e0c0'),
        'egg_basket': it(basket, wicker=A.flat('#a8804a', 0.15)),
        'milk_pail': it(pail, metal=A.alu),
        'saddle': it(saddle, leather=A.flat('#6a3a1a', 0.1), strap=A.fabric('#3a2a1a')),
        'horseshoe': it(horseshoe, metal=lambda f: P.steel(f, 0.35, tint='#6a5a4a', spread=0.12)),
        # ------------------------------------------------------------ car
        'spare_tire': it(tyre, rubber=A.plastic('#1e1f20'), metal=A.steel),
        'tire_iron': it(lug_wrench, metal=A.steel),
        'car_jack': it(scissor_jack, body='#a8281f', metal=A.steel),
        'motor_oil': it(quart, 'MOTOR OIL', '#e0b020', band='#1e1f20', word_ink='#e0b020', cap='#1e1f20'),
        'antifreeze': it(S(sh.jug, w=6.0, h=10.0, d=4.0), 'ANTIFREEZE', '#3aa05a', band='#e8e4da', body='#3aa05a',
                         cap='#e0b020'),
        'brake_fluid': it(S(sh.bottle, kind='pet'), 'BRAKE', '#e8e4da', band='#2a5aa8', word_ink='#e8e4da',
                          tint='#d8c890', liquid='#c8a040', cap='#e0b020'),
        'spark_plugs': it(plugs, metal=A.steel, white=A.flat('#efeae0')),
        'fan_belt': it(belt, rubber=A.plastic('#1e1f20'), core=A.flat('#3a3a3a')),
        'headlight_bulb': it(bulb, tint='#f0f0e8', metal=A.alu),
        'wiper_blades': it(blades, rubber=A.plastic('#1e1f20')),
        'radiator_hose': it(hose, rubber=A.plastic('#1e1f20'), metal=A.steel),
        'oil_filter': it(oil_filter, 'FILTER', '#e07a1a', band='#1e1f20', word_ink='#e07a1a', metal=A.alu),
        'car_wax': it(S(sh.tub, d=3.6, h=2.0), 'WAX', '#e0b020', band='#a8281f', word_ink='#e8e4da', cap='#1e1f20'),
        'tow_strap': it(strap_coil, strap=A.fabric('#e0b020'), metal=A.steel),
        'fuel_siphon': it(siphon, tint='#d8e4d8', red=A.plastic('#a8281f')),
        'diesel_can': it(jerrycan, body='#e0b020', cap='#2a2a2a', mark=A.flat('#2a2a2a')),
        'hydraulic_fluid': it(S(sh.can, d=4.0, h=6.0), 'HYDRAULIC', '#3a5a3a', band='#e8e0c0', under=A.tin),
        'tie_down_chain': it(chain_len, metal=A.steel),
        'wheel_chock': it(chock, rubber=A.plastic('#e0b020')),
    })
    return G


CREAM2 = '#efe6d0'
