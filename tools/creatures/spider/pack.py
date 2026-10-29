"""
Packs the Spider 7 creature for the game: assets/zomboidcraft/creatures/spider.zsp.

Inputs (made in headless Chromium with three.js, see README.md):
  spider.glb  - the pack's mesh and its 30 clips (export.html: FBX -> GLB, Walk_Right rebuilt as a mirror of Walk_left)
  bake.json   - bake.html: the out space (metres, +y up, +z toward the head, +x its left, feet on y = 0), each bone's
                inverse bind and every clip's bone transforms at 30 fps
  tex/        - the pack's textures cut to 2K JPEG (body_<skin>_c, skull_<skin>_c, body_red_e, skull_red_e)

The mesh is cut from 157k vertices to about 1/6 with quadric decimation. Each new corner takes its texture position and
bone weights from the original triangle nearest it within the same UV island as the triangle it belongs to, so seams
don't smear. Bone transforms are stored as a quaternion and a position (millimetres) per bone per frame.

python3 -I pack.py <spider.glb> <bake.json> <tex dir> <out.zsp> [body skin] [skull skin]
"""
import base64, io, json, struct, sys, zlib

import fast_simplification
import numpy as np
from pygltflib import GLTF2
from scipy.spatial import cKDTree

KEEP = 0.35


def accessor(g, blob, i):
    a = g.accessors[i]
    v = g.bufferViews[a.bufferView]
    comps = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a.type]
    dt = {5126: np.float32, 5125: np.uint32, 5123: np.uint16, 5121: np.uint8}[a.componentType]
    off = (v.byteOffset or 0) + (a.byteOffset or 0)
    stride = v.byteStride or comps * np.dtype(dt).itemsize
    raw = np.frombuffer(blob, dtype=np.uint8, count=stride * (a.count - 1) + comps * np.dtype(dt).itemsize, offset=off)
    out = np.lib.stride_tricks.as_strided(raw.view(dt) if stride == comps * np.dtype(dt).itemsize else raw,
                                          shape=(a.count, comps)) if stride == comps * np.dtype(dt).itemsize else None
    if out is None:
        rows = [np.frombuffer(blob, dtype=dt, count=comps, offset=off + r * stride) for r in range(a.count)]
        out = np.array(rows)
    out = np.array(out, dtype=np.float64 if dt == np.float32 else np.int64)
    if a.normalized and dt in (np.uint8, np.uint16):
        out = out / (255.0 if dt == np.uint8 else 65535.0)
    return out


def islands(tris, nverts):
    """UV islands: triangles joined through shared vertex indices (seams split vertices, so they part islands)."""
    parent = np.arange(nverts)

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for a, b, c in tris:
        ra, rb, rc = find(a), find(b), find(c)
        parent[rb] = ra
        parent[find(rc)] = ra
    return np.array([find(t[0]) for t in tris])


def closest_on_tri(p, a, b, c):
    """Barycentric coordinates of the point on triangle abc nearest p (vectorised over rows)."""
    ab, ac, ap = b - a, c - a, p - a
    d00 = (ab * ab).sum(1); d01 = (ab * ac).sum(1); d11 = (ac * ac).sum(1)
    d20 = (ap * ab).sum(1); d21 = (ap * ac).sum(1)
    den = d00 * d11 - d01 * d01
    den[den == 0] = 1e-12
    v = (d11 * d20 - d01 * d21) / den
    w = (d00 * d21 - d01 * d20) / den
    v = np.clip(v, 0, 1); w = np.clip(w, 0, 1)
    s = v + w
    over = s > 1
    v[over] /= s[over]; w[over] /= s[over]
    return np.stack([1 - v - w, v, w], 1)


