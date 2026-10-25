"""LabPBR material maps for the weapon textures: _s (specular) and _n (normal), painted alongside the colour.

Shader packs that read LabPBR (Photon among them) use these; vanilla ignores them. The format:
  _s: R perceptual smoothness, G reflectance (0-229 = F0 of a non-metal, 230-237 = a named metal, 255 =
      metal tinted by the colour), B porosity (0-64), A emission (0-254, 255 = none).
  _n: R and G the normal's tilt (DirectX: G points down the image), B ambient occlusion (255 = none),
      A height (255 = top).

Each material gets a few numbers. Wear decides where the finish has gone and bare metal shows: those
pixels are picked out by how much lighter than the finish they were painted.
"""
import numpy as np

IRON, GOLD, ALUMINIUM, CHROME, COPPER = 230, 231, 232, 233, 234
TINTED_METAL = 255
NONMETAL = 10           # about 4% reflectance, plastics, paint, rubber, glass

# smoothness, reflectance byte, and optionally (lightness over which the finish is worn, its reflectance, its
# smoothness). A black gun stays black under a shader pack: the finishes are all non-metal with a soft sheen,
# worn edges only a little shinier, and the few bare steel parts are metal tinted by their own dark colour.
# Named metals (iron, aluminium) ignore the colour and shine silver whatever it is, and on thin edges the
# texture's smaller copies blend their reflectance byte into the non-metal range as a near mirror: that is
# what made the rifle look silver.
WORN = 0.52
MATERIALS = {
    'anod': (0.44, NONMETAL, (0.36, NONMETAL, WORN)),
    'rail': (0.42, NONMETAL, (0.34, NONMETAL, WORN)),
    'guard': (0.42, NONMETAL, (0.36, NONMETAL, WORN)),
    'park': (0.32, NONMETAL, (0.40, NONMETAL, 0.46)),
    'hider': (0.30, NONMETAL, (0.40, NONMETAL, 0.46)),
    'steel': (0.50, TINTED_METAL, None),
    'bright': (0.58, TINTED_METAL, None),
    'port': (0.36, NONMETAL, (0.40, NONMETAL, 0.46)),
    'poly': (0.38, NONMETAL, None),
    'grip': (0.28, NONMETAL, None),
    'gripper': (0.28, NONMETAL, None),
    'scope': (0.44, NONMETAL, (0.36, NONMETAL, WORN)),
    'knurl': (0.38, NONMETAL, (0.36, NONMETAL, WORN)),
    'bezel': (0.42, NONMETAL, (0.36, NONMETAL, WORN)),
    'cap': (0.30, NONMETAL, (0.40, NONMETAL, 0.44)),
    'lamp': (0.92, 18, None),
    'lens_green': (0.92, 18, None),
    'tan': (0.34, NONMETAL, None),
    'mag': (0.34, NONMETAL, None),
    'rubber': (0.14, 8, None),
    'bore': (0.08, 6, None),
    'housing': (0.42, NONMETAL, None),
    'bezel_r': (0.42, NONMETAL, (0.36, NONMETAL, WORN)),
    'mark': (0.40, NONMETAL, (0.40, NONMETAL, WORN)),
    'screw': (0.50, TINTED_METAL, None),
    'can': (0.28, NONMETAL, (0.42, NONMETAL, 0.42)),
    'lens': (0.96, 18, None),
    'glass': (0.96, 18, None),
    'dot': (0.5, NONMETAL, None),
    'flash': (0.0, NONMETAL, None),
    'brass': (0.62, TINTED_METAL, None),
}
# how much rain darkens a finish (LabPBR porosity, 0-64): stippled grips and phosphate soak a little
POROSITY = {'grip': 18, 'gripper': 18, 'park': 10, 'hider': 10, 'cap': 10, 'can': 14, 'rubber': 6, 'tan': 6,
            'mag': 6, 'poly': 4}
EMISSIVE = {'dot': 254, 'flash': 254, 'reticle': 254}


def lum(rgb):
    return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722


def spec(mat, f, rgb):
    smooth, f0, bare = MATERIALS.get(mat, (0.3, NONMETAL, None))
    H, W = f.shape
    out = np.zeros((H, W, 4))
    s = np.full((H, W), smooth) + (f.noise(3.0, 401, 2) - 0.5) * 0.08
    g = np.full((H, W), f0 / 255.0)
    if bare:
        at, f0_worn, msmooth = bare
        m = lum(rgb) > at
        s = np.where(m, msmooth, s)
        g = np.where(m, f0_worn / 255.0, g)
    out[..., 0] = np.clip(s, 0, 1)
    out[..., 1] = g
    out[..., 2] = np.where(g > 229 / 255.0, 0, POROSITY.get(mat, 0) / 255.0)
    out[..., 3] = EMISSIVE.get(mat, 255) / 255.0
    return out


