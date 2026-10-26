# Pack the exported zombie meshes, kit atlases, per-type skins and eye masks into the mod's resources.
import json, struct, shutil, numpy as np
from pathlib import Path
from PIL import Image
R = Path('/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft')
SK = Path('/mnt/project-files/zomboid-mod/skin-template/ai-skin-15')
O = 'civilian construction doctor farmer firefighter hoodie jogger nurse office patient police prisoner security soldier survivor'.split()
for d in ('zbody', 'textures/entity/zbody/skin', 'textures/entity/zbody/kit', 'textures/entity/zbody/eyes'):
    (R / d).mkdir(parents=True, exist_ok=True)
tot = 0
for o in O:
    d = json.load(open(f'out/{o}.json'))
    b = bytearray(b'ZBD1'); b += struct.pack('<i', len(d['bones']))
    for bn in d['bones']:
        b += struct.pack('<b3f', bn['parent'], *bn['pos'])
    qs = [(q, 8) for q in d['body']] + [(q, q['small'] | q['stump'] << 1 | q['cut'] << 2) for q in d['kit']]
    b += struct.pack('<i', len(qs))
    for q, f in qs:
        b += struct.pack('<BB12f3f4f', q['b'], f, *q['p'], *q['n'], *q['uv'])
    (R / 'zbody' / f'{o}.bin').write_bytes(b); tot += len(b)
    Image.open(f'out/{o}.png').save(R / 'textures/entity/zbody/kit' / f'{o}.png', optimize=True)
    skin = Image.open(SK / f'{o}_1024.png').convert('RGBA'); skin.save(R / 'textures/entity/zbody/skin' / f'{o}.png', optimize=True)
    # eye mask: pale unsaturated pixels in the eye band of the face front, burning red (hot centre, darker rim)
    f = np.array(skin.crop((128, 128, 256, 256))).astype(int); N = 128
    mn, mx = f[..., :3].min(-1), f[..., :3].max(-1); yy = np.arange(N)[:, None]
    eye = (yy > N * .25) & (yy < N * .6) & (mn > 165) & (mx - mn < 50) & (mx > 195)
    out = np.zeros((N, N, 4), np.uint8); t = 0.75 + 0.25 * mn / 255
    out[eye] = np.stack([255 * t, 38 * t, 22 * t, np.full_like(t, 255)], -1)[eye].astype(np.uint8)
    Image.fromarray(out).save(R / 'textures/entity/zbody/eyes' / f'{o}.png', optimize=True)
    print(o, len(qs), int(eye.sum()))
print('bin total', tot // 1024, 'KB')
