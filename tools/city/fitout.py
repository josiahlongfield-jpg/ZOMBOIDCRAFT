"""The floor fit-out kit (of_): what the tower floors need that no other kit has. The health club on
an office tower's mid floor or a hotel's spa level (treadmills and bikes in a row at the glass, the
bench press and the power cage, dumbbells racked by the mirror wall, rubber flooring, stretching
mats), the hotel's sky club (light-up dance floor, the DJ booth with its two decks and mixer, the
speaker stacks, the mirror ball, high tables), the pool deck (blue mosaic, the coping, the glass
floor of the glass-bottomed pool, loungers, the ladder), the office (the big photocopier, the
conference table, the whiteboard) and the clinic (the dental chair with its lamp and tray, the
exam couch with its paper roll).

    python3 tools/city/fitout.py        rewrites the of_ entries in blocks.json

photo_textures.py draws the textures (fitout.register()); world/city/Floors.java fits floors with
them. The base model faces north: its front (the side you use it from) is at z = 0, anything on a
wall has its back at z = 16 against the wall.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc, edges  # noqa: E402

N = P.N
HERE = Path(__file__).parent


# ---------------------------------------------------------------- textures

def rubber(seed):
    """Gym flooring: black rubber tiles flecked with colour, the joints between the tiles."""
    t = F.plain(seed, (34, 34, 36), 0.06, 0.25)
    t.speckle(0.08, (90, 96, 104), 0.5, 0.05)
    t.speckle(0.02, (60, 90, 140), 0.6)
    for x in (0, 31):
        area(t, x, 0, x + 1, N, mul=0.55, dh=-0.2)
        area(t, 0, x, N, x + 1, mul=0.55, dh=-0.2)
    t.grime(0.12)
    return t


def mat(seed):
    """A stretching mat: blue foam with its moulded dimples, the edges worn pale."""
    t = F.plain(seed, (40, 74, 140), 0.05, 0.3)
    yy, xx = np.mgrid[0:N, 0:N]
    dimple = ((xx % 8 - 4) ** 2 + (yy % 8 - 4) ** 2) < 3
    t.c[dimple] *= 0.82
    t.h[dimple] -= 0.15
    t.frame(2, (70, 104, 170), h=0.8)
    t.grime(0.15)
    return t


def belt(seed):
    """A treadmill's belt: black ribbed rubber, the side rails in grey with the step strips."""
    t = F.plain(seed, (28, 28, 30), 0.04, 0.2)
    for y in range(0, N, 3):
        area(t, 0, y, N, y + 1, mul=0.8, dh=-0.08)
    area(t, 0, 0, 8, N, (130, 132, 136), h=0.9, smooth=0.5)
    area(t, 56, 0, 64, N, (130, 132, 136), h=0.9, smooth=0.5)
    for y in range(0, N, 4):
        area(t, 1, y, 7, y + 1, mul=0.7)
        area(t, 57, y, 63, y + 1, mul=0.7)
    t.grime(0.1)
    return t


def console(seed):
    """A treadmill or bike console: the dark panel, the dead red LED window, the buttons."""
    t = F.plain(seed, (46, 48, 54), 0.03, 0.55)
    t.f0[:] = 30
    area(t, 10, 10, 54, 30, (24, 10, 10), smooth=0.9)
    for i, x in enumerate(range(14, 50, 9)):
        area(t, x, 14, x + 6, 26, (70, 18, 16))
    for x in range(10, 56, 8):
        area(t, x, 38, x + 6, 44, (200, 40, 30) if x == 10 else (180, 182, 186), dh=0.15)
        edges(t, x, 38, x + 6, 44)
    area(t, 10, 50, 54, 54, (200, 120, 30))  # the safety-key strip
    t.grime(0.12)
    return t


