"""Draws the inventory screen's GUI sprites: worn canvas, steel, wood and concrete tiles, stitched frames,
recessed slots, masking tape, webbing, and the lighting overlays. Everything is drawn at 2x and the .mcmeta
files give the logical GUI size, so the art stays sharp at high GUI scales.

    python3 tools/ui/gen_inventory_gui.py
"""
import json
import os

import numpy as np
from PIL import Image, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/zomboidcraft/textures/gui/sprites/inventory')
rng = np.random.default_rng(7)
S = 2  # pixels per GUI pixel


def blur_wrap(a, r):
    h, w = a.shape
    big = np.tile(a, (3, 3))
    im = Image.fromarray(((big - big.min()) / (np.ptp(big) + 1e-9) * 255).astype(np.uint8))
    b = np.asarray(im.filter(ImageFilter.GaussianBlur(r))).astype(float) / 255
    return b[h:2 * h, w:2 * w]


def noise(n, r):
    return blur_wrap(rng.random((n, n)), r)


def fbm(n, rs=(1, 3, 8, 24), ws=(.15, .25, .3, .3)):
    out = np.zeros((n, n))
    for r, w in zip(rs, ws):
        a = noise(n, r) - .5
        out += w * a / max(1e-6, a.std())
    return out


def rgba(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255)
    a = np.full(rgb.shape[:2] + (1,), 255.0) if alpha is None else np.clip(alpha, 0, 255)[..., None]
    return np.concatenate([rgb, a], axis=2).astype(np.uint8)


def save(name, arr, gui):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(arr, 'RGBA').save(os.path.join(OUT, name + '.png'), optimize=True)
    with open(os.path.join(OUT, name + '.png.mcmeta'), 'w') as f:
        json.dump({'gui': {'scaling': gui}}, f)
        f.write('\n')


# ---------- tileable materials (logical 64x64, so 128 px) ----------
N = 128
x = np.arange(N)


def canvas():
    weave = (np.sin(x[None, :] * np.pi / 2) * .5 + .5) * .5 + (np.sin(x[:, None] * np.pi / 2 + np.pi / 2) * .5 + .5) * .5
    c = np.array([66, 68, 44.])[None, None] * (0.84 + 0.14 * weave[..., None] + 0.08 * (rng.random((N, N, 1)) - .5) + 0.1 * fbm(N)[..., None])
    c *= 1 - 0.28 * np.clip((noise(N, 14) - .55) * 4, 0, 1)[..., None]
    c += np.array([16, 10, 0])[None, None] * np.clip((noise(N, 22) - .6) * 5, 0, 1)[..., None]
    return rgba(c * .82)


def metal():
    m = np.array([54, 58, 61.])[None, None] * np.ones((N, N, 1))
    streak = blur_wrap(np.tile(rng.random((N, 1)), (1, N)), .8)
    m *= 0.85 + 0.15 * streak[..., None] + 0.06 * (rng.random((N, N, 1)) - .5) + 0.08 * fbm(N)[..., None]
    for _ in range(14):
        y0, x0 = rng.integers(0, N, 2)
        ang, length = rng.normal(0, .35), rng.integers(8, 40)
        for t in range(length):
            yy, xx = int(y0 + np.sin(ang) * t) % N, int(x0 + np.cos(ang) * t) % N
            m[yy, xx] = m[yy, xx] * .8 + np.array([120, 122, 120]) * .2
    rust = np.clip((noise(N, 7) - .68) * 6, 0, 1) * np.clip((noise(N, 20) - .45) * 4, 0, 1)
    m = m * (1 - rust[..., None]) + np.array([96, 52, 28])[None, None] * (0.7 + 0.5 * rng.random((N, N, 1))) * rust[..., None]
    return rgba(m * .85)


