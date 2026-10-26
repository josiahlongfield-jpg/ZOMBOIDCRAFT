#!/usr/bin/env python3
"""The town hall's kit. There are no town hall photos in the library yet, so this follows what the
small Midwestern city halls of the 1890s to 1910s are known for: a Romanesque front of rock-faced
stone below and brick above, the name cut in a stone band over a round-arched door, a date stone,
a corner clock tower with louvred belfry and a slate pyramid; inside, the offices off a central
hall (clerk, treasurer, water department, mayor) behind counters and grilles, the polling booths
and ballot box from the last election, notice boards, the honour roll, and upstairs the council
chamber with its curved bench, the podium for the public, the town map, pressed-tin ceilings.

- th_frieze_8_*: CITY HALL across eight stone tiles; th_datestone_0/1 (A.D. 1894);
- th_floor_tin: a floor whose underside is the pressed-tin ceiling of the room below;
- council bench (plain, ends, nameplates and microphones), the podium, voting booths (lower and
  upper), ballot box, notice boards (curfew, boil water, the shelter at the school), the town
  map in two tiles with its pins and the red line drawn round the quarantine, the bronze honour
  roll, door plaques (mayor, council, water, tax).

    python3 tools/city/townhall.py        rewrites the th_ entries in blocks.json

photo_textures.py draws the textures (townhall.register()); world/county/TownHall.java builds with
them. As in the other kits the base model faces north: anything on a wall has its back at z = 16.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
from furniture import area, disc, edges, text_mask  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
STONE = "stone_trim"
NAME = "CITY HALL"
PLAQUES = {"mayor": "MAYOR", "council": "COUNCIL|CHAMBER", "water": "WATER|DEPT", "tax": "TAX|OFFICE", "clerk": "CITY|CLERK"}
NOTICES = [
    ("CURFEW", "DUSK TO DAWN|BY ORDER OF|THE MAYOR", (232, 224, 196)),
    ("BOIL WATER", "ALL WARDS|UNTIL FURTHER|NOTICE", (236, 218, 120)),
    ("SHELTER", "HIGH SCHOOL|GYM - BRING|BLANKETS", (226, 226, 222)),
]


# ---------------------------------------------------------------- outside

def frieze(i, w=8):
    """CITY HALL cut in a stone band, one tile per block."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(9700 + i)
    m = text_mask(NAME, SERIF, 30, N * w - 40, 36, seed=w, spacing=1.3)
    full = np.zeros((N, N * w), np.float32)
    h, ww = m.shape
    x0 = (N * w - ww) // 2
    full[14:14 + h, x0:x0 + ww] = m
    part = full[:, i * N:(i + 1) * N]
    cut = part > 0.4
    t.c[cut] *= 0.4
    t.h[cut] = 0.45
    area(t, 0, 0, N, 4, mul=1.12, dh=0.12)
    area(t, 0, N - 4, N, N, mul=0.72, dh=-0.12)
    t.streaks(0.7, 0.8)
    return t


