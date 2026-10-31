"""Paints the two-post car lift's texture atlas.

python3 tools/hoist/paint.py   (writes src/main/resources/assets/zomboidcraft/textures/entity/hoist/atlas.png)

From refs/autoshop/two-post-lift: blue-painted posts chipped to bare steel at the edges and oily low down,
a grey steel control box with a green UP button, a red emergency stop and a white lamp, a black motor on a
pump block over an off-white plastic oil reservoir (oil line showing through), a lowering lever with a red
knob, black hydraulic hoses, rubber lift pads, the carriage's ladder of lock slots, a stencilled post plate,
and yellow-and-black hazard paint at the feet. Toon-flat colours with hard edge lines, like the rest of the
kits. 8 x 8 tiles of 32 px; HoistRenderer names each tile by index (row-major), so keep the two in step.
"""
import os

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'zomboidcraft', 'textures', 'entity', 'hoist', 'atlas.png')
T = 32
rng = np.random.default_rng(1983)

BLUE, STEEL, RUBBER, CHROME, RESERVOIR, BOX, GREEN, RED, LAMP, HOSE, HAZARD, PLATE, MOTOR, BASE, KNOB, LADDER, CABLE, STICKER, BLUE_DARK, PAD, \
    JACK_RED, CRANE_YELLOW, WASHER_GREEN, SOLVENT, TREAD, MACHINE_GREY, TURNTABLE, JACK_LABEL, BRUSH = range(29)


def noise(scale=1.0):
    return rng.normal(0, 1, (T, T)) * scale


def flat(rgb, grain=6, edge=0.72):
    a = np.zeros((T, T, 4))
    a[..., :3] = np.array(rgb)[None, None, :] + noise(grain)[..., None]
    a[..., 3] = 255
    # the toon edge line round the tile
    a[0, :, :3] *= edge
    a[-1, :, :3] *= edge
    a[:, 0, :3] *= edge
    a[:, -1, :3] *= edge
    return a


def chips(a, bare=(150, 150, 146), n=14, low_grime=True):
    for _ in range(n):
        x, y = rng.integers(0, T, 2)
        r = rng.integers(1, 3)
        a[max(0, y - r):y + r, max(0, x - r):x + r, :3] = np.array(bare) + rng.normal(0, 8)
    if low_grime:
        for y in range(T):
            k = max(0.0, (y - T * 0.6) / (T * 0.4))
            a[y, :, :3] *= 1 - 0.35 * k
    return a


def tile(i, a):
    col, row = i % 8, i // 8
    atlas[row * T:(row + 1) * T, col * T:(col + 1) * T] = np.clip(a, 0, 255)


atlas = np.zeros((8 * T, 8 * T, 4))
tile(BLUE, chips(flat((38, 92, 170)), n=18))
tile(BLUE_DARK, chips(flat((24, 58, 112)), n=8))
tile(STEEL, chips(flat((92, 94, 98), grain=9), bare=(140, 132, 120), n=10))
tile(RUBBER, flat((26, 26, 28), grain=4))
c = flat((196, 200, 206), grain=3)
for x in range(T):
    c[:, x, :3] *= 0.8 + 0.25 * np.sin(x / T * np.pi)     # a round ram catching the light
tile(CHROME, c)
r = flat((214, 204, 178), grain=5)
r[: T // 3, :, :3] *= 0.92
r[T // 3:, :, :3] = r[T // 3:, :, :3] * 0.6 + np.array((120, 84, 30)) * 0.4   # the oil showing through
r[T // 3 - 1:T // 3 + 1, :, :3] = (90, 60, 24)
tile(RESERVOIR, chips(r, bare=(150, 110, 60), n=6))
b = flat((150, 152, 148), grain=5)
b[4:20, 5:27, :3] = (226, 222, 210)          # the instruction sticker
b[6:18:3, 7:25, :3] = (120, 118, 112)
tile(BOX, b)
tile(GREEN, flat((60, 190, 80), grain=4, edge=0.6))
tile(RED, flat((190, 36, 34), grain=4, edge=0.6))
tile(LAMP, flat((236, 236, 250), grain=2, edge=0.8))
h = flat((22, 22, 24), grain=3)
h[:, ::6, :3] *= 1.25
tile(HOSE, h)
z = np.zeros((T, T, 4))
for y in range(T):
    for x in range(T):
        z[y, x, :3] = (232, 190, 30) if ((x + y) // 8) % 2 == 0 else (24, 24, 24)
z[..., 3] = 255
tile(HAZARD, chips(z, bare=(110, 106, 98), n=10, low_grime=False))
p = flat((196, 198, 196), grain=4)
img = Image.fromarray(p.astype(np.uint8), 'RGBA')
d = ImageDraw.Draw(img)
d.text((4, 10), "LIFT", fill=(40, 40, 40, 255))
tile(PLATE, np.array(img).astype(float))
m = flat((30, 32, 34), grain=4)
m[::3, :, :3] *= 1.4                          # the motor's cooling fins
tile(MOTOR, m)
tile(BASE, chips(flat((84, 86, 88), grain=10), bare=(120, 100, 80), n=12, low_grime=False))
tile(KNOB, flat((210, 40, 30), grain=3, edge=0.55))
l = flat((24, 58, 112))
for y in range(2, T, 6):
    l[y:y + 3, 8:24, :3] = (14, 14, 16)        # the lock slots the safety pawls click into
tile(LADDER, l)
tile(CABLE, flat((30, 30, 30), grain=2))
s = flat((236, 140, 30), grain=3)
s[10:22, 6:26, :3] = (24, 24, 24)
tile(STICKER, s)
tile(PAD, flat((44, 44, 46), grain=6))
# the shop tools (ShopToolRenderer): a red trolley jack with its white instruction label (refs garage-interior 05/06),
# a yellow engine crane, a grey tyre machine with its ribbed turntable, a green solvent tub, tyre tread, a brush
tile(JACK_RED, chips(flat((198, 30, 28), grain=5), bare=(120, 110, 100), n=8))
tile(CRANE_YELLOW, chips(flat((226, 178, 26), grain=6), bare=(110, 100, 90), n=14))
tile(WASHER_GREEN, chips(flat((46, 96, 58), grain=6), bare=(120, 120, 110), n=10))
v = flat((70, 64, 40), grain=8, edge=0.9)
v[::5, :, :3] *= 1.2                          # the dirty solvent's surface, oily streaks
tile(SOLVENT, v)
t = flat((30, 30, 32), grain=3)
for x in range(0, T, 4):
    t[:, x:x + 2, :3] *= 0.55                 # tread blocks
tile(TREAD, t)
tile(MACHINE_GREY, chips(flat((120, 124, 128), grain=6), bare=(160, 160, 156), n=8))
u = flat((150, 152, 156), grain=4)
for y in range(0, T, 5):
    u[y:y + 2, :, :3] *= 0.6                  # the turntable's ribs
tile(TURNTABLE, u)
lab = flat((232, 228, 216), grain=3, edge=0.85)
lab[5:27:4, 4:28, :3] = (110, 108, 104)
lab[22:28, 22:28, :3] = (190, 30, 28)
tile(JACK_LABEL, lab)
br = flat((160, 120, 70), grain=7)
br[:, ::3, :3] *= 0.7
tile(BRUSH, br)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
Image.fromarray(atlas.astype(np.uint8), 'RGBA').save(OUT)
print('wrote', OUT)
