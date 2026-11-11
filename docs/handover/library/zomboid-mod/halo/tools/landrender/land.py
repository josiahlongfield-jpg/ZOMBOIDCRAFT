"""Builds the Reach world's land between the missions (approved climate map, 2026-11-09).

Mission cores keep their own exact heights (Halo backdrop + level collision, moved only up/down so all seas meet at
one level). Outside, new land interpolates between the missions' edge heights with climate-shaped hills, the shared
sea and coast, edge mountains, the Sword Base land strip and the Lone Wolf rise.
Outputs (in out/): height.npy (4 m grid, metres), mask.npy (0 new land, 1 mission land, 2 level footprint),
clim.npy (16 m grid climate class), plus preview meshes.
"""
import json, numpy as np
from PIL import Image, ImageDraw, ImageFilter
import os
os.makedirs('out', exist_ok=True)
SEA = -14.0
N4, N16 = 4000, 1000           # 4 m and 16 m grids over -8000..8000
OFF = {"m10": (-5257, -4204), "m20": (-167, -5261), "m60": (4936, -5992), "m35": (-4299, 3102),
       "m52": (3983, 1544), "m50": (6225, -2533), "m70": (655, 5986)}
SH = {"m10": 100, "m20": 0, "m60": -22, "m35": 0, "m52": 131, "m50": 114, "m70": 225}
cs = json.load(open('cs.json'))
rng = np.random.default_rng(7)

def w2i(x, n): return (np.asarray(x) + 8000) / 16000 * n

def up(a, n, res=Image.BICUBIC):
    return np.array(Image.fromarray(a.astype(np.float32), 'F').resize((n, n), res))

def blur(a, r, passes=3):
    a = a.astype(np.float64)
    for _ in range(passes):
        for ax in (0, 1):
            p = np.pad(a, [(r + 1, r) if i == ax else (0, 0) for i in (0, 1)], mode='edge')
            c = np.cumsum(p, axis=ax)
            a = (np.take(c, range(2 * r + 1, c.shape[ax]), axis=ax) - np.take(c, range(0, c.shape[ax] - 2 * r - 1), axis=ax)) / (2 * r + 1)
    return a.astype(np.float32)

def dist_steps(mask, step_px, steps):
    """Approximate distance (in steps) from mask: 0 inside, k = within k dilations."""
    im = Image.fromarray((mask * 255).astype(np.uint8))
    D = np.full(mask.shape, steps + 1, np.float32); D[mask] = 0
    for k in range(1, steps + 1):
        im = im.filter(ImageFilter.MaxFilter(2 * step_px + 1))
        a = np.array(im) > 0
        D[a & (D > k)] = k
    return D

# ---- mission windows, footprints, ownership (nearest footprint wins), heights and water
names = list(OFF)
def window(m):
    c = cs[m]; ox, oz = OFF[m]
    x0 = int(round(w2i(c[0] + ox - 4000, N4))); z0 = int(round(w2i(c[2] + oz - 4000, N4)))
    xs, zs = slice(max(0, x0), min(N4, x0 + 2000)), slice(max(0, z0), min(N4, z0 + 2000))
    return xs, zs, (zs.start - z0, zs.stop - z0), (xs.start - x0, xs.stop - x0)
foot = np.zeros((N4, N4), bool); Dk = []
for m in names:
    xs, zs, (a0, a1), (b0, b1) = window(m)
    f = np.zeros((N4, N4), bool)
    f[zs, xs] = (np.array(Image.open(f'foot_{m}.png').convert('L')) > 128)[a0:a1, b0:b1]
    foot |= f
    f16 = np.array(Image.fromarray(f.astype(np.uint8) * 255).resize((N16, N16), Image.BOX)) > 0
    Dk.append(blur(dist_steps(f16, 4, 30) * 64.0, 3))
Dk = np.stack(Dk); own16 = Dk.argmin(0); D = Dk.min(0)
own4 = np.array(Image.fromarray(own16.astype(np.uint8)).resize((N4, N4), Image.NEAREST))
H_m = np.full((N4, N4), np.nan, np.float32); W_m = np.zeros((N4, N4), bool)
for k, m in enumerate(names):
    xs, zs, (a0, a1), (b0, b1) = window(m)
    a = np.array(Image.open(f'hgt_{m}.png').convert('RGB')).astype(np.int64)
    v = a[..., 0] + a[..., 1] * 256 + a[..., 2] * 65536
    h = (v / 100 - 2000).astype(np.float32); h[v == 0] = np.nan; h += SH[m]
    wt = np.array(Image.open(f'wat_{m}.png').convert('L')) > 128
    sel = own4[zs, xs] == k
    H_m[zs, xs] = np.where(sel, h[a0:a1, b0:b1], H_m[zs, xs])
    W_m[zs, xs] = np.where(sel, wt[a0:a1, b0:b1], W_m[zs, xs])
