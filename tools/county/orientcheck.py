"""orientcheck.py DUMP [DUMP...] [--all] [--list N]: linear pieces turned across their own line.

A rail, banister, balustrade, window or pane is drawn along one axis (its axis=x|z, or for an
edge piece its facing: a north or south edge runs along x). Laid in a row they must all run along
the row, so they join up. This lists every one whose row runs the other way: it has a neighbour
of the same kind beside it across its axis and nothing along it, so it stands as a stub turned
across the line (the bug Josiah found on a church gallery and on windows).

It also lists windows turned out of their wall: a window, pane or shutter that fills its cell sits
in a wall, so more solid blocks meet it along its run (the wall either side) than across it (the
room behind, the street in front). One with the wall across it and open air along it stands side-on,
its glass facing along the wall (the lit tower-crown slits Josiah found). A window written without
its axis or facing counts as the game places it: axis=z, facing=north. Those are listed too, since
a bare window is only right on a wall that happens to run that way.

--all checks every block with an axis or facing, not only the linear kinds.
"""
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

LINEAR = re.compile(r"(rail|banister|balust|window|glass|pane|fence|_bars|kerb|curb|hedge|railing|parapet|"
                    r"chainlink|picket|guard|barrier|lattice|grille|grate_fence)")
SKIP = re.compile(r"(railcar|rail_post|trail|_corner|_end|_post|_cap|_base|ext_win_)")


BLOCKS = json.loads((Path(__file__).resolve().parents[2] / "tools/city/blocks.json").read_text())["blocks"]


def drawn(name):
    """Which way a block's model runs as drawn (unturned): "x", "z" or None (square, or unknown)."""
    spec = BLOCKS.get(name)
    if not spec or "boxes" not in spec:
        return None
    bx = spec["boxes"]
    fx = sum(1 for b in bx if b[0] <= 0.5 and b[3] >= 15.5 and b[5] - b[2] < 15)
    fz = sum(1 for b in bx if b[2] <= 0.5 and b[5] >= 15.5 and b[3] - b[0] < 15)
    return "x" if fx > fz else "z" if fz > fx else None


def run_axis(state):
    """The axis a piece runs along as it shows in the world ("x" or "z"), or None."""
    name = state.split("[")[0]
    m = re.search(r"axis=([xyz])", state)
    if m:
        if m.group(1) not in "xz":
            return None
        # the model as drawn runs along z when axis=z; one drawn along x shows turned
        flip = drawn(name) == "x"
        return {"x": "z", "z": "x"}[m.group(1)] if flip else m.group(1)
    m = re.search(r"facing=(north|south|east|west)", state)
    if m:
        base = drawn(name) or "x"         # facing north, as drawn
        ns = m.group(1) in ("north", "south")
        return base if ns else {"x": "z", "z": "x"}[base]
    return None


WALL_PIECE = re.compile(r"(window|_win_|^win_|glass|pane|shutter|transom|louvre|grille)")
NOT_WALL = re.compile(r"(shards|duct|teller|propane|bottle|glasses|jar|cabinet|case|counter|table|door|lamp|mirror|tv|"
                      r"screen|display|fridge|freezer|cooler|oven|microwave)")


def wall_run(name):
    """For a window-like block that fills its cell along one way, which way it runs as drawn (unturned)."""
    spec = BLOCKS.get(name)
    if not spec or "boxes" not in spec or not spec.get("rotate") or not WALL_PIECE.search(name) or NOT_WALL.search(name):
        return None
    bx = spec["boxes"]
    xs = min(16, max(b[3] for b in bx)) - max(0, min(b[0] for b in bx))
    zs = min(16, max(b[5] for b in bx)) - max(0, min(b[2] for b in bx))
    if xs >= 15.5 and zs >= 15.5:
        return "x" if spec["rotate"] == "facing" else "z"
    run = "x" if xs >= 15.5 else "z" if zs >= 15.5 else None
    # a guard or rail hugging one edge of its cell isn't set in a wall
    lo, hi = (2, 5) if run == "x" else (0, 3)
    if run and not any(b[lo] <= 8 <= b[hi] for b in bx):
        return None
    return run


def panel(name):
    """A tall piece filling its cell one way (slats, louvres, partitions): it counts as wall."""
    spec = BLOCKS.get(name)
    bx = spec.get("boxes") if spec else None
    if not bx:
        return False
    xs = min(16, max(b[3] for b in bx)) - max(0, min(b[0] for b in bx))
    zs = min(16, max(b[5] for b in bx)) - max(0, min(b[2] for b in bx))
    ys = min(16, max(b[4] for b in bx)) - max(0, min(b[1] for b in bx))
    return (xs >= 15.5 or zs >= 15.5) and ys >= 12


def wall_axis(state):
    """The way an in-wall window runs as the game places it, or None if it isn't one."""
    name = state.split("[")[0]
    drawn_run = wall_run(name)
    if drawn_run is None:
        return None
    other = {"x": "z", "z": "x"}
    if BLOCKS[name]["rotate"] == "axis":
        m = re.search(r"axis=([xz])", state)
        return drawn_run if (m.group(1) if m else "z") == "z" else other[drawn_run]
    m = re.search(r"facing=(north|south|east|west)", state)
    return drawn_run if (m.group(1) if m else "north") in ("north", "south") else other[drawn_run]


