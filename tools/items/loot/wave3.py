"""Wave 3 item models: books and papers, keys and cards, valuables, toys and hobbies, service gear, junk.
Same conventions as gen_loot.py (inches, standing on y = 0, front toward +z); table(it) returns the entries.
Clothes, wearables and bags are made by the PLAYERS and UI threads, not here.
"""
import numpy as np

import art as A
import paint as P
import shapes as sh


def S(fn, **kw):
    return lambda m: fn(m, **kw)


GOLD = lambda f: P.steel(f, 0.72, tint='#d4a940', spread=0.1)      # noqa: E731
SILVER = lambda f: P.steel(f, 0.8, tint='#c8ccd0', spread=0.08)    # noqa: E731
BRASS = lambda f: P.steel(f, 0.6, tint='#c8a040', spread=0.12)     # noqa: E731
PAPER = A.paper('#efe8d6')
CREAM = '#efe6d0'


# ================================================================== books and papers


def book(w=6.0, l=9.0, t=1.0, spine='side'):
    """A book lying flat: printed front cover on top, page block showing on three edges, the spine at -x."""
    def build(m):
        c = 0.12
        m.box([-w / 2, 0, -l / 2], [w / 2, c, l / 2], 'side')
        m.box([-w / 2 + 0.1, c, -l / 2 + 0.12], [w / 2 - 0.12, t - c, l / 2 - 0.12], 'white')
        m.box([-w / 2, t - c, -l / 2], [w / 2, t, l / 2], 'label')
        m.box([-w / 2 - 0.05, 0, -l / 2], [-w / 2 + 0.25, t, l / 2], spine)
    return build


def magazine(w=8.0, l=10.5, t=0.25):
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, t * 0.6, l / 2], 'white')
        m.box([-w / 2, t * 0.6, -l / 2], [w / 2, t, l / 2], 'label')
    return build


def sleeved(inner):
    """Something kept in a clear plastic sleeve (rare comics, stamps, cards)."""
    def build(m):
        inner(m)
        lo, hi = m.bounds()
        # item models draw solid, so the sleeve shows as its welded clear rim, not a sheet over the print
        y0, y1 = lo[1], hi[1] + 0.04
        e = 0.15
        m.box([lo[0] - e, y0, lo[2] - e], [hi[0] + e, y1, lo[2]], 'glass')
        m.box([lo[0] - e, y0, hi[2]], [hi[0] + e, y1, hi[2] + e], 'glass')
        m.box([lo[0] - e, y0, lo[2]], [lo[0], y1, hi[2]], 'glass')
        m.box([hi[0], y0, lo[2]], [hi[0] + e, y1, hi[2]], 'glass')
    return build


def folded_paper(w=6.0, l=9.0, folds=3, mat='label'):
    def build(m):
        t = 0.06 * folds
        m.box([-w / 2, 0, -l / 2], [w / 2, t, l / 2], 'white')
        m.box([-w / 2, t, -l / 2], [w / 2, t + 0.03, l / 2], mat)
    return build


def newspaper(m):
    m.box([-6.0, 0, -4.0], [6.0, 0.5, 4.0], 'white')
    m.box([-6.0, 0.5, -4.0], [6.0, 0.55, 4.0], 'label')
    m.box([-6.0, 0.55, -4.0], [-5.6, 0.75, 4.0], 'white')


def notebook(m):
    book(5.5, 8.0, 0.5, spine='metal')(m)
    for z in np.arange(-3.6, 3.7, 0.6):
        m.box([-2.95, 0.05, z - 0.1], [-2.55, 0.55, z + 0.1], 'metal')


def pen(length=5.5, r=0.3, mat='body'):
    def build(m):
        m.rod(-length / 2, length / 2 - 0.6, r, mat, axis=0)
        m.rod(length / 2 - 0.6, length / 2, r * 0.55, 'metal', axis=0)
        m.box([-length / 2 + 0.2, r * 0.6, -0.08], [-length / 2 + 1.6, r + 0.15, 0.08], 'cap')
        sh.shift_up(m, r)
    return build


def pencil(m):
    m.box([-3.5, 0, -0.17], [3.0, 0.34, 0.17], 'body')
    m.box([3.0, 0.07, -0.1], [3.6, 0.27, 0.1], 'wood')
    m.box([-3.9, 0.02, -0.16], [-3.5, 0.32, 0.16], 'metal')
    m.box([-4.2, 0.03, -0.15], [-3.9, 0.31, 0.15], 'red')


def envelope(m):
    m.box([-4.5, 0, -2.0], [4.5, 0.08, 2.0], 'label')
    m.box([-4.5, 0.08, 0.6], [4.5, 0.1, 2.0], 'side')


def parcel(m):
    m.box([-5.0, 0, -3.5], [5.0, 4.0, 3.5], 'cardboard')
    m.box([-5.05, 0, -0.15], [5.05, 4.05, 0.15], 'cloth')
    m.box([-0.15, 0, -3.55], [0.15, 4.05, 3.55], 'cloth')
    m.box([1.0, 4.0, 0.6], [4.0, 4.04, 2.6], 'label')


def journal(m):
    book(5.0, 7.5, 1.0, spine='leather')(m)
    m.box([1.6, -0.02, -3.8], [2.0, 1.04, 3.8], 'strap')


def photo(w=4.0, l=6.0, border=0.2):
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, 0.04, l / 2], 'white')
        m.box([-w / 2 + border, 0.04, -l / 2 + border], [w / 2 - border, 0.06, l / 2 - border * (4 if w == l else 1)],
              'label')
    return build


def card(w=3.4, l=2.1, t=0.05, lanyard=False):
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, t, l / 2], 'body')
        m.box([-w / 2 + 0.15, t, -l / 2 + 0.15], [w / 2 - 0.15, t + 0.01, l / 2 - 0.15], 'label')
        if lanyard:
            m.box([-0.3, 0, -l / 2 - 0.5], [0.3, t, -l / 2], 'metal')
            m.box([-0.25, 0, -l / 2 - 6.0], [0.25, t, -l / 2 - 0.5], 'strap')
    return build


def folder(m):
    m.box([-4.6, 0, -6.0], [4.6, 0.4, 6.0], 'white')
    m.box([-4.75, 0.4, -6.1], [4.75, 0.48, 6.1], 'label')
    m.box([-1.5, 0.48, 6.1], [1.5, 0.48 + 0.02, 6.6], 'label')


def evidence_bag(m):
    m.box([-3.5, 0, -5.0], [3.5, 0.4, 5.0], 'glass')
    m.box([-3.5, 0.1, 3.0], [3.5, 0.45, 5.0], 'label')
    m.box([-1.0, 0.1, -2.0], [1.5, 0.35, 1.0], 'dark')


def binder(m):
    m.box([-5.5, 0, -6.0], [5.5, 2.0, 6.0], 'white')
    m.box([-5.6, 0, -6.1], [5.6, 0.12, 6.1], 'side')
    m.box([-5.6, 1.9, -6.1], [5.6, 2.02, 6.1], 'label')
    m.box([-5.8, 0, -6.1], [-5.4, 2.02, 6.1], 'side')


def kneeboard(m):
    m.box([-3.0, 0, -4.5], [3.0, 0.4, 4.5], 'body')
    m.box([-2.7, 0.4, -4.2], [2.7, 0.45, 3.6], 'label')
    m.box([-1.2, 0.4, 3.6], [1.2, 0.7, 4.3], 'metal')
    for z in (-2.5, 2.0):
        m.box([-3.6, 0.1, z - 0.6], [3.6, 0.2, z + 0.6], 'strap')


def card_ring(m):
    for i in range(5):
        m.box([-2.0 + i * 0.08, i * 0.06, -3.0 + i * 0.1], [2.0 + i * 0.08, i * 0.06 + 0.05, 3.0 + i * 0.1], 'label')
    m.rod(0, 0.4, 0.5, 'metal', cx=-1.6, cz=-2.4)


def blueprint_roll(m):
    m.rod(-9.0, 9.0, 0.9, 'label', axis=0)
    m.rod(-9.05, -9.0, 0.5, 'white', axis=0)
    m.box([-3.0, -0.95, -0.95], [-2.6, 0.95, 0.95], 'cloth')
    sh.shift_up(m, 0.95)


def playing_cards(m):
    m.box([-1.25, 0, -1.75], [1.25, 0.75, 1.75], 'label')
    m.box([-1.15, 0.75, 0.6], [1.15, 0.8, 1.75], 'side')


def dice(m):
    for x, y, rot in ((-0.5, 0.0, None), (0.55, 0.0, dict(axis='y', angle=22.5, origin=[0.9, 0, 0.3]))):
        m.box([x - 0.32, 0, -0.32 + (0.3 if rot else 0)], [x + 0.32, 0.64, 0.32 + (0.3 if rot else 0)], 'white', rot=rot)
        m.box([x - 0.08, 0.64, -0.08 + (0.3 if rot else 0)], [x + 0.08, 0.66, 0.08 + (0.3 if rot else 0)], 'dark',
              rot=rot)


def board_game(m):
    sh.flat_box(m, w=15.0, h=2.0, d=10.0)


# ================================================================== keys and cards


def key(length=2.4, bow=0.55, mat='metal', tag=None):
    """A key lying flat: round bow, shank, teeth along one edge; optionally a tag on a ring."""
    def build(m):
        t = 0.06
        m.box([-bow, 0, -bow], [bow, t, bow], mat)
        m.box([-bow * 0.3, 0, -bow * 0.3], [bow * 0.3, t + 0.005, bow * 0.3], 'dark')
        m.box([bow, 0, -0.14], [bow + length, t, 0.14], mat)
        for i in range(4):
            x = bow + length * (0.35 + 0.15 * i)
            m.box([x, 0, 0.14], [x + length * 0.08, t, 0.14 + 0.08 + 0.06 * (i % 2)], mat)
        if tag:
            m.box([-bow - 0.5, 0, -0.08], [-bow, t, 0.08], 'metal')
            m.box([-bow - 0.5 - tag[0], 0, -tag[1] / 2], [-bow - 0.5, 0.05, tag[1] / 2], 'label')
    return build


