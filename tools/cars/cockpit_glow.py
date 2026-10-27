#!/usr/bin/env python3
"""The lights in the bought cockpits: what glows when an aircraft's avionics have power (someone sat
in it). The cockpits themselves are left exactly as built; this only adds light drawn over them, laid
out after how the real ones are lit at night:

    jet_corvid (the F-22)       six colour LCDs: the tactical picture in the middle, attitude, engines,
                                stores, and the two small upfront displays (radios, cautions); the HUD;
                                the keypad, the soft keys round the displays and the console legends in
                                NVIS green; warning and caution lamps in their own colours.
    jet_gyrfalcon (the Su-27)   analogue gauges under dim red lighting: their markings and the panel
                                and console lettering glow red; signal lamps in their own colours; the
                                sight's green symbols on its glass.
    heli_hornbill (the AH-64D)  colour multipurpose displays in both seats (flight, map, weapons), the
                                engine strip and the amber radio display over them, the gunner's sight
                                showing its night channel; keys and legends NVIS green.

    python3 tools/cars/cockpit_glow.py [--only MODEL] [--preview DIR]

Every place that lights is picked from the seat's own view of the cockpit (a camera at the seat
looking down at the panel, the view rendered here), as a rectangle on that view:
    keys      the light marks on darker ground inside it (legends, key labels, dial markings) glow
              in the cockpit's tint, small strongly coloured spots (lamps) in their own colour
    screens   a page from cockpit_pages.py drawn over the display, a quad on the display's own face
Writes cars/<id>.zglow for each model: the triangles to draw glowing (copies of the cockpit's, and
the screens' quads), grouped so the screens come on one by one, and their texture. It holds texels
of the bought model's atlas, so it ships scrambled like the .zcar it goes with.
"""
import argparse
import io
import os
import struct
import sys
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import zcar  # noqa: E402
import cockpit_pages  # noqa: E402
from pack import scramble  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft')
NVIS = np.array([0.55, 1.0, 0.45])     # NVIS green A, the legends' backlight
RED = np.array([1.0, 0.28, 0.16])      # the old red instrument lighting
VIEW_W, VIEW_H, FOV = 1400, 1000, 110
TEX_W = 1024
PX_PER_M = 1100

# keys: (seat, pitch of the view in degrees down, rectangle on the view, strength)
# screens: (seat, pitch, rectangle, page, the display is its own face) or ('quad', TL, TR, BR, BL, page)
MODELS = {
    'jet_corvid': dict(tint=NVIS, keys=[
        (0, 30, (515, 470, 890, 788), 0.5),
        (0, 60, (395, 495, 575, 725), 0.35), (0, 60, (825, 495, 1005, 725), 0.35),
        (0, 60, (455, 400, 575, 475), 0.45), (0, 60, (825, 400, 945, 475), 0.45),
    ], screens=[
        (0, 30, (650, 553, 750, 650), 'tsd', False),
        (0, 30, (527, 553, 625, 645), 'adi', False),
        (0, 30, (780, 553, 878, 645), 'engines', False),
        (0, 30, (652, 668, 748, 785), 'stores', False),
        (0, 30, (548, 478, 622, 545), 'cni', False),
        (0, 30, (778, 478, 852, 545), 'icaw', False),
        ('quad', (0.074, 3.112, 5.788), (-0.074, 3.112, 5.788), (-0.074, 2.996, 5.788), (0.074, 2.996, 5.788), 'hud_jet'),
    ]),
    'heli_hornbill': dict(tint=NVIS, keys=[
        (0, 30, (545, 495, 960, 670), 0.55), (0, 60, (855, 420, 945, 520), 0.55),
        (0, 60, (335, 525, 520, 995), 0.28), (0, 60, (830, 535, 1005, 1000), 0.28),
        (1, 30, (420, 500, 980, 700), 0.55),
        (1, 60, (400, 545, 535, 1000), 0.28), (1, 60, (845, 440, 1000, 1000), 0.28),
    ], screens=[
        (0, 30, (589, 584, 671, 656), 'flight', True),
        (0, 30, (749, 583, 841, 656), 'tsd_heli', True),
        (0, 30, (557, 504, 672, 552), 'tapes', False),
        (0, 30, (716, 505, 856, 551), 'eufd', False),
        (1, 30, (646, 523, 754, 642), 'flir', False),
        (1, 30, (538, 575, 624, 652), 'weapons', True),
        (1, 30, (831, 592, 930, 664), 'tsd_heli', True),
        (1, 30, (446, 511, 600, 552), 'tapes', False),
        (1, 30, (828, 517, 958, 567), 'eufd', False),
    ]),
    'jet_gyrfalcon': dict(tint=RED, keys=[
        (0, 30, (440, 585, 1000, 745), 0.42),
        (0, 60, (300, 480, 560, 1000), 0.3), (0, 60, (830, 470, 1110, 1000), 0.3),
    ], screens=[
        ('quad', (0.058, 3.612, 5.888), (-0.072, 3.612, 5.888), (-0.072, 3.482, 5.928), (0.058, 3.482, 5.928), 'hud_flanker'),
    ]),
}


