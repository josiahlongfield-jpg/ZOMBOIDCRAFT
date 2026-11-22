"""Cortana 360 capsule v1: four-sided Pepper's-ghost pyramid, walk-around, 620 mm tall. mm, x width, y depth (front y=0), z up."""
import sys, math, json, os
sys.path.insert(0, os.path.dirname(__file__))
from helpers import *

W = D = 620.0
C = W / 2
BASE_H = 250.0
S2 = 230.0                 # pyramid half base
ZB = 252.0                 # glass bottom edge
ZS = ZB + S2               # screen face (482): puts every reflected image on the centre axis
ZTOP_GLASS = ZS - 10       # truncated apex
CR0, TOP = ZS - 2, 620.0   # crown underside 480
P = 30.0
SH = 3.0
MON = (481.5, 549.5, 44.9) # LG DualUp 28MQ780 without stand (x across, y front-back, thickness)

parts, meta, bought = {}, {}, {}
def add(key, name, man, material, colour, group, note, explode, print_note):
    parts[key] = man
    meta[key] = dict(name=name, material=material, colour=colour, group=group, note=note, explode=explode, print=print_note)
def buy(key, name, man, colour, note, explode):
    bought[key] = man
    meta[key] = dict(name=name, material='Bought part', colour=colour, group='Bought parts (shown for fit)', note=note, explode=explode, print=None)
def cbox(x0, y0, z0, x1, y1, z1, c):
    return prism(chamfer_rect(x0, y0, x1, y1, c), z0, z1 - z0)
T = W / 3.0
def tiles(man, z0, z1):
    out = {}
    for i, cx in enumerate('lcr'):
        for j, cy in enumerate('fmb'):
            out[cy + cx] = man ^ box(i * T - (1 if i == 0 else 0), j * T - (1 if j == 0 else 0), z0, (i + 1) * T + (1 if i == 2 else 0), (j + 1) * T + (1 if j == 2 else 0), z1)
    return out
TN = {'f': 'front', 'm': 'middle', 'b': 'back', 'l': 'left', 'c': 'centre', 'r': 'right'}

# glass faces (thin slabs) ------------------------------------------------
def face(side, thick=3.0, grow=0.0):
    # side: 0 front(-y), 1 right(+x), 2 back(+y), 3 left(-x); triangle truncated at ZTOP_GLASS
    h = ZTOP_GLASS - ZB
    a = S2 + grow + 2 * grow
    top = max(S2 - h - grow, 1) + 2 * grow
    pts2 = [(-a, -S2 - grow, ZB - grow), (a, -S2 - grow, ZB - grow), (top, -(S2 - h) + grow, ZTOP_GLASS + grow), (-top, -(S2 - h) + grow, ZTOP_GLASS + grow)]
    n = (0, -1 / math.sqrt(2), 1 / math.sqrt(2))
    pts = []
    for (x, y, z) in pts2:
        for s in (-0.5, 0.5):
            pts.append((x + n[0] * s * thick, y + n[1] * s * thick, z + n[2] * s * thick))
    m = M.hull_points(pts).rotate([0, 0, 90 * side]).translate([C, C, 0])
    return m
glass_all = union([face(i) for i in range(4)])
glass_cut = union([face(i, 3.8, 4) for i in range(4)])

