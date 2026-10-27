"""The picture house: the tr_ kit for the small-town theatre on main street (world/county/Theatre.java).

No photos of one in the library yet: this follows what the single-screen houses of the twenties to
the forties are known for. Outside, the marquee over the sidewalk (the letter board on three sides,
ringed with bulbs, the soffit of bulbs underneath), the neon blade over it spelling the name, the
glazed terracotta front, the recessed entry with the ticket booth, the poster cases and the
terrazzo with the name let into it. Inside, the lobby with the candy counter, the popcorn machine
and the soda fountain, the ticket taker's podium, the velvet ropes, the carpet, the sconces and the
chandelier; the stair up to the projection booth (the two projectors at their ports, the rewind
bench, the film cans) and the manager's office; the auditorium raked down to the stage, rows of
velvet seats on iron standards, the gilt proscenium, the curtains and the screen. The last show
left its own way: the board still advertising Saturday's matinee, closed by the county's order, or
asking for help.

Blocks (tile 0 is always the viewer's left):
- marquee: tr_mq_{a,a_on,b,c}_0..29 (fifteen by two, row 0 the upper), tr_mq_box(_on),
  tr_mq_side_0..3(_on) (two by two), tr_bulbs(_on) (axis);
- blade: tr_blade_0..5(_on), tr_blade_cap(_on), tr_blade_foot;
- front: tr_tc, tr_tc_panel, tr_tc_cornice, tr_tc_pilaster, tr_crest, tr_crest_side, tr_poster_0..5_{lo,hi},
  tr_booth_lo, tr_booth_hi, tr_terrazzo, tr_terrazzo_name_0..4 (read from the street);
- lobby: tr_carpet, tr_carpet_b, tr_plaster, tr_wainscot, tr_frieze, tr_candy, tr_candy_empty,
  tr_backbar, tr_soda, tr_popcorn_lo, tr_popcorn_hi, tr_podium, tr_sconce(_on), tr_chandelier(_on),
  tr_lobbycard_0..2, tr_stair, tr_sign_{men,ladies,booth,food};
- auditorium: tr_seat, tr_seat_up, tr_seat_mix, tr_seat_broken, tr_seat_coat, tr_seat_pop,
  tr_seat_aisle(_on), tr_ceiling, tr_plaster_chunk, tr_prosc_col, tr_prosc_0..21 (eleven by two),
  tr_curtain, tr_curtain_hem, tr_curtain_torn, tr_curtain_tie, tr_valance, tr_screen_0..35 and
  tr_screen_lit_0..35 (nine by four, row 0 the top), tr_stage, tr_apron(_on);
- booth: tr_projector_lo, tr_projector_hi, tr_port, tr_rewind, tr_cans, tr_reel, tr_film.

    python3 tools/city/theatre.py        rewrites the tr_ entries in blocks.json

photo_textures.py draws the textures (theatre.register()). The base model faces north: anything
on a wall has its back at z = 16. A face only shows the part of its texture under its box.
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import school  # noqa: E402
from furniture import area, disc, text_mask  # noqa: E402
from school import Pic, grain, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
SANS = school.SANS
SERIF = school.SERIF
HAND = school.HAND
NAME = "RIALTO"
RED = (150, 28, 30)
GOLD = (206, 164, 74)
CREAM = (226, 214, 184)
NEON = (255, 58, 48)
WARM = (255, 214, 140)
BOARDS = {
    # "_" is a letter fallen off the board (placed by hand, so no gap ever spells something else)
    "a": ("NOW SHOWING", "THE LONG HARVEST", "SAT MAT_NEE 2 PM"),
    "b": ("CLOSED BY ORD_R", "OF THE CO_NTY", "STAY HOME  ST_Y SAFE"),
    "c": ("WE ARE INSIDE", "PLEASE HELP", "12 ALIVE  NO SICK"),
}
FILMS = [
    ("THE LONG|HARVEST", "harvest"),
    ("COPPER|RIDGE", "western"),
    ("STARFALL", "rocket"),
    ("THE HOLLOW|HOUR", "house"),
    ("SUMMER AT|PINE LAKE", "lake"),
    ("NIGHT|TRAIN", "train"),
]


def glow(t, mask, col, amount=1.0, emit=0.9):
    """Lights a mask: the colour laid on, the emission set, a soft halo round it."""
    m = np.clip(mask, 0, 1)
    halo = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2)), np.float32) / 255
    t.c = t.c * (1 - halo[..., None] * 0.5 * amount) + np.array(col, np.float32) * halo[..., None] * 0.5 * amount
    t.mix(m, col, amount)
    t.emit[:] = np.maximum(t.emit, m * emit)
    return t


def bulb_mask(xs, ys, r):
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    m = np.zeros((N, N), np.float32)
    for x in xs:
        for y in ys:
            m = np.maximum(m, np.clip(r + 0.5 - np.hypot(xx - x, yy - y), 0, 1))
    return m


# ---------------------------------------------------------------- the marquee

def sheet_metal(seed, rgb=(222, 210, 178)):
    """The marquee's sheet metal: cream enamel, rust from every seam and rivet, black from the rain."""
    t = F.plain(seed, rgb, 0.04, 0.4)
    t.f0[:] = 40
    for y in (0, 31, 63):
        area(t, 0, y, N, y + 1, mul=0.7)
    for x in range(4, N, 10):
        for y in (3, 28, 35, 60):
            disc(t, x, y, 0.8, (120, 80, 50), h=0.9)
    t.streaks(0.7, 0.6)
    t.grime(0.3)
    return t


def soffit(seed, lit):
    """The marquee's underside: rows of bulbs in sockets on the cream sheet, a few dead or gone."""
    t = sheet_metal(seed, (214, 204, 176))
    rng = t.rng
    xs, ys = list(range(8, N, 16)), list(range(8, N, 16))
    for x in xs:
        for y in ys:
            disc(t, x, y, 4.2, (60, 56, 50), h=0.5)
    on = np.zeros((N, N), np.float32)
    for x in xs:
        for y in ys:
            r = rng.random()
            if r < 0.12:
                continue  # the bulb gone
            m = bulb_mask([x], [y], 3.2)
            if lit and r > 0.25:
                on = np.maximum(on, m)
            else:
                t.mix(m, (196, 190, 170) if r > 0.25 else (70, 64, 56))
    if lit:
        glow(t, on, WARM, 1.0, 1.0)
    return t


@lru_cache(maxsize=None)
def board_pic(key, lit):
    """The letter board across the marquee's front, fifteen blocks by two: the bulbs round it, the
    panel lit from behind, three tracks of black letters (and red), some fallen, some crooked."""
    p = Pic(15, 2, (40, 36, 34), 9600 + ord(key[0]) + lit)
    W, H = p.W, p.H
    panel = (236, 230, 206) if lit else (176, 172, 158)
    p.rect(9, 9, W - 9, H - 9, fill=panel)
    for y in (H / 3, 2 * H / 3):
        p.rect(9, y - 0.6, W - 9, y + 0.6, fill=(120, 116, 100))
    rows = BOARDS[key[0]]
    rng = np.random.default_rng(9610 + ord(key[0]))
    f = p.font(BLACK, 28)
    for k, line in enumerate(rows):
        cy = 9 + (H - 18) * (k + 0.5) / 3
        width = sum(f.getlength("N" if ch == "_" else ch) for ch in line) / 4 * 1.02
        x = W / 2 - width / 2
        red = key[0] == "c" and k == 1 or key[0] == "a" and k == 1
        for ch in line:
            w = f.getlength("N" if ch == "_" else ch) / 4 * 1.02
            if ch not in " _":
                col = RED if red else (24, 22, 22)
                g = Image.new("RGBA", (int(w * 4) + 40, 160), (0, 0, 0, 0))
                ImageDraw.Draw(g).text((20, 20), ch, font=f, fill=col + (255,))
                tilt = rng.normal(0, 3 if key[0] == "c" else 1)
                g = g.rotate(float(tilt), resample=Image.BICUBIC)
                p.im.alpha_composite(g, (int(x * 4) - 20, int((cy - 14 + rng.normal(0, 0.6)) * 4) - 20))
            x += w
    return p


def board(key, i):
    p = board_pic(key, key.endswith("_on"))
    t = p.tile(i, 9620 + i + ord(key[0]), smooth=0.4, grime=0.18)
    lit = key.endswith("_on")
    cols, rows = 15, 2
    cx, cy = i % cols, i // cols
    # the bulbs round the board: every 8 texels along the outer edges
    xs = [x for x in range(4, N, 8)]
    pts = []
    if cy == 0:
        pts += [(x, 4) for x in xs]
    if cy == rows - 1:
        pts += [(x, N - 4) for x in xs]
    if cx == 0:
        pts += [(4, y) for y in xs]
    if cx == cols - 1:
        pts += [(N - 4, y) for y in xs]
    rng = np.random.default_rng(9630 + i)
    on = np.zeros((N, N), np.float32)
    for x, y in pts:
        m = bulb_mask([x], [y], 2.2)
        if lit and rng.random() > 0.15:
            on = np.maximum(on, m)
        else:
            t.mix(m, (200, 192, 170) if rng.random() > 0.2 else (60, 56, 50))
    if lit:
        panel = (t.c.mean(-1) > 180).astype(np.float32)
        t.emit[:] = np.maximum(t.emit, panel * 0.3)
        glow(t, on, WARM, 1.0, 1.0)
    t.streaks(0.25, 0.8)
    return t


