"""Bake a Reach BSP's real lightmap (dual VMF, see lmdec) into one RGB light atlas matching the glb's TEXCOORD_1.
usage: lmbake.py <map> <sbsp name> <glb> <out prefix>
Writes <out>_light.npy (H,W,3 float32: diffuse light per texel, 1 = untouched albedo before exposure) and a preview png."""
import sys,ctypes,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import lmdec
from glbread import G
from PIL import Image
lib=ctypes.CDLL('/tmp/claude-0/halo/real/c/uvras.so')
def smooth_normals(P,T):
    fn=np.cross(P[T[:,1]]-P[T[:,0]],P[T[:,2]]-P[T[:,0]])
    N=np.zeros_like(P); 
    for c in range(3): np.add.at(N,T[:,c],fn)
    return N/np.maximum(np.linalg.norm(N,axis=1,keepdims=True),1e-9)
def bake(mapp,sbsp,glb,out,flipv=False):
    r=RR(mapp); nm=r.names(); L=lmdec.lightmap(r,nm,sbsp)
    if L is None: print('no lightmap',sbsp); return None
    W,H=L['W'],L['H']; g=G(glb)
    nrm=np.zeros((H,W,3),np.float32); cov=np.zeros((H,W),np.uint8); n_tri=0
    for ni,mi,M in g.walk():
        for p in g.j['meshes'][mi]['primitives']:
            if 'TEXCOORD_1' not in p['attributes']: continue
            P=g.acc(p['attributes']['POSITION']).astype(np.float64); UV=g.acc(p['attributes']['TEXCOORD_1']).astype(np.float32)
            T=g.acc(p['indices']).astype(np.int64).reshape(-1,3)
            Pw=(np.c_[P,np.ones(len(P))]@M.T)[:,:3]
            N=smooth_normals(Pw,T)
            fn=np.cross(Pw[T[:,1]]-Pw[T[:,0]],Pw[T[:,2]]-Pw[T[:,0]]); fn/=np.maximum(np.linalg.norm(fn,axis=1,keepdims=True),1e-12)
            Nc=N[T]  # (t,3,3) per corner; fall back to the face normal where smoothing crossed a crease or a thin double-sided sheet
            ok=(Nc*fn[:,None,:]).sum(-1)>0.7; Nc=np.where(ok[...,None],Nc,fn[:,None,:])
            Nh=np.stack([Nc[...,2],Nc[...,0],Nc[...,1]],-1).astype(np.float32)  # GL -> Halo axes
            uv=UV.copy()
            if flipv: uv[:,1]=1-uv[:,1]
            tu=np.ascontiguousarray(uv[T].reshape(-1,6),np.float32); ta=np.ascontiguousarray(Nh.reshape(-1,9),np.float32)
            lib.ras(len(T),tu.ctypes.data_as(ctypes.c_void_p),ta.ctypes.data_as(ctypes.c_void_p),W,H,nrm.ctypes.data_as(ctypes.c_void_p),cov.ctypes.data_as(ctypes.c_void_p))
            n_tri+=len(T)
    nn=nrm/np.maximum(np.linalg.norm(nrm,axis=-1,keepdims=True),1e-6)
    E=lmdec.irradiance(L,nn).astype(np.float32)
    E[cov==0]=0
    # dilate into the gutters so bilinear filtering never pulls in black
    m=cov>0
    for it in range(4):
        acc=np.zeros_like(E); cnt=np.zeros(E.shape[:2],np.float32)
        for dy,dx in ((0,1),(0,-1),(1,0),(-1,0)):
            sh=np.roll(np.roll(E,dy,0),dx,1); sm=np.roll(np.roll(m,dy,0),dx,1)
            acc+=sh*sm[...,None]; cnt+=sm
        fill=(~m)&(cnt>0); E[fill]=acc[fill]/cnt[fill][:,None]; m=m|fill
    np.save(out+'_light.npy',E)
    lit=E[cov>0]; ref=np.percentile(lit.max(1),95) if len(lit) else 1
    Image.fromarray((np.clip(E/ref,0,1)**(1/2.2)*255).astype(np.uint8)).save(out+'_light.png')
    Image.fromarray((cov*127).astype(np.uint8)).save(out+'_cov.png')
    print('baked',sbsp,W,H,'tris',n_tri,'covered %.1f%%'%(100*(cov>0).mean()),'p95',ref,flush=True)
    return E,cov,L
if __name__=='__main__':
    bake(sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4],len(sys.argv)>5 and sys.argv[5]=='flip')