def key_ring(m):
    m.rod(0, 0.08, 0.7, 'metal')
    m.rod(0, 0.081, 0.55, 'dark')
    for ang, ln in ((0, 2.2), (45, 1.8), (-60, 2.6)):
        a = np.radians(ang)
        k = key(ln, 0.45)
        sub = type(m)('k')
        k(sub)
        for p in sub.parts:
            p['lo'] = p['lo'] + np.array([0.7 + 0.45, 0.02, 0])
            p['hi'] = p['hi'] + np.array([0.7 + 0.45, 0.02, 0])
            if ang:
                p['rot'] = dict(axis='y', angle=float(np.clip(round(ang / 22.5) * 22.5, -45, 45)), origin=[0, 0, 0])
            m.parts.append(p)


def big_key(length=5.0, mat='metal'):
    def build(m):
        m.rod(0, 0.2, 1.1, mat)
        m.rod(0, 0.21, 0.55, 'dark')
        m.box([1.0, 0, -0.25], [1.0 + length, 0.2, 0.25], mat)
        m.box([length - 0.2, 0, 0.25], [length + 1.0, 0.2, 1.0], mat)
        m.box([length - 0.9, 0, 0.25], [length - 0.5, 0.2, 0.7], mat)
    return build


def ticket_punch(m):
    m.seg((-3.0, 0.4), (1.0, 0.4), 0.5, 0.5, 'metal')
    m.seg((-3.0, 1.4), (1.0, 0.9), 0.5, 0.5, 'metal')
    m.box([1.0, 0.2, -0.3], [2.0, 1.1, 0.3], 'metal')
    m.box([-3.2, 0.1, -0.35], [-1.5, 0.7, 0.35], 'grip')
    m.box([-3.2, 1.1, -0.35], [-1.5, 1.7, 0.35], 'grip')


def dog_tags(m):
    for i, (x, z) in enumerate(((0, 0), (0.5, 0.6))):
        m.box([x - 0.55, i * 0.05, z - 1.0], [x + 0.55, i * 0.05 + 0.04, z + 1.0], 'metal')
    m.box([-0.08, 0, -6.0], [0.08, 0.06, -1.0], 'metal')
    m.box([0.42, 0, -6.0], [0.58, 0.06, -0.4], 'metal')


def badge(m):
    m.box([-1.2, 0, -1.4], [1.2, 0.12, 1.4], 'metal')
    m.box([-1.5, 0, -0.5], [1.5, 0.12, 0.5], 'metal')
    m.box([-0.6, 0.12, -0.6], [0.6, 0.16, 0.6], 'label')
    m.box([-1.6, -0.4, -2.2], [1.6, 0, 2.2], 'leather')


# ================================================================== valuables


def bills(w=6.1, l=2.6, t=0.3, band=True):
    def build(m):
        m.box([-w / 2, 0, -l / 2], [w / 2, t, l / 2], 'label')
        if band:
            m.box([-0.7, -0.02, -l / 2 - 0.02], [0.7, t + 0.02, l / 2 + 0.02], 'mark')
    return build


def cash_stack(m):
    for i in range(3):
        bills(6.1, 2.6, 0.5)(m)
        for p in m.parts[-2:]:
            p['lo'] = p['lo'] + np.array([0.2 * i, 0.52 * i, 0.1 * i])
            p['hi'] = p['hi'] + np.array([0.2 * i, 0.52 * i, 0.1 * i])


def coin_roll(m):
    m.rod(-1.6, 1.6, 0.5, 'label', axis=0)
    m.rod(-1.75, -1.6, 0.4, 'metal', axis=0)
    m.rod(1.6, 1.75, 0.4, 'metal', axis=0)
    sh.shift_up(m, 0.5)


def wallet(m):
    m.box([-2.2, 0, -1.7], [2.2, 0.6, 1.7], 'leather')
    m.box([-2.15, 0.6, -1.65], [2.15, 0.62, 1.65], 'leather')
    m.box([-2.25, 0.15, -1.75], [2.25, 0.45, 1.75], 'dark')
    m.box([1.2, 0.5, -1.2], [2.6, 0.55, 0.4], 'cash')


def cig_pack(m):
    m.box([-1.1, 0, -0.45], [1.1, 3.4, 0.45], 'label')
    m.box([-1.1, 2.6, -0.46], [1.1, 3.4, 0.46], 'white')
    m.box([-0.7, 3.4, -0.15], [-0.4, 3.9, 0.15], 'side')
    m.box([-0.3, 3.4, -0.15], [0.0, 3.7, 0.15], 'side')


def humidor(m):
    m.box([-4.5, 0, -3.0], [4.5, 1.8, 3.0], 'wood')
    m.box([-4.6, 1.8, -3.1], [4.6, 2.1, 3.1], 'label')
    m.box([-0.4, 0.9, 3.0], [0.4, 1.5, 3.1], 'gold')


def sack_lying(m):
    m.box([-6.0, 0, -4.0], [6.0, 4.5, 4.0], 'label')
    m.box([-5.6, 4.5, -3.6], [5.6, 5.0, 3.6], 'label')
    m.box([6.0, 0.6, -3.0], [7.2, 3.6, 3.0], 'side')


def watch(face=0.75, strap='strap', case='metal', bracelet=False):
    """A wristwatch lying open: round case and crystal, crown, the strap out both sides."""
    def build(m):
        m.rod(0, 0.35, face, case)
        m.rod(0.35, 0.4, face * 0.82, 'dial')
        m.box([-0.05, 0.4, -0.05], [0.05, 0.42, face * 0.6], 'dark')
        m.box([-0.05, 0.4, -0.05], [face * 0.45, 0.42, 0.05], 'dark')
        m.box([face, 0.1, -0.1], [face + 0.2, 0.28, 0.1], case)
        sm = case if bracelet else strap
        for s in (-1, 1):
            z0, z1 = sorted((s * face * 0.8, s * (face + 3.0)))
            m.box([-face * 0.55, 0.05, z0], [face * 0.55, 0.2, z1], sm)
    return build


def pocket_watch(m):
    m.rod(0, 0.5, 1.0, 'gold')
    m.rod(0.5, 0.55, 0.85, 'dial')
    m.box([-0.05, 0.55, -0.05], [0.05, 0.57, 0.55], 'dark')
    m.rod(-0.15, 0.15, 0.22, 'gold', cx=0, cz=-1.15, axis=1)
    for i in range(6):
        z = -1.5 - i * 0.45
        m.box([-0.12 + (i % 2) * 0.05, 0.1, z - 0.3], [0.12 + (i % 2) * 0.05, 0.22, z], 'gold')
    sh.shift_up(m, 0.15)


def ring(r=0.45, w=0.18, stone=None, mat='gold'):
    def build(m):
        for s in (-1, 1):
            m.box([s * r - w / 2, 0, -r], [s * r + w / 2, 0.15, r], mat)
            m.box([-r, 0, s * r - w / 2], [r, 0.15, s * r + w / 2], mat)
        if stone:
            m.box([-stone, 0.15, r - stone], [stone, 0.15 + stone * 1.4, r + stone], 'gem')
    return build


def necklace(bead=None, pendant=True, mat='gold'):
    def build(m):
        pts = [(np.cos(a) * 2.6, np.sin(a) * 3.2) for a in np.linspace(0.15, np.pi * 2 - 0.15, 18)]
        for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
            if bead:
                m.box([x0 - bead, 0, z0 - bead], [x0 + bead, bead * 2, z0 + bead], mat)
            else:
                lo, hi = np.minimum([x0, z0], [x1, z1]), np.maximum([x0, z0], [x1, z1])
                m.box([lo[0] - 0.05, 0, lo[1] - 0.05], [hi[0] + 0.05, 0.08, hi[1] + 0.05], mat)
        if pendant:
            m.box([2.4, 0, -0.35], [3.2, 0.12, 0.35], mat)
            m.box([2.65, 0.12, -0.15], [2.95, 0.2, 0.15], 'gem')
    return build


def earrings(m):
    for x in (-0.6, 0.6):
        m.box([x - 0.12, 0, -0.5], [x + 0.12, 0.08, 0.2], 'gold')
        m.box([x - 0.2, 0, 0.2], [x + 0.2, 0.25, 0.6], 'gem')
    m.box([-1.2, -0.05, -0.9], [1.2, 0, 0.9], 'white')


def jewel_box(m):
    m.box([-3.5, 0, -2.5], [3.5, 2.6, 2.5], 'wood')
    m.box([-3.6, 2.6, -2.6], [3.6, 3.4, 2.6], 'wood')
    m.box([-2.4, 3.4, -1.5], [2.4, 3.42, 1.5], 'gold')
    m.box([-0.3, 2.3, 2.5], [0.3, 2.8, 2.6], 'gold')


def coin(r=0.55, t=0.1, mat='silver', stack=1):
    def build(m):
        for i in range(stack):
            m.rod(i * t, (i + 1) * t, r, mat, cx=0.05 * (i % 2), cz=0.0)
        m.rod(stack * t, stack * t + 0.01, r * 0.6, 'dark')
    return build


def album(m):
    book(9.0, 11.0, 1.4, spine='leather')(m)
    for x in (-2.5, 0.0, 2.5):
        m.rod(1.4, 1.45, 0.6, 'silver', cx=x, cz=3.2)


def cutlery_case(m):
    m.box([-7.0, 0, -4.5], [7.0, 2.4, 4.5], 'wood')
    m.box([-6.6, 2.4, -4.1], [6.6, 2.42, 4.1], 'felt')
    for i in range(6):
        x = -5.0 + i * 2.0
        m.box([x - 0.3, 2.42, -3.5], [x + 0.3, 2.6, 3.5], 'silver')
    m.box([-7.0, 2.4, -4.6], [7.0, 4.8, -4.4], 'wood')


