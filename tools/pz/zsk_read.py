"""Reads a .zsk back (as NpcSkin does), for checking a pack: python3 -I tools/pz/zsk_read.py FILE.zsk [MODEL_ID]"""
import io, os, struct, sys, zlib
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'npc'))
from pack import scramble  # noqa: E402  (XOR both ways)


def read(path, model_id=None):
    model_id = model_id or os.path.basename(path)[:-4]
    raw = zlib.decompress(scramble(open(path, 'rb').read(), model_id))
    b = io.BytesIO(raw)
    u = lambda f: struct.unpack('>' + f, b.read(struct.calcsize('>' + f)))
    utf = lambda: b.read(u('H')[0]).decode()
    assert b.read(4) == b'ZSKN' and u('i')[0] == 1
    groups = []
    for _ in range(u('i')[0]):
        mesh, piece, tex = utf(), utf(), utf()
        flags, level, n = u('bbi')
        v = np.frombuffer(b.read(n * 27), dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('uv', '>u2', 2), ('j', 'u1', 4), ('w', 'u1', 4)])
        t = np.frombuffer(b.read(u('i')[0] * 12), '>i4').reshape(-1, 3)
        groups.append({'mesh': mesh, 'piece': piece, 'texture': tex, 'flags': flags, 'level': level,
                       'pos': v['p'].astype(float), 'normal': v['n'] / 127.0, 'uv': v['uv'] / 65535.0,
                       'joint': v['j'].astype(int), 'weight': v['w'] / 255.0, 'tris': t.astype(int)})
    tex = {}
    for _ in range(u('i')[0]):
        k = utf(); tex[k] = b.read(u('i')[0])
    names, parent, inv = [], [], []
    for _ in range(u('i')[0]):
        names.append(utf()); parent.append(u('b')[0])
        m = np.eye(4); m[:3] = np.array(u('12f')).reshape(3, 4); inv.append(m)
    for _ in range(u('i')[0]):
        utf(); u('b3f')
    clips = {}
    for _ in range(u('i')[0]):
        name = utf(); frames, fps, nb = u('ifi')
        rec = np.frombuffer(b.read(frames * nb * 14), dtype=[('q', '>i2', 4), ('p', '>i2', 3)]).reshape(frames, nb)
        clips[name] = {'frames': frames, 'fps': fps, 'q': rec['q'] / 32767.0, 'p': rec['p'] / 1000.0}
    return {'groups': groups, 'textures': tex, 'bones': names, 'parent': parent, 'inverse': inv, 'clips': clips}


if __name__ == '__main__':
    z = read(*sys.argv[1:])
    print(len(z['groups']), 'groups;', len(z['textures']), 'textures;', len(z['bones']), 'bones;', len(z['clips']), 'clips')
    body = z['groups'][0]
    print('body', body['pos'].min(0).round(3), body['pos'].max(0).round(3))
