"""Furniture and fittings from Josiah's prop packs, as chunky blocks.

    python3 tools/props/props.py [--only REGEX] [--sheet OUT.png]

Each prop's mesh (tools/props/meshio.py reads glTF, OBJ and FBX) is turned to face north, scaled
to its blocks and voxelised at 1/16 of a block: a shell from points spread over its surface, then
everything the shell closes in on all six sides filled. The solid voxels of each block it spans are
merged into as few boxes as will cover them, and every face a box shows gets its own rectangle on
that block's atlas, painted at four pixels to the voxel from the colours of the surface it stands
for (so a fridge's handle, a cooker's dials and a cabinet's grain all come through). Then the props
are worn: dulled, grimed toward the floor and in their corners, and chipped along their edges.

Writes the atlases (and their LabPBR _n/_s and the preview's _nrm_gl maps) to tools/props/tex as
pk_<prop>[_<dx><dy><dz>].png, the blocks to tools/city/blocks.json and the props' block layout to
src/main/resources/data/zomboidcraft/props.json for world/county/Props.java. The packs themselves
(PACKS below) stay outside the repo; credits are in CREDITS.md.
"""
import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import meshio  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX_OUT = ROOT / "tools/props/tex"
BLOCKS = ROOT / "tools/city/blocks.json"
LAYOUT = ROOT / "src/main/resources/data/zomboidcraft/props.json"
PK = Path("/tmp/claude-0/packs")
POLY = Path("/mnt/project-files/zomboid-mod/assets/polyhaven-props")
GROCERY = PK / "grocery/meshes/GroceryPropsKit_Meshes_Textures"
CABINETS = PK / "vintage-office-cabinets-collection/x"

R = 16      # voxels per block
S = 4       # atlas pixels per voxel
DIRS = ["down", "up", "north", "south", "west", "east"]
VEC = np.array([(0, -1, 0), (0, 1, 0), (0, 0, -1), (0, 0, 1), (-1, 0, 0), (1, 0, 0)], np.float64)
AXIS = [1, 1, 2, 2, 0, 0]
SIGN = [-1, 1, -1, 1, -1, 1]
# the other two axes of each face, as the atlas rectangle's columns and rows run (Minecraft's own
# automatic UVs, so a face shows the right way round): (axis, flipped) for u, then for v
UV_AXES = {
    "down": ((0, False), (2, True)), "up": ((0, False), (2, False)),
    "north": ((0, True), (1, True)), "south": ((0, False), (1, True)),
    "west": ((2, False), (1, True)), "east": ((2, True), (1, True)),
}


def gltf(path, node=None, image=None):
    """A glTF's nodes; `image` stands in for the base colour when the file only carries a factor."""
    def load():
        parts = meshio.load_gltf(path, node)
        if image:
            img = np.array(Image.open(image).convert("RGBA"), np.float32)
            for p in parts:
                p.image, p.factor = img, (1, 1, 1, 1)
        return parts, False
    return load


def obj(path, only=None, image=None):
    return lambda: (meshio.load_obj(path, only, image), True)


def fbx(path, image=None, only=None):
    return lambda: (meshio.load_fbx(path, image, only), True)


KITCHEN = PK / "kitchen/Kitchen_furniture.glb"
KX = PK / "kitchen/x"
OFFICE = next(PK.glob("low-poly-office-pack-*"), PK) / "x"


def ph(name):
    """A Poly Haven model (glTF, 1K textures) from the project's CC0 library."""
    return gltf(next((POLY / name).glob("*.gltf")))


def office(name):
    return obj(next(OFFICE.rglob(f"{name}.obj")))


def grocery(name, tex=None):
    return fbx(GROCERY / "FBX" / f"{name}.fbx", GROCERY / "Textures" / tex if tex else None)

