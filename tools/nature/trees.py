#!/usr/bin/env python3
"""Turns the broadleaf and conifer trees of the Ultimate Trees Pack into the county's chunky trees.

The pack (Deezl, bought by Josiah) never goes into the repo or the jar. This reads its OBJ and
textures and writes only what the mod needs, all made from it:

  tools/nature/tex/tree_*.png          64px bark, end grain and leaf textures, graded to the county's
                                       wet, gloomy look (tools/city/nature.py hands them to gen_blocks)
  tools/city/blocks.json  tree_*       the tree blocks: bark, log ends, half-round trunk edges,
                                       limbs four thicknesses wide that join their neighbours,
                                       dense and sparse leaves, autumn and sick leaves
  src/main/resources/data/zomboidcraft/trees.bin
                                       every tree as a template of those blocks, at three sizes

Each tree is voxelised a quarter block at a time: the trunk and limbs filled solid, every limb
measured from its bark's area so a twig stays a twig, leaves counted where the leaf cards are
opaque. A block that's mostly wood is bark; the trunk's rounded edge is a half block; a limb is a
centred post with arms reaching into whichever neighbours it runs on into.

    python3 tools/nature/trees.py PACK_DIR [--preview DIR]

PACK_DIR holds the pack's unzipped OBJ/ and Textures/ folders.
"""
import argparse
import gzip
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
TEX_OUT = ROOT / "tools/nature/tex"
BLOCKS = ROOT / "tools/city/blocks.json"
TEMPLATES = ROOT / "src/main/resources/data/zomboidcraft/trees.bin"
N = 64
R = 4  # sub-voxels per block when filling the wood

# species: the pack's objects, its bark and leaf textures, sizes (scales of the model), grading
SPECIES = {
    "oak": dict(objs=["Oak_01", "Oak_02", "Oak_03"], wood=["Oak_trunk"], leaf=["Oak_leaves"],
                bark="oak trunk_d.png", leaves="oak leaf_d.png", scales=(0.42, 0.58, 0.74),
                green=(78, 96, 46), autumn={"brown": (124, 86, 44)}),
    "maple": dict(objs=["Maple_01", "Maple_02", "Maple_03"], wood=["Maple_trunk"], leaf=["Maple_leaves"],
                  bark="maple trunk_d.png", leaves="maple leaf_d.png", scales=(0.38, 0.52, 0.66),
                  green=(84, 102, 44), autumn={"red": (124, 40, 28), "gold": (172, 116, 36)}),
    "beech": dict(objs=["Beech_01", "Beech_02", "Beech_03"], wood=["Beech_Bark"], leaf=["Beech_Leaves"],
                  bark="beech bark_d.png", leaves="beech leaf_d.png", scales=(0.55, 0.72, 0.9),
                  green=(88, 108, 48), autumn={"gold": (176, 128, 40)}),
    "birch": dict(objs=["Birch_01", "Birch_02", "Birch_03"], wood=["Birch_Trunk"], leaf=["Birch_Leaves"],
                  bark="birch trunk_d.png", leaves="birch leaf_d.png", scales=(0.55, 0.72, 0.88),
                  green=(96, 114, 52), autumn={"gold": (186, 150, 52)}),
    "elm": dict(objs=["Elm_01", "Elm_02", "Elm_03"], wood=["elm_trunk"], leaf=["elm_leaves"],
                bark="elm trunk_d.png", leaves="elm leaf_d.png", scales=(0.45, 0.6, 0.76),
                green=(74, 92, 44), autumn={"gold": (162, 128, 46)}),
    "spruce": dict(objs=["Spruce_01", "Spruce_02", "Spruce_03"], wood=["Spruce_trunk"], leaf=["Spruce_branches"],
                   bark="spruce trunk_d.png", leaves="spruce branch_d.png", scales=(0.5, 0.66, 0.82),
                   green=(46, 66, 44), autumn={}),
    "fir": dict(objs=["Fir_01", "Fir_02", "Fir_03"], wood=["fir_trunk"], leaf=["fir_leaf"],
                bark="fir trunk_d.png", leaves="fir leaf_d.png", scales=(0.34, 0.44, 0.55),
                green=(44, 70, 54), autumn={}),
    "cedar": dict(objs=["Juniper_01", "Juniper_02", "Juniper_03"], wood=["Juniper_trunk"], leaf=["Juniper_branches"],
                  bark="juniper  trunk_d.png", leaves="juniper branch_d.png", scales=(0.5, 0.64, 0.8),
                  green=(52, 72, 50), autumn={}),
    "willow": dict(objs=["Willow_01", "Willow_02", "Willow_03"], wood=["Willow_trunk"], leaf=["Willow_branches"],
                   bark="Willow trunk_d.png", leaves="willow branch_d.png", scales=(0.7, 0.86, 1.02),
                   green=(98, 112, 52), autumn={"gold": (164, 144, 58)}),
    "dead": dict(objs=["Dead_Tree_01", "Dead_Tree_02", "Dead_Tree_03"], wood=["Old_trunk", "Old_stump"],
                 leaf=["Old_branch"], bark="Old trunk_d.png", leaves="Old branch_d.png", scales=(0.55, 0.75, 0.95),
                 green=(78, 66, 54), autumn={}),
}
SICK = (118, 120, 104)  # the grey, sick canopies
THICK = (3, 5, 8, 12)   # limb widths in pixels
THICKEN = 1.45