# The seat eyes the regions above were picked from. The game's eyes moved forward and down onto the
# design eye point later, so the picks keep these.
GLOW_EYES = {'jet_corvid': [(0.0, 3.24, 5.2)], 'jet_gyrfalcon': [(0.003, 3.72, 5.378)]}


# ------------------------------------------------------------------ the seat's view

def camera(eye, pitch):
    p = np.radians(pitch)
    f = np.array([0, -np.sin(p), np.cos(p)])
    r = np.array([-1.0, 0, 0])
    return f, r, np.cross(r, f)


def render(T, UV, eye, pitch, keep=None):
    """A z-buffered picture of triangles T (with uvs UV) from eye, looking ahead and pitch degrees down:
    per pixel the triangle (-1 for none), its uv, the point hit and the depth."""
    eye = np.asarray(eye, float)
    F = (VIEW_W / 2) / np.tan(np.radians(FOV / 2))
    f, r, up = camera(eye, pitch)
    sel = np.where(keep if keep is not None else np.ones(len(T), bool))[0]
    D = T[sel] - eye
    zf = D @ f
    xs = VIEW_W / 2 + (D @ r) / np.maximum(zf, 1e-6) * F
    ys = VIEW_H / 2 - (D @ up) / np.maximum(zf, 1e-6) * F
    zb = np.full((VIEW_H, VIEW_W), np.inf)
    tb = np.full((VIEW_H, VIEW_W), -1, np.int64)
    uvb = np.zeros((VIEW_H, VIEW_W, 2))
    pb = np.full((VIEW_H, VIEW_W, 3), np.nan)
    for k in range(len(sel)):
        if (zf[k] < 0.03).any():
            continue
        X, Y = xs[k], ys[k]
        x0, x1 = int(max(np.floor(X.min()), 0)), int(min(np.ceil(X.max()) + 1, VIEW_W))
        y0, y1 = int(max(np.floor(Y.min()), 0)), int(min(np.ceil(Y.max()) + 1, VIEW_H))
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        (ax, bx, cx), (ay, by, cy) = X, Y
        d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(d) < 1e-9:
            continue
        l0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / d
        l1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / d
        l2 = 1 - l0 - l1
        ins = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        if not ins.any():
            continue
        iw = np.stack([l0, l1, l2], -1) / zf[k]
        z = 1 / iw.sum(-1)
        bc = iw * z[..., None]
        sub = zb[y0:y1, x0:x1]
        m = ins & (z < sub)
        if not m.any():
            continue
        sub[m] = z[m]
        tb[y0:y1, x0:x1][m] = sel[k]
        uvb[y0:y1, x0:x1][m] = bc[m] @ UV[sel[k]]
        pb[y0:y1, x0:x1][m] = bc[m] @ T[sel[k]]
    return tb, uvb, pb, zb


def ray(eye, pitch, x, y):
    F = (VIEW_W / 2) / np.tan(np.radians(FOV / 2))
    f, r, up = camera(eye, pitch)
    d = f + r * (x - VIEW_W / 2) / F - up * (y - VIEW_H / 2) / F
    return d / np.linalg.norm(d)


# ------------------------------------------------------------------ what lights

def box_blur(a, r):
    """Mean over a (2r+1)^2 window, edges clamped."""
    k = 2 * r + 1
    p = np.pad(a, r, mode='edge').cumsum(0).cumsum(1)
    p = np.pad(p, ((1, 0), (1, 0)))
    return (p[k:, k:] - p[:-k, k:] - p[k:, :-k] + p[:-k, :-k]) / (k * k)


