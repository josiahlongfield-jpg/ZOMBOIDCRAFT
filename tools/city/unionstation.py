"""Union Station: the us_ kit for Grayhaven's railway terminal (world/county/UnionStation.java).

The station is the city's Beaux-Arts terminal of the old kind (Kansas City's, Chicago's and
Washington's Union Stations are what it is drawn from, from what is known of them; no photos of
them were in the library when this was made): a limestone front of three great arched windows
between paired columns, the name cut in the frieze and the clock in the attic over it; inside, a
grand hall under a coffered ceiling with the ticket windows down its sides and brass chandeliers,
the long concourse behind with the departures board, the stair up to the el and down to the
subway. When the trains stopped it was the city's evacuation point, and it shows.

Blocks (tile 0 is always the viewer's left; anything on a wall has its back at z = 16):
- us_frieze_14_0..13: UNION STATION cut in the frieze;
- us_board_0..23: the split-flap departures board, eight across and three down (row 0 the top),
  every train on it cancelled;
- us_sign_<key>_0..1: enamel signs hung from the ceiling (tickets, trains, subway, waiting room,
  baggage, exit);
- us_notice_0..2: the notices pasted on the walls at the end (evacuation, missing, quarantine);
- us_rustic, us_rustic_b, us_rustic_moss: the ground storey's rusticated limestone (clean, grimed
  and cracked, green at the foot);
- us_coffer: the coffered plaster ceiling; us_marble_a, us_marble_b: the floor's two marbles;
  us_marble_wall: the wall's veined cladding;
- us_window, us_window_broken: the great windows' bronze glazing bars and their glass.
- us_skylight, us_skylight_broken: the concourse roof's wired glass in steel bars.

    python3 tools/city/unionstation.py        rewrites the us_ entries in blocks.json

photo_textures.py draws the textures (unionstation.register()).
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
from furniture import area, text_mask  # noqa: E402
from school import Pic  # noqa: E402
from gasstation import weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
SERIF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
MONO = "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Bold.ttf"
STONE = "stone_trim"
NS = None  # photo_textures, handed over by register()

ENAMEL = (28, 44, 70)       # the terminal's signs: cream on deep blue
CREAM = (232, 222, 190)
BRONZE = (74, 60, 42)
FLAP = (232, 196, 110)      # the board's amber lettering

FRIEZE = "UNION STATION"
FW = 14
SIGNS = {"tickets": "TICKETS", "trains": "TRAINS  ↑  EL", "subway": "SUBWAY  ↓", "waiting": "WAITING ROOM",
         "baggage": "BAGGAGE", "exit": "EXIT  TO STREET"}
# the departures board: every train out, the evacuation specials too
TRAINS = [
    ("06:50", "UPTOWN  PINECREST", "1", "CANCELLED"),
    ("07:15", "MILLBROOK  FAIRVIEW", "2", "CANCELLED"),
    ("07:40", "EVAC SPECIAL  FORT CALDWELL", "3", "DEPARTED"),
    ("08:05", "SEABRIGHT  AIRPORT", "1", "CANCELLED"),
    ("08:20", "EVAC SPECIAL  FORT CALDWELL", "3", "CANCELLED"),
    ("08:45", "RIVERSIDE  ASHBY", "2", "SUSPENDED"),
    ("09:10", "KINGSBRIDGE  HOLLOW CREEK", "1", "SUSPENDED"),
]


# ---------------------------------------------------------------- stone

def frieze(i):
    """UNION STATION cut in the limestone frieze, one tile per block."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(9900 + i)
    m = text_mask(FRIEZE, SERIF, 30, N * FW - 40, 40, seed=FW, spacing=1.2)
    full = np.zeros((N, N * FW), np.float32)
    h, ww = m.shape
    full[12:12 + h, 20:20 + ww] = m
    cut = full[:, i * N:(i + 1) * N] > 0.4
    t.c[cut] *= 0.4
    t.h[cut] = 0.45
    area(t, 0, 0, N, 3, mul=1.1, dh=0.1)
    area(t, 0, N - 3, N, N, mul=0.75, dh=-0.1)
    t.streaks(0.7, 0.75)
    t.grime(0.15)
    return t


