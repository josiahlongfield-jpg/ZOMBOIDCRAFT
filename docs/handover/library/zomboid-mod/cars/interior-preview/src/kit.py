"""The interior detail kit: chunky boxes placed from each cabin's anchors, with painted faces for dials, radio and grilles."""
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

def rot(yaw=0, pitch=0, roll=0):
    y, p, r = np.radians([yaw, pitch, roll])
    Ry = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    Rz = np.array([[np.cos(r), -np.sin(r), 0], [np.sin(r), np.cos(r), 0], [0, 0, 1]])
    return Ry @ Rx @ Rz

# colours: worn 80s interior plastics and fabrics
DARK = (.16, .15, .15); TRIM = (.30, .27, .25); TAN = (.62, .52, .40); FAB = (.45, .36, .30)
CHROME = (.70, .72, .74); STEEL = (.48, .50, .52); RUBBER = (.11, .11, .11); KNOB = (.08, .08, .08)
CREAM = (.86, .82, .72); WOOD = (.45, .27, .14)

class Kit:
    def __init__(self):
        self.boxes = []; self.obj = 0; self.clear = None; self.cam_z = None

    def new(self):
        self.obj += 1; return self.obj

    def box(self, lo, hi, col, obj, R=None, pivot=None, tex=None, mat='plastic'):
        """An axis box from lo to hi (model space), optionally turned by R about pivot (default its centre)."""
        lo, hi = np.asarray(lo, float), np.asarray(hi, float)
        c = (lo + hi) / 2; h = np.abs(hi - lo) / 2
        R = np.eye(3) if R is None else R
        if pivot is not None:
            c = pivot + R @ (c - pivot)
        self.boxes.append(dict(c=c, h=h, R=R, col=np.array(col, float), obj=obj, tex=tex or {}, mat=mat))

    def at(self, c, size, col, obj, R=None, **kw):
        c = np.asarray(c, float); s = np.asarray(size, float) / 2
        self.box(c - s, c + s, col, obj, R=R, pivot=c if R is not None else None, **kw)

# ------------------------------------------------------------------ painted faces (face 5 = -z, toward the driver)

def canvas(w, h, col):
    im = Image.new('RGB', (w, h), tuple(int(v * 255) for v in col)); return im, ImageDraw.Draw(im)

def arr(im):
    return np.asarray(im).astype(float) / 255

def gauges(w_m, h_m):
    W = int(w_m * 900); H = int(h_m * 900)
    im, d = canvas(W, H, (.07, .07, .075))
    def dial(cx, cy, r, ticks, red=None, label=None):
        d.ellipse((cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3), fill=(120, 122, 126))
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(14, 14, 16))
        for i in range(ticks + 1):
            a = np.radians(225 - 270 * i / ticks); big = i % 2 == 0
            r0 = r * (0.72 if big else 0.82)
            col = (220, 60, 40) if red is not None and i >= red else (235, 232, 220)
            d.line((cx + r0 * np.cos(a), cy - r0 * np.sin(a), cx + r * 0.92 * np.cos(a), cy - r * 0.92 * np.sin(a)), fill=col, width=3 if big else 2)
        a = np.radians(225 - 270 * 0.18)
        d.line((cx, cy, cx + r * 0.8 * np.cos(a), cy - r * 0.8 * np.sin(a)), fill=(255, 120, 30), width=4)
        d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=(60, 60, 60))
        if label:
            f = ImageFont.truetype(FONT, max(8, int(r * 0.22)))
            d.text((cx, cy + r * 0.45), label, fill=(200, 196, 186), font=f, anchor='mm')
    r = H * 0.38
    dial(W * 0.27, H * 0.5, r, 12, label='MPH')
    dial(W * 0.73, H * 0.5, r, 8, red=6, label='RPM')
    for i, (lab, x) in enumerate((('F', 0.5), )):
        pass
    sr = H * 0.15
    dial(W * 0.5, H * 0.3, sr, 4, label='')
    dial(W * 0.5, H * 0.72, sr, 4, label='')
    for x, c in ((0.06, (60, 200, 80)), (0.94, (60, 200, 80))):  # indicator arrows
        d.polygon([(W * x, H * 0.2), (W * x + (8 if x < .5 else -8), H * 0.14), (W * x + (8 if x < .5 else -8), H * 0.26)], fill=(30, 70, 35))
    return arr(im)

