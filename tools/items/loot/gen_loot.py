"""World loot item models: every wave-1 item (food, drink, medical, hygiene and the leftovers they need) built from
boxes in inches and painted in the character style through the weapon tooling (tools/weapons/kit.py: toon outline
round each part's outside, chips, lit tops), a little worn and grimy as found. Labels carry real lettering and a
product picture (art.py); the shapes are in shapes.py.

Items of one roster category share one texture sheet, split when it would pass 1024 px (performance thread: no
per-item sheets). Sizes are real; anything longer than MAX inches is scaled down to fit the game's model limits.

    python3 tools/items/loot/gen_loot.py [--only REGEX] [--out DIR]

--out writes a preview set (models + sheets + a manifest for the viewer) instead of the mod's assets.
"""
import argparse
import json
import re
import sys
import time
import zlib
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / 'tools/weapons'))
import model as M  # noqa: E402
import paint as P  # noqa: E402
import kit  # noqa: E402
from headgear import display_for  # noqa: E402

import art as A  # noqa: E402
import shapes as sh  # noqa: E402
import gen_java  # noqa: E402
import wave2  # noqa: E402
import wave3  # noqa: E402

NS = 'zomboidcraft'
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
WEAR = 0.32         # found in the world: handled, a little scuffed, never battered
MAX = 44.0          # longest side in model units (the game takes -16..32)
SHEET = 1024


def S(fn, **kw):
    return lambda m: fn(m, **kw)


# ================================================================== custom builds (inches, standing on y = 0, front +z)


def egg_carton(m):
    m.box([-5.75, 0, -2.0], [5.75, 2.2, 2.0], 'label')
    for i in range(6):
        for j in (-1, 1):
            x, z = -4.8 + i * 1.92, j * 1.0
            m.box([x - 0.7, 2.2, z - 0.7], [x + 0.7, 2.7, z + 0.7], 'side')


def cheese(m):
    m.box([-2.0, 0, -1.25], [2.0, 1.6, 1.25], 'food')
    m.box([-0.8, 0, -1.3], [0.8, 1.65, 1.3], 'label')


def loaf(m):
    m.box([-2.25, 0, -5.0], [2.25, 3.8, 5.0], 'label')
    m.box([-1.9, 3.8, -5.0], [1.9, 4.6, 5.0], 'label')
    m.box([-1.0, 0.8, -5.9], [1.0, 3.4, -5.0], 'seal')
    m.box([-0.35, 1.7, -6.1], [0.35, 2.5, -5.9], 'tab')


def chicken(m):
    sh.tray(m, w=10, d=8, h=1.0, food_h=0.0)
    m.parts.pop()
    m.parts.pop()
    sh.ball(m, 3.2, 'food', 0.62, cy=0.6 + 2.0)
    for s in (-1, 1):
        m.seg((s * 1.4, 3.6), (s * 2.2, 5.0), 1.0, 1.0, 'food', z=2.4)
    m.box([1.5, 0.6, 1.8], [3.4, 0.64, 3.4], 'label')


def wrapped_parcel(m):
    m.box([-3.5, 0, -2.5], [3.5, 2.2, 2.5], 'label')
    m.box([-3.55, 0, -0.12], [3.55, 2.26, 0.12], 'seal')
    m.box([-0.12, 0, -2.55], [0.12, 2.26, 2.55], 'seal')


def cake(m):
    m.box([-5.0, 0, -5.0], [5.0, 0.3, 5.0], 'side')
    m.rod(0.3, 3.5, 4.2, 'food')
    m.rod(3.5, 3.9, 3.7, 'icing')
    for a in range(0, 360, 60):
        x, z = 2.3 * np.cos(np.radians(a)), 2.3 * np.sin(np.radians(a))
        m.box([x - 0.12, 3.9, z - 0.12], [x + 0.12, 5.6, z + 0.12], 'candle')


def six_pack(m):
    for i in range(3):
        for j in (-1, 1):
            m.rod(4.6, 4.9, 1.1, 'alu', cx=-2.7 + i * 2.7, cz=j * 1.35)
    m.box([-4.1, 0, -2.75], [4.1, 4.6, 2.75], 'label')
    m.box([-4.1, 4.6, -0.12], [4.1, 6.4, 0.12], 'label')


def wine_box(m):
    m.box([-5.0, 0, -2.5], [5.0, 8.0, 2.5], 'label')
    m.rod(2.5, 3.2, 0.6, 'cap', cx=0.0, cz=1.2, axis=2)
    m.box([-1.5, 7.4, -0.6], [1.5, 8.05, 0.6], 'seal')


def thermos(m):
    m.rod(0, 9.5, 1.6, 'body')
    m.rod(9.5, 10.4, 1.25, 'metal')
    m.rod(10.4, 12.6, 1.66, 'cap')
    m.box([1.4, 2.5, -0.35], [2.2, 7.5, 0.35], 'cap')


def hip_flask(m):
    m.box([-1.9, 0, -0.55], [1.9, 5.0, 0.55], 'metal')
    m.box([-1.6, 5.0, -0.45], [1.6, 5.4, 0.45], 'metal')
    m.rod(5.4, 6.2, 0.45, 'cap')


def rag(colour_mat='cloth'):
    def build(m):
        m.box([-3.2, 0, -2.6], [3.0, 0.35, 2.4], colour_mat)
        m.box([-2.6, 0.35, -2.2], [2.8, 0.7, 2.0], colour_mat)
        m.box([-1.8, 0.7, -1.4], [1.5, 0.95, 1.5], colour_mat)
        m.box([2.6, 0, -3.0], [3.6, 0.3, -2.0], colour_mat, rot=dict(axis='y', angle=22.5, origin=[3.0, 0, -2.5]))
    return build


def sling(m):
    m.box([-3.5, 0, -2.5], [3.5, 0.8, 2.5], 'cloth')
    m.box([-3.0, 0.8, -2.0], [3.0, 1.2, 2.0], 'cloth')
    m.box([-0.6, 1.2, -2.0], [0.6, 1.3, 2.0], 'strap')


def tweezers(m):
    m.seg((-2.0, 0.15), (2.0, 0.55), 0.25, 0.35, 'metal')
    m.seg((-2.0, 0.15), (2.0, 0.15), 0.22, 0.35, 'metal')


def inhaler(m):
    m.box([-0.6, 1.0, -0.65], [0.6, 3.4, 0.65], 'body')
    m.box([-0.55, 0, -0.6], [0.55, 1.0, 1.7], 'body')
    m.box([-0.4, 0.2, 1.7], [0.4, 0.8, 1.8], 'cap')
    m.rod(3.4, 4.2, 0.45, 'alu')


def pen(body_len=5.0, r=0.4):
    def build(m):
        m.rod(-body_len / 2, body_len / 2 - 1.3, r, 'body', axis=0)
        m.rod(body_len / 2 - 1.3, body_len / 2, r * 1.05, 'cap', axis=0)
        m.box([-0.6, r * 0.6, -0.25], [0.6, r + 0.05, 0.25], 'label')
        sh.shift_up(m, r)
    return build


def thermometer(m):
    m.rod(-2.4, 2.2, 0.16, 'glass', axis=0)
    m.rod(2.2, 2.6, 0.14, 'metal', axis=0)
    m.box([-2.0, 0.1, 0.15], [1.8, 0.2, 0.17], 'mark')
    sh.shift_up(m, 0.16)