def gold_bar(m):
    m.box([-3.5, 0, -1.6], [3.5, 1.4, 1.6], 'gold')
    m.box([-3.0, 1.4, -1.2], [3.0, 1.7, 1.2], 'gold')
    m.box([-1.2, 1.7, -0.5], [1.2, 1.72, 0.5], 'dark')


def egg(m):
    m.box([-1.4, 0, -1.4], [1.4, 0.4, 1.4], 'gold')
    sh.ball(m, 1.5, 'gem', 1.3, cy=2.3)
    for y in (1.6, 2.3, 3.0):
        m.rod(y - 0.08, y + 0.08, 1.52, 'gold')
    m.rod(4.2, 4.5, 0.3, 'gold')


def diamond_pouch(m):
    m.box([-2.0, 0, -1.5], [2.0, 1.0, 1.5], 'felt')
    m.box([-1.0, 1.0, -0.8], [1.0, 1.4, 0.8], 'felt')
    m.box([-0.9, 1.4, -0.15], [0.9, 1.5, 0.15], 'cloth')
    for x, z in ((2.4, 0.4), (2.9, -0.6), (3.3, 0.8)):
        m.box([x - 0.2, 0, z - 0.2], [x + 0.2, 0.35, z + 0.2], 'gem')


def watch_box(m):
    m.box([-2.5, 0, -2.5], [2.5, 2.0, 2.5], 'wood')
    m.box([-2.6, 2.0, -2.6], [2.6, 2.3, -0.8], 'wood', rot=dict(axis='x', angle=-45, origin=[0, 2.0, -2.6]))
    m.box([-2.2, 2.0, -2.2], [2.2, 2.05, 2.2], 'felt')
    watch(0.8, case='gold', bracelet=True)(m)
    for p in m.parts[-9:]:
        p['lo'] = p['lo'] + np.array([0, 2.05, 0])
        p['hi'] = p['hi'] + np.array([0, 2.05, 0])
        p['lo'][2], p['hi'][2] = max(p['lo'][2], -2.1), min(p['hi'][2], 2.1)


def card_box(m):
    m.box([-2.0, 0, -3.5], [2.0, 3.0, 3.5], 'label')
    for i in range(4):
        m.box([-1.8, 3.0, -3.0 + i * 1.6], [1.8, 3.4 + (i % 2) * 0.2, -2.9 + i * 1.6], 'white')


def rookie_card(m):
    m.box([-1.6, 0, -2.2], [1.6, 0.3, 2.2], 'glass')
    m.box([-1.3, 0.3, -1.9], [1.3, 0.32, 1.9], 'label')
    m.box([-1.6, 0.3, 1.8], [1.6, 0.36, 2.2], 'red')


def baseball(m, mat='white'):
    sh.ball(m, 1.45, mat)
    m.box([-1.0, 2.86, -0.05], [1.0, 2.94, 0.05], 'red')      # stitching over the top
    m.box([-0.05, 2.86, -0.8], [0.05, 2.94, 0.8], 'red')


def signed_ball(m):
    m.box([-1.8, 0, -1.8], [1.8, 0.4, 1.8], 'wood')
    baseball(m)
    for p in m.parts[1:]:
        p['lo'] = p['lo'] + np.array([0, 0.4, 0])
        p['hi'] = p['hi'] + np.array([0, 0.4, 0])


def trophy(m):
    m.box([-1.6, 0, -1.6], [1.6, 1.2, 1.6], 'wood')
    m.rod(1.2, 1.5, 0.9, 'gold')
    m.rod(1.5, 3.2, 0.3, 'gold')
    m.rod(3.2, 5.2, 1.2, 'gold')
    m.rod(5.2, 5.5, 1.35, 'gold')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.2, s * 1.9))
        m.box([x0, 3.6, -0.12], [x1, 4.8, 0.12], 'gold')
    m.box([-1.0, 0.3, 1.6], [1.0, 0.9, 1.65], 'label')


def medal(m):
    m.box([-0.7, 0, -3.0], [0.7, 0.05, -0.8], 'cloth')
    m.rod(0, 0.15, 0.85, 'gold', cz=0.2)


def medal_case(m):
    m.box([-5.0, 0, -4.0], [5.0, 1.2, 4.0], 'wood')
    m.box([-4.6, 1.2, -3.6], [4.6, 1.22, 3.6], 'felt')
    for i, x in enumerate((-2.8, 0.0, 2.8)):
        m.box([x - 0.6, 1.22, -2.6], [x + 0.6, 1.26, -0.6], 'cloth')
        m.rod(1.22, 1.32, 0.75, 'gold' if i != 1 else 'silver', cx=x, cz=0.2)


def mantel_clock(m):
    m.box([-4.5, 0, -2.0], [4.5, 0.6, 2.0], 'wood')
    m.box([-3.8, 0.6, -1.7], [3.8, 5.5, 1.7], 'wood')
    m.rod(5.5, 6.8, 2.2, 'wood', axis=1)
    m.rod(1.7, 1.75, 1.8, 'gold', cx=0, cz=3.6, axis=2)
    m.rod(1.75, 1.8, 1.55, 'dial', cx=0, cz=3.6, axis=2)


def frame(w=12.0, h=16.0, border=1.0, mat='wood'):
    def build(m):
        d = 0.8
        m.box([-w / 2, 0, -d / 2], [w / 2, border, d / 2], mat)
        m.box([-w / 2, h - border, -d / 2], [w / 2, h, d / 2], mat)
        m.box([-w / 2, border, -d / 2], [-w / 2 + border, h - border, d / 2], mat)
        m.box([w / 2 - border, border, -d / 2], [w / 2, h - border, d / 2], mat)
        m.box([-w / 2 + border, border, -0.1], [w / 2 - border, h - border, 0.1], 'label')
    return build


def chalice(m):
    m.rod(0, 0.3, 1.6, 'gold')
    m.rod(0.3, 0.8, 1.0, 'gold')
    m.rod(0.8, 3.8, 0.35, 'gold')
    m.rod(2.0, 2.6, 0.6, 'gold')
    m.rod(3.8, 4.4, 1.0, 'gold')
    m.rod(4.4, 6.5, 1.6, 'gold')
    m.rod(6.45, 6.5, 1.4, 'dark')


# ================================================================== toys and hobbies


def teddy(m):
    m.box([-1.6, 0, -1.2], [1.6, 3.4, 1.2], 'fur')
    m.box([-1.3, 3.4, -1.1], [1.3, 5.6, 1.1], 'fur')
    m.box([-0.6, 3.8, 1.1], [0.6, 4.6, 1.7], 'felt')
    m.box([-0.2, 4.4, 1.7], [0.2, 4.6, 1.75], 'dark')
    for s in (-1, 1):
        m.box([s * 0.55 - 0.15, 4.9, 1.1], [s * 0.55 + 0.15, 5.15, 1.15], 'dark')
        x0, x1 = sorted((s * 0.6, s * 1.4))
        m.box([x0, 5.4, -0.4], [x1, 6.2, 0.4], 'fur')
        x0, x1 = sorted((s * 1.6, s * 2.4))
        m.box([x0, 1.8, -0.6], [x1, 3.2, 0.6], 'fur')
        x0, x1 = sorted((s * 0.3, s * 1.5))
        m.box([x0, 0, 1.2], [x1, 1.1, 3.0], 'fur')
    m.box([-1.65, 3.2, -1.25], [1.65, 3.5, 1.25], 'red')


def figure(h=4.5, mat='body', skin='skin'):
    def build(m):
        u = h / 9
        for s in (-1, 1):
            m.box([s * u * 0.9 - u * 0.6, 0, -u * 0.6], [s * u * 0.9 + u * 0.6, u * 4, u * 0.6], 'dark')
            m.box([s * u * 2.2 - u * 0.5, u * 4.2, -u * 0.5], [s * u * 2.2 + u * 0.5, u * 7.2, u * 0.5], mat)
        m.box([-u * 1.7, u * 4, -u * 0.8], [u * 1.7, u * 7.4, u * 0.8], mat)
        m.box([-u * 0.9, u * 7.4, -u * 0.9], [u * 0.9, u * 9, u * 0.9], skin)
    return build


def blister(inner_h=4.5):
    def build(m):
        m.box([-2.5, 0, -0.15], [2.5, 7.5, 0.15], 'label')
        figure(inner_h)(m)
        for p in m.parts[-6:]:
            p['lo'] = p['lo'] + np.array([0, 1.2, 0.8])
            p['hi'] = p['hi'] + np.array([0, 1.2, 0.8])
        for x0, x1 in ((-1.9, -1.7), (1.7, 1.9)):
            m.box([x0, 1.0, 0.15], [x1, 6.0, 1.7], 'glass')
        m.box([-1.9, 6.0, 0.15], [1.9, 6.2, 1.7], 'glass')
    return build


def doll(m):
    m.box([-0.9, 0, -0.6], [0.9, 2.8, 0.6], 'cloth')
    m.box([-1.2, 2.8, -0.7], [1.2, 5.5, 0.7], 'cloth')
    m.box([-0.8, 5.5, -0.75], [0.8, 7.2, 0.75], 'skin')
    m.box([-0.95, 6.5, -0.85], [0.95, 7.5, 0.85], 'hair')
    m.box([-0.95, 5.2, -0.85], [-0.55, 6.6, 0.0], 'hair')
    m.box([0.55, 5.2, -0.85], [0.95, 6.6, 0.0], 'hair')
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.2, s * 1.7))
        m.box([x0, 3.2, -0.3], [x1, 5.3, 0.3], 'skin')


def wheels(m, xs, z_half, r, mat='rubber'):
    for x in xs:
        for s in (-1, 1):
            z0, z1 = sorted((s * z_half, s * (z_half + 0.2)))
            m.box([x - r, 0, z0], [x + r, r * 2, z1], mat)