def solid_block(state):
    """Something a window can be set in: anything but air, plants and small furniture."""
    if state == "air":
        return False
    name = state.split("[")[0]
    spec = BLOCKS.get(name)
    if spec is None:
        return not re.search(r"(grass|fern|flower|bush|leaves|vine|carpet|torch|button|pressure_plate|snow|rail)", name)
    if spec.get("model") == "cross":
        return False
    return spec.get("model") != "boxes" or wall_run(name) is not None or panel(name)


def walls(dump, nlist):
    meta = json.loads(Path(dump + ".json").read_text())
    W, H, D = meta["size"]
    vox = np.fromfile(dump + ".vox", dtype="<u2").reshape(H, D, W)
    pal = meta["palette"]
    run = [wall_axis(s) for s in pal]
    want = np.array([r is not None for r in run])
    solid = np.array([solid_block(s) for s in pal])
    bare = Counter()
    bad = defaultdict(list)
    cells = np.argwhere(want[vox])
    for y, z, x in cells:
        i = vox[y, z, x]
        if "[" not in pal[i]:
            bare[pal[i]] += 1
        if x == 0 or z == 0 or x == W - 1 or z == D - 1:
            continue    # cut off by the dump's edge

        def closed(dx, dz):
            xx, zz = x + dx, z + dz
            return int(0 <= xx < W and 0 <= zz < D and bool(solid[vox[y, zz, xx]]))
        xs = closed(1, 0) + closed(-1, 0)
        zs = closed(0, 1) + closed(0, -1)
        along, across = (xs, zs) if run[i] == "x" else (zs, xs)
        if along < across:
            bad[family(pal[i])].append((int(x), int(y), int(z), pal[i]))
    total = sum(len(v) for v in bad.values())
    print(f"{dump}: {total} windows turned out of their wall (of {len(cells)} in-wall pieces)")
    for f, lst in sorted(bad.items(), key=lambda kv: -len(kv[1]))[:nlist]:
        x, y, z, s = lst[0]
        print(f"   {len(lst):5d} {f:28s} e.g. x {x} y {y} z {z}  {s}")
    for name, n in bare.most_common(nlist):
        print(f"   bare {n:5d} {name} (placed as axis=z / facing=north)")
    return total


def family(state):
    return state.split("[")[0].replace("_broken", "").replace("_lit", "")


def check(dump, every, nlist):
    meta = json.loads(Path(dump + ".json").read_text())
    W, H, D = meta["size"]
    vox = np.fromfile(dump + ".vox", dtype="<u2").reshape(H, D, W)
    pal = meta["palette"]
    axis = [run_axis(s) for s in pal]
    fam = [family(s) for s in pal]
    want = np.array([a is not None and (every or (LINEAR.search(f) and not SKIP.search(f))) for a, f in zip(axis, fam)])
    if not want.any():
        print(f"{dump}: nothing linear")
        return 0
    famid = {f: i for i, f in enumerate(sorted(set(fam)))}
    fa = np.array([famid[f] for f in fam])
    ax = np.array([0 if a == "x" else 1 if a == "z" else -1 for a in axis])
    cells = np.argwhere(want[vox])
    bad = defaultdict(list)
    for y, z, x in cells:
        i = vox[y, z, x]

        def same(dx, dz):
            xx, zz = x + dx, z + dz
            return 0 <= xx < W and 0 <= zz < D and want[vox[y, zz, xx]] and fa[vox[y, zz, xx]] == fa[i]
        def solid(dx, dz):
            xx, zz = x + dx, z + dz
            return 0 <= xx < W and 0 <= zz < D and vox[y, zz, xx] != 0
        # along its line it meets its own kind, or a post or a wall it butts up to (a corner)
        along = (solid(1, 0) or solid(-1, 0)) if ax[i] == 0 else (solid(0, 1) or solid(0, -1))
        across = (same(0, 1) or same(0, -1)) if ax[i] == 0 else (same(1, 0) or same(-1, 0))
        if across and not along:
            bad[fam[i]].append((int(x), int(y), int(z), pal[i]))
    total = sum(len(v) for v in bad.values())
    print(f"{dump}: {total} pieces turned across their row (of {len(cells)} checked)")
    for f, lst in sorted(bad.items(), key=lambda kv: -len(kv[1]))[:nlist]:
        x, y, z, s = lst[0]
        print(f"   {len(lst):5d} {f:28s} e.g. x {x} y {y} z {z}  {s}")
    return total


def main():
    args = [a for a in sys.argv[1:]]
    every = "--all" in args
    nlist = int(args[args.index("--list") + 1]) if "--list" in args else 12
    dumps = [a for i, a in enumerate(args) if not a.startswith("--") and (i == 0 or args[i - 1] != "--list")]
    for d in dumps:
        check(d, every, nlist)
        walls(d, nlist)


if __name__ == "__main__":
    main()
