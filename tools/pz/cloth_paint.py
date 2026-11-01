"""Zomboid's clothes painted again for the survivor bodies, at four times their size, while the packs are built
(survivor_pack.py). Nothing of Zomboid's is kept here: this reads the game's own 256px textures on the PC that builds
the packs and paints over them there.

Every texel is first placed on the body: the garment's mesh (or, for a garment Zomboid paints straight on the skin, the
body's) is rasterised into its texture, so each texel knows where on her it sits, which way it faces, and how far it is
from its panel's edge. Then, per material:

    the source's colours and Zomboid's painted folds, smoothed of their pixels (camo and prints kept crisp)
    the cloth itself: denim twill, plain weave, knit, ripstop, leather grain, quilted nylon, rubber, brushed or
        scratched metal, moulded plastic
    seams: each panel's edge turned under, with a line of stitching (cloth and leather)
    wear: edges and the front of the knees and elbows rubbed pale, grime and mud low down, a few stains

A few garments are painted by hand on top of that (HAND): pockets, plackets, laces, yokes, put where they are on the
real thing from each texel's place on the body.
"""
import io

import numpy as np
from PIL import Image

SIZE = 1024


# ---------------------------------------------------------------- placing texels on the body
class Place:
    """Per texel of a SIZE x SIZE texture: covered by the mesh, its position (metres, game space: y up, +z ahead,
    +x her left), its normal, and its distance in from its panel's edge (texels, up to 32)."""

    def __init__(self, meshes, n=SIZE):
        self.n = n
        cov = np.zeros((n, n), bool); pos = np.zeros((n, n, 3), np.float32); nrm = np.zeros((n, n, 3), np.float32)
        for m in meshes:
            uv = np.mod(m['uv'], 1.0000001) * n
            for t in m['tris']:
                a, b, c = uv[t]
                x0, x1 = int(max(0, np.floor(min(a[0], b[0], c[0])))), int(min(n - 1, np.ceil(max(a[0], b[0], c[0]))))
                y0, y1 = int(max(0, np.floor(min(a[1], b[1], c[1])))), int(min(n - 1, np.ceil(max(a[1], b[1], c[1]))))
                if x1 < x0 or y1 < y0:
                    continue
                d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(d) < 1e-9:
                    continue
                ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
                l0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
                l1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
                l2 = 1 - l0 - l1
                inside = (l0 >= -0.02) & (l1 >= -0.02) & (l2 >= -0.02)
                if not inside.any():
                    continue
                L = np.stack([l0, l1, l2], -1)[inside]
                yy, xx = (ys[inside] - 0.5).astype(int), (xs[inside] - 0.5).astype(int)
                cov[yy, xx] = True
                pos[yy, xx] = L @ m['pos'][t]
                nrm[yy, xx] = L @ m['normal'][t]
        nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True) + 1e-9
        self.cov, self.pos, self.nrm = cov, pos, nrm
        self.dist = edge_dist(cov)

    def masked(self, mask):
        """The same places, cut down to a mask (a garment painted on the skin covers only part of the body)."""
        p = object.__new__(Place)
        p.n, p.pos, p.nrm = self.n, self.pos, self.nrm
        p.cov = self.cov & mask
        p.dist = edge_dist(p.cov)
        return p


def edge_dist(cov, maxd=32):
    d = np.zeros(cov.shape, np.float32)
    cur = cov.copy()
    for k in range(maxd):
        er = cur.copy()
        er[1:] &= cur[:-1]; er[:-1] &= cur[1:]; er[:, 1:] &= cur[:, :-1]; er[:, :-1] &= cur[:, 1:]
        d[cur & ~er] = k
        cur = er
        if not cur.any():
            break
    d[cur] = maxd
    return d


# ---------------------------------------------------------------- painting helpers
def blur(a, r):
    """About a gaussian of radius r: three box passes, edges held."""
    out = np.asarray(a, np.float64)
    k = max(1, int(round(r)))
    for _ in range(3):
        for ax in (0, 1):
            pad = [(k + 1, k) if i == ax else (0, 0) for i in range(out.ndim)]
            c = np.cumsum(np.pad(out, pad, mode='edge'), axis=ax)
            hi = np.take(c, np.arange(2 * k + 1, c.shape[ax]), axis=ax)
            lo = np.take(c, np.arange(0, c.shape[ax] - 2 * k - 1), axis=ax)
            out = (hi - lo) / (2 * k + 1)
    return out


def noise(n, scale, octaves=4, seed=0):
    """Smooth value noise in 0..1, features about n/scale texels across."""
    r = np.random.default_rng(seed)
    out = np.zeros((n, n), np.float32); amp = 1.0; tot = 0.0
    for o in range(octaves):
        s = max(2, int(scale * 2 ** o))
        g = r.random((s, s)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray(g, 'F').resize((n, n), Image.BICUBIC)); tot += amp; amp *= 0.5
    return np.clip(out / tot, 0, 1)


def mix(c, col, t):
    t = np.clip(np.asarray(t, np.float32), 0, 1)
    if t.ndim == 2:
        t = t[..., None]
    return c * (1 - t) + np.asarray(col, np.float32) * t


def outline(mask, w=1):
    er = mask.copy()
    for _ in range(w):
        e = er.copy()
        e[1:] &= er[:-1]; e[:-1] &= er[1:]; e[:, 1:] &= er[:, :-1]; e[:, :-1] &= er[:, 1:]
        er = e
    return mask & ~er


def dashes(n, dash=5, gap=3):
    ys, xs = np.mgrid[0:n, 0:n]
    return ((xs + ys) % (dash + gap)) < dash


def luma(c):
    return c[..., 0] * 0.299 + c[..., 1] * 0.587 + c[..., 2] * 0.114


