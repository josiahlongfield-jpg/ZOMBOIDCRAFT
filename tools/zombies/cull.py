# Strip the faces nobody can see from the packed zombie meshes: clothing faces that point into the body or into
# another piece of clothing on the same bone. They stay hidden in every pose (a bone's pieces move together), and a
# body drawn every frame is cheaper for each one gone. Run after pack.py; running it again changes nothing.
#   python3 tools/zombies/cull.py [--check]
import struct, sys, numpy as np
from collections import defaultdict
from pathlib import Path

R = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/zomboidcraft/zbody'
EPS = 1e-3


def read(path):
    b = path.read_bytes(); o = 4
    nb, = struct.unpack_from('<i', b, o); o += 4
    head = b[:o + nb * 13]; o += nb * 13
    n, = struct.unpack_from('<i', b, o); o += 4
    quads = []
    for _ in range(n):
        raw = b[o:o + 78]; v = struct.unpack_from('<19f', raw, 2); o += 78
        quads.append((raw[0], raw[1], np.array(v[:12]).reshape(4, 3), np.array(v[12:15]), raw))
    return head, quads


def write(path, head, quads):
    path.write_bytes(head + struct.pack('<i', len(quads)) + b''.join(q[4] for q in quads))


def plane(q):
    """Axis, coordinate and facing of an axis-aligned quad; None for a slanted one."""
    ax = int(np.argmax(np.abs(q[3])))
    return None if abs(q[3][ax]) < 0.999 else (ax, q[2][0][ax])


def whole_box(quads, group, lo, hi):
    """Six faces, one each way, each covering the whole side of the box: a closed box and nothing else."""
    if len(group) != 6 or not np.all(hi - lo > EPS):
        return False
    sides = set()
    for k in group:
        p, nn = quads[k][2], quads[k][3]
        ax = int(np.argmax(np.abs(nn)))
        at = hi[ax] if nn[ax] > 0 else lo[ax]
        if abs(nn[ax]) < 0.999 or np.abs(p[:, ax] - at).max() > EPS:
            return False
        others = [a for a in range(3) if a != ax]
        if any(abs(p[:, a].min() - lo[a]) > EPS or abs(p[:, a].max() - hi[a]) > EPS for a in others):
            return False
        sides.add((ax, nn[ax] > 0))
    return len(sides) == 6


def solids(quads):
    """Per bone, the boxes the quads close up: the body's six-sided boxes, and each run of clothing faces that all sit
    on the faces of their joint bounding box (the exporter leaves out faces two boxes share, so a run can be short)."""
    body, kit = defaultdict(list), defaultdict(list)
    owner = {}
    i, n = 0, len(quads)
    while i < n and quads[i][1] & 8:
        g = quads[i:i + 6]; P = np.concatenate([q[2] for q in g])
        body[g[0][0]].append((-1, P.min(0), P.max(0))); i += 6
    group, gid = [], 0

    def close(group, gid):
        for k in group:
            owner[k] = gid
        P = np.concatenate([quads[k][2] for k in group]); lo, hi = P.min(0), P.max(0)
        if whole_box(quads, group, lo, hi):
            kit[quads[group[0]][0]].append((gid, lo, hi))

    for k in range(i, n):
        pl = plane(quads[k])
        cand = group + [k]
        ok = pl is not None and group and quads[group[0]][0] == quads[k][0] and len(cand) <= 6
        if ok:
            P = np.concatenate([quads[j][2] for j in cand]); lo, hi = P.min(0), P.max(0)
            ok = all(plane(quads[j]) and (abs(plane(quads[j])[1] - lo[plane(quads[j])[0]]) < EPS
                                           or abs(plane(quads[j])[1] - hi[plane(quads[j])[0]]) < EPS) for j in cand)
        if ok:
            group = cand
        else:
            if group:
                close(group, gid); gid += 1
            group = [k]
    if group:
        close(group, gid)
    return body, kit, owner


def hidden(quads):
    body, kit, owner = solids(quads)
    out = set()
    for k, q in enumerate(quads):
        if q[1] & 14:
            continue   # the body is never stripped (clothes don't cover all of it), nor the stump caps and cut ends
        c = q[2].mean(0)
        # the middle and the corners pulled a hair toward it, a hair in front of the face: all inside something solid
        pts = [c] + [p + (c - p) * 0.02 for p in q[2]]
        pts = [p + q[3] * 0.03 for p in pts]

        def solid(p):
            return any(np.all(p > lo - 1e-4) and np.all(p < hi + 1e-4) for _, lo, hi in body[q[0]]) or any(
                g != owner.get(k) and np.all(p > lo + 1e-4) and np.all(p < hi - 1e-4) for g, lo, hi in kit[q[0]])
        if all(solid(p) for p in pts):
            out.add(k)
    return out


if __name__ == '__main__':
    total = kept = 0
    for path in sorted(R.glob('*.bin')):
        head, quads = read(path)
        gone = hidden(quads)
        rest = [q for k, q in enumerate(quads) if k not in gone]
        total += len(quads); kept += len(rest)
        print(f'{path.stem:12} {len(quads):5} -> {len(rest):5}')
        if '--check' not in sys.argv:
            write(path, head, rest)
    print(f'all {total} -> {kept} ({100 - 100 * kept // max(1, total)}% fewer)')
