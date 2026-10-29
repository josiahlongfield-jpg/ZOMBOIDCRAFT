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
    fit        one of our guns (models/item/weapon/<fit>/body.json): the mesh is scaled to that gun's length, its
               muzzle put on that gun's muzzle and its bore on the flash, so the hands, sights and flash still land
    unit_mm    if set, the mesh keeps its real size instead (source units in mm; the pbr pack is in cm, so 10) at
               our scale of 31 mm a pixel, still muzzle on muzzle: for a bought gun shorter or longer than ours
    length     if set, the mesh is scaled to this many pixels long instead (a stockless gun standing in for one of ours
               with a stock: fitted to our gun from the muzzle back to its receiver)
    pieces     {name: {"z": [from, to], "y": [from, to], "x": [from, to]}}: triangles whose centre is in that box (item
               pixels after fitting; y and x optional) are cut out into their own part, e.g. the pump as "handle"
    outline    outline thickness in item pixels (default 0.03; small things want less)

Writes src/main/resources/assets/zomboidcraft/meshes/<id>.zmesh (the id may hold a folder, e.g. loot/coin): zlib-deflated, then XORed with the same xorshift32
stream the cars use (seeded from the id), so the jar holds no usable model. Deflated payload, big-endian:
    "ZMSH" int version (3), float outline, int pngLength, png (the toon-painted textures stacked top to bottom), int parts,
    per part: UTF name, int triangles, then triangles * 3 * (short x,y,z in thousandths of an item pixel,
    char u,v, byte nx,ny,nz: the corner's smooth normal, which the outline shell is pushed out along)
in item model space (pixels, muzzle toward -z), the same space as the block gun it stands in for.
"""
import io, json, os, struct, sys, zlib
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


def toon(img):
    """Flatten the painted shading out of a PBR base colour: lift near-black to charcoal, pull each texel toward its
    own brightness, keep wear and edge highlights as flat flecks."""
    c = np.asarray(img.convert('RGB')).astype(float) / 255
    lum = c.mean(-1, keepdims=True)
    c = np.maximum(c, 0.16) * 0.6 + lum * 0.4
    c = np.round(c * 24) / 24                       # a little posterising, so painted gradients go flat
    return Image.fromarray(np.clip(c * 255, 0, 255).astype(np.uint8))


def axis(s):
    v = np.zeros(3); v[AXES[s[1]]] = -1 if s[0] == '-' else 1
    return v


def turn(forward, up):
    """The rotation taking the source's forward to -z and up to +y (forward x up lands on +x)."""
    f, u = axis(forward), axis(up)
    src = np.array([np.cross(f, u), u, -f])         # rows: what becomes x, y, z
    return src


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
    sc = trimesh.load(path(spec['obj']), force='scene', process=False)
    size = spec.get('texture', 1024)
    texs, verts, uvs, faces, n = [], [], [], [], 0
    used = [(g, t) for g, t in zip(sc.geometry.values(), spec['textures']) if t is not None and len(g.faces)]
    for k, (g, t) in enumerate(used):
        texs.append(toon(Image.open(path(t))).resize((size, size), Image.LANCZOS))
        uv = np.asarray(g.visual.uv, float) % 1.0
        uv[:, 1] = (k + 1 - uv[:, 1]) / len(used)            # flip v and stack this texture's band
        verts.append(np.asarray(g.vertices, float)); uvs.append(uv); faces.append(np.asarray(g.faces) + n)
        n += len(g.vertices)
    v = np.concatenate(verts) @ turn(spec['forward'], spec['up']).T
    v = fit(v, spec['fit'], spec.get('unit_mm'), spec.get('length'))
    uv = np.concatenate(uvs); f = np.concatenate(faces)
    normals = smooth_normals(v, f)
    centre = v[f].mean(1)
    parts, left = [], np.ones(len(f), bool)
    for name, box in spec.get('pieces', {}).items():
        take = left & (centre[:, 2] >= box['z'][0]) & (centre[:, 2] <= box['z'][1])
        if 'y' in box:
            take &= (centre[:, 1] >= box['y'][0]) & (centre[:, 1] <= box['y'][1])
        if 'x' in box:
            take &= (centre[:, 0] >= box['x'][0]) & (centre[:, 0] <= box['x'][1])
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
    lo, hi = v.min(0), v.max(0)
    print(f"{spec['id']}: fitted to {spec['fit']}, {lo.round(2)} .. {hi.round(2)} px, wrote {len(data) // 1024} KB")


if __name__ == '__main__':
    main(sys.argv[1])
