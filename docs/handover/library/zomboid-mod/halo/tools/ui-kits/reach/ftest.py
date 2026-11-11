import sys,struct;sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
import numpy as np
from jmad import *; from janim import *; from fk import *
from rmodel import find_tag,model
r=RR('/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach/maps/m20.map'); nm=r.names(); G=graph(r,13199)
k,o=find_tag(r,'mode',r'objects\characters\spartans\spartans',nm); M=model(r,o,nm)
par=[n['parent'] for n in M['nodes']]; S0=G['skel']
dq=[np.array(n['q']) for n in M['nodes']]; dt=[np.array(n['t']) for n in M['nodes']]
def mk(mode,conj=False,axneg=False):
    def f(k):
        u,v,a=k[0]/32767.,k[1]/32767.,k[2]/32767.
        n=octa(u,v)
        if axneg: n=-n
        if mode=='sin': s=a; c=np.sqrt(max(0,1-a*a))
        else: th=a*mode; s=np.sin(th/2); c=np.cos(th/2)
        q=np.array(list(n*s)+[c]); 
        if conj: q[:3]*=-1
        return q
    return f
def pose(name,f,key=0):
    a=[a for a in G['anims'] if a['name']==name][0]
    hdr,D=anim_data(r,G,a); S,A,F,AF,rest=split(hdr,D)
    R,T,SC=static(S,F)
    rots=list(dq); trans=list(dt)
    for n,q in R.items(): rots[n]=q
    for n,t in T.items(): trans[n]=t
    for i,t in enumerate(curve_tracks(A)):
        rots[AF[0][i]]=f(t['keys'][key]) if not t['hdr'][4] else rots[AF[0][i]]
    return fk(par,rots,trans),AF
