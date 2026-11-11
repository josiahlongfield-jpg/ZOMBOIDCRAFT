import sys,struct,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo'); sys.path.insert(0,'/tmp/claude-0/h4')
from rbitmap import top_size
from h4res import R4
from PIL import Image
def raw(r,o,which=0):
    d=r.d; ni,ia=struct.unpack_from('<II',d,o+0x7c); ia=r.off(r.X(ia)); nr,ra=struct.unpack_from('<II',d,o+0xa8); ra=r.off(r.X(ra))
    e=ia+which*0x30; w,h,depth=struct.unpack_from('<HHB',d,e); fmt,typ=struct.unpack_from('<HH',d,e+8)
    R=r.resource(struct.unpack_from('<I',d,ra+which*8)[0]&0xffff); n=top_size(w,h,fmt)
    for src in (R['ter'],R['sec'],R['prim']):
        if len(src)>=n: break
    return bytes(src[:n]),w,h,fmt
def ch(blocks,signed):
    # blocks: (N,8) uint8 -> (N,16) float
    if signed: a0=blocks[:,0].view(np.int8).astype(np.float32)/127; a1=blocks[:,1].view(np.int8).astype(np.float32)/127
    else: a0=blocks[:,0].astype(np.float32)/255; a1=blocks[:,1].astype(np.float32)/255
    a0=np.maximum(a0,-1); a1=np.maximum(a1,-1)
    if signed: c0=blocks[:,0].view(np.int8).astype(np.int16); c1=blocks[:,1].view(np.int8).astype(np.int16)
    else: c0=blocks[:,0].astype(np.int16); c1=blocks[:,1].astype(np.int16)
    big=c0>c1
    pal=np.zeros((len(blocks),8),np.float32); pal[:,0]=a0; pal[:,1]=a1
    for i in range(1,7):
        pal[:,i+1]=np.where(big,((7-i)*a0+i*a1)/7, pal[:,i+1])
    for i in range(1,5):
        pal[:,i+1]=np.where(~big,((5-i)*a0+i*a1)/5, pal[:,i+1])
    pal[:,6]=np.where(~big,-1.0 if signed else 0.0,pal[:,6]); pal[:,7]=np.where(~big,1.0,pal[:,7])
    bits=np.zeros(len(blocks),np.uint64)
    for k in range(6): bits|=blocks[:,2+k].astype(np.uint64)<<np.uint64(8*k)
    idx=np.stack([(bits>>np.uint64(3*j))&np.uint64(7) for j in range(16)],1).astype(np.int64)
    return np.take_along_axis(pal,idx,1)
def bc5(data,w,h,signed):
    b=np.frombuffer(data,np.uint8).reshape(-1,16); bw,bh=(w+3)//4,(h+3)//4
    x=ch(b[:,:8].copy(),signed); y=ch(b[:,8:].copy(),signed)
    def img(v): return v.reshape(bh,bw,4,4).transpose(0,2,1,3).reshape(bh*4,bw*4)[:h,:w]
    return img(x),img(y)
if __name__=='__main__':
    r=R4(sys.argv[1]); N=r.names()
    for name in sys.argv[2:]:
        k=[i for i,t in enumerate(r.tags) if t[0]=='bitm' and N[i].endswith('\\'+name)][0]
        data,w,h,fmt=raw(r,r.off(r.X(r.tags[k][2])))
        for signed in (True,False):
            x,y=bc5(data,w,h,signed); z=np.sqrt(np.clip(1-x*x-y*y,0,1)) if signed else None
            if not signed: x,y=x*2-1,y*2-1; z=np.sqrt(np.clip(1-x*x-y*y,0,1))
            rr=np.hypot(x,y); print(name,'signed' if signed else 'unsigned','fmt',fmt,w,h,'|xy|>0.9',(rr>0.9).mean().round(4),'mean',rr.mean().round(3))
            img=np.stack([x*0.5+0.5,y*0.5+0.5,z*0.5+0.5,np.ones_like(x)],-1)
            Image.fromarray((img*255+0.5).clip(0,255).astype(np.uint8)).save('/tmp/claude-0/h4/snorm/%s_%s.png'%(name,'s' if signed else 'u'))
