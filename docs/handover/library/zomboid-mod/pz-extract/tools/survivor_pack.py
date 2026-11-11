"""Builds the survivor packs (pz_survivor_f.zsk, pz_survivor_m.zsk) the game draws players with, from Project Zomboid's
own files and the body mods on this PC. Nothing from Zomboid or the mods is in the repo or the jar: run this on (a copy of)
the files and put the two .zsk files in config/zomboidcraft/npc/ on the PC that plays.

    python3 -I tools/pz/survivor_pack.py PZ_MEDIA WORKSHOP_108600 OUT_DIR

PZ_MEDIA is ...\\steamapps\\common\\ProjectZomboid\\media, WORKSHOP_108600 is ...\\steamapps\\workshop\\content\\108600.
Mods used (by Workshop id): 3429790870 Tomb's Player Body (body mesh, skins, faces, muscle, stubble, body hair, makeup),
3531765630 Tomb's Goth (skins, faces, muscle, makeup, hair), 3578569664 Detailed Player Texture, 3408780732 Evil's Ink.

Every woman wears Tomb's black bra and briefs: it's painted onto the Detailed and Ink skins here (those two are nude) and the
game lays it over everything again last. No nude or DOLL texture is read.

The .zsk is pack.py's format (tools/npc/skinned/pack.py), one level of detail. Zomboid's .x files are left-handed (D3D),
1 unit = about a metre: mirrored on z and scaled by K into model space (metres, feet on y=0, facing +z, +x her left).
A texture entry called "catalog" holds the options as JSON (UTF-8), for the appearance page.
"""
import glob, io, json, os, re, struct, sys, zlib
import xml.etree.ElementTree as ET
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'npc'))
import xfile, fbxbin  # noqa: E402
from pack import scramble  # noqa: E402

K = 1.89  # Zomboid's man is 0.98 tall: about 1.85 m
# Zomboid's .x people face -z with their left at +x in D3D's left-handed space: mirrored on z they face +z, left at +x,
# as the game wants. Tomb's FBX meshes are mirrored on z first to land on them (see fbx_mesh).
A = np.diag([K, K, -K, 1.0])
AI = np.linalg.inv(A)
TOMB, GOTH, DETAIL, INK = '3429790870', '3531765630', '3578569664', '3408780732'
FPS = 30.0


# ---------------------------------------------------------------- .x reading
def mat(o):
    """A D3D matrix (row vectors) as a column-vector one."""
    return np.array(o.nums[:16], dtype=float).reshape(4, 4).T


def frames(root):
    out = []

    def rec(o, parent):
        for k in o.kids:
            if k.kind == 'Frame':
                ftm = k.child('FrameTransformMatrix')
                out.append({'name': k.name, 'parent': parent, 'local': mat(ftm[0]) if ftm else np.eye(4),
                            'mesh': bool(k.child('Mesh'))})
                rec(k, k.name)
    rec(root, None)
    return out


def x_meshes(path):
    """Each mesh in a .x: vertices (model space at bind), normals, uvs (v down), triangles, and per vertex up to 4
    (bone name, weight) with each bone's offset (model -> bone, column form)."""
    root = xfile.parse(path)
    res = []
    for me in xfile.walk(root, 'Mesh'):
        nums = me.nums
        nv = int(nums[0]); pos = np.array(nums[1:1 + 3 * nv]).reshape(nv, 3); p = 1 + 3 * nv
        nf = int(nums[p]); p += 1; faces = []
        for _ in range(nf):
            c = int(nums[p]); faces.append([int(x) for x in nums[p + 1:p + 1 + c]]); p += 1 + c
        nrm = me.child('MeshNormals')[0]; nn = int(nrm.nums[0])
        nvec = np.array(nrm.nums[1:1 + 3 * nn]).reshape(nn, 3); q = 1 + 3 * nn
        nfn = int(nrm.nums[q]); q += 1; nfaces = []
        for _ in range(nfn):
            c = int(nrm.nums[q]); nfaces.append([int(x) for x in nrm.nums[q + 1:q + 1 + c]]); q += 1 + c
        tc = me.child('MeshTextureCoords')[0]; nt = int(tc.nums[0])
        uv = np.array(tc.nums[1:1 + 2 * nt]).reshape(nt, 2)
        W = [dict() for _ in range(nv)]; off = {}
        for sw in me.child('SkinWeights'):
            b = sw.strs[0]; k = int(sw.nums[0])
            idx = [int(x) for x in sw.nums[1:1 + k]]; wts = sw.nums[1 + k:1 + 2 * k]
            off[b] = np.array(sw.nums[1 + 2 * k:1 + 2 * k + 16]).reshape(4, 4).T
            for a, w in zip(idx, wts):
                W[a][b] = W[a].get(b, 0) + w
        corners = []
        for f, fn in zip(faces, nfaces):
            for t in range(1, len(f) - 1):
                for c in (0, t, t + 1):
                    corners.append((f[c], fn[c]))
        P = np.array([pos[v] for v, _ in corners]); N = np.array([nvec[n] for _, n in corners])
        T = np.array([uv[v] for v, _ in corners]); WW = [W[v] for v, _ in corners]
        res.append(weld(me.name, P, N, T, WW, off))
    return root, res


def weld(name, P, N, T, WW, off):
    """Corners (3 per triangle) into shared vertices."""
    key = np.round(np.concatenate([P, N, T], 1), 5)
    _, first, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    inv = inv.reshape(-1)
    return {'name': name, 'pos': P[first], 'normal': N[first], 'uv': T[first], 'w': [WW[i] for i in first],
            'tris': inv.reshape(-1, 3), 'off': off}


