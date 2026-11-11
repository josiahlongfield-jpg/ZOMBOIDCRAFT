import sys,os,json,zipfile,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
import wexport
from rres import RR
mp,out=sys.argv[1],sys.argv[2]; os.makedirs(out,exist_ok=True)
r=RR(mp); nm=r.names(); res={}
for k,t in enumerate(r.tags):
    if t[0]!='sbsp': continue
    short=nm[k].split('\\')[-1]
    try:
        c=wexport.collision(r,t[1])
        if c:
            V,T,M,ni=c; np.savez_compressed(f'{out}/{short}_collision.npz',V=V,T=T,M=M)
            res[short]=dict(coll_tris=int(len(T)),coll_instances=ni,min=V.min(0).round(2).tolist(),max=V.max(0).round(2).tolist())
        else: res[short]=None
    except Exception as e: res[short]=repr(e)
    print(short,res[short],flush=True)
json.dump(res,open(f'{out}/coll.json','w'),indent=1)
