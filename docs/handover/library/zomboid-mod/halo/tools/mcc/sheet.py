import sys,os,time,numpy as np
sys.argv=[sys.argv[0]]
import mccpose, orender, posed, anim
B=anim.BASE+'Animations/'
names=sorted(f[:-7] for f in os.listdir(B) if f.endswith('.uasset'))
D='/tmp/claude-0/mcc/anim/sheet2'; os.makedirs(D,exist_ok=True)
for n in [x for x in names if "Rifle" in x]:
    out='%s/%s.png'%(D,n)
    w='/tmp/claude-0/halo/exp/noble/w_ar.glb' if 'Rifle' in n else None
    V,T,UV,MM,tex=mccpose.scene(orender.BASE,n,0,w)
    lo,hi=V.min(0),V.max(0); c=(lo+hi)/2; c[1]=max(c[1],0.95)
    posed.shot(V,T,UV,MM,tex,out,W=300,H=400,c=tuple(c),rad=2.6)
    print(n,flush=True)
