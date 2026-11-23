"""Cortana turning capsule v1: front-view Pepper's-ghost tower with a 15.6 inch portable screen on a motorised turntable; her image about 31 cm tall.
mm, x width, y depth (front y=0), z up."""
import sys, math, json, os
sys.path.insert(0, os.path.dirname(__file__))
from helpers import *

# 15.6 inch 16:9 portable monitor (typical): body 357 x 223 x 10, picture 344 x 194
MON_L, MON_W, MON_T = 357.0, 223.0, 10.0
SCR_L, SCR_W = 344.0, 194.0
P, SH = 28.0, 3.0
W = 290.0
GY0, GZ0 = 30.0, 110.0
BASE_H = 100.0
OFF = GZ0 - GY0                       # glass plane z = y + OFF
CH_Z1 = GZ0 + SCR_L + 6               # crown underside
SCR_Z = CH_Z1 + 4.2                   # screen face (inside crown floor)
GY1, GZ1 = GY0 + (CH_Z1 - GZ0), CH_Z1 # glass top edge
IMG_Y = SCR_Z - OFF                   # where her image stands
D = math.ceil((IMG_Y + 92) / 4) * 4
TOP = CH_Z1 + 56
FIG_H = 310.0
CX = W / 2
TCX, TCY = W / 2, D / 2               # turntable axis (centre of the footprint)
print('D', D, 'TOP', TOP, 'IMG_Y', IMG_Y)

parts, meta, bought = {}, {}, {}
def add(key, name, man, material, colour, group, note, explode, print_note):
    parts[key] = man
    meta[key] = dict(name=name, material=material, colour=colour, group=group, note=note, explode=explode, print=print_note)
def buy(key, name, man, colour, note, explode):
    bought[key] = man
    meta[key] = dict(name=name, material='Bought part', colour=colour, group='Bought parts (shown for fit)', note=note, explode=explode, print=None)
def cbox(x0, y0, z0, x1, y1, z1, c):
    return prism(chamfer_rect(x0, y0, x1, y1, c), z0, z1 - z0)
NX, NY = 2, 2
TX, TY = W / NX, D / NY
COL = {0: 'left', 1: 'right'}
ROW = {0: 'front', 1: 'back'}
def tiles(man, z0, z1):
    out = {}
    for i in range(NX):
        for j in range(NY):
            out[(i, j)] = man ^ box(i * TX - (1 if i == 0 else 0), j * TY - (1 if j == 0 else 0), z0, (i + 1) * TX + (1 if i == NX - 1 else 0), (j + 1) * TY + (1 if j == NY - 1 else 0), z1)
    return out
def tex(i, j, dz):
    return [(-1 if i == 0 else 1) * 35, (j - 0.5) * 50, dz]
PILLARS = [(0, 0), (W - P, 0), (0, D - P), (W - P, D - P)]

