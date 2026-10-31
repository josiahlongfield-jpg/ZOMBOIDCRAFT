"""Packs a bought gun model into the file the mod ships (Josiah 2026-10-29: bought guns go in as real meshes,
toon-painted to sit with the block guns).

The source packs are Royalty Free and must never be committed (the licence forbids redistributing them and the repo
is public). Keep them in project files or scratch, convert FBX to OBJ with `assimp export in.fbx out.obj`, and run

    python3 tools/meshguns/pack.py SPEC.json

SPEC (paths may be relative to the spec file):
    id         the packed file's name, and what item models ask MeshGunRenderer for
    obj        the source OBJ
    textures   one base colour per object in the OBJ, in order (null leaves that object out: lens planes and such)
    forward    the source axis the muzzle points along, e.g. "+z" or "-x"
    up         the source axis that's up, e.g. "+y"
    axes       instead of forward/up, the source axes that become our x, y and z, e.g. ["-z", "+y", "+x"] (must be a
               turn, not a mirror): for things that aren't held like a gun, such as a blade standing up its model's y
    fit        one of our guns (models/item/weapon/<fit>/body.json): the mesh is scaled to that gun's length, its
               muzzle put on that gun's muzzle and its bore on the flash, so the hands, sights and flash still land
    unit_mm    if set, the mesh keeps its real size instead (source units in mm; the pbr pack is in cm, so 10) at
               our scale of 31 mm a pixel, still muzzle on muzzle: for a bought gun shorter or longer than ours
    length     if set, the mesh is scaled to this many pixels long instead (a stockless gun standing in for one of ours
               with a stock: fitted to our gun from the muzzle back to its receiver)
    pieces     {name: {"z": [from, to], "y": [from, to], "x": [from, to]}}: triangles whose centre is in that box (item
               pixels after fitting; y and x optional) are cut out into their own part, e.g. the pump as "handle"
    shift      [x, y, z] item pixels to move the fitted mesh by (a crossbow, which has no flash to put its bore on)
    box        instead of fit, one of our plain item models (models/item/weapon/<box>.json, e.g. "machete"): the mesh is
               scaled to that model's longest side and centred on it, for melee weapons and other things with no muzzle
    asis       true when the OBJ is already in item model space (cut from a mesh fitted elsewhere, e.g. a launcher taken off
               a bought rifle): no turning or fitting
    outline    outline thickness in item pixels (default 0.03; small things want less)
    split      true to read the OBJ's objects one by one even when they share a material (otherwise they come in as
               one), so textures can leave some out
    patches    [[texture index, u0, v0, u1, v1], ...]: rectangles of a base colour (fractions of its width and height,
               from the top left) painted over with the colour round them: brand logos and lettering
    groups     instead of textures: the OBJ's groups (its g/o names) by name, read one by one: {"texture": base colour for
               all of them, "textures": [[name part, base colour], ...] for groups painted from another, "skip": [name parts left out (lens planes)], "parts": {part: [name parts]} (cut out by
               group rather than by box: the magazine, the scope), "only": [name parts] (pack just those: a gun's own
               scope or suppressor as an attachment of its own, fitted with the gun so it keeps the gun's scale)}
    roll       degrees to turn it about z before mounting (180: a grip or bipod taken from under a gun, so it stands +y
               like everything on a top rail)
    clear      true for an optic you look through: the glass on its sight line is left out (see clear_glass)
    sight      for an optic: {"objects": [indices]} or {"groups": [name parts]}, its lens (usually left out of the drawing),
               whose middle is where the eye lines up; or {"at": [x, y, z]} in packed pixels. Written to bounds.json.
               (a piece's box may also hold "group": [name parts] to take only from those groups)
    keep       a box like a piece's: only the triangles whose centre is in it are packed (a gun's own scope or bipod
               taken off its fitted mesh to be an attachment of its own)
    drop       a list of such boxes whose triangles are left out (a light's trailing pressure-switch cable)
    mount      for an attachment: "rail" puts the middle of its clamp's foot at the origin (it stands up +y from the
               rail, muzzle end toward -z, as attachments.py draws them: grips are turned to hang +y too), "muzzle"
               puts the middle of its back end there (a suppressor, reaching forward along -z). Without fit it keeps
               its real size by unit_mm (source mm per unit). skin.py moves it onto each gun's mount point.

Writes src/main/resources/assets/zomboidcraft/meshes/<id>.zmesh (the id may hold a folder, e.g. loot/coin): zlib-deflated, then XORed with the same xorshift32
stream the cars use (seeded from the id), so the jar holds no usable model. Deflated payload, big-endian:
    "ZMSH" int version (3), float outline, int pngLength, png (the toon-painted textures stacked top to bottom), int parts,
    per part: UTF name, int triangles, then triangles * 3 * (short x,y,z in thousandths of an item pixel,
    char u,v, byte nx,ny,nz: the corner's smooth normal, which the outline shell is pushed out along)
in item model space (pixels, muzzle toward -z), the same space as the block gun it stands in for.
"""
import io, json, os, struct, sys, zlib
from PIL import ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True      # one bought texture is cut a few bytes short
import numpy as np
import trimesh
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import importlib.util  # noqa: E402
_cars = importlib.util.spec_from_file_location('cars_pack', os.path.join(ROOT, 'tools', 'cars', 'pack.py'))
_cars_pack = importlib.util.module_from_spec(_cars); _cars.loader.exec_module(_cars_pack)
scramble = _cars_pack.scramble      # same stream as the cars

