"""The ap_ kit: the inside of Grayhaven International's terminal (Josiah, 2026-10-29: "Resume work
and begin the airport"), a block a metre, after a 1980s American regional terminal: the check-in
hall's counters and belts, the security lanes, the gate lounges, the baggage claim, the signs.
world/county/AirportTerminal sets them out. Everything faces north (rotate=facing) unless said:

- ap_counter: a check-in position, the public's ledge to the north, the agent's monitor behind;
  ap_counter_scale: the bag well between two positions, the scale's plate and its read-out, the
  belt's start behind it;
- ap_belt / ap_belt_bags: the take-away belt behind the counters on its legs, along z
  (rotate=axis), bare or with a case left on it; ap_belt_hole: the rubber-curtained hole the belt
  goes through the wall by (a wall block);
- ap_kiosk: a self check-in kiosk, its screen leant back to the north;
- ap_xray_in / ap_xray / ap_xray_out: the x-ray, its in-feed rollers, the tunnel with its lead
  curtains, the out-feed, a run along x read west to east by the passenger facing north;
  ap_wtmd / ap_wtmd_top: the walk-through arch, two blocks high; ap_bins: the grey tubs stacked;
  ap_rollers: a roller table along x with a tub on it; ap_podium: the ID check's podium;
- ap_seats / ap_seats_bag / ap_seats_torn: a block of gate seating, two seats on a beam, a case
  left on one, a cushion ripped off; ap_gate_desk: the gate's podium;
- ap_fids_lo / ap_fids_hi (_dead): the departures screens on their pedestal, two high;
- ap_carousel / ap_carousel_bags: the claim carousel's slat plates over its skirt, along z
  (rotate=axis); ap_carousel_end: its round end (the nose to the north); ap_carousel_feed: the
  chute the bags come down from the back;
- ap_bag_up / ap_bag_flat / ap_bag_pile / ap_bag_open: luggage, standing, lying, stacked, burst;
  ap_cart: a luggage cart;
- ap_airline_<k>_<0..2>: an airline's backlit header over its counters; ap_way_<k>_<0..2>: the
  wayfinding signs hung from the ceiling, yellow on black; ap_gate_<1..12>: a gate's number;
  ap_claim_<1..3>: a carousel's number; ap_name_<0..15>: GRAYHAVEN INTERNATIONAL AIRPORT in
  channel letters over the kerb; ap_notice_<k>: the evacuation's paper notices.

The homage's shops and fittings (the Terminal homage, Josiah 19:51 "Main concourse + one pier", laid
out on the map's plan 22:54), made-up names:

- ap_store_<k>_<0..2>: a shop's backlit name box over its front (grilltown the burger counter,
  sakana the sushi bar, books the walk-through bookshop, coffee the coffee bar, reserve the
  reservations desk, luggage the bag shop); ap_menu_<k>_<0..2>:
  the backlit menu boards behind a counter (grilltown, sakana, coffee);
- ap_sushi_belt: the sushi bar's counter with the plates going round on its belt, along z
  (rotate=axis); ap_sushi_case: the glass case of fish on the chef's side, its front north;
- ap_gondola / ap_gondola_top: a bookshop's double-sided shelf run, along z (rotate=axis), two
  high; ap_gondola_end: its end cap of books face out (north); ap_book_table: a table of new books
  in stacks; ap_mag_rack: the magazine rack, the covers facing north; ap_tray_rack: the trays' trolley;
- ap_ad_<k>_<0..5>: a backlit advert three by two on a wall (vista, a camera's); ap_paper_plane: a
  folded paper plane as big as a man hung in the atrium, its nose north; ap_golf_front / ap_golf_rear:
  the terminal's electric cart, its nose north; ap_fir_trunk / ap_fir_needles(_sparse): the
  atrium's fir, in the forest's textures.

    python3 tools/city/airport.py      rewrites the ap_ entries in blocks.json

photo_textures.py draws the textures (airport.register()).
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
from furniture import area  # noqa: E402
from school import Pic  # noqa: E402
from gasstation import weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
MONO = "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Bold.ttf"
CHROME, ALU, BLACK_METAL, STEEL = "home_chrome", "ext_alu", "ext_black_metal", "st_galv"
PLASTIC_BLACK, RUBBER = "home_plastic_black", "home_rubber"
WAY_YELLOW, WAY_BLACK = (238, 186, 36), (24, 24, 26)

# the airlines: key, name on the header, the livery's two colours
AIRLINES = {
    "eagle": ("GRAY EAGLE", "AIRLINES", (34, 46, 84), (196, 200, 206)),
    "coastal": ("COASTAL", "ATLANTIC", (18, 108, 116), (236, 236, 228)),
    "north": ("NORTHSTAR", "AIR", (176, 30, 36), (240, 238, 232)),
    "skyline": ("SKYLINE", "EXPRESS", (226, 112, 28), (44, 44, 48)),
}

# wayfinding: key -> (text, arrow) with the arrow at the left, right, or up
WAYS = {
    "ticketing": ("TICKETING", "left"),
    "claim": ("BAGGAGE CLAIM", "right"),
    "gates_a": ("GATES 1-4", "left"),
    "gates_b": ("GATES 5-10", "right"),
    "security": ("SECURITY", "up"),
    "ground": ("GROUND TRANSPORT", "right"),
    "rest": ("RESTROOMS", "left"),
    "exit": ("EXIT", "up"),
}

# the pier's shops: key -> (name, the line under it, the box's colour, the letters' colour)
STORES = {
    "grilltown": ("GRILLTOWN", "BURGERS  FRIES  SHAKES", (172, 32, 34), (250, 208, 64)),
    "sakana": ("SAKANA", "SUSHI BAR", (22, 22, 24), (236, 234, 226)),
    "books": ("AIRSIDE BOOKS", "BOOKS  NEWS  GIFTS", (24, 68, 58), (238, 232, 212)),
    "coffee": ("HANGAR COFFEE", "ESPRESSO BAR", (66, 42, 30), (236, 220, 188)),
    "reserve": ("RESERVATIONS", "TICKETS  CHANGES  STANDBY", (30, 48, 96), (236, 236, 230)),
    "luggage": ("BAG & GO", "LUGGAGE  TRAVEL GEAR", (40, 40, 44), (240, 190, 60)),
}

# the backlit adverts on the food court's walls: key -> (headline, line, ground, ink), three by two tiles
ADS = {
    "vista": ("VISTA 35", "SEE MORE OF THE WORLD", (18, 30, 58), (240, 236, 226)),
}

# the menus: key -> rows of (item, price) over three boards
MENUS = {
    "grilltown": ["DOUBLE STACK|5.49", "GRILLTOWN CLASSIC|4.29", "BACON MELT|5.99", "CHICKEN CLUB|5.29",
                  "FRIES|1.99", "ONION RINGS|2.49", "CHILI DOG|3.29", "SHAKE|2.79", "SODA|1.49"],
    "sakana": ["SALMON NIGIRI|3.50", "TUNA NIGIRI|3.75", "CALIFORNIA ROLL|5.95", "SPICY TUNA ROLL|6.50",
               "EEL ROLL|7.25", "EDAMAME|2.95", "MISO SOUP|1.95", "BENTO BOX|9.95", "GREEN TEA|1.50"],
    "coffee": ["DRIP|1.65", "ESPRESSO|1.95", "CAPPUCCINO|2.95", "LATTE|3.25", "MOCHA|3.45",
               "HOT CHOCOLATE|2.25", "MUFFIN|1.95", "BAGEL|1.75", "COOKIE|1.25"],
}

NOTICES = {
    "evac": ("NOTICE", "ALL FLIGHTS|SUSPENDED|EVACUEES REPORT|TO GATE 7|ONE BAG ONLY", (160, 30, 26)),
    "fema": ("SHELTER", "COTS ISSUED AT|COUNTER 4|WATER 0800 & 1800|NO ONE LEAVES|THE TERMINAL", (30, 60, 120)),
    "missing": ("MISSING", "HAVE YOU SEEN|OUR SON|BLUE JACKET|FLIGHT GE 214", (30, 30, 30)),
}


# ---------------------------------------------------------------- textures

def counter_front(seed):
    """The check-in counter's face: grey laminate, a stainless kick, a scuffed band where bags bang."""
    t = F.plain(seed, (92, 98, 106), 0.04, 0.45)
    area(t, 0, N - 8, N, N, (150, 152, 152), h=0.85, smooth=0.6)
    area(t, 0, 20, N, 24, mul=0.85)
    t.speckle(0.02, (60, 60, 60), 0.5)
    t.grime(0.2)
    return t


def counter_top(seed):
    t = F.plain(seed, (196, 192, 182), 0.03, 0.55)
    t.speckle(0.04, (150, 146, 138), 0.4)
    t.grime(0.12)
    return t


def belt(seed):
    """Conveyor belt: black rubber, ribs across, worn grey down the middle where the bags slid."""
    t = F.plain(seed, (34, 34, 34), 0.05, 0.25)
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=0.7, dh=-0.06)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (1 + 0.25 * np.exp(-((xx - N / 2) / 14) ** 2))[..., None]
    return t


