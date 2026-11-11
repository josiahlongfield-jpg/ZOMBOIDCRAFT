import sys,struct,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
from reach import X
from PIL import Image
FOURCC={14:b'DXT1',15:b'DXT3',16:b'DXT5',38:b'ATI2',36:b'ATI1',43:b'ATI1',42:b'ATI1'}
BPB={14:8,15:16,16:16,38:16,36:8,43:8,42:8}

def _bc4s(blk):
    # blk: (N,8) uint8 -> (N,16) float in [-1,1], signed BC4
    e0=blk[:,0].astype(np.int8).astype(np.float32); e1=blk[:,1].astype(np.int8).astype(np.float32)
    e0=np.maximum(e0,-127)/127; e1=np.maximum(e1,-127)/127
    bits=np.zeros(len(blk),np.uint64)
    for i in range(6): bits|=blk[:,2+i].astype(np.uint64)<<np.uint64(8*i)
    idx=np.stack([((bits>>np.uint64(3*j))&np.uint64(7)).astype(np.int64) for j in range(16)],1)
    big=(blk[:,0].astype(np.int8)>blk[:,1].astype(np.int8))[:,None]
    pal8=np.stack([e0,e1]+[((7-i)*e0+i*e1)/7 for i in range(1,7)],1)
    pal6=np.stack([e0,e1]+[((5-i)*e0+i*e1)/5 for i in range(1,5)]+[-np.ones_like(e0),np.ones_like(e0)],1)
    pal=np.where(big,pal8,pal6)
    return np.take_along_axis(pal,idx,1)
def bc5s(w,h,data):
    bw,bh=max(1,(w+3)//4),max(1,(h+3)//4)
    b=np.frombuffer(data[:bw*bh*16],np.uint8).reshape(-1,16)
    x=_bc4s(b[:,:8]);y=_bc4s(b[:,8:])
    def tile(c): return c.reshape(bh,bw,4,4).transpose(0,2,1,3).reshape(bh*4,bw*4)[:h,:w]
    X_,Y_=tile(x),tile(y); Z=np.sqrt(np.clip(1-X_*X_-Y_*Y_,0,1))
    return Image.fromarray((np.dstack([X_,Y_,Z])*127.5+127.5).clip(0,255).astype(np.uint8))
SIGNED_DXN=True
def dds(w,h,fmt,data):
    if fmt==38 and SIGNED_DXN: return bc5s(w,h,data)
    if fmt in FOURCC:
        hdr=struct.pack('<4sIIIIIII44sII4sIIIIIIIIII',b'DDS ',124,0x1007|0x80000,h,w,len(data),0,1,b'\0'*44,32,4,FOURCC[fmt],0,0,0,0,0,0x1000,0,0,0,0)
        return Image.open(io.BytesIO(hdr+data))
    if fmt in (10,11):
        a=np.frombuffer(data[:w*h*4],np.uint8).reshape(h,w,4); return Image.fromarray(a[:,:,[2,1,0,3]])
    if fmt==0: a=np.frombuffer(data[:w*h],np.uint8).reshape(h,w); return Image.fromarray(a)
    if fmt in (1,2): a=np.frombuffer(data[:w*h],np.uint8).reshape(h,w); return Image.fromarray(a)
    if fmt==3: a=np.frombuffer(data[:w*h*2],np.uint8).reshape(h,w,2); return Image.fromarray(np.dstack([a[:,:,1]]*3+[a[:,:,0]]))
    if fmt==6:
        v=np.frombuffer(data[:w*h*2],'<u2').reshape(h,w); r=(v>>11)*255//31;g=((v>>5)&63)*255//63;b=(v&31)*255//31
        return Image.fromarray(np.dstack([r,g,b]).astype(np.uint8))
    raise Exception('fmt %d'%fmt)
def top_size(w,h,fmt):
    if fmt in BPB: return max(1,(w+3)//4)*max(1,(h+3)//4)*BPB[fmt]
    return w*h*{10:4,11:4,0:1,1:1,2:1,3:2,6:2}.get(fmt,4)
def bitmap(r,o,which=0):
    d=r.d
    ni,ia=struct.unpack_from('<II',d,o+0x7c); ia=r.off(X(ia))
    nr,ra=struct.unpack_from('<II',d,o+0xa8); ra=r.off(X(ra))
    e=ia+which*0x30
    w,h,depth=struct.unpack_from('<HHB',d,e); fmt,typ=struct.unpack_from('<HH',d,e+8)
    resd=struct.unpack_from('<I',d,ra+which*8)[0]
    R=r.resource(resd&0xffff)
    n=top_size(w,h,fmt)
    if typ==1: n*=6  # cubemap faces? keep first
    src=R['sec'] if len(R['sec'])>=top_size(w,h,fmt) else R['prim']
    data=bytes(src[:top_size(w,h,fmt)])
    return dds(w,h,fmt,data),(w,h,fmt,typ)
if __name__=='__main__':
    r=RR(sys.argv[1]);nm=r.names()
    for k,t in enumerate(r.tags):
        if t[0]=='bitm' and nm[k]==sys.argv[2]:
            im,info=bitmap(r,r.off(X(t[2]))); print(info); im.save(sys.argv[3])
