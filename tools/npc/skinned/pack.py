"""Packs a Mixamo-rigged character (with its clips) into a .zsk the game skins and animates (client NpcSkin).

    node tools/npc/skinned/run.js export.html mix.json text   (headless Chromium: the FBX files -> mix.json, see export.html)
    python3 tools/npc/skinned/pack.py ID mix.json SPEC.json OUT_DIR [face.json]

mix.json holds the Mixamo bind pose (metres, feet on y=0, facing +z, x her left), each mesh's skin and every clip
sampled at 30 fps (each bone's model-space position and turn). SPEC.json says which meshes to keep, the piece each
belongs to (pieces can be put on and taken off in game), its texture, whether it's drawn both sides, how much of it
to keep near and far (lods), and how far (m) clothes stand off the skin (push), and optionally one bone a stiff piece rides whole (bone, e.g. "mixamorigHead"). A mesh with several materials arrives split as "<mesh>.<material>". face.json (optional) is the original model's face rig, dumped by the page that posed her for Mixamo (kept with her
files in the project's storage, zomboid-mod/assets/woman/tools/): per vertex
how much follows the jaw and each top eyelid, and their pivots. Mixamo keeps only the body's bones, so those three
come back here as extra bones under the head, turned by the game (talking, blinking).

Characters someone else owns never go in the repo or the jar: write OUT_DIR outside it and copy ID.zsk into
config/zomboidcraft/npc/ on the PC that plays.

.zsk: zlib-deflated, then XORed with the xorshift32 stream seeded from the id (as a .znpc). Big-endian payload:
    "ZSKN" int 1
    int groups, per group: UTF mesh, UTF piece, UTF texture, byte flags (1 both sides, 2 cut-out alpha), byte level
        (0 near, full detail; 1 far, thinned),
        int vertices, per vertex: float x,y,z, byte nx,ny,nz, char u,v, byte 4 bones, byte 4 weights (0-255)
        int triangles, 3 * int index each (counter-clockwise from outside)
    int textures, per texture: UTF key, int length, PNG or JPEG bytes
    int bones, per bone: UTF name, byte parent (-1 root), float 12 (inverse bind, rows of a 3x4: model -> bone)
    int face bones, per: UTF name ("jaw", "lid_l", "lid_r"), byte bone index, float pivot x,y,z
    int clips, per clip: UTF name, int frames, float fps, int bones in the clip (the first N),
        per frame per bone: short qx,qy,qz,qw (x32767), short x,y,z (mm), model space
"""
import io, json, base64, os, struct, sys, zlib
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pack import scramble  # noqa: E402  (same scramble as a .znpc)


def load_mix(path):
    J = json.load(open(path))
    dec = lambda s, t: np.frombuffer(base64.b64decode(s), t)
    for m in J['meshes']:
        n = m['n']
        m['pos'] = dec(m['pos'], np.float32).reshape(n, 3).astype(np.float64)
        m['normal'] = dec(m['normal'], np.float32).reshape(n, 3).astype(np.float64)
        m['uv'] = dec(m['uv'], np.float32).reshape(n, 2).astype(np.float64)
        m['si'] = dec(m['si'], np.uint8).reshape(n, 4).astype(np.int64)
        m['sw'] = dec(m['sw'], np.float32).reshape(n, 4).astype(np.float64)
        m['ix'] = dec(m['ix'], np.uint32).reshape(-1, 3).astype(np.int64)
    J['inv'] = [None if a is None else np.array(a).reshape(4, 4).T for a in J['inv']]
    for c in J['clips'].values():
        c['data'] = dec(c['data'], np.float32).reshape(c['frames'], len(J['bones']), 7).astype(np.float64)
    return J


def thin(m, keep):
    """Quadric decimation down to about `keep` of the triangles; each kept vertex takes the attributes of one it absorbed."""
    if keep >= 0.999:
        return m
    import fast_simplification as fs
    P, F = m['pos'].astype(np.float32), m['ix'].astype(np.int32)
    _, _, coll = fs.simplify(P, F, target_reduction=1 - keep, return_collapses=True)
    p2, f2, mp = fs.replay_simplification(P, F, coll)
    _, rep = np.unique(mp, return_index=True)
    out = dict(m)
    out['pos'] = p2.astype(np.float64)
    for k in ('normal', 'uv', 'si', 'sw'):
        out[k] = m[k][rep]
    out['ix'] = f2.astype(np.int64)
    # smooth normals from the thinned surface, but keep the original's where it points the same general way
    n = np.zeros_like(out['pos'])
    tri = out['pos'][out['ix']]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    for k in range(3):
        np.add.at(n, out['ix'][:, k], fn)
    n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    out['normal'] = np.where((n * out['normal']).sum(1, keepdims=True) > 0.3, out['normal'], n)
    out['n'] = len(out['pos'])
    return out