def toy_car_b(l=3.0, w=1.3, h=0.8, truck=False):
    def build(m):
        r = h * 0.3
        m.box([-l / 2, r * 0.8, -w / 2], [l / 2, r * 0.8 + h * 0.5, w / 2], 'body')
        top = r * 0.8 + h * 0.5
        if truck:
            m.box([l * 0.15, top, -w / 2], [l / 2, top + h * 0.6, w / 2], 'body')
            m.box([l / 2, top + h * 0.15, -w * 0.38], [l / 2 + 0.02, top + h * 0.5, w * 0.38], 'glass')
            for z in (-w / 2, w / 2 - 0.12):
                m.box([-l / 2, top, z], [l * 0.1, top + h * 0.25, z + 0.12], 'body')
        else:
            m.box([-l * 0.25, top, -w * 0.42], [l * 0.22, top + h * 0.42, w * 0.42], 'glass')
        wheels(m, (-l * 0.32, l * 0.32), w / 2, r)
    return build


def cube(m):
    m.box([-1.15, 0, -1.15], [1.15, 2.3, 1.15], 'dark')
    cols = ['red', 'white', 'body']
    for i in range(3):
        for j in range(3):
            u, v = -0.75 + i * 0.75, 0.4 + j * 0.75
            m.box([u - 0.33, v - 0.33, 1.15], [u + 0.33, v + 0.33, 1.2], cols[(i + j) % 3])
            m.box([u - 0.33, 2.3, v - 1.5 - 0.33], [u + 0.33, 2.35, v - 1.5 + 0.33], cols[(i + 2 * j + 1) % 3])
            m.box([1.15, v - 0.33, u - 0.33], [1.2, v + 0.33, u + 0.33], cols[(2 * i + j + 2) % 3])


def yo_yo(m):
    m.rod(0, 0.4, 1.1, 'body', axis=2, cx=0, cz=1.1)
    m.rod(0.55, 0.95, 1.1, 'body', axis=2, cx=0, cz=1.1)
    m.rod(0.4, 0.55, 0.3, 'white', axis=2, cx=0, cz=1.1)
    m.box([-0.04, 1.1, 0.44], [0.04, 4.0, 0.51], 'white')


def rc_car(m):
    toy_car_b(9.0, 6.0, 2.6)(m)
    m.box([-4.0, 2.0, -2.6], [-3.6, 4.5, -2.4], 'dark')
    m.box([3.4, 2.0, -2.8], [4.8, 2.4, 2.8], 'body')
    m.box([-12.0, 0, 4.0], [-8.0, 1.5, 7.0], 'dark')
    m.box([-9.0, 1.5, 4.4], [-8.8, 4.5, 4.6], 'metal')


def glove(m):
    m.box([-2.8, 0, -2.6], [2.8, 1.6, 2.4], 'leather')
    for i in range(4):
        x = -2.2 + i * 1.3
        m.box([x - 0.55, 0.2, 2.4], [x + 0.55, 1.4, 5.4 + (0.4 if i in (1, 2) else 0)], 'leather')
    m.box([2.8, 0.2, -1.0], [4.4, 1.4, 1.6], 'leather')
    m.box([1.8, 0.4, 2.4], [3.6, 1.2, 4.2], 'cloth')
    m.box([-2.8, 1.55, -2.6], [2.8, 1.65, -1.2], 'red')


def football(m):
    m.rod(-2.4, 2.4, 1.2, 'leather', axis=0)
    m.rod(-3.4, 3.4, 0.7, 'leather', axis=0)
    m.box([-1.2, 1.18, -0.08], [1.2, 1.24, 0.08], 'white')
    sh.shift_up(m, 1.2)


def racket(m):
    m.box([-0.4, 0, -0.4], [0.4, 0.8, 7.0], 'grip')
    m.box([-0.25, 0.2, 7.0], [0.25, 0.6, 9.5], 'metal')
    for s in (-1, 1):
        x0, x1 = sorted((s * 3.6, s * 4.0))
        m.box([x0, 0.1, 9.5], [x1, 0.7, 19.0], 'metal')
    m.box([-4.0, 0.1, 19.0], [4.0, 0.7, 19.4], 'metal')
    m.box([-4.0, 0.1, 9.1], [4.0, 0.7, 9.5], 'metal')
    m.box([-3.6, 0.35, 9.5], [3.6, 0.45, 19.0], 'white')


def skateboard(m):
    m.box([-14.0, 2.0, -3.8], [14.0, 2.5, 3.8], 'wood')
    m.box([-16.0, 2.4, -3.6], [-14.0, 2.9, 3.6], 'wood')
    m.box([14.0, 2.4, -3.6], [16.0, 2.9, 3.6], 'wood')
    m.box([-14.0, 2.5, -3.8], [14.0, 2.55, 3.8], 'dark')
    for x in (-9.0, 9.0):
        m.box([x - 0.6, 1.4, -3.2], [x + 0.6, 2.0, 3.2], 'metal')
        for z in (-3.0, 3.0):
            m.rod(z - 0.6, z + 0.6, 0.7, 'white', cx=x, cz=0.7, axis=2)


def roller_skate(m):
    m.box([-2.0, 1.4, -1.6], [5.0, 2.6, 1.6], 'white')
    m.box([-2.0, 2.6, -1.5], [1.6, 7.0, 1.5], 'white')
    m.box([1.6, 2.6, -1.4], [3.2, 4.4, 1.4], 'white')
    m.box([-2.2, 1.2, -1.6], [5.2, 1.4, 1.6], 'metal')
    m.box([4.9, 0.4, -0.5], [5.7, 1.4, 0.5], 'red')
    wheels(m, (-0.8, 3.6), 1.1, 0.6, mat='red')


def frisbee(m):
    m.rod(0, 0.8, 4.4, 'body')
    m.rod(0.8, 0.9, 3.8, 'body')
    m.rod(0.9, 0.92, 2.0, 'label')


def fish_plaque(m):
    m.box([-8.0, 0, -0.5], [8.0, 7.0, 0.0], 'wood')
    m.box([-6.0, 2.2, 0.0], [5.0, 4.8, 1.2], 'skin')
    m.box([5.0, 2.8, 0.2], [6.8, 4.2, 1.0], 'skin')
    m.box([-6.8, 1.6, 0.3], [-6.0, 5.4, 0.9], 'skin')
    m.box([-2.0, 4.8, 0.4], [1.0, 5.6, 0.8], 'skin')
    m.box([-1.5, 0.4, 0.0], [1.5, 1.4, 0.1], 'gold')


def harmonica(m):
    m.box([-2.0, 0, -0.5], [2.0, 0.8, 0.5], 'metal')
    m.box([-1.9, 0.15, 0.5], [1.9, 0.65, 0.55], 'wood')
    for i in range(10):
        x = -1.75 + i * 0.39
        m.box([x - 0.1, 0.3, 0.55], [x + 0.1, 0.5, 0.56], 'dark')


def guitar(m):
    # built standing on its back in x-y, then laid down so the top faces up
    m.rod(0.0, 3.5, 6.5, 'wood', axis=2, cx=-6.0, cz=0)
    m.rod(0.0, 3.5, 5.2, 'wood', axis=2, cx=3.0, cz=0)
    m.rod(3.5, 3.55, 1.6, 'dark', axis=2, cx=-0.8, cz=0)
    m.box([8.0, -0.9, 2.4], [24.0, 0.9, 3.4], 'dark')
    m.box([24.0, -1.2, 2.2], [28.0, 1.2, 3.2], 'wood')
    m.box([-8.0, -1.8, 3.5], [-7.0, 1.8, 3.8], 'dark')
    for p in m.parts:
        lo, hi = p['lo'].copy(), p['hi'].copy()
        p['lo'] = np.array([lo[0], lo[2], lo[1]])
        p['hi'] = np.array([hi[0], hi[2], hi[1]])
        if 'axis' in p and p['axis'] == 2:
            p['axis'] = 1


def trumpet(m):
    m.rod(-7.0, 5.0, 0.35, 'gold', axis=0, cz=0)
    m.rod(5.0, 7.5, 1.0, 'gold', axis=0, cz=0)
    m.rod(7.5, 8.0, 2.3, 'gold', axis=0, cz=0)
    m.rod(-7.6, -7.0, 0.5, 'silver', axis=0, cz=0)
    m.rod(-3.0, 3.0, 0.3, 'gold', axis=0, cz=-1.8)
    for x in (-1.2, 0.0, 1.2):
        m.rod(-2.0, 1.4, 0.28, 'silver', cx=x, cz=-0.9, axis=2)
    sh.shift_up(m, 2.3)


def whistle(m):
    m.rod(0, 0.9, 0.6, 'metal', axis=0, cz=0)
    m.box([0.9, -0.15, -0.6], [2.2, 0.5, 0.6], 'metal')
    m.box([-0.1, 0.4, -0.1], [0.3, 0.7, 0.1], 'dark')
    m.box([-0.3, -0.6, -0.08], [0.0, -0.3, 0.08], 'metal')
    sh.shift_up(m, 0.6)


def stopwatch(m):
    m.rod(0, 0.6, 1.2, 'metal', axis=2, cx=0, cz=1.3)
    m.box([-0.15, 2.5, -0.15], [0.15, 3.0, 0.15], 'metal')
    m.rod(0.6, 0.62, 0.95, 'dial', axis=2, cx=0, cz=1.3)
    m.box([-0.04, 1.3, 0.62], [0.04, 2.0, 0.64], 'red')


def mirror(m):
    m.rod(0, 0.3, 1.4, 'gold')
    m.rod(0.3, 0.32, 1.2, 'lens')


def umbrella(m):
    m.rod(-12.0, 12.0, 1.0, 'cloth', axis=0)
    m.rod(-14.0, -12.0, 0.25, 'metal', axis=0)
    m.box([12.0, -0.4, -0.4], [15.5, 0.4, 0.4], 'handle')
    m.box([15.0, -0.4, -0.4], [15.8, 0.4, 2.2], 'handle')
    m.box([-2.0, 0.95, -1.05], [-1.4, 1.05, 1.05], 'strap')
    sh.shift_up(m, 1.0)


