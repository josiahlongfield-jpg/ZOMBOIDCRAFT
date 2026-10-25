"""Gun finishes worth looking at twice: oiled walnut from plain field grade to exhibition figure, cut checkering,
and the metal finishes a working gun or a fine one wears (hot blue, colour case hardening, a silver coin finish
with scroll engraving, bright nickel).

Wood grain is worked out from where each pixel is in the model, not from the box it's on, so a stock built from
dozens of slices reads as one piece of walnut: straight grain running down its length, the growth rings cut
through as stripes on the sides and arches on top, pores like fine dashes, and in figured wood the dark streaks
and the shimmer across the grain that make one stock worth more than another.
"""
import numpy as np

import model as M
import paint as P
import pbr


def _line(d, width):
    """Coverage of a line `width` model units wide at distance d from its centre, smoothed over one texture
    pixel (at the face's own density) so fine cuts come out as clean lines instead of speckle."""
    px = 1.0 / M.PX
    return np.clip((width / 2 - d) / px + 0.5, 0, 1)

# ---------------------------------------------------------------- walnut

# (light, dark, figure, curl, ring frequency): how the grades differ
GRADES = {
    # a working gun's walnut: even brown, straight grain, hardly any figure
    'field': ('#7c4e2c', '#45291a', 0.15, 0.0, 2.2),
    # a better blank: warmer, darker streaks through it
    'figured': ('#835530', '#2f1b0f', 0.65, 0.25, 2.6),
    # exhibition walnut: marble-cake figure and fiddleback shimmer across the grain
    'exhibition': ('#8d5c34', '#22120a', 1.0, 0.6, 3.0),
    # a beech or birch stock stained to pass for walnut: pale, plain, a little orange
    'hardwood': ('#9c6a3b', '#6b4426', 0.05, 0.0, 1.8),
    # rosewood or goncalo alves: a handgun's target grips, near black streaks in red-brown
    'rosewood': ('#7a3a22', '#2a120a', 0.85, 0.15, 3.4),
}


def _axes(grain):
    return {'z': (2, 0, 1), 'y': (1, 0, 2), 'x': (0, 1, 2)}[grain]


def walnut(f, grade='field', grain='z', tilt=0.0):
    """Oiled walnut of a grade, its grain along `grain` (a model axis), leaned `tilt` (per unit across) so a
    sloping grip's grain follows its slope."""
    light, dark, figure, curl, ring = GRADES[grade]
    ua, va, wa = _axes(grain)
    u, v, w = f.P[..., ua], f.P[..., va], f.P[..., wa]
    u = u + tilt * v
    seed = 501
    # the growth rings: centred on a pith line well off below the piece, warped, drifting slowly along it
    warp = P.fbm(u * 0.08, v * 0.35, w * 0.35, seed, 3) * 2.4
    r = np.sqrt((v + 9.0) ** 2 + (w - 3.0) ** 2)
    rings = np.sin((r * ring + warp + u * 0.015) * np.pi * 2) * 0.5 + 0.5
    late = rings ** 4                                   # the dark latewood of each ring, thinner than the light
    # pores: fine dashes along the grain
    pore = P.fbm(u * 0.9, v * 14.0, w * 14.0, seed + 3, 2)
    pores = np.clip((pore - 0.62) * 6, 0, 1)
    # figure: wandering dark streaks along the grain
    fig = P.fbm(u * 0.12, v * 0.9, w * 0.9, seed + 7, 4)
    streak = np.clip(1 - np.abs(np.sin(fig * 9.0)) * 3.2, 0, 1) * figure
    t = np.clip(late * 0.45 + streak * 0.75 + (P.fbm(u * 0.05, v * 0.25, w * 0.25, seed + 11, 2) - 0.5) * 0.5 + 0.18, 0, 1)
    c = P.mix(P.col(light), P.col(dark), t)
    c = c * (1 - 0.22 * pores)[..., None]
    if curl > 0:                                        # fiddleback: bands across the grain that catch the light
        drift = P.fbm(u * 0.25, v * 0.5, w * 0.5, seed + 13, 3) * 9.0
        band = np.sin(u * (6.0 + 2.5 * P.fbm(u * 0.1, v * 0.2, w * 0.2, seed + 14, 2)) + drift) * 0.5 + 0.5
        patch = np.clip((P.fbm(u * 0.15, v * 0.4, w * 0.4, seed + 15, 2) - 0.35) * 3, 0, 1)
        c = c * (1 + curl * 0.20 * (band - 0.5) * patch)[..., None]
    if abs(f.n[ua]) > 0.5 and ua not in f.box.get('seam', ()):    # end grain: the rings plain to see, darker
        c = P.mix(P.col(light), P.col(dark), np.clip(rings * 0.7 + 0.25, 0, 1)) * 0.78
    # oil finish: a slow sheen variation
    c = c * (1 + (P.fbm(u * 0.4, v * 0.4, w * 0.4, seed + 17, 2) - 0.5) * 0.08)[..., None]
    return c


