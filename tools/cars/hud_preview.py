"""HUD preview: the cockpit from the pilot's design eye, the world outside banked by the aircraft's
attitude, and the aircraft's head-up display drawn over it (F-22, Su-27 or Apache IHADSS style).
usage: hud.py model out [noseUp bank heading]"""
import sys; sys.path.insert(0, '/home/user/zomboidcraft/tools/cars')
import numpy as np, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cockpit_glow as cg
from zcar import read

W, H, VFOV = 1280, 720, 70.0
GUI = 2.0                                  # pixels per gui unit, as at gui scale 2 on 1280x720
model, out = sys.argv[1], sys.argv[2]
SEATS = {'jet_corvid': (0.0, 3.05, 5.56), 'jet_gyrfalcon': (-0.005, 3.585, 5.42), 'heli_hornbill': (0.0, 2.6, 4.35)}
heli = model.startswith('heli')
nose_up = float(sys.argv[3]) if len(sys.argv) > 3 else (-4 if heli else 7)
bank = float(sys.argv[4]) if len(sys.argv) > 4 else (9 if heli else 24)
heading = float(sys.argv[5]) if len(sys.argv) > 5 else 87
look = 8 if heli else 4                    # the view's tilt below the nose, degrees
import os
eye = tuple(float(v) for v in os.environ['EYE'].split(',')) if os.environ.get('EYE') else SEATS[model]
GLASS = {'jet_corvid': (-0.068, 0.068, 2.985, 3.105, 5.80), 'jet_gyrfalcon': (-0.088, 0.068, 3.52, 3.685, 5.83)}
ALT_M = 95 if heli else 820                # above the ground
SPEED = 180 / 3.6 if heli else 640 / 3.6   # m/s


def rx(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def ry(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rz(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])

# body -> world, as Flight.attitude(yaw, noseUp, bank): nose +z, left +x, up +y
R = ry(math.radians(-(heading - 180))) @ rx(math.radians(-nose_up)) @ rz(math.radians(bank))
cg.VIEW_W, cg.VIEW_H = W, H
cg.FOV = 2 * math.degrees(math.atan(math.tan(math.radians(VFOV / 2)) * W / H))
F = (W / 2) / math.tan(math.radians(cg.FOV / 2))
fb, rb, ub = cg.camera(np.array(eye), look)          # camera in the body frame
fw, rw, uw = R @ fb, R @ rb, R @ ub                    # and in the world

# ---- the cockpit and the world outside
s = read(model)['usable']; T = s['T']; UV = s['UV']; tex = np.asarray(s['image'].convert('RGB'))
keep = np.linalg.norm(T.mean(1) - np.array(eye), axis=1) < 7
tb, uvb, pb, zb = cg.render(T, UV, eye, look, keep)
th, tw = tex.shape[:2]
u = np.clip((uvb[..., 0] * tw).astype(int), 0, tw - 1); v = np.clip((uvb[..., 1] * th).astype(int), 0, th - 1)
pic = tex[v, u].astype(float) * 0.55               # a gloomy day: the cockpit's in shadow
gx, gy = np.meshgrid(np.arange(W) + 0.5, np.arange(H) + 0.5)
ray = fw[None, None] + rw[None, None] * ((gx - W / 2) / F)[..., None] + uw[None, None] * ((H / 2 - gy) / F)[..., None]
ray /= np.linalg.norm(ray, axis=-1, keepdims=True)
sky = np.clip(ray[..., 1], 0, 1)[..., None]
skyc = np.array([150, 156, 162]) * (1 - sky) + np.array([92, 100, 110]) * sky
t = ALT_M / np.maximum(-ray[..., 1], 1e-4)
px_, pz_ = ray[..., 0] * t, ray[..., 2] * t
field = ((np.floor(px_ / 140) + np.floor(pz_ / 110)) % 3)[..., None]
groundc = np.where(field == 0, [62, 72, 50], np.where(field == 1, [74, 70, 54], [55, 64, 48])).astype(float)
road = (np.abs((px_ + 0.3 * pz_) % 900 - 450) < 6)[..., None]
groundc = np.where(road, [70, 70, 72], groundc)
fog = np.clip(t / 6000, 0, 1)[..., None] ** 0.7
groundc = groundc * (1 - fog) + np.array([140, 146, 152]) * fog
outside = np.where(ray[..., 1:2] > 0, skyc, groundc)
glass = np.zeros(len(T), bool); glass[np.where(s['glass'])[0]] = True
see = (tb < 0) | glass[np.maximum(tb, 0)]
tint = np.where(((tb >= 0) & glass[np.maximum(tb, 0)])[..., None], 0.92, 1.0)
pic = np.where(see[..., None], outside * tint, pic)
base = Image.fromarray(np.clip(pic, 0, 255).astype(np.uint8)).convert('RGBA')

# ---- the HUD, in gui units
gw, gh = W / GUI, H / GUI
cx, cy = gw / 2, gh / 2
scale = gh / 2 / math.tan(math.radians(VFOV / 2))
hud = Image.new('RGBA', (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(hud)
FONT = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 13)
SMALL = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 10)
BIG = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 20)
COL = {'jet_corvid': (90, 255, 120), 'jet_gyrfalcon': (170, 255, 90), 'heli_hornbill': (80, 255, 110)}[model]
C = COL + (235,)


