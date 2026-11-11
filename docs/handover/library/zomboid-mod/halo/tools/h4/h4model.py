import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/h4')
from h4res import R4
def blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(r.X(a)) if c else 0)
def find(r,grp,name,nm):
    for k,t in enumerate(r.tags):
        if t[0]==grp and nm[k]==name: return k,r.off(r.X(t[2]))
    return None,None
def model(r,o):
    d=r.d
    nn,na=blk(r,o,0x30)
    nodes=[dict(sid=struct.unpack_from('<I',d,na+i*0x60)[0],parent=struct.unpack_from('<h',d,na+i*0x60+4)[0],t=struct.unpack_from('<3f',d,na+i*0x60+0xc),q=struct.unpack_from('<4f',d,na+i*0x60+0x18)) for i in range(nn)]
    nmat,ma=blk(r,o,0x48)
    mats=[struct.unpack_from('<I',d,ma+i*0x2c+0xc)[0] for i in range(nmat)]
    nc,ca=blk(r,o,0x74); comp=struct.unpack_from('<10f',d,ca+12) if nc else None
    nmesh,mea=blk(r,o,0x68)
    R=r.resource(struct.unpack_from('<I',d,o+0xf8)[0]&0xffff); c=R['ctrl']; prim=bytes(R['prim']); root=R['root']; F=R['fix']
    nvb=struct.unpack_from('<I',c,root+0x18)[0]; vbp=F[root+0x1c]&0xfffffff
    nib=struct.unpack_from('<I',c,root+0x24)[0]; ibp=F[root+0x28]&0xfffffff
    vbs=[];ibs=[]
    for i in range(nvb):
        inner=F[vbp+i*12]&0xfffffff; cnt,typ,stride,size=struct.unpack_from('<IHHI',c,inner); dp=F[inner+0x14]&0xfffffff
        vbs.append((cnt,typ,stride,prim[dp:dp+size]))
    for i in range(nib):
        inner=F[ibp+i*12]&0xfffffff; typ=struct.unpack_from('<I',c,inner)[0]; size=struct.unpack_from('<I',c,inner+8)[0]; dp=F[inner+0x14]&0xfffffff
        ibs.append((typ,np.frombuffer(prim[dp:dp+size],dtype='<u2')))
    nmc,nma=blk(r,o,0xbc); maps=[]
    for i in range(nmc):
        n,b=struct.unpack_from('<II',d,nma+i*12); b=r.off(r.X(b)) if n else 0; maps.append(list(d[b:b+n]))
    meshes=[]
    for m in range(nmesh):
        e=mea+m*0x70
        np_,pa=blk(r,e,0)
        vbi=struct.unpack_from('<8h',d,e+0x18); ibi=struct.unpack_from('<h',d,e+0x2a)[0]; rigid=struct.unpack_from('<b',d,e+0x30)[0]
        parts=[struct.unpack_from('<hhiihhBBHH',d,pa+k*24) for k in range(np_)]
        meshes.append(dict(vbi=vbi[0],ibi=ibi,rigid=rigid,parts=parts,raw=d[e+0x28:e+0x34]))
    return dict(nodes=nodes,mats=mats,comp=comp,meshes=meshes,vbs=vbs,ibs=ibs,maps=maps)
def decode(vb,comp):
    cnt,typ,stride,raw=vb
    a=np.frombuffer(raw,np.uint8).reshape(cnt,stride)
    p=a[:,0:12].copy().view('<f4').reshape(cnt,3)
    U,Nn,I=(12,16,32) if stride==40 else (16,20,36)
    uv=a[:,U:U+4].copy().view('<u2').reshape(cnt,2).astype(np.float32)/65535
    n=a[:,Nn:Nn+6].copy().view('<i2').reshape(cnt,3).astype(np.float32)/32767
    out=dict(typ=typ,stride=stride)
    if stride>=40: out['ni']=a[:,I:I+4]; out['nw']=a[:,I+4:I+8]
    if comp:
        x0,x1,y0,y1,z0,z1,u0,u1,v0,v1=comp
        p=p*np.array([x1-x0,y1-y0,z1-z0],np.float32)+np.array([x0,y0,z0],np.float32)
        uv=uv*np.array([u1-u0,v1-v0],np.float32)+np.array([u0,v0],np.float32)
    out.update(p=p,uv=uv,n=n); return out
def tris(ix,typ,st,cn):
    seg=ix[st:st+cn]
    if typ==5:
        out=[]
        for i in range(len(seg)-2):
            a,b,c=seg[i],seg[i+1],seg[i+2]
            if a==b or b==c or a==c: continue
            out.append((a,b,c) if i%2==0 else (b,a,c))
        return np.array(out,np.int64).reshape(-1,3)
    return seg[:len(seg)//3*3].reshape(-1,3).astype(np.int64)
