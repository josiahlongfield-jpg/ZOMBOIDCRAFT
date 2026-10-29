"""Tents: the tn_ kit for every tent in the world (world/county/Tents.java), the relief shelters on
the stadium's field and the station's concourse, the triage tents at the hospital, the GP tents in
the army's camps, the white field hospitals.

Photos: refs/military/field-hospital-tent (the JGSDF's frame tent at Camp Senzo: olive panels
bowing between the frame's poles, a low fly over a valance, zipped window flaps, a red cross
pinned on the wall, a clear curtain in the door, guy lines to stakes, green interlocking mats; a
1917 ward tent). A tent is a frame of aluminium poles under a canvas fly pitched at 22.5 degrees:

- the section is w blocks across (6 for a shelter or a GP tent, 10 or 18 for a hospital). The
  walls stand in the outermost cells, two blocks high, the canvas at the cell's outer face. The roof
  starts at the eave on top of them and climbs a panel per cell, 6.63 sixteenths a cell, to the
  ridge in the middle; panel k (k cells in from the wall) sits in row 3 + floor(k tan 22.5) above
  the tent's floor, so every panel of a wide tent is a block of its own;
- frame rows (every other row along the tent) carry a rafter under each panel, a pole inside each
  wall, a tie bar across under the eaves with the lamp on it, and the guy lines outside;
- the ends: the corner posts, end walls up to the roof line (the gable panels step up in two
  sixteenth strips), the door in the middle two cells, rolled or with a clear strip curtain.

Blocks, by canvas colour c (olive, tan, white). Every wall and roof piece faces the way its outside
does (its canvas on the model's north edge):
- tn_c_wall, tn_c_wall_p (with the frame's pole), tn_c_wall_lo, tn_c_wall_lo_p (the bottom row,
  sod cloth and stake loop), tn_c_win, tn_c_win_open, tn_c_cross (the red cross pinned on),
  tn_c_lbl_KEY_0..1 (a vinyl banner over two blocks, tile 0 at the viewer's left);
- tn_c_corner, tn_c_corner_lo (north and west walls: turn it to the corner);
- tn_c_roof_K, tn_c_roof_Kf (with a rafter), tn_c_roof_Kr, tn_c_roof_Kfr (with the ridge, for the
  middle panels); tn_c_roofend_K[r]_a / _b: the same at the end of the tent, its gable panel on
  the model's east (a) or west (b) side, the fly reaching past it;
- tn_c_door_hi_l/_r, tn_c_door_lo_l/_r (the jamb on the model's west, l, or east, r; the flap
  rolled over the door), tn_c_curtain_hi and tn_curtain_lo (clear PVC strips);
- fallen in: tn_c_sheet, tn_c_heap, tn_c_sag (the fly come down at 45 degrees), tn_pole,
  tn_pole_down (axis);
- shared: tn_tie, tn_tie_lamp, tn_tie_lamp_lit (axis), tn_guy, tn_guy_cut, tn_mat, tn_ply,
  tn_groundsheet, tn_screen, tn_table_med, tn_table_supply, tn_genset, tn_cable (axis), tn_ruck,
  tn_stand_KEY (the sign out front).

    python3 tools/city/tents.py          rewrites the tn_ entries in blocks.json

photo_textures.py draws the textures (tents.register()).
"""
import json
import math
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import broadcast as B  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = B.BOLD
STENCIL = B.STENCIL
RED = (190, 32, 34)
WHITE = (238, 236, 228)
INK = (30, 30, 32)

# the canvas and its liner (the inside face, lighter), and the tent widths each colour comes in
CANVAS = {
    "olive": ((84, 90, 62), (128, 130, 100), (6,)),
    "tan": ((184, 164, 122), (214, 202, 172), (6, 10)),
    "white": ((220, 220, 210), (238, 238, 230), (6, 10, 18)),
}
# the banners and the stand out front: the word, its colour, the small print
LABELS = {
    "triage": ("TRIAGE", RED, "AID STATION"),
    "shelter": ("SHELTER", (36, 70, 130), "CHECK IN HERE"),
    "supply": ("SUPPLIES", (40, 100, 60), "ONE PER FAMILY"),
}
TAN = math.tan(math.radians(22.5))
COS = math.cos(math.radians(22.5))
SIN = math.sin(math.radians(22.5))


def rise(k):
    """Panel k's row above row 3 and its eave edge's height in that row, in sixteenths."""
    r = math.floor(k * TAN + 1e-9)
    return r, 16 * (k * TAN - r)


# ---------------------------------------------------------------- the canvas

def waves(t, amount, h=0.06):
    """Tension folds: soft periodic creases at a slant, so the canvas reads as pulled over a frame."""
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    warp = P.tnoise(t.rng, 4) * 3.0
    s = np.zeros((N, N), np.float32)
    for m, n, a in ((1, 2, 1.0), (2, -1, 0.6), (3, 1, 0.35)):
        s += a * np.sin(2 * np.pi * (m * xx + n * yy + warp) / N + t.rng.uniform(0, 6.28))
    s /= 1.95
    t.c *= (1 + amount * s)[..., None]
    t.h += s * h