def plates(seed):
    """The carousel's slats: stainless plates overlapping, a dark gap between each, scuffed."""
    t = F.plain(seed, (150, 154, 156), 0.05, 0.6)
    t.f0[:] = 200
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, (40, 40, 40), dh=-0.2)
        area(t, 0, y + 1, N, y + 2, mul=1.15)
    t.speckle(0.03, (90, 90, 90), 0.5)
    t.grime(0.15)
    return t


def skirt(seed):
    """The carousel's skirt: brushed steel, the black rubber bumper along the top."""
    t = F.plain(seed, (138, 140, 142), 0.04, 0.55)
    t.f0[:] = 180
    area(t, 0, 0, N, 10, (28, 28, 28), h=0.9, smooth=0.2)
    t.grime(0.2)
    return t


def bag(seed, rgb, shell=True):
    """A suitcase's side: hard shell with its ribs, or nylon with a zip round it."""
    t = F.plain(seed, rgb, 0.05, 0.5 if shell else 0.15)
    if shell:
        for x in range(10, N, 12):
            area(t, x, 0, x + 2, N, mul=1.12, dh=0.05)
    else:
        t.vary(0.08)
        area(t, 2, 2, N - 2, 4, (40, 40, 40))
        area(t, 2, N - 4, N - 2, N - 2, (40, 40, 40))
    area(t, 0, 0, N, 2, mul=0.7)
    t.grime(0.25)
    return t


def bin_tub(seed):
    t = F.plain(seed, (118, 122, 124), 0.05, 0.35)
    area(t, 0, 0, N, 4, mul=1.15)
    t.grime(0.2)
    return t


def xray_body(seed):
    """The x-ray's housing: putty-coloured enamel, a dark band, the warning label."""
    t = F.plain(seed, (184, 180, 168), 0.04, 0.4)
    area(t, 0, N - 14, N, N - 10, (60, 70, 92))
    area(t, 6, 8, 24, 18, (226, 190, 40))
    area(t, 9, 10, 21, 16, (30, 30, 30))
    area(t, 11, 11, 19, 15, (226, 190, 40))
    t.grime(0.2)
    return t


def curtain(seed):
    """The lead curtain: strips of grey rubber hanging, gaps between."""
    t = F.plain(seed, (66, 70, 72), 0.06, 0.2)
    for x in range(0, N, 6):
        area(t, x, 0, x + 1, N, (14, 14, 14), dh=-0.3)
    t.alpha[:] = 255
    return t


def wtmd(seed):
    """The walk-through arch's panel: grey, the zone lights in a column, a blue stripe."""
    t = F.plain(seed, (170, 172, 176), 0.03, 0.45)
    area(t, 0, 0, 6, N, (44, 70, 130))
    for y in range(6, N - 4, 8):
        area(t, N - 12, y, N - 6, y + 4, (60, 30, 26))
    t.grime(0.15)
    return t


def kiosk_screen(seed, dead=False):
    t = F.plain(seed, (20, 24, 30), 0.03, 0.85)
    t.f0[:] = 40
    if not dead:
        area(t, 6, 6, N - 6, 16, (40, 80, 150))
        civic_text(t, "WELCOME|TOUCH TO START", 8, 20, N - 16, 30, (210, 214, 220))
        t.emit[6:N - 6, 6:N - 6] = 0.35
    else:
        t.crack(6, 0.1, (90, 96, 104))
    return t


def civic_text(t, text, x, y, w, h, col):
    m = F.text_mask(text, BOLD, 9, w, h)
    F.paste(t, m, x, y, col)


def seat_vinyl(seed):
    t = F.plain(seed, (36, 44, 60), 0.05, 0.35)
    for x in range(0, N, 16):
        area(t, x, 0, x + 1, N, mul=0.7, dh=-0.1)
    t.grime(0.2)
    return t


def seat_torn(seed):
    """The cushion slashed open, the yellow foam showing."""
    t = seat_vinyl(seed)
    yy, xx = np.mgrid[0:N, 0:N]
    hole = ((xx - 30) / 18) ** 2 + ((yy - 34) / 12) ** 2 < 1
    t.c[hole] = (196, 182, 120)
    t.h[hole] = 0.4
    return t


# ---------------------------------------------------------------- pictures

@lru_cache(maxsize=None)
def airline_pic(k):
    """An airline's header over its counters: the backlit box in the livery, the name and a stripe."""
    name, sub, ground, ink = AIRLINES[k]
    p = Pic(3, 1, (20, 20, 22), 30100 + sum(map(ord, k)))
    W = p.W
    p.rect(0, 10, W, 54, fill=ground)
    p.rect(0, 48, W, 54, fill=ink)
    p.rect(0, 10, W, 12, fill=ink)
    size = civic.fit(name, BOLD, 22, W - 70)
    p.text(W / 2 + 12, 16, name, BOLD, size, ink, anchor="ma")
    p.text(W / 2 + 12, 36, sub, BOLD, 9, ink, anchor="ma")
    # the bird / star / wave mark at the left
    p.ellipse(10, 16, 40, 46, fill=ink)
    p.poly([(14, 34), (40, 22), (30, 38)], fill=ground)
    return p


def airline(k, i):
    t = airline_pic(k).tile(i, 30200 + sum(map(ord, k)) * 3 + i, smooth=0.5, grime=0.15)
    t.alpha[:10] = 0
    t.alpha[54:] = 0
    t.emit[10:54] = 0.25
    weather(t, rust=0.0, streak=0.3, grime=0.1)
    return t


def arrow(p, x, y, size, way, col):
    s = size
    if way == "left":
        p.poly([(x, y + s / 2), (x + s * 0.6, y), (x + s * 0.6, y + s * 0.3), (x + s, y + s * 0.3), (x + s, y + s * 0.7),
                (x + s * 0.6, y + s * 0.7), (x + s * 0.6, y + s)], fill=col)
    elif way == "right":
        p.poly([(x + s, y + s / 2), (x + s * 0.4, y), (x + s * 0.4, y + s * 0.3), (x, y + s * 0.3), (x, y + s * 0.7),
                (x + s * 0.4, y + s * 0.7), (x + s * 0.4, y + s)], fill=col)
    else:
        p.poly([(x + s / 2, y), (x + s, y + s * 0.6), (x + s * 0.7, y + s * 0.6), (x + s * 0.7, y + s), (x + s * 0.3, y + s),
                (x + s * 0.3, y + s * 0.6), (x, y + s * 0.6)], fill=col)


@lru_cache(maxsize=None)
def way_pic(k):
    """A wayfinding sign three blocks long: black box, yellow capitals, the arrow at its end."""
    text, way = WAYS[k]
    p = Pic(3, 1, WAY_BLACK, 30300 + sum(map(ord, k)))
    W = p.W
    p.rect(0, 14, W, 50, fill=WAY_BLACK)
    p.rect(1, 15, W - 1, 49, outline=(70, 70, 70), width=1)
    ax = 8 if way == "left" else W - 34
    arrow(p, ax, 19, 26, way, WAY_YELLOW)
    size = civic.fit(text, BOLD, 20, W - 54)
    tx = W / 2 + (14 if way == "left" else -14)
    p.text(tx, 32 - size * 0.6, text, BOLD, size, WAY_YELLOW, anchor="ma")
    return p


def way(k, i):
    t = way_pic(k).tile(i, 30400 + sum(map(ord, k)) * 3 + i, smooth=0.4, grime=0.1)
    t.alpha[:14] = 0
    t.alpha[50:] = 0
    t.emit[14:50] = 0.2
    return t


def gate_sign(n):
    """A gate's number hung over its door: GATE small over the big figure, white on charcoal."""
    p = Pic(1, 1, (36, 38, 42), 30500 + n)
    W = p.W
    p.rect(0, 0, W, W, fill=(36, 38, 42))
    p.rect(1, 1, W - 1, W - 1, outline=WAY_YELLOW, width=1.5)
    p.text(W / 2, 5, "GATE", BOLD, 10, (220, 220, 214), anchor="ma")
    p.text(W / 2, 17, str(n), BOLD, 34 if n < 10 else 28, WAY_YELLOW, anchor="ma")
    t = p.tile(0, 30520 + n, smooth=0.4, grime=0.12)
    t.emit[:] = 0.15
    return t


def claim_sign(n):
    p = Pic(1, 1, WAY_BLACK, 30560 + n)
    W = p.W
    p.rect(0, 0, W, W, fill=WAY_BLACK)
    p.text(W / 2, 6, "CLAIM", BOLD, 10, WAY_YELLOW, anchor="ma")
    p.ellipse(14, 20, 50, 56, fill=WAY_YELLOW)
    p.text(W / 2, 22, str(n), BOLD, 26, WAY_BLACK, anchor="ma")
    return p.tile(0, 30570 + n, smooth=0.4, grime=0.12)


