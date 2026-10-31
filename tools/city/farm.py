"""The fm_ kit: everything a farm is built from, so a barn yard holds up next to the silos and the
houses (Josiah, 2026-10-29: the small things get the same care as the landmarks). Drawn from the
farm photos in refs/farm (barn-interior, dairy-barn, machine-shed, silos).

- bales: fm_bale / fm_bale_up (a round bale on its side or on end, its end showing the roll),
  fm_wrap / fm_wrap_up (wrapped in white plastic), fm_bales (a block of small square bales stacked
  two by two), fm_bales_low (one layer), fm_bale_one (a single bale dropped askew);
- fences: fm_rail / fm_rail_post / fm_rail_broken (post and rail in grey timber), the _w set in
  white paddock boards, fm_tpost (a steel T-post through a run of barbed wire), fm_post (a corner
  post on its own), fm_gate (a galvanised tube gate, hung as a door);
- the barn: fm_fieldstone / fm_fieldstone_moss and fm_cinder footings, fm_trim (white trim);
  the gambrel roof in the house kit's materials, fm_gam_{kind}_{piece}: steep_lo + steep_hi (the
  lower slope, two blocks rise a column), low / high (the upper slope, half a block a column, from
  the block's foot or its middle), low_crest / high_crest (with half the ridge cap), ridge_low /
  ridge_high (a middle column); fm_cupola_{kind} / _high (the ventilator on the ridge, its
  louvres, roof and weathervane, centred on the ridge line), fm_hay_track (the hay fork's track
  under the hood);
- inside: fm_stall_boards / fm_stall_bars (a horse stall's partition, boards below and bars above),
  fm_stall_door (its sliding front, hung as a door), fm_loop (a cattle stall's pipe divider),
  fm_headlock (the feed fence), fm_manger, fm_water_trough, fm_feed_bin (searchable),
  fm_ladder / fm_ladder_broken (climbable), fm_saddle_rack, fm_tack_trunk (searchable), fm_lantern / fm_lantern_lit (hanging), fm_chain,
  fm_bedroll, fm_grindstone, fm_anvil, fm_nest_boxes, fm_roost, fm_waterer, fm_feathers, fm_feeder;
- pens (the animals' furniture, tagged zomboidcraft:animal/*): fm_panel / fm_panel_post (galvanised
  corral panels), fm_stock_tank and fm_ring_feeder (round, a quarter a block, four round a corner),
  fm_butcher_hook (the gambrel on its tackle, hung from a beam), fm_meat_rail (a smokehouse rail of hams);
- the yard: fm_hive / fm_hive_tipped, fm_scarecrow_lo / _hi, fm_firewood (split wood stacked
  between stakes), fm_chop_block (with the axe in it), fm_well (the well's roof, windlass and
  bucket over its round stone curb), fm_outhouse_lo / _hi, fm_girt (a windmill tower's X brace),
  fm_pump (the windmill's pump stand), fm_pump_rod, fm_pipe, fm_fuel_stand / fm_fuel_tank /
  fm_fuel_tank_end (the farm diesel tank), fm_spout (a grain spout), fm_corn (ear corn in a crib),
  fm_pumpkin / fm_pumpkin_carved / fm_pumpkin_lit, fm_char_logs (a burnt-out pyre), fm_bones,
  fm_pole (a pole barn's post).

    python3 tools/city/farm.py          rewrites the fm_ entries in blocks.json

photo_textures.py draws the textures (farm.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
STRAW = (200, 172, 104)
GREY_STRAW = (150, 138, 108)


# ---------------------------------------------------------------- textures

def polar():
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = xx - 31.5, yy - 31.5
    return np.hypot(dx, dy), np.arctan2(dy, dx), xx, yy


def fibres(t, n, along, col, length=(6, 16), strength=0.5):
    """Short straws: n strokes along u (along=0) or v (along=1)."""
    for _ in range(n):
        x, y = t.rng.integers(0, N, 2)
        ln = int(t.rng.integers(*length))
        shade = np.array(col, np.float32) * float(t.rng.uniform(0.75, 1.2))
        for i in range(ln):
            px, py = (x + (i if along == 0 else 0)) % N, (y + (i if along == 1 else 0)) % N
            t.c[py, px] = t.c[py, px] * (1 - strength) + shade * strength
            t.h[py, px] += 0.08


def hay_end(seed, weathered=0.5):
    """The end of a round bale: the roll's spiral, straw ends bristling, the outer turns gone grey."""
    t = F.plain(seed, STRAW, 0.08, 0.1)
    r, a, xx, yy = polar()
    phase = (r / 3.6 - a / (2 * np.pi)) % 1.0
    ring = np.abs(phase - 0.5) * 2
    t.c *= (0.72 + 0.34 * ring)[..., None]
    t.h += ring * 0.15
    t.speckle(0.25, (226, 204, 140), 0.5, 0.1)
    t.speckle(0.12, (120, 96, 54), 0.5, -0.1)
    t.mix(np.clip((r - 14) / 18, 0, 1) * weathered, GREY_STRAW, 0.8)
    t.mix(np.clip((r - 28) / 4, 0, 1), (110, 100, 80), 0.6)
    disc(t, 31.5, 31.5, 3, col=(150, 120, 70), dh=-0.2)
    t.grime(0.15, (80, 72, 54))
    t.poro[:] = 120
    return t


def hay_round(seed, along, weathered=0.5):
    """A round bale's curve: straws laid round the roll, the net wrap over them, rained grey."""
    t = F.plain(seed, STRAW, 0.06, 0.1)
    fibres(t, 900, along, (214, 186, 120), strength=0.6)
    fibres(t, 300, along, (130, 106, 64), strength=0.4)
    t.mix(np.clip(P.fbm(t.rng) * 0.9 + 0.4, 0, 1) * weathered, GREY_STRAW, 0.85)
    # the net: thin pale strands on a diamond
    yy, xx = np.mgrid[0:N, 0:N]
    net = ((xx + yy) % 12 == 0) | ((xx - yy) % 12 == 0)
    t.c[net] = t.c[net] * 0.65 + np.array((226, 222, 210)) * 0.35
    t.h[net] += 0.1
    t.streaks(0.3, 0.8)
    t.poro[:] = 120
    return t


def sq_side(seed, top=False):
    """A small square bale's side: the flakes the baler packed, straws along it, two twines."""
    t = F.plain(seed, (196, 170, 100), 0.06, 0.1)
    fibres(t, 800, 0 if not top else 1, (214, 190, 124), strength=0.55)
    fibres(t, 200, 0 if not top else 1, (128, 104, 62), strength=0.4)
    for i in range(0, N, 9):
        k = i + int(t.rng.integers(-1, 2))
        if top:
            area(t, 0, k, N, k + 1, mul=0.7, dh=-0.2)
        else:
            area(t, k, 0, k + 1, N, mul=0.7, dh=-0.2)
    twine = (214, 96, 40) if seed % 2 else (60, 110, 170)
    for k in (18, 45):
        if top:
            area(t, k, 0, k + 1, N, col=twine, dh=0.15)
        else:
            area(t, 0, k, N, k + 1, col=twine, dh=0.15)
    t.grime(0.15, (110, 96, 60))
    t.poro[:] = 120
    return t


def sq_end(seed):
    """A square bale's end: cut straw ends, dense, the twines looped over."""
    t = F.plain(seed, (184, 158, 92), 0.08, 0.1)
    t.speckle(0.35, (222, 198, 132), 0.6, 0.1)
    t.speckle(0.2, (110, 88, 50), 0.5, -0.15)
    for k in (18, 45):
        area(t, k, 0, k + 1, N, col=(214, 96, 40), dh=0.15)
    t.frame(1, (140, 118, 70), 0.6, 0.1)
    t.poro[:] = 120
    return t


def wrap_end(seed):
    """A wrapped bale's end: white film pulled into folds toward the middle, dirt on its foot."""
    t = F.plain(seed, (230, 230, 224), 0.03, 0.75)
    r, a, xx, yy = polar()
    folds = np.sin(a * 9 + r * 0.05) * np.clip(1 - r / 34, 0, 1)
    t.c *= (1 + folds * 0.08)[..., None]
    t.h += folds * 0.2
    disc(t, 31.5, 31.5, 6, mul=0.92, dh=0.1)
    t.mix(np.clip((yy - 40) / 24, 0, 1) * 0.5, (110, 100, 80), 0.6)
    t.grime(0.15, (90, 90, 80))
    return t


def fieldstone(seed, moss=False):
    """A barn's fieldstone footing: round stones of every colour bedded in crumbling lime mortar."""
    base = F.plain(seed, (124, 120, 112), 0.06, 0.25)
    t = base
    rng = np.random.default_rng(seed)
    pts = rng.uniform(0, N, (26, 2))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    best = np.full((N, N), 1e9, np.float32)
    second = np.full((N, N), 1e9, np.float32)
    owner = np.zeros((N, N), int)
    for i, (px, py) in enumerate(pts):
        for ox in (-N, 0, N):
            for oy in (-N, 0, N):
                d = np.hypot(xx - px - ox, (yy - py - oy) * 1.25)
                nearer = d < best
                second = np.where(nearer, best, np.minimum(second, d))
                owner = np.where(nearer, i, owner)
                best = np.where(nearer, d, best)
    edge = second - best
    tints = rng.uniform(0.7, 1.25, (len(pts), 1)) * np.ones((1, 3))
    warm = rng.random(len(pts)) < 0.3
    tints[warm] *= np.array((1.12, 1.0, 0.86))
    cool = rng.random(len(pts)) < 0.2
    tints[cool] *= np.array((0.94, 0.98, 1.04))
    t.c *= tints[owner]
    mortar = edge < 2.6
    t.c[mortar] = np.array((150, 144, 128)) * (0.85 + 0.15 * rng.random((int(mortar.sum()), 1)))
    t.h = np.clip(0.45 + np.minimum(edge, 9) / 9 * 0.5, 0, 1)
    t.h[mortar] = 0.3
    t.speckle(0.05, (70, 70, 66), 0.4)
    if moss:
        m = np.clip(P.fbm(t.rng) * 1.4 + 0.1, 0, 1) * np.clip((yy - 20) / 44, 0, 1)
        t.mix(m, (70, 92, 46), 0.7)
    t.grime(0.25, (60, 56, 48))
    return t


