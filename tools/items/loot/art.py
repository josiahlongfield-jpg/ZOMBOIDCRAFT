"""Painting helpers for loot items: printed labels with real (tiny) lettering and a picture, wrapped round cans and
bottles or printed on box faces, plus the materials packaging is made of. Lengths are inches (one model unit).
"""
import zlib

import numpy as np

import paint as P

# a 3x5 capital alphabet (4 wide for M, W and N), enough for the words on a label
FONT = {
    'A': '010101111101101', 'B': '110101110101110', 'C': '011100100100011', 'D': '110101101101110',
    'E': '111100110100111', 'F': '111100110100100', 'G': '011100101101011', 'H': '101101111101101',
    'I': '111010010010111', 'J': '001001001101010', 'K': '101101110101101', 'L': '100100100100111',
    'M': '10011111111110011001', 'N': '10011101101110011001', 'O': '010101101101010', 'P': '110101110100100',
    'Q': '010101101110011', 'R': '110101110101101', 'S': '011100010001110', 'T': '111010010010010',
    'U': '101101101101111', 'V': '101101101101010', 'W': '10011001111111111001', 'X': '101101010101101',
    'Y': '101101010010010', 'Z': '111001010100111', '0': '111101101101111', '1': '010110010010111',
    '2': '111001111100111', '3': '111001011001111', '4': '101101111001001', '5': '111100111001111',
    '6': '111100111101111', '7': '111001010010010', '8': '111101111101111', '9': '111101111001111',
    ' ': '000000000000000', '-': '000000111000000', '.': '000000000000010', '&': '010101010101011',
    '%': '101001010100101', '+': '000010111010000',
}


def glyph(ch):
    bits = FONT.get(ch, FONT[' '])
    w = len(bits) // 5
    return w, [bits[r * w:(r + 1) * w] for r in range(5)]


def text_mask(u, v, word, cu, cv, h):
    """Where the word's letters are, centred at (cu, cv), letters h tall (u right, v down, same units)."""
    px = h / 5.0
    widths = [glyph(c)[0] for c in word]
    total = (sum(widths) + len(word) - 1) * px
    gx = np.floor((u - (cu - total / 2)) / px).astype(int)
    gy = np.floor((v - (cv - h / 2)) / px).astype(int)
    ink = np.zeros(u.shape, bool)
    at = 0
    for ch, w in zip(word, widths):
        _w, rows = glyph(ch)
        for r, row in enumerate(rows):
            for c, bit in enumerate(row):
                if bit == '1':
                    ink |= (gx == at + c) & (gy == r)
        at += w + 1
    return ink


def fit_text(word, width, h):
    """The letter height that fits the word across width, at most h."""
    cols = sum(glyph(c)[0] for c in word) + len(word) - 1
    return min(h, width / max(cols, 1) * 5.0)


def col(c):
    return P.col(c) if isinstance(c, str) else np.asarray(c, float)


def tone(c, k):
    return np.clip(col(c) * k, 0, 1)


def hue(name, sat=0.55, val=0.62):
    """A muted, steady colour from a name (for labels nobody chose a colour for)."""
    h = (zlib.crc32(name.encode()) % 360) / 360.0
    i = int(h * 6)
    f = h * 6 - i
    p, q, t = val * (1 - sat), val * (1 - sat * f), val * (1 - sat * (1 - f))
    return np.array([(val, t, p), (q, val, p), (p, val, t), (p, q, val), (t, p, val), (val, p, q)][i % 6])


# ================================================================== pictures


