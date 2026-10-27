"""walkcheck.py DUMP [--png OUT.png] [--list N] [--why X Z]: can players and zombies walk everywhere inside?

Reads a preview dump (DUMP.json + DUMP.vox, as SitePreview, CountyPreview and the other
preview tools write them) and the collision boxes in CitySpecs.java, builds the world's
collision at quarter-block resolution and walks a zombie-sized body (half a block wide, two
blocks tall, stepping half a block, jumping a block and a quarter, dropping up to three) in
from the dump's edge. Doors, gates and hatches count as open; ladders and climbable blocks
can be climbed.

It reports
  - floors under a roof nobody can walk to (with and without jumping), as clusters;
  - stair blocks nobody can stand on;
  - flat roofs (bare tops over three blocks up, not trees) and whether a way up reaches them.

Run it on every builder that places stairs; anything it lists is a stair or a door the game
won't let a player or a zombie through.
"""
import json
import re
import sys
from collections import deque
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
Q = 4            # sub-cells per block
BODY = 8         # body height in sub-cells (2 blocks: a zombie is 1.95)
STEP = 2         # walk up half a block
JUMP = 5         # jump up a block and a quarter
DROP = 12        # drop three blocks
TILE = 40

# ------------------------------------------------------------------ collision shapes

def city_specs():
    """name -> (kind, decor, boxes) from CitySpecs.java."""
    src = (ROOT / "src/main/java/com/zomboidcraft/world/blocks/CitySpecs.java").read_text()
    out = {}
    for m in re.finditer(r'^\s+s\("([a-z0-9_]+)", ([A-Z]+), ([A-Z_ |0-9]+), [^,]+, SoundType\.[A-Z_]+, [0-9.]+f((?:, [-0-9.]+)*)\);',
                         src, re.M):
        name, kind, flags, nums = m.groups()
        vals = [float(v) for v in nums.split(",")[1:]] if nums else []
        boxes = [vals[i:i + 6] for i in range(0, len(vals) - 5, 6)]
        out[name] = (kind, "DECOR" in flags, boxes)
    return out


def climbables():
    blocks = json.loads((ROOT / "tools/city/blocks.json").read_text())["blocks"]
    return {n for n, s in blocks.items() if s.get("climb")}


def rot(box, facing):
    x0, y0, z0, x1, y1, z1 = box
    if facing == "east":
        return [16 - z1, y0, x0, 16 - z0, y1, x1]
    if facing == "south":
        return [16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0]
    if facing == "west":
        return [z0, y0, 16 - x1, z1, y1, 16 - x0]
    return list(box)


def props(state):
    i = state.find("[")
    if i < 0:
        return state, {}
    return state[:i], dict(kv.split("=", 1) for kv in state[i + 1:-1].split(",") if "=" in kv)


NONE = re.compile(r"^(air|cave_air|void_air|short_grass|tall_grass|short_dry_grass|tall_dry_grass|grass|fern|large_fern|"
                  r"bush|firefly_bush|dead_bush|poppy|dandelion|cornflower|azure_bluet|oxeye_daisy|allium|blue_orchid|"
                  r"lily_of_the_valley|sunflower|lilac|rose_bush|peony|wildflowers|pink_petals|leaf_litter|"
                  r"[a-z_]*tulip|[a-z_]*sapling|[a-z_]*_sign|[a-z_]*_wall_sign|[a-z_]*_hanging_sign|torch|wall_torch|"
                  r"soul_torch|[a-z_]*_button|lever|[a-z_]*rail|redstone_wire|tripwire|water|lava|vine|glow_lichen|"
                  r"kelp|kelp_plant|seagrass|tall_seagrass|sugar_cane|cobweb|wheat|carrots|potatoes|beetroots|"
                  r"sweet_berry_bush|hanging_roots|structure_void|light|[a-z_]*_pressure_plate|[a-z_]*_banner|"
                  r"[a-z_]*_wall_banner|lily_pad|[a-z_]*_door|[a-z_]*_trapdoor|[a-z_]*_fence_gate|snow|"
                  r"red_mushroom|brown_mushroom|nether_sprouts|torchflower|frogspawn|[a-z_]*_carpet_none)$")


