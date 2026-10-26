"""Handheld radios: a toy walkie-talkie, a two-way radio and a military handheld.

    python3 tools/weapons/radios.py

Each is built standing on y = 0 with its front toward +z (like the bags), drawn big: the item display shrinks it.
They look like the character gear (chunky blocks, a thin outline round each part, muted colours) and run on the
same AA batteries as the night vision.

Josiah 2026-10-26: radios that actually work, carrying the players' voices (through the voice chat mod), and a
comms headset for the combat helmet (headgear.py) so only the wearer hears them.
"""
import json
from pathlib import Path

import numpy as np

import kit
import model as M
import paint as P
from headgear import rod_x, rod_y, rod_z

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zomboidcraft'
NS = 'zomboidcraft'


def body(m, w, d, y0, y1, mat, cut, z=0.0):
    """A block w wide and d deep from y0 to y1, its upright edges cut off so it reads rounded."""
    m.box((-w / 2, y0, z - d / 2 + cut), (w / 2, y1, z + d / 2 - cut), mat)
    m.box((-w / 2 + cut, y0, z - d / 2), (w / 2 - cut, y1, z + d / 2), mat)


def belt_clip(m, x0, x1, y0, y1, back):
    """A spring belt clip on the back: its hinge block at the top and the clip down from it."""
    m.box((x0 - 0.1, y1 - 0.5, back - 0.32), (x1 + 0.1, y1, back), 'clip')
    m.box((x0, y0, back - 0.32), (x1, y1 - 0.2, back - 0.12), 'clip')


def ptt(m, x, y0, y1, half=0.45, depth=0.18):
    """The big talk button on the left side, with grip ribs across it."""
    m.box((x - depth, y0, -half), (x, y1, half), 'ptt')
    for y in np.arange(y0 + 0.2, y1 - 0.1, 0.32):
        m.box((x - depth - 0.05, y, -half + 0.08), (x - depth, y + 0.12, half - 0.08), 'ptt')


# ---------------------------------------------------------------- the radios

def toy_radio(m):
    """A kids' walkie-talkie: a chunky moulded body, a round speaker, a big red call button, the talk button on
    the side, a long antenna with a ball on the end and a three-channel slide switch on top."""
    body(m, 3.4, 2.0, 0.0, 6.0, 'body', 0.45)
    m.box((-1.35, 0.9, 0.95), (1.35, 5.3, 1.1), 'panel')               # the front panel, its corners rounded
    m.box((-1.05, 0.6, 0.95), (1.05, 5.6, 1.1), 'panel')
    rod_z(m, 1.1, 1.18, 1.0, 'speaker', 0.0, 3.85)
    rod_z(m, 1.1, 1.34, 0.5, 'call', 0.0, 1.75)
    rod_z(m, 1.1, 1.24, 0.2, 'led', 0.85, 5.15)
    ptt(m, -1.7, 2.6, 4.5, 0.5, 0.22)
    rod_y(m, 6.0, 6.35, 0.5, 'body', -0.95, 0.0)                       # antenna base
    rod_y(m, 6.35, 9.9, 0.36, 'antenna', -0.95, 0.0)
    rod_y(m, 9.9, 10.45, 0.46, 'antenna', -0.95, 0.0)                  # the ball on its tip
    m.box((0.2, 6.0, -0.32), (1.35, 6.1, 0.32), 'slot')                # channel switch
    m.box((0.32, 6.1, -0.22), (0.68, 6.42, 0.22), 'switch')
    belt_clip(m, -0.55, 0.55, 2.4, 5.6, -1.0)
    m.box((-1.2, 0.0, -0.9), (1.2, 0.08, 0.9), 'panel')                # the battery door underneath