FLIGHTS = [("GE 214", "BOSTON", "1"), ("CA 1180", "ATLANTA", "3"), ("NS 66", "CHICAGO", "9"), ("SX 402", "PITTSBURGH", "2"),
           ("GE 238", "WASHINGTON", "8"), ("CA 77", "MIAMI", "4"), ("NS 912", "DETROIT", "7"), ("SX 418", "NEW YORK", "1"),
           ("GE 301", "PHILADELPHIA", "6"), ("CA 1202", "CHARLOTTE", "5"), ("NS 140", "DENVER", "10"), ("SX 455", "BALTIMORE", "10")]


@lru_cache(maxsize=None)
def fids_pic():
    """Four screens two by two: DEPARTURES, the rows of flights all cancelled but one boarding,
    the bottom pair the ARRIVALS, half of them DIVERTED."""
    p = Pic(2, 2, (12, 12, 16), 30600)
    W, H = p.W, p.H
    for sx in range(2):
        for sy in range(2):
            x0, y0 = sx * N + 3, sy * N + 4
            p.rect(x0, y0, x0 + N - 6, y0 + N - 10, fill=(10, 18, 54))
            p.rect(x0, y0, x0 + N - 6, y0 + 8, fill=(220, 220, 226))
            p.text(x0 + 2, y0 + 1.5, "DEPARTURES" if sy == 0 else "ARRIVALS", BOLD, 5, (10, 18, 54))
            for r in range(6):
                f, dest, gate = FLIGHTS[(sx * 6 + r + sy * 3) % len(FLIGHTS)]
                y = y0 + 10 + r * 7
                status = ("CANCELLED" if (r + sx) % 5 else "BOARDING") if sy == 0 else ("DIVERTED" if r % 2 else "CANCELLED")
                p.text(x0 + 1, y, f, MONO, 3.4, (230, 230, 230))
                p.text(x0 + 15, y, dest[:10], MONO, 3.4, (230, 230, 230))
                p.text(x0 + 41, y, status[:9], MONO, 3.4, (230, 70, 60) if status != "BOARDING" else (90, 220, 110))
    return p


def fids(i, dead=False):
    t = fids_pic().tile(i, 30610 + i, smooth=0.85, grime=0.05)
    t.f0[:] = 40
    if dead:
        t.c[:] = (14, 14, 16)
        t.crack(4 + i, 0.1, (80, 84, 92))
    else:
        t.emit[:] = 0.45
    return t


@lru_cache(maxsize=None)
def name_pic():
    """GRAYHAVEN INTERNATIONAL AIRPORT in tall channel letters, white faces on dark returns, two
    letters gone and one hanging."""
    p = Pic(16, 1, (0, 0, 0, 0), 30700)
    W = p.W
    text = "GRAYHAVEN  INTERNATIONAL  AIRPORT"
    size = civic.fit(text, BOLD, 40, W - 20)
    p.text(W / 2, 18, text, BOLD, size, (236, 236, 230), anchor="ma")
    return p


def name_tile(i):
    t = name_pic().tile(i, 30720 + i, smooth=0.5, grime=0.2)
    t.emit[t.alpha > 128] = 0.3
    return t


@lru_cache(maxsize=None)
def notice_pic(k):
    title, body, col = NOTICES[k]
    p = Pic(1, 1, (236, 232, 218), 30800 + sum(map(ord, k)))
    W = p.W
    p.rect(0, 0, W, W, fill=(236, 232, 218))
    p.rect(0, 0, W, 14, fill=col)
    p.text(W / 2, 2, title, BOLD, 10, (240, 240, 236), anchor="ma")
    p.text(W / 2, 18, body, BOLD, 5.6, (30, 30, 30), anchor="ma")
    return p


def notice(k):
    t = notice_pic(k).tile(0, 30820 + sum(map(ord, k)), smooth=0.1, grime=0.2)
    t.alpha[:] = 255
    return t


@lru_cache(maxsize=None)
def store_pic(k):
    """A shop's name box: the name big, the line under it, a mark at the left (a burger, a fish, a
    book, a cup)."""
    name, sub, ground, ink = STORES[k]
    p = Pic(3, 1, (20, 20, 22), 31000 + sum(map(ord, k)))
    W = p.W
    p.rect(0, 8, W, 56, fill=ground)
    p.rect(0, 8, W, 10, fill=ink)
    p.rect(0, 54, W, 56, fill=ink)
    size = civic.fit(name, BOLD, 24, W - 76)
    p.text(W / 2 + 14, 14, name, BOLD, size, ink, anchor="ma")
    p.text(W / 2 + 14, 40, sub, BOLD, 8, ink, anchor="ma")
    if k == "grilltown":
        p.ellipse(10, 16, 46, 30, fill=(226, 150, 60))
        p.rect(10, 29, 46, 33, fill=(90, 160, 60))
        p.rect(10, 33, 46, 38, fill=(110, 56, 30))
        p.ellipse(10, 36, 46, 48, fill=(226, 150, 60))
    elif k == "sakana":
        p.ellipse(12, 16, 46, 48, fill=(196, 30, 30))
        p.poly([(18, 32), (34, 24), (42, 32), (34, 40)], fill=ink)
        p.poly([(18, 32), (12, 26), (12, 38)], fill=ink)
    elif k == "books":
        p.poly([(10, 22), (28, 18), (28, 46), (10, 50)], fill=ink)
        p.poly([(30, 18), (48, 22), (48, 50), (30, 46)], fill=ink)
    elif k == "reserve":
        p.poly([(10, 34), (46, 30), (50, 32), (46, 34)], fill=ink)
        p.poly([(24, 32), (34, 18), (38, 18), (32, 32)], fill=ink)
        p.poly([(24, 33), (34, 46), (38, 46), (32, 33)], fill=ink)
        p.poly([(12, 34), (10, 26), (14, 26), (18, 33)], fill=ink)
    elif k == "luggage":
        p.rect(14, 22, 44, 48, fill=ink)
        p.rect(22, 16, 36, 22, outline=ink, width=3)
        p.line([(14, 30), (44, 30)], ground, 2)
        p.ellipse(16, 47, 21, 52, fill=ink)
        p.ellipse(37, 47, 42, 52, fill=ink)
    else:
        p.rect(14, 22, 38, 46, fill=ink)
        p.ellipse(34, 26, 46, 38, outline=ink, width=3)
        p.line([(18, 18), (20, 12)], ink, 2)
        p.line([(28, 18), (30, 12)], ink, 2)
    return p


def store(k, i):
    t = store_pic(k).tile(i, 31100 + sum(map(ord, k)) * 3 + i, smooth=0.5, grime=0.15)
    t.alpha[:8] = 0
    t.alpha[56:] = 0
    t.emit[8:56] = 0.3
    weather(t, rust=0.0, streak=0.25, grime=0.1)
    return t


@lru_cache(maxsize=None)
def ad_pic(k):
    """A backlit advert three by two: the night sky over a coast, a camera big at the left, the
    headline and the line beside it."""
    head, line, ground, ink = ADS[k]
    p = Pic(3, 2, ground, 31300 + sum(map(ord, k)))
    W, H = p.W, p.H
    for y in range(0, H, 4):
        f = y / H
        p.rect(0, y, W, y + 4, fill=tuple(int(c * (1 - f) + d * f) for c, d in zip(ground, (196, 120, 70))))
    p.poly([(0, H - 30), (W * 0.3, H - 44), (W * 0.55, H - 34), (W, H - 50), (W, H), (0, H)], fill=(20, 22, 30))
    p.ellipse(W - 52, 14, W - 30, 36, fill=(250, 236, 196))
    # the camera: body, lens rings, the finder hump, the shutter
    p.rect(14, 40, 78, 84, fill=(28, 28, 30))
    p.rect(14, 40, 78, 48, fill=(170, 172, 176))
    p.rect(30, 32, 52, 40, fill=(28, 28, 30))
    p.rect(62, 34, 72, 40, fill=(170, 172, 176))
    p.ellipse(30, 48, 62, 80, fill=(90, 92, 98))
    p.ellipse(35, 53, 57, 75, fill=(20, 20, 24))
    p.ellipse(41, 59, 51, 69, fill=(70, 110, 170))
    size = civic.fit(head, BOLD, 30, W - 110)
    p.text(96, 34, head, BOLD, size, ink)
    p.text(97, 34 + size + 6, line, BOLD, 9, ink)
    p.rect(96, 34 + size + 20, W - 16, 34 + size + 22, fill=(226, 160, 60))
    return p


def ad(k, i):
    t = ad_pic(k).tile(i, 31320 + sum(map(ord, k)) * 6 + i, smooth=0.5, grime=0.12)
    t.emit[:] = 0.35
    weather(t, rust=0.0, streak=0.2, grime=0.08)
    return t


