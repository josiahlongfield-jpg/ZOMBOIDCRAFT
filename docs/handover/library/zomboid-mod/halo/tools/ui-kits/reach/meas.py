import numpy as np
def report(nm,M,cx,cy,r=34):
    M=M[int(cy)-r:int(cy)+r,int(cx)-r:int(cx)+r]; cx=cx-int(cx)+r; cy=cy-int(cy)+r
    yy,xx=np.mgrid[0:M.shape[0],0:M.shape[1]]; R=np.hypot(xx+0.5-cx,yy+0.5-cy)
    ys,xs=np.nonzero(M>0.35)
    print(nm,'extent',xs.max()-xs.min()+1,ys.max()-ys.min()+1)
    prof=[M[(R>=k)&(R<k+1)].mean() for k in range(0,r)]
    print('  radial',' '.join('%d:%.2f'%(k,v) for k,v in enumerate(prof) if v>0.05))
    row=M[int(cy)]; print('  row',''.join('#' if v>0.6 else ('+' if v>0.25 else '.') for v in row))
    col=M[:,int(cx)]; print('  col',''.join('#' if v>0.6 else ('+' if v>0.25 else '.') for v in col))
    # tick thickness: profile across the right tick at x=cx+16
    x=int(cx+16); print('  tickcut',''.join('#' if v>0.6 else ('+' if v>0.25 else '.') for v in M[int(cy)-6:int(cy)+7,x]))
    ang=(np.degrees(np.arctan2(yy-cy+0.5,xx-cx+0.5))%360)
    for (r0,r1) in ((5,13),(13,25)):
        sel=(R>=r0)&(R<r1); bins=np.zeros(36)
        for b in range(36):
            s=sel&(ang>=b*10)&(ang<b*10+10); bins[b]=M[s].max() if s.any() else 0
        print('  ring',r0,r1,''.join('#' if v>0.5 else ('+' if v>0.25 else '.') for v in bins))