def decimate(P, UV, J, Wt, tris):
    """One material's mesh, cut down: positions, uvs, joints, weights and triangles of the result."""
    # weld by position for the decimator
    key, inv = np.unique(np.round(P, 5), axis=0, return_inverse=True)
    inv = inv.ravel()
    wt = inv[tris]
    wt = wt[(wt[:, 0] != wt[:, 1]) & (wt[:, 1] != wt[:, 2]) & (wt[:, 0] != wt[:, 2])]
    pts, faces = fast_simplification.simplify(key.astype(np.float32), wt.astype(np.int32), 1 - KEEP)
    # weld by position + uv first, so islands follow real uv seams even if the source split every corner
    _, wuv = np.unique(np.round(np.hstack([P, UV]), 5), axis=0, return_inverse=True)
    isl = islands(wuv.ravel()[tris], len(P))
    print("  islands", len(set(isl.tolist())))
    cen = P[tris].mean(1)
    tree = cKDTree(cen)
    by_island = {}
    for i, k in enumerate(isl):
        by_island.setdefault(k, []).append(i)
    by_island = {k: (np.array(v), cKDTree(cen[v])) for k, v in by_island.items()}
    out_p, out_uv, out_j, out_w, out_t, seen = [], [], [], [], [], {}
    fc = pts[faces].mean(1)
    _, near = tree.query(fc)
    for f, face in enumerate(faces):
        ids, kt = by_island[isl[near[f]]]
        corner = pts[face]
        _, k = kt.query(corner, k=min(4, len(ids)))
        k = np.asarray(k).reshape(3, -1)
        cand = ids[k]                                    # 3 x 4 candidate original triangles
        best = []
        for c in range(3):
            tri = tris[cand[c]]
            bc = closest_on_tri(np.repeat(corner[c:c + 1], len(tri), 0), P[tri[:, 0]], P[tri[:, 1]], P[tri[:, 2]])
            q = (bc[:, :, None] * P[tri]).sum(1)
            d = ((q - corner[c]) ** 2).sum(1)
            j = int(np.argmin(d))
            best.append((tri[j], bc[j]))
        idx = []
        for c in range(3):
            tri, bc = best[c]
            uv = (bc[:, None] * UV[tri]).sum(0)
            src = tri[int(np.argmax(bc))]
            k = (int(face[c]), round(float(uv[0]), 4), round(float(uv[1]), 4))
            if k not in seen:
                seen[k] = len(out_p)
                out_p.append(corner[c]); out_uv.append(uv); out_j.append(J[src]); out_w.append(Wt[src])
            idx.append(seen[k])
        out_t.append(idx)
    return np.array(out_p), np.array(out_uv), np.array(out_j), np.array(out_w), np.array(out_t)


def normals(P, T):
    """Smooth normals, shared across seams (by position)."""
    key, inv = np.unique(np.round(P, 4), axis=0, return_inverse=True)
    inv = inv.ravel()
    fn = np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]])
    acc = np.zeros((len(key), 3))
    for c in range(3):
        np.add.at(acc, inv[T[:, c]], fn)
    n = acc[inv]
    n /= np.maximum(1e-12, np.linalg.norm(n, axis=1))[:, None]
    return n


