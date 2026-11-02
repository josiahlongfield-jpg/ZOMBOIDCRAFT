"""Wave 5 decor models, batch 5: music and media (wave 4 stage 5; specs in decor.py). Built from the photos in
refs/items/decor/<id>/ (electric_guitar_and_amp holds guitar_amp's, projector_and_screen holds projector_screen's,
reel_to_reel_deck holds reel_to_reel's).

Every band, label and maker here is made up. Sizes run at about 0.4 units an inch like the furniture.
"""
import numpy as np

import art as A
import paint as P
from wave4 import blob, flat
from wave5games import face_paint, put, text
from wave5wall import BANDS, motif

# ================================================================== painters


def sleeve_face(k):
    """An album sleeve: the band's colours and graphic, the title across the top."""
    band, line, fld, acc, ink, g = BANDS[k % len(BANDS)]

    def draw(u, v, W, H, c, f):
        c = motif(c, u, v, g, W / 2, H * 0.58, W * 0.3, acc)
        c = put(c, v < H * 0.2, acc)
        return text(c, u, v, band, W / 2, H * 0.1, A.fit_text(band, W * 0.86, H * 0.11), ink if acc != ink else fld)
    return face_paint(draw, fld, ('front', 'back'), 0.05)


def record_face(label, faces=('front', 'back')):
    """A black record: the fine grooves catching the light in a bow tie, the coloured centre label, the hole."""
    def draw(u, v, W, H, c, f):
        x, y = u - W / 2, v - H / 2
        R = min(W, H) / 2
        r = np.hypot(x, y) / R
        a = np.arctan2(y, x)
        c = c * (0.8 + 0.12 * np.cos(r * 160) + 0.5 * np.cos(a * 2 + 0.6) ** 12 * (r > 0.36))[..., None]
        c = put(c, r < 0.34, label)
        c = put(c, (np.abs(r - 0.2) < 0.015), '#141414')
        return put(c, r < 0.04, '#0a0a0a')
    return face_paint(draw, '#121212', faces, 0.01)


def grille(colour, line, kind='cloth'):
    """Speaker grille cloth: a fine weave, or slats, or the punched metal of a hi-fi speaker."""
    def draw(u, v, W, H, c, f):
        if kind == 'slats':
            return put(c, (u / W * 9) % 1 < 0.25, line)
        if kind == 'holes':
            return put(c, np.hypot((u % 0.5) - 0.25, (v % 0.5) - 0.25) < 0.12, line)
        return c * (0.88 + 0.12 * (((u * 6) % 1 < 0.5) ^ ((v * 6) % 1 < 0.5)))[..., None]
    return face_paint(draw, colour, ('front',), 0.04)


def keys(n_white, black='#141414', white='#f0ece0', faces=('top', 'front')):
    """Piano keys: n white keys with the black ones in twos and threes."""
    def draw(u, v, W, H, c, f):
        kw = W / n_white
        q = u / kw
        c = put(c, np.ones(u.shape, bool), white)
        c = put(c, (q % 1) < 0.08, '#8a8680')
        i = np.floor(q).astype(int) % 7
        bk = ((q % 1) > 0.7) & np.isin(i, (0, 1, 3, 4, 5)) & (v < H * 0.6)
        bk |= ((q % 1) < 0.3) & np.isin(i, (1, 2, 4, 5, 6)) & (v < H * 0.6)
        return put(c, bk, black)
    return face_paint(draw, white, faces, 0.0)


def panel(kind, face, ink, glow='#40e080'):
    """A hi-fi front: a cassette window pair, a tuner dial, an amp's meters and knobs, a CD tray."""
    def draw(u, v, W, H, c, f):
        if kind == 'tape':
            for x0 in (0.08, 0.55):
                c = put(c, (u > W * x0) & (u < W * (x0 + 0.37)) & (v > H * 0.15) & (v < H * 0.75), '#1a1a1a')
                for s in (0.12, 0.25):
                    c = put(c, np.hypot(u - W * (x0 + s), v - H * 0.45) < H * 0.12, '#5a5a5a')
        elif kind == 'tuner':
            c = put(c, (u > W * 0.06) & (u < W * 0.7) & (v > H * 0.2) & (v < H * 0.7), '#0a1a1a')
            c = put(c, (u > W * 0.06) & (u < W * 0.7) & (np.abs(v - H * 0.45) < H * 0.03), glow)
            c = put(c, ((u / W * 30) % 1 < 0.15) & (u > W * 0.08) & (u < W * 0.68) & (np.abs(v - H * 0.3) < H * 0.05),
                    ink)
        elif kind == 'amp':
            for x0 in (0.08, 0.3):
                c = put(c, (u > W * x0) & (u < W * (x0 + 0.18)) & (v > H * 0.15) & (v < H * 0.6), '#e8dcb0')
                c = put(c, np.abs((u - W * (x0 + 0.09)) - (v - H * 0.5) * 0.5) < W * 0.004, '#141414')
        elif kind == 'cd':
            c = put(c, (u > W * 0.06) & (u < W * 0.5) & (np.abs(v - H * 0.3) < H * 0.08), '#1a1a1a')
            c = put(c, (u > W * 0.6) & (u < W * 0.9) & (v > H * 0.2) & (v < H * 0.55), '#0a1a1a')
            c = text(c, u, v, '12 0345', W * 0.75, H * 0.37, H * 0.14, glow)
        for x in (0.8, 0.9) if kind != 'cd' else ():
            c = put(c, np.hypot(u - W * x, v - H * 0.45) < H * 0.16, ink)
        return put(c, (u / W * 24 % 1 < 0.4) & (np.abs(v - H * 0.86) < H * 0.04) & (u > W * 0.06) & (u < W * 0.5),
                   ink)
    return face_paint(draw, face, ('front',), 0.02)


