import sys,struct
sys.path.insert(0,'/tmp/claude-0/halo')
from reach import X
def shader_bitmaps(r,datum,nm):
    """list of bitmap tag names in the shader's texture list (first = base map usually)"""
    k=datum&0xffff
    if datum==0xffffffff or k>=len(r.tags): return []
    o=r.off(X(r.tags[k][2])); d=r.d
    n,a=struct.unpack_from('<II',d,o+0x38)
    if not n: return []
    p=r.off(X(a))
    nt,ta=struct.unpack_from('<II',d,p+0x10)
    if not nt: return []
    ta=r.off(X(ta)); out=[]
    for i in range(nt):
        dd=struct.unpack_from('<I',d,ta+i*24+0xc)[0]; kk=dd&0xffff
        out.append(nm[kk] if dd!=0xffffffff and kk<len(nm) else None)
    return out
def shader_datums(r,o):
    d=r.d; n,a=struct.unpack_from('<II',d,o+0x48); a=r.off(X(a)) if n else 0
    return [struct.unpack_from('<I',d,a+i*0x2c+0xc)[0] for i in range(n)]
