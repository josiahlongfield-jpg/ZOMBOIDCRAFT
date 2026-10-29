"""The Harvestman's den: the hd_ kit it builds its nest from in the dark of a big building
(creature/HarvestmanDens.java carves the room and lays these where it settles).

It clears a room of what was in it and heaps the scrap it tore out round the walls: car panels,
ducting, corrugated sheet, rebar and cable, rust over old paint. In the middle a low matted nest of
what it dragged in (clothes, cable, insulation, bones) and the floor scattered with the leavings.
The walls round it are scored deep where it pulled itself up them. The bodies it keeps are hung
from the ceiling on steel cable, or wrapped in plastic sheeting and cable and hung like sides of
meat; the rest sit against the walls facing the way in.

Blocks (the base model faces north; anything on a wall has its back at z = 16 and faces the room):
- the scrap: hd_heap, hd_heap_b (a heap, two ways of falling), hd_heap_tall, hd_heap_low;
- the nest: hd_nest, hd_nest_b, hd_debris, hd_debris_b, hd_bones, hd_drag (a body dragged over the
  floor, along z);
- the walls: hd_gouges_0..2 (claw scores, a plate on the wall);
- the hanging: hd_cable, hd_cable_top (bolted to the ceiling), hd_cable_end (the knot a body hangs
  under), hd_cocoon, hd_cocoon_top (a body wrapped and hung, two blocks).

    python3 tools/city/harvestman_den.py          rewrites the hd_ entries in blocks.json

photo_textures.py draws the textures (harvestman_den.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402

N = P.N
HERE = Path(__file__).parent
RUST = (122, 64, 36)
RUST_DARK = (62, 34, 22)
RUST_LIGHT = (176, 94, 42)
PAINTS = [(78, 92, 64), (196, 186, 160), (124, 40, 34), (52, 70, 96), (170, 140, 60), (88, 88, 90)]
CABLE = (34, 34, 36)
BLOOD_OLD = (70, 18, 14)


# ---------------------------------------------------------------- painting helpers

def strand(t, pts, width, col, alpha=None, dh=0.15, smooth=None, f0=None, shade=True):
    """A cable, a bar or a strip of cloth along a polyline: drawn as discs, darker along its lower edge."""
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(2, int(np.hypot(x1 - x0, y1 - y0) * 2))
        for k in range(n + 1):
            x, y = x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n
            m = disc(t, x, y, width / 2, col=col, alpha=alpha, smooth=smooth, f0=f0)
            t.h[m] = np.maximum(t.h[m], 0.75 + dh)
            if shade:
                disc(t, x + width * 0.25, y + width * 0.3, width * 0.3, mul=0.7)


def wiggle(rng, x0, y0, x1, y1, n=6, amp=4.0):
    xs, ys = np.linspace(x0, x1, n), np.linspace(y0, y1, n)
    jx, jy = rng.normal(0, amp, n), rng.normal(0, amp, n)
    jx[0] = jx[-1] = jy[0] = jy[-1] = 0
    return list(zip(xs + jx, ys + jy))


def rust_skin(t, amount=1.0):
    rng = t.rng
    t.mix(np.clip(P.fbm(rng) * 1.6 + 0.2, 0, 1) * amount, RUST_DARK, 0.7)
    t.mix(np.clip(P.fbm(rng) * 1.8 - 0.3, 0, 1) * amount, RUST_LIGHT, 0.6)
    t.speckle(0.06 * amount, (40, 22, 14), 0.6, -0.05)
    t.speckle(0.03 * amount, (200, 120, 60), 0.4)


def chips(t, n, cols, size=(2, 7)):
    """Flakes of the paint it had, lifting off the rust."""
    rng = t.rng
    for _ in range(n):
        x, y = rng.uniform(0, N, 2)
        w, h = rng.uniform(*size, 2)
        col = cols[int(rng.integers(len(cols)))]
        area(t, x, y, x + w, y + h, col=col, dh=0.05, smooth=0.35)
        area(t, x, y + h, x + w, y + h + 1, mul=0.6)


def bright_scratches(t, n):
    rng = t.rng
    for _ in range(n):
        x, y = rng.uniform(0, N, 2)
        ang = rng.uniform(0, np.pi)
        for i in range(int(rng.integers(4, 14))):
            xi, yi = int(x + np.cos(ang) * i) % N, int(y + np.sin(ang) * i) % N
            t.c[yi, xi] = (150, 146, 140)
            t.f0[yi, xi] = 230
            t.smooth[yi, xi] = 0.6


# ---------------------------------------------------------------- the textures

def scrap(seed):
    """Torn steel: rust over the paint it had (a car's, a duct's, a locker's), bright where it was ripped."""
    t = F.plain(seed, RUST, 0.12, 0.25)
    t.f0[:] = 40
    rust_skin(t)
    chips(t, 12, [tuple(int(c * 0.55 + r * 0.45) for c, r in zip(p, RUST)) for p in PAINTS], (1, 4))
    # seams and rivets where panels overlap
    for y in t.rng.choice(N, 3, replace=False):
        area(t, 0, y, N, y + 1, mul=0.55, dh=-0.2)
        for x in range(2, N, 6):
            disc(t, x, y + 2.5, 0.8, col=(90, 50, 30), dh=0.15)
    bright_scratches(t, 10)
    t.crack(4, 0.2, (30, 18, 12))
    return t


def sheet(seed, paint):
    """Corrugated sheet ripped off a roof or a shed: the ridges up and down, paint half gone."""
    t = F.plain(seed, paint, 0.06, 0.3)
    x = np.arange(N)
    ridge = np.sin(x / 8 * 2 * np.pi)
    t.h = np.clip(0.6 + 0.25 * ridge[None, :] + t.h * 0 , 0, 1).astype(np.float32)
    t.c *= (0.85 + 0.15 * ridge[None, :, None])
    m = np.clip(P.fbm(t.rng) * 1.7 + np.linspace(-0.2, 0.6, N)[:, None], 0, 1)
    t.mix(m, RUST, 0.85)
    t.mix(np.clip(P.fbm(t.rng) * 2 - 0.4, 0, 1) * m, RUST_DARK, 0.8)
    t.speckle(0.04, (40, 24, 14), 0.5)
    t.streaks(0.6, 0.7)
    t.f0[:] = 40
    return t


def tangle(seed):
    """Cable, rebar and stripped wire knotted together (see-through between the strands)."""
    t = F.plain(seed, CABLE, 0.05, 0.4)
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(5):
        a, b = rng.uniform(0, N, 2), rng.uniform(0, N, 2)
        strand(t, wiggle(rng, *a, *b, amp=6), rng.uniform(2, 3.5), CABLE, alpha=255, smooth=0.5)
    for _ in range(3):
        a, b = rng.uniform(0, N, 2), rng.uniform(0, N, 2)
        strand(t, wiggle(rng, *a, *b, n=3, amp=2), 2.5, RUST, alpha=255, f0=60)
    for _ in range(4):
        a, b = rng.uniform(0, N, 2), rng.uniform(0, N, 2)
        strand(t, wiggle(rng, *a, *b, amp=8), 1, (150, 92, 56), alpha=255, f0=235, smooth=0.6, shade=False)
    return t


def cloth_scraps(t, n, size=(4, 12)):
    rng = t.rng
    cols = [(60, 62, 70), (110, 96, 80), (140, 136, 128), (48, 60, 84), (120, 50, 44), (84, 90, 60), (180, 170, 150)]
    for _ in range(n):
        x, y = rng.uniform(0, N, 2)
        w = rng.uniform(*size)
        ang = rng.uniform(0, np.pi)
        pts = [(x, y), (x + np.cos(ang) * w, y + np.sin(ang) * w)]
        strand(t, wiggle(rng, *pts[0], *pts[1], n=3, amp=2), rng.uniform(3, 6), cols[int(rng.integers(len(cols)))],
               alpha=255, dh=0.1)


def bone_pieces(t, n, alpha=255):
    rng = t.rng
    for _ in range(n):
        x, y = rng.uniform(4, N - 4, 2)
        ang = rng.uniform(0, np.pi)
        ln = rng.uniform(5, 13)
        x1, y1 = x + np.cos(ang) * ln, y + np.sin(ang) * ln
        col = (206, 196, 170) if rng.random() < 0.6 else (176, 160, 130)
        strand(t, [(x, y), (x1, y1)], 2, col, alpha=alpha, dh=0.2, smooth=0.3)
        for ex, ey in ((x, y), (x1, y1)):
            disc(t, ex, ey, 1.8, col=col, alpha=alpha, dh=0.2)


def nest(seed, bloody=False):
    """The nest's mat: clothes, insulation, cable and bones pressed down into one mass, stained dark."""
    t = F.plain(seed, (70, 62, 54), 0.15, 0.2)
    rng = t.rng
    t.mix(np.clip(P.fbm(rng) * 1.5, 0, 1), (196, 170, 90), 0.35)    # torn insulation
    cloth_scraps(t, 30)
    for _ in range(6):
        a, b = rng.uniform(0, N, 2), rng.uniform(0, N, 2)
        strand(t, wiggle(rng, *a, *b, amp=6), 2, CABLE, alpha=255, smooth=0.45)
    bone_pieces(t, 3)
    t.grime(0.5, (40, 30, 24))
    if bloody:
        for _ in range(3):
            F.blood(t, *rng.uniform(0, N, 2), rng.uniform(6, 14), amount=0.8)
    t.smooth = np.clip(t.smooth, 0, 0.35)
    return t


def debris(seed, bones=False):
    """What's left lying round the nest: shreds of clothes, cable ends, paper, bone, old blood (see-through floor decal)."""
    t = F.plain(seed, (80, 70, 60), 0.1, 0.2)
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(3):
        m = F.blob_mask(rng, *rng.uniform(0, N, 2), rng.uniform(5, 12), rough=0.8)
        heart = np.clip(m * 1.6 - 0.6, 0, 1)
        t.c = t.c * (1 - m[..., None]) + np.array((92, 26, 18), np.float32) * m[..., None]
        t.c = t.c * (1 - heart[..., None]) + np.array(BLOOD_OLD, np.float32) * heart[..., None]
        t.alpha = np.maximum(t.alpha, (m > 0.3) * 235)
        t.smooth = np.maximum(t.smooth, heart * 0.45)
    if bones:
        bone_pieces(t, 7)
    else:
        cloth_scraps(t, 9, (4, 9))
        bone_pieces(t, 2)
        for _ in range(4):
            a = rng.uniform(0, N, 2)
            strand(t, wiggle(rng, *a, *(a + rng.normal(0, 8, 2)), n=4, amp=2), 1.5, CABLE, alpha=255)
    return t


def drag(seed):
    """A body dragged across the floor: smeared streaks of blood running the length of the block."""
    t = F.plain(seed, (90, 24, 18), 0.1, 0.4)
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(14):
        x = rng.uniform(16, 44)
        w = rng.uniform(2, 6)
        wob = rng.uniform(0, 6)
        for y in range(N):
            xx = x + np.sin(y / N * 2 * np.pi + wob) * 2
            k = 0.5 + 0.5 * np.sin(y * 0.3 + wob * 3)
            area(t, xx, y, xx + w, y + 1, col=(int(60 + 34 * k), 18, 12), alpha=int(190 + 60 * k), smooth=0.4)
    F.blood(t, 32, rng.uniform(10, 50), 5, amount=0.9)
    return t


def gouges(seed, n):
    """Claw scores torn into a wall: deep dark grooves in threes, chipped pale at their lips, rust where they
    were dragged; a see-through plate laid over whatever the wall is."""
    t = F.plain(seed, (30, 26, 24), 0.05, 0.1)
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(n):
        cx, cy = rng.uniform(10, 54, 2)
        ang = rng.uniform(np.pi * 0.25, np.pi * 0.75) + rng.choice([0, 0.5])
        ln = rng.uniform(26, 44)
        dx, dy = np.cos(ang), np.sin(ang)
        px, py = -dy, dx
        strokes = []
        for k in (-1, 0, 1):
            ox, oy = cx + px * k * 5, cy + py * k * 5
            a = (ox - dx * ln / 2, oy - dy * ln / 2)
            steps = int(ln * 2)
            strokes.append([(a[0] + dx * ln * s / steps, a[1] + dy * ln * s / steps, 1.0 + 1.6 * np.sin(s / steps * np.pi))
                            for s in range(steps + 1)])      # deepest in the middle of the stroke
        # the chipped lips first, so every groove's dark core lies over them
        for pts in strokes:
            for x, y, w in pts:
                lip = disc(t, x, y, w + 1.0, col=(150, 142, 130), alpha=225)
                t.h[lip] = 0.85
        for pts in strokes:
            for x, y, w in pts:
                core = disc(t, x, y, w * 0.8, col=(20, 16, 14), alpha=255)
                t.h[core] = 0.2
            # rust dragged out of the bottom of the stroke
            ex, ey, _ = pts[-1]
            for j in range(int(rng.integers(4, 12))):
                area(t, ex, ey + j, ex + 1, ey + j + 1, col=(110, 52, 30), alpha=200 - j * 12)
    # fine scratches round them
    for _ in range(22):
        x, y = rng.uniform(0, N, 2)
        ang = rng.uniform(0, np.pi)
        for i in range(int(rng.integers(5, 16))):
            xi, yi = int(x + np.cos(ang) * i), int(y + np.sin(ang) * i)
            if 0 <= xi < N and 0 <= yi < N:
                t.c[yi, xi] = (160, 152, 140)
                t.alpha[yi, xi] = 200
    return t


def cable(seed):
    """Steel cable, its strands twisting round."""
    t = F.plain(seed, (70, 70, 72), 0.04, 0.55)
    y, x = np.mgrid[0:N, 0:N]
    tw = np.sin((x + y * 0.8) / 4 * np.pi)
    t.c *= (0.75 + 0.25 * tw)[..., None]
    t.h = (0.6 + 0.2 * tw).astype(np.float32)
    t.f0[:] = 232
    t.grime(0.4, (40, 26, 18))
    return t


def plastic(seed):
    """Plastic sheeting wound round and round a body, milky and smeared from inside."""
    t = F.plain(seed, (168, 170, 160), 0.08, 0.7)
    rng = t.rng
    for y in range(0, N, 5):
        area(t, 0, y, N, y + 1, mul=0.82, dh=0.1)
    t.mix(np.clip(P.fbm(rng) * 1.6 - 0.2, 0, 1), (90, 60, 48), 0.5)
    for _ in range(3):
        F.blood(t, *rng.uniform(0, N, 2), rng.uniform(4, 9), amount=0.6)
    for y in (12, 34, 52):
        strand(t, [(0, y), (N, y + rng.uniform(-3, 3))], 3, CABLE, smooth=0.5)
    return t


def textures():
    T = {}
    T["hd_scrap"] = lambda: scrap(47100)
    T["hd_scrap_b"] = lambda: scrap(47101)
    T["hd_sheet"] = lambda: sheet(47102, (64, 84, 98))
    T["hd_sheet_b"] = lambda: sheet(47103, (150, 146, 132))
    T["hd_tangle"] = lambda: tangle(47104)
    T["hd_tangle_b"] = lambda: tangle(47105)
    T["hd_nest"] = lambda: nest(47106)
    T["hd_nest_b"] = lambda: nest(47107, bloody=True)
    T["hd_debris"] = lambda: debris(47108)
    T["hd_debris_b"] = lambda: debris(47109)
    T["hd_bones"] = lambda: debris(47110, bones=True)
    T["hd_drag"] = lambda: drag(47111)
    for i in range(3):
        T[f"hd_gouges_{i}"] = lambda i=i: gouges(47112 + i, 1 + i % 2 + (i == 2))
    T["hd_cable"] = lambda: cable(47116)
    T["hd_plastic"] = lambda: plastic(47117)
    return T


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

    scrap, scrap_b, sh, sh_b = "hd_scrap", "hd_scrap_b", "hd_sheet", "hd_sheet_b"
    tg, tg_b = "hd_tangle", "hd_tangle_b"

    def heap(a, b, s1, s2, t1, top):
        """A heap of scrap: a slumped bed of it, panels leant into it at angles, cable knotted through."""
        return [
            [0, 0, 0, 16, 5, 16, a],
            [1, 5, 2, 14, 9, 15, b],
            turned([2, 4, 1, 15, 5, 13, s1], "x", 22.5),
            turned([3, 7, 4, 13, 8, 16, s2], "z", -22.5),
            turned([0, 3, 6, 9, 13, 7, a], "y", 45),
            [4, 9, 4, 11, top, 12, a],
            turned([6, 2, 0, 7.5, 3.5, 16, "hd_scrap_b"], "y", 22.5),       # a length of pipe through it
            turned([0, 2, 8, 16, top + 2, 8, t1], "y", 45),
            turned([0, 2, 8, 16, top + 1, 8, t1], "y", -45),
        ]

    add("hd_heap", heap(scrap, scrap_b, sh, sh_b, tg, 12), "Scrap Heap", "METAL", shape=[[0, 0, 0, 16, 12, 16]])
    add("hd_heap_b", heap(scrap_b, scrap, sh_b, sh, tg_b, 11), "Scrap Heap", "METAL", shape=[[0, 0, 0, 16, 11, 16]])
    add("hd_heap_tall", [
        [0, 0, 0, 16, 10, 16, scrap],
        [1, 10, 1, 15, 15, 15, scrap_b],
        turned([1, 6, 2, 15, 7, 15, sh], "x", 22.5),
        turned([2, 9, 3, 14, 10, 16, sh_b], "z", 22.5),
        [3, 15, 4, 12, 16, 13, scrap],
        turned([7, 10, 0, 8.5, 22, 1.5, scrap_b], "x", -22.5),                 # rebar standing out of it
        turned([11, 12, 9, 12.5, 24, 10.5, scrap], "z", 22.5),
        turned([0, 4, 8, 16, 20, 8, tg], "y", 45),
        turned([0, 4, 8, 16, 18, 8, tg_b], "y", -45),
    ], "Scrap Heap", "METAL", shape=[[0, 0, 0, 16, 16, 16]])
    add("hd_heap_low", [
        [1, 0, 1, 15, 2, 15, scrap],
        turned([2, 1, 3, 10, 2, 12, sh], "y", 22.5),
        turned([7, 1.5, 6, 15, 2.5, 14, scrap_b], "x", 22.5),
        turned([0, 0, 8, 16, 6, 8, tg], "y", 45),
    ], "Scrap", "METAL", shape=[[1, 0, 1, 15, 3, 15]])

    # the nest: a low mat of what it dragged in, cable and rags sticking up out of it
    for n, mat, t in (("hd_nest", "hd_nest", tg), ("hd_nest_b", "hd_nest_b", tg_b)):
        add(n, [
            [0, 0, 0, 16, 3, 16, mat],
            [2, 3, 1, 14, 5, 14, mat],
            [4, 5, 4, 12, 6, 11, mat],
            turned([0, 1, 8, 16, 9, 8, t], "y", 45),
            turned([0, 1, 8, 16, 8, 8, t], "y", -45),
        ], "Nest", "WOOL", shape=[[0, 0, 0, 16, 5, 16]])
    for n in ("hd_debris", "hd_debris_b", "hd_bones"):
        add(n, [[0, 0, 0, 16, 0.25, 16, {"up": n, "down": n, "side": n}]], "Remains" if n == "hd_bones" else "Debris",
            "GRAVEL", decor=True, shape=[])
    add("hd_drag", [[0, 0, 0, 16, 0.2, 16, {"up": "hd_drag", "down": "hd_drag", "side": "hd_drag"}]], "Blood Trail",
        "WOOL", rotate="axis", decor=True, shape=[])
    for i in range(3):
        n = f"hd_gouges_{i}"
        add(n, [[0, 0, 15.8, 16, 16, 16, {"north": n, "south": n, "side": n, "up": n, "down": n}]], "Claw Marks", "STONE",
            decor=True, shape=[])

    # the hanging: steel cable from a plate bolted to the ceiling, a knot at its end
    add("hd_cable", [[7.5, 0, 7.5, 8.5, 16, 8.5, "hd_cable"]], "Steel Cable", "CHAIN", rotate=None, decor=True, shape=[])
    add("hd_cable_top", [[7.5, 0, 7.5, 8.5, 14, 8.5, "hd_cable"], [5, 15, 5, 11, 16, 11, scrap],
                         [6.5, 14, 6.5, 9.5, 15, 9.5, scrap_b]], "Steel Cable", "CHAIN", rotate=None, decor=True, shape=[])
    add("hd_cable_end", [[7.5, 4, 7.5, 8.5, 16, 8.5, "hd_cable"], [6.8, 3, 6.8, 9.2, 6, 9.2, "hd_cable"],
                         turned([5.5, 0, 7.5, 10.5, 1, 8.5, "hd_cable"], "x", 22.5, (8, 1, 8)),
                         turned([7.5, 0, 5.5, 8.5, 1, 10.5, "hd_cable"], "z", 22.5, (8, 1, 8)),
                         [5.5, 0.5, 7.5, 6.5, 3, 8.5, "hd_cable"], [9.5, 0.5, 7.5, 10.5, 3, 8.5, "hd_cable"]],
        "Steel Cable", "CHAIN", rotate=None, decor=True, shape=[])
    # a body wound in plastic and cable, hung by its feet: two blocks, the top one under the cable
    pl = {"side": "hd_plastic", "up": "hd_plastic", "down": "hd_plastic"}
    add("hd_cocoon", [[5, 0, 5, 11, 4, 11, pl], [4, 4, 4.5, 12, 16, 11.5, pl]], "Wrapped Body", "WOOL",
        shape=[[4, 0, 4.5, 12, 16, 11.5]])
    add("hd_cocoon_top", [[4, 0, 4.5, 12, 9, 11.5, pl], [5, 9, 5, 11, 14, 11, pl], [6, 14, 6, 10, 15, 10, pl],
                          [7.5, 15, 7.5, 8.5, 16, 8.5, "hd_cable"]], "Wrapped Body", "WOOL",
        shape=[[4, 0, 4.5, 12, 15, 11.5]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("hd_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} den blocks in {path}")


if __name__ == "__main__":
    main()
