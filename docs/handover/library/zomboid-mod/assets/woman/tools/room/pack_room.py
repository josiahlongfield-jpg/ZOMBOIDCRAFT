# woman.zsk -> preview files: lod0 geometry + bones + face + clips as one deflated .bin (no textures), textures as their own files
import sys, struct, zlib, os
sys.path.insert(0, 'tools/npc')
from pack import scramble
src, out = sys.argv[1], sys.argv[2]
d = zlib.decompress(scramble(open(src, 'rb').read(), 'woman'))
o = 8
def rd(fmt):
    global o; v = struct.unpack_from('>' + fmt, d, o); o += struct.calcsize('>' + fmt); return v
def utf():
    global o; n, = rd('H'); s = d[o:o+n]; o += n; return s
ng, = rd('i'); keep = []
for _ in range(ng):
    start = o
    mesh, piece, tex = utf(), utf(), utf(); fl, lv = rd('bb'); nv, = rd('i'); o += nv * 27
    nt, = rd('i'); o += nt * 12
    if lv == 0: keep.append(d[start:o])
ntx, = rd('i'); sizes = {}
for _ in range(ntx):
    k = utf().decode(); n, = rd('i'); b = d[o:o+n]; o += n
    ext = 'png' if b[:4] == b'\x89PNG' else 'jpg'
    open(os.path.join(out, f'tex_{k}.{ext}'), 'wb').write(b); sizes[k] = (ext, n)
rest = d[o:]
geo = b'ZSKN' + struct.pack('>i', 1) + struct.pack('>i', len(keep)) + b''.join(keep) + struct.pack('>i', 0) + rest
open(os.path.join(out, 'woman.bin'), 'wb').write(zlib.compress(geo, 9))
print(sizes, len(geo), os.path.getsize(os.path.join(out, 'woman.bin')))
