import sys, io, numpy as np
sys.path.insert(0, '/home/user/zomboidcraft/tools/pz'); sys.path.insert(0, '/tmp/claude-0/halo')
import zsk_read as z
from raster import render
from PIL import Image
m = z.read('/mnt/project-files/zomboid-mod/tarkov/local-pc/npc/operator.zsk', 'operator')
clip = m['clips'][sys.argv[1] if len(sys.argv) > 1 else 'idle']; f = int(sys.argv[2]) if len(sys.argv) > 2 else 0
nb = clip['q'].shape[1]; n = len(m['bones'])
def qmat(q):
    x, y, zz, w = q
    return np.array([[1-2*(y*y+zz*zz), 2*(x*y-zz*w), 2*(x*zz+y*w)], [2*(x*y+zz*w), 1-2*(x*x+zz*zz), 2*(y*zz-x*w)], [2*(x*zz-y*w), 2*(y*zz+x*w), 1-2*(x*x+y*y)]])
skin = []
for i in range(n):
    if i < nb:
        W = np.eye(4); W[:3, :3] = qmat(clip['q'][f, i]); W[:3, 3] = clip['p'][f, i]
    else:
        W = np.linalg.inv(m['inverse'][i])
    skin.append(W @ m['inverse'][i])
skin = np.array(skin)
V, T, UV, MM, tex = [], [], [], [], {}
names = {}
off = 0
for g in m['groups']:
    p = np.c_[g['pos'], np.ones(len(g['pos']))]
    out = np.zeros((len(p), 3))
    for k in range(4):
        w = g['weight'][:, k]; j = g['joint'][:, k]
        out += w[:, None] * np.einsum('nij,nj->ni', skin[j], p)[:, :3]
    if g['texture'] not in names:
        names[g['texture']] = len(names)
        raw = m['textures'].get(g['texture'])
        tex[names[g['texture']]] = np.array(Image.open(io.BytesIO(raw)).convert('RGB')).astype(np.float32) if raw else np.full((2, 2, 3), 200, np.float32)
    V.append(out); UV.append(g['uv']); T.append(g['tris'] + off); MM.append(np.full(len(g['tris']), names[g['texture']])); off += len(p)
V = np.concatenate(V); T = np.concatenate(T); UV = np.concatenate(UV); MM = np.concatenate(MM)
print('bounds', V.min(0), V.max(0), len(T))
np.savez('/tmp/claude-0/op/posed.npz', V=V, T=T, UV=UV, MM=MM)
lo, hi = V.min(0), V.max(0); c = (lo + hi) / 2; hgt = hi[1] - lo[1]
eye = c + np.array([0.45, 0.08, 1.0]) / np.linalg.norm([0.45, 0.08, 1.0]) * hgt * 2.6
render(V, T, None, eye=eye, tgt=c, W=1200, H=1600, UV=UV, tex=tex, M=MM, fov=28, bg=(40, 44, 52)).save('/tmp/claude-0/op/op_' + (sys.argv[1] if len(sys.argv) > 1 else 'idle') + '.png')
