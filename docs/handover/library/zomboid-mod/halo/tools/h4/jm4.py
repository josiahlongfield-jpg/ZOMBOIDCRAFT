import sys,struct; sys.path.insert(0,'/tmp/claude-0/h4')
from h4res import R4
import h4model as M
def sidn(S,v):
    a=S[v] if v<len(S) else ''; b=S[v+0x1ab9] if v+0x1ab9<len(S) else ''
    return (a,b)
def jmad(r,name,nm=None,S=None):
    nm=nm or r.names(); S=S or r.strings(); d=r.d
    k,o=M.find(r,'jmad',name,nm)
    def blk(off): c,a=struct.unpack_from('<II',d,o+off); return c,(r.off(r.X(a)) if c else 0)
    c,b=blk(0x9c); anims=[]
    for i in range(c):
        e=b+i*64; v=struct.unpack_from('<I',d,e)[0]
        cc,aa=struct.unpack_from('<II',d,e+52); sd=r.off(r.X(aa)) if cc else 0
        sh=d[sd:sd+0x40] if cc else b''
        anims.append(dict(name=sidn(S,v),raw=d[e:e+64],shared=sh))
    rc,rb=blk(0x1e4); groups=[struct.unpack_from('<II',d,rb+i*12) for i in range(rc)]
    return o,anims,groups