WEAPONS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/models/item/weapon')
AXES = {'x': 0, 'y': 1, 'z': 2}
# every packed mesh's reach in item pixels (an attachment's round its clamp): attachments.py sizes its ghosts by it
BOUNDS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bounds.json')


def toon(img):
    """Flatten the painted shading out of a PBR base colour: lift near-black to charcoal, pull each texel toward its
    own brightness, keep wear and edge highlights as flat flecks."""
    c = np.asarray(img.convert('RGB')).astype(float) / 255
    lum = c.mean(-1, keepdims=True)
    c = np.maximum(c, 0.16) * 0.6 + lum * 0.4
    c = np.round(c * 24) / 24                       # a little posterising, so painted gradients go flat
    return Image.fromarray(np.clip(c * 255, 0, 255).astype(np.uint8))


def group_names(obj, geoms):
    """The OBJ's group names for trimesh's split objects: trimesh hands them back in the file's order reversed, which
    the triangle counts check."""
    names, counts, name, cnt = [], [], None, 0
    for line in open(obj, errors='ignore'):
        if line.startswith(('g ', 'o ')):
            if cnt:
                names.append(name); counts.append(cnt)
            name, cnt = line[2:].strip(), 0
        elif line.startswith('f '):
            cnt += len(line.split()) - 3
    if cnt:
        names.append(name); counts.append(cnt)
    names.reverse(); counts.reverse()
    if counts != [len(g.faces) for g in geoms]:
        raise SystemExit(f"{obj}: can't match its groups {list(zip(names, counts))} to {[len(g.faces) for g in geoms]}")
    return names


def clear_glass(v, f, sight):
    """The glass the eye looks through along an optic's sight line (parallel to z through the sight point): each
    piece of the mesh (faces joined by shared corners: a lens is its own piece, with its own patch of texture) that the
    line passes through, faces along it and is no wider than a lens. Returns which faces to leave out."""
    from scipy.sparse import coo_matrix
    from scipy.sparse.csgraph import connected_components
    tris = v[f]
    e1, e2 = tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0]
    nrm = np.cross(e1, e2)
    nrm = nrm / np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-12)
    d = np.array([0.0, 0.0, -1.0])
    p = np.cross(d, e2); det = (e1 * p).sum(1); ok = np.abs(det) > 1e-12
    inv = np.where(ok, 1 / np.where(ok, det, 1), 0)
    hit = np.zeros(len(f), bool)
    # the line itself and a ring round it, so a lens set a little off the middle is found too
    for a in [None] + list(np.linspace(0, 2 * np.pi, 8, endpoint=False)):
        off = (0.0, 0.0) if a is None else (0.15 * np.cos(a), 0.15 * np.sin(a))
        t0 = np.array([sight[0] + off[0], sight[1] + off[1], 1e3]) - tris[:, 0]
        u = (t0 * p).sum(1) * inv; q = np.cross(t0, e1); w = (q * d).sum(1) * inv
        hit |= ok & (u >= 0) & (w >= 0) & (u + w <= 1)
    edges = np.concatenate([f[:, [0, 1]], f[:, [1, 2]]])
    graph = coo_matrix((np.ones(len(edges)), (edges[:, 0], edges[:, 1])), shape=(len(v), len(v)))
    _, label = connected_components(graph, directed=False)
    piece = label[f[:, 0]]
    drop = np.zeros(len(f), bool)
    for c in np.unique(piece[hit]):
        m = piece == c
        r = np.hypot(tris[m, :, 0] - sight[0], tris[m, :, 1] - sight[1]).max()
        facing = np.abs(nrm[m, 2]).mean()
        if facing > 0.6 and r < 2.5:
            drop |= m
        print(f"  glass piece of {m.sum()} faces, {r:.2f} px wide, facing {facing:.2f}: {'cleared' if facing > 0.6 and r < 2.5 else 'kept'}")
    return drop


