"""Wave 5 decor models, batch 1: lighting and neon (wave 4 stage 5; specs in decor.py). Built from the photos in
refs/items/decor/<id>/. Every piece has its looks (decor.py's looks column, up to 16 shown by the placed block):
looks(it) returns {id: [entry for look 0, look 1, ...]}; table(it) the look-0 entries for the loot items.

Parts that light up when the piece is working use the glow materials (GLOW); decor_models.py gives those faces
light_emission in the placed block's ON models. Same conventions as gen_loot.py: inches, standing on y = 0, front
toward +z.
"""
import math

import art as A
import paint as P
import shapes as sh
from wave4 import blob, flat

GLOW = ('glow', 'glow2', 'glow3', 'glow4')
CREAM = '#efe6d0'


def neon(c):
    """A lit tube colour: the colour, pushed bright, with a hot white core showing through."""
    return A.flat(c, 0.03)


# ================================================================== helpers


def tube(m, p0, p1, r, mat, z=0.0):
    """A neon tube run in the x-y plane from p0 to p1 (a bar r across, round-ish)."""
    m.seg(p0, p1, r * 2, r * 2, mat, z=z, plane='xy', over=r)


def neon_text(m, word, cx, y0, h, r, mat, z=0.0):
    """A word bent in neon tube: the 3x5 font's strokes as tube runs, letters h tall, centred on cx."""
    px = h / 5.0
    widths = [A.glyph(ch)[0] for ch in word]
    total = (sum(widths) + len(word) - 1) * px
    x = cx - total / 2
    for ch, w in zip(word, widths):
        _, rows = A.glyph(ch)
        on = {(c, r_) for r_ in range(5) for c in range(w) if rows[r_][c] == '1'}
        for (c, r_) in on:
            X, Y = x + (c + 0.5) * px, y0 + (4 - r_ + 0.5) * px
            if (c + 1, r_) in on:
                tube(m, (X, Y), (X + px, Y), r, mat, z)
            if (c, r_ + 1) in on:
                tube(m, (X, Y), (X, Y - px), r, mat, z)
            for dc in (1, -1):                       # diagonal steps where no square path joins them
                if (c + dc, r_ + 1) in on and (c + dc, r_) not in on and (c, r_ + 1) not in on:
                    tube(m, (X, Y), (X + dc * px, Y - px), r, mat, z)
            if not any(((c + dc, r_ + dr) in on) for dc, dr in ((1, 0), (-1, 0), (0, 1), (0, -1),
                                                                 (1, 1), (-1, 1), (1, -1), (-1, -1))):
                tube(m, (X - r, Y), (X + r, Y), r, mat, z)
        x += (w + 1) * px
    return total


def backboard(m, w, h, mat='board', d=0.6, legs=True):
    """The clear acrylic or black grid a neon sign is built on, with the transformer box and its feet."""
    m.box([-w / 2, 1.0, -d], [w / 2, 1.0 + h, 0.0], mat)
    if legs:
        for x in (-w / 2 + 1.0, w / 2 - 1.0):
            m.box([x - 0.5, 0.0, -2.4], [x + 0.5, 1.0, 1.0], 'frame')
    m.box([w / 2 - 4.0, 1.4, -d - 1.4], [w / 2 - 1.0, 3.4, -d], 'frame')      # transformer
    m.seg((w / 2 - 2.5, 2.0), (w / 2 + 1.0, 0.2), 0.3, 0.3, 'dark', z=-d - 0.7)   # cord


# ================================================================== the pieces


STRING = [('#f8e0a0',) * 4, ('#e8402a', '#2ac04a', '#2a6ae8', '#f0c020'), ('#4a8af0',) * 4,
          ('#e8402a', '#2ac04a', '#e8402a', '#2ac04a'), ('#f080b0',) * 4, ('#a060e0',) * 4, ('#f0a040',) * 4,
          ('#e8f0ff',) * 4]


