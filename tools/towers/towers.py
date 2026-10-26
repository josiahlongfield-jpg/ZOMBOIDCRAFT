#!/usr/bin/env python3
"""Turns the twenty city buildings of the buildings pack (TowersJJ.fbx, bought by Josiah) into
downtown buildings you can walk into and climb.

The pack never goes into the repo or the jar. This reads its FBX and textures and writes only what
the mod needs, all made from it:

  tools/towers/tex/pt_*.png       64px walls, roofs, metals, roller shutters and shop windows cut
                                  from the pack's photos at the size they cover on the buildings
                                  (tools/city/nature.py hands them to photo_textures.py)
  tools/city/blocks.json  pt_*    their blocks
  src/main/resources/data/zomboidcraft/packtowers.bin
                                  every building as a template: its shell block by block, which
                                  cells are inside, its floors, doors, lamps and stair cores
                                  (world/city/PackTower.java builds and fits it out)

Each building is scaled so a storey comes out 4 blocks (the city's slab and three of room) and
its ground floor 4 to 10, the slabs landing in the bands of wall between its rows of windows.
Its shape is what every view from the four sides and from above agrees is solid; each outside
cell takes the material most of the model's surface there shows, so a recessed window sits a
block back from its wall. Glass becomes the city's windows, doors become real doors, the spot
lamps over the shops become coloured lamps, and the shop windows show the pack's photos of
window displays (only the ones with no brand or character in them; the rest are swapped).

    python3 tools/towers/towers.py PACK_DIR [--only 8,11] [--preview DIR]

PACK_DIR holds TowersJJ.fbx and x/Textures/ (the pack's Textures.rar unpacked).
"""
import argparse
import gzip
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/props"))
import meshio  # noqa: E402

TEX_OUT = ROOT / "tools/towers/tex"
BLOCKS = ROOT / "tools/city/blocks.json"
TEMPLATES = ROOT / "src/main/resources/data/zomboidcraft/packtowers.bin"
N = 64
SUB = 3  # sub-cells per block across each view

# what each building is, for its fit-out; pitch overrides where the windows mislead the measure
BUILDINGS = {
    1: dict(kind="office"), 2: dict(kind="flats"), 3: dict(kind="flats"), 4: dict(kind="flats"),
    5: dict(kind="flats"), 6: dict(kind="office"), 7: dict(kind="flats", pitch=3.4),
    8: dict(kind="flats", pitch=3.4, mats={"WhiteShiny": ("glass", "window", 21.0), "DarkGrayMetal": ("glass", "window", 21.0)}),
    9: dict(kind="flats"), 10: dict(kind="shop", pitch=3.4), 11: dict(kind="office"), 12: dict(kind="shop", pitch=3.4),
    13: dict(kind="flats"), 14: dict(kind="flats"), 15: dict(kind="shop", pitch=3.4), 16: dict(kind="office", pitch=3.3),
    17: dict(kind="office"), 18: dict(kind="flats", pitch=3.6), 19: dict(kind="flats"), 20: dict(kind="shop", pitch=3.4),
}

# ------------------------------------------------------------------ materials
# role, block (or texture recipe key). Roles: wall, roof, glass, metal, shutter, door, shop, lamp, skip
MATS = {
    "GrayFacade": ("wall", "pt_plaster_grey"), "CreamFacade": ("wall", "pt_plaster_cream"),
    "BeigeFacade": ("wall", "pt_plaster_beige"), "BlackFacade": ("wall", "pt_tile_black"),
    "OrabgeFacade": ("wall", "pt_plaster_orange"), "WhiteFacade": ("wall", "pt_plaster_light"),
    "YellowFacade": ("wall", "pt_plaster_yellow"), "GreenFacade": ("wall", "pt_plaster_green"),
    "BlueFacade": ("wall", "pt_plaster_blue"), "RedFacade": ("wall", "pt_plaster_rose"),
    "Bricks": ("wall", "pt_brick"), "DirtyConcret": ("wall", "pt_concrete_dirty"),
    "Quarry": ("wall", "pt_stone_rough"), "StoneTexture": ("wall", "pt_stone_flags"),
    "JaspeFloor": ("wall", "pt_marble"), "BlackQuarry": ("wall", "pt_granite_black"),
    "Woodx": ("wall", "pt_wood"), "YellowWorn": ("wall", "pt_ochre_worn"),
    "WhiteWall": ("wall", "pt_plaster_white"), "OrangeWall": ("wall", "pt_plaster_terracotta"),
    "RedImpermeable": ("roof", "pt_roof_red"), "ImpermeabilizanteRojo": ("roof", "pt_roof_red"),
    "GrayImpermeable": ("roof", "pt_roof_grey"),
    "GlassReflec": ("glass", "window"), "Window02": ("glass", "window"), "WindowBuild": ("glass", "window"),
    "WindowBuild1": ("glass", "window"),
    "Aluminium": ("metal", "pt_alu"), "DarkGrayMetal": ("metal", "steel_mullion"),
    "BlackMetal": ("metal", "pt_iron"), "BlueMetal": ("metal", "pt_panel_blue"), "BluelMate": ("metal", "pt_panel_blue"),
    "WhiteShiny": ("metal", "pt_metal_white"), "ConductMetal": ("metal", "pt_duct"), "Grids": ("metal", "pt_vent"),
    "Gridsx": ("metal", "pt_mesh"), "BrownWood": ("metal", "pt_wood_dark"),
    "MetalCurtain": ("shutter", "pt_shutter_grey"), "BlueMetalCurtain": ("shutter", "pt_shutter_blue"),
    "GrayMetalCurtain": ("shutter", "pt_shutter_steel"), "YelowMetalCurtain": ("shutter", "pt_shutter_yellow"),
    "WhiteMetalCurtain": ("shutter", "pt_shutter_white"),
    "TextureDoor": ("door", "flat_door"), "TextureDoor1": ("door", "shop_door"), "StoreDoor": ("door", "shop_door"),
    "GreenDoor": ("door", "shop_door"),
    "WhiteFocus": ("lamp", None),
    "Gauge": ("skip", None), "GlassTransparent": ("skip", None), "Material #5491": ("skip", None),
    "Material #5492": ("skip", None), "Grassx": ("skip", None),
}
SHOP_PREFIX = ("Esca",)
# shop photos with a brand, a shop's own name or a character in them, and the clean photo each
# one's windows show instead (about the same shape)
SWAP_PHOTOS = {
    "Esca1": "Escaparate 3", "Esca2": "Escaparate11", "Esca4": "Escaparate 5", "Esca5": "Escaparate15",
    "Escaparate08": "Escaparate (1)", "Escaparate7": "Escaparate (2)", "Escaparate9": "EscaparateA",
    "Escaparatex0": "Escaparatex1", "Escaparatex3": "Escaparatex2", "Escaparatex4": "Escaparate6",
}
SHOP_ROWS = 3  # every shop photo is cut three blocks high; its width follows its shape

