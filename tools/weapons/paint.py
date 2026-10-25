"""Procedural surface painting for the weapon models.

Every face of every box gets its own patch of texture. The painter knows where each pixel sits
in the model (in 1/16 block units), so wood grain, rust and grime run on across the faces of a
part instead of restarting at each edge, and it knows how far each pixel is from the face's
edges, which is where paint chips and metal gets polished by hands.
"""
import numpy as np

# ---------------------------------------------------------------- noise


def _hash(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = h.astype(np.int64) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0xFFFFFF)


def vnoise(x, y, z, seed=0):
    """Smooth value noise in [0, 1)."""
    x0, y0, z0 = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx, fy, fz = [f * f * (3 - 2 * f) for f in (fx, fy, fz)]
    ix, iy, iz = x0.astype(np.int64), y0.astype(np.int64), z0.astype(np.int64)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out = out + w * _hash(ix + dx, iy + dy, iz + dz, seed)
    return out


def fbm(x, y, z, seed=0, octaves=4):
    total, amp, norm = 0, 1.0, 0
    for o in range(octaves):
        total = total + amp * vnoise(x * 2 ** o, y * 2 ** o, z * 2 ** o, seed + o * 31)
        norm += amp
        amp *= 0.5
    return total / norm


def white(shape, rng):
    return rng.random(shape)


def mix(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) else t
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def col(hexstr):
    hexstr = hexstr.lstrip('#')
    return np.array([int(hexstr[i:i + 2], 16) / 255 for i in (0, 2, 4)])


# ---------------------------------------------------------------- the painting context


class Face:
    """One face patch: pixel positions in model space plus helpers the materials use."""

    def __init__(self, P, normal, edge, box, rng, wear, seed, uv):
        self.P = P                    # (H, W, 3) model-space position of each pixel centre
        self.n = normal               # outward normal (axis-aligned, before element rotation)
        self.edge = edge              # (H, W) distance to the nearest face edge, model units
        self.box = box                # the part being painted
        self.rng = rng
        self.wear = wear              # 0 fresh .. 1 battered
        self.seed = seed
        self.uv = uv                  # (H, W, 2) face-local coords in model units (u right, v down)
        self.shape = P.shape[:2]
        dims = box['hi'] - box['lo']
        self.axis = box.get('axis', int(np.argmax(dims)))   # the part's long axis
        self.dims = dims

    @property
    def X(self):
        return self.P[..., 0]

    @property
    def Y(self):
        return self.P[..., 1]

    @property
    def Z(self):
        return self.P[..., 2]

    def along(self):
        return self.P[..., self.axis]

    def across(self):
        """Distances from the part's centre line on the two other axes."""
        c = (self.box['lo'] + self.box['hi']) / 2
        a, b = [i for i in range(3) if i != self.axis]
        return self.P[..., a] - c[a], self.P[..., b] - c[b]

    def noise(self, f, seed=0, octaves=4, stretch=(1, 1, 1)):
        sx, sy, sz = stretch
        return fbm(self.X * f * sx, self.Y * f * sy, self.Z * f * sz, self.seed * 7 + seed, octaves)

    def end_face(self):
        return abs(self.n[self.axis]) > 0.5


# ---------------------------------------------------------------- base materials
# each takes a Face and returns (H, W, 3) floats in 0..1


def stretch_for(f, along=0.12):
    s = [1.0, 1.0, 1.0]
    s[f.axis] = along
    return s


def wood(f, light='#c9a77a', dark='#8a6440', ring=2.6, lacquer=0.06):
    a, b = f.across()
    r = np.sqrt(a * a + b * b)
    warp = f.noise(0.35, 1, 3) * 1.8
    rings = np.sin((r * ring + warp + f.along() * 0.05) * np.pi * 2) * 0.5 + 0.5
    fibres = f.noise(3.0, 2, 3, stretch_for(f, 0.05))
    t = np.clip(rings ** 3 * 0.55 + fibres * 0.45 + (f.noise(0.6, 3, 2) - 0.5) * 0.4, 0, 1)
    c = mix(col(light), col(dark), t)
    if f.end_face():   # end grain: rings plain to see, darker
        c = mix(col(light), col(dark), np.clip(rings * 0.8 + 0.2, 0, 1)) * 0.82
    return c * (1 + lacquer * (fibres - 0.5))[..., None]


