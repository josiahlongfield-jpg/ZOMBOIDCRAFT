"""The railway's modern stations: the tx_ kit (world/county/TownStation.java, and Union Station's
train hall, world/county/UnionHall.java).

In the late eighties the GRAYHAVEN COUNTY RAILWAY rebuilt its town stations in steel and glass:
a tall glazed ticket hall under a flat roof on white steel trusses, a timber slat ceiling with a
strip of rooflight down the middle, terrazzo underfoot, a glass canopy cantilevered out over the
sidewalk on tapered arms to meet the platform's own, the town's name in steel letters along the
parapet. Inside, everything a station needs and nothing for show: ticket machines and the ticket
office's windows, validators by the doors, the departures board, the network map with YOU ARE
HERE, wayfinding signs in the railway's green and cream, a news-and-coffee kiosk, toilets, the
staff office. Josiah's word for it (2026-10-27): elegant, but functional and intricately made,
not classic. It is still the end of the world: panes cracked and out, the machines pried open,
the ceiling stained, the board showing every train cancelled.

Drawn from what such stations are known to be (no photos of them were in the library when this
was made; rail/modern-train-hall, rail/light-rail-station-pavilion and rail/station-fittings
were queued; redo what the photos show is off).

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16; axis models run along z as drawn):
- structure: tx_column, tx_column_base, tx_column_cap (round white steel), tx_truss (a Warren
  truss along its axis), tx_beam (an I-beam along its axis), tx_roof (membrane on top, the slat
  ceiling under), tx_roof_glass (the rooflight), tx_parapet, tx_fascia;
- walls and floors: tx_panel, tx_panel_b (the outside's aluminium panels, clean and streaked),
  tx_panel_{hi,mid,lo}[_s] (the same hung as tall panels three blocks high, the _s ones streaked),
  tx_wall, tx_wall_base (inside, the base with its granite skirting), tx_granite, tx_terrazzo,
  tx_terrazzo_b (cracked), tx_terrazzo_d (the dark border);
- glazing: tx_glass, tx_glass_t (with a transom along its foot), tx_glass_broken, tx_glass_frame
  (the pane gone), tx_door (glass, steel frame, push bar), tx_canopy (glass in its bars),
  tx_canopy_edge (the gutter at the tip, facing out), tx_canopy_arm_0..6 (the canopy with its
  tapered arm under it, 0 at the wall, facing out);
- fittings: tx_bench (perforated steel, two seats), tx_tvm + tx_tvm_top (a ticket machine, two
  high; tx_tvm_smashed + tx_tvm_top_smashed pried open), tx_validator, tx_ticket_counter + tx_ticket_window over it, tx_bin,
  tx_pendant (the hall's lamps), tx_board_0..7 (departures, four across and two down),
  tx_map_<stop>_0..5 (the network map, three by two, YOU ARE HERE at that stop),
  tx_sign_<key>_0..1 (hung wayfinding signs), tx_name_<stop>_0..7 (the parapet's lettering).

Union Station's train hall (the late-eighties hall over the el, after the stations of steel trees
holding up a glass roof): tx_tree_base, tx_tree, tx_tree_fork (a white steel tree's foot, trunk
and the hub its eight arms spring from), tx_branch (an arm rising at 45 degrees toward its facing,
a block on and a block up each block), tx_branch_d (the diagonal arms, toward the facing and its
right), tx_vault, tx_vault_cracked, tx_vault_frame (the roof's glass in its bars, cracked, gone), tx_vault_beam,
tx_vault_node (the glass over the roof's I-beams, one way and crossing),
tx_paver, tx_paver_b (the platforms' flags), tx_edge (the platform's edge facing the track: the
coping, the white line, the yellow studs), tx_esc_step (an escalator's steps, stopped since the
power went: stairs facing the way up), tx_esc_side_l/_r (its glass balustrade with the rubber
handrail, over a step), tx_esc_newel_l/_r (where the handrail turns down at a landing),
tx_esc_plate (the comb plate at a landing, facing the steps), tx_step (granite stairs beside the escalators), tx_glass_rail (a glass balustrade
along its axis), tx_gate (a fare gate's cabinet along its axis, the paddles folded away: they fail
open), tx_clock (the hung clock, a face each way along its axis), tx_rod (a hanger),
tx_platnum_1/_2 (the hung platform numbers), tx_ceiling, tx_ceiling_lamp (the concourse's linear
metal ceiling and its light strips), tx_ceiling_lamp_off and tx_pendant_off (dead ones, which neither
flicker nor light).

    python3 tools/city/transit.py        rewrites the tx_ entries in blocks.json

photo_textures.py draws the textures (transit.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
from furniture import area, disc, edges  # noqa: E402
from school import Pic  # noqa: E402
from gasstation import glow, weather  # noqa: E402
from diner import metal, peel, rivets  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
BLACK = "/usr/share/fonts/opentype/inter/Inter-Black.otf"
MONO = "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Bold.ttf"
NS = None  # photo_textures, handed over by register()

GREEN = (38, 62, 46)        # the railway's dark green
CREAM = (222, 210, 172)     # and its cream
WHITE = (204, 206, 200)     # the structure's white steel
ANTH = (48, 52, 54)         # frames, mullions, fascia: anthracite
AMBER = (255, 168, 40)      # the board's LEDs

# the stops with a town station, and what their parapets say
STATIONS = {"uptown": "UPTOWN", "riverside": "RIVERSIDE", "foundry": "FOUNDRY ROW", "southport": "SOUTHPORT",
            "union": "UNION STATION"}
NAME_W = 8
# the network map: every stop round the county loop in running order, where it lies (x, z), and the
# subway loop under the island; drawn from Track's stops (seed 12345, the plan is fixed)
LOOP = [("uptown", "Uptown", 2160, -3650), ("pinecrest", "Pinecrest", 550, -5050), ("fort", "Fort Caldwell", -2350, -5250),
        ("riverside", "Riverside", -2560, -2260), ("millbrook", "Millbrook", -4750, -1000), ("dunmore", "Dunmore", -5850, 1150),
        ("fairview", "Fairview", -5150, 3300), ("ashby", "Ashby", -2700, 2650), ("hollow", "Hollow Creek", -1650, 5020),
        ("kings", "Kingsbridge", 1100, 6250), ("seabright", "Seabright", 3500, 5550), ("airport", "Airport", 1500, 3700),
        ("foundry", "Foundry Row", 1520, 1650), ("southport", "Southport", 2350, 880), ("union", "Union Station", 3040, -1150)]
SUBWAY = [("Broadcast Tower", 3100, -2000), ("Power Station", 3680, -1800), ("Waterfront", 3560, -1040),
          ("Union Station", 3050, -1040), ("Mercy General", 2850, -400), ("Stadium", 2640, -1100)]
SIGNS = {"trains": ("TRAINS", "train", "↑"), "tickets": ("TICKETS", "ticket", ""), "toilets": ("TOILETS", "wc", ""),
         "exit": ("WAY OUT", "exit", "↑"), "kiosk": ("NEWS · COFFEE", "cup", ""),
         "subway": ("SUBWAY", "subway", ""), "buses": ("BUSES · TAXIS", "bus", ""),
         "platforms": ("PLATFORMS 1 · 2", "train", "↑")}
BOARD = [("07:42", "UNION STATION", "1", "CANCELLED"), ("07:58", "MILLBROOK  FAIRVIEW", "1", "CANCELLED"),
         ("08:15", "EVAC SPECIAL", "1", "DELAYED"), ("08:31", "SEABRIGHT  AIRPORT", "1", "CANCELLED")]


# ---------------------------------------------------------------- materials

def steel(seed, rgb, rust=0.3, chips=1.0):
    """Painted structural steel: a satin coat, rain streaks, rust at the seams and where it chipped."""
    t = F.plain(seed, rgb, 0.03, 0.55)
    t.f0[:] = 30
    weather(t, rust=rust, streak=0.5, grime=0.22)
    if chips:
        peel(t, chips, under=(120, 82, 56))
    return t


def membrane(seed):
    """The flat roof: dark grey single-ply in lapped sheets, ponding stains, moss in the low spots."""
    t = F.plain(seed, (62, 64, 64), 0.05, 0.25)
    for y in (0, 31):
        area(t, 0, y, N, y + 2, mul=0.82, dh=0.12)
    pond = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2 - 0.2, 0, 1)
    t.mix(pond * 0.5, (40, 42, 38), 0.8)
    moss = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6), (32, 0.3))) * 2.4 - 1.0, 0, 1)
    t.mix(moss, (66, 84, 44), 0.75)
    t.h += moss * 0.15
    t.speckle(0.02, (90, 90, 86), 0.4)
    return t


def slats(seed, stained=True):
    """The ceiling seen from below: oak slats 6 wide on a black acoustic felt, each slat its own
    shade, water marks where the roof let go."""
    t = F.plain(seed, (14, 14, 14), 0.02, 0.1)
    t.h[:] = 0.2
    for k in range(8):
        x0 = k * 8 + 1
        shade = float(t.rng.uniform(0.86, 1.1))
        col = tuple(v * shade for v in (156, 112, 70))
        area(t, x0, 0, x0 + 6, N, col=col, h=0.9, smooth=0.35)
        grain = P.fbm(t.rng, ((2, 1.0), (32, 0.6), (64, 0.3)))
        t.c[:, x0:x0 + 6] *= (1 + 0.1 * grain[:, x0:x0 + 6])[..., None]
        area(t, x0, 0, x0 + 1, N, mul=1.12)
        area(t, x0 + 5, 0, x0 + 6, N, mul=0.72)
    if stained:
        stain = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2 - 0.5, 0, 1)
        t.mix(stain * 0.55, (92, 70, 44), 0.8)
    t.grime(0.15)
    return t


def glass(seed, tint=(150, 172, 176), alpha=105, cracked=0, holes=0.0, frit=False):
    """Clear float glass, tinted a little green, dirt and rain runs heavier toward the foot; cracked
    in stars (cracked), knocked through in jagged holes (holes, the share of the pane gone)."""
    t = F.plain(seed, tint, 0.0, 0.9)
    t.alpha[:] = alpha
    t.f0[:] = 60
    yy, xx = np.mgrid[0:N, 0:N]
    # grime settles toward the foot of the pane, evenly: no blotches, or every pane in a wall shows the same ones
    dirt = np.clip((yy / N - 0.68) / 0.32, 0, 1) ** 1.5 * 0.7
    t.mix(dirt * 0.3, (100, 102, 92), 0.8)
    t.alpha += dirt * 35
    spots = t.rng.random((N, N)) < 0.012
    t.c[spots] = t.c[spots] * 0.8 + 20
    t.alpha[spots] += 40
    t.c += (np.clip(1 - np.abs(xx - yy - 10) / 8, 0, 1) * 18)[..., None]   # the sheen across it
    if frit:
        dots = ((xx % 4) < 2) & ((yy % 4) < 2)
        t.c[dots] = (190, 194, 190)
        t.alpha[dots] = 200
    for _ in range(cracked):
        cx, cy = t.rng.uniform(10, N - 10, 2)
        for _ in range(int(t.rng.integers(5, 9))):
            ang = t.rng.uniform(0, np.pi * 2)
            ln = t.rng.uniform(14, 40)
            for i in range(int(ln)):
                ang += t.rng.normal(0, 0.06)
                x, y = int(cx + np.cos(ang) * i), int(cy + np.sin(ang) * i)
                if 0 <= x < N and 0 <= y < N:
                    t.c[y, x] = (186, 194, 192)
                    t.alpha[y, x] = 170
        disc(t, cx, cy, 1.6, col=(190, 198, 196), alpha=190)
    if holes:
        m = np.zeros((N, N), bool)
        for _ in range(2):
            x, y = t.rng.uniform(12, N - 12, 2)
            m |= F.blob_mask(t.rng, x, y, holes * 30, rough=0.9, wrap=False) > 0.5
        t.alpha[m] = 0
        edge = P.blur(m.astype(np.float32), 1) > 0.05
        t.c[edge & ~m] = (168, 178, 176)
        t.alpha[edge & ~m] = 200
    t.alpha = np.clip(t.alpha, 0, 255)
    return t


def panel(seed, streaked=False):
    """The outside's cladding: aluminium composite panels a block square in a pale warm grey,
    open joints round them, a fixing at each corner, rain streaking down from the joints."""
    t = F.plain(seed, (128, 128, 122), 0.03, 0.55)
    t.f0[:] = 120
    area(t, 0, 0, N, 1, col=(64, 64, 60), h=0.4)
    area(t, 0, 0, 1, N, col=(64, 64, 60), h=0.4)
    area(t, 0, 1, N, 2, mul=1.08)
    for x, y in ((4, 4), (N - 4, 4), (4, N - 4), (N - 4, N - 4)):
        disc(t, x, y, 0.8, col=(100, 100, 96), dh=0.2)
    t.streaks(0.8 if streaked else 0.4, 0.7)
    t.grime(0.32 if streaked else 0.18)
    if streaked:
        moss = np.clip(np.mgrid[0:N, 0:N][0] / N * 1.6 - 0.9 + P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 0.6, 0, 1)
        t.mix(moss, (78, 92, 54), 0.6)
    return t


def tall_panel(seed, part, streaked=False):
    """The cladding as hung: tall panels a block wide and three high, so a joint up each side and
    one across only at the panel's top (part "hi") or foot ("lo"); rain streaks running down from
    the joints, moss creeping up the low ones."""
    t = F.plain(seed, (128, 128, 122), 0.03, 0.55)
    t.f0[:] = 120
    area(t, 0, 0, 1, N, col=(60, 60, 56), h=0.4)
    area(t, N - 1, 0, N, N, col=(60, 60, 56), h=0.4)
    area(t, 1, 0, 2, N, mul=1.06)
    if part == "hi":
        area(t, 0, 0, N, 1, col=(60, 60, 56), h=0.4)
        area(t, 0, 1, N, 2, mul=1.08)
        for x in (5, N - 5):
            disc(t, x, 5, 0.8, col=(100, 100, 96), dh=0.2)
    if part == "lo":
        area(t, 0, N - 1, N, N, col=(60, 60, 56), h=0.4)
        for x in (5, N - 5):
            disc(t, x, N - 5, 0.8, col=(100, 100, 96), dh=0.2)
    t.streaks(0.8 if streaked else 0.35, 0.72)
    t.grime(0.3 if streaked else 0.15)
    if streaked and part == "lo":
        moss = np.clip(np.mgrid[0:N, 0:N][0] / N * 1.6 - 0.8 + P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 0.6, 0, 1)
        t.mix(moss, (78, 92, 54), 0.6)
    return t


def wall(seed, base=False):
    """Inside: smooth plaster in the railway's warm off-white, scuffed low, damp coming through;
    on the base course a skirting of polished dark granite."""
    t = F.plain(seed, (204, 200, 186), 0.04, 0.2)
    t.grime(0.2, col=(80, 76, 66))
    damp = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2 - 0.6, 0, 1)
    t.mix(damp * 0.5, (150, 136, 104), 0.6)
    if base:
        g = granite(seed + 1)
        t.c[N - 10:] = g.c[N - 10:]
        t.h[N - 10:] = 0.95
        t.smooth[N - 10:] = 0.75
        t.f0[N - 10:] = 40
        area(t, 0, N - 11, N, N - 10, mul=0.6, dh=-0.1)
        F.wear(t, 0.0, scratches=6)
    return t


def granite(seed):
    """Polished dark granite: black with grey and white grains, a little mica glint."""
    t = F.plain(seed, (56, 56, 58), 0.04, 0.75)
    t.f0[:] = 45
    t.speckle(0.25, (110, 110, 112), 0.6)
    t.speckle(0.06, (180, 178, 172), 0.7)
    t.speckle(0.03, (20, 20, 20), 0.5)
    t.grime(0.12)
    return t


def terrazzo(seed, base=(176, 172, 160), cracked=False, dark=False):
    """Terrazzo: marble chips in grey cement ground flat and polished, a brass strip along two
    edges (they meet the next slab's into a grid a block square), worn dull where feet go."""
    if dark:
        base = (74, 76, 76)
    t = F.plain(seed, base, 0.03, 0.62)
    t.f0[:] = 35
    chips = [((214, 210, 200), 0.05), ((92, 90, 86), 0.03), ((160, 120, 100), 0.015), ((130, 146, 132), 0.012),
             ((196, 186, 160), 0.04)]
    for col, chance in chips:
        m = t.rng.random((N, N)) < chance
        m = m | (np.roll(m, 1, 0) & (t.rng.random((N, N)) < 0.35))
        c = np.array(col, np.float32) if not dark else np.array(col, np.float32) * 0.55
        t.c[m] = t.c[m] * 0.35 + c * 0.65
    area(t, 0, 0, N, 1, col=(170, 136, 68), h=0.95, smooth=0.6, f0=200)
    area(t, 0, 0, 1, N, col=(170, 136, 68), h=0.95, smooth=0.6, f0=200)
    t.grime(0.22)
    if cracked:
        t.crack(2, 0.25, col=(50, 48, 44))
    return t


def truss_web(seed):
    """The truss's web seen side on: a white steel V each block (diagonals down from the top chord
    at the middle to the bottom chord at each end), a gusset plate at every node, nothing between."""
    t = steel(seed, WHITE, rust=0.25, chips=0.4)
    t.alpha[:] = 0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    top, bot = 12.0, 54.0
    # the diagonals' centre lines: from (0, bot) to (32, top) and on to (64, bot)
    for x0, x1 in ((0, 32), (64, 32)):
        dx, dy = x1 - x0, top - bot
        ln = np.hypot(dx, dy)
        d = np.abs((xx - x0) * dy - (yy - bot) * dx) / ln
        m = (d < 2.6) & (yy >= top - 1) & (yy <= bot + 1)
        t.alpha[m] = 255
        t.h[m] = 0.9
        rim = (d >= 1.8) & (d < 2.6) & m
        t.c[rim] *= 0.8
    for cx, cy in ((32, top + 2), (0, bot - 2), (64, bot - 2)):
        g = (np.abs(xx - cx) < 7) & (np.abs(yy - cy) < 4)
        t.alpha[g] = 255
        t.h[g] = 0.95
        for bx in (-4, 0, 4):
            disc(t, cx + bx, cy, 0.8, mul=0.7, dh=0.2)
    return t


def perf(seed):
    """Perforated steel sheet: round holes on a staggered grid, anthracite powder coat worn to the
    metal on the edges."""
    t = F.plain(seed, (150, 154, 154), 0.03, 0.55)
    t.f0[:] = 180
    yy, xx = np.mgrid[0:N, 0:N]
    for row in range(0, N, 4):
        off = 2 if (row // 4) % 2 else 0
        hole = (np.hypot((xx - off) % 4 - 1.5, yy - row - 1.5) < 1.1)
        t.alpha[hole] = 0
    edgem = (yy < 2) | (yy >= N - 2)
    t.c[edgem] = (120, 120, 118)
    t.grime(0.15)
    return t


def brushed(seed, rgb=(176, 180, 180)):
    return metal(seed, rgb, f0=220, smooth=0.6)


# ---------------------------------------------------------------- pictures

def emblem(p, cx, cy, r, ring=CREAM, fill=GREEN):
    """The railway's mark: a ring round a track running away to the horizon, its rails closing in
    toward the top without meeting, the sleepers shorter and closer together as they go."""
    p.ellipse(cx - r, cy - r, cx + r, cy + r, fill=fill, outline=ring, width=max(0.6, r * 0.14))
    w = r * 0.5
    bot, top = cy + r * 0.66, cy - r * 0.5
    for s in (-1, 1):
        p.poly([(cx + s * w * 1.0, bot), (cx + s * w * 0.13, top), (cx + s * w * 0.05, top), (cx + s * w * 0.78, bot)], fill=ring)
    for k, f in enumerate((0.0, 0.42, 0.68, 0.85)):
        yk = bot - (bot - top) * f - r * 0.06
        hw = w * (1.15 - f * 1.0)
        p.line([(cx - hw, yk), (cx + hw, yk)], ring, width=max(0.45, r * 0.09 * (1 - f * 0.6)))
    p.line([(cx - r * 0.72, top), (cx + r * 0.72, top)], ring, width=max(0.4, r * 0.05))


@lru_cache(maxsize=None)
def name_pic(key):
    """The parapet's lettering, eight blocks long: the town's name in brushed steel capitals on the
    anthracite fascia, the railway's mark at each end, a shadow under every letter."""
    p = Pic(NAME_W, 1, ANTH, 7700 + sum(map(ord, key)))
    W, H = p.W, p.H
    p.rect(0, 0, W, 4, fill=(70, 74, 76))
    p.rect(0, H - 3, W, H, fill=(30, 32, 34))
    text = STATIONS[key]
    size = civic.fit(text, BOLD, 42, W - 150)
    p.text(W / 2 + 1.2, H / 2 + 2.6, text, BOLD, size, (18, 18, 18), anchor="mm")
    p.text(W / 2, H / 2 + 1.4, text, BOLD, size, (200, 204, 204), anchor="mm")
    for cx in (34, W - 34):
        emblem(p, cx, H / 2, 17)
    return p


def name_tile(key, i):
    t = name_pic(key).tile(i, 7710 + sum(map(ord, key)) * 7 + i, smooth=0.5, grime=0.16)
    lum = t.c @ np.array((0.3, 0.59, 0.11), np.float32)
    lettering = lum > 150
    t.f0[lettering] = 220
    t.smooth[lettering] = 0.6
    t.h[lettering] = 0.95
    weather(t, rust=0.15, streak=0.6, grime=0.08)
    return t


def dots(p, x0, y0, text, size, col, pitch=2.0, font=MONO, dark=(40, 26, 10)):
    """Dot-matrix lettering: the text rasterised at one pixel a dot, each dot a round LED, the
    unlit ones showing faintly."""
    f = ImageFont.truetype(font, max(4, int(size)))
    w = int(f.getlength(text)) + 2
    h = int(size * 1.25)
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).text((0, 0), text, font=f, fill=255)
    a = np.asarray(im, np.float32) / 255
    for yy in range(h):
        for xx in range(w):
            cx, cy = x0 + xx * pitch, y0 + yy * pitch
            on = a[yy, xx] > 0.45
            r = pitch * 0.38
            p.ellipse(cx - r, cy - r, cx + r, cy + r, fill=col if on else dark)
    return w * pitch


@lru_cache(maxsize=None)
def board_pic():
    """The departures board, four blocks by two: DEPARTURES in cream on the railway green, then a
    line of amber dots for each train (time, where to, platform, what became of it), and the last
    line running the notice that was left on."""
    p = Pic(4, 2, (14, 14, 14), 7800)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, fill=(30, 32, 34))
    p.rect(3, 3, W - 3, H - 3, fill=(10, 10, 10))
    p.rect(3, 3, W - 3, 19, fill=GREEN)
    p.text(10, 6.5, "DEPARTURES", BOLD, 9, CREAM)
    emblem(p, W - 14, 11, 6)
    for x, s in ((10, "TIME"), (46, "TO"), (176, "PLAT"), (200, "STATUS")):
        p.text(x, 22, s, BOLD, 4.6, (150, 150, 140))
    for r, (time, dest, plat, status) in enumerate(BOARD):
        y = 31 + r * 18
        dots(p, 10, y, time, 6, AMBER, 1.25)
        dots(p, 46, y, dest, 6, AMBER, 1.25)
        dots(p, 180, y, plat, 6, AMBER, 1.25)
        dots(p, 200, y, status, 6, (255, 70, 40) if status != "DELAYED" else AMBER, 1.25)
    p.rect(3, H - 22, W - 3, H - 21, fill=(40, 40, 40))
    dots(p, 8, H - 18, "ALL SERVICES SUSPENDED UNTIL FURTHER NOT", 6, AMBER, 1.25)
    return p