def drum_head(word, ink, ring):
    """The bass drum's front head: the white skin, the port hole and the band's name."""
    def draw(u, v, W, H, c, f):
        x, y = u - W / 2, v - H / 2
        R = min(W, H) / 2
        r = np.hypot(x, y) / R
        c = put(c, np.abs(r - 0.62) < 0.05, ring)
        c = text(c, u, v, word, W / 2, H / 2, A.fit_text(word, W * 0.6, H * 0.16), ink)
        return put(c, np.hypot(x - R * 0.45, y - R * 0.45) / R < 0.14, '#1a1a1a')
    return face_paint(draw, '#efe9dc', ('front',), 0.02)


def screen_face(on_colour):
    """A picture tube: the rounded glass, darker to the corners."""
    def draw(u, v, W, H, c, f):
        x, y = (u / W - 0.5) * 2, (v / H - 0.5) * 2
        e = (np.abs(x) ** 4 + np.abs(y) ** 4)
        c = c * (1.05 - 0.5 * e)[..., None]
        return put(c, e > 0.9, '#141414')
    return face_paint(draw, on_colour, ('front',), 0.03)


def reel_face(tape):
    """A tape reel: the metal flange with its three windows and the brown tape wound round the hub."""
    def draw(u, v, W, H, c, f):
        x, y = u - W / 2, v - H / 2
        R = min(W, H) / 2
        r = np.hypot(x, y) / R
        a = np.arctan2(y, x)
        c = put(c, (r < 0.7) & (r > 0.2), tape)
        win = (r > 0.3) & (r < 0.85) & (np.cos(a * 3) > 0.35)
        c = put(c, win & ~((r < 0.7) & (r > 0.2)), '#141414')
        return put(c, r < 0.12, '#2a2a2a')
    return face_paint(draw, '#c8ccd0', ('front', 'back'), 0.02)


def screen_cloth(f):
    return A.plain(f, '#f4f2ea', 0.02)


# ================================================================== the pieces


def vinyl_record(m, k=0):
    """A record standing in its sleeve on a little wire rack, the disc half drawn out to show its label."""
    s = 5.0
    m.box([-s / 2, 0.6, -0.12], [s / 2, 0.6 + s, 0.12], 'sleeve')
    m.rod(-0.04, 0.04, 2.3, 'disc', cx=1.4, cz=0.6 + s / 2 + 0.3, axis=2)
    for x in (-1.6, 1.6):
        m.box([x - 0.08, 0.0, -0.8], [x + 0.08, 0.6, 0.8], 'wire')
        m.box([x - 0.08, 0.6, -0.5], [x + 0.08, 2.0, -0.35], 'wire')
        m.box([x - 0.08, 0.6, 0.35], [x + 0.08, 2.0, 0.5], 'wire')


def record_player(m, k=0):
    """A suitcase record player: the case with its handle and catches, the platter and record, the tonearm on
    its rest, the knobs along the front and the open lid behind with the speaker in it."""
    w, h, d = 7.0, 2.4, 6.0
    m.box([-w / 2, 0.0, -d / 2], [w / 2, h, d / 2], 'case')
    m.box([-w / 2 - 0.05, h - 0.1, -d / 2 - 0.05], [w / 2 + 0.05, h + 0.05, d / 2 + 0.05], 'trim')
    m.box([-w / 2 + 0.3, h, -d / 2 + 0.3], [w / 2 - 0.3, h + 0.1, d / 2 - 0.3], 'deck')
    m.rod(h + 0.1, h + 0.35, 2.3, 'platter', cx=-0.6, cz=0.2)
    m.rod(h + 0.35, h + 0.4, 2.2, 'disc', cx=-0.6, cz=0.2)
    m.rod(h + 0.1, h + 0.7, 0.25, 'metal', cx=2.4, cz=-1.6)
    flat(m, (2.4, -1.6), (0.9, 1.2), 0.14, 0.14, 'metal', y0=h + 0.53)
    m.box([0.7, h + 0.4, 1.0], [1.1, h + 0.6, 1.5], 'dark')
    for x in (1.8, 2.5):
        m.rod(h + 0.1, h + 0.4, 0.22, 'dark', cx=x, cz=2.2)
    m.box([-w / 2, h, -d / 2 - 0.3], [w / 2, h + 5.4, -d / 2], 'case')         # the lid, standing open
    m.box([-w / 2 + 0.4, h + 0.6, -d / 2 + 0.0], [w / 2 - 0.4, h + 5.0, -d / 2 + 0.05], 'cloth')
    m.box([-1.0, 1.0, d / 2], [1.0, 1.3, d / 2 + 0.4], 'trim')                 # handle
    for x in (-2.6, 2.6):
        m.box([x - 0.3, 1.6, d / 2], [x + 0.3, 2.0, d / 2 + 0.1], 'metal')


