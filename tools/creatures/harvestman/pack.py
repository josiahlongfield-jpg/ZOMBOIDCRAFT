#!/usr/bin/env python3
"""Packs the Harvestman's game model from the preview export (export.js writes near.json and far.json).

Writes src/main/resources/assets/zomboidcraft/creatures/harvestman.zhm, deflated:
  "ZHM1", int legs, per leg: hip x y z, a (thigh), lc (knee to point, straight), restA, restR   (floats)
  int levels, per level: int parts, per part: name (short length + utf-8), int triangles,
  then per vertex: float x y z, byte nx ny nz (x127), byte r g b (sRGB x255), byte flags (1 = glows).
Big-endian, as Java's DataInputStream reads it. The preview draws both sides of every triangle, so each one is
wound here to face the way its normals point (counter-clockwise from the front), and the game can cull back faces. Each part is in its own frame: the body's (z forward, y up; the arms and beard hunting, and closed in the feeding grip),
a thigh's (x from hip to knee) or a lower leg's (x along the straight line from knee to point, bowing toward +y).
"""
import json, struct, sys, zlib, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '../../../src/main/resources/assets/zomboidcraft/creatures/harvestman.zhm')
ORDER = ['body', 'armsHunt', 'armsFeed', 'thighF', 'thighR', 'lowF', 'lowR']


def level(data):
    b = bytearray(struct.pack('>i', len(ORDER)))
    for name in ORDER:
        v = data['parts'][name]
        n = len(v) // 30
        nb = name.encode()
        b += struct.pack('>h', len(nb)) + nb + struct.pack('>i', n)
        for t in range(0, len(v), 30):
            a, bb, c = v[t:t + 10], v[t + 10:t + 20], v[t + 20:t + 30]
            e1 = [bb[i] - a[i] for i in range(3)]
            e2 = [c[i] - a[i] for i in range(3)]
            g = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
            m = [a[3 + i] + bb[3 + i] + c[3 + i] for i in range(3)]
            corners = (a, bb, c) if sum(g[i] * m[i] for i in range(3)) >= 0 else (a, c, bb)
            for x, y, z, nx, ny, nz, r, g_, bl, glow in corners:
                b += vert(x, y, z, nx, ny, nz, r, g_, bl, glow)
    return b


def vert(x, y, z, nx, ny, nz, r, g, bl, glow):
    q = lambda f: max(-127, min(127, round(f * 127)))
    c = lambda f: max(0, min(255, round(f * 255)))
    return struct.pack('>fffbbbBBBB', x, y, z, q(nx), q(ny), q(nz), c(r), c(g), c(bl), 1 if glow else 0)


def main():
    near = json.load(open(sys.argv[1] if len(sys.argv) > 1 else 'near.json'))
    far = json.load(open(sys.argv[2] if len(sys.argv) > 2 else 'far.json'))
    b = bytearray(b'ZHM1') + struct.pack('>i', len(near['legs']))
    for L in near['legs']:
        b += struct.pack('>7f', *L['hip'], L['a'], L['lc'], L['restA'], L['restR'])
    b += struct.pack('>i', 2) + level(near) + level(far)
    raw = bytes(b)
    open(OUT, 'wb').write(zlib.compress(raw, 9))
    print('wrote', os.path.normpath(OUT), len(raw), '->', os.path.getsize(OUT), 'bytes')


main()
