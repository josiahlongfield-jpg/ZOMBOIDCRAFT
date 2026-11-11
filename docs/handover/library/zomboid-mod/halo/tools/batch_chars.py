import sys,json,traceback;sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import export
M=sys.argv[1]; tag=sys.argv[2]; out=sys.argv[3]
r=RR(M); nm=r.names()
export.TEXCACHE.clear()
res=export.export(r,nm,tag,out)
json.dump(dict(tag=tag,map=M.split('/')[-1],permutations=res['manifest'],pieces=res['instances']),open(out.replace('.glb','.json'),'w'),indent=1)
print(tag,res['tris'],res['meshes'],{k:len(v) for k,v in res['manifest'].items()})
