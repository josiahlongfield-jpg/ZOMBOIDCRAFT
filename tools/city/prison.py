"""The pn_ kit: Ravenrock Penitentiary's own pieces (Josiah, 2026-10-30: the northwest military
zone, the island prison reached by its ferry), after refs/prison: the cellhouse tiers and their
barred fronts (cellblock-tiers, solitary/04), the cells (cell-interior: the steel bunk, the
toilet and basin in one, the shelf and the fold-down desk), the solid doors of the hole with their
food slots (solitary/07-09), the visiting booths (visitation/02), the mess hall's bolted tables
(mess-hall), the yard (yard-fences: chain link, razor wire on outriggers) and the workshop.
world/county/Ravenrock*.java put them together.

- pn_bars: a cell front's bars, flat straps top, middle and foot (axis; the bars in the plane x = 8
  along z as drawn);
- pn_window(_broken): an outside window in the cellhouse wall: the steel sash in small wired panes,
  the bars outside it on the model's north;
- pn_catwalk: a tier's walkway, steel grating on angle edges; pn_rail: the mesh railing standing on
  its edge (north as drawn); pn_rail_end the same with the end post;
- pn_toilet_sink: the stainless toilet and basin in one against the back wall; pn_cell_desk: the
  fold-down desk and the swing-out stool; pn_shelf: the shelf over the bunk and what's on it;
- pn_mess_table: a steel table and its four stools on arms from the one post;
- pn_visit_booth / pn_visit_top: a visiting booth, the counter and the glass, the handset on the
  partition, a stool each side;
- pn_bench / pn_dumbbells / pn_pullup / pn_pullup_bar: the yard's iron pile;
- pn_post / pn_post_arm: the fence's galvanised post, the arm leaning out with the wire on it;
- pn_gate / pn_gate_top: a sliding chain-link gate in its tube frame, the razor coil along its top;
- pn_lockbox: the gang lock at a tier's end, the levers and the padlock; pn_panel: the control
  room's door board; pn_plate_press / pn_plates / pn_cart: the plate shop and the laundry;
- doors: pn_cell_door (barred, sliding in the real thing) and pn_seg_door (solid, the food slot);
- walls and floors: pn_ext(_stain, _moss) the whitewashed concrete outside, pn_paint (cream) over
  pn_dado (green) inside, pn_floor the waxed concrete;
- signs: pn_sgn_KEY_0..1, the name board pn_name_0..5, graffiti pn_spray_KEY_i.

    python3 tools/city/prison.py          rewrites the pn_ entries in blocks.json

photo_textures.py draws the textures (prison.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import broadcast as B  # noqa: E402
from furniture import area, disc, drips  # noqa: E402
from school import Pic, mixc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD, STENCIL, SPRAY = B.BOLD, B.STENCIL, B.SPRAY
OLD = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
CREAM = (226, 218, 192)
GREEN = (96, 128, 104)
BARS = (150, 160, 150)
INK = (30, 28, 26)
WHITE = (240, 238, 230)
RED = (170, 32, 28)
NAVY = (30, 44, 68)
YELLOW = (226, 186, 40)


# ---------------------------------------------------------------- textures

def flaking(t, amount, under=(60, 62, 60)):
    """Paint lifting off steel: chips showing the dark metal, rust in the worst of them."""
    m = np.clip(P.fbm(t.rng) * 2.2 - (1.6 - amount * 2), 0, 1)
    chips = m > 0.5
    t.c[chips] = np.array(under, np.float32)
    t.h[chips] -= 0.15
    rust = chips & (t.rng.random((N, N)) < 0.4)
    t.c[rust] = t.c[rust] * 0.4 + np.array((120, 64, 34), np.float32) * 0.6
    t.smooth[chips] = 0.25


def bar_paint(seed):
    """The bars: the pale grey-green every cellhouse was painted, worn bright where hands held them."""
    t = F.plain(seed, BARS, 0.05, 0.5)
    t.f0[:] = 40
    flaking(t, 0.25)
    yy = np.mgrid[0:N, 0:N][0]
    t.mix(np.exp(-((yy - 30) / 7.0) ** 2) * 0.35, (176, 180, 176))
    t.grime(0.2, (70, 66, 56))
    return t


def wired(seed, broken=False):
    """Wired glass: the diamond mesh in it, frosted with years of grime, a crack or two."""
    t = F.plain(seed, (168, 182, 178), 0.05, 0.8)
    t.f0[:] = 60
    t.alpha[:] = 170
    yy, xx = np.mgrid[0:N, 0:N]
    mesh = ((xx + yy) % 8 == 0) | ((xx - yy) % 8 == 0)
    t.c[mesh] *= 0.55
    t.alpha[mesh] = 230
    g = np.clip(P.fbm(t.rng) + 0.3, 0, 1)
    t.mix(g * 0.35, (120, 116, 96))
    t.alpha += g * 50
    for _ in range(2 if broken else 1):
        t.crack(1, 0.1, (40, 40, 40))
    if broken:
        disc(t, 28, 36, 12, alpha=0, soft=3)
    np.clip(t.alpha, 0, 255, out=t.alpha)
    return t


def cell_door(seed, top):
    """A barred door: flat straps across, round bars every eight, the lock box at the latch side
    (the bottom half's top), the number stencilled on the top strap. Clear between the bars."""
    t = F.plain(seed, BARS, 0.05, 0.5)
    t.f0[:] = 40
    t.alpha[:] = 0
    for x in range(3, N, 8):
        area(t, x, 0, x + 3, N, alpha=255, dh=0.2)
        area(t, x, 0, x + 1, N, mul=(1.15, 1.15, 1.15))
    for y in ((2, 7) if top else (28, 34)):
        area(t, 0, y, N, y + 5, alpha=255, dh=0.1)
    area(t, 0, 0, 3, N, alpha=255)
    area(t, N - 3, 0, N, N, alpha=255)
    if not top:
        area(t, 0, N - 4, N, N, alpha=255)
        area(t, 46, 6, 62, 24, col=(118, 124, 116), alpha=255, dh=0.3)
        area(t, 52, 12, 56, 18, col=(30, 30, 30), alpha=255, dh=-0.2)
    else:
        p = Pic(1, 1, (0, 0, 0, 0), seed)
        p.text(32, 4.5, str(seed % 40 + 1), STENCIL, 5, (240, 238, 220), anchor="mm")
        a = p.shrunk()
        m = a[..., 3] > 60
        t.c[m] = a[..., :3][m]
    flaking(t, 0.2)
    t.grime(0.25, (60, 56, 48))
    return t


def seg_door(seed, top):
    """The hole's door: solid steel in old green, rivets round the edge, the peep slot with its
    cover up top, the food slot's hatch on the bottom half; paint gone where boots kicked it."""
    t = F.plain(seed, (104, 132, 108), 0.06, 0.4)
    t.f0[:] = 30
    for i in range(4, N, 10):
        for x in (3, N - 4):
            disc(t, x, i, 1.0, col=(80, 100, 84), dh=0.3)
    if top:
        area(t, 20, 26, 44, 32, col=(26, 26, 26), dh=-0.4)
        area(t, 18, 22, 46, 26, col=(86, 110, 90), dh=0.3)
    else:
        area(t, 14, 4, 50, 16, col=(84, 106, 88), dh=0.35)
        area(t, 14, 15, 50, 16, col=(40, 44, 40), dh=-0.2)
        area(t, 30, 7, 34, 9, col=(150, 150, 140), dh=0.4)
        area(t, 50, 30, 58, 40, col=(130, 130, 120), dh=0.4)
        yy = np.mgrid[0:N, 0:N][0]
        t.mix(np.clip((yy - 44) / 20.0, 0, 1) * np.clip(P.fbm(t.rng) + 0.5, 0, 1), (64, 60, 50), 0.6)
    flaking(t, 0.3, (58, 60, 58))
    t.grime(0.3, (56, 54, 46))
    return t


def ext(seed, kind=""):
    """Whitewashed concrete: the board-form lines of the old pour under the limewash, weathered
    to grey where the rain runs, salt bloom at the foot; streaked or green where it never dries."""
    t = F.plain(seed, (214, 210, 198), 0.06, 0.15)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=(0.9, 0.9, 0.9), dh=-0.15)
    t.mix(np.clip(P.fbm(t.rng) * 1.2, 0, 1) * 0.3, (168, 164, 154))
    t.streaks(0.6 if kind == "stain" else 0.3, 0.72)
    if kind == "stain":
        t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.2, 0, 1) * 0.4, (120, 96, 70))
    if kind == "moss":
        yy = np.mgrid[0:N, 0:N][0]
        t.mix(np.clip((yy - 24) / 40.0, 0, 1) * np.clip(P.fbm(t.rng) + 0.4, 0, 1), (88, 104, 60), 0.7)
    t.crack(2, 0.2, (90, 88, 80))
    return t


