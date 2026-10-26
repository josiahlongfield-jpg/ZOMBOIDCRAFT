#!/usr/bin/env python3
"""Inks a block's parts the way the character bodies are drawn: every face of every box painted on
its own, with a dark inked rim, a lit top edge, a brighter top face and a darker underside, the
colours pushed a little flatter and bolder. Cartoonish but believable.

A box normally shows a crop of a shared material, so a rim painted on the material would land
wherever the crop happens to cut it. Here each face gets its own rectangle on a per-block atlas
(ink_<block>.png and its LabPBR _n/_s maps, plus the preview's _nrm_gl), painted from the crop the
face showed before, and the box gains an eighth item: each face's rectangle on that atlas, which
gen_blocks.py writes as the face's uv and mesh.py draws the same way.

    python3 tools/city/ink.py TEX_DIR OUT_DIR SPECS_OUT [--prefix ink__] NAME ...

reads tools/city/blocks.json and writes the inked specs to SPECS_OUT ({"blocks": {...}}, named
PREFIX + NAME, for mesh.py --extra or for merging back into blocks.json) and the atlases to OUT_DIR.
"""
import argparse
import json
import math
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).parent
DIRS = ["down", "up", "north", "south", "west", "east"]
P = 8                 # atlas pixels per 1/16 block, twice the city textures, so the one-pixel outline is fine
GUTTER = 4           # edge pixels repeated round each face, so mipmaps don't blend in its neighbours
MAPS = ("", "_n", "_s", "_nrm_gl")
EMPTY = {"": (0, 0, 0, 0), "_n": (128, 128, 255, 255), "_s": (0, 0, 0, 255), "_nrm_gl": (128, 128, 255, 255)}