def bp_cuff(m):
    m.box([-3.0, 0, -2.0], [3.0, 1.6, 2.0], 'cloth')
    m.box([-3.0, 1.6, -1.9], [2.6, 1.9, 1.9], 'cloth')
    m.rod(0.0, 0.5, 1.0, 'metal', cx=0.8, cz=0.0)
    m.rod(0.5, 0.58, 0.85, 'mark', cx=0.8, cz=0.0)
    m.seg((-3.0, 0.4), (-5.0, 0.4), 0.25, 0.25, 'body', z=1.0)
    sh.ball(m, 0.9, 'body', 1.2, cy=0.9)
    for p in m.parts[-2:]:
        p['lo'] = p['lo'] + np.array([-6.0, 0, 1.0])
        p['hi'] = p['hi'] + np.array([-6.0, 0, 1.0])


def stethoscope(m):
    m.arc((0, 3.5), 2.5, 200, 340, 0.3, 0.3, 'body')
    m.seg((-2.35, 2.6), (-2.6, 5.4), 0.3, 0.3, 'metal')
    m.seg((2.35, 2.6), (2.6, 5.4), 0.3, 0.3, 'metal')
    m.seg((0.0, 1.0), (0.0, -2.5), 0.3, 0.3, 'body')
    m.rod(-3.4, -2.6, 0.8, 'metal')
    sh.shift_up(m, 3.4)


def scalpel(m):
    m.box([-2.5, 0, -0.18], [1.5, 0.1, 0.18], 'metal')
    m.seg((1.5, 0.05), (2.8, 0.1), 0.35, 0.06, 'blade')


def tourniquet(m):
    m.box([-4.5, 0, -0.75], [4.5, 0.15, 0.75], 'strap')
    m.rod(-1.6, 1.6, 0.25, 'body', cx=0.4, cz=0.0, axis=2)
    m.box([-1.0, 0.15, -0.8], [0.0, 0.5, 0.8], 'body')
    sh.shift_up(m, 0.1)


def iv_bag(w, h):
    def build(m):
        m.box([-w / 2, 1.0, -0.45], [w / 2, h, 0.45], 'glass')
        m.box([-w / 2 + 0.4, h * 0.35, 0.45], [w / 2 - 0.4, h * 0.75, 0.5], 'label')
        m.box([-w / 2, h, -0.12], [w / 2, h + 0.5, 0.12], 'seal')
        m.rod(0.0, 1.0, 0.18, 'cap', cx=-0.5)
        m.rod(0.0, 1.0, 0.18, 'cap', cx=0.5)
    return build


def body_bag(m):
    m.box([-6.0, 0, -4.0], [6.0, 2.6, 4.0], 'cloth')
    m.box([-5.6, 2.6, -3.6], [5.6, 3.2, 3.6], 'cloth')
    m.box([-5.2, 3.2, -0.12], [5.2, 3.3, 0.12], 'zip')


def soap(m):
    m.box([-1.6, 0, -1.1], [1.6, 0.8, 1.1], 'body')
    m.box([-1.4, 0.8, -0.9], [1.4, 1.0, 0.9], 'body')
    m.box([-0.8, 1.0, -0.35], [0.8, 1.02, 0.35], 'mark')


def toothbrush(m):
    m.box([-3.6, 0, -0.22], [2.6, 0.3, 0.22], 'body')
    m.box([2.6, 0, -0.25], [3.6, 0.2, 0.25], 'body')
    m.box([2.65, 0.2, -0.22], [3.55, 0.65, 0.22], 'cap')


def razor(m):
    m.box([-3.0, 0, -0.2], [1.6, 0.35, 0.2], 'body')
    m.box([1.6, 0, -0.75], [2.3, 0.4, 0.75], 'cap')
    m.box([2.3, 0.05, -0.75], [2.4, 0.35, 0.75], 'metal')


def comb(m):
    m.box([-3.0, 0.9, -0.08], [3.0, 1.3, 0.08], 'body')
    for i in range(24):
        x = -2.9 + i * 0.25
        m.box([x, 0.0, -0.06], [x + 0.12, 0.9, 0.06], 'body')


def hairbrush(m):
    m.box([-4.5, 0, -0.35], [0.5, 0.6, 0.35], 'body')
    m.box([0.5, 0, -1.2], [4.0, 0.7, 1.2], 'body')
    m.box([0.8, 0.7, -1.0], [3.7, 0.9, 1.0], 'cap')
    m.box([0.8, 0.9, -1.0], [3.7, 1.6, 1.0], 'bristle')


def hair_dryer(m):
    m.rod(-3.5, 3.5, 1.4, 'body', cx=0.0, cz=7.0, axis=2)
    m.rod(3.5, 4.2, 1.1, 'metal', cx=0.0, cz=7.0, axis=2)
    m.box([-0.7, 0, -1.5], [0.7, 6.0, 0.7], 'body')
    m.box([-0.2, 3.6, 0.7], [0.2, 4.4, 0.9], 'cap')


def perfume(m):
    m.box([-1.3, 0, -0.65], [1.3, 2.8, 0.65], 'glass')
    m.rod(2.8, 3.1, 0.3, 'metal')
    m.box([-0.6, 3.1, -0.6], [0.6, 4.0, 0.6], 'cap')


def nail_clippers(m):
    m.box([-1.2, 0, -0.25], [1.0, 0.12, 0.25], 'metal')
    m.seg((-1.2, 0.2), (1.0, 0.35), 0.22, 0.5, 'metal')
    m.box([0.6, 0.35, -0.2], [0.8, 0.55, 0.2], 'metal')


def sponge(m):
    m.box([-2.2, 0, -1.4], [2.2, 1.0, 1.4], 'body')
    m.box([-2.2, 1.0, -1.4], [2.2, 1.3, 1.4], 'cap')


def mop_bucket(m):
    m.rod(0, 10.0, 5.0, 'body')
    m.rod(10.0, 10.6, 5.3, 'body')
    m.box([-5.4, 9.0, -0.3], [5.4, 9.3, 0.3], 'metal')
    m.box([-0.5, 2.0, -0.5], [0.5, 34.0, 0.5], 'stick')
    m.box([-1.6, 4.0, -1.6], [1.6, 9.0, 1.6], 'cloth')


def broom(m):
    m.box([-0.45, 7.0, -0.45], [0.45, 46.0, 0.45], 'stick')
    m.box([-4.5, 2.5, -1.0], [4.5, 7.0, 1.0], 'metal')
    m.box([-5.0, 0, -0.9], [5.0, 2.5, 0.9], 'bristle')


def spool(m):
    m.rod(0, 0.22, 0.8, 'core')
    m.rod(0.22, 1.8, 0.62, 'cloth')
    m.rod(1.8, 2.02, 0.8, 'core')


def pin_card(m):
    m.box([-1.6, 0, -1.2], [1.6, 0.06, 1.2], 'label')
    for i in range(5):
        z = -0.8 + i * 0.4
        m.box([-1.2, 0.06, z - 0.04], [1.2, 0.14, z + 0.04], 'metal')
        m.box([0.9, 0.06, z - 0.1], [1.25, 0.18, z + 0.1], 'cap')


