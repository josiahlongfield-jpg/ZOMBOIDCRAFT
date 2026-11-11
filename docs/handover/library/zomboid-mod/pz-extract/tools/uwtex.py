"""Full-body mod skins with Zomboid's own bra and briefs painted over (dilated so nothing shows at the edges).
usage: python3 -I uwtex.py <media> <mods> <outdir>"""
import sys
from PIL import Image, ImageFilter
import numpy as np
m, mods, out = sys.argv[1].rstrip('/'), sys.argv[2].rstrip('/'), sys.argv[3]
van = Image.open(f'{m}/textures/Body/FemaleBody01.png').convert('RGBA')
v = np.asarray(van).astype(int)
uw = (v[..., 2] > v[..., 0] + 8) & (v[..., 3] > 0)          # the light-blue underwear
# close gaps: per column, fill between the top and bottom of each underwear band (bra rows < 128, briefs below)
fill = uw.copy()
for lo, hi in ((0, 128), (128, 256)):
    band = uw[lo:hi]
    for x in range(256):
        ys = np.nonzero(band[:, x])[0]
        if len(ys): fill[lo + ys.min():lo + ys.max() + 1, x] = True
mask = Image.fromarray((fill * 255).astype('uint8')).filter(ImageFilter.MaxFilter(7))   # grow 3 px at 256
Image.fromarray(np.asarray(mask)).save(f'{out}/uwmask.png')
# underwear colour layer: vanilla pixels, holes in the grown rim filled with the average underwear colour
col = v[uw][:, :3].mean(0)
lay = v.copy(); lay[~uw, :3] = col; lay[..., 3] = np.asarray(mask)
layer = Image.fromarray(lay.clip(0, 255).astype('uint8'))
Image.fromarray(np.where(np.asarray(mask)[..., None] > 0, lay, 0).astype('uint8')).save(f'{out}/uwlayer.png')
for key, path in [('detail', '3578569664/mods/DetailedPlayerTextureFemaleB42/42/media/textures/Body/FemaleBody01.png'),
                  ('ink', '3408780732/mods/Evils Ink/common/media/textures/Body/FemaleBody01.png')]:
    im = Image.open(f'{mods}/{path}').convert('RGBA')
    if im.size[0] > 1024: im = im.resize((1024, 1024), Image.LANCZOS)
    L = layer.resize(im.size, Image.BILINEAR)
    a = np.asarray(L).astype(float); a[..., 3] = np.where(a[..., 3] > 20, 255, 0)   # hard edge, no see-through rim
    im.alpha_composite(Image.fromarray(a.astype('uint8')))
    im.save(f'{out}/f_uw_{key}.png')
van.save(f'{out}/f_uw_std.png')
Image.open(f'{m}/textures/Body/MaleBody01.png').convert('RGBA').save(f'{out}/m_uw.png')
print('ok', uw.sum())
