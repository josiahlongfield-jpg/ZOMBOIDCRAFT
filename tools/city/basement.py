#!/usr/bin/env python3
"""The county's basements: painted cinder-block walls going damp at the foot, the joists and
subfloor of the house overhead, a bare bulb on a pull chain, the furnace with its ducts, and
what people kept down there: shelves of home-canned preserves and store-bought cans, a first-aid
stockpile, ammo cans and boxes, a steel gun safe, blue water jugs, a wine rack.

    python3 tools/city/basement.py        rewrites the cellar_ entries in blocks.json

photo_textures.py draws the textures (basement.register()). Pieces follow furniture.py's rule:
the base model faces north, its back (z = 16) against the wall, so a piece's facing is the way
it looks into the room. The shelves' default tables are the basement_ ones in shop_loot.py;
House's Basement names its own table on each (food, medicine or guns) by what the room was for.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()

# the shelves' compartments, as texture rows (boards at y 0, 5, 10 and the top at 15)
ROWS = ((4, 20), (24, 40), (44, 60))


def back(seed):
    """The dark back of a shelf unit, dusty, a cobweb in one top corner."""
    t = F.plain(seed, (40, 34, 28), 0.05, 0.2)
    rng = t.rng
    if rng.random() < 0.7:
        x0 = 4 if rng.random() < 0.5 else N - 16
        for k in range(5):
            a = rng.uniform(0, 1.5)
            for r in range(1, 12):
                x, y = int(x0 + 6 + np.cos(a) * r), int(5 + np.sin(a) * r)
                if 0 <= x < N and 0 <= y < N:
                    t.c[y, x] = t.c[y, x] * 0.4 + np.array((200, 200, 196), np.float32) * 0.6
    return t


def preserves(seed):
    """Mason jars of what the garden gave: peaches, pickles, tomatoes, beets, jam, corn; gold
    and silver lids, masking-tape labels, a few emptied or broken."""
    t = back(seed)
    rng = t.rng
    fills = [(214, 132, 44), (112, 128, 46), (168, 46, 30), (106, 22, 42), (86, 22, 58), (206, 176, 58),
             (196, 164, 104), (92, 110, 48)]
    for y0, y1 in ROWS:
        x = 3
        while x < 58:
            if rng.random() < 0.14:
                x += int(rng.integers(4, 9))
                continue
            w = int(rng.integers(6, 9))
            h = int(rng.integers(10, y1 - y0 - 1))
            top = y1 - h
            empty = rng.random() < 0.12
            col = np.array(fills[rng.integers(len(fills))], np.float32) * rng.uniform(0.8, 1.1)
            glass = np.array((150, 160, 150), np.float32)
            body = glass * 0.7 if empty else col
            F.area(t, x, top + 2, x + w, y1, body, h=0.9, smooth=0.85, f0=40)
            if not empty:
                level = int(rng.integers(0, 3))
                F.area(t, x, top + 2, x + w, top + 2 + level, glass * 0.8, smooth=0.85)
            F.area(t, x + 1, top + 3, x + 2, y1 - 1, mul=1.45)  # the glass's highlight
            lid = (196, 160, 70) if rng.random() < 0.6 else (176, 176, 170)
            F.area(t, x, top, x + w, top + 2, lid, h=1.0, smooth=0.6, f0=180)
            if rng.random() < 0.6:
                ly = top + 4 + int(rng.integers(0, 3))
                F.area(t, x + 1, ly, x + w - 1, ly + 2, (226, 214, 176))  # the label
                F.area(t, x + 2, ly + 1, x + w - 2, ly + 2, (60, 50, 40))
            x += w + int(rng.integers(0, 2))
    return F.wear(t, 0.35, 0, (90, 84, 70))


def cans(seed):
    """Store-bought stock: cans stacked two high, flats of bottled water, a sack of rice."""
    t = back(seed)
    rng = t.rng
    for y0, y1 in ROWS:
        x = 3
        while x < 58:
            r = rng.random()
            if r < 0.12:
                x += int(rng.integers(4, 9))
            elif r < 0.25 and x < 44:
                # a flat of water bottles in its shrink-wrapped tray
                w = 14
                for bx in range(x, x + w, 3):
                    F.area(t, bx, y0 + 4, bx + 3, y1, (150, 190, 214), h=0.9, smooth=0.9, f0=40)
                    F.area(t, bx, y0 + 4, bx + 3, y0 + 6, (40, 90, 180))
                    F.area(t, bx + 1, y0 + 6, bx + 2, y1, mul=1.3)
                F.area(t, x, y1 - 4, x + w, y1, (160, 128, 86))
                x += w + 1
            elif r < 0.33 and x < 48:
                w = 10  # a sack of rice or beans, slumped
                F.area(t, x, y0 + 5, x + w, y1, (214, 204, 176), h=0.85, smooth=0.1)
                F.area(t, x + 1, y0 + 9, x + w - 1, y0 + 12, (170, 40, 34))
                x += w + 1
            else:
                w = 6
                for k, cy in enumerate((y1 - 8, y1 - 16)):
                    if k == 1 and rng.random() < 0.3:
                        break
                    col = F.CANS[rng.integers(len(F.CANS))]
                    F.area(t, x, cy, x + w, cy + 8, col, h=0.9, smooth=0.4, f0=40)
                    F.area(t, x, cy, x + w, cy + 1, (190, 190, 184), smooth=0.7, f0=200)
                    F.area(t, x + 1, cy + 3, x + w - 1, cy + 5, (236, 230, 214))
                x += w + 1
    return F.wear(t, 0.35, 0, (90, 84, 70))


def medical(seed):
    """A stockpile of first aid: white boxes with red crosses, pill bottles, gauze, peroxide,
    a red kit, boxes of gloves."""
    t = back(seed)
    rng = t.rng
    for y0, y1 in ROWS:
        x = 3
        while x < 58:
            r = rng.random()
            if r < 0.12:
                x += int(rng.integers(4, 9))
                continue
            if r < 0.4:
                w, h = int(rng.integers(9, 13)), int(rng.integers(8, 14))
                F.area(t, x, y1 - h, x + w, y1, (232, 230, 222), h=0.9)
                cx, cy = x + w // 2, y1 - h // 2
                F.area(t, cx - 1, cy - 3, cx + 1, cy + 3, (196, 30, 30))
                F.area(t, cx - 3, cy - 1, cx + 3, cy + 1, (196, 30, 30))
            elif r < 0.6:
                w = 4
                for k in range(int(rng.integers(2, 4))):
                    bx = x + k * 5
                    F.area(t, bx, y1 - 8, bx + w, y1, (214, 120, 30), h=0.9, smooth=0.8, f0=40)
                    F.area(t, bx, y1 - 10, bx + w, y1 - 8, (240, 240, 236), h=1.0)
                    F.area(t, bx, y1 - 6, bx + w, y1 - 3, (240, 236, 224))
                w = 15
            elif r < 0.75:
                w = 5  # peroxide
                F.area(t, x, y1 - 12, x + w, y1, (80, 46, 26), h=0.9, smooth=0.8, f0=40)
                F.area(t, x + 1, y1 - 14, x + w - 1, y1 - 12, (240, 240, 236))
                F.area(t, x, y1 - 8, x + w, y1 - 4, (236, 232, 220))
            elif r < 0.88:
                w = 12  # the red kit
                F.area(t, x, y1 - 10, x + w, y1, (176, 30, 28), h=0.9)
                F.area(t, x + 4, y1 - 12, x + 8, y1 - 10, (40, 40, 40))
                F.area(t, x + 5, y1 - 8, x + 7, y1 - 2, (240, 240, 236))
                F.area(t, x + 3, y1 - 6, x + 9, y1 - 4, (240, 240, 236))
            else:
                w = 10  # gloves
                F.area(t, x, y1 - 9, x + w, y1, (70, 110, 190), h=0.9)
                F.area(t, x + 2, y1 - 7, x + w - 2, y1 - 5, (236, 236, 236))
            x += w + 1
    return F.wear(t, 0.3, 0, (90, 84, 70))


def ammo(seed):
    """Olive ammo cans with their stencils, bright cartridge boxes, shotgun shell boxes, loose brass."""
    t = back(seed)
    rng = t.rng
    for y0, y1 in ROWS:
        x = 3
        while x < 58:
            r = rng.random()
            if r < 0.1:
                x += int(rng.integers(4, 8))
                continue
            if r < 0.45 and x < 46:
                w, h = 12, 11
                F.area(t, x, y1 - h, x + w, y1, (74, 82, 52), h=0.9, smooth=0.35, f0=40)
                F.area(t, x, y1 - h, x + w, y1 - h + 2, (60, 66, 42))
                F.area(t, x + 3, y1 - h - 1, x + 9, y1 - h, (40, 40, 36))  # the handle
                F.area(t, x + 2, y1 - 6, x + w - 2, y1 - 5, (210, 190, 80))
                F.area(t, x + 3, y1 - 4, x + w - 3, y1 - 3, (210, 190, 80))
            elif r < 0.75:
                w, h = int(rng.integers(6, 9)), int(rng.integers(5, 8))
                col = [(200, 40, 34), (220, 180, 40), (40, 110, 60), (30, 60, 120)][rng.integers(4)]
                for k in range(int(rng.integers(1, 3))):
                    by = y1 - h * (k + 1)
                    F.area(t, x, by, x + w, by + h, col, h=0.9)
                    F.area(t, x + 1, by + 2, x + w - 1, by + 3, (236, 230, 210))
            else:
                w = 8
                for k in range(int(rng.integers(3, 7))):
                    bx = x + int(rng.integers(0, w - 1))
                    F.area(t, bx, y1 - 3, bx + 1, y1, (200, 160, 70), h=0.95, smooth=0.7, f0=200)
            x += w + 1
    return F.wear(t, 0.35, 0, (90, 84, 70))


def cinder(seed, damp=False):
    """Painted cinder block in running bond: grey-white masonry paint over the block's pores,
    sunk mortar joints, white efflorescence weeping from them; a damp wall darkens from the
    floor up and grows black mould."""
    t = F.plain(seed, (196, 194, 184), 0.04, 0.15)
    rng = t.rng
    t.c *= (1 + P.fbm(rng, ((4, 0.4), (2, 0.6))) * 0.06)[..., None]
    pores = rng.random((N, N)) < 0.08
    t.c[pores] *= 0.82
    t.h[pores] -= 0.08
    for row, y in enumerate(range(0, N, 16)):
        F.area(t, 0, y, N, y + 2, mul=0.72, dh=-0.25)
        off = 0 if row % 2 == 0 else 16
        for x in range(off, N + 32, 32):
            F.area(t, x % N, y, x % N + 2, y + 16, mul=0.72, dh=-0.25)
    for _ in range(3):
        # efflorescence weeping down from a bed joint
        x, y = int(rng.integers(1, N - 1)), int(rng.choice(range(2, N, 16)))
        n = int(rng.integers(6, 20))
        fade = np.linspace(0.5, 0, n)[:, None, None]
        t.c[y:y + n, x - 1:x + 1] = t.c[y:y + n, x - 1:x + 1] * (1 - fade[:N - y]) + np.float32(232) * fade[:N - y]
    t.grime(0.25)
    if damp:
        rise = (np.linspace(0, 1, N) ** 2.2)[:, None] * (0.8 + 0.4 * P.fbm(rng)[..., None].mean(axis=-1))
        t.c *= (1 - np.clip(rise, 0, 0.55))[..., None]
        for _ in range(5):
            # mould spreads in freckled patches low on the wall
            cx, cy = rng.uniform(0, N), rng.uniform(36, N - 4)
            for _ in range(int(rng.integers(6, 14))):
                F.disc(t, cx + rng.normal(0, 3), cy + rng.normal(0, 2), rng.uniform(0.4, 1.2), mul=0.45)
    return t


def joist(seed):
    return F.wear(F.wood(seed, "pine", rot=True), 0.35, 4)


def subfloor(seed):
    """The underside of the floor above: planks laid across the joists, their nail rows, a stain."""
    t = F.wood(seed, "pine")
    for x in range(0, N, 16):
        F.area(t, x, 0, x + 1, N, mul=0.55, dh=-0.2)
    for x in range(8, N, 32):
        for y in range(4, N, 12):
            F.area(t, x, y, x + 1, y + 1, (60, 56, 50))
    F.stain(t, 40, 24, 9, (90, 70, 40), 0.4)
    return F.wear(t, 0.4, 0)


def furnace(seed):
    """An oil furnace's front: beige enamel panels, louvres at the foot, the sight glass, a
    maker's plate and the scorch above the burner door."""
    t = F.enamel(seed, (196, 186, 160))
    F.area(t, 0, 30, N, 32, mul=0.6)
    for y in range(42, 60, 3):
        F.area(t, 8, y, 56, y + 1, mul=0.45, dh=-0.2)
    F.area(t, 26, 18, 38, 24, (30, 26, 22), h=0.9)
    F.area(t, 29, 20, 35, 22, (210, 120, 40))
    F.area(t, 22, 6, 42, 11, (160, 150, 120))
    F.area(t, 24, 8, 40, 9, (60, 50, 40))
    F.stain(t, 32, 14, 8, (60, 50, 40), 0.4)
    t.streaks(0.6, 0.8)
    return F.wear(t, 0.4, 6)


