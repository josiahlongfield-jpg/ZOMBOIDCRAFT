"""The civil-defence shelter's kit (cd_), from refs/bunker: fallout-shelter-interior (the Swiss public
shelters: rooms painted yellow, green and orange, grey steel bunks three high, open steel cubbies of
folded blankets, folding tables), bunker-bunk-room (Kelvedon Hatch: cream block, a round diffuser in
the ceiling), bunker-air-filtration (the hand-cranked ventilator and its filter, the blue armoured
escape hatch in the wall).

- cd_wall_yellow, cd_wall_green, cd_wall_orange: the rooms' gloss paint over concrete;
- cd_bunk_head, cd_bunk_head_top, cd_bunk_foot, cd_bunk_foot_top: three tiers in two blocks, grey tube;
- cd_cubby, cd_cubby_top: open steel shelving, a blanket and a pillow folded in each square;
- cd_vent, cd_vent_top: the ventilator, its fan scroll, the crank for when the power goes, the filter;
- cd_escape: the blue armoured hatch to the escape tunnel, bolted shut from this side;
- cd_diffuser: the round air diffuser in the ceiling.

And the forest hatch's (fh_), from refs/bunker/bunker-bunk-room/09 (the Sjokarr shelter: rough spruce
boards on the walls and the floor, sleeping shelves built in): the survivalist lined his rooms himself.

- fh_boards: rough planks laid along the wall, weathered grey-brown, nail heads in rows;
- fh_bunk_head, fh_bunk_head_top, fh_bunk_foot, fh_bunk_foot_top: the bunks in timber, two high.

    python3 tools/city/shelter.py   # writes the blocks into blocks.json (textures: photo_textures.py)
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import bunker as B  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None
WALLS = {"yellow": (214, 182, 74), "green": (120, 160, 86), "orange": (206, 116, 58)}
BLUE = (52, 104, 150)
GREY = (150, 152, 148)


def wall(seed, rgb):
    """Gloss paint rolled over concrete, scuffed where beds and benches knock it."""
    t = B.painted(seed, rgb, "painted_concrete_02", 0.45, 0.45, span=2)
    t.streaks(0.2, 0.92)
    rng = t.rng
    for _ in range(5):
        x, y = rng.uniform(0, N), rng.uniform(30, 56)
        area(t, x, y, x + rng.uniform(3, 9), y + 1, mul=0.82)
    B.chips(t, 0.04, (150, 146, 136))
    return F.wear(t, 0.18, 2)


def escape(seed):
    """The escape hatch: a square armoured door in its frame, wing bolts all round, the stencil."""
    t = B.painted(seed, BLUE, "PaintedMetal001", 0.6, 0.4)
    F.edges(t, 2, 2, N - 2, N - 2, 2, light=1.15, dark=0.6, dh=0.2)
    area(t, 10, 14, N - 10, N - 6, mul=0.92, dh=0.15)
    F.gap(t, 10, 14, N - 10, N - 6, dark=0.35)
    for x, y in ((14, 18), (N - 14, 18), (14, N - 10), (N - 14, N - 10), (N // 2, 18), (N // 2, N - 10)):
        disc(t, x, y, 2.4, (160, 158, 150), dh=0.4)
        area(t, x - 3.5, y - 0.7, x + 3.5, y + 0.7, (150, 148, 140), dh=0.5)
    area(t, N // 2 - 8, N // 2 + 2, N // 2 + 8, N // 2 + 4, (170, 168, 160), dh=0.4)   # the handle
    B.stencil(t, "EMERGENCY EXIT", (236, 232, 220), 7, 6, 4, N - 12, 9, chip=0.02)
    B.rust_runs(t, 4, 0.4, top=14)
    return F.wear(t, 0.2, 3)


def vent_front(seed):
    """The ventilator's face: the fan's scroll, the crank boss, the gauge, the yellow filter plate."""
    t = B.painted(seed, GREY, "PaintedMetal001", 0.6, 0.4)
    disc(t, 22, 30, 16, mul=0.9, dh=0.25)
    disc(t, 22, 30, 16, mul=0.65, ring=1.5)
    disc(t, 22, 30, 4, (90, 92, 90), dh=0.4)
    area(t, 21, 30, 23, 46, (70, 70, 68), dh=0.5)          # the crank arm
    disc(t, 22, 46, 2.5, (40, 38, 36), dh=0.6)              # its handle
    disc(t, 48, 14, 6, (230, 228, 220), dh=0.2)             # the pressure gauge
    disc(t, 48, 14, 6, (40, 40, 40), ring=1)
    area(t, 48, 10, 49, 14, (180, 30, 24))
    area(t, 40, 30, 60, 52, (230, 206, 40), h=0.7)          # ABC filter plate
    F.edges(t, 40, 30, 60, 52, 1, light=1.05, dark=0.7, dh=0.02)
    m = F.text_mask("ABC|FILTER|1200", B.SANS, 4, 18, 18, seed=seed)
    F.paste(t, m, 41, 32, (30, 28, 24))
    B.rust_runs(t, 3, 0.3, top=48)
    return F.wear(t, 0.25, 3)


def boards(seed):
    """Rough sawn planks laid along the wall: uneven widths, gaps, nail heads where they meet the studs."""
    t = F.mat("Planks009", (150, 128, 100), 0.9, rot=True, seed=seed, smooth=0.15)
    rng = t.rng
    y = 0
    while y < N:
        h = int(rng.integers(9, 14))
        area(t, 0, y, N, min(N, y + h), mul=float(rng.uniform(0.82, 1.1)))
        area(t, 0, y, N, y + 1, mul=0.4, dh=-0.35)
        for x in (4, 36):
            disc(t, x, y + h / 2, 0.8, (70, 64, 58), dh=0.2)
        y += h
    t.mix(np.clip(P.fbm(rng) * 1.4 - 0.3, 0, 1), (96, 92, 84), 0.35)
    return F.wear(t, 0.25, 2)


def textures():
    T = {}
    T["fh_boards"] = lambda: boards(9920)
    for k, (name, rgb) in enumerate(WALLS.items()):
        T[f"cd_wall_{name}"] = lambda k=k, rgb=rgb: wall(9900 + k, rgb)
    T["cd_escape"] = lambda: escape(9910)
    T["cd_vent_front"] = lambda: vent_front(9911)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def tier(y, rail=False):
    """One bunk's tier at height y (in 16ths): the tube frame's long sides, the mattress, the blanket."""
    out = [[1, y - 1.5, 0, 2.5, y, 16, "bunk_grey"], [13.5, y - 1.5, 0, 15, y, 16, "bunk_grey"],
           [2.5, y - 1, 0, 13.5, y + 2, 16, {"up": "bunk_mattress", "side": "bunk_mattress", "down": "bunk_grey"}]]
    if rail:
        out.append([13.5, y + 5, 0, 15, y + 6, 16, "bunk_grey"])
    return out


def bunk(head, top):
    """Three tiers in two blocks: at 3 and 11 in the lower block, at 5 in the upper, under a rail."""
    posts = [[1, 0, 14.5, 2.5, 16 if not top else 13, 16, "bunk_grey"], [13.5, 0, 14.5, 15, 16 if not top else 13, 16, "bunk_grey"]]
    if not head:
        posts = [[1, 0, 0, 2.5, 16 if not top else 13, 1.5, "bunk_grey"], [13.5, 0, 0, 15, 16 if not top else 13, 1.5, "bunk_grey"]]
    tiers = (5,) if top else (3, 11)
    out = list(posts)
    for y in tiers:
        out += tier(y, rail=top)
        if head:
            out.append([3.5, y + 2, 11, 12.5, y + 3.5, 15, "home_pillow"])
            out.append([2.5, y + 2, 0, 13.5, y + 2.5, 11, "bunk_blanket"])
        else:
            out.append([2.5, y + 2, 1, 13.5, y + 3.5, 5, "bunk_blanket"])
            out.append([2.5, y + 2, 5, 13.5, y + 2.5, 16, "bunk_blanket"])
    return out


def cubby(top):
    """Open shelving in grey steel, a square for each bed: a folded blanket in each, a pillow on top."""
    out = [[0, 0, 2, 1, 16, 16, "bunk_grey"], [15, 0, 2, 16, 16, 16, "bunk_grey"], [7.5, 0, 2, 8.5, 16, 16, "bunk_grey"],
           [0, 0, 15, 16, 16, 16, "bunk_grey"]]
    shelves = (8, 15) if top else (0, 8)
    for y in shelves:
        out.append([0, y, 2, 16, y + 1, 15, "bunk_grey"])
    for x0, x1 in ((1, 7.5), (8.5, 15)):
        for y in shelves:
            if y + 1 >= 16:
                continue
            out.append([x0 + 0.5, y + 1, 4, x1 - 0.5, y + 3.5, 14, "bunk_blanket"])
            out.append([x0 + 1, y + 3.5, 6, x1 - 1, y + 5, 13, "home_pillow"])
    return out


def timber(spec):
    """A bunk's boxes with its olive steel swapped for timber."""
    out = []
    for b in spec["boxes"]:
        b = list(b)
        if b[6] == "bunk_olive":
            b[6] = "sy_timber"
        elif isinstance(b[6], dict):
            b[6] = {k: ("sy_timber" if v == "bunk_olive" else v) for k, v in b[6].items()}
        out.append(b)
    return out