# name: source, blocks (w, h, d) it spans, height in blocks, the way its front faces in the
# source (+x, -x, +z, -z), how it sits (back: against the wall at the block's south side), how
# hard it is worn, its material (sound, shine), title and loot table
PROPS = {
    "fridge": dict(src=gltf(KITCHEN, "^Fridge$"), cells=(1, 2, 1), height=1.86, front="+z",
                   title="Fridge", sound="METAL", shine=0.45, loot="home_fridge",
                   swap={"000": "home_fridge", "010": "home_fridge_top"}),
    "cooker": dict(src=gltf(KITCHEN, "^Gas_cooker$", KX / "Gas cooker/Clean/Gas_cooker_LP_Gas_cooker_BaseColor.png"),
                   cells=(1, 1, 1), height=0.94, fill=True, ymax=1.2, front="+z", title="Gas Cooker",
                   sound="METAL", shine=0.4, loot="home_oven", swap={"000": "home_stove"}),
    "dishwasher": dict(src=gltf(KITCHEN, "^Dishwasher$", KX / "Dishwasher/dishwasher_LP_Dishwasher _BaseColor.png"),
                       cells=(1, 1, 1), height=0.94, fill=True, front="+z", title="Dishwasher", sound="METAL",
                       shine=0.4, loot="kitchen", swap=[{"000": f"home_dishwasher_{f}"} for f in ("oak", "white", "walnut")]),
    "microwave": dict(src=gltf(KITCHEN, "^Microwave$", KX / "Microwave/microwave_Micro_BaseColor.png"),
                      cells=(1, 1, 1), width=0.82, front="+z", title="Microwave", sound="METAL", shine=0.4,
                      swap={"000": "home_microwave"}),
    "toaster": dict(src=gltf(KITCHEN, "^Toaster$"), cells=(1, 1, 1), width=0.55, front="+z", title="Toaster",
                    sound="METAL", shine=0.5, swap={"000": "home_toaster"}),
    "coffee": dict(src=gltf(KITCHEN, "^Coffee_machine$", KX / "Coffee Machine/coffee_machine_LP_Coffee_machine_BaseColor.png"),
                   cells=(1, 1, 1), height=0.62, front="+z", title="Coffee Maker", sound="METAL", shine=0.4,
                   swap={"000": "home_coffee_maker"}),
    "tv_crt": dict(src=ph("Television_01"), cells=(1, 1, 1), width=0.9, front="+z", title="Television",
                   sound="METAL", shine=0.3, swap={"000": "home_tv"}),
    "tv_wood": dict(src=ph("television_02"), cells=(1, 1, 1), width=0.9, front="+z", title="Television",
                    sound="WOOD", shine=0.3, swap={"000": "home_tv"}),
    "armchair": dict(src=ph("ArmChair_01"), cells=(1, 1, 1), width=0.94, front="+z", title="Armchair",
                     sound="WOOL", sit="middle", swap=[{"000": f"home_armchair_{c}"} for c in ("blue", "brown", "floral")]),
    "armchair_green": dict(src=ph("GreenChair_01"), cells=(1, 1, 1), width=0.94, front="+z", title="Armchair",
                           sound="WOOL", sit="middle", swap=[{"000": f"home_armchair_{c}"} for c in ("blue", "brown", "floral")]),
    "vending": dict(src=grocery("SM_Vending Machine_01", "T_VendingMachine_01_BC.png"), cells=(1, 2, 1),
                    height=1.95, front="-x", title="Vending Machine", sound="METAL", shine=0.5,
                    swap={"000": "store_vending", "010": "store_vending_top"}),
    "file_cabinet": dict(src=obj(CABINETS / "Obj/File_Cabinet.obj", None, CABINETS / "Textures/ue/T_FileCabinet_BC.tga"),
                         cells=(1, 1, 1), height=0.95, front="+z", title="Filing Cabinet", sound="WOOD", shine=0.25,
                         swap={"000": "filing_cabinet"}),
}


# ---------------------------------------------------------------- sampling

def samples(tris, uvs, h):
    """Points about h apart over every triangle, with their uvs and the triangle's normal."""
    P, U, NR = [], [], []
    nrm = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    nrm = nrm / np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-12)
    e = np.max(np.stack([np.linalg.norm(tris[:, a] - tris[:, b], axis=1) for a, b in ((0, 1), (0, 2), (1, 2))]), 0)
    n = np.clip(np.ceil(e / h).astype(int), 1, 400)
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


