"""Wave 5 decor models, batch 2: games and leisure (wave 4 stage 5; specs in decor.py). Built from the photos in
refs/items/decor/<id>/ (bowling_ball_and_pins, home_console_bundle and cocktail_arcade_table hold the photos for
bowling_ball_pins, home_console and cocktail_arcade).

Same conventions as wave5decor: model units standing on y = 0, front toward +z, glow materials (GLOW) light up in
the placed block's ON model. The small pieces are built a little over life size like batch 1; the big tables and
cabinets are built at the world's scale (about 0.4 units an inch) so a pool table is two blocks long.

The boards, screens, felts and signs are painted on their faces (face_paint): face-local coordinates, so a
dartboard's sectors or a chess board's squares come out right whatever size the piece is drawn at.
"""
import math

import numpy as np

import art as A
import paint as P
import shapes as sh
from wave4 import blob, flat, flat_arc

GLOW = ('glow', 'glow2', 'glow3', 'glow4')


def neon(c):
    return A.flat(c, 0.03)


# ================================================================== face painters


FACE_KEYS = {(2, 1): 'front', (2, -1): 'back', (0, 1): 'right', (0, -1): 'left', (1, 1): 'top', (1, -1): 'bottom'}


def face_paint(draw, base, faces=('front',), noise=0.04):
    """A material that paints draw(u, v, W, H, c, f) -> c on the named faces and the plain base elsewhere.
    u runs right and v down across the face, in model units, W x H the face's size."""
    def fn(f):
        c = A.plain(f, base, noise)
        ax = int(np.argmax(np.abs(f.n)))
        key = FACE_KEYS[(ax, 1 if f.n[ax] > 0 else -1)]
        if key not in faces and 'all' not in faces:
            return c
        d = f.dims
        W, H = {2: (d[0], d[1]), 0: (d[2], d[1]), 1: (d[0], d[2])}[ax]
        return draw(f.uv[..., 0], f.uv[..., 1], W, H, c, f)
    return fn


def put(c, mask, colour):
    return np.where(mask[..., None], P.col(colour) if isinstance(colour, str) else colour, c)


def text(c, u, v, word, cu, cv, h, ink):
    return put(c, A.text_mask(u, v, word, cu, cv, h), ink)


def sign(word, base, ink, frame=None, h=0.6, faces=('front',)):
    """A printed sign: the word as big as fits, centred, on the base colour, in an optional frame line."""
    def draw(u, v, W, H, c, f):
        if frame:
            e = min(W, H) * 0.08
            c = put(c, (u < e) | (u > W - e) | (v < e) | (v > H - e), frame)
        th = A.fit_text(word, W * 0.86, H * h)
        return text(c, u, v, word, W / 2, H / 2, th, ink)
    return face_paint(draw, base, faces, 0.03)


def score(digits, base, ink, faces=('front',)):
    """A score display: segment-style digits across a dark window."""
    def draw(u, v, W, H, c, f):
        th = A.fit_text(digits, W * 0.9, H * 0.7)
        c = put(c, A.text_mask(u, v, '8' * len(digits), W / 2, H / 2, th), A.tone(ink, 0.25))   # the unlit segments
        return text(c, u, v, digits, W / 2, H / 2, th, ink)
    return face_paint(draw, base, faces, 0.02)