def paint_over(img, box):
    """Fills a rectangle (fractions) with the median colour of a band just outside it."""
    a = np.asarray(img).copy()
    h, w = a.shape[:2]
    x0, y0, x1, y1 = int(box[0] * w), int(box[1] * h), int(np.ceil(box[2] * w)), int(np.ceil(box[3] * h))
    pad = max(4, (x1 - x0 + y1 - y0) // 16)
    ya, xa = max(0, y0 - pad), max(0, x0 - pad)
    out = np.ones(a[ya:y1 + pad, xa:x1 + pad].shape[:2], bool)
    out[y0 - ya:y1 - ya, x0 - xa:x1 - xa] = False
    a[y0:y1, x0:x1] = np.median(a[ya:y1 + pad, xa:x1 + pad][out], axis=0)
    return Image.fromarray(a)


def axis(s):
    v = np.zeros(3); v[AXES[s[1]]] = -1 if s[0] == '-' else 1
    return v


def turn(forward, up):
    """The rotation taking the source's forward to -z and up to +y (forward x up lands on +x)."""
    f, u = axis(forward), axis(up)
    src = np.array([np.cross(f, u), u, -f])         # rows: what becomes x, y, z
    return src


def axes(names):
    """The rotation whose rows are the source axes that become x, y and z."""
    m = np.array([axis(a) for a in names])
    if np.linalg.det(m) < 0:
        raise SystemExit(f"axes {names} mirror the model (its faces would turn inside out): flip one of them")
    return m


def model_elements(name):
    m = json.load(open(os.path.join(WEAPONS, name + '.json')))
    while 'elements' not in m:
        m = json.load(open(os.path.join(WEAPONS, m['parent'].split('item/weapon/')[1] + '.json')))
    a = np.array([e['from'] for e in m['elements']], float); b = np.array([e['to'] for e in m['elements']], float)
    return np.minimum(a, b).min(0), np.maximum(a, b).max(0)


def fit(v, gun, unit_mm=None, length=None):
    """Scale and move the mesh onto our gun: same length, same muzzle, bore through the flash, centred in x."""
    lo, hi = model_elements(gun + '/body')
    try:
        flo, fhi = model_elements(gun + '/hip/muzzle_flash')
        bore = (flo[1] + fhi[1]) / 2
    except (FileNotFoundError, KeyError):
        bore = None
    s = unit_mm / 31.0 if unit_mm else (length or hi[2] - lo[2]) / (v[:, 2].max() - v[:, 2].min())
    v = v * s
    v[:, 2] += lo[2] - v[:, 2].min()
    v[:, 0] += 8 - (v[:, 0].max() + v[:, 0].min()) / 2
    if bore is None:
        v[:, 1] += (lo[1] + hi[1]) / 2 - (v[:, 1].max() + v[:, 1].min()) / 2
    else:
        # the muzzle's face: whatever is within 1.5% of the length of the front
        tip = v[v[:, 2] < v[:, 2].min() + 0.015 * (v[:, 2].max() - v[:, 2].min())]
        v[:, 1] += bore - (tip[:, 1].max() + tip[:, 1].min()) / 2
    return v


def fit_box(v, model):
    """Scale and move the mesh onto one of our plain models: same longest side, same middle."""
    lo, hi = model_elements(model)
    a = int(np.argmax(hi - lo))
    v = v * (hi[a] - lo[a]) / (v[:, a].max() - v[:, a].min())
    return v + (lo + hi) / 2 - (v.max(0) + v.min(0)) / 2


def smooth_normals(v, faces):
    """Each corner's normal averaged over every face sharing its position (seams split by uv still agree)."""
    key = np.round(v, 4)
    _, idx = np.unique(key, axis=0, return_inverse=True)
    idx = idx.reshape(-1)
    tri = v[faces]
    fn = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    acc = np.zeros((idx.max() + 1, 3))
    for c in range(3):
        np.add.at(acc, idx[faces[:, c]], fn)
    acc /= np.linalg.norm(acc, axis=1, keepdims=True) + 1e-12
    return acc[idx]


def main(spec_path):
    spec = json.load(open(spec_path)); base = os.path.dirname(os.path.abspath(spec_path))
    path = lambda p: p if os.path.isabs(p) else os.path.join(base, p)
    grp = spec.get('groups')
    split = {'split_object': True, 'group_material': False} if spec.get('split') or grp else {}
    sc = trimesh.load(path(spec['obj']), force='scene', process=False, **split)
    size = spec.get('texture', 1024)
    texs, verts, uvs, faces, n = [], [], [], [], 0
    geoms = list(sc.geometry.values())
    if grp:
        names = group_names(path(spec['obj']), geoms)
        has = lambda nm, keys: any(k.lower() in nm.lower() for k in keys)
        keep_g = [g for g, nm in zip(geoms, names) if not has(nm, grp.get('skip', []))]
        group_of = {id(g): nm for g, nm in zip(geoms, names)}
        def tex_of(nm):
            return next((t for k, t in grp.get('textures', []) if k.lower() in nm.lower()), grp.get('texture'))
        used = [(g, tex_of(group_of[id(g)])) for g in keep_g if len(g.faces)]
    else:
        used = [(g, t) for g, t in zip(geoms, spec['textures']) if t is not None and len(g.faces)]
    # one texture band per distinct base colour, not per object
    bands = []
    for _, t in used:
        if t not in bands:
            bands.append(t)
    owner = []          # each face's group name (for groups.parts)
    for k, t in enumerate(bands):
        src = Image.open(path(t)).convert('RGB')
        for p in spec.get('patches', []):
            if p[0] == k:
                src = paint_over(src, p[1:])
        texs.append(toon(src).resize((size, size), Image.LANCZOS))
    for g, t in used:
        k = bands.index(t)
        uv = np.asarray(g.visual.uv, float) % 1.0
        uv[:, 1] = (k + 1 - uv[:, 1]) / len(bands)           # flip v and stack this texture's band
        owner += [group_of[id(g)] if grp else ''] * len(g.faces)
        verts.append(np.asarray(g.vertices, float)); uvs.append(uv); faces.append(np.asarray(g.faces) + n)
        n += len(g.vertices)
    v = np.concatenate(verts)
    # an optic's lens (left out of the drawing): carried through every move below, then read off as where the eye lines up
    lens = []
    for i, g in enumerate(geoms):
        if i in spec.get('sight', {}).get('objects', []) or (grp and any(
                k.lower() in group_of[id(g)].lower() for k in spec.get('sight', {}).get('groups', []))):
            lens.append(np.asarray(g.vertices, float))
    n_lens = sum(len(x) for x in lens)
    if n_lens:
        v = np.concatenate([v] + lens)
    if spec.get('asis'):
        pass
    elif spec.get('mount') and 'fit' not in spec:
        v = v @ (axes(spec['axes']) if 'axes' in spec else turn(spec['forward'], spec['up'])).T * (spec['unit_mm'] / 31.0)
    elif 'box' in spec:
        v = v @ (axes(spec['axes']) if 'axes' in spec else turn(spec['forward'], spec['up'])).T
        v = fit_box(v, spec['box'])
    else:
        v = v @ (axes(spec['axes']) if 'axes' in spec else turn(spec['forward'], spec['up'])).T
        v = fit(v, spec['fit'], spec.get('unit_mm'), spec.get('length'))
    v = v + np.array(spec.get('shift', [0, 0, 0]), float)
    uv = np.concatenate(uvs); f = np.concatenate(faces)

    def inside(c, box, tris=None):
        """Triangles whose centre is in the box; with "all", whose every corner is (tris: their corners)."""
        if box.get('all') and tris is not None:
            return np.all([inside(tris[:, i], dict(box, all=False)) for i in range(3)], axis=0)
        if box.get('any') and tris is not None:
            return np.any([inside(tris[:, i], dict(box, any=False)) for i in range(3)], axis=0)
        take = np.ones(len(c), bool)
        for a, k in AXES.items():
            if a in box:
                take &= (c[:, k] >= box[a][0]) & (c[:, k] <= box[a][1])
        return take

    owner = np.array(owner)
    if grp and grp.get('only'):         # after fitting, so a gun's own part keeps the gun's scale
        sel = np.array([any(k.lower() in o.lower() for k in grp['only']) for o in owner], bool); f, owner = f[sel], owner[sel]
    if 'keep' in spec:
        sel = inside(v[f].mean(1), spec['keep'], v[f]); f, owner = f[sel], owner[sel]
    for box in spec.get('drop', []):
        out = inside(v[f].mean(1), box, v[f])
        if 'group' in box:
            out &= np.array([any(k.lower() in o.lower() for k in box['group']) for o in owner], bool)
        sel = ~out; f, owner = f[sel], owner[sel]
    if spec.get('roll'):                # a part off the side or the underside, turned to stand on a top rail
        a = np.radians(spec['roll'])
        v = v @ np.array([[np.cos(a), -np.sin(a), 0], [np.sin(a), np.cos(a), 0], [0, 0, 1]]).T
    if spec.get('mount'):
        used = v[np.unique(f)]
        lo, hi = used.min(0), used.max(0)
        if spec['mount'] == 'rail':     # the foot: the lowest sixth, so a knob out to one side doesn't pull it over
            foot = used[used[:, 1] < lo[1] + (hi[1] - lo[1]) / 6]
            fm = (foot.min(0) + foot.max(0)) / 2
            if n_lens:                  # an optic sits square over the bore: its lens, not its clamp's levers
                fm[0] = (v[-n_lens:, 0].min() + v[-n_lens:, 0].max()) / 2
            v = v - np.array([fm[0], lo[1], fm[2]])
        else:                           # the back end: the last tenth
            back = used[used[:, 2] > hi[2] - (hi[2] - lo[2]) / 10]
            bm = (back.min(0) + back.max(0)) / 2
            v = v - np.array([bm[0], bm[1], hi[2]])
    sight = None
    if n_lens:
        L = v[-n_lens:]
        sight = (L.min(0) + L.max(0)) / 2
        v = v[:-n_lens]
    elif 'sight' in spec and 'at' in spec['sight']:
        sight = np.asarray(spec['sight']['at'], float)
    if spec.get('clear') and sight is not None:
        keep = ~clear_glass(v, f, sight)
        f, owner = f[keep], owner[keep]
    normals = smooth_normals(v, f)
    centre = v[f].mean(1)
    parts, left = [], np.ones(len(f), bool)
    for name, keys in (grp or {}).get('parts', {}).items():
        take = left & np.array([any(k.lower() in o.lower() for k in keys) for o in owner], bool)
        parts.append((name, f[take])); left &= ~take
    for name, box in spec.get('pieces', {}).items():
        take = left & inside(centre, box, v[f])
        if 'group' in box:              # only from these groups (the camo cloth round a scope, not the rail under it)
            take &= np.array([any(k.lower() in o.lower() for k in box['group']) for o in owner], bool)
        parts.append((name, f[take])); left &= ~take
    parts.insert(0, ('body', f[left]))

    atlas = Image.new('RGB', (size, size * len(texs)))
    for k, t in enumerate(texs):
        atlas.paste(t, (0, k * size))
    png = io.BytesIO(); atlas.save(png, 'PNG', optimize=True)
    out = io.BytesIO()
    out.write(b'ZMSH'); out.write(struct.pack('>i', 3)); out.write(struct.pack('>f', spec.get('outline', 0.03)))
    out.write(struct.pack('>i', len(png.getvalue()))); out.write(png.getvalue())
    out.write(struct.pack('>i', len(parts)))
    rec = np.zeros(len(v), dtype=[('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'),
                                  ('nx', 'i1'), ('ny', 'i1'), ('nz', 'i1')])
    q = np.clip(np.round(v * 1000), -32768, 32767)
    rec['x'], rec['y'], rec['z'] = q[:, 0], q[:, 1], q[:, 2]
    rec['u'] = np.round(uv[:, 0] * 65535); rec['v'] = np.round(uv[:, 1] * 65535)
    nq = np.round(normals * 127)
    rec['nx'], rec['ny'], rec['nz'] = nq[:, 0], nq[:, 1], nq[:, 2]
    for name, pf in parts:
        b = name.encode(); out.write(struct.pack('>H', len(b))); out.write(b)
        out.write(struct.pack('>i', len(pf)))
        out.write(rec[pf.reshape(-1)].tobytes())
        print(f"  {name}: {len(pf)} triangles")
    data = scramble(zlib.compress(out.getvalue(), 9), spec['id'])
    dest = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/meshes', spec['id'] + '.zmesh')
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    open(dest, 'wb').write(data)
    lo, hi = v[np.unique(f)].min(0), v[np.unique(f)].max(0)
    bounds = json.load(open(BOUNDS)) if os.path.exists(BOUNDS) else {}
    bounds[spec['id']] = [[round(float(x), 3) for x in lo], [round(float(x), 3) for x in hi]]
    if sight is not None:
        bounds[spec['id']].append([round(float(x), 3) for x in sight])
        print(f"  sight at {np.round(sight, 3)}")
    with open(BOUNDS, 'w') as fh:
        fh.write('{\n' + ',\n'.join(f' {json.dumps(k)}: {json.dumps(bounds[k])}' for k in sorted(bounds)) + '\n}\n')
    print(f"{spec['id']}: fitted to {spec.get('fit') or spec.get('box') or 'itself'}, {lo.round(2)} .. {hi.round(2)} px, wrote {len(data) // 1024} KB")


if __name__ == '__main__':
    main(sys.argv[1])
