import preview as p, numpy as np, json, fit, sys, glob, os
for g in sys.argv[1:] or fit.T:
    spec=json.load(open(f'/tmp/claude-0/tk/b1/local/guns/tarkov_{g}.json'))
    G,t,n,i,c=p.read_zsk(p.NPC+spec['fp']+'.zsk',spec['fp'],'/tmp/claude-0/tk/b2/pv/tmp','x_')
    kit={k['path']:k for k in spec['tarkov']['kit']}
    B={nm:np.linalg.inv(i[j]) for j,nm in enumerate(n)}
    def fr(path):
        k=kit[path]; 
        return B[k['bone']]@np.linalg.inv(p.at(k['at'])) if k.get('bone') in B else None
    bad=[]
    for path,k in kit.items():
        if not path: continue
        par=path[:path.rfind('/')]; f=fr(path); pf=fr(par) if par in kit else None
        port=fit.P.get(kit[par]['item'],{}).get('slots',{}).get(path[path.rfind('/')+1:]) if par in kit else None
        if f is None or pf is None or not port: bad.append((path,'n/a')); continue
        e=pf@p.at(port['at']); d=np.linalg.norm(e[:3,3]-f[:3,3])
        if d>0.003: bad.append((path,round(float(d),3)))
    print(g,bad)
