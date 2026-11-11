"""Reach sbsp render geometry reader (Sword Base first)."""
import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
from rmodel import strips
RG=0x340
def blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(X(a)) if c else 0)
def geometry(r,o,RG=RG):
    d=r.d
    nmesh,mea=blk(r,o,RG+4); nc,ca=blk(r,o,RG+0x10)
    comps=[struct.unpack_from('<10f',d,ca+i*52+4) for i in range(nc)]
    resd=struct.unpack_from('<I',d,o+RG+0x94)[0]
    R=r.resource(resd&0xffff); c=R['ctrl']; prim=bytes(R['prim']); root=R['root']
    def arr(off):
        n=struct.unpack_from('<I',c,off)[0]; p=R['fix'].get(off+4,0)&0x0fffffff; return n,p
    nvb,vbp=arr(root+0x18); nib,ibp=arr(root+0x24)
    vbs=[];ibs=[]
    for i in range(nvb):
        inner=R['fix'][vbp+i*12]&0x0fffffff
        cnt,typ,stride,size=struct.unpack_from('<IHHI',c,inner); dp=R['fix'][inner+0x14]&0x0fffffff
        vbs.append((cnt,typ,stride,prim[dp:dp+size]))
    for i in range(nib):
        inner=R['fix'][ibp+i*12]&0x0fffffff
        typ=struct.unpack_from('<I',c,inner)[0]; size=struct.unpack_from('<I',c,inner+8)[0]; dp=R['fix'][inner+0x14]&0x0fffffff
        ibs.append((typ,np.frombuffer(prim[dp:dp+size],dtype='<u2')))
    meshes=[]
    for m in range(nmesh):
        e=mea+m*0x5c
        np_,pa=blk(r,e,0); ns,sa=blk(r,e,0xc)
        vbi=struct.unpack_from('<8h',d,e+0x18); ibi=struct.unpack_from('<2h',d,e+0x28)
        rigid=struct.unpack_from('<b',d,e+0x2e)[0]; vtype=d[e+0x2f]
        parts=[struct.unpack_from('<hhiihhBBH',d,pa+k*24) for k in range(np_)]
        subs=[struct.unpack_from('<iiHH',d,sa+k*16) for k in range(ns)]
        meshes.append(dict(vbi=vbi,ibi=ibi,rigid=rigid,vtype=vtype,parts=parts,subs=subs))
    return dict(meshes=meshes,comps=comps,vbs=vbs,ibs=ibs)
def decode(vb):
    cnt,typ,stride,raw=vb
    a=np.frombuffer(raw,dtype=np.uint8).reshape(cnt,stride)
    p=a[:,0:12].copy().view('<f4').reshape(cnt,3)
    return p,a
if __name__=='__main__':
    r=RR(sys.argv[1]); t=r.tags[int(sys.argv[2])]; o=r.off(X(t[2]))
    G=geometry(r,o,int(sys.argv[3],16))
    print('comps',len(G['comps']),G['comps'][:3])
    print('vbs',len(G['vbs']),'ibs',len(G['ibs']),set(v[2] for v in G['vbs']))
    for i,m in enumerate(G['meshes']):
        vb=G['vbs'][m['vbi'][0]] if m['vbi'][0]>=0 else None
        s=''
        if vb:
            p,_=decode(vb); s='%d st%d %s %s'%(vb[0],vb[2],p.min(0).round(1),p.max(0).round(1))
        print(i,'vbi',m['vbi'][:3],'ibi',m['ibi'],'rigid',m['rigid'],'vt',m['vtype'],'parts',len(m['parts']),'subs',len(m['subs']),s)
