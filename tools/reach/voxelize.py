"""Turns a level's collision (triangles in metres, x east, y up, z south) into the Reach world's invisible walls.

Every 1 m block a collision triangle passes through becomes solid; the top block of each solid run keeps the height
the surface actually reaches inside it, in sixteenths (the reach_floor block's layers), so floors and slopes sit
where the level's own floors are rather than on whole blocks. Nothing is filled in or flattened: cliffs, canyons,
ledges and every storey of a building stay where the level puts them.

Input: one or more .npz files with V (n,3 float metres) and T (m,3+ int) as the layout copies keep them. The level
is turned by --yaw degrees about the vertical and moved by --offset x,y,z (blocks) into its place in the world.
Output (.zrv, read by com.zomboidcraft.world.reach.ReachVoxels):
    'ZRV1', int version 1, int chunk count, then per chunk: int cx, int cz, int offset, int length;
    each chunk's data, zlib-compressed: for the 256 columns (index lz * 16 + lx) a ushort run count, then per run
    a short first y, a ushort length and a byte top layer (1..16; the blocks under it are whole). Big-endian.

Personal use only: the .npz and .zrv files come from the player's own copy of the game and stay on their computer.
"""
import argparse
import struct
import zlib

import numpy as np

STEP = 0.3  # sample spacing on each triangle, metres (well under a block, so no block a surface crosses is missed)


def load(paths):
    Vs, Ts, base = [], [], 0
    for p in paths:
        z = np.load(p)
        V = z['V'].astype(np.float64)
        T = z['T'][:, :3].astype(np.int64)
        Vs.append(V)
        Ts.append(T + base)
        base += len(V)
    return np.concatenate(Vs), np.concatenate(Ts)


def place(V, yaw, offset):
    a = np.radians(yaw)
    c, s = np.cos(a), np.sin(a)
    x = V[:, 0] * c - V[:, 2] * s
    z = V[:, 0] * s + V[:, 2] * c
    return np.stack([x + offset[0], V[:, 1] + offset[1], z + offset[2]], axis=1)


def samples(V, T, step=STEP, batch=6_000_000):
    """Yields (n,3) points spread over the triangles at no more than `step` apart."""
    a, b, c = V[T[:, 0]], V[T[:, 1]], V[T[:, 2]]
    longest = np.maximum(np.linalg.norm(b - a, axis=1), np.maximum(np.linalg.norm(c - a, axis=1), np.linalg.norm(c - b, axis=1)))
    n = np.clip(np.ceil(longest / step).astype(np.int64), 1, 4000)
    for k in np.unique(n):
        idx = np.nonzero(n == k)[0]
        u, v = np.meshgrid(np.arange(k + 1), np.arange(k + 1))
        keep = (u + v) <= k
        u = (u[keep] / k)[None, :, None]
        v = (v[keep] / k)[None, :, None]
        per = max(1, batch // u.shape[1])
        for i in range(0, len(idx), per):
            j = idx[i:i + per]
            A, B, C = a[j][:, None, :], b[j][:, None, :], c[j][:, None, :]
            yield (A + (B - A) * u + (C - A) * v).reshape(-1, 3)


def voxels(V, T):
    """The solid blocks as a dict-like pair: keys (x, y, z ints) and the top of the surface inside each (0..1]."""
    keys, tops = [], []
    for P in samples(V, T):
        q = np.floor(P).astype(np.int64)
        frac = P[:, 1] - q[:, 1]
        k = (q[:, 0] + (1 << 20)) << 42 | (q[:, 2] + (1 << 20)) << 21 | (q[:, 1] + (1 << 20))
        uk, inv = np.unique(k, return_inverse=True)
        m = np.zeros(len(uk))
        np.maximum.at(m, inv, frac)
        keys.append(uk)
        tops.append(m)
    k = np.concatenate(keys)
    t = np.concatenate(tops)
    uk, inv = np.unique(k, return_inverse=True)
    m = np.zeros(len(uk))
    np.maximum.at(m, inv, t)
    x = (uk >> 42) - (1 << 20)
    z = ((uk >> 21) & ((1 << 21) - 1)) - (1 << 20)
    y = (uk & ((1 << 21) - 1)) - (1 << 20)
    return x, y, z, m


def runs(x, y, z, top):
    """Per column, the solid runs (first y, length, top layer); keys sorted by column then y."""
    order = np.lexsort((y, z, x))
    x, y, z, top = x[order], y[order], z[order], top[order]
    cols = {}
    start = 0
    n = len(x)
    while start < n:
        end = start
        while end + 1 < n and x[end + 1] == x[start] and z[end + 1] == z[start]:
            end += 1
        col = []
        r0 = start
        for i in range(start, end + 1):
            last = i == end or y[i + 1] != y[i] + 1
            if last:
                layer = int(np.clip(np.ceil(top[i] * 16), 1, 16))
                col.append((int(y[r0]), int(y[i] - y[r0] + 1), layer))
                r0 = i + 1
        cols[(int(x[start]), int(z[start]))] = col
        start = end + 1
    return cols


def runs_fast(x, y, z, top):
    order = np.lexsort((y, z, x))
    x, y, z, top = x[order], y[order], z[order], top[order]
    newcol = np.ones(len(x), bool)
    newcol[1:] = (x[1:] != x[:-1]) | (z[1:] != z[:-1])
    brk = newcol.copy()
    brk[1:] |= y[1:] != y[:-1] + 1
    starts = np.nonzero(brk)[0]
    ends = np.append(starts[1:], len(x)) - 1
    layer = np.clip(np.ceil(top[ends] * 16), 1, 16).astype(int)
    cols = {}
    for s, e, l in zip(starts, ends, layer):
        cols.setdefault((int(x[s]), int(z[s])), []).append((int(y[s]), int(e - s + 1), int(l)))
    return cols


def write(path, cols):
    chunks = {}
    for (x, z), col in cols.items():
        chunks.setdefault((x >> 4, z >> 4), {})[(x & 15) + (z & 15) * 16] = col
    blobs = []
    for (cx, cz), cc in sorted(chunks.items()):
        out = bytearray()
        for i in range(256):
            col = cc.get(i, ())
            out += struct.pack('>H', len(col))
            for y0, length, layer in col:
                out += struct.pack('>hHB', y0, length, layer)
        blobs.append((cx, cz, zlib.compress(bytes(out), 9)))
    head = 4 + 4 + 4 + 16 * len(blobs)
    with open(path, 'wb') as f:
        f.write(b'ZRV1' + struct.pack('>ii', 1, len(blobs)))
        off = head
        for cx, cz, b in blobs:
            f.write(struct.pack('>iiii', cx, cz, off, len(b)))
            off += len(b)
        for _, _, b in blobs:
            f.write(b)
    return len(blobs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('npz', nargs='+')
    ap.add_argument('--yaw', type=float, default=0)
    ap.add_argument('--offset', default='0,0,0')
    ap.add_argument('--npy', help='also save the solid blocks (x, y, z, layer) for previews')
    a = ap.parse_args()
    V, T = load(a.npz)
    V = place(V, a.yaw, [float(v) for v in a.offset.split(',')])
    x, y, z, top = voxels(V, T)
    cols = runs_fast(x, y, z, top)
    n = write(a.out, cols)
    if a.npy:
        np.save(a.npy, np.stack([x, y, z, np.clip(np.ceil(top * 16), 1, 16)], axis=1).astype(np.int32))
    print(f'{len(x)} solid blocks in {len(cols)} columns, {n} chunks -> {a.out}')


if __name__ == '__main__':
    main()
