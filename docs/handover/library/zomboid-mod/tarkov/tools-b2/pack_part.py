"""Packs exported Tarkov parts (pexp.py) into one-bone .zsk models for bench fitting: model tkp_<id>, its meshes in the
frame of the slot that holds it (bone 'part', bound at rest), glass left out, reticles added as r<aim>_<mode>.
Local/private only. usage: pack_part.py PARTS_DIR MARKS_DIR OUT_DIR id..."""
import sys, json, os, re, io
import numpy as np
src = open('/tmp/claude-0/tk/b1/pack_gun.py').read().split('# usage: pack_gun.py')[0]
NS = {}; exec(compile(src, 'pack_gun_lib', 'exec'), NS)
PD, MD, OD = sys.argv[1:4]; os.makedirs(OD, exist_ok=True)
SIGHT = ('Collimator', 'CompactCollimator', 'AssaultScope', 'OpticScope', 'SpecialScope')
for pid in sys.argv[4:]:
    r = json.load(open(PD + '/' + pid + '.json'))
    if 'fail' in r: print('skip', pid, r['fail'][:60]); continue
    NS['GROUPS'].clear(); NS['TEX'].clear()
    size = 1024 if r.get('kind') in SIGHT else 512
    W = [np.array(m) for m in r['world']]
    keep = {m['node'] for m in r['meshes']}
    for i, n in enumerate(r['nodes']):
        if i not in keep or not n.get('mesh'): continue
        P, N, U, _, _, subs = NS['rd'](n['mesh'])
        M = W[i]; Pw = P @ M[:3, :3].T + M[:3, 3]; Nw = N @ M[:3, :3].T
        if np.linalg.det(M[:3, :3]) < 0: subs = [T[:, ::-1] for T in subs]   # a mirrored mesh: its faces turned back out
        J = np.zeros((len(P), 4), int); W4 = np.zeros((len(P), 4)); W4[:, 0] = 1
        mats = n.get('mats') or [None]
        for s, T in enumerate(subs):
            m = mats[min(s, len(mats) - 1)]
            if NS['glassy'](m) and 'reflex_sight' not in ((m or {}).get('name') or '').lower() or len(T) == 0: continue
            NS['group'](n['name'] + '.%d' % s, 'part', NS['tex'](m, size), Pw, Nw, U, J, W4, T)
    for k, png in (r.get('reticles') or {}).items():
        NS['TEX'][k] = open(MD + '/' + png, 'rb').read()
    if not NS['GROUPS']: print('empty', pid, r['name']); continue
    model = 'tkp_' + pid
    NS['write'](model, ['part'], [-1], [np.eye(4)], {}, OD + '/' + model + '.zsk')