def string_lights(m):
    """Fairy lights off the hook: the green wire wound in loose loops on its card spool, round bulbs every few
    inches, the plug on its lead."""
    m.box([-3.0, 0.0, -0.4], [3.0, 5.0, 0.4], 'cardboard')               # spool card
    m.box([-3.4, 0.0, -0.4], [-3.0, 5.6, 0.4], 'cardboard')
    m.box([3.0, 0.0, -0.4], [3.4, 5.6, 0.4], 'cardboard')
    for i, (rx, ry) in enumerate(((2.6, 2.0), (2.9, 2.3), (2.3, 1.8))):
        for k in range(16):
            a0, a1 = math.radians(k * 22.5), math.radians((k + 1) * 22.5)
            p0 = (rx * math.cos(a0), 2.5 + ry * math.sin(a0))
            p1 = (rx * math.cos(a1), 2.5 + ry * math.sin(a1))
            m.seg(p0, p1, 0.12, 0.12, 'wire', z=0.6 + i * 0.25, plane='xy')
            if k % 2 == 0:
                g = GLOW[(k // 2 + i) % 4]
                blob(m, (p0[0], p0[1], 0.6 + i * 0.25 + 0.2), (0.32, 0.42, 0.32), g)
    m.seg((3.0, 1.0), (6.0, 0.3), 0.15, 0.15, 'wire', z=0.0, plane='xy')     # lead
    m.box([6.0, 0.0, -0.4], [7.2, 0.8, 0.4], 'dark')                       # plug


LAVA = [('#f04a1a', '#f8d040', '#2a2a2a'), ('#e8e040', '#40a0f0', '#2a2a2a'), ('#a040e0', '#40e0d0', '#c8c8c8'),
        ('#40e060', '#f0f0a0', '#1e1f20'), ('#f070b0', '#ffffff', '#c8a040'), ('#2a60e0', '#60f0f0', '#c8c8c8'),
        ('#e02a2a', '#f8f0e0', '#1e1f20'), ('#f0a020', '#a02a8a', '#c8a040'), ('#60f0f0', '#2a40a0', '#1e1f20'),
        ('#f8f8f0', '#e8402a', '#c8c8c8'), ('#9ae040', '#e040a0', '#2a2a2a'), ('#e8c040', '#e8402a', '#8a5a2a')]


def lava_lamp(m):
    """A lava lamp: the cone base and cap in metal, the tapered glass bottle of coloured liquid between them
    with the wax blobs risen and sinking in it."""
    m.rod(0.0, 1.0, 2.6, 'foot')                                          # base foot
    for y, r in ((1.0, 2.4), (2.0, 2.1), (3.0, 1.8), (4.0, 1.6), (5.0, 1.5)):
        m.rod(y, y + 1.0, r, 'foot')
    for y, r in ((6.0, 1.6), (7.5, 1.9), (9.0, 2.05), (10.5, 1.95), (12.0, 1.7), (13.5, 1.4)):
        m.rod(y, y + 1.5, r, 'glow2')                                     # liquid in the bottle
    blob(m, (0.0, 6.8, 0.0), (1.4, 0.9, 1.4), 'glow')                      # wax pool at the foot
    blob(m, (0.5, 9.2, 0.3), (0.9, 1.3, 0.9), 'glow')
    blob(m, (-0.4, 12.6, -0.2), (0.7, 0.9, 0.7), 'glow')
    blob(m, (0.2, 14.3, 0.4), (0.45, 0.5, 0.45), 'glow')
    for y, r in ((15.0, 1.3), (15.8, 1.1), (16.6, 0.9)):
        m.rod(y, y + 0.8, r, 'foot')                                      # cap
    m.seg((2.0, 0.6), (5.0, 0.1), 0.25, 0.25, 'dark', z=0.0, plane='xy')   # cord


WORDS = [('OPEN', '#f03a2a'), ('BAR', '#2ab0f0'), ('DINER', '#f070b0'), ('EAT', '#f0a020'), ('MOTEL', '#e83a3a'),
         ('VACANCY', '#40e060'), ('PIZZA', '#f04a1a'), ('COCKTAILS', '#e060e0'), ('LIVE MUSIC', '#40a0f0'),
         ('BURGERS', '#f0c020'), ('COFFEE', '#f8f0e0'), ('LIQUOR', '#2ae0c0'), ('TATTOO', '#e02a6a'),
         ('KARAOKE', '#a060f0'), ('HOT DOGS', '#f06a1a'), ('DANCE', '#f040a0')]


def neon_word_sign(m, k=0):
    """A neon word on a clear backboard, a border tube round it: one word in its colour, the transformer and
    cord behind."""
    word, _ = WORDS[k]
    h = 3.6
    w = max(14.0, (len(word) * 4 - 1) * h / 5 + 4.0)
    backboard(m, w, 7.0)
    neon_text(m, word, 0.0, 2.6, h, 0.2, 'glow', z=0.3)
    for p0, p1 in (((-w / 2 + 0.6, 1.6), (w / 2 - 0.6, 1.6)), ((-w / 2 + 0.6, 7.4), (w / 2 - 0.6, 7.4)),
                   ((-w / 2 + 0.6, 1.6), (-w / 2 + 0.6, 7.4)), ((w / 2 - 0.6, 1.6), (w / 2 - 0.6, 7.4))):
        tube(m, p0, p1, 0.18, 'glow2', z=0.3)
    for x in (-w / 2 + 2.0, 0.0, w / 2 - 2.0):                             # tube standoffs
        m.box([x - 0.15, 4.6, -0.1], [x + 0.15, 4.9, 0.3], 'lens')


BEER = [('COLD BEER', '#f0c020', '#e83a3a'), ('ON TAP', '#40a0f0', '#f8f0e0'), ('LAGER', '#f0a020', '#2ab0f0'),
        ('ALE', '#e83a3a', '#f0c020'), ('STOUT', '#f8f0e0', '#e0a020'), ('ICE COLD', '#60e0f0', '#2a6ae8'),
        ('BEER', '#40e060', '#f03a2a'), ('PILSNER', '#f0e040', '#e83a3a'), ('HAPPY HOUR', '#f040a0', '#40e0d0'),
        ('DRAFT', '#f06a1a', '#f8f0e0'), ('BREW', '#a060f0', '#f0c020'), ('CHEERS', '#2ae0c0', '#f040a0')]


def neon_beer_sign(m, k=0):
    """A bar-window beer sign: a script word over a beer mug drawn in tube, on the black grid backing with its
    chain hooks."""
    word, _, _ = BEER[k]
    backboard(m, 18.0, 13.0, mat='frame')
    neon_text(m, word, 1.0 if len(word) < 7 else 0.0, 9.0, 3.2, 0.18, 'glow', z=0.3)
    # the mug: body, handle, foam head
    tube(m, (-6.5, 2.6), (-6.5, 7.4), 0.2, 'glow2', z=0.3)
    tube(m, (-3.0, 2.6), (-3.0, 7.4), 0.2, 'glow2', z=0.3)
    tube(m, (-6.5, 2.6), (-3.0, 2.6), 0.2, 'glow2', z=0.3)
    for a in range(0, 181, 30):
        x = -4.75 + 1.75 * math.cos(math.radians(a))
        tube(m, (x, 7.4), (x + 0.3, 7.9 + 0.4 * math.sin(math.radians(a))), 0.2, 'glow2', z=0.3)
    for a0 in range(-90, 90, 30):
        a1 = a0 + 30
        tube(m, (-3.0 + 1.3 * math.cos(math.radians(a0)), 5.0 + 1.3 * math.sin(math.radians(a0))),
             (-3.0 + 1.3 * math.cos(math.radians(a1)), 5.0 + 1.3 * math.sin(math.radians(a1))), 0.2, 'glow2', z=0.3)
    tube(m, (0.0, 2.2), (8.0, 2.2), 0.15, 'glow', z=0.3)                    # underline
    for x in (-7.0, 7.0):                                                  # chain hooks
        m.rod(14.0, 14.8, 0.2, 'metal', cx=x, cz=-0.3)


def disco_ball(m, k=0):
    """A mirror ball: hundreds of small square mirrors on the sphere, the hanging ring and its motor on top."""
    r, c = 5.0, 5.4
    blob(m, (0.0, c, 0.0), (r * 0.98, r * 0.98, r * 0.98), 'dark')
    for lat in range(-75, 90, 15):
        ring = math.cos(math.radians(lat))
        n = max(4, int(24 * ring))
        for i in range(n):
            lon = math.radians(i * 360 / n + (lat % 30) * 0.5)
            x, y, z = r * ring * math.cos(lon), c + r * math.sin(math.radians(lat)), r * ring * math.sin(lon)
            s = 1.15
            g = ('glow', 'glow2', 'glow3')[(i + lat // 15) % 3]
            m.box([x - s / 2, y - s / 2, z - s / 2], [x + s / 2, y + s / 2, z + s / 2], g,
                  rot=dict(axis='y', angle=-math.degrees(lon), origin=[x, y, z]))
    m.rod(c + r, c + r + 0.8, 0.5, 'metal')                                 # hanging boss
    sh.torus(m, (0.0, c + r + 1.4), 0.6, 0.15, 0.15, 'metal')
    m.box([-1.2, c + r + 2.0, -1.0], [1.2, c + r + 3.4, 1.0], 'frame')     # motor
    m.seg((1.2, c + r + 2.6), (4.0, c + r + 2.0), 0.25, 0.25, 'dark', z=0.0, plane='xy')


BLACK = ['tube', 'bulb']


def black_light(m, k=0):
    """A black light: the 18-inch tube in its slim white fixture (look 0) or the dark purple bulb in a clamp
    lamp (look 1)."""
    if BLACK[k] == 'tube':
        m.box([-9.5, 0.0, -1.0], [9.5, 1.4, 1.0], 'body')
        m.rod(-9.0, 9.0, 0.55, 'glow', cx=1.9, cz=0.3, axis=0)
        for x in (-9.2, 9.2):
            m.box([x - 0.4, 1.0, -0.6], [x + 0.4, 2.6, 0.6], 'body')
        m.box([-1.2, 0.0, 1.0], [1.2, 0.9, 1.3], 'dark')                    # switch
        m.seg((9.5, 0.6), (12.0, 0.2), 0.25, 0.25, 'dark', z=0.0, plane='xy')
    else:
        m.box([-2.5, 0.0, -2.5], [2.5, 0.6, 2.5], 'metal')                   # clamp foot
        m.seg((0.0, 0.6), (0.0, 5.0), 0.4, 0.4, 'metal', z=0.0, plane='xy')
        m.rod(5.0, 7.5, 2.4, 'alu')                                          # reflector
        m.rod(7.5, 7.9, 2.9, 'alu')
        blob(m, (0.0, 8.6, 0.0), (1.6, 1.4, 1.6), 'glow')                    # bulb


STROBE = ['#f8f8ff', '#f03a2a']


def strobe_light(m, k=0):
    """A party strobe: the black box with its finned top, the flash tube behind the ribbed clear (or red) lens
    in a chrome reflector, the speed knob, the hanging yoke."""
    m.box([-4.0, 0.6, -2.4], [4.0, 5.0, 2.4], 'body')
    for x in (-3.0, -1.5, 0.0, 1.5, 3.0):
        m.box([x - 0.15, 5.0, -2.2], [x + 0.15, 5.6, 2.2], 'body')          # fins
    m.box([-3.4, 1.2, 2.4], [3.4, 4.4, 2.6], 'chrome')                     # reflector rim
    m.box([-3.1, 1.5, 2.6], [3.1, 4.1, 2.9], 'glow')                       # lens
    for x in (-2.2, -1.1, 0.0, 1.1, 2.2):
        m.box([x - 0.06, 1.5, 2.9], [x + 0.06, 4.1, 2.95], 'lens')
    m.rod(4.0, 4.4, 0.35, 'dark', cx=3.0, cz=0.6, axis=0)                  # speed knob
    flat(m, (-4.6, 0.0), (4.6, 0.0), 0.6, 0.6, 'metal')                     # yoke foot
    for x in (-4.4, 4.0):
        m.box([x, 0.0, -0.3], [x + 0.4, 3.0, 0.3], 'metal')


PLASMA = [('#f060c0', '#a040f0'), ('#60a0f0', '#e8e8ff'), ('#a040f0', '#f040a0')]


def plasma_ball(m, k=0):
    """A plasma ball: the clear globe on its black base, the glowing core and the lightning filaments reaching
    out to the glass."""
    c, r = 5.6, 3.9
    m.rod(0.0, 0.8, 2.6, 'dark')
    m.rod(0.8, 1.8, 2.0, 'dark')
    m.box([-0.6, 0.3, 2.3], [0.6, 0.9, 2.65], 'frame')                       # switch
    sh.torus(m, (0.0, c), r, 0.12, 0.12, 'globe', steps=32)                 # the glass: its rim catching light
    for y in (c - r * 0.5, c + r * 0.5):
        rr = r * math.cos(math.asin(0.5))
        for i in range(24):
            a0, a1 = math.radians(i * 15), math.radians(i * 15 + 15)
            flat(m, (rr * math.cos(a0), rr * math.sin(a0)), (rr * math.cos(a1), rr * math.sin(a1)), 0.1, 0.1, 'globe',
                 y0=y)
    blob(m, (0.0, c, 0.0), (0.8, 0.8, 0.8), 'glow')
    for i in range(12):
        a = math.radians(i * 30)
        e = math.radians(((i * 47) % 120) - 50)
        end = (r * 0.95 * math.cos(e) * math.cos(a), c + r * 0.95 * math.sin(e), r * 0.95 * math.cos(e) * math.sin(a))
        mid = (end[0] * 0.5 + 0.4 * math.sin(i), c + (end[1] - c) * 0.5 + 0.3, end[2] * 0.5 + 0.4 * math.cos(i))
        for p0, p1 in (((0.0, c, 0.0), mid), (mid, end)):
            L = math.dist(p0, p1)
            mx, my, mz = [(a_ + b_) / 2 for a_, b_ in zip(p0, p1)]
            yaw = math.degrees(math.atan2(-(p1[2] - p0[2]), p1[0] - p0[0]))
            pitch = math.degrees(math.atan2(p1[1] - p0[1], math.hypot(p1[0] - p0[0], p1[2] - p0[2])))
            m.box([mx - L / 2, my - 0.06, mz - 0.06], [mx + L / 2, my + 0.06, mz + 0.06], 'glow2',
                  rot=dict(axis='y', angle=yaw, origin=[mx, my, mz]))
            del pitch
        blob(m, end, (0.22, 0.22, 0.22), 'glow2')


# shapes as tube strokes on a 10 x 10 grid: lists of polylines
SHAPES = [
    ('palm', '#40e060', [[(5, 0), (5, 3), (4.6, 6), (5, 8)], [(5, 8), (2, 9), (0.5, 7)], [(5, 8), (8, 9.5), (9.5, 7.5)],
                         [(5, 8), (3, 6.5)], [(5, 8), (7.5, 6.5)], [(5, 8), (5.5, 10)]]),
    ('flamingo', '#f070b0', [[(4, 0), (4, 4)], [(4, 4), (2, 4.5), (1.5, 6), (3, 6.5), (6, 6), (7, 5), (6, 4), (4, 4)],
                             [(6.5, 5.6), (7.5, 8), (7, 9.5), (8.5, 9.5), (9, 8.6)]]),
    ('star', '#f0e040', [[(5, 10), (6.2, 6.6), (9.8, 6.6), (6.9, 4.4), (8, 1), (5, 3), (2, 1), (3.1, 4.4), (0.2, 6.6),
                          (3.8, 6.6), (5, 10)]]),
    ('heart', '#f03a5a', [[(5, 1), (1, 5), (0.6, 7.4), (2, 9), (3.8, 9), (5, 7.4), (6.2, 9), (8, 9), (9.4, 7.4), (9, 5),
                           (5, 1)]]),
    ('bolt', '#f0e040', [[(6, 10), (2.5, 5), (5, 5), (3.5, 0), (7.5, 6), (5, 6), (6, 10)]]),
    ('moon', '#e8f0ff', [[(6, 10), (3, 9), (1.4, 6), (1.6, 3.6), (3.4, 1.2), (6.4, 0.4), (8.6, 1.6), (6.4, 2.2), (4.6, 4),
                          (4.4, 6.6), (5.6, 8.8), (6, 10)]]),
    ('cactus', '#40e060', [[(4, 0), (4, 9), (6, 9), (6, 0)], [(4, 5), (2, 5), (2, 7.5)], [(6, 4), (8, 4), (8, 6.5)]]),
    ('cocktail', '#40e0d0', [[(1, 9), (9, 9), (5, 4.5), (1, 9)], [(5, 4.5), (5, 1)], [(3, 1), (7, 1)],
                             [(7, 9), (8.6, 10)]]),
    ('guitar', '#f0a020', [[(5, 0.6), (2.6, 2), (3, 4), (2.8, 5.4), (4.2, 6), (5.8, 6), (7.2, 5.4), (7, 4), (7.4, 2),
                            (5, 0.6)], [(5, 6), (5, 9.4)], [(4.4, 9.4), (5.6, 9.4)]]),
    ('cherry', '#f03a2a', [[(2.4, 3), (1.4, 2), (2.4, 0.8), (3.6, 1.6), (3.4, 3), (2.4, 3)],
                           [(6.4, 3.4), (5.6, 2.4), (6.6, 1.2), (7.8, 2), (7.6, 3.4), (6.4, 3.4)],
                           [(2.8, 3), (5, 9)], [(7, 3.4), (5, 9)], [(5, 9), (7.2, 9.6)]]),
    ('crown', '#f0c020', [[(1, 1), (9, 1), (9.4, 8), (7, 4.6), (5, 9), (3, 4.6), (0.6, 8), (1, 1)]]),
    ('skull', '#e8f0ff', [[(2, 4), (1.6, 7), (3, 9.4), (7, 9.4), (8.4, 7), (8, 4), (6.6, 3), (6.6, 1), (3.4, 1),
                           (3.4, 3), (2, 4)], [(3, 6), (4.4, 6.6)], [(5.6, 6.6), (7, 6)], [(4.4, 4.2), (5.6, 4.2)]]),
    ('rose', '#f03a5a', [[(5, 9), (3, 8), (3.4, 6), (5, 5.4), (6.6, 6), (7, 8), (5, 9)], [(4.4, 7.6), (5.6, 7)],
                         [(5, 5.4), (5, 0.4)], [(5, 2.6), (3, 3.6)], [(5, 3.6), (7, 4.4)]]),
    ('wave', '#2ab0f0', [[(0.4, 3), (2, 6), (4, 8), (6.4, 8.6), (8, 7.6), (6.6, 6.4), (5, 6.8), (5.6, 5), (8, 4),
                          (9.6, 3)], [(0.4, 1.6), (9.6, 1.6)]]),
    ('arrow', '#f06a1a', [[(0.4, 5), (8, 5)], [(5.6, 8), (9.4, 5), (5.6, 2)]]),
    ('peace', '#a060f0', [[(5, 0.6), (8, 2), (9.4, 5), (8, 8), (5, 9.4), (2, 8), (0.6, 5), (2, 2), (5, 0.6)],
                          [(5, 0.6), (5, 9.4)], [(5, 4.4), (2, 2)], [(5, 4.4), (8, 2)]]),
]


def neon_wall_shape(m, k=0):
    """A neon shape for a wall (palm, flamingo, star...): the bent tube on its clear acrylic backer cut to the
    shape's outline, the standoffs, the little transformer."""
    _, _, lines = SHAPES[k]
    s = 1.4
    backboard(m, 15.0, 15.0, legs=True)
    for line in lines:
        for (x0, y0), (x1, y1) in zip(line, line[1:]):
            tube(m, ((x0 - 5) * s, 1.5 + y0 * s), ((x1 - 5) * s, 1.5 + y1 * s), 0.22, 'glow', z=0.3)
    for x, y in ((-5.5, 3.0), (5.5, 3.0), (0.0, 14.5)):
        m.box([x - 0.15, y - 0.15, -0.1], [x + 0.15, y + 0.15, 0.3], 'lens')


BEACON = ['#f0a020', '#f03a2a', '#2a6ae8', '#40e060']


def rotating_beacon(m, k=0):
    """A rotating warning beacon: the black base with its magnet foot and cord, the coloured dome, and inside it
    the bulb and the mirror that turns round it."""
    m.rod(0.0, 0.5, 3.2, 'dark')                                            # magnet foot
    m.rod(0.5, 2.0, 2.9, 'body')                                            # base
    m.rod(2.0, 2.3, 3.0, 'metal')
    m.rod(2.3, 7.0, 2.6, 'glow')                                            # dome
    blob(m, (0.0, 7.0, 0.0), (2.6, 1.4, 2.6), 'glow')
    m.rod(2.3, 6.4, 0.5, 'glow2')                                            # bulb
    m.box([0.6, 2.6, -1.2], [1.0, 6.2, 1.2], 'chrome', rot=dict(axis='y', angle=30, origin=[0.8, 4.4, 0.0]))
    m.seg((2.9, 1.0), (6.0, 0.3), 0.25, 0.25, 'wire', z=0.0, plane='xy')
    m.box([6.0, 0.0, -0.5], [7.4, 0.8, 0.5], 'dark')                        # lighter plug


TANK = [('#c8a060', '#3a8a3a', '#f06a1a'), ('#2a2a2a', '#4ac06a', '#f0e040'), ('#e8e0d0', '#2a6a3a', '#2a6ae8'),
        ('#8a6a4a', '#6aa04a', '#f03a2a'), ('#3a4a8a', '#3a9a6a', '#f0f0f0')]


def aquarium(m, k=0):
    """A home fish tank: the glass box with its black trim, the lit hood with its feeding flap, gravel and a
    castle on the floor, water plants, the fish, the filter hanging off the back, the bubbler."""
    w, d, h = 15.0, 6.0, 10.0
    m.box([-w / 2, 0.0, -d / 2], [w / 2, 0.8, d / 2], 'frame')              # base trim
    m.box([-w / 2 + 0.3, 0.8, -d / 2 + 0.3], [w / 2 - 0.3, 2.0, d / 2 - 0.3], 'gravel')
    m.box([-w / 2 + 0.3, 2.0, -d / 2 + 0.3], [w / 2 - 0.3, h - 0.6, d / 2 - 0.3], 'water')
    for x in (-w / 2, w / 2 - 0.4):                                         # corner trim
        for z in (-d / 2, d / 2 - 0.4):
            m.box([x, 0.8, z], [x + 0.4, h, z + 0.4], 'frame')
    m.box([-w / 2 - 0.2, h, -d / 2 - 0.2], [w / 2 + 0.2, h + 1.2, d / 2 + 0.2], 'frame')    # hood
    m.box([-w / 2 + 0.6, h - 0.1, -d / 2 + 0.6], [w / 2 - 0.6, h, d / 2 - 0.6], 'glow')     # hood light
    m.box([-2.0, h + 1.2, -1.0], [2.0, h + 1.35, 1.6], 'dark')               # feeding flap
    m.box([2.5, 2.0, -1.6], [5.0, 5.2, 0.6], 'stone')                         # castle
    for x in (2.6, 3.6, 4.4):
        m.box([x, 5.2, -1.6], [x + 0.5, 5.8, 0.6], 'stone')
    m.box([3.3, 2.0, 0.6], [4.2, 3.4, 0.62], 'dark')
    for x, z, hh in ((-6.0, -1.6, 6.0), (-4.6, 1.0, 4.4), (-5.4, 0.0, 7.0), (6.0, -1.8, 5.0)):  # plants
        for i in range(3):
            a = math.radians(i * 120 + x * 20)
            m.seg((x, 2.0), (x + 0.8 * math.cos(a), 2.0 + hh), 0.35, 0.12, 'leaf', z=z + 0.4 * math.sin(a), plane='xy')
    for x, y, z, f in ((-1.5, 6.0, 0.5, 'glow2'), (1.0, 4.0, -0.8, 'glow2'), (-3.0, 4.2, -1.2, 'fish2'),
                       (0.0, 7.4, 1.4, 'fish2'), (3.6, 7.0, 0.0, 'glow2')):            # fish
        blob(m, (x, y, z), (0.7, 0.35, 0.2), f)
        m.box([x - 1.1, y - 0.3, z - 0.05], [x - 0.6, y + 0.3, z + 0.05], f)
    m.box([w / 2 - 3.6, 4.0, -d / 2 - 1.6], [w / 2 - 0.6, h + 0.4, -d / 2], 'frame')  # filter on the back
    for y in (2.4, 4.0, 5.6, 7.2):                                           # bubbles
        blob(m, (-1.0 + 0.2 * math.sin(y), y, -1.8), (0.15, 0.15, 0.15), 'lens')


ARROW = [('RESTAURANT', '#f03a2a', '#f0c020'), ('MOTEL', '#40e060', '#f03a2a'), ('CAFE', '#f070b0', '#2ab0f0'),
         ('BAR', '#2ab0f0', '#f0a020')]


def neon_arrow_sign(m, k=0):
    """A roadside arrow sign: the painted steel can with the word in neon down its face, chaser bulbs round the
    edge, the big neon arrow pointing in, on its post and footing."""
    word, _, _ = ARROW[k]
    m.box([-1.0, 0.0, -1.0], [1.0, 1.2, 1.0], 'stone')                       # footing
    m.rod(1.2, 10.0, 0.5, 'metal')                                            # post
    m.box([-9.0, 10.0, -1.4], [9.0, 16.0, 1.4], 'body')                       # sign can
    m.box([-9.2, 9.8, -1.5], [9.2, 10.2, 1.5], 'trim')
    m.box([-9.2, 15.8, -1.5], [9.2, 16.2, 1.5], 'trim')
    h = min(3.0, 16.0 * 5 / max(1, sum(A.glyph(ch)[0] + 1 for ch in word) - 1))
    neon_text(m, word, 0.0, 13.0 - h / 2, h, 0.16, 'glow', z=1.6)
    for i in range(19):                                                       # chaser bulbs
        x = -9.0 + i
        blob(m, (x, 16.45, 1.0), (0.25, 0.25, 0.25), 'glow3')
        blob(m, (x, 9.55, 1.0), (0.25, 0.25, 0.25), 'glow3')
    # the arrow under the can
    tube(m, (8.0, 8.8), (-4.0, 8.8), 0.25, 'glow2', z=0.4)
    tube(m, (-4.0, 8.8), (-1.6, 10.0), 0.25, 'glow2', z=0.4)
    tube(m, (-4.0, 8.8), (-1.6, 7.6), 0.25, 'glow2', z=0.4)
    m.box([-4.6, 7.2, -0.2], [8.6, 10.4, 0.2], 'body')                         # arrow backer
    m.box([-1.0, 7.0, -0.4], [1.0, 10.0, 0.4], 'metal')


FLOOR = [('#f03a2a', '#2ab0f0', '#f0e040', '#40e060'), ('#f070b0', '#a060f0', '#2ae0c0', '#f0a020'),
         ('#2a6ae8', '#40e0d0', '#e8f0ff', '#a060f0'), ('#f06a1a', '#f0c020', '#f03a2a', '#e8f0ff')]


def light_up_dance_floor(m, k=0):
    """A light-up dance floor section: a 4 x 4 grid of frosted glass squares lit in colour, in the black
    aluminium frame with its ramp edges."""
    n, s = 4, 4.0
    half = n * s / 2
    m.box([-half - 0.6, 0.0, -half - 0.6], [half + 0.6, 1.4, half + 0.6], 'frame')
    for i in range(n):
        for j in range(n):
            g = GLOW[(i * 3 + j * 5 + (i * j)) % 4]
            x, z = -half + i * s, -half + j * s
            m.box([x + 0.15, 1.4, z + 0.15], [x + s - 0.15, 1.6, z + s - 0.15], g)
    m.box([-half - 1.4, 0.0, -half - 0.6], [-half - 0.6, 0.8, half + 0.6], 'trim')
    m.box([half + 0.6, 0.0, -half - 0.6], [half + 1.4, 0.8, half + 0.6], 'trim')
    m.box([half - 3.0, 0.2, half + 0.6], [half - 1.0, 1.0, half + 1.0], 'dark')  # controller socket


GLOBE = [('GAS', '#e83a3a'), ('ETHYL', '#2a6ae8'), ('REGULAR', '#40a040'), ('SUPER', '#f0a020'), ('HI-TEST', '#e83a3a'),
         ('DIESEL', '#2a2a2a'), ('PREMIUM', '#a040a0'), ('MOTOR', '#2a8a8a'), ('FUEL', '#f06a1a'), ('ROYAL', '#1e3a8a')]


def gas_pump_globe(m, k=0):
    """An old gas pump globe: the milk-glass lens body in its coloured band, the printed word on the face, the
    metal neck collar it sat on, lit from inside."""
    m.rod(0.0, 1.2, 2.0, 'metal')                                              # neck collar
    m.rod(1.2, 2.2, 2.6, 'trim')
    blob(m, (0.0, 8.0, 0.0), (6.0, 6.0, 2.8), 'glow')                          # milk-glass body
    for z in (-2.0, 2.0):
        sh.torus(m, (0.0, 8.0), 5.0, 1.2, 0.5, 'trim', z=z, steps=24)          # coloured lens rings
    m.rod(2.72, 2.86, 3.6, 'label', cx=0.0, cz=8.0, axis=2)                     # printed faces
    m.rod(-2.86, -2.72, 3.6, 'label', cx=0.0, cz=8.0, axis=2)


def searchlight(m, k=0):
    """An old carbon-arc searchlight: the big drum with its cooling fins and lens door, the rear housing, the
    trunnion yoke on its turntable, the aiming handles."""
    c = 12.0
    m.rod(0.0, 1.5, 6.0, 'body')                                                # turntable
    m.rod(1.5, 3.0, 4.0, 'body')
    for x in (-8.6, 8.6):                                                       # yoke arms
        m.box([x - 0.8, 3.0, -1.2], [x + 0.8, c, 1.2], 'body')
    m.box([-9.4, 3.0, -1.2], [9.4, 4.4, 1.2], 'body')
    m.rod(-7.6, 7.6, 7.4, 'body', cx=0.0, cz=c, axis=2)                       # drum
    for z in (-6.0, -3.0, 0.0, 3.0):                                            # fins
        m.rod(z - 0.2, z + 0.2, 8.0, 'body', cx=0.0, cz=c, axis=2)
    m.rod(7.6, 8.4, 7.8, 'metal', cx=0.0, cz=c, axis=2)                         # lens rim
    m.rod(8.4, 8.8, 7.0, 'glow', cx=0.0, cz=c, axis=2)                          # lens
    for x in (-4.0, -2.0, 0.0, 2.0, 4.0):                                       # door slats
        m.box([x - 0.12, c - 7.0, 8.8], [x + 0.12, c + 7.0, 9.0], 'metal')
    m.rod(-10.0, -7.6, 4.0, 'body', cx=0.0, cz=c, axis=2)                      # rear housing
    m.rod(-10.6, -10.0, 2.6, 'metal', cx=0.0, cz=c, axis=2)
    for x in (-8.6, 8.6):
        m.rod(x - 1.0 if x < 0 else x, x if x < 0 else x + 1.0, 1.2, 'metal', cx=c, cz=0.0, axis=0)   # trunnions
    for x in (-5.0, 5.0):                                                       # aiming handles
        m.seg((x, c - 7.6), (x, c - 10.0), 0.4, 0.4, 'grip', z=6.0, plane='xy')


FIBRE = [('#f070b0', '#60c0f0', '#f0f0a0'), ('#40e060', '#2a6ae8', '#f0f0f0'), ('#a060f0', '#f040a0', '#60f0f0'),
         ('#f03a2a', '#f0c020', '#f8f0e0'), ('#2ae0c0', '#a060f0', '#f070b0'), ('#e8f0ff', '#60a0f0', '#a060f0')]


def fibre_optic_lamp(m):
    """A fibre-optic lamp: the clear cone base with the coloured wheel turning under it, and the spray of fine
    fibres fanning up and out from its top, each tip a point of light."""
    m.rod(0.0, 0.6, 2.2, 'dark')
    for y, r in ((0.6, 1.9), (1.4, 1.6), (2.2, 1.3), (3.0, 1.0)):
        m.rod(y, y + 0.8, r, 'glow2')                                       # lit cone
    m.rod(3.8, 4.4, 0.6, 'dark')                                            # collar
    for i in range(40):
        a = math.radians(i * 137.5)
        reach = 3.0 + 2.5 * ((i * 7) % 10) / 10
        lift = 6.0 + 4.0 * ((i * 3) % 10) / 10
        tip = (reach * math.cos(a), 4.4 + lift - reach * 0.35, reach * math.sin(a))
        mid = (tip[0] * 0.35, 4.4 + lift * 0.75, tip[2] * 0.35)
        for p0, p1 in (((0.0, 4.4, 0.0), mid), (mid, tip)):
            L = math.dist(p0, p1)
            mx, my, mz = [(a_ + b_) / 2 for a_, b_ in zip(p0, p1)]
            yaw = math.degrees(math.atan2(-(p1[2] - p0[2]), p1[0] - p0[0]))
            pitch = math.degrees(math.atan2(p1[1] - p0[1], math.hypot(p1[0] - p0[0], p1[2] - p0[2])))
            m.box([mx - L / 2, my - 0.03, mz - 0.03], [mx + L / 2, my + 0.03, mz + 0.03], 'lens',
                  rot=dict(axis='y', angle=yaw, origin=[mx, my, mz]))
            del pitch
        blob(m, tip, (0.12, 0.12, 0.12), GLOW[i % 3])


TUBES = ['#f03a2a', '#2ab0f0', '#40e060', '#f0c020', '#f070b0', '#a060f0', '#f8f0e0', '#f06a1a']


def neon_tube_kit(m):
    """A box of straight neon tubes from a sign shop: the long glass tubes with their electrode caps lying in
    the open cardboard carton, the coil of GTO cable and the little transformer."""
    m.box([-11.0, 0.0, -3.4], [11.0, 2.4, 3.4], 'cardboard')
    m.box([-11.0, 2.4, -3.4], [11.0, 2.6, -3.0], 'cardboard')
    for j, z in enumerate((-2.4, -1.2, 0.0, 1.2, 2.4)):
        m.rod(-10.0, 10.0, 0.42, 'glow', cx=2.0 + (j % 2) * 0.3, cz=z, axis=0)
        for x in (-10.6, 10.0):
            m.rod(x, x + 0.6, 0.48, 'dark', cx=2.0 + (j % 2) * 0.3, cz=z, axis=0)
    for i in range(10):                                                      # cable coil
        a0, a1 = math.radians(i * 36), math.radians(i * 36 + 36)
        flat(m, (12.8 + 1.4 * math.cos(a0), 1.6 * math.sin(a0)), (12.8 + 1.4 * math.cos(a1), 1.6 * math.sin(a1)),
             0.2, 0.2, 'wire')
    m.box([11.4, 0.0, -3.2], [14.0, 1.8, -1.6], 'frame')                      # transformer
    m.box([-3.0, 1.0, 3.4], [3.0, 2.0, 3.45], 'label')


MARQUEE = 'ABCDEHLMNOPRSTUW'


def marquee_bulb_letters(m, k=0):
    """A marquee letter: the letter's shape as a shallow painted metal can with a row of round bulbs down its
    face, stood up on its two little feet."""
    ch = MARQUEE[k]
    w, rows = A.glyph(ch)
    px = 3.0
    on = {(c, r_) for r_ in range(5) for c in range(w) if rows[r_][c] == '1'}
    x0 = -w * px / 2
    for (c, r_) in on:
        x, y = x0 + c * px, 1.0 + (4 - r_) * px
        m.box([x, y, -1.2], [x + px, y + px, 0.0], 'body')                    # the can
        m.box([x, y, 0.0], [x + px, y + px, 0.15], 'face')
        blob(m, (x + px / 2, y + px / 2, 0.55), (0.55, 0.55, 0.45), 'glow')    # bulb
        m.rod(0.15, 0.3, 0.65, 'metal', cx=x + px / 2, cz=y + px / 2, axis=2)  # socket ring
    for x in (x0 + 0.6, -x0 - 0.6):
        m.box([x - 0.4, 0.0, -2.0], [x + 0.4, 1.0, 0.8], 'dark')               # feet


def edison_chandelier(m, k=0):
    """An industrial chandelier of bare filament bulbs: the round ceiling plate (lying here as its stand), the
    cloth cords dropping to brass sockets at different lengths, the long squirrel-cage bulbs glowing amber."""
    m.rod(0.0, 0.8, 5.5, 'metal')                                              # the plate as a base
    m.rod(0.8, 1.6, 1.0, 'metal')
    m.rod(1.6, 18.0, 0.35, 'metal')                                            # the stem it hangs from
    m.rod(18.0, 18.8, 4.5, 'metal')                                            # canopy
    n = (5, 7, 9)[k]
    for i in range(n):
        a = math.radians(i * 360 / n)
        x, z = 3.4 * math.cos(a), 3.4 * math.sin(a)
        drop = 4.0 + 3.0 * ((i * 5) % 7) / 6
        m.rod(18.0 - drop, 18.0, 0.12, 'cord', cx=x, cz=z)                      # cord
        m.rod(18.0 - drop - 1.0, 18.0 - drop, 0.4, 'brass', cx=x, cz=z)         # socket
        blob(m, (x, 18.0 - drop - 2.4, z), (0.65, 1.4, 0.65), 'glass')          # bulb
        m.rod(18.0 - drop - 3.4, 18.0 - drop - 1.2, 0.08, 'glow', cx=x, cz=z)    # filament


TIFFANY = [('#3a8a3a', '#e8c040', '#c83a3a'), ('#2a6ae8', '#60c0f0', '#e8e0a0'), ('#c8602a', '#f0c020', '#3a7a3a'),
           ('#8a2a6a', '#e070b0', '#e8c040'), ('#2a7a6a', '#f0a020', '#e8e4c8'), ('#a8281f', '#f0c040', '#2a4a8a')]


def tiffany_lamp(m):
    """A Tiffany-style table lamp: the bronze base and stem, the cone shade of leaded glass in panels with a
    flower border, the bronze cap and finial, two pull chains."""
    m.rod(0.0, 0.6, 3.6, 'bronze')                                              # base
    blob(m, (0.0, 2.2, 0.0), (2.6, 2.0, 2.6), 'bronze')
    m.rod(3.0, 10.0, 0.45, 'bronze')
    for y, r in ((10.0, 7.6), (11.0, 6.8), (12.0, 5.8), (13.0, 4.6), (14.0, 3.3), (15.0, 2.0)):
        n = int(r * 3)
        for i in range(n):                                                      # leaded panels
            a = math.radians(i * 360 / n)
            g = ('glow', 'glow2', 'glow3')[(i + int(y)) % 3] if y < 12.0 else 'glow2'
            x, z = r * math.cos(a), r * math.sin(a)
            m.box([x - r * 1.05 * math.pi / n, y, z - 0.25], [x + r * 1.05 * math.pi / n, y + 1.0, z + 0.25], g,
                  rot=dict(axis='y', angle=-math.degrees(a) - 90, origin=[x, y + 0.5, z]))
    for y, r in ((11.0, 6.8), (13.0, 4.6)):                                     # lead lines
        for i in range(24):
            a0, a1 = math.radians(i * 15), math.radians(i * 15 + 15)
            flat(m, (r * math.cos(a0), r * math.sin(a0)), (r * math.cos(a1), r * math.sin(a1)), 0.15, 0.08, 'bronze',
                 y0=y)
    m.rod(16.0, 16.6, 1.6, 'bronze')                                            # cap
    m.rod(16.6, 17.6, 0.3, 'bronze')                                            # finial
    for x in (-0.6, 0.6):
        m.rod(7.5, 10.0, 0.06, 'brass', cx=x, cz=0.8)                           # pull chains


# ================================================================== tables


def _looks():
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.4, chips=0.3))      # noqa: E731
    acrylic = A.flat('#d8e4e8', 0.02)
    out = {}
    out['string_lights'] = [dict(b=string_lights, glow=neon(c[0]), glow2=neon(c[1]), glow3=neon(c[2]), glow4=neon(c[3]),
                                 wire=A.plastic('#2a4a2a'), cardboard=A.cardboard, dark=A.plastic('#1e1f20'))
                            for c in STRING]
    out['lava_lamp'] = [dict(b=lava_lamp, glow=neon(w), glow2=A.glass(liq, liq, 1.0), foot=paint(base),
                             dark=A.plastic('#1e1f20')) for w, liq, base in LAVA]
    out['neon_word_sign'] = [dict(b=lambda m, k=k: neon_word_sign(m, k), glow=neon(c), glow2=neon(WORDS[(k + 5) % 16][1]),
                                  board=acrylic, frame=A.plastic('#1e1f20'), lens=A.flat('#e8f0f0'))
                             for k, (_, c) in enumerate(WORDS)]
    out['neon_beer_sign'] = [dict(b=lambda m, k=k: neon_beer_sign(m, k), glow=neon(a), glow2=neon(b2),
                                  frame=A.plastic('#141414'), metal=A.steel) for k, (_, a, b2) in enumerate(BEER)]
    out['disco_ball'] = [dict(b=disco_ball, glow=c1, glow2=c2, glow3=c3, dark=A.flat('#3a3a3a'), metal=A.chrome,
                              frame=A.plastic('#1e1f20'))
                         for c1, c2, c3 in ((A.chrome, A.flat('#d8dce0', 0.02), A.flat('#9aa0a8', 0.02)),
                                            (wave3_gold(), A.flat('#f0d890', 0.02), A.flat('#b8963a', 0.02)),
                                            (A.flat('#f0c0c8', 0.02), A.chrome, A.flat('#c89098', 0.02)))]
    out['black_light'] = [dict(b=lambda m, k=k: black_light(m, k), glow=neon('#7a3af0'), body=A.plastic('#e8e4da'),
                               alu=A.alu, metal=A.steel, dark=A.plastic('#1e1f20')) for k in range(2)]
    out['strobe_light'] = [dict(b=lambda m, k=k: strobe_light(m, k), glow=neon(c), body=A.plastic('#1e1f20'),
                                chrome=A.chrome, lens=A.flat('#f0f0f0'), metal=A.steel, dark=A.plastic('#2a2a2a'))
                           for k, c in enumerate(STROBE)]
    out['plasma_ball'] = [dict(b=plasma_ball, glow=neon(c1), glow2=neon(c2), globe=A.glass('#c8d0e8'),
                               dark=A.plastic('#1a1a1a'), frame=A.plastic('#3a3a3a')) for c1, c2 in PLASMA]
    out['neon_wall_shape'] = [dict(b=lambda m, k=k: neon_wall_shape(m, k), glow=neon(c), board=acrylic,
                                   frame=A.plastic('#1e1f20'), lens=A.flat('#e8f0f0'), dark=A.plastic('#1e1f20'))
                              for k, (_, c, _) in enumerate(SHAPES)]
    out['rotating_beacon'] = [dict(b=rotating_beacon, glow=A.glass(c, c, 1.0), glow2=neon('#fff8e0'),
                                   body=A.plastic('#1e1f20'), metal=A.chrome, chrome=A.chrome,
                                   dark=A.plastic('#2a2a2a'), wire=A.plastic('#1e1f20')) for c in BEACON]
    out['aquarium'] = [dict(b=aquarium, frame=A.plastic('#1e1f20'), gravel=A.flat(g, 0.25), water=A.glass('#9ac8d8'),
                            glow=neon('#e8f4ff'), stone=A.flat('#8a8a84', 0.12), leaf=A.skin(p), glow2=neon(f),
                            fish2=A.flat('#f0f0f0'), dark=A.plastic('#1a1a1a'), lens=A.flat('#e8f0f0'))
                       for g, p, f in TANK]
    out['neon_arrow_sign'] = [dict(b=lambda m, k=k: neon_arrow_sign(m, k), glow=neon(c), glow2=neon(c2),
                                   glow3=neon('#fff0c0'), body=paint('#2a3a5a' if k % 2 else '#5a1a1a'),
                                   trim=A.chrome, metal=A.steel, stone=A.flat('#8a8a84', 0.12))
                              for k, (_, c, c2) in enumerate(ARROW)]
    out['light_up_dance_floor'] = [dict(b=light_up_dance_floor, glow=neon(a), glow2=neon(b2), glow3=neon(c), glow4=neon(d),
                                        frame=A.plastic('#1e1f20'), trim=A.alu, dark=A.plastic('#141414'))
                                   for a, b2, c, d in FLOOR]
    out['gas_pump_globe'] = [dict(b=lambda m, k=k: gas_pump_globe(m, k), word=w, base=c, glow=neon('#f4f0e4'),
                                  trim=paint(c), metal=A.steel) for k, (w, c) in enumerate(GLOBE)]
    out['searchlight'] = [dict(b=searchlight, body=paint('#4a5236'), metal=A.steel, glow=neon('#f8f4e0'),
                               grip=A.plastic('#1e1f20'))]
    out['fibre_optic_lamp'] = [dict(b=fibre_optic_lamp, glow=neon(a), glow2=neon(b2), glow3=neon(c),
                                    dark=A.plastic('#1e1f20'), lens=A.flat('#d8e4f0', 0.02)) for a, b2, c in FIBRE]
    out['neon_tube_kit'] = [dict(b=neon_tube_kit, word='NEON', base='#c8a070', glow=neon(c), dark=A.plastic('#1e1f20'),
                                 cardboard=A.cardboard, wire=A.plastic('#e8e4da'), frame=A.plastic('#2a2a2a'))
                            for c in TUBES]
    out['marquee_bulb_letters'] = [dict(b=lambda m, k=k: marquee_bulb_letters(m, k),
                                        body=paint(('#a8281f', '#1e1f20', '#e8e4da', '#2a4a8a')[k % 4]),
                                        face=paint(('#c8402a', '#2a2a2a', '#f0ece0', '#3a5aa0')[k % 4]),
                                        glow=neon('#fff0c0'), metal=A.chrome, dark=A.plastic('#1e1f20'))
                                   for k in range(len(MARQUEE))]
    out['edison_chandelier'] = [dict(b=lambda m, k=k: edison_chandelier(m, k), metal=paint('#2a2a2a'),
                                     cord=A.fabric('#2a2422'), brass=P.brass, glass=A.glass('#e8c890'),
                                     glow=neon('#f8b040')) for k in range(3)]
    out['tiffany_lamp'] = [dict(b=tiffany_lamp, glow=A.glass(a, a, 1.0), glow2=A.glass(b2, b2, 1.0),
                                glow3=A.glass(c, c, 1.0), bronze=lambda f: P.paint(f, '#5a4a2a', steel_base=0.5, chips=0.4),
                                brass=P.brass) for a, b2, c in TIFFANY]
    return out


def wave3_gold():
    import wave3
    return wave3.GOLD


def looks(it):
    """{id: [it() entry per look]}."""
    out = {}
    for i, ls in _looks().items():
        es = []
        for d in ls:
            d = dict(d)
            b = d.pop('b')
            word, base = d.pop('word', ''), d.pop('base', None)
            body = d.pop('body', None)
            e = it(b, word, base, band=base or CREAM, word_ink='#f4f0e4', **d)
            if body is not None:
                e['mats']['body'] = body          # mats are applied over the plain body colour
            es.append(e)
        out[i] = es
    return out


def table(it):
    return {i: es[0] for i, es in looks(it).items()}
