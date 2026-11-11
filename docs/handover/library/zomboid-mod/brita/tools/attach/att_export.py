# Step 3 preview by the game's maths: his picks (sight + grip) from the pose files + packed parts, as GunPoses.with,
# LocalAttachments.drawParts and RifleView compose them. Writes attfull/scenes.json (+ textures).
import sys, json, base64, gzip, io, numpy as np, os
sys.path.insert(0, '.')
import gameview as gv, full_export as fe, tp_export as te
from PIL import Image
os.makedirs('attfull', exist_ok=True)
ST = json.load(open('state_export.json'))
PARTS = 'tool/parts'
ARM = gv.ARM
def unz(s, dt): return np.frombuffer(gzip.decompress(base64.b64decode(s)), dt)
PD = {}
def part(pid):
    if pid in PD: return PD[pid]
    j = json.load(open(f'{PARTS}/{pid}.json'))
    pos = (unz(j['tris'], np.int16).astype(float) / 10000).reshape(-1, 3)
    uv = (unz(j['uv'], np.uint16).astype(float) / 65535).reshape(-1, 2)
    gl = unz(j['glass'], np.uint8) if j.get('glass') else np.zeros(len(pos) // 3, np.uint8)
    keep = np.repeat(gl == 0, 3)
    open(f'attfull/{pid}.png', 'wb').write(base64.b64decode(j['tex'].split(',', 1)[1]))
    lit = []
    for k, R in enumerate(j.get('reticles') or []):
        if R['main']: continue
        idx = (np.array(R['tri'])[:, None] * 3 + np.arange(3)).reshape(-1)
        open(f'attfull/{pid}_ret{k}.png', 'wb').write(base64.b64decode(R['tex'].split(',', 1)[1]))
        lit.append((f'{pid}_ret{k}', pos[idx], np.array(R['uv'], float).reshape(-1, 2)))
    PD[pid] = (pos[keep], uv[keep], lit)
    return PD[pid]
META = json.load(open('../partsout/parts.json'))
def Mg(g):
    m = np.eye(4); m[:3, :3] = gv.qmat(g['q']) * g['scale']; m[:3, 3] = g['t']; return m
def M7(v): return gv.M(v[:3], v[3:])
def dec(m):
    from scipy.spatial.transform import Rotation as Rr
    s = np.linalg.norm(m[:3, 0]); q = Rr.from_matrix(m[:3, :3] / s).as_quat(); return q, s
scenes = {}
for gid, a in ST['att'].items():
    optic = a.get('optic') or None; grip = a.get('grip') or None
    if not optic and not grip: continue
    P = json.load(open(f'../poses_att/{gid}.json'))
    if optic and optic not in P.get('optics', {}): print('no pose', gid, optic); continue
    k = P['k']; gpos, guv = fe.zmesh_full(gid)
    if optic:  # the no-irons mesh
        import zlib, struct
        raw = zlib.decompress(gv.cp.scramble(open(f'/mnt/project-files/zomboid-mod/brita/local-pc/guns-noirons/{gid}.zmesh', 'rb').read(), 'local/' + gid))
        b = io.BytesIO(raw); b.read(12); png = b.read(struct.unpack('>i', b.read(4))[0]); open(f'attfull/{gid}_noirons.png', 'wb').write(png)
        ps = []; us = []
        for _ in range(struct.unpack('>i', b.read(4))[0]):
            b.read(struct.unpack('>H', b.read(2))[0]); t = struct.unpack('>i', b.read(4))[0]
            rec = np.frombuffer(b.read(t * 39), dtype=[('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'), ('s', 'V3')])
            ps.append(np.stack([rec['x'], rec['y'], rec['z']], -1) / 1000.0); us.append(np.stack([rec['u'], rec['v']], -1) / 65535.0)
        gpos, guv = np.concatenate(ps), np.concatenate(us); gtex = gid + '_noirons'
    else:
        gtex = gid
    for mode in ('hip', 'ads', 'tpaim'):
        objs = []
        if mode == 'tpaim':
            p = dict(P['tpaim']); bones = dict(p['bones'])
            if grip: bones.update(P['grips'][grip]['tpaim'])
            p['bones'] = bones
            ap, au, S = te.skinned(p, p['clip'])
            G = S @ Mg(p["gun"]); G = G @ np.array([[1,0,0,-0.5],[0,1,0,-0.5],[0,0,1,-0.5],[0,0,0,1.0]])   # px/16 -> pack
            place = lambda px16: px16 @ G[:3, :3].T + G[:3, 3]
            objs.append(['skin', ap, au])
        else:
            hip = P['hip']; ads = P['optics'][optic]['ads'] if (optic and mode == 'ads') else P[mode]
            pose = ads if mode == 'ads' else hip
            bones = dict(pose['bones'])
            if grip:
                L = P['grips'][grip][mode]
                carry = Mg(pose['gun']) @ np.linalg.inv(Mg(P['ads']['gun'])) if mode == 'ads' else np.eye(4)
                for kk, v in L.items():
                    m = carry @ M7(v); q, _ = dec(m); bones[kk] = list(m[:3, 3]) + list(q)
            ap, au = fe.armsfull(bones); ap = ap + ARM
            g = pose['gun']
            place = lambda px16, g=g: (px16 - 0.5) * g['scale'] @ gv.qmat(g['q']).T + np.array(g['t']) + ARM
            objs.append(['skin', ap, au])
        objs.append([gtex, place(gpos / 16), guv])
        for pid in [x for x in (optic, grip) if x]:
            pos, uv, lit = part(pid)
            mount = np.array((P['optics'][pid] if pid == optic else P['grips'][pid])['mount'])
            pp = pos.copy()
            if pid == optic and mode == 'ads' and META[pid].get('mag', 1) <= 1:
                # the 1x shortening, as LocalAttachments.aiming
                back = META[pid]['back']; k16 = k * 16; g = P['optics'][pid]['ads']['gun']
                bp = (np.array([mount[0], mount[1] + META[pid]['line'] * k16, mount[2] + back * k16]) / 16 - 0.5) * g['scale'] @ gv.qmat(g['q']).T + np.array(g['t'])
                d = np.linalg.norm(bp + ARM - 0)  # eye at the origin of the eye frame (hand frame + ARM)
                d = np.linalg.norm(bp - (-ARM))
                if d < 0.15: pp[:, 2] = back + (pp[:, 2] - back) * d / 0.15
            objs.append([pid, place(mount / 16 + pp * k), uv])
            for lid, lp, luv in lit:
                lpp = lp.copy()
                objs.append([lid, place(mount / 16 + lpp * k), luv])
        scenes[f'{gid}_{mode}'] = {'tp': mode == 'tpaim', 'objs': [[n, fe.b64(p), fe.b64(u)] for n, p, u in objs]}
    print(gid, optic, grip)
json.dump(scenes, open('attfull/scenes.json', 'w'))