def board(i):
    t = board_pic().tile(i, 7810 + i, smooth=0.55, grime=0.14)
    lit = (t.c[..., 0] > 150) & (t.c[..., 2] < 120)
    glow(t, lit.astype(np.float32), (255, 176, 70), amount=0.4, emit=0.8)
    t.f0[:] = 45
    return t


def _proj(x, z, box):
    x0, z0, x1, z1 = box
    return 16 + (x + 6400) / 12800 * (x1 - x0) + x0 - 16, 14 + (z + 5600) / 12600 * (z1 - z0) + z0 - 14


@lru_cache(maxsize=None)
def map_pic(here):
    """The network map, three blocks by two, framed behind glass: the county loop in the railway's
    green through its fifteen stops (white for stations, open rings for halts) where they lie on
    the map, the subway's red loop under the island in the inset, the river and the sound in
    pale blue, YOU ARE HERE in red at this stop."""
    p = Pic(3, 2, (234, 230, 216), 7900 + sum(map(ord, here)))
    W, H = p.W, p.H
    p.rect(0, 0, W, H, fill=(60, 62, 62))
    p.rect(3, 3, W - 3, H - 3, fill=(236, 232, 218))
    p.rect(3, 3, W - 3, 15, fill=GREEN)
    emblem(p, 10, 9, 4.5)
    p.text(17, 5, "GRAYHAVEN COUNTY RAILWAY  &  SUBWAY", BOLD, 5.2, CREAM)
    box = (8, 20, 128, 120)
    # the water: the river in from the north-west and the sound to the east, roughly
    p.poly([(102, 18), (126, 18), (126, 122), (112, 122), (106, 96), (110, 70), (100, 44)], fill=(196, 214, 222))
    p.line([(8, 52), (40, 58), (70, 64), (100, 70)], (196, 214, 222), width=3)
    pts = [_proj(x, z, box) for _, _, x, z in LOOP]
    p.line(pts + [pts[0]], GREEN, width=2.6)
    big = {"pinecrest", "millbrook", "fairview", "kings", "seabright", "union", "uptown", "riverside", "foundry", "southport"}
    for (key, label, _, _), (x, y) in zip(LOOP, pts):
        if key in big:
            p.ellipse(x - 2.1, y - 2.1, x + 2.1, y + 2.1, fill=(250, 250, 246), outline=(20, 20, 20), width=0.8)
        else:
            p.ellipse(x - 1.6, y - 1.6, x + 1.6, y + 1.6, fill=GREEN, outline=(250, 250, 246), width=0.6)
        anchor = "ra" if x > 90 else "la"
        p.text(x + (-3.5 if anchor == "ra" else 3.5), y - 2.6, label, BOLD, 3.6, (30, 30, 30), anchor=anchor)
        if key == here:
            p.ellipse(x - 5, y - 5, x + 5, y + 5, outline=(200, 30, 24), width=1.2)
            p.rect(x - 22, y + 6, x + 22, y + 12.5, fill=(200, 30, 24))
            p.text(x, y + 6.8, "YOU ARE HERE", BOLD, 4.2, (250, 246, 236), anchor="ma")
    # the subway inset: the island's loop at four times the scale
    ix0, iy0, ix1, iy1 = 136, 22, 186, 112
    p.rect(ix0 - 2, iy0 - 2, ix1 + 2, iy1 + 2, outline=(90, 90, 86), width=0.6)
    p.text((ix0 + ix1) / 2, iy0 - 1, "SUBWAY", BOLD, 4, (180, 30, 24), anchor="ma")
    sp = [(ix0 + 4 + (x - 2600) / 1120 * (ix1 - ix0 - 8), iy0 + 8 + (z + 2050) / 1700 * (iy1 - iy0 - 16)) for _, x, z in SUBWAY]
    p.line(sp + [sp[0]], (190, 34, 28), width=2.2)
    for (label, _, _), (x, y) in zip(SUBWAY, sp):
        p.ellipse(x - 1.8, y - 1.8, x + 1.8, y + 1.8, fill=(250, 250, 246), outline=(20, 20, 20), width=0.6)
        p.text(x, y + 2.4, label, BOLD, 3.0, (30, 30, 30), anchor="ma")
    p.text(10, H - 10, "Trains every 20 minutes. Call the train with the button on the platform.", BOLD, 3.4, (60, 60, 60))
    return p


