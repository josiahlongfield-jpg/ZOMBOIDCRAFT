"""Adds a lighter level of detail (level 1) to a built zombie pack: every mesh thinned to about a fifth of its triangles.

Josiah's rule: once the Zomboid zombies are in, no Minecraft-style body is ever seen. Past the nearest few the zombies are
drawn from these thinned meshes instead (same skeleton, same textures, same clips: ZedBodies draws level 1), so a
horde costs a fraction of the full bodies but still looks like Zomboid's. Mesh records are copied from the full ones, so
skin weights, UVs and normals are Zomboid's own; only where a vertex sits after a collapse changes.

    python3 -I tools/pz/zombie_lod.py PACK.zsk OUT.zsk [KEEP [FAR [CROWD]]]
    shares of triangles kept: level 1 (0.2), level 2 (0.07), level 3 (0.045, the far crowd past look.crowdLightDistance;
    clothes keep 0.2 there; 2026-11-08 preview performance/crowd-lod: lighter broke hands and heads)

Needs meshoptimizer (pip install meshoptimizer). The packs are local only, never in the repo or jar.
"""
import io, os, struct, sys, zlib
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'npc'))
from pack import scramble  # noqa: E402

REC = np.dtype([('p', '>f4', 3), ('n', 'i1', 3), ('uv', '>u2', 2), ('j', 'u1', 4), ('w', 'u1', 4)])
# pieces too small to be seen past the nearest few: left out of level 1 altogether
SMALL = ('Ring_', 'Earring', 'Necklace', 'WristWatch', 'Bracelet', 'NoseRing', 'BellyButton', 'Glasses', 'Eyes')


def simplify(out, idx, P, attrs, weights, target, error):
    """meshopt_simplifyWithAttributes, called straight (the binding passes its float bare, which ctypes refuses)."""
    import ctypes
    from meshoptimizer.simplifier import lib
    fp, up = ctypes.POINTER(ctypes.c_float), ctypes.POINTER(ctypes.c_uint)
    st = ctypes.c_size_t
    return lib.meshopt_simplifyWithAttributes(
        out.ctypes.data_as(up), idx.ctypes.data_as(up), st(len(idx)), P.ctypes.data_as(fp), st(len(P)), st(12),
        attrs.ctypes.data_as(fp), st(attrs.shape[1] * 4), weights.ctypes.data_as(fp), st(len(weights)),
        ctypes.POINTER(ctypes.c_ubyte)(), st(target), ctypes.c_float(error), ctypes.c_uint(1), fp())   # 1: lock borders


