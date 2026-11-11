"""Pick preview cameras for a mission from its squad spawn points (places the player actually fights through).
usage: cams.py <map> <n>  -> prints jobs json for shot.js (files served from real/web/<map>/ symlinks)"""
import sys, json, os, glob, numpy as np
m, n = sys.argv[1], int(sys.argv[2])
W = '/tmp/claude-0/halo/real/web'
d = json.load(open(f'/mnt/project-files/zomboid-mod/halo/squads/sq_{m}.json'))
P = np.array([pt['pos'] for s in d['squads'] for pt in s['points'] if pt.get('kind') == 'spawn'], np.float64)
# farthest-point picks of spawn spots, each with the busiest spot within 80 m as its centre
rng = np.random.default_rng(1)
cnt = np.array([(np.linalg.norm(P - p, axis=1) < 40).sum() for p in P])
picks = [int(np.argmax(cnt))]
while len(picks) < n:
    dmin = np.min([np.linalg.norm(P - P[i], axis=1) for i in picks], 0)
    picks.append(int(np.argmax(dmin * np.sqrt(cnt))))
os.makedirs(f'{W}/{m}', exist_ok=True)
files = sorted(glob.glob(f'/tmp/claude-0/halo/real/{m}/{m}_*.glb'))
for f in files + glob.glob(f'/tmp/claude-0/halo/real/{m}/*_lm.png') + glob.glob(f'/tmp/claude-0/halo/real/{m}/sky_*.glb'):
    dst = f'{W}/{m}/' + os.path.basename(f)
    if not os.path.exists(dst): os.symlink(f, dst)
if not os.path.exists(f'{W}/{m}/tex'): os.symlink('/tmp/claude-0/halo/real/tex', f'{W}/{m}/tex')
fl = ','.join(f'{m}/' + os.path.basename(f) for f in files)
lm = ','.join((f'{m}/' + os.path.basename(f)[:-4] + '_lm.png') if os.path.exists(f[:-4] + '_lm.png') else '' for f in files)
sky = glob.glob(f'/tmp/claude-0/halo/real/{m}/sky_*.glb')
base = f'real.html?f={fl}&lm={lm}&auto=0&exp=2&sks=0.5' + (f'&sky={m}/' + os.path.basename(sky[0]) if sky else '')
jobs = []
for k, i in enumerate(picks):
    t = P[i]
    near = np.linalg.norm(P - t, axis=1); cand = np.where((near > 15) & (near < 60))[0]
    if len(cand): c = P[cand[np.argmax(near[cand])]]
    else: c = t + np.array([30, 0, 30])
    eye = c + np.array([0, 2.5, 0]); look = t + np.array([0, 1.5, 0])
    # step back 10 m away from the target and up 6 m so the shot opens out
    dv = eye - look; dv[1] = 0; dv /= max(np.linalg.norm(dv), 1e-6); eye = eye + dv * 10 + np.array([0, 6, 0])
    jobs.append([base + '&e=%.2f,%.2f,%.2f&c=%.2f,%.2f,%.2f&fov=75' % (*eye, *look), f'{W}/{m}_v{k}.png', 1280, 720])
ctr = P.mean(0); span = np.ptp(P[:, [0, 2]], 0).max()
eye = ctr + np.array([span * 0.35, span * 0.35, span * 0.35])
jobs.append([base + '&e=%.2f,%.2f,%.2f&c=%.2f,%.2f,%.2f&fov=60' % (*eye, *ctr), f'{W}/{m}_air.png', 1280, 720])
print(json.dumps(jobs))
