"""Reads a .zsk back the way NpcSkin does and poses it the way the game will (pose = translationRotate(clip) * inverse),
writing posed meshes + textures for zskview.html: proves the pack and the game's maths agree."""
import sys, struct, zlib, io, json, os, numpy as np
sys.path.insert(0, '/home/user/zomboidcraft/tools/npc')
from pack import scramble
from scipy.spatial.transform import Rotation as Rot
path, model, clip, frame, out = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5]
pre = None
if len(sys.argv) > 6: pre = json.loads(sys.argv[6])  # optional extra: {"ads":1} applies the ads matrix to every bone
d = zlib.decompress(scramble(open(path, 'rb').read(), model)); b = io.BytesIO(d)
u = lambda f: struct.unpack('>' + f, b.read(struct.calcsize('>' + f)))
utf = lambda: b.read(u('H')[0]).decode()
assert b.read(4) == b'ZSKN' and u('i')[0] == 1
G = []
for _ in range(u('i')[0]):
    mesh, piece, tex = utf(), utf(), utf(); fl, lv, n = u('bbi')
    rec = np.frombuffer(b.read(n * 27), dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('u', '>u2', 2), ('j', 'i1', 4), ('w', 'u1', 4)])
    t = u('i')[0]; T = np.frombuffer(b.read(t * 12), '>i4').reshape(-1, 3)
    G.append((mesh, piece, tex, rec, T))
os.makedirs(out, exist_ok=True)
texs = {}
for _ in range(u('i')[0]):
    k = utf(); data = b.read(u('i')[0]); ext = 'jpg' if data[:2] == b'\xff\xd8' else 'png'
    open(f'{out}/{k}.{ext}', 'wb').write(data); texs[k] = f'{k}.{ext}'
nb = u('i')[0]; inv = []; names = []
for _ in range(nb):
    names.append(utf()); u('b'); m = np.array(u('12f')).reshape(3, 4); M = np.eye(4); M[:3] = m; inv.append(M)
u('i'); clips = {}
for _ in range(u('i')[0]):
    nm = utf(); fr, fps, bn = u('ifi')
    rec = np.frombuffer(b.read(fr * bn * 14), dtype=[('q', '>i2', 4), ('p', '>i2', 3)]).reshape(fr, bn)
    clips[nm] = rec
r = clips[clip][min(frame, len(clips[clip]) - 1)]
pose = []
for i in range(nb):
    q = r[i]['q'] / 32767.; q /= np.linalg.norm(q); M = np.eye(4); M[:3, :3] = Rot.from_quat(q).as_matrix(); M[:3, 3] = r[i]['p'] / 1000.
    pose.append(M)
if pre and 'ads' in clips:
    a = clips['ads'][0][0]; q = a['q'] / 32767.; A = np.eye(4); A[:3, :3] = Rot.from_quat(q / np.linalg.norm(q)).as_matrix(); A[:3, 3] = a['p'] / 1000.
    pose = [A @ M for M in pose]
skin = [pose[i] @ inv[i] for i in range(nb)]
objs = []
show = set(pre.get('pieces')) if pre and pre.get('pieces') else None
for mesh, piece, tex, rec, T in G:
    if show and piece not in show: continue
    P = np.c_[rec['p'], np.ones(len(rec))]; W = rec['w'] / 255.
    out_p = np.zeros((len(rec), 3))
    for k in range(4):
        Mk = np.array([skin[j] for j in rec['j'][:, k]])
        out_p += W[:, k:k + 1] * np.einsum('vij,vj->vi', Mk, P)[:, :3]
    U = rec['u'] / 65535.
    objs.append({'tex': texs[tex], 'p': out_p[T.reshape(-1)].round(4).reshape(-1).tolist(), 'u': U[T.reshape(-1)].round(4).reshape(-1).tolist()})
json.dump(objs, open(f'{out}/scene.json', 'w')); print(len(objs), 'objects', nb, 'bones', list(clips))
