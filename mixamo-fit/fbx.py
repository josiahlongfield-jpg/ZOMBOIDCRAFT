"""Minimal binary FBX reader: node tree with properties (arrays decoded)."""
import struct, zlib, numpy as np

class Node:
    __slots__ = ("name", "props", "kids")
    def __init__(s, name, props, kids): s.name, s.props, s.kids = name, props, kids
    def find(s, name): return [k for k in s.kids if k.name == name]
    def one(s, name):
        r = s.find(name); return r[0] if r else None
    def __repr__(s): return f"Node({s.name},{s.props[:3]},{len(s.kids)})"

ARR = {b'f': ('<f', 4), b'd': ('<d', 8), b'l': ('<q', 8), b'i': ('<i', 4), b'b': ('<B', 1)}

def _prop(b, o):
    t = b[o:o+1]; o += 1
    if t == b'Y': return struct.unpack_from('<h', b, o)[0], o + 2
    if t == b'C': return b[o] != 0, o + 1
    if t == b'I': return struct.unpack_from('<i', b, o)[0], o + 4
    if t == b'F': return struct.unpack_from('<f', b, o)[0], o + 4
    if t == b'D': return struct.unpack_from('<d', b, o)[0], o + 8
    if t == b'L': return struct.unpack_from('<q', b, o)[0], o + 8
    if t in (b'S', b'R'):
        n = struct.unpack_from('<I', b, o)[0]; o += 4
        v = b[o:o+n]; o += n
        return (v.decode('utf-8', 'replace') if t == b'S' else v), o
    if t in ARR:
        n, enc, clen = struct.unpack_from('<III', b, o); o += 12
        raw = b[o:o+clen]; o += clen
        if enc == 1: raw = zlib.decompress(raw)
        fmt, sz = ARR[t]
        return np.frombuffer(raw, dtype=np.dtype(fmt), count=n).copy(), o
    raise ValueError(f"prop type {t} at {o}")

def _node(b, o, v):
    if v >= 7500:
        end, nprops, plen = struct.unpack_from('<QQQ', b, o); o += 24
    else:
        end, nprops, plen = struct.unpack_from('<III', b, o); o += 12
    nl = b[o]; o += 1
    name = b[o:o+nl].decode(); o += nl
    if end == 0: return None, o
    props = []
    po = o
    for _ in range(nprops):
        p, po = _prop(b, po); props.append(p)
    o += plen
    kids = []
    null = 25 if v >= 7500 else 13
    while o < end:
        if end - o == null: o = end; break
        k, o = _node(b, o, v)
        if k is None: break
        kids.append(k)
    return Node(name, props, kids), end

def load(path):
    b = open(path, 'rb').read()
    assert b[:20] == b'Kaydara FBX Binary  ', 'not binary FBX'
    v = struct.unpack_from('<I', b, 23)[0]
    o = 27; kids = []
    while o < len(b) - 200:
        k, o = _node(b, o, v)
        if k is None: break
        kids.append(k)
    return Node('root', [], kids), v
