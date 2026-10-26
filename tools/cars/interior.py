"""
The cabin detail kit: a centre stack with vents, radio and heater, a gauge hood with real dials, a
glovebox, a console with the gear lever, cup holders, handbrake and armrest, door cards, pedals,
mats, mirror, visors, dome light, and in the back the seat-back pockets, rear door cards, belts,
grab handles and the parcel shelf. Each piece is a chunky box placed from the cabin's own shape
(measured with rays from the seats), painted in the inked style: a thin dark outline only round
each whole object's outer edges (none where its parts join), lit tops, darker undersides, and the
wear of a car left out since the end of the world: faded tops, dust, scuffs, stains, torn seats,
rust. Abandoned and wrecked cars are worse (glovebox hanging open, visor down, trash on the floor);
burnt ones are charred.

wreck.build_variant calls add() to bake the kit into a variant: its boxes become triangles on the
fixed body (after the car's own solid triangles), its faces get their own rectangles on a strip
added to the bottom of the car's atlas, and the stock console the kit replaces is taken out. The
faces that light up at night (dials, radio display) are flagged in 'lit'.
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFont

PPM = 128          # kit texels per metre: 8 per Minecraft pixel, so the one-texel outline stays thin
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

# how each car's cabin is fitted out: 'car' bucket seats and a console, 'work' a bench and a column
# shifter, 'bus' just the driver's dash; extras by model
STYLE = {'16_PICKUP_01': 'work', '07_VAN': 'work', 'stepvan_delivery': 'work', 'MOVING_TRUCK': 'work',
         '036_TOW_TRUCK': 'work', 'stepvan_swat': 'work', '06_AMBULANCE': 'work', 'AMBULANCE_V2': 'work',
         '05_FIRE_TRUCK': 'work', '03_CITY_TRUCK': 'work', '04_1_GARBAGE_TRUCK': 'work',
         '04_2_GARBAGE_TRUCK': 'work', '01_CITY_BUS': 'bus', '02_SCHOOL_BUS': 'bus'}
EXTRAS = {'10_SEDAN_POLICE_01': {'police'}, '12_SUV_POLICE_01': {'police'}, '14_SEDAN_TAXI_01': {'meter'},
          '16_PICKUP_01': {'cb'}, '036_TOW_TRUCK': {'cb'}, 'MOVING_TRUCK': {'cb'}, '03_CITY_TRUCK': {'cb'},
          'stepvan_swat': {'cb'}, '06_AMBULANCE': {'cb'}, 'AMBULANCE_V2': {'cb'}, '05_FIRE_TRUCK': {'cb'}}
# a parcel shelf under the back window (saloons only)
SHELF = {'10_SEDAN_01', '14_SEDAN_02', '14_SEDAN_TAXI_01', '10_SEDAN_POLICE_01', '35_LIMOUSINE'}
WEAR = {'usable': 0.45, 'abandoned': 0.8, 'wrecked': 1.1, 'burnt': 1.0}

DARK = (.16, .15, .15); CHROME = (.66, .68, .7); STEEL = (.46, .48, .5); RUBBER = (.11, .11, .11)
KNOB = (.08, .08, .08); CREAM = (.84, .8, .7); WOOD = (.45, .27, .14)
DIALS = np.array([[82, 51, 160], [42, 101, 163], [100, 132, 171], [163, 42, 131]])


def rot(yaw=0, pitch=0, roll=0):
    y, p, r = np.radians([yaw, pitch, roll])
    Ry = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    Rz = np.array([[np.cos(r), -np.sin(r), 0], [np.sin(r), np.cos(r), 0], [0, 0, 1]])
    return Ry @ Rx @ Rz


def hit(T, o, d):
    """Distance along d from o to the nearest triangle of T, or None."""
    d = np.asarray(d, float); d = d / np.linalg.norm(d); o = np.asarray(o, float)
    e1 = T[:, 1] - T[:, 0]; e2 = T[:, 2] - T[:, 0]
    p = np.cross(d, e2); det = (e1 * p).sum(1)
    ok = np.abs(det) > 1e-9; inv = np.where(ok, 1 / np.where(ok, det, 1), 0)
    s = o - T[:, 0]; u = (s * p).sum(1) * inv
    q = np.cross(s, e1); v = (q * d).sum(1) * inv; t = (q * e2).sum(1) * inv
    m = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 0.02)
    return float(t[m].min()) if m.any() else None


def rear_bench(car, eye):
    """A back seat behind the front ones in a car without back doors (a jeep, a two-door SUV): its
    driver's-side eye, found by walking back from the front seat: footwell, a cushion about as high
    as the front one, then the backrest rising. None if there isn't one (a coupe's rear deck, a cab)."""
    T = car.P[car.cls != 'glass']; ex, ey, ez = eye
    up = hit(T, eye, [0, 1, 0]); roof = ey + (up if up is not None and up < 1.2 else 0.4)
    t = hit(T, [ex, ey - 0.25, ez + 0.12], [0, -1, 0])
    seat = ey - 0.25 - t if t is not None else ey - 0.6
    t = hit(T, [ex, seat + 0.3, ez + 0.1], [0, 0, -1])
    z = ez + 0.1 - (t if t is not None and t < 0.6 else 0.3) - 0.1
    ys = []
    for zz in np.arange(z, z - 1.6, -0.03):
        t = hit(T, [ex, roof - 0.03, zz], [0, -1, 0])
        ys.append((zz, None if t is None else roof - 0.03 - t))
    start = None
    for i, (zz, y) in enumerate(ys):
        if y is None:
            return None
        if start is None:
            if abs(y - seat) < 0.12:
                start, cushion = zz, y
        elif y > cushion + 0.1:
            # a backrest is a ridge: it rises, then drops again behind (a coupe's rear deck stays up)
            after = [v for zb, v in ys[i:] if zz - zb < 0.45 and v is not None]
            peak = max(after)
            ridge = any(v < peak - 0.15 for v in after[after.index(peak):])
            if start - zz < 0.25 or not ridge:
                start = None
                continue
            r = np.array([ex * 0.92, cushion + (ey - seat), zz + 0.05])
            h = hit(T, r, [0, 1, 0])
            return r if h is None or h > 0.05 else None
        elif y < cushion - 0.12:
            start = None
    return None


class Kit:
    def __init__(self):
        self.boxes = []; self.obj = 0

    def new(self):
        self.obj += 1
        return self.obj

    def box(self, lo, hi, col, obj, R=None, pivot=None, tex=None, mat='plastic', lit=False):
        """A box from lo to hi, optionally turned by R about pivot (its own centre by default)."""
        lo, hi = np.asarray(lo, float), np.asarray(hi, float)
        c = (lo + hi) / 2; h = np.maximum(np.abs(hi - lo) / 2, 0.0015)
        R = np.eye(3) if R is None else R
        if pivot is not None:
            c = pivot + R @ (c - pivot)
        self.boxes.append(dict(c=c, h=h, R=R, col=np.array(col, float), obj=obj, tex=tex or {}, mat=mat, lit=lit))

    def at(self, c, size, col, obj, R=None, **kw):
        c = np.asarray(c, float); s = np.asarray(size, float) / 2
        self.box(c - s, c + s, col, obj, R=R, pivot=c if R is not None else None, **kw)


# ---------------------------------------------------------------- painted faces

def canvas(w, h, col):
    im = Image.new('RGB', (max(1, int(w)), max(1, int(h))), tuple(int(v * 255) for v in col))
    return im, ImageDraw.Draw(im)


def font(px):
    return ImageFont.truetype(FONT, max(6, int(px)))


def gauges(w_m, h_m, cracked=False):
    W, H = int(w_m * 900), int(h_m * 900)
    im, d = canvas(W, H, (.07, .07, .075))

    def dial(cx, cy, r, ticks, red=None, label=None, needle=0.18):
        d.ellipse((cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3), fill=(120, 122, 126))
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(14, 14, 16))
        for i in range(ticks + 1):
            a = np.radians(225 - 270 * i / ticks); big = i % 2 == 0
            r0 = r * (0.72 if big else 0.82)
            col = (220, 60, 40) if red is not None and i >= red else (235, 232, 220)
            d.line((cx + r0 * np.cos(a), cy - r0 * np.sin(a), cx + r * 0.92 * np.cos(a), cy - r * 0.92 * np.sin(a)), fill=col, width=3 if big else 2)
        a = np.radians(225 - 270 * needle)
        d.line((cx, cy, cx + r * 0.8 * np.cos(a), cy - r * 0.8 * np.sin(a)), fill=(255, 120, 30), width=4)
        d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=(60, 60, 60))
        if label:
            d.text((cx, cy + r * 0.45), label, fill=(200, 196, 186), font=font(r * 0.22), anchor='mm')
    r = H * 0.38
    dial(W * 0.27, H * 0.5, r, 12, label='MPH', needle=0.0)
    dial(W * 0.73, H * 0.5, r, 8, red=6, label='RPM', needle=0.0)
    dial(W * 0.5, H * 0.3, H * 0.15, 4, needle=0.05)    # fuel, all but empty
    dial(W * 0.5, H * 0.72, H * 0.15, 4, needle=0.3)
    for x in (0.06, 0.94):
        s = 8 if x < .5 else -8
        d.polygon([(W * x, H * 0.2), (W * x + s, H * 0.14), (W * x + s, H * 0.26)], fill=(30, 70, 35))
    if cracked:
        x, y = W * 0.6, H * 0.35
        jig = np.random.default_rng(5)
        for a in np.radians([20, 100, 170, 250, 320]):
            pts = [(x, y)]
            for k in range(4):
                a += jig.uniform(-0.4, 0.4)
                pts.append((pts[-1][0] + np.cos(a) * W * 0.05, pts[-1][1] + np.sin(a) * W * 0.05))
            d.line(pts, fill=(200, 205, 210), width=1)
    return arr(im)


def radio(w_m, h_m, text='88.5'):
    W, H = int(w_m * 900), int(h_m * 900)
    im, d = canvas(W, H, (.10, .10, .11))
    d.rectangle((W * 0.28, H * 0.18, W * 0.72, H * 0.52), fill=(18, 30, 22))
    d.text((W * 0.5, H * 0.36), text, fill=(110, 255, 150), font=font(H * 0.26), anchor='mm')
    d.rectangle((W * 0.3, H * 0.62, W * 0.7, H * 0.7), fill=(4, 4, 4))
    for i in range(5):
        x = W * (0.3 + 0.085 * i)
        d.rectangle((x, H * 0.78, x + W * 0.07, H * 0.92), fill=(170, 170, 172))
    return arr(im)


def heater(w_m, h_m):
    W, H = int(w_m * 900), int(h_m * 900)
    im, d = canvas(W, H, (.12, .115, .11))
    for i, x in enumerate((0.2, 0.5, 0.8)):
        d.rectangle((W * (x - 0.12), H * 0.42, W * (x + 0.12), H * 0.58), fill=(10, 10, 10))
        if i == 0:
            for k in range(20):
                t = k / 19
                d.rectangle((W * (x - 0.12 + 0.24 * t), H * 0.25, W * (x - 0.12 + 0.24 * t + 0.013), H * 0.33),
                            fill=(int(60 + 180 * t), 70, int(220 - 180 * t)))
        else:
            d.text((W * x, H * 0.28), ('FAN', 'AIR')[i - 1], fill=(200, 196, 186), font=font(H * 0.17), anchor='mm')
    return arr(im)


def meter(w_m, h_m):
    W, H = int(w_m * 900), int(h_m * 900)
    im, d = canvas(W, H, (.1, .1, .1))
    d.rectangle((W * 0.1, H * 0.15, W * 0.9, H * 0.55), fill=(30, 8, 6))
    d.text((W * 0.5, H * 0.35), '$ 4.70', fill=(255, 70, 40), font=font(H * 0.3), anchor='mm')
    d.text((W * 0.5, H * 0.78), 'FOR HIRE', fill=(220, 210, 190), font=font(H * 0.16), anchor='mm')
    return arr(im)


def grille(w_m, h_m, col=(.12, .12, .12)):
    W, H = int(w_m * 700), int(h_m * 700)
    im, d = canvas(W, H, col)
    for y in range(6, H - 4, 9):
        for x in range(6 + (y // 9 % 2) * 4, W - 4, 9):
            d.ellipse((x - 2.4, y - 2.4, x + 2.4, y + 2.4), fill=(0, 0, 0))
    return arr(im)


def ribs(w_m, h_m):
    W, H = int(w_m * 300), int(h_m * 300)
    im, d = canvas(W, H, (.13, .13, .13))
    for x in range(4, W - 3, 8):
        d.rectangle((x, 5, x + 3, H - 5), fill=(46, 46, 46))
    return arr(im)


def shift(w_m, h_m):
    W, H = int(w_m * 900), int(h_m * 900)
    im, d = canvas(W, H, (.09, .09, .09))
    for i, ch in enumerate('PRND21'):
        d.text((W * 0.22, H * (0.1 + 0.16 * i)), ch, fill=(255, 150, 60) if ch == 'D' else (215, 210, 200), font=font(W * 0.34), anchor='mm')
    d.rectangle((W * 0.5, H * 0.04, W * 0.66, H * 0.96), fill=(2, 2, 2))
    return arr(im)


def stitched(w_m, h_m, col, thread=(.78, .72, .6)):
    W, H = max(8, int(w_m * 400)), max(8, int(h_m * 400))
    im, d = canvas(W, H, col)
    t = tuple(int(v * 255) for v in thread)
    for x in range(7, W - 7, 7):
        d.line((x, 5, x + 3, 5), fill=t, width=1); d.line((x, H - 6, x + 3, H - 6), fill=t, width=1)
    for y in range(7, H - 7, 7):
        d.line((5, y, 5, y + 3), fill=t, width=1); d.line((W - 6, y, W - 6, y + 3), fill=t, width=1)
    return arr(im)


def woodgrain(w_m, h_m):
    W, H = int(w_m * 400), max(6, int(h_m * 400))
    im, d = canvas(W, H, WOOD)
    rng = np.random.default_rng(3)
    for k in range(H // 3):
        y = k * 3 + rng.uniform(0, 2); c = int(70 + rng.uniform(-20, 20))
        d.line([(x, y + 1.2 * np.sin(x / 37 + k)) for x in range(0, W, 6)], fill=(c + 40, c, c // 2), width=1)
    return arr(im)


def mirror_glass(w_m, h_m):
    W, H = int(w_m * 500), int(h_m * 500)
    im, d = canvas(W, H, (.5, .56, .6))
    d.polygon([(W * .1, H), (W * .35, 0), (W * .5, 0), (W * .25, H)], fill=(170, 180, 188))
    return arr(im)


def paper(w_m, h_m, kind):
    W, H = max(8, int(w_m * 600)), max(8, int(h_m * 600))
    im, d = canvas(W, H, (.86, .83, .7))
    if kind == 'map':
        for i in range(6):
            d.line((0, H * i / 6, W, H * (i + .5) / 6), fill=(190, 120, 90), width=2)
        for i in range(4):
            d.line((W * i / 4, 0, W * (i + .3) / 4, H), fill=(110, 140, 180), width=2)
        d.line((W / 2, 0, W / 2, H), fill=(150, 140, 120), width=1)
    else:
        for i in range(1, 6):
            d.line((W * 0.12, H * i / 7, W * 0.88, H * i / 7), fill=(120, 110, 100), width=1)
        d.rectangle((0, 0, W, H * 0.12), fill=(200, 60, 50))
    return arr(im)


def arr(im):
    return np.asarray(im).astype(np.float32) / 255


# ---------------------------------------------------------------- the cabin's shape

def anchors(car, eyes):
    """Where the kit goes, from rays through the undamaged car (model space, y up, +z forward, driver +x)."""
    solid = car.cls != 'glass'
    T, TA = car.P[solid], car.P
    e = np.asarray(eyes[0], float); ex, ey, ez = e
    a = dict(eye=e)
    t = hit(T, e, [0, 1, 0])
    a['roofless'] = t is None or t > 1.2
    a['roof'] = ey + (t if t is not None and t <= 1.2 else 0.3)
    fl = []
    for x in (ex, ex * 0.6, -ex * 0.6, -ex):
        for dz in (0.35, 0.5):
            o = [x, ey - 0.6, ez + dz]
            t = hit(T, o, [0, -1, 0])
            if t is not None and t < 1.2:
                fl.append(o[1] - t)
    a['floor'] = float(np.median(fl)) if fl else ey - 1.0
    t = hit(T, [ex, ey - 0.25, ez + 0.12], [0, -1, 0])
    a['seat'] = ey - 0.25 - t if t is not None and t < 0.8 else a['floor'] + 0.3
    t = hit(T, [0, a['seat'] - 0.04, ez + 0.05], [1, 0, 0])
    a['seat_in'] = t if t is not None and t < ex else ex - 0.25
    t = hit(T, [ex, a['seat'] + 0.3, ez + 0.1], [0, 0, -1])
    a['seat_back'] = ez + 0.1 - (t if t is not None and t < 0.6 else 0.35)
    t = hit(T, [ex, ey - 0.4, ez + 0.1], [1, 0, 0])
    a['door'] = ex + (t if t is not None and t < 0.8 else 0.4)
    a['sill'] = ey - 0.25
    for y in np.arange(ey - 0.6, ey + 0.1, 0.02):
        t = hit(T, [ex, y, ez + 0.1], [1, 0, 0])
        if t is None or ex + t > a['door'] + 0.12:
            a['sill'] = y
            break
    prof = {}
    for name, x in (('mid', 0.12), ('pass', -ex), ('drv', ex)):  # mid just off a split windscreen's post
        rows = []
        for y in np.arange(ey + 0.1, a['floor'], -0.02):
            t = hit(T, [x, y, ez], [0, 0, 1])
            rows.append((float(y), None if t is None else float(ez + t)))
        prof[name] = rows
    a['prof'] = prof
    top = [y for y, z in prof['mid'] if z is not None and z < ez + 1.3 and y < ey - 0.15]
    a['dash_top'] = top[0] if top else ey - 0.2
    t = hit(TA, [0, a['roof'] - 0.04, ez], [0, 0, 1])
    a['header_z'] = ez + t if t is not None and t < 1.5 else ez + 0.5
    t = hit(T, [ex, ey - 0.25, ez], [0, 0, 1])
    a['wheel_z'] = ez + (t if t is not None and t < 1.0 else 0.4)
    # the dials: the pack paints instrument faces from a few palette colours (as lamps.py finds them)
    H, W = car.src.shape[:2]; uvc = car.UV.mean(1)
    src = car.src[np.clip(((uvc[:, 1] % 1) * H).astype(int), 0, H - 1), np.clip(((uvc[:, 0] % 1) * W).astype(int), 0, W - 1)] * 255
    C = car.P.mean(1)
    dial = (np.abs(src[:, None] - DIALS[None]).max(2) < 8).any(1) & (C[:, 0] > 0) & (C[:, 2] > ez + 0.2) & (C[:, 2] < ez + 1.2) \
        & (C[:, 1] > ey - 0.75) & (C[:, 1] < ey - 0.05)
    if dial.any():
        a['cluster'] = np.array([ex, C[dial, 1].mean(), C[dial, 2].min()])
    else:
        t = hit(T, [ex - 0.25, a['dash_top'] - 0.1, ez], [0, 0, 1])
        a['cluster'] = np.array([ex, a['dash_top'] - 0.1, ez + (t if t is not None and t < 1.2 else 0.6)])
    rear = [np.asarray(s, float) for s in eyes[1:] if s[2] < ez - 0.5 and s[0] > 0]
    a['rear'] = rear[0] if rear else None
    if a['rear'] is not None:
        r = a['rear']
        t = hit(T, [r[0], r[1] - 0.35, r[2]], [0, 0, 1])
        a['front_back'] = r[2] + (t if t is not None and t < 1.0 else 0.6)  # back face of the front seat
        t = hit(T, [r[0], r[1] - 0.25, r[2] + 0.1], [0, -1, 0])
        a['rear_seat'] = r[1] - 0.25 - t if t is not None and t < 0.8 else a['seat']
        t = hit(T, [r[0], r[1] - 0.4, r[2]], [1, 0, 0])
        a['rear_door'] = r[0] + (t if t is not None and t < 0.8 else 0.4)
        # the parcel shelf: past the back seat's backrest, where the floor of the window starts
        a['shelf'], a['shelf_y'] = (0, 0), 0
        top, start = 0.0, None
        for z in np.arange(r[2] - 0.1, r[2] - 1.2, -0.03):
            t = hit(T, [0.3, a['roof'] - 0.02, z], [0, -1, 0]); g = hit(TA, [0.3, a['roof'] - 0.02, z], [0, -1, 0])
            if t is None:
                break
            y = a['roof'] - 0.02 - t
            if start is None:
                if top - y > 0.08:
                    start, a['shelf_y'] = z, y
                top = max(top, y)
            elif g is None or t - g < 0.02 or abs(y - a['shelf_y']) > 0.05:
                a['shelf'] = (z + 0.03, start)
                break
    return a


def stack_z(a):
    rows = [z for y, z in a['prof']['mid'] if z is not None and a['dash_top'] - 0.26 < y < a['dash_top'] - 0.03 and z < a['eye'][2] + 1.2]
    return float(np.max(rows)) if rows else a['eye'][2] + 0.6


def face_z(a, side, y):
    rows = [z for yy, z in a['prof'][side] if z is not None and abs(yy - y) < 0.05 and z < a['eye'][2] + 1.3]
    return float(np.min(rows)) if rows else stack_z(a)


def firewall(a, side):
    rows = [z for y, z in a['prof'][side] if z is not None and y < a['floor'] + 0.25 and z < a['eye'][2] + 1.5]
    return float(np.max(rows)) if rows else a['eye'][2] + 0.9


# ---------------------------------------------------------------- the kit

def build(a, style, extras, vname, rng, tint=None, gone=()):
    """The kit's boxes for one cabin, worn and cluttered for the variant."""
    k = Kit()
    rough = vname in ('abandoned', 'wrecked')
    ex, ey, ez = a['eye']; f, s, dt = a['floor'], a['seat'], a['dash_top']
    sz = stack_z(a)
    TRIM, FAB = (.28, .25, .23), (.42, .34, .28)
    if tint is not None:  # the car's own interior colour
        t = np.asarray(tint)
        TRIM = tuple(np.clip(t * 1.3 + 0.05, 0, 1)); FAB = tuple(np.clip(t * 1.9 + 0.06, 0, 1))
    top = dt - 0.03
    bus = style == 'bus'

    # centre stack: vents, radio, heater (on a bus, a radio box on the dash beside the driver)
    o = k.new(); half = 0.17
    sx = ex - 0.45 if bus else 0.0
    if bus:
        sz = face_z(a, 'drv', dt - 0.1)
    bot = s + 0.10 if style == 'car' else dt - 0.33
    k.box([sx - half, bot, sz - 0.07], [sx + half, top, sz + 0.02], TRIM, o)
    for x in (sx - 0.085, sx + 0.085):
        v = k.new()
        k.box([x - 0.07, top - 0.085, sz - 0.085], [x + 0.07, top - 0.02, sz - 0.07], DARK, v)
        for j in range(4):
            if rough and rng.random() < 0.25:
                continue  # a slat snapped off
            y = top - 0.075 + j * 0.016
            k.box([x - 0.062, y, sz - 0.09], [x + 0.062, y + 0.005, sz - 0.074], (.22, .21, .21), v,
                  R=rot(pitch=-20), pivot=np.array([x, y, sz - 0.082]))
    r = k.new(); ry = top - 0.15
    k.box([sx - 0.12, ry - 0.035, sz - 0.09], [sx + 0.12, ry + 0.035, sz - 0.07], CHROME, r, mat='metal')
    k.box([sx - 0.112, ry - 0.028, sz - 0.096], [sx + 0.112, ry + 0.028, sz - 0.09], DARK, r, tex={5: radio(0.224, 0.056)}, lit=True)
    for x in (sx - 0.095, sx + 0.095):
        if not (rough and rng.random() < 0.3):
            k.at([x, ry, sz - 0.104], [0.022, 0.022, 0.016], KNOB, r)
    if style == 'car':
        hy = ry - 0.085; h = k.new()
        k.box([-0.12, hy - 0.035, sz - 0.085], [0.12, hy + 0.035, sz - 0.07], DARK, h, tex={5: heater(0.24, 0.07)})
        for x in (-0.072, 0.0, 0.072):
            k.at([x, hy - 0.002, sz - 0.092], [0.012, 0.018, 0.016], (.75, .74, .7), h)

    # gauge hood round the driver's dials
    cx, cy, cz = a['cluster']
    b = k.new(); gw, gh = 0.36, 0.15
    k.box([cx - gw / 2 - 0.02, cy - gh / 2 - 0.015, cz - 0.03], [cx + gw / 2 + 0.02, cy + gh / 2 + 0.015, cz + 0.03], DARK, b)
    k.box([cx - gw / 2 - 0.03, cy + gh / 2 + 0.015, cz - 0.11], [cx + gw / 2 + 0.03, cy + gh / 2 + 0.04, cz + 0.03], DARK, b)
    k.box([cx - gw / 2, cy - gh / 2, cz - 0.04], [cx + gw / 2, cy + gh / 2, cz - 0.03], (.05, .05, .05), b,
          tex={5: gauges(gw, gh, cracked=vname == 'wrecked')}, lit=True, mat='glass')

    # glovebox, hanging open on a rough car, with its junk spilling
    if not bus:
        g = k.new(); gy = dt - 0.15; gz = face_z(a, 'pass', gy)
        if rough and rng.random() < 0.6:
            hinge = np.array([0, gy - 0.07, gz - 0.025]); R = rot(pitch=-75)
            k.box([-ex - 0.19, gy - 0.07, gz - 0.025], [-ex + 0.17, gy + 0.07, gz - 0.005], TRIM, g, R=R, pivot=hinge)
            k.box([-ex - 0.17, gy - 0.06, gz - 0.005], [-ex + 0.15, gy + 0.06, gz + 0.01], (.03, .03, .03), g)
            p = k.new()
            k.box([-ex - 0.1, gy - 0.075, gz - 0.12], [-ex + 0.04, gy - 0.07, gz - 0.02], (.86, .83, .7), p,
                  R=rot(yaw=12), tex={2: paper(0.14, 0.1, 'map')}, mat='paper')
        else:
            k.box([-ex - 0.19, gy - 0.07, gz - 0.025], [-ex + 0.17, gy + 0.07, gz + 0.01], TRIM, g)
            k.at([-ex - 0.01, gy + 0.045, gz - 0.032], [0.07, 0.016, 0.016], CHROME, g, mat='metal')

    # vents at each end of the dash
    for side, x in (('drv', a['door'] - 0.14), ('pass', -a['door'] + 0.14)):
        if bus and side == 'pass':
            continue
        vz = face_z(a, side, dt - 0.07); v = k.new()
        k.box([x - 0.06, dt - 0.1, vz - 0.03], [x + 0.06, dt - 0.035, vz + 0.01], DARK, v)
        for j in range(4):
            y = dt - 0.093 + j * 0.016
            k.box([x - 0.052, y, vz - 0.036], [x + 0.052, y + 0.005, vz - 0.02], (.22, .21, .21), v)

    if 'meter' in extras:  # taxi meter on the dash top
        m = k.new(); mz = face_z(a, 'mid', dt - 0.02) - 0.02
        k.box([-0.08, dt, mz - 0.07], [0.08, dt + 0.09, mz], (.12, .12, .12), m)
        k.box([-0.075, dt + 0.005, mz - 0.075], [0.075, dt + 0.085, mz - 0.07], (.1, .1, .1), m, tex={5: meter(0.15, 0.08)}, lit=True)

    cw = min(0.13, a['seat_in'] - 0.04)
    if style == 'car' and cw > 0.07:
        # console, gear lever, cup holders, armrest lid, handbrake
        c = k.new()
        k.box([-cw, f, a['seat_back'] - 0.05], [cw, s + 0.07, sz - 0.07], TRIM, c)
        lid_lo = a['seat_back'] - 0.06
        k.box([-cw - 0.01, s + 0.07, lid_lo], [cw + 0.01, s + 0.13, ez + 0.02], FAB, c,
              tex={2: stitched(2 * cw + 0.02, ez + 0.02 - lid_lo, FAB)}, mat='fabric')
        gz = sz - 0.2
        k.box([-0.045, s + 0.07, gz - 0.09], [0.045, s + 0.078, gz + 0.09], (.09, .09, .09), c, tex={2: shift(0.09, 0.18)})
        gl = k.new(); lean = rot(pitch=-12)
        base = np.array([0.0, s + 0.078, gz - 0.02])
        for i, (w, hh) in enumerate(((0.07, 0.03), (0.055, 0.03), (0.04, 0.03))):
            k.at(base + [0, hh / 2 + i * 0.028, 0], [w, hh, w], (.12, .11, .10), gl, R=lean, mat='fabric')
        k.at(base + [0, 0.14, -0.012], [0.012, 0.09, 0.012], CHROME, gl, R=lean, mat='metal')
        k.at(base + [0, 0.2, -0.022], [0.04, 0.045, 0.045], KNOB, gl, R=lean)
        cupz = (gz - 0.09 + ez + 0.02) / 2
        if 'police' in extras:
            pr = k.new(); rz = cupz
            R = rot(pitch=25); c0 = np.array([0.0, s + 0.14, rz])
            k.box([-0.1, s + 0.07, rz - 0.06], [0.1, s + 0.1, rz + 0.06], STEEL, pr, mat='metal')
            k.at(c0, [0.24, 0.09, 0.15], (.08, .08, .09), pr, R=R)
            k.at(c0 + R @ [0, 0, -0.076], [0.22, 0.07, 0.004], DARK, pr, R=R, tex={5: radio(0.22, 0.07, 'CH 4')}, lit=True)
            mic = k.new(); mp = np.array([0.17 + 0.02, top - 0.2, sz - 0.05])
            k.at(mp, [0.035, 0.085, 0.05], KNOB, mic)
            end = c0 + [0.1, 0.0, 0.03]; st = mp + [0, -0.045, 0]
            for j in range(16):
                u = j / 15
                p = st * (1 - u) + end * u + [0.012 * np.cos(j * 2.2), -0.09 * np.sin(np.pi * u), 0.012 * np.sin(j * 2.2)]
                k.at(p, [0.011, 0.011, 0.011], (.04, .04, .04), mic)
        else:
            for x in (-0.05, 0.05):
                cu = k.new()
                k.box([x - 0.04, s + 0.07, cupz - 0.04], [x + 0.04, s + 0.074, cupz + 0.04], (.06, .06, .06), cu)
                for dx, dz, w, d2 in ((-0.04, 0, 0.008, 0.088), (0.04, 0, 0.008, 0.088), (0, -0.04, 0.088, 0.008), (0, 0.04, 0.088, 0.008)):
                    k.at([x + dx, s + 0.078, cupz + dz], [w, 0.012, d2], (.2, .19, .18), cu)
            if rng.random() < 0.7:
                cp = k.new(); x = -0.05
                k.at([x, s + 0.12, cupz], [0.062, 0.1, 0.062], (.9, .89, .85), cp, mat='paper')
                k.at([x, s + 0.105, cupz], [0.066, 0.03, 0.066], (.55, .32, .18), cp)
                if not rough:
                    k.at([x, s + 0.176, cupz], [0.068, 0.012, 0.068], (.95, .95, .95), cp)
        hb = k.new(); hx = cw - 0.012
        piv = np.array([hx, s + 0.07, ez - 0.02]); R = rot(pitch=-22)
        k.box(piv + [-0.012, 0, 0], piv + [0.012, 0.03, 0.26], (.10, .10, .10), hb, R=R, pivot=piv)
        k.box(piv + [-0.016, -0.004, 0.17], piv + [0.016, 0.034, 0.27], (.14, .13, .12), hb, R=R, pivot=piv, mat='fabric')
        k.box(piv + [-0.007, 0.006, 0.27], piv + [0.007, 0.022, 0.29], CHROME, hb, R=R, pivot=piv, mat='metal')
        if a['rear'] is not None:  # an ashtray and a vent on the console's back for the back seat
            ab = k.new(); bz = a['seat_back'] - 0.05
            k.box([-0.08, s - 0.12, bz - 0.012], [0.08, s - 0.04, bz], CHROME, ab, mat='metal')
            k.box([-0.06, s + 0.0, bz - 0.01], [0.06, s + 0.05, bz], DARK, ab)
            for j in range(3):
                k.box([-0.055, s + 0.008 + j * 0.014, bz - 0.016], [0.055, s + 0.012 + j * 0.014, bz - 0.01], (.22, .21, .21), ab)
    elif style == 'work':
        cs = k.new(); wz = a['wheel_z']
        piv = np.array([ex - 0.06, dt - 0.1, wz + 0.1]); R = rot(yaw=62, pitch=-14)  # out to the right, a little back and down
        k.box(piv + [-0.006, -0.006, -0.15], piv + [0.006, 0.006, 0], (.2, .2, .2), cs, R=R, pivot=piv)
        k.at(piv + R @ np.array([0, 0, -0.15]), [0.03, 0.03, 0.05], KNOB, cs, R=R)
        if a['seat_in'] < 0.25:
            ar = k.new()
            k.box([-0.12, s + 0.06, a['seat_back'] - 0.02], [0.12, s + 0.2, ez + 0.05], FAB, ar,
                  tex={2: stitched(0.24, ez + 0.07 - a['seat_back'], FAB)}, mat='fabric')
        if 'cb' in extras:
            cb = k.new(); cby = dt - 0.28; cx2 = -ex + 0.05; cz2 = face_z(a, 'pass', dt - 0.15) + 0.04
            k.box([cx2 - 0.11, cby - 0.03, cz2 - 0.18], [cx2 + 0.11, cby + 0.03, cz2], (.2, .2, .21), cb)
            k.box([cx2 - 0.1, cby - 0.022, cz2 - 0.185], [cx2 + 0.1, cby + 0.022, cz2 - 0.18], DARK, cb, tex={5: radio(0.2, 0.044, 'CH 19')}, lit=True)
            for x in (cx2 - 0.118, cx2 + 0.112):
                k.box([x, cby - 0.035, cz2 - 0.15], [x + 0.006, dt - 0.2, cz2 - 0.03], (.17, .17, .18), cb, mat='metal')
            m = k.new()
            k.at([cx2 + 0.15, cby + 0.02, cz2 - 0.1], [0.035, 0.075, 0.05], KNOB, m)
            for j in range(12):
                u = j / 11
                k.at([cx2 + 0.15 - 0.04 * u + 0.01 * np.cos(j * 2.2), cby - 0.02 - 0.06 * np.sin(np.pi * u), cz2 - 0.1 + 0.01 * np.sin(j * 2.2)],
                     [0.011, 0.011, 0.011], (.04, .04, .04), m)

    # pedals and mats
    fw = firewall(a, 'drv')
    for x, w, hgt in ((ex - 0.04, 0.11, 0.07), (ex - 0.2, 0.05, 0.11)):
        p = k.new()
        k.at([x, f + 0.13, fw - 0.2], [w, hgt, 0.014], RUBBER, p, R=rot(pitch=-35), mat='rubber')
        k.box([x - 0.008, f + 0.13, fw - 0.2], [x + 0.008, min(dt - 0.25, f + 0.45), fw - 0.19], STEEL, p, mat='metal')
    mats = [(a['seat_in'] + 0.02, a['door'] - 0.05, ez + 0.12, fw - 0.06)]
    if not bus:
        mats.append((-a['door'] + 0.05, -a['seat_in'] - 0.02, ez + 0.12, firewall(a, 'pass') - 0.06))
    if a['rear'] is not None:
        for sg in (1, -1):
            x0, x1 = sorted([sg * 0.12, sg * (a['rear_door'] - 0.06)])
            mats.append((x0, x1, a['rear'][2] + 0.18, a['seat_back'] - 0.08))
    for x0, x1, z0, z1 in mats:
        if x1 - x0 < 0.1 or z1 - z0 < 0.1 or (vname == 'wrecked' and rng.random() < 0.3):
            continue
        m = k.new()
        R = rot(yaw=rng.uniform(-6, 6) if rough else 0)
        k.box([x0, f + 0.004, z0], [x1, f + 0.016, z1], (.13, .13, .13), m, R=R, tex={2: ribs(x1 - x0, z1 - z0)}, mat='rubber')

    # roof: mirror, visors, dome light, a sagging headliner on a rough car
    if not a['roofless']:
        rf, hz = a['roof'], a['header_z']
        mi = k.new(); mR = rot(yaw=-8 + (rng.uniform(15, 30) if rough else 0), roll=rng.uniform(-12, 12) if rough else 0)
        k.box([-0.012, rf - 0.07, hz - 0.06], [0.012, rf - 0.005, hz - 0.035], DARK, mi)
        mc = np.array([0.0, rf - 0.1, hz - 0.07])
        k.at(mc, [0.25, 0.065, 0.03], DARK, mi, R=mR)
        k.at(mc + mR @ [0, 0, -0.016], [0.23, 0.05, 0.004], (.6, .66, .7), mi, R=mR, tex={5: mirror_glass(0.23, 0.05)}, mat='glass')
        if rng.random() < 0.5 and not bus:
            fr = k.new()
            k.box([0.05, rf - 0.2, hz - 0.075], [0.052, rf - 0.13, hz - 0.073], (.85, .85, .8), fr)
            y = rf - 0.268
            for w, hh in ((0.045, 0.025), (0.036, 0.028), (0.024, 0.025), (0.008, 0.016)):
                k.box([0.051 - w / 2, y, hz - 0.076], [0.051 + w / 2, y + hh, hz - 0.072],
                      (.1, .5, .2) if vname == 'usable' else (.35, .42, .25), fr, mat='paper')
                y += hh * 0.8
        for x0, x1, drv in ((0.05, a['door'] - 0.08, True), (-a['door'] + 0.08, -0.05, False)):
            if bus and not drv:
                continue
            vi = k.new(); vc = (.66, .6, .52)
            down = rough and not drv and rng.random() < 0.6
            hinge = np.array([0, rf - 0.03, hz - 0.03])
            R = rot(pitch=80) if down else None
            k.box([x0, rf - 0.045, hz - 0.3], [x1, rf - 0.02, hz - 0.03], vc, vi, R=R, pivot=hinge if down else None,
                  tex={3: stitched(x1 - x0, 0.27, vc, (.52, .47, .4))}, mat='fabric')
            k.box([x1 - 0.02, rf - 0.03, hz - 0.08], [x1 + 0.015, rf, hz - 0.05], (.35, .33, .3), vi)
            if drv and rng.random() < 0.5:
                t = k.new()
                k.box([x0 + 0.1, rf - 0.048, hz - 0.2], [x0 + 0.2, rf - 0.045, hz - 0.11], (.95, .93, .7), t,
                      tex={3: paper(0.1, 0.09, 'note')}, mat='paper')
        dl = k.new(); dz = a['seat_back'] + 0.05
        k.box([-0.08, rf - 0.025, dz - 0.04], [0.08, rf, dz + 0.04], (.3, .28, .26), dl)
        k.box([-0.065, rf - 0.03, dz - 0.03], [0.065, rf - 0.025, dz + 0.03], CREAM, dl, mat='glass')
        if rough and rng.random() < 0.7:  # the headliner's come unglued and hangs down
            hl = k.new(); z0 = (a['rear'][2] if a['rear'] is not None else a['seat_back']) - 0.1
            piv = np.array([0.0, rf - 0.01, z0 + 0.35])
            k.box([-0.35, rf - 0.012, z0], [0.3, rf - 0.004, z0 + 0.35], (.7, .64, .55), hl, R=rot(pitch=-28, roll=6), pivot=piv, mat='fabric')

    # door cards on every door with someone beside it
    doors = [(1, ez, a['door'], True), (-1, ez, a['door'], True)]
    if bus:
        doors = []
    if a['rear'] is not None:
        doors += [(1, a['rear'][2], a['rear_door'], False), (-1, a['rear'][2], a['rear_door'], False)]
    for sg, z, dx0, front in doors:
        if any(gs == sg and z0 - 0.1 < z < z1 + 0.1 for gs, z0, z1 in gone):
            continue  # that door hangs open or is gone: its own inside panel went with it
        dx = sg * dx0; inward = -sg; sl = a['sill']
        lo_y = f
        d = k.new()
        ax0, ax1 = sorted([dx, dx + inward * 0.075])
        k.box([ax0, sl - 0.27, z - 0.25], [ax1, sl - 0.2, z + 0.32], TRIM, d)
        k.box([ax0 - 0.003, sl - 0.2, z - 0.22], [ax1 + 0.003, sl - 0.19, z + 0.12], FAB, d,
              tex={2: stitched(0.078, 0.34, FAB)}, mat='fabric')
        tx0, tx1 = sorted([dx, dx + inward * 0.012])
        if style == 'car':
            k.box([tx0, sl - 0.06, z - 0.25], [tx1, sl - 0.03, z + 0.4], WOOD, d, tex={(1 if sg > 0 else 0): woodgrain(0.65, 0.03)}, mat='wood')
        else:
            k.box([tx0, sl - 0.06, z - 0.25], [tx1, sl - 0.03, z + 0.4], CHROME, d, mat='metal')
        hd = k.new(); hx0, hx1 = sorted([dx, dx + inward * 0.02])
        k.box([hx0, sl - 0.115, z + 0.26], [hx1, sl - 0.095, z + 0.36], CHROME, hd, mat='metal')
        k.box([hx0, sl - 0.13, z + 0.24], [hx0 + (hx1 - hx0) * 0.4, sl - 0.08, z + 0.38], DARK, hd)
        cr = k.new(); hub = np.array([dx + inward * 0.025, sl - 0.14, z + 0.05])
        k.at(hub, [0.05, 0.045, 0.045], DARK, cr)
        k.at(hub + [inward * 0.032, 0, 0], [0.014, 0.022, 0.022], CHROME, cr, mat='metal')
        arm = rot(pitch=35 + rng.uniform(-60, 60))
        k.box(hub + [inward * 0.04 - 0.006, -0.008, -0.1], hub + [inward * 0.04 + 0.006, 0.008, 0.0], CHROME, cr, R=arm, pivot=hub, mat='metal')
        if not (rough and rng.random() < 0.25):
            k.at(hub + arm @ np.array([0, 0, -0.095]) + [inward * 0.06, 0, 0], [0.05, 0.022, 0.022], KNOB, cr)
        lk = k.new()
        k.box([dx + inward * 0.03 - 0.008, sl - 0.01, z - 0.2], [dx + inward * 0.03 + 0.008, sl + 0.05, z - 0.184], CHROME, lk, mat='metal')
        if front:
            sp = k.new(); sx0, sx1 = sorted([dx, dx + inward * 0.015])
            k.box([sx0, lo_y + 0.1, z + 0.36], [sx1, lo_y + 0.26, z + 0.56], (.12, .12, .12), sp, tex={(1 if sg > 0 else 0): grille(0.2, 0.16)})
        else:
            at = k.new(); ay0 = sl - 0.2
            k.box([ax0 + 0.01, ay0 + 0.001, z + 0.16], [ax1 - 0.01, ay0 + 0.012, z + 0.28], CHROME, at, mat='metal')
        mp = k.new(); px0, px1 = sorted([dx, dx + inward * 0.045])
        k.box([px0, lo_y + 0.08, z - 0.2], [px1, lo_y + 0.2, z + 0.3], TRIM, mp)
        if front and sg < 0 and rng.random() < 0.6:
            m = k.new(); p0, p1 = sorted([dx + inward * 0.045, dx + inward * 0.05])
            k.box([p0, lo_y + 0.17, z - 0.05], [p1, lo_y + 0.27, z + 0.12], (.86, .83, .7), m,
                  R=rot(roll=8 * sg), pivot=np.array([dx, lo_y + 0.2, z]), tex={(1 if sg > 0 else 0): paper(0.17, 0.1, 'map')}, mat='paper')
        # the belt hanging on the pillar behind the seat
        bt = k.new(); bx = dx + inward * 0.04; bz = (a['seat_back'] if front else a['rear'][2] - 0.25) - 0.03
        k.box([bx - 0.004, s + 0.15, bz - 0.025], [bx + 0.004, a['roof'] - 0.12 if not a['roofless'] else sl + 0.25, bz + 0.025],
              (.15, .15, .16), bt, mat='fabric')
        k.box([bx - 0.008, s + 0.12, bz - 0.02], [bx + 0.008, s + 0.17, bz + 0.02], CHROME, bt, mat='metal')
        if not front and not a['roofless']:
            gh = k.new(); gy = a['roof'] - 0.06; gx = dx + inward * 0.06
            k.box([gx - 0.015, gy, z - 0.1], [gx + 0.015, gy + 0.02, z + 0.1], (.2, .19, .18), gh)
            k.box([gx - 0.015, gy + 0.02, z - 0.1], [gx + 0.015, a['roof'], z - 0.08], (.2, .19, .18), gh)
            k.box([gx - 0.015, gy + 0.02, z + 0.08], [gx + 0.015, a['roof'], z + 0.1], (.2, .19, .18), gh)

    # the back seat: pockets on the front seats' backs, a shelf under the back window
    if a['rear'] is not None:
        fb = a['front_back']; rs = a['rear_seat']
        for sg in (1, -1):
            pk = k.new(); x = sg * ex
            k.box([x - 0.17, rs + 0.02, fb - 0.025], [x + 0.17, rs + 0.24, fb - 0.002], FAB, pk,
                  tex={5: stitched(0.34, 0.22, FAB)}, mat='fabric')
            if rng.random() < 0.5:
                k.box([x - 0.12, rs + 0.18, fb - 0.02], [x + 0.08, rs + 0.27, fb - 0.012], (.82, .78, .62), pk,
                      tex={5: paper(0.2, 0.09, 'note')}, mat='paper')
        if style == 'car' and 'shelf' in extras and a['shelf'][1] - a['shelf'][0] > 0.15:
            sh = k.new(); z0, z1 = a['shelf']; y = a['shelf_y'] + 0.01
            k.box([-a['rear_door'] + 0.05, y - 0.02, z0], [a['rear_door'] - 0.05, y, z1], TRIM, sh)
            for x in (-0.5, 0.5):
                k.box([x - 0.1, y, z0 + 0.04], [x + 0.1, y + 0.012, min(z1, z0 + 0.18)], (.12, .12, .12), sh, tex={2: grille(0.2, 0.14)})
            if rng.random() < 0.6:
                tb = k.new()
                k.at([0.15, y + 0.035, (z0 + z1) / 2], [0.22, 0.07, 0.12], (.75, .7, .6), tb, R=rot(yaw=20), mat='paper')

    # trash on the floor
    if vname != 'burnt':
        n = {'usable': 2, 'abandoned': 6, 'wrecked': 9}[vname]
        spots = [(rng.uniform(-a['door'] + 0.12, a['door'] - 0.12), rng.uniform(ez + 0.15, ez + 0.45)) for _ in range(n)]
        if a['rear'] is not None:
            spots += [(rng.uniform(-0.5, 0.5), rng.uniform(a['rear'][2] + 0.15, a['seat_back'] - 0.1)) for _ in range(n // 2)]
        for x, z in spots:
            if abs(x) < cw + 0.02 and style == 'car':
                continue
            t = k.new(); kind = rng.integers(4); R = rot(yaw=rng.uniform(0, 180))
            if kind == 0:   # a crushed can
                k.at([x, f + 0.03, z], [0.065, 0.03, 0.11], [(.7, .1, .1), (.2, .3, .6), (.75, .72, .66)][rng.integers(3)], t, R=R, mat='metal')
            elif kind == 1:  # a wrapper
                k.at([x, f + 0.02, z], [0.12, 0.006, 0.09], [(.85, .8, .25), (.85, .85, .82), (.6, .15, .1)][rng.integers(3)], t, R=R @ rot(roll=6), mat='paper')
            elif kind == 2:  # leaves
                for j in range(3):
                    k.at([x + rng.uniform(-0.05, 0.05), f + 0.02, z + rng.uniform(-0.05, 0.05)], [0.05, 0.004, 0.035],
                         (.45, .3, .12) if j % 2 else (.35, .3, .1), t, R=rot(yaw=rng.uniform(0, 180)), mat='paper')
            else:            # a bottle on its side
                k.at([x, f + 0.035, z], [0.055, 0.055, 0.16], (.16, .2, .1), t, R=R, mat='glass')
                k.at([x, f + 0.035, z] + R @ np.array([0, 0, 0.1]), [0.022, 0.022, 0.05], (.16, .2, .1), t, R=R, mat='glass')
    return k


# ---------------------------------------------------------------- paint and bake

# per face (axis, sign): the face's right and up axes as (axis, sign), as seen from outside it
FACE = {(0, 1): ((2, -1), (1, 1)), (0, -1): ((2, 1), (1, 1)), (1, 1): ((0, -1), (2, 1)),
        (1, -1): ((0, 1), (2, 1)), (2, 1): ((0, 1), (1, 1)), (2, -1): ((0, -1), (1, 1))}
FIDX = {(0, 1): 0, (0, -1): 1, (1, 1): 2, (1, -1): 3, (2, 1): 4, (2, -1): 5}


def inside(b, p, eps=1e-4):
    q = (p - b['c']) @ b['R']
    return (np.abs(q) <= b['h'] + eps).all(-1)


def rim_mask(b, ax, sg, w, h, others):
    """Outline along the face's edges, except where another part of the same object carries on past it."""
    (ra, rs), (ua, us) = FACE[(ax, sg)]
    rim = np.zeros((h, w), bool)
    eps = 0.002

    def covered(s, t, out_axis, out_sign):
        q = np.zeros((len(s), 3))
        q[:, ax] = sg * (b['h'][ax] - eps)
        q[:, ra] = rs * (s * 2 - 1) * b['h'][ra]
        q[:, ua] = us * (t * 2 - 1) * b['h'][ua]
        q[:, out_axis] += out_sign * eps
        p = b['c'] + q @ b['R'].T
        hidden = np.zeros(len(s), bool)
        for o in others:
            hidden |= inside(o, p)
        return hidden
    us_ = (np.arange(w) + 0.5) / w; vs_ = (np.arange(h) + 0.5) / h
    rim[0, :] = ~covered(us_, np.ones(w), ua, us)       # top edge
    rim[-1, :] = ~covered(us_, np.zeros(w), ua, -us)     # bottom edge
    rim[:, 0] |= ~covered(np.zeros(h), 1 - vs_, ra, -rs)
    rim[:, -1] |= ~covered(np.ones(h), 1 - vs_, ra, rs)
    return rim


def noise(h, w, rng, cell=6):
    g = rng.random((h // cell + 2, w // cell + 2))
    im = Image.fromarray((g * 255).astype(np.uint8)).resize(((w // cell + 2) * cell, (h // cell + 2) * cell), Image.BILINEAR)
    return np.asarray(im).astype(np.float32)[:h, :w] / 255


def paint_face(b, ax, sg, w, h, rim, wear, rng, burnt, world_up):
    fi = FIDX[(ax, sg)]
    tex = b['tex'].get(fi)
    if tex is not None:
        img = np.asarray(Image.fromarray((tex * 255).astype(np.uint8)).resize((w, h), Image.LANCZOS)).astype(np.float32) / 255
    else:
        img = np.ones((h, w, 3), np.float32) * b['col']
    mat = b['mat']
    yy, xx = np.mgrid[0:h, 0:w]
    up = world_up > 0.7; down = world_up < -0.5
    mott = noise(h, w, rng)
    img *= (1 + (mott[..., None] - 0.5) * 0.12 * (0.5 + wear))
    if mat == 'fabric':
        img *= (1 + 0.035 * (((xx + yy) % 2) - 0.5))[..., None]
    if up and mat not in ('glass',):
        fade = 0.12 * wear * (0.6 + 0.4 * mott)
        img = img * (1 - fade[..., None]) + np.array([.62, .58, .52]) * fade[..., None]
        dust = (rng.random((h, w)) < 0.05 * wear) | (noise(h, w, rng, 4) > 1 - 0.12 * wear)
        img[dust] = img[dust] * 0.75 + np.array([.5, .46, .4]) * 0.25
    if mat in ('plastic', 'wood') and min(w, h) > 6:
        for _ in range(rng.poisson(2.5 * wear * max(1, w * h / 4000))):
            x0, y0 = rng.integers(w), rng.integers(h); L = rng.integers(3, 12); a = rng.uniform(0, np.pi)
            for t in range(L):
                x, y = int(x0 + np.cos(a) * t), int(y0 + np.sin(a) * t)
                if 0 <= x < w and 0 <= y < h:
                    img[y, x] = np.minimum(img[y, x] * 1.35 + 0.04, 1)
    if mat == 'fabric' and min(w, h) > 8:
        for _ in range(rng.poisson(1.2 * wear)):
            cx, cy, r = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(2, max(3, min(w, h) / 3))
            m = np.clip(1 - np.hypot(xx - cx, yy - cy) / r, 0, 1) * 0.3
            img *= (1 - m[..., None])
        if up and wear > 0.7 and rng.random() < 0.6 and min(w, h) > 14:
            x, y = rng.uniform(w * 0.2, w * 0.5), rng.uniform(h * 0.2, h * 0.8)
            for t in range(int(min(w, h) * 0.5)):
                x += 1; y += rng.uniform(-1, 1)
                xi, yi = int(x), int(np.clip(y, 0, h - 2))
                if xi < w:
                    img[yi, xi] = (.92, .82, .5)      # the foam showing through
                    img[yi + 1, xi] = (.08, .07, .06)
    if mat == 'metal':
        spots = rng.random((h, w)) < 0.025 * wear
        img[spots] = (.42, .24, .12)
    if burnt:
        char = np.array([.07, .06, .055]) * (0.8 + 0.4 * mott[..., None])
        rust = (mott > 0.62)[..., None] * np.array([.3, .14, .06])
        img = img * 0.12 + char * 0.88 + rust * (0.3 if mat == 'metal' else 0.1)
    if up:
        img = img * 1.06 + 0.01
    elif down:
        img = img * 0.68
    if min(w, h) < 3:
        img *= 0.85
    else:
        near = np.zeros_like(rim)
        near[1:] |= rim[:-1]; near[:-1] |= rim[1:]; near[:, 1:] |= rim[:, :-1]; near[:, :-1] |= rim[:, 1:]
        near &= ~rim
        if not up and not down:
            img[1][near[1]] = np.minimum(img[1][near[1]] * 1.25 + 0.03, 1)    # lit line under the top edge
        chip = near & (rng.random((h, w)) < 0.25 * wear)
        img[chip] = np.minimum(img[chip] * 1.2 + 0.03, 1)
        img[rim] = (.05, .045, .04)
    return np.clip(img, 0, 1)


def bake(kit, wear, rng, burnt, width):
    """Every face of every box painted and shelf-packed onto a strip width pixels wide.
    Returns triangles (n,3,3), their uv in strip pixels (n,3,2), which are lit, and the strip image."""
    faces = []
    for bi, b in enumerate(kit.boxes):
        others = [o for o in kit.boxes if o['obj'] == b['obj'] and o is not b]
        for (ax, sg), ((ra, rs), (ua, us)) in FACE.items():
            fi = FIDX[(ax, sg)]
            dens = PPM
            tex = b['tex'].get(fi)
            if tex is not None:
                dens = max(PPM, min(tex.shape[1] / (2 * b['h'][ra]), 900))
            w = int(np.clip(np.ceil(2 * b['h'][ra] * dens), 1, width - 2))
            h = int(np.clip(np.ceil(2 * b['h'][ua] * dens), 1, 512))
            rim = rim_mask(b, ax, sg, w, h, others)
            n = b['R'][:, ax] * sg
            img = paint_face(b, ax, sg, w, h, rim, wear, rng, burnt, n[1])
            faces.append((bi, ax, sg, img))
    order = sorted(range(len(faces)), key=lambda i: -faces[i][3].shape[0])
    x = y = row = 0; place = {}
    for i in order:
        h, w = faces[i][3].shape[:2]
        if x + w + 1 > width:
            x, y, row = 0, y + row + 1, 0
        place[i] = (x, y); x += w + 1; row = max(row, h)
    H = (y + row + 1 + 7) // 8 * 8
    strip = np.zeros((H, width, 3), np.float32)
    tris, uvs, lit = [], [], []
    for i, (bi, ax, sg, img) in enumerate(faces):
        b = kit.boxes[bi]; px, py = place[i]; h, w = img.shape[:2]
        strip[py:py + h, px:px + w] = img
        (ra, rs), (ua, us) = FACE[(ax, sg)]
        pts, uv = [], []
        for cr, cu in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            q = np.zeros(3); q[ax] = sg * b['h'][ax]; q[ra] = rs * cr * b['h'][ra]; q[ua] = us * cu * b['h'][ua]
            pts.append(b['c'] + b['R'] @ q)
            uv.append([px + (cr + 1) / 2 * w, py + (1 - (cu + 1) / 2) * h])
        tris += [[pts[0], pts[1], pts[2]], [pts[0], pts[2], pts[3]]]
        uvs += [[uv[0], uv[1], uv[2]], [uv[0], uv[2], uv[3]]]
        on = b['lit'] and FIDX[(ax, sg)] in b['tex'] and not burnt
        lit += [on, on]
    return np.array(tris), np.array(uvs), np.array(lit, bool), strip


def stock_console(P, inside_mask, a, style):
    """The car's own console and lever where the kit's console goes, to be taken out."""
    if style != 'car' or min(0.13, a['seat_in'] - 0.04) <= 0.07:
        return np.zeros(len(P), bool)
    ex, ey, ez = a['eye']; cw = min(0.13, a['seat_in'] - 0.04)
    C = P.mean(1)
    return inside_mask & (np.abs(C[:, 0]) < cw + 0.06) & (C[:, 2] > a['seat_back'] - 0.1) & (C[:, 2] < stack_z(a) - 0.05) \
        & (C[:, 1] > a['floor'] + 0.02) & (C[:, 1] < a['seat'] + 0.4)


def fit(car, eyes, vname, seed, P, origin, img):
    """The kit for one variant of a car: (triangles, uv 0-1 against the grown atlas, lit, grown atlas
    image, the factor the old v coordinates shrink by, and which of P's triangles to drop)."""
    if not hasattr(car, 'kit_anchors'):
        car.kit_anchors = anchors(car, eyes)
    a = car.kit_anchors
    style = STYLE.get(car.name, 'car')
    extras = set(EXTRAS.get(car.name, ()))
    if car.name in SHELF:
        extras.add('shelf')
    ins = car.cls == 'interior'
    sel = ins & (car.cls != 'glass')
    tint = None
    if sel.any():
        H, W = car.src.shape[:2]; uvc = car.UV[sel].mean(1)
        cols = car.src[np.clip(((uvc[:, 1] % 1) * H).astype(int), 0, H - 1), np.clip(((uvc[:, 0] % 1) * W).astype(int), 0, W - 1)]
        tint = np.median(cols, 0) * 0.75
    gone = []
    for i, p in enumerate(car.parts):
        if p['cls'] != 'door' or p.get('kind') != 'side':
            continue
        mine = (origin >= 0) & (car.pid[np.maximum(origin, 0)] == i)
        lo, hi = car.P[car.pid == i].reshape(-1, 3).min(0), car.P[car.pid == i].reshape(-1, 3).max(0)
        if not mine.any() or np.abs(P[mine] - car.P[origin[mine]]).max() > 0.01:
            gone.append((1 if lo[0] + hi[0] > 0 else -1, lo[2], hi[2]))
    rng = np.random.default_rng(seed + 7)
    kit = build(a, style, extras, vname, rng, tint, gone)
    tris, uvpx, lit, strip = bake(kit, WEAR[vname], rng, vname == 'burnt', img.shape[1])
    H0 = img.shape[0]
    grown = np.concatenate([img, strip], 0)
    uv = uvpx.copy(); uv[..., 1] += H0
    uv /= np.array([grown.shape[1], grown.shape[0]])
    drop = stock_console(P, np.where(origin >= 0, ins[np.maximum(origin, 0)], False), a, style)
    return tris, uv, lit, grown, H0 / grown.shape[0], drop
