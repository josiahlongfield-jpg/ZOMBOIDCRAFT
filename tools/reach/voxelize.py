"""Turns a level's collision (triangles in metres, x east, y up, z south) into the Reach world's invisible walls.

Every 1 m block a collision triangle passes through becomes solid; the top block of each solid run keeps, for each of
its four quarters (half a block each way), the height the surface actually reaches inside that quarter in eighths of
a block, so floors and slopes sit where the level's own floors are rather than on whole blocks. Nothing is filled in or flattened: cliffs, canyons,
ledges and every storey of a building stay where the level puts them.

Input: one or more .npz files with V (n,3 float metres) and T (m,3+ int) as the layout copies keep them. The level
is turned by --yaw degrees about the vertical and moved by --offset x,y,z (blocks) into its place in the world.
Output (.zrv, read by com.zomboidcraft.world.reach.ReachVoxels):
    'ZRV1', int version 2, int chunk count, then per chunk: int cx, int cz, int offset, int length;
    each chunk's data, zlib-compressed: for the 256 columns (index lz * 16 + lx) a ushort run count, then per run
    a short first y, a ushort length and a ushort top: the top block's four quarter heights in eighths (0..8), four
    bits each, north-west lowest, then north-east, south-west, south-east (the blocks under it are whole). Big-endian.

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
    """The solid blocks (x, y, z ints) and, per block, the top of the surface inside each quarter (0..1, -1 = none)."""
    keys, tops = [], []
    for P in samples(V, T):
        q = np.floor(P).astype(np.int64)
        frac = P[:, 1] - q[:, 1]
        quarter = (np.floor(P[:, 0] * 2).astype(np.int64) & 1) + 2 * (np.floor(P[:, 2] * 2).astype(np.int64) & 1)
        k = ((q[:, 0] + (1 << 18)) << 40 | (q[:, 2] + (1 << 18)) << 21 | (q[:, 1] + (1 << 20))) * 4 + quarter
        uk, inv = np.unique(k, return_inverse=True)
        m = np.full(len(uk), -1.0)
        np.maximum.at(m, inv, frac)
        keys.append(uk)
        tops.append(m)
    k = np.concatenate(keys)
    t = np.concatenate(tops)
    uk, inv = np.unique(k, return_inverse=True)
    m = np.full(len(uk), -1.0)
    np.maximum.at(m, inv, t)
    block, quarter = uk // 4, uk % 4
    ub, binv = np.unique(block, return_inverse=True)
    quarters = np.full((len(ub), 4), -1.0)
    quarters[binv, quarter] = m
    x = (ub >> 40) - (1 << 18)
    z = ((ub >> 21) & ((1 << 19) - 1)) - (1 << 18)
    y = (ub & ((1 << 21) - 1)) - (1 << 20)
    return x, y, z, quarters


def pack(quarters):
    """Four quarter tops (0..1, -1 = no surface there) as eighths in four nibbles."""
    h = np.where(quarters < 0, 0, np.clip(np.ceil(quarters * 8), 1, 8)).astype(np.int64)
    return h[:, 0] | h[:, 1] << 4 | h[:, 2] << 8 | h[:, 3] << 12


def runs_fast(x, y, z, top):
    """Per column, the solid runs (first y, length, packed quarter tops of the run's top block)."""
    order = np.lexsort((y, z, x))
    x, y, z, top = x[order], y[order], z[order], top[order]
    newcol = np.ones(len(x), bool)
    newcol[1:] = (x[1:] != x[:-1]) | (z[1:] != z[:-1])
    brk = newcol.copy()
    brk[1:] |= y[1:] != y[:-1] + 1
    starts = np.nonzero(brk)[0]
    ends = np.append(starts[1:], len(x)) - 1
    layer = top[ends]
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
                out += struct.pack('>hHH', y0, length, layer)
        blobs.append((cx, cz, zlib.compress(bytes(out), 9)))
    head = 4 + 4 + 4 + 16 * len(blobs)
    with open(path, 'wb') as f:
        f.write(b'ZRV1' + struct.pack('>ii', 2, len(blobs)))
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
    ap.add_argument('--npy', help='also save the solid blocks (x, y, z, packed quarter tops) for previews')
    a = ap.parse_args()
    V, T = load(a.npz)
    V = place(V, a.yaw, [float(v) for v in a.offset.split(',')])
    x, y, z, quarters = voxels(V, T)
    top = pack(quarters)
    cols = runs_fast(x, y, z, top)
    n = write(a.out, cols)
    if a.npy:
        np.save(a.npy, np.stack([x, y, z, top], axis=1).astype(np.int32))
    print(f'{len(x)} solid blocks in {len(cols)} columns, {n} chunks -> {a.out}')


if __name__ == '__main__':
    main()