def canvas(seed, rgb, mud=False, seam=False, roof=False):
    """Waxed duck: a faint twill, folds where it's pulled, sun-faded, mildew and mud from the
    ground up on the bottom row; a welded seam down the middle over the frame's pole."""
    t = F.plain(seed, rgb, 0.04 if roof else 0.06, 0.22)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) % 3) == 0] *= 0.965
    t.h[((xx + yy) % 3) == 0] -= 0.03
    waves(t, 0.025 if roof else 0.05)
    lum = sum(rgb) / 3
    if seam:
        area(t, 30, 0, 34, N, mul=0.9, dh=0.12)
        area(t, 33, 0, 34, N, mul=0.8)
        for x in (28, 36):
            for y in range(1, N, 3):
                area(t, x, y, x + 1, y + 2, mul=0.82, dh=-0.04)
        # the canvas bows away from the pole either side
        for i in range(8):
            area(t, 22 + i, 0, 23 + i, N, mul=1 - 0.012 * i)
            area(t, 41 - i, 0, 42 - i, N, mul=1 - 0.012 * i)
    if roof:
        # weathered darker than the walls, the wet line down the middle where the rain ran; leaves, a gull's droppings
        t.c *= 0.94
        yy2 = np.arange(N)[:, None]
        t.c *= (1 - 0.05 * np.exp(-((yy2 - 20) / 10.0) ** 2))[..., None]
        t.speckle(0.006, (120, 80, 36), 0.7, 0.1)
        t.speckle(0.004, (150, 110, 50), 0.6, 0.1)
        t.speckle(0.003, (232, 230, 220), 0.8, 0.05)
        # white vinyl shows its grime in blotches: keep it to a film
        t.grime(0.11 if lum > 200 else 0.25, (50, 52, 40))
    else:
        t.streaks(0.25, 0.86 if lum > 150 else 0.92)
        t.grime(0.07 if lum > 200 else 0.15, (60, 58, 46))
    if mud:
        n = np.clip(P.fbm(t.rng) * 0.6 + 0.5, 0, 1)
        m = np.clip((yy - 42) / 20.0, 0, 1) * (0.55 + 0.45 * n)
        t.mix(m * 0.75, (82, 66, 46))
        splash = (t.rng.random((N, N)) < 0.025) & (yy > 36)
        t.c[splash] = t.c[splash] * 0.6 + np.array((88, 72, 50), np.float32) * 0.4
        mil = P.blur(((t.rng.random((N, N)) < 0.012) & (yy > 30)).astype(np.float32), 1) * 2.5
        t.mix(mil * 0.35, (44, 50, 36))
        area(t, 0, 58, N, N, mul=0.8, dh=0.05)
    return t


def liner(seed, rgb):
    """The inside face: a lighter liner with its quilting seams, smoke-stained toward the top."""
    t = F.plain(seed, rgb, 0.04, 0.2)
    waves(t, 0.03, 0.03)
    for y in (0, 32):
        area(t, 0, y, N, y + 1, mul=0.9, dh=-0.05)
    t.grime(0.12, (80, 76, 64))
    return t


def window(seed, rgb, open_):
    """A window in the wall: the zip-round flap shut, or rolled up over the insect mesh."""
    t = canvas(seed, rgb)
    tape = tuple(int(v * 0.72) for v in rgb)
    x0, y0, x1, y1 = 10, 10, 54, 46
    if open_:
        area(t, x0, y0, x1, y1, col=(30, 32, 30), h=0.35, smooth=0.1)
        yy, xx = np.mgrid[y0:y1, x0:x1]
        g = ((xx % 2) == 0) | ((yy % 2) == 0)
        t.c[y0:y1, x0:x1][g] = (58, 62, 58)
        t.c[y0 + 20:y1, x0:x1] *= 0.85
    else:
        area(t, x0, y0, x1, y1, mul=0.94, dh=0.08)
        waves(t, 0.02)
    for (a, b, c, d) in ((x0 - 2, y0 - 2, x1 + 2, y0), (x0 - 2, y1, x1 + 2, y1 + 2),
                         (x0 - 2, y0, x0, y1), (x1, y0, x1 + 2, y1)):
        area(t, a, b, c, d, col=tape, dh=0.1)
    # the zip down both sides and along the bottom
    for y in range(y0, y1, 2):
        area(t, x0 - 1, y, x0, y + 1, col=(40, 40, 36))
        area(t, x1, y, x1 + 1, y + 1, col=(40, 40, 36))
    for x in range(x0, x1, 2):
        area(t, x, y1, x + 1, y1 + 1, col=(40, 40, 36))
    # toggles at the top that tie the flap up
    for x in (22, 42):
        area(t, x, y0 - 6, x + 2, y0 + 3, col=tape, dh=0.15)
    return t


def fold(seed, rgb):
    """Canvas lying where it fell: creased, trodden, muddy."""
    t = canvas(seed, rgb)
    rng = t.rng
    for _ in range(3):
        x0, y0 = rng.uniform(0, N, 2)
        a = rng.uniform(0, np.pi)
        for s in range(int(rng.uniform(20, 50))):
            x, y = int(x0 + np.cos(a) * s) % N, int(y0 + np.sin(a) * s) % N
            t.c[y, x] *= 0.86
            t.c[(y + 1) % N, x] *= 0.8
            t.c[(y + 2) % N, x] *= 1.1
            t.c[(y + 3) % N, x] *= 1.06
            t.h[(y + 1) % N, x] -= 0.15
    t.grime(0.45, (74, 62, 44))
    t.speckle(0.02, (60, 50, 36), 0.6)
    return t


def cross(seed):
    """The red cross on a square of white cloth, creased from its pack, pinned at the corners."""
    t = F.plain(seed, (236, 234, 226), 0.04, 0.2)
    waves(t, 0.04)
    area(t, 26, 8, 38, 56, col=RED)
    area(t, 8, 26, 56, 38, col=RED)
    t.c[26:38, 8:56] *= 1.0
    for x, y in ((3, 3), (59, 3), (3, 59), (59, 59)):
        disc(t, x, y, 1.4, col=(150, 150, 140), f0=120)
    t.streaks(0.2, 0.9)
    t.grime(0.2, (90, 80, 60))
    return t


def vinyl(seed):
    t = F.plain(seed, (236, 236, 230), 0.03, 0.4)
    t.grime(0.1)
    return t