def rustic(seed, dirt=0.0, cracks=0, moss=False):
    """The ground storey's rusticated limestone: the front's smooth stone cut into long blocks,
    two courses a block, with deep chamfered joints between them and the head joints staggered,
    grimed from the street; splashed green at the foot (moss)."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(seed)
    half = N // 2
    for y0 in (0, half):
        area(t, 0, y0, N, y0 + 2, mul=0.42, dh=-0.4)
        area(t, 0, y0 + 2, N, y0 + 4, mul=0.78, dh=-0.2)
        area(t, 0, y0 + half - 2, N, y0 + half, mul=0.88, dh=-0.08)
    for y0, x in ((0, N // 3), (half, N * 5 // 6)):
        area(t, x, y0, x + 2, y0 + half, mul=0.5, dh=-0.3)
    t.vary(0.05)
    t.grime(0.18 + dirt)
    if cracks:
        t.crack(cracks, 0.2, col=(40, 38, 34))
    if moss:
        yy = np.mgrid[0:N, 0:N][0]
        m = np.clip((yy / N - 0.45) * 1.8, 0, 1) * np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3))) + 0.6, 0, 1)
        t.mix(m, (70, 86, 52), 0.7)
    t.streaks(0.4, 0.8)
    return t


def marble(seed, base, vein, veins=0.05):
    """Polished marble: a cloudy ground, veins where the noise crosses zero, scuffed and stained
    by the years and the crowds."""
    t = F.plain(seed, base, 0.05, 0.7)
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.6), (16, 0.3), (32, 0.15)))
    t.c *= (1 + 0.08 * n)[..., None]
    v = np.abs(n) < veins
    t.c[v] = t.c[v] * 0.4 + np.array(vein, np.float32) * 0.6
    n2 = P.fbm(t.rng, ((8, 1.0), (16, 0.5)))
    v2 = np.abs(n2) < veins * 0.5
    t.c[v2] = t.c[v2] * 0.7 + np.array(vein, np.float32) * 0.3
    t.f0[:] = 40
    # the joint round each slab, and the scuffs and grime of the end
    area(t, 0, 0, N, 1, mul=0.7, dh=-0.1)
    area(t, 0, 0, 1, N, mul=0.7, dh=-0.1)
    t.grime(0.22)
    t.crack(int(t.rng.integers(0, 2)), 0.12, col=(70, 64, 56))
    return t


def coffer(seed):
    """A coffer of the hall's ceiling: a sunk square panel in cream plaster, gilt mouldings
    stepping down to it and a rosette in the middle, gone grey with soot and water."""
    t = F.plain(seed, (214, 204, 180), 0.04, 0.3)
    for k, (col, h) in enumerate([((196, 186, 160), 0.85), ((176, 150, 96), 0.7), ((204, 194, 168), 0.55), ((190, 180, 154), 0.4)]):
        m = 4 + k * 5
        area(t, m, m, N - m, N - m, col=col, h=h)
    # gilt bead round the panel
    for m in (9, 10):
        area(t, m, m, N - m, m + 1, col=(170, 140, 70), h=0.72)
        area(t, m, N - m - 1, N - m, N - m, col=(170, 140, 70), h=0.72)
        area(t, m, m, m + 1, N - m, col=(170, 140, 70), h=0.72)
        area(t, N - m - 1, m, N - m, N - m, col=(170, 140, 70), h=0.72)
    c = N // 2
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - c + 0.5, yy - c + 0.5)
    petals = (r < 7) & (np.cos(np.arctan2(yy - c, xx - c) * 8) > -0.2 + r / 10)
    t.c[petals] = (182, 150, 82)
    t.h[petals] = 0.7
    t.c[r < 2.2] = (150, 120, 60)
    t.vary(0.06)
    t.grime(0.35, col=(60, 56, 50))
    # water coming through the roof
    stain = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2 - 0.4, 0, 1)
    t.mix(stain * 0.5, (120, 104, 76), 0.6)
    return t


def window(seed, broken=False):
    """The great windows' glazing: bronze bars in a grid of four panes a block, the glass grey
    with grime, broken out here and there (or mostly, in the broken one)."""
    t = F.plain(seed, (150, 168, 172), 0.04, 0.85)
    t.alpha[:] = 150
    t.f0[:] = 60
    t.grime(0.3, col=(70, 74, 70))
    bar = np.zeros((N, N), bool)
    for p in (0, 1, 31, 32, 33, 62, 63):
        bar[p, :] = True
        bar[:, p] = True
    if broken:
        holes = np.zeros((N, N), bool)
        for qx in range(2):
            for qy in range(2):
                if t.rng.random() < 0.7:
                    x0, y0 = qx * 32 + 2, qy * 32 + 2
                    yy, xx = np.mgrid[0:30, 0:30]
                    jag = P.fbm(t.rng, ((4, 1.0), (8, 0.5)))[:30, :30]
                    keep = (np.minimum(np.minimum(xx, 29 - xx), np.minimum(yy, 29 - yy)) + jag * 6) < 3 + t.rng.random() * 4
                    holes[y0:y0 + 30, x0:x0 + 30] = ~keep
        t.alpha[holes] = 0
    t.c[bar] = BRONZE
    t.alpha[bar] = 255
    t.h[bar] = 0.95
    t.smooth[bar] = 0.35
    t.f0[bar] = 230
    return t


def skylight(seed, broken=False):
    """The concourse roof's glazing: wired glass in steel bars, four panes a block, gone green-grey
    with soot and lichen on top, the wire's square mesh showing through; panes out here and there
    (most of them in the broken one), the rain coming in."""
    t = F.plain(seed, (128, 146, 136), 0.05, 0.8)
    t.alpha[:] = 165
    t.f0[:] = 55
    yy, xx = np.mgrid[0:N, 0:N]
    wire = (xx % 6 == 3) | (yy % 6 == 3)
    t.c[wire] = t.c[wire] * 0.78
    t.grime(0.5, col=(62, 68, 56))
    lichen = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5), (16, 0.25))) * 2.2 - 0.9, 0, 1)
    t.mix(lichen * 0.6, (88, 104, 60), 0.7)
    bar = np.zeros((N, N), bool)
    for q in (0, 1, 31, 32, 33, 62, 63):
        bar[q, :] = True
        bar[:, q] = True
    holes = np.zeros((N, N), bool)
    for qx in range(2):
        for qy in range(2):
            if t.rng.random() < (0.75 if broken else 0.12):
                x0, y0 = qx * 32 + 2, qy * 32 + 2
                yy2, xx2 = np.mgrid[0:30, 0:30]
                jag = P.fbm(t.rng, ((4, 1.0), (8, 0.5)))[:30, :30]
                keep = (np.minimum(np.minimum(xx2, 29 - xx2), np.minimum(yy2, 29 - yy2)) + jag * 6) < 2 + t.rng.random() * 4
                holes[y0:y0 + 30, x0:x0 + 30] = ~keep
    t.alpha[holes] = 0
    t.c[bar] = (58, 62, 60)
    t.alpha[bar] = 255
    t.h[bar] = 0.9
    t.smooth[bar] = 0.3
    t.f0[bar] = 180
    rust = bar & (P.fbm(t.rng, ((8, 1.0), (16, 0.5))) > 0.25)
    t.c[rust] = (112, 70, 42)
    return t


# ---------------------------------------------------------------- pictures

@lru_cache(maxsize=None)
def board_pic():
    """The departures board, eight blocks by three: the header, then a row of flaps for each
    train, amber on black, a seam across the middle of every flap."""
    p = Pic(8, 3, (16, 16, 16), 9950)
    W, H = p.W, p.H
    p.rect(0, 0, W, H, fill=(22, 22, 20))
    p.rect(2, 2, W - 2, H - 2, outline=(70, 62, 46), width=1.5)
    p.text(W / 2, 4, "DEPARTURES", BOLD, 13, CREAM, anchor="ma")
    for x, s in ((10, "TIME"), (58, "DESTINATION"), (298, "TRACK"), (330, "STATUS")):
        p.text(x, 21, s, BOLD, 6, (190, 182, 160))
    top, row = 30, 22
    cw = 7.2
    for r, (time, dest, track, status) in enumerate(TRAINS):
        y = top + r * row
        cols = [(10, time, 5), (58, dest, 27), (304, track, 1), (330, status, 10)]
        for x0, text, n in cols:
            for k in range(n):
                cx = x0 + k * (cw + 1.1)
                p.rect(cx, y, cx + cw, y + row - 5, fill=(34, 34, 32))
            for k, ch in enumerate(text[:n]):
                cx = x0 + k * (cw + 1.1)
                col = (226, 88, 70) if text in ("CANCELLED", "SUSPENDED") else FLAP
                p.text(cx + cw / 2, y + 1.5, ch, MONO, 12, col, anchor="ma")
            for k in range(n):
                cx = x0 + k * (cw + 1.1)
                p.line([(cx, y + (row - 5) / 2), (cx + cw, y + (row - 5) / 2)], (10, 10, 10), width=0.7)
    # a flap stuck half over in one row, and a pasted notice over the corner
    p.rect(420, 120, 500, 182, fill=(232, 228, 210))
    p.text(460, 126, "ALL SERVICE|SUSPENDED|BY ORDER|OF THE|GOVERNOR", BOLD, 7.5, (160, 30, 24), anchor="ma")
    return p


def board(i):
    t = board_pic().tile(i, 9960 + i, smooth=0.5, grime=0.18)
    t.f0[:] = 30
    return t


@lru_cache(maxsize=None)
def sign_pic(key):
    """A hung enamel sign two blocks long: cream capitals on the terminal's blue, a cream rule."""
    p = Pic(2, 1, ENAMEL, 9970 + sum(map(ord, key)))
    W = p.W
    p.rect(0, 16, W, 48, fill=ENAMEL)
    p.rect(3, 19, W - 3, 45, outline=CREAM, width=1.2)
    text = SIGNS[key]
    size = civic.fit(text, BOLD, 15, W - 22)
    p.text(W / 2, 32 - size * 0.62, text, BOLD, size, CREAM, anchor="ma")
    return p