def map_tile(here, i):
    t = map_pic(here).tile(i, 7910 + sum(map(ord, here)) * 5 + i, smooth=0.75, grime=0.18)
    t.f0[:] = 55   # behind glass
    t.streaks(0.3, 0.85)
    return t


def picto(p, kind, x, y, s, col):
    """A pictogram s high at (x, y) top left: a train front, a ticket, the WC figures, a cup, an
    exit arrow out of a door."""
    if kind == "train":
        p.rect(x + s * 0.15, y, x + s * 0.85, y + s * 0.8, fill=col)
        p.rect(x + s * 0.25, y + s * 0.12, x + s * 0.75, y + s * 0.4, fill=GREEN)
        p.ellipse(x + s * 0.24, y + s * 0.55, x + s * 0.36, y + s * 0.67, fill=GREEN)
        p.ellipse(x + s * 0.64, y + s * 0.55, x + s * 0.76, y + s * 0.67, fill=GREEN)
        p.line([(x + s * 0.3, y + s * 0.8), (x + s * 0.12, y + s)], col, width=s * 0.08)
        p.line([(x + s * 0.7, y + s * 0.8), (x + s * 0.88, y + s)], col, width=s * 0.08)
    elif kind == "ticket":
        p.rect(x, y + s * 0.2, x + s, y + s * 0.8, fill=col)
        p.ellipse(x + s * 0.62, y + s * 0.36, x + s * 0.9, y + s * 0.64, fill=GREEN)
        p.line([(x + s * 0.1, y + s * 0.38), (x + s * 0.5, y + s * 0.38)], GREEN, width=s * 0.06)
        p.line([(x + s * 0.1, y + s * 0.55), (x + s * 0.4, y + s * 0.55)], GREEN, width=s * 0.06)
    elif kind == "wc":
        for k, dx in enumerate((0.0, 0.55)):
            cx = x + s * (dx + 0.22)
            p.ellipse(cx - s * 0.1, y, cx + s * 0.1, y + s * 0.2, fill=col)
            if k == 0:
                p.rect(cx - s * 0.15, y + s * 0.25, cx + s * 0.15, y + s * 0.65, fill=col)
            else:
                p.poly([(cx, y + s * 0.25), (cx + s * 0.2, y + s * 0.7), (cx - s * 0.2, y + s * 0.7)], fill=col)
            p.rect(cx - s * 0.1, y + s * 0.6, cx + s * 0.1, y + s, fill=col)
        p.line([(x + s * 0.5, y), (x + s * 0.5, y + s)], col, width=s * 0.05)
    elif kind == "cup":
        p.poly([(x + s * 0.1, y + s * 0.3), (x + s * 0.75, y + s * 0.3), (x + s * 0.65, y + s), (x + s * 0.2, y + s)], fill=col)
        p.ellipse(x + s * 0.6, y + s * 0.42, x + s * 0.95, y + s * 0.75, outline=col, width=s * 0.08)
        for k in (0.3, 0.5):
            p.line([(x + s * k, y + s * 0.2), (x + s * (k + 0.05), y)], col, width=s * 0.05)
    elif kind == "subway":
        # a train's front in the mouth of a tunnel
        p.ellipse(x, y, x + s, y + s * 0.9, fill=col)
        p.rect(x, y + s * 0.45, x + s, y + s, fill=col)
        p.rect(x + s * 0.24, y + s * 0.34, x + s * 0.76, y + s * 0.95, fill=GREEN)
        p.rect(x + s * 0.32, y + s * 0.44, x + s * 0.68, y + s * 0.62, fill=col)
        p.ellipse(x + s * 0.32, y + s * 0.72, x + s * 0.42, y + s * 0.82, fill=col)
        p.ellipse(x + s * 0.58, y + s * 0.72, x + s * 0.68, y + s * 0.82, fill=col)
    elif kind == "bus":
        p.rect(x + s * 0.1, y + s * 0.05, x + s * 0.9, y + s * 0.85, fill=col)
        p.rect(x + s * 0.18, y + s * 0.15, x + s * 0.82, y + s * 0.48, fill=GREEN)
        p.rect(x + s * 0.18, y + s * 0.6, x + s * 0.3, y + s * 0.7, fill=GREEN)
        p.rect(x + s * 0.7, y + s * 0.6, x + s * 0.82, y + s * 0.7, fill=GREEN)
        p.rect(x + s * 0.16, y + s * 0.85, x + s * 0.3, y + s, fill=col)
        p.rect(x + s * 0.7, y + s * 0.85, x + s * 0.84, y + s, fill=col)
    elif kind == "exit":
        p.rect(x + s * 0.45, y, x + s, y + s, outline=col, width=s * 0.08)
        p.line([(x, y + s * 0.5), (x + s * 0.7, y + s * 0.5)], col, width=s * 0.12)
        p.poly([(x, y + s * 0.5), (x + s * 0.25, y + s * 0.25), (x + s * 0.25, y + s * 0.75)], fill=col)