def karaoke_machine(m, k=0):
    """A home karaoke machine as in the photo: the speaker cabinet with its round grille, the top deck with the
    cassette door, the buttons and the lit level meter, the microphone on its curled cord."""
    w, h, d = 6.4, 7.0, 4.6
    m.box([-w / 2, 0.0, -d / 2], [w / 2, h, d / 2], 'cab')
    m.box([-w / 2 + 0.4, 0.6, d / 2], [w / 2 - 0.4, 4.6, d / 2 + 0.05], 'cloth')
    m.box([-w / 2, h, -d / 2], [w / 2, h + 1.4, d / 2 - 0.6], 'cab')
    m.box([-w / 2 + 0.3, 5.0, d / 2], [w / 2 - 0.3, h, d / 2 + 0.05], 'front')
    m.box([-w / 2 + 0.4, 5.6, d / 2 + 0.05], [-0.2, 6.6, d / 2 + 0.1], 'glow')
    m.rod(-0.2, 0.0, 0.25, 'dark', cx=1.6, cz=6.1, axis=2)
    m.rod(-0.2, 0.0, 0.25, 'dark', cx=2.4, cz=6.1, axis=2)
    m.box([-1.4, h + 1.4, -1.6], [1.4, h + 1.6, 0.6], 'dark')                  # carry handle
    flat(m, (1.8, d / 2 - 0.3), (-1.0, d / 2 - 0.3), 0.5, 0.5, 'mic', y0=h)
    blob(m, (-1.4, h + 0.3, d / 2 - 0.3), 0.45, 'metal')
    m.seg((2.6, h + 0.2), (3.4, 2.0), 0.15, 0.15, 'dark', z=d / 2 - 0.3)


def big_crt_tv(m, k=0):
    """A console television like the photos: the wooden cabinet on short legs, the big rounded picture tube in
    its mask, the dials beside it and the long speaker grille underneath."""
    w, h, d = 12.0, 16.0, 8.0
    m.box([-w / 2, 1.2, -d / 2], [w / 2, 1.2 + h, d / 2], 'cab')
    m.box([-w / 2 - 0.2, 1.2 + h, -d / 2 - 0.2], [w / 2 + 0.2, 1.6 + h, d / 2 + 0.2], 'cab')
    for x in (-w / 2 + 0.6, w / 2 - 0.6):
        for z in (-d / 2 + 0.6, d / 2 - 0.6):
            m.rod(0.0, 1.2, 0.4, 'cab', cx=x, cz=z)
    m.box([-w / 2 + 0.6, 8.0, d / 2], [w / 2 - 0.6, 16.4, d / 2 + 0.3], 'mask')
    m.box([-4.6, 8.6, d / 2 + 0.3], [2.8, 15.8, d / 2 + 0.6], 'glow')
    for y in (14.6, 12.6, 10.6):
        m.rod(d / 2 + 0.3, d / 2 + 0.8, 0.5, 'knob', cx=4.2, cz=y, axis=2)
    m.box([-w / 2 + 0.8, 2.0, d / 2], [w / 2 - 0.8, 7.2, d / 2 + 0.15], 'cloth')
    m.box([-w / 2 + 0.6, 7.4, d / 2], [w / 2 - 0.6, 7.7, d / 2 + 0.25], 'trim')


def accordion(m, k=0):
    """A piano accordion standing on end: the treble side with its keys, the pleated bellows with the leather
    corners, the bass side with rows of buttons, the straps."""
    h, d = 8.0, 3.4
    m.box([1.4, 0.0, -d / 2], [4.4, h, d / 2], 'shell')
    m.box([3.0, 0.4, d / 2], [4.6, h - 0.4, d / 2 + 0.5], 'keyboard')
    for q in range(9):                                                  # the bellows pleats
        x = -1.6 + q * 0.34
        m.box([x, 0.2 if q % 2 else 0.0, -d / 2 + (0.2 if q % 2 else 0.0)],
              [x + 0.34, h - (0.2 if q % 2 else 0.0), d / 2 - (0.2 if q % 2 else 0.0)], 'bellows' if q % 2 else 'pleat')
    m.box([-4.4, 0.0, -d / 2], [-1.6, h, d / 2], 'shell')
    m.box([-4.4, 1.0, d / 2], [-2.0, h - 1.0, d / 2 + 0.1], 'buttons')
    m.box([-4.4, h - 0.4, -d / 2], [4.4, h, -d / 2 + 0.3], 'grill')
    for x in (-3.0, 2.8):
        m.box([x - 0.3, h, -1.0], [x + 0.3, h + 0.4, 1.0], 'strap')


