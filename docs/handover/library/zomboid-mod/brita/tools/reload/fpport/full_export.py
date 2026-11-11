import sys, io, json, struct, zlib, base64, numpy as np
sys.path.insert(0, '.')
import zsk2, gameview as gv
from PIL import Image
MODEL = 'pz_survivor_f'
G, names, par, inv = zsk2.load(f'/mnt/project-files/zomboid-mod/survivor/packs/{MODEL}.zsk', MODEL)
TEX = zsk2.LASTTEX
body = [g for g in G if g['piece'] == 'body' and g['level'] == 0][0]
names_, inv_, P, J, W, Tr, side = gv.pack(MODEL)
skin = Image.open(io.BytesIO(TEX['skin/tomb/1/normal'])).convert('RGB'); skin.save('full/skin.png')
def zmesh_full(gid):
    raw = zlib.decompress(gv.cp.scramble(open(f'{gv.GUNS}/{gid}.zmesh', 'rb').read(), 'local/' + gid))
    b = io.BytesIO(raw); b.read(12); png = b.read(struct.unpack('>i', b.read(4))[0])
    open(f'full/{gid}.png', 'wb').write(png)
    pos = []; uv = []
    for _ in range(struct.unpack('>i', b.read(4))[0]):
        name = b.read(struct.unpack('>H', b.read(2))[0]).decode(); t = struct.unpack('>i', b.read(4))[0]
        rec = np.frombuffer(b.read(t * 39), dtype=[('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'), ('s', 'V3')])
        pos.append(np.stack([rec['x'], rec['y'], rec['z']], -1) / 1000.0); uv.append(np.stack([rec['u'], rec['v']], -1) / 65535.0)
    return np.concatenate(pos), np.concatenate(uv)
def armsfull(bones):
    # same skinning as gameview.arms but per vertex, then the kept triangles
    n = len(names_); m = [None] * (n + 8); sh = {}
    for s, sd in enumerate('RL'):
        sh[s] = np.array(bones[sd + '_UpperArm'][:3])
        for part in ['UpperArm', 'Forearm', 'Hand', 'Finger0', 'Finger1']:
            bi = names_.index(f'Bip01_{sd}_{part}'); v = bones[f'{sd}_{part}']; m[bi] = gv.M(v[:3], v[3:]) @ inv_[bi]
        f1 = names_.index(f'Bip01_{sd}_Finger1')
        for k in range(4):
            v = bones.get(f'{sd}_Finger1_{gv.FING[k]}', bones[f'{sd}_Finger1']); m[n + s * 4 + k] = gv.M(v[:3], v[3:]) @ inv_[f1]
    for bi in range(n + 8):
        if m[bi] is None:
            z = np.zeros((4, 4)); z[:3, 3] = sh[side[bi]]; z[3, 3] = 1; m[bi] = z
    Mm = np.stack(m); ph = np.concatenate([P, np.ones((len(P), 1))], 1); out = np.zeros((len(P), 3))
    for k in range(4): out += W[:, k:k + 1] * np.einsum('vij,vj->vi', Mm[J[:, k]], ph)[:, :3]
    return out[Tr].reshape(-1, 3), body['uv'][Tr].reshape(-1, 2)
def b64(a): return base64.b64encode(np.ascontiguousarray(a, dtype='<f4').tobytes()).decode()
scenes = {}
def main():
    gp = {}
    for gid in json.load(open('num_bare.json'))['numbers']['guns']:
        Pz = json.load(open(f'poses_out/{gid}.json')); pos, uv = zmesh_full(gid)
        for mode in ('hip', 'ads'):
            p = Pz[mode]; g = p['gun']; q = gv.qmat(g['q'])
            gun = (pos / 16 - 0.5) * g['scale'] @ q.T + np.array(g['t']) + gv.ARM
            ap, au = armsfull(p['bones']); ap = ap + gv.ARM
            scenes[f'{gid}_{mode}'] = {'gun': gid, 'gp': b64(gun), 'gu': b64(uv), 'ap': b64(ap), 'au': b64(au)}
    json.dump(scenes, open('full/scenes.json', 'w'))
    print(len(scenes))
    
if __name__ == '__main__': main()