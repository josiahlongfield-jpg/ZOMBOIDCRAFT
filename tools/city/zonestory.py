"""The zs_ kit: what Zomboid's zone stories leave out in the county (world/county/ZoneStories), drawn
from the photos in refs/stories/zone (forest-camp, hunter-camp, trapper-camp, burying-camp,
fishing-trip, bbq-party, beach-party, music-fest, baseball).

- zs_tent_<c>_door_l|_r, _open_l|_r, _end_l|_r: a two-person ridge tent in green, blue or orange
  nylon, two blocks wide and two long, a block high at the ridge (refs: forest-camp 01, 03, 05).
  It faces the way its door does: the door pair is the north row, _l the west block when it faces
  north, the end pair behind; _open has the door rolled back on a sleeping bag. _down is one block
  of a tent that has come down, the canvas heaped over its bent poles;
- zs_rod: a rod propped on a forked stick, pointing out over the water (rotate=facing);
  zs_rod_down lying in the grass; zs_tackle: the tackle box open on its trays (searchable,
  loot=story_fishing_trip); zs_stringer: the catch on a cord (refs: fishing-trip 03, 05);
- zs_trap: a wire cage trap, zs_snare: a wire snare set on its stake; zs_hide_rack: a hide
  stretched on a frame of poles, leaning back; zs_hoop: a pelt laced into a willow hoop
  (refs: trapper-camp 05, 07, 08); zs_lean / zs_lean_mid: a tarp lean-to on poles, open to the
  north, posts at both sides or none (refs: trapper-camp 01-03);
- zs_deer_a / zs_deer_b: a shot deer lying on its side, hind and fore (b to the north);
  zs_gun_case: a soft rifle case (searchable, loot=story_hunter_camp) (refs: hunter-camp);
- zs_spoil (a heap of dug earth with the shovel stuck in it), zs_spoil_low, zs_grave_a / _b: the
  two ends of a grave dug a block down, laid at the ground's own level (refs: burying-camp);
- zs_cans: empty beer cans; zs_ruck_<forest|hunter|trapper|burying>: a camping rucksack
  (searchable, loot=story_<x>_camp); zs_tote (loot=story_beach_party); zs_cooler
  (loot=story_bbq_party); zs_fest_pack (loot=story_music_fest); zs_kitbag (loot=story_baseball);
- zs_base, zs_home, zs_rubber, zs_bat, zs_glove, zs_bench (axis), zs_fence / zs_fence_top: a
  sandlot diamond and its backstop (refs: baseball 01, 03, 07); zs_log: a log to sit on (axis).

    python3 tools/city/zonestory.py      rewrites the zs_ entries in blocks.json

photo_textures.py draws the textures (zonestory.register()).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from bunker import chips, stencil  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BARK, POLE, GALV = "tree_oak_bark", "painted_steel", "st_galv"
COLOURS = {"green": (62, 96, 54), "blue": (38, 82, 150), "orange": (204, 98, 32)}
RUCKS = {"forest": (168, 40, 34), "hunter": (88, 92, 58), "trapper": (122, 92, 60), "burying": (70, 84, 104)}


# ---------------------------------------------------------------- textures

def nylon(seed, rgb):
    """Tent nylon: the ripstop grid, a seam, the dirt splashed up from the ground at the bottom."""
    t = F.plain(seed, rgb, 0.06, 0.45)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[(xx % 8 == 0) | (yy % 8 == 0)] *= 0.9
    t.c[30:32] *= 0.78
    t.h[30:32] += 0.05
    dirt = np.clip((yy - 44) / 20.0, 0, 1) * np.clip(P.fbm(t.rng) + 0.7, 0, 1)
    t.mix(dirt, (70, 58, 42), 0.55)
    t.grime(0.18, (60, 56, 46))
    return t


def gable(seed, rgb, door, open_):
    """The left half of a tent's end, as seen from outside: the ridge at the left edge, the cloth
    under the slope (row >= column); the door's zip round half the arch, or the door rolled away."""
    t = nylon(seed, rgb)
    yy, xx = np.mgrid[0:N, 0:N]
    keep = yy >= xx
    t.c[(yy - xx >= 0) & (yy - xx < 3)] *= 0.7       # the seam along the slope
    if door:
        # the door: half an arch from the ridge out to x = 20, its zip a dark line round it
        def arch(w, top):
            return (xx < w) & ((yy >= 40) | ((xx / w) ** 2 + (np.maximum(40 - yy, 0) / (40 - top)) ** 2 < 1))
        inside = arch(20, 18)
        edge = inside & ~arch(18, 20)
        if open_:
            keep &= ~(inside & ~edge)
        else:
            t.c[inside] *= 0.92
        t.c[edge] = (30, 30, 32)
    t.alpha = np.where(keep, 255, 0).astype(np.float32)
    return t


