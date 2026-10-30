"""The red growth: the sg_ kit for what the breach spreads (ground zero round Ridgeview Labs, the lab's
lower levels and, later, the Nether's Spore Forest).

Drawn from what fungus, mould and slime mould actually do: a crust that starts as speckled patches,
joins into a mat and thickens into blistered folds; veins that run up walls along the damp and the
cracks, branching as they go; strings that hang from ceilings; stalks that push up where it is
thickest, capped and gilled; pods that swell and glow faintly before they burst. Deep red to rust,
paler at the growing edge, wet-looking. Ash from the burn lies under and between it.

Blocks (the base model faces north; anything on a wall has its back at z = 16 and faces the room):
- the crust on the ground, three stages: sg_crust_0 (speckled patches), sg_crust_1 (a joined mat),
  sg_crust_2 (thick and blistered, raised);
- on walls, three stages: sg_vein_0..2 (a plate on the wall);
- sg_hang (strings hanging from a ceiling), sg_blister (a low swelling), sg_mass (a whole block grown
  through), sg_stalk, sg_stalk_top (a fungal stalk two high, capped), sg_pods (a glowing cluster);
- the burn: sg_ash (a dusting), sg_ash_drift (a low drift).

    python3 tools/city/spore.py          rewrites the sg_ entries in blocks.json

photo_textures.py draws the textures (spore.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
from furniture import disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
DEEP = (92, 14, 16)
RED = (150, 30, 24)
EDGE = (196, 92, 62)
POD = (255, 110, 40)
ASH = (86, 84, 82)


def growth(t, amount, thick=0.0, glow=0.0):
    """The growth over whatever is there: a mask from noise, deep red in the middle, paler at the
    growing edge, pores over it, wet sheen; returns the mask."""
    n = P.fbm(t.rng, ((4, 1.0), (8, 0.7), (16, 0.5), (32, 0.3)))
    m = np.clip((n + amount - 0.5) * 4, 0, 1)
    edge = np.clip(1 - np.abs(m - 0.5) * 2, 0, 1)
    t.mix(m, RED, 1.0)
    t.mix(np.clip(m * (P.fbm(t.rng) + 0.6), 0, 1), DEEP, 0.6)
    t.mix(edge, EDGE, 0.7)
    t.alpha[:] = np.maximum(t.alpha, np.where(m > 0.15, 255, 0))
    t.h[:] = np.where(m > 0.15, 0.7 + m * 0.2 + thick * np.clip(n, 0, 1) * 0.6, t.h)
    t.smooth[:] = np.where(m > 0.15, 0.75, t.smooth)
    pores = (t.rng.random((N, N)) < 0.06) & (m > 0.5)
    t.c[pores] *= 0.5
    t.h[pores] -= 0.1
    if thick:
        for _ in range(int(10 * thick)):
            x, y = t.rng.uniform(0, N, 2)
            if m[int(y), int(x)] > 0.6:
                r = t.rng.uniform(2, 5)
                disc(t, x, y, r, mul=1.18, dh=0.25, smooth=0.9, soft=1.5)
                disc(t, x - r * 0.3, y - r * 0.3, r * 0.3, mul=1.25)
    if glow:
        for _ in range(int(14 * glow)):
            x, y = t.rng.uniform(0, N, 2)
            if m[int(y), int(x)] > 0.5:
                disc(t, x, y, t.rng.uniform(0.8, 1.6), col=POD)
                t.emit[int(y) % N, int(x) % N] = 0.8
    return m


def crust(seed, stage):
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    growth(t, (0.25, 0.55, 0.85)[stage], thick=stage * 0.5, glow=stage * 0.4)
    return t


def vein(seed, stage):
    """Veins up a wall: branching lines from the bottom, thicker low down, a mat at the foot."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    rng = t.rng
    tips = [(rng.uniform(4, 60), N - 1, -np.pi / 2 + rng.normal(0, 0.3), 2.5 + stage) for _ in range(2 + stage * 2)]
    while tips:
        x, y, a, w = tips.pop()
        for _ in range(int(rng.uniform(20, 50))):
            disc(t, x, y, w / 2, col=RED, alpha=255, h=0.85, smooth=0.75)
            disc(t, x - w * 0.2, y, w * 0.2, mul=1.2)
            a += rng.normal(0, 0.25)
            x += np.cos(a)
            y += np.sin(a)
            w = max(0.8, w * 0.985)
            if rng.random() < 0.05 and w > 1.2:
                tips.append((x, y, a + rng.choice((-0.7, 0.7)), w * 0.7))
            if y < 0:
                break
    if stage:
        foot = P.Tex(seed + 1, (0, 0, 0))
        foot.alpha[:] = 0
        growth(foot, 0.3 + stage * 0.15, thick=stage * 0.4, glow=stage * 0.3)
        yy = np.arange(N)[:, None] / N
        keep = (foot.alpha > 0) & (yy > 1 - stage * 0.3) * np.ones((1, N), bool)
        for a in ("c", "h", "smooth", "emit"):
            getattr(t, a)[keep] = getattr(foot, a)[keep]
        t.alpha[keep] = 255
    t.mix(np.clip(P.fbm(t.rng) + 0.3, 0, 1) * (t.alpha > 0), DEEP, 0.4)
    return t


