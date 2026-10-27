"""orientcheck.py DUMP [DUMP...] [--all] [--list N]: linear pieces turned across their own line.

A rail, banister, balustrade, window or pane is drawn along one axis (its axis=x|z, or for an
edge piece its facing: a north or south edge runs along x). Laid in a row they must all run along
the row, so they join up. This lists every one whose row runs the other way: it has a neighbour
of the same kind beside it across its axis and none along it, so it stands as a stub turned
across the line (the bug Josiah found on a church gallery and on windows).

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
        along = (same(1, 0) or same(-1, 0)) if ax[i] == 0 else (same(0, 1) or same(0, -1))
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


if __name__ == "__main__":
    main()
