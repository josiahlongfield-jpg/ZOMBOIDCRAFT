"""The rg_ kit: Caldwell Tank Ranges (Josiah, 2026-10-30: the northwest military zone), after
refs/ranges: the target tank hulks shot through and rusting downrange, the range signs. world/county
TankRanges sets them out.

A tank hulk is built of pieces in its own frame, nose to the north (rotate=facing turns it), five
blocks wide and eight long: the tracks down each side (x 0 and 4), the hull between, the turret three
by three on top with the gun out over the nose. Each piece comes in three finishes v: sand (the tan
camouflage, faded and chalked with graffiti), rust (a target hulk, gone orange and holed by rounds),
burnt (blackened, the rust coming through the soot):

- rg_track_<v>: a block of the track run along z, the road wheels on the west face, the fender over
  the top (turn it south for the east track); rg_track_end_<v>: the run's end rounding over the
  idler, the sprocket's teeth, the track's end to the north;
- rg_hull_<v>: the hull between the tracks, its deck on top; rg_glacis_<v>: the hull's nose
  stepping down to the north;
- rg_turret_c_<v> (the turret's rounded corner in the block's north-west), rg_turret_e_<v> (its side,
  the face to the west), rg_turret_m_<v> (its middle, the commander's cupola), rg_turret_f_<v> (its
  front middle, the mantlet and the gun's root), all standing down into the hull's top;
- rg_barrel_<v>: the gun tube along z at the mantlet's height; rg_muzzle_<v>: its end, the
  fume extractor bulging and the muzzle.

And the range's signs, panels on a pair of posts facing north: rg_danger_<0..1> (DANGER / LIVE
FIRING / KEEP OUT, red on white, two tiles), rg_tanks_<0..1> (the red triangle with the tank in it,
TANK CROSSING under it); rg_shell (a dud round stuck nose-down in the dirt) and rg_frag (the ground
scattered with shell fragments, a carpet).

    python3 tools/city/ranges.py          rewrites the rg_ entries in blocks.json

photo_textures.py draws the textures (ranges.register()).
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area  # noqa: E402
from school import Pic  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FINISH = {"sand": (172, 150, 108), "rust": (136, 76, 40), "burnt": (54, 50, 46)}
TRACK = (52, 50, 46)


# ---------------------------------------------------------------- textures

def armour(seed, v):
    """Rolled plate in the finish: the weld seams, the bolt heads, the chipping; the rust's bullet
    holes punched through; the soot streaked over the burnt with rust blooming under it."""
    t = F.plain(seed, FINISH[v], 0.08, 0.25)
    t.f0[:] = 40
    area(t, 0, 30, N, 32, mul=(0.7, 0.7, 0.7), dh=0.2)
    for x in range(6, N, 20):
        area(t, x, 26, x + 2, 28, mul=(0.6, 0.6, 0.6), dh=0.3)
    if v == "sand":
        g = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 1.6 - 0.4, 0, 1)
        t.mix(g, (124, 112, 80), 0.6)
        t.speckle(0.03, (92, 70, 50), 0.6)
        t.grime(0.3, (120, 96, 66))
    elif v == "rust":
        t.vary(0.2, tint=(18, 6, -4))
        t.speckle(0.08, (166, 96, 46), 0.6)
        t.speckle(0.05, (70, 40, 26), 0.6)
        yy, xx = np.mgrid[0:N, 0:N]
        for _ in range(4):
            cx, cy = t.rng.uniform(6, N - 6, 2)
            r = t.rng.uniform(2.0, 3.5)
            d = np.hypot(xx - cx, yy - cy)
            t.c[d < r + 1.5] = t.c[d < r + 1.5] * 0.6 + np.array((180, 110, 60)) * 0.4
            t.c[d < r] = (16, 12, 10)
            t.h[d < r] = 0.1
    else:
        t.vary(0.25)
        g = np.clip(P.fbm(t.rng, ((8, 1.0), (32, 0.5))) * 1.4 - 0.2, 0, 1)
        t.mix(g, (112, 60, 34), 0.5)
        t.streaks(0.4, 0.6)
    return t


def deck(seed, v):
    """The hull's top: the engine deck's grilles to the back, a hatch, the plates' seams."""
    t = armour(seed, v)
    for y in range(36, 60, 4):
        area(t, 8, y, 56, y + 2, col=(30, 28, 26), dh=-0.3)
    area(t, 20, 6, 44, 26, mul=(0.85, 0.85, 0.85), dh=0.2)
    area(t, 22, 8, 42, 24, mul=(1.08, 1.08, 1.08))
    return t


