"""Harbor Point Generating Station: the pw_ kit for Grayhaven Light & Power's coal plant on the
island's east side (world/county/PowerStation.java, PowerHalls.java, PowerYards.java).

No photos of it in the library yet (city/power-station-* to be queued): this follows what the
American city stations of the twenties were known for, the ones the utilities built on the
waterfront and kept running into the nineties. A brick turbine hall along the street like a
church, its tall arched steel windows black with soot, the name in cast stone over them; the
boiler house behind it taller still with its four brick stacks banded in iron; the control room
of the fifties between them, the benchboards and the meters and the one-line diagram across the
wall. Behind, the coal yard with its pile, the crusher tower and the gallery carrying the belt
up into the boiler house; the fuel oil tanks the plant was converted to in the seventies; the
switchyard with its transformers and lattice gantries. The last weeks: the night shift kept
Unit 3 on for the hospital and the shelters until the coal ran out, living in the control room.

Blocks (tile 0 is always the viewer's left; the base model faces north, anything on a wall has
its back at z = 16):
- the walls: pw_sash, pw_sash_broken, pw_sash_lit (a tall window's steel sash, set back in the
  wall), pw_arch_0..9 (its arched head, five by two), pw_name_0..19 (the cast-stone letters),
  pw_glp_0..15 (the carved panel over the door, eight by two), pw_logo_0..3, pw_mural_0..31
  (the painted name down the boiler house, sixteen by two), pw_stack_band, pw_cap, pw_soot;
- the turbines: pw_tg (casing), pw_tg_joint (the split line's flange), pw_tg_round (the casing's
  rounded top edge toward the facing), pw_tg_plate, pw_gen, pw_gen_round, pw_exciter, pw_rotor
  (axis; drawn along z), pw_pedestal, pw_gauge;
- the crane: pw_crane (axis), pw_crane_end, pw_crane_rail (axis), pw_trolley, pw_rope, pw_hook,
  pw_crane_cab, pw_tons_0..1;
- the boilers: pw_boiler, pw_burner(_on), pw_drum (axis), pw_drum_end, pw_gauge_glass,
  pw_steam (axis), pw_steam_v, pw_duct, pw_fan, pw_mill, pw_chute, pw_safety_valve;
- coal and oil: pw_belt, pw_belt_coal (axis), pw_coal_slab, pw_crusher, pw_hopper, pw_elev,
  pw_tank, pw_tank_roof, pw_tanklabel_0..3, pw_trestle, pw_dozer_blade/body/cab, pw_track (axis);
- the switchyard: pw_xfmr, pw_fins, pw_bushing, pw_bushing_lo, pw_conservator (axis),
  pw_breaker, pw_post_ins, pw_bus (axis), pw_disconnect (axis), pw_leg, pw_girt, pw_diag,
  pw_diagx, pw_ins_string, pw_wire (axis);
- the control room: pw_bench(_on), pw_vb_lo(_on), pw_vb_hi(_on), pw_annun(_on), pw_mimic_0..11
  (six by two), pw_freq_0..1, pw_desk;
- the shop and the offices: pw_lathe_head, pw_lathe_tail, pw_drill, pw_grinder, pw_vise_bench,
  pw_welder, pw_parts, pw_reel (axis), pw_pipe_rack (axis), pw_hardhats, pw_timeclock,
  pw_labbench, pw_fumehood, pw_model;
- floors: pw_tile, pw_tile_line;
- pictures: pw_safety_0..5 (three by two), pw_banner_0..5, pw_sgn_KEY_0..1, pw_spray_KEY_i.

    python3 tools/city/power.py          rewrites the pw_ entries in blocks.json

photo_textures.py draws the textures (power.register()).
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
from furniture import area, disc, drips  # noqa: E402
from school import Pic, mixc, HAND  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = B.BLACK
BOLD = B.BOLD
STENCIL = B.STENCIL
SPRAY = B.SPRAY
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
GREEN = (30, 70, 52)
CREAM = (232, 224, 200)
STONE = (196, 188, 168)
MACHINE = (98, 128, 112)
YELLOW = (230, 186, 30)
RED = (170, 30, 26)
WHITE = (244, 242, 236)
BRICK = (128, 60, 46)
MORTAR = (110, 104, 96)

SIGNS = {
    "turbine": ("TURBINE HALL", GREEN, CREAM),
    "boiler": ("BOILER HOUSE", GREEN, CREAM),
    "control": ("CONTROL ROOM", GREEN, CREAM),
    "shop": ("MACHINE SHOP", GREEN, CREAM),
    "stores": ("STORES", GREEN, CREAM),
    "lockers": ("LOCKER ROOM", GREEN, CREAM),
    "aid": ("FIRST AID", (30, 110, 60), WHITE),
    "office": ("PLANT OFFICE", GREEN, CREAM),
    "super": ("SUPERINTENDENT", GREEN, CREAM),
    "eng": ("ENGINEERING", GREEN, CREAM),
    "lab": ("CHEMICAL LAB", GREEN, CREAM),
    "lunch": ("LUNCH ROOM", GREEN, CREAM),
    "records": ("RECORDS", GREEN, CREAM),
    "visitors": ("VISITORS REPORT TO OFFICE", GREEN, CREAM),
    "coal": ("COAL HANDLING", GREEN, CREAM),
    "yard": ("SWITCHYARD", GREEN, CREAM),
    "relay": ("RELAY HOUSE", GREEN, CREAM),
    "unit1": ("UNIT 1", GREEN, CREAM),
    "unit2": ("UNIT 2", GREEN, CREAM),
    "unit3": ("UNIT 3", GREEN, CREAM),
    "men": ("MEN", GREEN, CREAM),
    "danger": ("DANGER HIGH VOLTAGE", YELLOW, (16, 16, 16)),
    "keepout": ("KEEP OUT", RED, WHITE),
    "hats": ("HARD HATS REQUIRED", YELLOW, (16, 16, 16)),
    "ears": ("HEARING PROTECTION", YELLOW, (16, 16, 16)),
    "smoke": ("NO SMOKING", RED, WHITE),
    "gate": ("ALL VEHICLES STOP", RED, WHITE),
    "oil": ("NO. 6 FUEL OIL", YELLOW, (16, 16, 16)),
    "crane": ("OVERHEAD CRANE", YELLOW, (16, 16, 16)),
}
SPRAYS = {
    "lights": ("KEEP THE LIGHTS ON", 4),
    "unit3": ("UNIT 3 STAYS ON", 3),
    "coal": ("NO MORE COAL", 3),
    "here": ("CREW IN CONTROL RM", 4),
}


# ---------------------------------------------------------------- walls

def sash(seed, lit=False, broken=False):
    """An industrial steel sash: four by four lights in a block, the bars painted green gone black,
    the glass grimed brown; broken, lights out, some patched with board."""
    t = F.plain(seed, (70, 76, 72), 0.05, 0.9)
    rng = t.rng
    y = np.arange(N)[:, None] / N
    t.c[:] = np.array((78, 84, 80), np.float32) * (0.8 + 0.35 * (1 - y))[..., None]
    t.vary(0.08, ((2, 1.0), (4, 0.6)))
    t.mix(np.clip(P.fbm(rng) * 1.2 + 0.3, 0, 1), (96, 82, 60), 0.35)
    t.alpha[:] = 150
    t.f0[:] = 20
    t.poro[:] = 0
    if lit:
        t.c[:] = mixc((220, 190, 120), (120, 100, 70), 0.3)
        t.alpha[:] = 225
        t.emit[:] = 0.55
    for k in range(4):
        for j in range(4):
            x0, y0 = k * 16 + 2, j * 16 + 2
            r = rng.random()
            if broken and r < 0.28:
                area(t, x0, y0, x0 + 13, y0 + 13, alpha=0)
                t.emit[y0:y0 + 13, x0:x0 + 13] = 0
            elif broken and r < 0.36:
                area(t, x0, y0, x0 + 13, y0 + 13, col=(120, 98, 70), alpha=255, smooth=0.1)
                t.emit[y0:y0 + 13, x0:x0 + 13] = 0
            elif r < 0.1:
                # a light painted over in the war and never scraped
                area(t, x0, y0, x0 + 13, y0 + 13, col=(70, 90, 80), alpha=240, smooth=0.3)
    bar = (40, 46, 42)
    for k in range(0, N + 1, 16):
        for a in (k - 2, k - 1, k, k + 1):
            if 0 <= a < N:
                t.c[:, a] = bar
                t.c[a, :] = bar
                t.alpha[:, a] = 255
                t.alpha[a, :] = 255
                t.emit[:, a] = 0
                t.emit[a, :] = 0
                t.smooth[:, a] = 0.4
                t.smooth[a, :] = 0.4
                t.f0[:, a] = 231
                t.f0[a, :] = 231
    t.speckle(0.02, (130, 70, 40), 0.6)
    return t


def brick_pic(p, x0, y0, x1, y1, seed=0):
    """Common bond on a picture: the courses, the mortar, a header course every sixth."""
    rng = np.random.default_rng(seed)
    p.rect(x0, y0, x1, y1, fill=MORTAR)
    row = 0
    y = y0
    while y < y1:
        off = 0 if row % 2 == 0 else 8
        bw = 8 if row % 6 == 5 else 16
        x = x0 - off
        while x < x1:
            c = tuple(int(v * rng.uniform(0.82, 1.1)) for v in BRICK)
            if min(x + bw - 1, x1) > max(x, x0):
                p.rect(max(x, x0), y, min(x + bw - 1, x1), min(y + 7, y1), fill=c)
            x += bw
        y += 8
        row += 1


@lru_cache(maxsize=None)
def arch_pics():
    """A tall window's head: the segmental arch in two rings of headers, the fanlight's bars
    radiating from the springing; mask is the brick with the light cut out, glass the light."""
    mask = Pic(5, 2, (0, 0, 0, 0), 40000)
    W, H = mask.W, mask.H
    brick_pic(mask, 0, 0, W, H, 40001)
    cx, cy = W / 2, H + 40
    ro, ri = H + 36, H + 18
    # the rings of headers
    mask.ellipse(cx - ro, cy - ro, cx + ro, cy + ro, fill=(96, 44, 34))
    for k in range(0, 180, 4):
        a = math.radians(180 + k)
        mask.line([(cx + math.cos(a) * ri, cy + math.sin(a) * ri), (cx + math.cos(a) * ro, cy + math.sin(a) * ro)], MORTAR, 0.6)
    mask.ellipse(cx - ri, cy - ri, cx + ri, cy + ri, fill=(150, 146, 134))
    rk = ri - 3
    # cut the light out (PIL writes the see-through colour straight over)
    mask.d.ellipse([(cx - rk) * 4, (cy - rk) * 4, (cx + rk) * 4, (cy + rk) * 4], fill=(0, 0, 0, 0))
    mask.d.rectangle([0, (H - 2) * 4, W * 4, H * 4], fill=(0, 0, 0, 0))
    glass = Pic(5, 2, (0, 0, 0, 0), 40002)
    glass.d.ellipse([(cx - rk) * 4, (cy - rk) * 4, (cx + rk) * 4, (cy + rk) * 4], fill=(84, 88, 80, 150))
    for k in range(1, 12):
        a = math.radians(180 + k * 15)
        glass.line([(cx, cy), (cx + math.cos(a) * rk, cy + math.sin(a) * rk)], (40, 46, 42), 1.6)
    for r in (rk * 0.62, rk * 0.84):
        glass.ellipse(cx - r, cy - r, cx + r, cy + r, outline=(40, 46, 42), width=1.6)
    glass.ellipse(cx - rk, cy - rk, cx + rk, cy + rk, outline=(40, 46, 42), width=2)
    for x in range(0, W + 1, 64):
        glass.line([(x, 0), (x, H)], (40, 46, 42), 2)
    glass.d.rectangle([0, (H - 3) * 4, W * 4, H * 4], fill=(40, 46, 42, 255))
    return mask, glass


def arch(i, part):
    mask, glass = arch_pics()
    if part == "mask":
        t = mask.tile(i, 40010 + i, smooth=0.15, grime=0.3)
        t.streaks(0.6, 0.7)
        return t
    t = glass.tile(i, 40030 + i, smooth=0.9, grime=0.0)
    t.f0[:] = 20
    lum = t.alpha < 200
    t.mix(np.where(lum, np.clip(P.fbm(t.rng) + 0.3, 0, 1), 0), (96, 82, 60), 0.3)
    return t


@lru_cache(maxsize=None)
def name_pic():
    """The name in cast-stone letters along the turbine hall's parapet."""
    p = Pic(20, 1, (0, 0, 0, 0), 40100)
    W, H = p.W, p.H
    text = "HARBOR  POINT  STATION"
    size = civic.fit(text, SERIF, 44, W - 30)
    p.text(W / 2 + 2, H / 2 + 3, text, SERIF, size, (60, 50, 40), anchor="mm", a=180)
    p.text(W / 2, H / 2 + 1, text, SERIF, size, STONE, anchor="mm")
    return p


