"""Preview: Arsenal's own ammo box models (and Brita's RPG rocket) in our toon look: flattened, posterised paint,
lit tops, dark undersides and the thin dark ink line round the outside, rendered three views each."""
import os, subprocess, sys, math, numpy as np
from PIL import Image, ImageFilter, ImageDraw, ImageFont
S = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/ammo/'
AR = "/mnt/user-data/uploads/108600/2297098490/mods/ARSENAL(26)GunFighter[MOD 2.0]/media/"
BR = "/mnt/user-data/uploads/108600/2200148440/mods/Brita's Weapon Pack/media/"

def load(src):
    tmp = S + 'tmp.obj'
    subprocess.run(['assimp', 'export', src, tmp, '-ptv', '-tri', '-jiv'], capture_output=True)
    V, T, F = [], [], []
    for line in open(tmp):
        if line.startswith('v '): V.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('vt '): T.append([float(x) for x in line.split()[1:3]])
        elif line.startswith('f '):
            idx = [p.split('/') for p in line.split()[1:]]
            if len(idx) == 3: F.append([(int(a[0]) - 1, int(a[1]) - 1 if len(a) > 1 and a[1] else 0) for a in idx])
    V = np.array(V, float); V[:, 2] *= -1          # PZ reads .x with z flipped
    T = np.array(T or [[0, 0]], float)
    tri = np.array([[V[v] for v, _ in f][::-1] for f in F])   # and the winding swapped
    uv = np.array([[T[t] for _, t in f][::-1] for f in F])
    return tri, uv

def toon(img):
    c = np.asarray(img.convert('RGB')).astype(float) / 255
    lum = c.mean(-1, keepdims=True)
    c = np.maximum(c, 0.16) * 0.6 + lum * 0.4
    c = np.round(c * 24) / 24
    return c * 255

def R(ax, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return {'x': np.array([[1,0,0],[0,c,-s],[0,s,c]]), 'y': np.array([[c,0,s],[0,1,0],[-s,0,c]])}[ax]

def render(tri, uv, tex, view, size=300):
    th, tw = tex.shape[:2]
    V = R('x', view[0]) @ R('y', view[1])
    p = tri.reshape(-1, 3); c = (p.min(0) + p.max(0)) / 2
    q = ((V @ (p - c).T).T).reshape(-1, 3, 3)
    ext = np.abs(q[..., :2]).max() or 1
    sc = size * 0.42 / ext
    img = np.full((size, size, 3), 34.0); zb = np.full((size, size), -1e9); mask = np.zeros((size, size), bool)
    L = np.array([0.35, 0.85, 0.4]); L /= np.linalg.norm(L)
    for k in range(len(q)):
        X = q[k, :, 0] * sc + size / 2; Y = size / 2 - q[k, :, 1] * sc; Z = q[k, :, 2]
        n = np.cross(q[k, 1] - q[k, 0], q[k, 2] - q[k, 0]); nl = np.linalg.norm(n)
        if nl < 1e-12: continue
        n /= nl
        if n[2] < 0: continue                      # back face
        li = 0.55 + 0.45 * max(0, n @ L)
        li = np.round(li * 4) / 4                  # stepped toon light
        x0, x1 = int(max(0, X.min())), int(min(size - 1, X.max()) + 1)
        y0, y1 = int(max(0, Y.min())), int(min(size - 1, Y.max()) + 1)
        if x1 <= x0 or y1 <= y0: continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
        d = (Y[1]-Y[2])*(X[0]-X[2]) + (X[2]-X[1])*(Y[0]-Y[2])
        if abs(d) < 1e-9: continue
        w0 = ((Y[1]-Y[2])*(gx-X[2]) + (X[2]-X[1])*(gy-Y[2])) / d
        w1 = ((Y[2]-Y[0])*(gx-X[2]) + (X[0]-X[2])*(gy-Y[2])) / d
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
        z = w0*Z[0] + w1*Z[1] + w2*Z[2]
        sub = zb[y0:y1, x0:x1]; m &= z > sub
        T = uv[k]
        tu = np.clip(((w0*T[0,0] + w1*T[1,0] + w2*T[2,0]) % 1 * tw).astype(int), 0, tw-1)
        tv = np.clip(((1 - (w0*T[0,1] + w1*T[1,1] + w2*T[2,1]) % 1) * th).astype(int), 0, th-1)
        col = tex[tv, tu]
        sub[m] = z[m]; img[y0:y1, x0:x1][m] = col[m] * li; mask[y0:y1, x0:x1][m] = True
    # the ink line round the outside
    mi = Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))
    ring = (np.asarray(mi) > 0) & ~mask
    img[ring] = 13
    return Image.fromarray(img.clip(0, 255).astype(np.uint8))

ITEMS = [
    ('9mm (we have a box)', AR + 'models_X/WorldItems/Bullets9mm.x', 'A'),
    ('.45 ACP  (new)', AR + 'models_X/WorldItems/Bullets45.x', 'A'),
    ('5.7x28mm  (new)', AR + 'models_X/WorldItems/Bullets57.x', 'B'),
    ('.45 Colt  (new)', AR + 'models_X/WorldItems/Bullets45LC.x', 'A'),
    ('.500 S&W  (new)', AR + 'models_X/WorldItems/Bullets50MAG.x', 'A'),
    ('5.56 (we have a box)', AR + 'models_X/WorldItems/556Bullets.x', 'A'),
    ('.308 (we have a box)', AR + 'models_X/WorldItems/308Bullets.x', 'A'),
    ('.22 LR (we have a box)', AR + 'models_X/WorldItems/Bullets22.x', 'A'),
    ('.50 BMG (we have a box)', AR + 'models_X/WorldItems/50BMGBullets.x', 'A'),
    ('12 gauge (we have a box)', AR + 'models_X/WorldItems/12gShotgunShells.x', 'A'),
    ('PG-7V rocket  (new)', AR + 'models_X/weapons/parts/Standard_Mag_Rocket.x', 'RPG'),
]
TEX = {'A': S + 'src/Bullets_A.png', 'B': S + 'src/Bullets_B.png', 'RPG': BR + 'textures/weapons/firearm/RPG_7.png'}
views = [(25, -35), (25, 55), (-15, 160)]
size = 300
sheet = Image.new('RGB', (size * 3 + 260, size * len(ITEMS)), (24, 24, 24))
dr = ImageDraw.Draw(sheet)
try:
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 22)
except OSError:
    font = ImageFont.load_default()
cache = {}
for i, (label, src, t) in enumerate(ITEMS):
    tri, uv = load(src)
    if t == 'RPG':  # lay the rocket along x like the rounds
        tri = tri[..., [1, 0, 2]] * [1, -1, 1]
        uv = uv[:, ::-1]
    if t not in cache:
        im = Image.open(TEX[t]).convert('RGB'); im.thumbnail((1024, 1024)); cache[t] = toon(im)
    print(label, len(tri), 'tris', np.ptp(tri.reshape(-1, 3), 0).round(3))
    for j, v in enumerate(views):
        sheet.paste(render(tri, uv, cache[t], v, size), (260 + j * size, i * size))
    dr.text((14, i * size + size // 2 - 12), label, fill=(225, 225, 225), font=font)
sheet.save(S + 'arsenal_sheet.png')
