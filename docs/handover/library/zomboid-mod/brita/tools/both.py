"""Preview sheet: per calibre, Arsenal's round in toon (on Josiah's PC) beside our own turned one (the shared jar)."""
import sys, numpy as np
sys.argv = ['x']
from PIL import Image, ImageDraw, ImageFont
import importlib.util
S = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/ammo/'
spec = importlib.util.spec_from_file_location('ars', S + 'arsenal_lib.py'); A = importlib.util.module_from_spec(spec); spec.loader.exec_module(A)
import rounds as RD
AR = A.AR
def hexcol(h): return np.array([int(h[i:i+2], 16) for i in (1, 3, 5)], float)

def render_cols(tri, cols, view, size=260):
    # paint each triangle with its colour via a 1x1 "texture" per colour: reuse the textured renderer
    pal = sorted(set(cols)); tex = np.array([[hexcol(c) for c in pal]])
    idx = {c: i for i, c in enumerate(pal)}
    uv = np.array([[[(idx[c] + .5) / len(pal), .5]] * 3 for c in cols])
    return A.render(tri, uv, tex, view, size)

ROWS = [('9mm', 'Bullets9mm', 'A'), ('.380 ACP', 'Bullets380', 'A'), ('.38 Special', 'Bullets38', 'A'),
        ('.44 Magnum', 'Bullets44', 'A'), ('.45 ACP  NEW', 'Bullets45', 'A'), ('5.7x28mm  NEW', 'Bullets57', 'B'),
        ('.45 Colt  NEW', 'Bullets45LC', 'A'), ('.500 S&W  NEW', 'Bullets50MAG', 'A'), ('.22 LR', 'Bullets22', 'A'),
        ('5.56mm', '556Bullets', 'A'), ('.308', '308Bullets', 'A'), ('.30-30 (Arsenal .45-70)', 'Bullets4570', 'A'),
        ('.50 BMG', '50BMGBullets', 'A'), ('12 gauge', '12gShotgunShells', 'A'), ('40mm grenade', '40Round', 'A')]
TEX = {k: S + f'src/Bullets_{k}.png' for k in 'ABCD'}
views = [(25, -35), (-20, 150)]
size = 260
W = 300 + size * 4
sheet = Image.new('RGB', (W, 70 + size * len(ROWS)), (24, 24, 24))
dr = ImageDraw.Draw(sheet)
f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 20)
fb = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
dr.text((300 + 40, 22), "Arsenal's round, toon (your PC)", fill=(240, 220, 160), font=fb)
dr.text((300 + size * 2 + 40, 22), 'Our own round (shared jar, friends)', fill=(160, 210, 240), font=fb)
cache = {}
for i, (label, mesh, t) in enumerate(ROWS):
    y = 70 + i * size
    tri, uv = A.load(AR + f'models_X/WorldItems/{mesh}.x')
    if t not in cache:
        im = Image.open(TEX[t]).convert('RGB'); im.thumbnail((1024, 1024)); cache[t] = A.toon(im)
    # lay Arsenal's along +x like ours: its long axis is z
    ext = np.ptp(tri.reshape(-1, 3), 0)
    if ext[2] >= ext[0]: tri = tri[..., [2, 1, 0]]
    for j, v in enumerate(views):
        sheet.paste(A.render(tri, uv, cache[t], v, size), (300 + j * size, y))
    key = label.split('  ')[0].split(' (')[0]
    ot, oc = RD.lathe(RD.ROUNDS[key])
    for j, v in enumerate(views):
        sheet.paste(render_cols(ot, oc, v, size), (300 + (2 + j) * size, y))
    dr.text((16, y + size // 2 - 12), label, fill=(225, 225, 225), font=f)
sheet.save(S + 'rounds_both.png')
print('ok', sheet.size)