def name(i):
    t = name_pic().tile(i, 40110 + i, smooth=0.2, grime=0.25)
    t.streaks(0.7, 0.7)
    return t


@lru_cache(maxsize=None)
def glp_pic():
    """The carved limestone over the door: the company's name, the date, the bolts of lightning."""
    p = Pic(8, 2, STONE, 40200)
    W, H = p.W, p.H
    p.rect(4, 4, W - 4, H - 4, outline=(150, 144, 128), width=3)
    p.rect(10, 10, W - 10, H - 10, outline=(214, 208, 190), width=1)
    p.text(W / 2, 44, "GRAYHAVEN LIGHT AND POWER COMPANY", SERIF, civic.fit("GRAYHAVEN LIGHT AND POWER COMPANY", SERIF, 26, W - 90),
           (110, 104, 90), anchor="mm")
    p.text(W / 2, 90, "MCMXXVI", SERIF, 26, (110, 104, 90), anchor="mm")
    for sx in (40, W - 40):
        p.poly([(sx + 6, 66), (sx - 8, 92), (sx + 1, 92), (sx - 6, 118), (sx + 12, 86), (sx + 3, 86), (sx + 12, 66)], fill=(130, 124, 108))
    return p


def glp(i):
    t = glp_pic().tile(i, 40210 + i, smooth=0.2, grime=0.3)
    t.streaks(0.9, 0.65)
    return t


@lru_cache(maxsize=None)
def logo_pic():
    p = Pic(2, 2, (0, 0, 0, 0), 40300)
    W, H = p.W, p.H
    p.ellipse(4, 4, W - 4, H - 4, fill=GREEN, outline=CREAM, width=7)
    p.poly([(70, 20), (40, 68), (60, 68), (48, 110), (90, 56), (68, 56), (84, 20)], fill=YELLOW)
    p.text(W / 2, 100, "GL&P", BLACK, 14, CREAM, anchor="mm")
    return p


def logo(i):
    return logo_pic().tile(i, 40310 + i, smooth=0.5, grime=0.2)


@lru_cache(maxsize=None)
def mural_pic():
    """Painted down the boiler house in the fifties: the company's name in cream on its green band."""
    p = Pic(16, 2, (0, 0, 0, 0), 40400)
    W, H = p.W, p.H
    p.rect(0, 8, W, H - 8, fill=GREEN, a=230)
    p.rect(0, 14, W, 17, fill=CREAM, a=220)
    p.rect(0, H - 17, W, H - 14, fill=CREAM, a=220)
    text = "GRAYHAVEN LIGHT & POWER"
    p.text(W / 2 + 40, H / 2 + 1, text, BLACK, civic.fit(text, BLACK, 70, W - 200), CREAM, anchor="mm")
    p.ellipse(20, 20, 108, 108, fill=CREAM, a=230)
    p.poly([(70, 30), (46, 66), (62, 66), (52, 100), (86, 58), (70, 58), (82, 30)], fill=GREEN)
    return p


def mural(i):
    t = mural_pic().tile(i, 40410 + i, smooth=0.1, grime=0.35)
    flakes = t.rng.random((N, N)) < 0.08
    t.alpha[flakes] = 0
    t.streaks(0.9, 0.65)
    return t


def stack_band(seed):
    """The stack's brick with an iron band round it, rust run down from the band."""
    t = P.bricks(seed, (118, 56, 44), MORTAR, soot=0.7)
    area(t, 0, 24, N, 32, col=(46, 40, 36), dh=0.25, smooth=0.3)
    for x in range(4, N, 12):
        disc(t, x, 28, 1.3, col=(70, 60, 50), h=1.0)
    drips(t, 0, N, 32, 10, (4, 20), col=(110, 60, 34))
    return t


def cap(seed):
    t = F.plain(seed, (34, 32, 30), 0.08, 0.2)
    t.speckle(0.2, (20, 18, 16), 0.5)
    t.streaks(1.0, 0.6)
    return t


def soot(seed):
    t = F.plain(seed, (18, 16, 15), 0.1, 0.05)
    t.speckle(0.1, (40, 36, 32), 0.5)
    return t


# ---------------------------------------------------------------- machines

def enamel(seed, rgb, ribs=False, bolts=False):
    """Machinery enamel gone chalky and oily: the turbine green, the crane's yellow."""
    t = F.plain(seed, rgb, 0.05, 0.5)
    t.vary(0.04)
    t.speckle(0.03, mixc(rgb, (30, 30, 30), 0.5), 0.5)
    if ribs:
        for x in (0, 31):
            area(t, x, 0, x + 3, N, mul=0.75, dh=0.2)
    if bolts:
        for x in range(4, N, 8):
            disc(t, x, 4, 1.3, mul=0.7, h=1.0)
            disc(t, x, N - 5, 1.3, mul=0.7, h=1.0)
    t.grime(0.18, (40, 36, 28))
    return t


def tg_plate(seed):
    """The unit's nameplate: brass, black enamel letters, the rating."""
    p = Pic(1, 1, (176, 146, 70), seed)
    p.rect(2, 2, 62, 62, outline=(110, 86, 40), width=1.5)
    p.text(32, 10, "WESTMARK", BLACK, 9, (30, 26, 20), anchor="mm")
    p.text(32, 20, "ELECTRIC", BOLD, 6, (30, 26, 20), anchor="mm")
    p.text(32, 32, "TURBINE-|GENERATOR", BOLD, 5, (30, 26, 20), anchor="mm")
    p.text(32, 50, "60,000 KW|3600 RPM", BOLD, 5, (30, 26, 20), anchor="mm")
    t = p.tile(0, seed + 1, smooth=0.6, grime=0.2)
    t.f0[:] = 200
    return t


def blades(seed):
    """A rotor's row of blades seen edge on: the polished steel, the shroud band at the tips."""
    t = F.plain(seed, (170, 172, 168), 0.05, 0.8)
    t.f0[:] = 200
    for x in range(0, N, 4):
        area(t, x, 0, x + 1, N, mul=0.6, dh=-0.2)
    area(t, 0, 0, N, 5, col=(140, 140, 136), dh=0.2)
    t.speckle(0.04, (120, 80, 50), 0.5)
    return t


