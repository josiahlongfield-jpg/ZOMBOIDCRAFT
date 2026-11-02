"""Wave 5 decor models, batch 3: furniture and comfort (wave 4 stage 5; specs in decor.py). Built from the photos in
refs/items/decor/<id>/. The hand truck photos show trolleys, so it's the usual two-wheel hand truck.

Same conventions as wave5games: model units standing on y = 0, front toward +z, furniture at the world's scale
(about 0.4 units an inch), painted faces through wave5games.face_paint.
"""
import math

import numpy as np

import art as A
import paint as P
import shapes as sh
from wave4 import blob, flat, flat_arc
from wave5games import face_paint, put, text, sign, neon


# ================================================================== painters


def check(c1, c2, n=8.0):
    """Houndstooth-ish check for fabric."""
    def fn(f):
        u, v = f.uv[..., 0], f.uv[..., 1]
        k = ((np.floor(u * n) + np.floor(v * n)) % 2 == 0) | ((np.floor(u * n * 2) + np.floor(v * n)) % 3 == 0)
        c = A.plain(f, c1, 0.08)
        return np.where(k[..., None], P.col(c2) * (1 + (f.noise(2.0, 5, 2) - 0.5) * 0.08)[..., None], c)
    return fn


def stripes(c1, c2, n=3.0):
    def fn(f):
        k = (f.P[..., 2] * n / 4.0) % 1 < 0.5
        c = A.plain(f, c1, 0.08)
        return np.where(k[..., None], P.col(c2), c)
    return fn


def rope_net(rope, gap='#1e1f20'):
    def draw(u, v, W, H, c, f):
        a, b = (u + v) * 1.2 % 1, (u - v) * 1.2 % 1
        return put(c, (a < 0.22) | (b < 0.22), rope)
    return face_paint(draw, gap, ('all',), 0.05)


def tufted(base, faces=('front',), n=6):
    """Channel-tufted vinyl: vertical channels with stitched seams."""
    def draw(u, v, W, H, c, f):
        k = (u / W * n) % 1
        c = c * (1 - 0.18 * np.abs(k - 0.5) * 2)[..., None]
        return put(c, k < 0.04, A.tone(base, 0.6))
    return face_paint(draw, base, faces, 0.05)


def baffles(base):
    def draw(u, v, W, H, c, f):
        k = (v / H * 7) % 1
        c = c * (1 - 0.12 * np.abs(k - 0.5) * 2)[..., None]
        sheen = np.exp(-((u / W - 0.3) ** 2 + (v / H - 0.3) ** 2) * 12)
        return c * (1 + 0.35 * sheen)[..., None]
    return face_paint(draw, base, ('top',), 0.03)


def rug(field, border, accent, ink):
    """A Persian rug's face: guard stripes and a vine border, a field of small repeating motifs, the central
    medallion with its pendants and the corner spandrels."""
    def draw(u, v, W, H, c, f):
        x, y = u / W, v / H
        e = np.minimum(np.minimum(x, 1 - x) * W, np.minimum(y, 1 - y) * H)
        b = min(W, H)
        c = put(c, e < b * 0.14, border)
        c = put(c, (np.abs(e - b * 0.02) < b * 0.008) | (np.abs(e - b * 0.12) < b * 0.008)
                | (np.abs(e - b * 0.14) < b * 0.01), ink)
        vine = (e < b * 0.12) & (e > b * 0.03) & (np.sin((u + v) * 9.0) * np.sin((u - v) * 9.0) > 0.55)
        c = put(c, vine, accent)
        inner = e >= b * 0.15
        mx, my = (u * 6 / b) % 1, (v * 6 / b) % 1
        motif = inner & (np.abs(mx - 0.5) + np.abs(my - 0.5) < 0.28) & (np.abs(mx - 0.5) + np.abs(my - 0.5) > 0.16)
        c = put(c, motif, accent)
        c = put(c, inner & (np.hypot(mx - 0.5, my - 0.5) < 0.07), ink)
        cx, cy = (u - W / 2) / (W * 0.24), (v - H / 2) / (H * 0.3)
        d = np.abs(cx) + np.abs(cy)
        med = d < 1.0
        c = put(c, med, border)
        c = put(c, med & (np.abs(d - 0.8) < 0.06), ink)
        c = put(c, med & (d < 0.55), accent)
        petals = med & (d < 0.45) & (np.cos(np.arctan2(cy, cx) * 8) * 0.12 + 0.3 > d)
        c = put(c, petals, field)
        c = put(c, np.hypot(cx, cy) < 0.08, ink)
        for sx in (0, 1):                      # corner spandrels
            for sy in (0, 1):
                q = np.hypot((x - sx) * W, (y - sy) * H)
                c = put(c, inner & (q < b * 0.42) & (q > b * 0.38), accent)
                c = put(c, inner & (q < b * 0.36) & (q > b * 0.15), A.tone(border, 0.85))
        wear = f.noise(0.8, 33, 3) > 0.7
        return np.where(wear[..., None], c * 0.85, c)
    return face_paint(draw, field, ('top',), 0.06)


def dial(face='#f0e8d0', ink='#1e1a14'):
    """A clock dial: chapter ring with twelve hour marks and the minute track."""
    def draw(u, v, W, H, c, f):
        x, y = u - W / 2, v - H / 2
        R = min(W, H) / 2
        r = np.hypot(x, y) / R
        a = (np.degrees(np.arctan2(y, x)) + 360) % 360
        c = put(c, r > 0.97, A.tone(ink, 2.0))
        c = put(c, (np.abs(r - 0.9) < 0.012) | (np.abs(r - 0.7) < 0.012), ink)
        hour = ((a + 3) % 30 < 6) & (r > 0.72) & (r < 0.88)
        minute = ((a + 0.6) % 6 < 1.2) & (r > 0.86) & (r < 0.9)
        c = put(c, hour | minute, ink)
        return put(c, r < 0.05, ink)
    return face_paint(draw, face, ('front',), 0.03)


def fur(c1, c2):
    def fn(f):
        t = f.noise(7.0, 61, 3) * 0.6 + f.noise(1.5, 63, 2) * 0.4
        c = P.mix(P.col(c1), P.col(c2), np.clip(t * 1.6 - 0.3, 0, 1))
        tips = f.noise(14.0, 62, 2) > 0.68
        return np.where(tips[..., None], c * 1.18, c)
    return fn


# ================================================================== the pieces


