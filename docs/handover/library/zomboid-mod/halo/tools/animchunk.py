import sys,os,pickle,numpy as np;sys.path.insert(0,'/tmp/claude-0/halo')
import animexp2
from animexp import loadglb,cq,ct
BUDGET=23_500_000
def run(glbin,outbase,dbs):
    base=os.path.getsize(glbin); items=[]
    for dbp in dbs:
        db=pickle.load(open(dbp,'rb'))
        for name in sorted(db['clips']):
            try: typ,fr,rot,tr=animexp2.tracks(db,name)
            except Exception: continue
            sz=sum(len(v)*8+40 for v in rot.values())+sum(len(v)*12+40 for v in tr.values())+300*(len(rot)+len(tr))
            items.append((name,dbp,sz))
    chunks=[[]]; cur=base
    for it in items:
        if cur+it[2]>BUDGET and chunks[-1]: chunks.append([]); cur=base
        chunks[-1].append(it); cur+=it[2]
    outs=[]
    for k,ch in enumerate(chunks):
        out=outbase+('' if len(chunks)==1 else '_part%02d'%(k+1))+'.glb'
        g=loadglb(glbin); names={n.get('name'):i for i,n in enumerate(g.j['nodes'])}; g.j['animations']=[]; times={}
        for name,dbp,_ in ch:
            db=pickle.load(open(dbp,'rb')) if dbp not in run.__dict__ else run.__dict__[dbp]
            run.__dict__[dbp]=db
            typ,fr,rot,tr=animexp2.tracks(db,name); skel=db['skel']; c=[];s=[]
            for kind,dd in (('rotation',rot),('translation',tr)):
                for n,v in dd.items():
                    nm=skel[n]
                    if nm not in names: continue
                    if len(v) not in times: times[len(v)]=g.acc(np.arange(len(v),dtype=np.float32)[:,None]/30.,'SCALAR',5126,minmax=True)
                    if kind=='rotation':
                        q=cq(v) if len(v)>1 else cq(v[0])[None]
                        for i in range(1,len(q)):
                            if np.dot(q[i],q[i-1])<0: q[i]=-q[i]
                        o=g.acc(np.round(q*32767).astype(np.int16),'VEC4',5122,normalized=True)
                    else: o=g.acc((ct(v) if len(v)>1 else ct(v[0])[None]).astype(np.float32),'VEC3',5126)
                    s.append(dict(input=times[len(v)],output=o,interpolation='LINEAR')); c.append(dict(sampler=len(s)-1,target=dict(node=names[nm],path=kind)))
            if c: g.j['animations'].append(dict(name=name+('' if typ==1 else ' [overlay]' if typ==2 else ' [replace]'),channels=c,samplers=s))
        g.save(out); outs.append((os.path.basename(out),os.path.getsize(out),len(g.j['animations']),ch[0][0],ch[-1][0]))
    for o in outs: print(o)
if __name__=='__main__': run(sys.argv[1],sys.argv[2],sys.argv[3:])