def round_tilt(f, k=0.7):
    """A round bar's faces lean outward toward their sides, so the stepped boxes of a rod catch the light
    like a cylinder: returns the tilt along the patch's u and v (zero on flat parts and end faces)."""
    r = f.box.get('round')
    if not r or f.end_face():
        return 0.0, 0.0
    a, b = f.across()
    axes = [i for i in range(3) if i != f.axis]
    off = np.clip((a if abs(f.n[axes[0]]) < 0.5 else b) / r, -1, 1)
    H, W = off.shape
    du = np.sign(off[:, -1].mean() - off[:, 0].mean()) if W > 1 else 0.0
    dv = np.sign(off[-1, :].mean() - off[0, :].mean()) if H > 1 else 0.0
    return off * k * du, off * k * dv


def normal(f, rgb, lu, lv, px, bevel=0.09, bump=0.5, tilt=0.55, height=None, rel=False):
    """Edge bevels (so blocky parts catch the light like machined edges), the lean of round bars, plus a
    little relief from the painted detail (and `height`, if given). lu, lv: the face's size in model units;
    px: texture pixels per unit. rel: relief from the detail's contrast against the face's own lightness, so
    checkering and serrations on a near-black finish stand out as much as on a light one."""
    u, v = f.uv[..., 0], f.uv[..., 1]
    b = max(1.0 / px, min(bevel, 0.3 * min(lu, lv)))

    def t(d):
        return tilt * np.clip(1 - d / b, 0, 1)

    ru, rv = round_tilt(f)
    nx = -t(u) + t(lu - u) + ru
    ny = -t(v) + t(lv - v) + rv
    L = lum(rgb)
    if rel:
        L = L * 0.35 / np.clip(L.mean(), 0.1, 0.35)
    if height is not None:
        L = L + height
    if L.shape[0] > 2 and L.shape[1] > 2:
        dv, du = np.gradient(L)
        nx = nx - du * bump
        ny = ny - dv * bump
    r = np.sqrt(nx * nx + ny * ny)
    k = np.where(r > 0.9, 0.9 / np.maximum(r, 1e-6), 1.0)
    nx, ny = nx * k, ny * k
    H, W = f.shape
    out = np.ones((H, W, 4))
    out[..., 0] = nx * 0.5 + 0.5
    out[..., 1] = ny * 0.5 + 0.5
    return out


# ---------------------------------------------------------------- melee weapons
# Each melee material is one of these kinds. The kind reads the painted colour (before grime, scratches
# and blood go on) to tell the finish from bare metal from rust; those passes then change it where they
# landed. B in _s is porosity (0-64): how much a surface soaks up rain and darkens in it, which shader packs
# with wetness use.

PLAIN = {  # smoothness, reflectance byte, porosity
    'lacquer': (0.56, NONMETAL, 6),       # a lacquered bat or stick
    'varnish': (0.40, NONMETAL, 22),      # a varnished tool handle, worn thin
    'cloth_tape': (0.10, NONMETAL, 52),
    'grip_tape': (0.22, NONMETAL, 28),
    'duct': (0.46, NONMETAL, 4),
    'polymer': (0.36, NONMETAL, 2),
    'rubber': (0.14, 8, 0),
    'fibreglass': (0.60, NONMETAL, 2),
    'cord': (0.30, NONMETAL, 40),         # silk cord over ray skin
    'card': (0.18, NONMETAL, 60),
}
METAL_UNDER = {'steel', 'blade', 'painted', 'cast_iron', 'galvanised', 'chrome', 'brass'}
RUST = (0.12, NONMETAL, 56)
BLOOD = (0.48, 6, 0)


def sat(rgb):
    mx = rgb.max(-1)
    return (mx - rgb.min(-1)) / np.maximum(mx, 1e-3)