def components(mask):
    """Connected regions of a mask (4-neighbour): a label per texel (1..n), 0 outside."""
    ys, xs = np.nonzero(mask)
    if not len(ys):
        return np.zeros(mask.shape, int), 0
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    m = np.pad(mask[y0:y1, x0:x1], 1)
    big = m.size + 1
    lab = np.where(m, np.arange(m.size).reshape(m.shape) + 1, big)
    while True:
        new = lab.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            new = np.minimum(new, np.where(m, np.roll(lab, (dy, dx), (0, 1)), big))
        new[~m] = big
        if (new == lab).all():
            break
        lab = new
    _, inv = np.unique(np.where(m, lab, 0), return_inverse=True)
    out = np.zeros(mask.shape, int)
    out[y0:y1, x0:x1] = inv.reshape(m.shape)[1:-1, 1:-1]
    return out, int(inv.max())


def face_of(tb, pb, T, eye):
    """The flat face that covers most of a patch of the view: one of its triangles, its normal (toward
    the eye) and the point of it seen nearest the patch's middle."""
    ids, counts = np.unique(tb[tb >= 0], return_counts=True)
    planes = []
    for t, c in sorted(zip(ids, counts), key=lambda x: -x[1]):
        a, b, cc = T[t]
        n = np.cross(b - a, cc - a)
        n /= max(np.linalg.norm(n), 1e-12)
        if n @ (eye - a) < 0:
            n = -n
        for pl in planes:
            if pl[1] @ n > 0.998 and abs((a - pl[2]) @ pl[1]) < 0.002:
                pl[3] += c
                pl[4].append(t)
                break
        else:
            planes.append([t, n, a, c, [t]])
    best = max(planes, key=lambda pl: pl[3])
    on = np.isin(tb, best[4])
    ys, xs = np.nonzero(on)
    h, w = tb.shape
    k = np.argmin((ys - h / 2) ** 2 + (xs - w / 2) ** 2)
    return best[0], best[1], pb[ys[k], xs[k]]


def screen_quad(spec, T, views, eyes):
    """The four corners (top left, top right, bottom right, bottom left, as the seat sees it) of a
    display's lit area, a hair in from its edges."""
    if spec[0] == 'quad':
        q = np.array(spec[1:5], float)
    else:
        seat, pitch, (x0, y0, x1, y1), _, face = spec
        eye = np.array(eyes[seat])
        tb, _, pb, _ = views[(seat, pitch)]
        # the face most of the rectangle shows, and the point on it nearest the rectangle's middle
        t0, n, p0 = face_of(tb[y0:y1, x0:x1], pb[y0:y1, x0:x1], T, eye)
        q = None
        if face:
            # the display's own face: the triangles flat with the middle one and wholly inside the rectangle
            ids = np.unique(tb[y0:y1, x0:x1])
            ids = ids[ids >= 0]
            pts = []
            for t in ids:
                v = T[t]
                nt = np.cross(v[1] - v[0], v[2] - v[0])
                nt /= max(np.linalg.norm(nt), 1e-12)
                if abs(nt @ n) < 0.995 or np.abs((v - p0) @ n).max() > 0.003:
                    continue
                ys, xs = np.nonzero(tb == t)
                if ((xs >= x0) & (xs < x1) & (ys >= y0) & (ys < y1)).mean() < 0.97:
                    continue
                pts.append(v)
            if pts:
                pts = np.concatenate(pts)
                right = np.array([-1.0, 0, 0]) - n * -n[0]
                right /= np.linalg.norm(right)
                up = np.cross(n, right)
                cr, cu = (pts - p0) @ right, (pts - p0) @ up
                l, rr, bo, to = cr.min(), cr.max(), cu.min(), cu.max()
                q = np.array([p0 + right * l + up * to, p0 + right * rr + up * to,
                              p0 + right * rr + up * bo, p0 + right * l + up * bo])
        if q is None:
            q = []
            for x, y in ((x0, y0), (x1, y0), (x1, y1), (x0, y1)):
                d = ray(eye, pitch, x, y)
                q.append(eye + d * ((p0 - eye) @ n) / (d @ n))
            q = np.array(q)
    mid = q.mean(0)
    return mid + (q - mid) * 0.96