# ---------------------------------------------------------------- FBX (Tomb's body and Goth hair)
def lcl(node, V):
    """An FBX model's local scale, rotation (XYZ Euler, degrees) and translation applied to points."""
    props = {x.props[0]: np.array(x.props[4:7], dtype=float) for x in node.one('Properties70').kids if x.props[0].startswith('Lcl')} \
        if node.one('Properties70') else {}
    S = props.get('Lcl Scaling', np.ones(3)); r = np.radians(props.get('Lcl Rotation', np.zeros(3)))
    T = props.get('Lcl Translation', np.zeros(3))
    cx, cy, cz = np.cos(r); sx, sy, sz = np.sin(r)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return (Rz @ Ry @ Rx @ (V * S).T).T + T


def fbx_mesh(path, fit=None, xoff=None):
    """Tomb's FBX meshes: mirrored on z (FBX is right-handed) and moved onto the .x skeleton by a similarity fit of the
    bones (or the one given, so hair lands where the body did). Returns the mesh in .x model space and the fit."""
    r, _ = fbxbin.read(path)
    objs = r.one('Objects'); conns = r.one('Connections')
    byid = {k.props[0]: k for k in objs.kids}
    links = [(c.props[1], c.props[2]) for c in conns.kids]
    geo = [k for k in objs.kids if k.name == 'Geometry'][0]
    V = geo.one('Vertices').props[0].reshape(-1, 3)
    PVI = geo.one('PolygonVertexIndex').props[0]
    uvl = geo.one('LayerElementUV'); UV = uvl.one('UV').props[0].reshape(-1, 2); UVI = uvl.one('UVIndex').props[0]
    nl = geo.one('LayerElementNormal'); NR = nl.one('Normals').props[0].reshape(-1, 3)
    by_poly = nl.one('MappingInformationType').props[0].startswith('ByPolygonVertex')
    nm = lambda n: n.props[1].split('\x00')[0].replace(' ', '_')
    clusters = {}
    for k in objs.kids:
        if k.name == 'Deformer' and k.props[2] == 'Cluster':
            bone = [byid[c] for c, p in links if p == k.props[0] and c in byid and byid[c].name == 'Model']
            clusters[nm(bone[0])] = (k.one('Indexes').props[0] if k.one('Indexes') else np.zeros(0, int),
                                     k.one('Weights').props[0] if k.one('Weights') else np.zeros(0),
                                     k.one('Transform').props[0].reshape(4, 4), k.one('TransformLink').props[0].reshape(4, 4))
    # the mesh node's own place (and its parents'), when it wasn't baked into the vertices (Goth's Reaper hair)
    gid = geo.props[0]
    node = next((byid[p] for c, p in links if c == gid and p in byid and byid[p].name == 'Model'), None)
    while node is not None:
        V = lcl(node, V)
        node = next((byid[p] for c, p in links if c == node.props[0] and p in byid and byid[p].name == 'Model'), None)
    Z = np.array([1.0, 1.0, -1.0]); V = V * Z; NR = NR * Z
    if fit is None:
        X, Y = [], []
        for b, (_, _, _, TL) in clusters.items():
            if b in xoff:
                X.append(TL[3, :3] * Z); Y.append(np.linalg.inv(xoff[b])[:3, 3])
        X, Y = np.array(X), np.array(Y)
        mx, my = X.mean(0), Y.mean(0); Xc, Yc = X - mx, Y - my
        U, Sg, Vt = np.linalg.svd(Yc.T @ Xc); D = np.eye(3); D[2, 2] = np.sign(np.linalg.det(U @ Vt))
        R = U @ D @ Vt; s = (Sg * np.diag(D)).sum() / (Xc ** 2).sum(); t = my - s * R @ mx
        res = np.linalg.norm((s * (R @ X.T)).T + t - Y, axis=1)
        print(f'  fit {os.path.basename(path)}: scale {s:.4f}, bones off by {res.max() * 100:.1f} cm at most')
        fit = (s, R, t)
    s, R, t = fit
    Vx = (s * (R @ V.T)).T + t
    W = [dict() for _ in range(len(V))]
    for b, (idx, w, _, _) in clusters.items():
        for i, ww in zip(idx, w):
            W[i][b] = W[i].get(b, 0) + ww
    P, N, T, WW = [], [], [], []
    poly = []
    for j, v in enumerate(PVI):
        poly.append((v if v >= 0 else ~v, j))
        if v < 0:
            for k in range(1, len(poly) - 1):
                for cp, pv in (poly[0], poly[k], poly[k + 1]):
                    P.append(Vx[cp]); n = R @ NR[pv if by_poly else cp]; N.append(n / (np.linalg.norm(n) + 1e-9))
                    u = UV[UVI[pv]]; T.append((u[0], 1 - u[1])); WW.append(W[cp])
            poly = []
    return weld(os.path.basename(path), np.array(P), np.array(N), np.array(T), WW, {}), fit


