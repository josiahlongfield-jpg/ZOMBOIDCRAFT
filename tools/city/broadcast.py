"""Grayhaven Broadcast Center: the tv_ kit for WGHV Channel 6, its tower and its dishes
(world/county/Broadcast.java, BroadcastHouse.java, BroadcastTower.java).

No photos of it in the library yet (city/tv-station-* to be queued): this follows what the
American network affiliates of the seventies and eighties were known for. A low brown-brick
block with bronze ribbon windows and the call letters on the parapet, the windowless studio box
beside it with the station's slogan painted down its side; NewsCenter 6's set under the lighting
grid, the control room's monitor wall, master control's racks of tape decks, the newsroom's wire
machines and police scanners, the FM station upstairs with its turntables and cart machines.
Behind it the self-supporting lattice tower, painted in its red and white aviation bands up to
the antenna, the ladder up its spine, the ice bridge carrying the lines over from the transmitter
building, and the farm of satellite dishes. The last weeks: the station stayed on the air as the
county's emergency broadcaster, the staff living on the top floor until the tape ran out.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the tower: tv_leg_C (a leg, C = r red or w white), tv_diag_C (one diagonal across the block in
  its face plane, the face along x at z = 8; turned it is the other way), tv_diagx_C (both),
  tv_girt_C (a horizontal along the face), tv_grate (a platform's floor), tv_rail (its rail),
  tv_spine (the centre column the ladder and the lines go up), tv_ladder (caged), tv_beacon,
  tv_beacon_lit, tv_beacon_top(_lit), tv_ant (the top mast), tv_ant_bay (an FM bay on its arm),
  tv_mw (a microwave drum), tv_coax (rigid line, axis), tv_ice (the ice bridge's deck, axis);
- the dishes: tv_dish (a 45-degree plate, the reflector's face up toward the facing), tv_feed,
  tv_strut, tv_dish_small, tv_pier;
- the station: tv_tx(_on), tv_tx_top(_on) (the transmitter's two blocks), tv_rack(_on),
  tv_vtr(_on), tv_switcher(_on), tv_audio, tv_monitor(_on), tv_onair(_on), tv_desk, tv_desk_end,
  tv_cam_lo, tv_cam_hi, tv_pipe (the lighting grid's pipe, axis), tv_fresnel, tv_tapes,
  tv_records, tv_wire, tv_scanner, tv_console, tv_turntables, tv_carts, tv_mic, tv_case,
  tv_flat, tv_cable, tv_floor, tv_cyc, tv_chroma, tv_acoustic;
- pictures: tv_name_0..13 (the call letters, one row), tv_logo_0..3 (two by two), tv_mural_0..23
  (twelve by two), tv_set_0..31 (the set's backdrop, eight by four), tv_wx_0..5 (the weather map,
  three by two), tv_mwall_0..17 and tv_mwall_on_0..17 (the monitor wall, six by three),
  tv_promo_0..3, tv_banner_0..5 (six by one), tv_sgn_KEY_0..1 (two by one),
  tv_spray_KEY_i.

    python3 tools/city/broadcast.py          rewrites the tv_ entries in blocks.json

photo_textures.py draws the textures (broadcast.register()).
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
from furniture import area, disc, drips  # noqa: E402
from school import Pic, mixc, HAND  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
STENCIL = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
SPRAY = "/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf"
NAVY = (22, 40, 92)
ORANGE = (232, 112, 30)
WHITE = (244, 242, 236)
AVRED = (196, 52, 34)
YELLOW = (230, 186, 30)

SIGNS = {
    "news": ("NEWSROOM", NAVY, WHITE),
    "studio": ("STUDIO A", NAVY, WHITE),
    "control": ("CONTROL ROOM", NAVY, WHITE),
    "master": ("MASTER CONTROL", NAVY, WHITE),
    "tapes": ("TAPE LIBRARY", NAVY, WHITE),
    "edit": ("EDITING", NAVY, WHITE),
    "weather": ("WEATHER CENTER", NAVY, WHITE),
    "sales": ("SALES", NAVY, WHITE),
    "gm": ("GENERAL MANAGER", NAVY, WHITE),
    "eng": ("ENGINEERING", NAVY, WHITE),
    "radio": ("WGHV-FM 98.1", ORANGE, WHITE),
    "records": ("RECORD LIBRARY", ORANGE, WHITE),
    "prod": ("PRODUCTION", NAVY, WHITE),
    "break": ("BREAK ROOM", NAVY, WHITE),
    "green": ("GREEN ROOM", NAVY, WHITE),
    "conf": ("CONFERENCE", NAVY, WHITE),
    "dock": ("SCENE DOCK", NAVY, WHITE),
    "tx": ("TRANSMITTER", NAVY, WHITE),
    "gen": ("GENERATOR", NAVY, WHITE),
    "security": ("SECURITY", NAVY, WHITE),
    "men": ("MEN", NAVY, WHITE),
    "women": ("WOMEN", NAVY, WHITE),
    "staff": ("STAFF ONLY", (130, 24, 28), WHITE),
    "quiet": ("QUIET - TAPING", (130, 24, 28), WHITE),
    "ebs": ("EMERGENCY BROADCAST", (170, 22, 22), WHITE),
    "rf": ("DANGER HIGH RF", YELLOW, (16, 16, 16)),
    "climb": ("NO CLIMBING", YELLOW, (16, 16, 16)),
}
SPRAYS = {
    "onair": ("STILL ON AIR", 3),
    "third": ("WE ARE ON 3RD FLOOR", 3),
    "trust": ("DONT TRUST THE BROADCAST", 4),
}


# ---------------------------------------------------------------- the steel

def steel(seed, rgb, rough=0.5):
    """Painted tower steel: the aviation paint chalked by the weather, flaking at the bolts, rust
    run down from every joint."""
    t = F.plain(seed, rgb, 0.05, 0.35)
    t.vary(0.05)
    t.speckle(0.05 * rough, (110, 64, 40), 0.6)
    t.speckle(0.08, mixc(rgb, (255, 255, 255), 0.25), 0.3)
    for x in (6, 58):
        for y in range(5, N, 16):
            disc(t, x, y, 1.4, mixc(rgb, (60, 50, 40), 0.4), h=0.9)
    t.streaks(0.6 * rough, 0.75)
    t.grime(0.18)
    return t


def galv(seed):
    """Hot-dip galvanised steel gone dull, the spangle still in it, white rust in the corners."""
    t = F.plain(seed, (150, 154, 156), 0.05, 0.5)
    t.f0[:] = 120
    t.vary(0.08)
    t.speckle(0.2, (176, 180, 182), 0.3)
    t.speckle(0.05, (210, 210, 200), 0.4)
    t.streaks(0.4, 0.8)
    t.grime(0.15)
    return t


def grate(seed):
    """Bar grating: the bearing bars a hand apart, the cross rods, the gaps you see down through."""
    t = galv(seed)
    t.alpha[:] = 0
    for k in range(0, N, 6):
        t.alpha[:, k:k + 2] = 255
    for k in range(0, N, 16):
        t.alpha[k:k + 1, :] = 255
    t.alpha[:2, :] = 255
    t.alpha[-2:, :] = 255
    t.alpha[:, :2] = 255
    t.alpha[:, -2:] = 255
    return t


def coax(seed):
    """Rigid transmission line: copper gone brown and green, the flange bolts every metre."""
    t = F.plain(seed, (150, 96, 60), 0.06, 0.5)
    t.f0[:] = 200
    t.speckle(0.2, (90, 120, 96), 0.4)
    for y in (0, 32):
        area(t, 0, y, N, y + 3, col=(110, 70, 44), dh=0.1)
    t.grime(0.2)
    return t


def ant(seed):
    """The top mast's radome: fibreglass panels gone grey, the slot joints, a stain where the beacon drips."""
    t = F.plain(seed, (196, 194, 186), 0.04, 0.3)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.75)
    for x in range(0, N, 21):
        area(t, x, 0, x + 1, N, mul=0.85)
    t.streaks(0.7, 0.75)
    t.grime(0.2)
    return t


