"""Writes the Reach world's land between the levels (.zrh, read by com.zomboidcraft.world.reach.ReachLand).

Input: height.npy (n x n float metres, rows north to south), flags.npy (n x n uint8: bits 0-1 kind 0 new land /
1 a level's own surroundings / 2 a level's footprint, bit 2 under the sea, bits 3-5 climate 0 sea 1 snow 2 tundra
3 green 4 dry grass 5 desert), the world x and z of the first sample and the metres between samples.
--test writes a small made-up land for CI instead (no Halo files there).

Personal use only: a real land file is made from the player's own copy of the game and stays on their computer.
"""
import argparse
import struct
import zlib

import numpy as np


def write(path, height, flags, x0, z0, cell):
    n = height.shape[0]
    assert height.shape == (n, n) and flags.shape == (n, n)
    h = np.clip(np.round(height * 10), -32768, 32767).astype('>i2')
    data = zlib.compress(h.tobytes() + flags.astype(np.uint8).tobytes(), 6)
    with open(path, 'wb') as f:
        f.write(b'ZRH1' + struct.pack('>iiiiii', 1, n, int(x0), int(z0), int(cell), len(data)))
        f.write(data)
    return len(data)


def test_land():
    """A 256 m square from (900, 900): ground at -590 sloping up 1 m every 16 m east, with the CI test level's
    footprint (1000..1040) left to the level's own walls and a strip of sea along the west edge."""
    n, cell = 65, 4
    xs = 900 + np.arange(n) * cell
    X, Z = np.meshgrid(xs, 900 + np.arange(n) * cell)
    height = (-590 + (X - 900) / 16.0).astype(np.float32)
    flags = np.full((n, n), 3 << 3, np.uint8)
    foot = (X >= 996) & (X <= 1044) & (Z >= 996) & (Z <= 1044)
    flags[foot] = 2 | 3 << 3
    sea = X < 912
    height[sea] = -620
    flags[sea] = 4 | 0 << 3
    return height, flags, 900, 900, cell


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--height')
    ap.add_argument('--flags')
    ap.add_argument('--origin', default='-8000,-8000')
    ap.add_argument('--cell', type=int, default=4)
    ap.add_argument('--test', action='store_true')
    a = ap.parse_args()
    if a.test:
        height, flags, x0, z0, cell = test_land()
    else:
        height, flags = np.load(a.height), np.load(a.flags)
        x0, z0 = (int(v) for v in a.origin.split(','))
        cell = a.cell
    size = write(a.out, height, flags, x0, z0, cell)
    print(f'land {height.shape[0]} x {height.shape[0]} samples every {cell} m from {x0}, {z0}: {size} bytes -> {a.out}')


if __name__ == '__main__':
    main()