def dartboard_face(sa, sb, ra, rb, rings_only=False):
    """A bristle board on a round rod's front: numbers ring, doubles, outer singles, trebles, inner singles,
    outer bull and bull, the twenty sectors alternating, the spider wire between them."""
    def draw(u, v, W, H, c, f):
        x, y = u - W / 2, v - H / 2
        R = f.box.get('round', min(W, H) / 2)
        r = np.hypot(x, y) / R
        a = (np.degrees(np.arctan2(-y, x)) + 360 + 9) % 360
        sec = (a // 18).astype(int)
        odd = sec % 2 == 1
        if rings_only:          # the practice board: plain rings, green and white
            band = (r * 10).astype(int) % 2 == 0
            c = put(c, (r < 0.8) & band, sa)
            c = put(c, (r < 0.8) & ~band, sb)
            c = put(c, r < 0.08, ra)
            c = put(c, (r >= 0.8) & (r < 1.0), '#141414')
        else:
            c = put(c, (r < 1.0), '#141414')
            single = ((r >= 0.1) & (r < 0.43)) | ((r >= 0.47) & (r < 0.74))
            c = put(c, single & odd, sa)
            c = put(c, single & ~odd, sb)
            ring = ((r >= 0.43) & (r < 0.47)) | ((r >= 0.74) & (r < 0.8))
            c = put(c, ring & odd, ra)
            c = put(c, ring & ~odd, rb)
            c = put(c, (r >= 0.04) & (r < 0.1), rb)
            c = put(c, r < 0.04, ra)
            spoke = (np.abs(((a % 18) - 0) * np.pi / 180 * r * R) < 0.06) & (r > 0.1) & (r < 0.8)
            wires = spoke | (np.abs(r - 0.43) < 0.008) | (np.abs(r - 0.47) < 0.008) | (np.abs(r - 0.74) < 0.008) \
                | (np.abs(r - 0.8) < 0.008) | (np.abs(r - 0.1) < 0.008)
            c = put(c, wires, '#c8ccd0')
        # the numbers: a white tick for each round the ring
        mid = (a % 18 > 7) & (a % 18 < 11) & (r > 0.86) & (r < 0.95)
        return put(c, mid, '#e8e4da')
    return face_paint(draw, '#141414', ('front',), 0.03)


def chess_top(light, dark, border):
    def draw(u, v, W, H, c, f):
        b = W * 0.08
        i = np.floor((u - b) / ((W - 2 * b) / 8)).astype(int)
        j = np.floor((v - b) / ((H - 2 * b) / 8)).astype(int)
        inside = (u > b) & (u < W - b) & (v > b) & (v < H - b)
        c = put(c, inside & ((i + j) % 2 == 0), light)
        c = put(c, inside & ((i + j) % 2 == 1), dark)
        line = inside & ((np.abs(u - b) < W * 0.006) | (np.abs(u - (W - b)) < W * 0.006)
                         | (np.abs(v - b) < H * 0.006) | (np.abs(v - (H - b)) < H * 0.006))
        c = put(c, line, light)
        # the file letters and rank numbers in the border
        for k in range(8):
            cu = b + (k + 0.5) * (W - 2 * b) / 8
            c = text(c, u, v, 'ABCDEFGH'[k], cu, H - b / 2, b * 0.5, light)
            c = text(c, u, v, str(8 - k), b / 2, b + (k + 0.5) * (H - 2 * b) / 8, b * 0.5, light)
        return c
    return face_paint(draw, border, ('top',), 0.05)


def foos_field(grass, line):
    def draw(u, v, W, H, c, f):
        stripe = ((u / W * 10).astype(int) % 2 == 0)
        c = np.where(stripe[..., None], c * 0.92, c)
        t = min(W, H) * 0.012
        lines = (np.abs(u - W / 2) < t) | (np.abs(np.hypot(u - W / 2, v - H / 2) - H * 0.16) < t)
        lines |= (u < t * 3) | (u > W - t * 3) | (v < t * 3) | (v > H - t * 3)
        for x0 in (0, W):
            box = (np.abs(u - x0) < W * 0.12) & (np.abs(v - H / 2) < H * 0.3)
            edge = box & ((np.abs(np.abs(u - x0) - W * 0.12) < t) | (np.abs(np.abs(v - H / 2) - H * 0.3) < t))
            lines |= edge
        return put(c, lines, line)
    return face_paint(draw, grass, ('top',), 0.05)


def pingpong_top(top, line):
    def draw(u, v, W, H, c, f):
        t = min(W, H) * 0.02
        edge = (u < t) | (u > W - t) | (v < t) | (v > H - t) | (np.abs(v - H / 2) < t * 0.4)
        return put(c, edge, line)
    return face_paint(draw, top, ('top',), 0.03)


def hockey_top(red, blue):
    def draw(u, v, W, H, c, f):
        holes = ((u * 6) % 1 < 0.18) & ((v * 6) % 1 < 0.18)
        c = np.where(holes[..., None], c * 0.8, c)
        t = H * 0.012
        c = put(c, np.abs(u - W / 2) < t * 1.5, red)
        c = put(c, (np.abs(u - W * 0.33) < t) | (np.abs(u - W * 0.67) < t), blue)
        rr = np.hypot(u - W / 2, v - H / 2)
        c = put(c, (np.abs(rr - H * 0.18) < t * 1.4), red)
        c = put(c, rr < H * 0.04, blue)
        for x0 in (0, W):                           # goal creases
            c = put(c, np.abs(np.hypot(u - x0, v - H / 2) - H * 0.22) < t * 1.4, red)
        for x0 in (W * 0.18, W * 0.82):             # face-off rings with their cross
            for y0 in (H * 0.28, H * 0.72):
                d = np.hypot(u - x0, v - y0)
                ring = np.abs(d - H * 0.08) < t * 1.2
                cross = (d < H * 0.11) & ((np.abs(u - x0) < t * 0.8) | (np.abs(v - y0) < t * 0.8))
                c = put(c, ring | cross, '#1e1f20')
                c = put(c, d < H * 0.025, blue)
        return c
    return face_paint(draw, '#e8ecee', ('top',), 0.03)


def chalkboard(word):
    """A dart score board: the game's number at the top, two columns ruled down it, chalked scores struck out."""
    def draw(u, v, W, H, c, f):
        t = W * 0.03
        c = put(c, (np.abs(u - W / 2) < t * 0.5) & (v > H * 0.18), '#d8dcd8')
        c = put(c, np.abs(v - H * 0.18) < t * 0.5, '#d8dcd8')
        c = text(c, u, v, word, W / 2, H * 0.09, H * 0.07, '#e8ece8')
        for k, n in enumerate(('441', '360', '301', '220', '180', '140')):
            cv = H * (0.26 + k * 0.11)
            for cu, nn in ((W * 0.25, n), (W * 0.75, str(int(n) - 40 - k * 7))):
                c = text(c, u, v, nn, cu, cv, H * 0.045, '#c8ccc8')
                if k < 5:
                    c = put(c, (np.abs(v - cv) < t * 0.3) & (np.abs(u - cu) < W * 0.14), '#b8bcb8')
        return c
    return face_paint(draw, '#1e2a24', ('all',), 0.08)


def chip_row(colour, edge='#f0ece0'):
    """A row of chips lying in their groove: the coloured chips, each with its white edge spots."""
    def draw(u, v, W, H, c, f):
        n = 18
        k = (u / W * n) % 1
        c = put(c, k < 0.08, A.tone(colour, 0.6))
        spot = (k > 0.3) & (k < 0.55) & (((v / H) * 6) % 1 < 0.35)
        return put(c, spot, edge)
    return face_paint(draw, colour, ('all',), 0.04)


SPRITES = {
    'alien': ['0010100', '0111110', '1101011', '1111111', '1011101', '1000001'],
    'crab': ['1000001', '0111110', '1101011', '1111111', '0100010'],
    'ship': ['0001000', '0011100', '1111111', '1111111'],
    'car': ['0110', '1111', '0110', '1111'],
    'blob': ['0110', '1111', '1111', '0110'],
    'star': ['010', '111', '010'],
}


def stamp(c, u, v, name, x0, y0, p, colour):
    rows = SPRITES[name]
    gx = np.floor((u - x0) / p).astype(int)
    gy = np.floor((v - y0) / p).astype(int)
    on = np.zeros(u.shape, bool)
    for r, row in enumerate(rows):
        for q, b in enumerate(row):
            if b == '1':
                on |= (gx == q) & (gy == r)
    return put(c, on, colour)


def screen(kind, c1, c2, c3, bg='#0a0c14', faces=('front',)):
    """A lit game screen: the game's scene in blocky sprites, the score along the top."""
    def draw(u, v, W, H, c, f):
        p = W / 64
        c = put(c, u > -1, bg)
        if kind == 'invaders':
            for r in range(3):
                for q in range(6):
                    c = stamp(c, u, v, 'alien' if r else 'crab', W * (0.12 + q * 0.13), H * (0.16 + r * 0.13), p,
                              (c1, c2, c2)[r])
            for q in range(3):
                c = put(c, (np.abs(u - W * (0.25 + q * 0.25)) < W * 0.06) & (np.abs(v - H * 0.75) < H * 0.03), c3)
            c = stamp(c, u, v, 'ship', W * 0.45, H * 0.86, p, c3)
        elif kind == 'bricks':
            for r, col in enumerate((c1, c1, c2, c2, c3, c3)):
                rows = (v > H * (0.14 + r * 0.05)) & (v < H * (0.18 + r * 0.05)) & ((u / W * 12) % 1 > 0.08)
                if r == 4:
                    rows &= (u < W * 0.4) | (u > W * 0.55)
                c = put(c, rows, col)
            c = put(c, (np.abs(u - W * 0.55) < W * 0.08) & (np.abs(v - H * 0.9) < H * 0.015), '#e8e8e8')
            c = put(c, (np.abs(u - W * 0.4) < p * 1.2) & (np.abs(v - H * 0.6) < p * 1.2), '#e8e8e8')
        elif kind == 'maze':
            gx, gy = (u / W * 9).astype(int), (v / H * 9).astype(int)
            wall = (((gx % 2 == 0) & (gy % 3 != 1)) | ((gy % 2 == 0) & (gx % 3 != 1))) & (v > H * 0.1)
            fx, fy = (u / W * 9) % 1, (v / H * 9) % 1
            c = put(c, wall & ((fx < 0.12) | (fy < 0.12)), c1)
            dots = ~wall & (np.abs(fx - 0.5) < 0.06) & (np.abs(fy - 0.5) < 0.06) & (v > H * 0.1)
            c = put(c, dots, '#f0e0c0')
            c = stamp(c, u, v, 'blob', W * 0.48, H * 0.52, p * 1.5, c2)
            c = stamp(c, u, v, 'blob', W * 0.25, H * 0.3, p * 1.5, c3)
            c = stamp(c, u, v, 'blob', W * 0.72, H * 0.72, p * 1.5, '#f070b0')
        elif kind == 'road':
            road = np.abs(u - W / 2) < W * 0.3
            c = put(c, ~road, c1)
            c = put(c, road, '#4a4a4e')
            c = put(c, road & (np.abs(u - W / 2) < p) & ((v / H * 8) % 1 < 0.5), '#e8e8e8')
            c = put(c, np.abs(np.abs(u - W / 2) - W * 0.3) < p, '#e8e8e8')
            c = stamp(c, u, v, 'car', W * 0.55, H * 0.72, p * 2, c2)
            c = stamp(c, u, v, 'car', W * 0.32, H * 0.3, p * 2, c3)
        elif kind == 'stars':
            st = (np.sin(u * 37.1) * np.cos(v * 29.3) > 0.985)
            c = put(c, st, '#e8e8f0')
            for q in range(4):
                c = stamp(c, u, v, 'crab', W * (0.15 + q * 0.2), H * (0.2 + (q % 2) * 0.1), p, c2)
            c = stamp(c, u, v, 'ship', W * 0.47, H * 0.82, p, c1)
            c = put(c, (np.abs(u - W * 0.5) < p * 0.5) & (v > H * 0.5) & (v < H * 0.6), c3)
        elif kind == 'blocks':
            well = np.abs(u - W / 2) < W * 0.25
            c = put(c, ~well, A.tone(c1, 0.3))
            gx, gy = ((u - W * 0.25) / (W / 20)).astype(int), (v / (H / 20)).astype(int)
            heights = [3, 4, 4, 6, 5, 2, 3, 5, 7, 4]
            fill = well & (gy >= 20 - np.take(heights, np.clip(gx, 0, 9)))
            cs = [c1, c2, c3]
            for k in range(3):
                c = put(c, fill & ((gx + gy) % 3 == k), cs[k])
            c = put(c, well & (((u - W * 0.25) / (W / 20)) % 1 < 0.1) & fill, '#0a0a0a')
            c = put(c, well & (gx >= 4) & (gx <= 6) & (gy == 6), c2)
            c = put(c, well & (gx == 5) & (gy == 5), c2)
        elif kind == 'fight':
            c = put(c, v > H * 0.75, c1)
            for x0, col in ((0.3, c2), (0.62, c3)):
                body = (np.abs(u - W * x0) < W * 0.05) & (v > H * 0.42) & (v < H * 0.75)
                head = np.hypot(u - W * x0, v - H * 0.36) < W * 0.04
                c = put(c, body | head, col)
            for x0, col in ((0.05, c2), (0.55, c3)):
                c = put(c, (u > W * x0) & (u < W * (x0 + 0.4)) & (np.abs(v - H * 0.17) < H * 0.02), col)
        c = text(c, u, v, '1UP 004250', W * 0.5, H * 0.06, H * 0.05, '#f0f0f0')
        scan = (v / H * 120).astype(int) % 2 == 0
        return np.where(scan[..., None], c * 0.9, c)
    return face_paint(draw, bg, faces, 0.0)


def reels(symbols):
    """Three reels behind the window, each showing three symbols, the red pay line across the middle."""
    def draw(u, v, W, H, c, f):
        k = (u / (W / 3)).astype(int)
        lu = (u % (W / 3))
        c = put(c, lu < W * 0.02, '#2a2a2a')
        for reel in range(3):
            for row in range(3):
                s = symbols[(reel * 2 + row) % len(symbols)]
                cu, cv = (reel + 0.5) * W / 3, (row + 0.5) * H / 3
                at = (k == reel)
                h = min(W / 3, H / 3) * 0.6
                if s == '7':
                    c = put(c, at & A.text_mask(u, v, '7', cu, cv, h), '#c82a2a')
                elif s == 'BAR':
                    box = at & (np.abs(u - cu) < W / 3 * 0.38) & (np.abs(v - cv) < h * 0.4)
                    c = put(c, box, '#1e1f20')
                    c = put(c, at & A.text_mask(u, v, 'BAR', cu, cv, h * 0.45), '#f0ece0')
                elif s == 'cherry':
                    for dx in (-0.15, 0.15):
                        c = put(c, at & (np.hypot(u - cu - dx * W / 3, v - cv - h * 0.15) < h * 0.22), '#c8202a')
                    c = put(c, at & (np.abs(u - cu) < h * 0.05) & (v < cv) & (v > cv - h * 0.45), '#3a7a2a')
                elif s == 'bell':
                    bell = at & (np.abs(u - cu) < h * 0.35 * np.clip((v - cv + h * 0.45) / (h * 0.7), 0.3, 1)) \
                        & (np.abs(v - cv) < h * 0.4)
                    c = put(c, bell, '#e8b820')
                elif s == 'plum':
                    c = put(c, at & (np.hypot(u - cu, (v - cv) * 0.8) < h * 0.32), '#6a2a8a')
        c = put(c, np.abs(v - H / 2) < H * 0.012, '#e83a2a')
        shade = 1 - (np.abs((v % (H / 3)) / (H / 3) - 0.5) * 0.5)
        return c * shade[..., None]
    return face_paint(draw, '#f4f0e4', ('front',), 0.02)


def skee_target(face, ring, hole):
    """The skee-ball target: the 10, 20, 30, 40 and 50 rings round the top hole, the two 100 holes in the corners."""
    def draw(u, v, W, H, c, f):
        cx = W / 2
        for k, (cy, r0) in enumerate(((H * 0.55, W * 0.4), (H * 0.42, W * 0.3), (H * 0.33, W * 0.22),
                                      (H * 0.27, W * 0.14), (H * 0.22, W * 0.07))):
            d = np.hypot(u - cx, v - cy)
            c = put(c, d < r0, ring if k % 2 == 0 else face)
            c = put(c, np.abs(d - r0) < W * 0.012, '#f0ece0')
        c = put(c, np.hypot(u - cx, v - H * 0.22) < W * 0.05, hole)
        for x0 in (W * 0.1, W * 0.9):
            c = put(c, np.hypot(u - x0, v - H * 0.1) < W * 0.05, hole)
            c = text(c, u, v, '100', x0, H * 0.19, H * 0.04, '#f0ece0')
        c = text(c, u, v, '50', cx, H * 0.29, H * 0.035, '#f0ece0')
        c = text(c, u, v, '10', cx, H * 0.9, H * 0.05, '#f0ece0')
        return c
    return face_paint(draw, face, ('top',), 0.04)


def net(colour='#e8e8e8', back='#2a2a2a', faces=('all',)):
    def draw(u, v, W, H, c, f):
        g = ((u * 4) % 1 < 0.15) | ((v * 4) % 1 < 0.15)
        return put(c, g, colour)
    return face_paint(draw, back, faces, 0.02)


def side_art(c1, c2):
    """A cabinet side: the paint with a bold diagonal band of the game's colour and a thin stripe beside it."""
    def draw(u, v, W, H, c, f):
        P_ = f.P
        t = (P_[..., 1] * 0.8 + P_[..., 2] * 0.6)
        c = put(c, (t % 18) < 5, c1)
        return put(c, ((t % 18) > 5.6) & ((t % 18) < 6.6), c2)
    return lambda colour: face_paint(draw, colour, ('left', 'right'), 0.04)


def playfield(base, c1, c2, c3):
    def draw(u, v, W, H, c, f):
        rr = np.hypot(u - W / 2, v - H * 0.32)
        c = put(c, (rr % (W * 0.24)) < W * 0.05, c1)
        c = put(c, (np.abs(u - W / 2) < W * 0.25) & (np.abs(v - H * 0.65) < H * 0.06), c2)
        for q in range(5):                         # the arrow inserts up the middle
            x0, y0 = W * 0.5, H * (0.45 + q * 0.06)
            tri = (np.abs(u - x0) < (v - y0 + H * 0.03) * 0.8) & (v > y0 - H * 0.03) & (v < y0 + H * 0.01)
            c = put(c, tri, c3 if q % 2 else c2)
        for x0 in (W * 0.2, W * 0.4, W * 0.6, W * 0.8):
            c = put(c, np.hypot(u - x0, v - H * 0.12) < W * 0.03, c3)
        return c
    return face_paint(draw, base, ('top',), 0.04)


def swirl(c1, c2):
    """A bowling ball's swirled urethane."""
    def fn(f):
        t = np.sin(f.noise(0.5, 21, 3) * 9.0 + f.P[..., 1] * 0.8) * 0.5 + 0.5
        return P.mix(P.col(c1), P.col(c2), t ** 2)
    return fn


# ================================================================== the pieces


def dartboard(m, k=0):
    """A dartboard on its floor stand: the weighted base and post, the wooden cabinet with its doors swung open
    (chalk score boards inside), the board on its backing, three darts in it and three spare in the post's
    holder, the chalk tray."""
    cy = 21.0
    m.rod(0.0, 0.6, 5.2, 'stand')
    m.rod(0.6, 1.2, 4.4, 'stand')
    m.rod(1.2, 13.5, 0.7, 'stand', cz=-1.0)
    m.box([-1.2, 12.6, -1.8], [1.2, 13.6, -0.6], 'stand')                         # cabinet bracket
    m.box([-7.0, 13.0, -1.6], [7.0, 29.0, -0.8], 'backing')                       # back panel
    for x0, x1 in ((-7.0, -6.2), (6.2, 7.0)):
        m.box([x0, 13.0, -1.6], [x1, 29.0, 1.6], 'wood')
    m.box([-7.0, 28.2, -1.6], [7.0, 29.0, 1.6], 'wood')
    m.box([-7.0, 13.0, -1.6], [7.0, 13.8, 1.6], 'wood')
    for s in (-1, 1):                                                               # doors open square
        x0, x1 = sorted([s * 7.0, s * 7.6])
        m.box([x0, 13.0, 1.6], [x1, 29.0, 8.6], 'wood')
        c0, c1 = sorted([s * 7.0, s * 6.85])
        m.box([c0, 14.2, 2.2], [c1, 27.8, 8.0], 'chalk')                           # score board inside
    m.rod(-0.8, 0.5, 5.6, 'board', cx=0.0, cz=cy, axis=2)                         # the board
    sh.torus(m, (0.0, cy), 5.6, 0.35, 0.5, 'metal', z=0.4, steps=20)               # number ring wire
    for x, y in ((1.2, 1.8), (-2.0, -1.2), (0.3, -0.4)):                           # darts in the board
        m.rod(0.5, 2.6, 0.2, 'metal', cx=x, cz=cy + y, axis=2)
        m.rod(2.6, 3.8, 0.1, 'shaft', cx=x, cz=cy + y, axis=2)
        m.box([x - 0.05, cy + y - 0.55, 3.4], [x + 0.05, cy + y + 0.55, 4.6], 'flight')
        m.box([x - 0.55, cy + y - 0.05, 3.4], [x + 0.55, cy + y + 0.05, 4.6], 'flight')
    m.box([-1.2, 8.0, -0.4], [1.2, 9.6, 0.4], 'stand')                             # spare dart holder
    for x in (-0.6, 0.0, 0.6):
        m.rod(9.6, 11.0, 0.12, 'metal', cx=x, cz=0.0)
        m.rod(11.0, 11.8, 0.08, 'shaft', cx=x, cz=0.0)
        m.box([x - 0.03, 11.4, -0.35], [x + 0.03, 12.3, 0.35], 'flight')
    m.box([-4.0, 13.8, 0.4], [4.0, 14.3, 1.6], 'wood')                             # chalk tray
    m.box([-3.2, 14.3, 0.7], [-2.2, 14.6, 1.2], 'chalkstick')
    m.box([1.6, 14.3, 0.5], [3.2, 14.9, 1.3], 'eraser')


def pool_cue(m, k=0):
    """A pool cue in its floor stand with the cue case beside it: the rubber bumper, the wood butt with its
    pointed inlays and wrap, the joint, the long tapered maple shaft, the white ferrule and chalked tip; the stand's
    weighted foot, cup and clip; the hard case with its strap, pocket and zips; a cube of chalk."""
    m.rod(0.0, 0.8, 3.2, 'stand')
    m.rod(0.8, 2.2, 0.95, 'stand')
    m.rod(0.8, 15.0, 0.3, 'stand', cz=-1.4)
    m.box([-0.6, 14.0, -1.6], [0.6, 15.0, 0.6], 'stand')                            # clip
    m.rod(0.9, 1.3, 0.62, 'rubber')
    m.rod(1.3, 3.0, 0.62, 'butt')
    m.rod(3.0, 8.0, 0.64, 'wrap')
    m.rod(8.0, 8.4, 0.62, 'inlay')
    m.rod(8.4, 12.0, 0.56, 'butt')
    for a in (0, 90, 180, 270):                                                      # the points
        x, z = 0.56 * math.cos(math.radians(a)), 0.56 * math.sin(math.radians(a))
        m.box([x - 0.12, 9.0, z - 0.12], [x + 0.12, 11.8, z + 0.12], 'inlay')
    m.rod(12.0, 12.6, 0.54, 'metal')
    for y0, y1, r in ((12.6, 17.0, 0.46), (17.0, 21.5, 0.38), (21.5, 25.5, 0.3)):
        m.rod(y0, y1, r, 'shaft')
    m.rod(25.5, 26.0, 0.28, 'ferrule')
    m.rod(26.0, 26.3, 0.27, 'tip')
    cx = 4.2
    sh.oval_rod(m, 0.0, 22.0, 1.6, 1.1, 'case', cx=cx, cz=-0.3)                     # the case
    m.rod(22.0, 22.8, 1.2, 'trim', cx=cx, cz=-0.3)
    m.rod(0.0, 0.6, 1.5, 'trim', cx=cx, cz=-0.3)
    m.box([cx - 1.3, 3.0, 0.7], [cx + 1.3, 12.0, 1.3], 'case2')                     # pocket
    m.box([cx - 1.3, 11.6, 1.3], [cx + 1.3, 11.8, 1.35], 'metal')                   # its zip
    m.box([cx - 0.15, 0.6, 0.75], [cx + 0.15, 21.6, 0.85], 'metal')                 # the long zip
    m.seg((cx - 1.65, 18.0), (cx - 1.65, 6.0), 0.5, 0.3, 'strap', z=-0.3)           # strap
    m.box([cx - 0.7, 14.0, 0.8], [cx + 0.7, 15.6, 1.6], 'strap')                    # handle
    m.box([-2.8, 0.8, 1.2], [-2.0, 1.6, 2.0], 'chalk')                              # chalk on the foot


CHESS_START = ['RNBQKBNR', 'PPPPPPPP']
CHESS_MID = {   # (file, rank-from-top) for an Italian Game a few moves in
    'w': [('K', 6, 7), ('R', 5, 7), ('R', 0, 7), ('Q', 3, 7), ('B', 2, 7), ('B', 2, 4), ('N', 5, 5), ('N', 2, 5),
          ('P', 0, 6), ('P', 1, 6), ('P', 2, 6), ('P', 3, 5), ('P', 4, 4), ('P', 5, 6), ('P', 6, 6), ('P', 7, 6)],
    'b': [('K', 4, 0), ('Q', 3, 0), ('R', 0, 0), ('R', 7, 0), ('B', 2, 0), ('B', 2, 3), ('N', 2, 2), ('N', 5, 2),
          ('P', 0, 1), ('P', 1, 1), ('P', 2, 1), ('P', 3, 2), ('P', 4, 3), ('P', 5, 1), ('P', 6, 1), ('P', 7, 1)],
}


def chess_piece(m, kind, x, z, mat, face):
    y = 0.8
    m.rod(y, y + 0.3, 0.52, mat, cx=x, cz=z)
    if kind == 'P':
        m.rod(y + 0.3, y + 1.0, 0.28, mat, cx=x, cz=z)
        m.rod(y + 1.0, y + 1.12, 0.36, mat, cx=x, cz=z)
        blob(m, (x, y + 1.45, z), 0.32, mat)
    elif kind == 'R':
        m.rod(y + 0.3, y + 1.8, 0.4, mat, cx=x, cz=z)
        m.rod(y + 1.8, y + 2.2, 0.5, mat, cx=x, cz=z)
        for dx, dz in ((-0.32, -0.32), (0.32, -0.32), (-0.32, 0.32), (0.32, 0.32)):
            m.box([x + dx - 0.14, y + 2.2, z + dz - 0.14], [x + dx + 0.14, y + 2.5, z + dz + 0.14], mat)
    elif kind == 'N':
        m.rod(y + 0.3, y + 0.9, 0.4, mat, cx=x, cz=z)
        m.box([x - 0.26, y + 0.9, z - 0.3 * face], [x + 0.26, y + 2.3, z + 0.3 * face], mat)
        m.box([x - 0.23, y + 1.6, min(z, z - 0.85 * face)], [x + 0.23, y + 2.1, max(z, z - 0.85 * face)], mat)
        m.box([x - 0.2, y + 2.3, z + 0.05 * face - 0.12], [x + 0.2, y + 2.6, z + 0.05 * face + 0.12], mat)
    elif kind == 'B':
        m.rod(y + 0.3, y + 1.6, 0.3, mat, cx=x, cz=z)
        m.rod(y + 1.25, y + 1.37, 0.38, mat, cx=x, cz=z)
        blob(m, (x, y + 1.95, z), (0.33, 0.5, 0.33), mat)
        blob(m, (x, y + 2.55, z), 0.12, mat)
    else:
        tall = 2.3 if kind == 'Q' else 2.5
        m.rod(y + 0.3, y + tall, 0.34, mat, cx=x, cz=z)
        m.rod(y + tall - 0.6, y + tall - 0.48, 0.44, mat, cx=x, cz=z)
        m.rod(y + tall, y + tall + 0.3, 0.44, mat, cx=x, cz=z)
        if kind == 'Q':
            blob(m, (x, y + tall + 0.45, z), 0.2, mat)
        else:
            m.box([x - 0.08, y + tall + 0.3, z - 0.08], [x + 0.08, y + tall + 1.0, z + 0.08], mat)
            m.box([x - 0.26, y + tall + 0.58, z - 0.08], [x + 0.26, y + tall + 0.74, z + 0.08], mat)


def chess_set(m, k=0):
    """A wooden chess board with its men set out (the start, or an Italian Game a few moves in): the inlaid
    squares with the files and ranks round the border, Staunton pieces in light and dark wood, a chess clock."""
    half, b = 6.5, 13.0 * 0.08
    m.box([-half, 0.0, -half], [half, 0.8, half], 'board')
    s = (13.0 - 2 * b) / 8
    at = lambda i, j: (-half + b + (i + 0.5) * s, -half + b + (j + 0.5) * s)        # noqa: E731
    if k % 2 == 0:
        for side, mat, rows, face in (('w', 'pw', (7, 6), 1), ('b', 'pb', (0, 1), -1)):
            for row, line in zip(rows, CHESS_START):
                for i, kind in enumerate(line):
                    chess_piece(m, kind, *at(i, row), mat, face)
    else:
        for side, mat, face in (('w', 'pw', 1), ('b', 'pb', -1)):
            for kind, i, j in CHESS_MID[side]:
                chess_piece(m, kind, *at(i, j), mat, face)
    # the chess clock beside the board
    m.box([half + 0.6, 0.0, -2.4], [half + 3.2, 2.6, 2.4], 'clock')
    for z in (-1.2, 1.2):
        m.rod(half + 3.2, half + 3.3, 0.85, 'clockface', cx=1.4, cz=z, axis=0)
        m.rod(2.6, 3.0, 0.22, 'metal', cx=half + 1.9, cz=z)
    m.box([half + 3.3, 1.35, -1.25], [half + 3.35, 1.95, -1.15], 'clockhand')
    m.box([half + 3.3, 1.35, 1.15], [half + 3.35, 1.85, 1.25], 'clockhand')


FOOS = [1, 2, 3, 5, 5, 3, 2, 1]
FOOS_TEAM = [0, 0, 1, 0, 1, 0, 1, 1]


def foosball_table(m, k=0):
    """A foosball table: the cabinet on its four legs with the cross bar, the striped pitch, the goals, eight
    rods through the sides with their players hung on them (goalie, defence, midfield and attack for each team),
    the grips on each team's side, the bead score counters, a ball on the centre spot."""
    L, W, y0, y1 = 11.0, 6.0, 7.0, 13.0
    m.box([-L, y0, W - 0.6], [L, y1, W], 'cab')
    m.box([-L, y0, -W], [L, y1, -W + 0.6], 'cab')
    m.box([-L, y0, -W], [-L + 0.6, y1, W], 'cab')
    m.box([L - 0.6, y0, -W], [L, y1, W], 'cab')
    m.box([-L + 0.6, y0 + 0.6, -W + 0.6], [L - 0.6, y0 + 1.0, W - 0.6], 'field')
    m.box([-L + 0.6, y0, -W + 0.6], [L - 0.6, y0 + 0.6, W - 0.6], 'cab')
    for x in (-L + 0.55, L - 0.6):                                                   # goals
        m.box([x, y0 + 1.0, -1.6], [x + 0.05, y0 + 2.6, 1.6], 'dark')
    for sx in (-1, 1):                                                               # corner ramps
        for sz in (-1, 1):
            m.box([sx * (L - 0.6) - (0.8 if sx > 0 else 0), y0 + 1.0, sz * (W - 0.6) - (0.8 if sz > 0 else 0)],
                  [sx * (L - 0.6) + (0.8 if sx < 0 else 0), y0 + 1.5, sz * (W - 0.6) + (0.8 if sz < 0 else 0)], 'cab')
            m.box([sx * (L - 1.4) - 0.7, 0.0, sz * (W - 1.4) - 0.7], [sx * (L - 1.4) + 0.7, y0, sz * (W - 1.4) + 0.7],
                  'leg')
            m.rod(0.0, 0.4, 0.9, 'dark', cx=sx * (L - 1.4), cz=sz * (W - 1.4))
    m.box([-L + 1.4, 2.0, -0.5], [L - 1.4, 3.0, 0.5], 'leg')                          # cross bar
    for sx in (-1, 1):
        m.box([sx * (L - 1.4) - 0.5, 2.0, -W + 1.4], [sx * (L - 1.4) + 0.5, 3.0, W - 1.4], 'leg')
    yr = y1 - 1.5
    for i, (n, team) in enumerate(zip(FOOS, FOOS_TEAM)):
        x = -9.1 + i * 2.6
        side = 1 if team == 0 else -1
        z0, z1 = (-W - 0.8, W + 3.6) if side > 0 else (-W - 3.6, W + 0.8)
        m.rod(z0, z1, 0.2, 'rod', cx=x, cz=yr, axis=2)
        g0, g1 = (W + 1.0, W + 3.8) if side > 0 else (-W - 3.8, -W - 1.0)
        m.rod(g0, g1, 0.45, 'grip0' if team == 0 else 'grip1', cx=x, cz=yr, axis=2)
        m.rod(-W - 0.8 if side > 0 else W + 0.3, -W - 0.3 if side > 0 else W + 0.8, 0.35, 'dark', cx=x, cz=yr, axis=2)
        mat = 'team0' if team == 0 else 'team1'
        for q in range(n):
            z = -W + 0.6 + (q + 1) * (2 * W - 1.2) / (n + 1)
            blob(m, (x, yr + 1.0, z), 0.42, 'head')
            m.box([x - 0.36, yr - 0.9, z - 0.5], [x + 0.36, yr + 0.6, z + 0.5], mat)
            m.box([x - 0.26, y0 + 1.2, z - 0.36], [x + 0.26, yr - 0.9, z + 0.36], 'shorts')
            m.box([x - 0.26 - (0.3 if team else 0), y0 + 1.1, z - 0.4], [x + 0.26 + (0 if team else 0.3), y0 + 1.6,
                                                                         z + 0.4], 'shorts')
    for sx in (-1, 1):                                                                # bead score counters
        m.rod(-3.0, 3.0, 0.08, 'metal', cx=sx * (L - 0.3), cz=y1 + 0.5, axis=2)
        for q in range(5):
            m.rod(-2.8 + q * 0.7, -2.3 + q * 0.7, 0.3, 'team0' if sx < 0 else 'team1', cx=sx * (L - 0.3),
                  cz=y1 + 0.5, axis=2)
        m.box([sx * (L - 0.3) - 0.1, y1, -3.0], [sx * (L - 0.3) + 0.1, y1 + 0.5, -2.9], 'metal')
        m.box([sx * (L - 0.3) - 0.1, y1, 2.9], [sx * (L - 0.3) + 0.1, y1 + 0.5, 3.0], 'metal')
    blob(m, (0.0, y0 + 1.35, 0.0), 0.35, 'ball')


def ping_pong_table(m, k=0):
    """A table tennis table: the top with its white edge and centre lines, the net on its clamp posts, two bats
    and a ball left on it; the outdoor concrete one on its two pedestals, or the indoor one on its folding steel
    legs and wheels."""
    L, W, top = 18.0, 10.0, 10.0
    m.box([-L, top - 0.6, -W], [L, top, W], 'top')
    for z in (-W - 0.6, W + 0.6):                                                    # net posts
        m.box([-0.3, top - 0.6, z - 0.3], [0.3, top + 2.3, z + 0.3], 'metal')
    m.box([-0.04, top, -W - 0.3], [0.04, top + 2.0, W + 0.3], 'net')
    m.box([-0.08, top + 1.85, -W - 0.3], [0.08, top + 2.1, W + 0.3], 'tape')
    if k == 0:
        for x in (-11.0, 11.0):
            m.box([x - 1.6, 0.0, -6.5], [x + 1.6, top - 0.6, 6.5], 'stand')
            m.box([x - 3.0, 0.0, -7.5], [x + 3.0, 1.0, 7.5], 'stand')
    else:
        m.box([-L + 0.6, top - 1.6, -W + 0.6], [L - 0.6, top - 0.6, W - 0.6], 'frame')
        for x in (-14.0, -4.0, 4.0, 14.0):
            for z in (-7.5, 7.5):
                m.rod(1.4, top - 1.6, 0.4, 'metal', cx=x, cz=z)
            m.rod(-7.5, 7.5, 0.3, 'metal', cx=x, cz=3.0, axis=2)
        for x in (-14.0, 14.0):
            for z in (-7.5, 7.5):
                m.rod(z - 0.4, z + 0.4, 0.75, 'rubber', cx=x, cz=0.75, axis=2)
        for x in (-4.0, 4.0):
            for z in (-7.5, 7.5):
                m.rod(0.0, 1.4, 0.5, 'rubber', cx=x, cz=z)
    for x, z, a in ((-12.0, 4.0, 0), (-9.5, -3.0, 1)):                                # bats
        m.rod(top, top + 0.25, 1.7, 'bat', cx=x, cz=z)
        m.rod(top + 0.25, top + 0.3, 1.6, 'bat2' if a else 'bat', cx=x, cz=z)
        m.box([x + 1.5, top, z - 0.35], [x + 3.6, top + 0.55, z + 0.35], 'handle')
    blob(m, (-6.0, top + 0.35, 1.0), 0.35, 'ball')


def poker_chip_case(m, k=0):
    """A poker chip case standing open: the shell with its corner guards, rim, locks and handle, the lid up with
    its felt lining, five rows of chips lying in their grooves, two decks of cards and five dice in the end bay."""
    w, d, h = 6.0, 4.0, 3.0
    m.box([-w, 0.0, -d], [w, h - 0.4, d], 'case')
    for x0, x1, z0, z1 in ((-w, w, -d, -d + 0.3), (-w, w, d - 0.3, d), (-w, -w + 0.3, -d, d), (w - 0.3, w, -d, d)):
        m.box([x0, h - 0.4, z0], [x1, h, z1], 'metal')
    m.box([-w + 0.3, h - 0.6, -d + 0.3], [w - 0.3, h - 0.5, d - 0.3], 'tray')
    for r in range(5):
        z = -d + 1.0 + r * 1.5
        m.rod(-w + 0.6, 1.3, 0.62, f'chip{r}', cx=h - 0.35, cz=z, axis=0)
    m.box([1.6, h - 0.6, -d + 0.5], [w - 0.4, h - 0.55, d - 0.5], 'tray')
    m.box([2.0, h - 0.6, -d + 0.8], [4.4, h + 0.4, -0.4], 'deck0')
    m.box([2.0, h - 0.6, 0.0], [4.4, h + 0.4, 1.8], 'deck1')
    for q, (x, z) in enumerate(((2.3, 2.4), (3.2, 2.6), (4.2, 2.3), (5.0, 3.1), (5.0, 1.0))):
        m.box([x - 0.32, h - 0.55, z - 0.32], [x + 0.32, h + 0.1, z + 0.32], 'dice')
        m.box([x - 0.08, h + 0.1, z - 0.08], [x + 0.08, h + 0.12, z + 0.08], 'pip')
    m.box([-w, 0.0, -d - 0.8], [w, 8.0 + 0.4, -d], 'case')                          # the lid, open
    m.box([-w + 0.4, 0.6, -d - 0.05], [w - 0.4, 7.8, -d], 'lining')
    for x in (-w, w - 0.6):
        for y in (0.0, 7.8):
            m.box([x, y, -d - 0.85], [x + 0.6, y + 0.6, -d + 0.05], 'metal')         # corner guards
    for x in (-w, w - 0.6):
        m.box([x, 0.0, d - 0.6], [x + 0.6, 0.6, d + 0.05], 'metal')
    for x in (-3.6, 3.6):
        m.box([x - 0.5, 1.2, d], [x + 0.5, 2.2, d + 0.3], 'metal')                   # locks
    for x in (-1.8, 1.8):
        m.box([x - 0.3, 1.0, d], [x + 0.3, 1.8, d + 0.6], 'metal')
    m.box([-2.1, 1.0, d + 0.5], [2.1, 1.6, d + 1.1], 'handle')


def bowling_ball_pins(m, k=0):
    """Ten bowling pins racked in their triangle with a ball rolled up in front: each pin's maple belly, neck
    with its two red stripes and head; the ball's swirled shell and three finger holes."""
    s = 4.0
    for row in range(4):
        n = 4 - row
        z = -6.0 + row * s * 0.87
        for i in range(n):
            x = (i - (n - 1) / 2) * s
            m.rod(0.0, 0.4, 0.6, 'pin', cx=x, cz=z)
            blob(m, (x, 2.5, z), (1.2, 2.2, 1.2), 'pin')
            m.rod(4.2, 5.6, 0.55, 'pin', cx=x, cz=z)
            for y in (4.6, 5.1):
                m.rod(y, y + 0.25, 0.58, 'stripe', cx=x, cz=z)
            blob(m, (x, 6.5, z), (0.72, 1.0, 0.72), 'pin')
    c = (2.6, 2.2, 8.6)
    blob(m, c, 2.2, 'ball')
    for dx, dy in ((-0.5, 0.7), (0.5, 0.7), (0.0, -0.5)):
        m.rod(c[2] + 1.85, c[2] + 2.2, 0.28, 'hole', cx=c[0] + dx, cz=c[1] + dy, axis=2)


def punching_bag(m, k=0):
    """A heavy bag on its free-standing stand: the two back posts and their feet with the weight plates on the
    pegs, the top beam reaching forward, the swivel and four chains, the bag with its trim bands and D-rings, a
    pair of gloves hung on the post."""
    for x in (-5.0, 5.0):
        m.box([x - 0.6, 0.0, -7.0], [x + 0.6, 1.0, 4.5], 'frame')                   # feet
        m.box([x - 0.5, 1.0, -6.5], [x + 0.5, 30.0, -5.5], 'frame')                  # posts
        m.rod(x + (0.5 if x > 0 else -2.4), x + (2.4 if x > 0 else -0.5), 0.3, 'metal', cx=2.2, cz=-4.0, axis=0)
        m.rod(x + (0.8 if x > 0 else -1.6), x + (1.6 if x > 0 else -0.8), 2.0, 'plate', cx=2.2, cz=-4.0, axis=0)
    m.box([-5.5, 29.0, -6.5], [5.5, 30.4, -5.5], 'frame')
    m.box([-0.6, 29.0, -6.5], [0.6, 30.4, 3.8], 'frame')                             # the arm
    m.box([-5.0, 2.0, -6.5], [5.0, 3.0, -5.5], 'frame')
    m.rod(28.2, 29.0, 0.4, 'metal', cz=3.0)                                           # swivel
    m.rod(27.4, 28.2, 0.15, 'chain', cz=3.0)
    for dx, dz in ((-2.3, 0.0), (2.3, 0.0), (0.0, -2.3), (0.0, 2.3)):
        if dz == 0:
            m.seg((0.0, 27.4), (dx, 25.6), 0.18, 0.18, 'chain', z=3.0, plane='xy')
        else:
            m.seg((3.0, 27.4), (3.0 + dz, 25.6), 0.18, 0.18, 'chain', z=0.0, plane='zy')
        m.box([dx - 0.2, 25.3, 3.0 + dz - 0.2], [dx + 0.2, 25.9, 3.0 + dz + 0.2], 'metal')
    m.rod(9.0, 25.0, 3.3, 'bag', cz=3.0)
    m.rod(25.0, 25.5, 3.0, 'bag', cz=3.0)
    m.rod(8.5, 9.0, 3.0, 'bag', cz=3.0)
    for y in (10.0, 23.6):
        m.rod(y, y + 0.4, 3.36, 'trim', cz=3.0)
    m.rod(15.0, 18.0, 3.34, 'trim2', cz=3.0)
    for y in (19.5, 18.0):                                                           # the gloves
        blob(m, (5.9, y, -5.2), (0.9, 1.0, 1.1), 'glove')
        m.rod(y - 1.6, y - 0.8, 0.6, 'cuff', cx=5.9, cz=-5.2)


ARCADE = [  # cabinet, side band, stripe, marquee word, marquee base, marquee ink, screen kind, colours
    ('#1e1f20', '#e83a2a', '#f0c020', 'STAR RAID', '#1a1a3a', '#f0c020', 'invaders', '#40e060', '#f0f0f0', '#e83a2a'),
    ('#e8e4da', '#2a6ae8', '#e83a2a', 'ROAD FURY', '#e83a2a', '#f8f0e0', 'road', '#3a8a3a', '#e8402a', '#2a6ae8'),
    ('#2a2a5a', '#f070b0', '#40e0d0', 'BRICK BLAST', '#f070b0', '#1e1f20', 'bricks', '#e83a2a', '#f0c020', '#40e060'),
    ('#1e1f20', '#f0c020', '#2a6ae8', 'MAZE RUN', '#f0c020', '#1e1f20', 'maze', '#2a4ae8', '#f0e040', '#e83a2a'),
    ('#5a1a1a', '#f0a020', '#f0f0f0', 'SPACE WAR', '#0a0a1a', '#f04a2a', 'stars', '#f0f0f0', '#a040f0', '#f0e040'),
    ('#1a3a2a', '#40e060', '#f0e040', 'BLOCK FALL', '#1a1a1a', '#40e060', 'blocks', '#e83a2a', '#2a6ae8', '#f0c020'),
    ('#3a1a4a', '#e83a2a', '#f0c020', 'FIGHT NITE', '#e83a2a', '#f0e040', 'fight', '#8a5a3a', '#f0c020', '#2a6ae8'),
    ('#1e1f20', '#40c0f0', '#f070b0', 'ALIEN DROP', '#40c0f0', '#1a1a3a', 'invaders', '#f070b0', '#40c0f0', '#f0e040'),
    ('#7a4a2a', '#f0c020', '#3a2a1a', 'GOLD RUSH', '#f0c020', '#5a2a1a', 'maze', '#c8862a', '#f0e040', '#e83a2a'),
    ('#e8e4da', '#40a040', '#1e1f20', 'RALLY', '#40a040', '#f8f0e0', 'road', '#c8a060', '#2a6ae8', '#e8e8e8'),
    ('#1a2a5a', '#f0f0f0', '#e83a2a', 'SKY ACE', '#2a4a8a', '#f0f0f0', 'stars', '#f0e040', '#e83a2a', '#40e060'),
    ('#2a2a2a', '#a040f0', '#40e060', 'MOON BASE', '#a040f0', '#f0f0f0', 'invaders', '#40e060', '#a040f0', '#f0a020'),
    ('#5a3a1a', '#e83a2a', '#40e060', 'CAVE QUEST', '#3a2a1a', '#f0a020', 'maze', '#8a6a4a', '#40e060', '#f070b0'),
    ('#1e1f20', '#6a8a3a', '#a8281f', 'DEAD WALK', '#1a1a1a', '#8ab040', 'fight', '#4a3a3a', '#6a8a3a', '#a8281f'),
    ('#f0c020', '#1e1f20', '#e83a2a', 'TURBO', '#1e1f20', '#f0c020', 'road', '#2a6a2a', '#f0c020', '#e83a2a'),
    ('#2a4a8a', '#f0c020', '#e8e4da', 'PUZZLE PIT', '#f0c020', '#2a4a8a', 'blocks', '#f070b0', '#40c0f0', '#f0e040'),
]


def arcade_cabinet(m, k=0):
    """An upright arcade cabinet: the two painted side panels with their T-molding edges, the kick plate and coin
    door with its lit slots, the control panel jutting out with two sticks and their buttons, the monitor leaning
    back in its bezel, the speaker grille and the lit marquee on top."""
    xs = 5.0
    profile = ((0.0, 14.0, -6.0, 4.2), (14.0, 16.0, -6.0, 6.0), (16.0, 24.0, -6.0, 2.6), (24.0, 28.0, -6.0, 4.0))
    for s in (-1, 1):
        for y0, y1, z0, z1 in profile:
            m.box([s * xs - 0.3, y0, z0], [s * xs + 0.3, y1, z1], 'side')
            m.box([s * xs - 0.34, y0, z1], [s * xs + 0.34, y1, z1 + 0.15], 'tmold')
    x = xs - 0.3
    m.box([-x, 0.0, -6.0], [x, 28.0, -5.4], 'cab')                                  # back
    m.box([-x, 0.0, 3.6], [x, 14.0, 4.2], 'cab')                                    # front
    m.box([-x, 0.0, 3.6], [x, 1.4, 4.3], 'dark')                                    # kick plate
    m.box([-2.0, 4.0, 4.2], [2.0, 9.5, 4.45], 'metal')                              # coin door
    for cx in (-1.0, 1.0):
        m.box([cx - 0.4, 7.4, 4.45], [cx + 0.4, 8.6, 4.55], 'glow4')
        m.box([cx - 0.3, 6.2, 4.45], [cx + 0.3, 6.6, 4.6], 'dark')
    m.box([-x, 13.4, -1.0], [x, 14.6, 6.0], 'panel')                                # control panel
    m.box([-x, 13.4, 5.4], [x, 14.6, 6.1], 'tmold')
    for px in (-2.2, 2.2):
        m.rod(14.6, 15.0, 0.6, 'dark', cx=px - 0.8, cz=3.6)
        m.rod(15.0, 16.0, 0.12, 'metal', cx=px - 0.8, cz=3.6)
        blob(m, (px - 0.8, 16.25, 3.6), 0.45, 'knob')
        for q, (bx, bz) in enumerate(((0.6, 3.2), (1.4, 3.0), (2.2, 3.2))):
            m.rod(14.6, 14.95, 0.3, ('btn0', 'btn1', 'btn0')[q], cx=px + bx - 0.6, cz=bz)
    m.rod(14.6, 14.8, 0.25, 'glow4', cx=-0.5, cz=1.2)
    m.rod(14.6, 14.8, 0.25, 'glow4', cx=0.5, cz=1.2)
    m.box([-x, 14.0, -1.0], [x, 15.6, 1.4], 'cab')
    tilt = dict(axis='x', angle=-22.5, origin=[0.0, 15.6, 1.4])
    m.box([-x, 15.6, 0.8], [x, 23.6, 1.4], 'bezel', rot=tilt)
    m.box([-3.6, 16.4, 1.4], [3.6, 22.8, 1.5], 'glow', rot=tilt)                    # the screen
    m.box([-x, 22.8, -6.0], [x, 24.2, 0.0], 'cab')
    m.box([-x, 23.4, 0.0], [x, 24.2, 3.6], 'grille')
    m.box([-x, 24.0, 3.4], [x, 27.6, 3.9], 'glow3')                                 # marquee
    m.box([-x, 27.6, -6.0], [x, 28.0, 4.0], 'cab')
    m.box([-x, 24.0, -6.0], [x, 27.6, 3.4], 'cab')


def pool_table(m, k=0):
    """A pool table: the cloth bed with its cushions, the wooden rails with their pearl sight diamonds, six
    pockets with their leather baskets and corner castings; carved legs, or the coin table's solid pedestal and
    ball slot; the balls racked at the foot spot, the cue ball on the head spot, a cue across the cloth and chalk
    on the rail."""
    L, W, top = 16.0, 9.0, 12.6
    m.box([-L + 1.0, 8.6, -W + 1.0], [L - 1.0, 11.0, W - 1.0], 'wood')              # apron
    if k < 4:
        for x in (-13.5, 13.5):
            for z in (-6.5, 6.5):
                m.rod(1.0, 8.6, 1.1, 'wood', cx=x, cz=z)
                m.rod(3.5, 6.0, 1.4, 'wood', cx=x, cz=z)
                blob(m, (x, 0.9, z), 1.2, 'wood')
    else:
        m.box([-12.0, 0.0, -5.5], [12.0, 8.6, 5.5], 'wood')
        m.box([-13.0, 0.0, -6.5], [13.0, 1.0, 6.5], 'dark')
        m.box([-2.0, 9.0, W - 1.0], [2.0, 10.6, W - 0.6], 'metal')                  # coin slide
        m.box([-3.0, 4.0, 5.5], [3.0, 6.0, 5.8], 'dark')                            # ball return
    m.box([-L + 1.6, 11.0, -W + 1.6], [L - 1.6, 11.8, W - 1.6], 'cloth')
    gaps = [(-L + 2.6, -1.0), (1.0, L - 2.6)]
    for z0, z1 in ((-W + 1.6, -W + 2.2), (W - 2.2, W - 1.6)):
        for x0, x1 in gaps:
            m.box([x0, 11.8, z0], [x1, 12.4, z1], 'cushion')
    for x0, x1 in ((-L + 1.6, -L + 2.2), (L - 2.2, L - 1.6)):
        m.box([x0, 11.8, -W + 2.6], [x1, 12.4, W - 2.6], 'cushion')
    for z0, z1 in ((-W, -W + 1.6), (W - 1.6, W)):
        m.box([-L, 11.0, z0], [L, top, z1], 'rail')
    for x0, x1 in ((-L, -L + 1.6), (L - 1.6, L)):
        m.box([x0, 11.0, -W], [x1, top, W], 'rail')
    for z in (-W + 0.8, W - 0.8):                                                    # sights
        for x in (-11.0, -7.4, -3.8, 3.8, 7.4, 11.0):
            m.box([x - 0.2, top, z - 0.12], [x + 0.2, top + 0.05, z + 0.12], 'pearl')
    for x in (-L + 0.8, L - 0.8):
        for z in (-3.6, 0.0, 3.6):
            m.box([x - 0.12, top, z - 0.2], [x + 0.12, top + 0.05, z + 0.2], 'pearl')
    for x, z in ((-L + 1.5, -W + 1.5), (L - 1.5, -W + 1.5), (-L + 1.5, W - 1.5), (L - 1.5, W - 1.5),
                 (0.0, -W + 1.2), (0.0, W - 1.2)):
        m.rod(11.2, top + 0.05, 0.95, 'dark', cx=x, cz=z)
        blob(m, (x, 10.2, z), (1.2, 1.0, 1.2), 'leather')
        if x:                                                                       # corner castings
            ox, oz = x + (-0.6 if x < 0 else 0.6), z + (-0.6 if z < 0 else 0.6)
            m.box([ox - 0.5, top, oz - 0.5], [ox + 0.5, top + 0.1, oz + 0.5], 'metal')
    rack = [1, 9, 2, 10, 8, 3, 11, 7, 14, 4, 5, 13, 15, 6, 12]
    n = 0
    for row in range(5):
        for i in range(row + 1):
            b = rack[n]
            n += 1
            x, z = 7.0 + row * 0.78, (i - row / 2) * 0.9
            colour = f'b{(b - 1) % 8 + 1}'
            if b > 8:
                blob(m, (x, 12.25, z), 0.45, 'b0')
                m.rod(12.05, 12.45, 0.47, colour, cx=x, cz=z)
            else:
                blob(m, (x, 12.25, z), 0.45, colour)
    blob(m, (-7.0, 12.25, 0.4), 0.45, 'b0')
    flat(m, (-12.5, -4.5), (5.0, 4.5), 0.45, 0.45, 'cue', y0=11.8)
    m.box([L - 1.2, top, -W + 0.4], [L - 0.6, top + 0.6, -W + 1.0], 'chalk')


def air_hockey_table(m, k=0):
    """An air hockey table: the cabinet on its legs, the white air-hole playfield with its centre and blue lines,
    face-off rings and goal creases, the high rails with the goal slots in each end, the scoreboard arch over the
    middle with its lit scores, two pucks and two strikers."""
    L, W, top = 16.0, 8.5, 11.6
    m.box([-L, 3.0, -W], [L, 11.0, W], 'cab')
    for x in (-L + 1.0, L - 1.0):
        for z in (-W + 1.0, W - 1.0):
            m.box([x - 0.9, 0.0, z - 0.9], [x + 0.9, 3.0, z + 0.9], 'leg')
            m.rod(0.0, 0.3, 1.1, 'dark', cx=x, cz=z)
    m.box([-L, 11.0, -W], [L, top, W], 'field')
    for z0, z1 in ((-W - 0.4, -W + 0.4), (W - 0.4, W + 0.4)):
        m.box([-L, 11.0, z0], [L, top + 1.4, z1], 'rail')
    for x0, x1 in ((-L - 0.4, -L + 0.4), (L - 0.4, L + 0.4)):
        for z0, z1 in ((-W - 0.4, -2.4), (2.4, W + 0.4)):
            m.box([x0, 11.0, z0], [x1, top + 1.4, z1], 'rail')
        m.box([x0, 9.4, -2.4], [x1, 11.0, 2.4], 'dark')                               # goal slot
        m.box([x0 - (0.4 if x0 < 0 else -0.8), 6.0, -2.0], [x0 + (0.0 if x0 < 0 else 1.2) + (0.4 if x0 < 0 else 0),
                                                            8.0, 2.0], 'metal')        # puck return
    for z in (-W - 0.4, W + 0.4):                                                     # scoreboard arch
        m.box([-0.5, top, z - 0.4 if z < 0 else z - 0.4], [0.5, 21.0, z + 0.4], 'rail')
    m.box([-0.7, 19.0, -W - 0.8], [0.7, 22.6, W + 0.8], 'cab')
    m.box([-0.75, 19.6, -6.0], [0.75, 22.0, -1.0], 'glow')
    m.box([-0.75, 19.6, 1.0], [0.75, 22.0, 6.0], 'glow2')
    m.box([-0.72, 22.6, -W - 0.8], [0.72, 23.2, W + 0.8], 'trim')
    for x, mat in ((-4.0, 'puck'), (5.5, 'puck')):
        m.rod(top, top + 0.3, 0.8, mat, cx=x, cz=1.5 if x < 0 else -2.0)
    for x, mat in ((-12.0, 'mallet0'), (12.0, 'mallet1')):
        m.rod(top, top + 0.5, 1.2, mat, cx=x, cz=0.0)
        m.rod(top + 0.5, top + 1.5, 0.45, mat, cx=x, cz=0.0)
        blob(m, (x, top + 1.7, 0.0), 0.6, mat)


def poker_table(m, k=0):
    """An oval poker table: the two pedestals, the padded rail with cup holders, the wooden racetrack, the felt with
    its betting line, the dealer's chip tray, cards dealt to each seat with chip stacks, the board in the middle."""
    R, half = 8.5, 8.5
    for x in (-half, half):
        m.rod(0.0, 0.8, 4.0, 'pedestal', cx=x)
        m.rod(0.8, 10.0, 1.0, 'pedestal', cx=x)
        m.rod(10.0, 11.0, R - 0.5, 'wood', cx=x)
        m.rod(11.0, 11.65, R - 0.9, 'wood2', cx=x)
        m.rod(11.0, 11.7, R - 1.6, 'felt', cx=x)
    m.box([-half, 10.0, -R + 0.5], [half, 11.0, R - 0.5], 'wood')
    m.box([-half, 11.0, -R + 0.9], [half, 11.65, R - 0.9], 'wood2')
    m.box([-half, 11.0, -R + 1.6], [half, 11.7, R - 1.6], 'felt')
    for cx, a0, a1 in ((-half, 90, 270), (half, -90, 90)):
        flat_arc(m, (cx, 0.0), R - 0.2, a0, a1, 1.4, 1.4, 'rail', y0=10.8, steps=10)
        flat_arc(m, (cx, 0.0), R - 3.6, a0, a1, 0.15, 0.03, 'line', y0=11.7, steps=10)
    for z in (-R + 0.2, R - 0.2):
        m.box([-half, 10.8, z - 0.7], [half, 12.2, z + 0.7], 'rail')
        m.box([-half, 11.7, (R - 3.6) * (1 if z > 0 else -1) - 0.075], [half, 11.73,
                                                                           (R - 3.6) * (1 if z > 0 else -1) + 0.075],
              'line')
    m.box([-2.0, 11.7, R - 3.0], [2.0, 12.1, R - 1.6], 'metal')                       # dealer's tray
    for q in range(8):
        m.box([-1.85 + q * 0.47, 11.75, R - 2.9], [-1.45 + q * 0.47, 12.15, R - 1.7], f'chip{q % 4}')
    seats = [(-15.0, 0.0), (-11.0, -5.5), (-4.0, -6.0), (4.0, -6.0), (11.0, -5.5), (15.0, 0.0), (11.0, 5.0),
             (-11.0, 5.0)]
    for q, (x, z) in enumerate(seats):
        sx, sz = x * 0.8, z * 0.72
        flat(m, (sx - 0.5, sz), (sx + 0.5, sz), 1.4, 0.05, 'cardback', y0=11.7)
        flat(m, (sx - 0.1, sz + 0.3), (sx + 0.9, sz + 0.1), 1.4, 0.05, 'cardback', y0=11.75)
        hgt = 0.4 + (q * 37 % 5) * 0.25
        m.rod(11.7, 11.7 + hgt, 0.42, f'chip{q % 4}', cx=sx + (1.4 if x < 0 else -1.4) * 0.8, cz=sz * 0.85)
        if abs(z) > 1:                                                              # cup holder in the rail
            m.rod(12.2, 12.25, 0.5, 'dark', cx=x * 0.7, cz=(R - 0.2) * (1 if z > 0 else -1))
    for q in range(5):
        m.box([-3.0 + q * 1.3, 11.7, -0.7], [-2.0 + q * 1.3, 11.76, 0.7], 'card')
        m.box([-2.8 + q * 1.3, 11.76, -0.3], [-2.2 + q * 1.3, 11.77, 0.3], 'pip' if q % 2 else 'pip2')
    m.rod(11.7, 12.6, 0.6, 'chip3', cx=0.0, cz=-2.2)                                   # the pot
    m.rod(11.7, 12.2, 0.6, 'chip1', cx=1.3, cz=-2.4)
    m.box([-0.9, 11.7, 2.0], [0.9, 12.1, 3.0], 'button')


CONSOLE = [  # shell, trim, buttons, cartridge, label base, label word
    ('#c8c8cc', '#7a7a80', '#6a4aa0', '#9a9aa0', '#e83a2a', 'SUPER RACE'),
    ('#2a2a2a', '#4a4a4a', '#c8302a', '#3a3a3a', '#2a6ae8', 'SPACE ACE'),
    ('#e8e4da', '#9a968c', '#2a6ae8', '#e8e4da', '#40a040', 'JUNGLE KING'),
    ('#3a3a40', '#5a5a60', '#40a040', '#2a2a2a', '#f0c020', 'GOLD QUEST'),
    ('#6a4aa0', '#4a3a7a', '#f0c020', '#5a3a8a', '#f070b0', 'STAR KART'),
    ('#d8ccb0', '#8a7a5a', '#a8281f', '#c8bca0', '#1e3a8a', 'KNIGHT TALE'),
]


def home_console(m, k=0):
    """A home games console with its pad and games: the two-step shell with the slot on top and a cartridge in
    it, the power and reset switches, the two pad ports, the pad lying in front on its cord with its cross pad and
    four buttons, a second cartridge beside it."""
    m.box([-5.0, 0.0, -4.0], [5.0, 1.6, 4.0], 'shell')
    m.box([-4.6, 1.6, -3.6], [4.6, 2.4, 2.6], 'shell')
    for z in (-2.9, -2.5, 1.6, 2.0):                                                  # moulded grooves
        m.box([-4.4, 2.4, z], [4.4, 2.44, z + 0.12], 'trim')
    m.box([-2.6, 2.4, -2.4], [2.6, 2.46, 1.2], 'dark')                                # slot
    m.box([-2.2, 2.4, -2.0], [2.2, 5.8, 0.4], 'cart')
    m.box([-1.8, 3.2, 0.4], [1.8, 5.4, 0.45], 'cartlabel')
    m.box([-2.2, 5.8, -2.0], [2.2, 6.0, 0.4], 'cart')
    for x in (-3.6, 3.0):
        m.box([x, 2.4, 1.2], [x + 0.7, 2.75, 2.2], 'btn')
    m.box([-0.9, 1.0, 4.0], [-0.3, 1.2, 4.05], 'glow4')                               # power light
    for x in (-3.8, 2.0):
        m.box([x, 0.4, 4.0], [x + 1.8, 1.2, 4.06], 'dark')
    for x in (-4.2, 4.2):
        for z in (-3.2, 3.2):
            m.rod(-0.1, 0.0, 0.35, 'dark', cx=x, cz=z)
    for x in (-3.6, -0.6):                                                            # the pad
        m.rod(0.0, 0.8, 1.3, 'shell', cx=x, cz=7.2)
    m.box([-3.6, 0.0, 6.2], [-0.6, 0.8, 8.0], 'shell')
    m.box([-4.0, 0.8, 6.95], [-3.2, 0.95, 7.45], 'dark')
    m.box([-3.85, 0.8, 6.8], [-3.35, 0.95, 7.6], 'dark')
    for q, (dx, dz) in enumerate(((0.45, 0.0), (-0.45, 0.0), (0.0, 0.45), (0.0, -0.45))):
        m.rod(0.8, 1.0, 0.22, ('b1', 'b2', 'b3', 'b4')[q], cx=-0.6 + dx, cz=7.2 + dz)
    for x in (-2.4, -1.8):
        m.box([x - 0.2, 0.8, 7.3], [x + 0.2, 0.86, 7.5], 'trim')
    flat(m, (-2.1, 6.2), (-2.4, 5.0), 0.2, 0.2, 'cord')
    flat(m, (-2.4, 5.0), (-2.9, 4.0), 0.2, 0.2, 'cord')
    m.box([6.0, 0.0, -2.0], [10.0, 0.8, 1.6], 'cart')                                 # the spare game
    m.box([6.4, 0.8, -1.6], [9.6, 0.85, 1.2], 'cartlabel2')


COCKTAIL = [  # cabinet, legs (chrome or wood sides), screen kind, colours
    ('#2a1a10', False, 'invaders', '#40e060', '#f0f0f0', '#e83a2a'),
    ('#e8e4da', True, 'maze', '#2a4ae8', '#f0e040', '#e83a2a'),
    ('#1e1f20', True, 'stars', '#f0f0f0', '#a040f0', '#f0e040'),
    ('#5a3a1a', False, 'bricks', '#e83a2a', '#f0c020', '#40e060'),
    ('#a8281f', True, 'blocks', '#f070b0', '#40c0f0', '#f0e040'),
    ('#1e1f20', False, 'road', '#3a8a3a', '#e8402a', '#2a6ae8'),
    ('#2a4a8a', True, 'invaders', '#f070b0', '#40c0f0', '#f0e040'),
    ('#3a2a1a', False, 'maze', '#c8862a', '#f0e040', '#e83a2a'),
]


def cocktail_arcade(m, k=0):
    """A cocktail table arcade: the low cabinet with the game screen looking up through the smoked glass top,
    a control panel with stick and buttons at each end for the two players, the coin door with lit slots, chrome
    legs or wooden plinth, the instruction cards under the glass."""
    chrome = COCKTAIL[k % len(COCKTAIL)][1]
    lo = 2.6 if chrome else 0.0
    m.box([-7.0, lo, -5.0], [7.0, 13.0, 5.0], 'cab')
    if chrome:
        for x in (-5.8, 5.8):
            for z in (-3.8, 3.8):
                m.rod(0.0, lo, 0.4, 'metal', cx=x, cz=z)
                m.rod(0.0, 0.2, 0.8, 'metal', cx=x, cz=z)
    else:
        m.box([-7.2, 0.0, -5.2], [7.2, 0.8, 5.2], 'dark')
    m.box([-8.0, 13.0, -5.6], [8.0, 13.6, 5.6], 'topglass')
    m.box([-4.0, 13.6, -3.2], [4.0, 13.62, 3.2], 'glow')
    for x in (-6.4, 5.2):
        m.box([x, 13.6, -4.6], [x + 1.2, 13.62, -2.0], 'card')
    for s in (-1, 1):
        m.box([sorted([s * 7.0, s * 8.8])[0], 10.6, -3.0], [sorted([s * 7.0, s * 8.8])[1], 12.4, 3.0], 'panel')
        m.rod(12.4, 13.4, 0.12, 'metal', cx=s * 8.0, cz=-1.4)
        blob(m, (s * 8.0, 13.6, -1.4), 0.4, 'knob')
        for q, z in enumerate((0.8, 1.9)):
            m.rod(12.4, 12.7, 0.3, 'btn0' if q else 'btn1', cx=s * 8.0, cz=z)
    m.box([-2.0, 4.0, 5.0], [2.0, 9.0, 5.3], 'metal')
    for cx in (-1.0, 1.0):
        m.box([cx - 0.35, 7.0, 5.3], [cx + 0.35, 8.0, 5.4], 'glow4')


PINBALL = [  # cabinet, backglass base, title, playfield, inserts, inserts 2
    ('#a8281f', '#1a2a5a', 'SPACE PATROL', '#1a2a5a', '#f0c020', '#e83a2a'),
    ('#7a4a1a', '#e8b040', 'WILD WEST', '#c8862a', '#a8281f', '#f0f0f0'),
    ('#1a5a7a', '#0a2a4a', 'DEEP SEA', '#1a5a7a', '#40e0d0', '#f0c020'),
    ('#1e1f20', '#e83a2a', 'HOT ROD', '#2a2a2a', '#f0c020', '#e83a2a'),
    ('#e8e4da', '#2a6ae8', 'ROCKET', '#2a4a8a', '#f0f0f0', '#e83a2a'),
    ('#2a5a2a', '#3a7a2a', 'JUNGLE', '#3a6a2a', '#f0c020', '#e86a1a'),
    ('#a8281f', '#f0c020', 'CIRCUS', '#e83a2a', '#f0f0f0', '#2a6ae8'),
    ('#2a6ae8', '#40c0f0', 'HIGH DIVE', '#2a8ac0', '#f070b0', '#f0e040'),
    ('#3a3a3a', '#5a5a6a', 'KNIGHTS', '#3a3a4a', '#c8a040', '#a8281f'),
    ('#5a1a1a', '#e86a1a', 'VOLCANO', '#3a1a1a', '#f0a020', '#f0e040'),
    ('#f0c020', '#1e1f20', 'DRAGSTER', '#2a2a2a', '#f0c020', '#40e060'),
    ('#3a1a5a', '#1a0a3a', 'STARLIGHT', '#2a1a4a', '#f070b0', '#40e0d0'),
]


def pinball_machine(m, k=0):
    """A pinball machine: the cabinet on four legs with its chrome side rails and lockdown bar, the painted
    playfield with its three pop bumpers, slingshots, lane guides, flippers, the apron and a ball; the plunger and
    flipper buttons, the coin door with lit slots; the backbox up the back with its lit backglass and score
    display."""
    hw, hl = 5.8, 10.5
    for x in (-hw + 0.6, hw - 0.6):
        m.rod(0.0, 10.0, 0.45, 'metal', cx=x, cz=hl - 0.8)
        m.rod(0.0, 10.4, 0.45, 'metal', cx=x, cz=-hl + 0.8)
        for z in (hl - 0.8, -hl + 0.8):
            m.rod(0.0, 0.3, 0.8, 'metal', cx=x, cz=z)
    m.box([-hw, 9.6, -hl], [hw, 13.6, hl], 'cab')
    m.box([-hw, 13.6, -hl], [hw, 14.2, 0.0], 'cab')
    for x0, x1 in ((-hw, -hw + 0.4), (hw - 0.4, hw)):
        m.box([x0, 13.6, -hl], [x1, 14.6, hl], 'metal')
    m.box([-hw, 13.6, hl - 0.7], [hw, 14.4, hl + 0.2], 'metal')                       # lockdown bar
    m.box([-hw + 0.4, 13.5, -hl + 0.3], [hw - 0.4, 13.7, hl - 0.7], 'field')
    for x, z in ((-1.6, -6.0), (1.6, -6.0), (0.0, -4.2)):                             # pop bumpers
        m.rod(13.7, 14.4, 0.7, 'trim', cx=x, cz=z)
        m.rod(14.4, 14.7, 0.85, 'glow2', cx=x, cz=z)
    for s in (-1, 1):
        m.box([s * 2.6 - 0.5, 13.7, 3.2], [s * 2.6 + 0.5, 14.2, 5.8], 'sling')
        m.box([s * 2.6 - (0.6 if s > 0 else -0.5) - 0.1, 13.7, 3.2], [s * 2.6 - (0.5 if s > 0 else -0.6), 14.2, 5.8],
              'rubber')
        flat(m, (s * 3.0, 6.6), (s * 0.7, 7.6), 0.55, 0.5, 'flipper', y0=13.7)
        flat(m, (s * (hw - 1.0), 2.0), (s * 3.4, 6.2), 0.25, 0.6, 'metal', y0=13.7)
        m.rod(hw, hw + 0.4, 0.35, 'btn', cx=11.8, cz=hl - 2.0, axis=0) if s > 0 else \
            m.rod(-hw - 0.4, -hw, 0.35, 'btn', cx=11.8, cz=hl - 2.0, axis=0)
    for x in (-2.0, -0.7, 0.7, 2.0):                                                   # top lanes
        m.box([x - 0.1, 13.7, -hl + 1.0], [x + 0.1, 14.2, -hl + 3.0], 'metal')
    m.box([hw - 1.6, 13.7, -hl + 2.0], [hw - 1.4, 14.3, hl - 0.8], 'metal')            # shooter lane wall
    m.box([-hw + 0.4, 13.7, 8.2], [hw - 1.6, 14.0, hl - 0.7], 'apron')
    blob(m, (1.2, 14.05, 1.0), 0.35, 'chrome')
    m.rod(hl, hl + 1.4, 0.18, 'metal', cx=hw - 1.0, cz=12.2, axis=2)                  # plunger
    blob(m, (hw - 1.0, 12.2, hl + 1.6), (0.45, 0.45, 0.5), 'knob')
    m.box([-2.4, 10.4, hl], [2.4, 13.0, hl + 0.25], 'metal')                          # coin door
    for cx in (-1.0, 1.0):
        m.box([cx - 0.35, 11.8, hl + 0.25], [cx + 0.35, 12.8, hl + 0.35], 'glow4')
    m.box([-hw, 13.6, -hl - 1.0], [hw, 27.0, -hl + 1.4], 'cab')                       # backbox
    m.box([-hw + 0.5, 18.0, -hl + 1.4], [hw - 0.5, 26.4, -hl + 1.55], 'glow3')        # backglass
    m.box([-hw + 1.0, 15.2, -hl + 1.4], [hw - 1.0, 17.2, -hl + 1.55], 'glow')         # score display
    m.box([-hw - 0.1, 26.6, -hl - 1.1], [hw + 0.1, 27.4, -hl + 1.6], 'metal')


CLAW = [('#e8e4da', '#f070b0', 'PRIZES'), ('#f0c020', '#40a040', 'CRANE'), ('#2a6ae8', '#f0f0f0', 'CLAW'),
        ('#a8281f', '#f0c020', 'WIN A PRIZE')]
PLUSH = [((-3.0, -4.0), 1.6, 'p0'), ((0.5, -4.6), 1.7, 'p1'), ((4.0, -4.0), 1.6, 'p2'), ((-3.6, -0.6), 1.5, 'p3'),
         ((0.0, -1.0), 1.8, 'p0'), ((3.6, -0.6), 1.5, 'p1'), ((0.4, 2.8), 1.5, 'p2'), ((4.0, 3.2), 1.6, 'p3'),
         ((-1.8, -2.6), 1.3, 'p2'), ((2.2, -2.4), 1.3, 'p3'), ((2.2, 1.0), 1.2, 'p0'), ((-1.6, 1.0), 1.2, 'p1')]


def claw_machine(m, k=0):
    """A claw crane: the lower cabinet with its prize door, coin slot and control ledge (stick and drop button),
    the case of corner posts over a heap of plush toys with eyes, the clear prize chute, the gantry and trolley
    with the claw hung on its cable, the lit sign box on top."""
    m.box([-7.0, 0.0, -7.0], [7.0, 12.0, 7.0], 'cab')
    m.box([-7.1, 0.0, -7.1], [7.1, 0.8, 7.1], 'dark')
    m.box([-5.6, 1.4, 7.0], [-1.6, 5.4, 7.3], 'dark')                                  # prize door
    m.box([-5.6, 5.4, 7.0], [-1.6, 5.8, 7.4], 'trim')
    m.box([2.0, 3.0, 7.0], [4.0, 6.0, 7.25], 'metal')
    m.box([2.6, 4.8, 7.25], [3.4, 5.6, 7.35], 'glow4')
    m.box([-6.0, 10.4, 7.0], [6.0, 11.4, 8.8], 'panel')
    m.rod(11.4, 12.4, 0.12, 'metal', cx=-2.0, cz=8.0)
    blob(m, (-2.0, 12.7, 8.0), 0.5, 'knob')
    m.rod(11.4, 11.8, 0.7, 'trim', cx=2.4, cz=8.0)
    m.rod(11.8, 12.1, 0.6, 'glow2', cx=2.4, cz=8.0)
    for x in (-7.0, 6.4):
        for z in (-7.0, 6.4):
            m.box([x, 12.0, z], [x + 0.6, 28.0, z + 0.6], 'cab')
    m.box([-7.0, 12.0, -7.0], [7.0, 12.4, 7.0], 'floor')
    m.box([-7.0, 12.0, -7.0], [7.0, 28.0, -6.6], 'mirror')
    for (x, z), r, mat in PLUSH:                                                       # the toys
        blob(m, (x, 12.4 + r * 0.8, z), (r, r * 0.85, r), mat)
        for dx in (-0.4, 0.4):
            m.box([x + dx - 0.12, 12.4 + r * 0.95, z + r * 0.92], [x + dx + 0.12, 12.4 + r * 1.2, z + r * 1.0], 'eye')
        blob(m, (x - r * 0.55, 12.4 + r * 1.55, z), r * 0.3, mat)
        blob(m, (x + r * 0.55, 12.4 + r * 1.55, z), r * 0.3, mat)
    for x0, x1, z0, z1 in ((-6.4, -2.4, 2.6, 2.8), (-6.4, -2.4, 6.2, 6.4), (-2.6, -2.4, 2.6, 6.4)):
        m.box([x0, 12.4, z0], [x1, 16.4, z1], 'chute')
    for x in (-6.0, 6.0):
        m.box([x - 0.3, 26.6, -6.4], [x + 0.3, 27.2, 6.4], 'metal')
    m.box([-6.0, 26.4, -0.3], [6.0, 27.0, 0.3], 'metal')
    m.box([0.4, 25.8, -0.7], [1.8, 26.4, 0.7], 'metal')
    m.rod(21.0, 25.8, 0.08, 'wire', cx=1.1, cz=0.0)
    m.rod(20.0, 21.0, 0.55, 'metal', cx=1.1, cz=0.0)
    m.seg((1.5, 20.0), (2.5, 18.4), 0.3, 0.3, 'metal', z=0.0)
    m.seg((2.5, 18.4), (2.0, 17.4), 0.3, 0.3, 'metal', z=0.0)
    for dz in (-0.4, 0.4):
        m.seg((0.7, 20.0), (-0.3, 18.4), 0.3, 0.3, 'metal', z=dz)
        m.seg((-0.3, 18.4), (0.2, 17.4), 0.3, 0.3, 'metal', z=dz)
    m.box([-6.6, 27.6, -6.6], [6.6, 28.0, 6.6], 'glow')
    m.box([-7.0, 28.0, -7.0], [7.0, 32.0, 7.0], 'cab')
    m.box([-6.4, 28.5, 7.0], [6.4, 31.5, 7.15], 'glow3')


def skee_ball_lane(m, k=0):
    """A skee-ball lane: the long cabinet with lit rails, the wooden lane rolling up to the hump, the target
    board tilted up behind it with its scoring rings and holes, the net over the top, the ball gate at the front
    holding the balls, the score board on the back, the ticket dispenser."""
    zf, zb = 20.0, -20.0
    for x0, x1 in ((-6.0, -5.0), (5.0, 6.0)):
        m.box([x0, 0.0, -6.0], [x1, 8.2, zf], 'cab')
        m.box([x0, 8.2, -6.0], [x1, 8.5, zf], 'glow')
        m.box([x0, 0.0, zb], [x1, 18.0, -6.0], 'cab')
    m.box([-5.0, 0.0, -6.0], [5.0, 6.0, zf], 'cab')
    m.box([-5.0, 6.0, -5.0], [5.0, 6.6, 16.0], 'lane')
    m.box([-5.0, 6.6, -6.0], [5.0, 7.2, -4.0], 'lane')                                   # hump
    m.box([-5.0, 0.0, zb], [5.0, 6.6, -6.0], 'cab')
    tilt = dict(axis='x', angle=45, origin=[0.0, 6.6, -6.0])
    m.box([-5.0, 5.6, -19.0], [5.0, 6.6, -6.0], 'target', rot=tilt)
    m.box([-6.0, 0.0, zb], [6.0, 20.0, zb + 1.4], 'cab')
    m.box([-6.0, 17.6, zb], [6.0, 18.0, -8.0], 'net')
    for x0, x1 in ((-6.0, -5.0), (5.0, 6.0)):
        m.box([x0, 8.2, -8.0], [x1, 18.0, -7.6], 'net')
    m.box([-6.0, 20.0, zb], [6.0, 28.0, zb + 1.4], 'cab')
    m.box([-5.4, 24.4, zb + 1.4], [5.4, 27.6, zb + 1.55], 'glow3')
    m.box([-4.0, 20.8, zb + 1.4], [4.0, 23.6, zb + 1.55], 'glow2')
    m.box([-5.0, 6.0, 16.0], [5.0, 7.0, zf], 'trim')                                     # ball gate
    m.box([-5.0, 7.0, zf - 0.4], [5.0, 8.6, zf], 'trim')
    for q in range(5):
        blob(m, (-3.6 + q * 1.8, 7.9, 18.0), 0.85, 'ball')
    m.box([6.0, 3.0, 14.0], [6.6, 6.0, 17.0], 'metal')                                    # ticket dispenser
    m.box([6.6, 3.6, 15.0], [6.7, 4.6, 16.4], 'ticket')


SLOT = [('#e86a1a', '#c8a040'), ('#a8281f', '#c8c8cc'), ('#2a6a3a', '#c8a040'), ('#1e1f20', '#c8c8cc'),
        ('#c8c8cc', '#e83a2a'), ('#c8a040', '#1e1f20')]


def slot_machine(m, k=0):
    """A one-armed bandit on its stand: the cast body with raised panels and the crest on top, the lit reel window
    with three reels and the pay line, the award card, the coin slot, the payout cup, the side lever with its red
    ball knob."""
    m.box([-5.0, 0.0, -4.5], [5.0, 1.0, 4.5], 'dark')
    m.box([-4.6, 1.0, -4.1], [4.6, 10.0, 4.1], 'stand')
    m.box([-4.6, 9.4, -4.1], [4.6, 10.0, 4.4], 'trim')
    m.box([-4.5, 10.0, -4.0], [4.5, 21.0, 4.0], 'body')
    m.box([-4.0, 21.0, -3.6], [4.0, 22.6, 3.6], 'body')
    blob(m, (0.0, 23.0, 0.0), (3.0, 1.2, 2.4), 'trim')
    for s in (-1, 1):                                                                     # raised side panels
        x0, x1 = sorted([s * 4.5, s * 4.7])
        m.box([x0, 12.0, -2.8], [x1, 19.4, 2.8], 'trim')
    m.box([-3.6, 14.8, 4.0], [3.6, 19.0, 4.25], 'trim')
    m.box([-3.1, 15.2, 4.25], [3.1, 18.6, 4.3], 'glow')                                   # reels
    m.box([-3.4, 19.3, 4.0], [3.4, 20.7, 4.18], 'award')
    m.box([2.0, 22.6, 0.6], [3.0, 22.7, 1.6], 'dark')
    m.box([-3.0, 10.4, 4.0], [3.0, 11.0, 6.0], 'trim')                                    # payout cup
    m.box([-3.0, 10.4, 5.6], [3.0, 12.2, 6.0], 'trim')
    for x0, x1 in ((-3.0, -2.6), (2.6, 3.0)):
        m.box([x0, 10.4, 4.0], [x1, 12.2, 6.0], 'trim')
    m.box([-2.0, 12.4, 4.0], [2.0, 13.6, 4.2], 'dark')
    m.rod(4.5, 5.6, 0.9, 'chrome', cx=16.0, cz=0.0, axis=0)                               # the lever
    m.rod(16.0, 24.0, 0.3, 'chrome', cx=5.6, cz=0.0)
    blob(m, (5.6, 24.6, 0.0), 0.9, 'knob')


# ================================================================== looks


def _looks():
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.4, chips=0.3))      # noqa: E731
    wood = lambda a, b: (lambda f: P.wood(f, a, b))                              # noqa: E731
    dark = A.plastic('#1e1f20')
    out = {}

    out['dartboard'] = [dict(b=dartboard, board=dartboard_face(sa, sb, ra, rb, rings), wood=wd, stand=A.plastic('#1e1f20'),
                             backing=bk, metal=A.steel, shaft=A.plastic(sc), flight=A.plastic(fc),
                             chalk=chalkboard('501'), chalkstick=A.flat('#f0f0ea'), eraser=A.fabric('#3a3a3a'),
                             _px={'board': 14, 'chalk': 10})
                        for sa, sb, ra, rb, rings, wd, bk, sc, fc in (
                            ('#141414', '#e8dcc0', '#c8202a', '#2a8a3a', False, wood('#6a4428', '#3a2414'),
                             A.fabric('#2a2422'), '#2a2a2a', '#c8202a'),
                            ('#141414', '#e8dcc0', '#c8202a', '#2a8a3a', False, wood('#c9a77a', '#8a6440'),
                             A.fabric('#8a6a48'), '#2a6ae8', '#f0c020'),
                            ('#2a8a3a', '#f0ece0', '#c8202a', '#c8202a', True, wood('#8a5a34', '#5a3a1a'),
                             A.fabric('#b89a6a'), '#c8202a', '#c8202a'),
                            ('#141414', '#e8dcc0', '#c8202a', '#2a8a3a', False, paint('#1e1f20'),
                             A.fabric('#1e1f20'), '#e8e4da', '#40a040'))]
    out['pool_cue'] = [dict(b=pool_cue, stand=paint('#2a2a2a'), rubber=A.plastic('#141414'), butt=wood(b1, b2),
                            wrap=A.fabric(wr), inlay=A.flat(il), metal=A.steel, shaft=wood('#e8d4a8', '#c8a878'),
                            ferrule=A.flat('#f0ece0'), tip=A.flat('#2a5aa8'), case=A.fabric(cs), case2=A.fabric(c2),
                            trim=A.plastic('#1e1f20'), strap=A.fabric('#1e1f20'), chalk=A.flat('#2a6ae8'))
                       for b1, b2, wr, il, cs, c2 in (('#3a1a10', '#1a0a08', '#2a2a2a', '#e8dcc0', '#1e1f20', '#2a2a2a'),
                                                      ('#7a3a1a', '#4a1a0a', '#1e3a6a', '#f0ece0', '#5a3a1a', '#7a4a2a'),
                                                      ('#1e1f20', '#0a0a0a', '#a8281f', '#c8c8cc', '#a8281f', '#1e1f20'),
                                                      ('#8a5a2a', '#5a3a1a', '#3a5a3a', '#1e1f20', '#2a3a5a', '#3a4a6a'))]
    out['chess_set'] = []
    for light, darkc, bord, pw, pb in (('#e8d4a8', '#6a4428', '#3a2414', ('#f0dcb0', '#c8a878'), ('#3a2414', '#1a0a04')),
                                       ('#f0ece0', '#2a5a3a', '#1e1f20', ('#f0ece0', '#c8c4b8'), ('#2a2a2a', '#0a0a0a')),
                                       ('#d8b888', '#8a4a2a', '#5a2a14', ('#e8c890', '#b89060'), ('#6a2a14', '#3a1408'))):
        for pos in range(2):
            out['chess_set'].append(dict(b=chess_set, board=chess_top(light, darkc, bord), pw=wood(*pw), pb=wood(*pb),
                                         clock=wood('#6a4428', '#3a2414'), clockface=A.flat('#f0ece0'),
                                         clockhand=A.flat('#141414'), metal=A.steel, _px={'board': 14}))
    out['foosball_table'] = [dict(b=foosball_table, cab=cab, leg=leg, field=foos_field('#2a7a3a', '#f0f0f0'),
                                  dark=A.plastic('#141414'), rod=A.chrome, grip0=A.plastic('#1e1f20'),
                                  grip1=A.plastic('#1e1f20'), team0=A.plastic(t0), team1=A.plastic(t1),
                                  head=A.plastic('#e8c8a0'), shorts=A.plastic('#1e1f20'), metal=A.steel,
                                  ball=A.flat('#f0f0f0'), _px={'field': 8})
                             for cab, leg, t0, t1 in ((wood('#8a5a34', '#5a3a1a'), paint('#1e1f20'), '#c8202a', '#2a4ae8'),
                                                      (paint('#1e1f20'), paint('#2a2a2a'), '#f0c020', '#1e1f20'),
                                                      (paint('#e8e4da'), A.chrome, '#e83a2a', '#e8e4da'),
                                                      (wood('#c9a77a', '#8a6440'), paint('#3a3a3a'), '#2a8a3a', '#f06a1a'))]
    out['ping_pong_table'] = [dict(b=ping_pong_table, top=pingpong_top(t, '#f0f0f0'), stand=A.flat(st, 0.18),
                                   frame=paint('#1e1f20'), metal=A.steel, net=net(), tape=A.flat('#f0f0f0'),
                                   rubber=A.plastic('#1a1a1a'), bat=A.skin('#c8202a'), bat2=A.skin('#1a1a1a'),
                                   handle=wood('#c9a77a', '#8a6440'), ball=A.flat('#f8f4e8'), _px={'net': 10})
                              for t, st in (('#2a6a4a', '#a8a8a0'), ('#2a4a8a', '#8a8a84'))]
    out['poker_chip_case'] = [dict(b=poker_chip_case, case=cs, metal=A.alu, tray=A.fabric(tr), lining=A.fabric(ln),
                                   chip0=chip_row('#c8202a'), chip1=chip_row('#2a4ae8'), chip2=chip_row('#1e1f20'),
                                   chip3=chip_row('#2a8a3a'), chip4=chip_row('#f0ece0', '#c8202a'),
                                   deck0=sign('CARDS', '#a8281f', '#f0ece0', faces=('front', 'top')),
                                   deck1=sign('CARDS', '#1e3a8a', '#f0ece0', faces=('front', 'top')),
                                   dice=A.flat('#f0ece0'), pip=A.flat('#c8202a'), handle=A.plastic('#1e1f20'),
                                   _px={'chip0': 12, 'chip1': 12, 'chip2': 12, 'chip3': 12, 'chip4': 12, 'deck0': 14,
                                        'deck1': 14})
                              for cs, tr, ln in ((A.alu, '#1e1f20', '#1e1f20'), (A.plastic('#1e1f20'), '#2a2a2a', '#1a3a2a'),
                                                 (wood('#6a3a1a', '#3a1a0a'), '#5a1a1a', '#2a4a2a'))]
    out['bowling_ball_pins'] = [dict(b=bowling_ball_pins, pin=A.flat('#f4f0e6', 0.04), stripe=A.flat('#c8202a'),
                                     ball=swirl(c1, c2), hole=A.flat('#0a0a0a'))
                                for c1, c2 in (('#1e1f20', '#4a4a50'), ('#2a4ae8', '#80a0f0'), ('#c8202a', '#f0a020'),
                                               ('#2a8a3a', '#e8e4da'), ('#6a2a8a', '#f070b0'), ('#e86a1a', '#f0c020'),
                                               ('#1a1a3a', '#40c0f0'), ('#8a6a2a', '#e8c890'))]
    out['punching_bag'] = [dict(b=punching_bag, frame=paint(fr), metal=A.steel, plate=paint('#1e1f20'),
                                chain=A.steel, bag=lambda f, c=bc: P.leather(f, c), trim=A.flat(tc), trim2=A.flat(t2),
                                glove=A.skin(gc), cuff=A.flat('#f0ece0'))
                           for fr, bc, tc, t2, gc in (('#1e1f20', '#1e1f20', '#f0ece0', '#c8202a', '#c8202a'),
                                                      ('#3a3a3a', '#8a1a1a', '#1e1f20', '#f0ece0', '#1e1f20'),
                                                      ('#2a2a2a', '#8a6a48', '#3a2a1a', '#3a2a1a', '#2a4ae8'))]
    out['arcade_cabinet'] = []
    for cab, c1, c2, word, mb, mi, kind, s1, s2, s3 in ARCADE:
        out['arcade_cabinet'].append(dict(
            b=arcade_cabinet, side=side_art(c1, c2)(cab), cab=paint(cab), tmold=A.plastic(c1), dark=A.plastic('#141414'),
            metal=A.steel, glow4=neon('#e83a2a'), panel=sign(word, A.tone(cab, 1.2), c1, faces=('top',), h=0.25),
            knob=A.plastic(c1), btn0=A.plastic('#e83a2a'), btn1=A.plastic('#f0c020'), bezel=A.plastic('#141414'),
            glow=screen(kind, s1, s2, s3), grille=net('#3a3a3a', '#141414'), glow3=sign(word, mb, mi, frame=c1),
            _px={'glow': 12, 'glow3': 12, 'panel': 8}))
    out['pool_table'] = [dict(b=pool_table, wood=wd, rail=wd, cloth=A.fabric(cl), cushion=A.fabric(A.hexof(A.tone(cl, 0.85))),
                              pearl=A.flat('#f0ece0'), dark=A.plastic('#0e0e0e'), leather=A.fabric('#3a2414'),
                              metal=P.brass if k < 4 else A.chrome, chalk=A.flat('#2a6ae8'), cue=wood('#e8d4a8', '#3a1a10'),
                              b0=A.flat('#f4f0e6', 0.02), b1=A.flat('#f0c020', 0.02), b2=A.flat('#2a4ae8', 0.02),
                              b3=A.flat('#c8202a', 0.02), b4=A.flat('#6a2a8a', 0.02), b5=A.flat('#f06a1a', 0.02),
                              b6=A.flat('#2a8a3a', 0.02), b7=A.flat('#7a1a1a', 0.02), b8=A.flat('#141414', 0.02))
                         for k, (cl, wd) in enumerate((('#2a7a3a', wood('#6a3a1a', '#3a1a0a')),
                                                       ('#2a5a9a', wood('#c9a77a', '#8a6440')),
                                                       ('#7a1a2a', wood('#4a2414', '#2a1408')),
                                                       ('#1a5a3a', wood('#8a5a34', '#5a3a1a')),
                                                       ('#2a6a8a', paint('#1e1f20')),
                                                       ('#a8281f', paint('#2a2a2a'))))]
    out['air_hockey_table'] = [dict(b=air_hockey_table, cab=side_art(c1, '#f0f0f0')(cb), leg=paint('#1e1f20'),
                                    dark=A.plastic('#141414'), field=hockey_top('#c8202a', '#2a4ae8'), rail=paint(rl),
                                    metal=A.steel, glow=score('07', '#141414', '#e83a2a', faces=('left', 'right')),
                                    glow2=score('05', '#141414', '#2a8ae8', faces=('left', 'right')),
                                    trim=A.plastic(c1), puck=A.plastic('#c8202a'), mallet0=A.plastic('#c8202a'),
                                    mallet1=A.plastic('#2a4ae8'), _px={'field': 6, 'glow': 10, 'glow2': 10})
                               for cb, c1, rl in (('#1e1f20', '#e83a2a', '#1e1f20'), ('#2a4a8a', '#f0c020', '#1e1f20'),
                                                  ('#e8e4da', '#2a6ae8', '#c8c8cc'))]
    out['poker_table'] = [dict(b=poker_table, pedestal=paint('#1e1f20'), wood=wood('#6a3a1a', '#3a1a0a'),
                               wood2=wood('#a86a34', '#6a3a1a'), felt=A.fabric(fl), rail=lambda f: P.leather(f, '#1a1a1a'),
                               line=A.flat('#e8c860'), metal=A.chrome, card=A.flat('#f8f4ec'),
                               cardback=A.flat(cb), pip=A.flat('#c8202a'), pip2=A.flat('#141414'),
                               chip0=chip_row('#c8202a'), chip1=chip_row('#2a4ae8'), chip2=chip_row('#1e1f20'),
                               chip3=chip_row('#2a8a3a'), dark=A.plastic('#141414'), button=A.flat('#f0ece0'))
                          for fl, cb in (('#2a6a3a', '#a8281f'), ('#1a3a6a', '#2a2a5a'))]
    out['home_console'] = [dict(b=home_console, shell=A.plastic(sh_), trim=A.plastic(tr), btn=A.plastic(bt),
                                cart=A.plastic(ct), cartlabel=sign(word, lb, '#f8f0e0', h=0.3),
                                cartlabel2=sign(CONSOLE[(k + 2) % 6][5], CONSOLE[(k + 2) % 6][4], '#f8f0e0',
                                                faces=('top',), h=0.3),
                                dark=A.plastic('#141414'), glow4=neon('#e83a2a'), cord=A.plastic('#2a2a2a'),
                                b1=A.plastic('#c8202a'), b2=A.plastic('#f0c020'), b3=A.plastic('#2a4ae8'),
                                b4=A.plastic('#2a8a3a'), _px={'cartlabel': 14, 'cartlabel2': 14})
                           for k, (sh_, tr, bt, ct, lb, word) in enumerate(CONSOLE)]
    out['cocktail_arcade'] = [dict(b=cocktail_arcade, cab=paint(cab) if cab != '#2a1a10' and cab != '#5a3a1a'
                                   and cab != '#3a2a1a' else wood(A.hexof(A.tone(cab, 2.2)), cab),
                                   metal=A.chrome, dark=A.plastic('#141414'), topglass=A.flat('#141820', 0.02),
                                   glow=screen(kind, s1, s2, s3, faces=('top',)), card=sign('INSERT COIN', '#f0ece0',
                                                                                         '#1e1f20', faces=('top',)),
                                   panel=A.plastic('#1e1f20'), knob=A.plastic('#c8202a'), btn0=A.plastic('#f0c020'),
                                   btn1=A.plastic('#c8202a'), glow4=neon('#e83a2a'), _px={'glow': 12, 'card': 12})
                              for cab, _, kind, s1, s2, s3 in COCKTAIL]
    out['pinball_machine'] = [dict(b=pinball_machine, cab=side_art(i1, i2)(cab), metal=A.chrome,
                                   field=playfield(pf, i1, i2, '#f0f0f0'), trim=A.plastic('#f0f0f0'),
                                   glow2=neon(i1), sling=A.plastic(i2), rubber=A.plastic('#f0f0f0'),
                                   flipper=A.plastic('#f0f0f0'), btn=A.plastic('#c8202a'), apron=A.plastic(A.tone(pf, 0.6)),
                                   chrome=A.chrome, knob=A.plastic('#c8202a'), glow4=neon('#e83a2a'),
                                   glow3=sign(title, bg, i1, frame=i2, h=0.3), glow=score('1250630', '#141008', '#f0a020'),
                                   _px={'glow3': 12, 'glow': 12, 'field': 8})
                              for cab, bg, title, pf, i1, i2 in PINBALL]
    out['claw_machine'] = [dict(b=claw_machine, cab=paint(cb), dark=A.plastic('#141414'), trim=A.plastic(tc),
                                metal=A.chrome, glow4=neon('#e83a2a'), panel=paint(tc), knob=A.plastic('#c8202a'),
                                glow2=neon('#e83a2a'), floor=A.flat('#2a2a2a'), mirror=A.flat('#8a96a0', 0.02),
                                p0=A.fabric('#f070b0'), p1=A.fabric('#f0e040'), p2=A.fabric('#60c0f0'),
                                p3=A.fabric('#f0f0f0'), eye=A.flat('#141414'), chute=A.flat('#c8dce8', 0.02),
                                wire=A.steel, glow=neon('#f8f4f0'), glow3=sign(word, tc, cb, frame=cb),
                                _px={'glow3': 12})
                           for cb, tc, word in CLAW]
    out['skee_ball_lane'] = [dict(b=skee_ball_lane, cab=paint(cb), glow=neon(rl), lane=wood('#e8c890', '#b08a58'),
                                  target=skee_target(tf, tr, '#141414'), net=net('#e8e8e8', '#1e1f20'),
                                  glow3=sign('ALLEY ROLL', cb, rl, frame=rl), glow2=score('0350', '#141414', '#e83a2a'),
                                  trim=A.plastic('#1e1f20'), ball=A.flat('#8a5a34', 0.08), metal=A.steel,
                                  ticket=A.flat('#f0a020'), _px={'target': 8, 'glow3': 12, 'glow2': 12})
                             for cb, rl, tf, tr in (('#a8281f', '#f0c020', '#f0c020', '#c8202a'),
                                                    ('#e85aa0', '#40c0f0', '#2a4ae8', '#f070b0'))]
    out['slot_machine'] = [dict(b=slot_machine, body=paint(bd), trim=paint(tr) if tr != '#c8c8cc' else A.chrome,
                                stand=wood('#6a3a1a', '#3a1a0a') if q % 2 == 0 else paint('#1e1f20'),
                                dark=A.plastic('#141414'), glow=reels(['7', 'cherry', 'BAR', 'bell', 'plum', '7']),
                                award=sign('JACKPOT', '#f0ece0', '#a8281f', frame='#a8281f', h=0.5), chrome=A.chrome,
                                knob=A.plastic('#c8202a'), _px={'glow': 14, 'award': 12})
                           for q, (bd, tr) in enumerate(SLOT)]
    for ls in out.values():                 # each builder hears which look it's drawing
        for k, d in enumerate(ls):
            d['b'] = (lambda fn, k: (lambda m: fn(m, k)))(d['b'], k)
    return out


def looks(it, wrap):
    """{id: [it() entry per look]}: built through wave5decor.looks' wrapper."""
    return {i: [wrap(it, d) for d in ls] for i, ls in _looks().items()}
