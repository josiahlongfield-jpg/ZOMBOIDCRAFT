# Pose an exported Spartan GLB with a clip from the delivered animation GLBs (glTF space), weapon on b_r_grip.
import sys;sys.path.insert(0,'/tmp/claude-0/halo')
import numpy as np, viewglb
from viewglb import load, qm
_C={}
def anims(path):
    if path not in _C:
        j,acc,img=load(path); _C[path]=(j,acc)
    return _C[path]
def clip_pose(path,name,t=0.0):
    j,acc=anims(path); a=[x for x in j['animations'] if x['name']==name][0]
    q={}; tr={}
    for ch in a['channels']:
        s=a['samplers'][ch['sampler']]; T=acc(s['input'])[:,0]; O=acc(s['output']).astype(float)
        i=int(np.clip(np.searchsorted(T,t,'right')-1,0,len(T)-1)); k=min(i+1,len(T)-1)
        f=0 if k==i else np.clip((t-T[i])/(T[k]-T[i]),0,1); v=O[i]*(1-f)+O[k]*f
        nn=j['nodes'][ch['target']['node']]['name']
        if ch['target']['path']=='rotation': q[nn]=v/np.linalg.norm(v)
        elif ch['target']['path']=='translation': tr[nn]=v
    return q,tr,float(acc(a['samplers'][0]['input'])[-1,0])
def world(glb,q,tr):
    j,acc,img=load(glb); nodes=j['nodes']; parent={}
    for i,n in enumerate(nodes):
        for c in n.get('children',[]): parent[c]=i
    Wd={}
    def W(i):
        if i in Wd: return Wd[i]
        n=nodes[i]; qq=q.get(n['name'],n.get('rotation',[0,0,0,1])); tt=tr.get(n['name'],n.get('translation',[0,0,0]))
        M=np.eye(4); M[:3,:3]=qm(qq); M[:3,3]=tt
        if i in parent: M=W(parent[i])@M
        Wd[i]=M; return M
    return {n['name']:W(i) for i,n in enumerate(nodes)}
def build(glb,apath,clip,t=0.0,weapon=None,grip='b_r_grip',wsel=None):
    q,tr,dur=clip_pose(apath,clip,t)
    absq={}
    j,_,_=load(glb)
    for n in j['nodes']:
        if n['name'] in q or n['name'] in tr:
            absq[n['name']]=(q.get(n['name'],np.array(n.get('rotation',[0,0,0,1]))),tr.get(n['name'],np.array(n.get('translation',[0,0,0]))))
    V,T,UV,MM,tex=viewglb.scene(glb,None,{},absq)
    if weapon:
        Mg=world(glb,q,tr)[grip]
        V2,T2,UV2,MM2,tex2=viewglb.scene(weapon,wsel)
        g=V2@Mg[:3,:3].T+Mg[:3,3]
        off=max(int(MM.max())+1,max(tex.keys(),default=-1)+1)
        T=np.concatenate([T,T2+len(V)]); V=np.concatenate([V,g]); UV=np.concatenate([UV,UV2]); MM=np.concatenate([MM,MM2+off])
        for k,v in tex2.items(): tex[k+off]=v
    return V,T,UV,MM,tex