def add_face(m, face, base):
    """Mixes the face rig's weights (jaw, lid_l, lid_r = bones base..base+2) into a mesh's Mixamo skin."""
    f = face['meshes'].get(m['name']) or face['meshes'].get(m['name'].split('.')[0])
    if not f or not f['idx']:
        return
    from scipy.spatial import cKDTree
    fp = np.array(f['pos'])
    d, j = cKDTree(fp).query(m['pos'])
    hit = d < 2e-4
    if hit.sum() < 0.5 * len(fp) and f.get('box'):
        # dumped from Blender (face_dump.py): the same mesh, but Mixamo may have moved or scaled it a little, so line the
        # two up by the whole mesh's box before matching
        lo, hi = np.array(f['box'][0]), np.array(f['box'][1])
        mlo, mhi = m['pos'].min(0), m['pos'].max(0)
        fp = (fp - lo) / np.maximum(hi - lo, 1e-9) * (mhi - mlo) + mlo
        d, j = cKDTree(fp).query(m['pos'])
        hit = d < max(2e-4, 1e-3 * float((mhi - mlo).max()))
        if len(fp) > face.get('_fit_n', 0):
            # the pivots move the same way, by the mesh with the most of the face on it
            face['_fit'], face['_fit_n'] = (lo, hi, mlo, mhi), len(fp)
    if hit.sum() < 0.5 * len(f['pos']):
        print(f"  WARNING {m['name']}: only {hit.sum()} of {len(f['pos'])} face vertices found; was face.json made from"
              " the same pose you uploaded to Mixamo?")
    fw = np.zeros((m['n'], 3))
    fw[hit] = np.array(f['w'])[j[hit]]
    total = fw.sum(1).clip(0, 1)
    idx = np.concatenate([m['si'], base + np.tile(np.arange(3), (m['n'], 1))], 1)
    w = np.concatenate([m['sw'] * (1 - total)[:, None], fw], 1)
    order = np.argsort(-w, 1)[:, :4]
    m['si'] = np.take_along_axis(idx, order, 1)
    m['sw'] = np.take_along_axis(w, order, 1)
    print(f"  {m['name']}: {hit.sum()} vertices on the face rig")


