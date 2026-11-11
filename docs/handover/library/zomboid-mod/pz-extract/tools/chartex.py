"""Character textures. Mod bodies: the chest/waist/hips/crotch area is replaced with the vanilla body (underwear) before any
clothes go on, and the trouser area is cut out, so only face, neck, arms and hands come from the mod.
usage: python3 -I chartex.py <media> <mods> <outdir>"""
import sys
from PIL import Image
import numpy as np
m, mods, out = sys.argv[1].rstrip('/'), sys.argv[2].rstrip('/'), sys.argv[3]
T = lambda p: Image.open(f'{m}/textures/{p}').convert('RGBA')
def tint(im, rgb):
    a = np.asarray(im).astype(float); a[..., :3] *= np.array(rgb) / 255.0
    return Image.fromarray(a.clip(0, 255).astype('uint8'))
def mask(names, size):
    acc = np.zeros(size[::-1], bool)
    for n in names:
        a = np.asarray(T(f'Body/Masks/{n}.png').resize(size, Image.NEAREST))[..., 3] > 127
        acc |= a
    return acc
def dress(body, shirt_rgb):
    size = body.size
    for layer in [tint(T('clothes/shoes_socks_textres/whitesocks_short.png'), (230, 228, 220)),
                  tint(T('clothes/shoes_socks_textres/trainers_white.png'), (235, 235, 235)),
                  tint(T('clothes/shirt_tshirt_textures/tshirt_white.png'), shirt_rgb)]:
        body.alpha_composite(layer.resize(size, Image.BILINEAR))
    b = np.asarray(body).copy()
    b[mask(['Belt', 'Crotch', 'LeftLeg', 'RightLeg'], size), 3] = 0   # under the jeans
    return Image.fromarray(b)
dress(T('Body/MaleBody01.png'), (52, 70, 104)).save(f'{out}/m_body.png')
van = T('Body/FemaleBody01.png')
dress(van.copy(), (120, 40, 52)).save(f'{out}/f_body_std.png')
for key, path in [('detail', '3578569664/mods/DetailedPlayerTextureFemaleB42/42/media/textures/Body/FemaleBody01.png'),
                  ('ink', '3408780732/mods/Evils Ink/common/media/textures/Body/FemaleBody01.png')]:
    im = Image.open(f'{mods}/{path}').convert('RGBA')
    if im.size[0] > 1024: im = im.resize((1024, 1024), Image.LANCZOS)
    size = im.size
    a = np.asarray(im).copy(); v = np.asarray(van.resize(size, Image.BILINEAR))
    cover = mask(['Chest', 'Waist', 'Belt', 'Crotch'], size)
    a[cover] = v[cover]
    dress(Image.fromarray(a), (120, 40, 52)).save(f'{out}/f_body_{key}.png')
for k, rgb in [('m_hair', (58, 44, 34)), ('f_hair', (92, 60, 38))]:
    tint(T('F_Hair_White.png'), rgb).save(f'{out}/{k}.png')
T('clothes/trousers_mesh/trousersmesh_denim.png').save(f'{out}/m_jeans.png')
T('clothes/trousers_mesh/trousersmesh_denimlight.png').save(f'{out}/f_jeans.png')
print('ok')