def beacon(seed, lit):
    """An obstruction light: the red fresnel globe, its ribs; lit, glowing."""
    t = F.plain(seed, (200, 40, 30) if lit else (110, 26, 22), 0.04, 0.8)
    yy, xx = np.mgrid[0:N, 0:N]
    for k in range(0, N, 8):
        area(t, 0, k, N, k + 2, mul=0.8 if not lit else 0.92)
    if lit:
        t.c[:] = np.clip(t.c * 1.25 + 30, 0, 255)
        t.emit[:] = 1.0
    return t


def dish(seed, back=False):
    """The reflector: white panels, the seams round in rings and out in spokes; behind, grey ribs."""
    if back:
        t = F.plain(seed, (120, 124, 126), 0.05, 0.4)
        for k in range(0, N, 16):
            area(t, k, 0, k + 3, N, col=(90, 92, 94), dh=0.2)
        t.streaks(0.5, 0.7)
        t.grime(0.2)
        return t
    t = F.plain(seed, (226, 226, 220), 0.04, 0.35)
    for k in range(0, N, 32):
        area(t, k, 0, k + 1, N, mul=0.82)
        area(t, 0, k, N, k + 1, mul=0.82)
    t.streaks(0.4, 0.8)
    t.grime(0.18, (90, 96, 80))
    return t


def floor(seed, tape=False):
    """The studio floor: dark grey paint gone glossy, scuffed; with tape, the gaffer tape marks
    where the cameras stood."""
    t = F.plain(seed, (58, 58, 62), 0.04, 0.6)
    t.vary(0.06)
    rng = t.rng
    for _ in range(3 if tape else 0):
        x, y = rng.uniform(4, 52, 2)
        col = (230, 200, 40) if rng.random() < 0.5 else (230, 230, 230)
        area(t, x, y, x + 10, y + 2, col=col, smooth=0.3)
        area(t, x, y, x + 2, y + 10, col=col, smooth=0.3)
    for _ in range(25):
        x, y = rng.uniform(0, N, 2)
        area(t, x, y, x + rng.uniform(3, 14), y + 1, mul=1.25)
    t.grime(0.15)
    return t


def cyc(seed, rgb=(196, 196, 190)):
    """The cyc and the set walls: flat theatre paint over hardboard, the seams filled, scuffed at foot height."""
    t = F.plain(seed, rgb, 0.03, 0.15)
    t.vary(0.03)
    area(t, 0, 0, 1, N, mul=0.94)
    t.grime(0.12)
    return t


def acoustic(seed):
    """Studio acoustic panels: grey fabric over glass wool, a panel a block, the battens between."""
    t = F.plain(seed, (92, 94, 104), 0.05, 0.05)
    t.speckle(0.5, (80, 82, 92), 0.3, 0.05)
    area(t, 0, 0, N, 3, col=(60, 52, 44), dh=0.2)
    area(t, 0, 0, 3, N, col=(60, 52, 44), dh=0.2)
    t.grime(0.1)
    return t


# ---------------------------------------------------------------- the equipment faces

def panel(seed, rgb=(70, 74, 82)):
    t = F.plain(seed, rgb, 0.03, 0.45)
    t.grime(0.12)
    return t


def lamp(t, x, y, col, on, r=1.3):
    disc(t, x, y, r, col if on else mixc(col, (30, 30, 30), 0.7), h=0.9)
    if on:
        yy, xx = np.mgrid[0:N, 0:N]
        t.emit[(xx - x) ** 2 + (yy - y) ** 2 <= r * r] = 1.0


def meter(t, x, y, w, h, on):
    area(t, x, y, x + w, y + h, col=(232, 222, 190) if on else (170, 164, 140), smooth=0.8)
    area(t, x, y, x + w, y + 1, col=(40, 40, 40))
    needle = x + w * (0.3 + 0.4 * t.rng.random())
    area(t, needle, y + 2, needle + 1, y + h, col=(20, 20, 20))
    if on:
        t.emit[int(y):int(y + h), int(x):int(x + w)] = 0.6


def tx_front(seed, on, top=False):
    """The transmitter's cabinets: cream and blue enamel, the meters across the top, the tuning
    knobs and the lamps; the lower half louvres and the door handle."""
    t = panel(seed, (196, 190, 170))
    area(t, 0, 0, N, 6, col=NAVY)
    if top:
        for x in (6, 24, 42):
            meter(t, x, 12, 14, 12, on)
        for x in range(8, 60, 10):
            disc(t, x, 34, 2.5, (40, 40, 40), h=0.95)
        for k, x in enumerate(range(8, 60, 8)):
            lamp(t, x, 46, (230, 60, 40) if k % 3 == 0 else (90, 220, 90), on and t.rng.random() < 0.8)
        area(t, 4, 54, 60, 58, col=(40, 40, 40))
    else:
        for y in range(8, 50, 4):
            area(t, 8, y, 56, y + 2, col=(60, 60, 60), dh=-0.2)
        area(t, 54, 26, 58, 36, col=(180, 180, 180), dh=0.3, f0=200)
        area(t, 0, N - 4, N, N, col=(40, 40, 40))
    area(t, 0, 0, 1, N, mul=0.6)
    area(t, N - 1, 0, N, N, mul=0.6)
    return t


def rack(seed, on):
    """A nineteen-inch rack: patch bays, a waveform monitor, sync generators, the lamps along them."""
    t = panel(seed, (46, 48, 52))
    area(t, 0, 0, 4, N, col=(150, 150, 152), f0=120)
    area(t, 60, 0, N, N, col=(150, 150, 152), f0=120)
    y = 2
    rng = t.rng
    while y < 60:
        kind = rng.integers(0, 4)
        h = (6, 8, 12, 5)[kind]
        if y + h > 62:
            break
        area(t, 5, y, 59, y + h - 1, col=(70, 72, 76) if kind != 2 else (30, 30, 34))
        if kind == 0:
            for x in range(8, 58, 3):
                disc(t, x, y + 2, 0.8, (10, 10, 10))
                disc(t, x, y + 4, 0.8, (10, 10, 10))
        elif kind == 2:
            area(t, 18, y + 1, 46, y + h - 2, col=(20, 50, 30) if on else (14, 20, 16))
            if on:
                for x in range(19, 46):
                    yy = y + h / 2 + math.sin(x / 2) * 3
                    area(t, x, yy, x + 1, yy + 1, col=(120, 255, 140))
                t.emit[y + 1:y + h - 2, 18:46] = 0.7
        else:
            for x in range(8, 56, 6):
                lamp(t, x, y + h / 2, (90, 220, 90) if rng.random() < 0.7 else (230, 160, 40), on and rng.random() < 0.7, 1.0)
        y += h + 1
    return t


def vtr(seed, on):
    """Tape decks in their rack: the cassette slots, counters, the row of transport buttons."""
    t = panel(seed, (176, 172, 160))
    for y0 in (2, 22, 42):
        area(t, 2, y0, 62, y0 + 18, col=(150, 146, 136), dh=0.1)
        area(t, 6, y0 + 3, 34, y0 + 9, col=(20, 20, 22))
        area(t, 40, y0 + 3, 58, y0 + 7, col=(30, 10, 8))
        if on:
            area(t, 42, y0 + 4, 56, y0 + 6, col=(255, 70, 40))
            t.emit[y0 + 4:y0 + 6, 42:56] = 1.0
        for k, x in enumerate(range(8, 58, 7)):
            area(t, x, y0 + 12, x + 5, y0 + 15, col=(200, 40, 30) if k == 2 else (60, 60, 64), dh=0.2)
    return t


