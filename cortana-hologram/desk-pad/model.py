"""Cortana desk mist pad v1: parametric printed parts (mm). x = width, y = depth (front y=0), z = up (desk z=0)."""
import json, math, sys
import numpy as np
import manifold3d as mf
from manifold3d import Manifold as M, CrossSection as CS
import trimesh

SEG = 72

def box(x0, y0, z0, x1, y1, z1):
    x0, x1 = min(x0, x1), max(x0, x1); y0, y1 = min(y0, y1), max(y0, y1); z0, z1 = min(z0, z1), max(z0, z1)
    return M.cube([x1 - x0, y1 - y0, z1 - z0]).translate([x0, y0, z0])

def cyl(r, h, x=0, y=0, z=0, seg=SEG, r2=None):
    return M.cylinder(h, r, r if r2 is None else r2, seg).translate([x, y, z])

def cylx(r, h, x, y, z, seg=SEG):  # along +x
    return M.cylinder(h, r, r, seg).rotate([0, 90, 0]).translate([x, y, z])

def cyly(r, h, x, y, z, seg=SEG):  # along +y
    return M.cylinder(h, r, r, seg).rotate([-90, 0, 0]).translate([x, y, z])

def prism(pts, z0, h):
    return M.extrude(CS([pts]), h).translate([0, 0, z0])

def chamfer_rect(x0, y0, x1, y1, c):
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]

def chamfer_front(x0, y0, x1, y1, c):  # chamfer only the two front corners
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1), (x0, y1), (x0, y0 + c)]

def union(parts):
    return M.batch_boolean(parts, mf.OpType.Add) if len(parts) > 1 else parts[0]

def hexgrid(x0, y0, x1, y1, cell, wall, z0, h):
    """hex cell cutters filling a rectangle (flat-to-flat = cell)"""
    r = cell / math.sqrt(3)  # circumradius for flat-to-flat cell
    pitch_x = cell + wall
    pitch_y = (cell + wall) * math.sqrt(3) / 2
    cutters = []
    row = 0
    y = y0
    while y < y1 + cell:
        x = x0 + (pitch_x / 2 if row % 2 else 0)
        while x < x1 + cell:
            cutters.append(cyl(r, h, x, y, z0, seg=6).rotate([0, 0, 0]))
            x += pitch_x
        y += pitch_y
        row += 1
    return union(cutters) ^ box(x0, y0, z0 - 1, x1, y1, z0 + h + 1)

# ---------------- key dimensions ----------------
W, DECK_D, DECK_H = 250.0, 150.0, 75.0          # front deck
TOW_X0, TOW_X1, TOW_Y1, TOW_H = 20.0, 230.0, 285.0, 200.0  # rear emitter tower
CX, CY = 125.0, 62.0                              # mist slot / ring centre
SLOT_L, SLOT_W, GUARD_W, GUARD_OFF = 130.0, 8.0, 5.0, 11.0
CAP_Z0, CAP_Z1 = 380.0, 430.0
SHELL = 3.0

parts = {}
meta = {}

def add(key, name, man, material, colour, group, note, explode, print_note):
    parts[key] = man
    meta[key] = dict(name=name, material=material, colour=colour, group=group, note=note, explode=explode, print=print_note)

