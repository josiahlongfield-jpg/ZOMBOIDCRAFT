import sys,struct,zlib
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from reach import Reach,X
RES_BASE=0x167000
class RR(Reach):
    def __init__(s,path):
        super().__init__(path)
        d=s.d
        play=[t for t in s.tags if t[0]=='play'][0]; po=s.off(X(play[2]))
        def blk(o,k): c,a=struct.unpack_from('<II',d,o+k); return c,s.off(X(a))
        npg,pa=blk(po,0x18); nseg,sa=blk(po,0x3c)
        s.pages=[struct.unpack_from('<HBbhhIII',d,pa+i*88) for i in range(npg)]
        s.segs=[struct.unpack_from('<hhiihh',d,sa+i*16) for i in range(nseg)]
        zone=[t for t in s.tags if t[0]=='zone'][0]; zo=s.off(X(zone[2]))
        nr,ra=blk(zo,0x64); s.res=[ra+i*64 for i in range(nr)]
        size=struct.unpack_from('<I',d,zo+0x148)[0]; s.defo=s.off(X(struct.unpack_from('<I',d,zo+0x154)[0])); s.defsize=size
        s.cache={}
        ns,fa=blk(po,0xc)
        import os,mmap
        s.shared=[]
        for i in range(ns):
            o=fa+i*0x108; e=d.find(b'\0',o); nm=d[o:e].decode('latin1').split('\\')[-1]
            pth=os.path.join(os.path.dirname(path),nm)
            if os.path.exists(pth):
                f=open(pth,'rb'); s.shared.append((f,mmap.mmap(f.fileno(),0,access=mmap.ACCESS_READ)))
            else: s.shared.append(None)
    def page(s,i):
        if i in s.cache: return s.cache[i]
        salt,flags,codec,sh,idx,off,comp,unc=s.pages[i]
        src=s.d if sh==-1 else (s.shared[sh][1] if s.shared[sh] else None)
        if src is None: raise Exception('page in missing shared map %d'%sh)
        rb=struct.unpack_from("<I",src,0x4d0)[0]; raw=src[rb+off:rb+off+comp]
        out=zlib.decompress(raw,-15) if comp!=unc else raw
        if len(s.cache)>2: s.cache.clear()
        s.cache[i]=out; return out
    def resource(s,i):
        """(control bytes with fixups applied as ('def'|'page', offset) map, root offset, page data)"""
        o=s.res[i]; d=s.d
        salt,typ,flags,coff,csize,_,_,seg,root=struct.unpack_from('<HBBIIIHHI',d,o+16)
        ctrl=bytearray(d[s.defo+coff:s.defo+coff+csize])
        fc,fa=struct.unpack_from('<II',d,o+0x28); fa=s.off(X(fa))
        fix={}
        for k in range(fc):
            bo,addr=struct.unpack_from('<II',d,fa+k*8); fix[bo]=addr
        ps,ss,po,so_,_,_=s.segs[seg]
        prim=memoryview(s.page(ps))[po:] if ps>=0 else b''
        sec=memoryview(s.page(ss))[so_:] if ss>=0 else b''
        return dict(typ=typ,ctrl=bytes(ctrl),fix=fix,root=root&0x0fffffff,prim=prim,sec=sec)
def ptr(r,res,addr):
    t=addr>>28; o=addr&0x0fffffff
    if t==2: return ('ctrl',o)
    if t==4: return ('prim',o)
    if t==6: return ('sec',o)
    return ('?%d'%t,o)
