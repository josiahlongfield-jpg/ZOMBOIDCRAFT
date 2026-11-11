"""Our own toon rounds (the shared jar's look, for whoever hasn't got Arsenal's): each calibre turned from its real
measurements (mm). A profile runs base to tip as (z, radius, material) rings; the mesh is that profile turned 24
ways round, painted by material along its length."""
import numpy as np, math

BRASS, NICKEL, STEEL = '#b88a3c', '#b7b7ae', '#7b7d74'
COPPER, LEAD, POLY = '#a85a32', '#8a8c8e', '#a8262a'

def lerp(a, b, n):
    return [a + (b - a) * i / n for i in range(1, n + 1)]

def ogive(z0, length, r0, r_tip, flat=0.0, n=6):
    """A round nose (or a pointed one, r_tip near 0) from radius r0 at z0, flat-nosed if flat > 0."""
    out = []
    for i in range(1, n + 1):
        t = i / n
        r = r_tip + (r0 - r_tip) * math.cos(t * math.pi / 2) ** 0.9
        out.append((z0 + length * math.sin(t * math.pi / 2) ** 1.2, max(r, flat)))
    return out

def cartridge(case, oal, body, rim, neck=None, shoulder=None, bullet='round', bcol=COPPER, tip=None,
              case_col=BRASS, rimmed=False, flat=0.0):
    """case/oal lengths, body/rim/neck diameters (mm). shoulder: where the bottleneck starts (from the base)."""
    rb, rr = body / 2, rim / 2
    rn = (neck or body) / 2
    P = [(0, 0, 'primer'), (0, rr * 0.45, 'primer'), (0, rr, case_col)]
    if rimmed:
        P += [(rr * 0.12, rr, case_col), (rr * 0.12, rb, case_col)]
    else:
        P += [(0.9, rr, case_col), (1.2, rb * 0.82, case_col), (1.9, rb * 0.82, case_col), (2.3, rb, case_col)]
    if shoulder:
        P += [(shoulder, rb * 0.97, case_col), (shoulder + (rb - rn) * 1.2, rn, case_col)]
    P += [(case, rn, case_col)]
    bl = oal - case
    if bullet == 'round':
        P += [(case + 0.01, rn * 0.98, bcol)] + [(z, r, bcol) for z, r in ogive(case + bl * 0.25, bl * 0.75, rn * 0.98, 0.0, flat)]
    elif bullet == 'spitzer':
        P += [(case + 0.01, rn * 0.98, bcol), (case + bl * 0.2, rn * 0.98, bcol)]
        prof = ogive(case + bl * 0.2, bl * 0.8, rn * 0.98, 0.15, 0, 7)
        tipz = case + bl * 0.8
        P += [(z, r, tip if (tip and z > tipz) else bcol) for z, r in prof]
    elif bullet == 'swc':       # semi-wadcutter: a step shoulder then a flat-topped cone
        P += [(case + 0.01, rn * 0.99, bcol), (case + bl * 0.25, rn * 0.99, bcol), (case + bl * 0.3, rn * 0.78, bcol),
              (oal - 0.2, rn * 0.62, bcol), (oal, rn * 0.55, bcol), (oal, 0, bcol)]
    elif bullet == 'hollow':    # hollow point: soft rim round a dark cup
        P += [(case + 0.01, rn * 0.98, bcol)] + [(z, r, bcol) for z, r in ogive(case + bl * 0.2, bl * 0.8, rn * 0.98, rn * 0.45, rn * 0.45, 5)]
        P += [(oal, rn * 0.3, 'cup'), (oal - bl * 0.25, 0, 'cup')]
    return P

def shell(length, dia, head, hull=POLY):
    r = dia / 2
    return [(0, 0, 'primer'), (0, r * 0.25, 'primer'), (0, r * 1.04, BRASS), (0.9, r * 1.04, BRASS), (0.9, r, BRASS),
            (head, r, BRASS), (head + 0.01, r * 0.99, hull), (length - 1.5, r * 0.99, hull), (length, r * 0.8, hull),
            (length, r * 0.25, 'crimp'), (length - 1.2, 0, 'crimp')]