def sign(key, i):
    t = sign_pic(key).tile(i, 9980 + sum(map(ord, key)) * 3 + i, smooth=0.6, grime=0.2)
    t.alpha[:16] = 0
    t.alpha[48:] = 0
    t.f0[:] = 50
    weather(t, rust=0.3, streak=0.4, grime=0.1)
    return t


NOTICES = [
    ("EVACUATION", "TRAINS LEAVE FROM|TRACK 3 ONLY|ONE BAG EACH|HAVE PAPERS READY", (150, 30, 24)),
    ("MISSING", "HAVE YOU SEEN|OUR DAUGHTER|LAST SEEN HERE|PLEASE CALL", (30, 30, 30)),
    ("QUARANTINE", "THIS STATION IS|CLOSED BY ORDER|DO NOT ENTER|TRESPASSERS SHOT", (30, 30, 30)),
]


@lru_cache(maxsize=None)
def notice_pic(k):
    title, body, col = NOTICES[k]
    p = Pic(1, 1, (0, 0, 0, 0), 9990 + k)
    paper = (232, 226, 204) if k != 1 else (238, 234, 222)
    p.rect(6, 4, 58, 60, fill=paper)
    p.text(32, 7, title, BOLD, 6.2, col, anchor="ma")
    p.rect(10, 15, 54, 15.6, fill=col)
    if k == 1:
        p.rect(20, 18, 44, 36, fill=(150, 146, 134))
        p.ellipse(27, 20, 37, 30, fill=(110, 104, 96))
        p.text(32, 39, body, BOLD, 3.4, (40, 40, 40), anchor="ma")
    else:
        p.text(32, 19, body, BOLD, 4.0, (40, 40, 40), anchor="ma")
    return p