def radio(w_m, h_m):
    W = int(w_m * 900); H = int(h_m * 900)
    im, d = canvas(W, H, (.10, .10, .11))
    d.rectangle((W * 0.28, H * 0.18, W * 0.72, H * 0.52), fill=(18, 30, 22))
    f = ImageFont.truetype(FONT, int(H * 0.26))
    d.text((W * 0.5, H * 0.36), '88.5', fill=(110, 255, 150), font=f, anchor='mm')
    d.rectangle((W * 0.3, H * 0.62, W * 0.7, H * 0.7), fill=(4, 4, 4))  # cassette slot
    for i in range(5):
        x = W * (0.3 + 0.085 * i)
        d.rectangle((x, H * 0.78, x + W * 0.07, H * 0.92), fill=(170, 170, 172))
    return arr(im)

def heater(w_m, h_m):
    W = int(w_m * 900); H = int(h_m * 900)
    im, d = canvas(W, H, (.12, .115, .11))
    for i, x in enumerate((0.2, 0.5, 0.8)):
        d.rectangle((W * (x - 0.12), H * 0.42, W * (x + 0.12), H * 0.58), fill=(10, 10, 10))
        if i == 0:
            for k in range(20):
                t = k / 19; c = (int(60 + 180 * t), 70, int(220 - 180 * t))
                d.rectangle((W * (x - 0.12 + 0.24 * t), H * 0.25, W * (x - 0.12 + 0.24 * t + 0.013), H * 0.33), fill=c)
        else:
            f = ImageFont.truetype(FONT, int(H * 0.17))
            d.text((W * x, H * 0.28), ('FAN', 'AIR')[i - 1], fill=(200, 196, 186), font=f, anchor='mm')
    return arr(im)

