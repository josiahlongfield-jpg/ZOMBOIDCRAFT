#!/usr/bin/env python3
"""The fronts of main street's brick and frame blocks, after the Kentucky and Ohio towns in the
photo library (refs/town/main-street-exterior: Wilmore, Smiths Grove, Gibsonburg, Cochise): the
corbelled brick table and moulded stone cornice along a parapet, an Italianate cornice of painted
boards on scroll brackets, arched stone hoods over the upstairs windows, a corbel under an oriel
bay, the stone pediment with its date stone and finial, the long tin gallery over the sidewalk on
fluted cast-iron posts, the plank canopy of a false front, a 1950s remodel's black structural
glass and its flat aluminium canopy hung on rods, the metal coping on its parapet, and the painted
trim and cap boards of a frame front.

    python3 tools/city/fronts.py        rewrites the front_ entries in blocks.json

photo_textures.py draws the textures (fronts.register()); world/county/Storefront.java puts the
pieces up. They follow the exterior kit: the base model faces north, which for anything on a wall
is the way out from it (its back, z = 16, against the wall), so a piece goes in the cell in front
of the wall facing away from it. Pieces that reach out of their cell keep their collision inside
it (gen_blocks clamps front_ shapes).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import exterior as E  # noqa: E402
from furniture import area, disc, paste, sunk, text_mask  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"

BRICKS = ("brick_red", "brick_dark", "brick_buff", "brick_orange")
YEARS = ("1884", "1891", "1896", "1902", "1909")
GLASS = {"black": (22, 24, 26), "green": (38, 74, 62), "cream": (214, 204, 176)}
WOOD_PAINT = {"white": "ext_trim_white", "brown": "ext_trim_brown", "green": "ext_paint_green", "black": "ext_paint_black"}
TIN = {"red": (128, 52, 42), "green": (60, 92, 70)}
STONE = "stone_trim"


# ---------------------------------------------------------------- textures

def datestone(seed, year):
    """A limestone block with the year cut into a sunk panel, the cuts filled with a century of soot."""
    t = NS.build(STONE)
    t.rng = np.random.default_rng(seed)
    area(t, 6, 14, N - 6, N - 14, mul=0.92, dh=-0.06)
    sunk(t, 6, 14, N - 6, N - 14, 2)
    m = text_mask(year, SERIF, 20, N - 16, 30, seed=seed)
    paste(t, m, 8, 17, (70, 66, 58), 0.85, h=0.45)
    # a margin line cut round the panel and a crack through one corner
    for y in (10, N - 11):
        area(t, 3, y, N - 3, y + 1, mul=0.7, dh=-0.1)
    t.crack(1, 0.2)
    t.streaks(0.6, 0.8)
    return t


def vitrolite(seed, rgb):
    """Structural glass: two big polished panels a block, black joints, a chipped corner, grime at the foot."""
    t = F.plain(seed, rgb, 0.025, 0.92)
    t.f0[:] = 40
    rng = t.rng
    for x in (0, 31):
        area(t, x, 0, x + 1, N, (14, 14, 14), smooth=0.2, dh=-0.2)
    area(t, 0, 0, N, 1, (14, 14, 14), smooth=0.2, dh=-0.2)
    # a soft reflection band across the polish
    yy = np.arange(N)[:, None]
    t.c *= (1 + 0.08 * np.exp(-((yy - 20) / 6.0) ** 2))[..., None]
    for _ in range(2):
        x, y = rng.uniform(4, N - 6), rng.uniform(4, N - 6)
        disc(t, x, y, rng.uniform(1.5, 3), (110, 104, 94), smooth=0.1, dh=-0.15)
    t.crack(int(rng.integers(1, 3)), 0.25, col=(8, 8, 8))
    area(t, 0, N - 10, N, N, mul=0.82)
    t.grime(0.12)
    return t


def soffit(seed):
    """The underside of a 1950s canopy: ribbed aluminium panels and a can light gone dark."""
    t = F.steel(seed, (168, 170, 166))
    for x in range(0, N, 8):
        area(t, x, 0, x + 1, N, mul=0.72, dh=-0.1)
        area(t, x + 1, 0, x + 2, N, mul=1.12)
    disc(t, 32, 32, 7, (60, 60, 58), smooth=0.3)
    disc(t, 32, 32, 5, (196, 190, 160), smooth=0.8)
    disc(t, 32, 32, 7, mul=0.7, ring=1)
    t.grime(0.35, (60, 52, 40))
    t.streaks(0.4, 0.8)
    return t


def fascia(seed, stripe):
    """The canopy's enamelled edge band: cream with a coloured stripe, dented and rust-run."""
    t = F.plain(seed, (214, 206, 182), 0.03, 0.6)
    area(t, 0, 24, N, 34, stripe)
    area(t, 0, 23, N, 24, mul=0.7)
    area(t, 0, 34, N, 35, mul=0.7)
    rng = t.rng
    for _ in range(3):
        x = rng.uniform(0, N)
        area(t, x, 34, x + 1.5, N, (118, 66, 34), smooth=0.1)
    for _ in range(2):
        disc(t, rng.uniform(0, N), rng.uniform(4, N - 4), rng.uniform(2, 4), mul=0.85, dh=-0.1)
    t.grime(0.3)
    return t