def safe(seed, top):
    """A steel gun safe in dark green hammertone: the combination dial and three-spoke handle
    on the top block, its maker's badge; the lower door plain but for the bolt line."""
    t = F.plain(seed, (40, 56, 44), 0.05, 0.55)
    t.f0[:] = 60
    t.c *= (1 + P.fbm(t.rng, ((16, 0.5), (8, 0.5))) * 0.08)[..., None]
    F.area(t, 4, 0, 6, N, mul=0.6)
    F.area(t, 58, 0, 60, N, mul=0.6)
    if top:
        F.area(t, 4, 0, 60, 2, mul=0.6)
        F.disc(t, 32, 36, 6, (170, 170, 164), h=1.0, smooth=0.7, f0=200)
        F.disc(t, 32, 36, 2, (40, 40, 40))
        for a in (0.5, 2.6, 4.7):
            F.area(t, int(32 + np.cos(a) * 9) - 1, int(48 + np.sin(a) * 6) - 1, int(32 + np.cos(a) * 9) + 1,
                   int(48 + np.sin(a) * 6) + 1, (190, 190, 184), h=1.0, f0=200)
        F.area(t, 24, 12, 40, 18, (190, 160, 70), h=1.0, f0=180)
    else:
        F.area(t, 4, 62, 60, 64, mul=0.6)
    return F.wear(t, 0.3, 6)


