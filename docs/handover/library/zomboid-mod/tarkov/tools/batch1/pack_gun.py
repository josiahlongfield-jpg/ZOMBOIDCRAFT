"""(batch 1 copy) Packs the exported Tarkov operator (tp.json) or kitted M4A1 first-person rig (fp.json) into .zsk files the game's
NpcSkin reads (format in the mod's tools/npc/skinned/pack.py). Local/private only: output goes to the player's PC."""
import json, sys, struct, zlib, io, os, re
import numpy as np
from PIL import Image
from scipy.spatial.transform import Rotation as Rot
sys.path.insert(0, '/home/user/zomboidcraft/tools/npc')
from pack import scramble
D = '/tmp/claude-0/tk/web/data/'
S = np.diag([-1., 1, 1, 1])

def col(e): return np.array(e, float).reshape(4, 4).T          # three.js column-major -> 4x4
def rowm(e): return np.array(e, float).reshape(4, 4)            # row-major

def rd(h):
    b = open(D + h['file'], 'rb').read(); n = h['n']; o = 0
    def f(k):
        nonlocal o
        a = np.frombuffer(b, '<f4', n * k, o).reshape(n, k).astype(float); o += n * k * 4; return a
    P, N, U = f(3), f(3), f(2)
    SI = SW = None
    if h.get('skin'): SI, SW = f(4).astype(int), f(4)
    subs = []
    for c in h['subs']:
        subs.append(np.frombuffer(b, '<u4', c, o).astype(int).reshape(-1, 3)); o += c * 4
    return P, N, U, SI, SW, subs

