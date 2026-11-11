# Paints the body skin under the sports bra and panties a plain skin colour, so nothing anatomical is in the texture.
import json, numpy as np, os, sys
from PIL import Image, ImageDraw, ImageFilter
from scipy.spatial import cKDTree
H=os.path.dirname(os.path.abspath(__file__))
J=json.load(open(f'{H}/geo.json')); B=open(f'{H}/geo.bin','rb').read()
def arr(m,k,t,c): return np.frombuffer(B,t,m['n' if k!='idx' else 't']*c if k!='idx' else m['t']*3,m[k]).reshape(-1,c)
M=J['meshes']; bare=[M[i] for i in J['looks']['bare|0']]
cover=np.concatenate([arr(m,'pos',np.float32,3) for m in bare if m['name'] in ('SportsBra','Short_Pants')])
pan=np.concatenate([arr(m,'pos',np.float32,3) for m in bare if m['name']=='Short_Pants']); lo=pan.min(0)-0.02; hi=pan.max(0)+0.02
print('panties box',lo,hi)
tree=cKDTree(cover)
size=4096
for t in (1001,1002,1003):
    src=Image.open(f'{H}/in/textures/Shion_Body_Nude_Diffuse.{t}.png').convert('RGB')
    mask=Image.new('L',(size,size),0); d=ImageDraw.Draw(mask)
    keep=Image.new('L',(size,size),0); dk=ImageDraw.Draw(keep)
    hit=0; tot=0
    for m in M:
        if m['mat']!='Shion_Body_Nude' or m['tile']!=t: continue
        P=arr(m,'pos',np.float32,3); U=arr(m,'uv',np.float32,2); I=arr(m,'idx',np.uint32,3)
        c=P[I].mean(axis=1)
        dist,_=tree.query(c)
        cov=(dist<0.02)|np.all((c>lo)&(c<hi),axis=1)
        # also anything between the bra cups / inside the panties' band that sits behind them
        tot+=len(I); hit+=int(cov.sum())
        for tri,cv in zip(I,cov):
            pts=[(float(U[k,0])*size,(1-float(U[k,1]))*size) for k in tri]
            (d if cv else dk).polygon(pts,fill=255)
    mask=mask.filter(ImageFilter.MaxFilter(9))
    keep=keep.filter(ImageFilter.MaxFilter(9))
    a=np.asarray(src).astype(np.float32); kp=(np.asarray(keep)>0)&~(np.asarray(mask)>0); mk=~kp
    skin=np.median(a[kp],axis=0) if kp.any() else np.array([200,160,140.])
    a[mk]=skin
    # smooth the filled skin into its surroundings (normalised blur of the kept skin, coarse to fine), so no flat patches
    from scipy.ndimage import gaussian_filter
    sm=1024; f=size//sm
    small=np.asarray(Image.fromarray(a.clip(0,255).astype(np.uint8)).resize((sm,sm),Image.BILINEAR)).astype(np.float32)
    known=np.asarray(Image.fromarray((kp*255).astype(np.uint8)).resize((sm,sm),Image.BILINEAR)).astype(np.float32)/255
    known=(known>0.99).astype(np.float32)
    fill=np.empty_like(small); fill[:]=skin
    for r in (96,48,24,12,6,3):
        w=gaussian_filter(known,r)
        num=np.stack([gaussian_filter(small[...,c]*known,r) for c in range(3)],-1)
        ok=w>0.05
        fill[ok]=num[ok]/w[ok,None]
    big=np.asarray(Image.fromarray(fill.clip(0,255).astype(np.uint8)).resize((size,size),Image.BILINEAR)).astype(np.float32)
    a[mk]=big[mk]
    out=Image.fromarray(a.clip(0,255).astype(np.uint8))
    out.save(f'{H}/tex/body_{t}.png')
    print(t,'covered tris',hit,'of',tot,'skin',skin)