# ---------------- base ----------------
base = cbox(0, 0, 0, W, D, BASE_H, 34)
for rot in range(4):  # bevel top edge on all 4 sides
    bev = M.extrude(CS([[(-1, BASE_H - 16), (16, BASE_H + 1), (-1, BASE_H + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
    base = base - bev.translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0])
base = base - cbox(SH, SH, -1, W - SH, D - SH, BASE_H - 4, 31)
for rot in range(4):
    feat = []
    # light bar slot, speaker grille (hex) on each side, panel lines
    feat.append(box(C - 120, -1, 200, C + 120, 4, 206))
    feat.append(union([cyly(3.4, 8, C + dx, -1, 110 + dz, seg=6) for dx in range(-72, 73, 9) for dz in range(-36, 37, 9) if (dx / 72.0) ** 2 + (dz / 36.0) ** 2 <= 1.0]))
    feat.append(box(60, -1, 40, W - 60, 1.0, 41.5))
    feat.append(box(60, -1, 176, W - 60, 1.0, 177.5))
    base = base - union(feat).translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0])
# rear ports (back side)
base = base - box(C - 70, D - SH - 1, 60, C - 58, D + 1, 66) - cyly(5.6, 8, C - 30, D - SH - 1, 63) - box(C + 0, D - SH - 1, 56, C + 20, D + 1, 70)
# pillar sockets + bosses
for (px, py) in [(0, 0), (W - P, 0), (0, D - P), (W - P, D - P)]:
    base = base - box(px + 3.4, py + 3.4, BASE_H - 10, px + P - 3.4, py + P - 3.4, BASE_H + 1)
    base = base + (box(px + 1.5, py + 1.5, BASE_H - 34, px + P - 1.5, py + P - 1.5, BASE_H - 6) - box(px + 3.4, py + 3.4, BASE_H - 35, px + P - 3.4, py + P - 3.4, BASE_H))
# pad recess
base = base - cyl(96, 4, C, C, BASE_H - 3.5)
# joint ribs on tile seams + screw bosses
for k in (1, 2):
    base = base + box(k * T - 3, SH, 3, k * T + 3, D - SH, 14) + box(SH, k * T - 3, 3, W - SH, k * T + 3, 14)
BOSS = [(x, y) for x in (40, C, W - 40) for y in (40, C, D - 40) if not (x == C and y == C)] + [(T / 2, T / 2 + T), (W - T / 2, T / 2 + T)]
BOSS = [(x, y) for (x, y) in BOSS if not ((x in (40, W - 40)) and (y in (40, D - 40)))] + [(60, 60), (W - 60, 60), (60, D - 60), (W - 60, D - 60)]
for (bx, by) in BOSS:
    base = base + (cyl(5, BASE_H - 10, bx, by, 3) - cyl(2.1, BASE_H, bx, by, -1))
# internal speaker shelf (down-firing 4 inch driver, centre)
base = base + (cyl(62, 6, C, C, 150) - cyl(50, 8, C, C, 149))
for k, piece in tiles(base, -1, BASE_H + 2).items():
    add('base_' + k, f'Base tile, {TN[k[0]]} {TN[k[1]]}', piece, 'Matte black PLA', '#1c2028', 'Base',
        'One of nine base tiles. They bolt together on internal ribs into a pedestal that holds the speaker, the four light bars, the electronics and the power socket.' if k != 'mc' else
        'Centre base tile. It carries the holo pad recess and the speaker shelf.',
        [(-1 if k[1] == 'l' else 1 if k[1] == 'r' else 0) * 50, (-1 if k[0] == 'f' else 1 if k[0] == 'b' else 0) * 50, -60],
        'Print upside down (top deck on the bed). No supports.')
# bottom plates (3x3)
plate = cbox(SH + 0.4, SH + 0.4, 0, W - SH - 0.4, D - SH - 0.4, 2.4, 30.6)
for (bx, by) in BOSS:
    plate = plate - cyl(1.7, 6, bx, by, -1) - cyl(3.2, 1.6, bx, by, -0.1)
for (fx, fy) in [(50, 50), (W - 50, 50), (50, D - 50), (W - 50, D - 50)]:
    plate = plate - cyl(6.2, 1.2, fx, fy, -0.1)
for i in range(10):
    plate = plate - box(C - 100 + i * 20, D - 200, -1, C - 92 + i * 20, D - 60, 5)
for k, piece in tiles(plate, -1, 6).items():
    if piece.is_empty():
        continue
    add('plate_' + k, f'Bottom plate, {TN[k[0]]} {TN[k[1]]}', piece, 'Matte black PLA', '#252a33', 'Base',
        'Closes the base from below; rubber feet sit in the corner plates.',
        [(-1 if k[1] == 'l' else 1 if k[1] == 'r' else 0) * 50, (-1 if k[0] == 'f' else 1 if k[0] == 'b' else 0) * 50, -130], 'Print flat.')

# ---------------- glass frame (4 corner pieces) ----------------
fr = cbox(C - S2 - 22, C - S2 - 22, BASE_H, C + S2 + 22, C + S2 + 22, BASE_H + 14, 12) - cbox(C - S2 + 14, C - S2 + 14, BASE_H - 1, C + S2 - 14, C + S2 - 14, BASE_H + 15, 6)
fr = fr - glass_cut
for (sx, sy) in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
    fr = fr - cyl(1.7, 30, C + sx * (S2 + 12), C + sy * (S2 + 12), BASE_H - 5) - cyl(1.7, 30, C + sx * (S2 + 12), C, BASE_H - 5) if False else fr
for k, (x0, x1, y0, y1) in {'fl': (0, C, 0, C), 'fr': (C, W, 0, C), 'bl': (0, C, C, D), 'br': (C, W, C, D)}.items():
    add('frame_' + k, f'Glass frame, {TN[k[0]]} {TN[k[1]]} corner', fr ^ box(x0, y0, 0, x1, y1, 400), 'Matte black PLA', '#2b313c', 'Optics',
        'A quarter of the square frame that holds the bottom edges of the four glass panels in angled slots.',
        [(-1 if k[1] == 'l' else 1) * 30, (-1 if k[0] == 'f' else 1) * 30, 40], 'Print flat, no supports.')

# ---------------- hip rails (4 corners, each 2 pieces) ----------------
for idx, (sx, sy) in enumerate([(-1, -1), (1, -1), (1, 1), (-1, 1)]):
    a = (C + sx * S2, C + sy * S2, ZB)
    h = ZTOP_GLASS - ZB
    b = (C + sx * (S2 - h), C + sy * (S2 - h), ZTOP_GLASS)
    pts = []
    for (px, py, pz) in (a, b):
        for dx in (-9, 9):
            for dy in (-9, 9):
                pts.append((px + sx * 9 + dx * 0.7, py + sy * 9 + dy * 0.7, pz + (dx + dy) * 0 ))
                pts.append((px + sx * 9 + dx * 0.7, py + sy * 9 + dy * 0.7, pz + 10))
    rail = M.hull_points(pts) - glass_cut
    rail = rail - box(-10, -10, -10, W + 10, D + 10, BASE_H + 14) - box(-10, -10, ZTOP_GLASS - 14, W + 10, D + 10, 900)
    nm = {(-1, -1): 'front left', (1, -1): 'front right', (1, 1): 'back right', (-1, 1): 'back left'}[(sx, sy)]
    zmid = ZB + h / 2
    for half, (z0, z1) in (('lo', (0, zmid)), ('hi', (zmid, 800))):
        add(f'hip{idx}_{half}', f'Corner rail, {nm}, {"lower" if half == "lo" else "upper"}', rail ^ box(-10, -10, z0, W + 10, D + 10, z1), 'Matte black PLA', '#2b313c', 'Optics',
            'Runs up one corner of the pyramid and grips the edges of two glass panels, so the seam between views is as thin as possible.',
            [sx * 60, sy * 60, 30 if half == 'hi' else 0], 'Print lying on its side, no supports.')

# apex cap
apex = cbox(C - 26, C - 26, ZTOP_GLASS - 14, C + 26, C + 26, CR0, 8) - glass_cut
add('apex', 'Apex cap', apex, 'Matte black PLA', '#2b313c', 'Optics',
    'Holds the four glass panels together at the top, just under the screen.', [0, 0, 90], 'Print upright.')

# ---------------- holo pad ----------------
diff = cyl(95.6, 3.4, C, C, BASE_H - 3.5) - cyl(60, 5, C, C, BASE_H - 4)
add('pad_diff', 'Holo pad diffuser', diff, 'Natural / translucent PETG', '#5cc8ff', 'Holo pad',
    'Glows from an LED ring underneath, pulsing when she speaks and shifting colour with her mood.', [0, 0, 50], 'Print flat.')
cover = cyl(98, 3, C, C, BASE_H) - cyl(56, 5, C, C, BASE_H - 1)
for i in range(16):
    w_ = M.extrude(CS([[(62, -5), (90, -9), (90, 9), (62, 5)]]), 5).translate([0, 0, BASE_H - 1]).rotate([0, 0, i * 22.5 + 11.25]).translate([C, C, 0])
    cover = cover - w_
add('pad_cover', 'Holo pad cover', cover, 'Matte black PLA', '#15181e', 'Holo pad',
    'The emitter she stands on, at the centre where all four reflections meet, so her feet land on it whichever side you look from.', [0, 0, 70], 'Print flat.')

# ---------------- pillars ----------------
def pillar(px, py):
    p = cbox(px, py, BASE_H - 8, px + P, py + P, CR0 + 8, 6)
    p = p - box(px - 1, py - 1, BASE_H - 9, px + P + 1, py + P + 1, BASE_H) + box(px + 3.6, py + 3.6, BASE_H - 8, px + P - 3.6, py + P - 3.6, BASE_H)
    p = p - box(px - 1, py - 1, CR0, px + P + 1, py + P + 1, CR0 + 9) + box(px + 3.6, py + 3.6, CR0, px + P - 3.6, py + P - 3.6, CR0 + 8)
    for zz in (BASE_H + 60, BASE_H + 120, BASE_H + 180):
        p = p - (cbox(px - 1, py - 1, zz, px + P + 1, py + P + 1, zz + 2, 6) - cbox(px + 1.2, py + 1.2, zz - 1, px + P - 1.2, py + P - 1.2, zz + 3, 5))
    for (ex, ey, ex1, ey1) in [(px + 13, py - 1, px + 17, py + 2.5), (px + 13, py + P - 2.5, px + 17, py + P + 1), (px - 1, py + 13, px + 2.5, py + 17), (px + P - 2.5, py + 13, px + P + 1, py + 17)]:
        p = p - box(ex, ey, BASE_H + 15, ex1, ey1, CR0 - 15)  # glow channels on every face
    p = p - box(px + 10, py + 10, BASE_H - 9, px + P - 10, py + P - 10, CR0 + 9)
    return p
for k, (px, py) in {'fl': (0, 0), 'fr': (W - P, 0), 'bl': (0, D - P), 'br': (W - P, D - P)}.items():
    add('pil_' + k, f'Pillar, {TN[k[0]]} {TN[k[1]]}', pillar(px, py), 'Matte black PLA', '#1c2028', 'Frame',
        'Corner pillar with glowing edge lines on every face. A 10 mm steel rod runs through it into the base and crown.',
        [(-1 if k[1] == 'l' else 1) * 80, (-1 if k[0] == 'f' else 1) * 80, 0], 'Print upright, no supports.')

# ---------------- crown ----------------
crown = cbox(0, 0, CR0, W, D, TOP, 34)
for rot in range(4):
    bev = M.extrude(CS([[(-1, TOP - 18), (18, TOP + 1), (-1, TOP + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
    crown = crown - bev.translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0])
    crown = crown - (box(C - 13, -1, CR0 + 66, C + 13, SH + 1, CR0 + 84) + cyly(1.6, 6, C - 70, -1, CR0 + 75, seg=16) + cyly(1.6, 6, C + 70, -1, CR0 + 75, seg=16) + box(C - 140, -1, CR0 + 40, C + 140, 1.2, CR0 + 43)).translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0])
crown = crown - cbox(SH, SH, CR0 + 4, W - SH, D - SH, TOP - 4, 31)
# screen window (square image area) + monitor pocket walls
crown = crown - box(C - 236, C - 236, CR0 - 1, C + 236, C + 236, CR0 + 4.1)
mx0, my0 = C - MON[0] / 2, C - MON[1] / 2
crown = crown + (box(mx0 - 5, my0 - 5, CR0 + 4, mx0 + MON[0] + 5, my0 + MON[1] + 5, CR0 + 4 + MON[2] + 3) - box(mx0 - 0.5, my0 - 0.5, CR0 + 3, mx0 + MON[0] + 0.5, my0 + MON[1] + 0.5, CR0 + 60))
for (px, py) in [(0, 0), (W - P, 0), (0, D - P), (W - P, D - P)]:
    crown = crown - box(px + 3.4, py + 3.4, CR0 - 1, px + P - 3.4, py + P - 3.4, CR0 + 9)
    crown = crown + (box(px + 1.5, py + 1.5, CR0 + 4, px + P - 1.5, py + P - 1.5, CR0 + 30) - box(px + 3.4, py + 3.4, CR0, px + P - 3.4, py + P - 3.4, CR0 + 31))
crown = crown - box(mx0 - 0.5, my0 - 0.5, CR0 + 3, mx0 + MON[0] + 0.5, my0 + MON[1] + 0.5, CR0 + 60)
crown = crown - (hexgrid(C - 150, C - 150, C + 150, C + 150, 7, 2, TOP - 5, 7) ^ cyl(150, 10, C, C, TOP - 6))
crown = crown - box(C - 25, D - SH - 1, TOP - 40, C + 25, D + 1, TOP - 24)
for k in (1, 2):
    crown = crown + box(k * T - 3, SH, TOP - 16, k * T + 3, D - SH, TOP - 4) + box(SH, k * T - 3, TOP - 16, W - SH, k * T + 3, TOP - 4)
for k, piece in tiles(crown, CR0 - 2, TOP + 2).items():
    add('crown_' + k, f'Crown tile, {TN[k[0]]} {TN[k[1]]}', piece, 'Matte black PLA', '#1c2028', 'Crown',
        'One of nine crown tiles. Together they hold the square screen face-down over the glass, a camera and two mic holes on each of the four sides, and the exhaust grille.',
        [(-1 if k[1] == 'l' else 1 if k[1] == 'r' else 0) * 50, (-1 if k[0] == 'f' else 1 if k[0] == 'b' else 0) * 50, 150], 'Print upside down (top on the bed).')

# camera brackets (x4) and light bars (x4)
cams, bars = [], []
for rot in range(4):
    cb = box(C - 18, SH, CR0 + 62, C + 18, 22, CR0 + 65) + box(C - 18, SH, CR0 + 62, C - 15, 22, CR0 + 88) + box(C + 15, SH, CR0 + 62, C + 18, 22, CR0 + 88)
    cams.append(cb.translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0]))
    lb = box(C - 118, SH, 198, C + 118, 9, 208) - box(C - 114, 5, 200, C + 114, 10, 206)
    bars.append(lb.translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0]))