@lru_cache(maxsize=None)
def menu_pic(k):
    """Three backlit boards over a counter: dark ground, the items and prices in rows, a picture
    of the food on the middle one."""
    items = MENUS[k]
    _, _, ground, ink = STORES[k]
    p = Pic(3, 1, (16, 16, 18), 31200 + sum(map(ord, k)))
    W = p.W
    p.rect(0, 2, W, 62, fill=(24, 24, 26))
    for b in range(3):
        x0 = b * N + 2
        p.rect(x0, 4, x0 + N - 4, 60, fill=(32, 30, 30))
        p.rect(x0, 4, x0 + N - 4, 12, fill=ground)
        p.text(x0 + (N - 4) / 2, 5.5, ("MEALS", "FAVORITES", "SIDES & DRINKS")[b], BOLD, 5, ink, anchor="ma")
        if b == 1:
            p.ellipse(x0 + 14, 14, x0 + 46, 30, fill=(226, 150, 60) if k != "sakana" else (236, 236, 230))
            if k == "sakana":
                p.rect(x0 + 18, 18, x0 + 42, 25, fill=(232, 110, 70))
        for r in range(3):
            item, price = items[b * 3 + r].split("|")
            y = (34 if b == 1 else 16) + r * 9
            p.text(x0 + 3, y, item, BOLD, 3.8, (236, 232, 222))
            p.text(x0 + N - 7, y, price, BOLD, 3.8, ink, anchor="ra")
    return p


def menu(k, i):
    t = menu_pic(k).tile(i, 31300 + sum(map(ord, k)) * 3 + i, smooth=0.6, grime=0.1)
    t.emit[4:60] = 0.35
    return t


def books_retail(seed):
    """New books on a shop shelf: bright spines of every height, the price stickers, the shelf's
    edge strip with the labels."""
    t = F.plain(seed, (30, 28, 26), 0.02, 0.3)
    rng = t.rng
    pal = [(196, 40, 36), (30, 60, 140), (236, 200, 60), (30, 120, 90), (230, 230, 220), (20, 20, 22),
           (220, 110, 40), (120, 50, 120), (90, 150, 190)]
    for y0 in (2, 34):
        x = 1
        while x < N - 2:
            w = int(rng.integers(3, 7))
            h = int(rng.integers(18, 28))
            col = pal[rng.integers(len(pal))]
            area(t, x, y0 + 28 - h, x + w, y0 + 28, col=col, dh=0.3)
            area(t, x, y0 + 28 - h, x + 1, y0 + 28, mul=(0.7, 0.7, 0.7))
            if rng.random() < 0.6:
                area(t, x + 1, y0 + 30 - h, x + w - 1, y0 + 31 - h, col=(236, 220, 140))
            x += w
        area(t, 0, y0 + 28, N, y0 + 31, col=(200, 200, 196))
        area(t, 4, y0 + 28, 12, y0 + 31, col=(236, 236, 236))
    t.grime(0.15)
    return t


def books_face(seed):
    """Books face out: covers in a grid, each a block of colour with its title bar."""
    t = F.plain(seed, (40, 38, 36), 0.02, 0.3)
    rng = t.rng
    pal = [(196, 40, 36), (30, 60, 140), (236, 200, 60), (30, 120, 90), (230, 230, 220), (20, 20, 22), (220, 110, 40)]
    for gy in range(2):
        for gx in range(3):
            x0, y0 = 2 + gx * 21, 2 + gy * 31
            col = pal[rng.integers(len(pal))]
            area(t, x0, y0, x0 + 18, y0 + 27, col=col, dh=0.3)
            area(t, x0 + 2, y0 + 4, x0 + 16, y0 + 8, col=(236, 232, 220) if sum(col) < 400 else (20, 20, 20))
            area(t, x0 + 4, y0 + 18, x0 + 14, y0 + 20, col=(236, 232, 220) if sum(col) < 400 else (20, 20, 20))
    t.grime(0.12)
    return t


def mags(seed):
    """Magazine covers in a rack: each a photo-coloured block, the masthead across its top."""
    t = F.plain(seed, (50, 50, 50), 0.02, 0.5)
    rng = t.rng
    for gy in range(3):
        for gx in range(4):
            x0, y0 = 1 + gx * 16, 1 + gy * 21
            base = rng.integers(60, 230, 3)
            area(t, x0, y0, x0 + 14, y0 + 19, col=tuple(int(v) for v in base), smooth=0.7)
            area(t, x0, y0, x0 + 14, y0 + 4, col=(200, 30, 30) if rng.random() < 0.5 else (240, 240, 240))
            area(t, x0 + 3, y0 + 7, x0 + 11, y0 + 17, mul=(0.8, 0.75, 0.7))
    return t


def conveyor(seed):
    """The sushi belt: stainless crescent plates in a chain, the gap between each."""
    t = F.plain(seed, (176, 178, 178), 0.03, 0.75)
    t.f0[:] = 200
    for y in range(0, N, 8):
        area(t, 0, y, N, y + 1, (60, 60, 60), dh=-0.3)
    t.grime(0.1)
    return t


def fish(seed):
    """The case's tray: crushed ice, slabs of salmon and tuna and white fish, a leaf between."""
    t = F.plain(seed, (220, 228, 232), 0.06, 0.8)
    t.speckle(0.2, (250, 252, 255), 0.5, 0.2)
    for x0, col in ((4, (236, 116, 70)), (22, (176, 40, 50)), (40, (236, 228, 214))):
        area(t, x0, 10, x0 + 14, 54, col=col, dh=0.4, smooth=0.6)
        for y in range(14, 54, 5):
            area(t, x0, y, x0 + 14, y + 1, mul=(1.15, 1.15, 1.15))
    area(t, 19, 20, 21, 44, col=(60, 140, 50))
    return t


def textures():
    out = {
        "ap_counter_front": lambda: counter_front(30001),
        "ap_counter_top": lambda: counter_top(30002),
        "ap_belt": lambda: belt(30003),
        "ap_plates": lambda: plates(30004),
        "ap_skirt": lambda: skirt(30005),
        "ap_bin": lambda: bin_tub(30006),
        "ap_xray_body": lambda: xray_body(30007),
        "ap_curtain": lambda: curtain(30008),
        "ap_wtmd": lambda: wtmd(30009),
        "ap_kiosk_screen": lambda: kiosk_screen(30010),
        "ap_kiosk_dead": lambda: kiosk_screen(30011, True),
        "ap_vinyl": lambda: seat_vinyl(30012),
        "ap_vinyl_torn": lambda: seat_torn(30013),
        "ap_scale": lambda: F.plain(30014, (150, 152, 150), 0.03, 0.6),
        "ap_readout": lambda: readout(30015),
    }
    for k, (seed, rgb, shell) in {"black": (30020, (30, 30, 32), True), "navy": (30021, (32, 40, 70), False),
                                  "red": (30022, (150, 34, 36), True), "grey": (30023, (112, 116, 118), True),
                                  "green": (30024, (62, 74, 50), False), "tan": (30025, (150, 124, 86), False)}.items():
        out[f"ap_bag_{k}"] = lambda seed=seed, rgb=rgb, shell=shell: bag(seed, rgb, shell)
    for k in AIRLINES:
        for i in range(3):
            out[f"ap_airline_{k}_{i}"] = lambda k=k, i=i: airline(k, i)
    for k in WAYS:
        for i in range(3):
            out[f"ap_way_{k}_{i}"] = lambda k=k, i=i: way(k, i)
    for n in range(1, 13):
        out[f"ap_gate_{n}"] = lambda n=n: gate_sign(n)
    for n in range(1, 4):
        out[f"ap_claim_{n}"] = lambda n=n: claim_sign(n)
    for i in range(4):
        out[f"ap_fids_{i}"] = lambda i=i: fids(i)
        out[f"ap_fids_dead_{i}"] = lambda i=i: fids(i, True)
    for i in range(16):
        out[f"ap_name_{i}"] = lambda i=i: name_tile(i)
    for k in NOTICES:
        out[f"ap_notice_{k}"] = lambda k=k: notice(k)
    for k in STORES:
        for i in range(3):
            out[f"ap_store_{k}_{i}"] = lambda k=k, i=i: store(k, i)
    for k in MENUS:
        for i in range(3):
            out[f"ap_menu_{k}_{i}"] = lambda k=k, i=i: menu(k, i)
    for k in ADS:
        for i in range(6):
            out[f"ap_ad_{k}_{i}"] = lambda k=k, i=i: ad(k, i)
    out.update({
        "ap_books_retail": lambda: books_retail(31401),
        "ap_books_face": lambda: books_face(31402),
        "ap_mags": lambda: mags(31403),
        "ap_conveyor": lambda: conveyor(31404),
        "ap_fish": lambda: fish(31405),
        "ap_laminate": lambda: F.plain(31406, (226, 224, 218), 0.02, 0.5),
        "ap_wood": lambda: F.plain(31407, (150, 108, 70), 0.06, 0.4),
        "ap_paper": lambda: F.plain(31408, (240, 238, 230), 0.02, 0.3),
        "ap_cart_body": lambda: F.plain(31409, (226, 228, 224), 0.03, 0.55),
        "ap_cart_seat": lambda: F.plain(31410, (40, 52, 84), 0.05, 0.4),
    })
    return out


