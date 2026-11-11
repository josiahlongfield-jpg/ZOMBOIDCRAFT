import sys,struct
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR,ptr
def coll_tris(prim,blocks):
    """blocks: list of 9 (count, prim offset). returns verts, tris(with material)."""
    (ns,so),(ne,eo),(nv,vo)=blocks[6],blocks[7],blocks[8]
    V=[struct.unpack_from('<3f',prim,vo+i*16) for i in range(nv)]
    E=[struct.unpack_from('<6H',prim,eo+i*12) for i in range(ne)]
    T=[]
    for s in range(ns):
        pl,fe,mat=struct.unpack_from('<HHh',prim,so+s*12)
        poly=[];ei=fe
        for _ in range(128):
            sv,ev,fw,rv,ls,rs=E[ei]
            if ls==s: poly.append(sv); ei=fw
            else: poly.append(ev); ei=rv
            if ei==fe: break
        for k in range(1,len(poly)-1): T.append((poly[0],poly[k],poly[k+1],mat))
    return V,T
def blocks_at(R,base):
    c=R['ctrl'];out=[]
    for b in range(9):
        n=struct.unpack_from('<I',c,base+b*12)[0]; f=R['fix'].get(base+b*12+4)
        out.append((n, (f&0x0fffffff) if f is not None else 0))
    return out
def bsp_resources(r):
    sb={t[1]:t for t in r.tags if t[0]=='sbsp'}
    out={}
    for i,o in enumerate(r.res):
        dat=struct.unpack_from('<I',r.d,o+12)[0]; typ=r.d[o+18]
        if dat in sb: out.setdefault(dat,{})[typ]=i
    return out
if __name__=='__main__':
    import numpy as np
    r=RR(sys.argv[1])
    for dat,ty in bsp_resources(r).items():
        print(hex(dat),ty)
        if 5 not in ty: continue
        R=r.resource(ty[5]); c=R['ctrl']; rt=R['root']
        n=struct.unpack_from('<I',c,rt)[0]; f=R['fix'].get(rt+4)
        ni=struct.unpack_from('<I',c,rt+0x18)[0]
        print(' coll bsps',n,'instances',ni)
        if n:
            V,T=coll_tris(R['prim'],blocks_at(R,f&0x0fffffff))
            a=np.array(V); print(' verts',len(V),'tris',len(T),a.min(0).round(1),a.max(0).round(1))