def stereo_system(m, k=0):
    """A hi-fi tower: the rack with its glass door and the components stacked inside (tape deck, tuner, amplifier,
    CD player, the turntable on top), and the two tall speakers either side."""
    w, d = 7.6, 6.0
    m.box([-w / 2, 0.0, -d / 2], [w / 2, 0.8, d / 2], 'rack')
    for x in (-w / 2, w / 2 - 0.4):
        m.box([x, 0.0, -d / 2], [x + 0.4, 15.0, d / 2], 'rack')
    m.box([-w / 2, 0.8, -d / 2], [w / 2, 15.0, -d / 2 + 0.3], 'rack')
    y = 0.8
    for kind, hh in (('amp', 3.0), ('tuner', 2.4), ('tape', 2.8), ('cd', 2.0)):
        m.box([-w / 2 + 0.5, y + 0.1, -d / 2 + 0.5], [w / 2 - 0.5, y + hh, d / 2 - 0.4], 'unit')
        m.box([-w / 2 + 0.5, y + 0.1, d / 2 - 0.4], [w / 2 - 0.5, y + hh, d / 2 - 0.3], f'p_{kind}')
        y += hh + 0.2
    m.box([-w / 2, 15.0, -d / 2], [w / 2, 15.4, d / 2], 'rack')
    m.box([-w / 2 + 0.3, 15.4, -d / 2 + 0.3], [w / 2 - 0.3, 16.4, d / 2 - 0.3], 'unit')
    m.rod(16.4, 16.6, 2.4, 'disc', cx=-0.6, cz=0.0)
    for s in (-1, 1):
        x = s * (w / 2 + 3.2)
        m.box([x - 2.6, 0.0, -d / 2], [x + 2.6, 17.0, d / 2], 'spk')
        m.box([x - 2.4, 0.4, d / 2], [x + 2.4, 16.6, d / 2 + 0.1], 'cloth')


def guitar_amp(m, k=0):
    """A combo amplifier as in the photos, the tolex box with its grille cloth, top handle and control panel, and an
    electric guitar leaning on it: the body, the pickups and bridge, the neck and headstock with its tuners."""
    w, h, d = 9.0, 8.0, 4.4
    m.box([-w / 2, 0.0, -d / 2], [w / 2, h, d / 2], 'tolex')
    m.box([-w / 2 + 0.4, 0.4, d / 2], [w / 2 - 0.4, h - 1.6, d / 2 + 0.05], 'cloth')
    m.box([-w / 2 + 0.4, h - 1.4, d / 2 - 0.2], [w / 2 - 0.4, h - 0.3, d / 2 + 0.05], 'chrome')
    for q in range(6):
        m.rod(d / 2 + 0.05, d / 2 + 0.35, 0.22, 'dark', cx=-3.4 + q * 1.2, cz=h - 0.85, axis=2)
    m.box([-1.6, h, -0.4], [1.6, h + 0.3, 0.4], 'dark')
    for x in (-w / 2 + 0.3, w / 2 - 0.3):                                 # the corner caps
        for y in (0.3, h - 0.3):
            blob(m, (x, y, d / 2 - 0.3), 0.32, 'chrome')
    gz = d / 2 + 1.2                                                     # the guitar, leaning
    blob(m, (2.4, 2.8, gz), (2.6, 2.4, 0.7), 'guitar')
    blob(m, (2.1, 4.8, gz), (2.0, 1.6, 0.7), 'guitar')
    m.box([1.6, 2.6, gz + 0.6], [3.0, 3.0, gz + 0.8], 'chrome')
    m.box([1.6, 3.6, gz + 0.6], [3.0, 4.0, gz + 0.8], 'dark')
    m.seg((2.0, 5.6), (0.6, 14.0), 0.8, 0.5, 'neck', z=gz)
    m.seg((0.6, 14.0), (0.4, 16.2), 1.1, 0.4, 'guitar', z=gz)
    m.seg((2.4, 1.6), (0.8, 13.0), 0.06, 0.06, 'chrome', z=gz + 0.32)


def drum_kit(m, k=0):
    """A five-piece kit like the photos: the bass drum with the band's name on its head, the two rack toms on it,
    the floor tom on its legs, the snare on its stand, the hi-hat, the crash and ride cymbals and the stool."""
    m.rod(-3.4, 3.4, 4.6, 'shell', cx=0.0, cz=4.6, axis=2)
    m.box([-4.0, 0.6, 3.4], [4.0, 8.6, 3.5], 'head')
    for x in (-3.0, 3.0):
        m.box([x - 0.12, 0.0, 2.8], [x + 0.12, 2.0, 3.4], 'chrome')
    for x in (-2.6, 2.6):                                               # the rack toms
        m.rod(9.4, 12.2, 2.4, 'shell', cx=x, cz=1.0)
        m.rod(12.2, 12.3, 2.3, 'skin', cx=x, cz=1.0)
    m.rod(9.2, 9.6, 0.25, 'chrome', cx=0.0, cz=1.0)
    m.rod(0.0, 9.4, 0.25, 'chrome', cx=0.0, cz=-1.0)
    m.rod(3.0, 9.0, 3.4, 'shell', cx=9.0, cz=1.0)                       # floor tom
    m.rod(9.0, 9.1, 3.3, 'skin', cx=9.0, cz=1.0)
    for dx in (-2.8, 2.8):
        m.rod(0.0, 3.0, 0.18, 'chrome', cx=9.0 + dx, cz=1.0)
    m.rod(7.8, 10.2, 2.8, 'shell', cx=-6.4, cz=3.4)                     # snare
    m.rod(10.2, 10.3, 2.7, 'skin', cx=-6.4, cz=3.4)
    m.rod(0.0, 7.8, 0.2, 'chrome', cx=-6.4, cz=3.4)
    for x, z, y, r in ((-10.4, 1.0, 13.0, 2.8), (-6.0, -3.6, 19.0, 3.6), (7.0, -3.6, 17.0, 4.2)):
        m.rod(0.0, y, 0.18, 'chrome', cx=x, cz=z)
        m.rod(y, y + 0.12, r, 'cymbal', cx=x, cz=z)
        blob(m, (x, y + 0.2, z), 0.4, 'cymbal')
    m.rod(12.6, 12.72, 2.8, 'cymbal', cx=-10.4, cz=1.0)
    m.rod(0.0, 6.6, 0.3, 'chrome', cx=0.0, cz=-7.0)                     # the stool
    m.rod(6.6, 7.6, 2.4, 'seat', cx=0.0, cz=-7.0)
    for dx in (-2.0, 2.0):
        m.box([dx - 0.15, 0.0, -9.0], [dx + 0.15, 0.3, -5.0], 'chrome')