def paint(seed, rgb, dado=False):
    """Gloss paint on rendered block, flaking where damp gets in; the dado's darker band and the
    pin stripe along its top."""
    t = F.plain(seed, rgb, 0.04, 0.5)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=(0.94, 0.94, 0.94), dh=-0.05)
    if dado:
        area(t, 0, 0, N, 3, col=mixc(rgb, (20, 30, 20), 0.35), dh=0.1)
        area(t, 0, 4, N, 5, col=(200, 196, 170))
        yy = np.mgrid[0:N, 0:N][0]
        t.mix(np.clip((yy - 48) / 16.0, 0, 1) * 0.3, (60, 58, 50))
    flaking(t, 0.12, (170, 166, 150))
    t.grime(0.15, (80, 76, 64))
    return t


def floor(seed):
    """The flats: concrete waxed for decades, scuffed paths, a drain grating, the yellow line."""
    t = F.plain(seed, (150, 148, 140), 0.05, 0.62)
    t.f0[:] = 30
    t.mix(np.clip(P.fbm(t.rng) * 1.2, 0, 1) * 0.25, (120, 118, 110))
    t.speckle(0.03, (100, 98, 92), 0.5)
    t.crack(1, 0.15, (70, 70, 66))
    return t


def floor_line(seed):
    t = floor(seed)
    area(t, 0, 26, N, 38, col=(206, 168, 46), dh=0.05)
    t.mix(np.clip(P.fbm(t.rng) - 0.1, 0, 1) * 0.5, (150, 148, 140))
    return t