def plate(seed):
    """A weight plate's face: cast iron, the raised rim and hub, the weight cast in."""
    t = F.plain(seed, (40, 40, 42), 0.06, 0.35)
    t.f0[:] = 120
    disc(t, 32, 32, 30, dh=0.2, ring=4)
    disc(t, 32, 32, 9, (90, 90, 94), dh=0.25)
    disc(t, 32, 32, 4, (20, 20, 20), dh=-0.3)
    t.speckle(0.03, (110, 70, 40), 0.5)
    t.grime(0.15)
    return t


def dance(seed, rgb, lit):
    """A light-up dance floor tile: the frosted panel glowing its colour, the chrome frame."""
    base = rgb if lit else tuple(int(v * 0.25) for v in rgb)
    t = F.plain(seed, base, 0.05, 0.85)
    yy, xx = np.mgrid[0:N, 0:N]
    glow = 1 - np.hypot(xx - 31.5, yy - 31.5) / 46
    t.c *= (0.75 + 0.45 * glow)[..., None]
    t.frame(3, (170, 172, 178), h=0.95, smooth=0.8, f0=200)
    t.speckle(0.01, (255, 255, 255), 0.3)
    if lit:
        t.emit[3:-3, 3:-3] = 0.9
    t.grime(0.08)
    return t


def dj_front(seed):
    """The DJ booth's front: black carpet-covered panel, the club's chrome stripe."""
    t = F.plain(seed, (22, 20, 26), 0.06, 0.15)
    t.speckle(0.06, (50, 46, 60), 0.4)
    area(t, 0, 20, N, 24, (190, 192, 200), h=0.9, smooth=0.85, f0=220)
    area(t, 0, 40, N, 42, (160, 30, 140), h=0.85, smooth=0.7)
    t.grime(0.1)
    return t


def decks(seed):
    """Two turntables and the mixer between them, seen from above."""
    t = F.plain(seed, (30, 30, 34), 0.03, 0.5)
    for cx in (14, 50):
        area(t, cx - 13, 6, cx + 13, 58, (54, 54, 58), dh=0.1)
        disc(t, cx, 30, 11, (16, 16, 16), dh=0.1, smooth=0.8)
        disc(t, cx, 30, 4, (170, 40, 40))
        disc(t, cx, 30, 1, (200, 200, 200))
        area(t, cx + 8, 42, cx + 10, 54, (190, 190, 196), f0=220)  # the tone arm
    area(t, 28, 8, 36, 56, (60, 62, 68), dh=0.15)
    for y in range(12, 52, 6):
        area(t, 30, y, 34, y + 2, (200, 200, 206))
    t.grime(0.1)
    return t


def speaker(seed):
    """A speaker cabinet's front: the black grille cloth, two cones showing through, the corners."""
    t = F.plain(seed, (26, 26, 28), 0.04, 0.2)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c[(xx + yy) % 2 == 0] *= 0.85
    disc(t, 32, 20, 13, mul=0.7, dh=-0.2)
    disc(t, 32, 20, 4, mul=1.4)
    disc(t, 32, 48, 9, mul=0.7, dh=-0.2)
    for x, y in ((3, 3), (60, 3), (3, 60), (60, 60)):
        disc(t, x, y, 3, (160, 160, 166), f0=220)
    t.grime(0.12)
    return t


def mirrorball(seed):
    """A mirror ball's facets: little square mirrors catching the light in all directions."""
    t = F.plain(seed, (160, 164, 172), 0.02, 0.95)
    t.f0[:] = 230
    rng = t.rng
    for y in range(0, N, 4):
        for x in range(0, N, 4):
            area(t, x, y, x + 3, y + 3, mul=rng.uniform(0.5, 1.5))
            area(t, x + 3, y, x + 4, y + 4, (40, 40, 44))
            area(t, x, y + 3, x + 4, y + 4, (40, 40, 44))
    t.emit[rng.random((N, N)) < 0.05] = 0.6
    return t