def dj_turntables(m, k=0):
    """Two direct-drive turntables and the mixer between them on a stand, as in the photo: the plinths, the
    platters with their strobe dots and records, the tonearms, the pitch sliders and the mixer's faders."""
    m.box([-10.0, 0.0, -3.0], [-9.4, 13.0, 3.0], 'stand')
    m.box([9.4, 0.0, -3.0], [10.0, 13.0, 3.0], 'stand')
    m.box([-10.0, 13.0, -3.4], [10.0, 13.6, 3.4], 'stand')
    for x in (-10.0, 9.4):
        m.box([x, 0.0, -3.4], [x + 0.6, 0.6, 3.4], 'stand')
    for s in (-1, 1):
        cx = s * 5.6
        m.box([cx - 3.6, 13.6, -2.9], [cx + 3.6, 14.8, 2.9], 'plinth')
        m.rod(14.8, 15.2, 2.5, 'platter', cx=cx - 0.6, cz=0.0)
        m.rod(15.2, 15.26, 2.4, 'disc', cx=cx - 0.6, cz=0.0)
        m.rod(14.8, 15.6, 0.3, 'chrome', cx=cx + 2.6, cz=-2.0)
        flat(m, (cx + 2.6, -2.0), (cx + 1.6, 1.2), 0.15, 0.15, 'chrome', y0=15.45)
        m.box([cx + 2.4, 14.8, 0.4], [cx + 3.2, 14.9, 2.4], 'dark')
        m.rod(14.8, 15.0, 0.3, 'dark', cx=cx - 3.0, cz=2.4)
    m.box([-2.0, 13.6, -2.9], [2.0, 15.0, 2.9], 'mixer')
    for x in (-1.2, 0.0, 1.2):
        m.box([x - 0.12, 15.0, 0.4], [x + 0.12, 15.2, 2.0], 'dark')
        for z in (-2.2, -1.4, -0.6):
            m.rod(15.0, 15.2, 0.18, 'chrome', cx=x, cz=z)
    m.box([-0.8, 15.0, 2.2], [0.8, 15.2, 2.6], 'glow')


def projector_screen(m, k=0):
    """A tripod screen pulled up on its post with a film projector on a little cart in front of it: the reels on
    their arms, the lens with its glow, the cart's two shelves and casters."""
    m.rod(0.0, 26.0, 0.3, 'metal', cx=0.0, cz=-4.0)
    for a in (90, 210, 330):
        r = np.radians(a)
        m.seg((0.0, 6.0), (np.cos(r) * 4.0, 0.0), 0.3, 0.3, 'metal', z=-4.0 + np.sin(r) * 0.0)
    m.box([-11.0, 9.0, -4.4], [11.0, 9.8, -3.6], 'case')
    m.box([-10.6, 9.8, -4.1], [10.6, 26.0, -4.0], 'screen')
    m.box([-0.6, 25.8, -4.4], [0.6, 26.6, -3.6], 'metal')
    for x in (-3.0, 3.0):                                               # the cart
        for z in (2.0, 7.0):
            m.rod(0.6, 9.0, 0.2, 'metal', cx=x, cz=z)
            blob(m, (x, 0.3, z), 0.3, 'dark')
    for y in (3.0, 9.0):
        m.box([-3.4, y, 1.6], [3.4, y + 0.3, 7.4], 'shelf')
    m.box([-2.0, 9.3, 2.4], [2.0, 12.0, 6.6], 'projector')
    m.rod(1.4, 3.0, 0.7, 'metal', cx=0.0, cz=10.6, axis=2)
    m.rod(1.2, 1.4, 0.6, 'glow', cx=0.0, cz=10.6, axis=2)
    m.seg((0.0, 12.0), (0.0, 14.6), 0.3, 0.3, 'metal', z=5.8)
    m.seg((0.0, 12.0), (0.0, 14.0), 0.3, 0.3, 'metal', z=3.0)
    m.rod(-0.2, 0.2, 2.2, 'reel', cx=14.6, cz=5.8, axis=0)
    m.rod(-0.2, 0.2, 1.8, 'reel', cx=14.0, cz=3.0, axis=0)


