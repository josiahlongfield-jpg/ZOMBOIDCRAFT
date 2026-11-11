import json,re,collections,sys
sys.argv=['x','../distro.lua']; exec(open('parse.py').read().split('E = env()')[0])
rows=json.load(open('rows.json'))
picks=[]
for l in open('/mnt/project-files/zomboid-mod/brita/wave1-picks.txt'):
    if ':' not in l or 'picks' in l: continue
    for p in l.split(':',1)[1].split(';'):
        p=re.sub(r'\(.*?\)','',p).strip()
        if p: picks.append(p)
ALIAS={'AssaultRifle2':'M14','DSHK':'DSHK_Fold','Ruger_No1_308':'Ruger_No1_3006'}
MAIN=['GunStoreGuns','PawnShopGuns','PoliceGuns','ArmyGuns','SecurityGuns','HuntGuns','SurplusGuns','A26SWAT_Trunk','A26Military_Trunk']
res={}
E=env(0.5)
for g in picks:
    d=collections.OrderedDict()
    for lst,gun,expr,i in rows:
        if gun!=g and gun!=ALIAS.get(g): continue
        if gun!=g and lst.startswith('Case_'): continue
        key=lst.replace('ProceduralDistributions.list','').replace('VehicleDistributions.','').strip('[]"')
        w = ev(expr,E) if expr else 'worn'
        cross=[c for c in CROSS+['MILxNON'] if expr and c in expr]
        d.setdefault(key,[]).append((w, sorted(set(cross)), expr))
    res[g]=d
json.dump(res,open('picks.json','w'),indent=0,default=str)
for g,d in res.items():
    parts=[]
    for k,v in d.items():
        ws=[x[0] for x in v]; cr=set(c for x in v for c in x[1])
        tot = sum(w for w in ws if isinstance(w,(int,float)) and w>0) if any(isinstance(w,(int,float)) for w in ws) else 'worn'
        parts.append(f"{k}={tot if isinstance(tot,str) else round(tot,2)}{'*'+'/'.join(sorted(cr)) if cr else ''}")
    print(g, '|', '; '.join(parts))
