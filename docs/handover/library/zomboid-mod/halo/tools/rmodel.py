"""Reach MCC render model (mode) decoder -> meshes with positions/uv/normals/parts."""
import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
def blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(X(a)) if c else 0)
def find_tag(r,grp,name,nm=None):
    nm=nm or r.names()
    for k,t in enumerate(r.tags):
        if t[0]==grp and nm[k]==name: return k,r.off(X(t[2]))
    return None,None
def tagname(r,datum,nm):
    k=datum&0xffff
    return (r.tags[k][0],nm[k]) if k<len(r.tags) and datum!=0xffffffff else (None,None)
def strips(ix):
    out=[]
    for i in range(len(ix)-2):
        a,b,c=ix[i],ix[i+1],ix[i+2]
        if a==b or b==c or a==c: continue
        out.append((a,b,c) if i%2==0 else (b,a,c))
    return out
def model(r,o,nm):
    d=r.d
    nn,na=blk(r,o,0x30)
    nodes=[]
    for i in range(nn):
        e=na+i*0x60
        nodes.append(dict(parent=struct.unpack_from('<h',d,e+4)[0],t=struct.unpack_from('<3f',d,e+0xc),q=struct.unpack_from('<4f',d,e+0x18),
            inv=struct.unpack_from('<12f',d,e+0x2c)))
    nmat,ma=blk(r,o,0x48)
    mats=[tagname(r,struct.unpack_from('<I',d,ma+i*0x2c+0xc)[0],nm)[1] for i in range(nmat)]
    nmesh,mea=blk(r,o,0x68)
    nc,ca=blk(r,o,0x74)
    comps=[]
    for i in range(nc):
        e=ca+i*52; b=struct.unpack_from('<10f',d,e+4); comps.append(b)
    resd=struct.unpack_from('<I',d,o+0xf8)[0]
    R=r.resource(resd&0xffff); c=R['ctrl']; prim=bytes(R['prim'])
    # vertex buffers at root: count@root+0x18, ptr@root+0x1c ; index bufs @root+0x24/0x28
    root=R['root']
    def arr(off):
        n=struct.unpack_from('<I',c,off)[0]; p=R['fix'].get(off+4,0)&0x0fffffff; return n,p
    nvb,vbp=arr(root+0x18); nib,ibp=arr(root+0x24)
    vbs=[];ibs=[]
    for i in range(nvb):
        e=vbp+i*12; inner=R['fix'][e]&0x0fffffff
        cnt,typ,stride,size=struct.unpack_from('<IHHI',c,inner); dp=R['fix'][inner+0x14]&0x0fffffff
        vbs.append((cnt,typ,stride,prim[dp:dp+size]))
    for i in range(nib):
        e=ibp+i*12; inner=R['fix'][e]&0x0fffffff
        typ=struct.unpack_from('<I',c,inner)[0]; size=struct.unpack_from('<I',c,inner+8)[0]; dp=R['fix'][inner+0x14]&0x0fffffff
        ibs.append((typ,np.frombuffer(prim[dp:dp+size],dtype='<u2')))
    meshes=[]
    for m in range(nmesh):
        e=mea+m*0x5c
        np_,pa=blk(r,e,0)
        vbi=struct.unpack_from('<8h',d,e+0x18); ibi=struct.unpack_from('<2h',d,e+0x28)
        rigid=struct.unpack_from('<b',d,e+0x2e)[0]; vtype=d[e+0x2f]; itype=d[e+0x32]
        parts=[struct.unpack_from('<hhiihhBBH',d,pa+k*24) for k in range(np_)]
        meshes.append(dict(vbi=vbi,ibi=ibi,rigid=rigid,vtype=vtype,itype=itype,parts=parts))
    return dict(nodes=nodes,mats=mats,meshes=meshes,comps=comps,vbs=vbs,ibs=ibs)
def decode_vb(vb,comp):
    cnt,typ,stride,raw=vb
    a=np.frombuffer(raw,dtype=np.uint8).reshape(cnt,stride)
    out={}
    if stride in (44,):
        p=a[:,0:12].copy().view('<f4').reshape(cnt,3)
        uv=a[:,16:20].copy().view('<u2').reshape(cnt,2).astype(np.float32)/65535.0
        n=a[:,20:26].copy().view('<i2').reshape(cnt,3).astype(np.float32)/32767.0
        out['ni']=a[:,36:40]; out['nw']=a[:,40:44]
    else:
        p=a[:,0:12].copy().view('<f4').reshape(cnt,3)
        uv=a[:,16:20].copy().view('<u2').reshape(cnt,2).astype(np.float32)/65535.0
        n=a[:,20:26].copy().view('<i2').reshape(cnt,3).astype(np.float32)/32767.0
    if comp:
        x0,x1,y0,y1,z0,z1,u0,u1,v0,v1=comp
        p=p*np.array([x1-x0,y1-y0,z1-z0],np.float32)+np.array([x0,y0,z0],np.float32)
        uv=uv*np.array([u1-u0,v1-v0],np.float32)+np.array([u0,v0],np.float32)
    out.update(p=p,uv=uv,n=n,typ=typ,stride=stride); return out
def triangles(M):
    """returns list of (V(n,3),UV,N,tris(k,3),matidx(k)) per mesh"""
    res=[]
    comp=M['comps'][0] if M['comps'] else None
    for me in M['meshes']:
        if me['vbi'][0]<0 or me['ibi'][0]<0: continue
        vb=decode_vb(M['vbs'][me['vbi'][0]],comp)
        it,ix=M['ibs'][me['ibi'][0]]
        T=[];MI=[]
        for (mat,sort,st,cn,ss,sc,ptype,flags,vc) in me['parts']:
            seg=ix[st:st+cn]
            tr=strips(seg) if it==5 else [tuple(seg[i:i+3]) for i in range(0,len(seg)-2,3)]
            T+=tr; MI+=[mat]*len(tr)
        res.append(dict(vb=vb,T=np.array(T,np.int64).reshape(-1,3),M=np.array(MI),rigid=me['rigid']))
    return res
def write_obj(path,M,parts):
    with open(path,'w') as f:
        base=1
        for mi,pt in enumerate(parts):
            V=pt['vb']['p']; UV=pt['vb']['uv']
            for v in V: f.write('v %.5f %.5f %.5f\n'%tuple(v*3.048))
            for t in UV: f.write('vt %.5f %.5f\n'%(t[0],1-t[1]))
            cur=None
            for (a,b,c),m in zip(pt['T'],pt['M']):
                if m!=cur: f.write('g mesh%d_mat%d\n'%(mi,m)); cur=m
                f.write('f %d/%d %d/%d %d/%d\n'%(a+base,a+base,b+base,b+base,c+base,c+base))
            base+=len(V)
if __name__=='__main__':
    r=RR(sys.argv[1]); nm=r.names()
    k,o=find_tag(r,'mode',sys.argv[2],nm)
    M=model(r,o,nm)
    print('nodes',len(M['nodes']),'mats',M['mats'][:12],'meshes',len(M['meshes']),[ (v[0],v[1],v[2]) for v in M['vbs']])
    P=triangles(M)
    for p in P: print(len(p['vb']['p']),len(p['T']),p['vb']['p'].min(0),p['vb']['p'].max(0),p['vb']['uv'].min(0),p['vb']['uv'].max(0))
    write_obj(sys.argv[3],M,P)