def track_side(seed, v):
    """The track's outside: the links along the bottom and top, five road wheels between with
    their hubs, the return rollers; mud packed in, the finish on the skirt along the top."""
    t = P.Tex(seed, TRACK)
    t.vary(0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    for x in range(0, N, 4):
        area(t, x, 40, x + 2, 48, mul=(1.3, 1.3, 1.3), dh=0.3)
        area(t, x, 0, x + 2, 6, mul=(1.3, 1.3, 1.3), dh=0.3)
    body = armour(seed + 1, v)
    t.c[6:12] = body.c[6:12]
    for cx in (16, 48):
        d = np.hypot(xx - cx, yy - 26)
        wheel = d < 13
        t.c[wheel] = np.array(FINISH[v]) * 0.8
        t.h[wheel] = 0.9
        t.c[(d < 13) & (d > 11)] = (34, 32, 30)
        t.c[d < 4] = (70, 66, 60)
        t.c[d < 2] = (30, 28, 26)
    mud = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.5))) * 1.5 - 0.3, 0, 1) * (yy > 30)
    t.mix(mud, (90, 70, 48), 0.6)
    if v == "rust":
        t.vary(0.15, tint=(20, 6, -6))
    return t


def track_top(seed):
    t = P.Tex(seed, TRACK)
    t.vary(0.1)
    for y in range(0, N, 6):
        area(t, 0, y, N, y + 3, mul=(1.25, 1.25, 1.25), dh=0.3)
        area(t, 28, y, 36, y + 3, col=(90, 86, 80), dh=0.4)
    t.speckle(0.05, (100, 78, 52), 0.6)
    return t


def sign_pic(kind):
    white, red, black = (236, 234, 228), (196, 30, 30), (20, 20, 20)
    p = Pic(2, 1, white, 32300 + len(kind))
    W, H = p.W, p.H
    p.rect(0, 0, W, H, outline=red, width=3)
    if kind == "danger":
        p.rect(3, 3, W - 3, 22, fill=red)
        p.text(W / 2, 6, "DANGER", BOLD, 14, white, anchor="ma")
        p.text(W / 2, 28, "LIVE FIRING", BOLD, 12, black, anchor="ma")
        p.text(W / 2, 46, "KEEP OUT", BOLD, 12, red, anchor="ma")
    else:
        cx = W / 2
        p.poly([(cx, 4), (cx - 22, 40), (cx + 22, 40)], fill=red)
        p.poly([(cx, 12), (cx - 14, 35), (cx + 14, 35)], fill=white)
        p.rect(cx - 9, 27, cx + 9, 32, fill=black)
        p.rect(cx - 5, 23, cx + 4, 27, fill=black)
        p.rect(cx - 14, 24, cx - 4, 25, fill=black)
        p.text(W / 2, 45, "TANK CROSSING", BOLD, 11, black, anchor="ma")
    return p


@lru_cache(maxsize=None)
def pics():
    return {k: sign_pic(k) for k in ("danger", "tanks")}


def shell(seed):
    t = F.plain(seed, (84, 92, 70), 0.08, 0.3)
    area(t, 0, 0, N, 10, col=(170, 130, 60), dh=0.3, smooth=0.6, f0=120)
    t.grime(0.4, (90, 70, 48))
    return t


def frag(seed):
    """Fragments of shell casing in the dirt: twisted bits of grey steel, a copper band, see-through between."""
    t = P.Tex(seed, (0, 0, 0))
    t.alpha[:] = 0
    rng = t.rng
    for _ in range(10):
        x, y = rng.integers(2, N - 8, 2)
        w, h = rng.integers(2, 7, 2)
        col = (176, 120, 70) if rng.random() < 0.15 else (110 + rng.integers(0, 30),) * 3
        area(t, x, y, x + w, y + h, col=col, alpha=255, dh=0.3)
    return t


@lru_cache(maxsize=None)
def textures():
    out = {"rg_track_top": lambda: track_top(32201), "rg_shell": lambda: shell(32202), "rg_frag": lambda: frag(32203)}
    for k, v in enumerate(FINISH):
        out[f"rg_armour_{v}"] = lambda v=v, k=k: armour(32210 + k, v)
        out[f"rg_deck_{v}"] = lambda v=v, k=k: deck(32220 + k, v)
        out[f"rg_track_side_{v}"] = lambda v=v, k=k: track_side(32230 + k, v)
    for kind in ("danger", "tanks"):
        for i in range(2):
            out[f"rg_{kind}_{i}"] = lambda kind=kind, i=i: pics()[kind].tile(i, 32310 + i, smooth=0.4, grime=0.3)
    return out


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- shapes

def faces(tex, side=None, **over):
    f = {d: tex for d in ("north", "south", "east", "west", "up", "down")}
    if side:
        f.update({d: side for d in ("north", "south", "east", "west")})
    f.update({k: v for k, v in over.items() if v})
    return f


