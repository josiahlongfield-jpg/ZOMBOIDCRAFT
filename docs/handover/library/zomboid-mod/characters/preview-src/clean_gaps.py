# fill white seams/edges (where the AI's blocks are narrower than the template) by growing in neighbouring colour;
# the face front is left alone so the eye whites survive
from PIL import Image
import numpy as np, sys
for f in sys.argv[1:]:
    a = np.array(Image.open(f).convert('RGBA')).astype(float)
    op = a[..., 3] > 0
    w = (a[..., :3].min(-1) > 215) & op; w[128:256, 128:256] = False; n0 = int(w.sum())
    for _ in range(80):
        if not w.any(): break
        good = op & ~w; acc = np.zeros(a.shape[:2] + (3,)); cnt = np.zeros(a.shape[:2])
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            g = np.roll(good, (dy, dx), (0, 1)); c = np.roll(a[..., :3], (dy, dx), (0, 1))
            acc += c * g[..., None]; cnt += g
        fill = w & (cnt > 0)
        a[fill, :3] = acc[fill] / cnt[fill][:, None]; w &= ~fill
    out = Image.fromarray(a.round().astype('uint8')); out.save(f)
    for r in (256, 64): out.resize((r, r), Image.LANCZOS).save(f.replace('_1024', f'_{r}'))
    print(f.split('/')[-1], n0, int(w.sum()))
