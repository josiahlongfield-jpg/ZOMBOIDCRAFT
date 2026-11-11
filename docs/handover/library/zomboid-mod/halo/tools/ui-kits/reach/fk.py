import numpy as np
def qmat(q):
    x,y,z,w=q/np.linalg.norm(q)
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def fk(parents,rots,trans):
    n=len(parents); W=[None]*n
    for i in range(n):
        R=qmat(rots[i]); t=np.array(trans[i])
        if parents[i]<0: W[i]=(R,t)
        else:
            PR,Pt=W[parents[i]]; W[i]=(PR@R,PR@t+Pt)
    return W
