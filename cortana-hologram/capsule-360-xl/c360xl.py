"""Cortana 360 capsule XL v1: four-sided Pepper's-ghost pyramid lit by four 32 inch monitors in a pinwheel,
her image about 64 cm tall on the centre axis. mm, x width, y depth (front y=0), z up (floor z=0).
Aluminium 3030 skeleton (bought) + printed Forge-style shell, optics holders and pad."""
import sys, math, json, os
sys.path.insert(0, os.path.dirname(__file__))
from helpers import *

W = D = 1800.0
C = W / 2
BASE_H = 350.0             # deck top
S2 = 800.0                 # pyramid half base
ZB = BASE_H + 6            # glass bottom edge (sits in shoes)
ZS = ZB + S2               # screen faces: every reflection lands on the centre axis
V0 = 120.0                 # glass tips stop 120 mm from the axis
ZTOP_GLASS = ZS - V0
CR0 = ZS - 3               # underside of the screen mask
TOP = 1268.0
GT = 5.0                   # beam-splitter acrylic thickness
MON = (714.0, 424.0, 45.0) # 32 inch monitor body (long, short, thick)
ACT = (698.0, 393.0)
SOFF = 125.0               # pinwheel offset: each screen starts 126 mm out from the axis, shifted 125 mm sideways
FIG_H = 640.0
FEET = ZB + 6              # her feet (on the pad)
P = 60.0                   # pillar cover size
COL = 110.0                # base corner column size

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

# ---------------- skeleton (bought, 3030 aluminium) ----------------
E = 30.0
posts = [box(px, py, 0, px + E, py + E, 1235) for (px, py) in [(15, 15), (W - 45, 15), (W - 45, D - 45), (15, D - 45)]]
rails = []
for (z0) in (0, BASE_H - 42, 1205):
    rails += [box(45, 15, z0, W - 45, 45, z0 + E), box(45, D - 45, z0, W - 45, D - 15, z0 + E),
              box(15, 45, z0, 45, D - 45, z0 + E), box(W - 45, 45, z0, W - 15, D - 45, z0 + E)]
cross = [box(C - 215, 45, BASE_H - 42, C - 185, D - 45, BASE_H - 12), box(C + 185, 45, BASE_H - 42, C + 215, D - 45, BASE_H - 12)]
# VESA beams over each screen: front/back along x at z 1205-1235, left/right along y at z 1235-1265
VC = (SOFF + 1 + MON[0] / 2)       # radial distance of a screen's centre
UC = MON[1] / 2 - SOFF              # sideways offset of a screen's centre
beams = [box(45, C - VC - 15, 1205, W - 45, C - VC + 15, 1235), box(45, C + VC - 15, 1205, W - 45, C + VC + 15, 1235),
         box(C + VC - 15, 15, 1235, C + VC + 15, D - 15, 1265), box(C - VC - 15, 15, 1235, C - VC + 15, D - 15, 1265)]
skeleton = union(posts + rails + cross + beams)

def grow_box_list(lst, g):
    out = []
    for m in lst:
        b = m.bounding_box()
        out.append(box(b[0] - g, b[1] - g, b[2] - g, b[3] + g, b[4] + g, b[5] + g))
    return union(out)
skel_cut = grow_box_list(posts + rails + cross + beams, 0.5)

# ---------------- base corner columns ----------------
def column(k):
    c = cbox(0, 0, 0, COL, COL, BASE_H, 16)
    bev = M.extrude(CS([[(-1, BASE_H - 14), (14, BASE_H + 1), (-1, BASE_H + 1)]]), 300).rotate([90, 0, 90]).translate([-1, 0, 0])
    c = c - bev - bev.rotate([0, 0, -90]).translate([0, 300, 0]).mirror([1, 0, 0]).translate([0, 0, 0]) if False else c - bev
    c = c - (cbox(4, 4, -1, COL - 4, COL - 4, BASE_H - 4, 13) - box(0, 0, 0, 0.1, 0.1, 0.1))
    for zz in (90, 180, 270):  # panel lines
        c = c - (box(-1, -1, zz, COL + 1, 1.5, zz + 2) + box(-1, -1, zz, 1.5, COL + 1, zz + 2))
    c = c - box(20, -1, 300, 24, 2.5, 330) - box(-1, 20, 300, 2.5, 24, 330)  # glow marks
    c = c + (box(COL - 14, 4, BASE_H - 4, COL, COL, BASE_H) + box(4, COL - 14, BASE_H - 4, COL, COL, BASE_H))  # deck lip
    return rot(c, k) - skel_cut