add('cams', 'Camera brackets (set of 4)', union(cams), 'Black PETG', '#30394a', 'Crown',
    'One USB camera behind each side window, so she always knows which side you are on and the view on that side is rendered from exactly where you stand.', [0, 0, 190], 'Print flat, all four at once.')
add('bars', 'Light bar diffusers (set of 4)', union(bars), 'Natural / translucent PETG', '#5cc8ff', 'Base',
    'A glowing strip across each side of the pedestal.', [0, 0, -20], 'Print flat, two per plate.')
# monitor clips
clips = []
for (cx, cy, sx, sy) in [(mx0, my0, 1, 1), (mx0 + MON[0], my0, -1, 1), (mx0, my0 + MON[1], 1, -1), (mx0 + MON[0], my0 + MON[1], -1, -1)]:
    c = box(cx - sx * 5, cy + sy * 30, CR0 + 4 + MON[2] + 3, cx + sx * 40, cy + sy * 60, CR0 + 4 + MON[2] + 8) + box(cx + sx * 6, cy + sy * 34, CR0 + 4.2 + MON[2], cx + sx * 36, cy + sy * 56, CR0 + 4 + MON[2] + 3)
    clips.append(c)
add('clips', 'Screen clips (set of 4)', union(clips), 'Black PETG', '#30394a', 'Crown', 'Hold the screen down on its ledge.', [0, 0, 230], 'Print flat, all four at once.')
# electronics tray
et = box(C - 200, D - 230, 16.5, C - 4, D - 70, 19) + box(C + 4, D - 230, 16.5, C + 200, D - 70, 19)
add('etray', 'Electronics trays (pair)', et, 'Matte black PLA', '#4a5260', 'Base', 'Mounts the ESP32, amplifier, USB hub and power board in the back of the pedestal.', [0, 60, -100], 'Print flat.')

