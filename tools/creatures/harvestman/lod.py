#!/usr/bin/env python3
"""Cuts the Harvestman's game model down to three levels of detail, so a few of them don't sink the frame rate.

Run after pack.py:  python3 tools/creatures/harvestman/lod.py
Reads harvestman.zhm, takes its near level (the full preview mesh, ~57,000 triangles drawn) and writes the file back
with three levels made from it: near, middle and far (budgets per part in BUDGET, ~12,000 / ~4,000 / ~1,600
triangles drawn for a whole creature). Each part is simplified whole (layered plates keep their order); the near level keeps open
edges, the others may close them. Each simplified corner takes its colour and normal from the original corner at that spot that
faces most nearly the same way, so baked shading and hard edges survive.

Needs: pip install pyfqmr scipy numpy.  --sheet out.png renders every level side by side to check them.
"""
import os
import struct
import sys
import zlib

import numpy as np
import pyfqmr
from scipy.spatial import cKDTree

HERE = os.path.dirname(os.path.abspath(__file__))
ZHM = os.path.join(HERE, '../../../src/main/resources/assets/zomboidcraft/creatures/harvestman.zhm')

# triangles per part at each level: near, middle, far
BUDGET = {
    'body': (5200, 1800, 700),
    'armsHunt': (700, 260, 100),
    'armsFeed': (700, 260, 100),
    'thighF': (500, 160, 60),
    'thighR': (500, 160, 60),
    'lowF': (1100, 360, 140),
    'lowR': (1100, 360, 140),
}


def read(path):
    d = zlib.decompress(open(path, 'rb').read())
    assert d[:4] == b'ZHM1'
    o = 4
    legs, = struct.unpack_from('>i', d, o)
    head = d[:o + 4 + legs * 28]
    o += 4 + legs * 28
    nlev, = struct.unpack_from('>i', d, o)
    o += 4
    levels = []
    for _ in range(nlev):
        n, = struct.unpack_from('>i', d, o)
        o += 4
        parts = []
        for _ in range(n):
            ln, = struct.unpack_from('>h', d, o)
            o += 2
            name = d[o:o + ln].decode()
            o += ln
            t, = struct.unpack_from('>i', d, o)
            o += 4
            raw = np.frombuffer(d, dtype=np.dtype([('p', '>f4', 3), ('n', 'i1', 3), ('c', 'u1', 3), ('g', 'u1')]),
                                count=t * 3, offset=o)
            o += t * 3 * 19
            parts.append((name, raw['p'].astype(np.float64).reshape(t, 3, 3), raw['n'].astype(np.float64).reshape(t, 3, 3) / 127,
                          raw['c'].reshape(t, 3, 3).copy(), raw['g'].reshape(t, 3)[:, 0].copy()))
        levels.append(parts)
    return head, levels


def simplify(p, n, c, target, border=True):
    """p, n, c: (t, 3, k) corners (c may carry extra channels). Returns the same for about `target` triangles."""
    t = len(p)
    if t <= target or target <= 0:
        return p, n, c
    flat = p.reshape(-1, 3)
    key = np.round(flat / 1e-4).astype(np.int64)
    uniq, inv = np.unique(key, axis=0, return_inverse=True)
    inv = inv.reshape(-1)
    verts = np.zeros((len(uniq), 3))
    np.add.at(verts, inv, flat)
    verts /= np.bincount(inv, minlength=len(uniq))[:, None]
    faces = inv.reshape(t, 3)
    s = pyfqmr.Simplify()
    s.setMesh(verts, faces)
    s.simplify_mesh(target_count=target, aggressiveness=5, preserve_border=border, verbose=False)
    nv, nf, _ = s.getMesh()
    if len(nf) == 0:
        return p, n, c
    # each new corner: the original corner nearest it that faces most nearly as the new triangle does
    tree = cKDTree(flat)
    out_p = nv[nf]
    e1, e2 = out_p[:, 1] - out_p[:, 0], out_p[:, 2] - out_p[:, 0]
    fn = np.cross(e1, e2)
    fn /= np.maximum(np.linalg.norm(fn, axis=1, keepdims=True), 1e-12)
    on = n.reshape(-1, 3)
    oc = c.reshape(-1, c.shape[2])
    out_n = np.zeros_like(out_p)
    out_c = np.zeros((len(nf), 3, oc.shape[1]), dtype=np.uint8)
    dist, idx = tree.query(out_p.reshape(-1, 3), k=8)
    idx = idx.reshape(len(nf), 3, 8)
    dist = dist.reshape(len(nf), 3, 8)
    for k in range(3):
        cand_n = on[idx[:, k]]                                   # (f, 8, 3)
        facing = np.einsum('fkj,fj->fk', cand_n, fn)
        score = facing - 4 * (dist[:, k] - dist[:, k, :1])      # nearest first, then the one facing our way
        best = idx[:, k][np.arange(len(nf)), np.argmax(score, axis=1)]
        out_n[:, k] = on[best]
        out_c[:, k] = oc[best]
    # wind each new triangle the way its normals point, as pack.py does, so back faces can be culled
    flip = np.einsum('fj,fj->f', fn, out_n.sum(axis=1)) < 0
    out_p[flip] = out_p[flip][:, [0, 2, 1]]
    out_n[flip] = out_n[flip][:, [0, 2, 1]]
    out_c[flip] = out_c[flip][:, [0, 2, 1]]
    return out_p, out_n, out_c


