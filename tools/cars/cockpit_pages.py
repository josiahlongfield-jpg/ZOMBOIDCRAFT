"""What the cockpit screens show with the avionics on and the aircraft sat on a deck: each page drawn
fresh (nothing taken from the bought models), in the colours the real displays use at night, for
cockpit_glow.py to light over the screens. Made-up readouts only, no real call signs or names.

Each painter takes the page's size in pixels and returns an RGB float image (0 to 1) on black:
black is no light, so the screen face under it shows through.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

S = 4  # drawn at four times the size, then shrunk, for smooth lines
FONTS = '/usr/share/fonts/truetype/dejavu/'
GREEN = (70, 255, 100)
NVG = (120, 255, 110)
CYAN = (70, 215, 255)
YELLOW = (255, 225, 60)
AMBER = (255, 165, 40)
RED = (255, 60, 45)
WHITE = (230, 238, 232)
MAGENTA = (235, 90, 235)


def font(px, bold=True):
    name = 'DejaVuSansMono-Bold.ttf' if bold else 'DejaVuSansMono.ttf'
    return ImageFont.truetype(os.path.join(FONTS, name), max(6, int(px * S)))


class Page:
    """A page being drawn: coordinates in page pixels (0 to w, 0 to h), lines in page pixels."""

    def __init__(self, w, h, ground=(0, 0, 0)):
        self.w, self.h = w, h
        self.im = Image.new('RGB', (w * S, h * S), ground)
        self.d = ImageDraw.Draw(self.im)

    def line(self, pts, col, width=1.0):
        self.d.line([(x * S, y * S) for x, y in pts], fill=col, width=max(1, int(width * S)), joint='curve')

    def rect(self, x0, y0, x1, y1, col, width=1.0, fill=None):
        self.d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], outline=col, width=max(1, int(width * S)), fill=fill)

    def circle(self, x, y, r, col, width=1.0, fill=None):
        self.d.ellipse([(x - r) * S, (y - r) * S, (x + r) * S, (y + r) * S], outline=col,
                       width=max(1, int(width * S)), fill=fill)

    def arc(self, x, y, r, a0, a1, col, width=1.0):
        self.d.arc([(x - r) * S, (y - r) * S, (x + r) * S, (y + r) * S], a0, a1, fill=col, width=max(1, int(width * S)))

    def poly(self, pts, col, fill=None, width=1.0):
        self.d.polygon([(x * S, y * S) for x, y in pts], outline=col, fill=fill, width=max(1, int(width * S)))

    def text(self, x, y, s, col, px, anchor='la', bold=True):
        self.d.text((x * S, y * S), s, fill=col, font=font(px, bold), anchor=anchor)

    def done(self):
        im = self.im.resize((self.w, self.h), Image.LANCZOS)
        return np.clip(np.asarray(im, float) / 255, 0, 1)


def keys(p, col, n=5, sides='lrb'):
    """The soft-key labels round a display's edge: short dim bars, one lit."""
    w, h = p.w, p.h
    dim = tuple(int(c * 0.45) for c in col)
    for i in range(n):
        f = (i + 0.5) / n
        for side in sides:
            c = col if (i * 7 + ord(side)) % 5 == 0 else dim
            if side == 'l':
                p.line([(2, h * (0.15 + 0.7 * f)), (2 + w * 0.06, h * (0.15 + 0.7 * f))], c, 1.2)
            elif side == 'r':
                p.line([(w - 2, h * (0.15 + 0.7 * f)), (w - 2 - w * 0.06, h * (0.15 + 0.7 * f))], c, 1.2)
            elif side == 'b':
                p.line([(w * (0.12 + 0.76 * f) - w * 0.04, h - 3), (w * (0.12 + 0.76 * f) + w * 0.04, h - 3)], c, 1.2)
            elif side == 't':
                p.line([(w * (0.12 + 0.76 * f) - w * 0.04, 3), (w * (0.12 + 0.76 * f) + w * 0.04, 3)], c, 1.2)


# ------------------------------------------------------------------ the stealth fighter (six colour LCDs)

