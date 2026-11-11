"""land use from the roads: farms (parcels + strip fields + hedgerows), woods, village houses. render(window) at any resolution."""
import json, numpy as np, random
from PIL import Image, ImageDraw, ImageFilter
exec(open('layout.py').read().split("# which mission")[0].replace("H4 = np.load", "H4 = None and np.load").replace("H = H4.reshape", "g_ = np.load('grids.npz'); H = g_['H'] #").replace("mask = M4.reshape", "mask = g_['mask'] #").replace("sea = (np.load", "sea = g_['sea'] #"))
g = np.load('grids.npz'); slope, dmis, dsea = g['slope'], g['dmis'], g['dsea']
R = json.load(open('roads.json')); road = np.load('road.npy')
rng = np.random.default_rng(5)
def boxmean(a, r):
    c = np.cumsum(np.cumsum(np.pad(a, ((r + 1, r), (r + 1, r)), mode='edge'), 0), 1)
    return (c[2 * r + 1:, 2 * r + 1:] - c[:-2 * r - 1, 2 * r + 1:] - c[2 * r + 1:, :-2 * r - 1] + c[:-2 * r - 1, :-2 * r - 1]) / (2 * r + 1) ** 2
def up(a, n): return np.array(Image.fromarray(a.astype(np.float32), 'F').resize((n, n), Image.BICUBIC))
droad = dist(road, 0) * CELL
flat = boxmean(slope, 3)
edge = np.minimum(np.minimum(xx, N - 1 - xx), np.minimum(yy, N - 1 - yy)) * CELL
# farmland: gentle, temperate or dry grass, near a road, off the missions' land
farm = np.isin(clim, (3, 4)) & (mask == 0) & ~sea & (flat < 0.11) & (droad < 1000) & (H < 520) & (dsea > 60) & (edge > 300)
farm &= ~((clim == 4) & (rng.random((N, N)) < 0) )
farmf = boxmean(farm.astype(np.float64), 3)
# woods field: fBm, denser on slopes and at the edges of mission land (blends into Halo's own forests)
def fbm(oct_):
    o = np.zeros((N, N), np.float32)
    for cells, amp in oct_: o += (up(rng.random((cells, cells)) * 2 - 1, N)) * amp
    return o
wn = fbm([(12, 0.6), (30, 0.45), (80, 0.3), (200, 0.15)])
wood = wn + 0.25 + np.clip(flat - 0.08, 0, 0.4) * 2.6 + np.clip(1 - dmis / 1200, 0, 1) * 0.5 * (mask == 0) - farmf * 1.4 - (droad < 40) * 3
wood_ok = np.isin(clim, (1, 2, 3, 4)) & ~sea & (mask == 0) & (H < 900)
woodv = np.where(wood_ok, wood, -9)
# farm seeds (farmsteads) along the roads
seeds = []
for name, kind, p in R['edges']:
    rr, cc = np.divmod(np.array(p), N)
    for k in range(20, len(p) - 10, 26):
        a, b = k - 3, k + 3
        tx, tz = cc[b] - cc[a], rr[b] - rr[a]; L = np.hypot(tx, tz) or 1; tx, tz = tx / L, tz / L
        for side in (1, -1):
            x, z = c2w(rr[k], cc[k]); x += -tz * side * 110; z += tx * side * 110
            r_, c_ = w2c(x, z)
            if 0 <= r_ < N and 0 <= c_ < N and farm[r_, c_] and all(np.hypot(x - s[0], z - s[1]) > 380 for s in seeds):
                seeds.append((x, z, np.arctan2(tz, tx) + rng.normal(0, 0.08), rng.uniform(70, 160), rng.uniform(150, 380), int(rng.integers(1 << 30))))
# more farms off the roads, on farm tracks
fr, fc_ = np.nonzero(farm)
for k in rng.permutation(len(fr))[:20000]:
    x, z = c2w(fr[k], fc_[k])
    if all(np.hypot(x - s[0], z - s[1]) > 380 for s in seeds[-400:]) and all(np.hypot(x - s[0], z - s[1]) > 380 for s in seeds):
        # face the nearest road
        seeds.append((x, z, rng.uniform(0, np.pi), rng.uniform(70, 170), rng.uniform(150, 420), int(rng.integers(1 << 30))))
seeds = np.array(seeds)
print('farmsteads', len(seeds), 'farm km2', farm.sum() * CELL * CELL / 1e6, flush=True)
VIL = R['villages']
FIELD = np.array([(201, 176, 96), (120, 152, 70), (92, 130, 62), (134, 104, 72), (176, 168, 108), (150, 140, 70), (108, 140, 84)], np.float32)

_cr = np.random.default_rng(9).random((2667, 2667)).astype(np.float32)
def sample_can(X, Z):
    fx = (X + 8000) / 6.0; fz = (Z + 8000) / 6.0
    i = np.clip(fx.astype(int), 0, 2665); j = np.clip(fz.astype(int), 0, 2665); tx = fx - i; tz = fz - j
    return (_cr[j, i] * (1 - tx) + _cr[j, i + 1] * tx) * (1 - tz) + (_cr[j + 1, i] * (1 - tx) + _cr[j + 1, i + 1] * tx) * tz