def white_wood(seed):
    """White paint on boards: the grain through it, chipped to grey wood at the edges, green mould low down."""
    t = F.plain(seed, (226, 224, 214), 0.03, 0.4)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    g = P.tnoise(t.rng, 4) * 5
    grain = np.sin(yy * 1.1 + g) * 0.03
    t.c *= (1 + grain)[..., None]
    t.h += grain
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.3)
    chips = (P.fbm(t.rng) > 0.55)
    t.c[chips] = (128, 120, 104)
    t.h[chips] -= 0.1
    t.mix(np.clip((yy - 44) / 20, 0, 1) * 0.4, (110, 120, 80), 0.5)
    t.streaks(0.3, 0.85)
    t.grime(0.2, (90, 88, 76))
    return t


def gate(seed, top):
    """A galvanised tube gate (cut out round the tubes): five rails, the end stiles, the diagonal
    brace; the top half holds only the top rail."""
    t = F.plain(seed, (168, 172, 170), 0.04, 0.55)
    t.f0[:] = 200
    t.alpha[:] = 0
    tubes = [N - 6] if top else [5, 17, 29, 41, 53]
    for y in tubes:
        area(t, 0, y - 2, N, y + 2, alpha=255)
        area(t, 0, y - 2, N, y - 1, mul=1.25)
        area(t, 0, y + 1, N, y + 2, mul=0.6)
    for x in (1, N - 3):
        y0 = N - 8 if top else 0
        area(t, x, y0, x + 3, N, alpha=255)
        area(t, x, y0, x + 1, N, mul=1.2)
    if not top:
        for i in range(N):
            x = int(i * 0.9) + 3
            if x < N - 3:
                area(t, x, N - 1 - i, x + 2, N - i, alpha=255)
    t.speckle(0.02, (130, 80, 50), 0.5)
    t.grime(0.15, (90, 90, 80))
    return t


def louvre(seed):
    """A cupola's louvres: white slats sloping down and out, the dark loft behind."""
    t = white_wood(seed)
    for y in range(4, N - 4, 6):
        area(t, 6, y, N - 6, y + 2, mul=0.25, dh=-0.4)
        area(t, 6, y + 2, N - 6, y + 3, mul=1.1, dh=0.2)
    t.frame(6, (214, 212, 202), 0.9, 0.4)
    return t


def hive_tex(seed, front=False):
    """A beehive box: white paint gone chalky, the hand-holds, propolis stains; the front has the
    entrance at its foot."""
    t = F.plain(seed, (222, 218, 202), 0.04, 0.3)
    area(t, 0, 31, N, 33, mul=0.6, dh=-0.3)
    if front:
        area(t, 10, 56, 54, 60, col=(30, 26, 20), dh=-0.6)
        area(t, 10, 60, 54, 62, col=(140, 120, 80))
    else:
        for y in (12, 44):
            area(t, 22, y, 42, y + 5, mul=0.5, dh=-0.4)
    t.speckle(0.02, (150, 110, 50), 0.6)
    t.streaks(0.4, 0.8)
    t.grime(0.25, (100, 96, 76))
    return t


def corn(seed):
    """Ear corn heaped in a crib: cobs every way, kernels in rows, husks, mouse-chewed."""
    t = F.plain(seed, (190, 140, 50), 0.05, 0.2)
    rng = t.rng
    for _ in range(46):
        x, y = rng.uniform(0, N, 2)
        ang = rng.uniform(0, np.pi)
        ln, w = rng.uniform(12, 20), rng.uniform(3.2, 4.4)
        col = np.array((222, 168, 52)) * rng.uniform(0.75, 1.1)
        for i in range(int(ln)):
            cx, cy = x + np.cos(ang) * i, y + np.sin(ang) * i
            k = (1 - abs(i - ln / 2) / (ln / 2)) ** 0.4
            disc(t, cx % N, cy % N, w * k, col=tuple(col * (0.9 + 0.2 * ((i % 2)))), h=0.6 + 0.3 * k)
    t.speckle(0.15, (120, 80, 30), 0.4, -0.1)
    t.speckle(0.05, (214, 200, 160), 0.6)
    t.grime(0.25, (70, 60, 40))
    return t


def mesh(seed):
    """Hardware cloth on a corn crib: galvanised wire on a 4 px grid, cut out between."""
    t = F.plain(seed, (150, 154, 150), 0.04, 0.5)
    t.f0[:] = 180
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N]
    m = (xx % 5 == 0) | (yy % 5 == 0)
    t.alpha[m] = 255
    t.speckle(0.03, (130, 80, 40), 0.6)
    return t


def flannel(seed):
    t = F.plain(seed, (150, 36, 30), 0.05, 0.1)
    for k in range(0, N, 16):
        area(t, k, 0, k + 6, N, mul=0.45)
        area(t, 0, k, N, k + 6, mul=0.45)
    t.grime(0.4, (70, 60, 40))
    t.poro[:] = 160
    return t


def denim(seed):
    t = F.plain(seed, (66, 82, 108), 0.07, 0.1)
    for y in range(0, N, 2):
        area(t, 0, y, N, y + 1, mul=0.9)
    t.grime(0.45, (80, 70, 50))
    t.poro[:] = 160
    return t


def water(seed):
    """Stock-tank water: green-brown, scummed at the edge, leaves floating."""
    t = F.plain(seed, (46, 56, 40), 0.08, 0.95)
    t.mix(np.clip(P.fbm(t.rng) * 1.2, 0, 1) * 0.5, (90, 100, 50), 0.6)
    t.speckle(0.01, (120, 90, 40), 0.8)
    t.h[:] = 0.5
    t.f0[:] = 20
    return t


def feed(seed):
    """Cracked corn and pellets in a trough or bin."""
    t = F.plain(seed, (186, 150, 70), 0.08, 0.15)
    t.speckle(0.3, (220, 180, 80), 0.6, 0.15)
    t.speckle(0.2, (120, 100, 60), 0.6, -0.1)
    t.speckle(0.05, (100, 120, 70), 0.6)
    t.poro[:] = 100
    return t


def char(seed):
    """Charred wood: black, split in squares, grey ash in the cracks."""
    t = F.plain(seed, (34, 30, 28), 0.08, 0.15)
    for y in range(0, N, 8):
        area(t, 0, y + int(t.rng.integers(0, 3)), N, y + 1 + int(t.rng.integers(0, 3)), col=(110, 104, 96), dh=-0.3)
    for _ in range(40):
        x, y = t.rng.integers(0, N, 2)
        area(t, x, y, x + 1, y + 7, col=(100, 96, 90), dh=-0.3)
    t.speckle(0.05, (70, 60, 52), 0.6)
    t.poro[:] = 200
    return t


def split_end(seed):
    """Split firewood's ends: wedges of pale wood, the rings, the bark rim."""
    t = F.plain(seed, (170, 140, 98), 0.06, 0.15)
    for _ in range(18):
        x, y = t.rng.uniform(0, N, 2)
        r0 = t.rng.uniform(6, 10)
        disc(t, x, y, r0, col=(70, 56, 42))
        disc(t, x, y, r0 - 1.2, col=tuple(np.array((184, 152, 108)) * t.rng.uniform(0.8, 1.05)))
        for rr in np.arange(1.5, r0 - 1.5, 1.8):
            disc(t, x, y, rr, mul=0.88, ring=0.5)
    t.grime(0.2, (90, 76, 60))
    return t