def tsd(w, h, seed):
    """The big middle display: the tactical picture, plan view, ownship low in the middle."""
    p = Page(w, h, (3, 6, 14))
    rng = np.random.default_rng(seed)
    cx, cy = w / 2, h * 0.78
    ring = (28, 86, 104)
    for k in (0.24, 0.48, 0.72):
        p.arc(cx, cy, h * k, 200, 340, ring, 1)
    for a in range(200, 341, 10):
        r0, r1 = h * 0.72, h * (0.75 if a % 30 else 0.78)
        t = math.radians(a)
        p.line([(cx + r0 * math.cos(t), cy + r0 * math.sin(t)), (cx + r1 * math.cos(t), cy + r1 * math.sin(t))], ring, 1)
    # the route out, waypoints and the steering line
    pts = [(cx, cy), (cx + w * 0.08, cy - h * 0.3), (cx - w * 0.12, cy - h * 0.55), (cx - w * 0.05, cy - h * 0.72)]
    p.line(pts, CYAN, 1.2)
    for x, y in pts[1:]:
        p.circle(x, y, w * 0.022, CYAN, 1)
    # ownship
    s = w * 0.035
    p.poly([(cx, cy - s * 1.4), (cx + s, cy + s * 0.6), (cx, cy + s * 0.2), (cx - s, cy + s * 0.6)], WHITE, WHITE)
    # tracks: friendlies green, hostiles red with their way of travel, an unknown yellow
    for _ in range(3):
        x, y = cx + rng.uniform(-0.38, 0.38) * w, cy - rng.uniform(0.2, 0.68) * h
        p.arc(x, y, w * 0.03, 180, 360, GREEN, 1.4)
        p.line([(x, y), (x + rng.uniform(-1, 1) * w * 0.05, y - w * 0.05)], GREEN, 1)
    for _ in range(2):
        x, y = cx + rng.uniform(-0.35, 0.35) * w, cy - rng.uniform(0.55, 0.75) * h
        r = w * 0.03
        p.poly([(x, y - r), (x + r, y + r * 0.7), (x - r, y + r * 0.7)], RED, None, 1.4)
        p.line([(x, y), (x + rng.uniform(-1, 1) * w * 0.04, y + w * 0.06)], RED, 1)
    x, y = cx + rng.uniform(-0.3, 0.3) * w, cy - rng.uniform(0.3, 0.5) * h
    p.rect(x - w * 0.025, y - w * 0.025, x + w * 0.025, y + w * 0.025, YELLOW, 1.3)
    p.text(5, 4, '80', CYAN, h * 0.07)
    p.text(w - 5, 4, 'SA', WHITE, h * 0.07, 'ra')
    p.text(w / 2, h - 5, 'NAV  360', CYAN, h * 0.055, 'md')
    keys(p, CYAN, 5, 'lr')
    return p.done()


def adi(w, h, seed):
    """Attitude: the ball, sky over ground, level on the deck, with the heading tape over it."""
    p = Page(w, h, (0, 0, 0))
    cx, cy, r = w / 2, h * 0.55, min(w, h) * 0.36
    p.d.pieslice([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], 180, 360, fill=(30, 85, 170))
    p.d.pieslice([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], 0, 180, fill=(105, 62, 26))
    p.line([(cx - r, cy), (cx + r, cy)], WHITE, 1.4)
    for k, half in ((-2, 0.25), (-1, 0.4), (1, 0.4), (2, 0.25)):
        y = cy + k * r * 0.22
        p.line([(cx - r * half, y), (cx + r * half, y)], WHITE, 1)
    p.circle(cx, cy, r, (150, 160, 150), 1)
    s = r * 0.45
    p.line([(cx - s, cy), (cx - s * 0.35, cy), (cx - s * 0.18, cy + s * 0.22), (cx, cy), (cx + s * 0.18, cy + s * 0.22),
            (cx + s * 0.35, cy), (cx + s, cy)], YELLOW, 2)
    # heading tape
    p.rect(w * 0.18, 3, w * 0.82, h * 0.12, (120, 130, 120), 1)
    for i in range(-4, 5):
        x = w / 2 + i * w * 0.07
        p.line([(x, h * 0.12), (x, h * (0.09 if i % 2 else 0.07))], WHITE, 1)
    p.text(w / 2, h * 0.02, '36', WHITE, h * 0.06, 'ma')
    # speed and altitude boxes
    p.rect(3, cy - h * 0.06, w * 0.17, cy + h * 0.06, WHITE, 1, (0, 0, 0))
    p.text(w * 0.1, cy, '0', GREEN, h * 0.07, 'mm')
    p.rect(w * 0.83, cy - h * 0.06, w - 3, cy + h * 0.06, WHITE, 1, (0, 0, 0))
    p.text(w * 0.9, cy, '60', GREEN, h * 0.07, 'mm')
    keys(p, GREEN, 5, 'b')
    return p.done()


