import sys;sys.path.insert(0,'/tmp/claude-0/halo')
import numpy as np
from jmad import *; from janim import *; from fk import *
from rmodel import find_tag,model
from export import info
from viewglb import shot
from PIL import Image,ImageDraw
r=RR('/tmp/claude-0/halo/maps/m20.map'); nm=r.names()
SP={'elite':(8045,r'objects\characters\elite\elite','elite_minor'),'grunt':(8583,r'objects\characters\grunt\grunt','grunt_minor'),
    'jackal':(8947,r'objects\characters\jackal\jackal','jackal_jackal_minor'),'hunter':(9102,r'objects\characters\hunter\hunter','hunter_minor')}
def setup(sp):
    gk,mp,glb=SP[sp]; G=graph(r,gk); k,o=find_tag(r,'mode',mp,nm); M=model(r,o,nm)
    names=info(r,o)[0]; par=[n['parent'] for n in M['nodes']]
    dq=[np.array(n['q']) for n in M['nodes']]; dt=[np.array(n['t']) for n in M['nodes']]
    gi=[names.index(s) if s in names else -1 for s in G['skel']]
    return dict(G=G,names=names,par=par,dq=dq,dt=dt,gi=gi,glb=f'/tmp/claude-0/halo/exp/covenant/{glb}.glb')
def prep(C,name):
    a=[a for a in C['G']['anims'] if a['name']==name][0]
    hdr,D=anim_data(r,C['G'],a); S,A,F,AF,rest=split(hdr,D); R,T,SC=static(S,F)
    return a,R,T,AF,curve_tracks2(A),trans_tracks(A)
def pose_at(C,P,f):
    a,R,T,AF,TR,TT=P; gi=C['gi']
    rots=list(C['dq']); trans=list(C['dt'])
    def s(L,n,v):
        if gi[n]>=0: L[gi[n]]=v
    for n,q in R.items(): s(rots,n,q)
    for n,t in T.items(): s(trans,n,t)
    for i,t in enumerate(TR): s(rots,AF[0][i],sample_track(t,f))
    for i,t in enumerate(TT): s(trans,AF[1][i],sample_trans(t,f))
    return rots,trans
def to_abs(C,rots,trans):
    return {C['names'][i]:(np.array([q[1],q[2],q[0],q[3]])/np.linalg.norm(q),np.array([t[1],t[2],t[0]])*3.048) for i,(q,t) in enumerate(zip(rots,trans))}
if __name__=='__main__':
    sp=sys.argv[1]; clips=sys.argv[2:]; C=setup(sp)
    print('unmapped',[s for s,g in zip(C['G']['skel'],C['gi']) if g<0])
    feet=[i for i,n in enumerate(C['names']) if 'foot' in n]
    tiles=[]
    for c in clips:
        P=prep(C,c); fr=P[0]['frames']
        for f in [0,fr//3,2*fr//3]:
            ro,tr=pose_at(C,P,f); W=fk(C['par'],ro,tr)
            print(c,f,'feet z',[round(W[i][1][2],3) for i in feet])
            shot(C['glb'],'/tmp/claude-0/halo/cv.png',absq=to_abs(C,ro,tr),views=((1,0.25,0.8),),W=240,H=300,fixc=((0,1.0,0),2.8 if sp!='grunt' else 1.8))
            im=Image.open('/tmp/claude-0/halo/cv.png').convert('RGB'); ImageDraw.Draw(im).text((4,4),f'{c} f{f}',fill=(255,255,255)); tiles.append(im)
    ro,tr=C['dq'],C['dt']; W=fk(C['par'],ro,tr); print('default feet z',[round(W[i][1][2],3) for i in feet])
    o=Image.new('RGB',(240*len(tiles),300)); [o.paste(t,(i*240,0)) for i,t in enumerate(tiles)]; o.save(f'/tmp/claude-0/halo/cv_{sp}.png')