def bean_bag(m, k=0):
    """A bean bag chair: the slumped body, the higher back pushed up behind the seat hollow, the piped seams and
    the carry handle on top."""
    blob(m, (0.0, 3.2, 0.6), (6.4, 3.2, 6.0), 'cloth')
    blob(m, (0.0, 7.4, -2.6), (4.6, 4.8, 3.4), 'cloth')
    blob(m, (0.0, 4.6, 2.2), (4.6, 1.4, 3.6), 'cloth')                          # the seat, sunk in
    for dx in (-4.4, 4.4):
        blob(m, (dx, 4.4, 0.0), (2.0, 2.6, 4.4), 'cloth')                       # bulging sides
    m.seg((-0.8, 12.0), (0.8, 12.0), 0.3, 0.6, 'trim', z=-2.6)                   # handle
    for x in (-0.8, 0.8):
        m.seg((x, 11.6), (x, 12.0), 0.3, 0.6, 'trim', z=-2.6)


def bar_stools(m, k=0):
    """A pair of bar stools: the bentwood or turned-wood stools with their leg rings and round cushions, or the
    chrome swivel stools with a weighted base, gas column, foot ring and padded backs."""
    chrome = k >= 2
    for cx in (-4.5, 4.5):
        if not chrome:
            for a in (45, 135, 225, 315):
                dx, dz = math.cos(math.radians(a)), math.sin(math.radians(a))
                m.rod(0.0, 11.4, 0.32, 'wood', cx=cx + dx * 1.9, cz=dz * 1.9)
            flat_arc(m, (cx, 0.0), 2.05, 0, 360, 0.35, 0.35, 'wood', y0=4.0, steps=8)
            flat_arc(m, (cx, 0.0), 1.95, 0, 360, 0.3, 0.3, 'wood', y0=8.0, steps=8)
            m.rod(11.4, 11.8, 2.6, 'wood', cx=cx)
            m.rod(11.8, 12.8, 2.5, 'seat', cx=cx)
            blob(m, (cx, 12.8, 0.0), (2.4, 0.5, 2.4), 'seat')
        else:
            m.rod(0.0, 0.5, 2.6, 'metal', cx=cx)
            m.rod(0.5, 10.4, 0.45, 'metal', cx=cx)
            flat_arc(m, (cx, 0.0), 2.0, 0, 360, 0.3, 0.3, 'metal', y0=4.6, steps=12)
            m.rod(10.4, 10.9, 1.0, 'dark', cx=cx)
            m.box([cx - 2.6, 10.9, -2.4], [cx + 2.6, 12.2, 2.4], 'seat')
            m.box([cx - 2.4, 12.2, -2.6], [cx + 2.4, 17.2, -1.6], 'seat', rot=dict(axis='x', angle=-22.5,
                                                                                   origin=[cx, 12.2, -2.1]))
            m.seg((cx, 10.9), (cx, 12.4), 0.6, 0.6, 'metal', z=-2.3)


def hand_truck(m, k=0):
    """A two-wheel hand truck: the steel nose plate, the two tall rails with their cross bars, the loop handle
    with rubber grips, the axle bracket, two pneumatic wheels."""
    for x in (-2.8, 2.8):
        m.rod(0.0, 19.0, 0.35, 'frame', cx=x, cz=-1.2)
        m.seg((x, 19.0), (x * 0.7, 20.6), 0.6, 0.6, 'frame', z=-1.2)
    m.seg((-2.0, 20.6), (2.0, 20.6), 0.6, 0.6, 'frame', z=-1.2)
    for x in (-2.0, 2.0):
        m.seg((x - 0.6, 20.6), (x + 0.6, 20.6), 0.8, 0.8, 'grip', z=-1.2)
    for y in (4.0, 8.0, 12.0, 16.0):
        m.box([-2.8, y - 0.2, -1.4], [2.8, y + 0.2, -1.0], 'frame')
    m.box([-3.4, 0.0, -1.2], [3.4, 0.4, 2.6], 'plate')                          # nose plate
    m.box([-3.4, 0.4, -1.4], [3.4, 1.0, -1.0], 'frame')
    m.box([-3.2, 1.4, -3.0], [3.2, 2.2, -1.0], 'frame')                          # axle bracket
    m.rod(-4.8, 4.8, 0.25, 'metal', cx=2.2, cz=-2.4, axis=0)
    for x0, x1 in ((-5.0, -3.6), (3.6, 5.0)):
        m.rod(x0, x1, 2.2, 'tyre', cx=2.2, cz=-2.4, axis=0)
        m.rod(x0 - 0.05, x1 + 0.05, 1.0, 'hub', cx=2.2, cz=-2.4, axis=0)


def recliner(m, k=0):
    """A recliner: the skirted base, the thick padded arms, the seat cushion, the pillow back in three rolls
    leaning back, the footrest swung up (every other look) and the pull handle."""
    up = k % 2 == 1
    m.box([-7.0, 0.0, -6.0], [7.0, 2.0, 6.0], 'dark')
    m.box([-6.4, 2.0, -6.0], [6.4, 6.6, 6.4], 'cover')
    for s in (-1, 1):
        x0, x1 = sorted([s * 4.8, s * 7.2])
        m.box([x0, 2.0, -6.0], [x1, 10.4, 6.4], 'cover')
        blob(m, ((x0 + x1) / 2, 10.4, 0.2), (1.4, 0.9, 6.4), 'cover')
    m.box([-4.8, 6.6, -4.4], [4.8, 8.4, 6.2], 'cover')
    blob(m, (0.0, 8.4, 1.0), (4.6, 0.6, 5.0), 'cover')
    tilt = dict(axis='x', angle=-22.5, origin=[0.0, 7.0, -4.6])
    m.box([-5.2, 7.0, -6.4], [5.2, 19.0, -4.0], 'cover', rot=tilt)
    for y, h in ((8.4, 3.6), (12.2, 3.6), (16.0, 3.4)):
        m.box([-4.6, y, -4.2], [4.6, y + h, -2.8], 'cover', rot=tilt)
    if up:
        m.box([-4.6, 5.4, 6.4], [4.6, 7.6, 13.0], 'cover')                     # the footrest out
        m.box([-3.0, 2.4, 6.6], [3.0, 5.4, 7.2], 'dark')
    m.box([7.2, 7.0, 3.6], [7.5, 8.6, 4.4], 'dark')                              # pull handle


def hammock(m, k=0):
    """A hammock on its free-standing wooden stand: the long base beam and feet, the angled uprights with their
    hooks, the chains or ropes, the spreader bars, the sagging bed of rope net or striped canvas."""
    m.box([-19.0, 0.0, -0.8], [19.0, 1.4, 0.8], 'wood')
    for x in (-15.0, 15.0):
        m.box([x - 0.8, 0.0, -6.0], [x + 0.8, 1.0, 6.0], 'wood')
    for s in (-1, 1):
        m.seg((s * 18.4, 1.0), (s * 15.6, 17.0), 1.2, 1.2, 'wood', z=0.0)
        m.box([s * 15.6 - 0.2, 16.4, -0.2], [s * 15.6 + 0.2, 17.2, 0.2], 'metal')
        m.seg((s * 15.6, 16.6), (s * 12.6, 13.4), 0.25, 0.25, 'rope', z=0.0)
        m.rod(-4.4, 4.4, 0.35, 'wood', cx=s * 12.4, cz=13.2, axis=2)            # spreader
        for z in (-4.0, -2.0, 0.0, 2.0, 4.0):
            m.seg((s * 12.6, 13.4), (s * 12.4, 13.2), 0.15, 0.15, 'rope', z=z)
    pts = [(-12.4, 13.0), (-9.0, 10.4), (-5.0, 8.6), (0.0, 8.0), (5.0, 8.6), (9.0, 10.4), (12.4, 13.0)]
    for p0, p1 in zip(pts, pts[1:]):
        m.seg(p0, p1, 0.3, 8.6, 'bed', z=0.0)


