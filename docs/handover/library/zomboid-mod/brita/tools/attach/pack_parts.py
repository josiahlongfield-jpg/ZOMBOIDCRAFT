# The posing tool's attachment parts (his pack's sights, Brita's grips) -> zmesh files for the game, LOCAL ONLY.
# usage: python3 -I pack_parts.py <tool parts dir> <out dir>       writes <out>/<id>.zmesh (parts "body", "glass"),
# <id>_ret<k>.zmesh for each lit reticle (red dots, holo, a piggyback dot), <id>_reticle.png (a scope's own reticle,
# drawn over its zoomed picture) and parts.json. Positions are metres x 16 (a metre is one block), in the gun's frame
# (x right, y up, z back), origin where the part mounts; the game scales them by the gun's model units per metre.
import base64, gzip, io, json, os, struct, sys, zlib
import numpy as np
sys.path.insert(0, '/home/user/zomboidcraft/tools/cars')
import importlib.util
spec = importlib.util.spec_from_file_location('cars_pack', '/home/user/zomboidcraft/tools/cars/pack.py')
cp = importlib.util.module_from_spec(spec); spec.loader.exec_module(cp)
SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
def unz(s, dt): return np.frombuffer(gzip.decompress(base64.b64decode(s)), dt)
def dataurl(s): return base64.b64decode(s.split(',', 1)[1])
def write(path, mid, outline, png, parts):
    out = io.BytesIO()
    out.write(b'ZMSH'); out.write(struct.pack('>i', 3)); out.write(struct.pack('>f', outline))
    out.write(struct.pack('>i', len(png))); out.write(png); out.write(struct.pack('>i', len(parts)))
    for name, pos, uv, sn in parts:
        n = len(pos) // 3
        rec = np.zeros(n * 3, dtype=[('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('u', '>u2'), ('v', '>u2'), ('nx', 'i1'), ('ny', 'i1'), ('nz', 'i1')])
        q = np.clip(np.round(pos * 16 * 1000), -32768, 32767)
        rec['x'], rec['y'], rec['z'] = q[:, 0], q[:, 1], q[:, 2]
        rec['u'] = np.round(np.clip(uv[:, 0], 0, 1) * 65535); rec['v'] = np.round(np.clip(uv[:, 1], 0, 1) * 65535)
        rec['nx'], rec['ny'], rec['nz'] = sn[:, 0], sn[:, 1], sn[:, 2]
        b = name.encode(); out.write(struct.pack('>H', len(b))); out.write(b); out.write(struct.pack('>i', n)); out.write(rec.tobytes())
    open(path, 'wb').write(cp.scramble(zlib.compress(out.getvalue(), 9), mid))
index = json.load(open(os.path.join(SRC, 'index.json')))
meta = {}
for p in index:
    if p['kind'] not in ('optic', 'grip'): continue
    j = json.load(open(os.path.join(SRC, p['id'] + '.json')))
    pos = (unz(j['tris'], np.int16).astype(float) / 10000).reshape(-1, 3)
    uv = (unz(j['uv'], np.uint16).astype(float) / 65535).reshape(-1, 2)
    sn = unz(j['sn'], np.int8).reshape(-1, 3) if j.get('sn') else np.zeros((len(pos), 3), np.int8)
    nt = len(pos) // 3
    gl = unz(j['glass'], np.uint8) if j.get('glass') else np.zeros(nt, np.uint8)
    tri = np.arange(nt)
    def pick(mask):
        idx = (tri[mask][:, None] * 3 + np.arange(3)).reshape(-1)
        return pos[idx], uv[idx], sn[idx]
    parts = [('body',) + pick(gl == 0)]
    if (gl == 1).any(): parts.append(('glass',) + pick(gl == 1))
    write(os.path.join(OUT, p['id'] + '.zmesh'), 'local/parts/' + p['id'], j.get('outline', 0.001) * 16, dataurl(j['tex']), parts)
    m = {k: p[k] for k in ('name', 'kind', 'mount', 'mag', 'line', 'front', 'back', 'eyepiece', 'dot') if k in p}
    lit = []
    for k, R in enumerate(j.get('reticles') or []):
        rt = np.array(R['tri']); idx = (rt[:, None] * 3 + np.arange(3)).reshape(-1)
        ruv = np.array(R['uv'], float).reshape(-1, 2); ruv[:, 1] = 1 - ruv[:, 1]   # the tool's reticle pictures load flipped
        if R['main']:
            open(os.path.join(OUT, p['id'] + '_reticle.png'), 'wb').write(dataurl(R['tex']))
            m['reticle'] = {'r': R['r'], 'z': R['z']}
        else:
            write(os.path.join(OUT, f"{p['id']}_ret{k}.zmesh"), f"local/parts/{p['id']}_ret{k}", 0, dataurl(R['tex']),
                  [('body', pos[idx], ruv, np.zeros((len(idx), 3), np.int8))])
            lit.append(f"{p['id']}_ret{k}")
    if lit: m['lit'] = lit
    meta[p['id']] = m
    print(p['id'], [(n, len(a) // 3) for n, a, _, _ in parts], m.get('mag'), lit)
json.dump(meta, open(os.path.join(OUT, 'parts.json'), 'w'), indent=1)
