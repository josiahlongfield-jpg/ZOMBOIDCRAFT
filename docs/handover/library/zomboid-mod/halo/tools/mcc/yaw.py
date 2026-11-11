import sys,os,numpy as np
sys.argv=[sys.argv[0]]
import mccpose, anim, poseglb as pg
G=mccpose.G
B0=pg.world(G,{},{})
def fwd(Wd,bone):
    L=np.linalg.inv(B0[bone][:3,:3])@np.array([0,0,1.0])
    f=Wd[bone][:3,:3]@L; return np.degrees(np.arctan2(f[0],f[2]))
def yaws(n,fr=0):
    A=mccpose.absq(n,fr); Wd=pg.world(G,{k:v[0] for k,v in A.items()},{k:v[1] for k,v in A.items()})
    return fwd(Wd,'b_pelvis'),fwd(Wd,'b_head'),Wd['b_pelvis'][:3,3]
if __name__=='__main__':
    for n in sorted(f[:-7] for f in os.listdir(anim.BASE+'Animations/') if f.endswith('.uasset')):
        p,h,t=yaws(n); print('%-24s pelvis %7.1f head %7.1f  at %s'%(n,p,h,np.round(t,2)))