def velvet_rope_stands(m, k=0):
    """Two queue posts and their rope: the domed weighted bases, the polished columns, the urn tops with their
    rope hooks, the velvet rope swagged between them with its end clips."""
    for x in (-8.0, 8.0):
        m.rod(0.0, 0.5, 2.6, 'post', cx=x)
        blob(m, (x, 0.5, 0.0), (2.4, 1.0, 2.4), 'post')
        m.rod(1.2, 14.0, 0.45, 'post', cx=x)
        for y in (6.0, 13.4):
            m.rod(y, y + 0.4, 0.65, 'post', cx=x)
        blob(m, (x, 14.8, 0.0), (0.9, 1.0, 0.9), 'post')
        blob(m, (x, 16.0, 0.0), 0.45, 'post')
        m.box([x - 0.15 if x < 0 else x - 0.9, 13.2, -0.15], [x + 0.9 if x < 0 else x + 0.15, 13.6, 0.15], 'post')
    pts = [(-7.2, 13.0), (-5.0, 10.8), (-2.5, 9.6), (0.0, 9.3), (2.5, 9.6), (5.0, 10.8), (7.2, 13.0)]
    for p0, p1 in zip(pts, pts[1:]):
        m.seg(p0, p1, 0.8, 0.8, 'rope', z=0.0)
    for x in (-7.2, 7.2):
        m.rod(12.6, 13.4, 0.5, 'post', cx=x, cz=0.0)


def mounted_deer_head(m, k=0):
    """A deer head on its shield plaque: the neck coming off the board, the head and muzzle, glass eyes, ears, the
    antlers with their tines, the board with its routed edge and feet so it stands on a shelf."""
    m.box([-5.0, 1.0, -1.2], [5.0, 14.0, 0.0], 'plaque')
    m.box([-4.4, 1.6, 0.0], [4.4, 13.4, 0.3], 'plaque2')
    for x in (-3.6, 3.6):
        m.box([x - 0.5, 0.0, -2.6], [x + 0.5, 1.0, 1.0], 'plaque')
    blob(m, (0.0, 7.0, 2.6), (3.2, 4.4, 2.8), 'hide')                            # neck
    blob(m, (0.0, 10.4, 5.4), (2.4, 2.6, 3.0), 'hide')                           # head
    blob(m, (0.0, 9.0, 8.4), (1.4, 1.4, 2.2), 'hide')                            # muzzle
    blob(m, (0.0, 9.2, 10.4), (0.7, 0.6, 0.4), 'nose')
    blob(m, (0.0, 8.0, 7.6), (1.2, 0.6, 1.8), 'pale')                            # chin
    for s in (-1, 1):
        blob(m, (s * 1.7, 11.2, 7.0), 0.35, 'eye')
        blob(m, (s * 3.0, 12.4, 4.4), (1.4, 0.7, 0.5), 'hide')                   # ears
    tines = 3 + k % 3
    spread = 1.0 + 0.15 * (k % 3)
    for s in (-1, 1):
        beam = [(s * 1.4, 12.6), (s * 3.4 * spread, 15.0), (s * 4.6 * spread, 18.0), (s * 4.0 * spread, 21.6),
                (s * 2.6 * spread, 24.0)]
        for p0, p1 in zip(beam, beam[1:]):
            m.seg(p0, p1, 0.55, 0.55, 'antler', z=5.0)
        for q in range(tines):
            bx, by = beam[1 + q % 4] if q < 4 else beam[2]
            m.seg((bx, by), (bx - s * 0.4, by + 3.0), 0.4, 0.4, 'antler', z=5.0)
        m.seg((s * 2.0, 13.4), (s * 1.2, 15.2), 0.4, 0.4, 'antler', z=5.6)       # brow tine


def waterbed(m, k=0):
    """A waterbed: the padded wooden frame with its deck and pedestal, the vinyl water mattress with its baffle
    ripples, the safety liner over the rails, the headboard; made up with a sheet and pillows on the second look."""
    W, L = 12.0, 16.0
    m.box([-W + 1.0, 0.0, -L + 1.0], [W - 1.0, 3.0, L - 1.0], 'plinth')         # pedestal
    m.box([-W, 3.0, -L], [W, 3.6, L], 'wood')                                    # deck
    for x0, x1, z0, z1 in ((-W, W, -L, -L + 1.4), (-W, W, L - 1.4, L), (-W, -W + 1.4, -L, L), (W - 1.4, W, -L, L)):
        m.box([x0, 3.6, z0], [x1, 8.0, z1], 'rail')
        m.box([x0, 8.0, z0], [x1, 8.6, z1], 'pad')
    m.box([-W + 1.4, 3.6, -L + 1.4], [W - 1.4, 7.8, L - 1.4], 'vinyl')
    m.box([-W, 8.0, -L - 1.6], [W, 20.0, -L], 'wood')                            # headboard
    m.box([-W + 1.0, 9.0, -L], [W - 1.0, 19.0, -L + 0.3], 'plinth')
    m.box([-W, 20.0, -L - 1.8], [W, 20.6, -L + 0.2], 'wood')
    if k == 1:
        m.box([-W + 1.4, 7.8, -L + 7.0], [W - 1.4, 8.4, L - 1.4], 'sheet')
        m.box([-W + 1.4, 8.4, -L + 7.0], [W - 1.4, 9.0, -L + 11.0], 'sheet')
        for x in (-5.2, 5.2):
            blob(m, (x, 8.8, -L + 4.2), (4.2, 1.2, 2.4), 'pillow')


