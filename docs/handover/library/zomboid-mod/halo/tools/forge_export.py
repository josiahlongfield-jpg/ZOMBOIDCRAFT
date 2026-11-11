import sys,os,json,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from rres import RR
import export
export.MATHOOK=True
r=RR('forge_halo.map'); nm=r.names()
modes={nm[k] for k,t in enumerate(r.tags) if t[0]=='mode'}
want=[]
for k,t in enumerate(r.tags):
    if t[0] not in ('bloc','scen','mach','ctrl','eqip'): continue
    n=nm[k]
    if 'garbage' in n or 'debris' in n or '\\fx\\' in n or 'spawning' in n or 'cinematic' in n or 'sky_halo' in n: continue
    want.append((t[0],n))
os.makedirs('forge/glb',exist_ok=True); info=[]
for g,n in want:
    short=n.split('\\')[-1]; fn=f'forge/glb/{short}.glb'
    mode=n if n in modes else None
    if mode is None:
        info.append(dict(group=g,tag=n,model=None)); continue
    if not os.path.exists(fn):
        try:
            ko,mo=export.find_tag(r,'mode',n,nm); _,_,regs=export.info(r,mo)
            pick={rn:ps[0][0] for rn,ps in regs if ps}
            export.export(r,nm,n,fn,maxsz=512,pick=pick); export.TEXCACHE.clear()
        except Exception as ex:
            print('FAIL',short,ex,flush=True); info.append(dict(group=g,tag=n,model=None,err=str(ex))); continue
    info.append(dict(group=g,tag=n,model=short+'.glb')); print('ok',short,flush=True)
json.dump(info,open('forge/pieces.json','w'),indent=1)
print('done',len(info))