def vanilla(name, p):
    """Collision boxes of a vanilla block (as much of the game's shapes as the world uses)."""
    n = name[len("minecraft:"):]
    if NONE.search(n):
        if n == "snow" and int(p.get("layers", "1")) > 1:
            return [[0, 0, 0, 16, (int(p["layers"]) - 1) * 2, 16]]
        return []
    if n.endswith("_stairs"):
        top = p.get("half") == "top"
        slab = [0, 8, 0, 16, 16, 16] if top else [0, 0, 0, 16, 8, 16]
        back = rot([0, 0 if top else 8, 0, 16, 8 if top else 16, 8], p.get("facing", "north"))
        return [slab, back]
    if n.endswith("_slab"):
        t = p.get("type", "bottom")
        return [[0, 0, 0, 16, 16, 16]] if t == "double" else [[0, 8 if t == "top" else 0, 0, 16, 16 if t == "top" else 8, 16]]
    if n.endswith("_carpet"):
        return [[0, 0, 0, 16, 1, 16]]
    if n.endswith("_fence") or n.endswith("_wall") or n.endswith("_pane") or n in ("iron_bars",):
        a, b = (4, 12) if n.endswith("_wall") else (7, 9) if n.endswith("_pane") or n == "iron_bars" else (6, 10)
        h = 16 if n.endswith("_pane") or n == "iron_bars" else 24
        out = [[a, 0, a, b, h, b]]
        for side, box in (("north", [a, 0, 0, b, h, a]), ("south", [a, 0, b, b, h, 16]),
                          ("west", [0, 0, a, a, h, b]), ("east", [b, 0, a, 16, h, b])):
            if p.get(side, "false") not in ("false", "none"):
                out.append(box)
        return out
    if n == "ladder":
        return [rot([0, 0, 13, 16, 16, 16], p.get("facing", "north"))]
    if n.endswith("_bed"):
        return [[0, 0, 0, 16, 9, 16]]
    if n in ("farmland", "dirt_path"):
        return [[0, 0, 0, 16, 15, 16]]
    if n in ("chest", "trapped_chest", "ender_chest"):
        return [[1, 0, 1, 15, 14, 15]]
    if n == "campfire" or n == "soul_campfire":
        return [[0, 0, 0, 16, 7, 16]]
    if n in ("flower_pot", "iron_chain", "chain", "lantern", "soul_lantern", "lightning_rod", "end_rod") or n.startswith("potted_"):
        return [[6, 0, 6, 10, 6, 10]] if n == "flower_pot" or n.startswith("potted_") else \
            [[5, 0, 5, 11, 9, 11]] if n.endswith("lantern") else [[7, 0, 7, 9, 16, 9]]
    if n.endswith("anvil"):
        return [[0, 0, 2, 16, 16, 14]]
    if n == "mud" or n == "soul_sand":
        return [[0, 0, 0, 16, 14, 16]]
    return [[0, 0, 0, 16, 16, 16]]


class Shapes:
    def __init__(self):
        self.specs = city_specs()
        self.climb = climbables()

    def of(self, state):
        """(boxes, climbable) for a block state as the dump names it."""
        name, p = props(state)
        if name == "air":
            return [], False
        if name.startswith("minecraft:"):
            return vanilla(name, p), name == "minecraft:ladder"
        if name == "barricade":
            # boards over a doorway: a hammer pries them off, zombies knock them loose
            return [], False
        spec = self.specs.get(name)
        if spec is None:
            return [[0, 0, 0, 16, 16, 16]], False
        kind, decor, boxes = spec
        if decor or kind in ("DOOR", "TRAPDOOR") or name.startswith("bunk_blast_"):
            # a bunker's blast door is a door too: a crowbar pries it (barricade/CrowbarItem)
            return [], name in self.climb
        if "stairs" in name:
            # a steel flight's side stringers are a pixel thick: a body fits between them, though
            # not the half-block lattice this walks on, so they're left out
            boxes = [b for b in boxes if min(b[3] - b[0], b[5] - b[2]) > 2]
            # and its bottom plate is what you stand on over an open frame, not a rug to walk over
            boxes = [b[:4] + [max(b[4], 4)] + b[5:] if b[1] <= 0 and b[4] <= 2 else b for b in boxes]
        if kind in ("FACING",) or (kind == "PLAIN" and False):
            boxes = [rot(b, p.get("facing", "north")) for b in boxes]
        elif kind == "AXIS" and p.get("axis") == "x":
            boxes = [rot(b, "east") for b in boxes]
        return boxes, name in self.climb


