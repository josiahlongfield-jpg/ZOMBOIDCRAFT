"""Render a glb (single textured mesh) as raw vs toon preview sheet.
usage: python3 -I render.py model.glb out.png "Title" """
import sys, json, struct, io
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

glb, out, title = sys.argv[1], sys.argv[2], sys.argv[3]
d = open(glb, 'rb').read()
jl = struct.unpack('<I', d[12:16])[0]
j = json.loads(d[20:20 + jl])
bin_ = d[20 + jl + 8:]

def acc(i):
    a = j['accessors'][i]; bv = j['bufferViews'][a['bufferView']]
    n = {'VEC3': 3, 'VEC2': 2, 'SCALAR': 1}[a['type']]
    dt = {5126: np.float32, 5123: np.uint16, 5125: np.uint32}[a['componentType']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    stride = bv.get('byteStride', 0)
    if stride and stride != n * np.dtype(dt).itemsize:
        raw = np.frombuffer(bin_, np.uint8, a['count'] * stride, off).reshape(-1, stride)
        return raw[:, :n * np.dtype(dt).itemsize].copy().view(dt).reshape(a['count'], n)
    return np.frombuffer(bin_, dt, a['count'] * n, off).reshape(a['count'], n)

p = j['meshes'][0]['primitives'][0]
P = acc(p['attributes']['POSITION']).astype(np.float64)
N = acc(p['attributes']['NORMAL']).astype(np.float64)
UV = acc(p['attributes']['TEXCOORD_0']).astype(np.float64)
I = acc(p['indices']).reshape(-1, 3).astype(np.int64)
img = j['images'][0]; bv = j['bufferViews'][img['bufferView']]
tex = np.asarray(Image.open(io.BytesIO(bin_[bv.get('byteOffset', 0):bv.get('byteOffset', 0) + bv['byteLength']])).convert('RGB'), np.float64) / 255
TH, TW = tex.shape[:2]
print('tris', len(I), 'tex', TW, TH)

# put feet on y=0, scale to 1.85 "blocks" tall like our walkers
P[:, 1] -= P[:, 1].min()
H = P[:, 1].max()

W_, H_ = 420, 760
def raster(yaw):
    c, s = np.cos(yaw), np.sin(yaw)
    R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    V = P @ R.T; Nn = N @ R.T
    sc = (H_ * 0.86) / H
    sx = W_ / 2 + V[:, 0] * sc
    sy = H_ * 0.93 - V[:, 1] * sc
    z = V[:, 2]
    zb = np.full((H_, W_), -1e9); uvb = np.zeros((H_, W_, 2)); nb = np.zeros((H_, W_, 3)); mask = np.zeros((H_, W_), bool)
    for t in I:
        x0, x1, x2 = sx[t]; y0, y1, y2 = sy[t]
        xa, xb = int(max(0, np.floor(min(x0, x1, x2)))), int(min(W_ - 1, np.ceil(max(x0, x1, x2))))
        ya, yb = int(max(0, np.floor(min(y0, y1, y2)))), int(min(H_ - 1, np.ceil(max(y0, y1, y2))))
        if xb < xa or yb < ya: continue
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-12: continue
        X, Y = np.meshgrid(np.arange(xa, xb + 1) + .5, np.arange(ya, yb + 1) + .5)
        w0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / den
        w1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / den
        w2 = 1 - w0 - w1
        ins = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not ins.any(): continue
        zz = w0 * z[t[0]] + w1 * z[t[1]] + w2 * z[t[2]]
        sub = zb[ya:yb + 1, xa:xb + 1]
        upd = ins & (zz > sub)
        if not upd.any(): continue
        sub[upd] = zz[upd]
        mask[ya:yb + 1, xa:xb + 1][upd] = True
        for k in range(2):
            uvb[ya:yb + 1, xa:xb + 1, k][upd] = (w0 * UV[t[0], k] + w1 * UV[t[1], k] + w2 * UV[t[2], k])[upd]
        for k in range(3):
            nb[ya:yb + 1, xa:xb + 1, k][upd] = (w0 * Nn[t[0], k] + w1 * Nn[t[1], k] + w2 * Nn[t[2], k])[upd]
    nb /= np.linalg.norm(nb, axis=2, keepdims=True) + 1e-9
    return zb, uvb, nb, mask

def sample(uvb):
    u = np.clip(uvb[..., 0], 0, 1) * (TW - 1); v = np.clip(uvb[..., 1], 0, 1) * (TH - 1)  # glTF v down
    return tex[v.astype(int), u.astype(int)]

L = np.array([-0.45, 0.75, 0.5]); L /= np.linalg.norm(L)
BG_TOP, BG_BOT = np.array([0.13, 0.14, 0.16]), np.array([0.06, 0.06, 0.07])

def background():
    t = np.linspace(0, 1, H_)[:, None, None]
    return BG_TOP * (1 - t) + BG_BOT * t + np.zeros((H_, W_, 3))

def shade_raw(zb, uvb, nb, mask):
    col = sample(uvb)
    ndl = np.clip((nb * L).sum(2), 0, 1)
    rim = np.clip(1 - nb[..., 2], 0, 1) ** 3 * 0.15
    lit = col * (0.35 + 0.75 * ndl[..., None]) + rim[..., None]
    o = background(); o[mask] = np.clip(lit[mask], 0, 1); return o

def shade_toon(zb, uvb, nb, mask):
    col = sample(uvb)
    # flatten texture noise into paint: blur in UV-space via posterise + median-ish smoothing in screen space
    ci = Image.fromarray((col * 255).astype(np.uint8)).filter(ImageFilter.ModeFilter(5))
    col = np.asarray(ci, np.float64) / 255
    col = np.round(col * 10) / 10
    # project grade: desaturate ~45%, darken toward sooty brown, lift near-black to charcoal
    g = col.mean(2, keepdims=True)
    col = col * 0.55 + g * 0.45
    col = col * 0.82 + np.array([0.12, 0.105, 0.08]) * 0.18
    lum = col.mean(2, keepdims=True)
    col = np.where(lum < 0.3, col + (0.3 - lum) * 0.55 * 0.6, col)
    ndl = (nb * L).sum(2)
    band = np.where(ndl > 0.45, 1.06, np.where(ndl > 0.0, 0.86, 0.68))
    up = np.where(nb[..., 1] > 0.6, 1.06, np.where(nb[..., 1] < -0.5, 0.85, 1.0))
    lit = np.clip(col * (band * up)[..., None], 0, 1)
    # ink: silhouette + depth jumps + crease (normal) jumps
    ink = np.zeros(mask.shape, bool)
    for dy, dx in ((1, 0), (0, 1), (-1, 0), (0, -1)):
        m2 = np.roll(np.roll(mask, dy, 0), dx, 1)
        z2 = np.roll(np.roll(zb, dy, 0), dx, 1)
        n2 = np.roll(np.roll(nb, dy, 0), dx, 1)
        ink |= mask & (~m2)
        ink |= mask & m2 & (np.abs(zb - z2) > 0.012)
        ink |= mask & m2 & ((nb * n2).sum(2) < 0.35)
    inkc = np.array([0.05, 0.045, 0.04])
    o = background(); o[mask] = lit[mask]; o[ink] = inkc
    return o

views = [(0, 'front'), (np.radians(35), 'three-quarter'), (np.radians(90), 'side'), (np.pi, 'back')]
rows = {'raw': [], 'toon': []}
for yaw, name in views:
    buf = raster(yaw)
    rows['raw'].append(shade_raw(*buf)); rows['toon'].append(shade_toon(*buf))
    print('view', name)

pad, head, lab = 16, 70, 34
SW = pad + len(views) * (W_ + pad); SH = head + 2 * (lab + H_ + pad)
sheet = Image.new('RGB', (SW, SH), (22, 23, 26))
dr = ImageDraw.Draw(sheet)
try:
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 30)
    f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 20)
except OSError:
    F = f = ImageFont.load_default()
dr.text((pad, 18), title, font=F, fill=(230, 226, 214))
for r, (key, label) in enumerate([('raw', 'A. Straight in, no editing'), ('toon', 'B. Toon-painted (same pass as the guns: flat paint, 3 light bands, ink outlines, moody grade)')]):
    y = head + r * (lab + H_ + pad)
    dr.text((pad, y + 4), label, font=f, fill=(200, 196, 186))
    for c, im in enumerate(rows[key]):
        sheet.paste(Image.fromarray((np.clip(im, 0, 1) * 255).astype(np.uint8)), (pad + c * (W_ + pad), y + lab))
sheet.save(out)
print('saved', out)
