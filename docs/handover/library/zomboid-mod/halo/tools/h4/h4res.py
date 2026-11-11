import sys,struct,zlib,os,mmap
sys.path.insert(0,'/tmp/claude-0/h4')
from h4 import H4
class R4(H4):
    def __init__(s,path):
        super().__init__(path); d=s.d
        def blk(o,k): c,a=struct.unpack_from('<II',d,o+k); return c,s.off(s.X(a))
        play=[t for t in s.tags if t[0]=='play'][0]; po=s.off(s.X(play[2]))
        nf,fa=blk(po,0xc); npg,pa=blk(po,0x18); nseg,sa=blk(po,0x30)
        s.pages=[struct.unpack_from('<HBbhhIII',d,pa+i*88) for i in range(npg)]
        s.segs=[struct.unpack_from('<III6h',d,sa+i*24) for i in range(nseg)]
        zone=[t for t in s.tags if t[0]=='zone'][0]; zo=s.off(s.X(zone[2]))
        nr,ra=blk(zo,0x58); s.res=[ra+i*68 for i in range(nr)]
        s.defo=s.off(s.X(struct.unpack_from('<I',d,zo+0x160)[0])); s.defsize=struct.unpack_from('<I',d,zo+0x154)[0]
        s.shared=[]
        for i in range(nf):
            o=fa+i*0x108; e=d.find(b'\0',o); nm=d[o:e].decode('latin1').split('\\')[-1]
            p=os.path.join(os.path.dirname(os.path.abspath(path)),nm)
            if os.path.exists(p):
                f=open(p,'rb'); m=mmap.mmap(f.fileno(),0,access=mmap.ACCESS_READ); s.shared.append(m)
            else: s.shared.append(None)
        s.cache={}
    def page(s,i):
        if i in s.cache: return s.cache[i]
        salt,flags,codec,sh,idx,off,comp,unc=s.pages[i]
        src=s.d if sh==-1 else s.shared[sh]
        if src is None: raise Exception('page %d needs shared file %d'%(i,sh))
        rb=struct.unpack_from('<I',src,0x4c8)[0]; raw=src[rb+off:rb+off+comp]
        out=zlib.decompress(raw,-15) if comp!=unc else bytes(raw)
        if len(s.cache)>3: s.cache.clear()
        s.cache[i]=out; return out
    def resource(s,i):
        o=s.res[i]; d=s.d
        salt,typ,flags,csize,_,seg,root=struct.unpack_from('<HBBIHHI',d,o+16)
        c3,a3=struct.unpack_from('<II',d,o+0x38); coff=struct.unpack_from('<I',d,s.off(s.X(a3)))[0] if c3 else 0
        ctrl=bytes(d[s.defo+coff:s.defo+coff+csize])
        fc,fa=struct.unpack_from('<II',d,o+0x20); fa=s.off(s.X(fa)) if fc else 0
        fix={}
        for k in range(fc):
            bo,addr=struct.unpack_from('<II',d,fa+k*8); fix[bo]=addr
        po,so,to,pp,sp,tp=s.segs[seg][:6]
        def g(p,off):
            if p<0: return b''
            return memoryview(s.page(p))[off:]
        return dict(typ=typ,ctrl=ctrl,fix=fix,root=root&0x0fffffff,prim=g(pp,po),sec=g(sp,so),ter=g(tp,to),seg=s.segs[seg])