# ---------------------------------------------------------------- reading the pack

def load_obj(path):
    """Every object's triangles: positions (T,3,3), uvs (T,3,2) and material names (T)."""
    vs, vts = [], []
    objs = {}
    cur = mtl = None
    for line in open(path, errors="replace"):
        c = line[:2]
        if c == "v ":
            vs.append(line.split()[1:4])
        elif c == "vt":
            vts.append(line.split()[1:3])
        elif c == "o ":
            cur = line[2:].strip()
            objs[cur] = ([], [], [])
        elif line.startswith("usemtl"):
            mtl = line.split(None, 1)[1].strip()
        elif c == "f ":
            pts = [p.split("/") for p in line.split()[1:]]
            vi = [int(p[0]) - 1 for p in pts]
            ti = [int(p[1]) - 1 if len(p) > 1 and p[1] else 0 for p in pts]
            for k in range(1, len(vi) - 1):
                objs[cur][0].append((vi[0], vi[k], vi[k + 1]))
                objs[cur][1].append((ti[0], ti[k], ti[k + 1]))
                objs[cur][2].append(mtl)
    V = np.array(vs, np.float64)
    T = np.array(vts, np.float64) if vts else np.zeros((1, 2))
    out = {}
    for name, (fv, ft, fm) in objs.items():
        if fv:
            out[name] = (V[np.array(fv)], T[np.array(ft)], np.array(fm))
    return out


def find(objs, prefix):
    for k in objs:
        if k.startswith(prefix + "_tree") or k.startswith(prefix + ".") or k.split("_tree")[0] == prefix:
            return objs[k]
    raise KeyError(prefix)


def rgba(tex_dir, name):
    return np.array(Image.open(tex_dir / name).convert("RGBA"), np.float32)


# ---------------------------------------------------------------- sampling

def samples(tris, uvs, h, normals=False):
    """Points spread over triangles about h apart: positions, uvs and the area each stands for."""
    P, U, A, NR = [], [], [], []
    nrm = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    nrm = nrm / np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
    e = np.maximum(np.linalg.norm(tris[:, 1] - tris[:, 0], axis=1), np.linalg.norm(tris[:, 2] - tris[:, 0], axis=1))
    e = np.maximum(e, np.linalg.norm(tris[:, 2] - tris[:, 1], axis=1))
    n = np.clip(np.ceil(e / h).astype(int), 1, 200)
    area = 0.5 * np.linalg.norm(np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0]), axis=1)
    for k in np.unique(n):
        sel = np.nonzero(n == k)[0]
        i, j = np.meshgrid(np.arange(k + 1), np.arange(k + 1), indexing="ij")
        m = (i + j) <= k
        a = (i[m] + 1 / 3) / (k + 1)
        b = (j[m] + 1 / 3) / (k + 1)
        keep = a + b <= 1
        a, b = a[keep], b[keep]
        w = np.stack([1 - a - b, a, b], 1)  # (S,3)
        t = tris[sel]
        u = uvs[sel]
        P.append(np.einsum("sk,tkd->tsd", w, t).reshape(-1, 3))
        U.append(np.einsum("sk,tkd->tsd", w, u).reshape(-1, 2))
        A.append(np.repeat(area[sel] / len(w), len(w)))
        NR.append(np.repeat(nrm[sel], len(w), axis=0))
    if normals:
        return np.concatenate(P), np.concatenate(U), np.concatenate(A), np.concatenate(NR)
    return np.concatenate(P), np.concatenate(U), np.concatenate(A)