def panel(seed):
    """The door board: a grey steel face, the cell numbers in rows, a toggle and a lamp each, a
    few still red."""
    t = F.plain(seed, (118, 122, 120), 0.04, 0.4)
    t.f0[:] = 50
    for r in range(4):
        for c in range(6):
            x, y = 6 + c * 9, 8 + r * 14
            area(t, x, y, x + 6, y + 2, col=(220, 220, 210))
            area(t, x + 2, y + 4, x + 4, y + 9, col=(200, 200, 196), dh=0.4)
            lit = t.rng.random() < 0.25
            disc(t, x + 3, y + 11.5, 1.4, col=(230, 60, 40) if lit else (90, 40, 34))
            if lit:
                t.emit[y + 10:y + 13, x + 2:x + 5] = 0.8
    t.grime(0.2)
    return t


def plate_tex(seed):
    """A blank plate fresh off the press: embossed rim, the state band, RAVENROCK made."""
    t = F.plain(seed, (232, 230, 220), 0.03, 0.6)
    t.f0[:] = 80
    area(t, 0, 0, N, 3, col=(150, 150, 146), dh=0.4)
    area(t, 0, N - 3, N, N, col=(150, 150, 146), dh=0.4)
    area(t, 0, 4, N, 14, col=(36, 70, 120))
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 9, "GRAYHAVEN", BOLD, 5, (240, 240, 230), anchor="mm")
    p.text(32, 36, "RVN 0" + str(seed % 900 + 100), STENCIL, 9, (36, 70, 120), anchor="mm")
    a = p.shrunk()
    m = a[..., 3] > 60
    t.c[m] = a[..., :3][m]
    t.h[m] += 0.3
    return t


def vinyl(seed):
    t = F.plain(seed, (36, 36, 38), 0.05, 0.55)
    t.crack(3, 0.2, (90, 80, 60))
    area(t, 20, 20, 30, 34, col=(120, 100, 70), dh=-0.2)
    return t


def mattress(seed):
    """A thin striped prison mattress, stained."""
    t = F.plain(seed, (200, 196, 180), 0.04, 0.2)
    for x in range(0, N, 8):
        area(t, x, 0, x + 2, N, col=(110, 120, 140))
    t.mix(np.clip(P.fbm(t.rng) - 0.2, 0, 1) * 0.6, (150, 120, 70))
    return t


# ---- signs

SIGNS = {
    "a": ("A BLOCK", NAVY, WHITE),
    "b": ("B BLOCK", NAVY, WHITE),
    "c": ("C BLOCK", NAVY, WHITE),
    "d": ("D BLOCK", NAVY, WHITE),
    "seg": ("SEGREGATION", (120, 24, 22), WHITE),
    "mess": ("DINING HALL", NAVY, WHITE),
    "infirm": ("INFIRMARY", (30, 90, 60), WHITE),
    "laundry": ("LAUNDRY", NAVY, WHITE),
    "shop": ("PLATE SHOP", NAVY, WHITE),
    "visit": ("VISITORS REPORT HERE", NAVY, WHITE),
    "control": ("CONTROL", (120, 24, 22), WHITE),
    "sally": ("SALLY PORT", YELLOW, INK),
    "hostage": ("NO HOSTAGES WILL|BE EXCHANGED", WHITE, RED),
    "warn": ("NO WARNING SHOTS|IN THIS AREA", YELLOW, INK),
    "line": ("DO NOT CROSS|YELLOW LINE", YELLOW, INK),
    "staff": ("STAFF ONLY", (120, 24, 22), WHITE),
    "ferry": ("RAVENROCK FERRY|AUTHORIZED ONLY", NAVY, WHITE),
    "dock": ("RAVENROCK|LANDING", NAVY, WHITE),
    "bell": ("RING FOR|THE FERRY", YELLOW, INK),
    "warden": ("WARDEN", NAVY, WHITE),
    "chapel": ("CHAPEL", NAVY, WHITE),
    "admin": ("ADMINISTRATION", NAVY, WHITE),
}
SPRAYS = {
    "left": ("THEY LEFT US", 4),
    "open": ("OPEN THE GATES", 4),
    "safe": ("SAFE INSIDE", 3),
    "count": ("COUNT TIME", 3),
}


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 51400 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 14, W - 1, H - 14, fill=field)
    p.rect(1, 14, W - 1, H - 14, outline=mixc(field, (0, 0, 0), 0.3), width=1)
    lines = text.split("|")
    font = STENCIL if field in (YELLOW, WHITE) else BOLD
    size = civic.fit(text, font, 12 if len(lines) == 1 else 9, W - 12)
    for i, line in enumerate(lines):
        y = H / 2 + (i - (len(lines) - 1) / 2) * size * 1.15
        p.text(W / 2, y, line, font, size, ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 51450 + i + len(key) * 11, smooth=0.5, grime=0.25)


