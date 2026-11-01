"""Zomboid's roughing-up for the zombie packs: blood, grime and torn holes, one set per body part.

Zomboid paints a zombie's blood and dirt per body part, from its tiled overlays (textures/BloodTextures/BloodOverlay.png
and GrimeOverlay.png), and tears holes in its clothes where it's been hurt. Its clothes share the body's UV layout, so
the part masks (textures/Body/Masks/<Part>.png) mark the same place on the skin and on every garment. These go into
the zombie packs (never the repo or jar: they're made from Zomboid's files) as:

    zfx/blood1/<Part>, zfx/blood2/<Part>   blood on that part, light and soaked (laid over)
    zfx/grime/<Part>                       dirt on that part (laid over)
    zfx/hole/<Part>                        ragged holes on that part, graded white at their hearts (cut from a garment,
                                           wider the more worn; on the body the skin shows back through: REVEAL)

ZedBodies rolls which parts get which, per zombie.

    python3 -I tools/pz/zombie_fx.py PACK.zsk OUT.zsk MEDIA_ROOT...   adds them to a pack already built
"""
import io, os, struct, sys, zlib
import numpy as np
from PIL import Image, ImageFilter  # noqa: F401

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'npc'))
from pack import scramble  # noqa: E402

PARTS = ['Head', 'Chest', 'Waist', 'Belt', 'Crotch', 'LeftArm', 'RightArm', 'LeftLeg', 'RightLeg']
SIZE = 512


def find(roots, rel):
    for r in roots:
        p = os.path.join(r, rel)
        if os.path.exists(p):
            return p
    return None


def png(img):
    b = io.BytesIO(); img.save(b, 'PNG', optimize=True); return b.getvalue()


def tiled(path, reps):
    """An overlay tiled reps times across the UV square, as Zomboid's shader repeats it."""
    t = Image.open(path).convert('RGBA')
    big = Image.new('RGBA', (t.width * reps, t.height * reps))
    for y in range(reps):
        for x in range(reps):
            big.paste(t, (x * t.width, y * t.height))
    return np.asarray(big.resize((SIZE, SIZE), Image.LANCZOS)).astype(np.float32)


def holes(seed):
    """Ragged tears, graded: white at the heart of each, greying to its edge. A weak cut (a lightly worn zombie) opens
    only the whitest, small rips; a full one opens them wide (SurvivorTextures cuts where it's whiter than 382 less the
    layer's strength, so 255 cuts past the middle grey)."""
    r = np.random.default_rng(seed)
    f = np.zeros((SIZE, SIZE), np.float32)
    for octave, w in ((16, 1.0), (48, 0.45), (128, 0.2)):
        n = Image.fromarray((r.random((octave, octave)) * 255).astype(np.uint8)).resize((SIZE, SIZE), Image.BICUBIC)
        f += np.asarray(n, np.float32) / 255 * w
    f /= 1.65
    return ((f - 0.62) / 0.26).clip(0, 1) * 255


def textures(roots):
    blood_p = find(roots, 'textures/BloodTextures/BloodOverlay.png')
    grime_p = find(roots, 'textures/BloodTextures/GrimeOverlay.png')
    if not blood_p or not grime_p:
        print('  no blood or grime overlay (is the base game media given?)')
        return {}
    # both are laid out like the body's own texture: blood's red channel says where blood gathers first (Zomboid's
    # shader lets more of it through the more a part bleeds), grime's alpha where the dirt sits
    raw = tiled(blood_p, 1)
    r = raw[..., 0] / max(1.0, np.percentile(raw[..., 0], 99))
    blood = np.zeros_like(raw)
    blood[..., 0] = 58 + 42 * r.clip(0, 1); blood[..., 1] = 12 + 8 * r.clip(0, 1); blood[..., 2] = 9
    grime = tiled(grime_p, 1)
    out = {}
    for i, part in enumerate(PARTS):
        mp = find(roots, f'textures/Body/Masks/{part}.png')
        if not mp:
            print(f'  no body part mask {part}'); continue
        mask = np.asarray(Image.open(mp).convert('L').resize((SIZE, SIZE), Image.NEAREST), np.float32) / 255
        mask = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), np.float32) / 255
        # k: for blood, how far down the red channel it reaches (streaks first, then the wider stains); for grime, how thick
        for key, src, k in (('blood1', blood, 0.55), ('blood2', blood, 0.3), ('grime', grime, 0.45)):
            a = src.copy()
            cover = ((r - k) / 0.25).clip(0, 0.92) if key != 'grime' else src[..., 3] / 255 * k
            a[..., 3] = 255 * cover * mask
            out[f'zfx/{key}/{part}'] = png(Image.fromarray(a.clip(0, 255).astype(np.uint8)))
        h = holes(1000 + i) * (mask > 0.5)
        out[f'zfx/hole/{part}'] = png(Image.fromarray(h.astype(np.uint8)).convert('RGBA'))
    print(f'  roughing-up: {len(out)} textures')
    return out


def patch(src, dst, roots):
    """Adds the textures to a built pack, leaving everything else as it was."""
    mid_in, mid_out = os.path.basename(src)[:-4], os.path.basename(dst)[:-4]
    raw = zlib.decompress(scramble(open(src, 'rb').read(), mid_in))
    b = io.BytesIO(raw)
    u = lambda f: struct.unpack('>' + f, b.read(struct.calcsize('>' + f)))
    utf = lambda: b.read(u('H')[0]).decode()
    assert b.read(4) == b'ZSKN' and u('i')[0] == 1
    for _ in range(u('i')[0]):
        utf(); utf(); utf()
        _, _, n = u('bbi'); b.seek(n * 27, 1)
        b.seek(u('i')[0] * 12, 1)
    at = b.tell()
    tex = {}
    for _ in range(u('i')[0]):
        k = utf(); tex[k] = b.read(u('i')[0])
    rest = b.read()
    tex.update(textures(roots))
    out = io.BytesIO()
    out.write(raw[:at])
    out.write(struct.pack('>i', len(tex)))
    for key, data in tex.items():
        kb = key.encode(); out.write(struct.pack('>H', len(kb)) + kb + struct.pack('>i', len(data)) + data)
    out.write(rest)
    open(dst, 'wb').write(scramble(zlib.compress(out.getvalue(), 9), mid_out))
    print(f'{dst}: {len(tex)} textures, {os.path.getsize(dst) // 1024} KB')


if __name__ == '__main__':
    patch(sys.argv[1], sys.argv[2], sys.argv[3:])