np.save('out/foot.npy', foot)
# holes with no ground and no water (e.g. New Alexandria's streets, which only the real models have): fill from around
hole = np.isnan(H_m) & ~W_m
fill = np.where(np.isnan(H_m) | W_m, 0, H_m); wgt0 = (~np.isnan(H_m) & ~W_m).astype(np.float32)
h16 = np.array(Image.fromarray(fill.astype(np.float32), 'F').resize((N16, N16), Image.BOX)); w16_ = np.array(Image.fromarray(wgt0, 'F').resize((N16, N16), Image.BOX))
for r in (2, 6, 16, 40):
    num = blur(h16 * w16_, r); den = blur(w16_, r)
    est = np.where(den > 1e-3, num / np.maximum(den, 1e-3), np.nan)
    if r == 2: E = est
    else: E = np.where(np.isnan(E), est, E)
E4 = up(np.nan_to_num(E, nan=SEA + 5), N4, Image.BILINEAR)
H_m = np.where(hole, E4, H_m)
np.save('out/hole.npy', hole)
CORE, BLEND = 420.0, 1100.0
w16 = np.clip(1 - (D - CORE) / (BLEND - CORE), 0, 1); w16 = w16 * w16 * (3 - 2 * w16)

# sea polygon (approved map) on the 16 m grid
def poly(pts, n):
    im = Image.new('L', (n, n), 0)
    ImageDraw.Draw(im).polygon([(w2i(x, n), w2i(z, n)) for x, z in pts], fill=255)
    return np.array(im) > 0
SEAPTS = [(-2700, -8000), (8000, -8000), (8000, 8000), (7300, 8000), (6900, 6200), (6200, 4400), (5700, 600), (5300, -1500),
          (4300, -3300), (2600, -3700), (1300, -3300), (-600, -3500), (-1900, -3900), (-2500, -5000)]
sea16 = poly(SEAPTS, N16)
cn = (up(rng.random((24, 24)), N16) - 0.5) * 0.9 + (up(rng.random((80, 80)), N16) - 0.5) * 0.35
sea16 = (blur(sea16.astype(np.float32), 18) + cn * 0.55) > 0.5
# Sword Base land strip: keep land in the corridor
STRIP = [(-620, -5000), (40, -5000), (120, -3200), (-700, -3200)]
strip16 = poly(STRIP, N16)
sea16 &= ~strip16
# mission cores keep their own water/land
core16 = D <= CORE
seaD = dist_steps(~sea16, 2, 30) * 32.0         # metres into the sea from the coast
landD = blur(dist_steps(sea16, 2, 30) * 32.0, 3)         # metres inland from the coast
seaD = blur(seaD, 3)

# ---- anchor heights from each mission's ring
pts = []
ys, xs_ = np.nonzero((D >= CORE - 64) & (D <= CORE + 64))
Hm16 = np.array(Image.fromarray(np.nan_to_num(H_m, nan=-9999).astype(np.float32), 'F').resize((N16, N16), Image.NEAREST))
for y, x in zip(ys, xs_):
    h = Hm16[y, x]
    if h > SEA + 1 and h < 1400: pts.append((x, y, h))
pts = np.array(pts); pts = pts[rng.choice(len(pts), min(4000, len(pts)), replace=False)]
G = 125
gy, gx = np.mgrid[0:G, 0:G] * (N16 / G) + N16 / G / 2
d2 = (gx[..., None] - pts[:, 0]) ** 2 + (gy[..., None] - pts[:, 1]) ** 2 + 4.0
wgt = 1 / d2 ** 1.5
A = (wgt * pts[:, 2]).sum(-1) / wgt.sum(-1)
A16 = up(A, N16)

# ---- climate (same field as the approved map)
yy, xx = np.mgrid[0:N16, 0:N16]; xn = xx / N16 * 2 - 1; zn = yy / N16 * 2 - 1
nz = up(rng.random((64, 64)), N16) - 0.5
t = zn - 0.3 * xn + 0.07 * nz + 0.05 * (up(rng.random((200, 200)), N16) - 0.5)
def fbm(sizes_amps):
    out = np.zeros((N16, N16), np.float32)
    for cells, amp in sizes_amps:
        out += (up(rng.random((cells, cells)) * 2 - 1, N16)) * amp
    return out
