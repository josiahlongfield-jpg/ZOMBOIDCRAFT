# Third-person preview by the game's maths: her clip's first frame, the arm bones from the pose file (Spine1-relative,
# pack space), the split fingers, the whole body skinned; the gun from the pose file. Writes tpfull/scenes.json.
import sys, json, numpy as np
sys.path.insert(0, '.')
import gameview as gv, clips, full_export as fe
MODEL = 'pz_survivor_f'
names, par, inv, C = clips.load('../f.zsk', MODEL)
_, inv_, P, J, W, Tr_arm, side = gv.pack(MODEL)
body = fe.body; Tr = body['tris']
n = len(names); REF = names.index('Bip01_Spine1')
def qm(q): return gv.qmat(q)
def Mx(p, q): return gv.M(p, q)
def skinned(pose, clip):
    c = C[clip]; m = [Mx(c['p'][0, b], c['q'][0, b]) for b in range(n)]
    S = m[REF]
    W_ = {}
    for k, v in pose['bones'].items(): W_[k] = S @ Mx(v[:3], v[3:])
    for b in range(n):
        if names[b] in W_: m[b] = W_[names[b]]
    sk = [m[b] @ inv[b] for b in range(n)]
    for s, sd in enumerate('RL'):
        f1 = names.index(f'Bip01_{sd}_Finger1')
        for k in range(4): sk.append(W_.get(f'Bip01_{sd}_Finger1_{gv.FING[k]}', m[f1]) @ inv[f1])
    Mm = np.stack(sk); ph = np.concatenate([P, np.ones((len(P), 1))], 1); out = np.zeros((len(P), 3))
    for k in range(4): out += W[:, k:k + 1] * np.einsum('vij,vj->vi', Mm[J[:, k]], ph)[:, :3]
    return out[Tr].reshape(-1, 3), body['uv'][Tr].reshape(-1, 2), S
scenes = {}
def main():
    for gid in json.load(open('num_bare.json'))['numbers']['guns']:
        Pz = json.load(open(f'../poses_tp/{gid}.json')); pos, uv = fe.zmesh_full(gid)
        for mode in ('tpready', 'tpaim'):
            p = Pz[mode]; ap, au, S = skinned(p, p['clip']); g = p['gun']
            loc = (pos / 16 - 0.5) * g['scale'] @ qm(g['q']).T + np.array(g['t'])
            gun = loc @ S[:3, :3].T + S[:3, 3]
            scenes[f'{gid}_{mode}'] = {'gun': gid, 'gp': fe.b64(gun), 'gu': fe.b64(uv), 'ap': fe.b64(ap), 'au': fe.b64(au), 'aim': mode == 'tpaim'}
    json.dump(scenes, open('tpfull/scenes.json', 'w')); print(len(scenes))

if __name__ == '__main__': main()