def iron(seed, fluted=False):
    """Cast iron painted dark green, the paint chipped to rust where carts and bumpers caught it."""
    t = F.plain(seed, (42, 66, 50), 0.05, 0.45)
    if fluted:
        for x in range(0, N, 6):
            area(t, x, 0, x + 2, N, mul=0.7, dh=-0.12)
            area(t, x + 2, 0, x + 3, N, mul=1.15)
    n = P.fbm(t.rng, ((8, 1.0), (16, 0.7), (32, 0.4)))
    m = np.clip((n - 0.45) * 5, 0, 1)
    rust = np.array((110, 62, 34), np.float32) * (0.8 + 0.4 * (P.fbm(t.rng) * 0.5 + 0.5))[..., None]
    t.c = t.c * (1 - m[..., None]) + rust * m[..., None]
    t.smooth *= 1 - m * 0.6
    area(t, 0, N - 8, N, N, mul=0.78)
    t.streaks(0.5, 0.75)
    return t


def tin(seed, rgb):
    """Corrugated tin, the ribs running down the slope, the paint gone to rust along them and at the laps."""
    t = F.plain(seed, rgb, 0.05, 0.4)
    t.f0[:] = 30
    rib = np.sin(np.arange(N) * 2 * np.pi / 8)
    t.c *= (1 + 0.14 * rib)[None, :, None]
    t.h += (rib * 0.08)[None, :]
    n = P.fbm(t.rng, ((4, 1.0), (16, 0.6), (32, 0.5)))
    m = np.clip((n - 0.3) * 3, 0, 1) * (0.5 + 0.5 * (rib[None, :] < -0.3))
    rust = np.array((120, 64, 32), np.float32)
    t.c = t.c * (1 - m[..., None] * 0.8) + rust * (m[..., None] * 0.8)
    area(t, 0, 31, N, 33, mul=0.7, dh=-0.1)
    t.streaks(0.7, 0.72)
    return t


def lampbox(seed):
    """A dead light box's frosted glass: milky, yellowed, flies dead in the bottom, the dirt of years on it."""
    t = F.plain(seed, (208, 204, 186), 0.04, 0.55)
    t.c *= (1 - 0.1 * np.linspace(0, 1, N))[:, None, None]
    rng = t.rng
    for _ in range(14):
        disc(t, rng.uniform(6, N - 6), rng.uniform(N - 12, N - 4), rng.uniform(0.6, 1.2), (30, 26, 22))
    area(t, 0, 0, N, 3, (40, 40, 40), smooth=0.3)
    area(t, 0, N - 3, N, N, (40, 40, 40), smooth=0.3)
    t.grime(0.3, (70, 62, 46))
    t.streaks(0.6, 0.8)
    return t


