import struct,sys
class Map:
    def __init__(s,path):
        s.d=d=open(path,'rb').read()
        s.idx=struct.unpack_from('<I',d,0x10)[0]
        s.base,s.scen,_,s.count=struct.unpack_from('<IIII',d,s.idx)
        s.magic=s.base-0x28  # address of index header
        s.tags=[]
        arr=s.off(s.base)
        for i in range(s.count):
            c1,c2,c3,tid,name,data,ext,pad=struct.unpack_from('<4s4s4sIIIII',d,arr+i*32)
            s.tags.append(dict(cls=c1[::-1].decode('latin1'),id=tid,name=s.cstr(s.off(name)),data=data,ext=ext))
        s.byid={t['id']:t for t in s.tags}
    def off(s,addr): return addr-s.magic+s.idx
    def cstr(s,o):
        e=s.d.index(b'\0',o); return s.d[o:e].decode('latin1')
if __name__=='__main__':
    m=Map(sys.argv[1]); sc=m.byid[m.scen]; print(sc)
    for t in m.tags:
        if t['cls'] in ('sbsp','scnr'): print(t)
    o=m.off(sc['data'])+0x5A4
    n,a,_=struct.unpack_from('<III',m.d,o); print('bsps',n,hex(a))
    e=m.off(a)
    for i in range(n):
        fo,sz,mg,pad=struct.unpack_from('<IIII',m.d,e+i*32); cls,np,nl,tid=struct.unpack_from('<4sIII',m.d,e+i*32+16)
        print(hex(fo),sz,hex(mg),cls[::-1],m.byid.get(tid,{}).get('name'))