for k in range(4):
    col = column(k)
    for s, (z0, z1) in enumerate(((0, 175), (175, BASE_H + 1))):
        ex = rotv([-60, -60, s * 30], k)
        add(f'col{k}_{s}', f'Base corner column, {CORNER[k]}, {"lower" if s == 0 else "upper"}', col ^ box(-10, -10, z0, W + 10, D + 10, z1), 'Matte black PLA', '#1c2028', 'Base',
            'Chamfered corner of the pedestal, wrapped round the aluminium corner post, with panel lines and a glow mark.', ex, 'Print upright, no supports.')

# ---------------- base fascia: grilles + light bars (each side) ----------------
for k in range(4):
    g = box(C - 120, 4, 60, C + 120, 12, 220)
    g = g - (hexgrid(C - 108, 60 + 12, C + 108, 220 - 12, 8, 2.4, -1, 20).rotate([90, 0, 0]).translate([0, 0, 0]) if False else box(0, 0, 0, 0.1, 0.1, 0.1))
    holes = union([cyly(3.6, 12, C + dx, 3, 140 + dz, seg=6) for dx in range(-104, 105, 10) for dz in range(-64, 65, 9) if (dx / 104.0) ** 2 + (dz / 64.0) ** 2 <= 1.0])
    g = (g - holes)
    add(f'grille{k}', f'Vent and speaker grille, {SIDE[k]}', rot(g, k), 'Matte black PLA', '#353b46', 'Base',
        'Hex grille in the middle of each side. Sound from the down-firing speaker and warm air from the electronics come out here.', rotv([0, -60, 0], k), 'Print flat (face down).')
    bars = []
    for i in range(5):
        x0 = C - 600 + i * 240
        b = box(x0 + 0.5, 4, 256, x0 + 239.5, 12, 268) - box(x0 + 4, 6, 258, x0 + 236, 13, 266)
        bars.append(b)
    add(f'bars{k}', f'Light bar diffusers, {SIDE[k]} (5 pieces)', rot(union(bars), k), 'Natural / translucent PETG', '#5cc8ff', 'Base',
        'A long glowing strip across the side of the pedestal, lit by an LED strip behind it and synced to her mood.', rotv([0, -40, 0], k), 'Print flat, five pieces.', pieces=[[239, 8, 12]] * 5)

# ---------------- glass shoes (5 per side) ----------------
for k in range(4):
    sh = []
    for dx in (-600, -300, 0, 300, 600):
        s = cbox(C + dx - 40, C - S2 - 22, BASE_H, C + dx + 40, C - S2 + 28, BASE_H + 18, 5) - box(C + dx - 36, C - S2 - 18, BASE_H - 1, C + dx + 36, C - S2 - 7, BASE_H + 14) - box(C + dx - 36, C - S2 + 14, BASE_H - 1, C + dx + 36, C - S2 + 24, BASE_H + 9)
        sh.append(s)
    shoe = rot(union(sh), k) - glass_cut
    add(f'shoes{k}', f'Glass shoes, {SIDE[k]} (5 pieces)', shoe, 'Black PETG', '#30394a', 'Optics',
        'Small angled blocks screwed to the deck. The bottom edge of the glass sits in their slots at exactly 45 degrees.', rotv([0, -50, 20], k), 'Print flat, five at once.', pieces=[[80, 50, 18]] * 5)