def wood():
    yy = np.arange(N)[:, None] * np.ones((1, N))
    stretch = np.asarray(Image.fromarray((rng.random((N, 8)) * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC)).astype(float) / 255
    ring = np.sin(yy * 0.9 + stretch * 14) * .5 + .5
    w = np.array([74, 50, 31.])[None, None] * (0.75 + 0.25 * ring[..., None] + 0.1 * (rng.random((N, N, 1)) - .5))
    w[(np.arange(N) % 32) < 1] *= 0.45
    return rgba(w * .85)


def concrete():
    c = np.array([60, 60, 58.])[None, None] * (0.8 + 0.25 * fbm(N)[..., None] + 0.12 * (rng.random((N, N, 1)) - .5))
    c *= 1 - 0.35 * np.clip((noise(N, 18) - .5) * 3, 0, 1)[..., None]
    return rgba(c * .85)


def webbing():
    rib = (np.sin(np.arange(N)[:, None] * np.pi / 1.5) * .5 + .5) * np.ones((1, N))
    w = np.array([50, 54, 35.])[None, None] * (0.8 + 0.25 * rib[..., None] + 0.1 * (rng.random((N, N, 1)) - .5) + 0.08 * fbm(N)[..., None])
    return rgba(w)


for name, fn in [('canvas', canvas), ('metal', metal), ('wood', wood), ('concrete', concrete), ('webbing', webbing)]:
    save(name, fn(), {'type': 'tile', 'width': 64, 'height': 64})


# ---------- frames: transparent centre, drawn over a material ----------
def frame(kind):
    L, B = 48, 10  # logical size and border
    n = L * S
    a = np.zeros((n, n, 4))
    yy, xx = np.mgrid[0:n, 0:n]
    d = np.minimum(np.minimum(xx, n - 1 - xx), np.minimum(yy, n - 1 - yy))  # distance to edge in px
    # outer binding: dark leather (canvas) or a steel lip (metal)
    bind = d < 3 * S
    if kind == 'canvas':
        col = np.array([27, 22, 16.]) * (0.85 + 0.3 * rng.random((n, n, 1)))
    else:
        col = np.array([30, 32, 34.]) * (0.85 + 0.3 * rng.random((n, n, 1)))
    a[bind, :3] = col[bind]
    a[bind, 3] = 255
    a[(d == 0), 3] = 255
    # bevel: highlight on top/left inside the binding, shade on bottom/right
    edge = (d >= 3 * S) & (d < 4 * S)
    a[edge & ((yy < n / 2) | (xx < n / 2)), :] = [255, 240, 210, 40]
    a[edge & ((yy >= n / 2) & (xx >= n / 2)), :] = [0, 0, 0, 90]
    # inner shadow falling off over the border band
    inner = (d >= 4 * S) & (d < B * S)
    a[inner, :3] = 0
    a[inner, 3] = np.maximum(a[inner, 3], (1 - (d[inner] - 4 * S) / ((B - 4) * S)) * 70)
    if kind == 'canvas':
        # dashed stitching line
        s = (d >= 6 * S) & (d < 6 * S + 2)
        along = np.where((yy == d) | (yy == n - 1 - d), xx, yy)
        dash = (along // (3 * S)) % 2 == 0
        m = s & dash
        a[m, :3] = [200, 184, 140]
        a[m, 3] = 120
    # corner rivets
    for cy, cx in [(5, 5), (5, L - 6), (L - 6, 5), (L - 6, L - 6)]:
        r = np.hypot(yy - (cy + .5) * S, xx - (cx + .5) * S)
        rv = r < 2.2 * S
        shade = np.clip(1.25 - (yy - cy * S + xx - cx * S) / (6.0 * S), .55, 1.3)
        base = np.array([184, 146, 74.]) if kind == 'canvas' else np.array([140, 144, 146.])
        a[rv, :3] = (base[None] * shade[rv][:, None])
        a[rv, 3] = 255
        ring = (r >= 2.2 * S) & (r < 2.9 * S)
        a[ring] = [10, 8, 5, 200]
    return a.clip(0, 255).astype(np.uint8)


for kind in ('canvas', 'metal'):
    save('frame_' + kind, frame(kind), {'type': 'nine_slice', 'width': 48, 'height': 48, 'border': 10})


# ---------- slots ----------
def slot():
    n = 18 * S
    a = np.zeros((n, n, 4))
    yy, xx = np.mgrid[0:n, 0:n]
    a[..., :3] = 10
    a[..., 3] = 165
    top = np.clip(1 - yy / (5.0 * S), 0, 1)
    left = np.clip(1 - xx / (4.0 * S), 0, 1)
    a[..., 3] = np.minimum(255, a[..., 3] + 80 * np.maximum(top, left))
    a[yy == n - 1, :] = [255, 240, 210, 34]
    a[xx == n - 1, :] = [255, 240, 210, 22]
    a[(yy == 0) | (xx == 0), :] = [0, 0, 0, 230]
    return a.astype(np.uint8)


save('slot', slot(), {'type': 'stretch'})


def slot_ring(color, size=22):
    n = size * S
    a = np.zeros((n, n, 4))
    yy, xx = np.mgrid[0:n, 0:n]
    d = np.minimum(np.minimum(xx, n - 1 - xx), np.minimum(yy, n - 1 - yy))
    r = d < 2 * S
    shade = np.where(yy < n / 2, 1.15, .85)
    a[r, :3] = np.array(color)[None] * shade[r][:, None]
    a[r, 3] = 255
    a[d == 0] = [20, 14, 6, 255]
    return a.clip(0, 255).astype(np.uint8)


save('slot_selected', slot_ring([214, 176, 96]), {'type': 'stretch'})
save('slot_hover', slot_ring([226, 194, 122], 18) // np.array([1, 1, 1, 3], dtype=np.uint8), {'type': 'stretch'})


# ---------- masking tape label ----------
def tape():
    L, H = 48, 14
    w, h = L * S, H * S
    p = np.array([206, 193, 158.])[None, None] * (0.92 + 0.06 * fbm(128)[:h, :w][..., None] + 0.05 * (rng.random((h, w, 1)) - .5))
    alpha = np.full((h, w), 245.0)
    # torn ends
    for side in (0, 1):
        for y in range(h):
            cut = int(rng.integers(0, 4 * S))
            if side == 0:
                alpha[y, :cut] = 0
            else:
                alpha[y, w - cut:] = 0
    alpha[0, :] *= .6
    alpha[-1, :] *= .6
    return rgba(p, alpha)


save('tape', tape(), {'type': 'nine_slice', 'width': 48, 'height': 14, 'border': {'left': 5, 'right': 5, 'top': 2, 'bottom': 2}})


# ---------- character mirror ----------
def mirror():
    w, h = 64 * S, 120 * S
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot((xx - w * .45) / w, (yy - h * .35) / h * .8)
    c = np.array([58, 58, 48.])[None, None] * np.clip(1.05 - r * 1.9, .18, 1)[..., None]
    c *= 0.95 + 0.1 * rng.random((h, w, 1))
    a = rgba(c)
    d = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    a[d < 1] = [0, 0, 0, 255]
    sh = (d < 4 * S) & (d >= 1)
    a[sh, :3] = (a[sh, :3] * (0.5 + 0.5 * (d[sh] / (4 * S)))[:, None]).astype(np.uint8)
    return a


save('mirror', mirror(), {'type': 'stretch'})


# ---------- lighting overlays ----------
def vignette():
    w, h = 160, 90
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot((xx - w / 2) / (w / 2), (yy - h * .45) / (h / 2))
    alpha = np.clip((r - .55) / .65, 0, 1) ** 1.6 * 200
    return rgba(np.zeros((h, w, 3)), alpha)


def torch():
    n = 128
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot(xx - n / 2, yy - n / 2) / (n / 2)
    alpha = np.clip(1 - r, 0, 1) ** 2.2 * 46
    return rgba(np.ones((n, n, 3)) * np.array([255, 205, 140]), alpha)


save('vignette', vignette(), {'type': 'stretch'})
save('torch', torch(), {'type': 'stretch'})


# ---------- bag tab ----------
def tab(selected):
    w, h = 26 * S, 28 * S
    yy, xx = np.mgrid[0:h, 0:w]
    base = canvas()[: h, : w, :3].astype(float) * (1 if selected else .7)
    a = rgba(base)
    d = np.minimum(np.minimum(xx, w - 1 - xx), yy)
    a[d < 2 * S, :3] = [27, 22, 16]
    a[(d >= 2 * S) & (d < 3 * S) & (yy < h / 2)] = [120, 112, 90, 255]
    return a


save('tab', tab(False), {'type': 'stretch'})
save('tab_selected', tab(True), {'type': 'stretch'})
print('wrote', len(os.listdir(OUT)), 'files to', os.path.normpath(OUT))


# ---------- tooltip (style zomboidcraft:kit -> sprites tooltip/kit_background and tooltip/kit_frame) ----------
def tooltip(part):
    n = 100
    a = np.zeros((n, n, 4))
    yy, xx = np.mgrid[0:n, 0:n]
    d = np.minimum(np.minimum(xx, n - 1 - xx), np.minimum(yy, n - 1 - yy))
    if part == 'background':
        inside = d >= 9
        a[inside] = [16, 13, 10, 240]
        a[d == 9] = [0, 0, 0, 255]
    else:
        a[d == 10] = [184, 146, 74, 200]
        for cy, cx in [(10, 10), (10, n - 11), (n - 11, 10), (n - 11, n - 11)]:
            a[(abs(yy - cy) <= 1) & (abs(xx - cx) <= 1)] = [226, 194, 122, 255]
    return a.astype(np.uint8)


TIP = os.path.join(OUT, '../tooltip')
os.makedirs(TIP, exist_ok=True)
for part in ('background', 'frame'):
    Image.fromarray(tooltip(part), 'RGBA').save(os.path.join(TIP, f'kit_{part}.png'), optimize=True)
    with open(os.path.join(TIP, f'kit_{part}.png.mcmeta'), 'w') as f:
        json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': 100, 'height': 100, 'border': 12}}}, f)
        f.write('\n')