def barber_chair(m, k=0):
    """An old barber chair: the round base and hydraulic column with its pump pedal, the upholstered seat, the
    rolled back and the headrest on its bar, the arms with their porcelain-and-metal rests, the step-up foot
    plate on its arm."""
    m.rod(0.0, 0.6, 4.2, 'metal')
    m.rod(0.6, 1.4, 3.0, 'metal')
    m.rod(1.4, 6.0, 1.4, 'metal')
    m.rod(3.0, 4.6, 1.8, 'trim')
    m.box([1.4, 0.6, 2.2], [2.4, 1.2, 5.2], 'metal')                             # pump pedal
    m.box([-4.6, 6.0, -4.0], [4.6, 7.0, 4.4], 'metal')
    m.box([-4.4, 7.0, -3.6], [4.4, 9.2, 4.6], 'pad')
    blob(m, (0.0, 9.2, 0.6), (4.2, 0.6, 4.0), 'pad')
    tilt = dict(axis='x', angle=-22.5, origin=[0.0, 8.6, -3.8])
    m.box([-4.4, 8.6, -5.0], [4.4, 19.0, -3.4], 'metal', rot=tilt)
    m.box([-3.9, 9.2, -3.4], [3.9, 18.4, -2.4], 'pad', rot=tilt)
    m.rod(19.0, 23.0, 0.3, 'metal', cx=0.0, cz=-8.6)                             # headrest bar
    m.box([-2.6, 21.6, -9.4], [2.6, 23.8, -7.6], 'pad')
    for s in (-1, 1):
        x0, x1 = sorted([s * 4.6, s * 5.6])
        m.box([x0, 7.0, -3.6], [x1, 11.4, 4.0], 'trim')
        m.box([x0 - 0.2, 11.4, -3.6], [x1 + 0.2, 12.2, 5.4], 'arm')
        blob(m, ((x0 + x1) / 2, 11.8, 5.4), (0.8, 0.6, 0.8), 'arm')
    m.seg((0.0, 6.4), (0.0, 3.0), 0.8, 0.8, 'metal', z=7.0)                      # foot rest arm
    m.box([-0.4, 4.0, 4.4], [0.4, 6.6, 7.2], 'metal')
    m.box([-3.2, 2.4, 6.6], [3.2, 3.0, 10.0], 'trim')


def diner_booth(m, k=0):
    """A diner booth: two benches facing across a table, each with a channel-tufted back and a seat cushion on a
    chrome-kicked base; the table with its laminate top, chrome edge and pedestal; mugs, a napkin dispenser, salt
    and pepper and a sauce bottle on it."""
    for s in (-1, 1):
        x0, x1 = sorted([s * 6.0, s * 12.0])
        m.box([x0, 0.0, -8.0], [x1, 1.0, 8.0], 'chrome')
        m.box([x0, 1.0, -8.0], [x1, 6.6, 8.0], 'vinyl')
        blob(m, (s * 8.4, 6.6, 0.0), (2.6, 0.7, 7.8), 'vinyl')
        bx0, bx1 = sorted([s * 10.6, s * 12.0])
        m.box([bx0, 6.6, -8.0], [bx1, 18.0, 8.0], 'back')
        blob(m, (s * 11.3, 18.0, 0.0), (0.9, 0.7, 7.9), 'vinyl')
        m.box([bx0 - 0.05, 17.4, -8.05], [bx1 + 0.05, 18.0, 8.05], 'chrome')
    m.rod(0.0, 0.4, 2.4, 'chrome')
    m.rod(0.4, 10.4, 0.6, 'chrome')
    m.box([-4.8, 10.4, -7.0], [4.8, 11.0, 7.0], 'top')
    m.box([-4.9, 10.3, -7.1], [4.9, 10.6, 7.1], 'chrome')
    for x, z in ((-2.6, 3.0), (2.4, -2.6)):
        m.rod(11.0, 12.6, 0.6, 'mug', cx=x, cz=z)
        m.box([x + 0.5, 11.3, z - 0.1], [x + 1.0, 12.2, z + 0.1], 'mug')
    m.box([-1.0, 11.0, -6.0], [1.0, 13.2, -5.0], 'chrome')                         # napkins
    m.box([-0.8, 11.2, -5.0], [0.8, 13.0, -4.95], 'napkin')
    m.rod(11.0, 12.6, 0.35, 'salt', cx=1.6, cz=-5.6)
    m.rod(11.0, 12.6, 0.35, 'pepper', cx=2.4, cz=-5.6)
    m.rod(11.0, 14.0, 0.55, 'sauce', cx=-2.2, cz=-5.6)
    m.rod(14.0, 14.8, 0.25, 'sauce', cx=-2.2, cz=-5.6)


BOTTLES = ['g1', 'g2', 'g3', 'g4']


def home_bar(m, k=0):
    """A home bar: the panelled counter with its padded arm rest, brass foot rail and top, the beer tap tower, and
    the back bar behind it with mirror panels, two shelves of bottles and glasses."""
    L = 12.0
    m.box([-L, 0.0, 1.0], [L, 15.6, 6.0], 'wood')
    for x in (-8.0, 0.0, 8.0):
        m.box([x - 3.4, 2.0, 6.0], [x + 3.4, 13.6, 6.3], 'panel')
    m.box([-L - 0.4, 15.6, 0.6], [L + 0.4, 16.6, 7.0], 'wood2')
    m.box([-L - 0.4, 15.0, 6.0], [L + 0.4, 16.0, 7.2], 'pad')                     # arm rest
    m.rod(-L, L, 0.3, 'brass', cx=2.4, cz=7.6, axis=0)                           # foot rail
    for x in (-10.0, 0.0, 10.0):
        m.box([x - 0.2, 2.0, 6.3], [x + 0.2, 2.6, 7.8], 'brass')
    m.rod(16.6, 20.0, 0.5, 'brass', cx=-6.0, cz=3.6)                              # taps
    m.rod(-7.6, -4.4, 0.4, 'brass', cx=19.6, cz=3.6, axis=0)
    for x in (-7.2, -6.0, -4.8):
        m.box([x - 0.12, 18.2, 3.6], [x + 0.12, 19.6, 4.4], 'brass')
        m.box([x - 0.2, 19.6, 3.8], [x + 0.2, 21.0, 4.2], 'dark')
    for x in (3.0, 4.4):
        m.rod(16.6, 18.2, 0.5, 'glass', cx=x, cz=3.0)
    m.box([-L, 0.0, -6.0], [L, 10.0, -1.0], 'wood')                              # back bar
    m.box([-L, 10.0, -6.0], [L, 29.0, -5.2], 'wood')
    m.box([-L + 1.0, 11.0, -5.2], [L - 1.0, 28.0, -5.1], 'mirror')
    for y in (10.0, 17.6):
        m.box([-L, y, -6.0], [L, y + 0.6, -2.8], 'wood2')
        for q in range(10):
            x = -L + 1.6 + q * 2.3
            g = BOTTLES[(q * 3 + int(y)) % 4]
            hgt = 4.2 + (q % 3) * 0.6
            m.rod(y + 0.6, y + 0.6 + hgt, 0.55, g, cx=x, cz=-4.2)
            m.rod(y + 0.6 + hgt, y + 1.8 + hgt, 0.22, g, cx=x, cz=-4.2)
            m.rod(y + 1.6 + hgt, y + 2.0 + hgt, 0.26, 'cap', cx=x, cz=-4.2)
    m.box([-L, 28.4, -6.4], [L, 29.4, -2.4], 'wood2')