# ---------------- hip rails (4 corners, 6 pieces each) ----------------
h = ZTOP_GLASS - ZB
for idx, (sx, sy) in enumerate([(-1, -1), (1, -1), (1, 1), (-1, 1)]):
    a = (C + sx * S2, C + sy * S2, ZB)
    b = (C + sx * (S2 - h), C + sy * (S2 - h), ZTOP_GLASS)
    pts = []
    for (px, py, pz) in (a, b):
        for dx in (-11, 11):
            for dy in (-11, 11):
                pts.append((px + sx * 11 + dx * 0.7, py + sy * 11 + dy * 0.7, pz))
                pts.append((px + sx * 11 + dx * 0.7, py + sy * 11 + dy * 0.7, pz + 12))
    rail = M.hull_points(pts) - glass_cut
    rail = rail - box(-10, -10, -10, W + 10, D + 10, BASE_H) - box(-10, -10, ZTOP_GLASS - 14, W + 10, D + 10, 2000)
    zs = [BASE_H + i * (ZTOP_GLASS - 14 - BASE_H) / 6 for i in range(7)]
    for s in range(6):
        add(f'hip{idx}_{s}', f'Corner rail, {CORNER[idx]}, piece {s + 1} of 6', rail ^ box(-10, -10, zs[s] + (0.3 if s else 0), W + 10, D + 10, zs[s + 1]), 'Matte black PLA', '#2b313c', 'Optics',
            'Runs up one corner of the pyramid and grips the edges of two glass panels, so the seam between two views is as thin as possible. Pinned end to end.',
            [sx * 70, sy * 70, s * 18], 'Print lying on its side, no supports.')

# ---------------- apex hub ----------------
hub = cbox(C - 124, C - 124, ZTOP_GLASS - 14, C + 124, C + 124, CR0, 30) - glass_cut
hub = hub - cbox(C - 117, C - 117, ZTOP_GLASS + 4, C + 117, C + 117, CR0 - 4, 26) - cbox(C - 100, C - 100, ZTOP_GLASS - 15, C + 100, C + 100, ZTOP_GLASS + 5, 22)
for k in range(4):
    hub = hub - rot(box(C - 90, C - 125, CR0 - 40, C + 90, C - 122.5, CR0 - 37), k)  # glow line
add('hub', 'Apex hub', hub, 'Matte black PLA', '#2b313c', 'Optics',
    'Hangs from the screen mask in the gap at the centre of the four screens and locks the four glass tips together above her head.', [0, 0, 120], 'Print upright (top on the bed).')

# ---------------- holo pad ----------------
pad_base = cyl(124, 6, C, C, BASE_H) - cyl(8, 8, C, C + 90, BASE_H - 1)
pad_diff = cyl(104, 2, C, C, BASE_H + 6)
cover = cyl(124, 4, C, C, BASE_H + 8) - cyl(60, 6, C, C, BASE_H + 7) - (cyl(125, 3, C, C, BASE_H + 6) - cyl(106, 3, C, C, BASE_H + 6) if False else box(0, 0, 0, 0.1, 0.1, 0.1))
cover = cover + (cyl(124, 2, C, C, BASE_H + 6) - cyl(105, 3, C, C, BASE_H + 5.5))
for i in range(20):
    cover = cover - M.extrude(CS([[(66, -5), (98, -9), (98, 9), (66, 5)]]), 6).translate([0, 0, BASE_H + 7]).rotate([0, 0, i * 18 + 9]).translate([C, C, 0])
add('pad_base', 'Holo pad base', pad_base, 'Matte black PLA', '#252a33', 'Holo pad', 'Holds the LED ring on the deck, right under the point where all four reflections meet.', [0, 0, 30], 'Print flat.')
add('pad_diff', 'Holo pad diffuser', pad_diff, 'Natural / translucent PETG', '#5cc8ff', 'Holo pad', 'Glows through the cover windows, pulsing when she speaks and shifting colour with her mood.', [0, 0, 60], 'Print flat.')
add('pad_cover', 'Holo pad cover', cover, 'Matte black PLA', '#15181e', 'Holo pad', 'The emitter she stands on. Her feet land here from every side.', [0, 0, 90], 'Print flat.')