def can_opener(m):
    m.box([-0.5, 0, -0.3], [0.5, 1.1, 0.3], 'metal')
    m.rod(0.3, 0.75, 0.5, 'metal', cx=0.0, cz=0.45, axis=2)
    m.box([0.55, 0.6, -0.35], [1.5, 1.1, 0.35], 'handle')
    m.seg((-0.3, 0.2), (-6.0, 0.2), 0.55, 0.45, 'handle', z=0.25)
    m.seg((-0.3, 0.9), (-6.0, 0.6), 0.55, 0.45, 'handle', z=-0.25)


def crushed_can(m):
    sh.soda_can(m, h=3.4)
    for p in m.parts:
        if p['mat'] == 'label' and p['lo'][1] < 1.0:
            p['lo'] = p['lo'] + np.array([0.15, 0, 0])
            p['hi'] = p['hi'] - np.array([0.0, 0, 0.2])


# ================================================================== the table

DEF_LOOK = {
    'tin': A.tin, 'lid': A.tin, 'alu': A.alu, 'tab': A.alu, 'metal': A.steel, 'core': A.cardboard,
    'straw': A.flat('#e8e0d0'), 'tray': A.flat('#ece8e0', 0.03), 'mark': A.flat('#b3261e'),
    'handle': A.plastic('#2a2b2d'), 'zip': A.plastic('#1e1f20'), 'strap': A.fabric('#33363a'),
    'stem': A.flat('#5a4326'), 'leaf': A.skin('#4e7a2f'), 'stick': A.flat('#b08a5a', 0.1),
    'bristle': A.flat('#c8a050', 0.15), 'blade': A.chrome, 'food': A.meat(), 'cloth': A.fabric('#e8e3d8'),
    'icing': A.flat('#f2e8e0'), 'candle': A.flat('#e07a9a'),
    # wave 2 parts
    'wood': lambda f: P.wood(f), 'grip': A.plastic('#1e1f20'), 'red': A.flat('#a8281f'), 'lens': A.flat('#f0ecd0'),
    'dark': A.plastic('#1a1a1a'), 'rubber': lambda f: P.rubber(f), 'keys': A.plastic('#5a5a5a'),
    'grille': A.plastic('#1e1f20'), 'frame': A.plastic('#2a2a2a'), 'board': A.flat('#2f6a3a'),
    'wax': A.flat('#efe6d0', 0.04), 'dial': A.flat('#efe6d0'), 'wire': A.steel, 'leather': lambda f: P.leather(f),
    'stone': A.flat('#8a8a84', 0.12), 'wicker': A.flat('#a8804a', 0.15), 'white': A.flat('#efeae0'),
    'head': A.flat('#2f6a3e'), 'bill': A.flat('#d8a020'), 'cardboard': A.cardboard,
    # wave 3 parts
    'gold': wave3.GOLD, 'silver': wave3.SILVER, 'gem': A.flat('#e8f0f0', 0.05), 'felt': A.flat('#7a1a2a'),
    'fur': A.fabric('#9a6a3a'), 'hair': A.flat('#5a3a1a'), 'pearl': A.flat('#f2eee4', 0.04), 'cash': A.flat('#9ab08a'),
    'mould': A.flat('#8aa86a'),
}


def label_painter(L, name, under=None):
    wrap, flat = A.wrap(L, name, under), A.printed_box(L, name)

    def fn(f):
        if f.box.get('round', 0) >= 0.3 and f.box.get('axis', 1) == 1:
            return wrap(f)
        return flat(f)
    return fn


def it(build, word='', base=None, pic=None, art=None, art2=None, style=None, band=None, word_ink=None, ink=None,
       cap='#e8e4da', tint='#cfdcd8', liquid=None, level=0.85, body=None, under=None, **mats):
    L = {k: v for k, v in dict(word=word, base=base, pic=pic, art=art, art2=art2, band=band, word_ink=word_ink,
                                ink=ink, style=style or ('window' if pic else 'band')).items() if v is not None}
    return dict(build=build, L=L, cap=cap, tint=tint, liquid=liquid, level=level, body=body, under=under, mats=mats)


def look_for(name, e):
    L = e['L']
    base = L.get('base') or A.hue(name)
    look = dict(DEF_LOOK)
    look['label'] = label_painter(L, name, e['under'])
    look['side'] = A.flat(A.tone(base, 0.8))
    look['seal'] = A.foil(A.tone(base, 0.9))
    look['cap'] = e['cap'] if callable(e['cap']) else A.plastic(e['cap'])
    look['glass'] = A.glass(e['tint'], e['liquid'], e['level'])
    look['body'] = A.plastic(e['body'] or base)
    look['skin'] = A.skin(base)
    for k, v in e['mats'].items():
        look[k] = A.flat(v) if isinstance(v, str) else v
    look['_after'] = [lambda f, c: P.grime(f, c, 0.1 + f.wear * 0.2)]
    # printing needs whole texels per letter pixel: labels on small things get finer texels than the rest
    look['_px'] = {'label': 16 if e.get('big', 0) <= 12 else 10}
    return look


C = sh.can
RED, CREAM, BLUE, WHITE, DARK = '#a8281f', '#efe6d0', '#2a5aa8', '#e8e4da', '#1c1a17'
RX = dict(style='rx', word='RX', tint='#b0601a', liquid='#e8d9a8', level=0.7)

