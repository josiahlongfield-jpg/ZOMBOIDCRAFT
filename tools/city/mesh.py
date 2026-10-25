#!/usr/bin/env python3
"""Turns a city voxel dump (tools/city/CityPreview.java) into meshes for the three.js preview.

Faces between solid blocks are dropped, coplanar runs of the same texture are merged into long
quads with a repeating texture, and corners get Minecraft-style ambient occlusion. Far from the
camera, interiors are dropped and windows count as solid, like a distant-horizons LOD.

    python3 tools/city/mesh.py DUMP TEX_DIR OUT_DIR [--near x,y,z ...] [--radius 110] [--extra SPECS]

--extra adds block specs (tools/county/vanilla.py writes them for the vanilla blocks in a county
dump); besides cube/boxes/cross/slab they may use the vanilla shapes stairs, fence, gate, wall,
pane, trapdoor, log and flat.

Output: OUT_DIR/mesh.json plus one <texture>.bin per texture holding, per vertex, int16 x,y,z in
1/16 block, int8 normal (padded to 4), int16 u,v in texels and uint8 AO (padded to 4), then
uint32 triangle indices.
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).parent
# direction order: east, west, up, down, south, north
DIRS = ["east", "west", "up", "down", "south", "north"]
NORMALS = np.array([(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)])
FACING = ["north", "east", "south", "west"]


def face_quad(box, d):
    """Corners (4x3, in 16ths) and texel UVs (4x2) of one face of a box, wound outward."""
    x0, y0, z0, x1, y1, z1 = box
    if d == 0:
        c = [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)]
        uv = [(16 - z, y) for _, y, z in c]
    elif d == 1:
        c = [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)]
        uv = [(z, y) for _, y, z in c]
    elif d == 2:
        c = [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)]
        uv = [(x, 16 - z) for x, _, z in c]
    elif d == 3:
        c = [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]
        uv = [(x, z) for x, _, z in c]
    elif d == 4:
        c = [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
        uv = [(x, y) for x, y, _ in c]
    else:
        c = [(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)]
        uv = [(16 - x, y) for x, y, _ in c]
    return np.array(c, float), np.array(uv, float)


def rotate_pts(p, rot):
    """Quarter turns clockwise seen from above, about the block centre (in 16ths)."""
    p = p.copy()
    for _ in range(rot):
        x, z = p[:, 0].copy(), p[:, 2].copy()
        p[:, 0], p[:, 2] = 16 - z, x
    return p


def rotate_dir(d, rot):
    n = NORMALS[d].copy()
    for _ in range(rot):
        n = np.array([-n[2], n[1], n[0]])
    return int(np.where((NORMALS == n).all(1))[0][0])


def parse(name):
    if "[" not in name:
        return name, {}
    base, rest = name[:-1].split("[", 1)
    return base, dict(kv.split("=") for kv in rest.split(","))


VANILLA_SHAPES = {"slab", "stairs", "fence", "gate", "wall", "pane", "trapdoor", "trapdoor_open", "flat"}
VANILLA_FACING = {"stairs", "gate", "trapdoor", "trapdoor_open"}
# what each connecting shape joins up with, besides solid cubes
CONNECTS = {"fence": ("fence", "gate"), "wall": ("wall",), "pane": ("pane",)}


def turn_box(box, rot):
    """A box turned by quarter turns clockwise seen from above (north -> east)."""
    x0, y0, z0, x1, y1, z1 = box
    for _ in range(rot):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return [x0, y0, z0, x1, y1, z1]


def vanilla_boxes(model, props, spec):
    """Boxes (in 16ths, before the block's facing turn) for Minecraft's own block shapes."""
    if model == "slab":
        return [[0, 8, 0, 16, 16, 16]] if props.get("type") == "top" else [[0, 0, 0, 16, 8, 16]]
    if model == "flat":
        return [[0, 0, 0, 16, spec.get("height", 1), 16]]
    if model == "stairs":
        boxes = [[0, 0, 0, 16, 8, 16], [0, 8, 0, 16, 16, 8]]
        if props.get("half") == "top":
            boxes = [[b[0], 16 - b[4], b[2], b[3], 16 - b[1], b[5]] for b in boxes]
        return boxes
    if model == "trapdoor":
        if props.get("open") == "true":
            return [[0, 0, 13, 16, 16, 16]]
        return [[0, 13, 0, 16, 16, 16]] if props.get("half") == "top" else [[0, 0, 0, 16, 3, 16]]
    if model == "trapdoor_open":
        return [[0, 0, 15, 16, 16, 16]]
    if model == "gate":
        return [[0, 5, 7, 2, 16, 9], [14, 5, 7, 16, 16, 9], [2, 6, 7, 14, 9, 9], [2, 12, 7, 14, 15, 9]]
    post, arms = {
        "fence": ([6, 0, 6, 10, 16, 10], [[7, 12, 0, 9, 15, 6], [7, 6, 0, 9, 9, 6]]),
        "wall": ([4, 0, 4, 12, 16, 12], [[5, 0, 0, 11, 14, 4]]),
        "pane": ([7, 0, 7, 9, 16, 9], [[7, 0, 0, 9, 16, 7]]),
    }[model]
    boxes = [post]
    for r, side in enumerate(FACING):
        if props.get(side) == "true":
            boxes += [turn_box(a, r) for a in arms]
    return boxes


def connect(names, vox, blocks):
    """Fences, walls and panes join their neighbours: gives each its north/east/south/west props
    (new palette entries) the way the game's post-processing would."""
    kind = []
    for name in names:
        spec = blocks.get(parse(name)[0], {}) if name != "air" else {}
        m = spec.get("model", "cube")
        solid = m in ("cube", "log") and spec.get("height", 16) == 16 and not parse(name)[0].endswith(("_leaves", "water")) \
            and not parse(name)[0].startswith(("window", "glass"))
        kind.append("solid" if solid else m)
    names = list(names)
    for i in range(len(names)):
        if kind[i] not in CONNECTS:
            continue
        ys, zs, xs = np.nonzero(vox == i)
        if not len(ys):
            continue
        ok = np.isin(np.array(kind), CONNECTS[kind[i]] + ("solid",))
        bits = np.zeros(len(ys), int)
        for b, (dx, dz) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0))):
            nx, nz = np.clip(xs + dx, 0, vox.shape[2] - 1), np.clip(zs + dz, 0, vox.shape[1] - 1)
            bits |= ok[vox[ys, nz, nx]].astype(int) << b
        base, props = parse(names[i])
        for v in np.unique(bits):
            p = dict(props)
            for b, side in enumerate(FACING):
                p[side] = "true" if v >> b & 1 else "false"
            names.append(base + "[" + ",".join(f"{k}={x}" for k, x in p.items()) + "]")
            kind.append(kind[i])
            m = bits == v
            vox[ys[m], zs[m], xs[m]] = len(names) - 1
    return names


