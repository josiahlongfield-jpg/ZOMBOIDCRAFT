"""Paints the lifts' and escalators' textures and writes their block models and blockstates.

python3 tools/lifts/paint.py   (writes into src/main/resources/assets/zomboidcraft/)

- entity/lift/atlas.png: the car, 8 x 8 tiles of 64 px; LiftRenderer.java names each tile by
  index (row-major); keep the two in step.
- block/lift_door_{left,right}_{lower,upper}: a 2 x 2 block picture of worn stainless landing doors
  (seen from the landing), lift_door_edge for the leaf's edges and back.
- block/lift_button(_lit): the call plate; block/lift_machine(_top): the winding machine.
- block/escalator_{tread,riser}_{up,down}: the city's escalator step textures, animated so the
  treads slide the way the steps run (16 frames, a tick each).

Same worn-down palette as the rest of the world: grime low down, scuffs, dents, rust bleeding from seams.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'zomboidcraft')
TEX = os.path.join(ASSETS, 'textures')
NS = 'zomboidcraft'


def save(img, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def blur(a, k=1):
    out = a.copy()
    for _ in range(k):
        out = (out + np.roll(out, 1, 0) + np.roll(out, -1, 0) + np.roll(out, 1, 1) + np.roll(out, -1, 1)) / 5
    return out


def fbm(h, w, seed, octaves=4):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp = 1.0
    for o in range(octaves):
        s = 2 ** (o + 1)
        g = r.random((s + 1, s + 1))
        ys = np.linspace(0, s, h, endpoint=False)
        xs = np.linspace(0, s, w, endpoint=False)
        y0, x0 = ys.astype(int), xs.astype(int)
        fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
        a = g[y0][:, x0] * (1 - fx) + g[y0][:, x0 + 1] * fx
        b = g[y0 + 1][:, x0] * (1 - fx) + g[y0 + 1][:, x0 + 1] * fx
        out += (a * (1 - fy) + b * fy) * amp
        amp /= 2
    return (out - out.min()) / (out.max() - out.min() + 1e-9)


def to_img(rgb):
    a = np.clip(rgb, 0, 255).astype(np.uint8)
    alpha = np.full(a.shape[:2] + (1,), 255, np.uint8)
    return Image.fromarray(np.concatenate([a, alpha], 2), 'RGBA')


def brushed(h, w, seed, base=(150, 153, 156), vertical=True, grain=26):
    """Brushed stainless: fine streaks along the brushing, a broad sheen, dull patches."""
    r = np.random.default_rng(seed)
    n = r.random(w if vertical else h)
    n = blur(n[None, :] if vertical else n[:, None], 0)
    streak = np.repeat(n, h, 0) if vertical else np.repeat(n, w, 1)
    streak += r.random((h, w)) * 0.25
    sheen = fbm(h, w, seed + 1, 3)
    v = (streak - 0.5) * grain + (sheen - 0.5) * 30
    return np.stack([np.full((h, w), c, float) + v for c in base], 2)


def grime(rgb, seed, low=0.35, strength=70, tint=(58, 50, 40)):
    """Dirt rising from the floor and in drifts, plus speckles."""
    h, w, _ = rgb.shape
    ys = np.linspace(1, 0, h)[:, None]
    rise = np.clip((ys - (1 - low)) / low, 0, 1) ** 1.5
    drift = fbm(h, w, seed, 4)
    amt = np.clip(rise * 0.8 + (drift - 0.62) * 1.4, 0, 1) * strength / 100
    r = np.random.default_rng(seed + 9)
    amt += (r.random((h, w)) < 0.025) * 0.35
    t = np.array(tint, float)[None, None, :]
    return rgb * (1 - amt[..., None]) + t * amt[..., None]


def scratches(rgb, seed, n=10, light=40):
    r = random.Random(seed)
    h, w, _ = rgb.shape
    for _ in range(n):
        x, y = r.uniform(0, w), r.uniform(0, h)
        ang, ln = r.uniform(0, math.pi), r.uniform(3, w * 0.4)
        for t in np.linspace(0, ln, int(ln * 2)):
            xi, yi = int(x + math.cos(ang) * t), int(y + math.sin(ang) * t)
            if 0 <= xi < w and 0 <= yi < h:
                rgb[yi, xi] += light * r.choice((1, -0.6))
    return rgb


def dent(rgb, cx, cy, rad, depth=40):
    h, w, _ = rgb.shape
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx - cx, yy - cy) / rad
    m = np.clip(1 - d, 0, 1)
    # a lit rim on top, dark below
    shade = m * (np.sign(yy - cy) * depth)
    rgb -= shade[..., None]
    return rgb


def rust_streak(rgb, x, y0, seed, length=20):
    r = np.random.default_rng(seed)
    h, w, _ = rgb.shape
    for i in range(length):
        yy = y0 + i
        if yy >= h:
            break
        a = (1 - i / length) * 0.55
        for dx in (-1, 0, 1):
            xx = x + dx + int(r.integers(-1, 2) * (i % 5 == 0))
            if 0 <= xx < w:
                rgb[yy, xx] = rgb[yy, xx] * (1 - a) + np.array([110, 62, 30]) * a
    return rgb


# ------------------------------------------------------------------ landing doors

def doors():
    S = 64
    rgb = brushed(2 * S, 2 * S, 101, (148, 151, 154), vertical=True)
    # a vertical sheen band down each leaf
    xx = np.arange(2 * S)
    band = np.exp(-((xx % S - S * 0.35) / (S * 0.18)) ** 2) * 18
    rgb += band[None, :, None]
    rgb = scratches(rgb, 7, 14, 14)
    rgb = dent(rgb, 40, 104, 7, 26)       # kicked at shin height
    rgb = dent(rgb, 93, 116, 5, 22)
    rgb = grime(rgb, 33, low=0.28, strength=95)
    # the meeting seam: a dark gap, the leaves' rubber edges either side
    for x in (S - 2, S - 1, S, S + 1):
        rgb[:, x] = rgb[:, x] * 0.25 + np.array([18, 18, 20]) * 0.75
    rgb[:, S - 3] *= 0.8
    rgb[:, S + 2] *= 0.8
    # rust bleeding from the seam and the sill
    rgb = rust_streak(rgb, S - 3, 70, 3, 26)
    rgb = rust_streak(rgb, S + 2, 30, 4, 18)
    # handprints and smears
    r = np.random.default_rng(12)
    for cx, cy in ((S - 14, 70), (S + 12, 66), (S + 16, 78)):
        yy, xx = np.mgrid[0:2 * S, 0:2 * S]
        m = np.clip(1 - np.hypot((xx - cx) / 6, (yy - cy) / 8), 0, 1) * 0.35
        rgb = rgb * (1 - m[..., None]) + np.array([70, 58, 46]) * m[..., None]
    # a faded inspection sticker on the right leaf at eye height
    rgb[44:54, S + 34:S + 46] = rgb[44:54, S + 34:S + 46] * 0.4 + np.array([200, 190, 150]) * 0.6
    rgb[46:47, S + 36:S + 44] = [60, 60, 70]
    rgb[49:50, S + 36:S + 42] = [60, 60, 70]
    rgb[51:52, S + 36:S + 43] = [60, 60, 70]
    # the frame's shadow at the top
    rgb[:3] *= 0.7
    img = to_img(rgb)
    # the picture as you face it: left leaf on the left
    save(img.crop((0, S, S, 2 * S)), 'block', 'lift_door_left_lower.png')
    save(img.crop((S, S, 2 * S, 2 * S)), 'block', 'lift_door_right_lower.png')
    save(img.crop((0, 0, S, S)), 'block', 'lift_door_left_upper.png')
    save(img.crop((S, 0, 2 * S, S)), 'block', 'lift_door_right_upper.png')
    edge = brushed(S, S, 105, (110, 112, 115), vertical=True)
    edge = grime(edge, 34, low=0.5, strength=60)
    save(to_img(edge), 'block', 'lift_door_edge.png')


# ------------------------------------------------------------------ call button and machine

def button():
    """The call plate, as in the photos (refs/city/lifts/call-buttons-indicators): a tall brushed
    steel plate with two round black buttons, up over down, their arrows white (lit: the rings and
    arrows glow). 64 px; the plate's front reads the texture's window under its model box (cols
    22..42, rows 12..64, 4 px a sixteenth), the buttons' faces the 16 px squares at cols 44..60, rows 0..32."""
    S = 64
    yy, xx = np.mgrid[0:S, 0:S]
    for lit in (False, True):
        rgb = brushed(S, S, 201, (168, 170, 172), vertical=True, grain=18)
        rgb = scratches(rgb, 202, 8, 24)
        rgb = grime(rgb, 203, low=0.35, strength=40)
        x0, x1, y0, y1 = 22, 42, 12, 64
        # the plate's bevel and its four screws
        rgb[y0, x0:x1] += 34
        rgb[y0:y1, x0] += 22
        rgb[y1 - 1, x0:x1] -= 44
        rgb[y0:y1, x1 - 1] -= 34
        for sx, sy in ((x0 + 3, y0 + 3), (x1 - 4, y0 + 3), (x0 + 3, y1 - 4), (x1 - 4, y1 - 4)):
            rgb[sy - 1:sy + 1, sx - 1:sx + 1] = [96, 96, 98]
            rgb[sy - 1, sx - 1] = [200, 200, 204]
        # dark rings behind the two buttons (the buttons themselves are the model's nubs)
        for cy in (25, 47):
            d = np.hypot(xx - 31.5, yy - cy)
            rgb[(d < 7.5) & (d >= 5.5)] = [44, 45, 47]
            rgb[(d < 8.5) & (d >= 7.5)] += 30
        # a fire notice etched under them, small
        rgb[57:58, 26:38] = [70, 40, 36]
        rgb[59:60, 27:37] = [70, 70, 72]
        # the button faces: black, a white arrow, a ring that glows when called
        for k, up in ((0, True), (1, False)):
            ox, oy = 44, k * 16
            dd = np.hypot(xx[:16, :16] - 7.5, yy[:16, :16] - 7.5)
            f = np.ones((16, 16, 3)) * [26, 26, 28]
            f[dd < 6] = [38, 38, 40]
            ring = (dd >= 6) & (dd < 8)
            f[ring] = [255, 196, 90] if lit else [120, 122, 126]
            for r in range(5):
                row = (4 + r) if up else (11 - r)
                f[row, 8 - r - 1:8 + r] = [255, 238, 200] if lit else [222, 222, 222]
            if not lit:
                f[2:5, 3:12] += 18  # the face's sheen
            rgb[oy:oy + 16, ox:ox + 16] = f
        save(to_img(rgb), 'block', 'lift_button_lit.png' if lit else 'lift_button.png')