# texture recipes: (pack texture file or None, tint for colour-only materials, metres one block covers
# of the texture is worked out from the models; shine)
RECIPES = {
    "pt_plaster_grey": ("GreyFacade", None), "pt_plaster_cream": ("CreamFacade", None),
    "pt_plaster_beige": ("BeigeFacade", None), "pt_tile_black": ("BlackTile", None),
    "pt_plaster_orange": ("OrangeFacade", None), "pt_plaster_light": ("LightFacade", None),
    "pt_plaster_yellow": ("YellowFacade", None), "pt_plaster_green": ("GreenFacade", None),
    "pt_plaster_blue": ("BlueFacade", None), "pt_plaster_rose": ("RedFacade", None),
    "pt_brick": ("Bricksx", None), "pt_concrete_dirty": ("ConcretDirty", None),
    "pt_stone_rough": ("Quarry0", None), "pt_stone_flags": ("StoneTexture", None),
    "pt_marble": ("JaspeadoFloor", None), "pt_granite_black": ("BlackQuarryFloor", None),
    "pt_wood": ("WoodsTx", None), "pt_ochre_worn": ("YellowWorn", None),
    "pt_plaster_white": ("PlasticWall", (214, 212, 204)), "pt_plaster_terracotta": ("LightFacade", (178, 82, 56)),
    "pt_roof_red": ("RedImpermeablex", None), "pt_roof_grey": ("GreyImpermeablex", None),
    "pt_alu": ("MetalConducto", (150, 152, 152)), "pt_iron": ("MetalConducto", (34, 34, 36)),
    "pt_panel_blue": ("MetalConducto", (30, 58, 82)), "pt_metal_white": ("MetalConducto", (198, 198, 192)),
    "pt_duct": ("MetalConducto", None), "pt_vent": ("Grids", None), "pt_mesh": ("Gridx", None),
    "pt_wood_dark": ("WoodsTx", (52, 34, 22)),
    "pt_shutter_grey": ("MetalCurtain", None), "pt_shutter_blue": ("BlueMetalCurtain", None),
    "pt_shutter_steel": ("GreyMetalCurtain", None), "pt_shutter_yellow": ("YellowMetalCurtain", None),
    "pt_shutter_white": ("WhiteMetalCurtain", None),
}
# how big a piece of each texture one block shows, in texture widths, where the models don't say
DEFAULT_SPAN = 0.16
SPANS = {"pt_brick": 0.45}
# metals are drawn, not cut from a photo: colour and kind
METALS = {"pt_alu": ((150, 152, 150), "brushed"), "pt_iron": ((36, 35, 36), "iron"),
          "pt_panel_blue": ((34, 62, 86), "panel"), "pt_metal_white": ((196, 196, 188), "white"),
          "pt_duct": ((158, 160, 158), "duct")}
# where the clean slats are in each shutter photo
SHUTTER_AT = {"pt_shutter_steel": (0.5, 0.3), "pt_shutter_grey": (0.5, 0.4)}
TITLES = {"pt_alu": "Aluminium Frame", "pt_iron": "Black Iron", "pt_mesh": "Wire Mesh", "pt_vent": "Louvred Vent"}


def samples(tris, uvs, h):
    """Points about h apart over every triangle, with their uvs and the triangle's normal."""
    P, U, NR = [], [], []
    nrm = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    nrm = nrm / np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-12)
    e = np.max(np.stack([np.linalg.norm(tris[:, a] - tris[:, b], axis=1) for a, b in ((0, 1), (0, 2), (1, 2))]), 0)
    n = np.clip(np.ceil(e / h).astype(int), 1, 600)
    for k in np.unique(n):
        sel = np.nonzero(n == k)[0]
        i, j = np.meshgrid(np.arange(k + 1), np.arange(k + 1), indexing="ij")
        m = (i + j) <= k
        a, b = (i[m] + 1 / 3) / (k + 1), (j[m] + 1 / 3) / (k + 1)
        keep = a + b <= 1
        w = np.stack([1 - a[keep] - b[keep], a[keep], b[keep]], 1)
        P.append(np.einsum("sk,tkd->tsd", w, tris[sel]).reshape(-1, 3))
        U.append(np.einsum("sk,tkd->tsd", w, uvs[sel]).reshape(-1, 2))
        NR.append(np.repeat(nrm[sel], len(w), axis=0))
    return np.concatenate(P), np.concatenate(U), np.concatenate(NR)


def load(pack):
    parts = meshio.load_fbx(Path(pack) / "TowersJJ.fbx", materials=Path(pack) / "x/Textures")
    out = {}
    for p in parts:
        b, mat = p.name.split("/", 1)
        out.setdefault(int(b.replace("Estructure", "")), []).append((mat, p))
    return out


def role(mat, mats=MATS):
    if mat.startswith(SHOP_PREFIX):
        return "shop"
    return mats.get(mat, ("skip", None))[0]


# ------------------------------------------------------------------ measuring a building

def storeys(pts, nrm, mats, names, table, pitch=None):
    """The storey height and the height of the first band of wall above the ground floor's shop
    windows (the first upper slab), from where the windows are."""
    glass = np.isin(mats, [i for i, n in enumerate(names) if role(n, table) == "glass"])
    ground = np.isin(mats, [i for i, n in enumerate(names) if role(n, table) in ("shop", "door", "shutter")])
    low = ground & (pts[:, 1] < 7)  # a shutter run up a whole wall is no shop front
    band = float(np.percentile(pts[low, 1], 98)) if low.sum() > 50 else 3.4
    y = pts[glass & (np.abs(nrm[:, 1]) < 0.3), 1]
    y = y[y > band + 0.3]
    if pitch is None:
        if len(y) > 200 and y.max() - y.min() > 7:
            h = np.bincount((y / 0.05).astype(int)).astype(float)
            h -= h.mean()
            ac = np.correlate(h, h, "full")[len(h) - 1:]
            lags = np.arange(len(ac)) * 0.05
            sel = (lags >= 2.8) & (lags <= 4.4)
            pitch = float(lags[sel][np.argmax(ac[sel])])
        else:
            pitch = 3.4
    if len(y) > 50:
        # fold the windows into one storey: the slab goes in the middle of the wall between them
        bins = 40
        f = np.bincount(((y % pitch) / pitch * bins).astype(int) % bins, minlength=bins).astype(float)
        f = np.convolve(np.concatenate([f, f, f]), np.ones(5) / 5, "same")[bins:2 * bins]
        wall = np.argmin(f)
        c = (wall + 0.5) / bins * pitch
    else:
        c = band + 0.4
    c0 = c + math.ceil((band + 0.25 - c) / pitch) * pitch
    while c0 < band + 0.25:
        c0 += pitch
    return pitch, band, c0