def steel(f, base=0.58, tint='#8f969c', brushed=True, spread=0.18):
    t = col(tint) / max(col(tint))
    streak = f.noise(5.0, 4, 2, stretch_for(f, 0.04)) if brushed else f.noise(4.0, 4, 2)
    blot = f.noise(0.8, 5, 3)
    v = base + (streak - 0.5) * spread + (blot - 0.5) * 0.12
    return v[..., None] * t


def rust(f, c, amount, seed=11):
    """Mottled orange-brown corrosion over c where noise rises past the amount."""
    if amount <= 0:
        return c
    n = f.noise(1.1, seed, 4)
    speck = white(f.shape, f.rng)
    m = np.clip((n - (1 - amount * 0.65)) * 6, 0, 1)
    m = np.maximum(m, (speck > 1 - amount * 0.05) * 0.8)
    tone = mix(col('#5b2f17'), col('#9a5426'), f.noise(4.0, seed + 1, 2))
    return mix(c, tone, m)


def paint(f, colour, steel_base=0.42, chips=0.5, gloss=0.05):
    """Enamel over steel, chipped along edges and scuffed in patches."""
    c = np.ones(f.shape + (3,)) * col(colour)
    c = c * (1 + (f.noise(2.0, 21, 3) - 0.5) * 0.18 + gloss * (f.noise(0.5, 22, 2) - 0.5))[..., None]
    under = steel(f, steel_base, spread=0.12)
    wear = chips * (0.4 + f.wear)
    n = f.noise(2.6, 23, 3)
    edge_chip = (f.edge < 0.08 + 0.22 * wear) & (n > 0.62 - 0.25 * wear)
    patch = n > 0.92 - 0.15 * wear
    m = edge_chip | patch
    c = np.where(m[..., None], under, c)
    # a thin dark rim round each chip
    rim = (~m) & ((f.edge < 0.12 + 0.22 * wear) & (n > 0.57 - 0.25 * wear) | (n > 0.88 - 0.15 * wear))
    c = np.where(rim[..., None], c * 0.72, c)
    return c


def anodised(f, base='#2b2e33', wear_to='#70767c'):
    c = np.ones(f.shape + (3,)) * col(base)
    c = c * (1 + (f.noise(6.0, 31, 2) - 0.5) * 0.25 + (f.noise(0.7, 32, 2) - 0.5) * 0.2)[..., None]
    n = f.noise(3.5, 33, 3)
    # hard anodising only wears through in thin broken lines along the most handled edges: a wide pale rim
    # round every face is what turned the whole rifle grey once the texture was shrunk on screen
    worn = (f.edge < 0.012 + 0.012 * f.wear) & (n > 0.64 - 0.1 * f.wear)
    c = np.where(worn[..., None], mix(col(wear_to), c, 0.4), c)
    return c


def polymer(f, base='#2c2c2e', stipple=0.09):
    c = np.ones(f.shape + (3,)) * col(base)
    s = white(f.shape, f.rng)
    c = c * (1 + (s - 0.5) * stipple * 2 + (f.noise(1.0, 41, 2) - 0.5) * 0.15)[..., None]
    rub = (f.edge < 0.03) & (f.noise(4.0, 42, 2) > 0.5)
    return np.where(rub[..., None], c * 1.35, c)


def parkerised(f, base='#474940'):
    c = np.ones(f.shape + (3,)) * col(base)
    s = white(f.shape, f.rng)
    c = c * (1 + (s - 0.5) * 0.22 + (f.noise(1.4, 51, 3) - 0.5) * 0.25)[..., None]
    worn = (f.edge < 0.015) & (f.noise(3.0, 52, 2) > 0.65)
    return np.where(worn[..., None], mix(c, col('#6a6d6e'), 0.45), c)