# ---------------------------------------------------------------- what each garment is made of
MATERIALS = ('denim', 'cotton', 'knit', 'ripstop', 'leather', 'nylon', 'rubber', 'metal', 'plastic', 'mesh', 'glass')


def material(item, texture):
    """A clothingItem's material, by Zomboid's names for it and its texture."""
    s = (item + ' ' + texture).lower()
    rules = [
        ('glass', ('glasses', 'goggles', 'monocle', 'visor')),
        ('metal', ('metal', 'chainmail', 'steel', 'armour_scrap', 'scrap', 'greave', 'vambrace', 'cuirass', 'tyre')),
        ('plastic', ('helmet', 'hardhat', 'crash', 'hockeymask', 'hockey', 'football', 'riot', 'shinpad', 'kneepad',
                     'elbowpad', 'shoulderpad', 'gasmask', 'respirator', 'welding', 'nbc', 'scba', 'bucket')),
        ('rubber', ('wellies', 'rubber', 'surgical', 'dish', 'flipflop', 'wader', 'wetsuit', 'apron_black')),
        ('leather', ('leather', 'boot', 'shoes', 'belt', 'holster', 'biker', 'cowboy', 'riding', 'glove', 'strap',
                     'bandolier', 'sheepskin', 'gaiter', 'jockey')),
        ('denim', ('denim', 'jean', 'dungaree', 'overall')),
        ('knit', ('jumper', 'sweater', 'beany', 'beanie', 'scarf', 'balaclava', 'cardigan', 'wool', 'legwarmer', 'sock',
                  'longjohn', 'winterhat', 'knit', 'turtleneck', 'earmuff')),
        ('nylon', ('padded', 'puffer', 'shellsuit', 'parka', 'ski', 'poncho', 'raincoat', 'windbreaker', 'tracksuit',
                   'hazmat', 'boilersuit', 'fireman', 'bulletvest', 'vest_bullet', 'highviz', 'hivis')),
        ('ripstop', ('army', 'camo', 'military', 'ghillie', 'chestrig', 'bag_chestrig', 'bdu', 'pilot', 'flight')),
        ('mesh', ('mesh_', 'fishnet', 'netting')),
    ]
    for mat, words in rules:
        if any(w in s for w in words):
            return mat
    return 'cotton'