def vertical(pitch, c0, s):
    """The heights' map into rows: the ground floor's c0 metres into G rows over the slab at row 0,
    each storey above into 4. G is even (the stairs climb in pairs) and 4 to 10."""
    G = int(max(4, min(10, 2 * round((c0 * s + 0.5) / 2))))

    def f(y):
        y = np.asarray(y, np.float64)
        lo = 1 + y * (G - 0.5) / c0
        hi = G + 0.5 + (y - c0) * 4 / pitch
        return np.where(y < c0, lo, hi)
    return G, f


def front_turn(pts, nrm, mats, names, band, table):
    """Quarter turns about y that bring the side with the shop windows and doors to face north (-z)."""
    keys = [i for i, n in enumerate(names) if role(n, table) in ("shop", "door", "shutter")]
    sel = np.isin(mats, keys) & (pts[:, 1] < band + 0.5)
    if sel.sum() < 30:
        sel = np.isin(mats, [i for i, n in enumerate(names) if role(n, table) == "glass"]) & (pts[:, 1] < band + 0.5)
    score = {}
    for k, d in enumerate([(0, -1), (1, 0), (0, 1), (-1, 0)]):  # north, east, south, west
        score[k] = int(((nrm[sel, 0] * d[0] + nrm[sel, 2] * d[1]) > 0.8).sum())
    # each quarter turn brings the side one step clockwise of north round to north
    return max(score, key=score.get)


def turn(a, q):
    """(x, z) of points or normals turned q quarters: east to north each quarter."""
    a = a.copy()
    for _ in range(q):
        x, z = a[:, 0].copy(), a[:, 2].copy()
        a[:, 0], a[:, 2] = z, -x
    return a


# ------------------------------------------------------------------ voxels