def boiler(seed):
    """The boiler's casing: corrugated aluminium lagging gone dull, the sheets lapped, rivets."""
    t = F.plain(seed, (150, 152, 150), 0.05, 0.45)
    t.f0[:] = 200
    for x in range(0, N, 6):
        area(t, x, 0, x + 2, N, mul=0.88, dh=-0.1)
    area(t, 0, 31, N, 33, mul=0.7, dh=0.15)
    for x in range(3, N, 9):
        disc(t, x, 34, 0.9, mul=0.6, h=1.0)
    t.streaks(0.8, 0.7)
    t.grime(0.25, (50, 44, 36))
    return t


def burner(seed, on):
    """A burner front: the register's ring of vanes, the sight glass, the oil gun's pipes; lit, the fire behind."""
    t = enamel(seed, (64, 68, 66))
    disc(t, 32, 30, 22, col=(44, 46, 46), h=0.9)
    for k in range(12):
        a = k * math.pi / 6
        area(t, 32 + math.cos(a) * 14, 30 + math.sin(a) * 14, 32 + math.cos(a) * 14 + 3, 30 + math.sin(a) * 14 + 3, col=(90, 92, 90))
    disc(t, 32, 30, 8, col=(250, 160, 60) if on else (30, 28, 26), h=0.5)
    if on:
        yy, xx = np.mgrid[0:N, 0:N]
        t.emit[(xx - 32) ** 2 + (yy - 30) ** 2 <= 64] = 1.0
    area(t, 6, 56, 58, 60, col=(150, 30, 26))
    area(t, 50, 4, 54, 56, col=(60, 60, 60), dh=0.2)
    return t


def duct(seed):
    t = F.plain(seed, (108, 80, 62), 0.08, 0.2)
    t.speckle(0.1, (140, 76, 40), 0.5)
    for y in (0, 32):
        area(t, 0, y, N, y + 3, mul=0.7, dh=0.25)
    t.streaks(0.9, 0.6)
    return t


def gauge_glass(seed):
    t = F.plain(seed, (60, 62, 60), 0.03, 0.5)
    area(t, 24, 4, 40, 60, col=(200, 210, 210), smooth=0.95, alpha=200)
    area(t, 24, 32, 40, 60, col=(120, 150, 170))
    area(t, 22, 0, 42, 4, col=(150, 120, 60))
    area(t, 22, 60, 42, N, col=(150, 120, 60))
    return t


def belt(seed, coal=False):
    t = F.plain(seed, (34, 34, 34), 0.06, 0.15)
    area(t, 0, 0, N, 4, col=(70, 70, 66))
    area(t, 0, N - 4, N, N, col=(70, 70, 66))
    if coal:
        t.speckle(0.6, (20, 20, 22), 0.8, 0.2)
        t.speckle(0.2, (60, 60, 66), 0.5, 0.3)
    return t


def tank(seed):
    """A fuel oil tank's shell: white paint, the weld seams, rust weeping from the seams."""
    t = F.plain(seed, (214, 212, 204), 0.04, 0.3)
    for y in (0, 31):
        area(t, 0, y, N, y + 1, mul=0.8, dh=0.1)
    area(t, 40, 0, 41, N, mul=0.85, dh=0.1)
    drips(t, 0, N, 1, 8, (6, 30), col=(140, 80, 40))
    drips(t, 0, N, 32, 6, (6, 26), col=(140, 80, 40))
    t.streaks(0.7, 0.75)
    t.grime(0.15)
    return t


@lru_cache(maxsize=None)
def tanklabel_pic():
    p = Pic(2, 2, (0, 0, 0, 0), 40500)
    W, H = p.W, p.H
    p.rect(4, 6, W - 4, H - 6, fill=WHITE, a=235)
    p.text(W / 2, 30, "TANK 2", BLACK, 26, GREEN, anchor="mm")
    p.text(W / 2, 64, "NO. 6 FUEL OIL", BOLD, civic.fit("NO. 6 FUEL OIL", BOLD, 15, W - 20), (20, 20, 20), anchor="mm")
    p.rect(14, 82, W - 14, 108, fill=RED)
    p.text(W / 2, 95, "FLAMMABLE", BLACK, 13, WHITE, anchor="mm")
    return p


def tanklabel(i):
    t = tanklabel_pic().tile(i, 40510 + i, smooth=0.3, grime=0.2)
    t.streaks(0.6, 0.7)
    return t


def xfmr(seed):
    """The transformer's tank: ANSI grey, stiffener ribs, oil weeping down from the gaskets."""
    t = F.plain(seed, (112, 118, 118), 0.04, 0.4)
    for x in (0, 32):
        area(t, x, 0, x + 4, N, mul=0.82, dh=0.2)
    drips(t, 0, N, 2, 4, (8, 30), col=(70, 60, 40))
    t.grime(0.15)
    return t


def fins(seed):
    t = F.plain(seed, (112, 118, 118), 0.04, 0.4)
    for x in range(0, N, 4):
        area(t, x, 0, x + 2, N, mul=0.6, dh=-0.3)
    t.grime(0.2)
    return t


def porcelain(seed):
    t = F.plain(seed, (120, 66, 40), 0.04, 0.85)
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 2, mul=1.25)
    t.grime(0.1)
    return t


# ---------------------------------------------------------------- the control room

def bench_top(seed, on):
    """The benchboard's sloped face: the mimic bus in red and green tape, the control switches with
    their red and green lamps, the pistol-grip handles."""
    t = B.panel(seed, (176, 182, 172))
    rng = t.rng
    area(t, 0, 10, N, 13, col=(170, 40, 30))
    for x in (10, 30, 50):
        area(t, x, 13, x + 2, 34, col=(40, 120, 60))
    for x in (6, 20, 34, 48):
        disc(t, x + 4, 44, 4, col=(40, 40, 40), h=0.9)
        area(t, x + 3, 38, x + 5, 50, col=(30, 30, 30))
        B.lamp(t, x + 1, 54, (220, 40, 30), on and rng.random() < 0.5)
        B.lamp(t, x + 7, 54, (40, 200, 70), on and rng.random() < 0.5)
    for x in (4, 22, 40):
        B.meter(t, x, 18, 14, 10, on)
    t.grime(0.1)
    return t


def bench_front(seed):
    t = B.panel(seed, (176, 182, 172))
    area(t, 0, 0, N, 3, col=(60, 62, 60))
    F.gap(t, 4, 8, 60, 58)
    disc(t, 54, 32, 1.5, col=(150, 150, 150), h=0.9)
    t.grime(0.15)
    return t


def vboard(seed, top, on):
    """The vertical board: up top the round meters and the chart recorders, below the switch rows
    and the indicating lamps."""
    t = B.panel(seed, (176, 182, 172))
    rng = t.rng
    if top:
        for x in (4, 24, 44):
            disc(t, x + 8, 14, 8, col=(232, 222, 190) if on else (170, 164, 140), h=0.9)
            area(t, x + 8, 7, x + 9, 15, col=(20, 20, 20))
            if on:
                yy, xx = np.mgrid[0:N, 0:N]
                t.emit[(xx - x - 8) ** 2 + (yy - 14) ** 2 <= 60] = 0.5
        for x in (6, 34):
            area(t, x, 30, x + 24, 56, col=(40, 40, 40))
            area(t, x + 2, 32, x + 22, 54, col=(236, 232, 214) if on else (200, 196, 180), smooth=0.8)
            for k in range(6):
                area(t, x + 2, 34 + k * 3, x + 22, 34 + k * 3 + 1, col=(150, 180, 170))
            pts = [(x + 2 + j, 44 + 5 * math.sin(j / 3 + seed)) for j in range(20)]
            for px, py in pts:
                area(t, px, py, px + 1, py + 1, col=(170, 30, 30))
            if on:
                t.emit[32:54, x + 2:x + 22] = 0.4
    else:
        for row in (8, 30):
            for x in range(6, 60, 12):
                disc(t, x, row + 8, 3.5, col=(40, 40, 40), h=0.9)
                area(t, x - 1, row + 2, x + 1, row + 14, col=(26, 26, 26))
                B.lamp(t, x - 3, row + 18, (220, 40, 30), on and rng.random() < 0.4)
                B.lamp(t, x + 3, row + 18, (40, 200, 70), on and rng.random() < 0.6)
        area(t, 0, 54, N, N, col=(60, 62, 60))
    t.grime(0.12)
    return t


def annun(seed, on):
    """The annunciator: a grid of alarm windows, white with black lettering; on, some flashing amber and red."""
    t = B.panel(seed, (60, 62, 60))
    rng = t.rng
    for j in range(4):
        for k in range(4):
            x, y = 3 + k * 15, 3 + j * 15
            lit = on and rng.random() < 0.3
            col = (250, 200, 80) if lit and rng.random() < 0.6 else (240, 80, 60) if lit else (210, 208, 196)
            area(t, x, y, x + 13, y + 13, col=col, smooth=0.8)
            area(t, x + 3, y + 5, x + 10, y + 6, col=(30, 30, 30))
            area(t, x + 3, y + 8, x + 9, y + 9, col=(30, 30, 30))
            if lit:
                t.emit[y:y + 13, x:x + 13] = 0.9
    return t