# ---------------- pillar covers (4 corners, 4 sections each) ----------------
PZ0, PZ1 = BASE_H, 1140.0
def pillar(k):
    p = cbox(0, 0, PZ0, P, P, PZ1, 9)
    p = p - box(44, 44, PZ0 - 1, P + 1, P + 1, PZ1 + 1)  # inner corner notch faces the glass
    p = p + cbox(44 - 0.01, 44 - 0.01, PZ0, P - 3, P - 3, PZ1, 0.0) if False else p
    for zz in [PZ0 + 100 + 160 * i for i in range(5)]:
        p = p - (cbox(-1, -1, zz, P + 1, P + 1, zz + 2, 9) - cbox(1.5, 1.5, zz - 1, P - 1.5, P - 1.5, zz + 3, 8))
    p = p - box(28, -1, PZ0 + 30, 32, 2.5, PZ1 - 30) - box(-1, 28, PZ0 + 30, 2.5, 32, PZ1 - 30)  # glow channels
    p = p - (cbox(5, 5, PZ0 - 1, P - 5, P - 5, PZ1 + 1, 6) - box(40, 40, PZ0 - 2, P + 1, P + 1, PZ1 + 2))  # hollow shell
    return rot(p, k) - skel_cut
for k in range(4):
    p = pillar(k)
    zs = [PZ0 + i * (PZ1 - PZ0) / 4 for i in range(5)]
    for s in range(4):
        add(f'pil{k}_{s}', f'Pillar cover, {CORNER[k]}, section {s + 1} of 4', p ^ box(-10, -10, zs[s], W + 10, D + 10, zs[s + 1]), 'Matte black PLA', '#1c2028', 'Frame',
            'Clips round the aluminium corner post, with glowing edge lines up two faces.', rotv([-90, -90, s * 25], k), 'Print upright, no supports.')

