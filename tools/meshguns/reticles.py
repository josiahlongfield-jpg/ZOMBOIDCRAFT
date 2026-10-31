"""Draws what each sight shows when you aim through it (Josiah 2026-10-30: every attachment works the way it's meant to,
and a sight that comes with its own reticle always uses that one). Writes textures/gui/sight/<model key>.png:

- a sight the gun stays drawn behind (red dots, the holo, the low-power scopes): just its reticle, which CombatHud
  draws in the middle of the screen at Attachments.Optic.sight() of the screen's height (three times that through
  the magnifier);
- a scope you look into: the whole eyepiece picture (dark rim, black all round, its reticle), stretched to the
  screen's height.

A sight whose pack came with a reticle uses that very image (Josiah 2026-10-30: "the scope sights that the attachment
packs offer, not the ones you made"): the holo the Eotech folder's reticle.jpg ring, the 4x prism its reticle3.jpg
chevron (the pack's spare, on its 1000 px canvas with the aim point in the middle), the mini red dot the RMR's lit dot
(Prism_RMR2_Sight_Emissive.png). The packs' images must not sit in the public repo as they are, so these go out like
the bought meshes: assets/zomboidcraft/sights/<key>.zsight, a PNG zlib-deflated and XORed with the cars' xorshift32
stream seeded from "sight/<key>" (SightTextures.java undoes it). The rest came with no reticle and are drawn, as their
real kind of sight has.

    python3 tools/meshguns/reticles.py [PACKS]

PACKS: the folder holding the attachment packs (attach/Gun Attachments/..., vol1/...); without it the pack sights
already written are kept.
"""
import importlib.util
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/zomboidcraft/textures/gui/sight'
PACKED = ROOT / 'src/main/resources/assets/zomboidcraft/sights'
_spec = importlib.util.spec_from_file_location('cars_pack', ROOT / 'tools' / 'cars' / 'pack.py')
_cars_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_cars_pack)
scramble = _cars_pack.scramble      # the bought meshes' stream
SS = 4      # drawn this many times bigger, then shrunk: smooth edges

RED = (235, 25, 20)
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

    def done(self):
        return self.img.resize((self.side, self.side), Image.LANCZOS)


def cross(c, half, gap=0.0, reach=None, color=ETCHED):
    """Hairs `half` wide out from `gap` to `reach` (default: the canvas edge)."""
    reach = reach or c.span
    for s in (-1, 1):
        c.rect(s * gap, -half, s * reach, half, color)
        c.rect(-half, s * gap, half, s * reach, color)


# ---------------------------------------------------------------- sights the gun stays drawn behind

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
    cross(c, 1.5, gap=6, reach=55)
    cross(c, 4.5, gap=55)
    c.disc(4, RED)
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


# ---------------------------------------------------------------- the packs' own

def lit(img):
    """A reticle photographed on black: its colour, with the black made see-through."""
    a = np.asarray(img.convert('RGB'), float) / 255
    alpha = np.clip((a.max(2) - 0.06) / 0.94, 0, 1)
    rgb = np.where(alpha[..., None] > 0, a / np.maximum(a.max(2, keepdims=True), 1e-6), 0)
    out = np.dstack([rgb, alpha])
    return Image.fromarray((out * 255).round().astype(np.uint8), 'RGBA')


def pack_holo(packs):
    # the ring on its 1000 px canvas, cut to the same 460-unit square the HUD size was set for
    img = Image.open(packs / 'attach/Gun Attachments/Eotech Holo/reticle.jpg')
    return lit(img.crop((270, 270, 730, 730))).resize((256, 256), Image.LANCZOS)


def pack_prism(packs):
    # the chevron's 1000 px canvas, a quarter bigger (800 of it across the picture), over the eyepiece
    img = Image.open(packs / 'attach/Gun Attachments/Eotech Holo/reticle3.jpg')
    reticle = lit(img.crop((100, 100, 900, 900))).resize((1024, 1024), Image.LANCZOS)
    pic = eyepiece(lambda c: None)
    pic.alpha_composite(reticle)
    return pic


def pack_mini_dot(packs):
    # the RMR's lit dot, centred, filling half the square like the drawn dots
    img = Image.open(packs / 'vol1/Prism RMR2/PBR/Prism_RMR2_Sight_Emissive.png')
    return lit(img.crop((1872, 2041, 2152, 2321))).resize((64, 64), Image.LANCZOS)


def write_packed(key, img):
    import io
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    PACKED.mkdir(parents=True, exist_ok=True)
    (PACKED / f'{key}.zsight').write_bytes(scramble(zlib.compress(buf.getvalue(), 9), f'sight/{key}'))
    (OUT / f'{key}.png').unlink(missing_ok=True)


FROM_PACKS = {'holo': pack_holo, 'scope_4x': pack_prism, 'mini_dot': pack_mini_dot}
HUD = {'red_dot': dot, 'compact_dot': dot,
       'combat_scope': combat_scope, 'rifle_optic': rifle_optic, 'pistol_scope': pistol_scope}
SCOPES = {'hunting_scope': duplex, 'precision_scope': mil_dot, 'long_scope': long_range,
          'crossbow_scope': crossbow}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for key, make in HUD.items():
        make().save(OUT / f'{key}.png')
    for key, draw in SCOPES.items():
        draw, span = draw if isinstance(draw, tuple) else (draw, 1000)
        eyepiece(draw, span=span).save(OUT / f'{key}.png')
    if len(sys.argv) > 1:
        packs = Path(sys.argv[1])
        for key, make in FROM_PACKS.items():
            write_packed(key, make(packs))
        print(f'{len(FROM_PACKS)} pack sights in {PACKED}')
    print(f'{len(HUD) + len(SCOPES)} drawn sights in {OUT}')


if __name__ == '__main__':
    main()