def two_way_radio(m):
    """A two-way radio, the kind shops, security guards and campers carry: a slim body with rubber side grips,
    an amber screen, a slotted speaker, three buttons, a stubby antenna, a power and volume knob, the talk
    button on the left and the battery door and belt clip on the back."""
    body(m, 3.0, 1.7, 0.0, 6.4, 'body', 0.32)
    for s in (1, -1):
        x0, x1 = sorted((s * 1.5, s * 1.62))
        m.box((x0, 0.5, -0.62), (x1, 4.8, 0.62), 'grip')
    m.box((-1.18, 4.4, 0.85), (1.18, 5.65, 0.88), 'bezel')
    m.box((-1.0, 4.55, 0.88), (1.0, 5.45, 0.9), 'lcd')
    m.box((-1.15, 1.6, 0.85), (1.15, 4.05, 0.9), 'speaker')
    for x in (-0.9, -0.25, 0.4):
        m.box((x, 0.65, 0.85), (x + 0.5, 1.15, 1.0), 'button')
    rod_y(m, 6.4, 6.75, 0.42, 'rubber', -0.75, 0.0)                    # antenna
    rod_y(m, 6.75, 9.3, 0.3, 'rubber', -0.75, 0.0)
    rod_y(m, 9.3, 9.5, 0.22, 'rubber', -0.75, 0.0)
    rod_y(m, 6.4, 6.95, 0.38, 'knob', 0.62, 0.0)
    m.box((0.58, 6.95, 0.1), (0.66, 6.97, 0.35), 'mark')
    m.box((-0.12, 6.4, -0.12), (0.12, 6.47, 0.12), 'led')
    ptt(m, -1.62, 3.2, 4.6, 0.42, 0.16)
    m.box((-1.72, 2.55, -0.25), (-1.62, 2.95, 0.25), 'button')         # call button under it
    m.box((-1.25, 0.35, -0.88), (1.25, 4.7, -0.85), 'door')
    m.box((-0.3, 0.45, -0.95), (0.3, 0.75, -0.88), 'button')           # its latch
    belt_clip(m, -0.5, 0.5, 2.6, 6.0, -0.88)


def military_radio(m):
    """A military handheld: a tough olive body on a big battery box, a screen and keypad on the front, the
    channel and volume knobs on top, a long flexible whip antenna, the talk button on the left, the connector
    for a headset on the right, and the battery box's latches."""
    body(m, 3.1, 2.4, 0.0, 2.9, 'pack', 0.36)
    body(m, 3.0, 2.3, 2.9, 8.2, 'body', 0.36)
    m.box((-1.62, 2.6, -0.5), (1.62, 3.15, 0.5), 'latch')                # latches over the join, both sides
    m.box((-1.1, 5.85, 1.15), (1.1, 7.25, 1.18), 'bezel')
    m.box((-0.95, 6.0, 1.18), (0.95, 7.1, 1.2), 'lcd_mil')
    for row in range(4):
        for col in range(3):
            x, y = -0.95 + col * 0.68, 3.35 + row * 0.6
            m.box((x, y, 1.15), (x + 0.52, y + 0.44, 1.3), 'key')
    m.box((-1.3, 0.4, 1.2), (1.3, 2.4, 1.24), 'plate')                  # the battery's label plate
    rod_y(m, 8.2, 8.95, 0.62, 'knob', 0.55, 0.15)                       # channel knob
    m.box((0.5, 8.95, 0.25), (0.6, 8.97, 0.75), 'mark')
    rod_y(m, 8.2, 8.75, 0.42, 'knob', -0.35, -0.55)                     # volume
    rod_y(m, 8.2, 8.8, 0.5, 'antenna_base', -0.95, 0.55)
    rod_y(m, 8.8, 14.4, 0.27, 'antenna', -0.95, 0.55)
    rod_y(m, 14.4, 14.7, 0.33, 'antenna', -0.95, 0.55)
    ptt(m, -1.5, 5.0, 7.0, 0.55, 0.2)
    rod_x(m, 1.5, 1.82, 0.42, 'antenna_base', 7.35, 0.0)                # the headset connector and its cap
    rod_x(m, 1.82, 1.95, 0.46, 'cap', 7.35, 0.0)
    belt_clip(m, -0.6, 0.6, 3.6, 7.8, -1.15)


# ---------------------------------------------------------------- looks