# ---------------- 1. front deck shell ----------------
outer = prism(chamfer_front(0, 0, W, DECK_D, 22), 0, DECK_H)
# top bevel: shave front top edge with a 45 deg wedge
wedge = prism([(-1, -1), (-1, 10), (12, -1)], 0, 1).scale([1, 1, 1])
bevel = M.extrude(CS([[(0, -1), (14, -1), (-1, 14 + 0), (-1, 0)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
# bevel profile in (y,z): cut triangle at y<8, z>DECK_H-8
bev = M.extrude(CS([[(-1, DECK_H - 9), (9, DECK_H + 1), (-1, DECK_H + 1)]]), W + 2).rotate([90, 0, 90]).translate([-1, 0, 0])
outer = outer - bev
inner = prism(chamfer_front(SHELL, SHELL, W - SHELL, DECK_D + 5, 22 - 1.2), -1, DECK_H - 4 + 1)
deck = outer - inner
# ring groove (seat for holo ring), 3 mm deep
deck = deck - (cyl(91, 3.2, CX, CY, DECK_H - 3) - cyl(71, 3.4, CX, CY, DECK_H - 3.1))
# through openings for the nozzle (inside ring)
deck = deck - box(CX - 76, CY - 22, DECK_H - 6, CX + 76, CY + 22, DECK_H + 1)
# Forge panel lines on top and front (1 mm grooves)
for yy in (118, 128):
    deck = deck - box(12, yy, DECK_H - 1, W - 12, yy + 1.2, DECK_H + 1)
for xx in (40, 210):
    deck = deck - box(xx, 100, DECK_H - 1, xx + 1.2, 140, DECK_H + 1)
deck = deck - box(40, -1, 18, W - 40, 1.0, 19.2) - box(40, -1, 48, W - 40, 1.0, 49.2)
# front water window (shows tank level) and front status light slot
deck = deck - box(95, -1, 24, 155, SHELL + 1, 42)
deck = deck - box(110, -1, 54, 140, SHELL + 1, 57)
# side intake grilles for guard fans (both sides)
for side in (0, 1):
    for i in range(7):
        z = 14 + i * 7
        if side == 0:
            deck = deck - box(-1, 40, z, SHELL + 1, 95, z + 3.5)
        else:
            deck = deck - box(W - SHELL - 1, 40, z, W + 1, 95, z + 3.5)
# internal screw bosses for bottom plate (M3 heat-set inserts)
for (bx, by) in [(14, 30), (W - 14, 30), (10, 140), (W - 10, 140), (CX, 140)]:
    deck = deck + (cyl(5, DECK_H - 8, bx, by, 4) - cyl(2.1, DECK_H, bx, by, 0))
# rear tongue joining the tower (with two M4 holes)
tongue = box(TOW_X0 + 10, DECK_D - 1, 0, TOW_X1 - 10, DECK_D + 12, 6) - cyl(2.2, 10, 70, DECK_D + 6, -1) - cyl(2.2, 10, 180, DECK_D + 6, -1)
deck = deck + tongue
# tank bay rails
for xx in (34, 213):
    deck = deck + box(xx, 6, 4, xx + 3, 118, 12)
add('deck', 'Front deck shell', deck, 'Matte black PLA', '#1b1f26', 'Shell',
    'Main body. Holds the water tank, mist nozzle, guard fans and holo ring. Front window shows the water level.',
    [0, -40, 0], 'Print upside down (top face on the bed), no supports except the front window bridge.')

# ---------------- 2. bottom plate ----------------
bp = prism(chamfer_front(SHELL + 0.4, SHELL + 0.4, W - SHELL - 0.4, DECK_D - 0.4, 21), 0, 3)
for (bx, by) in [(14, 30), (W - 14, 30), (10, 140), (W - 10, 140), (CX, 140)]:
    bp = bp - cyl(1.7, 5, bx, by, -1) - cyl(3.2, 1.6, bx, by, -0.1)
for (fx, fy) in [(30, 20), (W - 30, 20), (30, 135), (W - 30, 135)]:
    bp = bp - cyl(6.2, 1.2, fx, fy, -0.1)  # rubber foot recesses
# vent slots under electronics + drain/inspection hole under tank
for i in range(8):
    bp = bp - box(20 + i * 7, 120, -1, 23 + i * 7, 142, 5)
bp = bp - cyl(8, 5, CX, 60, -1)
bp = bp + box(30, 10, 3, 31.6, 118, 8) + box(W - 31.6, 10, 3, W - 30, 118, 8)
add('base', 'Bottom plate', bp, 'Matte black PLA', '#23272f', 'Shell',
    'Closes the deck. Screws in with five M3 screws; rubber feet sit in recesses. Lift it off to reach the tank drain.',
    [0, -40, -45], 'Print flat.')

# ---------------- 3. water tank ----------------
TX0, TY0, TX1, TY1, TH = 37.0, 10.0, 213.0, 116.0, 58.0
tank = box(TX0, TY0, 4, TX1, TY1, 4 + TH) - box(TX0 + 2.4, TY0 + 2.4, 4 + 2.4, TX1 - 2.4, TY1 - 2.4, 4 + TH + 1)
tank = tank + (cyl(30, 6, CX, CY, 6.4) - cyl(26.5, 7, CX, CY, 6))  # mister disc cradle ring
tank = tank + (box(TX1 - 30, TY0 + 6, 6.4, TX1 - 6, TY0 + 30, 20) - box(TX1 - 27, TY0 + 9, 6, TX1 - 9, TY0 + 27, 21))  # float switch cage
tank = tank + box(TX0 + 2, TY0 + 2, 4 + TH - 3, TX1 - 2, TY0 + 4.4, 4 + TH)  # lid lip front
tank = tank - box(TX0 + 60, TY0 - 1, 22, TX0 + 116, TY0 + 3, 40)  # front level window opening (sealed with clear sheet)
add('tank', 'Water tank (about 1 litre)', tank, 'PETG (clear or translucent blue)', '#4fa3e0', 'Mist module',
    'Holds about 0.9 L of distilled water. The 24 V mister disc sits in the round cradle, the float switch cuts power if it runs low.',
    [0, -110, -30], 'Print upright, 4 walls, 100% top and bottom for watertightness. Seal inside with a coat of food-safe epoxy if it weeps.')

# ---------------- 4. tank lid with mist chamber ----------------
LZ = 4 + TH
lid = box(TX0 - 1, TY0 - 1, LZ, TX1 + 1, TY1 + 1, LZ + 3)
lid = lid + (box(CX - 66, CY - 18, LZ + 3, CX + 66, CY + 18, LZ + 8.5) - box(CX - 63, CY - 15, LZ, CX + 63, CY + 15, LZ + 9))  # chimney collar
lid = lid - box(CX - 63, CY - 3.5, LZ - 1, CX + 63, CY + 3.5, LZ + 4)  # mist outlet into nozzle
# 40 mm fan port (air in) at right rear corner
lid = lid - cyl(19, 5, 190, 100, LZ - 1)
for (dx, dy) in [(-16, -16), (16, -16), (-16, 16), (16, 16)]:
    lid = lid - cyl(1.6, 5, 190 + dx, 100 + dy, LZ - 1)
# fill cap with screw-on plug at left rear
lid = lid + (cyl(14, 8, 60, 100, LZ + 3) - cyl(11, 10, 60, 100, LZ))
lid = lid - cyl(11, 5, 60, 100, LZ - 1)
# drip return spigot (from collector) at rear centre
lid = lid + (cyl(4.5, 10, 160, 104, LZ + 3) - cyl(2.6, 15, 160, 104, LZ - 1))
lid = lid - cyl(2.6, 5, 160, 104, LZ - 1)
# cable gland for mister cable
lid = lid + (cyl(5, 6, 90, 104, LZ + 3) - cyl(2.5, 10, 90, 104, LZ))
lid = lid - cyl(2.5, 5, 90, 104, LZ - 1)
add('lid', 'Tank lid and mist chamber', lid, 'PETG (black)', '#2a3140', 'Mist module',
    'Seals the tank. A 40 mm fan blows air into the tank so the fog rises out of the long outlet into the nozzle. Has the fill cap, drip return spigot and cable gland.',
    [0, -110, 10], 'Print flat, top up.')

# ---------------- 5. slot nozzle ----------------
NZ0 = LZ + 3.1
NZ1 = DECK_H - 2.0
nz = box(CX - 75, CY - 21, NZ0, CX + 75, CY + 21, NZ1)
# central mist channel: funnels 126x7 inlet up to 130x8 exit
nz = nz - box(CX - SLOT_L / 2, CY - SLOT_W / 2, NZ0 - 1, CX + SLOT_L / 2, CY + SLOT_W / 2, NZ1 + 1)
# guard air channels both sides, fed from side ports
for s in (-1, 1):
    yc = CY + s * GUARD_OFF
    nz = nz - box(CX - SLOT_L / 2, yc - GUARD_W / 2, NZ0 + 2, CX + SLOT_L / 2, yc + GUARD_W / 2, NZ1 + 1)
    nz = nz - box(CX - 75 - 1, yc - GUARD_W / 2, NZ0 + 2, CX - SLOT_L / 2 + 1, yc + GUARD_W / 2, NZ0 + 2 + 6)
    nz = nz - box(CX + SLOT_L / 2 - 1, yc - GUARD_W / 2, NZ0 + 2, CX + 76, yc + GUARD_W / 2, NZ0 + 2 + 6)
# honeycomb insert pocket at the top (depth 15)
nz = nz - box(CX - 67, CY - 16.5, NZ1 - 15, CX + 67, CY + 16.5, NZ1 + 1)
# side duct spigots for guard fan hoses
for sx in (-1, 1):
    xx = CX + sx * 75
    for s in (-1, 1):
        yc = CY + s * GUARD_OFF
        sp = box(min(xx, xx + sx * 8), yc - 4.5, NZ0 + 0.5, max(xx, xx + sx * 8), yc + 4.5, NZ0 + 9.5) - box(min(xx, xx + sx * 9) - 0.5, yc - GUARD_W / 2, NZ0 + 2, max(xx, xx + sx * 9) + 0.5, yc + GUARD_W / 2, NZ0 + 8)
        nz = nz + sp
add('nozzle', 'Slot nozzle', nz, 'PETG (black)', '#30394a', 'Mist module',
    'Turns the fog into a thin flat sheet: a 130 x 8 mm mist slot with a 5 mm guard-air slot each side, which keep the sheet straight so her image stays sharp.',
    [0, -60, 40], 'Print upright. Fine 0.12 mm layers on the slot lips.')

# ---------------- 6. honeycomb straightener ----------------
hz0 = NZ1 - 15
hc = box(CX - 66.6, CY - 16.1, hz0, CX + 66.6, CY + 16.1, NZ1)
hc = hc - hexgrid(CX - 65, CY - 15, CX + 65, CY + 15, 3.0, 0.6, hz0 - 1, 17)
hc = hc + box(CX - 66.6, CY - 16.1, hz0, CX + 66.6, CY - 14.4, NZ1) + box(CX - 66.6, CY + 14.4, hz0, CX + 66.6, CY + 16.1, NZ1)
hc = hc + box(CX - 66.6, CY - 16.1, hz0, CX - 65, CY + 16.1, NZ1) + box(CX + 65, CY - 16.1, hz0, CX + 66.6, CY + 16.1, NZ1)
# solid divider walls between mist and guard zones
for s in (-1, 1):
    hc = hc + box(CX - 65, CY + s * 6.7 - 0.6, hz0, CX + 65, CY + s * 6.7 + 0.6, NZ1)
add('honey', 'Honeycomb straightener', hc, 'PETG (black)', '#3b4558', 'Mist module',
    'Thousands of 3 mm hex tubes that calm the air so the mist leaves as a smooth sheet (it replaces the drinking straws). Drops into the top of the nozzle.',
    [0, -60, 75], 'Print upright, 0.12 mm layers.')

# ---------------- 7. guard fan ducts (pair) ----------------
ducts = []
for sx in (-1, 1):
    fx = 14 if sx < 0 else W - 14 - 0  # fan sits against side grille
    xo = 4 if sx < 0 else W - 4 - 44
    housing = box(xo, 45, 12, xo + 44, 89, 56) - box(xo + 2, 47, 10, xo + 42, 87, 54)
    housing = housing - box(xo - 1 if sx < 0 else xo + 41, 47, 14, xo + 3 if sx < 0 else xo + 45, 87, 54)
    for (dy, dz) in [(-16, -16), (16, -16), (-16, 16), (16, 16)]:
        housing = housing - cylx(1.6, 50, xo - 2, 67 + dy, 34 + dz)
    # transition up to the nozzle ports
    x_in = xo + 44 if sx < 0 else xo
    x_out = CX - 83 if sx < 0 else CX + 83
    xa, xb = (x_in, x_out) if sx < 0 else (x_out, x_in)
    tube = box(xa - 0.5, CY - 18, NZ0 - 1, xb + 0.5, CY + 18, NZ0 + 11) - box(xa - 1, CY - 16, NZ0 + 1, xb + 1, CY + 16, NZ0 + 9)
    # vertical plenum between housing and tube
    px0 = xo + 44 if sx < 0 else xo - 6
    plen = box(px0, CY - 18, NZ0 - 1, px0 + 6, 89, 56) - box(px0 + 1.5, CY - 16, NZ0 + 1, px0 + 4.5, 87, 54)
    ducts += [housing, tube, plen]
duct = union(ducts)
add('ducts', 'Guard fan housings and ducts (pair)', duct, 'Matte black PLA', '#2b2f38', 'Mist module',
    'Each holds a quiet 40 mm fan behind the side grille and channels its air into the nozzle\'s guard slots.',
    [0, -60, 10], 'Print as two mirrored pieces.')

# ---------------- 8. holo ring diffuser + cover ----------------
RZ = DECK_H - 3
diff = cyl(90.6, 5, CX, CY, RZ) - cyl(71.4, 7, CX, CY, RZ - 1)
diff = diff - (cyl(86, 3.5, CX, CY, RZ - 0.1) - cyl(76, 4, CX, CY, RZ - 0.5))  # LED strip channel underneath
add('diffuser', 'Holo ring diffuser', diff, 'Translucent / natural PETG', '#5cc8ff', 'Holo ring',
    'Glows from the LED strip (26 addressable LEDs) sitting in the channel underneath. It pulses with her voice and shifts colour with her mood.',
    [0, 0, 120], 'Print flat.')

cover = cyl(94, 3.2, CX, CY, DECK_H) - cyl(68, 4, CX, CY, DECK_H - 1)
# 12 Forge-style segment windows + inner notch ring
for i in range(12):
    a = i * 30 + 15
    win = M.extrude(CS([[(72.5, -6), (86.5, -9), (86.5, 9), (72.5, 6)]]), 5).translate([0, 0, DECK_H - 1]).rotate([0, 0, a]).translate([CX, CY, 0])
    cover = cover - win
for i in range(12):
    a = i * 30
    tick = box(88.5, -1.2, DECK_H + 2.2, 94.5, 1.2, DECK_H + 4).rotate([0, 0, a]).translate([CX, CY, 0])
    cover = cover - tick
cover = cover + (cyl(94, 1.2, CX, CY, DECK_H + 3.2) - cyl(91, 2, CX, CY, DECK_H + 3))
add('cover', 'Holo ring cover', cover, 'Matte black PLA', '#15181e', 'Holo ring',
    'Forge-style emitter ring she stands on: twelve light windows over the diffuser and raised outer lip, like a desk-size version of her in-game hologram pad.',
    [0, 0, 150], 'Print flat.')

# ---------------- 9. rear emitter tower ----------------
tw_out = prism(chamfer_rect(TOW_X0, DECK_D, TOW_X1, TOW_Y1, 18), 0, TOW_H)
def slope_wedge(off):
    k = 70.0 / 55.0
    dz = off * math.sqrt(1 + k * k)
    zl = lambda y: TOW_H - 70 + k * (y - DECK_D) - dz
    ytop = DECK_D + (TOW_H + 10 - (TOW_H - 70) + dz) / k
    pts = []
    for x in (-5, W + 5):
        pts += [(x, DECK_D - 5, zl(DECK_D - 5)), (x, DECK_D - 5, TOW_H + 10), (x, ytop, TOW_H + 10)]
    return M.hull_points(pts)
tw_out = tw_out - slope_wedge(0)
hollow = prism(chamfer_rect(TOW_X0 + SHELL, DECK_D + SHELL, TOW_X1 - SHELL, TOW_Y1 - SHELL, 16.8), -1, TOW_H - 4 + 1) - slope_wedge(4)
tower = tw_out - hollow
# emitter window on the slope (120 x 46), with glass rebate
ang = math.degrees(math.atan2(70, 55))
win = box(-60, -30, -10, 60, 30, 10).rotate([ang, 0, 0]).translate([CX, 0, 0])
mid_y, mid_z = DECK_D + 27.5 + 3, TOW_H - 35 + 3.8
tower = tower - win.translate([0, mid_y, mid_z])
reb = box(CX - 64, -27, 0, CX + 64, 27, 1.6).rotate([ang - 90 + 0, 0, 0])
tower = tower - box(CX - 64, -27, -0.2, CX + 64, 27, 1.8).rotate([-(90 - ang) + 90 - 90, 0, 0]).translate([0, 0, 0]).rotate([0, 0, 0]).translate([0, mid_y, mid_z]) if False else tower
tower = tower - box(-63, -33, -0.9, 63, 33, 0.9).rotate([ang, 0, 0]).translate([CX, mid_y - 4.5 * (-70 / 89.0), mid_z - 4.5 * (55 / 89.0)])
# louvre vents on both sides (projector cooling) and rear port panel opening
for side in (TOW_X0 - 1, TOW_X1 - SHELL - 1):
    for i in range(9):
        z = 25 + i * 9
        tower = tower - box(side, 200, z, side + SHELL + 2, 265, z + 4.5)
tower = tower - box(70, TOW_Y1 - SHELL - 1, 12, 180, TOW_Y1 + 1, 52)
# spine socket on top (rear centre)
tower = tower - box(CX - 21, 248, TOW_H - 5, CX + 21, 278, TOW_H + 1)
tower = tower + (box(CX - 25, 244, TOW_H - 12, CX + 25, 282, TOW_H - 4) - box(CX - 21, 248, TOW_H - 13, CX + 21, 278, TOW_H))
# drip tube + cable pass-through down to the deck
tower = tower - cyl(4, 40, 210, 273, TOW_H - 40)
# panel lines
for zz in (60, 120):
    for (x0, x1, y0, y1) in [(TOW_X0 - 1, TOW_X0 + 1, 170, TOW_Y1 - 10), (TOW_X1 - 1, TOW_X1 + 1, 170, TOW_Y1 - 10)]:
        tower = tower - box(x0, y0, zz, x1, y1, zz + 1.2)
# underside lip that the deck tongue bolts to
tower = tower + (box(TOW_X0 + 8, DECK_D, 6, TOW_X1 - 8, DECK_D + 14, 10) - cyl(2.2, 20, 70, DECK_D + 6, 0) - cyl(2.2, 20, 180, DECK_D + 6, 0))
add('tower', 'Rear emitter tower', tower, 'Matte black PLA', '#1b1f26', 'Shell',
    'Holds the projector standing lens-up and the angled mirror behind a glass window. The light leaves the sloped front face and crosses the mist sheet from behind, so you never look into the lens.',
    [0, 60, 0], 'Print upright. The slope needs no supports.')

# ---------------- 10. projector cradle ----------------
cr = box(CX - 40, 164.5, 5, CX + 40, 281.5, 9)  # sled base
for xx in (CX - 35, CX + 29):
    cr = cr + (box(xx, 170, 5, xx + 6, 278, 112) - box(xx - 1, 182, 30, xx + 7, 266, 100))  # side cheeks with strap windows
for (y0_, y1_) in ((164.5, 166.5), (279.5, 281.5)):
    cr = cr + box(CX - 29, y0_, 5, CX + 29, y1_, 22)  # end stops (1.5 mm play)
for k in range(6):  # adjustment slots
    cr = cr - box(CX - 22, 172 + k * 18, 4, CX + 22, 178 + k * 18, 10)
cr = cr + box(CX - 40, 164.5, 2.5, CX - 32, 281.5, 5) + box(CX + 32, 164.5, 2.5, CX + 40, 281.5, 5)
add('cradle', 'Projector cradle', cr, 'Matte black PLA', '#353b46', 'Optics',
    'Adjustable sled for the projector, standing lens-up. Velcro straps through the side windows; the slots let it slide to centre the image. Sized for the Optoma ML1050STi (112 x 123 x 57 mm) standing lens-up, turned sideways so her image comes out portrait, with end stops so it cannot slide.',
    [0, 80, 70], 'Print flat.')

# ---------------- 11. tilt mirror mount ----------------
mm_ = box(CX - 28, -3, -33, CX + 28, 3, 33)  # mirror backing plate (holds 110x50 front-surface mirror)
mm_ = mm_ - box(CX - 25, -4, -30, CX + 25, -1.5, 30)
mm_ = mm_ + box(CX - 32, -3, -4, CX - 28, 3, 4) + box(CX + 28, -3, -4, CX + 32, 3, 4)
mm_ = mm_ + cylx(3, 6, CX - 38, 0, 0) + cylx(3, 6, CX + 32, 0, 0)  # trunnions
arm = box(CX - 6, 3, -31, CX + 6, 14, -25)
mm_ = mm_ + arm - cyly(1.7, 20, CX, 0, -28)  # tilt screw lug
mm_ = mm_.rotate([45, 0, 0]).translate([0, 226, 158])
yoke = []
for xx in (CX - 44, CX + 36):
    y = box(xx, 214, 140, xx + 8, 256, 196) - cylx(3.2, 10, xx - 1, 226, 158) + box(xx - 4, 222, 192, xx + 12, 256, 196)
    yoke.append(y)
mirror_mount = union([mm_] + yoke)
add('mirror', 'Tilting mirror mount', mirror_mount, 'Matte black PLA', '#404857', 'Optics',
    'Holds a 60 x 50 mm front-surface mirror at 45 degrees with a thumb-screw for about plus or minus 10 degrees of tilt, so the image can be aimed up or down the mist sheet.',
    [0, 80, 120], 'Print flat.')

# ---------------- 12. electronics tray ----------------
et = box(25, 165, 4, 57, 275, 6.5)
for (bx, by) in [(29, 172), (53, 172), (29, 233), (53, 233)]:
    et = et + (cyl(3, 6, bx, by, 6.5) - cyl(1.2, 8, bx, by, 6))
et = et + box(25, 165, 6.5, 27, 240, 26)
et2 = box(193, 165, 4, 225, 240, 6.5)
for (bx, by) in [(197, 172), (221, 172), (197, 233), (221, 233)]:
    et2 = et2 + (cyl(3, 6, bx, by, 6.5) - cyl(1.2, 8, bx, by, 6))
et2 = et2 + box(223, 165, 6.5, 225, 240, 26)
et = et + et2
add('etray', 'Electronics trays', et, 'Matte black PLA', '#4a5260', 'Electronics',
    'Two slim trays either side of the projector. Left: ESP32 (or Pico W) and a compact 4-channel MOSFET board, one behind the other. Right: the 24 V to 12 V and 5 V step-down converters. Cooled through the tower louvres.',
    [0, 80, 40], 'Print flat.')

# ---------------- 13. rear port panel ----------------
pp = box(68, TOW_Y1 - SHELL - 2.2, 10, 182, TOW_Y1 - SHELL, 54)
pp = pp + box(71, TOW_Y1 - SHELL, 13, 179, TOW_Y1 + 0.0, 51)
pp = pp - box(80, TOW_Y1 - 10, 22, 98, TOW_Y1 + 5, 30)        # HDMI
pp = pp - box(106, TOW_Y1 - 10, 23, 116, TOW_Y1 + 5, 27)       # USB-C
pp = pp - cyly(5.6, 15, 132, TOW_Y1 - 10, 26)                  # DC 24 V barrel
pp = pp - box(148, TOW_Y1 - 10, 20, 168, TOW_Y1 + 5, 32)       # rocker switch
for i in range(6):
    pp = pp - box(80 + i * 15, TOW_Y1 - 10, 38, 88 + i * 15, TOW_Y1 + 5, 46)
add('ports', 'Rear port panel', pp, 'Matte black PLA', '#2b2f38', 'Electronics',
    'HDMI in, USB-C (control link to her box), 24 V power socket and a rocker switch, plus vents.',
    [0, 110, 0], 'Print flat.')

# ---------------- 14. spine + arm ----------------
SZ0 = TOW_H - 4
sp_shape = chamfer_rect(CX - 20.6, 248.4, CX + 20.6, 277.6, 7)
spine = prism(sp_shape, SZ0, CAP_Z1 - 10 - SZ0)
spine = spine - prism(chamfer_rect(CX - 17, 252, CX + 17, 274, 5), SZ0 - 1, CAP_Z1 - 10 - SZ0 - 6)  # hollow (cables + drip tube)
# front groove line + glowing strip channel
spine = spine - box(CX - 3, 247, SZ0 + 30, CX + 3, 250, CAP_Z1 - 40)
# arm reaching forward to the cap (tapered)
arm_prof = [(277.6, CAP_Z1 - 10), (277.6, CAP_Z1 + 2), (150, CAP_Z1 + 2), (120, CAP_Z1 - 6), (120, CAP_Z1 - 18), (248.4, CAP_Z1 - 40), (248.4, CAP_Z1 - 10)]
armm = M.extrude(CS([arm_prof]), 34).rotate([90, 0, 90]).translate([CX - 17, 0, 0])
armm = armm - M.extrude(CS([[(273, CAP_Z1 - 12), (273, CAP_Z1 - 2), (150, CAP_Z1 - 2), (128, CAP_Z1 - 8), (128, CAP_Z1 - 15), (252, CAP_Z1 - 34), (252, CAP_Z1 - 12)]]), 26).rotate([90, 0, 90]).translate([CX - 13, 0, 0])
spine = spine + armm
spine = spine - cyl(2.2, 20, CX, 135, CAP_Z1 - 12)  # M4 into cap
add('spine', 'Spine and cap arm', spine, 'Matte black PLA', '#1b1f26', 'Collector',
    'Carries the collector cap over her head. Hollow inside for the drip-return tube and the cap\'s fan and Peltier wires; the front groove holds a thin LED line.',
    [0, 120, 60], 'Print lying on its side. Optional 10 mm aluminium tube inside for stiffness.')

# ---------------- 15. collector cap ----------------
cap_shape = chamfer_rect(CX - 100, CY - 45, CX + 100, CY + 78, 26)
cap = prism(cap_shape, CAP_Z0, CAP_Z1 - CAP_Z0)
cap = cap - prism(chamfer_rect(CX - 97, CY - 42, CX + 97, CY + 75, 24.8), CAP_Z0 - 1, CAP_Z1 - CAP_Z0 - 3 + 1)
# bottom face with intake slot over the sheet (160 x 46)
cap = cap + (prism(cap_shape, CAP_Z0, 3) - box(CX - 80, CY - 23, CAP_Z0 - 1, CX + 80, CY + 23, CAP_Z0 + 4))
# angled lower bevel all round
# top exhaust grille over the fan (60 mm), hex pattern
cap = cap - (hexgrid(CX - 28, CY + 22, CX + 28, CY + 70, 5, 1.6, CAP_Z1 - 4, 6) ^ cyl(29, 10, CX, CY + 46, CAP_Z1 - 5))
for (dx, dy) in [(-25, -25), (25, -25), (-25, 25), (25, 25)]:
    cap = cap + (cyl(3.2, 6, CX + dx, CY + 46 + dy, CAP_Z1 - 9) - cyl(1.6, 10, CX + dx, CY + 46 + dy, CAP_Z1 - 10))
# panel line ring + emitter edge strip slot
cap = cap - box(CX - 100.5, CY - 20, CAP_Z0 + 20, CX - 99, CY + 50, CAP_Z0 + 22) - box(CX + 99, CY - 20, CAP_Z0 + 20, CX + 100.5, CY + 50, CAP_Z0 + 22)
cap = cap - box(CX - 60, CY - 46, CAP_Z0 + 8, CX + 60, CY - 43.5, CAP_Z0 + 11)
# arm socket
cap = cap + (box(CX - 19, CY + 50, CAP_Z1 - 14, CX + 19, CY + 79, CAP_Z1 - 3)) - cyl(2.2, 20, CX, 135, CAP_Z1 - 20)
add('cap', 'Collector cap', cap, 'Matte black PLA', '#1b1f26', 'Collector',
    'Sits over her head and catches the rising mist. A 60 mm fan pulls it in over a Peltier-cooled plate; the condensed water drips into the tray and runs back down the spine to the tank.',
    [0, 0, 120], 'Print upside down.')

# ---------------- 16. cold plate tray ----------------
ct = box(CX - 80, CY - 32, CAP_Z0 + 4, CX + 80, CY + 40, CAP_Z0 + 7)
ct = ct + box(CX - 80, CY - 32, CAP_Z0 + 4, CX + 80, CY - 30, CAP_Z0 + 12) + box(CX - 80, CY + 38, CAP_Z0 + 4, CX + 80, CY + 40, CAP_Z0 + 12)
ct = ct - box(CX - 70, CY - 22, CAP_Z0 + 3, CX + 70, CY + 28, CAP_Z0 + 8)  # open grid for mist flow
for i in range(13):
    ct = ct + box(CX - 70 + i * 11.4, CY - 22, CAP_Z0 + 4, CX - 68.5 + i * 11.4, CY + 28, CAP_Z0 + 7)
# sloped gutter to the rear outlet + spigot
ct = ct + box(CX - 80, CY + 30, CAP_Z0 + 4, CX + 80, CY + 38, CAP_Z0 + 6)
ct = ct + (cyl(3.5, 10, CX + 35, CY + 40, CAP_Z0 + 4).rotate([0, 0, 0]) - cyl(2, 12, CX + 35, CY + 40, CAP_Z0 + 3))
add('tray', 'Cold plate and drip tray', ct, 'PETG (black)', '#30394a', 'Collector',
    'Carries the aluminium cold plate (Peltier TEC1-12706 underneath, heatsink above). Its gutter collects the drips and feeds the return tube.',
    [0, 0, 70], 'Print flat.')

# ---------------- bought parts (shown for fit, not printed) ----------------
bought = {}
def buy(key, name, man, colour, note):
    bought[key] = man
    meta[key] = dict(name=name, material='Bought part', colour=colour, group='Bought parts (shown for fit)', note=note, explode=meta.get(key, {}).get('explode', [0, 0, 0]), print=None)

proj = box(CX - 28.5, 167, 9, CX + 28.5, 279, 132) + cyl(14, 4, CX, 223, 132)
buy('projector', 'Optoma ML1050STi projector', proj, '#7d8898', '0.8 throw, 535 ANSI lumens, 112 x 123 x 57 mm, about 0.4 kg. Stands lens-up; the mirror turns its light forward through the window.')
meta['projector']['explode'] = [0, 80, 70]
mir = box(CX - 25, -1.5, -30, CX + 25, 0, 30).rotate([45, 0, 0]).translate([0, 226, 158])
buy('mirrorglass', 'Front-surface mirror 60 x 50 mm', mir, '#d9e6f2', 'Avoids the double image a normal mirror gives.')
meta['mirrorglass']['explode'] = [0, 80, 120]
fan40 = []
for xo in (6, W - 6 - 40):
    fan40.append(box(xo, 47, 14, xo + 10, 87, 54))
fan40.append(box(170, 80, LZ + 3, 210, 120, LZ + 13))
buy('fans', '40 mm fans (x3)', union(fan40), '#8a6d3b', 'Two guard fans plus the tank air fan. Quiet 12 V (Noctua NF-A4x10 class).')
meta['fans']['explode'] = [0, -60, 10]
disc = cyl(26, 10, CX, CY, 7)
buy('disc', '24 V ultrasonic mister', disc, '#c9ced6', 'Sits in the tank cradle.')
meta['disc']['explode'] = [0, -110, -30]
fan60 = box(CX - 30, CY + 16, CAP_Z1 - 18, CX + 30, CY + 76, CAP_Z1 - 3.5) - cyl(27, 30, CX, CY + 46, CAP_Z1 - 25)
plate = box(CX - 60, CY - 20, CAP_Z0 + 7, CX + 60, CY + 26, CAP_Z0 + 10) + box(CX - 20, CY - 20, CAP_Z0 + 10, CX + 20, CY + 20, CAP_Z0 + 14)
buy('collector_hw', '60 mm fan, Peltier and cold plate', fan60 + plate, '#9aa4b2', 'Collector hardware.')
meta['collector_hw']['explode'] = [0, 0, 100]
nrm = (-70 / 89.0, 55 / 89.0)
glass = box(-62, -32, -0.75, 62, 32, 0.75).rotate([ang, 0, 0]).translate([CX, mid_y - 4.5 * nrm[0], mid_z - 4.5 * nrm[1]])
buy('glass', 'Clear window, 124 x 64 mm', glass, '#a8d8ff', 'Keeps mist off the mirror and projector. 2 mm acrylic or glass.')
meta['glass']['explode'] = [0, 60, 0]

# her figure (placeholder silhouette made of simple shapes, NOT her model)
fig = union([cyl(9, 40, CX - 8, CY, DECK_H + 4, r2=7), cyl(9, 40, CX + 8, CY, DECK_H + 4, r2=7),
             cyl(9, 70, CX - 7, CY, DECK_H + 44, r2=11), cyl(9, 70, CX + 7, CY, DECK_H + 44, r2=11),
             cyl(17, 98, CX, CY, DECK_H + 110, r2=21),
             cyl(5, 18, CX, CY, DECK_H + 204), cyl(4, 92, CX - 24, CY, DECK_H + 112, r2=5).rotate([0, 0, 0]), cyl(4, 92, CX + 24, CY, DECK_H + 112, r2=5), M.sphere(14, 48).scale([0.9, 0.95, 1.15]).translate([CX, CY, DECK_H + 236])])
fig = fig ^ box(CX - 60, CY - 4, 0, CX + 60, CY + 4, 600)  # flatten: she's an image on a sheet
buy('figure', 'Her image on the mist sheet (25 cm)', fig, '#5cc8ff', 'A flat stand-in silhouette showing where her projected image appears. Her real model never goes into these files.')
meta['figure']['explode'] = [0, 0, 0]
sheet = box(CX - SLOT_L / 2, CY - 1.5, DECK_H + 1, CX + SLOT_L / 2, CY + 1.5, CAP_Z0)
buy('sheet', 'Mist sheet', sheet, '#bfe9ff', 'The thin curtain of fog (shown as a pane).')
meta['sheet']['explode'] = [0, 0, 0]

# ---------------- export ----------------
out = sys.argv[1]
scene = trimesh.Scene()
summary = []
for coll, is_print in ((parts, True), (bought, False)):
    for key, man in coll.items():
        mesh = man.to_mesh()
        v = np.asarray(mesh.vert_properties)[:, :3]
        f = np.asarray(mesh.tri_verts)
        tm = trimesh.Trimesh(v, f, process=False)
        col = meta[key]['colour'].lstrip('#')
        rgba = [int(col[i:i + 2], 16) for i in (0, 2, 4)] + [255]
        scene.add_geometry(tm, node_name=key, geom_name=key)
        if man.is_empty(): print("EMPTY", key); continue
        lo, hi = man.bounding_box()[:3], man.bounding_box()[3:]
        dims = [round(hi[i] - lo[i]) for i in range(3)]
        entry = dict(key=key, printed=is_print, dims=dims, tris=len(f), **meta[key])
        if is_print:
            vol = man.volume() / 1000.0  # cm3
            dens = 1.27 if 'PETG' in meta[key]['material'] else 1.24
            grams = vol * dens * 0.75  # thin-walled parts print near solid; 75% fill factor
            entry['grams'] = round(grams)
            fits = sorted(dims)[2] <= 256 and sorted(dims)[1] <= 256
            entry['fits'] = bool(fits)
            if not man.status() == mf.Error.NoError:
                print('BAD', key, man.status())
            entry['genus'] = man.genus()
            tm.export(f'{out}/stl/{key}.stl')
        summary.append(entry)
scene.export(f'{out}/pad.glb')
json.dump(summary, open(f'{out}/parts.json', 'w'), indent=1)
for s in summary:
    print(s['key'], s['dims'], s.get('grams'), s.get('fits'), s['tris'])

# clash check
allp = {**parts, **bought}
for a, b in [('projector', 'cradle'), ('projector', 'tower'), ('projector', 'mirror'), ('projector', 'etray'), ('projector', 'ports'),
             ('mirror', 'tower'), ('mirrorglass', 'tower'), ('cradle', 'tower'), ('cradle', 'etray'), ('mirror', 'glass')]:
    v = (allp[a] ^ allp[b]).volume()
    print('CLASH' if v > 1 else 'ok', a, b, round(v))
