"""Cortana 360 capsule M v1: four-sided Pepper's-ghost pyramid lit by four 15.6 inch portable monitors in a pinwheel,
her image about 31 cm (12 in) tall on the centre axis. mm, x width, y depth (front y=0), z up. 2020 aluminium skeleton + printed shell."""
import sys, math, json, os
sys.path.insert(0, os.path.dirname(__file__))
from helpers import *

W = D = 920.0
C = W / 2
BASE_H = 160.0
S2 = 400.0
ZB = BASE_H + 5
ZS = ZB + S2
V0 = 60.0
ZTOP_GLASS = ZS - V0
CR0 = ZS - 3
GT = 3.0
MON = (357.0, 223.0, 10.0)   # 15.6 inch portable monitor body (long, short, thick)
ACT = (344.0, 194.0)
SOFF = 62.0
FIG_H = 310.0
FEET = ZS - 392.0
P = 36.0
COL = 70.0
E = 20.0
ZR = ZS + MON[2] + 4          # crown ring / front-back beams bottom
ZR2 = ZR + E                  # side beams bottom
TOP = ZR2 + E + 3
CZ0 = CR0 - 13
CAP = 56.0
parts, meta, bought = {}, {}, {}
SET = {}
def add(key, name, man, material, colour, group, note, explode, print_note, pieces=None):
    parts[key] = man
    meta[key] = dict(name=name, material=material, colour=colour, group=group, note=note, explode=explode, print=print_note)
    if pieces:
        SET[key] = pieces
def buy(key, name, man, colour, note, explode):
    bought[key] = man
    meta[key] = dict(name=name, material='Bought part', colour=colour, group='Bought parts (shown for fit)', note=note, explode=explode, print=None)
def cbox(x0, y0, z0, x1, y1, z1, c):
    return prism(chamfer_rect(x0, y0, x1, y1, c), z0, z1 - z0)
def rot(man, k):
    return man.translate([-C, -C, 0]).rotate([0, 0, 90 * k]).translate([C, C, 0])
def rotv(v, k):
    x, y, z = v
    for _ in range(k % 4):
        x, y = -y, x
    return [x, y, z]
SIDE = ['front', 'right', 'back', 'left']
CORNER = {0: 'front left', 1: 'front right', 2: 'back right', 3: 'back left'}

# ---------------- glass faces ----------------
def face(side, thick=GT, grow=0.0):
    h = ZTOP_GLASS - ZB
    a = S2 + 3 * grow
    top = (S2 - h) + 2 * grow
    pts2 = [(-a, -S2 - grow, ZB - grow), (a, -S2 - grow, ZB - grow), (top, -(S2 - h) + grow, ZTOP_GLASS + grow), (-top, -(S2 - h) + grow, ZTOP_GLASS + grow)]
    n = (0, -1 / math.sqrt(2), 1 / math.sqrt(2))
    pts = [(x + n[0] * s * thick, y + n[1] * s * thick, z + n[2] * s * thick) for (x, y, z) in pts2 for s in (-0.5, 0.5)]
    return M.hull_points(pts).rotate([0, 0, 90 * side]).translate([C, C, 0])
glass = [face(i) for i in range(4)]
glass_cut = union([face(i, GT + 0.8, 4) for i in range(4)])


# ---------------- skeleton (bought, 2020 aluminium) ----------------
posts = [box(px, py, 0, px + E, py + E, ZR + E) for (px, py) in [(10, 10), (W - 30, 10), (W - 30, D - 30), (10, D - 30)]]
rails = []
for z0 in (0, BASE_H - 12 - E, ZR):
    rails += [box(30, 10, z0, W - 30, 30, z0 + E), box(30, D - 30, z0, W - 30, D - 10, z0 + E),
              box(10, 30, z0, 30, D - 30, z0 + E), box(W - 30, 30, z0, W - 10, D - 30, z0 + E)]
cross = [box(C - 130, 30, BASE_H - 12 - E, C - 110, D - 30, BASE_H - 12), box(C + 110, 30, BASE_H - 12 - E, C + 130, D - 30, BASE_H - 12)]
VC = (SOFF + 1 + MON[0] / 2)
UC = MON[1] / 2 - SOFF
beams = [box(30, C - VC - 10, ZR, W - 30, C - VC + 10, ZR + E), box(30, C + VC - 10, ZR, W - 30, C + VC + 10, ZR + E),
         box(C + VC - 10, 10, ZR2, C + VC + 10, D - 10, ZR2 + E), box(C - VC - 10, 10, ZR2, C - VC + 10, D - 10, ZR2 + E)]