def hull(V, dims):
    """Solid cells: inside what the views from west, east, north, south and above all see."""
    W, H, D = dims
    k = SUB
    sx, sy, sz = (np.floor(V * k).astype(np.int64)[:, i] for i in range(3))
    ok = (sx >= 0) & (sy >= 0) & (sz >= 0) & (sx < W * k) & (sy < H * k) & (sz < D * k)
    sx, sy, sz, V = sx[ok], sy[ok], sz[ok], V[ok]

    def view(a, b, depth, shape, far):
        img = np.full(shape, np.inf if not far else -np.inf)
        (np.maximum if far else np.minimum).at(img, (a, b), depth)
        img[np.isinf(img)] = np.nan
        # per block: the median of its sub-cells that saw something, if most did
        r = img.reshape(shape[0] // k, k, shape[1] // k, k).transpose(0, 2, 1, 3).reshape(shape[0] // k, shape[1] // k, -1)
        cnt = np.sum(~np.isnan(r), -1)
        with np.errstate(all="ignore"):
            med = np.nanmedian(r, -1)
        med[cnt < (k * k + 1) // 2] = np.nan
        return med

    wv = view(sy, sz, V[:, 0], (H * k, D * k), False)
    ev = view(sy, sz, V[:, 0], (H * k, D * k), True)
    nv = view(sy, sx, V[:, 2], (H * k, W * k), False)
    sv = view(sy, sx, V[:, 2], (H * k, W * k), True)
    tv = view(sz, sx, V[:, 1], (D * k, W * k), True)
    cx = np.arange(W)[None, None, :] + 0.5
    cy = np.arange(H)[:, None, None] + 0.5
    cz = np.arange(D)[None, :, None] + 0.5
    with np.errstate(invalid="ignore"):
        solid = (cx >= wv[:, :, None]) & (cx <= ev[:, :, None])
        solid &= (cz >= nv[:, None, :]) & (cz <= sv[:, None, :])
        solid &= cy <= tv[None, :, :]
    return solid  # [y, z, x]


NB = [(0, 0, 1), (0, 0, -1), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0)]  # (dy, dz, dx): east west up down south north
FACE = ["east", "west", "up", "down", "south", "north"]


def exposed(solid):
    """For each cell, which of its six faces look onto outside air: [6, y, z, x]."""
    H, D, W = solid.shape
    pad = np.pad(solid, 1, constant_values=False)
    pad[0] = True  # below the ground is never outside
    out = []
    for dy, dz, dx in NB:
        nbr = pad[1 + dy:1 + dy + H, 1 + dz:1 + dz + D, 1 + dx:1 + dx + W]
        out.append(solid & ~nbr)
    return np.stack(out)


def materials(V, nrm, mats, uv, solid, ex, nmat):
    """The material each outside cell shows, from the samples just inside the surface over it that
    face the way the cell's open faces do (so a window's reveals don't paint the window), and the
    mean uv of what it shows (for the shop photos)."""
    H, D, W = solid.shape
    inward = V - nrm * 0.5
    ix, iy, iz = (np.floor(inward[:, i]).astype(np.int64) for i in range(3))
    ok = (ix >= 0) & (iy >= 0) & (iz >= 0) & (ix < W) & (iy < H) & (iz < D)
    # which of the six faces each sample looks out of (NB order), -1 if none clearly
    axes = np.stack([nrm[:, 0], -nrm[:, 0], nrm[:, 1], -nrm[:, 1], nrm[:, 2], -nrm[:, 2]], 1)
    d = np.argmax(axes, 1)
    d[np.max(axes, 1) < 0.7] = 6
    cell = (iy * D + iz) * W + ix
    cell, m, u, d = cell[ok], mats[ok], uv[ok], d[ok]
    key = (cell * 7 + d) * nmat + m
    uk, inv, cnt = np.unique(key, return_inverse=True, return_counts=True)
    cd, m_of = uk // nmat, uk % nmat
    c_of, d_of = cd // 7, cd % 7
    su = np.zeros((len(uk), 2))
    np.add.at(su, inv, u)
    muv = su / cnt[:, None]
    # how much each (cell, material) shows on the cell's open faces, horizontal faces counting most
    exf = ex.reshape(6, -1)
    open_face = np.zeros(len(uk), bool)
    hz = d_of < 6
    open_face[hz] = exf[d_of[hz], c_of[hz]]
    weight = cnt * np.where(open_face, np.where(np.isin(d_of, [2, 3]), 1.0, 1.5), 0.02)
    order = np.lexsort((-weight, c_of))
    first = np.ones(len(order), bool)
    first[1:] = c_of[order][1:] != c_of[order][:-1]
    best = np.full(H * D * W, -1, np.int64)
    pick = order[first]
    best[c_of[pick]] = m_of[pick]
    uvmap = {int(c): muv[j] for c, j in zip(c_of[pick], pick)}
    return best.reshape(H, D, W), uvmap


def spread(best, boundary):
    """Boundary cells that no sample reached take a neighbour's material."""
    best = best.copy()
    for _ in range(12):
        miss = boundary & (best < 0)
        if not miss.any():
            break
        for ax in range(3):
            for sh in (1, -1):
                nb = np.roll(best, sh, ax)
                take = miss & (nb >= 0) & (best < 0)
                best[take] = nb[take]
    return best


# ------------------------------------------------------------------ the template

def convert(num, parts):
    """One building voxelised: its solid cells, which faces show, and each shell cell's material."""
    cfg = BUILDINGS[num]
    table = {**MATS, **cfg.get("mats", {})}
    names = sorted({m for m, _ in parts})
    idx = {n: i for i, n in enumerate(names)}
    stems = {m: p.tex for m, p in parts}
    P, U, NR, M = [], [], [], []
    for m, p in parts:
        pts, uv, n = samples(p.tris / 100.0, p.uvs, 0.11)
        P.append(pts)
        U.append(uv)
        NR.append(n)
        M.append(np.full(len(pts), idx[m]))
    P, U, NR, M = np.concatenate(P), np.concatenate(U), np.concatenate(NR), np.concatenate(M)
    P -= [P[:, 0].min(), 0, P[:, 2].min()]
    # an override that stops at a height leaves the material as it was above it
    for n, spec in list(table.items()):
        if len(spec) > 2 and n in idx:
            hi = f"{n}@hi"
            names.append(hi)
            idx[hi] = len(names) - 1
            stems[hi] = stems[n]
            table[hi] = MATS[n]
            M[(M == idx[n]) & (P[:, 1] > spec[2])] = idx[hi]
    pitch, band, c0 = storeys(P, NR, M, names, table, cfg.get("pitch"))
    s = 4 / pitch
    G, vmap = vertical(pitch, c0, s)
    q = front_turn(P, NR, M, names, band, table)
    P, NR = turn(P, q), turn(NR, q)
    P -= [P[:, 0].min(), 0, P[:, 2].min()]
    V = np.stack([P[:, 0] * s + 0.25, vmap(P[:, 1]), P[:, 2] * s + 0.25], 1)
    W, H, D = int(V[:, 0].max()) + 2, int(V[:, 1].max()) + 2, int(V[:, 2].max()) + 2
    solid = hull(V, (W, H, D))
    solid[0] = solid[1]  # the slab and plinth under the ground floor
    ex = exposed(solid)
    boundary = ex.any(0)
    roles = np.array([role(n, table) for n in names])
    vote = ~np.isin(roles[M], ["lamp", "skip"])
    best, uvmap = materials(V[vote], NR[vote], M[vote], U[vote], solid, ex, len(names))
    best = spread(best, boundary)
    lit = roles[M] == "lamp"
    lamps = np.floor(V[lit] + NR[lit] * 0.6).astype(np.int64)
    print(f"Estructure{num}: pitch {pitch:.2f} m, scale {s:.2f}, ground band {band:.1f} m -> G {G}, "
          f"turned {q}, {W}x{H}x{D}, {int(solid.sum())} solid, {int(boundary.sum())} shell", flush=True)
    return dict(num=num, kind=cfg["kind"], names=names, stems=stems, roles=roles, table=table, solid=solid, ex=ex,
                boundary=boundary, best=best, uvmap=uvmap, G=G, s=s, pitch=pitch, dims=(W, H, D), lamps=lamps)


# ------------------------------------------------------------------ cells to blocks

def photo_of(stem):
    """The clean photo a shop window shows, and its short id."""
    stem = SWAP_PHOTOS.get(stem, stem)
    key = stem.replace("Escaparate", "").replace("Esca", "s").replace("(", "").replace(")", "").replace(" ", "").lower()
    return stem, ("e" + key.lstrip("0") if key[:1].isdigit() else key)


OUTWARD = {0: "east", 1: "west", 4: "south", 5: "north"}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def outward(ex, y, z, x):
    """The horizontal side a shell cell shows most, as a direction (None if only up or down)."""
    for i in (5, 4, 0, 1):
        if ex[i, y, z, x]:
            return OUTWARD[i]
    return None


def components(mask):
    """Labels of the 6-connected parts of a 3D mask (0 outside it)."""
    lab = np.where(mask, np.arange(mask.size).reshape(mask.shape) + 1, 0)
    big = mask.size + 1
    while True:
        cur = np.where(mask, lab, big)
        m = cur.copy()
        for ax in range(3):
            for sh in (1, -1):
                r = np.roll(cur, sh, ax)
                # no wrapping round the edges
                sl = [slice(None)] * 3
                sl[ax] = slice(0, 1) if sh == 1 else slice(-1, None)
                r[tuple(sl)] = big
                m = np.minimum(m, r)
        m = np.where(mask, m, 0)
        if np.array_equal(m, lab):
            return lab
        lab = m


def close_windows(best, shell, ex, roles):
    """A bit of wall left between two panes of the same window, side by side or one above the
    other on the same face, becomes glass too."""
    best = best.copy()
    glass_m = np.nonzero(roles == "glass")[0]
    for _ in range(2):
        glass = shell & np.isin(best, glass_m)
        for d, along in ((5, 2), (4, 2), (0, 1), (1, 1)):
            face = shell & ex[d]
            g = glass & face
            fill = np.zeros_like(g)
            for ax in (along, 0):
                a = np.roll(g, 1, ax)
                b = np.roll(g, -1, ax)
                fill |= a & b
            fill &= face & ~glass
            if fill.any():
                src = np.roll(np.where(g, best, -1), 1, along)
                src = np.where(src >= 0, src, np.roll(np.where(g, best, -1), 1, 0))
                take = fill & (src >= 0)
                best[take] = src[take]
    return best


def assemble(t, photos):
    """The template's cells: 0 outside, 1 inside, else a palette entry (a block, or a marker the
    Java side builds: @door, @doortop, @lamp, @slab, @ground)."""
    solid, ex, boundary, best, roles, names = t["solid"], t["ex"], t["boundary"], t["best"], t["roles"], t["names"]
    H, D, W = solid.shape
    G = t["G"]
    pal, pidx = [], {}

    def code(block):
        if block not in pidx:
            pidx[block] = len(pal) + 2
            pal.append(block)
        return pidx[block]

    cells = np.zeros(solid.shape, np.int32)
    inside = solid & ~boundary
    # pockets too small to be rooms (inside roof plant and thick parapets) are filled
    lab = components(inside)
    ids, cnt = np.unique(lab[lab > 0], return_counts=True)
    small = np.isin(lab, ids[cnt < 40])
    inside &= ~small
    shell = solid & ~inside
    best = spread(np.where(shell & (best < 0), -1, best), shell)
    cells[inside] = 1
    best = close_windows(best, shell, ex, roles)
    # the building's own wall for its plinth and anything that comes out odd
    rb = roles[np.clip(best, 0, None)]
    walls = best[boundary & (rb == "wall") & (np.arange(H)[:, None, None] >= 1) & (np.arange(H)[:, None, None] < G)]
    walls = walls if len(walls) else best[boundary & (rb == "wall")]
    base = names[np.bincount(walls).argmax()] if len(walls) else None
    base_block = t["table"][base][1] if base else "concrete_dark"
    uvmap = t["uvmap"]
    for y, z, x in zip(*np.nonzero(shell)):
        m = best[y, z, x]
        r = roles[m] if m >= 0 else "wall"
        n = names[m] if m >= 0 else None
        face = outward(ex, y, z, x)
        if y == 0 or m < 0 or r in ("lamp", "skip"):
            block = base_block
        elif r == "glass" or (r == "shop" and face is None):
            ax = "x" if face in ("north", "south", None) else "z"
            block = f"window[axis={ax}]"
        elif r == "shop":
            stem, key = photo_of(t["stems"][n])
            g = photos.setdefault(key, dict(stem=stem, cells=[]))
            u, v = uvmap.get((y * D + z) * W + x, (0.5, 0.5))
            g["cells"].append((t["num"], y, z, x, face, float(u), float(v)))
            block = ("@shop", key, face)  # tiled once every building has had its say
        elif r == "door":
            block = ("@door", t["table"][n][1], face)
        else:
            block = t["table"][n][1]
        cells[y, z, x] = -1
        t.setdefault("pending", []).append((y, z, x, block))
    t["cells"], t["code"], t["pal"], t["base"] = cells, code, pal, base_block
    return t


def doors(t):
    """Door cells: the bottom one of each door column at street level becomes a door, the next its
    top half, any above a fanlight; doors anywhere else are glass. Pairs hinge on their outer edges."""
    pend = {(y, z, x): b for y, z, x, b in t["pending"]}
    cols = {}
    for (y, z, x), b in pend.items():
        if isinstance(b, tuple) and b[0] == "@door":
            cols.setdefault((z, x), []).append(y)
    for (z, x), ys in cols.items():
        ys.sort()
        _, kind, face = pend[(ys[0], z, x)]
        for y in ys:
            ax = "x" if face in ("north", "south", None) else "z"
            if ys[0] == 1 and face is not None and y == 1:
                pend[(y, z, x)] = ("@door", kind, face)
            elif ys[0] == 1 and face is not None and y == 2:
                pend[(y, z, x)] = "@doortop"
            else:
                pend[(y, z, x)] = f"window[axis={ax}]"
    t["pending"] = [(y, z, x, b) for (y, z, x), b in pend.items()]


def ensure_door(t):
    """A building with no door at the front gets a pair of glass doors in the middle of it, and
    a gap knocked through any yard wall standing in front of them."""
    if any(isinstance(b, tuple) and b[0] == "@door" and b[2] == "north" for *_, b in t["pending"]):
        return
    cells, ex = t["cells"], t["ex"]
    H, D, W = cells.shape

    def front(xx):
        # the first wall with rooms right behind it, looking in from the street
        for z in range(D - 1):
            if cells[1, z, xx] not in (0, 1) and cells[1, z + 1, xx] == 1 and ex[5, 1, z, xx] and cells[2, z, xx] != 0:
                return z
        return -1

    best = None
    for x in range(1, W - 2):
        z0, z1 = front(x), front(x + 1)
        if z0 >= 0 and z0 == z1 and (best is None or (z0, abs(x + 0.5 - W / 2)) < (best[1], abs(best[0] + 0.5 - W / 2))):
            best = (x, z0)
    if best is None:
        print(f"  Estructure{t['num']}: no room for a front door", flush=True)
        return
    x, z = best
    pend = {(y, zz, xx): b for y, zz, xx, b in t["pending"]}
    for xx in (x, x + 1):
        pend[(1, z, xx)] = ("@door", "shop_door", "north")
        pend[(2, z, xx)] = "@doortop"
        for zz in range(z):
            for y in range(1, 4):
                if cells[y, zz, xx] != 0:
                    cells[y, zz, xx] = 0
                    pend.pop((y, zz, xx), None)
    t["pending"] = [(y, zz, xx, b) for (y, zz, xx), b in pend.items()]


def finish(t, tiles):
    """Every pending shell cell to its palette entry; markers for lamps, slabs and the ground floor."""
    cells, code = t["cells"], t["code"]
    H, D, W = cells.shape
    G = t["G"]
    pend = {(y, z, x): b for y, z, x, b in t["pending"]}
    # door pairs: two side by side hinge on their outer edges
    for (y, z, x), b in list(pend.items()):
        if isinstance(b, tuple) and b[0] == "@door":
            _, kind, face = b
            along = (1, 0) if face in ("north", "south") else (0, 1)
            nxt = pend.get((y, z + along[1], x + along[0]))
            prv = pend.get((y, z - along[1], x - along[0]))
            pair_next = isinstance(nxt, tuple) and nxt[0] == "@door"
            pair_prev = isinstance(prv, tuple) and prv[0] == "@door"
            facing = OPPOSITE[face]
            # Doors.pair: facing south, the west door hinges right and the east one left
            west_first = facing in ("south", "west")
            hinge = "?" if pair_next == pair_prev else ("right" if pair_next == west_first else "left")
            pend[(y, z, x)] = f"@door:{kind}:{facing}:{hinge}"
        elif isinstance(b, tuple) and b[0] == "@shop":
            pend[(y, z, x)] = tiles.get((t["num"], y, z, x)) or f"window[axis={'x' if b[2] in ('north', 'south') else 'z'}]"
    for (y, z, x), b in pend.items():
        cells[y, z, x] = code(b)
    # lamps just outside the shell, above the doors
    for x, y, z in t["lamps"]:
        if 3 <= y < H and 0 <= z < D and 0 <= x < W and cells[y, z, x] == 0:
            near = cells[y, max(0, z - 1):z + 2, max(0, x - 1):x + 2]
            if (near >= 2).any():
                cells[y, z, x] = code("@lamp")
    # one lamp per run of them
    lamp = code("@lamp")
    for y, z, x in zip(*np.nonzero(cells == lamp)):
        for dz, dx in ((0, 1), (1, 0), (0, 2), (2, 0)):
            if z + dz < D and x + dx < W and cells[y, z + dz, x + dx] == lamp:
                cells[y, z + dz, x + dx] = 0
    # set into the wall behind, flush, rather than a box stuck on the face
    pal = t["pal"]

    def plain_wall(v):
        e = pal[v - 2] if v >= 2 else ""
        return e.startswith("pt_") and not e.startswith("pt_shop_") and not e.startswith(("pt_glass", "pt_mesh"))

    for y, z, x in zip(*np.nonzero(cells == lamp)):
        for dz, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            zz, xx = z + dz, x + dx
            if 0 <= zz < D and 0 <= xx < W and plain_wall(cells[y, zz, xx]):
                cells[y, zz, xx] = lamp
                cells[y, z, x] = 0
                break
    # a red light on top of every mast
    solid = cells != 0
    top = np.where(solid.any(0), H - 1 - np.argmax(solid[::-1], 0), -1)
    for z in range(D):
        for x in range(W):
            h = top[z, x]
            around = top[max(0, z - 3):z + 4, max(0, x - 3):x + 4]
            if h > 0 and h == around.max() and h - np.median(around) >= 6 and h + 1 < H:
                cells[h + 1, z, x] = code("aviation_light")
    inside = cells == 1
    cells[0][inside[0]] = code("@ground")
    levels = [0]
    y = G
    while y < H - 3:
        room = inside[y + 1] & inside[y + 2] & inside[y + 3]
        slab = inside[y] & room
        if slab.sum() >= 12:
            cells[y][slab] = code("@slab")
            levels.append(y)
        y += 4
    t["levels"] = levels


def tile_photos(photos, pack_tex):
    """Cuts each shop photo into blocks, three high and as wide as its shape (at most five), and
    lays it along each shop window from the viewer's left, repeating along a wide one; rows of a
    tall window above the photo are plain glass. Returns {(building, y, z, x): block} and
    {block: image}."""
    tiles, tex = {}, {}
    for key, g in sorted(photos.items()):
        img = Image.open(pack_tex / f"{g['stem']}.png").convert("RGB")
        gh = SHOP_ROWS
        gw = int(np.clip(round(img.width / img.height * gh), 2, 5))
        a = np.asarray(img.resize((gw * N, gh * N), Image.LANCZOS), np.float32)
        # each window: the cells of one photo on one wall, in runs along it
        walls = {}
        for num, y, z, x, face, u, v in g["cells"]:
            along, depth = (x, z) if face in ("north", "south") else (z, x)
            walls.setdefault((num, face, depth), []).append((along, y, z, x))
        for (num, face, depth), cs in walls.items():
            cs.sort()
            runs, cur = [], [cs[0]]
            for c in cs[1:]:
                if c[0] - cur[-1][0] > 1:
                    runs.append(cur)
                    cur = []
                cur.append(c)
            runs.append(cur)
            for run in runs:
                amin, amax = min(c[0] for c in run), max(c[0] for c in run)
                ymin = min(c[1] for c in run)
                for along, y, z, x in run:
                    j = gh - 1 - (y - ymin)
                    if j < 0:
                        tiles[(num, y, z, x)] = f"window[axis={'x' if face in ('north', 'south') else 'z'}]"
                        continue
                    # from the viewer's left: east on a north wall, west on a south one, north on a
                    # west one, south on an east one
                    i = (amax - along if face in ("north", "east") else along - amin) % gw
                    name = f"pt_shop_{key}_{i}_{j}"
                    tiles[(num, y, z, x)] = f"{name}[facing={face}]"
                    if name not in tex:
                        tex[name] = a[j * N:(j + 1) * N, i * N:(i + 1) * N]
    return tiles, tex


# ------------------------------------------------------------------ floors and stair cores

def biggest_rect(F):
    """The largest axis-aligned rectangle of True in a 2D [z, x] mask, as (x0, z0, x1, z1) inclusive."""
    D, W = F.shape
    h = np.zeros(W, int)
    best, area = None, 0
    for z in range(D):
        h = np.where(F[z], h + 1, 0)
        stack = []
        for x in range(W + 1):
            cur = h[x] if x < W else 0
            start = x
            while stack and stack[-1][1] >= cur:
                start, hh = stack.pop()
                a = hh * (x - start)
                if a > area:
                    area, best = a, (start, z - hh + 1, x - 1, z)
            stack.append((start, cur))
    return best


CORES = {"single": (5, 10), "double": (14, 10)}


def plates(t):
    """Each floor's biggest clear rectangle, and the stair cores that climb through them: each core
    serves a run of floors from where the one below it stopped."""
    cells, code = t["cells"], t["code"]
    H, D, W = cells.shape
    floor_codes = [code("@ground"), code("@slab")]
    masks, rects = [], []
    for y in t["levels"]:
        F = np.isin(cells[y], floor_codes)
        for r in (1, 2, 3):
            if y + r < H:
                F &= cells[y + r] == 1
        masks.append(F)
        rects.append(biggest_rect(F))
    t["rects"] = rects
    kind = t["kind"]
    big = [r for r in rects if r and (r[2] - r[0] + 1) >= 26 and (r[3] - r[1] + 1) >= 20]
    ctype = "double" if kind == "office" and len(big) >= 0.7 * len(rects) and len(rects) >= 5 else "single"
    cores, start = [], 0
    while start < len(rects) - 1:
        cw, cd = CORES[ctype if not cores else "single"]
        r0 = rects[start]
        if r0 is None:
            break
        best = None
        for z0 in range(2, D - cd):
            for x0 in range(0, W - cw):
                n = 0
                for k in range(start, len(masks)):
                    m = masks[k]
                    if m[z0:z0 + cd, x0:x0 + cw].all() and m[z0 - 2:z0, x0:x0 + cw].all():
                        n += 1
                    else:
                        break
                if n < 2:
                    continue
                cx, cz = (r0[0] + r0[2]) / 2, (r0[1] + r0[3]) / 2
                # central, the stair door looking toward the front, a little back of the middle
                score = n * 1000 - abs(x0 + cw / 2 - cx) - abs(z0 + cd / 2 - (cz + 2)) * 0.7
                if best is None or score > best[0]:
                    best = (score, x0, z0, n)
        if best is None:
            break
        _, x0, z0, n = best
        cores.append(dict(type=ctype if not cores else "single", x=x0, z=z0, first=start, last=start + n - 1))
        start = start + n - 1
        if n < 2:
            break
    t["cores"] = cores
    served = cores[-1]["last"] if cores else 0
    print(f"  levels {t['levels']} cores {[(c['type'], c['x'], c['z'], c['first'], c['last']) for c in cores]}"
          f" ({served + 1} of {len(rects)} floors reached)", flush=True)


# ------------------------------------------------------------------ textures and blocks

def texel_spans(data, scale):
    """For each recipe: how much of its texture one block covers, from the models' uvs (texture
    repeats per metre times the metres a block stands for)."""
    acc = {}
    for parts in data.values():
        for m, p in parts:
            if m not in MATS or MATS[m][1] not in RECIPES or p.image is None:
                continue
            T = p.tris / 100.0
            a = np.linalg.norm(np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0]), axis=1) / 2
            e1, e2 = p.uvs[:, 1] - p.uvs[:, 0], p.uvs[:, 2] - p.uvs[:, 0]
            ua = np.abs(e1[:, 0] * e2[:, 1] - e1[:, 1] * e2[:, 0]) / 2
            ok = a > 0.05
            if ok.any():
                acc.setdefault(MATS[m][1], []).append((np.sqrt(ua[ok] / a[ok]), a[ok]))
    spans = {}
    for name, lst in acc.items():
        d = np.concatenate([x for x, _ in lst])
        w = np.concatenate([y for _, y in lst])
        o = np.argsort(d)
        med = d[o][np.searchsorted(np.cumsum(w[o]), w.sum() / 2)]
        spans[name] = float(med) / scale
    return spans