class Palette:
    def __init__(self, names, blocks, tex_dir):
        self.tex_names = []
        self.tex_index = {}
        self.tex_alpha = {}
        n = len(names)
        self.cube = np.zeros(n, bool)          # drawn as a (possibly lowered) cube
        self.opaque = np.zeros(n, bool)        # hides the faces of its neighbours
        self.glass = np.zeros(n, bool)         # translucent cube: glass hides glass
        self.pane = np.zeros(n, bool)          # a window or shutter filling the wall (solid from afar)
        self.height = np.full(n, 16.0)
        self.face_tex = np.full((n, 6), -1, int)
        self.models = {}                       # id -> list of (corners, uvs, dir, tex, on_edge)
        self.tex_dir = tex_dir
        for i, name in enumerate(names):
            if name == "air":
                continue
            base, props = parse(name)
            spec = blocks[base]
            if "door" in spec:
                self.models[i] = self.door(spec, props)
                continue
            rot = 0
            if spec.get("rotate") == "facing" or (spec.get("model") in VANILLA_FACING and "facing" in props):
                rot = FACING.index(props.get("facing", "north"))
            elif spec.get("rotate") == "axis":
                rot = 1 if props.get("axis", "z") == "x" else 0
            model = spec.get("model", "cube")
            self.pane[i] = base.startswith(("window", "boarded_window", "shutter"))
            if model == "slab" and props.get("type") == "double":
                model = "cube"
            elif model in VANILLA_SHAPES:
                tex = spec["tex"]
                spec = {"boxes": [b + [tex] for b in vanilla_boxes(model, props, spec)], "tex": tex}
                if model == "flat" or (model == "trapdoor" and props.get("open") != "true"):
                    rot = 0
                model = "boxes"
            if model == "log":
                axis = props.get("axis", "y")
                ends = {"y": (2, 3), "x": (0, 1), "z": (4, 5)}[axis]
                self.cube[i] = True
                for d in range(6):
                    self.face_tex[i, d] = self.tex(spec["tex"]["top" if d in ends else "side"])
                self.opaque[i] = True
                continue
            if model == "cube":
                self.cube[i] = True
                self.height[i] = spec.get("height", 16)
                for d in range(6):
                    self.face_tex[i, rotate_dir(d, rot)] = self.tex(self.tex_for(spec["tex"], DIRS[d]))
                alpha = any(self.tex_alpha[self.tex_names[t]] for t in self.face_tex[i])
                self.glass[i] = alpha
                self.opaque[i] = not alpha and self.height[i] == 16
            elif model == "boxes":
                faces = []
                for b in spec["boxes"]:
                    box, tname = b[:6], b[6]
                    t = self.tex(tname if isinstance(tname, str) else self.tex_for(spec["tex"], "side"))
                    for d in range(6):
                        c, uv = face_quad(box, d)
                        # a face on the block's boundary hides behind a solid neighbour
                        edge = box[[3, 0, 4, 1, 5, 2][d]] == (16 if d % 2 == 0 else 0)
                        faces.append((rotate_pts(c, rot), uv, rotate_dir(d, rot), t, edge))
                self.models[i] = faces
            elif model == "cross":
                t = self.tex(spec["tex"])
                faces = []
                for a, b in (((0.5, 0.5), (15.5, 15.5)), ((0.5, 15.5), (15.5, 0.5))):
                    c = np.array([(a[0], 0, a[1]), (b[0], 0, b[1]), (b[0], 16, b[1]), (a[0], 16, a[1])], float)
                    uv = np.array([(0, 0), (16, 0), (16, 16), (0, 16)], float)
                    faces.append((c, uv, 2, t, False))
                self.models[i] = faces

    def door(self, spec, props):
        """A vanilla door: a 3/16 panel on the side away from its facing when shut, swung a
        quarter turn toward its hinge (the placer's left or right) when open."""
        f = FACING.index(props.get("facing", "north"))
        if props.get("open") == "true":
            side = (f + 3) % 4 if props.get("hinge", "left") == "left" else (f + 1) % 4
        else:
            side = (f + 2) % 4
        t = self.tex(spec["tex"]["top" if props.get("half") == "upper" else "bottom"])
        faces = []
        for d in range(6):
            c, uv = face_quad(np.array([0, 0, 0, 16, 16, 3]), d)
            faces.append((rotate_pts(c, side), uv, rotate_dir(d, side), t, d == 5))
        return faces

    @staticmethod
    def tex_for(spec, face):
        if isinstance(spec, str):
            return spec
        if face in spec:
            return spec[face]
        if face in ("north", "south", "east", "west") and "side" in spec:
            return spec["side"]
        return spec.get("side") or next(iter(spec.values()))

    def tex(self, name):
        if name not in self.tex_index:
            self.tex_index[name] = len(self.tex_names)
            self.tex_names.append(name)
            a = np.array(Image.open(self.tex_dir / f"{name}.png").convert("RGBA"))[..., 3]
            self.tex_alpha[name] = bool((a < 250).any())
        return self.tex_index[name]