# ---------------------------------------------------------------- skeleton and clips
class Skeleton:
    def __init__(self, body_x):
        root, meshes = x_meshes(body_x)
        fr = [f for f in frames(root) if not f['mesh']]
        self.names = [f['name'] for f in fr]
        ix = {n: i for i, n in enumerate(self.names)}
        self.parent = [ix.get(f['parent'], -1) for f in fr]
        self.local = [f['local'] for f in fr]
        self.world = []
        for i in range(len(fr)):
            p = self.parent[i]
            self.world.append(self.local[i] if p < 0 else self.world[p] @ self.local[i])
        self.body = meshes[0]
        # model -> bone at rest (the T pose the meshes are bound in): the body's own offsets where it has them; a bone it
        # isn't skinned to (toes, the dress bones Tomb's body uses) sits on its parent at rest as the frames have it (the
        # frames hold a relaxed pose, not the bind, so their world matrices aren't used)
        bind = []
        for i, n in enumerate(self.names):
            if n in self.body['off']:
                bind.append(np.linalg.inv(self.body['off'][n]))
            else:
                p = self.parent[i]
                bind.append(self.local[i] if p < 0 else bind[p] @ self.local[i])
        self.bind = bind
        self.off = [np.linalg.inv(w) for w in bind]
        print(f'  skeleton {os.path.basename(body_x)}: {len(self.names)} bones, {len(self.body["off"])} skinned')
        self.ix = ix

    def hip(self):
        return self.world[self.ix['Bip01']][1, 3]


def clip(path, skel, hip_scale=1.0):
    """A .x animation sampled at FPS into model space: per frame per bone (x,y,z,w) and position, game space."""
    root = xfile.parse(path)
    tps = next((o.nums[0] for o in root.kids if o.kind == 'AnimTicksPerSecond'), 4800.0)
    aset = [o for o in root.kids if o.kind == 'AnimationSet'][0]
    tracks = {}
    dur = 0
    for a in aset.child('Animation'):
        tr = {}
        for k in a.child('AnimationKey'):
            ns = k.nums; kt = int(ns[0]); nk = int(ns[1]); p = 2; times, vals = [], []
            for _ in range(nk):
                t = ns[p]; c = int(ns[p + 1]); times.append(t / tps); vals.append(ns[p + 2:p + 2 + c]); p += 2 + c
            dur = max(dur, times[-1])
            if kt == 0:
                # w,x,y,z for the row-vector matrix: its conjugate turns column vectors
                tr['q'] = (np.array(times), np.array([(-x, -y, -z, w) for w, x, y, z in vals]))
            elif kt == 2:
                tr['p'] = (np.array(times), np.array(vals))
        tracks[a.refs[0]] = tr
    frames_n = max(2, int(round(dur * FPS)) + 1)
    nb = len(skel.names)
    out = np.zeros((frames_n, nb, 7))
    roots = {'Dummy01', 'Bip01', 'Translation_Data'}
    for f in range(frames_n):
        t = min(dur, f / FPS)
        world = [None] * nb
        for i, n in enumerate(skel.names):
            L = skel.local[i].copy()
            tr = tracks.get(n, {})
            if 'q' in tr:
                L[:3, :3] = qmat(slerp_at(*tr['q'], t))
            if 'p' in tr and n in roots:
                # only the root moves where the clip says; every other bone keeps this body's own lengths
                L[:3, 3] = lerp_at(*tr['p'], t) * (hip_scale if n == 'Bip01' else 1.0)
            p = skel.parent[i]
            world[i] = L if p < 0 else world[p] @ L
            G = A @ world[i] @ AI
            Rg = G[:3, :3]
            u, _, vt = np.linalg.svd(Rg); Rg = u @ vt
            out[f, i, :3] = G[:3, 3]
            out[f, i, 3:] = quat(Rg)
    return {'frames': frames_n, 'fps': FPS, 'data': out}