def picture(kind, x, y, r, art, art2, f):
    """A small product picture centred at (x, y) = 0, radius about r, in label space. Returns (mask, colour)."""
    d = np.hypot(x, y)
    a2 = col(art2)
    if kind == 'heap':          # a bowl heaped with round pieces (beans, stew, peas)
        bowl = (np.abs(x) < r) & (y > -0.1 * r) & (y < 0.55 * r) & ((x / r) ** 2 + ((y + 0.1 * r) / (0.65 * r)) ** 2 < 1)
        pile = ((x / r) ** 2 + ((y + 0.05 * r) / (0.55 * r)) ** 2 < 1) & (y < 0.05 * r)
        bits = pile & (np.sin(x * 23 / r) * np.sin(y * 29 / r) > -0.1)
        c = np.where(bits[..., None], col(art), np.where(pile[..., None], a2, P.col('#ece6d8')))
        return bowl | pile, c
    if kind == 'fruit':         # one round fruit with a leaf
        body = d < r * 0.8
        leaf = ((x - 0.25 * r) ** 2 / (0.3 * r) ** 2 + (y + 0.85 * r) ** 2 / (0.14 * r) ** 2) < 1
        shine = (np.hypot(x + 0.3 * r, y + 0.3 * r) < 0.18 * r)
        c = np.where(leaf[..., None], P.col('#4e7a2f'), np.where(shine[..., None], np.minimum(col(art) * 1.6, 1), col(art)))
        return body | leaf, c
    if kind == 'fish':
        body = (x / r) ** 2 + (y / (0.45 * r)) ** 2 < 1
        tail = (x > 0.8 * r) & (x < 1.25 * r) & (np.abs(y) < (x - 0.8 * r) * 1.2 + 0.05 * r)
        eye = np.hypot(x + 0.6 * r, y + 0.1 * r) < 0.09 * r
        c = np.where(eye[..., None], P.col('#111111'), col(art))
        return body | tail | eye, c
    if kind == 'cross':         # medical red cross on white
        m = ((np.abs(x) < 0.25 * r) & (np.abs(y) < 0.75 * r)) | ((np.abs(y) < 0.25 * r) & (np.abs(x) < 0.75 * r))
        return m, col(art)
    if kind == 'drop':
        m = (np.hypot(x, y - 0.2 * r) < 0.55 * r) | ((np.abs(x) < (y + 0.9 * r) * 0.45) & (y < 0.2 * r) & (y > -0.9 * r))
        return m, col(art)
    if kind == 'star':
        ang = np.arctan2(y, x)
        m = d < r * (0.45 + 0.35 * np.abs(np.cos(ang * 2.5)))
        return m, col(art)
    if kind == 'sun':
        m = (d < 0.45 * r) | ((d < 0.9 * r) & (np.cos(np.arctan2(y, x) * 8) > 0.6))
        return m, col(art)
    if kind == 'cow':           # a cow's spots on white: milk
        m = (np.sin(x * 9 / r + 1) * np.sin(y * 7 / r) > 0.55) & (d < r)
        return m, col(art)
    if kind == 'pill':
        a = (np.abs(x) < 0.75 * r) & (np.abs(y) < 0.3 * r)
        cap = a & (x > 0)
        return a, np.where(cap[..., None], col(art), a2)
    if kind == 'wave':
        m = np.abs(y - 0.25 * r * np.sin(x * 4 / r)) < 0.18 * r
        return m & (np.abs(x) < r), col(art)
    if kind == 'bowl':
        bowl = ((x / r) ** 2 + ((y - 0.1 * r) / (0.55 * r)) ** 2 < 1) & (y > 0)
        flakes = ((x / (0.9 * r)) ** 2 + ((y - 0.05 * r) / (0.25 * r)) ** 2 < 1) & (f.noise(9, 102, 1) > 0.35)
        c = np.where(flakes[..., None], P.mix(col(art), a2, f.noise(8, 103, 1)), P.col('#e9e3d6'))
        return bowl | flakes, c
    if kind == 'leaf':
        m = ((x / (0.9 * r)) ** 2 + (y / (0.45 * r)) ** 2 < 1)
        vein = np.abs(y) < 0.05 * r
        return m, np.where(vein[..., None], a2, col(art))
    return np.zeros(x.shape, bool), col(art)


# ================================================================== labels


def label_colours(L, name):
    base = L.get('base') or hue(name)
    return dict(base=col(base), band=col(L.get('band', '#e9e2d0')), ink=col(L.get('ink', '#1c1a17')),
                word_ink=col(L.get('word_ink', L.get('ink', '#1c1a17'))), art=col(L.get('art', tone(base, 0.6))),
                art2=col(L.get('art2', '#e9e2d0')))


