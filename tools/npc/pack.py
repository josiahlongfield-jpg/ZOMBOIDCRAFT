"""Builds the NPC model files the mod ships from bought character models.

The source models must never be committed (the repo is public and their licences forbid
redistributing them). Keep them in the project's files (zomboid-mod/assets/<pack>/) and run

    python3 tools/npc/pack.py ID SOURCE.glb

which writes src/main/resources/assets/zomboidcraft/npc/<ID>.znpc with two looks of the one mesh:
    raw   the model's own texture, as bought
    toon  the texture flattened into paint (mode filter, posterised, the muted character grade);
          the game adds three light bands and an ink outline (NpcRenderer)

A .znpc is zlib-deflated, then XORed with an xorshift32 stream seeded from the id (like a .zcar),
so the file in the jar isn't a usable model on its own. Deflated payload, big-endian:
    "ZNPC" int version (1), int vertices, then per vertex
        short x,y,z (mm; feet at y=0, facing +z), char u,v (0-65535), byte nx,ny,nz (-127..127)
    int triangles, then 3 * char vertex index each (counter-clockwise seen from outside)
    int looks, then per look: UTF name, int jpegLength, jpeg bytes
"""
import io, json, os, struct, sys, zlib
import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HEIGHT = 1.85  # metres, top of the head (or hair) to the soles
TEX = 1024


def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def scramble(data, model_id):
    """XOR with xorshift32; NpcAssets.java undoes it."""
    x = (0x3C6EF372 ^ java_hash(model_id)) & 0xFFFFFFFF or 1
    key = bytearray()
    for _ in range((len(data) + 3) // 4):
        x ^= (x << 13) & 0xFFFFFFFF; x ^= x >> 17; x ^= (x << 5) & 0xFFFFFFFF
        key += x.to_bytes(4, 'big')
    return bytes(np.frombuffer(data, np.uint8) ^ np.frombuffer(bytes(key[:len(data)]), np.uint8))


def read_glb(path):
    d = open(path, 'rb').read()
    jl = struct.unpack('<I', d[12:16])[0]
    j = json.loads(d[20:20 + jl])
    b = d[20 + jl + 8:]

    def acc(i):
        a = j['accessors'][i]; bv = j['bufferViews'][a['bufferView']]
        n = {'VEC3': 3, 'VEC2': 2, 'SCALAR': 1}[a['type']]
        dt = np.dtype({5126: np.float32, 5123: np.uint16, 5125: np.uint32}[a['componentType']])
        off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        stride = bv.get('byteStride', 0) or n * dt.itemsize
        raw = np.frombuffer(b, np.uint8, (a['count'] - 1) * stride + n * dt.itemsize, off)
        rows = np.lib.stride_tricks.as_strided(raw, (a['count'], n * dt.itemsize), (stride, 1))
        return rows.copy().view(dt).reshape(a['count'], n)

    if len(j['meshes']) != 1 or len(j['meshes'][0]['primitives']) != 1:
        sys.exit('expected one mesh with one primitive')
    p = j['meshes'][0]['primitives'][0]
    img = j['images'][j['textures'][j['materials'][p['material']]['pbrMetallicRoughness']['baseColorTexture']['index']]['source']]
    bv = j['bufferViews'][img['bufferView']]
    tex = Image.open(io.BytesIO(b[bv.get('byteOffset', 0):bv.get('byteOffset', 0) + bv['byteLength']]))
    return (acc(p['attributes']['POSITION']).astype(np.float64), acc(p['attributes']['NORMAL']).astype(np.float64),
            acc(p['attributes']['TEXCOORD_0']).astype(np.float64), acc(p['indices']).reshape(-1, 3).astype(np.int64), tex)


def dilate(img):
    """Spreads each UV island's colour into the empty texture round it, so filtering never pulls in black."""
    rgba = np.asarray(img.convert('RGBA'), np.float64)
    rgb, have = rgba[..., :3].copy(), rgba[..., 3] > 127
    for _ in range(24):
        if have.all():
            break
        acc = np.zeros_like(rgb); cnt = np.zeros(have.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            acc += np.roll(np.roll(rgb * have[..., None], dy, 0), dx, 1)
            cnt += np.roll(np.roll(have, dy, 0), dx, 1)
        grow = ~have & (cnt > 0)
        rgb[grow] = acc[grow] / cnt[grow][:, None]
        have = have | grow
    rgb[~have] = rgb[have].mean(0)
    return Image.fromarray(rgb.clip(0, 255).astype(np.uint8))


def toon(img):
    """The paint pass: flat patches, ten levels a channel, then the characters' grade
    (desaturated about 45%, pulled toward sooty brown, near-black lifted to charcoal)."""
    flat = img.filter(ImageFilter.ModeFilter(7))
    c = np.asarray(flat, np.float64) / 255
    c = np.round(c * 10) / 10
    g = c.mean(2, keepdims=True)
    c = c * 0.55 + g * 0.45
    c = c * 0.82 + np.array([0.12, 0.105, 0.08]) * 0.18
    lum = c.mean(2, keepdims=True)
    c = np.where(lum < 0.3, c + (0.3 - lum) * 0.33, c)
    return Image.fromarray((c.clip(0, 1) * 255).astype(np.uint8))


def jpeg(img):
    out = io.BytesIO()
    img.save(out, 'JPEG', quality=90, optimize=True)
    return out.getvalue()


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    model_id, src = sys.argv[1], sys.argv[2]
    P, N, UV, I, tex = read_glb(src)
    P[:, 1] -= P[:, 1].min()
    P *= HEIGHT / P[:, 1].max()
    P[:, 0] -= (P[:, 0].min() + P[:, 0].max()) / 2
    P[:, 2] -= (P[:, 2].min() + P[:, 2].max()) / 2
    N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-9
    if len(P) > 65535:
        sys.exit('too many vertices for char indices')
    base = dilate(tex).resize((TEX, TEX), Image.LANCZOS)
    looks = [('raw', jpeg(base)), ('toon', jpeg(toon(base)))]

    out = io.BytesIO()
    out.write(b'ZNPC'); out.write(struct.pack('>ii', 1, len(P)))
    verts = np.zeros(len(P), dtype=[('p', '>i2', 3), ('uv', '>u2', 2), ('n', 'i1', 3)])
    verts['p'] = np.round(P * 1000).astype(np.int16)
    verts['uv'] = np.round(UV.clip(0, 1) * 65535).astype(np.uint16)
    verts['n'] = np.round(N * 127).astype(np.int8)
    out.write(verts.tobytes())
    out.write(struct.pack('>i', len(I))); out.write(I.astype('>u2').tobytes())
    out.write(struct.pack('>i', len(looks)))
    for name, data in looks:
        nb = name.encode()
        out.write(struct.pack('>H', len(nb)) + nb + struct.pack('>i', len(data)) + data)
    packed = scramble(zlib.compress(out.getvalue(), 9), model_id)
    dest = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/npc', model_id + '.znpc')
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    open(dest, 'wb').write(packed)
    print(f'{model_id}: {len(P)} vertices, {len(I)} triangles, {len(packed) // 1024} KB -> {dest}')
    if os.environ.get('NPC_PREVIEW'):
        for name, data in looks:
            Image.open(io.BytesIO(data)).save(os.path.join(os.environ['NPC_PREVIEW'], f'{model_id}_{name}.png'))


if __name__ == '__main__':
    main()