def jukebox(m, k=0):
    """A diner jukebox: the cabinet with the arched top, the lit coloured tubes up both sides and round the arch,
    the record window with its selection strips, the buttons, the coin slot and the speaker grille below."""
    w, d = 13.0, 9.0
    m.box([-w / 2, 0.0, -d / 2], [w / 2, 16.0, d / 2], 'cab')
    m.arc((0.0, 16.0), w / 2 - 0.6, 0, 180, 1.4, d, 'cab', z=0.0)
    m.box([-w / 2 + 1.0, 16.0, -d / 2], [w / 2 - 1.0, 18.6, d / 2], 'cab')
    m.box([-4.8, 18.6, -d / 2], [4.8, 20.6, d / 2], 'cab')
    m.box([-2.8, 20.6, -d / 2], [2.8, 21.8, d / 2], 'cab')
    m.arc((0.0, 16.0), w / 2 - 0.2, 0, 180, 0.6, 0.6, 'glow', z=d / 2 + 0.1)
    for x in (-w / 2 + 0.4, w / 2 - 0.4):
        m.rod(1.0, 16.0, 0.4, 'glow2', cx=x, cz=d / 2 - 0.2)
    m.box([-4.6, 11.0, d / 2], [4.6, 18.0, d / 2 + 0.3], 'chrome')
    m.box([-4.2, 11.4, d / 2 + 0.3], [4.2, 17.6, d / 2 + 0.35], 'inside')
    m.rod(d / 2 + 0.35, d / 2 + 0.45, 2.6, 'disc', cx=0.0, cz=14.6, axis=2)
    m.box([-4.6, 9.0, d / 2], [4.6, 10.6, d / 2 + 0.6], 'strips')
    m.box([-4.6, 8.4, d / 2], [4.6, 9.0, d / 2 + 0.4], 'chrome')
    m.box([3.6, 7.4, d / 2], [4.4, 8.2, d / 2 + 0.2], 'chrome')
    m.box([-5.0, 1.0, d / 2], [5.0, 7.0, d / 2 + 0.2], 'cloth')
    m.box([-w / 2, 0.0, d / 2], [w / 2, 0.8, d / 2 + 0.3], 'chrome')


def upright_piano(m, k=0):
    """An upright piano as in the photos: the tall case with its lid and music desk, the keyboard on the keybed
    with the cheek blocks, the front legs on their toe blocks, the lower panel and the brass pedals."""
    w, h, d = 23.0, 20.0, 9.0
    m.box([-w / 2, 0.6, -d / 2], [w / 2, h, -d / 2 + 5.0], 'case')
    m.box([-w / 2 - 0.2, h, -d / 2 - 0.2], [w / 2 + 0.2, h + 0.5, -d / 2 + 5.2], 'case')
    m.box([-w / 2 + 1.2, 11.8, -d / 2 + 5.0], [w / 2 - 1.2, h - 0.6, -d / 2 + 5.3], 'panel')
    m.box([-w / 2, 8.6, -d / 2 + 5.0], [w / 2, 9.6, d / 2], 'case')           # keybed
    m.box([-w / 2 + 1.2, 9.6, -d / 2 + 5.4], [w / 2 - 1.2, 10.0, d / 2 - 0.4], 'keys')
    for x in (-w / 2, w / 2 - 1.2):
        m.box([x, 9.6, -d / 2 + 5.0], [x + 1.2, 11.0, d / 2], 'case')
    m.box([-w / 2 + 1.2, 10.0, -d / 2 + 5.0], [w / 2 - 1.2, 11.8, -d / 2 + 5.6], 'case')
    m.box([-w / 2 + 1.4, 1.2, -d / 2 + 5.0], [w / 2 - 1.4, 8.6, -d / 2 + 5.2], 'panel')
    for x in (-w / 2 + 0.6, w / 2 - 0.6):
        m.rod(0.6, 8.6, 0.5, 'case', cx=x, cz=d / 2 - 0.6)
        m.box([x - 0.6, 0.0, -d / 2], [x + 0.6, 0.6, d / 2], 'case')
    for x in (-1.0, 0.0, 1.0):
        m.box([x - 0.25, 0.8, -d / 2 + 5.2], [x + 0.25, 1.0, -d / 2 + 6.6], 'brass')


def gramophone(m, k=0):
    """A horn gramophone: the square wooden case with its crank, the turntable and record, the back bracket and
    the tone arm rising into the big flaring horn."""
    m.box([-3.0, 0.0, -3.0], [3.0, 3.0, 3.0], 'case')
    m.box([-3.2, 3.0, -3.2], [3.2, 3.3, 3.2], 'case')
    m.box([-2.8, 0.4, 3.0], [2.8, 2.6, 3.1], 'panel')
    m.rod(3.3, 3.5, 2.6, 'felt', cx=0.0, cz=0.0)
    m.rod(3.5, 3.55, 2.5, 'disc', cx=0.0, cz=0.0)
    m.rod(3.0, 3.8, 0.2, 'brass', cx=1.6, cz=0.6, axis=0)
    m.rod(1.6, 2.6, 0.2, 'brass', cx=3.8, cz=0.6)
    m.rod(3.3, 7.0, 0.3, 'brass', cx=2.4, cz=-2.4)
    m.seg((2.4, 7.0), (0.8, 6.0), 0.4, 0.4, 'brass', z=-2.4)
    m.rod(3.6, 6.0, 0.15, 'brass', cx=0.8, cz=-2.4)
    for q in range(10):                                                # the horn flaring forward
        r = 0.3 + 0.3 * q ** 1.25
        z = -2.4 + q * 0.7
        m.rod(z, z + 0.72, r, 'horn', cx=2.4 - q * 0.26, cz=7.0 + q * 0.5, axis=2)
    m.rod(z + 0.72, z + 0.76, r * 0.86, 'horn_in', cx=2.4 - 9 * 0.26, cz=7.0 + 9 * 0.5, axis=2)