def classify(spec, T, UV, img, views, quads):
    """The glow of each atlas texel in the model's key regions (premultiplied colour), and which
    triangles were seen in them."""
    W, H = img.size
    col = np.asarray(img.convert('RGB'), float) / 255
    region = np.zeros((H, W))
    scale = np.zeros((H, W))
    seen = set()
    for seat, pitch, (x0, y0, x1, y1), strength in spec['keys']:
        tb, uvb, pb, _ = views[(seat, pitch)]
        t = tb[y0:y1, x0:x1]
        ok = t >= 0
        pts = pb[y0:y1, x0:x1][ok]
        # not under a screen: its page lights it
        under = np.zeros(len(pts), bool)
        for q in quads:
            n = np.cross(q[1] - q[0], q[3] - q[0])
            n /= np.linalg.norm(n)
            ex, ey = q[1] - q[0], q[3] - q[0]
            rel = pts - q[0]
            s, u = rel @ ex / (ex @ ex), rel @ ey / (ey @ ey)
            under |= (np.abs(rel @ n) < 0.012) & (s > -0.04) & (s < 1.04) & (u > -0.04) & (u < 1.04)
        idx = np.nonzero(ok)
        ok[idx[0][under], idx[1][under]] = False
        uv = uvb[y0:y1, x0:x1][ok]
        tx = np.clip((uv[:, 0] * W).astype(int), 0, W - 1)
        ty = np.clip((uv[:, 1] * H).astype(int), 0, H - 1)
        np.maximum.at(region, (ty, tx), strength)
        # how bright this panel's brightest marks are: the legends are picked against that
        np.maximum.at(scale, (ty, tx), np.percentile(col[ty, tx] @ np.array([0.3, 0.59, 0.11]), 99))
        seen.update(int(i) for i in np.unique(t[ok]))
    # fill the odd texel the view skipped between ones it hit
    near = np.maximum.reduce([np.roll(region, s, a) for s in (-1, 0, 1) for a in (0, 1)])
    region = np.where(region > 0, region, near * (box_blur((region > 0).astype(float), 1) > 0.3))
    live = region > 0
    lum = col @ np.array([0.3, 0.59, 0.11])
    mx, mn = col.max(-1), col.min(-1)
    sat = np.where(mx > 0.05, (mx - mn) / np.maximum(mx, 1e-6), 0)
    scale = np.maximum(np.where(scale > 0, scale, near_max(scale)), 0.02)
    ln = np.clip(lum / scale, 0, 1.5)
    local = box_blur(ln, 4)
    glow = np.zeros(col.shape)
    legend = live & (ln > 0.45) & (ln - local > 0.12) & (sat < 0.45)
    k = np.clip((ln - local) * 2 + 0.3, 0, 1)[..., None]
    glow[legend] = (spec['tint'] * region[..., None] * k)[legend]
    lamp = live & (sat > 0.5) & (mx > np.minimum(0.3, scale * 0.5))
    lab, nlab = components(lamp)
    if nlab:
        size = np.bincount(lab.ravel())
        small = size < 80
        small[0] = False
        lamp = small[lab]
        glow[lamp] = (col / np.maximum(mx, 1e-3)[..., None] * 0.95)[lamp]
    return glow, seen


def near_max(a):
    """Each texel's largest neighbour (3 x 3)."""
    return np.maximum.reduce([np.roll(np.roll(a, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)])


def covers(uvpx, lit):
    """Whether a triangle's footprint on the atlas (in texels) takes in any lit texel."""
    H, W = lit.shape
    x0, y0 = np.floor(uvpx.min(0)).astype(int)
    x1, y1 = np.ceil(uvpx.max(0)).astype(int) + 1
    x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, W), min(y1, H)
    if x1 <= x0 or y1 <= y0:
        return False
    xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
    a, b, c = uvpx
    d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    if abs(d) < 1e-9:
        return False
    l0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
    l1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
    ins = (l0 >= -0.3) & (l1 >= -0.3) & (1 - l0 - l1 >= -0.3)
    return bool((ins & lit[y0:y1, x0:x1]).any())


def merge(rects):
    """Overlapping rectangles merged until none overlap."""
    rects = [list(r) for r in rects]
    changed = True
    while changed:
        changed = False
        out = []
        for r in rects:
            for o in out:
                if r[0] < o[2] and o[0] < r[2] and r[1] < o[3] and o[1] < r[3]:
                    o[0], o[1], o[2], o[3] = min(o[0], r[0]), min(o[1], r[1]), max(o[2], r[2]), max(o[3], r[3])
                    changed = True
                    break
            else:
                out.append(r)
        rects = out
    return rects