# ------------------------------------------------------------------ the walk graph

def quant(box):
    """A box in pixels to sub-cells: lips of a pixel or two out of the block (nosings, sills)
    dropped, an edge a pixel into a sub-cell not counted, but never a thin wall lost."""
    if box[1] <= 0 and box[4] <= 2:
        return 0, 0, 0, 0, 0, 0     # a rug, a decal: walked over
    out = []
    for a in range(3):
        v0, v1 = box[a], box[a + 3]
        v0 = 0 if -3 < v0 < 0 else v0
        v1 = 16 if 16 < v1 < 19 else v1
        if v1 <= v0:
            return 0, 0, 0, 0, 0, 0
        lo, hi = int(np.floor((v0 + 1) / 4)), int(np.ceil((v1 - 1) / 4))
        if hi <= lo:
            lo = int(np.floor((v0 + v1) / 8))
            hi = lo + 1
        out.append((lo, hi))
    return out[0][0], out[1][0], out[2][0], out[0][1], out[1][1], out[2][1]


def runs(occ):
    """For each sub-cell, how many free sub-cells start there going up (along axis 0)."""
    r = np.zeros(occ.shape, np.int16)
    free = ~occ
    acc = np.zeros(occ.shape[1:], np.int16)
    for s in range(occ.shape[0] - 1, -1, -1):
        acc = np.where(free[s], np.minimum(acc + 1, 64), 0)
        r[s] = acc
    return r