def tape(f, base='#18181a', band=0.55, sheen=0.08):
    """Wrapped tape: a helix of overlapping turns with slightly raised edges."""
    a, b = f.across()
    ang = np.arctan2(b, a) / (2 * np.pi)
    phase = (f.along() / band + ang) % 1.0
    ridge = np.clip(1 - np.abs(phase - 0.5) * 6, 0, 1)
    c = np.ones(f.shape + (3,)) * col(base)
    c = c * (1 + sheen * (f.noise(3, 61, 2) - 0.5) * 2 + ridge * 0.5 - 0.15)[..., None]
    fray = (f.noise(5.0, 62, 2) > 0.8) & (f.edge < 0.1)
    return np.where(fray[..., None], c * 1.5, c)


def rubber(f, base='#141414', ring=0.32):
    ridges = (np.sin(f.along() / ring * np.pi * 2) > 0.3)
    c = np.ones(f.shape + (3,)) * col(base)
    c = c * (1 + (white(f.shape, f.rng) - 0.5) * 0.12)[..., None]
    return np.where(ridges[..., None], c * 1.5, c)


def cast_iron(f, base='#383432', rim=True):
    c = np.ones(f.shape + (3,)) * col(base)
    c = c * (1 + (white(f.shape, f.rng) - 0.5) * 0.3 + (f.noise(1.2, 71, 3) - 0.5) * 0.3)[..., None]
    scuff = f.noise(2.5, 72, 3) > 0.7
    c = np.where(scuff[..., None], mix(c, col('#6a645e'), 0.6), c)
    if not rim:
        return c
    edge = f.edge < 0.06
    return np.where(edge[..., None], mix(c, col('#7a756f'), 0.5), c)


def chrome(f):
    a, b = f.across()
    t = np.sin((a + b) * 3.0 + f.noise(0.4, 81, 2) * 4) * 0.5 + 0.5
    c = mix(col('#5f666d'), col('#e4e8ea'), t ** 1.5)
    return c * (1 + (f.noise(6.0, 82, 2, stretch_for(f, 0.05)) - 0.5) * 0.12)[..., None]


def brass(f):
    return steel(f, 0.7, tint='#c8a24a', spread=0.2)


def galvanised(f):
    c = steel(f, 0.52, tint='#9aa1a3', brushed=False, spread=0.1)
    spangle = vnoise(f.X * 2.2, f.Y * 2.2, f.Z * 2.2, f.seed + 91)
    c = c * (0.88 + 0.24 * np.floor(spangle * 4)[..., None] / 4)
    bloom = f.noise(1.0, 92, 3) > 0.7
    return np.where(bloom[..., None], mix(c, col('#d8d8cf'), 0.45), c)


def duct_tape(f):
    weave = (np.sin(f.uv[..., 0] * 25) * np.sin(f.uv[..., 1] * 25) > 0.2)
    c = np.ones(f.shape + (3,)) * col('#8d9294')
    c = c * (1 + (f.noise(1.5, 101, 3) - 0.5) * 0.35)[..., None]
    c = np.where(weave[..., None], c * 0.94, c)
    return tape(f, '#8f9496', band=0.9, sheen=0.15) * 0.5 + c * 0.5


def fibreglass(f, base='#d9a619'):
    c = np.ones(f.shape + (3,)) * col(base)
    fib = f.noise(4.0, 111, 2, stretch_for(f, 0.03))
    return c * (1 + (fib - 0.5) * 0.25 + (f.noise(0.8, 112, 2) - 0.5) * 0.15)[..., None]


def silk_wrap(f):
    """Katana handle: black cord in a diamond lattice over pale ray skin."""
    u = f.along() / 0.75
    a, b = f.across()
    w = (a + b) / 0.75
    d1 = (u + w) % 1.0
    d2 = (u - w) % 1.0
    cord = (np.abs(d1 - 0.5) > 0.22) | (np.abs(d2 - 0.5) > 0.22)
    skin = mix(col('#ddd6c4'), col('#b9b19a'), white(f.shape, f.rng) * 0.7)
    silk = np.ones(f.shape + (3,)) * col('#16161c') * (1 + (f.noise(5, 121, 2) - 0.5) * 0.6)[..., None]
    return np.where(cord[..., None], silk, skin)


