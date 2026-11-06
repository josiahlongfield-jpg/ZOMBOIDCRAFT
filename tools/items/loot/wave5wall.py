"""Wave 5 decor models, batch 4: wall art and display (wave 4 stage 5; specs in decor.py). Built from the photos in
refs/items/decor/<id>/ (licence_plate_collection holds licence_plate_set's). The neon clock has no photos yet, so it
isn't built (no roster row) until REFERENCES fetches some.

Every poster, film, band, team, sign and brand here is made up. Wall pieces stand on two small feet like the neon
signs, so they sit on a shelf or the floor against a wall.
"""
import calendar
import math

import numpy as np

import art as A
import paint as P
import shapes as sh
from wave4 import blob, flat
from wave5games import face_paint, put, text

# ================================================================== helpers


def feet(m, w, z=-1.0, y=0.0, d=2.4, mat='frame'):
    for x in (-w / 2 + 1.0, w / 2 - 1.0):
        m.box([x - 0.5, y, z - d], [x + 0.5, y + 1.0, z + 1.0], mat)


def frame(m, w, h, y0=1.0, z0=-0.8, z1=0.0, t=0.8, mat='frame'):
    """A picture frame: four mouldings round a w x h opening, the front at z1."""
    for x0, x1, ya, yb in ((-w / 2 - t, w / 2 + t, y0, y0 + t), (-w / 2 - t, w / 2 + t, y0 + t + h, y0 + 2 * t + h),
                           (-w / 2 - t, -w / 2, y0, y0 + 2 * t + h), (w / 2, w / 2 + t, y0, y0 + 2 * t + h)):
        m.box([x0, ya, z0], [x1, yb, z1 + 0.2], mat)


def motif(c, u, v, kind, cx, cy, s, col):
    """A bold poster graphic s across at (cx, cy) (u right, v down)."""
    x, y = (u - cx) / s, (v - cy) / s
    r = np.hypot(x, y)
    if kind == 'guitar':
        body = (np.hypot(x / 0.55, (y - 0.25) / 0.45) < 1) | (np.hypot(x / 0.42, (y + 0.15) / 0.35) < 1)
        neck = (np.abs(x) < 0.07) & (y < -0.3) & (y > -1.0)
        m = body | neck
        c = put(c, m, col)
        return put(c, np.hypot(x, y - 0.1) < 0.12, '#141414')
    if kind == 'star':
        a = np.arctan2(y, x)
        return put(c, r < 0.32 + 0.38 * (np.cos(a * 5 + np.pi / 2) * 0.5 + 0.5) ** 3, col)
    if kind == 'skull':
        c = put(c, (np.hypot(x / 0.6, y / 0.55) < 1) | ((np.abs(x) < 0.35) & (y > 0.2) & (y < 0.75)), col)
        for ex in (-0.25, 0.25):
            c = put(c, np.hypot(x - ex, y - 0.05) < 0.15, '#141414')
        return put(c, (np.abs(x) < 0.3) & (y > 0.55) & (y < 0.75) & ((x * 10) % 1 < 0.3), '#141414')
    if kind == 'bolt':
        m = (np.abs(x - (-y * 0.4)) < 0.18) & (np.abs(y) < 0.9) | ((np.abs(y) < 0.1) & (np.abs(x) < 0.45))
        return put(c, m, col)
    if kind == 'sun':
        a = np.arctan2(y, x)
        c = put(c, (r < 0.95) & (np.cos(a * 12) > 0.6) & (r > 0.5), col)
        return put(c, r < 0.45, col)
    if kind == 'figure':
        c = put(c, np.hypot(x, y + 0.55) < 0.22, col)
        return put(c, (y > -0.3) & (y < 1.0) & (np.abs(x) < 0.25 + (y + 0.3) * 0.3), col)
    if kind == 'couple':
        for dx in (-0.35, 0.35):
            c = put(c, np.hypot(x - dx, y + 0.5) < 0.2, col)
            c = put(c, (y > -0.28) & (y < 1.0) & (np.abs(x - dx) < 0.2 + (y + 0.28) * 0.25), col)
        return c
    if kind == 'fin':
        c = put(c, (y > 0.2) & (np.sin(x * 9) * 0.06 + 0.2 < y), A.tone(col, 0.6))
        return put(c, (y < 0.25) & (y > -0.8) & (np.abs(x + 0.1 - (y + 0.8) * 0.15) < (y + 0.8) * 0.45), col)
    if kind == 'moon':
        return put(c, (r < 0.7) & (np.hypot(x - 0.3, y + 0.15) > 0.6), col)
    if kind == 'mountain':
        c = put(c, y > 0.6 - np.maximum(0, 0.9 - np.abs(x + 0.2) * 1.4), col)
        return put(c, (y > 0.6 - np.maximum(0, 0.9 - np.abs(x + 0.2) * 1.4)) & (y < -0.05), '#f0f0f0')
    if kind == 'ship':
        c = put(c, (y > 0.2) & (y < 0.45) & (np.abs(x) < 0.8 - (y - 0.2)), col)
        return put(c, (np.abs(x) < 0.05) & (y < 0.2) & (y > -0.8) | ((x > 0.05) & (x < 0.6) & (y < 0.1) & (y > -0.7)
                                                                     & (x - 0.05 < (y + 0.7) * 0.7)), col)
    if kind == 'rocket':
        c = put(c, (np.abs(x) < 0.22) & (y > -0.6) & (y < 0.6), col)
        c = put(c, (y < -0.6) & (np.abs(x) < (y + 1.0) * 0.55), col)
        return put(c, (y > 0.3) & (y < 0.75) & (np.abs(x) < 0.22 + (y - 0.3) * 0.8), A.tone(col, 0.7))
    return c


# ================================================================== painters