def readout(seed):
    t = F.plain(seed, (16, 16, 16), 0.02, 0.8)
    area(t, 8, 20, N - 8, 40, (60, 10, 8))
    civic_text(t, "0.0", 10, 20, N - 20, 20, (230, 50, 40))
    return t


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the pieces

def counter():
    front = {"north": "ap_counter_front", "side": "ap_counter_front", "south": "home_white", "up": "ap_counter_top",
             "down": "ap_counter_front"}
    b = [[0, 0, 5, 16, 11.5, 16, front],
         [0, 11.5, 3.5, 16, 12.5, 8, "ap_counter_top"], [0, 8, 3.5, 16, 11.5, 5, "ap_counter_front"],
         # the agent's monitor and keyboard, the boarding-pass printer
         [5, 12.5, 11.5, 11, 13, 13.5, PLASTIC_BLACK], [7.5, 13, 12.5, 8.5, 14, 13, PLASTIC_BLACK],
         [4.5, 14, 12, 11.5, 18, 13, {"south": "fit_screen_off", "side": PLASTIC_BLACK, "north": PLASTIC_BLACK,
                                      "up": PLASTIC_BLACK, "down": PLASTIC_BLACK}],
         [5, 11.5, 14, 11, 12, 15.5, "home_plastic_beige"], [12, 11.5, 11, 15, 13.5, 14, "home_plastic_beige"]]
    return b


def counter_scale():
    b = [[1, 0, 0.5, 15, 2.5, 11, {"up": "ap_scale", "side": "ap_counter_front", "down": STEEL}],
         [0, 0, 0, 1, 11.5, 16, "ap_counter_front"], [15, 0, 0, 16, 11.5, 16, "ap_counter_front"],
         [11.5, 2.5, 0.6, 14.5, 10, 2.4, {"north": "ap_readout", "side": PLASTIC_BLACK, "up": PLASTIC_BLACK,
                                           "down": PLASTIC_BLACK, "south": PLASTIC_BLACK}],
         [1, 0, 11, 15, 3, 16, STEEL], [1.5, 3, 11, 14.5, 3.4, 16, "ap_belt"]]
    return b


def belt_run(bags=False):
    """Along z: the belt on its frame, side rails, legs at each end."""
    b = [[3, 4, 0, 13, 4.6, 16, {"up": "ap_belt", "side": RUBBER, "down": RUBBER}],
         [2, 3, 0, 3, 5.6, 16, STEEL], [13, 3, 0, 14, 5.6, 16, STEEL],
         [3, 3, 0, 13, 4, 16, STEEL],
         [2.2, 0, 1, 3.2, 3, 2, STEEL], [12.8, 0, 1, 13.8, 3, 2, STEEL],
         [2.2, 0, 14, 3.2, 3, 15, STEEL], [12.8, 0, 14, 13.8, 3, 15, STEEL]]
    if bags:
        b += [[4, 4.6, 3, 12, 7.6, 13, {"side": "ap_bag_black", "up": "ap_bag_black", "down": "ap_bag_black"}],
              [7, 7.6, 6, 9, 8.2, 10, PLASTIC_BLACK]]
    return b


def belt_hole():
    """A block of the back wall with the belt's opening, the rubber curtain hung in it."""
    wall = "ap_counter_front"
    return [[0, 6, 6, 16, 16, 10, wall], [0, 0, 6, 2, 6, 10, wall], [14, 0, 6, 16, 6, 10, wall],
            [2, 0.5, 7.5, 14, 6, 8.5, "ap_curtain"], [2, 0, 6, 14, 0.5, 10, STEEL]]


def kiosk():
    screen = {"north": "ap_kiosk_screen", "side": PLASTIC_BLACK, "up": PLASTIC_BLACK, "down": PLASTIC_BLACK, "south": PLASTIC_BLACK}
    lean = {"axis": "x", "angle": -22.5, "origin": [8, 10, 8]}
    return [[5, 0, 6, 11, 1, 11, STEEL], [6, 1, 7, 10, 10, 10, {"side": "home_white", "up": "home_white", "down": "home_white",
                                                                     "north": "home_white"}],
            [3.5, 10, 5.5, 12.5, 16, 8.5, screen, None, lean], [5, 8.5, 6, 11, 9.5, 7, PLASTIC_BLACK]]


def xray(part):
    """The x-ray along x: in-feed rollers, the tunnel with its curtains, the out-feed. The bags go
    east, the operator's monitor on the north."""
    rollers = [[0, 6, 4, 16, 7, 12, {"up": "ap_belt", "side": STEEL, "down": STEEL}], [0, 5, 3.5, 16, 7.6, 4, STEEL],
               [0, 5, 12, 16, 7.6, 12.5, STEEL], [1, 0, 4, 2, 6, 5, STEEL], [14, 0, 4, 15, 6, 5, STEEL],
               [1, 0, 11, 2, 6, 12, STEEL], [14, 0, 11, 15, 6, 12, STEEL]]
    if part == "in":
        return rollers + [[3, 7, 5, 11, 9.5, 11, "ap_bin"]]
    if part == "out":
        return rollers + [[6, 7, 5, 14, 9.5, 11, "ap_bin"], [7, 9.5, 6, 13, 12, 10, {"side": "ap_bag_navy", "up": "ap_bag_navy",
                                                                                         "down": "ap_bag_navy"}]]
    body = {"north": "ap_xray_body", "south": "ap_xray_body", "up": "ap_xray_body", "down": STEEL, "side": "ap_xray_body"}
    return [[0, 0, 2, 16, 6, 14, body], [0, 13, 2, 16, 16, 14, body], [0, 6, 2, 16, 13, 3.5, body],
            [0, 6, 12.5, 16, 13, 14, body], [0.2, 6, 3.5, 0.8, 13, 12.5, "ap_curtain"],
            [15.2, 6, 3.5, 15.8, 13, 12.5, "ap_curtain"], [0, 6, 4, 16, 6.5, 12, "ap_belt"],
            # the operator's screen on its arm
            [6, 16, 0, 10, 16.6, 2, STEEL], [7.5, 13, 0, 8.5, 16, 1, STEEL],
            [5, 16.6, 0, 11, 21, 1.4, {"north": "fit_screen_off", "side": PLASTIC_BLACK, "up": PLASTIC_BLACK,
                                        "down": PLASTIC_BLACK, "south": PLASTIC_BLACK}]]


def wtmd_lo():
    panel = {"side": "ap_wtmd", "north": "ap_wtmd", "south": "ap_wtmd", "up": STEEL, "down": STEEL}
    return [[0, 0, 5, 2.5, 16, 11, panel], [13.5, 0, 5, 16, 16, 11, panel], [0, 0, 4, 16, 0.4, 12, RUBBER]]


def wtmd_hi():
    panel = {"side": "ap_wtmd", "north": "ap_wtmd", "south": "ap_wtmd", "up": STEEL, "down": STEEL}
    return [[0, 0, 5, 2.5, 6, 11, panel], [13.5, 0, 5, 16, 6, 11, panel], [0, 6, 5, 16, 10, 11, panel],
            [6, 10, 7.5, 10, 11, 8.5, "lamp_glass"]]


def bins():
    b = []
    for k in range(6):
        y = k * 1.6
        b.append([3 + (k % 2) * 0.3, y, 3, 13 + (k % 2) * 0.3, y + 1.6, 13, {"side": "ap_bin", "up": "ap_bin", "down": "ap_bin"}])
    return b


def rollers():
    return [[0, 9, 3, 16, 10, 13, {"up": STEEL, "side": STEEL, "down": STEEL}], [1, 0, 4, 2, 9, 5, STEEL],
            [14, 0, 4, 15, 9, 5, STEEL], [1, 0, 11, 2, 9, 12, STEEL], [14, 0, 11, 15, 9, 12, STEEL],
            [4, 10, 4, 12, 12.5, 12, "ap_bin"]]


def podium():
    return [[3, 0, 4, 13, 12, 12, {"side": "ap_counter_front", "north": "ap_counter_front", "up": "ap_counter_top",
                                   "down": STEEL}],
            [2, 12, 3, 14, 13, 13, "ap_counter_top"], [10, 13, 8, 11, 17, 9, STEEL], [8.5, 17, 7, 12, 18, 10, PLASTIC_BLACK],
            [4, 13, 6, 9, 13.6, 10, "home_plastic_beige"]]