def pool_tile(seed):
    """The pool's blue mosaic: small glazed squares, the grout gone green at the edges."""
    t = F.plain(seed, (40, 110, 160), 0.04, 0.85)
    t.f0[:] = 40
    rng = t.rng
    for y in range(0, N, 8):
        for x in range(0, N, 8):
            area(t, x, y, x + 8, y + 8, mul=rng.uniform(0.85, 1.12))
            area(t, x, y, x + 8, y + 1, (190, 200, 196), smooth=0.2)
            area(t, x, y, x + 1, y + 8, (190, 200, 196), smooth=0.2)
    t.grime(0.18, (40, 70, 40))
    return t


def coping(seed):
    """The pool's edge: pale bullnosed stone with the dark tile band and the depth marks."""
    t = F.plain(seed, (200, 194, 180), 0.05, 0.4)
    t.speckle(0.05, (150, 144, 130), 0.4)
    area(t, 0, 0, N, 6, mul=1.08, dh=0.1)
    area(t, 0, 52, N, 64, (20, 40, 90))
    t.grime(0.15)
    return t


def pool_glass(seed):
    """The glass floor of the hanging pool: thick laminated glass, the steel frame, a little green."""
    t = F.plain(seed, (150, 196, 200), 0.02, 0.95)
    t.alpha[:] = 70
    t.f0[:] = 70
    t.frame(2, (120, 124, 130), h=0.95, smooth=0.6, f0=200)
    t.alpha[:2, :], t.alpha[-2:, :], t.alpha[:, :2], t.alpha[:, -2:] = 255, 255, 255, 255
    return t


def whiteboard(seed):
    """A whiteboard: the last meeting's flow chart half rubbed out, the marker tray's aluminium frame."""
    t = F.plain(seed, (226, 228, 226), 0.02, 0.85)
    t.frame(2, (170, 172, 176), f0=200)
    rng = t.rng
    for _ in range(5):
        x, y = rng.integers(6, 46, 2)
        area(t, x, y, x + 12, y + 1, (30, 50, 150))
        area(t, x, y + 8, x + 12, y + 9, (30, 50, 150))
        area(t, x, y, x + 1, y + 9, (30, 50, 150))
        area(t, x + 11, y, x + 12, y + 9, (30, 50, 150))
    for _ in range(3):
        x, y = rng.integers(6, 50, 2)
        area(t, x, y, x + 10, y + 1, (160, 30, 30))
    t.grime(0.06)
    return t


def copier_top(seed):
    t = F.plain(seed, (196, 192, 180), 0.03, 0.5)
    area(t, 6, 6, 44, 58, (60, 70, 70), smooth=0.9)  # the platen glass under the lid shadow
    area(t, 48, 8, 60, 30, (70, 72, 76))
    for i, y in enumerate(range(10, 28, 6)):
        area(t, 50, y, 58, y + 3, (60, 160, 60) if i == 0 else (200, 200, 196))
    t.grime(0.12)
    return t


def copier_front(seed):
    t = F.plain(seed, (190, 186, 174), 0.03, 0.45)
    for y in (18, 34, 50):
        F.gap(t, 4, y, 60, y + 14)
        area(t, 24, y + 5, 40, y + 7, (90, 90, 94))
    area(t, 4, 4, 60, 14, (176, 172, 160))
    t.grime(0.15)
    return t


def vinyl(seed, rgb):
    """Clinic upholstery: wipe-clean vinyl with its stitched seams."""
    t = F.plain(seed, rgb, 0.04, 0.6)
    for y in (21, 42):
        area(t, 0, y, N, y + 1, mul=0.7, dh=-0.15)
    t.grime(0.12)
    return t


def exam_paper(seed):
    t = F.plain(seed, (232, 230, 222), 0.03, 0.2)
    for y in range(0, N, 6):
        area(t, 0, y, N, y + 1, mul=0.94)
    area(t, 0, 0, N, 3, mul=0.85)
    t.grime(0.08)
    return t


