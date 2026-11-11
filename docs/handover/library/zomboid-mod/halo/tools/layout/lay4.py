"""final layout map + zooms: roads, farm tracks, farmsteads, village houses, labels"""
import json, numpy as np, sys
from PIL import Image, ImageDraw, ImageFont
exec(open('lay3.py').read().split("fbm_fine = None")[0])
OUT = '/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/'
rs = np.random.default_rng(3)
def chaikin(P, it=3):
    P = np.asarray(P, float)
    for _ in range(it):
        Q = np.empty((len(P) * 2 - 2, 2)); Q[0::2] = P[:-1] * 0.75 + P[1:] * 0.25; Q[1::2] = P[:-1] * 0.25 + P[1:] * 0.75
        P = np.vstack([P[:1], Q, P[-1:]])
    return P
lines = []  # (kind, Nx2 world)
for name, kind, p in R['edges']:
    rr, cc = np.divmod(np.array(p), N)
    pts = np.array([c2w(r, c) for r, c in zip(rr, cc)])[::2]
    if len(pts) < 2: continue
    k = 'main' if kind == 'mission' else 'ledge' if kind == 'ledge' else 'shore' if kind == 'shore' else 'track' if kind == 'track' else 'country'
    lines.append((k, chaikin(pts)))
# farm tracks: each off-road farm to the nearest road cell
rc = np.argwhere(road); rw = np.array([c2w(r, c) for r, c in rc])
for s_ in seeds:
    d = np.hypot(rw[:, 0] - s_[0], rw[:, 1] - s_[1]); k = d.argmin()
    if 60 < d[k] < 900:
        mid = (np.array(s_[:2]) + rw[k]) / 2 + rs.normal(0, 25, 2)
        lines.append(('farm', chaikin([s_[:2], mid, rw[k]], 2)))
# village houses along the roads
houses = []  # (x, z, ang, w, d, kind)
roadpts = np.vstack([L for k, L in lines if k in ('main', 'country')])
for v in VIL:
    near = [(k, L) for k, L in lines if k in ('main', 'country')]
    rad = 380 if v['kind'] == 'village' else 260
    for k, L in near:
        dd = np.hypot(L[:, 0] - v['x'], L[:, 1] - v['z'])
        idx = np.nonzero(dd < rad)[0]
        for i in idx[::2]:
            a, b = L[max(i - 2, 0)], L[min(i + 2, len(L) - 1)]; t = b - a; t = t / (np.linalg.norm(t) + 1e-9); nrm = np.array([-t[1], t[0]])
            for side in (1, -1):
                if rs.random() < 0.1 + 0.5 * (np.hypot(L[i][0] - v['x'], L[i][1] - v['z']) / rad) ** 2: continue
                c = L[i] + nrm * side * rs.uniform(13, 17)
                if np.min(np.hypot(roadpts[:, 0] - c[0], roadpts[:, 1] - c[1])) < 9: continue
                if any(np.hypot(c[0] - h[0], c[1] - h[1]) < 13 for h in houses[-60:]): continue
                houses.append((c[0], c[1], np.arctan2(t[1], t[0]), rs.uniform(9, 15), rs.uniform(7, 10), 'house'))
    # the church / hall at the middle
    houses.append((v['x'] + 20, v['z'] + 20, rs.uniform(0, 3), 26, 13, 'church'))
for s_ in seeds:  # farmhouse + barn
    a = s_[2]
    houses.append((s_[0], s_[1], a, 13, 9, 'farm'))
    houses.append((s_[0] + np.cos(a + 1.3) * 26, s_[1] + np.sin(a + 1.3) * 26, a, 22, 12, 'barn'))
json.dump(dict(lines=[(k, L.round(1).tolist()) for k, L in lines], houses=[list(map(lambda q: q if isinstance(q, str) else round(float(q), 1), h)) for h in houses]), open('layout_vec.json', 'w'))
print('lines', len(lines), 'houses', len(houses), flush=True)
STY = {'main': ((236, 232, 220), (60, 56, 50), 7.5), 'country': ((222, 204, 160), (70, 62, 48), 5.5), 'track': ((196, 170, 120), None, 4),
       'shore': ((214, 196, 150), None, 3), 'ledge': ((236, 232, 220), (120, 70, 50), 7.5), 'farm': ((176, 150, 104), None, 3)}