@lru_cache(maxsize=None)
def banner_pic(key):
    """A vinyl banner two blocks across, its word in the band the banner covers (rows 8..56)."""
    word, col, small = LABELS[key]
    p = Pic(2, 1, (236, 236, 230), seed=len(key))
    p.rect(0, 8, p.W, 12, fill=col)
    p.rect(0, 52, p.W, 56, fill=col)
    f = civic.fit(word, BOLD, 26, p.W - (64 if key == "triage" else 14))
    p.text(p.W / 2, 28, word, BOLD, f, col, anchor="mm")
    p.text(p.W / 2, 45, small, BOLD, 8, INK, anchor="mm")
    if key == "triage":
        for cx in (14, p.W - 14):
            p.rect(cx - 3, 20, cx + 3, 40, fill=RED)
            p.rect(cx - 10, 27, cx + 10, 33, fill=RED)
    for x in (4, p.W - 6):
        for y in (14, 47):
            p.ellipse(x, y, x + 3, y + 3, fill=(180, 180, 170), outline=(90, 90, 90))
    return p


def banner(key, i):
    t = banner_pic(key).tile(i, 47100 + len(key) * 3 + i, smooth=0.45, grime=0.18)
    waves(t, 0.03)
    t.streaks(0.2, 0.9)
    return t


@lru_cache(maxsize=None)
def stand_pic(key):
    """The sign on its stand by the door: the word under a coloured head, what to do under it."""
    word, col, small = LABELS[key]
    p = Pic(1, 1, (236, 236, 230), seed=len(key) + 7)
    p.rect(4, 4, N - 4, 18, fill=col)
    p.text(N / 2, 11, "RELIEF", BOLD, 8, WHITE, anchor="mm")
    if key == "triage":
        p.rect(N / 2 - 3, 21, N / 2 + 3, 37, fill=RED)
        p.rect(N / 2 - 8, 26, N / 2 + 8, 32, fill=RED)
    else:
        p.text(N / 2, 29, word, BOLD, civic.fit(word, BOLD, 12, N - 8), col, anchor="mm")
    p.text(N / 2, 44, word if key == "triage" else "THIS WAY", BOLD, 8, INK, anchor="mm")
    p.text(N / 2, 53, small, BOLD, 5, INK, anchor="mm")
    p.rect(2, 2, N - 2, N - 2, outline=(160, 160, 150), width=1)
    return p


def stand(key):
    return stand_pic(key).tile(0, 47130 + len(key), smooth=0.4, grime=0.2)


def pvc(seed):
    """Clear PVC strip: see-through, a highlight down one edge, scuffed at the bottom."""
    t = F.plain(seed, (196, 210, 210), 0.02, 0.8)
    t.alpha[:] = 80
    area(t, 2, 0, 4, N, col=(236, 244, 244), alpha=140)
    yy = np.arange(N)[:, None]
    t.alpha[:] += (np.clip((yy - 44) / 20, 0, 1) * 60).astype(np.float32)
    t.c *= (1 - np.clip((yy - 44) / 20, 0, 1) * 0.35)[..., None]
    t.f0[:] = 40
    return t


# ---------------------------------------------------------------- the floor and the fittings

def mat(seed):
    """Green interlocking foam mats a metre square: the jigsaw teeth at the joins, dimpled,
    trodden mud."""
    t = F.plain(seed, (110, 168, 132), 0.05, 0.25)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) % 4 == 0) & ((xx - yy) % 4 == 0)] *= 0.93
    gap = (40, 70, 52)
    for i in range(N):
        tooth = 1 if (i // 8) % 2 == 0 else 4
        area(t, tooth, i, tooth + 1, i + 1, col=gap, dh=-0.3)
        area(t, i, tooth, i + 1, tooth + 1, col=gap, dh=-0.3)
    for i in range(8, N, 8):
        area(t, 1, i, 5, i + 1, col=gap, dh=-0.3)
        area(t, i, 1, i + 1, 5, col=gap, dh=-0.3)
    rng = t.rng
    for _ in range(4):
        x, y = rng.uniform(6, 58, 2)
        F.stain(t, x, y, rng.uniform(3, 6), col=(96, 80, 56), amount=0.4, tide=False)
    t.grime(0.25, (70, 64, 46))
    return t


def ply(seed):
    """Plywood sheets laid on the dirt: the grain, a joint, screws round the edge, boot mud."""
    t = F.plain(seed, (168, 148, 112), 0.04, 0.2)
    g = P.tnoise(t.rng, 4)
    yy = np.arange(N)[:, None]
    grain = np.sin(yy * 0.9 + g * 6) * 0.06
    t.c *= (1 + grain)[..., None]
    area(t, 0, 0, N, 1, col=(70, 56, 40), dh=-0.3)
    for x in range(4, N, 10):
        disc(t, x, 4, 0.8, col=(90, 86, 80), f0=80)
    t.grime(0.55, (86, 76, 60))
    t.speckle(0.015, (60, 50, 36), 0.5)
    return t


def groundsheet(seed):
    t = F.plain(seed, (82, 98, 116), 0.05, 0.45)
    for v in (21, 42):
        area(t, v, 0, v + 1, N, mul=0.82, dh=-0.1)
        area(t, 0, v, N, v + 1, mul=0.82, dh=-0.1)
    t.grime(0.4, (80, 70, 50))
    return t


def cord(seed, rgb=(170, 156, 116)):
    """Guy line: twisted nylon, the twist running at a slant."""
    t = F.plain(seed, rgb, 0.04, 0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy * 2) % 6) < 2] *= 0.8
    t.grime(0.2)
    return t


def gen_tank(seed):
    t = F.plain(seed, (176, 40, 30), 0.04, 0.6)
    disc(t, 32, 32, 7, col=(60, 60, 60), f0=60)
    disc(t, 32, 32, 4, col=(170, 170, 166), f0=120)
    area(t, 8, 48, 56, 54, col=(230, 220, 200))
    t.grime(0.3)
    t.streaks(0.3)
    return t


