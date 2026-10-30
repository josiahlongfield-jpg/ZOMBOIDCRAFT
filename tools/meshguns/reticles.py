"""Draws what each sight shows when you aim through it (Josiah 2026-10-30: every attachment works the way it's meant to,
and a sight that comes with its own reticle always uses that one). Writes textures/gui/sight/<model key>.png:

- a sight the gun stays drawn behind (red dots, the holo, the low-power scopes): just its reticle, which CombatHud
  draws in the middle of the screen at Attachments.Optic.sight() of the screen's height (three times that through
  the magnifier);
- a scope you look into: the whole eyepiece picture (dark rim, black all round, its reticle), stretched to the
  screen's height.

The holo and the 4x prism scope are drawn to the reticles that came with the attachment pack (the Eotech folder's
reticle.jpg ring and reticle3.jpg chevron), measured from those images on their 1000 px canvas with the aim point in
the middle: the pack's own images never ship. The rest are the reticles their real kind of sight has.

    python3 tools/meshguns/reticles.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/zomboidcraft/textures/gui/sight'
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
SS = 4      # drawn this many times bigger, then shrunk: smooth edges

RED = (235, 25, 20)
ORANGE = (245, 90, 15)
DEEP_RED = (200, 25, 22)
ETCHED = (18, 18, 18)


class Canvas:
    """Draws in reference units (the aim point at 0, 0, +y down) onto a square texture of `side` px that spans
    `span` units, layer by layer."""

    def __init__(self, side, span):
        self.side, self.span = side, span
        self.img = Image.new('RGBA', (side * SS, side * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def p(self, x, y):
        k = self.side * SS / self.span
        return (self.side * SS / 2 + x * k, self.side * SS / 2 + y * k)

    def k(self, v):
        return v * self.side * SS / self.span

    def rect(self, x0, y0, x1, y1, color, a=255):
        x0, x1, y0, y1 = min(x0, x1), max(x0, x1), min(y0, y1), max(y0, y1)
        self.d.rectangle([self.p(x0, y0), self.p(x1, y1)], fill=color + (a,))

    def disc(self, r, color, x=0, y=0, a=255):
        (cx, cy) = self.p(x, y)
        self.d.ellipse([cx - self.k(r), cy - self.k(r), cx + self.k(r), cy + self.k(r)], fill=color + (a,))

    def ring(self, r_in, r_out, color):
        (cx, cy) = self.p(0, 0)
        ro, ri = self.k(r_out), self.k(r_in)
        self.d.ellipse([cx - ro, cy - ro, cx + ro, cy + ro], fill=color + (255,))
        self.d.ellipse([cx - ri, cy - ri, cx + ri, cy + ri], fill=(0, 0, 0, 0))

    def poly(self, pts, color):
        self.d.polygon([self.p(x, y) for x, y in pts], fill=color + (255,))

    def text(self, s, x, y, size, color):
        f = ImageFont.truetype(FONT, int(self.k(size)))
        self.d.text(self.p(x, y), s, font=f, fill=color + (255,), anchor='lm')

    def done(self):
        return self.img.resize((self.side, self.side), Image.LANCZOS)


def cross(c, half, gap=0.0, reach=None, color=ETCHED):
    """Hairs `half` wide out from `gap` to `reach` (default: the canvas edge)."""
    reach = reach or c.span
    for s in (-1, 1):
        c.rect(s * gap, -half, s * reach, half, color)
        c.rect(-half, s * gap, half, s * reach, color)


# ---------------------------------------------------------------- sights the gun stays drawn behind

def holo():
    # the pack's ring: 154-176 units, ticks out to 215 about 14 wide, a 16-unit dot, all one red
    c = Canvas(256, 460)
    c.ring(154, 176, RED)
    for x0, y0, x1, y1 in ((-215, -7, -176, 7), (176, -7, 215, 7), (-7, -215, 7, -176), (-7, 176, 7, 215)):
        c.rect(x0, y0, x1, y1, RED)
    c.disc(16, RED)
    return c.done()


def dot():
    c = Canvas(64, 4)
    c.disc(1, RED)
    return c.done()


def combat_scope():
    # a fixed 3x combat scope: a lit dot on fine crosshairs that thicken to posts
    c = Canvas(256, 200)
    cross(c, 0.6, gap=4, reach=60)
    cross(c, 2.2, gap=60)
    c.disc(3, RED)
    return c.done()


def rifle_optic():
    # a 2x rifle scope: a lit dot inside a thin lit circle, fine crosshairs
    c = Canvas(256, 200)
    cross(c, 0.6, gap=16)
    c.ring(13, 14.5, RED)
    c.disc(2.5, RED)
    return c.done()


def pistol_scope():
    # a long-eye-relief pistol scope: plain fine crosshairs, a small lit centre
    c = Canvas(256, 200)
    cross(c, 0.7, gap=3, reach=55)
    cross(c, 2.0, gap=55)
    c.disc(1.8, RED)
    return c.done()


# ---------------------------------------------------------------- scopes you look into

def eyepiece(reticle, T=1024, span=1000):
    """The round eyepiece picture: a rim that darkens, black outside, the reticle (drawn on a 1000-unit canvas the
    size of the picture, or `span` units for one drawn bigger) over it."""
    n = T * 2
    yy, xx = np.mgrid[0:n, 0:n]
    c = (n - 1) / 2
    r = np.hypot((xx - c) / n, (yy - c) / n)
    R = 0.46
    alpha = np.clip((r / R - 0.84) / 0.16, 0, 1) ** 1.6 * 0.88
    alpha = np.where(r >= R, 1.0, alpha)
    alpha = alpha.reshape(T, 2, T, 2).mean(axis=(1, 3))
    base = np.zeros((T, T, 4), np.uint8)
    base[..., :3] = 5
    base[..., 3] = (alpha * 255).round().astype(np.uint8)
    img = Image.fromarray(base, 'RGBA')
    canvas = Canvas(T, span)
    reticle(canvas)
    img.alpha_composite(canvas.done())
    return img


def acog(c):
    # the pack's chevron (drawn a quarter bigger than its canvas, the stadia reaching the rim as in the real one):
    # tip on the aim point, arms 30 out by 40 down, 20 thick; the stem 5 wide down to 862 with
    # an orange 4 tick (35 each side) at 78, red ticks at 125 and 188 (the 6), a grey one at 266 and the foot at 362
    c.poly([(0, 0), (30, 40), (21, 42), (0, 20), (-21, 42), (-30, 40)], ORANGE)
    c.rect(-2.5, 42, 2.5, 110, ORANGE)
    c.rect(-2.5, 110, 2.5, 190, DEEP_RED)
    c.rect(-2.5, 190, 2.5, 362, (70, 70, 70))
    c.rect(-35, 75, 35, 81, ORANGE)
    c.text('4', 42, 82, 42, ORANGE)
    c.rect(-28, 122, 28, 128, DEEP_RED)
    c.rect(-25, 185, 25, 191, DEEP_RED)
    c.text('6', 42, 188, 42, DEEP_RED)
    c.rect(-13, 263, 13, 269, (70, 70, 70))
    c.rect(-11, 359, 11, 365, (70, 70, 70))


def duplex(c):
    cross(c, 1.6, reach=470)
    cross(c, 6.3, gap=120, reach=470)


def mil_dot(c):
    # fine hairs with a dot every mil (32 units here), heavy posts from 5 mils out
    cross(c, 1.4, reach=470)
    cross(c, 5.5, gap=165, reach=470)
    for i in range(1, 5):
        for s in (-1, 1):
            c.disc(4.5, ETCHED, x=s * i * 32)
            c.disc(4.5, ETCHED, y=s * i * 32)


def long_range(c):
    # a long-range tree: hashed hairs every half mil, hold-over rows below, a small lit centre
    cross(c, 1.2, reach=470)
    cross(c, 5.0, gap=200, reach=470)
    for i in range(1, 13):
        L = 7 if i % 2 else 12
        for s in (-1, 1):
            c.rect(s * i * 16 - 1.2, -L, s * i * 16 + 1.2, L, ETCHED)
            c.rect(-L, s * i * 16 - 1.2, L, s * i * 16 + 1.2, ETCHED)
    for row in range(2, 11, 2):
        y = row * 16
        for j in range(1, row // 2 + 2):
            for s in (-1, 1):
                c.disc(2.6, ETCHED, x=s * j * 16, y=y)
    c.disc(3.2, RED)


def crossbow(c):
    # a crossbow scope: crosshairs with three range lines below for 20, 30 and 40 yards
    cross(c, 1.5, reach=470)
    cross(c, 6.0, gap=150, reach=470)
    for i, y in enumerate((38, 80, 125)):
        L = 60 - i * 12
        c.rect(-L, y - 1.5, L, y + 1.5, ETCHED)


HUD = {'holo': holo, 'red_dot': dot, 'mini_dot': dot, 'compact_dot': dot,
       'combat_scope': combat_scope, 'rifle_optic': rifle_optic, 'pistol_scope': pistol_scope}
SCOPES = {'scope_4x': (acog, 800), 'hunting_scope': duplex, 'precision_scope': mil_dot, 'long_scope': long_range,
          'crossbow_scope': crossbow}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for key, make in HUD.items():
        make().save(OUT / f'{key}.png')
    for key, draw in SCOPES.items():
        draw, span = draw if isinstance(draw, tuple) else (draw, 1000)
        eyepiece(draw, span=span).save(OUT / f'{key}.png')
    print(f'{len(HUD) + len(SCOPES)} sights in {OUT}')


if __name__ == '__main__':
    main()
