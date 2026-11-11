import sys,glob,json,struct,os;sys.path.insert(0,'/tmp/claude-0/halo')
from animexp import loadglb
n_changed=0
for p in sorted(glob.glob('/tmp/claude-0/halo/vanim/*.glb')):
    g=loadglb(p); j=g.j; roots=j['scenes'][0]['nodes']
    regs={}
    for i in roots:
        nm=j['nodes'][i].get('name','')
        if 'mesh' in j['nodes'][i] and '__' in nm:
            r,pm=nm.split('__',1); regs.setdefault(r,[]).append((pm,i))
    drop=set()
    for r,lst in regs.items():
        pms=[p_ for p_,_ in lst]
        if r=='shield' and len(regs)>1: drop|={i for _,i in lst}; continue
        if 'default' in pms: drop|={i for p_,i in lst if p_ not in ('default','glass_transparent')}
    if drop:
        j['scenes'][0]['nodes']=[i for i in roots if i not in drop]
        j.setdefault('extras',{})['hidden_variants']=[j['nodes'][i]['name'] for i in drop]
        g.save(p); n_changed+=1
print('cleaned',n_changed)