def textures():
    T = {}
    for y in YEARS:
        T[f"front_datestone_{y}"] = lambda y=y: datestone(8000 + int(y) % 97, y)
    for i, (c, rgb) in enumerate(GLASS.items()):
        T[f"front_vitrolite_{c}"] = lambda rgb=rgb, i=i: vitrolite(8100 + i, rgb)
    T["front_soffit"] = lambda: soffit(8110)
    T["front_fascia_red"] = lambda: fascia(8111, (150, 36, 30))
    T["front_fascia_teal"] = lambda: fascia(8112, (34, 104, 100))
    T["front_iron"] = lambda: iron(8113)
    T["front_iron_fluted"] = lambda: iron(8114, True)
    for i, (c, rgb) in enumerate(TIN.items()):
        T[f"front_tin_{c}"] = lambda rgb=rgb, i=i: tin(8120 + i, rgb)
    T["front_boards"] = lambda: E.paint(8125, (112, 98, 80), 0.5)
    T["front_lampbox"] = lambda: lampbox(8126)
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

    def add(name, boxes, title, sound, rotate="facing", decor=False, render=None, tex=None):
        # a piece of boxes, or a plain cube with its faces' textures
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if render:
            spec["render"] = render
        out[name] = spec

    # ---- the brick table under a parapet's cornice: three courses stepping out, dentils in the middle one
    for b in BRICKS + (STONE,):
        name = "front_corbel_" + ("stone" if b == STONE else b.split("_")[1])
        boxes = [[0, 0, 13, 16, 5, 16, b], [0, 5, 13, 16, 9, 16, b]]
        boxes += [[x, 5, 9, x + 2.5, 9, 13, b] for x in (0.75, 4.75, 8.75, 12.75)]
        boxes += [[0, 9, 8, 16, 13, 16, b], [0, 13, 5, 16, 16, 16, b]]
        add(name, boxes, "Corbelled Cornice", "STONE")

    # ---- the moulded stone cornice on top of it: bed mould, corona with its drip, the wash on top
    add("front_cornice_stone", [[0, 0, 9, 16, 3, 16, STONE], [0, 3, 4, 16, 9, 16, {"down": "stone_trim_soot", "side": STONE}],
                                [0, 9, 2, 16, 12, 16, STONE], [0, 12, 3, 16, 13.5, 16, STONE]], "Stone Cornice", "STONE")

    # ---- an Italianate cornice: frieze board, soffit, fascia and crown; a scroll bracket every other block under it
    for c, paint_ in WOOD_PAINT.items():
        add(f"front_cornice_wood_{c}", [[0, 0, 13, 16, 16, 16, paint_],
                                        [0, 8, 2, 16, 10, 13, {"down": "ext_soffit", "side": paint_, "up": paint_}],
                                        [0, 7, 1, 16, 14, 2.5, paint_], [0, 14, 0, 16, 16, 13, paint_],
                                        [0, 10, 2.5, 16, 11, 13, paint_]], "Wood Cornice", "WOOD")
        add(f"front_bracket_wood_{c}", [[6, 0, 12, 10, 4, 16, paint_], [6.5, 3, 9, 9.5, 8, 16, paint_],
                                        [6.5, 7, 5.5, 9.5, 12, 16, paint_], [6, 11, 2, 10, 16, 16, paint_],
                                        [7, -2, 12.5, 9, 0, 14.5, paint_], [5, 0, 15, 11, 16, 16, paint_]],
            "Cornice Bracket", "WOOD")

    # ---- the stone hood over an upstairs window: a segmental arch with its keystone and dripped ends
    add("front_hood_arch", [[0, 0, 13, 16, 2, 16, STONE], [0, -1.5, 12.5, 2, 2, 16, STONE], [14, -1.5, 12.5, 16, 2, 16, STONE],
                            [1.5, 2, 13, 14.5, 4, 16, STONE], [3.5, 4, 13, 12.5, 5.5, 16, STONE],
                            [6, 5.5, 13, 10, 6.5, 16, STONE], [6.5, 0, 12, 9.5, 7.5, 16, STONE]], "Window Hood", "STONE")
    add("front_hood_flat", [[0, 0, 13, 16, 3.5, 16, STONE], [-0.5, 3.5, 12, 16.5, 5, 16, STONE], [6.5, 0, 12.5, 9.5, 4, 16, STONE]],
        "Window Lintel", "STONE")
    # ---- the corbel an oriel bay stands on
    add("front_bay_corbel", [[0, 9, 0, 16, 16, 16, STONE], [0, 4, 5, 16, 9, 16, STONE], [0, 0, 10, 16, 4, 16, STONE],
                             [0, 15, -0.5, 16, 16, 16, STONE]], "Bay Corbel", "STONE")

    # ---- the pediment over the middle of a front: its sloped stone coping (rising to the middle), date stone and finial
    slope = [[0, 0, 0, 16, 4, 16, STONE], [4, 4, 0, 16, 8, 16, STONE], [8, 8, 0, 16, 12, 16, STONE], [12, 12, 0, 16, 16, 16, STONE],
             [0, 3, -1, 16, 4, 0, STONE]]
    add("front_pediment_l", slope, "Pediment", "STONE")
    add("front_pediment_r", mirror_x(slope), "Pediment", "STONE")
    add("front_finial", [[3.5, 0, 3.5, 12.5, 2, 12.5, STONE], [5.5, 2, 5.5, 10.5, 4, 10.5, STONE], [4.5, 4, 4.5, 11.5, 9, 11.5, STONE],
                         [5.5, 9, 5.5, 10.5, 10.5, 10.5, STONE], [7, 10.5, 7, 9, 14, 9, STONE]], "Finial", "STONE", rotate=None)
    for y in YEARS:
        add(f"front_datestone_{y}", None, "Date Stone", "STONE",
            tex={"north": f"front_datestone_{y}", "side": STONE, "up": STONE, "down": STONE})

    # ---- a 1950s front: black (green, cream) structural glass, the aluminium canopy and its rods, the parapet's coping
    for c in GLASS:
        add(f"front_vitrolite_{c}", None, "Structural Glass", "GLASS", rotate=None, tex=f"front_vitrolite_{c}")
    slab = [0, 12, 0, 16, 15, 16, {"down": "front_soffit", "up": "ext_alu", "side": "ext_alu"}]
    add("front_canopy_metal", [slab], "Canopy", "METAL")
    for c in ("red", "teal"):
        add(f"front_canopy_metal_edge_{c}", [slab, [0, 9, 0, 16, 16, 1.5, {"north": f"front_fascia_{c}", "side": "ext_alu",
                                                                            "south": "ext_alu", "up": "ext_alu", "down": "ext_alu"}]],
            "Canopy", "METAL")
    rod = [[7.4, i * 2, 16 - i * 2 - 2, 8.6, i * 2 + 2.4, 16 - i * 2 + 0.4, "ext_alu"] for i in range(8)]
    add("front_canopy_rod", rod + [[6, 13, 15, 10, 16, 16, "ext_black_metal"], [6.5, 0, 0, 9.5, 1, 2, "ext_black_metal"]],
        "Canopy Rod", "METAL", decor=True)
    # the coping's lips turn down every face, so it can cap a parapet running either way
    add("front_coping_metal", [[0, 0, 0, 16, 2.5, 16, "ext_alu"], [0, -2, -0.5, 16, 2.5, 0, "ext_alu"],
                               [0, -2, 16, 16, 2.5, 16.5, "ext_alu"], [-0.5, -2, 0, 0, 2.5, 16, "ext_alu"],
                               [16, -2, 0, 16.5, 2.5, 16, "ext_alu"]], "Coping", "METAL", rotate=None)

    # ---- the long gallery over the sidewalk: tin on a wall plate and a beam carried by fluted iron posts
    for c in TIN:
        t = f"front_tin_{c}"
        roof = {"up": t, "down": "ext_soffit", "side": t}
        add(f"front_gallery_{c}", [[0, 11, 8, 16, 13, 16, roof], [0, 10, 0, 16, 12, 8, roof],
                                   [0, 6, 14, 16, 10, 16, "front_iron"]], "Gallery Roof", "METAL")
        add(f"front_gallery_{c}_edge", [[0, 9, 8, 16, 11, 16, roof], [0, 8, 0, 16, 10, 8, roof],
                                        [0, 3, 6, 16, 8, 10, "front_iron"], [0, 4, 0, 16, 9, 1.5, "front_iron"],
                                        [0, 7.5, -0.5, 16, 8.5, 0, f"front_fascia_red" if c == "red" else "front_fascia_teal"]],
            "Gallery Roof", "METAL")
    fl = "front_iron_fluted"
    add("front_gallery_post_base", [[4, 0, 4, 12, 3, 12, "front_iron"], [5, 3, 5, 11, 4, 11, "front_iron"],
                                    [6, 4, 6, 10, 16, 10, fl]], "Gallery Post", "METAL", rotate=None)
    add("front_gallery_post", [[6, 0, 6, 10, 16, 10, fl]], "Gallery Post", "METAL", rotate=None)
    top = [[6, 0, 6, 10, 10, 10, fl], [5, 10, 5, 11, 12, 11, "front_iron"], [4.5, 12, 4.5, 11.5, 16, 11.5, "front_iron"]]
    for k in range(4):
        # the scroll brackets out along the beam either side
        top += [[10 + k * 1.5, 4 + k * 2.5, 7.25, 11.5 + k * 1.5, 7 + k * 2.5, 8.75, "front_iron"],
                [4.5 - k * 1.5, 4 + k * 2.5, 7.25, 6 - k * 1.5, 7 + k * 2.5, 8.75, "front_iron"]]
    add("front_gallery_post_top", top, "Gallery Post", "METAL")

    # ---- a false front's plank canopy, sloping off a ledger on the wall to a beam on the posts
    boards = {"up": "front_boards", "down": "ext_porch_boards", "side": "ext_fascia"}
    add("front_canopy_wood", [[0, 10, 8, 16, 12, 16, boards], [0, 9, 0, 16, 11, 8, boards],
                              [0, 6, 14, 16, 10, 16, "ext_trim_brown"]] + [[x, 8, 0, x + 1.5, 10, 16, "ext_trim_brown"] for x in (2, 10)],
        "Plank Canopy", "WOOD")
    add("front_canopy_wood_edge", [[0, 7, 8, 16, 9, 16, boards], [0, 6, 0, 16, 8, 8, boards],
                                   [0, 0, 5, 16, 6, 11, "ext_trim_brown"], [0, 3, 0, 16, 8, 1.5, "ext_fascia"]]
        + [[x, 6, 0, x + 1.5, 8, 16, "ext_trim_brown"] for x in (2, 10)], "Plank Canopy", "WOOD")

    # ---- a shop's light box gone dark (the lit ones are froglights, for the colour Photon gives them)
    add("front_lampbox_dead", [[1, 1, 1, 15, 15, 15, {"side": "front_lampbox", "up": "ext_black_metal", "down": "ext_black_metal"}],
                               [0, 14, 0, 16, 16, 16, "ext_black_metal"], [0, 0, 0, 16, 1.5, 16, "ext_black_metal"]],
        "Light Box", "GLASS", rotate=None)

    # ---- a frame front's painted trim, and the cap board along a false front's top
    for c, t in (("white", "ext_trim_white"), ("brown", "ext_trim_brown")):
        add(f"front_trim_{c}", None, "Trim Board", "WOOD", rotate=None, tex=t)
        add(f"front_cap_{c}", [[0, 8, 13, 16, 16, 16, t], [0, 16, 9, 16, 18, 32, t], [0, 14.5, 9, 16, 16, 13, t]],
            "Cap Board", "WOOD")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("front_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    lines = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in kept.items())
    rest = {k: v for k, v in data.items() if k != "blocks"}
    head = "".join(f'  {json.dumps(k)}: {json.dumps(v)},\n' for k, v in rest.items())
    path.write_text("{\n" + head + '  "blocks": {\n' + lines + "\n  }\n}\n")
    print(f"{len(made)} front blocks in {path}")


if __name__ == "__main__":
    main()
