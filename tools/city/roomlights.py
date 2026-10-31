"""The lx_ kit: a light switch for every room, and the cube lamps the modern buildings are lit with
(Josiah 2026-10-31: the blue cube lights by the downtown police doors, with some red). Rooms start
dark; the switch by the door (the interact key) turns every lamp in its room on or off
(world/blocks/LightSwitch). world/county/RoomLights puts them in.

- lx_switch / lx_switch_on: the switch plate on the wall, the toggle down (off) or up (on), the wall
  to the south as drawn (rotate=facing: the plate faces the way the block faces);
- lx_cube_blue / lx_cube_blue_lit, lx_cube_red / lx_cube_red_lit: a frosted glass cube under a
  brushed steel ceiling plate, hung from the top of the block, dark or glowing cold blue / red.

    python3 tools/city/roomlights.py      rewrites the lx_ entries in blocks.json and the lamp pairs

The lamp pairs (data/zomboidcraft/lamps.txt: "off on", one room fitting a line) are what a switch
turns and what world gen darkens; they come from blocks.json by name, see LAMP below.
"""
import json
import re
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area  # noqa: E402

N = P.N
HERE = Path(__file__).parent
LAMPS = HERE.parent.parent / "src/main/resources/data/zomboidcraft/lamps.txt"
# the room fittings, by their dark names: not signs, screens, windows, street or yard lights
LAMP = re.compile(r"^(ceiling_light_off|cellar_bulb|bunk_lamp|bunk_lamp_hang|bunk_lamp_red|bunk_tube|"
                  r"fit_(tube|red|lantern|globe|globe_down|highbay|surgical|overhead|troffer|downlight|sconce)|"
                  r"fs_lamp_red|tr_sconce|tr_chandelier|cv_table_lamp|cv_lamp_globe|ch_pendant|ch_chandelier|"
                  r"dn_(hang_\d|globe|orb|soffit_tube)|hs_slab_lamp|hs_or_light|un_slab_\w+_lamp|un_table_lamp|"
                  r"om_lights|rs_lamp|wall_lantern_off|tn_tie_lamp|lx_cube_\w+|as_highbay|as_strip|as_pit_lamp)$")


# ---------------------------------------------------------------- textures

def glass(seed, rgb, lit):
    """Frosted glass: milky, brighter to the middle; lit, it glows its colour out to the edges."""
    t = F.plain(seed, rgb, 0.03, 0.8)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - N / 2, yy - N / 2) / (N / 2)
    t.c *= (1.08 - 0.18 * d)[..., None]
    area(t, 0, 0, N, 2, mul=0.85)
    area(t, 0, N - 2, N, N, mul=0.85)
    if lit:
        t.c = np.minimum(t.c + 30 * (1 - d)[..., None], 255)
        t.emit[:] = np.clip(1.0 - 0.25 * d, 0.6, 1)
    return t


def steel(seed):
    t = F.plain(seed, (168, 170, 172), 0.04, 0.6)
    for y in range(0, N, 2):
        t.c[y] *= 0.97
    return t


def plate(seed):
    """The switch plate: off-white plastic, the two screws, finger grime round the toggle slot."""
    t = F.plain(seed, (226, 222, 210), 0.03, 0.5)
    for cy in (10, N - 10):
        F.disc(t, N / 2, cy, 2, col=(150, 150, 146))
    area(t, N / 2 - 4, N / 2 - 9, N / 2 + 4, N / 2 + 9, col=(60, 58, 54), dh=-0.4)
    F.disc(t, N / 2 + 6, N / 2 + 4, 9, mul=0.92)
    return t


def textures():
    return {"lx_blue": lambda: glass(49900, (150, 176, 196), False),
            "lx_blue_lit": lambda: glass(49901, (70, 150, 255), True),
            "lx_red": lambda: glass(49902, (190, 160, 160), False),
            "lx_red_lit": lambda: glass(49903, (255, 60, 50), True),
            "lx_steel": lambda: steel(49904),
            "lx_plate": lambda: plate(49905)}


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- models

def switch(on):
    """The plate on the wall to the south as drawn, the toggle out of its slot, up or down."""
    tog = "lx_steel"
    face = {"north": "lx_plate", "south": "lx_plate", "east": "lx_plate", "west": "lx_plate", "up": "lx_plate", "down": "lx_plate"}
    b = [[6, 5, 15.4, 10, 11, 16, face]]
    b.append([7.4, 7.6, 14.2, 8.6, 8.4, 15.4, tog, None, {"axis": "x", "angle": 22.5 if on else -22.5, "origin": [8, 8, 15.4]}])
    return b


def cube(colour, lit):
    glow = f"lx_{colour}{'_lit' if lit else ''}"
    return [[4, 15, 4, 12, 16, 12, "lx_steel"], [7.5, 13, 7.5, 8.5, 15, 8.5, "lx_steel"], [5, 7, 5, 11, 13, 11, glow]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate=None, light=0, sound="GLASS", **extra):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape, "render": "cutout", **extra}
        if rotate:
            spec["rotate"] = rotate
        if light:
            spec["light"] = light
        out[name] = spec

    add("lx_switch", switch(False), "Light Switch", [[6, 5, 14, 10, 11, 16]], "facing", sound="STONE", switch=True, decor=True)
    add("lx_switch_on", switch(True), "Light Switch", [[6, 5, 14, 10, 11, 16]], "facing", sound="STONE", switch=True, decor=True)
    for colour, light in (("blue", 13), ("red", 10)):
        add(f"lx_cube_{colour}", cube(colour, False), "Cube Light", [[5, 7, 5, 11, 16, 11]])
        add(f"lx_cube_{colour}_lit", cube(colour, True), "Cube Light", [[5, 7, 5, 11, 16, 11]], light=light)
    return out


def lamp_pairs(data):
    """Each room fitting's dark name and its lit one, as the game reads them."""
    b = data["blocks"]
    pairs = []
    for k in b:
        if not LAMP.match(k):
            continue
        for on in ((k[:-4],) if k.endswith("_off") else (k + "_lit", k + "_on")):
            if on in b and b[on].get("light", 0) > 0:
                pairs.append((k, on))
                break
    return pairs


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    # in place: blocks.json is append-only, its order is the registry's
    data["blocks"] = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("lx_") or k in made}
    data["blocks"].update({k: v for k, v in made.items() if k not in data["blocks"]})
    path.write_text(json.dumps(data, indent=1) + "\n")
    pairs = lamp_pairs(data)
    LAMPS.write_text("# room lamps, dark then lit: written by tools/city/roomlights.py\n" + "".join(f"{a} {c}\n" for a, c in pairs))
    print(f"{len(made)} room light blocks in {path}, {len(pairs)} lamp pairs in {LAMPS}")


if __name__ == "__main__":
    main()