BANDS = [  # band, venue line, colours (field, accent, ink), graphic
    ('THE RUSTY NAILS', 'LIVE FRI', '#f0c020', '#c8202a', '#141414', 'guitar'),
    ('DEAD AIR', 'ONE NIGHT ONLY', '#141414', '#e8e4da', '#c8202a', 'skull'),
    ('NIGHT OWLS', 'AT THE PALACE', '#2a2a5a', '#f0a020', '#f0ece0', 'moon'),
    ('VOLTAGE', 'TOUR 93', '#e8e4da', '#2a4ae8', '#141414', 'bolt'),
    ('SUNDOWNERS', 'SUMMER JAM', '#f06a1a', '#f8e040', '#2a1a10', 'sun'),
    ('BLACK RIVER', 'SAT 9PM', '#1a3a2a', '#e8c860', '#f0ece0', 'mountain'),
    ('GLAM CATS', 'LIVE', '#e85aa0', '#f8f0e0', '#2a1a3a', 'star'),
    ('IRON HORSE', 'BIKER FEST', '#3a3a3a', '#e86a1a', '#f0ece0', 'skull'),
    ('JUKEBOX JIVE', 'DANCE HALL', '#2a8a8a', '#f0c020', '#f0ece0', 'couple'),
    ('STATIC', 'ALL AGES', '#c8202a', '#141414', '#f0ece0', 'bolt'),
    ('MOONSHINE', 'BARN DANCE', '#8a5a2a', '#f0e0b0', '#2a1a10', 'moon'),
    ('THE DRIFTERS', 'FAREWELL SHOW', '#1e3a6a', '#e8c860', '#f0ece0', 'ship'),
    ('NEON TIGER', 'CLUB NIGHT', '#141414', '#f070b0', '#40e0d0', 'star'),
    ('GRAVE DIGGERS', 'HALLOWEEN', '#3a1a4a', '#f06a1a', '#e8e4da', 'skull'),
    ('HIGHWAY 9', 'COUNTY FAIR', '#e8dcc0', '#a8281f', '#1e3a6a', 'guitar'),
    ('ROCKET BOYS', 'SPACE ROCK', '#0a1a3a', '#e83a2a', '#f0ece0', 'rocket'),
]
FILMS = [  # title, tagline, colours (top, bottom, title ink), graphic
    ('DEEP WATER', 'DONT GO IN', '#1e5a8a', '#0a1a2a', '#e83a2a', 'fin'),
    ('LONE RIDER', 'A WESTERN', '#e8a040', '#6a2a10', '#f8f0e0', 'figure'),
    ('MOON BASE 9', 'NO WAY HOME', '#0a0a2a', '#2a2a5a', '#f0e040', 'rocket'),
    ('THE MUMMYS CURSE', 'IT LIVES', '#2a4a3a', '#0a1a10', '#e8c860', 'figure'),
    ('SUMMER LOVE', 'A ROMANCE', '#f0a0b0', '#a83a5a', '#f8f0e0', 'couple'),
    ('NIGHT OF THE DEAD', 'THEY RISE', '#3a0a0a', '#0a0a0a', '#c8e040', 'skull'),
    ('FAST LANE', 'NO BRAKES', '#f0c020', '#c8302a', '#141414', 'bolt'),
    ('STAR PATROL', 'BEYOND', '#2a1a4a', '#0a0a1a', '#40c0f0', 'star'),
    ('HIGH PEAK', 'SURVIVE', '#9ac0e0', '#3a5a7a', '#f8f8f8', 'mountain'),
    ('PIRATE GOLD', 'ADVENTURE', '#c8862a', '#3a2a1a', '#f0e0a0', 'ship'),
    ('THE HAUNTING', 'BEWARE', '#2a2a3a', '#0a0a10', '#e8e4da', 'moon'),
    ('DESERT SUN', 'AN EPIC', '#f06a1a', '#8a2a10', '#f8f0e0', 'sun'),
    ('ROCK CITY', 'TURN IT UP', '#141414', '#3a1a3a', '#f070b0', 'guitar'),
    ('KNOX COUNTY', 'A TRUE STORY', '#5a6a4a', '#1a2a1a', '#f0ece0', 'figure'),
    ('ATOMIC MAN', 'HERO RETURNS', '#e8e040', '#2a6ae8', '#c8202a', 'figure'),
    ('LAST TRAIN', 'A THRILLER', '#8a8a84', '#2a2a2a', '#f0c020', 'bolt'),
]


def band_poster(k):
    band, line, fld, acc, ink, g = BANDS[k % len(BANDS)]

    def draw(u, v, W, H, c, f):
        c = put(c, (v > H * 0.04) & (v < H * 0.2), acc)
        c = text(c, u, v, band, W / 2, H * 0.12, A.fit_text(band, W * 0.9, H * 0.1), ink if acc != ink else fld)
        c = motif(c, u, v, g, W / 2, H * 0.47, W * 0.32, acc)
        c = text(c, u, v, line, W / 2, H * 0.78, A.fit_text(line, W * 0.8, H * 0.06), ink)
        for q in range(3):
            c = put(c, (np.abs(v - H * (0.86 + q * 0.035)) < H * 0.008) & (np.abs(u - W / 2) < W * (0.35 - q * 0.06)),
                    ink)
        return c
    return face_paint(draw, fld, ('front',), 0.06)


def movie_poster(k):
    title, tag, top, bot, ink, g = FILMS[k % len(FILMS)]

    def draw(u, v, W, H, c, f):
        t = np.clip(v / H, 0, 1)
        c = P.mix(P.col(top), P.col(bot), t ** 1.2) * (1 + (f.noise(2.0, 4, 2) - 0.5) * 0.06)[..., None]
        c = text(c, u, v, tag, W / 2, H * 0.06, A.fit_text(tag, W * 0.6, H * 0.035), '#f0ece0')
        c = motif(c, u, v, g, W / 2, H * 0.4, W * 0.38, A.hexof(A.tone(ink, 0.9)))
        c = text(c, u, v, title, W / 2, H * 0.72, A.fit_text(title, W * 0.92, H * 0.1), ink)
        for q in range(4):          # the credits block
            c = put(c, (np.abs(v - H * (0.83 + q * 0.03)) < H * 0.006) & (np.abs(u - W / 2) < W * 0.4)
                    & ((u * 3 / W * 7) % 1 < 0.8), '#c8c4b8')
        return c
    return face_paint(draw, top, ('front',), 0.0)


MONTHS = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER',
          'NOVEMBER', 'DECEMBER']
SCENES = ['mountain', 'moon', 'sun', 'figure', 'sun', 'ship', 'sun', 'fin', 'mountain', 'moon', 'mountain', 'star']


def calendar_page(month):
    """A 1993 calendar page: the month's picture, the name, the day grid with Sundays in red."""
    first, days = calendar.monthrange(1993, month + 1)
    first = (first + 1) % 7                                        # Sunday first

    def draw(u, v, W, H, c, f):
        c = put(c, v < H * 0.42, '#7aa0c0')
        c = motif(c, u, v, SCENES[month], W / 2, H * 0.24, W * 0.3, '#3a6a3a' if month % 3 else '#e8c040')
        c = put(c, (v > H * 0.42) & (v < H * 0.5), '#a8281f')
        c = text(c, u, v, MONTHS[month] + ' 1993', W / 2, H * 0.46, A.fit_text(MONTHS[month] + ' 1993', W * 0.8,
                                                                                    H * 0.05), '#f0ece0')
        cw, ch = W * 0.9 / 7, H * 0.44 / 6
        x0, y0 = W * 0.05, H * 0.53
        for q in range(7):
            c = text(c, u, v, 'SMTWTFS'[q], x0 + (q + 0.5) * cw, y0 + ch * 0.4, ch * 0.32, '#a8281f')
        for d in range(days):
            cell = first + d
            col, row = cell % 7, cell // 7 + 1
            if row > 5:
                row, col = 5, col
            c = text(c, u, v, str(d + 1), x0 + (col + 0.5) * cw, y0 + (row + 0.5) * ch, ch * 0.4,
                     '#a8281f' if col == 0 else '#1e1f20')
        c = put(c, ((np.abs(((u - x0) / cw) % 1) < 0.03) | (np.abs(((v - y0) / ch) % 1) < 0.03)) & (v > y0 + ch * 0.8)
                & (u > x0) & (u < W - x0), '#b8b4a8')
        return c
    return face_paint(draw, '#f4f0e4', ('front',), 0.03)


def pennant_paint(word, fld, ink, x0, x1, yc):
    """Felt pennant colour with its word along the length (absolute coordinates: the pennant's own span)."""
    def fn(f):
        c = A.plain(f, fld, 0.08)
        if f.n[2] <= 0:
            return c
        u = f.P[..., 0] - x0
        v = yc - f.P[..., 1]
        L = x1 - x0
        c = text(c, u, v + 0.0, word, L * 0.55, 0.0, A.fit_text(word, L * 0.6, 1.4), ink)
        return put(c, u < L * 0.12, ink)
    return fn


