"""Builds the NPC model files the mod ships from bought character models.

The source models must never be committed (the repo is public and their licences forbid
redistributing them). Keep them in the project's files (zomboid-mod/assets/<pack>/) and run

    python3 tools/npc/pack.py ID SOURCE.glb|SOURCE.obj [--height M] [--out DIR]

which writes src/main/resources/assets/zomboidcraft/npc/<ID>.znpc (or DIR/<ID>.znpc) with two looks of
the one mesh:
    raw   the model's own texture, as bought
    toon  the texture flattened into paint (mode filter, posterised, the muted character grade);
          the game adds three light bands and an ink outline (NpcRenderer)

An OBJ's materials (one map_Kd each, looked up by file name under the OBJ's folder) are packed into one
texture atlas. Models of characters someone else owns go out with --out to a folder outside the repo; the
player copies the file into config/zomboidcraft/npc/ and the game loads it from there (NpcAssets).

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
HEIGHT = 1.85  # metres, top of the head (or hair) to the soles, unless --height says otherwise
TEX = 1024  # glb textures; OBJ atlases stay 2048 (many materials share them)


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


def read_obj(path):
    """Triangles from a Wavefront OBJ, every corner its own (position, uv, normal), materials tiled into one atlas."""
    folder = os.path.dirname(os.path.abspath(path))
    vs, vts, vns, corners, mats = [], [], [], [], []
    names, mat = [], None
    for line in open(path, errors='replace'):
        t = line.split()
        if not t:
            continue
        if t[0] == 'v':
            vs.append([float(x) for x in t[1:4]])
        elif t[0] == 'vt':
            vts.append([float(x) for x in t[1:3]])
        elif t[0] == 'vn':
            vns.append([float(x) for x in t[1:4]])
        elif t[0] == 'usemtl':
            mat = t[1]
            if mat not in names:
                names.append(mat)
        elif t[0] == 'f':
            c = [tuple(int(x) - 1 if x else -1 for x in (k.split('/') + ['', ''])[:3]) for k in t[1:]]
            for i in range(1, len(c) - 1):
                corners.append((c[0], c[i], c[i + 1]))
                mats.append(names.index(mat))
    maps = {}
    mtl = os.path.splitext(path)[0] + '.mtl'
    cur = None
    for line in open(mtl, errors='replace'):
        t = line.split(None, 1)
        if len(t) == 2 and t[0] == 'newmtl':
            cur = t[1].strip()
        elif len(t) == 2 and t[0] == 'map_Kd':
            want = os.path.basename(t[1].strip().replace('\\', '/'))
            for root, _, files in os.walk(folder):
                if want in files:
                    maps[cur] = os.path.join(root, want)
    V, T, N = np.array(vs), np.array(vts), np.array(vns)
    C = np.array(corners)  # tris x 3 corners x (v, vt, vn)
    tiles = int(np.ceil(np.sqrt(len(names))))
    tile = 2048 // tiles
    atlas = Image.new('RGB', (tile * tiles, tile * tiles), (128, 128, 128))
    for i, name in enumerate(names):
        if name in maps:
            atlas.paste(Image.open(maps[name]).convert('RGB').resize((tile, tile), Image.LANCZOS), (i % tiles * tile, i // tiles * tile))
        else:
            print('no texture for', name)
    uv = T[C[..., 1]]  # tris x 3 x 2, OBJ v runs up
    uv -= np.floor(uv.mean(1, keepdims=True))  # tiled uvs back into 0..1 per triangle
    uv = uv.clip(0.002, 0.998)
    m = np.array(mats)[:, None]
    u = (m % tiles + uv[..., 0]) / tiles
    v = (m // tiles + 1 - uv[..., 1]) / tiles
    P = V[C[..., 0]].reshape(-1, 3)
    NN = N[C[..., 2]].reshape(-1, 3) if len(N) else np.tile([0, 1, 0], (len(P), 1))
    UV = np.stack([u, v], -1).reshape(-1, 2)
    # weld corners that share position, uv and normal
    key = np.concatenate([np.round(P * 1e4), np.round(UV * 1e5), np.round(NN * 100)], 1)
    _, first, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    return P[first].astype(np.float64), NN[first].astype(np.float64), UV[first], inv.reshape(-1, 3), atlas


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
    args = sys.argv[1:]
    opts = {}
    for flag in ('--height', '--out'):
        if flag in args:
            k = args.index(flag)
            opts[flag] = args[k + 1]
            del args[k:k + 2]
    if len(args) != 2:
        sys.exit(__doc__)
    model_id, src = args
    P, N, UV, I, tex = read_obj(src) if src.lower().endswith('.obj') else read_glb(src)
    P[:, 1] -= P[:, 1].min()
    P *= float(opts.get('--height', HEIGHT)) / P[:, 1].max()
    P[:, 0] -= (P[:, 0].min() + P[:, 0].max()) / 2
    P[:, 2] -= (P[:, 2].min() + P[:, 2].max()) / 2
    N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-9
    if len(P) > 65535:
        sys.exit('too many vertices for char indices')
    base = dilate(tex)
    if base.size[0] > 2048 or src.lower().endswith('.glb'):
        base = base.resize((TEX, TEX), Image.LANCZOS)
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
    dest = os.path.join(opts.get('--out') or os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/npc'), model_id + '.znpc')
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    open(dest, 'wb').write(packed)
    print(f'{model_id}: {len(P)} vertices, {len(I)} triangles, {len(packed) // 1024} KB -> {dest}')
    if os.environ.get('NPC_PREVIEW'):
        for name, data in looks:
            Image.open(io.BytesIO(data)).save(os.path.join(os.environ['NPC_PREVIEW'], f'{model_id}_{name}.png'))


if __name__ == '__main__':
    main()
