import sys;sys.path.insert(0,'/tmp/claude-0/halo')
from ftest import dq,dt,par,S0,M
from jmad import *; from janim import *; from fk import *
from viewglb import shot
from PIL import Image
r=RR('/tmp/claude-0/halo/maps/m20.map')
def to_abs(rots,trans):
    return {S0[i]:(np.array([q[1],q[2],q[0],q[3]])/np.linalg.norm(q),np.array([t[1],t[2],t[0]])*3.048) for i,(q,t) in enumerate(zip(rots,trans))}
def prep(G,name):
    a=[a for a in G['anims'] if a['name']==name][0]
    hdr,D=anim_data(r,G,a); S,A,F,AF,rest=split(hdr,D); R,T,SC=static(S,F)
    return a,R,T,AF,curve_tracks2(A),trans_tracks(A)
def pose_at(P,f):
    a,R,T,AF,TR,TT=P
    rots=list(dq); trans=list(dt)
    for n,q in R.items(): rots[n]=q
    for n,t in T.items(): trans[n]=t
    for i,t in enumerate(TR): rots[AF[0][i]]=sample_track(t,f)
    for i,t in enumerate(TT): trans[AF[1][i]]=sample_trans(t,f)
    return rots,trans
if __name__=='__main__':
    gk=int(sys.argv[1]); name=sys.argv[2]; out=sys.argv[3]; n=int(sys.argv[4]) if len(sys.argv)>4 else 6
    G=graph(r,gk); P=prep(G,name); fr=P[0]['frames']
    tiles=[]
    for i in range(n):
        f=i*(fr-1)/(n-1); rots,trans=pose_at(P,f)
        shot('/tmp/claude-0/halo/exp/noble/spartan_default.glb','/tmp/claude-0/halo/sp.png',absq=to_abs(rots,trans),views=((1,0.1,0.0),),W=220,H=300,fixc=((0,1.0,0),2.6))
        tiles.append(Image.open('/tmp/claude-0/halo/sp.png').copy())
    o=Image.new('RGB',(220*n,300)); [o.paste(t,(i*220,0)) for i,t in enumerate(tiles)]; o.save(out); print(fr)