def lookup(img, uv):
    H, W = img.shape[:2]
    x = np.clip((uv[:, 0] % 1.0) * W, 0, W - 1).astype(int)
    y = np.clip((1 - uv[:, 1] % 1.0) * H, 0, H - 1).astype(int)
    return img[y, x]


# ---------------------------------------------------------------- voxelising

def near(shell, axis, k, sign):
    """Whether there is shell within k cells in one direction along an axis."""
    out = np.zeros_like(shell)
    n = shell.shape[axis]
    for s in range(1, k + 1):
        if s >= n:
            break
        src = [slice(None)] * 3
        dst = [slice(None)] * 3
        if sign > 0:
            src[axis], dst[axis] = slice(s, None), slice(0, n - s)
        else:
            src[axis], dst[axis] = slice(0, n - s), slice(s, None)
        out[tuple(dst)] |= shell[tuple(src)]
    return out


def fill(shell, k):
    """Solid wood: the shell plus whatever it closes in on a slice through any of the three axes,
    as long as bark lies close on every side in that slice (a limb's cross-section, not the hole
    between two limbs that cross)."""
    solid = shell.copy()
    close = [near(shell, a, k, 1) & near(shell, a, k, -1) for a in range(3)]
    for axis in range(3):
        moved = np.moveaxis(shell, axis, 0)
        inside = np.zeros_like(moved)
        for i in range(moved.shape[0]):
            sl = moved[i]
            if not sl.any():
                continue
            img = Image.fromarray(np.pad(sl, 1).astype(np.uint8) * 255, "L")
            ImageDraw.floodfill(img, (0, 0), 128)
            a = np.array(img)[1:-1, 1:-1]
            inside[i] = a == 0
        inside = np.moveaxis(inside, 0, axis)
        b, c = [a for a in range(3) if a != axis]
        solid |= inside & close[b] & close[c]
    return solid