def switcher(seed, on):
    """The production switcher's top: the rows of square buttons, buses lit in red and green, the T-bar's slot."""
    t = panel(seed, (60, 62, 70))
    rng = t.rng
    for r, y in enumerate(range(6, 44, 7)):
        for x in range(4, 50, 5):
            col = (210, 210, 200)
            lit = on and rng.random() < 0.12
            if lit:
                col = (240, 60, 40) if r == 0 else (80, 230, 90)
            area(t, x, y, x + 4, y + 4, col=col if lit else mixc(col, (40, 40, 40), 0.3), dh=0.2)
            if lit:
                t.emit[y:y + 4, x:x + 4] = 1.0
    area(t, 54, 8, 58, 44, col=(20, 20, 20), dh=-0.3)
    area(t, 50, 22, 62, 26, col=(200, 200, 200), dh=0.4, f0=180)
    area(t, 4, 50, 60, 60, col=(40, 40, 46))
    return t


def audio(seed):
    """The audio board: channel strips, the knobs down each, the faders at the bottom, the scribble strip."""
    t = panel(seed, (80, 82, 88))
    for x in range(3, 61, 6):
        for y in range(4, 34, 6):
            disc(t, x + 2, y, 1.5, (30, 30, 30) if y > 10 else (200, 60, 40), h=0.95)
        area(t, x + 1.5, 38, x + 2.5, 58, col=(20, 20, 20))
        k = t.rng.uniform(40, 54)
        area(t, x, k, x + 5, k + 3, col=(220, 220, 214), dh=0.4)
    area(t, 0, 34, N, 36, col=(236, 232, 210))
    return t


def screen(seed, kind, on):
    """A monitor's picture: colour bars, the EBS card, snow, a camera on the empty desk, the map."""
    t = F.plain(seed, (12, 14, 16), 0.02, 0.9)
    if not on:
        yy = np.arange(N)[:, None] / N
        t.c += (np.array((40, 46, 52), np.float32) * np.clip(0.7 - yy, 0, 1)[..., None])
        t.grime(0.15, (90, 90, 90))
        return t
    rng = t.rng
    if kind == "bars":
        cols = [(192, 192, 192), (192, 192, 0), (0, 192, 192), (0, 192, 0), (192, 0, 192), (192, 0, 0), (0, 0, 192)]
        for i, c in enumerate(cols):
            area(t, i * N / 7, 0, (i + 1) * N / 7 + 1, 44, col=c)
        area(t, 0, 44, N, 50, col=(0, 0, 160))
        area(t, 0, 50, N, N, col=(20, 20, 20))
        area(t, 10, 52, 24, N, col=(230, 230, 230))
    elif kind == "ebs":
        t.c[:] = (20, 30, 130)
        p = Pic(1, 1, (0, 0, 0, 0), seed)
        p.text(32, 8, "EMERGENCY", BLACK, 8, (250, 250, 250), anchor="mm")
        p.text(32, 17, "BROADCAST SYSTEM", BOLD, 5.4, (250, 250, 250), anchor="mm")
        p.text(32, 34, "STAY INDOORS|LOCK YOUR DOORS|AWAIT INSTRUCTIONS", BOLD, 4.2, (250, 230, 120), anchor="ma")
        m = p.tile(0, seed, smooth=0.3, grime=0)
        a = m.alpha / 255
        t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    elif kind == "snow":
        v = rng.uniform(40, 230, (N, N))
        t.c[:] = v[..., None]
    elif kind == "standby":
        t.c[:] = (30, 30, 40)
        area(t, 0, 0, N, N, col=(30, 70, 140))
        disc(t, 32, 28, 16, NAVY)
        disc(t, 32, 28, 16, ORANGE, ring=2.5)
        p = Pic(1, 1, (0, 0, 0, 0), seed)
        p.text(32, 28, "6", BLACK, 24, (250, 250, 250), anchor="mm")
        p.text(32, 54, "PLEASE STAND BY", BOLD, 5, (250, 250, 250), anchor="mm")
        m = p.tile(0, seed, smooth=0.3, grime=0)
        a = m.alpha / 255
        t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    elif kind == "desk":
        t.c[:] = (40, 60, 110)
        area(t, 0, 40, N, N, col=(90, 70, 50))
        area(t, 0, 36, N, 41, col=(200, 120, 40))
        area(t, 20, 18, 30, 40, col=(30, 30, 40))
        disc(t, 25, 14, 5, (180, 140, 110))
        area(t, 40, 6, 60, 14, col=(250, 250, 250))
    else:  # map
        t.c[:] = (40, 90, 160)
        area(t, 0, 20, 40, N, col=(70, 140, 70))
        area(t, 30, 8, 54, 40, col=(90, 150, 80))
        for _ in range(4):
            x, y = rng.uniform(8, 52, 2)
            disc(t, x, y, 5, (220, 40, 30), alpha=None, ring=1)
    # scan lines and the glass's curve
    for y in range(0, N, 2):
        t.c[y] *= 0.86
    yy, xx = np.mgrid[0:N, 0:N] / N - 0.5
    t.c *= np.clip(1.15 - (xx ** 2 + yy ** 2) * 1.2, 0.5, 1.2)[..., None]
    t.emit[:] = 0.75
    return t


def onair(seed, on):
    """ON AIR in red over the studio doors, lit when it's lit."""
    t = F.plain(seed, (40, 12, 10), 0.02, 0.9)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 32, "ON AIR", BLACK, 15, (255, 70, 50) if on else (110, 40, 34), anchor="mm")
    m = p.tile(0, seed, smooth=0.3, grime=0)
    a = m.alpha / 255
    t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    if on:
        t.c[:] = np.clip(t.c + (50, 0, 0), 0, 255)
        t.emit[:] = np.clip(a, 0.3, 1.0)
    F.edges(t, 0, 0, 64, 64, w=3)
    return t


def desk_front(seed):
    """The anchor desk's front: the walnut laminate, the orange band, the 6 in its circle."""
    t = F.plain(seed, (92, 60, 40), 0.04, 0.5)
    area(t, 0, 20, N, 26, col=ORANGE)
    area(t, 0, 26, N, 28, col=(236, 236, 230))
    area(t, 0, N - 4, N, N, col=(30, 30, 30))
    t.grime(0.12)
    return t


def cam(seed):
    """The studio camera's body: grey enamel, the maker's plate, the tally light, NEWSCENTER 6."""
    t = F.plain(seed, (130, 134, 140), 0.04, 0.5)
    area(t, 0, 0, N, 4, col=(60, 60, 64))
    area(t, 6, 40, 40, 48, col=NAVY)
    area(t, 6, 44, 40, 45, col=ORANGE)
    lamp(t, 54, 10, (240, 50, 40), False, 2.5)
    t.grime(0.15)
    return t


def lens(seed):
    t = F.plain(seed, (20, 20, 24), 0.02, 0.9)
    disc(t, 32, 32, 22, (40, 44, 56), ring=4)
    disc(t, 32, 32, 14, (30, 40, 70))
    disc(t, 26, 26, 4, (140, 160, 200))
    t.f0[:] = 120
    return t


def spines(seed, kind):
    """Tape boxes or record sleeves on their shelves, the labels in hand."""
    t = F.plain(seed, (90, 70, 50), 0.04, 0.3)
    rng = t.rng
    for y0 in (2, 34):
        x = 2
        while x < 62:
            w = rng.integers(2, 4) if kind == "records" else rng.integers(4, 7)
            col = tuple(int(v) for v in (rng.integers(30, 220, 3) if kind == "records" else (rng.choice([20, 230]),) * 3))
            area(t, x, y0 + 2, x + w - 0.5, y0 + 28, col=col)
            if kind == "tapes":
                area(t, x + 1, y0 + 6, x + w - 1.5, y0 + 18, col=(236, 230, 210))
            x += w
        area(t, 0, y0 + 28, N, y0 + 31, col=(110, 86, 60), dh=0.2)
    t.grime(0.15)
    return t