# the rectangle Minecraft samples for each face of a box (from, to in 16ths): gen_blocks.AUTO_UV
AUTO_UV = {
    "down": lambda f, t: [f[0], 16 - t[2], t[0], 16 - f[2]],
    "up": lambda f, t: [f[0], f[2], t[0], t[2]],
    "north": lambda f, t: [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
    "south": lambda f, t: [f[0], 16 - t[1], t[0], 16 - f[1]],
    "west": lambda f, t: [f[2], 16 - t[1], t[2], 16 - f[1]],
    "east": lambda f, t: [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
}


def fit(a0, a1):
    """gen_blocks.fit: a face poking out of the block shows the nearest 16 of the texture."""
    span = a1 - a0
    if span > 16:
        return 0, 16
    if a0 < 0:
        return 0, span
    if a1 > 16:
        return 16 - span, 16
    return a0, a1


def face_tex(t, face):
    if isinstance(t, str):
        return t
    if face in t:
        return t[face]
    if face in ("north", "south", "east", "west") and "side" in t:
        return t["side"]
    return t.get("side") or next(iter(t.values()))


class Source:
    """The source textures, each map loaded once."""

    def __init__(self, tex_dir):
        self.dir = tex_dir
        self.cache = {}

    def get(self, name, suffix):
        key = (name, suffix)
        if key not in self.cache:
            path = self.dir / f"{name}{suffix}.png"
            if path.exists():
                self.cache[key] = np.array(Image.open(path).convert("RGBA")).astype(np.float32)
            else:
                self.cache[key] = None
        return self.cache[key]


def crop(src, rect, w, h):
    """The part of a texture a face samples (rect in 16ths, v down), resized to w x h pixels."""
    if src is None:
        return None
    s = src.shape[0] / 16
    (u0, u1), (v0, v1) = fit(rect[0], rect[2]), fit(rect[1], rect[3])
    x0, x1 = int(round(u0 * s)), max(int(round(u1 * s)), int(round(u0 * s)) + 1)
    y0, y1 = int(round(v0 * s)), max(int(round(v1 * s)), int(round(v0 * s)) + 1)
    part = Image.fromarray(src[y0:y1, x0:x1].astype(np.uint8))
    return np.array(part.resize((w, h), Image.NEAREST)).astype(np.float32)


# which way each edge of a face's image points in the block (top, bottom, left, right), as the
# face is sampled by Minecraft's automatic UVs
EDGES = {
    "north": ("up", "down", "east", "west"), "south": ("up", "down", "west", "east"),
    "west": ("up", "down", "north", "south"), "east": ("up", "down", "south", "north"),
    "up": ("north", "south", "west", "east"), "down": ("south", "north", "west", "east"),
}
# pieces of one object that span blocks: the sides where it carries on into its neighbour, so
# the seam gets no rim (a block named X_top joins down, and X joins up when X_top exists)
JOINS = {
    "bunk_bed_head": {"north"}, "bunk_bed_head_top": {"north"}, "bunk_bed_foot": {"south"},
    "bunk_bed_foot_top": {"south"}, "bunk_gen_engine": {"east"}, "bunk_gen_alternator": {"west"},
}


def joins(name, blocks):
    j = set(JOINS.get(name, ()))
    if name.endswith("_top"):
        j.add("down")
    if name + "_top" in blocks:
        j.add("up")
    for side, sides in (("_l", {"east"}), ("_m", {"east", "west"}), ("_r", {"west"})):
        if name.endswith(side) and name[:-2] + "_m" in blocks:
            j |= sides
    return j


def flush(box, side):
    f, t = box[:3], box[3:6]
    return {"up": t[1] >= 16, "down": f[1] <= 0, "east": t[0] >= 16, "west": f[0] <= 0,
            "south": t[2] >= 16, "north": f[2] <= 0}[side]


NORMAL = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}
EPS = 0.05


def face_point(d, u, v, box):
    """The point of face d of box at Minecraft UV (u, v), in 16ths."""
    f, t = box[:3], box[3:6]
    return {"north": (16 - u, 16 - v, f[2]), "south": (u, 16 - v, t[2]), "west": (f[0], 16 - v, u),
            "east": (t[0], 16 - v, 16 - u), "up": (u, t[1], v), "down": (u, f[1], 16 - v)}[d]


def inside(box, p):
    return all(box[i] < p[i] < box[3 + i] for i in range(3))


def rim_mask(d, box, others, joined, w, h):
    """Where a face gets its outline: along its edges, except where another part carries the
    surface on (a sleeve into its cuff, a counter into the next block) or butts into it (a leg
    under a table top). Only the object's outer edges and the edges of attached parts are inked."""
    rect = AUTO_UV[d](box[:3], box[3:6])
    ax, sign = NORMAL[d]
    plane = box[3 + ax] if sign > 0 else box[ax]
    rim = np.zeros((h, w), bool)

    def covered(u, v, side):
        if side in joined and flush(box, side):
            return True
        p = np.array(face_point(d, u, v, box), float)
        sa, ss = NORMAL[side]
        p[sa] += ss * EPS
        pin, pout = p.copy(), p.copy()
        pin[ax] -= sign * EPS
        pout[ax] += sign * EPS
        for o in others:
            if inside(o, pin):
                o_plane = o[3 + ax] if sign > 0 else o[ax]
                if inside(o, pout) or abs(o_plane - plane) < 0.01:
                    return True
        return False

    us = rect[0] + (np.arange(w) + 0.5) / w * (rect[2] - rect[0])
    vs = rect[1] + (np.arange(h) + 0.5) / h * (rect[3] - rect[1])
    top, bottom, left, right = EDGES[d]
    for i, u in enumerate(us):
        rim[0, i] |= not covered(u, rect[1], top)
        rim[-1, i] |= not covered(u, rect[3], bottom)
    for j, v in enumerate(vs):
        rim[j, 0] |= not covered(rect[0], v, left)
        rim[j, -1] |= not covered(rect[2], v, right)
    return rim


def ink(c, n, face, w, h, rng, box, rim, lift=0):
    """Paints the inked look over a face's colour c (h x w x 4) and height n (LabPBR _n alpha): a
    thin dark outline where rim is set, chipped paint just inside it, a lit line under a top edge,
    a brighter top face and a darker underside. The shade toward the floor runs over the whole
    object's height (lift: 16 for an upper block)."""
    rgb = c[..., :3]
    lum = rgb @ np.array([0.3, 0.59, 0.11], np.float32)
    rgb = lum[..., None] + (rgb - lum[..., None]) * 1.2        # bolder colour
    rgb = 128 + (rgb - 128) * 1.08
    if face == "up":
        rgb = rgb * 1.1 + 8                                     # lit from above
    elif face == "down":
        rgb = rgb * 0.62
    else:
        y = box[4] - (np.arange(h) + 0.5) / h * (box[4] - box[1]) + lift
        rgb = rgb * (0.88 + 0.12 * np.clip(y / 32, 0, 1))[:, None, None]
    if min(w, h) < 0.75 * P:
        rgb *= 0.8   # a sliver of an edge: just a touch darker
    else:
        near = np.zeros((h, w), bool)
        near[1:] |= rim[:-1]
        near[:-1] |= rim[1:]
        near[:, 1:] |= rim[:, :-1]
        near[:, :-1] |= rim[:, 1:]
        chips = near & ~rim & (rng.random((h, w)) < (0.18 if min(w, h) >= 2 * P else 0))  # none on thin rails
        rgb[chips] = rgb[chips] + (255 - rgb[chips]) * 0.25
        rgb[rim] = rgb[rim] * 0.4 + 5
        if face != "down":
            lit = np.zeros((h, w), bool)
            lit[1] = rim[0] & ~rim[1]
            rgb[lit] = rgb[lit] + (255 - rgb[lit]) * 0.2
        if n is not None:
            n[rim, 3] *= 0.6                                   # a bevel for Photon's parallax
    c[..., :3] = np.clip(rgb, 0, 255)
    return c, n


def pack(sizes):
    """Shelf-packs (w, h) rectangles with gutters into the smallest square power-of-two atlas."""
    area = sum((w + 2 * GUTTER) * (h + 2 * GUTTER) for w, h in sizes)
    side = max(16, 2 ** math.ceil(math.log2(math.sqrt(area * 1.25))))
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    while True:
        spots, x, y, row = [None] * len(sizes), 0, 0, 0
        ok = True
        for i in order:
            w, h = sizes[i][0] + 2 * GUTTER, sizes[i][1] + 2 * GUTTER
            if x + w > side:
                x, y, row = 0, y + row, 0
            if y + h > side or w > side:
                ok = False
                break
            spots[i] = (x + GUTTER, y + GUTTER)
            x, row = x + -(-w // 4) * 4, max(row, -(-h // 4) * 4)  # on a 4-pixel grid: two clean mip levels
        if ok:
            return side, spots
        side *= 2


def ink_block(name, spec, source, out_dir, atlas_name, joined=frozenset()):
    rng = np.random.default_rng(zlib.crc32(name.encode()))
    faces, keys, regions = [], {}, []
    boxes = []
    for b in spec["boxes"]:
        f, t = b[:3], b[3:6]
        uvs = {}
        for d in DIRS:
            tex = face_tex(b[6], d)
            rect = AUTO_UV[d](f, t)
            w = max(1, int(round(abs(rect[2] - rect[0]) * P)))
            h = max(1, int(round(abs(rect[3] - rect[1]) * P)))
            key = (tex, tuple(rect), d, tuple(b[:6]))
            if key not in keys:
                keys[key] = len(regions)
                regions.append((tex, rect, d, w, h, b[:6]))
            uvs[d] = keys[key]
        boxes.append((b, uvs))
    side, spots = pack([(r[3], r[4]) for r in regions])
    lift = 16 if "down" in joined else 0
    sheets = {s: np.zeros((side, side, 4), np.float32) + np.array(EMPTY[s], np.float32) for s in MAPS}
    solid = True
    used = np.zeros((side, side), bool)
    rects = []
    for (tex, rect, d, w, h, box), (x, y) in zip(regions, spots):
        parts = {s: crop(source.get(tex, s), rect, w, h) for s in MAPS}
        c = parts[""]
        if c is None:
            raise SystemExit(f"{name}: no texture {tex}")
        opaque = c[..., 3].mean() > 242
        solid &= opaque
        if opaque:
            others = [o[:6] for o in spec["boxes"] if o[:6] != box]
            rim = rim_mask(d, box, others, joined, w, h)
            c, parts["_n"] = ink(c, parts["_n"], d, w, h, rng, box, rim, lift)
            parts[""] = c
        for s, img in parts.items():
            if img is None:
                continue
            # the face, its edge pixels repeated into the gutter so mipmaps don't bleed
            padded = np.pad(img, ((GUTTER, GUTTER), (GUTTER, GUTTER), (0, 0)), mode="edge")
            sheets[s][y - GUTTER:y + h + GUTTER, x - GUTTER:x + w + GUTTER] = padded
        used[y - GUTTER:y + h + GUTTER, x - GUTTER:x + w + GUTTER] = True
        k = 16 / side
        rects.append([round(x * k, 4), round(y * k, 4), round((x + w) * k, 4), round((y + h) * k, 4)])
    # spread the faces' colours into the unused space, so a far-off mipmap never mixes in a
    # colour from nowhere; then keep it opaque when every face is, so the block stays solid
    for s in MAPS:
        img, have = sheets[s], used.copy()
        for _ in range(16):
            if have.all():
                break
            acc = np.zeros_like(img)
            cnt = np.zeros(have.shape, np.float32)
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                m = np.roll(have, (dy, dx), (0, 1))
                acc += np.roll(img, (dy, dx), (0, 1)) * m[..., None]
                cnt += m
            grow = ~have & (cnt > 0)
            img[grow] = acc[grow] / cnt[grow][:, None]
            have |= grow
    if solid:
        sheets[""][..., 3] = 255
    for s, img in sheets.items():
        Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(out_dir / f"{atlas_name}{s}.png")
    new = json.loads(json.dumps(spec))
    new["boxes"] = [list(b[:6]) + [atlas_name, {d: rects[i] for d, i in uvs.items()}] for b, uvs in boxes]
    new["ink"] = True
    return new


def main():
    global P
    ap = argparse.ArgumentParser()
    ap.add_argument("tex_dir", type=Path)
    ap.add_argument("out_dir", type=Path)
    ap.add_argument("specs_out", type=Path)
    ap.add_argument("--prefix", default="ink__")
    ap.add_argument("--px", type=int, default=P, help="atlas pixels per 1/16 block (the outline is one)")
    ap.add_argument("names", nargs="+")
    a = ap.parse_args()
    P = a.px
    blocks = json.load(open(HERE / "blocks.json"))["blocks"]
    source = Source(a.tex_dir)
    out = {}
    for name in a.names:
        spec = blocks[name]
        if spec.get("model") != "boxes":
            print("skip", name, "(not a boxes model)")
            continue
        out[a.prefix + name] = ink_block(name, spec, source, a.out_dir, f"ink_{name}", frozenset(joins(name, blocks)))
    json.dump({"blocks": out}, open(a.specs_out, "w"))
    print(len(out), "blocks inked")


if __name__ == "__main__":
    main()
