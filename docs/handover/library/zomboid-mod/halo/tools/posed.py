# Spartan glb posed with a Reach clip, plus the weapon on b_r_grip, rendered with the textured raster.
import sys;sys.path.insert(0,'/tmp/claude-0/halo')
import numpy as np
import idlepose as ip
from fk import fk
from viewglb import scene
from raster import render
from PIL import Image
par=[n['parent'] for n in ip.MD['nodes']]
def build(glb, clip, f, weapon=None, grip='b_r_grip'):
    P=ip.prep(clip); rots,trans=ip.pose_at(P,f)
    V,T,UV,MM,tex=scene(glb,None,{},ip.to_abs(rots,trans))
    if weapon:
        Wd=fk(par,rots,trans); R,t=Wd[ip.S0.index(grip)]
        V2,T2,UV2,MM2,tex2=scene(weapon)
        h=np.stack([V2[:,2],V2[:,0],V2[:,1]],1)/3.048          # glTF -> Halo
        hw=h@R.T+t; g=np.stack([hw[:,1],hw[:,2],hw[:,0]],1)*3.048
        off=max(tex.keys(),default=-1)+1 if tex else 0; off=max(off,int(MM.max())+1)
        T=np.concatenate([T,T2+len(V)]); V=np.concatenate([V,g]); UV=np.concatenate([UV,UV2]); MM=np.concatenate([MM,MM2+off])
        for k,v in tex2.items(): tex[k+off]=v
    return V,T,UV,MM,tex
def shot(V,T,UV,MM,tex,out,eye=(0.35,0.12,1),c=(0,0.95,0),rad=2.4,W=600,H=800,bg=(40,44,52),fov=30):
    c=np.array(c); e=np.array(eye,float); e=e/np.linalg.norm(e)*rad*1.6
    render(V,T,None,eye=c+e,tgt=c,W=W,H=H,UV=UV,tex=tex,M=MM,fov=fov,bg=bg).save(out)
if __name__=='__main__':
    glb,clip,f,wpn,out=sys.argv[1:6]
    shot(*build(glb,clip,float(f),wpn if wpn!='-' else None),out)
