# Cut a turnaround painted over zombie-turnaround-template.png into a standard 64-layout skin at 1024.
import sys
from PIL import Image, ImageOps, ImageEnhance
src = Image.open(sys.argv[1]).convert('RGBA'); out = sys.argv[2]
k = src.width / 3072                          # the template is 3072x2048
S, G, INSET = 1800 / 34.4, 1938, 0.2          # px per unit, sole line, units trimmed off each block edge (ink outline)
N = 16; skin = Image.new('RGBA', (64 * N, 64 * N), (0, 0, 0, 0))
def crop(cx, x0, x1, y0, y1, flip=False, band=None):
    """Block in model units -> image crop (inset past the drawn outline)."""
    L, R = cx + (x0 + INSET) * S, cx + (x1 - INSET) * S
    T, B = G - (y1 - 0.4 - INSET) * S, G - (y0 - 0.4 + INSET) * S
    if band == 'top': B = T + min(B - T, R - L) * 0.6                 # a patch from the top of the block, not a stretched strip
    if band == 'bottom': T = B - min(B - T, R - L) * 0.6
    if band == 'outer': L = R - (R - L) * 0.18
    if band == 'inner': R = L + (R - L) * 0.18
    im = src.crop(tuple(round(v * k) for v in (L, T, R, B)))
    return ImageOps.mirror(im) if flip else im
def put(im, u, v, w, h):                      # skin-pixel rectangle (fractional rows allowed)
    W, H = round(w * N), round(h * N)
    if W > 0 and H > 0: skin.paste(im.resize((W, H), Image.LANCZOS), (round(u * N), round(v * N)))
FX, BX, SX = 560, 1536, 2560
# how the body samples each 12-row limb strip
ARM = [(0, 4.96, 21.6, 27.3, 1.35), (4.96, 9.39, 16.7, 21.6, 1.25), (9.39, 12, 13.7, 16.7, 1.35)]
LEG = [(0, 5.68, 8.4, 15.2, 1.7), (5.68, 10.89, 1.8, 8.4, 1.5), (10.89, 12, 0.4, 1.8, 1.6)]
SIDE_ARM = [(-1.35, 1.35), (-1.25, 1.25), (-0.75, 0.95)]; SIDE_LEG = [(-1.7, 1.7), (-1.5, 1.5), (-1.6, 3.6)]
def side(z0, z1, y0, y1, flip): return crop(SX, -z1, -z0, y0, y1, flip)    # side view faces left: front (+z) at image left
def limb(u, v, rows, sides, ax, front_back_x, mirror_outer):
    # u, v: region origin; faces right(0-4) front(4-8) left(8-12) back(12-16)
    for (r0, r1, y0, y1, hw), (z0, z1) in zip(rows, sides):
        put(crop(FX, ax - hw, ax + hw, y0, y1), u + 4, v + 4 + r0, 4, r1 - r0)
        put(crop(BX, front_back_x - hw, front_back_x + hw, y0, y1), u + 12, v + 4 + r0, 4, r1 - r0)
        put(side(z0, z1, y0, y1, True), u + 0, v + 4 + r0, 4, r1 - r0)       # -x face: front on its right
        put(side(z0, z1, y0, y1, False), u + 8, v + 4 + r0, 4, r1 - r0)      # +x face: front on its left
    top = rows[0]; bot = rows[-1]
    put(crop(FX, ax - top[4], ax + top[4], top[2], top[3], band='top'), u + 4, v, 4, 4)
    put(ImageEnhance.Brightness(crop(FX, ax - bot[4], ax + bot[4], bot[2], bot[3], band='bottom')).enhance(0.6), u + 8, v, 4, 4)
# head
put(crop(FX, -3.2, 3.2, 27.6, 34.8), 8, 8, 8, 8)
put(crop(BX, -3.2, 3.2, 27.6, 34.8), 24, 8, 8, 8)
put(side(-3.3, 3.3, 27.6, 34.8, True), 0, 8, 8, 8)
put(side(-3.3, 3.3, 27.6, 34.8, False), 16, 8, 8, 8)
put(crop(BX, -3.2, 3.2, 27.6, 34.8, band='top'), 8, 0, 8, 8)
put(ImageEnhance.Brightness(crop(FX, -3.2, 3.2, 27.6, 34.8, band='bottom')).enhance(0.6), 16, 0, 8, 8)
# torso: chest rows 0-5.46, waist 5.0-9.1, hips 9.1-12 (as the body samples them)
for x0, cx in [(20, FX), (32, BX)]:
    put(crop(cx, -3.3, 3.3, 18.16, 21.96), x0, 20 + 5.0, 8, 4.1)
    put(crop(cx, -3.8, 3.8, 21.96, 27.6), x0, 20, 8, 5.46)
    put(crop(cx, -3.5, 3.5, 15.2, 18.2), x0, 20 + 9.1, 8, 2.9)
for x0, flip, band in [(16, False, 'inner'), (28, True, 'inner')]:      # sides: arms hide them in the side view, so use the front's edge
    put(crop(FX, -3.3, 3.3, 18.16, 21.96, flip, band), x0, 25.0, 4, 4.1)
    put(crop(FX, -3.8, 3.8, 21.96, 27.6, flip, band), x0, 20, 4, 5.46)
    put(crop(FX, -3.5, 3.5, 15.2, 18.2, flip, band), x0, 29.1, 4, 2.9)
put(crop(FX, -3.8, 3.8, 21.96, 27.6, band='top'), 20, 16, 8, 4)
put(ImageEnhance.Brightness(crop(FX, -3.5, 3.5, 15.2, 18.2, band='bottom')).enhance(0.6), 28, 16, 8, 4)
# arms (character's right is image-left from the front, image-right from the back) and legs
limb(40, 16, ARM, SIDE_ARM, -5.05, 5.05, True)
limb(32, 48, ARM, SIDE_ARM, 5.05, -5.05, False)
limb(0, 16, LEG, SIDE_LEG, -1.82, 1.82, True)
limb(16, 48, LEG, SIDE_LEG, 1.82, -1.82, False)
skin.save(out + '_1024.png')
for r in (256, 64): skin.resize((r, r), Image.LANCZOS).save(out + f'_{r}.png')
print('ok', skin.size)