def machine():
    S = 64
    r = np.random.default_rng(301)
    base = np.array([74, 92, 80], float)
    rgb = np.ones((S, S, 3)) * base + (fbm(S, S, 302) - 0.5)[..., None] * 30
    # chipped paint to bare steel and oil
    chips = fbm(S, S, 303, 5) > 0.8
    rgb[chips] = [120, 118, 112]
    oil = fbm(S, S, 304, 4) > 0.78
    rgb[oil] *= 0.6
    # panel seams and bolts
    for k in (0, 31, 63):
        rgb[k, :] *= 0.5
        rgb[:, k] *= 0.5
    for x in (4, 27, 36, 59):
        for y in (4, 27, 36, 59):
            rgb[y - 1:y + 2, x - 1:x + 2] = [150, 150, 140]
            rgb[y + 1, x - 1:x + 2] = [40, 40, 36]
    # the rating plate
    rgb[40:50, 10:26] = [170, 160, 120]
    rgb[42, 12:24] = [50, 50, 50]
    rgb[45, 12:22] = [50, 50, 50]
    save(to_img(grime(rgb, 305, low=0.3, strength=50)), 'block', 'lift_machine.png')
    top = np.ones((S, S, 3)) * [90, 92, 94] + (fbm(S, S, 306) - 0.5)[..., None] * 24
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot(xx - S / 2, yy - S / 2)
    sheave = d < 26
    top[sheave] = [120, 122, 124]
    grooves = sheave & ((d.astype(int) % 4) == 0)
    top[grooves] = [50, 50, 52]
    top[d < 6] = [70, 70, 72]
    save(to_img(top), 'block', 'lift_machine_top.png')


