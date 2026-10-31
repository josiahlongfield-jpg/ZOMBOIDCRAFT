"""The hw_ kit: the interstate's own furniture, for the three highway places (Josiah 2026-10-31:
the rest area, the toll plaza and the I-88 / I-495 interchange), after refs/roads/{rest-area,
toll-plaza, highway-interchange, overpass, abandoned-traffic-jam}. world/county/{RestArea,
TollPlaza, Interchange} set them out, over the roadways world/county/Lanes paints.

Every facing piece is drawn with its front to the north (z = 0) and rotate=facing, so facing is
the way the front looks:

- hw_rail / hw_rail_span / hw_rail_end / hw_rail_bent: the galvanized W-beam guardrail, the road
  to the north: on its post and blockout, the length between posts, the flared end terminal with
  its yellow-and-black end pad, and a length a car went through, bent back off its post;
- hw_parapet / hw_parapet_rail: the bridge's concrete safety-shape parapet, the deck to the north,
  plain or with the galvanized tube rail along its top; hw_deck (the slab, its formed underside),
  hw_girder (rotate=axis: a precast I-girder under the deck), hw_pier (the bent's column),
  hw_conc (the cap beam, abutments, wing walls), hw_mse (the abutment's retaining wall panels);
- hw_g_<sign>_<col><row>: the signs, one block a tile, row 0 at the bottom, col 0 at the reader's
  left: green guide signs with interstate shields, the turnpike's name on the toll
  canopy, the lane signs over the booths, the blue rest-area signs and the brown welcome board;
  hw_sign_leg: the galvanized I-post under a ground-mounted sign;
- hw_gantry_post / hw_gantry_beam (rotate=axis) / hw_gantry_foot: the overhead sign structure's
  truss legs, its truss and the concrete footing with the anchor bolts;
- the toll plaza: hw_booth_lo / hw_booth_hi (a booth's wall: stainless below, glass above, the pay
  window in the hi), hw_booth_cap (its roof), hw_booth_lamp(_lit) (the lane light on its roof),
  hw_canopy / hw_canopy_lit (the canopy's soffit, a light set in it), hw_lane_go(_lit) /
  hw_lane_stop(_lit) (the lane-use signal, green arrow or red X), hw_island (the booth's raised
  island, its striped curb), hw_cushion (the yellow crash cushion on the island's nose), hw_arm_box
  and hw_arm (rotate=axis) / hw_arm_snap (the lane gate, its arm snapped and lying), hw_coin (the
  exact-change basket), hw_ticket (the ticket machine);
- on the road: hw_drum / hw_drum_down (the orange channelizing drum, standing or knocked flat),
  hw_delineator (the white flexible post), hw_chevron (the curve's chevron on its post), hw_marker
  (the green mile marker), hw_callbox (the yellow emergency call box);
- the rest area: hw_picnic_l / hw_picnic_r (a picnic table with its benches, the two ends),
  hw_grill (the pedestal grill), hw_map_lo / hw_map_hi (the lit map case: you are here), hw_urinal,
  hw_dryer (on the restroom wall, the wall to the south as drawn), hw_bags (the pet-waste station),
  hw_ledge / hw_capstone (the welcome center's ledgestone and its cap), hw_timber_post and
  hw_timber_beam (rotate=axis).

    python3 tools/city/highway.py      rewrites the hw_ entries in blocks.json

photo_textures.py draws the textures (highway.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips  # noqa: E402

N = P.N
HERE = Path(__file__).parent
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
GALV, CONC = "st_galv", "hw_conc"
GREEN, BLUE, BROWN = (0, 108, 74), (14, 62, 140), (98, 60, 34)
PURPLE, YELLOW, ORANGE, RED = (84, 44, 124), (238, 192, 24), (232, 110, 20), (176, 24, 26)
WHITE, BLACK = (238, 238, 232), (22, 22, 22)
BLACK_TEX = "tire"

# the signs: tiles across and up, the board's colour, the lettering's, its lines (text, size in
# pixels at 64 a block, the line's middle as a share of the height from the top, font), shields
# ({number, x and y of the middle as shares, height as a share}), the columns whose posts show behind
# the board, and what's sprayed over it
SIGNS = {
    "i495n": dict(cols=5, rows=2, bg=GREEN, fg=WHITE, lines=[("NORTH", 40, 0.32, SANS), ("Pinecrest", 30, 0.72, SANS)],
                  shields=[(495, 0.17, 0.33, 0.48)], text_x=0.58),
    "i495s": dict(cols=5, rows=2, bg=GREEN, fg=WHITE, lines=[("SOUTH", 40, 0.32, SANS), ("Ashby Heights", 30, 0.72, SANS)],
                  shields=[(495, 0.17, 0.33, 0.48)], text_x=0.58),
    "i88e": dict(cols=5, rows=2, bg=GREEN, fg=WHITE, lines=[("EAST", 40, 0.32, SANS), ("Grayhaven", 30, 0.72, SANS)],
                 shields=[(88, 0.17, 0.33, 0.48)], text_x=0.58),
    "i88e_q": dict(cols=5, rows=2, bg=GREEN, fg=WHITE, lines=[("EAST", 40, 0.32, SANS), ("Grayhaven", 30, 0.72, SANS)],
                   shields=[(88, 0.17, 0.33, 0.48)], text_x=0.58, spray="QUARANTINE"),
    "i88w": dict(cols=5, rows=2, bg=GREEN, fg=WHITE, lines=[("WEST", 40, 0.32, SANS), ("Millbrook", 30, 0.72, SANS)],
                 shields=[(88, 0.17, 0.33, 0.48)], text_x=0.58),
    "exit": dict(cols=2, rows=1, bg=YELLOW, fg=BLACK, lines=[("EXIT ONLY", 26, 0.5, SANS)]),
    "ramp": dict(cols=1, rows=1, bg=YELLOW, fg=BLACK, lines=[("RAMP", 15, 0.24, SANS), ("25", 24, 0.55, SANS), ("MPH", 13, 0.84, SANS)]),
    "keep": dict(cols=1, rows=1, bg=WHITE, fg=BLACK, lines=[("KEEP", 14, 0.22, SANS), ("RIGHT", 13, 0.42, SANS), ("↘", 30, 0.75, SANS)]),
    "toll": dict(cols=10, rows=1, bg=BLUE, fg=WHITE, lines=[("GRAYHAVEN TURNPIKE", 42, 0.52, SERIF)]),
    "toll_q": dict(cols=10, rows=1, bg=BLUE, fg=WHITE, lines=[("GRAYHAVEN TURNPIKE", 42, 0.52, SERIF)], spray="NO WAY THROUGH"),
    "cash": dict(cols=2, rows=1, bg=GREEN, fg=WHITE, lines=[("CASH", 30, 0.38, SANS), ("RECEIPTS", 14, 0.78, SANS)]),
    "epass": dict(cols=2, rows=1, bg=PURPLE, fg=WHITE, lines=[("E-PASS", 30, 0.38, SANS), ("ONLY", 16, 0.78, SANS)]),
    "change": dict(cols=2, rows=1, bg=YELLOW, fg=BLACK, lines=[("EXACT", 22, 0.33, SANS), ("CHANGE", 22, 0.72, SANS)]),
    "closed": dict(cols=2, rows=1, bg=ORANGE, fg=BLACK, lines=[("LANE", 22, 0.33, SANS), ("CLOSED", 22, 0.72, SANS)]),
    "rates": dict(cols=3, rows=2, bg=WHITE, fg=BLACK, lines=[("TOLL", 34, 0.17, SANS), ("CARS   $1.50", 19, 0.42, SANS),
                                                            ("TRUCKS  $4.75", 19, 0.6, SANS), ("PER AXLE OVER 2  $1.25", 12, 0.78, SANS),
                                                            ("RECEIPTS ON REQUEST", 10, 0.92, SANS)], legs=[0, 2]),
    "plaza": dict(cols=4, rows=2, bg=GREEN, fg=WHITE, lines=[("TOLL PLAZA", 38, 0.33, SANS), ("1/2 MILE", 30, 0.72, SANS)], legs=[0, 3]),
    "pay": dict(cols=2, rows=1, bg=RED, fg=WHITE, lines=[("STOP", 26, 0.35, SANS), ("PAY TOLL", 16, 0.76, SANS)]),
    "rest": dict(cols=4, rows=2, bg=BLUE, fg=WHITE, lines=[("REST AREA", 40, 0.34, SANS), ("NEXT RIGHT", 28, 0.74, SANS)], legs=[0, 3]),
    "rest_in": dict(cols=3, rows=1, bg=BLUE, fg=WHITE, lines=[("REST AREA ↗", 32, 0.52, SANS)], legs=[1]),
    "trucks": dict(cols=2, rows=1, bg=BLUE, fg=WHITE, lines=[("← TRUCKS", 24, 0.52, SANS)], legs=[0]),
    "cars": dict(cols=2, rows=1, bg=BLUE, fg=WHITE, lines=[("CARS →", 24, 0.52, SANS)], legs=[1]),
    "welcome": dict(cols=6, rows=2, bg=BROWN, fg=(236, 226, 196), lines=[("WELCOME TO", 30, 0.27, SERIF), ("GRAYHAVEN COUNTY", 44, 0.62, SERIF),
                                                                      ("THE PINE RIDGE GATEWAY", 13, 0.88, SANS)], legs=[0, 5], wood=True),
    "welcome_q": dict(cols=6, rows=2, bg=BROWN, fg=(236, 226, 196), lines=[("WELCOME TO", 30, 0.27, SERIF), ("GRAYHAVEN COUNTY", 44, 0.62, SERIF),
                                                                        ("THE PINE RIDGE GATEWAY", 13, 0.88, SANS)], legs=[0, 5], wood=True,
                      spray="INFECTED"),
    "next": dict(cols=4, rows=2, bg=GREEN, fg=WHITE, lines=[("NEXT REST AREA", 26, 0.33, SANS), ("38 MILES", 32, 0.72, SANS)], legs=[0, 3]),
    "pet": dict(cols=1, rows=1, bg=BROWN, fg=WHITE, lines=[("PET", 20, 0.32, SANS), ("AREA", 18, 0.7, SANS)], legs=[0]),
    "info": dict(cols=2, rows=1, bg=BROWN, fg=WHITE, lines=[("VISITOR", 22, 0.33, SANS), ("INFORMATION", 16, 0.73, SANS)]),
    "rest_closed": dict(cols=3, rows=1, bg=ORANGE, fg=BLACK, lines=[("REST AREA CLOSED", 22, 0.52, SANS)], legs=[0, 2]),
    "mile": dict(cols=1, rows=1, bg=GREEN, fg=WHITE, lines=[("MILE", 14, 0.3, SANS), ("41", 28, 0.68, SANS)], legs=[0]),
}


# ---------------------------------------------------------------- the signs' art

def shield(im, cx, cy, h, number):
    """The interstate shield: the red crown band, the blue field, the white border and number."""
    d = ImageDraw.Draw(im)
    w = h * 0.92
    top, bot = cy - h / 2, cy + h / 2

    def outline(s):
        pts = []
        for i in range(21):
            a = i / 20
            pts.append((cx - w / 2 * s + w * s * a, top + h * (1 - s) / 2 + h * 0.06 * s * np.sin(np.pi * a)))
        for i in range(21):
            a = i / 20
            x = cx + w / 2 * s - w * s * a
            y = top + h * (1 - s) / 2 + h * 0.18 * s + (h * 0.82 * s) * (1 - (2 * a - 1) ** 2) ** 0.6
            pts.append((x, y))
        return pts

    d.polygon(outline(1.0), fill=WHITE + (255,))
    inner = outline(0.88)
    d.polygon(inner, fill=BLUE + (255,))
    band = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(band).polygon(inner, fill=RED + (255,))
    band_mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(band_mask).rectangle([0, 0, im.size[0], top + h * 0.3], fill=255)
    im.alpha_composite(Image.composite(band, Image.new("RGBA", im.size, (0, 0, 0, 0)), band_mask))
    d = ImageDraw.Draw(im)
    d.text((cx, top + h * 0.17), "INTERSTATE", font=ImageFont.truetype(SANS, max(5, int(h * 0.09))), fill=WHITE + (255,), anchor="mm")
    size = int(h * (0.5 if number < 100 else 0.4))
    d.text((cx, top + h * 0.58), str(number), font=ImageFont.truetype(SANS, size), fill=WHITE + (255,), anchor="mm")


def sign_art(spec):
    """The whole face, cols x rows blocks, RGBA: the board, its border and everything on it."""
    W, H = spec["cols"] * N, spec["rows"] * N
    im = Image.new("RGBA", (W, H), spec["bg"] + (255,))
    d = ImageDraw.Draw(im)
    fg = spec["fg"]
    if spec.get("wood"):
        # routed boards: the frame proud, the planks' joints across
        for y in range(N // 2, H, N // 2):
            d.line([(0, y), (W, y)], fill=tuple(int(c * 0.7) for c in spec["bg"]) + (255,), width=2)
        d.rectangle([3, 3, W - 4, H - 4], outline=(70, 42, 24, 255), width=7)
        d.rectangle([12, 12, W - 13, H - 13], outline=fg + (255,), width=2)
    else:
        d.rounded_rectangle([3, 3, W - 4, H - 4], radius=10, outline=fg + (255,), width=3)
    tx = spec.get("text_x", 0.5) * W
    for text, size, y, font in spec["lines"]:
        f = ImageFont.truetype(font, size)
        room = W - 20 if "text_x" not in spec else (W - tx) * 2 - 24
        while d.textlength(text, font=f) > room and size > 6:
            size -= 1
            f = ImageFont.truetype(font, size)
        d.text((tx, y * H), text, font=f, fill=fg + (255,), anchor="mm")
    for number, sx, sy, sh in spec.get("shields", []):
        shield(im, sx * W, sy * H, sh * H, number)
    if spec.get("spray"):
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        size = int(min(H * 0.42, W / max(4, len(spec["spray"])) * 1.25))
        ld.text((W / 2, H * 0.5), spec["spray"], font=ImageFont.truetype(SANS, size), fill=(200, 24, 20, 235), anchor="mm")
        layer = layer.rotate(-5, center=(W / 2, H / 2))
        a = np.array(layer)
        rng = np.random.default_rng(len(spec["spray"]))
        ys, xs = np.nonzero(a[:, :, 3] > 200)
        for _ in range(12 * spec["cols"]):
            k = rng.integers(len(xs))
            ln = int(rng.integers(5, H // 3))
            a[ys[k]:min(H, ys[k] + ln), xs[k]:xs[k] + 1] = (180, 24, 20, 220)
        im = Image.alpha_composite(im, Image.fromarray(a))
    return np.array(im).astype(np.float32)


ART = {}


def sign_tile(key, col, row, seed):
    """One block of a sign: its art over a faintly varied board, the reflective sheeting dulled and
    streaked, a dent or two, the paint chipped on the wooden one."""
    spec = SIGNS[key]
    if key not in ART:
        ART[key] = sign_art(spec)
    art = ART[key]
    r0, c0 = (spec["rows"] - 1 - row) * N, col * N
    a = art[r0:r0 + N, c0:c0 + N]
    t = F.plain(seed, spec["bg"], 0.04, 0.45)
    t.c = a[:, :, :3].copy()
    t.vary(0.05)
    if spec.get("wood"):
        chips(t, 0.08, under=(120, 92, 62))
        weather(t, rust=0.0, streak=0.7, grime=0.35)
    else:
        weather(t, rust=0.15, streak=0.5, grime=0.2, dents=1)
        t.smooth[:] = 0.55
    return t


# ---------------------------------------------------------------- materials

def conc(seed, rgb=(150, 148, 142), forms=False):
    """Weathered bridge concrete: blotchy, rain-darkened from the top, a crack, efflorescence."""
    t = F.plain(seed, rgb, 0.08, 0.15)
    t.vary(0.08)
    if forms:
        for x in range(0, N, N // 4):
            t.c[:, x] *= 0.86
            t.h[:, x] -= 0.1
        for y in (N // 3, 2 * N // 3):
            t.c[y] *= 0.9
    t.streaks(0.5, 0.78)
    t.crack(1, 0.2, (60, 58, 54))
    t.speckle(0.02, (214, 212, 204), 0.4)
    t.grime(0.2, (60, 56, 50))
    return t


def deck_under(seed):
    """The slab's underside: the board-formed soffit, a rust stain where a drain leaked."""
    t = conc(seed, (136, 134, 128), forms=True)
    F.stain(t, N * 0.6, N * 0.4, 9, col=(120, 82, 50), amount=0.35)
    return t


