"""Cortana desk capsule v1: Pepper's-ghost hologram tower, 620 mm (24.4 in) tall. mm, x width, y depth (front y=0), z up."""
import sys, math, json
sys.path.insert(0, __import__('os').path.dirname(__file__))
from helpers import *

W, D = 300.0, 500.0
BASE_H, CH_Z0, CH_Z1, TOP = 140.0, 140.0, 540.0, 620.0
P = 30.0            # pillar size
SH = 3.0
# optics: glass plane z = y + 120 from (y30, z150) to (y420, z540); screen face-down at z 536 -> image plane y = 416
GY0, GZ0, GY1, GZ1 = 30.0, 150.0, 420.0, 540.0
IMG_Y = 416.0
SCR_Z = 536.0

parts, meta, bought = {}, {}, {}
def add(key, name, man, material, colour, group, note, explode, print_note):
    parts[key] = man
    meta[key] = dict(name=name, material=material, colour=colour, group=group, note=note, explode=explode, print=print_note)
def buy(key, name, man, colour, note, explode):
    bought[key] = man
    meta[key] = dict(name=name, material='Bought part', colour=colour, group='Bought parts (shown for fit)', note=note, explode=explode, print=None)

def chamfer_box(x0, y0, z0, x1, y1, z1, c):
    return prism(chamfer_rect(x0, y0, x1, y1, c), z0, z1 - z0)