@lru_cache(maxsize=None)
def mimic_pic():
    """The station's one-line diagram across the wall: the three units, the buses, the feeders out
    to the city; marked up in grease pencil in the last weeks."""
    p = Pic(6, 2, (176, 182, 172), 40600)
    W, H = p.W, p.H
    p.rect(0, 0, W, 14, fill=GREEN)
    p.text(W / 2, 7, "HARBOR POINT STATION - 13.8 KV / 69 KV ONE LINE", BOLD, 7, CREAM, anchor="mm")
    p.line([(20, 70), (W - 20, 70)], (170, 40, 30), 3)
    p.line([(20, 34), (W - 20, 34)], (40, 70, 160), 3)
    for k, x in enumerate((70, 190, 310)):
        p.ellipse(x - 14, 92, x + 14, 120, outline=(30, 30, 30), width=2)
        p.text(x, 106, "G" + str(k + 1), BOLD, 9, (30, 30, 30), anchor="mm")
        p.line([(x, 92), (x, 70)], (30, 30, 30), 2)
        p.rect(x - 4, 76, x + 4, 84, fill=(40, 160, 70) if k == 2 else (190, 40, 30))
        p.line([(x, 70), (x, 34)], (30, 30, 30), 2)
        p.ellipse(x - 7, 46, x + 7, 60, outline=(30, 30, 30), width=1.5)
    feeders = ["MERCY GEN", "DOWNTOWN", "SHELTERS", "PUMP STA", "UNION STA", "EASTSIDE"]
    for k, f in enumerate(feeders):
        x = 30 + k * 64
        p.line([(x, 34), (x, 20)], (30, 30, 30), 2)
        p.text(x, 26, f, BOLD, 4, (30, 30, 30), anchor="lm")
        if f not in ("MERCY GEN", "SHELTERS"):
            p.scrawl(x - 8, 14, "X", HAND, 12, (190, 20, 20), 0.2)
    p.scrawl(90, 92, "U3 ONLY|KEEP MERCY ON", HAND, 9, (190, 20, 20), 0.3)
    return p


def mimic(i):
    return mimic_pic().tile(i, 40610 + i, smooth=0.4, grime=0.15)


@lru_cache(maxsize=None)
def freq_pic():
    p = Pic(2, 1, (30, 30, 30), 40700)
    W, H = p.W, p.H
    p.rect(4, 8, W - 4, H - 8, fill=(16, 18, 16))
    p.text(W / 2, H / 2 - 4, "59.80", STENCIL, 26, (240, 90, 40), anchor="mm")
    p.text(W / 2, H - 16, "SYSTEM FREQUENCY  HZ", BOLD, 6, (200, 200, 190), anchor="mm")
    return p


def freq(i):
    t = freq_pic().tile(i, 40710 + i, smooth=0.5, grime=0.05)
    m = (t.c[..., 0] > 200) & (t.c[..., 1] < 140)
    t.emit[m] = 0.8
    return t


@lru_cache(maxsize=None)
def safety_pic():
    """The safety board by the time clock: the days without an accident, the last number crossed out."""
    p = Pic(3, 2, (236, 232, 216), 40800)
    W, H = p.W, p.H
    p.rect(0, 0, W, 26, fill=GREEN)
    p.text(W / 2, 13, "SAFETY FIRST", BLACK, 16, CREAM, anchor="mm")
    p.text(W / 2, 40, "THIS PLANT HAS WORKED", BOLD, 9, (30, 30, 30), anchor="mm")
    p.rect(W / 2 - 34, 50, W / 2 + 34, 90, fill=(20, 20, 20))
    p.text(W / 2, 70, "412", STENCIL, 30, (240, 240, 230), anchor="mm")
    p.text(W / 2, 104, "DAYS WITHOUT A LOST-TIME ACCIDENT", BOLD, civic.fit("DAYS WITHOUT A LOST-TIME ACCIDENT", BOLD, 8, W - 20),
           (30, 30, 30), anchor="mm")
    p.line([(W / 2 - 36, 54), (W / 2 + 36, 88)], (200, 20, 20), 3)
    p.scrawl(W / 2 + 40, 56, "0", HAND, 26, (200, 20, 20), 0.2)
    p.rect(2, 2, W - 2, H - 2, outline=(110, 90, 50), width=3)
    return p


def safety(i):
    return safety_pic().tile(i, 40810 + i, smooth=0.3, grime=0.2)


@lru_cache(maxsize=None)
def tons_pic():
    p = Pic(2, 1, (0, 0, 0, 0), 40900)
    W, H = p.W, p.H
    p.rect(2, 12, W - 2, H - 12, fill=YELLOW)
    p.rect(2, 12, W - 2, 16, fill=(20, 20, 20))
    p.rect(2, H - 16, W - 2, H - 12, fill=(20, 20, 20))
    p.text(W / 2, H / 2, "50 TONS", BLACK, 20, (20, 20, 20), anchor="mm")
    return p


def tons(i):
    return tons_pic().tile(i, 40910 + i, smooth=0.4, grime=0.2)


@lru_cache(maxsize=None)
def banner_pic():
    p = Pic(6, 1, (232, 228, 214), 41000)
    W, H = p.W, p.H
    p.scrawl(14, 6, "POWER CREW INSIDE", SPRAY, civic.fit("POWER CREW INSIDE", SPRAY, 34, W - 40), (200, 24, 24), 0.25)
    p.scrawl(40, 40, "DONT SHOOT - KNOCK 3X", SPRAY, civic.fit("DONT SHOOT - KNOCK 3X", SPRAY, 18, W - 90), (20, 20, 20), 0.3)
    return p


def banner(i):
    t = banner_pic().tile(i, 41010 + i, smooth=0.1, grime=0.3)
    t.c *= (0.9 + 0.1 * np.cos(np.arange(N)[None, :] / N * 2 * np.pi * 2))[..., None]
    if i == 0:
        yy = np.arange(N)[:, None]
        t.alpha[(yy > 46 + 5 * np.sin(np.arange(N)[None, :] / 4)) & (np.arange(N)[None, :] < 30)] = 0
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    text, field, ink = SIGNS[key]
    p = Pic(2, 1, (0, 0, 0, 0), 41100 + len(key) * 3)
    W, H = p.W, p.H
    p.rect(1, 16, W - 1, H - 16, fill=field)
    if field in (YELLOW, RED):
        p.text(W / 2, H / 2, text, STENCIL, civic.fit(text, STENCIL, 15, W - 14), ink, anchor="mm")
        if key == "danger":
            p.poly([(10, 40), (18, 26), (26, 40)], fill=(16, 16, 16))
    else:
        p.rect(1, H - 19, W - 1, H - 16, fill=CREAM)
        p.text(W / 2, H / 2 - 1, text, BOLD, civic.fit(text, BOLD, 14, W - 16), ink, anchor="mm")
    return p


def sign(key, i):
    return sign_pic(key).tile(i, 41150 + i + len(key) * 11, smooth=0.5, grime=0.2)


@lru_cache(maxsize=None)
def spray_pic(key):
    text, cols = SPRAYS[key]
    p = Pic(cols, 1, (0, 0, 0, 0), 41200 + len(key))
    size = civic.fit(text, SPRAY, 28, p.W - 10)
    p.scrawl(6, 18, text, SPRAY, size, (210, 24, 30) if key != "coal" else (30, 30, 30), jitter=0.4, a=235, angle=-2)
    return p


def spray(key, i):
    t = spray_pic(key).tile(i, 41210 + i + len(key) * 5, smooth=0.3, grime=0.0)
    lum = t.alpha > 20
    for _ in range(4):
        x, y = int(t.rng.integers(4, 60)), int(t.rng.integers(30, 50))
        if lum[y, x]:
            drips(t, x, x + 1, y, 1, (4, 12), col=(180, 20, 20) if key != "coal" else (30, 30, 30))
            t.alpha[y:y + 12, x:x + 1] = 230
    return t


# ---------------------------------------------------------------- shop, offices, floors

def quarry_tile(seed, line=False):
    """The turbine hall's floor: red quarry tile, six inches square, oil soaked in by the units."""
    t = F.plain(seed, (132, 64, 46), 0.06, 0.35)
    rng = t.rng
    for k in range(0, N, 8):
        area(t, k, 0, k + 1, N, col=(90, 80, 70), dh=-0.2)
        area(t, 0, k, N, k + 1, col=(90, 80, 70), dh=-0.2)
    for x in range(0, N, 8):
        for y in range(0, N, 8):
            area(t, x + 1, y + 1, x + 8, y + 8, mul=rng.uniform(0.86, 1.1))
    if line:
        area(t, 0, 26, N, 38, col=YELLOW, smooth=0.3)
        t.speckle(0.08, (132, 64, 46), 0.6)
    t.grime(0.25, (30, 26, 22))
    return t


def parts(seed):
    """Stores shelving: bins of valves and fittings, a gasket on a hook, the bin labels."""
    t = F.plain(seed, (90, 96, 92), 0.04, 0.4)
    rng = t.rng
    for row in (2, 22, 42):
        area(t, 0, row + 16, N, row + 19, col=(70, 76, 72), dh=0.2)
        for x in range(2, 60, 15):
            area(t, x, row + 4, x + 13, row + 16, col=(60, 62, 60))
            area(t, x + 3, row + 6, x + 10, row + 9, col=(232, 228, 210))
            for _ in range(3):
                disc(t, x + rng.uniform(3, 10), row + rng.uniform(11, 14), 1.8,
                     col=(176, 146, 70) if rng.random() < 0.5 else (140, 142, 140), h=0.9)
    t.grime(0.15)
    return t


def hardhats(seed):
    t = F.plain(seed, (150, 120, 80), 0.04, 0.2)
    for x, col in ((8, (240, 240, 236)), (24, YELLOW), (40, (240, 240, 236)), (56, (220, 110, 30))):
        area(t, x - 1, 6, x + 1, 14, col=(60, 60, 60))
        disc(t, x, 22, 7, col=col, h=0.9)
        area(t, x - 9, 22, x + 9, 25, col=mixc(col, (0, 0, 0), 0.2))
    area(t, 0, 36, N, 38, col=(110, 80, 50))
    t.grime(0.15)
    return t


