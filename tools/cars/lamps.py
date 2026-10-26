"""
Finds each car's lamps and dashboard for the game's car lights, writing
src/main/resources/assets/zomboidcraft/cars/lamps.json.

    python3 tools/cars/lamps.py <folder with the pack's .glb files>

The pack paints every lamp from one palette: headlight and reversing-light lenses share one
pale grey, tail lights one red. A lamp is a triangle of that colour on the car's front (head)
or back (tail, reverse). Each variant is rebuilt with pack.py's seeds so the triangle indices
match the .zcar files exactly; they're stored per mesh (0 the body, then each lid) as runs of
indices into that mesh's opaque triangles. The dash is where the driver's eye, looking down
and ahead, meets the instrument panel facing them;
the dials and the radio's display, in their own palette colours there, are lamps too.
"""
import argparse, glob, json, os, sys
from multiprocessing import Pool
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pack, wreck

LENS, TAIL = np.array([173, 168, 165]), np.array([161, 48, 49])
# the instrument faces and the radio's display
DIALS = np.array([[82, 51, 160], [42, 101, 163], [100, 132, 171], [163, 42, 131]])
HEAD, BRAKE, REVERSE, DASH, PANEL = 1, 2, 3, 4, 5  # PANEL: the cabin kit's backlit dials and displays


def source_colour(car):
    H, W = car.src.shape[:2]
    uv = car.UV.mean(1)
    return car.src[np.clip(((uv[:, 1] % 1) * H).astype(int), 0, H - 1),
                   np.clip(((uv[:, 0] % 1) * W).astype(int), 0, W - 1)] * 255


def kinds(T, C, eye):
    n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0]); n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    c = T.mean(1)
    zlo, zhi = T[..., 2].min(), T[..., 2].max()
    lens = np.abs(C - LENS).max(1) < 8
    red = np.abs(C - TAIL).max(1) < 8
    edge = np.linalg.norm(T - np.roll(T, 1, 1), axis=2).max(1)
    front = (n[:, 2] > 0.5) & (c[:, 2] > zhi - 0.45) & (c[:, 1] < 1.6)
    rear = (n[:, 2] < -0.5) & (c[:, 2] < zlo + 0.45)
    k = np.zeros(len(T), int)
    k[front & lens] = HEAD
    k[rear & red] = BRAKE
    k[rear & lens & (c[:, 1] < 1.2) & (edge < 0.45)] = REVERSE  # not the chrome round a back window
    dial = (np.abs(C[:, None] - DIALS[None]).max(2) < 8).any(1)
    cabin = (c[:, 2] > eye[2] + 0.2) & (c[:, 2] < eye[2] + 1.2) & (c[:, 1] > eye[1] - 0.75) & (c[:, 1] < eye[1] - 0.05) & (n[:, 2] < -0.2)
    k[dial & cabin] = DASH
    return k


def runs(idx):
    out, start = [], None
    for a, b in zip(idx, list(idx[1:]) + [None]):
        start = a if start is None else start
        if b != a + 1:
            out.append([int(start), int(a - start + 1)])
            start = None
    return out


def hit(T, o, d):
    e1 = T[:, 1] - T[:, 0]; e2 = T[:, 2] - T[:, 0]
    p = np.cross(d, e2); det = (e1 * p).sum(1)
    ok = np.abs(det) > 1e-9; inv = np.where(ok, 1 / np.where(ok, det, 1), 0)
    s = o - T[:, 0]; u = (s * p).sum(1) * inv
    q = np.cross(s, e1); v = (q * d).sum(1) * inv; t = (q * e2).sum(1) * inv
    m = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 0.02)
    if not m.any():
        return None, None
    i = np.where(m)[0][np.argmin(t[m])]
    return t[i], i


def dash(T, eye):
    """The instrument panel ahead of the driver: the longest run of rays meeting one panel facing them."""
    hits = []
    for deg in np.arange(4, 46, 1.5):
        a = np.radians(deg); d = np.array([0, -np.sin(a), np.cos(a)])
        t, i = hit(T, eye, d)
        if t is None:
            continue
        n = np.cross(T[i, 1] - T[i, 0], T[i, 2] - T[i, 0]); n /= np.linalg.norm(n)
        if n[2] < -0.85 and 0.45 < t < 1.0:
            hits.append((t, eye + d * t, n))
    best, run = [], []
    for h in hits:
        if run and (abs(h[0] - run[-1][0]) > 0.06 or h[2] @ run[-1][2] < 0.98):
            run = []
        run.append(h)
        if len(run) > len(best):
            best = list(run)
    if not best:
        return eye + [0, -0.3, 0.6], np.array([0, 0, -1.0])
    p = np.mean([h[1] for h in best], 0)
    n = np.mean([h[2] for h in best], 0)
    return p, n / np.linalg.norm(n)


def build(path):
    stem = os.path.basename(path)[:-4]
    mid = pack.MODELS[stem][0]
    car = wreck.prepare(path)
    src = source_colour(car)
    out = {}
    for i, vname in enumerate(pack.VARIANTS):
        b = wreck.build_variant(car, vname, pack.java_hash(mid) % 100000 * 10 + i,
                                pack.BOOTS.get(mid, 'car'), pack.THEMES.get(mid, 'trunk'))
        o = b['origin']
        C = np.where((o >= 0)[:, None], src[np.maximum(o, 0)], 0)
        k = kinds(b['tris'], C, wreck.seats(car)[0])
        k[b['lit']] = PANEL
        if i == 0:
            lo, hi = b['tris'].reshape(-1, 3).min(0), b['tris'].reshape(-1, 3).max(0)
            centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])
            solid = ~b['glass']
            p, n = dash(b['tris'][solid], wreck.seats(car)[0])
            out['dash'] = [round(float(x), 3) for x in np.concatenate([p - centre, n])]
        meshes = [(b['lid'] < 0) & (b['wheel'] < 0)] + [b['lid'] == j for j in range(len(b['lids']))]
        lamps = []
        for j, m in enumerate(meshes):
            opaque = np.where(m & ~b['glass'])[0]
            for kind in (HEAD, BRAKE, REVERSE, DASH, PANEL):
                for start, count in runs(np.where(k[opaque] == kind)[0]):
                    lamps.append([j, kind, start, count])
        out[vname] = lamps
    print(f'{mid:20s} dash {out["dash"][:3]}  lamps {[len(out[v]) for v in pack.VARIANTS]}', flush=True)
    return mid, out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('source')
    ap.add_argument('--jobs', type=int, default=4)
    a = ap.parse_args()
    files = sorted(f for f in glob.glob(os.path.join(a.source, '**', '*.glb'), recursive=True)
                   if os.path.basename(f)[:-4] in pack.MODELS)
    with Pool(a.jobs) as pool:
        res = dict(pool.map(build, files))
    with open(os.path.join(pack.ROOT, 'src/main/resources/assets/zomboidcraft/cars/lamps.json'), 'w') as f:
        json.dump(dict(sorted(res.items())), f, separators=(',', ':'))


if __name__ == '__main__':
    main()
