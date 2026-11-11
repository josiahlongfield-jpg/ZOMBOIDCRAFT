import sys,json,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
import viewglb
V,T,UV,MM,tex=viewglb.scene(sys.argv[1])
A=V[T[:,0]];B=V[T[:,1]];C=V[T[:,2]]
N=np.cross(B-A,C-A);ar=np.linalg.norm(N,axis=1)/2;Nn=N/np.maximum(2*ar[:,None],1e-9)
E1=B-A;E2=C-A
def ray(o,d,tmax=500):
    p=np.cross(d,E2);det=(E1*p).sum(1);ok=np.abs(det)>1e-9;inv=np.where(ok,1/np.where(ok,det,1),0)
    s=o-A;u=(s*p).sum(1)*inv;qv=np.cross(s,E1);v=(qv*d).sum(1)*inv;t=(E2*qv).sum(1)*inv
    hit=ok&(u>=0)&(v>=0)&(u+v<=1)&(t>1e-3)&(t<tmax)
    return t[hit].min() if hit.any() else tmax
rng=np.random.default_rng(1)
cand=np.where((np.abs(Nn[:,1])>0.95)&(ar>1.5))[0]
rng.shuffle(cand)
res=[]
for ti in cand[:400]:
    c=(A[ti]+B[ti]+C[ti])/3
    # floor must be the upward facing surface below us: shoot down from c+0.5
    o=c+np.array([0,1.7,0])
    up=ray(o,np.array([0,1.,0]),80)
    if up>=80 or up<1.0: continue
    best=(0,None)
    for k in range(16):
        a=k*np.pi/8;d=np.array([np.cos(a),-0.05,np.sin(a)]);d/=np.linalg.norm(d)
        t=ray(o,d)
        if t>best[0]: best=(t,d)
    res.append((best[0],up,o.tolist(),(o+best[1]*min(best[0],60)).tolist()))
res.sort(key=lambda x:-x[0]*min(x[1],20))
pick=[]
for r in res:
    if all(np.linalg.norm(np.array(r[2])-np.array(p[2]))>15 for p in pick): pick.append(r)
    if len(pick)>=8: break
for p in pick: print(round(p[0],1),round(p[1],1),np.round(p[2],1))
json.dump(pick,open('/tmp/claude-0/halo/web/vistas.json','w'))
