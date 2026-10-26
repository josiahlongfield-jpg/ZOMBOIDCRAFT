# Render exported quads (rest pose) front and side, z-buffered, to check placement and face orientation
import json, sys, numpy as np
from PIL import Image
o = sys.argv[1]; d = json.load(open(f'out/{o}.json')); atlas = np.array(Image.open(f'out/{o}.png').convert('RGBA'))
skin = np.array(Image.open(f'/mnt/project-files/zomboid-mod/skin-template/ai-skin-15/{o}_1024.png').convert('RGBA'))
B = d['bones']; off = []
for b in B:
    p = np.array(b['pos']); 
    if b['parent'] >= 0: p = p + off[b['parent']]
    off.append(p)
S = 14; W, H = 1200, 560
img = np.full((H, W, 3), 255, np.uint8); zb = np.full((H, W), -1e9)
def draw(q, tex, cx, view, stump_ok):
    if q.get('stump') and not stump_ok: return
    P = np.array(q['p']).reshape(4, 3) + off[q['b']]
    n = np.array(q['n'])
    if view == 'side': P = P[:, [2, 1, 0]] * [-1, 1, 1]; n = n[[2, 1, 0]] * [-1, 1, 1]
    if n[2] <= 0: return
    sx = cx + P[:, 0] * S; sy = H - 20 - (P[:, 1] - 0.4) * S
    u0, v0, u1, v1 = q['uv']; th, tw = tex.shape[:2]
    a = np.array([sx[0], sy[0]]); e1 = np.array([sx[1], sy[1]]) - a; e2 = np.array([sx[3], sy[3]]) - a
    det = e1[0] * e2[1] - e1[1] * e2[0]
    if abs(det) < 1e-6: return
    x0, x1 = int(max(0, sx.min())), int(min(W - 1, sx.max())); y0, y1 = int(max(0, sy.min())), int(min(H - 1, sy.max()))
    if x1 < x0 or y1 < y0: return
    ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]; dx = xs + .5 - a[0]; dy = ys + .5 - a[1]
    s = (dx * e2[1] - dy * e2[0]) / det; t = (e1[0] * dy - e1[1] * dx) / det
    m = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
    z = P[0, 2] + s * (P[1, 2] - P[0, 2]) + t * (P[3, 2] - P[0, 2])
    tu = np.clip(((u0 + s * (u1 - u0)) * tw).astype(int), 0, tw - 1); tv = np.clip(((v0 + t * (v1 - v0)) * th).astype(int), 0, th - 1)
    c = tex[tv, tu]; m &= c[..., 3] > 127; m &= z > zb[ys, xs]
    zb[ys[m], xs[m]] = z[m]; img[ys[m], xs[m]] = c[m][:, :3]
for cx, view in ((300, 'front'), (900, 'side')):
    for q in d['body']: draw(q, skin, cx, view, False)
    for q in d['kit']: draw(q, atlas, cx, view, False)
Image.fromarray(img).save(f'out/{o}_check.png')