TEX = {}
def tex(m, size=1024):
    """Texture key for a material (its _MainTex as JPEG, alpha dropped: Tarkov keeps gloss there)."""
    t = (m or {}).get('_MainTex')
    c = (m or {}).get('_Color') or [1, 1, 1, 1]
    if not t and ((m or {}).get('name') or '').lower().startswith('watch'):
        c = [0.11, 0.11, 0.12]      # the watch's own texture isn't in the bundles we have: a black watch, not a white one
    if not t:
        key = 'flat_%02x%02x%02x' % tuple(int(255 * min(1, x)) for x in c[:3])
        if key not in TEX:
            im = Image.new('RGB', (4, 4), tuple(int(255 * min(1, x)) for x in c[:3])); b = io.BytesIO(); im.save(b, 'PNG'); TEX[key] = b.getvalue()
        return key
    key = os.path.basename(t).rsplit('.', 1)[0].lower()
    if key not in TEX:
        im = Image.open(D + t).convert('RGB')
        if max(im.size) > size: im = im.resize((size, size * im.size[1] // im.size[0]), Image.LANCZOS)
        if c[:3] != [1, 1, 1] and any(abs(x - 1) > 0.02 for x in c[:3]):
            a = np.asarray(im).astype(float) * np.array(c[:3]); im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
        b = io.BytesIO(); im.save(b, 'JPEG', quality=90); TEX[key] = b.getvalue()
    return key

def glassy(m): return bool(re.search(r'glass|linza|lens|collimator|reflex|_outside', ((m or {}).get('name') or '').lower()))

GROUPS = []
def group(name, piece, key, P, N, U, J, W, T):
    """One zsk group; triangles whose uvs leave 0..1 are unwelded and shifted back into their tile."""
    if U.min() < -0.001 or U.max() > 1.001:
        idx = T.reshape(-1); P, N, U, J, W = P[idx], N[idx], U[idx].copy(), J[idx], W[idx]
        T = np.arange(len(idx)).reshape(-1, 3)
        tu = U.reshape(-1, 3, 2); off = np.floor(tu.min(1) + 1e-4)
        tu -= off[:, None, :]; U = np.clip(tu.reshape(-1, 2), 0, 1)
    else:
        used = np.unique(T); remap = -np.ones(len(P), int); remap[used] = np.arange(len(used))
        P, N, U, J, W = P[used], N[used], U[used], J[used], W[used]; T = remap[T]
    GROUPS.append((name, piece, key, P, N, U, J, W, T))

def wb(J, W):
    W = np.clip(W, 0, None); s = W.sum(1, keepdims=True); s[s == 0] = 1; W = W / s
    q = np.round(W * 255).astype(int); q[:, 0] += 255 - q.sum(1)
    return J.astype(int), np.clip(q, 0, 255)

def write(model, bones, parents, inv, clips, out):
    o = io.BytesIO(); w = o.write
    def utf(s): b = s.encode(); w(struct.pack('>H', len(b)) + b)
    w(b'ZSKN' + struct.pack('>i', 1)); w(struct.pack('>i', len(GROUPS)))
    for name, piece, key, P, N, U, J, W, T in GROUPS:
        utf(name); utf(piece); utf(key); w(struct.pack('>bbi', 0, 0, len(P)))
        nn = N / np.maximum(1e-9, np.linalg.norm(N, axis=1, keepdims=True))
        Jq, Wq = wb(J, W)
        rec = np.zeros(len(P), dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('u', '>u2', 2), ('j', 'i1', 4), ('w', 'u1', 4)])
        rec['p'] = P; rec['n'] = np.round(nn * 127); rec['u'] = np.round(np.clip(U, 0, 1) * 65535); rec['j'] = Jq; rec['w'] = Wq
        w(rec.tobytes()); w(struct.pack('>i', len(T))); w(T.astype('>i4').tobytes())
    w(struct.pack('>i', len(TEX)))
    for k, b in TEX.items(): utf(k); w(struct.pack('>i', len(b)) + b)
    w(struct.pack('>i', len(bones)))
    for i, nm in enumerate(bones):
        utf(nm); w(struct.pack('>b', parents[i]) + struct.pack('>12f', *inv[i][:3].reshape(-1)))
    w(struct.pack('>i', 0))
    w(struct.pack('>i', len(clips)))
    for name, (fps, frames) in clips.items():
        utf(name); w(struct.pack('>ifi', len(frames), fps, len(bones)))
        rec = np.zeros((len(frames), len(bones)), dtype=[('q', '>i2', 4), ('p', '>i2', 3)])
        for f, fr in enumerate(frames):
            for b, m in enumerate(fr):
                R = m[:3, :3]; sc = np.linalg.norm(R, axis=0); q = Rot.from_matrix(R / sc).as_quat()
                if q[3] < 0: q = -q
                rec[f, b]['q'] = np.round(q * 32767); rec[f, b]['p'] = np.clip(np.round(m[:3, 3] * 1000), -32767, 32767)
        w(rec.tobytes())
    raw = zlib.compress(o.getvalue(), 9)
    open(out, 'wb').write(scramble(raw, model)); print(out, len(raw) // 1024, 'KiB', len(GROUPS), 'groups', len(TEX), 'textures')

def rigid(nodes, i, M, joint, piece):
    n = nodes[i]; P, N, U, _, _, subs = rd(n['mesh'])
    Pw = P @ M[:3, :3].T + M[:3, 3]; Nw = N @ M[:3, :3].T
    J = np.zeros((len(P), 4), int); J[:, 0] = joint; W = np.zeros((len(P), 4)); W[:, 0] = 1
    for s, T in enumerate(subs):
        m = (n.get('mats') or [None])[min(s, len(n.get('mats') or [None]) - 1)] if n.get('mats') else None
        if glassy(m) or len(T) == 0: continue
        group(n['name'] + '.%d' % s, piece, tex(m), Pw, Nw, U, J, W, T)

def skinned(part, names, rest, piece, pre=None):
    """A skinned mesh, put into the skeleton's rest pose (its own binds folded in), bones by name."""
    P, N, U, SI, SW, subs = rd(part['mesh'])
    idx = {n: i for i, n in enumerate(names)}
    bm = [idx.get(p.split('/')[-1], -1) for p in part['bones']]
    if min(bm) < 0: print('missing bones', [p for p, b in zip(part['bones'], bm) if b < 0])
    Mb = [rest[b] @ (S @ rowm(e) @ S) if b >= 0 else np.eye(4) for b, e in zip(bm, part['mesh']['bind'])]
    Mb = np.array(Mb)
    A = np.einsum('vk,vkij->vij', SW, Mb[SI])
    if pre is not None: A = np.einsum('ij,vjk->vik', pre, A)
    Pw = np.einsum('vij,vj->vi', A[:, :3, :3], P) + A[:, :3, 3]; Nw = np.einsum('vij,vj->vi', A[:, :3, :3], N)
    J = np.array([[max(0, bm[s]) for s in r] for r in SI])
    for s, T in enumerate(subs):
        m = part['mats'][min(s, len(part['mats']) - 1)] if part.get('mats') else None
        if glassy(m) or len(T) == 0: continue
        group(part['name'] + '.%d' % s, piece, tex(m, 2048 if piece in ('head',) else 1024), Pw, Nw, U, J, SW, T)


# usage: pack_gun.py FP_JSON GUN_NODES_JSON MODEL OUT RENAME_JSON [RETICLE_PNG]
FPJ, GJ, MODEL, OUT, REN = sys.argv[1:6]
RET = sys.argv[6] if len(sys.argv) > 6 else None
X = json.load(open(FPJ)); H = json.load(open(D + 'hands.json')); G = json.load(open(D + GJ)); ren = json.loads(REN)
names = [b['name'] for b in X['bones']]; rest = [col(m) for m in X['rest']]
cam0 = col(X['cam0']); nh = sum(1 for b in X['bones'] if b['kind'] == 'hand')
hw = [col(m) for m in X['handWorld0']]; bi = [col(m) for m in X['boneInv']]
sk = next(n for n in H if n.get('mesh') and n['mesh'].get('skin'))
P, N, U, SI, SW, subs = rd(sk['mesh'])
Mb = np.array([cam0 @ hw[b] @ bi[b] for b in range(nh)])
A = np.einsum('vk,vkij->vij', SW, Mb[SI])
Pw = np.einsum('vij,vj->vi', A[:, :3, :3], P) + A[:, :3, 3]; Nw = np.einsum('vij,vj->vi', A[:, :3, :3], N)
for s, T in enumerate(subs):
    m = sk['mats'][min(s, len(sk['mats']) - 1)] if sk.get('mats') else None
    group('arms.%d' % s, 'arms', tex(m, 2048), Pw, Nw, U, SI, SW, T)
for b, bb in enumerate(X['bones']):
    if bb['kind'] == 'mesh': rigid(G, bb['node'], rest[b], b, 'gun')
clips = {ren.get(n, n): (c['fps'], [[col(m) for m in fr] for fr in c['frames']]) for n, c in X['clips'].items() if ren.get(n, n)}
if X.get('aim0'):        # a blade or grenade has no sight: no ads
    a = col(X['aim0'])
    T = np.eye(4); T[2, 3] = -0.10
    clips['ads'] = (30.0, [[T @ np.linalg.inv(a)] + [np.eye(4)] * (len(names) - 1)])
# third person: every gun piece where it sits on the gun's own root at rest (the game hangs it on the body's weapon bone)
clips['held'] = (30.0, [[col(m) for m in X['held']]])
if RET: TEX['reticle'] = open(RET, 'rb').read()
def unscaled(r):
    r = r.copy(); r[:3, :3] = r[:3, :3] / np.linalg.norm(r[:3, :3], axis=0); return r
# a scaled part (the PK-06's mesh is stored at 1/24) keeps its size in its vertices: its bone binds without the scale,
# as the game's poses (rotation and place only) carry none
write(MODEL, names, [-1] * len(names), [np.linalg.inv(unscaled(r)) for r in rest], clips, OUT)
print('clips', {k: len(v[1]) for k, v in clips.items()})
