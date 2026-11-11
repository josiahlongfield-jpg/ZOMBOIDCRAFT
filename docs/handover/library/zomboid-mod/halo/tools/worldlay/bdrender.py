import sys,json,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo/worldlay'); sys.path.insert(0,'/tmp/claude-0/halo')
from glbtop import tris
from survey import load, samples
from PIL import Image
OUT='/tmp/claude-0/halo/worldlay/ov'
def grid(groups,lo,px,nx,nz,ycap):
    hm=np.full((nz,nx),-1e9,np.float32)
    for V,T in groups:
        c=V[T].mean(1); k=(c[:,0]>lo[0]-500)&(c[:,0]<lo[0]+nx*px+500)&(c[:,2]>lo[1]-500)&(c[:,2]<lo[1]+nz*px+500)&(c[:,1]<ycap)
        T=T[k]
        if not len(T): continue
        P=samples(V,T,px)
        ix=((P[:,0]-lo[0])/px).astype(np.int64); iz=((P[:,2]-lo[1])/px).astype(np.int64)
        ok=(ix>=0)&(ix<nx)&(iz>=0)&(iz<nz)&(P[:,1]<ycap)
        np.maximum.at(hm,(iz[ok],ix[ok]),P[ok,1])
    for _ in range(3):
        e=hm<=-1e8
        if not e.any(): break
        p=np.pad(hm,1,constant_values=-1e9)
        nb=np.max(np.stack([p[1+dz:1+dz+nz,1+dx:1+dx+nx] for dz in(-1,0,1) for dx in(-1,0,1)]),0); hm[e]=nb[e]
    return hm
def run(m,glb,half=6000,px=8.0,collmap=None):
    sv=json.load(open(f'/tmp/claude-0/halo/survey/{collmap or m}.json'))
    play=[(V,T) for s,V,T,M in load(collmap or m) if any(b['bsp']==s and b['kind']=='play' for b in sv['bsps'])]
    if m=='m45': play=[(V,T) for s,V,T,M in load('m45') if s in('m45_000','m45_001','m45_005','m45_010','m45_015')]
    pv=np.concatenate([V for V,T in play]); c=(np.percentile(pv,2,axis=0)+np.percentile(pv,98,axis=0))/2
    ytop=np.percentile(pv[:,1],99.5)+600
    lo=(c[0]-half,c[2]-half); n=int(2*half/px)
    G=tris(glb)
    hb=grid(G,lo,px,n,n,ytop); hp=grid(play,lo,px,n,n,ytop)
    has=hb>-1e8; hasp=hp>-1e8
    h=np.where(hasp,np.maximum(hp,np.where(has,hb,-1e9)),hb)
    allh=h[has|hasp]; y0=np.percentile(allh,2); y1=np.percentile(allh,99)
    hh=np.clip(np.where(has|hasp,h,y0),y0,y1)
    gz,gx=np.gradient(hh,px); shade=np.clip(0.55+0.45*((-gx-gz)/np.sqrt(gx*gx+gz*gz+1))*0.9,0,1)
    t=(hh-y0)/max(1,y1-y0)
    stops=np.array([[40,72,60],[86,110,70],[140,134,98],[184,176,156],[236,236,232]],np.float32)
    ti=np.clip(t*(len(stops)-1),0,len(stops)-1-1e-6); i0=ti.astype(int); f=ti-i0
    col=(stops[i0]*(1-f)[...,None]+stops[i0+1]*f[...,None])*(0.35+0.75*shade)[...,None]
    col=np.where(hasp[...,None],col*1.0,col*0.72)
    # outline play area
    e=hasp&~(np.roll(hasp,1,0)&np.roll(hasp,-1,0)&np.roll(hasp,1,1)&np.roll(hasp,-1,1))
    col[e]=[217,164,65]
    a=np.where(has|hasp,255,0)
    Image.fromarray(np.dstack([np.clip(col,0,255),a]).astype(np.uint8),'RGBA').save(f'{OUT}/land_{m}.png',optimize=True)
    info=dict(map=m,px=px,size=2*half,cx=float(c[0]),cz=float(c[2]),y_min=round(float(y0)),y_max=round(float(y1)),
        land_km2=round(float(has.sum()*px*px/1e6),1),play_km2=round(float(hasp.sum()*px*px/1e6),2))
    json.dump(info,open(f'{OUT}/land_{m}.json','w')); print(info,flush=True)
if __name__=='__main__':
    B='/tmp/claude-0/halo/bdglb'
    for m in sys.argv[1:]:
        try: run(m,f'{B}/{m}.glb',collmap=None)
        except Exception as ex: print(m,'ERR',repr(ex),flush=True)
