import numpy as np
from PIL import Image
H=np.load('out/height.npy');clim=np.load('out/clim.npy');sea=np.load('out/sea16.npy');hn=np.load('out/hnan.npy')
h=np.array(Image.fromarray(H,'F').resize((1000,1000),Image.BOX))
gy,gx=np.gradient(h,16.0)
n=np.dstack([-gx,np.ones_like(h),-gy]);n/=np.linalg.norm(n,axis=2,keepdims=True)
L=np.array([-0.5,0.7,-0.5]);L/=np.linalg.norm(L);sh=np.clip((n*L).sum(2),0,1)
pal=np.array([[46,84,104],[236,240,245],[150,158,140],[96,128,72],[160,146,96],[208,180,128]],float)
c=pal[np.where(clim==0,3,clim)]
slope=np.hypot(gx,gy);rock=np.clip((slope-0.6)/0.6,0,1)[...,None];c=c*(1-rock)+np.array([128,124,118])*rock
img=c*(0.45+0.75*sh[...,None])
hn16=np.array(Image.fromarray(hn.astype('u1')*255).resize((1000,1000),Image.BOX))>128
water=(h<-14)&(sea|hn16)
img[water]=[46,84,104]
Image.fromarray(np.clip(img,0,255).astype('u1')).save('land_top.jpg',quality=88)
