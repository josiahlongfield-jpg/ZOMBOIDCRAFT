"""villages + road network"""
import json, subprocess, numpy as np, random
exec(open('layout.py').read().split("# which mission")[0])
g = np.load("grids.npz"); sea = g["sea"]; mask, lab, slope, dfoot, dmis, dsea = g['mask'], g['lab'], g['slope'], g['dfoot'], g['dmis'], g['dsea']
keys = list(OFF); foot = mask == 2
rng = random.Random(11)
def boxmean(a, r):
    c = np.cumsum(np.cumsum(np.pad(a, ((r + 1, r), (r + 1, r)), mode='edge'), 0), 1)
    return (c[2 * r + 1:, 2 * r + 1:] - c[:-2 * r - 1, 2 * r + 1:] - c[2 * r + 1:, :-2 * r - 1] + c[:-2 * r - 1, :-2 * r - 1]) / (2 * r + 1) ** 2
flat = boxmean(slope, 5)
edge = np.minimum(np.minimum(xx, N - 1 - xx), np.minimum(yy, N - 1 - yy)) * CELL
# ---- villages
cand = (np.isin(clim, (2, 3, 4))) & (mask == 0) & ~sea & (dmis > 900) & (edge > 1200) & (flat < 0.05) & (H < 450)
inland = cand & (dsea > 400); coast = cand & (dsea > 40) & (dsea < 260)
NAMES_V = ["Hidegkut", "Darvas", "Retfalu", "Tolgyes", "Szenthaza", "Kovar", "Ladany", "Bokros", "Feherto", "Kisret", "Nagyerdo", "Malomvolgy",
           "Szelmalom", "Kopasz", "Hegyalja", "Kut"]
COAST_V = ["Halaszfalu", "Kikoto", "Sosmocsar", "Partos"]
def pick(cmask, n, spacing, score):
    rs, cs_ = np.nonzero(cmask); order = np.argsort(-score[rs, cs_] + np.array([rng.random() * 0.02 for _ in rs]))
    out = []
    for k in order:
        x, z = c2w(rs[k], cs_[k])
        if all(np.hypot(x - a, z - b) > spacing for a, b, *_ in out + placed) and all(np.hypot(x - CEN[m][0], z - CEN[m][1]) > 2200 for m in keys):
            out.append((x, z)); 
            if len(out) >= n: break
    return out
placed = []
sc = -flat * 10 - np.abs(H - 120) / 600 + (clim == 3) * 0.3
vil = pick(inland, 14, 2400, sc); placed += vil
cvil = pick(coast, 4, 2600, sc); placed += cvil
V = [dict(name=NAMES_V[i], x=x, z=z, kind='village') for i, (x, z) in enumerate(vil)] + [dict(name=COAST_V[i], x=x, z=z, kind='harbour') for i, (x, z) in enumerate(cvil)]
print(len(vil), len(cvil), flush=True)
# ---- roads
base = np.ones((N, N), np.float32)
base[mask == 1] = 2.5
base[(clim == 1)] *= 1.6
base[sea] = 1e9; base[foot] = 1e9; base[edge < 64] = 1e9
H.astype(np.float32).tofile('h.f32')
def route(src, tgt, gmax=0.11, cost=base, hfile='h.f32'):
    c = cost.copy(); c[tgt] = np.minimum(c[tgt], 3.0)
    c.astype(np.float32).tofile('c.f32'); src.astype(np.uint8).tofile('s.u8'); tgt.astype(np.uint8).tofile('t.u8')
    r = subprocess.run(['./dij', str(N), str(N), str(CELL), 'c.f32', hfile, 's.u8', 't.u8', 'p.i32', str(gmax)])
    a = np.fromfile('p.i32', np.int32)
    if a[0] == 0: return None, 0
    return a[1:1 + a[0]], a[1 + a[0]:].view(np.float32)[0]