def shelf(sizes, width):
    """Places rectangles (w, h) in rows across width, tallest first: their corners, and the height used."""
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    at = [None] * len(sizes)
    x = y = row = 0
    for i in order:
        w, h = sizes[i]
        if x + w > width:
            x, y, row = 0, y + row, 0
        at[i] = (x, y)
        x += w
        row = max(row, h)
    return at, y + row


def build(model, spec, preview=None):
    states = zcar.read(model)
    u = states['usable']
    T, UV, glass, img = u['T'], u['UV'], u['glass'], u['image']
    W, H = img.size
    eyes = GLOW_EYES.get(model) or [tuple(e) for e in {m['id']: m for m in zcar.models()}[model]['seats']]
    near = np.zeros(len(T), bool)
    for e in eyes:
        near |= np.linalg.norm(T.mean(1) - np.array(e), axis=1) < 4
    keep = near & ~glass
    views = {}
    for s in list(spec['keys']) + [x for x in spec['screens'] if x[0] != 'quad']:
        key = (s[0], s[1])
        if key not in views:
            views[key] = render(T, UV, eyes[key[0]], key[1], keep)
    quads = [screen_quad(s, T, views, eyes) for s in spec['screens']]
    glow, seen = classify(spec, T, UV, img, views, quads)
    lit = glow.max(-1) > 0.03
    tris = [t for t in sorted(seen) if covers(UV[t] * np.array([W, H]), lit)]

    # the texture: a patch of the glow layer round each glowing triangle, then each screen's page
    foot = []
    for t in tris:
        q = UV[t] * np.array([W, H])
        foot.append([max(int(np.floor(q[:, 0].min())) - 1, 0), max(int(np.floor(q[:, 1].min())) - 1, 0),
                     min(int(np.ceil(q[:, 0].max())) + 1, W), min(int(np.ceil(q[:, 1].max())) + 1, H)])
    patches = merge(foot)
    pages = []
    for i, (s, q) in enumerate(zip(spec['screens'], quads)):
        name = s[-1] if s[0] == 'quad' else s[3]
        pw = int(np.clip(round(np.linalg.norm(q[1] - q[0]) * PX_PER_M), 40, 256))
        ph = int(np.clip(round(np.linalg.norm(q[3] - q[0]) * PX_PER_M), 32, 256))
        pages.append((name, pw, ph, cockpit_pages.PAGES[name](pw, ph, 11 + i)))
    sizes = [(r[2] - r[0] + 2, r[3] - r[1] + 2) for r in patches] + [(pw + 2, ph + 2) for _, pw, ph, _ in pages]
    at, used = shelf(sizes, TEX_W)
    TH = (used + 15) // 16 * 16
    tex = np.zeros((TH, TEX_W, 3))
    for r, (x, y) in zip(patches, at):
        tex[y + 1:y + 1 + r[3] - r[1], x + 1:x + 1 + r[2] - r[0]] = glow[r[1]:r[3], r[0]:r[2]]
    for (_, pw, ph, page), (x, y) in zip(pages, at[len(patches):]):
        tex[y + 1:y + 1 + ph, x + 1:x + 1 + pw] = page

    # the triangles: each glowing one with its uvs moved onto its patch, then two per screen
    out = []
    for t, f in zip(tris, foot):
        j = next(i for i, r in enumerate(patches) if r[0] <= f[0] and r[1] <= f[1] and r[2] >= f[2] and r[3] >= f[3])
        r, (x, y) = patches[j], at[j]
        q = UV[t] * np.array([W, H]) - np.array([r[0], r[1]]) + np.array([x + 1, y + 1])
        out.append((0, T[t], q / np.array([TEX_W, TH])))
    for i, (q, (x, y), (_, pw, ph, _)) in enumerate(zip(quads, at[len(patches):], pages)):
        u0, v0, u1, v1 = (x + 1) / TEX_W, (y + 1) / TH, (x + 1 + pw) / TEX_W, (y + 1 + ph) / TH
        tl, tr, br, bl = q
        uv = {0: (u0, v0), 1: (u1, v0), 2: (u1, v1), 3: (u0, v1)}
        for a, b, c in ((0, 3, 2), (0, 2, 1)):
            p = np.array([q[a], q[b], q[c]])
            n = np.cross(p[1] - p[0], p[2] - p[0])
            seat = eyes[spec['screens'][i][0]] if spec['screens'][i][0] != 'quad' else eyes[0]
            order = (a, b, c) if n @ (np.array(seat) - p[0]) > 0 else (a, c, b)
            out.append((i + 1, np.array([q[k] for k in order]), np.array([uv[k] for k in order])))
    mids = np.array([q.mean(0) for q in quads])
    dash = mids.mean(0) + (np.mean(eyes, 0) - mids.mean(0)) * 0.3
    print(f'{model}: {len(tris)} glowing triangles in {len(patches)} patches, {len(pages)} screens, '
          f'texture {TEX_W}x{TH}', flush=True)
    if preview:
        night(model, T, UV, img, keep, eyes, spec, out, tex, preview)
    return out, tex, dash, [s[-1] if s[0] == 'quad' else s[3] for s in spec['screens']]


