import numpy as np
from PIL import Image
def look(eye,tgt,up=(0,1,0)):
    eye=np.array(eye,float);f=np.array(tgt,float)-eye;f/=np.linalg.norm(f)
    r=np.cross(f,up);r/=np.linalg.norm(r);u=np.cross(r,f)
    return eye,np.stack([r,u,-f])
def render(V,T,cols=None,eye=(1,0.5,1),tgt=(0,0,0),W=800,H=600,fov=35,UV=None,tex=None,M=None,bg=(40,44,52),light=(0.4,0.8,0.5)):
    eye,R=look(eye,tgt); P=(V-eye)@R.T
    z=-P[:,2]; s=1/np.tan(np.radians(fov)/2)
    x=(P[:,0]/np.maximum(z,1e-6))*s*H/2+W/2; y=-(P[:,1]/np.maximum(z,1e-6))*s*H/2+H/2
    img=np.zeros((H,W,3),np.float32)+np.array(bg,np.float32); zb=np.full((H,W),1e9,np.float32)
    L=np.array(light,float);L/=np.linalg.norm(L)
    for ti,(a,b,c) in enumerate(T):
        if min(z[a],z[b],z[c])<=1e-4: continue
        n=np.cross(V[b]-V[a],V[c]-V[a]); nl=np.linalg.norm(n)
        if nl==0: continue
        n/=nl; sh=0.35+0.65*abs(n@L)
        xs=np.array([x[a],x[b],x[c]]); ys=np.array([y[a],y[b],y[c]])
        x0=max(int(xs.min()),0);x1=min(int(xs.max())+1,W-1);y0=max(int(ys.min()),0);y1=min(int(ys.max())+1,H-1)
        if x0>x1 or y0>y1: continue
        gx,gy=np.meshgrid(np.arange(x0,x1+1)+.5,np.arange(y0,y1+1)+.5)
        d=(ys[1]-ys[2])*(xs[0]-xs[2])+(xs[2]-xs[1])*(ys[0]-ys[2])
        if abs(d)<1e-9: continue
        w0=((ys[1]-ys[2])*(gx-xs[2])+(xs[2]-xs[1])*(gy-ys[2]))/d
        w1=((ys[2]-ys[0])*(gx-xs[2])+(xs[0]-xs[2])*(gy-ys[2]))/d
        w2=1-w0-w1; m=(w0>=0)&(w1>=0)&(w2>=0)
        if not m.any(): continue
        zz=w0*z[a]+w1*z[b]+w2*z[c]
        sub=zb[y0:y1+1,x0:x1+1]; m&=zz<sub
        if not m.any(): continue
        sub[m]=zz[m]
        if tex is not None and UV is not None and M is not None and tex.get(int(M[ti])) is not None:
            t=tex[int(M[ti])]; th,tw=t.shape[:2]
            # perspective-correct
            iz=w0/z[a]+w1/z[b]+w2/z[c]
            u=(w0*UV[a,0]/z[a]+w1*UV[b,0]/z[b]+w2*UV[c,0]/z[c])/iz
            v=(w0*UV[a,1]/z[a]+w1*UV[b,1]/z[b]+w2*UV[c,1]/z[c])/iz
            px=(np.mod(u,1)*tw).astype(int)%tw; py=(np.mod(v,1)*th).astype(int)%th
            col=t[py[m],px[m],:3].astype(np.float32)*sh
        else:
            cc=np.array(cols[ti] if cols is not None else (200,200,200),np.float32)
            col=cc*sh
        img[y0:y1+1,x0:x1+1][m]=col
    return Image.fromarray(np.clip(img,0,255).astype(np.uint8))