def seats(kind="plain"):
    """Two seats on a black beam, facing north: the shells, the cushions, the arms, the legs."""
    cushion = "ap_vinyl_torn" if kind == "torn" else "ap_vinyl"
    b = [[0, 5, 9, 16, 6, 10, BLACK_METAL],  # the beam
         [2, 0, 8.5, 3.5, 5, 10.5, BLACK_METAL], [12.5, 0, 8.5, 14, 5, 10.5, BLACK_METAL],
         [1.5, 0, 7.5, 4, 0.4, 11.5, BLACK_METAL], [12, 0, 7.5, 14.5, 0.4, 11.5, BLACK_METAL]]
    for x0 in (0.4, 8.2):
        b += [[x0, 6, 3, x0 + 7.4, 7.6, 11, {"up": cushion, "side": "ap_vinyl", "down": BLACK_METAL}],
              [x0, 7.6, 10.5, x0 + 7.4, 15.5, 12.2, {"north": "ap_vinyl", "side": "ap_vinyl", "south": BLACK_METAL,
                                                       "up": "ap_vinyl", "down": "ap_vinyl"}]]
    for x in (0, 7.6, 15.2):
        b += [[x, 6, 4, x + 0.8, 10, 11, CHROME], [x - 0.2, 10, 3.5, x + 1, 10.6, 9, BLACK_METAL]]
    if kind == "bag":
        b += [[9.5, 7.6, 3.5, 15, 15, 8.5, {"side": "ap_bag_red", "up": "ap_bag_red", "down": "ap_bag_red"}],
              [11.5, 15, 5.5, 13, 16, 6.5, PLASTIC_BLACK]]
    return b


def gate_desk():
    front = {"north": "ap_counter_front", "side": "ap_counter_front", "south": "home_white", "up": "ap_counter_top",
             "down": STEEL}
    return [[0, 0, 4, 16, 11.5, 14, front], [0, 11.5, 3, 16, 12.5, 7, "ap_counter_top"],
            [4, 12.5, 9, 10, 17, 10, {"south": "fit_screen_off", "side": PLASTIC_BLACK, "north": PLASTIC_BLACK,
                                      "up": PLASTIC_BLACK, "down": PLASTIC_BLACK}],
            [6.5, 11.5, 10, 7.5, 12.5, 11, PLASTIC_BLACK], [11, 11.5, 9, 15, 14, 13, "home_plastic_beige"],
            [1, 11.5, 9, 3, 15, 11, PLASTIC_BLACK]]


def fids_piece(top, dead=False):
    """Half the departures stand: the two screens of its row on the pedestal's mast."""
    row = 0 if top else 2
    sfx = "dead_" if dead else ""
    b = []
    for c in range(2):
        tex = f"ap_fids_{sfx}{row + (1 - c)}"
        x0 = 0.3 + c * 7.8
        b.append([x0, 2 if not top else 0, 6, x0 + 7.6, 16 if not top else 13, 9,
                  {"north": tex, "side": PLASTIC_BLACK, "south": PLASTIC_BLACK, "up": PLASTIC_BLACK, "down": PLASTIC_BLACK}])
    if top:
        b.append([0, 13, 5.5, 16, 14, 9.5, BLACK_METAL])
    else:
        b += [[7, 0, 9, 9, 2, 11, BLACK_METAL], [5, 0, 7, 11, 0.6, 13, BLACK_METAL], [7.4, 2, 9, 8.6, 6, 10, BLACK_METAL]]
    return b


def carousel_run(bags=False):
    """Along z: the plates over the skirt, the bumper; bags riding on it if asked."""
    b = [[0, 0, 0, 16, 6, 16, {"up": "ap_plates", "side": "ap_skirt", "down": STEEL}],
         [0, 6, 0, 1, 7, 16, RUBBER], [15, 6, 0, 16, 7, 16, RUBBER]]
    if bags:
        b += [[3, 6, 2, 13, 9, 9, {"side": "ap_bag_grey", "up": "ap_bag_grey", "down": "ap_bag_grey"}],
              [4, 6, 10, 9, 10, 15, {"side": "ap_bag_tan", "up": "ap_bag_tan", "down": "ap_bag_tan"}]]
    return b


def carousel_end():
    """The round end: the plates fanned round a quarter, the skirt cut to a curve in steps."""
    b = []
    for k, (x0, x1) in enumerate(((0, 16), (1, 15), (2.5, 13.5), (5, 11))):
        z0 = k * 4
        b.append([x0, 0, z0, x1, 6, z0 + 4, {"up": "ap_plates", "side": "ap_skirt", "down": STEEL}])
        b.append([x0, 6, z0, x0 + 1, 7, z0 + 4, RUBBER])
        b.append([x1 - 1, 6, z0, x1, 7, z0 + 4, RUBBER])
    return [[q[0], q[1], 16 - q[5], q[3], q[4], 16 - q[2], q[6]] for q in b]


def carousel_feed():
    """The chute the bags come down from the back wall: a belt on a slope, its steel sides."""
    slope = {"axis": "x", "angle": -22.5, "origin": [8, 6, 16]}
    return [[3, 6, 0, 13, 7, 16, {"up": "ap_belt", "side": STEEL, "down": STEEL}, None, slope],
            [2, 6, 0, 3, 10, 16, STEEL, None, slope], [13, 6, 0, 14, 10, 16, STEEL, None, slope],
            [2, 0, 12, 14, 6, 16, "ap_skirt"]]


def bag_up(tex="ap_bag_black"):
    return [[3, 0.6, 5, 13, 13, 11, {"side": tex, "up": tex, "down": tex}], [6, 13, 7.5, 6.6, 16, 8.5, CHROME],
            [9.4, 13, 7.5, 10, 16, 8.5, CHROME], [6, 15.4, 7.5, 10, 16, 8.5, CHROME],
            [3.5, 0, 5.5, 5, 0.6, 7, RUBBER], [11, 0, 5.5, 12.5, 0.6, 7, RUBBER]]


def bag_flat(tex="ap_bag_red"):
    return [[2, 0, 3, 14, 4, 13, {"side": tex, "up": tex, "down": tex}], [6, 4, 7.5, 10, 4.6, 8.5, PLASTIC_BLACK]]


def bag_pile():
    return [[1, 0, 2, 13, 4, 12, {"side": "ap_bag_grey", "up": "ap_bag_grey", "down": "ap_bag_grey"}],
            [2.5, 4, 3, 12, 7.5, 11, {"side": "ap_bag_navy", "up": "ap_bag_navy", "down": "ap_bag_navy"}],
            [5, 0, 11, 15, 4.5, 15.5, {"side": "ap_bag_green", "up": "ap_bag_green", "down": "ap_bag_green"}],
            [5, 7.5, 6, 9, 8.2, 7, PLASTIC_BLACK]]


def bag_open():
    """A case burst open on the floor, the lid back, the clothes spilled out of it."""
    return [[2, 0, 6, 14, 2.5, 14, {"side": "ap_bag_red", "up": "home_fab_blue", "down": "ap_bag_red"}],
            [2, 0, 1.5, 14, 1, 6, {"up": "ap_bag_red", "side": "ap_bag_red", "down": "ap_bag_red"}],
            [3, 2.5, 7, 9, 3.4, 12, "home_fab_blue"], [8, 0, 13, 14, 0.8, 16, "sc_fabric_red"],
            [10, 2.5, 8, 13, 3, 11, "home_white"]]


def cart():
    """A luggage cart: the steel frame, the basket at the front, the push bar, the wheels."""
    return [[2, 2, 2, 14, 2.6, 14, STEEL], [2, 2.6, 13.4, 14, 13, 14, STEEL], [2, 12, 13, 14, 13.4, 14.6, "home_plastic_black"],
            [2, 2.6, 2, 2.6, 7, 13.4, STEEL], [13.4, 2.6, 2, 14, 7, 13.4, STEEL], [2, 6.4, 2, 14, 7, 2.6, STEEL],
            [3, 0, 2.5, 4, 2, 4, RUBBER], [12, 0, 2.5, 13, 2, 4, RUBBER], [3, 0, 12, 4, 2, 13.5, RUBBER],
            [12, 0, 12, 13, 2, 13.5, RUBBER], [4, 2.6, 4, 12, 7, 12, {"side": "ap_bag_tan", "up": "ap_bag_tan", "down": "ap_bag_tan"}]]


def hung(face, back="ext_black_metal"):
    """A sign tile hung from the ceiling on two rods: the panel in the block's middle third."""
    tex = {"north": face, "south": face, "side": back, "up": back, "down": back}
    return [[0, 3.5, 7.4, 16, 12.5, 8.6, tex], [3, 12.5, 7.8, 3.6, 16, 8.2, STEEL], [12.4, 12.5, 7.8, 13, 16, 8.2, STEEL]]


def header(face):
    """An airline's backlit box on the wall over its counters (its back to the wall, south)."""
    tex = {"north": face, "side": "ext_black_metal", "up": "ext_black_metal", "down": "ext_black_metal", "south": "ext_black_metal"}
    return [[0, 2.5, 12, 16, 13.5, 16, tex]]


def letters(face):
    """A tile of the channel letters standing off the canopy's fascia (the block south of it): the
    letters cut out of the tile's face, nothing between them."""
    return [[0, 0, 14.5, 16, 16, 16, {"north": face, "south": face, "side": face, "up": face, "down": face}]]


def flat_notice(face):
    return [[0.5, 2, 15.6, 15.5, 15, 16, {"north": face, "south": face, "side": face, "up": face, "down": face}]]