@lru_cache(maxsize=None)
def sign_pic(key):
    """A hung wayfinding sign two blocks long: the pictogram in a cream square, the word in cream
    capitals on the railway green, an arrow where there is one, a thin steel edge."""
    p = Pic(2, 1, (0, 0, 0, 0), 7950 + sum(map(ord, key)))
    W = p.W
    text, kind, arrow = SIGNS[key]
    p.rect(0, 18, W, 46, fill=(150, 152, 150))
    p.rect(1, 19, W - 1, 45, fill=GREEN)
    p.rect(4, 22, 24, 42, fill=CREAM)
    picto(p, kind, 6.5, 24.5, 15, GREEN)
    size = civic.fit(text, BOLD, 13, W - 48)
    p.text(30, 32 - size * 0.62, text, BOLD, size, CREAM)
    if arrow:
        p.text(W - 8, 32 - 8, arrow, BOLD, 14, CREAM, anchor="ra")
    return p


def sign(key, i):
    t = sign_pic(key).tile(i, 7960 + sum(map(ord, key)) * 3 + i, smooth=0.55, grime=0.18)
    t.alpha[:18] = 0
    t.alpha[46:] = 0
    t.f0[:] = 40
    weather(t, rust=0.1, streak=0.4, grime=0.08)
    return t


# ---------------------------------------------------------------- the machines

def tvm(seed, top, smashed=False):
    """A ticket machine, two blocks high, anthracite with the railway's green hood: up top the
    screen (dark now, or kicked in), the destination buttons and the instructions; below the coin
    slot, the card slot, the notes slot, the tray the tickets drop into, the coin return. Pried
    open in the smashed one, the cash box gone."""
    t = steel(seed, ANTH, rust=0.15, chips=0.3)
    if top:
        area(t, 0, 0, N, 10, col=GREEN, h=0.9)
        m = F.text_mask("TICKETS", BOLD, 7, 44, 9, seed=3)
        F.paste(t, m, 10, 1, CREAM, h=0.95)
        area(t, 8, 16, 44, 44, col=(16, 18, 20), h=0.6, smooth=0.85, f0=60)
        edges(t, 7, 15, 45, 45, w=1, light=0.7, dark=1.3)
        if smashed:
            for _ in range(3):
                cx, cy = t.rng.uniform(14, 38), t.rng.uniform(20, 40)
                for _ in range(7):
                    ang = t.rng.uniform(0, np.pi * 2)
                    for k in range(int(t.rng.uniform(5, 14))):
                        x, y = int(cx + np.cos(ang) * k), int(cy + np.sin(ang) * k)
                        if 8 <= x < 44 and 16 <= y < 44:
                            t.c[y, x] = (150, 160, 162)
        else:
            m = F.text_mask("OUT OF|ORDER", BOLD, 6, 30, 14, seed=4)
            area(t, 11, 22, 41, 38, col=(222, 218, 200), h=0.7)
            F.paste(t, m, 11, 23, (180, 40, 30))
        for k in range(6):
            y = 16 + k * 5
            area(t, 48, y, 58, y + 3, col=(196, 198, 196), h=0.95, f0=200)
        F.paste(t, F.text_mask("1 ZONE|DAY|RETURN", BOLD, 3, 14, 12, seed=5), 46, 50, (210, 210, 200))
    else:
        if smashed:
            area(t, 6, 4, 58, 40, col=(26, 26, 26), h=0.2)
            area(t, 8, 30, 56, 40, col=(60, 50, 40), h=0.25)
            edges(t, 6, 4, 58, 40, w=2, light=0.6, dark=1.2)
            area(t, 4, 6, 7, 38, col=(150, 150, 148), h=1.0, f0=200)   # the door bent back
        else:
            area(t, 10, 6, 20, 12, col=(196, 198, 196), h=0.95, f0=200)
            area(t, 14, 8, 16, 10, col=(10, 10, 10))
            area(t, 26, 6, 40, 12, col=(196, 198, 196), h=0.95, f0=200)
            area(t, 28, 8, 38, 9, col=(10, 10, 10))
            area(t, 44, 6, 56, 14, col=(196, 198, 196), h=0.95, f0=200)
            area(t, 45, 9, 55, 10, col=(10, 10, 10))
            area(t, 12, 20, 52, 34, col=(20, 20, 20), h=0.3)
            edges(t, 12, 20, 52, 34, w=1, light=0.7, dark=1.3)
            F.paste(t, F.text_mask("TAKE TICKET HERE", BOLD, 3, 36, 5, seed=6), 14, 36, (200, 200, 190))
            area(t, 24, 44, 40, 52, col=(18, 18, 18), h=0.3)
            F.paste(t, F.text_mask("COIN RETURN", BOLD, 3, 24, 5, seed=7), 20, 54, (200, 200, 190))
        F.fingers(t, 32, 26, 12, 0.25)
    t.grime(0.12)
    return t


def validator(seed):
    """The validator's face: a slot under a little green screen, VALIDATE HERE and an arrow."""
    t = steel(seed, (52, 58, 60), rust=0.1, chips=0.2)
    area(t, 8, 6, 56, 22, col=(20, 34, 26), h=0.6, smooth=0.85)
    yy, xx = np.mgrid[0:N, 0:N]
    lit = ((yy > 10) & (yy < 18) & (xx > 14) & (xx < 50)).astype(np.float32) * 0.6
    glow(t, lit, (80, 220, 120), amount=0.5, emit=0.4)
    area(t, 12, 34, 52, 38, col=(8, 8, 8))
    edges(t, 11, 33, 53, 39, w=1, light=1.3, dark=0.6)
    F.paste(t, F.text_mask("VALIDATE|HERE", BOLD, 4, 36, 12, seed=8), 14, 42, CREAM)
    return t


def ticket_glass(seed):
    """The ticket window's glass: a speaking grille cut in it, a CLOSED card hung behind it."""
    t = glass(seed, alpha=95)
    yy, xx = np.mgrid[0:N, 0:N]
    holes = (np.hypot(xx - 32, yy - 24) < 7) & ((xx + yy) % 3 == 0)
    t.c[holes] = (40, 40, 40)
    t.alpha[holes] = 200
    disc(t, 32, 24, 7.5, col=(150, 152, 150), ring=1.2, alpha=255)
    area(t, 20, 34, 44, 46, col=(232, 226, 204), alpha=255, h=0.8)
    F.paste(t, F.text_mask("CLOSED", BOLD, 6, 22, 8, seed=9), 21, 36, (170, 30, 24))
    return t


def counter_front(seed):
    """Below the ticket window: green enamel panels in a steel frame, kicked and scraped."""
    t = steel(seed, GREEN, rust=0.2, chips=0.8)
    for y in (0, 31):
        area(t, 0, y, N, y + 2, col=(130, 132, 130), h=0.95, f0=200)
    area(t, 0, N - 6, N, N, col=(150, 150, 148), h=0.9, f0=200)
    F.wear(t, 0.1, scratches=10)
    return t


def door_tex(seed, top):
    """The hall's glass doors: a slim anthracite frame round clear glass, a brushed push bar at hand
    height, a kick plate, the railway's mark and PUSH on the glass at eye level."""
    t = steel(seed, ANTH, rust=0.1, chips=0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    g = (xx >= 6) & (xx < N - 6) & (yy >= (6 if top else 0)) & (yy < (N if top else N - 14))
    t.alpha[g] = 0
    if top:
        e = Pic(1, 1, (0, 0, 0, 0), seed)
        emblem(e, 32, 34, 8)
        e.text(32, 46, "PUSH", BOLD, 5, CREAM, anchor="ma")
        a = e.shrunk()
        m = a[..., 3] > 100
        t.c[m] = a[..., :3][m]
        t.alpha[m] = 255
    else:
        area(t, 6, 2, N - 6, 7, col=(190, 194, 194), alpha=255, h=0.95, f0=220)
        edges(t, 6, 2, N - 6, 7)
        area(t, 6, N - 14, N - 6, N, col=(170, 172, 172), alpha=255, h=0.85, f0=220)
        F.fingers(t, 32, 4, 10, 0.3)
    return t


def lamp(seed, on):
    """The pendant's diffuser seen from below: opal glass, grey with dust and dead insects, bright
    when it's lit."""
    t = F.plain(seed, (226, 224, 214) if on else (150, 150, 144), 0.03, 0.6)
    if on:
        t.emit[:] = 0.9
    t.grime(0.18)
    return t


def bin_side(seed):
    t = brushed(seed)
    area(t, 0, 10, N, 18, col=GREEN, h=0.9)
    F.paste(t, F.text_mask("LITTER", BOLD, 5, 30, 7, seed=11), 17, 11, CREAM)
    t.grime(0.25)
    return t


def bin_top(seed):
    t = brushed(seed)
    disc(t, 32, 32, 16, col=(14, 14, 14), h=0.2)
    t.grime(0.3)
    return t


def gutter(seed):
    t = steel(seed, ANTH, rust=0.35, chips=0.4)
    area(t, 0, 0, N, 3, mul=1.2, dh=0.1)
    moss = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 2 - 0.4, 0, 1)
    t.mix(moss * (np.mgrid[0:N, 0:N][0] < 12), (70, 86, 46), 0.8)
    return t


