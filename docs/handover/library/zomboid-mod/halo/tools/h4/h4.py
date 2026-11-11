import struct,sys,mmap
class H4:
    def __init__(s,path):
        s._f=open(path,'rb'); s.d=d=mmap.mmap(s._f.fileno(),0,access=mmap.ACCESS_READ)
        s.vbase,s.tagidx=struct.unpack_from('<QQ',d,0x2d8)
        s.tbase=struct.unpack_from('<I',d,0x4e0)[0]+struct.unpack_from('<I',d,0x4c8)[0]
        s.ih=s.tbase+(s.tagidx-s.vbase)
        assert d[s.ih+0x48:s.ih+0x4c]==b'sgat'
        g=s.u32(s.ih); ga=s.u64(s.ih+8); tc=s.u32(s.ih+0x10); ta=s.u64(s.ih+0x18)
        s.groups=[d[s.offI(ga)+k*16:s.offI(ga)+k*16+4][::-1].decode('latin1') for k in range(g)]
        s.tags=[]
        for k in range(tc):
            gi,salt,addr=struct.unpack_from('<hHI',d,s.offI(ta)+k*8)
            s.tags.append((s.groups[gi] if gi>=0 else None,(salt<<16)|k,addr))
    def u32(s,o): return struct.unpack_from('<I',s.d,o)[0]
    def u64(s,o): return struct.unpack_from('<Q',s.d,o)[0]
    def off(s,a): return a-s.vbase+s.tbase-0x10000
    def offI(s,a): return a-s.vbase+s.tbase
    def toff(s,addr32): return s.off(addr32*4+0x50000000) if False else s.off(s.X(addr32))
    def X(s,a): return a*4+0x50000000
def names(s):
    d=s.d; cnt,fto,fts,fio=struct.unpack_from('<IIII',d,0x20); a0=s.u32(0x4c4)
    bo=(fto+a0)&0xffffffff; io=(fio+a0)&0xffffffff
    out=[]
    for k in range(cnt):
        o=bo+struct.unpack_from('<i',d,io+4*k)[0]; e=d.find(b'\0',o); out.append(d[o:e].decode('latin1'))
    return out
def strings(s):
    if hasattr(s,'_S'): return s._S
    d=s.d; a0=s.u32(0x4c4)
    cnt,tbl,sz,idx=struct.unpack_from('<IIII',d,0x30)
    tbl=(tbl+a0)&0xffffffff; idx=(idx+a0)&0xffffffff
    out=[]
    for i in range(cnt):
        v=struct.unpack_from('<i',d,idx+4*i)[0]
        if v<0: out.append(''); continue
        o=tbl+v; e=d.find(b'\0',o); out.append(d[o:e].decode('latin1'))
    s._S=out; return out
H4.names=names; H4.strings=strings