def voxelise(tris, uvs, mats, wood_mats, leaf_mats, leaf_img, scale):
    """The tree as cells: {(x,y,z): kind} with kinds 'bark', 'log', ('side', facing), ('limb', px, mask),
    'leaves', 'sparse'. The trunk's foot sits at (0, 0, 0)."""
    tris = tris * scale
    wood = np.isin(mats, wood_mats)
    leaf = np.isin(mats, leaf_mats)
    wt = tris[wood]
    base = wt[wt[:, :, 1].min(1) < 1.2 * scale + 0.3]
    cx, cz = base[:, :, 0].mean(), base[:, :, 2].mean()
    # the trunk stands in the middle of its block, not on a block's edge
    tris = tris - np.array([cx - 0.5, 0, cz - 0.5])
    wt = tris[wood]
    lo = np.floor(tris.reshape(-1, 3).min(0)) - 1
    hi = np.ceil(tris.reshape(-1, 3).max(0)) + 1
    lo[1] = max(lo[1], -1)
    dims = (hi - lo).astype(int)

    # wood: shell sampled finely, filled, counted per block; bark area per block for the limbs
    P, _, A, NR = samples(wt, uvs[wood], 0.5 / R, normals=True)
    sub = np.floor((P - lo) * R).astype(int)
    ok = (sub >= 0).all(1) & (sub < dims * R).all(1)
    shell = np.zeros(tuple(dims * R), bool)
    shell[tuple(sub[ok].T)] = True
    solid = fill(shell, int(2.2 * scale * R) + 2)
    vol = solid.reshape(dims[0], R, dims[1], R, dims[2], R).sum((1, 3, 5)) / R ** 3
    cell = np.floor(P - lo).astype(int)
    ok = (cell >= 0).all(1) & (cell < dims).all(1)
    area = np.zeros(tuple(dims))
    np.add.at(area, tuple(cell[ok].T), A[ok])
    # each bark sample's limb diameter: how far the solid wood runs straight in from it
    diam = across(solid, (P - lo) * R, NR) / R
    dcell = {}
    for c, d in zip(map(tuple, cell[ok]), diam[ok]):
        dcell.setdefault(c, []).append(d)

    # leaves: card area where the card's texture is opaque
    lcov = np.zeros(tuple(dims))
    if leaf.any():
        P, U, A = samples(tris[leaf], uvs[leaf], 0.12)
        alpha = lookup(leaf_img, U)[:, 3]
        cell = np.floor(P - lo).astype(int)
        ok = (cell >= 0).all(1) & (cell < dims).all(1) & (alpha > 110)
        np.add.at(lcov, tuple(cell[ok].T), A[ok])

    cells = {}
    full = vol >= 0.5
    for x, y, z in zip(*np.nonzero(full)):
        cells[(x, y, z)] = "bark"
    # the trunk's rounded edge: a half block on the side the wood is
    sv = solid.reshape(dims[0], R, dims[1], R, dims[2], R)
    for x, y, z in zip(*np.nonzero((vol >= 0.18) & ~full)):
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if not any(0 <= x + dx < dims[0] and 0 <= z + dz < dims[2] and full[x + dx, y, z + dz] for dx, dz in nb):
            continue
        s = sv[x, :, y, :, z, :]
        idx = np.argwhere(s)
        mx, mz = idx[:, 0].mean() - (R - 1) / 2, idx[:, 2].mean() - (R - 1) / 2
        f = ("east" if mx > 0 else "west") if abs(mx) > abs(mz) else ("south" if mz > 0 else "north")
        cells[(x, y, z)] = ("side", f)
    # limbs: diameter from the bark's area over a block's length, fattened a little so the trunk
    # and main limbs read as solid wood from the ground (the pack's trees are slender)
    for x, y, z in zip(*np.nonzero((area > 0) & ~full)):
        if (x, y, z) in cells:
            continue
        ds = dcell.get((x, y, z))
        if not ds or area[x, y, z] < 0.02:
            continue
        dr = float(np.median(ds))
        # under two sub-blocks across the ray can't tell a twig from a branch: go by the bark's area
        d = (dr if dr * R >= 2.2 else area[x, y, z] / math.pi) * 16 * THICKEN
        if d < 1.6:
            continue
        if d >= 13:
            cells[(x, y, z)] = "bark"
            continue
        px = 3 if d < 4.5 else 5 if d < 7 else 8 if d < 10.5 else 12
        cells[(x, y, z)] = ["limb", px, 0]
    bridge(cells, sv, dims)
    connect(cells, sv, dims)
    # leaves where nothing woody is, judged against how leafy this tree is at its leafiest (one
    # tree's sprays are big cards, another's single leaves): averaged over each block's
    # neighbours so the crown fills out, gaps and all
    if lcov.any():
        lb = lcov.copy()
        for axis in range(3):
            lb = (np.roll(lb, 1, axis) + lb + np.roll(lb, -1, axis)) / 3
        ref = float(np.percentile(lb[lcov > 0], 90))
        dense = (lb >= 0.42 * ref) & (lcov > 0)
        sparse = ((lb >= 0.17 * ref) & (lcov > 0)) | (lb >= 0.3 * ref)
        for x, y, z in zip(*np.nonzero(dense | sparse)):
            if (x, y, z) not in cells:
                cells[(x, y, z)] = "leaves" if dense[x, y, z] else "sparse"
    off = lo.astype(int)
    out = {}
    for (x, y, z), k in cells.items():
        X, Y, Z = x + off[0], y + off[1], z + off[2]
        if Y < 0:
            continue
        out[(X, Y, Z)] = tuple(k) if isinstance(k, list) else k
    return out


def across(solid, pos, nrm):
    """Distance through solid wood from each surface point along its normal, inward either way."""
    best = np.zeros(len(pos))
    shape = np.array(solid.shape)
    for sign in (1, -1):
        run = np.zeros(len(pos))
        alive = np.ones(len(pos), bool)
        for k in range(1, 4 * R * 2):
            q = np.floor(pos - sign * nrm * k * 0.5).astype(int)
            inb = (q >= 0).all(1) & (q < shape).all(1)
            hit = np.zeros(len(pos), bool)
            qi = q[inb]
            hit[inb] = solid[qi[:, 0], qi[:, 1], qi[:, 2]]
            alive &= hit
            run += alive
            if not alive.any():
                break
        best = np.maximum(best, run * 0.5 + 0.5)
    return best