def coping(seed):
    """The parapet's capping: folded aluminium, a drip edge, bird mess and moss."""
    t = F.plain(seed, (150, 152, 150), 0.04, 0.5)
    t.f0[:] = 140
    area(t, 0, 0, N, 2, mul=1.15, dh=0.1)
    t.speckle(0.03, (220, 220, 210), 0.6)
    moss = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6))) * 2.2 - 0.8, 0, 1)
    t.mix(moss, (72, 90, 48), 0.7)
    t.grime(0.25)
    return t


# ---------------------------------------------------------------- Union Station's train hall

def paver(seed, cracked=False):
    """The platforms' paving: big pale concrete flags, two to a block each way, each its own shade,
    open joints with grit in them, gum spots and scuffs where the crowds stood; cracked and green in
    the joints under the roof's holes."""
    t = F.plain(seed, (160, 158, 150), 0.05, 0.3)
    for i in range(2):
        for j in range(2):
            area(t, i * 32, j * 32, i * 32 + 32, j * 32 + 32, mul=float(t.rng.uniform(0.93, 1.06)))
    joint = np.zeros((N, N), bool)
    for v in (0, 32):
        joint[v, :] = True
        joint[:, v] = True
    t.c[joint] = (92, 90, 84)
    t.h[joint] = 0.45
    t.speckle(0.01, (58, 58, 56), 0.6)
    t.speckle(0.02, (120, 118, 110), 0.4)
    t.grime(0.22)
    if cracked:
        t.crack(3, 0.3, col=(66, 64, 58))
        moss = np.clip(P.blur(joint.astype(np.float32), 1) * 2, 0, 1) * np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 2 + 0.2, 0, 1)
        t.mix(moss, (70, 92, 44), 0.85)
        t.h += moss * 0.1
    return t


