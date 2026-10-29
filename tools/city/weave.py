"""The wv_ kit: the mill's floors, in place of washing machines and stone slabs for looms and white
wool for bales (Josiah, 2026-10-29: "Everything in this world must be of the same level of detail
and effort"). world/county/Landmarks.mill lays them out in rows.

Every piece is rotate=facing, the weaver's side (the front) on the north:

- wv_loom_l / wv_loom_r: a power loom, two blocks wide (l the west half as drawn): the cast-iron
  side frames, the warp beam at the back with the threads running forward through the heddles to
  the reed, the cloth wound on the beam at the front, the drive pulley and its gear on the end;
  wv_loom_l_cut: the same with the warp cut and hanging;
- wv_frame: a spinning frame's end: the bobbin rail with its rows of bobbins, the creel over it;
- wv_bale / wv_bale_burst: a cotton bale in its jute wrap and steel bands, and one split open with
  the cotton bursting out of the cut.

    python3 tools/city/weave.py      rewrites the wv_ entries in blocks.json

photo_textures.py draws the textures (weave.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from gasstation import weather  # noqa: E402
from bunker import chips  # noqa: E402

N = P.N
HERE = Path(__file__).parent
IRON = "wv_iron"


def iron(seed):
    """Machinery green over cast iron, worn to black metal on every edge a hand touched, oil and lint."""
    t = F.plain(seed, (70, 88, 76), 0.06, 0.35)
    t.f0[:] = 24
    weather(t, rust=0.35, streak=0.3, grime=0.35)
    chips(t, 0.2, under=(52, 52, 50))
    return t


def warp(seed):
    """The warp: hundreds of cotton threads side by side, a little slack here and there, greyed with dust."""
    t = F.plain(seed, (214, 208, 192), 0.03, 0.2)
    for x in range(0, N, 2):
        t.c[:, x] *= 0.8
        t.h[:, x] = 0.4
    t.grime(0.25, (150, 144, 130))
    return t


def cloth(seed):
    """Woven grey cloth: the weave's fine check, the selvedge, dust in the folds."""
    t = F.plain(seed, (196, 190, 176), 0.04, 0.15)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (1 - 0.08 * ((xx + yy) % 2))[..., None]
    t.grime(0.3, (130, 124, 110))
    return t


