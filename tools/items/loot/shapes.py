"""Shape builders for loot items: each takes a Model and its own sizes in inches, stands the item on y = 0 with its
front toward +z, and names the materials it uses ('label', 'cap', ...) so a look can paint them.

The round things are Model.rod (three boxes in a stepped circle), which is how the guns' barrels and the cans
already read in the character style.
"""
import math

import numpy as np


# ================================================================== cans, tins, jars


def can(m, d=3.0, h=4.4):
    r = d / 2
    m.rod(0.0, 0.18, r, 'tin')
    m.rod(0.18, h - 0.18, r - 0.04, 'label')
    m.rod(h - 0.18, h, r, 'tin')
    m.rod(h, h + 0.04, r * 0.84, 'lid')


def soda_can(m, d=2.6, h=4.8):
    r = d / 2
    m.rod(0.0, 0.25, r * 0.88, 'alu')
    m.rod(0.25, h * 0.85, r, 'label')
    m.rod(h * 0.85, h * 0.93, r * 0.9, 'label')
    m.rod(h * 0.93, h - 0.08, r * 0.8, 'alu')
    m.rod(h - 0.08, h, r * 0.84, 'alu')
    m.box([-0.25, h - 0.02, -0.55 * r], [0.25, h + 0.04, 0.35 * r], 'tab')


def rect_tin(m, w=4.2, h=1.1, d=3.0, key=True):
    # a flat sardine or corned-beef tin lying down: its printed lid on top, a key on the side
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'tin')
    m.box([-w / 2 + 0.12, h, -d / 2 + 0.12], [w / 2 - 0.12, h + 0.05, d / 2 - 0.12], 'label')
    if key:
        m.box([w / 2, h * 0.5, -0.1], [w / 2 + 0.9, h * 0.62, 0.1], 'tin')
        m.box([w / 2 + 0.75, h * 0.3, -0.35], [w / 2 + 0.95, h * 0.82, 0.35], 'tin')


def tall_tin(m, w=3.5, h=5.0, d=2.4):
    # a square ham or corned beef tin standing up, label round the front
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'label')
    m.box([-w / 2 - 0.05, h - 0.15, -d / 2 - 0.05], [w / 2 + 0.05, h, d / 2 + 0.05], 'tin')
    m.box([-w / 2 - 0.05, 0, -d / 2 - 0.05], [w / 2 + 0.05, 0.15, d / 2 + 0.05], 'tin')


def jar(m, d=3.0, h=4.2, label=0.55):
    r = d / 2
    m.rod(0.0, h - 0.6, r, 'glass')
    if label:
        m.rod(h * 0.25, h * 0.25 + (h - 0.6) * label * 0.9, r + 0.03, 'label')
    m.rod(h - 0.6, h - 0.45, r * 0.85, 'glass')
    m.rod(h - 0.45, h, r * 0.92, 'cap')


def tub(m, d=4.5, h=3.0):
    r = d / 2
    m.rod(0.0, h - 0.3, r * 0.92, 'label')
    m.rod(h - 0.3, h, r, 'cap')


# ================================================================== bottles


def bottle(m, kind='beer'):
    if kind == 'beer':
        m.rod(0.0, 5.4, 1.2, 'glass')
        m.rod(1.2, 4.2, 1.23, 'label')
        m.rod(5.4, 6.2, 0.85, 'glass')
        m.rod(6.2, 8.6, 0.5, 'glass')
        m.rod(8.6, 9.0, 0.56, 'cap')
    elif kind == 'wine':
        m.rod(0.0, 7.6, 1.5, 'glass')
        m.rod(2.0, 5.6, 1.53, 'label')
        m.rod(7.6, 8.6, 1.0, 'glass')
        m.rod(8.6, 11.4, 0.55, 'glass')
        m.rod(10.2, 12.0, 0.6, 'cap')
    elif kind == 'soda':
        m.rod(0.0, 4.2, 1.1, 'glass')
        m.rod(1.0, 3.2, 1.13, 'label')
        m.rod(4.2, 5.6, 0.75, 'glass')
        m.rod(5.6, 7.6, 0.45, 'glass')
        m.rod(7.6, 7.9, 0.52, 'cap')
    elif kind == 'square':
        m.box([-1.7, 0, -1.15], [1.7, 6.6, 1.15], 'glass')
        m.box([-1.45, 6.6, -0.95], [1.45, 7.2, 0.95], 'glass')
        m.rod(7.2, 9.4, 0.5, 'glass')
        m.rod(9.4, 10.4, 0.56, 'cap')
        m.box([-1.45, 1.3, 1.15], [1.45, 4.9, 1.2], 'label')
    elif kind == 'flask_bottle':         # a flat half-pint
        m.box([-1.6, 0, -0.7], [1.6, 5.2, 0.7], 'glass')
        m.box([-1.3, 1.0, 0.7], [1.3, 3.8, 0.74], 'label')
        m.rod(5.2, 6.4, 0.42, 'glass')
        m.rod(6.4, 7.0, 0.48, 'cap')
    elif kind == 'pet':                  # a plastic water or soda bottle
        m.rod(0.0, 6.4, 1.3, 'glass')
        m.rod(2.0, 4.4, 1.33, 'label')
        m.rod(6.4, 7.4, 0.9, 'glass')
        m.rod(7.4, 8.0, 0.5, 'glass')
        m.rod(8.0, 8.5, 0.55, 'cap')
    elif kind == 'two_litre':
        m.rod(0.0, 9.0, 2.1, 'glass')
        m.rod(3.0, 6.5, 2.13, 'label')
        m.rod(9.0, 10.6, 1.3, 'glass')
        m.rod(10.6, 11.4, 0.6, 'glass')
        m.rod(11.4, 12.0, 0.65, 'cap')
    elif kind == 'champagne':
        m.rod(0.0, 8.0, 1.75, 'glass')
        m.rod(2.0, 5.2, 1.78, 'label')
        m.rod(8.0, 9.4, 1.1, 'glass')
        m.rod(9.4, 12.4, 0.62, 'glass')
        m.rod(10.8, 13.0, 0.68, 'cap')


