"""The yd_ kit: what a family had round its house, built to the landmarks' detail (Josiah, 2026-10-29:
"the time and effort you put into the major landmarks are only as good as the care and attention
given to the smaller parts that surround them"). world/county/Homes.java and Kit.java put them
together; the swing set, the sandbox, the scarecrow, the firewood and the burnt-out fire come from
the school's sc_ and the farm's fm_ kits.

First pass from general knowledge; the photo sets asked for (refs/suburb/picket-fences,
backyard-sheds, backyard-play, house-foundations-porches) refine it when they land.

- fences: yd_picket_{white,bare} (six pickets a block along x, the two rails behind them on +z),
  _broken (pickets kicked out, one hanging), yd_picket_post_*, yd_picket_gate_* (_open swung back
  onto +z); yd_privacy_{cedar,grey,brown} the dog-eared board fence, two blocks high (the low block
  and yd_privacy_top_*, the rails behind on +z), _broken / _top_broken with boards out, and
  yd_privacy_post_* / yd_privacy_post_top_* the 4x4 posts with their caps;
- the deck round a trailer's door: yd_deck_rail (balusters along x) and yd_deck_post;
- the trampoline: yd_tramp / yd_tramp_torn in the middle of its three by three, the frame, the
  pad and the mat reaching over the cells round it;
- the above-ground pool, steel walls with the print faded: yd_pool_edge_{blue,white} (the wall on
  the north side, the scummed water behind it), yd_pool_corner_* (the wall curving round the
  north-west corner), yd_pool_ladder_* (an edge with the A-frame ladder over it) and yd_pool_water
  / yd_pool_water_b (the middle, leaves and a ball floating);
- the dog: yd_doghouse_{red,white} (the door to the north), yd_dog_stake (the chain still on it),
  yd_dog_bowl;
- the washing line: yd_line_post (a T-post, its arm along z) and yd_line / yd_line_sheet /
  yd_line_shirts the three lines along x at the arm's height, what was left pegged out on them;
- yd_firering (the stones round a fire pit; fm_char_logs goes in the middle), yd_compost (the
  black plastic bin), yd_shed_door (a garden shed's boarded door, hung as a door).

    python3 tools/city/yard.py          rewrites the yd_ entries in blocks.json

photo_textures.py draws the textures (yard.register()).
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


# ---------------------------------------------------------------- textures

def upright(t):
    """The grain turned to run up a board or a picket."""
    for a in ("c", "h", "smooth", "f0", "alpha", "emit", "poro"):
        setattr(t, a, np.ascontiguousarray(np.swapaxes(getattr(t, a), 0, 1)))
    return t


def grey_wood(seed):
    """Fence boards left out for years: silver-grey, the grain open, black where the rain sits."""
    t = F.mat("weathered_planks", (138, 132, 120), 1.15, seed=seed)
    t.streaks(0.3, 0.7)
    t.grime(0.25, (54, 52, 46))
    return t


def cedar(seed, rgb):
    """Cedar or pine fence boards, stained once and left: the colour, the grain, rain streaks."""
    t = F.mat("raw_plank_wall", rgb, 1.2, seed=seed)
    t.streaks(0.35, 0.72)
    t.grime(0.2, (56, 44, 34))
    t.speckle(0.006, (30, 26, 20), 0.6)
    return t


def white_paint(seed):
    """White paint on pine: the grain through it, flaking to grey wood, green mould at the foot."""
    t = F.mat("wood_peeling_paint_weathered", (220, 218, 208), 0.8, seed=seed)
    yy = np.mgrid[0:N, 0:N][0].astype(np.float32)
    t.mix(np.clip((yy - 44) / 20, 0, 1) * np.clip(P.fbm(t.rng) + 0.5, 0, 1) * 0.5, (86, 100, 62))
    t.streaks(0.25, 0.8)
    return t


def tramp_mat(seed, torn):
    """The jumping mat: black polypropylene weave, sun-greyed, the stitched V-rings round its edge;
    torn, ripped through in a ragged slit the leaves blew through."""
    t = F.plain(seed, (30, 31, 33), 0.05, 0.35)
    yy, xx = np.mgrid[0:N, 0:N]
    weave = ((xx // 2 + yy // 2) % 2 == 0)
    t.c[weave] *= 1.25
    t.h[weave] += 0.08
    t.mix(np.clip(P.fbm(t.rng) * 0.9 + 0.2, 0, 1) * 0.35, (88, 88, 84))
    t.speckle(0.01, (110, 92, 50), 0.7)
    if torn:
        m = np.zeros((N, N), bool)
        x = 10.0
        for y in range(6, 58):
            x += t.rng.normal(0.7, 0.9)
            w = 2 + 3 * math.sin(y / 52 * math.pi)
            m[y, max(0, int(x - w)):min(N, int(x + w))] = True
        t.alpha[m] = 0
        frayed = np.roll(m, 1, 1) | np.roll(m, -1, 1)
        t.c[frayed & ~m] = (70, 70, 66)
    return t


def tramp_pad(seed):
    """The frame pad: blue vinyl sun-faded to grey-blue, split along its seams, the foam showing."""
    t = F.plain(seed, (64, 104, 150), 0.06, 0.5)
    t.mix(np.clip(P.fbm(t.rng) + 0.3, 0, 1) * 0.45, (150, 166, 176))
    for y in (0, 31, 32, 63):
        area(t, 0, y, N, y + 1, mul=0.7, dh=-0.2)
    for _ in range(3):
        x, y = t.rng.integers(4, N - 12, 2)
        area(t, x, y, x + int(t.rng.integers(5, 12)), y + 2, col=(222, 210, 150), dh=-0.2)
    t.grime(0.35, (60, 64, 56))
    return t


def pool_wall(seed, kind):
    """The pool's steel wall, printed outside: a faded stone-and-tile pattern on the blue, plain
    ribbed white on the other; rust bleeding from the bolts at the foot."""
    if kind == "blue":
        t = F.plain(seed, (84, 124, 152), 0.05, 0.5)
        yy, xx = np.mgrid[0:N, 0:N]
        tiles = ((xx // 8 + (yy // 8) % 2) % 2 == 0)
        t.c[tiles] *= 0.86
        area(t, 0, 4, N, 6, col=(216, 220, 214))
        area(t, 0, 58, N, 60, col=(216, 220, 214))
        t.mix(np.clip(P.fbm(t.rng) + 0.4, 0, 1) * 0.35, (170, 182, 186))
    else:
        t = F.plain(seed, (214, 214, 206), 0.04, 0.5)
        for x in range(0, N, 8):
            area(t, x, 0, x + 1, N, mul=0.82, dh=-0.2)
    t.f0[:] = 40
    for x in (4, 36):
        disc(t, x, 60, 1, col=(110, 70, 40))
        area(t, x - 1, 60, x + 1, N, col=(132, 76, 40), alpha=None)
    t.streaks(0.3, 0.8)
    t.grime(0.3, (70, 70, 56))
    return t


def pool_ledge(seed):
    t = F.plain(seed, (218, 218, 210), 0.04, 0.55)
    t.grime(0.35, (90, 92, 76))
    t.speckle(0.02, (60, 60, 50), 0.5)
    return t


def pool_water(seed, junk):
    """A summer of rain in a pool nobody's skimmed: green-black water, a scum of pollen round the
    edge, leaves floating; the odd beach ball."""
    t = F.plain(seed, (40, 58, 36), 0.1, 0.95)
    t.mix(np.clip(P.fbm(t.rng) * 1.3, 0, 1) * 0.6, (96, 104, 48), 0.7)
    t.f0[:] = 20
    t.h[:] = 0.5
    for _ in range(8 if junk else 4):
        x, y = t.rng.uniform(4, N - 4, 2)
        r = float(t.rng.uniform(1.2, 2.4))
        disc(t, x, y, r, col=tuple(int(v) for v in t.rng.choice([(120, 84, 36), (150, 110, 40), (96, 70, 30)])), dh=0.1,
             smooth=0.2)
    if junk:
        disc(t, 40, 22, 5, col=(230, 220, 200), dh=0.3, smooth=0.6)
        for a in range(6):
            cx, cy = 40 + math.cos(a) * 2.5, 22 + math.sin(a) * 2.5
            disc(t, cx, cy, 2, col=[(200, 40, 40), (40, 90, 200), (230, 190, 40)][a % 3], smooth=0.6)
    return t


def liner(seed):
    t = F.plain(seed, (70, 126, 160), 0.05, 0.6)
    t.mix(np.clip(P.fbm(t.rng) + 0.2, 0, 1) * 0.5, (70, 84, 50))
    return t


def clapboard(seed, rgb):
    """A dog house's clapboard: lapped boards a hand wide, paint flaking."""
    t = F.plain(seed, rgb, 0.05, 0.35)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, mul=0.55, dh=-0.35)
        area(t, 0, y + 1, N, y + 3, mul=1.08, dh=0.1)
    chips = P.fbm(t.rng) > 0.6
    t.c[chips] = (140, 128, 110)
    t.streaks(0.3, 0.75)
    t.grime(0.25, (60, 52, 40))
    return t


