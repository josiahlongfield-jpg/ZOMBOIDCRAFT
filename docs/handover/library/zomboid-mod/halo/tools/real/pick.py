"""Score candidate shots and keep the best n with different spots. usage: pick.py <map> <n>"""
import sys, json, numpy as np
from PIL import Image
m, n = sys.argv[1], int(sys.argv[2])
C = json.load(open(f'{m}_cands.json'))['cands']
clear = np.array([0.55, 0.66, 0.80]) ** (1 / 2.2) * 255
sc = []
for i, c in enumerate(C):
    a = np.asarray(Image.open(f'cand/{m}_{i}.jpg').convert('RGB'), np.float32)
    lum = a.mean(2) / 255
    blank = (np.abs(a - [140, 168, 204]).sum(2) < 30).mean()
    dark = (lum < 0.06).mean()
    gx = np.abs(np.diff(lum, axis=1)).mean() + np.abs(np.diff(lum, axis=0)).mean()
    h, w = lum.shape; bl = lum[:h // 16 * 16, :w // 16 * 16].reshape(h // 16, 16, w // 16, 16).std(axis=(1, 3))
    flat = (bl < 0.012).mean()  # untextured void, sky boxes, level edges
    s = gx * 10 - flat * 2.5 + min(lum.mean(), 0.5) - blank * 2 - dark * 1.5 - (0.5 if lum.mean() > 0.85 else 0)
    sc.append((s, i, c['spot'], c['fov']))
sc.sort(reverse=True)
out, spots = [], set()
for s, i, sp, fov in sc:
    if sp in spots: continue
    out.append(i); spots.add(sp)
    if len(out) >= n: break
print(' '.join(map(str, out)))
s = Image.new('RGB', (1280, 360 * ((len(out) + 1) // 2)))
for k, i in enumerate(out): s.paste(Image.open(f'cand/{m}_{i}.jpg').resize((640, 360)), ((k % 2) * 640, (k // 2) * 360))
s.save(f'{m}_pick.png')