def hang(seed):
    """Strings hanging from the ceiling, beaded with drops."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    for _ in range(9):
        x = t.rng.uniform(3, 61)
        length = t.rng.uniform(16, 60)
        w = t.rng.uniform(1.0, 2.2)
        for y in np.arange(0, length, 0.5):
            disc(t, x + np.sin(y * 0.15) * 0.8, y, w * (1 - y / length * 0.6) / 2, col=RED, alpha=255, smooth=0.85)
        disc(t, x, length, w * 0.9, col=EDGE, alpha=255, smooth=0.95)
    t.mix(np.clip(P.fbm(t.rng) + 0.4, 0, 1) * (t.alpha > 0), DEEP, 0.4)
    return t


def flesh(seed, gills=False):
    """The stalks' skin, or the gills under a cap."""
    t = P.Tex(seed, RED)
    if gills:
        for x in range(0, N, 3):
            t.c[:, x] = DEEP
            t.h[:, x] = 0.5
    else:
        n = t.vary(0.25, tint=(20, -10, -10))
        for x in range(0, N, 9):
            t.c[:, x:x + 2] *= 0.75
        t.h[:] = 0.7 + n * 0.2
    t.mix(np.clip(P.fbm(t.rng) * 1.4, 0, 1), DEEP, 0.5)
    t.smooth[:] = 0.8
    return t


def cap(seed):
    t = flesh(seed)
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.2, 0, 1), EDGE, 0.5)
    for _ in range(18):
        x, y = t.rng.uniform(0, N, 2)
        disc(t, x, y, t.rng.uniform(1, 2.5), col=(220, 150, 110), dh=0.15)
    return t


def pod(seed):
    """A pod's skin: stretched thin, the light inside showing through between darker veins."""
    t = P.Tex(seed, POD)
    n = t.vary(0.2)
    t.mix(np.clip(-n * 2, 0, 1), RED, 0.7)
    t.emit[:] = np.clip(0.4 + n * 0.8, 0.1, 1)
    t.smooth[:] = 0.95
    return t


def ash(seed, patchy=True):
    """Fine grey ash, darker cinders in it, a few flecks of white."""
    t = P.Tex(seed, ASH)
    n = t.vary(0.15)
    t.speckle(0.08, (50, 48, 46), 0.6, -0.05)
    t.speckle(0.015, (170, 168, 162), 0.4)
    t.smooth[:] = 0.05
    t.poro[:] = 60
    if patchy:
        t.alpha[:] = np.where(n + P.fbm(t.rng) * 0.6 > -0.45, 255, 0)
    return t


def textures():
    T = {}
    for s in range(3):
        T[f"sg_crust_{s}"] = lambda s=s: crust(47500, s)     # one seed: each stage grows out of the last and they tile
        T[f"sg_vein_{s}"] = lambda s=s: vein(47510 + s, s)
    T["sg_hang"] = lambda: hang(47520)
    T["sg_flesh"] = lambda: flesh(47521)
    T["sg_gills"] = lambda: flesh(47522, gills=True)
    T["sg_cap"] = lambda: cap(47523)
    T["sg_pod"] = lambda: pod(47524)
    T["sg_mass"] = lambda: crust_full(47525)
    T["sg_ash"] = lambda: ash(47526)
    T["sg_ash_full"] = lambda: ash(47527, patchy=False)
    return T