def wood_stove(m, k=0):
    """A cast-iron cook stove: the body on its cabriole legs, the cooktop with four lids, the firebox door with
    its draught, the oven door and thermometer, the ash drawer, the warming closet on the back, the flue pipe and
    damper, a kettle and a pot on top."""
    for x in (-8.0, 8.0):
        for z in (-4.6, 4.6):
            m.rod(0.0, 3.0, 0.5, 'iron', cx=x, cz=z)
            m.rod(0.0, 0.4, 0.8, 'iron', cx=x, cz=z)
    m.box([-9.0, 3.0, -5.4], [9.0, 12.0, 5.4], 'body')
    m.box([-9.4, 12.0, -5.8], [9.4, 12.8, 5.8], 'iron')                           # cooktop
    for x, z in ((-5.0, -2.6), (-5.0, 2.6), (0.0, -2.6), (0.0, 2.6)):
        m.rod(12.8, 13.0, 1.8, 'iron', cx=x, cz=z)
        m.box([x - 0.3, 13.0, z - 0.1], [x + 0.3, 13.3, z + 0.1], 'trim')
    m.box([-8.6, 5.0, 5.4], [-3.6, 11.0, 5.8], 'iron')                            # firebox door
    m.box([-8.0, 6.0, 5.8], [-4.2, 7.0, 5.9], 'glow')                             # the draught, embers showing
    m.rod(5.8, 6.2, 0.9, 'trim', cx=-6.1, cz=9.6, axis=2)
    m.box([-3.0, 4.0, 5.4], [8.4, 11.4, 5.8], 'door')                             # oven door
    m.rod(5.8, 6.1, 0.8, 'dial', cx=2.7, cz=9.8, axis=2)
    m.box([-1.0, 10.4, 5.8], [6.4, 10.8, 6.4], 'trim')
    m.box([-8.6, 3.2, 5.4], [-3.6, 4.6, 5.8], 'trim')                            # ash drawer
    m.box([-9.0, 12.8, -5.8], [9.0, 22.0, -4.0], 'body')                          # warming closet
    m.box([-9.4, 22.0, -6.2], [9.4, 22.6, -1.8], 'iron')
    m.box([-8.0, 14.0, -4.0], [8.0, 20.4, -3.7], 'door')
    m.rod(22.6, 32.0, 1.6, 'iron', cx=6.0, cz=-4.4)                               # flue
    m.rod(26.0, 26.4, 1.8, 'iron', cx=6.0, cz=-4.4)
    blob(m, (-5.0, 14.8, 2.6), (1.6, 1.4, 1.6), 'kettle')                        # kettle
    m.seg((-5.0, 16.2), (-5.0, 17.0), 0.3, 0.3, 'kettle', z=2.6)
    m.seg((-3.6, 15.0), (-2.6, 16.0), 0.4, 0.4, 'kettle', z=2.6)
    m.rod(13.0, 15.6, 1.6, 'pot', cx=0.0, cz=-2.6)
    m.rod(15.6, 15.8, 1.7, 'pot', cx=0.0, cz=-2.6)


def persian_rug(m, k=0):
    """A Persian rug: the woven face with its border, field and medallion, the knotted fringe at each end."""
    W, D = 17.0, 11.0
    m.box([-W, 0.0, -D], [W, 0.3, D], 'rug')
    for x0 in (-W - 1.0, W):
        m.box([x0, 0.0, -D + 0.4], [x0 + 1.0, 0.12, D - 0.4], 'fringe')


VEND = [  # body, header word, header colours, products: 'cans' or 'bags'
    ('#c8202a', 'COLD DRINKS', '#f0ece0', 'cans'),
    ('#2a4ae8', 'ICE COLD', '#f0ece0', 'cans'),
    ('#1e1f20', 'SNACKS', '#f0c020', 'bags'),
    ('#e86a1a', 'SNACKS', '#f8f0e0', 'bags'),
    ('#2a8a3a', 'SODA', '#f0ece0', 'cans'),
    ('#5a3a2a', 'CANDY', '#f0c020', 'bags'),
]


def vending_machine(m, k=0):
    """A vending machine: the tall cabinet with the lit header, the window recess lit inside with its spiral
    shelves of drink cans or snack bags, the selection panel with its keypad, coin and note slots and the price
    display, the push flap of the delivery bin, the kick plate."""
    cans = VEND[k % len(VEND)][3] == 'cans'
    m.box([-7.5, 0.0, -6.0], [7.5, 29.0, 4.0], 'body')
    for x0, x1, y0, y1 in ((-7.5, -7.0, 0.0, 29.0), (-7.0, 2.6, 0.0, 6.0), (-7.0, 2.6, 25.4, 29.0),
                           (2.6, 7.5, 0.0, 29.0)):
        m.box([x0, y0, 4.0], [x1, y1, 6.0], 'body')
    m.box([-7.6, 0.0, 5.0], [7.6, 1.2, 6.2], 'dark')
    m.box([-7.0, 26.0, 6.0], [7.0, 28.6, 6.2], 'glow3')                           # header
    m.box([-7.0, 6.0, 4.0], [2.6, 25.4, 4.05], 'dark')                            # window recess
    m.box([-7.0, 6.0, 5.8], [2.6, 6.6, 6.2], 'trim')
    m.box([-7.0, 24.8, 5.8], [2.6, 25.4, 6.2], 'trim')
    m.box([-7.0, 6.0, 5.8], [-6.4, 25.4, 6.2], 'trim')
    m.box([2.0, 6.0, 5.8], [2.6, 25.4, 6.2], 'trim')
    m.box([-6.4, 24.4, 4.0], [2.0, 24.8, 5.2], 'glow')                            # inside light
    for r in range(5):
        y = 8.0 + r * 3.4
        m.box([-6.4, y - 0.3, 3.0], [2.0, y, 5.6], 'metal')
        for q in range(5):
            x = -5.4 + q * 1.6
            mat = f'prod{(q + r) % 4}'
            if cans:
                m.rod(y, y + 2.4, 0.62, mat, cx=x, cz=4.6)
            else:
                m.box([x - 0.7, y, 4.2], [x + 0.7, y + 2.6, 5.0], mat)
            m.box([x - 0.5, y - 0.6, 5.6], [x + 0.5, y - 0.3, 5.7], 'label')
    m.box([2.6, 6.0, 6.0], [7.0, 25.4, 6.2], 'trim')                              # selection panel
    m.box([3.2, 21.0, 6.2], [6.4, 22.6, 6.3], 'glow2')
    for r in range(4):
        for q in range(3):
            m.box([3.4 + q * 1.0, 13.0 + r * 1.4, 6.2], [4.1 + q * 1.0, 13.8 + r * 1.4, 6.45], 'btn')
    m.box([4.2, 18.6, 6.2], [5.4, 20.0, 6.35], 'metal')                          # coin slot
    m.box([4.6, 19.1, 6.35], [5.0, 19.6, 6.4], 'dark')
    m.box([3.4, 10.0, 6.2], [6.2, 11.2, 6.35], 'metal')                          # note slot
    m.box([3.8, 10.5, 6.35], [5.8, 10.7, 6.4], 'dark')
    m.box([-6.4, 2.0, 6.0], [4.0, 5.0, 6.4], 'metal')                            # delivery flap
    m.box([-6.0, 2.4, 6.4], [3.6, 4.6, 6.45], 'dark')


