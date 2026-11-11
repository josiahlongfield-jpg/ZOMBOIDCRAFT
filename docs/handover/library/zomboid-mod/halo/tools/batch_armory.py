import sys,json;sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import export
r=RR('/tmp/claude-0/halo/maps/m20.map');nm=r.names()
man=json.load(open('/tmp/claude-0/halo/exp/characters/m20_spartans.json'))['permutations']
fix={'?80163':'female','?801d8':'helmet_801d8'}
out=[]
for rn,perms in man.items():
    for pn,e in perms.items():
        if not (e['mesh'] or e['pieces']): continue
        fn='%s__%s'%(rn,fix.get(pn,pn))
        res=export.export(r,nm,'objects\\characters\\spartans\\spartans','/tmp/claude-0/halo/exp/armory/%s.glb'%fn,pick={rn:pn})
        out.append(fn); print(fn,res['tris'],flush=True)
json.dump(out,open('/tmp/claude-0/halo/exp/armory/_list.json','w'))