# ---------------- crown ----------------
CZ0 = 1140.0
def crown_side(k):
    f = box(90, 0, CZ0, W - 90, 3, TOP)                    # outer skin
    f = f + box(90, 0, CZ0 + 6, W - 90, 70, CZ0 + 10)      # ledge under the screen mask (mask 1153-1156)
    f = f + box(90, 3, TOP - 6, W - 90, 40, TOP - 3)       # lip under the top skin
    f = f + box(90, 11, 1205, W - 90, 15, 1235)            # fixing tab against the ring rail
    f = f - M.extrude(CS([[(-1, TOP - 14), (14, TOP + 1), (-1, TOP + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
    f = f - box(110, -1, CZ0 + 30, W - 110, 1.2, CZ0 + 33)  # glow line
    f = f - box(C - 12, -1, 1240, C + 12, 5, 1256)          # camera window
    for dx in (-80, 80):
        f = f - cyly(1.6, 6, C + dx, -1, 1248, seg=16)      # mic holes
    for i in range(1, 7):                                   # joint ribs
        x = 90 + i * (W - 180) / 7
        f = f + box(x - 3, 3, CZ0 + 10, x + 3, 11, TOP - 6)
    return f
for k in range(4):
    f = rot(crown_side(k), k) - skel_cut
    segs = []
    for i in range(7):
        x0 = 90 + i * (W - 180) / 7
        segs.append(f ^ rot(box(x0 + 0.3, -5, 1000, x0 + (W - 180) / 7 - 0.3, 200, 1400), k))
    add(f'cside{k}', f'Crown side, {SIDE[k]} (7 pieces)', union(segs), 'Matte black PLA', '#1c2028', 'Crown',
        'The band round the crown. Its ledge carries the screen mask and its lip carries the top skin. The middle piece has the camera window and two mic holes.',
        rotv([0, -80, 140], k), 'Print upside down, no supports, seven pieces.', pieces=[[(W - 180) / 7, 70, TOP - CZ0]] * 7)
def ccap(k):
    c = cbox(0, 0, CZ0, 90, 90, TOP, 16)
    c = c - M.extrude(CS([[(-1, TOP - 14), (14, TOP + 1), (-1, TOP + 1)]]), 200).rotate([90, 0, 90]).translate([-1, 0, 0])
    c = c - M.extrude(CS([[(-1, TOP - 14), (14, TOP + 1), (-1, TOP + 1)]]), 200).rotate([90, 0, 90]).rotate([0, 0, 90]).translate([0, -1, 0]).mirror([1, 0, 0]) if False else c
    c = c - cbox(4, 4, CZ0 - 1, 86, 86, TOP - 4, 13)
    c = c + (box(60, 60, CZ0, 90, 90, CZ0 + 10) - box(64, 64, CZ0 - 1, 91, 91, CZ0 + 11)) if False else c
    return rot(c, k) - skel_cut
for k in range(4):
    add(f'ccap{k}', f'Crown corner cap, {CORNER[k]}', ccap(k), 'Matte black PLA', '#1c2028', 'Crown', 'Chamfered crown corner over the top of the corner post.', rotv([-80, -80, 160], k), 'Print upside down.')

# screens, mask windows, VESA adapters
mons, wins, vesa = [], [], []
for k in range(4):
    mx = box(C - SOFF, C - SOFF - 1 - MON[0], ZS, C - SOFF + MON[1], C - SOFF - 1, ZS + MON[2])
    mons.append(rot(mx, k))
    bz_side, bz_end = (MON[1] - ACT[1]) / 2, (MON[0] - ACT[0]) / 2
    wins.append(rot(box(C - SOFF + bz_side, C - SOFF - 1 - MON[0] + bz_end, CR0 - 1, C - SOFF + MON[1] - bz_side, C - SOFF - 1 - bz_end, CR0 + 4), k))
    zt = 1205 if k in (0, 2) else 1235
    v = box(C + UC - 70, C - VC - 70, ZS + MON[2], C + UC + 70, C - VC + 70, ZS + MON[2] + 4) + (box(C + UC - 70, C - VC - 15, ZS + MON[2] + 4, C + UC + 70, C - VC + 15, zt) - box(C + UC - 60, C - VC - 16, ZS + MON[2] + 3, C + UC + 60, C - VC + 16, zt - 4) if zt - (ZS + MON[2] + 4) > 1 else box(0, 0, 0, 0.1, 0.1, 0.1))
    for (dx, dy) in [(-50, -50), (50, -50), (-50, 50), (50, 50)]:
        v = v - cyl(2.6, 60, C + UC + dx, C - VC + dy, ZS)
    vesa.append(rot(v, k))
add('vesa', 'VESA adapters (set of 4)', union(vesa), 'Black PETG', '#30394a', 'Crown',
    'Bolt each screen\'s VESA holes to its aluminium beam. Two are flat plates, two are tall, because the side beams sit one beam higher.', [0, 0, 260], 'Print flat, one at a time.', pieces=[[140, 140, 34]] * 4)
mask = box(40, 40, CR0, W - 40, D - 40, ZS)
for k in range(4):
    mask = mask - rot(box(0, 0, CR0 - 1, 92, 92, ZS + 1), k)
mask = mask - union(wins)
cams = []
for k in range(4):
    cb = box(C - 20, 4, 1236, C + 20, 26, 1239) + box(C - 20, 4, 1236, C - 17, 26, 1258) + box(C + 17, 4, 1236, C + 20, 26, 1258)
    cams.append(rot(cb, k))
add('cams', 'Camera brackets (set of 4)', union(cams), 'Black PETG', '#30394a', 'Crown',
    'One USB camera behind each side window, so she knows which side you are on and that view is drawn from where you stand.', [0, 0, 220], 'Print flat, all four at once.', pieces=[[40, 22, 22]] * 4)
grille = cyl(126, 3, C, C, TOP) - (hexgrid(C - 118, C - 118, C + 118, C + 118, 9, 2.4, TOP - 1, 5) ^ cyl(118, 6, C, C, TOP - 1))
add('topgrille', 'Crown exhaust grille', grille, 'Matte black PLA', '#353b46', 'Crown', 'Lets the heat from the four screens out of the top.', [0, 0, 300], 'Print flat.')
# electronics trays (pair) under the deck, hung from the cross rails
et = box(C - 200, C + 250, BASE_H - 46, C - 2, C + 450, BASE_H - 43) + box(C + 2, C + 250, BASE_H - 46, C + 200, C + 450, BASE_H - 43)
add('etray', 'Electronics trays (pair)', et, 'Matte black PLA', '#4a5260', 'Base', 'Hang under the cross rails and carry the ESP32, amplifier, USB hub and power board.', [0, 0, -120], 'Print flat.', pieces=[[198, 200, 3]] * 2)

# ---------------- bought ----------------
buy('skeleton', 'Aluminium 3030 frame (posts, rings, beams)', skeleton, '#9aa4b2',
    'Four 1.235 m corner posts, three square rings, two cross rails under the deck and four beams that carry the screens. Cut to length by the supplier.', [0, 0, 0])
for k in range(4):
    buy(f'mon{k}', f'32 inch monitor, {SIDE[k]} view (face down)', mons[k], '#59616e',
        'A slim 32 inch monitor lying face down. Its long side runs out from the centre, which is what sets her height.', rotv([0, -40, 240], k))
    buy(f'glass{k}', f'Beam-splitter panel, {SIDE[k]}', glass[k], '#a8d8ff',
        'Trapezoid of beam-splitter acrylic: 1600 mm along the bottom, 240 mm along the top, 962 mm up the slope, at 45 degrees.', rotv([0, -30, 0], k))
buy('mask', 'Screen mask (black composite sheet, 2 pieces)', mask, '#0c0f14', 'Matte black aluminium composite with four windows cut to the screens. Hides the screen edges and the gap in the middle.', [0, 0, 200])
deck = box(15, 15, BASE_H - 12, W - 15, D - 15, BASE_H)
for k in range(4):
    deck = deck - rot(box(0, 0, BASE_H - 13, COL + 2, COL + 2, BASE_H + 1), k)
buy('deck', 'Deck (18 mm black MDF, 2 pieces)', deck, '#20252d', 'The floor of the display, painted matte black.', [0, 0, -40])
sides = union([rot(box(COL, 12, 10, W - COL, 15, BASE_H), k) for k in range(4)])
buy('panels', 'Pedestal side panels (black composite, x4)', sides, '#262b33', 'Matte black aluminium composite, cut out behind the grilles and light bars.', [0, 0, -40])
top = box(4, 4, TOP - 3, W - 4, D - 4, TOP) - cyl(120, 6, C, C, TOP - 4)
for k in range(4):
    top = top - rot(box(-1, -1, TOP - 4, 92, 92, TOP + 1), k)
buy('topskin', 'Crown top (black composite, 2 pieces)', top, '#20252d', 'Lies on the side beams and the crown lip.', [0, 0, 340])
buy('speaker', '5 inch speaker (down-firing) + amplifier', cyl(65, 50, C, C - 350, 150), '#8a6d3b', 'Fires down under the deck; sound comes out of all four grilles.', [0, 0, -60])
cm = union([rot(box(C - 10, 8, 1240, C + 10, 16, 1256), k) for k in range(4)])
buy('cam', 'USB camera boards (x4)', cm, '#9aa4b2', 'Small 1080p cameras, one per side.', [0, 0, 220])
# stand-in figure (generic shapes, scaled to FIG_H)
fz = 4.0
fig0 = union([cyl(9, 36, -8, 0, fz, r2=7), cyl(9, 36, 8, 0, fz, r2=7), cyl(9, 62, -7, 0, fz + 36, r2=11), cyl(9, 62, 7, 0, fz + 36, r2=11),
              cyl(16, 70, 0, 0, fz + 96, r2=19), cyl(4, 66, -22, 0, fz + 100, r2=5), cyl(4, 66, 22, 0, fz + 100, r2=5),
              cyl(5, 12, 0, 0, fz + 164), M.sphere(12, 48).scale([0.9, 0.95, 1.15]).translate([0, 0, fz + 182])])
bb = fig0.bounding_box()
sc = FIG_H / (bb[5] - bb[2])
fig = fig0.translate([0, 0, -bb[2]]).scale([sc, sc, sc]).translate([C, C, FEET])
buy('figure', 'Where she appears (about 64 cm, seen from all four sides)', fig, '#5cc8ff',
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
