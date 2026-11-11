import sys,struct,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo'); sys.path.insert(0,'/tmp/claude-0/h4')
from rbitmap import dds,top_size
def bitmap(r,o,which=0):
    d=r.d
    ni,ia=struct.unpack_from('<II',d,o+0x7c); ia=r.off(r.X(ia))
    nr,ra=struct.unpack_from('<II',d,o+0xa8); ra=r.off(r.X(ra))
    e=ia+which*0x30
    w,h,depth=struct.unpack_from('<HHB',d,e); fmt,typ=struct.unpack_from('<HH',d,e+8)
    R=r.resource(struct.unpack_from('<I',d,ra+which*8)[0]&0xffff)
    n=top_size(w,h,fmt)
    for src in (R['ter'],R['sec'],R['prim']):
        if len(src)>=n: break
    return dds(w,h,fmt,bytes(src[:n])),(w,h,fmt,typ)