def rosary(m):
    necklace(bead=0.14, pendant=False, mat='wood')(m)
    m.box([2.5, 0, -0.08], [4.6, 0.1, 0.08], 'silver')
    m.box([3.6, 0, -0.55], [3.75, 0.1, 0.55], 'silver')


def snow_globe(m):
    m.box([-1.8, 0, -1.8], [1.8, 1.0, 1.8], 'wood')
    sh.ball(m, 1.9, 'glass', 1.0, cy=2.8)
    m.box([-0.6, 1.0, -0.6], [0.6, 2.4, 0.6], 'white')
    m.box([-0.8, 2.4, -0.8], [0.8, 2.9, 0.8], 'red')


def rabbit_foot(m):
    m.box([-0.5, 0, -0.5], [0.5, 1.0, 2.5], 'fur')
    m.box([-0.35, 0.1, 2.5], [0.35, 0.8, 3.2], 'gold')
    m.rod(0.3, 0.35, 0.5, 'gold', cz=3.6)


# ================================================================== service gear


def handset(m):
    m.box([-1.0, 0, -1.0], [1.0, 6.5, 1.0], 'body')
    m.rod(5.5, 7.5, 1.4, 'body')
    m.rod(-0.6, 0.0, 1.3, 'body')
    m.box([-0.3, 3.0, 1.0], [0.3, 4.2, 1.2], 'dark')
    m.box([-0.15, -0.6, -1.4], [0.15, 0.0, 1.4], 'cloth')
    sh.shift_up(m, 0.6)


def patch(m):
    m.rod(0, 0.1, 1.8, 'cloth')
    m.rod(0.1, 0.12, 1.5, 'label')


def signal_mirror(m):
    m.box([-1.5, 0, -2.0], [1.5, 0.2, 2.0], 'metal')
    m.box([-1.3, 0.2, -1.8], [1.3, 0.22, 1.8], 'lens')
    m.rod(0.2, 0.24, 0.25, 'dark')


def grenade_can(m):
    m.rod(0, 4.8, 1.2, 'label')
    m.rod(4.8, 5.4, 0.6, 'metal')
    m.box([0.5, 3.5, -0.2], [0.8, 5.6, 0.2], 'metal')
    m.rod(5.4, 5.8, 0.3, 'metal', cx=-0.6)


def e_tool(m):
    m.box([-0.4, 0, -0.4], [0.4, 0.8, 10.0], 'wood')
    m.box([-1.4, 0.1, -1.2], [1.4, 0.6, -0.4], 'dark')
    m.box([-3.0, 0, 10.0], [3.0, 0.5, 17.0], 'metal')
    m.box([-0.6, 0, 9.6], [0.6, 1.0, 10.6], 'metal')


def canteen_cup(m):
    m.rod(0, 3.2, 1.9, 'metal')
    m.rod(3.15, 3.2, 1.75, 'dark')
    m.box([1.9, 2.4, -0.5], [5.0, 2.7, 0.5], 'metal')


def rations_case(m):
    m.box([-8.0, 0, -5.5], [8.0, 10.0, 5.5], 'cardboard')
    m.box([-8.05, 3.0, 5.5], [8.05, 7.0, 5.55], 'label')
    m.box([-8.05, 0, -0.2], [8.05, 10.05, 0.2], 'strap')


def handcuffs(m):
    for x in (-1.6, 1.6):
        m.rod(0, 0.3, 1.3, 'metal', cx=x)
        m.rod(0, 0.31, 0.95, 'dark', cx=x)
    m.box([-0.4, 0.05, -0.15], [0.4, 0.25, 0.15], 'metal')


def belt_radio(m):
    m.box([-1.4, 0, -0.9], [1.4, 6.0, 0.9], 'body')
    m.box([-1.0, 3.6, 0.9], [1.0, 5.4, 0.95], 'grille')
    m.rod(6.0, 9.5, 0.25, 'dark', cx=0.8)
    m.rod(6.0, 6.6, 0.35, 'keys', cx=-0.6)
    m.box([-1.0, 1.0, -1.3], [1.0, 3.8, -0.9], 'leather')


def extinguisher(m):
    m.rod(0, 12.0, 2.3, 'label')
    m.rod(12.0, 13.2, 1.4, 'red')
    m.box([-0.5, 13.2, -0.4], [0.5, 14.2, 0.4], 'metal')
    m.box([-0.4, 14.2, -0.5], [3.0, 14.6, 0.5], 'metal')
    m.box([-0.4, 13.4, -0.5], [2.6, 13.7, 0.5], 'metal')
    m.seg((-0.8, 13.8), (-2.8, 10.0), 0.6, 0.6, 'rubber')
    m.box([-3.2, 6.0, -0.35], [-2.5, 10.0, 0.35], 'rubber')


def nozzle(m):
    m.rod(-5.0, 1.0, 1.0, 'metal', axis=0)
    m.rod(1.0, 4.0, 1.4, 'red', axis=0)
    m.rod(4.0, 5.0, 1.8, 'metal', axis=0)
    m.box([-1.0, 0.8, -0.3], [1.0, 2.8, 0.3], 'metal')
    sh.shift_up(m, 1.8)


def halligan(m):
    m.box([-14.0, 0, -0.5], [14.0, 1.0, 0.5], 'metal')
    m.box([-16.0, 0, -0.3], [-14.0, 0.6, 1.4], 'metal')
    m.box([14.0, 0, -2.5], [14.8, 1.0, 2.5], 'metal')
    m.box([14.8, 0.1, -0.5], [17.0, 0.8, 0.5], 'metal')
    m.box([-4.0, 0, -0.55], [4.0, 1.05, 0.55], 'grip')


def scba(m):
    m.rod(-9.0, 9.0, 3.4, 'label', axis=0)
    m.rod(9.0, 10.5, 1.4, 'metal', axis=0)
    m.box([-6.0, -3.6, -3.6], [6.0, -3.2, 3.6], 'dark')
    for x in (-4.0, 4.0):
        m.box([x - 0.6, -3.5, -3.5], [x + 0.6, 3.5, -3.3], 'strap')
    m.rod(10.5, 11.5, 0.6, 'dial', axis=0)
    sh.shift_up(m, 3.6)


def cone(m):
    m.box([-6.0, 0, -6.0], [6.0, 1.0, 6.0], 'dark')
    for i, (r, y0, y1) in enumerate(((3.6, 1.0, 5.0), (2.8, 5.0, 10.0), (2.0, 10.0, 15.0), (1.2, 15.0, 18.0))):
        m.rod(y0, y1, r, 'white' if i == 2 else 'body')


def road_sign(m):
    m.box([-12.0, 0, -12.0], [12.0, 0.3, 12.0], 'metal')
    m.box([-11.0, 0.3, -11.0], [11.0, 0.32, 11.0], 'label')
    for z in (-6.0, 6.0):
        m.box([-0.6, 0.3, z - 0.3], [0.6, 0.6, z + 0.3], 'metal')
    m.box([-1.0, 0, 12.0], [1.0, 1.0, 24.0], 'metal')


# ================================================================== junk


def shards(m):
    for x, z, w, l, a in ((-1.0, 0.0, 1.6, 2.4, 0), (1.2, 0.6, 1.2, 1.8, 22.5), (0.2, -1.6, 1.0, 1.4, -22.5),
                          (-2.2, -1.2, 0.8, 1.0, 45)):
        m.box([x - w / 2, 0, z - l / 2], [x + w / 2, 0.12, z + l / 2], 'glass',
              rot=dict(axis='y', angle=a, origin=[x, 0, z]) if a else None)


def paper_bundle(m):
    m.box([-6.0, 0, -4.0], [6.0, 6.0, 4.0], 'white')
    for i in range(6):
        m.box([-6.05, i + 0.05, -4.05], [6.05, i + 0.12, 4.05], 'label')
    for x in (-3.0, 3.0):
        m.box([x - 0.12, -0.02, -4.1], [x + 0.12, 6.05, 4.1], 'cloth')
    m.box([-6.1, 2.5, -0.12], [6.1, 6.05, 0.12], 'cloth')


def flat_cardboard(m):
    m.box([-10.0, 0, -7.0], [10.0, 0.25, 7.0], 'cardboard')
    m.box([-10.0, 0.25, -7.0], [10.0, 0.5, -2.0], 'cardboard')
    m.box([-4.0, 0.5, -6.0], [4.0, 0.52, -3.0], 'label')


def spoiled(m):
    m.box([-2.5, 0, -2.0], [2.5, 1.6, 2.0], 'label')
    m.box([-2.0, 1.6, -1.6], [1.6, 2.2, 1.4], 'food')
    m.box([-1.4, 2.2, -1.0], [0.6, 2.6, 0.6], 'mould')


def bones(m):
    for (x0, z0), (x1, z1) in (((-3.0, -1.0), (3.0, 0.6)), ((-2.0, 1.6), (1.6, -1.8))):
        m.seg((x0, 0.25), (x1, 0.25), 0.35, 0.45, 'white', z=(z0 + z1) / 2)
    for x, z in ((-3.0, -1.0), (3.0, 0.6)):
        m.box([x - 0.45, 0, z - 0.45], [x + 0.45, 0.65, z + 0.45], 'white')


def butts(m):
    for i, (x, z) in enumerate(((0, 0), (0.8, 0.4), (-0.6, 0.9), (0.3, -0.8), (-0.9, -0.4))):
        m.box([x - 0.6, 0, z - 0.14], [x + 0.2, 0.28, z + 0.14], 'white')
        m.box([x + 0.2, 0, z - 0.14], [x + 0.6, 0.28, z + 0.14], 'cap')
        m.box([x - 0.65, 0.02, z - 0.12], [x - 0.6, 0.26, z + 0.12], 'dark')


