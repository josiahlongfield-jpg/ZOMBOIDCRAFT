"""Cabin anchors for the interior kit, measured from each car's real mesh (centred like the game)."""
import sys, os, numpy as np
sys.path.insert(0, '/home/user/zomboidcraft/tools/cars')
import wreck, pack, lamps as L

def hit(T, o, d):
    d = np.asarray(d, float); d /= np.linalg.norm(d)
    t, i = L.hit(T, np.asarray(o, float), d)
    return t

def load(path, variant='usable'):
    stem = os.path.basename(path)[:-4]; mid = pack.MODELS[stem][0]
    car = wreck.prepare(path); src = L.source_colour(car)
    vi = pack.VARIANTS.index(variant)
    b = wreck.build_variant(car, variant, pack.java_hash(mid) % 100000 * 10 + vi, pack.BOOTS.get(mid, 'car'), pack.THEMES.get(mid, 'trunk'))
    lo, hi = b['tris'].reshape(-1, 3).min(0), b['tris'].reshape(-1, 3).max(0)
    centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])
    col = np.asarray(b['colour']).astype(float) / 255; Hh, Ww = col.shape[:2]; uvc = b['uv'].mean(1)
    base = col[np.clip((uvc[:, 1] * Hh).astype(int), 0, Hh - 1), np.clip((uvc[:, 0] * Ww).astype(int), 0, Ww - 1)]
    o = b['origin']; part = np.where(o >= 0, car.pid[np.maximum(o, 0)], 999)
    C = np.where((o >= 0)[:, None], src[np.maximum(o, 0)], 0)
    eye = wreck.seats(car)[0] - centre
    kind = L.kinds(b['tris'] - centre, C, eye + centre) if False else L.kinds(b['tris'], C, wreck.seats(car)[0])
    inside = np.where(o >= 0, car.cls[np.maximum(o, 0)] == 'interior', False)
    return dict(inside=inside, mid=mid, T=b['tris'] - centre, base=base, glass=b['glass'], part=part, kind=kind, eye=eye, h=hi[1] - lo[1], size=hi - lo)

def anchors(car):
    T = car['T'][~car['glass']]; TA = car['T']
    e = car['eye']; ex, ey, ez = e
    a = dict(eye=e)
    ins = car['inside'] & ~car['glass']
    a['tint'] = np.median(car['base'][ins], 0) if ins.any() else None
    a['roof'] = ey + (hit(T, e, [0, 1, 0]) or 0.3)
    # floor: lowest of a few rays down through the front footwells, started under the dash
    fl = []
    for x in (ex, ex * 0.6, -ex * 0.6, -ex):
        for dz in (0.35, 0.5):
            o = [x, ey - 0.6, ez + dz]
            t = hit(T, o, [0, -1, 0])
            if t is not None and t < 1.2: fl.append(o[1] - t)
    a['floor'] = float(np.median(fl)) if fl else ey - 1.0
    # seat cushion top under the driver's thighs
    t = hit(T, [ex, ey - 0.25, ez + 0.12], [0, -1, 0])
    a['seat'] = ey - 0.25 - t if t is not None else a['floor'] + 0.35
    # inner edge of the driver's seat, from the middle of the car outwards, just under the cushion top
    t = hit(T, [0, a['seat'] - 0.04, ez + 0.05], [1, 0, 0])
    a['seat_in'] = t if t is not None and t < ex else ex - 0.25
    t = hit(T, [ex, a['seat'] + 0.3, ez + 0.1], [0, 0, -1])
    a['seat_back'] = ez + 0.1 - (t if t is not None else 0.4)
    t = hit(TA, [0, ey + 0.05, ez], [0, 0, -1])
    a['back_room'] = t if t is not None else 1.0
    a['door'] = ex + (hit(T, [ex, ey - 0.4, ez + 0.1], [1, 0, 0]) or 0.4)
    # window sill: the lowest height where a ray out of the door no longer hits the solid door close by
    sill = ey - 0.25
    for y in np.arange(ey - 0.6, ey + 0.1, 0.02):
        t = hit(T, [ex, y, ez + 0.1], [1, 0, 0])
        if t is None or ex + t > a['door'] + 0.12: sill = y; break
    a['sill'] = sill
    # the dash face down the middle and in front of the passenger, at several heights
    prof = {}
    for name, x in (('mid', 0.0), ('pass', -ex), ('drv', ex)):
        rows = []
        for y in np.arange(ey + 0.1, a['floor'], -0.02):
            t = hit(T, [x, y, ez], [0, 0, 1])
            rows.append((round(float(y), 3), None if t is None else round(float(ez + t), 3)))
        prof[name] = rows
    a['prof'] = prof
    top = [y for y, z in prof['mid'] if z is not None and z < ez + 1.3]
    a['dash_top'] = top[0] if top else ey - 0.2
    near = [z for y, z in prof['mid'] if z is not None and z < ez + 1.3 and y < a['dash_top'] - 0.05]
    a['dash_mid_z'] = float(np.median(near)) if near else ez + 0.7
    t = hit(TA, [0, a['roof'] - 0.04, ez], [0, 0, 1])
    a['header_z'] = ez + t if t is not None else ez + 0.5
    a['wheel_z'] = ez + (hit(T, [ex, ey - 0.25, ez], [0, 0, 1]) or 0.4)
    dm = car['kind'] == L.DASH
    a['cluster'] = car['T'][dm].mean((0, 1)) if dm.any() else np.array([ex, a['dash_top'] - 0.05, a['wheel_z'] + 0.2])
    return a

if __name__ == '__main__':
    I = sys.argv[1]
    import glob
    files = {os.path.basename(f)[:-4]: f for f in glob.glob(I + '/**/*.glb', recursive=True)}
    for stem in sys.argv[2:]:
        car = load(files[stem]); a = anchors(car)
        print(stem, {k: (np.round(v, 3).tolist() if k != 'prof' else '') for k, v in a.items()})
        for y, z in a['prof']['mid'][::3]: print('   mid', y, z, ' pass', dict(a['prof']['pass']).get(y))