def thin(rec, tris, keep, joints, least=72):
    """meshoptimizer's simplifier, keeping Zomboid's own vertices (so UVs, normals and skin stay exact; it already
    knows UV seams). Weighed on normals, UVs and where each vertex's bones sit, so it never folds the two legs (or an
    arm and the side) into one: that's what made a thinned pair of trousers into a skirt. Open edges (a trouser hem,
    a sleeve, the hair's fringe) are locked where they are: pulled in, they left the skin cut away under the clothes
    showing as holes through the legs."""
    if len(tris) < 40:
        return rec, tris
    P = np.ascontiguousarray(rec['p'], dtype=np.float32)
    w = rec['w'].astype(np.float32) / 255.0
    w /= np.maximum(w.sum(1, keepdims=True), 1e-6)
    bone_at = (joints[rec['j'].astype(int)] * w[..., None]).sum(1)   # where its bones are, weighed
    attrs = np.ascontiguousarray(np.hstack([rec['n'].astype(np.float32) / 127.0, rec['uv'].astype(np.float32) / 65535.0,
                                            bone_at]), dtype=np.float32)
    weights = np.array([0.5, 0.5, 0.5, 1.0, 1.0, 4.0, 4.0, 4.0], dtype=np.float32)
    idx = np.ascontiguousarray(tris.astype(np.uint32).ravel())
    out = np.zeros_like(idx)
    target = max(least, int(len(idx) * keep) // 3 * 3)
    n = simplify(out, idx, P, attrs, weights, target, 1.0)
    used = out[:n]
    keep_v, remap = np.unique(used, return_inverse=True)
    return rec[keep_v].copy(), remap.reshape(-1, 3).astype('>i4')


def add_lod(src, dst, keep=0.2, far=0.07, crowd=0.045):
    mid_in, mid_out = os.path.basename(src)[:-4], os.path.basename(dst)[:-4]
    raw = zlib.decompress(scramble(open(src, 'rb').read(), mid_in))
    b = io.BytesIO(raw)
    u = lambda f: struct.unpack('>' + f, b.read(struct.calcsize('>' + f)))
    utf = lambda: b.read(u('H')[0]).decode()
    assert b.read(4) == b'ZSKN' and u('i')[0] == 1
    groups = []
    for _ in range(u('i')[0]):
        mesh, piece, tex = utf(), utf(), utf()
        flags, level, n = u('bbi')
        rec = np.frombuffer(b.read(n * 27), dtype=REC)
        tris = np.frombuffer(b.read(u('i')[0] * 12), '>i4').reshape(-1, 3)
        groups.append((mesh, piece, tex, flags, level, rec, tris))
    rest = b.read()
    # where each bone's joint is at rest: from the inverse bind matrices after the meshes and textures
    rb = io.BytesIO(rest)
    ru = lambda f: struct.unpack('>' + f, rb.read(struct.calcsize('>' + f)))
    for _ in range(ru('i')[0]):
        rb.read(ru('H')[0]); rb.read(ru('i')[0])
    joints = []
    for _ in range(ru('i')[0]):
        rb.read(ru('H')[0]); ru('b')
        m = np.eye(4); m[:3] = np.array(ru('12f')).reshape(3, 4)
        joints.append(np.linalg.inv(m)[:3, 3])
    joints = np.array(joints, dtype=np.float32)
    full = [g for g in groups if g[4] == 0]
    lods = []
    t0, t1, t2, t3 = 0, 0, 0, 0
    for mesh, piece, tex, flags, level, rec, tris in full:
        if any(piece.startswith(s) or mesh.startswith(s) for s in SMALL):
            continue
        # the clothes are small meshes already, and thinned hard they lose whole sleeves: they keep more than the body
        body = piece == 'body'
        r1, f1 = thin(rec, tris, keep if body else max(keep, 0.55), joints)
        lods.append((mesh, piece, tex, flags, 1, r1, f1))
        # level 2, the far crowd: still Zomboid's body in 3D (Josiah: no flat pictures, scopes would show them up)
        r2, f2 = thin(rec, tris, far if body else max(far, 0.35), joints, 36)
        lods.append((mesh, piece, tex, flags, 2, r2, f2))
        # level 3, the far crowd only where a figure is small on screen (never through a scope's zoom)
        r3, f3 = thin(rec, tris, crowd if body else max(crowd, 0.2), joints, 24)
        lods.append((mesh, piece, tex, flags, 3, r3, f3))
        t0 += len(tris); t1 += len(f1); t2 += len(f2); t3 += len(f3)
    out = io.BytesIO()
    out.write(b'ZSKN' + struct.pack('>i', 1))
    out.write(struct.pack('>i', len(full) + len(lods)))
    s = lambda x: struct.pack('>H', len(x.encode())) + x.encode()
    for mesh, piece, tex, flags, level, rec, tris in full + lods:
        out.write(s(mesh) + s(piece) + s(tex) + struct.pack('>bbi', flags, level, len(rec)))
        out.write(np.ascontiguousarray(rec, dtype=REC).tobytes())
        out.write(struct.pack('>i', len(tris)) + np.ascontiguousarray(tris, dtype='>i4').tobytes())
    out.write(rest)
    open(dst, 'wb').write(scramble(zlib.compress(out.getvalue(), 9), mid_out))
    print(f'{dst}: {len(full)} meshes, {t0} triangles; level 1 {t1}, level 2 {t2}, level 3 {t3}; {os.path.getsize(dst) // 1024} KB')


if __name__ == '__main__':
    add_lod(sys.argv[1], sys.argv[2], *(float(a) for a in sys.argv[3:6]))