# ------------------------------------------------------------------ escalator treads

def escalators():
    city = os.path.join(TEX, 'block', 'city')
    for name in ('tread', 'riser'):
        src = np.array(Image.open(os.path.join(city, f'tx_esc_{name}.png')).convert('RGBA'))
        S = src.shape[0]
        frames = 16
        step = S // frames
        for run, sign in (('up', -1), ('down', 1)):
            # up the steps is toward the model's north, where the texture's v is low: the picture slides that way
            strip = np.concatenate([np.roll(src, sign * step * k, 0) for k in range(frames)], 0)
            save(Image.fromarray(strip, 'RGBA'), 'block', f'escalator_{name}_{run}.png')
            with open(os.path.join(TEX, 'block', f'escalator_{name}_{run}.png.mcmeta'), 'w') as f:
                json.dump({'animation': {'frametime': 1}}, f, indent=2)
                f.write('\n')


# ------------------------------------------------------------------ the car's atlas

T = 64


def car_atlas():
    """The car, from the photos (refs/city/lifts/car-interior, car-panel): brushed stainless side
    panels with seams, a walnut laminate back wall with a steel strip, tube handrails, a dark
    ceiling with a diffuser, a stud rubber floor, and the car's own operating panel: round chrome
    buttons on black number labels, door open/close and a yellow alarm button. Tiles are 64 px; the
    renderer lays the walls, floor and doors a tile to about a block, so they stay sharp."""
    atlas = np.zeros((T * 8, T * 8, 3))
    yy, xx = np.mgrid[0:T, 0:T]

    def put(i, rgb):
        atlas[(i // 8) * T:(i // 8 + 1) * T, (i % 8) * T:(i % 8 + 1) * T] = rgb

    # 0 wall: a brushed stainless panel, seams at its edges, a sheen band, finger smears at rail height
    w = brushed(T, T, 401, (150, 153, 157), vertical=True, grain=22)
    w += (np.exp(-((xx - T * 0.3) / (T * 0.16)) ** 2) * 16)[..., None]
    w[:, :2] = w[:, :2] * 0.45
    w[:, -2:] = w[:, -2:] * 0.7
    w = scratches(w, 402, 9, 16)
    w = dent(w, 40, 50, 5, 16)
    put(0, grime(w, 403, low=0.25, strength=45))
    # 1 walnut laminate with a steel strip down one edge
    g = np.sin(xx * 0.55 + fbm(T, T, 404) * 12 + yy * 0.04) * 0.5 + 0.5
    wood = np.stack([88 + g * 34, 54 + g * 20, 32 + g * 12], 2) + (fbm(T, T, 405) - 0.5)[..., None] * 18
    wood[:, -3:] = [150, 152, 155]
    wood[:, -4] = [60, 50, 40]
    wood = scratches(wood, 424, 6, 18)
    put(1, grime(wood, 406, low=0.2, strength=35))
    # 2 stud rubber floor, worn grey where people stand, grit
    fl = np.ones((T, T, 3)) * [44, 44, 46] + (fbm(T, T, 407) - 0.5)[..., None] * 16
    studs = ((xx % 10 - 5) ** 2 + (yy % 10 - 5) ** 2) < 7
    fl[studs] += 18
    fl[studs & ((xx % 10 - 5) + (yy % 10 - 5) < -1)] += 10
    put(2, grime(fl, 408, low=1.0, strength=38, tint=(74, 64, 50)))
    # 3 ceiling: dark brushed with a row of small downlight rings
    ce = brushed(T, T, 409, (64, 64, 66), vertical=False, grain=10)
    for cx in (16, 48):
        d = np.hypot(xx - cx, yy - 32)
        ce[(d < 5) & (d >= 3.5)] = [140, 140, 142]
        ce[d < 3.5] = [210, 200, 180]
    put(3, ce)
    # 4 the light panel: a warm diffuser behind an egg-crate grille, one tube dimmer
    li = np.ones((T, T, 3)) * [255, 244, 215]
    li[(yy % 8) == 0] = [190, 182, 160]
    li[(xx % 8) == 0] = [190, 182, 160]
    li[:, 40:56] *= 0.8
    li[:2] = li[-2:] = li[:, :2] = li[:, -2:] = [150, 150, 150]
    put(4, li)
    # 5 door leaf (the car's and the landing's while they slide): brushed stainless, vertical
    d = brushed(T, T, 410, (152, 155, 158), vertical=True, grain=24)
    d += (np.exp(-((xx - T * 0.35) / (T * 0.18)) ** 2) * 18)[..., None]
    d = scratches(d, 411, 8, 14)
    put(5, grime(d, 412, low=0.25, strength=60))
    # 6 car roof: greasy galvanised steel, a bolted seam
    ro = np.ones((T, T, 3)) * [96, 98, 96] + (fbm(T, T, 413, 5) - 0.5)[..., None] * 40
    ro[fbm(T, T, 414) > 0.6] *= 0.55
    ro[31:33] *= 0.5
    ro[29, ::8] = [150, 150, 140]
    put(6, ro)
    # 7 rail: polished steel tube
    ra = np.ones((T, T, 3)) * [170, 172, 175]
    ra += np.sin(np.linspace(0, math.pi, T))[:, None, None] * 55 - 28
    ra = scratches(ra, 425, 4, 20)
    put(7, ra)
    # 8 the operating panel: brushed stainless strip, a darker inset edge, Braille-ish dots and the capacity plate
    pa = brushed(T, T, 415, (168, 170, 174), vertical=True, grain=16)
    pa[:3] *= 0.6
    pa[-3:] *= 0.6
    pa[:, :3] *= 0.6
    pa[:, -3:] *= 0.6
    pa[6:12, 10:54] = [190, 186, 170]
    pa[8, 14:50] = [70, 70, 72]
    pa[10, 14:40] = [70, 70, 72]
    put(8, grime(scratches(pa, 426, 10, 20), 427, low=0.3, strength=30))
    # 9 / 10 floor buttons, off and lit: domed chrome, a halo ring
    dd = np.hypot(xx - 31.5, yy - 31.5)
    b = np.ones((T, T, 3)) * [150, 152, 156]
    b[dd < 30] = [96, 98, 102]
    b[dd < 25] = [190, 192, 196]
    b[(dd < 25) & (yy < 28)] += 30
    b[dd < 8] = [150, 152, 156]
    put(9, b)
    b2 = b.copy()
    b2[(dd < 30) & (dd >= 25)] = [255, 180, 70]
    b2[dd < 25] = b2[dd < 25] * 0.6 + np.array([255, 200, 110]) * 0.4
    put(10, b2)
    # 11 / 12 display segments, lit amber and dark
    put(11, np.ones((T, T, 3)) * [255, 128, 40])
    put(12, np.ones((T, T, 3)) * [40, 16, 12])
    # 13 display glass: black with a faint reflection
    gl = np.ones((T, T, 3)) * [10, 10, 12]
    gl[(xx + yy) % 46 < 3] += 14
    put(13, gl)
    # 14 the ropes: oily dark steel, laid strands
    put(14, np.ones((T, T, 3)) * [42, 40, 36] + ((yy + xx) % 8 < 2)[..., None] * 25)
    # 15 crosshead: yellow-painted steel, chipped
    be = np.ones((T, T, 3)) * [190, 150, 40] + (fbm(T, T, 416) - 0.5)[..., None] * 30
    be[fbm(T, T, 417, 5) > 0.68] = [90, 88, 84]
    put(15, grime(be, 418, low=0.5, strength=45))
    # 16 kick plate: diamond-plate steel, scuffed
    k = np.ones((T, T, 3)) * [112, 114, 117] + (fbm(T, T, 419) - 0.5)[..., None] * 20
    dia = (((xx + yy) % 12 < 2) & ((xx // 6 + yy // 6) % 2 == 0)) | (((xx - yy) % 12 < 2) & ((xx // 6 + yy // 6) % 2 == 1))
    k[dia] += 40
    put(16, grime(scratches(k, 420, 14, 40), 421, low=1.0, strength=40))
    # 17 sill: grooved aluminium
    si = np.ones((T, T, 3)) * [150, 150, 145]
    si[(yy % 6) < 2] = [70, 70, 68]
    put(17, grime(si, 422, low=1.0, strength=35))
    # 18 display frame and landing door frame: brushed stainless
    put(18, grime(brushed(T, T, 423, (172, 175, 179), vertical=True, grain=18), 428, low=0.3, strength=35))
    # 19 number label: black
    put(19, np.ones((T, T, 3)) * [20, 20, 22] + (fbm(T, T, 429) - 0.5)[..., None] * 8)
    # 20 label numerals: white
    put(20, np.ones((T, T, 3)) * [235, 235, 230])
    # 21 / 22 door open (arrows out) and door close (arrows in) buttons
    for i, out in ((21, True), (22, False)):
        bb = b.copy()
        for r in range(8):
            for side in (-1, 1):
                tip = 31.5 + side * (18 if out else 4)
                x = int(tip - side * r * (1 if out else -1))
                bb[31 - r // 1:33 + r // 1, x:x + 1] = [30, 30, 32]
        bb[31:33, 22:42] = [30, 30, 32] if not out else bb[31:33, 22:42]
        put(i, bb)
    # 23 alarm: a yellow button with a bell
    al = b.copy()
    al[dd < 25] = [220, 180, 40]
    al[(dd < 25) & (yy < 28)] += 20
    bell = (np.hypot(xx - 31.5, (yy - 34) * 1.3) < 11) & (yy < 38) & (yy > 20)
    al[bell] = [40, 34, 20]
    al[40:43, 22:42] = [40, 34, 20]
    put(23, al)
    save(to_img(atlas), 'entity', 'lift', 'atlas.png')


# ------------------------------------------------------------------ models and blockstates

def write(rel, data):
    p = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def face(tex, uv=None, cull=None):
    f = {'texture': tex}
    if uv:
        f['uv'] = uv
    if cull:
        f['cullface'] = cull
    return f


def models():
    # landing doors: a leaf a block wide and high, 2 px thick, 1 px in from the landing face
    for side in ('left', 'right'):
        for part in ('lower', 'upper'):
            name = f'lift_door_{side}_{part}'
            write(f'models/block/{name}.json', {
                'parent': 'minecraft:block/block',
                'textures': {'particle': f'{NS}:block/{name}', 'front': f'{NS}:block/{name}', 'edge': f'{NS}:block/lift_door_edge'},
                'elements': [{'from': [0, 0, 1], 'to': [16, 16, 3], 'faces': {
                    'north': face('#front'), 'south': face('#edge'), 'east': face('#edge', [0, 0, 2, 16]),
                    'west': face('#edge', [0, 0, 2, 16]), 'up': face('#edge', [0, 0, 16, 2]), 'down': face('#edge', [0, 0, 16, 2])}}]})
    write('models/block/lift_door_open.json', {'textures': {'particle': f'{NS}:block/lift_door_edge'}, 'elements': []})
    variants = {}
    for facing, rot in ROT.items():
        for side in ('left', 'right'):
            for part in ('lower', 'upper'):
                for op in ('false', 'true'):
                    m = f'{NS}:block/lift_door_open' if op == 'true' else f'{NS}:block/lift_door_{side}_{part}'
                    v = {'model': m}
                    if rot:
                        v['y'] = rot
                    variants[f'facing={facing},open={op},part={part},side={side}'] = v
    write('blockstates/lift_door.json', {'variants': variants})
    # the call plate (a sixteenth proud of the wall), its two buttons proud of it (lit: they glow)
    for lit in (False, True):
        name = 'lift_button_lit' if lit else 'lift_button'
        nubs = []
        for k, (y0, y1) in enumerate(((8.4, 10.6), (2.9, 5.1))):
            nub = {'from': [6.9, y0, 14.4], 'to': [9.1, y1, 15], 'faces': {
                'north': face('#plate', [11, k * 4, 15, k * 4 + 4]), 'east': face('#plate', [11, k * 4, 11.25, k * 4 + 4]),
                'west': face('#plate', [11, k * 4, 11.25, k * 4 + 4]), 'up': face('#plate', [11, k * 4, 15, k * 4 + 0.25]),
                'down': face('#plate', [11, k * 4 + 3.75, 15, k * 4 + 4])}}
            if lit:
                nub['light_emission'] = 15
            nubs.append(nub)
        write(f'models/block/{name}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'particle': f'{NS}:block/{name}', 'plate': f'{NS}:block/{name}'},
            'elements': [
                {'from': [5.5, 0, 15], 'to': [10.5, 13, 16], 'faces': {
                    'north': face('#plate'), 'south': face('#plate', [5.5, 3, 10.5, 16], cull='south'),
                    'east': face('#plate', [5.5, 3, 6.5, 16]), 'west': face('#plate', [9.5, 3, 10.5, 16]),
                    'up': face('#plate', [5.5, 3, 10.5, 4]), 'down': face('#plate', [5.5, 15, 10.5, 16])}},
                *nubs]})
    variants = {}
    for facing, rot in ROT.items():
        for lit in ('false', 'true'):
            v = {'model': f'{NS}:block/lift_button' + ('_lit' if lit == 'true' else '')}
            if rot:
                v['y'] = rot
            variants[f'facing={facing},lit={lit}'] = v
    write('blockstates/lift_button.json', {'variants': variants})
    # the machine
    write('models/block/lift_machine.json', {
        'parent': 'minecraft:block/cube',
        'textures': {'particle': f'{NS}:block/lift_machine', 'north': f'{NS}:block/lift_machine', 'south': f'{NS}:block/lift_machine',
                     'east': f'{NS}:block/lift_machine', 'west': f'{NS}:block/lift_machine', 'up': f'{NS}:block/lift_machine_top',
                     'down': f'{NS}:block/lift_machine'}})
    variants = {}
    for facing, rot in ROT.items():
        v = {'model': f'{NS}:block/lift_machine'}
        if rot:
            v['y'] = rot
        variants[f'facing={facing}'] = v
    write('blockstates/lift_machine.json', {'variants': variants})
    # escalator steps, as the city's stopped ones but running
    for run in ('up', 'down'):
        t = {'particle': f'{NS}:block/city/tx_esc_riser', 'tread': f'{NS}:block/escalator_tread_{run}',
             'riser': f'{NS}:block/escalator_riser_{run}', 'side': f'{NS}:block/city/tx_esc_riser'}
        els = []
        for box in ([0, 0, 0, 16, 8, 16], [0, 8, 0, 16, 16, 8]):
            els.append({'from': box[:3], 'to': box[3:], 'faces': {
                # the steps rise toward the model's north, so the risers you see climbing face south
                'north': face('#side'), 'south': face('#riser'), 'east': face('#side'), 'west': face('#side'),
                'up': face('#tread'), 'down': face('#side')}})
        write(f'models/block/escalator_{run}.json', {'parent': 'minecraft:block/block', 'textures': t, 'elements': els})
    variants = {}
    for facing, rot in ROT.items():
        for run in ('up', 'down'):
            v = {'model': f'{NS}:block/escalator_{run}'}
            if rot:
                v['y'] = rot
            variants[f'facing={facing},run={run}'] = v
    write('blockstates/escalator.json', {'variants': variants})


if __name__ == '__main__':
    doors()
    button()
    machine()
    escalators()
    car_atlas()
    models()
    print('painted lifts and escalators')
