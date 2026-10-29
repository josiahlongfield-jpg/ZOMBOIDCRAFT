"""Reads the packed vehicle files (.zcar, written by pack.py and aircraft.py) back for the previews.

    from zcar import read, place
    states = read('jet_corvid')            # {state: dict(T, UV, glass, image)}
    T = place(states['usable']['T'], x, y, z, yaw)

T is (n, 3, 3) triangles in model space (metres, nose toward +z, wheels on y = 0), UV (n, 3, 2)
atlas coordinates (v down), glass a flag per triangle, image the state's texture (PIL). Lids and
wheels are folded into the body, shut and straight.
"""
import io
import json
import os
import struct
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CARS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars')
INDEX = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars/models.json')


def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def unscramble(data, model_id):
    x = (0x5A0C4A55 ^ java_hash(model_id)) & 0xFFFFFFFF or 1
    words = (len(data) + 3) // 4
    keys = np.empty(words, '>u4')
    for i in range(words):
        x ^= (x << 13) & 0xFFFFFFFF
        x ^= x >> 17
        x ^= (x << 5) & 0xFFFFFFFF
        keys[i] = x
    k = keys.view('u1')[:len(data)]
    return (np.frombuffer(data, 'u1') ^ k).tobytes()


def _mesh(b, at):
    opaque, glass = struct.unpack_from('>ii', b, at)
    at += 8
    n = opaque + glass
    raw = np.frombuffer(b, '>u2', n * 3 * 5, at).reshape(n, 3, 5)
    at += n * 3 * 10
    T = raw[..., :3].astype(np.uint16).view(np.int16).astype(float) / 1000
    UV = raw[..., 3:].astype(float) / 65535
    g = np.zeros(n, bool)
    g[opaque:] = True
    return (T, UV, g), at


def read(model_id):
    with open(os.path.join(CARS, model_id + '.zcar'), 'rb') as f:
        b = zlib.decompress(unscramble(f.read(), model_id))
    assert b[:4] == b'ZCAR' and struct.unpack_from('>i', b, 4)[0] == 3
    count = struct.unpack_from('>i', b, 8)[0]
    at = 12
    out = {}
    for _ in range(count):
        ln = struct.unpack_from('>H', b, at)[0]
        name = b[at + 2:at + 2 + ln].decode()
        at += 2 + ln
        jl = struct.unpack_from('>i', b, at)[0]
        image = Image.open(io.BytesIO(b[at + 4:at + 4 + jl])).convert('RGB')
        at += 4 + jl
        parts = []
        body, at = _mesh(b, at)
        parts.append(body)
        lids = struct.unpack_from('>i', b, at)[0]
        at += 4
        for _ in range(lids):
            at += 1 + 16
            m, at = _mesh(b, at)
            parts.append(m)
        wheels = struct.unpack_from('>i', b, at)[0]
        at += 4
        for _ in range(wheels):
            at += 16 + 1
            m, at = _mesh(b, at)
            parts.append(m)
        T = np.concatenate([p[0] for p in parts])
        UV = np.concatenate([p[1] for p in parts])
        G = np.concatenate([p[2] for p in parts])
        out[name] = dict(T=T, UV=UV, glass=G, image=image)
    return out


def place(T, x, y, z, yaw):
    """Model space to world: turned to Minecraft yaw (0 faces south, +z; 90 west) and moved."""
    a = np.radians(yaw)
    c, s = np.cos(a), np.sin(a)
    X = T[..., 0] * c - T[..., 2] * s
    Z = T[..., 2] * c + T[..., 0] * s
    return np.stack([X + x, T[..., 1] + y, Z + z], -1)


def models():
    with open(INDEX) as f:
        return json.load(f)['models']


def pick(entry, index, variants=('usable', 'abandoned', 'wrecked', 'burnt')):
    """The model and state a preview shows for a dump's car entry [x, y, z, yaw, min, max, names?]:
    a named one by id or kind, else one in the length range (never a jet), chosen by position."""
    x, y, z = entry[:3]
    h = java_hash(f'{x:.2f},{y},{z:.2f}')
    names = entry[6].split(',') if len(entry) > 6 and entry[6] else None
    if names and len(names) == 1 and '@' in names[0]:
        return tuple(names[0].lstrip('=').split('@'))
    names = [n.lstrip('=') for n in names] if names else None
    if names:
        cands = [m for m in index if m['id'] in names or m['kind'] in names]
    else:
        cands = [m for m in index if m['kind'] not in ('jet', 'boat') and entry[4] <= m['size'][2] <= entry[5]]
    if not cands:
        return None, None
    m = cands[h % len(cands)]
    state = variants[[0, 0, 0, 1, 1, 2][(h >> 8) % 6]] if m['kind'] != 'jet' else variants[[0, 0, 1][(h >> 8) % 3]]
    return m['id'], state