@lru_cache(maxsize=None)
def side_pic(lit):
    """The marquee's ends, two blocks by two: the name in red on cream, bulbs round."""
    p = Pic(2, 2, (40, 36, 34), 9640 + lit)
    p.rect(9, 9, p.W - 9, p.H - 9, fill=(232, 222, 196) if lit else (190, 182, 160))
    size = civic.fit(NAME, BLACK, 64, p.W - 22)
    p.text(p.W / 2, p.H / 2 - size * 0.62, NAME, BLACK, size, (196, 30, 28) if lit else (130, 34, 30), anchor="ma")
    return p


def side(i, lit):
    t = side_pic(lit).tile(i, 9645 + i, smooth=0.4, grime=0.2)
    cx, cy = i % 2, i // 2
    pts = ([(x, 4) for x in range(4, N, 8)] if cy == 0 else [(x, N - 4) for x in range(4, N, 8)])
    pts += [(4, y) for y in range(4, N, 8)] if cx == 0 else [(N - 4, y) for y in range(4, N, 8)]
    on = np.zeros((N, N), np.float32)
    for x, y in pts:
        m = bulb_mask([x], [y], 2.2)
        if lit:
            on = np.maximum(on, m)
        else:
            t.mix(m, (200, 192, 170))
    if lit:
        red = (t.c[..., 0] > 150) & (t.c[..., 1] < 90)
        t.emit[:] = np.maximum(t.emit, red * 0.8)
        glow(t, on, WARM, 1.0, 1.0)
    t.streaks(0.25, 0.8)
    return t


def bulb(seed, lit, dead=False):
    t = F.plain(seed, (236, 226, 196) if lit else (176, 170, 150), 0.03, 0.9)
    t.f0[:] = 60
    if dead:
        t.c[:] = (64, 60, 54)
    elif lit:
        t.c[:] = WARM
        t.emit[:] = 1.0
    t.grime(0.1)
    return t


# ---------------------------------------------------------------- the blade

@lru_cache(maxsize=None)
def blade_pic(lit):
    """The blade, one block across and eight high: the cap, RIALTO a letter to a block in neon tube
    on red enamel, bulbs chasing down both edges, the foot."""
    p = Pic(1, 8, (120, 26, 24), 9650 + lit)
    W = p.W
    p.rect(0, 0, W, p.H, fill=(132, 30, 28))
    p.rect(5, 5, W - 5, p.H - 5, outline=GOLD, width=2)
    return p