def console_top(seed):
    """The radio board's top: the eight pots, the cue switches, the VU meters at the back."""
    t = panel(seed, (180, 176, 164))
    for k, x in enumerate(range(4, 60, 7)):
        disc(t, x + 3, 40, 3, (30, 30, 30), h=0.95)
        area(t, x + 1, 50, x + 5, 54, col=(200, 60, 40) if k % 2 else (230, 230, 220))
    meter(t, 8, 6, 20, 14, False)
    meter(t, 36, 6, 20, 14, False)
    return t


def turntable(seed):
    t = panel(seed, (60, 58, 56))
    for cx in (16, 48):
        disc(t, cx, 30, 13, (24, 24, 24))
        disc(t, cx, 30, 4, (200, 60, 40) if cx == 16 else (230, 220, 200))
        area(t, cx + 10, 14, cx + 11, 34, col=(200, 200, 200), f0=200)
    return t


def carts(seed):
    """Cart machines stacked three high: the slots, the start buttons, the spot labels."""
    t = panel(seed, (150, 150, 146))
    for y0 in (2, 23, 44):
        area(t, 4, y0 + 2, 40, y0 + 10, col=(30, 30, 30))
        area(t, 46, y0 + 3, 56, y0 + 9, col=(70, 200, 80))
        area(t, 4, y0 + 12, 60, y0 + 17, col=(236, 230, 200))
    return t


def wire(seed):
    """The wire machine: its yellow paper running out over the floor, the type in its lines."""
    t = F.plain(seed, (242, 236, 200), 0.03, 0.2)
    for y in range(4, N, 3):
        x1 = t.rng.uniform(20, 60)
        area(t, 4, y, x1, y + 1, col=(60, 60, 70))
    area(t, 0, 0, 2, N, col=(160, 160, 150))
    area(t, 62, 0, N, N, col=(160, 160, 150))
    return t


def case(seed):
    """A road case: black ply, aluminium edges, the corners, the station's stencil."""
    t = F.plain(seed, (32, 32, 34), 0.04, 0.3)
    F.edges(t, 0, 0, N, N, w=3)
    area(t, 0, 0, N, 3, col=(170, 170, 170), f0=180)
    area(t, 0, N - 3, N, N, col=(170, 170, 170), f0=180)
    for x, y in ((0, 0), (56, 0), (0, 56), (56, 56)):
        area(t, x, y, x + 8, y + 8, col=(190, 190, 190), f0=200, dh=0.2)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.text(32, 32, "WGHV-6", STENCIL, 9, (230, 230, 220), anchor="mm")
    m = p.tile(0, seed, smooth=0.3, grime=0)
    a = m.alpha / 255
    t.c[:] = t.c * (1 - a[..., None]) + m.c * a[..., None]
    t.grime(0.2)
    return t


def flat(seed, back=False):
    """A scenery flat: the painted front (a newsroom's brick, half the old morning show), the raw frame behind."""
    if back:
        t = F.plain(seed, (190, 176, 150), 0.04, 0.2)
        for x in (0, 30, 60):
            area(t, x, 0, x + 4, N, col=(160, 130, 90), dh=0.2)
        area(t, 0, 0, N, 4, col=(160, 130, 90), dh=0.2)
        area(t, 0, 30, N, 34, col=(160, 130, 90), dh=0.2)
        return t
    t = F.plain(seed, (120, 150, 180), 0.04, 0.2)
    area(t, 0, 40, N, N, col=(220, 200, 150))
    disc(t, 44, 20, 8, (250, 220, 120))
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the pictures

@lru_cache(maxsize=None)
def name_pic():
    """The call letters along the parapet: orange-and-white channel letters standing off the brick."""
    p = Pic(14, 1, (0, 0, 0, 0), 32000)
    W, H = p.W, p.H
    p.text(W * 0.18, H / 2 + 1, "WGHV", BLACK, 48, ORANGE, anchor="mm")
    p.text(W * 0.62, H / 2 + 1, "NEWSCENTER", BLACK, civic.fit("NEWSCENTER", BLACK, 48, W * 0.52), WHITE, anchor="mm")
    p.ellipse(W - 60, 6, W - 8, H - 6, fill=NAVY, outline=ORANGE, width=4)
    p.text(W - 34, H / 2 + 1, "6", BLACK, 44, WHITE, anchor="mm")
    return p


def name(i):
    t = name_pic().tile(i, 32010 + i, smooth=0.5, grime=0.2)
    t.streaks(0.4, 0.8)
    return t


@lru_cache(maxsize=None)
def logo_pic():
    p = Pic(2, 2, (0, 0, 0, 0), 32100)
    W, H = p.W, p.H
    p.ellipse(4, 4, W - 4, H - 4, fill=NAVY, outline=ORANGE, width=8)
    p.text(W / 2, H / 2 - 4, "6", BLACK, 92, WHITE, anchor="mm")
    p.text(W / 2, H - 22, "WGHV", BLACK, 16, ORANGE, anchor="mm")
    return p


def logo(i):
    return logo_pic().tile(i, 32110 + i, smooth=0.5, grime=0.15)


@lru_cache(maxsize=None)
def mural_pic():
    """Painted down the studio's brick in the seventies, faded since: the slogan and the logo."""
    p = Pic(12, 2, (0, 0, 0, 0), 32200)
    W, H = p.W, p.H
    p.rect(0, 6, W, H - 6, fill=NAVY, a=225)
    p.rect(0, H - 16, W, H - 10, fill=ORANGE, a=230)
    p.ellipse(10, 12, 108, 110, fill=(250, 250, 250), a=230)
    p.text(59, 60, "6", BLACK, 80, NAVY, anchor="mm")
    p.text(W / 2 + 50, 40, "CHANNEL 6  ·  NEWSCENTER 6", BLACK, civic.fit("CHANNEL 6  ·  NEWSCENTER 6", BLACK, 44, W - 180), WHITE, anchor="mm")
    p.text(W / 2 + 50, 86, "SERVING GRAYHAVEN COUNTY SINCE 1954", BOLD, civic.fit("SERVING GRAYHAVEN COUNTY SINCE 1954", BOLD, 26, W - 220), (240, 200, 120), anchor="mm")
    return p


def mural(i):
    t = mural_pic().tile(i, 32210 + i, smooth=0.1, grime=0.3)
    # the paint gone in flakes, the brick showing through
    flakes = t.rng.random((N, N)) < 0.06
    t.alpha[flakes] = 0
    t.streaks(0.8, 0.7)
    return t


@lru_cache(maxsize=None)
def set_pic():
    """NewsCenter 6's backdrop: the harbour skyline at dusk behind the glass, the title band across."""
    p = Pic(8, 4, (22, 30, 60), 32300)
    W, H = p.W, p.H
    rng = np.random.default_rng(32301)
    for k in range(40):
        y = k * H / 40
        c = mixc((24, 34, 80), (200, 110, 70), (k / 40) ** 1.6)
        p.rect(0, y, W, y + H / 40 + 1, fill=c)
    x = 0
    while x < W:
        w = rng.uniform(14, 40)
        h = rng.uniform(40, 170)
        p.rect(x, H - 40 - h, x + w, H - 40, fill=(18, 22, 36))
        for wy in np.arange(H - 40 - h + 6, H - 46, 7):
            for wx in np.arange(x + 3, x + w - 3, 5):
                if rng.random() < 0.35:
                    p.rect(wx, wy, wx + 2, wy + 3, fill=(250, 220, 140))
        x += w + rng.uniform(0, 6)
    p.rect(0, H - 40, W, H, fill=(30, 50, 80))
    p.rect(0, 20, W, 64, fill=NAVY, a=235)
    p.rect(0, 62, W, 66, fill=ORANGE)
    p.text(W / 2, 42, "NEWSCENTER 6", BLACK, 34, WHITE, anchor="mm")
    return p


