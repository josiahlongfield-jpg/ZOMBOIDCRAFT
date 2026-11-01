"""A small reader for binary FBX files (the format Project Zomboid's vehicle models ship in).

    from fbx import load
    meshes = load('Vehicles_X_Body.fbx')     # {name: dict(P=(n,3,3) triangles, UV=(n,3,2), M=4x4 world)}

Only what a static model needs: each mesh's triangles (polygons fanned) in the file's own units, its UVs
(v up, as stored) and the node's world transform (translation, rotation XYZ in degrees, scaling, parents
applied). P is already moved by that transform. Rigs, animation and materials are skipped.
"""
import struct
import zlib

import numpy as np

ARR = {b'f': ('<f4', 4), b'd': ('<f8', 8), b'l': ('<i8', 8), b'i': ('<i4', 4), b'b': ('<u1', 1)}


class Node:
    __slots__ = ('name', 'props', 'kids')

    def __init__(self, name, props, kids):
        self.name, self.props, self.kids = name, props, kids

    def find(self, name):
        for k in self.kids:
            if k.name == name:
                return k
        return None

    def all(self, name):
        return [k for k in self.kids if k.name == name]


def _props(b, at, n):
    out = []
    for _ in range(n):
        t = b[at:at + 1]
        at += 1
        if t == b'Y':
            out.append(struct.unpack_from('<h', b, at)[0]); at += 2
        elif t == b'C':
            out.append(bool(b[at])); at += 1
        elif t == b'I':
            out.append(struct.unpack_from('<i', b, at)[0]); at += 4
        elif t == b'F':
            out.append(struct.unpack_from('<f', b, at)[0]); at += 4
        elif t == b'D':
            out.append(struct.unpack_from('<d', b, at)[0]); at += 8
        elif t == b'L':
            out.append(struct.unpack_from('<q', b, at)[0]); at += 8
        elif t in ARR:
            ln, enc, cl = struct.unpack_from('<III', b, at)
            at += 12
            raw = b[at:at + cl]
            at += cl
            if enc:
                raw = zlib.decompress(raw)
            dt, _ = ARR[t]
            out.append(np.frombuffer(raw, dt, ln))
        elif t in (b'S', b'R'):
            ln = struct.unpack_from('<I', b, at)[0]
            at += 4
            out.append(b[at:at + ln])
            at += ln
        else:
            raise ValueError(f'unknown FBX property type {t!r}')
    return out, at


def _node(b, at, wide):
    if wide:
        end, n, _ = struct.unpack_from('<QQQ', b, at)
        at += 24
    else:
        end, n, _ = struct.unpack_from('<III', b, at)
        at += 12
    if end == 0:
        return None, at
    ln = b[at]
    name = b[at + 1:at + 1 + ln].decode('latin1')
    at += 1 + ln
    props, at = _props(b, at, n)
    kids = []
    null = 25 if wide else 13
    while at < end:
        if end - at == null and not any(b[at:end]):
            at = end
            break
        k, at = _node(b, at, wide)
        if k is None:
            break
        kids.append(k)
    return Node(name, props, kids), end


def parse(path):
    b = open(path, 'rb').read()
    assert b[:20] == b'Kaydara FBX Binary  ', 'not a binary FBX'
    version = struct.unpack_from('<I', b, 23)[0]
    wide = version >= 7500
    at = 27
    top = []
    while at < len(b):
        k, at = _node(b, at, wide)
        if k is None:
            break
        top.append(k)
    return Node('', [], top), version


def _name(p):
    return p.split(b'\x00\x01')[0].decode('latin1') if isinstance(p, bytes) else str(p)


def _p70(node):
    out = {}
    p = node.find('Properties70') if node else None
    for k in (p.kids if p else []):
        key = _name(k.props[0])
        out[key] = k.props[4:]
    return out


