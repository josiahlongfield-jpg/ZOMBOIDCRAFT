"""Paint Tomb's dark underwear set onto every skin (same UV layout). Coverage = Tomb's set plus Zomboid's own bra/briefs area, grown.
usage: python3 -I tombuw.py <S>"""
import sys
import numpy as np
from PIL import Image, ImageFilter
S = sys.argv[1]; B = f'{S}/pzdata/media/textures/Body'; M = f'{S}/pzmods/108600'; O = f'{S}/pzout'
R = 512
def vmask(tex):
    v = np.asarray(Image.open(f'{B}/{tex}').convert('RGBA')).astype(int)
    uw = (v[..., 2] > v[..., 0] + 8) & (v[..., 3] > 0)
    fill = uw.copy()
    for lo, hi in ((0, 128), (128, 256)):
        for x in range(256):
            ys = np.nonzero(uw[lo:hi, x])[0]
            if len(ys): fill[lo + ys.min():lo + ys.max() + 1, x] = True
    return Image.fromarray((fill * 255).astype('uint8')).resize((R, R), Image.NEAREST)
arm = np.asarray(Image.open(f'{B}/Masks/LeftArm.png').convert('RGBA').resize((R, R)))[..., 3] > 128
for g, tex in (('f', 'FemaleBody01.png'), ('m', 'MaleBody01.png')):
    G = 'Female' if g == 'f' else 'Male'
    tomb = np.asarray(Image.open(f"{M}/3429790870/mods/Tomb's Player Body - Textures/media/textures/Body/{G}Body01.png").convert('RGBA')).astype(float)
    vm = vmask(tex)
    van_grow = np.asarray(vm.filter(ImageFilter.MaxFilter(7))) > 0            # Zomboid's area + 3px
    near = np.asarray(vm.filter(ImageFilter.MaxFilter(41))) > 0               # where Tomb's set can be
    skin = np.median(tomb[arm][:, :3], 0)
    lum = tomb[..., :3].mean(2); dark = (lum < skin.mean() * 0.62) & near
    dark = np.asarray(Image.fromarray((dark * 255).astype('uint8')).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))) > 0
    mask = np.asarray(Image.fromarray((dark * 255).astype('uint8')).filter(ImageFilter.MaxFilter(5))) > 0   # Tomb's own set, 2px safety rim
    avg = tomb[dark][:, :3].mean(0)
    lay = tomb.copy(); fillpx = mask & ~dark; lay[fillpx, :3] = avg; lay[..., 3] = mask * 255
    layer = Image.fromarray(lay.clip(0, 255).astype('uint8'))
    print(g, 'tomb set px', dark.sum(), 'final', mask.sum(), 'filled', fillpx.sum(), 'avg', avg.round())
    srcs = [('van', f'{B}/{tex}')]
    if g == 'f':
        srcs += [('detail', f'{M}/3578569664/mods/DetailedPlayerTextureFemaleB42/42/media/textures/Body/FemaleBody01.png'),
                 ('ink', f'{M}/3408780732/mods/Evils Ink/common/media/textures/Body/FemaleBody01.png')]
    srcs += [('goth', f"{M}/3531765630/mods/Tomb's Player Body - Goth Textures/media/textures/Body/{G}Body01.png"),
             ('tomb', f"{M}/3429790870/mods/Tomb's Player Body - Textures/media/textures/Body/{G}Body01.png")]
    for k, p in srcs:
        im = Image.open(p).convert('RGBA')
        if k in ('tomb', 'goth'):            # their own set already: keep as is
            im.save(f'{O}/bk_{g}_{k}.png', optimize=True); continue
        if k == 'van':                       # Zomboid's blue set -> skin, by normalised blur of the surrounding skin
            v = np.asarray(im.resize((R, R), Image.BICUBIC)).astype(float)
            blue = van_grow & (v[..., 3] > 0)          # Zomboid's whole bra/briefs area (incl. its white trim), grown 3px
            ok = (~blue) & (v[..., 3] > 0); out = v.copy()
            for rad in (2, 4, 8, 16):
                w = np.asarray(Image.fromarray((ok * 255).astype('uint8')).filter(ImageFilter.GaussianBlur(rad))).astype(float)
                cs = [np.asarray(Image.fromarray((v[..., c] * ok).astype('uint8')).filter(ImageFilter.GaussianBlur(rad))).astype(float) for c in range(3)]
                fillm = blue & (w > 8) & ~ok
                for c in range(3): out[..., c][fillm] = cs[c][fillm] / w[fillm] * 255
                ok = ok | fillm
            im = Image.fromarray(out.clip(0, 255).astype('uint8'))
        size = max(R, min(1024, im.size[0]))
        im = im.resize((size, size), Image.BICUBIC if im.size[0] < size else Image.LANCZOS)
        L = layer.resize((size, size), Image.NEAREST if size == R else Image.BILINEAR)
        a = np.asarray(L).copy(); a[..., 3] = np.where(a[..., 3] > 20, 255, 0)
        im.alpha_composite(Image.fromarray(a))
        im.save(f'{O}/bk_{g}_{k}.png', optimize=True)