def image_bytes(path, size, alpha):
    img = Image.open(path)
    img = img.convert('RGBA' if alpha else 'RGB')
    if max(img.size) > size:
        img = img.resize((size, size * img.size[1] // img.size[0]), Image.LANCZOS)
    out = io.BytesIO()
    if alpha:
        img.save(out, 'PNG', optimize=True)
    else:
        img.save(out, 'JPEG', quality=90, optimize=True)
    return out.getvalue()


def main():
    model_id, mix_path, spec_path, out_dir = sys.argv[1:5]
    face = json.load(open(sys.argv[5])) if len(sys.argv) > 5 else None
    J = load_mix(mix_path)
    spec = json.load(open(spec_path))
    root = os.path.dirname(os.path.abspath(spec_path))
    # bones that nothing is skinned to and no clip needs (Mixamo's *_End tips) are dropped
    keep = [i for i, a in enumerate(J['inv']) if a is not None]
    remap = {old: new for new, old in enumerate(keep)}
    names = [J['bones'][i] for i in keep]
    parent = []
    for i in keep:
        p = J['parent'][i]
        while p >= 0 and p not in remap:
            p = J['parent'][p]
        parent.append(remap.get(p, -1))
    nb = len(names)
    head = names.index('mixamorigHead')
    face_bones = []
    if face:
        for key, name in (('jaw', 'jaw'), ('eye_l', 'lid_l'), ('eye_r', 'lid_r')):
            face_bones.append((name, nb + len(face_bones), face['pivots'][key]))
    out = io.BytesIO()
    out.write(b'ZSKN' + struct.pack('>i', 1))
    groups = []
    for g, lod in [(g, lod) for lod in range(2) for g in spec['groups']]:
        m = next(x for x in J['meshes'] if x['name'] == g['mesh'])
        m = dict(m)
        m['si'] = np.vectorize(lambda i: remap.get(i, 0))(m['si'])
        if g.get('bone'):
            # a stiff piece (face, hair, hat) rides one bone whole, whatever Mixamo guessed for it
            m['si'] = np.zeros_like(m['si']); m['si'][:, 0] = names.index(g['bone'])
            m['sw'] = np.zeros_like(m['sw']); m['sw'][:, 0] = 1
        if face:
            add_face(m, face, nb)
        m = thin(m, g.get('lods', [1.0, 0.4])[lod])
        m['lod'] = lod
        if g.get('push'):
            # clothes stand a few mm off the (thinned) skin, so it never pokes through them
            nn = m['normal'] / (np.linalg.norm(m['normal'], axis=1, keepdims=True) + 1e-12)
            m['pos'] = m['pos'] + nn * g['push']
        groups.append((g, m))
    out.write(struct.pack('>i', len(groups)))
    tris = 0
    for g, m in groups:
        flags = (1 if g.get('both') else 0) | (2 if g.get('cutout') else 0)
        for s in (g['mesh'], g['piece'], g['texture']):
            b = s.encode(); out.write(struct.pack('>H', len(b)) + b)
        out.write(struct.pack('>bbi', flags, m['lod'], m['n']))
        w = m['sw'] / np.maximum(m['sw'].sum(1, keepdims=True), 1e-9)
        wb = np.floor(w * 255 + 0.5).astype(np.int64)
        wb[:, 0] += 255 - wb.sum(1)  # bytes that sum to exactly 255
        v = np.zeros(m['n'], dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('uv', '>u2', 2), ('j', 'u1', 4), ('w', 'u1', 4)])
        v['p'] = m['pos']
        nn = m['normal'] / (np.linalg.norm(m['normal'], axis=1, keepdims=True) + 1e-12)
        v['n'] = np.round(nn * 127).astype(np.int8)
        # three's FBX uvs run up the texture; Minecraft's run down
        v['uv'] = np.round(np.stack([m['uv'][:, 0], 1 - m['uv'][:, 1]], 1).clip(0, 1) * 65535).astype(np.uint16)
        v['j'] = m['si']
        v['w'] = wb.clip(0, 255)
        out.write(v.tobytes())
        out.write(struct.pack('>i', len(m['ix'])))
        out.write(m['ix'].astype('>i4').tobytes())
        tris += len(m['ix'])
        print(f"{g['mesh']:12s} {g['piece']:10s} lod {m['lod']} {m['n']:6d} verts {len(m['ix']):6d} tris")
    texs = spec['textures']
    out.write(struct.pack('>i', len(texs)))
    for key, t in texs.items():
        data = image_bytes(os.path.join(root, t['file']), t.get('size', 1024), t.get('alpha', False))
        b = key.encode(); out.write(struct.pack('>H', len(b)) + b + struct.pack('>i', len(data)) + data)
    all_names = names + [f[0] for f in face_bones]
    out.write(struct.pack('>i', len(all_names)))
    for i, n in enumerate(all_names):
        if i < nb:
            inv, par = J['inv'][keep[i]], parent[i]
        else:
            px, py, pz = face_bones[i - nb][2]
            if face.get('_fit'):
                lo, hi, mlo, mhi = face['_fit']
                px, py, pz = (np.array([px, py, pz]) - lo) / np.maximum(hi - lo, 1e-9) * (mhi - mlo) + mlo
            inv, par = np.array([[1, 0, 0, -px], [0, 1, 0, -py], [0, 0, 1, -pz], [0, 0, 0, 1.]]), head
        b = n.encode()
        out.write(struct.pack('>H', len(b)) + b + struct.pack('>b', par) + struct.pack('>12f', *inv[:3].reshape(-1)))
    out.write(struct.pack('>i', len(face_bones)))
    for name, idx, piv in face_bones:
        b = name.encode(); out.write(struct.pack('>H', len(b)) + b + struct.pack('>b3f', idx, *piv))
    clips = spec['clips']
    out.write(struct.pack('>i', len(clips)))
    for name, src in clips.items():
        c = J['clips'][src]
        d = c['data'][:, keep]
        q = d[:, :, 3:7]
        q = q * np.sign(q[:, :, 3:4] + 1e-12)  # w >= 0, so neighbouring frames don't flip
        b = name.encode()
        out.write(struct.pack('>H', len(b)) + b + struct.pack('>ifi', c['frames'], c['fps'], nb))
        rec = np.zeros((c['frames'], nb), dtype=[('q', '>i2', 4), ('p', '>i2', 3)])
        rec['q'] = np.round(q * 32767).astype(np.int16)
        rec['p'] = np.round(d[:, :, :3] * 1000).astype(np.int16)
        out.write(rec.tobytes())
    raw = out.getvalue()
    packed = scramble(zlib.compress(raw, 9), model_id)
    os.makedirs(out_dir, exist_ok=True)
    dest = os.path.join(out_dir, model_id + '.zsk')
    open(dest, 'wb').write(packed)
    print(f'{model_id}: {tris} triangles, {len(all_names)} bones, {len(clips)} clips, {len(packed) // 1024} KB -> {dest}')


if __name__ == '__main__':
    main()