def wrapper(m):
    m.box([-3.0, 0, -2.5], [3.0, 0.15, 2.5], 'label')
    m.box([-2.0, 0.15, -1.6], [2.4, 0.45, 1.4], 'label')
    m.box([1.0, 0.45, -0.8], [2.0, 0.6, 0.2], 'label')


def broken_radio(m):
    m.box([-3.0, 0, -0.9], [3.0, 4.0, 0.9], 'body')
    m.box([-2.6, 0.5, 0.9], [0.4, 3.5, 0.95], 'grille')
    m.box([1.0, 1.0, 0.9], [2.6, 3.0, 0.95], 'glass')
    m.box([-0.6, 3.6, -0.9], [3.0, 4.0, 0.9], 'dark')
    m.box([2.6, 3.2, -0.6], [4.4, 3.6, 0.4], 'wire')
    m.box([-3.0, 0, -0.9], [-1.4, 1.6, 0.0], 'dark')


def broken_toy(m):
    toy_car_b(3.0, 1.3, 0.8)(m)
    del m.parts[-1]
    m.parts[0]['rot'] = dict(axis='z', angle=22.5, origin=[1.5, 0, 0])


def shoe(m):
    m.box([-2.0, 0, -1.6], [7.0, 0.6, 1.6], 'rubber')
    m.box([-1.8, 0.6, -1.5], [5.0, 2.6, 1.5], 'leather')
    m.box([5.0, 0.6, -1.4], [7.0, 1.6, 1.4], 'leather')
    m.box([-1.8, 2.6, -1.5], [1.8, 4.6, 1.5], 'leather')
    m.box([1.8, 2.4, -1.2], [5.0, 2.8, 1.2], 'cloth')


def band_ball(m):
    sh.ball(m, 1.3, 'red')
    m.rod(0.0, 2.7, 1.32, 'body')
    for p in m.parts[-3:]:
        p['lo'][2], p['hi'][2] = max(p['lo'][2], -0.3), min(p['hi'][2], 0.3)


def clips(m):
    m.box([-1.5, 0, -1.0], [1.5, 0.8, 1.0], 'label')
    for x, z in ((2.2, 0.2), (2.6, -0.6)):
        m.box([x - 0.2, 0, z - 0.6], [x + 0.2, 0.05, z + 0.6], 'metal')
        m.box([x - 0.08, 0.05, z - 0.45], [x + 0.08, 0.06, z + 0.45], 'dark')


def stapler(m):
    m.box([-3.0, 0, -0.7], [3.0, 0.4, 0.7], 'dark')
    m.box([-3.0, 0.4, -0.6], [-2.0, 1.4, 0.6], 'dark')
    m.box([-2.8, 1.0, -0.65], [3.2, 1.8, 0.65], 'body')
    m.box([2.6, 0.5, -0.4], [3.2, 1.0, 0.4], 'metal')


def hanger(m):
    m.seg((-8.0, 0.0), (0.0, 4.5), 0.25, 0.25, 'metal')
    m.seg((8.0, 0.0), (0.0, 4.5), 0.25, 0.25, 'metal')
    m.box([-8.2, -0.12, -0.12], [8.2, 0.12, 0.12], 'metal')
    m.box([-0.12, 4.5, -0.12], [0.12, 6.0, 0.12], 'metal')
    m.box([-1.2, 5.9, -0.12], [0.12, 6.2, 0.12], 'metal')
    for p in m.parts:
        p['lo'] = np.array([p['lo'][0], p['lo'][2] + 0.15, p['lo'][1]])
        p['hi'] = np.array([p['hi'][0], p['hi'][2] + 0.15, p['hi'][1]])
        if p['rot']:
            o = p['rot']['origin']
            p['rot'] = dict(axis='y', angle=-p['rot']['angle'], origin=[o[0], o[2] + 0.15, o[1]])


def light_tube(m):
    m.rod(-22.0, 22.0, 0.6, 'white', axis=0)
    for x in (-22.5, 22.0):
        m.rod(x, x + 0.5, 0.62, 'metal', axis=0)
    sh.shift_up(m, 0.6)


def offcut(m):
    m.rod(-5.0, 5.0, 0.8, 'metal', axis=0)
    m.rod(-5.0, 5.0, 0.55, 'dark', axis=0)
    sh.shift_up(m, 0.8)


def scraps(m):
    for i, (x, z, l, a) in enumerate(((0, 0, 9.0, 0), (1.0, 2.6, 7.0, 22.5), (-1.0, -2.4, 6.0, -22.5))):
        m.box([x - l / 2, i * 0.75, z - 1.2], [x + l / 2, i * 0.75 + 0.75, z + 1.2], 'wood',
              rot=dict(axis='y', angle=a, origin=[x, 0, z]) if a else None)


def tin_sheet(m):
    m.box([-10.0, 0, -7.0], [10.0, 0.08, 7.0], 'metal')
    for z in np.arange(-6.0, 6.5, 2.0):
        m.box([-10.0, 0.08, z - 0.4], [10.0, 0.4, z + 0.4], 'metal')


def lost_box(m):
    m.box([-7.0, 0, -5.0], [7.0, 6.0, 5.0], 'cardboard')
    m.box([-3.0, 6.0, -2.0], [3.0, 6.5, 2.0], 'cloth')
    m.box([-6.0, 4.5, 5.0], [0.0, 5.6, 5.05], 'label')
    m.box([2.0, 5.5, -4.0], [6.0, 7.5, -1.0], 'leather')


# ================================================================== the table