def P(x, y):
    return (x * GUI, y * GUI)
def line(x0, y0, x1, y1, c=C, w=1.5):
    d.line([P(x0, y0), P(x1, y1)], fill=c, width=max(1, round(w)))
def text(x, y, s, c=C, f=FONT, anchor='la'):
    d.text(P(x, y), s, fill=c, font=f, anchor=anchor)
def box(x0, y0, x1, y1, c=C):
    d.rectangle([P(x0, y0), P(x1, y1)], outline=c, width=2)
def circle(x, y, r, c=C, w=2):
    d.ellipse([P(x - r, y - r), P(x + r, y + r)], outline=c, width=w)
def project(dw):
    z = dw @ fw
    if z < 0.05:
        return None
    return cx + (dw @ rw) / z * scale, cy - (dw @ uw) / z * scale


def glass_field():
    x0, x1, y0, y1, z = GLASS[model]
    pts = []
    for gx_ in (x0, x1):
        for gy_ in (y0, y1):
            D = np.array([gx_, gy_, z]) - np.array(eye)
            zz = D @ fb
            pts.append((cx + (D @ rb) / zz * scale, cy - (D @ ub) / zz * scale))
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    return min(xs), max(xs), min(ys), max(ys)


def rot(px, py, ox, oy, a):
    c, s_ = math.cos(a), math.sin(a)
    dx, dy = px - ox, py - oy
    return ox + dx * c - dy * s_, oy + dx * s_ + dy * c


nose = project(R @ np.array([0, 0, 1.0]))
aoa, slip = (2.5, 1.5) if heli else (5.5, 0.6)
vel = R @ (rx(math.radians(aoa)) @ ry(math.radians(-slip)) @ np.array([0, 0, 1.0]))
fpm = project(vel)
roll = -math.radians(bank)             # screen turn of the horizon (clockwise for a right bank)
flat = np.array([R[0, 2], 0, R[2, 2]]); flat /= np.linalg.norm(flat)
alt_ft = round((ALT_M + 63 + 120) * 3.281 / 10) * 10
kts = SPEED * 1.944
mach = SPEED / 340
G = 1 / math.cos(roll) if not heli else 1.03
vs = SPEED * math.sin(math.radians(nose_up - aoa))


def rung_point(p):
    r = math.radians(p)
    return project(flat * math.cos(r) + np.array([0, math.sin(r), 0]))


def clip_box(x0, y0, x1, y1):
    """Only draws conformal symbols inside the HUD's glass (a real HUD's field of view)."""
    global d
    return (x0, y0, x1, y1)