HC = {'house': (196, 176, 160), 'church': (232, 226, 214), 'farm': (200, 184, 166), 'barn': (150, 84, 64)}
def draw(x0, z0, size, px, labels=True, minw=None):
    im, _ = render(x0, z0, size, px); im = im.resize((px * 2, px * 2), Image.BICUBIC); S = px * 2 / size
    dr = ImageDraw.Draw(im)
    P = lambda q: ((q[0] - x0) * S, (q[1] - z0) * S)
    for layer in (0, 1):
        for k, L in sorted(lines, key=lambda q: ['farm', 'shore', 'track', 'country', 'ledge', 'main'].index(q[0])):
            col, case, w = STY[k]; wpx = max(w * S, {'main': 3.4, 'ledge': 3.4, 'country': 2.4, 'track': 1.8, 'shore': 1.6, 'farm': 1.0}[k] * (px / 1000) * 1.6)
            if L[:, 0].max() < x0 or L[:, 0].min() > x0 + size or L[:, 1].max() < z0 or L[:, 1].min() > z0 + size: continue
            pts = [P(q) for q in L]
            if layer == 0 and case: dr.line(pts, fill=case, width=int(round(wpx + 2.5)), joint='curve')
            if layer == 1:
                if k == 'shore':
                    for i in range(0, len(pts) - 1, 2): dr.line(pts[i:i + 2], fill=col, width=int(round(wpx)))
                else: dr.line(pts, fill=col, width=int(round(wpx)), joint='curve')
    for x, z, a, w, d, kind in houses:
        if not (x0 - 50 < x < x0 + size + 50 and z0 - 50 < z < z0 + size + 50): continue
        c, s_ = np.cos(a), np.sin(a); hw = max(w / 2 * S, 1.5); hd = max(d / 2 * S, 1.2)
        q = [(P((x, z))[0] + c * u - s_ * v, P((x, z))[1] + s_ * u + c * v) for u, v in ((-hw, -hd), (hw, -hd), (hw, hd), (-hw, hd))]
        dr.polygon(q, fill=HC[kind], outline=(40, 36, 32))
    return im, dr, S, P
if __name__ == '__main__':
    import os
    font = lambda n: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', n) if os.path.exists('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf') else ImageFont.load_default()
    im, dr, S, P = draw(-8000, -8000, 16000, 1100)
    for m, (x, z) in CEN.items():
        X, Y = P((x, z)); t = NAMES[m]; dr.text((X, Y), t, fill=(255, 255, 255), font=font(26), anchor='mm', stroke_width=3, stroke_fill=(20, 20, 20))
    X, Y = P((2300, 6900)); dr.text((X, Y - 24), 'Lone Wolf', fill=(255, 255, 255), font=font(20), anchor='mm', stroke_width=3, stroke_fill=(20, 20, 20))
    for v in VIL:
        X, Y = P((v['x'], v['z'])); dr.text((X + 14, Y - 14), v['name'], fill=(255, 246, 214), font=font(19), anchor='lm', stroke_width=3, stroke_fill=(30, 26, 20))
    im.save(OUT + 'lay_map.png')
    for nm, (cx, cz, sz) in {'z1': (VIL[1]['x'], VIL[1]['z'] + 100, 1300), 'z2': (VIL[8]['x'], VIL[8]['z'], 1300), 'z3': (VIL[2]['x'] + 600, VIL[2]['z'] - 1400, 3000)}.items():
        im, dr, S, P = draw(cx - sz / 2, cz - sz / 2, sz, 600)
        im.save(OUT + f'lay_{nm}.png')
    led = [L for k, L in lines if k == 'ledge'][0]
    lo_, hi_ = led.min(0), led.max(0); c_ = (lo_ + hi_) / 2; sz = max(hi_ - lo_) + 1200
    im, dr, S, P = draw(c_[0] - sz / 2, c_[1] - sz / 2, sz, 700)
    for m in ('m50', 'm60'):
        X, Y = P(CEN[m]); dr.text((X, Y), NAMES[m], fill=(255, 255, 255), font=font(30), anchor='mm', stroke_width=3, stroke_fill=(20, 20, 20))
    im.save(OUT + 'lay_ledge.png'); print('ledge box', lo_.round(), hi_.round(), round(float(np.hypot(*np.diff(led, axis=0).T).sum())))
    print([v['name'] for v in VIL])
