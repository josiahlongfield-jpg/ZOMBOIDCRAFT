"""Writes each vehicle's wheel centres into models.json, read from its packed .zcar ("usable" state),
so the server knows where the wheels are: the hoist arms reach in just inside them, the trolley jack
goes under the right one, and the renderer tips a jacked car about its real axles.

    python3 tools/cars/wheels.py

Each model gains "wheels": [[cx, cz, r], ...] in model space (metres, nose toward +z, +x the left side).
"""
import json
import struct
import zlib

from zcar import CARS, INDEX, unscramble


def skip_mesh(b, at):
    opaque, extra = struct.unpack_from('>ii', b, at)
    return at + 8 + (opaque + extra) * 3 * 10


def wheels(model_id):
    with open(f'{CARS}/{model_id}.zcar', 'rb') as f:
        b = zlib.decompress(unscramble(f.read(), model_id))
    count = struct.unpack_from('>i', b, 8)[0]
    at = 12
    found = {}
    for _ in range(count):
        ln = struct.unpack_from('>H', b, at)[0]
        name = b[at + 2:at + 2 + ln].decode()
        at += 2 + ln
        at += 4 + struct.unpack_from('>i', b, at)[0]
        at = skip_mesh(b, at)
        lids = struct.unpack_from('>i', b, at)[0]
        at += 4
        for _ in range(lids):
            at = skip_mesh(b, at + 17)
        n = struct.unpack_from('>i', b, at)[0]
        at += 4
        out = []
        for _ in range(n):
            cx, cy, cz, r = struct.unpack_from('>4f', b, at)
            out.append([round(cx, 3), round(cz, 3), round(r, 3)])
            at = skip_mesh(b, at + 17)
        found[name] = out
        if name == 'usable':
            return out
        # later states (and turrets) aren't needed; the usable state comes first in every file
    return next(iter(found.values()), [])


def main():
    with open(INDEX) as f:
        index = json.load(f)
    for m in index['models']:
        try:
            m['wheels'] = wheels(m['id'])
        except FileNotFoundError:
            m['wheels'] = []      # a vehicle kept only on Josiah's computer
        print(f"{m['id']:20s} {len(m['wheels'])} wheels  " +
              ' '.join(f'({x:+.2f},{z:+.2f})' for x, z, _ in m['wheels']))
    with open(INDEX, 'w') as f:
        f.write(json.dumps(index, separators=(',', ':')))


if __name__ == '__main__':
    main()