def track(v, end=False):
    a, side = f"rg_armour_{v}", f"rg_track_side_{v}"
    run = faces("rg_track_top", west=side, east=side)
    if not end:
        return [[3, 0, 0, 15, 12, 16, run], [1, 12, 0, 16, 13, 16, faces(a)]]
    return [[3, 2, 4, 15, 11, 16, run], [3, 4, 1, 15, 9, 4, run], [1, 12, 2, 16, 13, 16, faces(a)],
            [5, 5, 0, 13, 8, 1, faces("rg_track_top")]]


def hull(v):
    a = f"rg_armour_{v}"
    return [[0, 0, 0, 16, 13, 16, faces(a, up=f"rg_deck_{v}")]]


def glacis(v):
    a = f"rg_armour_{v}"
    return [[0, 0, 10, 16, 13, 16, faces(a)], [0, 0, 5, 16, 10, 10, faces(a)], [0, 1, 1, 16, 6, 5, faces(a)]]


def turret(v, part):
    a = f"rg_armour_{v}"
    f = faces(a)
    if part == "c":
        return [[8, -3, 6, 16, 9, 16, f], [12, -3, 2, 16, 8, 6, f], [4, -3, 10, 8, 8, 16, f]]
    if part == "e":
        return [[5, -3, 0, 16, 9, 16, f], [3, -3, 2, 5, 7, 14, f]]
    if part == "m":
        return [[0, -3, 0, 16, 9, 16, f], [4, 9, 4, 12, 12, 12, f], [5, 12, 5, 11, 12.5, 11, faces("rg_track_top")]]
    return [[0, -3, 5, 16, 9, 16, f], [3, -2, 0, 13, 7, 5, f], [6.5, 1, -2, 9.5, 4, 0, faces(a)]]


def barrel(v, muzzle=False):
    a = f"rg_armour_{v}"
    out = [[6.5, 1, 0, 9.5, 4, 16, faces(a)]]
    if muzzle:
        out = [[6.5, 1, 2, 9.5, 4, 16, faces(a)], [5.5, 0, 6, 10.5, 5, 11, faces(a)], [6, 0.5, 0, 10, 4.5, 2, faces(a, north="rg_track_top")]]
    return out


def sign(face):
    post = "ext_black_metal"
    return [[0, 6, 7, 16, 16, 8.5, faces(post, north=face)], [2, 0, 8.5, 3, 15, 9.5, post], [13, 0, 8.5, 14, 15, 9.5, post]]


def blocks():
    out = {}

    def add(name, boxes, title, shape, rotate="facing", sound="METAL", cutout=False, extra=None):
        spec = {"model": "boxes", "boxes": [[round(v, 3) if isinstance(v, float) else v for v in q[:6]] + q[6:] for q in boxes],
                "title": title, "sound": sound, "shape": shape}
        if rotate:
            spec["rotate"] = rotate
        if cutout:
            spec["render"] = "cutout"
        spec.update(extra or {})
        out[name] = spec

    for v in FINISH:
        add(f"rg_track_{v}", track(v), "Tank Track", [[1, 0, 0, 16, 13, 16]])
        add(f"rg_track_end_{v}", track(v, True), "Tank Track", [[1, 0, 2, 16, 13, 16]])
        add(f"rg_hull_{v}", hull(v), "Tank Hull", [[0, 0, 0, 16, 13, 16]])
        add(f"rg_glacis_{v}", glacis(v), "Tank Hull", [[0, 0, 4, 16, 13, 16]])
        for part in ("c", "e", "m", "f"):
            add(f"rg_turret_{part}_{v}", turret(v, part), "Tank Turret", [[0, 0, 0, 16, 9, 16]])
        add(f"rg_barrel_{v}", barrel(v), "Tank Gun", [[6, 0, 0, 10, 5, 16]])
        add(f"rg_muzzle_{v}", barrel(v, True), "Tank Gun", [[6, 0, 0, 10, 5, 16]])
    for kind in ("danger", "tanks"):
        for i in range(2):
            add(f"rg_{kind}_{i}", sign(f"rg_{kind}_{i}"), "Range Sign", [[0, 6, 7, 16, 16, 9.5]])
    add("rg_shell", [[6, 0, 6, 10, 9, 10, faces("rg_shell")], [6.5, 9, 6.5, 9.5, 11, 9.5, faces("rg_shell")]], "Dud Round",
        [[6, 0, 6, 10, 11, 10]], rotate=None)
    add("rg_frag", [[0, 0, 0, 16, 0.4, 16, "rg_frag"]], "Shell Fragments", [[0, 0, 0, 16, 0.4, 16]],
        rotate=None, cutout=True, extra={"decor": True})
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    data["blocks"] = {k: v for k, v in data["blocks"].items() if not k.startswith("rg_")}
    data["blocks"].update(made)
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} range blocks in {path}")


if __name__ == "__main__":
    main()