def textures():
    T = {}
    T["of_rubber"] = lambda: rubber(9901)
    T["of_mat"] = lambda: mat(9902)
    T["of_belt"] = lambda: belt(9903)
    T["of_console"] = lambda: console(9904)
    T["of_plate"] = lambda: plate(9905)
    for k, rgb in (("a", (220, 40, 160)), ("b", (40, 120, 230)), ("c", (240, 200, 40)), ("d", (40, 210, 120))):
        T[f"of_dance_{k}"] = lambda k=k, rgb=rgb: dance(9910 + ord(k), rgb, True)
        T[f"of_dance_{k}_off"] = lambda k=k, rgb=rgb: dance(9910 + ord(k), rgb, False)
    T["of_dj_front"] = lambda: dj_front(9920)
    T["of_decks"] = lambda: decks(9921)
    T["of_speaker"] = lambda: speaker(9922)
    T["of_mirrorball"] = lambda: mirrorball(9923)
    T["of_pool_tile"] = lambda: pool_tile(9930)
    T["of_coping"] = lambda: coping(9931)
    T["of_pool_glass"] = lambda: pool_glass(9932)
    T["of_whiteboard"] = lambda: whiteboard(9940)
    T["of_copier_top"] = lambda: copier_top(9941)
    T["of_copier_front"] = lambda: copier_front(9942)
    T["of_vinyl_teal"] = lambda: vinyl(9950, (60, 128, 132))
    T["of_vinyl_black"] = lambda: vinyl(9951, (36, 36, 40))
    T["of_exam_paper"] = lambda: exam_paper(9952)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        if rotate == "axis" and boxes:
            boxes = P.along_z(boxes)
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

    black, chrome, steel, grey, alu = "home_plastic_black", "home_chrome", "home_steel", "bunk_grey", "ext_alu"
    white, beige, lam = "home_white", "home_plastic_beige", "desk_laminate"
    pad = "of_vinyl_black"

    def face(front, side, up=None):
        return {"north": front, "side": side, "up": up or side, "down": side}

    # ---- the health club
    add("of_rubber", None, "Gym Flooring", "STONE", rotate=None, tex={"up": "of_rubber", "side": "floor_concrete", "down": "floor_concrete"})
    add("of_mat", [[1, 0, 0, 15, 0.8, 16, {"up": "of_mat", "side": "of_mat"}]], "Exercise Mat", "WOOL", decor=True)
    # treadmill: you run north, towards the console
    add("of_treadmill", [[2, 0, 1, 14, 2.5, 16, {"up": "of_belt", "side": black}], [1, 0, 1, 2, 3.5, 16, grey], [14, 0, 1, 15, 3.5, 16, grey],
                         [1.5, 2.5, 1.5, 2.5, 13, 2.5, grey], [13.5, 2.5, 1.5, 14.5, 13, 2.5, grey],
                         [1.5, 10, 2.5, 2.5, 11, 9, black], [13.5, 10, 2.5, 14.5, 11, 9, black],
                         [2, 12, 0.5, 14, 15.5, 4, face("of_console", black, "of_console")]], "Treadmill", "METAL")
    # exercise bike: you pedal facing north
    add("of_bike", [[3, 0, 1, 13, 1, 3, grey], [3, 0, 13, 13, 1, 15, grey], [7, 0, 2, 9, 2, 14, grey],
                    [7, 1, 2, 9, 8, 7, {"side": "of_plate", "north": grey, "south": grey, "up": grey, "down": grey}],
                    [7.5, 2, 6, 8.5, 10, 12, grey], [6, 9.5, 10, 10, 11, 14, pad],
                    [7.5, 6, 3, 8.5, 13, 4, grey], [4, 12.5, 2.5, 12, 13.5, 3.5, black],
                    [5.5, 13.5, 2, 10.5, 16, 4, face("of_console", black, "of_console")]], "Exercise Bike", "METAL")
    # bench press: lie on the bench with your head at the north uprights, the bar racked over you
    add("of_bench_press", [[5.5, 5, 3, 10.5, 7, 15, pad], [7, 0, 4, 9, 5, 5, steel], [7, 0, 13, 9, 5, 14, steel], [5, 0, 13, 11, 1, 15, steel],
                           [3, 0, 1, 4, 12, 2, steel], [12, 0, 1, 13, 12, 2, steel], [2.5, 0, 0.5, 13.5, 1, 3, steel],
                           [0, 11, 1, 16, 11.8, 1.8, chrome],
                           [0, 7.5, -1.5, 1.5, 15.5, 4.5, {"side": "of_plate", "east": "of_plate", "west": "of_plate", "up": black, "down": black}],
                           [14.5, 7.5, -1.5, 16, 15.5, 4.5, {"side": "of_plate", "east": "of_plate", "west": "of_plate", "up": black, "down": black}]],
        "Weight Bench", "METAL")
    # power cage: four posts, the bar on the hooks, plates stacked on the back pegs
    posts = [[x, 0, z, x + 1.5, 16, z + 1.5, steel] for x in (1, 13.5) for z in (1, 13.5)]
    add("of_squat_rack", posts + [[1, 15, 1, 16, 16, 2.5, steel], [1, 15, 13.5, 15, 16, 15, steel], [1, 0, 1, 2.5, 1, 15, steel], [13.5, 0, 1, 15, 1, 15, steel],
                                  [0, 11, 2.8, 16, 11.8, 3.6, chrome],
                                  [3, 1, 14, 13, 9, 15.5, {"south": "of_plate", "north": "of_plate", "side": black, "up": black, "down": black}]],
        "Power Rack", "METAL")
    bells = []
    for tier, (y, z) in enumerate(((7, 4), (12, 9))):
        bells += [[1, y - 1, z - 1, 15, y, z + 4, steel]]
        for x in range(2, 15, 3):
            bells += [[x, y, z, x + 2, y + 2.5, z + 3, "of_plate"]]
    add("of_dumbbells", bells + [[1, 0, 3, 2, 12, 15, steel], [14, 0, 3, 15, 12, 15, steel], [1, 0, 13, 15, 1, 15, steel]], "Dumbbell Rack", "METAL")
    add("of_plate_tree", [[7, 0, 7, 9, 14, 9, steel], [4, 0, 4, 12, 1, 12, steel],
                          [4, 2, 6, 7, 12, 10, {"side": "of_plate", "west": "of_plate", "east": "of_plate", "up": black, "down": black}],
                          [9, 1, 6, 12, 9, 10, {"side": "of_plate", "west": "of_plate", "east": "of_plate", "up": black, "down": black}]],
        "Weight Plates", "METAL")
    add("of_mirror", [[0, 1, 15.4, 16, 16, 16, {"north": "home_mirror", "side": alu, "up": alu, "down": alu}]], "Mirror Wall", "GLASS", decor=True)

    # ---- the sky club
    for k in ("a", "b", "c", "d"):
        add(f"of_dance_{k}", None, "Dance Floor", "GLASS", rotate=None, tex={"up": f"of_dance_{k}", "side": "floor_concrete", "down": "floor_concrete"}, light=9)
        add(f"of_dance_{k}_off", None, "Dance Floor", "GLASS", rotate=None, tex={"up": f"of_dance_{k}_off", "side": "floor_concrete", "down": "floor_concrete"})
    # the DJ booth faces the crowd north; the DJ stands behind it
    add("of_dj_booth", [[0, 0, 0, 16, 11, 2.5, face("of_dj_front", "of_dj_front")], [0, 0, 2.5, 16, 10, 14, "of_dj_front"],
                        [0, 10, 1, 16, 11, 15, {"up": "of_decks", "side": black, "down": black}],
                        [2, 11, 4, 14, 11.6, 12, {"up": "of_decks", "side": black}]], "DJ Booth", "METAL", loot="storage")
    add("of_speaker", [[1.5, 0, 2, 14.5, 16, 14, face("of_speaker", "of_dj_front", black)]], "Speaker Stack", "WOOD")
    add("of_high_table", [[7, 0, 7, 9, 11, 9, chrome], [4, 0, 4, 12, 0.6, 12, chrome],
                          [2, 11, 2, 14, 12, 14, {"up": "home_vanity_top", "side": black, "down": black}]], "High Table", "METAL")
    add("of_mirrorball", [[7.6, 12, 7.6, 8.4, 16, 8.4, chrome], [5, 6, 5, 11, 12, 11, "of_mirrorball"]], "Mirror Ball", "GLASS", rotate=None,
        decor=True, light=6)

    # ---- the pool deck
    add("of_pool_tile", None, "Pool Tile", "STONE", rotate=None, tex={"all": "of_pool_tile"})
    add("of_coping", None, "Pool Coping", "STONE", rotate=None, tex={"up": "of_coping", "side": "of_pool_tile", "down": "floor_concrete"})
    add("of_pool_glass", None, "Pool Glass", "GLASS", rotate=None, tex={"all": "of_pool_glass"}, render="translucent")
    add("of_lounger", [[2, 0, 1, 3, 4, 2, alu], [13, 0, 1, 14, 4, 2, alu], [2, 0, 14, 3, 4, 15, alu], [13, 0, 14, 14, 4, 15, alu],
                       [2, 4, 1, 14, 5, 16, {"up": "home_fab_blue", "side": alu}], [2, 5, 11, 14, 12, 15, {"north": "home_fab_blue", "side": alu, "up": alu}]],
        "Sun Lounger", "WOOD")
    add("of_pool_ladder", [[2, 0, 9, 3.2, 15, 10.2, chrome], [12.8, 0, 9, 14, 15, 10.2, chrome], [2, 14, 10, 3.2, 15.2, 16, chrome],
                           [12.8, 14, 10, 14, 15.2, 16, chrome]] + [[3, y, 9.2, 13, y + 0.8, 11, steel] for y in (2, 6, 10)],
        "Pool Ladder", "METAL", decor=True)

    # ---- the office
    add("of_copier", [[1, 0, 2, 15, 12, 14, face("of_copier_front", beige, "of_copier_top")], [1, 12, 2, 13, 13, 14, {"up": beige, "side": beige}],
                      [15, 6, 4, 16, 8, 12, beige]], "Photocopier", "METAL")
    top = {"up": "home_top_walnut", "side": "home_wood_walnut", "down": "home_wood_walnut"}
    add("of_conf_table", [[0, 10.5, 0, 16, 12, 16, top], [6, 0, 4, 10, 10.5, 12, "home_wood_walnut"]], "Conference Table", "WOOD", rotate="axis")
    add("of_whiteboard", [[0, 4, 15.2, 16, 15, 16, face("of_whiteboard", alu)], [0, 3.5, 13.8, 16, 4, 16, alu]], "Whiteboard", "METAL", decor=True)

    # ---- the clinic
    add("of_dental_chair", [[6, 0, 6, 10, 4, 10, white], [4, 0, 4, 12, 0.8, 12, white], [4, 4, 2, 12, 6.5, 12, "of_vinyl_teal"],
                            [4, 6.5, 10, 12, 9, 15, "of_vinyl_teal"], [5, 9, 13, 11, 13, 16, "of_vinyl_teal"], [4, 5.5, 0, 12, 6.5, 2, "of_vinyl_teal"],
                            [13, 0, 13, 14, 15, 14, white], [8, 14.5, 12, 14, 15.5, 13, white], [6, 13.5, 11, 9, 15, 13, {"down": "fit_lens_off", "side": white}],
                            [13, 8, 3, 16, 8.6, 8, steel]], "Dental Chair", "METAL")
    add("of_exam_table", [[1, 0, 1, 15, 7, 15, face("home_drw_white", white)], [0.5, 7, 0.5, 15.5, 9, 15.5, "of_vinyl_teal"],
                          [3, 9, 1, 13, 9.3, 15, {"up": "of_exam_paper", "side": "of_exam_paper"}],
                          [2, 9, 12, 14, 12, 15.5, "of_vinyl_teal"], [2.5, 12, 14.5, 13.5, 13.5, 15.5, {"side": "of_exam_paper", "up": "of_exam_paper"}]],
        "Exam Couch", "WOOL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("of_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} fit-out blocks in {path}")


if __name__ == "__main__":
    main()