def quat(m):
    """Rotation of a 3x3 matrix as x, y, z, w."""
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = np.sqrt(t + 1) * 2
        q = [(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, s / 4]
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = np.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        q = [s / 4, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s, (m[2, 1] - m[1, 2]) / s]
    elif m[1, 1] > m[2, 2]:
        s = np.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        q = [(m[0, 1] + m[1, 0]) / s, s / 4, (m[1, 2] + m[2, 1]) / s, (m[0, 2] - m[2, 0]) / s]
    else:
        s = np.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        q = [(m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, s / 4, (m[1, 0] - m[0, 1]) / s]
    q = np.array(q)
    return q / np.linalg.norm(q)


def scramble(data, model):
    """xorshift32 keyed on the model id (as NpcAssets/CarAssets undo it)."""
    h = 0
    for ch in model:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    x = (0x3C6EF372 ^ h) & 0xFFFFFFFF or 1
    out = bytearray(data)
    for i in range(0, len(out), 4):
        x ^= (x << 13) & 0xFFFFFFFF
        x ^= x >> 17
        x ^= (x << 5) & 0xFFFFFFFF
        for j in range(4):
            if i + j < len(out):
                out[i + j] ^= (x >> (24 - 8 * j)) & 255
    return bytes(out)


def main():
    glb, bake_path, tex, out_path = sys.argv[1:5]
    body_skin = sys.argv[5] if len(sys.argv) > 5 else "1"
    skull_skin = sys.argv[6] if len(sys.argv) > 6 else "1"
    g = GLTF2().load(glb)
    blob = g.binary_blob()
    bake = json.load(open(bake_path))
    prims = g.meshes[0].primitives
    at = prims[0].attributes
    P = accessor(g, blob, at.POSITION)
    M = np.array(bake["meshToOut"]).reshape(4, 4).T
    P = P @ M[:3, :3].T + M[:3, 3]
    UV = accessor(g, blob, at.TEXCOORD_0)
    J = accessor(g, blob, at.JOINTS_0)
    Wt = accessor(g, blob, at.WEIGHTS_0)
    groups = []
    for prim in prims:
        tris = accessor(g, blob, prim.indices).reshape(-1, 3)
        p, uv, j, w, t = decimate(P, UV, J, Wt, tris)
        groups.append((g.materials[prim.material].name.lower(), p, uv, j, w, t))
        print(g.materials[prim.material].name, len(tris), "->", len(t), "triangles,", len(p), "vertices")
    buf = io.BytesIO()
    W = lambda fmt, *v: buf.write(struct.pack(">" + fmt, *v))
    def utf(s):
        b = s.encode("utf-8"); W("H", len(b)); buf.write(b)
    buf.write(b"ZSPD"); W("i", 1)
    W("i", len(groups))
    for name, p, uv, j, w, t in groups:
        utf(name)
        n = normals(p, t)
        W("i", len(p))
        for v in range(len(p)):
            W("fff", *p[v])
            nn = np.clip(np.round(n[v] * 127), -127, 127).astype(int)
            W("bbb", *nn)
            W("HH", int(np.clip(uv[v][0], 0, 1) * 65535), int(np.clip(uv[v][1], 0, 1) * 65535))
            ww = w[v] / max(1e-9, w[v].sum())
            q = np.round(ww * 255).astype(int)
            q[int(np.argmax(q))] += 255 - q.sum()
            W("BBBB", *[int(x) for x in j[v]])
            W("BBBB", *[int(x) for x in q])
        W("i", len(t))
        for tri in t:
            W("iii", *[int(x) for x in tri])
    looks = [("body", f"{tex}/body_{body_skin}_c.jpg"), ("skull", f"{tex}/skull_{skull_skin}_c.jpg"),
             ("body_glow", f"{tex}/body_red_e.jpg"), ("skull_glow", f"{tex}/skull_red_e.jpg")]
    W("i", len(looks))
    for name, path in looks:
        data = open(path, "rb").read()
        utf(name); W("i", len(data)); buf.write(data)
    bones = bake["bones"]
    W("i", len(bones))
    for b, inv in zip(bones, bake["inv"]):
        utf(b["name"]); W("b", b["parent"])
        m = np.array(inv).reshape(4, 4).T
        W("12f", *m[:3, :4].ravel())
    W("i", len(bake["clips"]))
    for clip in bake["clips"]:
        utf(clip["name"]); W("i", clip["frames"]); W("f", 30.0)
        arr = np.frombuffer(base64.b64decode(clip["data"]), dtype=np.float32).reshape(clip["frames"], len(bones), 3, 4)
        for f in range(clip["frames"]):
            for b in range(len(bones)):
                m = arr[f, b]
                q = quat(m[:, :3])
                W("4h", *[int(round(x * 32767)) for x in q])
                W("3h", *[int(np.clip(round(x * 1000), -32767, 32767)) for x in m[:, 3]])
    data = zlib.compress(buf.getvalue(), 9)
    open(out_path, "wb").write(scramble(data, "spider"))
    print("wrote", out_path, len(data), "bytes")


if __name__ == "__main__":
    main()