def mse(seed):
    """Precast retaining-wall panels: square panels on the diagonal joint, a rough exposed face."""
    t = F.plain(seed, (168, 162, 150), 0.06, 0.12)
    t.speckle(0.25, (120, 116, 108), 0.35, -0.05)
    yy, xx = np.mgrid[0:N, 0:N]
    j = ((xx + yy) % (N // 2) < 1.5) | ((xx - yy) % (N // 2) < 1.5)
    t.c[j] *= 0.55
    t.h[j] -= 0.3
    t.streaks(0.4, 0.8)
    return t


def wbeam(seed):
    """The W-beam's face: two galvanized crowns and the valley between, the splice bolts, road film."""
    t = F.plain(seed, (176, 180, 178), 0.05, 0.45)
    for y in range(N):
        s = np.sin(y / N * 2 * np.pi * 2 - np.pi / 2)
        t.c[y] *= 0.86 + 0.16 * (s * 0.5 + 0.5)
        t.h[y] = 0.5 + 0.4 * (s * 0.5 + 0.5)
    for x in (6, N - 6):
        for y in (N // 4, 3 * N // 4):
            F.disc(t, x, y, 2, col=(110, 110, 104))
    t.grime(0.35, (70, 64, 56))
    t.speckle(0.03, (130, 92, 60), 0.4)
    return t


def stripes(seed, a, b, w=8, base=None):
    """Diagonal hazard stripes, scuffed."""
    t = F.plain(seed, a, 0.04, 0.4)
    yy, xx = np.mgrid[0:N, 0:N]
    m = ((xx + yy) // w) % 2 == 1
    t.c[m] = b
    t.vary(0.06)
    t.grime(0.3, (50, 46, 40))
    chips(t, 0.03, under=base or (120, 118, 112))
    return t


def steel_paint(seed, rgb):
    t = F.plain(seed, rgb, 0.04, 0.4)
    weather(t, rust=0.3, streak=0.5, grime=0.3, dents=1)
    return t


def booth_steel(seed):
    """The booth's brushed stainless below the glass, the kick dents and tyre scuffs."""
    t = F.plain(seed, (178, 180, 182), 0.03, 0.6)
    for y in range(0, N, 2):
        t.c[y] *= 0.97
    area(t, 0, N - 10, N, N, mul=0.82)
    t.grime(0.3, (62, 58, 52))
    weather(t, rust=0.0, streak=0.3, grime=0.25, dents=2)
    return t


def booth_glass(seed, broken):
    """The booth's glass: tinted, dusty, a few cracked across, alpha so the inside shows."""
    t = F.plain(seed, (150, 170, 176), 0.03, 0.9)
    t.alpha[:] = 120
    t.grime(0.4, (110, 104, 92))
    if broken:
        rng = t.rng
        cx, cy = rng.uniform(16, 48, 2)
        for a in np.linspace(0, 2 * np.pi, 9, endpoint=False):
            for r in range(0, 40):
                x, y = int(cx + np.cos(a) * r), int(cy + np.sin(a) * r)
                if 0 <= x < N and 0 <= y < N:
                    t.c[y, x] = (230, 236, 236)
                    t.alpha[y, x] = 220
        F.disc(t, cx, cy, 5, alpha=0)
    return t


def lane(seed, go, lit):
    """The lane-use signal's face: a black box, the green arrow or the red X in lamps."""
    t = F.plain(seed, (24, 24, 24), 0.03, 0.4)
    col = (60, 230, 90) if go else (240, 40, 30)
    dim = tuple(int(c * 0.28) for c in col)
    m = np.zeros((N, N), np.float32)
    im = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(im)
    if go:
        d.line([(32, 12), (32, 50)], fill=255, width=7)
        d.polygon([(18, 34), (46, 34), (32, 54)], fill=255)
    else:
        d.line([(16, 16), (48, 48)], fill=255, width=8)
        d.line([(48, 16), (16, 48)], fill=255, width=8)
    m = np.asarray(im, np.float32) / 255
    # the lamps the shape is made of
    dots = ((np.mgrid[0:N, 0:N][0] % 4 < 3) & (np.mgrid[0:N, 0:N][1] % 4 < 3)).astype(np.float32)
    m = m * dots
    t.mix(m, col if lit else dim, 1.0)
    if lit:
        t.emit[:] = m
    t.frame(3, (60, 60, 58))
    return t


def soffit(seed, lit):
    """The canopy's underside: white metal pans, a light in the middle of this one."""
    t = F.plain(seed, (214, 214, 208), 0.03, 0.4)
    for x in (0, N // 2):
        t.c[:, x:x + 1] *= 0.8
    t.grime(0.3, (90, 86, 78))
    t.streaks(0.2)
    if lit is not None:
        area(t, 16, 16, 48, 48, col=(250, 248, 236) if lit else (178, 180, 176))
        t.frame(1, (120, 120, 116))
        if lit:
            t.emit[16:48, 16:48] = 1.0
    return t


def island_side(seed):
    return stripes(seed, YELLOW, BLACK, 10, base=(150, 148, 142))


def cushion(seed):
    """The crash cushion's segments: yellow plastic cells, the joints black."""
    t = F.plain(seed, YELLOW, 0.04, 0.45)
    for x in range(0, N, N // 4):
        t.c[:, x:x + 2] = (40, 40, 38)
    weather(t, rust=0.0, streak=0.4, grime=0.35, dents=2)
    return t


def chevron(seed, bg=YELLOW, fg=BLACK):
    """The chevron: a black arrow on yellow (the cushion's nose and the curve's sign)."""
    t = F.plain(seed, bg, 0.04, 0.5)
    im = Image.new("L", (N, N), 0)
    ImageDraw.Draw(im).polygon([(18, 8), (38, 8), (52, 32), (38, 56), (18, 56), (32, 32)], fill=255)
    t.mix(np.asarray(im, np.float32) / 255, fg, 1.0)
    t.frame(2, fg)
    weather(t, rust=0.1, streak=0.5, grime=0.25)
    return t


def arm(seed):
    """The gate arm: red and white bands, reflective, scuffed."""
    t = F.plain(seed, WHITE, 0.03, 0.5)
    for x in range(0, N, 32):
        t.c[:, x:x + 16] = RED
    t.grime(0.25, (60, 56, 50))
    return t


def drum(seed):
    """The channelizing drum: orange plastic, two white reflective bands."""
    t = F.plain(seed, ORANGE, 0.05, 0.45)
    for y0 in (14, 34):
        t.c[y0:y0 + 9] = (232, 232, 226)
    t.grime(0.35, (70, 60, 50))
    t.streaks(0.4)
    return t


def map_art(seed, row):
    """The map case: the county under scratched plexiglass, the interstates in red and blue, YOU ARE
    HERE, the rest areas on it, lit from inside; row 0 the lower half."""
    W, H = N, 2 * N
    im = Image.new("RGB", (W, H), (226, 220, 196))
    d = ImageDraw.Draw(im)
    rng = np.random.default_rng(seed)
    d.rectangle([0, 0, W, 14], fill=BROWN)
    d.text((W / 2, 7), "GRAYHAVEN COUNTY", font=ImageFont.truetype(SANS, 6), fill=WHITE, anchor="mm")
    for _ in range(9):
        x, y = rng.uniform(-10, W), rng.uniform(16, H)
        d.ellipse([x, y, x + rng.uniform(10, 26), y + rng.uniform(8, 16)], fill=(176, 206, 150))
    d.ellipse([W - 22, 70, W + 18, 120], fill=(120, 170, 210))
    d.line([(30, 16), (34, 60), (40, 90), (36, H)], fill=(196, 40, 36), width=3)
    d.line([(0, 70), (W, 64)], fill=(40, 70, 170), width=3)
    d.line([(8, 16), (14, 70), (10, H)], fill=(196, 40, 36), width=2)
    d.ellipse([28, 30, 38, 40], fill=(220, 30, 30), outline=BLACK)
    d.text((44, 26), "YOU", font=ImageFont.truetype(SANS, 6), fill=BLACK)
    d.text((44, 33), "ARE HERE", font=ImageFont.truetype(SANS, 5), fill=BLACK)
    a = np.asarray(im, np.float32)
    part = a[(1 - row) * N:(2 - row) * N]
    t = F.plain(seed, (220, 214, 190), 0.02, 0.8)
    t.c = part.copy()
    t.grime(0.25, (120, 110, 90))
    t.crack(2, 0.1, (240, 240, 236))
    t.emit[:] = 0.55
    return t


def ledge(seed):
    """Ledgestone: thin courses of grey-tan sandstone split to random lengths, the joints raked
    back in shadow rather than mortared, a stone here and there proud of the rest."""
    rng = np.random.default_rng(seed)
    t = F.plain(seed, (158, 142, 118), 0.1, 0.1)
    t.vary(0.12)
    y = 0
    while y < N:
        hgt = int(rng.integers(3, 8))
        x = int(rng.integers(-24, 0))
        while x < N:
            ln = int(rng.integers(8, 40))
            tone = rng.uniform(0.7, 1.2)
            tint = np.array([1.0, rng.uniform(0.9, 1.0), rng.uniform(0.78, 0.98)])
            x0, x1, y1 = max(0, x), min(N, x + ln - 1), min(N, y + hgt - 1)
            t.c[y:y1, x0:x1] *= tint * tone
            t.h[y:y1, x0:x1] += rng.uniform(-0.1, 0.15)
            if x + ln - 1 < N:
                t.c[y:y1, max(0, x + ln - 1):min(N, x + ln)] *= 0.45
                t.h[y:y1, max(0, x + ln - 1):min(N, x + ln)] -= 0.3
            x += ln
        t.c[min(N - 1, y + hgt - 1)] *= 0.4
        t.h[min(N - 1, y + hgt - 1)] -= 0.4
        y += hgt
    t.speckle(0.06, (96, 88, 74), 0.35)
    t.streaks(0.3, 0.85)
    return t


def timber(seed):
    t = F.mat("Wood049", (120, 84, 54), 1.1, rot=True, seed=seed, smooth=0.2)
    t.streaks(0.4)
    t.grime(0.3, (50, 40, 30))
    return t


def porcelain(seed):
    t = F.plain(seed, (232, 232, 226), 0.02, 0.7)
    t.grime(0.25, (150, 130, 90))
    t.streaks(0.3, 0.85)
    return t


def chrome(seed):
    t = F.plain(seed, (200, 204, 206), 0.03, 0.8)
    t.grime(0.2, (90, 86, 80))
    return t


def grill(seed):
    t = F.plain(seed, (34, 32, 30), 0.05, 0.2)
    for x in range(2, N, 6):
        t.c[:, x:x + 2] = (70, 66, 60)
    t.speckle(0.08, (120, 66, 36), 0.5)
    return t


def callbox(seed):
    t = F.plain(seed, YELLOW, 0.04, 0.4)
    im = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(im)
    d.text((N / 2, 18), "SOS", font=ImageFont.truetype(SANS, 16), fill=255, anchor="mm")
    d.text((N / 2, 36), "CALL BOX", font=ImageFont.truetype(SANS, 8), fill=255, anchor="mm")
    d.ellipse([24, 44, 40, 60], outline=255, width=2)
    t.mix(np.asarray(im, np.float32) / 255, BLACK, 1.0)
    weather(t, rust=0.4, streak=0.5, grime=0.3, dents=1)
    return t


def lamp(seed, lit):
    t = F.plain(seed, (230, 150, 30) if lit else (120, 80, 30), 0.03, 0.8)
    if lit:
        t.emit[:] = 0.9
    t.grime(0.2)
    return t


def textures():
    out = {
        "hw_conc": lambda: conc(49950), "hw_conc_col": lambda: conc(49951, forms=True), "hw_deck_under": lambda: deck_under(49952),
        "hw_mse": lambda: mse(49953), "hw_wbeam": lambda: wbeam(49954), "hw_endpad": lambda: stripes(49955, YELLOW, BLACK, 8),
        "hw_booth_steel": lambda: booth_steel(49956), "hw_booth_glass": lambda: booth_glass(49957, False),
        "hw_booth_glass_x": lambda: booth_glass(49958, True), "hw_booth_roof": lambda: steel_paint(49959, (196, 198, 196)),
        "hw_lamp": lambda: lamp(49960, False), "hw_lamp_lit": lambda: lamp(49961, True),
        "hw_soffit": lambda: soffit(49962, None), "hw_soffit_light": lambda: soffit(49963, False), "hw_soffit_lit": lambda: soffit(49964, True),
        "hw_fascia": lambda: steel_paint(49965, BLUE), "hw_membrane": lambda: conc(49966, (90, 92, 94)),
        "hw_go": lambda: lane(49967, True, False), "hw_go_lit": lambda: lane(49968, True, True),
        "hw_stop": lambda: lane(49969, False, False), "hw_stop_lit": lambda: lane(49970, False, True),
        "hw_island_top": lambda: conc(49971, (170, 168, 160)), "hw_island_side": lambda: island_side(49972),
        "hw_cushion": lambda: cushion(49973), "hw_chevron": lambda: chevron(49974), "hw_arm": lambda: arm(49975),
        "hw_yellow": lambda: steel_paint(49976, YELLOW), "hw_drum": lambda: drum(49977), "hw_grey": lambda: steel_paint(49978, (120, 124, 126)),
        "hw_map_0": lambda: map_art(49979, 0), "hw_map_1": lambda: map_art(49979, 1), "hw_brown": lambda: steel_paint(49980, BROWN),
        "hw_ledge": lambda: ledge(49981), "hw_capstone": lambda: conc(49982, (190, 170, 140)), "hw_timber": lambda: timber(49983),
        "hw_porcelain": lambda: porcelain(49984), "hw_chrome": lambda: chrome(49985), "hw_grill": lambda: grill(49986),
        "hw_callbox": lambda: callbox(49987), "hw_green": lambda: steel_paint(49988, GREEN), "hw_white": lambda: steel_paint(49989, WHITE),
        "hw_tabletop": lambda: steel_paint(49990, (110, 120, 96)),
    }
    i = 0
    for key, spec in SIGNS.items():
        for c in range(spec["cols"]):
            for r in range(spec["rows"]):
                out[f"hw_g_{key}_{c}{r}"] = lambda key=key, c=c, r=r, i=i: sign_tile(key, c, r, 50000 + i)
                i += 1
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- models

def faces(front, rest):
    return {"north": front, "south": rest, "east": rest, "west": rest, "up": rest, "down": rest}


def sign_piece(key, col, row):
    spec = SIGNS[key]
    back = "hw_brown" if spec.get("wood") else GALV
    b = [[0, 0, 12.5, 16, 16, 14, faces(f"hw_g_{key}_{col}{row}", back)]]
    if spec.get("wood"):
        b[0][2], b[0][5] = 11.5, 14
    b += [[0, 3, 14, 16, 4.5, 15.5, GALV], [0, 11.5, 14, 16, 13, 15.5, GALV]]
    if col in spec.get("legs", []):
        post = "hw_timber" if spec.get("wood") else GALV
        b.append([6, 0, 14, 10, 16, 16, post] if spec.get("wood") else [6.5, 0, 14, 9.5, 16, 16, post])
    return b


def rail(post=True, end=False, bent=False):
    beam = faces("hw_wbeam", GALV)
    b = []
    if post:
        b += [[6.5, 0, 8, 9.5, 11, 11, GALV], [6.8, 6, 6, 9.2, 10, 8, GALV]]
    if end:
        b.append([0, 6.5, 4.5, 16, 10.5, 6, beam, None, {"axis": "y", "angle": -22.5, "origin": [16, 8, 5]}])
        b.append([-1.5, 3, 4, 2.5, 12, 7, faces("hw_endpad", GALV), None, {"axis": "y", "angle": -22.5, "origin": [16, 8, 5]}])
    elif bent:
        b.append([0, 5.5, 5, 9, 9.5, 6.5, beam, None, {"axis": "y", "angle": 22.5, "origin": [0, 8, 5]}])
        b.append([9, 4.5, 9, 16, 8.5, 10.5, beam, None, {"axis": "y", "angle": -22.5, "origin": [16, 8, 9]}])
    else:
        b.append([0, 6.5, 4.5, 16, 10.5, 6, beam])
    return b


def parapet(rail_top):
    c = CONC
    b = [[0, 0, 2.5, 16, 2.5, 13, c], [0, 2.5, 4, 16, 6, 13, c], [0, 6, 5.5, 16, 13, 13, c], [0, 13, 6, 16, 14, 12.5, c]]
    if rail_top:
        b += [[7, 14, 8.5, 9, 20, 10.5, GALV], [0, 19, 8.8, 16, 21, 10.2, GALV]]
    return b


def gantry_post():
    b = []
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        b.append([x, 0, z, x + 2, 16, z + 2, GALV])
    for y in (0, 8):
        b += [[2, y, 2.5, 14, y + 1, 3.5, GALV], [2, y, 12.5, 14, y + 1, 13.5, GALV],
              [2.5, y, 2, 3.5, y + 1, 14, GALV], [12.5, y, 2, 13.5, y + 1, 14, GALV]]
    return b


def gantry_beam():
    b = []
    for y, z in ((2, 3), (2, 12), (13, 3), (13, 12)):
        b.append([0, y, z, 16, y + 1.5, z + 1.5, GALV])
    for x in (0, 8):
        b += [[x, 2, 3.5, x + 1, 14.5, 4.5, GALV], [x, 2, 12, x + 1, 14.5, 13, GALV], [x, 2, 3, x + 1, 3, 13.5, GALV]]
    return b


def booth(hi, broken=False):
    if not hi:
        return [[0, 0, 14, 16, 16, 16, faces("hw_booth_steel", "hw_booth_steel")], [0, 13, 11.5, 16, 14, 14, GALV]]
    glass = "hw_booth_glass_x" if broken else "hw_booth_glass"
    b = [[0, 0, 14.5, 16, 16, 15.5, glass], [0, 0, 14, 16, 1.5, 16, GALV], [0, 14.5, 14, 16, 16, 16, GALV],
         [0, 0, 14, 1.2, 16, 16, GALV], [14.8, 0, 14, 16, 16, 16, GALV]]
    if not broken:
        # the sliding pay window's frame and the cash ledge outside it
        b += [[7.4, 1.5, 13.8, 8.6, 14.5, 14.5, GALV], [3, 0, 11.5, 13, 0.8, 14, GALV]]
    return b


def lane_signal(go, lit):
    face = f"hw_{'go' if go else 'stop'}{'_lit' if lit else ''}"
    return [[3, 3, 10, 13, 15, 16, faces(face, "hw_grey")], [2.5, 14, 9, 13.5, 15, 16, "hw_grey"], [7, 15, 12, 9, 16, 14, GALV]]


def cushion_model():
    return [[2, 0, 0, 14, 1, 16, GALV], [2.5, 1, 1, 13.5, 11, 16, faces("hw_chevron", "hw_cushion")], [3, 11, 2, 13, 11.6, 15, "hw_cushion"]]


def arm_box():
    return [[4, 0, 5, 12, 13, 12, "hw_yellow"], [5, 13, 6, 11, 15, 11, "hw_grey"], [12, 10.5, 7.5, 16, 12, 9.5, "hw_arm"]]


def arm_bar():
    return [[0, 10.5, 7.5, 16, 12, 9.5, "hw_arm"]]


def arm_snap():
    return [[0, 0, 6, 16, 1.5, 8, "hw_arm", None, {"axis": "y", "angle": 22.5, "origin": [8, 0, 7]}],
            [1, 0, 10, 9, 1.5, 12, "hw_arm", None, {"axis": "y", "angle": -22.5, "origin": [5, 0, 11]}]]


def coin():
    return [[6.5, 0, 6.5, 9.5, 9, 9.5, "hw_yellow"], [3.5, 9, 3.5, 12.5, 12, 12.5, faces("hw_grey", "hw_yellow")],
            [4.5, 12, 4.5, 11.5, 12.3, 11.5, "hw_chrome"]]


def ticket():
    return [[4, 0, 6, 12, 14, 12, faces("hw_grey", "hw_grey")], [5.5, 9, 5.6, 7.5, 11, 6, "hw_green"], [8.5, 7, 5.6, 11, 8, 6, BLACK_TEX]]


def drum_model(down):
    if down:
        return [[2, 0, 2, 14, 10, 15, faces("hw_drum", "hw_drum")], [1, 0, 0, 15, 1.5, 2, BLACK_TEX]]
    return [[3, 0, 3, 13, 15, 13, "hw_drum"], [2, 0, 2, 14, 1.5, 14, BLACK_TEX], [5, 15, 5, 11, 16, 11, "hw_drum"]]


def delineator():
    return [[7.3, 0, 7.3, 8.7, 13, 8.7, "hw_white"], [7.1, 10, 7.1, 8.9, 12, 8.9, "hw_yellow"]]


def chevron_sign():
    return [[7.3, 0, 9, 8.7, 13, 10.4, GALV], [3, 4, 7.6, 13, 15, 8.6, faces("hw_chevron", GALV)]]


def callbox_model():
    return [[7.3, 0, 9, 8.7, 10, 10.4, GALV], [4.5, 8, 6, 11.5, 15, 9, faces("hw_callbox", "hw_yellow")], [7, 15, 8, 9, 16, 8.5, BLACK_TEX]]


def picnic(side):
    """One end of a picnic table along x, its benches north and south, the A-frame leg at this end."""
    lx = 1.5 if side == "l" else 12.5
    top = "hw_timber"
    b = [[0, 9, 3, 16, 10.5, 13, top], [0, 5, 0, 16, 6, 3, top], [0, 5, 13, 16, 6, 16, top],
         [lx, 0, 0, lx + 2, 5, 1.5, GALV], [lx, 0, 14.5, lx + 2, 5, 16, GALV], [lx, 5, 1, lx + 2, 6, 15, GALV],
         [lx, 0, 4, lx + 2, 9, 5.5, GALV], [lx, 0, 10.5, lx + 2, 9, 12, GALV]]
    return b


def grill_model():
    return [[7, 0, 7, 9, 9, 9, GALV], [2.5, 9, 4, 13.5, 11.5, 12, faces("hw_grill", "hw_grill")], [2.5, 11.5, 4, 13.5, 12, 12, "hw_grill"],
            [2.5, 11.5, 11, 13.5, 15, 12, "hw_grill"]]


def map_case(row):
    if row == 0:
        return [[2, 0, 6, 14, 6, 13, faces("hw_brown", "hw_brown")], [1.5, 6, 8.5, 14.5, 16, 12, faces("hw_map_0", "hw_brown")]]
    return [[1.5, 0, 8.5, 14.5, 13, 12, faces("hw_map_1", "hw_brown")], [0.5, 13, 6, 15.5, 15, 14, "hw_brown"]]


def urinal():
    return [[4.5, 3, 11, 11.5, 12, 16, faces("hw_porcelain", "hw_porcelain")], [7, 12, 14, 9, 15, 16, "hw_chrome"], [5.5, 4, 11, 10.5, 5, 13, "hw_chrome"]]


def dryer():
    return [[4.5, 8, 12.5, 11.5, 13, 16, faces("hw_chrome", "hw_chrome")], [7, 7.2, 13, 9, 8, 14.5, "hw_chrome"]]


def bags():
    return [[7.3, 0, 7.3, 8.7, 13, 8.7, "hw_green"], [5, 8, 5.4, 11, 12, 7.3, faces("hw_green", "hw_green")], [4.5, 12.5, 6, 11.5, 15.5, 7.3, "hw_white"]]


def marker():
    return [[7.3, 0, 9, 8.7, 8, 10.4, GALV]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="METAL", **extra):
        if rotate == "axis":
            # RULE: an axis model runs along z, axis= names the way its row runs
            boxes, shape = P.along_z(boxes), P.along_z(shape)
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape, "render": "cutout", **extra}
        if rotate:
            spec["rotate"] = rotate
        out[name] = spec

    add("hw_rail", rail(), "Guardrail", [[0, 0, 4.5, 16, 11, 11]])
    add("hw_rail_span", rail(post=False), "Guardrail", [[0, 6, 4.5, 16, 11, 6]])
    add("hw_rail_end", rail(end=True), "Guardrail End", [[0, 0, 4, 16, 12, 11]])
    add("hw_rail_bent", rail(post=False, bent=True), "Guardrail", [[0, 4, 4, 16, 10, 11]])
    add("hw_parapet", parapet(False), "Bridge Parapet", [[0, 0, 2.5, 16, 14, 13]], sound="STONE")
    add("hw_parapet_rail", parapet(True), "Bridge Parapet", [[0, 0, 2.5, 16, 24, 13]], sound="STONE")
    add("hw_girder", [[0, 0, 4, 16, 3, 12, CONC], [0, 3, 6.8, 16, 13, 9.2, CONC], [0, 13, 4.5, 16, 16, 11.5, CONC]],
        "Bridge Girder", [[0, 0, 4, 16, 16, 12]], rotate="axis", sound="STONE")
    add("hw_pier", [[2, 0, 2, 14, 16, 14, "hw_conc_col"]], "Bridge Pier", [[2, 0, 2, 14, 16, 14]], rotate=None, sound="STONE")
    add("hw_gantry_post", gantry_post(), "Sign Gantry", [[2, 0, 2, 14, 16, 14]], rotate=None)
    add("hw_gantry_beam", gantry_beam(), "Sign Gantry", [[0, 2, 3, 16, 14.5, 13.5]], rotate="axis")
    add("hw_gantry_foot", [[1, 0, 1, 15, 8, 15, CONC], [2, 8, 2, 3, 9.5, 3, GALV], [13, 8, 2, 14, 9.5, 3, GALV],
                           [2, 8, 13, 3, 9.5, 14, GALV], [13, 8, 13, 14, 9.5, 14, GALV], [2, 8, 2, 14, 8.5, 14, GALV]],
        "Gantry Footing", [[1, 0, 1, 15, 9, 15]], rotate=None, sound="STONE")
    add("hw_sign_leg", [[6.5, 0, 14, 9.5, 16, 16, GALV]], "Sign Post", [[6.5, 0, 14, 9.5, 16, 16]])
    add("hw_sign_leg_wood", [[6, 0, 14, 10, 16, 16, "hw_timber"]], "Sign Post", [[6, 0, 14, 10, 16, 16]], sound="WOOD")
    for key, spec in SIGNS.items():
        for c in range(spec["cols"]):
            for r in range(spec["rows"]):
                add(f"hw_g_{key}_{c}{r}", sign_piece(key, c, r), "Road Sign", [[0, 0, 11.5, 16, 16, 16]],
                    sound="WOOD" if spec.get("wood") else "METAL")
    add("hw_booth_lo", booth(False), "Toll Booth", [[0, 0, 11.5, 16, 16, 16]])
    add("hw_booth_hi", booth(True), "Toll Booth", [[0, 0, 14, 16, 16, 16]], sound="GLASS")
    add("hw_booth_hi_x", booth(True, True), "Toll Booth", [[0, 0, 14, 16, 16, 16]], sound="GLASS")
    add("hw_booth_cap", [[0, 0, 0, 16, 4, 16, faces("hw_booth_roof", "hw_booth_roof")], [-1, 3, -1, 17, 5, 17, "hw_booth_roof"]],
        "Toll Booth Roof", [[0, 0, 0, 16, 5, 16]], rotate=None)
    # the lane light stands on the booth's roof, the cap's top 5 up the cell below
    add("hw_booth_lamp", [[5, -11, 5, 11, -9, 11, "hw_grey"], [6, -9, 6, 10, -4, 10, "hw_lamp"]], "Lane Light", [[5, 0, 5, 11, 1, 11]], rotate=None)
    add("hw_booth_lamp_lit", [[5, -11, 5, 11, -9, 11, "hw_grey"], [6, -9, 6, 10, -4, 10, "hw_lamp_lit"]], "Lane Light", [[5, 0, 5, 11, 1, 11]],
        rotate=None, light=8)
    for lit in (False, True):
        sfx = "_lit" if lit else ""
        add(f"hw_canopy{sfx}", [[0, 0, 0, 16, 16, 16, {"down": "hw_soffit_lit" if lit else "hw_soffit_light", "up": "hw_membrane",
                                                    "north": "hw_fascia", "south": "hw_fascia", "east": "hw_fascia", "west": "hw_fascia"}]],
            "Toll Canopy", [[0, 0, 0, 16, 16, 16]], rotate=None, **({"light": 12} if lit else {}))
        for go in (True, False):
            add(f"hw_lane_{'go' if go else 'stop'}{sfx}", lane_signal(go, lit), "Lane Signal", [[2.5, 3, 9, 13.5, 16, 16]],
                **({"light": 6} if lit else {}))
    add("hw_canopy_plain", [[0, 0, 0, 16, 16, 16, {"down": "hw_soffit", "up": "hw_membrane", "north": "hw_fascia", "south": "hw_fascia",
                                                  "east": "hw_fascia", "west": "hw_fascia"}]], "Toll Canopy", [[0, 0, 0, 16, 16, 16]], rotate=None)
    add("hw_cushion", cushion_model(), "Crash Cushion", [[2, 0, 0, 14, 12, 16]])
    add("hw_arm_box", arm_box(), "Toll Gate", [[4, 0, 5, 12, 15, 12]])
    add("hw_arm", arm_bar(), "Toll Gate Arm", [[0, 10.5, 7.5, 16, 12, 9.5]], rotate="axis")
    add("hw_arm_snap", arm_snap(), "Toll Gate Arm", [[0, 0, 4, 16, 2, 14]], decor=True)
    add("hw_coin", coin(), "Exact Change Basket", [[3.5, 0, 3.5, 12.5, 12.5, 12.5]])
    add("hw_ticket", ticket(), "Ticket Machine", [[4, 0, 5.6, 12, 14, 12]])
    add("hw_drum", drum_model(False), "Traffic Drum", [[2, 0, 2, 14, 16, 14]], rotate=None, sound="WOOL")
    add("hw_drum_down", drum_model(True), "Traffic Drum", [[1, 0, 0, 15, 10, 15]], sound="WOOL")
    add("hw_delineator", delineator(), "Delineator", [[7, 0, 7, 9, 13, 9]], rotate=None, decor=True)
    add("hw_chevron", chevron_sign(), "Curve Sign", [[3, 0, 7.6, 13, 15, 10.4]])
    add("hw_marker", marker() + [[5, 8, 8.4, 11, 15, 9, faces("hw_g_mile_00", GALV)]], "Mile Marker", [[5, 0, 8.4, 11, 15, 10.4]])
    add("hw_callbox", callbox_model(), "Call Box", [[4.5, 0, 6, 11.5, 16, 10.4]])
    add("hw_picnic_l", picnic("l"), "Picnic Table", [[0, 0, 0, 16, 10.5, 16]], sound="WOOD")
    add("hw_picnic_r", picnic("r"), "Picnic Table", [[0, 0, 0, 16, 10.5, 16]], sound="WOOD")
    add("hw_grill", grill_model(), "Grill", [[2.5, 0, 4, 13.5, 15, 12]], rotate="facing")
    add("hw_map_lo", map_case(0), "Map Case", [[1.5, 0, 6, 14.5, 16, 13]], light=6)
    add("hw_map_hi", map_case(1), "Map Case", [[0.5, 0, 6, 15.5, 15, 14]], light=6)
    add("hw_urinal", urinal(), "Urinal", [[4.5, 3, 11, 11.5, 15, 16]], sound="STONE")
    add("hw_dryer", dryer(), "Hand Dryer", [[4.5, 7, 12.5, 11.5, 13, 16]], decor=True)
    add("hw_bags", bags(), "Pet Waste Station", [[4.5, 0, 5.4, 11.5, 15.5, 8.7]])
    add("hw_timber_post", [[3, 0, 3, 13, 16, 13, "hw_timber"]], "Timber Post", [[3, 0, 3, 13, 16, 13]], rotate=None, sound="WOOD")
    add("hw_timber_beam", [[0, 3, 4, 16, 13, 12, "hw_timber"]], "Timber Beam", [[0, 3, 4, 16, 13, 12]], rotate="axis", sound="WOOD")
    # the plain cubes
    out["hw_conc"] = {"tex": "hw_conc", "title": "Concrete", "sound": "STONE"}
    out["hw_deck"] = {"tex": {"up": "hw_conc", "down": "hw_deck_under", "side": "hw_conc"}, "title": "Bridge Deck", "sound": "STONE"}
    out["hw_mse"] = {"tex": "hw_mse", "title": "Retaining Wall", "sound": "STONE"}
    out["hw_island"] = {"tex": {"up": "hw_island_top", "down": "hw_conc", "side": "hw_island_side"}, "height": 6, "title": "Toll Island", "sound": "STONE"}
    out["hw_ledge"] = {"tex": "hw_ledge", "title": "Ledgestone", "sound": "STONE"}
    out["hw_capstone"] = {"tex": {"up": "hw_capstone", "down": "hw_capstone", "side": "hw_ledge"}, "title": "Ledgestone", "sound": "STONE"}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("hw_") or k in made}
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} highway blocks in {path}")


if __name__ == "__main__":
    main()