def bridge(cells, sv, dims):
    """Limbs that only meet corner to corner get a block of limb between them."""
    wood = {c for c, k in cells.items() if k != "leaves"}
    for c in list(wood):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if abs(dx) + abs(dy) + abs(dz) < 2:
                        continue
                    o = (c[0] + dx, c[1] + dy, c[2] + dz)
                    if o not in wood:
                        continue
                    # face neighbours of c on the way to o
                    steps = [(c[0] + dx, c[1], c[2]), (c[0], c[1] + dy, c[2]), (c[0], c[1], c[2] + dz)]
                    steps = [s for s in steps if s != c]
                    if any(s in wood for s in steps):
                        continue
                    px = min(k[1] if isinstance(k, list) else 12 for k in (cells[c], cells[o]))
                    pick = steps[0] if dy == 0 else (c[0], c[1] + dy, c[2])
                    cells[pick] = ["limb", px, 0]
                    wood.add(pick)


DIRS6 = [("north", (0, 0, -1)), ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)),
         ("up", (0, 1, 0)), ("down", (0, -1, 0))]


def face(sv, c, side):
    """The quarter-block wood of a block's face layer on one side, as a grid."""
    s = sv[c[0], :, c[1], :, c[2], :]  # (sx, sy, sz)
    return {"north": s[:, :, 0], "south": s[:, :, R - 1], "east": s[R - 1, :, :], "west": s[0, :, :],
            "up": s[:, R - 1, :], "down": s[:, 0, :]}[side]


OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def connect(cells, sv, dims):
    """A limb's arms: toward each neighbour the wood really runs on into (their touching faces
    share wood, give or take a quarter block), so limbs that only lie side by side stay apart."""
    def grown(a):
        g = a.copy()
        g[1:] |= a[:-1]
        g[:-1] |= a[1:]
        g[:, 1:] |= a[:, :-1]
        g[:, :-1] |= a[:, 1:]
        return g

    for c, k in cells.items():
        if not isinstance(k, list):
            continue
        mine = sv[c[0], :, c[1], :, c[2], :].any()
        m = 0
        for i, (side, d) in enumerate(DIRS6):
            o = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
            if o not in cells or cells[o] in ("leaves", "sparse"):
                continue
            if not (0 <= o[0] < dims[0] and 0 <= o[1] < dims[1] and 0 <= o[2] < dims[2]):
                continue
            theirs = sv[o[0], :, o[1], :, o[2], :].any()
            a, b = face(sv, c, side), face(sv, o, OPPOSITE[side])
            # a block added to join two limbs has no wood of its own: it joins whatever is there
            if not mine or not theirs or (grown(a) & b).any():
                m |= 1 << i
        k[2] = m


# ---------------------------------------------------------------- textures

def grade(rgb, mean, contrast=1.0, sat=1.0):
    lum = rgb @ np.array([0.299, 0.587, 0.114], np.float32)
    rel = (lum / max(float(lum.mean()), 1.0) - 1) * contrast + 1
    out = rel[..., None] * np.array(mean, np.float32)
    if sat != 1.0:
        g = out @ np.array([0.299, 0.587, 0.114], np.float32)
        out = g[..., None] + (out - g[..., None]) * sat
    return np.clip(out, 0, 255)


def save(name, rgb, alpha=None):
    TEX_OUT.mkdir(parents=True, exist_ok=True)
    a = np.full(rgb.shape[:2], 255, np.float32) if alpha is None else alpha
    Image.fromarray(np.dstack([np.clip(rgb, 0, 255), a]).astype(np.uint8), "RGBA").save(TEX_OUT / f"{name}.png")