def lcd(f, bg, ink):
    """A small screen: the channel in two big blocky digits on the right, signal and battery bars on the left."""
    u = f.uv[..., 0] / max(f.uv[..., 0].max(), 1e-3)
    v = f.uv[..., 1] / max(f.uv[..., 1].max(), 1e-3)
    c = P.plain(f, bg, 0.03) * (1 - v * 0.12)[..., None]
    on = np.zeros(u.shape, bool)
    for x0 in (0.5, 0.74):                                              # two digits, segments of an 8 drawn partly
        x1 = x0 + 0.18
        on |= (u > x0) & (u < x1) & (np.abs(v - 0.2) < 0.06)
        on |= (u > x0) & (u < x1) & (np.abs(v - 0.5) < 0.05) & (x0 > 0.6)
        on |= (np.abs(u - x1) < 0.03) & (v > 0.18) & (v < 0.82)
        on |= (np.abs(u - x0) < 0.03) & (v > 0.18) & (v < 0.5) & (x0 < 0.6)
        on |= (u > x0) & (u < x1) & (np.abs(v - 0.8) < 0.06) & (x0 < 0.6)
        on |= (np.abs(u - x0) < 0.03) & (v > 0.5) & (v < 0.82) & (x0 < 0.6)
    for i, h in enumerate((0.2, 0.35, 0.5)):                            # signal bars
        x = 0.08 + i * 0.09
        on |= (u > x) & (u < x + 0.05) & (v < 0.85) & (v > 0.85 - h)
    on |= (u > 0.08) & (u < 0.34) & (np.abs(v - 0.15) < 0.035)          # battery
    return np.where(on[..., None], P.mix(c, P.col(ink), 0.85), c)


def slots(f, c, k=5.0):
    """A speaker's slots: rows across the face."""
    v = f.uv[..., 1] * k
    return np.where(((v % 1) < 0.42)[..., None], c * 0.3, c)


def rings(f, c, k=4.0):
    """A round speaker's rings, out from the middle of the face."""
    u = f.uv[..., 0] - f.uv[..., 0].max() / 2
    v = f.uv[..., 1] - f.uv[..., 1].max() / 2
    r = np.sqrt(u * u + v * v)
    return np.where(((r * k) % 1 < 0.38)[..., None], c * 0.35, c)


def ribbed(f, c, k=8.0):
    return c * (1 - 0.12 * (np.sin(f.Y * k * np.pi) > 0.3))[..., None]


def radio_look(body, accent, panel='#2c2f33', antenna='#161616', screen=('#b37b2c', '#2a1a08')):
    def shell(f):
        c = P.polymer(f, body, 0.06)
        return P.mix(c, c * 0.88, np.clip((f.noise(1.2, 421, 3) - 0.5) * 2, 0, 1) * 0.3)

    def plate(f):
        c = P.polymer(f, panel, 0.04)
        a, b = f.uv[..., 0], f.uv[..., 1]
        rows = (b * 6) % 1 < 0.4
        chars = np.sin(a * 60 + np.floor(b * 6) * 2.1) + np.sin(a * 23 + np.floor(b * 6)) > 0.7
        return np.where((rows & chars & (b > 0.3))[..., None], P.mix(c, P.col('#9a9a8a'), 0.5), c)

    def knurl(f):
        c = P.rubber(f, '#181818', 0.1)
        return np.where((np.sin((f.X + f.Z) * 22) > 0.3)[..., None], c * 0.6, c)

    d = dict(body=shell, pack=lambda f: P.polymer(f, accent, 0.06), panel=lambda f: P.polymer(f, panel, 0.05),
             grip=lambda f: ribbed(f, P.rubber(f, accent, 0.1)), bezel=lambda f: P.polymer(f, '#121314', 0.04),
             lcd=lambda f: lcd(f, *screen), lcd_mil=lambda f: lcd(f, '#76805f', '#1c2116'),
             speaker=lambda f: slots(f, P.polymer(f, '#1c1d1e', 0.05)),
             button=lambda f: P.rubber(f, '#3e4043', 0.1), key=lambda f: P.rubber(f, '#1d1e1d', 0.1),
             ptt=lambda f: P.rubber(f, '#1a1a1a', 0.12), rubber=lambda f: P.rubber(f, antenna, 0.18),
             antenna=lambda f: P.rubber(f, antenna, 0.2), antenna_base=lambda f: P.anodised(f, '#1d1e1f', '#55595e'),
             knob=knurl, cap=knurl, mark=lambda f: P.plain(f, '#c9c2ae', 0.05), led=lambda f: P.plain(f, '#6e1c18', 0.1),
             clip=lambda f: P.polymer(f, '#161718', 0.05), door=shell, plate=plate,
             latch=lambda f: P.steel(f, 0.35, tint='#5f6366', spread=0.08), slot=lambda f: P.plain(f, '#101010', 0.04),
             switch=lambda f: P.polymer(f, panel, 0.05), call=lambda f: P.polymer(f, '#8a2c22', 0.05))
    d['_after'] = [lambda f, c: P.grime(f, c, 0.1)]
    return d


