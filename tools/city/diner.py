"""The diner: the dn_ kit for the roadside diners and the truck stop (world/county/Diner.java).

Drawn from the library's photos. refs/roadside/diner-railcar: the Worcester lunch cars (Miss
Worcester's cream body with its blue name and BOOTH SERVICE at the ends, the blue striped awning
over the sash windows, the brick base and the steps up at the end; Bolton Landing's navy car with
gold pinstripes and gold lettering, cream window frames), the Grubstake (a red trolley car with a
curved end, gold window frames under green transom glass, the menu case and the hours placard by
the steps, a pipe handrail) and the Americana (a Kullman: fluted stainless, red and blue neon
along the eave, the glass-block vestibule rising into a tower with the name in neon, red barrel
roof). refs/roadside/diner-brick: Sylva's Coffee Shop (one storey of brick under a brown
standing-seam mansard that runs out over the walk, a fluorescent tube in its soffit; the maroon
signboard with HOME COOKED MEALS and HOME MADE PIES panels at its ends, a soda sign either side;
painted lettering on the windows; a letterboard on legs with letters gone; the sign hung from a
mast arm on a pipe pole; newspaper boxes, yellow wheel stops, lattice). refs/town/diner-cafe:
the Roundabout's dining room (high-back booths, oxblood seats and grey backs with a navy inset,
black tables with a ribbed chrome edge on one post, black and white check, the drop ceiling with
its fire horn and sprinklers, cage-globe pendants, the specials banner over the counter,
diamond-plate stainless behind the line, the Please Wait To Be Seated chalkboard). The kitchen
behind follows what a short-order line is known to hold (no photo of one yet).

Every name is made up: the cars are MISS GRAYHAVEN (cream and navy), NIGHT OWL LUNCH (navy and
gold), WHISTLE STOP (red) and SILVER MOON (stainless); the brick one is the HILLTOP CAFE; the soda
is RIDGE COLA.

Blocks (tile 0 is always the viewer's left):
- the cars: dn_body_{w,b,r}, dn_backq_{w,b,r,s}, dn_flute, dn_flute_dent, dn_name_{w,b,r,s}_0..5, dn_svc_{w,b,r}_0..1,
  dn_corner_{w,b,r,s}, dn_win_{c,g,s}_{lo,hi}(_broken), dn_eave_{w,b,r,s}(_on), dn_roof_{d,r,s},
  dn_crown_{a,b,c}_{d,r}, dn_awning(_torn), dn_glassblock(_lit), dn_door, dn_tower_0..3(_on),
  dn_neon_diner_0..1(_on), dn_menucase, dn_hours, dn_rail;
- the brick cafe: dn_mansard, dn_mansard_corner, dn_soffit, dn_soffit_tube(_on), dn_board_{c,t}_0..7,
  dn_cola_0..1, dn_paintwin_0..11, dn_marquee_{a,b}_0..5, dn_marquee_leg,
  dn_hang_0..5(_on), dn_mast, dn_mast_arm, dn_lattice, dn_wheelstop, dn_wall_{brick,cmu}_{tile,panel,quilt,frp,teal};
- the dining room: dn_checker, dn_checker_worn, dn_mosaic, dn_tile, dn_tile_crack, dn_quilt, dn_pine,
  dn_teal, dn_droptile(_alarm,_stain,_gone), dn_globe(_on), dn_orb(_on), dn_booth_{r,b,g}(_torn),
  dn_table(_set,_mess,_blood), dn_stool_{r,b,g}, dn_stool_torn, dn_stool_stub, dn_counter_{q,t,r},
  dn_counter_{q,t,r}_end, dn_ct_{register,cake,pies,napkins,mess,donuts,menus}, dn_menuboard_0..5,
  dn_specials_0..5, dn_chalk_{a,b}_{lo,hi}, dn_clock(_on), dn_poster_0..3, dn_note_0..2, dn_mugshelf,
  dn_gumball, dn_cig_{lo,hi}, dn_plates_broken, dn_menus_floor;
- behind the counter: dn_backbar, dn_urn, dn_brewer(_on), dn_mixer, dn_juice, dn_mugs, dn_toaster,
  dn_griddle, dn_fryer, dn_range, dn_hood(_on), dn_pass(_on);
- the kitchen: dn_quarry, dn_quarry_grease, dn_frp, dn_prep, dn_prep_bare, dn_lowboy, dn_sink, dn_drain,
  dn_dish_{lo,hi}, dn_dry(_low), dn_cold, dn_walkin, dn_walkin_door, dn_ice_{lo,hi}, dn_mop, dn_grease;
- the truck stop: dn_shower, dn_sign_showers, dn_cb, dn_scale.

    python3 tools/city/diner.py        rewrites the dn_ entries in blocks.json

photo_textures.py draws the textures (diner.register()). The base model faces north: anything on a
wall has its back at z = 16. A face only shows the part of its texture under its box.
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
import school  # noqa: E402
from furniture import area, disc, edges  # noqa: E402
from school import Pic, mixc  # noqa: E402
from gasstation import glow, lit_where, weather, plate  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BLACK = "/usr/share/fonts/opentype/inter/InterDisplay-Black.otf"
SCRIPT = "/usr/share/fonts/truetype/freefont/FreeSerifBoldItalic.ttf"
SERIFB = "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf"
SERIF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
SANS = school.SANS
COND = school.COND
HAND = school.HAND
MARKER = F.SCRAWL
WHITE = (238, 236, 228)
CREAM = (232, 222, 190)
NAVY = (30, 54, 118)
GOLD = (222, 178, 58)
RED = (172, 32, 36)
OXBLOOD = (118, 24, 32)
MAROON = (108, 30, 34)
CHARCOAL = (30, 30, 32)
CARS = {
    # the body, its trim (corners, eave, sash), the lettering, the pinstripes, the name
    "w": dict(body=CREAM, trim=NAVY, ink=NAVY, stripe=NAVY, name="Miss Grayhaven", frame="c"),
    "b": dict(body=NAVY, trim=CREAM, ink=GOLD, stripe=GOLD, name="Night Owl Lunch", frame="c"),
    "r": dict(body=(160, 40, 32), trim=(150, 34, 28), ink=CREAM, stripe=GOLD, name="Whistle Stop", frame="g"),
}
FRAMES = {"c": (226, 216, 184), "g": (196, 150, 62), "s": (198, 202, 204), "m": (100, 30, 34)}
TRANSOM = {"c": (176, 190, 160), "g": (80, 150, 100), "s": (140, 168, 196)}
VINYL = {"r": (128, 26, 34), "b": (40, 52, 104), "g": (36, 108, 92)}
FOOD = [(236, 226, 196), (200, 150, 70), (150, 60, 40), (220, 190, 60), (90, 120, 60), (120, 80, 50)]


def stext(p, x, y, s, font, size, fill, stroke=None, sw=0.0, anchor="ma", a=255):
    """Pic text with an outline (the outline alone, when fill is None, draws neon tubes)."""
    f = p.font(font, size)
    d, layer = p._pen(a)
    for k, line in enumerate(s.split("|")):
        d.text((x * 4, (y + k * size * 1.15) * 4), line, font=f, fill=p._c(fill, a) if fill is not None else None,
               anchor=anchor, stroke_width=int(sw * 4), stroke_fill=p._c(stroke, a) if stroke is not None else None)
    p._ink(layer)


def metal(seed, rgb=(196, 200, 202), brushed=True, f0=230, smooth=0.75):
    """Stainless: brushed along x, a soft sheen down it."""
    t = F.plain(seed, rgb, 0.03, smooth)
    t.f0[:] = f0
    if brushed:
        for _ in range(60):
            y = t.rng.integers(0, N)
            area(t, 0, y, N, y + 1, mul=float(t.rng.uniform(0.95, 1.05)))
    yy = np.arange(N, dtype=np.float32)[:, None]
    t.c *= (1.06 - 0.12 * yy / N)[..., None]
    return t


def peel(t, amount, under=(150, 150, 146)):
    """Paint flaking off in patches, the primer or bare metal under it."""
    rng = t.rng
    for _ in range(int(amount * 5)):
        x, y = rng.uniform(0, N), rng.uniform(0, N)
        m = F.blob_mask(rng, x, y, rng.uniform(0.6, 1.8), rough=1.1)
        m = np.maximum(m, F.blob_mask(rng, x + rng.uniform(-2, 2), y + rng.uniform(1, 3), rng.uniform(0.4, 1.2), rough=1.1))
        t.mix(m, under, 0.75)
        t.h -= m * 0.12
    return t


def rivets(t, xs, step=8, col=None):
    for x in xs:
        for y in range(4, N, step):
            disc(t, x, y, 0.9, col, mul=1.12 if col is None else None, dh=0.3)


# ---------------------------------------------------------------- the railcars

def panel_marks(t, k):
    """The car's panel seams and rivet lines, and the years on it."""
    for x in (0, 31, 32, 63):
        area(t, x, 0, x + 1, N, mul=0.72, dh=-0.2)
    rivets(t, (2.5, 29.5, 34.5, 61.5))
    peel(t, 0.5 if k != "w" else 0.7, (150, 146, 136))
    weather(t, rust=0.7, streak=0.65, grime=0.3, dents=1)


def body(k, seed):
    """A car's lower body: painted steel, the pinstripes under the windows, the sill band."""
    C = CARS[k]
    t = F.plain(seed, C["body"], 0.04, 0.55)
    t.f0[:] = 40
    area(t, 0, 4, N, 6, C["stripe"], smooth=0.6)
    area(t, 0, 8, N, 9, C["stripe"], smooth=0.6)
    area(t, 0, N - 7, N, N, C["trim"] if k != "r" else mixc(C["body"], (0, 0, 0), 0.25), dh=0.15)
    area(t, 0, N - 7, N, N - 6, mul=1.2)
    panel_marks(t, k)
    return t


@lru_cache(maxsize=None)
def name_pic(k):
    """The car's name along its lower body, six blocks: script with a shadow, the pinstripes."""
    C = CARS[k]
    p = Pic(6, 1, C["body"], 9000 + ord(k))
    W = p.W
    p.rect(0, 4, W, 6, fill=C["stripe"])
    p.rect(0, 8, W, 9, fill=C["stripe"])
    p.rect(0, N - 7, W, N, fill=C["trim"] if k != "r" else mixc(C["body"], (0, 0, 0), 0.25))
    font = SCRIPT if k != "r" else SERIFB
    size = civic.fit(C["name"], font, 46, W - 60)
    shadow = (255, 255, 255) if k == "w" else (20, 20, 24)
    stext(p, W / 2 + 1.6, 12 + 1.6, C["name"], font, size, shadow, shadow, 0.8)
    stext(p, W / 2, 12, C["name"], font, size, C["ink"], mixc(C["ink"], (0, 0, 0), 0.4), 0.5)
    # the swash under the name
    p.line([(W * 0.2, N - 11), (W * 0.5, N - 13), (W * 0.8, N - 11)], fill=C["ink"], width=1.3)
    return p


def name(k, i):
    t = name_pic(k).tile(i, 9010 + ord(k) * 7 + i, smooth=0.55, grime=0.1)
    t.f0[:] = 40
    panel_marks(t, k)
    return t


@lru_cache(maxsize=None)
def svc_pic(k):
    C = CARS[k]
    p = Pic(2, 1, C["body"], 9050 + ord(k))
    p.rect(0, 4, p.W, 6, fill=C["stripe"])
    p.rect(0, 8, p.W, 9, fill=C["stripe"])
    p.rect(0, N - 7, p.W, N, fill=C["trim"] if k != "r" else mixc(C["body"], (0, 0, 0), 0.25))
    size = civic.fit("SERVICE", SERIF, 15, p.W - 50)
    stext(p, p.W / 2, 14, "BOOTH|SERVICE", SERIF, size, C["ink"])
    for y in (46, 50):
        p.rect(p.W / 2 - 20, y, p.W / 2 + 20, y + 1.4, fill=C["ink"])
    return p


def svc(k, i):
    t = svc_pic(k).tile(i, 9060 + ord(k) * 3 + i, smooth=0.55, grime=0.1)
    panel_marks(t, k)
    return t


def flute(seed, dent=False):
    """Kullman's lower body: fluted stainless between two red enamel bands with chrome strips."""
    t = metal(seed, (192, 196, 198), brushed=False, f0=230, smooth=0.8)
    xx = np.arange(N, dtype=np.float32)[None, :]
    t.c *= (0.92 + 0.14 * np.sin(xx / 4.0 * np.pi) ** 2)[..., None]
    t.h += (np.sin(xx / 4.0 * np.pi) ** 2) * 0.2
    for y0, y1 in ((3, 12), (N - 12, N - 4)):
        area(t, 0, y0, N, y1, RED, smooth=0.75, f0=40, dh=0.1)
        area(t, 0, y0 - 2, N, y0, (220, 224, 226), smooth=0.9, f0=233, dh=0.25)
        area(t, 0, y1, N, y1 + 2, (220, 224, 226), smooth=0.9, f0=233, dh=0.25)
    if dent:
        disc(t, 22, 30, 7, mul=0.82, dh=-0.3, soft=2)
        disc(t, 19, 27, 3, mul=1.15)
        t.crack(1, 0.1, (90, 90, 90))
    weather(t, rust=0.3, streak=0.7, grime=0.28, dents=1)
    return t


@lru_cache(maxsize=None)
def moon_band_pic():
    """SILVER MOON on the red band of the stainless car, chrome letters with a dark edge."""
    p = Pic(6, 1, (192, 196, 198), 9070)
    W = p.W
    for x in range(0, W, 4):
        p.rect(x, 0, x + 2, N, fill=(206, 210, 212))
    p.rect(0, 10, W, N - 10, fill=RED)
    p.rect(0, 8, W, 10, fill=(226, 228, 230))
    p.rect(0, N - 10, W, N - 8, fill=(226, 228, 230))
    size = civic.fit("SILVER MOON", BLACK, 34, W - 80)
    stext(p, W / 2, 16, "SILVER MOON", BLACK, size, (230, 232, 234), (40, 40, 44), 0.8)
    p.ellipse(18, 18, 46, 46, fill=(240, 220, 120))
    p.ellipse(26, 14, 52, 42, fill=RED)
    p.ellipse(W - 46, 18, W - 18, 46, fill=(240, 220, 120))
    p.ellipse(W - 52, 14, W - 26, 42, fill=RED)
    return p


def moon_band(i):
    t = moon_band_pic().tile(i, 9075 + i, smooth=0.8, grime=0.08)
    t.f0[:] = 230
    red = (t.c[..., 0] > 140) & (t.c[..., 1] < 60)
    t.f0[red] = 40
    weather(t, rust=0.2, streak=0.6, grime=0.25, dents=1)
    return t


def trim(k, seed):
    """Paint for the corners, sash and eave: the car's trim colour, or stainless."""
    if k == "s":
        return weather(metal(seed), rust=0.2, streak=0.6, grime=0.25)
    t = F.plain(seed, CARS[k]["trim"] if k in CARS else FRAMES[k], 0.05, 0.55)
    peel(t, 0.8)
    return weather(t, rust=0.6, streak=0.6, grime=0.3)


def frame_tex(f, seed):
    if f == "s":
        return weather(metal(seed, f0=233, smooth=0.85), rust=0.1, streak=0.5, grime=0.2)
    t = F.plain(seed, FRAMES[f], 0.05, 0.5)
    peel(t, 0.9)
    return weather(t, rust=0.5, streak=0.5, grime=0.3)


