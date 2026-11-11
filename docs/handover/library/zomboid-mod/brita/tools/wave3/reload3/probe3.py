# prints per gun: grip, mag top, right-side max-x point near the receiver above the magazine (charging handle guess)
import sys, json, numpy as np
sys.path.insert(0, '/mnt/project-files/zomboid-mod/brita/tools/reload')
import comps
SP = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad'
MF = json.load(open('/mnt/project-files/zomboid-mod/brita/tools/wave3/magframes3.json'))
for g in sys.argv[1:]:
    hdr, png, tex, parts, rest = comps.load(g)
    P = np.concatenate([np.stack([r['x'], r['y'], r['z']], -1).reshape(-1, 3) / 1000. for n, r in parts if n != 'mag'])
    gj = json.load(open(f'{SP}/posing3/guns/{g}.json')); grip = gj['grip']; line = gj['sights'].get('rear', [0, 0, 0])[1] if gj.get('sights') else None
    f = MF.get(g); top = [f['o'][i] + f['ax'][i] * f['L'] for i in range(3)] if f else None
    out = f'{g:16s} grip {np.round(grip,2).tolist()} magtop {np.round(top,2).tolist() if top else None} line {line} '
    if top:
        m = (np.abs(P[:, 2] - top[2]) < 3.5) & (P[:, 1] > top[1] + 0.3) & (P[:, 1] < (line or 99) - 0.3)
        Q = P[m]
        if len(Q): k = Q[:, 0].argmax(); out += f'rmax {np.round(Q[k],2).tolist()} lmin {np.round(Q[Q[:,0].argmin()],2).tolist()}'
    print(out)