def sleeping(seed):
    """A sleeping bag: navy shell quilted in bands, the red lining showing at the turned-back top."""
    t = F.plain(seed, (40, 52, 90), 0.06, 0.4)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[yy % 10 < 1] *= 0.6
    t.h -= (np.abs((yy % 10) - 5) / 5.0) * 0.08
    t.c[:10] = t.c[:10] * 0.3 + np.array((170, 40, 36), np.float32) * 0.7
    t.grime(0.2)
    return t


def plastic(seed, rgb, gloss=0.55):
    t = F.plain(seed, rgb, 0.04, gloss)
    t.grime(0.2, (50, 50, 44))
    chips(t, 0.03, under=tuple(int(v * 0.7) for v in rgb))
    return t


def tray(seed):
    """The tackle box's trays: compartments of lures, hooks, sinkers and bobbers."""
    t = F.plain(seed, (190, 186, 170), 0.04, 0.5)
    for y in range(0, N, 16):
        t.c[y:y + 2] *= 0.6
    for x in range(0, N, 21):
        t.c[:, x:x + 2] *= 0.6
    rng = t.rng
    for _ in range(40):
        x, y = rng.integers(2, N - 4, 2)
        col = [(200, 40, 30), (230, 200, 40), (240, 240, 236), (60, 120, 60), (160, 160, 168), (220, 110, 30)][rng.integers(6)]
        t.c[y:y + 2 + rng.integers(3), x:x + 2] = col
    return t


def rod(seed):
    t = F.plain(seed, (28, 28, 32), 0.04, 0.7)
    for y in range(4, N, 14):
        t.c[y:y + 2] = (170, 40, 30)
    return t


def fish(seed):
    """A trout's side: dark speckled back, the pink stripe, a pale belly; scales catching light."""
    t = F.plain(seed, (150, 156, 150), 0.05, 0.7)
    yy, _ = np.mgrid[0:N, 0:N]
    t.c[yy < 20] = t.c[yy < 20] * 0.35 + np.array((50, 70, 50), np.float32) * 0.65
    band = (yy >= 26) & (yy < 34)
    t.c[band] = t.c[band] * 0.5 + np.array((200, 110, 110), np.float32) * 0.5
    t.c[yy > 44] = t.c[yy > 44] * 0.5 + np.array((228, 226, 214), np.float32) * 0.5
    t.speckle(0.05, (20, 24, 20), 0.7)
    return t


def hide(seed):
    """The flesh side of a stretched hide, scraped and drying: cream to tan, darker veins and edges."""
    t = F.plain(seed, (204, 178, 136), 0.12, 0.25)
    t.mix(np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6))) * 1.4, 0, 1), (150, 104, 70), 0.6)
    t.crack(6, 0.05, (130, 80, 60))
    yy, xx = np.mgrid[0:N, 0:N]
    rim = np.minimum(np.minimum(xx, N - 1 - xx), np.minimum(yy, N - 1 - yy)) < 4
    t.c[rim] *= 0.7
    return t


def grave_wall(seed):
    """A grave's cut side: dark topsoil, then the clay and the orange subsoil in bands (refs 02)."""
    t = F.plain(seed, (88, 66, 46), 0.15, 0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    wob = np.sin(xx / 9.0 + seed) * 2
    t.mix(((yy + wob) < 12).astype(np.float32), (44, 34, 26), 0.8)
    t.mix((((yy + wob) > 26) & ((yy + wob) < 34)).astype(np.float32), (150, 70, 40), 0.55)
    t.mix(((yy + wob) > 44).astype(np.float32), (140, 120, 90), 0.4)
    t.speckle(0.04, (120, 116, 108), 0.6, 0.1)
    return t


def can(seed):
    t = F.plain(seed, (176, 30, 30), 0.04, 0.7)
    t.c[24:40] = (210, 210, 214)
    stencil(t, "BREW", (176, 30, 30), 12, 4, 24, N - 8, 16, chip=0.1)
    return t


def ruck(seed, rgb):
    """A pack's cordura: the weave, black webbing straps down it, scuffs."""
    t = F.plain(seed, rgb, 0.06, 0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) % 3) == 0] *= 0.94
    for x in (14, 46):
        t.c[:, x:x + 5] = (26, 26, 28)
        t.c[20:24, x - 1:x + 6] = (120, 120, 116)  # the buckles
    t.grime(0.25, (60, 52, 40))
    return t


