#!/usr/bin/env python3
"""Cuts the heavy bought meshes down to about a third of their triangles (Josiah 2026-10-30: a /zc profile showed the
guns, up to 32,000 triangles drawn twice over for the ink, taking a quarter of a city frame).

Run after pack.py:  python3 tools/meshguns/lod.py [ID ...] [--dry] [--sheet DIR]
With no ids it does every .zmesh over MIN_TRIS. Each part is cut by collapsing corners onto their neighbours (see cut),
so the paint stays where it was and uv seams stay shut. lod_done.json remembers what was cut (by id: triangles after), so a second run leaves it
alone; pack.py rewriting a mesh changes its count and it's cut again.

Needs: pip install meshoptimizer numpy pillow.  --sheet DIR renders each mesh before and after, side by side.
"""
import io
import json
import os
import struct
import sys
import zlib

import numpy as np
import meshoptimizer

HERE = os.path.dirname(os.path.abspath(__file__))
MESHES = os.path.normpath(os.path.join(HERE, '../../src/main/resources/assets/zomboidcraft/meshes'))
DONE = os.path.join(HERE, 'lod_done.json')
MIN_TRIS = 6000      # meshes under this (pistols, small sights) are left whole: they sit closest to the eye
KEEP = 1 / 3         # share of triangles kept
MAX_ERROR = 0.15     # how far (item pixels) the surface may move; a gun is 16-40 pixels long
FLOOR = 2500         # never cut a mesh below this