def set_tile(i):
    t = set_pic().tile(i, 32310 + i, smooth=0.2, grime=0.1)
    t.emit[:] = 0.0
    return t


@lru_cache(maxsize=None)
def wx_pic():
    """The weather wall's last map: the county under the front, the highs and the lows, and over it in
    marker the quarantine zones somebody drew on live."""
    p = Pic(3, 2, (40, 90, 160), 32400)
    W, H = p.W, p.H
    p.poly([(20, 20), (110, 10), (150, 40), (170, 110), (120, 120), (60, 110), (30, 70)], fill=(80, 150, 80))
    p.poly([(140, 30), (190, 26), (184, 80), (160, 70)], fill=(90, 160, 90))
    p.text(60, 60, "68", BLACK, 14, WHITE, anchor="mm")
    p.text(130, 90, "71", BLACK, 14, WHITE, anchor="mm")
    p.text(160, 46, "L", BLACK, 18, (220, 40, 30), anchor="mm")
    p.text(30, 100, "H", BLACK, 18, (40, 80, 220), anchor="mm")
    p.rect(0, 0, W, 12, fill=NAVY)
    p.text(W / 2, 6, "WEATHER WATCH 6", BOLD, 7, WHITE, anchor="mm")
    p.ellipse(70, 40, 120, 80, outline=(230, 20, 20), width=3)
    p.line([(80, 50), (110, 72)], (230, 20, 20), 3)
    p.line([(110, 50), (80, 72)], (230, 20, 20), 3)
    p.scrawl(110, 100, "ZONE A", HAND, 9, (230, 20, 20), 0.3)
    return p


def wx(i):
    t = wx_pic().tile(i, 32410 + i, smooth=0.4, grime=0.1)
    return t


MWALL = ["bars", "ebs", "snow", "standby", "desk", "map", "ebs", "bars", "standby", "snow", "ebs", "desk",
         "map", "snow", "ebs", "bars", "standby", "ebs"]


def mwall(i, on):
    """One monitor in the wall: the bezel round the picture, its label strip under."""
    s = screen(32500 + i, MWALL[i], on)
    t = F.plain(32520 + i, (40, 40, 44), 0.03, 0.4)
    t.c[4:52, 4:60] = s.c[4:52, 4:60]
    t.emit[4:52, 4:60] = s.emit[4:52, 4:60]
    t.smooth[4:52, 4:60] = 0.9
    area(t, 10, 55, 54, 60, col=(220, 216, 200))
    F.edges(t, 3, 3, 61, 53, w=1, light=0.6, dark=1.2)
    return t


PROMOS = [("NEWSCENTER 6", "AT 6 AND 11", NAVY), ("SKYCAM 6", "FIRST ON THE SCENE", (30, 110, 170)),
          ("WEATHER WATCH 6", "WITH DON KESSLER", (40, 130, 70)), ("98.1 FM", "THE HARBOR", ORANGE)]


def promo(k):
    head, sub, col = PROMOS[k]
    p = Pic(1, 1, (236, 232, 220), 32600 + k)
    p.rect(2, 2, 62, 62, fill=col)
    p.text(32, 12, head, BLACK, civic.fit(head, BLACK, 9, 54), WHITE, anchor="mm")
    if k == 0:
        for cx, hair in ((22, (60, 40, 30)), (42, (180, 140, 70))):
            p.ellipse(cx - 7, 22, cx + 7, 38, fill=(220, 180, 150))
            p.ellipse(cx - 8, 19, cx + 8, 30, fill=hair)
            p.rect(cx - 9, 38, cx + 9, 52, fill=(40, 40, 60))
    elif k == 1:
        p.poly([(10, 34), (50, 30), (56, 36), (14, 40)], fill=(240, 240, 240))
        p.line([(6, 26), (58, 24)], (30, 30, 30), 1)
    elif k == 2:
        p.ellipse(18, 22, 46, 50, fill=(250, 210, 80))
        p.poly([(30, 34), (50, 30), (54, 44), (26, 46)], fill=(230, 230, 236))
    else:
        for r in (6, 12, 18):
            p.ellipse(32 - r, 36 - r, 32 + r, 36 + r, outline=WHITE, width=1)
    p.text(32, 57, sub, BOLD, civic.fit(sub, BOLD, 5, 56), WHITE, anchor="mm")
    t = p.tile(0, 32610 + k, smooth=0.3, grime=0.25)
    return t


@lru_cache(maxsize=None)
def banner_pic():
    """A bedsheet hung off the parapet in the last weeks, painted by hand."""
    p = Pic(6, 1, (232, 228, 214), 32700)
    W, H = p.W, p.H
    p.scrawl(14, 6, "WATCH 6 - 98.1 FM", SPRAY, civic.fit("WATCH 6 - 98.1 FM", SPRAY, 34, W - 40), (200, 24, 24), 0.25)
    p.scrawl(40, 40, "EMERGENCY INFO HERE", SPRAY, civic.fit("EMERGENCY INFO HERE", SPRAY, 18, W - 90), (20, 20, 20), 0.3)
    return p


def banner(i):
    t = banner_pic().tile(i, 32710 + i, smooth=0.1, grime=0.3)
    t.c *= (0.9 + 0.1 * np.cos(np.arange(N)[None, :] / N * 2 * np.pi * 2))[..., None]
    if i == 5:
        yy = np.arange(N)[:, None]
        t.alpha[(yy > 44 + 6 * np.sin(np.arange(N)[None, :] / 5)) & (np.arange(N)[None, :] > 30)] = 0
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 32800 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    if field == YELLOW:
        p.text(W / 2, H / 2, text, STENCIL, civic.fit(text, STENCIL, 15, W - 14), ink, anchor="mm")
    else:
        p.rect(1, H - 19, W - 1, H - 16, fill=ORANGE if field == NAVY else WHITE)
        p.text(W / 2, H / 2 - 1, text, BOLD, civic.fit(text, BOLD, 14, W - 16), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 32850 + i + len(key) * 11, smooth=0.5, grime=0.15)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 32900 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key != "trust" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 32910 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "trust" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