def sushi_belt():
    """The counter along z: the lacquer front both sides, the belt down the middle with its
    rails, the plates riding on it, the ledge either side."""
    front = {"side": "dn_booth_r", "north": "ap_laminate", "south": "ap_laminate", "up": "ap_laminate", "down": STEEL}
    b = [[1, 0, 0, 15, 12, 16, {"east": "sc_fabric_red", "west": "sc_fabric_red", "north": "ap_laminate",
                                "south": "ap_laminate", "up": "ap_laminate", "down": STEEL}],
         [0, 12, 0, 4, 13, 16, "ap_wood"], [12, 12, 0, 16, 13, 16, "ap_wood"],
         [5, 12, 0, 11, 13, 16, "ap_conveyor"], [4.4, 12, 0, 5, 14, 16, CHROME], [11, 12, 0, 11.6, 14, 16, CHROME]]
    for z, col in ((2, "home_white"), (7, "home_plastic_beige"), (12, "home_white")):
        b += [[5.6, 13, z, 10.4, 13.5, z + 3.5, col], [6.4, 13.5, z + 0.8, 9.6, 14.2, z + 2.7, "ap_fish"]]
    del front
    return b


def sushi_case():
    """The chef's case: the trays of fish on ice under the sloped glass, its front north."""
    return [[0, 0, 4, 16, 10, 16, {"side": "ap_laminate", "north": "ap_laminate", "south": STEEL, "up": STEEL, "down": STEEL}],
            [0.5, 10, 5, 15.5, 11, 15, "ap_fish"],
            [0, 10, 4, 0.6, 16, 16, CHROME], [15.4, 10, 4, 16, 16, 16, CHROME],
            [0.6, 15.4, 9, 15.4, 16, 16, "tx_glass"],
            [0.6, 10, 3.5, 15.4, 16.6, 4.1, "tx_glass", None, {"axis": "x", "angle": 22.5, "origin": [8, 10, 4]}]]


def gondola(top=False):
    """A bookshop's shelf run along z, books both sides: the base, the back panel, four shelves in
    two blocks (the top one with the header strip)."""
    tex = {"east": "ap_books_retail", "west": "ap_books_retail", "north": "ap_laminate", "south": "ap_laminate",
           "up": "ap_laminate", "down": "ap_laminate"}
    if not top:
        return [[1, 0, 0, 15, 2, 16, "ap_laminate"], [7.5, 2, 0, 8.5, 16, 16, "ap_laminate"],
                [1.5, 2, 0.2, 7.5, 16, 15.8, tex], [8.5, 2, 0.2, 14.5, 16, 15.8, tex],
                [1, 7.8, 0, 7.5, 8.2, 16, "ap_laminate"], [8.5, 7.8, 0, 15, 8.2, 16, "ap_laminate"]]
    return [[2, 0, 0.2, 7.5, 12, 15.8, tex], [8.5, 0, 0.2, 14, 12, 15.8, tex],
            [7.5, 0, 0, 8.5, 15, 16, "ap_laminate"], [2, 12, 0, 14, 13, 16, "ap_laminate"],
            [1.5, 13, 0, 14.5, 15, 16, {"side": "ap_store_books_1", "north": "ap_laminate", "south": "ap_laminate",
                                         "up": "ap_laminate", "down": "ap_laminate"}]]


def gondola_end():
    face = {"north": "ap_books_face", "side": "ap_laminate", "south": "ap_laminate", "up": "ap_laminate",
            "down": "ap_laminate"}
    return [[1, 0, 2, 15, 2, 14, "ap_laminate"], [1, 2, 6, 15, 16, 14, "ap_laminate"],
            [1.5, 2, 3, 14.5, 15, 6, face], [1, 8, 2.5, 15, 8.4, 6, "ap_laminate"]]


def book_table():
    b = [[1, 0, 1, 3, 9, 3, "ap_wood"], [13, 0, 1, 15, 9, 3, "ap_wood"], [1, 0, 13, 3, 9, 15, "ap_wood"],
         [13, 0, 13, 15, 9, 15, "ap_wood"], [0, 9, 0, 16, 10.5, 16, "ap_wood"]]
    for x0, z0, h in ((1.5, 1.5, 3), (6, 2, 4.5), (10.5, 1.5, 2), (2, 7, 5), (8, 8, 3.5), (12, 9, 2.5)):
        b.append([x0, 10.5, z0, x0 + 4, 10.5 + h, z0 + 5.5, {"up": "ap_books_face", "side": "ap_books_retail",
                                                            "north": "ap_books_retail", "south": "ap_books_retail",
                                                            "down": "ap_laminate"}])
    return b


def mag_rack():
    """Three tiers leaning back, the covers north, the wire lips."""
    b = [[0, 0, 10, 16, 16, 16, {"north": "ap_laminate", "side": "ap_laminate", "south": "ap_laminate",
                                 "up": "ap_laminate", "down": "ap_laminate"}]]
    for k in range(3):
        y0, z0 = 1 + k * 5, 6 + k * 1.3
        b += [[0.5, y0, z0, 15.5, y0 + 5, z0 + 1, {"north": "ap_mags", "side": "ap_laminate", "south": "ap_laminate",
                                                    "up": "ap_laminate", "down": "ap_laminate"}],
              [0.5, y0, z0 - 1.5, 15.5, y0 + 1.2, z0 - 1.1, CHROME]]
    return b


def tray_rack():
    b = [[1, 0, 1, 2, 16, 2, CHROME], [14, 0, 1, 15, 16, 2, CHROME], [1, 0, 14, 2, 16, 15, CHROME],
         [14, 0, 14, 15, 16, 15, CHROME]]
    for y in range(3, 16, 3):
        b.append([1.5, y, 1.5, 14.5, y + 0.6, 14.5, "sc_fabric_red"])
    return b


def paper_plane():
    """A folded paper plane the size of a man hung in the atrium, its nose north: the two wings as
    thin sheets widening back to the tail in steps, the keel fold under them, the hanger on top."""
    b = []
    for k in range(6):
        z0, z1 = -6 + k * 4.5, -6 + (k + 1) * 4.5
        w = 0.8 + k * 2.4
        y = 8 + k * 0.35
        b.append([8 - w, y, z0, 8, y + 0.3, z1, "ap_paper"])
        b.append([8, y, z0, 8 + w, y + 0.3, z1, "ap_paper"])
        b.append([7.85, y - 0.6 - k * 0.55, z0, 8.15, y, z1, "ap_paper"])
    b.append([7.9, 8.3, 4, 8.1, 16, 4.2, STEEL])
    b.append([7.9, 9.5, 16, 8.1, 16, 16.2, STEEL])
    return b


def golf_wheel(x0, x1, z, r=3.2):
    a = r * 0.4142
    t = {"axis": "x", "angle": 45, "origin": [8, r, z]}
    tyre = [[x0, 0, z - a, x1, 2 * r, z + a, "gs_tyre"], [x0, r - a, z - r, x1, r + a, z + r, "gs_tyre"]]
    return tyre + [q + [None, t] for q in tyre] + [[x0 - 0.2, r - 1.4, z - 1.4, x1 + 0.2, r + 1.4, z + 1.4, CHROME]]


def golf(front):
    """The terminal's electric cart, two blocks, its nose north: the white body, the bench seats,
    the canopy on its four posts, the bag rack at the back."""
    body = "ap_cart_body"
    b = [[1, 3, 0 if front else 0, 15, 5, 16, body]]
    b += golf_wheel(0, 2, 5 if front else 10) + golf_wheel(14, 16, 5 if front else 10)
    if front:
        b += [[1.5, 5, 0, 14.5, 10, 6, body], [1, 4, -0.5, 15, 7, 0.5, PLASTIC_BLACK],
              [3, 8, 1, 5, 9, 2, "ap_paper"], [11, 8, 1, 13, 9, 2, "ap_paper"],
              [2, 10, 6, 14, 13, 7, PLASTIC_BLACK],
              [6, 10, 9, 7, 15, 10, PLASTIC_BLACK], [4.5, 14.5, 8.5, 8.5, 15.2, 11.5, PLASTIC_BLACK],
              [2, 5, 10, 14, 8, 16, "ap_cart_seat"], [1, 13, 6.5, 2, 26, 7.5, CHROME], [14, 13, 6.5, 15, 26, 7.5, CHROME],
              [0.5, 26, 6, 15.5, 27, 16, body], [2, 13, 6.4, 14, 22, 6.6, "rp_glass"]]
    else:
        b += [[2, 5, 0, 14, 13, 2, "ap_cart_seat"], [2, 5, 2, 14, 8, 8, "ap_cart_seat"],
              [2, 5, 8, 14, 13, 10, "ap_cart_seat"], [2, 5, 10, 14, 8, 15, "ap_cart_seat"],
              [1, 13, 14, 2, 26, 15, CHROME], [14, 13, 14, 15, 26, 15, CHROME],
              [0.5, 26, 0, 15.5, 27, 15, body], [1, 5, 15, 15, 6, 16.5, CHROME]]
    return b


