import re,json,sys
S=sys.argv[1]
F="/mnt/user-data/uploads/108600/2297098490/mods/ARSENAL(26)GunFighter[MOD 2.0]/media/scripts/PARTS_GUNFIGHTER/GunFighter_Weapon_Parts.txt"
t=open(F,encoding='latin-1').read().replace('\r','')
t=re.sub(r'/\*.*?\*/','',t,flags=re.S)
parts={}
for m in re.finditer(r'item\s+(\w+)\s*\{(.*?)\n\s*\}',t,re.S):
    b=m.group(2); p={}
    for k,v in re.findall(r'(\w+)\s*=\s*(.*?)(?:,\s*\n|\n(?=\s*\w+\s*=)|$)',b,re.S):
        p[k]=' '.join(v.split())
    if p.get('Type')!='WeaponPart': continue
    p['MountOn']=[x.strip() for x in re.split(r'[;\s]+',p.get('MountOn','')) if x.strip()]
    parts[m.group(1)]=p
json.dump(parts,open(S+'/review/parts.json','w'),indent=0)
print(len(parts))
from collections import Counter
print(Counter(p.get('PartType') for p in parts.values()))
PIL=['Colt1911','SW_M500','AR57_PDW','M2Carbine','AAC_Honey','AssaultRifle2','BOAR','M870_CQB','Winchester1866','American180','L86','RPG_7','TenPointTurbo_XLT','DSHK']
for g in PIL:
    f=[(k,p.get('PartType'),p.get('WorldStaticModel')) for k,p in parts.items() if g in p['MountOn']]
    print(g,len(f),Counter(x[1] for x in f))