def paint(p):
    rr, cc = np.divmod(np.asarray(p), N)
    for k in range(len(rr)):
        road[rr[k], cc[k]] = True
        if k: road[(rr[k] + rr[k - 1]) // 2, (cc[k] + cc[k - 1]) // 2] = True; road[(rr[k] + rr[k - 1] + 1) // 2, (cc[k] + cc[k - 1] + 1) // 2] = True
OFF_KEY = {v: k for k, v in NAMES.items()}
hubs = []  # (name, target mask)
for i, m in enumerate(keys):
    land = lab == i
    ring = dil(land, 2) & ~land & ~sea
    hubs.append((NAMES[m], ring, 'mission'))
lw = np.hypot(WX - 2300, WZ - 6900) < 140
hubs.append(('Lone Wolf', lw, 'mission'))
for v in V:
    hubs.append((v['name'], np.hypot(WX - v['x'], WZ - v['z']) < 60, v['kind']))
shore = base.copy(); near = sea & dil(~sea, 4) & dil((mask > 0) & ~sea, 60)
shore[near] = 4.0
np.where(sea, SEA, H).astype(np.float32).tofile('hs.f32')
road = np.zeros((N, N), bool); cls = np.zeros((N, N), np.uint8)
start = [k for k, h in enumerate(hubs) if h[0] == 'New Alexandria'][0]
road |= hubs[start][1]; done = {start}; edges = []
hub_id = np.full((N, N), -1, np.int16)
for k, h in enumerate(hubs): hub_id[h[1]] = k
while len(done) < len(hubs):
    tgt = np.zeros((N, N), bool)
    for k, h in enumerate(hubs):
        if k not in done: tgt |= h[1]
    kind_ = 'road'
    p, cost = route(road, tgt)
    for gm in (0.2, 0.3):
        if p is None: p, cost = route(road, tgt, gm); kind_ = 'track'
    if p is None:  # last resort (The Package): a ledge road cut into the cliffs just above the water, onto its shore
        low = np.zeros((N, N), bool)
        for k2 in range(len(hubs)):
            if k2 not in done and hubs[k2][0] in OFF_KEY:
                lk = (lab == keys.index(OFF_KEY[hubs[k2][0]])) & (mask == 2) & ~sea & (H < SEA + 25) & dil(sea, 2)
                hub_id[lk] = k2; low |= lk
        p, cost = route(road, low, 0.11, shore, 'hs.f32'); kind_ = 'ledge'
    if p is None: print('unreachable', [hubs[k][0] for k in range(len(hubs)) if k not in done]); break
    k = int(hub_id.flat[p[0]]); done.add(k)
    paint(p); road |= hubs[k][1] if hubs[k][2] != 'mission' else False
    edges.append((hubs[k][0], hubs[k][2] if kind_ == 'road' else kind_, p.tolist()))
    print('joined', hubs[k][0], len(p), round(float(cost)), flush=True)
# loops: where two places are close but the network goes far round, a direct road
from collections import deque
def netdist(a, b):
    sa = np.argwhere(dil(hubs[a][1], 1) & road); sb = dil(hubs[b][1], 1) & road
    if not len(sa): return 1e9
    D = {tuple(x): 0 for x in sa}; q = deque(map(tuple, sa))
    while q:
        r, c = q.popleft(); d = D[(r, c)]
        if sb[r, c]: return d * CELL
        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                n = (r + dr, c + dc)
                if 0 <= n[0] < N and 0 <= n[1] < N and road[n] and n not in D: D[n] = d + 1; q.append(n)
    return 1e9
def centre(k): r, c = np.argwhere(hubs[k][1]).mean(0); return c2w(r, c)
cheap = base.copy(); 
for a in range(len(hubs)):
    for b in range(a + 1, len(hubs)):
        (xa, za), (xb, zb) = centre(a), centre(b); d = np.hypot(xa - xb, za - zb)
        if d > 5000 or 'mission' == hubs[a][2] == hubs[b][2]: continue
        nd = netdist(a, b)
        if nd < 2.2 * d: continue
        cheap[road] = 0.9
        p, cost = route(hubs[a][1], hubs[b][1], 0.11, cheap)
        if p is None: continue
        rr, cc = np.divmod(p, N); new = ~road[rr, cc]
        if new.sum() == 0 or new.sum() * CELL > 0.9 * d: continue  # no real saving: it would just be a second road beside the first
        paint(p); edges.append((hubs[a][0] + '-' + hubs[b][0], 'loop', p.tolist()))
        print('loop', hubs[a][0], hubs[b][0], round(d), round(nd), int(new.sum()), flush=True)
json.dump(dict(villages=V, edges=edges), open('roads.json', 'w'))
np.save('road.npy', road)