TIN = [  # word, line 2, field, ink, round?
    ('MOTOR OIL', 'SINCE 1922', '#1e3a6a', '#f0c020', False), ('COLD COLA', 'DRINK', '#c8202a', '#f8f0e0', True),
    ('GASOLINE', 'HI POWER', '#f0c020', '#c8202a', True), ('TIRES', 'ROAD KING', '#141414', '#f0ece0', False),
    ('FRESH BREAD', 'DAILY', '#e8dcc0', '#8a2a1a', False), ('ICE CREAM', 'SOLD HERE', '#f0a0b0', '#2a3a8a', True),
    ('ROOT BEER', 'ICE COLD', '#6a2a14', '#f0e0a0', True), ('SPARK PLUGS', 'FIRE RIGHT', '#2a6a3a', '#f0ece0', False),
    ('FEED SEED', 'FARM SUPPLY', '#3a6a2a', '#f0e040', False), ('BAIT', 'LIVE WORMS', '#1e5a8a', '#f0ece0', False),
    ('TOBACCO', 'FINE CUT', '#8a5a2a', '#f0e0a0', False), ('BATTERIES', 'LONG LIFE', '#e86a1a', '#141414', False),
    ('COFFEE', 'HOT', '#3a2414', '#e8c860', True), ('AUTO PARTS', 'OPEN', '#c8202a', '#f0ece0', False),
    ('NO PARKING', 'TOW ZONE', '#f0ece0', '#c8202a', False), ('ROUTE 66', 'MAIN ST', '#141414', '#f0ece0', True),
]


def tin_face(k):
    word, line, fld, ink, _ = TIN[k % len(TIN)]

    def draw(u, v, W, H, c, f):
        e = min(W, H) * 0.07
        edge = (u < e) | (u > W - e) | (v < e) | (v > H - e)
        c = put(c, edge, ink)
        c = text(c, u, v, word, W / 2, H * 0.42, A.fit_text(word, W * 0.8, H * 0.3), ink)
        c = text(c, u, v, line, W / 2, H * 0.72, A.fit_text(line, W * 0.6, H * 0.12), ink)
        rust = f.noise(1.4, 77, 3) > 0.72
        c = np.where(rust[..., None], P.mix(c, P.col('#7a3a1a'), 0.6), c)
        for hx, hy in ((e * 1.5, e * 1.5), (W - e * 1.5, e * 1.5), (e * 1.5, H - e * 1.5), (W - e * 1.5, H - e * 1.5)):
            c = put(c, np.hypot(u - hx, v - hy) < e * 0.4, '#3a3a3a')
        return c
    return face_paint(draw, fld, ('front',), 0.05)


STATES = [('KENTUCKY', '#f0f0f0', '#1e3a8a'), ('OHIO', '#f0e040', '#1e1f20'), ('TEXAS', '#f0ece0', '#c8202a'),
          ('YUKON', '#f0c020', '#141414'), ('QUEBEC', '#c8202a', '#f0ece0'), ('MAINE', '#f0f0f0', '#2a6a3a'),
          ('NEVADA', '#2a4a8a', '#f0ece0'), ('IOWA', '#f0ece0', '#1e1f20'), ('IDAHO', '#c8302a', '#f0ece0'),
          ('OREGON', '#e8dcc0', '#2a5a2a'), ('ALASKA', '#f0c020', '#1e3a8a'), ('UTAH', '#f0ece0', '#8a3a1a')]


def plate(state, fld, ink, num):
    def draw(u, v, W, H, c, f):
        e = min(W, H) * 0.06
        c = put(c, (u < e) | (u > W - e) | (v < e) | (v > H - e), ink)
        c = text(c, u, v, state, W / 2, H * 0.2, A.fit_text(state, W * 0.6, H * 0.14), ink)
        c = text(c, u, v, num, W / 2, H * 0.58, A.fit_text(num, W * 0.85, H * 0.42), ink)
        for hx in (W * 0.22, W * 0.78):
            c = put(c, np.hypot(u - hx, v - H * 0.12) < e * 0.5, '#3a3a3a')
        rust = f.noise(1.2, 9, 3) > 0.78
        return np.where(rust[..., None], P.mix(c, P.col('#7a4a2a'), 0.5), c)
    return face_paint(draw, fld, ('front',), 0.04)


def butterflies(back, palettes, n=(4, 3)):
    """Pinned butterflies laid out in rows on the case's backing board, each with forewings and hindwings, an eye
    spot and dark veins and edges."""
    def draw(u, v, W, H, c, f):
        cols, rows = n
        for i in range(cols):
            for j in range(rows):
                cx, cy = W * (i + 0.5) / cols, H * (0.12 + (j + 0.5) / rows * 0.82)
                s = min(W / cols, H / rows) * 0.42
                c1, c2 = palettes[(i + j * 2) % len(palettes)]
                x, y = (u - cx) / s, (v - cy) / s
                fore = np.hypot((np.abs(x) - 0.45) / 0.5, (y + 0.15) / 0.38) < 1
                hind = np.hypot((np.abs(x) - 0.3) / 0.32, (y - 0.38) / 0.36) < 1
                wing = fore | hind
                c = put(c, wing, c1)
                c = put(c, wing & (np.hypot((np.abs(x) - 0.55) / 0.12, (y + 0.15) / 0.1) < 1), c2)
                edge = wing & ~(np.hypot((np.abs(x) - 0.45) / 0.42, (y + 0.15) / 0.3) < 1) & ~hind
                c = put(c, edge, '#1a1a1a')
                c = put(c, (np.abs(x) < 0.05) & (np.abs(y) < 0.5), '#2a1a10')
            c = text(c, u, v, 'LEPIDOPTERA', W / 2, H * 0.05, H * 0.03, '#3a3a3a')
        return c
    return face_paint(draw, back, ('front',), 0.03)


def disc_face(metal, label, word):
    """A presented record: the grooves catching the light, the centre label with its title."""
    def draw(u, v, W, H, c, f):
        x, y = u - W / 2, v - H / 2
        R = min(W, H) / 2
        r = np.hypot(x, y) / R
        a = np.arctan2(y, x)
        c = c * (0.85 + 0.15 * np.cos(r * 120) + 0.25 * np.cos(a * 2) ** 8)[..., None]
        c = put(c, r < 0.33, label)
        c = text(c, u, v, word, W / 2, H / 2 - R * 0.12, R * 0.1, '#141414')
        return put(c, r < 0.04, '#141414')
    return face_paint(draw, metal, ('front',), 0.02)


def parchment(kind, tint, ink, sea):
    def draw(u, v, W, H, c, f):
        age = f.noise(0.6, 41, 3)
        c = c * (0.9 + age * 0.2)[..., None]
        e = min(W, H) * 0.05
        c = put(c, (np.abs(u - e) < e * 0.15) | (np.abs(u - (W - e)) < e * 0.15) | (np.abs(v - e) < e * 0.15)
                | (np.abs(v - (H - e)) < e * 0.15), ink)
        if kind == 'world':
            for cx in (W * 0.28, W * 0.72):
                R = min(W * 0.22, H * 0.4)
                x, y = u - cx, v - H * 0.5
                d = np.hypot(x, y)
                c = put(c, d < R, sea)
                land = (d < R * 0.95) & (f.noise(0.35, int(cx) + 3, 4) > 0.55)
                c = put(c, land, tint)
                c = put(c, land & (f.noise(0.35, int(cx) + 3, 4) > 0.62), A.hexof(A.tone(tint, 0.85)))
                c = put(c, np.abs(d - R) < R * 0.02, ink)
                c = put(c, (d < R) & ((np.abs(x) < R * 0.005) | (np.abs(y) < R * 0.005)
                                      | (np.abs(np.hypot(x * 0.5, y) - R * 0.5) < R * 0.005)), ink)
        else:                                   # the county: a river, the roads, the towns, the back roads dashed
            c = put(c, np.abs(v - H * (0.5 + 0.18 * np.sin(u / W * 6))) < H * 0.02, sea)
            for k, (a, b) in enumerate(((0.1, 0.9), (0.3, 0.2), (0.8, 0.6))):
                road = np.abs(v - H * (a + (b - a) * u / W)) < H * 0.008
                c = put(c, road, ink)
            back = (np.abs(u - W * (0.4 + 0.1 * np.sin(v / H * 9))) < W * 0.004) & ((v / H * 30) % 1 < 0.5)
            c = put(c, back, '#a8281f')
            for tx, ty in ((0.25, 0.3), (0.6, 0.45), (0.75, 0.75), (0.4, 0.7)):
                c = put(c, np.hypot(u - W * tx, v - H * ty) < min(W, H) * 0.025, ink)
        rose = np.hypot(u - W * 0.85, v - H * 0.82)
        R = min(W, H) * 0.08
        a = np.arctan2(v - H * 0.82, u - W * 0.85)
        c = put(c, rose < R * (0.4 + 0.6 * np.abs(np.cos(a * 2)) ** 6), ink)
        c = put(c, (np.abs(u - W / 2) < W * 0.18) & (np.abs(v - H * 0.08) < H * 0.035), A.hexof(A.tone(tint, 0.9)))
        stain = f.noise(1.1, 47, 3) > 0.74
        return np.where(stain[..., None], P.mix(c, P.col('#8a6a3a'), 0.35), c)
    return face_paint(draw, tint, ('front',), 0.05)


