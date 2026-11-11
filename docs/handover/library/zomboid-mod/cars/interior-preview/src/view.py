"""Before/after views of a cabin with the interior kit: the car as the game draws it, kit pieces with a thin outline round each whole object."""
import sys, glob, os, numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, '../lightprev'); sys.path.insert(0, '../toon')
import render, toon
from render import camera, raster, normals
import anchors as A, kit as K

W, H = 760, 480
render.W, render.H = W, H
SUN = np.array([0.35, 0.85, 0.4]); SUN /= np.linalg.norm(SUN)
# per local axis and sign: (right axis, sign), (up axis, sign) as seen from outside that face
FACE = {(0, 1): ((2, -1), (1, 1)), (0, -1): ((2, 1), (1, 1)), (1, 1): ((0, -1), (2, 1)),
        (1, -1): ((0, 1), (2, 1)), (2, 1): ((0, 1), (1, 1)), (2, -1): ((0, -1), (1, 1))}
FIDX = {(0, 1): 0, (0, -1): 1, (1, 1): 2, (1, -1): 3, (2, 1): 4, (2, -1): 5}

def kit_tris(kit):
    T, owner, face = [], [], []
    for bi, b in enumerate(kit.boxes):
        for ax in range(3):
            for sg in (1, -1):
                (ra, rs), (ua, us) = FACE[(ax, sg)]
                pts = []
                for cr, cu in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                    q = np.zeros(3); q[ax] = sg * b['h'][ax]; q[ra] = rs * cr * b['h'][ra]; q[ua] = us * cu * b['h'][ua]
                    pts.append(b['c'] + b['R'] @ q)
                T += [[pts[0], pts[1], pts[2]], [pts[0], pts[2], pts[3]]]
                owner += [bi, bi]; face += [(ax, sg)] * 2
    return np.array(T), np.array(owner), face

def shot(car, a, kit, cam, cut=None):
    T = car['T']; solid = ~car['glass']
    keep = solid.copy()
    if kit is not None and getattr(kit, 'clear', None) is not None:
        keep &= ~kit.clear(T.mean(1), car['inside'])
    if cut is not None:
        keep &= cut(T.mean(1))
    P = T[keep]; base = car['base'][keep]
    gP, gC = render.ground(step=1.0) if False else (None, None)
    gP, gC = toon.ground()
    if kit is not None:
        boxes = [b for b in kit.boxes if cut is None or cut(b['c'][None])[0]]
        kit2 = K.Kit(); kit2.boxes = boxes
        kT, kown, kface = kit_tris(kit2) if boxes else (np.zeros((0, 3, 3)), np.zeros(0, int), [])
    else:
        boxes, kT, kown, kface = [], np.zeros((0, 3, 3)), np.zeros(0, int), []
    nb, nk = len(P), len(kT)
    allP = np.concatenate([P, kT, gP]); N = normals(allP)
    cols = np.concatenate([base, np.array([boxes[o]['col'] for o in kown]).reshape(-1, 3), gC])
    _, _, zb, ids = raster(allP, cols, np.zeros_like(cols), cam)
    eye, r, u, f, foc = cam
    yy, xx = np.mgrid[0:H, 0:W] + 0.5
    m = ids >= 0; t = np.where(m, ids, 0)
    z = np.where(m, zb, 1)
    Pp = eye + ((xx - W / 2) / foc * z)[..., None] * r + (-(yy - H / 2) / foc * z)[..., None] * u + z[..., None] * f
    n = N[t]; c = cols[t].copy()
    shade = 0.45 + 0.55 * np.clip(n @ SUN, 0, 1)
    isk = m & (t >= nb) & (t < nb + nk)
    obj = np.full((H, W), -1)
    if nk:
        ki = np.where(isk, t - nb, 0)
        bown = kown[ki]
        obj = np.where(isk, np.array([b['obj'] for b in boxes])[bown], -1)
        # painted faces
        for bi, b in enumerate(boxes):
            if not b['tex']: continue
            pm = isk & (bown == bi)
            if not pm.any(): continue
            q = (Pp[pm] - b['c']) @ b['R']
            fids = np.array([FIDX[kface[i]] for i in ki[pm]])
            for fi, tex in b['tex'].items():
                if tex is None: continue
                sel = fids == fi
                if not sel.any(): continue
                (ax, sg) = [k for k, v in FIDX.items() if v == fi][0]
                (ra, rs), (ua, us) = FACE[(ax, sg)]
                sx = (q[sel, ra] * rs + b['h'][ra]) / (2 * b['h'][ra]); sy = (q[sel, ua] * us + b['h'][ua]) / (2 * b['h'][ua])
                th, tw = tex.shape[:2]
                px = np.clip((sx * tw).astype(int), 0, tw - 1); py = np.clip(((1 - sy) * th).astype(int), 0, th - 1)
                cc = c[pm]; cc[sel] = tex[py, px]; c[pm] = cc
        mats = np.array([b['mat'] for b in boxes])[bown]
        top = np.where(n[..., 1] > 0.7, 1.08, np.where(n[..., 1] < -0.5, 0.72, 1.0))
        flat = isk & np.isin(mats, ['lit', 'glass'])
        shade = np.where(isk, shade * top, shade)
        shade = np.where(flat, np.maximum(shade, 0.92), shade)
    img = c * shade[..., None] * 1.2
    # thin outline round each kit object: where it meets anything else, or folds over itself
    e = np.zeros((H, W), bool)
    zz = np.where(m, zb, 1e3)
    for dy, dx in ((0, 1), (1, 0)):
        sa = (slice(0, H - dy), slice(0, W - dx)); sb = (slice(dy, H), slice(dx, W))
        ka, kb = obj[sa], obj[sb]
        diff = (ka != kb) & ((ka >= 0) | (kb >= 0))
        same = (ka == kb) & (ka >= 0) & (np.abs(zz[sa] - zz[sb]) > 0.06 * np.minimum(zz[sa], zz[sb]))
        hit = diff | same
        nearer_a = zz[sa] <= zz[sb]
        a_k = ka >= 0; b_k = kb >= 0
        # draw on the kit side, the nearer one if both are kit
        put_a = hit & a_k & (nearer_a | ~b_k)
        put_b = hit & b_k & (~nearer_a | ~a_k)
        e[sa] |= put_a; e[sb] |= put_b
    img[e] = np.array([.05, .045, .04])
    sky = np.array([.62, .68, .72])
    out = np.where(m[..., None], img, sky)
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))