def reel_to_reel(m, k=0):
    """A reel-to-reel deck standing up as in the photos: the brushed face, the two big reels with tape threaded
    between, the head cover, the lit VU meters, the piano-key transport buttons and the wooden side cheeks."""
    w, h, d = 10.0, 11.0, 4.0
    m.box([-w / 2, 0.0, -d / 2], [w / 2, h, d / 2], 'face')
    for x in (-w / 2 - 0.5, w / 2):
        m.box([x, 0.0, -d / 2], [x + 0.5, h, d / 2], 'cheek')
    for x in (-2.4, 2.4):
        m.rod(d / 2 + 0.2, d / 2 + 0.5, 2.3, 'reel', cx=x, cz=8.2, axis=2)
        m.rod(d / 2, d / 2 + 0.6, 0.3, 'dark', cx=x, cz=8.2, axis=2)
    m.box([-1.6, 4.6, d / 2], [1.6, 6.0, d / 2 + 0.6], 'dark')
    m.box([-2.4, 5.9, d / 2 + 0.3], [2.4, 6.1, d / 2 + 0.35], 'tape')
    for x in (-3.6, -1.8):
        m.box([x, 2.4, d / 2], [x + 1.4, 3.6, d / 2 + 0.1], 'glow2')
    for q in range(5):
        m.box([0.4 + q * 0.8, 2.4, d / 2], [1.0 + q * 0.8, 3.0, d / 2 + 0.3], 'keycap')
    m.box([-4.4, 0.6, d / 2], [4.4, 1.6, d / 2 + 0.1], 'dark')
    m.rod(d / 2, d / 2 + 0.4, 0.4, 'keycap', cx=3.6, cz=1.1, axis=2)


# ================================================================== looks


