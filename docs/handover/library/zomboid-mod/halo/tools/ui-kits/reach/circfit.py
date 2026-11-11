import numpy as np
def fit(a,mask=None):
    ys,xs=np.nonzero(a>0.15*a.max() if mask is None else (a>0.15*a.max())&mask)
    w=a[ys,xs].astype(float); x=xs+0.5; y=ys+0.5
    A=np.stack([x,y,np.ones_like(x)],1)*np.sqrt(w)[:,None]; b=(x*x+y*y)*np.sqrt(w)
    c=np.linalg.lstsq(A,b,rcond=None)[0]; cx,cy=c[0]/2,c[1]/2; r=np.sqrt(c[2]+cx*cx+cy*cy)
    return cx,cy,r