# ---------------- base ----------------
base = cbox(0, 0, 0, W, D, BASE_H, 22)
base = base - M.extrude(CS([[(-1, BASE_H - 14), (14, BASE_H + 1), (-1, BASE_H + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
base = base - cbox(SH, SH, -1, W - SH, D - SH, BASE_H - 4, 19)
base = base - union([cyly(3.0, 8, CX + dx, -1, 44 + dz, seg=6) for dx in range(-56, 57, 8) for dz in range(-22, 23, 8) if (dx / 56.0) ** 2 + (dz / 22.0) ** 2 <= 1.0])
base = base - box(40, -1, 74, W - 40, 4, 79)
base = base - box(40, -1, 14, W - 40, 1.0, 15.5) - box(40, -1, 68, W - 40, 1.0, 69.5)
for side in (-1, W - SH - 1):
    for i in range(10):
        base = base - box(side, D - 230 + i * 16, 24, side + SH + 2, D - 223 + i * 16, 64)
base = base - box(CX - 50, D - SH - 1, 40, CX - 38, D + 1, 46) - box(CX + 20, D - SH - 1, 36, CX + 40, D + 1, 50)
for (px, py) in PILLARS:
    base = base - box(px + 3.4, py + 3.4, BASE_H - 10, px + P - 3.4, py + P - 3.4, BASE_H + 1)
    base = base + (box(px + 1.5, py + 1.5, BASE_H - 30, px + P - 1.5, py + P - 1.5, BASE_H - 4) - box(px + 3.4, py + 3.4, BASE_H - 31, px + P - 3.4, py + P - 3.4, BASE_H))
padclip = box(34, IMG_Y - 90, 0, W - 34, D - 30, 2000)
base = base - (cyl(76, 4, CX, IMG_Y, BASE_H - 3.5) ^ padclip)
for k in range(1, NX):
    base = base + box(k * TX - 3, 46, 3, k * TX + 3, D - SH, 12)
for k in range(1, NY):
    base = base + box(SH, k * TY - 3, 3, W - SH, k * TY + 3, 12)
BOSS = [(20, y) for y in (60, TY * 0.5, TY * 1.5, D - 60)] + [(W - 20, y) for y in (60, TY * 0.5, TY * 1.5, D - 60)] + [(CX - 60, D - 30), (CX + 60, D - 30)]
for (bx, by) in BOSS:
    base = base + (cyl(5, BASE_H - 8, bx, by, 3) - cyl(2.1, BASE_H, bx, by, -1))
for (i, j), piece in tiles(base, -1, BASE_H + 2).items():
    add(f'base_{i}{j}', f'Base tile, {ROW[j]} {COL[i]}', piece, 'Matte black PLA', '#1c2028', 'Base',
        'One of four base tiles. They bolt together on internal ribs; the base holds the speaker, the light bar and the electronics, and sits on the turntable.',
        tex(i, j, -40), 'Print upside down (top deck on the bed). No supports.')
plate = cbox(SH + 0.4, SH + 0.4, 0, W - SH - 0.4, D - SH - 0.4, 2.4, 18.6)
for (bx, by) in BOSS:
    plate = plate - cyl(1.7, 6, bx, by, -1) - cyl(3.2, 1.6, bx, by, -0.1)
plate = plate - cyl(22, 6, TCX, TCY, -1)          # cable hole down to the turntable
for (fx, fy) in [(TCX + 120 * math.cos(math.radians(a)), TCY + 120 * math.sin(math.radians(a))) for a in (45, 135, 225, 315)]:
    plate = plate - cyl(1.7, 6, fx, fy, -1) - cyl(3.2, 1.6, fx, fy, -0.1)   # turntable adapter screws
for i in range(8):
    plate = plate - box(CX - 80 + i * 20, D - 120, -1, CX - 72 + i * 20, D - 40, 5)
for (i, j), piece in tiles(plate, -1, 6).items():
    add(f'plate_{i}{j}', f'Bottom plate, {ROW[j]} {COL[i]}', piece, 'Matte black PLA', '#252a33', 'Base',
        'Closes the base from below; rubber feet sit in the corner plates.', tex(i, j, -110), 'Print flat.')

# ---------------- pillars (4 corners x 2 sections) ----------------
SLOT = 3.6
def pillar(px, py, fx, fy):
    z0, z1 = BASE_H - 10, CH_Z1 + 10
    p = cbox(px, py, z0, px + P, py + P, z1, 7)
    p = p - box(px - 1, py - 1, z0 - 1, px + P + 1, py + P + 1, BASE_H) + box(px + 3.6, py + 3.6, z0, px + P - 3.6, py + P - 3.6, BASE_H)
    p = p - box(px - 1, py - 1, CH_Z1, px + P + 1, py + P + 1, z1 + 1) + box(px + 3.6, py + 3.6, CH_Z1, px + P - 3.6, py + P - 3.6, z1)
    sx = (14, 14 + SLOT) if fx > 0 else (W - 14 - SLOT, W - 14)
    p = p - (box(sx[0], py + 12, BASE_H - 1, sx[1], py + P + 1, CH_Z1 + 1) if fy > 0 else box(sx[0], py - 1, BASE_H - 1, sx[1], py + P - 12, CH_Z1 + 1))
    if fy < 0:
        p = p - (box(px + 12, D - 14 - SLOT, BASE_H - 1, px + P + 1, D - 14, CH_Z1 + 1) if fx > 0 else box(px - 1, D - 14 - SLOT, BASE_H - 1, px + P - 12, D - 14, CH_Z1 + 1))
    else:
        p = p - box(px + 15, py - 1, BASE_H + 20, px + 19, py + 2.5, CH_Z1 - 20)
    zz = BASE_H + 100
    while zz < CH_Z1 - 50:
        p = p - (cbox(px - 1, py - 1, zz, px + P + 1, py + P + 1, zz + 2, 7) - cbox(px + 1.2, py + 1.2, zz - 1, px + P - 1.2, py + P - 1.2, zz + 3, 6))
        zz += 100
    p = p - box(px + 12, py + 12, z0 - 1, px + P - 12, py + P - 12, z1 + 1)
    return p
SEC = [BASE_H - 12, BASE_H + 180, CH_Z1 + 12]
for (px, py) in PILLARS:
    fx = 1 if px == 0 else -1
    fy = 1 if py == 0 else -1
    nm = ('front' if fy > 0 else 'back') + ' ' + ('left' if fx > 0 else 'right')
    p = pillar(px, py, fx, fy)
    for s in range(2):
        add(f'pil_{nm.replace(" ", "_")}_{s}', f'Pillar, {nm}, section {s + 1} of 2', p ^ box(-10, -10, SEC[s], W + 10, D + 10, SEC[s + 1]), 'Matte black PLA', '#1c2028', 'Frame',
            'Corner pillar holding the smoked side panel' + (' and the black back panel' if fy < 0 else ', with a glowing edge line on its front face') + '. An 8 mm steel rod runs through both sections.',
            [fx * -80, fy * -80, s * 20], 'Print upright, no supports.')

# ---------------- reflector rails (each side in 4 pieces) ----------------
L = math.hypot(GY1 - GY0, GZ1 - GZ0)
def rail_bar(xa, xb, inner):
    w = xb - xa
    bar = box(0, 0, -9, w, L, 9)
    bar = bar - (box(w - 9, -1, -1.9, w + 1, L + 1, 1.9) if inner > 0 else box(-1, -1, -1.9, 9, L + 1, 1.9))
    for i in range(1, 8):
        bar = bar - cyl(1.7, 20, w / 2, L * i / 8, -10)
    return bar.rotate([45, 0, 0]).translate([xa, GY0, GZ0])
pcut = union([box(px - 0.5, py - 0.5, 0, px + P + 0.5, py + P + 0.5, 2000) for (px, py) in PILLARS])
for side, (xa, xb, inner) in (('left', (17.6, 40, 1)), ('right', (W - 40, W - 17.6, -1))):
    foot = box(xa, P + 1, BASE_H, xb, P + 28, GZ0 + 6)
    tab = box(xa, GY1 - 20, GZ1 - 14, xb, GY1 + 16, CH_Z1)
    full = (rail_bar(xa, xb, inner) + foot + tab) - pcut - box(-10, -10, CH_Z1, 2000, 2000, 3000) - box(-10, -10, -10, 2000, 2000, BASE_H) - box(40 - 8.5, 0, -1.9, W - 40 + 8.5, L - 3.5, 1.9).rotate([45, 0, 0]).translate([0, GY0, GZ0])
    zs = [BASE_H, GZ0 + 175, CH_Z1 + 1]
    for s in range(2):
        add(f'rail_{side}_{s}', f'Reflector rail, {side}, piece {s + 1} of 2', full ^ box(-10, -10, zs[s], 2000, 2000, zs[s + 1]), 'Matte black PLA', '#2b313c', 'Optics',
            'Holds the edge of the beam-splitter glass at exactly 45 degrees, joined end to end with pins and screws into the side.',
            [(-1 if side == 'left' else 1) * 60, -30, s * 15], 'Print lying on its side, no supports.')

# ---------------- holo pad ----------------
diff = (cyl(75.6, 3.4, CX, IMG_Y, BASE_H - 3.5) - cyl(52, 5, CX, IMG_Y, BASE_H - 4)) ^ padclip
cover = cyl(78, 3, CX, IMG_Y, BASE_H) - cyl(48, 5, CX, IMG_Y, BASE_H - 1)
for i in range(16):
    cover = cover - M.extrude(CS([[(54, -4), (72, -7), (72, 7), (54, 4)]]), 5).translate([0, 0, BASE_H - 1]).rotate([0, 0, i * 22.5 + 11.25]).translate([CX, IMG_Y, 0])
cover = (cover + (cyl(48, 2, CX, IMG_Y, BASE_H) - cyl(42, 3, CX, IMG_Y, BASE_H - 1))) ^ padclip
for k, (pc, nm, mat, col, note, ex) in {
        'pad_diff': (diff, 'Holo pad diffuser', 'Natural / translucent PETG', '#5cc8ff', 'Glows from an LED ring underneath, pulsing with her voice and mood.', [0, 0, 60]),
        'pad_cover': (cover, 'Holo pad cover', 'Matte black PLA', '#15181e', 'The emitter she stands on, exactly where her reflected feet land. Sixteen light windows.', [0, 0, 90])}.items():
    for half, (x0, x1) in (('l', (0, CX)), ('r', (CX, W))):
        add(f'{k}_{half}', nm + (', left half' if half == 'l' else ', right half'), pc ^ box(x0, 0, 0, x1, D, 2000), mat, col, 'Holo pad', note, [ex[0] + (-20 if half == 'l' else 20), ex[1], ex[2]], 'Print flat.')

# ---------------- crown ----------------
crown = cbox(0, 0, CH_Z1, W, D, TOP, 22)
crown = crown - M.extrude(CS([[(-1, TOP - 12), (12, TOP + 1), (-1, TOP + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
crown = crown - cbox(SH, SH, CH_Z1 + 4, W - SH, D - SH, TOP - 4, 19)
my0 = GY0 - (MON_L - SCR_L) / 2
mx0 = CX - MON_W / 2
crown = crown - box(CX - SCR_W / 2 - 2, GY0 - 2, CH_Z1 - 1, CX + SCR_W / 2 + 2, GY0 + SCR_L + 2, CH_Z1 + 4.1)
crown = crown + (box(mx0 - 5, my0 - 5, CH_Z1 + 4, mx0 + MON_W + 5, my0 + MON_L + 5, CH_Z1 + 4 + MON_T + 3) - box(mx0 - 0.5, my0 - 0.5, CH_Z1 + 3, mx0 + MON_W + 0.5, my0 + MON_L + 0.5, TOP - 4))
for (px, py) in PILLARS:
    crown = crown - box(px + 3.4, py + 3.4, CH_Z1 - 1, px + P - 3.4, py + P - 3.4, CH_Z1 + 10)
    crown = crown + (box(px + 1.5, py + 1.5, CH_Z1 + 4, px + P - 1.5, py + P - 1.5, CH_Z1 + 30) - box(px + 3.4, py + 3.4, CH_Z1, px + P - 3.4, py + P - 3.4, CH_Z1 + 31))
crown = crown - box(mx0 - 0.5, my0 - 0.5, CH_Z1 + 3, mx0 + MON_W + 0.5, my0 + MON_L + 0.5, TOP - 4)
crown = crown - box(CX - 10, -1, TOP - 32, CX + 10, SH + 1, TOP - 18)
for dx in (-60, 60):
    crown = crown - cyly(1.5, 6, CX + dx, -1, TOP - 25, seg=16)
crown = crown - box(50, -1, CH_Z1 + 10, W - 50, 1.2, CH_Z1 + 12.5)
crown = crown - (hexgrid(CX - 80, D - 230, CX + 80, D - 40, 6, 2, TOP - 5, 7) ^ box(CX - 80, D - 230, TOP - 6, CX + 80, D - 40, TOP + 1))
crown = crown - box(CX - 20, D - SH - 1, TOP - 34, CX + 20, D + 1, TOP - 22)
for k in range(1, NX):
    crown = crown + box(k * TX - 3, SH, TOP - 12, k * TX + 3, D - SH, TOP - 4)
for k in range(1, NY):
    crown = crown + box(SH, k * TY - 3, TOP - 12, W - SH, k * TY + 3, TOP - 4)
for (i, j), piece in tiles(crown, CH_Z1 - 2, TOP + 2).items():
    add(f'crown_{i}{j}', f'Crown tile, {ROW[j]} {COL[i]}', piece, 'Matte black PLA', '#1c2028', 'Crown',
        'One of four crown tiles. Together they hold the 15.6 inch screen face-down above the glass, the camera, two mic holes and the exhaust grille.',
        tex(i, j, 140), 'Print upside down (top on the bed).')

clips = []
for (cx, cy, sx, sy) in [(mx0, my0, 1, 1), (mx0 + MON_W, my0, -1, 1), (mx0, my0 + MON_L, 1, -1), (mx0 + MON_W, my0 + MON_L, -1, -1)]:
    clips.append(box(cx - sx * 5, cy + sy * 40, CH_Z1 + 4 + MON_T + 3, cx + sx * 40, cy + sy * 75, CH_Z1 + 4 + MON_T + 8) + box(cx + sx * 6, cy + sy * 44, CH_Z1 + 4.2 + MON_T, cx + sx * 36, cy + sy * 71, CH_Z1 + 4 + MON_T + 3))
add('clips', 'Screen clips (set of 4)', union(clips), 'Black PETG', '#30394a', 'Crown', 'Hold the screen down on its ledge.', [0, 0, 220], 'Print flat, all four at once.')
cam = box(CX - 16, SH, TOP - 36, CX + 16, 20, TOP - 33) + box(CX - 16, SH, TOP - 36, CX - 13, 20, TOP - 14) + box(CX + 13, SH, TOP - 36, CX + 16, 20, TOP - 14)
add('cambracket', 'Camera bracket', cam, 'Black PETG', '#30394a', 'Crown', 'Holds a small USB camera behind the front window. It tells the turntable where you are, so she always turns to face you.', [0, -90, 160], 'Print flat.')
lb = box(42, SH, 72, W - 42, 9, 81) - box(46, 5, 74, W - 46, 10, 79)
add('lightbar', 'Light bar diffusers (2 halves)', lb, 'Natural / translucent PETG', '#5cc8ff', 'Base', 'A glowing strip across the front of the base, printed in two halves.', [0, -120, 0], 'Print flat, two halves.')
spb = box(CX - 55, SH, 12, CX + 55, 12, 70) - cyly(38, 20, CX, -1, 44)
add('baffle', 'Speaker baffle', spb, 'Matte black PLA', '#353b46', 'Base', 'Holds a 3 inch full-range speaker behind the front grille.', [0, -110, -10], 'Print flat.')
et = box(28, D - 230, 12.5, CX - 4, D - 50, 15) + box(CX + 4, D - 230, 12.5, W - 28, D - 50, 15)
for (bx, by) in BOSS:
    et = et - cyl(5.6, 10, bx, by, 10)
add('etray', 'Electronics trays (pair)', et, 'Matte black PLA', '#4a5260', 'Base', 'Mount the ESP32, amplifier, USB hub and power board in the back of the base.', [0, 60, -60], 'Print flat.')

# ---------------- bought ----------------
buy('monitor', '15.6 inch portable monitor, face down (about 357 x 223 x 10 mm)', box(mx0, my0, CH_Z1 + 4.2, mx0 + MON_W, my0 + MON_L, CH_Z1 + 4.2 + MON_T), '#59616e',
    'Any slim 15.6 inch portable monitor (USB-C or mini HDMI), as bright as possible. Its long side runs front to back, which sets her height.', [0, 0, 200])
glass = box(40 - 8, 0, -1.5, W - 40 + 8, L - 4, 1.5).rotate([45, 0, 0]).translate([0, GY0, GZ0])
buy('glass', f'Beam-splitter glass, about {round(W - 64)} x {round(L - 4)} mm', glass, '#a8d8ff',
    'Teleprompter glass (about 70/30) at 45 degrees. 3 mm, one sheet.', [0, -20, 0])
for k, x0 in (('sideL', 14), ('sideR', W - 14 - SH)):
    buy(k, 'Smoked acrylic side panel ' + ('left' if k == 'sideL' else 'right'), box(x0, 14, BASE_H, x0 + SH, D - 14, CH_Z1), '#3a4556',
        f'Dark tinted 3 mm acrylic, {round(D - 28)} x {round(CH_Z1 - BASE_H)} mm.', [-140 if k == 'sideL' else 140, 0, 0])
buy('back', 'Matte black back panel', box(14, D - 14 - SH, BASE_H, W - 14, D - 14, CH_Z1), '#0c0f14', 'Black foam board or acrylic.', [0, 120, 0])
buy('speaker', '3 inch speaker + amplifier', cyly(38, 28, CX, 12, 44), '#8a6d3b', 'Behind the front grille.', [0, -110, -10])
buy('cam', 'USB camera board', box(CX - 9, 7, TOP - 31, CX + 9, 13, TOP - 19), '#9aa4b2', 'Small 1080p camera.', [0, -90, 160])
s = FIG_H / 300.0
fig = union([cyl(9 * s, 50 * s, CX - 10 * s, IMG_Y, BASE_H + 6, r2=7 * s), cyl(9 * s, 50 * s, CX + 10 * s, IMG_Y, BASE_H + 6, r2=7 * s),
             cyl(9 * s, 88 * s, CX - 9 * s, IMG_Y, BASE_H + 6 + 50 * s, r2=12 * s), cyl(9 * s, 88 * s, CX + 9 * s, IMG_Y, BASE_H + 6 + 50 * s, r2=12 * s),
             cyl(19 * s, 100 * s, CX, IMG_Y, BASE_H + 6 + 136 * s, r2=23 * s), cyl(4 * s, 100 * s, CX - 29 * s, IMG_Y, BASE_H + 6 + 140 * s, r2=5 * s), cyl(4 * s, 100 * s, CX + 29 * s, IMG_Y, BASE_H + 6 + 140 * s, r2=5 * s),
             cyl(5 * s, 18 * s, CX, IMG_Y, BASE_H + 6 + 234 * s), M.sphere(15 * s, 48).scale([0.9, 0.95, 1.15]).translate([CX, IMG_Y, BASE_H + 6 + 268 * s])])
fig = fig ^ box(0, IMG_Y - 4, 0, W, IMG_Y + 4, 3000)
buy('figure', f'Where her image appears (about {round(FIG_H / 10)} cm)', fig, '#5cc8ff', 'A flat stand-in shape marking where her reflected image stands. Her real model never goes into these files.', [0, 0, 0])


# ---------------- turntable (under the capsule, z < 0) ----------------
def quarters(man, key, name, mat, col, note, ex, pn):
    q = [man ^ box(TCX + (0 if i else -400), TCY + (0 if j else -400), -200, TCX + (400 if i else 0), TCY + (400 if j else 0), 10) for i in (0, 1) for j in (0, 1)]
    add(key, name + ' (4 quarters)', union(q), mat, col, 'Turntable', note, ex, pn)
TZ_DECK = -24.0
ad = cyl(150, 6, TCX, TCY, -6) - cyl(22, 8, TCX, TCY, -7)
ad = ad + (cyl(148, 8, TCX, TCY, -14) - cyl(122, 10, TCX, TCY, -15))           # rests on the bearing's top race
ring = cyl(90, 8, TCX, TCY, -14) - cyl(72, 10, TCX, TCY, -15)
ring = ring + union([box(-3, 0, -14, 3, 7, -6).translate([0, 72 - 7 + 0.01, 0]).rotate([0, 0, i * 5]).translate([TCX, TCY, 0]) for i in range(72)])
teeth_gaps = union([box(-2.2, 0, -15, 2.2, 6.5, -5).translate([0, 72 - 6.5, 0]).rotate([0, 0, i * 5 + 2.5]).translate([TCX, TCY, 0]) for i in range(72)])
ad = ad + (ring - teeth_gaps)
for a in (45, 135, 225, 315):
    fx, fy = TCX + 120 * math.cos(math.radians(a)), TCY + 120 * math.sin(math.radians(a))
    ad = ad - cyl(1.7, 30, fx, fy, -20)
for a in (0, 90, 180, 270):
    fx, fy = TCX + 135 * math.cos(math.radians(a)), TCY + 135 * math.sin(math.radians(a))
    ad = ad - cyl(2.2, 30, fx, fy, -20)
quarters(ad, 'tt_adapter', 'Turntable top plate with ring gear', 'Black PETG', '#30394a',
         'Screws to the bottom of the capsule and to the top race of the bearing. The ring of teeth inside it is what the motor turns.', [0, 0, -50], 'Print flat, ring-gear side up.')
pin = cyl(11.5, 8, TCX + 60, TCY, -14) - union([box(-1.8, 9, -15, 1.8, 13, -5).rotate([0, 0, i * 30]).translate([TCX + 60, TCY, 0]) for i in range(12)]) - cyl(2.6, 10, TCX + 60, TCY, -15)
add('tt_pinion', 'Motor pinion', pin, 'Black PETG', '#30394a', 'Turntable', 'Press-fits on the motor shaft and drives the ring gear about six to one, slow and quiet.', [0, 0, -40], 'Print flat.')
pl = cyl(185, 46, TCX, TCY, -78) + cyl(185, 8, TCX, TCY, -32, r2=178)
pl = pl - cyl(181, 47, TCX, TCY, -79) - cyl(176, 9, TCX, TCY, -32.005) + (cyl(181, 4, TCX, TCY, -28) - cyl(30, 6, TCX, TCY, -29) - cyl(13, 6, TCX + 60, TCY, -29))
for (dx, dy) in [(-15.5, -15.5), (15.5, -15.5), (-15.5, 15.5), (15.5, 15.5)]:
    pl = pl - cyl(1.7, 6, TCX + 60 + dx, TCY + dy, -29)
for a in (0, 90, 180, 270):
    pl = pl - cyl(2.2, 6, TCX + 135 * math.cos(math.radians(a)), TCY + 135 * math.sin(math.radians(a)), -29)
pl = pl - (cyl(186, 3, TCX, TCY, -54) - cyl(183.5, 4, TCX, TCY, -54.5))           # glow line
pl = pl - box(TCX - 14, TCY + 170, -66, TCX + 14, TCY + 190, -56)                 # power + USB in, at the back
for a in range(0, 360, 45):
    bx, by = TCX + 172 * math.cos(math.radians(a + 22.5)), TCY + 172 * math.sin(math.radians(a + 22.5))
    pl = pl + (cyl(5, 46, bx, by, -74) - cyl(2.1, 50, bx, by, -79))
quarters(pl, 'tt_plinth', 'Turntable plinth', 'Matte black PLA', '#1c2028',
         'The fixed round base. It carries the bearing and the motor, with a glowing ring round its waist. The capsule turns above it.', [0, 0, -90], 'Print upside down (deck on the bed). No supports.')
fl = cyl(180.5, 3, TCX, TCY, -78)
for a in range(0, 360, 45):
    bx, by = TCX + 172 * math.cos(math.radians(a + 22.5)), TCY + 172 * math.sin(math.radians(a + 22.5))
    fl = fl - cyl(1.7, 5, bx, by, -79) - cyl(3.2, 1.6, bx, by, -78.1)
for a in (0, 120, 240):
    fl = fl - cyl(8.2, 1.2, TCX + 150 * math.cos(math.radians(a)), TCY + 150 * math.sin(math.radians(a)), -78.1)
for i in range(6):
    fl = fl - box(TCX - 100 + i * 20, TCY - 160, -79, TCX - 92 + i * 20, TCY - 90, -74)
quarters(fl, 'tt_floor', 'Plinth floor', 'Matte black PLA', '#252a33', 'Closes the plinth. Three rubber feet sit in its recesses; vents under the motor driver.', [0, 0, -130], 'Print flat.')
bear = cyl(150, 10, TCX, TCY, TZ_DECK) - cyl(95, 12, TCX, TCY, TZ_DECK - 1)
buy('bearing', '300 mm round turntable bearing', bear, '#9aa4b2', 'A steel ball-bearing "lazy Susan" ring, about 300 mm across and 10 mm thick. Takes the capsule weight.', [0, 0, -70])
mot = box(TCX + 60 - 21, TCY - 21, -51, TCX + 60 + 21, TCY + 21, -28) + cyl(2.5, 22, TCX + 60, TCY, -28)
buy('motor', 'Pancake stepper motor (NEMA 17, 23 mm)', mot, '#59616e', 'Hangs under the plinth deck. Driven by a silent TMC2209 driver, so you will not hear it turn.', [0, 0, -110])
buy('driver', 'Motor driver + ESP32 board', box(TCX - 40, TCY - 130, -74, TCX + 10, TCY - 90, -66), '#2f6f4f', 'Silent stepper driver and controller, plus a hall sensor that finds the home position.', [0, 0, -120])

# ---------------- export ----------------
out = sys.argv[1]
import numpy as np, trimesh
os.makedirs(out + '/stl', exist_ok=True)
scene = trimesh.Scene(); summary = []
SET = {'clips': [8, 45, 35], 'lightbar': [6, 9, 103], 'etray': [3, 180, 118], 'cambracket': None, 'tt_adapter': [150, 150, 14], 'tt_plinth': [185, 185, 54], 'tt_floor': [181, 181, 3]}
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
            sd = sorted(SET.get(key) or dims)
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
        if (a in bought and b in bought) or {a, b} == {'tt_adapter', 'tt_pinion'}:
            continue
        ba, bb_ = allp[a].bounding_box(), allp[b].bounding_box()
        if any(ba[k] > bb_[k + 3] or bb_[k] > ba[k + 3] for k in range(3)):
            continue
        v = (allp[a] ^ allp[b]).volume()
        if v > 1:
            print('CLASH', a, b, round(v))
pr = [x for x in summary if x['printed']]
print(len(pr), 'printed', sum(x['grams'] for x in pr), 'g', 'TOP', TOP, 'D', D)