# ---------------------------------------------------------------- the painting
def source(img, n, crisp):
    """Zomboid's texture at n: smooth upscaling, or for camo and prints its own colours kept crisp."""
    src = np.asarray(img.convert('RGBA').resize((n, n), Image.BICUBIC)).astype(np.float32) / 255
    rgb = np.clip(src[..., :3], 0, 1)
    if crisp:
        sm = np.stack([blur(rgb[..., i], n / 340) for i in range(3)], -1)
        pix = sm[src[..., 3] > 0.5]
        if len(pix) > 64:
            k = 5
            pal = pix[np.random.default_rng(1).choice(len(pix), k, replace=False)]
            sub = pix[::max(1, len(pix) // 20000)]
            for _ in range(10):
                lab = np.argmin(((sub[:, None] - pal[None]) ** 2).sum(-1), 1)
                pal = np.array([sub[lab == i].mean(0) if (lab == i).any() else pal[i] for i in range(k)])
            idx = np.argmin(((sm[..., None, :] - pal[None, None]) ** 2).sum(-1), -1)
            q = pal[idx]
            # keep where the source really varies within a colour (a print's detail), lose the pixel stair-steps
            rgb = q * 0.75 + sm * 0.25
    return rgb, src[..., 3]


def weave(mat, n, base, seed):
    """The cloth: a brightness multiplier (n x n), and where a material has its own colour detail, a tint."""
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
    f = np.ones((n, n), np.float32)
    if mat == 'denim':
        f *= 0.9 + 0.16 * (np.sin((xs + ys * 0.5) * 2 * np.pi / 3.2) * 0.5 + 0.5)
        f *= 0.9 + 0.2 * noise(n, 24, 3, seed)
    elif mat == 'cotton':
        f *= 0.95 + 0.07 * ((np.sin(xs * 2 * np.pi / 2.6) * np.sin(ys * 2 * np.pi / 2.6)) * 0.5 + 0.5)
        f *= 0.95 + 0.1 * noise(n, 60, 3, seed)
    elif mat == 'knit':
        v = np.abs(((xs / 5.0) % 1) - 0.5) * 2
        row = ((ys + v * 3.0) / 6.0) % 1
        f *= 0.84 + 0.26 * np.sin(row * np.pi) ** 0.6
        f *= 0.92 + 0.12 * noise(n, 40, 3, seed)
    elif mat == 'ripstop':
        grid = ((xs % 13) < 1.1) | ((ys % 13) < 1.1)
        f *= 0.95 + 0.08 * (np.sin((xs - ys) * 2 * np.pi / 2.5) * 0.5 + 0.5)
        f *= np.where(grid, 1.1, 1.0)
        f *= 0.95 + 0.1 * noise(n, 50, 3, seed)
    elif mat == 'leather':
        g = noise(n, 180, 2, seed)
        f *= 0.86 + 0.24 * g
        crease = np.abs(noise(n, 14, 3, seed + 1) - 0.5) < 0.012
        f *= np.where(crease, 0.78, 1.0)
    elif mat == 'nylon':
        f *= 0.97 + 0.05 * (np.sin((xs + ys) * 2 * np.pi / 2.0) * 0.5 + 0.5)
        f *= 0.9 + 0.18 * blur(noise(n, 10, 3, seed), 6)
    elif mat == 'rubber':
        f *= 0.93 + 0.1 * noise(n, 120, 2, seed)
    elif mat == 'metal':
        streak = blur(np.random.default_rng(seed).random((n, n)).astype(np.float32), 0.6)
        streak = np.asarray(Image.fromarray(streak, 'F').resize((n, n // 16), Image.BILINEAR).resize((n, n), Image.BILINEAR))
        f *= 0.85 + 0.3 * streak
        scratch = np.abs(np.sin(xs * 0.09 + ys * 0.31 + 8 * noise(n, 6, 2, seed + 2))) < 0.012
        f *= np.where(scratch & (noise(n, 20, 2, seed + 3) > 0.55), 1.35, 1.0)
    elif mat == 'plastic':
        f *= 0.96 + 0.07 * noise(n, 30, 3, seed)
    elif mat == 'mesh':
        hole = ((xs % 6) < 2) & ((ys % 6) < 2)
        f *= np.where(hole, 0.55, 1.0)
    return f


def seams(c, place, mat, thread, seed):
    """Every panel's edge turned under and stitched (cloth and leather); hard materials just darken at their rims."""
    d = place.dist
    n = place.n
    if mat in ('metal', 'plastic', 'glass', 'rubber'):
        rim = np.exp(-d / 2.0) * place.cov
        return mix(c, c * (0.6 if mat != 'metal' else 1.4), rim * (0.5 if mat != 'metal' else 0.35))
    c = mix(c, c * 0.72, np.exp(-d / 1.6) * place.cov * 0.65)
    st = dashes(n, 5, 3)
    for at in ((3.5, 6.5) if mat in ('denim', 'leather', 'ripstop') else (4.0,)):
        line = (np.abs(d - at) < 0.75) & place.cov & st
        c = mix(c, thread, line * 0.6)
    return c


def wear(c, place, mat, seed):
    n = place.n
    cov = place.cov
    x, y = place.pos[..., 0], place.pos[..., 1]
    front = np.clip(place.nrm[..., 2], 0, 1)
    pale = np.array([0.82, 0.80, 0.74], np.float32)
    soft = mat not in ('metal', 'plastic', 'glass')
    if soft:
        rub = np.exp(-place.dist / 4.0) * noise(n, 18, 3, seed + 10) * 0.35
        knees = front * np.exp(-((y - 0.50) / 0.08) ** 2) * 0.25
        elbows = np.exp(-((np.abs(x) - 0.42) / 0.06) ** 2) * (y > 1.2) * 0.2
        c = mix(c, c * 0.55 + pale * 0.45, (rub + knees + elbows) * cov)
    else:
        scuff = (noise(n, 26, 3, seed + 11) > 0.7) * 0.5
        c = mix(c, c * 0.7 + pale * 0.3, scuff * cov)
    # dirt: heavy round the feet and hems, a little everywhere, a few stains
    low = np.clip((0.32 - y) / 0.26, 0, 1)
    mud = np.array([0.24, 0.20, 0.15], np.float32)
    c = mix(c, mud, low * (0.3 + 0.45 * noise(n, 12, 3, seed + 12)) * cov * 0.8)
    c = mix(c, c * 0.82, (noise(n, 9, 4, seed + 13) > 0.64) * 0.45 * cov)
    spat = (noise(n, 70, 2, seed + 14) > 0.78) & (y < 0.45)
    c = mix(c, mud * 1.2, spat * 0.5 * cov)
    return c


def paint(img, place, mat, crisp=False, hand=None, seed=0, n=SIZE, overlay=False):
    """One texture painted again. img: Zomboid's; place: its texels on the body (Place); overlay: a garment painted on
    the skin, whose own alpha says where it is. Returns an RGBA image at n."""
    rgb, alpha = source(img, n, crisp)
    if overlay:
        place = place.masked(alpha > 0.5)
    cov = place.cov
    # Zomboid's painted shading, kept broad (its pixels go): the garment's colour is then the source smoothed
    lum = luma(rgb)
    shade_src = blur(lum, n / 170)
    med = np.median(shade_src[cov]) if cov.any() else 0.5
    shade = np.clip(shade_src / max(med, 0.05), 0.55, 1.3)
    colour = rgb if crisp else np.stack([blur(rgb[..., i], n / 400) for i in range(3)], -1)
    flat = colour / np.maximum(shade, 0.05)[..., None] if not crisp else colour
    c = flat * shade[..., None] if not crisp else colour
    c = c * weave(mat, n, c, seed)[..., None]
    thread = np.clip(np.median(c[cov], 0) * (0.62 if luma(np.median(c[cov], 0)[None])[0] > 0.35 else 1.6), 0, 1) \
        if cov.any() else np.array([0.5, 0.5, 0.5])
    if mat == 'denim':
        thread = np.array([0.70, 0.52, 0.27], np.float32)
    c = seams(c, place, mat, thread, seed)
    c = wear(c, place, mat, seed)
    if hand:
        c = hand(c, place, thread)
    out = np.nan_to_num(np.where(cov[..., None], c, rgb), nan=0.5)
    a = alpha
    if overlay:
        a = np.where(place.cov, np.maximum(alpha, 0), alpha)
    return Image.fromarray((np.dstack([np.clip(out, 0, 1), np.clip(a, 0, 1)]) * 255).astype('uint8'), 'RGBA')


def encode(img, place):
    """As a JPEG where it's solid over all it covers (most garments), else a PNG that keeps its alpha."""
    a = np.asarray(img)[..., 3]
    if place is not None and place.cov.any() and np.percentile(a[place.cov], 2) > 250:
        b = io.BytesIO(); img.convert('RGB').save(b, 'JPEG', quality=88, optimize=True)
        return b.getvalue()
    b = io.BytesIO(); img.save(b, 'PNG', optimize=True)
    return b.getvalue()


# ---------------------------------------------------------------- by hand
def _jeans(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    n = p.n
    st = dashes(n, 5, 3)
    front = np.clip(p.nrm[..., 2], 0, 1)
    fade = front * np.exp(-((y - 0.52) / 0.09) ** 2) * 0.4 + front * np.exp(-((y - 0.80) / 0.12) ** 2) * 0.25
    whisk = front * np.exp(-((y - 0.86) / 0.04) ** 2) * (np.sin(np.abs(x) * 260 + y * 40) > 0.6) * 0.45
    c = mix(c, [0.50, 0.60, 0.72], (fade + whisk) * p.cov)
    back = p.nrm[..., 2] < -0.35
    pocket = back & (np.abs(np.abs(x) - 0.085) < 0.055) & (y > 0.80) & (y < 0.93) & p.cov
    c = mix(c, c * 0.72, outline(pocket, 2) * 0.9)
    c = mix(c, thread, (outline(pocket, 4) & ~outline(pocket, 3) & st) * 0.7)
    yk = 0.925 + np.abs(x) * 0.3
    c = mix(c, c * 0.75, (back & (np.abs(y - yk) < 0.0025) & p.cov) * 0.8)
    c = mix(c, thread, (back & (np.abs(y - yk + 0.006) < 0.0018) & st & p.cov) * 0.7)
    fp = (p.nrm[..., 2] > 0.3) & (np.abs(x) > 0.06) & (np.abs(x) < 0.14) & (y > 0.92) & ((y - 0.92) > (0.14 - np.abs(x)) * 0.9) & p.cov
    c = mix(c, c * 0.7, outline(fp, 2) * 0.8)
    c = mix(c, thread, ((p.nrm[..., 2] > 0.5) & (np.abs(x - 0.018) < 0.0035) & (y > 0.86) & (y < 1.02) & p.cov) * 0.6)
    return c


def _field_jacket(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    front = (p.nrm[..., 2] > 0.35) & p.cov
    plk = front & (np.abs(x) < 0.012) & (y < 1.47)
    c = mix(c, c * 0.75, outline(plk, 2))
    for by in np.arange(1.0, 1.46, 0.075):
        btn = front & ((x ** 2 + ((y - by) * 0.8) ** 2) < 0.0062 ** 2)
        c = mix(c, [0.12, 0.11, 0.08], btn)
        c = mix(c, [0.32, 0.30, 0.22], outline(btn, 2) * 0.7)
    for sx in (-1, 1):
        pk = front & (np.abs(x - sx * 0.075) < 0.042) & (y > 1.24) & (y < 1.36)
        flap = front & (np.abs(x - sx * 0.075) < 0.046) & (y > 1.33) & (y < 1.375)
        c = mix(c, c * 0.68, outline(pk, 2) * 0.9)
        c = mix(c, c * 1.1, flap * 0.25)
        c = mix(c, c * 0.55, outline(flap, 2) * 0.9)
        c = mix(c, [0.12, 0.11, 0.08], front & (((x - sx * 0.075) ** 2 + (y - 1.345) ** 2) < 0.006 ** 2))
        lp = front & (np.abs(x - sx * 0.08) < 0.05) & (y > 1.0) & (y < 1.14)
        c = mix(c, c * 0.68, outline(lp, 2) * 0.9)
    return c


def _boots(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    shoe = p.cov
    if not shoe.any():
        return c
    top = y[shoe].max()
    c = mix(c, [0.07, 0.07, 0.065], (shoe & (y > top - 0.025)) * 0.8)
    lane = np.abs(np.abs(x) - 0.095)
    ladder = np.abs(((y * 100) % 2.2) - 1.1)
    lace = shoe & (p.nrm[..., 2] > 0.45) & (lane < 0.022) & (y > 0.07) & (y < top - 0.03)
    c = mix(c, [0.05, 0.05, 0.05], lace * 0.5)
    c = mix(c, [0.16, 0.15, 0.12], (lace & (ladder < 0.28)) * 0.95)
    c = mix(c, [0.45, 0.43, 0.38], (shoe & (np.abs(lane - 0.022) < 0.004) & (ladder < 0.35) & (y > 0.07) & (y < top - 0.03)) * 0.9)
    c = mix(c, [0.05, 0.045, 0.04], shoe & (y < 0.022))
    c = mix(c, [0.18, 0.16, 0.13], shoe & (np.abs(y - 0.022) < 0.0025))
    return c


# texture (Zomboid's path, lower case, no extension) -> painted by hand on top of the builder
HAND = {
    'clothes/trousers_mesh/trousersmesh_denim': _jeans,
    'clothes/trousers_mesh/trousersmesh_denimblack': _jeans,
    'clothes/trousers_mesh/trousersmesh_denimlight': _jeans,
    'clothes/jacket/jacket_camogreen': _field_jacket,
    'clothes/shoes_socks_textres/armyboots': _boots,
    'clothes/shoes_socks_textres/armyboots_desert': _boots,
}


# ---------------------------------------------------------------- by hand: the thirty most worn (2026-11-01)
# Where things are on the bodies at rest (T pose, metres, game space): feet at y 0, crown ~1.84, hips ~1.0, shoulders
# ~1.5, arms straight out along x to the wrists at |x| ~0.7, front at +z (normal z > 0).
MATERIAL = {  # textures whose material the names get wrong
    'clothes/jacket/jacket_navyblue': 'denim',
    'clothes/hat/armyhelmet': 'ripstop',
    'clothes/shoes_socks_textres/trainers_blue': 'nylon',
    'clothes/shoes_socks_textres/trainers_white': 'nylon',
    'clothes/bulletvest/vest_bulletcivilian': 'nylon',
}
CRISP = ('lumberjack', 'police', 'camo', 'shellsuit')


def _front(p, k=0.3):
    return (p.nrm[..., 2] > k) & p.cov


def _back(p, k=0.3):
    return (p.nrm[..., 2] < -k) & p.cov


def _box(p, cx, cy, w, h, side):
    x, y = p.pos[..., 0], p.pos[..., 1]
    return side & (np.abs(x - cx) < w / 2) & (np.abs(y - cy) < h / 2)


def _pocket(c, p, m, thread, flap=None, dark=0.68):
    st = dashes(p.n, 4, 3)
    c = mix(c, c * dark, outline(m, 2) * 0.9)
    c = mix(c, thread, (outline(m, 4) & ~outline(m, 3) & st) * 0.6)
    if flap is not None:
        c = mix(c, c * 1.08, flap * 0.3)
        c = mix(c, c * 0.5, outline(flap, 2) * 0.9)
        c = mix(c, thread, (outline(flap, 4) & ~outline(flap, 3) & st) * 0.6)
    return c


def _buttons(c, p, x0, ys, r=0.006, col=(0.12, 0.11, 0.09), side=None):
    x, y = p.pos[..., 0], p.pos[..., 1]
    side = _front(p) if side is None else side
    for by in ys:
        b = side & (((x - x0) ** 2 + (y - by) ** 2) < r ** 2)
        c = mix(c, col, b)
        c = mix(c, np.asarray(col) * 2.2 + 0.08, outline(b, 1) * 0.6)
    return c


def _zip(c, p, x0, y0, y1, side=None, metal=(0.62, 0.60, 0.55)):
    x, y = p.pos[..., 0], p.pos[..., 1]
    side = _front(p) if side is None else side
    tape = side & (np.abs(x - x0) < 0.007) & (y > y0) & (y < y1)
    teeth = tape & (np.abs(x - x0) < 0.0035) & (((y * 900).astype(int) % 2) == 0)
    c = mix(c, c * 0.55, tape * 0.8)
    c = mix(c, metal, teeth * 0.9)
    pull = side & (np.abs(x - x0 - 0.004) < 0.004) & (np.abs(y - y1 + 0.015) < 0.012)
    return mix(c, np.asarray(metal) * 1.1, pull)


def _rib(c, p, m, across, pitch=0.006, depth=0.22):
    """Ribbed knit over a mask: ribs run along the band; 'across' is the position the ribs alternate over."""
    r = (np.sin(across * 2 * np.pi / pitch) * 0.5 + 0.5)
    return mix(c, c * (1 - depth), m * r)


def _sleeve_end(p, w=0.06):
    x = np.abs(p.pos[..., 0])
    if not p.cov.any():
        return p.cov
    return p.cov & (x > x[p.cov].max() - w) & (x > 0.3)


def _hem(p, h=0.05, torso=0.25):
    x, y = p.pos[..., 0], p.pos[..., 1]
    t = p.cov & (np.abs(x) < torso)
    if not t.any():
        return t
    return t & (y < y[t].min() + h)


def _neck(p, h=0.04, torso=0.2):
    x, y = p.pos[..., 0], p.pos[..., 1]
    t = p.cov & (np.abs(x) < torso)
    if not t.any():
        return t
    return t & (y > y[t].max() - h)


def _tshirt(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    c = _rib(c, p, _neck(p, 0.025), x * 0.7 + z, 0.004, 0.18)
    # shoulder seams and a fold under each arm
    sh = p.cov & (np.abs(np.abs(x) - 0.17) < 0.004) & (y > 1.40)
    c = mix(c, c * 0.75, sh * 0.6)
    pit = p.cov & (np.exp(-((np.abs(x) - 0.2) / 0.03) ** 2 - ((y - 1.38) / 0.04) ** 2) > 0.3)
    return mix(c, c * 0.8, pit * 0.4)


def _hoodie(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    f = _front(p)
    pouch = f & (np.abs(x) < 0.11 - (y - 1.0) * 0.25) & (y > 1.0) & (y < 1.15)
    c = _pocket(c, p, pouch, thread, dark=0.7)
    for sx in (-1, 1):
        cord = f & (np.abs(x - sx * 0.035) < 0.0035) & (y > 1.33) & (y < 1.47)
        c = mix(c, c * 0.85 + 0.12, cord)
        c = mix(c, [0.55, 0.55, 0.52], f & (np.abs(x - sx * 0.035) < 0.005) & (np.abs(y - 1.33) < 0.008))
    c = _rib(c, p, _sleeve_end(p, 0.05), y + z, 0.005)
    return _rib(c, p, _hem(p, 0.05), x + z, 0.005)


def _trainers(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    s = p.cov
    sole = s & (y < 0.03)
    c = mix(c, [0.86, 0.85, 0.82], sole)
    c = mix(c, [0.25, 0.24, 0.22], s & (y < 0.008))
    c = mix(c, c * 0.6, s & (np.abs(y - 0.03) < 0.002))
    lane = np.abs(np.abs(x) - 0.095)
    lace = s & (p.nrm[..., 2] > 0.4) & (lane < 0.016) & (y > 0.05) & (y < 0.1)
    c = mix(c, [0.9, 0.9, 0.88], (lace & (np.abs(((y * 100) % 1.6) - 0.8) < 0.25)) * 0.9)
    side = s & (np.abs(p.nrm[..., 0]) > 0.5) & (y > 0.035) & (y < 0.075)
    c = mix(c, c * 1.25 + 0.06, (side & (np.abs(((z * 60) % 1) - 0.5) < 0.12)) * 0.6)
    return c


def _leather_jacket(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    c = _zip(c, p, 0.0, 0.70, 1.47)
    f = _front(p)
    for sx in (-1, 1):
        chest = f & (np.abs(y - 1.33 - (x * sx - 0.08) * 0.4) < 0.004) & (np.abs(x - sx * 0.09) < 0.045)
        c = mix(c, [0.6, 0.58, 0.54], chest * 0.8)
        c = _pocket(c, p, _box(p, sx * 0.09, 1.02, 0.07, 0.10, f), thread)
    c = mix(c, c * 1.6 + 0.05, (noise(p.n, 8, 3, 21) > 0.72) * 0.25 * p.cov)  # sheen where it's rubbed
    cuff = _sleeve_end(p, 0.05)
    return mix(c, c * 0.7, outline(cuff, 2) * 0.8)


def _denim_jacket(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    f = _front(p)
    copper = (0.62, 0.42, 0.22)
    c = _buttons(c, p, 0.0, np.arange(1.0, 1.47, 0.08), 0.007, copper)
    for sx in (-1, 1):
        pk = _box(p, sx * 0.08, 1.30, 0.07, 0.07, f)
        flap = _box(p, sx * 0.08, 1.345, 0.075, 0.03, f)
        c = _pocket(c, p, pk, thread, flap)
        c = _buttons(c, p, sx * 0.08, [1.335], 0.005, copper)
        yoke = f & (np.abs(y - 1.39) < 0.002) & (np.abs(x) > 0.02)
        c = mix(c, thread, (yoke & dashes(p.n, 4, 3)) * 0.6)
    band = _hem(p, 0.045)
    return mix(c, c * 0.8, outline(band, 2) * 0.7)


def _cap(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    if not p.cov.any():
        return c
    top = y[p.cov].max()
    crown = p.cov & (p.nrm[..., 1] > -0.2) & (y > top - 0.13)
    ang = np.arctan2(x, z + 0.03)
    seam = crown & (np.abs(((ang / (np.pi / 3)) % 1) - 0.5) > 0.46)
    c = mix(c, c * 0.6, seam * 0.8)
    c = mix(c, c * 0.5, p.cov & (y > top - 0.008))  # the button
    brim = p.cov & (z > 0.08) & (y < top - 0.06)
    c = mix(c, thread, (brim & (np.abs((z * 400) % 3 - 1.5) < 0.25)) * 0.4)
    eyelet = crown & (np.abs(y - (top - 0.06)) < 0.005) & (np.abs(((ang / (np.pi / 3)) % 1) - 0.25) < 0.04)
    return mix(c, c * 0.4, eyelet)


def _beanie(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    if not p.cov.any():
        return c
    bot = y[p.cov].min()
    ang = np.arctan2(x, z + 0.03)
    cuff = p.cov & (y < bot + 0.05)
    c = _rib(c, p, p.cov, ang, 0.06, 0.18)
    c = mix(c, c * 1.08, cuff * 0.5)
    return mix(c, c * 0.7, outline(cuff, 2) * 0.6)


def _cargo(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    side = (np.abs(p.nrm[..., 0]) > 0.5) & p.cov
    for sx in (-1, 1):
        s = side & (np.sign(x) == sx)
        pk = s & (np.abs(y - 0.62) < 0.07) & (np.abs(p.pos[..., 2] + 0.05) < 0.06)
        flap = s & (np.abs(y - 0.695) < 0.015) & (np.abs(p.pos[..., 2] + 0.05) < 0.065)
        c = _pocket(c, p, pk, thread, flap)
        knee = _front(p) & (np.sign(x) == sx) & (np.abs(y - 0.50) < 0.07) & (np.abs(np.abs(x) - 0.11) < 0.06)
        c = mix(c, c * 0.9, knee * 0.4)
        c = mix(c, thread, (outline(knee, 3) & ~outline(knee, 2) & dashes(p.n, 4, 3)) * 0.6)
        bp = _back(p) & (np.abs(x - sx * 0.085) < 0.05) & (y > 0.82) & (y < 0.93)
        bf = _back(p) & (np.abs(x - sx * 0.085) < 0.053) & (np.abs(y - 0.925) < 0.015)
        c = _pocket(c, p, bp, thread, bf)
    loops = p.cov & (y > 1.06) & (np.abs(((np.arctan2(x, p.pos[..., 2] + 0.07) / (np.pi / 4)) % 1) - 0.5) < 0.05)
    return mix(c, c * 0.65, loops * 0.7)


def _combat_shirt(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    f = _front(p)
    c = mix(c, c * 0.72, outline(f & (np.abs(x) < 0.012) & (y > 1.0), 2))
    c = _buttons(c, p, 0.0, np.arange(1.05, 1.45, 0.07), 0.005)
    for sx in (-1, 1):
        pk = _box(p, sx * 0.08, 1.30, 0.07, 0.08, f)
        flap = _box(p, sx * 0.08, 1.35, 0.075, 0.03, f)
        c = _pocket(c, p, pk, thread, flap)
        arm = p.cov & (np.abs(x - sx * 0.30) < 0.04) & (p.pos[..., 2] > -0.02) & (y > 1.40)
        c = _pocket(c, p, arm, thread)
    return c


def _plate_carrier(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    web = (_front(p, 0.4) | _back(p, 0.4)) & (y > 1.12) & (y < 1.32) & (np.abs(x) < 0.15)
    rows = web & ((((y - 1.12) * 1000).astype(int) % 25) < 4)
    c = mix(c, c * 0.62, rows * 0.9)
    tack = rows & ((((x + 0.2) * 1000).astype(int) % 38) < 3)
    c = mix(c, c * 1.3, tack * 0.8)
    plate = (_front(p, 0.4) | _back(p, 0.4)) & (np.abs(x) < 0.13) & (y > 1.1) & (y < 1.5)
    c = mix(c, c * 0.65, outline(plate, 2) * 0.8)
    tape = _box(p, -0.07, 1.42, 0.09, 0.025, _front(p, 0.4))
    c = mix(c, c * 0.8 + 0.06, tape)
    side = p.cov & (np.abs(p.nrm[..., 0]) > 0.6)
    return mix(c, [0.08, 0.08, 0.07], (side & ((((y * 1000).astype(int)) % 30) < 6)) * 0.6)


def _army_helmet(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    if not p.cov.any():
        return c
    top = y[p.cov].max()
    band = p.cov & (np.abs(y - (top - 0.09)) < 0.012)
    c = mix(c, [0.10, 0.10, 0.08], band * 0.85)
    c = mix(c, c * 0.6, p.cov & (np.abs(y - (top - 0.06) - np.sin(np.arctan2(x, z) * 3) * 0.012) < 0.002))
    rim = p.cov & (y < y[p.cov].min() + 0.012)
    return mix(c, [0.12, 0.12, 0.10], rim * 0.7)


def _gloves(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    ax = np.abs(x)
    knuck = p.cov & (p.nrm[..., 1] > 0.3) & (np.abs(ax - 0.78) < 0.02)
    c = mix(c, c * 0.65, outline(knuck, 2) * 0.8)
    c = mix(c, c * 1.15, knuck * 0.3)
    strap = p.cov & (np.abs(ax - 0.70) < 0.012)
    c = mix(c, c * 0.7, strap * 0.6)
    return mix(c, c * 0.5, outline(strap, 1) * 0.6)


def _flannel(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    f = _front(p)
    c = mix(c, c * 0.75, outline(f & (np.abs(x) < 0.012) & (y > 1.0), 2))
    c = _buttons(c, p, 0.0, np.arange(1.05, 1.45, 0.07), 0.005, (0.85, 0.83, 0.78))
    for sx in (-1, 1):
        c = _pocket(c, p, _box(p, sx * 0.08, 1.30, 0.065, 0.075, f), thread, _box(p, sx * 0.08, 1.345, 0.07, 0.025, f))
    c = mix(c, c * 1.08 + 0.03, noise(p.n, 90, 2, 31) * 0.25 * p.cov)  # brushed nap
    return c


def _padded(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    ch = p.cov & (np.abs(((y * 100) % 7) - 3.5) > 3.1) & (y < 1.55)
    c = mix(c, c * 0.6, ch * 0.7)
    puff = np.cos(((y * 100) % 7) / 7 * 2 * np.pi) * 0.5 + 0.5
    c = mix(c, c * 1.12, (1 - puff) * 0.35 * p.cov)
    c = _zip(c, p, 0.0, 0.97, 1.55)
    hood = p.cov & (y > 1.6)
    if hood.any():
        fur = hood & (p.dist < 12)
        strand = noise(p.n, 300, 1, 41)
        c = mix(c, [0.55, 0.50, 0.42], fur * (0.5 + 0.5 * strand))
    return c


def _jumper(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    c = _rib(c, p, _neck(p, 0.03), x + z, 0.005)
    c = _rib(c, p, _sleeve_end(p, 0.06), y + z, 0.005)
    c = _rib(c, p, _hem(p, 0.06), x + z, 0.005)
    cable = _front(p) & (np.abs(x) < 0.03) & (y > 1.0) & (y < 1.45)
    tw = np.sin(y * 2 * np.pi / 0.03 + np.sign(x) * np.pi / 2) * 0.5 + 0.5
    return mix(c, c * 0.75, cable * tw * 0.6)


def _socks(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    c = _rib(c, p, p.cov & (y > 0.08), x + p.pos[..., 2], 0.004, 0.15)
    top = p.cov & (y > y[p.cov].max() - 0.03) if p.cov.any() else p.cov
    c = _rib(c, p, top, x + p.pos[..., 2], 0.003, 0.25)
    heel = p.cov & (y < 0.07) & ((p.pos[..., 2] < -0.08) | (p.pos[..., 2] > 0.10))
    return mix(c, c * 0.78, heel * 0.6)


def _chest_rig(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    f = _front(p, 0.4)
    for i, px in enumerate((-0.10, -0.035, 0.035, 0.10)):
        pouch = _box(p, px, 1.17, 0.058, 0.11, f)
        flap = _box(p, px, 1.215, 0.06, 0.035, f)
        c = _pocket(c, p, pouch, thread, flap, dark=0.6)
        c = mix(c, [0.08, 0.08, 0.07], _box(p, px, 1.205, 0.012, 0.02, f))
    strap = p.cov & (np.abs(np.abs(x) - 0.09) < 0.016) & (y > 1.30)
    c = mix(c, c * 0.8, strap * 0.6)
    return mix(c, [0.10, 0.10, 0.09], _box(p, 0, 1.06, 0.30, 0.012, p.cov) * 0.8)


def _police_jacket(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    f = _front(p)
    c = _zip(c, p, 0.0, 0.95, 1.48)
    badge = f & (((x + 0.08) ** 2 + ((y - 1.36) * 0.8) ** 2) < 0.022 ** 2)
    c = mix(c, [0.78, 0.66, 0.30], badge * 0.9)
    c = mix(c, [0.45, 0.36, 0.14], outline(badge, 2))
    for sx in (-1, 1):
        ep = p.cov & (np.abs(np.abs(x) - 0.17) < 0.06) & (y > 1.49) & (np.sign(x) == sx)
        c = mix(c, c * 0.7, outline(ep, 2) * 0.8)
        c = _pocket(c, p, _box(p, sx * 0.09, 1.04, 0.07, 0.09, f), thread)
    return c


def _shellsuit(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    c = _zip(c, p, 0.0, 1.0, 1.55)
    arm = p.cov & (np.abs(x) > 0.22)
    stripe = arm & (np.abs(z - 0.0) < 0.012) | arm & (np.abs(z + 0.03) < 0.006)
    c = mix(c, [0.88, 0.88, 0.86], stripe * 0.9)
    c = _rib(c, p, _sleeve_end(p, 0.04), y + z, 0.004)
    c = _rib(c, p, _hem(p, 0.04), x + z, 0.004)
    return mix(c, c * 1.35 + 0.05, (noise(p.n, 6, 3, 51) > 0.65) * 0.25 * p.cov)


def _dress_shirt(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    f = _front(p)
    c = mix(c, c * 0.85, outline(f & (np.abs(x) < 0.012) & (y > 1.0), 1))
    c = _buttons(c, p, 0.0, np.arange(1.03, 1.47, 0.065), 0.0045, (0.92, 0.92, 0.9))
    c = mix(c, c * 0.88, _box(p, -0.08, 1.33, 0.06, 0.07, f) & outline(_box(p, -0.08, 1.33, 0.06, 0.07, f), 1))
    cuff = _sleeve_end(p, 0.05)
    return mix(c, c * 0.85, outline(cuff, 1))


def _suit_trousers(c, p, thread):
    x, y = p.pos[..., 0], p.pos[..., 1]
    for sx in (-1, 1):
        crease = _front(p) & (np.abs(x - sx * 0.105) < 0.0025) & (y < 0.95)
        c = mix(c, c * 1.18 + 0.03, crease * 0.7)
        c = mix(c, c * 0.75, _front(p) & (np.abs(x - sx * 0.105 - 0.004) < 0.002) & (y < 0.95))
        welt = _back(p) & (np.abs(x - sx * 0.085) < 0.045) & (np.abs(y - 0.93) < 0.003)
        c = mix(c, c * 0.45, welt)
    band = p.cov & (y > 1.05)
    return mix(c, c * 0.75, outline(band, 1) * 0.7)


def _tank_top(c, p, thread):
    x, z = p.pos[..., 0], p.pos[..., 2]
    edge = p.cov & (p.dist < 6)
    return _rib(c, p, edge, x + z + p.pos[..., 1], 0.004, 0.2)


def _sweatpants(c, p, thread):
    x, y, z = p.pos[..., 0], p.pos[..., 1], p.pos[..., 2]
    c = mix(c, c * 1.08 + 0.02, noise(p.n, 120, 2, 61) * 0.3 * p.cov)  # fleece
    cuff = p.cov & (y < 0.14)
    c = _rib(c, p, cuff, x + z, 0.005)
    band = p.cov & (y > 1.03)
    c = _rib(c, p, band, x + z, 0.005)
    side = p.cov & (np.abs(p.nrm[..., 0]) > 0.75) & (y > 0.14) & (y < 1.03)
    c = mix(c, [0.88, 0.88, 0.86], side * 0.85)
    cord = _front(p) & (np.abs(np.abs(x) - 0.02) < 0.003) & (y > 0.98) & (y < 1.06)
    return mix(c, [0.9, 0.9, 0.88], cord)


HAND.update({
    'clothes/shirt_tshirt_textures/tshirt_white': _tshirt,
    'clothes/hoodie/hoodieup_white': _hoodie,
    'clothes/shoes_socks_textres/trainers_blue': _trainers,
    'clothes/shoes_socks_textres/trainers_white': _trainers,
    'clothes/jacketlong/jacketlong_black': _leather_jacket,
    'clothes/jacketlong/jacketlong_brownleather': _leather_jacket,
    'clothes/jacket/jacket_navyblue': _denim_jacket,
    'clothes/hat/baseballcapred': _cap,
    'clothes/hat/baseballcapgreen': _cap,
    'clothes/hat/baseballcapblue': _cap,
    'clothes/hat/beanyhatwhite': _beanie,
    'clothes/trousers_mesh/trousersmesh_camogreen': _cargo,
    'clothes/trousers_mesh/trousers_camoolivedrab': _cargo,
    'clothes/shirt_tshirt_textures/shirt_camogreen': _combat_shirt,
    'clothes/bulletvest/vest_bulletcivilian': _plate_carrier,
    'clothes/bulletvest/vest_bulletcamogreen': _plate_carrier,
    'clothes/hat/armyhelmet': _army_helmet,
    'clothes/gloves/fingerlessleathergloves_black': _gloves,
    'clothes/gloves/leathergloves_black': _gloves,
    'clothes/gloves/leathergloves_brown': _gloves,
    'clothes/jacketpadded/paddedjacket': _padded,
    'clothes/jumper/jumper_roundneck': _jumper,
    'clothes/shoes_socks_textres/whitesocks_long': _socks,
    'clothes/bag/chestrig': _chest_rig,
    'clothes/jacket/jacket_police': _police_jacket,
    'clothes/jacket/jacket_shellsuit_black': _shellsuit,
    'clothes/shirt_tshirt_textures/shirt_formal': _dress_shirt,
    'clothes/suittrousers/suittrousersmesh_suit': _suit_trousers,
    'clothes/jumper/jumper_tanktop': _tank_top,
})
for _c in ('blue', 'green', 'red', 'yellow'):
    HAND['clothes/trousers_mesh/trousers_sport' + _c] = _sweatpants
for _c in ('blue', 'green', 'grey', 'red', 'yellow'):
    HAND['clothes/shirt_tshirt_textures/shirt_lumberjack' + _c] = _flannel