ITEMS = {
    # ---------------------------------------------------------------- canned & dry
    'canned_beans': it(C, 'BEANS', '#8a2a22', 'heap', '#a0522d', '#6b2f1a', under=A.tin),
    'canned_soup': it(C, 'SOUP', '#b02a22', 'heap', '#d0602a', '#e8b070', band=CREAM, under=A.tin),
    'canned_chili': it(C, 'CHILI', '#6e2a1a', 'heap', '#8a2a1a', '#5a1a10', band='#e8c070', under=A.tin),
    'canned_corn': it(C, 'CORN', '#2f6a3a', 'heap', '#e8c23a', '#d9a92a', under=A.tin),
    'canned_peas': it(C, 'PEAS', '#2e5a8c', 'heap', '#6a9a3a', '#4a7a2a', under=A.tin),
    'canned_carrots': it(C, 'CARROTS', '#c8622a', 'heap', '#e07a2a', '#c0501a', under=A.tin),
    'canned_tomatoes': it(C, 'TOMATOES', '#9a2a1f', 'fruit', '#c8321f', under=A.tin),
    'canned_peaches': it(C, 'PEACHES', '#d58a3a', 'fruit', '#f0a050', under=A.tin),
    'canned_pineapple': it(C, 'PINEAPPLE', '#c8a02a', 'sun', '#e8c860', under=A.tin),
    'canned_tuna': it(S(C, d=3.4, h=1.6), 'TUNA', '#2a4a7a', 'fish', '#8aa0b8', under=A.tin),
    'canned_sardines': it(sh.rect_tin, 'SARDINES', '#1f5a6a', 'fish', '#a8b4bc'),
    'canned_ham': it(S(sh.tall_tin, w=4.6, h=3.2, d=3.0), 'HAM', '#7a2a2a', 'heap', '#d88a80', '#b05a50'),
    'canned_corned_beef': it(S(sh.tall_tin, w=3.0, h=4.0, d=2.0), 'BEEF', '#3a3f7a', 'heap', '#b0604a', '#8a3a2a'),
    'canned_spaghetti': it(C, 'SPAGHETTI', '#d05a20', 'heap', '#e88a2a', '#b84a1a', under=A.tin),
    'canned_ravioli': it(C, 'RAVIOLI', '#8a3a1a', 'heap', '#e8b060', '#a8301a', under=A.tin),
    'canned_stew': it(C, 'STEW', '#5a3a22', 'heap', '#8a5a2a', '#5a2a10', under=A.tin),
    'canned_dog_food': it(S(C, d=3.4, h=4.6), 'DOG FOOD', '#8a6a2a', 'heap', '#6a3a1a', '#4a2a10', under=A.tin),
    'canned_cat_food': it(S(C, d=3.0, h=1.6), 'CAT', '#5a2a6a', 'fish', '#d8a050', under=A.tin),
    'canned_condensed_milk': it(S(C, d=3.0, h=3.2), 'MILK', BLUE, 'cow', DARK, band='#f2ece0', under=A.tin),
    'canned_coffee': it(S(C, d=4.2, h=5.6), 'COFFEE', '#3a2418', 'bowl', '#6a3a1a', '#3a1a08', band='#c8a040',
                        lid=A.plastic('#c23a22')),
    'peanut_butter': it(S(sh.jar, d=3.2, h=4.2), 'PEANUT', '#c8862a', tint='#b07a3a', liquid='#a0662a', cap='#c23a22'),
    'jam_jar': it(S(sh.jar, d=2.8, h=3.6), 'JAM', CREAM, 'fruit', '#9a1a2a', tint='#6a1a2a', liquid='#5a0a1a',
                  cap='#d9c49a'),
    'honey_jar': it(S(sh.jar, d=3.0, h=4.0), 'HONEY', '#e8c060', 'star', '#c8861a', tint='#c8861a',
                    liquid='#b0700a', cap='#d0b060', style='band'),
    'pickles_jar': it(S(sh.jar, d=3.6, h=6.0), 'PICKLES', '#2a5a2a', 'leaf', '#7a9a3a', tint='#8a9a4a',
                      liquid='#6a7a2a', cap='#e0c040', band='#efe6c0'),
    'home_preserves': it(S(sh.jar, d=3.0, h=4.6, label=0.3), 'PEACH', '#e9e0c8', tint='#d0802a', liquid='#c8701a',
                         cap='#a8a8a0', word_ink='#3a2a1a', band='#e9e0c8'),
    'cereal_box': it(S(sh.box, flap=True), 'CEREAL', '#d8a52a', 'bowl', '#c46a1c', '#e8b04a', band='#efe6d0'),
    'oats_box': it(S(sh.tub, d=5.0, h=9.5), 'OATS', '#a8402a', 'bowl', '#c8a060', '#e0c890', band=CREAM, cap='#d8c8a0'),
    'pasta_box': it(S(sh.box, w=3.0, h=9.0, d=2.0), 'PASTA', BLUE, 'wave', '#e8c870', band=CREAM),
    'rice_bag': it(S(sh.bag, w=5.0, h=8.0, d=2.5), 'RICE', '#d8d2c0', band=RED, word_ink='#f0e8d8'),
    'flour_bag': it(S(sh.sack, w=5.5, h=9.0, d=3.5), 'FLOUR', '#e6dfcc', band=BLUE, word_ink='#f0e8d8'),
    'sugar_bag': it(S(sh.sack, w=5.0, h=8.0, d=3.0), 'SUGAR', '#efe9df', band='#2a7a4a', word_ink='#f0e8d8'),
    'salt_box': it(S(sh.tub, d=3.4, h=5.4), 'SALT', BLUE, band=WHITE),
    'cooking_oil': it(S(sh.bottle, kind='pet'), 'OIL', '#2a6a3a', 'drop', '#e0b030', tint='#d8b84a',
                      liquid='#c89a2a', cap='#e0c040', band=CREAM),
    'crackers_box': it(S(sh.box, w=4.0, h=8.5, d=3.0), 'CRACKERS', '#c23a22', 'heap', '#e0b060', '#c89040',
                       band=CREAM),
    'instant_noodles': it(S(sh.tub, d=3.6, h=3.4), 'NOODLES', '#e04a2a', 'wave', '#f0d070', band=CREAM,
                          cap=A.foil('#d8d0c0')),
    'mac_and_cheese': it(S(sh.box, w=5.0, h=7.0, d=1.8), 'MAC', BLUE, 'bowl', '#f0a020', '#e8c040', band=CREAM),
    'powdered_milk': it(S(sh.box, w=5.0, h=7.0, d=3.0), 'MILK', WHITE, 'cow', DARK, band=WHITE, style='window'),
    'dried_beans': it(S(sh.bag, w=4.5, h=7.0, d=1.5), 'BEANS', '#d8c8a0', 'heap', '#7a3a2a', '#5a2a1a', band='#efe6d0'),
    'beef_jerky': it(S(sh.pouch, w=5.0, h=8.0, d=0.8), 'JERKY', '#5a2a1a', art='#8a3a1a', band=CREAM, style='stripes'),
    'trail_mix': it(S(sh.pouch, w=5.0, h=7.0, d=1.2), 'TRAIL MIX', '#3a6a3a', 'heap', '#a07040', '#7a4a2a', band=CREAM),
    'mre': it(S(sh.pouch, w=7.0, h=10.0, d=1.2), 'MEAL', '#8a7a52', band='#6b5a3a', word_ink='#e0d8b8',
              seal=A.foil('#7a6a48')),
    'ration_bar': it(S(sh.bar, w=4.5, h=1.2, d=3.0), 'RATION', '#c8b070', band='#6b5a3a', word_ink='#e0d8b8'),
    'survival_bucket': it(S(sh.tub, d=11.0, h=13.0), 'FOOD', '#e6e2d6', band=RED, word_ink='#f0e8d8', cap='#e6e2d6'),
    'baby_formula': it(S(C, d=4.0, h=5.4), 'FORMULA', WHITE, 'star', '#6aa0d0', band='#dce8f0',
                       lid=A.plastic('#6aa0d0')),
    # ---------------------------------------------------------------- fresh, fridge & freezer
    'apple': it(sh.apple, base='#a8261e', skin=A.skin('#a8261e')),
    'orange': it(sh.citrus, base='#e0821e', skin=A.skin('#e0821e', '#c86a10')),
    'banana': it(sh.banana, base='#e8c840', skin=A.skin('#e8c840', '#5a4020')),
    'lettuce': it(S(sh.head_veg, r=2.6), base='#8ab84a', leaf=A.skin('#6aa03a')),
    'tomato': it(sh.tomato, base='#c8321f'),
    'potato': it(sh.potato, base='#b0885a', skin=A.skin('#b0885a', '#6a4a2a')),
    'onion': it(sh.onion, base='#a47a44'),
    'carrot': it(sh.carrot, base='#e0782a'),
    'cabbage': it(S(sh.head_veg, r=3.0), base='#a8c87a', leaf=A.skin('#7aa05a')),
    'eggs_carton': it(egg_carton, 'EGGS', '#d8d0bc', band=BLUE, word_ink='#f0e8d8', side=A.flat('#d8d0bc')),
    'milk_carton': it(sh.carton, 'MILK', '#f0ece2', 'cow', DARK, band=BLUE, word_ink='#f0ece2', style='window'),
    'orange_juice': it(sh.carton, 'JUICE', '#f0a020', 'fruit', '#e0821e', band=CREAM),
    'butter': it(S(sh.box, w=5.0, h=1.4, d=2.6), 'BUTTER', '#f2e8c8', band=RED, word_ink='#f2e8c8'),
    'cheese_block': it(cheese, 'CHEESE', RED, band='#e8c040', food=A.skin('#e8a830')),
    'sliced_bread': it(loaf, 'BREAD', '#f0ece2', art='#d0a040', band=CREAM, style='stripes', tab=A.plastic('#2a6ab0')),
    'hot_dogs': it(S(sh.tray, w=6.0, d=3.0, h=0.6, food_h=0.7), 'WIENERS', RED, food=A.meat('#c86a5a', '#d88a7a')),
    'bacon': it(S(sh.tray, w=7.0, d=4.0, h=0.4, food_h=0.4), 'BACON', RED, food=A.meat('#b04a40', '#f0d8c8')),
    'lunch_meat': it(S(sh.tray, w=5.0, d=5.0, h=0.5, food_h=0.6), 'BOLOGNA', RED, food=A.meat('#d08a8a', '#f0c8c8')),
    'ground_beef': it(S(sh.tray, w=6.0, d=5.0, h=1.0, food_h=1.0), 'BEEF', RED, food=A.meat('#a83a35', '#d8a090')),
    'steak': it(S(sh.tray, w=8.0, d=6.0, h=0.8, food_h=0.8), 'STEAK', RED),
    'chicken_whole': it(chicken, 'CHICKEN', '#e8c040', food=A.skin('#e8c8a0', '#c8a070')),
    'pork_chops': it(S(sh.tray, w=8.0, d=6.0, h=0.8, food_h=0.7), 'PORK', RED, food=A.meat('#d08a80', '#f2e0d0')),
    'fish_fillets': it(S(sh.box, w=7.0, h=1.6, d=5.0), 'FISH', BLUE, 'fish', '#d8c0a0', band='#dce8f0'),
    'frozen_pizza': it(S(sh.flat_box, w=10.0, h=1.2, d=10.0), 'PIZZA', '#b02a22', 'heap', '#c8321f', '#e8c060',
                       band=CREAM),
    'frozen_dinner': it(S(sh.flat_box, w=9.0, h=1.4, d=7.0), 'DINNER', '#1f3a6a', 'heap', '#8a5a2a', '#e8c060',
                        band=CREAM),
    'ice_cream': it(S(sh.tub, d=5.0, h=4.5), 'ICE CREAM', '#f0e6d0', 'star', '#8a3a2a', band='#8a3a2a',
                    word_ink='#f0e6d0', cap='#8a3a2a', style='band'),
    'venison': it(wrapped_parcel, 'DEER', '#d8cba8', band='#d8cba8', word_ink='#3a2a1a', seal=A.flat('#e8e0d0')),
    'leftovers': it(S(sh.tub, d=5.0, h=2.6), '', '#d8d4c8', cap='#3a7ab0'),
    # ---------------------------------------------------------------- snacks & sweets
    'chips_bag': it(S(sh.bag, w=6.0, h=9.0, d=2.4), 'CHIPS', '#d8a020', art='#c23a22', band=CREAM, style='stripes'),
    'pretzels': it(S(sh.bag, w=6.0, h=9.0, d=2.2), 'PRETZELS', BLUE, 'heap', '#a0602a', '#7a4a1a', band=CREAM),
    'popcorn_bag': it(S(sh.bag, w=6.0, h=10.0, d=3.0), 'POPCORN', '#d02a2a', art='#f0e0a0', band=CREAM,
                      style='stripes'),
    'microwave_popcorn': it(S(sh.flat_box, w=7.0, h=0.8, d=5.0), 'POPCORN', '#e8a020', 'heap', '#f0e8c0', '#e0c070',
                            band=CREAM),
    'chocolate_bar': it(S(sh.bar, w=5.5, h=0.4, d=2.2), 'CHOCOLATE', '#5a2a1a', band='#c8a040', word_ink='#5a2a1a'),
    'peanut_candy_bar': it(S(sh.bar, w=5.0, h=0.6, d=1.6), 'PEANUT', '#e0b020', band=RED, word_ink='#f0e8d0'),
    'gum_pack': it(S(sh.bar, w=3.0, h=0.4, d=1.0), 'GUM', '#3ab06a', band=CREAM),
    'hard_candy': it(S(sh.bag, w=4.5, h=6.0, d=1.4), 'CANDY', '#c23a7a', art='#f0e0e0', band=CREAM, style='stripes'),
    'licorice': it(S(sh.bag, w=3.0, h=8.0, d=0.6), 'LICORICE', '#b0201e', band=CREAM),
    'cookies_pack': it(S(sh.box, w=7.0, h=2.2, d=2.6), 'COOKIES', BLUE, 'heap', '#7a4a2a', '#a0703a', band=CREAM),
    'snack_cakes': it(S(sh.box, w=5.0, h=1.6, d=3.5), 'CAKES', WHITE, band='#2a7ab0', word_ink=WHITE),
    'granola_bar': it(S(sh.bar, w=4.5, h=0.6, d=1.5), 'GRANOLA', '#3a7a3a', band=CREAM),
    'crackers_cheese': it(S(sh.bar, w=5.0, h=1.2, d=1.8), 'CHEESE', '#e07a1a', band=CREAM),
    'beef_stick': it(S(sh.bar, w=7.0, h=0.6, d=0.7), 'BEEF', '#7a2a1a', band='#e8c070'),
    'peanuts_jar': it(S(sh.jar, d=3.2, h=4.4), 'PEANUTS', BLUE, 'heap', '#c08040', '#a06020', tint='#c8a060',
                      liquid='#b08040', cap=BLUE),
    'sunflower_seeds': it(S(sh.bag, w=4.0, h=6.0, d=1.2), 'SEEDS', '#2a3a5a', 'sun', '#e0b020', band='#e8e0c8'),
    'marshmallows': it(S(sh.bag, w=6.0, h=8.0, d=3.0), 'MALLOWS', '#2a6ab0', band='#f2ece0'),
    'candy_box': it(S(sh.box, w=3.5, h=5.0, d=1.0), 'CANDY', '#c23a22', band='#f0d020'),
    'nachos_tray': it(S(sh.tray, w=7.0, d=5.0, h=1.0, food_h=0.8), 'NACHOS', RED, food=A.skin('#e8b840', '#e07a1a')),
    'pie_slice': it(S(sh.tray, w=5.0, d=4.0, h=0.6, food_h=1.4), 'PIE', '#8a3a2a', food=A.skin('#c8862a', '#7a2a2a')),
    'donut_box': it(S(sh.flat_box, w=11.0, h=3.0, d=11.0), 'DONUTS', '#f0e6d0', 'star', '#e07a9a', band='#f8f0e0'),
    'birthday_cake': it(cake, side=A.cardboard, food=A.flat('#f0dce0'), icing=A.flat('#e07a9a')),
    'fancy_chocolates': it(S(sh.flat_box, w=8.0, h=1.2, d=6.0), 'CHOCOLATES', '#6a1a2a', band='#c8a040',
                           word_ink='#6a1a2a'),
    # ---------------------------------------------------------------- drinks
    'water_bottle': it(S(sh.bottle, kind='pet'), 'WATER', '#2a6ab0', 'wave', '#e8f0f8', band='#2a6ab0',
                       word_ink='#f0f4f8', tint='#c8dce8', liquid='#a8c8e0', cap='#2a6ab0'),
    'water_jug': it(sh.jug, 'WATER', '#2a6ab0', band='#2a6ab0', word_ink='#f0f4f8', body='#d8e4e8', cap='#2a6ab0'),
    'cola_can': it(sh.soda_can, 'COLA', '#a3241f', 'wave', '#efe9de', band='#a3241f', word_ink='#efe9de',
                   style='band'),
    'lemon_soda_can': it(sh.soda_can, 'LEMON', '#3a9a3a', 'fruit', '#e8d040', band='#f0e8a0', style='band'),
    'root_beer_can': it(sh.soda_can, 'ROOT BEER', '#5a2a1a', band='#e8d0a0'),
    'soda_bottle': it(S(sh.bottle, kind='two_litre'), 'COLA', '#a3241f', band='#a3241f', word_ink='#efe9de',
                      tint='#4a2a18', liquid='#2a160c', cap='#a3241f'),
    'glass_soda': it(S(sh.bottle, kind='soda'), 'POP', '#c23a22', band='#c23a22', word_ink=CREAM, tint='#5a8a6a',
                     liquid='#2a1a10', cap='#c8c8c0'),
    'sports_drink': it(S(sh.bottle, kind='pet'), 'SPORT', '#e07a1a', art='#f0b040', band=CREAM, style='stripes',
                       tint='#a8d0e8', liquid='#3a8ae0', cap='#e07a1a'),
    'juice_box': it(sh.juice_box, 'JUICE', '#3a8a3a', 'fruit', '#e0821e', band=CREAM),
    'iced_tea': it(S(sh.bottle, kind='pet'), 'ICED TEA', '#2a5a3a', 'leaf', '#6aa03a', '#3a6a2a', band=CREAM,
                   tint='#c89040', liquid='#a8601a', cap=WHITE),
    'coffee_thermos': it(thermos, body='#2f5a3e', cap='#3a3a3a'),
    'instant_coffee': it(S(sh.jar, d=3.4, h=5.0), 'COFFEE', '#3a2418', band='#c8a040', tint='#3a2418',
                         liquid='#2a1a10', cap=RED),
    'tea_bags': it(S(sh.box, w=5.0, h=3.0, d=3.0), 'TEA', '#c8401a', 'leaf', '#4a7a2a', '#2a5a1a', band=CREAM),
    'cocoa_mix': it(S(sh.box, w=4.0, h=6.0, d=2.0), 'COCOA', '#5a2a1a', 'bowl', '#7a4a2a', '#5a2a1a', band=CREAM),
    'canned_beer': it(sh.soda_can, 'BEER', BLUE, band='#e8e2d0'),
    'beer_six_pack': it(six_pack, 'BEER', BLUE, band='#e8e2d0', style='band'),
    'bottled_beer': it(S(sh.bottle, kind='beer'), 'LAGER', '#e8e0c8', band=RED, word_ink='#f0e8d0', tint='#6a3a12',
                       liquid='#3a1a08', cap='#c8a040'),
    'wine_bottle': it(S(sh.bottle, kind='wine'), 'WINE', CREAM, 'leaf', '#6a1a2a', '#3a0a10', band=CREAM,
                      word_ink='#3a1a1a', style='band', tint='#2a3a1a', liquid='#2a0a10', cap='#6a1a2a'),
    'boxed_wine': it(wine_box, 'WINE', '#6a1a2a', 'leaf', '#c8a040', '#8a6a20', band=CREAM, cap='#2a2a2a'),
    'vodka': it(S(sh.bottle, kind='wine'), 'VODKA', WHITE, band=RED, word_ink=CREAM, tint='#d8e4e8',
                liquid='#d0e0e8', cap=RED),
    'whiskey': it(S(sh.bottle, kind='square'), 'WHISKEY', '#141414', band='#141414', word_ink='#e5d7ae',
                  tint='#6b3a12', liquid='#7a3d0e', cap='#151515'),
    'rum': it(S(sh.bottle, kind='wine'), 'RUM', '#e8d8a8', 'star', '#8a1a1a', band='#e8d8a8', word_ink='#3a1a08',
              style='band', tint='#5a3010', liquid='#6a3810', cap='#c8a040'),
    'moonshine_jar': it(S(sh.jar, d=3.4, h=6.0, label=0.3), 'XXX', '#d8cba8', band='#d8cba8', word_ink='#3a2a1a',
                        tint='#d8e4e0', liquid='#e0e8e0', cap='#9a9a92'),
    'hip_flask': it(hip_flask, cap='#9a9a92', metal=A.chrome),
    'aged_whiskey': it(S(sh.bottle, kind='square'), '18 YEAR', '#1a2a1a', band='#1a2a1a', word_ink='#d8c070',
                       tint='#5a2e10', liquid='#6a3410', cap='#2a1a10'),
    'vintage_wine': it(S(sh.bottle, kind='wine'), '1961', '#e8dcc0', band='#e8dcc0', word_ink='#5a1a10',
                       tint='#1a2a14', liquid='#1a0508', cap='#3a0a10'),
    'champagne': it(S(sh.bottle, kind='champagne'), 'BRUT', CREAM, band=CREAM, word_ink='#2a2a2a', tint='#1a3a1a',
                    liquid='#1a2a10', cap=A.foil('#c8a040')),
    # ---------------------------------------------------------------- medical
    'sterile_bandage': it(S(sh.roll, d=2.0, h=3.0, core=0.6), label=A.fabric('#efeae0')),
    'adhesive_bandages': it(S(sh.box, w=3.2, h=4.5, d=1.2), 'BANDAGES', WHITE, 'cross', RED, band='#f4f0e8'),
    'gauze_pads': it(S(sh.box, w=4.0, h=5.0, d=1.5), 'GAUZE', BLUE, 'cross', RED, band='#f4f0e8'),
    'rag': it(rag(), cloth=A.fabric('#8a8478')),
    'ripped_sheet': it(rag(), cloth=A.fabric('#c8ccd4')),
    'arm_sling': it(sling, strap=A.fabric('#d8d2c4')),
    'elastic_wrap': it(S(sh.roll, d=2.4, h=3.0, core=0.6), label=A.fabric('#d8b090')),
    'medical_tape': it(S(sh.tape_roll, d=2.2, w=1.0), label=A.fabric('#efeae0'), core=A.plastic(WHITE)),
    'disinfectant': it(S(sh.bottle, kind='pet'), 'DISINFECTANT', WHITE, 'cross', RED, band='#f4f0e8',
                       tint='#8a5a2a', liquid='#6a3a1a', cap=WHITE),
    'rubbing_alcohol': it(S(sh.bottle, kind='pet'), 'ALCOHOL', WHITE, band=BLUE, word_ink=WHITE, tint='#d8e8f0',
                          liquid='#e0ecf0', cap=BLUE),
    'peroxide': it(S(sh.bottle, kind='pet'), 'PEROXIDE', WHITE, band='#5a3a1a', word_ink=WHITE, tint='#5a3a1a',
                   cap=WHITE),
    'antiseptic_wipes': it(S(sh.box, w=4.0, h=1.0, d=3.0), 'WIPES', '#2a8aa0', 'drop', '#efeae0', band='#2a8aa0'),
    'suture_kit': it(S(sh.pouch, w=3.0, h=4.5, d=0.4), 'SUTURE', '#3a7ab0', style='rx', band='#f4f0e8'),
    'tweezers': it(tweezers),
    'burn_ointment': it(S(sh.tube, length=5.0, w=1.4), 'BURN', '#e07a1a', band=CREAM, cap=WHITE),
    'antibiotic_cream': it(S(sh.tube, length=4.5, w=1.2), 'CREAM', '#2a7a5a', band=CREAM, cap=WHITE),
    'painkillers': it(S(sh.pill_bottle, d=1.8, h=3.2), 'PAIN', RED, band='#f0ece0', tint='#ece8e0', cap=WHITE),
    'strong_painkillers': it(sh.pill_bottle, base='#3b5c8c', band='#f4f0e8', **RX),
    'antibiotics': it(sh.pill_bottle, base='#3a7a3a', band='#f4f0e8', **RX),
    'sleeping_pills': it(S(sh.pill_bottle, d=1.8, h=3.0), 'SLEEP', '#2a3a7a', 'star', '#e8d070', band='#2a3a7a',
                         word_ink=CREAM, style='band', tint='#ece8e0', cap=WHITE),
    'beta_blockers': it(sh.pill_bottle, base='#7a3a6a', band='#f4f0e8', **RX),
    'antidepressants': it(sh.pill_bottle, base='#c8862a', band='#f4f0e8', **RX),
    'vitamins': it(S(sh.pill_bottle, d=2.4, h=4.0), 'VITAMINS', '#e8a020', 'sun', '#e05a1a', band=CREAM,
                   tint='#c8862a', cap=WHITE),
    'cold_medicine': it(S(sh.bottle, kind='flask_bottle'), 'COLD', '#2a4a9a', band=CREAM, tint='#6a2a6a',
                        liquid='#5a1a5a', cap=WHITE),
    'antacids': it(S(sh.bar, w=4.0, h=0.9, d=1.0), 'ANTACID', '#3a8ac8', band=CREAM),
    'allergy_pills': it(S(sh.box, w=3.0, h=2.0, d=0.8), 'ALLERGY', WHITE, band='#c8402a', word_ink=WHITE),
    'inhaler': it(inhaler, body='#3a6ab0', cap='#8a8a8a'),
    'insulin_kit': it(pen(6.0, 0.4), 'INSULIN', WHITE, body='#e8e4da', cap='#3a7ab0'),
    'thermometer': it(thermometer, tint='#e8f0f0', mark=A.flat('#c8261e')),
    'cold_pack': it(S(sh.pouch, w=4.0, h=6.0, d=0.8), 'COLD PACK', '#3a8ac8', band=CREAM),
    'blood_pressure_cuff': it(bp_cuff, cloth=A.fabric('#3a4a5a'), body='#1e1f20', mark=A.flat('#efeae0')),
    'stethoscope': it(stethoscope, body='#1e1f20', metal=A.chrome),
    'scalpel': it(scalpel),
    'surgical_gloves': it(S(sh.box, w=9.5, h=5.0, d=3.0), 'GLOVES', WHITE, band='#3a7ab0', word_ink=WHITE),
    'face_masks': it(S(sh.box, w=7.0, h=4.0, d=3.5), 'MASKS', WHITE, band='#3a8a8a', word_ink=WHITE),
    'first_aid_kit': it(sh.kit_case, body=WHITE, lid=lambda f: P.paint(f, '#d9d4c6', steel_base=0.5, chips=0.35),
                        handle=A.chrome),
    'trauma_kit': it(S(sh.soft_bag, w=16.0, h=9.0, d=9.0, straps=True), body='#b02a22', mark=A.flat('#efeae0'),
                     strap=A.fabric('#2a2a2a')),
    'ifak': it(S(sh.soft_bag, w=6.0, h=7.0, d=3.0), body='#5a5a3a', mark=A.flat('#b02a22')),
    'tourniquet': it(tourniquet, body='#1e1f20', strap=A.fabric('#1e1f20')),
    'morphine_autoinjector': it(pen(6.0, 0.45), 'MORPHINE', WHITE, body='#4a7a3a', cap='#e0b020'),
    'saline_bag': it(iv_bag(6.0, 9.0), 'SALINE', WHITE, band=WHITE, tint='#dde8ec', liquid='#d0e0e8', level=0.9,
                     cap=BLUE, seal=A.flat('#dde8ec')),
    'blood_bag': it(iv_bag(4.0, 7.0), 'O NEG', WHITE, band=WHITE, tint='#8a1a1a', liquid='#6a0a0a', level=0.95,
                    cap=RED, seal=A.flat('#d8d0d0')),
    'adrenaline_pen': it(pen(6.0, 0.45), 'EPI', WHITE, body='#e0b020', cap='#3a7ab0'),
    'field_surgery_kit': it(S(sh.kit_case, w=12.0, h=8.0, d=3.5), body='#5a5a3a', lid=A.plastic('#5a5a3a'),
                            mark=A.flat('#efeae0')),
    'portable_defib': it(S(sh.kit_case, w=11.0, h=9.0, d=4.5), body='#3a8a4a', lid=A.plastic('#3a8a4a'),
                         mark=A.flat('#efeae0')),
    'surgical_set': it(S(sh.kit_case, w=12.0, h=3.0, d=7.0, handle=False), body=WHITE, lid=A.steel,
                       mark=A.flat('#2a2a2a'), metal=A.chrome),
    'body_bag': it(body_bag, cloth=A.fabric('#2a2a2a')),
    'vet_meds': it(S(sh.pill_bottle, d=2.6, h=4.0), 'VET', '#3a7a3a', band=CREAM, tint='#ece8e0', cap=WHITE),
    # ---------------------------------------------------------------- hygiene & household
    'soap_bar': it(soap, body='#d8e0c8', mark=A.flat('#c0c8b0')),
    'shampoo': it(sh.squeeze, 'SHAMPOO', '#3a8a8a', band=CREAM, body='#3a8a8a'),
    'toothbrush': it(toothbrush, body='#3a7ab0', cap=WHITE),
    'toothpaste': it(S(sh.tube, length=7.0, w=1.6), 'TOOTHPASTE', WHITE, band=RED, word_ink=WHITE, cap=WHITE),
    'toilet_paper': it(sh.roll, label=A.paper('#f2efe8')),
    'razor': it(razor, body='#3a6ab0', cap='#2a2a2a'),
    'shaving_cream': it(S(sh.spray, d=2.4, h=6.5), 'SHAVE', WHITE, band=BLUE, word_ink=WHITE, cap=BLUE),
    'deodorant': it(S(sh.spray, d=1.8, h=5.0), 'DEO', '#2a4a7a', band=CREAM, cap='#2a4a7a'),
    'comb': it(comb, body='#1e1f20'),
    'hairbrush': it(hairbrush, body='#6a3a2a', cap='#1e1f20', bristle=A.flat('#1e1f20')),
    'hair_dryer': it(hair_dryer, body='#d8d0c8', cap='#2a2a2a'),
    'perfume': it(perfume, tint='#e8c8d0', liquid='#e0a8b8', cap='#c8a040', metal=A.chrome),
    'makeup_bag': it(S(sh.soft_bag, w=7.0, h=4.5, d=3.0), body='#c86a8a', mark=A.flat('#e8c0d0')),
    'nail_clippers': it(nail_clippers, metal=A.chrome),
    'bleach': it(sh.jug, 'BLEACH', BLUE, band='#efeae0', body='#efeae0', cap=BLUE),
    'detergent': it(S(sh.jug, w=7.0, h=9.0, d=4.5), 'DETERGENT', '#e07a1a', band=BLUE, word_ink=WHITE,
                    body='#e07a1a', cap=BLUE),
    'dish_soap': it(S(sh.squeeze, w=2.6, h=8.0, d=1.4), 'DISH', '#5aa03a', band=CREAM, body='#5aa03a', cap=WHITE),
    'sponge': it(sponge, body='#e8c040', cap='#3a7a3a'),
    'trash_bags': it(S(sh.box, w=6.0, h=4.0, d=3.0), 'TRASH BAGS', '#2a2a2a', band='#e07a1a'),
    'mop_bucket': it(mop_bucket, body='#e0b020', cloth=A.fabric('#d8d0c0')),
    'broom': it(broom),
    'air_freshener': it(S(sh.spray, d=2.4, h=7.0), 'FRESH', '#6aa0d0', 'leaf', '#3a8a3a', '#2a6a2a', band=CREAM,
                        cap='#6aa0d0'),
    'sewing_kit': it(S(sh.rect_tin, w=4.5, h=1.2, d=3.0, key=False), 'SEWING', '#c23a5a', 'star', '#e8c040',
                     band=CREAM),
    'thread_spool': it(spool, cloth=A.fabric('#2a4a9a'), core=A.flat('#c8a070')),
    'safety_pins': it(pin_card, '', CREAM, metal=A.chrome, cap=A.plastic('#c8b060')),
    'bug_spray': it(S(sh.spray, d=2.4, h=7.0), 'BUG', '#3a6a2a', art='#e0b020', band=CREAM, style='stripes',
                    cap='#e0b020'),
    'sunscreen': it(S(sh.squeeze, w=2.4, h=6.5, d=1.3), 'SUN', '#e07a1a', 'sun', '#f0c020', band=CREAM, body=WHITE,
                    cap='#e07a1a'),
    # ---------------------------------------------------------------- leftovers and the opener
    'empty_soda_can': it(crushed_can, 'SODA', None, band=CREAM),
    'empty_jar': it(S(sh.jar, d=3.0, h=4.2, label=0), tint='#d8e4e0', cap='#9a9a92'),
    'can_opener': it(can_opener, handle=A.plastic('#a8281f'), metal=A.steel),
}
ITEMS.update(wave2.table(it))
ITEMS.update(wave3.table(it))