def seamless(a):
    s = a.shape[0]
    b = np.roll(np.roll(a, s // 2, 0), s // 2, 1)
    tent = 1 - np.abs(np.arange(s) - (s - 1) / 2) / (s / 2)
    tent = np.clip(tent * 3.0 - 0.2, 0, 1)
    m = np.outer(tent, tent)[..., None]
    return a * m + b * (1 - m)


def piece(img, span, seed, centre=None, grain=1.0):
    """A block's worth of a photo texture (span of its width), wrapped so it tiles."""
    H, W = img.shape[:2]
    rng = np.random.default_rng(seed)
    if span >= 1:
        reps = int(math.ceil(span))
        big = np.tile(img, (reps, reps, 1))
        size = int(min(W, H) * span)
        a = big[:size, :size]
    else:
        size = max(8, int(min(W, H) * span))
        if centre is None:
            x0, y0 = rng.integers(0, W - size + 1), rng.integers(0, H - size + 1)
        else:
            x0, y0 = int(centre[0] * W - size / 2), int(centre[1] * H - size / 2)
            x0, y0 = int(np.clip(x0, 0, W - size)), int(np.clip(y0, 0, H - size))
        a = img[y0:y0 + size, x0:x0 + size]
    a = np.asarray(Image.fromarray(a.astype(np.uint8)).resize((N, N), Image.LANCZOS), np.float32)
    return seamless(flatten(a, grain=grain))


def flatten(a, radius=10, grain=1.0):
    """Takes out a texture's big light and dark patches so it repeats block after block without a
    grid showing; the fine grain stays (scaled by grain)."""
    blur = np.asarray(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius)),
                      np.float32)
    return np.clip((a - blur) * grain + a.reshape(-1, 3).mean(0), 0, 255)