# ---------------- base (4 quadrants) ----------------
base = chamfer_box(0, 0, 0, W, D, BASE_H, 24)
# stepped top edge: bevel the front top
bev = M.extrude(CS([[(-1, BASE_H - 14), (14, BASE_H + 1), (-1, BASE_H + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
base = base - bev
base = base - chamfer_box(SH, SH, -1, W - SH, D - SH, BASE_H - 5, 21)        # hollow, 5 mm top deck
# front speaker grille (hex) and glowing light bar slot
grille = hexgrid(90, -2, 210, 2, 7, 2, 0, 1)  # placeholder (replaced below)
spk = union([cyly(3.2, 8, 150 + dx, -1, 62 + dz, seg=6) for dx in range(-56, 57, 8) for dz in range(-24, 25, 8) if (dx * dx) / 56 ** 2 + (dz * dz) / 24 ** 2 <= 1.0])
base = base - spk
base = base - box(40, -1, 106, 260, 4, 112)                                 # light bar slot (diffuser strip behind)
base = base - box(40, -1, 24, 260, 1.0, 25.5) - box(40, -1, 98, 260, 1.0, 99.5)  # panel lines
# side vents
for side in (-1, W - SH - 1):
    for i in range(10):
        base = base - box(side, 300 + i * 14, 30, side + SH + 2, 307 + i * 14, 100)
# rear ports: USB-C (to PC), DC in, switch
base = base - box(110, D - SH - 1, 40, 122, D + 1, 46) - cyly(5.6, 8, 150, D - SH - 1, 43) - box(172, D - SH - 1, 36, 192, D + 1, 50)
# pillar sockets in the top deck (pillars drop 10 mm in)
for (px, py) in [(0, 0), (W - P, 0), (0, D - P), (W - P, D - P)]:
    base = base - box(px + 3.4, py + 3.4, BASE_H - 10, px + P - 3.4, py + P - 3.4, BASE_H + 1)
    base = base + (box(px + 1.5, py + 1.5, BASE_H - 30, px + P - 1.5, py + P - 1.5, BASE_H - 5) - box(px + 3.4, py + 3.4, BASE_H - 31, px + P - 3.4, py + P - 3.4, BASE_H))
# holo pad recess on the deck at the image foot
base = base - (cyl(102, 4, 150, IMG_Y, BASE_H - 3.5) ^ box(32, 300, BASE_H - 4, W - 32, D - 32, BASE_H + 1))
# internal bosses + joint ribs on split lines
for (bx, by) in [(20, 120), (W - 20, 120), (20, 380), (W - 20, 380), (150, 70), (150, D - 30)]:
    base = base + (cyl(5, BASE_H - 8, bx, by, 3) - cyl(2.1, BASE_H, bx, by, -1))
for xx in (147, 150):
    base = base + box(xx, SH, 3, xx + 3, D - SH, 12)
for yy in (247, 250):
    base = base + box(SH, yy, 3, W - SH, yy + 3, 12)
quads = {
    'base_fl': box(-1, -1, -1, 150, 250, BASE_H + 2),
    'base_fr': box(150, -1, -1, W + 1, 250, BASE_H + 2),
    'base_rl': box(-1, 250, -1, 150, D + 1, BASE_H + 2),
    'base_rr': box(150, 250, -1, W + 1, D + 1, BASE_H + 2),
}
qn = {'base_fl': 'Base, front left', 'base_fr': 'Base, front right', 'base_rl': 'Base, rear left', 'base_rr': 'Base, rear right'}
qe = {'base_fl': [-30, -40, -30], 'base_fr': [30, -40, -30], 'base_rl': [-30, 40, -30], 'base_rr': [30, 40, -30]}
for k, c in quads.items():
    piece = base ^ c
    # alignment pin holes on the split faces
    add(k, qn[k], piece, 'Matte black PLA', '#1c2028', 'Base',
        'One quarter of the base. The four quarters bolt together on internal ribs; the base holds the speaker, the light bar, the electronics and the power socket.' if k != 'base_fl' else
        'Front left quarter, with half of the speaker grille and the glowing light bar. The four quarters bolt together on internal ribs.',
        qe[k], 'Print upside down (top deck on the bed). No supports.')

# bottom plates (2)
for k, (x0, x1, y0, y1) in {'plate_fl': (SH + 0.4, 149.8, SH + 0.4, 249.8), 'plate_fr': (150.2, W - SH - 0.4, SH + 0.4, 249.8), 'plate_rl': (SH + 0.4, 149.8, 250.2, D - SH - 0.4), 'plate_rr': (150.2, W - SH - 0.4, 250.2, D - SH - 0.4)}.items():
    pl = prism([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], 0, 3)
    pl = pl - prism(chamfer_rect(-50, -50, W + 50, D + 50, 0), -1, 0.5) if False else pl
    pl = pl ^ chamfer_box(SH + 0.4, SH + 0.4, -1, W - SH - 0.4, D - SH - 0.4, 5, 20.6)
    for (bx, by) in [(20, 120), (W - 20, 120), (20, 380), (W - 20, 380), (150, 70), (150, D - 30)]:
        pl = pl - cyl(1.7, 6, bx, by, -1) - cyl(3.2, 1.6, bx, by, -0.1)
    for (fx, fy) in [(30, 30), (W - 30, 30), (30, D - 30), (W - 30, D - 30)]:
        pl = pl - cyl(6.2, 1.2, fx, fy, -0.1)
    if k[6] == 'r':
        for i in range(9):
            pl = pl - box(60 + i * 20, 300, -1, 68 + i * 20, 440, 5)
    add(k, 'Bottom plate, ' + {'fl': 'front left', 'fr': 'front right', 'rl': 'rear left', 'rr': 'rear right'}[k[6:]], pl, 'Matte black PLA', '#252a33', 'Base',
        'Closes the base. Screws into the bosses; rubber feet sit in the recesses. The rear plate has cooling vents under the electronics.',
        [-30 if k[7] == 'l' else 30, -60 if k[6] == 'f' else 60, -90], 'Print flat.')

# ---------------- pillars (4 corners, each in 2 sections) ----------------
SLOT = 3.6
def pillar(px, py, fx, fy):
    """fx, fy: +1 if pillar sits at min side (inner faces toward +), -1 at max side"""
    p = chamfer_box(px, py, BASE_H - 10, px + P, py + P, CH_Z1 + 10, 6)
    p = p - box(px - 1, py - 1, BASE_H - 11, px + P + 1, py + P + 1, BASE_H) + box(px + 3.6, py + 3.6, BASE_H - 10, px + P - 3.6, py + P - 3.6, BASE_H)
    p = p - box(px - 1, py - 1, CH_Z1, px + P + 1, py + P + 1, CH_Z1 + 11) + box(px + 3.6, py + 3.6, CH_Z1, px + P - 3.6, py + P - 3.6, CH_Z1 + 10)
    # side-panel slot: panel plane x = 12..15.6 (left) or W-15.6..W-12 (right), slot opens toward the other pillar along y
    sx = (12, 12 + SLOT) if fx > 0 else (W - 12 - SLOT, W - 12)
    if fy > 0:
        p = p - box(sx[0], py + 10, BASE_H - 1, sx[1], py + P + 1, CH_Z1 + 1)
    else:
        p = p - box(sx[0], py - 1, BASE_H - 1, sx[1], py + P - 10, CH_Z1 + 1)
    # back-panel slot on rear pillars: plane y = D-15.6..D-12
    if fy < 0:
        if fx > 0:
            p = p - box(px + 10, D - 12 - SLOT, BASE_H - 1, px + P + 1, D - 12, CH_Z1 + 1)
        else:
            p = p - box(px - 1, D - 12 - SLOT, BASE_H - 1, px + P - 10, D - 12, CH_Z1 + 1)
    # glowing edge channel on the outer front face
    if fy > 0:
        p = p - box(px + 13, py - 1, BASE_H + 20, px + 17, py + 2.5, CH_Z1 - 20)
    # Forge rib grooves
    for zz in (BASE_H + 100, BASE_H + 200, BASE_H + 300):
        p = p - (chamfer_box(px - 1, py - 1, zz, px + P + 1, py + P + 1, zz + 2, 6) - chamfer_box(px + 1.2, py + 1.2, zz - 1, px + P - 1.2, py + P - 1.2, zz + 3, 5))
    p = p - box(px + 10, py + 10, BASE_H - 11, px + P - 10, py + P - 10, CH_Z1 + 11)  # cable/rod core 10 mm
    return p
pil = {'fl': pillar(0, 0, 1, 1), 'fr': pillar(W - P, 0, -1, 1), 'rl': pillar(0, D - P, 1, -1), 'rr': pillar(W - P, D - P, -1, -1)}
names = {'fl': 'front left', 'fr': 'front right', 'rl': 'rear left', 'rr': 'rear right'}
pe = {'fl': [-70, -60, 0], 'fr': [70, -60, 0], 'rl': [-70, 60, 0], 'rr': [70, 60, 0]}
MID = 340.0
for k, p in pil.items():
    lo = p ^ box(-10, -10, 0, W + 10, D + 10, MID)
    hi = p ^ box(-10, -10, MID, W + 10, D + 10, 700)
    # spigot joint
    cx_, cy_ = (P / 2 if k[1] == 'l' else W - P / 2), (P / 2 if k[0] == 'f' else D - P / 2)
    add('pil_' + k + '_lo', f'Pillar, {names[k]}, lower', lo, 'Matte black PLA', '#1c2028', 'Frame',
        'Corner pillar holding the smoked side panel' + (' and the black back panel' if k[0] == 'r' else '') + (' with a glowing edge line on its front face' if k[0] == 'f' else '') + '. A 10 mm steel rod runs through all pillar sections for stiffness.',
        pe[k], 'Print upright, no supports.')
    add('pil_' + k + '_hi', f'Pillar, {names[k]}, upper', hi, 'Matte black PLA', '#1c2028', 'Frame',
        'Upper section of the corner pillar; joins the lower section over the steel rod.', [pe[k][0], pe[k][1], 60], 'Print upright, no supports.')

# ---------------- reflector rails (left/right, each 2 pieces) ----------------
def rail(x0, x1):
    L = math.hypot(GY1 - GY0, GZ1 - GZ0)
    r = box(0, -7, -9, L, 7, 9)  # along +u (local x)
    r = r - box(-1, -1.9, -10, L + 1, 1.9, 2)   # glass slot 3.8 wide, 11 deep (opens toward -z local -> toward glass)
    r = r - box(-1, -8, -10, L + 1, 8, -4) if False else r
    for i in range(1, 6):
        r = r - cyl(1.7, 30, L * i / 6, 0, -15) if False else r
    # orient: local x along glass direction (dy,dz) = (1,1)/sqrt2; slot faces inward toward the glass plane centre in x
    r = r.rotate([0, 0, 90])            # local x -> world y
    r = r.rotate([45, 0, 0])            # tilt up 45 deg toward back
    r = r.rotate([0, 0, 0])
    r = r.translate([0, GY0, GZ0])
    return r
def rail_bar(xa, xb, inner_sign):
    L = math.hypot(GY1 - GY0, GZ1 - GZ0)
    w = abs(xb - xa)
    bar = box(0, 0, -8, w, L, 8)                      # x across, y along glass, z normal
    sx0 = 0 if inner_sign > 0 else w - 8
    bar = bar - box(w - 8 if inner_sign > 0 else -1, -1, -1.9, w + 1 if inner_sign > 0 else 8, L + 1, 1.9)  # slot for glass edge (8 deep)
    for i in range(1, 5):
        bar = bar - cyl(1.7, 20, w / 2, L * i / 5, -10)
    bar = bar.rotate([45, 0, 0]).translate([min(xa, xb), GY0, GZ0])
    return bar
railL = rail_bar(15.6, 36, 1)
railR = rail_bar(W - 36, W - 15.6, -1)
# end blocks: foot on the base deck at the front, tab under the crown at the back
footL = box(15.6, 31, BASE_H, 36, 56, GZ0 + 6)
footR = box(W - 36, 31, BASE_H, W - 15.6, 56, GZ0 + 6)
tabL = box(15.6, GY1 - 18, GZ1 - 12, 36, GY1 + 14, CH_Z1)
tabR = box(W - 36, GY1 - 18, GZ1 - 12, W - 15.6, GY1 + 14, CH_Z1)
glass_cut = box(36 - 8, -50, -1.9, W - 36 + 8, 700, 1.9).rotate([45, 0, 0]).translate([0, GY0, GZ0])
fullL = (railL + footL + tabL) - glass_cut - box(-10, -10, CH_Z1, 400, 700, 800) - box(-10, -10, -10, 400, 700, BASE_H)
fullR = (railR + footR + tabR) - glass_cut - box(-10, -10, CH_Z1, 400, 700, 800) - box(-10, -10, -10, 400, 700, BASE_H)
pcut = union([box(px - 0.5, py - 0.5, 0, px + P + 0.5, py + P + 0.5, 800) for (px, py) in [(0, 0), (W - P, 0), (0, D - P), (W - P, D - P)]])
fullL = fullL - pcut
fullR = fullR - pcut
SPLIT_Z = 345.0
for side, fullr, ex in (('L', fullL, -45), ('R', fullR, 45)):
    lo = fullr ^ box(-10, -10, 0, 400, 700, SPLIT_Z)
    hi = fullr ^ box(-10, -10, SPLIT_Z, 400, 700, 800)
    nm = 'left' if side == 'L' else 'right'
    add('rail' + side + '_lo', f'Reflector rail, {nm}, lower', lo, 'Matte black PLA', '#2b313c', 'Optics',
        'Holds the edge of the beam-splitter glass at exactly 45 degrees. The lower piece has a foot that screws to the base deck.', [ex, -20, 0], 'Print lying on its side, no supports.')
    add('rail' + side + '_hi', f'Reflector rail, {nm}, upper', hi, 'Matte black PLA', '#2b313c', 'Optics',
        'Upper half of the 45-degree rail; its tab screws to the underside of the crown.', [ex, -20, 40], 'Print lying on its side, no supports.')

# ---------------- holo pad (cover + diffuser) ----------------
padclip = box(32, 300, 0, W - 32, D - 32, 800)
diff = (cyl(101.6, 3.4, 150, IMG_Y, BASE_H - 3.5) - cyl(70, 5, 150, IMG_Y, BASE_H - 4)) ^ padclip
add('pad_diff', 'Holo pad diffuser', diff, 'Natural / translucent PETG', '#5cc8ff', 'Holo pad',
    'Glows from an LED ring underneath. It pulses when she speaks and changes colour with her mood.', [0, 0, 60], 'Print flat.')
cover = (cyl(104, 3, 150, IMG_Y, BASE_H) - cyl(66, 5, 150, IMG_Y, BASE_H - 1))
for i in range(16):
    a = i * 22.5 + 11.25
    win = M.extrude(CS([[(72, -5), (96, -9), (96, 9), (72, 5)]]), 5).translate([0, 0, BASE_H - 1]).rotate([0, 0, a]).translate([150, IMG_Y, 0])
    cover = cover - win
cover = cover + (cyl(66, 2, 150, IMG_Y, BASE_H) - cyl(58, 3, 150, IMG_Y, BASE_H - 1))
cover = cover ^ padclip
add('pad_cover', 'Holo pad cover', cover, 'Matte black PLA', '#15181e', 'Holo pad',
    'The emitter she stands on, at exactly the spot where her reflected image meets the floor, so her feet land on it. Sixteen light windows over the diffuser.',
    [0, 0, 90], 'Print flat.')

# ---------------- crown (front + rear halves) ----------------
crown = chamfer_box(0, 0, CH_Z1, W, D, TOP, 24)
bevc = M.extrude(CS([[(-1, TOP - 16), (16, TOP + 1), (-1, TOP + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
crown = crown - bevc
crown = crown - chamfer_box(SH, SH, CH_Z1 + 4, W - SH, D - SH, TOP - 4, 21)       # hollow
# monitor pocket from below and screen window
crown = crown - box(25, 20, CH_Z1 - 1, W - 25, 424, CH_Z1 + 4.1)  # window through the floor (screen faces down)
crown = crown + (box(20, 15, CH_Z1 + 4, W - 20, 429, CH_Z1 + 18) - box(25.5, 20.5, CH_Z1 + 3, W - 25.5, 423.5, CH_Z1 + 19))  # monitor cradle walls
for (cx, cy) in [(40, 30), (W - 40, 30), (40, 414), (W - 40, 414)]:
    crown = crown + (cyl(4, 30, cx, cy, CH_Z1 + 18) - cyl(1.7, 40, cx, cy, CH_Z1 + 10)) if False else crown
# pillar sockets
for (px, py) in [(0, 0), (W - P, 0), (0, D - P), (W - P, D - P)]:
    crown = crown - box(px + 3.4, py + 3.4, CH_Z1 - 1, px + P - 3.4, py + P - 3.4, CH_Z1 + 10)
    crown = crown + (box(px + 1.5, py + 1.5, CH_Z1 + 4, px + P - 1.5, py + P - 1.5, CH_Z1 + 30) - box(px + 3.4, py + 3.4, CH_Z1, px + P - 3.4, py + P - 3.4, CH_Z1 + 31))
crown = crown - box(25.5, 20.5, CH_Z1 + 4.0, W - 25.5, 423.5, CH_Z1 + 16)
# camera window front centre + mic holes + status slit
crown = crown - box(138, -1, 584, 162, SH + 1, 600)
for dx in (-60, 60):
    crown = crown - cyly(1.6, 6, 150 + dx, -1, 592, seg=16)
crown = crown - box(70, -1, 560, 230, 1.2, 563)
# top: hex exhaust grille over monitor electronics + panel lines
crown = crown - (hexgrid(90, 300, 210, 460, 6, 2, TOP - 5, 7) ^ box(90, 300, TOP - 6, 210, 460, TOP + 1))
crown = crown - box(20, 120, TOP - 1, W - 20, 121.2, TOP + 1) - box(20, 280, TOP - 1, W - 20, 281.2, TOP + 1)
# rear cable exit
crown = crown - box(130, D - SH - 1, 570, 170, D + 1, 584)
# split ribs
crown = crown + box(SH, 247, TOP - 14, W - SH, 253, TOP - 4) + box(147, SH, TOP - 14, 153, D - SH, TOP - 4)
for k, (x0, x1, y0, y1, nm, ex) in {'crown_fl': (-1, 150, -1, 250, 'front left', [-30, -50, 120]), 'crown_fr': (150, W + 1, -1, 250, 'front right', [30, -50, 120]), 'crown_rl': (-1, 150, 250, D + 1, 'rear left', [-30, 50, 120]), 'crown_rr': (150, W + 1, 250, D + 1, 'rear right', [30, 50, 120])}.items():
    add(k, f'Crown, {nm}', crown ^ box(x0, y0, CH_Z1 - 2, x1, y1, TOP + 2), 'Matte black PLA', '#1c2028', 'Crown',
        ('Front quarter: holds the screen face-down above the reflector, with ' + ('the camera window and a mic hole. ' if True else '') if k[6] == 'f' else 'Rear quarter with half the exhaust grille' + (' and the cable exit. ' if True else '. ')) + 'The four quarters bolt together on centre ribs.',
        ex, 'Print upside down (top on the bed).')

# monitor corner clips (set of 4)
clips = []
for (cx, cy, sx, sy) in [(25.5, 20.5, 1, 1), (W - 25.5, 20.5, -1, 1), (25.5, 423.5, 1, -1), (W - 25.5, 423.5, -1, -1)]:
    c = box(cx - sx * 5, cy + sy * 8, CH_Z1 + 18, cx + sx * 30, cy + sy * 30, CH_Z1 + 22) + box(cx + sx * 6, cy + sy * 6, CH_Z1 + 15.2, cx + sx * 28, cy + sy * 28, CH_Z1 + 18)
    clips.append(c - cyl(1.7, 30, cx - sx * 2.5, cy + sy * 15, CH_Z1 + 10))
add('clips', 'Screen clips (set of 4)', union(clips), 'Black PETG', '#30394a', 'Crown',
    'Screw onto the top of the screen ledge and press the screen down so it cannot shift. Foam pads stop rattles.', [0, 0, 170], 'Print flat, all four at once.')

# camera bracket
cam = box(132, SH, 576, 168, 22, 579) + box(132, SH, 576, 135, 22, 606) + box(165, SH, 576, 168, 22, 606) - box(140, SH - 1, 580, 160, SH + 2, 604)
add('cambracket', 'Camera bracket', cam, 'Black PETG', '#30394a', 'Crown',
    'Holds a small USB camera board behind the front window so she can see and face you.', [0, -80, 140], 'Print flat.')

# electronics tray in the base (rear)
et = box(25, 280, 12.5, 275, 470, 15) - cyl(5.6, 10, 150, D - 30, 10) - cyl(5.6, 10, 20, 380, 10) - cyl(5.6, 10, W - 20, 380, 10)
for (bx, by) in [(35, 290), (110, 290), (35, 460), (110, 460), (190, 290), (265, 290), (190, 460), (265, 460)]:
    et = et + (cyl(3, 6, bx, by, 15) - cyl(1.2, 8, bx, by, 14.5))
et = et + (cyl(42, 4, 150, 120, 3) - cyl(37, 6, 150, 120, 2)) if False else et
add('etray', 'Electronics tray', et, 'Matte black PLA', '#4a5260', 'Base',
    'Mounts the ESP32, the amplifier board, the USB hub and the power board in the rear of the base.', [0, 60, -60], 'Print flat.')

# speaker baffle
spb = box(84, SH, 20, 216, 12, 104) - cyly(38, 20, 150, -1, 62)
for (dx, dz) in [(-44, -36), (44, -36), (-44, 36), (44, 36)]:
    spb = spb - cyly(1.7, 20, 150 + dx, -1, 62 + dz)
add('baffle', 'Speaker baffle', spb, 'Matte black PLA', '#353b46', 'Base',
    'Holds a 3 inch full-range speaker behind the front grille.', [0, -110, -10], 'Print flat.')

# light bar diffuser
lb = box(42, SH, 104, 258, 9, 114) - box(46, 5, 106, 254, 10, 112)
add('lightbar', 'Light bar diffuser', lb, 'Natural / translucent PETG', '#5cc8ff', 'Base',
    'A glowing strip across the front of the base, lit by the same LEDs as the pad.', [0, -120, 0], 'Print flat.')

# ---------------- bought parts ----------------
mon = box(27, 22, CH_Z1 + 4.2, W - 27, 422, CH_Z1 + 15)
buy('monitor', '17.3 inch portable monitor (portrait, face down)', mon, '#59616e',
    'Shows her on pure black. About 400 x 245 mm, USB-C or HDMI, as bright as possible (500 nits or more). Its long side runs front to back so her reflection stands tall.', [0, 0, 150])
scr = box(34, 30, CH_Z1 + 4.1, W - 34, 412, CH_Z1 + 4.3)
buy('screenglow', 'Screen picture area', scr, '#0b1220', 'The 382 x 215 mm picture area.', [0, 0, 150])
glass = box(36 - 7.5, 0, -1.5, W - 36 + 7.5, math.hypot(GY1 - GY0, GZ1 - GZ0) - 4, 1.5).rotate([45, 0, 0]).translate([0, GY0, GZ0])
buy('glass', 'Beam-splitter glass, about 258 x 545 mm', glass, '#a8d8ff',
    'Teleprompter glass (about 70/30) at 45 degrees. It reflects the screen so she appears standing inside, while you still see through it.', [0, -20, 0])
for k, x0 in (('sideL', 12), ('sideR', W - 15.6)):
    buy(k, 'Smoked acrylic side panel ' + ('left' if k == 'sideL' else 'right'), box(x0, 12, BASE_H, x0 + SH, D - 12, CH_Z1), '#3a4556',
        'Dark tinted 3 mm acrylic, 476 x 400 mm. Keeps the inside dark so she looks bright, but you can still see into the capsule.', [-110 if k == 'sideL' else 110, 0, 0])
buy('back', 'Matte black back panel', box(12, D - 15.6, BASE_H, W - 12, D - 12, CH_Z1), '#0c0f14',
    'Black 3 mm foam board or acrylic. The darker the background, the more solid she looks.', [0, 100, 0])
buy('speaker', '3 inch speaker + amplifier', cyly(38, 30, 150, 12, 62), '#8a6d3b', 'Full-range driver behind the grille.', [0, -110, -10])
buy('cam', 'USB camera board', box(140, 8, 582, 160, 14, 602), '#9aa4b2', 'Small 1080p USB camera.', [0, -80, 140])
fig = union([cyl(11, 50, 140, IMG_Y, BASE_H + 8, r2=8), cyl(11, 50, 160, IMG_Y, BASE_H + 8, r2=8),
             cyl(11, 90, 141, IMG_Y, BASE_H + 58, r2=13), cyl(11, 90, 159, IMG_Y, BASE_H + 58, r2=13),
             cyl(22, 120, 150, IMG_Y, BASE_H + 145, r2=26), cyl(5, 120, 120, IMG_Y, BASE_H + 150, r2=6), cyl(5, 120, 180, IMG_Y, BASE_H + 150, r2=6),
             cyl(6, 22, 150, IMG_Y, BASE_H + 262), M.sphere(18, 48).scale([0.9, 0.95, 1.15]).translate([150, IMG_Y, BASE_H + 300])])
fig = fig ^ box(0, IMG_Y - 4, 0, W, IMG_Y + 4, 900)
buy('figure', 'Where her image appears (about 38 cm)', fig, '#5cc8ff',
    'A flat stand-in shape marking where her reflected image stands, right above the holo pad. Her real model never goes into these files.', [0, 0, 0])

# ---------------- export ----------------
out = sys.argv[1]
import numpy as np, trimesh, os
os.makedirs(out + '/stl', exist_ok=True)
scene = trimesh.Scene()
summary = []
for coll, is_print in ((parts, True), (bought, False)):
    for key, man in coll.items():
        if man.is_empty():
            print('EMPTY', key); continue
        mesh = man.to_mesh()
        v = np.asarray(mesh.vert_properties)[:, :3]
        f = np.asarray(mesh.tri_verts)
        tm = trimesh.Trimesh(v, f, process=False)
        scene.add_geometry(tm, node_name=key, geom_name=key)
        bb = man.bounding_box()
        dims = [round(bb[i + 3] - bb[i]) for i in range(3)]
        e = dict(key=key, printed=is_print, dims=dims, tris=len(f), **meta[key])
        if is_print:
            dens = 1.27 if 'PETG' in meta[key]['material'] else 1.24
            e['grams'] = round(man.volume() / 1000 * dens * 0.75)
            s = sorted([12, 35, 35] if key == 'clips' else dims)
            e['fits'] = bool(s[2] <= 256 and s[1] <= 256)
            tm.export(f'{out}/stl/{key}.stl')
        summary.append(e)
scene.export(f'{out}/capsule.glb')
json.dump(summary, open(f'{out}/parts.json', 'w'), indent=1)
for s in summary:
    print(s['key'], s['dims'], s.get('grams'), s.get('fits'))
allp = {**parts, **bought}
pairs = [('monitor', 'crown_fl'), ('monitor', 'crown_rr'), ('monitor', 'clips'), ('glass', 'railL_lo'), ('glass', 'railL_hi'), ('glass', 'railR_lo'), ('glass', 'railR_hi'),
         ('glass', 'base_fl'), ('glass', 'crown_fl'), ('glass', 'crown_rl'), ('glass', 'pil_fl_lo'), ('glass', 'pad_cover'), ('sideL', 'pil_fl_lo'), ('sideL', 'railL_lo'), ('sideR', 'railR_hi'),
         ('back', 'pil_rl_hi'), ('back', 'pad_cover'), ('railL_lo', 'pil_fl_lo'), ('railL_hi', 'crown_rl'), ('railL_hi', 'crown_fl'), ('railL_lo', 'base_fl'), ('speaker', 'base_fl'), ('cam', 'crown_fl'), ('cambracket', 'crown_fl'), ('cambracket', 'crown_fr'), ('etray', 'base_rl'), ('clips', 'crown_fl'), ('figure', 'glass')]
for a, b in pairs:
    v = (allp[a] ^ allp[b]).volume()
    print('CLASH' if v > 1 else 'ok', a, b, round(v))