def main():
    dump = sys.argv[1]
    args = sys.argv[2:]
    png = args[args.index("--png") + 1] if "--png" in args else None
    nlist = int(args[args.index("--list") + 1]) if "--list" in args else 12
    meta = json.loads(Path(dump + ".json").read_text())
    W, H, D = meta["size"]
    vox = np.fromfile(dump + ".vox", dtype="<u2").reshape(H, D, W)
    pal = meta["palette"]
    sh = Shapes()
    P = len(pal)
    stamp = np.zeros((P, Q, Q, Q), bool)       # [id, sy, sz, sx], the part inside the block
    outside = {}                                 # id -> boxes reaching out of the block (sub-cell ranges)
    climb = np.zeros(P, bool)
    plate = np.zeros(P, bool)                    # a floor a pixel thick (a grate): walked over on a
                                                 # floor, stood on where it spans air
    stairish = np.array(["stair" in n for n in pal])
    treeish = np.array([("leaves" in n or "tree_" in n or "_log" in n) for n in pal])
    for i, st in enumerate(pal):
        boxes, cl = sh.of(st)
        climb[i] = cl
        for b in boxes:
            if b[1] <= 0 and b[4] <= 2 and b[3] - b[0] >= 16 and b[5] - b[2] >= 16:
                plate[i] = True
            x0, y0, z0, x1, y1, z1 = quant(b)
            if x1 <= x0 or y1 <= y0 or z1 <= z0:
                continue
            stamp[i, max(0, y0):min(Q, y1), max(0, z0):min(Q, z1), max(0, x0):min(Q, x1)] = True
            if x0 < 0 or y0 < 0 or z0 < 0 or x1 > Q or y1 > Q or z1 > Q:
                outside.setdefault(i, []).append((x0, y0, z0, x1, y1, z1))
    full = stamp.all(axis=(1, 2, 3))
    stairish &= stamp.any(axis=(1, 2, 3))      # a "way to the stairs" sign is no stair
    # the body stands on a lattice of half-block cells ("halves"): its footprint is one half square
    nodes = {}        # (hx, hz) -> array of stand levels (sub-cells)
    edges = []        # (hx, hz, s, hx2, hz2, s2)
    covered = {}      # (hx, hz) -> bool per level: something overhead
    lows = []         # (hx, hz, s, s of what's overhead): floors with under two blocks of room
    for tx in range(0, W, TILE):
        for tz in range(0, D, TILE):
            # the tile plus a block round it (shapes reach out a block; moves cross into the next)
            ax0, az0 = max(0, tx - 1), max(0, tz - 1)
            ax1, az1 = min(W, tx + TILE + 2), min(D, tz + TILE + 2)
            v = vox[:, az0:az1, ax0:ax1]
            h, dz, dx = v.shape
            # stamp[v] is [y, z, x, sy, sz, sx] -> [y, sy, z, sz, x, sx]
            occ = stamp[v].transpose(0, 3, 1, 4, 2, 5).reshape(h * Q, dz * Q, dx * Q).copy()
            for i, bl in outside.items():
                ys, zs, xs = np.nonzero(v == i)
                if len(ys) == 0:
                    continue
                for (x0, y0, z0, x1, y1, z1) in bl:
                    for sy in range(y0, y1):
                        for sz in range(z0, z1):
                            for sx in range(x0, x1):
                                yy, zz, xx = ys * Q + sy, zs * Q + sz, xs * Q + sx
                                ok = (yy >= 0) & (yy < h * Q) & (zz >= 0) & (zz < dz * Q) & (xx >= 0) & (xx < dx * Q)
                                occ[yy[ok], zz[ok], xx[ok]] = True
            # a plate over air holds a body at the block's foot: its own bottom quarter counts (a
            # touch high to stand on, but the room under it stays whole)
            ys, zs, xs = np.nonzero(plate[v][1:] & ~stamp[v[:-1]].any(axis=(3, 4, 5)))
            for y_, z_, x_ in zip(ys + 1, zs, xs):
                occ[y_ * Q, z_ * Q:(z_ + 1) * Q, x_ * Q:(x_ + 1) * Q] = True
            n = h * Q
            foot = occ.reshape(n, dz * 2, 2, dx * 2, 2).any(axis=(2, 4))
            del occ
            rf = runs(foot)
            below = np.zeros_like(foot)
            below[1:] = foot[:-1]
            clim = np.repeat(np.repeat(np.repeat(climb[v], Q, axis=0), 2, axis=1), 2, axis=2)
            stand = (below | clim) & (rf >= BODY)
            stand[0] = False
            # head-knockers: a floor with something between one and two blocks over it
            low = below & (rf >= 4) & (rf < BODY)
            low[0] = False
            # a roof overhead: a solid block (not a tree's) anywhere above in the block column
            built = (stamp[v].any(axis=(3, 4, 5)) & ~treeish[v])
            above_b = np.zeros_like(built)
            acc = np.zeros(built.shape[1:], bool)
            for y in range(h - 1, -1, -1):
                above_b[y] = acc
                acc = acc | built[y]
            above = np.repeat(np.repeat(np.repeat(above_b, Q, axis=0), 2, axis=1), 2, axis=2)
            ix0, iz0 = (tx - ax0) * 2, (tz - az0) * 2
            ix1, iz1 = (min(W, tx + TILE) - ax0) * 2, (min(D, tz + TILE) - az0) * 2
            ox, oz = ax0 * 2, az0 * 2
            for zz in range(iz0, iz1):
                for xx in range(ix0, ix1):
                    lv = np.nonzero(stand[:, zz, xx])[0]
                    if len(lv):
                        nodes[(xx + ox, zz + oz)] = lv
                        covered[(xx + ox, zz + oz)] = above[lv, zz, xx]
                    for s in np.nonzero(low[:, zz, xx])[0]:
                        lows.append((xx + ox, zz + oz, int(s), int(s + rf[s, zz, xx])))
            for east in (True, False):
                if east:
                    xe = min(ix1, dx * 2 - 1)
                    sl_a = (slice(None), slice(iz0, iz1), slice(ix0, xe))
                    sl_b = (slice(None), slice(iz0, iz1), slice(ix0 + 1, xe + 1))
                else:
                    ze = min(iz1, dz * 2 - 1)
                    sl_a = (slice(None), slice(iz0, ze), slice(ix0, ix1))
                    sl_b = (slice(None), slice(iz0 + 1, ze + 1), slice(ix0, ix1))
                A, B, RA, RB = stand[sl_a], stand[sl_b], rf[sl_a], rf[sl_b]
                for d in range(-DROP, JUMP + 1):
                    lo, hi = max(0, -d), min(n, n - d)
                    if hi <= lo:
                        continue
                    ok = A[lo:hi] & B[lo + d:hi + d]
                    if not ok.any():
                        continue
                    # both footprints clear from their floor to a body over the higher of the two
                    up = max(0, d)
                    ok &= (RA[lo:hi] >= up + BODY) & (RB[lo + d:hi + d] >= up - d + BODY)
                    ss, zs, xs = np.nonzero(ok)
                    for s1, z1, x1 in zip(ss + lo, zs, xs):
                        X, Z = int(x1) + ix0 + ox, int(z1) + iz0 + oz
                        edges.append((X, Z, int(s1), X + 1 if east else X, Z if east else Z + 1, int(s1) + d))
            # climbing: straight up and down a ladder a quarter block at a time
            for zz in range(iz0, iz1):
                for xx in range(ix0, ix1):
                    for s in np.nonzero(clim[:, zz, xx] & stand[:, zz, xx])[0]:
                        if s + 1 < n and stand[s + 1, zz, xx]:
                            X, Z = xx + ox, zz + oz
                            edges.append((X, Z, int(s), X, Z, int(s) + 1))
    # ------------------------------------------------------------------ the search
    idx = {}
    keys = []
    for (x, z), lv in nodes.items():
        for s in lv:
            idx[(x, z, int(s))] = len(keys)
            keys.append((x, z, int(s)))
    N = len(keys)
    adj_all = [[] for _ in range(N)]
    adj_walk = [[] for _ in range(N)]
    undirected = [[] for _ in range(N)]
    for (x, z, s, x2, z2, s2) in edges:
        a, b = idx.get((x, z, s)), idx.get((x2, z2, s2))
        if a is None or b is None:
            continue
        undirected[a].append(b)
        undirected[b].append(a)
        # down any drop of up to three blocks; up a step on foot, or a jump
        for p, q, rise in ((a, b, s2 - s), (b, a, s - s2)):
            if rise <= JUMP and rise >= -DROP:
                adj_all[p].append(q)
                if rise <= STEP:
                    adj_walk[p].append(q)
    # seeds: the lowest stand on every column round the dump's edge
    seeds = []
    for (x, z), lv in nodes.items():
        if x == 0 or z == 0 or x == 2 * W - 1 or z == 2 * D - 1:
            seeds.append(idx[(x, z, int(lv[0]))])

    def bfs(adj):
        seen = np.zeros(N, bool)
        dq = deque(seeds)
        seen[seeds] = True
        while dq:
            a = dq.popleft()
            for b in adj[a]:
                if not seen[b]:
                    seen[b] = True
                    dq.append(b)
        return seen

    walk_ok = bfs(adj_walk)
    any_ok = bfs(adj_all)
    # --why X Z: every place to stand in that block column, and how it's reached (for debugging a pocket)
    for k in range(len(args)):
        if args[k] == "--why":
            bx, bz = int(args[k + 1]), int(args[k + 2])
            for hx in (2 * bx, 2 * bx + 1):
                for hz in (2 * bz, 2 * bz + 1):
                    for s in nodes.get((hx, hz), []):
                        i = idx[(hx, hz, int(s))]
                        print(f"why x {hx / 2:.1f} z {hz / 2:.1f} y {s / Q:.2f}: walk {bool(walk_ok[i])} jump {bool(any_ok[i])}"
                              f" links {[(keys[j][0] / 2, keys[j][1] / 2, keys[j][2] / Q) for j in undirected[i]]}")
    cov = np.array([bool(covered[(x, z)][list(nodes[(x, z)]).index(s)]) for (x, z, s) in keys])
    ground_s = int(np.median([keys[i][2] for i in seeds])) if seeds else 0
    print(f"{dump}: {N} places to stand, {walk_ok.sum()} walkable from outside, {any_ok.sum()} with jumps; "
          f"ground at y {ground_s / Q:.2f}")

    def clusters(mask):
        lab = -np.ones(N, int)
        out = []
        for i in np.nonzero(mask)[0]:
            if lab[i] >= 0:
                continue
            comp = [i]
            lab[i] = len(out)
            dq = deque([i])
            while dq:
                a = dq.popleft()
                for b in undirected[a]:
                    if mask[b] and lab[b] < 0:
                        lab[b] = len(out)
                        comp.append(b)
                        dq.append(b)
            out.append(comp)
        out.sort(key=len, reverse=True)
        return out

    def describe(comp):
        xs = [keys[i][0] // 2 for i in comp]
        zs = [keys[i][1] // 2 for i in comp]
        ss = [keys[i][2] for i in comp]
        return f"{len(comp):5d} spots  x {min(xs)}..{max(xs)}  z {min(zs)}..{max(zs)}  y {min(ss) / Q:.2f}..{max(ss) / Q:.2f}"

    # floors under a roof nobody walks to (tiny pockets of one or two spots are furniture gaps)
    lost = cov & ~walk_ok
    cl = [c for c in clusters(lost) if len(c) >= 3]
    # a pocket with a stair, a ladder, a hatch or a door in it was meant to be reached; one with
    # none is an attic, a roof void or a sealed shaft
    way = np.array([("stair" in n or "door" in n or "ladder" in n or "hatch" in n or "climb" in n) for n in pal])
    meant, sealed = [], []
    for c in cl:
        xs = [keys[i][0] // 2 for i in c]
        zs = [keys[i][1] // 2 for i in c]
        ys = [keys[i][2] // Q for i in c]
        box = vox[max(0, min(ys) - 1):max(ys) + 2, max(0, min(zs) - 1):max(zs) + 2, max(0, min(xs) - 1):max(xs) + 2]
        (meant if way[box].any() else sealed).append(c)
    print(f"\nindoor floor nobody can walk to, with a stair or door in it: {sum(len(c) for c in meant)} spots in"
          f" {len(meant)} pockets ({sum(1 for c in meant if any_ok[c].all())} only by jumping);"
          f" sealed (attics, voids): {sum(len(c) for c in sealed)} spots in {len(sealed)}")
    for c in meant[:nlist]:
        print("  ", describe(c), " (jump)" if any_ok[c].all() else "")
    # stair blocks: does anyone stand on them?
    stair_cells = np.argwhere(stairish[vox])   # y, z, x
    standing = {}
    for i in np.nonzero(walk_ok)[0]:
        x, z, s = keys[i]
        standing.setdefault((x // 2, z // 2), []).append(s)
    bad = []
    for y, z, x in stair_cells:
        lv = standing.get((int(x), int(z)), [])
        if not any(y * Q <= s <= y * Q + Q + 1 for s in lv):
            # a stair under another (a roof's eave, the underside of a flight) isn't a tread
            if y + 1 < H and stairish[vox[y + 1, z, x]]:
                continue
            bad.append((int(x), int(y), int(z), pal[vox[y, z, x]]))
    tread = [b for b in bad if "half=top" not in b[3] and not b[3].startswith("minecraft:")]
    print(f"\nstair blocks nobody can stand on: {len(tread)} (of {len(stair_cells)}; vanilla roof stairs left out)")
    groups = {}
    for x, y, z, n in tread:
        groups.setdefault((x // 8, z // 8, y // 4), []).append((x, y, z, n))
    for g, lst in sorted(groups.items(), key=lambda kv: -len(kv[1]))[:nlist]:
        x, y, z, n = lst[0]
        print(f"   {len(lst):3d} near x {x} y {y} z {z}  {n}")
    # head-knockers next to where people walk: a floor a body can step onto but not stand up on
    walk_at = {}
    for i in np.nonzero(walk_ok)[0]:
        x, z, s = keys[i]
        walk_at.setdefault((x, z), set()).add(s)
    knock = {}
    for x, z, s, top in lows:
        near = any(any(abs(t - s) <= STEP for t in walk_at.get((x + dx, z + dz), ())) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if not near:
            continue
        # only floors proper (a full block under it), not the top of a desk, a rail or a bed
        if s % Q or not full[vox[s // Q - 1, z // 2, x // 2]]:
            continue
        room = (top - s) / Q
        name = pal[vox[top // Q, z // 2, x // 2]].split("[")[0]
        # under a block and a half is plainly furniture or a ledge; trees are walked round
        if room < 1.5 or name.startswith("tree_") or "leaves" in name:
            continue
        knock.setdefault(name, []).append((x // 2, s / Q, z // 2, (top - s) / Q))
    total = sum(len(v) for v in knock.values())
    print(f"\nhead-knockers beside walkways (1.5 to 2 blocks of room): {total} spots")
    for name, lst in sorted(knock.items(), key=lambda kv: -len(kv[1]))[:nlist]:
        x, y, z, room = lst[0]
        print(f"   {len(lst):4d} under {name:28s} e.g. x {x} y {y:.2f} z {z} ({room:.2f} room)")
    # doors: someone must be able to stand in the doorway
    doors = np.array([("door" in n and "half=lower" in n and "trapdoor" not in n) for n in pal])
    shut = []
    for y, z, x in np.argwhere(doors[vox]):
        lv = set()
        for hx in (2 * x, 2 * x + 1):
            for hz in (2 * z, 2 * z + 1):
                lv |= walk_at.get((int(hx), int(hz)), set())
        if not any(y * Q - Q <= s <= y * Q + STEP for s in lv):
            shut.append((int(x), int(y), int(z), pal[vox[y, z, x]].split("[")[0]))
    print(f"\ndoorways nobody can walk through: {len(shut)} (of {int(doors[vox].sum())})")
    for x, y, z, n in shut[:nlist]:
        print(f"   x {x} y {y} z {z}  {n}")
    # roofs: bare tops three blocks over the ground, not on trees
    roofs = np.array([(not cov[i]) and keys[i][2] >= ground_s + 3 * Q and
                      not treeish[vox[keys[i][2] // Q - 1 if keys[i][2] % Q == 0 else keys[i][2] // Q, keys[i][1] // 2, keys[i][0] // 2]]
                      for i in range(N)]) if N else np.zeros(0, bool)
    rc = [c for c in clusters(roofs) if len(c) >= 16]
    # flat: most of it on one level (parapets and plant aside)
    rc = [c for c in rc if np.bincount([keys[i][2] for i in c]).max() >= 0.6 * len(c)]
    up = [c for c in rc if walk_ok[c].any()]
    print(f"\nflat roofs: {len(rc)}, {len(up)} with a way up on foot")
    for c in rc[:nlist]:
        print("  ", describe(c), " reached" if walk_ok[c].any() else " NO WAY UP")
    if png:
        from PIL import Image
        img = np.zeros((2 * D, 2 * W, 3), np.uint8) + 40
        for i, (x, z, s) in enumerate(keys):
            if cov[i]:
                c = (60, 160, 60) if walk_ok[i] else (230, 160, 30) if any_ok[i] else (220, 40, 40)
                if img[z, x, 0] != 220:
                    img[z, x] = c
            elif roofs[i]:
                img[z, x] = (90, 120, 230) if walk_ok[i] else (160, 60, 200)
        Image.fromarray(img).resize((W * 4, D * 4), Image.NEAREST).save(png)
        print("map:", png)


if __name__ == "__main__":
    main()