def jug(seed):
    t = F.plain(seed, (110, 160, 206), 0.06, 0.85)
    t.f0[:] = 40
    for y in range(0, N, 16):
        F.area(t, 0, y, N, y + 2, mul=0.8)
    t.grime(0.2)
    return t


def wine(seed):
    """A wine rack's front: a dark lattice of cubbies, bottle ends in most, dust on the glass."""
    t = F.wood(seed, "dark")
    rng = t.rng
    for gy in range(0, N, 16):
        for gx in range(0, N, 16):
            F.area(t, gx + 2, gy + 2, gx + 14, gy + 14, (24, 18, 14), h=0.6)
            if rng.random() < 0.8:
                col = [(30, 50, 34), (40, 22, 26), (24, 26, 22)][rng.integers(3)]
                F.disc(t, gx + 8, gy + 8, 4.6, col, h=0.9, smooth=0.8, f0=40)
                F.disc(t, gx + 7, gy + 7, 1.2, mul=1.8)
                if rng.random() < 0.3:
                    F.disc(t, gx + 8, gy + 8, 4.6, mul=(1.6, 1.55, 1.45))  # dust on the glass
    return F.wear(t, 0.35, 0)


def textures():
    return {
        "cellar_back": lambda: back(5000),
        "cellar_preserves": lambda: preserves(5001), "cellar_cans": lambda: cans(5002),
        "cellar_medical": lambda: medical(5003), "cellar_ammo": lambda: ammo(5004),
        "cellar_cinder": lambda: cinder(5005), "cellar_cinder_damp": lambda: cinder(5006, True),
        "cellar_joist": lambda: joist(5007), "cellar_subfloor": lambda: subfloor(5008),
        "cellar_furnace": lambda: furnace(5009), "cellar_safe": lambda: safe(5010, False),
        "cellar_safe_top": lambda: safe(5011, True), "cellar_jug": lambda: jug(5012), "cellar_wine": lambda: wine(5013),
    }


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, light=0, loot=None):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        if loot:
            spec["loot"] = loot
        out[name] = spec

    wood, steel = "home_wood_dark", "home_steel"
    for kind, frame, title, loot in (("preserves", wood, "Preserves Shelf", "basement_food"),
                                     ("cans", steel, "Supply Shelf", "basement_food"),
                                     ("medical", steel, "First Aid Shelf", "basement_medicine"),
                                     ("ammo", steel, "Ammo Shelf", "basement_guns")):
        add(f"cellar_shelf_{kind}", F.shelving(frame, f"cellar_{kind}"), title, "WOOD" if frame == wood else "METAL",
            loot=loot)
    for name, tex in (("cellar_wall", "cellar_cinder"), ("cellar_wall_damp", "cellar_cinder_damp")):
        out[name] = {"tex": tex, "title": "Block Wall", "sound": "STONE"}
    # joists overhead, running along z, the subfloor's boards across them
    add("cellar_joists", [[0, 15, 0, 16, 16, 16, "cellar_subfloor"], [1, 10, 0, 3, 15, 16, "cellar_joist"],
                          [9, 10, 0, 11, 15, 16, "cellar_joist"]], "Floor Joists", "WOOD", rotate="axis", decor=True)
    for lit in (False, True):
        add("cellar_bulb_lit" if lit else "cellar_bulb",
            [[1, 10, 0, 3, 15, 16, "cellar_joist"], [9, 10, 0, 11, 15, 16, "cellar_joist"],
             [0, 15, 0, 16, 16, 16, "cellar_subfloor"], [6, 13, 6, 10, 15, 10, "ext_black_metal"],
             [6.5, 9.5, 6.5, 9.5, 13, 9.5, "lamp_glass" if lit else "ext_glass_dark"],
             [5.5, 6, 7.9, 5.7, 13, 8.1, "home_steel"]], "Light Bulb", "GLASS", rotate="axis", decor=True,
            light=10 if lit else 0)
    add("cellar_furnace", [[2, 0, 2, 14, 16, 14, {"north": "cellar_furnace", "side": "home_heater", "up": "home_heater",
                                                   "down": "home_heater"}]], "Furnace", "METAL")
    add("cellar_furnace_top", [[3, 0, 3, 13, 9, 13, "ext_galv"], [5, 9, 5, 11, 16, 11, "ext_galv"],
                               [5, 12, 11, 11, 16, 16, "ext_galv"]], "Furnace Plenum", "METAL")
    # a supply duct hung tight under the subfloor, between the joists; the riser off the plenum
    add("cellar_duct", [[0, 15, 0, 16, 16, 16, "cellar_subfloor"], [5, 9, 0, 11, 15, 16, "ext_galv"]], "Duct", "METAL",
        rotate="axis", decor=True)
    add("cellar_duct_up", [[0, 15, 0, 16, 16, 16, "cellar_subfloor"], [5, 0, 5, 11, 15, 11, "ext_galv"]], "Duct", "METAL",
        rotate=None)
    for top in (False, True):
        add("cellar_safe_top" if top else "cellar_safe",
            [[1, 0, 2, 15, 16 if top else 16, 16, {"north": "cellar_safe_top" if top else "cellar_safe",
                                                    "side": "cellar_safe", "up": "cellar_safe", "down": "cellar_safe"}]],
            "Gun Safe", "METAL", loot="basement_guns")
    jugs = []
    for x, z in ((1.5, 2), (8.5, 2), (1.5, 9), (8.5, 9)):
        jugs += [[x, 0, z, x + 6, 9, z + 6, "cellar_jug"], [x + 2, 9, z + 2, x + 4, 11, z + 4, "ext_galv_lid"]]
    add("cellar_jugs", jugs, "Water Jugs", "WOOL")
    add("cellar_wine_rack", [[0, 0, 4, 16, 16, 16, {"north": "cellar_wine", "side": wood, "up": wood, "down": wood}]],
        "Wine Rack", "WOOD", loot="liquor")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("cellar_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    lines = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in kept.items())
    rest = {k: v for k, v in data.items() if k != "blocks"}
    head = "".join(f'  {json.dumps(k)}: {json.dumps(v)},\n' for k, v in rest.items())
    path.write_text("{\n" + head + '  "blocks": {\n' + lines + "\n  }\n}\n")
    print(f"{len(made)} basement blocks in {path}")


if __name__ == "__main__":
    main()