def checkering(f, base, pitch=0.21, angle=32.0, border=0.06):
    """Cut checkering on a panel: two sets of grooves crossing at `angle` either side of the panel's long side,
    leaving rows of little pyramids lit on their upper faces; a single cut border round the panel. base is the
    wood underneath. pitch in model units: real 20-lines-an-inch checkering is finer than a texel, so it's drawn
    at the coarsest size that still reads as checkering."""
    a = np.radians(angle)
    uu, vv = f.uv[..., 0], f.uv[..., 1]
    if uu.max() < vv.max():                             # cut along the panel's long side
        uu, vv = vv, uu
    d1 = (uu * np.cos(a) + vv * np.sin(a)) / pitch
    d2 = (uu * np.cos(a) - vv * np.sin(a)) / pitch
    f1, f2 = d1 % 1.0, d2 % 1.0
    a1, a2 = 1 - np.abs(2 * f1 - 1), 1 - np.abs(2 * f2 - 1)    # 0 in a groove, 1 at a pyramid's point
    # which face of its pyramid each pixel is on, and whether that face looks up (v runs down the face)
    up1 = np.where(f1 < 0.5, -1.0, 1.0) * np.sin(a)
    up2 = np.where(f2 < 0.5, 1.0, -1.0) * np.sin(a)
    light = np.where(a1 < a2, up1, up2)
    h = np.minimum(a1, a2)
    shade = 0.92 + 0.22 * light - 0.42 * np.clip(0.35 - h, 0, 0.35) / 0.35
    c = base * shade[..., None]
    edge = _line(np.abs(f.edge - border - 0.03), 0.045)
    c = P.mix(c, base * 0.45, edge)
    return np.where((f.edge < border)[..., None], base, c)


# ---------------------------------------------------------------- metal

def blued(f, base='#1b2028', hi='#3b4552', wear_to='#7c828a', polish=0.5):
    """Hot salt blue on polished steel: blue-black with long soft highlights along the part, worn to bright
    steel in fine broken lines on the most handled edges."""
    streak = f.noise(2.2, 601, 2, P.stretch_for(f, 0.05))
    blot = f.noise(0.6, 602, 2)
    t = np.clip((streak - 0.5) * polish * 1.6 + (blot - 0.5) * 0.5 + 0.3, 0, 1)
    c = P.mix(P.col(base), P.col(hi), t)
    n = f.noise(3.5, 603, 3)
    worn = (f.edge < 0.014 + 0.02 * f.wear) & (n > 0.62 - 0.12 * f.wear)
    return np.where(worn[..., None], P.mix(P.col(wear_to), c, 0.35), c)


def case_hardened(f):
    """Colour case hardening: swirls of blue, purple, amber and straw over frosted grey steel, as the bone
    charcoal left it."""
    s1 = P.fbm(f.X * 0.9, f.Y * 0.9, f.Z * 0.9, 611, 4)
    s2 = P.fbm(f.X * 1.7 + 4, f.Y * 1.7, f.Z * 1.7, 612, 3)
    swirl = np.sin(s1 * 11.0 + s2 * 4.0) * 0.5 + 0.5
    blue, purple, amber, straw, grey = (P.col(h) for h in ('#2d3f73', '#5b3d72', '#9a6a35', '#c8a75a', '#7b7f84'))
    c = P.mix(grey, blue, np.clip(swirl * 1.6 - 0.2, 0, 1))
    c = P.mix(c, purple, np.clip((s2 - 0.55) * 4, 0, 1) * 0.8)
    c = P.mix(c, amber, np.clip((s1 - 0.6) * 5, 0, 1) * 0.75)
    c = P.mix(c, straw, np.clip((swirl - 0.85) * 6, 0, 1) * 0.6)
    frost = (P.white(f.shape, f.rng) - 0.5) * 0.08
    c = c * (1 + frost + (f.noise(5.0, 613, 2) - 0.5) * 0.1)[..., None]
    n = f.noise(3.5, 614, 3)
    worn = (f.edge < 0.016 + 0.02 * f.wear) & (n > 0.6 - 0.12 * f.wear)
    return np.where(worn[..., None], P.mix(P.col('#9a9fa4'), c, 0.4), c)


