import struct,sys,zlib,re
VBASE=0x1cc670000
class Reach:
    def __init__(s,path):
        import mmap; s._f=open(path,'rb'); s.d=d=mmap.mmap(s._f.fileno(),0,access=mmap.ACCESS_READ)
        sig=d.index(b'sgat\x00',0x100000) if False else None
        s.vbase=struct.unpack_from('<Q',d,0x2e0)[0]
        s.tagidx=struct.unpack_from('<Q',d,0x2e8)[0]
        s.tbase=struct.unpack_from('<I',d,0x4d0)[0]+struct.unpack_from('<I',d,0x4e8)[0]
        s.ih=s.tbase+(s.tagidx-s.vbase)
        assert d[s.ih+0x48:s.ih+0x4c]==b'sgat', d[s.ih+0x48:s.ih+0x4c]
        g=s.u32(s.ih); ga=s.u64(s.ih+8); tc=s.u32(s.ih+0x10); ta=s.u64(s.ih+0x18)
        s.groups=[]
        for k in range(g):
            o=s.off(ga)+k*16
            s.groups.append(d[o:o+4][::-1].decode('latin1'))
        s.tags=[]
        for k in range(tc):
            o=s.off(ta)+k*8
            gi,salt,addr=struct.unpack_from('<hHI',d,o)
            s.tags.append((s.groups[gi] if gi>=0 else None, (salt<<16)|k, addr))
    def names(s):
        d=s.d; cnt,fto,fts,fio=struct.unpack_from('<IIII',d,0x20); a0=struct.unpack_from('<I',d,0x4cc)[0]
        bo=(fto+a0)&0xffffffff; io=(fio+a0)&0xffffffff
        out=[]
        for k in range(cnt):
            o=bo+struct.unpack_from('<I',d,io+4*k)[0]; e=d.find(b'\0',o); out.append(d[o:e].decode('latin1'))
        return out
    def u32(s,o): return struct.unpack_from('<I',s.d,o)[0]
    def u64(s,o): return struct.unpack_from('<Q',s.d,o)[0]
    def off(s,a): return a-s.vbase+s.tbase
    def tagoff(s,addr32): return s.off(addr32*4+ (s.vbase & ~0xFFFFFFFF) if False else s.vbase - (s.vbase % 0x10000000) + addr32*4) if False else s.off(s.expand(addr32))
    def expand(s,a): return (a<<2) + 0x50000000 if False else (a*4)
if __name__=='__main__':
    r=Reach(sys.argv[1])
    print(hex(r.ih),hex(r.vbase),hex(r.tagidx),hex(r.tbase),len(r.groups),len(r.tags))
    import collections
    c=collections.Counter(t[0] for t in r.tags); print(c.most_common(40))
    for t in r.tags:
        if t[0] in ('scnr','sbsp'): print(t[0],hex(t[1]),hex(t[2]),hex(t[2]*4))

def X(a): return a*4+0x50000000
def _strings(s):
    if hasattr(s,'_S'): return s._S
    d=s.d; a0=struct.unpack_from('<I',d,0x4cc)[0]
    cnt,tbl,sz,idx=struct.unpack_from('<IIII',d,0x30)
    tbl=(tbl+a0)&0xffffffff; idx=(idx+a0)&0xffffffff
    out=[]
    for i in range(cnt):
        o=tbl+struct.unpack_from('<i',d,idx+4*i)[0]; e=d.find(b'\0',o); out.append(d[o:e].decode('latin1'))
    s._S=out; return out
def sid(s,v):
    S=_strings(s); i=v&0x1ffff
    if v>>17: return '?%x'%v
    if i>=0x4ca: i+=0x128b
    return S[i] if i<len(S) else '?%x'%v
Reach.sid=sid