def jhash(s):
    h = 0
    for c in s:
        h = (31 * h + ord(c)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= 1 << 31 else h


def scramble(data, mid):
    """CarAssets.unscramble: xorshift32 seeded from the id; the same both ways."""
    x = (0x5A0C4A55 ^ jhash(mid)) & 0xFFFFFFFF or 1
    a = np.frombuffer(data + b'\0' * (-len(data) % 4), dtype='>u4').copy()
    ks = np.empty(len(a), dtype=np.uint32)
    for i in range(len(a)):
        x ^= (x << 13) & 0xFFFFFFFF
        x ^= x >> 17
        x ^= (x << 5) & 0xFFFFFFFF
        ks[i] = x
    return (a ^ ks).astype('>u4').tobytes()[:len(data)]


CORNER = np.dtype([('p', '>i2', 3), ('uv', '>u2', 2), ('s', 'i1', 3)])


def read(mid):
    raw = open(os.path.join(MESHES, mid + '.zmesh'), 'rb').read()
    d = zlib.decompress(scramble(raw, mid))
    assert d[:4] == b'ZMSH' and struct.unpack_from('>i', d, 4)[0] == 3
    outline, = struct.unpack_from('>f', d, 8)
    n, = struct.unpack_from('>i', d, 12)
    png = d[16:16 + n]
    o = 16 + n
    count, = struct.unpack_from('>i', d, o)
    o += 4
    parts = []
    for _ in range(count):
        ln, = struct.unpack_from('>H', d, o)
        name = d[o + 2:o + 2 + ln].decode()
        o += 2 + ln
        t, = struct.unpack_from('>i', d, o)
        o += 4
        c = np.frombuffer(d, dtype=CORNER, count=t * 3, offset=o)
        o += t * 3 * CORNER.itemsize
        parts.append((name, c['p'].reshape(t, 3, 3).astype(np.int32), c['uv'].reshape(t, 3, 2).astype(np.int32),
                      c['s'].reshape(t, 3, 3).astype(np.int32)))
    return outline, png, parts


def write(mid, outline, png, parts):
    b = io.BytesIO()
    b.write(b'ZMSH' + struct.pack('>i', 3) + struct.pack('>f', outline) + struct.pack('>i', len(png)) + png)
    b.write(struct.pack('>i', len(parts)))
    for name, p, uv, s in parts:
        nb = name.encode()
        b.write(struct.pack('>H', len(nb)) + nb + struct.pack('>i', len(p)))
        c = np.empty(len(p) * 3, dtype=CORNER)
        c['p'] = p.reshape(-1, 3)
        c['uv'] = uv.reshape(-1, 2)
        c['s'] = s.reshape(-1, 3)
        b.write(c.tobytes())
    open(os.path.join(MESHES, mid + '.zmesh'), 'wb').write(scramble(zlib.compress(b.getvalue(), 9), mid))


def cut(p, uv, s, target):
    """A part (int arrays as stored) cut to about target triangles by collapsing corners onto their neighbours
    (meshoptimizer): no corner moves and every kept one keeps its own uv and smooth normal, and a corner on a uv seam
    only slides along the seam, so the paint stays put and the seams stay shut."""
    t = len(p)
    key = np.concatenate([p.reshape(-1, 3), uv.reshape(-1, 2), s.reshape(-1, 3)], axis=1)
    rows, inv = np.unique(key, axis=0, return_inverse=True)
    pos = np.ascontiguousarray(rows[:, :3], dtype=np.float32)
    idx = inv.reshape(-1).astype(np.uint32)
    dst = np.zeros(len(idx), dtype=np.uint32)
    n = meshoptimizer.simplify(dst, idx, pos, target_index_count=target * 3, target_error=MAX_ERROR * 1000,
                               options=meshoptimizer.SIMPLIFY_ERROR_ABSOLUTE)
    if n == 0 or n >= len(idx):
        return p, uv, s
    f = rows[dst[:n].reshape(-1, 3)]
    return f[:, :, :3].astype(np.int32), f[:, :, 3:5].astype(np.int32), f[:, :, 5:].astype(np.int32)


def cut_part(p, uv, s, keep):
    if len(p) < 200:
        return p, uv, s
    return cut(p, uv, s, int(round(len(p) * keep)))


def sheet(path, png, before, after):
    """The mesh lit and textured, side and three-quarter views, before (top) and after (bottom)."""
    from PIL import Image
    tex = np.asarray(Image.open(io.BytesIO(png)).convert('RGB')).astype(np.float64)
    th, tw = tex.shape[:2]
    W, H = 900, 380
    views = ((np.pi / 2, 0.0), (np.pi / 2 + 0.6, 0.35))
    allp = np.concatenate([q[1].reshape(-1, 3) for q in before]).astype(np.float64) / 1000
    lo, hi = allp.min(0), allp.max(0)
    mid = (lo + hi) / 2
    scale = 0.8 * W / max(np.linalg.norm(hi - lo), 1e-6)
    img = Image.new('RGB', (W * len(views), H * 2), (215, 215, 220))
    for row, parts in enumerate((before, after)):
        for vi, (yaw, pitch) in enumerate(views):
            cy, sy, cp, sp = np.cos(yaw), np.sin(yaw), np.cos(pitch), np.sin(pitch)
            R = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]])
            buf = np.full((H, W), np.inf)
            col = np.zeros((H, W, 3)) + 218
            for _, p, uv, _s in parts:
                P = (p.reshape(-1, 3) / 1000 - mid) @ R.T
                X = (P[:, 0] * scale + W / 2).reshape(-1, 3)
                Y = (-P[:, 1] * scale + H / 2).reshape(-1, 3)
                Z = P[:, 2].reshape(-1, 3)
                U = (uv.reshape(-1, 2) / 65535.0).reshape(-1, 3, 2)
                N = np.cross(P.reshape(-1, 3, 3)[:, 1] - P.reshape(-1, 3, 3)[:, 0],
                             P.reshape(-1, 3, 3)[:, 2] - P.reshape(-1, 3, 3)[:, 0])
                N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
                lit = 0.5 + 0.5 * np.abs(N @ np.array([0.3, 0.6, -0.75]))
                for t in range(len(X)):
                    x0, x1 = int(max(0, X[t].min())), int(min(W - 1, X[t].max()) + 1)
                    y0, y1 = int(max(0, Y[t].min())), int(min(H - 1, Y[t].max()) + 1)
                    if x1 <= x0 or y1 <= y0:
                        continue
                    xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
                    (ax, bx, cx), (ay, by, cyy) = X[t], Y[t]
                    den = (by - cyy) * (ax - cx) + (cx - bx) * (ay - cyy)
                    if abs(den) < 1e-9:
                        continue
                    w0 = ((by - cyy) * (xs - cx) + (cx - bx) * (ys - cyy)) / den
                    w1 = ((cyy - ay) * (xs - cx) + (ax - cx) * (ys - cyy)) / den
                    w2 = 1 - w0 - w1
                    ins = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
                    if not ins.any():
                        continue
                    z = w0 * Z[t, 0] + w1 * Z[t, 1] + w2 * Z[t, 2]
                    sub = buf[y0:y1, x0:x1]
                    m = ins & (z < sub)
                    if not m.any():
                        continue
                    sub[m] = z[m]
                    u = w0 * U[t, 0, 0] + w1 * U[t, 1, 0] + w2 * U[t, 2, 0]
                    v = w0 * U[t, 0, 1] + w1 * U[t, 1, 1] + w2 * U[t, 2, 1]
                    tx = np.clip((u * tw).astype(int), 0, tw - 1)
                    ty = np.clip((v * th).astype(int), 0, th - 1)
                    c = tex[ty, tx] * lit[t]
                    col[y0:y1, x0:x1][m] = c[m]
            img.paste(Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)), (vi * W, row * H))
    img.save(path)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    sheet_dir = sys.argv[sys.argv.index('--sheet') + 1] if '--sheet' in sys.argv else None
    if sheet_dir in args:
        args.remove(sheet_dir)
    done = json.load(open(DONE)) if os.path.exists(DONE) else {}
    if args:
        ids = args
    else:
        ids = sorted(os.path.relpath(os.path.join(dp, f), MESHES)[:-6].replace(os.sep, '/')
                     for dp, _, fs in os.walk(MESHES) for f in fs if f.endswith('.zmesh'))
    for mid in ids:
        outline, png, parts = read(mid)
        total = sum(len(p[1]) for p in parts)
        if total < MIN_TRIS or done.get(mid) == total:
            continue
        keep = max(KEEP, FLOOR / total)
        cut = [(name,) + cut_part(p, uv, s, keep) for name, p, uv, s in parts]
        after = sum(len(c[1]) for c in cut)
        print(f'{mid}: {total} -> {after} ({", ".join(f"{c[0]} {len(o[1])}->{len(c[1])}" for c, o in zip(cut, parts))})')
        if sheet_dir:
            os.makedirs(sheet_dir, exist_ok=True)
            sheet(os.path.join(sheet_dir, mid.replace('/', '_') + '.png'), png, parts, cut)
        if '--dry' not in sys.argv:
            write(mid, outline, png, cut)
            done[mid] = after
    if '--dry' not in sys.argv:
        json.dump(done, open(DONE, 'w'), indent=1, sort_keys=True)


main()