def massage_chair(m, k=0):
    """A full-body massage chair: the moulded side shells, the seat, the deep back with its roller cushions and
    padded head pillow, the arm sleeves, the leg and calf rest angled down at the front, the corded remote."""
    for s in (-1, 1):
        x0, x1 = sorted([s * 5.2, s * 7.4])
        m.box([x0, 2.0, -6.0], [x1, 12.0, 5.0], 'shell')
        blob(m, ((x0 + x1) / 2, 12.0, -0.6), (1.6, 1.2, 5.6), 'pad')               # arm sleeves
    m.box([-7.4, 0.0, -6.4], [7.4, 2.0, 5.0], 'shell')
    m.box([-5.2, 2.0, -5.0], [5.2, 7.2, 5.0], 'shell')
    m.box([-5.0, 7.2, -4.0], [5.0, 9.0, 5.0], 'pad')
    tilt = dict(axis='x', angle=-22.5, origin=[0.0, 8.0, -5.0])
    m.box([-6.0, 8.0, -7.6], [6.0, 26.0, -5.0], 'shell', rot=tilt)
    for y in (9.0, 12.2, 15.4, 18.6):
        m.box([-4.6, y, -5.0], [4.6, y + 2.8, -3.4], 'pad', rot=tilt)
    m.box([-3.6, 22.0, -5.0], [3.6, 25.0, -3.0], 'pad', rot=tilt)
    leg = dict(axis='x', angle=45, origin=[0.0, 7.6, 5.0])
    m.box([-4.6, 7.6, 5.0], [4.6, 9.6, 14.0], 'shell', rot=leg)
    for x in (-2.2, 2.2):
        m.box([x - 1.6, 9.6, 5.4], [x + 1.6, 10.6, 13.6], 'pad', rot=leg)
    m.box([7.4, 8.0, 3.0], [8.2, 11.0, 4.0], 'dark')                               # remote
    m.seg((7.8, 8.0), (7.6, 6.0), 0.2, 0.2, 'dark', z=3.4)


def grandfather_clock(m, k=0):
    """A longcase clock: the plinth base with bracket feet, the waist with its glazed door frame showing the brass
    pendulum and the three weights on their chains, the hood with columns and the arched bonnet and finials, the
    dial with its hands, the moon arch."""
    m.box([-5.6, 0.0, -3.6], [5.6, 1.0, 3.6], 'wood')
    m.box([-5.2, 1.0, -3.2], [5.2, 7.0, 3.2], 'wood')
    m.box([-5.6, 7.0, -3.6], [5.6, 7.8, 3.6], 'wood2')
    m.box([-4.4, 7.8, -2.8], [4.4, 21.0, 2.0], 'wood')                            # waist
    m.box([-4.4, 7.8, 2.0], [-3.6, 21.0, 2.6], 'wood2')
    m.box([3.6, 7.8, 2.0], [4.4, 21.0, 2.6], 'wood2')
    m.box([-3.6, 7.8, 2.0], [3.6, 8.6, 2.6], 'wood2')
    m.box([-3.6, 20.2, 2.0], [3.6, 21.0, 2.6], 'wood2')
    m.box([-3.6, 8.6, 1.6], [3.6, 20.2, 1.7], 'back')                             # case back seen through
    m.rod(9.0, 19.4, 0.12, 'brass', cx=0.0, cz=1.0)                                # pendulum rod
    m.rod(0.6, 1.4, 1.5, 'brass', cx=0.0, cz=10.4, axis=2)                         # bob
    for x in (-2.2, 0.0, 2.2):
        m.rod(17.6, 20.2, 0.05, 'brass', cx=x, cz=-1.0)
        m.rod(13.6 + abs(x) * 0.4, 17.6, 0.6, 'brass', cx=x, cz=-1.0)              # weights
    m.box([-5.0, 21.0, -3.2], [5.0, 21.8, 3.0], 'wood2')
    m.box([-4.6, 21.8, -2.8], [4.6, 30.0, 2.6], 'wood')                            # hood
    for x in (-4.6, 4.0):
        m.rod(21.8, 29.0, 0.35, 'brass', cx=x + 0.3, cz=2.4)
    m.rod(2.6, 2.7, 3.4, 'dial', cx=0.0, cz=25.2, axis=2)
    m.box([-0.08, 25.2, 2.7], [0.08, 27.6, 2.78], 'hand')
    m.box([0.0, 25.12, 2.78], [1.8, 25.28, 2.84], 'hand')
    m.rod(2.6, 2.68, 1.6, 'moon', cx=0.0, cz=28.8, axis=2)
    m.box([-5.0, 30.0, -3.2], [5.0, 30.6, 3.0], 'wood2')
    for x in (-3.8, 0.0, 3.8):
        blob(m, (x, 31.2, 0.0), (0.5, 0.7, 0.5), 'brass')


def stuffed_bear(m, k=0):
    """A taxidermy bear standing on its hind legs on a rock and timber base: the legs, the heavy body, the hump,
    the arms raised forward with claws, the head with its muzzle, open mouth and ears."""
    m.box([-8.0, 0.0, -6.0], [8.0, 1.6, 6.0], 'wood')
    blob(m, (1.0, 2.4, -1.0), (6.0, 1.6, 4.4), 'rock')
    for s in (-1, 1):
        blob(m, (s * 2.4, 6.0, 0.0), (2.0, 4.6, 2.2), 'fur')                     # legs
        blob(m, (s * 2.4, 2.0, 1.4), (1.6, 0.9, 2.2), 'fur')                     # feet
        for q in range(4):
            m.box([s * 2.4 - 1.2 + q * 0.7, 1.6, 3.4], [s * 2.4 - 0.9 + q * 0.7, 2.0, 4.0], 'claw')
    blob(m, (0.0, 14.0, -0.4), (5.0, 6.8, 4.2), 'fur')                           # body
    blob(m, (0.0, 19.4, -1.6), (4.0, 3.0, 3.4), 'fur')                           # hump
    blob(m, (0.0, 15.0, 2.4), (3.6, 4.8, 1.8), 'fur2')                           # chest
    for s in (-1, 1):
        m.seg((s * 4.0, 19.0), (s * 6.0, 22.6), 2.6, 2.6, 'fur', z=1.4)
        blob(m, (s * 6.2, 23.4, 2.6), (1.4, 1.4, 1.8), 'fur')                    # paws
        for q in range(4):
            m.box([s * 6.2 - 1.0 + q * 0.6, 24.4, 3.6], [s * 6.2 - 0.8 + q * 0.6, 25.4, 3.9], 'claw')
    blob(m, (0.0, 24.4, 0.6), (2.8, 2.6, 2.6), 'fur')                            # head
    blob(m, (0.0, 23.6, 3.4), (1.4, 1.2, 1.8), 'fur2')                           # muzzle
    blob(m, (0.0, 24.0, 5.0), (0.6, 0.5, 0.3), 'nose')
    m.box([-0.9, 22.4, 2.6], [0.9, 23.2, 4.6], 'mouth')
    for s in (-1, 1):
        blob(m, (s * 1.3, 25.4, 2.8), 0.3, 'eye')
        blob(m, (s * 2.2, 26.8, 0.0), (0.8, 0.8, 0.5), 'fur')