def engines(w, h, seed):
    """Engine and fuel: two engines side by side, tapes and readouts, idle with the APU running."""
    p = Page(w, h)
    p.text(w / 2, 4, 'ENG', WHITE, h * 0.07, 'ma')
    cols = [('RPM', 0.28, GREEN), ('FTIT', 0.42, GREEN), ('NOZ', 0.9, CYAN)]
    for e, x0 in ((0, 0.08), (1, 0.55)):
        for i, (name, fill, col) in enumerate(cols):
            x = w * (x0 + i * 0.13)
            top, bot = h * 0.16, h * 0.62
            p.rect(x, top, x + w * 0.08, bot, (110, 120, 110), 1)
            y = bot - (bot - top) * fill
            p.rect(x + 1, y, x + w * 0.08 - 1, bot - 1, col, 1, col)
            if e == 0:
                p.text(x + w * 0.04, bot + 2, name[0], WHITE, h * 0.05, 'ma')
    p.text(w * 0.06, h * 0.74, 'FUEL', WHITE, h * 0.06)
    p.text(w * 0.94, h * 0.74, '18200', GREEN, h * 0.07, 'ra')
    p.text(w * 0.06, h * 0.85, 'APU', WHITE, h * 0.06)
    p.text(w * 0.94, h * 0.85, 'ON', GREEN, h * 0.07, 'ra')
    keys(p, GREEN, 4, 'b')
    return p.done()


def stores(w, h, seed):
    """Stores: the jet from above with its bays, the missiles in them, and the gun."""
    p = Page(w, h)
    cx = w / 2
    s = min(w, h)
    body = [(cx, h * 0.1), (cx + s * 0.06, h * 0.32), (cx + s * 0.38, h * 0.6), (cx + s * 0.38, h * 0.66),
            (cx + s * 0.1, h * 0.7), (cx + s * 0.2, h * 0.86), (cx + s * 0.06, h * 0.86), (cx, h * 0.8),
            (cx - s * 0.06, h * 0.86), (cx - s * 0.2, h * 0.86), (cx - s * 0.1, h * 0.7), (cx - s * 0.38, h * 0.66),
            (cx - s * 0.38, h * 0.6), (cx - s * 0.06, h * 0.32)]
    p.poly(body, (150, 160, 150), None, 1)
    for i in range(6):
        x = cx + (i - 2.5) * s * 0.045
        p.rect(x - s * 0.015, h * 0.42, x + s * 0.015, h * 0.6, GREEN, 1, GREEN if i != 4 else None)
    for side in (-1, 1):
        x = cx + side * s * 0.16
        p.rect(x - s * 0.015, h * 0.46, x + s * 0.015, h * 0.58, GREEN, 1, GREEN)
    p.text(5, 4, 'SMS', WHITE, h * 0.07)
    p.text(w - 5, 4, 'SAFE', AMBER, h * 0.07, 'ra')
    p.text(w / 2, h * 0.9, 'GUN 480', GREEN, h * 0.06, 'ma')
    return p.done()


def textlines(w, h, rows, size=0.13, head=None):
    p = Page(w, h)
    y = 3
    if head:
        p.text(4, y, head[0], head[1], h * size)
        y += h * size * 1.25
    for s, col in rows:
        p.text(4, y, s, col, h * size)
        y += h * size * 1.18
    return p


def cni(w, h, seed):
    """The left upfront display: radios, navigation and the identification set."""
    return textlines(w, h, [('C1 251.250', GREEN), ('C2 121.500', GREEN), ('TCN 74X', CYAN), ('IFF 4021', GREEN),
                            ('DL  ON', GREEN)], 0.12, ('CNI', WHITE)).done()


def icaw(w, h, seed):
    """The right upfront display: cautions and advisories, sat on a carrier deck."""
    return textlines(w, h, [('PARK BRAKE', AMBER), ('HOOK DOWN', AMBER), ('APU ON', GREEN), ('GEN 1 2', GREEN),
                            ('OBOGS', GREEN)], 0.12, ('ICAW', WHITE)).done()