def sample(a, X, Z, order=1):
    if a is None: return sample_can(X, Z)
    """bilinear sample of a 16 m grid at world points"""
    fx = (X + 8000) / CELL - 0.5; fz = (Z + 8000) / CELL - 0.5
    i = np.clip(np.floor(fx).astype(int), 0, N - 2); j = np.clip(np.floor(fz).astype(int), 0, N - 2)
    tx = np.clip(fx - i, 0, 1); tz = np.clip(fz - j, 0, 1)
    if order == 0: return a[np.clip(np.round(fz).astype(int), 0, N - 1), np.clip(np.round(fx).astype(int), 0, N - 1)]
    a = a.astype(np.float32)
    return (a[j, i] * (1 - tx) + a[j, i + 1] * tx) * (1 - tz) + (a[j + 1, i] * (1 - tx) + a[j + 1, i + 1] * tx) * tz

def render(x0, z0, size, px):
    s = size / px
    X, Z = np.meshgrid(x0 + (np.arange(px) + 0.5) * s, z0 + (np.arange(px) + 0.5) * s)
    Hh = sample(H, X, Z); cl = sample(clim, X, Z, 0); mk = sample(mask, X, Z, 0); se = sample(sea.astype(np.float32), X, Z) > 0.5
    gx, gz = np.gradient(Hh, s); shade = np.clip(0.82 + (-gx * 0.6 - gz * 0.6) * 0.9, 0.45, 1.25)
    pal = np.array([(40, 70, 110), (226, 232, 238), (148, 156, 132), (112, 146, 78), (168, 158, 104), (206, 186, 138)], np.float32)
    img = pal[cl]
    # farms
    fm = sample(farmf, X, Z) > 0.5
    fine = sample(fbm_fine, X, Z) if False else 0
    if len(seeds):
        best = np.full(X.shape, 1e18); sid = np.zeros(X.shape, int)
        for k, (sx, sz, *_r) in enumerate(seeds):
            if abs(sx - (x0 + size / 2)) > size / 2 + 2000 or abs(sz - (z0 + size / 2)) > size / 2 + 2000: continue
            d = (X - sx) ** 2 + (Z - sz) ** 2
            m = d < best; best[m] = d[m]; sid[m] = k
        sd = seeds[sid]; ang = sd[:, :, 2] if sd.ndim == 3 else sd[..., 2]
        u = (X - sd[..., 0]) * np.cos(sd[..., 2]) + (Z - sd[..., 1]) * np.sin(sd[..., 2])
        v = -(X - sd[..., 0]) * np.sin(sd[..., 2]) + (Z - sd[..., 1]) * np.cos(sd[..., 2])
        fu = np.floor(u / sd[..., 4]); fv = np.floor(v / sd[..., 3])
        h = (sd[..., 5].astype(np.int64) * 7919 + fu.astype(np.int64) * 104729 + fv.astype(np.int64) * 1299709) % 7
        h2 = (sd[..., 5].astype(np.int64) // 7) % 7
        h = np.where((fu + fv) % 3 == 0, h, h2)  # a farm keeps mostly one or two crops
        fc = FIELD[h]
        # field furrows: faint stripes along each strip
        fur = (np.sin(v / 3.0 * np.pi) * 0.04 + 1)[..., None]
        img = np.where(fm[..., None], fc * fur, img)
        # hedgerows between farms and along field edges
        edge_u = np.abs(u / sd[..., 4] - np.round(u / sd[..., 4])) * sd[..., 4] < 1.4 * s
        edge_v = np.abs(v / sd[..., 3] - np.round(v / sd[..., 3])) * sd[..., 3] < 0.9 * s
        sid_e = np.zeros(X.shape, bool); sid_e[:, 1:] |= sid[:, 1:] != sid[:, :-1]; sid_e[1:] |= sid[1:] != sid[:-1]
        hedge = fm & (sid_e | (edge_u & (h % 2 == 0)))
        img[hedge] = (62, 92, 48)
    # woods
    wv = sample(woodv, X, Z) + (np.random.default_rng(1).random(X.shape) - 0.5) * 0.12
    wd = (wv > 0.55) & ~(fm & (wv < 0.9))
    conifer = (cl <= 2) | (Hh > 500)
    wc = np.where(conifer[..., None], np.array([46, 72, 52], np.float32), np.array([58, 92, 50], np.float32))
    wc = np.where((cl == 4)[..., None], np.array([104, 112, 64], np.float32), wc)
    cn = sample(CAN, X, Z) if s < 5 else 1.0
    img = np.where(wd[..., None], wc * (0.78 + 0.44 * cn)[..., None] if s < 5 else wc, img)
    img = img * shade[..., None]
    img[mk > 0] = img[mk > 0] * 0.5 + 64
    img[se] = (38, 66, 104)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    return im, (X, Z, fm, wd)

CAN = None
fbm_fine = None
if __name__ == '__main__':
    im, _ = render(-8000, -8000, 16000, 1600)
    im.save('/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/lay_use.png')
    np.save('seeds.npy', seeds); np.save('farmf.npy', farmf); np.save('woodv.npy', woodv)