def _rot(r):
    rx, ry, rz = np.radians(r)
    X = np.array([[1, 0, 0], [0, np.cos(rx), -np.sin(rx)], [0, np.sin(rx), np.cos(rx)]])
    Y = np.array([[np.cos(ry), 0, np.sin(ry)], [0, 1, 0], [-np.sin(ry), 0, np.cos(ry)]])
    Z = np.array([[np.cos(rz), -np.sin(rz), 0], [np.sin(rz), np.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X  # FBX eEulerXYZ: X first


def _local(model):
    p = _p70(model)
    t = np.array(p.get('Lcl Translation', (0, 0, 0)), float)
    r = np.array(p.get('Lcl Rotation', (0, 0, 0)), float)
    s = np.array(p.get('Lcl Scaling', (1, 1, 1)), float)
    pre = np.array(p.get('PreRotation', (0, 0, 0)), float)
    post = np.array(p.get('PostRotation', (0, 0, 0)), float)
    rp = np.array(p.get('RotationPivot', (0, 0, 0)), float)
    sp = np.array(p.get('ScalingPivot', (0, 0, 0)), float)
    ro = np.array(p.get('RotationOffset', (0, 0, 0)), float)
    so = np.array(p.get('ScalingOffset', (0, 0, 0)), float)

    def T(v):
        m = np.eye(4); m[:3, 3] = v; return m

    def R(v):
        m = np.eye(4); m[:3, :3] = _rot(v); return m

    S = np.diag([*s, 1.0])
    Rpost = np.eye(4); Rpost[:3, :3] = np.linalg.inv(_rot(post))
    return T(t) @ T(ro) @ T(rp) @ R(pre) @ R(r) @ Rpost @ T(-rp) @ T(so) @ T(sp) @ S @ T(-sp)


def _geom_tris(g):
    V = g.find('Vertices').props[0].reshape(-1, 3).astype(float)
    idx = g.find('PolygonVertexIndex').props[0].astype(np.int64)
    ends = idx < 0
    vi = np.where(ends, ~idx, idx)
    uvl = g.find('LayerElementUV')
    uv_per_pv = None
    if uvl is not None:
        U = uvl.find('UV').props[0].reshape(-1, 2).astype(float)
        mapping = uvl.find('MappingInformationType').props[0]
        ref = uvl.find('ReferenceInformationType').props[0]
        ui = uvl.find('UVIndex')
        if mapping == b'ByPolygonVertex':
            uv_per_pv = U[ui.props[0]] if (ref == b'IndexToDirect' and ui is not None) else U
        elif mapping in (b'ByVertice', b'ByVertex', b'ByControlPoint'):
            uv_per_pv = U[vi]
    # fan each polygon
    starts = np.r_[0, np.where(ends)[0][:-1] + 1]
    stops = np.where(ends)[0] + 1
    a, bb, c = [], [], []
    for s0, s1 in zip(starts, stops):
        for k in range(s0 + 1, s1 - 1):
            a.append(s0); bb.append(k); c.append(k + 1)
    pv = np.stack([a, bb, c], 1)
    P = V[vi[pv]]
    UV = uv_per_pv[pv] if uv_per_pv is not None else np.zeros(pv.shape + (2,))
    return P, UV


def load(path):
    root, _ = parse(path)
    objs = root.find('Objects')
    conns = root.find('Connections')
    models, geoms = {}, {}
    for o in objs.kids:
        if o.name == 'Model':
            models[o.props[0]] = o
        elif o.name == 'Geometry' and len(o.props) > 2 and o.props[2] == b'Mesh':
            geoms[o.props[0]] = o
    parent, geo_of = {}, {}
    for c in conns.kids:
        if c.props[0] != b'OO':
            continue
        child, par = c.props[1], c.props[2]
        if child in geoms and par in models:
            geo_of[par] = child
        elif child in models:
            parent[child] = par
    world = {}

    def W(mid):
        if mid in world:
            return world[mid]
        m = _local(models[mid])
        p = parent.get(mid)
        if p in models:
            m = W(p) @ m
        world[mid] = m
        return m

    out = {}
    for mid, gid in geo_of.items():
        P, UV = _geom_tris(geoms[gid])
        p70 = _p70(models[mid])
        # geometric (pivot-free) offset of the mesh within its node
        gt = np.array(p70.get('GeometricTranslation', (0, 0, 0)), float)
        gr = np.array(p70.get('GeometricRotation', (0, 0, 0)), float)
        gs = np.array(p70.get('GeometricScaling', (1, 1, 1)), float)
        G = np.eye(4)
        G[:3, :3] = _rot(gr) @ np.diag(gs)
        G[:3, 3] = gt
        M = W(mid) @ G
        Q = P @ M[:3, :3].T + M[:3, 3]
        name = _name(models[mid].props[1])
        out[name] = dict(P=Q, UV=UV, M=M, raw=P)
    return out


if __name__ == '__main__':
    import sys
    for f in sys.argv[1:]:
        print(f)
        for n, m in load(f).items():
            lo, hi = m['P'].reshape(-1, 3).min(0), m['P'].reshape(-1, 3).max(0)
            print(f'  {n:40s} {len(m["P"]):6d} tris  {np.round(lo, 2)} .. {np.round(hi, 2)}')