def jug(m, w=6.0, h=10.0, d=6.0, handle=True):
    # a gallon jug (water, bleach, detergent): squared body, shoulder, cap, a handle moulded into one corner
    m.box([-w / 2, 0, -d / 2], [w / 2, h * 0.82, d / 2], 'body')
    m.box([-w / 2 + 0.3, h * 0.25, d / 2], [w / 2 - 0.3, h * 0.65, d / 2 + 0.04], 'label')
    m.box([-w / 2 + 0.6, h * 0.82, -d / 2 + 0.6], [w / 2 - 0.6, h * 0.9, d / 2 - 0.6], 'body')
    m.rod(h * 0.9, h, 0.75, 'cap', cx=-w * 0.2, cz=0)
    if handle:
        m.box([w / 2 - 1.2, h * 0.9, -0.4], [w / 2 - 0.4, h * 0.97, 0.4], 'body')
        m.box([w / 2 - 0.4, h * 0.6, -0.4], [w / 2, h * 0.97, 0.4], 'body')


def squeeze(m, w=2.6, h=7.5, d=1.4):
    # a squeeze bottle (dish soap, shampoo): flattened oval body, flip cap
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'body')
    m.box([-w / 2 + 0.2, h * 0.2, d / 2], [w / 2 - 0.2, h * 0.75, d / 2 + 0.04], 'label')
    m.rod(h, h + 0.9, 0.5, 'cap')


def spray(m, d=2.2, h=6.5):
    r = d / 2
    m.rod(0.0, h, r, 'label')
    m.rod(h, h + 0.4, r * 0.75, 'alu')
    m.rod(h + 0.4, h + 1.4, r * 0.95, 'cap')


def pill_bottle(m, d=1.5, h=2.6):
    r = d / 2
    m.rod(0.0, h, r, 'glass')
    m.rod(h * 0.2, h * 0.8, r + 0.03, 'label')
    m.rod(h, h + 0.6, r + 0.1, 'cap')


def tube(m, length=6.0, w=1.6):
    # a squeeze tube lying on its side: round cap end, flattened crimped tail
    m.rod(-length / 2, length / 2 - 1.0, w / 2, 'label', axis=0)
    m.box([length / 2 - 1.0, 0.0 + w / 2 - 0.15, -w * 0.6], [length / 2, w / 2 + 0.15, w * 0.6], 'label')
    m.rod(-length / 2 - 0.7, -length / 2, w * 0.32, 'cap', axis=0)
    shift_up(m, w / 2)


def shift_up(m, dy):
    for p in m.parts:
        p['lo'] = p['lo'] + np.array([0, dy, 0])
        p['hi'] = p['hi'] + np.array([0, dy, 0])
        if p['rot']:
            p['rot'] = dict(p['rot'], origin=list(np.array(p['rot']['origin']) + np.array([0, dy, 0])))


# ================================================================== boxes, bags, cartons


def box(m, w=7.5, h=11.0, d=2.5, flap=False):
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'label')
    if flap:
        m.box([0.2 - w / 2 * 0, h, -d / 2], [w / 2, h + 0.5, d / 2], 'label',
              rot=dict(axis='z', angle=-22.5, origin=[0.2, h, 0]))


