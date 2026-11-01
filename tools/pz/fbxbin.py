"""Minimal binary FBX reader (7.x) -> node tree."""
import struct, zlib
import numpy as np

class Node:
    def __init__(s, name, props, kids): s.name, s.props, s.kids = name, props, kids
    def find(s, name): return [k for k in s.kids if k.name == name]
    def one(s, name):
        r = s.find(name); return r[0] if r else None
    def __repr__(s): return f'<{s.name} {[p if not isinstance(p,np.ndarray) else p.shape for p in s.props][:4]} kids={len(s.kids)}>'

def read(path):
    b = open(path, 'rb').read()
    ver = struct.unpack_from('<I', b, 23)[0]
    big = ver >= 7500
    def prop(o):
        t = chr(b[o]); o += 1
        if t == 'Y': return struct.unpack_from('<h', b, o)[0], o + 2
        if t == 'C': return b[o] != 0, o + 1
        if t == 'I': return struct.unpack_from('<i', b, o)[0], o + 4
        if t == 'F': return struct.unpack_from('<f', b, o)[0], o + 4
        if t == 'D': return struct.unpack_from('<d', b, o)[0], o + 8
        if t == 'L': return struct.unpack_from('<q', b, o)[0], o + 8
        if t in 'SR':
            n = struct.unpack_from('<I', b, o)[0]; o += 4
            v = b[o:o + n]; return (v.decode('utf-8', 'replace') if t == 'S' else v), o + n
        if t in 'fdlib':
            n, enc, cl = struct.unpack_from('<III', b, o); o += 12
            raw = b[o:o + cl]; o += cl
            if enc == 1: raw = zlib.decompress(raw)
            dt = {'f': '<f4', 'd': '<f8', 'l': '<i8', 'i': '<i4', 'b': 'u1'}[t]
            return np.frombuffer(raw, dt, n), o
        raise ValueError(t)
    def node(o):
        if big: end, np_, pl = struct.unpack_from('<QQQ', b, o); o += 24
        else: end, np_, pl = struct.unpack_from('<III', b, o); o += 12
        nl = b[o]; o += 1
        if end == 0: return None, o
        name = b[o:o + nl].decode(); o += nl
        props = []
        for _ in range(np_):
            v, o = prop(o); props.append(v)
        kids = []
        while o < end:
            k, o = node(o)
            if k is None: break
            kids.append(k)
        return Node(name, props, kids), end
    o = 27; top = []
    while o < len(b) - 200:
        n, o2 = node(o)
        if n is None: break
        top.append(n); o = o2
    return Node('root', [], top), ver