def _looks():
    paint = lambda c: (lambda f: P.paint(f, c, steel_base=0.4, chips=0.3))      # noqa: E731
    wood = lambda a, b: (lambda f: P.wood(f, a, b))                              # noqa: E731
    leather = lambda c: (lambda f: P.leather(f, c))                             # noqa: E731
    dark = A.plastic('#1e1f20')
    blk = A.plastic('#141414')
    out = {}
    LABELS = ['#f0c020', '#c8202a', '#e8e4da', '#2a8a3a', '#2a4ae8', '#f06a1a', '#8a2a8a', '#40c0c0']
    out['vinyl_record'] = [dict(b=vinyl_record, sleeve=sleeve_face(k), disc=record_face(LABELS[k % 8]), wire=A.chrome,
                                _px={'sleeve': 12, 'disc': 12}) for k in range(16)]
    CASES = [('#e8dcc0', '#a8281f'), ('#2a8a8a', '#e8e4da'), ('#6a3a1e', '#e8c860'), ('#8a8a84', '#141414')]
    out['record_player'] = [dict(b=record_player, case=leather(a), trim=A.flat(t), deck=A.flat('#d8d4c8'),
                                 platter=dark, disc=record_face(LABELS[k], ('top',)),
                                 metal=A.chrome, dark=dark, cloth=grille('#c8b890', '#8a7a5a'),
                                 _px={'disc': 10}) for k, (a, t) in enumerate(CASES)]
    out['karaoke_machine'] = [dict(b=karaoke_machine, cab=A.plastic(c), cloth=grille('#1a1a1a', '#2a2a2a'),
                                   front=A.plastic('#2a2a2c'), glow=A.flat('#40e080', 0.02), dark=dark,
                                   mic=A.plastic('#141414'), metal=A.chrome)
                              for c in ('#1e1f20', '#8a8a90')]
    TVS = [('#5a3418', '#3a2010'), ('#3a1a10', '#1a0a06'), ('#8a6a44', '#5a4024')]
    out['big_crt_tv'] = [dict(b=big_crt_tv, cab=wood(a, b2), mask=A.flat(m2), glow=screen_face('#5a6a6a'),
                              knob=A.plastic('#c8b080' if q != 2 else '#c8ccd0'), cloth=grille(g, b2, 'slats'),
                              trim=P.brass if q != 2 else A.chrome)
                         for q, ((a, b2), m2, g) in enumerate(zip(TVS, ('#c8a878', '#2a1a10', '#b8b4ac'),
                                                                    ('#c8a878', '#d8c098', '#4a3a2a')))]
    out['accordion'] = [dict(b=accordion, shell=A.plastic(s, 0.2), keyboard=keys(14, faces=('front',)),
                             bellows=A.fabric(bl), pleat=A.fabric('#3a3a3a'), buttons=grille('#141414', '#e8e4da', 'holes'),
                             grill=A.chrome, strap=leather('#3a2414'), _px={'keyboard': 10})
                        for s, bl in (('#a8281f', '#141414'), ('#141414', '#a8281f'))]
    HIFI = [('#1a1a1c', '#c8ccd0', '#40e080'), ('#c8ccd0', '#2a2a2a', '#40c0f0'), ('#1a1a1c', '#c8202a', '#f04030')]
    out['stereo_system'] = []
    for face, ink, g in HIFI:
        d = dict(b=stereo_system, rack=wood('#3a2a1a', '#1a1008') if face != '#c8ccd0' else A.plastic('#2a2a2a'),
                 unit=A.plastic(face), disc=record_face('#c8202a', ('top',)),
                 spk=wood('#4a3020', '#2a1a10'), cloth=grille('#202022', '#2a2a2a'), _px={})
        for kind in ('amp', 'tuner', 'tape', 'cd'):
            d[f'p_{kind}'] = panel(kind, face, ink, g)
            d['_px'][f'p_{kind}'] = 10
        out['stereo_system'].append(d)
    AMPS = [('#141414', '#3a3a3a'), ('#e8dcb0', '#8a7a5a'), ('#141414', '#c8c0a8'), ('#6a1a1a', '#2a2a2a')]
    GTRS = ['#a8181e', '#141414', '#f0ece0', '#2a4ae8', '#e8a020', '#3a8a5a', '#8a2a8a', '#f06a1a']
    out['guitar_amp'] = [dict(b=guitar_amp, tolex=leather(AMPS[k % 4][0]), cloth=grille(AMPS[k % 4][1], '#000'),
                              chrome=A.chrome, dark=dark, guitar=paint(GTRS[k]),
                              neck=wood('#c89860', '#8a6040')) for k in range(8)]
    SHELLS = ['#c8401a', '#141414', '#a8181e', '#1e3a8a']
    out['drum_kit'] = [dict(b=drum_kit, shell=paint(s), skin=A.flat('#efe9dc'), chrome=A.chrome,
                            cymbal=P.brass, seat=leather('#141414'),
                            head=drum_head(BANDS[k * 3][0], '#141414', s), _px={'head': 10})
                       for k, s in enumerate(SHELLS)]
    out['dj_turntables'] = [dict(b=dj_turntables, stand=paint('#2a2a2a'), plinth=A.plastic(p), mixer=A.plastic(p),
                                 platter=A.chrome, disc=record_face(LABELS[k + 2], ('top',)), chrome=A.chrome,
                                 dark=dark, glow=A.flat('#40e080', 0.02), _px={'disc': 10})
                            for k, p in enumerate(('#b8bcc0', '#1e1f20'))]
    out['projector_screen'] = [dict(b=projector_screen, metal=A.chrome, case=paint(c), screen=screen_cloth, dark=dark,
                                    shelf=paint('#3a3a3a'), projector=paint(pj), glow=A.flat('#f8f0d0', 0.02),
                                    reel=A.steel) for c, pj in (('#2a2a2a', '#3a4a5a'), ('#6a6a64', '#5a5a52'))]
    JUKE = [('#6a3418', '#f06a1a', '#f0c020'), ('#e8c020', '#e83a8a', '#40e0d0'), ('#141414', '#c8202a', '#2a8ae8'),
            ('#e8e4da', '#e83a2a', '#40c040')]
    out['jukebox'] = [dict(b=jukebox, cab=wood(c, '#3a1a08') if q == 0 else paint(c),
                           glow=A.flat(g1, 0.02), glow2=A.flat(g2, 0.02),
                           inside=A.flat('#2a2a2a'), disc=record_face('#f0c020'), strips=keys(20, '#c8202a', '#f0ece0',
                                                                                              ('front', 'top')),
                           chrome=A.chrome, cloth=grille('#c8a878', '#8a6a44'))
                      for q, (c, g1, g2) in enumerate(JUKE)]
    out['upright_piano'] = [dict(b=upright_piano, case=cs, panel=cs, keys=keys(52, faces=('top',)), brass=P.brass,
                                 _px={'keys': 14})
                            for cs in (paint('#141414'), wood('#5a2414', '#3a140a'), wood('#7a5030', '#4a301a'))]
    out['gramophone'] = [dict(b=gramophone, case=wood(a, b2), panel=wood(b2, a), felt=A.fabric('#2a5a2a'),
                              disc=record_face('#c8202a', ('top',)), brass=P.brass, horn=hn, horn_in=hi)
                         for (a, b2), hn, hi in ((('#8a5a30', '#5a3418'), P.brass, A.flat('#3a2a10')),
                                                 (('#3a1a10', '#1a0a06'), paint('#141414'), A.flat('#a8281f')))]
    out['reel_to_reel'] = [dict(b=reel_to_reel, face=A.plastic(f), cheek=wood('#6a3a1e', '#3a1a0a'),
                                reel=reel_face('#5a3a20'), dark=dark, tape=A.flat('#5a3a20'),
                                glow2=A.flat('#f0c060', 0.02), keycap=A.plastic(kc), _px={'reel': 10})
                           for f, kc in (('#c8ccd0', '#2a2a2a'), ('#2a2a2c', '#c8ccd0'))]
    for ls in out.values():
        for k, d in enumerate(ls):
            d['b'] = (lambda fn, k: (lambda m: fn(m, k)))(d['b'], k)
    return out


def looks(it, wrap):
    return {i: [wrap(it, d) for d in ls] for i, ls in _looks().items()}
