"""Candidate preview cameras for a mission from its squad spawn points. usage: cams2.py <map> <n spots>
prints: url (one page load renders every candidate) and writes real/web/<map>_cands.json"""
import sys, json, os, glob, numpy as np
m, n = sys.argv[1], int(sys.argv[2])
W = '/tmp/claude-0/halo/real/web'
_sq = f'/mnt/project-files/zomboid-mod/halo/squads/sq_{m}.json'
d = json.load(open(_sq if os.path.exists(_sq) else f'/tmp/claude-0/halo/real/sq_{m}.json'))
P = np.array([pt['pos'] for s in d['squads'] for pt in s['points'] if pt.get('kind') == 'spawn'], np.float64)
P = P[np.isfinite(P).all(1) & (np.abs(P).max(1) > 1)]
cnt = np.array([(np.linalg.norm(P - p, axis=1) < 40).sum() for p in P])
picks = [int(np.argmax(cnt))]
while len(picks) < min(n, len(P)):
    dmin = np.min([np.linalg.norm(P - P[i], axis=1) for i in picks], 0)
    picks.append(int(np.argmax(dmin * np.sqrt(cnt))))
os.makedirs(f'{W}/{m}', exist_ok=True)
files = sorted(f for f in glob.glob(f'/tmp/claude-0/halo/real/{m}/*.glb') if not os.path.basename(f).startswith('sky_'))
for f in files + glob.glob(f'/tmp/claude-0/halo/real/{m}/*_lm.png') + glob.glob(f'/tmp/claude-0/halo/real/{m}/sky_*.glb'):
    dst = f'{W}/{m}/' + os.path.basename(f)
    if not os.path.exists(dst): os.symlink(f, dst)
if not os.path.lexists(f'{W}/{m}/tex'): os.symlink('/tmp/claude-0/halo/real/tex', f'{W}/{m}/tex')
fl = ','.join(f'{m}/' + os.path.basename(f) for f in files)
lm = ','.join((f'{m}/' + os.path.basename(f)[:-4] + '_lm.png') if os.path.exists(f[:-4] + '_lm.png') else '' for f in files)
SKYPICK = {'m60': 'glassing', 'm45': 'sky_mission', 'm70_bonus': 'sunset'}
sky = sorted(glob.glob(f'/tmp/claude-0/halo/real/{m}/sky_*.glb'), key=lambda f: (SKYPICK.get(m, '@') not in f, not any(k in os.path.basename(f) for k in ('mission', 'level', m)), 'cine' in f, f))
cands = []
for i in picks:
    t = P[i]; near = np.linalg.norm(P - t, axis=1)
    others = np.where((near > 12) & (near < 90))[0]
    for o in others[np.argsort(-cnt[others])][:2]:
        eye = P[o] + [0, 2.2, 0]; look = t + [0, 1.4, 0]
        cands.append(dict(spot=int(i), e=eye.tolist(), c=look.tolist(), fov=75))
    for ang in (0.6, 2.7, 4.8):
        eye = t + [70 * np.cos(ang), 35, 70 * np.sin(ang)]
        cands.append(dict(spot=int(i), e=eye.tolist(), c=(t + [0, 2, 0]).tolist(), fov=65))
cams = ';'.join('%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d' % (*c['e'], *c['c'], c['fov']) for c in cands)
A = json.load(open('/tmp/claude-0/halo/real/atmo.json')).get(m, {})
cx = A.get('cfxs'); fg = next(iter(A.get('fog', {}).values()), None)
atmo = '&auto=0&exp=2'
if cx:  # Halo auto exposure: per-camera log-average metering to the mission's target, clamped to its stop range (m20 calibrated to 2)
    atmo = '&auto=1&key=%.4f&expk=1&emin=%.2f&emax=%.2f' % (3.8 * cx['target'], cx['emin'], cx['emax'] + 1)
    atmo += '&exb=%.3f' % (2 * cx['target'] / 0.095)  # half-way between metered and the mission's fixed key: steadier, like the game's slow adaptation
if fg:
    r = fg['raw']; atmo += '&fogc=%.3f,%.3f,%.3f' % tuple(max(v, 0) ** (1 / 2.2) for v in r[14:17])
    atmo += '&fogd=%d' % min(8000, max(1500, 3.048 * max(r[5], r[13])))
EXTRA = {'m52': '&hfb=350&hft=120&hfd=6'}  # New Alexandria: haze filling the depths under the towers
atmo += EXTRA.get(m, '')
base = f'real.html?f={fl}&lm={lm}{atmo}&sks=0.5&w=960&h=540' + (f'&sky={m}/' + os.path.basename(sky[0]) if sky else '')
json.dump(dict(base=base.replace('&w=960&h=540', ''), cands=cands), open(f'{W}/{m}_cands.json', 'w'))
c0 = cands[0]
print(base + '&e=%.2f,%.2f,%.2f&c=%.2f,%.2f,%.2f' % (*c0['e'], *c0['c']) + '&cams=' + cams)