TOY_LOOK = dict(radio_look('#a3843c', '#7a2a22', panel='#39404a'),
                speaker=lambda f: rings(f, P.polymer(f, '#262a30', 0.05)))
TWO_WAY_LOOK = radio_look('#2a2b2d', '#7a6a36')
MILITARY_LOOK = radio_look('#4a4e3b', '#3e4233', panel='#3c4030')


class Radio:
    def __init__(self, key, title, build, look, seed):
        self.key, self.title, self.build, self.look, self.seed = key, title, build, look, seed


RADIOS = [
    Radio('toy_radio', 'Toy Walkie-Talkie', toy_radio, TOY_LOOK, 971),
    Radio('two_way_radio', 'Two-Way Radio', two_way_radio, TWO_WAY_LOOK, 972),
    Radio('military_radio', 'Military Handheld Radio', military_radio, MILITARY_LOOK, 973),
]


# ---------------------------------------------------------------- writing

HELD = {  # how a radio sits in the hand: upright in the fist, its front out
    'thirdperson_righthand': ([0, 0, 0], [0, 1.5, 1.0], 8.0),
    'firstperson_righthand': ([0, -25, 0], [1.2, 1.2, 0], 7.5),
}


def display(m):
    d = {}
    gui_rot = (18, -32, 0)
    s, t = M.fit(m, M.R_xyz(*gui_rot), 14.0)
    d['gui'] = M.transform(gui_rot, t, s)
    for ctx, (rot, trans, size) in HELD.items():
        s2, t2 = M.fit(m, M.R_xyz(*rot), size)
        d[ctx] = M.transform(rot, np.array(trans) + t2, s2)
    s3, t3 = M.fit(m, np.eye(3), 7.0, (0, 2, 0))
    d['ground'] = M.transform([0, 0, 0], t3, s3)
    s4, t4 = M.fit(m, M.R_xyz(0, 180, 0), 12)
    d['fixed'] = M.transform([0, 180, 0], t4, s4)
    return d


def drawn(radio):
    return kit.drawn(radio.key, radio.build, t=(8.0, 0.5, 8.0), look=radio.look, seed=radio.seed, wear=kit.LIKE_NEW)


def main():
    lang, summary = {}, []
    for radio in RADIOS:
        m = drawn(radio)
        patches = kit.paint([m])
        img, uvs, T = kit.pack(patches)
        tex = f'item/radio/{radio.key}'
        kit.save_textures(img, ASSETS / f'textures/{tex}.png')
        M.dump({'gui_light': 'front', 'textures': {'0': f'{NS}:{tex}', 'particle': f'{NS}:{tex}'},
                'elements': kit.elements(m, 0, uvs, np.zeros(3)), 'display': display(m)},
               ASSETS / f'models/item/radio/{radio.key}.json')
        M.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/radio/{radio.key}'}},
               ASSETS / f'items/{radio.key}.json')
        lang[f'item.{NS}.{radio.key}'] = radio.title
        summary.append(f'{radio.key}: {len(m.parts)} boxes, {T}px')
    path = ASSETS / 'lang/en_us.json'
    data = json.loads(path.read_text())
    data.update(lang)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')
    print('\n'.join(summary))


if __name__ == '__main__':
    main()
