# Kit for the in-browser reload editor: body mesh + rigs + gun pieces + the hand-made specs (LOCAL-ONLY data: private artifact only).
import sys, os, json, base64, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import engine as e, specs, gzip
import ar15
specs.SPECS['Bush_AR15_MOE'] = ar15.SPEC
# Natural whole-arm solver + his AR-57 elbow habit for every gun except his own hand-tuned AR-57 (old solver kept there).
for _g, _s in specs.SPECS.items():
    if _g == 'AR57_PDW': continue
    _s.setdefault('solver', 'natural'); _s.setdefault('style', ar15.SPEC['style'])
ALTP = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/redit/posing'
def posef(gid):
    f = f'{e.POSES}/{gid}.json'
    return f if os.path.exists(f) else f'{ALTP}/game/{gid}.json'
def toolf(gid):
    f = f'{e.TOOLG}/{gid}.json'
    return f if os.path.exists(f) else f'{ALTP}/guns/{gid}.json'
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
R6 = lambda a: [round(float(x), 6) for x in np.asarray(a).reshape(-1)]
VIEWS = {'fp': 'hip', 'tp': 'tpready', 'ta': 'tpaim'}

def clean(o):
    if isinstance(o, dict): return {k: clean(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)): return [clean(v) for v in o]
    if isinstance(o, (np.floating, float)): return round(float(o), 5)
    if isinstance(o, np.integer): return int(o)
    return o

static = json.load(open('static.json'))
static.update({'names': e.NAMES, 'INV': [R6(m) for m in e.INV], 'INV_': [R6(m) for m in e.INV_], 'SIDE': [int(s) for s in e.SIDE],
               'FIST': e.FIST, 'FING': e.FING, 'ARM': R6(e.gv.ARM), 'SPINE': e.SPINE,
               'skin': base64.b64encode(open('skin.png', 'rb').read()).decode()})
# first person draws the whole arm (upper arm included), not just forearm + hand
_ab = {e.NAMES.index(f'Bip01_{sd}_{p}') for sd in 'RL' for p in ['UpperArm', 'Forearm', 'Hand', 'Finger0', 'Finger1']} | set(range(e.NB, e.NB + 8))
_J = np.asarray(e.BJ).astype(int); _W = np.asarray(e.BW)
_ok = np.array([sum(_W[v, k] for k in range(4) if _W[v, k] > 0 and _J[v, k] in _ab) > 0.6 for v in range(len(_J))])
_T = np.asarray(e.TR_BODY).reshape(-1, 3); static['trA2'] = e.b64(_T[_ok[_T].all(1)].reshape(-1).astype(np.float32))
json.dump(static, open(f'{OUT}/static.json', 'w'))

class RigX(e.Rig):
    def __init__(self, gid, view):
        P = json.load(open(posef(gid))); p = P[VIEWS[view]]
        g = p['gun']; self.view = view; self.scale = g['scale']; self.Rg0 = e.qmat(g['q']); self.tg0 = np.array(g['t'])
        self.grip = np.array(json.load(open(toolf(gid)))['grip'], float)
        self.bones = {k: e.mat(np.array(v[:3]), e.qmat(v[3:])) for k, v in p['bones'].items()}
        if view == 'fp': self.place = e.mat(np.array(e.gv.ARM), np.eye(3)); self.clipm = None
        else:
            c = e.CLIPS[p['clip']]; self.clipm = [e.mat(c['p'][0, b], e.qmat(c['q'][0, b])) for b in range(e.NB)]
            self.place = self.clipm[e.SPINE]

index = []
for gid, spec in specs.SPECS.items():
    parts_raw, tex, png = e.gun_tris(gid)
    groups = {}; taken = [np.zeros(len(p['pos']), bool) for p in parts_raw]; gout = {}
    for name, g in spec.get('parts', {}).items():
        if 'clone' in g: continue
        ms = e.select(parts_raw, g['sel'])
        for i, m in enumerate(ms): m &= ~taken[i]; taken[i] |= m
        groups[name] = ms
    groups['_body'] = [~t for t in taken]
    for name, ms in groups.items():
        pos = np.concatenate([p['pos'][m] for p, m in zip(parts_raw, ms)]).reshape(-1, 3)
        uv = np.concatenate([p['uv'][m] for p, m in zip(parts_raw, ms)]).reshape(-1, 2)
        gout[name] = {'p': e.b64(pos), 'u': e.b64(uv), 'c0': R6(pos.mean(0) if len(pos) else np.zeros(3))}
    for name, g in spec.get('parts', {}).items():
        if 'clone' in g: gout[name] = {'clone': g['clone']}
    for name, v in spec.get('extra', {}).items():
        pos, col = e.build_prims(v['prims']); gout[name] = {'p': e.b64(pos), 'c': e.b64(col), 'c0': R6(pos.mean(0))}
    rigs = {}
    for view in VIEWS:
        r = RigX(gid, view)
        rigs[view] = {'scale': r.scale, 'Rg0': R6(r.Rg0), 'tg0': R6(r.tg0), 'grip': R6(r.grip), 'place': R6(r.place),
                      'bones': {k: R6(m) for k, m in r.bones.items()}, 'clipm': [R6(m) for m in r.clipm] if r.clipm else None}
    sp = {k: v for k, v in spec.items() if k not in ('extra', 'armfit')}
    sp['parts'] = {k: {kk: vv for kk, vv in v.items() if kk not in ('sel',)} for k, v in spec.get('parts', {}).items()}
    sp['extraInfo'] = {k: {kk: vv for kk, vv in v.items() if kk != 'prims'} for k, v in spec.get('extra', {}).items()}
    fd = json.load(open(toolf(gid))).get('field')
    kit = {'field': fd, 'gid': gid, 'tex': base64.b64encode(png).decode(), 'groups': gout, 'rigs': rigs, 'spec': clean(sp)}
    json.dump(kit, open(f'{OUT}/g_{gid}.json', 'w'))
    index.append(gid); print(gid, os.path.getsize(f'{OUT}/g_{gid}.json'))
json.dump(index, open(f'{OUT}/index.json', 'w'))