def window(f, part, broken=False, seed=0):
    """A sash window, one block of a two-block bay: the lower sash on its sill, or the upper one
    with the transom of coloured glass over it; the frame in the car's trim (cream, gold, chrome)
    or the brick cafe's maroon storefront. Broken: a hole knocked through, cracks out from it."""
    rng = np.random.default_rng(seed)
    t = F.plain(seed, (92, 108, 114), 0.05, 0.95)
    t.f0[:] = 40
    yy, xx = np.mgrid[0:N, 0:N]
    t.c += (np.clip(1 - np.abs(xx * 0.8 - yy - 10) / 8, 0, 1) * 40)[..., None]
    t.c += (np.clip(1 - np.abs(xx * 0.8 - yy + 22) / 4, 0, 1) * 24)[..., None]
    t.alpha[:] = 118
    fc = FRAMES[f]
    m = np.zeros((N, N), bool)
    s = 3 if f == "m" else 4
    m[:, :s] = True
    m[:, N - s:] = True
    if f == "m":
        if part == "lo":
            m[N - 6:] = True
        else:
            m[:5] = True
    elif part == "lo":
        m[N - 7:] = True
        m[:4] = True
    else:
        m[:5] = True
        m[N - 4:] = True
        m[17:20] = True
        tr = (yy >= 5) & (yy < 17) & ~m
        col = TRANSOM[f]
        t.c[tr] = np.array(col, np.float32) * (0.8 + 0.4 * P.fbm(rng, ((8, 1.0), (16, 0.5))))[tr][..., None]
        t.alpha[tr] = 190
    t.c[m] = fc
    t.alpha[m] = 255
    t.smooth[m] = 0.55 if f != "s" else 0.85
    t.f0[m] = 233 if f == "s" else 40
    t.h[m] = 0.95
    frame = np.zeros((N, N), np.float32)
    frame[m] = 1
    edge = P.blur(frame, 1) * (1 - frame)
    t.c *= (1 - edge * 0.35)[..., None]
    if f != "s":
        flakes = m & (rng.random((N, N)) < 0.08)
        t.c[flakes] = (150, 146, 136)
    if broken:
        cx, cy = rng.uniform(20, 44), rng.uniform(24, 44) if part == "lo" else rng.uniform(26, 50)
        hole = F.blob_mask(rng, cx, cy, rng.uniform(9, 16), rough=1.1, wrap=False) > 0.5
        hole &= ~m
        t.alpha[hole] = 0
        for a in rng.uniform(0, np.pi * 2, 9):
            r0 = 6
            for r in np.arange(r0, 40, 0.7):
                x, y = int(cx + np.cos(a) * r), int(cy + np.sin(a) * r)
                if 0 <= x < N and 0 <= y < N and not m[y, x] and not hole[y, x]:
                    t.c[y, x] = (210, 220, 222)
                    t.alpha[y, x] = 200
                a += rng.normal(0, 0.05)
    t.c[~m] *= 1.0
    t.grime(0.15)
    return t


@lru_cache(maxsize=None)
def paintwin_pic():
    """The cafe's windows painted for the season: FRESH SALADS with a bowl, HOME MADE PIES! with a
    cherry pie, six windows across and two high, the paint in poster colours."""
    p = Pic(6, 2, (0, 0, 0, 0), 9100)
    H = p.H
    yel, grn, red = (234, 216, 70), (110, 190, 110), (196, 40, 50)
    stext(p, 96, 20, "FRESH", COND, 30, yel, grn, 1.2)
    # the bowl of salad
    p.ellipse(54, 62, 138, 80, fill=(140, 200, 90), outline=(30, 60, 30), width=1)
    for k in range(14):
        x = 58 + k * 6
        p.ellipse(x, 52 + (k % 3) * 3, x + 9, 64 + (k % 2) * 3, fill=((80, 160, 70), (180, 40, 40), (120, 190, 80))[k % 3])
    p.poly([(56, 74), (136, 74), (122, 96), (70, 96)], fill=(232, 140, 60), outline=(60, 30, 20))
    for x in range(72, 124, 8):
        p.line([(x, 78), (x + 4, 92)], fill=(200, 100, 40), width=0.8)
    stext(p, 96, 100, "SALADS", COND, 26, yel, grn, 1.2)
    stext(p, 288, 12, "HOME MADE", COND, 26, (226, 60, 70), WHITE, 1.4)
    # the cherry pie
    p.ellipse(222, 60, 290, 92, fill=(214, 170, 110), outline=(80, 50, 30), width=1)
    p.poly([(224, 76), (288, 76), (280, 96), (232, 96)], fill=(236, 236, 230), outline=(60, 60, 60))
    for k in range(4):
        x = 236 + k * 13
        p.line([(x, 66), (x + 8, 88)], fill=(170, 120, 70), width=1)
    p.ellipse(252, 52, 260, 60, fill=(190, 20, 30))
    p.ellipse(260, 50, 268, 58, fill=(190, 20, 30))
    p.line([(256, 52), (262, 40), (266, 50)], fill=(60, 110, 40), width=0.8)
    stext(p, 320, 70, "PIES!", COND, 40, (226, 60, 70), WHITE, 1.4)
    p.rect(0, 0, p.W, 2, fill=(0, 0, 0, 0))
    return p


def paintwin(i):
    part = "hi" if i < 6 else "lo"
    t = window("m", part, False, 9110 + i)
    paint = paintwin_pic().tile(i, 9120 + i, smooth=0.5, grime=0)
    m = paint.alpha / 255 * (t.alpha < 250)  # on the glass, not the frame
    rng = np.random.default_rng(9130 + i)
    m *= (rng.random((N, N)) > 0.05)  # flaked where the scraper caught it
    t.c = t.c * (1 - m[..., None]) + paint.c * m[..., None]
    t.alpha[:] = np.maximum(t.alpha, m * 255)
    t.smooth[m > 0.5] = 0.5
    return t


def roof(kind, seed):
    """The car's roof: d, tar roll roofing gone grey with laps across and moss in the low spots;
    r, red standing seam with white ribs; s, the stainless car's red barrel caps."""
    if kind == "d":
        t = F.plain(seed, (54, 54, 56), 0.08, 0.15)
        t.speckle(0.25, (90, 90, 92), 0.5, height=0.1)
        for y in (0, 21, 42):
            area(t, 0, y, N, y + 1, mul=0.6, dh=0.2)
            area(t, 0, y + 1, N, y + 3, mul=1.15, dh=0.1)
        rng = t.rng
        for _ in range(3):
            F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(6, 14), (70, 80, 50), 0.4)
        t.grime(0.3)
        return t
    t = F.plain(seed, (166, 38, 36) if kind == "r" else (178, 34, 38), 0.05, 0.6)
    t.f0[:] = 40
    for x in (0, 32):
        area(t, x, 0, x + 3, N, (220, 214, 210), dh=0.4, smooth=0.6)
        area(t, x + 3, 0, x + 4, N, mul=0.75)
    t.mix(P.fbm(t.rng) * 0.3 + 0.15, (190, 120, 110), 0.4)  # chalked by the sun
    return weather(t, rust=0.4, streak=0.6, grime=0.25)


def vault(kind, seed):
    """The car's ceiling: varnished maple strips along it, or cream enamel panels; water stains,
    nicotine yellowing toward the grill end."""
    if kind == "wood":
        t = F.wood(seed, "oak")
        t.c = t.c * 0.6 + np.array((200, 150, 90), np.float32) * 0.4
        for y in range(0, N, 8):
            area(t, 0, y, N, y + 1, mul=0.7, dh=-0.15)
        t.smooth[:] = 0.6
    else:
        t = F.plain(seed, (226, 218, 194), 0.03, 0.7)
        for x in (0, 32):
            area(t, x, 0, x + 1, N, mul=0.75)
        rivets(t, (2, 30, 34, 62), 10)
    rng = t.rng
    for _ in range(2):
        F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(5, 12), (140, 110, 60), 0.35)
    t.mix(np.full((N, N), 0.12, np.float32), (170, 140, 70))
    t.grime(0.15)
    return t


def glassblock(seed, lit=False):
    """Glass block: four by four, the mortar, each block's fluted face catching the light."""
    t = F.plain(seed, (170, 196, 200), 0.04, 0.9)
    t.f0[:] = 40
    yy, xx = np.mgrid[0:N, 0:N]
    u, v = xx % 16, yy % 16
    t.c *= (0.86 + 0.18 * np.cos(u / 16 * np.pi * 4) ** 2)[..., None]
    t.h += (np.cos(u / 16 * np.pi * 4) ** 2) * 0.25
    mortar = (u < 1) | (v < 1) | (u > 14) | (v > 14)
    edge = ((u < 3) | (v < 3) | (u > 12) | (v > 12)) & ~mortar
    t.c[edge] *= 0.82
    t.c[mortar] = (176, 172, 164)
    t.smooth[mortar] = 0.2
    t.h[mortar] = 0.5
    t.alpha[:] = np.where(mortar, 255, 225)
    if lit:
        warm = ~mortar
        t.c[warm] = t.c[warm] * 0.55 + np.array((255, 214, 150), np.float32) * 0.45
        t.emit[warm] = 0.55
    t.grime(0.2)
    return t


def door_tex(seed, top):
    """The diner's door: stainless, a porthole in the top, the push bar and the quilted kick plate."""
    t = metal(seed, f0=230)
    t.frame(2, (170, 172, 172), 0.95, 0.7, f0=230)
    if top:
        yy, xx = np.mgrid[0:N, 0:N]
        d = np.hypot(xx - 32, yy - 32)
        ring = (d < 17) & (d >= 13)
        glass = d < 13
        t.c[ring] = (226, 228, 230)
        t.h[ring] = 1.0
        t.f0[ring] = 233
        t.c[glass] = (70, 84, 90)
        t.alpha[glass] = 110
        t.f0[glass] = 40
        t.c += (np.clip(1 - np.abs(xx - yy - 4) / 5, 0, 1) * glass * 50)[..., None]
        m = F.text_mask("PULL", SANS, 6, 24, 8, seed=1)
        F.paste(t, m, 20, 52, (40, 40, 40))
    else:
        area(t, 6, 6, 58, 10, (226, 228, 230), dh=0.5, f0=233)
        edges(t, 6, 6, 58, 10)
        q = quilt(seed + 1)
        t.c[40:62, 3:61] = q.c[40:62, 3:61]
        t.h[40:62, 3:61] = q.h[40:62, 3:61]
        F.fingers(t, 32, 8, 10, 0.3)
    t.grime(0.25)
    return t


@lru_cache(maxsize=None)
def tower_pic(lit):
    """The stainless car's tower over the vestibule, two by two: SILVER MOON in script over the
    crescent, DINER in chrome capitals, stars either side, the neon tubes along the edges."""
    p = Pic(2, 2, (30, 32, 36), 9150 + lit)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, fill=(196, 200, 202))
    p.rect(6, 6, W - 6, H - 6, fill=(26, 26, 30))
    tube = (255, 80, 150) if lit else (130, 70, 100)
    blue = (80, 150, 255) if lit else (70, 90, 130)
    p.rect(8, 8, W - 8, 10, fill=blue)
    p.rect(8, H - 10, W - 8, H - 8, fill=blue)
    p.ellipse(44, 16, 84, 56, fill=(240, 220, 120) if lit else (170, 160, 110))
    p.ellipse(54, 10, 92, 50, fill=(26, 26, 30))
    stext(p, W / 2 + 6, 46, "Silver Moon", SCRIPT, 22, None, tube, 0.9)
    stext(p, W / 2, 80, "DINER", BLACK, 20, (210, 214, 216), (60, 60, 64), 0.6)
    for x in (18, W - 18):
        p.poly([(x, 84), (x + 3, 92), (x + 11, 92), (x + 4.5, 97), (x + 7, 105), (x, 100), (x - 7, 105), (x - 4.5, 97),
                (x - 11, 92), (x - 3, 92)], fill=(240, 220, 120) if lit else (150, 140, 100))
    return p


def tower(i, lit):
    t = tower_pic(lit).tile(i, 9160 + i * 2 + lit, smooth=0.7, grime=0.1)
    t.f0[:] = 40
    if lit:
        lit_where(t, (t.c.max(-1) > 200) & (t.c.min(-1) < 200), 0.95)
    weather(t, rust=0.3, streak=0.6, grime=0.25)
    return t


@lru_cache(maxsize=None)
def neon_diner_pic(lit):
    """Diner in pink script tubes over a blue wave, a flame over the i, on its black backer."""
    p = Pic(2, 1, (0, 0, 0, 0), 9170 + lit)
    W = p.W
    p.rect(2, 4, W - 2, N - 2, fill=(20, 20, 22))
    pink = (255, 70, 140) if lit else (120, 70, 90)
    blue = (60, 140, 255) if lit else (60, 80, 110)
    stext(p, W / 2, 6, "Diner", SCRIPT, 34, None, pink, 1.0)
    for k, col in enumerate((blue, (255, 255, 255) if lit else (110, 110, 110), pink)):
        y = 48 + k * 3
        p.line([(18, y), (44, y - 5), (70, y + 2), (96, y - 4), (110, y)], fill=col, width=1.0)
    p.ellipse(52, 4, 58, 12, fill=(255, 190, 60) if lit else (120, 100, 70))
    return p


def neon_diner(i, lit):
    t = neon_diner_pic(lit).tile(i, 9175 + i * 2 + lit, smooth=0.5, grime=0.05)
    if lit:
        lit_where(t, (t.c.max(-1) > 150) & (t.alpha > 200), 1.0)
    return t


def menucase(seed):
    """The menu case by the door: a varnished frame, the glass, two typed sheets pinned up."""
    t = F.wood(seed, "oak")
    t.c = t.c * 0.7 + np.array((210, 190, 150), np.float32) * 0.3
    area(t, 6, 6, 58, 58, (236, 232, 220), h=0.5)
    for x0 in (10, 34):
        area(t, x0, 10, x0 + 20, 54, (246, 244, 236), h=0.55)
        for y in range(14, 52, 3):
            w = t.rng.uniform(8, 18)
            area(t, x0 + 2, y, x0 + 2 + w, y + 1, (60, 60, 64))
    area(t, 6, 6, 58, 58, smooth=0.95)
    t.mix(np.clip(1 - np.abs(np.mgrid[0:N, 0:N][1] - np.mgrid[0:N, 0:N][0] - 6) / 6, 0, 1) * 0.3, (255, 255, 255))
    edges(t, 4, 4, 60, 60)
    weather(t, rust=0.0, streak=0.4, grime=0.3)
    return t


def hours(seed):
    """The hours placard: OPEN TIL 4 AM, the name, BURGERS STEAKS BREAKFAST, since 1937."""
    p = Pic(1, 1, (236, 228, 204), seed)
    p.rect(2, 2, N - 2, N - 2, outline=(150, 120, 60), width=1)
    stext(p, 32, 5, "OPEN TIL 4 AM", SERIF, 5.5, (40, 40, 40))
    stext(p, 32, 12, "Whistle Stop", SCRIPT, 10, NAVY)
    stext(p, 32, 25, "DINER", SERIF, 6, NAVY)
    stext(p, 32, 33, "BURGERS STEAKS|BREAKFAST", SERIF, 4.5, (40, 40, 40))
    stext(p, 32, 46, "Since 1937", SCRIPT, 5, (40, 40, 40))
    for y in (54, 57):
        p.rect(14, y, 50, y + 0.8, fill=(110, 110, 110))
    t = p.tile(0, seed + 1, smooth=0.4, grime=0.2)
    F.stain(t, 50, 10, 6, (170, 140, 80), 0.4)
    return t


# ---------------------------------------------------------------- the brick cafe

def mansard(seed):
    """The brown standing-seam fascia over the walk: ribs every quarter, chalked by the sun."""
    t = F.plain(seed, (112, 74, 60), 0.05, 0.45)
    t.f0[:] = 40
    for x in range(0, N, 16):
        area(t, x, 0, x + 2, N, mul=1.25, dh=0.4)
        area(t, x + 2, 0, x + 3, N, mul=0.7)
    t.mix(P.fbm(t.rng) * 0.3 + 0.2, (150, 120, 110), 0.35)
    return weather(t, rust=0.5, streak=0.7, grime=0.3)


def soffit(seed):
    """The soffit under the mansard: dark brown v-groove panel, water stains, a wasp nest or two."""
    t = F.plain(seed, (70, 50, 40), 0.05, 0.35)
    for x in range(0, N, 8):
        area(t, x, 0, x + 1, N, mul=0.55, dh=-0.25)
    rng = t.rng
    F.stain(t, rng.uniform(0, N), rng.uniform(0, N), 10, (110, 90, 70), 0.4)
    if rng.random() < 0.6:
        disc(t, rng.uniform(10, 54), rng.uniform(10, 54), 4, (150, 140, 120), dh=0.4)
    t.grime(0.25)
    return t