# ---------------- bought ----------------
mon = box(mx0, my0, CR0 + 4.2, mx0 + MON[0], my0 + MON[1], CR0 + 4.2 + MON[2])
buy('monitor', 'LG DualUp 28" monitor, face down (481.5 x 549.5 x 45 mm)', mon, '#59616e',
    'A near-square 2560 x 2880 screen. Its middle 466 mm square shows four views of her, one beside each glass face.', [0, 0, 280])
buy('glass', 'Four beam-splitter glass panels', glass_all, '#a8d8ff',
    'Teleprompter glass triangles (about 460 mm base, 310 mm slant, tip cut off) at 45 degrees. Each one shows her from its own side.', [0, 0, 0])
buy('speaker', '4 inch speaker (down-firing) + amplifier', cyl(50, 40, C, C, 110), '#8a6d3b', 'Fires down inside the pedestal and comes out of all four grilles.', [0, 0, -60])
cm = []
for rot in range(4):
    cm.append(box(C - 10, 8, CR0 + 66, C + 10, 14, CR0 + 86).translate([-C, -C, 0]).rotate([0, 0, 90 * rot]).translate([C, C, 0]))
buy('cam', 'USB camera boards (x4)', union(cm), '#9aa4b2', 'Small 1080p cameras, one per side.', [0, 0, 190])
fig = union([cyl(9, 36, C - 8, C, ZB + 4, r2=7), cyl(9, 36, C + 8, C, ZB + 4, r2=7), cyl(9, 62, C - 7, C, ZB + 40, r2=11), cyl(9, 62, C + 7, C, ZB + 40, r2=11),
             cyl(16, 70, C, C, ZB + 100, r2=19), cyl(4, 66, C - 22, C, ZB + 104, r2=5), cyl(4, 66, C + 22, C, ZB + 104, r2=5),
             cyl(5, 12, C, C, ZB + 168), M.sphere(12, 48).scale([0.9, 0.95, 1.15]).translate([C, C, ZB + 186])])