TEAMS = [  # name over number, number, shirt, trim, ink
    ('KOVAC', '12', '#f0f0f0', '#2a8a3a', '#1e3a6a'), ('DUNN', '5', '#c8202a', '#f0f0f0', '#f0f0f0'),
    ('REYES', '23', '#1e3a8a', '#f0c020', '#f0f0f0'), ('MILLER', '7', '#f0c020', '#141414', '#141414'),
    ('OKAFOR', '9', '#2a8a3a', '#f0f0f0', '#f0f0f0'), ('BRANDT', '33', '#6a1a2a', '#e8c860', '#e8c860'),
    ('HAYES', '1', '#e86a1a', '#141414', '#141414'), ('SATO', '17', '#141414', '#c8202a', '#f0f0f0'),
    ('LANE', '44', '#5a2a8a', '#f0c020', '#f0f0f0'), ('BOONE', '88', '#6a8ac0', '#1e1f20', '#1e1f20'),
]


def jersey_face(k):
    name, num, shirt, trim, ink = TEAMS[k % len(TEAMS)]

    def draw(u, v, W, H, c, f):
        c = text(c, u, v, name, W / 2, H * 0.2, A.fit_text(name, W * 0.6, H * 0.08), ink)
        c = text(c, u, v, num, W / 2, H * 0.52, A.fit_text(num, W * 0.6, H * 0.4), ink)
        sig = np.abs(v - H * (0.76 + 0.03 * np.sin(u / W * 30) + 0.02 * np.sin(u / W * 71))) < H * 0.006
        return put(c, sig & (u > W * 0.25) & (u < W * 0.8), '#1a1a5a')
    return face_paint(draw, shirt, ('front',), 0.05)