def coin(f, engraved=True, cell=0.95):
    """A silver coin finish (bright steel, finely brushed) with English scroll engraving: scrolls a cell
    across, each one a cut spiral shaded on its inner side, leaves sprouting off where it ends, and a cut
    border round each face."""
    c = P.steel(f, 0.74, tint='#cfd2d4', spread=0.08)
    if not engraved:
        return c
    dark = P.col('#2a2c2e')
    uu, vv = f.uv[..., 0] / cell, f.uv[..., 1] / cell
    ink = np.zeros(f.shape)
    tone = np.zeros(f.shape)
    for shift in (0.0, 0.5):                            # two offset grids, so scrolls interlock
        su, sv = uu + shift, vv + shift * 0.7
        cu, cv = np.floor(su), np.floor(sv)
        h = P._hash(cu.astype(np.int64), cv.astype(np.int64), np.full(cu.shape, int(shift * 2), np.int64), 615)
        h2 = P._hash(cu.astype(np.int64), cv.astype(np.int64), np.full(cu.shape, 7, np.int64), 616)
        size = 0.30 + 0.12 * h2                         # each scroll's reach, in cells
        du, dv = su - cu - 0.5 - (h - 0.5) * 0.3, sv - cv - 0.5 + (h2 - 0.5) * 0.3
        r = np.sqrt(du * du + dv * dv) + 1e-6
        turn = np.where(h > 0.5, 1.0, -1.0)
        th = np.arctan2(dv, du) * turn + h * 6.283
        phase = th * 0.5 - r / size * 5.0
        grad = np.sqrt((5.0 / size) ** 2 + (0.5 / r) ** 2)
        dist = np.abs(np.sin(phase)) / grad * cell      # model units from the spiral's cut
        inside = r < size
        ink = np.maximum(ink, _line(dist, 0.055) * inside)
        tone = np.maximum(tone, (np.sin(phase) > 0) * (np.sin(phase) < 0.55) * inside * (r > 0.04))
        # leaves: almond shapes curling off the end of the scroll
        lr = r - size * 1.05
        leaf = (np.abs(lr) < 0.045) & (np.cos(th * 3.0 + h2 * 5) > 0.6)
        ink = np.maximum(ink, leaf * 0.6)
    c = c * (1 - 0.22 * tone)[..., None]
    c = P.mix(c, dark, ink * 0.8)
    border = _line(np.abs(f.edge - 0.075), 0.05)
    return P.mix(c, dark, border * 0.8)


def nickel(f):
    """Bright nickel plate: warm silver, soft reflections."""
    a, b = f.across()
    t = np.sin((a + b) * 2.0 + f.noise(0.35, 621, 2) * 3.5) * 0.5 + 0.5
    c = P.mix(P.col('#8e918f'), P.col('#e3e3dc'), t ** 1.3)
    return c * (1 + (f.noise(5.0, 622, 2, P.stretch_for(f, 0.06)) - 0.5) * 0.06)[..., None]


def gold(f):
    return P.steel(f, 0.72, tint='#d9b25a', spread=0.12)


# ---------------------------------------------------------------- the LabPBR numbers to go with them

WOOD_SPEC = {   # oil satin to hand-rubbed gloss; the checkering rougher and soaking in rain
    'satin': (0.40, pbr.NONMETAL, None),
    'gloss': (0.62, pbr.NONMETAL, None),
    'check': (0.24, pbr.NONMETAL, None),
}
METAL_SPEC = {
    'blued': (0.56, pbr.TINTED_METAL, None),
    'case': (0.50, pbr.TINTED_METAL, None),
    'coin': (0.66, pbr.TINTED_METAL, None),
    'nickel': (0.78, pbr.TINTED_METAL, None),
    'gold': (0.70, pbr.TINTED_METAL, None),
}
