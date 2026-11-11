"""Adds to kit_trees.json each gun's main eye point (the kit part and aim whose frame is the packed rig's aim0), found by
matching every kit eye point, placed through its anchor bone's rest frame, against aim0."""
import json,numpy as np
from scipy.spatial.transform import Rotation as Rot
T=json.load(open('kit_trees.json')); P=json.load(open('parts_game.json'))
def col(e): return np.array(e,float).reshape(4,4).T
def A(at): m=np.eye(4); m[:3,:3]=Rot.from_quat(at[3:]).as_matrix(); m[:3,3]=at[:3]; return m
for g,v in T.items():
    fp=json.load(open('/tmp/claude-0/tk/b1/pack/fp_%s.json'%g))
    if not fp.get('aim0'): print(g,'no aim0'); continue
    names=[b['name'] for b in fp['bones']]; rest=[col(m) for m in fp['rest']]
    def un(m): m=m.copy(); m[:3,:3]/=np.linalg.norm(m[:3,:3],axis=0); return m
    aim0=col(fp['aim0']); best=None
    for e in v['kit']:
        p=P[e['item']]
        if not p.get('aims') or e.get('bone') not in names: continue
        F=un(rest[names.index(e['bone'])])@np.linalg.inv(A(e['at']))
        for i,a in enumerate(p['aims']):
            W=F@A(a['at']); err=np.abs(W-aim0).max()
            if best is None or err<best[0]: best=(err,e['path'],i,p['name'])
    v['aimPath'],v['aimIndex']=best[1],best[2]
    print(g,'main sight',best[3],best[1],best[2],'err %.5f'%best[0])
json.dump(T,open('kit_trees.json','w'),indent=1)