def design(f, u, v, W, H, L, cs):
    """Lays the label out on a flat sheet W wide and H tall (u right from the left edge, v down from the top): the
    base colour, a pale window with the picture, the word across a band, small print and a worn torn edge."""
    c = np.ones(u.shape + (3,)) * cs['base']
    style = L.get('style', 'window')
    word = L.get('word', '').upper()
    cu = W / 2
    if style == 'window':
        win = (np.abs(u - cu) < W * 0.38) & (v > H * 0.18) & (v < H * 0.86)
        c = np.where(win[..., None], cs['band'], c)
        th = fit_text(word, W * 0.7, H * 0.16)
        ink = text_mask(u, v, word, cu, H * 0.3, th) if word else np.zeros(u.shape, bool)
        c = np.where(ink[..., None], cs['word_ink'], c)
        r = min(W * 0.28, H * 0.22)
        m, pc = picture(L.get('pic', 'heap'), u - cu, v - H * 0.62, r, cs['art'], cs['art2'], f)
        c = np.where(m[..., None], pc, c)
    elif style == 'band':           # a plain field with one band across carrying the word
        band = (v > H * 0.32) & (v < H * 0.62)
        c = np.where(band[..., None], cs['band'], c)
        th = fit_text(word, W * 0.8, H * 0.2)
        ink = text_mask(u, v, word, cu, H * 0.47, th) if word else np.zeros(u.shape, bool)
        c = np.where(ink[..., None], cs['word_ink'], c)
        if L.get('pic'):
            r = min(W * 0.18, H * 0.13)
            m, pc = picture(L['pic'], u - cu, v - H * 0.8, r, cs['art'], cs['art2'], f)
            c = np.where(m[..., None], pc, c)
    elif style == 'stripes':        # bold diagonal stripes behind the word (snacks, sports drinks)
        st = ((u + v) / max(H * 0.25, 0.2)) % 1 < 0.35
        c = np.where(st[..., None], cs['art'], c)
        box = (np.abs(u - cu) < W * 0.42) & (np.abs(v - H * 0.45) < H * 0.17)
        c = np.where(box[..., None], cs['band'], c)
        th = fit_text(word, W * 0.75, H * 0.2)
        ink = text_mask(u, v, word, cu, H * 0.45, th) if word else np.zeros(u.shape, bool)
        c = np.where(ink[..., None], cs['word_ink'], c)
    elif style == 'rx':             # a white pharmacy label: a coloured header and lines of type
        c = np.ones(u.shape + (3,)) * cs['band']
        head = v < H * 0.28
        c = np.where(head[..., None], cs['base'], c)
        th = fit_text(word, W * 0.85, H * 0.18)
        ink = text_mask(u, v, word, cu, H * 0.14, th) if word else np.zeros(u.shape, bool)
        c = np.where(ink[..., None], cs['band'], c)
        lines = ((v > H * 0.4) & (((v - H * 0.4) / (H * 0.13)) % 1 < 0.38) & (u > W * 0.1) & (u < W * 0.9)
                 & (np.sin(u * 13) + np.sin(u * 5.3) > -0.7))
        c = np.where(lines[..., None], cs['ink'] * 0.8 + cs['band'] * 0.2, c)
    # small print down one side, a torn and stained edge
    fine = (u > W * 0.03) & (u < W * 0.1) & ((v * 6 / max(H, 1e-3)) % 1 < 0.4) & (v > H * 0.2) & (v < H * 0.85)
    if style != 'rx':
        c = np.where(fine[..., None], c * 0.6, c)
    stain = f.noise(1.3, 73, 3) > 0.76
    c = np.where(stain[..., None], P.mix(c, P.col('#7a6a48'), 0.3), c)
    return c


def wrap(L, name, under=None):
    """A paper or printed sleeve round a round part (built by Model.rod about the y axis at x = z = 0). The label
    is one sheet as long as the circumference; its centre faces the front (+z)."""
    cs = label_colours(L, name)

    def fn(f):
        side = abs(f.n[1]) < 0.5
        if not side:
            return P.plain(f, cs['base'], 0.06) if under is None else under(f)
        lo, hi = f.box['lo'][1], f.box['hi'][1]
        r = max(f.box['hi'][0], f.box['hi'][2])
        circ = 2 * np.pi * r
        a = (np.arctan2(f.Z, f.X) / (2 * np.pi) - 0.25 + 0.5) % 1.0      # 0.5 at the front
        u = a * circ
        v = (hi - f.Y)
        H = hi - lo
        c = design(f, u - circ * 0.25, v, circ * 0.5, H, L, cs)
        # the back half of the sleeve: plain base with lines of type
        back = (a < 0.25) | (a > 0.75)
        bc = np.ones(c.shape) * cs['base']
        lines = (((v / max(H, 1e-3)) * 7) % 1 < 0.3) & (v > H * 0.25) & (v < H * 0.8) & (np.sin(u * 11) > -0.5)
        bc = np.where(lines[..., None], bc * 0.55, bc)
        c = np.where(back[..., None], bc, c)
        if under is not None:       # torn: the can shows through where the paper's gone
            torn = (f.noise(0.9, 74, 3) > 0.8) & ((v < H * 0.18) | (v > H * 0.82))
            c = np.where(torn[..., None], under(f), c)
        return c
    return fn