def metal(seed, colour, kind):
    """Painted and bare metals, drawn: brushed grain, a seam, rust where the paint went."""
    rng = np.random.default_rng(seed)
    a = np.ones((N, N, 3), np.float32) * np.array(colour, np.float32)
    grain = rng.normal(0, 1, (N, 1)) * 0.5 + rng.normal(0, 1, (N, N)) * 0.35
    grain = np.asarray(Image.fromarray(((grain + 3) * 40).clip(0, 255).astype(np.uint8)).filter(
        ImageFilter.BoxBlur(1)), np.float32) / 40 - 3
    a *= (1 + 0.035 * grain)[..., None]
    if kind == "panel":
        a[:, :2] *= 0.72  # the seam between panels
        a[:, 2] *= 1.12
    if kind == "duct":
        for y in (0, 31, 32):
            a[y] *= 0.8 if y != 32 else 1.1
    if kind in ("panel", "white", "iron"):
        rust = rng.random((N // 4, N // 4))
        rust = np.asarray(Image.fromarray((rust * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC), np.float32) / 255
        m = np.clip((rust - 0.72) * 5, 0, 1)[..., None] * (0.7 if kind == "white" else 0.5)
        a = a * (1 - m) + np.array([92, 52, 30], np.float32) * m
    return np.clip(a, 0, 255)


def weather(a, seed, amount=1.0):
    """Grime for a city that was left out in the rain: duller, darker toward the foot of the block,
    streaks running down and the odd stain."""
    rng = np.random.default_rng(seed)
    lum = a @ np.array([0.299, 0.587, 0.114], np.float32)
    a = lum[..., None] + (a - lum[..., None]) * 0.82  # a little less colour
    a = a * 0.9
    streak = np.zeros(N)
    for _ in range(4):
        c = rng.integers(0, N)
        streak += np.exp(-((np.arange(N) - c) ** 2) / (2 * rng.uniform(1, 3) ** 2)) * rng.uniform(0.05, 0.14)
    down = np.linspace(0.0, 1.0, N)[:, None] ** 2
    dark = 1 - amount * (streak[None, :] * (0.4 + 0.6 * down) + 0.05 * down)
    blot = rng.random((N // 8, N // 8))
    blot = np.asarray(Image.fromarray((blot * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC), np.float32) / 255
    dark *= 1 - amount * 0.06 * np.clip(blot - 0.55, 0, 1) * 4
    return np.clip(a * dark[..., None], 0, 255)


def tint(a, colour):
    lum = a @ np.array([0.299, 0.587, 0.114], np.float32)
    rel = lum / max(float(lum.mean()), 1.0)
    rel = 1 + (rel - 1) * 0.6
    return np.clip(rel[..., None] * np.array(colour, np.float32), 0, 255)


def save(name, rgb, alpha=None):
    TEX_OUT.mkdir(parents=True, exist_ok=True)
    a = np.full(rgb.shape[:2], 255, np.float32) if alpha is None else alpha
    Image.fromarray(np.dstack([np.clip(rgb, 0, 255), a]).astype(np.uint8), "RGBA").save(TEX_OUT / f"{name}.png")


def make_textures(pack_tex, spans, shop_tex):
    """Every pt_ texture: the walls and roofs at their size on the buildings, weathered; metals drawn,
    the duct photo, tinted; shutters cut from their slats; the shop windows' tiles."""
    for old in TEX_OUT.glob("pt_*.png"):
        old.unlink()
    for k, (name, (src, colour)) in enumerate(sorted(RECIPES.items())):
        img = np.asarray(Image.open(pack_tex / f"{src}.png").convert("RGBA"), np.float32)
        rgb, alpha = img[..., :3], None
        if name in METALS:
            save(name, weather(metal(k, *METALS[name]), k, 0.5))
            continue
        if name.startswith("pt_shutter_"):
            # the middle of the shutter, where the slats run: about ten to a block
            a = piece(rgb, 0.24, k, centre=SHUTTER_AT.get(name, (0.5, 0.55)))
        elif name == "pt_mesh":
            a = piece(rgb, 0.5, k)
            al = piece(img[..., 3:].repeat(3, -1), 0.5, k)[..., 0]
            alpha = np.where(al > 128, 255, 0).astype(np.float32)
            a = np.clip(a * 0.6, 0, 255)
        elif name == "pt_vent":
            a = np.asarray(Image.fromarray(rgb.astype(np.uint8)).resize((N, N), Image.LANCZOS), np.float32)
        else:
            span = SPANS.get(name, spans.get(name, DEFAULT_SPAN)) if colour is None else 0.2
            a = piece(rgb, max(0.04, span), k, grain=0.55 if "plaster" in name else 0.85)
        if colour is not None:
            a = tint(a, colour)
        # streaks repeat block after block into stripes, so walls only dull a little (the Decay
        # pass and the city's grime blocks do the real damage); shutters and roofs take more
        a = weather(a, k, 0.15 if name.startswith(("pt_plaster", "pt_tile", "pt_brick", "pt_stone", "pt_marble",
                                                   "pt_granite", "pt_concrete")) else 0.7)
        save(name, a, alpha)
    # the back of a shop window's display, seen from inside the shop
    save("pt_shop_back", weather(np.full((N, N, 3), 38.0) + np.random.default_rng(5).normal(0, 4, (N, N, 1)), 99, 0.6))
    for name, a in shop_tex.items():
        # behind the glass, at night: a little darker and colder than the photo
        a = a * np.array([0.86, 0.88, 0.95], np.float32) * 0.85
        save(name, a)


def title_of(name):
    if name in TITLES:
        return TITLES[name]
    if name.startswith("pt_shop_"):
        return "Shop Window Display"
    words = name[3:].split("_")
    return " ".join(w.capitalize() for w in reversed(words)) if words[0] in ("plaster", "roof", "shutter", "panel", "metal") \
        else " ".join(w.capitalize() for w in words)


def block_specs(shop_tiles):
    specs = {}
    for name in RECIPES:
        spec = {"tex": name, "title": title_of(name)}
        if name.startswith("pt_roof_"):
            spec["tex"] = {"up": name, "down": "ceiling_tile", "side": "concrete_dark"}
        if name.startswith(("pt_shutter_", "pt_alu", "pt_iron", "pt_panel", "pt_metal", "pt_duct", "pt_vent", "pt_mesh")):
            spec["sound"] = "METAL"
        specs[name] = spec
    for name in sorted(shop_tiles):
        specs[name] = {"tex": {"north": name, "side": "pt_shop_back", "up": "pt_shop_back", "down": "pt_shop_back"},
                       "rotate": "facing", "title": "Shop Window Display", "sound": "GLASS"}
    return specs


def write_blocks(specs):
    data = json.loads(BLOCKS.read_text())
    blocks = {k: v for k, v in data["blocks"].items() if not k.startswith("pt_")}
    blocks.update(specs)
    data["blocks"] = blocks
    BLOCKS.write_text(json.dumps(data, indent=1) + "\n")


# ------------------------------------------------------------------ the templates file

def write_templates(ts):
    """packtowers.bin (gzip), big-endian: int count, then per building: UTF name, UTF kind, short w,
    h, d, short ground-floor height, short level count and each level's y and biggest clear
    rectangle (x0, z0, x1, z1), short core count and each core's type (0 single, 1 double), x, z,
    first and last level, short palette size and its UTF entries, then the cells y by z by x as
    runs: short length, short value (0 outside, 1 inside, n >= 2 palette entry n - 2)."""
    buf = bytearray()

    def utf(s):
        b = s.encode("utf-8")
        buf.extend(struct.pack(">H", len(b)))
        buf.extend(b)
    buf.extend(struct.pack(">i", len(ts)))
    for t in ts:
        cells = t["cells"]
        H, D, W = cells.shape
        utf(f"e{t['num']}")
        utf(t["kind"])
        buf.extend(struct.pack(">hhhh", W, H, D, t["G"]))
        buf.extend(struct.pack(">h", len(t["levels"])))
        for y, r in zip(t["levels"], t["rects"]):
            r = r or (0, 0, -1, -1)
            buf.extend(struct.pack(">hhhhh", y, *r))
        buf.extend(struct.pack(">h", len(t["cores"])))
        for c in t["cores"]:
            buf.extend(struct.pack(">hhhhh", 1 if c["type"] == "double" else 0, c["x"], c["z"], c["first"], c["last"]))
        buf.extend(struct.pack(">h", len(t["pal"])))
        for p in t["pal"]:
            utf(p)
        flat = cells.ravel()
        change = np.nonzero(np.diff(flat))[0] + 1
        starts = np.concatenate([[0], change])
        ends = np.concatenate([change, [len(flat)]])
        runs = []
        for s, e in zip(starts, ends):
            n, v = e - s, int(flat[s])
            while n > 0:
                k = min(n, 32767)
                runs.append((k, v))
                n -= k
        buf.extend(struct.pack(">i", len(runs)))
        buf.extend(b"".join(struct.pack(">hh", k, v) for k, v in runs))
    TEMPLATES.write_bytes(gzip.compress(bytes(buf), 9))
    print(f"{len(ts)} templates, {len(buf) // 1024} KB before gzip, {TEMPLATES.stat().st_size // 1024} KB in the jar")


def preview_dump(t, out):
    """A city dump (tools/city/mesh.py) of one template on a pad, markers as plain blocks."""
    cells = t["cells"]
    H, D, W = cells.shape
    M = 6
    pal = ["air", "asphalt"]
    look = {"@ground": "lobby_floor_white", "@slab": "office_floor", "@lamp": "minecraft:pearlescent_froglight",
            "@doortop": "air"}
    vox = np.zeros((H + 2, D + 2 * M, W + 2 * M), np.uint16)
    vox[0] = 1
    lut = np.zeros(len(t["pal"]) + 2, np.uint16)
    for i, p in enumerate(t["pal"]):
        if p.startswith("@door:"):
            _, kind, facing, hinge = p.split(":")
            b = f"{kind}[facing={facing},hinge={'left' if hinge == '?' else hinge},open=false,half=lower]"
        else:
            b = look.get(p, p)
        if b not in pal:
            pal.append(b)
        lut[i + 2] = pal.index(b)
    sub = lut[np.clip(cells, 0, None)]
    vox[1:H + 1, M:M + D, M:M + W] = sub
    # door tops
    for i, p in enumerate(t["pal"]):
        if p.startswith("@door:"):
            _, kind, facing, hinge = p.split(":")
            top = f"{kind}[facing={facing},hinge={'left' if hinge == '?' else hinge},open=false,half=upper]"
            if top not in pal:
                pal.append(top)
            ys, zs, xs = np.nonzero(cells == i + 2)
            vox[ys + 2, zs + M, xs + M] = pal.index(top)
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    vox.tofile(str(out) + ".vox")
    Path(str(out) + ".json").write_text(json.dumps({"size": [W + 2 * M, H + 2, D + 2 * M], "origin": [0, 0, 0],
                                                   "palette": pal, "lamps": [], "cars": []}))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pack")
    ap.add_argument("--only")
    ap.add_argument("--preview")
    a = ap.parse_args()
    pack_tex = Path(a.pack) / "x/Textures"
    data = load(a.pack)
    nums = [int(x) for x in a.only.split(",")] if a.only else sorted(data)
    ts, photos = [], {}
    for num in nums:
        t = convert(num, data[num])
        assemble(t, photos)
        doors(t)
        ensure_door(t)
        ts.append(t)
        del t["ex"]
    tiles, shop_tex = tile_photos(photos, pack_tex)
    for t in ts:
        finish(t, tiles)
        plates(t)
    if a.preview:
        for t in ts:
            preview_dump(t, Path(a.preview) / f"e{t['num']}")
    make_textures(pack_tex, texel_spans(data, 1.2), shop_tex)
    write_blocks(block_specs(shop_tex))
    if not a.only:
        write_templates(ts)
    print(f"{len(RECIPES)} wall/roof/metal blocks, {len(shop_tex)} shop window tiles", flush=True)


if __name__ == "__main__":
    main()