def qmat(q):
    x, y, z, w = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def quat(R):
    t = np.trace(R)
    if t > 0:
        s = np.sqrt(t + 1) * 2; return np.array([(R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s, s / 4])
    i = int(np.argmax(np.diag(R)))
    if i == 0:
        s = np.sqrt(1 + R[0, 0] - R[1, 1] - R[2, 2]) * 2
        return np.array([s / 4, (R[0, 1] + R[1, 0]) / s, (R[0, 2] + R[2, 0]) / s, (R[2, 1] - R[1, 2]) / s])
    if i == 1:
        s = np.sqrt(1 + R[1, 1] - R[0, 0] - R[2, 2]) * 2
        return np.array([(R[0, 1] + R[1, 0]) / s, s / 4, (R[1, 2] + R[2, 1]) / s, (R[0, 2] - R[2, 0]) / s])
    s = np.sqrt(1 + R[2, 2] - R[0, 0] - R[1, 1]) * 2
    return np.array([(R[0, 2] + R[2, 0]) / s, (R[1, 2] + R[2, 1]) / s, s / 4, (R[1, 0] - R[0, 1]) / s])


def seg(times, t):
    if t <= times[0]:
        return 0, 0, 0.0
    if t >= times[-1]:
        return len(times) - 1, len(times) - 1, 0.0
    j = int(np.searchsorted(times, t)) - 1
    return j, j + 1, (t - times[j]) / max(1e-9, times[j + 1] - times[j])


def lerp_at(times, vals, t):
    a, b, k = seg(times, t)
    return vals[a] * (1 - k) + vals[b] * k


def slerp_at(times, vals, t):
    a, b, k = seg(times, t)
    qa, qb = vals[a] / np.linalg.norm(vals[a]), vals[b] / np.linalg.norm(vals[b])
    d = qa @ qb
    if d < 0:
        qb, d = -qb, -d
    if d > 0.9995:
        q = qa + (qb - qa) * k
    else:
        th = np.arccos(d); q = (np.sin((1 - k) * th) * qa + np.sin(k * th) * qb) / np.sin(th)
    return q / np.linalg.norm(q)


# ---------------------------------------------------------------- meshes into game space
def to_game(m, skel, bone_off=None):
    """A mesh at its own bind into this skeleton's (each vertex carried by its bones from the offsets it was bound with
    to the skeleton's), then into game space; weights as 4 bone indices."""
    n = len(m['pos'])
    P = np.hstack([m['pos'], np.ones((n, 1))])
    si = np.zeros((n, 4), int); sw = np.zeros((n, 4))
    out = np.zeros((n, 3)); nrm = np.zeros((n, 3))
    for v in range(n):
        top = sorted(m['w'][v].items(), key=lambda kv: -kv[1])[:4]
        tot = sum(w for _, w in top) or 1
        acc = np.zeros(4); accn = np.zeros(3)
        # a bone Zomboid's body isn't skinned to (the dress bones and toes Tomb's body uses) hands its share to the nearest
        # one it is: Zomboid's clips don't move those bones the way Tomb bound them, so they'd pull the legs together
        folded = {}
        for b, w in m['w'][v].items():
            while b in skel.ix and b not in skel.body['off'] and skel.parent[skel.ix[b]] >= 0:
                b = skel.names[skel.parent[skel.ix[b]]]
            folded[b] = folded.get(b, 0) + w
        top = sorted(folded.items(), key=lambda kv: -kv[1])[:4]
        tot = sum(w for _, w in top) or 1
        for k, (b, w) in enumerate(top):
            bi = skel.ix.get(b)
            if bi is None:
                continue
            si[v, k] = bi; sw[v, k] = w / tot
            src = (bone_off or m['off']).get(b)
            M = np.eye(4) if src is None else np.linalg.inv(skel.off[bi]) @ src
            acc += sw[v, k] * (M @ P[v]); accn += sw[v, k] * (M[:3, :3] @ m['normal'][v])
        if sw[v].sum() == 0:
            si[v, 0] = skel.ix['Bip01_Head']; sw[v, 0] = 1; acc = P[v]; accn = m['normal'][v]
        out[v] = (A @ acc)[:3]
        g = np.array([accn[0], accn[1], -accn[2]]); nrm[v] = g / (np.linalg.norm(g) + 1e-9)
    # every triangle wound counter-clockwise seen from where its corners' normals point (the FBX mirror turns them over)
    tris = m['tris'].copy()
    tp = out[tris]
    face = np.cross(tp[:, 1] - tp[:, 0], tp[:, 2] - tp[:, 0])
    back = (face * nrm[tris].sum(1)).sum(1) < 0
    tris[back] = tris[back][:, [0, 2, 1]]
    return {'pos': out, 'normal': nrm, 'uv': m['uv'], 'si': si, 'sw': sw, 'tris': tris}


# ---------------------------------------------------------------- textures
def png(img):
    b = io.BytesIO(); img.save(b, 'PNG', optimize=True); return b.getvalue()


def limit(img, size=1024):
    return img if img.size[0] <= size else img.resize((size, size), Image.LANCZOS)


def pad(img, px=12):
    """An opaque skin: the clear gutters between UV islands take the colour of the nearest island, so no dark seam shows
    where a texture is sampled across an island's edge."""
    a = np.asarray(img.convert('RGBA')).astype(float)
    have = a[..., 3] > 127
    rgb = a[..., :3] * have[..., None]
    w = have.astype(float)
    for _ in range(px):
        if have.all():
            break
        sm = lambda x: (np.roll(x, 1, 0) + np.roll(x, -1, 0) + np.roll(x, 1, 1) + np.roll(x, -1, 1))
        nw = sm(w); nrgb = sm(rgb)
        grow = (~have) & (nw > 0)
        rgb[grow] = nrgb[grow]; w[grow] = nw[grow]
        rgb[grow] /= w[grow][:, None]; w[grow] = 1
        have = have | grow
    out = np.where(have[..., None], rgb, rgb[have].mean(0) if have.any() else 0)
    return Image.fromarray(np.dstack([out, np.full(out.shape[:2], 255.0)]).clip(0, 255).astype('uint8'))


def black_set(tomb_skin, media, G):
    """Tomb's own dark bra and briefs off its tone-1 skin, as a layer (alpha = the set, grown 2px), at 512."""
    R = 512
    v = np.asarray(Image.open(f'{media}/textures/Body/{G}Body01.png').convert('RGBA')).astype(int) \
        if os.path.exists(f'{media}/textures/Body/{G}Body01.png') else None
    tomb = np.asarray(Image.open(tomb_skin).convert('RGBA').resize((R, R))).astype(float)
    arm = np.asarray(Image.open(f'{media}/textures/Body/Masks/LeftArm.png').convert('RGBA').resize((R, R)))[..., 3] > 128
    if v is not None:
        uw = (v[..., 2] > v[..., 0] + 8) & (v[..., 3] > 0)
        fill = uw.copy(); h = uw.shape[0]
        for lo, hi in ((0, h // 2), (h // 2, h)):
            for x in range(uw.shape[1]):
                ys = np.nonzero(uw[lo:hi, x])[0]
                if len(ys):
                    fill[lo + ys.min():lo + ys.max() + 1, x] = True
        vm = Image.fromarray((fill * 255).astype('uint8')).resize((R, R), Image.NEAREST)
        near = np.asarray(vm.filter(ImageFilter.MaxFilter(41))) > 0
    else:
        near = np.ones((R, R), bool)
    skin = np.median(tomb[arm][:, :3], 0)
    if G == 'Female':
        dark = (tomb[..., :3].mean(2) < skin.mean() * 0.62) & near & (tomb[..., 3] > 0)
    else:  # his grey briefs: colourless where skin is warm
        mx, mn = tomb[..., :3].max(2), tomb[..., :3].min(2)
        dark = ((mx - mn) / np.maximum(mx, 1) < 0.12) & near & (tomb[..., 3] > 0)
    print(f'  {G} underwear layer: {int(dark.sum())} px')
    g = lambda m, f: np.asarray(Image.fromarray((m * 255).astype('uint8')).filter(f)) > 0
    dark = g(g(dark, ImageFilter.MaxFilter(3)), ImageFilter.MinFilter(3))
    mask = g(dark, ImageFilter.MaxFilter(5))
    lay = tomb.copy(); lay[mask & ~dark, :3] = tomb[dark][:, :3].mean(0); lay[..., 3] = mask * 255
    return Image.fromarray(lay.clip(0, 255).astype('uint8'))


def over(base, layer):
    out = base.convert('RGBA').copy()
    L = layer.resize(out.size, Image.BILINEAR)
    a = np.asarray(L).copy(); a[..., 3] = np.where(a[..., 3] > 20, 255, 0)
    out.alpha_composite(Image.fromarray(a))
    return out


def pretty(s):
    s = re.sub(r'^(Kate|Bob)_(Hair|Beard)_|^[FM]_', '', s)
    s = re.sub(r'(?<=[a-z])(?=[A-Z0-9])', ' ', s)
    return s.replace('_', ' ').strip()


# ---------------------------------------------------------------- the pack
CLIPS = {  # game name: Zomboid clip (Kate's own for women where Zomboid has one)
    'idle': ('Bob_Idle', 'Kate_Idle'), 'idle_1': ('Bob_IdleExt01',), 'idle_2': ('Bob_IdleExt02',), 'idle_3': ('Bob_IdleExt03',),
    'idle_4': ('Bob_IdleExt04',), 'idle_5': ('Bob_IdleExt05',), 'idle_hurt': ('Bob_Idle', 'Kate_IdleInjured'),
    'walk': ('Bob_Walk', 'Kate_Walk'), 'walk_back': ('Bob_WalkBwdNoAim1Hand',), 'strafe_l': ('Bob_Strafe_Left',),
    'strafe_r': ('Bob_Strafe_Right',), 'run': ('Bob_Run', 'Kate_Run'), 'sprint': ('Bob_Sprint',),
    'limp_l': ('Bob_WalkLightLimpL', 'Kate_Limp'), 'limp_r': ('Bob_WalkLightLimpR',), 'limp_heavy_l': ('Bob_WalkHeavyLimpL',),
    'limp_heavy_r': ('Bob_WalkHeavyLimpR',), 'run_limp': ('Bob_RunLightLimpL',),
    'sneak': ('Bob_IdleSneak',), 'sneak_walk': ('Bob_WalkSneak',), 'sneak_run': ('Bob_RunSneak',),
    'crouch': ('Bob_IdleLowSneak',), 'crouch_walk': ('Bob_WalkLowSneak',), 'crouch_run': ('Bob_RunLowSneak',),
    'fall': ('Bob_FallIdle',), 'land': ('Bob_FallLanding_Small',), 'land_hard': ('Bob_FallLanding_Heavy',),
    'sit_ground': ('Bob_SitGround_Idle',), 'sit_down': ('Bob_SitGround_In',), 'sit': ('Bob_IdleSeatedExt01',),
    'sleep': ('Bob_Asleep',), 'drive': ('Bob_IdleDriving',), 'passenger': ('Bob_IdlePassenger',), 'climb': ('Bob_ClimbRope',),
    'die': ('Bob_HitDeath_Walking',), 'dead': ('Bob_EndDeath',), 'get_up': ('Bob_GetUp_Back',),
    'turn_l': ('Bob_Turn90L',), 'turn_r': ('Bob_Turn90R',), 'vault': ('Bob_VaultOver',),
    'shove': ('Bob_Shove',), 'punch': ('Bob_AttackPunch01_Hit',), 'stomp': ('Bob_AttackFloorStamp',),
    'eat': ('Bob_IdleEating1Hand',), 'drink': ('Bob_IdleDrinkWithHand',), 'loot': ('Bob_IdleLooting_Mid',),
    'hammer': ('Bob_IdleHammering',),
    'rifle_idle': ('Bob_IdleRifle',), 'rifle_walk': ('Bob_WalkRifle',), 'rifle_run': ('Bob_RunRifle',),
    'rifle_sprint': ('Bob_Sprint_Rifle',), 'rifle_aim': ('Bob_IdleAimRifle',), 'rifle_aim_walk': ('Bob_WalkAimRifle',),
    'rifle_aim_back': ('Bob_WalkBwdAimRifle',), 'rifle_fire': ('Bob_AttackRifle',),
    'pistol_idle': ('Bob_IdleHandgun',), 'pistol_walk': ('Bob_WalkHandgun',), 'pistol_run': ('Bob_Run_Handgun',),
    'pistol_sprint': ('Bob_Sprint_Handgun',), 'pistol_aim': ('Bob_IdleAimHandgun',), 'pistol_aim_walk': ('Bob_WalkAimHandgun',),
    'pistol_aim_back': ('Bob_WalkBwdAimHandgun',), 'pistol_fire': ('Bob_AttackHandgun',),
    'bat_idle': ('Bob_IdleBat',), 'bat_walk': ('Bob_WalkBat',), 'bat_run': ('Bob_RunBat',), 'bat_aim': ('Bob_IdleAimBat',),
    'bat_swing': ('Bob_AttackBat01_Hit',),
    'one_idle': ('Bob_IdleNoAim1Hand',), 'one_walk': ('Bob_WalkNoAim1Hand',), 'one_aim': ('Bob_IdleAim1Hand',),
    'one_swing': ('Bob_Attack1Hand01_Hit',), 'knife_stab': ('Bob_AttackKnife01_Hit',),
    'heavy_idle': ('Bob_Idle2H_Heavy',), 'heavy_walk': ('Bob_Walk2H_Heavy',), 'heavy_swing': ('Bob_Attack2H_Heavy01_Hit',),
    'spear_aim': ('Bob_IdleAimSpear',), 'spear_stab': ('Bob_AttackSpear01_Hit',),
}


def build(sex, media, ws, out_dir):
    F = sex == 'f'
    G = 'Female' if F else 'Male'
    mods = lambda mid, sub: f'{ws}/{mid}/mods/{sub}'
    tomb_tex = mods(TOMB, "Tomb's Player Body - Textures") + '/media/textures/Body'
    goth_tex = mods(GOTH, "Tomb's Player Body - Goth Textures") + '/media/textures'
    print(f'{G}:')
    skel = Skeleton(f'{media}/models_X/Skinned/{G}Body.x')
    male = skel if not F else Skeleton(f'{media}/models_X/Skinned/MaleBody.x')
    xoff = {n: skel.off[i] for i, n in enumerate(skel.names)}

    groups = []  # (mesh name, piece, texture key, flags, game mesh)
    fbx = sorted(glob.glob(mods(TOMB, "Tomb's Player Body") + '/42*/media/models_X/Skinned/' + G + 'Body.fbx')) \
        or glob.glob(mods(TOMB, "Tomb's Player Body") + '/media/models_X/Skinned/' + G + 'Body.fbx')
    body, fit = fbx_mesh(fbx[-1], xoff=skel.body['off'])
    # Tomb's body carries a long skirt-shaped shell that Zomboid never shows (its skins are clear there): dropped
    alpha = np.asarray(Image.open(f"{mods(TOMB, "Tomb's Player Body - Textures")}/media/textures/Body/{G}Body01.png").convert('RGBA'))[..., 3]
    h, w = alpha.shape
    corners = body['uv'][body['tris']]
    pts = np.concatenate([corners, corners.mean(1, keepdims=True)], 1)
    seen = alpha[(pts[..., 1].clip(0, 0.9999) * h).astype(int), (pts[..., 0].clip(0, 0.9999) * w).astype(int)] > 64
    keep = seen.sum(1) >= 2
    print(f'  body: {int((~keep).sum())} of {len(keep)} triangles are never shown, dropped')
    body['tris'] = body['tris'][keep]
    groups.append(('body', 'body', 'skin', 0, to_game(body, skel, bone_off=xoff)))

    cat = {'sex': sex, 'height': 0, 'packs': [], 'faces': {}, 'hair': [], 'beard': [], 'stubble': [], 'bodyhair': [],
           'makeup': {'eyes': [], 'lips': [], 'face': []}, 'clips': []}
    tex = {}

    # hair: Zomboid's own (hairStyles.xml) and Tomb's Goth hair; one piece each
    hair_tex = {}
    styles = []
    info = {}  # Zomboid's own grooming: each style's length, what it trims to, whether hair grows into it, hat swaps
    for f in (f'{media}/hairStyles/hairStyles.xml', *glob.glob(mods(GOTH, "Tomb's Goth Hair") + '/media/hairStyles/*.xml')):
        for e in ET.parse(f).getroot():
            if e.tag != ('female' if F else 'male'):
                continue
            name = e.findtext('name')
            info[name] = {'level': int(e.findtext('level') or 1), 'trim': [t.text for t in e.findall('trimChoices')],
                          'grow': (e.findtext('growReference') or '').strip() == 'true',
                          'choose': e.find('noChoose') is None,
                          'alt': {a.get('category'): a.get('style') for a in e.findall('alternate')}}
            if not e.findtext('model'):
                continue
            styles.append((name, e.findtext('model').replace('\\', '/').rsplit('/', 1)[1], e.findtext('texture')))
    hair_files = {os.path.basename(p).lower()[:-2]: p for p in glob.glob(f'{media}/models_X/Skinned/Hair/*.x')}
    goth_files = {os.path.basename(p).lower()[:-4]: p for p in glob.glob(mods(GOTH, "Tomb's Goth Hair") + '/media/models_X/Skinned/Hair/*.fbx')}
    for name, model, texname in styles:
        low = model.lower()
        tail = low.split('hair_', 1)[1] if 'hair_' in low else low
        path = goth_files.get(low) or hair_files.get(low) or hair_files.get(('kate_hair_' if F else 'bob_hair_') + tail)
        if not path:
            print(f'  no mesh for hair {name} ({model})'); continue
        if path.endswith('.fbx'):
            m, _ = fbx_mesh(path, fit=fit)
            g = to_game(m, skel, bone_off=xoff)
            goth = True
        else:
            _, ms = x_meshes(path); g = to_game(ms[0], skel); goth = False
        hid = 'hair:' + name
        key = 'hairtex/' + texname
        hair_tex[texname] = True
        groups.append((os.path.basename(path), hid, key, 3, g))
        cat['hair'].append({'id': name, 'name': pretty(name), 'piece': hid, 'goth': goth, **info.get(name, {})})
    cat['bald'] = [n for n, v in info.items() if v['level'] == 0 and v['choose'] and n not in {h['id'] for h in cat['hair']}]
    if not F:
        beard_files = {os.path.basename(p).lower()[:-2]: p for p in glob.glob(f'{media}/models_X/Skinned/Beards/*.x')}
        for e in ET.parse(f'{media}/hairStyles/beardStyles.xml').getroot():
            model = (e.findtext('model') or '').replace('\\', '/').rsplit('/', 1)[-1]
            path = beard_files.get(model.lower())
            if not path:
                continue
            _, ms = x_meshes(path)
            name = e.findtext('name'); hid = 'beard:' + name
            binfo = {'level': int(e.findtext('level') or 1), 'trim': [t.text for t in e.findall('trimChoices')],
                     'grow': (e.findtext('growReference') or '').strip() == 'true'}
            hair_tex[e.findtext('texture')] = True
            groups.append((os.path.basename(path), hid, 'hairtex/' + e.findtext('texture'), 3, to_game(ms[0], skel)))
            cat['beard'].append({'id': name, 'name': pretty(name), 'piece': hid, **binfo})
    for t in hair_tex:
        tex['hairtex/' + t] = png(Image.open(f'{media}/textures/{t}.png').convert('RGBA'))

    # skins: per pack, tone and build (muscle), men also with a hairy body ("a")
    uw = black_set(f'{tomb_tex}/{G}Body01.png', media, G)
    tex['uw'] = png(uw)

    def add_skin(pack, tone, build, path, hairy=False, bake=False):
        img = limit(Image.open(path).convert('RGBA'))
        if bake:
            img = over(img, uw)
        key = f'skin/{pack}/{tone}/{build}' + ('/hairy' if hairy else '')
        tex[key] = png(pad(img))
        return key

    def builds(pack, base_dir, tone, base_path, hairy=False, bake=False):
        out = ['normal']
        add_skin(pack, tone, 'normal', base_path, hairy, bake)
        if hairy:
            return out  # muscle skins have no body hair
        for build, fn in (('toned', f'{G[0]}_Muscle{tone:02d}'), ('strong', f'{G[0]}_MuscleStrong{tone:02d}')):
            mp = f'{base_dir}/Muscle/{fn}.png'
            if os.path.exists(mp) and (np.asarray(Image.open(mp).convert('RGBA'))[..., 3] > 8).mean() > 0.05:
                base = limit(Image.open(base_path).convert('RGBA'))
                img = over(base, Image.open(mp).convert('RGBA'))
                if bake:
                    img = over(img, uw)
                tex[f'skin/{pack}/{tone}/{build}' + ('/hairy' if hairy else '')] = png(pad(img))
                out.append(build)
        return out

    def pack(pid, name, face_set, tones):
        cat['packs'].append({'id': pid, 'name': name, 'faces': face_set, 'tones': tones})

    tomb_tones = {}
    for t in range(1, 6):
        tomb_tones[t] = builds('tomb', tomb_tex, t, f'{tomb_tex}/{G}Body{t:02d}.png')
        if not F:
            builds('tomb', tomb_tex, t, f'{tomb_tex}/{G}Body{t:02d}a.png', hairy=True)
    pack('tomb', "Tomb's Player Body", 'tomb', {str(t): b for t, b in tomb_tones.items()})
    gb = builds('goth', f'{goth_tex}/Body', 1, f'{goth_tex}/Body/{G}Body01.png')
    if not F and os.path.exists(f'{goth_tex}/Body/{G}Body01a.png'):
        builds('goth', f'{goth_tex}/Body', 1, f'{goth_tex}/Body/{G}Body01a.png', hairy=True)
    pack('goth', "Tomb's Goth", 'goth', {'1': gb})
    if F:
        d = mods(DETAIL, 'DetailedPlayerTextureFemaleB42')
        dt = {}
        for t in range(1, 6):
            p = next(iter(glob.glob(f'{d}/*/media/textures/Body/FemaleBody{t:02d}.png') + glob.glob(f'{d}/media/textures/Body/FemaleBody{t:02d}.png')))
            dt[t] = builds('detail', f'{d}/media/textures/Body', t, p, bake=True)
        pack('detail', 'Detailed Player Texture', 'detail', {str(t): b for t, b in dt.items()})
        it = {}
        e = mods(INK, 'Evils Ink')
        for t in range(1, 6):
            p = next(iter(glob.glob(f'{e}/*/media/textures/Body/FemaleBody{t:02d}.png') + glob.glob(f'{e}/media/textures/Body/FemaleBody{t:02d}.png')))
            it[t] = builds('ink', f'{e}/media/textures/Body', t, p, bake=True)
        pack('ink', "Evil's Ink", 'tomb', {str(t): b for t, b in it.items()})

    # faces: Tomb's 9 per sex at 5 tones, Goth's 9 at one, Detailed's one face at 5 tones (women)
    tomb_faces = mods(TOMB, "Tomb's Player Body - Customisation") + '/media/textures/Faces'
    names = []
    for d in sorted(glob.glob(tomb_faces + '/*')):
        files = sorted(glob.glob(f'{d}/{G}0*.png'))
        if not files:
            continue
        n = os.path.basename(d); names.append(n)
        for f in files:
            tex[f'face/tomb/{n}/{int(f[-6:-4])}'] = png(Image.open(f).convert('RGBA'))
    cat['faces']['tomb'] = {'names': names, 'tones': 5}
    gn = []
    for d in sorted(glob.glob(goth_tex + '/Faces/*')):
        f = f'{d}/{G}01.png'
        if os.path.exists(f):
            n = os.path.basename(d); gn.append(n); tex[f'face/goth/{n}/1'] = png(limit(Image.open(f).convert('RGBA')))
    cat['faces']['goth'] = {'names': gn, 'tones': 1}
    if F:
        d = mods(DETAIL, 'DetailedPlayerTextureFemaleB42') + '/media/textures/Faces/Alice'
        for t in range(1, 6):
            tex[f'face/detail/Detailed/{t}'] = png(limit(Image.open(f'{d}/Female{t:02d}.png').convert('RGBA')))
        cat['faces']['detail'] = {'names': ['Detailed'], 'tones': 5}

    # stubble, body hair, makeup: overlays laid on the skin
    for f in sorted(glob.glob(tomb_tex + '/Stubble/*.png')):
        n = os.path.basename(f)[:-4]
        if F and not n.startswith('F_') or not F and not n.startswith('M_'):
            continue
        kind = 'bodyhair' if 'BodyHair' in n else 'stubble'
        tex[f'{kind}/{n}'] = png(Image.open(f).convert('RGBA'))
        cat[kind].append({'id': n, 'name': pretty(n).replace('Beard Stubble', 'Beard stubble').replace('Hair Stubble', 'Head stubble')})
    for f in sorted(glob.glob(tomb_tex + '/MakeUp/*.png')) + sorted(glob.glob(mods(GOTH, "Tomb's Goth Makeup") + '/media/textures/Body/MakeUp/*.png')):
        n = os.path.basename(f)[:-4]
        low = n.lower()
        kind = 'face' if 'fullface' in low else 'lips' if 'lipstick' in low else 'eyes'
        tex[f'makeup/{n}'] = png(Image.open(f).convert('RGBA'))
        label = re.sub(r'^makeup_\d+_', '', n)
        label = label.replace('eyeshadow', 'Eyeshadow ').replace('lipstick', 'Lipstick ').strip()
        cat['makeup'][kind].append({'id': n, 'name': pretty(label).replace('Goth ', 'Goth: ').strip().capitalize()})

    # clips
    clips = {}
    hip = skel.hip() / male.hip()
    for name, src in CLIPS.items():
        pick = src[1] if F and len(src) > 1 else src[0]
        who = pick.split('_')[0]
        p = f'{media}/anims_X/{who}/{pick}.x'
        if not os.path.exists(p):
            print(f'  missing clip {pick}'); continue
        clips[name] = clip(p, skel, 1.0 if who == 'Kate' else hip)
        cat['clips'].append(name)
    top = max(g[4]['pos'][:, 1].max() for g in groups if g[1] == 'body')
    cat['height'] = round(float(top), 3)
    tex['catalog'] = json.dumps(cat, indent=1).encode('utf-8')
    write(f'pz_survivor_{sex}', groups, tex, skel, clips, out_dir)


def write(model_id, groups, tex, skel, clips, out_dir):
    out = io.BytesIO()
    out.write(b'ZSKN' + struct.pack('>i', 1))
    out.write(struct.pack('>i', len(groups)))
    tris = 0
    for mesh, piece, texture, flags, m in groups:
        for s in (mesh, piece, texture):
            b = s.encode(); out.write(struct.pack('>H', len(b)) + b)
        n = len(m['pos'])
        out.write(struct.pack('>bbi', flags, 0, n))
        w = m['sw'] / np.maximum(m['sw'].sum(1, keepdims=True), 1e-9)
        wb = np.floor(w * 255 + 0.5).astype(np.int64); wb[:, 0] += 255 - wb.sum(1)
        v = np.zeros(n, dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('uv', '>u2', 2), ('j', 'u1', 4), ('w', 'u1', 4)])
        v['p'] = m['pos']; v['n'] = np.round(m['normal'] * 127).astype(np.int8)
        v['uv'] = np.round(np.mod(m['uv'], 1.0000001).clip(0, 1) * 65535).astype(np.uint16)  # .x uvs already run down
        v['j'] = m['si']; v['w'] = wb.clip(0, 255)
        out.write(v.tobytes())
        out.write(struct.pack('>i', len(m['tris'])))
        out.write(m['tris'].astype('>i4').tobytes())
        tris += len(m['tris'])
    out.write(struct.pack('>i', len(tex)))
    for key, data in tex.items():
        b = key.encode(); out.write(struct.pack('>H', len(b)) + b + struct.pack('>i', len(data)) + data)
    out.write(struct.pack('>i', len(skel.names)))
    for i, n in enumerate(skel.names):
        inv = A @ skel.off[i] @ AI
        b = n.encode()
        out.write(struct.pack('>H', len(b)) + b + struct.pack('>b', skel.parent[i]) + struct.pack('>12f', *inv[:3].reshape(-1)))
    out.write(struct.pack('>i', 0))  # no face bones
    out.write(struct.pack('>i', len(clips)))
    nb = len(skel.names)
    for name, c in clips.items():
        q = c['data'][:, :, 3:7]; q = q * np.sign(q[:, :, 3:4] + 1e-12)
        b = name.encode()
        out.write(struct.pack('>H', len(b)) + b + struct.pack('>ifi', c['frames'], c['fps'], nb))
        rec = np.zeros((c['frames'], nb), dtype=[('q', '>i2', 4), ('p', '>i2', 3)])
        rec['q'] = np.round(q * 32767).astype(np.int16)
        rec['p'] = np.round(c['data'][:, :, :3] * 1000).astype(np.int16)
        out.write(rec.tobytes())
    raw = out.getvalue()
    packed = scramble(zlib.compress(raw, 9), model_id)
    os.makedirs(out_dir, exist_ok=True)
    dest = os.path.join(out_dir, model_id + '.zsk')
    open(dest, 'wb').write(packed)
    print(f'{model_id}: {len(groups)} meshes, {tris} triangles, {len(tex)} textures, {nb} bones, {len(clips)} clips, '
          f'{len(packed) // 1024} KB -> {dest}')


if __name__ == '__main__':
    media, ws, out_dir = sys.argv[1:4]
    for sx in (sys.argv[4:] or ['f', 'm']):
        build(sx, media.rstrip('/'), ws.rstrip('/'), out_dir)