class Out:
    """Collects quads per texture."""

    def __init__(self, ntex):
        self.parts = [[] for _ in range(ntex)]

    def add(self, tex, corners, normals, uvs, ao):
        # corners (n,4,3) in 16ths, normals (n,3), uvs (n,4,2) texels, ao (n,4) 0..3
        for t in np.unique(tex):
            m = tex == t
            self.parts[t].append((corners[m], normals[m], uvs[m], ao[m]))

    def write(self, out_dir, names, alpha):
        index = []
        for t, parts in enumerate(self.parts):
            if not parts:
                continue
            c = np.concatenate([p[0] for p in parts]).reshape(-1, 3)
            n = np.repeat(np.concatenate([p[1] for p in parts]), 4, axis=0)
            uv = np.concatenate([p[2] for p in parts]).reshape(-1, 2)
            ao = np.concatenate([p[3] for p in parts]).reshape(-1)
            nq = len(c) // 4
            pos = np.round(c).astype("<i2")
            nrm = np.zeros((len(n), 4), "i1")
            nrm[:, :3] = n * 127
            uvs = np.round(uv).astype("<i2")
            aos = np.zeros((len(ao), 4), "u1")
            aos[:, :3] = (np.array([0.45, 0.65, 0.82, 1.0])[ao.astype(int)] * 255)[:, None]
            aos[:, 3] = 255
            base = np.arange(nq, dtype="<u4") * 4
            idx = np.stack([base, base + 1, base + 2, base, base + 2, base + 3], 1).reshape(-1)
            path = out_dir / f"{names[t]}.bin"
            with open(path, "wb") as f:
                for a in (pos, nrm, uvs, aos, idx):
                    f.write(a.tobytes())
            index.append({"tex": names[t], "verts": len(pos), "indices": len(idx), "alpha": alpha[names[t]]})
        return index