def grille(w_m, h_m, col=(.12, .12, .12)):
    W = int(w_m * 700); H = int(h_m * 700)
    im, d = canvas(W, H, col)
    for y in range(6, H - 4, 9):
        for x in range(6 + (y // 9 % 2) * 4, W - 4, 9):
            d.ellipse((x - 2.4, y - 2.4, x + 2.4, y + 2.4), fill=(0, 0, 0))
    return arr(im)

def ribs(w_m, h_m):
    W = int(w_m * 400); H = int(h_m * 400)
    im, d = canvas(W, H, (.13, .13, .13))
    for x in range(5, W - 3, 10):
        d.rectangle((x, 6, x + 4, H - 6), fill=(48, 48, 48))
    return arr(im)

def shift(w_m, h_m):
    W = int(w_m * 900); H = int(h_m * 900)
    im, d = canvas(W, H, (.09, .09, .09))
    f = ImageFont.truetype(FONT, int(W * 0.34))
    for i, ch in enumerate('PRND21'):
        d.text((W * 0.22, H * (0.1 + 0.16 * i)), ch, fill=(255, 150, 60) if ch == 'D' else (215, 210, 200), font=f, anchor='mm')
    d.rectangle((W * 0.5, H * 0.04, W * 0.66, H * 0.96), fill=(2, 2, 2))
    return arr(im)

def stitched(w_m, h_m, col, thread=(.80, .74, .62)):
    W = max(8, int(w_m * 500)); H = max(8, int(h_m * 500))
    im, d = canvas(W, H, col)
    t = tuple(int(v * 255) for v in thread)
    for x in range(8, W - 8, 9):
        d.line((x, 6, x + 5, 6), fill=t, width=2); d.line((x, H - 7, x + 5, H - 7), fill=t, width=2)
    for y in range(8, H - 8, 9):
        d.line((6, y, 6, y + 5), fill=t, width=2); d.line((W - 7, y, W - 7, y + 5), fill=t, width=2)
    return arr(im)

def woodgrain(w_m, h_m):
    W = int(w_m * 500); H = max(6, int(h_m * 500))
    im, d = canvas(W, H, WOOD)
    rng = np.random.default_rng(3)
    for k in range(H // 3):
        y = k * 3 + rng.uniform(0, 2); c = int(70 + rng.uniform(-20, 20))
        d.line([(x, y + 1.2 * np.sin(x / 37 + k)) for x in range(0, W, 6)], fill=(c + 40, c, c // 2), width=1)
    return arr(im)

def mirror_glass(w_m, h_m):
    W = int(w_m * 600); H = int(h_m * 600)
    im, d = canvas(W, H, (.55, .62, .68))
    d.polygon([(W * .1, H), (W * .35, 0), (W * .5, 0), (W * .25, H)], fill=(190, 200, 210))
    return arr(im)

# ------------------------------------------------------------------ the kit, from a cabin's anchors

def stack_z(a):
    rows = [z for y, z in a['prof']['mid'] if z is not None and a['dash_top'] - 0.26 < y < a['dash_top'] - 0.03 and z < a['eye'][2] + 1.2]
    return float(np.max(rows)) if rows else a['eye'][2] + 0.6

def face_z(a, side, y):
    rows = [z for yy, z in a['prof'][side] if z is not None and abs(yy - y) < 0.05 and z < a['eye'][2] + 1.3]
    return float(np.min(rows)) if rows else stack_z(a)

def firewall(a, side):
    rows = [z for y, z in a['prof'][side] if z is not None and y < a['floor'] + 0.25]
    return float(np.max(rows)) if rows else a['eye'][2] + 0.9

def build(a, style='car', extras=(), seed=1):
    k = Kit(); rng = np.random.default_rng(seed)
    TRIM, FAB = globals()['TRIM'], globals()['FAB']
    if a.get('tint') is not None:  # match the car's own interior colour
        t = np.asarray(a['tint'])
        TRIM = tuple(np.clip(t * 1.3 + 0.05, 0, 1)); FAB = tuple(np.clip(t * 1.9 + 0.06, 0, 1))
    ex, ey, ez = a['eye']; f, s, dt = a['floor'], a['seat'], a['dash_top']
    sz = stack_z(a)

    # --- centre stack: vents, radio, heater panel, as one moulding standing proud of the dash
    o = k.new()
    half = 0.17
    top = dt - 0.03; bot = s + 0.10 if style == 'car' else dt - 0.33
    k.box([-half, bot, sz - 0.07], [half, top, sz + 0.02], TRIM, o)
    for x in (-0.085, 0.085):  # two square vents with slats
        v = k.new()
        k.box([x - 0.07, top - 0.085, sz - 0.085], [x + 0.07, top - 0.02, sz - 0.07], DARK, v)
        for j in range(4):
            y = top - 0.075 + j * 0.016
            k.box([x - 0.062, y, sz - 0.09], [x + 0.062, y + 0.005, sz - 0.074], (.22, .21, .21), v, R=rot(pitch=-20), pivot=np.array([x, y, sz - 0.082]))
    r = k.new(); ry = top - 0.15
    k.box([-0.12, ry - 0.035, sz - 0.09], [0.12, ry + 0.035, sz - 0.07], CHROME, r)
    k.box([-0.112, ry - 0.028, sz - 0.096], [0.112, ry + 0.028, sz - 0.09], DARK, r, tex={5: radio(0.224, 0.056)})
    for x in (-0.095, 0.095):
        k.at([x, ry, sz - 0.104], [0.022, 0.022, 0.016], KNOB, r)
    if style == 'car':
        hy = ry - 0.085
        h = k.new()
        k.box([-0.12, hy - 0.035, sz - 0.085], [0.12, hy + 0.035, sz - 0.07], DARK, h, tex={5: heater(0.24, 0.07)})
        for x in (-0.072, 0.0, 0.072):
            k.at([x, hy - 0.002, sz - 0.092], [0.012, 0.018, 0.016], (.75, .74, .7), h)

    # --- glovebox
    g = k.new(); gy = dt - 0.15; gz = face_z(a, 'pass', gy)
    k.box([-ex - 0.19, gy - 0.07, gz - 0.025], [-ex + 0.17, gy + 0.07, gz + 0.01], TRIM, g)
    k.at([-ex - 0.01, gy + 0.045, gz - 0.032], [0.07, 0.016, 0.016], CHROME, g)
    k.at([-ex - 0.01, gy + 0.045, gz - 0.041], [0.012, 0.008, 0.002], (.1, .1, .1), g)

    # --- outer vents at each end of the dash
    for side, x in (('drv', a['door'] - 0.14), ('pass', -a['door'] + 0.14)):
        vz = face_z(a, side, dt - 0.07); v = k.new()
        k.box([x - 0.06, dt - 0.1, vz - 0.03], [x + 0.06, dt - 0.035, vz + 0.01], DARK, v)
        for j in range(4):
            y = dt - 0.093 + j * 0.016
            k.box([x - 0.052, y, vz - 0.036], [x + 0.052, y + 0.005, vz - 0.02], (.22, .21, .21), v)

    # --- gauge binnacle round the existing dials
    cx, cy, cz = a['cluster']
    b = k.new(); gw, gh = 0.36, 0.15
    k.box([cx - gw / 2 - 0.02, cy - gh / 2 - 0.015, cz - 0.03], [cx + gw / 2 + 0.02, cy + gh / 2 + 0.015, cz + 0.03], DARK, b)
    k.box([cx - gw / 2 - 0.03, cy + gh / 2 + 0.015, cz - 0.11], [cx + gw / 2 + 0.03, cy + gh / 2 + 0.04, cz + 0.03], DARK, b)  # hood
    k.box([cx - gw / 2, cy - gh / 2, cz - 0.04], [cx + gw / 2, cy + gh / 2, cz - 0.03], (.05, .05, .05), b, tex={5: gauges(gw, gh)}, mat='lit')

    # --- console between the seats, gear lever, cup holders, armrest, handbrake
    if style == 'car':
        c = k.new(); cw = min(0.13, a['seat_in'] - 0.04)
        # the stock lever and tunnel go: the kit's console takes their place
        k.clear = lambda C, inside, cw=cw: inside & (np.abs(C[:, 0]) < cw + 0.06) & (C[:, 2] > ez - 0.4) & (C[:, 2] < sz - 0.05) & (C[:, 1] > f + 0.02) & (C[:, 1] < s + 0.4)
        k.box([-cw, f, ez - 0.32], [cw, s + 0.07, sz - 0.07], TRIM, c)
        k.box([-cw - 0.01, s + 0.07, ez - 0.32], [cw + 0.01, s + 0.13, ez + 0.02], FAB, c,
              tex={2: stitched(2 * cw + 0.02, 0.34, FAB)}, mat='fabric')  # padded lid
        gz = sz - 0.2
        k.box([-0.045, s + 0.07, gz - 0.09], [0.045, s + 0.078, gz + 0.09], (.09, .09, .09), c, tex={2: shift(0.09, 0.18)})
        gl = k.new()
        lean = rot(pitch=-12)
        base = np.array([0.0, s + 0.078, gz - 0.02])
        for i, (w, hh) in enumerate(((0.07, 0.03), (0.055, 0.03), (0.04, 0.03))):  # leather boot
            k.at(base + [0, hh / 2 + i * 0.028, 0], [w, hh, w], (.12, .11, .10), gl, R=lean, mat='fabric')
        k.at(base + [0, 0.14, -0.012], [0.012, 0.09, 0.012], CHROME, gl, R=lean)
        k.at(base + [0, 0.2, -0.022], [0.04, 0.045, 0.045], KNOB, gl, R=lean)
        k.at(base + [0.012, 0.22, -0.03], [0.012, 0.012, 0.014], CHROME, gl, R=lean)  # button
        cupz = (gz - 0.09 + ez + 0.02) / 2
        for x in (() if 'police' in extras else (-0.05, 0.05)):
            cu = k.new()
            k.box([x - 0.04, s + 0.07, cupz - 0.04], [x + 0.04, s + 0.074, cupz + 0.04], (.06, .06, .06), cu)
            for dx, dz, w, d2 in ((-0.04, 0, 0.008, 0.088), (0.04, 0, 0.008, 0.088), (0, -0.04, 0.088, 0.008), (0, 0.04, 0.088, 0.008)):
                k.at([x + dx, s + 0.078, cupz + dz], [w, 0.012, d2], (.2, .19, .18), cu)
        if 'cup' in extras and 'police' not in extras:
            cp = k.new(); x = -0.05
            k.at([x, s + 0.12, cupz], [0.062, 0.1, 0.062], (.93, .92, .88), cp, mat='paper')
            k.at([x, s + 0.105, cupz], [0.066, 0.03, 0.066], (.55, .32, .18), cp)
            k.at([x, s + 0.176, cupz], [0.068, 0.012, 0.068], (.95, .95, .95), cp)
        hb = k.new(); hx = cw - 0.012
        piv = np.array([hx, s + 0.07, ez - 0.02])
        R = rot(pitch=-22)
        k.box(piv + [-0.012, 0, 0], piv + [0.012, 0.03, 0.26], (.10, .10, .10), hb, R=R, pivot=piv)
        k.box(piv + [-0.016, -0.004, 0.17], piv + [0.016, 0.034, 0.27], (.14, .13, .12), hb, R=R, pivot=piv, mat='fabric')
        k.box(piv + [-0.007, 0.006, 0.27], piv + [0.007, 0.022, 0.29], CHROME, hb, R=R, pivot=piv)
        k.box([hx - 0.02, s + 0.07, ez - 0.03], [hx + 0.02, s + 0.075, ez + 0.28], (.06, .06, .06), hb)
    else:
        # bench seat: a column shifter on the steering column and a fold-down armrest
        cs = k.new(); wz = a['wheel_z']
        piv = np.array([ex - 0.08, dt - 0.08, wz + 0.08])
        R = rot(yaw=-60, roll=-15)
        k.box(piv, piv + [0.012, 0.012, -0.0], CHROME, cs)
        k.box(piv + [-0.006, -0.006, -0.18], piv + [0.006, 0.006, 0], CHROME, cs, R=rot(yaw=40, pitch=-15), pivot=piv)
        tip = piv + rot(yaw=40, pitch=-15) @ np.array([0, 0, -0.18])
        k.at(tip, [0.03, 0.03, 0.05], KNOB, cs, R=rot(yaw=40, pitch=-15))
        ar = k.new()
        k.box([-0.12, s + 0.06, ez - 0.36], [0.12, s + 0.2, ez + 0.05], FAB, ar, tex={2: stitched(0.24, 0.41, FAB)}, mat='fabric')
        if 'cb' in extras:  # CB radio hung under the dash in front of the passenger, mic on its hook
            cb = k.new(); cby = dt - 0.28; cx = -ex + 0.05; cz = face_z(a, 'pass', dt - 0.15) + 0.04
            k.box([cx - 0.11, cby - 0.03, cz - 0.18], [cx + 0.11, cby + 0.03, cz], (.2, .2, .21), cb)
            k.box([cx - 0.1, cby - 0.022, cz - 0.185], [cx + 0.1, cby + 0.022, cz - 0.18], DARK, cb, tex={5: radio(0.2, 0.044)})
            for x in (cx - 0.118, cx + 0.112):
                k.box([x, cby - 0.035, cz - 0.15], [x + 0.006, dt - 0.2, cz - 0.03], (.17, .17, .18), cb)
            m = k.new()
            k.at([cx + 0.15, cby + 0.02, cz - 0.1], [0.035, 0.075, 0.05], KNOB, m)
            for j in range(12):
                u = j / 11
                k.at([cx + 0.15 - 0.04 * u + 0.01 * np.cos(j * 2.2), cby - 0.02 - 0.06 * np.sin(np.pi * u), cz - 0.1 + 0.01 * np.sin(j * 2.2)], [0.011, 0.011, 0.011], (.04, .04, .04), m)

    # --- pedals hanging from under the dash
    fw = firewall(a, 'drv')
    for x, w, hgt in ((ex - 0.04, 0.11, 0.07), (ex - 0.2, 0.05, 0.11)):
        p = k.new()
        R = rot(pitch=-35)
        c0 = np.array([x, f + 0.13, fw - 0.2])
        k.at(c0, [w, hgt, 0.014], RUBBER, p, R=R)
        k.box([x - 0.008, f + 0.13, fw - 0.2], [x + 0.008, dt - 0.25, fw - 0.19], STEEL, p)
    # --- rubber floor mats
    for x0, x1 in ((a['seat_in'] + 0.02, a['door'] - 0.05), (-a['door'] + 0.05, -a['seat_in'] - 0.02)):
        m = k.new(); z0, z1 = ez + 0.12, fw - 0.06
        k.box([x0, f + 0.004, z0], [x1, f + 0.016, z1], (.13, .13, .13), m, tex={2: ribs(x1 - x0, z1 - z0)}, mat='rubber')

    # --- rear-view mirror, air freshener, sun visors, dome light
    rf, hz = a['roof'], a['header_z']
    mi = k.new()
    k.box([-0.012, rf - 0.07, hz - 0.06], [0.012, rf - 0.005, hz - 0.035], DARK, mi)
    R = rot(yaw=-8)
    mc = np.array([0.0, rf - 0.1, hz - 0.07])
    k.at(mc, [0.25, 0.065, 0.03], DARK, mi, R=R)
    k.at(mc + R @ [0, 0, -0.016], [0.23, 0.05, 0.004], (.6, .66, .7), mi, R=R, tex={5: mirror_glass(0.23, 0.05)}, mat='glass')
    if 'freshener' in extras:
        fr = k.new()
        k.box([0.05, rf - 0.2, hz - 0.075], [0.052, rf - 0.13, hz - 0.073], (.85, .85, .8), fr)
        tree = [(0.045, 0.025), (0.036, 0.028), (0.024, 0.025), (0.008, 0.016)]
        y = rf - 0.268
        for w, hh in tree:
            k.box([0.051 - w / 2, y, hz - 0.076], [0.051 + w / 2, y + hh, hz - 0.072], (.1, .55, .2), fr); y += hh * 0.8
    for x0, x1, drv in ((0.05, a['door'] - 0.08, True), (-a['door'] + 0.08, -0.05, False)):
        vi = k.new()
        k.box([x0, rf - 0.045, hz - 0.3], [x1, rf - 0.02, hz - 0.03], (.70, .64, .55), vi,
              tex={3: stitched(x1 - x0, 0.27, (.70, .64, .55), (.55, .5, .42))}, mat='fabric')
        k.box([x1 - 0.02, rf - 0.03, hz - 0.08], [x1 + 0.015, rf, hz - 0.05], (.35, .33, .3), vi)
        if drv and 'ticket' in extras:
            t = k.new()
            k.box([x0 + 0.1, rf - 0.047, hz - 0.2], [x0 + 0.2, rf - 0.045, hz - 0.11], (.95, .93, .7), t, mat='paper')
            k.box([x0 + 0.06, rf - 0.049, hz - 0.17], [x1 - 0.06, rf - 0.046, hz - 0.155], (.25, .22, .2), t)
    dl = k.new()
    k.box([-0.08, rf - 0.025, ez - 0.25], [0.08, rf, ez - 0.17], TRIM, dl)
    k.box([-0.065, rf - 0.03, ez - 0.24], [0.065, rf - 0.025, ez - 0.18], CREAM, dl, mat='lit')

    # --- door cards: armrest with a pull, handle, window crank, lock knob, speaker, map pocket, trim strip
    for sg in (1, -1):
        dx = sg * a['door']; inward = -sg
        sl = a['sill']
        d = k.new()
        ax0, ax1 = sorted([dx, dx + inward * 0.075])
        k.box([ax0, sl - 0.27, ez - 0.25], [ax1, sl - 0.2, ez + 0.32], TRIM, d)
        k.box([ax0 - 0.003, sl - 0.2, ez - 0.22], [ax1 + 0.003, sl - 0.19, ez + 0.12], FAB, d, tex={2: stitched(0.078, 0.34, FAB)}, mat='fabric')
        tx0, tx1 = sorted([dx, dx + inward * 0.012])
        tex = woodgrain(0.6, 0.03) if style == 'car' else None
        k.box([tx0, sl - 0.06, ez - 0.25], [tx1, sl - 0.03, ez + 0.4], WOOD if tex is not None else CHROME, d,
              tex={(1 if sg > 0 else 0): tex} if tex is not None else None)
        hd = k.new()
        hx0, hx1 = sorted([dx, dx + inward * 0.02])
        k.box([hx0, sl - 0.115, ez + 0.26], [hx1, sl - 0.095, ez + 0.36], CHROME, hd)
        k.box([hx0, sl - 0.13, ez + 0.24], [hx0 + (hx1 - hx0) * 0.4, sl - 0.08, ez + 0.38], DARK, hd)
        cr = k.new(); cz = ez + 0.05; cyy = sl - 0.14
        hub = np.array([dx + inward * 0.025, cyy, cz])
        k.at(hub, [0.05, 0.045, 0.045], DARK, cr)
        k.at(hub + [inward * 0.032, 0, 0], [0.014, 0.022, 0.022], CHROME, cr)
        arm = rot(roll=0, pitch=35)
        k.box(hub + [inward * 0.04 - 0.006, -0.008, -0.1], hub + [inward * 0.04 + 0.006, 0.008, 0.0], CHROME, cr, R=arm, pivot=hub)
        tip = hub + arm @ np.array([0, 0, -0.095])
        k.at(tip + [inward * 0.06, 0, 0], [0.05, 0.022, 0.022], KNOB, cr)
        lk = k.new()
        k.box([dx + inward * 0.03 - 0.008, sl - 0.01, ez - 0.2], [dx + inward * 0.03 + 0.008, sl + 0.05, ez - 0.184], CHROME, lk)
        sp = k.new(); sx0, sx1 = sorted([dx, dx + inward * 0.015])
        k.box([sx0, f + 0.1, ez + 0.36], [sx1, f + 0.26, ez + 0.56], (.12, .12, .12), sp, tex={(1 if sg > 0 else 0): grille(0.2, 0.16)})
        mp = k.new(); px0, px1 = sorted([dx, dx + inward * 0.045])
        k.box([px0, f + 0.08, ez - 0.2], [px1, f + 0.2, ez + 0.3], TRIM, mp)
        if sg < 0 and 'map' in extras:
            m = k.new(); p0, p1 = sorted([dx + inward * 0.045, dx + inward * 0.05])
            k.box([p0, f + 0.17, ez - 0.05], [p1, f + 0.27, ez + 0.12], (.85, .8, .62), m, R=rot(roll=8 * sg), pivot=np.array([dx, f + 0.2, ez]), mat='paper')

    # --- police: radio on the console, handset on the stack, a cage behind the seats with the shotgun rack
    if 'police' in extras:
        pr = k.new(); rz = (sz - 0.29 + ez + 0.02) / 2
        R = rot(pitch=25)
        c0 = np.array([0.0, s + 0.14, rz])
        k.box([-0.1, s + 0.07, rz - 0.06], [0.1, s + 0.1, rz + 0.06], STEEL, pr)
        k.at(c0, [0.24, 0.09, 0.15], (.08, .08, .09), pr, R=R)
        k.at(c0 + R @ [0, 0, -0.076], [0.22, 0.07, 0.004], DARK, pr, R=R, tex={5: radio(0.22, 0.07)})
        mic = k.new(); mp = np.array([0.17 + 0.02, top - 0.2, sz - 0.05])
        k.at(mp, [0.035, 0.085, 0.05], KNOB, mic)
        k.at(mp + [0.0, 0.0, -0.026], [0.025, 0.03, 0.004], (.3, .3, .3), mic)
        end = c0 + [0.1, 0.0, 0.03]; st = mp + [0, -0.045, 0]
        for j in range(16):
            u = j / 15; p = st * (1 - u) + end * u + [0.012 * np.cos(j * 2.2), -0.09 * np.sin(np.pi * u), 0.012 * np.sin(j * 2.2)]
            k.at(p, [0.011, 0.011, 0.011], (.04, .04, .04), mic)
        cz = a['seat_back'] - 0.16; k.cam_z = cz + 0.08
        cg = k.new(); w = a['door'] - 0.06
        y0, y1 = s + 0.12, a['roof'] - 0.03
        k.box([-w, y0, cz - 0.01], [w, y0 + 0.22, cz + 0.01], (.2, .21, .22), cg)
        k.box([-w + 0.02, y0 + 0.22, cz - 0.004], [w - 0.02, y1 - 0.02, cz + 0.004], (.3, .31, .32), cg, mat='glass',
              tex={5: grille(2 * w - 0.04, y1 - y0 - 0.24, (.36, .37, .38))})
        for x in (-w, -0.012, w - 0.024):
            k.box([x, y0, cz - 0.015], [x + 0.024, y1, cz + 0.015], (.22, .23, .24), cg)
        k.box([-w, y1 - 0.024, cz - 0.015], [w, y1, cz + 0.015], (.22, .23, .24), cg)
        sg = k.new(); gx = 0.06; gz = cz + 0.04
        k.box([gx - 0.022, y0 + 0.02, gz - 0.035], [gx + 0.022, y0 + 0.25, gz + 0.035], WOOD, sg)  # stock
        k.box([gx - 0.018, y0 + 0.25, gz - 0.03], [gx + 0.018, y0 + 0.42, gz + 0.03], (.13, .13, .14), sg)  # receiver
        k.box([gx - 0.011, y0 + 0.42, gz - 0.01], [gx + 0.011, y1 - 0.06, gz + 0.012], (.2, .2, .21), sg)  # barrel
        k.box([gx - 0.012, y0 + 0.42, gz + 0.012], [gx + 0.012, y1 - 0.18, gz + 0.03], WOOD, sg)  # pump grip
        for y in (y0 + 0.3, y1 - 0.12):
            cl = k.new()
            k.box([gx - 0.04, y - 0.025, cz + 0.005], [gx + 0.04, y + 0.025, gz + 0.04], (.1, .1, .1), cl)
    return k
