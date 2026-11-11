import sys,json,numpy as np
WU=3.048
def voxelize(V,T,lo,hi,res=1.0):
    """surface voxels: triangle points sampled at < half a block, in blocks (x east, y up, z south)."""
    P=V*WU/res; lo=np.array(lo)*WU/res; hi=np.array(hi)*WU/res
    keep=np.all((P[T[:,0]]>=lo)&(P[T[:,0]]<=hi),axis=1)
    T=T[keep]
    size=np.ceil(hi-lo).astype(int)+1
    occ=np.zeros(size,dtype=bool)
    a,b,c=P[T[:,0]]-lo,P[T[:,1]]-lo,P[T[:,2]]-lo
    L=np.maximum(np.linalg.norm(b-a,axis=1),np.maximum(np.linalg.norm(c-a,axis=1),np.linalg.norm(c-b,axis=1)))
    n=np.clip(np.ceil(L*2.5).astype(int),1,400)
    for k in np.unique(n):
        sel=n==k
        u,v=np.meshgrid(np.arange(k+1),np.arange(k+1)); m=(u+v)<=k
        u=u[m]/k; v=v[m]/k
        pts=a[sel][:,None,:]+(b[sel]-a[sel])[:,None,:]*u[None,:,None]+(c[sel]-a[sel])[:,None,:]*v[None,:,None]
        q=np.floor(pts.reshape(-1,3)).astype(int)
        q=q[np.all((q>=0)&(q<size),axis=1)]
        occ[q[:,0],q[:,1],q[:,2]]=True
    return occ
if __name__=='__main__':
    z=np.load(sys.argv[1]); V=z['V']; T=z['T']
    lo=[float(x) for x in sys.argv[2].split(',')]; hi=[float(x) for x in sys.argv[3].split(',')]
    occ=voxelize(V,T[:,:3].astype(int),lo,hi)
    print(occ.shape, occ.sum())
    np.save(sys.argv[4],occ)