@lru_cache(maxsize=None)
def name_pic():
    """The name over the gatehouse: raised letters on the limestone band, six blocks wide."""
    p = Pic(6, 1, (0, 0, 0, 0), 51500)
    W, H = p.W, p.H
    p.rect(0, 10, W, H - 10, fill=(196, 188, 164))
    p.rect(0, 10, W, H - 10, outline=(150, 142, 120), width=2)
    p.text(W / 2, H / 2, "RAVENROCK STATE PENITENTIARY", OLD, civic.fit("RAVENROCK STATE PENITENTIARY", OLD, 22, W - 16),
           (64, 58, 48), anchor="mm")
    return p


def name_tile(i):
    return name_pic().tile(i, 51510 + i, smooth=0.25, grime=0.35)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 51600 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (200, 24, 30) if key != "count" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 51610 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "count" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def textures():
    T = {
        "pn_bar": lambda: bar_paint(51000),
        "pn_glass": lambda: wired(51001),
        "pn_glass_broken": lambda: wired(51002, True),
        "pn_cdoor_top": lambda: cell_door(51003, True),
        "pn_cdoor_bottom": lambda: cell_door(51004, False),
        "pn_sdoor_top": lambda: seg_door(51005, True),
        "pn_sdoor_bottom": lambda: seg_door(51006, False),
        "pn_ext": lambda: ext(51007),
        "pn_ext_stain": lambda: ext(51008, "stain"),
        "pn_ext_moss": lambda: ext(51009, "moss"),
        "pn_paint": lambda: paint(51010, CREAM),
        "pn_dado": lambda: paint(51011, GREEN, True),
        "pn_floor": lambda: floor(51012),
        "pn_floor_line": lambda: floor_line(51013),
        "pn_panel": lambda: panel(51014),
        "pn_plate": lambda: plate_tex(51015),
        "pn_vinyl": lambda: vinyl(51016),
        "pn_mattress": lambda: mattress(51017),
        "pn_green": lambda: seg_paint(51018),
    }
    for key in SIGNS:
        for i in range(2):
            T[f"pn_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for i in range(6):
        T[f"pn_name_{i}"] = lambda i=i: name_tile(i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"pn_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def seg_paint(seed):
    t = F.plain(seed, (104, 132, 108), 0.05, 0.4)
    flaking(t, 0.2, (58, 60, 58))
    t.grime(0.2, (56, 54, 46))
    return t


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

BAR, STEEL, SS, CHROME, BLACK, GALV = "pn_bar", "painted_steel", "fit_stainless", "home_chrome", "home_plastic_black", "st_galv"


def bars():
    """Four round bars in the plane x = 8 along z, the flat straps at the foot, the middle, the top."""
    b = [[7.3, 0, z, 8.7, 16, z + 1.4, BAR] for z in (1.3, 5.3, 9.3, 13.3)]
    b += [[7, 0, 0, 9, 1.5, 16, BAR], [7, 7.2, 0, 9, 8.4, 16, BAR], [7, 14.6, 0, 9, 16, 16, BAR]]
    return b


def window(broken):
    """The sash flush with the inside of the wall, small panes of wired glass in a steel grid, the
    bars outside (north) set in the reveal."""
    glass = "pn_glass_broken" if broken else "pn_glass"
    b = [[0, 0, 9, 16, 16, 10, glass]]
    b += [[0, 0, 8.6, 16, 1, 10.4, STEEL], [0, 15, 8.6, 16, 16, 10.4, STEEL], [0, 0, 8.6, 1, 16, 10.4, STEEL],
          [15, 0, 8.6, 16, 16, 10.4, STEEL], [7.5, 0, 8.6, 8.5, 16, 10.4, STEEL], [0, 7.5, 8.6, 16, 8.5, 10.4, STEEL],
          [3.6, 0, 8.8, 4.2, 16, 10.2, STEEL], [11.8, 0, 8.8, 12.4, 16, 10.2, STEEL],
          [0, 3.6, 8.8, 16, 4.2, 10.2, STEEL], [0, 11.8, 8.8, 16, 12.4, 10.2, STEEL]]
    b += [[x, 0, 2, x + 1.4, 16, 3.4, BAR] for x in (1.3, 5.3, 9.3, 13.3)]
    b += [[0, 7.4, 1.8, 16, 8.6, 3.6, BAR]]
    if broken:
        b[0] = [0, 0, 9, 7.5, 7.5, 10, glass]
        b.insert(1, [8.5, 8.5, 9, 16, 16, 10, glass])
    return b


def catwalk():
    return [[0, 13, 0, 16, 16, 16, {"up": "grate", "down": "grate", "side": STEEL}],
            [0, 12, 0, 16, 13, 1.2, STEEL], [0, 12, 14.8, 16, 13, 16, STEEL]]


def rail(end):
    """Mesh railing on the walkway's north edge: kick plate, posts, the mesh, the top rail."""
    b = [[0, 0, 0, 16, 2, 1, STEEL], [0, 14, 0, 16, 15.6, 1.6, STEEL], [0, 7, 0.2, 16, 8, 1.2, STEEL],
         [0, 2, 0.4, 16, 14, 0.8, "chainlink"], [7.2, 0, 0, 8.8, 15, 1.6, STEEL]]
    if end:
        b.append([0, 0, 0, 1.6, 15.6, 1.6, STEEL])
    return b


def toilet_sink():
    """The combination unit against the back wall (south): the cabinet, the bowl out front with no
    seat, the basin in its top, the push buttons, the cup left on it."""
    return [[3, 0, 9, 13, 14, 16, SS], [2.6, 14, 8.6, 13.4, 14.6, 16, SS],
            [4.5, 0, 3, 11.5, 6, 9, SS], [4, 6, 2.5, 12, 7, 9, SS], [5.5, 6.2, 3.8, 10.5, 7.1, 8, "home_toilet_water"],
            [5, 12, 9.4, 11, 14.2, 13.6, "home_basin"], [7.3, 14, 14, 8.7, 15.4, 15, CHROME],
            [12.2, 9, 8.8, 13, 10, 9.6, CHROME], [3, 9, 8.8, 3.8, 10, 9.6, CHROME],
            [11.4, 14.6, 10.5, 12.6, 16, 11.7, "fit_grey"]]


def cell_desk():
    """The steel desk on its wall brackets, a book and a cup on it; the stool on its arm swung out."""
    return [[1, 10.5, 9, 15, 11.5, 16, STEEL], [2, 8, 15, 3, 10.5, 16, STEEL], [13, 8, 15, 14, 10.5, 16, STEEL],
            [3, 11.5, 11, 8, 13, 15, "home_books"], [10, 11.5, 12, 11.6, 13.4, 13.6, "fit_grey"],
            [6, 6.5, 3, 10, 7.5, 7, STEEL], [7.4, 0, 4.4, 8.6, 6.5, 5.6, STEEL], [7.4, 2, 5, 8.6, 3, 16, STEEL]]


def shelf():
    """The shelf high on the back wall: folded clothes, the books, a soap tin, a photo propped up."""
    return [[0, 12, 11, 16, 12.8, 16, STEEL], [1, 8, 15, 2, 12, 16, STEEL], [14, 8, 15, 15, 12, 16, STEEL],
            [1, 12.8, 11.5, 6, 15, 15.5, "home_clothes"], [7, 12.8, 12, 11, 15.8, 15.6, "home_books"],
            [12, 12.8, 12, 14, 13.4, 14, CHROME], [12, 12.8, 14.6, 15, 15, 15, "paper"]]


def mess_table():
    """A round-ish steel table bolted down on its one post, four stools out on arms from its foot."""
    b = [[3, 10.5, 3, 13, 11.5, 13, SS], [4, 11.5, 2.4, 12, 11.6, 13.6, SS], [7, 0, 7, 9, 10.5, 9, STEEL],
         [5, 0, 5, 11, 0.6, 11, STEEL]]
    for x0, z0 in ((6.5, -0.5), (6.5, 13.5), (-0.5, 6.5), (13.5, 6.5)):
        b.append([x0 + 0.5, 6.5, z0 + 0.5, x0 + 3, 7.4, z0 + 3, SS])
        b.append([x0 + 1.2, 0, z0 + 1.2, x0 + 2.3, 6.5, z0 + 2.3, STEEL])
    b += [[7.5, 1.5, 1, 8.5, 2.5, 15, STEEL], [1, 1.5, 7.5, 15, 2.5, 8.5, STEEL]]
    return b


def visit_booth(top):
    """A booth: the partitions either side, the counter, the glass between the two sides, the
    handset on its hook and the cord; a stool each side. pn_visit_top carries the glass on up."""
    part = {"north": "desk_laminate", "south": "desk_laminate", "side": "pn_paint", "up": STEEL, "down": STEEL}
    if top:
        return [[0, 0, 1, 1, 9, 15, part], [15, 0, 1, 16, 9, 15, part], [1, 0, 7.6, 15, 9, 8.4, "pn_glass"],
                [0, 9, 7, 16, 10, 9, STEEL], [1, 0, 7.2, 1.6, 9, 8.8, STEEL], [14.4, 0, 7.2, 15, 9, 8.8, STEEL]]
    return [[0, 0, 1, 1, 16, 15, part], [15, 0, 1, 16, 16, 15, part],
            [1, 9.5, 3, 15, 10.5, 13, "desk_laminate"], [1, 0, 7, 15, 9.5, 9, "pn_paint"],
            [1, 10.5, 7.6, 15, 16, 8.4, "pn_glass"], [1, 10.5, 7.2, 15, 11, 8.8, STEEL],
            [1, 12, 4, 2.4, 15, 5.4, BLACK], [1.2, 9, 4.4, 1.8, 12, 5, BLACK],
            [1, 12, 10.6, 2.4, 15, 12, BLACK],
            [6, 6.5, 0, 10, 7.4, 3, SS], [7.4, 0, 1, 8.6, 6.5, 2.2, STEEL],
            [6, 6.5, 13, 10, 7.4, 16, SS], [7.4, 0, 13.8, 8.6, 6.5, 15, STEEL]]


def bench():
    """A flat bench under the rack, the bar on it loaded with plates, the uprights at its head."""
    return [[6, 5.5, 1, 10, 7.5, 13, "pn_vinyl"], [7, 0, 2, 9, 5.5, 3, STEEL], [7, 0, 11, 9, 5.5, 12, STEEL],
            [6, 0, 1.5, 10, 0.8, 3.5, STEEL], [6, 0, 10.5, 10, 0.8, 12.5, STEEL],
            [3, 0, 13, 4, 14, 14, STEEL], [12, 0, 13, 13, 14, 14, STEEL], [2.5, 0, 12, 4.5, 0.8, 15, STEEL],
            [11.5, 0, 12, 13.5, 0.8, 15, STEEL],
            [-1, 13.6, 13, 17, 14.4, 13.8, CHROME], [-1, 10, 11.4, 1, 18, 15.4, "home_plastic_black"],
            [15, 10, 11.4, 17, 18, 15.4, "home_plastic_black"]]


def dumbbells():
    b = [[1, 0, 6, 2, 9, 12, STEEL], [14, 0, 6, 15, 9, 12, STEEL], [1, 4, 6, 15, 5, 12, STEEL], [1, 8, 7, 15, 9, 11, STEEL]]
    for i, x in enumerate((2.5, 6.0, 9.5, 12.5)):
        w = 1.0 + i * 0.25
        for y in (5, 9):
            b += [[x, y, 7.6 - w, x + 1.6, y + 2 * w, 7.6 + 0.2, BLACK], [x + 0.5, y + w - 0.3, 7.6, x + 1.1, y + w + 0.3, 10.4, CHROME],
                  [x, y, 10.2, x + 1.6, y + 2 * w, 10.4 + w, BLACK]]
    return b


def pullup(bar):
    b = [[1, 0, 7, 2.6, 16, 8.6, GALV], [13.4, 0, 7, 15, 16, 8.6, GALV]]
    if bar:
        b += [[1, 13.4, 7.2, 15, 14.6, 8.4, CHROME], [0.6, 15.4, 6.6, 3, 16, 9, GALV], [13, 15.4, 6.6, 15.4, 16, 9, GALV]]
    else:
        b += [[0.4, 0, 6.4, 3.2, 0.8, 9.2, "concrete_panel"], [12.8, 0, 6.4, 15.6, 0.8, 9.2, "concrete_panel"]]
    return b


def post(arm):
    """A galvanised fence post with its cap; with the arm, the outrigger leaning out over the north
    side and three strands of barbed wire on it, a razor coil sitting in the crook."""
    b = [[7, 0, 7, 9, 16, 9, GALV], [6.6, 15.4, 6.6, 9.4, 16, 9.4, GALV]]
    if arm:
        b += [[7.3, 10, 1, 8.7, 16, 2.4, GALV, None, {"axis": "x", "angle": -22.5, "origin": [8, 10, 8]}],
              [0, 11.2, 4.4, 16, 11.5, 4.7, "home_chrome"], [0, 13.5, 3.0, 16, 13.8, 3.3, "home_chrome"],
              [0, 15.6, 1.8, 16, 15.9, 2.1, "home_chrome"]]
    return b


def gate(top):
    """A sliding gate panel in the plane x = 8: the tube frame, the brace, the chain link, the
    rollers on the foot; the top piece carries the razor coil along the frame."""
    if top:
        return [[7.2, 0, 0, 8.8, 16, 1.6, GALV], [7.2, 0, 14.4, 8.8, 16, 16, GALV], [7.6, 0, 1.6, 8.4, 14, 14.4, "chainlink"],
                [7.2, 14, 0, 8.8, 15.6, 16, GALV], [4, 15.6, 0, 12, 16, 16, "st_razor"]]
    return [[7.2, 0, 0, 8.8, 16, 1.6, GALV], [7.2, 0, 14.4, 8.8, 16, 16, GALV], [7.2, 1, 0, 8.8, 2.6, 16, GALV],
            [7.6, 2.6, 1.6, 8.4, 16, 14.4, "chainlink"],
            [7.3, 2.6, 7.4, 8.7, 16, 8.6, GALV, None, {"axis": "x", "angle": 45, "origin": [8, 9, 8]}],
            [6.6, 0, 3, 9.4, 1.2, 5, BLACK], [6.6, 0, 11, 9.4, 1.2, 13, BLACK]]


def lockbox():
    """The gang lock at a tier's end: a steel cabinet on the wall, its door off, the levers inside
    that throw a whole tier's bolts, the padlock hasp hanging."""
    b = [[3, 2, 11, 13, 15, 16, STEEL], [3.4, 2.4, 10.6, 12.6, 14.6, 11, "fit_grey"]]
    for x in (5, 8, 11):
        b += [[x - 0.4, 6, 9, x + 0.4, 12, 10.6, CHROME], [x - 0.8, 11.6, 8.4, x + 0.8, 12.6, 9.2, BLACK]]
    b += [[12.6, 8, 10, 13.6, 10, 11, CHROME], [12.8, 6.4, 9.8, 13.4, 8, 10.4, "fit_grey"]]
    return b


def control_panel():
    return [[1, 0, 6, 15, 9, 16, STEEL], [0.6, 9, 4, 15.4, 10, 16, STEEL],
            [1, 10, 9, 15, 15, 16, {"north": "pn_panel", "side": STEEL, "up": STEEL, "down": STEEL},
             None, {"axis": "x", "angle": -22.5, "origin": [8, 10, 9]}],
            [3, 10, 4.6, 6, 10.6, 7, "home_plastic_black"], [9, 10, 5, 12.5, 10.4, 7.5, "paper"]]


def plate_press():
    """The plate shop's press: the cast frame in machinery green, the ram, the flywheel on its
    shaft, the guard, a plate in the die."""
    green = "pn_green"
    return [[3, 0, 3, 13, 3, 13, green], [4, 3, 9, 12, 15, 13, green], [4, 12, 3, 12, 15, 9, green],
            [5, 8, 4, 11, 12, 8, CHROME], [4, 3, 3, 12, 6, 9, green], [5, 6, 4, 11, 6.4, 8, "pn_plate"],
            [12, 9, 9, 15, 15.6, 9.8, BLACK], [12, 11.6, 9.8, 13.4, 13, 12, CHROME],
            [2, 5, 4, 4, 6, 9, "cp_hazard"]]


def plates():
    b = []
    for i in range(5):
        x, z = 2 + (i % 2) * 0.6, 3 + (i % 3) * 0.4
        b.append([x, i * 0.8, z, x + 12, i * 0.8 + 0.6, z + 6, {"up": "pn_plate", "down": STEEL, "side": STEEL}])
    return b


def cart():
    """A laundry cart: the canvas bin slung in its frame, the castors, a heap of grey in it."""
    return [[1, 3, 2, 15, 13, 14, {"side": "fs_canvas", "north": "fs_canvas", "up": "home_clothes", "down": "fs_canvas"}],
            [0.6, 12.4, 1.6, 15.4, 13.4, 14.4, CHROME],
            [1, 0, 2, 2, 3, 3, CHROME], [14, 0, 2, 15, 3, 3, CHROME], [1, 0, 13, 2, 3, 14, CHROME], [14, 0, 13, 15, 3, 14, CHROME],
            [0.8, 0, 1.6, 2.2, 1.2, 3.4, BLACK], [13.8, 0, 1.6, 15.2, 1.2, 3.4, BLACK],
            [0.8, 0, 12.6, 2.2, 1.2, 14.4, BLACK], [13.8, 0, 12.6, 15.2, 1.2, 14.4, BLACK]]


def bunk(upper):
    """A steel bunk off the back wall (south): the pan, the thin striped mattress, the folded grey
    blanket; the upper one hangs on chains from the wall, the lower stands on its legs."""
    b = [[0, 6, 6, 16, 7.4, 16, {"up": "bunk_grey", "side": STEEL, "down": STEEL}],
         [0.4, 7.4, 6.4, 15.6, 8.8, 15.6, "pn_mattress"], [11, 8.8, 7, 15, 10, 15, "bunk_blanket"],
         [0.6, 8.8, 11, 4.6, 10.2, 15, "home_pillow"]]
    if upper:
        b += [[0.6, 7.4, 6.2, 1.2, 16, 6.8, CHROME], [14.8, 7.4, 6.2, 15.4, 16, 6.8, CHROME]]
    else:
        b += [[0.6, 0, 6.4, 1.8, 6, 7.6, STEEL], [14.2, 0, 6.4, 15.4, 6, 7.6, STEEL]]
    return b


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

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    add("pn_bars", bars(), "Cell Bars", "METAL", rotate="axis", render="cutout", shape=[[7, 0, 0, 9, 16, 16]])
    add("pn_window", window(False), "Barred Window", "GLASS", render="translucent", shape=[[0, 0, 2, 16, 16, 10.4]])
    add("pn_window_broken", window(True), "Barred Window", "GLASS", render="translucent", shape=[[0, 0, 2, 16, 16, 10.4]])
    add("pn_catwalk", catwalk(), "Tier Walkway", "METAL", rotate=None, render="cutout", shape=[[0, 13, 0, 16, 16, 16]])
    add("pn_rail", rail(False), "Tier Railing", "METAL", render="cutout", shape=[[0, 0, 0, 16, 24, 1.6]])
    add("pn_rail_end", rail(True), "Tier Railing", "METAL", render="cutout", shape=[[0, 0, 0, 16, 24, 1.6]])
    add("pn_toilet_sink", toilet_sink(), "Cell Toilet", "METAL", shape=[[3, 0, 3, 13, 14.6, 16]])
    add("pn_cell_desk", cell_desk(), "Cell Desk", "METAL", shape=[[1, 10.5, 9, 15, 11.5, 16], [6, 0, 3, 10, 7.5, 7]],
        loot="home_desk")
    add("pn_shelf", shelf(), "Cell Shelf", "METAL", decor=True, loot="home_desk")
    add("pn_bunk", bunk(False), "Cell Bunk", "METAL", shape=[[0, 0, 6, 16, 9, 16]])
    add("pn_bunk_upper", bunk(True), "Cell Bunk", "METAL", shape=[[0, 6, 6, 16, 9, 16]])
    add("pn_mess_table", mess_table(), "Mess Table", "METAL", rotate=None, shape=[[3, 0, 3, 13, 11.6, 13]])
    add("pn_visit_booth", visit_booth(False), "Visiting Booth", "METAL", render="translucent",
        shape=[[0, 0, 1, 1, 16, 15], [15, 0, 1, 16, 16, 15], [1, 0, 7, 15, 16, 9]])
    add("pn_visit_top", visit_booth(True), "Visiting Booth", "GLASS", render="translucent",
        shape=[[0, 0, 1, 1, 10, 15], [15, 0, 1, 16, 10, 15], [1, 0, 7, 15, 10, 9]])
    add("pn_bench", bench(), "Weight Bench", "METAL", shape=[[3, 0, 1, 13, 8, 15]])
    add("pn_dumbbells", dumbbells(), "Dumbbell Rack", "METAL", shape=[[1, 0, 5, 15, 12, 13]])
    add("pn_pullup", pullup(False), "Pull-up Bars", "METAL", rotate="axis", shape=[[1, 0, 7, 15, 16, 8.6]])
    add("pn_pullup_bar", pullup(True), "Pull-up Bars", "METAL", rotate="axis", shape=[[1, 0, 7, 15, 16, 8.6]])
    add("pn_post", post(False), "Fence Post", "METAL", rotate=None, shape=[[7, 0, 7, 9, 16, 9]])
    add("pn_post_arm", post(True), "Fence Post", "METAL", render="cutout", shape=[[7, 0, 7, 9, 16, 9]])
    add("pn_gate", gate(False), "Sliding Gate", "METAL", rotate="axis", render="cutout", shape=[[7, 0, 0, 9, 16, 16]])
    add("pn_gate_top", gate(True), "Sliding Gate", "METAL", rotate="axis", render="cutout", shape=[[7, 0, 0, 9, 16, 16]])
    add("pn_lockbox", lockbox(), "Gang Lock", "METAL", shape=[[3, 2, 9, 13, 15, 16]])
    add("pn_panel", control_panel(), "Door Control Board", "METAL", shape=[[1, 0, 4, 15, 15, 16]])
    add("pn_plate_press", plate_press(), "Plate Press", "METAL", shape=[[3, 0, 3, 13, 15, 13]])
    add("pn_plates", plates(), "Licence Plates", "METAL", rotate="axis", shape=[[2, 0, 3, 15, 4, 10]], loot="basement_supplies")
    add("pn_cart", cart(), "Laundry Cart", "WOOL", shape=[[1, 0, 2, 15, 13.4, 14]], loot="home_wardrobe")
    add("pn_cell_door", None, "Cell Door", "METAL", rotate=None, door="steel",
        **{"tex": {"top": "pn_cdoor_top", "bottom": "pn_cdoor_bottom"}})
    add("pn_seg_door", None, "Segregation Door", "METAL", rotate=None, door="steel",
        **{"tex": {"top": "pn_sdoor_top", "bottom": "pn_sdoor_bottom"}})
    for n in ("pn_ext", "pn_ext_stain", "pn_ext_moss"):
        add(n, None, "Whitewashed Concrete", "STONE", rotate=None, tex=n)
    add("pn_paint", None, "Painted Wall", "STONE", rotate=None, tex="pn_paint")
    add("pn_dado", None, "Painted Wall", "STONE", rotate=None, tex="pn_dado")
    add("pn_floor", None, "Concrete Floor", "STONE", rotate=None, tex={"up": "pn_floor", "side": "concrete_panel", "down": "concrete_panel"})
    add("pn_floor_line", None, "Concrete Floor", "STONE", rotate=None,
        tex={"up": "pn_floor_line", "side": "concrete_panel", "down": "concrete_panel"})
    for key in SIGNS:
        for i in range(2):
            add(f"pn_sgn_{key}_{i}", plate(f"pn_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for i in range(6):
        add(f"pn_name_{i}", plate(f"pn_name_{i}", 14.6), "Stone Lettering", "STONE", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"pn_spray_{key}_{i}", plate(f"pn_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("pn_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} prison blocks in {path}")


if __name__ == "__main__":
    main()