skeleton = union(posts + rails + cross + beams)
def grow_box_list(lst, g):
    return union([box(*(lambda b: (b[0] - g, b[1] - g, b[2] - g, b[3] + g, b[4] + g, b[5] + g))(m.bounding_box())) for m in lst])
skel_cut = grow_box_list(posts + rails + cross + beams, 0.5)

# ---------------- base corner columns ----------------
def column(k):
    c = cbox(0, 0, 0, COL, COL, BASE_H, 10)
    c = c - M.extrude(CS([[(-1, BASE_H - 9), (9, BASE_H + 1), (-1, BASE_H + 1)]]), 200).rotate([90, 0, 90]).translate([-1, 0, 0])
    c = c - cbox(3, 3, -1, COL - 3, COL - 3, BASE_H - 3, 8)
    for zz in (50, 100):
        c = c - (box(-1, -1, zz, COL + 1, 1.2, zz + 1.6) + box(-1, -1, zz, 1.2, COL + 1, zz + 1.6))
    c = c - box(14, -1, 120, 17, 2, 140) - box(-1, 14, 120, 2, 17, 140)
    c = c + (box(COL - 10, 3, BASE_H - 3, COL, COL, BASE_H) + box(3, COL - 10, BASE_H - 3, COL, COL, BASE_H))
    return rot(c, k) - skel_cut
for k in range(4):
    add(f'col{k}', f'Base corner column, {CORNER[k]}', column(k), 'Matte black PLA', '#1c2028', 'Base',
        'Chamfered corner of the pedestal, wrapped round the aluminium corner post, with panel lines and a glow mark.', rotv([-40, -40, 0], k), 'Print upright, no supports.')

# ---------------- base fascia ----------------
for k in range(4):
    g = box(C - 80, 3, 30, C + 80, 7, 110)
    holes = union([cyly(3.0, 10, C + dx, 2, 70 + dz, seg=6) for dx in range(-70, 71, 8) for dz in range(-32, 33, 7) if (dx / 70.0) ** 2 + (dz / 32.0) ** 2 <= 1.0])
    add(f'grille{k}', f'Vent and speaker grille, {SIDE[k]}', rot(g - holes, k), 'Matte black PLA', '#353b46', 'Base',
        'Hex grille in the middle of each side. Sound from the down-firing speaker and warm air come out here.', rotv([0, -40, 0], k), 'Print flat (face down).')
    bars = [box(C - 300 + i * 200 + 0.5, 3, 125, C - 300 + (i + 1) * 200 - 0.5, 7, 133) - box(C - 300 + i * 200 + 3, 4.5, 126.5, C - 300 + (i + 1) * 200 - 3, 8, 131.5) for i in range(3)]
    add(f'bars{k}', f'Light bar diffusers, {SIDE[k]} (3 pieces)', rot(union(bars), k), 'Natural / translucent PETG', '#5cc8ff', 'Base',
        'A glowing strip across the side of the pedestal, lit by an LED strip behind it and synced to her mood.', rotv([0, -30, 0], k), 'Print flat, three pieces.', pieces=[[199, 4, 8]] * 3)

# ---------------- glass shoes (3 per side) ----------------
for k in range(4):
    sh = []
    for dx in (-250, 0, 250):
        sh.append(cbox(C + dx - 30, C - S2 - 16, BASE_H, C + dx + 30, C - S2 + 22, BASE_H + 14, 4) - box(C + dx - 26, C - S2 - 12, BASE_H - 1, C + dx + 26, C - S2 - 6, BASE_H + 10))
    add(f'shoes{k}', f'Glass shoes, {SIDE[k]} (3 pieces)', rot(union(sh), k) - glass_cut, 'Black PETG', '#30394a', 'Optics',
        'Small angled blocks screwed to the deck. The bottom edge of the panel sits in their slots at exactly 45 degrees.', rotv([0, -30, 15], k), 'Print flat, three at once.', pieces=[[60, 38, 14]] * 3)