def tube(seed, on):
    t = F.plain(seed, (236, 236, 230) if on else (196, 196, 192), 0.02, 0.8)
    if on:
        t.emit[:] = 1.0
    else:
        area(t, 0, 0, 6, N, (90, 80, 60))  # the burnt end
        area(t, N - 6, 0, N, N, (90, 80, 60))
    return t


BOARDS = {"c": ("HILLTOP CAFE", "Fine Food Since 1931", "HOME|COOKED|MEALS", "HOME|MADE|PIES", "the"),
          "t": ("ROUTE 9 TRUCK STOP", "Diesel  -  Food  -  Showers", "OPEN|24|HOURS", "DRIVERS|WELCOME", "")}


@lru_cache(maxsize=None)
def board_pic(v):
    """The signboard over the windows, eight blocks: the name in white serif with a black shadow
    over the black band of its motto, the cream panels at the ends."""
    name_, motto, left, right, small = BOARDS[v]
    p = Pic(8, 1, MAROON, 9200 + ord(v))
    W = p.W
    p.rect(1, 1, W - 1, N - 1, outline=(200, 160, 80), width=1.4)
    for x0 in (4, W - N + 4):
        p.rect(x0, 6, x0 + N - 8, N - 6, fill=(232, 222, 190), outline=(60, 40, 30), width=0.8)
    stext(p, N / 2, 12, left, SERIF, 11, (30, 30, 30))
    stext(p, W - N / 2, 12, right, SERIF, 11, (30, 30, 30))
    if small:
        stext(p, W / 2, 2, small, SCRIPT, 11, WHITE)
    size = civic.fit(name_, SERIFB, 30, W - 2 * N - 20)
    stext(p, W / 2 + 1.5, 14 + 1.5, name_, SERIFB, size, (10, 10, 10))
    stext(p, W / 2, 14, name_, SERIFB, size, WHITE, (10, 10, 10), 0.6)
    p.rect(N + 20, 46, W - N - 20, 58, fill=(18, 18, 20))
    stext(p, W / 2, 47, motto, SERIF, 9, WHITE)
    p.rect(N + 40, 12, W - N - 40, 13, fill=(200, 160, 80))
    return p


def board(v, i):
    t = board_pic(v).tile(i, 9210 + ord(v) * 9 + i, smooth=0.45, grime=0.15)
    t.f0[:] = 40
    peel(t, 0.5, (160, 150, 130))
    weather(t, rust=0.4, streak=0.7, grime=0.3)
    return t


@lru_cache(maxsize=None)
def cola_pic():
    """RIDGE COLA: white script on red, the white ribbon under it, Enjoy over it."""
    p = Pic(2, 1, (190, 24, 32), 9230)
    W = p.W
    p.poly([(0, 44), (W * 0.3, 52), (W * 0.7, 40), (W, 46), (W, N), (0, N)], fill=(240, 240, 236))
    p.poly([(0, 48), (W * 0.3, 56), (W * 0.7, 44), (W, 50), (W, 52), (W * 0.7, 47), (W * 0.3, 59), (0, 51)], fill=(190, 24, 32))
    stext(p, W / 2, 2, "Enjoy", SCRIPT, 8, WHITE)
    stext(p, W / 2, 10, "Ridge Cola", SCRIPT, 26, WHITE)
    return p


def cola(i):
    t = cola_pic().tile(i, 9235 + i, smooth=0.6, grime=0.15)
    t.f0[:] = 40
    return weather(t, rust=0.6, streak=0.6, grime=0.3, dents=1)


@lru_cache(maxsize=None)
def hang_pic(lit):
    """The hanging sign, three by two: The HILLTOP CAFE in blue on white, GOOD HOME COOKING."""
    p = Pic(3, 2, (240, 240, 236), 9240 + lit)
    W, H = p.W, p.H
    blue = (30, 60, 170)
    p.rect(0, 0, W, H, outline=(40, 80, 180), width=5)
    p.rect(6, 6, W - 6, H - 6, outline=(200, 200, 196), width=0.6)
    stext(p, W / 2, 10, "The", SCRIPT, 14, blue)
    stext(p, W / 2, 26, "HILLTOP", BLACK, 26, blue)
    stext(p, W / 2, 56, "CAFE", BLACK, 26, blue)
    stext(p, W / 2, 92, "GOOD HOME COOKING", SANS, 9, blue)
    p.rect(16, 106, W - 16, 112, fill=(214, 214, 208))
    return p


def hang(i, lit):
    t = hang_pic(lit).tile(i, 9245 + i * 2 + lit, smooth=0.6, grime=0.1)
    t.f0[:] = 40
    if lit:
        lit_where(t, t.c.min(-1) > 200, 0.7)
    weather(t, rust=0.5, streak=0.5, grime=0.3)
    return t


MARQUEE = {"a": ("HOME M DE PIE", "FRESH GR UND CHUCK", "VARIETY VEG IES"),
           "b": ("NO FOOD NO WATER", "DO NOT ENTER", "GOD HELP US")}


@lru_cache(maxsize=None)
def marquee_pic(v):
    """The letterboard on its legs, three by two: a black frame, the white face with its tracks,
    the push-in letters crooked, gone or swapped for the wrong colour."""
    p = Pic(3, 2, (240, 238, 228), 9260 + ord(v))
    rng = np.random.default_rng(9265 + ord(v))
    W, H = p.W, p.H
    p.rect(0, 0, W, H, outline=(20, 20, 22), width=7)
    size = min(civic.fit(line, BLACK, 22, W - 40 - 1.5 * len(line)) for line in MARQUEE[v])
    for k, line in enumerate(MARQUEE[v]):
        y = 14 + k * 36
        p.rect(8, y + 26, W - 8, y + 27, fill=(190, 188, 180))
        f = p.font(BLACK, size)
        total = sum(f.getlength(c) for c in line) / 4 + len(line) * 1.5
        x = W / 2 - total / 2
        for c in line:
            w = f.getlength(c) / 4 + 1.5
            if c != " " and rng.random() > 0.06:
                col = (200, 70, 20) if rng.random() < 0.12 else (14, 14, 16)
                p.text(x + rng.normal(0, 0.3), y + rng.normal(0, 0.8), c, BLACK, size, col)
            x += w
    return p


def marquee(v, i):
    t = marquee_pic(v).tile(i, 9270 + ord(v) * 6 + i, smooth=0.5, grime=0.15)
    t.f0[:] = 40
    return weather(t, rust=0.4, streak=0.5, grime=0.3)