def crust_full(seed):
    t = P.Tex(seed, DEEP)
    growth(t, 1.2, thick=1.0, glow=0.5)
    t.alpha[:] = 255
    return t


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, **extra):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        spec.update(extra)
        out[name] = spec

    def turned(b, axis, angle, origin=(8, 8, 8)):
        return b[:7] + [None, {"axis": axis, "angle": angle, "origin": list(origin)}]

    def flat(tex, y1=0.25):
        return {"up": tex, "down": tex, "side": tex}

    for s in range(3):
        n = f"sg_crust_{s}"
        bxs = [[0, 0, 0, 16, 0.3, 16, flat(n)]]
        add(n, bxs, "Red Growth", "PACKED_MUD" if s == 2 else "WOOL", rotate="facing", decor=True, render="cutout",
            shape=[], **({"light": 3} if s == 2 else {}))
        v = f"sg_vein_{s}"
        add(v, [[0, 0, 15.6, 16, 16, 16, {"north": v, "south": v, "side": v, "up": v, "down": v}]], "Red Growth", "WOOL",
            decor=True, render="cutout", shape=[])
    add("sg_hang", [[0, 0, 8, 16, 16, 8, {"north": "sg_hang", "south": "sg_hang"}],
                    [8, 0, 0, 8, 16, 16, {"east": "sg_hang", "west": "sg_hang"}],
                    [0, 15.7, 0, 16, 16, 16, flat("sg_crust_1")]], "Hanging Growth", "WOOL", rotate=None, decor=True,
        render="cutout", shape=[])
    add("sg_blister", [[1, 0, 1, 15, 3, 15, {"up": "sg_mass", "side": "sg_flesh", "down": "sg_flesh"}],
                       [3, 3, 3, 12, 6, 11, {"up": "sg_mass", "side": "sg_flesh", "down": "sg_flesh"}],
                       [5, 6, 5, 9, 8, 9, "sg_pod"]], "Blister", "PACKED_MUD", light=2, shape=[[1, 0, 1, 15, 6, 15]])
    add("sg_mass", [[0, 0, 0, 16, 16, 16, "sg_mass"]], "Grown-Through Block", "PACKED_MUD", rotate=None, light=2)
    add("sg_stalk", [[5, 0, 5, 11, 16, 11, "sg_flesh"], [3, 0, 3, 13, 3, 13, {"up": "sg_mass", "side": "sg_flesh"}],
                     turned([9, 4, 6, 13, 12, 10, "sg_flesh"], "z", -22.5, (11, 4, 8))],
        "Fungal Stalk", "PACKED_MUD", shape=[[5, 0, 5, 11, 16, 11]])
    add("sg_stalk_top", [[5.5, 0, 5.5, 10.5, 8, 10.5, "sg_flesh"],
                         [0, 8, 0, 16, 10, 16, {"up": "sg_cap", "side": "sg_cap", "down": "sg_gills"}],
                         [2, 10, 2, 14, 12, 14, {"up": "sg_cap", "side": "sg_cap", "down": "sg_gills"}],
                         [5, 12, 5, 11, 13, 11, "sg_cap"]], "Fungal Stalk", "PACKED_MUD",
        shape=[[0, 0, 0, 16, 13, 16]], light=1)
    add("sg_pods", [[2, 0, 3, 8, 7, 9, "sg_pod"], [8, 0, 6, 14, 9, 12, "sg_pod"], [5, 0, 10, 9, 5, 14, "sg_pod"],
                    [0, 0, 0, 16, 0.3, 16, flat("sg_crust_1")]], "Spore Pods", "PACKED_MUD", light=6,
        shape=[[2, 0, 3, 14, 9, 14]])
    add("sg_ash", [[0, 0, 0, 16, 0.3, 16, flat("sg_ash")]], "Ash", "SAND", rotate="facing", decor=True, render="cutout",
        shape=[])
    add("sg_ash_drift", [[0, 0, 0, 16, 2, 16, flat("sg_ash_full")], [2, 2, 1, 14, 3.5, 12, flat("sg_ash_full")],
                         [5, 3.5, 3, 11, 4.5, 8, flat("sg_ash_full")]], "Ash Drift", "SAND",
        shape=[[0, 0, 0, 16, 2, 16]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("sg_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} growth blocks in {path}")


if __name__ == "__main__":
    main()