def label(im, text):
    d = ImageDraw.Draw(im); f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
    d.rectangle((0, 0, W, 24), fill=(11, 12, 14)); d.text((8, 4), text, fill=(232, 228, 218), font=f)
    return im

def views(car, a, kit):
    e = a['eye']; ex = e[0]
    pe = e * [-1, 1, 1]
    out = []
    out.append(camera(e + [0, 0.02, -0.05], e + [-0.25, -0.45, 1.0], 95))
    bz = kit.cam_z if kit.cam_z is not None else e[2] - min(0.85, a['back_room'] - 0.12)
    out.append(camera(np.array([0.0, e[1] + 0.02, bz]), np.array([0, a['dash_top'] - 0.12, e[2] + 0.6]), 80))
    out.append(camera(e + [0, 0, -0.05], e + [-1.0, -0.6, 0.45], 95))
    return out

if __name__ == '__main__':
    I = sys.argv[1]; stem = sys.argv[2]; style = sys.argv[3]; extras = sys.argv[4].split(',') if len(sys.argv) > 4 else []
    files = {os.path.basename(f)[:-4]: f for f in glob.glob(I + '/**/*.glb', recursive=True)}
    car = A.load(files[stem]); a = A.anchors(car)
    kit = K.build(a, style, extras)
    e = a['eye']
    rows = []
    for cam in views(car, a, kit):
        rows.append((shot(car, a, None, cam), shot(car, a, kit, cam)))
    cut = lambda C: C[:, 1] < e[1] + 0.12
    topcam = camera(e * [0.3, 1, 1] + [0.0, 2.0, -0.35], e * [0.3, 1, 1] + [0, -0.5, 0.35], 62)
    rows.append((shot(car, a, None, topcam, cut), shot(car, a, kit, topcam, cut)))
    names = ['from the driver\'s seat', 'from the back seat', 'looking across the cabin', 'from above, roof off']
    title = sys.argv[5] if len(sys.argv) > 5 else stem
    sheet = Image.new('RGB', (W * 2, H * len(rows)))
    for i, (x, y) in enumerate(rows):
        sheet.paste(label(x, f'{title.upper()}  -  TODAY  -  {names[i]}'), (0, H * i))
        sheet.paste(label(y, f'{title.upper()}  -  DETAIL KIT  -  {names[i]}'), (W, H * i))
    sheet.save(f'{stem}.png'); print('ok', len(kit.boxes), 'boxes')