def textures():
    T = {}
    T["tv_red"] = lambda: steel(33000, AVRED)
    T["tv_white"] = lambda: steel(33001, (226, 224, 216))
    T["tv_galv"] = lambda: galv(33002)
    T["tv_grate"] = lambda: grate(33003)
    T["tv_coax"] = lambda: coax(33004)
    T["tv_ant"] = lambda: ant(33005)
    T["tv_beacon"] = lambda: beacon(33006, False)
    T["tv_beacon_lit"] = lambda: beacon(33007, True)
    T["tv_dish"] = lambda: dish(33008)
    T["tv_dish_back"] = lambda: dish(33009, back=True)
    T["tv_floor"] = lambda: floor(33010)
    T["tv_floor_tape"] = lambda: floor(33043, tape=True)
    T["tv_cyc"] = lambda: cyc(33011)
    T["tv_chroma"] = lambda: cyc(33012, (40, 70, 170))
    T["tv_acoustic"] = lambda: acoustic(33013)
    T["tv_tx_front"] = lambda: tx_front(33014, False)
    T["tv_tx_front_on"] = lambda: tx_front(33015, True)
    T["tv_tx_top"] = lambda: tx_front(33016, False, top=True)
    T["tv_tx_top_on"] = lambda: tx_front(33017, True, top=True)
    T["tv_rack"] = lambda: rack(33018, False)
    T["tv_rack_on"] = lambda: rack(33019, True)
    T["tv_vtr"] = lambda: vtr(33020, False)
    T["tv_vtr_on"] = lambda: vtr(33021, True)
    T["tv_switcher"] = lambda: switcher(33022, False)
    T["tv_switcher_on"] = lambda: switcher(33023, True)
    T["tv_audio"] = lambda: audio(33024)
    T["tv_screen"] = lambda: screen(33025, "bars", False)
    T["tv_screen_on"] = lambda: screen(33026, "bars", True)
    T["tv_screen_ebs"] = lambda: screen(33027, "ebs", True)
    T["tv_onair"] = lambda: onair(33028, False)
    T["tv_onair_on"] = lambda: onair(33029, True)
    T["tv_desk_front"] = lambda: desk_front(33030)
    T["tv_cam"] = lambda: cam(33031)
    T["tv_lens"] = lambda: lens(33032)
    T["tv_tapes"] = lambda: spines(33033, "tapes")
    T["tv_records"] = lambda: spines(33034, "records")
    T["tv_console"] = lambda: console_top(33035)
    T["tv_turntable"] = lambda: turntable(33036)
    T["tv_carts"] = lambda: carts(33037)
    T["tv_wire"] = lambda: wire(33038)
    T["tv_case"] = lambda: case(33039)
    T["tv_flat"] = lambda: flat(33040)
    T["tv_flat_back"] = lambda: flat(33041, back=True)
    T["tv_panel"] = lambda: panel(33042)
    for i in range(14):
        T[f"tv_name_{i}"] = lambda i=i: name(i)
    for i in range(4):
        T[f"tv_logo_{i}"] = lambda i=i: logo(i)
        T[f"tv_promo_{i}"] = lambda i=i: promo(i)
    for i in range(24):
        T[f"tv_mural_{i}"] = lambda i=i: mural(i)
    for i in range(32):
        T[f"tv_set_{i}"] = lambda i=i: set_tile(i)
    for i in range(6):
        T[f"tv_wx_{i}"] = lambda i=i: wx(i)
        T[f"tv_banner_{i}"] = lambda i=i: banner(i)
    for i in range(18):
        T[f"tv_mwall_{i}"] = lambda i=i: mwall(i, False)
        T[f"tv_mwall_on_{i}"] = lambda i=i: mwall(i, True)
    for key in SIGNS:
        for i in range(2):
            T[f"tv_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"tv_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
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

    black, white, chrome, grey = "home_plastic_black", "home_white", "home_chrome", "bunk_grey"

    def face(front, side, up=None):
        return {"north": front, "side": side, "up": up or side, "down": side}

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the tower's members, in its two paints
    d45 = {"axis": "z", "angle": 45, "origin": [8, 8, 8]}
    for c, tex in (("r", "tv_red"), ("w", "tv_white")):
        add(f"tv_leg_{c}", [[5, 0, 5, 11, 16, 11, tex], [4, 7, 4, 12, 9, 12, tex]], "Tower Leg", "METAL", rotate=None,
            shape=[[5, 0, 5, 11, 16, 11]])
        # a diagonal across the block in the plane z = 8: bottom-left to top-right seen from the north
        add(f"tv_diag_{c}", [[-3.3, 7.25, 7.25, 19.3, 8.75, 8.75, tex, None, d45]], "Tower Brace", "METAL",
            render="cutout", shape=[[0, 0, 7, 16, 16, 9]])
        add(f"tv_diagx_{c}", [[-3.3, 7.25, 7.25, 19.3, 8.75, 8.75, tex, None, d45],
                              [-3.3, 7.25, 7.0, 19.3, 8.75, 7.25, tex, None, {"axis": "z", "angle": -45, "origin": [8, 8, 8]}],
                              [7, 7, 6.5, 9, 9, 9.5, tex]], "Tower Brace", "METAL", render="cutout", shape=[[0, 0, 7, 16, 16, 9]])
        add(f"tv_girt_{c}", [[0, 7, 7, 16, 9, 9, tex]], "Tower Girt", "METAL", render="cutout", shape=[[0, 7, 7, 16, 9, 9]])
    add("tv_grate", [[0, 0, 0, 16, 1, 16, "tv_grate"], [0, 0, 0, 16, 1, 1, "tv_galv"], [0, 0, 15, 16, 1, 16, "tv_galv"]],
        "Grating", "METAL", rotate=None, render="cutout", shape=[[0, 0, 0, 16, 1, 16]])
    add("tv_rail", [[1, 0, 14, 2.5, 16, 15.5, "tv_galv"], [13.5, 0, 14, 15, 16, 15.5, "tv_galv"], [0, 14, 14, 16, 15.5, 15.5, "tv_galv"],
                    [0, 7.5, 14.2, 16, 8.7, 15.3, "tv_galv"], [0, 0, 14.6, 16, 2.5, 15, "tv_galv"]],
        "Railing", "METAL", render="cutout", shape=[[0, 0, 14, 16, 24, 15.5]])
    add("tv_spine", [[5, 0, 5, 11, 16, 11, "tv_galv"], [11, 0, 6, 13, 16, 8, "tv_coax"], [11, 0, 9, 13, 16, 11, "tv_coax"],
                     [3, 0, 6, 5, 16, 8, black], [6, 0, 3, 8, 16, 5, "tv_coax"]], "Tower Spine", "METAL", rotate=None)
    hoops = []
    for y in (2, 10):
        hoops += [[0, y, 0, 1, y + 1, 16, "tv_galv"], [15, y, 0, 16, y + 1, 16, "tv_galv"], [0, y, 0, 16, y + 1, 1, "tv_galv"]]
    add("tv_ladder", [[1, 0, 14, 2.5, 16, 16, "tv_galv"], [13.5, 0, 14, 15, 16, 16, "tv_galv"],
                      [2.5, 2, 14.5, 13.5, 3, 15.5, "tv_galv"], [2.5, 6, 14.5, 13.5, 7, 15.5, "tv_galv"],
                      [2.5, 10, 14.5, 13.5, 11, 15.5, "tv_galv"], [2.5, 14, 14.5, 13.5, 15, 15.5, "tv_galv"],
                      [0, 0, 0, 0.8, 16, 0.8, "tv_galv"], [15.2, 0, 0, 16, 16, 0.8, "tv_galv"]] + hoops,
        "Caged Ladder", "METAL", climb=True, render="cutout", shape=[[1, 0, 14, 15, 16, 16]])
    for suffix, tex, light in (("", "tv_beacon", None), ("_lit", "tv_beacon_lit", 12)):
        add(f"tv_beacon{suffix}", [[6, 0, 6, 10, 2, 10, "tv_galv"], [5.5, 2, 5.5, 10.5, 8, 10.5, tex], [6.5, 8, 6.5, 9.5, 9, 9.5, "tv_galv"]],
            "Obstruction Light", "GLASS", rotate=None, decor=True, **({"light": light} if light else {}))
        add(f"tv_beacon_top{suffix}", [[4, 0, 4, 12, 3, 12, "tv_galv"], [3.5, 3, 3.5, 12.5, 12, 12.5, tex], [6, 12, 6, 10, 14, 10, "tv_galv"]],
            "Beacon", "GLASS", rotate=None, decor=True, **({"light": light + 2} if light else {}))
    add("tv_ant", [[3, 0, 3, 13, 16, 13, "tv_ant"], [2.5, 0, 2.5, 13.5, 1, 13.5, "tv_galv"]], "Antenna Mast", "METAL", rotate=None)
    # an FM bay: the arm off the leg (back at z = 16) and the ring element on it
    add("tv_ant_bay", [[7, 7, 6, 9, 9, 16, "tv_galv"], [3, 3, 2, 13, 4, 4, "tv_coax"], [3, 12, 2, 13, 13, 4, "tv_coax"],
                       [3, 4, 2, 4, 12, 4, "tv_coax"], [12, 4, 2, 13, 12, 4, "tv_coax"], [7, 4, 4, 9, 7, 6, "tv_galv"]],
        "FM Antenna", "METAL", render="cutout", shape=[[3, 3, 2, 13, 13, 16]])
    add("tv_mw", [[2, 2, 2, 14, 14, 9, {"north": "tv_dish", "side": "tv_galv", "up": "tv_galv", "down": "tv_galv", "south": "tv_dish_back"}],
                  [3, 3, 0.5, 13, 13, 2, {"north": "tv_ant", "side": "tv_ant"}], [7, 0, 9, 9, 16, 16, "tv_galv"]],
        "Microwave Antenna", "METAL", shape=[[2, 0, 0.5, 14, 16, 16]])
    add("tv_coax", [[2, 2, 0, 5, 5, 16, "tv_coax"], [6.5, 2, 0, 9.5, 5, 16, "tv_coax"], [11, 2, 0, 14, 5, 16, "tv_coax"],
                    [1, 0, 7, 15, 2, 9, "tv_galv"]], "Transmission Line", "METAL", rotate="axis", shape=[[1, 0, 0, 15, 5, 16]])
    # the ice bridge's deck: grating overhead on its rails, the lines on top (axis: along z)
    add("tv_ice", [[0, 14, 0, 16, 15, 16, "tv_grate"], [0, 13, 0, 1.5, 15, 16, "tv_galv"], [14.5, 13, 0, 16, 15, 16, "tv_galv"],
                   [3, 15, 0, 6, 16, 16, "tv_coax"], [7, 15, 0, 10, 16, 16, "tv_coax"], [11, 15, 0, 13, 16, 16, black]],
        "Ice Bridge", "METAL", rotate="axis", render="cutout", shape=[[0, 13, 0, 16, 16, 16]])
    add("tv_post", [[6, 0, 6, 10, 16, 10, "tv_galv"]], "Steel Post", "METAL", rotate=None)
    add("tv_pier", [[0, 0, 0, 16, 12, 16, "floor_concrete"], [2, 12, 2, 14, 13, 14, "tv_galv"], [3, 13, 3, 4, 15, 4, "tv_galv"],
                    [12, 13, 3, 13, 15, 4, "tv_galv"], [3, 13, 12, 4, 15, 13, "tv_galv"], [12, 13, 12, 13, 15, 13, "tv_galv"]],
        "Tower Footing", "STONE", rotate=None, shape=[[0, 0, 0, 16, 13, 16]])

    # ---- the dishes: a reflector face tilted 45 degrees (its face up toward the facing), long
    # enough that a diagonal staircase of them reads as one surface
    tilt = {"axis": "x", "angle": 45, "origin": [8, 8, 8]}
    add("tv_dish", [[0, 7.4, -3.4, 16, 8.6, 19.4, {"up": "tv_dish", "down": "tv_dish_back", "side": "tv_galv"}, None, tilt]],
        "Satellite Dish", "METAL", render="cutout", shape=[[0, 0, 0, 16, 16, 16]])
    add("tv_feed", [[5, 5, 5, 11, 11, 11, {"all": grey, "north": black}], [6, 6, 3, 10, 10, 5, black]], "Dish Feed", "METAL",
        shape=[[5, 5, 3, 11, 11, 11]])
    add("tv_strut", [[7.2, -3.4, 7.2, 8.8, 19.4, 8.8, "tv_galv", None, tilt]], "Feed Strut", "METAL", render="cutout",
        shape=[[6, 0, 6, 10, 16, 10]])
    add("tv_dish_small", [[7, 0, 9, 9, 7, 11, "tv_galv"], [6, 0, 8, 10, 1, 12, "tv_galv"],
                          [1, 4, 2, 15, 15, 3.5, {"north": "tv_dish", "south": "tv_dish_back", "side": "tv_galv"}, None, {"axis": "x", "angle": 22.5, "origin": [8, 8, 8]}],
                          [7.5, 6, 3, 8.5, 7, 10, "tv_galv"], [7, 9, -2, 9, 11, 0, black]],
        "Satellite Dish", "METAL", render="cutout", shape=[[1, 0, 1, 15, 15, 12]])

    # ---- the transmitter and the racks
    for suffix, light in (("", None), ("_on", 6)):
        add(f"tv_tx{suffix}", [[0, 0, 1, 16, 16, 16, face(f"tv_tx_front{suffix}", "tv_panel")]], "Transmitter", "METAL",
            **({"light": light} if light else {}))
        add(f"tv_tx_top{suffix}", [[0, 0, 1, 16, 14, 16, face(f"tv_tx_top{suffix}", "tv_panel")], [0, 14, 0, 16, 16, 16, "tv_panel"]],
            "Transmitter", "METAL", **({"light": light} if light else {}))
        add(f"tv_rack{suffix}", [[1, 0, 1, 15, 16, 15, face(f"tv_rack{suffix}", "bunk_server_side", "bunk_server_lid")]], "Equipment Rack", "METAL",
            **({"light": 4} if light else {}))
        add(f"tv_vtr{suffix}", [[1, 0, 1, 15, 16, 15, face(f"tv_vtr{suffix}", "bunk_server_side", "bunk_server_lid")]], "Tape Decks", "METAL",
            loot="storage", **({"light": 4} if light else {}))
        add(f"tv_switcher{suffix}", [[0, 0, 3, 16, 9, 16, {"side": "tv_panel", "north": "tv_panel", "up": f"tv_switcher{suffix}", "down": "tv_panel"}],
                                     [0, 9, 9, 16, 10.5, 16, {"side": "tv_panel", "up": f"tv_switcher{suffix}", "down": "tv_panel"}]],
            "Production Switcher", "METAL", **({"light": 4} if light else {}))
        add(f"tv_monitor{suffix}", [[1, 0, 2, 15, 12, 15, face("tv_screen" + ("_on" if light else ""), "tv_panel")], [3, 12, 4, 13, 13, 13, "tv_panel"]],
            "Monitor", "METAL", **({"light": 5} if light else {}))
        add(f"tv_onair{suffix}", [[2, 4, 10, 14, 12, 16, face(f"tv_onair{suffix}", black)]], "On Air Light", "METAL", decor=True,
            **({"light": 7} if light else {}))
    add("tv_ebs", [[1, 0, 2, 15, 12, 15, face("tv_screen_ebs", "tv_panel")], [3, 12, 4, 13, 13, 13, "tv_panel"]], "Monitor", "METAL", light=5)
    add("tv_audio", [[0, 0, 3, 16, 9, 16, {"side": "tv_panel", "up": "tv_audio", "down": "tv_panel"}],
                     [0, 9, 10, 16, 12, 16, "tv_panel"]], "Audio Board", "METAL")
    # the anchor desk: its front to the cameras (north), the top a slab of walnut
    add("tv_desk", [[0, 0, 0, 16, 11, 3, face("tv_desk_front", "cv_oak")], [0, 11, 0, 16, 12.5, 12, {"all": "cv_oak", "up": "cv_oak"}],
                    [0, 0, 3, 16, 11, 4, "cv_oak"], [5, 12.5, 6, 11, 13, 9, "tv_wire"]], "Anchor Desk", "WOOD", loot="office",
        shape=[[0, 0, 0, 16, 12.5, 12]])
    add("tv_desk_end", [[0, 0, 0, 16, 11, 3, face("tv_desk_front", "cv_oak")], [0, 11, 0, 16, 12.5, 12, "cv_oak"],
                        [13, 0, 3, 16, 11, 12, "cv_oak"]], "Anchor Desk", "WOOD", shape=[[0, 0, 0, 16, 12.5, 12]])
    # the pedestal camera: the column and its base (lo), the body, lens and prompter hood (hi), aimed north
    add("tv_cam_lo", [[2, 0, 2, 14, 2, 14, grey], [3, 0, 3, 5, 1, 5, black], [11, 0, 3, 13, 1, 5, black], [3, 0, 11, 5, 1, 13, black],
                      [11, 0, 11, 13, 1, 13, black], [6, 2, 6, 10, 16, 10, "tv_galv"], [2, 10, 7.5, 14, 11, 8.5, chrome]],
        "Studio Camera", "METAL", shape=[[2, 0, 2, 14, 16, 14]])
    add("tv_cam_hi", [[5, 0, 5, 11, 2, 11, "tv_galv"], [4, 2, 3, 12, 9, 15, face("tv_cam", "tv_cam", "tv_cam")],
                      [5, 3, 0, 11, 8, 3, face("tv_lens", black)], [3, 9, 0, 13, 15, 6, face("tv_screen", black, black)],
                      [6, 3, 15, 10, 8, 16.5, black], [12, 5, 12, 16, 6, 13, chrome]],
        "Studio Camera", "METAL", shape=[[3, 0, 0, 13, 15, 15]])
    # the lighting grid: a pipe along z at the top of the block, a fresnel hanging off it under
    add("tv_pipe", [[7, 14, 0, 9, 16, 16, "tv_galv"]], "Lighting Grid", "METAL", rotate="axis", shape=[[7, 14, 0, 9, 16, 16]])
    add("tv_fresnel", [[7, 14, 0, 9, 16, 16, "tv_galv"], [7.5, 11, 7.5, 8.5, 14, 8.5, black], [5, 4, 4, 11, 11, 12, black],
                       [5.5, 4.5, 3, 10.5, 10.5, 4, {"all": grey, "north": "bunk_glass_off"}], [4, 6, 6, 5, 12, 10, grey],
                       [11, 6, 6, 12, 12, 10, grey]], "Studio Light", "METAL", render="cutout", shape=[[4, 4, 0, 12, 16, 16]])
    add("tv_fresnel_on", [[7, 14, 0, 9, 16, 16, "tv_galv"], [7.5, 11, 7.5, 8.5, 14, 8.5, black], [5, 4, 4, 11, 11, 12, black],
                          [5.5, 4.5, 3, 10.5, 10.5, 4, {"all": grey, "north": "bunk_glass_on"}], [4, 6, 6, 5, 12, 10, grey], [11, 6, 6, 12, 12, 10, grey]],
        "Studio Light", "METAL", render="cutout", light=12, shape=[[4, 4, 0, 12, 16, 16]])

    # ---- shelves and desks
    add("tv_tapes", [[0, 0, 4, 16, 16, 16, face("tv_tapes", "cv_oak")]], "Tape Shelves", "WOOD", loot="storage")
    add("tv_records", [[0, 0, 4, 16, 16, 16, face("tv_records", "cv_oak")]], "Record Shelves", "WOOD", loot="storage")
    add("tv_wire", [[2, 0, 4, 14, 8, 14, grey], [1, 8, 3, 15, 13, 15, {"all": "sc_beige", "up": "tv_wire"}], [3, 13, 6, 13, 16, 8, "tv_wire"],
                    [3, 0, 0, 13, 0.5, 4, "tv_wire"], [3, 0.5, 2.5, 13, 8, 3, "tv_wire"]], "Wire Machine", "METAL", loot="office",
        shape=[[1, 0, 3, 15, 16, 15]])
    add("tv_scanner", [[0, 0, 0, 16, 11, 16, {"all": "cv_oak"}], [2, 11, 3, 14, 15, 11, face("tv_panel", black)],
                       [3, 12, 2.6, 9, 14, 3, "hs_screen_on"], [12, 15, 6, 12.6, 22, 6.6, black]],
        "Police Scanner", "WOOD", loot="office", shape=[[0, 0, 0, 16, 15, 16]])
    add("tv_console", [[0, 0, 3, 16, 9, 16, {"side": "sc_beige", "up": "tv_console", "down": "sc_beige"}], [0, 9, 10, 16, 13, 16, "sc_beige"]],
        "Radio Console", "METAL")
    add("tv_turntables", [[0, 0, 2, 16, 10, 16, face("cv_oak", "cv_oak", "tv_turntable")]], "Turntables", "WOOD", loot="storage")
    add("tv_carts", [[1, 0, 2, 15, 16, 15, face("tv_carts", "tv_panel")]], "Cart Machines", "METAL")
    add("tv_mic", [[0, 0, 0, 16, 11, 16, "cv_oak"], [13, 11, 12, 15, 18, 14, chrome], [6, 17.5, 6, 14, 18.5, 7, chrome],
                   [4, 15, 4, 8, 19, 8, black]], "Microphone", "WOOD", shape=[[0, 0, 0, 16, 11, 16]])
    add("tv_case", [[1, 0, 1, 15, 12, 15, {"all": "tv_case", "up": "tv_case"}]], "Road Case", "WOOD", loot="hardware")
    add("tv_flat", [[0, 0, 13, 16, 16, 15, {"north": "tv_flat", "south": "tv_flat_back", "side": "cv_oak", "up": "cv_oak", "down": "cv_oak"}],
                    [0, 0, 15, 1, 16, 16, "cv_oak"], [15, 0, 15, 16, 16, 16, "cv_oak"]], "Scenery Flat", "WOOD")
    add("tv_cable", [[3, 0, 3, 13, 1.5, 13, black], [5, 1.5, 5, 11, 2.5, 11, black], [0, 0, 7, 3, 1, 9, black]], "Cable", "WOOL",
        decor=True, shape=[[3, 0, 3, 13, 2.5, 13]])
    for n in ("tv_floor", "tv_floor_tape"):
        add(n, None, "Studio Floor", "STONE", rotate=None, tex={"up": n, "side": "floor_concrete", "down": "floor_concrete"})
    for n, t in (("tv_cyc", "Cyc Wall"), ("tv_chroma", "Chroma Wall"), ("tv_acoustic", "Acoustic Panel")):
        add(n, None, t, "WOOL" if n == "tv_acoustic" else "STONE", rotate=None, tex={"all": n})

    # ---- plates
    for i in range(14):
        add(f"tv_name_{i}", plate(f"tv_name_{i}", 13.6), "Channel Letters", "METAL", decor=True)
    for i in range(4):
        add(f"tv_logo_{i}", plate(f"tv_logo_{i}", 14.6), "Station Logo", "METAL", decor=True)
        add(f"tv_promo_{i}", plate(f"tv_promo_{i}", 15.6), "Poster", "WOOL", decor=True)
    for i in range(24):
        add(f"tv_mural_{i}", plate(f"tv_mural_{i}", 15.3), "Painted Sign", "STONE", decor=True)
    for i in range(32):
        add(f"tv_set_{i}", plate(f"tv_set_{i}", 14.0), "News Set", "WOOD", decor=True)
    for i in range(6):
        add(f"tv_wx_{i}", plate(f"tv_wx_{i}", 14.8), "Weather Map", "METAL", decor=True)
        add(f"tv_banner_{i}", plate(f"tv_banner_{i}", 15.6), "Banner", "WOOL", decor=True)
    for i in range(18):
        add(f"tv_mwall_{i}", [[0, 0, 4, 16, 16, 16, face(f"tv_mwall_{i}", "tv_panel")]], "Monitor Wall", "METAL")
        add(f"tv_mwall_on_{i}", [[0, 0, 4, 16, 16, 16, face(f"tv_mwall_on_{i}", "tv_panel")]], "Monitor Wall", "METAL", light=6)
    for key in SIGNS:
        for i in range(2):
            add(f"tv_sgn_{key}_{i}", plate(f"tv_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"tv_spray_{key}_{i}", plate(f"tv_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("tv_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} broadcast blocks in {path}")


if __name__ == "__main__":
    main()