def blade(i, lit):
    """Tile i of the blade: 0 the cap, 1..6 the letters, 7 the foot."""
    t = blade_pic(lit).tile(i, 9655 + i, smooth=0.45, grime=0.2)
    t.f0[:] = 40
    if 1 <= i <= 6:
        ch = NAME[i - 1]
        m = text_mask(ch, BLACK, 46, N, N, seed=i)
        solid = (m > 0.5).astype(np.float32)
        er = np.asarray(Image.fromarray((solid * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(5)), np.float32) / 255
        tube = np.clip(solid - er, 0, 1)
        t.mix(solid, (96, 22, 20))
        if lit:
            glow(t, tube, NEON, 1.0, 1.0)
        else:
            t.mix(tube, (190, 120, 110))
    pts = [(9, y) for y in range(4, N, 10)] + [(N - 9, y) for y in range(9, N, 10)]
    on = np.zeros((N, N), np.float32)
    for x, y in pts:
        m = bulb_mask([x], [y], 2.0)
        if lit and t.rng.random() > 0.2:
            on = np.maximum(on, m)
        else:
            t.mix(m, (200, 190, 160))
    if lit:
        glow(t, on, (255, 200, 90), 1.0, 1.0)
    if i == 0:
        area(t, 10, 20, N - 10, 44, GOLD, h=0.9, f0=200)
    t.streaks(0.4, 0.75)
    return t


# ---------------------------------------------------------------- the front

def terracotta(seed, kind="plain", wear=None):
    """Glazed terracotta, cream: the joints, the crazed glaze, soot down from the cornice, a chip
    showing the red body. kind: plain, panel (a sunburst in relief), cornice (dentils), pilaster (flutes).
    wear (the plain blocks' weathered versions): b (rain-stained), chip (a corner broken off to the
    red body), crack, rust (a bleed from an anchor bolt), soot (black down from the coping)."""
    t = F.plain(seed, CREAM, 0.03, 0.62)
    t.f0[:] = 50
    area(t, 0, 0, N, 1, mul=0.75, dh=-0.2)
    area(t, 0, 0, 1, N, mul=0.75, dh=-0.2)
    rng = t.rng
    if kind == "panel":
        cx, cy = 32, 44
        for k in range(9):
            a = np.pi * (k + 0.5) / 9
            for r in range(8, 28):
                x, y = int(cx - np.cos(a) * r), int(cy - np.sin(a) * r)
                area(t, x, y, x + 2, y + 2, mul=0.82, dh=0.25)
        disc(t, cx, cy, 6, (200, 170, 110), h=0.95)
        area(t, 4, 50, 60, 54, mul=0.85, dh=0.2)
    elif kind == "cornice":
        area(t, 0, 0, N, 14, mul=1.05, dh=0.3)
        for x in range(2, N, 8):
            area(t, x, 18, x + 5, 30, mul=0.92, dh=0.25)
        area(t, 0, 34, N, 38, mul=0.8, dh=-0.1)
        for x in range(0, N, 16):
            area(t, x + 4, 44, x + 12, 52, (180, 150, 92), h=0.9)
    elif kind == "pilaster":
        for x in (10, 22, 34, 46):
            area(t, x, 0, x + 6, N, mul=0.86, dh=-0.3)
            area(t, x + 1, 0, x + 2, N, mul=1.08)
    for _ in range(14):
        x, y = rng.uniform(0, N, 2)
        area(t, int(x), int(y), int(x) + int(rng.integers(3, 10)), int(y) + 1, mul=0.85)
    if wear == "chip":
        # a corner gone: the red-brown body, rough, its edge raw
        cx, cy = (rng.choice([0, N]), rng.choice([0, N]))
        m = F.blob_mask(rng, cx, cy, rng.uniform(16, 26), rough=0.7, wrap=False)
        t.mix(m, (146, 82, 58))
        t.mix(((rng.random((N, N)) < 0.3) & (m > 0.5)).astype(np.float32), (110, 60, 44), 0.5)
        t.h -= m * 0.5
        t.smooth -= m * 0.4
        t.f0[m > 0.5] = 10
        edge = np.clip(m * (1 - m) * 4, 0, 1)
        t.mix(edge, (90, 70, 56), 0.7)
    elif wear == "crack":
        t.crack(3, 0.35, (60, 52, 44))
        t.crack(2, 0.2, (90, 80, 66))
    elif wear == "rust":
        x = int(rng.uniform(14, 50))
        disc(t, x, 6, 2.5, (70, 40, 26), h=0.7)
        for k in range(4):
            xx = x + int(rng.integers(-3, 4))
            length = int(rng.uniform(30, 60))
            m = np.zeros((N, N), np.float32)
            m[6:6 + length, max(0, xx - 1):xx + 2] = np.linspace(0.8, 0.1, len(range(6, min(N, 6 + length))))[:, None]
            t.mix(P.blur(m, 1), (150, 78, 36), 0.8)
    elif wear == "soot":
        y = np.arange(N, dtype=np.float32)[:, None] / N
        g = np.clip(1.1 - y * 1.4 + P.fbm(rng) * 0.35, 0, 1) * np.ones((1, N), np.float32)
        t.mix(g * 0.75, (38, 34, 30))
        t.streaks(0.9, 0.4)
    if wear in ("b", "soot", "rust"):
        t.streaks(0.9, 0.5)
        t.grime(0.4)
    t.streaks(0.65, 0.65)
    t.grime(0.25)
    return t


POSTER_COLS = {
    "harvest": ((222, 150, 60), (120, 60, 30)),
    "western": ((200, 96, 50), (60, 30, 24)),
    "rocket": ((30, 40, 90), (220, 220, 240)),
    "house": ((24, 30, 40), (200, 210, 160)),
    "lake": ((70, 160, 200), (240, 220, 140)),
    "train": ((40, 30, 50), (240, 180, 60)),
}


@lru_cache(maxsize=None)
def poster_pic(n):
    """A one-sheet in its case, a block across and two high: the picture, the title, the credit
    block, NOW SHOWING or COMING SOON on the case's header, the brass frame."""
    title, kind = FILMS[n]
    bg, ink = POSTER_COLS[kind]
    p = Pic(1, 2, (150, 120, 60), 9700 + n)
    W, H = p.W, p.H
    p.rect(4, 12, W - 4, H - 4, fill=bg)
    sky = mixc(bg, (255, 255, 255), 0.25)
    if kind == "harvest":
        p.rect(4, 12, W - 4, 70, fill=(236, 170, 80))
        p.ellipse(18, 40, 46, 68, fill=(250, 220, 120))
        p.rect(4, 64, W - 4, 96, fill=(196, 150, 50))
        for x in range(6, W - 4, 3):
            p.line([(x, 96), (x + 1, 72)], (150, 110, 40), 0.6)
        p.poly([(40, 66), (40, 56), (46, 50), (52, 56), (52, 66)], fill=(80, 40, 30))
    elif kind == "western":
        p.rect(4, 12, W - 4, 74, fill=(226, 130, 70))
        p.poly([(4, 74), (10, 50), (22, 50), (26, 74)], fill=(150, 60, 40))
        p.poly([(30, 74), (36, 44), (54, 44), (60, 74)], fill=(130, 50, 34))
        p.rect(4, 74, W - 4, 96, fill=(170, 90, 50))
        p.poly([(22, 92), (24, 80), (34, 78), (40, 82), (42, 92)], fill=(30, 20, 18))
        p.rect(29, 70, 34, 79, fill=(30, 20, 18))
        p.ellipse(28, 66, 35, 71, fill=(30, 20, 18))
    elif kind == "rocket":
        rng = np.random.default_rng(3)
        for _ in range(40):
            x, y = rng.uniform(6, W - 6), rng.uniform(14, 90)
            p.ellipse(x, y, x + 0.8, y + 0.8, fill=(240, 240, 255))
        p.ellipse(34, 20, 56, 42, fill=(200, 80, 60))
        p.poly([(24, 92), (30, 40), (36, 92)], fill=(220, 220, 230))
        p.poly([(22, 92), (26, 80), (26, 92)], fill=(200, 40, 40))
        p.poly([(38, 92), (34, 80), (34, 92)], fill=(200, 40, 40))
        p.poly([(27, 92), (30, 104), (33, 92)], fill=(255, 170, 40))
    elif kind == "house":
        p.ellipse(36, 18, 54, 36, fill=(230, 230, 200))
        p.poly([(10, 96), (10, 60), (20, 46), (30, 60), (30, 52), (36, 52), (36, 66), (46, 66), (46, 96)], fill=(14, 16, 20))
        p.rect(16, 66, 20, 72, fill=(240, 200, 80))
        p.rect(38, 72, 42, 78, fill=(240, 200, 80))
        for x in (6, 52):
            p.line([(x, 96), (x + 3, 60), (x - 2, 50)], (14, 16, 20), 1.2)
    elif kind == "lake":
        p.rect(4, 12, W - 4, 60, fill=(120, 190, 220))
        p.rect(4, 60, W - 4, 80, fill=(40, 110, 160))
        p.rect(4, 80, W - 4, 100, fill=(236, 210, 150))
        for x0 in (8, 44):
            p.poly([(x0, 60), (x0 + 6, 30), (x0 + 12, 60)], fill=(30, 80, 50))
        p.ellipse(24, 74, 30, 80, fill=(230, 160, 120))
        p.ellipse(32, 74, 38, 80, fill=(230, 160, 120))
    else:
        p.rect(4, 70, W - 4, 74, fill=(70, 60, 60))
        p.rect(10, 52, 54, 70, fill=(20, 18, 24))
        for x in range(14, 50, 8):
            p.rect(x, 56, x + 5, 61, fill=(250, 200, 90))
        p.ellipse(4, 54, 14, 70, fill=(255, 240, 180))
        p.poly([(4, 58), (-10, 40), (-10, 90)], fill=(255, 240, 180), a=90)
    size = civic.fit(title, BLACK, 13, W - 10)
    p.text(W / 2, 98, title, BLACK, size, ink, anchor="ma")
    for k in range(4):
        y = 110 + k * 3
        p.rect(10, y, W - 10, y + 1.2, fill=mixc(ink, bg, 0.4))
    head = "NOW SHOWING" if n % 2 == 0 else "COMING SOON"
    p.rect(0, 0, W, 12, fill=(40, 34, 30))
    p.text(W / 2, 3, head, SANS, 6, (236, 200, 120), anchor="ma")
    p.rect(1, 1, W - 1, H - 1, outline=GOLD, width=3)
    return p


def poster(n, part):
    t = poster_pic(n).tile(0 if part == "hi" else 1, 9710 + n * 2 + (part == "lo"), smooth=0.75, grime=0.15)
    yy, xx = np.mgrid[0:N, 0:N]
    streak = (np.abs((xx - yy * 0.6) - 20) < 3).astype(np.float32)
    t.mix(streak, (250, 250, 250), 0.15)
    t.streaks(0.25, 0.85)
    return t


def booth_glass(seed):
    """The ticket booth's glass: brass glazing bars, the ticket window with its speaking grille, TICKETS in gilt."""
    t = F.plain(seed, (60, 70, 72), 0.04, 0.95)
    t.f0[:] = 60
    t.frame(3, GOLD, f0=220)
    area(t, 30, 0, 34, N, GOLD, f0=220, h=0.95)
    disc(t, 16, 36, 6, (90, 84, 70), h=0.7)
    for k in range(5):
        disc(t, 13 + (k % 3) * 3, 33 + (k // 3) * 4, 0.7, (30, 30, 30), h=0.4)
    m = text_mask("TICKETS", SANS, 7, 56, 9, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 4, 6, GOLD)
    area(t, 4, 56, 60, 62, (110, 80, 50))
    t.grime(0.25)
    return t


def booth_base(seed):
    """The booth's base: mahogany panels, a brass kick plate, the deal tray slot."""
    t = F.plain(seed, (96, 50, 36), 0.06, 0.6)
    g = grain(t.rng, True)
    t.c *= (1 + 0.08 * g)[..., None]
    for x0 in (4, 34):
        F.gap(t, x0, 10, x0 + 26, 50)
    area(t, 0, 54, N, N, GOLD, f0=220, h=0.9)
    area(t, 8, 0, 56, 6, (60, 40, 30))
    t.grime(0.3)
    return t


def terrazzo(seed):
    """Terrazzo: marble chips in cream cement, polished and then forty years of shoes, brass strips."""
    t = F.plain(seed, (204, 196, 176), 0.04, 0.7)
    rng = t.rng
    for _ in range(200):
        x, y = rng.uniform(0, N, 2)
        col = [(150, 140, 120), (110, 104, 96), (210, 160, 140), (230, 226, 216)][rng.integers(4)]
        disc(t, x, y, rng.uniform(0.6, 1.6), col, h=0.95)
    area(t, 0, 0, N, 1, GOLD, f0=220)
    area(t, 0, 0, 1, N, GOLD, f0=220)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def name_pic():
    p = Pic(5, 1, (0, 0, 0, 0), 9730)
    p.rect(2, 6, p.W - 2, p.H - 6, fill=(120, 30, 30))
    p.rect(4, 8, p.W - 4, p.H - 8, outline=GOLD, width=1.5)
    size = civic.fit(NAME, SERIF, 48, p.W - 40)
    p.text(p.W / 2, p.H / 2 - size * 0.6, NAME, SERIF, size, (232, 220, 190), anchor="ma")
    return p


def terrazzo_name(i):
    """A tile of the name in the terrazzo, upright for someone coming in off the street (so turned
    half round in the texture, whose top is north)."""
    t = terrazzo(9731 + i)
    a = name_pic().shrunk()
    tile = a[:, i * N:(i + 1) * N][::-1, ::-1]
    m = tile[..., 3:4] / 255
    t.c = t.c * (1 - m) + tile[..., :3] * m
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the lobby

def carpet(seed, worn=False):
    """The lobby carpet: deep red, a gold and black deco figure repeating every block, trodden to the
    weave down the middle, stains."""
    t = F.plain(seed, (120, 24, 30), 0.06, 0.05)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    d = np.abs(xx - 32) + np.abs(yy - 32)
    m1 = (np.abs(d - 20) < 1.6).astype(np.float32)
    m2 = (np.abs(d - 26) < 1.0).astype(np.float32)
    fan = ((d < 9) & ((xx + yy) % 4 < 2)).astype(np.float32)
    t.mix(m1, (196, 150, 60))
    t.mix(m2, (24, 18, 18))
    t.mix(fan, (196, 150, 60))
    corners = ((np.hypot(xx, yy) < 7) | (np.hypot(xx - 64, yy) < 7) | (np.hypot(xx, yy - 64) < 7) | (np.hypot(xx - 64, yy - 64) < 7))
    t.mix(corners.astype(np.float32), (28, 70, 60))
    t.speckle(0.25, (90, 20, 24), 0.5)
    if worn:
        w = np.clip(F.periodic(t.rng, 4) * 1.4 - 0.4, 0, 1)
        t.c = t.c * (1 - 0.4 * w[..., None]) + np.array((110, 80, 70), np.float32) * 0.4 * w[..., None]
        F.stain(t, t.rng.uniform(10, 54), t.rng.uniform(10, 54), 10, (60, 30, 24), 0.6)
    t.grime(0.25)
    return t


def plaster(seed, wear=None):
    """The walls above the panelling: oxblood paint with a damask stencilled in old gold; wear:
    stain (a water stain run down from the roof), peel (the paint and plaster blown off)."""
    t = F.plain(seed, (104, 30, 32), 0.05, 0.3)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    for cx, cy in ((16, 16), (48, 48), (48, -16), (16, 80), (-16, 48), (80, 16)):
        d = np.hypot((xx - cx) * 1.4, yy - cy)
        m = ((np.abs(d - 9) < 1) | (d < 2.5)).astype(np.float32)
        t.mix(m, (170, 130, 64), 0.7)
    t.streaks(0.6, 0.7)
    if wear == "stain":
        F.stain(t, t.rng.uniform(14, 50), t.rng.uniform(8, 30), t.rng.uniform(10, 16), (60, 50, 40), 0.5)
        t.streaks(0.9, 0.5)
    elif wear == "peel":
        m = F.blob_mask(t.rng, t.rng.uniform(16, 48), t.rng.uniform(16, 48), t.rng.uniform(9, 15), rough=0.7, wrap=False)
        t.mix(m, (196, 188, 170))
        t.mix(((t.rng.random((N, N)) < 0.2) & (m > 0.5)).astype(np.float32), (150, 144, 130), 0.6)
        t.h -= m * 0.3
        t.mix(np.clip(m * (1 - m) * 4, 0, 1), (60, 22, 22), 0.6)
    t.grime(0.25)
    return t


def upper(seed, stain=False):
    """The upper walls of the house: ochre paint with a diamond lattice stencilled darker, rain
    stains from the roof."""
    t = F.plain(seed, (170, 132, 80), 0.05, 0.3)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    lat = ((np.abs(((xx + yy) % 32) - 16) < 0.8) | (np.abs(((xx - yy) % 32) - 16) < 0.8)).astype(np.float32)
    t.mix(lat, (120, 86, 46), 0.6)
    dots = ((np.hypot((xx % 32) - 16, (yy % 32)) < 2.2) | (np.hypot((xx % 32) - 16, (yy % 32) - 32) < 2.2)).astype(np.float32)
    t.mix(dots, (196, 160, 90), 0.8)
    t.streaks(0.8, 0.6)
    if stain:
        F.stain(t, t.rng.uniform(14, 50), t.rng.uniform(4, 20), t.rng.uniform(10, 18), (90, 70, 46), 0.55)
    t.grime(0.3)
    return t


def wainscot(seed):
    t = F.plain(seed, (70, 34, 24), 0.05, 0.6)
    g = grain(t.rng, True)
    t.c *= (1 + 0.1 * g)[..., None]
    F.gap(t, 6, 8, 58, 54)
    area(t, 0, 0, N, 4, mul=1.2, dh=0.3)
    t.grime(0.35)
    return t


def frieze(seed):
    t = plaster(seed)
    area(t, 0, 0, N, 18, (150, 116, 56), h=0.9)
    for x in range(0, N, 8):
        area(t, x + 2, 4, x + 6, 14, (90, 26, 28))
    area(t, 0, 18, N, 20, (40, 20, 18))
    return t


@lru_cache(maxsize=None)
def candy_pic(empty):
    """Looking into the candy case: two glass shelves of boxes stacked flat, bars fanned in their
    trays, jars of sweets with their lids, the price cards (or cleared out, the glass cracked)."""
    p = Pic(1, 1, (36, 28, 24), 9740 + empty)
    rng = np.random.default_rng(9741)
    wraps = [(214, 52, 44), (238, 196, 56), (56, 96, 176), (236, 232, 220), (92, 52, 30), (60, 150, 86), (226, 120, 40),
             (150, 60, 140)]
    for y in (16, 38):
        base = y + 12
        p.rect(3, base, 61, base + 1.5, fill=(170, 190, 190))
        x = 4.0
        while x < 56:
            if empty and rng.random() > 0.15:
                x += rng.uniform(5, 9)
                continue
            col = wraps[rng.integers(len(wraps))]
            kind = rng.random()
            if kind < 0.45:
                # boxes stacked flat, a label band across each
                w, hh = rng.uniform(8, 12), rng.uniform(3, 4.5)
                for k in range(int(rng.integers(1, 4))):
                    yb = base - (k + 1) * hh
                    p.rect(x + k * 0.4, yb, x + w + k * 0.4, yb + hh - 0.4, fill=col)
                    p.rect(x + 1.5 + k * 0.4, yb + 1, x + w - 1.5 + k * 0.4, yb + 2, fill=(250, 246, 230))
            elif kind < 0.75:
                # bars fanned in a tray
                w = rng.uniform(9, 13)
                p.rect(x, base - 2, x + w, base, fill=(180, 170, 150))
                for k in range(4):
                    p.rect(x + 0.5, base - 2 - (k + 1) * 1.6, x + w - 0.5, base - 2.3 - k * 1.6, fill=wraps[rng.integers(len(wraps))])
            else:
                # a jar of sweets, its red lid
                w, h = rng.uniform(6, 8), rng.uniform(8, 11)
                p.rect(x, base - h, x + w, base, fill=(200, 210, 205))
                for _ in range(int(w * h / 3)):
                    cx, cy = rng.uniform(x + 0.8, x + w - 0.8), rng.uniform(base - h + 2, base - 0.8)
                    p.rect(cx - 0.6, cy - 0.6, cx + 0.6, cy + 0.6, fill=wraps[rng.integers(len(wraps))])
                p.rect(x - 0.3, base - h - 1.5, x + w + 0.3, base - h, fill=(190, 40, 36))
            if rng.random() < 0.4:
                p.rect(x + 1, base + 1.8, x + 4, base + 3.6, fill=(240, 236, 220))
            x += w + 1
    if empty:
        p.line([(10, 4), (30, 30), (24, 60)], (230, 230, 230), 0.6)
        p.line([(30, 30), (54, 22)], (230, 230, 230), 0.5)
    return p


def velour(seed):
    """The black masking round the screen: velour gone grey with dust, its folds, a moth hole."""
    t = F.plain(seed, (22, 20, 21), 0.05, 0.05)
    xx = np.arange(N, dtype=np.float32)
    t.c *= (0.8 + 0.3 * np.sin(xx / N * np.pi * 5))[None, :, None]
    t.grime(0.3, (70, 66, 62))
    t.speckle(0.02, (90, 86, 80), 0.5)
    return t


def candy(empty):
    t = candy_pic(empty).tile(0, 9745 + empty, smooth=0.9, grime=0.1)
    t.f0[:] = 60
    yy, xx = np.mgrid[0:N, 0:N]
    t.mix((np.abs(xx - yy - 10) < 2).astype(np.float32), (240, 240, 240), 0.2)
    return t


def popcorn_glass(seed):
    """The popcorn machine's cabinet: the kettle hung in the glass, popcorn heaped below gone grey,
    POPCORN across the red header."""
    t = F.plain(seed, (40, 34, 30), 0.04, 0.9)
    area(t, 0, 0, N, 14, (190, 30, 30))
    m = text_mask("POPCORN", BLACK, 10, 58, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 3, 1, (250, 236, 200))
    disc(t, 32, 26, 9, (190, 190, 186), h=0.9)
    area(t, 22, 26, 42, 30, (150, 150, 146), f0=200)
    rng = t.rng
    for _ in range(180):
        x, y = rng.uniform(4, 60), rng.uniform(40, 62)
        disc(t, x, y, rng.uniform(1, 2), (222, 210, 170) if rng.random() < 0.7 else (150, 140, 110), h=0.8)
    t.frame(2, (200, 200, 196), f0=220)
    t.grime(0.2)
    return t


def cart_red(seed):
    t = F.plain(seed, (170, 30, 30), 0.05, 0.6)
    t.f0[:] = 40
    for y in (10, 54):
        area(t, 0, y, N, y + 2, (220, 200, 140), f0=200)
    m = text_mask("FRESH  HOT", SANS, 7, 56, 9, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 4, 28, (240, 220, 160))
    t.streaks(0.4, 0.8)
    t.grime(0.3)
    return t


def soda_front(seed):
    """The soda fountain's front: stainless, three taps, ICE COLD in red script, the drip tray."""
    t = F.plain(seed, (190, 192, 190), 0.03, 0.8)
    t.f0[:] = 230
    area(t, 4, 6, 60, 22, (176, 30, 30))
    m = text_mask("ICE COLD", SERIF, 9, 52, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 6, 8, (250, 240, 230))
    for x in (14, 30, 46):
        area(t, x, 26, x + 4, 40, (60, 60, 60), f0=200)
    area(t, 6, 52, 58, 58, (70, 70, 70), f0=200)
    t.grime(0.25)
    return t


def cups(seed):
    t = F.plain(seed, (236, 232, 220), 0.03, 0.4)
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=0.86)
    area(t, 0, 20, N, 30, (190, 40, 40))
    t.grime(0.2)
    return t


def podium(seed):
    t = F.plain(seed, (84, 44, 32), 0.06, 0.6)
    g = grain(t.rng, True)
    t.c *= (1 + 0.08 * g)[..., None]
    area(t, 12, 20, 52, 30, GOLD, f0=220, h=0.95)
    m = text_mask("TICKETS PLEASE", SANS, 5, 38, 7, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 13, 21, (60, 40, 20))
    t.grime(0.3)
    return t


def frosted(seed, lit):
    t = F.plain(seed, (236, 226, 200) if lit else (190, 184, 168), 0.04, 0.85)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    rays = (np.abs(np.sin(np.arctan2(yy - 64, xx - 32) * 9)) < 0.18).astype(np.float32)
    t.mix(rays, (150, 130, 90), 0.5)
    if lit:
        t.c = t.c * 0.6 + np.array((255, 220, 160), np.float32) * 0.4
        t.emit[:] = 0.85
    t.grime(0.15)
    return t


def crystal(seed, lit):
    t = F.plain(seed, (210, 214, 220), 0.08, 0.95)
    t.f0[:] = 90
    t.speckle(0.25, (255, 255, 255), 0.8)
    if lit:
        t.c = t.c * 0.5 + np.array((255, 226, 170), np.float32) * 0.5
        t.emit[:] = 0.8
    t.grime(0.12)
    return t


def brass(seed):
    t = F.plain(seed, (176, 140, 70), 0.05, 0.75)
    t.f0[:] = 230
    t.speckle(0.06, (80, 90, 60), 0.6)
    t.grime(0.2)
    return t


def velvet(seed, rgb=(130, 22, 30), torn=False):
    t = F.plain(seed, rgb, 0.06, 0.08)
    t.speckle(0.2, mixc(rgb, (0, 0, 0), 0.3), 0.5)
    if torn:
        for _ in range(3):
            x, y = t.rng.uniform(8, 56, 2)
            disc(t, x, y, t.rng.uniform(3, 7), (220, 200, 120), h=0.7)
    t.grime(0.25)
    return t


def lobbycard(n):
    """A lobby card in its frame: a sepia still from the picture, the title strip."""
    title, kind = FILMS[n + 1]
    t = F.plain(9750 + n, (30, 24, 20), 0.05, 0.6)
    area(t, 0, 0, N, N, GOLD, f0=220)
    area(t, 3, 3, 61, 61, (30, 24, 20))
    # the still: the picture off its one-sheet, faded toward sepia under the glass
    a = poster_pic(n + 1).shrunk().astype(np.float32)
    still = a[16:58, 5:59, :3]
    grey = still.mean(-1, keepdims=True)
    still = still * 0.55 + grey * np.array([1.05, 0.9, 0.7], np.float32) * 0.45
    t.c[6:48, 5:59] = still
    rng = t.rng
    F.stain(t, rng.uniform(10, 54), rng.uniform(10, 44), rng.uniform(3, 6), (90, 70, 40), 0.5)
    area(t, 4, 50, 60, 58, (40, 30, 24))
    m = text_mask(title.replace("|", " "), SANS, 5, 54, 7, seed=n)
    F.paste(t, (m > 0.4).astype(np.float32), 5, 51, (230, 200, 140))
    t.grime(0.25)
    return t


def plaque(seed, text):
    t = F.plain(seed, (176, 140, 70), 0.04, 0.8)
    t.f0[:] = 230
    area(t, 3, 3, 61, 61, (40, 30, 26))
    size = civic.fit(text, SANS, 9, 54)
    m = text_mask(text, SANS, size, 56, 40, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 4, 12, (220, 190, 120))
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the auditorium

def seat_end(seed):
    """The iron standard at the end of a row: a deco fan cast in it, picked out in gold."""
    t = F.plain(seed, (40, 36, 34), 0.05, 0.5)
    t.f0[:] = 60
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    rays = ((np.abs(np.sin(np.arctan2(yy - 50, xx - 32) * 7)) < 0.2) & (np.hypot(xx - 32, yy - 50) < 26)).astype(np.float32)
    t.mix(rays, GOLD, 0.8)
    t.speckle(0.05, (110, 70, 40), 0.6)
    t.grime(0.2)
    return t


def coffer(seed):
    """The auditorium ceiling: plaster coffers, a gilt rosette in each, the water got in and brought it down in places."""
    t = F.plain(seed, (200, 186, 156), 0.04, 0.3)
    area(t, 0, 0, N, 6, mul=0.8, dh=-0.3)
    area(t, 0, 0, 6, N, mul=0.8, dh=-0.3)
    area(t, 10, 10, 54, 54, mul=0.9, dh=-0.2)
    disc(t, 32, 32, 7, GOLD, h=0.95)
    disc(t, 32, 32, 3, (140, 100, 50), h=0.9)
    F.stain(t, t.rng.uniform(10, 54), t.rng.uniform(10, 54), 16, (120, 100, 70), 0.5)
    t.crack(3)
    t.grime(0.3)
    return t


def gilt(seed, kind="plain"):
    t = F.plain(seed, (190, 150, 70), 0.06, 0.7)
    t.f0[:] = 230
    if kind == "col":
        for x in (8, 20, 32, 44):
            area(t, x, 0, x + 8, N, mul=0.75, dh=-0.4)
            area(t, x + 2, 0, x + 3, N, mul=1.2)
    t.speckle(0.08, (90, 80, 60), 0.6)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def prosc_pic():
    """The proscenium's top band, eleven blocks by two: gilt scrolls, the masks in a cartouche at the middle."""
    p = Pic(11, 2, (176, 136, 64), 9760)
    W, H = p.W, p.H
    p.rect(0, 0, W, 10, fill=(204, 166, 86))
    p.rect(0, H - 12, W, H, fill=(204, 166, 86))
    p.rect(0, H - 14, W, H - 12, fill=(110, 80, 40))
    for x in range(0, W, 32):
        p.ellipse(x + 4, 30, x + 28, 54, outline=(120, 86, 40), width=2)
        p.ellipse(x + 10, 36, x + 22, 48, fill=(216, 180, 100))
        p.line([(x + 28, 42), (x + 36, 42)], (120, 86, 40), 2)
    cx = W / 2
    p.ellipse(cx - 40, 8, cx + 40, H - 8, fill=(150, 30, 34), outline=(220, 186, 100), width=3)
    for dx, smile in ((-15, True), (15, False)):
        p.ellipse(cx + dx - 12, 30, cx + dx + 12, 64, fill=(238, 228, 200))
        p.ellipse(cx + dx - 7, 40, cx + dx - 3, 45, fill=(30, 20, 20))
        p.ellipse(cx + dx + 3, 40, cx + dx + 7, 45, fill=(30, 20, 20))
        if smile:
            p.d.arc([(cx + dx - 7) * 4, 46 * 4, (cx + dx + 7) * 4, 58 * 4], 10, 170, fill=(30, 20, 20, 255), width=8)
        else:
            p.d.arc([(cx + dx - 7) * 4, 52 * 4, (cx + dx + 7) * 4, 64 * 4], 190, 350, fill=(30, 20, 20, 255), width=8)
    return p


def prosc(i):
    t = prosc_pic().tile(i, 9761 + i, smooth=0.7, grime=0.3)
    t.f0[:] = 200
    t.speckle(0.06, (90, 80, 60), 0.6)
    t.streaks(0.3, 0.75)
    return t


def curtain(seed, kind="plain"):
    """Stage velvet: deep folds (light and shade in bands down the drop), the gold fringe at the hem,
    the tie-back, or torn to rags at the bottom."""
    t = F.plain(seed, (140, 20, 28), 0.04, 0.08)
    xx = np.arange(N, dtype=np.float32)
    fold = 0.75 + 0.35 * np.sin(xx / N * np.pi * 6 + seed % 3)
    t.c *= fold[None, :, None]
    t.speckle(0.15, (90, 14, 20), 0.4)
    if kind == "hem":
        area(t, 0, 48, N, 58, GOLD, h=0.9)
        for x in range(0, N, 2):
            area(t, x, 58, x + 1, N, (180, 140, 60))
    elif kind == "valance":
        area(t, 0, 40, N, 48, GOLD, h=0.9)
        for x in range(0, N, 2):
            area(t, x, 48, x + 1, 58, (180, 140, 60))
        t.alpha[58:] = 0
    elif kind == "torn":
        rng = t.rng
        edge = 40 + np.cumsum(rng.normal(0, 3, N)).astype(int) % 20
        for x in range(N):
            t.alpha[edge[x]:, x] = 0
        for _ in range(3):
            x, y = rng.uniform(6, 58), rng.uniform(6, 36)
            r = rng.uniform(2, 5)
            yy, xx2 = np.mgrid[0:N, 0:N]
            t.alpha[np.hypot(xx2 - x, yy - y) < r] = 0
    t.streaks(0.3, 0.8)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def screen_pic(lit):
    """The screen, nine blocks by four: the black masking round it, the perforated white gone grey and
    tide-marked with damp, the long tear; lit, the last frame stuck in the gate and burning through."""
    p = Pic(9, 4, (16, 16, 18), 9770 + lit)
    W, H = p.W, p.H
    p.rect(14, 10, W - 14, H - 12, fill=(206, 204, 196) if not lit else (236, 232, 220))
    if lit:
        # the frame: a field at dusk, a farmhouse, two figures; then the burn eating it from the middle
        p.rect(14, 10, W - 14, 150, fill=(232, 176, 110))
        p.rect(14, 150, W - 14, H - 12, fill=(150, 120, 60))
        p.poly([(300, 150), (300, 112), (330, 90), (360, 112), (360, 150)], fill=(70, 50, 40))
        for x in (200, 214):
            p.rect(x, 136, x + 6, 160, fill=(40, 30, 30))
            p.ellipse(x - 1, 128, x + 7, 137, fill=(40, 30, 30))
        cx, cy = 288, 128
        for r, col in ((70, (255, 236, 200)), (52, (255, 190, 90)), (40, (250, 250, 240)), (24, (255, 255, 255))):
            p.ellipse(cx - r * 1.2, cy - r, cx + r * 1.2, cy + r, fill=col)
        p.ellipse(cx - 16, cy - 10, cx + 4, cy + 8, fill=(120, 60, 30))
    else:
        rng = np.random.default_rng(9771)
        for _ in range(6):
            x, y = rng.uniform(40, W - 40), rng.uniform(30, H - 30)
            r = rng.uniform(20, 50)
            p.ellipse(x - r, y - r * 0.6, x + r, y + r * 0.6, outline=(150, 140, 120), width=2, a=150)
        p.scrawl(60, H - 60, "WE WERE HERE", school.SCRAWL, 18, (120, 20, 20), 0.4)
    p.line([(380, 20), (400, 120), (392, 230)], (16, 16, 18), 5)
    return p


def screen(i, lit):
    t = screen_pic(lit).tile(i, 9775 + i + lit * 40, smooth=0.3, grime=0.1 if lit else 0.2)
    yy, xx = np.mgrid[0:N, 0:N]
    holes = ((xx % 3 == 0) & (yy % 3 == 0)).astype(np.float32)
    t.c *= (1 - 0.08 * holes)[..., None]
    if lit:
        lum = t.c.mean(-1)
        t.emit[:] = np.clip((lum - 60) / 140, 0, 1) * 0.9
    return t


def boards(seed):
    """The stage: painted black boards gone grey, gaff tape marks, a chalk cross."""
    t = F.plain(seed, (56, 52, 50), 0.05, 0.35)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.2)
    area(t, 20, 30, 28, 32, (220, 200, 60))
    area(t, 23, 26, 25, 36, (220, 200, 60))
    t.grime(0.3)
    return t


def apron(seed, lit):
    """The stage front: a moulded panel, the footlight trough along the top with its row of lamps."""
    t = F.plain(seed, (60, 30, 26), 0.05, 0.5)
    F.gap(t, 6, 22, 58, 58)
    area(t, 0, 0, N, 12, (40, 36, 34))
    on = bulb_mask(range(4, N, 8), [6], 2.4)
    if lit:
        glow(t, on, (255, 210, 150), 1.0, 1.0)
    else:
        t.mix(on, (190, 180, 150))
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- the booth

def hammered(seed, rgb=(70, 78, 80)):
    t = F.plain(seed, rgb, 0.08, 0.6)
    t.f0[:] = 120
    t.speckle(0.3, mixc(rgb, (255, 255, 255), 0.15), 0.4)
    t.grime(0.2)
    return t


def reel_face(seed):
    """A film reel side on: the aluminium flange with its holes, the film wound dark inside."""
    t = F.plain(seed, (40, 34, 30), 0.04, 0.6)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    r = np.hypot(xx - 32, yy - 32)
    t.mix((r < 30).astype(np.float32), (184, 186, 186))
    t.mix((r < 22).astype(np.float32), (36, 30, 26))
    for k in range(5):
        a = k * np.pi * 2 / 5
        t.mix((np.hypot(xx - 32 - np.cos(a) * 12, yy - 32 - np.sin(a) * 12) < 4).astype(np.float32), (60, 60, 60))
    t.mix((r < 4).astype(np.float32), (200, 200, 200))
    t.f0[:] = 200
    t.grime(0.2)
    return t


def film_can(seed):
    t = F.plain(seed, (176, 178, 176), 0.04, 0.7)
    t.f0[:] = 210
    area(t, 16, 20, 48, 34, (230, 220, 190))
    m = text_mask("REEL 3", HAND, 7, 30, 10, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 17, 22, (40, 40, 120))
    for y in (6, 58):
        area(t, 0, y, N, y + 2, mul=0.75)
    t.speckle(0.05, (120, 80, 50), 0.6)
    t.grime(0.25)
    return t


def film_strip(seed):
    t = F.plain(seed, (40, 30, 24), 0.03, 0.85)
    t.f0[:] = 60
    for y in range(0, N, 6):
        area(t, 0, y, 6, y + 3, (200, 190, 160))
        area(t, N - 6, y, N, y + 3, (200, 190, 160))
    t.alpha[:] = 255
    return t


# ---------------------------------------------------------------- registering the textures

def textures():
    T = {}
    T["tr_mq_metal"] = lambda: sheet_metal(9601)
    T["tr_soffit"] = lambda: soffit(9602, False)
    T["tr_soffit_on"] = lambda: soffit(9603, True)
    for key in ("a", "a_on", "b", "c"):
        for i in range(30):
            T[f"tr_mq_{key}_{i}"] = lambda key=key, i=i: board(key, i)
    for i in range(4):
        T[f"tr_mq_side_{i}"] = lambda i=i: side(i, False)
        T[f"tr_mq_side_{i}_on"] = lambda i=i: side(i, True)
    T["tr_bulb"] = lambda: bulb(9604, False)
    T["tr_bulb_on"] = lambda: bulb(9605, True)
    T["tr_bulb_dead"] = lambda: bulb(9606, False, dead=True)
    for i in range(8):
        T[f"tr_blade_t{i}"] = lambda i=i: blade(i, False)
        T[f"tr_blade_t{i}_on"] = lambda i=i: blade(i, True)
    T["tr_tc"] = lambda: terracotta(9680)
    for k, wear in enumerate(("b", "chip", "crack", "rust", "soot")):
        T[f"tr_tc_{wear}"] = lambda k=k, wear=wear: terracotta(9690 + k, wear=wear)
    T["tr_tc_panel"] = lambda: terracotta(9681, "panel")
    T["tr_tc_cornice"] = lambda: terracotta(9682, "cornice")
    T["tr_tc_pilaster"] = lambda: terracotta(9683, "pilaster")
    for n in range(len(FILMS)):
        for part in ("lo", "hi"):
            T[f"tr_poster_{n}_{part}"] = lambda n=n, part=part: poster(n, part)
    T["tr_booth_glass"] = lambda: booth_glass(9720)
    T["tr_booth_base"] = lambda: booth_base(9721)
    T["tr_terrazzo"] = lambda: terrazzo(9722)
    for i in range(5):
        T[f"tr_terrazzo_name_{i}"] = lambda i=i: terrazzo_name(i)
    T["tr_carpet"] = lambda: carpet(9735)
    T["tr_carpet_b"] = lambda: carpet(9736, worn=True)
    T["tr_plaster"] = lambda: plaster(9737)
    T["tr_plaster_stain"] = lambda: plaster(9738, "stain")
    T["tr_plaster_peel"] = lambda: plaster(9739, "peel")
    T["tr_upper"] = lambda: upper(9733)
    T["tr_upper_stain"] = lambda: upper(9734, True)
    T["tr_wainscot"] = lambda: wainscot(9738)
    T["tr_frieze"] = lambda: frieze(9739)
    T["tr_candy_glass"] = lambda: candy(False)
    T["tr_candy_glass_empty"] = lambda: candy(True)
    T["tr_popcorn_glass"] = lambda: popcorn_glass(9746)
    T["tr_cart_red"] = lambda: cart_red(9747)
    T["tr_soda_front"] = lambda: soda_front(9748)
    T["tr_cups"] = lambda: cups(9749)
    T["tr_podium"] = lambda: podium(9752)
    T["tr_frosted"] = lambda: frosted(9753, False)
    T["tr_frosted_on"] = lambda: frosted(9754, True)
    T["tr_crystal"] = lambda: crystal(9755, False)
    T["tr_crystal_on"] = lambda: crystal(9756, True)
    T["tr_brass"] = lambda: brass(9757)
    T["tr_velvet"] = lambda: velvet(9758)
    T["tr_velvet_torn"] = lambda: velvet(9759, torn=True)
    T["tr_coat"] = lambda: velvet(9762, (60, 70, 86))
    for n in range(3):
        T[f"tr_lobbycard_{n}"] = lambda n=n: lobbycard(n)
    for k, text in (("men", "MEN"), ("ladies", "LADIES"), ("booth", "PROJECTION|STAFF ONLY"), ("food", "NO OUTSIDE|FOOD OR DRINK")):
        T[f"tr_sign_{k}"] = lambda k=k, text=text: plaque(9763 + len(k), text)
    T["tr_seat_end"] = lambda: seat_end(9764)
    T["tr_coffer"] = lambda: coffer(9765)
    T["tr_gilt"] = lambda: gilt(9766)
    T["tr_gilt_col"] = lambda: gilt(9767, "col")
    for i in range(22):
        T[f"tr_prosc_{i}"] = lambda i=i: prosc(i)
    T["tr_curtain"] = lambda: curtain(9790)
    T["tr_curtain_hem"] = lambda: curtain(9791, "hem")
    T["tr_curtain_torn"] = lambda: curtain(9792, "torn")
    T["tr_valance"] = lambda: curtain(9793, "valance")
    for i in range(36):
        T[f"tr_screen_{i}"] = lambda i=i: screen(i, False)
        T[f"tr_screen_lit_{i}"] = lambda i=i: screen(i, True)
    T["tr_boards"] = lambda: boards(9794)
    T["tr_velour"] = lambda: velour(9799)
    T["tr_apron"] = lambda: apron(9795, False)
    T["tr_apron_on"] = lambda: apron(9796, True)
    T["tr_hammered"] = lambda: hammered(9797)
    T["tr_lamphouse"] = lambda: hammered(9798, (34, 34, 36))
    T["tr_reel"] = lambda: reel_face(9799)
    T["tr_can"] = lambda: film_can(9800)
    T["tr_film"] = lambda: film_strip(9801)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    black, chrome, wood, glass = "home_plastic_black", "home_chrome", "home_wood_dark", "sc_glass"
    metal, brass, gilt = "tr_mq_metal", "tr_brass", "tr_gilt"

    def cube(front, side, up=None, down=None):
        return [[0, 0, 0, 16, 16, 16, {"north": front, "side": side, "up": up or side, "down": down or side}]]

    # ---- the marquee
    for key in ("a", "a_on", "b", "c"):
        lit = key.endswith("_on")
        for i in range(30):
            add(f"tr_mq_{key}_{i}", cube(f"tr_mq_{key}_{i}", metal, down="tr_soffit_on" if lit else "tr_soffit"), "Marquee", "METAL",
                **({"light": 12} if lit and i % 3 == 1 else {}))
    add("tr_mq_box", cube(metal, metal, down="tr_soffit"), "Marquee", "METAL")
    add("tr_mq_box_on", cube(metal, metal, down="tr_soffit_on"), "Marquee", "METAL", light=11)
    # the lit soffit between the ones that carry the light (it only glows)
    add("tr_mq_box_glow", cube(metal, metal, down="tr_soffit_on"), "Marquee", "METAL")
    for i in range(4):
        add(f"tr_mq_side_{i}", cube(f"tr_mq_side_{i}", metal, down="tr_soffit"), "Marquee", "METAL")
        add(f"tr_mq_side_{i}_on", cube(f"tr_mq_side_{i}_on", metal, down="tr_soffit_on"), "Marquee", "METAL", **({"light": 12} if i % 2 == 0 else {}))
    for suffix, b in (("", "tr_bulb"), ("_on", "tr_bulb_on")):
        bulbs = [[6, 0, 0, 10, 2, 16, metal]] + [[6.75, 2, z + 0.75, 9.25, 4.5, z + 3.25, b] for z in (0, 4, 8, 12)]
        add(f"tr_bulbs{suffix}", bulbs, "Marquee Bulbs", "GLASS", rotate="axis", decor=True)

    # ---- the blade: the cap, the six letters, the foot (its faces east and west, its back to the front)
    for suffix in ("", "_on"):
        for i in range(8):
            face = {"east": f"tr_blade_t{i}{suffix}", "west": f"tr_blade_t{i}{suffix}", "north": metal, "south": metal, "up": metal, "down": metal}
            if i == 0:
                boxes = [[6.5, 0, 0, 9.5, 12, 16, face], [7, 12, 6, 9, 16, 10, gilt]]
            elif i == 7:
                boxes = [[6.5, 6, 0, 9.5, 16, 16, face], [7, 3, 6, 9, 6, 10, gilt], [7, 8, 14, 9, 10, 16, metal]]
            else:
                boxes = [[6.5, 0, 0, 9.5, 16, 16, face]]
            name = "tr_blade_cap" if i == 0 else "tr_blade_foot" if i == 7 else f"tr_blade_{i - 1}"
            if i == 7 and suffix:
                continue
            # the neon throws its red light from two of the letters (the rest glow without lighting)
            add(name + suffix, boxes, "Sign", "METAL", **({"light": 12} if suffix and i in (2, 5) else {}))

    # ---- the front
    for name in ("tr_tc", "tr_tc_b", "tr_tc_chip", "tr_tc_crack", "tr_tc_rust", "tr_tc_soot", "tr_tc_panel", "tr_tc_cornice", "tr_tc_pilaster"):
        add(name, None, "Terracotta", "STONE", rotate=None, tex={"all": name})
    add("tr_crest", [[2, 0, 6, 14, 8, 10, {"north": "tr_tc_panel", "side": "tr_tc"}], [4, 8, 6, 12, 12, 10, "tr_tc"], [6, 12, 6, 10, 16, 10, "tr_tc"],
                     [7, 15, 7, 9, 16, 9, gilt]], "Terracotta", "STONE")
    add("tr_crest_side", [[0, 0, 6, 16, 4, 10, "tr_tc"], [0, 4, 7, 16, 5, 9, gilt]], "Terracotta", "STONE")
    for n in range(len(FILMS)):
        add(f"tr_poster_{n}_lo", [[1, 1, 13, 15, 16, 16, {"north": f"tr_poster_{n}_lo", "side": brass, "up": brass, "down": brass}]], "Poster Case", "METAL",
            decor=True)
        add(f"tr_poster_{n}_hi", [[1, 0, 13, 15, 15, 16, {"north": f"tr_poster_{n}_hi", "side": brass, "up": brass, "down": brass}]], "Poster Case", "METAL",
            decor=True)
    add("tr_booth_lo", [[2, 0, 2, 14, 11, 14, {"north": "tr_booth_base", "side": "tr_booth_base", "up": wood}], [1.5, 11, 1.5, 14.5, 12, 14.5, brass],
                        [2.5, 12, 2.5, 13.5, 16, 13.5, "tr_booth_glass"], [5, 11, 0.5, 11, 11.6, 2, brass]], "Ticket Booth", "WOOD", loot="till")
    add("tr_booth_hi", [[2.5, 0, 2.5, 13.5, 12, 13.5, "tr_booth_glass"], [1, 12, 1, 15, 13.5, 15, brass], [3, 13.5, 3, 13, 16, 13, gilt]],
        "Ticket Booth", "WOOD")
    add("tr_terrazzo", None, "Terrazzo", "STONE", rotate=None, tex={"all": "tr_terrazzo"})
    for i in range(5):
        add(f"tr_terrazzo_name_{i}", None, "Terrazzo", "STONE", rotate=None, tex={"up": f"tr_terrazzo_name_{i}", "side": "tr_terrazzo", "down": "tr_terrazzo"})

    # ---- the lobby
    for name in ("tr_carpet", "tr_carpet_b"):
        add(name, None, "Carpet", "WOOL", rotate=None, tex={"up": name, "side": name, "down": "floor_concrete"})
    for name in ("tr_plaster", "tr_plaster_stain", "tr_plaster_peel", "tr_upper", "tr_upper_stain", "tr_wainscot", "tr_frieze"):
        add(name, None, "Wall", "STONE", rotate=None, tex={"all": name})
    for name, tex in (("tr_candy", "tr_candy_glass"), ("tr_candy_empty", "tr_candy_glass_empty")):
        add(name, [[1, 0, 2, 15, 4, 14, {"north": "tr_wainscot", "side": "tr_wainscot", "up": wood}], [1, 4, 2, 15, 15, 14, {"north": tex, "side": tex, "up": glass, "down": wood}],
                   [1, 15, 2, 15, 15.6, 14, brass], [0.5, 0, 1.5, 15.5, 1, 14.5, brass]], "Candy Counter", "GLASS", loot="theatre_candy")
    back = [[0, 0, 6, 16, 14, 16, {"north": "tr_wainscot", "side": "tr_wainscot", "up": wood}]]
    add("tr_backbar", back + [[2, 14, 9, 4, 16, 11, "tr_cups"], [5, 14, 9, 7, 16, 11, "tr_cups"], [10, 14, 8, 14, 15, 12, chrome]], "Back Counter", "WOOD",
        loot="theatre_candy")
    add("tr_soda", [[0, 0, 6, 16, 9, 16, {"north": "tr_wainscot", "side": "tr_wainscot", "up": wood}],
                    [3, 9, 8, 13, 16, 15, {"north": "tr_soda_front", "side": chrome, "up": chrome}], [4, 9, 6.5, 12, 9.6, 8, chrome]], "Soda Fountain", "METAL",
        loot="drinks")
    wheels = [[x, 0, z, x + 1.5, 3, z + 2, black] for x in (2.5, 12) for z in (3.5, 10.5)]
    add("tr_popcorn_lo", [[2, 3, 3, 14, 16, 13, {"north": "tr_cart_red", "side": "tr_cart_red", "up": chrome}], [1.5, 15, 2.5, 14.5, 16, 13.5, chrome]] + wheels,
        "Popcorn Machine", "METAL")
    pop = {"north": "tr_popcorn_glass", "side": "tr_popcorn_glass", "up": "tr_cart_red", "down": chrome}
    add("tr_popcorn_hi", [[2, 0, 3, 14, 13, 13, pop], [2, 13, 3, 14, 16, 13, pop], [1.5, 15.5, 2.5, 14.5, 16, 13.5, chrome]], "Popcorn Machine", "METAL")
    add("tr_podium", [[4, 0, 5, 12, 13, 11, {"north": "tr_podium", "side": "tr_podium", "up": wood}], [3.5, 13, 4, 12.5, 14, 12, wood],
                      [9, 14, 6, 11.5, 16, 9, brass]], "Ticket Stand", "WOOD")
    for suffix in ("", "_on"):
        fr = f"tr_frosted{suffix}"
        add(f"tr_sconce{suffix}", [[6, 5, 15, 10, 12, 16, brass], [4, 8, 12.5, 12, 13, 15.5, {"north": fr, "side": fr, "up": fr, "down": brass}],
                                   [7.5, 6, 14, 8.5, 8, 15, brass]], "Sconce", "GLASS", decor=True, **({"light": 11} if suffix else {}))
        cr = f"tr_crystal{suffix}"
        add(f"tr_chandelier{suffix}", [[7.5, 10, 7.5, 8.5, 16, 8.5, brass], [4, 5, 4, 12, 8, 12, cr], [5, 8, 5, 11, 10, 11, cr], [6, 2, 6, 10, 5, 10, cr],
                                       [7, 0, 7, 9, 2, 9, cr], [3.5, 7.5, 3.5, 12.5, 8, 12.5, brass]], "Chandelier", "GLASS", rotate=None, decor=True,
            **({"light": 14} if suffix else {}))
    for n in range(3):
        add(f"tr_lobbycard_{n}", [[3, 4, 15, 13, 12, 16, f"tr_lobbycard_{n}"]], "Lobby Card", "WOOD", decor=True)
    add("tr_stair", [[0, 0, 0, 16, 8, 16, {"up": "tr_carpet", "side": "tr_wainscot", "down": "tr_wainscot"}],
                     [0, 8, 0, 16, 16, 8, {"up": "tr_carpet", "side": "tr_wainscot", "down": "tr_wainscot"}],
                     [0, 7, 15.4, 16, 8, 16, brass], [0, 15, 7.4, 16, 16, 8, brass]], "Stair", "WOOL")
    for k in ("men", "ladies", "booth", "food"):
        add(f"tr_sign_{k}", [[2, 4, 15.4, 14, 12, 16, f"tr_sign_{k}"]], "Sign", "METAL", decor=True)

    # ---- the seats: two to a block on iron standards (the sitter faces north in the base)
    vel, iron = "tr_velvet", "tr_seat_end"

    def chair(x0, state, cloth=vel):
        x1 = x0 + 6.5
        if state == "up":
            return [[x0, 6, 8, x1, 12, 10, cloth], [x0, 6, 10, x1, 15, 12, cloth], [x0, 6, 12, x1, 15, 12.6, wood]]
        if state == "broken":
            return [[x0, 5, 3, x1, 7, 10, cloth], [x0 + 0.5, 0, 0.5, x1 - 0.5, 1.5, 7.5, cloth]]
        return [[x0, 5, 3, x1, 7, 10, cloth], [x0, 6, 10, x1, 15, 12, cloth], [x0, 6, 12, x1, 15, 12.6, wood]]

    frame = [[0, 0, 6, 1, 9, 13, iron], [0, 9, 5.5, 1.4, 10, 13, wood], [7.5, 0, 6, 8.5, 9, 13, iron], [7.3, 9, 5.5, 8.7, 10, 13, wood]]
    seats = {
        "tr_seat": chair(1, "down") + chair(8.5, "down"),
        "tr_seat_up": chair(1, "up") + chair(8.5, "up"),
        "tr_seat_mix": chair(1, "down") + chair(8.5, "up"),
        "tr_seat_broken": chair(1, "broken") + chair(8.5, "down", "tr_velvet_torn"),
        "tr_seat_coat": chair(1, "down") + [[0.8, 9, 9.5, 7.8, 15.5, 13, "tr_coat"], [1.5, 7, 4, 7, 7.6, 9, "tr_coat"]] + chair(8.5, "up"),
        "tr_seat_pop": chair(1, "down") + [[3, 7, 5, 5.5, 10, 7.5, "tr_cart_red"]] + chair(8.5, "down"),
    }
    for name, parts in seats.items():
        add(name, frame + parts, "Theatre Seat", "WOOL")
    for suffix, b in (("", "tr_bulb"), ("_on", "tr_bulb_on")):
        light = {"light": 5} if suffix else {}
        add(f"tr_seat_aisle{suffix}", frame + chair(1, "down") + chair(8.5, "down") + [[15, 0, 6, 16, 9, 13, iron], [14.6, 9, 5.5, 16, 10, 13, wood],
                                                                                      [15.2, 1, 4.6, 16, 2.6, 6, b]], "Theatre Seat", "WOOL", **light)
        add(f"tr_seat_aisle_l{suffix}", frame + chair(1, "down") + chair(8.5, "up") + [[0, 1, 4.6, 0.8, 2.6, 6, b]], "Theatre Seat", "WOOL", **light)

    # ---- the house: the ceiling, the proscenium, the curtains, the screen, the stage
    add("tr_ceiling", None, "Plaster Ceiling", "STONE", rotate=None, tex={"all": "tr_coffer"})
    add("tr_plaster_chunk", [[2, 0, 3, 8, 2, 8, "tr_coffer"], [9, 0, 7, 13, 1.5, 12, "tr_coffer"], [5, 0, 10, 8, 1, 13, "tr_coffer"]], "Plaster", "STONE",
        decor=True)
    add("tr_prosc_col", cube("tr_gilt_col", "tr_plaster"), "Proscenium", "STONE")
    for i in range(22):
        add(f"tr_prosc_{i}", cube(f"tr_prosc_{i}", gilt), "Proscenium", "STONE")

    def pleats(tex, x0=0, x1=16):
        step = (x1 - x0) / 4
        return [[round(x0 + k * step, 3), 0, 11 + (k % 2) * 1.5, round(x0 + (k + 1) * step, 3), 16, 13.5 + (k % 2) * 1.5,
                 {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}] for k in range(4)]
    add("tr_curtain", pleats("tr_curtain"), "Curtain", "WOOL", decor=True)
    add("tr_curtain_hem", pleats("tr_curtain_hem"), "Curtain", "WOOL", decor=True)
    add("tr_curtain_torn", pleats("tr_curtain_torn"), "Curtain", "WOOL", decor=True)
    add("tr_curtain_tie", pleats("tr_curtain", 4, 12) + [[3.5, 6, 10, 12.5, 8, 15.8, gilt]], "Curtain", "WOOL", decor=True)
    add("tr_valance", [[0, 0, 11, 16, 16, 14, {"north": "tr_valance", "south": "tr_valance", "side": "tr_valance", "up": "tr_curtain", "down": "tr_curtain"}]],
        "Curtain", "WOOL", decor=True)
    for i in range(36):
        add(f"tr_screen_{i}", [[0, 0, 15, 16, 16, 16, {"north": f"tr_screen_{i}", "side": black}]], "Screen", "WOOL", decor=True)
        add(f"tr_screen_lit_{i}", [[0, 0, 15, 16, 16, 16, {"north": f"tr_screen_lit_{i}", "side": black}]], "Screen", "WOOL", decor=True, light=8)
    add("tr_stage", None, "Stage", "WOOD", rotate=None, tex={"all": "tr_boards"})
    add("tr_masking", None, "Masking", "WOOL", rotate=None, tex={"all": "tr_velour"})
    # the upstairs floors over the lobby: boards (the booth) or carpet (the office) on top, the coffers under
    add("tr_loft", None, "Floor", "WOOD", rotate=None, tex={"up": "floor_boards", "down": "tr_coffer", "side": "tr_plaster"})
    add("tr_loft_carpet", None, "Floor", "WOOL", rotate=None, tex={"up": "tr_carpet_b", "down": "tr_coffer", "side": "tr_plaster"})
    add("tr_apron", cube("tr_apron", wood, up="tr_boards"), "Stage", "WOOD")
    add("tr_apron_on", cube("tr_apron_on", wood, up="tr_boards"), "Stage", "WOOD", light=7)

    # ---- the projection booth
    ham, lamp = "tr_hammered", "tr_lamphouse"
    add("tr_projector_lo", [[3, 0, 3, 13, 1, 13, ham], [5, 1, 5, 11, 8, 11, ham], [4, 8, 2, 10, 15, 10, ham], [9, 8, 7, 15, 16, 15, lamp],
                            [5.5, 10, 0, 7.5, 12, 2, chrome], [10, 14, 9, 14, 16, 13, lamp]], "Projector", "METAL")
    reel = {"east": "tr_reel", "west": "tr_reel", "north": ham, "south": ham, "up": ham, "down": ham}
    add("tr_projector_hi", [[10.5, 0, 9.5, 13.5, 9, 12.5, lamp], [10, 9, 9, 14, 10, 13, lamp], [4.5, 0, 6, 7.5, 3, 12, ham], [5.5, 2, 3, 6.5, 14, 15, reel]],
        "Projector", "METAL")
    wall = "tr_plaster"
    add("tr_port", [[0, 0, 0, 16, 7, 16, wall], [0, 11, 0, 16, 16, 16, wall], [0, 7, 0, 5, 11, 16, wall], [11, 7, 0, 16, 11, 16, wall],
                    [5, 7, 7.5, 11, 11, 8.5, glass]], "Projection Port", "STONE")
    add("tr_rewind", [[0, 0, 4, 16, 10, 14, {"north": ham, "side": ham, "up": wood}], [2, 10, 8, 3, 12, 10, chrome], [13, 10, 8, 14, 12, 10, chrome],
                      [1.5, 10, 4, 2.5, 16, 14, reel], [13.5, 10, 4, 14.5, 16, 14, reel]], "Rewind Bench", "METAL")
    can = {"side": "tr_can", "up": "tr_can", "down": "tr_can"}
    add("tr_cans", [[3, 0, 3, 13, 2, 13, can], [3.5, 2, 2.5, 13.5, 4, 12.5, can], [2.5, 4, 3.5, 12.5, 6, 13.5, can], [3, 6, 3, 13, 8, 13, can]],
        "Film Cans", "METAL")
    add("tr_reel", [[2, 0, 2, 14, 1.2, 14, {"up": "tr_reel", "side": "tr_can", "down": "tr_can"}]], "Film Reel", "METAL", decor=True)
    film = "tr_film"
    add("tr_film", [[1, 0, 2, 12, 0.3, 3, film], [4, 0, 5, 15, 0.3, 6, film], [3, 0, 9, 4, 0.3, 15, film], [6, 0, 7, 6.3, 5, 12, film],
                    [9, 0, 10, 14, 0.3, 11, film], [6, 4.7, 7, 10, 5, 12, film]], "Film", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("tr_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} theatre blocks in {path}")


if __name__ == "__main__":
    main()