def edge_top(seed):
    """The platform's edge from above, the track at the top: the concrete coping with its worn nose,
    the white line, then a band of yellow studs, then the flags."""
    t = paver(seed)
    area(t, 0, 0, N, 12, col=(176, 174, 166), h=0.85)
    area(t, 0, 0, N, 2, mul=0.8, dh=-0.05)
    t.speckle(0.05, (120, 118, 110), 0.5)
    area(t, 0, 13, N, 16, col=(226, 224, 214), h=0.9)
    area(t, 0, 18, N, 34, col=(214, 170, 30), h=0.8)
    for row in range(20, 34, 4):
        for col in range(2 + (row // 4) % 2 * 2, N, 4):
            disc(t, col, row, 1.1, mul=1.12, dh=0.15, soft=0.6)
    t.grime(0.2)
    return t


def edge_face(seed):
    """The platform's face over the track: concrete, black with brake dust and rubbed by the cars."""
    t = F.plain(seed, (120, 118, 112), 0.06, 0.25)
    area(t, 0, 0, N, 10, col=(170, 168, 160), h=0.85)
    t.grime(0.45, col=(30, 26, 22))
    t.streaks(0.6, 0.7)
    return t


def esc_tread(seed):
    """An escalator's steps from above: aluminium cleats running the way up, a yellow strip along
    each step's nose (the stair block's two treads: the nose of the upper at half way, the lower's
    at the foot)."""
    t = metal(seed, (168, 172, 172), f0=220, smooth=0.5)
    yy, xx = np.mgrid[0:N, 0:N]
    groove = (xx % 3) == 0
    t.c[groove] *= 0.45
    t.h[groove] = 0.4
    for y0 in (26, 58):
        area(t, 0, y0, N, y0 + 6, col=(220, 176, 34), h=0.85, smooth=0.4, f0=40)
        t.c[y0:y0 + 6][groove[y0:y0 + 6]] *= 0.7
    t.grime(0.3)
    return t


def esc_riser(seed):
    t = metal(seed, (150, 154, 154), f0=220, smooth=0.5)
    xx = np.mgrid[0:N, 0:N][1]
    t.c[(xx % 3) == 0] *= 0.5
    t.grime(0.35)
    return t


def esc_glass(seed):
    t = glass(seed, tint=(160, 176, 174), alpha=80)
    return t


def rubber(seed):
    t = F.plain(seed, (24, 24, 24), 0.05, 0.45)
    t.f0[:] = 30
    t.grime(0.1, col=(60, 56, 50))
    return t


def esc_comb(seed):
    """The comb plate at a landing: ribbed aluminium, the yellow comb along the edge the steps run
    under (the top of the picture), its teeth in the cleats."""
    t = metal(seed, (160, 164, 164), f0=220, smooth=0.5)
    yy, xx = np.mgrid[0:N, 0:N]
    rib = ((xx + yy // 8 * 2) % 8) < 2
    t.c[rib] *= 1.15
    t.h[rib] = 0.95
    area(t, 0, 0, N, 10, col=(214, 170, 30), h=0.8)
    teeth = (yy < 4) & ((xx % 3) != 0)
    t.c[teeth] *= 0.6
    t.grime(0.3)
    return t


def gate_side(seed):
    """A fare gate's cabinet: brushed stainless, the railway's green band, an arrow (green, dead)
    and a red cross lit by nothing now."""
    t = metal(seed, (170, 174, 174), f0=220, smooth=0.55)
    area(t, 0, 8, N, 16, col=GREEN, h=0.9)
    F.paste(t, F.text_mask("GRAYHAVEN RAILWAY", BOLD, 3, 60, 5, seed=12), 2, 9, CREAM)
    area(t, 0, N - 6, N, N, col=(60, 62, 62), h=0.7)
    F.fingers(t, 16, 30, 12, 0.3)
    F.fingers(t, 48, 30, 12, 0.3)
    t.grime(0.2)
    return t


def gate_top(seed):
    t = metal(seed, (60, 64, 66), f0=60, smooth=0.6)
    area(t, 18, 6, 46, 26, col=(20, 22, 24), h=0.6, smooth=0.85)
    edges(t, 17, 5, 47, 27, w=1)
    disc(t, 32, 16, 6, col=(40, 70, 52), ring=1.4)
    area(t, 22, 40, 42, 44, col=(8, 8, 8))
    edges(t, 21, 39, 43, 45, w=1, light=1.3, dark=0.6)
    disc(t, 12, 48, 2.5, col=(120, 30, 24))
    disc(t, 52, 48, 2.5, col=(30, 90, 50))
    t.grime(0.15)
    return t


def paddle(seed):
    t = glass(seed, tint=(186, 196, 194), alpha=150, frit=True)
    return t


@lru_cache(maxsize=None)
def clock_pic():
    p = Pic(1, 1, (0, 0, 0, 0), 8010)
    c = 32
    p.ellipse(c - 30, c - 30, c + 30, c + 30, fill=(30, 32, 34))
    p.ellipse(c - 27, c - 27, c + 27, c + 27, fill=(236, 234, 226))
    for k in range(60):
        a = k / 60 * np.pi * 2
        r0 = 20 if k % 5 == 0 else 24
        w = 2.6 if k % 5 == 0 else 0.8
        p.line([(c + np.sin(a) * r0, c - np.cos(a) * r0), (c + np.sin(a) * 25.5, c - np.cos(a) * 25.5)], (20, 20, 20), width=w)
    emblem(p, c, c + 11, 4.5)
    # stopped at 4:52, when the power went
    for a, ln, w in ((4.87 / 12, 14, 3.2), (52 / 60, 22, 2.2)):
        ang = a * np.pi * 2
        p.line([(c - np.sin(ang) * 4, c + np.cos(ang) * 4), (c + np.sin(ang) * ln, c - np.cos(ang) * ln)], (14, 14, 14), width=w)
    p.ellipse(c - 2, c - 2, c + 2, c + 2, fill=(14, 14, 14))
    return p


def clock_face(seed):
    t = clock_pic().tile(0, seed, smooth=0.75, grime=0.18)
    t.f0[:] = 50
    return t


@lru_cache(maxsize=None)
def platnum_pic(n):
    p = Pic(1, 1, (0, 0, 0, 0), 8020 + n)
    p.rect(4, 4, 60, 60, fill=(150, 152, 150))
    p.rect(6, 6, 58, 58, fill=GREEN)
    p.text(32, 34, str(n), BLACK, 44, CREAM, anchor="mm")
    return p


def platnum(n):
    t = platnum_pic(n).tile(0, 8030 + n, smooth=0.55, grime=0.16)
    weather(t, rust=0.1, streak=0.4, grime=0.08)
    return t


def baffle(seed):
    """The concourse's ceiling from below: linear aluminium baffles, the black void over them,
    some missing, the rest bowed and stained where the platforms leak."""
    t = F.plain(seed, (14, 14, 14), 0.02, 0.1)
    t.h[:] = 0.2
    for k in range(8):
        if t.rng.random() < 0.06:
            continue
        x0 = k * 8 + 1
        area(t, x0, 0, x0 + 5, N, col=(188, 190, 188), h=0.9, smooth=0.5, f0=160)
        area(t, x0, 0, x0 + 1, N, mul=1.1)
        area(t, x0 + 4, 0, x0 + 5, N, mul=0.7)
    stain = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2 - 0.5, 0, 1)
    t.mix(stain * 0.55, (110, 90, 60), 0.8)
    t.grime(0.2)
    return t


def strip_lamp(seed, on):
    t = baffle(seed)
    area(t, 24, 0, 40, N, col=(30, 30, 30), h=0.5)
    area(t, 26, 0, 38, N, col=(232, 232, 222) if on else (140, 140, 134), h=0.7)
    if on:
        t.emit[:, 26:38] = 0.9
    return t


def help_face(seed):
    """A help point: the railway's green, HELP in cream, the speaker grille, the call button."""
    t = steel(seed, GREEN, rust=0.15, chips=0.5)
    F.paste(t, F.text_mask("HELP", BOLD, 8, 40, 10, seed=13), 12, 6, CREAM)
    for yy in range(22, 36, 3):
        for xx in range(20, 46, 3):
            disc(t, xx, yy, 0.8, col=(14, 14, 14), dh=-0.2)
    disc(t, 32, 46, 5, col=(170, 30, 24), h=0.95)
    disc(t, 32, 46, 5.5, col=(190, 194, 194), ring=1)
    return t


def vault_glass(seed, cracked=0):
    """The roof's glass from below and above: fritted against the sun, green with algae and leaves
    in the low corners where the rain sits."""
    t = glass(seed, tint=(140, 158, 150), alpha=120, frit=True, cracked=cracked)
    yy, xx = np.mgrid[0:N, 0:N]
    corner = np.clip(1 - np.minimum(xx, yy) / 12, 0, 1) * np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 2 + 0.3, 0, 1)
    t.mix(corner, (64, 82, 40), 0.8)
    t.alpha = np.clip(t.alpha + corner * 120, 0, 255)
    leaves = (t.rng.random((N, N)) < 0.004)
    for y, x in zip(*np.nonzero(leaves)):
        disc(t, x, y, 1.6, col=(110, 76, 36), alpha=240)
    return t


def step_top(seed):
    """A granite stair's treads from above: dark polished granite, a ribbed anti-slip insert and a
    yellow line at each nose (the stair block's two: the upper's at half way, the lower's at the foot)."""
    t = granite(seed)
    for y0 in (24, 56):
        area(t, 0, y0, N, y0 + 6, col=(70, 72, 72), h=0.8, f0=120)
        for x in range(0, N, 2):
            area(t, x, y0, x + 1, y0 + 6, mul=0.7, dh=-0.1)
        area(t, 0, y0 + 6, N, y0 + 8, col=(214, 170, 30), h=0.85)
    t.grime(0.2)
    return t


def textures():
    T = {}
    T["tx_steel_w"] = lambda: steel(7600, WHITE, rust=0.3, chips=0.7)
    T["tx_steel_d"] = lambda: steel(7601, ANTH, rust=0.2, chips=0.4)
    T["tx_green"] = lambda: steel(7602, GREEN, rust=0.2, chips=0.6)
    T["tx_fascia"] = lambda: steel(7603, ANTH, rust=0.3, chips=0.3)
    T["tx_coping"] = lambda: coping(7604)
    T["tx_membrane"] = lambda: membrane(7605)
    T["tx_slat_ceiling"] = lambda: slats(7606)
    T["tx_glass"] = lambda: glass(7607)
    T["tx_glass_cracked"] = lambda: glass(7608, cracked=2)
    T["tx_glass_broken"] = lambda: glass(7609, cracked=1, holes=0.45)
    T["tx_glass_roof"] = lambda: glass(7610, tint=(132, 150, 140), alpha=150, frit=True, cracked=1)
    T["tx_canopy_glass"] = lambda: glass(7611, tint=(140, 158, 156), alpha=130, frit=True)
    T["tx_panel"] = lambda: panel(7612)
    for k, part in enumerate(("hi", "mid", "lo")):
        T[f"tx_panel_{part}"] = lambda k=k, part=part: tall_panel(7640 + k, part)
        T[f"tx_panel_{part}_s"] = lambda k=k, part=part: tall_panel(7650 + k, part, streaked=True)
    T["tx_panel_b"] = lambda: panel(7613, streaked=True)
    T["tx_wall"] = lambda: wall(7614)
    T["tx_wall_base"] = lambda: wall(7615, base=True)
    T["tx_granite"] = lambda: granite(7616)
    T["tx_terrazzo"] = lambda: terrazzo(7617)
    T["tx_terrazzo_b"] = lambda: terrazzo(7618, cracked=True)
    T["tx_terrazzo_d"] = lambda: terrazzo(7619, dark=True)
    T["tx_truss_web"] = lambda: truss_web(7620)
    T["tx_perf"] = lambda: perf(7621)
    T["tx_tvm_lo"] = lambda: tvm(7622, False)
    T["tx_tvm_hi"] = lambda: tvm(7623, True)
    T["tx_tvm_lo_smashed"] = lambda: tvm(7624, False, smashed=True)
    T["tx_tvm_hi_smashed"] = lambda: tvm(7625, True, smashed=True)
    T["tx_validator"] = lambda: validator(7626)
    T["tx_ticket_glass"] = lambda: ticket_glass(7627)
    T["tx_counter_front"] = lambda: counter_front(7628)
    T["tx_door_top"] = lambda: door_tex(7629, True)
    T["tx_door_bottom"] = lambda: door_tex(7630, False)
    T["tx_lamp"] = lambda: lamp(7631, False)
    T["tx_lamp_on"] = lambda: lamp(7632, True)
    T["tx_bin"] = lambda: bin_side(7633)
    T["tx_bin_top"] = lambda: bin_top(7634)
    T["tx_gutter"] = lambda: gutter(7635)
    T["tx_brushed"] = lambda: brushed(7636)
    T["tx_step_top"] = lambda: step_top(7720)
    T["tx_paver"] = lambda: paver(7700)
    T["tx_paver_b"] = lambda: paver(7701, cracked=True)
    T["tx_edge_top"] = lambda: edge_top(7702)
    T["tx_edge_face"] = lambda: edge_face(7703)
    T["tx_esc_tread"] = lambda: esc_tread(7704)
    T["tx_esc_riser"] = lambda: esc_riser(7705)
    T["tx_esc_glass"] = lambda: esc_glass(7706)
    T["tx_rubber"] = lambda: rubber(7707)
    T["tx_esc_comb"] = lambda: esc_comb(7708)
    T["tx_gate_side"] = lambda: gate_side(7709)
    T["tx_gate_top"] = lambda: gate_top(7710)
    T["tx_paddle"] = lambda: paddle(7711)
    T["tx_clock"] = lambda: clock_face(7712)
    T["tx_platnum_1"] = lambda: platnum(1)
    T["tx_platnum_2"] = lambda: platnum(2)
    T["tx_baffle"] = lambda: baffle(7713)
    T["tx_strip"] = lambda: strip_lamp(7714, False)
    T["tx_strip_on"] = lambda: strip_lamp(7715, True)
    T["tx_help"] = lambda: help_face(7716)
    T["tx_vault_glass"] = lambda: vault_glass(7717)
    T["tx_vault_glass_cracked"] = lambda: vault_glass(7718, cracked=2)
    T["tx_concrete"] = lambda: F.plain(7719, (132, 130, 124), 0.08, 0.25)
    for i in range(8):
        T[f"tx_board_{i}"] = lambda i=i: board(i)
    for key in STATIONS:
        for i in range(NAME_W):
            T[f"tx_name_{key}_{i}"] = lambda key=key, i=i: name_tile(key, i)
        for i in range(6):
            T[f"tx_map_{key}_{i}"] = lambda key=key, i=i: map_tile(key, i)
    for key in SIGNS:
        for i in range(2):
            T[f"tx_sign_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def column(x0=5, x1=11, y0=0, y1=16):
    """A round column in plan, as two crossed boxes (an octagon near enough)."""
    return [[x0, y0, x0 + 1, x1, y1, x1 - 1, "tx_steel_w"], [x0 + 1, y0, x0, x1 - 1, y1, x1, "tx_steel_w"]]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, **extra):
        if boxes and rotate == "axis":
            boxes = P.along_z(boxes)  # RULE: axis models run along z as drawn
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    # the structure
    add("tx_column", column(), "Steel Column", "METAL", rotate=None)
    add("tx_column_base", column() + [[3.5, 0, 3.5, 12.5, 1.5, 12.5, "tx_steel_d"]]
        + [[x, 1.5, z, x + 1, 2.2, z + 1, "tx_steel_d"] for x in (4.5, 10.5) for z in (4.5, 10.5)], "Steel Column", "METAL", rotate=None)
    add("tx_column_cap", column(y1=14.5) + [[3.5, 14.5, 3.5, 12.5, 16, 12.5, "tx_steel_w"],
                                            [7.4, 10, 2, 8.6, 14.5, 14, "tx_steel_w"], [2, 10, 7.4, 14, 14.5, 8.6, "tx_steel_w"]],
        "Steel Column", "METAL", rotate=None)
    web = {"east": "tx_truss_web", "west": "tx_truss_web", "up": "tx_steel_w", "down": "tx_steel_w",
           "north": "tx_steel_w", "south": "tx_steel_w"}
    add("tx_truss", [[6.5, 13, 0, 9.5, 16, 16, "tx_steel_w"], [6.5, 0, 0, 9.5, 2.5, 16, "tx_steel_w"],
                     [7.5, 2.5, 0, 8.5, 13, 16, web]], "Roof Truss", "METAL", rotate="axis", render="cutout")
    add("tx_beam", [[4, 13.5, 0, 12, 16, 16, "tx_steel_w"], [7, 2.5, 0, 9, 13.5, 16, "tx_steel_w"],
                    [4, 0, 0, 12, 2.5, 16, "tx_steel_w"]], "Steel Beam", "METAL", rotate="axis")
    add("tx_roof", None, "Roof", "STONE", rotate=None, tex={"up": "tx_membrane", "down": "tx_slat_ceiling", "side": "tx_fascia"})
    add("tx_roof_glass", None, "Rooflight", "GLASS", rotate=None, tex={"all": "tx_glass_roof"}, render="translucent")
    add("tx_parapet", None, "Parapet", "METAL", rotate=None, tex={"up": "tx_coping", "down": "tx_fascia", "side": "tx_fascia"})
    add("tx_fascia", None, "Fascia", "METAL", rotate=None, tex={"all": "tx_fascia"})
    for key in STATIONS:
        for i in range(NAME_W):
            n = f"tx_name_{key}_{i}"
            add(n, None, "Station Name", "METAL", tex={"north": n, "south": "tx_fascia", "east": "tx_fascia",
                                                         "west": "tx_fascia", "up": "tx_coping", "down": "tx_fascia"})
    # walls and floors
    for n in ("tx_panel", "tx_panel_b", "tx_wall", "tx_granite", "tx_terrazzo", "tx_terrazzo_b", "tx_terrazzo_d"):
        add(n, None, {"tx_panel": "Cladding Panel", "tx_panel_b": "Cladding Panel", "tx_wall": "Plaster Wall",
                      "tx_granite": "Granite", "tx_terrazzo": "Terrazzo", "tx_terrazzo_b": "Terrazzo",
                      "tx_terrazzo_d": "Terrazzo"}[n], "STONE", rotate=None, tex={"all": n})
    for part in ("hi", "mid", "lo"):
        for v in ("", "_s"):
            n = f"tx_panel_{part}{v}"
            add(n, None, "Cladding Panel", "STONE", rotate=None, tex={"side": n, "up": "tx_panel", "down": "tx_panel"})
    add("tx_wall_base", None, "Plaster Wall", "STONE", rotate=None, tex={"side": "tx_wall_base", "up": "tx_wall", "down": "tx_wall"})
    # glazing: the pane on the block's middle, along z; a mullion at its north end (a row of them
    # puts one every block), a transom along the foot of the _t ones
    def pane(tex):
        return {"east": tex, "west": tex, "north": tex, "south": tex, "up": tex, "down": tex}
    mull = [6.5, 0, 0, 9.5, 16, 1.5, "tx_steel_d"]
    tran = [6.5, 0, 1.5, 9.5, 1.5, 16, "tx_steel_d"]
    add("tx_glass", [[7.5, 0, 1.5, 8.5, 16, 16, pane("tx_glass")], mull], "Glass Wall", "GLASS", rotate="axis", render="translucent")
    add("tx_glass_t", [[7.5, 1.5, 1.5, 8.5, 16, 16, pane("tx_glass")], mull, tran], "Glass Wall", "GLASS", rotate="axis",
        render="translucent")
    add("tx_glass_cracked", [[7.5, 0, 1.5, 8.5, 16, 16, pane("tx_glass_cracked")], mull], "Glass Wall", "GLASS", rotate="axis",
        render="translucent")
    add("tx_glass_broken", [[7.5, 0, 1.5, 8.5, 16, 16, pane("tx_glass_broken")], mull], "Glass Wall", "GLASS", rotate="axis",
        render="translucent")
    add("tx_glass_frame", [mull, tran], "Glass Wall Frame", "METAL", rotate="axis")
    out["tx_door"] = {"door": "steel", "tex": {"top": "tx_door_top", "bottom": "tx_door_bottom"}, "title": "Glass Door"}
    # the canopy: fritted glass at the top of the block in a grid of bars (one along each block's
    # west and north edge); the gutter at the tip; the tapered arms under it, 0 at the wall
    cglass = [0, 12, 0, 16, 13, 16, {"up": "tx_canopy_glass", "down": "tx_canopy_glass", "side": "tx_steel_d"}]
    bars = [[0, 11, 0, 1.5, 13.5, 16, "tx_steel_d"], [1.5, 11.5, 0, 16, 13, 1.5, "tx_steel_d"]]
    add("tx_canopy", [cglass] + bars, "Glass Canopy", "GLASS", rotate=None, render="translucent")
    add("tx_canopy_edge", [[0, 12, 4, 16, 13, 16, cglass[6]], [0, 10, 0, 16, 14, 4, {"up": "tx_gutter", "side": "tx_steel_d",
                                                                                         "down": "tx_steel_d"}],
                           [0, 11, 4, 1.5, 13.5, 16, "tx_steel_d"]], "Canopy Gutter", "METAL", render="translucent")
    for k in range(7):
        # the arm's underside rises from y 4 at the wall to y 10.5 at the tip, half a step a half block
        b0, b1 = 4 + k * 0.93, 4 + k * 0.93 + 0.46
        arm = [[7, b1, 0, 9, 12, 8, "tx_steel_w"], [7, b0, 8, 9, 12, 16, "tx_steel_w"],
               [6, b1, 0, 10, b1 + 0.8, 8, "tx_steel_w"], [6, b0, 8, 10, b0 + 0.8, 16, "tx_steel_w"]]
        add(f"tx_canopy_arm_{k}", [cglass] + bars + arm, "Glass Canopy", "GLASS", render="translucent")
    # fittings
    seat = {"up": "tx_perf", "down": "tx_perf", "side": "tx_steel_d"}
    add("tx_bench", [[1, 6, 3, 15, 7, 11, seat], [1, 7, 11, 15, 15, 12, {"north": "tx_perf", "south": "tx_perf", "side": "tx_steel_d"}],
                     [7.4, 7, 4, 8.6, 10.5, 11, "tx_steel_d"], [1, 7, 4, 2, 10.5, 11, "tx_steel_d"], [14, 7, 4, 15, 10.5, 11, "tx_steel_d"],
                     [3, 0, 6, 5, 6, 9, "tx_steel_d"], [11, 0, 6, 13, 6, 9, "tx_steel_d"], [2, 0, 5, 6, 0.6, 10, "tx_steel_d"],
                     [10, 0, 5, 14, 0.6, 10, "tx_steel_d"]], "Station Bench", "METAL", render="cutout")
    add("tx_tvm", [[1, 0, 4, 15, 16, 16, {"north": "tx_tvm_lo", "side": "tx_steel_d", "up": "tx_steel_d", "down": "tx_steel_d"}],
                   [5, 4.5, 2.6, 11, 6, 4, "tx_steel_d"]], "Ticket Machine", "METAL", loot="cash")
    add("tx_tvm_top", [[1, 0, 4, 15, 13, 16, {"north": "tx_tvm_hi", "side": "tx_steel_d", "up": "tx_green", "down": "tx_steel_d"}],
                       [0.5, 13, 2, 15.5, 14.2, 16, "tx_green"]], "Ticket Machine", "METAL")
    add("tx_tvm_smashed", [[1, 0, 4, 15, 16, 16, {"north": "tx_tvm_lo_smashed", "side": "tx_steel_d", "up": "tx_steel_d",
                                                  "down": "tx_steel_d"}]], "Ticket Machine", "METAL")
    add("tx_tvm_top_smashed", [[1, 0, 4, 15, 13, 16, {"north": "tx_tvm_hi_smashed", "side": "tx_steel_d", "up": "tx_green",
                                                      "down": "tx_steel_d"}], [0.5, 13, 2, 15.5, 14.2, 16, "tx_green"]],
        "Ticket Machine", "METAL")
    add("tx_validator", [[7, 0, 7, 9, 9, 9, "tx_steel_d"], [6, 0, 6, 10, 0.6, 10, "tx_steel_d"],
                         [5, 9, 5, 11, 14, 10, {"north": "tx_validator", "side": "tx_green", "up": "tx_green", "down": "tx_green",
                                                "south": "tx_green"}]], "Ticket Validator", "METAL")
    # the ticket office's window over its counter: the granite ledge, the deal tray, the glass with
    # its speaking grille in a steel frame; below it the counter's green enamel front
    add("tx_ticket_counter", None, "Ticket Counter", "METAL", tex={"north": "tx_counter_front", "south": "tx_wall",
                                                                   "east": "tx_wall", "west": "tx_wall", "up": "tx_granite",
                                                                   "down": "tx_wall"})
    add("tx_ticket_window", [[0, 0, 1.5, 16, 1, 16, "tx_granite"], [5, 1, 2, 11, 1.4, 9, "tx_brushed"],
                             [0, 1, 9, 16, 15, 10, pane("tx_ticket_glass")], [0, 1, 8.5, 1, 16, 10.5, "tx_steel_d"],
                             [15, 1, 8.5, 16, 16, 10.5, "tx_steel_d"], [1, 15, 8.5, 15, 16, 10.5, "tx_steel_d"]],
        "Ticket Window", "GLASS", render="translucent")
    add("tx_bin", [[4, 0, 5, 12, 11, 11, {"side": "tx_bin", "up": "tx_bin_top", "down": "tx_brushed"}],
                   [5, 0, 4, 11, 11, 12, {"side": "tx_bin", "up": "tx_bin_top", "down": "tx_brushed"}]], "Litter Bin", "METAL",
        rotate=None, loot="scraps")
    add("tx_pendant", [[7.5, 8, 7.5, 8.5, 16, 8.5, "tx_steel_d"], [5, 6, 5, 11, 8, 11, "tx_steel_d"],
                       [3, 4, 3, 13, 6, 13, {"side": "tx_steel_d", "up": "tx_steel_d", "down": "tx_lamp"}]],
        "Pendant Light", "METAL", rotate=None, decor=True, flicker={"light": 13, "on": {"tx_lamp": "tx_lamp_on"}})
    # a dead one: no flicker, no light (the hall has hundreds; only a few still stutter)
    add("tx_pendant_off", [[7.5, 8, 7.5, 8.5, 16, 8.5, "tx_steel_d"], [5, 6, 5, 11, 8, 11, "tx_steel_d"],
                           [3, 4, 3, 13, 6, 13, {"side": "tx_steel_d", "up": "tx_steel_d", "down": "tx_lamp"}]],
        "Pendant Light", "METAL", rotate=None, decor=True)
    for i in range(8):
        n = f"tx_board_{i}"
        add(n, [[0, 0, 12, 16, 16, 16, {"north": n, "side": "tx_steel_d", "up": "tx_steel_d", "down": "tx_steel_d",
                                         "south": "tx_steel_d"}]], "Departures Board", "METAL")
    for key in STATIONS:
        for i in range(6):
            n = f"tx_map_{key}_{i}"
            add(n, [[0, 0, 15, 16, 16, 16, {"north": n, "side": "tx_steel_d", "up": "tx_steel_d", "down": "tx_steel_d",
                                             "south": "tx_steel_d"}]], "Network Map", "GLASS", decor=True)
    for key in SIGNS:
        for i in range(2):
            n = f"tx_sign_{key}_{i}"
            add(n, [[0, 4.5, 7.5, 16, 11.5, 8.5, {"north": n, "south": "tx_green", "side": "tx_steel_d", "up": "tx_steel_d",
                                                  "down": "tx_steel_d"}],
                    [7.6, 11.5, 7.6, 8.4, 16, 8.4, "tx_steel_d"]], "Sign", "METAL", decor=True, render="cutout")
    # ------------------------------------------------ Union Station's train hall
    add("tx_tree_base", column(5.5, 10.5) + [[3, 0, 3, 13, 1.2, 13, "tx_steel_d"], [4, 1.2, 4, 12, 2.4, 12, "tx_steel_w"],
                                             [4.8, 2.4, 4.8, 11.2, 3.4, 11.2, "tx_steel_w"]]
        + [[x, 1.2, z, x + 1, 1.8, z + 1, "tx_steel_d"] for x in (3.6, 11.4) for z in (3.6, 11.4)], "Steel Tree", "METAL", rotate=None)
    add("tx_tree", column(5.5, 10.5), "Steel Tree", "METAL", rotate=None)

    def chain(x0, y0, z0, x1, y1, z1, n=8, r=1.6):
        """Small cubes along a line: a slim member running diagonally through the block."""
        out_ = []
        for i in range(n):
            t = (i + 0.5) / n
            x, y, z = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t
            out_.append([round(x - r, 2), round(y - r, 2), round(z - r, 2), round(x + r, 2), round(y + r, 2), round(z + r, 2), "tx_steel_w"])
        return out_
    hub = column(4.5, 11.5, 6, 10) + [[5, 10, 5, 11, 11, 11, "tx_steel_w"]]
    arms = []
    for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        arms += chain(8, 9, 8, 8 + dx * 8, 16.5, 8 + dz * 8, n=5)
    for dx, dz in ((1, -1), (1, 1), (-1, 1), (-1, -1)):
        arms += chain(8, 9, 8, 8 + dx * 8, 16, 8 + dz * 8, n=5)
    add("tx_tree_fork", column(5.5, 10.5, 0, 6) + hub + arms, "Steel Tree", "METAL", rotate=None, render="cutout")
    # an arm rising toward the facing (north as drawn): in at the foot of the south side, out at the top of the north
    add("tx_branch", chain(8, 0.5, 16, 8, 16.5, 0), "Steel Tree Arm", "METAL", decor=True, render="cutout")
    # a diagonal arm, toward north-east as drawn: rising a block for each block on along both
    add("tx_branch_d", chain(0, 0, 16, 16, 16, 0, n=10), "Steel Tree Arm", "METAL", decor=True, render="cutout")
    vglass = [0, 14, 0, 16, 15, 16, {"up": "tx_vault_glass", "down": "tx_vault_glass", "side": "tx_steel_d"}]
    vcrack = [0, 14, 0, 16, 15, 16, {"up": "tx_vault_glass_cracked", "down": "tx_vault_glass_cracked", "side": "tx_steel_d"}]
    vbars = [[0, 12.5, 0, 1.2, 16, 16, "tx_steel_w"], [1.2, 12.5, 0, 16, 16, 1.2, "tx_steel_w"],
             [0, 15, 7.6, 16, 15.6, 8.4, "tx_steel_d"]]
    add("tx_vault", [vglass] + vbars, "Glass Roof", "GLASS", rotate=None, render="translucent")
    add("tx_vault_cracked", [vcrack] + vbars, "Glass Roof", "GLASS", rotate=None, render="translucent")
    add("tx_vault_frame", vbars[:2], "Glass Roof Frame", "METAL", rotate=None, render="cutout")
    # the roof's steel under the glass: an I-beam along z (the arms of the trees end on its foot),
    # and the node where two cross
    ibeam = [[4, 11, 0, 12, 14, 16, "tx_steel_w"], [7, 2.5, 0, 9, 11, 16, "tx_steel_w"], [4, 0, 0, 12, 2.5, 16, "tx_steel_w"]]
    ibeam_x = [[b[2], b[1], b[0], b[5], b[4], b[3], b[6]] for b in ibeam]
    add("tx_vault_beam", [vglass] + ibeam, "Glass Roof Beam", "GLASS", rotate="axis", render="translucent")
    add("tx_vault_node", [vglass] + ibeam + ibeam_x + [[3, 0, 3, 13, 3, 13, "tx_steel_d"]], "Glass Roof Beam", "GLASS",
        rotate=None, render="translucent")
    add("tx_paver", None, "Paving", "STONE", rotate=None, tex={"all": "tx_paver"})
    add("tx_paver_b", None, "Paving", "STONE", rotate=None, tex={"all": "tx_paver_b"})
    add("tx_concrete", None, "Concrete", "STONE", rotate=None, tex={"all": "tx_concrete"})
    add("tx_edge", [[0, 0, 0, 16, 16, 16, {"up": "tx_edge_top", "north": "tx_edge_face", "side": "tx_concrete", "down": "tx_concrete"}]],
        "Platform Edge", "STONE")
    # the escalator, stopped: stairs facing the way up, its glass balustrade over each step on the
    # left or the right of the way up, the handrail rising a block a block in four short runs
    add("tx_esc_step", [[0, 0, 0, 16, 8, 16, {"up": "tx_esc_tread", "side": "tx_esc_riser", "down": "tx_esc_riser"}],
                        [0, 8, 0, 16, 16, 8, {"up": "tx_esc_tread", "side": "tx_esc_riser", "down": "tx_esc_riser"}]],
        "Escalator", "METAL")
    eg = {"east": "tx_esc_glass", "west": "tx_esc_glass", "north": "tx_esc_glass", "south": "tx_esc_glass",
          "up": "tx_esc_glass", "down": "tx_esc_glass"}
    for side, x0 in (("l", 0.4), ("r", 14.0)):
        bx = []
        for i in range(4):
            za, zb = 16 - 4 * (i + 1), 16 - 4 * i
            top = 7 + 4 * i
            bx.append([x0 + 0.4, -8 + 4 * i, za, x0 + 1.2, top, zb, eg])
            # each run of the handrail tall enough to meet the next, so it reads as one rail
            bx.append([x0, top, za, x0 + 1.6, top + 4.4, zb, "tx_rubber"])
            bx.append([x0 - (0 if side == "l" else 0.6), -8 + 4 * i, za, x0 + 1.6 + (0.6 if side == "l" else 0), -6 + 4 * i, zb, "tx_brushed"])
        add(f"tx_esc_side_{side}", bx, "Escalator Balustrade", "GLASS", decor=True, render="translucent")
        add(f"tx_esc_newel_{side}", [[x0 + 0.4, 0, 4, x0 + 1.2, 13, 16, eg], [x0, 13, 2.5, x0 + 1.6, 15, 16, "tx_rubber"],
                                     [x0, 4, 0.5, x0 + 1.6, 15, 2.5, "tx_rubber"], [x0 - 0.3, 0, 2, x0 + 1.9, 3, 16, "tx_brushed"]],
            "Escalator Balustrade", "GLASS", decor=True, render="translucent")
    add("tx_step", [[0, 0, 0, 16, 8, 16, {"up": "tx_step_top", "side": "tx_granite", "down": "tx_granite"}],
                    [0, 8, 0, 16, 16, 8, {"up": "tx_step_top", "side": "tx_granite", "down": "tx_granite"}]], "Granite Steps", "STONE")
    add("tx_esc_plate", [[0, 0, 0, 16, 16, 16, {"up": "tx_esc_comb", "side": "tx_esc_riser", "down": "tx_esc_riser"}]],
        "Escalator Landing", "METAL")
    add("tx_glass_rail", [[7.5, 0, 0, 8.5, 14.5, 16, pane("tx_esc_glass")], [6.8, 14.5, 0, 9.2, 16, 16, "tx_brushed"],
                          [6.8, 0, 0, 9.2, 1.5, 16, "tx_brushed"]], "Glass Balustrade", "GLASS", rotate="axis", render="translucent")
    gside = {"east": "tx_gate_side", "west": "tx_gate_side", "north": "tx_steel_d", "south": "tx_steel_d",
             "up": "tx_gate_top", "down": "tx_steel_d"}
    add("tx_gate", [[5, 0, 1, 11, 15, 15, gside], [4.4, 6, 3, 5, 13, 13, pane("tx_paddle")], [11, 6, 3, 11.6, 13, 13, pane("tx_paddle")],
                    [5.5, 15, 2, 10.5, 15.4, 14, "tx_steel_d"]], "Fare Gate", "METAL", rotate="axis", render="translucent")
    cface = {"east": "tx_clock", "west": "tx_clock", "north": "tx_steel_d", "south": "tx_steel_d", "up": "tx_steel_d",
             "down": "tx_steel_d"}
    add("tx_clock", [[6.5, 2, 1, 9.5, 14, 15, cface], [6.5, 1, 2, 9.5, 15, 14, cface], [7.6, 15, 7.6, 8.4, 16, 8.4, "tx_steel_d"]],
        "Station Clock", "METAL", rotate="axis", decor=True, render="cutout")
    add("tx_rod", [[7.6, 0, 7.6, 8.4, 16, 8.4, "tx_steel_d"]], "Hanger", "METAL", rotate=None, decor=True)
    for n in (1, 2):
        face = {"east": f"tx_platnum_{n}", "west": f"tx_platnum_{n}", "north": "tx_steel_d", "south": "tx_steel_d",
                "up": "tx_steel_d", "down": "tx_steel_d"}
        add(f"tx_platnum_{n}", [[7, 1, 1, 9, 15, 15, face], [7.6, 15, 7.6, 8.4, 16, 8.4, "tx_steel_d"]], "Platform Sign", "METAL",
            rotate="axis", decor=True, render="cutout")
    add("tx_ceiling", None, "Metal Ceiling", "METAL", rotate=None, tex={"down": "tx_baffle", "up": "tx_concrete", "side": "tx_concrete"})
    add("tx_ceiling_lamp", [[0, 0, 0, 16, 16, 16, {"down": "tx_strip", "up": "tx_concrete", "side": "tx_concrete"}]],
        "Ceiling Light", "METAL", rotate="axis", flicker={"light": 12, "on": {"tx_strip": "tx_strip_on"}})
    add("tx_ceiling_lamp_off", [[0, 0, 0, 16, 16, 16, {"down": "tx_strip", "up": "tx_concrete", "side": "tx_concrete"}]],
        "Ceiling Light", "METAL", rotate="axis")
    add("tx_help", [[4.5, 0, 6, 11.5, 16, 10, {"north": "tx_help", "side": "tx_steel_d", "up": "tx_steel_d", "down": "tx_steel_d",
                                               "south": "tx_green"}]], "Help Point", "METAL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("tx_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} transit blocks in {path}")


if __name__ == "__main__":
    main()
