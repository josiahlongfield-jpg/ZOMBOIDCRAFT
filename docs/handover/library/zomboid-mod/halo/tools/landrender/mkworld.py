import numpy as np, struct, json, glob, sys
from PIL import Image
SEA=-14.0
H=np.load('out/height.npy');clim=np.load('out/clim.npy');mask=np.load('out/mask.npy');sea=np.load('out/sea16.npy');hn=np.load('out/hnan.npy')
N=1000;st=16.0
h=H[::4,::4].copy();m=mask[::4,::4];h[m==2]-=1.5
xs=np.arange(N)*st-8000+2;X,Z=np.meshgrid(xs,xs)
V=np.stack([X,h,Z],-1).reshape(-1,3).astype('f4')
cmap=np.array([1,3,10,1,8,9],'u1');C=cmap[clim].reshape(-1)
i=np.arange(N*N).reshape(N,N);a=i[:-1,:-1].ravel();b=i[:-1,1:].ravel();c=i[1:,:-1].ravel();d=i[1:,1:].ravel()
T=np.concatenate([np.stack([a,c,b],1),np.stack([b,c,d],1)]).astype('u4')
Vs=[V];Ts=[T];Cs=[C];n=len(V)
# sea surface
hn16=hn[::4,::4]
wet=(sea|hn16)&(h<SEA)
wy,wx=np.nonzero(wet[:-1,:-1])
q=np.stack([wx*st-8000+2,np.full(len(wx),SEA),wy*st-8000+2],1)
P=np.concatenate([q,q+[st,0,0],q+[0,0,st],q+[st,0,st]]).astype('f4');k=len(wx)
idx=np.arange(k)
Tw=np.concatenate([np.stack([idx,idx+2*k,idx+k],1),np.stack([idx+k,idx+2*k,idx+3*k],1)])+n
Vs.append(P);Ts.append(Tw.astype('u4'));Cs.append(np.full(len(P),2,'u1'));n+=len(P)
# mission collision near the camera
OFF={"m10":(-5257,-4204),"m20":(-167,-5261),"m60":(4936,-5992),"m35":(-4299,3102),"m52":(3983,1544),"m50":(6225,-2533),"m70":(655,5986)}
SH={"m10":100,"m20":0,"m60":-22,"m35":0,"m52":131,"m50":114,"m70":225};CL={"m60":11,"m70":12}
for mname in sys.argv[2].split(',') if len(sys.argv)>2 and sys.argv[2] else []:
    for x in sorted(glob.glob(f'/tmp/claude-0/halo/layout/{mname}/*_collision.npz')):
        if '_000_' in x or (mname=='m60' and 'm20_090' in x): continue
        dz=np.load(x);VV=dz['V'].astype('f4')+np.array([OFF[mname][0],SH[mname],OFF[mname][1]],'f4')
        Vs.append(VV);Ts.append(dz['T'][:,:3].astype('u4')+n);Cs.append(np.full(len(VV),CL.get(mname,0),'u1'));n+=len(VV)
V=np.concatenate(Vs);T=np.concatenate(Ts);C=np.concatenate(Cs);C=np.concatenate([C,np.zeros((-len(C))%4,'u1')])
open(sys.argv[1],'wb').write(struct.pack('<III',len(V),len(T),0)+V.tobytes()+T.tobytes()+C.tobytes())
print(len(V),len(T))
