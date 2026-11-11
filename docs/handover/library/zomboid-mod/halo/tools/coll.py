import struct,sys,json
sys.path.insert(0,'/tmp/claude-0/halo')
from cemap import Map
def load(path):
    m=Map(path); d=m.d
    sc=m.byid[m.scen]; o=m.off(sc['data'])+0x5A4
    n,a,_=struct.unpack_from('<III',d,o); e=m.off(a)
    out=[]
    for bi in range(n):
        fo,sz,mg,_=struct.unpack_from('<IIII',d,e+bi*32)
        boff=lambda x:x-mg+fo
        so=boff(struct.unpack_from('<I',d,fo)[0])
        def refl(base,k):
            c,a,_=struct.unpack_from('<III',d,base+k); return c,boff(a)
        nm,am=refl(so,0xa4)
        mats=[]
        for i in range(nm):
            cls,np,nl,tid=struct.unpack_from('<4sIII',d,am+i*20)
            mats.append(m.byid.get(tid,{}).get('name','?'))
        nc,ac=refl(so,0xb0)
        R=[refl(ac,k*12) for k in range(8)]
        ns,asf=R[5]; ne,ae=R[6]; nv,av=R[7]
        V=[struct.unpack_from('<3f',d,av+i*16) for i in range(nv)]
        E=[struct.unpack_from('<6i',d,ae+i*24) for i in range(ne)]
        tris=[]
        for s in range(ns):
            pl,fe,fl,br,mat=struct.unpack_from('<iiBbh',d,asf+s*12)
            poly=[]; ei=fe
            for _ in range(64):
                sv,ev,fw,rv,ls,rs=E[ei]
                if ls==s: poly.append(sv); ei=fw
                else: poly.append(ev); ei=rv
                if ei==fe: break
            for k in range(1,len(poly)-1): tris.append((poly[0],poly[k],poly[k+1],mat,fl))
        out.append(dict(V=V,T=tris,mats=mats))
    return m,out
if __name__=='__main__':
    m,bs=load(sys.argv[1])
    for b in bs:
        import numpy as np
        V=np.array(b['V']); print(len(b['V']),len(b['T']),V.min(0),V.max(0))
        for i,n in enumerate(b['mats']): print(i,n)
