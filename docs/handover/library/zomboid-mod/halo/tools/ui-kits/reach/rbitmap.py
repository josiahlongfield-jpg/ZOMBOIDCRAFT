import sys,struct,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from rres import RR
from reach import X
from PIL import Image
FOURCC={14:b'DXT1',15:b'DXT3',16:b'DXT5',38:b'ATI2',36:b'ATI1',43:b'ATI1',42:b'ATI1'}
BPB={14:8,15:16,16:16,38:16,36:8,43:8,42:8}
def dds(w,h,fmt,data):
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