def gen_panel(seed):
    """The genset's panel: two outlets under covers, the breakers, the hour meter, the choke."""
    t = F.plain(seed, (50, 52, 54), 0.03, 0.4)
    area(t, 4, 4, 60, 60, col=(70, 72, 74))
    for x in (14, 34):
        area(t, x, 30, x + 14, 46, col=(30, 30, 30), dh=0.2)
        for dx in (4, 9):
            area(t, x + dx, 35, x + dx + 1, 40, col=(200, 196, 180))
    area(t, 10, 10, 24, 18, col=(200, 200, 190))
    area(t, 12, 12, 22, 16, col=(30, 30, 30))
    for x in (34, 42, 50):
        area(t, x, 10, x + 5, 20, col=(20, 20, 20), dh=0.2)
    disc(t, 52, 50, 3, col=(176, 40, 30))
    t.grime(0.3)
    return t


def gen_engine(seed):
    t = F.plain(seed, (60, 60, 62), 0.05, 0.3)
    for y in range(4, N, 6):
        area(t, 0, y, N, y + 2, mul=0.65, dh=-0.2)
    t.grime(0.45, (40, 30, 20))
    t.speckle(0.02, (120, 70, 40), 0.6)
    return t


def ruck(seed):
    """A soldier's ruck: olive nylon, the webbing in rows, a buckle."""
    t = F.plain(seed, (82, 88, 60), 0.05, 0.2)
    for y in range(8, 56, 7):
        area(t, 4, y, 60, y + 3, mul=0.82, dh=0.1)
        for x in range(4, 60, 8):
            area(t, x, y, x + 1, y + 3, mul=0.6)
    area(t, 26, 2, 38, 8, col=(30, 30, 28), dh=0.2)
    t.grime(0.3)
    return t


def table_top(seed):
    t = F.plain(seed, (214, 212, 204), 0.03, 0.35)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx % 4) == 0) & ((yy % 4) == 0)] *= 0.95
    t.grime(0.25)
    return t


def box_med(seed):
    t = F.plain(seed, (232, 230, 222), 0.03, 0.3)
    area(t, 26, 12, 38, 44, col=RED)
    area(t, 16, 22, 48, 34, col=RED)
    area(t, 8, 50, 56, 54, col=(80, 80, 80))
    t.grime(0.2)
    return t


def gauze(seed):
    t = F.plain(seed, (240, 238, 232), 0.03, 0.2)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[((xx + yy) % 2) == 0] *= 0.96
    t.grime(0.1)
    return t


def clip(seed):
    """A clipboard: brown board, the steel clip, the sheet of names."""
    t = F.plain(seed, (130, 96, 60), 0.04, 0.3)
    area(t, 6, 6, 58, 60, col=(240, 238, 230))
    for y in range(16, 58, 4):
        area(t, 10, y, 54, y + 1, col=(120, 120, 140))
        area(t, 12, y - 2, 12 + int(t.rng.uniform(10, 36)), y, col=(40, 40, 60))
    area(t, 20, 2, 44, 10, col=(170, 170, 166), f0=120)
    return t


def screen(seed):
    """A ward screen's curtain: pale green, pleated."""
    t = F.plain(seed, (150, 192, 184), 0.04, 0.25)
    xx = np.arange(N)[None, :]
    s = np.sin(xx * 2 * np.pi / 8)
    t.c *= (1 + 0.1 * s)[..., None]
    t.h += s * 0.1
    t.grime(0.2)
    return t


def bulb(seed, lit):
    t = F.plain(seed, (250, 236, 190) if lit else (200, 196, 184), 0.02, 0.8)
    if lit:
        t.emit[:] = 1.0
    return t


def textures():
    T = {}
    for ci, (c, (rgb, lin, _)) in enumerate(CANVAS.items()):
        s = 47000 + ci * 20
        T[f"tn_{c}_wall"] = lambda s=s, rgb=rgb: canvas(s, rgb)
        T[f"tn_{c}_wall_seam"] = lambda s=s, rgb=rgb: canvas(s + 1, rgb, seam=True)
        T[f"tn_{c}_wall_lo"] = lambda s=s, rgb=rgb: canvas(s + 2, rgb, mud=True)
        T[f"tn_{c}_wall_lo_seam"] = lambda s=s, rgb=rgb: canvas(s + 3, rgb, mud=True, seam=True)
        T[f"tn_{c}_win"] = lambda s=s, rgb=rgb: window(s + 4, rgb, False)
        T[f"tn_{c}_win_open"] = lambda s=s, rgb=rgb: window(s + 5, rgb, True)
        T[f"tn_{c}_roof"] = lambda s=s, rgb=rgb: canvas(s + 6, rgb, roof=True)
        T[f"tn_{c}_roof_seam"] = lambda s=s, rgb=rgb: canvas(s + 7, rgb, seam=True, roof=True)
        T[f"tn_{c}_liner"] = lambda s=s, lin=lin: liner(s + 8, lin)
        T[f"tn_{c}_fold"] = lambda s=s, rgb=rgb: fold(s + 9, rgb)
    T["tn_cross"] = lambda: cross(47080)
    T["tn_vinyl"] = lambda: vinyl(47081)
    for key in LABELS:
        for i in range(2):
            T[f"tn_banner_{key}_{i}"] = lambda key=key, i=i: banner(key, i)
        T[f"tn_stand_{key}"] = lambda key=key: stand(key)
    T["tn_pvc"] = lambda: pvc(47082)
    T["tn_mat"] = lambda: mat(47083)
    T["tn_ply"] = lambda: ply(47084)
    T["tn_groundsheet"] = lambda: groundsheet(47085)
    T["tn_cord"] = lambda: cord(47086)
    T["tn_gen_tank"] = lambda: gen_tank(47087)
    T["tn_gen_panel"] = lambda: gen_panel(47088)
    T["tn_gen_engine"] = lambda: gen_engine(47089)
    T["tn_ruck"] = lambda: ruck(47090)
    T["tn_table_top"] = lambda: table_top(47091)
    T["tn_box_med"] = lambda: box_med(47092)
    T["tn_gauze"] = lambda: gauze(47093)
    T["tn_clip"] = lambda: clip(47094)
    T["tn_screen"] = lambda: screen(47095)
    T["tn_shade"] = lambda: F.plain(47098, (44, 62, 48), 0.04, 0.5)
    T["tn_mesh"] = lambda: window(47099, (60, 60, 56), True)
    T["tn_bulb"] = lambda: bulb(47096, False)
    T["tn_bulb_lit"] = lambda: bulb(47097, True)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def faces(out, inside, axis="z"):
    """A panel's faces: its outside on the model's north (or west, for an x panel) face."""
    f = {"north": out, "south": out, "east": out, "west": out, "up": out, "down": out}
    if axis == "z":
        f["south"] = inside
    else:
        f["east"] = inside
    return f