if model == 'jet_corvid':
    # ---------------- F-22-style HUD: 30 x 25 degree field over the nose
    fx0, fx1, fy0, fy1 = glass_field()
    layer = Image.new('RGBA', (W, H)); dl = ImageDraw.Draw(layer)
    # pitch ladder centred on the flight path marker, every 5 degrees
    for p in range(-30, 35, 5):
        at = rung_point(p)
        if at is None:
            continue
        # shift the ladder sideways so it centres on the flight path marker, as the F-22's does
        ox = fpm[0] - nose[0] if p != 0 else 0
        x, y = at[0], at[1]
        half, gap = (70, 10) if p == 0 else (26, 9)
        segs = []
        if p == 0:
            segs = [((-half, 0), (-gap, 0)), ((gap, 0), (half, 0))]
        elif p > 0:
            segs = [((-half, 0), (-gap, 0)), ((gap, 0), (half, 0)), ((-half, 0), (-half, 5)), ((half, 0), (half, 5))]
        else:
            for k in range(-half, -gap, 7):
                segs.append(((k, 0), (min(k + 4, -gap), 0)))
            for k in range(gap, half, 7):
                segs.append(((k, 0), (min(k + 4, half), 0)))
            # negative rungs bend toward the horizon, steeper the further below
            tip = min(8, 2 - p / 4)
            segs += [((-half, 0), (-half + 3, -tip)), ((half, 0), (half - 3, -tip))]
        for (a0, b0), (a1, b1) in segs:
            p0 = rot(x + a0, y + b0, x, y, roll); p1 = rot(x + a1, y + b1, x, y, roll)
            dl.line([P(*p0), P(*p1)], fill=C, width=2)
        if p != 0:
            for sx in (-1, 1):
                tx, ty = rot(x + sx * (half + 9), y + (2 if p > 0 else -2), x, y, roll)
                dl.text(P(tx, ty), str(abs(p)), fill=C, font=SMALL, anchor='mm')
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).rectangle([P(fx0, fy0), P(fx1, fy1)], fill=255)
    hud.paste(layer, (0, 0), Image.composite(layer.getchannel('A'), Image.new('L', (W, H), 0), mask))
    d = ImageDraw.Draw(hud)
    # flight path marker: circle, wings, tail
    x, y = fpm
    circle(x, y, 4); line(x - 12, y, x - 4, y); line(x + 4, y, x + 12, y); line(x, y - 4, x, y - 9)
    # waterline (the nose) and the gun cross
    x, y = nose
    for a, b in [((-10, 3), (-6, 3)), ((-6, 3), (-3, -1)), ((-3, -1), (0, 3)), ((0, 3), (3, -1)), ((3, -1), (6, 3)), ((6, 3), (10, 3))]:
        line(x + a[0], y + a[1] - 18, x + b[0], y + b[1] - 18)
    line(x - 5, y, x + 5, y); line(x, y - 5, x, y + 5)
    # heading tape along the top of the glass
    hy = fy0 + 4
    for k in range(-25, 30, 5):
        mark = (math.floor(heading / 5) * 5 + k) % 360
        off = ((mark - heading + 540) % 360) - 180
        if abs(off) > 22:
            continue
        hx = cx + off * 2.6
        line(hx, hy, hx, hy + (6 if mark % 10 == 0 else 3))
        if mark % 10 == 0:
            text(hx, hy + 8, '%02d' % (mark // 10 % 36), f=SMALL, anchor='ma')
    line(cx, hy + 20, cx - 3, hy + 25); line(cx, hy + 20, cx + 3, hy + 25)
    text(cx, hy + 26, '%03d' % round(heading), f=SMALL, anchor='ma')
    # airspeed box (knots), Mach and G; altitude box (feet) and vertical velocity
    ax, ay = fx0 + 4, cy - 2
    box(ax, ay - 7, ax + 30, ay + 7); text(ax + 27, ay, '%d' % round(kts), anchor='rm')
    text(ax, ay - 22, 'G %.1f' % G, f=SMALL); text(ax, ay - 14, '%.1f' % 9.0, f=SMALL)
    text(ax, ay + 11, 'M %.2f' % mach, f=SMALL)
    text(ax, ay + 20, 'α %.1f' % aoa, f=SMALL)
    bx = fx1 - 4
    box(bx - 38, ay - 7, bx, ay + 7); text(bx - 3, ay, '{:,}'.format(alt_ft), anchor='rm')
    text(bx, ay + 11, '%+d' % round(vs * 196.85 / 100) + '00', f=SMALL, anchor='ra')
    text(bx, ay - 22, 'R  %d' % round(ALT_M * 3.281), f=SMALL, anchor='ra')
    # bank scale arc along the bottom
    by = fy1 - 4
    for a in (-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60):
        ang = math.radians(a)
        r0, r1 = 30, 30 + (5 if a % 30 == 0 else 3)
        line(cx + r0 * math.sin(ang), by - 30 + r0 * math.cos(ang), cx + r1 * math.sin(ang), by - 30 + r1 * math.cos(ang))
    ang = math.radians(-bank)
    tx, ty = cx + 27 * math.sin(ang), by - 30 + 27 * math.cos(ang)
    d.polygon([P(tx, ty), P(tx - 2.5, ty - 5), P(tx + 2.5, ty - 5)], outline=C, width=2)
    # mode and weapons
    text(fx0 + 4, fy1 - 22, 'NAV', f=SMALL); text(fx0 + 4, fy1 - 13, 'ARM', f=SMALL)
    text(fx1 - 4, fy1 - 13, 'GUN 480', f=SMALL, anchor='ra')
    text(fx1 - 4, fy1 - 22, 'AIM-9 2', f=SMALL, anchor='ra')
    text(fx1 - 4, fy1 - 31, 'FUEL 14.2', f=SMALL, anchor='ra')
    hud_name = 'F-22-style HUD'

elif model == 'jet_gyrfalcon':
    # ---------------- Su-27-style (ILS-31) HUD: km/h and metres, Cyrillic modes
    fx0, fx1, fy0, fy1 = glass_field()
    layer = Image.new('RGBA', (W, H)); dl = ImageDraw.Draw(layer)
    for p in range(-40, 45, 10):
        at = rung_point(p)
        if at is None:
            continue
        x, y = at
        if p == 0:
            segs = [((-110, 0), (-40, 0)), ((-30, 0), (-12, 0)), ((12, 0), (30, 0)), ((40, 0), (110, 0))]
        else:
            segs = [((10, 0), (30, 0)), ((30, 0), (30, 4 if p > 0 else -4))] if p > 0 else [((10, 0), (16, 0)), ((21, 0), (27, 0)), ((27, 0), (27, -4))]
            segs += [((-a1, b1), (-a0, b0)) for (a0, b0), (a1, b1) in segs]
        for (a0, b0), (a1, b1) in segs:
            dl.line([P(*rot(x + a0, y + b0, x, y, roll)), P(*rot(x + a1, y + b1, x, y, roll))], fill=C, width=2)
        if p != 0:
            tx, ty = rot(x + 40, y, x, y, roll)
            dl.text(P(tx, ty), str(p), fill=C, font=SMALL, anchor='mm')
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).rectangle([P(fx0, fy0), P(fx1, fy1)], fill=255)
    hud.paste(layer, (0, 0), Image.composite(layer.getchannel('A'), Image.new('L', (W, H), 0), mask))
    d = ImageDraw.Draw(hud)
    # the aircraft symbol at the boresight and the velocity vector
    x, y = nose
    circle(x, y, 3); line(x - 14, y, x - 3, y); line(x + 3, y, x + 14, y); line(x, y - 3, x, y - 7)
    x, y = fpm
    d.polygon([P(x, y - 4), P(x + 4, y), P(x, y + 4), P(x - 4, y)], outline=C, width=2)
    # speed (km/h) and height (m), Mach, load factor n, alpha
    text(fx0 + 2, fy0 + 4, 'V %d' % round(SPEED * 3.6), f=FONT)
    text(fx0 + 2, fy0 + 16, 'M %.2f' % mach, f=SMALL)
    text(fx1 - 2, fy0 + 4, 'H %d' % round(ALT_M), f=FONT, anchor='ra')
    text(fx1 - 2, fy0 + 16, 'Vy %+.0f' % vs, f=SMALL, anchor='ra')
    text(fx0 + 2, fy1 - 24, 'n %.1f' % G, f=SMALL)
    text(fx0 + 2, fy1 - 14, 'α %.0f' % aoa, f=SMALL)
    # compass along the top, Russian style: degrees with the heading in a notch
    for k in range(-30, 35, 10):
        mark = (math.floor(heading / 10) * 10 + k) % 360
        off = ((mark - heading + 540) % 360) - 180
        if abs(off) > 28:
            continue
        hx = cx + off * 1.8
        line(hx, fy0 + 30, hx, fy0 + 34)
        text(hx, fy0 + 36, '%d' % (mark // 10), f=SMALL, anchor='ma')
    text(cx, fy0 + 20, '%03d' % round(heading), f=SMALL, anchor='ma')
    # bank: a small arc and pointer under the symbol
    for a in (-30, -15, 0, 15, 30):
        ang = math.radians(a)
        line(cx + 22 * math.sin(ang), fy1 - 34 + 22 * math.cos(ang), cx + 26 * math.sin(ang), fy1 - 34 + 26 * math.cos(ang))
    ang = math.radians(-bank)
    line(cx, fy1 - 34, cx + 20 * math.sin(ang), fy1 - 34 + 20 * math.cos(ang))
    # mode and weapons: НАВ (navigation), Р-73 heat-seekers, ГШ-30 cannon rounds
    text(cx, fy1 - 12, 'НАВ', f=FONT, anchor='ma')
    text(fx1 - 2, fy1 - 24, 'ГШ 150', f=SMALL, anchor='ra')
    text(fx1 - 2, fy1 - 14, 'Р-73  2', f=SMALL, anchor='ra')
    hud_name = 'Su-27-style HUD'

else:
    # ---------------- Apache IHADSS: helmet-mounted, moves with your head, knots and feet
    fx0, fx1, fy0, fy1 = cx - 150, cx + 150, cy - 108, cy + 108
    # heading tape with cardinal letters
    for k in range(-40, 45, 5):
        mark = (math.floor(heading / 5) * 5 + k) % 360
        off = ((mark - heading + 540) % 360) - 180
        if abs(off) > 36:
            continue
        hx = cx + off * 2.4
        line(hx, fy0, hx, fy0 + (6 if mark % 10 == 0 else 3))
        if mark % 30 == 0:
            lbl = {0: 'N', 90: 'E', 180: 'S', 270: 'W'}.get(mark, '%d' % (mark // 10))
            text(hx, fy0 + 8, lbl, f=SMALL, anchor='ma')
    box(cx - 12, fy0 - 14, cx + 12, fy0 - 2); text(cx, fy0 - 8, '%03d' % round(heading), f=SMALL, anchor='mm')
    # LOS reticle (where your head points)
    line(cx - 10, cy, cx - 3, cy); line(cx + 3, cy, cx + 10, cy); line(cx, cy - 10, cx, cy - 3); line(cx, cy + 3, cx, cy + 10)
    # horizon line, banked, and the nose's position
    at = rung_point(0)
    if at is not None:
        for a0, a1 in [(-90, -20), (20, 90)]:
            line(*rot(at[0] + a0, at[1], at[0], at[1], roll), *rot(at[0] + a1, at[1], at[0], at[1], roll))
    x, y = nose
    line(x - 6, y, x - 2, y); line(x + 2, y, x + 6, y)
    # velocity vector: a line from the centre toward your drift, and the acceleration cue
    line(cx, cy, cx + 6, cy - 22, w=2); circle(cx + 9, cy - 26, 3)
    # torque, airspeed, altitudes and the radar altitude's scale
    text(fx0, fy0 - 10, '%d%%' % 84, f=FONT)
    text(fx0, cy - 4, '%d' % round(kts), f=FONT)
    text(fx1, fy0 - 10, '%d' % (alt_ft), f=FONT, anchor='ra')
    text(fx1 - 22, cy - 4, '%d' % round(ALT_M * 3.281), f=FONT, anchor='ra')
    sx, s0, s1 = fx1 - 14, cy - 50, cy + 50
    line(sx, s0, sx, s1)
    for k in range(0, 201, 50):
        yy = s1 - k / 200 * (s1 - s0); line(sx, yy, sx + 4, yy)
    ra = min(200, ALT_M * 3.281)
    d.rectangle([P(sx + 6, s1 - ra / 200 * (s1 - s0)), P(sx + 9, s1)], outline=C, fill=C)
    # vertical speed pointer on the same scale
    vy = cy - max(-50, min(50, vs * 196.85 / 1000 * 50))
    d.polygon([P(sx - 2, vy), P(sx - 7, vy - 3), P(sx - 7, vy + 3)], outline=C, width=2)
    # trim ball under the reticle
    box(cx - 20, fy1 - 8, cx + 20, fy1 - 1); line(cx - 4, fy1 - 8, cx - 4, fy1 - 1); line(cx + 4, fy1 - 8, cx + 4, fy1 - 1)
    circle(cx + 2, fy1 - 4.5, 2.6, w=2)
    # head-tracking field of regard: where your head points in the aircraft
    box(fx0 + 2, fy1 - 20, fx0 + 32, fy1 - 2); circle(fx0 + 17, fy1 - 9, 1.6)
    # weapons
    text(fx0 + 40, fy1 - 10, 'GUN 1200', f=SMALL); text(fx1, fy1 - 10, 'RKT 38  MSL 8', f=SMALL, anchor='ra')
    hud_name = 'Apache IHADSS-style'

glow = hud.filter(ImageFilter.GaussianBlur(3))
glow.putalpha(glow.getchannel('A').point(lambda a: a * 0.6))
img = Image.alpha_composite(base, glow)
img = Image.alpha_composite(img, hud)
img.convert('RGB').save(out, quality=92)
print('saved', out, hud_name)