# ================================================================== output


def scaled(m, k):
    for p in m.parts:
        p['lo'], p['hi'] = p['lo'] * k, p['hi'] * k
        if p['rot']:
            p['rot'] = dict(p['rot'], origin=list(np.asarray(p['rot']['origin']) * k))
        if 'round' in p:
            p['round'] = p['round'] * k
    return m


def make(name):
    e = ITEMS[name]

    def build(m):
        e['build'](m)
        lo, hi = m.bounds()
        big = max(hi - lo)
        if big > MAX:
            scaled(m, MAX / big)
    probe = M.Model(name)
    e['build'](probe)
    lo, hi = probe.bounds()
    e['big'] = float(max(hi - lo))
    return kit.drawn(name, build, look=look_for(name, e), seed=zlib.crc32(name.encode()) % 1000, wear=WEAR)


def px_for(m):
    lo, hi = m.bounds()
    return int(np.clip(round(64.0 / max(hi - lo)), 5, 16))


def sheets(names):
    """Paints the items and packs them into shared sheets of at most SHEET px. Yields (names, models, img, uvs)."""
    models = [make(n) for n in names]
    patches = []
    for mi, m in enumerate(models):
        for (key, face, layers) in kit.paint([m], px=px_for(m)):
            patches.append(((mi, key[1]), face, layers))
    groups = [list(range(len(models)))]
    out = []
    while groups:
        g = groups.pop(0)
        sub = [pt for pt in patches if pt[0][0] in g]
        img, uvs, T = kit.pack(sub)
        if T > SHEET and len(g) > 1:
            groups[:0] = [g[:len(g) // 2], g[len(g) // 2:]]
            continue
        out.append(([names[i] for i in g], [models[i] for i in g], g, img, uvs, T))
    return out


def tab_of(rows):
    """Sheet name per item: the roster category it's in, shortened."""
    short = {'Canned & dry food': 'dry', 'Fresh, fridge & freezer': 'fresh', 'Snacks & sweets': 'snacks',
             'Drinks': 'drinks', 'Medical': 'medical', 'Hygiene & household': 'hygiene',
             'Kitchen & cooking': 'kitchen', 'Hand tools': 'tools', 'Building & materials': 'materials',
             'Electrical & electronics': 'electrical', 'Light & fire': 'light', 'Outdoors, camping & farm': 'camping',
             'Vehicle & fuel': 'car', 'Books, magazines & papers': 'books', 'Keys, cards & documents': 'keys',
             'Valuables & trade goods': 'valuables', 'Toys, hobbies & personal': 'toys', 'Military & service': 'service',
             'Junk & scrap': 'junk'}
    return {r['id']: short.get(r['cat'], 'misc') for r in rows}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='.')
    ap.add_argument('--out', default=None)
    a = ap.parse_args()
    rows = [r for r in gen_java.rows() if r['live']]
    missing = [r['id'] for r in rows if r['id'] not in ITEMS]
    if missing:
        raise SystemExit(f'no model for: {missing}')
    cat = tab_of(rows)
    names = [r['id'] for r in rows if re.search(a.only, r['id'])]
    preview = Path(a.out) if a.out else None
    manifest = []
    t0 = time.time()
    for group in dict.fromkeys(cat[n] for n in names):
        mine = [n for n in names if cat[n] == group]
        for si, (gn, models, idx, img, uvs, T) in enumerate(sheets(mine)):
            sheet = group if si == 0 else f'{group}_{si + 1}'
            tex = f'item/loot/{sheet}'
            kit.save_textures(img, (preview / f'{sheet}.png') if preview else ASSETS / f'textures/{tex}.png')
            for n, m, mi in zip(gn, models, idx):
                lo, hi = m.bounds()
                shift = np.array([8.0, 8.0, 8.0]) - (lo + hi) / 2
                els = kit.elements(m, mi, uvs, shift)
                fit = kit.shifted_model([m], shift)
                size = float(max(hi - lo))
                disp = display_for(fit, gui=14.0, hand=float(np.clip(3.0 + size * 0.6, 4.5, 14.0)))
                if preview:
                    M.dump({'textures': {'0': sheet, 'particle': sheet}, 'elements': els}, preview / f'{n}.json')
                    manifest.append(dict(name=n, size=(hi - lo).tolist(), sheet=sheet))
                else:
                    M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                            'elements': els, 'display': disp}, ASSETS / f'models/item/loot/{n}.json')
                    if (ASSETS / f'meshes/loot/{n}.zmesh').exists():     # drawn as a toon mesh (mesh.py)
                        M.dump({'model': {'type': 'minecraft:special', 'base': f'{NS}:item/loot/{n}',
                                          'model': {'type': f'{NS}:mesh', 'id': f'loot/{n}', 'part': 'body'}}},
                               ASSETS / f'items/{n}.json')
                    else:
                        M.dump({'model': kit.ref(f'loot/{n}')}, ASSETS / f'items/{n}.json')
            print(f'{sheet}: {len(gn)} items, {T}px  ({time.time() - t0:.0f}s)')
    if preview:
        (preview / 'manifest.json').write_text(json.dumps(manifest))


if __name__ == '__main__':
    main()