def flat_box(m, w=10.0, h=1.8, d=10.0):
    # a pizza box or a TV dinner: lying flat, the print on its lid
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'side')
    m.box([-w / 2 + 0.2, h, -d / 2 + 0.2], [w / 2 - 0.2, h + 0.04, d / 2 - 0.2], 'label')


def bag(m, w=6.0, h=8.5, d=2.4):
    # a pillow bag: the puffed body and the flat crimped seals top and bottom
    m.box([-w / 2, 0.5, -d / 2], [w / 2, h - 0.6, d / 2], 'label')
    m.box([-w / 2 + 0.2, 0.0, -0.18], [w / 2 - 0.2, 0.5, 0.18], 'seal')
    m.box([-w / 2 + 0.2, h - 0.6, -0.18], [w / 2 - 0.2, h, 0.18], 'seal')


def sack(m, w=5.5, h=9.0, d=3.5):
    # a paper sack (flour, sugar, rice): squared body, top folded over and taped
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'label')
    m.box([-w / 2 + 0.1, h, -d / 2 + 0.3], [w / 2 - 0.1, h + 0.5, d / 2 - 0.3], 'seal')


def carton(m, w=3.8, h=7.6, d=3.8):
    # a gable-top carton: the box, the sloped roof, the sealed fin on top
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'label')
    m.box([-w / 2, h, -d * 0.36], [w / 2, h + d * 0.22, d * 0.36], 'label')
    m.box([-w / 2, h + d * 0.22, -0.12], [w / 2, h + d * 0.22 + 0.6, 0.12], 'seal')


def juice_box(m, w=2.4, h=4.0, d=1.5):
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'label')
    m.seg((0.5, h), (0.9, h + 1.4), 0.18, 0.18, 'straw', z=0.2)


def bar(m, w=5.5, h=0.5, d=2.0):
    # a wrapped bar lying flat, the crimped ends a little thinner
    m.box([-w / 2 + 0.3, 0, -d / 2], [w / 2 - 0.3, h, d / 2], 'label')
    for s in (-1, 1):
        x0, x1 = sorted((s * (w / 2 - 0.3), s * w / 2))
        m.box([x0, h * 0.2, -d / 2 + 0.1], [x1, h * 0.8, d / 2 - 0.1], 'seal')


def pouch(m, w=7.0, h=10.0, d=1.2):
    # a flat retort pouch or ration bag standing up, its top seal and tear notch
    m.box([-w / 2, 0, -d / 2], [w / 2, h - 0.6, d / 2], 'label')
    m.box([-w / 2, h - 0.6, -0.15], [w / 2, h, 0.15], 'seal')


def tray(m, w=7.5, d=5.5, h=1.2, food_h=0.7):
    # a foam tray under cling film with the food humped up inside and a price sticker
    m.box([-w / 2, 0, -d / 2], [w / 2, h * 0.6, d / 2], 'tray')
    m.box([-w / 2 + 0.4, h * 0.6, -d / 2 + 0.4], [w / 2 - 0.4, h * 0.6 + food_h, d / 2 - 0.4], 'food')
    m.box([w / 2 - 2.3, h * 0.6 + food_h, d / 2 - 1.8], [w / 2 - 0.5, h * 0.6 + food_h + 0.04, d / 2 - 0.5], 'label')


def roll(m, d=4.2, h=4.2, core=1.6, axis_up=True):
    m.rod(0, h, d / 2, 'label')
    m.rod(h, h + 0.02, core / 2, 'core')


def tape_roll(m, d=3.0, w=1.0):
    m.rod(0, w, d / 2, 'label')
    m.rod(w, w + 0.02, d / 2 * 0.62, 'core')


def kit_case(m, w=10.0, h=7.0, d=3.0, handle=True):
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d * 0.3], 'body')
    m.box([-w / 2, 0, d * 0.3], [w / 2, h, d / 2], 'lid')
    m.box([-w * 0.16, h * 0.41, d / 2], [w * 0.16, h * 0.59, d / 2 + 0.06], 'mark')
    m.box([-w * 0.06, h * 0.27, d / 2], [w * 0.06, h * 0.73, d / 2 + 0.06], 'mark')
    for x in (-w * 0.36, w * 0.36):
        m.box([x - 0.5, h * 0.88, d * 0.2], [x + 0.5, h * 0.97, d / 2 + 0.25], 'metal')
    if handle:
        m.box([-1.6, h, -0.25], [-1.3, h + 0.7, 0.25], 'metal')
        m.box([1.3, h, -0.25], [1.6, h + 0.7, 0.25], 'metal')
        m.box([-1.6, h + 0.6, -0.3], [1.6, h + 0.9, 0.3], 'handle')