def theatre_throne(m, k=0):
    """A stage throne: the gilded frame on lion-paw feet, the deep red velvet seat with its fringe, the arms
    ending in carved lion heads, the tall padded back in its gilt surround, the crown crest on top with the
    rampant lions either side."""
    for x in (-6.4, 6.4):
        for z in (-4.6, 4.6):
            blob(m, (x, 0.8, z + 0.6), (1.2, 0.8, 1.4), 'gilt')
            m.rod(1.2, 6.0, 0.7, 'gilt', cx=x, cz=z)
    m.box([-7.0, 6.0, -5.4], [7.0, 8.0, 5.6], 'gilt')
    m.box([-6.4, 5.0, 5.6], [6.4, 7.0, 5.8], 'fringe')
    m.box([-6.0, 8.0, -4.6], [6.0, 9.6, 5.4], 'velvet')
    blob(m, (0.0, 9.6, 0.6), (5.8, 0.8, 4.8), 'velvet')
    for s in (-1, 1):
        x0, x1 = sorted([s * 6.0, s * 7.4])
        m.box([x0, 8.0, -4.6], [x1, 12.0, 4.4], 'gilt')
        m.box([x0 + 0.2, 12.0, -4.6], [x1 - 0.2, 12.6, 4.4], 'velvet')
        blob(m, ((x0 + x1) / 2, 12.6, 5.2), (1.0, 1.2, 1.2), 'gilt')                 # lion heads
        blob(m, ((x0 + x1) / 2, 12.2, 6.2), (0.5, 0.5, 0.5), 'gilt')
    m.box([-7.4, 8.0, -6.4], [7.4, 27.0, -4.6], 'gilt')                               # back frame
    m.box([-5.8, 9.6, -4.6], [5.8, 25.0, -3.6], 'velvet')
    blob(m, (0.0, 20.0, -3.8), (3.0, 3.4, 0.4), 'gilt')                               # crest medallion
    m.box([-1.6, 18.0, -3.5], [1.6, 22.0, -3.3], 'monogram')
    for s in (-1, 1):
        blob(m, (s * 7.0, 28.0, -5.4), (1.4, 2.4, 1.2), 'gilt')                       # rampant lions
        blob(m, (s * 6.4, 30.6, -5.4), (0.9, 1.0, 0.9), 'gilt')
    m.rod(27.0, 28.4, 2.2, 'gilt', cx=0.0, cz=-5.4)                                    # crown
    blob(m, (0.0, 29.6, -5.4), (2.0, 1.6, 1.6), 'velvet')
    for a in range(5):
        x = -1.8 + a * 0.9
        m.box([x - 0.2, 28.4, -5.6], [x + 0.2, 30.4, -5.2], 'gilt')
    blob(m, (0.0, 31.2, -5.4), 0.5, 'gilt')
    m.box([-0.1, 31.4, -5.5], [0.1, 32.0, -5.3], 'gilt')


# ================================================================== looks