def level(parts, li):
    b = bytearray(struct.pack('>i', len(parts)))
    total = 0
    for name, p, n, c, g in parts:
        want = BUDGET.get(name, (len(p),) * 3)[li]
        # simplified whole, so layered plates keep their order; the glow rides along as a fourth channel
        c4 = np.concatenate([c, np.repeat(g[:, None], 3, axis=1)[..., None]], axis=2)
        sp, sn, sc = simplify(p, n, c4, want, border=li == 0)
        pieces = [(sp, sn, sc[..., :3], (sc[..., 3].max(axis=1) != 0).astype(np.uint8))]
        tris = sum(len(x[0]) for x in pieces)
        total += tris
        nb = name.encode()
        b += struct.pack('>h', len(nb)) + nb + struct.pack('>i', tris)
        for sp, sn, sc, flags in pieces:
            q = np.clip(np.round(sn * 127), -127, 127).astype(np.int8)
            for t in range(len(sp)):
                for k in range(3):
                    b += struct.pack('>fffbbbBBBB', *sp[t, k], *q[t, k], *sc[t, k], int(flags[t]))
        print(f'  level {li} {name}: {len(p)} -> {tris}')
    return b, total


def sheet(path, levels):
    """Each level's body and a front thigh and lower leg, shaded by their baked colours, side by side."""
    from PIL import Image
    W, H = 360, 300
    img = Image.new('RGB', (W * len(levels), H * 2), (200, 200, 205))
    for li, parts in enumerate(levels):
        for row, (yaw, pitch) in enumerate(((0.6, 0.35), (2.4, 0.2))):
            buf = np.full((H, W), np.inf)
            col = np.zeros((H, W, 3), dtype=np.uint8) + np.array([200, 200, 205], dtype=np.uint8)
            cy, sy, cp, sp_ = np.cos(yaw), np.sin(yaw), np.cos(pitch), np.sin(pitch)
            R = np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]]) @ np.eye(3)
            R = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]]) @ R
            for name, p, n, c, g in parts:
                if name not in ('body', 'armsHunt'):
                    continue
                q = p.reshape(-1, 3) @ R.T
                sx = (q[:, 0] * 85 + W / 2).reshape(-1, 3)
                sy2 = (-q[:, 1] * 85 + H / 2).reshape(-1, 3)
                sz = q[:, 2].reshape(-1, 3)
                for t in range(len(sx)):
                    x0, x1 = int(max(0, sx[t].min())), int(min(W - 1, sx[t].max()))
                    y0, y1 = int(max(0, sy2[t].min())), int(min(H - 1, sy2[t].max()))
                    if x1 < x0 or y1 < y0:
                        continue
                    xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                    (ax, bx, cx), (ay, by, cy2) = sx[t], sy2[t]
                    den = (by - cy2) * (ax - cx) + (cx - bx) * (ay - cy2)
                    if abs(den) < 1e-9:
                        continue
                    w0 = ((by - cy2) * (xs - cx) + (cx - bx) * (ys - cy2)) / den
                    w1 = ((cy2 - ay) * (xs - cx) + (ax - cx) * (ys - cy2)) / den
                    w2 = 1 - w0 - w1
                    inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
                    if not inside.any():
                        continue
                    z = w0 * sz[t, 0] + w1 * sz[t, 1] + w2 * sz[t, 2]
                    sub = buf[y0:y1 + 1, x0:x1 + 1]
                    m = inside & (z < sub)
                    sub[m] = z[m]
                    cc = (w0[..., None] * c[t, 0] + w1[..., None] * c[t, 1] + w2[..., None] * c[t, 2])
                    nn = (w0[..., None] * n[t, 0] + w1[..., None] * n[t, 1] + w2[..., None] * n[t, 2]) @ R.T
                    lit = 0.45 + 0.55 * np.clip(nn @ np.array([0.3, 0.7, -0.6]) / np.maximum(np.linalg.norm(nn, axis=-1), 1e-6), 0, 1)
                    shade = np.clip(cc * lit[..., None] * 3 + 25, 0, 255).astype(np.uint8)
                    col[y0:y1 + 1, x0:x1 + 1][m] = shade[m]
            img.paste(Image.fromarray(col), (li * W, row * H))
    img.save(path)
    print('sheet', path)


def main():
    head, levels = read(ZHM)
    if len(levels) != 2 and '--force' not in sys.argv:
        sys.exit('harvestman.zhm has %d levels: already cut down (run pack.py first, or --force)' % len(levels))
    full = levels[0]
    out = bytearray(head) + struct.pack('>i', 3)
    made = []
    for li in range(3):
        b, total = level(full, li)
        print(f'level {li}: {total} triangles in the file')
        out += b
        made.append(b)
    raw = bytes(out)
    if '--dry' not in sys.argv:
        open(ZHM, 'wb').write(zlib.compress(raw, 9))
        print('wrote', os.path.normpath(ZHM), len(raw), '->', os.path.getsize(ZHM), 'bytes')
    if '--sheet' in sys.argv:
        tmp = os.path.join(os.path.dirname(sys.argv[sys.argv.index('--sheet') + 1]) or '.', '_lod.zhm')
        open(tmp, 'wb').write(zlib.compress(raw, 9))
        _, lv = read(tmp)
        os.remove(tmp)
        sheet(sys.argv[sys.argv.index('--sheet') + 1], [full] + lv)


main()
