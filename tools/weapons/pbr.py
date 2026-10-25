"""LabPBR material maps for the weapon textures: _s (specular) and _n (normal), painted alongside the colour.

Shader packs that read LabPBR (Photon among them) use these; vanilla ignores them. The format:
  _s: R perceptual smoothness, G reflectance (0-229 = F0 of a non-metal, 230-237 = a named metal, 255 =
      metal tinted by the colour), B porosity (0 here), A emission (0-254, 255 = none).
  _n: R and G the normal's tilt (DirectX: G points down the image), B ambient occlusion (255 = none),
      A height (255 = top).

Each material gets a few numbers. Wear decides where the finish has gone and bare metal shows: those
pixels are picked out by how much lighter than the finish they were painted.
"""
import numpy as np

IRON, GOLD, ALUMINIUM, CHROME, COPPER = 230, 231, 232, 233, 234
TINTED_METAL = 255
NONMETAL = 10           # about 4% reflectance, plastics, paint, rubber, glass

# smoothness, reflectance byte, and optionally (lightness over which it's bare metal, metal, its smoothness)
MATERIALS = {
    'anod': (0.46, NONMETAL, (0.36, ALUMINIUM, 0.72)),
    'rail': (0.44, NONMETAL, (0.36, ALUMINIUM, 0.72)),
    'guard': (0.44, NONMETAL, (0.36, ALUMINIUM, 0.72)),
    'park': (0.24, NONMETAL, (0.40, IRON, 0.58)),
    'hider': (0.22, NONMETAL, (0.40, IRON, 0.55)),
    'steel': (0.62, IRON, None),
    'bright': (0.74, IRON, None),
    'port': (0.30, NONMETAL, (0.40, IRON, 0.6)),
    'poly': (0.34, NONMETAL, None),
    'grip': (0.26, NONMETAL, None),
    'gripper': (0.26, NONMETAL, None),
    'scope': (0.50, NONMETAL, (0.36, ALUMINIUM, 0.72)),
    'knurl': (0.40, NONMETAL, (0.36, ALUMINIUM, 0.72)),
    'bezel': (0.46, NONMETAL, (0.36, ALUMINIUM, 0.72)),
    'cap': (0.22, NONMETAL, (0.40, IRON, 0.55)),
    'lamp': (0.92, 18, None),
    'lens_red': (0.92, 18, None),
    'tan': (0.30, NONMETAL, None),
    'mag': (0.30, NONMETAL, None),
    'rubber': (0.12, 8, None),
    'bore': (0.08, 6, None),
    'housing': (0.42, NONMETAL, None),
    'can': (0.20, NONMETAL, (0.42, IRON, 0.5)),
    'lens': (0.96, 18, None),
    'glass': (0.96, 18, None),
    'dot': (0.5, NONMETAL, None),
    'flash': (0.0, NONMETAL, None),
    'brass': (0.62, TINTED_METAL, None),
}
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
        at, metal, msmooth = bare
        m = lum(rgb) > at
        s = np.where(m, msmooth, s)
        g = np.where(m, metal / 255.0, g)
    out[..., 0] = np.clip(s, 0, 1)
    out[..., 1] = g
    out[..., 2] = 0
    out[..., 3] = EMISSIVE.get(mat, 255) / 255.0
    return out


def normal(f, rgb, lu, lv, px, bevel=0.09, bump=0.5, tilt=0.55):
    """Edge bevels (so blocky parts catch the light like machined edges) plus a little relief from the
    painted detail. lu, lv: the face's size in model units; px: texture pixels per unit."""
    u, v = f.uv[..., 0], f.uv[..., 1]
    b = max(1.0 / px, min(bevel, 0.3 * min(lu, lv)))

    def t(d):
        return tilt * np.clip(1 - d / b, 0, 1)

    nx = -t(u) + t(lu - u)
    ny = -t(v) + t(lv - v)
    L = lum(rgb)
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