def tote(seed):
    t = F.plain(seed, (236, 232, 220), 0.04, 0.3)
    _, xx = np.mgrid[0:N, 0:N]
    t.c[(xx // 8) % 2 == 1] = (40, 110, 180)
    t.grime(0.15)
    return t


def kitbag(seed):
    t = F.plain(seed, (30, 38, 70), 0.05, 0.3)
    stencil(t, "RIVERDOGS", (236, 232, 220), 12, 2, 20, N - 4, 22, chip=0.2)
    t.grime(0.2, (90, 80, 60))
    return t


def fest(seed):
    """A black daypack covered in band patches and badges."""
    t = F.plain(seed, (30, 30, 32), 0.05, 0.3)
    rng = t.rng
    for _ in range(6):
        x, y = rng.integers(4, N - 16, 2)
        col = [(200, 40, 40), (230, 200, 40), (60, 120, 200), (230, 230, 226), (120, 40, 140)][rng.integers(5)]
        t.c[y:y + 10, x:x + 12] = col
        t.c[y + 3:y + 6, x + 2:x + 10] *= 0.5
    t.grime(0.2)
    return t


def bat_wood(seed):
    t = F.plain(seed, (206, 170, 118), 0.05, 0.55)
    yy, _ = np.mgrid[0:N, 0:N]
    t.c *= (1 + 0.05 * np.sin(yy / 2.5 + P.fbm(t.rng) * 3))[..., None]
    t.c[:, :6] = (30, 30, 30)    # the tape on the handle
    return t


def gun_case(seed):
    t = F.plain(seed, (86, 88, 56), 0.06, 0.25)
    t.c[30:33] = (24, 24, 24)    # the zip
    t.c[2:6] *= 0.6
    t.c[-6:-2] *= 0.6
    t.grime(0.25)
    return t


def textures():
    out = {}
    for i, (c, rgb) in enumerate(COLOURS.items()):
        out[f"zs_nylon_{c}"] = (lambda s, rgb: lambda: nylon(s, rgb))(89000 + i * 10, rgb)
        out[f"zs_gable_{c}"] = (lambda s, rgb: lambda: gable(s, rgb, False, False))(89001 + i * 10, rgb)
        out[f"zs_door_{c}"] = (lambda s, rgb: lambda: gable(s, rgb, True, False))(89002 + i * 10, rgb)
        out[f"zs_dooropen_{c}"] = (lambda s, rgb: lambda: gable(s, rgb, True, True))(89003 + i * 10, rgb)
    for i, (k, rgb) in enumerate(RUCKS.items()):
        out[f"zs_ruck_{k}"] = (lambda s, rgb: lambda: ruck(s, rgb))(89100 + i, rgb)
    out.update({
        "zs_sleeping": lambda: sleeping(89200),
        "zs_tackle": lambda: plastic(89201, (52, 104, 66)),
        "zs_tray": lambda: tray(89202),
        "zs_rod": lambda: rod(89203),
        "zs_fish": lambda: fish(89204),
        "zs_hide": lambda: hide(89205),
        "zs_grave_wall": lambda: grave_wall(89206),
        "zs_can": lambda: can(89207),
        "zs_tote": lambda: tote(89208),
        "zs_cooler_red": lambda: plastic(89209, (178, 34, 30)),
        "zs_kitbag": lambda: kitbag(89210),
        "zs_fest": lambda: fest(89211),
        "zs_bat_wood": lambda: bat_wood(89212),
        "zs_gun_case": lambda: gun_case(89213),
    })
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def turn(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": [round(v, 3) for v in origin]}


SQ = round(16 * math.sqrt(2), 3)
PANEL_UV = {"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], "north": [0, 0, 16, 0.5], "south": [0, 0, 16, 0.5],
            "east": [0, 0, 0.5, 16], "west": [0, 0, 0.5, 16]}


def disc_strips(r, cx, cy, step=2.0):
    """Horizontal strips filling a circle of radius r round (cx, cy): (y0, y1, x0, x1)."""
    out = []
    y = cy - r
    while y < cy + r - 0.01:
        y1 = min(cy + r, y + step)
        m = (y + y1) / 2 - cy
        h = math.sqrt(max(0.0, r * r - m * m))
        out.append((round(y, 2), round(y1, 2), round(cx - h, 2), round(cx + h, 2)))
        y = y1
    return out


def tent(c, part, side):
    """One block of the tent: part door, open or end; side l (the ridge at its east edge when the
    tent faces north) or r. The slope at 45 degrees, the end cloth cut to it, the groundsheet."""
    ny = f"zs_nylon_{c}"
    left = side == "l"
    if left:
        b = [[0, 0, 0, SQ, 0.5, 16, ny, PANEL_UV, turn("z", 45, (0, 0, 8))]]
    else:
        b = [[16 - SQ, 0, 0, 16, 0.5, 16, ny, PANEL_UV, turn("z", -45, (16, 0, 8))]]
    b.append([0, 0, 0, 16, 0.2, 16, "tn_groundsheet"])
    out_uv, in_uv = ([0, 0, 16, 16], [16, 0, 0, 16]) if left else ([16, 0, 0, 16], [0, 0, 16, 16])
    ridge = 15.4 if left else 0
    if part in ("door", "open"):
        tex = f"zs_door_{c}" if part == "door" else f"zs_dooropen_{c}"
        b.append([0, 0, 0, 16, 16, 0.3, tex, {"north": out_uv, "south": in_uv}])
        b.append([ridge, 0, -0.7, ridge + 0.6, 15.6, -0.1, POLE])              # the front pole
        b.append([ridge, 15.2, -1.4, ridge + 0.6, 15.8, 0.2, POLE])            # the ridge's end
        b.append([0.3 if left else 15.1, 0, -0.4, 0.9 if left else 15.7, 1.2, 0.2, GALV])   # a peg
        if part == "open":
            x0 = 6.6 if left else 7.6
            b.append([x0, 0.3, -0.9, x0 + 1.8, 7.4, 0.3, ny])                   # the door rolled and tied
            b.append([9 if left else 0.4, 0.2, 1.5, 15.6 if left else 7, 2.2, 15.8, "zs_sleeping"])
    else:
        tex = f"zs_gable_{c}"
        b.append([0, 0, 15.7, 16, 16, 16, tex, {"north": out_uv, "south": in_uv}])
        b.append([ridge, 0, 16.1, ridge + 0.6, 15.6, 16.7, POLE])
        b.append([0.3 if left else 15.1, 0, 15.8, 0.9 if left else 15.7, 1.2, 16.4, GALV])
    return b


def tent_down(c):
    """A tent that has come down: the cloth heaped over its poles, one pole still standing bent."""
    ny = f"zs_nylon_{c}"
    return [[0.5, 0, 1, 15.5, 1, 15, ny], [2, 1, 3, 9, 2.8, 10, ny], [7, 1, 6, 14, 2.2, 13.5, ny],
            [3, 1, 9, 8, 1.6, 14, ny],
            [1, 0.4, 7.6, 17, 1, 8.2, POLE, None, turn("y", 22.5, (8, 0, 8))],
            [7.7, 1, 7.7, 8.3, 11, 8.3, POLE, None, turn("x", 22.5, (8, 1, 8))]]


def rod_model():
    """The rod on its forked stick, the butt in the grass, the tip out over the water (north)."""
    r = turn("x", 22.5, (8, 0.5, 15))
    return [[7.7, 0.5, -8, 8.3, 1.1, 15, "zs_rod", None, r],
            [7.6, 0, 4.2, 8.4, 4.4, 5, BARK], [6.8, 3.6, 4.2, 7.5, 5.6, 5, BARK], [8.5, 3.6, 4.2, 9.2, 5.6, 5, BARK],
            [8.3, 0.8, 11.2, 9.8, 2.4, 12.6, "home_plastic_black"], [9.8, 1.4, 11.7, 10.6, 1.8, 12.1, "hw_chrome"]]


def rod_down():
    r = turn("y", 45, (8, 0, 8))
    return [[0, 0, 7.7, 16, 0.6, 8.3, "zs_rod", None, r], [10, 0, 8.3, 11.5, 1.4, 9.8, "home_plastic_black", None, r]]


def tackle():
    """The box open: lid back, the two cantilever trays out on their arms."""
    body = {"side": "zs_tackle", "up": "zs_tray", "down": "zs_tackle"}
    tr = {"side": "zs_tackle", "up": "zs_tray", "down": "zs_tackle"}
    return [[3, 0, 5, 13, 4.5, 12, body], [3, 4.5, 11.6, 13, 9.5, 12.3, "zs_tackle"],
            [3.4, 4.5, 3.4, 12.6, 5.8, 6.4, tr], [3.4, 5.4, 6.6, 12.6, 6.8, 9.6, tr],
            [3, 4.5, 6, 3.4, 6.2, 9, "hw_chrome"], [12.6, 4.5, 6, 13, 6.2, 9, "hw_chrome"]]


def stringer():
    f = "zs_fish"
    return [[1, 0, 2, 1.6, 0.3, 14, "yd_cord"], [1.6, 0, 3, 11, 1.6, 5, f], [11, 0, 3.4, 13, 1.2, 4.6, f],
            [1.6, 0, 7.5, 13, 1.8, 9.9, f], [13, 0, 8, 15, 1.3, 9.4, f], [1.6, 0, 11.5, 9.5, 1.4, 13.2, f],
            [9.5, 0, 11.8, 11, 1, 12.9, f]]


def trap():
    """A wire cage trap, its door end north, the trip plate inside, a carry handle on top."""
    return [[5, 0, 1, 11, 6, 15, "chainlink"], [4.8, 0, 0.8, 11.2, 0.4, 15.2, GALV],
            [4.8, 5.8, 0.8, 5.2, 6.2, 15.2, GALV], [10.8, 5.8, 0.8, 11.2, 6.2, 15.2, GALV],
            [4.8, 0, 0.8, 11.2, 6.2, 1.2, "chainlink"], [6, 0.4, 9, 10, 0.8, 13, GALV],
            [7.5, 6.2, 7.6, 8.5, 7.6, 8.4, GALV]]


def snare():
    return [[3, 0, 7.5, 13, 0.8, 8.5, BARK, None, turn("y", 22.5, (8, 0, 8))], [7.5, 0, 9, 8.5, 7, 10, BARK],
            [5, 1, 9.6, 11, 1.3, 9.9, GALV], [5, 5, 9.6, 11, 5.3, 9.9, GALV], [5, 1, 9.6, 5.3, 5.3, 9.9, GALV],
            [10.7, 1, 9.6, 11, 5.3, 9.9, GALV]]


def hide_rack():
    """Poles lashed in a square, leaning back, the hide laced into it flesh side out (north)."""
    r = turn("x", -22.5, (8, 0, 9.5))
    skin = {"north": "zs_hide", "south": "mu_fur_deer", "side": "mu_fur_deer"}
    return [[1, 0, 9, 2, 16, 10, BARK, None, r], [14, 0, 9, 15, 16, 10, BARK, None, r],
            [0, 13.5, 9, 16, 14.5, 10, BARK, None, r], [0, 1.5, 9, 16, 2.5, 10, BARK, None, r],
            [2.6, 3, 9.2, 13.4, 13, 9.8, skin, None, r]]


def hoop():
    """A pelt laced into a bent willow hoop, leaning back against its prop."""
    r = turn("x", -22.5, (8, 0, 9))
    b = []
    for y0, y1, x0, x1 in disc_strips(6.5, 7.5, 8, step=1.5):
        b.append([x0 + 1, y0, 8.6, x1 - 1, y1, 9.2, {"north": "mu_fur_fox", "south": "zs_hide", "side": "zs_hide"}, None, r])
        b.append([x0, y0, 8.5, x0 + 1, y1, 9.3, BARK, None, r])
        b.append([x1 - 1, y0, 8.5, x1, y1, 9.3, BARK, None, r])
    b.append([7.5, 0, 11, 8.5, 9, 12, BARK, None, turn("x", 22.5, (8, 0, 11.5))])
    return b


def lean(posts):
    """The tarp from the ridge pole at the front down to the ground at the back, a bed of boughs
    under it; posts at the block's two sides (a run of them: posts at its ends only)."""
    b = [[0, 0, -4.8, 16, 0.4, 15, "tarp_green", {"up": [0, 0, 16, 16], "down": [0, 0, 16, 16], "north": [0, 0, 16, 0.4],
                                                     "south": [0, 0, 16, 0.4], "east": [0, 0, 0.4, 16], "west": [0, 0, 0.4, 16]},
          turn("x", 45, (8, 0, 15))],
         [0, 13, -0.4, 16, 14, 0.6, BARK], [0, 0, 3, 16, 0.8, 13, "tree_oak_leaves"]]
    if posts:
        b += [[0.3, 0, -0.6, 1.3, 13.6, 0.4, BARK], [14.7, 0, -0.6, 15.7, 13.6, 0.4, BARK]]
    return b


def deer(part):
    """A deer down on its side, head to the north: b is the fore half (z 0..16 of a 32-long
    frame), a the hind half. Its legs stick out east; the antlers lie in the grass."""
    fur = "mu_fur_deer"
    full = [[x0, y0, 6, x1, y1, 26, fur] for y0, y1, x0, x1 in disc_strips(4, 8, 4, step=1.6)]   # the barrel
    full += [[5.5, 0.5, 0, 10.5, 5, 7, fur],                             # the neck
             [5, 0.4, -5, 10, 4.6, 1, fur], [6, 0.4, -8, 9, 3.2, -5, fur], [6.6, 1, -8.4, 8.4, 2.6, -8, "home_plastic_black"],
             [4, 4.2, -2, 5.5, 5.2, 0.5, fur], [9.5, 4.2, -2, 11, 5.2, 0.5, fur],      # the ears
             [11.5, 1, 8, 22, 2, 9, fur], [11.5, 2.4, 10.2, 21, 3.4, 11.2, fur],       # the forelegs
             [11.5, 1, 21, 22.5, 2.2, 22.2, fur], [11.5, 2.6, 23.4, 21.5, 3.8, 24.6, fur],  # the hind legs
             [21.5, 0.9, 7.8, 23, 2.1, 9.4, "home_plastic_black"], [22, 0.9, 20.8, 23.5, 2.3, 22.4, "home_plastic_black"],
             [6, 1.5, 26, 9, 3.5, 28, fur],                                # the tail
             [3, 1, -6, 4, 2, 1, BARK], [1, 1, -4, 3, 2, -3, BARK], [11, 1, -6, 12, 2, 1, BARK], [12, 1, -4, 14, 2, -3, BARK]]
    out = []
    lo, hi = (-8, 16) if part == "b" else (16, 34)
    for bx in full:
        z0, z1 = max(bx[2], lo), min(bx[5], hi)
        if z1 > z0:
            off = 0 if part == "b" else 16
            out.append([bx[0], bx[1], z0 - off, bx[3], bx[4], z1 - off, bx[6]])
    return out


def gun_case_model():
    return [[0.5, 0, 6, 15.5, 2.6, 10.4, "zs_gun_case"], [6, 2.6, 7.6, 10, 3.2, 8.8, "home_plastic_black"],
            [15.5, 0.4, 6.8, 16, 2.2, 9.6, "zs_gun_case"]]


def spoil(shovel):
    b = [[0, 0, 0, 16, 4, 16, "ch_dirt"], [2, 4, 2, 14, 7, 13, "ch_dirt"], [4, 7, 4, 11, 9, 10, "ch_dirt"]]
    if not shovel:
        return [[0, 0, 0, 16, 3, 16, "ch_dirt"], [3, 3, 3, 13, 5, 12, "ch_dirt"]]
    r = turn("x", 22.5, (8, 7, 8))
    b += [[7.6, 9, 7.6, 8.4, 20, 8.4, "fm_split", None, r], [6, 5, 7.6, 10, 9.5, 8.2, "rust", None, r],
          [6.5, 19.5, 7.5, 9.5, 20.6, 8.5, "home_plastic_black", None, r]]
    return b


def grave(end):
    """A grave dug a block down, two blocks long: a is the head end (its wall north), b the foot."""
    w = "zs_grave_wall"
    b = [[0, 0, 0, 1.5, 16, 16, w], [14.5, 0, 0, 16, 16, 16, w], [1.5, 0, 0, 14.5, 0.6, 16, "ch_dirt"]]
    b.append([1.5, 0, 0, 14.5, 16, 1.5, w] if end == "a" else [1.5, 0, 14.5, 14.5, 16, 16, w])
    return b


def cans():
    c = {"side": "zs_can", "up": "hw_chrome", "down": "hw_chrome"}
    return [[3, 0, 3, 4.4, 2.4, 4.4, c], [8, 0, 9, 10.6, 1.3, 10.3, c, None, turn("y", 22.5, (9, 0, 9.6))],
            [11, 0, 4, 13, 0.6, 6, c], [4, 0, 11, 6.6, 1.3, 12.3, c, None, turn("y", -45, (5, 0, 11.6))],
            [12, 0, 12, 13.4, 2.4, 13.4, c]]


def ruck_model(k):
    t = f"zs_ruck_{k}"
    return [[4, 0, 5.5, 12, 11, 11, t], [3.8, 9.5, 5, 12.2, 12.5, 11.4, t], [5, 1, 4.5, 11, 6.5, 5.5, t],
            [5, 2, 11, 6, 10.5, 12, "home_plastic_black"], [10, 2, 11, 11, 10.5, 12, "home_plastic_black"],
            [3, 0, 4.2, 13, 3, 7, "zs_sleeping"]]


def tote_model():
    return [[3, 0, 6, 13, 8, 10, "zs_tote"], [4.5, 8, 7.6, 5.5, 11, 8.4, "yd_cord"], [10.5, 8, 7.6, 11.5, 11, 8.4, "yd_cord"],
            [5.5, 10.4, 7.6, 10.5, 11, 8.4, "yd_cord"], [6, 7, 6.6, 10, 9.4, 9.4, "sb_towel_a"]]


def cooler_model():
    return [[2, 0, 4, 14, 8, 12, "zs_cooler_red"], [1.8, 8, 3.8, 14.2, 9.6, 12.2, "hw_white"],
            [0.8, 5, 7, 2, 6, 9, "hw_white"], [14, 5, 7, 15.2, 6, 9, "hw_white"]]


def fest_model():
    return [[3, 0, 4, 13, 5, 12, "zs_fest"], [4, 5, 5, 12, 6, 11, "zs_fest"], [1, 0, 6, 3, 0.6, 7, "home_plastic_black"],
            [13, 0, 9, 15, 0.6, 10, "home_plastic_black"]]


def kitbag_model():
    return [[1, 0, 4.5, 15, 4.5, 10.5, "zs_kitbag"], [6, 4.5, 7, 10, 5.1, 8, "home_plastic_black"],
            [15, 1.5, 6, 16, 3, 9, "zs_kitbag"]]


def bat():
    r = turn("y", 45, (8, 0, 8))
    return [[0.5, 0, 7.5, 16, 1.0, 8.5, "zs_bat_wood", None, r], [9, 0, 7.2, 16.5, 1.6, 8.8, "zs_bat_wood", None, r],
            [-0.2, 0, 7.2, 0.6, 1.4, 8.8, "zs_bat_wood", None, r]]


def glove():
    lea = "fm_leather"
    return [[5, 0, 5, 11, 2.2, 10.5, lea], [5.3, 0, 10.5, 6.5, 1.6, 13, lea], [6.8, 0, 10.5, 8, 1.6, 13.4, lea],
            [8.3, 0, 10.5, 9.5, 1.6, 13.2, lea], [9.8, 0, 10.5, 11, 1.6, 12.6, lea], [10.6, 0, 6, 13, 1.4, 9, lea]]


def bench():
    p = "yd_board_grey"
    return [[5, 4, 0, 10, 5, 16, p], [5.5, 0, 1, 9.5, 4, 2.5, p], [5.5, 0, 13.5, 9.5, 4, 15, p], [7, 1.2, 0, 8, 2, 16, p]]


def fence(top):
    b = [[0, 0, 7.6, 16, 16, 8.4, "chainlink"], [0, 0, 7.2, 1.2, 16, 8.8, GALV]]
    if top:
        b.append([0, 15, 7.4, 16, 16, 8.6, GALV])
    else:
        b.append([0, 0.5, 7.5, 16, 1.1, 8.5, GALV])
    return b


def log():
    bark = {"side": BARK, "up": BARK, "down": BARK, "north": "tree_oak_rings", "south": "tree_oak_rings"}
    return [[x0, y0, 0.5, x1, y1, 15.5, bark] for y0, y1, x0, x1 in disc_strips(3.6, 8, 3.6, step=1.8)]


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

    for c in COLOURS:
        for part in ("door", "open", "end"):
            for side in ("l", "r"):
                add(f"zs_tent_{c}_{part}_{side}", tent(c, part, side), "Tent", "WOOL")
        add(f"zs_tent_{c}_down", tent_down(c), "Fallen Tent", "WOOL", decor=True)
    for k in RUCKS:
        add(f"zs_ruck_{k}", ruck_model(k), "Rucksack", "WOOL", loot=f"story_{k}_camp")
    add("zs_rod", rod_model(), "Fishing Rod", "WOOD", decor=True)
    add("zs_rod_down", rod_down(), "Fishing Rod", "WOOD", decor=True)
    add("zs_tackle", tackle(), "Tackle Box", "STONE", loot="story_fishing_trip")
    add("zs_stringer", stringer(), "Fish on a Stringer", "WOOL", decor=True)
    add("zs_trap", trap(), "Cage Trap", "METAL")
    add("zs_snare", snare(), "Snare", "WOOD", decor=True)
    add("zs_hide_rack", hide_rack(), "Stretched Hide", "WOOL")
    add("zs_hoop", hoop(), "Pelt on a Hoop", "WOOL", decor=True)
    add("zs_lean", lean(True), "Lean-to", "WOOL")
    add("zs_lean_mid", lean(False), "Lean-to", "WOOL")
    add("zs_deer_a", deer("a"), "Dead Deer", "WOOL", decor=True)
    add("zs_deer_b", deer("b"), "Dead Deer", "WOOL", decor=True)
    add("zs_gun_case", gun_case_model(), "Rifle Case", "WOOL", loot="story_hunter_camp")
    add("zs_spoil", spoil(True), "Dug Earth", "GRAVEL", rotate=None)
    add("zs_spoil_low", spoil(False), "Dug Earth", "GRAVEL", rotate=None)
    add("zs_grave_a", grave("a"), "Open Grave", "GRAVEL")
    add("zs_grave_b", grave("b"), "Open Grave", "GRAVEL")
    add("zs_cans", cans(), "Empty Cans", "METAL", decor=True)
    add("zs_tote", tote_model(), "Beach Bag", "WOOL", loot="story_beach_party")
    add("zs_cooler", cooler_model(), "Cooler", "STONE", loot="story_bbq_party")
    add("zs_fest_pack", fest_model(), "Daypack", "WOOL", loot="story_music_fest")
    add("zs_kitbag", kitbag_model(), "Equipment Bag", "WOOL", loot="story_baseball")
    add("zs_base", [[5, 0, 5, 11, 1, 11, "bp_canvas"]], "Base", "WOOL", rotate=None, decor=True)
    add("zs_home", [[4.5, 0, 4.5, 11.5, 0.3, 11.5, "hw_white"]], "Home Plate", "STONE", rotate=None, decor=True)
    add("zs_rubber", [[5, 0, 7.2, 11, 0.4, 8.8, "hw_white"]], "Pitcher's Rubber", "STONE", decor=True)
    add("zs_bat", bat(), "Baseball Bat", "WOOD", decor=True)
    add("zs_glove", glove(), "Baseball Glove", "WOOL", decor=True)
    add("zs_bench", bench(), "Bench", "WOOD", rotate="axis")
    add("zs_fence", fence(False), "Backstop", "METAL", render="cutout")
    add("zs_fence_top", fence(True), "Backstop", "METAL", render="cutout")
    add("zs_log", log(), "Log", "WOOD", rotate="axis")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    blocks_ = data["blocks"]
    made = blocks()
    for k in [k for k in blocks_ if k.startswith("zs_") and k not in made]:
        del blocks_[k]
    blocks_.update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} zone story blocks in {path}")


if __name__ == "__main__":
    main()