def printed_box(L, name, front=(2, 1)):
    """Print on a box: the full design on the front (+z) and back, a simple version on the sides, plain ends."""
    cs = label_colours(L, name)

    def fn(f):
        base = P.plain(f, cs['base'], 0.05)
        lo, hi = f.box['lo'], f.box['hi']
        ax = int(np.argmax(np.abs(f.n)))
        if ax == 1:                                   # top and bottom: plain, a flap seam
            return base
        uax = 0 if ax == 2 else 2
        W = hi[uax] - lo[uax]
        H = hi[1] - lo[1]
        u = (f.P[..., uax] - lo[uax]) if (ax == 2 and f.n[2] > 0) or (ax == 0 and f.n[0] < 0) else (hi[uax] - f.P[..., uax])
        v = hi[1] - f.Y
        if ax == 2:
            return design(f, u, v, W, H, L, cs)
        side = dict(L, style='band', pic=None)
        return design(f, u, v, W, H, side, cs)
    return fn


# ================================================================== materials


def tin(f):
    c = P.steel(f, 0.68, tint='#b9c0c8', spread=0.1)
    beads = (np.sin(f.Y * 9.0) > 0.55) & (abs(f.n[1]) < 0.5)
    return np.where(beads[..., None], c * 0.85, c)


def alu(f):
    return P.steel(f, 0.72, tint='#c4c8cc', spread=0.08)


def plastic(colour, stipple=0.05):
    def fn(f):
        return P.polymer(f, colour, stipple)
    return fn


def flat(colour, noise=0.06):
    def fn(f):
        return P.plain(f, colour, noise)
    return fn


def glass(tint, liquid=None, level=1.0):
    """Bottle glass: its tint, a bright streak where it catches the light, the liquid darker below its line."""
    def fn(f):
        c = P.plain(f, tint, 0.05)
        lo, hi = f.box['lo'][1], f.box['hi'][1]
        if liquid is not None:
            full = f.Y < lo + (hi - lo) * level
            c = np.where(full[..., None], P.mix(c, col(liquid), 0.7), c)
        u = f.uv[..., 0] / max(float(f.uv[..., 0].max()), 1e-6)
        glint = (np.abs(u - 0.25) < 0.07) & (abs(f.n[1]) < 0.5)
        return np.where(glint[..., None], np.minimum(c * 1.7 + 0.12, 1), c)
    return fn


def cardboard(f):
    c = P.plain(f, '#a98b62', 0.1)
    flutes = (np.sin(f.uv[..., 0] * 40) > 0.7)
    return np.where(flutes[..., None], c * 0.94, c)


def paper(colour='#d9cfb4'):
    def fn(f):
        c = P.plain(f, colour, 0.08)
        crease = np.abs(np.sin(f.uv[..., 0] * 3.1 + f.uv[..., 1] * 1.7)) < 0.04
        return np.where(crease[..., None], c * 0.85, c)
    return fn


def foil(colour):
    """Crinkled foil or a printed plastic packet: bright creases."""
    def fn(f):
        c = P.plain(f, colour, 0.08)
        n = f.noise(3.5, 131, 2)
        return c * (0.85 + 0.35 * np.abs(n - 0.5) * 2)[..., None]
    return fn


def meat(colour='#9b3a33', fat='#e2c9b6'):
    def fn(f):
        c = P.plain(f, colour, 0.1)
        marb = f.noise(2.5, 141, 3) > 0.62
        return np.where(marb[..., None], col(fat), c)
    return fn


def skin(colour, spots=None, shine=0.15):
    """Fruit and veg skin: its colour with mottling, freckles if given."""
    def fn(f):
        c = P.plain(f, colour, 0.14)
        c = P.mix(c, tone(colour, 1.25), np.clip(f.noise(1.1, 151, 3) - 0.4, 0, 1) * shine * 3)
        if spots:
            sp = P.white(f.shape, f.rng) > 0.93
            c = np.where(sp[..., None], col(spots), c)
        return c
    return fn


def fabric(colour):
    def fn(f):
        c = P.plain(f, colour, 0.12)
        u, v = f.uv[..., 0], f.uv[..., 1]
        weave = ((np.floor(u * 10) + np.floor(v * 10)) % 2) * 2 - 1
        return c * (1 + weave * 0.04)[..., None]
    return fn


def chrome(f):
    return P.chrome(f)


def steel(f):
    return P.steel(f, 0.6, spread=0.12)
