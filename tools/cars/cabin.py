"""
Where a car's cabin controls are, for the game: the outside door handle that goes with each seat
(the green highlight that picks your seat), the steering wheel (split off and turned with the
steering, the driver's hands on its rim) and the glovebox (searched for the key). pack.py adds them
to each model in models.json; run on its own it adds them to the models.json there now:

    python3 tools/cars/cabin.py SOURCE_DIR

Model space as everywhere here: metres, y up, nose toward +z, the driver's side +x.
"""
import glob, json, os, sys
import numpy as np
import wreck, interior
from wreck import bbox

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MODELS_JSON = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars/models.json')


def islands(T):
    """A label per triangle: triangles sharing a corner are one island."""
    V = np.round(T.reshape(-1, 3) * 2000).astype(np.int64)
    _, inv = np.unique(V, axis=0, return_inverse=True)
    inv = inv.ravel().reshape(-1, 3)
    parent = np.arange(inv.max() + 1)

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for a, b, c in inv:
        ra = find(a)
        parent[find(b)] = ra
        parent[find(c)] = ra
    return np.array([find(a) for a in inv[:, 0]])


def steering(car, eye):
    """
    The steering wheel's rim: the ring-shaped island in front of the driver, as (centre, normal toward
    the driver, radius), or None. The hub and spokes are whatever lies inside the rim's disc.
    """
    C = car.P.mean(1)
    near = (np.abs(C[:, 0] - eye[0]) < 0.45) & (C[:, 1] > eye[1] - 0.9) & (C[:, 1] < eye[1] + 0.05) \
        & (C[:, 2] > eye[2] + 0.1) & (C[:, 2] < eye[2] + 1.0)
    near &= np.array([car.parts[i]['cls'] not in ('glass', 'door') for i in car.pid])
    idx = np.where(near)[0]
    if len(idx) == 0:
        return None
    labels = islands(car.P[idx])
    best = None
    for label in np.unique(labels):
        k = idx[labels == label]
        if len(k) < 24:
            continue
        V = np.unique(np.round(car.P[k].reshape(-1, 3), 4), axis=0)
        mean = V.mean(0)
        _, _, vt = np.linalg.svd(V - mean, full_matrices=False)
        span = np.ptp((V - mean) @ vt.T, axis=0)
        # a flat ring a steering wheel across
        if not (0.24 < span[0] < 0.6 and span[1] > 0.72 * span[0] and span[2] < 0.5 * span[1]):
            continue
        n = vt[2] if vt[2][2] < 0 else -vt[2]
        c = mean.copy()
        for ax in (vt[0], vt[1]):
            d = (V - mean) @ ax
            c += ax * (d.min() + d.max()) / 2
        radial = np.sqrt(np.maximum(((V - c) ** 2).sum(1) - ((V - c) @ n) ** 2, 0))
        score = abs(c[0] - eye[0]) + (radial < 0.5 * radial.max()).mean()
        if best is None or score < best[0]:
            best = (score, c, n, radial.max())
    return None if best is None else best[1:]


def handles(car, eyes):
    """Each seat's outside door handle: at the back edge of the side door on its side nearest it, just under the window."""
    doors = [i for i, p in enumerate(car.parts) if p['cls'] == 'door' and p.get('kind') == 'side']
    out = []
    for eye in eyes:
        side = 1 if eye[0] > 0.25 else -1
        mine = [i for i in doors if np.sign((car.parts[i]['lo'][0] + car.parts[i]['hi'][0]) / 2) == side]
        if not mine:
            out.append(None)
            continue
        i = min(mine, key=lambda d: max(0, car.parts[d]['lo'][2] - eye[2], eye[2] - car.parts[d]['hi'][2]))
        p = car.parts[i]
        glass = [j for j, d in car.door_of.items() if d == i and j != i]
        sill = min(bbox(car.P[car.pid == j])[0][1] for j in glass) if glass else p['hi'][1]
        if sill < 0.45 * car.hi[1]:
            out.append(None)  # glass to the floor: a bus's folding door
            continue
        x = p['hi'][0] + 0.02 if side > 0 else p['lo'][0] - 0.02
        out.append(np.array([x, sill - 0.1, p['lo'][2] + 0.12]))
    return out


def info(car, centre):
    """The cabin entries for a model's models.json record, in its centred model space."""
    eyes = wreck.seats(car)
    out = {}
    hs = handles(car, eyes)
    out['handles'] = [None if h is None else [round(float(v), 3) for v in h - centre] for h in hs]
    w = steering(car, eyes[0])
    if w is not None:
        c, n, r = w
        out['steering'] = [round(float(v), 3) for v in list(c - centre) + list(n)] + [round(float(r), 3)]
    a = interior.anchors(car, eyes)
    if a['dash_top'] is not None and interior.STYLE.get(car.name) != 'bus':  # the kit gives buses none
        gy = a['dash_top'] - 0.15
        g = np.array([-eyes[0][0] - 0.01, gy, interior.face_z(a, 'pass', gy) - 0.01])
        out['glovebox'] = [round(float(v), 3) for v in g - centre]
    return out


def main():
    source = sys.argv[1]
    files = {os.path.basename(f)[:-4]: f for f in glob.glob(os.path.join(source, '**', '*.glb'), recursive=True)}
    import pack
    with open(MODELS_JSON) as f:
        index = json.load(f)
    by_id = {m['id']: m for m in index['models']}
    for stem, (mid, *_rest) in pack.MODELS.items():
        if stem not in files or mid not in by_id:
            continue
        car = wreck.Car(files[stem])
        m = by_id[mid]
        # the model's centre, from its seats as pack.py wrote them
        centre = wreck.seats(car)[0] - np.array(m['seats'][0])
        for k in ('handles', 'steering', 'glovebox'):
            m.pop(k, None)
        m.update(info(car, centre))
        print(f"{mid:20s} handles {sum(h is not None for h in m['handles'])}/{len(m['handles'])}"
              f"  steering {'yes' if 'steering' in m else 'NO'}  glovebox {'yes' if 'glovebox' in m else 'no'}", flush=True)
    with open(MODELS_JSON, 'w') as f:
        json.dump(index, f, separators=(',', ':'))


if __name__ == '__main__':
    main()