def turn(angle, y, z=8):
    return {"axis": "x", "angle": angle, "origin": [8, round(y, 3), z]}


def roof_boxes(k, c, frame, ridge, x0=0, x1=16):
    """Panel k of the fly, its eave edge at the model's north: the panel itself (the roof canvas
    up, the liner down), the eave's purlin, overhang and valance on the outermost, the rafter
    under a frame row's, the ridge pole and the cap over the middle one."""
    _, o = rise(k)
    up, dn = f"tn_{c}_roof_seam" if frame else f"tn_{c}_roof", f"tn_{c}_liner"
    yc = o + 8 * TAN
    half = 8 / COS + 0.15
    ov = 4 if k == 0 else 0
    tex = {"up": up, "down": dn, "north": up, "south": up, "east": up, "west": up}
    rot = turn(-22.5, yc)
    out = [[x0, yc - 0.35, 8 - half - ov, x1, yc + 0.35, 8 + half, tex, None, rot]]
    if k == 0:
        # the purlin along the wall's head, and the valance hanging off the fly's edge
        out.append([0, -1.4, 0.2, 16, 0, 1.6, "ext_alu"])
        d = half + ov
        ez, ey = 8 - d * COS, yc - d * SIN
        # scalloped: drawn tight to the frame at the block's edges, sagging deepest between them
        for sx0, sx1, dep in ((0, 2, 2.4), (2, 4.5, 3.3), (4.5, 11.5, 4.2), (11.5, 14, 3.3), (14, 16, 2.4)):
            sx0, sx1 = max(sx0, x0), min(sx1, x1)
            if sx1 > sx0:
                out.append([sx0, round(ey - dep, 3), ez - 0.3, sx1, ey + 0.1, ez + 0.3, faces(up, dn)])
    if frame:
        out.append([7.3, yc - 1.75, 8 - half, 8.7, yc - 0.35, 8 + half, "ext_alu", None, rot])
    if ridge:
        ry = o + 16 * TAN
        out.append([x0, yc + 0.35, 8 + half - 3.4, x1, yc + 0.95, 8 + half + 0.3, tex, None, rot])
        out.append([0, ry - 2.8, 14.6, 16, ry - 1.4, 16, "ext_alu"])
    return out


def roof_shape(k):
    _, o = rise(k)
    return [[0, min(o, 15), 0, 16, min(16, o + 4 * TAN + 0.8), 8],
            [0, min(o + 8 * TAN, 15), 8, 16, min(16, o + 12 * TAN + 0.8), 16]]