hills = fbm([(8, 90), (20, 45), (40, 30), (90, 12), (200, 3)])
ridges = np.abs(fbm([(16, 1.0), (48, 0.5)]))
rough = np.clip(0.8 + 0.9 * np.clip(-t - 0.2, 0, 1) + 0.4 * np.clip(t - 0.45, 0, 1), 0.5, 1.6)   # north/NW rougher, middle gentler
P = A16 + hills * rough
P += (1 - ridges) ** 3 * 220 * np.clip(-t - 0.45, 0, 1)                                         # cold north: mountain ridges
bad = np.clip((t - 0.45) / 0.25, 0, 1)
P = P * (1 - 0.25 * bad) + (np.round(P / 8) * 8) * 0.25 * bad                                   # south: terraced badlands
# map edges (land side): mountain wall so the world ends naturally
ed = np.minimum(np.minimum(xx, N16 - 1 - xx), np.minimum(yy, N16 - 1 - yy)) * 16.0
P += np.clip(1 - ed / 900, 0, 1) ** 2 * 380 * (~sea16)
# coast: beaches/cliffs down to the sea; sea floor deepens offshore
cliffy = np.clip((up(rng.random((14, 14)), N16) - 0.55) * 4, 0, 1)
L_c = 1100 * (1 - cliffy) + 140 * cliffy
cz = np.clip(landD / L_c, 0, 1); cz = cz * cz * (3 - 2 * cz)
P = np.where(sea16, SEA - 4 - 45 * np.clip(seaD / 900, 0, 1), SEA + 2.5 + (np.maximum(P, SEA + 2.5) - SEA - 2.5) * cz)
# Sword Base strip: a ridge road from the plateau (~55) down to the mainland
sx = (xx / N16 * 16000 - 8000); sz = (yy / N16 * 16000 - 8000)
u = np.clip((sz - (-5000)) / (-3200 - (-5000)), 0, 1)
cxl = -300 + 160 * np.sin(u * 3.1) + 60 * np.sin(u * 9 + 1)
half = np.abs(sx - cxl) + 40 * nz
top = 55 + (P[int(w2i(-3100, N16)), int(w2i(-300, N16))] - 55) * u
sw = np.clip(1 - (half - 240) / 420, 0, 1); sw = sw * sw * (3 - 2 * sw) * ((sz > -5050) & (sz < -2900))
P = np.where(sw > 0, P * (1 - sw) + np.maximum(top, P) * sw, P)
# Lone Wolf rise
LW = (2300, 6900)
r = np.hypot(sx - LW[0], sz - LW[1])
base = P[int(w2i(LW[1], N16)), int(w2i(LW[0], N16))]
lw = np.clip(1 - (r - 140) / 420, 0, 1); lw = lw * lw * (3 - 2 * lw)
P = P * (1 - lw) + (base + 75) * lw
np.save('out/lw_top.npy', np.array([base + 75]))

# ---- final 4 m heights: mission land inside cores, new land outside
P4 = up(P, N4); w4 = up(w16, N4, Image.BILINEAR)
sea4 = up(sea16.astype(np.float32), N4, Image.BILINEAR) > 0.5
Hm_f = np.where(np.isnan(H_m) | W_m, np.minimum(np.nan_to_num(H_m, nan=SEA - 15), SEA - 6), H_m)
Hm_f = np.where(w4 < 0.999, np.clip(Hm_f, P4 - 200, P4 + 200), Hm_f)
np.save('out/hnan.npy', W_m | (np.isnan(H_m)))
H = w4 * Hm_f + (1 - w4) * P4
strip4 = up(sw.astype(np.float32), N4, Image.BILINEAR)
H = np.where((strip4 > 0) & ~foot, np.maximum(H, H * (1 - strip4) + up(np.maximum(top, P).astype(np.float32), N4) * strip4), H)
mask = np.where(foot, 2, np.where(w4 >= 0.999, 1, 0)).astype(np.uint8)
clim = np.select([sea16, t < -0.85, t < -0.62, t < 0.35, t < 0.6], [0, 1, 2, 3, 4], 5).astype(np.uint8)  # 0 sea 1 snow 2 tundra 3 green 4 dry 5 desert
np.save('out/height.npy', H.astype(np.float32)); np.save('out/mask.npy', mask); np.save('out/clim.npy', clim)
np.save('out/sea16.npy', sea16); np.save('out/P16.npy', P.astype(np.float32)); np.save('out/w16.npy', w16)
print('height range', np.percentile(H, [0, 1, 50, 99, 100]).round(), 'mask', np.bincount(mask.ravel()) / mask.size)