def pumpkin_tex(seed, face=None):
    """A pumpkin's skin: ribs, the bloom, soft dark spots; carved, its face (lit or not)."""
    t = F.plain(seed, (210, 112, 30), 0.06, 0.4)
    for x in range(0, N, 10):
        area(t, x, 0, x + 2, N, mul=0.7, dh=-0.3)
    t.speckle(0.02, (90, 60, 30), 0.5)
    t.grime(0.15, (110, 80, 40))
    if face:
        lit = face == "lit"
        col = (255, 200, 90) if lit else (36, 22, 12)
        for cx in (20, 44):
            for i in range(8):
                area(t, cx - i // 2 - 1, 20 + i, cx + i // 2 + 1, 21 + i, col=col, dh=-0.5)
        for x in range(14, 50, 4):
            area(t, x, 40 + (x % 8 == 2) * 4, x + 3, 48, col=col, dh=-0.5)
        if lit:
            t.emit[(t.c[..., 0] > 240)] = 0.9
    return t


def stall_door(seed, top):
    """A horse stall's sliding door: tongue-and-groove boards in a steel frame below, round bars
    above (cut out between), a feed hatch in the bars."""
    t = F.mat("weathered_brown_planks", (124, 104, 82), 1.1, seed=seed)
    if top:
        t.alpha[:] = 0
        for x in range(4, N - 2, 7):
            area(t, x, 0, x + 2, N, col=(70, 72, 70), alpha=255, smooth=0.5)
        area(t, 0, 0, N, 3, col=(70, 72, 70), alpha=255)
    else:
        for x in range(0, N, 8):
            area(t, x, 0, x + 1, N, mul=0.6, dh=-0.3)
        area(t, 0, N - 4, N, N, col=(70, 72, 70), alpha=255)
        area(t, 0, 0, N, 3, col=(70, 72, 70))
        area(t, 46, 26, 52, 30, col=(40, 40, 40), dh=0.4)
    area(t, 0, 0, 3, N, col=(70, 72, 70), alpha=255)
    area(t, N - 3, 0, N, N, col=(70, 72, 70), alpha=255)
    return t


def dress_paint(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.45)
    t.f0[:] = 30
    t.streaks(0.35, 0.8)
    t.speckle(0.02, (110, 64, 36), 0.7, -0.05)
    t.grime(0.3, (60, 50, 40))
    return t


def ring_boards(seed):
    """The round pen's boards seen side on: three white boards, the gaps between cut out."""
    t = white_wood(seed)
    t.alpha[:] = 0
    for y0 in (4, 24, 44):
        area(t, 0, y0, N, y0 + 14, alpha=255)
        area(t, 0, y0, N, y0 + 1, mul=1.15)
        area(t, 0, y0 + 13, N, y0 + 14, mul=0.6)
    return t


def silage_plastic(seed):
    """The black sheet over a silage bunker: creased, dusty, split here and there."""
    t = F.plain(seed, (30, 32, 32), 0.06, 0.7)
    t.h += np.sin(np.mgrid[0:N, 0:N][1] * 0.3 + P.fbm(t.rng) * 6) * 0.1
    t.mix(np.clip(P.fbm(t.rng) * 1.2, 0, 1) * 0.4, (110, 104, 92), 0.6)
    return t


def hutch(seed):
    """A calf hutch's white polyethylene: moulded ribs, green mould low down, scuffs."""
    t = F.plain(seed, (228, 228, 222), 0.03, 0.6)
    for x in range(0, N, 8):
        area(t, x, 0, x + 2, N, mul=0.88, dh=0.2)
    yy = np.mgrid[0:N, 0:N][0]
    t.mix(np.clip((yy - 40) / 24, 0, 1) * 0.5, (110, 120, 80), 0.6)
    t.grime(0.2, (100, 96, 80))
    return t


def textures():
    return {
        "fm_hay_end": lambda: hay_end(53000),
        "fm_hay_end_grey": lambda: hay_end(53001, 0.9),
        "fm_hay_round_u": lambda: hay_round(53002, 0),
        "fm_hay_round_v": lambda: hay_round(53003, 1),
        "fm_sq_side": lambda: sq_side(53004),
        "fm_sq_top": lambda: sq_side(53005, top=True),
        "fm_sq_end": lambda: sq_end(53006),
        "fm_wrap_end": lambda: wrap_end(53007),
        "fm_fieldstone": lambda: fieldstone(53008),
        "fm_fieldstone_moss": lambda: fieldstone(53009, moss=True),
        "fm_cinder": lambda: F.mat("concrete_block_wall", (170, 166, 154), 1.0, seed=53010),
        "fm_white": lambda: white_wood(53011),
        "fm_gate_bottom": lambda: gate(53012, False),
        "fm_gate_top": lambda: gate(53013, True),
        "fm_louvre": lambda: louvre(53014),
        "fm_hive": lambda: hive_tex(53015),
        "fm_hive_front": lambda: hive_tex(53016, True),
        "fm_corn": lambda: corn(53017),
        "fm_mesh": lambda: mesh(53018),
        "fm_flannel": lambda: flannel(53019),
        "fm_denim": lambda: denim(53020),
        "fm_water": lambda: water(53021),
        "fm_feed": lambda: feed(53022),
        "fm_char": lambda: char(53023),
        "fm_split": lambda: split_end(53024),
        "fm_boards": lambda: F.mat("weathered_brown_planks", (124, 104, 82), 1.1, seed=53025),
        "fm_leather": lambda: F.mat("Leather007", (110, 64, 36), 1.1, seed=53026),
        "fm_pumpkin": lambda: pumpkin_tex(53027),
        "fm_pumpkin_face": lambda: pumpkin_tex(53028, "dark"),
        "fm_pumpkin_lit": lambda: pumpkin_tex(53029, "lit"),
        "fm_tank_red": lambda: dress_paint(53030, (168, 40, 32)),
        "fm_tpost": lambda: F.plain(53031, (62, 92, 58), 0.06, 0.4),
        "fm_ring_boards": lambda: ring_boards(53034),
        "fm_silage_plastic": lambda: silage_plastic(53035),
        "fm_hutch": lambda: hutch(53036),
        "fm_stall_door_bottom": lambda: stall_door(53032, False),
        "fm_stall_door_top": lambda: stall_door(53033, True),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

TIM, TIMH, GALV, STEEL, WHITE = "cp_timber", "cp_timber_h", "ext_galv", "painted_steel", "fm_white"
ROOF_KINDS = ["tin_galv", "tin_red", "tin_green", "slate", "brown", "charcoal", "red", "grey"]
DIRS = ("north", "south", "east", "west", "up", "down")


def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


AUTO_UV = {
    "down": lambda f, t: [f[0], 16 - t[2], t[0], 16 - f[2]],
    "up": lambda f, t: [f[0], f[2], t[0], t[2]],
    "north": lambda f, t: [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
    "south": lambda f, t: [f[0], 16 - t[1], t[0], 16 - f[1]],
    "west": lambda f, t: [f[2], 16 - t[1], t[2], 16 - f[1]],
    "east": lambda f, t: [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
}


def span_uv(b, frame):
    """UVs that stretch one texture over a whole object: each face of box b samples the part of its
    sprite that its place in frame (x0, y0, z0, x1, y1, z1) covers, so strips read as one round end."""
    fx0, fy0, fz0, fx1, fy1, fz1 = frame
    s = [16 / (fx1 - fx0), 16 / (fy1 - fy0), 16 / (fz1 - fz0)]
    lo = [(b[0] - fx0) * s[0], (b[1] - fy0) * s[1], (b[2] - fz0) * s[2]]
    hi = [(b[3] - fx0) * s[0], (b[4] - fy0) * s[1], (b[5] - fz0) * s[2]]
    return {d: [round(v, 3) for v in AUTO_UV[d](lo, hi)] for d in DIRS}


def disc_strips(r, cx, cy, step=2.0):
    """Horizontal strips filling a circle of radius r round (cx, cy): (y0, y1, x0, x1)."""
    out = []
    y = cy - r
    while y < cy + r - 0.01:
        y1 = min(y + step, cy + r)
        m = (y + y1) / 2 - cy
        half = math.sqrt(max(r * r - m * m, 0)) * 1.02
        out.append((round(y, 3), round(y1, 3), round(cx - half, 3), round(cx + half, 3)))
        y = y1
    return out


def bale(end, curve_u, curve_v, upright=False):
    """A round bale 22 across and 18 wide: strips two pixels deep stack its round, the end texture
    stretched over all of them so the roll reads whole. On its side the roll runs along z (an axis
    model); on end it stands round y."""
    r, c = 11, 8
    out = []
    if not upright:
        frame = (c - r, 0, -1, c + r, 2 * r, 17)
        for y0, y1, x0, x1 in disc_strips(r, c, r):
            b = [x0, y0, -1, x1, y1, 17]
            tex = {"north": end, "south": end, "east": curve_v, "west": curve_v, "up": curve_u, "down": curve_u}
            out.append(b + [tex, span_uv(b, frame)])
    else:
        frame = (c - r, 0, c - r, c + r, 18, c + r)
        for z0, z1, x0, x1 in disc_strips(r, c, c):
            b = [x0, 0, z0, x1, 18, z1]
            tex = {"up": end, "down": end, "north": curve_v, "south": curve_v, "east": curve_v, "west": curve_v}
            out.append(b + [tex, span_uv(b, frame)])
    return out


def bales(layers=2, askew=False):
    """Small square bales two to a layer along z, each a touch out of line."""
    rng = np.random.default_rng(layers * 7 + askew)
    tex = {"north": "fm_sq_end", "south": "fm_sq_end", "east": "fm_sq_side", "west": "fm_sq_side",
           "up": "fm_sq_top", "down": "fm_sq_top"}
    out = []
    if askew:
        return [[3, 0, 0.5, 11, 7.5, 15.5, tex, None, turn("y", 22.5, (7, 4, 8))]]
    for k in range(layers):
        for i in range(2):
            dx = float(rng.uniform(-0.3, 0.3))
            dz = float(rng.uniform(-0.6, 0.6))
            out.append([round(i * 8 + 0.2 + dx, 2), k * 8, round(0.2 + dz, 2), round(i * 8 + 7.8 + dx, 2),
                        k * 8 + 7.8, round(15.8 + dz, 2), tex])
    return out


def rails(tex, ys, thick=1.5, high=3.0):
    return [[8 - thick / 2, y, 0, 8 + thick / 2, y + high, 16, tex] for y in ys]


def fence_post(tex, cap=True):
    b = [[5.75, 0, 5.75, 10.25, 17, 10.25, tex]]
    if cap:
        b.append([6.5, 17, 6.5, 9.5, 17.8, 9.5, tex])
    return b


def tpost():
    """A green steel T-post with its anchor plate and the wire clips, the wire through it."""
    st = "fm_tpost"
    return [[7.2, 0, 7.4, 8.8, 16, 8.6, st], [7.6, 0, 6.4, 8.4, 16, 9.6, st],
            [6.5, 0, 6, 9.5, 2, 10, st],
            [7.5, 0, 0, 8.5, 16, 16, "barbed_wire"]]


def gambrel(kind, piece):
    """One column of a gambrel roof, rising to the north (the model's z = 0): steep_lo/steep_hi rise
    two blocks a column in 4 px courses on 2 px treads; low/high rise half a block in 2 px courses
    on 4 px treads, from the block's foot or its middle; _crest adds half the ridge cap on the high
    edge; ridge_low/ridge_high is a middle column, up from both sides."""
    tread, riser, cap = f"ext_{kind}_tread", f"ext_{kind}_riser", f"ext_{kind}_cap"
    # the ends show the sheet's own edge, not the house kit's white rake board: stepped this fine,
    # a white rake reads as a staircase up the gable
    f = {"up": tread, "south": riser, "north": "fm_boards", "down": "fm_boards", "east": riser, "west": riser}
    out = []
    if piece in ("steep_lo", "steep_hi"):
        # t runs from the low (south) edge: t = 16 - z
        ms = range(0, 4) if piece == "steep_lo" else range(4, 8)
        base = 0 if piece == "steep_lo" else 16
        for m in ms:
            h = 4 * (m + 1) - base
            out.append([0, 0, 16 - (2 * m + 2), 16, h, 16 - 2 * m, f])
        if piece == "steep_lo":
            out.append([0, 0, 0, 16, 16, 8, f])
        return out
    if piece.startswith("ridge"):
        b = 0 if piece == "ridge_low" else 8
        f2 = dict(f, north=riser)
        out += [[0, 0, 0, 16, b + 2, 4, f2], [0, 0, 12, 16, b + 2, 16, f],
                [0, 0, 4, 16, b + 4, 8, f2], [0, 0, 8, 16, b + 4, 12, f],
                [0, b + 4, 6, 16, b + 5.5, 10, cap], [0, b + 3.5, 5.5, 16, b + 4.5, 10.5, cap]]
        return out
    b = 0 if piece.startswith("low") else 8
    for m in range(4):
        out.append([0, 0, 16 - 4 * (m + 1), 16, b + 2 * (m + 1), 16 - 4 * m, f])
    if piece.endswith("crest"):
        out += [[0, b + 7.5, 0, 16, b + 9, 1.8, cap], [0, b + 7, 1.8, 16, b + 8.2, 2.6, cap]]
    return out


def cupola(kind, shift):
    """The ventilator on a barn's ridge, 24 px square centred on the ridge line (the block's x = 0):
    the saddle down into the roof, the louvred box with its corner boards, a cornice, the stepped
    pyramid roof, the finial and the weathervane's arrow and compass arms."""
    tread, riser = f"ext_{kind}_tread", f"ext_{kind}_riser"
    s = shift
    b = [[-11, s - 6, -3, 11, s + 2, 19, "fm_white"],
         [-10, s + 2, -2, 10, s + 13, 18, {"north": "fm_louvre", "south": "fm_louvre", "east": "fm_louvre",
                                           "west": "fm_louvre", "up": "fm_white", "down": "fm_white"}]]
    for x in (-10.6, 8.6):
        for z in (-2.6, 16.6):
            b.append([x, s + 2, z, x + 2, s + 13, z + 2, "fm_white"])
    b.append([-11.5, s + 13, -3.5, 11.5, s + 14.5, 19.5, "fm_white"])
    f = {"up": tread, "side": riser, "down": "ext_soffit"}
    for k, inset in enumerate((0, 3, 6, 9)):
        b.append([-12.5 + inset, s + 14.5 + 2 * k, -4.5 + inset, 12.5 - inset, s + 16.5 + 2 * k, 20.5 - inset, f])
    top = s + 22.5
    b += [[-1, top, 7, 1, top + 1.5, 9, "rust"],
          [-0.4, top + 1.5, 7.6, 0.4, 32 + min(0, s), 8.4, STEEL]]
    vy = min(top + 7, 30 + min(0, s))
    b += [[-5, vy - 2, 7.7, 5, vy - 1.4, 8.3, STEEL], [-0.3, vy - 2, 3, 0.3, vy - 1.4, 13, STEEL],
          [-7, vy, 7.8, 6, vy + 0.8, 8.2, "rust"], [-7.5, vy - 1, 7.85, -5, vy + 1.8, 8.15, "rust"],
          [5, vy - 0.3, 7.7, 7, vy + 1.1, 8.3, "rust"]]
    return b


def hay_track():
    """The hay fork's track under the hood: the steel track along the ridge, its trolley, the rope
    and the double harpoon fork hanging under it (a block down), centred on the ridge line."""
    return [[-1.5, 12, 0, 1.5, 13, 16, STEEL], [-0.5, 13, 0, 0.5, 15, 16, STEEL],
            [-2, 9, 5, 2, 12, 11, "rust"], [-2.5, 8.5, 6, -2, 11.5, 10, "rust"], [2, 8.5, 6, 2.5, 11.5, 10, "rust"],
            [-0.3, -9, 7.7, 0.3, 9, 8.3, "sc_rope"],
            [-3, -10, 7.4, 3, -9, 8.6, "rust"],
            [-3, -16, 7.5, -2, -10, 8.5, "rust"], [2, -16, 7.5, 3, -10, 8.5, "rust"],
            [-3.4, -16, 7.4, -1.6, -15, 8.6, "rust"], [1.6, -16, 7.4, 3.4, -15, 8.6, "rust"]]


def stall_boards():
    """A horse stall's lower partition: tongue-and-groove boards and the cap rail along the top."""
    return [[7, 0, 0, 9, 15, 16, "fm_boards"], [6.5, 15, 0, 9.5, 16, 16, TIMH],
            [6.8, 0, 0, 9.2, 15, 0.8, TIM]]


def stall_bars():
    """The upper partition: round steel bars in a channel, a top rail."""
    b = [[7, 0, 0, 9, 1.5, 16, STEEL], [7, 12, 0, 9, 13.5, 16, STEEL]]
    for z in np.arange(1, 16, 2.5):
        b.append([7.6, 1.5, round(z, 2), 8.4, 12, round(z + 0.8, 2), STEEL])
    return b


def loop():
    """A cattle stall's divider of galvanised pipe: two rails and an upright."""
    return [[7.3, 6, 0, 8.7, 7.4, 16, GALV], [7.3, 13, 0, 8.7, 14.4, 16, GALV],
            [7.3, 0, 7.3, 8.7, 14.4, 8.7, GALV], [6.5, 0, 6.5, 9.5, 0.6, 9.5, "concrete_dark"]]


def headlock():
    """The feed fence: two rails, yoked uprights in pairs (the stanchions swinging on their pins),
    yellow-painted, run along z."""
    y = "steel_yellow"
    b = [[7.2, 1, 0, 8.8, 2.4, 16, y], [7.2, 14.5, 0, 8.8, 16, 16, y]]
    for z0 in (1.5, 9.5):
        b += [[7.4, 2.4, z0, 8.6, 14.5, z0 + 1.2, y],
              [7.4, 2.4, z0 + 4, 8.6, 10, z0 + 5.2, y, None, turn("x", 22.5, (8, 6, z0 + 4.6))],
              [7.4, 10, z0 + 4.2, 8.6, 14.5, z0 + 5.4, y]]
    return b


def manger():
    """A manger at the wall (the model's south): the trough box with feed in it, the slatted hay
    rack leaning out over it."""
    b = [[1, 0, 9, 15, 7, 16, "fm_boards"], [2, 3, 10, 14, 6, 15, "fm_feed"],
         [1, 7, 15, 15, 16, 16, TIMH], [1, 14.5, 8, 15, 16, 9.5, TIMH]]
    for x in (2, 5, 8, 11, 14):
        b.append([x, 7, 9, x + 1, 15, 10, TIM, None, turn("x", -22.5, (x + 0.5, 7, 9.5))])
    return b


def water_trough():
    """A galvanised stock trough along z: ribbed sides, the rolled rim, green water, the drain plug."""
    w = {"up": "fm_water", "side": GALV, "down": GALV}
    return [[2, 0, 0, 14, 9, 16, w],
            [1.5, 8.5, 0, 2.5, 10, 16, GALV], [13.5, 8.5, 0, 14.5, 10, 16, GALV],
            [2, 0, 0, 14, 10, 0.8, GALV], [2, 0, 15.2, 14, 10, 16, GALV],
            [7.2, 1, -0.6, 8.8, 2.6, 0, "rust"]]


def feed_bin():
    """A plywood feed bin, its hinged lid propped, sacks in it and one slumped against it."""
    return [[1, 0, 2, 15, 11, 15, "crate_wood"], [0.5, 11, 1.5, 15.5, 12, 15.5, "crate_wood"],
            [1, 11, 15, 15, 23, 16, "crate_wood", None, turn("x", -22.5, (8, 12, 15.5))],
            [2.5, 10, 3, 13.5, 12.5, 14, "fm_feed"],
            [3, 0, -1, 11, 7, 2, "om_burlap"]]


def saddle_rack():
    """A saddle on its wall rack (the wall to the south), the bridle hanging beside it."""
    lea = "fm_leather"
    return [[6, 6, 10, 10, 8, 16, TIMH],
            [3, 7, 4, 13, 10, 15, lea], [5, 10, 6, 11, 11.5, 13, lea], [4, 5, 5, 5, 9, 12, lea], [11, 5, 5, 12, 9, 12, lea],
            [7.3, 2, 6, 8.7, 7, 7, STEEL],
            [13, 6, 14.5, 14, 14, 15.5, lea], [12.5, 13, 14.5, 14.5, 14, 16, STEEL]]


def ladder(broken=False):
    """A loft ladder nailed up against the wall (the model's south): two rough stiles, the rungs
    let into them, the nail heads; one rung split out on a broken one."""
    b = [[2, 0, 13, 4, 16, 15.5, TIMH], [12, 0, 13, 14, 16, 15.5, TIMH]]
    for i, y in enumerate((1.5, 5.5, 9.5, 13.5)):
        if broken and i == 2:
            b += [[4, y, 13.4, 7, y + 1.5, 14.6, TIMH], [10.5, y - 0.6, 13.4, 12, y + 1, 14.6, TIMH]]
            continue
        b += [[4, y, 13.4, 12, y + 1.5, 14.6, TIMH], [2.7, y + 0.4, 12.7, 3.3, y + 1.1, 13, STEEL],
              [12.7, y + 0.4, 12.7, 13.3, y + 1.1, 13, STEEL]]
    return b


def tack_trunk():
    return [[1, 0, 3, 15, 9, 15, {"north": "fm_boards", "side": "fm_boards", "up": "fm_boards", "down": "crate_wood"}],
            [0.5, 9, 2.5, 15.5, 10.5, 15.5, TIMH], [6.5, 6, 2.2, 9.5, 9, 3, STEEL],
            [0.5, 2, 2.5, 15.5, 3, 15.5, STEEL]]


def lantern(lit):
    """A kerosene barn lantern on its chain (hanging from the block above): the guard, the globe,
    the tank and the hood."""
    glass = "fit_lens_on" if lit else "fit_lens_off"
    return [[6, 2, 6, 10, 4, 10, "rust"], [6.5, 4, 6.5, 9.5, 9, 9.5, glass],
            [6, 4, 6, 6.6, 9, 6.6, STEEL], [9.4, 4, 9.4, 10, 9, 10, STEEL],
            [5.5, 9, 5.5, 10.5, 10.5, 10.5, "rust"], [7, 10.5, 7, 9, 12, 9, "rust"],
            [7.6, 12, 7.6, 8.4, 16, 8.4, STEEL]]


def lantern_floor(lit):
    """The same lantern stood on a floor or a crate, its bail folded down."""
    return [[b[0], b[1] - 2, b[2], b[3], b[4] - 2, b[5], b[6]] for b in lantern(lit)[:6]] + [[6.5, 10, 7.7, 9.5, 10.6, 8.3, STEEL]]


def drum():
    """A 55-gallon steel drum: the rolling hoops, the chimes, the bung and its vent, rust where
    the paint's chipped."""
    return [[3, 0, 3, 13, 15, 13, "barrel_rust"], [2.6, 4.5, 2.6, 13.4, 5.3, 13.4, "barrel_rust"],
            [2.6, 9.7, 2.6, 13.4, 10.5, 13.4, "barrel_rust"], [2.8, 14.5, 2.8, 13.2, 15.4, 13.2, "rust"],
            [5, 15, 5, 6.5, 15.6, 6.5, STEEL], [10, 15, 10, 11, 15.4, 11, STEEL]]


def chain():
    return [[7.5, 0, 7.6, 8.5, 16, 8.4, STEEL], [7.6, 2, 7.5, 8.4, 3.5, 8.5, "rust"], [7.6, 9, 7.5, 8.4, 10.5, 8.5, "rust"]]


def bedroll():
    return [[2, 0, 1, 14, 2.5, 14, "tarp_green"], [2.5, 2.5, 1.5, 13.5, 3, 13, "tarp_green"],
            [3, 0, 12, 13, 3.5, 15.5, "home_pillow"]]


def grindstone():
    """A treadle grindstone on its wooden frame, the stone stood in its trough."""
    stone = {"side": "stone_trim", "north": "concrete_dark", "south": "concrete_dark"}
    b = [[1, 0, 2, 3, 8, 4, TIM], [13, 0, 2, 15, 8, 4, TIM], [1, 0, 12, 3, 8, 14, TIM], [13, 0, 12, 15, 8, 14, TIM],
         [1, 6, 4, 3, 8, 12, TIMH], [13, 6, 4, 15, 8, 12, TIMH],
         [4, 6, 5, 12, 9, 11, "rust"]]
    for a in (0, 22.5, 45, -22.5):
        b.append([7, 6, 4, 9, 14, 12, stone] + ([None, turn("x", a, (8, 10, 8))] if a else []))
    b.append([2, 2, 7, 14, 3, 9, STEEL])
    return b


def anvil():
    """An anvil on its stump, a hammer across it."""
    return [[3, 0, 3, 13, 8, 13, {"up": "tree_oak_rings", "side": "tree_oak_bark"}],
            [5, 8, 4, 11, 10, 12, "rust"], [6, 10, 5, 10, 12, 11, "rust"],
            [4, 12, 2, 12, 15, 14, "rust"], [6, 12, 0, 10, 14.5, 2, "rust"],
            [8, 15, 4, 9, 16, 12, TIMH], [7.5, 15, 10, 9.5, 17, 12.5, STEEL]]


def nest_boxes():
    """A row of nest boxes against the wall (south), straw in each, a perch rail before them."""
    b = [[0, 0, 7, 16, 1, 16, "fm_boards"], [0, 7, 7, 16, 8, 16, "fm_boards"], [0, 14, 7, 16, 15, 16, "fm_boards"],
         [0, 0, 15, 16, 15, 16, "fm_boards"]]
    for x in (0, 5.3, 10.6, 15):
        b.append([x, 0, 7, x + 1, 15, 16, "fm_boards"])
    for y in (1, 8):
        for x in (1, 6.3, 11.6):
            b.append([x, y, 8, x + 4.3, y + 1.5, 15, "straw_floor"])
        b.append([0, y + 1, 5, 16, y + 2, 6, TIMH])
    return b


def roost():
    """Roost poles along z on two trestles, the droppings under."""
    return [[3, 9, 0, 5, 11, 16, TIMH], [11, 12, 0, 13, 14, 16, TIMH],
            [3, 0, 7, 5, 9, 9, TIM], [11, 0, 7, 13, 12, 9, TIM],
            [0, 0, 0, 16, 0.3, 16, "ch_dirt"]]


def waterer():
    """A poultry waterer: the galvanised fount on its saucer."""
    return [[4, 0, 4, 12, 1.5, 12, GALV], [5, 1.5, 5, 11, 9, 11, GALV], [6, 9, 6, 10, 10.5, 10, GALV],
            [7, 10.5, 7.5, 9, 11.5, 8.5, STEEL], [4.5, 1, 4.5, 11.5, 1.3, 11.5, "fm_water"]]


def feathers():
    return [[2, 0, 3, 6, 0.3, 5, "mu_feather_white"], [8, 0, 9, 11, 0.3, 13, "mu_feather_white"],
            [11, 0, 2, 14, 0.3, 4, "mu_feather_white"], [4, 0, 11, 7, 0.4, 14, "blood_splat"]]


def hive(tipped=False):
    """A Langstroth hive on its stand: bottom board, two deep boxes, the inner cover and the
    telescoping lid; tipped, the boxes lie spilled and the lid is off."""
    side = {"north": "fm_hive_front", "side": "fm_hive", "up": "crate_wood", "down": "crate_wood"}
    if tipped:
        return [[1, 0, 1, 3, 3, 15, "concrete_dark"], [13, 0, 1, 15, 3, 15, "concrete_dark"],
                [2, 0, 2, 13, 9, 11, side, None, turn("y", 22.5, (8, 4, 7))],
                [4, 0, 9, 13, 3, 16, "crate_wood"], [5, 3, 10, 6, 5, 16, "crate_wood"], [8, 3, 10, 9, 5, 16, "crate_wood"],
                [0, 0, 11, 6, 1.5, 16, GALV]]
    return [[1, 0, 1, 3, 3, 15, "concrete_dark"], [13, 0, 1, 15, 3, 15, "concrete_dark"],
            [2, 3, 1, 14, 4, 15, "crate_wood"], [2, 4, 2, 14, 10, 15, side], [2, 10, 2, 14, 15.5, 15, side],
            [2, 15.5, 2, 14, 16, 15, "crate_wood"]]


def hive_lid():
    """The telescoping lid on top (the block above), a brick on it against the wind."""
    return [[1.5, 0, 1.5, 14.5, 2, 15.5, GALV], [5, 2, 6, 11, 4, 9, "brick_red"]]


def scarecrow(hi):
    """A scarecrow on a cross of poles: the low block its post and legs in overalls, the high its
    flannel body and arms out along x, the burlap head and the straw hat."""
    if not hi:
        return [[7, 0, 7, 9, 16, 9, TIM],
                [5, 4, 6.5, 7.5, 16, 9.5, "fm_denim"], [8.5, 4, 6.5, 11, 16, 9.5, "fm_denim"],
                [5, 2, 6, 7.5, 4, 10, "straw_floor"], [8.5, 1, 6, 11, 4, 10, "straw_floor"]]
    return [[7, 0, 7, 9, 13, 9, TIM], [-6, 8, 7.2, 22, 9.6, 8.8, TIMH],
            [4.5, 0, 5.5, 11.5, 9, 10.5, "fm_flannel"], [-5, 6.5, 6, 4.5, 10.5, 10, "fm_flannel"],
            [11.5, 6.5, 6, 21, 10.5, 10, "fm_flannel"],
            [-6.5, 6, 6.5, -5, 9, 9.5, "straw_floor"], [21, 5.5, 6.5, 22.5, 9, 9.5, "straw_floor"],
            [5, 9, 5, 11, 15, 11, "om_burlap"], [6, 11, 4.8, 7.5, 12.2, 5, "home_plastic_black"],
            [8.5, 11, 4.8, 10, 12.2, 5, "home_plastic_black"],
            [2, 14.5, 1, 14, 15.3, 15, "straw_floor"], [5, 15.3, 4.5, 11, 18.5, 11.5, "straw_floor"]]


def firewood():
    """Split firewood stacked crosswise along z between two stakes, the top course ragged."""
    tex = {"north": "fm_split", "south": "fm_split", "side": "bark", "up": "bark", "down": "bark"}
    b = []
    rng = np.random.default_rng(17)
    for row in range(4):
        for i in range(4):
            x0 = i * 4 + float(rng.uniform(-0.3, 0.3))
            h = 3.4
            y0 = row * 3.6
            if row == 3 and rng.random() < 0.4:
                continue
            b.append([round(x0, 2), round(y0, 2), 0.5, round(x0 + 3.8, 2), round(y0 + h, 2), 15.5, tex,
                      None, turn("z", 45 if (i + row) % 2 else -45, (x0 + 1.9, y0 + 1.7, 8))]
                     if row % 2 else [round(x0, 2), round(y0, 2), 0.5, round(x0 + 3.8, 2), round(y0 + h, 2), 15.5, tex])
    return b


def chop_block():
    """A chopping block (a round of oak), the axe still in it, chips round its foot."""
    return [[3, 0, 3, 13, 9, 13, {"up": "tree_oak_rings", "side": "tree_oak_bark"}],
            [7.5, 9, 5, 8.5, 21, 6.5, TIM, None, turn("x", -22.5, (8, 9, 6))],
            [6, 7, 4.5, 10, 10, 7, STEEL],
            [1, 0, 1, 4, 0.4, 3, "fm_split"], [12, 0, 11, 15, 0.4, 14, "fm_split"]]


def well():
    """A well's head over its stone curb (one block below): two posts, the windlass with its crank
    and rope, the bucket, the shingled gable roof along x."""
    sh = {"up": "ext_brown_tread", "side": "ext_brown_riser", "down": "ext_soffit"}
    b = [[-8, -16, 6, -5, 18, 10, TIM], [21, -16, 6, 24, 18, 10, TIM],
         [-5, 8, 6.5, 21, 11, 9.5, {"side": "tree_oak_bark", "east": "tree_oak_rings", "west": "tree_oak_rings"}],
         [24, 8.5, 7.5, 26, 9.5, 8.5, STEEL], [25.5, 4, 7.5, 26.5, 9, 8.5, STEEL], [25.5, 3, 6.5, 26.5, 4, 10, TIM],
         [7.6, -8, 7.6, 8.4, 8, 8.4, "sc_rope"],
         [5, -14, 5, 11, -8, 11, {"side": "fm_boards", "up": "fm_water", "down": "fm_boards"}],
         [4.8, -12, 4.8, 11.2, -11, 11.2, "rust"], [7.6, -8, 4.8, 8.4, -6, 11.2, "rust"],
         [-9, 18, 6, 25, 20, 10, TIMH]]
    for j in range(4):
        y0 = 18 + j * 2.5
        b.append([-10, y0, -6 + 3 * j, 26, y0 + 2.5, 8, dict(sh, south="ext_soffit")])
        b.append([-10, y0, 8, 26, y0 + 2.5, 22 - 3 * j, dict(sh, north="ext_soffit")])
    b.append([-10, 28, 6, 26, 29.5, 10, "ext_brown_cap"])
    return b


def outhouse(hi):
    """An outhouse of grey boards, a block square: the low block its walls and door with the moon
    cut in it, the high its upper walls, the vent and the shed roof falling to the back."""
    wall = {"side": "barn_grey", "up": "barn_grey", "down": "barn_grey"}
    if not hi:
        return [[0, 0, 0, 16, 16, 16, wall], [2, 0, -0.6, 14, 16, 0, "fm_boards"],
                [3, 2, -1, 13, 3.5, -0.6, TIMH], [3, 12, -1, 13, 13.5, -0.6, TIMH],
                [3, 3.5, -1, 4.5, 12, -0.6, TIM, None, turn("z", -22.5, (8, 8, -0.8))],
                [12, 7, -1.1, 13, 8.5, -0.6, STEEL]]
    return [[0, 0, 0, 16, 9, 16, wall], [2, 0, -0.6, 14, 4, 0, "fm_boards"],
            [6, 4, -0.7, 10, 8, 0, "home_plastic_black"], [7, 5, -0.8, 9, 7, -0.6, "fm_boards"],
            [-1, 9, -2, 17, 11, 18, {"up": "ext_tin_galv_tread", "side": "ext_fascia", "down": "ext_soffit"}],
            [-1, 11, -2, 17, 12, 6, {"up": "ext_tin_galv_tread", "side": "ext_fascia", "down": "ext_soffit"}],
            [11, 12, 10, 13, 15, 12, "rust"]]


def girt():
    """A windmill tower's X brace: two angle irons corner to corner across the block, along z."""
    a = "rust"
    h = 22.63 / 2 + 0.5
    return [[7.6, 8 - 0.6, 8 - h, 8.4, 8 + 0.6, 8 + h, a, None, turn("x", 45, (8, 8, 8))],
            [7.6, 8 - 0.6, 8 - h, 8.4, 8 + 0.6, 8 + h, a, None, turn("x", -45, (8, 8, 8))],
            [7.5, 7.4, 7.4, 8.5, 8.6, 8.6, STEEL]]


def pump():
    """The windmill's pump stand: the cast pump body on the well casing, its spout, the rod's
    stuffing box, the base flange."""
    c = "rust"
    return [[5, 0, 5, 11, 1, 11, "concrete_dark"], [6.5, 1, 6.5, 9.5, 9, 9.5, c], [6, 9, 6, 10, 11, 10, c],
            [9.5, 6, 7.2, 15, 7.6, 8.8, c], [14, 4.5, 7.2, 15.4, 7.6, 8.8, c],
            [7.5, 11, 7.5, 8.5, 16, 8.5, STEEL]]


def pump_rod():
    return [[7.6, 0, 7.6, 8.4, 16, 8.4, STEEL], [7.2, 7, 7.2, 8.8, 8.5, 8.8, "rust"]]


def pipe():
    """A galvanised pipe along z on its saddle."""
    return [[7, 4, 0, 9, 6, 16, GALV], [6.6, 3.6, 7, 9.4, 6.4, 9, "rust"], [7.6, 0, 7.6, 8.4, 3.6, 8.4, STEEL]]


def fuel_stand():
    """The diesel tank's stand: two A-frame legs of angle iron, along z."""
    b = []
    for z in (1, 13):
        b += [[3, 0, z, 4, 16, z + 1.5, STEEL, None, turn("z", 22.5, (3.5, 8, z))],
              [12, 0, z, 13, 16, z + 1.5, STEEL, None, turn("z", -22.5, (12.5, 8, z))],
              [2, 6, z, 14, 7, z + 1.5, STEEL]]
    b.append([7.5, 6, 2, 8.5, 7, 14, STEEL])
    return b


def fuel_tank(end):
    """The tank: a red steel cylinder along z on its stand (strips round its curve); the end piece
    carries the dished head, the filler and the hose to its nozzle hanging down."""
    out = []
    frame = (1, 1, 0, 15, 15, 16)
    for y0, y1, x0, x1 in disc_strips(7, 8, 8, 1.75):
        bz = [x0, y0, 0 if not end else 1.2, x1, y1, 16]
        out.append(bz + [{"side": "fm_tank_red", "north": "fm_tank_red", "south": "fm_tank_red"}, span_uv(bz, frame)])
    if end:
        out += [[3, 3, 0.4, 13, 13, 1.2, "fm_tank_red"], [7, 15, 6, 9, 16.5, 9, STEEL],
                [6, 7, -0.5, 10, 9, 0.4, STEEL], [7.4, -8, -1.5, 8.6, 8, -0.5, "home_plastic_black"],
                [6.8, -10, -2.2, 9.2, -7.5, -0.8, "rust"]]
    return out


def spout():
    """A section of grain spout: galvanised tube with a seam band."""
    return [[5.5, 0, 5.5, 10.5, 16, 10.5, GALV], [5, 7, 5, 11, 8.5, 11, "rust"]]


def pumpkin(face):
    tex = {"side": "fm_pumpkin", "up": "fm_pumpkin", "down": "fm_pumpkin"}
    if face:
        tex["north"] = face
    return [[3, 0, 3, 13, 9, 13, tex], [4, 9, 4, 12, 9.6, 12, "fm_pumpkin"], [7.3, 9.6, 7.3, 8.7, 12, 8.7, "tree_oak_bark"]]


def char_logs():
    """What's left of a pyre: charred logs crossed in a heap, ash under them, a bone or two."""
    t = {"side": "fm_char", "north": "fm_char", "south": "fm_char"}
    return [[0, 0, 0, 16, 0.4, 16, "burnt_ground"],
            [1, 0.4, 3, 4, 3.4, 16, t, None, turn("y", 22.5, (2.5, 2, 9))],
            [11, 0.4, 0, 14, 3.4, 13, t, None, turn("y", -22.5, (12.5, 2, 6))],
            [0, 3.4, 6, 16, 6.4, 9, {"side": "fm_char", "up": "fm_char"}],
            [5, 0.4, 5, 9, 2.4, 11, "mu_bone"], [9, 0.4, 10, 13, 1.4, 11.4, "mu_bone"]]


def bones():
    return [[2, 0, 4, 11, 1.4, 5.4, "mu_bone"], [9, 0, 3.5, 11.5, 2, 6, "mu_bone"],
            [4, 0, 8, 13, 1.6, 9.6, "mu_bone", None, turn("y", 22.5, (8, 1, 9))],
            [3, 0, 11, 9, 3, 15, "mu_bone"], [3.5, 3, 11.5, 8.5, 3.6, 14.5, "mu_bone"]]


def hutch_piece(front):
    """A calf hutch, a block wide, its round roof in three steps; the front has the doorway and
    the feed bucket rings, the back its vent."""
    h = "fm_hutch"
    b = [[0, 0, 0, 1, 12, 16, h], [15, 0, 0, 16, 12, 16, h],
         [0, 12, 0, 16, 14, 16, h], [1.5, 14, 0, 14.5, 15.5, 16, h], [4, 15.5, 0, 12, 16.5, 16, h]]
    if front:
        b += [[1, 0, 0, 4, 12, 1, h], [12, 0, 0, 15, 12, 1, h], [4, 9, 0, 12, 12, 1, h],
              [12.5, 6, -1.5, 14.5, 6.5, 0, STEEL], [12, 3, -2, 15, 6, 0.5, "sc_bucket"]]
    else:
        b += [[1, 0, 15, 15, 12, 16, h], [6, 9, 15.5, 10, 11, 16.6, "home_plastic_black"]]
    return b


def silage():
    """The black sheet over a silage bunker's face, draped a block down, tyres on it."""
    return [[0, 0, 0, 16, 1, 16, "fm_silage_plastic"], [0, 1, 4, 16, 1.6, 10, "fm_silage_plastic"]]


def beam():
    """A log beam along z: a ranch gate's crossbar."""
    return [[3, 2, 0, 13, 12, 16, {"side": "tree_spruce_bark", "north": "tree_spruce_rings", "south": "tree_spruce_rings"}]]


def panel(post=False):
    """A corral panel along z (refs/farm/cattle-pen-feedlot): five galvanised tube rails, a short
    upright at the joint; with post, the round pipe post the panels are chained to."""
    b = [[7.4, y, 0, 8.6, y + 1.2, 16, GALV] for y in (2, 5, 8, 11, 14)]
    b.append([7.3, 0, 7.4, 8.7, 15.5, 8.6, GALV])
    if post:
        b += [[6.4, 0, 6.4, 9.6, 17, 9.6, GALV], [6.1, 17, 6.1, 9.9, 17.6, 9.9, GALV],
              [6.2, 13.2, 6.2, 9.8, 14.2, 9.8, "rust"]]
    return b


def quarter(r, step=2.0):
    """The quarter of a circle of radius r round the block's south-east corner (16, 16), as z strips
    (z0, z1, x0): each strip runs from x0 to the corner's side."""
    out = []
    for z0, z1, x0, x1 in disc_strips(r, 16, 16, step):
        if z1 <= 16.001:
            out.append((z0, z1, max(x0, 0.0)))
    return out


def stock_tank():
    """A quarter of a round galvanised stock tank, eight foot across and two deep (refs/farm/
    cattle-water-trough): four of them round a corner make the tank, its rolled rim and the water."""
    b = []
    for z0, z1, x0 in quarter(14.5):
        b.append([x0, 0, z0, 16, 9, z1, {"up": "fm_water", "side": GALV, "down": GALV}])
        b.append([x0, 9, z0, min(16, x0 + 1.0), 10.2, z1, GALV])
    z0, z1, x0 = quarter(14.5)[0]
    b.append([x0, 9, z0, 16, 10.2, z0 + 1.0, GALV])
    for y in (2.5, 6):
        for z0, z1, x0 in quarter(14.7, 3.0):
            b.append([x0 - 0.2, y, z0, x0 + 0.3, y + 0.6, z1, GALV])
    return b


def ring_feeder():
    """A quarter of a round bale ring feeder (refs/farm/cattle-pen-feedlot): the galvanised ring,
    its top and bottom tubes and the slanted bars, round the bale it was last filled with, half
    eaten. Four round a corner make it."""
    b = []
    hay = {"up": "fm_hay_end", "down": "fm_hay_end", "side": "fm_hay_round_v"}
    for z0, z1, x0 in quarter(11.5):
        b.append([x0, 0, z0, 16, 15 if x0 > 8 else 13, z1, hay])
    for z0, z1, x0 in quarter(15.2, 1.5):
        for y0, y1 in ((1.5, 3), (14, 15.5)):
            b.append([x0, y0, z0, min(16, x0 + 1.5), y1, z1, GALV])
    # the bars: short uprights round the ring, leaning in
    for k in range(5):
        a = math.radians(9 + k * 18)
        x, z = 16 - 15.2 * math.cos(a), 16 - 15.2 * math.sin(a)
        b.append([round(x - 0.6, 2), 3, round(z - 0.6, 2), round(x + 0.6, 2), 14, round(z + 0.6, 2), GALV])
    b.append([0, 0, 0, 16, 0.3, 16, "fm_feed"])
    return b


def feeder():
    """A poultry tube feeder (refs/farm/chicken-coop-and-run): the red plastic pan, the tube of
    feed above it and the cap, standing on the litter."""
    red = "fm_tank_red"
    return [[3, 0, 3, 13, 1, 13, red], [2.5, 1, 2.5, 13.5, 2.6, 13.5, red], [4, 1, 4, 12, 2.4, 12, "fm_feed"],
            [5, 2.6, 5, 11, 12, 11, red], [4.5, 12, 4.5, 11.5, 13.2, 11.5, red], [7.5, 13.2, 7.5, 8.5, 14.5, 8.5, STEEL]]


def butcher_hook():
    """A butcher's gambrel hung off a beam (refs/farm/butcher-hook; the hanging rig as farms used
    it): the chain from the beam above to the block and tackle, the hoist chain down to the steel
    spreader, its ends hooked up, with the hook for the ring in the middle."""
    b = [[7.6, 12, 7.6, 8.4, 16, 8.4, STEEL],
         [6.5, 9, 7, 9.5, 12, 9, STEEL], [7, 9.5, 6.6, 9, 11.5, 9.4, "rust"],
         [7.7, 5, 7.7, 8.3, 9, 8.3, STEEL],
         [2, 3.6, 7.4, 14, 4.6, 8.6, STEEL],
         [2, 4.6, 7.4, 2.9, 6.6, 8.6, STEEL], [13.1, 4.6, 7.4, 14, 6.6, 8.6, STEEL],
         [7.4, 2.4, 7.5, 8.6, 3.6, 8.5, STEEL], [7.4, 1.6, 7.5, 8, 2.4, 8.5, "rust"]]
    return b


def meat_rail():
    """A smokehouse rail along z (refs/farm/butcher-hook): the pole under the roof, S-hooks on it,
    hams sewn in cloth sacks and a side of bacon hung to smoke."""
    b = [[7, 13.5, 0, 9, 15.5, 16, TIMH]]
    for z, kind in ((3, "sack"), (8, "slab"), (13, "sack")):
        b.append([7.8, 11, z - 0.3, 8.2, 13.5, z + 0.3, STEEL])
        if kind == "sack":
            b.append([6, 4, z - 2, 10, 11, z + 2, "om_burlap"])
            b.append([6.8, 3, z - 1.2, 9.2, 4, z + 1.2, "om_burlap"])
        else:
            b.append([7.2, 4.5, z - 2.2, 8.8, 11, z + 2.2, "fm_leather"])
    return b


def pole():
    """A pole barn's treated post, a knee brace up to the plate on one side (the model's south)."""
    return [[5, 0, 5, 11, 16, 11, TIM], [6, 10, 11, 10, 11.5, 22, TIMH, None, turn("x", -45, (8, 10, 11))]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    # bales
    add("fm_bale", bale("fm_hay_end", "fm_hay_round_u", "fm_hay_round_v"), "Round Bale", "GRASS", rotate="axis")
    add("fm_bale_grey", bale("fm_hay_end_grey", "fm_hay_round_u", "fm_hay_round_v"), "Round Bale", "GRASS", rotate="axis")
    add("fm_bale_up", bale("fm_hay_end", "fm_hay_round_v", "fm_hay_round_v", upright=True), "Round Bale", "GRASS", rotate=None)
    add("fm_wrap", bale("fm_wrap_end", "bale_wrap", "bale_wrap"), "Wrapped Bale", "WOOL", rotate="axis")
    add("fm_wrap_up", bale("fm_wrap_end", "bale_wrap", "bale_wrap", upright=True), "Wrapped Bale", "WOOL", rotate=None)
    add("fm_bales", bales(2), "Hay Bales", "GRASS", rotate="axis")
    add("fm_bales_low", bales(1), "Hay Bales", "GRASS", rotate="axis")
    add("fm_bale_one", bales(1, askew=True), "Hay Bale", "GRASS", rotate="facing")
    # fences
    add("fm_rail", rails(TIMH, (3.5, 8, 12.5)), "Rail Fence", "WOOD", rotate="axis")
    add("fm_rail_post", fence_post(TIM) + rails(TIMH, (3.5, 8, 12.5)), "Rail Fence", "WOOD", rotate="axis")
    add("fm_rail_broken", rails(TIMH, (12.5,)) + [[7.25, 3.5, 0, 8.75, 6.5, 16, TIMH, None, turn("x", -22.5, (8, 5, 0))]],
        "Rail Fence", "WOOD", rotate="axis")
    add("fm_rail_w", rails(WHITE, (3, 8, 13), 1.2, 3.5), "Paddock Fence", "WOOD", rotate="axis")
    add("fm_rail_post_w", fence_post(WHITE) + rails(WHITE, (3, 8, 13), 1.2, 3.5), "Paddock Fence", "WOOD", rotate="axis")
    add("fm_post", fence_post(TIM), "Fence Post", "WOOD", rotate=None)
    add("fm_post_w", fence_post(WHITE), "Fence Post", "WOOD", rotate=None)
    add("fm_tpost", tpost(), "T-Post", "METAL", rotate="axis", render="cutout")
    out["fm_gate"] = {"door": "steel", "tex": {"top": "fm_gate_top", "bottom": "fm_gate_bottom"}, "title": "Farm Gate"}
    out["fm_stall_door"] = {"door": "wood", "tex": {"top": "fm_stall_door_top", "bottom": "fm_stall_door_bottom"},
                            "title": "Stall Door"}
    # the barn
    for name, tex, title in (("fm_fieldstone", "fm_fieldstone", "Fieldstone"), ("fm_fieldstone_moss", "fm_fieldstone_moss", "Fieldstone"),
                             ("fm_cinder", "fm_cinder", "Cinder Block"), ("fm_trim", "fm_white", "Trim")):
        out[name] = {"tex": tex, "title": title, "sound": "WOOD" if name == "fm_trim" else "STONE"}
    for kind in ROOF_KINDS:
        snd = "METAL" if kind.startswith("tin") else "STONE"
        for piece in ("steep_lo", "steep_hi", "low", "high", "low_crest", "high_crest", "ridge_low", "ridge_high"):
            add(f"fm_gam_{kind}_{piece}", gambrel(kind, piece), "Barn Roof", snd)
        add(f"fm_cupola_{kind}", cupola(kind, 0), "Cupola", "WOOD", rotate=None)
        add(f"fm_cupola_{kind}_high", cupola(kind, -8), "Cupola", "WOOD", rotate=None)
    add("fm_hay_track", hay_track(), "Hay Track", "METAL", rotate=None)
    # inside
    add("fm_stall_boards", stall_boards(), "Stall", "WOOD", rotate="axis")
    add("fm_stall_bars", stall_bars(), "Stall Bars", "METAL", rotate="axis")
    add("fm_loop", loop(), "Stall Divider", "METAL", rotate="axis")
    add("fm_headlock", headlock(), "Headlock", "METAL", rotate="axis")
    add("fm_manger", manger(), "Manger", "WOOD")
    add("fm_water_trough", water_trough(), "Water Trough", "METAL", rotate="axis")
    add("fm_feed_bin", feed_bin(), "Feed Bin", "WOOD", loot="storage")
    add("fm_saddle_rack", saddle_rack(), "Saddle", "WOOD")
    add("fm_ladder", ladder(), "Ladder", "WOOD", climb=True)
    add("fm_ladder_broken", ladder(True), "Ladder", "WOOD", climb=True)
    add("fm_tack_trunk", tack_trunk(), "Tack Trunk", "WOOD", loot="storage")
    add("fm_lantern", lantern(False), "Lantern", "METAL", rotate=None, decor=True)
    add("fm_lantern_lit", lantern(True), "Lantern", "METAL", rotate=None, decor=True, light=11)
    add("fm_lantern_floor", lantern_floor(False), "Lantern", "METAL", rotate=None, decor=True)
    add("fm_lantern_floor_lit", lantern_floor(True), "Lantern", "METAL", rotate=None, decor=True, light=11)
    add("fm_drum", drum(), "Drum", "METAL", rotate=None, loot="storage")
    add("fm_chain", chain(), "Chain", "METAL", rotate=None, decor=True)
    add("fm_bedroll", bedroll(), "Bedroll", "WOOL", decor=True)
    add("fm_grindstone", grindstone(), "Grindstone", "WOOD")
    add("fm_anvil", anvil(), "Anvil", "METAL")
    add("fm_nest_boxes", nest_boxes(), "Nest Boxes", "WOOD")
    add("fm_roost", roost(), "Roost", "WOOD", rotate="axis")
    add("fm_waterer", waterer(), "Waterer", "METAL", rotate=None)
    add("fm_feathers", feathers(), "Feathers", "WOOL", rotate=None, decor=True)
    # the yard
    add("fm_hive", hive(), "Beehive", "WOOD")
    add("fm_hive_lid", hive_lid(), "Beehive", "METAL")
    add("fm_hive_tipped", hive(True), "Beehive", "WOOD")
    add("fm_scarecrow_lo", scarecrow(False), "Scarecrow", "GRASS")
    add("fm_scarecrow_hi", scarecrow(True), "Scarecrow", "GRASS")
    add("fm_firewood", firewood(), "Firewood", "WOOD", rotate="axis")
    add("fm_chop_block", chop_block(), "Chopping Block", "WOOD")
    add("fm_well", well(), "Well", "WOOD")
    add("fm_outhouse_lo", outhouse(False), "Outhouse", "WOOD")
    add("fm_outhouse_hi", outhouse(True), "Outhouse", "WOOD")
    add("fm_girt", girt(), "Tower Brace", "METAL", rotate="axis", decor=True)
    add("fm_pump", pump(), "Windmill Pump", "METAL")
    add("fm_pump_rod", pump_rod(), "Pump Rod", "METAL", rotate=None, decor=True)
    add("fm_pipe", pipe(), "Pipe", "METAL", rotate="axis")
    add("fm_fuel_stand", fuel_stand(), "Tank Stand", "METAL", rotate="axis")
    add("fm_fuel_tank", fuel_tank(False), "Diesel Tank", "METAL", rotate="axis")
    add("fm_fuel_tank_end", fuel_tank(True), "Diesel Tank", "METAL")
    add("fm_spout", spout(), "Grain Spout", "METAL", rotate=None)
    out["fm_corn"] = {"tex": "fm_corn", "title": "Ear Corn", "sound": "GRASS"}
    add("fm_pumpkin", pumpkin(None), "Pumpkin", "WOOD")
    add("fm_pumpkin_carved", pumpkin("fm_pumpkin_face"), "Pumpkin", "WOOD")
    add("fm_pumpkin_lit", pumpkin("fm_pumpkin_lit"), "Jack o'Lantern", "WOOD", light=9)
    add("fm_char_logs", char_logs(), "Burnt Logs", "WOOD", rotate=None, decor=True)
    add("fm_bones", bones(), "Bones", "STONE", decor=True)
    add("fm_pole", pole(), "Pole", "WOOD")
    add("fm_hutch_front", hutch_piece(True), "Calf Hutch", "WOOD")
    add("fm_hutch_back", hutch_piece(False), "Calf Hutch", "WOOD")
    add("fm_silage", silage(), "Silage Cover", "WOOL", rotate=None, decor=True)
    add("fm_beam", beam(), "Log Beam", "WOOD", rotate="axis")
    add("fm_panel", panel(), "Corral Panel", "METAL", rotate="axis")
    add("fm_panel_post", panel(True), "Corral Panel", "METAL", rotate="axis")
    add("fm_stock_tank", stock_tank(), "Stock Tank", "METAL")
    add("fm_ring_feeder", ring_feeder(), "Bale Feeder", "METAL")
    add("fm_feeder", feeder(), "Poultry Feeder", "WOOD", rotate=None)
    add("fm_butcher_hook", butcher_hook(), "Butcher Hook", "METAL", rotate="axis")
    add("fm_meat_rail", meat_rail(), "Smokehouse Rail", "WOOD", rotate="axis", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    # the gambrel's flared eave: the house kit's eave piece with the sheet's edge for a rake and
    # roof boards under it, like the rest of the barn roof
    for kind in ROOF_KINDS:
        riser = f"ext_{kind}_riser"
        eave = json.loads(json.dumps(blocks_[f"ext_roof_{kind}_eave"]))
        for b in eave["boxes"]:
            if isinstance(b[6], dict):
                b[6] = {f: (riser if t.startswith("ext_rake") else "fm_boards" if t == "ext_soffit" else t) for f, t in b[6].items()}
        eave["title"] = "Barn Roof"
        made[f"fm_gam_{kind}_eave"] = eave
    for k in [k for k in blocks_ if k.startswith("fm_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} farm blocks in {path}")


if __name__ == "__main__":
    main()
