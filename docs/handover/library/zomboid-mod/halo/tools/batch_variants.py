import sys,json,struct;sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import export
from rmodel import find_tag
from variants import variants,change_colors
from reach import X
M,obj,mode,outdir,prefix=sys.argv[1:6]
grp=sys.argv[6] if len(sys.argv)>6 else 'bipd'
r=RR(M); nm=r.names()
k,ho=find_tag(r,'hlmt',obj,nm); k,bo=find_tag(r,grp,obj,nm)
V=variants(r,ho); C=change_colors(r,bo)
skip=set(sys.argv[7].split(',')) if len(sys.argv)>7 else set()
info={}
for vn,regs in V.items():
    if vn in skip: continue
    pick={rn:ps[0] for rn,ps in regs.items() if ps}
    cols=C.get(vn)
    out='%s/%s_%s.glb'%(outdir,prefix,vn)
    res=export.export(r,nm,mode,out,pick=pick,colors=cols)
    info[vn]=dict(pick=pick,colors=cols,tris=res['tris'])
    print(vn,res['tris'],pick,cols,flush=True)
json.dump(dict(object=obj,map=M.split('/')[-1],variants=info),open('%s/%s_variants.json'%(outdir,prefix),'w'),indent=1)