buy('figure', 'Where she appears (about 20 cm, seen from all four sides)', fig, '#5cc8ff',
    'A stand-in shape at the centre of the pyramid. Her real model never goes into these files.', [0, 0, 0])

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
        bb = man.bounding_box(); dims = [round(bb[i + 3] - bb[i]) for i in range(3)]
        e = dict(key=key, printed=is_print, dims=dims, tris=len(tm.faces), **meta[key])
        if is_print:
            dens = 1.27 if 'PETG' in meta[key]['material'] else 1.24
            e['grams'] = round(man.volume() / 1000 * dens * 0.75)
            sd = sorted({'clips': [8, 45, 35], 'cams': [3, 36, 26], 'bars': [6, 10, 236], 'etray': [3, 160, 196]}.get(key, dims))
            e['fits'] = bool(sd[2] <= 256 and sd[1] <= 256)
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
        if a in bought and b in bought:
            continue
        ba, bb_ = allp[a].bounding_box(), allp[b].bounding_box()
        if any(ba[k] > bb_[k + 3] or bb_[k] > ba[k + 3] for k in range(3)):
            continue
        v = (allp[a] ^ allp[b]).volume()
        if v > 1:
            print('CLASH', a, b, round(v))
pr = [s for s in summary if s['printed']]
print(len(pr), 'printed', sum(s['grams'] for s in pr), 'g')