def hud_jet(w, h, seed):
    """The fighter's head-up display: flight path marker, ladder, heading tape and the two boxes."""
    p = Page(w, h)
    g = GREEN
    cx, cy = w / 2, h * 0.55
    for k in (-1, 1):
        y = cy - k * h * 0.22
        for side in (-1, 1):
            xs = [cx + side * w * 0.1, cx + side * w * 0.3]
            if k < 0:
                p.line([(xs[0], y), (xs[0] + side * w * 0.07, y)], g, 1)
                p.line([(xs[1] - side * w * 0.07, y), (xs[1], y)], g, 1)
            else:
                p.line([(xs[0], y), (xs[1], y)], g, 1)
            p.line([(xs[1], y), (xs[1], y + k * h * 0.03)], g, 1)
        p.text(cx + w * 0.34, y, '5' if k > 0 else '-5', g, h * 0.05, 'lm')
    p.line([(cx - w * 0.4, cy), (cx - w * 0.12, cy)], g, 1)
    p.line([(cx + w * 0.12, cy), (cx + w * 0.4, cy)], g, 1)
    r = w * 0.025
    p.circle(cx, cy, r, g, 1.2)
    p.line([(cx - r * 3, cy), (cx - r, cy)], g, 1.2)
    p.line([(cx + r, cy), (cx + r * 3, cy)], g, 1.2)
    p.line([(cx, cy - r), (cx, cy - r * 2.2)], g, 1.2)
    for i in range(-3, 4):
        x = cx + i * w * 0.08
        p.line([(x, h * 0.08), (x, h * (0.12 if i % 2 else 0.14))], g, 1)
    p.text(cx, h * 0.15, '360', g, h * 0.06, 'ma')
    p.rect(w * 0.04, cy - h * 0.05, w * 0.18, cy + h * 0.05, g, 1)
    p.text(w * 0.11, cy, '0', g, h * 0.06, 'mm')
    p.rect(w * 0.82, cy - h * 0.05, w * 0.96, cy + h * 0.05, g, 1)
    p.text(w * 0.89, cy, '62', g, h * 0.06, 'mm')
    p.text(w * 0.06, h * 0.86, 'NAV', g, h * 0.055)
    p.text(w * 0.94, h * 0.86, 'G 1.0', g, h * 0.055, 'ra')
    return p.done()


# ------------------------------------------------------------------ the twin-tail fighter (gauges and a HUD)

def hud_flanker(w, h, seed):
    """The heavy fighter's sight: aiming ring, ladder, heading scale, navigation mode."""
    p = Page(w, h)
    g = NVG
    cx, cy = w / 2, h * 0.52
    p.circle(cx, cy, w * 0.035, g, 1.2)
    p.circle(cx, cy, w * 0.006, g, 1, g)
    p.line([(cx - w * 0.42, cy), (cx - w * 0.14, cy)], g, 1.2)
    p.line([(cx + w * 0.14, cy), (cx + w * 0.42, cy)], g, 1.2)
    for k in (-1, 1):
        y = cy - k * h * 0.24
        for side in (-1, 1):
            a, b = cx + side * w * 0.12, cx + side * w * 0.28
            if k > 0:
                p.line([(a, y), (b, y)], g, 1)
            else:
                for t in range(3):
                    p.line([(a + side * t * w * 0.055, y), (a + side * (t * w * 0.055 + w * 0.03), y)], g, 1)
    for i in range(-3, 4):
        x = cx + i * w * 0.09
        p.line([(x, h * 0.9), (x, h * (0.86 if i % 2 else 0.84))], g, 1)
    p.text(cx, h * 0.82, '00', g, h * 0.06, 'md')
    p.text(w * 0.06, cy - h * 0.1, '0', g, h * 0.065)
    p.text(w * 0.94, cy - h * 0.1, '18', g, h * 0.065, 'ra')
    p.text(w * 0.06, h * 0.06, 'НАВ', g, h * 0.06)
    return p.done()


# ------------------------------------------------------------------ the attack helicopter (colour MPDs)