def timeclock(seed):
    t = F.plain(seed, (130, 120, 100), 0.04, 0.2)
    area(t, 22, 6, 42, 34, col=(70, 72, 70))
    disc(t, 32, 16, 7, col=(236, 232, 214), h=0.9)
    area(t, 32, 11, 33, 17, col=(20, 20, 20))
    area(t, 28, 26, 36, 29, col=(30, 30, 30))
    for x in (2, 46):
        for y in range(4, 60, 5):
            area(t, x, y, x + 16, y + 4, col=(220, 214, 190))
            area(t, x + 2, y, x + 14, y + 1, col=(200, 60, 50) if (x + y) % 3 == 0 else (60, 60, 60))
    t.grime(0.1)
    return t


def machine(seed, rgb=(92, 104, 96)):
    t = enamel(seed, rgb)
    t.speckle(0.04, (40, 30, 20), 0.6)
    return t


def lab_top(seed):
    t = F.plain(seed, (30, 32, 32), 0.03, 0.7)
    for x, col in ((10, (200, 220, 220)), (22, (180, 200, 120)), (40, (200, 220, 220)), (52, (220, 150, 90))):
        disc(t, x, 30, 4, col=col, h=0.9, smooth=0.9)
    area(t, 4, 44, 60, 58, col=(220, 216, 200))
    return t


def reel(seed):
    t = F.wood(seed, "pine")
    disc(t, 32, 32, 8, col=(60, 50, 40), h=0.4)
    for r in (16, 26):
        disc(t, 32, 32, r, ring=1, mul=0.7)
    return t


def cable(seed):
    t = F.plain(seed, (30, 30, 32), 0.05, 0.3)
    for y in range(0, N, 5):
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.2)
    return t


def dozer(seed):
    t = enamel(seed, (214, 160, 30))
    t.speckle(0.1, (120, 70, 30), 0.5)
    t.grime(0.35, (50, 40, 26))
    return t


def track(seed):
    t = F.plain(seed, (52, 48, 44), 0.08, 0.3)
    for x in range(0, N, 8):
        area(t, x, 0, x + 2, N, mul=0.6, dh=0.3)
    t.speckle(0.1, (120, 80, 50), 0.5)
    return t


def model_case(seed):
    """The model of the station in its case in the lobby, seen from above: the turbine hall's
    roof, the boiler house, the four stacks."""
    t = F.plain(seed, (90, 110, 80), 0.04, 0.6)
    area(t, 6, 34, 58, 54, col=(150, 80, 60))
    area(t, 6, 12, 58, 34, col=(130, 70, 54))
    for x in (12, 26, 40, 54):
        disc(t, x - 2, 22, 3.5, col=(60, 40, 34), h=0.9)
    area(t, 0, 0, N, 2, col=(140, 110, 60))
    return t


