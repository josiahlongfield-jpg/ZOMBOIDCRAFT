"""Each batch-1 gun's kit as a tree for bench fitting (gun file "tarkov"): the base item, and for every kit part its path
(slot names from the gun down), item, anchor bone in the first-person model (a mesh of its own), that bone's place in
the frame of the slot holding the part, and the bones that are its meshes (hidden once it's swapped out). Checks the
part data against the assembled kit (the anchor's place through the chain of slots)."""
import sys,json,os
import numpy as np
from scipy.spatial.transform import Rotation as Rot
TK2='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/tk2'
cwd=os.getcwd(); os.chdir(TK2); sys.path.insert(0,TK2)
from db import *; from kit import resolve
os.chdir(cwd)
K=json.load(open(TK2+'/kits.json'))
D='/tmp/claude-0/tk/web/data/'
def M(p,q,s=(1,1,1)):
    m=np.eye(4); m[:3,:3]=Rot.from_quat(q).as_matrix()*np.array(s); m[:3,3]=p; return m
def A(at): return M(at[:3],at[3:])
def un(m):
    m=m.copy(); m[:3,:3]/=np.linalg.norm(m[:3,:3],axis=0)
    if np.linalg.det(m[:3,:3])<0: m[:3,0]*=-1
    return m
def pq(m):
    m=un(m); return [round(float(x),6) for x in list(m[:3,3])+list(Rot.from_matrix(m[:3,:3]).as_quat())]
res={}
for g,v in K.items():
    parts=[('',v['id'])]+resolve(v['id'],v['kit'])
    N=json.load(open(D+'b1_%s.json'%g)); fp=json.load(open('/tmp/claude-0/tk/b1/pack/fp_%s.json'%g))
    bone_of={b['node']:b['name'] for b in fp['bones'] if b['kind']=='mesh'}
    Wk=[None]*len(N)
    for i,n in enumerate(N): Wk[i]=(Wk[n['parent']] if n['parent']>=0 else np.eye(4))@M(n['p'],n['q'],n['s'])
    PJ={c:json.load(open('parts/%s.json'%c)) for _,c in parts}
    start=0; kit=[]; frames={}; worst=0
    for path,c in parts:
        pj=PJ[c]; n=len(pj['nodes'])
        if N[start]['name']!=pj['nodes'][0]['name']: print('!! block mismatch',g,path,N[start]['name'],pj['nodes'][0]['name']); break
        own=[i for i in range(start,start+n) if i in bone_of]
        # the anchor: the part's own main mesh (named after it), else its biggest (a gun's body, not its bolt or catch)
        size=lambda i:(N[i].get('mesh') or {}).get('n',0)
        lod=[i for i in own if N[i]['name']==pj['root']+'_LOD0'] or sorted(own,key=size,reverse=True)
        e={'path':path,'item':c,'bones':[bone_of[i] for i in own]}
        # the slot frame from the parent's data, and the check against the kit as assembled
        if path=='': F=np.eye(4)
        else:
            par,sn=path.rsplit('/',1); F=frames[par]@A(PJ[dict(parts)[par] if par else v['id']]['slots'][sn]['at']) if sn in PJ[dict(parts)[par]]['slots'] else None
        if F is None: print('!! no slot data',g,path); F=np.eye(4)
        frames[path]=F
        if lod:
            a=lod[0]; rel=np.linalg.inv(F)@un(Wk[a])
            # the same from the part's own data
            mine=un(np.array(pj['world'][a-start]))
            err=np.abs(rel-mine).max(); worst=max(worst,err)
            if err>1e-3: print('  mismatch',g,path,N[a]['name'],round(float(err),4))
            e['bone']=bone_of[a]; e['at']=pq(Wk[a] if False else un(np.array(pj['world'][a-start])))
        kit.append(e); start+=n
    if start!=len(N): print('!! nodes left',g,start,len(N))
    res[g]={'base':v['id'],'kit':kit}
    print(g,len(kit),'parts','worst %.5f'%worst)
json.dump(res,open('kit_trees.json','w'),indent=1)
