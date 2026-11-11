# python3 export_kit2.py OUTDIR [gid...]: editor kits for wave-2 guns, each with its reload ported from Josiah's edited
# template (port.js) as kit.views (LOCAL-ONLY data: scratch, project files and private artifacts only).
import shutil, sys, os, json, base64, subprocess, copy, numpy as np
ED = '/mnt/project-files/zomboid-mod/brita/tools/reload-editor'
sys.path[:0] = ['/mnt/project-files/zomboid-mod/brita/tools/reload', ED, os.path.dirname(os.path.abspath(__file__)), '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/wave2']
import engine as e, specs, ar15, importlib
specs2 = importlib.import_module(os.environ.get("SPECMOD", "specs3"))
specs.SPECS['Bush_AR15_MOE'] = ar15.SPEC
SP = specs2.SP; ALTP = specs2.ALTP; RED = f'{SP}/redit'
OUT = os.path.abspath(sys.argv[1]); os.makedirs(OUT, exist_ok=True)
R6 = lambda a: [round(float(x), 6) for x in np.asarray(a).reshape(-1)]
VIEWS = {'fp': 'hip', 'tp': 'tpready', 'ta': 'tpaim'}
def clean(o):
    if isinstance(o, dict): return {k: clean(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)): return [clean(v) for v in o]
    if isinstance(o, (np.floating, float)): return round(float(o), 5)
    if isinstance(o, np.integer): return int(o)
    return o
class RigX(e.Rig):
    def __init__(self, gid, view):
        P = json.load(open(f'{ALTP}/game/{gid}.json')); p = P.get(VIEWS[view]) or P['tpready' if view == 'ta' else VIEWS[view]]
        g = p['gun']; self.view = view; self.scale = g['scale']; self.Rg0 = e.qmat(g['q']); self.tg0 = np.array(g['t'])
        self.grip = np.array(json.load(open(f'{ALTP}/guns/{gid}.json'))['grip'], float)
        self.bones = {k: e.mat(np.array(v[:3]), e.qmat(v[3:])) for k, v in p['bones'].items()}
        if view == 'fp': self.place = e.mat(np.array(e.gv.ARM), np.eye(3)); self.clipm = None
        else:
            c = e.CLIPS[p['clip']]; self.clipm = [e.mat(c['p'][0, b], e.qmat(c['q'][0, b])) for b in range(e.NB)]
            self.place = self.clipm[e.SPINE]
gids = sys.argv[2:] or list(specs2.G)
for gid in gids:
    W = specs2.G[gid]; tspec = copy.deepcopy(specs.SPECS[W['template']])
    for d in W.get('drop', []):
        tspec['parts'].pop(d, None); tspec.get('moves', {}).pop(d, None)
    tspec['events'] = [x for x in tspec.get('events', []) if x[1] not in W.get('drop', [])]
    spec = {**tspec, 'parts': W['parts'], 'extra': W.get('extra', {})}
    parts_raw, tex, png = e.gun_tris(gid)
    groups = {}; taken = [np.zeros(len(p['pos']), bool) for p in parts_raw]; gout = {}
    for name, g in spec['parts'].items():
        if 'clone' in g: continue
        ms = e.select(parts_raw, g['sel'])
        for i, m in enumerate(ms): m &= ~taken[i]; taken[i] |= m
        groups[name] = ms
    groups['_body'] = [~t for t in taken]
    for name, ms in groups.items():
        pos = np.concatenate([p['pos'][m] for p, m in zip(parts_raw, ms)]).reshape(-1, 3)
        uv = np.concatenate([p['uv'][m] for p, m in zip(parts_raw, ms)]).reshape(-1, 2)
        gout[name] = {'p': e.b64(pos), 'u': e.b64(uv), 'c0': R6(pos.mean(0) if len(pos) else np.zeros(3))}
    for name, g in spec['parts'].items():
        if 'clone' in g: gout[name] = {'clone': g['clone']}
    for name, v in spec['extra'].items():
        pos, col = e.build_prims(v['prims']); gout[name] = {'p': e.b64(pos), 'c': e.b64(col), 'c0': R6(pos.mean(0))}
    rigs = {}
    for view in VIEWS:
        r = RigX(gid, view)
        rigs[view] = {'scale': r.scale, 'Rg0': R6(r.Rg0), 'tg0': R6(r.tg0), 'grip': R6(r.grip), 'place': R6(r.place),
                      'bones': {k: R6(m) for k, m in r.bones.items()}, 'clipm': [R6(m) for m in r.clipm] if r.clipm else None}
    sp = {k: v for k, v in spec.items() if k not in ('extra', 'armfit')}
    sp['parts'] = {k: {kk: vv for kk, vv in v.items() if kk != 'sel'} for k, v in spec['parts'].items()}
    sp['extraInfo'] = {k: {kk: vv for kk, vv in v.items() if kk != 'prims'} for k, v in spec['extra'].items()}
    # his edited template, carried onto this gun
    cfg = {'template': W['template'], 'anchors': W['anchors'], 'drop': W.get('drop', []), 'parts': sp['parts'], 'extraInfo': sp['extraInfo'],
           'after': W.get('after', []), 'match': W.get('match', []), 'rigN': rigs['fp']}
    cf = f'{OUT}/port_{gid}.json'; json.dump(cfg, open(cf, 'w'))
    subprocess.run(['node', 'port.js', cf, f'{OUT}/views_{gid}.json'], cwd=RED, check=True)
    views = json.load(open(f'{OUT}/views_{gid}.json'))
    fd = json.load(open(f'{ALTP}/guns/{gid}.json')).get('field')
    kit = {'field': fd, 'gid': gid, 'tex': base64.b64encode(png).decode(), 'groups': gout, 'rigs': rigs, 'spec': clean(sp), 'views': views,
           'from': W['template']}
    json.dump(kit, open(f'{OUT}/g_{gid}.json', 'w'))
    subprocess.run(['node', 'armfit0.js', f'{OUT}/g_{gid}.json'], cwd=RED, check=True)
    subprocess.run(['node', 'fit.js', f'{OUT}/g_{gid}.json'], cwd=RED, check=True)
    shutil.copy(f'{OUT}/g_{gid}.json', f'{RED}/kit/g_{gid}.json')
    print(gid, '<-', W['template'], os.path.getsize(f'{OUT}/g_{gid}.json'))