def lightbox(face):
    tex = {"north": face, "side": "home_chrome", "up": "home_chrome", "down": "home_chrome", "south": BLACK_METAL}
    return [[0, 0, 13, 16, 16, 16, tex]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="METAL", cutout=True, light=0, decor=False):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape}
        if rotate:
            spec["rotate"] = rotate
        if cutout:
            spec["render"] = "cutout"
        if light:
            spec["light"] = light
        if decor:
            spec["decor"] = True
        out[name] = spec

    add("ap_counter", counter(), "Check-in Counter", [[0, 0, 3.5, 16, 12.5, 16]])
    add("ap_counter_scale", counter_scale(), "Baggage Scale", [[0, 0, 0, 16, 11.5, 16]])
    add("ap_belt", belt_run(), "Baggage Belt", [[2, 0, 0, 14, 5.6, 16]], rotate="axis")
    add("ap_belt_bags", belt_run(True), "Baggage Belt", [[2, 0, 0, 14, 7.6, 16]], rotate="axis")
    add("ap_belt_hole", belt_hole(), "Baggage Belt", [[0, 0, 6, 16, 16, 10]])
    add("ap_kiosk", kiosk(), "Check-in Kiosk", [[3.5, 0, 5, 12.5, 16, 11]])
    for part in ("in", "out"):
        add(f"ap_xray_{part}", xray(part), "X-Ray Belt", [[0, 0, 3.5, 16, 9.5, 12.5]])
    add("ap_xray", xray("body"), "X-Ray Machine", [[0, 0, 0, 16, 16, 14]])
    add("ap_wtmd", wtmd_lo(), "Metal Detector", [[0, 0, 5, 2.5, 16, 11], [13.5, 0, 5, 16, 16, 11]])
    add("ap_wtmd_top", wtmd_hi(), "Metal Detector", [[0, 0, 5, 16, 10, 11]])
    add("ap_bins", bins(), "Security Bins", [[3, 0, 3, 13.3, 9.6, 13]], sound="WOOD")
    add("ap_rollers", rollers(), "Roller Table", [[0, 0, 3, 16, 10, 13]])
    add("ap_podium", podium(), "ID Check Podium", [[2, 0, 3, 14, 13, 13]])
    for kind in ("plain", "bag", "torn"):
        add("ap_seats" + ("" if kind == "plain" else "_" + kind), seats(kind), "Gate Seating", [[0, 0, 3, 16, 10, 12.2]])
    add("ap_gate_desk", gate_desk(), "Gate Podium", [[0, 0, 3, 16, 12.5, 14]])
    for dead in (False, True):
        sfx = "_dead" if dead else ""
        add("ap_fids_lo" + sfx, fids_piece(False, dead), "Departures Screens", [[0, 0, 5, 16, 16, 13]], light=0 if dead else 6)
        add("ap_fids_hi" + sfx, fids_piece(True, dead), "Departures Screens", [[0, 0, 5.5, 16, 14, 9.5]], light=0 if dead else 6)
    add("ap_carousel", carousel_run(), "Baggage Carousel", [[0, 0, 0, 16, 7, 16]], rotate="axis")
    add("ap_carousel_bags", carousel_run(True), "Baggage Carousel", [[0, 0, 0, 16, 9, 16]], rotate="axis")
    add("ap_carousel_end", carousel_end(), "Baggage Carousel", [[0, 0, 0, 16, 7, 16]])
    add("ap_carousel_feed", carousel_feed(), "Baggage Chute", [[2, 0, 0, 14, 10, 16]])
    add("ap_bag_up", bag_up(), "Suitcase", [[3, 0, 5, 13, 13, 11]], sound="WOOL")
    add("ap_bag_up_b", bag_up("ap_bag_grey"), "Suitcase", [[3, 0, 5, 13, 13, 11]], sound="WOOL")
    add("ap_bag_flat", bag_flat(), "Suitcase", [[2, 0, 3, 14, 4, 13]], sound="WOOL")
    add("ap_bag_flat_b", bag_flat("ap_bag_navy"), "Suitcase", [[2, 0, 3, 14, 4, 13]], sound="WOOL")
    add("ap_bag_pile", bag_pile(), "Luggage", [[1, 0, 2, 15, 7.5, 15.5]], sound="WOOL")
    add("ap_bag_open", bag_open(), "Burst Suitcase", [[2, 0, 1.5, 14, 2.5, 14]], sound="WOOL")
    add("ap_cart", cart(), "Luggage Cart", [[2, 0, 2, 14, 13.4, 14.6]])
    for k in AIRLINES:
        for i in range(3):
            n = f"ap_airline_{k}_{i}"
            add(n, header(n), "Airline Sign", [[0, 2.5, 12, 16, 13.5, 16]], light=8, decor=True)
    for k in WAYS:
        for i in range(3):
            n = f"ap_way_{k}_{i}"
            add(n, hung(n), "Sign", [[0, 3.5, 7.4, 16, 12.5, 8.6]], light=4, decor=True)
    for n in range(1, 13):
        add(f"ap_gate_{n}", hung(f"ap_gate_{n}"), "Gate Sign", [[0, 3.5, 7.4, 16, 12.5, 8.6]], light=4, decor=True)
    for n in range(1, 4):
        add(f"ap_claim_{n}", hung(f"ap_claim_{n}"), "Claim Sign", [[0, 3.5, 7.4, 16, 12.5, 8.6]], decor=True)
    for i in range(16):
        add(f"ap_name_{i}", letters(f"ap_name_{i}"), "Sign", [[0, 0, 14.5, 16, 16, 16]], decor=True)
    for k in NOTICES:
        add(f"ap_notice_{k}", flat_notice(f"ap_notice_{k}"), "Notice", [[0.5, 2, 15.6, 15.5, 15, 16]], sound="WOOL", decor=True)
    for k in STORES:
        for i in range(3):
            n = f"ap_store_{k}_{i}"
            add(n, header(n), "Shop Sign", [[0, 2.5, 12, 16, 13.5, 16]], light=8, decor=True)
    for k in MENUS:
        for i in range(3):
            n = f"ap_menu_{k}_{i}"
            add(n, [[0, 0, 13, 16, 16, 16, {"north": n, "side": BLACK_METAL, "up": BLACK_METAL, "down": BLACK_METAL,
                                           "south": BLACK_METAL}]], "Menu Board", [[0, 0, 13, 16, 16, 16]], light=6, decor=True)
    add("ap_sushi_belt", sushi_belt(), "Sushi Counter", [[0, 0, 0, 16, 14, 16]], rotate="axis")
    add("ap_sushi_case", sushi_case(), "Sushi Case", [[0, 0, 3.5, 16, 16, 16]])
    add("ap_gondola", gondola(), "Book Shelves", [[1, 0, 0, 15, 16, 16]], rotate="axis", sound="WOOD")
    add("ap_gondola_top", gondola(True), "Book Shelves", [[1.5, 0, 0, 14.5, 15, 16]], rotate="axis", sound="WOOD")
    add("ap_gondola_end", gondola_end(), "Book Display", [[1, 0, 2, 15, 16, 14]], sound="WOOD")
    add("ap_book_table", book_table(), "Book Table", [[0, 0, 0, 16, 15.5, 16]], sound="WOOD")
    add("ap_mag_rack", mag_rack(), "Magazine Rack", [[0, 0, 5, 16, 16, 16]])
    add("ap_tray_rack", tray_rack(), "Tray Trolley", [[1, 0, 1, 15, 16, 15]])
    for k in ADS:
        for i in range(6):
            n = f"ap_ad_{k}_{i}"
            add(n, lightbox(n), "Advert", [[0, 0, 13, 16, 16, 16]], light=8, decor=True)
    add("ap_paper_plane", paper_plane(), "Paper Plane", [[0, 4, 0, 16, 12, 16]], sound="WOOL", decor=True)
    add("ap_golf_front", golf(True), "Terminal Cart", [[0, 0, 0, 16, 16, 16]])
    add("ap_golf_rear", golf(False), "Terminal Cart", [[0, 0, 0, 16, 16, 16]])
    # the atrium's fir: the forest's bark and needles, but its own blocks, so the tidy-up that keeps
    # trees out from under roofs leaves this one, planted under the lantern on purpose, standing
    out["ap_fir_trunk"] = {"tex": {"side": "tree_fir_bark", "up": "tree_fir_rings", "down": "tree_fir_rings"},
                           "title": "Atrium Fir", "sound": "WOOD"}
    out["ap_fir_needles"] = {"tex": "tree_fir_leaves", "title": "Atrium Fir", "sound": "GRASS", "render": "cutout"}
    out["ap_fir_needles_sparse"] = {"tex": "tree_fir_leaves_sparse", "title": "Atrium Fir", "sound": "GRASS", "render": "cutout"}
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    stray = [k for k in data["blocks"] if k.startswith("ap_") and k not in made]
    if stray:
        sys.exit(f"ap_ prefix already used by {stray[:5]}")
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("ap_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} airport blocks in {path}")


if __name__ == "__main__":
    main()