def tiled_crop(img, px_per_block, seed):
    """A 64px block-sized piece of a tiling photo texture, at the size it was on the tree."""
    rng = np.random.default_rng(seed)
    H, W = img.shape[:2]
    size = int(min(W, H, max(16, px_per_block)))
    x0, y0 = rng.integers(0, W - size + 1), rng.integers(0, H - size + 1)
    crop = Image.fromarray(img[y0:y0 + size, x0:x0 + size, :3].astype(np.uint8)).resize((N, N), Image.LANCZOS)
    a = np.array(crop, np.float32)
    # make it wrap: blend each edge with the far one
    w = np.clip(np.minimum(np.arange(N), N - 1 - np.arange(N)) / 8, 0, 1)
    rolled = np.roll(np.roll(a, N // 2, 0), N // 2, 1)
    m = np.minimum(w[:, None], w[None, :])[..., None]
    return a * m + rolled * (1 - m)


def bark_tex(tex_dir, sp, cfg, ppm):
    img = rgba(tex_dir, cfg["bark"])
    a = tiled_crop(img, ppm, hash(sp) % 1000)
    mean = a.reshape(-1, 3).mean(0)
    # wet and dark: the county's bark is soaked, a little greener in the cracks
    a = grade(a, mean * np.array([0.9, 0.92, 0.9]), 1.2, 0.8)
    save(f"tree_{sp}_bark", a)
    return a


def rings_tex(tex_dir, sp, bark):
    """End grain from the pack's stump, ringed with this tree's bark."""
    img = rgba(tex_dir, "Old stump_d.png")
    H, W = img.shape[:2]
    c = Image.fromarray(img[int(H * 0.1):int(H * 0.9), int(W * 0.1):int(W * 0.9), :3].astype(np.uint8)).resize((N, N), Image.LANCZOS)
    a = np.array(c, np.float32)
    a = grade(a, (118, 92, 62), 1.1, 0.8)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - N / 2 + 0.5, yy - N / 2 + 0.5)
    edge = r > N / 2 - 4
    a[edge] = bark[edge]
    save(f"tree_{sp}_rings", a)


def leaf_tex(tex_dir, sp, cfg, ppm, colour, dense, suffix, seed):
    """Leaves for a block: the pack's own sprays, stamped at their size on the tree all over a
    tile that wraps, shaded deeper underneath, cut out sharp."""
    img = rgba(tex_dir, cfg["leaves"])
    H, W = img.shape[:2]
    spray = Image.fromarray(img.astype(np.uint8), "RGBA")
    rng = np.random.default_rng(seed)
    big = N * 3
    canvas = np.zeros((big, big, 4), np.float32)
    target = 0.82 if dense else (0.42 if sp == "dead" else 0.48)
    # the texture's width on the tree, in metres, is its width in blocks: stamp it that big
    size = int(np.clip(W / ppm * N * 3, 18, big * 0.9))
    for _ in range(400):
        s = int(size * rng.uniform(0.7, 1.2))
        sp_img = spray.resize((s, s), Image.LANCZOS).rotate(rng.uniform(0, 360), Image.BILINEAR, expand=True)
        arr = np.array(sp_img, np.float32)
        shade = rng.uniform(0.62, 1.08)
        arr[..., :3] *= shade
        h, w = arr.shape[:2]
        x0, y0 = rng.integers(-w // 2, big - w // 2), rng.integers(-h // 2, big - h // 2)
        for oy in (-big, 0, big):
            for ox in (-big, 0, big):
                X0, Y0 = x0 + ox, y0 + oy
                xa, ya, xb, yb = max(0, X0), max(0, Y0), min(big, X0 + w), min(big, Y0 + h)
                if xa >= xb or ya >= yb:
                    continue
                src = arr[ya - Y0:yb - Y0, xa - X0:xb - X0]
                m = (src[..., 3:4] > 110) & (canvas[ya:yb, xa:xb, 3:4] < 1)
                m = m | ((src[..., 3:4] > 110) & (rng.random() < 0.5))
                canvas[ya:yb, xa:xb] = np.where(m, np.concatenate([src[..., :3], np.full(src[..., :1].shape, 255)], -1),
                                                canvas[ya:yb, xa:xb])
        cov = (canvas[..., 3] > 0).mean()
        if cov >= target:
            break
    small = Image.fromarray(canvas.astype(np.uint8), "RGBA").resize((N, N), Image.BOX)
    a = np.array(small, np.float32)
    alpha = np.where(a[..., 3] > 120, 255, 0).astype(np.float32)
    rgb = a[..., :3] / np.maximum(a[..., 3:4] / 255, 0.05)
    m = alpha > 0
    rgb = grade(rgb, colour, 1.35, 0.9)
    rgb[~m] = 0
    save(f"tree_{sp}_{suffix}", rgb, alpha)


def scale_of(tris, uvs, img_size=512):
    """Texture pixels per metre on the model."""
    a3 = 0.5 * np.linalg.norm(np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0]), axis=1)
    d1, d2 = uvs[:, 1] - uvs[:, 0], uvs[:, 2] - uvs[:, 0]
    a2 = 0.5 * np.abs(d1[:, 0] * d2[:, 1] - d1[:, 1] * d2[:, 0]) * img_size * img_size
    ok = (a3 > 1e-6) & (a2 > 1e-6)
    return float(np.sqrt(np.median(a2[ok] / a3[ok])))


