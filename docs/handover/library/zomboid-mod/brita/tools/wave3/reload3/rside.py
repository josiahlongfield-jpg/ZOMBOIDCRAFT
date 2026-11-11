# right-side (from +x) or left-side textured views with a px grid: python3 rside.py out.png side(r|l) gid...
import sys, json, numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, '/mnt/project-files/zomboid-mod/brita/tools/reload'); import comps
SP = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad'
MF = json.load(open('/mnt/project-files/zomboid-mod/brita/tools/wave3/magframes3.json'))
out, side, gids = sys.argv[1], sys.argv[2], sys.argv[3:]
S = 60; ims = []
for g in gids:
    hdr, png, tex, parts, rest = comps.load(g)
    T = np.concatenate([np.stack([r['x'], r['y'], r['z']], -1).reshape(-1, 3, 3) / 1000. for n, r in parts])
    UV = np.concatenate([np.stack([r['u'], r['v']], -1).reshape(-1, 3, 2) / 65535. for n, r in parts])
    lo = T.reshape(-1, 3).min(0) - 0.5; hi = T.reshape(-1, 3).max(0) + 0.5
    W = int((hi[2] - lo[2]) * S); H = int((hi[1] - lo[1]) * S)
    im = Image.new('RGB', (W, H + 18), (235, 235, 235)); d = ImageDraw.Draw(im)
    X = (lambda z: (hi[2] - z) * S) if side == 'r' else (lambda z: (z - lo[2]) * S)
    Y = lambda y: (hi[1] - y) * S + 18
    for z in range(int(lo[2]) + 1, int(hi[2]) + 1):
        d.line([(X(z), 18), (X(z), H + 18)], fill=(200, 200, 255) if z % 2 else (160, 160, 255)); d.text((X(z) + 1, 2), str(z), fill=(0, 0, 160))
    for y in range(int(lo[1]) + 1, int(hi[1]) + 1):
        d.line([(0, Y(y)), (W, Y(y))], fill=(200, 255, 200)); d.text((1, Y(y) - 10), str(y), fill=(0, 120, 0))
    order = np.argsort(T[:, :, 0].mean(1) * (1 if side == 'r' else -1))
    th, tw = tex.shape[:2]
    for t in order:
        n = np.cross(T[t, 1] - T[t, 0], T[t, 2] - T[t, 0]); ln = np.linalg.norm(n) + 1e-9
        u, v = UV[t].mean(0); c = tex[min(th - 1, int(v * th)), min(tw - 1, int(u * tw))]
        sh = 0.5 + 0.5 * abs(n[0]) / ln; c = tuple(int(x * sh) for x in c)
        d.polygon([(X(p[2]), Y(p[1])) for p in T[t]], fill=c)
    f = MF.get(g)
    if f:
        tp = [f['o'][i] + f['ax'][i] * f['L'] for i in range(3)]; d.ellipse([X(tp[2]) - 4, Y(tp[1]) - 4, X(tp[2]) + 4, Y(tp[1]) + 4], outline=(255, 0, 0), width=2)
    gp = json.load(open(f'{SP}/posing3/guns/{g}.json'))['grip']; d.ellipse([X(gp[2]) - 4, Y(gp[1]) - 4, X(gp[2]) + 4, Y(gp[1]) + 4], outline=(255, 0, 255), width=2)
    d.text((W - 150, H), g + (' RIGHT' if side == 'r' else ' LEFT'), fill=(0, 0, 0)); ims.append(im)
Wt = max(i.width for i in ims); sheet = Image.new('RGB', (Wt, sum(i.height + 6 for i in ims)), (0, 0, 0)); y = 0
for i in ims: sheet.paste(i, (0, y)); y += i.height + 6
sheet.save(out); print(sheet.size)
