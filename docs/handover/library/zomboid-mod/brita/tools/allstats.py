import re,os,json,glob
R="/mnt/user-data/uploads/108600/2297098490/mods/ARSENAL(26)GunFighter[MOD 2.0]/media/scripts/"
out={}
for f in [os.path.join(d,x) for d,_,fs in os.walk(R) for x in fs if x.endswith('.txt')]:
    t=open(f,encoding='latin-1').read()
    for m in re.finditer(r'item\s+(\w+)\s*\{(.*?)\n\s*\}',t,re.S):
        p=dict((k.strip(),v.strip().rstrip(',')) for k,v in re.findall(r'^\s*(\w+)\s*=\s*([^\n]*)',m.group(2),re.M))
        if p.get('SubCategory')=='Firearm' or p.get('Ranged')=='TRUE':
            out[m.group(1)]=dict(p,_file=f[len(R):])
json.dump(out,open(os.path.dirname(__file__)+'/allstats.json','w'))
print(len(out))
K=['DisplayName','AmmoType','MinDamage','MaxDamage','MaxRange','HitChance','AimingTime','RecoilDelay','ReloadTime','ClipSize','MaxAmmo','SoundRadius','ProjectileCount','Weight','FireMode','FireModePossibilities','JamGunChance','ConditionMax']
for g in ['Glock17','G17','M4A1','M16A2','AR15','Bush_AR15_MOE','Colt1911','SW_M500','AR57_PDW','M2Carbine','AAC_Honey','AssaultRifle2','BOAR','M870_CQB','Winchester1866','American180','L86','RPG_7','TenPointTurbo_XLT','DSHK','Rem700','M40A3','Remington870','MP5','HK_MP5','SW_686','Ruger_1022','M82','FAL','Win94','SW_629','Python']:
    if g in out: print(g,'|',' | '.join(f"{k}={out[g].get(k,'')}" for k in K))