# ---------------------------------------------------------------- blocks

def limb_boxes(px, tex):
    a, b = 8 - px / 2, 8 + px / 2
    return [[a, a, a, b, b, b, tex]]


def block_specs(species):
    out = {}
    for sp, cfg in species.items():
        bark, rings = f"tree_{sp}_bark", f"tree_{sp}_rings"
        title = "Dead" if sp == "dead" else sp.capitalize()
        out[f"tree_{sp}_bark"] = {"tex": bark, "title": f"{title} Bark", "sound": "WOOD", "drops": "minecraft:oak_log"}
        out[f"tree_{sp}_log"] = {"tex": {"side": bark, "up": rings, "down": rings}, "title": f"{title} Log",
                                 "sound": "WOOD", "drops": "minecraft:oak_log"}
        out[f"tree_{sp}_side"] = {"model": "boxes", "boxes": [[0, 0, 0, 16, 16, 8, {"side": bark, "up": rings, "down": rings}]],
                                  "rotate": "facing", "title": f"{title} Trunk Edge", "sound": "WOOD", "drops": "minecraft:stick"}
        for px in THICK:
            out[f"tree_{sp}_limb_{px}"] = {"model": "boxes", "boxes": limb_boxes(px, bark), "branch": px,
                                          "title": f"{title} Limb", "sound": "WOOD",
                                          "drops": "minecraft:stick" if px < 8 else "minecraft:oak_log"}
        if cfg["leaves"]:
            leaves = "Bare Twigs" if sp == "dead" else f"{title} Leaves"
            for col in ["leaves"] + [f"leaves_{k}" for k in cfg["autumn"]]:
                out[f"tree_{sp}_{col}"] = {"tex": f"tree_{sp}_{col}", "title": leaves, "sound": "GRASS",
                                           "leaves": True, "drops": ""}
                out[f"tree_{sp}_{col}_sparse"] = {"tex": f"tree_{sp}_{col}_sparse", "title": leaves,
                                                  "sound": "GRASS", "leaves": True, "drops": ""}
                if sp == "dead":
                    # bare twigs are a crossed spray, not a box: a box of them reads as a dark lump
                    out[f"tree_{sp}_{col}_sparse"]["model"] = "cross"
    for col in ("leaves_sick", "leaves_sick_sparse"):
        out[f"tree_{col}"] = {"tex": f"tree_{col}", "title": "Sick Leaves", "sound": "GRASS", "leaves": True, "drops": ""}
    return out


def write_blocks(specs):
    data = json.loads(BLOCKS.read_text())
    blocks = {k: v for k, v in data["blocks"].items() if not k.startswith("tree_")}
    blocks.update(specs)
    data["blocks"] = blocks
    BLOCKS.write_text(json.dumps(data, indent=1) + "\n")


# ---------------------------------------------------------------- templates

def state(sp, kind):
    if kind == "bark":
        return f"tree_{sp}_bark"
    if kind == "log":
        return f"tree_{sp}_log"
    if kind in ("leaves", "sparse"):
        return f"tree_{sp}_leaves" + ("_sparse" if kind == "sparse" else "")
    if kind[0] == "side":
        return f"tree_{sp}_side[facing={kind[1]}]"
    _, px, m = kind
    props = ",".join(f"{n}={'true' if m >> i & 1 else 'false'}" for i, (n, _) in enumerate(DIRS6))
    return f"tree_{sp}_limb_{px}[{props}]"