def colours(part, uv, flip):
    if part.image is None:
        c = np.tile(np.array(part.factor[:4], np.float32) * [255, 255, 255, 255], (len(uv), 1))
        return c
    img = part.image
    H, W = img.shape[:2]
    x = np.clip((uv[:, 0] % 1.0) * W, 0, W - 1).astype(int)
    v = uv[:, 1] % 1.0
    y = np.clip(((1 - v) if flip else v) * H, 0, H - 1).astype(int)
    return img[y, x] * np.array(part.factor[:4], np.float32)


TURN = {"-z": 0, "+x": 90, "+z": 180, "-x": -90}


def placed(cfg, parts):
    """The prop's triangles turned to face north and scaled into its blocks, in voxels."""
    th = np.radians(TURN[cfg["front"]])
    c, s = np.cos(th), np.sin(th)
    rot = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    if cfg.get("up") == "z":
        # z-up sources stood up: (x, y, z) -> (x, z, -y)
        parts = [meshio.Part(np.stack([p.tris[..., 0], p.tris[..., 2], -p.tris[..., 1]], -1), p.uvs, p.image,
                             p.factor, p.name) for p in parts]
    tris = [p.tris @ rot.T for p in parts]
    if "ymax" in cfg:
        # stray bits floating over the model (a lid left in the air) are cut away
        tris = [t[t[:, :, 1].mean(1) < cfg["ymax"]] for t in tris]
    allp = np.concatenate([t.reshape(-1, 3) for t in tris])
    lo, hi = allp.min(0), allp.max(0)
    size = hi - lo
    cells = np.array(cfg["cells"], np.float64)
    room = cells * 15.5 / 16
    if cfg.get("fill"):
        # kitchen units fill their block like the rest of a counter run: scaled on each axis
        k = np.array([room[0] / size[0], cfg["height"] / size[1], room[2] / size[2]]) * R
    else:
        k = cfg["height"] / size[1] if "height" in cfg else cfg["width"] / size[0]
        k = np.full(3, min(k, room[0] / size[0], room[1] / size[1], room[2] / size[2]) * R)
    span = size * k
    out = []
    for t in tris:
        q = (t - lo) * k
        q[..., 0] += (cells[0] * R - span[0]) / 2
        if cfg.get("sit", "back") == "back":
            q[..., 2] += cells[2] * R - span[2] - 0.25
        else:
            q[..., 2] += (cells[2] * R - span[2]) / 2
        out.append(q)
    return out


# ---------------------------------------------------------------- voxels

def voxelise(cfg, parts, flip):
    dims = tuple(int(v * R) for v in (cfg["cells"][0], cfg["cells"][1], cfg["cells"][2]))
    shell = np.zeros(dims, bool)
    pts, cols, nrms = [], [], []
    for part, t in zip(parts, placed(cfg, parts)):
        p, uv, n = samples(t, part.uvs, 1.0 / S)
        c = colours(part, uv, flip)
        keep = c[:, 3] > 100
        pts.append(p[keep])
        cols.append(c[keep, :3])
        nrms.append(n[keep])
    P, C, NR = np.concatenate(pts), np.concatenate(cols), np.concatenate(nrms)
    inner = P - NR * 0.3
    vi = np.clip(np.floor(inner).astype(int), 0, np.array(dims) - 1)
    shell[vi[:, 0], vi[:, 1], vi[:, 2]] = True
    solid = shell | enclosed(shell)
    return solid, P, C, NR


def enclosed(shell):
    """Cells with shell somewhere on all six sides along the three axes."""
    out = np.ones_like(shell)
    for ax in range(3):
        fwd = np.maximum.accumulate(shell, axis=ax)
        back = np.flip(np.maximum.accumulate(np.flip(shell, ax), axis=ax), ax)
        out &= fwd & back
    return out & ~shell


