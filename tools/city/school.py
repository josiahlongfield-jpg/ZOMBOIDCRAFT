"""The school kit (sc_): a county elementary or high school of the sixties and seventies as it was
left. There are no photos of one in the library yet, so it follows what such schools are known for:
long single-storey brick wings with ribbon windows over coloured enamel panels, the name in
aluminium letters, the letter board out front; inside, painted block halls with a coloured dado,
rows of lockers, the trophy case and the drinking fountain, vinyl tile; classrooms with the
chalkboard and the alphabet over it, chair-desks in rows, the teacher's steel desk, cubbies and coat
hooks, the reading rug, the fish tank, the TV on its cart; the office with its counter, the mail
slots and the PA; the library; and the gym that doubled as the cafeteria, where the county ran its
shelter at the end: cots on the court, the water and the blankets stacked by the wall, the notes
families left for each other.

Blocks (tile 0 is always the viewer's left; the colour key a, b or c is the school's scheme):
- walls: sc_cmu, sc_cmu_dado_K, sc_note_* (messages on the hall walls), sc_pad_K (gym pads),
  sc_mural_0..11 (six by two, row 0 the upper), sc_spandrel_K (the enamel panels outside);
- floors: sc_vct, sc_vct_b, sc_vct_K, sc_court, sc_court_line (axis), sc_court_key_K,
  sc_court_c_K_0..15 (the centre circle, four by four, read from the south, tile i at column i % 4,
  row i // 4 counted from the north-west);
- classroom: sc_chalk_V_0..2, sc_alpha_0..4, sc_desk_{a,b}(_tipped), sc_tdesk_ped / sc_tdesk_knee
  (the knee to the sitter's right), sc_desk_things, sc_cubby, sc_hooks_{a,b}, sc_board_{art,info}_0..1,
  sc_pa, sc_rug_0..3 (two by two like the centre), sc_shelf_low, sc_tank, sc_av_cart, sc_projector,
  sc_pack_{red,blue,green}, sc_lunchbox;
- halls: sc_locker(_top)(_open)_K, sc_fountain, sc_trophy_{lo,hi}_0..1, sc_pennant_K_0..2,
  sc_banner_K_0..2, sc_room_N, sc_plaque_*, sc_pull, sc_bell;
- office: sc_counter, sc_mailboxes, sc_pa_console, sc_mimeo, sc_eye_chart, sc_sign_visitors;
- gym: sc_hoop_{l,r}, sc_score_0..2(_lit), sc_bleacher, sc_bleacher_frame, sc_cafe_table (axis),
  sc_cafe_folded(_top), sc_cot_{head,foot}(_b), sc_shelter_0..1, sc_messages_0..1, sc_register,
  sc_cases_water, sc_cases_blankets, sc_ball_cage, sc_rope, sc_rope_end, sc_ball_{red,orange},
  sc_milk, sc_trays;
- outside: sc_letters_{e,h}_0..9, sc_monument_{e,h}_0..2, sc_lboard_V_0..2, sc_sign_school,
  sc_sign_bus, sc_stencil_0..2 (BUSES ONLY, read from the street), sc_bike_rack (axis).

    python3 tools/city/school.py        rewrites the sc_ entries in blocks.json

photo_textures.py draws the textures (school.register()); world/county/School.java builds with them.
The base model faces north: anything on a wall has its back at z = 16 against the wall. A face only
shows the part of its texture under the box (Minecraft's automatic UVs), so each picture is painted
where its box sits in the block.
"""
import json
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import civic  # noqa: E402
import sheriff  # noqa: E402
from furniture import area, disc, edges, text_mask  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None  # photo_textures, handed over by register()
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
COND = "/usr/share/fonts/truetype/crosextra/Carlito-Bold.ttf"
HAND = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SCRAWL = F.SCRAWL
OBLIQUE = "/usr/share/fonts/truetype/freefont/FreeSansOblique.ttf"
COLOURS = {"a": (44, 74, 132), "b": (122, 36, 44), "c": (40, 100, 66)}
GOLD = (226, 180, 56)  # every scheme's second colour
CREAM = (214, 206, 180)
CHALK = (228, 230, 222)
SLATE = (46, 64, 54)
LINE = (232, 228, 210)
NAMES = {"e": ("GRAYHAVEN ELEMENTARY", "GRAYHAVEN|ELEMENTARY SCHOOL"), "h": ("GRAYHAVEN HIGH SCHOOL", "GRAYHAVEN|HIGH SCHOOL")}
ROOMS = [f"{f}{n:02d}" for f in (1, 2) for n in range(1, 13)]
PLAQUES = {"office": "OFFICE", "principal": "PRINCIPAL", "nurse": "NURSE", "library": "LIBRARY", "boys": "BOYS",
           "girls": "GIRLS", "gym": "GYMNASIUM", "kitchen": "KITCHEN|STAFF ONLY", "custodian": "CUSTODIAN", "teachers": "TEACHERS"}


def grain(rng, vertical=True):
    """Wood grain: tileable noise drawn out along the boards (down the texture, or across)."""
    g = P.tnoise(rng, 32).mean(axis=0)
    g = (g - g.mean()) / (g.std() + 1e-6)
    out = np.tile(g[None, :], (N, 1)) * 0.7 + P.tnoise(rng, 4) * 0.3
    return out if vertical else out.T


def mixc(a, b, k):
    return tuple(float(a[i]) * (1 - k) + float(b[i]) * k for i in range(3))


# ---------------------------------------------------------------- pictures several blocks across

class Pic:
    """A picture drawn with PIL at four times size and shrunk, cols by rows blocks: tile i is the
    column i % cols, row i // cols (row 0 the top)."""
    S = 4

    def __init__(self, cols, rows, bg, seed=0):
        self.cols, self.rows = cols, rows
        self.W, self.H = cols * N, rows * N
        self.rng = np.random.default_rng(seed)
        bg = tuple(int(v) for v in bg)
        self.im = Image.new("RGBA", (self.W * 4, self.H * 4), bg + (255,) if len(bg) == 3 else bg)
        self.d = ImageDraw.Draw(self.im)

    def _c(self, col, a=255):
        col = tuple(int(v) for v in col)
        return col if len(col) == 4 else col + (a,)

    def font(self, path, size):
        return ImageFont.truetype(path, max(1, int(size * 4)))

    def _pen(self, a):
        """Where to draw: straight on the picture when opaque, else on a layer laid over after
        (PIL writes a see-through colour over what is under instead of blending it)."""
        if a >= 255:
            return self.d, None
        layer = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        return ImageDraw.Draw(layer), layer

    def _ink(self, layer):
        if layer is not None:
            self.im.alpha_composite(layer)

    def rect(self, x0, y0, x1, y1, fill=None, outline=None, width=1, a=255):
        d, layer = self._pen(a)
        d.rectangle([x0 * 4, y0 * 4, x1 * 4 - 1, y1 * 4 - 1], fill=self._c(fill, a) if fill is not None else None,
                    outline=self._c(outline, a) if outline is not None else None, width=int(width * 4))
        self._ink(layer)

    def ellipse(self, x0, y0, x1, y1, fill=None, outline=None, width=1, a=255):
        d, layer = self._pen(a)
        d.ellipse([x0 * 4, y0 * 4, x1 * 4, y1 * 4], fill=self._c(fill, a) if fill is not None else None,
                  outline=self._c(outline, a) if outline is not None else None, width=int(width * 4))
        self._ink(layer)

    def poly(self, pts, fill=None, outline=None, a=255):
        d, layer = self._pen(a)
        d.polygon([(x * 4, y * 4) for x, y in pts], fill=self._c(fill, a) if fill is not None else None,
                  outline=self._c(outline, a) if outline is not None else None)
        self._ink(layer)

    def line(self, pts, fill, width=1, a=255):
        d, layer = self._pen(a)
        d.line([(x * 4, y * 4) for x, y in pts], fill=self._c(fill, a), width=max(1, int(width * 4)), joint="curve")
        self._ink(layer)

    def text(self, x, y, s, path, size, fill, a=255, anchor="la", spacing=1.0):
        f = self.font(path, size)
        d, layer = self._pen(a)
        for k, line in enumerate(s.split("|")):
            d.text((x * 4, (y + k * size * 1.15 * spacing) * 4), line, font=f, fill=self._c(fill, a), anchor=anchor)
        self._ink(layer)

    def scrawl(self, x, y, s, path, size, fill, jitter=0.3, a=255, angle=0.0, spacing=1.15):
        """Hand lettering: each letter a little up or down and turned, the lines a little crooked."""
        f = self.font(path, size)
        layer = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        for k, line in enumerate(s.split("|")):
            cx = x * 4
            cy = (y + k * size * spacing) * 4
            for ch in line:
                w = d.textlength(ch, font=f)
                g = Image.new("RGBA", (int(w) + int(size * 8), int(size * 8)), (0, 0, 0, 0))
                ImageDraw.Draw(g).text((size * 2, size * 2), ch, font=f, fill=self._c(fill, a))
                g = g.rotate(float(self.rng.normal(0, jitter * 8)), resample=Image.BICUBIC)
                layer.alpha_composite(g, (int(cx - size * 2), int(cy - size * 2 + self.rng.normal(0, jitter * size))))
                cx += w + self.rng.normal(0, jitter * 2)
        if angle:
            layer = layer.rotate(angle, resample=Image.BICUBIC, center=(x * 4, y * 4))
        self.im.alpha_composite(layer)

    def paste(self, other, x, y):
        self.im.alpha_composite(other.im, (int(x * 4), int(y * 4)))

    @lru_cache(maxsize=None)
    def shrunk(self):
        a = np.asarray(self.im.resize((self.W, self.H), Image.BOX), np.float32)
        return a

    def tile(self, i, seed, smooth=0.3, grime=0.12, h=0.75):
        a = self.shrunk()
        cx, cy = (i % self.cols) * N, (i // self.cols) * N
        t = F.plain(seed, (128, 128, 128), 0, smooth)
        t.c[:] = a[cy:cy + N, cx:cx + N, :3]
        t.alpha[:] = a[cy:cy + N, cx:cx + N, 3]
        t.h[:] = h
        if grime:
            t.grime(grime)
        return t


def chalky(t, region, amount=0.35):
    """Chalk on slate: the strokes broken up where the stick skipped."""
    y0, y1, x0, x1 = region
    lum = t.c[y0:y1, x0:x1] @ np.array((0.3, 0.59, 0.11), np.float32)
    bright = np.clip((lum - 90) / 120, 0, 1)
    n = t.rng.random(bright.shape)
    t.c[y0:y1, x0:x1] *= (1 - bright * amount * (n < 0.35))[..., None]


# ---------------------------------------------------------------- walls and floors

def dado(key):
    """Painted block with the school's colour up to the chair rail and a white pin stripe over it,
    scuffed along the bottom by chairs and shoes."""
    t = sheriff.cmu(9700 + ord(key), CREAM)
    col = np.array(COLOURS[key], np.float32)
    rel = t.c / np.array(CREAM, np.float32)
    t.c[18:] = np.clip(rel[18:] * col * 1.05, 0, 255)
    t.smooth[18:] = 0.62
    area(t, 0, 14, N, 18, (230, 226, 212), smooth=0.6)
    area(t, 0, 14, N, 15, mul=1.06)
    area(t, 0, 17, N, 18, mul=0.8)
    rng = t.rng
    for _ in range(16):
        x, y = rng.integers(0, N), rng.integers(40, N - 2)
        area(t, x, y, x + rng.integers(3, 12), y + 1, (52, 48, 42))
    for _ in range(5):
        x = rng.integers(0, N - 8)
        area(t, x, 24, x + rng.integers(4, 10), 26, mul=1.3)  # paint chipped to the block
    t.grime(0.1)
    return t


def spray(p, x, y, s, size, col, seed, angle=0.0, drips=True):
    """Spray paint: a soft overspray round each stroke, runs under the heavy ones."""
    q = Pic(p.cols, p.rows, (0, 0, 0, 0), seed)
    q.scrawl(x, y, s, SCRAWL, size, col, 0.25, angle=angle)
    a = np.asarray(q.im)[..., 3].astype(np.float32)
    soft = np.asarray(Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.GaussianBlur(10)), np.float32)
    halo = Image.new("RGBA", q.im.size, tuple(int(v) for v in col) + (0,))
    halo.putalpha(Image.fromarray(np.clip(soft * 0.45, 0, 255).astype(np.uint8)))
    p.im.alpha_composite(halo)
    p.im.alpha_composite(q.im)
    if drips:
        rng = np.random.default_rng(seed)
        ys, xs = np.nonzero(a > 200)
        if len(xs):
            for _ in range(10):
                k = rng.integers(len(xs))
                x0, y0 = xs[k] / 4, ys[k] / 4
                p.line([(x0, y0), (x0, y0 + rng.uniform(3, 12))], col, 0.8, a=200)


def note(kind):
    """What people wrote on the hall walls at the end, on the cream block over the dado."""
    seed = 9710 + ["gym_l", "gym_r", "x", "bus", "kids", "hands"].index(kind)
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    orange, red, blue = (236, 106, 26), (176, 22, 20), (40, 52, 120)
    if kind.startswith("gym"):
        spray(p, 6, 8, "SHELTER", 11, orange, seed)
        left = kind.endswith("l")
        y = 34
        pts = [(12, y), (44, y), (44, y - 6), (56, y + 6), (44, y + 18), (44, y + 12), (12, y + 12)]
        if left:
            pts = [(N - px, py) for px, py in pts]
        p.poly(pts, fill=orange, a=230)
        spray(p, 22 if not left else 18, 50, "GYM", 9, orange, seed + 1, drips=False)
    elif kind == "x":
        p.line([(6, 6), (58, 58)], red, 5, a=230)
        p.line([(58, 6), (6, 58)], red, 5, a=230)
        spray(p, 10, 22, "DO NOT|OPEN", 10, red, seed)
    elif kind == "bus":
        p.scrawl(4, 10, "BUS 3 LEFT 2:40|BUS 5 NEVER|CAME. WAIT|INSIDE", HAND, 6.5, blue, 0.35)
    elif kind == "kids":
        p.scrawl(3, 8, "RM 104 KIDS|WENT WITH THE|NAVARROS TO|ELM ST  - T.H.", HAND, 6, (30, 30, 34), 0.35)
        p.line([(4, 52), (40, 50)], (30, 30, 34), 0.8)
    else:
        rng = np.random.default_rng(seed)
        for k in range(3):
            hx, hy = 10 + k * 18 + rng.uniform(-3, 3), 14 + rng.uniform(-6, 10)
            p.ellipse(hx - 5, hy, hx + 5, hy + 10, fill=(96, 14, 12), a=220)
            for f in range(5):
                fx = hx - 5 + f * 2.6
                p.ellipse(fx - 1.2, hy - 8 + abs(f - 2) * 1.5, fx + 1.4, hy + 1, fill=(96, 14, 12), a=220)
            for s in range(6):
                sx = hx - 4 + s * 1.6
                p.line([(sx, hy + 9), (sx + rng.uniform(-1, 1), hy + 9 + rng.uniform(10, 30))], (80, 12, 10), 1.0, a=180)
    t = sheriff.cmu(seed, CREAM)
    a = p.shrunk()
    m = a[..., 3:4] / 255
    t.c = t.c * (1 - m) + a[..., :3] * m * (t.c / np.array(CREAM, np.float32))
    t.smooth = np.where(m[..., 0] > 0.5, 0.45, t.smooth)
    return t


def vct(seed, worn=False, colour=None):
    """Vinyl tile, nine inch squares (six to the block), the chips in them, waxed and walked to the glue."""
    base = (206, 200, 180)
    t = F.plain(seed, base, 0.02, 0.5)
    rng = t.rng
    k = N // 4
    for j in range(4):
        for i in range(4):
            r = rng.random()
            col = colour if colour else base if r < 0.5 else (176, 174, 160) if r < 0.85 else (156, 146, 124)
            col = tuple(c * rng.uniform(0.96, 1.04) for c in col)
            area(t, i * k, j * k, i * k + k, j * k + k, col)
    t.speckle(0.12, (112, 106, 94), 0.55)
    t.speckle(0.06, (238, 234, 222), 0.5)
    for s in range(0, N, k):
        area(t, s, 0, s + 1, N, mul=0.86, dh=-0.06)
        area(t, 0, s, N, s + 1, mul=0.86, dh=-0.06)
    yy, xx = np.mgrid[0:N, 0:N]
    t.smooth[:] = 0.42 + 0.2 * np.clip(F.periodic(rng, 4), 0, 1)  # the wax, patchy
    if worn:
        F.stain(t, rng.uniform(10, 54), rng.uniform(10, 54), rng.uniform(8, 16), (120, 104, 76), 0.35)
        q = rng.integers(0, 4, 2) * k
        area(t, q[0], q[1], q[0] + k, q[1] + k, (36, 32, 28), h=0.55, smooth=0.25)  # a tile come up, the mastic under
        for _ in range(8):
            x, y = rng.integers(0, N - 6, 2)
            area(t, x, y, x + rng.integers(2, 7), y + 1, (40, 38, 34))  # heel marks
    t.grime(0.32 if worn else 0.22)
    return t


def maple(seed, key=None, paint=None):
    """The gym floor: maple strips running the length of the court, varnished, scuffed black by
    sneakers, grey where the water got under the finish. paint: a colour laid over it (the key)."""
    t = F.plain(seed, (198, 150, 96), 0.02, 0.72)
    t.f0[:] = 28
    rng = t.rng
    for x in range(0, N, 4):
        tone = rng.uniform(0.9, 1.08)
        area(t, x, 0, x + 4, N, mul=tone)
        area(t, x, 0, x + 1, N, mul=0.84, dh=-0.05)
        for y in rng.integers(0, N, 2):
            area(t, x, y, x + 4, y + 1, mul=0.8, dh=-0.04)
    g = grain(rng)
    t.c *= (1 + 0.05 * g)[..., None]
    if paint is not None:
        rel = t.c / np.array((198, 150, 96), np.float32)
        t.c = np.clip(rel * np.array(paint, np.float32), 0, 255)
        wear = np.clip(F.periodic(rng, 8) * 1.5 - 0.6, 0, 1)
        t.c = t.c * (1 - wear[..., None] * 0.5) + np.array((198, 150, 96), np.float32) * wear[..., None] * 0.5
    for _ in range(9):
        x, y = rng.uniform(0, N, 2)
        a = rng.uniform(0, np.pi)
        for i in range(int(rng.integers(5, 14))):
            xi, yi = int(x + np.cos(a + i * 0.08) * i) % N, int(y + np.sin(a + i * 0.08) * i) % N
            t.c[yi, xi] *= 0.45
    F.stain(t, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(6, 14), (150, 140, 120), 0.3)
    t.grime(0.12)
    return t


