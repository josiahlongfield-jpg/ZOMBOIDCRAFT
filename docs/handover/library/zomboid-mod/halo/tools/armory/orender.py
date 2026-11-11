import sys,json;sys.path.insert(0,'/tmp/claude-0/halo')
import numpy as np, poseglb as pg, posed, viewglb
G='/tmp/claude-0/arm/mp_spartans.glb'
MAN=json.load(open('/tmp/claude-0/arm/mp_spartans.json'))['permutations']
AI='/mnt/user-data/uploads/additions/halo-reach/animations/spartan_ai_animations_part01.glb'
_pose={}
def pose(clip='alert:rifle:idle'):
    if clip in _pose: return _pose[clip]
    q,tr,dur=pg.clip_pose(AI,clip,0.0); j,_,_=pg.load(G); absq={}
    for n in j['nodes']:
        if n['name'] in q or n['name'] in tr:
            absq[n['name']]=(q.get(n['name'],np.array(n.get('rotation',[0,0,0,1]))),tr.get(n['name'],np.array(n.get('translation',[0,0,0]))))
    _pose[clip]=absq; return absq
def nodes_for(outfit):
    keep=set()
    for rg,pn in outfit.items():
        e=MAN.get(rg,{}).get(pn)
        if not e: continue
        if e['mesh']: keep.add(e['mesh'])
        for p in e['pieces']:
            a,b=p.split('__(',1); keep.add('inst__'+a+'__'+b.rstrip(')'))
    return keep
def outfit_from(base,items):
    o=dict(base)
    for rg,pn in items: o[rg]=pn
    return o
BASE={'arms':'default','body':'default','decals':'default','head':'default','flair_knees':'default'}
def scene(outfit,clip='alert:rifle:idle'):
    keep=nodes_for(outfit)
    return viewglb.scene(G,lambda n: n in keep,{},pose(clip))
W_DMR='/tmp/claude-0/halo/exp/noble/w_dmr.glb'
def scene_w(outfit,weapon=W_DMR,clip='alert:rifle:idle'):
    V,T,UV,MM,tex=scene(outfit,clip)
    q,tr,dur=pg.clip_pose(AI,clip,0.0); Mg=pg.world(G,q,tr)['b_r_grip']
    V2,T2,UV2,MM2,tex2=viewglb.scene(weapon,None)
    g=V2@Mg[:3,:3].T+Mg[:3,3]; off=max(int(MM.max())+1,max(tex.keys(),default=-1)+1)
    T=np.concatenate([T,T2+len(V)]); V=np.concatenate([V,g]); UV=np.concatenate([UV,UV2]); MM=np.concatenate([MM,MM2+off])
    for k,v in tex2.items(): tex[k+off]=v
    return V,T,UV,MM,tex