def lattice(seed):
    """White lattice: laths crossing at forty-five degrees, gone grey and green at the bottom."""
    t = F.plain(seed, (226, 226, 218), 0.05, 0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    a = ((xx + yy) % 16) < 4
    b = ((xx - yy) % 16) < 4
    t.alpha[:] = np.where(a | b, 255, 0)
    t.c[a & b] *= 0.9
    t.c[b & ~a] *= 0.82
    t.mix(np.clip((yy - 40) / 24, 0, 1) * 0.6, (100, 120, 80))
    t.grime(0.3)
    return t


def wheelstop(seed):
    t = F.plain(seed, (232, 196, 40), 0.06, 0.25)
    t.speckle(0.2, (150, 146, 136), 0.7)
    for _ in range(4):
        x, y = t.rng.uniform(0, N, 2)
        disc(t, x, y, t.rng.uniform(2, 5), (140, 136, 128), dh=-0.2)
    t.grime(0.35)
    return t


def grease(seed):
    t = F.plain(seed, (90, 92, 92), 0.05, 0.4)
    m = F.text_mask("INEDIBLE|KITCHEN|GREASE", SANS, 7, 56, 30, seed=seed)
    F.paste(t, m, 4, 14, (230, 230, 220))
    F.drips(t, 4, 60, 2, 10, (6, 30), (60, 46, 26), 2)
    t.grime(0.4)
    return t


# ---------------------------------------------------------------- the dining room

def checker(seed, worn=False):
    """Twelve-inch tile, black and white: waxed, the grout, scuffs; worn ones cracked and lifted."""
    t = F.plain(seed, (222, 220, 208), 0.03, 0.65)
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N]
    dark = ((xx // 16) + (yy // 16)) % 2 == 1
    t.c[dark] = (30, 30, 32)
    t.c *= (0.94 + 0.12 * rng.random((4, 4)))[(yy // 16), (xx // 16)][..., None]
    grout = (xx % 16 == 0) | (yy % 16 == 0)
    t.c[grout] = (120, 118, 110)
    t.h[grout] = 0.6
    t.speckle(0.04, (110, 106, 96), 0.3)
    if worn:
        for _ in range(3):
            tx, ty = rng.integers(0, 4, 2)
            x0, y0 = tx * 16, ty * 16
            if rng.random() < 0.4:
                area(t, x0 + 1, y0 + 1, x0 + 16, y0 + 16, (70, 62, 50), h=0.3, smooth=0.1)  # gone: the black glue
            else:
                t.crack(1, 0.2)
        for _ in range(5):
            F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(3, 8), (90, 70, 50), 0.4, tide=False)
        t.grime(0.35)
    else:
        t.grime(0.15)
    return t


def quarry(seed, greasy=False):
    """Red quarry tile, six inch, dark grout; greasy: a slick of old fat in front of the line."""
    t = F.plain(seed, (150, 62, 44), 0.06, 0.25)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (0.88 + 0.22 * t.rng.random((6, 6)))[(yy // 11) % 6, (xx // 11) % 6][..., None]
    grout = (xx % 11 < 1) | (yy % 11 < 1)
    t.c[grout] = (70, 66, 60)
    t.h[grout] = 0.5
    if greasy:
        m = F.blob_mask(t.rng, 32, 32, 26, rough=0.6)
        t.mix(m * 0.6, (50, 30, 20))
        t.smooth = np.maximum(t.smooth, m * 0.85)
    t.grime(0.3)
    return t


def mosaic(seed):
    """The vestibule floor: penny tile, cream, a black diamond in it."""
    t = F.plain(seed, (90, 86, 80), 0.03, 0.4)
    yy, xx = np.mgrid[0:N, 0:N]
    for cy in np.arange(2, N, 4):
        for cx in np.arange(2 + (cy // 4 % 2) * 2, N, 4):
            col = (30, 30, 32) if abs(cx - 32) + abs(cy - 32) < 18 and abs(cx - 32) + abs(cy - 32) > 10 else (226, 218, 196)
            disc(t, cx, cy, 1.6, col, h=0.8, smooth=0.6)
    t.grime(0.3)
    return t


def tile(seed, kind="band"):
    """Four-inch wall tile, glossy cream, a black cap band and a maroon row under it; white for the
    kitchen and the restrooms; cracked: some broken off to the grey mortar."""
    rgb = (226, 218, 194) if kind != "white" else (236, 236, 232)
    t = F.plain(seed, rgb, 0.03, 0.8)
    yy, xx = np.mgrid[0:N, 0:N]
    if kind in ("band", "crack"):
        t.c[(yy < 8)] = (30, 30, 32)
        t.c[(yy >= 8) & (yy < 16)] = MAROON
    grout = (xx % 8 == 0) | (yy % 8 == 0)
    t.c[grout] = (150, 146, 136)
    t.h[grout] = 0.6
    t.smooth[grout] = 0.2
    t.c *= (0.95 + 0.1 * t.rng.random((8, 8)))[yy // 8, xx // 8][..., None]
    if kind == "crack":
        for _ in range(4):
            x0, y0 = t.rng.integers(0, 8, 2) * 8
            area(t, x0 + 1, y0 + 1, x0 + 8, y0 + 8, (130, 126, 118), h=0.4, smooth=0.1)
        t.crack(2, 0.2)
    t.grime(0.2)
    return t


def quilt(seed):
    """Diamond-plate stainless, the backsplash behind the line: the quilting catching the light."""
    t = metal(seed, (200, 204, 206), brushed=False, f0=230, smooth=0.85)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    u, v = (xx + yy) % 8, (xx - yy) % 8
    face = (u < 4) ^ (v < 4)
    t.c *= np.where(face, 1.12, 0.86)[..., None]
    t.h += np.where(face, 0.15, -0.1)
    t.mix(((u < 0.8) | (v < 0.8)).astype(np.float32), (110, 112, 114), 0.6)
    return t


def pine(seed):
    """Knotty pine boards up the cafe's walls, the grooves, the knots, the varnish gone orange."""
    t = F.wood(seed, "oak")
    t.c = t.c * 0.5 + np.array((206, 150, 84), np.float32) * 0.5
    for x in range(0, N, 10):
        area(t, x, 0, x + 1, N, mul=0.55, dh=-0.3)
    for _ in range(4):
        x, y = t.rng.uniform(2, N - 2), t.rng.uniform(4, N - 4)
        disc(t, x, y, t.rng.uniform(1.2, 2.4), (90, 50, 24), dh=-0.1)
    t.smooth[:] = 0.55
    t.grime(0.2)
    return t


def paintwall(seed, rgb=(64, 140, 158)):
    """Painted plaster, roller stipple, chair scuffs, a water stain from the roof."""
    t = F.plain(seed, rgb, 0.05, 0.35)
    t.speckle(0.3, mixc(rgb, WHITE, 0.15), 0.2, height=0.05)
    rng = t.rng
    for _ in range(5):
        x, y = rng.uniform(0, N), rng.uniform(30, 50)
        area(t, x, y, x + rng.uniform(4, 12), y + 1, (60, 56, 50))
    F.stain(t, rng.uniform(0, N), rng.uniform(0, 20), rng.uniform(6, 12), (130, 110, 70), 0.35)
    t.grime(0.25)
    return t


def frp(seed):
    """White pebbled panel on the kitchen walls, its seams, grease spatter."""
    t = F.plain(seed, (232, 232, 226), 0.03, 0.35)
    t.speckle(0.4, (214, 214, 206), 0.3, height=0.15)
    area(t, 0, 0, 2, N, (190, 190, 186), dh=0.2)
    t.speckle(0.03, (150, 120, 60), 0.6)
    t.grime(0.35, (90, 70, 40))
    return t


def droptile(seed, kind="plain"):
    """Two-foot ceiling tile in its white grid: fissured; the fire horn, a sprinkler, a stain."""
    t = F.plain(seed, (214, 212, 204), 0.03, 0.15)
    t.speckle(0.25, (170, 166, 156), 0.6, height=-0.1)
    for k in (0, 32):
        area(t, 0, k, N, k + 2, (238, 238, 234), h=0.9, smooth=0.5)
        area(t, k, 0, k + 2, N, (238, 238, 234), h=0.9, smooth=0.5)
    rng = t.rng
    if kind == "alarm":
        disc(t, 48, 48, 7, (190, 30, 30), dh=0.5, smooth=0.6)
        disc(t, 48, 48, 3, (236, 236, 230), dh=0.6, smooth=0.9)
    elif kind == "stain":
        F.stain(t, rng.uniform(10, 54), rng.uniform(10, 54), rng.uniform(10, 18), (140, 110, 60), 0.6)
        t.mix(F.blob_mask(rng, 20, 20, 6) * 0.4, (40, 40, 30))
    elif kind == "sprinkler":
        disc(t, 16, 16, 3, (200, 200, 200), dh=0.4, f0=233, smooth=0.9)
        disc(t, 16, 16, 1.2, (190, 30, 30), dh=0.5)
    t.grime(0.2)
    return t


def globe(seed, on):
    """Milk glass: brighter where the bulb sits; lit, glowing warm."""
    t = F.plain(seed, (232, 228, 214) if not on else (255, 240, 210), 0.02, 0.9)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (1.05 - 0.2 * np.abs(yy - 32) / 32)[..., None]
    if on:
        t.emit[:] = 0.9
    else:
        t.mix((t.rng.random((N, N)) < 0.03).astype(np.float32), (50, 40, 30), 0.7)  # flies in it
    return t


def vinyl(k, seed, torn=False):
    """Channel-tufted vinyl: the channels, the stitching, the shine; torn, the yellow foam through it
    and a strip of tape where someone tried."""
    rgb = VINYL[k]
    t = F.plain(seed, rgb, 0.05, 0.75)
    t.f0[:] = 30
    xx = np.arange(N, dtype=np.float32)[None, :]
    t.c *= (0.86 + 0.24 * np.abs(np.sin(xx / 16 * np.pi)) ** 0.5)[..., None]
    t.h += (np.abs(np.sin(xx / 16 * np.pi)) ** 0.5) * 0.3
    for x in range(0, N, 16):
        area(t, x, 0, x + 1, N, mul=0.6, dh=-0.3)
        for y in range(1, N, 3):
            area(t, x + 2, y, x + 3, y + 1, mul=1.2)
    rng = t.rng
    t.mix((rng.random((N, N)) < 0.02).astype(np.float32), mixc(rgb, WHITE, 0.3), 0.4)  # cracking
    if torn:
        m = F.blob_mask(rng, rng.uniform(16, 48), rng.uniform(16, 48), rng.uniform(7, 12), rough=1.2, wrap=False)
        t.mix(m, (224, 196, 110))
        t.h -= m * 0.3
        t.smooth -= m * 0.6
        x, y = rng.uniform(10, 40), rng.uniform(10, 50)
        area(t, x, y, x + 20, y + 5, (190, 190, 180), smooth=0.5)
    t.grime(0.2)
    return t


def shell(seed):
    """The booth's back: grey laminate shell, scuffed, the chrome edge strip at the top."""
    t = F.plain(seed, (118, 122, 126), 0.03, 0.5)
    area(t, 0, 0, N, 3, (210, 212, 214), f0=233, smooth=0.85)
    t.speckle(0.03, (90, 92, 96), 0.4)
    for _ in range(5):
        x, y = t.rng.uniform(0, N), t.rng.uniform(40, N)
        area(t, x, y, x + t.rng.uniform(4, 12), y + 1, (80, 80, 82))
    t.grime(0.2)
    return t


def formica(seed, kind):
    """Laminate: black with the grey boomerangs, red cracked-ice, or white marble."""
    if kind == "black":
        t = F.plain(seed, (34, 34, 36), 0.04, 0.8)
        t.speckle(0.08, (90, 90, 96), 0.6)
        rng = t.rng
        for _ in range(4):
            x, y = rng.uniform(0, N, 2)
            a = rng.uniform(0, np.pi)
            for s in np.linspace(-6, 6, 25):
                xi, yi = int(x + np.cos(a) * s) % N, int(y + np.sin(a) * s + 0.06 * s * s) % N
                t.c[yi, xi] = (80, 82, 88)
    elif kind == "red":
        t = F.plain(seed, (176, 36, 40), 0.05, 0.8)
        t.crack(14, 0.0, (220, 120, 120))
        t.speckle(0.05, (100, 20, 24), 0.5)
    else:
        t = F.plain(seed, (232, 230, 224), 0.03, 0.8)
        t.crack(8, 0.0, (170, 168, 164))
    t.f0[:] = 30
    rng = t.rng
    for _ in range(3):  # rings from cups, knife scores
        disc(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(3, 5), mul=0.9, ring=0.8)
    t.grime(0.15)
    return t


def rib(seed):
    """Ribbed chrome edge banding."""
    t = metal(seed, (214, 218, 220), brushed=False, f0=233, smooth=0.9)
    yy = np.arange(N)[:, None]
    t.c *= np.where(yy % 4 < 2, 1.12, 0.8)[..., None] * np.ones((1, N, 1), np.float32)
    t.h += np.where(yy % 4 < 2, 0.2, -0.1) * np.ones((1, N), np.float32)
    return t


def cfront(seed, kind):
    """The counter's front, facing the stools: quilted stainless under a red enamel band, cream tile
    under a black band, or red laminate with chrome strips; scuffed along the bottom by shoes."""
    if kind == "q":
        t = quilt(seed)
        area(t, 0, 4, N, 16, RED, f0=40, smooth=0.75)
        area(t, 0, 16, N, 18, (226, 228, 230), f0=233, smooth=0.9, dh=0.3)
        area(t, 0, 2, N, 4, (226, 228, 230), f0=233, smooth=0.9, dh=0.3)
    elif kind == "t":
        t = tile(seed, "band")
    else:
        t = formica(seed, "red")
        for y in (8, 30, 52):
            area(t, 0, y, N, y + 2, (220, 222, 224), f0=233, smooth=0.9, dh=0.3)
    rng = t.rng
    for _ in range(8):
        x, y = rng.uniform(0, N), rng.uniform(44, N)
        area(t, x, y, x + rng.uniform(3, 8), y + 1, (40, 36, 30))
    t.grime(0.25)
    return t


def bb_front(seed):
    """The back bar's base: stainless, two drawers over two doors, the pulls, fingerprints."""
    t = metal(seed)
    F.gap(t, 2, 2, 31, 18)
    F.gap(t, 33, 2, 62, 18)
    F.gap(t, 2, 20, 31, 62)
    F.gap(t, 33, 20, 62, 62)
    for x in (12, 43):
        area(t, x, 8, x + 10, 10, (226, 228, 230), dh=0.4, f0=233)
    area(t, 27, 26, 29, 44, (226, 228, 230), dh=0.4, f0=233)
    area(t, 35, 26, 37, 44, (226, 228, 230), dh=0.4, f0=233)
    F.fingers(t, 28, 34, 8, 0.25)
    t.grime(0.3)
    return t


def urn_face(seed):
    """The twin urns' fronts: chrome, the sight glasses with coffee low in them, the gauges."""
    t = metal(seed, (214, 216, 218), brushed=False, f0=233, smooth=0.9)
    for x in (12, 44):
        area(t, x - 2, 8, x + 2, 52, (60, 70, 74), h=0.9, smooth=0.95)
        area(t, x - 1.5, 38, x + 1.5, 52, (60, 34, 18))
        disc(t, x, 6, 4, (236, 236, 232), dh=0.4)
        area(t, x - 0.5, 4, x + 0.5, 7, (30, 30, 30))
    m = F.text_mask("COFFEE", SANS, 6, 30, 8, seed=2)
    F.paste(t, m, 17, 54, (40, 40, 40))
    return t


def brewer(seed, on):
    """The brewer: black and stainless, two pots on the warmers (one orange-topped decaf)."""
    t = F.plain(seed, (34, 34, 36), 0.03, 0.6)
    area(t, 0, 0, N, 14, (190, 194, 196), f0=230, smooth=0.8)
    for x, cap in ((6, (30, 30, 30)), (36, (220, 120, 30))):
        area(t, x, 22, x + 22, 52, (110, 120, 124), smooth=0.95)
        area(t, x + 1, 36, x + 21, 52, (50, 28, 14), smooth=0.95)
        area(t, x + 2, 18, x + 20, 23, cap)
        area(t, x, 54, x + 22, 57, (255, 60, 30) if on else (80, 40, 30))
    if on:
        lit_where(t, (t.c[..., 0] > 200) & (t.c[..., 1] < 90), 1.0)
    t.grime(0.3)
    return t


def mixer_face(seed):
    """The milkshake mixer: mint enamel, three spindles, the steel cups under them."""
    t = F.plain(seed, (150, 204, 176), 0.04, 0.75)
    for x in (10, 30, 50):
        area(t, x - 1, 8, x + 1, 30, (210, 212, 214), f0=233)
        area(t, x - 5, 30, x + 5, 50, (200, 204, 206), f0=230, smooth=0.85)
        area(t, x - 5, 30, x + 5, 32, mul=1.2)
    area(t, 0, 0, N, 8, (120, 180, 150))
    t.grime(0.25)
    return t


def juice_face(seed):
    """The juice dispenser: two clear bowls, orange and grape, the white base and taps."""
    t = F.plain(seed, (236, 236, 232), 0.03, 0.7)
    for x, col in ((4, (240, 150, 40)), (34, (110, 40, 110))):
        area(t, x, 4, x + 26, 40, (180, 200, 206), smooth=0.95)
        area(t, x + 1, 16, x + 25, 40, col, smooth=0.95)
        area(t, x + 10, 44, x + 16, 50, (30, 30, 30))
    t.grime(0.25)
    return t


def mugs(seed):
    """Mugs on the back-bar shelf, saucers stacked, a few gone, one broken."""
    t = F.plain(seed, (60, 44, 32), 0.03, 0.4)
    t.alpha[:] = 0
    rng = t.rng
    for y in (6, 30, 54):
        area(t, 0, y + 8, N, y + 10, (190, 192, 194), alpha=255, f0=230)
        for x in range(2, N - 8, 9):
            if rng.random() < 0.75:
                area(t, x, y - 4, x + 7, y + 8, (236, 234, 226), alpha=255, smooth=0.8)
                area(t, x + 7, y - 2, x + 9, y + 4, (220, 218, 210), alpha=255)
                area(t, x, y - 4, x + 7, y - 2, (150, 30, 30) if (x // 9) % 3 == 0 else (60, 60, 140), alpha=255)
    return t


def toaster(seed):
    t = metal(seed)
    area(t, 6, 20, 58, 30, (24, 22, 20))
    for x in (12, 26, 40):
        disc(t, x, 46, 4, (30, 30, 30), dh=0.4)
    t.grime(0.4, (70, 50, 30))
    return t


def griddle_top(seed):
    """The griddle's top: seasoned black steel, carbon, a scraper left on it, what was cooking when
    they ran (eggs gone grey, bacon, hash browns furred with mould); the grease trough."""
    t = F.plain(seed, (40, 38, 36), 0.08, 0.55)
    t.f0[:] = 230
    rng = t.rng
    t.mix(P.fbm(rng) * 0.5 + 0.3, (70, 50, 36), 0.4)
    for _ in range(5):
        x, y = rng.uniform(6, 58), rng.uniform(10, 50)
        if rng.random() < 0.5:
            disc(t, x, y, rng.uniform(3, 5), (200, 196, 170), dh=0.2, smooth=0.2)
            disc(t, x + 0.5, y, 1.6, (150, 140, 60), dh=0.3)
        else:
            area(t, x, y, x + rng.uniform(8, 14), y + 3, (120, 60, 40), dh=0.2)
        m = F.blob_mask(rng, x, y, 4, rough=0.9)
        t.mix(m * 0.4, (100, 120, 80))
    area(t, 40, 30, 58, 36, (190, 192, 194), dh=0.4, f0=230)
    area(t, 30, 32, 40, 34, (30, 30, 30), dh=0.3)
    area(t, 0, 0, N, 5, (70, 60, 44), dh=-0.3, smooth=0.8)  # the trough
    return t


def griddle_front(seed):
    t = metal(seed)
    for x in (10, 26, 42, 54):
        disc(t, x, 22, 3.5, (24, 24, 24), dh=0.5)
        area(t, x - 0.5, 18, x + 0.5, 21, (220, 220, 220))
    area(t, 4, 36, 60, 46, (170, 172, 172))
    F.gap(t, 4, 36, 60, 46)
    area(t, 28, 40, 36, 42, (220, 222, 224), dh=0.4)
    t.grime(0.45, (80, 60, 30))
    return t


def fryer_top(seed):
    """Two fry wells of congealed fat, the baskets hung up behind them."""
    t = metal(seed)
    for x in (4, 34):
        area(t, x, 10, x + 26, 56, (60, 40, 18), dh=-0.2, smooth=0.9)
        area(t, x + 3, 13, x + 23, 53, (80, 54, 24), smooth=0.95)
        for gx in range(x + 4, x + 22, 3):
            area(t, gx, 4, gx + 1, 10, (150, 150, 150))
    t.grime(0.4, (70, 50, 20))
    return t


def fryer_front(seed):
    t = metal(seed)
    F.gap(t, 4, 14, 60, 60)
    area(t, 26, 18, 38, 20, (226, 228, 230), dh=0.4)
    disc(t, 32, 8, 3, (24, 24, 24), dh=0.4)
    area(t, 50, 4, 58, 10, (200, 30, 30))
    t.grime(0.45, (80, 60, 30))
    return t


def range_top(seed):
    """Six burners under cast grates, the spills burnt on round them."""
    t = F.plain(seed, (44, 44, 46), 0.04, 0.4)
    for cx in (12, 32, 52):
        for cy in (18, 46):
            disc(t, cx, cy, 6, (30, 30, 30), ring=1.5, dh=0.3)
            disc(t, cx, cy, 2, (60, 60, 60), dh=0.2)
            F.stain(t, cx, cy, 8, (90, 60, 30), 0.4, tide=False)
    for k in range(0, N, 21):
        area(t, k, 0, k + 2, N, (24, 24, 24), dh=0.5)
    area(t, 0, 31, N, 33, (24, 24, 24), dh=0.5)
    return t


def range_front(seed):
    t = metal(seed)
    for x in range(6, 60, 10):
        disc(t, x, 6, 3, (24, 24, 24), dh=0.5)
    F.gap(t, 3, 14, 61, 62)
    area(t, 8, 18, 56, 21, (226, 228, 230), dh=0.4)
    t.grime(0.4, (80, 60, 30))
    return t


def baffles(seed, lamp=False, on=False):
    """The hood's underside: stainless baffle filters in a row, grease run down them; a lamp."""
    t = metal(seed)
    xx = np.arange(N)[None, :]
    t.c *= np.where(xx % 8 < 4, 1.1, 0.8)[..., None] * np.ones((N, 1, 1), np.float32)
    t.h += np.where(xx % 8 < 4, 0.2, -0.1) * np.ones((N, 1), np.float32)
    F.drips(t, 0, N, 0, 20, (6, 40), (90, 64, 26), 1)
    if lamp:
        disc(t, 32, 32, 6, (255, 240, 200) if on else (200, 196, 180), dh=0.4, smooth=0.95)
        if on:
            t.emit[:] = np.maximum(t.emit, F.blob_mask(t.rng, 32, 32, 6, 0, wrap=False))
    t.grime(0.3, (80, 60, 30))
    return t


def prep_top(seed, bare=False):
    """The prep table: stainless, a poly board with a knife and onion on it, a hotel pan."""
    t = metal(seed)
    if not bare:
        area(t, 4, 6, 40, 34, (236, 234, 226), smooth=0.4, dh=0.3)
        area(t, 10, 18, 34, 20, (200, 202, 204), dh=0.5, f0=233)
        area(t, 34, 17, 40, 21, (30, 30, 30), dh=0.5)
        for k in range(4):
            disc(t, 14 + k * 4, 26, 2, (220, 210, 170), dh=0.4)
        area(t, 44, 6, 60, 58, (176, 180, 182), dh=0.2, smooth=0.85)
        area(t, 46, 8, 58, 56, (130, 110, 70), dh=0.1, smooth=0.3)
        t.mix(F.blob_mask(t.rng, 52, 30, 8, 0.9, False) * 0.6, (90, 120, 70))
    t.grime(0.25)
    return t


def lowboy(seed):
    t = metal(seed)
    F.gap(t, 2, 6, 31, 62)
    F.gap(t, 33, 6, 62, 62)
    area(t, 26, 10, 28, 56, (226, 228, 230), dh=0.4)
    area(t, 36, 10, 38, 56, (226, 228, 230), dh=0.4)
    disc(t, 32, 3, 2, (230, 230, 230))
    t.grime(0.3)
    return t


def sink_top(seed):
    t = metal(seed)
    area(t, 6, 6, 58, 58, (120, 124, 126), dh=-0.5, smooth=0.9)
    disc(t, 32, 32, 3, (40, 40, 40), dh=-0.6)
    t.mix(F.blob_mask(t.rng, 30, 36, 14, 0.8, False) * 0.5, (90, 80, 50))
    return t


def drain_top(seed):
    t = metal(seed)
    for x in range(8, 58, 6):
        area(t, x, 6, x + 2, 58, mul=0.7, dh=-0.2)
    for k in range(5):
        x = 10 + k * 9
        area(t, x, 10, x + 2, 54, (236, 234, 226), dh=0.5)
    return t


def dish_front(seed):
    t = metal(seed)
    F.gap(t, 4, 12, 60, 62)
    area(t, 20, 16, 44, 19, (226, 228, 230), dh=0.4)
    for x in (12, 24):
        disc(t, x, 6, 3.5, (236, 236, 232), dh=0.3)
        area(t, x - 0.5, 4, x + 0.5, 6, (200, 30, 30))
    disc(t, 52, 6, 1.5, (60, 255, 60))
    t.grime(0.3)
    return t


def shelf_face(seed, kind):
    """Wire shelving, four shelves: number-ten cans and flour sacks; picked over (low); or the
    walk-in's milk crates, egg flats and produce, all gone to mould."""
    t = F.plain(seed, (60, 60, 60), 0.03, 0.4)
    t.alpha[:] = 0
    rng = t.rng
    keep = 0.25 if kind == "low" else 0.9
    for y in (14, 30, 46, 62):
        area(t, 0, y - 2, N, y, (196, 198, 198), alpha=255, f0=230)
        x = 1
        while x < N - 6:
            r = rng.random()
            if rng.random() > keep:
                x += 7
                continue
            if kind == "cold":
                if r < 0.4:
                    col = ((180, 30, 30), (30, 60, 150))[rng.integers(2)]
                    area(t, x, y - 13, x + 12, y - 2, col, alpha=255)
                    for gx in range(x + 1, x + 12, 3):
                        area(t, gx, y - 12, gx + 1, y - 3, (20, 20, 20), alpha=255)
                    x += 13
                elif r < 0.7:
                    area(t, x, y - 8, x + 14, y - 2, (200, 180, 140), alpha=255)
                    for gx in range(x, x + 14, 3):
                        disc(t, gx + 1.5, y - 7, 1.2, (230, 220, 200))
                    x += 15
                else:
                    area(t, x, y - 10, x + 12, y - 2, (180, 150, 100), alpha=255)
                    t.mix(F.blob_mask(rng, x + 6, y - 9, 4, 0.9, False), (100, 140, 90), 0.7)
                    x += 13
            else:
                if r < 0.55:
                    lab = ((200, 40, 30), (40, 120, 60), (220, 180, 40), (60, 60, 160))[rng.integers(4)]
                    area(t, x, y - 11, x + 7, y - 2, (180, 184, 186), alpha=255, f0=230)
                    area(t, x, y - 9, x + 7, y - 4, lab, alpha=255)
                    x += 8
                elif r < 0.8:
                    area(t, x, y - 12, x + 12, y - 2, (236, 232, 220), alpha=255)
                    area(t, x + 2, y - 9, x + 10, y - 6, (40, 70, 150), alpha=255)
                    x += 13
                else:
                    area(t, x, y - 12, x + 14, y - 2, (176, 136, 90), alpha=255)
                    area(t, x, y - 8, x + 14, y - 7, (140, 100, 60), alpha=255)
                    x += 15
    for x in (0, N - 2):
        area(t, x, 0, x + 2, N, (196, 198, 198), alpha=255, f0=230)
    t.grime(0.15)
    return t


def walkin(seed):
    """The walk-in's panels: embossed white aluminium, the seams, the cam-lock caps."""
    t = F.plain(seed, (226, 228, 226), 0.03, 0.4)
    t.speckle(0.4, (210, 212, 210), 0.3, height=0.12)
    area(t, 0, 0, 2, N, (180, 182, 180), dh=0.3)
    for y in (12, 50):
        disc(t, 32, y, 2.2, (200, 200, 200), dh=0.3)
    t.grime(0.3)
    return t


def walkin_door(seed, top):
    t = walkin(seed)
    t.frame(2, (190, 192, 190), 0.95)
    if top:
        area(t, 16, 12, 48, 30, (70, 84, 90), smooth=0.95)
        t.alpha[12:30, 16:48] = 120
        m = F.text_mask("KEEP DOOR|CLOSED", SANS, 6, 40, 16, seed=3)
        area(t, 12, 40, 52, 56, (236, 200, 40))
        F.paste(t, m, 12, 40, (20, 20, 20))
    else:
        area(t, 44, 4, 60, 14, (200, 202, 204), dh=0.6, f0=233)
        area(t, 54, 6, 58, 30, (200, 202, 204), dh=0.6, f0=233)
        area(t, 2, 50, 62, 62, (190, 192, 194), f0=230)
    return t


def ice_face(seed, part):
    t = metal(seed)
    if part == "lo":
        F.gap(t, 6, 6, 58, 40)
        area(t, 24, 10, 40, 13, (30, 30, 30))
    else:
        for y in range(36, 60, 4):
            area(t, 8, y, 56, y + 2, (40, 40, 40), dh=-0.2)
        m = F.text_mask("ICE", BLACK, 10, 30, 14, seed=4)
        F.paste(t, m, 17, 10, (60, 120, 190))
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- signs and the boards

@lru_cache(maxsize=None)
def menuboard_pic():
    """The menu over the back bar, three by two: black letterboard, BREAKFAST, LUNCH and DINNER on
    coloured headers, the dishes and prices in white push-in letters, some fallen out."""
    p = Pic(3, 2, (22, 22, 24), 9300)
    rng = np.random.default_rng(9301)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, outline=(150, 110, 60), width=3)
    for y in range(8, H - 4, 6):
        p.rect(4, y, W - 4, y + 0.4, fill=(36, 36, 38))
    cols = (("BREAKFAST", (190, 40, 40), (("2 EGGS", "1.95"), ("HOTCAKES", "1.65"), ("BISCUITS", "2.25"),
                                            ("HAM & EGGS", "3.95"), ("GRITS", ".95"), ("COFFEE", ".45"))),
            ("LUNCH", (40, 90, 170), (("HAMBURGER", "1.75"), ("CHZBURGER", "1.95"), ("B.L.T.", "1.85"), ("PATTY MELT", "2.45"),
                                     ("CHILI", "1.95"), ("FRIES", ".85"))),
            ("DINNER", (40, 130, 70), (("MEAT LOAF", "4.25"), ("COUNTRY STK", "4.95"), ("PORK CHOPS", "4.75"),
                                      ("CATFISH", "4.50"), ("PIE", "1.25"), ("SHAKES", "1.10"))))
    for c, (head, col, items) in enumerate(cols):
        x0 = c * N + 5
        p.rect(x0, 6, x0 + N - 10, 18, fill=col)
        stext(p, x0 + (N - 10) / 2, 7, head, COND, 9, WHITE)
        for k, (item, price) in enumerate(items):
            y = 24 + k * 15
            line = "".join(ch if rng.random() > 0.05 else " " for ch in item)
            p.text(x0, y, line, COND, min(6.2, civic.fit(item, COND, 7, 38)), (236, 236, 230))
            p.text(x0 + N - 10, y, price, COND, 6.2, (236, 220, 120), anchor="ra")
    return p


def menuboard(i):
    t = menuboard_pic().tile(i, 9305 + i, smooth=0.3, grime=0.15)
    return t


@lru_cache(maxsize=None)
def specials_pic():
    """The specials banner over the counter, six wide: Blue Plate Specials, a day and a dish to
    each block, the red end with its star."""
    p = Pic(6, 1, (244, 244, 240), 9310)
    W = p.W
    blue, red = (30, 50, 140), (190, 36, 40)
    p.rect(0, 0, W, 4, fill=(200, 200, 196))
    p.rect(0, N - 4, W, N, fill=(200, 200, 196))
    stext(p, N / 2, 18, "Blue Plate|Specials", SCRIPT, 13, blue)
    days = (("Monday", "Meat Loaf"), ("Tuesday", "Pot Roast"), ("Wednesday", "Fried Chicken"), ("Thursday", "Country Ham"))
    for k, (day, dish) in enumerate(days):
        x = (k + 1) * N + N / 2
        stext(p, x, 14, day, SERIF, 8, red)
        size = civic.fit(dish, SCRIPT, 13, N - 4)
        stext(p, x, 28, dish, SCRIPT, size, blue)
        p.line([((k + 1) * N + 2, 50), ((k + 1) * N + 6, 12)], fill=(150, 150, 150), width=0.6)
    p.rect(5 * N + 8, 4, W, N - 4, fill=red)
    p.poly([(5.5 * N + 4 + 18 * np.cos(a - np.pi / 2) * (1 if j % 2 == 0 else 0.42), N / 2 + 18 * np.sin(a - np.pi / 2) * (1 if j % 2 == 0 else 0.42))
            for j, a in enumerate(np.linspace(0, 2 * np.pi, 11)[:-1])], fill=(40, 70, 180))
    return p


def specials(i):
    return specials_pic().tile(i, 9315 + i, smooth=0.5, grime=0.15)


@lru_cache(maxsize=None)
def chalk_pic(v):
    """The chalkboard stand by the door, one by two: Please Wait To Be Seated on its wooden head,
    the chalk under it; b, what someone wrote after."""
    p = Pic(1, 2, (0, 0, 0, 0), 9320 + ord(v))
    p.rect(6, 2, N - 6, 26, fill=(150, 110, 70), outline=(100, 70, 40), width=1)
    stext(p, N / 2, 4, "Please Wait|To Be Seated", SERIF, 8, (24, 20, 16))
    p.rect(6, 26, N - 6, 98, fill=(30, 36, 34), outline=(120, 90, 60), width=2)
    chalk = (226, 226, 220)
    if v == "a":
        stext(p, N / 2, 32, "TRY OUR|HOME MADE", HAND, 7, chalk)
        stext(p, N / 2, 52, "PIES!", MARKER, 13, (240, 120, 110))
        for x in (16, 48):
            p.ellipse(x - 4, 76, x + 4, 84, outline=(240, 220, 120), width=0.8)
            p.ellipse(x - 1, 79, x + 1, 81, fill=(240, 160, 60))
            p.line([(x, 84), (x, 94)], fill=(120, 200, 120), width=0.7)
    else:
        stext(p, N / 2, 32, "CLOSED|NO FOOD|NO WATER", MARKER, 8, chalk)
        stext(p, N / 2, 66, "PRAY", MARKER, 13, (240, 110, 100))
        p.line([(10, 92), (54, 90)], fill=chalk, width=0.6)
    return p


def chalk(v, part):
    t = chalk_pic(v).tile(0 if part == "hi" else 1, 9325 + ord(v) * 2 + (part == "lo"), smooth=0.3, grime=0.1)
    school.chalky(t, (26, 64, 6, 58) if part == "hi" else (0, 34, 6, 58))
    return t


def clock(seed, on):
    """The wall clock: cream face, the hours, RIDGE COLA on it, a neon ring round it."""
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.ellipse(2, 2, N - 2, N - 2, fill=(30, 30, 32))
    ring = (255, 60, 60) if on else (120, 60, 60)
    p.ellipse(4, 4, N - 4, N - 4, outline=ring, width=2)
    p.ellipse(8, 8, N - 8, N - 8, fill=(240, 234, 214))
    for k in range(12):
        a = k / 12 * 2 * np.pi
        x, y = 32 + np.sin(a) * 20, 32 - np.cos(a) * 20
        p.rect(x - 1, y - 1, x + 1, y + 1, fill=(30, 30, 30))
    stext(p, 32, 38, "Ridge Cola", SCRIPT, 6, (190, 24, 32))
    p.line([(32, 32), (32 + 10, 32 - 9)], fill=(20, 20, 20), width=1.4)
    p.line([(32, 32), (32 - 4, 32 + 15)], fill=(20, 20, 20), width=0.9)
    t = p.tile(0, seed + 1, smooth=0.7, grime=0.1)
    if on:
        lit_where(t, (t.c[..., 0] > 200) & (t.c[..., 1] < 100), 1.0)
    return t


POSTERS = [("Drink|Ridge Cola", (190, 24, 32), WHITE, SCRIPT), ("PIE|a la mode|65c", (240, 220, 190), (120, 40, 40), SERIFB),
           ("Hand-Dipped|MILK|SHAKES", (150, 210, 200), (190, 40, 80), BLACK), ("BREAKFAST|SERVED|ALL DAY", (250, 220, 90), (40, 40, 40), BLACK)]


def poster(n):
    text, bg, ink, font = POSTERS[n]
    t = F.plain(9340 + n, bg, 0.04, 0.4)
    m = F.text_mask(text, font, civic.fit(text, font, 14, 56), 56, 50, seed=n)
    F.paste(t, m, 4, 7, ink)
    t.frame(2, (230, 226, 216), 0.9)
    F.stain(t, 50, 12, 6, (170, 140, 80), 0.3)
    t.grime(0.25)
    return t


NOTES = ["CLOSED", "NO FOOD|LEFT|DONT|BREAK IN", "GONE NORTH|GOD BLESS|- MARGE"]


def note(n):
    t = F.plain(9350 + n, (240, 238, 228), 0.03, 0.2)
    t.alpha[:] = 0
    area(t, 8, 6, 56, 58, alpha=255)
    m = F.text_mask(NOTES[n], MARKER, 9 if n else 13, 46, 48, jitter=0.4, seed=n)
    F.paste(t, m, 9, 8, (20, 20, 24) if n != 1 else (170, 20, 20))
    for x, y in ((10, 6), (46, 6)):
        area(t, x, y - 2, x + 8, y + 3, (220, 214, 170), alpha=200)  # tape
    return t


def gumball(seed):
    """The gumball globe: glass full of colour."""
    t = F.plain(seed, (190, 210, 214), 0.03, 0.95)
    rng = t.rng
    cols = [(220, 40, 40), (250, 200, 40), (60, 160, 230), (240, 240, 236), (60, 190, 90), (230, 110, 190)]
    for _ in range(60):
        disc(t, rng.uniform(0, N), rng.uniform(16, N), 3, cols[rng.integers(len(cols))], dh=0.3, smooth=0.8)
    t.alpha[:] = 235
    return t


def cig_face(seed, part):
    """The cigarette machine: knobs and coin slot below, the windows of packs above."""
    if part == "lo":
        t = F.wood(seed, "walnut")
        for x in range(6, 60, 9):
            area(t, x, 8, x + 6, 14, (210, 212, 214), dh=0.5, f0=233)
        area(t, 26, 30, 38, 40, (190, 192, 194), dh=0.3, f0=233)
        area(t, 30, 33, 34, 34, (20, 20, 20))
        area(t, 10, 50, 54, 58, (40, 40, 40), dh=-0.3)
    else:
        t = F.plain(seed, (40, 40, 44), 0.03, 0.6)
        rng = t.rng
        for y in (10, 34):
            for x in range(4, 60, 9):
                col = ((236, 236, 230), (190, 30, 30), (40, 70, 150), (60, 140, 80), (220, 180, 50))[rng.integers(5)]
                area(t, x, y, x + 7, y + 18, col)
                area(t, x, y, x + 7, y + 6, mixc(col, WHITE, 0.5))
        m = F.text_mask("CIGARETTES", SANS, 6, 56, 8, seed=5)
        F.paste(t, m, 4, 0, (230, 200, 90))
    t.grime(0.3)
    return t


def tickets(seed):
    """Order slips on the rail, green and white, scrawled."""
    t = F.plain(seed, (210, 212, 214), 0.02, 0.8)
    t.alpha[:] = 0
    area(t, 0, 0, N, 4, (210, 212, 214), alpha=255, f0=230)
    rng = t.rng
    for x in range(2, N - 10, 12):
        if rng.random() < 0.7:
            area(t, x, 4, x + 9, 4 + rng.uniform(16, 26), (220, 236, 216) if rng.random() < 0.5 else (240, 240, 236), alpha=255)
            for y in range(8, 24, 3):
                area(t, x + 1, y, x + rng.uniform(3, 8), y + 0.6, (40, 40, 90), alpha=255)
    return t


def shards_plate(seed):
    t = F.plain(seed, (236, 234, 226), 0.02, 0.8)
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(14):
        x, y = rng.uniform(4, 60, 2)
        r = rng.uniform(1.5, 5)
        m = F.blob_mask(rng, x, y, r, 1.2, False) > 0.5
        t.alpha[m] = 255
        if rng.random() < 0.4:
            t.c[m] = (60, 80, 150)
    area(t, 20, 40, 34, 42, (200, 202, 204), alpha=255, f0=233)
    return t


def menus_floor(seed):
    t = F.plain(seed, (190, 30, 34), 0.03, 0.8)
    t.alpha[:] = 0
    rng = t.rng
    for k in range(3):
        x, y = rng.uniform(4, 30), rng.uniform(4, 36)
        area(t, x, y, x + 22, y + 26, (190, 30, 34) if k != 1 else (30, 50, 120), alpha=255)
        area(t, x + 3, y + 4, x + 19, y + 8, (240, 230, 200), alpha=255)
    return t


def curtain(seed):
    t = F.plain(seed, (226, 226, 214), 0.04, 0.5)
    xx = np.arange(N, dtype=np.float32)[None, :]
    t.c *= (0.85 + 0.2 * np.sin(xx / 6 * np.pi) ** 2)[..., None]
    t.mix(np.clip((np.arange(N)[:, None] - 44) / 20, 0, 1) * np.ones((1, N)) * P.fbm(t.rng), (60, 70, 50), 0.6)
    return t


def cb_face(seed):
    """The trucker's shelf: CB sets with their meters, whip antennas, caps."""
    t = F.plain(seed, (150, 120, 80), 0.04, 0.3)
    for y in range(4, N, 6):
        for x in range(4, N, 6):
            disc(t, x, y, 0.8, (60, 50, 40))
    rng = t.rng
    for x in (4, 24, 44):
        area(t, x, 30, x + 16, 40, (30, 30, 32), dh=0.4)
        area(t, x + 2, 32, x + 7, 36, (220, 200, 120))
        disc(t, x + 12, 35, 1.6, (180, 180, 180))
    for x in (8, 30, 52):
        area(t, x, 2, x + 1, 28, (200, 200, 200))
    for x in (6, 36):
        disc(t, x + 8, 50, 7, ((30, 60, 120), (180, 40, 40))[int(rng.integers(2))], dh=0.3)
    t.grime(0.2)
    return t


def scale_deck(seed):
    t = F.plain(seed, (110, 112, 112), 0.05, 0.4)
    t.f0[:] = 230
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= np.where(((xx + yy) % 6 < 2) & ((xx - yy) % 6 < 2), 1.15, 1.0)[..., None]
    area(t, 0, 0, N, 3, (230, 190, 40), f0=40)
    weather(t, rust=1.0, streak=0.2, grime=0.4)
    return t


def textures():
    T = {}
    for k in CARS:
        T[f"dn_body_{k}"] = lambda k=k: body(k, 9400 + ord(k))
        for i in range(6):
            T[f"dn_name_{k}_{i}"] = lambda k=k, i=i: name(k, i)
        for i in range(2):
            T[f"dn_svc_{k}_{i}"] = lambda k=k, i=i: svc(k, i)
        T[f"dn_trim_{k}"] = lambda k=k: trim(k, 9410 + ord(k))
    T["dn_trim_s"] = lambda: trim("s", 9420)
    for i in range(6):
        T[f"dn_name_s_{i}"] = lambda i=i: moon_band(i)
    T["dn_flute"] = lambda: flute(9421)
    T["dn_flute_dent"] = lambda: flute(9422, True)
    for f in FRAMES:
        T[f"dn_frame_{f}"] = lambda f=f: frame_tex(f, 9430 + ord(f))
    for f in ("c", "g", "s", "m"):
        for part in ("lo", "hi"):
            T[f"dn_win_{f}_{part}"] = lambda f=f, part=part: window(f, part, False, 9440 + ord(f) * 4 + (part == "hi"))
            T[f"dn_win_{f}_{part}_broken"] = lambda f=f, part=part: window(f, part, True, 9441 + ord(f) * 4 + (part == "hi") * 2)
    for i in range(12):
        T[f"dn_paintwin_{i}"] = lambda i=i: paintwin(i)
    T["dn_roof_d"] = lambda: roof("d", 9460)
    T["dn_roof_r"] = lambda: roof("r", 9461)
    T["dn_roof_s"] = lambda: roof("s", 9462)
    T["dn_vault_wood"] = lambda: vault("wood", 9463)
    T["dn_vault"] = lambda: vault("enamel", 9464)
    T["dn_glassblock"] = lambda: glassblock(9465)
    T["dn_glassblock_lit"] = lambda: glassblock(9466, True)
    T["dn_door_top"] = lambda: door_tex(9467, True)
    T["dn_door_bottom"] = lambda: door_tex(9468, False)
    for i in range(4):
        T[f"dn_tower_{i}"] = lambda i=i: tower(i, False)
        T[f"dn_tower_{i}_on"] = lambda i=i: tower(i, True)
    for i in range(2):
        T[f"dn_neon_diner_{i}"] = lambda i=i: neon_diner(i, False)
        T[f"dn_neon_diner_{i}_on"] = lambda i=i: neon_diner(i, True)
    T["dn_menucase"] = lambda: menucase(9470)
    T["dn_hours"] = lambda: hours(9471)
    T["dn_mansard"] = lambda: mansard(9480)
    T["dn_soffit"] = lambda: soffit(9481)
    T["dn_tube"] = lambda: tube(9482, False)
    T["dn_tube_on"] = lambda: tube(9483, True)
    for v in BOARDS:
        for i in range(8):
            T[f"dn_board_{v}_{i}"] = lambda v=v, i=i: board(v, i)
    for i in range(2):
        T[f"dn_cola_{i}"] = lambda i=i: cola(i)
    for i in range(6):
        T[f"dn_hang_{i}"] = lambda i=i: hang(i, False)
        T[f"dn_hang_{i}_on"] = lambda i=i: hang(i, True)
        for v in MARQUEE:
            T[f"dn_marquee_{v}_{i}"] = lambda v=v, i=i: marquee(v, i)
    T["dn_lattice"] = lambda: lattice(9490)
    T["dn_wheelstop"] = lambda: wheelstop(9491)
    T["dn_grease"] = lambda: grease(9492)
    T["dn_checker"] = lambda: checker(9500)
    T["dn_checker_worn"] = lambda: checker(9501, True)
    T["dn_quarry"] = lambda: quarry(9502)
    T["dn_quarry_grease"] = lambda: quarry(9503, True)
    T["dn_mosaic"] = lambda: mosaic(9504)
    T["dn_tile"] = lambda: tile(9505)
    T["dn_tile_crack"] = lambda: tile(9506, "crack")
    T["dn_tile_white"] = lambda: tile(9507, "white")
    T["dn_quilt"] = lambda: quilt(9508)
    T["dn_pine"] = lambda: pine(9509)
    T["dn_teal"] = lambda: paintwall(9510)
    T["dn_frp"] = lambda: frp(9511)
    for kind in ("plain", "alarm", "stain", "sprinkler"):
        T["dn_droptile" if kind == "plain" else f"dn_droptile_{kind}"] = lambda kind=kind: droptile(9512 + len(kind), kind)
    T["dn_globe"] = lambda: globe(9520, False)
    T["dn_globe_on"] = lambda: globe(9521, True)
    for k in VINYL:
        T[f"dn_vinyl_{k}"] = lambda k=k: vinyl(k, 9530 + ord(k))
        T[f"dn_vinyl_{k}_torn"] = lambda k=k: vinyl(k, 9531 + ord(k), True)
    T["dn_shell"] = lambda: shell(9540)
    for kind in ("black", "red", "marble"):
        T[f"dn_formica_{kind}"] = lambda kind=kind: formica(9541 + len(kind), kind)
    T["dn_rib"] = lambda: rib(9550)
    for kind in ("q", "t", "r"):
        T[f"dn_cfront_{kind}"] = lambda kind=kind: cfront(9551 + ord(kind), kind)
    T["dn_bb_front"] = lambda: bb_front(9560)
    T["dn_urn_face"] = lambda: urn_face(9561)
    T["dn_brewer"] = lambda: brewer(9562, False)
    T["dn_brewer_on"] = lambda: brewer(9563, True)
    T["dn_mixer"] = lambda: mixer_face(9564)
    T["dn_juice"] = lambda: juice_face(9565)
    T["dn_mugs"] = lambda: mugs(9566)
    T["dn_toaster"] = lambda: toaster(9567)
    T["dn_griddle_top"] = lambda: griddle_top(9570)
    T["dn_griddle_front"] = lambda: griddle_front(9571)
    T["dn_fryer_top"] = lambda: fryer_top(9572)
    T["dn_fryer_front"] = lambda: fryer_front(9573)
    T["dn_range_top"] = lambda: range_top(9574)
    T["dn_range_front"] = lambda: range_front(9575)
    T["dn_baffles"] = lambda: baffles(9576)
    T["dn_baffles_lamp"] = lambda: baffles(9577, True)
    T["dn_baffles_lamp_on"] = lambda: baffles(9578, True, True)
    T["dn_prep_top"] = lambda: prep_top(9580)
    T["dn_prep_bare"] = lambda: prep_top(9581, True)
    T["dn_lowboy"] = lambda: lowboy(9582)
    T["dn_sink_top"] = lambda: sink_top(9583)
    T["dn_drain_top"] = lambda: drain_top(9584)
    T["dn_dish_front"] = lambda: dish_front(9585)
    for kind in ("dry", "low", "cold"):
        T[f"dn_shelf_{kind}"] = lambda kind=kind: shelf_face(9586 + len(kind), kind)
    T["dn_walkin"] = lambda: walkin(9590)
    T["dn_walkin_door_top"] = lambda: walkin_door(9591, True)
    T["dn_walkin_door_bottom"] = lambda: walkin_door(9592, False)
    T["dn_ice_lo"] = lambda: ice_face(9593, "lo")
    T["dn_ice_hi"] = lambda: ice_face(9594, "hi")
    for i in range(6):
        T[f"dn_menuboard_{i}"] = lambda i=i: menuboard(i)
        T[f"dn_specials_{i}"] = lambda i=i: specials(i)
    for v in ("a", "b"):
        for part in ("lo", "hi"):
            T[f"dn_chalk_{v}_{part}"] = lambda v=v, part=part: chalk(v, part)
    T["dn_clock"] = lambda: clock(9600, False)
    T["dn_clock_on"] = lambda: clock(9601, True)
    for n in range(len(POSTERS)):
        T[f"dn_poster_{n}"] = lambda n=n: poster(n)
    for n in range(len(NOTES)):
        T[f"dn_note_{n}"] = lambda n=n: note(n)
    T["dn_gumball"] = lambda: gumball(9610)
    T["dn_cig_lo"] = lambda: cig_face(9611, "lo")
    T["dn_cig_hi"] = lambda: cig_face(9612, "hi")
    T["dn_tickets"] = lambda: tickets(9613)
    T["dn_shards_plate"] = lambda: shards_plate(9614)
    T["dn_menus_floor"] = lambda: menus_floor(9615)
    T["dn_curtain"] = lambda: curtain(9616)
    T["dn_sign_showers"] = lambda: plate(9617, "SHOWERS|ASK AT|REGISTER", (30, 70, 150), WHITE)
    T["dn_cb"] = lambda: cb_face(9618)
    T["dn_scale"] = lambda: scale_deck(9619)
    T["dn_enamel_red"] = lambda: weather(F.plain(9620, (200, 40, 40), 0.04, 0.7), rust=0.5, streak=0.5, grime=0.25, dents=1)
    T["dn_enamel_green"] = lambda: weather(F.plain(9621, (90, 150, 110), 0.04, 0.7), rust=0.5, streak=0.5, grime=0.25)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if boxes and rotate == "axis":
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

    chrome, black, rubber, steel, glass = "home_chrome", "home_plastic_black", "home_rubber", "fit_stainless", "sc_glass"

    def cube(front, side, up=None, down=None, south=None):
        face = {"north": front, "side": side, "up": up or side, "down": down or side}
        if south:
            face["south"] = south
        return [[0, 0, 0, 16, 16, 16, face]]

    def lit(on):
        return {"light": on} if on else {}

    # ---- the cars' walls: the outside faces north, the inside (south) is the dining room's tile
    for k in CARS:
        add(f"dn_body_{k}", cube(f"dn_body_{k}", f"dn_body_{k}", south="dn_tile"), "Diner Car", "METAL")
        for i in range(6):
            add(f"dn_name_{k}_{i}", cube(f"dn_name_{k}_{i}", f"dn_body_{k}", south="dn_tile"), "Diner Car", "METAL")
        for i in range(2):
            add(f"dn_svc_{k}_{i}", cube(f"dn_svc_{k}_{i}", f"dn_body_{k}", south="dn_tile"), "Diner Car", "METAL")
    # the back wall behind the line: the car outside, diamond plate inside
    for k in CARS:
        add(f"dn_backq_{k}", cube(f"dn_body_{k}", f"dn_body_{k}", south="dn_quilt"), "Diner Car", "METAL")
    add("dn_backq_s", cube("dn_flute", "dn_trim_s", south="dn_quilt"), "Diner Car", "METAL")
    add("dn_flute", cube("dn_flute", "dn_trim_s", south="dn_tile"), "Diner Car", "METAL")
    add("dn_flute_dent", cube("dn_flute_dent", "dn_trim_s", south="dn_tile"), "Diner Car", "METAL")
    for i in range(6):
        add(f"dn_name_s_{i}", cube(f"dn_name_s_{i}", "dn_trim_s", south="dn_tile"), "Diner Car", "METAL")
    # the rounded corner posts
    for k in list(CARS) + ["s"]:
        t = f"dn_trim_{k}"
        add(f"dn_corner_{k}", [[2, 0, 2, 14, 16, 14, t], [1, 0, 3, 2, 16, 13, t], [14, 0, 3, 15, 16, 13, t], [3, 0, 1, 13, 16, 2, t],
                               [3, 0, 14, 13, 16, 15, t]], "Diner Car", "METAL", rotate=None)
    # the sash windows: the pane in the middle of the wall, the frame round it
    for f in ("c", "g", "s", "m"):
        fr = f"dn_frame_{f}"
        for part in ("lo", "hi"):
            for b in ("", "_broken"):
                n = f"dn_win_{f}_{part}{b}"
                boxes = [[0, 0, 6, 16, 16, 10, {"north": n, "south": n, "side": fr, "up": fr, "down": fr}]]
                if part == "lo":
                    boxes.append([0, 0, 4.5, 16, 1.5, 11.5, fr])  # the sill
                add(n, boxes, "Window", "GLASS", rotate="axis", render="translucent")
    for i in range(12):
        n = f"dn_paintwin_{i}"
        boxes = [[0, 0, 6, 16, 16, 10, {"north": n, "south": n, "side": "dn_frame_m", "up": "dn_frame_m", "down": "dn_frame_m"}]]
        add(n, boxes, "Window", "GLASS", rotate="facing", render="translucent")

    # ---- the roofs: the eave over the wall (facing out), the deck, the crown stepping up the barrel
    roofs = {"w": ("dn_roof_d", "dn_vault_wood"), "b": ("dn_roof_d", "dn_vault_wood"), "r": ("dn_roof_r", "dn_vault_wood"), "s": ("dn_roof_s", "dn_vault")}
    for k, (r, v) in roofs.items():
        t = f"dn_trim_{k}"
        fascia = {"north": t, "side": t, "up": r, "down": t}
        eave = [[0, 0, -4, 16, 3, 16, fascia], [0, 3, -2, 16, 7, 16, r], [0, 7, 1, 16, 11, 16, r], [0, 11, 5, 16, 14, 16, r],
                [0, 14, 10, 16, 16, 16, r]]
        add(f"dn_eave_{k}", eave, "Diner Roof", "METAL")
        if k == "s":
            # the neon along the stainless car's eave, blue over red
            tubes_on = [[0, 0.5, -4.6, 16, 1.3, -4, "dn_tube_on"], [0, 2, -4.6, 16, 2.6, -4, "dn_tube_on"]]
            add("dn_eave_s_on", eave + tubes_on, "Diner Roof", "METAL", light=9)
        add(f"dn_roof_{k}", None, "Diner Roof", "METAL", rotate=None, tex={"up": r, "side": r, "down": v})
    for r in ("dn_roof_d", "dn_roof_r", "dn_roof_s"):
        key = r[-1]
        add(f"dn_crown_a_{key}", [[0, 0, 0, 16, 3, 16, r], [0, 3, 8, 16, 6, 16, r]], "Diner Roof", "METAL")
        add(f"dn_crown_b_{key}", [[0, 0, 0, 16, 7, 16, r], [0, 7, 8, 16, 9, 16, r]], "Diner Roof", "METAL")
        add(f"dn_crown_c_{key}", [[0, 0, 0, 16, 10, 16, r]], "Diner Roof", "METAL", rotate=None)
    # Miss Worcester's striped awning over the windows (facing out), its scalloped valance
    for torn in ("", "_torn"):
        boxes = [[0, 12, 6, 16, 16, 16, "awning_blue"], [0, 9, 0, 16, 12, 6, "awning_blue"], [0, 6, -4, 16, 9, 0, "awning_blue"],
                 [0, 3, -5, 16, 6, -4, "awning_blue"]]
        if torn:
            boxes = boxes[:2] + [[0, 6, -4, 7, 9, 0, "awning_blue"], [2, -4, -2, 6, 6, -1.4, "awning_blue"]]
        add(f"dn_awning{torn}", boxes, "Awning", "WOOL", decor=True)
    add("dn_glassblock", None, "Glass Block", "GLASS", rotate=None, tex={"all": "dn_glassblock"}, render="translucent")
    add("dn_glassblock_lit", None, "Glass Block", "GLASS", rotate=None, tex={"all": "dn_glassblock_lit"}, render="translucent", light=6)
    out["dn_door"] = {"door": "steel", "tex": {"top": "dn_door_top", "bottom": "dn_door_bottom"}, "title": "Diner Door"}
    # the tower sign: the front shows tile i, the back the tile seen from behind
    for i in range(4):
        r, c = divmod(i, 2)
        back = r * 2 + (1 - c)
        for s in ("", "_on"):
            add(f"dn_tower_{i}{s}", [[0, 0, 0, 16, 16, 16, {"north": f"dn_tower_{i}{s}", "south": f"dn_tower_{back}{s}", "side": "dn_trim_s",
                                                         "up": "dn_trim_s", "down": "dn_trim_s"}]], "Diner Sign", "METAL", **lit(9 if s and r == 0 else 0))
    for i in range(2):
        for s in ("", "_on"):
            add(f"dn_neon_diner_{i}{s}", [[0, 0, 15, 16, 16, 16, f"dn_neon_diner_{i}{s}"]], "Neon Sign", "GLASS", decor=True, render="cutout",
                **lit(10 if s else 0))
    add("dn_menucase", [[2, 2, 14.5, 14, 14, 16, {"north": "dn_menucase", "side": "home_wood_walnut", "up": "home_wood_walnut", "down": "home_wood_walnut"}]],
        "Menu Case", "WOOD", decor=True)
    add("dn_hours", [[3, 2, 15.4, 13, 15, 16, "dn_hours"]], "Sign", "WOOD", decor=True)
    # a pipe handrail along the side of the steps (the open side north, the rail at the block's west edge)
    pipe = "painted_steel"
    add("dn_rail", [[0.5, 0, 7, 2, 14, 9, pipe], [0.5, 13, 0, 2, 14.5, 16, pipe]], "Handrail", "METAL")
    add("dn_rail_slope", [[0.5, 0, 7, 2, 10, 9, pipe], [0.5, 9, 0, 2, 10.5, 5, pipe], [0.5, 10.5, 5, 2, 12, 10, pipe], [0.5, 12, 10, 2, 13.5, 16, pipe]],
        "Handrail", "METAL")

    # ---- the brick cafe's outside
    ms = {"north": "dn_mansard", "side": "dn_mansard", "up": "dn_mansard", "down": "dn_soffit"}
    add("dn_mansard", [[0, 0, 0, 16, 4, 16, ms], [0, 4, 3, 16, 8, 16, ms], [0, 8, 6, 16, 12, 16, ms], [0, 12, 9, 16, 16, 16, ms]], "Mansard", "METAL")
    add("dn_mansard_corner", [[0, 0, 0, 16, 4, 16, ms], [3, 4, 3, 16, 8, 16, ms], [6, 8, 6, 16, 12, 16, ms], [9, 12, 9, 16, 16, 16, ms]], "Mansard", "METAL")
    add("dn_soffit", None, "Soffit", "WOOD", rotate=None, tex={"down": "dn_soffit", "side": "dn_mansard", "up": "dn_mansard"})
    for s in ("", "_on"):
        add(f"dn_soffit_tube{s}", [[0, 0, 0, 16, 16, 16, {"down": "dn_soffit", "side": "dn_mansard", "up": "dn_mansard"}],
                                   [0, -2, 6, 16, 0, 10, "home_white"], [0, -2.6, 7.2, 16, -2, 8.8, f"dn_tube{s}"]],
            "Soffit Light", "WOOD", rotate="axis", **lit(12 if s else 0))
    for v in BOARDS:
        for i in range(8):
            add(f"dn_board_{v}_{i}", [[0, 0, 12, 16, 16, 16, {"north": f"dn_board_{v}_{i}", "side": "dn_mansard", "up": "dn_mansard",
                                                                "down": "dn_mansard"}]], "Sign", "WOOD", decor=True)
    for i in range(2):
        add(f"dn_cola_{i}", [[0, 1, 14, 16, 15, 16, {"north": f"dn_cola_{i}", "side": "gs_steel", "up": "gs_steel", "down": "gs_steel"}]],
            "Sign", "METAL", decor=True)
    for i in range(6):
        r, c = divmod(i, 3)
        back = r * 3 + (2 - c)
        for s in ("", "_on"):
            add(f"dn_hang_{i}{s}", [[0, 0, 6, 16, 16, 10, {"north": f"dn_hang_{i}{s}", "south": f"dn_hang_{back}{s}", "side": "gs_steel",
                                                          "up": "gs_steel", "down": "gs_steel"}]], "Sign", "METAL", **lit(8 if s and c == 1 else 0))
        for v in MARQUEE:
            add(f"dn_marquee_{v}_{i}", [[0, 0, 5, 16, 16, 11, {"north": f"dn_marquee_{v}_{i}", "south": "dn_trim_s", "side": black, "up": black,
                                                              "down": black}]], "Letter Board", "METAL")
    add("dn_marquee_leg", [[1, 0, 6, 3, 16, 10, black], [13, 0, 6, 15, 16, 10, black]], "Letter Board", "METAL")
    add("dn_mast", [[6.5, 0, 6.5, 9.5, 16, 9.5, "ext_galv"]], "Sign Pole", "METAL", rotate=None)
    add("dn_mast_arm", [[0, 12, 7, 16, 13.5, 9, "ext_galv"], [0, 6, 7.2, 16, 7, 8.8, "ext_galv"], [2, 7, 7.5, 3, 12, 8.5, "ext_galv"],
                        [13, 7, 7.5, 14, 12, 8.5, "ext_galv"], [7.5, 0, 7.6, 8.5, 6, 8.4, "ext_galv"]], "Sign Arm", "METAL", rotate="axis")
    add("dn_lattice", [[0, 0, 7, 16, 16, 9, {"north": "dn_lattice", "south": "dn_lattice", "side": "home_white", "up": "home_white", "down": "home_white"}]],
        "Lattice", "WOOD", rotate="axis", render="cutout")
    add("dn_wheelstop", [[1, 0, 5, 15, 2.5, 11, "dn_wheelstop"], [2, 2.5, 6, 14, 3, 10, "dn_wheelstop"]], "Wheel Stop", "STONE", rotate="axis", decor=True)
    add("dn_grease", [[2, 0, 2, 14, 14, 14, {"north": "dn_grease", "side": "dn_grease", "up": black}], [1.5, 14, 1.5, 14.5, 15, 14.5, black]],
        "Grease Bin", "METAL")
    # the cafe's walls: brick or block outside (south), the dining room's or kitchen's face inside (north)
    for ext in ("brick_red", "sh_cmu_tan"):
        e = "brick" if ext == "brick_red" else "cmu"
        for inner in ("tile", "pine", "quilt", "frp", "teal", "tile_white"):
            add(f"dn_wall_{e}_{inner}", cube(f"dn_{inner}", ext), "Wall", "STONE")
    # the inside partitions: one face each side
    add("dn_part_frp_teal", cube("dn_frp", "dn_teal", south="dn_teal"), "Wall", "STONE")
    add("dn_part_frp_pine", cube("dn_frp", "dn_pine", south="dn_pine"), "Wall", "STONE")
    add("dn_part_quilt_tile", cube("dn_quilt", "dn_tile", south="dn_tile"), "Wall", "STONE")
    add("dn_part_tile", cube("dn_tile_white", "dn_tile_white"), "Wall", "STONE", rotate=None)
    for k in ("dn_quilt", "dn_tile", "dn_tile_crack", "dn_pine", "dn_teal", "dn_frp", "dn_walkin", "dn_tile_white"):
        add(k, None, "Wall", "METAL" if k in ("dn_quilt", "dn_walkin") else "STONE", rotate=None, tex={"all": k})
    add("dn_enamel_red", None, "Enamel Panel", "METAL", rotate=None, tex={"all": "dn_enamel_red"})

    # ---- floors and ceilings
    for k in ("dn_checker", "dn_checker_worn", "dn_quarry", "dn_quarry_grease", "dn_mosaic", "dn_scale"):
        add(k, None, "Floor Tile", "STONE", rotate=None, tex={"up": k, "side": "floor_concrete", "down": "floor_concrete"})
    for kind in ("", "_alarm", "_stain", "_sprinkler"):
        add(f"dn_droptile{kind}", None, "Ceiling Tile", "WOOL", rotate=None, tex={"down": f"dn_droptile{kind}", "side": "drywall", "up": "drywall"})
    add("dn_droptile_gone", [[0, 15, 0, 16, 16, 2, "dn_droptile"], [0, 15, 14, 16, 16, 16, "dn_droptile"], [0, 15, 2, 2, 16, 14, "dn_droptile"],
                             [14, 15, 2, 16, 16, 14, "dn_droptile"], [2, 2, 3, 3, 15, 4, "fit_stainless"], [9, 6, 6, 10, 15, 7, "home_rubber"]],
        "Ceiling Tile", "WOOL", rotate=None)
    for s in ("", "_on"):
        g = f"dn_globe{s}"
        add(f"dn_globe{s}", [[7.5, 11, 7.5, 8.5, 16, 8.5, chrome], [6, 10, 6, 10, 11, 10, chrome], [4.5, 3, 4.5, 11.5, 10, 11.5, g],
                            [5.5, 2, 5.5, 10.5, 3, 10.5, g]], "Ceiling Light", "GLASS", rotate=None, decor=True, **lit(13 if s else 0))
        # the cage-globe pendant: the bands of a sphere round a shade
        cage = "home_white"
        add(f"dn_orb{s}", [[7.6, 12, 7.6, 8.4, 16, 8.4, chrome], [7, 11.5, 7, 9, 12, 9, chrome], [6, 5, 6, 10, 9, 10, g],
                           [2, 1, 7.6, 14, 13, 8.4, cage], [7.6, 1, 2, 8.4, 13, 14, cage], [3, 6.6, 3, 13, 7.4, 13, cage]],
            "Pendant Light", "GLASS", rotate=None, decor=True, render="cutout", **lit(12 if s else 0))

    # ---- the booths: seat toward the facing, the high back behind (south), the shell grey round it
    for k in VINYL:
        for torn in ("", "_torn"):
            v = f"dn_vinyl_{k}{torn}"
            back = {"north": f"dn_vinyl_{k}", "side": "dn_shell", "up": "dn_shell", "down": "dn_shell", "south": "dn_shell"}
            add(f"dn_booth_{k}{torn}", [[0.5, 0, 2.5, 15.5, 4, 12.5, black], [0, 4, 1.5, 16, 7.5, 12.5, {"north": v, "up": v, "side": v, "down": black}],
                                         [0, 0, 12.5, 16, 20, 16, "dn_shell"], [0.5, 7.5, 10.5, 15.5, 18.5, 12.5, back]],
                "Booth", "WOOL")
    # the table: black laminate in its ribbed chrome edge, on one post; set, left in a mess, or blood
    top = {"up": "dn_formica_black", "down": "dn_formica_black", "side": "dn_rib"}
    table = [[0, 11, 0, 16, 12.5, 16, top], [7, 0, 7, 9, 11, 9, chrome], [4, 0, 4, 12, 0.6, 12, chrome]]
    setting = [[1, 12.5, 1, 4, 16, 3, chrome], [1.5, 12.5, 3.5, 2.5, 16.5, 4.5, "dn_enamel_red"], [3, 12.5, 3.5, 4, 16, 4.5, "fit_yellow"],
               [5, 12.5, 1.5, 6, 14.5, 2.5, "home_white"], [6.5, 12.5, 1.5, 7.5, 14.5, 2.5, black], [8.5, 12.5, 1, 11, 15, 2.5, glass],
               [11, 12.5, 0.5, 15, 17, 3, "dn_trim_s"], [11.5, 15, 0.4, 14.5, 16.5, 0.6, "dn_enamel_red"]]
    add("dn_table", table, "Table", "WOOD")
    add("dn_table_set", table + setting, "Table", "WOOD")
    mess = [[2, 12.5, 5, 7, 13, 10, "home_white"], [3, 13, 6, 6, 13.4, 9, "dn_griddle_top"], [9, 12.5, 8, 14, 13, 13, "home_white"],
            [10, 12.5, 4, 12, 15, 6, "home_white"], [12.5, 12.5, 3, 15.5, 13, 5, "home_white"], [5, 12.5, 11, 11, 12.6, 12, chrome],
            [8, 12.5, 2, 9, 14.5, 3, glass]]
    add("dn_table_mess", table + setting[:5] + mess, "Table", "WOOD")
    add("dn_table_blood", table + mess[:3] + [[1, 12.5, 2, 15, 12.55, 14, "home_blood_pool"]], "Table", "WOOD")
    for k in VINYL:
        v = f"dn_vinyl_{k}"
        add(f"dn_stool_{k}", [[4, 0, 4, 12, 0.6, 12, chrome], [7.2, 0.6, 7.2, 8.8, 9, 8.8, chrome], [3.5, 9, 3.5, 12.5, 10, 12.5, chrome],
                              [4, 10, 4, 12, 12, 12, {"up": v, "side": v, "down": chrome}]], "Stool", "METAL", rotate=None)
    add("dn_stool_torn", [[4, 0, 4, 12, 0.6, 12, chrome], [7.2, 0.6, 7.2, 8.8, 9, 8.8, chrome], [3.5, 9, 3.5, 12.5, 10, 12.5, chrome],
                          [4, 10, 4, 12, 12, 12, {"up": "dn_vinyl_r_torn", "side": "dn_vinyl_r", "down": chrome}]], "Stool", "METAL", rotate=None)
    add("dn_stool_stub", [[4, 0, 4, 12, 0.6, 12, chrome], [7.2, 0.6, 7.2, 8.8, 9, 8.8, chrome], [3.5, 9, 3.5, 12.5, 10, 12.5, chrome]],
        "Stool", "METAL", rotate=None)

    # ---- the counter: its front toward the stools (north), the top overhanging it, the foot rail
    tops = {"q": "dn_formica_red", "t": "dn_formica_marble", "r": "dn_formica_red"}
    for k, tp in tops.items():
        f = f"dn_cfront_{k}"
        body_ = [[0, 0, 2, 16, 14, 16, {"north": f, "side": f, "up": tp, "down": f}], [0, 14, -2, 16, 15.5, 16, {"up": tp, "down": tp, "side": "dn_rib"}],
                 [0, 1.5, -0.5, 16, 2.5, 0.5, chrome], [3, 1.5, 0.5, 4, 2.5, 2, chrome], [12, 1.5, 0.5, 13, 2.5, 2, chrome]]
        add(f"dn_counter_{k}", body_, "Counter", "METAL", loot="kitchen")
        # the end: rounded off toward the east
        end = [[0, 0, 2, 12, 14, 16, {"north": f, "side": f, "up": tp, "down": f}], [12, 0, 4, 14, 14, 14, f], [14, 0, 6, 15, 14, 12, f],
               [0, 14, -2, 13, 15.5, 16, {"up": tp, "down": tp, "side": "dn_rib"}], [13, 14, 0, 15, 15.5, 15, {"up": tp, "down": tp, "side": "dn_rib"}],
               [15, 14, 3, 16, 15.5, 12, {"up": tp, "down": tp, "side": "dn_rib"}], [0, 1.5, -0.5, 12, 2.5, 0.5, chrome]]
        add(f"dn_counter_{k}_end", end, "Counter", "METAL", loot="kitchen")
    # on the counter (placed over it, so a box at y 0 sits half a pixel above its top)
    ct = {
        "register": ([[3, -0.5, 4, 13, 6, 13, "dn_trim_s"], [4, 6, 8, 12, 10, 13, "dn_trim_s"], [5, 10, 9, 11, 12, 10, black],
                      [3.5, 2, 3.5, 12.5, 5, 4, "fit_brass"]], "Cash Register", "till"),
        "cake": ([[3, -0.5, 3, 13, 1, 13, chrome], [7, 1, 7, 9, 3, 9, chrome], [4, 3, 4, 12, 4, 12, chrome], [5, 4, 5, 11, 8, 11, "dn_enamel_green"],
                  [4.5, 4, 4.5, 11.5, 11, 11.5, glass], [7.5, 11, 7.5, 8.5, 12, 8.5, chrome]], "Cake Stand", None),
        "pies": ([[1, -0.5, 2, 15, 1, 14, chrome], [1.5, 1, 2.5, 14.5, 13, 13.5, glass], [1.5, 6.5, 2.5, 14.5, 7, 13.5, chrome],
                  [3, 1, 4, 8, 3, 9, "dn_enamel_red"], [9, 1, 5, 14, 3, 12, "dn_griddle_top"], [3, 7, 4, 13, 9, 12, "dn_enamel_red"],
                  [1, 13, 2, 15, 14, 14, chrome]], "Pie Case", "kitchen"),
        "napkins": ([[2, -0.5, 6, 6, 4, 9, chrome], [7, -0.5, 6, 8, 5, 7, "dn_enamel_red"], [8.5, -0.5, 6, 9.5, 5, 7, "fit_yellow"],
                     [10.5, -0.5, 6, 11.5, 3, 7, "home_white"], [12, -0.5, 6, 13, 3, 7, black], [13.5, -0.5, 5, 15, 3.5, 7, glass]], "Napkins", None),
        "mess": ([[1, -0.5, 3, 7, 0.2, 9, "home_white"], [2, 0.2, 4, 6, 0.5, 8, "dn_griddle_top"], [9, -0.5, 4, 12, 3, 7, "home_white"],
                  [12, 0.5, 5, 13, 2, 6, "home_white"], [8, -0.5, 9, 15, 0.2, 13, "home_white"], [3, -0.5, 10, 8, 0, 11, chrome]], "Dishes", None),
        "donuts": ([[2, -0.5, 3, 14, 7, 13, {"north": glass, "side": glass, "up": glass, "down": chrome}], [3, -0.4, 4, 7, 1.5, 8, "dn_trim_r"],
                    [8, -0.4, 4, 12, 1.5, 8, "dn_enamel_red"], [5, -0.4, 8.5, 9, 1.5, 12, "dn_trim_r"]], "Donut Case", "kitchen"),
        "menus": ([[3, -0.5, 8, 13, 4, 10, chrome], [3.5, 0, 8.5, 12.5, 9, 9.5, "dn_enamel_red"], [4, 0, 9.5, 12, 8, 10, "dn_trim_b"]], "Menus", None),
    }
    for k, (boxes, title, loot) in ct.items():
        add(f"dn_ct_{k}", boxes, title, "METAL", decor=True, loot=loot)

    # ---- the back bar and what stands on it
    bb = {"north": "dn_bb_front", "side": steel, "up": steel, "down": steel}
    add("dn_backbar", [[0, 0, 1, 16, 15, 16, bb], [0, 15, 0, 16, 16, 16, steel], [0, 0, 0.5, 16, 1, 1, black]], "Back Bar", "METAL", loot="kitchen")
    urn = {"north": "dn_urn_face", "side": chrome, "up": chrome, "down": chrome}
    add("dn_urn", [[1, 0, 5, 7, 14, 11, urn], [9, 0, 5, 15, 14, 11, urn], [2, 14, 6, 6, 15, 10, chrome], [10, 14, 6, 14, 15, 10, chrome],
                   [3.5, 3, 3, 4.5, 4, 5, black], [11.5, 3, 3, 12.5, 4, 5, black]], "Coffee Urns", "METAL")
    for s in ("", "_on"):
        add(f"dn_brewer{s}", [[1, 0, 6, 15, 14, 15, {"north": f"dn_brewer{s}", "side": black, "up": steel}], [1, 14, 4, 15, 15.5, 15, steel],
                              [2, 0, 3, 7, 6, 6, glass], [9, 0, 3, 14, 6, 6, glass]], "Coffee Brewer", "METAL", **lit(4 if s else 0))
    add("dn_mixer", [[1, 0, 8, 15, 14, 14, {"north": "dn_mixer", "side": "dn_enamel_green", "up": "dn_enamel_green"}],
                     [1, 10, 4, 15, 13, 8, "dn_enamel_green"], [2, 0, 4, 5, 5, 7, steel], [6.5, 0, 4, 9.5, 5, 7, steel], [11, 0, 4, 14, 5, 7, steel]],
        "Milkshake Mixer", "METAL")
    add("dn_juice", [[2, 0, 4, 14, 13, 13, {"north": "dn_juice", "side": "home_white", "up": glass}]], "Juice Dispenser", "METAL", loot="drinks")
    add("dn_mugs", [[0, 0, 11, 16, 16, 16, {"north": "dn_mugs", "side": "home_wood_dark", "up": "home_wood_dark", "down": "home_wood_dark"}]],
        "Mug Shelf", "WOOD", render="cutout", decor=True)
    add("dn_toaster", [[2, 0, 4, 14, 10, 13, {"north": "dn_toaster", "side": steel, "up": steel}]], "Toaster", "METAL")
    # the line: griddle, fryer, range (the cook faces north, the counter behind him)
    fit = {"griddle": ("dn_griddle_front", "dn_griddle_top"), "fryer": ("dn_fryer_front", "dn_fryer_top"), "range": ("dn_range_front", "dn_range_top")}
    for k, (front, topt) in fit.items():
        boxes = [[0, 1, 0.5, 16, 14.5, 15, {"north": front, "side": steel, "up": topt, "down": steel}], [0, 14.5, 13, 16, 22, 16, "dn_quilt"],
                 [1, 0, 1, 2, 1, 2, black], [14, 0, 1, 15, 1, 2, black], [1, 0, 13, 2, 1, 14, black], [14, 0, 13, 15, 1, 14, black]]
        if k == "fryer":
            boxes += [[2, 14.5, 9, 7, 18, 12, "fit_stainless"], [9, 14.5, 9, 14, 18, 12, "fit_stainless"]]
        add(f"dn_{k}", boxes, k.title(), "METAL", loot="kitchen" if k == "range" else None)
    # the hood hung over the line, its filters underneath (a lamp in them)
    for s in ("", "_on"):
        und = "dn_baffles_lamp" + s
        add(f"dn_hood{s}", [[0, 6, -2, 16, 16, 16, {"north": steel, "side": steel, "up": steel, "down": und}], [0, 4, -2.5, 16, 6, -1.5, steel]],
            "Exhaust Hood", "METAL", **lit(10 if s else 0))
    # the pass through the kitchen wall: the shelf both sides, the heat lamp over it, the tickets
    for s in ("", "_on"):
        lamp = "dn_tube_on" if s else "dn_enamel_red"
        add(f"dn_pass{s}", [[0, 0, -3, 16, 1.5, 19, steel], [0, 14, 0, 16, 16, 16, steel], [1, 12.5, 5, 15, 14, 11, lamp],
                            [0, 9, -0.5, 16, 14, 0, "dn_tickets"], [0, 13.5, -1, 16, 14, 0.5, chrome]],
            "Pass Window", "METAL", render="cutout", **lit(7 if s else 0))
    # the kitchen's own
    for k, tp in (("prep", "dn_prep_top"), ("prep_bare", "dn_prep_bare")):
        add(f"dn_{k}", [[0, 13, 0, 16, 14.5, 16, {"up": tp, "side": steel, "down": steel}], [0.5, 0, 0.5, 2, 13, 2, steel], [14, 0, 0.5, 15.5, 13, 2, steel],
                        [0.5, 0, 14, 2, 13, 15.5, steel], [14, 0, 14, 15.5, 13, 15.5, steel], [1, 3, 1, 15, 4, 15, steel],
                        [3, 4, 3, 9, 8, 9, "store_appliance"], [10, 4, 4, 14, 6, 12, "fit_pans"]], "Prep Table", "METAL", loot="kitchen")
    add("dn_lowboy", [[0, 1, 0.5, 16, 14.5, 16, {"north": "dn_lowboy", "side": steel, "up": "dn_prep_top", "down": steel}]], "Refrigerator",
        "METAL", loot="kitchen")
    add("dn_sink", [[0, 0, 1, 16, 14, 16, {"north": steel, "side": steel, "up": "dn_sink_top"}], [0, 14, 13, 16, 20, 16, steel],
                    [7, 14, 12, 9, 26, 14, chrome], [7, 24, 4, 9, 25.5, 13, chrome], [7.4, 18, 4, 8.6, 24, 5.2, black]], "Sink", "METAL")
    add("dn_drain", [[0, 0, 1, 16, 14, 16, {"north": steel, "side": steel, "up": "dn_drain_top"}], [0, 14, 13, 16, 20, 16, steel],
                     [2, 14, 3, 14, 21, 12, "fit_cabinet"]], "Drainboard", "METAL")
    add("dn_dish_lo", [[0, 0, 1, 16, 16, 16, {"north": "dn_dish_front", "side": steel, "up": steel}]], "Dish Machine", "METAL")
    add("dn_dish_hi", [[1, 0, 2, 15, 8, 15, steel], [6, 8, 6, 10, 16, 10, steel]], "Dish Machine", "METAL")
    for kind in ("dry", "low", "cold"):
        sh = f"dn_shelf_{kind}"
        face = {"north": sh, "south": sh, "side": "home_steel", "up": "home_steel", "down": "home_steel"}
        add(f"dn_{kind}" if kind != "low" else "dn_dry_low", [[1, 0, 2, 15, 16, 14, face], [0.5, 0, 1.5, 1.5, 16, 14.5, "home_steel"],
                                                              [14.5, 0, 1.5, 15.5, 16, 14.5, "home_steel"]],
            "Shelving", "METAL", loot=None if kind == "low" else "grocery", render="cutout")
    add("dn_walkin", None, "Walk-in Cooler", "METAL", rotate=None, tex={"all": "dn_walkin"})
    out["dn_walkin_door"] = {"door": "steel", "tex": {"top": "dn_walkin_door_top", "bottom": "dn_walkin_door_bottom"}, "title": "Walk-in Door"}
    add("dn_ice_lo", cube("dn_ice_lo", steel), "Ice Machine", "METAL")
    add("dn_ice_hi", [[0, 0, 1, 16, 14, 16, {"north": "dn_ice_hi", "side": steel, "up": steel}]], "Ice Machine", "METAL")
    add("dn_mop", [[3, 0, 3, 13, 7, 12, "fit_yellow"], [3, 7, 9, 13, 11, 12, "fit_yellow"], [7, 0, 7, 8, 26, 8, "home_wood_dark"],
                   [5, 0, 5.5, 10, 2, 9.5, "home_white"], [3.5, 0, 3.5, 4.5, 1, 4.5, black], [11.5, 0, 10.5, 12.5, 1, 11.5, black]],
        "Mop Bucket", "METAL", rotate=None, decor=True)

    # ---- signs, the boards, the odds and ends
    for i in range(6):
        add(f"dn_menuboard_{i}", [[0, 0, 15, 16, 16, 16, f"dn_menuboard_{i}"]], "Menu Board", "WOOD", decor=True)
        add(f"dn_specials_{i}", [[0, 2, 7, 16, 14, 9, {"north": f"dn_specials_{i}", "south": f"dn_specials_{5 - i}", "side": "home_white",
                                                      "up": "home_white", "down": "home_white"}], [7.6, 14, 7.6, 8.4, 16, 8.4, chrome]],
            "Specials Banner", "WOOD", decor=True)
    for v in ("a", "b"):
        add(f"dn_chalk_{v}_hi", [[1.5, 0, 7.5, 14.5, 16, 8.5, {"north": f"dn_chalk_{v}_hi", "south": "home_wood_dark", "side": "home_wood_dark",
                                                              "up": "home_wood_dark", "down": "home_wood_dark"}]], "Sign", "WOOD", decor=True)
        add(f"dn_chalk_{v}_lo", [[1.5, 8, 7.5, 14.5, 16, 8.5, {"north": f"dn_chalk_{v}_lo", "south": "home_wood_dark", "side": "home_wood_dark",
                                                              "up": "home_wood_dark", "down": "home_wood_dark"}],
                                 [3, 0, 8.5, 4, 8, 9.5, black], [12, 0, 8.5, 13, 8, 9.5, black], [2, 0, 6, 14, 0.6, 12, black]], "Sign", "WOOD", decor=True)
    for s in ("", "_on"):
        add(f"dn_clock{s}", [[1, 1, 14.5, 15, 15, 16, f"dn_clock{s}"]], "Clock", "METAL", decor=True, render="cutout", **lit(5 if s else 0))
    for n in range(len(POSTERS)):
        add(f"dn_poster_{n}", [[2, 1, 15.6, 14, 15, 16, f"dn_poster_{n}"]], "Poster", "WOOL", decor=True)
    # a note taped to the window glass: it stands in the room cell, its back on the pane beyond
    for n in range(len(NOTES)):
        add(f"dn_note_{n}", [[2, 3, 21.6, 14, 15, 22, {"north": f"dn_note_{n}", "south": "home_white", "side": "home_white", "up": "home_white",
                                                      "down": "home_white"}]], "Note", "WOOL", decor=True, render="cutout")
    add("dn_mugshelf", [[0, 6, 11, 16, 16, 16, {"north": "dn_mugs", "side": "home_wood_dark", "up": "home_wood_dark", "down": "home_wood_dark"}]],
        "Mug Shelf", "WOOD", decor=True, render="cutout")
    add("dn_gumball", [[5, 0, 5, 11, 0.8, 11, "dn_enamel_red"], [7.4, 0.8, 7.4, 8.6, 8, 8.6, chrome], [5, 8, 5, 11, 11, 11, "dn_enamel_red"],
                       [4.5, 11, 4.5, 11.5, 17, 11.5, "dn_gumball"], [6.5, 17, 6.5, 9.5, 18, 9.5, "dn_enamel_red"],
                       [7, 9, 4.4, 9, 10, 5, chrome]], "Gumball Machine", "GLASS", rotate=None, render="translucent")
    add("dn_cig_lo", [[1, 0, 3, 15, 16, 15, {"north": "dn_cig_lo", "side": "home_wood_walnut", "up": "home_wood_walnut"}],
                      [2, 2, 2.4, 14, 3, 3, chrome]], "Cigarette Machine", "METAL", loot="till")
    add("dn_cig_hi", [[1, 0, 3, 15, 13, 15, {"north": "dn_cig_hi", "side": "home_wood_walnut", "up": "home_wood_walnut"}],
                      [1, 13, 3, 15, 14, 15, chrome]], "Cigarette Machine", "METAL")
    add("dn_plates_broken", [[0, 0, 0, 16, 0.25, 16, "dn_shards_plate"]], "Broken Plates", "GLASS", rotate=None, decor=True, render="cutout")
    add("dn_menus_floor", [[0, 0, 0, 16, 0.25, 16, "dn_menus_floor"]], "Menus", "WOOL", decor=True, render="cutout")

    # ---- the truck stop's
    add("dn_shower", [[0, 0, 15, 16, 16, 16, "dn_tile_white"], [0, 0, 0, 1, 16, 16, "dn_tile_white"], [15, 0, 0, 16, 16, 16, "dn_tile_white"],
                      [1, 0, 1, 15, 0.4, 15, "dn_tile_white"], [7, 0.4, 7, 9, 0.5, 9, chrome], [1, 15, 0.8, 15, 15.6, 1.4, chrome],
                      [1, 2, 0.5, 9, 15, 1, "dn_curtain"], [6, 12, 13, 10, 13, 15, chrome], [7.5, 13, 14, 8.5, 16, 15, chrome]],
        "Shower", "STONE", render="cutout")
    add("dn_sign_showers", [[2, 3, 15.4, 14, 13, 16, "dn_sign_showers"]], "Sign", "METAL", decor=True)
    add("dn_cb", [[0, 0, 12, 16, 16, 16, {"north": "dn_cb", "side": "home_wood_dark", "up": "home_wood_dark", "down": "home_wood_dark"}],
                  [1, 5, 6, 15, 6, 12, "home_wood_dark"], [2, 6, 7, 7, 9, 11, black], [9, 6, 7, 14, 9, 11, black]], "CB Shop", "WOOD", loot="hardware")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("dn_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} diner blocks in {path}")


if __name__ == "__main__":
    main()