def blocks():
    out = {}
    have = json.loads((HERE / "blocks.json").read_text())["blocks"]

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    add("fh_boards", None, "Plank Wall", "WOOD", rotate=None, tex={"side": "fh_boards", "up": "floor_boards", "down": "floor_boards"})
    for part in ("head", "head_top", "foot", "foot_top"):
        add(f"fh_bunk_{part}", timber(have[f"bunk_bed_{part}"]), "Bunk", "WOOD")
    for name in WALLS:
        add(f"cd_wall_{name}", None, "Painted Wall", "STONE", rotate=None,
            tex={"side": f"cd_wall_{name}", "up": "bunk_concrete", "down": "bunk_concrete"})
    add("cd_bunk_head", bunk(True, False), "Shelter Bunk", "METAL")
    add("cd_bunk_head_top", bunk(True, True), "Shelter Bunk", "METAL")
    add("cd_bunk_foot", bunk(False, False), "Shelter Bunk", "METAL")
    add("cd_bunk_foot_top", bunk(False, True), "Shelter Bunk", "METAL")
    add("cd_cubby", cubby(False), "Blanket Shelves", "METAL", loot="home_wardrobe")
    add("cd_cubby_top", cubby(True), "Blanket Shelves", "METAL")
    # the ventilator: the fan and its motor on a frame, the crank on the front, the filter canister
    # beside it, the duct up to the ceiling
    add("cd_vent", [[1, 0, 2, 15, 1.5, 15, "bunk_grey"],
                    [1.5, 1.5, 4, 14.5, 15, 14, {"north": "cd_vent_front", "side": "bunk_grey", "up": "bunk_grey", "down": "bunk_grey"}],
                    [3, 7, 2.5, 4, 8, 4, "bunk_grey"]], "Ventilator", "METAL")
    add("cd_vent_top", [[3, 0, 5, 13, 9, 13, {"north": "bunk_grey", "side": "bunk_grey", "up": "ext_galv", "down": "bunk_grey"}],
                        [5.5, 9, 7, 10.5, 16, 12, "ext_galv"], [4.5, 2, 4, 11.5, 7, 5, "bunk_grey"]], "Ventilator", "METAL")
    add("cd_escape", [[0, 0, 14, 16, 16, 16, {"north": "cd_escape", "side": "bunk_grey"}]], "Escape Hatch", "METAL", decor=True)
    add("cd_diffuser", [[2, 15, 2, 14, 16, 14, "ext_galv"], [4, 14, 4, 12, 15, 12, "ext_galv"],
                        [5.5, 13, 5.5, 10.5, 14, 10.5, "bunk_grille"]], "Air Diffuser", "METAL", rotate=None, decor=True)
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    kept = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith(("cd_", "fh_")) or k in made}
    kept.update({k: v for k, v in made.items() if k not in kept})
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} shelter blocks in {path}")


if __name__ == "__main__":
    main()
