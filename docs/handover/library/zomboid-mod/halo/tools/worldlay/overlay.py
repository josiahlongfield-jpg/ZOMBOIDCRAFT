"""RGBA true-scale top-down overlays of chosen Reach BSPs, for the world layout preview."""
import os,sys,json,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from survey import load, samples
from PIL import Image
OUT='/tmp/claude-0/halo/worldlay/ov'; os.makedirs(OUT,exist_ok=True)
def ov(name,m,bsps=None,px=2.0,skip=(),big=False,crop=None,pad=0):
    B=load(m); grp=[]
    for s,V,T,M in B:
        ext=V.max(0)-V.min(0)
        if len(T)<200 or (max(ext[0],ext[2])>6000 and not big): continue
        if bsps and s not in bsps: continue
        if s in skip: continue
        grp.append((V,T))
    if crop:
        pv=np.concatenate([V for s,V,T,M in B if s in crop])
        lo=np.percentile(pv,0.3,axis=0)-pad; hi=np.percentile(pv,99.7,axis=0)+pad
        hi[1]=pv[:,1].max()+50; lo[1]=-1e9
    else:
        allV=np.concatenate([V for V,T in grp])
        lo=np.percentile(allV,0.3,axis=0); hi=np.percentile(allV,99.7,axis=0)
        lo-=10; hi+=10
    nx,nz=int((hi[0]-lo[0])/px)+1,int((hi[2]-lo[2])/px)+1
    hm=np.full((nz,nx),-1e9,np.float32)
    for V,T in grp:
        if crop:
            c=V[T].mean(1); k=(c[:,0]>lo[0])&(c[:,0]<hi[0])&(c[:,2]>lo[2])&(c[:,2]<hi[2])
            T=T[k]
            if not len(T): continue
        P=samples(V,T,px)
        ix=((P[:,0]-lo[0])/px).astype(np.int64); iz=((P[:,2]-lo[2])/px).astype(np.int64)
        ok=(ix>=0)&(ix<nx)&(iz>=0)&(iz<nz)&(P[:,1]<hi[1]+30)
        np.maximum.at(hm,(iz[ok],ix[ok]),P[ok,1])
    for _ in range(2):
        e=hm<=-1e8
        p=np.pad(hm,1,constant_values=-1e9)
        nb=np.max(np.stack([p[1+dz:1+dz+nz,1+dx:1+dx+nx] for dz in(-1,0,1) for dx in(-1,0,1)]),0)
        hm[e]=nb[e]
    has=hm>-1e8
    y0=np.percentile(hm[has],1); y1=np.percentile(hm[has],99)
    h=np.clip(np.where(has,hm,y0),y0,y1)
    gz,gx=np.gradient(h,px)
    shade=np.clip(0.55+0.45*((-gx-gz)/np.sqrt(gx*gx+gz*gz+1))*0.9,0,1)
    t=(h-y0)/max(1e-3,y1-y0)
    stops=np.array([[46,78,62],[96,118,74],[154,146,104],[196,188,168],[240,240,236]],np.float32)
    ti=np.clip(t*(len(stops)-1),0,len(stops)-1-1e-6); i0=ti.astype(int); f=ti-i0
    col=stops[i0]*(1-f)[...,None]+stops[i0+1]*f[...,None]
    col=np.clip(col*(0.35+0.75*shade)[...,None],0,255)
    rgba=np.dstack([col,np.where(has,255,0)]).astype(np.uint8)
    Image.fromarray(rgba,'RGBA').save(f'{OUT}/{name}.png',optimize=True)
    # true centre of footprint (row = +z south)
    zz,xx=np.nonzero(has)
    info=dict(name=name,map=m,px=px,w=nx*px,d=nz*px,lo=[float(lo[0]),float(lo[2])],
              y_min=round(float(y0),1),y_max=round(float(y1),1),
              area_km2=round(float(has.sum()*px*px/1e6),3),
              cx=float(lo[0]+xx.mean()*px),cz=float(lo[2]+zz.mean()*px))
    json.dump(info,open(f'{OUT}/{name}.json','w'))
    print(name,round(nx*px),'x',round(nz*px),'m',info['area_km2'],'km2',flush=True)
SITES={
 'm52':('m52',None,2.0,(),True,['m52_001','m52_010','m52_020','m52_030','m52_040'],250),
 'm50':('m50',None,2.0,()),
 'm20':('m20',None,1.5,()),
 'm60':('m60',None,2.0,()),
 'm45g':('m45',['m45_000','m45_001','m45_005','m45_010','m45_015'],2.0,()),
 'm30':('m30',None,1.5,()),
 'm10':('m10',None,2.0,()),
 'm35':('m35',None,2.0,('m35_001',)),
 'm70':('m70',None,2.0,()),
 '30_settlement':('30_settlement',None,1.0,()),
 '50_panopticon':('50_panopticon',None,1.0,()),
 'dlc_medium':('dlc_medium',None,1.0,()),
 'dlc_invasion':('dlc_invasion',None,1.0,()),
 'forge_halo':('forge_halo',None,1.5,()),
 'trainingpreserve':('trainingpreserve',None,1.0,()),
 'ff10_prototype':('ff10_prototype',None,1.0,()),
 '45_launch_station':('45_launch_station',None,1.0,()),
 'cex_beaver_creek':('cex_beaver_creek',None,1.0,()),
 'cex_timberland':('cex_timberland',None,1.0,()),
 'cex_headlong':('cex_headlong',None,1.0,(),True),
 'cex_hangemhigh':('cex_hangemhigh',None,1.0,(),True),
 'cex_ff_halo':('cex_ff_halo',None,1.0,(),True),
 'cv_ext':('m45',['m45_050'],1.0,(),True),
 'm70_bonus':('m70_bonus',None,1.0,(),True),
 'ff_unearthed':('ff_unearthed',None,1.0,(),True),
 '52_ivory_tower':('52_ivory_tower',None,0.5,()),
 'cex_prisoner':('cex_prisoner',None,1.0,()),
 'cex_damnation':('cex_damnation',None,1.0,()),
 # orbit
 'cv_hangar':('m45',['m45_060'],0.5,()),
 'cv_hall':('m45',['m45_065'],0.5,()),
 'cv_reactor':('m45',['m45_070'],0.5,()),
 'cv_bridge':('m45',['m45_080'],0.5,()),
 'cv_ff':('ff45_corvette',None,0.75,()),
 'cv_aft':('45_aftship',None,0.5,()),
 'anchor9':('dlc_slayer',None,0.5,()),
 'condemned':('condemned',None,0.5,()),
}
if __name__=='__main__':
    for k in (sys.argv[1:] or SITES):
        try: ov(k,*SITES[k])
        except Exception as e: print(k,'ERR',repr(e),flush=True)
