# Posing tool numbers -> config/zomboidcraft/guns/poses/<id>.json (client GunPoses).
# usage: python3 to_game.py numbers.json outdir [pz_survivor_f.zsk] [attachment runs...]
#   (numbers.json = {"state":..,"numbers":{"guns":{..}}}, his poses with no attachments; each attachment run is the tool's
#   numbers with one sight or one grip on each gun, made by brita/tools/attach/runs.py)
# First person (hip/ads): the hand frame (eye at -ARM). Third person (tpready/tpaim, needs the survivor pack for the
# clips' first frames): everything in her Bip01_Spine1's own frame at that frame, in the pack's space (metres,
# facing +z, x to her left), so the game lays the arms and gun off her chest wherever the clip has it.
import json, sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ARM = np.array([0.56, -0.52, -0.72])
REF = 'Bip01_Spine1'
def qrot(q, v):
    x, y, z, w = q; u = np.array([x, y, z]); v = np.asarray(v, float)
    return v + 2 * np.cross(u, np.cross(u, v) + w * v)
def qmul(a, b):
    ax, ay, az, aw = a; bx, by, bz, bw = b
    return np.array([aw*bx + ax*bw + ay*bz - az*by, aw*by - ax*bz + ay*bw + az*bx, aw*bz + ax*by - ay*bx + az*bw, aw*bw - ax*bx - ay*by - az*bz])
def qconj(q): return np.array([-q[0], -q[1], -q[2], q[3]])
r5 = lambda a: [round(float(c), 5) for c in a]
def conv(m):
    g = m['gun']; q = g['quat']; s = g['scale']
    t = np.array(g['gripEye']) - ARM - qrot(q, s * (np.array(g['gripPx']) / 16 - 0.5))
    bones = {k: r5(list(np.array(b['pos']) - ARM) + list(b['quat'])) for k, b in m['bonesEye'].items()}
    return {'gun': {'t': r5(t), 'q': q, 'scale': s}, 'bones': bones}
# the tool's body frame is the pack's turned half round y: points (-x, y, -z); a bone's turn q -> (-qx, qy, -qz, qw)
TURN = np.array([0.0, 1.0, 0.0, 0.0])
toP = lambda p: np.array([-p[0], p[1], -p[2]])
def conv_tp(m, ref):
    rp, rq = ref; rqi = qconj(rq)
    loc = lambda p: qrot(rqi, toP(p) - rp)
    g = m['gun']; s = g['scale']
    gq = qmul(rqi, qmul(TURN, g['quat']))                          # the gun's own frame is not turned, only her body's
    t = loc(g['gripBody']) + qrot(gq, s * (0.5 - np.array(g['gripPx']) / 16))
    bones = {}
    for k, b in m['bonesBody'].items():
        bq = b['quat']; bones[k] = r5(list(loc(b['pos'])) + list(qmul(rqi, [-bq[0], bq[1], -bq[2], bq[3]])))
    return {'clip': m['clip'], 'gun': {'t': r5(t), 'q': r5(gq / np.linalg.norm(gq)), 'scale': s}, 'bones': bones}
d = json.load(open(sys.argv[1])); d = d.get('numbers', d)
os.makedirs(sys.argv[2], exist_ok=True)
refs = {}
if len(sys.argv) > 3:
    import clips
    names, par, inv, C = clips.load(sys.argv[3], 'pz_survivor_f')
    i = names.index(REF)
    refs = {k: (c['p'][0, i], c['q'][0, i]) for k, c in C.items()}
runs = [json.load(open(f)) for f in sys.argv[4:]]
runs = [r.get('numbers', r) for r in runs]
for gid, g in d['guns'].items():
    o = {'from': 'posing tool ' + d.get('tool', ''), 'hip': conv(g['hip']), 'ads': conv(g['ads'])}
    for m in ('tpready', 'tpaim'):
        if m in g and g[m]['clip'] in refs: o[m] = conv_tp(g[m], refs[g[m]['clip']])
    optics, grips = {}, {}
    for r in runs:
        rg = r['guns'].get(gid)
        at = rg and rg['ads'].get('att')
        if not at: continue
        o['k'] = at['metresToGunUnits']
        if 'optic' in at and 'grip' not in at:
            optics[at['optic']['id']] = {'mount': at['optic']['mountPx'], 'ads': conv(rg['ads'])}
        if 'grip' in at and 'optic' not in at:
            hands = {}
            for m in ('hip', 'ads'):
                hands[m] = {k: v for k, v in conv(rg[m])['bones'].items() if k.startswith('L_')}
            for m in ('tpready', 'tpaim'):
                if m in rg and rg[m]['clip'] in refs:
                    hands[m] = {k: v for k, v in conv_tp(rg[m], refs[rg[m]['clip']])['bones'].items() if k.startswith('Bip01_L_')}
            grips[at['grip']['id']] = dict(mount=at['grip']['mountPx'], **hands)
    if optics or grips: o['optics'] = optics; o['grips'] = grips
    json.dump(o, open(os.path.join(sys.argv[2], gid + '.json'), 'w'), indent=1)
    print(gid, [k for k in o if k.startswith('tp')], len(optics), 'optics', len(grips), 'grips')