def write(model, tris, tex, dash, names):
    """cars/<id>.zglow: 'ZGLW', version, the texture (PNG, premultiplied, alpha its strength), the cabin's
    glow spot, the groups (0 the keys, then one per screen: 1 a display, 2 a head-up display), and the
    triangles: group, then three corners of x, y, z, u, v."""
    rgba = np.concatenate([tex, tex.max(-1, keepdims=True)], -1)
    buf = io.BytesIO()
    Image.fromarray(np.clip(rgba * 255, 0, 255).astype(np.uint8), 'RGBA').save(buf, 'PNG', optimize=True)
    png = buf.getvalue()
    out = io.BytesIO()
    out.write(b'ZGLW')
    out.write(struct.pack('>ii', 1, len(png)))
    out.write(png)
    out.write(struct.pack('>fff', *dash))
    out.write(struct.pack('>i', len(names) + 1))
    out.write(struct.pack('>b', 0))
    for n in names:
        out.write(struct.pack('>b', 2 if n.startswith('hud') else 1))
    out.write(struct.pack('>i', len(tris)))
    for g, p, uv in tris:
        out.write(struct.pack('>b', g))
        for k in range(3):
            out.write(struct.pack('>fffff', *p[k], *uv[k]))
    data = scramble(zlib.compress(out.getvalue(), 9), model)
    path = os.path.join(ASSETS, 'cars', f'{model}.zglow')
    with open(path, 'wb') as f:
        f.write(data)
    print(f'  wrote {os.path.relpath(path, ROOT)} ({len(data) // 1024} KB)')


def night(model, T, UV, img, keep, eyes, spec, tris, tex, outdir):
    """What each seat sees at night with the power on: the cockpit dim, the glow over it."""
    os.makedirs(outdir, exist_ok=True)
    W, H = img.size
    col = np.asarray(img.convert('RGB'), float) / 255
    # the keys and the screens each added on, as the game does (the glow doesn't hide glow)
    passes = [[t for t in tris if (t[0] == 0) == first] for first in (True, False)]
    for seat, eye in enumerate(eyes):
        for pitch in (30, 60):
            tb, uvb, _, zb = render(T, UV, eye, pitch, keep)
            hit = tb >= 0
            base = np.zeros((VIEW_H, VIEW_W, 3))
            tx = np.clip((uvb[..., 0] * W).astype(int), 0, W - 1)
            ty = np.clip((uvb[..., 1] * H).astype(int), 0, H - 1)
            base[hit] = col[ty[hit], tx[hit]] * 0.07
            for group in passes:
                if not group:
                    continue
                gb, guv, _, gz = render(np.array([p for _, p, _ in group]), np.array([uv for _, _, uv in group]), eye, pitch)
                g = (gb >= 0) & (gz <= zb + 0.01)
                gx = np.clip((guv[..., 0] * TEX_W).astype(int), 0, TEX_W - 1)
                gy = np.clip((guv[..., 1] * tex.shape[0]).astype(int), 0, tex.shape[0] - 1)
                base[g] += tex[gy[g], gx[g]]
            Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8)).save(
                os.path.join(outdir, f'{model}_{seat}_{pitch}.png'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default=None)
    ap.add_argument('--preview', default=None)
    a = ap.parse_args()
    for model, spec in MODELS.items():
        if a.only and a.only != model:
            continue
        write(model, *build(model, spec, a.preview))


if __name__ == '__main__':
    main()