def soft_bag(m, w=8.0, h=5.0, d=3.5, zip_colour=True, straps=False):
    m.box([-w / 2, 0, -d / 2], [w / 2, h, d / 2], 'body')
    m.box([-w / 2 + 0.2, h - 0.05, -0.15], [w / 2 - 0.2, h + 0.08, 0.15], 'zip')
    m.box([w / 2 - 1.2, h - 0.6, 0.15], [w / 2 - 0.9, h + 0.08, 0.35], 'zip')
    m.box([-w * 0.25, h * 0.35, d / 2], [w * 0.25, h * 0.7, d / 2 + 0.05], 'mark')
    if straps:
        for x in (-w * 0.3, w * 0.3):
            m.box([x - 0.5, h, -0.2], [x + 0.5, h + 1.8, 0.2], 'strap')
        m.box([-w * 0.3 - 0.5, h + 1.6, -0.2], [w * 0.3 + 0.5, h + 1.9, 0.2], 'strap')


# ================================================================== produce


def ball(m, r, mat, squash=1.0, cy=None):
    cy = r * squash if cy is None else cy
    h = r * squash
    tag = (round(cy, 4), r, squash)     # tells mesh.py these two rods are one ball
    m.rod(cy - h, cy + h, r * 0.82, mat, ball=tag)
    m.rod(cy - h * 0.8, cy + h * 0.8, r, mat, ball=tag)


def apple(m, r=1.5):
    ball(m, r, 'skin', 0.9)
    m.box([-0.08, r * 1.8, -0.08], [0.08, r * 1.8 + 0.6, 0.08], 'stem')
    m.box([0.08, r * 1.8 + 0.3, -0.1], [0.7, r * 1.8 + 0.45, 0.25], 'leaf')


def citrus(m, r=1.5):
    ball(m, r, 'skin', 0.95)
    m.box([-0.15, r * 1.9 - 0.05, -0.15], [0.15, r * 1.9 + 0.12, 0.15], 'stem')


def tomato(m, r=1.3):
    ball(m, r, 'skin', 0.75)
    for a in range(0, 180, 45):
        x, z = math.cos(math.radians(a)), math.sin(math.radians(a))
        m.box([-0.5 * abs(x) - 0.08, r * 1.5 - 0.02, -0.5 * abs(z) - 0.08], [0.5 * abs(x) + 0.08, r * 1.5 + 0.08, 0.5 * abs(z) + 0.08], 'leaf')


def onion(m, r=1.4):
    ball(m, r, 'skin', 0.85)
    m.rod(r * 1.7, r * 1.7 + 0.8, 0.25, 'skin')
    m.rod(-0.3, 0.0, 0.3, 'stem')


def potato(m):
    m.box([-1.8, 0, -1.1], [1.8, 1.6, 1.1], 'skin')
    m.box([-1.4, 0.2, -1.3], [1.2, 1.4, 1.3], 'skin')
    m.box([-2.0, 0.3, -0.8], [1.9, 1.3, 0.8], 'skin')
    m.box([0.4, 1.5, -0.5], [1.4, 1.75, 0.4], 'skin')


def banana(m):
    pts = [(-3.5, 1.6), (-2.0, 0.6), (0.0, 0.2), (2.0, 0.6), (3.4, 1.7)]
    for a, b in zip(pts, pts[1:]):
        m.seg(a, b, 1.1, 1.1, 'skin')
    m.seg((3.4, 1.7), (3.9, 2.5), 0.4, 0.4, 'stem')
    m.box([-3.9, 1.5, -0.25], [-3.4, 2.0, 0.25], 'stem')


def carrot(m):
    m.seg((-3.2, 0.5), (0.0, 0.6), 1.1, 1.1, 'skin')
    m.seg((0.0, 0.6), (2.4, 0.5), 0.75, 0.75, 'skin')
    m.seg((2.4, 0.5), (3.6, 0.45), 0.35, 0.35, 'skin')
    for dz, dy in ((-0.3, 1.3), (0.0, 1.8), (0.3, 1.2)):
        m.seg((-3.2, 0.6), (-4.6, 0.6 + dy), 0.2, 0.2, 'leaf', z=dz)


def head_veg(m, r=2.6):
    ball(m, r, 'skin', 0.85)
    for a in range(0, 360, 90):
        x, z = math.cos(math.radians(a)) * r * 0.75, math.sin(math.radians(a)) * r * 0.75
        m.box([x - 0.9, r * 0.2, z - 0.9], [x + 0.9, r * 1.4, z + 0.9], 'leaf')


# ================================================================== small things


def stick(m, length, w, mat, y=None):
    y = w / 2 if y is None else y
    m.box([-length / 2, y - w / 2, -w / 2], [length / 2, y + w / 2, w / 2], mat)