def leather(f, base='#4a2f1f'):
    c = np.ones(f.shape + (3,)) * col(base)
    return c * (1 + (f.noise(3.0, 131, 3) - 0.5) * 0.35 + (white(f.shape, f.rng) - 0.5) * 0.1)[..., None]


def plain(f, colour, noise=0.12):
    c = np.ones(f.shape + (3,)) * col(colour)
    return c * (1 + (f.noise(2.0, 141, 3) - 0.5) * noise)[..., None]


def blade(f, base=0.66, edge_axis=0, edge_sign=1, bevel=0.32, hamon=False, dark=None):
    """A blade's flats with a bright ground bevel along the cutting edge (+x by default)."""
    c = steel(f, base, spread=0.16) if dark is None else plain(f, dark, 0.2)
    lo, hi = f.box['lo'][edge_axis], f.box['hi'][edge_axis]
    edge_at = hi if edge_sign > 0 else lo
    d = np.abs(edge_at - f.P[..., edge_axis])
    if f.box.get('no_edge'):
        d = d + 99
    wobble = (f.noise(1.5, 151, 2) - 0.5) * 0.12
    bright = steel(f, 0.86, spread=0.1)
    if hamon:
        line = d < bevel * 2.2 + wobble * 3 + np.sin(f.along() * 2.2) * 0.12
        c = np.where(line[..., None], mix(c, col('#eef0f2'), 0.55), c)
    c = np.where((d < bevel + wobble)[..., None], bright, c)
    return c


# ---------------------------------------------------------------- finishing passes


def round_shade(f, c):
    """Darken towards the sides of a round bar's faces so stepped rods read as round."""
    if not f.box.get('round') or f.end_face():
        return c
    a, b = f.across()
    r = f.box['round']
    # which across-axis lies in this face?
    axes = [i for i in range(3) if i != f.axis]
    k = 0 if abs(f.n[axes[0]]) < 0.5 else 1
    off = (a if k == 0 else b) / max(r, 1e-3)
    s = 1.0 - 0.28 * np.clip(np.abs(off), 0, 1.2) ** 2
    return c * s[..., None]


def scratches(f, c, amount):
    if amount <= 0:
        return c
    H, W = f.shape
    out = c.copy()
    n = int(f.rng.poisson(amount * (H * W) / 400.0))
    for _ in range(n):
        y, x = f.rng.integers(0, H), f.rng.integers(0, W)
        ang = f.rng.random() * np.pi
        L = f.rng.integers(3, 12)
        for t in range(L):
            yy, xx = int(y + np.sin(ang) * t), int(x + np.cos(ang) * t)
            if 0 <= yy < H and 0 <= xx < W:
                out[yy, xx] = np.minimum(out[yy, xx] * 1.25 + 0.05, 1)
    return out


def grime(f, c, amount):
    if amount <= 0:
        return c
    n = f.noise(0.9, 161, 4)
    g = np.clip((n - 0.45) * 2.2, 0, 1) * amount
    return mix(c, c * col('#4a4136') * 1.4, g)


def blood(f, c, amount, reach):
    """Dried blood: dark splashes on the business end (reach = how far up the weapon), drips below."""
    if amount <= 0:
        return c
    h = np.clip((f.P[..., 1] - reach[0]) / max(reach[1] - reach[0], 1e-3), 0, 1)
    n = f.noise(0.9, 171, 4)
    spot = f.noise(3.5, 172, 2)
    m = (n * 0.75 + spot * 0.25) > (1.02 - amount * 0.55 * h)
    fine = white(f.shape, f.rng) > 1 - amount * 0.03 * h
    fresh = mix(col('#3d0807'), col('#6e100c'), f.noise(2.0, 173, 2))
    out = np.where(m[..., None], mix(c, fresh, 0.85), c)
    return np.where(fine[..., None], mix(out, fresh, 0.7), out)
