"""Scratch: ride profile (speed, time, depth) for the Glass Line shuttle, data for the route map."""
import json, math
d = json.load(open('/tmp/claude-0/plan/tube/shuttle.json'))
P0 = d['pts']; m = d['marks']
# resample at exactly one block along the path
cum = [0.0]
for i in range(1, len(P0)):
    cum.append(cum[-1] + math.hypot(P0[i][0]-P0[i-1][0], P0[i][1]-P0[i-1][1]))
P = []; j = 0
for k in range(int(cum[-1])):
    while cum[j + 1] < k: j += 1
    f = (k - cum[j]) / max(1e-9, cum[j + 1] - cum[j])
    P.append((P0[j][0] + (P0[j+1][0] - P0[j][0]) * f, P0[j][1] + (P0[j+1][1] - P0[j][1]) * f))
n = len(P)
def radius(i):
    a, b, c = P[(i-4) % n], P[i], P[(i+4) % n]
    area2 = abs((b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0]))
    return 1e9 if area2 < 1e-6 else math.dist(a, b)*math.dist(b, c)*math.dist(c, a)/(2*area2)
WN, WS = -109, 151
def wet(x, z): return WN < z < WS
def nat_bed(x, z):
    dist = min(z - WN, WS - z); t = max(0, min(1, dist / 40)); t = t*t*(3-2*t)
    return 60.5 + (48 - 60.5) * t
def nearest(pt): return min(range(n), key=lambda i: (P[i][0]-pt[0])**2 + (P[i][1]-pt[1])**2)
im = nearest(((m['mercy_a'][0]+m['mercy_b'][0])/2, m['mercy_a'][1]))
iq = nearest(((m['quay_a'][0]+m['quay_b'][0])/2, m['quay_a'][1]))
VMAX, ACC, BRK, GRIP = 9.0, 0.6, 0.75, 1.1
def ride(i0, i1):
    run = [(i0 + k) % n for k in range((i1 - i0) % n + 1)]
    L = len(run)
    lim = [min(VMAX, math.sqrt(GRIP * radius(i))) for i in run]
    v = [0.0] * L
    for k in range(1, L): v[k] = min(lim[k], math.sqrt(v[k-1]**2 + 2*ACC))
    v[-1] = 0
    for k in range(L-2, -1, -1): v[k] = min(v[k], math.sqrt(v[k+1]**2 + 2*BRK))
    t = [0.0]
    for k in range(1, L): t.append(t[-1] + 1 / max(0.3, (v[k] + v[k-1]) / 2))
    return run, t
run, t = ride(im, iq)
run2, t2 = ride(iq, im)
L = len(run)
# bed: 46 everywhere, dipping to 40 in the long water run (3% each way)
wk = [k for k in range(L) if wet(*P[run[k]])]
# the first wet run (the tube out to the quay)
first = []
for k in wk:
    if first and k != first[-1] + 1: break
    first.append(k)
a0, a1 = first[0], first[-1]
q0 = next(k for k in first if P[run[k]][1] > 128)
ys = []
for k in range(L):
    if a0 <= k <= q0:
        ys.append(max(40, 46 - 0.03 * (k - a0), 46 - 0.03 * (q0 - k)))
    else:
        ys.append(46.0)
tube = list(range(a0, q0))
print("tube proper", len(tube), "blocks", round(t[q0] - t[a0]), "s")
wet_time = t[first[-1]] - t[first[0]]
print("Mercy->Quay", L, "blocks", round(t[-1]), "s; first water run", len(first), "blocks", round(wet_time), "s; tube", len(tube))
print("Quay->Mercy", len(run2), "blocks", round(t2[-1]), "s")
wk2 = [k for k in range(len(run2)) if wet(*P[run2[k]])]
print("water back", len(wk2), "blocks", round(t2[wk2[-1]] - t2[wk2[0]]), "s")
step = 4
out = {
    'pts': [[round(x, 1), round(z, 1)] for x, z in P],
    'marks': m,
    'run': {'y': [round(ys[k], 2) for k in range(0, L, step)],
            'nat': [round(nat_bed(*P[run[k]]), 2) if wet(*P[run[k]]) else None for k in range(0, L, step)],
            't': [round(t[k], 1) for k in range(0, L, step)],
            'x': [[round(P[run[k]][0], 1), round(P[run[k]][1], 1)] for k in range(0, L, step)]},
    'times': {'there': round(t[-1]), 'back': round(t2[-1]), 'wet': round(wet_time), 'wetLen': len(tube), 'tubeT': round(t[q0] - t[a0]), 'lap': n},
}
open('/tmp/claude-0/plan/tube/tube.js', 'w').write('window.T = ' + json.dumps(out) + ';\n')