# ---------------- hip rails (4 corners, 3 pieces each) ----------------
h = ZTOP_GLASS - ZB
for idx, (sx, sy) in enumerate([(-1, -1), (1, -1), (1, 1), (-1, 1)]):
    a = (C + sx * S2, C + sy * S2, ZB)
    b = (C + sx * (S2 - h), C + sy * (S2 - h), ZTOP_GLASS)
    pts = []
    for (px, py, pz) in (a, b):
        for dx in (-8, 8):
            for dy in (-8, 8):
                pts.append((px + sx * 8 + dx * 0.7, py + sy * 8 + dy * 0.7, pz))
                pts.append((px + sx * 8 + dx * 0.7, py + sy * 8 + dy * 0.7, pz + 9))
    rail = M.hull_points(pts) - glass_cut
    rail = rail - box(-10, -10, -10, W + 10, D + 10, BASE_H) - box(-10, -10, ZTOP_GLASS - 10, W + 10, D + 10, 2000)
    zs = [BASE_H + i * (ZTOP_GLASS - 10 - BASE_H) / 3 for i in range(4)]
    for s in range(3):
        add(f'hip{idx}_{s}', f'Corner rail, {CORNER[idx]}, piece {s + 1} of 3', rail ^ box(-10, -10, zs[s] + (0.3 if s else 0), W + 10, D + 10, zs[s + 1]), 'Matte black PLA', '#2b313c', 'Optics',
            'Runs up one corner of the pyramid and grips the edges of two panels, so the seam between two views is as thin as possible. Pinned end to end.',
            [sx * 40, sy * 40, s * 12], 'Print lying on its side, no supports.')

# ---------------- apex hub ----------------
hub = cbox(C - 66, C - 66, ZTOP_GLASS - 10, C + 66, C + 66, CR0, 16) - glass_cut
hub = hub - cbox(C - 61, C - 61, ZTOP_GLASS + 3, C + 61, C + 61, CR0 - 3, 14) - cbox(C - 50, C - 50, ZTOP_GLASS - 11, C + 50, C + 50, ZTOP_GLASS + 4, 12)
for k in range(4):
    hub = hub - rot(box(C - 45, C - 67, CR0 - 22, C + 45, C - 65, CR0 - 20), k)
add('hub', 'Apex hub', hub, 'Matte black PLA', '#2b313c', 'Optics',
    'Hangs from the screen mask in the gap at the centre of the four screens and locks the four panel tips together above her head.', [0, 0, 70], 'Print upright (top on the bed).')

# ---------------- holo pad ----------------
pad_base = cyl(72, 4, C, C, BASE_H) - cyl(5, 6, C, C + 50, BASE_H - 1)
pad_diff = cyl(60, 2, C, C, BASE_H + 4)
cover = cyl(72, 3, C, C, BASE_H + 6) - cyl(34, 5, C, C, BASE_H + 5)
cover = cover + (cyl(72, 2, C, C, BASE_H + 4) - cyl(61, 3, C, C, BASE_H + 3.5))
for i in range(16):
    cover = cover - M.extrude(CS([[(38, -3), (56, -6), (56, 6), (38, 3)]]), 5).translate([0, 0, BASE_H + 5]).rotate([0, 0, i * 22.5 + 11.25]).translate([C, C, 0])
add('pad_base', 'Holo pad base', pad_base, 'Matte black PLA', '#252a33', 'Holo pad', 'Holds the LED ring on the deck, right under the point where all four reflections meet.', [0, 0, 20], 'Print flat.')
add('pad_diff', 'Holo pad diffuser', pad_diff, 'Natural / translucent PETG', '#5cc8ff', 'Holo pad', 'Glows through the cover windows, pulsing when she speaks and shifting colour with her mood.', [0, 0, 40], 'Print flat.')
add('pad_cover', 'Holo pad cover', cover, 'Matte black PLA', '#15181e', 'Holo pad', 'The emitter she stands on. Her feet land here from every side.', [0, 0, 60], 'Print flat.')

# ---------------- pillar covers (2 sections) ----------------
PZ0, PZ1 = BASE_H, CZ0
def pillar(k):
    p = cbox(0, 0, PZ0, P, P, PZ1, 6)
    p = p - box(28, 28, PZ0 - 1, P + 1, P + 1, PZ1 + 1)
    for zz in [PZ0 + 60 + 90 * i for i in range(4)]:
        p = p - (cbox(-1, -1, zz, P + 1, P + 1, zz + 1.6, 6) - cbox(1.2, 1.2, zz - 1, P - 1.2, P - 1.2, zz + 3, 5))
    p = p - box(16, -1, PZ0 + 20, 19, 2, PZ1 - 20) - box(-1, 16, PZ0 + 20, 2, 19, PZ1 - 20)
    p = p - (cbox(3.5, 3.5, PZ0 - 1, P - 3.5, P - 3.5, PZ1 + 1, 4) - box(25, 25, PZ0 - 2, P + 1, P + 1, PZ1 + 2))
    return rot(p, k) - skel_cut
