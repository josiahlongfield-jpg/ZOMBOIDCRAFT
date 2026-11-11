import sys,struct,json,numpy as np; sys.path.insert(0,'/tmp/claude-0/h4')
from h4res import R4
import h4model as M
r=R4('m90.map'); nm=r.names(); d=r.d; S=r.strings()
PC=sys.argv[1]
k,o=M.find(r,'pcaa',PC,nm)
c,a=struct.unpack_from('<II',d,o+0x38); b=r.off(r.X(a))
ents=[]
for e in range(c):
    eo=b+e*0x64; mesh,nv=struct.unpack_from('<II',d,eo)
    sc,sa=struct.unpack_from('<II',d,eo+0x4c); sb=r.off(r.X(sa))
    subs=[struct.unpack_from('<IIIIII',d,sb+i*28) for i in range(sc)]
    ents.append((mesh,nv,subs))
resi=struct.unpack_from('<I',d,o+0x44)[0]&0xffff
R=r.resource(resi); ctrl=bytes(R['ctrl']); p=bytes(R['prim'])
mk,mo=M.find(r,'mode','objects\\characters\\storm_cortana\\storm_cortana',nm); m=M.model(r,mo)
out={}; off=0
def cv(p): return np.stack([p[:,1],p[:,2],p[:,0]],1)
for (mesh,nv,subs),hdr in zip(ents,[0,0x1c]):
    size=struct.unpack_from("<I",ctrl,hdr+8)[0]; ncomp=size//(nv*16)
    doff=R['fix'][hdr+20]&0xfffffff
    u=np.frombuffer(p[doff:doff+size],'<u2').reshape(ncomp,nv,8).astype(np.float32)/65535
    dev=u-u[-1].mean(0)
    B=np.stack([cv(dev[i,:,:3]) for i in range(ncomp)])  # glTF axes, unscaled
    vb=M.decode(m['vbs'][m['meshes'][mesh]['vbi']],m['comp']); P=cv(vb['p'])*3.048
    B.astype('<f4').tofile(f'/tmp/claude-0/h4/pcab_{sys.argv[3]}_{mesh}.bin')
    np.save(f'/tmp/claude-0/h4/pcaP_{sys.argv[3]}_{mesh}.npy',P)
    co={}
    for (sid,fo,fc,_,nc,rd) in subs:
        R2=r.resource(rd&0xffff); n=struct.unpack_from('<I',bytes(R2['ctrl']),0)[0]
        arr=np.frombuffer(bytes(R2['prim'][:n]),'<f4').reshape(-1,nc)
        nmn=S[sid+0x1ab9] if sid+0x1ab9<len(S) else str(sid); co[nmn]=arr.round(5).tolist()
    out[mesh]=dict(nv=nv,ncomp=ncomp,coef=co)
    print(mesh,nv,ncomp,list(co)[:13],[len(v) for v in co.values()])
json.dump(out,open(sys.argv[2],'w'))