def write_templates(templates):
    """trees.bin (gzip): int count, then per tree: UTF name, short palette size, UTF states,
    int cells, then per cell byte x, short y, byte z, short state."""
    buf = bytearray()

    def utf(s):
        b = s.encode()
        buf.extend(struct.pack(">H", len(b)))
        buf.extend(b)

    buf.extend(struct.pack(">i", len(templates)))
    for name, cells in templates:
        pal = sorted({s for s in cells.values()})
        idx = {s: i for i, s in enumerate(pal)}
        utf(name)
        buf.extend(struct.pack(">H", len(pal)))
        for s in pal:
            utf(s)
        buf.extend(struct.pack(">i", len(cells)))
        for (x, y, z), s in sorted(cells.items(), key=lambda c: (c[0][1], c[0][0], c[0][2])):
            buf.extend(struct.pack(">bhbH", x, y, z, idx[s]))
    TEMPLATES.parent.mkdir(parents=True, exist_ok=True)
    TEMPLATES.write_bytes(gzip.compress(bytes(buf), 9))
    return len(buf)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pack", type=Path)
    ap.add_argument("--only", default="")
    ap.add_argument("--preview", type=Path)
    args = ap.parse_args()
    tex_dir = next(args.pack.rglob("oak trunk_d.png")).parent
    obj = next(args.pack.rglob("Deezl Trees Pack.obj"))
    objs = load_obj(obj)
    templates = []
    only = set(args.only.split(",")) - {""}
    for sp, cfg in SPECIES.items():
        if only and sp not in only:
            continue
        leaf_img = rgba(tex_dir, cfg["leaves"]) if cfg["leaves"] else None
        bark_ppm = None
        for i, prefix in enumerate(cfg["objs"]):
            tris, uvs, mats = find(objs, prefix)
            if bark_ppm is None:
                w = np.isin(mats, cfg["wood"])
                bark_ppm = scale_of(tris[w], uvs[w])
                bark = bark_tex(tex_dir, sp, cfg, bark_ppm * 0.7)
                rings_tex(tex_dir, sp, bark)
                if leaf_img is not None:
                    lf = np.isin(mats, cfg["leaf"])
                    ppm = scale_of(tris[lf], uvs[lf])
                    for k, col in [("leaves", cfg["green"])] + [(f"leaves_{a}", c) for a, c in cfg["autumn"].items()]:
                        leaf_tex(tex_dir, sp, cfg, ppm, col, True, k, 11 + i)
                        leaf_tex(tex_dir, sp, cfg, ppm, col, False, k + "_sparse", 23 + i)
                    if sp == "elm":
                        leaf_tex(tex_dir, "", cfg, ppm, SICK, True, "leaves_sick", 31)
                        leaf_tex(tex_dir, "", cfg, ppm, SICK, False, "leaves_sick_sparse", 37)
            for size, scale in zip(("small", "medium", "large"), cfg["scales"]):
                cells = voxelise(tris, uvs, mats, cfg["wood"], cfg["leaf"], leaf_img, scale)
                if sp == "dead":
                    # broken tops show their end grain; the twigs are thin all through
                    # a bare crown is mostly air: keep about half the twigs that grow straight off wood
                    wood = {c for c, k in cells.items() if k not in ("leaves", "sparse")}
                    rng = np.random.default_rng(91 + i)
                    for (x, y, z), k in sorted(cells.items()):
                        if k == "bark" and (x, y + 1, z) not in cells:
                            cells[(x, y, z)] = "log"
                        elif k in ("leaves", "sparse"):
                            touch = any((x + dx, y + dy, z + dz) in wood for dx, dy, dz in
                                        ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
                            if touch and rng.random() < 0.45:
                                cells[(x, y, z)] = "sparse"
                            else:
                                del cells[(x, y, z)]
                states = {c: state(sp, k) for c, k in cells.items()}
                templates.append((f"{sp}_{i + 1}_{size}", states))
                n_leaf = sum(1 for k in cells.values() if k in ("leaves", "sparse"))
                ys = [c[1] for c in cells]
                xs = [c[0] for c in cells]
                print(f"{sp}_{i + 1}_{size}: {len(cells)} blocks ({n_leaf} leaves), {max(ys) + 1} tall, "
                      f"{max(xs) - min(xs) + 1} wide", flush=True)
    for k in [p.name for p in TEX_OUT.glob("tree__*")]:
        (TEX_OUT / k).rename(TEX_OUT / k.replace("tree__", "tree_"))
    write_blocks(block_specs({k: v for k, v in SPECIES.items()}))
    size = write_templates(templates)
    print(f"{len(templates)} templates, {size // 1024} KB before gzip, {TEMPLATES.stat().st_size // 1024} KB in the jar")


if __name__ == "__main__":
    main()