def notice(k):
    t = notice_pic(k).tile(0, 9995 + k, smooth=0.2, grime=0.25)
    t.streaks(0.5, 0.8)
    return t


def textures():
    T = {}
    for i in range(FW):
        T[f"us_frieze_{FW}_{i}"] = lambda i=i: frieze(i)
    for i in range(24):
        T[f"us_board_{i}"] = lambda i=i: board(i)
    for k in SIGNS:
        for i in range(2):
            T[f"us_sign_{k}_{i}"] = lambda k=k, i=i: sign(k, i)
    for k in range(len(NOTICES)):
        T[f"us_notice_{k}"] = lambda k=k: notice(k)
    T["us_coffer"] = lambda: coffer(9901)
    T["us_rustic"] = lambda: rustic(9910)
    T["us_rustic_b"] = lambda: rustic(9911, dirt=0.12, cracks=2)
    T["us_rustic_moss"] = lambda: rustic(9912, dirt=0.08, moss=True)
    T["us_marble_a"] = lambda: marble(9902, (214, 204, 182), (150, 130, 110))
    T["us_marble_b"] = lambda: marble(9903, (60, 82, 70), (170, 180, 160), veins=0.04)
    T["us_marble_wall"] = lambda: marble(9904, (196, 184, 160), (120, 104, 84), veins=0.06)
    T["us_window"] = lambda: window(9905)
    T["us_window_broken"] = lambda: window(9906, broken=True)
    T["us_skylight"] = lambda: skylight(9913)
    T["us_skylight_broken"] = lambda: skylight(9914, broken=True)
    T["us_bronze"] = lambda: F.plain(9907, BRONZE, 0.08, 0.4)
    T["us_flap_back"] = lambda: F.plain(9908, (26, 26, 24), 0.05, 0.3)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

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

    for i in range(FW):
        n = f"us_frieze_{FW}_{i}"
        add(n, None, "Frieze", "STONE", tex={"north": n, "side": STONE, "up": STONE, "down": STONE})
    for i in range(24):
        n = f"us_board_{i}"
        add(n, [[0, 0, 12, 16, 16, 16, {"north": n, "side": "us_flap_back", "up": "us_flap_back", "down": "us_flap_back",
                                         "south": "us_flap_back"}]], "Departures Board", "METAL")
    for k in SIGNS:
        for i in range(2):
            n = f"us_sign_{k}_{i}"
            add(n, [[0, 3, 7.5, 16, 13, 8.5, {"north": n, "south": "us_bronze", "side": "us_bronze", "up": "us_bronze",
                                               "down": "us_bronze"}],
                    [7.6 if i == 0 else 7.6, 13, 7.6, 8.4, 16, 8.4, "us_bronze"]], "Sign", "METAL", decor=True, render="cutout")
    for k in range(len(NOTICES)):
        n = f"us_notice_{k}"
        add(n, [[0, 0, 15.6, 16, 16, 16, {"north": n, "south": n, "side": n, "up": n, "down": n}]], "Notice", "WOOL",
            decor=True, render="cutout")
    add("us_coffer", None, "Coffered Ceiling", "STONE", rotate=None, tex={"down": "us_coffer", "side": "cv_plaster", "up": "cv_plaster"})
    for n in ("us_rustic", "us_rustic_b", "us_rustic_moss"):
        add(n, None, "Rusticated Stone", "STONE", rotate=None, tex={"all": n})
    add("us_marble_a", None, "Marble Floor", "STONE", rotate=None, tex={"all": "us_marble_a"})
    add("us_marble_b", None, "Marble Floor", "STONE", rotate=None, tex={"all": "us_marble_b"})
    add("us_marble_wall", None, "Marble", "STONE", rotate=None, tex={"all": "us_marble_wall"})
    for n in ("us_window", "us_window_broken"):
        add(n, [[0, 0, 7, 16, 16, 9, {"north": n, "south": n, "side": "us_bronze", "up": "us_bronze", "down": "us_bronze"}]],
            "Station Window", "GLASS", rotate="axis", render="translucent")
    for n in ("us_skylight", "us_skylight_broken"):
        add(n, None, "Wired Glass Skylight", "GLASS", rotate=None, tex={"all": n}, render="translucent")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("us_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} union station blocks in {path}")


if __name__ == "__main__":
    main()