def table(it):
    G = {}
    BOOK = dict(band=CREAM, style='band')
    sb = lambda word, base, art='#e8c040': it(book(6.0, 9.0, 1.0), word, base, 'star', art, band=CREAM,  # noqa: E731
                                                   white=A.flat('#efe8d6'))
    G.update({
        # ------------------------------------------------------------ books and papers
        'skillbook_carpentry': sb('CARPENTRY', '#8a5a2a'), 'skillbook_first_aid': it(book(6.0, 9.0, 1.0), 'FIRST AID',
                                                                                     '#e8e4da', 'cross', '#c23a22'),
        'skillbook_blunt': sb('DEFENCE', '#2a3a5a'), 'skillbook_blade': sb('KNIFE CRAFT', '#3a5a3a'),
        'skillbook_sprinting': sb('TRACK', '#c8402a'), 'skillbook_sneaking': it(book(6.0, 9.0, 1.0), 'STALKING', '#3a4a2a',
                                                                              'leaf', '#6a8a3a', '#3a6a2a'),
        'skillbook_aiming': sb('MARKSMAN', '#5a3a2a', '#c23a22'), 'skillbook_maintenance': sb('REPAIR', '#2a5aa8'),
        'skillbook_masonry': sb('BRICKWORK', '#a8502a'), 'skillbook_metalworking': sb('METAL SHOP', '#4a4a4a', '#e07a1a'),
        'skillbook_piloting': it(book(7.0, 10.0, 1.4), 'FLIGHT', '#1e2a4a', 'wave', '#c8d4e8', band=CREAM),
        'mag_carpentry': it(magazine(), 'WOODWORK', '#c8a070', 'star', '#8a5a2a', band=CREAM),
        'mag_mechanics': it(magazine(), 'MOTOR', '#c23a22', 'heap', '#2a2a2a', band=CREAM),
        'mag_survival': it(magazine(), 'SURVIVE', '#4a5a2a', 'leaf', '#6a8a3a', '#3a6a2a', band=CREAM),
        'mag_cooking': it(magazine(), 'COOKING', '#e8c040', 'bowl', '#c8402a', band=CREAM),
        'mag_electronics': it(magazine(), 'CIRCUITS', '#2a6ab0', 'wave', '#e8c040', band=CREAM),
        'mag_farming': it(magazine(), 'FARMING', '#6aa03a', 'cow', '#2a2a2a', band=CREAM),
        'mag_hunting': it(magazine(), 'HUNTER', '#6a4a2a', 'fish', '#c8a070', band=CREAM),
        'mag_guns': it(magazine(), 'GUNS', '#1e1f20', 'star', '#c23a22', band='#c8a040'),
        'paperback': it(book(4.2, 7.0, 1.0), 'NOVEL', '#7a2a4a', 'star', '#e8c040', band=CREAM),
        'hardback': it(book(6.5, 9.5, 1.6), 'BOOK', '#2a3a2a', **BOOK),
        'bible': it(book(5.5, 8.5, 1.5), 'BIBLE', '#1e1f20', 'cross', '#c8a040', band='#1e1f20', word_ink='#c8a040',
                    art2='#c8a040'),
        'hymn_book': it(book(5.0, 7.5, 0.9), 'HYMNS', '#5a1a1a', band='#5a1a1a', word_ink='#c8a040', style='band'),
        'magazine': it(magazine(), 'LIFE', '#e8e4da', 'sun', '#e07a1a', band='#c23a22', word_ink='#e8e4da'),
        'comic_book': it(magazine(6.6, 10.2, 0.15), 'POW', '#e8c040', 'star', '#c23a22', band='#2a5aa8',
                         word_ink='#e8e4da'),
        'rare_comic': it(sleeved(magazine(6.6, 10.2, 0.15)), 'No 1', '#2a5aa8', 'star', '#e8c040', band='#c23a22',
                         word_ink='#e8e4da', tint='#e8f0f0'),
        'first_edition': it(book(6.0, 9.0, 1.4, spine='leather'), 'FIRST ED', '#4a2a1a', band='#4a2a1a',
                            word_ink='#c8a040', style='band'),
        'newspaper': it(newspaper, 'NEWS', '#e8e4da', band='#e8e4da', style='band', white=A.flat('#ddd6c4')),
        'childrens_book': it(book(8.0, 8.0, 0.5), 'ABC', '#e8c040', 'sun', '#e07a1a', band='#6ab0e0'),
        'textbook': it(book(8.0, 10.5, 1.4), 'ALGEBRA', '#2a5aa8', **BOOK),
        'encyclopedia': it(book(8.0, 10.5, 2.0, spine='gold'), 'VOL IX', '#5a1a1a', band='#5a1a1a', word_ink='#c8a040',
                           style='band'),
        'cookbook': it(book(7.5, 9.5, 1.2), 'RECIPES', '#e8e4da', 'bowl', '#c8402a', band='#c8402a', word_ink='#e8e4da'),
        'crossword_book': it(magazine(5.5, 8.0, 0.3), 'PUZZLES', '#e8e4da', band='#2a2a2a', word_ink='#e8e4da',
                             style='band'),
        'playing_cards': it(playing_cards, 'CARDS', '#c23a22', 'star', '#e8e4da', band='#c23a22', word_ink='#e8e4da'),
        'dice': it(dice),
        'board_game': it(board_game, 'MONOPOLE', '#e8e4da', 'star', '#c23a22', band='#2a6a3a', word_ink='#e8e4da'),
        'county_map': it(folded_paper(4.0, 9.0), 'COUNTY', '#e8e4da', 'wave', '#2a6ab0', band='#c23a22',
                         word_ink='#e8e4da'),
        'marked_map': it(folded_paper(4.0, 9.0), 'MAP', '#d8cba8', 'cross', '#c23a22', band='#d8cba8'),
        'transit_map': it(folded_paper(3.5, 8.0), 'TRANSIT', '#e8e4da', 'wave', '#c23a22', band='#2a5aa8',
                          word_ink='#e8e4da'),
        'timetable': it(folded_paper(3.5, 7.0, 2), 'TIMES', '#e8e4da', band='#e8c040', style='band'),
        'notebook': it(notebook, 'NOTES', '#2a5aa8', band=CREAM, style='band'),
        'pen': it(pen(), body='#2a5aa8', cap='#2a2a2a'),
        'pencil': it(pencil, body='#e8c040', wood=A.flat('#d8b080'), red=A.flat('#e08a9a')),
        'envelope': it(envelope, 'MAIL', '#e8dcc0', band='#e8dcc0', style='band', side=A.flat('#ddd0b0')),
        'parcel': it(parcel, 'POST', '#e8e4da', band='#c23a22', word_ink='#e8e4da', cloth=A.flat('#c8b080')),
        'journal': it(journal, 'DIARY', '#6a3a1a', band='#6a3a1a', word_ink='#c8a040', style='band',
                      strap=A.flat('#4a2a1a')),
        'photograph': it(photo(4.0, 6.0), '', '#6a7a5a', 'sun', '#e8c040'),
        'polaroid_photo': it(photo(3.5, 3.5, 0.25), '', '#5a6a7a', 'leaf', '#6a8a3a', '#3a6a2a'),
        'id_card': it(card(), 'ID', '#e8e4da', 'sun', '#c8a070', band='#2a5aa8', word_ink='#e8e4da', body='#e8e4da'),
        'press_pass': it(card(2.6, 3.8, lanyard=True), 'PRESS', '#e8e4da', band='#c23a22', word_ink='#e8e4da',
                         body='#e8e4da', strap=A.fabric('#2a5aa8')),
        'case_file': it(folder, 'CASE', '#d8b878', band='#d8b878', style='band'),
        'evidence_bag': it(evidence_bag, 'EVIDENCE', '#e8e4da', band='#c23a22', word_ink='#e8e4da', tint='#e8f0f0'),
        'flight_manual': it(binder, 'NATOPS', '#1e2a4a', band='#e8c040', side=A.plastic('#1e2a4a')),
        'kneeboard': it(kneeboard, 'CHECK', '#e8e4da', band='#e8e4da', style='band', body='#2a2a2a',
                        strap=A.fabric('#4a4a2a')),
        'maintenance_cards': it(card_ring, 'MRC', '#e8e4da', band='#e8c040', style='band'),
        'blueprint_paper': it(blueprint_roll, '', '#2a5aa8', 'wave', '#e8e4da', cloth=A.flat('#c23a22')),
        # ------------------------------------------------------------ keys and cards
        'house_key': it(key(2.2), metal=BRASS),
        'key_ring': it(key_ring, metal=SILVER),
        'padlock_key': it(key(1.6, 0.45), metal=SILVER),
        'safe_combination': it(card(3.0, 2.0), '24 36 7', '#efe8b0', band='#efe8b0', style='band', body='#efe8b0'),
        'deposit_box_key': it(key(2.6, 0.6, tag=(1.0, 0.8)), 'BOX 117', '#e8e4da', band='#e8e4da', metal=BRASS),
        'locker_key': it(key(1.8, 0.45, tag=(0.9, 0.9)), '42', '#e07a1a', band='#e07a1a', metal=SILVER),
        'keycard': it(card(), 'ACCESS', '#e8e4da', band='#2a5aa8', word_ink='#e8e4da', body='#e8e4da'),
        'military_keycard': it(card(), 'RESTRICTED', '#3a4a2a', band='#c23a22', word_ink='#e8e4da', body='#3a4a2a'),
        'bunker_key': it(big_key(5.0), metal=lambda f: P.steel(f, 0.4, tint='#5a5a52', spread=0.12)),
        'vault_key': it(big_key(6.0, 'gold')),
        'transit_ticket': it(card(2.8, 1.4, 0.03), 'TRANSIT', '#e8e4da', band='#c23a22', word_ink='#e8e4da',
                             body='#e8e4da'),
        'ticket_punch': it(ticket_punch, metal=A.chrome, grip=A.plastic('#1e1f20')),
        'credit_card': it(card(), 'CREDIT', '#2a5aa8', band='#c8a040', body='#2a5aa8'),
        'drivers_license': it(card(), 'LICENSE', '#e8e4da', 'sun', '#c8a070', band='#c23a22', word_ink='#e8e4da',
                              body='#e8e4da'),
        'registration': it(folded_paper(4.0, 6.0, 2), 'REG', '#e8e4da', band='#e8c040', style='band'),
        'dog_tags': it(dog_tags, metal=SILVER),
        'police_badge': it(badge, 'POLICE', '#1e2a4a', band='#1e2a4a', word_ink='#e8c040', metal=GOLD),
        # ------------------------------------------------------------ valuables
        'cash_bundle': it(bills(), '$', '#9ab08a', band='#9ab08a', style='band', mark=A.flat('#e8dcc0')),
        'cash_stack': it(cash_stack, '$100', '#9ab08a', band='#9ab08a', style='band', mark=A.flat('#c8a070')),
        'coin_roll': it(coin_roll, '25', '#c8a070', band='#c8a070', style='band', metal=SILVER),
        'wallet': it(wallet, leather=lambda f: P.leather(f), cash=A.flat('#9ab08a')),
        'cigarettes': it(cig_pack, 'SMOKES', '#c23a22', band='#e8e4da', white=A.flat('#e8e4da'), side=A.flat('#efe8d6')),
        'cigarette_carton': it(S(sh.box, w=8.5, h=3.5, d=1.8), 'SMOKES 10', '#c23a22', band='#e8e4da'),
        'cigars': it(humidor, 'HABANA', '#6a3a1a', band='#c8a040', gold=GOLD),
        'pipe_tobacco': it(S(sh.rect_tin, w=4.0, h=1.2, d=3.0, key=False), 'TOBACCO', '#2a4a2a', 'leaf', '#c8a040',
                           band=CREAM),
        'coffee_beans_sack': it(sack_lying, 'COFFEE', '#c8b088', band='#6a3a1a', word_ink='#e8e4da', side=A.flat('#a89070')),
        'wristwatch': it(watch(), metal=A.chrome, strap=A.flat('#3a2a1a'), dial=A.flat('#e8e4da')),
        'gold_watch': it(watch(0.75, case='gold', bracelet=True), gold=GOLD, dial=A.flat('#efe6d0')),
        'pocket_watch': it(pocket_watch, gold=GOLD, dial=A.flat('#efe6d0')),
        'wedding_ring': it(ring(), gold=GOLD),
        'silver_necklace': it(necklace(mat='silver'), silver=SILVER, gem=A.flat('#6ab0e0')),
        'gold_necklace': it(necklace(), gold=GOLD, gem=A.flat('#c23a22')),
        'pearl_necklace': it(necklace(bead=0.22, pendant=False, mat='pearl'), pearl=A.flat('#f2eee4', 0.04)),
        'earrings': it(earrings, gold=GOLD, gem=A.flat('#6ab0e0')),
        'diamond_ring': it(ring(stone=0.28), gold=GOLD, gem=A.flat('#e8f4f8', 0.04)),
        'class_ring': it(ring(0.5, 0.25, stone=0.25), gold=GOLD, gem=A.flat('#c23a22')),
        'jewellery_box': it(jewel_box, wood=A.flat('#6a2a2a', 0.08), gold=GOLD),
        'silver_coin': it(coin(), silver=SILVER),
        'coin_collection': it(album, 'COINS', '#1e2a4a', band='#1e2a4a', word_ink='#c8a040', style='band',
                              silver=SILVER),
        'silverware_set': it(cutlery_case, wood=A.flat('#5a2a1a', 0.08), felt=A.flat('#7a1a2a'), silver=SILVER),
        'gold_coin': it(coin(0.6, 0.12, 'gold'), gold=GOLD),
        'gold_bar': it(gold_bar, gold=GOLD),
        'inverted_stamp': it(sleeved(photo(1.2, 1.4, 0.1)), '', '#c23a22', 'wave', '#2a5aa8', tint='#e8f0f0'),
        'jewelled_egg': it(egg, gold=GOLD, gem=A.flat('#2a8a6a', 0.05)),
        'uncut_diamonds': it(diamond_pouch, felt=A.flat('#1e1f20'), cloth=A.flat('#c8a040'), gem=A.flat('#e8f0f0', 0.06)),
        'chronometer': it(watch_box, wood=A.flat('#3a1a0a', 0.08), felt=A.flat('#1e2a4a'), gold=GOLD,
                          dial=A.flat('#1e1f20')),
        'stamp_collection': it(album, 'STAMPS', '#2a4a2a', band='#2a4a2a', word_ink='#c8a040', style='band',
                               silver=A.flat('#c23a22')),
        'baseball_cards': it(card_box, 'CARDS', '#2a5aa8', 'star', '#e8c040', band=CREAM),
        'rookie_card': it(rookie_card, '', '#2a6a3a', 'star', '#e8c040', tint='#e8f0f0'),
        'signed_ball': it(signed_ball, tint='#e8f0f0'),
        'championship_ring': it(ring(0.55, 0.3, stone=0.35), gold=GOLD, gem=A.flat('#2a5aa8')),
        'trophy': it(trophy, 'CHAMP', '#c8a040', band='#c8a040', style='band', gold=GOLD),
        'medal': it(medal, gold=GOLD, cloth=A.flat('#2a5aa8')),
        'war_medal_case': it(medal_case, wood=A.flat('#3a1a0a', 0.08), felt=A.flat('#1e2a4a'), gold=GOLD, silver=SILVER,
                             cloth=A.flat('#a8281f'), tint='#e8f0f0'),
        'antique_clock': it(mantel_clock, wood=A.flat('#4a2a1a', 0.1), gold=BRASS, dial=A.flat('#efe6d0')),
        'art_print': it(frame(12.0, 16.0, 0.6, 'dark'), '', '#6ab0e0', 'sun', '#e07a1a', style='window', band='#e8e4da'),
        'oil_painting': it(frame(16.0, 20.0, 1.6, 'gold'), '', '#3a4a2a', 'leaf', '#6a8a3a', '#3a5a2a', style='window',
                           band='#c8b888', gold=GOLD),
        'church_chalice': it(chalice, gold=GOLD),
        # ------------------------------------------------------------ toys and hobbies
        'teddy_bear': it(teddy, fur=A.fabric('#9a6a3a'), felt=A.flat('#c8a070'), red=A.fabric('#a8281f')),
        'action_figure': it(figure(), body='#2a5aa8', skin=A.flat('#d8a888')),
        'mint_action_figure': it(blister(), 'HERO', '#c23a22', 'star', '#e8c040', band='#2a5aa8', word_ink='#e8e4da',
                                 body='#2a6a3a', skin=A.flat('#d8a888'), tint='#e8f0f0'),
        'sealed_console': it(S(sh.box, w=14.0, h=10.0, d=4.0), 'GAME SYSTEM', '#1e1f20', 'star', '#c23a22',
                             band='#e8e4da'),
        'doll': it(doll, cloth=A.fabric('#c23a7a'), skin=A.flat('#f0c8a8'), hair=A.flat('#e8c040')),
        'toy_car': it(toy_car_b(), body='#c23a22', tint='#a8c8d8'),
        'toy_truck': it(toy_car_b(6.0, 2.6, 1.8, truck=True), body='#e8c040', tint='#a8c8d8'),
        'rubiks_cube': it(cube, red=A.flat('#c23a22'), white=A.flat('#e8e4da'), body='#2a5aa8'),
        'yo_yo': it(yo_yo, body='#c23a22'),
        'jigsaw_puzzle': it(S(sh.flat_box, w=12.0, h=2.2, d=9.0), '1000', '#2a6a8a', 'sun', '#e8c040', band=CREAM),
        'model_kit': it(S(sh.flat_box, w=13.0, h=2.0, d=7.0), 'MODEL KIT', '#e8e4da', 'wave', '#2a5aa8', band='#c23a22',
                        word_ink='#e8e4da'),
        'rc_car': it(rc_car, body='#c23a22', tint='#3a4a5a'),
        'baseball': it(baseball),
        'baseball_glove': it(glove, leather=lambda f: P.leather(f, '#8a4a1a'), cloth=A.flat('#6a3a1a')),
        'football': it(football, leather=lambda f: P.leather(f, '#6a3a1a')),
        'basketball': it(S(sh.ball, r=4.7, mat='skin'), skin=A.skin('#d86a2a', spots='#2a2a2a', shine=0.05)),
        'tennis_racket': it(racket, grip=A.plastic('#e8e4da'), metal=A.chrome, white=A.flat('#d8d8c8')),
        'skateboard': it(skateboard, wood=A.flat('#c8a070', 0.08), dark=A.flat('#1e1f20'), white=A.plastic('#e8c040')),
        'roller_skates': it(roller_skate, white=A.flat('#e8e4da'), red=A.plastic('#c23a7a')),
        'frisbee': it(frisbee, '', '#e8c040', 'star', '#c23a22', body='#e8c040'),
        'fishing_trophy': it(fish_plaque, wood=A.flat('#6a3a1a', 0.1), skin=A.skin('#6a8a5a', spots='#3a4a2a'), gold=GOLD),
        'harmonica': it(harmonica, metal=A.chrome, wood=A.flat('#8a5a2a')),
        'acoustic_guitar': it(guitar, wood=A.flat('#c88a4a', 0.08)),
        'trumpet': it(trumpet, gold=BRASS, silver=SILVER),
        'whistle': it(whistle, metal=A.chrome),
        'stopwatch': it(stopwatch, metal=A.chrome, dial=A.flat('#e8e4da')),
        'pocket_mirror': it(mirror, gold=GOLD, lens=A.flat('#c8dce0', 0.03)),
        'umbrella': it(umbrella, cloth=A.fabric('#1e1f20'), handle=A.flat('#6a3a1a'), strap=A.fabric('#1e1f20')),
        'rosary': it(rosary, wood=A.flat('#4a2a1a'), silver=SILVER),
        'snow_globe': it(snow_globe, tint='#e8f0f4', wood=A.flat('#6a3a1a'), red=A.flat('#c23a22')),
        'lucky_rabbit_foot': it(rabbit_foot, fur=A.fabric('#e8e0d0'), gold=GOLD),
        # ------------------------------------------------------------ service gear
        'sound_powered_phone': it(handset, body='#3a3a3a'),
        'squadron_patch': it(patch, 'VF 31', '#1e2a4a', 'star', '#e8c040', band='#1e2a4a', word_ink='#e8c040',
                             cloth=A.fabric('#c8a040')),
        'signal_mirror': it(signal_mirror, lens=A.flat('#c8dce0', 0.03)),
        'smoke_canister': it(grenade_can, 'SMOKE', '#4a5a3a', band='#e8e4da', style='band'),
        'entrenching_tool': it(e_tool, metal=lambda f: P.paint(f, '#4a5a3a', steel_base=0.4, chips=0.4),
                               wood=A.flat('#6a4a2a')),
        'mil_canteen_cup': it(canteen_cup, metal=A.alu),
        'field_rations_case': it(rations_case, 'RATIONS', '#c8b088', band='#4a5a3a', word_ink='#e8e4da',
                                 strap=A.flat('#4a5a3a')),
        'handcuffs': it(handcuffs, metal=A.chrome),
        'police_radio_belt': it(belt_radio, body='#1e1f20', keys=A.plastic('#c23a22')),
        'fire_extinguisher': it(extinguisher, 'FIRE', '#c23a22', band='#e8e4da', style='band', red=A.plastic('#1e1f20')),
        'fire_hose_nozzle': it(nozzle, metal=BRASS, red=A.plastic('#c23a22')),
        'halligan_tool': it(halligan, metal=A.steel, grip=A.plastic('#e8c040')),
        'scba_tank': it(scba, 'AIR', '#e8c040', band='#1e1f20', word_ink='#e8c040', strap=A.fabric('#1e1f20')),
        'traffic_cone': it(cone, body='#e07a1a', white=A.flat('#e8e4da')),
        'road_sign': it(road_sign, 'STOP', '#c23a22', band='#c23a22', word_ink='#e8e4da', style='band'),
        # ------------------------------------------------------------ junk
        'broken_glass': it(shards, tint='#c8dcd8'),
        'newspaper_bundle': it(paper_bundle, 'NEWS', '#ddd6c4', band='#ddd6c4', style='band', white=A.flat('#d8d0bc'),
                               cloth=A.flat('#c8b080')),
        'cardboard': it(flat_cardboard, 'THIS SIDE UP', '#c8a878', band='#c8a878', style='band'),
        'spoiled_food': it(spoiled, '', '#e8e4da', body='#e8e4da', food=A.flat('#6a5a3a'), mould=A.flat('#8aa86a')),
        'bone_scrap': it(bones, white=A.flat('#e0d6c0', 0.1)),
        'cigarette_butts': it(butts, cap=A.flat('#d89a5a'), white=A.flat('#e8e4da')),
        'fast_food_wrapper': it(wrapper, 'BURGER', '#e8e4da', band='#c23a22', word_ink='#e8c040'),
        'broken_radio': it(broken_radio, body='#6a5a4a', tint='#3a3a3a'),
        'broken_toy': it(broken_toy, body='#2a5aa8', tint='#a8c8d8'),
        'old_shoe': it(shoe, leather=lambda f: P.leather(f, '#3a2a1a'), cloth=A.flat('#c8b080')),
        'rubber_band_ball': it(band_ball, red=A.plastic('#c23a22'), body='#e8c040'),
        'paperclips': it(clips, 'CLIPS', '#2a5aa8', band=CREAM, metal=A.chrome),
        'stapler': it(stapler, body='#1e1f20', metal=A.chrome),
        'coat_hanger': it(hanger, metal=A.chrome),
        'light_tube': it(light_tube, white=A.flat('#efeee8', 0.03)),
        'pipe_offcut': it(offcut, metal=A.steel),
        'wood_scraps': it(scraps, wood=A.flat('#b08a5a', 0.12)),
        'sheet_of_tin': it(tin_sheet, metal=lambda f: P.steel(f, 0.5, tint='#a0a4a8', spread=0.15)),
        'lost_property_box': it(lost_box, 'LOST', '#e8e4da', band='#e8e4da', style='band',
                                cloth=A.fabric('#c23a22'), leather=lambda f: P.leather(f)),
    })
    return G