def jute(seed):
    """The bale's jute wrap: coarse brown hessian, the weave open, stains from the warehouse floor."""
    t = F.plain(seed, (150, 120, 84), 0.08, 0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (1 - 0.15 * (((xx // 2) + (yy // 2)) % 2))[..., None]
    t.grime(0.35, (80, 66, 48))
    return t


def cotton(seed):
    """Raw cotton: white tufts, soft shadows between them, grey where the damp got in."""
    t = F.plain(seed, (232, 228, 220), 0.05, 0.05)
    n = P.fbm(t.rng, ((8, 1.0), (16, 0.7), (32, 0.4)))
    t.c *= (0.82 + 0.18 * np.clip(n * 0.5 + 0.6, 0, 1))[..., None]
    t.h[:] = 0.5 + 0.4 * n
    t.grime(0.2, (150, 150, 140))
    return t


def textures():
    return {"wv_iron": lambda: iron(49470), "wv_warp": lambda: warp(49471), "wv_cloth": lambda: cloth(49472),
            "wv_jute": lambda: jute(49473), "wv_cotton": lambda: cotton(49474)}


def register(ns):
    ns.EXTRA.update(textures())


def loom(half, cut=False):
    """Half the loom (two blocks along x, the weaver on the north). l is x 0 .. 16 of 32, r 16 .. 32."""
    b = []
    # the side frames at the loom's two ends, cross rails between them
    for x0 in (1, 29):
        b += [[x0, 0, 2, x0 + 2, 13, 4, IRON], [x0, 0, 12, x0 + 2, 13, 14, IRON], [x0, 0, 2, x0 + 2, 2, 14, IRON],
              [x0, 11, 2, x0 + 2, 13, 14, IRON], [x0, 4, 4, x0 + 2, 6, 12, IRON]]
    b += [[1, 1, 7.4, 31, 2.2, 8.6, IRON], [1, 11, 13, 31, 12.2, 14, IRON]]
    # the warp beam at the back, the threads forward and up to the heddles, the reed, the cloth beam
    b += [[3, 7, 11, 29, 11, 15, {"side": "wv_warp", "up": "wv_warp", "down": "wv_warp", "east": IRON, "west": IRON}]]
    if cut:
        b += [[3, 2, 10.6, 29, 7, 11, "wv_warp"]]
    else:
        b += [[3, 11, 6, 29, 11.4, 11, "wv_warp"], [3, 9, 6.5, 29, 15, 7.5, {"north": IRON, "south": IRON, "side": IRON, "up": IRON}],
              [3, 11, 4.5, 29, 11.4, 6.5, "wv_cloth"]]
    b += [[3, 8, 2.5, 29, 11, 5, {"side": "wv_cloth", "up": "wv_cloth", "down": "wv_cloth", "east": IRON, "west": IRON}],
          [3, 11.4, 5, 29, 12.6, 6.6, IRON]]
    # the drive: the pulley and the gear on the east end, the belt gone up to a shaft long since taken down
    b += [[31, 4, 4, 32.5, 12, 12, IRON], [31.5, 6, 3, 32.2, 10, 13, IRON], [31.5, 3, 6, 32.2, 13, 10, IRON]]
    lo, hi = (0, 16) if half == "l" else (16, 32)
    out = []
    for q in b:
        x0, x1 = max(q[0], lo), min(q[3], hi)
        if x1 <= x0 or (x1 - x0 < 0.05):
            continue
        out.append([x0 - lo, q[1], q[2], x1 - lo, q[4], q[5]] + q[6:])
    return out


def spin_frame():
    """A spinning frame seen down its length (along x): the rail, two rows of bobbins on their
    spindles, the creel of roving bobbins over them, the drafting rollers between."""
    b = [[0, 0, 3, 16, 3, 13, IRON], [0, 3, 6, 16, 6, 10, IRON], [0, 9, 4, 16, 10, 12, IRON], [0, 13, 7, 16, 14, 9, IRON]]
    for x in (2, 6, 10, 14):
        for z, s in ((2.5, 1), (11.5, -1)):
            b.append([x - 1, 6, z, x + 1, 9, z + 2, "wv_cotton"])
            b.append([x - 0.3, 3, z + 0.7, x + 0.3, 10, z + 1.3, IRON])
            b.append([x - 1.2, 14, z + 1.5 * s + (0 if s > 0 else 0), x + 1.2, 16.5, z + 1.5 * s + 2, "wv_cotton"])
    b.append([0, 10, 5.5, 16, 11.2, 10.5, IRON])
    return b


def bale(burst):
    jute = {"side": "wv_jute", "up": "wv_jute", "down": "wv_jute"}
    b = [[1, 0, 2, 15, 12, 14, jute]]
    for x in (3, 8, 13):
        b.append([x - 0.5, -0.1, 1.8, x + 0.5, 12.2, 14.2, "st_galv"])
    if burst:
        b += [[4, 12, 5, 12, 14.5, 11, "wv_cotton"], [2, 10, 1, 9, 13.5, 2.2, "wv_cotton"], [6, 13.5, 6, 10, 15.5, 9, "wv_cotton"]]
    return b


def blocks():
    out = {}

    def add(name, boxes, title, shape, sound="METAL", cutout=False):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "rotate": "facing", "shape": shape}
        if cutout:
            spec["render"] = "cutout"
        out[name] = spec

    for half in "lr":
        add(f"wv_loom_{half}", loom(half), "Power Loom", [[0, 0, 2, 16, 13, 15]])
        add(f"wv_loom_{half}_cut", loom(half, True), "Power Loom", [[0, 0, 2, 16, 13, 15]])
    add("wv_frame", spin_frame(), "Spinning Frame", [[0, 0, 2, 16, 16, 14]])
    add("wv_bale", bale(False), "Cotton Bale", [[1, 0, 2, 15, 12, 14]], "WOOL")
    add("wv_bale_burst", bale(True), "Cotton Bale", [[1, 0, 2, 15, 12, 14]], "WOOL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("wv_")}
    made = blocks()
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} mill blocks in {path}")


if __name__ == "__main__":
    main()
