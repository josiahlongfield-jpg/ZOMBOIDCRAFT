# shion.zsk lod0 -> chk.bin + chk.json (exactly what the game reads) and tex_*.{jpg,png}
import sys, struct, zlib, json, numpy as np
sys.path.insert(0, '/home/user/zomboidcraft/tools/npc')
from pack import scramble
d = zlib.decompress(scramble(open(sys.argv[1],'rb').read(), 'shion')); o=8
def rd(f):
    global o; v=struct.unpack_from('>'+f,d,o); o+=struct.calcsize('>'+f); return v
def utf():
    global o; n,=rd('H'); s=d[o:o+n].decode(); o+=n; return s
out=bytearray(); J={'groups':[]}
def put(a):
    off=len(out); out.extend(a.tobytes()); return off
ng,=rd('i')
rec=np.dtype([('p','>f4',3),('n','i1',3),('uv','>u2',2),('j','u1',4),('w','u1',4)])
for _ in range(ng):
    mesh,piece,tex=utf(),utf(),utf(); fl,lv=rd('bb'); nv,=rd('i'); v=np.frombuffer(d,rec,nv,o); o+=nv*rec.itemsize
    nt,=rd('i'); ix=np.frombuffer(d,'>i4',nt*3,o); o+=nt*12
    if lv: continue
    J['groups'].append(dict(mesh=mesh,piece=piece,tex=tex,flags=fl,n=nv,t=nt*3,
        p=put(v['p'].astype('<f4')),uv=put((v['uv'].astype('<f4')/65535)),j=put(v['j'].astype(np.float32)),w=put(v['w'].astype('<f4')/255),ix=put(ix.astype('<u4'))))
ntx,=rd('i'); J['tex']={}
for _ in range(ntx):
    k=utf(); n,=rd('i'); b=d[o:o+n]; o+=n; ext='png' if b[:4]==b'\x89PNG' else 'jpg'
    open(f'tex_{k.replace("@","_")}.{ext}','wb').write(b); J['tex'][k]=f'tex_{k.replace("@","_")}.{ext}'
nbn,=rd('i'); J['bones']=[]; J['parent']=[]; inv=[]
for _ in range(nbn):
    nm=utf(); p,=rd('b'); m=rd('12f'); J['bones'].append(nm); J['parent'].append(p); inv.append(m)
J['inv']=inv
nf,=rd('i')
for _ in range(nf): utf(); rd('b3f')
nc,=rd('i'); J['clips']={}
for _ in range(nc):
    nm=utf(); fr,fps,cb=rd('ifi'); r=np.frombuffer(d,np.dtype([('q','>i2',4),('p','>i2',3)]),fr*cb,o); o+=fr*cb*14
    q=r['q'].astype('<f4')/32767; p=r['p'].astype('<f4')/1000
    J['clips'][nm]=dict(frames=fr,fps=fps,bones=cb,q=put(q),p=put(p))
open('chk.bin','wb').write(bytes(out)); json.dump(J,open('chk.json','w'))
print(len(J['groups']),'groups',len(J['bones']),'bones',list(J['clips']))