def court_line(seed):
    t = maple(seed)
    area(t, 30, 0, 34, N, LINE, smooth=0.6)
    t.grime(0.12)
    return t


@lru_cache(maxsize=None)
def centre_masks():
    """The centre circle, four blocks by four: the painted disc, the ring, the big G, EAGLES."""
    S = N * 4
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    r = np.hypot(xx - S / 2 + 0.5, yy - S / 2 + 0.5)
    fill = (r < 112).astype(np.float32)
    ring = (np.abs(r - 116) < 3.5).astype(np.float32)
    g = text_mask("G", SERIF, 120, S, 150, seed=1)
    big_g = np.zeros((S, S), np.float32)
    big_g[40:190] = g
    word = np.zeros((S, S), np.float32)
    word[178:206, 56:200] = text_mask("EAGLES", SANS, civic.fit("EAGLES", SANS, 26, 144), 144, 28, seed=2)
    return fill, ring, big_g * fill, word * fill


def centre(key, i):
    t = maple(9730 + i)
    fill, ring, g, word = centre_masks()
    ox, oy = (i % 4) * N, (i // 4) * N
    sl = (slice(oy, oy + N), slice(ox, ox + N))
    rel = t.c / np.array((198, 150, 96), np.float32)
    col = np.clip(rel * np.array(COLOURS[key], np.float32) * 1.1, 0, 255)
    t.c = t.c * (1 - fill[sl][..., None]) + col * fill[sl][..., None]
    t.mix(ring[sl], LINE)
    t.mix(g[sl], GOLD)
    t.mix(word[sl], LINE)
    wear = np.clip(F.periodic(t.rng, 8) * 1.4 - 0.7, 0, 1) * 0.5
    t.c = t.c * (1 - wear[..., None]) + np.array((198, 150, 96), np.float32) * wear[..., None] * rel.mean(-1, keepdims=True)
    t.grime(0.1)
    return t


def spandrel(key):
    """The enamel panel under a ribbon window: the school's colour, chalked by the sun, an aluminium frame."""
    t = F.plain(9740 + ord(key), mixc(COLOURS[key], (255, 255, 255), 0.12), 0.04, 0.6)
    t.f0[:] = 40
    fade = np.clip(F.periodic(t.rng, 4) * 0.8 + 0.3, 0, 1)
    t.mix(fade * 0.25, (196, 196, 190))
    t.frame(3, (168, 170, 168), f0=150)
    area(t, 0, 31, N, 33, (150, 152, 150), f0=150)
    t.streaks(0.9, 0.7)
    t.grime(0.22)
    return t


def pad(key):
    """Gym wall padding: vinyl over foam in two panels, split in places, the yellow foam showing."""
    t = F.plain(9745 + ord(key), COLOURS[key], 0.03, 0.42)
    for x in (0, 32):
        area(t, x, 0, x + 1, N, mul=0.45, dh=-0.3)
        area(t, x + 1, 0, x + 3, N, mul=1.15)
        area(t, x + 29, 0, x + 31, N, mul=0.75)
    area(t, 0, 0, N, 2, mul=1.2)
    area(t, 0, N - 2, N, N, mul=0.6)
    rng = t.rng
    for _ in range(2):
        x, y = rng.integers(6, 50), rng.integers(10, 50)
        area(t, x, y, x + rng.integers(3, 8), y + rng.integers(4, 12), (214, 192, 110), h=0.6)
    t.grime(0.25)
    return t


@lru_cache(maxsize=None)
def mural_pic():
    """The children's mural, six blocks by two: OUR TOWN over the hills, the church and the water
    tower and the school, a rainbow, the sun, a line of children holding hands, handprints round it."""
    p = Pic(6, 2, (150, 190, 222), 9750)
    W, H = p.W, p.H
    for y in range(0, 70, 2):
        p.rect(0, y, W, y + 2, fill=mixc((132, 178, 218), (206, 226, 236), y / 70))
    p.ellipse(14, 8, 44, 38, fill=(250, 206, 50))
    for a in range(0, 360, 30):
        ca, sa = np.cos(np.radians(a)), np.sin(np.radians(a))
        p.line([(29 + ca * 18, 23 + sa * 18), (29 + ca * 26, 23 + sa * 26)], (250, 196, 40), 2.5)
    for k, col in enumerate([(220, 50, 46), (240, 140, 40), (246, 216, 60), (70, 160, 70), (60, 110, 200), (120, 70, 160)]):
        r = 92 - k * 5
        p.ellipse(W / 2 - r, 88 - r * 0.7, W / 2 + r, 88 + r * 0.7, outline=col, width=5)
    for k, (col, base, amp) in enumerate([((98, 168, 76), 76, 8), ((70, 140, 60), 90, 6), ((52, 118, 50), 104, 4)]):
        pts = [(x, base + amp * np.sin(x / 31 + k * 2)) for x in range(0, W + 4, 4)] + [(W, H), (0, H)]
        p.poly(pts, fill=col)
    # the water tower, the church, houses, the school with its flag
    p.rect(300, 40, 302, 80, fill=(90, 90, 96))
    p.rect(318, 40, 320, 80, fill=(90, 90, 96))
    p.ellipse(292, 24, 328, 46, fill=(200, 204, 210), outline=(40, 40, 40), width=1)
    p.text(298, 30, "GRAYHAVEN", SANS, 3.2, (60, 60, 70))
    p.rect(214, 52, 236, 80, fill=(240, 240, 236), outline=(40, 40, 40))
    p.poly([(212, 52), (225, 40), (238, 52)], fill=(160, 40, 40), outline=(40, 40, 40))
    p.rect(221, 22, 229, 42, fill=(240, 240, 236), outline=(40, 40, 40))
    p.poly([(219, 22), (225, 8), (231, 22)], fill=(160, 40, 40), outline=(40, 40, 40))
    p.line([(225, 2), (225, 10)], (40, 40, 40), 1)
    p.line([(222, 5), (228, 5)], (40, 40, 40), 1)
    for k, (hx, col, roof) in enumerate([(60, (230, 200, 120), (140, 60, 40)), (88, (150, 190, 230), (60, 60, 70)),
                                         (250, (240, 160, 160), (90, 50, 40)), (350, (200, 230, 160), (120, 40, 40))]):
        p.rect(hx, 64, hx + 18, 82, fill=col, outline=(40, 40, 40))
        p.poly([(hx - 3, 64), (hx + 9, 52), (hx + 21, 64)], fill=roof, outline=(40, 40, 40))
        p.rect(hx + 7, 72, hx + 12, 82, fill=(120, 80, 50))
    p.rect(126, 56, 190, 82, fill=(180, 70, 56), outline=(40, 40, 40))
    for wx in range(130, 188, 8):
        p.rect(wx, 62, wx + 5, 68, fill=(170, 210, 240))
    p.rect(152, 70, 164, 82, fill=(80, 60, 50))
    p.line([(196, 34), (196, 82)], (60, 60, 60), 1.2)
    p.rect(197, 34, 210, 42, fill=(230, 60, 60))
    for tx in (112, 204, 272, 340, 372):
        p.rect(tx, 66, tx + 3, 84, fill=(110, 70, 40))
        p.ellipse(tx - 7, 52, tx + 10, 70, fill=(46, 130, 56), outline=(30, 80, 30))
    # the children holding hands, each painted by a different hand
    rng = np.random.default_rng(9751)
    for k in range(16):
        cx = 18 + k * 23
        col = [(220, 60, 60), (60, 120, 220), (250, 200, 50), (70, 170, 80), (230, 120, 200), (240, 140, 40)][k % 6]
        skin = [(240, 200, 170), (200, 150, 110), (140, 96, 64), (230, 186, 150)][k % 4]
        p.ellipse(cx - 4, 96, cx + 4, 104, fill=skin, outline=(40, 40, 40))
        p.poly([(cx - 5, 116), (cx, 104), (cx + 5, 116)], fill=col, outline=(40, 40, 40))
        p.line([(cx - 2, 116), (cx - 3, 124)], (40, 40, 40), 1)
        p.line([(cx + 2, 116), (cx + 3, 124)], (40, 40, 40), 1)
        p.line([(cx - 3, 108), (cx - 11.5, 110)], (40, 40, 40), 1)
        p.line([(cx + 3, 108), (cx + 11.5, 110)], (40, 40, 40), 1)
    p.text(W / 2, 46, "OUR TOWN", SANS, 15, (250, 250, 246), anchor="ma")
    p.text(W / 2 + 1, 47, "OUR TOWN", SANS, 15, (40, 40, 60), a=90, anchor="ma")
    p.text(W - 6, H - 10, "MRS. HALE'S 3RD GRADE  -  1984", HAND, 4.5, (40, 40, 40), anchor="ra")
    for _ in range(26):
        hx, hy = rng.uniform(4, W - 8), rng.choice([rng.uniform(2, 10), rng.uniform(H - 12, H - 4)])
        col = tuple(int(c) for c in rng.integers(40, 240, 3))
        p.ellipse(hx - 2.5, hy, hx + 2.5, hy + 5, fill=col)
        for f in range(5):
            p.ellipse(hx - 3 + f * 1.4, hy - 3 + abs(f - 2) * 0.8, hx - 2 + f * 1.4, hy + 1, fill=col)
    return p


def mural(i):
    """A tile of the mural painted on the block: the joints showing through, flaking where the damp
    got behind it, a tag sprayed across the middle."""
    p = mural_pic()
    t = sheriff.cmu(9752 + i, CREAM)
    a = p.shrunk()
    ox, oy = (i % 6) * N, (i // 6) * N
    pic = a[oy:oy + N, ox:ox + N, :3]
    rel = t.c / np.array(CREAM, np.float32)
    flake = np.clip(F.periodic(t.rng, 8) * 2 - 1.1, 0, 1)
    paint = (1 - flake)[..., None]
    t.c = t.c * (1 - paint) + pic * np.clip(rel, 0.75, 1.1) * paint
    t.smooth[:] = 0.4
    if i in (8, 9):
        q = Pic(1, 1, (0, 0, 0, 0), 9760 + i)
        spray(q, 2 if i == 8 else -40, 14, "DEAD END", 13, (30, 30, 30), 9760, drips=True)
        b = q.shrunk()
        m = b[..., 3:4] / 255
        t.c = t.c * (1 - m) + b[..., :3] * m
    t.grime(0.14)
    return t


# ---------------------------------------------------------------- the classroom

CHALKBOARDS = ("lesson", "pickup", "shelter")


@lru_cache(maxsize=None)
def chalk_pic(kind):
    """A chalkboard three blocks across, the board in the top 56 rows (its box stands off the tray):
    a Monday's lesson; the list of who got picked up, crossed off one by one; or the note the teacher
    left when they shut the school."""
    seed = 9770 + CHALKBOARDS.index(kind)
    p = Pic(3, 1, SLATE, seed)
    rng = p.rng
    W = p.W
    # erasing: the old lessons left as grey swipes
    for _ in range(30):
        x, y = rng.uniform(0, W), rng.uniform(4, 50)
        p.ellipse(x - rng.uniform(8, 20), y - 2, x + rng.uniform(8, 20), y + 3, fill=(120, 132, 124), a=int(rng.integers(14, 36)))
    for _ in range(6):
        y = rng.uniform(8, 48)
        p.scrawl(rng.uniform(4, W - 50), y, "".join(rng.choice(list("abcdefgh+=12345 "), 10)), HAND, 6, (150, 160, 150), 0.4, a=40)
    if kind == "lesson":
        p.scrawl(6, 4, "Monday, Oct. 14", OBLIQUE, 6.5, CHALK, 0.25)
        p.scrawl(6, 15, "Spelling:|  house   mouse|  found   cloud|  shout   proud", HAND, 5.5, CHALK, 0.3, spacing=1.25)
        p.scrawl(74, 6, "  12        7         45|+  9     x 8      - 18|----      ----      ----|  21", HAND, 6.5, CHALK, 0.25, spacing=1.15)
        p.scrawl(140, 6, "HOMEWORK|p. 42  #1-10", HAND, 6, (240, 220, 120), 0.25)
        p.line([(140, 22), (178, 22)], (240, 220, 120), 0.8, a=220)
        p.ellipse(158, 30, 176, 48, outline=CHALK, width=1.2)
        for a in range(0, 360, 45):
            ca, sa = np.cos(np.radians(a)), np.sin(np.radians(a))
            p.line([(167 + ca * 11, 39 + sa * 11), (167 + ca * 15, 39 + sa * 15)], CHALK, 1, a=230)
        p.scrawl(140, 36, "Good|job!", OBLIQUE, 5.5, (240, 170, 190), 0.3)
    elif kind == "pickup":
        p.scrawl(8, 3, "PICKED UP", HAND, 7, CHALK, 0.25)
        p.line([(8, 12), (60, 12)], CHALK, 0.8)
        names = ["Amy R.", "Josh B.", "Kevin L.", "Tara W.", "Luis M.", "Becky S.", "Danny O.", "Mei C.",
                 "Ricky P.", "Sara K.", "Tommy H.", "Lisa D.", "Carl V.", "Jenny F.", "Eli G.", "Rosa N."]
        for k, nm in enumerate(names):
            cx, cy = 8 + (k // 6) * 46, 15 + (k % 6) * 6.5
            p.scrawl(cx, cy, nm, HAND, 5, CHALK, 0.3)
            if k < 13:
                p.line([(cx - 1, cy + 3), (cx + 30, cy + 2.5 + rng.uniform(-1, 1))], CHALK, 0.7, a=230)
        p.scrawl(146, 3, "STILL HERE", HAND, 7, (250, 200, 120), 0.25)
        p.line([(146, 12), (188, 12)], (250, 200, 120), 0.8)
        for k, nm in enumerate(["Jenny F.", "Eli G.", "Rosa N."]):
            p.scrawl(148, 16 + k * 8, nm, HAND, 6, (250, 200, 120), 0.3)
        p.scrawl(146, 44, "bus 5?", OBLIQUE, 5.5, CHALK, 0.35)
    else:
        p.scrawl(10, 4, "SCHOOL CLOSED", HAND, 9, CHALK, 0.2)
        p.scrawl(12, 18, "by order of the county|until further notice", OBLIQUE, 5.5, CHALK, 0.3)
        p.scrawl(80, 14, "If you read this:|go to the GYM.|Bring water.|Do NOT go outside|after dark.", HAND, 5.5, CHALK, 0.3)
        p.scrawl(150, 30, "We love you all|  - Mrs. Hale", OBLIQUE, 5.5, (246, 190, 200), 0.3)
        p.poly([(166, 20), (170, 14), (175, 18), (180, 14), (184, 20), (175, 28)], outline=(246, 160, 180))
    # the aluminium frame round the board: the top and bottom rails, the ends on the end tiles
    p.rect(0, 0, W, 2, fill=(176, 178, 176))
    p.rect(0, 53, W, 56, fill=(160, 162, 160))
    p.rect(0, 0, 2, 56, fill=(176, 178, 176))
    p.rect(W - 2, 0, W, 56, fill=(150, 152, 150))
    p.rect(0, 56, W, N, fill=(160, 162, 160))
    return p


def chalkboard(kind, i):
    p = chalk_pic(kind)
    t = p.tile(i, 9773 + CHALKBOARDS.index(kind) * 3 + i, smooth=0.18, grime=0.08)
    chalky(t, (2, 53, 0, N))
    t.f0[:] = 8
    t.f0[:2], t.f0[53:] = 160, 160
    return t


@lru_cache(maxsize=None)
def alphabet_pic():
    """The alphabet cards over the board, Aa to Zz with the handwriting lines, in rows 4..44."""
    p = Pic(5, 1, (0, 0, 0, 0), 9780)
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    w = p.W / 26
    for k, ch in enumerate(letters):
        x = k * w
        p.rect(x + 0.6, 6, x + w - 0.6, 42, fill=(244, 240, 226))
        p.rect(x + 0.6, 6, x + w - 0.6, 9, fill=[(220, 70, 60), (60, 120, 210), (70, 160, 80), (240, 180, 40)][k % 4])
        for ly in (16, 26, 36):
            p.line([(x + 1.5, ly), (x + w - 1.5, ly)], (120, 150, 210), 0.4)
        p.text(x + w / 2, 15, ch + ch.lower(), SANS, 8, (30, 30, 30), anchor="ma")
    return p


def alphabet(i):
    t = alphabet_pic().tile(i, 9781 + i, smooth=0.25, grime=0.18)
    rng = t.rng
    for _ in range(2):
        x = rng.integers(0, N - 12)
        area(t, x, 6, x + 12, 42, alpha=0)  # a card come down
    return t


def desktop(seed):
    """A chair-desk's writing top: wood-grain laminate, the pencil groove, initials cut in, doodles."""
    t = F.plain(seed, (176, 138, 92), 0.05, 0.5)
    g = grain(t.rng, False)
    t.c *= (1 + 0.08 * g)[..., None]
    area(t, 8, 2, 56, 4, mul=0.7, dh=-0.2)  # the pencil groove at the far edge
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    p.scrawl(14, 10, "J+T", HAND, 7, (80, 56, 36), 0.4)
    p.scrawl(32, 18, "math|sucks", HAND, 4.5, (60, 60, 120), 0.4)
    p.ellipse(16, 20, 24, 27, outline=(80, 56, 36), width=0.7)
    a = p.shrunk()
    m = a[..., 3:4] / 255
    t.c = t.c * (1 - m) + a[..., :3] * m
    t.grime(0.2)
    return t


def plastic(seed, rgb):
    t = F.plain(seed, rgb, 0.03, 0.62)
    t.f0[:] = 30
    t.grime(0.15)
    return t


def steel_paint(seed, rgb):
    t = F.plain(seed, rgb, 0.04, 0.45)
    t.f0[:] = 40
    t.speckle(0.02, (90, 70, 50), 0.6)
    t.grime(0.2)
    return t


def drawers(seed):
    """The teacher's desk pedestal: three drawers, the bottom one deep, pulls, a key lock, dented."""
    t = steel_paint(seed, (132, 142, 126))
    for y0, y1 in ((16, 28), (29, 41), (42, 64)):
        F.gap(t, 6, y0, 32, y1)
        area(t, 14, y0 + 3, 24, y0 + 5, (190, 192, 190), h=0.95, f0=200)
    disc(t, 19, 18.5, 1.2, (200, 180, 100))
    t.grime(0.15)
    return t


def gradebook(seed):
    """The open grade book on the desk: green ledger, ticks and grades in two inks."""
    t = F.plain(seed, (232, 236, 218), 0.02, 0.3)
    for y in range(12, 36, 2):
        area(t, 8, y, 32, y + 1, (150, 190, 160))
    area(t, 19, 12, 20, 36, (140, 60, 60))
    rng = t.rng
    for y in range(13, 35, 2):
        for x in range(10, 30, 3):
            if rng.random() < 0.7:
                area(t, x, y, x + 1, y + 1, (30, 40, 120) if rng.random() < 0.8 else (180, 30, 30))
    area(t, 8, 12, 32, 13, mul=0.8)
    return t


@lru_cache(maxsize=None)
def board_pic(kind):
    """A corkboard two blocks across (rows 4..60): the children's drawings of their families, or
    the fire drill route, the lunch menu and the class rules."""
    p = Pic(2, 1, (170, 128, 84), 9790 + (kind == "info"))
    rng = p.rng
    for _ in range(400):
        x, y = rng.uniform(0, p.W), rng.uniform(0, N)
        p.rect(x, y, x + 0.5, y + 0.5, fill=(120, 86, 56), a=120)
    p.rect(0, 4, p.W, 6, fill=(120, 90, 60))
    p.rect(0, 58, p.W, 60, fill=(110, 80, 52))
    p.rect(0, 4, 2, 60, fill=(120, 90, 60))
    p.rect(p.W - 2, 4, p.W, 60, fill=(110, 80, 52))
    if kind == "art":
        p.rect(30, 7, 98, 15, fill=(250, 220, 60))
        p.text(64, 8, "OUR FAMILIES", SANS, 5.5, (200, 40, 40), anchor="ma")
        for k in range(6):
            x, y = 6 + (k % 3) * 40 + rng.uniform(-2, 2), 18 + (k // 3) * 20 + rng.uniform(-1, 1)
            p.rect(x, y, x + 34, y + 18, fill=(246, 244, 236))
            sky = rng.random() < 0.5
            if sky:
                p.rect(x, y, x + 34, y + 3, fill=(120, 170, 230))
            p.rect(x, y + 15, x + 34, y + 18, fill=(90, 170, 70))
            p.ellipse(x + 26, y + 1, x + 32, y + 7, fill=(250, 210, 40))
            n = int(rng.integers(2, 5))
            for f in range(n):
                fx = x + 4 + f * 6
                tall = 9 if f < 2 else 6
                p.ellipse(fx - 1.5, y + 15 - tall - 3, fx + 1.5, y + 15 - tall, outline=(30, 30, 30), width=0.5)
                p.line([(fx, y + 15 - tall), (fx, y + 15 - tall / 2)], tuple(int(c) for c in rng.integers(30, 220, 3)), 0.8)
                p.line([(fx, y + 15 - tall / 2), (fx - 1.5, y + 15)], (30, 30, 30), 0.5)
                p.line([(fx, y + 15 - tall / 2), (fx + 1.5, y + 15)], (30, 30, 30), 0.5)
            p.scrawl(x + 2, y + 1, ["Amy", "Josh", "Mei", "Eli", "Rosa", "Kevin"][k], HAND, 3, (40, 40, 40), 0.3)
            p.rect(x + 16, y - 1, x + 18, y + 1, fill=(220, 40, 40))
    else:
        p.rect(6, 9, 58, 54, fill=(244, 242, 232))
        p.text(32, 11, "FIRE DRILL", SANS, 5, (190, 30, 30), anchor="ma")
        p.rect(10, 20, 54, 50, outline=(40, 40, 40), width=0.5)
        for k in range(4):
            p.rect(10 + k * 11, 20, 21 + k * 11, 31, outline=(40, 40, 40), width=0.4)
        p.line([(16, 34), (48, 34), (48, 46)], (200, 30, 30), 1)
        p.poly([(45, 44), (51, 44), (48, 49)], fill=(200, 30, 30))
        p.text(32, 50, "WALK  DON'T RUN", SANS, 2.6, (40, 40, 40), anchor="ma")
        p.rect(66, 9, 122, 34, fill=(250, 238, 160))
        p.text(94, 11, "LUNCH MENU", SANS, 4, (40, 40, 40), anchor="ma")
        p.text(70, 18, "MON  pizza|TUE  tacos|WED  fish sticks|THU  sloppy joe|FRI  chicken", HAND, 2.8, (40, 40, 40), spacing=0.95)
        p.rect(66, 37, 122, 56, fill=(200, 230, 250))
        p.text(94, 38, "CLASS RULES", SANS, 3.8, (30, 50, 120), anchor="ma")
        p.text(70, 44, "1. raise your hand|2. be kind|3. stay with your class", HAND, 2.8, (40, 40, 40), spacing=0.95)
        for x, y in ((32, 8), (94, 8), (94, 36)):
            p.rect(x - 1, y - 1, x + 1, y + 1, fill=(40, 140, 220))
    return p


def board(kind, i):
    return board_pic(kind).tile(i, 9792 + i + (kind == "info") * 2, smooth=0.2, grime=0.2)


def pa_grille(seed):
    """The classroom PA speaker: a wooden box painted to match the wall, a round cloth grille."""
    t = F.plain(seed, (196, 188, 166), 0.03, 0.4)
    disc(t, 32, 26, 11, (96, 90, 80), dh=-0.1)
    for y in range(16, 37, 2):
        area(t, 21, y, 43, y + 1, mul=0.75)
    edges(t, 16, 12, 48, 44, w=1)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def rug_pic():
    """The reading rug, two blocks by two: a border of the alphabet in coloured squares, a road and
    houses in the middle to drive toy cars along."""
    p = Pic(2, 2, (60, 110, 190), 9800)
    cols = [(220, 60, 50), (250, 190, 40), (70, 170, 80), (240, 130, 40), (150, 80, 180)]
    s = p.W / 8
    k = 0
    for i in range(8):
        for j in range(8):
            if i in (0, 7) or j in (0, 7):
                p.rect(i * s + 1, j * s + 1, i * s + s - 1, j * s + s - 1, fill=cols[k % 5])
                p.text(i * s + s / 2, j * s + 2.5, "ABCDEFGHIJKLMNOPQRSTUVWXYZ"[k % 26], SANS, 8, (250, 250, 246), anchor="ma")
                k += 1
    p.rect(s * 1.5, s * 3.6, s * 6.5, s * 4.4, fill=(70, 70, 76))
    for x in np.arange(s * 1.7, s * 6.4, 4):
        p.rect(x, s * 4 - 0.4, x + 2, s * 4 + 0.4, fill=(240, 220, 80))
    for hx, hy in ((s * 2, s * 1.6), (s * 4.5, s * 1.8), (s * 2.6, s * 5), (s * 5, s * 5.2)):
        p.rect(hx, hy, hx + 10, hy + 10, fill=(240, 236, 220))
        p.poly([(hx - 1, hy), (hx + 5, hy - 5), (hx + 11, hy)], fill=(200, 50, 40))
    return p


def rug(i):
    t = rug_pic().tile(i, 9801 + i, smooth=0.05, grime=0.3)
    t.vary(0.06)
    F.stain(t, t.rng.uniform(10, 54), t.rng.uniform(10, 54), 10, (70, 60, 40), 0.3)
    return t


def cubby(seed):
    """The cubbies: nine pigeonholes in birch ply, a name taped over each, bags and lunchboxes left."""
    t = F.plain(seed, (206, 176, 128), 0.04, 0.4)
    rng = t.rng
    names = ["AMY", "JOSH", "MEI", "ELI", "ROSA", "KEVIN", "TARA", "LUIS", "SARA"]
    for j in range(3):
        for i in range(3):
            x0, y0 = 3 + i * 20, 3 + j * 20
            area(t, x0, y0 + 4, x0 + 18, y0 + 19, (60, 44, 30), dh=-0.3)
            area(t, x0, y0 + 4, x0 + 18, y0 + 6, mul=0.6)
            area(t, x0 + 2, y0, x0 + 16, y0 + 4, (240, 236, 220))
            m = text_mask(names[j * 3 + i], SANS, 3, 14, 4, seed=i + j)
            F.paste(t, (m > 0.4).astype(np.float32), x0 + 2, y0, (40, 40, 40))
            r = rng.random()
            if r < 0.45:
                col = [(200, 50, 50), (50, 90, 190), (60, 150, 70), (230, 150, 40)][int(rng.integers(4))]
                area(t, x0 + 3, y0 + 8, x0 + 15, y0 + 19, col, h=0.9)
                area(t, x0 + 5, y0 + 12, x0 + 13, y0 + 16, mul=0.8)
            elif r < 0.65:
                area(t, x0 + 4, y0 + 12, x0 + 14, y0 + 19, (200, 60, 50), h=0.9, f0=120)
                area(t, x0 + 6, y0 + 14, x0 + 12, y0 + 17, (250, 220, 60))
    t.grime(0.2)
    return t


def shelf_low(seed):
    """A low bookcase (rows 24..64): picture books spine out, plastic bins of crayons on the top shelf."""
    t = F.plain(seed, (190, 160, 112), 0.04, 0.4)
    rng = t.rng
    for y0, y1 in ((26, 43), (45, 62)):
        area(t, 3, y0, 61, y1, (50, 38, 26), dh=-0.3)
        x = 4
        while x < 60:
            w = int(rng.integers(2, 5))
            h = int(rng.integers(10, y1 - y0))
            col = tuple(int(c) for c in rng.integers(40, 230, 3))
            if rng.random() < 0.12:
                x += w
                continue
            area(t, x, y1 - h, min(60, x + w), y1, col, h=0.85)
            area(t, x, y1 - h, x + 1, y1, mul=1.2)
            x += w
    t.grime(0.25)
    return t


def tank(seed):
    """The class fish tank, long dead: green water gone murky, the gravel, a plastic plant."""
    t = F.plain(seed, (90, 130, 80), 0.08, 0.9)
    t.alpha[:] = 170
    t.f0[:] = 60
    area(t, 0, 24, N, 32, (150, 130, 100), alpha=250)
    area(t, 26, 8, 30, 24, (60, 140, 60), alpha=230)
    area(t, 20, 12, 26, 16, (60, 140, 60), alpha=230)
    area(t, 0, 4, N, 7, (110, 140, 90), alpha=200)
    t.grime(0.4, (50, 70, 40))
    return t


def pack(seed, rgb):
    t = F.plain(seed, rgb, 0.05, 0.25)
    for y in range(0, N, 3):
        area(t, 0, y, N, y + 1, mul=0.93)
    area(t, 20, 30, 44, 34, (40, 40, 40))
    t.grime(0.25)
    return t


def lunchbox(seed):
    """A tin lunchbox, its lid printed with a rocket over the moon."""
    t = F.plain(seed, (200, 40, 40), 0.03, 0.6)
    t.f0[:] = 160
    disc(t, 40, 30, 8, (230, 230, 220))
    area(t, 22, 22, 28, 40, (240, 240, 236))
    area(t, 23, 18, 27, 22, (60, 120, 220))
    area(t, 22, 40, 28, 44, (250, 160, 40))
    t.grime(0.3)
    return t


# ---------------------------------------------------------------- the halls

def locker(key, part, opened=False):
    """Hall lockers, three doors to the block in the school's colour: louvres at the top of the upper
    block and the foot of the lower, the lift handle at the joint, the number plate; dented, a
    sticker or two. opened: the middle door hangs open, a jacket and books inside."""
    col = COLOURS[key]
    seed = 9810 + ord(key) * 4 + (part == "hi") * 2 + opened
    t = steel_paint(seed, mixc(col, (255, 255, 255), 0.1))
    rng = t.rng
    edges_x = [0, 21, 42, N]
    for k in range(3):
        x0, x1 = edges_x[k] + 1, edges_x[k + 1] - 1
        area(t, x0 - 1, 0, x0, N, mul=0.45, dh=-0.3)
        if part == "hi":
            for y in range(4, 16, 3):
                area(t, x0 + 4, y, x1 - 4, y + 1, (30, 30, 34), dh=-0.2)
            area(t, x0 + 6, 20, x1 - 6, 25, (186, 176, 120), f0=200)
            m = text_mask(str(100 + int(rng.integers(0, 900))), SANS, 4, x1 - x0 - 12, 5, seed=seed + k)
            F.paste(t, (m > 0.4).astype(np.float32), x0 + 6, 20, (30, 30, 30))
        else:
            for y in range(46, 60, 3):
                area(t, x0 + 4, y, x1 - 4, y + 1, (30, 30, 34), dh=-0.2)
            area(t, x0 + 7, 2, x1 - 7, 8, (176, 178, 176), h=0.95, f0=200)
            area(t, x0 + 8, 3, x1 - 8, 7, (40, 40, 40))
        if rng.random() < 0.35:
            x, y = rng.integers(x0 + 2, x1 - 4), rng.integers(10, 50)
            disc(t, x, y, rng.uniform(2, 4), mul=0.8, dh=-0.2)
        if rng.random() < 0.2:
            x, y = rng.integers(x0 + 2, x1 - 8), rng.integers(28, 44)
            area(t, x, y, x + 6, y + 4, tuple(int(c) for c in rng.integers(60, 250, 3)))
    if opened:
        x0, x1 = 22, 41
        area(t, x0, 0, x1, N, (28, 28, 30), h=0.3, smooth=0.2)
        if part == "lo":
            area(t, x0 + 2, 30, x1 - 2, N, (90, 60, 40), h=0.6)
            area(t, x0 + 4, 50, x1 - 4, 60, (200, 190, 160), h=0.7)
        else:
            area(t, x0, 20, x1, 22, (90, 90, 94))
            area(t, x0 + 3, 10, x1 - 3, 19, (160, 40, 40), h=0.7)
            area(t, x0 + 2, 26, x1 - 2, N, (60, 80, 140), h=0.6)
    t.grime(0.18)
    return t


def fountain_tex(seed):
    t = F.plain(seed, (184, 186, 186), 0.03, 0.7)
    t.f0[:] = 200
    t.grime(0.3, (110, 100, 70))
    return t


@lru_cache(maxsize=None)
def trophy_pic(part):
    """The trophy case's back, two blocks across: blue felt, a team photo, the pennant, plaques;
    the cups themselves stand on the shelves as boxes."""
    p = Pic(2, 1, (40, 56, 110), 9830 + (part == "hi"))
    rng = p.rng
    if part == "lo":
        p.rect(8, 6, 46, 30, fill=(236, 232, 220))
        p.rect(10, 8, 44, 24, fill=(120, 110, 96))
        for r in range(2):
            for k in range(7):
                cx = 13 + k * 4.6
                p.ellipse(cx - 1.5, 10 + r * 7, cx + 1.5, 13 + r * 7, fill=(200, 160, 130))
                p.rect(cx - 2, 13 + r * 7, cx + 2, 17 + r * 7, fill=(200, 60, 50) if r == 0 else (230, 230, 230))
        p.text(27, 25, "EAGLES 1979", SANS, 3, (40, 40, 40), anchor="ma")
        p.rect(80, 8, 118, 22, fill=(120, 80, 50))
        p.rect(82, 10, 116, 20, fill=(200, 170, 90))
        p.text(99, 12, "STATE CHAMPIONS", SANS, 2.6, (40, 30, 20), anchor="ma")
    else:
        p.poly([(10, 14), (60, 22), (10, 30)], fill=(226, 180, 56))
        p.text(14, 19, "EAGLES", SANS, 5, (40, 56, 110))
        for k in range(3):
            p.rect(70 + k * 18, 16, 84 + k * 18, 34, fill=(110, 76, 46))
            p.rect(72 + k * 18, 18, 82 + k * 18, 28, fill=(206, 176, 96))
            p.rect(73 + k * 18, 30, 81 + k * 18, 32, fill=(206, 176, 96))
        p.rect(8, 40, 120, 42, fill=(70, 50, 34))
    for _ in range(300):
        x, y = rng.uniform(0, p.W), rng.uniform(0, N)
        p.rect(x, y, x + 0.5, y + 0.5, fill=(30, 40, 80), a=90)
    return p


def trophies(part, i):
    return trophy_pic(part).tile(i, 9832 + i + (part == "hi") * 2, smooth=0.1, grime=0.15)


def glass(seed):
    t = F.plain(seed, (200, 214, 214), 0.02, 0.92)
    t.alpha[:] = 52
    t.f0[:] = 60
    rng = t.rng
    for _ in range(5):
        x, y = rng.integers(4, 60, 2)
        disc(t, x, y, rng.uniform(3, 7), (150, 150, 140), alpha=90)
    t.frame(1, (90, 64, 40))
    t.alpha[0, :], t.alpha[-1, :], t.alpha[:, 0], t.alpha[:, -1] = 255, 255, 255, 255
    return t


def metal(seed, rgb, f0=220, smooth=0.75):
    t = F.plain(seed, rgb, 0.04, smooth)
    t.f0[:] = f0
    t.grime(0.15)
    return t


def pennant(key, i):
    """A felt pennant (rows 8..48): the point to the right, the lettering in gold, the stick at the left."""
    p = Pic(1, 1, (0, 0, 0, 0), 9840 + i)
    col = COLOURS[key]
    p.poly([(4, 10), (62, 28), (4, 46)], fill=col)
    p.rect(2, 8, 5, 48, fill=(200, 180, 140))
    text = ["EAGLES", "GO EAGLES!", "CHAMPS '79"][i]
    q = Pic(1, 1, (0, 0, 0, 0), i)
    q.text(30, 23.5, text, SANS, 5.5 if i == 0 else 4.2, GOLD, anchor="ma")
    p.im.alpha_composite(q.im)
    t = p.tile(0, 9843 + i + ord(key) * 3, smooth=0.05, grime=0.2)
    return t


@lru_cache(maxsize=None)
def banner_pic(key):
    """The felt banner in the hall, three blocks across (rows 4..60): HOME OF THE EAGLES."""
    p = Pic(3, 1, (0, 0, 0, 0), 9850 + ord(key))
    p.rect(2, 6, p.W - 2, 54, fill=COLOURS[key])
    for x in np.arange(4, p.W - 4, 6):
        p.poly([(x, 54), (x + 3, 59), (x + 6, 54)], fill=COLOURS[key])
    p.rect(2, 6, p.W - 2, 9, fill=GOLD)
    p.rect(2, 51, p.W - 2, 54, fill=GOLD)
    p.text(p.W / 2, 13, "HOME OF THE", SANS, 7, GOLD, anchor="ma")
    p.text(p.W / 2, 24, "EAGLES", SERIF, 20, (246, 242, 230), anchor="ma")
    for x in (10, p.W - 12):
        p.ellipse(x - 2, 6.5, x + 2, 10.5, fill=(190, 190, 186))
    return p


def banner(key, i):
    return banner_pic(key).tile(i, 9853 + i + ord(key), smooth=0.05, grime=0.2)


def room_plate(seed, text):
    """A room sign: brown plastic, the number engraved white, the Braille dots under, on a slot at
    rows 20..48, columns 12..52 (where its box sits)."""
    t = F.plain(seed, (70, 52, 40), 0.03, 0.5)
    area(t, 12, 20, 52, 48, (78, 58, 44))
    edges(t, 12, 20, 52, 48, w=1)
    size = civic.fit(text.replace("|", " "), SANS, 13 if len(text) <= 3 else 8, 36)
    m = text_mask(text, SANS, size, 36, 18 if "|" not in text else 22, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 14, 22, (236, 232, 222), h=0.9)
    for k in range(5):
        disc(t, 24 + k * 3, 44, 0.7, (200, 196, 186), h=0.95)
    t.grime(0.15)
    return t


def pull_station(seed):
    """The fire alarm pull station (columns 20..44, rows 16..52): red box, FIRE, the white handle."""
    t = F.plain(seed, (196, 30, 26), 0.03, 0.6)
    m = text_mask("FIRE", SANS, 6, 20, 7, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 22, 18, (246, 242, 230))
    area(t, 24, 28, 40, 34, (240, 240, 236), h=0.95)
    m = text_mask("PULL|DOWN", SANS, 3, 20, 8, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 22, 38, (246, 242, 230))
    edges(t, 20, 16, 44, 52, w=1)
    t.grime(0.2)
    return t


def bell(seed):
    t = F.plain(seed, (190, 34, 28), 0.03, 0.75)
    t.f0[:] = 80
    disc(t, 32, 32, 18, mul=1.1)
    disc(t, 32, 32, 4, (40, 40, 40))
    disc(t, 32, 32, 20, mul=0.75, ring=2)
    t.grime(0.25)
    return t


# ---------------------------------------------------------------- the office

def counter_front(seed):
    """The office counter's face (rows 8..64): grooved ash panelling, a kick plate, scuffs."""
    t = F.plain(seed, (170, 132, 86), 0.04, 0.4)
    g = grain(t.rng)
    t.c *= (1 + 0.07 * g)[..., None]
    for x in range(0, N, 8):
        area(t, x, 8, x + 1, N, mul=0.6, dh=-0.2)
    area(t, 0, 58, N, N, (40, 36, 32))
    for _ in range(6):
        x = t.rng.integers(0, 56)
        area(t, x, 52, x + 6, 57, mul=0.8)
    t.grime(0.2)
    return t


def mailboxes(seed):
    """Teachers' mail slots (rows 4..64): four by four pigeonholes, names typed on tape, notes in some."""
    t = F.plain(seed, (190, 160, 112), 0.04, 0.4)
    rng = t.rng
    for j in range(4):
        for i in range(4):
            x0, y0 = 2 + i * 15, 6 + j * 14.5
            area(t, x0, y0 + 3, x0 + 14, y0 + 13, (56, 42, 30), dh=-0.3)
            area(t, x0 + 2, y0, x0 + 12, y0 + 3, (236, 232, 220))
            area(t, x0 + 3, y0 + 1, x0 + 3 + rng.integers(4, 9), y0 + 2, (40, 40, 40))
            if rng.random() < 0.5:
                area(t, x0 + 1, y0 + 9, x0 + 13, y0 + 13, (230, 226, 210) if rng.random() < 0.7 else (200, 170, 220), h=0.9)
    t.grime(0.22)
    return t


def pa_panel(seed):
    """The PA console's top: a toggle and a lamp for each room, the all-call key, the VU meter (up
    face, columns 8..56, rows 16..52)."""
    t = F.plain(seed, (200, 190, 162), 0.03, 0.5)
    area(t, 8, 16, 56, 52, (180, 172, 150))
    for j in range(3):
        for i in range(6):
            x, y = 11 + i * 7, 20 + j * 8
            area(t, x, y, x + 2, y + 4, (40, 40, 40), h=0.95)
            disc(t, x + 4, y + 1, 0.8, (120, 30, 20))
    area(t, 11, 44, 30, 50, (230, 220, 180))
    area(t, 20, 45, 21, 49, (30, 30, 30))
    area(t, 36, 44, 52, 50, (180, 40, 30), h=0.95)
    t.grime(0.2)
    return t


def mimeo(seed):
    """The spirit duplicator's face (columns 12..52, rows 8..28): beige, the drum window, purple ink everywhere."""
    t = F.plain(seed, (204, 194, 168), 0.03, 0.45)
    area(t, 16, 10, 48, 18, (60, 50, 70))
    for _ in range(8):
        disc(t, t.rng.uniform(12, 52), t.rng.uniform(8, 28), t.rng.uniform(1, 3), (110, 60, 150))
    t.grime(0.25)
    return t


def copies(seed):
    t = F.plain(seed, (232, 228, 236), 0.02, 0.2)
    for y in range(0, N, 3):
        area(t, 0, y, N, y + 1, (150, 110, 190))
    return t


def eye_chart(seed):
    """The nurse's eye chart (columns 12..52): the big E and the rows getting smaller."""
    t = F.plain(seed, (240, 238, 230), 0.02, 0.3)
    rows = ["E", "F P", "T O Z", "L P E D", "P E C F D", "E D F C Z P"]
    y = 4
    for k, r in enumerate(rows):
        size = [14, 8, 6, 5, 4, 3][k]
        m = text_mask(r, SANS, size, 36, size + 2, seed=k)
        F.paste(t, (m > 0.4).astype(np.float32), 14, y, (20, 20, 20))
        y += size + 3
    area(t, 12, 0, 13, N, (120, 120, 120))
    area(t, 51, 0, 52, N, (120, 120, 120))
    t.grime(0.2)
    return t


def visitors(seed):
    """The visitors' sign (columns 4..60, rows 16..48)."""
    t = F.plain(seed, (236, 232, 218), 0.02, 0.4)
    area(t, 4, 16, 60, 48, (236, 232, 218))
    edges(t, 4, 16, 60, 48, w=1)
    area(t, 4, 16, 60, 24, (180, 40, 36))
    m = text_mask("ATTENTION", SANS, 5, 50, 7, seed=1)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 17, (246, 242, 230))
    m = text_mask("ALL VISITORS|MUST REPORT|TO THE OFFICE", SANS, 4, 50, 22, seed=2)
    F.paste(t, (m > 0.4).astype(np.float32), 7, 25, (30, 30, 30))
    t.grime(0.2)
    return t


# ---------------------------------------------------------------- the gym

@lru_cache(maxsize=None)
def backboard_pic():
    """The backboard, two blocks across: white, the black edge, the shooter's square in orange
    over the rim at the seam."""
    p = Pic(2, 1, (238, 238, 232), 9860)
    p.rect(0, 0, p.W, 3, fill=(30, 30, 30))
    p.rect(0, N - 3, p.W, N, fill=(30, 30, 30))
    p.rect(0, 0, 3, N, fill=(30, 30, 30))
    p.rect(p.W - 3, 0, p.W, N, fill=(30, 30, 30))
    p.rect(N - 14, 30, N + 14, 58, outline=(232, 100, 30), width=2.4)
    p.text(N, 10, "EAGLES", SANS, 7, (60, 60, 70), anchor="ma")
    return p


def backboard(i):
    t = backboard_pic().tile(i, 9861 + i, smooth=0.55, grime=0.18)
    t.streaks(0.4, 0.85)
    for _ in range(4):
        disc(t, t.rng.uniform(8, 56), t.rng.uniform(10, 50), 2.5, (150, 120, 90), soft=1.5)  # ball marks
    return t


def net(seed):
    t = F.plain(seed, (232, 230, 222), 0.03, 0.2)
    yy, xx = np.mgrid[0:N, 0:N]
    m = ((xx + yy) % 8 < 1) | ((xx - yy) % 8 < 1)
    t.alpha[:] = np.where(m, 255, 0)
    t.grime(0.4)
    return t


@lru_cache(maxsize=None)
def score_pic(lit):
    """The scoreboard, three blocks across: HOME, GUEST, the clock and the period in bulb digits."""
    p = Pic(3, 1, (24, 24, 26), 9870 + lit)
    p.rect(0, 0, p.W, 12, fill=COLOURS["a"])
    p.text(p.W / 2, 2, "GRAYHAVEN EAGLES", SANS, 7, GOLD, anchor="ma")
    for x, label in ((32, "HOME"), (p.W - 32, "GUEST")):
        p.text(x, 15, label, SANS, 6, (236, 232, 220), anchor="ma")
    on = (255, 70, 40) if lit else (70, 34, 28)

    def digit(x, y, d, s=1.0):
        segs = {0: "abcdef", 1: "bc", 2: "abged", 3: "abgcd", 4: "fgbc", 5: "afgcd", 6: "afgedc", 7: "abc", 8: "abcdefg", 9: "abcfgd"}[d]
        w, h = 8 * s, 14 * s
        seg = {"a": [(x, y), (x + w, y)], "b": [(x + w, y), (x + w, y + h / 2)], "c": [(x + w, y + h / 2), (x + w, y + h)],
               "d": [(x, y + h), (x + w, y + h)], "e": [(x, y + h / 2), (x, y + h)], "f": [(x, y), (x, y + h / 2)],
               "g": [(x, y + h / 2), (x + w, y + h / 2)]}
        for k, pts in seg.items():
            p.line(pts, on if k in segs else (44, 30, 28), 1.6 * s)

    for x, (a, b) in ((20, (4, 2)), (p.W - 44, (3, 8))):
        digit(x, 26, a, 1.3)
        digit(x + 14, 26, b, 1.3)
    p.text(p.W / 2, 15, "TIME", SANS, 5, (236, 232, 220), anchor="ma")
    digit(p.W / 2 - 22, 24, 0, 1.0)
    digit(p.W / 2 - 10, 24, 0, 1.0)
    p.ellipse(p.W / 2 - 1, 28, p.W / 2 + 1, 30, fill=on)
    p.ellipse(p.W / 2 - 1, 33, p.W / 2 + 1, 35, fill=on)
    digit(p.W / 2 + 4, 24, 0, 1.0)
    digit(p.W / 2 + 16, 24, 0, 1.0)
    p.text(p.W / 2, 44, "PERIOD", SANS, 4, (236, 232, 220), anchor="ma")
    digit(p.W / 2 - 4, 50, 4, 0.7)
    return p


def score(i, lit):
    t = score_pic(lit).tile(i, 9872 + i + lit * 3, smooth=0.3, grime=0.12)
    if lit:
        lum = t.c[..., 0] - t.c[..., 1]
        t.emit[:] = np.clip((lum - 100) / 80, 0, 1) * 0.9
    return t


def bleacher_wood(seed):
    t = F.plain(seed, (184, 146, 96), 0.05, 0.55)
    for y in range(0, N, 16):
        area(t, 0, y, N, y + 1, mul=0.6, dh=-0.2)
    g = grain(t.rng, False)
    t.c *= (1 + 0.06 * g)[..., None]
    t.grime(0.25)
    return t


def cafe_top(seed):
    """A cafeteria table's top: speckled tan laminate, a steel edge band, gum and pencil."""
    t = F.plain(seed, (204, 186, 150), 0.02, 0.55)
    t.speckle(0.2, (160, 140, 110), 0.5)
    t.frame(2, (170, 172, 170), f0=200)
    for _ in range(3):
        disc(t, t.rng.uniform(6, 58), t.rng.uniform(6, 58), 1.2, tuple(int(c) for c in t.rng.integers(80, 240, 3)), h=0.95)
    t.grime(0.2)
    return t


def canvas_tex(seed):
    t = F.plain(seed, (110, 112, 74), 0.05, 0.15)
    for y in range(0, N, 2):
        area(t, 0, y, N, y + 1, mul=0.95)
    t.grime(0.3)
    return t


def blanket(seed, rgb=(118, 116, 110)):
    """A grey relief blanket: wool, two stripes near the end."""
    t = F.plain(seed, rgb, 0.08, 0.05)
    area(t, 0, 8, N, 11, (60, 60, 64))
    area(t, 0, 14, N, 16, (60, 60, 64))
    t.speckle(0.2, (90, 90, 88), 0.4)
    t.grime(0.2)
    return t


def teddy(seed):
    t = F.plain(seed, (150, 106, 66), 0.1, 0.05)
    t.speckle(0.3, (120, 84, 50), 0.5)
    t.grime(0.3)
    return t


@lru_cache(maxsize=None)
def shelter_pic():
    """The banner across the gym wall, two blocks across: COUNTY EMERGENCY SHELTER, hand-painted on a sheet."""
    p = Pic(2, 1, (226, 222, 206), 9880)
    p.text(N, 6, "COUNTY EMERGENCY", SANS, 9, (30, 60, 140), anchor="ma")
    p.text(N, 20, "SHELTER", SANS, 18, (30, 60, 140), anchor="ma")
    p.scrawl(10, 44, "register at the table - no weapons past the door", HAND, 4.2, (40, 40, 40), 0.25)
    return p


def shelter(i):
    t = shelter_pic().tile(i, 9881 + i, smooth=0.05, grime=0.25)
    yy = np.arange(N)[:, None]
    t.c *= (1 + 0.05 * np.sin(np.arange(N)[None, :] / 5 + i * 3) * (yy / N))[..., None]  # the sheet's sag
    return t


@lru_cache(maxsize=None)
def messages_pic():
    """The message wall, two blocks across: notes taped up by the families, MISSING flyers with
    photos, a child's drawing; bare wall between (see-through)."""
    p = Pic(2, 1, (0, 0, 0, 0), 9885)
    rng = p.rng
    notes = ["MOM - WE WENT TO|THE CHURCH. LOVE, D", "DAVID CALL|GRANDMA", "LOOKING FOR|ROSA NAVARRO|AGE 8",
             "BUS 5 KIDS -|ASK AT TABLE", "HAS ANYONE SEEN|MY HUSBAND|GARY - BLUE COAT", "WE WAITED.|GONE NORTH|-THE KELLERS"]
    for k in range(10):
        x, y = rng.uniform(2, p.W - 30), rng.uniform(2, N - 26)
        w, h = rng.uniform(22, 30), rng.uniform(16, 24)
        if k in (2, 6):
            p.rect(x, y, x + 24, y + 30, fill=(244, 242, 234))
            p.text(x + 12, y + 1, "MISSING", SANS, 4, (180, 20, 20), anchor="ma")
            p.rect(x + 6, y + 7, x + 18, y + 20, fill=(130, 120, 110))
            p.ellipse(x + 9, y + 9, x + 15, y + 15, fill=(200, 170, 140))
            p.text(x + 12, y + 22, ["ROSA, 8", "GARY, 41"][k == 6], SANS, 2.8, (30, 30, 30), anchor="ma")
        else:
            col = [(246, 244, 236), (250, 238, 150), (236, 210, 230), (210, 230, 246)][k % 4]
            p.rect(x, y, x + w, y + h, fill=col)
            p.scrawl(x + 1.5, y + 2, notes[k % len(notes)], HAND, 3.2, (30, 30, 60), 0.3)
        p.rect(x + w / 2 - 4, y - 1, x + w / 2 + 4, y + 1.5, fill=(230, 226, 200), a=200)
    return p


def messages(i):
    return messages_pic().tile(i, 9886 + i, smooth=0.1, grime=0.15)


def register_card(seed):
    t = F.plain(seed, (240, 238, 226), 0.02, 0.3)
    m = text_mask("REGISTER|HERE", SANS, 6, 24, 14, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 8, 16, (30, 50, 140))
    return t


def clipboard(seed):
    t = F.plain(seed, (160, 120, 76), 0.05, 0.3)
    area(t, 38, 18, 54, 42, (244, 242, 236))
    for y in range(22, 42, 2):
        area(t, 40, y, 40 + int(t.rng.integers(6, 13)), y + 1, (40, 40, 90))
    area(t, 42, 16, 50, 19, (170, 172, 170), f0=200)
    return t


def water_case(seed):
    t = F.plain(seed, (190, 214, 230), 0.06, 0.8)
    t.alpha[:] = 255
    for x in range(0, N, 10):
        area(t, x, 0, x + 1, N, mul=0.85)
    area(t, 0, 20, N, 30, (40, 90, 170))
    m = text_mask("DRINKING WATER", SANS, 5, 60, 8, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 2, 21, (246, 246, 246))
    t.grime(0.15)
    return t


def box_stencil(seed, text):
    t = F.plain(seed, (170, 134, 90), 0.06, 0.2)
    m = text_mask(text, COND, 7, 52, 10, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 6, 26, (36, 32, 28), amount=0.85)
    area(t, 0, 4, N, 7, (200, 180, 140))
    t.grime(0.25)
    return t


def cage(seed):
    t = F.plain(seed, (150, 152, 150), 0.03, 0.5)
    t.f0[:] = 160
    yy, xx = np.mgrid[0:N, 0:N]
    m = (xx % 8 < 1) | (yy % 8 < 1)
    t.alpha[:] = np.where(m, 255, 0)
    t.frame(2, (120, 122, 120))
    t.alpha[:2], t.alpha[-2:], t.alpha[:, :2], t.alpha[:, -2:] = 255, 255, 255, 255
    return t


def ball(seed, rgb, seams):
    t = F.plain(seed, rgb, 0.05, 0.3)
    t.speckle(0.4, mixc(rgb, (0, 0, 0), 0.2), 0.4, height=0.05)
    if seams:
        area(t, 0, 31, N, 33, (30, 26, 22))
        area(t, 31, 0, 33, N, (30, 26, 22))
    t.grime(0.3)
    return t


def rope(seed):
    t = F.plain(seed, (190, 170, 120), 0.06, 0.1)
    yy, xx = np.mgrid[0:N, 0:N]
    t.c *= (1 + 0.18 * np.sin((xx + yy) * 0.9))[..., None]
    t.grime(0.3)
    return t


def milk(seed):
    """The milk cooler (columns 4..60, rows 16..64): white enamel, MILK, the cow, rust at the foot."""
    t = F.plain(seed, (232, 232, 226), 0.03, 0.6)
    t.f0[:] = 50
    area(t, 4, 20, 60, 32, (40, 90, 170))
    m = text_mask("MILK", SANS, 9, 40, 11, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 12, 20, (246, 246, 246))
    area(t, 4, 58, 60, 64, (140, 90, 50))
    t.streaks(0.4, 0.8)
    t.grime(0.25)
    return t


def trays(seed):
    t = F.plain(seed, (190, 140, 90), 0.04, 0.5)
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=0.7)
    return t


# ---------------------------------------------------------------- outside

def letters(key, i):
    """Aluminium letters on stand-offs, ten blocks for the name, the shadow each throws."""
    text = NAMES[key][0]
    LWID = 10
    t = F.plain(9890 + i + (key == "h") * 10, (196, 198, 196), 0.03, 0.75)
    t.f0[:] = 220
    width = N * LWID - 16
    m = text_mask(text, SANS, civic.fit(text, SANS, 46, width), width, 52, seed=90)
    full = np.zeros((N, N * LWID), np.float32)
    h, w = m.shape
    full[6:6 + h, 8:8 + w] = m
    part = full[:, i * N:(i + 1) * N] > 0.45
    shade = np.zeros_like(part)
    shade[2:, 2:] = part[:-2, :-2]
    shade &= ~part
    t.alpha[:] = np.where(part | shade, 255, 0)
    t.c[shade] = (46, 42, 36)
    t.smooth[shade] = 0.1
    t.f0[shade] = 10
    t.h[:] = np.where(part, 0.9, 0)
    t.grime(0.15)
    t.streaks(0.6, 0.8)
    return t


@lru_cache(maxsize=None)
def monument_pic(key):
    """The sign cabinet on the brick base, three blocks across: the name, HOME OF THE EAGLES."""
    p = Pic(3, 1, (238, 236, 228), 9910 + (key == "h"))
    p.rect(0, 0, p.W, 4, fill=(150, 152, 150))
    p.rect(0, N - 4, p.W, N, fill=(150, 152, 150))
    p.rect(0, 0, 3, N, fill=(150, 152, 150))
    p.rect(p.W - 3, 0, p.W, N, fill=(150, 152, 150))
    top, bottom = NAMES[key][1].split("|")
    p.text(p.W / 2, 8, top, SERIF, 15, COLOURS["a"], anchor="ma")
    p.text(p.W / 2, 27, bottom, SANS, 9, COLOURS["a"], anchor="ma")
    p.rect(20, 41, p.W - 20, 42, fill=GOLD)
    p.text(p.W / 2, 45, "HOME OF THE EAGLES", SANS, 6, (90, 90, 96), anchor="ma")
    return p


def monument(key, i):
    t = monument_pic(key).tile(i, 9912 + i + (key == "h") * 3, smooth=0.45, grime=0.3)
    t.streaks(0.9, 0.7)
    return t


LBOARDS = ("closed", "shelter", "picture")


@lru_cache(maxsize=None)
def lboard_pic(kind):
    """The changeable letter board under the name: black ribbed panel, white plastic letters, some
    fallen and some hung crooked."""
    p = Pic(3, 1, (26, 26, 28), 9920 + LBOARDS.index(kind))
    for y in range(4, N - 4, 4):
        p.line([(3, y), (p.W - 3, y)], (40, 40, 44), 0.5)
    p.rect(0, 0, p.W, 3, fill=(150, 152, 150))
    p.rect(0, N - 3, p.W, N, fill=(150, 152, 150))
    p.rect(0, 0, 3, N, fill=(150, 152, 150))
    p.rect(p.W - 3, 0, p.W, N, fill=(150, 152, 150))
    lines = {"closed": ["SCHOOL CLOSED", "UNTIL FURTHER", "NOTICE", "STAY SAFE"],
             "shelter": ["SHELTER IN GYM", "WATER  BLANKETS", "REGISTER INSIDE", "GOD BLESS"],
             "picture": ["PICTURE DAY OCT 18", "BOOK FAIR NEXT WK", "", "NO SCHOOL"]}[kind]
    rng = p.rng
    f = p.font(SANS, 8)
    for k, line in enumerate(lines):
        y = 6 + k * 14
        tw = p.d.textlength(line, font=f) / 4
        x = p.W / 2 - tw / 2
        gone = {i for i, ch in enumerate(line) if ch != " " and rng.random() <= 0.05}
        if P.rude_gaps(line, gone):
            gone = set()
        for i, ch in enumerate(line):
            cw = p.d.textlength(ch, font=f) / 4
            if ch != " " and i not in gone:
                dy = rng.normal(0, 0.4) + (2.5 if rng.random() < 0.03 else 0)
                q = Image.new("RGBA", (int(cw * 4) + 40, 60), (0, 0, 0, 0))
                ImageDraw.Draw(q).text((20, 4), ch, font=f, fill=(240, 238, 230, 255))
                if rng.random() < 0.1:
                    q = q.rotate(rng.uniform(-25, 25), resample=Image.BICUBIC)
                p.im.alpha_composite(q, (int(x * 4) - 20, int((y + dy) * 4)))
            x += cw
    return p


def lboard(kind, i):
    return lboard_pic(kind).tile(i, 9924 + LBOARDS.index(kind) * 3 + i, smooth=0.4, grime=0.18)


def sign_school(seed):
    """The school crossing sign (columns 4..60, rows 0..48): yellow-green pentagon, two children walking."""
    p = Pic(1, 1, (0, 0, 0, 0), seed)
    col = (206, 220, 40)
    p.poly([(32, 1), (60, 18), (60, 47), (4, 47), (4, 18)], fill=(30, 30, 30))
    p.poly([(32, 3.5), (58, 19), (58, 45), (6, 45), (6, 19)], fill=col)
    for cx, s in ((24, 1.0), (40, 0.85)):
        p.ellipse(cx - 3 * s, 14, cx + 3 * s, 20 * s + (1 - s) * 20, fill=(30, 30, 30))
        p.poly([(cx - 4 * s, 22), (cx + 4 * s, 22), (cx + 5 * s, 34), (cx - 5 * s, 34)], fill=(30, 30, 30))
        p.line([(cx - 3 * s, 34), (cx - 6 * s, 42)], (30, 30, 30), 2.4 * s)
        p.line([(cx + 3 * s, 34), (cx + 5 * s, 42)], (30, 30, 30), 2.4 * s)
    p.line([(28, 26), (36, 27)], (30, 30, 30), 1.6)
    t = p.tile(0, seed, smooth=0.5, grime=0.2)
    t.f0[:] = 60
    t.streaks(0.6, 0.8)
    return t


def sign_bus(seed):
    t = F.plain(seed, (236, 236, 230), 0.02, 0.5)
    area(t, 8, 0, 56, 48, (236, 236, 230))
    edges(t, 8, 0, 56, 48, w=1)
    m = text_mask("SCHOOL BUS|LOADING|ZONE", SANS, 6, 44, 24, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 10, 2, (30, 30, 30))
    area(t, 10, 30, 54, 44, (190, 30, 30))
    m = text_mask("NO PARKING|7AM - 4PM", SANS, 4, 40, 12, seed=seed + 1)
    F.paste(t, (m > 0.4).astype(np.float32), 12, 31, (246, 242, 236))
    t.streaks(0.6, 0.8)
    t.grime(0.2)
    return t


@lru_cache(maxsize=None)
def stencil_mask():
    """BUSES ONLY in yellow road paint, three blocks across, upside down so it reads from the street."""
    W = N * 3
    m = text_mask("BUSES ONLY", COND, civic.fit("BUSES ONLY", COND, 30, W - 12), W - 12, 40, seed=3)
    full = np.zeros((N, W), np.float32)
    full[12:52, 6:W - 6] = m
    return full[::-1, ::-1]


def stencil(i):
    t = NS.PHOTO["asphalt"]()
    m = stencil_mask()[:, i * N:(i + 1) * N]
    wear = np.clip(F.periodic(t.rng, 8) * 1.2 + 0.5, 0, 1)
    t.mix(m * wear * 0.9, (214, 176, 40))
    t.smooth = np.maximum(t.smooth, m * 0.3)
    return t


def galv(seed):
    t = F.plain(seed, (168, 172, 168), 0.06, 0.5)
    t.f0[:] = 170
    t.speckle(0.05, (120, 90, 60), 0.6)
    t.streaks(0.5, 0.8)
    return t



# ---------------------------------------------------------------- the grounds: playground, court, field

PIPES = {"red": (166, 46, 38), "blue": (42, 86, 150), "yellow": (220, 176, 40), "green": (46, 112, 66)}


def pipe(seed, rgb):
    """Playground pipe: thick enamel gone chalky in the sun, chipped through to rust, streaked by rain."""
    t = F.plain(seed, rgb, 0.05, 0.5)
    t.f0[:] = 40
    fade = np.clip(F.periodic(t.rng, 16) * 0.8 + 0.3, 0, 1)
    t.c = t.c * (1 - 0.28 * fade[..., None]) + np.array((206, 200, 188), np.float32) * 0.28 * fade[..., None]
    t.speckle(0.05, (118, 70, 40), 0.8)
    t.streaks(0.45, 0.72)
    t.grime(0.22)
    return t


def chain(seed):
    """Swing chain, galvanised gone dull: the links as bright and dark bands, rust at the wear points."""
    t = F.plain(seed, (150, 152, 148), 0.05, 0.5)
    t.f0[:] = 160
    for y in range(0, N, 6):
        area(t, 0, y, N, y + 2, mul=0.55, dh=-0.2)
        area(t, 0, y + 3, N, y + 4, mul=1.2)
    t.speckle(0.06, (130, 76, 40), 0.7)
    return t


def belt(seed):
    """A swing's belt seat: black rubber, sun-cracked grey, the steel strap ends."""
    t = F.plain(seed, (34, 34, 34), 0.05, 0.25)
    for _ in range(18):
        x, y = t.rng.uniform(0, N, 2)
        area(t, int(x), int(y), int(x) + int(t.rng.integers(2, 8)), int(y) + 1, (90, 90, 88), h=0.4)
    area(t, 0, 0, 6, N, (120, 118, 110), f0=150)
    area(t, N - 6, 0, N, N, (120, 118, 110), f0=150)
    t.grime(0.2)
    return t


def chute(seed):
    """The slide's bed: stainless polished by forty years of trousers, scratched along the run, rust where the rain sits."""
    t = F.plain(seed, (188, 192, 194), 0.03, 0.85)
    t.f0[:] = 230
    for _ in range(60):
        x = int(t.rng.uniform(0, N))
        y0 = int(t.rng.uniform(0, N))
        area(t, x, y0, x + 1, min(N, y0 + int(t.rng.integers(6, 30))), mul=t.rng.uniform(0.8, 1.15))
    F.stain(t, t.rng.uniform(10, 54), 6, 8, (130, 90, 60), 0.35)
    t.streaks(0.3, 0.8)
    t.grime(0.1)
    return t


def tread(seed):
    """Diamond plate for the slide's deck and the merry-go-round, painted, worn back to steel."""
    t = F.plain(seed, (150, 150, 146), 0.04, 0.55)
    t.f0[:] = 120
    for y in range(0, N, 6):
        for x in range((y // 6) % 2 * 3, N, 6):
            area(t, x, y, x + 3, y + 1, mul=1.25, dh=0.3)
    t.speckle(0.04, (120, 70, 40), 0.7)
    t.grime(0.2)
    return t


def mulch(seed, wet=False):
    """Wood chips under the playground: brown bark shreds, grey where they've sat in the rain, the odd stone, a bottle cap."""
    t = F.plain(seed, (106, 74, 46) if not wet else (76, 56, 40), 0.1, 0.1)
    rng = t.rng
    for _ in range(260):
        x, y = rng.uniform(0, N, 2)
        w, h = int(rng.integers(2, 6)), int(rng.integers(1, 3))
        if rng.random() < 0.5:
            w, h = h, w
        tone = tuple(int(c) for c in np.array((130, 92, 56)) * rng.uniform(0.5, 1.3))
        if rng.random() < 0.15:
            tone = (128, 124, 116)
        area(t, int(x), int(y), int(x) + w, int(y) + h, tone, h=rng.uniform(0.5, 1.0))
    disc(t, rng.uniform(8, 56), rng.uniform(8, 56), 1.6, (190, 60, 40), h=0.9)
    t.grime(0.25)
    return t


def sand(seed):
    """The sandbox: pale sand pocked by rain, footprints, a dark wet patch, leaves blown in."""
    t = F.plain(seed, (196, 176, 136), 0.06, 0.1)
    t.speckle(0.3, (170, 150, 112), 0.4)
    rng = t.rng
    for _ in range(3):
        x, y = rng.uniform(8, 56, 2)
        for dx, dy in ((0, 0), (5, 8)):
            disc(t, x + dx, y + dy, 2.6, (160, 140, 104), h=0.3)
    for _ in range(6):
        disc(t, rng.uniform(0, N), rng.uniform(0, N), 1.4, (120, 96, 50), h=0.6)
    F.stain(t, rng.uniform(10, 54), rng.uniform(10, 54), 12, (140, 120, 90), 0.4)
    t.grime(0.15)
    return t


def timber(seed):
    """A landscape timber: treated pine gone silver, checks along the grain, green where it stays damp."""
    t = F.plain(seed, (124, 116, 100), 0.06, 0.2)
    g = grain(t.rng, False)
    t.c *= (1 + 0.1 * g)[..., None]
    for _ in range(6):
        y = int(t.rng.uniform(0, N))
        x = int(t.rng.uniform(0, 40))
        area(t, x, y, x + int(t.rng.integers(8, 24)), y + 1, mul=0.5, dh=-0.3)
    t.speckle(0.06, (80, 100, 60), 0.5)
    t.grime(0.3)
    return t


def chips_edge(seed):
    """The mulch with the timber that keeps it in, along the top (north) edge."""
    t = mulch(seed)
    e = timber(seed + 1)
    t.c[:14] = e.c[:14]
    area(t, 0, 14, N, 15, mul=0.4, dh=-0.4)
    return t


@lru_cache(maxsize=None)
def merry_pic():
    """The merry-go-round's deck from above, two blocks across: six painted sectors on diamond plate, worn to steel round the rim."""
    p = Pic(2, 2, (150, 150, 146), 9950)
    S = p.W
    c = S / 2
    cols = [PIPES["red"], PIPES["yellow"], PIPES["blue"], PIPES["green"], PIPES["red"], PIPES["yellow"]]
    for k in range(6):
        a0, a1 = k * 60, (k + 1) * 60
        p.d.pieslice([16, 16, (S - 4) * 4, (S - 4) * 4], a0, a1, fill=cols[k] + (255,))
    p.ellipse(c - 10, c - 10, c + 10, c + 10, fill=(150, 150, 146))
    p.ellipse(4, 4, S - 4, S - 4, outline=(200, 200, 196), width=3)
    return p


def merry(i):
    t = merry_pic().tile(i, 9951 + i, smooth=0.5, grime=0.2)
    t.c = t.c * 0.82 + tread(9955 + i).c * 0.18
    t.alpha[:] = 255
    t.speckle(0.05, (150, 150, 146), 0.6)
    return t


def backboard_out(seed):
    """An outdoor backboard, steel painted white: the shooter's square, bolt heads, rust bleeding from every hole."""
    t = F.plain(seed, (222, 220, 210), 0.04, 0.45)
    t.f0[:] = 40
    t.frame(3, (40, 40, 40))
    for x0, y0, x1, y1 in ((22, 30, 42, 32), (22, 30, 24, 46), (40, 30, 42, 46)):
        area(t, x0, y0, x1, y1, (200, 80, 30))
    for x in (6, 58):
        for y in (12, 52):
            disc(t, x, y, 1.2, (90, 60, 40), h=0.9)
            area(t, x, y, x + 1, min(N, y + 10), (130, 80, 50), h=0.5)
    t.streaks(0.6, 0.7)
    t.grime(0.3)
    return t


def chain_net(seed):
    t = F.plain(seed, (160, 160, 156), 0.05, 0.5)
    t.f0[:] = 160
    yy, xx = np.mgrid[0:N, 0:N]
    m = ((xx + yy) % 10 < 1) | ((xx - yy) % 10 < 1)
    t.alpha[:] = np.where(m, 255, 0)
    t.speckle(0.08, (130, 76, 40), 0.7)
    return t


def goal_pad(seed):
    """The padding round a goalpost's foot: navy vinyl, the seams, EAGLES stencilled up it, torn to the yellow foam."""
    t = F.plain(seed, (34, 44, 84), 0.05, 0.4)
    for x in (0, 31, 63):
        area(t, x, 0, x + 1, N, mul=0.6)
    m = text_mask("EAGLES", SANS, 9, 56, 12, seed=seed)
    F.paste(t, (m > 0.4).astype(np.float32), 4, 26, (226, 224, 214))
    F.stain(t, 46, 48, 5, (210, 190, 90), 0.8, tide=False)
    t.streaks(0.4, 0.8)
    t.grime(0.25)
    return t


def alu_plank(seed):
    """A bleacher plank: extruded aluminium, ridged lengthwise, gone dull and chalky, gum and a cup ring."""
    t = F.plain(seed, (176, 178, 176), 0.04, 0.55)
    t.f0[:] = 180
    for y in range(0, N, 4):
        area(t, 0, y, N, y + 1, mul=0.78, dh=-0.1)
    for _ in range(3):
        disc(t, t.rng.uniform(4, 60), t.rng.uniform(4, 60), 1.2, (60, 60, 62), h=0.9)
    t.streaks(0.35, 0.8)
    t.grime(0.2)
    return t


def horse_body(seed):
    """A spring rider pony: moulded plastic, white with brown patches once, now yellowed and scuffed through."""
    t = F.plain(seed, (226, 218, 196), 0.04, 0.45)
    rng = t.rng
    for _ in range(4):
        F.stain(t, rng.uniform(6, 58), rng.uniform(6, 58), rng.uniform(6, 12), (120, 70, 40), 0.85, tide=False)
    t.speckle(0.04, (90, 90, 90), 0.5)
    t.streaks(0.3, 0.8)
    t.grime(0.25)
    return t


def bucket(seed):
    t = F.plain(seed, (196, 50, 44), 0.05, 0.6)
    t.grime(0.3)
    return t



def turf(seed, dark=False, line=False, mud=False, rough=False):
    """The football field: mown grass in bands (light, or the dark band mown the other way), a chalk
    line down the middle gone patchy, or the mud worn bare between the hash marks, studs pressed in."""
    base = (88, 124, 64) if dark else (104, 140, 74)
    t = F.plain(seed, base, 0.1, 0.08)
    rng = t.rng
    for _ in range(420):
        x, y = rng.uniform(0, N, 2)
        area(t, int(x), int(y), int(x) + 1, int(y) + int(rng.integers(1, 4)), mul=rng.uniform(0.7, 1.3), h=rng.uniform(0.4, 0.9))
    if rough:
        # unmown since: the blades long and laid over, gone straw-coloured, clover and dandelions in it
        for _ in range(300):
            x, y = rng.uniform(0, N, 2)
            col = (150, 140, 80) if rng.random() < 0.4 else (80, 112, 56)
            area(t, int(x), int(y), int(x) + int(rng.integers(2, 6)), int(y) + 1, col, h=rng.uniform(0.6, 1.0))
        for _ in range(5):
            disc(t, rng.uniform(4, 60), rng.uniform(4, 60), 1.2, (226, 196, 40), h=0.9)
    if mud:
        m = np.clip(F.periodic(rng, 6) * 1.6 - 0.35, 0, 1)
        t.c = t.c * (1 - m[..., None]) + np.array((92, 72, 50), np.float32) * m[..., None]
        for _ in range(30):
            disc(t, rng.uniform(0, N), rng.uniform(0, N), 0.8, (64, 50, 36), h=0.3)
    if line:
        keep = (rng.random(N) > 0.2) & (np.convolve(rng.random(N) > 0.85, np.ones(6), "same") < 1)
        for y in range(N):
            if keep[y]:
                area(t, 29, y, 35, y + 1, (226, 226, 214), h=0.6)
    t.grime(0.15)
    return t


def turf_side(seed):
    t = F.plain(seed, (96, 74, 52), 0.1, 0.1)
    t.speckle(0.2, (70, 54, 40), 0.5)
    area(t, 0, 0, N, 5, (98, 134, 70))
    for x in range(0, N, 2):
        area(t, x, 5, x + 1, 5 + int(t.rng.integers(0, 4)), (90, 126, 64))
    t.grime(0.2)
    return t

def textures():
    T = {}
    T["sc_cmu"] = lambda: sheriff.cmu(9700, CREAM)
    for k in COLOURS:
        T[f"sc_cmu_dado_{k}"] = lambda k=k: dado(k)
        T[f"sc_vct_{k}"] = lambda k=k: vct(9720 + ord(k), colour=COLOURS[k])
        T[f"sc_court_key_{k}"] = lambda k=k: maple(9726 + ord(k), paint=COLOURS[k])
        T[f"sc_spandrel_{k}"] = lambda k=k: spandrel(k)
        T[f"sc_pad_{k}"] = lambda k=k: pad(k)
        T[f"sc_locker_side_{k}"] = lambda k=k: steel_paint(9808 + ord(k), mixc(COLOURS[k], (255, 255, 255), 0.1))
        for i in range(16):
            T[f"sc_court_c_{k}_{i}"] = lambda k=k, i=i: centre(k, i)
        for part in ("lo", "hi"):
            for o in (False, True):
                T[f"sc_locker_{part}_{k}{'_open' if o else ''}"] = lambda k=k, part=part, o=o: locker(k, part, o)
        for i in range(3):
            T[f"sc_pennant_{k}_{i}"] = lambda k=k, i=i: pennant(k, i)
            T[f"sc_banner_{k}_{i}"] = lambda k=k, i=i: banner(k, i)
    for kind in ("gym_l", "gym_r", "x", "bus", "kids", "hands"):
        T[f"sc_note_{kind}"] = lambda kind=kind: note(kind)
    T["sc_vct"] = lambda: vct(9718)
    T["sc_vct_b"] = lambda: vct(9719, worn=True)
    T["sc_court"] = lambda: maple(9725)
    T["sc_court_line"] = lambda: court_line(9729)
    for i in range(12):
        T[f"sc_mural_{i}"] = lambda i=i: mural(i)
    for kind in CHALKBOARDS:
        for i in range(3):
            T[f"sc_chalk_{kind}_{i}"] = lambda kind=kind, i=i: chalkboard(kind, i)
    for i in range(5):
        T[f"sc_alpha_{i}"] = lambda i=i: alphabet(i)
    T["sc_desktop"] = lambda: desktop(9785)
    T["sc_seat_orange"] = lambda: plastic(9786, (214, 110, 40))
    T["sc_seat_blue"] = lambda: plastic(9787, (52, 92, 150))
    T["sc_steel_green"] = lambda: steel_paint(9788, (132, 142, 126))
    T["sc_drawers"] = lambda: drawers(9789)
    T["sc_gradebook"] = lambda: gradebook(9796)
    T["sc_apple"] = lambda: plastic(9797, (176, 30, 30))
    T["sc_beige"] = lambda: plastic(9798, (204, 194, 168))
    for kind in ("art", "info"):
        for i in range(2):
            T[f"sc_board_{kind}_{i}"] = lambda kind=kind, i=i: board(kind, i)
    T["sc_pa"] = lambda: pa_grille(9799)
    for i in range(4):
        T[f"sc_rug_{i}"] = lambda i=i: rug(i)
    T["sc_cubby"] = lambda: cubby(9805)
    T["sc_shelf_low"] = lambda: shelf_low(9806)
    T["sc_tank"] = lambda: tank(9807)
    for name, rgb in (("red", (176, 40, 36)), ("blue", (44, 74, 150)), ("green", (52, 120, 64)), ("yellow", (230, 190, 40))):
        T[f"sc_fabric_{name}"] = lambda rgb=rgb, name=name: pack(9800 + len(name) * 7, rgb)
    T["sc_lunchbox"] = lambda: lunchbox(9809)
    T["sc_fountain"] = lambda: fountain_tex(9825)
    for part in ("lo", "hi"):
        for i in range(2):
            T[f"sc_trophies_{part}_{i}"] = lambda part=part, i=i: trophies(part, i)
    T["sc_glass"] = lambda: glass(9836)
    T["sc_gold"] = lambda: metal(9837, (214, 170, 60))
    T["sc_red"] = lambda: plastic(9838, (190, 32, 28))
    for n in ROOMS:
        T[f"sc_room_{n}"] = lambda n=n: room_plate(9860 + int(n), n)
    for k, text in PLAQUES.items():
        T[f"sc_plaque_{k}"] = lambda k=k, text=text: room_plate(9960 + list(PLAQUES).index(k), text)
    T["sc_pull"] = lambda: pull_station(9839)
    T["sc_bell"] = lambda: bell(9840)
    T["sc_counter_front"] = lambda: counter_front(9841)
    T["sc_mailboxes"] = lambda: mailboxes(9842)
    T["sc_pa_panel"] = lambda: pa_panel(9843)
    T["sc_mimeo"] = lambda: mimeo(9844)
    T["sc_copies"] = lambda: copies(9845)
    T["sc_eye_chart"] = lambda: eye_chart(9846)
    T["sc_sign_visitors"] = lambda: visitors(9847)
    for i in range(2):
        T[f"sc_backboard_{i}"] = lambda i=i: backboard(i)
        T[f"sc_shelter_{i}"] = lambda i=i: shelter(i)
        T[f"sc_messages_{i}"] = lambda i=i: messages(i)
    T["sc_rim"] = lambda: metal(9863, (226, 90, 30), f0=120, smooth=0.6)
    T["sc_net"] = lambda: net(9864)
    for i in range(3):
        T[f"sc_score_{i}"] = lambda i=i: score(i, False)
        T[f"sc_score_{i}_lit"] = lambda i=i: score(i, True)
    T["sc_bleacher_wood"] = lambda: bleacher_wood(9878)
    T["sc_cafe_top"] = lambda: cafe_top(9879)
    T["sc_canvas"] = lambda: canvas_tex(9882)
    T["sc_blanket"] = lambda: blanket(9883)
    T["sc_blanket_b"] = lambda: blanket(9884, (96, 102, 120))
    T["sc_teddy"] = lambda: teddy(9887)
    T["sc_register_card"] = lambda: register_card(9888)
    T["sc_clipboard"] = lambda: clipboard(9889)
    T["sc_water_case"] = lambda: water_case(9900)
    T["sc_box_blankets"] = lambda: box_stencil(9901, "BLANKETS")
    T["sc_box_cots"] = lambda: box_stencil(9902, "COTS - 12")
    T["sc_cage"] = lambda: cage(9903)
    T["sc_ball_red"] = lambda: ball(9904, (190, 40, 36), False)
    T["sc_ball_orange"] = lambda: ball(9905, (206, 100, 40), True)
    T["sc_rope"] = lambda: rope(9906)
    T["sc_milk"] = lambda: milk(9907)
    T["sc_trays"] = lambda: trays(9908)
    for key in NAMES:
        for i in range(10):
            T[f"sc_letters_{key}_{i}"] = lambda key=key, i=i: letters(key, i)
        for i in range(3):
            T[f"sc_monument_{key}_{i}"] = lambda key=key, i=i: monument(key, i)
    for kind in LBOARDS:
        for i in range(3):
            T[f"sc_lboard_{kind}_{i}"] = lambda kind=kind, i=i: lboard(kind, i)
    T["sc_sign_school"] = lambda: sign_school(9930)
    T["sc_sign_bus"] = lambda: sign_bus(9931)
    T["sc_galv"] = lambda: galv(9932)
    for name, rgb in PIPES.items():
        T[f"sc_pipe_{name}"] = lambda rgb=rgb, name=name: pipe(9940 + len(name), rgb)
    T["sc_chain"] = lambda: chain(9944)
    T["sc_belt"] = lambda: belt(9945)
    T["sc_chute"] = lambda: chute(9946)
    T["sc_tread"] = lambda: tread(9947)
    T["sc_chips"] = lambda: mulch(9948)
    T["sc_chips_b"] = lambda: mulch(9949, wet=True)
    T["sc_chips_edge"] = lambda: chips_edge(9960)
    T["sc_sand"] = lambda: sand(9961)
    T["sc_timber"] = lambda: timber(9962)
    for i in range(4):
        T[f"sc_merry_{i}"] = lambda i=i: merry(i)
    T["sc_board_out"] = lambda: backboard_out(9963)
    T["sc_chain_net"] = lambda: chain_net(9964)
    T["sc_goal_pad"] = lambda: goal_pad(9965)
    T["sc_alu_plank"] = lambda: alu_plank(9966)
    T["sc_horse"] = lambda: horse_body(9967)
    T["sc_bucket"] = lambda: bucket(9968)
    T["sc_turf"] = lambda: turf(9970)
    T["sc_turf_d"] = lambda: turf(9971, dark=True)
    T["sc_turf_line"] = lambda: turf(9972, line=True)
    T["sc_turf_mud"] = lambda: turf(9973, mud=True)
    T["sc_turf_rough"] = lambda: turf(9975, rough=True)
    T["sc_turf_side"] = lambda: turf_side(9974)
    return T


def register(ns):
    """Hands the textures over (the stencil reads the asphalt photo through ns)."""
    global NS
    NS = ns
    made = textures()
    for i in range(3):
        made[f"sc_stencil_{i}"] = lambda i=i: stencil(i)
    ns.EXTRA.update(made)


# ---------------------------------------------------------------- the blocks

def turn_z(boxes):
    """The same boxes knocked over onto their right side (a quarter turn about the north-south axis)."""
    out = []
    for b in boxes:
        x0, y0, z0, x1, y1, z1 = b[:6]
        out.append([y0, 16 - x1, z0, y1, 16 - x0, z1, b[6]])
    return out


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, loot=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if loot:
            spec["loot"] = loot
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    steel, chrome, black, grey, oak = "streetlight_metal", "home_chrome", "home_plastic_black", "bunk_grey", "cv_oak"
    alu, lam, white = "ext_alu", "desk_laminate", "home_white"

    def face(front, side):
        return {"north": front, "side": side, "up": side, "down": side}

    # ---- walls and floors
    add("sc_cmu", None, "Painted Block", "STONE", rotate=None, tex={"all": "sc_cmu"})
    for k in COLOURS:
        add(f"sc_cmu_dado_{k}", None, "Painted Block", "STONE", rotate=None, tex={"side": f"sc_cmu_dado_{k}", "up": "sc_cmu", "down": "sc_cmu"})
        add(f"sc_vct_{k}", None, "Vinyl Tile", "STONE", rotate=None, tex={"up": f"sc_vct_{k}", "side": "floor_concrete", "down": "floor_concrete"})
        add(f"sc_court_key_{k}", None, "Gym Floor", "WOOD", rotate=None, tex={"up": f"sc_court_key_{k}", "side": "sc_court", "down": "sc_court"})
        add(f"sc_spandrel_{k}", None, "Enamel Panel", "METAL", rotate=None, tex={"all": f"sc_spandrel_{k}"})
        add(f"sc_pad_{k}", [[0, 0, 0, 16, 16, 16, face(f"sc_pad_{k}", "sc_cmu")]], "Wall Pad", "WOOL")
        for i in range(16):
            add(f"sc_court_c_{k}_{i}", None, "Gym Floor", "WOOD", rotate=None, tex={"up": f"sc_court_c_{k}_{i}", "side": "sc_court", "down": "sc_court"})
    for kind in ("gym_l", "gym_r", "x", "bus", "kids", "hands"):
        add(f"sc_note_{kind}", [[0, 0, 0, 16, 16, 16, face(f"sc_note_{kind}", "sc_cmu")]], "Painted Block", "STONE")
    for i in range(12):
        add(f"sc_mural_{i}", [[0, 0, 0, 16, 16, 16, face(f"sc_mural_{i}", "sc_cmu")]], "Mural", "STONE")
    for name in ("sc_vct", "sc_vct_b"):
        add(name, None, "Vinyl Tile", "STONE", rotate=None, tex={"up": name, "side": "floor_concrete", "down": "floor_concrete"})
    add("sc_court", None, "Gym Floor", "WOOD", rotate=None, tex={"up": "sc_court", "side": "sc_court", "down": "sc_court"})
    add("sc_court_line", None, "Gym Floor", "WOOD", rotate="axis", tex={"up": "sc_court_line", "side": "sc_court", "down": "sc_court"})

    # ---- the classroom
    for kind in CHALKBOARDS:
        for i in range(3):
            boxes = [[0, 2, 15, 16, 16, 16, face(f"sc_chalk_{kind}_{i}", alu)],
                     [0, 1.4, 13, 16, 2, 16, alu], [0, 2, 13, 16, 2.6, 13.4, alu]]
            boxes += [[[3, 2, 13.6, 7, 3.2, 15, "sc_beige"], [3, 3.2, 13.6, 7, 3.6, 15, "home_wood_dark"]],
                      [[6, 2, 14, 8.5, 2.5, 14.5, white], [9, 2, 14.2, 10, 2.5, 14.7, "sc_apple"]],
                      [[10, 2, 14, 12.5, 2.5, 14.5, white]]][i]
            add(f"sc_chalk_{kind}_{i}", boxes, "Chalkboard", "WOOD", decor=True)
    for i in range(5):
        add(f"sc_alpha_{i}", [[0, 5, 15.6, 16, 15, 16, f"sc_alpha_{i}"]], "Alphabet Cards", "WOOL", decor=True)
    for v, seat in (("a", "sc_seat_orange"), ("b", "sc_seat_blue")):
        desk = [[3, 0, 9, 4, 7, 10, chrome], [12, 0, 9, 13, 7, 10, chrome], [3, 0, 14, 4, 7, 15, chrome], [12, 0, 14, 13, 7, 15, chrome],
                [2.5, 7, 8.5, 13.5, 8, 15.5, seat], [3, 10, 15, 13, 15, 16, seat], [3.5, 8, 15.2, 4.5, 10, 15.8, chrome],
                [11.5, 8, 15.2, 12.5, 10, 15.8, chrome], [12, 6, 2, 13, 7, 9, chrome], [12, 7, 2, 13, 11, 3, chrome],
                [2, 11, 0, 14, 12, 8, {"up": "sc_desktop", "side": lam, "down": lam}], [3.5, 2, 9, 12.5, 5, 14.5, "bunk_mesh"]]
        add(f"sc_desk_{v}", desk, "School Desk", "METAL")
        add(f"sc_desk_{v}_tipped", turn_z(desk), "School Desk", "METAL")
    top = {"up": "sc_desktop", "side": lam, "down": lam}
    add("sc_tdesk_ped", [[0, 12, 0, 16, 13.2, 16, top], [1, 0.6, 1, 8, 12, 15, {"south": "sc_drawers", "side": "sc_steel_green", "up": "sc_steel_green", "down": "sc_steel_green"}],
                         [1, 0, 1, 8, 0.6, 15, grey], [8, 2, 0.5, 16, 12, 1.5, "sc_steel_green"]], "Teacher's Desk", "METAL", loot="school")
    add("sc_tdesk_knee", [[0, 12, 0, 16, 13.2, 16, top], [0, 10.5, 2, 15, 12, 14, {"south": "sc_steel_green", "side": "sc_steel_green"}],
                          [0, 2, 0.5, 15, 12, 1.5, "sc_steel_green"], [15, 0, 1, 16, 12, 15, "sc_steel_green"]], "Teacher's Desk", "METAL")
    add("sc_desk_things", [[2, 0, 3, 9, 0.6, 10, {"up": "sc_gradebook", "side": white}], [10.5, 0, 4, 12.5, 2, 6, "sc_apple"],
                           [11.3, 2, 4.8, 11.7, 2.8, 5.2, "home_wood_dark"], [11, 0, 10, 13.5, 3.5, 12.5, "sc_beige"],
                           [11.5, 3.5, 10.6, 11.9, 5, 11, "sc_apple"], [12.3, 3.5, 11.2, 12.7, 5.2, 11.6, "sc_seat_blue"],
                           [3, 0, 11, 8, 1.4, 14.5, "paper"], [13, 0, 1, 15, 1.5, 3, "sc_red"]], "Desk Things", "WOOD", decor=True)
    add("sc_cubby", [[0, 0, 4, 16, 16, 16, face("sc_cubby", oak)]], "Cubbies", "WOOD", loot="school")
    hooks = [[0, 11, 15, 16, 12.5, 16, oak]] + [[x, 10, 14.4, x + 1, 12, 15, chrome] for x in (2.5, 7.5, 12.5)]
    add("sc_hooks_a", hooks + [[1, 1, 13, 5, 10.5, 15, "sc_fabric_red"], [1.5, 6, 12.6, 4.5, 9, 13, "sc_fabric_red"],
                               [6, 3, 12.5, 10, 9.5, 15, "sc_fabric_blue"], [6.5, 4, 12, 9.5, 7, 12.5, "sc_fabric_blue"],
                               [11, 0, 13, 15, 10.5, 15, "sc_fabric_yellow"]], "Coat Hooks", "WOOD", decor=True)
    add("sc_hooks_b", hooks + [[6, 3, 12.5, 10, 9.5, 15, "sc_fabric_green"], [12, 4, 14, 13, 10.5, 15, "sc_fabric_red"]],
        "Coat Hooks", "WOOD", decor=True)
    for kind in ("art", "info"):
        for i in range(2):
            add(f"sc_board_{kind}_{i}", [[0, 1, 15, 16, 15, 16, face(f"sc_board_{kind}_{i}", oak)]], "Bulletin Board", "WOOD", decor=True)
    add("sc_pa", [[4, 5, 14, 12, 13, 16, face("sc_pa", "sc_beige")]], "Speaker", "WOOD", decor=True)
    for i in range(4):
        add(f"sc_rug_{i}", [[0, 0, 0, 16, 0.5, 16, {"up": f"sc_rug_{i}", "side": f"sc_rug_{i}", "down": f"sc_rug_{i}"}]],
            "Reading Rug", "WOOL", rotate=None, decor=True)
    add("sc_shelf_low", [[0, 0, 3, 16, 10, 16, face("sc_shelf_low", oak)], [0, 10, 2.5, 16, 10.6, 16, oak],
                         [2, 10.6, 5, 7, 13, 12, "sc_seat_orange"], [9, 10.6, 5, 14, 13, 12, "sc_seat_blue"]], "Bookcase", "WOOD", loot="school")
    add("sc_tank", [[1, 0, 4, 15, 8, 15, oak], [1.5, 8, 4.5, 14.5, 15, 14.5, {"side": "sc_tank", "up": "sc_tank", "down": "sc_tank"}],
                    [1.3, 15, 4.3, 14.7, 16, 14.7, black]], "Fish Tank", "GLASS")
    cart = [[1, 15, 2, 15, 16, 14, grey], [1, 7, 2, 15, 8, 14, grey], [1, 0.8, 2, 15, 1.6, 14, grey]] + \
           [[x, 0.6, z, x + 1, 16, z + 1, grey] for x in (1, 14) for z in (2, 13)] + \
           [[x, 0, z, x + 1.4, 0.6, z + 1.4, black] for x in (1, 13.6) for z in (2, 12.6)]
    add("sc_av_cart", cart + [[3, 8, 4, 12, 10, 11, "sc_beige"]], "AV Cart", "METAL")
    add("sc_projector", [[2, 8, 3, 14, 9, 13, grey]] + [[x, 0, z, x + 1, 8, z + 1, grey] for x in (2, 13) for z in (3, 12)] +
        [[3.5, 9, 4, 12.5, 12, 12, {"north": "sc_beige", "side": "sc_beige", "up": "sc_glass"}], [7.2, 12, 10, 8.2, 20, 11, grey],
         [6, 19, 7.5, 9.5, 21.5, 12, grey], [6.8, 19.6, 7, 8.7, 21, 7.5, "sc_glass"]], "Overhead Projector", "METAL")
    for colour in ("red", "blue", "green"):
        fab = f"sc_fabric_{colour}"
        add(f"sc_pack_{colour}", [[4, 0, 5, 12, 7, 10, fab], [5, 1, 4, 11, 4.5, 5, fab], [5, 4, 10, 6, 7, 11, black], [10, 4, 10, 11, 7, 11, black],
                                  [6.5, 7, 7, 9.5, 7.6, 8, black]], "Backpack", "WOOL", decor=True, loot="school")
    add("sc_lunchbox", [[5, 0, 6, 11, 4, 10, "sc_lunchbox"], [7, 4, 7.6, 9, 5, 8.4, black]], "Lunchbox", "METAL", decor=True)

    # ---- the halls
    for k in COLOURS:
        side = f"sc_locker_side_{k}"
        for part in ("lo", "hi"):
            name = "sc_locker" if part == "lo" else "sc_locker_top"
            boxes = [[0, 0, 10, 16, 16, 16, {"north": f"sc_locker_{part}_{k}", "side": side, "up": side, "down": side}]]
            add(f"{name}_{k}", boxes, "Locker", "METAL", **({"loot": "school"} if part == "lo" else {}))
            door = [[10.5, 0.4 if part == "lo" else 0, 4.8, 11.1, 16 if part == "lo" else 15.6, 10, side]]
            add(f"{name}_open_{k}", [[0, 0, 10, 16, 16, 16, {"north": f"sc_locker_{part}_{k}_open", "side": side, "up": side, "down": side}]] + door,
                "Locker", "METAL", **({"loot": "school"} if part == "lo" else {}))
    add("sc_fountain", [[3, 9, 8, 13, 11, 16, "sc_fountain"], [4, 4, 12, 12, 9, 16, "sc_fountain"], [7.5, 11, 10.5, 8.5, 12, 11.5, chrome],
                        [12.5, 9.5, 10, 13.5, 10.5, 11, chrome], [7, 0, 14, 9, 4, 16, "bunk_pipe_grey"]], "Drinking Fountain", "METAL")
    for i in range(2):
        cups = [[[3, 7, 9, 5, 10, 11, "sc_gold"], [2.5, 10, 8.5, 5.5, 12, 11.5, "sc_gold"], [3.5, 6, 9.5, 4.5, 7, 10.5, black],
                 [10, 7, 10, 13, 8, 12, black], [10.5, 8, 10.5, 12.5, 13, 11.5, "sc_gold"]],
                [[4, 7, 9, 6, 9, 11, black], [4.3, 9, 9.3, 5.7, 14, 10.7, "sc_gold"], [9, 7, 8, 14, 11, 9, "sc_ball_orange"]]][i]
        add(f"sc_trophy_lo_{i}", [[0, 0, 6, 16, 6, 16, face("sc_counter_front", oak)],
                                  [0, 6, 15, 16, 16, 16, face(f"sc_trophies_lo_{i}", oak)], [0, 6, 6, 16, 7, 15, oak],
                                  [0, 7, 6, 16, 16, 6.4, "sc_glass"]] + cups, "Trophy Case", "WOOD")
        cups_hi = [[[2, 6.6, 9, 4, 9, 11, "sc_gold"], [11, 6.6, 9, 14, 7.6, 11, black], [11.5, 7.6, 9.5, 13.5, 12, 10.5, "sc_gold"]],
                   [[5, 6.6, 8, 11, 10, 12, "sc_ball_red"], [1, 6.6, 9, 3, 11, 11, "sc_gold"]]][i]
        add(f"sc_trophy_hi_{i}", [[0, 0, 15, 16, 14, 16, face(f"sc_trophies_hi_{i}", oak)], [0, 6, 6, 16, 6.6, 15, oak],
                                  [0, 0, 6, 16, 14, 6.4, "sc_glass"], [0, 14, 5.5, 16, 16, 16, oak]] + cups_hi, "Trophy Case", "WOOD")
    for k in COLOURS:
        for i in range(3):
            add(f"sc_pennant_{k}_{i}", [[0, 4, 15.6, 16, 14, 16, f"sc_pennant_{k}_{i}"]], "Pennant", "WOOL", decor=True)
            add(f"sc_banner_{k}_{i}", [[0, 1, 15.5, 16, 15, 16, f"sc_banner_{k}_{i}"]], "Banner", "WOOL", decor=True)
    for n in ROOMS:
        add(f"sc_room_{n}", [[3, 4, 15.4, 13, 11, 16, face(f"sc_room_{n}", "home_wood_dark")]], "Room Sign", "WOOD", decor=True)
    for k in PLAQUES:
        add(f"sc_plaque_{k}", [[3, 4, 15.4, 13, 11, 16, face(f"sc_plaque_{k}", "home_wood_dark")]], "Room Sign", "WOOD", decor=True)
    add("sc_pull", [[5, 3, 14.5, 11, 12, 16, face("sc_pull", "sc_red")], [6, 8, 14, 10, 9.4, 14.5, white]], "Fire Alarm", "METAL", decor=True)
    add("sc_bell", [[3, 3, 14, 13, 13, 15.4, face("sc_bell", "sc_red")], [7, 7, 15.4, 9, 9, 16, grey], [7.5, 7.5, 13.4, 8.5, 8.5, 14, black]],
        "Alarm Bell", "METAL", decor=True)

    # ---- the office
    add("sc_counter", [[0, 14, 0, 16, 16, 16, {"up": lam, "side": lam, "down": lam}], [0, 0, 1, 16, 14, 3, face("sc_counter_front", "cv_oak")],
                       [0, 0, 3, 1, 14, 16, oak], [15, 0, 3, 16, 14, 16, oak], [1, 6, 3, 15, 7, 15, oak],
                       [2, 7, 5, 8, 10, 13, "paper"], [9, 7, 4, 14, 12, 13, "cardboard"]], "Counter", "WOOD", loot="office")
    add("sc_mailboxes", [[0, 0, 8, 16, 15, 16, face("sc_mailboxes", oak)]], "Mail Slots", "WOOD", decor=True, loot="office")
    add("sc_pa_console", [[2, 0, 4, 14, 4, 13, {"up": "sc_pa_panel", "side": "sc_beige", "down": "sc_beige"}], [10, 4, 2, 12, 4.6, 4, black],
                          [10.8, 4.6, 2.8, 11.2, 9, 3.2, grey], [10.2, 9, 1.8, 11.8, 10.6, 3.6, black]], "PA Console", "METAL", decor=True)
    add("sc_mimeo", [[2, 8, 3, 14, 9, 13, grey]] + [[x, 0, z, x + 1, 8, z + 1, grey] for x in (2, 13) for z in (3, 12)] +
        [[3, 9, 4, 13, 14, 12, face("sc_mimeo", "sc_beige")], [4, 12, 5, 12, 15, 11, black], [13, 11, 7, 14.5, 12, 8, chrome],
         [14, 11, 7, 14.6, 14, 7.6, chrome], [4, 9, 0.5, 10, 9.8, 4, "sc_copies"], [3, 4, 4, 13, 4.6, 12, "sc_copies"]], "Duplicator", "METAL")
    add("sc_eye_chart", [[3, 0, 15.6, 13, 16, 16, "sc_eye_chart"]], "Eye Chart", "WOOL", decor=True)
    add("sc_sign_visitors", [[1, 4, 15.4, 15, 12, 16, face("sc_sign_visitors", white)]], "Sign", "METAL", decor=True)

    # ---- the gym
    for side, i in (("l", 0), ("r", 1)):
        bb = [[0, -1.6, 8, 16, 15.2, 9, {"north": f"sc_backboard_{i}", "south": white, "side": white, "up": white, "down": white}]]
        if side == "l":
            rim = [[0, 0.6, 0, 3.6, 1.2, 0.6, "sc_rim"], [3, 0.6, 0.6, 3.6, 1.2, 7, "sc_rim"], [0, 0.6, 7, 3.6, 1.2, 7.6, "sc_rim"],
                   [0, 0, 7.6, 2, 1.2, 8, "sc_rim"], [0, -5, 0.2, 3.4, 0.6, 7.4, "sc_net"], [3, 4, 9, 4, 5, 16, grey], [3, 10, 9, 4, 11, 16, grey]]
        else:
            rim = [[12.4, 0.6, 0, 16, 1.2, 0.6, "sc_rim"], [12.4, 0.6, 0.6, 13, 1.2, 7, "sc_rim"], [12.4, 0.6, 7, 16, 1.2, 7.6, "sc_rim"],
                   [14, 0, 7.6, 16, 1.2, 8, "sc_rim"], [12.6, -5, 0.2, 16, 0.6, 7.4, "sc_net"], [12, 4, 9, 13, 5, 16, grey], [12, 10, 9, 13, 11, 16, grey]]
        add(f"sc_hoop_{side}", bb + rim, "Basketball Hoop", "METAL", decor=True)
    for i in range(3):
        for lit in ("", "_lit"):
            add(f"sc_score_{i}{lit}", [[0, 0, 12, 16, 16, 16, face(f"sc_score_{i}{lit}", black)]], "Scoreboard", "METAL", decor=True,
                **({"light": 6} if lit else {}))
    wood = "sc_bleacher_wood"
    add("sc_bleacher", [[0, 9, 8, 16, 11, 16, wood], [0, 3, 0, 16, 4.5, 8, wood], [1, 0, 0, 2, 9, 16, grey], [14, 0, 0, 15, 9, 16, grey],
                        [0, 4.5, 7.6, 16, 9, 8.4, grey]], "Bleachers", "WOOD")
    add("sc_bleacher_frame", [[1, 0, 0, 2, 16, 16, grey], [14, 0, 0, 15, 16, 16, grey], [2, 7, 7, 14, 8, 9, grey]], "Bleachers", "METAL")
    stools = [[x, 6.5, z, x + 3, 7.5, z + 3, "sc_seat_orange"] for x in (0.2, 12.8) for z in (2, 11)]
    arms = [[3.2, 6, z + 1, 7.5, 6.6, z + 2, chrome] for z in (2, 11)] + [[8.5, 6, z + 1, 12.8, 6.6, z + 2, chrome] for z in (2, 11)]
    add("sc_cafe_table", [[3, 10, 0, 13, 11, 16, {"up": "sc_cafe_top", "side": "sc_cafe_top", "down": grey}], [7.5, 6, 0, 8.5, 10, 16, chrome],
                          [7, 0, 6, 9, 6, 10, chrome], [5, 0, 2, 11, 0.8, 14, chrome]] + stools + arms, "Cafeteria Table", "METAL",
        rotate="axis")
    fold = [[3, 1, 6, 13, 16, 7.4, {"north": "sc_cafe_top", "south": grey, "side": chrome}], [3, 1, 8.6, 13, 16, 10, {"south": "sc_cafe_top", "north": grey, "side": chrome}],
            [4, 0, 7, 5.4, 1, 9, black], [10.6, 0, 7, 12, 1, 9, black], [2.5, 2, 5, 3.5, 3, 11, chrome], [12.5, 2, 5, 13.5, 3, 11, chrome]]
    add("sc_cafe_folded", fold, "Folded Table", "METAL")
    add("sc_cafe_folded_top", [[3, 0, 6, 13, 12, 7.4, {"north": "sc_cafe_top", "south": grey, "side": chrome}],
                               [3, 0, 8.6, 13, 12, 10, {"south": "sc_cafe_top", "north": grey, "side": chrome}], [3, 12, 7.4, 13, 12.6, 8.6, chrome]],
        "Folded Table", "METAL")
    rails = [[1, 5, 0, 2, 6, 16, alu], [14, 5, 0, 15, 6, 16, alu], [2, 5.4, 0, 14, 6.2, 16, "sc_canvas"]]
    legs_h = [[1, 0, 1.5, 2, 5, 2.5, alu], [14, 0, 1.5, 15, 5, 2.5, alu], [1, 1.5, 1.5, 15, 2.3, 2.5, alu]]
    legs_f = [[1, 0, 13.5, 2, 5, 14.5, alu], [14, 0, 13.5, 15, 5, 14.5, alu], [1, 1.5, 13.5, 15, 2.3, 14.5, alu]]
    add("sc_cot_head", rails + legs_h + [[3, 6.2, 1, 13, 8, 6, "home_pillow"], [2.5, 6.2, 9, 13.5, 7.2, 16, "sc_blanket"]], "Cot", "WOOL")
    add("sc_cot_foot", rails + legs_f + [[2.5, 6.2, 0, 13.5, 7.2, 12, "sc_blanket"], [2.2, 3.4, 11.5, 13.8, 7.2, 12.2, "sc_blanket"]], "Cot", "WOOL")
    add("sc_cot_head_b", rails + legs_h + [[3.5, 6.2, 1.5, 12.5, 7.6, 5.5, "home_pillow"], [6, 6.2, 8, 10, 10, 11, "sc_teddy"],
                                           [6.5, 10, 8.5, 9.5, 12.5, 11, "sc_teddy"], [6.2, 12, 9.2, 7.2, 13, 10, "sc_teddy"], [8.8, 12, 9.2, 9.8, 13, 10, "sc_teddy"]],
        "Cot", "WOOL")
    add("sc_cot_foot_b", rails + legs_f + [[3, 6.2, 1, 12, 9, 9, "sc_blanket_b"], [5, 9, 3, 11, 10, 8, "sc_blanket_b"], [12, 0, 4, 16, 6.2, 10, "sc_blanket_b"]],
        "Cot", "WOOL")
    for i in range(2):
        add(f"sc_shelter_{i}", [[0, 0, 15.5, 16, 16, 16, f"sc_shelter_{i}"]], "Shelter Banner", "WOOL", decor=True)
        add(f"sc_messages_{i}", [[0, 0, 15.6, 16, 16, 16, f"sc_messages_{i}"]], "Messages", "WOOL", decor=True)
    add("sc_register", [[2, 0, 3, 8, 3, 3.6, "sc_register_card"], [2, 0, 4.4, 8, 3, 5, "sc_register_card"], [2, 3, 3.6, 8, 3.3, 4.4, "sc_register_card"],
                        [8, 0, 3, 15, 0.4, 13, {"up": "sc_clipboard", "side": "home_wood_dark"}], [3, 0, 9, 6, 3, 12, chrome],
                        [3.5, 3, 10, 3.9, 5, 10.4, "sc_seat_blue"], [4.5, 3, 10.5, 4.9, 5.2, 10.9, black]], "Registration", "WOOD", decor=True)
    add("sc_cases_water", [[1, 0, 1, 15, 6, 15, "sc_water_case"], [2, 6, 2, 14, 12, 14, "sc_water_case"]], "Water", "WOOL", loot="drinks")
    add("sc_cases_blankets", [[1, 0, 2, 15, 10, 14, face("sc_box_blankets", "cardboard")], [3, 10, 4, 13, 15, 13, face("sc_box_cots", "cardboard")]],
        "Supplies", "WOOD", loot="home_boxes")
    add("sc_ball_cage", [[1, 1, 1, 15, 13, 15, "sc_cage"], [3, 1, 3, 7, 5, 7, "sc_ball_orange"], [8, 1, 4, 12, 5, 8, "sc_ball_red"],
                         [4, 5, 8, 8, 9, 12, "sc_ball_orange"], [9, 5, 9, 13, 9, 13, "sc_ball_red"], [2, 0, 2, 3, 1, 3, black],
                         [13, 0, 2, 14, 1, 3, black], [2, 0, 13, 3, 1, 14, black], [13, 0, 13, 14, 1, 14, black]], "Ball Cart", "METAL", loot="school_gym")
    add("sc_rope", [[7, 0, 7, 9, 16, 9, "sc_rope"]], "Climbing Rope", "WOOL", rotate=None, decor=True)
    add("sc_rope_end", [[7, 2, 7, 9, 16, 9, "sc_rope"], [6, 0, 6, 10, 3, 10, "sc_rope"]], "Climbing Rope", "WOOL", rotate=None, decor=True)
    add("sc_ball_red", [[5, 0, 5, 11, 6, 11, "sc_ball_red"], [5.5, 0.5, 4.6, 10.5, 5.5, 11.4, "sc_ball_red"]], "Ball", "WOOL", rotate=None, decor=True)
    add("sc_ball_orange", [[5, 0, 5, 11, 6, 11, "sc_ball_orange"], [5.5, 0.5, 4.6, 10.5, 5.5, 11.4, "sc_ball_orange"]], "Basketball", "WOOL",
        rotate=None, decor=True)
    add("sc_milk", [[1, 0, 2, 15, 12, 15, {"north": "sc_milk", "side": white, "up": "fit_stainless", "down": white}],
                    [2, 12, 2.5, 14, 12.6, 14.5, "fit_stainless"]], "Milk Cooler", "METAL", loot="drinks")
    add("sc_trays", [[2, 0, 3, 14, 3, 13, {"side": "sc_trays", "up": "sc_trays", "down": "sc_trays"}]], "Trays", "WOOD", decor=True)

    # ---- outside
    for key in NAMES:
        for i in range(10):
            name = f"sc_letters_{key}_{i}"
            add(name, [[0, 0, 15.2, 16, 16, 16, name]], "Lettering", "METAL", decor=True)
        for i in range(3):
            add(f"sc_monument_{key}_{i}", [[0, 0, 5, 16, 16, 11, {"north": f"sc_monument_{key}_{i}", "south": alu, "side": alu, "up": alu, "down": alu}]],
                "School Sign", "METAL")
    for kind in LBOARDS:
        for i in range(3):
            add(f"sc_lboard_{kind}_{i}", [[0, 0, 5, 16, 16, 11, {"north": f"sc_lboard_{kind}_{i}", "south": alu, "side": alu, "up": alu, "down": alu}]],
                "Letter Board", "METAL")
    add("sc_sign_school", [[7, 0, 7, 9, 16, 9, steel], [1, 4, 8.6, 15, 16, 9.2, {"north": "sc_sign_school", "south": steel, "side": steel}]],
        "School Sign", "METAL")
    add("sc_sign_bus", [[7, 0, 7, 9, 4, 9, steel], [2, 4, 9, 14, 16, 9.6, {"north": "sc_sign_bus", "south": steel, "side": steel}]], "Sign", "METAL")
    for i in range(3):
        add(f"sc_stencil_{i}", None, "Asphalt", "STONE", rotate=None, tex={"up": f"sc_stencil_{i}", "side": "asphalt", "down": "asphalt"},
            height=14)
    rack = [[0, 0, 7, 16, 1, 9, "sc_galv"], [0, 11, 7.4, 16, 12, 8.6, "sc_galv"]] + [[x, 1, 7.6, x + 0.8, 11, 8.4, "sc_galv"] for x in range(1, 16, 3)]
    add("sc_bike_rack", rack, "Bike Rack", "METAL", rotate="axis")

    # ---- the grounds: the playground, the court, the field
    def pipe_x(x0, x1, y, z, t, r=1.25):
        return [x0, y - r, z - r, x1, y + r, z + r, t]

    def lean(xa, xb, z, y0, y1, t, w=1.25, steps=4):
        """A pipe from (xa, y0) to (xb, y1) at depth z, as a stair of short boxes."""
        out = []
        for k in range(steps):
            ya, yb = y0 + (y1 - y0) * k / steps, y0 + (y1 - y0) * (k + 1) / steps
            xm = xa + (xb - xa) * (k + 0.5) / steps
            out.append([round(xm - w, 3), round(ya, 3), z - w, round(xm + w, 3), round(yb, 3), z + w, t])
        return out

    blue, red, yellow, green = "sc_pipe_blue", "sc_pipe_red", "sc_pipe_yellow", "sc_pipe_green"
    ch, tim = "sc_chain", "sc_timber"
    # the swing set: the A-frame legs splayed across x, the bar along z, two swings to a bay
    for k in range(3):
        legs = lean(1.5 + 2 * k, 3.5 + 2 * k, 8, 0, 16, blue) + lean(14.5 - 2 * k, 12.5 - 2 * k, 8, 0, 16, blue)
        if k == 0:
            legs += [[0, 0, 6, 4, 0.6, 10, grey], [12, 0, 6, 16, 0.6, 10, grey], [3, 6, 7.4, 13, 7, 8.6, blue]]
        if k == 2:
            legs += [[5.5, 12.5, 5.5, 10.5, 16, 10.5, blue], [6.75, 13, 0, 9.25, 15.5, 16, blue]]
        add(f"sc_swing_leg_{k}", legs, "Swing Set", "METAL", rotate="axis")
    bar = [[6.75, 13, 0, 9.25, 15.5, 16, blue]]
    hang = [[7.6, 0, z - 0.4, 8.4, 13, z + 0.4, ch] for z in (4, 12)] + [[7.3, 12.5, z - 0.7, 8.7, 13.2, z + 0.7, grey] for z in (4, 12)]
    add("sc_swing_bar", bar, "Swing Set", "METAL", rotate="axis")
    add("sc_swing_top", bar + hang, "Swing Set", "METAL", rotate="axis")
    add("sc_swing_chain", [[7.6, 0, z - 0.4, 8.4, 16, z + 0.4, ch] for z in (4, 12)], "Swing", "METAL", rotate="axis", decor=True)
    seat = [[6, 6, 3, 10, 7, 5, "sc_belt"], [6, 5, 5, 10, 6, 11, "sc_belt"], [6, 6, 11, 10, 7, 13, "sc_belt"]]
    add("sc_swing_seat", [[7.6, 7, z - 0.4, 8.4, 16, z + 0.4, ch] for z in (4, 12)] + seat, "Swing", "METAL", rotate="axis", decor=True)
    add("sc_swing_snapped", [[7.6, 5, 3.6, 8.4, 16, 4.4, ch], [7.6, 11, 11.6, 8.4, 16, 12.4, ch], [6.5, 0.5, 3.4, 9.5, 5, 5, "sc_belt"],
                             [7.6, 0, 9, 8.4, 0.6, 14, ch]], "Swing", "METAL", rotate="axis", decor=True)
    add("sc_swing_wrapped", bar + [[6.5, 15.5, 5, 9.5, 16, 11, "sc_belt"], [6.2, 12.4, 4, 9.8, 16, 5, ch], [6.2, 12.4, 11, 9.8, 16, 12, ch],
                                   [7.6, 8, 4.6, 8.4, 12.5, 5.4, ch], [7.6, 9.5, 10.6, 8.4, 12.5, 11.4, ch]], "Swing Set", "METAL", rotate="axis")
    # the slide (base faces the way it runs out: north): the ladder behind, the deck, the chute down in three
    posts = [[1, 0, 1, 3, 16, 3, red], [13, 0, 1, 15, 16, 3, red], [1, 0, 13, 3, 16, 15, red], [13, 0, 13, 15, 16, 15, red]]
    add("sc_slide_post", posts + [[3, 7, 1.5, 13, 8, 2.5, red], [3, 7, 13.5, 13, 8, 14.5, red]], "Slide", "METAL")
    deck = [[0, 0, 0, 16, 1.5, 16, "sc_tread"]] + [[x, 1.5, z, x + 2, 12, z + 2, red] for x in (1, 13) for z in (1, 13)]
    deck += [[1, 10, 3, 3, 11.5, 13, red], [13, 10, 3, 15, 11.5, 13, red], [1, 5.5, 3, 3, 6.5, 13, red], [13, 5.5, 3, 15, 6.5, 13, red],
             [1, 11.5, 1, 15, 13, 3, red]]
    add("sc_slide_deck", deck, "Slide", "METAL")
    rails = [[3, 0, 6, 4, 16, 7.5, red], [12, 0, 6, 13, 16, 7.5, red]] + [[4, y, 6.4, 12, y + 1, 7.4, "sc_tread"] for y in (2, 6, 10, 14)]
    add("sc_slide_ladder", rails, "Slide Ladder", "METAL")
    add("sc_slide_ladder_top", [[3, 0, 6, 4, 10, 7.5, red], [12, 0, 6, 13, 10, 7.5, red], [3, 9, 0, 4, 10, 6, red], [12, 9, 0, 13, 10, 6, red]],
        "Slide Ladder", "METAL")

    def run(top_at, steps=8, z_lo=0, z_hi=16):
        out = []
        for i in range(steps):
            z0 = z_lo + (z_hi - z_lo) * i / steps
            z1 = z_lo + (z_hi - z_lo) * (i + 1) / steps
            top = min(16, top_at(z1))
            out.append([3, round(top - 1.5, 3), round(z0, 3), 13, round(top, 3), round(z1, 3), "sc_chute"])
            out.append([2, round(top - 1.5, 3), round(z0, 3), 3, round(min(16, top + 3.5), 3), round(z1, 3), "sc_chute"])
            out.append([13, round(top - 1.5, 3), round(z0, 3), 14, round(min(16, top + 3.5), 3), round(z1, 3), "sc_chute"])
        return out
    add("sc_slide_hi", run(lambda z: z, 16), "Slide", "METAL")
    add("sc_slide_lo", run(lambda z: 4 + 12 * z / 16, 16) + [[3, 0, 7, 4, 7, 8, red], [12, 0, 7, 13, 7, 8, red]], "Slide", "METAL")
    add("sc_slide_out", run(lambda z: 4, 6, 4, 16) + [[3, 3.5, 0, 13, 5, 4, "sc_chute"], [2, 3.5, 0, 3, 6, 4, "sc_chute"], [13, 3.5, 0, 14, 6, 4, "sc_chute"],
                                                     [3, 0, 1, 4, 3.5, 3, red], [12, 0, 1, 13, 3.5, 3, red]], "Slide", "METAL")
    # the monkey bars (axis): the ladder ends, the rungs overhead
    up = [[1, 0, 7, 3, 16, 9, yellow], [13, 0, 7, 15, 16, 9, yellow]]
    add("sc_monkey_ladder", up + [[3, y, 7.4, 13, y + 1, 8.6, yellow] for y in (3, 8, 13)], "Monkey Bars", "METAL", rotate="axis")
    bars = [[1, 13, 0, 3, 15, 16, yellow], [13, 13, 0, 15, 15, 16, yellow]] + [[3, 13.5, z, 13, 14.5, z + 1, grey] for z in (1.5, 5.5, 9.5, 13.5)]
    add("sc_monkey_bars", bars, "Monkey Bars", "METAL", rotate="axis")
    add("sc_monkey_end", bars + [[1, 0, 7, 3, 13, 9, yellow], [13, 0, 7, 15, 13, 9, yellow]], "Monkey Bars", "METAL", rotate="axis")
    # the merry-go-round, two by two (tile i at column i % 2, row i // 2 from the north-west): the deck's
    # quarter, the hub, the rail out to its post
    for i in range(4):
        col, row = i % 2, i // 2
        fx = (lambda v: 16 - v) if col == 0 else (lambda v: v)
        fz = (lambda v: 16 - v) if row == 0 else (lambda v: v)

        def box(a0, b0, a1, b1, y0, y1, t):
            xa, xb = sorted((fx(a0), fx(a1)))
            za, zb = sorted((fz(b0), fz(b1)))
            return [round(xa, 3), y0, round(za, 3), round(xb, 3), y1, round(zb, 3), t]
        r, parts = 15, []
        for k in range(4):
            a, b = r * k / 4, r * (k + 1) / 4
            ext = (r * r - a * a) ** 0.5
            parts.append(box(a, 0, b, ext, 2, 3.5, {"up": f"sc_merry_{i}", "side": "sc_tread", "down": grey}))
        parts.append(box(0, 0, 3, 3, 0, 2, grey))
        parts.append(box(0, 0, 1.5, 1.5, 3.5, 11, blue))
        parts.append(box(9, 9, 10.5, 10.5, 3.5, 10, blue))
        for k in range(3):
            parts.append(box(1.5 + 2.6 * k, 1.5 + 2.6 * k, 4.1 + 2.6 * k, 4.1 + 2.6 * k, 9, 10, blue))
        add(f"sc_merry_{i}", parts, "Merry-Go-Round", "METAL", rotate=None)
    # the spring rider
    coil = [[6.5 - (y % 2) * 0.6, y, 6.5 - (y % 2) * 0.6, 9.5 + (y % 2) * 0.6, y + 1, 9.5 + (y % 2) * 0.6, "sc_galv"] for y in range(0, 6)]
    hb = "sc_horse"
    add("sc_horse", [[4.5, 0, 4.5, 11.5, 0.3, 11.5, "concrete_dark"]] + coil +
        [[5.5, 6, 4, 10.5, 10, 13, hb], [6, 9, 1, 10, 14, 5, hb], [6.5, 9.5, 0, 9.5, 12, 1.2, hb], [6.5, 14, 2, 7.5, 15.6, 3, hb],
         [8.5, 14, 2, 9.5, 15.6, 3, hb], [7.5, 10, 5, 8.5, 15, 7, black], [7.5, 8, 13, 8.5, 10.5, 14.8, black],
         [5.4, 10, 7, 10.6, 10.6, 11, red], [4, 12, 3.4, 12, 12.8, 4.2, chrome], [3.5, 7, 7.5, 5.5, 7.6, 9, chrome],
         [10.5, 7, 7.5, 12.5, 7.6, 9, chrome]], "Spring Rider", "METAL")
    # the seesaw (axis): three long, tile 0 the low end, 1 the pivot, 2 the high end
    for t in range(3):
        parts = []
        for j in range(4):
            y = 2 + 0.25 * (16 * t + 4 * j + 2)
            parts.append([6, round(y - 1, 3), 4 * j, 10, round(y + 1, 3), 4 * j + 4, tim])
        if t == 1:
            parts += [[5, 0, 7, 6, 8, 9, green], [10, 0, 7, 11, 8, 9, green], [5, 7, 7.5, 11, 8, 8.5, grey], [4, 0, 6, 12, 0.6, 10, grey]]
        else:
            zc = 3.5 if t == 0 else 12.5
            y = 2 + 0.25 * (16 * t + zc)
            parts += [[7.5, y + 1, zc - 0.5, 8.5, y + 6, zc + 0.5, green], [5, y + 5, zc - 0.5, 11, y + 6, zc + 0.5, green]]
            parts += [[5.5, y + 0.5, 0 if t == 0 else 13.5, 10.5, y + 1.6, 2.5 if t == 0 else 16, black]]
        add(f"sc_seesaw_{t}", parts, "Seesaw", "WOOD", rotate="axis")
    # the sandbox and the mulch, the timbers that hold them in
    add("sc_sand", None, "Sand", "SAND", rotate=None, tex={"up": "sc_sand", "side": "sc_sand", "down": "sc_sand"}, height=13)
    add("sc_sand_edge", [[0, 0, 3, 16, 13, 16, {"up": "sc_sand", "side": "sc_sand", "down": "sc_sand"}], [0, 0, 0, 16, 16, 3, tim]], "Sandbox", "WOOD")
    add("sc_sand_corner", [[3, 0, 3, 16, 13, 16, {"up": "sc_sand", "side": "sc_sand", "down": "sc_sand"}], [0, 0, 0, 16, 16, 3, tim],
                           [0, 0, 3, 3, 16, 16, tim]], "Sandbox", "WOOD")
    add("sc_bucket", [[5, 0, 5, 10, 5, 10, "sc_bucket"], [5.5, 5, 7.2, 9.5, 5.6, 7.8, grey], [10.5, 0, 3, 11.5, 0.6, 12, "sc_pipe_blue"],
                      [10, 0, 2, 12, 0.8, 4, "sc_pipe_blue"]], "Bucket", "WOOD", decor=True)
    for name in ("sc_chips", "sc_chips_b"):
        add(name, None, "Wood Chips", "GRASS", rotate=None, tex={"all": name})
    add("sc_chips_edge", None, "Wood Chips", "GRASS", tex={"up": "sc_chips_edge", "side": "sc_chips", "down": "sc_chips"})
    # the outdoor hoop: the post, the gooseneck over the top, the board and rim on it, the chain net under
    add("sc_hoop_post", [[6.5, 0, 6.5, 9.5, 16, 9.5, "sc_galv"]], "Hoop Post", "METAL", rotate=None)
    add("sc_hoop_neck", [[6.5, 0, 6.5, 9.5, 9, 9.5, "sc_galv"], [6.5, 6, 0, 9.5, 9, 6.5, "sc_galv"]], "Hoop Post", "METAL")
    ring = [[3.5, 0, 2, 12.5, 1, 3, "sc_rim"], [3.5, 0, 10, 12.5, 1, 11, "sc_rim"], [3.5, 0, 3, 4.5, 1, 10, "sc_rim"], [11.5, 0, 3, 12.5, 1, 10, "sc_rim"],
            [6, 0, 11, 10, 2, 12, "sc_rim"]]
    add("sc_hoop_board", [[0, 1, 12, 16, 15, 13.5, {"north": "sc_board_out", "side": "sc_galv"}], [6.5, 6, 13.5, 9.5, 9, 16, "sc_galv"]] + ring,
        "Basketball Hoop", "METAL", decor=True)
    net = [[4.5, 8, 3, 11.5, 16, 3.3, "sc_chain_net"], [4.5, 8, 9.7, 11.5, 16, 10, "sc_chain_net"], [4.5, 8, 3, 4.8, 16, 10, "sc_chain_net"],
           [11.2, 8, 3, 11.5, 16, 10, "sc_chain_net"]]
    add("sc_hoop_net", net, "Chain Net", "METAL", decor=True)
    # the goalposts: the padded foot, the post, the crossbar (axis) with its ends, the uprights and the wind ribbons
    add("sc_goal_pad", [[5, 0, 5, 11, 16, 11, "sc_goal_pad"]], "Goal Post", "WOOL", rotate=None)
    add("sc_goal_post", [[7, 0, 7, 9, 16, 9, yellow]], "Goal Post", "METAL", rotate=None)
    add("sc_goal_top", [[7, 0, 7, 9, 12, 9, yellow], [9, 8, 7.7, 14, 11.5, 8.3, red]], "Goal Post", "METAL", rotate=None)
    add("sc_goal_bar", [[7, 14, 0, 9, 16, 16, yellow]], "Goal Post", "METAL", rotate="axis")
    add("sc_goal_mid", [[7, 14, 0, 9, 16, 16, yellow], [7, 0, 7, 9, 14, 9, yellow], [6.5, 12, 6.5, 9.5, 14, 9.5, yellow]], "Goal Post", "METAL", rotate="axis")
    add("sc_goal_end", [[7, 14, 7, 9, 16, 16, yellow], [6.8, 13.8, 6.8, 9.2, 16, 9.2, yellow]], "Goal Post", "METAL")
    # the field's turf: mown bands, the lines, the mud
    for name in ("sc_turf", "sc_turf_d", "sc_turf_mud", "sc_turf_rough"):
        add(name, None, "Turf", "GRASS", rotate=None, tex={"up": name, "side": "sc_turf_side", "down": "sc_turf_side"})
    add("sc_turf_line", None, "Turf", "GRASS", rotate="axis", tex={"up": "sc_turf_line", "side": "sc_turf_side", "down": "sc_turf_side"})
    # aluminium bleachers out at the field
    plank = "sc_alu_plank"
    add("sc_bleacher_alu", [[0, 9, 8, 16, 11, 16, plank], [0, 3, 0, 16, 4.5, 8, plank], [1, 0, 0, 2, 9, 16, grey], [14, 0, 0, 15, 9, 16, grey],
                            [0, 4.5, 7.6, 16, 9, 8.4, grey]], "Bleachers", "METAL")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("sc_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} school blocks in {path}")


if __name__ == "__main__":
    main()