def stained(palette, figure, seed=0):
    """Leaded glass after the photos: big irregular panes in deep amber, ruby, violet and green, every pane cut out
    by a heavy dark came, the figure's halo and face their own panes, a border of small squares."""
    def draw(u, v, W, H, c, f):
        rng = np.random.default_rng(seed + 7)
        pts = rng.random((22, 2)) * [W, H]
        d = np.stack([np.hypot(u - px, v - py) for px, py in pts])
        o = np.sort(d, axis=0)
        near = np.argmin(d, axis=0)
        c = P.col(palette[0]) * np.ones(u.shape + (3,))
        for q in range(len(pts)):
            c = put(c, near == q, palette[q % len(palette)])
        lead = (o[1] - o[0]) < W * 0.035
        bw = W * 0.08
        border = (u < bw) | (u > W - bw) | (v < bw) | (v > H - bw)
        sq = border & (((u // (bw * 0.9)) + (v // (bw * 0.9))) % 2 == 0)
        c = put(c, border, palette[1])
        c = put(c, sq, palette[2])
        lead &= ~border
        lead |= border & ((np.abs(u % (bw * 0.9)) < W * 0.012) | (np.abs(v % (bw * 0.9)) < W * 0.012))
        x, y = (u - W / 2) / W, (v - H * 0.42) / H
        if figure:
            rh = np.hypot(x, (y + 0.12) * H / W)
            robe = (y > -0.04) & (y < 0.42) & (np.abs(x) < 0.12 + y * 0.45)
            c = put(c, robe, palette[3 % len(palette)])
            fold = robe & (np.abs(np.sin((x / (0.12 + y * 0.45 + 1e-3)) * 4.5)) < 0.12)
            lead = (lead & ~robe & (rh > 0.3)) | fold
            lead |= robe & ~((y > -0.03) & (y < 0.41) & (np.abs(x) < 0.11 + y * 0.45))
            c = put(c, rh < 0.3, '#d8a830')
            c = put(c, rh < 0.17, '#e8d0b0')
            lead |= (np.abs(rh - 0.3) < 0.025) | (np.abs(rh - 0.17) < 0.02)
            eye = (np.abs(np.abs(x) - 0.06) < 0.02) & (np.abs(rh - 0.06) < 0.03) & (y + 0.12 < 0) & (y + 0.12 > -0.03)
            c = put(c, eye, '#4a3020')
        else:
            r = np.hypot(x, (y + 0.02) * H / W)
            a = np.arctan2(y + 0.02, x)
            rose = r < 0.36
            c = put(c, rose, palette[3 % len(palette)])
            c = put(c, rose & (np.cos(a * 8) > 0.3) & (r > 0.12), palette[2])
            c = put(c, r < 0.12, '#d8a830')
            lead = (lead & ~rose) | (np.abs(r - 0.36) < 0.02) | (np.abs(r - 0.12) < 0.015) \
                | (rose & (r > 0.12) & (np.abs(np.cos(a * 8) - 0.3) < 0.1))
        c = c * (0.8 + 0.35 * f.noise(4.0, 31 + seed, 2))[..., None]
        return put(c, lead, '#1e1c1a')
    return face_paint(draw, palette[0], ('front', 'back'), 0.0)


# ================================================================== the pieces


def poster(m, k=0):
    """A poster in a thin black snap frame on a stand, its corners tacked."""
    w, h = 10.0, 15.0
    m.box([-w / 2, 1.0, -0.4], [w / 2, 1.0 + h, -0.2], 'backing')
    m.box([-w / 2 + 0.2, 1.2, -0.2], [w / 2 - 0.2, 0.8 + h, -0.15], 'print')
    frame(m, w - 0.4, h - 0.4, y0=1.0, z0=-0.4, z1=-0.15, t=0.3)
    feet(m, w, z=-0.3)


def wall_calendar(m, k=0):
    """A wall calendar hung on its nail from a small stand: the spiral binding, the month's page with picture and
    days, the edge of the pages under it."""
    w, h = 8.0, 12.0
    m.box([-0.3, 0.0, -2.0], [0.3, 15.0, -1.4], 'frame')                       # stand post
    m.box([-2.4, 0.0, -2.6], [2.4, 0.6, 0.6], 'frame')
    m.rod(-1.4, -0.4, 0.15, 'metal', cx=0.0, cz=14.4, axis=2)                  # nail
    for x in np.linspace(-w / 2 + 0.6, w / 2 - 0.6, 10):
        sh.torus(m, (x, 1.0 + h + 0.2), 0.35, 0.12, 0.12, 'metal', z=-0.3, steps=8)
    m.box([-w / 2, 1.0, -0.8], [w / 2, 1.0 + h, -0.4], 'pages')
    m.box([-w / 2, 1.0, -0.4], [w / 2, 1.0 + h, -0.3], 'print')
    m.seg((0.0, 14.4), (-w / 2 + 0.6, 1.0 + h + 0.3), 0.08, 0.08, 'metal', z=-0.9)
    m.seg((0.0, 14.4), (w / 2 - 0.6, 1.0 + h + 0.3), 0.08, 0.08, 'metal', z=-0.9)


def pennant_set(m, k=0):
    """Three felt pennants pinned overlapping on a board: each tapered to its point, the word along it, the
    stitched strip at the wide end."""
    m.box([-11.0, 1.0, -1.0], [11.0, 13.0, -0.6], 'board')
    feet(m, 22.0, z=-0.8)
    for q, (yc, x0, mat) in enumerate(((10.0, -10.0, 'p0'), (6.8, -9.0, 'p1'), (3.6, -10.0, 'p2'))):
        L, h = 19.0, 3.4
        steps = 8
        for s in range(steps):
            xa = x0 + L * s / steps
            hh = h * (1 - s / steps) / 2
            m.box([xa, yc - hh, -0.6 + q * 0.05], [xa + L / steps + 0.01, yc + hh, -0.45 + q * 0.05], mat)
        m.box([x0 - 0.6, yc - h / 2 - 0.1, -0.6 + q * 0.05], [x0, yc + h / 2 + 0.1, -0.4 + q * 0.05], 'strip')
        m.box([x0 + 0.2, yc - 0.2, -0.45], [x0 + 0.6, yc + 0.2, -0.3], 'metal')


def tin_sign(m, k=0):
    """An embossed tin sign, oblong or round, its edge rolled, nail holes in its corners, rust working in, on a
    pair of feet."""
    rnd = TIN[k % len(TIN)][4]
    if rnd:
        m.rod(-0.5, -0.2, 6.6, 'tin', cx=0.0, cz=8.0, axis=2)
        sh.torus(m, (0.0, 8.0), 6.4, 0.5, 0.5, 'rim', z=-0.2, steps=20)
    else:
        m.box([-8.0, 2.0, -0.5], [8.0, 13.0, -0.2], 'tin')
        for x0, x1, y0, y1 in ((-8.2, 8.2, 1.8, 2.2), (-8.2, 8.2, 12.8, 13.2), (-8.2, -7.8, 1.8, 13.2),
                               (7.8, 8.2, 1.8, 13.2)):
            m.box([x0, y0, -0.6], [x1, y1, -0.05], 'rim')
    feet(m, 12.0, z=-0.6)


def licence_plate_set(m, k=0):
    """A board of old licence plates screwed up in rows, every one a different state, colour and number."""
    m.box([-11.0, 1.0, -1.2], [11.0, 21.0, -0.6], 'board')
    for x in np.linspace(-10.0, 10.0, 6):
        m.box([x - 0.05, 1.0, -0.62], [x + 0.05, 21.0, -0.58], 'gap')
    feet(m, 22.0, z=-0.9)
    for r in range(4):
        for q in range(3):
            x, y = -7.0 + q * 7.0, 3.0 + r * 4.6
            m.box([x - 3.0, y, -0.6], [x + 3.0, y + 3.2, -0.4], f'pl{(r * 3 + q + k) % 12}')
            for sx in (-2.0, 2.0):
                m.box([x + sx - 0.12, y + 2.6, -0.4], [x + sx + 0.12, y + 2.84, -0.32], 'metal')


def butterfly_display(m, k=0):
    """A shadow box of pinned butterflies: the deep wooden frame, the pale backing board with the specimens in
    rows and the case label, the pins standing out from each body."""
    w, h = 16.0, 12.0
    m.box([-w / 2, 1.0, -1.6], [w / 2, 1.0 + h, -1.2], 'print')
    for x0, x1, y0, y1 in ((-w / 2 - 0.8, w / 2 + 0.8, 0.2, 1.0), (-w / 2 - 0.8, w / 2 + 0.8, 1.0 + h, 1.8 + h),
                           (-w / 2 - 0.8, -w / 2, 0.2, 1.8 + h), (w / 2, w / 2 + 0.8, 0.2, 1.8 + h)):
        m.box([x0, y0, -1.8], [x1, y1, 0.2], 'frame')
    for i in range(4):
        for j in range(3):
            x = -w / 2 + w * (i + 0.5) / 4
            y = 1.0 + h - h * (0.12 + (j + 0.5) / 3 * 0.82)
            m.box([x - 0.08, y - 0.5, -1.2], [x + 0.08, y + 0.5, -0.9], 'pin')
            blob(m, (x, y + 0.6, -0.9), 0.1, 'metal')
    feet(m, w + 1.6, z=-1.2)


SPRAY = [('#c8202a', '#2a4ae8', '#f0c020', '#2a8a3a', '#141414', '#f0f0f0'),
         ('#f070b0', '#40e0d0', '#a040f0', '#f0e040', '#f06a1a', '#141414'),
         ('#e8e4da', '#8a8a84', '#3a3a3a', '#c8202a', '#1e3a6a', '#5a6a3a')]


def spray_paint_kit(m, k=0):
    """A cardboard tray of spray cans: each can's printed body and swatch band, the domed shoulder, the coloured
    cap and a spare nozzle; a dust mask and a pair of gloves lying beside."""
    m.box([-6.0, 0.0, -3.6], [6.0, 2.4, 3.6], 'cardboard')
    m.box([-5.8, 2.0, -3.4], [5.8, 2.4, 3.4], 'dark')
    for q in range(6):
        x, z = -3.8 + (q % 3) * 3.8, -1.6 + (q // 3) * 3.2
        mat = f'c{q}'
        m.rod(0.4, 7.4, 1.25, 'can', cx=x, cz=z)
        m.rod(4.0, 6.0, 1.28, mat, cx=x, cz=z)
        m.rod(7.4, 7.9, 1.1, 'can', cx=x, cz=z)
        m.rod(7.9, 9.4, 1.15, mat, cx=x, cz=z)
        m.box([x - 0.2, 9.4, z + 0.2], [x + 0.2, 9.8, z + 0.6], 'dark')
    m.box([6.4, 0.0, -1.0], [9.4, 0.6, 2.0], 'mask')
    flat(m, (6.8, 3.0), (9.6, 4.0), 1.2, 0.3, 'glove')


def framed_gold_record(m, k=0):
    """A presentation frame: the black frame and mat board, the gold (or platinum) disc with its label, the
    miniature sleeve beside it and the engraved plaque underneath."""
    w, h = 14.0, 16.0
    m.box([-w / 2, 1.0, -1.4], [w / 2, 1.0 + h, -1.0], 'mat')
    frame(m, w, h, y0=1.0, z0=-1.6, z1=0.0, t=0.9)
    m.rod(-1.0, -0.8, 5.0, 'disc', cx=-1.0, cz=11.0, axis=2)
    m.box([3.0, 9.0, -1.0], [6.0, 13.0, -0.9], 'sleeve')
    m.box([-4.0, 2.6, -1.0], [4.0, 4.6, -0.85], 'plaque')
    feet(m, w + 1.8, z=-1.2)


def mounted_swordfish(m, k=0):
    """A mounted billfish on its plaque: the long body arched as if leaping, the sword or spear bill, the tall
    dorsal fin, the sickle tail, the pectoral fins and the glass eye."""
    sail = k == 2
    m.box([-6.0, 1.0, -2.0], [6.0, 12.0, -1.2], 'plaque')
    feet(m, 12.0, z=-1.6)
    m.box([-0.6, 6.0, -1.2], [0.6, 7.0, 0.6], 'metal')                       # the mount bar
    blob(m, (0.0, 7.0, 1.6), (12.0, 2.6, 1.6), 'fish')
    blob(m, (-6.0, 6.9, 1.6), (6.0, 2.0, 1.4), 'fish')
    blob(m, (7.0, 6.8, 1.6), (6.0, 2.2, 1.4), 'fish')
    blob(m, (0.0, 6.0, 1.6), (11.0, 1.4, 1.5), 'belly')
    m.seg((12.0, 7.4), (21.0, 7.8), 0.6 if k != 1 else 0.9, 0.6, 'fish', z=1.6)       # bill
    blob(m, (10.4, 7.8, 2.9), 0.4, 'eye')
    tall = 7.0 if sail else 3.6
    for s in range(8):
        x = 6.0 - s * (14.0 if sail else 4.0) / 8
        hh = tall * (1 - s / 9) if sail else tall * (1 - s / 8) ** 0.5
        m.box([x - (1.8 if sail else 0.6), 9.0, 1.45], [x, 9.0 + hh, 1.75], 'fin')
    m.seg((-12.0, 7.0), (-17.0, 13.0), 1.2, 0.4, 'fin', z=1.6)                    # tail
    m.seg((-12.0, 7.0), (-17.0, 1.0), 1.2, 0.4, 'fin', z=1.6)
    m.seg((-11.0, 7.0), (-13.4, 7.0), 1.6, 0.6, 'fish', z=1.6)
    m.seg((7.0, 6.0), (4.0, 3.6), 0.8, 0.3, 'fin', z=3.0)                        # pectoral


def antique_map(m, k=0):
    """An antique map behind glass in a gilt or dark wood frame: the parchment with its border, the two
    hemispheres or the county's roads and river, the compass rose and the title cartouche."""
    w, h = 20.0, 14.0
    m.box([-w / 2, 1.0, -1.0], [w / 2, 1.0 + h, -0.8], 'print')
    frame(m, w, h, y0=1.0, z0=-1.2, z1=-0.6, t=1.0)
    feet(m, w + 2.0, z=-1.0)


def stuffed_owl(m, k=0):
    """A mounted owl on its branch and turned base: the body with folded wings, the head with its facial disc,
    ear tufts on the horned owl, the big eyes and hooked beak, the talons gripping the branch."""
    horned = k == 0
    m.rod(0.0, 1.0, 4.0, 'wood')
    m.rod(1.0, 1.4, 3.6, 'wood')
    m.seg((-3.0, 1.4), (1.0, 5.0), 1.0, 1.0, 'branch', z=0.0)
    m.seg((1.0, 5.0), (4.0, 5.6), 0.9, 0.9, 'branch', z=0.0)
    blob(m, (0.6, 9.6, 0.0), (2.6, 4.0, 2.4), 'plumage')
    blob(m, (0.6, 9.0, 1.2), (2.0, 3.0, 1.4), 'breast')
    for s in (-1, 1):
        blob(m, (0.6 + s * 2.2, 9.4, -0.4), (0.8, 3.4, 1.8), 'wing')
    blob(m, (0.6, 14.6, 0.2), (2.4, 2.2, 2.2), 'plumage')
    blob(m, (0.6, 14.4, 1.6), (1.9, 1.7, 0.8), 'disc')
    for s in (-1, 1):
        blob(m, (0.6 + s * 0.85, 14.8, 2.3), 0.5, 'eye')
        blob(m, (0.6 + s * 0.85, 14.8, 2.6), 0.22, 'pupil')
        if horned:
            m.seg((0.6 + s * 1.2, 16.2), (0.6 + s * 1.8, 17.8), 0.6, 0.5, 'plumage', z=0.4)
        for q in range(3):
            m.box([0.6 + s * 1.0 - 0.4 + q * 0.4, 5.4, 0.2], [0.6 + s * 1.0 - 0.25 + q * 0.4, 6.0, 0.9], 'talon')
    m.box([0.4, 13.6, 2.2], [0.8, 14.4, 2.9], 'beak')


def signed_jersey_frame(m, k=0):
    """A signed shirt in a shadow box: the deep frame and backing, the shirt spread out with its sleeves, collar,
    name, number and the signature across it, the brass plate under it."""
    w, h = 18.0, 20.0
    m.box([-w / 2, 1.0, -2.0], [w / 2, 1.0 + h, -1.6], 'backing')
    for x0, x1, y0, y1 in ((-w / 2 - 1.0, w / 2 + 1.0, 0.0, 1.0), (-w / 2 - 1.0, w / 2 + 1.0, 1.0 + h, 2.0 + h),
                           (-w / 2 - 1.0, -w / 2, 0.0, 2.0 + h), (w / 2, w / 2 + 1.0, 0.0, 2.0 + h)):
        m.box([x0, y0, -2.2], [x1, y1, 0.4], 'frame')
    m.box([-5.0, 4.0, -1.6], [5.0, 18.0, -1.3], 'shirt')
    for s in (-1, 1):
        m.seg((s * 5.0, 17.0), (s * 8.4, 13.4), 3.0, 0.3, 'sleeve', z=-1.45)
        m.seg((s * 8.0, 13.8), (s * 8.6, 13.2), 3.2, 0.32, 'trim', z=-1.45)
    m.box([-1.6, 17.6, -1.3], [1.6, 18.2, -1.2], 'trim')
    m.box([-2.0, 1.6, -1.6], [2.0, 2.8, -1.45], 'plate')
    feet(m, w + 2.0, z=-1.2)


def stained_glass_panel(m, k=0):
    """A leaded stained glass panel in its oak frame on a stand, round topped: the painted panes, the lead cames
    and the reinforcing bars across the back."""
    w, h = 10.0, 18.0
    m.box([-w / 2, 2.0, -0.4], [w / 2, 2.0 + h, -0.1], 'glasspane')
    for y in (8.0, 14.0):
        m.box([-w / 2, y - 0.1, -0.5], [w / 2, y + 0.1, -0.4], 'lead')
    frame(m, w, h, y0=1.2, z0=-0.6, z1=0.1, t=0.8)
    for s in (-1, 1):                                                         # the stand
        m.box([s * (w / 2 + 0.4) - 0.4, 0.0, -0.6], [s * (w / 2 + 0.4) + 0.4, 6.0, 0.1], 'frame')
        m.box([s * (w / 2 + 0.4) - 0.5, 0.0, -3.0], [s * (w / 2 + 0.4) + 0.5, 0.8, 2.6], 'frame')


def film_prop_display(m, k=0):
    """A film prop in its museum case: the plinth with the brass plaque, the case's corner posts and lit top,
    and on the plinth one of the props: a robot's head, a space helmet, an alien egg, a ray gun on its stand, a
    monster's skull or a snowman costume."""
    m.box([-6.0, 0.0, -6.0], [6.0, 8.0, 6.0], 'plinth')
    m.box([-6.4, 8.0, -6.4], [6.4, 8.6, 6.4], 'trim')
    m.box([-2.4, 3.0, 6.0], [2.4, 5.0, 6.15], 'plaque')
    for x in (-6.2, 5.8):
        for z in (-6.2, 5.8):
            m.box([x, 8.6, z], [x + 0.4, 28.0, z + 0.4], 'trim')
    m.box([-6.4, 28.0, -6.4], [6.4, 29.0, 6.4], 'plinth')
    m.box([-5.6, 27.8, -5.6], [5.6, 28.0, 5.6], 'glow')
    kind = k % 6
    y = 8.6
    if kind == 0:                                    # robot head
        m.box([-1.0, y, -1.0], [1.0, y + 3.0, 1.0], 'prop2')
        m.box([-3.6, y + 3.0, -3.0], [3.6, y + 9.6, 3.2], 'prop')
        for s in (-1, 1):
            m.rod(3.2, 3.6, 1.2, 'glow2', cx=s * 1.6, cz=y + 7.2, axis=2)
            m.rod(-3.8 if s < 0 else 3.6, -3.6 if s < 0 else 3.8, 1.4, 'prop2', cx=y + 6.0, cz=0.0, axis=0)
        m.box([-2.0, y + 4.0, 3.2], [2.0, y + 4.8, 3.4], 'prop2')
        m.rod(y + 9.6, y + 12.0, 0.15, 'prop2')
        blob(m, (0.0, y + 12.3, 0.0), 0.4, 'glow2')
    elif kind == 1:                                  # space helmet
        m.rod(y, y + 1.0, 2.6, 'prop2')
        blob(m, (0.0, y + 5.6, 0.0), (4.4, 4.8, 4.4), 'prop')
        blob(m, (0.0, y + 6.0, 2.0), (3.0, 2.6, 2.6), 'visor')
        m.rod(y + 1.0, y + 1.8, 3.2, 'prop2')
        for s in (-1, 1):
            m.rod(s * 4.2 - 0.4, s * 4.2 + 0.4, 0.8, 'prop2', cx=y + 5.6, cz=0.0, axis=0)
    elif kind == 2:                                  # alien egg
        blob(m, (0.0, y + 6.0, 0.0), (3.8, 6.0, 3.8), 'prop')
        for q in range(4):
            a = q * math.pi / 2
            blob(m, (2.8 * math.cos(a), y + 1.2, 2.8 * math.sin(a)), (1.2, 1.2, 1.2), 'prop2')
        m.box([-1.6, y + 11.6, -1.6], [1.6, y + 12.0, 1.6], 'glow2')
    elif kind == 3:                                  # ray gun on its stand
        m.rod(y, y + 0.6, 2.6, 'prop2')
        m.rod(y + 0.6, y + 5.0, 0.3, 'prop2')
        m.rod(-4.0, 5.0, 1.0, 'prop', cx=y + 6.4, cz=0.0, axis=0)
        for x in (-2.0, -0.6, 0.8):
            m.rod(x, x + 0.6, 1.5, 'prop2', cx=y + 6.4, cz=0.0, axis=0)
        m.rod(5.0, 6.6, 0.6, 'glow2', cx=y + 6.4, cz=0.0, axis=0)
        blob(m, (-2.0, y + 7.8, 0.0), (1.2, 1.2, 1.2), 'glow2')
        m.box([-3.6, y + 2.6, -0.6], [-2.2, y + 5.8, 0.6], 'prop2')
    elif kind == 4:                                  # monster skull
        m.box([-4.0, y, -3.0], [4.0, y + 1.0, 3.0], 'prop2')
        blob(m, (0.0, y + 4.8, -1.0), (4.0, 3.8, 3.6), 'prop')
        blob(m, (0.0, y + 3.4, 2.6), (2.6, 2.2, 3.0), 'prop')
        for s in (-1, 1):
            blob(m, (s * 1.8, y + 5.6, 1.8), (1.0, 1.0, 0.8), 'dark')
            m.seg((s * 2.6, y + 8.0), (s * 4.6, y + 11.6), 0.8, 0.8, 'prop', z=-1.0)
        for q in range(6):
            m.box([-1.8 + q * 0.7, y + 1.4, 5.2], [-1.5 + q * 0.7, y + 2.4, 5.5], 'teeth')
    else:                                            # snowman costume
        blob(m, (0.0, y + 4.0, 0.0), (4.2, 4.0, 4.0), 'prop')
        blob(m, (0.0, y + 10.4, 0.0), (3.2, 3.0, 3.0), 'prop')
        blob(m, (0.0, y + 15.2, 0.0), (2.4, 2.4, 2.4), 'prop')
        for s in (-1, 1):
            blob(m, (s * 0.8, y + 15.8, 2.2), 0.3, 'dark')
        m.seg((0.0, y + 15.0), (0.0, y + 15.0), 0.6, 0.6, 'dark', z=3.0)
        blob(m, (0.0, y + 15.0, 2.9), (0.3, 0.3, 1.0), 'prop2')
        m.rod(y + 12.4, y + 13.4, 3.0, 'scarf')
        m.box([2.0, y + 9.0, 2.6], [2.8, y + 12.4, 3.0], 'scarf')
        for yy in (y + 9.0, y + 10.6, y + 12.0):
            blob(m, (0.0, yy, 3.0), 0.3, 'dark')


# ================================================================== looks


def _looks():
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.4, chips=0.3))      # noqa: E731
    wood = lambda a, b: (lambda f: P.wood(f, a, b))                              # noqa: E731
    dark = A.plastic('#1e1f20')
    blk = A.plastic('#141414')
    out = {}
    out['band_poster'] = [dict(b=poster, print=band_poster(k), backing=A.cardboard, frame=blk, _px={'print': 10})
                          for k in range(len(BANDS))]
    out['movie_poster'] = [dict(b=poster, print=movie_poster(k), backing=A.cardboard, frame=blk, _px={'print': 10})
                           for k in range(len(FILMS))]
    out['wall_calendar'] = [dict(b=wall_calendar, print=calendar_page(k), pages=A.flat('#f0ece0'), metal=A.steel,
                                 frame=wood('#c9a77a', '#8a6440'), _px={'print': 12}) for k in range(12)]
    PEN = [('TIGERS', '#e86a1a', '#141414'), ('EAGLES', '#1e3a8a', '#f0f0f0'), ('BEARS', '#141414', '#f0c020'),
           ('HAWKS', '#c8202a', '#f0f0f0'), ('KNOX U', '#2a8a3a', '#f0f0f0'), ('STATE', '#6a1a2a', '#e8c860'),
           ('ROCKETS', '#f0c020', '#c8202a'), ('PIRATES', '#2a2a2a', '#e8e4da'), ('COUGARS', '#5a2a8a', '#f0f0f0'),
           ('MINERS', '#8a6a3a', '#141414'), ('RIVER CATS', '#2a8a8a', '#f0f0f0'), ('BULLDOGS', '#a8281f', '#f0e0a0')]
    out['pennant_set'] = []
    for k in range(12):
        d = dict(b=pennant_set, board=wood('#c9a77a', '#8a6440'), frame=wood('#8a5a34', '#5a3a1a'), metal=A.steel,
                 strip=A.fabric('#f0f0f0'), _px={})
        for q, (yc, x0) in enumerate(((10.0, -10.0), (6.8, -9.0), (3.6, -10.0))):
            word, fld, ink = PEN[(k + q * 4) % 12]
            d[f'p{q}'] = pennant_paint(word, fld, ink, x0, x0 + 19.0, yc)
            d['_px'][f'p{q}'] = 10
        out['pennant_set'].append(d)
    out['tin_sign'] = [dict(b=tin_sign, tin=tin_face(k), rim=paint(TIN[k][3]), frame=blk, _px={'tin': 10})
                       for k in range(len(TIN))]
    out['licence_plate_set'] = []
    for k in range(16):
        d = dict(b=licence_plate_set, board=wood(*(('#8a5a34', '#5a3a1a'), ('#c9a77a', '#8a6440'),
                                                   ('#5a3a2a', '#2a1a10'), ('#a89070', '#6a5a40'))[k % 4]),
                 gap=A.flat('#2a1a10'), frame=wood('#5a3a1a', '#2a1a0a'), metal=A.steel, _px={})
        for q in range(12):
            st, fld, ink = STATES[(q * 5 + k * 7) % len(STATES)]
            num = f'{(q * 37 + k * 13) % 900 + 100}-{chr(65 + (q + k) % 26)}{chr(65 + (q * 3 + k) % 26)}'
            d[f'pl{q}'] = plate(st, fld, ink, num)
            d['_px'][f'pl{q}'] = 10
        out['licence_plate_set'].append(d)
    BUTTER = [[('#2a6ae8', '#141414'), ('#f0a020', '#141414'), ('#f0e8d0', '#3a3a3a'), ('#c8202a', '#f0e040')],
              [('#e8dcc0', '#2a2a2a'), ('#8a6a4a', '#e8c860'), ('#f0f0f0', '#141414'), ('#5a4a3a', '#e8a040')],
              [('#40c0f0', '#1a2a5a'), ('#a040f0', '#f0e040'), ('#40e060', '#141414'), ('#f070b0', '#2a2a2a')],
              [('#f0c020', '#141414'), ('#e86a1a', '#f0f0f0'), ('#2a8a3a', '#f0e040'), ('#c8202a', '#141414')]]
    out['butterfly_display'] = [dict(b=butterfly_display, print=butterflies(bk, BUTTER[k]), frame=fr, pin=A.flat('#2a1a10'),
                                     metal=A.steel, _px={'print': 10})
                                for k, (bk, fr) in enumerate((('#f0ece0', wood('#6a3a1a', '#3a1a0a')),
                                                              ('#e8e4da', paint('#141414')),
                                                              ('#d8ccb0', wood('#c9a77a', '#8a6440')),
                                                              ('#f4f0e8', paint('#e8e4da'))))]
    out['spray_paint_kit'] = []
    for k in range(12):
        pal = SPRAY[k % 3]
        d = dict(b=spray_paint_kit, cardboard=A.cardboard, dark=dark, can=A.alu if k < 6 else paint('#e8e4da'),
                 mask=A.flat('#f0f0f0'), glove=A.fabric('#e8c040'))
        for q in range(6):
            d[f'c{q}'] = A.plastic(pal[(q + k) % 6])
        out['spray_paint_kit'].append(d)
    out['framed_gold_record'] = [dict(b=framed_gold_record, mat=A.flat(mt), frame=fr, disc=disc_face(dc, lb, wd),
                                      sleeve=A.flat(lb), plaque=P.brass, _px={'disc': 12})
                                 for mt, fr, dc, lb, wd in (('#141414', paint('#141414'), '#d8b040', '#c8202a', 'GOLD'),
                                                            ('#f0ece0', paint('#141414'), '#d0d4d8', '#2a4ae8', 'PLATINUM'),
                                                            ('#1e1f20', P.brass, '#d8b040', '#141414', 'GOLD'),
                                                            ('#2a2a3a', paint('#141414'), '#d8b040', '#f0c020', 'GOLD'),
                                                            ('#f0ece0', A.chrome, '#d0d4d8', '#c8202a', 'PLATINUM'),
                                                            ('#3a1a1a', wood('#5a1a10', '#2a0a04'), '#d8b040', '#f0ece0',
                                                             'GOLD'))]
    out['mounted_swordfish'] = [dict(b=mounted_swordfish, plaque=wood('#6a3a1a', '#3a1a0a'), frame=wood('#5a3a1a', '#2a1a0a'),
                                     metal=P.brass, fish=A.skin(fs), belly=A.skin('#e8ecee'), fin=A.skin(fn),
                                     eye=A.flat('#141414', 0.01))
                                for fs, fn in (('#2a4a8a', '#1a2a5a'), ('#5a6a7a', '#3a4a5a'), ('#1e3a6a', '#2a6ae8'))]
    out['antique_map'] = [dict(b=antique_map, print=parchment(kd, tn, ik, se), frame=fr, _px={'print': 10})
                          for kd, tn, ik, se, fr in (('world', '#e8d8b0', '#3a2a1a', '#a8c0b8', P.brass),
                                                     ('world', '#f0e0b8', '#5a2a1a', '#c8d0c0',
                                                      wood('#3a1a0a', '#1a0a04')),
                                                     ('county', '#e8dcc0', '#3a2a1a', '#7aa0c0', wood('#6a3a1a', '#3a1a0a')),
                                                     ('county', '#d8c8a0', '#2a1a10', '#6a8ab0', P.brass))]
    out['stuffed_owl'] = [dict(b=stuffed_owl, wood=wood('#6a3a1a', '#3a1a0a'), branch=wood('#6a5a48', '#3a2a1a'),
                               plumage=lambda f, a=a, b2=b2: P.mix(P.col(a), P.col(b2),
                                                                   np.clip(f.noise(6.0, 3, 3) * 1.5 - 0.3, 0, 1)),
                               breast=A.flat(br, 0.2), wing=A.flat(wg, 0.2), disc=A.flat(dc, 0.1),
                               eye=A.flat(ey, 0.02), pupil=A.flat('#0a0a0a', 0.01), talon=A.flat('#2a2a2a'),
                               beak=A.flat('#3a3a3a'))
                          for a, b2, br, wg, dc, ey in (('#8a6a48', '#3a2a1a', '#c8a880', '#6a4a2a', '#c89a6a', '#f0a020'),
                                                        ('#e8d0a0', '#a87a4a', '#f4f0e8', '#d8b888', '#f8f4ee', '#1a1a1a'))]
    out['signed_jersey_frame'] = [dict(b=signed_jersey_frame, shirt=jersey_face(k), sleeve=A.fabric(TEAMS[k][2]),
                                       trim=A.fabric(TEAMS[k][3]), backing=A.fabric(('#141414', '#1e3a6a', '#3a1a1a')[k % 3]),
                                       frame=wood('#5a3a1a', '#2a1a0a') if k % 2 else paint('#141414'),
                                       plate=P.brass, _px={'shirt': 10}) for k in range(len(TEAMS))]
    GLASS = [('#7a1a1e', '#c89030', '#3a2a6a', '#5a3a7a', '#2a5a3a', '#a86a1a'),
             ('#1e2e6a', '#8a1a1e', '#d8b050', '#6a1a2a', '#3a5a2a'),
             ('#a86a1a', '#2a4a2a', '#7a1a1e', '#3a2a6a', '#d8b050'),
             ('#3a2a6a', '#d8b050', '#7a1a1e', '#1e4a6a', '#a86a1a'),
             ('#2a5a3a', '#7a1a1e', '#d8b050', '#8a1a1e', '#1e2e6a'),
             ('#6a1a2a', '#1e2e6a', '#c89030', '#2a5a3a', '#5a3a7a')]
    out['stained_glass_panel'] = [dict(b=stained_glass_panel, glasspane=stained(pal, k % 2 == 0, k), lead=A.flat('#2a2a2a'),
                                       frame=wood('#8a5a34', '#5a3a1a'), _px={'glasspane': 10})
                                  for k, pal in enumerate(GLASS)]
    PROPS = [('#8a8a84', '#3a3a3a', '#f03a2a'), ('#f0f0f0', '#c8c8cc', '#40c0f0'), ('#5a6a3a', '#2a3a1a', '#80f040'),
             ('#c8a040', '#c8202a', '#40e0d0'), ('#e8dcc0', '#3a2a1a', '#f0a020'), ('#f8f8f8', '#2a2a2a', '#f0e0a0')]
    out['film_prop_display'] = [dict(b=film_prop_display, plinth=paint('#141414'), trim=P.brass,
                                     plaque=P.brass, glow=neon_f('#f8f4ee'), prop=A.flat(a, 0.12), prop2=A.flat(b2, 0.1),
                                     glow2=neon_f(c), visor=A.flat('#c8a040', 0.02), dark=A.flat('#141414'),
                                     teeth=A.flat('#f0ece0'), scarf=A.fabric('#c8202a'))
                                for a, b2, c in PROPS]
    for ls in out.values():
        for k, d in enumerate(ls):
            d['b'] = (lambda fn, k: (lambda m: fn(m, k)))(d['b'], k)
    return out


def neon_f(c):
    return A.flat(c, 0.03)


def looks(it, wrap):
    return {i: [wrap(it, d) for d in ls] for i, ls in _looks().items()}
