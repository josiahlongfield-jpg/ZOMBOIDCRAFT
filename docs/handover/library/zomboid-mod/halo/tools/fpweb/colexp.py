import sys,json;sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import export
r=RR('/tmp/claude-0/halo/maps/m20.map'); nm=r.names()
V=json.load(open('/mnt/user-data/uploads/halo-reach/noble/spartan_variants.json'))['variants']
C={k:v['colors'] for k,v in V.items() if v.get('colors')}
C['six']=C.pop('default')
C['john']=[[0.30,0.34,0.17],[0.25,0.27,0.14],[0.35,0.37,0.2],[0.6,0.5,0.3]]   # MJOLNIR olive (approx.)
C['jorge']=[[0.28,0.29,0.25],[0.2,0.21,0.19],[0.35,0.33,0.25],[0.5,0.45,0.35]] # Mark IV grey-olive (approx.)
for k,c in C.items():
    export.TEXCACHE.clear()
    export.export(r,nm,'objects\\characters\\spartans\\fp\\fp','/tmp/claude-0/fpweb/col/%s.glb'%k,colors=c); print(k,flush=True)