def textures():
    T = {}
    T["pw_sash"] = lambda: sash(41500)
    T["pw_sash_broken"] = lambda: sash(41501, broken=True)
    T["pw_sash_lit"] = lambda: sash(41502, lit=True)
    for i in range(10):
        T[f"pw_arch_{i}"] = lambda i=i: arch(i, "mask")
        T[f"pw_archg_{i}"] = lambda i=i: arch(i, "glass")
    for i in range(20):
        T[f"pw_name_{i}"] = lambda i=i: name(i)
    for i in range(16):
        T[f"pw_glp_{i}"] = lambda i=i: glp(i)
    for i in range(4):
        T[f"pw_logo_{i}"] = lambda i=i: logo(i)
        T[f"pw_tanklabel_{i}"] = lambda i=i: tanklabel(i)
    for i in range(32):
        T[f"pw_mural_{i}"] = lambda i=i: mural(i)
    T["pw_stack_band"] = lambda: stack_band(41510)
    T["pw_cap"] = lambda: cap(41511)
    T["pw_soot"] = lambda: soot(41512)
    T["pw_tg"] = lambda: enamel(41513, MACHINE)
    T["pw_tg_flange"] = lambda: enamel(41514, mixc(MACHINE, (0, 0, 0), 0.15), bolts=True)
    T["pw_tg_plate"] = lambda: tg_plate(41515)
    T["pw_gen"] = lambda: enamel(41516, (190, 186, 164), ribs=True)
    T["pw_blades"] = lambda: blades(41517)
    T["pw_crane"] = lambda: enamel(41518, (214, 168, 34), ribs=True)
    T["pw_boiler"] = lambda: boiler(41519)
    T["pw_burner"] = lambda: burner(41520, False)
    T["pw_burner_on"] = lambda: burner(41521, True)
    T["pw_drum"] = lambda: enamel(41522, (88, 96, 94), bolts=True)
    T["pw_duct"] = lambda: duct(41523)
    T["pw_gauge_glass"] = lambda: gauge_glass(41524)
    T["pw_belt"] = lambda: belt(41525)
    T["pw_belt_coal"] = lambda: belt(41526, coal=True)
    T["pw_tank"] = lambda: tank(41527)
    T["pw_xfmr"] = lambda: xfmr(41528)
    T["pw_fins"] = lambda: fins(41529)
    T["pw_porcelain"] = lambda: porcelain(41530)
    T["pw_bench_top"] = lambda: bench_top(41531, False)
    T["pw_bench_top_on"] = lambda: bench_top(41532, True)
    T["pw_bench_front"] = lambda: bench_front(41533)
    T["pw_vb_lo"] = lambda: vboard(41534, False, False)
    T["pw_vb_lo_on"] = lambda: vboard(41535, False, True)
    T["pw_vb_hi"] = lambda: vboard(41536, True, False)
    T["pw_vb_hi_on"] = lambda: vboard(41537, True, True)
    T["pw_annun"] = lambda: annun(41538, False)
    T["pw_annun_on"] = lambda: annun(41539, True)
    T["pw_panel"] = lambda: B.panel(41540, (176, 182, 172))
    T["pw_tile"] = lambda: quarry_tile(41541)
    T["pw_tile_line"] = lambda: quarry_tile(41542, line=True)
    T["pw_parts"] = lambda: parts(41543)
    T["pw_hardhats"] = lambda: hardhats(41544)
    T["pw_timeclock"] = lambda: timeclock(41545)
    T["pw_machine"] = lambda: machine(41546)
    T["pw_lab_top"] = lambda: lab_top(41547)
    T["pw_reel"] = lambda: reel(41548)
    T["pw_cable"] = lambda: cable(41549)
    T["pw_dozer"] = lambda: dozer(41550)
    T["pw_track"] = lambda: track(41551)
    T["pw_model"] = lambda: model_case(41552)
    T["pw_shaft"] = lambda: enamel(41553, (176, 178, 176))
    for i in range(12):
        T[f"pw_mimic_{i}"] = lambda i=i: mimic(i)
    for i in range(2):
        T[f"pw_freq_{i}"] = lambda i=i: freq(i)
        T[f"pw_tons_{i}"] = lambda i=i: tons(i)
    for i in range(6):
        T[f"pw_safety_{i}"] = lambda i=i: safety(i)
        T[f"pw_banner_{i}"] = lambda i=i: banner(i)
    for key in SIGNS:
        for i in range(2):
            T[f"pw_sgn_{key}_{i}"] = lambda key=key, i=i: sign(key, i)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            T[f"pw_spray_{key}_{i}"] = lambda key=key, i=i: spray(key, i)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis" and P.drawn_along(boxes) == "x":
            boxes = P.along_z(boxes)  # RULE: axis models run along z as drawn (the rotor's discs fool the test)
            if "shape" in extra:
                extra["shape"] = [[b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]] for b in extra["shape"]]
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

    black, chrome, grey, galv = "home_plastic_black", "home_chrome", "bunk_grey", "tv_galv"
    brick, steel = "brick_red", "painted_steel"

    def face(front, side, up=None):
        return {"north": front, "side": side, "up": up or side, "down": side}

    def plate(tex, z0=15.2):
        return [[0, 0, z0, 16, 16, 16, {"north": tex, "south": tex, "side": tex, "up": tex, "down": tex}]]

    # ---- the walls: the sash set back in the wall's middle, the arch's brick round its light
    for n, tex, light in (("pw_sash", "pw_sash", None), ("pw_sash_broken", "pw_sash_broken", None), ("pw_sash_lit", "pw_sash_lit", 5)):
        add(n, [[0, 0, 7, 16, 16, 9, {"north": tex, "south": tex, "side": "pw_tg", "up": "pw_tg", "down": "pw_tg"}]],
            "Steel Window", "GLASS", render="translucent", shape=[[0, 0, 7, 16, 16, 9]], **({"light": light} if light else {}))
    for i in range(10):
        m, g = f"pw_arch_{i}", f"pw_archg_{i}"
        add(m, [[0, 0, 0, 16, 16, 7, {"north": m, "south": m, "side": brick, "up": brick, "down": brick}],
                [0, 0, 7, 16, 16, 9, {"north": g, "south": g, "side": "pw_tg", "up": "pw_tg", "down": "pw_tg"}],
                [0, 0, 9, 16, 16, 16, {"north": m, "south": m, "side": brick, "up": brick, "down": brick}]],
            "Arched Window", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])
    for i in range(20):
        add(f"pw_name_{i}", plate(f"pw_name_{i}", 14.4), "Cast Stone Letters", "STONE", decor=True)
    for i in range(16):
        add(f"pw_glp_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"pw_glp_{i}", "south": "stone_trim", "side": "stone_trim",
                                                   "up": "stone_trim", "down": "stone_trim"}]], "Carved Stone", "STONE")
    for i in range(4):
        add(f"pw_logo_{i}", plate(f"pw_logo_{i}", 14.8), "Company Seal", "METAL", decor=True)
        add(f"pw_tanklabel_{i}", plate(f"pw_tanklabel_{i}", 15.6), "Tank Label", "METAL", decor=True)
    for i in range(32):
        add(f"pw_mural_{i}", plate(f"pw_mural_{i}", 15.3), "Painted Sign", "STONE", decor=True)
    add("pw_stack_band", None, "Stack Brick", "STONE", rotate=None, tex={"side": "pw_stack_band", "up": "brick_red_soot", "down": "brick_red_soot"})
    add("pw_cap", None, "Stack Cap", "METAL", rotate=None, tex={"all": "pw_cap"})
    add("pw_soot", None, "Soot", "STONE", rotate=None, tex={"all": "pw_soot"})

    # ---- the turbine-generators: long along x, so their rounded edges face north and south
    add("pw_tg", None, "Turbine Casing", "METAL", rotate=None, tex={"side": "pw_tg", "up": "pw_tg", "down": "pw_tg"})
    add("pw_tg_joint", [[0, 0, 0, 16, 16, 16, "pw_tg"], [-0.6, 13, -0.6, 16.6, 16, 16.6, "pw_tg_flange"]], "Turbine Casing", "METAL",
        rotate=None, shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_tg_round", [[0, 0, 0, 16, 9, 16, "pw_tg"], [0, 9, 2, 16, 12, 16, "pw_tg"], [0, 12, 5, 16, 14, 16, "pw_tg"],
                        [0, 14, 9, 16, 16, 16, "pw_tg"]], "Turbine Casing", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_tg_plate", plate("pw_tg_plate", 15.4), "Nameplate", "METAL", decor=True)
    add("pw_gen", None, "Generator", "METAL", rotate=None, tex={"side": "pw_gen", "up": "pw_gen", "down": "pw_gen"})
    add("pw_gen_round", [[0, 0, 0, 16, 9, 16, "pw_gen"], [0, 9, 2, 16, 12, 16, "pw_gen"], [0, 12, 5, 16, 14, 16, "pw_gen"],
                         [0, 14, 9, 16, 16, 16, "pw_gen"]], "Generator", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_exciter", [[2, 0, 2, 14, 2, 14, "pw_tg"], [3, 2, 1, 13, 13, 15, {"all": "pw_gen", "north": "fit_louvre"}],
                       [6, 13, 6, 10, 15, 10, black]], "Exciter", "METAL", shape=[[2, 0, 1, 14, 15, 15]])
    # the rotor lifted out of unit 2: the shaft along z, the rows of blades round it two blocks across
    rot = []
    for z0, r in ((1, 13), (4, 11), (7, 11), (10, 9), (13, 9)):
        # each row in two halves so the axis rule reads the shaft, not the blades, as the run
        for a, b in ((8 - r, 8), (8, 8 + r)):
            rot.append([a, 8 - r, z0, b, 8 + r, z0 + 2, {"north": "pw_blades", "south": "pw_blades", "side": "pw_blades",
                                                         "up": "pw_blades", "down": "pw_blades"}])
    rot.append([6, 6, 0, 10, 10, 16, "pw_shaft"])
    add("pw_rotor", rot, "Turbine Rotor", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_pedestal", [[0, 0, 0, 16, 12, 16, "floor_concrete"], [1, 12, 1, 15, 13, 15, steel], [5, 13, 4, 11, 16, 12, "pw_tg"]],
        "Bearing Pedestal", "STONE", rotate=None, shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_gauge", [[6, 0, 9, 10, 9, 12, steel], [1, 9, 10, 15, 16, 13, face("fit_gauges", steel)]], "Gauge Board", "METAL",
        shape=[[1, 0, 9, 15, 16, 13]])

    # ---- the crane
    add("pw_crane", [[0, 0, 2, 16, 16, 14, {"side": "pw_crane", "up": "pw_crane", "down": "pw_crane"}],
                     [0, 15, 0, 16, 16, 16, "pw_crane"], [0, 0, 0, 16, 1, 16, "pw_crane"]], "Crane Girder", "METAL",
        rotate="axis", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_crane_end", [[0, 4, 0, 16, 16, 16, "pw_crane"], [2, 0, 3, 6, 4, 13, black], [10, 0, 3, 14, 4, 13, black]],
        "Crane End Truck", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    # the runway rail on its corbel: the rail along the top, the bracket into the wall behind
    add("pw_crane_rail", [[0, 13, 6, 16, 16, 10, steel], [0, 16, 7, 16, 17, 9, chrome], [0, 9, 4, 16, 13, 16, steel]],
        "Crane Runway", "METAL", rotate="facing", shape=[[0, 9, 4, 16, 16, 16]])
    add("pw_trolley", [[0, 0, 0, 16, 10, 16, "pw_crane"], [2, 10, 3, 14, 15, 13, {"all": grey, "north": "fit_louvre"}],
                       [4, 0, -2, 12, 8, 0, "pw_crane"]], "Crane Trolley", "METAL", shape=[[0, 0, 0, 16, 15, 16]])
    add("pw_rope", [[5, 0, 5, 6, 16, 6, chrome], [10, 0, 5, 11, 16, 6, chrome], [5, 0, 10, 6, 16, 11, chrome], [10, 0, 10, 11, 16, 11, chrome]],
        "Hoist Rope", "METAL", rotate=None, render="cutout", decor=True, shape=[[5, 0, 5, 11, 16, 11]])
    add("pw_hook", [[3, 9, 3, 13, 16, 13, "pw_crane"], [4, 10, 2.5, 12, 15, 3, "pw_tons_0"], [7, 4, 7, 9, 9, 9, steel],
                    [5, 0, 7, 7, 5, 9, steel], [9, 0, 7, 11, 4, 9, steel], [5, 0, 7, 11, 1, 9, steel]], "Crane Hook", "METAL",
        rotate=None, shape=[[3, 0, 3, 13, 16, 13]])
    add("pw_crane_cab", [[1, 0, 1, 15, 14, 15, {"all": "pw_crane", "north": "pw_sash", "east": "pw_sash", "west": "pw_sash"}],
                         [0, 14, 0, 16, 16, 16, "pw_crane"]], "Crane Cab", "METAL", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])
    for i in range(2):
        add(f"pw_tons_{i}", plate(f"pw_tons_{i}", 15.5), "Crane Sign", "METAL", decor=True)

    # ---- the boilers
    add("pw_boiler", [[0, 0, 0, 16, 16, 16, {"side": "pw_boiler", "up": "pw_boiler", "down": "pw_boiler"}],
                      [6, 0, -1, 10, 16, 0, steel]], "Boiler Casing", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    for suffix, light in (("", None), ("_on", 10)):
        add(f"pw_burner{suffix}", [[0, 0, 0, 16, 16, 16, {"north": f"pw_burner{suffix}", "side": "pw_boiler", "up": "pw_boiler", "down": "pw_boiler"}],
                                   [12, 2, -3, 14, 4, 0, "bunk_pipe_red"], [3, 12, -2, 5, 16, 0, "bunk_pipe_grey"]],
            "Burner", "METAL", shape=[[0, 0, 0, 16, 16, 16]], **({"light": light} if light else {}))
    add("pw_drum", [[0, 3, 1, 16, 13, 15, "pw_drum"], [0, 1, 3, 16, 15, 13, "pw_drum"], [7, 15, 7, 9, 16, 9, steel]], "Steam Drum", "METAL",
        rotate="axis", shape=[[0, 1, 1, 16, 15, 15]])
    add("pw_drum_end", [[1, 3, 4, 15, 13, 16, "pw_drum"], [3, 1, 4, 13, 15, 16, "pw_drum"], [4, 4, 2, 12, 12, 4, "pw_drum"],
                        [5, 5, 1, 11, 11, 2, steel]], "Steam Drum", "METAL", shape=[[1, 1, 1, 15, 15, 16]])
    add("pw_gauge_glass", [[5, 0, 12, 11, 16, 16, face("pw_gauge_glass", steel)]], "Water Gauge", "GLASS", decor=True, render="translucent")
    add("pw_steam", [[2, 2, 0, 14, 14, 16, "fit_lagging"], [1.5, 1.5, 0, 14.5, 14.5, 1, steel], [1.5, 1.5, 15, 14.5, 14.5, 16, steel]],
        "Steam Main", "METAL", rotate="axis", shape=[[2, 2, 0, 14, 14, 16]])
    add("pw_steam_v", [[2, 0, 2, 14, 16, 14, "fit_lagging"], [1.5, 0, 1.5, 14.5, 1, 14.5, steel]], "Steam Main", "METAL", rotate=None)
    add("pw_duct", None, "Flue Duct", "METAL", rotate=None, tex={"all": "pw_duct"})
    add("pw_fan", [[0, 0, 0, 16, 3, 16, steel], [1, 3, 2, 15, 16, 14, {"all": "pw_drum", "north": "fit_louvre"}], [4, 6, 14, 12, 14, 16, "pw_drum"]],
        "Forced Draft Fan", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_mill", [[0, 0, 0, 16, 2, 16, steel], [2, 2, 2, 14, 14, 14, {"all": "pw_tg", "up": "pw_drum"}], [6, 14, 6, 10, 16, 10, "pw_duct"],
                    [5, 4, 0, 11, 10, 2, "pw_drum"]], "Coal Pulverizer", "METAL", rotate=None)
    add("pw_chute", [[3, 0, 3, 13, 16, 13, "pw_duct"]], "Coal Chute", "METAL", rotate=None)
    add("pw_safety_valve", [[5, 0, 5, 11, 6, 11, steel], [6, 6, 6, 10, 14, 10, "pw_drum"], [7, 14, 3, 9, 15, 13, steel], [4, 2, 6, 5, 9, 10, steel]],
        "Safety Valve", "METAL", rotate=None, shape=[[4, 0, 3, 12, 15, 13]])

    # ---- coal, oil and the dozer
    add("pw_belt", [[0, 5, 1, 16, 6, 15, {"up": "pw_belt", "side": black, "down": black}], [0, 3, 0, 16, 5, 1, steel], [0, 3, 15, 16, 5, 16, steel],
                    [6, 3.5, 1, 10, 5, 15, chrome], [1, 0, 0, 2, 3, 1, steel], [1, 0, 15, 2, 3, 16, steel]],
        "Conveyor", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 6, 16]])
    add("pw_belt_coal", [[0, 5, 1, 16, 6, 15, {"up": "pw_belt_coal", "side": black, "down": black}], [0, 3, 0, 16, 5, 1, steel],
                         [0, 3, 15, 16, 5, 16, steel], [0, 6, 4, 16, 8, 12, "gl_coal_top"], [1, 0, 0, 2, 3, 1, steel], [1, 0, 15, 2, 3, 16, steel]],
        "Conveyor", "METAL", rotate="axis", shape=[[0, 0, 0, 16, 8, 16]])
    add("pw_coal_slab", [[0, 0, 0, 16, 8, 16, {"up": "gl_coal_top", "side": "gl_coal", "down": "gl_coal"}]], "Coal", "GRAVEL", rotate=None,
        shape=[[0, 0, 0, 16, 8, 16]])
    add("pw_crusher", [[0, 0, 0, 16, 10, 16, {"all": "pw_tg", "north": "pw_machine"}], [2, 10, 2, 14, 16, 14, "pw_duct"],
                       [5, 3, -2, 11, 9, 0, "pw_drum"]], "Coal Crusher", "METAL")
    add("pw_hopper", [[0, 13, 0, 16, 16, 16, "grate"], [0, 0, 0, 2, 13, 16, "pw_duct"], [14, 0, 0, 16, 13, 16, "pw_duct"],
                      [2, 0, 0, 14, 13, 2, "pw_duct"], [2, 0, 14, 14, 13, 16, "pw_duct"]], "Track Hopper", "METAL", rotate=None,
        render="cutout")
    add("pw_elev", [[1, 0, 1, 15, 16, 15, {"all": "pw_duct", "north": "pw_machine"}], [4, 6, 0.5, 12, 12, 1, steel]], "Bucket Elevator", "METAL")
    add("pw_tank", None, "Oil Tank", "METAL", rotate=None, tex={"side": "pw_tank", "up": "pw_tank", "down": "pw_tank"})
    add("pw_tank_roof", [[0, 0, 0, 16, 8, 16, {"all": "pw_tank", "up": "corrugated_grey"}]], "Tank Roof", "METAL", rotate=None,
        shape=[[0, 0, 0, 16, 8, 16]])
    add("pw_trestle", [[1, 0, 1, 3, 16, 3, galv], [13, 0, 1, 15, 16, 3, galv], [1, 0, 13, 3, 16, 15, galv], [13, 0, 13, 15, 16, 15, galv],
                       [3, 7, 1.5, 13, 9, 2.5, galv], [3, 7, 13.5, 13, 9, 14.5, galv], [1.5, 7, 3, 2.5, 9, 13, galv],
                       [13.5, 7, 3, 14.5, 9, 13, galv]], "Trestle", "METAL", rotate=None, render="cutout", shape=[[1, 0, 1, 15, 16, 15]])
    add("pw_dozer_blade", [[0, 0, 2, 16, 12, 6, {"all": "pw_dozer", "north": steel}], [0, 0, 1, 16, 2, 3, steel], [6, 4, 6, 10, 8, 16, "pw_dozer"]],
        "Bulldozer", "METAL", shape=[[0, 0, 1, 16, 12, 16]])
    add("pw_dozer_body", [[0, 4, 0, 16, 14, 16, {"all": "pw_dozer", "north": "fit_louvre"}], [6, 14, 4, 9, 20, 7, black]], "Bulldozer", "METAL",
        shape=[[0, 4, 0, 16, 16, 16]])
    add("pw_dozer_cab", [[1, 0, 1, 15, 14, 15, {"all": "pw_dozer", "north": "pw_sash_broken", "east": "pw_sash_broken", "west": "pw_sash_broken",
                                                "south": "pw_sash_broken"}], [0, 14, 0, 16, 16, 16, "pw_dozer"]],
        "Bulldozer", "METAL", render="translucent", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_track", [[0, 0, 0, 16, 7, 16, {"side": "pw_track", "up": "pw_track", "down": "pw_track"}]], "Bulldozer Track", "METAL",
        rotate="axis", shape=[[0, 0, 0, 16, 7, 16]])

    # ---- the switchyard
    add("pw_xfmr", None, "Transformer", "METAL", rotate=None, tex={"side": "pw_xfmr", "up": "pw_xfmr", "down": "pw_xfmr"})
    add("pw_fins", [[k, 0, 2, k + 1, 16, 16, "pw_fins"] for k in range(1, 16, 2)] + [[0, 14, 6, 16, 15, 9, steel], [0, 1, 6, 16, 2, 9, steel]],
        "Radiators", "METAL", render="cutout", shape=[[0, 0, 2, 16, 16, 16]])
    sheds = [[5 - (k % 2), y, 5 - (k % 2), 11 + (k % 2), y + 1.5, 11 + (k % 2), "pw_porcelain"] for k, y in enumerate(range(0, 14, 2))]
    add("pw_bushing", [[6, 0, 6, 10, 16, 10, "pw_porcelain"]] + sheds + [[7, 15, 7, 9, 18, 9, chrome]], "Bushing", "STONE", rotate=None,
        render="cutout", shape=[[5, 0, 5, 11, 16, 11]])
    add("pw_bushing_lo", [[6, 0, 6, 10, 9, 10, "pw_porcelain"], [5, 2, 5, 11, 3.5, 11, "pw_porcelain"], [5, 5, 5, 11, 6.5, 11, "pw_porcelain"],
                          [7, 9, 7, 9, 12, 9, chrome]], "Bushing", "STONE", rotate=None, shape=[[5, 0, 5, 11, 12, 11]])
    add("pw_conservator", [[0, 3, 2, 16, 13, 14, "pw_xfmr"], [0, 2, 4, 16, 14, 12, "pw_xfmr"], [7, 0, 7, 9, 3, 9, steel]], "Conservator", "METAL",
        rotate="axis", shape=[[0, 0, 2, 16, 14, 14]])
    add("pw_breaker", [[0, 0, 0, 16, 3, 16, steel], [1, 3, 4, 5, 12, 12, "pw_xfmr"], [6, 3, 4, 10, 12, 12, "pw_xfmr"], [11, 3, 4, 15, 12, 12, "pw_xfmr"],
                       [2, 12, 7, 4, 16, 9, "pw_porcelain"], [7, 12, 7, 9, 16, 9, "pw_porcelain"], [12, 12, 7, 14, 16, 9, "pw_porcelain"],
                       [1, 3, 12, 15, 10, 15, {"all": grey, "north": "bunk_switch"}]], "Circuit Breaker", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    add("pw_post_ins", [[5, 0, 5, 11, 2, 11, galv], [6.5, 2, 6.5, 9.5, 14, 9.5, "pw_porcelain"]]
        + [[5, y, 5, 11, y + 1, 11, "pw_porcelain"] for y in (4, 7, 10)] + [[5, 14, 5, 11, 15, 11, chrome], [7, 15, 7, 9, 16, 9, chrome]],
        "Post Insulator", "STONE", rotate=None, render="cutout", shape=[[5, 0, 5, 11, 16, 11]])
    add("pw_bus", [[6.5, 6.5, 0, 9.5, 9.5, 16, chrome]], "Bus Tube", "METAL", rotate="axis", decor=True, shape=[[6, 6, 0, 10, 10, 16]])
    add("pw_disconnect", [[6, 0, 1, 10, 2, 15, galv], [7, 2, 2, 9, 12, 4, "pw_porcelain"], [7, 2, 12, 9, 12, 14, "pw_porcelain"],
                          [7.5, 12, 2, 8.5, 13, 14, chrome], [6.5, 2, 1, 9.5, 3, 5, "pw_porcelain"], [6.5, 2, 11, 9.5, 3, 15, "pw_porcelain"]],
        "Disconnect Switch", "METAL", rotate="axis", render="cutout", shape=[[6, 0, 1, 10, 13, 15]])
    d45 = {"axis": "z", "angle": 45, "origin": [8, 8, 8]}
    add("pw_leg", [[5.5, 0, 5.5, 10.5, 16, 10.5, galv], [4.5, 7, 4.5, 11.5, 9, 11.5, galv]], "Steel Lattice", "METAL", rotate=None,
        shape=[[5, 0, 5, 11, 16, 11]])
    add("pw_girt", [[0, 7, 7, 16, 9, 9, galv], [0, 7, 6, 16, 7.5, 10, galv]], "Steel Lattice", "METAL", rotate="axis", render="cutout",
        shape=[[0, 7, 6, 16, 9, 10]])
    add("pw_diag", [[-3.3, 7.25, 7.25, 19.3, 8.75, 8.75, galv, None, d45]], "Steel Lattice", "METAL", render="cutout", shape=[[0, 0, 7, 16, 16, 9]])
    add("pw_diagx", [[-3.3, 7.25, 7.25, 19.3, 8.75, 8.75, galv, None, d45],
                     [-3.3, 7.25, 7.0, 19.3, 8.75, 7.25, galv, None, {"axis": "z", "angle": -45, "origin": [8, 8, 8]}],
                     [7, 7, 6.5, 9, 9, 9.5, galv]], "Steel Lattice", "METAL", render="cutout", shape=[[0, 0, 7, 16, 16, 9]])
    add("pw_ins_string", [[7.5, 10, 7.5, 8.5, 16, 8.5, chrome]] + [[5, y, 5, 11, y + 1, 11, "pw_porcelain"] for y in (2, 4, 6, 8)]
        + [[7, 0, 7, 9, 10, 9, "pw_porcelain"]], "Insulator String", "STONE", rotate=None, render="cutout", decor=True,
        shape=[[5, 0, 5, 11, 16, 11]])
    add("pw_wire", [[7.6, 14.5, 0, 8.4, 15.3, 16, "home_plastic_black"]], "Line", "METAL", rotate="axis", decor=True, shape=[[7, 14, 0, 9, 16, 16]])

    # ---- the control room
    for suffix, light in (("", None), ("_on", 4)):
        top = f"pw_bench_top{suffix}"
        add(f"pw_bench{suffix}", [[0, 0, 4, 16, 9, 16, {"north": "pw_bench_front", "side": "pw_panel", "up": "pw_panel", "down": "pw_panel"}],
                                  [0, 9, 0, 16, 10, 12, {"up": top, "north": "pw_panel", "side": "pw_panel", "down": "pw_panel"}, None,
                                   {"axis": "x", "angle": 22.5, "origin": [8, 9, 12]}],
                                  [0, 9, 12, 16, 13, 16, "pw_panel"]],
            "Benchboard", "METAL", shape=[[0, 0, 0, 16, 13, 16]], **({"light": light} if light else {}))
        add(f"pw_vb_lo{suffix}", [[0, 0, 4, 16, 16, 16, face(f"pw_vb_lo{suffix}", "pw_panel")]], "Control Board", "METAL",
            **({"light": light} if light else {}))
        add(f"pw_vb_hi{suffix}", [[0, 0, 4, 16, 16, 16, face(f"pw_vb_hi{suffix}", "pw_panel")], [0, 15, 2, 16, 16, 4, "pw_panel"]],
            "Control Board", "METAL", **({"light": light} if light else {}))
        add(f"pw_annun{suffix}", [[0, 0, 4, 16, 16, 16, face(f"pw_annun{suffix}", "pw_panel")]], "Annunciator", "METAL",
            **({"light": 6} if light else {}))
    for i in range(12):
        add(f"pw_mimic_{i}", plate(f"pw_mimic_{i}", 15.0), "One-Line Diagram", "METAL", decor=True)
    for i in range(2):
        add(f"pw_freq_{i}", plate(f"pw_freq_{i}", 14.6), "Frequency Meter", "METAL", decor=True, light=3)
    add("pw_desk", [[0, 11, 0, 16, 12.5, 14, "cv_oak"], [0, 0, 1, 4, 11, 13, {"all": grey, "north": "bunk_grey"}], [12, 0, 1, 16, 11, 13, grey],
                    [5, 12.5, 4, 9, 14, 8, black], [10, 12.5, 2, 14, 13.5, 9, "paper"]], "Supervisor's Desk", "METAL", loot="office",
        shape=[[0, 0, 0, 16, 12.5, 14]])

    # ---- the shop, the stores, the offices
    add("pw_lathe_head", [[0, 0, 3, 16, 9, 13, "pw_machine"], [0, 9, 4, 16, 16, 13, "pw_machine"], [12, 10, 2, 16, 14, 4, steel],
                          [2, 9, 2, 6, 12, 4, black]], "Lathe", "METAL", shape=[[0, 0, 2, 16, 16, 13]])
    add("pw_lathe_tail", [[0, 0, 3, 16, 9, 13, "pw_machine"], [0, 9, 6, 16, 10, 10, steel], [4, 10, 5, 10, 14, 11, "pw_machine"],
                          [0, 10.5, 7, 16, 11.5, 8, chrome]], "Lathe", "METAL", shape=[[0, 0, 3, 16, 14, 13]])
    add("pw_drill", [[3, 0, 3, 13, 1, 13, "pw_machine"], [7, 1, 9, 9, 16, 11, steel], [4, 8, 2, 12, 9, 10, "pw_machine"],
                     [5, 13, 4, 11, 16, 12, "pw_machine"], [7.5, 9, 6, 8.5, 13, 7, chrome]], "Drill Press", "METAL", shape=[[3, 0, 2, 13, 16, 13]])
    add("pw_grinder", [[6, 0, 6, 10, 9, 10, "pw_machine"], [4, 9, 5, 12, 13, 11, "pw_machine"], [1, 9, 6, 4, 14, 10, grey],
                       [12, 9, 6, 15, 14, 10, grey]], "Grinder", "METAL", shape=[[1, 0, 5, 15, 14, 11]])
    add("pw_vise_bench", [[0, 11, 0, 16, 13, 16, "cv_oak"], [1, 0, 1, 3, 11, 3, steel], [13, 0, 1, 15, 11, 3, steel], [1, 0, 13, 3, 11, 15, steel],
                          [13, 0, 13, 15, 11, 15, steel], [1, 3, 1, 15, 4, 15, "cv_oak"], [3, 13, 0, 8, 16, 4, "pw_machine"]],
        "Workbench", "WOOD", loot="hardware", shape=[[0, 0, 0, 16, 13, 16]])
    add("pw_welder", [[2, 0, 3, 12, 10, 13, {"all": "pw_drum", "north": "fit_louvre"}], [12, 0, 4, 15, 15, 7, "bunk_tank"],
                      [12, 0, 9, 15, 15, 12, "bunk_pipe_red"]], "Welder", "METAL", shape=[[2, 0, 3, 15, 15, 13]])
    add("pw_parts", [[0, 0, 3, 16, 16, 16, face("pw_parts", steel)]], "Parts Shelves", "METAL", loot="hardware")
    add("pw_reel", [[1, 1, 0, 15, 15, 2, {"all": "pw_reel", "side": "pw_reel"}], [1, 1, 14, 15, 15, 16, {"all": "pw_reel", "side": "pw_reel"}],
                    [3, 3, 2, 13, 13, 14, "pw_cable"], [7, 0, 2, 9, 1, 14, "cv_oak"]], "Cable Reel", "WOOD", rotate="axis",
        shape=[[1, 0, 0, 15, 15, 16]])
    add("pw_pipe_rack", [[0, 0, 1, 16, 1, 15, steel], [0, 1, 2, 16, 4, 5, "bunk_pipe_grey"], [0, 1, 6, 16, 4, 9, "bunk_pipe_grey"],
                         [0, 1, 10, 16, 4, 13, "bunk_pipe_red"], [0, 4, 4, 16, 7, 7, "bunk_pipe_grey"], [0, 4, 8, 16, 7, 11, "bunk_pipe_green"]],
        "Pipe Stock", "METAL", rotate="axis", shape=[[0, 0, 1, 16, 7, 15]])
    add("pw_hardhats", [[0, 4, 14, 16, 14, 16, face("pw_hardhats", "cv_oak")]], "Hard Hats", "WOOD", decor=True)
    add("pw_timeclock", [[0, 0, 14, 16, 16, 16, face("pw_timeclock", "cv_oak")], [5, 6, 11, 11, 12, 14, grey]], "Time Clock", "METAL", decor=True)
    add("pw_labbench", [[0, 0, 1, 16, 12, 16, {"all": "sc_beige", "north": "home_white"}], [0, 12, 0, 16, 13, 16, {"all": "pw_lab_top", "up": "pw_lab_top"}],
                        [12, 13, 12, 13, 18, 13, chrome], [9, 17, 12, 13, 18, 13, chrome]], "Lab Bench", "WOOD", loot="storage",
        shape=[[0, 0, 0, 16, 13, 16]])
    add("pw_fumehood", [[0, 0, 1, 16, 16, 16, {"all": "home_white", "north": "pw_sash"}], [1, 6, 0, 15, 7, 1, chrome]], "Fume Hood", "METAL",
        render="translucent")
    add("pw_model", [[1, 0, 1, 15, 9, 15, "cv_oak"], [1, 9, 1, 15, 10, 15, {"all": "cv_oak", "up": "pw_model"}],
                     [1, 10, 1, 15, 16, 15, "cc_glass"]], "Station Model", "GLASS", rotate=None, render="translucent", shape=[[1, 0, 1, 15, 16, 15]])
    for n in ("pw_tile", "pw_tile_line"):
        add(n, None, "Quarry Tile", "STONE", rotate=None, tex={"up": n, "side": "floor_concrete", "down": "floor_concrete"})

    # ---- pictures
    for i in range(6):
        add(f"pw_safety_{i}", plate(f"pw_safety_{i}", 15.4), "Safety Board", "WOOD", decor=True)
        add(f"pw_banner_{i}", plate(f"pw_banner_{i}", 15.6), "Banner", "WOOL", decor=True)
    for key in SIGNS:
        for i in range(2):
            add(f"pw_sgn_{key}_{i}", plate(f"pw_sgn_{key}_{i}"), "Sign", "METAL", decor=True)
    for key, (_, cols) in SPRAYS.items():
        for i in range(cols):
            add(f"pw_spray_{key}_{i}", plate(f"pw_spray_{key}_{i}", 15.8), "Graffiti", "WOOL", decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("pw_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} power station blocks in {path}")


if __name__ == "__main__":
    main()