def nameplate(seed, name):
    t = F.plain(seed, (196, 170, 120), 0.04, 0.3)
    m = F.text_mask(name, F.SANS, 22, N, N, seed=seed)
    F.paste(t, m, 0, 0, (60, 40, 26))
    t.grime(0.3, (70, 60, 44))
    return t


def cord(seed):
    t = F.plain(seed, (214, 210, 198), 0.06, 0.2)
    for i in range(0, N, 3):
        area(t, i, 0, i + 1, N, mul=0.82)
    t.grime(0.3, (100, 96, 84))
    return t


def sheet(seed):
    """A bed sheet left on the line: faded blue floral on white, grey where the rain ran down it."""
    t = F.plain(seed, (222, 220, 212), 0.04, 0.15)
    for _ in range(26):
        x, y = t.rng.uniform(0, N, 2)
        disc(t, x, y, 2.2, col=(120, 150, 196))
        disc(t, x, y, 0.8, col=(220, 190, 90))
    t.streaks(0.5, 0.75)
    t.grime(0.35, (110, 104, 86))
    t.poro[:] = 180
    return t


def shirts(seed):
    """Two shirts and a towel pegged out, read off the tile's columns: plaid, a faded tee, a striped towel."""
    t = F.plain(seed, (180, 60, 50), 0.04, 0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    plaid = (xx // 4 % 2 == 0) | (yy // 4 % 2 == 0)
    t.c[(xx < 22) & plaid] *= 0.6
    area(t, 22, 0, 42, N, col=(150, 162, 120))
    area(t, 42, 0, N, N, col=(220, 214, 200))
    for y in range(4, N, 10):
        area(t, 42, y, N, y + 4, col=(70, 110, 170))
    t.streaks(0.4, 0.75)
    t.grime(0.3, (100, 96, 80))
    t.poro[:] = 180
    return t


def ash(seed):
    t = F.plain(seed, (150, 146, 140), 0.12, 0.05)
    t.speckle(0.2, (40, 38, 36), 0.7, -0.05)
    t.speckle(0.05, (220, 216, 208), 0.5)
    t.poro[:] = 200
    return t


def compost(seed):
    """The black plastic bin: moulded ribs round it, sun-greyed, the recycled logo on its side."""
    t = F.plain(seed, (34, 36, 34), 0.04, 0.45)
    for y in range(0, N, 12):
        area(t, 0, y, N, y + 2, mul=1.35, dh=0.2)
    t.mix(np.clip(P.fbm(t.rng) + 0.4, 0, 1) * 0.3, (96, 98, 92))
    disc(t, 32, 34, 6, col=(70, 74, 70), ring=1)
    t.grime(0.2, (70, 64, 50))
    return t


def shed_door(seed, rgb, top):
    """A shed's ledged-and-braced door: boards painted and peeling, the Z of the ledges and the brace
    on the inside showing through the gaps, the hasp and a padlock; the top half the same."""
    t = F.mat("distressed_painted_planks", rgb, 1.1, seed=seed)
    t = upright(t)
    for x in range(0, N, 11):
        area(t, x, 0, x + 1, N, mul=0.45, dh=-0.35)
    area(t, 0, 0, 2, N, mul=0.7)
    area(t, N - 2, 0, N, N, mul=0.7)
    if top:
        area(t, 0, 0, N, 3, mul=0.65)
        for x in (6, 52):
            area(t, x, 14, x + 7, 17, col=(40, 38, 36), dh=0.3)
    else:
        area(t, 0, N - 3, N, N, mul=0.65)
        area(t, 50, 4, 58, 7, col=(70, 70, 70), dh=0.3, smooth=0.5)
        area(t, 52, 7, 56, 12, col=(150, 120, 60), dh=0.3, smooth=0.6)
        for x in (6, 52):
            area(t, x, 46, x + 7, 49, col=(40, 38, 36), dh=0.3)
    t.grime(0.3, (60, 52, 40))
    return t


def textures():
    return {
        "yd_picket_white": lambda: upright(white_paint(49100)),
        "yd_picket_white_h": lambda: white_paint(49101),
        "yd_picket_bare": lambda: upright(grey_wood(49102)),
        "yd_picket_bare_h": lambda: grey_wood(49103),
        "yd_board_cedar": lambda: upright(cedar(49104, (128, 98, 76))),
        "yd_board_cedar_h": lambda: cedar(49105, (128, 98, 76)),
        "yd_board_grey": lambda: upright(grey_wood(49106)),
        "yd_board_grey_h": lambda: grey_wood(49107),
        "yd_board_brown": lambda: upright(cedar(49108, (92, 62, 44))),
        "yd_board_brown_h": lambda: cedar(49109, (92, 62, 44)),
        "yd_tramp_mat": lambda: tramp_mat(49110, False),
        "yd_tramp_mat_torn": lambda: tramp_mat(49111, True),
        "yd_tramp_pad": lambda: tramp_pad(49112),
        "yd_pool_blue": lambda: pool_wall(49113, "blue"),
        "yd_pool_white": lambda: pool_wall(49114, "white"),
        "yd_pool_ledge": lambda: pool_ledge(49115),
        "yd_pool_water": lambda: pool_water(49116, False),
        "yd_pool_water_b": lambda: pool_water(49117, True),
        "yd_pool_liner": lambda: liner(49118),
        "yd_clap_red": lambda: clapboard(49119, (150, 46, 36)),
        "yd_clap_white": lambda: clapboard(49120, (222, 220, 210)),
        "yd_name_max": lambda: nameplate(49121, "MAX"),
        "yd_name_buster": lambda: nameplate(49122, "BUSTER"),
        "yd_cord": lambda: cord(49123),
        "yd_sheet": lambda: sheet(49124),
        "yd_shirts": lambda: shirts(49125),
        "yd_ash": lambda: ash(49126),
        "yd_compost": lambda: compost(49127),
        "yd_shed_door_top": lambda: shed_door(49128, (150, 44, 34), True),
        "yd_shed_door_bottom": lambda: shed_door(49129, (150, 44, 34), False),
    }


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


def faces(side, up=None, north=None):
    f = {d: side for d in ("north", "south", "east", "west", "up", "down")}
    if up:
        f["up"] = f["down"] = up
    if north:
        f["north"] = north
    return f


GALV, STEEL, CHAIN = "st_galv", "painted_steel", "sc_chain"
SPOT = 2.667  # picket spacing: six a block


def picket(x, tex, top=15.0, z=7.0, lean=0):
    """One picket, 1.8 wide, pointed: the shaft and the point, a square turned 45 degrees."""
    w, s = 1.8, 1.27
    rot = turn("z", lean, (x + w / 2, 0, z)) if lean else None
    return [[x, 0.6, z, x + w, top, z + 0.7, tex, None, rot],
            [x + w / 2 - s / 2, top - s / 2, z, x + w / 2 + s / 2, top + s / 2, z + 0.7, tex, None,
             rot if lean else turn("z", 45, (x + w / 2, top, z + 0.35))]]


def picket_fence(kind, broken=False):
    v, h = "yd_picket_" + kind, "yd_picket_" + kind + "_h"
    b = [[0, 3, 7.7, 16, 5, 9.1, h], [0, 10.5, 7.7, 16, 12.5, 9.1, h]]
    gone = {1, 4} if broken else set()
    for i in range(6):
        if i in gone:
            continue
        lean = -22.5 if broken and i == 2 else 0
        b += picket(0.43 + i * SPOT, v, 15 - (0.6 if i % 2 else 0), lean=lean)
    if broken:
        # a picket hanging by its top nail
        b.append([0.43 + 4 * SPOT, 6, 6.6, 0.43 + 4 * SPOT + 1.8, 15.5, 7.2, v, None, turn("z", 22.5, (0.43 + 4 * SPOT + 0.9, 12, 7))])
    return b


def picket_post(kind):
    v = "yd_picket_" + kind
    return [[6.4, 0, 6.4, 9.6, 16.5, 9.6, v], [6, 16.5, 6, 10, 17.3, 10, v], [6.9, 17.3, 6.9, 9.1, 17.9, 9.1, v],
            [0, 3, 7.7, 6.4, 5, 9.1, v + "_h"], [9.6, 3, 7.7, 16, 5, 9.1, v + "_h"],
            [0, 10.5, 7.7, 6.4, 12.5, 9.1, v + "_h"], [9.6, 10.5, 7.7, 16, 12.5, 9.1, v + "_h"]]


def swing(box, hx=0.6, hz=8.4):
    """A box of a gate swung a quarter turn about its hinge at (hx, hz): along x becomes along +z."""
    x0, y0, z0, x1, y1, z1 = box[:6]
    out = [round(hx - (z1 - hz), 3), y0, round(hz + (x0 - hx), 3), round(hx - (z0 - hz), 3), y1, round(hz + (x1 - hx), 3)] + box[6:8]
    if len(box) > 8 and box[8]:
        r = box[8]
        ox, oy, oz = r["origin"]
        out += [None] * (8 - len(out)) + [turn("x" if r["axis"] == "z" else r["axis"], -r["angle"] if r["axis"] == "z" else r["angle"],
                                               (hx - (oz - hz), oy, hz + (ox - hx)))]
    return out


def gate(kind, open_):
    """The gate: five pickets on two ledges and the brace between them, the strap hinges at x = 0,
    the latch at x = 16; open, swung back a quarter turn onto +z about the hinge post."""
    v, h = "yd_picket_" + kind, "yd_picket_" + kind + "_h"
    b = [[0.6, 3, 7.7, 15.4, 5, 9.1, h], [0.6, 10.5, 7.7, 15.4, 12.5, 9.1, h],
         [1.2, 7.8, 7.75, 14.8, 9.4, 9.05, h, None, turn("z", 22.5, (8, 8.6, 8.4))],
         [0.2, 3.4, 9.1, 3.2, 4.6, 9.4, GALV], [0.2, 10.9, 9.1, 3.2, 12.1, 9.4, GALV],
         [13.6, 7.2, 9.1, 15.8, 8.2, 9.6, GALV]]
    for i in range(5):
        b += picket(0.9 + i * 3.0, v, 15.4)
    return [swing(x) for x in b] if open_ else b


def privacy(kind, top, broken=False):
    """Dog-eared boards, seven a block, nailed to the rails behind them on +z: the low block from the
    ground to the middle rail, the top block to 1.85 m with the dog ears and the top rail."""
    v, h = "yd_board_" + kind, "yd_board_" + kind + "_h"
    b = []
    gone = ({2, 3} if not top else {2, 3, 5}) if broken else set()
    for i in range(7):
        if i in gone:
            continue
        x0 = i * 16 / 7 + 0.04
        x1 = x0 + 16 / 7 - 0.08
        if top:
            b.append([x0, 0, 7, x1, 13.3, 7.6, v])
            b.append([x0 + 0.45, 13.3, 7, x1 - 0.45, 13.9, 7.6, v])
        else:
            b.append([x0, 0.5, 7, x1, 16, 7.6, v])
    if broken and not top:
        # one board kicked in, hanging off the low rail
        b.append([2 * 16 / 7, 1, 7.6, 3 * 16 / 7, 15, 8.2, v, None, turn("x", 22.5, (8, 15, 7.9))])
    if top:
        b.append([0, 10.2, 7.6, 16, 12.8, 9.2, h])
    else:
        b.append([0, 2.5, 7.6, 16, 5.1, 9.2, h])
    return b


def privacy_post(kind, top):
    """A 4x4 post set behind the boards, the rails butted into its sides, the boards running on across
    its face; the top block carries the post's cap."""
    v = "yd_board_" + kind
    b = [x for x in privacy(kind, top) if x[6] == v]
    b.append([6.6, 0, 7.6, 9.4, 15.6 if top else 16, 10.4, v])
    if top:
        b += [[6.2, 15.6, 7.2, 9.8, 16.3, 10.8, v], [0, 10.2, 7.6, 6.6, 12.8, 9.2, v + "_h"], [9.4, 10.2, 7.6, 16, 12.8, 9.2, v + "_h"]]
    else:
        b += [[0, 2.5, 7.6, 6.6, 5.1, 9.2, v + "_h"], [9.4, 2.5, 7.6, 16, 5.1, 9.2, v + "_h"]]
    return b


def deck_rail():
    """Balusters 2x2 along x, the 2x4 rails and the flat cap a hand wide."""
    v, h = "yd_picket_bare", "yd_picket_bare_h"
    b = [[0, 1.5, 7.2, 16, 3, 8.8, h], [0, 13.2, 7.2, 16, 14.5, 8.8, h], [0, 14.5, 6.5, 16, 15.3, 9.5, h]]
    for i in range(6):
        x = 0.9 + i * SPOT
        b.append([x, 1.5, 7.5, x + 1.1, 13.2, 8.5, v])
    return b


def deck_post():
    v = "yd_picket_bare"
    return [[6.4, 0, 6.4, 9.6, 16, 9.6, v], [5.8, 16, 5.8, 10.2, 16.8, 10.2, v]]


def octagon(r, y0, y1, tex, cx=8, cz=8):
    """An octagon r across the flats, y0 to y1: two bars crossing and the same pair turned 45 degrees."""
    a = r * math.tan(math.radians(22.5))
    bars = [[cx - r, y0, cz - a, cx + r, y1, cz + a, tex], [cx - a, y0, cz - r, cx + a, y1, cz + r, tex]]
    return bars + [bar + [None, turn("y", 45, (cx, y0, cz))] for bar in bars]


def trampoline(torn):
    """A 10-foot trampoline in the middle of its three by three: the galvanised ring on six W legs,
    the springs under the faded pad and the black mat in the middle."""
    mat = "yd_tramp_mat_torn" if torn else "yd_tramp_mat"
    b = octagon(22.2, 12.2, 13.0, "yd_tramp_pad")
    b += [[8 - 15.6, 13.0, 8 - 6.4, 8 + 15.6, 13.2, 8 + 6.4, mat], [8 - 6.4, 13.0, 8 - 15.6, 8 + 6.4, 13.2, 8 + 15.6, mat]]
    b += [m + [None, turn("y", 45, (8, 13, 8))] for m in b[-2:]]
    # the ring and the legs
    for k in range(6):
        ang = k * math.pi / 3 + math.pi / 6
        x, z = 8 + math.cos(ang) * 21.5, 8 + math.sin(ang) * 21.5
        b.append([round(x - 0.5, 2), 0, round(z - 0.5, 2), round(x + 0.5, 2), 12.2, round(z + 0.5, 2), GALV])
    # the U of the legs either side, and a foot plate under the two between
    for x in (26.6, -10.6):
        b.append([x - 0.5, 0, -2.75, x + 0.5, 0.8, 18.75, GALV])
    for z in (29.5, -13.5):
        b.append([5, 0, z - 0.5, 11, 0.8, z + 0.5, GALV])
    return b


def pool_wall_box(x0, x1, tex, z=0.0):
    """The wall along x at z (its outside on -z): the steel, the liner inside, the ledge on top."""
    return [[x0, 0, z, x1, 19, z + 0.6, faces(tex, north=tex)],
            [x0, 0.5, z + 0.6, x1, 18.6, z + 0.8, "yd_pool_liner"],
            [x0, 19, z - 1.0, x1, 19.6, z + 2.2, "yd_pool_ledge"]]


def pool_edge(c, ladder=False):
    tex = "yd_pool_" + c
    b = pool_wall_box(0, 16, tex)
    b += [[7, 0, -1.6, 9, 19, 0, "yd_pool_ledge"], [6.6, 18.4, -1.8, 9.4, 19.6, 0.4, "yd_pool_ledge"],
          [0, 0, -0.6, 16, 1.2, 0, "yd_pool_ledge"]]
    b.append([0, 15, 0.8, 16, 15.2, 16, "yd_pool_water"])
    if ladder:
        # the A-frame ladder over the wall: the rails up and over, the treads either side
        for x in (4.5, 11.5):
            b.append([x - 0.5, 0, -9, x + 0.5, 23, -8, "yd_pool_ledge", None, turn("x", -22.5, (x, 0, -8.5))])
            b.append([x - 0.5, 0, 8, x + 0.5, 23, 9, "yd_pool_ledge", None, turn("x", 22.5, (x, 0, 8.5))])
        for i, y in enumerate((4, 9, 14)):
            off = (19 - y) * math.tan(math.radians(22.5)) + 0.6
            b.append([4.5, y, -0.6 - off - 1.5, 11.5, y + 0.7, -0.6 - off + 0.5, "yd_pool_ledge"])
            b.append([4.5, y, 0.6 + off - 0.5, 11.5, y + 0.7, 0.6 + off + 1.5, "yd_pool_ledge"])
        b.append([4, 23, -1, 12, 24, 1.5, "yd_pool_ledge"])
    return b


TURN = 1  # the sense of a turn about y in the block models; flip if the curve comes out inside out


def pool_wall_z(z0, z1, tex, x=0.0):
    """The wall along z at x (its outside on -x)."""
    return [[x, 0, z0, x + 0.6, 19, z1, faces(tex)],
            [x + 0.6, 0.5, z0, x + 0.8, 18.6, z1, "yd_pool_liner"],
            [x - 1.0, 19, z0, x + 2.2, 19.6, z1, "yd_pool_ledge"]]


def pool_corner(c):
    """The wall round the north-west corner: a quarter circle about the cell's south-east corner from
    (16, 0) round to (0, 16): a short straight along x and one along z at its ends, and three chords
    between them, the first drawn along x and turned 22.5 degrees, the next turned 45, the last drawn
    along z and turned back 22.5; the water inside the curve in strips."""
    tex = "yd_pool_" + c
    r = 16.0
    b = pool_wall_box(12.9, 16, tex) + pool_wall_z(12.9, 16, tex)
    half = r * math.sin(math.radians(11.25)) + 0.3
    for th, tilt in ((247.5, 22.5), (225, 45), (202.5, 67.5)):
        cx = 16 + math.cos(math.radians(th)) * r * math.cos(math.radians(11.25))
        cz = 16 + math.sin(math.radians(th)) * r * math.cos(math.radians(11.25))
        if tilt <= 45:
            boxes, ang = pool_wall_box(cx - half, cx + half, tex, z=cz), TURN * tilt
        else:
            boxes, ang = pool_wall_z(cz - half, cz + half, tex, x=cx), -TURN * (90 - tilt)
        for s in boxes:
            b.append([round(v, 3) for v in s[:6]] + [s[6], None, turn("y", ang, (cx, 0, cz))])
    for z0, z1, x0 in ((1.6, 4, 12.0), (4, 8, 5.6), (8, 12, 2.3), (12, 16, 0.8)):
        b.append([x0, 15, z0, 16, 15.2, z1, "yd_pool_water"])
    return b


def doghouse(c):
    """A dog house a metre long, its door to the north: clapboard walls on a raised floor, the arched
    doorway, a pitched roof of shingles overhanging, the name over the door."""
    wall = "yd_clap_" + c
    tread = faces("ext_grey_tread")
    b = [[2, 0, 1, 14, 1.5, 17, "yd_picket_bare_h"],
         [2, 1.5, 16, 14, 10, 17, wall], [2, 1.5, 1, 3, 10, 16, wall], [13, 1.5, 1, 14, 10, 16, wall],
         [2, 1.5, 1, 5.2, 10, 2, wall], [10.8, 1.5, 1, 14, 10, 2, wall], [5.2, 8, 1, 10.8, 10, 2, wall],
         [5.6, 7.4, 1.1, 10.4, 8, 1.9, wall]]
    # the gable ends: stepped to the ridge
    for i, (x0, x1) in enumerate(((2.6, 13.4), (4, 12), (5.4, 10.6), (6.8, 9.2))):
        y = 10 + i * 1.4
        b.append([x0, y, 1, x1, y + 1.4, 2, wall])
        b.append([x0, y, 16, x1, y + 1.4, 17, wall])
    # the roof: two slopes at 45 degrees, the ridge along z
    half = 6 * math.sqrt(2) + 1.2
    b.append([8 - half, 15.6, 0, 8, 16.4, 18, tread, None, turn("z", 45, (8, 16.4, 9))])
    b.append([8, 15.6, 0, 8 + half, 16.4, 18, tread, None, turn("z", -45, (8, 16.4, 9))])
    b.append([7.2, 16.2, 0, 8.8, 17, 18, "ext_fascia"])
    b.append([6, 10.3, 0.7, 10, 11.7, 1.0, "yd_name_" + ("max" if c == "red" else "buster")])
    return b


def dog_stake():
    """The steel stake, the chain run out across the grass and the collar on the end of it."""
    b = [[7.3, 0, 7.3, 8.7, 3, 8.7, GALV], [6.5, 3, 6.5, 9.5, 3.6, 9.5, GALV]]
    for i in range(7):
        x = 8 + i * 1.6
        b.append([x, 0.1, 7.6 + (i % 2) * 0.2, x + 1.4, 0.5, 8.4 + (i % 2) * 0.2, CHAIN])
    b += [[18.5, 0, 6.5, 22, 0.6, 7, "fm_leather"], [18.5, 0, 9.5, 22, 0.6, 10, "fm_leather"],
          [18.5, 0, 6.5, 19, 0.6, 10, "fm_leather"], [21.5, 0, 6.5, 22, 0.6, 10, "fm_leather"]]
    return b


def dog_bowl():
    return [[4, 0, 4, 12, 2.4, 12, "home_steel"], [3.5, 2.2, 3.5, 12.5, 2.8, 12.5, "home_steel"],
            [5, 1, 5, 11, 2.3, 11, "yd_pool_water"]]


def line_post():
    """A galvanised T-post a little over two metres, the arm along z, the three hooks on it."""
    b = [[7.3, 0, 7.3, 8.7, 31, 8.7, GALV], [7.4, 29.6, -2, 8.6, 30.8, 18, GALV],
         [6.8, 0, 6.8, 9.2, 0.6, 9.2, "concrete_panel"]]
    for z in (0, 8, 16):
        b.append([7.6, 28.6, z - 0.3, 8.4, 29.6, z + 0.3, GALV])
    b += [[7.5, 26, 7.5, 8.5, 29.6, 8.5, GALV, None, turn("z", 45, (8, 29.6, 8))]]
    return b


def lines(what):
    """The three lines along x at the arm's hooks, and what's left pegged on them."""
    b = [[0, 28.8, z - 0.15, 16, 29.1, z + 0.15, "yd_cord"] for z in (0, 8, 16)]
    if what == "sheet":
        b.append([1, 13, 7.8, 15, 29, 8.2, faces("yd_sheet")])
        b.append([3, 13, 7.6, 9, 20, 8.4, "yd_sheet", None, turn("x", 22.5, (6, 20, 8))])
    elif what == "shirts":
        b.append([1, 21, 15.8, 5.4, 29, 16.2, "yd_shirts", {"north": [0, 0, 22, 64], "south": [0, 0, 22, 64]}])
        b.append([0, 25, 15.8, 6.4, 29, 16.2, "yd_shirts", {"north": [0, 0, 22, 32], "south": [0, 0, 22, 32]}])
        b.append([7, 22, 15.8, 11, 29, 16.2, "yd_shirts", {"north": [22, 0, 42, 64], "south": [22, 0, 42, 64]}])
        b.append([12, 17, -0.2, 15, 29, 0.2, "yd_shirts", {"north": [42, 0, 64, 64], "south": [42, 0, 64, 64]}])
        for x in (2, 4.4, 8, 10, 13, 14.5):
            b.append([x, 28.6, 15.7 if x < 12 else -0.3, x + 0.4, 29.6, 16.3 if x < 12 else 0.3, "yd_cord"])
    return b


def firering():
    """Eight stones round a pit, turned every way, the ash between them."""
    b = [[1.5, 0, 1.5, 14.5, 0.3, 14.5, "yd_ash"]]
    for k in range(8):
        a = k * math.pi / 4
        x, z = 8 + math.cos(a) * 6.2, 8 + math.sin(a) * 6.2
        w = 2.4 + (k % 3) * 0.4
        stone = [round(x - w, 2), 0, round(z - 1.6, 2), round(x + w, 2), 2.2 + (k % 2) * 0.8, round(z + 1.6, 2), "pt_stone_rough"]
        ang = (-45, -22.5, 22.5, 45)[k % 4]
        b.append(stone + [None, turn("y", ang, (round(x, 2), 0, round(z, 2)))])
    return b


def compost_bin():
    return [[3, 0, 3, 13, 12, 13, "yd_compost"], [3.6, 12, 3.6, 12.4, 13.6, 12.4, "yd_compost"],
            [3.2, 13.6, 3.2, 12.8, 14.4, 12.8, faces("yd_compost", up="home_plastic_black")],
            [6.5, 14.4, 7.4, 9.5, 15.2, 8.6, "home_plastic_black"],
            [5, 1, 2.6, 11, 5, 3, "home_plastic_black"]]


# ---------------------------------------------------------------- blocks

SIDES = {"north": "west", "south": "east", "east": "north", "west": "south"}


def along_z(boxes):
    """RULE (paint_textures.along_z): an axis model is drawn along z. These are drawn along x, so turn
    each box a quarter, x -> 16 - z: faces and uv rects follow, a turn about z becomes one about x
    and a turn about x one about z the other way."""
    out = []
    for b in boxes:
        x0, y0, z0, x1, y1, z1 = b[:6]
        nb = [z0, y0, round(16 - x1, 3), z1, y1, round(16 - x0, 3)]
        tex = b[6]
        if isinstance(tex, dict):
            tex = {SIDES.get(k, k): v for k, v in tex.items()}
        nb.append(tex)
        if len(b) > 7:
            uv = b[7]
            nb.append({SIDES.get(k, k): v for k, v in uv.items()} if isinstance(uv, dict) else uv)
        if len(b) > 8 and b[8]:
            r = b[8]
            ox, oy, oz = r["origin"]
            axis, ang = {"z": ("x", r["angle"]), "x": ("z", -r["angle"]), "y": ("y", r["angle"])}[r["axis"]]
            nb.append(turn(axis, ang, (oz, oy, 16 - ox)))
        out.append(nb)
    return out


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, light=None, loot=None, **extra):
        if rotate == "axis":
            boxes = along_z(boxes)
            if "shape" in extra:
                extra["shape"] = [q[:6] for q in along_z([list(r) + [None] for r in extra["shape"]])]
        spec = {"model": "boxes", "boxes": boxes} if boxes else {}
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

    for k in ("white", "bare"):
        add("yd_picket_" + k, picket_fence(k), "Picket Fence", "WOOD", rotate="axis", shape=[[0, 0, 7, 16, 16, 9.1]])
        add("yd_picket_" + k + "_broken", picket_fence(k, True), "Picket Fence", "WOOD", rotate="axis", shape=[[0, 0, 7, 16, 16, 9.1]])
        add("yd_picket_post_" + k, picket_post(k), "Fence Post", "WOOD", rotate="axis", shape=[[6.4, 0, 6.4, 9.6, 17.9, 9.6]])
        add("yd_picket_gate_" + k, gate(k, False), "Gate", "WOOD", rotate="axis", shape=[[0, 0, 7, 16, 16, 9.6]])
        add("yd_picket_gate_" + k + "_open", gate(k, True), "Gate", "WOOD", rotate="axis", decor=True)
    for k in ("cedar", "grey", "brown"):
        add("yd_privacy_" + k, privacy(k, False), "Board Fence", "WOOD", rotate="axis", shape=[[0, 0, 7, 16, 16, 9.2]])
        add("yd_privacy_top_" + k, privacy(k, True), "Board Fence", "WOOD", rotate="axis", shape=[[0, 0, 7, 16, 14, 9.2]])
        add("yd_privacy_" + k + "_broken", privacy(k, False, True), "Board Fence", "WOOD", rotate="axis",
            shape=[[0, 0, 7, 16, 16, 9.2]])
        add("yd_privacy_top_" + k + "_broken", privacy(k, True, True), "Board Fence", "WOOD", rotate="axis",
            shape=[[0, 0, 7, 16, 14, 9.2]])
        add("yd_privacy_post_" + k, privacy_post(k, False), "Fence Post", "WOOD", rotate="axis", shape=[[0, 0, 7, 16, 16, 10.4]])
        add("yd_privacy_post_top_" + k, privacy_post(k, True), "Fence Post", "WOOD", rotate="axis",
            shape=[[0, 0, 7, 16, 16, 10.4]])
    add("yd_deck_rail", deck_rail(), "Deck Rail", "WOOD", rotate="axis", shape=[[0, 0, 6.5, 16, 15.3, 9.5]])
    add("yd_deck_post", deck_post(), "Deck Post", "WOOD", rotate=None, shape=[[6.4, 0, 6.4, 9.6, 16.8, 9.6]])
    add("yd_tramp", trampoline(False), "Trampoline", "WOOL", rotate=None, shape=[[0, 0, 0, 16, 13.2, 16]])
    add("yd_tramp_torn", trampoline(True), "Trampoline", "WOOL", rotate=None, shape=[[0, 0, 0, 16, 13.2, 16]],
        render="cutout")
    for c in ("blue", "white"):
        add("yd_pool_edge_" + c, pool_edge(c), "Pool", "METAL", shape=[[0, 0, 0, 16, 19.6, 1]])
        add("yd_pool_corner_" + c, pool_corner(c), "Pool", "METAL", shape=[[0, 0, 0, 16, 19.6, 1], [0, 0, 0, 1, 19.6, 16]])
        add("yd_pool_ladder_" + c, pool_edge(c, True), "Pool", "METAL", shape=[[0, 0, 0, 16, 19.6, 1]])
    add("yd_pool_water", [[0, 15, 0, 16, 15.2, 16, "yd_pool_water"]], "Pool Water", "GRASS", rotate=None, decor=True)
    add("yd_pool_water_b", [[0, 15, 0, 16, 15.2, 16, "yd_pool_water_b"]], "Pool Water", "GRASS", rotate=None, decor=True)
    for c in ("red", "white"):
        add("yd_doghouse_" + c, doghouse(c), "Dog House", "WOOD", shape=[[2, 0, 1, 14, 16, 16]])
    add("yd_dog_stake", dog_stake(), "Dog Stake", "METAL", decor=True)
    add("yd_dog_bowl", dog_bowl(), "Dog Bowl", "METAL", decor=True)
    add("yd_line_post", line_post(), "Washing Line Post", "METAL", rotate="axis", shape=[[7.3, 0, 7.3, 8.7, 16, 8.7]])
    for v in ("", "_sheet", "_shirts"):
        add("yd_line" + v, lines(v[1:]), "Washing Line", "WOOL", rotate="axis", decor=True)
    add("yd_firering", firering(), "Fire Pit", "STONE", rotate=None, shape=[[1, 0, 1, 15, 2, 15]])
    add("yd_compost", compost_bin(), "Compost Bin", "STONE", shape=[[3, 0, 3, 13, 15, 13]])
    out["yd_shed_door"] = {"door": "wood", "tex": {"top": "yd_shed_door_top", "bottom": "yd_shed_door_bottom"}, "title": "Shed Door"}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("yd_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} yard blocks in {path}")


if __name__ == "__main__":
    main()
