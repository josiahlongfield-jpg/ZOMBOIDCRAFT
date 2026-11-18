"""Shared bits for Cortana's moves in Blender (runs inside Blender 5.x; numpy + mathutils only).

cortana_moves.bin (zlib, big-endian): 'ZMOV', int 1, int nBones, nBones x UTF names, int count, then per clip:
UTF name, UTF tag ('flat'/'heel'), int frames, float fps, float speed, byte travel, frames x 3 float root (x, z, yaw),
frames x bones x 7 int16 (quat xyzw * 32767, pos mm). Poses are model space (Y up, facing +Z), metres.
"""
import json, struct, zlib
import numpy as np
from mathutils import Matrix, Quaternion, Vector

# game model space (Y up) <-> Blender (Z up): the glTF convention
C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
CI = C.inverted()


def read_moves(path):
    raw = zlib.decompress(open(path, 'rb').read())
    o = [0]

    def rd(fmt):
        v = struct.unpack_from('>' + fmt, raw, o[0]); o[0] += struct.calcsize('>' + fmt); return v

    def utf():
        n, = rd('H'); s = raw[o[0]:o[0] + n].decode(); o[0] += n; return s

    assert raw[:4] == b'ZMOV'; o[0] = 4
    ver, nb = rd('ii')
    bones = [utf() for _ in range(nb)]
    count, = rd('i')
    clips = []
    for _ in range(count):
        name, tag = utf(), utf()
        frames, = rd('i'); fps, speed = rd('ff'); travel, = rd('b')
        root = np.frombuffer(raw, '>f4', frames * 3, o[0]).reshape(frames, 3).astype(np.float64); o[0] += frames * 12
        a = np.frombuffer(raw, '>i2', frames * nb * 7, o[0]).reshape(frames, nb, 7).astype(np.float64); o[0] += frames * nb * 14
        q = a[..., :4] / 32767.0
        q /= np.linalg.norm(q, axis=-1, keepdims=True)
        clips.append(dict(name=name, tag=tag, fps=fps, speed=speed, travel=bool(travel), root=root, q=q, p=a[..., 4:] / 1000.0))
    return bones, clips


def write_moves(path, bones, clips):
    out = bytearray()
    W = lambda f, *v: out.extend(struct.pack('>' + f, *v))

    def utf(s):
        b = s.encode(); W('H', len(b)); out.extend(b)

    out += b'ZMOV'; W('i', 1); W('i', len(bones))
    for b in bones: utf(b)
    W('i', len(clips))
    for c in clips:
        utf(c['name']); utf(c['tag']); F = len(c['root'])
        W('i', F); W('f', c['fps']); W('f', c['speed']); W('b', 1 if c['travel'] else 0)
        out += np.asarray(c['root'], '>f4').tobytes()
        q = np.asarray(c['q'], np.float64); q = q / np.linalg.norm(q, axis=-1, keepdims=True)
        a = np.concatenate([np.round(q * 32767), np.round(np.asarray(c['p']) * 1000)], axis=-1)
        out += np.clip(a, -32768, 32767).astype('>i2').tobytes()
    open(path, 'wb').write(zlib.compress(bytes(out), 6))


def glb_rest(path):
    """bone name -> rest global matrix in game space (inverse of the glTF inverse bind matrix)"""
    data = open(path, 'rb').read()
    jl, = struct.unpack_from('<I', data, 12)
    js = json.loads(data[20:20 + jl])
    bin_at = 20 + jl + 8
    skin = js['skins'][0]
    acc = js['accessors'][skin['inverseBindMatrices']]
    bv = js['bufferViews'][acc['bufferView']]
    off = bin_at + bv.get('byteOffset', 0) + acc.get('byteOffset', 0)
    m = np.frombuffer(data, '<f4', acc['count'] * 16, off).reshape(-1, 4, 4)
    out = {}
    for i, j in enumerate(skin['joints']):
        ibm = Matrix(m[i].T.tolist())  # glTF matrices are column-major
        out[js['nodes'][j].get('name', str(j))] = ibm.inverted()
    return out


def game_matrix(q, p):
    x, y, z, w = q
    return Matrix.Translation(Vector(p)) @ Quaternion((w, x, y, z)).to_matrix().to_4x4()
