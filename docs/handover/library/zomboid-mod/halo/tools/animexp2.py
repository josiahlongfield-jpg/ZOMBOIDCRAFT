import sys,json,struct,pickle,numpy as np;sys.path.insert(0,'/tmp/claude-0/halo')
from janim import *
from animexp import loadglb,cq,ct
class FakeA: pass
def tracks(db,name):
    typ,fr,nodes,hdr,D=db['clips'][name]
    S,A,F,AF,rest=split(hdr,D); R,T,SC=static(S,F)
    rot={n:np.array([q]) for n,q in R.items()}; tr={n:np.array([t]) for n,t in T.items()}
    if A and A[0]==9:
        for i,t in enumerate(curve_tracks2(A)): rot[AF[0][i]]=np.array([sample_track(t,f) for f in range(fr)])
        for i,t in enumerate(trans_tracks(A)): tr[AF[1][i]]=np.array([sample_trans(t,f) for f in range(fr)])
    elif A and A[0]==3:
        RR_,TT=raw3(A)
        for i,x in enumerate(RR_): rot[AF[0][i]]=x
        for i,x in enumerate(TT): tr[AF[1][i]]=x
    elif A: raise ValueError('codec %d'%A[0])
    return typ,fr,rot,tr
def run(glbin,out,dbs):
    g=loadglb(glbin); names={n.get('name'):i for i,n in enumerate(g.j['nodes'])}
    g.j['animations']=[]; times={}; bad=[]; nclip=0; miss=set()
    def tacc(n):
        if n not in times: times[n]=g.acc(np.arange(n,dtype=np.float32)[:,None]/30.,'SCALAR',5126,minmax=True)
        return times[n]
    for dbp in dbs:
        db=pickle.load(open(dbp,'rb')); skel=db['skel']
        for name in sorted(db['clips']):
            try: typ,fr,rot,tr=tracks(db,name)
            except Exception as e: bad.append((name,str(e))); continue
            ch=[];sm=[]
            for kind,dd in (('rotation',rot),('translation',tr)):
                for n,v in dd.items():
                    nm=skel[n]
                    if nm not in names: miss.add(nm); continue
                    if kind=='rotation':
                        q=cq(v) if len(v)>1 else cq(v[0])[None]
                        for k in range(1,len(q)):
                            if np.dot(q[k],q[k-1])<0: q[k]=-q[k]
                        o=g.acc(np.round(q*32767).astype(np.int16),'VEC4',5122,normalized=True)
                    else:
                        o=g.acc((ct(v) if len(v)>1 else ct(v[0])[None]).astype(np.float32),'VEC3',5126)
                    sm.append(dict(input=tacc(len(v)),output=o,interpolation='LINEAR'))
                    ch.append(dict(sampler=len(sm)-1,target=dict(node=names[nm],path=kind)))
            if ch:
                g.j['animations'].append(dict(name=name+('' if typ==1 else ' [overlay]' if typ==2 else ' [replace]'),channels=ch,samplers=sm)); nclip+=1
    g.save(out); print(out,'clips',nclip,'failed',len(bad),bad[:3],'missing nodes',sorted(miss)[:8],flush=True)
if __name__=='__main__':
    run(sys.argv[1],sys.argv[2],sys.argv[3:])