def gable_boxes(k, c, side, ridge=False):
    """The end wall up to the roof line in two sixteenth strips, on the model's east (a) or west
    (b) edge, its outside facing out of the tent."""
    _, o = rise(k)
    out = []
    xa, xb = (15.1, 15.7) if side == "a" else (0.3, 0.9)
    tex = f"tn_{c}_wall"
    f = {"north": tex, "south": tex, "up": tex, "down": tex,
         "east": tex if side == "a" else f"tn_{c}_liner", "west": tex if side == "b" else f"tn_{c}_liner"}
    for z0 in range(0, 16, 2):
        h = o + z0 * TAN - 0.25
        if h > 0.3:
            out.append([xa, 0, z0, xb, round(h, 3), z0 + 2, f])
    if ridge:
        # the vent under the ridge: a mesh panel with its storm flap tied up over it
        y1 = o + 9 * TAN - 0.8
        xo = (15.7, 16.0) if side == "a" else (0.0, 0.3)
        uv = {d: [0, 0, 16, 16] if d in ("east", "west") else [0, 0, 1, 1] for d in ("north", "south", "east", "west", "up", "down")}
        out.append([xo[0], round(y1 - 3.4, 3), 9.5, xo[1], round(y1, 3), 14.5, "tn_mesh", uv])
        xr = (15.8, 16.6) if side == "a" else (-0.6, 0.2)
        out.append([xr[0], round(y1, 3), 9.3, xr[1], round(y1 + 0.9, 3), 14.7, tex])
    return out


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis" and P.drawn_along(boxes) == "x":
            boxes = P.along_z(boxes)  # RULE: axis models run along z as drawn
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

    alu, galv, black, cd = "ext_alu", "st_galv", "home_plastic_black", "tn_cord"

    for c, (_, _, widths) in CANVAS.items():
        lin = f"tn_{c}_liner"
        kmax = max(widths) // 2
        tops = {w // 2 - 1 for w in widths} | {1}  # 1: the entry vestibule's ridge

        def panel(tex):
            return [0, 0, 0.3, 16, 16, 0.9, faces(tex, lin)]

        stake = [[7.6, 0, -1.0, 8.4, 2.4, -0.4, galv], [7.2, 1.0, -0.5, 8.8, 1.8, 0.3, cd]]
        sod = [[0, 0, 0.9, 16, 0.3, 3.6, f"tn_{c}_wall_lo"]]
        pole = [[7.3, 0, 0.9, 8.7, 16, 2.3, alu]]
        foot = [[6, 0, 0.9, 10, 0.4, 3.6, galv]]
        wall_shape = [[0, 0, 0, 16, 16, 1.2]]
        add(f"tn_{c}_wall", [panel(f"tn_{c}_wall")], "Tent", "WOOL", shape=wall_shape)
        add(f"tn_{c}_wall_p", [panel(f"tn_{c}_wall_seam")] + pole, "Tent", "WOOL", shape=wall_shape)
        add(f"tn_{c}_wall_lo", [panel(f"tn_{c}_wall_lo")] + sod + stake, "Tent", "WOOL", shape=wall_shape)
        add(f"tn_{c}_wall_lo_p", [panel(f"tn_{c}_wall_lo_seam")] + sod + stake + pole + foot, "Tent", "WOOL",
            shape=wall_shape)
        add(f"tn_{c}_win", [panel(f"tn_{c}_win")], "Tent", "WOOL", shape=wall_shape)
        roll = [[2, 12.6, -1.8, 14, 14.8, 0.3, f"tn_{c}_wall"], [4, 10.6, -2.0, 4.6, 13, -1.4, cd],
                [11.4, 10.6, -2.0, 12, 13, -1.4, cd]]
        add(f"tn_{c}_win_open", [panel(f"tn_{c}_win_open")] + roll, "Tent", "WOOL", shape=wall_shape)
        add(f"tn_{c}_cross", [panel(f"tn_{c}_wall"),
                              [1, 1.5, -0.35, 15, 15, -0.05, {"north": "tn_cross", "south": "tn_cross", "side": "tn_vinyl",
                                                              "up": "tn_vinyl", "down": "tn_vinyl"}]],
            "Tent", "WOOL", shape=wall_shape)
        for key in LABELS:
            for i in range(2):
                add(f"tn_{c}_lbl_{key}_{i}",
                    [panel(f"tn_{c}_wall"),
                     [0, 2, -0.45, 16, 14, -0.1, {"north": f"tn_banner_{key}_{i}", "south": "tn_vinyl", "side": "tn_vinyl",
                                                  "up": "tn_vinyl", "down": "tn_vinyl"}],
                     [7.6 if i else 0.4, 14, -0.4, 8.4 if i else 1.2, 16, -0.15, cd]],
                    "Tent", "WOOL", shape=wall_shape)
        # the corners: north and west walls round a corner post
        west = [0.3, 0, 0.9, 0.9, 16, 16, faces(f"tn_{c}_wall", lin, axis="x")]
        west_lo = [0.3, 0, 0.9, 0.9, 16, 16, faces(f"tn_{c}_wall_lo", lin, axis="x")]
        post = [[0.9, 0, 0.9, 2.5, 16, 2.5, alu]]
        add(f"tn_{c}_corner", [panel(f"tn_{c}_wall"), west] + post, "Tent", "WOOL",
            shape=[[0, 0, 0, 16, 16, 1.2], [0, 0, 0, 1.2, 16, 16]])
        add(f"tn_{c}_corner_lo", [panel(f"tn_{c}_wall_lo"), west_lo] + post + sod
            + [[0.9, 0, 3.6, 3.6, 0.3, 16, f"tn_{c}_wall_lo"], [0, 0, -1.4, 1.6, 2.6, -0.6, galv]],
            "Tent", "WOOL", shape=[[0, 0, 0, 16, 16, 1.2], [0, 0, 0, 1.2, 16, 16]])
        # the door: the flap rolled up over it, the side flaps tied back to the jambs
        for side in ("l", "r"):
            def m(b, side=side):
                if side == "l":
                    return b
                return [16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5]] + b[6:]
            jamb = [0, 0, 0.3, 1.4, 16, 1.7, alu]
            gather = [1.4, 0, -0.6, 3.8, 11.4, 0.9, f"tn_{c}_wall"]
            tie = [3.6, 8.6, -0.8, 4.0, 9.4, 1.0, cd]
            top = [0, 11.4, 0.3, 16, 16, 0.9, faces(f"tn_{c}_wall", lin)]
            rolled = [0, 9.4, -1.6, 16, 11.6, 0.6, f"tn_{c}_wall"]
            add(f"tn_{c}_door_hi_{side}", [m(b) for b in (jamb, gather, tie, top, rolled)], "Tent Door", "WOOL", decor=True)
            add(f"tn_{c}_door_lo_{side}", [m(b) for b in (jamb, [1.4, 0, -0.6, 3.8, 16, 0.9, f"tn_{c}_wall"],
                                                          [3.6, 4, -0.8, 4.0, 4.8, 1.0, cd])], "Tent Door", "WOOL", decor=True)
        strips_hi = [[i * 2 + 0.1, 0, 1.0, i * 2 + 1.9, 11.4, 1.25, "tn_pvc"] for i in range(8)]
        add(f"tn_{c}_curtain_hi", strips_hi + [[0, 11.4, 0.3, 16, 16, 0.9, faces(f"tn_{c}_wall", lin)],
                                               [0, 11, 0.9, 16, 11.4, 1.4, alu]], "Strip Curtain", "WOOL", decor=True)
        # the fly, panel by panel
        for k in range(kmax):
            for frame in (False, True):
                for ridge in ((False, True) if k in tops else (False,)):
                    sfx = ("f" if frame else "") + ("r" if ridge else "")
                    add(f"tn_{c}_roof_{k}{sfx}", roof_boxes(k, c, frame, ridge), "Tent", "WOOL", shape=roof_shape(k))
            for ridge in ((False, True) if k in tops else (False,)):
                for side in ("a", "b"):
                    x0, x1 = (0, 19) if side == "a" else (-3, 16)
                    add(f"tn_{c}_roofend_{k}{'r' if ridge else ''}_{side}",
                        roof_boxes(k, c, False, ridge, x0, x1) + gable_boxes(k, c, side, ridge), "Tent", "WOOL",
                        shape=roof_shape(k))
        # fallen in
        fd = f"tn_{c}_fold"
        add(f"tn_{c}_sheet", [[0, 0, 0, 16, 0.4, 16, fd], [0, 0.4, 3.5, 16, 0.9, 5.5, fd], [2, 0.4, 10, 13, 0.7, 12, fd]],
            "Canvas", "WOOL", decor=True)
        hp = []
        for sgn in (-1, 1):
            zc = 8 + sgn * 4
            hp.append([0, 3.65, zc - 5.8, 16, 4.35, zc + 5.8, faces(fd, lin), None,
                       {"axis": "x", "angle": 45 * sgn, "origin": [8, 4, zc]}])
        for x0 in (0.4, 15.0):
            for z0 in range(1, 15, 2):
                hp.append([x0, 0, z0, x0 + 0.6, round(max(0.5, 8.6 - abs(z0 + 1 - 8) * 1.05), 2), z0 + 2, fd])
        hp.append([0, 0, 15, 16, 0.6, 16, fd])
        add(f"tn_{c}_heap", hp, "Canvas", "WOOL", shape=[[0, 0, 1, 16, 4, 15], [0, 4, 4, 16, 8, 12]])
        add(f"tn_{c}_sag", [[0, 7.65, -3.3, 16, 8.35, 19.3, faces(fd, lin), None, {"axis": "x", "angle": -45, "origin": [8, 8, 8]}]],
            "Canvas", "WOOL", shape=[[0, 0, 8, 16, 8, 16], [0, 8, 12, 16, 16, 16]])

    # the frame's tie bar under the eaves and its lamp (drawn along z, placed axis = across the tent)
    bar = [[7.4, 15.0, 0, 8.6, 16.2, 16, alu]]
    lamp = [[7.9, 11.6, 7.9, 8.1, 15.0, 8.1, black], [7.3, 11.0, 7.3, 8.7, 11.6, 8.7, "tn_shade"],
            [6.6, 10.4, 6.6, 9.4, 11.0, 9.4, "tn_shade"], [6.0, 9.8, 6.0, 10.0, 10.4, 10.0, "tn_shade"]]
    add("tn_tie", bar, "Tent Frame", "METAL", rotate="axis", decor=True)
    add("tn_tie_lamp", bar + lamp + [[7.4, 9.0, 7.4, 8.6, 9.8, 8.6, "tn_bulb"]], "Tent Lamp", "METAL", rotate="axis", decor=True)
    add("tn_tie_lamp_lit", bar + lamp + [[7.4, 9.0, 7.4, 8.6, 9.8, 8.6, "tn_bulb_lit"]], "Tent Lamp", "METAL", rotate="axis",
        decor=True, light=12)
    # a guy line from its stake out on the ground up to the fly's edge over the wall (the tent to the model's south)
    zc, yc = 0.6 + 16 * SIN, 1.0 + 16 * COS
    gr = {"axis": "x", "angle": 22.5, "origin": [8, round(yc, 3), round(zc, 3)]}
    add("tn_guy", [[7.5, 0, 0.1, 8.5, 2.2, 1.1, galv], [7.2, 1.8, 0.0, 8.8, 2.3, 1.2, galv],
                   [7.75, yc - 16, zc - 0.25, 8.25, yc + 16, zc + 0.25, cd, None, gr],
                   [7.45, yc - 12.5, zc - 0.55, 8.55, yc - 10.5, zc + 0.55, alu, None, gr]],
        "Guy Line", "WOOL", decor=True)
    add("tn_guy_cut", [[7.5, 0, 0.1, 8.5, 2.2, 1.1, galv], [7.75, 0, 1.1, 8.25, 0.4, 9, cd], [8.25, 0, 8.5, 12, 0.4, 9, cd],
                       [11.5, 0, 9, 12, 0.4, 14, cd]], "Guy Line", "WOOL", decor=True)
    add("tn_pole", [[7.3, 0, 7.3, 8.7, 16, 8.7, alu], [6.6, 0, 6.6, 9.4, 0.5, 9.4, galv]], "Tent Pole", "METAL", rotate=None,
        shape=[[7, 0, 7, 9, 16, 9]])
    add("tn_pole_down", [[7.3, 0, 0, 8.7, 1.4, 16, alu], [7.0, 0, 7.0, 9.0, 1.8, 9.0, galv]], "Tent Pole", "METAL", rotate="axis",
        decor=True)
    # floors under the tent
    add("tn_mat", None, "Floor Mats", "WOOL", rotate=None, tex={"up": "tn_mat", "side": "tn_mat", "down": "tn_mat"})
    add("tn_ply", None, "Plywood Floor", "WOOD", rotate=None, tex={"up": "tn_ply", "side": "tn_ply", "down": "tn_ply"})
    add("tn_groundsheet", None, "Groundsheet", "WOOL", rotate=None,
        tex={"up": "tn_groundsheet", "side": "tn_groundsheet", "down": "tn_groundsheet"})
    curtain_lo = [[i * 2 + 0.1, 0, 1.0, i * 2 + 1.9, 16, 1.25, "tn_pvc"] for i in range(8)]
    add("tn_curtain_lo", curtain_lo, "Strip Curtain", "WOOL", decor=True)
    # a ward screen on casters
    sc = [[0.5, 1, 7.4, 1.3, 15, 8.2, alu], [7.6, 1, 7.4, 8.4, 15, 8.2, alu], [14.7, 1, 7.4, 15.5, 15, 8.2, alu],
          [0.5, 14.4, 7.4, 15.5, 15.2, 8.2, alu], [1.3, 3, 7.6, 7.6, 14.4, 8.0, "tn_screen"], [8.4, 3, 7.6, 14.7, 14.4, 8.0, "tn_screen"]]
    sc += [[x - 0.6, 0, 6.6, x + 0.9, 1, 9.0, black] for x in (0.6, 7.7, 14.8)]
    add("tn_screen", sc, "Ward Screen", "WOOL", shape=[[0, 0, 7.2, 16, 15, 8.4]])
    # folding tables: the medics' and the supply desk's
    top = [[0.5, 11, 2, 15.5, 12, 14, "tn_table_top"]]
    legs = [[x, 0, z, x + 0.8, 11, z + 0.8, alu] for x in (1.5, 13.7) for z in (3, 12.2)]
    legs += [[1.5, 3, 3.4, 14.5, 3.6, 4.0, alu], [1.5, 3, 12.4, 14.5, 3.6, 13.0, alu]]
    medbox = {"north": "tn_box_med", "south": "tn_box_med", "east": "tn_box_med", "west": "tn_box_med", "up": "cardboard",
              "down": "cardboard"}
    add("tn_table_med", top + legs + [[2, 12, 4, 7, 15.5, 9, medbox], [8.5, 12, 4, 10.5, 14, 6, "tn_gauze"],
                                      [11, 12, 4.5, 13, 14, 6.5, "tn_gauze"], [8, 12, 8.5, 11.5, 12.3, 12.5, "tn_clip"],
                                      [12.5, 12, 9, 14, 15.5, 10.5, "tn_gauze"]],
        "Medic's Table", "WOOD", loot="basement_medicine")
    add("tn_table_supply", top + legs + [[2, 12, 3.5, 8, 16, 9, "cardboard"], [2.5, 16, 4, 7.5, 16.4, 8.5, "cardboard"],
                                         [9, 12, 5, 13, 12.3, 10, "tn_clip"], [10, 12, 10.5, 14, 14.5, 13, black]],
        "Supply Table", "WOOD", loot="basement_food")
    # the genset that ran the lights: its tube frame, the engine, the tank on top, the panel at the front
    gen = [[x, 0, z, x + 1, 11, z + 1, alu] for x in (1, 14) for z in (2, 13)]
    gen += [[1, 10.4, 2, 15, 11, 3, alu], [1, 10.4, 13, 15, 11, 14, alu], [1, 0.4, 2, 2, 1, 14, alu], [14, 0.4, 2, 15, 1, 14, alu]]
    gen += [[3, 1, 5, 10, 7.5, 12, {"north": "tn_gen_engine", "south": "tn_gen_engine", "east": "tn_gen_engine",
                                    "west": "tn_gen_engine", "up": "tn_gen_engine", "down": "tn_gen_engine"}],
            [2.5, 7.5, 3.5, 13.5, 10.4, 12.5, {"north": "tn_gen_tank", "south": "tn_gen_tank", "east": "tn_gen_tank",
                                              "west": "tn_gen_tank", "up": "tn_gen_tank", "down": "tn_gen_tank"}],
            [10.5, 1.5, 2.6, 14, 7.2, 6, {"north": "tn_gen_panel", "south": "tn_gen_engine", "east": "tn_gen_engine",
                                          "west": "tn_gen_engine", "up": "tn_gen_engine", "down": "tn_gen_engine"}],
            [7, 10.4, 7, 9, 11.4, 9, "home_chrome"], [11, 3, 9, 14, 6, 13, alu]]
    add("tn_genset", gen, "Generator", "METAL", shape=[[1, 0, 2, 15, 11, 14]])
    add("tn_cable", [[7.4, 0, 0, 8.2, 0.6, 6, black], [7.8, 0, 5.4, 8.6, 0.6, 11, black], [7.2, 0, 10.6, 8.0, 0.6, 16, black],
                     [7.4, 0, 5.4, 8.6, 0.6, 6.2, black], [7.2, 0, 10.6, 8.6, 0.6, 11.4, black]],
        "Power Cable", "WOOL", rotate="axis", decor=True)
    add("tn_ruck", [[3, 0, 4, 13, 9, 10, "tn_ruck"], [3.5, 9, 4.5, 12.5, 10.5, 9.5, "tn_ruck"], [1.8, 1, 5.5, 3, 6, 8.5, "tn_ruck"],
                    [13, 1, 5.5, 14.2, 6, 8.5, "tn_ruck"], [5, 2, 10, 6, 8.5, 10.6, cd], [10, 2, 10, 11, 8.5, 10.6, cd],
                    [4, 9.2, 9.5, 12, 11, 11.2, "tn_ruck"]],
        "Rucksack", "WOOL", loot="basement_supplies")
    for key in LABELS:
        t = f"tn_stand_{key}"
        add(f"tn_stand_{key}", [[2, 0, 6.6, 3, 13, 7.4, alu], [13, 0, 6.6, 14, 13, 7.4, alu], [1, 0, 4, 4, 0.6, 10, alu],
                                [12, 0, 4, 15, 0.6, 10, alu],
                                [1.5, 3, 7.4, 14.5, 16, 8.0, {"north": t, "south": t, "east": "tn_table_top", "west": "tn_table_top",
                                                               "up": "tn_table_top", "down": "tn_table_top"},
                                 {"north": [0, 0, 16, 16], "south": [0, 0, 16, 16], "east": [0, 0, 1, 16], "west": [0, 0, 1, 16],
                                  "up": [0, 0, 16, 1], "down": [0, 0, 16, 1]}]],
            "Sign", "METAL", shape=[[1.5, 0, 6.6, 14.5, 16, 8.0]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("tn_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} tent blocks in {path}")


if __name__ == "__main__":
    main()
