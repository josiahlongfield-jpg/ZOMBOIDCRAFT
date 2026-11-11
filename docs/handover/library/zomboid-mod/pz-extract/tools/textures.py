"""Composite PZ zombie textures (body layout UVs). usage: python3 -I textures.py <media> <outdir>"""
import sys
from PIL import Image, ImageChops
import numpy as np
m, out = sys.argv[1].rstrip('/'), sys.argv[2]
T = lambda p: Image.open(f'{m}/textures/{p}').convert('RGBA')

def tint(im, rgb):
    a = np.asarray(im).astype(float); a[..., :3] *= np.array(rgb) / 255.0
    return Image.fromarray(a.clip(0, 255).astype('uint8'))

def overlay(base, ov, amt):
    """blood/grime: magenta-free overlay; use its alpha scaled by amt, only where base is opaque"""
    b = np.asarray(base).astype(float); o = np.asarray(ov).astype(float)
    a = (o[..., 3:4] / 255.0) * amt * (b[..., 3:4] / 255.0)
    b[..., :3] = b[..., :3] * (1 - a) + o[..., :3] * a
    return Image.fromarray(b.clip(0, 255).astype('uint8'))

blood, grime = T('BloodTextures/BloodOverlay.png'), T('BloodTextures/GrimeOverlay.png')
dirty = lambda im, b=0.45, g=0.55: overlay(overlay(im, grime, g), blood, b)

for lvl in (1, 2, 3):
  body = T(f'Body/M_ZedBody01_level{lvl}.png')
  for layer in [tint(T('clothes/shoes_socks_textres/whitesocks_short.png'), (200, 196, 186)),
              tint(T('clothes/shoes_socks_textres/trainers_white.png'), (120, 128, 140)),
              tint(T('clothes/shirt_tshirt_textures/tshirt_white.png'), (150, 60, 52))]:
      body.alpha_composite(layer)
  body = dirty(body, 0.55, 0.5)
  b = np.asarray(body).copy()
  for mk in ['Chest', 'Waist', 'LeftArm', 'RightArm', 'Belt', 'Crotch', 'LeftLeg', 'RightLeg']:
      ma = np.asarray(T(f'Body/Masks/{mk}.png'))[..., 3] > 127
      b[ma, 3] = 0
  Image.fromarray(b).save(f'{out}/body{lvl}.png')
dirty(T('clothes/trousers_mesh/trousersmesh_denim.png')).save(f'{out}/trousers.png')
dirty(T('clothes/jacket/jacket_leather_black.png'), 0.35, 0.45).save(f'{out}/jacket.png')
hair = tint(T('F_Hair_White.png'), (70, 56, 44))
overlay(hair, grime.resize(hair.size), 0.5).save(f'{out}/hair.png')
print('ok')