def _looks():
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.4, chips=0.3))      # noqa: E731
    wood = lambda a, b: (lambda f: P.wood(f, a, b))                              # noqa: E731
    leather = lambda c: (lambda f: P.leather(f, c))                             # noqa: E731
    dark = A.plastic('#1e1f20')
    out = {}
    out['bean_bag'] = [dict(b=bean_bag, cloth=cl, trim=A.fabric('#1e1f20'))
                       for cl in (check('#e8e4da', '#3a3a3a', 3.0), A.fabric('#e8501a'), A.fabric('#2a4ae8'),
                                  A.fabric('#c8202a'), A.fabric('#2a8a3a'), leather('#3a2414'), A.fabric('#6a2a8a'),
                                  A.fabric('#8a8a84'))]
    out['bar_stools'] = [dict(b=bar_stools, wood=wd, seat=st, metal=A.chrome, dark=dark)
                         for wd, st in ((wood('#4a2414', '#2a1408'), A.fabric('#e8dcc0')),
                                        (wood('#e8d0a0', '#c8a878'), A.fabric('#1a4a4a')),
                                        (A.chrome, leather('#1e1f20')), (A.chrome, A.skin('#c8202a')),
                                        (A.chrome, A.skin('#2a8a8a')))]
    out['hand_truck'] = [dict(b=hand_truck, frame=paint('#c8302a'), grip=A.plastic('#141414'), plate=A.steel,
                              metal=A.steel, tyre=A.plastic('#1a1a1a'), hub=A.plastic('#c8c8cc'))]
    out['recliner'] = [dict(b=recliner, cover=cv, dark=dark)
                       for cv in (leather('#1e1f20'), leather('#4a2414'), A.fabric('#6a5a48'), A.fabric('#3a4a6a'),
                                  leather('#7a1a1a'), A.fabric('#5a6a3a'))]
    out['hammock'] = [dict(b=hammock, wood=wood('#c9a77a', '#8a6440'), metal=A.steel, rope=A.fabric('#d8c8a0'),
                           bed=bd)
                      for bd in (rope_net('#d89a7a'), stripes('#e8e4da', '#2a6ae8'), stripes('#f0c020', '#c8302a'),
                                 A.fabric('#d8ccb0'))]
    out['velvet_rope_stands'] = [dict(b=velvet_rope_stands, post=pt, rope=A.fabric(rp))
                                 for pt, rp in ((P.brass, '#a8181f'), (A.chrome, '#1e3a8a'), (A.chrome, '#141414'))]
    out['mounted_deer_head'] = [dict(b=mounted_deer_head, plaque=wood(a, b2), plaque2=wood(A.hexof(A.tone(a, 1.15)), a),
                                     hide=fur('#8a5a34', '#6a4024'), pale=A.flat('#e8dcc8'), nose=A.flat('#141414'),
                                     eye=A.flat('#1a0a04', 0.01), antler=fur('#c8b088', '#8a6a48'))
                                for a, b2 in (('#6a3a1a', '#3a1a0a'), ('#c9a77a', '#8a6440'), ('#3a2414', '#1a0a04'),
                                              ('#8a5a34', '#5a3a1a'))]
    out['waterbed'] = [dict(b=waterbed, plinth=wood('#6a3a1a', '#3a1a0a'), wood=wood('#8a5a34', '#5a3a1a'),
                            rail=leather('#3a2414'), pad=leather('#4a2a1a'), vinyl=baffles(vn), sheet=A.fabric('#f0ece0'),
                            pillow=A.fabric('#f0ece0'))
                       for vn in ('#1e3a7a', '#a81a1a')]
    out['barber_chair'] = [dict(b=barber_chair, metal=mt, trim=tr, pad=pd, arm=ar)
                           for mt, tr, pd, ar in ((P.brass, wood('#6a3a1a', '#3a1a0a'), leather('#5a1a14'),
                                                   A.flat('#f0ece0')),
                                                  (P.brass, paint('#c8a040'), A.fabric('#1a4a2a'), A.flat('#f0ece0')))]
    out['diner_booth'] = [dict(b=diner_booth, chrome=A.chrome, vinyl=A.skin(vc), back=tufted(vc, ('left', 'right')),
                               top=A.flat(tp, 0.1), mug=A.flat('#f0ece0'), napkin=A.flat('#f8f8f8'),
                               salt=A.flat('#f0f0f0'), pepper=A.flat('#3a3a3a'), sauce=A.plastic('#c8202a'))
                          for vc, tp in (('#c8202a', '#9aa8a8'), ('#d8786a', '#c8c0a8'), ('#2a9a9a', '#e8e4da'),
                                         ('#1e1f20', '#c8202a'))]
    out['home_bar'] = [dict(b=home_bar, wood=wd, wood2=wd2, panel=wd2, pad=leather('#1e1f20'), brass=br,
                            dark=dark, glass=A.flat('#e8d080'), mirror=A.flat('#8a96a0', 0.03),
                            g1=A.flat('#4a2a0a'), g2=A.flat('#2a5a2a'), g3=A.flat('#c8a060'), g4=A.flat('#d8dcd8'),
                            cap=A.flat('#c8a040'))
                       for wd, wd2, br in ((wood('#5a1a10', '#2a0a04'), wood('#7a2a14', '#4a1408'), P.brass),
                                           (wood('#c9a77a', '#8a6440'), wood('#a8865a', '#6a4a28'), P.brass),
                                           (paint('#1e1f20'), paint('#2a2a2a'), A.chrome))]
    out['wood_stove'] = [dict(b=wood_stove, iron=lambda f: P.cast_iron(f), body=bd, door=dr, trim=A.chrome,
                              dial=dial('#f0ece0'), kettle=A.flat('#c8c8c0', 0.08), pot=paint('#2a2a2a'),
                              glow=A.flat('#b8481a', 0.1))
                         for bd, dr in ((lambda f: P.cast_iron(f), lambda f: P.cast_iron(f)),
                                        (paint('#e8dcc0'), paint('#3a6a4a')), (paint('#5a6a7a'), paint('#3a4a5a')))]
    out['persian_rug'] = [dict(b=persian_rug, rug=rug(f_, b_, a_, i_), fringe=A.fabric('#e8dcc8'), _px={'rug': 6})
                          for f_, b_, a_, i_ in (('#8a1a1a', '#1e2a4a', '#e8c890', '#141414'),
                                                 ('#e8dcc0', '#8a2a2a', '#2a4a6a', '#3a2a1a'),
                                                 ('#1e2a4a', '#8a1a1a', '#e8c890', '#e8dcc0'),
                                                 ('#6a1a2a', '#e8c890', '#1a4a3a', '#141414'),
                                                 ('#2a4a3a', '#8a5a2a', '#e8c890', '#1a1a1a'),
                                                 ('#c8862a', '#3a1a1a', '#e8dcc0', '#1a1a1a'),
                                                 ('#3a2a4a', '#c8a040', '#a83a3a', '#e8dcc0'),
                                                 ('#a8582a', '#2a2a3a', '#e8d4a8', '#1a1a1a'))]
    out['vending_machine'] = [dict(b=vending_machine, body=paint(bd), dark=A.plastic('#141414'), trim=A.chrome,
                                   metal=A.steel, glow=neon('#f8f4f0'), glow2=neon('#40e060'),
                                   glow3=sign(word, bd, ink, frame=ink, h=0.55), btn=A.plastic('#e8e4da'),
                                   label=A.flat('#f0ece0'), prod0=A.plastic('#c8202a'), prod1=A.plastic('#2a4ae8'),
                                   prod2=A.plastic('#f0c020'), prod3=A.plastic('#2a8a3a'), _px={'glow3': 10})
                              for bd, word, ink, _ in VEND]
    out['massage_chair'] = [dict(b=massage_chair, shell=sh_, pad=pd, dark=dark)
                            for sh_, pd in ((A.plastic('#2a2a2a'), leather('#141414')),
                                            (A.plastic('#e8e4da'), leather('#6a4a2a')))]
    out['grandfather_clock'] = [dict(b=grandfather_clock, wood=wd, wood2=wd2, back=A.flat('#2a1a10'), brass=P.brass,
                                     dial=dial(), hand=A.flat('#141414'), moon=A.flat('#2a3a6a'), _px={'dial': 12})
                                for wd, wd2 in ((wood('#5a1a10', '#2a0a04'), wood('#7a2a14', '#4a1408')),
                                                (wood('#6a4428', '#3a2414'), wood('#8a5a34', '#5a3a1a')),
                                                (paint('#141414'), paint('#2a2a2a')))]
    out['stuffed_bear'] = [dict(b=stuffed_bear, wood=wood('#6a3a1a', '#3a1a0a'), rock=A.flat('#7a7a72', 0.2),
                                fur=fur(a, b2), fur2=fur(A.hexof(A.tone(a, 1.25)), a), claw=A.flat('#d8ccb0'),
                                nose=A.flat('#141414'), mouth=A.flat('#5a1a1a'), eye=A.flat('#1a0a04', 0.01))
                           for a, b2 in (('#7a4a24', '#4a2a10'), ('#2a2422', '#141210'))]
    out['theatre_throne'] = [dict(b=theatre_throne, gilt=P.brass, velvet=A.fabric('#8a1018'),
                                  fringe=A.fabric('#c8a040'), monogram=sign('R', '#8a1018', '#e8c860', h=0.8))]
    for ls in out.values():
        for k, d in enumerate(ls):
            d['b'] = (lambda fn, k: (lambda m: fn(m, k)))(d['b'], k)
    return out


def looks(it, wrap):
    return {i: [wrap(it, d) for d in ls] for i, ls in _looks().items()}