for k in range(4):
    p = pillar(k)
    zm = (PZ0 + PZ1) / 2
    for s, (z0, z1) in enumerate(((PZ0, zm), (zm, PZ1))):
        add(f'pil{k}_{s}', f'Pillar cover, {CORNER[k]}, {"lower" if s == 0 else "upper"}', p ^ box(-10, -10, z0 + (0.3 if s else 0), W + 10, D + 10, z1), 'Matte black PLA', '#1c2028', 'Frame',
            'Clips round the aluminium corner post, with glowing edge lines up two faces.', rotv([-50, -50, s * 20], k), 'Print upright, no supports.')

# ---------------- crown ----------------
NSEG = 5
SEGL = (W - 2 * CAP) / NSEG
def crown_side(k):
    f = box(CAP, 0, CZ0, W - CAP, 3, TOP)
    f = f + box(CAP, 0, CZ0 + 6, W - CAP, 45, CZ0 + 10)
    f = f + box(CAP, 3, TOP - 6, W - CAP, 26, TOP - 3)
    f = f + box(CAP, 3, ZR, W - CAP, 9.5, ZR + E)
    f = f - M.extrude(CS([[(-1, TOP - 9), (9, TOP + 1), (-1, TOP + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
    f = f - box(CAP + 14, -1, CZ0 + 18, W - CAP - 14, 1.0, CZ0 + 20)
    f = f - box(C - 8, -1, ZR + E + 3, C + 8, 4, ZR + E + 13)
    for dx in (-60, 60):
        f = f - cyly(1.4, 5, C + dx, -1, ZR + E + 8, seg=16)
    for i in range(1, NSEG):
        x = CAP + i * SEGL
        f = f + box(x - 2.5, 3, CZ0 + 10, x + 2.5, 9, TOP - 6)
    return f
for k in range(4):
    f = rot(crown_side(k), k) - skel_cut
    segs = [f ^ rot(box(CAP + i * SEGL + 0.3, -5, 0, CAP + (i + 1) * SEGL - 0.3, 200, 2000), k) for i in range(NSEG)]
    add(f'cside{k}', f'Crown side, {SIDE[k]} ({NSEG} pieces)', union(segs), 'Matte black PLA', '#1c2028', 'Crown',
        'The band round the crown. Its ledge carries the screen mask and its lip carries the top. One piece has the camera window and two mic holes.',
        rotv([0, -50, 90], k), 'Print upside down, no supports.', pieces=[[SEGL, 45, TOP - CZ0]] * NSEG)
def ccap(k):
    c = cbox(0, 0, CZ0, CAP, CAP, TOP, 10)
    c = c - M.extrude(CS([[(-1, TOP - 9), (9, TOP + 1), (-1, TOP + 1)]]), 200).rotate([90, 0, 90]).translate([-1, 0, 0])
    c = c - cbox(3, 3, CZ0 - 1, CAP - 3, CAP - 3, TOP - 3, 8)
    return rot(c, k) - skel_cut
for k in range(4):
    add(f'ccap{k}', f'Crown corner cap, {CORNER[k]}', ccap(k), 'Matte black PLA', '#1c2028', 'Crown', 'Chamfered crown corner over the top of the corner post.', rotv([-50, -50, 100], k), 'Print upside down.')

mons, wins, holds = [], [], []
for k in range(4):
    mx = box(C - SOFF, C - SOFF - 1 - MON[0], ZS, C - SOFF + MON[1], C - SOFF - 1, ZS + MON[2])
    mons.append(rot(mx, k))
    bz_side, bz_end = (MON[1] - ACT[1]) / 2, (MON[0] - ACT[0]) / 2
    wins.append(rot(box(C - SOFF + bz_side, C - SOFF - 1 - MON[0] + bz_end, CR0 - 1, C - SOFF + MON[1] - bz_side, C - SOFF - 1 - bz_end, CR0 + 4), k))
    zt = ZR if k in (0, 2) else ZR2
    v = box(C + UC - 50, C - VC - 30, ZS + MON[2], C + UC + 50, C - VC + 30, ZS + MON[2] + 3)
    v = v + (box(C + UC - 50, C - VC - 10, ZS + MON[2] + 3, C + UC + 50, C - VC + 10, zt) - box(C + UC - 46, C - VC - 11, ZS + MON[2] + 2, C + UC + 46, C - VC + 11, zt - 3))
    holds.append(rot(v, k))
add('holds', 'Screen hold-downs (set of 4)', union(holds), 'Black PETG', '#30394a', 'Crown',
    'Bolt to the beams and press each screen face down onto the mask, with a felt pad so nothing scratches. Two are short, two tall, because the side beams sit one beam higher.', [0, 0, 150], 'Print flat, all four at once.', pieces=[[100, 60, 24]] * 4)
mask = box(26, 26, CR0, W - 26, D - 26, ZS)
for k in range(4):
    mask = mask - rot(box(-1, -1, CR0 - 1, CAP + 2, CAP + 2, ZS + 1), k)
mask = mask - union(wins)
cams = [rot(box(C - 14, 3, ZR + E + 1, C + 14, 18, ZR + E + 2.5) + box(C - 14, 3, ZR + E + 1, C - 11.5, 18, TOP - 6) + box(C + 11.5, 3, ZR + E + 1, C + 14, 18, TOP - 6), k) for k in range(4)]
add('cams', 'Camera brackets (set of 4)', union(cams), 'Black PETG', '#30394a', 'Crown',
    'One small USB camera behind each side window, so she knows which side you are on and that view is drawn from where you stand.', [0, 0, 130], 'Print flat, all four at once.', pieces=[[28, 15, 16]] * 4)
grille = cyl(72, 3, C, C, TOP) - (hexgrid(C - 64, C - 64, C + 64, C + 64, 7, 2, TOP - 1, 5) ^ cyl(64, 6, C, C, TOP - 1))
add('topgrille', 'Crown exhaust grille', grille, 'Matte black PLA', '#353b46', 'Crown', 'Lets the warmth from the four screens out of the top.', [0, 0, 180], 'Print flat.')
et = box(C - 108, C + 150, BASE_H - 12 - E - 4, C - 2, C + 300, BASE_H - 12 - E - 1) + box(C + 2, C + 150, BASE_H - 12 - E - 4, C + 108, C + 300, BASE_H - 12 - E - 1)
add('etray', 'Electronics trays (pair)', et, 'Matte black PLA', '#4a5260', 'Base', 'Hang under the cross rails and carry the ESP32, amplifier, USB hub and power board.', [0, 0, -70], 'Print flat.', pieces=[[106, 150, 3]] * 2)

# ---------------- bought ----------------
buy('skeleton', 'Aluminium 2020 frame (posts, rings, beams)', skeleton, '#9aa4b2',
    'Four corner posts, three square rings, two cross rails under the deck and four beams over the screens. Cut to length by the supplier.', [0, 0, 0])
for k in range(4):
    buy(f'mon{k}', f'15.6 inch portable monitor, {SIDE[k]} view (face down)', mons[k], '#59616e',
        'A slim 15.6 inch portable monitor lying face down. Its long side runs out from the centre, which is what sets her height.', rotv([0, -25, 150], k))
    buy(f'glass{k}', f'Beam-splitter panel, {SIDE[k]}', glass[k], '#a8d8ff',
        'Trapezoid of beam-splitter acrylic: 800 mm along the bottom, 120 mm along the top, 481 mm up the slope, at 45 degrees.', rotv([0, -20, 0], k))
buy('mask', 'Screen mask (black composite sheet)', mask, '#0c0f14', 'Matte black aluminium composite with four windows cut to the screens. Hides the screen edges and the gap in the middle.', [0, 0, 120])
deck = box(10, 10, BASE_H - 12, W - 10, D - 10, BASE_H)
for k in range(4):
    deck = deck - rot(box(-1, -1, BASE_H - 13, COL + 1, COL + 1, BASE_H + 1), k)
buy('deck', 'Deck (12 mm black MDF)', deck, '#20252d', 'The floor of the display, painted matte black.', [0, 0, -25])
sides = union([rot(box(COL, 7, 8, W - COL, 10, BASE_H - 12), k) for k in range(4)])
buy('panels', 'Pedestal side panels (black composite, x4)', sides, '#262b33', 'Matte black aluminium composite, cut out behind the grilles and light bars.', [0, 0, -25])
top = box(3, 3, TOP - 3, W - 3, D - 3, TOP) - cyl(66, 6, C, C, TOP - 4)
for k in range(4):
    top = top - rot(box(-1, -1, TOP - 4, CAP + 1, CAP + 1, TOP + 1), k)
buy('topskin', 'Crown top (black composite)', top, '#20252d', 'Lies on the side beams and the crown lip.', [0, 0, 210])
buy('speaker', '3 inch speaker (down-firing) + amplifier', cyl(40, 36, C, C - 200, 60), '#8a6d3b', 'Fires down under the deck; sound comes out of all four grilles.', [0, 0, -40])
cm = union([rot(box(C - 7, 5, ZR + E + 3, C + 7, 11, ZR + E + 13), k) for k in range(4)])
buy('cam', 'USB camera boards (x4)', cm, '#9aa4b2', 'Small 1080p cameras, one per side.', [0, 0, 130])
fz = 4.0
fig0 = union([cyl(9, 36, -8, 0, fz, r2=7), cyl(9, 36, 8, 0, fz, r2=7), cyl(9, 62, -7, 0, fz + 36, r2=11), cyl(9, 62, 7, 0, fz + 36, r2=11),
              cyl(16, 70, 0, 0, fz + 96, r2=19), cyl(4, 66, -22, 0, fz + 100, r2=5), cyl(4, 66, 22, 0, fz + 100, r2=5),
              cyl(5, 12, 0, 0, fz + 164), M.sphere(12, 48).scale([0.9, 0.95, 1.15]).translate([0, 0, fz + 182])])
bb = fig0.bounding_box()
sc = FIG_H / (bb[5] - bb[2])
fig = fig0.translate([0, 0, -bb[2]]).scale([sc, sc, sc]).translate([C, C, FEET])
buy('figure', 'Where she appears (about 31 cm, seen from all four sides)', fig, '#5cc8ff',
    'A stand-in shape on the centre axis. Each face shows her from its own side. Her real model never goes into these files.', [0, 0, 0])

# ---------------- export ----------------
out = sys.argv[1]
import numpy as np, trimesh
os.makedirs(out + '/stl', exist_ok=True)
scene = trimesh.Scene(); summary = []
for coll, is_print in ((parts, True), (bought, False)):
    for key, man in coll.items():
        if man.is_empty():
            print('EMPTY', key); continue
        mesh = man.to_mesh()
        tm = trimesh.Trimesh(np.asarray(mesh.vert_properties)[:, :3], np.asarray(mesh.tri_verts), process=False)
        scene.add_geometry(tm, node_name=key, geom_name=key)
        b = man.bounding_box(); dims = [round(b[i + 3] - b[i]) for i in range(3)]
        e = dict(key=key, printed=is_print, dims=dims, tris=len(tm.faces), **meta[key])
        if is_print:
            dens = 1.27 if 'PETG' in meta[key]['material'] else 1.24
            e['grams'] = round(man.volume() / 1000 * dens * 0.75)
            pcs = SET.get(key)
            if pcs:
                e['dims'] = [round(x) for x in pcs[0]]; e['pieces'] = len(pcs)
                e['fits'] = all(sorted(p)[2] <= 256 and sorted(p)[1] <= 256 for p in pcs)
            else:
                sd = sorted(dims)
                e['fits'] = bool(sd[2] <= 256 and sd[1] <= 256) or (sd[2] <= 330 and sd[1] <= 60)
            if not e['fits']: print('NOFIT', key, dims)
            tm.export(f'{out}/stl/{key}.stl')
        summary.append(e)
scene.export(f'{out}/capsule.glb')
json.dump(summary, open(f'{out}/parts.json', 'w'), indent=1)
allp = {**parts, **bought}
keys = list(allp)
for i in range(len(keys)):
    for j in range(i + 1, len(keys)):
        a, b = keys[i], keys[j]
        if a in bought and b in bought and not (a == 'figure' or b == 'figure'):
            continue
        ba, bb_ = allp[a].bounding_box(), allp[b].bounding_box()
        if any(ba[k] > bb_[k + 3] or bb_[k] > ba[k + 3] for k in range(3)):
            continue
        v = (allp[a] ^ allp[b]).volume()
        if v > 1:
            print('CLASH', a, b, round(v))
pr = [s for s in summary if s['printed']]
npieces = sum(s.get('pieces', 1) for s in pr)
print(len(pr), 'entries', npieces, 'pieces', sum(s['grams'] for s in pr), 'g', 'TOP', TOP, 'ZS', ZS, 'head top', FEET + FIG_H, 'v_head', ZS - FEET - FIG_H)