def rustiness(rgb):
    """How much of each pixel is orange-brown corrosion: rust is warm and saturated, steel is grey, and red
    paint is far redder (much less green) than rust."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    ratio = g / np.maximum(r, 1e-3)
    warm = (r > g) & (g > b) & (ratio > 0.3) & (ratio < 0.8)
    return np.where(warm, np.clip((sat(rgb) - 0.15) / 0.4, 0, 1), 0.0)


def melee_spec(kind, f, rgb, wear):
    """Smoothness, reflectance (0-1) and porosity (0-1 of the byte) for a melee surface of this kind."""
    H, W = f.shape
    jitter = (f.noise(3.0, 401, 2) - 0.5) * 0.08
    if kind in PLAIN:
        s0, g0, p0 = PLAIN[kind]
        if kind in ('lacquer', 'varnish'):
            s0 -= 0.18 * wear          # the finish wears dull where hands and hits go
        return (np.full((H, W), s0) + jitter, np.full((H, W), g0 / 255.0), np.full((H, W), p0 / 255.0))
    L = lum(rgb)
    s = np.zeros((H, W))
    g = np.full((H, W), IRON / 255.0)
    p = np.zeros((H, W))
    if kind in ('steel', 'blade'):
        s = 0.42 + 0.42 * np.clip((L - 0.3) / 0.55, 0, 1)        # the brighter, the more polished
        if kind == 'blade':                                   # a dark coated blade (the machete's)
            coat = (L < 0.42) & (sat(rgb) < 0.2)
            s = np.where(coat, 0.32, s)
            g = np.where(coat, NONMETAL / 255.0, g)
    elif kind == 'painted':
        bare = sat(rgb) < 0.18
        s = np.where(bare, 0.5, 0.5 - 0.14 * wear)
        g = np.where(bare, IRON / 255.0, NONMETAL / 255.0)
    elif kind == 'cast_iron':                                 # seasoned or blackened, scuffed to bare iron
        bare = L > 0.3
        s = np.where(bare, 0.46, 0.40)
        g = np.where(bare, IRON / 255.0, NONMETAL / 255.0)
        p = np.where(bare, 0.0, 6 / 255.0)
    elif kind == 'galvanised':                                # zinc, with white oxide bloom
        bloom = L > 0.63
        s = np.where(bloom, 0.2, 0.42)
        g = np.where(bloom, NONMETAL / 255.0, TINTED_METAL / 255.0)
        p = np.where(bloom, 20 / 255.0, 0.0)
    elif kind == 'chrome':
        s = np.full((H, W), 0.86)
        g = np.full((H, W), CHROME / 255.0)
    elif kind == 'brass':
        s = np.full((H, W), 0.62)
        g = np.full((H, W), TINTED_METAL / 255.0)
    else:
        raise KeyError(f'no melee PBR kind {kind!r}')
    if kind != 'chrome' and kind != 'brass':
        rust = rustiness(rgb)
        s = s * (1 - rust) + RUST[0] * rust
        g = np.where(rust > 0.5, RUST[1] / 255.0, g)
        p = p * (1 - rust) + RUST[2] / 255.0 * rust
    return s + jitter, g, p


def melee_layers(kind, f, base, final, wear, marks, lu, lv, px):
    """The _s and _n patches for one melee face. base: the painted colour; final: after grime, scratches
    and blood; marks: how strongly each of those passes landed on each pixel."""
    s, g, p = melee_spec(kind, f, base, wear)
    grime = marks.get('grime')
    if grime is not None:
        s = s * (1 - 0.6 * grime)
        g = np.where((grime > 0.55) & (g > 229 / 255.0), NONMETAL / 255.0, g)
        p = np.maximum(p, 40 / 255.0 * grime)
    scratch = marks.get('scratch')
    if scratch is not None:
        if kind in METAL_UNDER:                               # scored through to bright metal
            s = np.where(scratch, 0.56, s)
            g = np.where(scratch, IRON / 255.0 if kind != 'galvanised' else TINTED_METAL / 255.0, g)
        else:                                                 # scuffed: rougher, and wood soaks through
            s = np.where(scratch, s * 0.6, s)
            p = np.where(scratch, np.maximum(p, 36 / 255.0), p)
    blood = marks.get('blood')
    if blood is not None:
        tack = (f.noise(2.2, 409, 2) - 0.5) * 0.24          # some patches still tacky, some dried flat
        s = np.where(blood, BLOOD[0] + tack, s)
        g = np.where(blood, BLOOD[1] / 255.0, g)
        p = np.where(blood, 0.0, p)
    H, W = f.shape
    sp = np.zeros((H, W, 4))
    sp[..., 0] = np.clip(s, 0, 1)
    sp[..., 1] = g
    sp[..., 2] = np.clip(p, 0, 64 / 255.0)
    sp[..., 3] = 1.0
    height = None
    if kind not in PLAIN and kind not in ('chrome', 'brass'):
        height = rustiness(base) * (f.noise(9.0, 411, 2) - 0.5) * 0.9     # pitted where it's rusted
    if blood is not None:
        bl = blood * 0.12                                     # blood stands a little proud
        height = bl if height is None else height + bl
    return sp, normal(f, final, lu, lv, px, height=height)