def flight(w, h, seed):
    """The flight page, green: horizon, heading tape, airspeed, radar altitude, torque and rotor."""
    p = Page(w, h)
    g = NVG
    cx, cy = w / 2, h * 0.52
    p.line([(cx - w * 0.36, cy), (cx - w * 0.08, cy)], g, 1.2)
    p.line([(cx + w * 0.08, cy), (cx + w * 0.36, cy)], g, 1.2)
    s = w * 0.06
    p.line([(cx - s, cy), (cx - s * 0.4, cy), (cx, cy + s * 0.4), (cx + s * 0.4, cy), (cx + s, cy)], g, 1.4)
    for i in range(-4, 5):
        x = cx + i * w * 0.075
        p.line([(x, h * 0.06), (x, h * (0.1 if i % 2 else 0.12))], g, 1)
    p.text(cx, h * 0.13, '33  N  03', g, h * 0.055, 'ma')
    p.text(w * 0.07, cy, '0', g, h * 0.075, 'lm')
    p.text(w * 0.93, cy, '62', g, h * 0.075, 'rm')
    p.text(w * 0.07, h * 0.72, 'TQ 0%', g, h * 0.06)
    p.text(w * 0.93, h * 0.72, 'NR 101', g, h * 0.06, 'ra')
    p.rect(cx - w * 0.08, h * 0.8, cx + w * 0.08, h * 0.86, g, 1)
    p.circle(cx, h * 0.83, h * 0.025, g, 1, g)
    keys(p, g, 6, 'tb')
    return p.done()


def tsd_heli(w, h, seed):
    """The tactical situation page in colour: sea, the coast, the route out and what's been seen."""
    rng = np.random.default_rng(seed)
    p = Page(w, h, (6, 18, 38))
    # the coast: a ragged edge across the top third, land above it
    xs = np.linspace(0, w, 12)
    ys = h * 0.32 + rng.normal(0, h * 0.04, len(xs))
    p.poly([(0, 0), (w, 0)] + [(x, y) for x, y in zip(xs[::-1], ys[::-1])], (20, 60, 24), (14, 46, 18))
    for k in range(2):
        q = [(x, y - h * (0.08 + 0.08 * k) + rng.normal(0, 2)) for x, y in zip(xs, ys)]
        p.line(q, (90, 70, 30), 1)
    for i in range(1, 4):
        p.line([(w * i / 4, 0), (w * i / 4, h)], (26, 40, 60), 1)
        p.line([(0, h * i / 4), (w, h * i / 4)], (26, 40, 60), 1)
    cx, cy = w / 2, h * 0.62
    route = [(cx, cy), (cx - w * 0.12, cy - h * 0.25), (cx + w * 0.05, cy - h * 0.45), (cx + w * 0.2, cy - h * 0.55)]
    p.line(route, CYAN, 1.2)
    for i, (x, y) in enumerate(route[1:]):
        p.circle(x, y, w * 0.02, CYAN, 1)
        p.text(x + w * 0.03, y, f'W{i + 1}', CYAN, h * 0.045, 'lm')
    for _ in range(2):
        x, y = rng.uniform(0.15, 0.85) * w, rng.uniform(0.08, 0.3) * h
        r = w * 0.025
        p.poly([(x, y - r), (x + r, y), (x, y + r), (x - r, y)], RED, None, 1.3)
    s = w * 0.03
    p.poly([(cx, cy - s * 1.3), (cx + s * 0.6, cy + s), (cx - s * 0.6, cy + s)], WHITE, WHITE)
    p.text(5, h - 4, 'TSD', WHITE, h * 0.06, 'ld')
    p.text(w - 5, h - 4, '5KM', WHITE, h * 0.05, 'rd')
    return p.done()