def boxes(solid):
    """Greedy cover of the solid cells by boxes: along x, then z, then up."""
    used = np.zeros_like(solid)
    X, Y, Z = solid.shape
    out = []
    for y in range(Y):
        for z in range(Z):
            for x in range(X):
                if not solid[x, y, z] or used[x, y, z]:
                    continue
                x1 = x
                while x1 + 1 < X and solid[x1 + 1, y, z] and not used[x1 + 1, y, z]:
                    x1 += 1
                z1 = z
                while z1 + 1 < Z and np.all(solid[x:x1 + 1, y, z1 + 1]) and not used[x:x1 + 1, y, z1 + 1].any():
                    z1 += 1
                y1 = y
                while (y1 + 1 < Y and np.all(solid[x:x1 + 1, y1 + 1, z:z1 + 1])
                       and not used[x:x1 + 1, y1 + 1, z:z1 + 1].any()):
                    y1 += 1
                used[x:x1 + 1, y:y1 + 1, z:z1 + 1] = True
                out.append((x, y, z, x1 + 1, y1 + 1, z1 + 1))
    return out


# ---------------------------------------------------------------- colour lookup

class Surface:
    """The surface's colours binned by the way they face, the voxel layer and a quarter-voxel
    square across it, with coarser fallbacks."""

    def __init__(self, P, C, NR):
        self.fine, self.coarse = {}, {}
        q = P - NR * 0.3
        for d in range(6):
            m = NR @ VEC[d] > 0.3
            if not m.any():
                continue
            ax = AXIS[d]
            a1, a2 = [a for a in range(3) if a != ax]
            layer = np.floor(q[m, ax]).astype(np.int64)
            u = np.floor(P[m, a1] * S).astype(np.int64)
            v = np.floor(P[m, a2] * S).astype(np.int64)
            for table, uu, vv in ((self.fine, u, v), (self.coarse, u // S, v // S)):
                key = ((layer + 64) * 4096 + (uu + 64 * S)) * 4096 + (vv + 64 * S)
                k, inv = np.unique(key, return_inverse=True)
                sums = np.zeros((len(k), 3))
                np.add.at(sums, inv, C[m])
                cnt = np.bincount(inv)
                table[d] = (k, sums / cnt[:, None])
        self.mean = C.mean(0)

    @staticmethod
    def _get(table, d, layer, u, v, coarse):
        if d not in table:
            return None, np.zeros(len(u), bool)
        k, val = table[d]
        off = 64 if coarse else 64 * S
        key = ((layer + 64) * 4096 + (u + off)) * 4096 + (v + off)
        i = np.clip(np.searchsorted(k, key), 0, len(k) - 1)
        hit = k[i] == key
        return val[i], hit

    def colour(self, d, layer, u, v):
        """Colours for texels at fine (u, v) on voxel layer `layer` facing d."""
        out = np.tile(self.mean, (len(u), 1))
        done = np.zeros(len(u), bool)
        for table, coarse in ((self.fine, False), (self.coarse, True)):
            uu, vv = (u // S, v // S) if coarse else (u, v)
            for dl in (0, -1, 1, -2, 2, -3, 3):
                val, hit = self._get(table, d, layer + dl * -SIGN[d], uu, vv, coarse)
                take = hit & ~done
                if val is not None:
                    out[take] = val[take]
                done |= take
            if done.all():
                break
        # anything still missing takes the colour of the nearest painted texel on the same face
        return out, done


# ---------------------------------------------------------------- painting

def face_grid(d, box):
    """Texel centres of a box face in global voxel units, laid out the way Minecraft samples the
    face's rectangle: arrays (h, w, 3), plus the voxel layer the face shows."""
    (au, fu), (av, fv) = UV_AXES[DIRS[d]]
    lo, hi = np.array(box[:3], float), np.array(box[3:], float)
    w, h = int((hi[au] - lo[au]) * S), int((hi[av] - lo[av]) * S)
    us = lo[au] + (np.arange(w) + 0.5) / S
    vs = lo[av] + (np.arange(h) + 0.5) / S
    if fu:
        us = us[::-1]
    if fv:
        vs = vs[::-1]
    pts = np.zeros((h, w, 3))
    pts[..., au] = us[None, :]
    pts[..., av] = vs[:, None]
    ax = AXIS[d]
    pts[..., ax] = hi[ax] if SIGN[d] > 0 else lo[ax]
    layer = int(hi[ax] - 1) if SIGN[d] > 0 else int(lo[ax])
    return pts, layer


def fill_missing(img, have, rounds=64):
    """Spreads painted texels into the unpainted ones; anything still out of reach takes the mean."""
    if have.all() or not have.any():
        return img
    img, have = img.copy(), have.copy()
    for _ in range(rounds):
        if have.all():
            break
        acc = np.zeros_like(img)
        cnt = np.zeros(have.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            sh = np.roll(np.roll(have, dy, 0), dx, 1)
            acc += np.roll(np.roll(img, dy, 0), dx, 1) * sh[..., None]
            cnt += sh
        new = ~have & (cnt > 0)
        img[new] = acc[new] / cnt[new][:, None]
        have |= new
    img[~have] = img[have].mean(0)
    return img


def occlusion(solid, d, pts):
    """How shut in each texel is: solid voxels in front of the face around it (0 open, 1 shut)."""
    ax = AXIS[d]
    out = pts.copy()
    out[..., ax] += SIGN[d] * 0.5
    occ = np.zeros(pts.shape[:2])
    a1, a2 = [a for a in range(3) if a != ax]
    for o1 in (-0.6, 0, 0.6):
        for o2 in (-0.6, 0, 0.6):
            q = out.copy()
            q[..., a1] += o1
            q[..., a2] += o2
            vi = np.floor(q).astype(int)
            ok = np.all((vi >= 0) & (vi < np.array(solid.shape)), -1)
            v = np.zeros(pts.shape[:2], bool)
            idx = vi[ok]
            v[ok] = solid[idx[:, 0], idx[:, 1], idx[:, 2]]
            occ += v
    return occ / 9


def hidden(solid, d, box):
    """Whether something solid of the prop lies against the whole face."""
    ax = AXIS[d]
    lo, hi = list(box[:3]), list(box[3:])
    at = hi[ax] if SIGN[d] > 0 else lo[ax] - 1
    if at < 0 or at >= solid.shape[ax]:
        return False
    sl = [slice(lo[i], hi[i]) for i in range(3)]
    sl[ax] = slice(at, at + 1)
    return bool(solid[tuple(sl)].all())


def wear(rgb, d, pts, occ, rng, cfg, height):
    """Dull, grimy and chipped: old furniture in a dead town."""
    amount = cfg.get("wear", 0.5)
    lum = rgb @ np.array([0.299, 0.587, 0.114])
    rgb = lum[..., None] + (rgb - lum[..., None]) * 0.82
    rgb = rgb * 0.94
    # grime: blotches, thicker toward the floor and in corners
    h, w = rgb.shape[:2]
    noise = blotch(rng, h, w)
    floor = np.clip(1 - pts[..., 1] / max(height * 0.35, 1), 0, 1)
    g = amount * (0.1 + 0.25 * noise + 0.25 * floor + 0.35 * occ)
    rgb = rgb * (1 - g[..., None] * 0.55) + np.array([62, 52, 40]) * g[..., None] * 0.55 * 0.4
    # chips along the edges of fronts and tops
    if d != 0 and min(h, w) > 3:
        edge = np.zeros((h, w), bool)
        edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
        chip = edge & (rng.random((h, w)) < 0.12 * amount)
        rgb[chip] = rgb[chip] * 0.55 + 70
    return np.clip(rgb, 0, 255)


def blotch(rng, h, w):
    small = rng.random((max(2, h // 6 + 1), max(2, w // 6 + 1)))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.array(img, np.float32) / 255


def pack(rects):
    """Shelf-packs (w, h) rectangles with a 1 px gutter into the smallest square power of two."""
    order = sorted(range(len(rects)), key=lambda i: -rects[i][1])
    size = 16
    while True:
        pos, x, y, row = {}, 0, 0, 0
        ok = True
        for i in order:
            w, h = rects[i][0] + 2, rects[i][1] + 2
            if w > size:
                ok = False
                break
            if x + w > size:
                x, y, row = 0, y + row, 0
            if y + h > size:
                ok = False
                break
            pos[i] = (x + 1, y + 1)
            x += w
            row = max(row, h)
        if ok:
            return size, pos
        size *= 2


def build(name, cfg):
    parts, flip = cfg["src"]()
    solid, P, C, NR = voxelise(cfg, parts, flip)
    surf = Surface(P, C, NR)
    rng = np.random.default_rng(abs(hash(name)) % (2 ** 32))
    height = cfg["cells"][1] * R
    blocks, layout = {}, []
    cw, ch, cd = cfg["cells"]
    for cy in range(ch):
        for cz in range(cd):
            for cx in range(cw):
                o = np.array([cx, cy, cz]) * R
                sub = solid[o[0]:o[0] + R, o[1]:o[1] + R, o[2]:o[2] + R]
                if not sub.any():
                    continue
                bname = f"pk_{name}" + ("" if (cx, cy, cz) == (0, 0, 0) else f"_{cx}{cy}{cz}")
                faces = []
                for b in boxes(sub):
                    g = tuple(int(v) for v in (o[0] + b[0], o[1] + b[1], o[2] + b[2], o[0] + b[3], o[1] + b[4], o[2] + b[5]))
                    for d in range(6):
                        if hidden(solid, d, g):
                            continue
                        pts, layer = face_grid(d, g)
                        h, w = pts.shape[:2]
                        ax = AXIS[d]
                        a1, a2 = [a for a in range(3) if a != ax]
                        u = np.floor(pts[..., a1] * S).astype(np.int64).ravel()
                        v = np.floor(pts[..., a2] * S).astype(np.int64).ravel()
                        col, have = surf.colour(d, np.full(u.shape, layer, np.int64), u, v)
                        img = fill_missing(col.reshape(h, w, 3), have.reshape(h, w))
                        occ = occlusion(solid, d, pts)
                        img = wear(img, d, pts, occ, rng, cfg, height)
                        faces.append((b, d, img, occ))
                if not faces:
                    continue
                size, pos = pack([(f[2].shape[1], f[2].shape[0]) for f in faces])
                maps = {k: np.zeros((size, size, 4), np.float32) for k in ("", "_n", "_s", "_nrm_gl")}
                spec_boxes = {}
                for i, (b, d, img, occ) in enumerate(faces):
                    x, y = pos[i]
                    h, w = img.shape[:2]
                    pad = np.pad(img, ((1, 1), (1, 1), (0, 0)), mode="edge")
                    po = np.pad(occ, 1, mode="edge")
                    maps[""][y - 1:y + h + 1, x - 1:x + w + 1] = np.dstack([pad, np.full(po.shape, 255)])
                    nrm = np.dstack([np.full(po.shape, 128), np.full(po.shape, 128),
                                     255 * (1 - 0.55 * po), np.full(po.shape, 255)])
                    maps["_n"][y - 1:y + h + 1, x - 1:x + w + 1] = nrm
                    maps["_nrm_gl"][y - 1:y + h + 1, x - 1:x + w + 1] = [128, 128, 255, 255]
                    maps["_s"][y - 1:y + h + 1, x - 1:x + w + 1] = [cfg.get("shine", 0.2) * 255, 10, 20, 255]
                    key = tuple(b)
                    entry = spec_boxes.setdefault(key, ({}, {}))
                    entry[0][DIRS[d]] = bname
                    entry[1][DIRS[d]] = [round(x * 16 / size, 4), round(y * 16 / size, 4),
                                         round((x + w) * 16 / size, 4), round((y + h) * 16 / size, 4)]
                # the gaps between faces take their neighbours' colours, opaque: mipmaps then never
                # blend a face's edge with see-through pixels, and the block draws as solid
                col = maps[""]
                have = col[..., 3] > 0
                col[..., :3] = fill_missing(col[..., :3], have, 8)
                col[..., 3] = 255
                for k in ("_n", "_nrm_gl"):
                    maps[k][~have] = [128, 128, 255, 255]
                maps["_s"][~have] = [0, 10, 0, 255]
                TEX_OUT.mkdir(parents=True, exist_ok=True)
                for k, a in maps.items():
                    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(TEX_OUT / f"{bname}{k}.png")
                spec = {"model": "boxes", "rotate": "facing", "title": cfg["title"], "sound": cfg.get("sound", "WOOD"),
                        "boxes": [list(k) + [v[0], v[1]] for k, v in spec_boxes.items()]}
                cell = f"{cx}{cy}{cz}"
                loot = cfg.get("loot") if cell == "000" else None
                orig = next((ORIGINALS[w[cell]] for w in swaps(cfg) if w.get(cell) in ORIGINALS), None)
                if orig and "loot" in orig:
                    loot = orig["loot"]   # a stand-in searches like the block it stands in for
                if loot:
                    spec["loot"] = loot
                blocks[bname] = spec
                layout.append({"block": bname, "at": [cx, cy, cz]})
                print(f"{bname}: {len(spec_boxes)} boxes, {len(faces)} faces, atlas {size}", flush=True)
    return blocks, {"cells": list(cfg["cells"]), "blocks": layout}


def sheet(names, out):
    """Each block's atlas side by side, for a look."""
    ims = [Image.open(TEX_OUT / f"{n}.png").convert("RGBA") for n in names]
    W = sum(max(256, i.width) for i in ims) + 8 * len(ims)
    H = max(max(256, i.height) for i in ims)
    canvas = Image.new("RGBA", (W, H), (60, 70, 80, 255))
    x = 0
    for im in ims:
        k = max(1, 256 // im.width)
        im2 = im.resize((im.width * k, im.height * k), Image.NEAREST)
        canvas.alpha_composite(im2, (x, 0))
        x += im2.width + 8
    canvas.save(out)


ORIGINALS = {}


def swaps(cfg):
    """The built-in objects a prop stands in for: {cell: block} each."""
    swap = cfg.get("swap", [])
    return [swap] if isinstance(swap, dict) else swap
SWAPS = ROOT / "src/main/java/com/zomboidcraft/world/county/PropSwaps.java"


def write_swaps(layout):
    """PropSwaps.java: for every built-in block a prop can stand in for, its group (the first block
    of the object, so a fridge and its top roll the same) and the stand-ins, one per variant."""
    groups = {}
    for name, cfg in PROPS.items():
        if name not in layout:
            continue
        cells = {b["at"][0] * 100 + b["at"][1] * 10 + b["at"][2]: b["block"] for b in layout[name]["blocks"]}
        for swap in swaps(cfg):
            key = tuple(sorted(swap.items()))
            groups.setdefault(key, []).append({c: cells.get(int(c), "air") for c in swap})
    entries = []
    for key, variants in sorted(groups.items()):
        first = key[0][1]
        for cell, orig in key:
            alts = [first] + [v[cell] for v in variants]
            entries.append(f'        Map.entry("{orig}", new String[] {{{", ".join(json.dumps(a) for a in alts)}}})')
    body = ",\n".join(entries)
    SWAPS.write_text(f"""package com.zomboidcraft.world.county;

import java.util.Map;

/**
 * Written by tools/props/props.py: the pack props that stand in for built-in blocks. Each entry is
 * the object's group (its first block, so a fridge and its top pick alike) and then one stand-in
 * per variant; {{@link Cover}} keeps the built-in block as one more variant.
 */
final class PropSwaps {{
    static final Map<String, String[]> TABLE = Map.ofEntries(
{body});

    private PropSwaps() {{
    }}
}}
""")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--sheet")
    args = ap.parse_args()
    data = json.loads(BLOCKS.read_text())
    ORIGINALS.update(data["blocks"])
    layout = json.loads(LAYOUT.read_text()) if LAYOUT.exists() else {}
    made = []
    for name, cfg in PROPS.items():
        if args.only and not re.search(args.only, name):
            continue
        for k in [k for k in data["blocks"] if k == f"pk_{name}" or k.startswith(f"pk_{name}_")]:
            del data["blocks"][k]
        blocks, lay = build(name, cfg)
        data["blocks"].update(blocks)
        layout[name] = lay
        made += list(blocks)
    BLOCKS.write_text(json.dumps(data, indent=1) + "\n")
    LAYOUT.parent.mkdir(parents=True, exist_ok=True)
    LAYOUT.write_text(json.dumps(dict(sorted(layout.items())), indent=1) + "\n")
    write_swaps(layout)
    print(f"{len(made)} prop blocks")
    if args.sheet and made:
        sheet(made, args.sheet)


if __name__ == "__main__":
    main()