def grenade40():
    r = 20.0
    return [(0, 0, 'primer'), (0, 6, 'primer'), (0, 22, '#8e9198'), (3, 22, '#8e9198'), (3, r, '#8e9198'), (34, r, '#8e9198'),
            (34.01, 19.5, '#c9a33a'), (40, 19.5, '#c9a33a'), (40.01, 19.6, '#4b5530'), (60, 19.6, '#4b5530')] + \
           [(z, r2, '#4b5530') for z, r2 in ogive(60, 40, 19.6, 3.5, 3.5, 7)] + [(100, 3.5, '#c9a33a'), (101.5, 0, '#c9a33a')]

COL = {'primer': '#c9a03e', 'cup': '#2a201a', 'crimp': '#7e1b1e'}

def lathe(P, sides=24):
    """Triangles (n,3,3) and a colour per triangle, the profile's z along +x (tip toward +x)."""
    tris, cols = [], []
    for (z0, r0, c0), (z1, r1, c1) in zip(P, P[1:]):
        col = COL.get(c1, c1) if c1 == c0 or c1 in COL else (COL.get(c1, c1))
        for k in range(sides):
            a0, a1 = 2 * math.pi * k / sides, 2 * math.pi * (k + 1) / sides
            p = lambda z, r, a: (z, r * math.cos(a), r * math.sin(a))
            A, B, C, D = p(z0, r0, a0), p(z0, r0, a1), p(z1, r1, a1), p(z1, r1, a0)
            if r0 > 1e-6: tris.append((A, C, B)); cols.append(col)
            if r1 > 1e-6: tris.append((A, D, C)); cols.append(col)
    return np.array(tris, float), cols

ROUNDS = {
    '9mm': cartridge(19.15, 29.7, 9.93, 9.96, bullet='round'),
    '.380 ACP': cartridge(17.3, 25.0, 9.5, 9.5, bullet='round'),
    '.38 Special': cartridge(29.3, 39.4, 9.6, 11.2, bullet='swc', bcol=LEAD, rimmed=True),
    '.44 Magnum': cartridge(32.6, 40.9, 11.6, 13.1, bullet='hollow', bcol=COPPER, rimmed=True, case_col=NICKEL),
    '.45 ACP': cartridge(22.8, 32.4, 12.0, 12.0, bullet='round'),
    '5.7x28mm': cartridge(28.9, 40.5, 7.95, 7.8, neck=6.3, shoulder=21.5, bullet='spitzer', bcol=COPPER, tip='#2d3e8a'),
    '.45 Colt': cartridge(32.6, 41.0, 12.2, 12.9, bullet='round', bcol=LEAD, rimmed=True, flat=3.0),
    '.500 S&W': cartridge(41.3, 57.0, 13.4, 14.2, bullet='hollow', bcol=COPPER, rimmed=True, case_col=NICKEL),
    '.22 LR': cartridge(15.6, 25.4, 5.7, 6.9, bullet='round', bcol=LEAD, rimmed=True, case_col=COPPER),
    '5.56mm': cartridge(44.7, 57.4, 9.6, 9.6, neck=6.4, shoulder=36.5, bullet='spitzer', tip='#3d6b2c'),
    '.308': cartridge(51.2, 71.1, 11.9, 12.0, neck=8.7, shoulder=40.0, bullet='spitzer'),
    '.30-30': cartridge(51.8, 64.8, 10.7, 12.9, neck=8.4, shoulder=38.0, bullet='round', rimmed=True, flat=2.2, bcol=LEAD),
    '.50 BMG': cartridge(99.0, 138.0, 20.4, 20.4, neck=14.2, shoulder=77.0, bullet='spitzer', tip='#262626'),
    '12 gauge': shell(70.0, 20.5, 12.0),
    '40mm grenade': grenade40(),
}