def weapons(w, h, seed):
    """The weapon page: the helicopter from above, missiles on the outboard rails, rockets, the gun, SAFE."""
    p = Page(w, h)
    g = NVG
    cx = w / 2
    p.poly([(cx, h * 0.12), (cx + w * 0.05, h * 0.3), (cx + w * 0.05, h * 0.75), (cx, h * 0.9),
            (cx - w * 0.05, h * 0.75), (cx - w * 0.05, h * 0.3)], g, None, 1)
    p.line([(cx - w * 0.38, h * 0.45), (cx + w * 0.38, h * 0.45)], g, 1)
    for side in (-1, 1):
        for k, x0 in enumerate((0.3, 0.16)):
            x = cx + side * w * x0
            if k == 0:
                for i in range(4):
                    xx = x + (i % 2 - 0.5) * w * 0.05
                    yy = h * 0.5 + (i // 2) * h * 0.1
                    p.rect(xx - w * 0.018, yy, xx + w * 0.018, yy + h * 0.08, g, 1, g if i != 3 else None)
            else:
                p.circle(x, h * 0.56, w * 0.045, g, 1)
                p.text(x, h * 0.56, '19', g, h * 0.05, 'mm')
    p.text(5, 4, 'WPN', WHITE, h * 0.07)
    p.rect(w - 5 - w * 0.22, 3, w - 5, 3 + h * 0.09, AMBER, 1)
    p.text(w - 5 - w * 0.11, 3 + h * 0.045, 'SAFE', AMBER, h * 0.06, 'mm')
    p.text(cx, h * 0.93, 'GUN 1200', g, h * 0.06, 'md')
    keys(p, g, 6, 'b')
    return p.done()


def tapes(w, h, seed):
    """The engine strip over the displays: vertical tapes, idle on the deck, numbers under them."""
    p = Page(w, h)
    n = 6
    fills = (0.62, 0.6, 0.55, 0.57, 0.82, 0.8)
    for i in range(n):
        x = w * (0.06 + i * 0.155)
        top, bot = h * 0.1, h * 0.72
        p.rect(x, top, x + w * 0.09, bot, (130, 140, 120), 1)
        y = bot - (bot - top) * fills[i]
        col = YELLOW if i >= 4 else GREEN
        p.rect(x + 1, y, x + w * 0.09 - 1, bot - 1, col, 1, col)
        p.text(x + w * 0.045, h * 0.76, str(int(fills[i] * 100)), WHITE, h * 0.14, 'ma')
    return p.done()


def eufd(w, h, seed):
    """The upfront text display: radios and the clock, amber on black."""
    p = Page(w, h)
    rows = [('UHF 305.000*', 'VHF 127.000'), ('FM1  30.000', 'FM2  41.250'), ('HF  4.5250', '14:32:05')]
    size = min(h * 0.17, (w - 8) / (25 * 0.62))
    y = 4
    for a, b in rows:
        p.text(4, y, a, AMBER, size)
        p.text(w - 4, y, b, AMBER, size, 'ra')
        y += size * 1.3
    return p.done()


def flir(w, h, seed):
    """The gunner's sight on its night channel: white-hot green, the sea, the deck edge, the reticle."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:h, 0:w] / np.array([h, w])[:, None, None]
    horizon = 0.42
    sky = 0.16 + 0.1 * (yy / horizon)
    sea = 0.22 + 0.1 * np.sin(xx * 40 + yy * 9) * (yy - horizon) + 0.06 * rng.random((h, w))
    img = np.where(yy < horizon, sky, sea)
    # a ship on the horizon, hot
    ship = (np.abs(xx - 0.68) < 0.12) & (yy > horizon - 0.06) & (yy < horizon) | \
           (np.abs(xx - 0.72) < 0.025) & (yy > horizon - 0.13) & (yy < horizon)
    img = np.where(ship, 0.75, img)
    base = np.stack([img * 0.45, img, img * 0.5], -1)
    p = Page(w, h)
    p.im = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8)).resize((w * S, h * S), Image.BILINEAR)
    p.d = ImageDraw.Draw(p.im)
    g = NVG
    cx, cy = w / 2, h / 2
    p.line([(cx - w * 0.25, cy), (cx - w * 0.05, cy)], g, 1.2)
    p.line([(cx + w * 0.05, cy), (cx + w * 0.25, cy)], g, 1.2)
    p.line([(cx, cy - h * 0.25), (cx, cy - h * 0.05)], g, 1.2)
    p.line([(cx, cy + h * 0.05), (cx, cy + h * 0.25)], g, 1.2)
    p.rect(w * 0.6, h * 0.3, w * 0.82, h * 0.46, g, 1)
    p.text(5, 4, 'FLIR W', g, h * 0.06)
    p.text(w - 5, 4, 'TADS', g, h * 0.06, 'ra')
    p.text(w / 2, h - 5, 'RNG 1250', g, h * 0.06, 'md')
    return p.done()


PAGES = {
    'tsd': tsd, 'adi': adi, 'engines': engines, 'stores': stores, 'cni': cni, 'icaw': icaw, 'hud_jet': hud_jet,
    'hud_flanker': hud_flanker, 'flight': flight, 'tsd_heli': tsd_heli, 'weapons': weapons, 'tapes': tapes,
    'eufd': eufd, 'flir': flir,
}