def datestone(i):
    t = NS.build(STONE)
    t.rng = np.random.default_rng(9710 + i)
    text = ("A.D.", "1894")[i]
    m = text_mask(text, SERIF, 20, 52, 30, seed=9710 + i, spacing=1.1)
    cut = np.zeros((N, N), np.float32)
    h, w = m.shape
    cut[17:17 + h, (N - w) // 2:(N - w) // 2 + w] = m
    t.c *= (1 - 0.55 * cut)[..., None]
    t.h -= 0.35 * cut
    # the sunk panel runs across both tiles: its edge on the outer side of each
    if i == 0:
        edges(t, 3, 4, N, N - 4, w=1, light=1.12, dark=0.7)
    else:
        edges(t, 0, 4, N - 3, N - 4, w=1, light=1.12, dark=0.7)
    t.streaks(0.7, 0.75)
    return t


# ---------------------------------------------------------------- inside

def tin(seed):
    """Pressed tin ceiling: four by four squares, each a raised rim, a rosette and a diamond, cream
    paint gone yellow, rust bleeding at the seams and water stains."""
    t = F.plain(seed, (218, 210, 186), 0.03, 0.45)
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    u, v = (xx % 16) - 7.5, (yy % 16) - 7.5
    r = np.hypot(u, v)
    rim = (np.maximum(np.abs(u), np.abs(v)) > 6.5)
    t.h[rim] += 0.35
    t.c[rim] *= 1.06
    t.h[(r < 3.2)] += 0.45
    t.c[(r < 3.2)] *= 1.04
    ring = (r > 4.2) & (r < 5.0)
    t.h[ring] += 0.25
    dia = (np.abs(u) + np.abs(v) > 5.6) & (np.abs(u) + np.abs(v) < 6.4)
    t.h[dia] += 0.2
    t.c[rim & (rng.random((N, N)) < 0.05)] *= 0.8
    # seams every 32 with rust bleeding down from them
    for s in (0, 32):
        area(t, s, 0, s + 1, N, (120, 70, 40), h=0.3)
        area(t, 0, s, N, s + 1, (120, 70, 40), h=0.3)
    for _ in range(4):
        cx, cy = rng.integers(4, N - 4, 2)
        rr = float(rng.uniform(5, 12))
        m = np.hypot(xx - cx, yy - cy) < rr * (0.8 + 0.4 * rng.random((N, N)))
        t.c[m] = t.c[m] * 0.82 + np.array([150, 104, 50]) * 0.18
    t.grime(0.25)
    return t


def felt(seed):
    t = F.plain(seed, (38, 74, 50), 0.06, 0.15)
    t.grime(0.2)
    return t


def nameplate(seed):
    """The bench's front strip: brass plates with names (fictional), on walnut."""
    t = F.wood(seed, "walnut")
    F.tone(t, (78, 50, 32))
    rng = t.rng
    names = ["ALD. HARLAN", "ALD. PRUETT", "MAYOR VOSS", "ALD. KIRBY", "CLERK", "ALD. DUNMORE", "ALD. TEAGUE"]
    nm = names[int(rng.integers(0, len(names)))]
    area(t, 6, 18, 58, 46, (176, 146, 80), h=0.8, smooth=0.7, f0=220)
    m = text_mask(nm, SANS, civic.fit(nm, SANS, 7, 50), 50, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 8, 26, (40, 30, 20), h=0.6)
    return F.wear(t, 0.25, 1)


def booth_curtain(seed):
    t = F.plain(seed, (34, 44, 82), 0.05, 0.25)
    x = np.arange(N, dtype=np.float32)
    t.c *= (1 + 0.16 * np.sin(x / 2.4))[None, :, None]
    t.h += (0.1 * np.sin(x / 2.4))[None, :]
    area(t, 0, 0, N, 4, (196, 192, 184))
    t.grime(0.3)
    return t


def vote_sign(seed):
    t = F.plain(seed, (232, 228, 216), 0.03, 0.3)
    m = text_mask("VOTE", SANS, 18, 50, 22, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 8, 20, (160, 30, 34), h=0.8)
    area(t, 0, 8, N, 12, (34, 44, 82))
    area(t, 0, 50, N, 54, (34, 44, 82))
    t.grime(0.2)
    return t


def ballot(seed):
    """The ballot box's front: oak, a brass lock, BALLOTS stencilled, the seal tape torn."""
    t = F.wood(seed, "oak")
    F.tone(t, (150, 110, 66))
    edges(t, 1, 1, N - 1, N - 1, w=2)
    m = text_mask("BALLOTS", SANS, 9, 50, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 8, 22, (30, 26, 24), h=0.6)
    area(t, 28, 40, 36, 48, (176, 146, 80), h=0.9, smooth=0.7, f0=220)
    area(t, 0, 6, 22, 9, (226, 222, 210))
    return F.wear(t, 0.3, 1)


def ballot_top(seed):
    t = F.wood(seed, "oak")
    F.tone(t, (150, 110, 66))
    area(t, 16, 28, 48, 34, (20, 16, 14), h=0.0)
    return F.wear(t, 0.3, 1)


def notice(seed, head, body, paper):
    """A cork board with the emergency notice pinned over the old ones, curling."""
    t = F.plain(seed, (172, 124, 78), 0.1, 0.2)
    rng = t.rng
    area(t, 0, 0, N, 3, (92, 66, 40))
    area(t, 0, N - 3, N, N, (92, 66, 40))
    # old sheets behind
    for x0, y0, x1, y1 in [(4, 6, 24, 30), (40, 34, 60, 58)]:
        area(t, x0, y0, x1, y1, (210, 204, 186), h=0.3)
        for k in range(y0 + 3, y1 - 3, 3):
            area(t, x0 + 2, k, x1 - 2 - int(rng.integers(0, 5)), k + 1, (120, 116, 108))
    # the notice
    x0, y0, x1, y1 = 12, 8, 54, 58
    area(t, x0, y0, x1, y1, paper, h=0.45)
    area(t, x0, y1 - 1, x1, y1, mul=0.7)
    m = text_mask(head, SANS, civic.fit(head, SANS, 8, x1 - x0 - 2), x1 - x0 - 4, 10, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), x0 + 2, y0 + 3, (150, 24, 24), h=0.6)
    m = text_mask(body, SANS, civic.fit(body, SANS, 5, x1 - x0 - 2), x1 - x0 - 4, 26, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), x0 + 2, y0 + 16, (34, 30, 28), h=0.6)
    area(t, (x0 + x1) // 2, y0 + 1, (x0 + x1) // 2 + 2, y0 + 3, (190, 40, 30), dh=0.3)
    t.grime(0.25)
    return t


def town_map(i, seed=9760):
    """The town map across two tiles: the street grid, the river, the square, pins, and the red
    marker line somebody drew round the east side with QUARANTINE written along it."""
    full_c = np.zeros((N, 2 * N, 3), np.float32)
    rng = np.random.default_rng(seed)
    base = np.array([226, 216, 188], np.float32)
    full_c[:] = base
    full_c *= (0.97 + 0.06 * rng.random((N, 2 * N, 1)))
    road = np.array([250, 246, 236], np.float32)
    edge = np.array([150, 140, 120], np.float32)
    for x in range(8, 2 * N - 4, 13):
        full_c[4:N - 4, x:x + 2] = road
        full_c[4:N - 4, x + 2] = edge
    for y in range(8, N - 4, 11):
        full_c[y:y + 2, 4:2 * N - 4] = road
        full_c[y + 2, 4:2 * N - 4] = edge
    # the river down the west side, the square in the middle green, the rail line
    for y in range(4, N - 4):
        x = int(18 + 6 * np.sin(y / 9.0))
        full_c[y, x:x + 4] = (90, 130, 176)
    full_c[26:38, 58:70] = (130, 168, 110)
    full_c[48:50, 4:2 * N - 4] = (60, 56, 52)
    # the red line round the east blocks and its pins
    yy, xx = np.mgrid[0:N, 0:2 * N]
    d = np.hypot((xx - 100) / 24.0, (yy - 30) / 22.0)
    full_c[(d > 0.95) & (d < 1.08)] = (190, 30, 28)
    for _ in range(14):
        px, py = int(rng.integers(8, 2 * N - 8)), int(rng.integers(6, N - 6))
        c = (200, 30, 30) if rng.random() < 0.6 else (40, 80, 180)
        full_c[py - 1:py + 2, px - 1:px + 2] = c
    m = text_mask("QUARANTINE", SANS, 6, 46, 8, seed=seed)
    h, w = m.shape
    sub = full_c[52:52 + h, 78:78 + w]
    sub[m > 0.4] = (190, 30, 28)
    t = F.plain(seed + i, (200, 200, 200), 0.0, 0.25)
    t.c[:] = full_c[:, i * N:(i + 1) * N]
    # the frame: walnut on the outer three sides of each tile
    fr = (70, 46, 30)
    area(t, 0, 0, N, 3, fr)
    area(t, 0, N - 3, N, N, fr)
    if i == 0:
        area(t, 0, 0, 3, N, fr)
    else:
        area(t, N - 3, 0, N, N, fr)
    t.grime(0.2)
    return t


def honor(seed):
    """The bronze honour roll: the heading, two columns of names too small to read, green at the edges."""
    t = F.plain(seed, (112, 84, 46), 0.06, 0.55)
    t.f0[:] = 200
    edges(t, 1, 1, N - 1, N - 1, w=2, light=1.25, dark=0.6)
    m = text_mask("HONOR ROLL", SERIF, 7, 54, 10, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 5, 5, (196, 164, 96), h=0.9)
    m = text_mask("1917 - 1918", SERIF, 5, 40, 7, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 12, 14, (196, 164, 96), h=0.9)
    rng = t.rng
    for col in (6, 34):
        for y in range(24, 58, 3):
            area(t, col, y, col + 18 + int(rng.integers(-4, 4)), y + 1, (196, 164, 96), h=0.8)
    yy, xx = np.mgrid[0:N, 0:N]
    vg = (np.minimum(np.minimum(xx, N - 1 - xx), np.minimum(yy, N - 1 - yy)) < 5) & (rng.random((N, N)) < 0.5)
    t.c[vg] = t.c[vg] * 0.5 + np.array([70, 130, 110]) * 0.5
    t.grime(0.2)
    return t


def textures():
    T = {}
    for i in range(8):
        T[f"th_frieze_8_{i}"] = lambda i=i: frieze(i)
    for i in range(2):
        T[f"th_datestone_{i}"] = lambda i=i: datestone(i)
        T[f"th_map_{i}"] = lambda i=i: town_map(i)
    T["th_tin"] = lambda: tin(9720)
    T["th_felt"] = lambda: felt(9721)
    T["th_nameplate"] = lambda: nameplate(9722)
    T["th_curtain"] = lambda: booth_curtain(9723)
    T["th_vote"] = lambda: vote_sign(9724)
    T["th_ballot"] = lambda: ballot(9725)
    T["th_ballot_top"] = lambda: ballot_top(9726)
    T["th_honor"] = lambda: honor(9727)
    for k, (head, body, paper) in enumerate(NOTICES):
        T[f"th_notice_{k}"] = lambda k=k, head=head, body=body, paper=paper: notice(9730 + k, head, body, paper)
    for k, (name, text) in enumerate(PLAQUES.items()):
        T[f"th_plaque_{name}"] = lambda text=text, k=k: civic.plaque(9740 + k, text)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def mirror_x(boxes):
    return [[16 - b[3], b[1], b[2], 16 - b[0], b[4], b[5], *b[6:]] for b in boxes]


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, render=None, tex=None, light=None, loot=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    for i in range(8):
        add(f"th_frieze_8_{i}", None, "Frieze", "STONE", tex={"north": f"th_frieze_8_{i}", "side": STONE, "up": STONE, "down": STONE})
    for i in range(2):
        add(f"th_datestone_{i}", None, "Date Stone", "STONE", tex={"north": f"th_datestone_{i}", "side": STONE, "up": STONE, "down": STONE})
    add("th_floor_tin", None, "Floor", "WOOD", rotate=None, tex={"up": "floor_boards", "down": "th_tin", "side": "cv_plaster"})

    # the council's bench: the panelled front toward the public (north), the felt top, the
    # nameplate along its front edge, a microphone on a gooseneck; the ends curl round
    walnut = "cv_panel_oak"
    top = {"up": "th_felt", "side": "cv_oak", "down": "cv_oak", "north": "cv_oak", "south": "cv_oak"}
    desk = [[0, 0, 0, 16, 13, 3, walnut], [0, 13, 0, 16, 14.5, 11, top], [0, 0, 9, 16, 1, 11, "cv_oak"],
            [2, 14.5, 0.5, 14, 17, 2, {"north": "th_nameplate", "side": "cv_oak", "up": "cv_oak", "south": "cv_oak"}]]
    mic = [[9, 14.5, 5, 11, 15.2, 7, "cv_iron"], [9.6, 15.2, 5.6, 10.4, 20, 6.4, "cv_iron"],
           [9.4, 19.5, 3.2, 10.6, 20.5, 6.4, "cv_iron"], [9.1, 19.2, 2.4, 10.9, 21, 3.4, "cv_iron"]]
    paper = [[3, 14.5, 4.5, 8, 14.8, 9.5, "paper"]]
    add("th_bench", desk + mic + paper, "Council Bench", "WOOD")
    add("th_bench_plain", desk[:3] + paper, "Council Bench", "WOOD")
    end = [[0, 0, 0, 2, 14.5, 11, walnut]]
    add("th_bench_end", desk + mic + end, "Council Bench", "WOOD")
    add("th_bench_end_l", desk + mic + mirror_x(end), "Council Bench", "WOOD")

    # the podium for the public: a walnut lectern, a reading slope, a microphone on a stem
    add("th_podium", [[3, 0, 4, 13, 2, 12, "cv_oak"], [4, 2, 5, 12, 16, 11, walnut], [3, 16, 3, 13, 17.5, 12, top],
                      [7.6, 17.5, 4, 8.4, 24, 4.8, "cv_iron"], [7.2, 23.5, 4.4, 8.8, 25, 7, "cv_iron"]],
        "Podium", "WOOD")

    # the voting booth: a frame of white posts, navy curtains on the sides and half across the
    # front, a shelf at the back to write on; the top carries the rail and the VOTE card
    post = "ext_trim_white"
    posts = [[0, 0, 0, 1, 16, 1, post], [15, 0, 0, 16, 16, 1, post], [0, 0, 15, 1, 16, 16, post], [15, 0, 15, 16, 16, 16, post]]
    cur = {"north": "th_curtain", "south": "th_curtain", "side": "th_curtain", "up": "th_curtain", "down": "th_curtain"}
    add("th_booth", posts + [[0.2, 1, 1, 0.8, 16, 15, cur], [15.2, 1, 1, 15.8, 16, 15, cur], [1, 3, 0.2, 7, 16, 0.8, cur],
                             [1, 0, 15, 15, 16, 15.8, "cv_oak"], [1, 11, 10, 15, 12, 15, "cv_oak"], [4, 12, 11, 9, 12.2, 14, "paper"]],
        "Voting Booth", "WOOL", render="cutout")
    add("th_booth_top", [[0, 0, 0, 1, 3, 1, post], [15, 0, 0, 16, 3, 1, post], [0, 0, 15, 1, 3, 16, post], [15, 0, 15, 16, 3, 16, post],
                         [0, 2, 0, 16, 3, 1, post], [0, 2, 15, 16, 3, 16, post], [0, 2, 0, 1, 3, 16, post], [15, 2, 0, 16, 3, 16, post],
                         [0.2, 0, 1, 0.8, 2, 15, cur], [15.2, 0, 1, 15.8, 2, 15, cur], [1, 0, 0.2, 7, 2, 0.8, cur],
                         [4, 3, 0, 12, 8, 0.6, {"north": "th_vote", "south": "th_vote", "side": post, "up": post, "down": post}]],
        "Voting Booth", "WOOL", decor=True, render="cutout")
    add("th_ballot_box", [[3, 0, 4, 13, 9, 12, {"north": "th_ballot", "up": "th_ballot_top", "side": "cv_oak", "south": "cv_oak", "down": "cv_oak"}]],
        "Ballot Box", "WOOD", decor=True, loot="mail")

    # on the walls
    for k in range(len(NOTICES)):
        add(f"th_notice_{k}", [[0.5, 1, 15, 15.5, 15, 16, {"north": f"th_notice_{k}", "side": "cv_oak", "up": "cv_oak", "down": "cv_oak"}]],
            "Notice Board", "WOOD", decor=True)
    for i in range(2):
        add(f"th_map_{i}", [[0, 2, 15, 16, 14, 16, {"north": f"th_map_{i}", "side": "cv_oak", "up": "cv_oak", "down": "cv_oak"}]],
            "Town Map", "WOOD", decor=True)
    add("th_honor", [[2, 2, 15.2, 14, 14, 16, {"north": "th_honor", "side": "cv_brass", "up": "cv_brass", "down": "cv_brass"}]],
        "Honor Roll", "METAL", decor=True)
    for k in PLAQUES:
        add(f"th_plaque_{k}", [[3, 4, 15.4, 13, 11.5, 16, {"north": f"th_plaque_{k}", "side": "cv_brass"}]], "Door Plaque", "METAL",
            decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("th_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} town hall blocks in {path}")


if __name__ == "__main__":
    main()