def ao_for(opaque_pad, ys, zs, xs, d):
    """Per-corner occlusion (0 darkest .. 3 open) for faces of cells (ys, zs, xs) facing d."""
    n = NORMALS[d]
    # two tangent axes in (x, y, z)
    axes = [a for a in range(3) if n[a] == 0]
    corners_out = []
    c, _ = face_quad((0, 0, 0, 16, 16, 16), d)
    for corner in c:
        s = [0, 0, 0]
        for a in axes:
            s[a] = 1 if corner[a] > 8 else -1
        def occ(dx, dy, dz):
            x = xs + 1 + n[0] + dx
            y = ys + 1 + n[1] + dy
            z = zs + 1 + n[2] + dz
            return opaque_pad[y, z, x].astype(int)
        a0, a1 = axes
        v0 = [0, 0, 0]; v0[a0] = s[a0]
        v1 = [0, 0, 0]; v1[a1] = s[a1]
        side1, side2 = occ(*v0), occ(*v1)
        corner_o = occ(v0[0] + v1[0], v0[1] + v1[1], v0[2] + v1[2])
        corners_out.append(np.where((side1 + side2) == 2, 0, 3 - side1 - side2 - corner_o))
    return np.stack(corners_out, 1)


def export_maps(tex_dir, out_dir, name):
    """LabPBR maps to what three.js wants: albedo, GL normal, ORM (AO, roughness, metal) and
    emissive colour. Returns whether the texture glows."""
    albedo = Image.open(tex_dir / f"{name}.png").convert("RGBA")
    albedo.save(out_dir / f"{name}.png")
    Image.open(tex_dir / f"{name}_nrm_gl.png").convert("RGB").save(out_dir / f"{name}_n.png")
    s = np.array(Image.open(tex_dir / f"{name}_s.png").convert("RGBA")).astype(float)
    labn = np.array(Image.open(tex_dir / f"{name}_n.png").convert("RGBA")).astype(float)
    orm = np.zeros(s.shape[:2] + (3,))
    orm[..., 0] = labn[..., 2]
    orm[..., 1] = (1 - s[..., 0] / 255) ** 2 * 255
    orm[..., 2] = np.where(s[..., 1] >= 230, 255, 0)
    Image.fromarray(orm.astype(np.uint8)).save(out_dir / f"{name}_orm.png")
    glow = np.where(s[..., 3] < 255, s[..., 3] / 254, 0)
    if not glow.any():
        return False
    e = np.array(albedo).astype(float)[..., :3] * glow[..., None]
    Image.fromarray(e.astype(np.uint8)).save(out_dir / f"{name}_e.png")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dump")
    ap.add_argument("tex_dir", type=Path)
    ap.add_argument("out_dir", type=Path)
    ap.add_argument("--near", action="append", default=[])
    ap.add_argument("--radius", type=float, default=110)
    ap.add_argument("--extra", action="append", default=[])
    a = ap.parse_args()
    meta = json.load(open(a.dump + ".json"))
    sx, H, sz = meta["size"]
    ox, _, oz = meta["origin"]
    vox = np.fromfile(a.dump + ".vox", dtype="<u2").reshape(H, sz, sx)
    blocks = json.load(open(HERE / "blocks.json"))["blocks"]
    for extra in a.extra:
        blocks.update(json.load(open(extra))["blocks"])
    meta["palette"] = connect(meta["palette"], vox, blocks)
    pal = Palette(meta["palette"], blocks, a.tex_dir)

    # near: within the radius of any camera; far: LOD (no interiors, windows solid)
    zz, xx = np.mgrid[0:sz, 0:sx]
    near_col = np.zeros((sz, sx), bool)
    for s in a.near or ["0,2,40"]:
        cx, _, cz = map(float, s.split(","))
        near_col |= np.hypot(xx + ox - cx, zz + oz - cz) < a.radius
    opaque = pal.opaque[vox]
    far_solid = opaque | ((pal.glass[vox] | pal.pane[vox]) & ~near_col[None])
    # air that sees the sky (or is right next to air that does) counts as outside
    covered = np.flip(np.maximum.accumulate(np.flip(far_solid, 0), 0), 0)
    sky = ~covered
    outside = sky.copy()
    for ax in (1, 2):
        outside |= np.roll(sky, 1, ax) | np.roll(sky, -1, ax)
    outside |= np.roll(outside, -1, 0)  # the cell under an overhang edge
    del covered, sky

    opaque_pad = np.pad(opaque, 1)
    out = Out(0)
    out.parts = []
    nfaces = 0

    def grow():
        while len(out.parts) < len(pal.tex_names):
            out.parts.append([])

    # ---- cubes
    cube = pal.cube[vox]
    for d in range(6):
        n = NORMALS[d]
        nb = np.roll(vox, shift=(-n[1], -n[2], -n[0]), axis=(0, 1, 2))
        if d == 2:
            nb[-1] = 0
        if d == 3:
            nb[0] = 0
        if d == 0:
            nb[:, :, -1] = 0
        if d == 1:
            nb[:, :, 0] = 0
        if d == 4:
            nb[:, -1] = 0
        if d == 5:
            nb[:, 0] = 0
        lowered = pal.height[vox] < 16
        vis = cube & ~pal.opaque[nb] & ~(pal.glass[vox] & pal.glass[nb])
        if d == 2:
            vis |= cube & lowered
        far = ~near_col[None]
        # far away: the neighbouring cell must be outside, and glass counts as solid
        outside_nb = np.roll(outside, shift=(-n[1], -n[2], -n[0]), axis=(0, 1, 2))
        vis &= ~far | outside_nb
        vis &= ~far | ~(pal.glass[nb] & ~lowered)
        del outside_nb, nb
        ys, zs, xs = np.nonzero(vis)
        ids = vox[ys, zs, xs]
        tex = pal.face_tex[ids, d]
        ao = ao_for(opaque_pad, ys, zs, xs, d)
        # merge runs along x (or z for faces looking along x): same texture, same AO, same height
        run_axis = xs if d not in (0, 1) else zs
        key = (tex.astype(np.int64) << 20) | (ao[:, 0] << 18) | (ao[:, 1] << 16) | (ao[:, 2] << 14) | (ao[:, 3] << 12) | pal.height[ids].astype(np.int64)
        other = (ys, zs) if d not in (0, 1) else (ys, xs)
        order = np.lexsort((run_axis, other[1], other[0]))
        ys, zs, xs, tex, ao, key, run_axis = ys[order], zs[order], xs[order], tex[order], ao[order], key[order], run_axis[order]
        o0, o1 = other[0][order], other[1][order]
        start = np.ones(len(ys), bool)
        if len(ys):
            start[1:] = ~((o0[1:] == o0[:-1]) & (o1[1:] == o1[:-1]) & (run_axis[1:] == run_axis[:-1] + 1) & (key[1:] == key[:-1]))
        rid = np.cumsum(start) - 1
        length = np.bincount(rid)
        first = np.nonzero(start)[0]
        ys, zs, xs, tex, ao = ys[first], zs[first], xs[first], tex[first], ao[first]
        ids = vox[ys, zs, xs]
        h = pal.height[ids]
        nq = len(ys)
        nfaces += nq
        corners = np.zeros((nq, 4, 3))
        uvs = np.zeros((nq, 4, 2))
        origin = np.stack([(xs + ox) * 16, ys * 16, (zs + oz) * 16], 1)
        for L, hh in set(zip(length.tolist(), h.tolist())):
            m = (length == L) & (h == hh)
            box = (0, 0, 0, 16 * L, hh, 16) if d not in (0, 1) else (0, 0, 0, 16, hh, 16 * L)
            c, uv = face_quad(box, d)
            corners[m] = origin[m][:, None, :] + c[None]
            uvs[m] = uv[None]
        normals = np.repeat(NORMALS[d][None], nq, 0)
        grow()
        out.add(tex, corners, normals, uvs, ao)
        print(f"cubes {DIRS[d]}: {nq} quads ({len(order)} faces)")
    del cube

    # ---- models (boxes, crosses)
    is_model = np.zeros(len(pal.cube), bool)
    is_model[list(pal.models)] = True
    ys, zs, xs = np.nonzero(is_model[vox])
    ids = vox[ys, zs, xs]
    keep = near_col[zs, xs] | outside[ys, zs, xs]
    ys, zs, xs, ids = ys[keep], zs[keep], xs[keep], ids[keep]
    for i in np.unique(ids):
        m = ids == i
        py, pz, px = ys[m], zs[m], xs[m]
        base = np.stack([(px + ox) * 16, py * 16, (pz + oz) * 16], 1)
        for c, uv, d, t, edge in pal.models[i]:
            sel = np.ones(len(py), bool)
            if edge:
                n = NORMALS[d]
                sel = ~opaque_pad[py + 1 + n[1], pz + 1 + n[2], px + 1 + n[0]]
            k = int(sel.sum())
            if not k:
                continue
            corners = base[sel][:, None, :] + c[None]
            grow()
            out.add(np.full(k, t), corners, np.repeat(NORMALS[d][None], k, 0), np.repeat(uv[None], k, 0), np.full((k, 4), 3))
            nfaces += k
    print("total quads", nfaces)

    a.out_dir.mkdir(parents=True, exist_ok=True)
    index = out.write(a.out_dir, pal.tex_names, pal.tex_alpha)
    emissive = {name: export_maps(a.tex_dir, a.out_dir, name) for name in pal.tex_names}
    for e in index:
        e["emissive"] = emissive[e["tex"]]
    # light-giving blocks near the cameras, so the preview can give the closest real lights
    lights = []
    for i, name in enumerate(meta["palette"]):
        base = parse(name)[0]
        if name == "air" or base.startswith("streetlight") or blocks[base].get("light", 0) < 12:
            continue
        ys, zs, xs = np.nonzero(vox == i)
        keep = near_col[zs, xs]
        for x, y, z in zip(xs[keep], ys[keep], zs[keep]):
            lights.append([int(x + ox), int(y), int(z + oz), base])
    json.dump({"textures": index, "lamps": meta.get("lamps", []), "lights": lights}, open(a.out_dir / "mesh.json", "w"))


if __name__ == "__main__":
    main()
