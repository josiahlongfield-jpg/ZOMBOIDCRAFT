import json,re,sys,os
sys.argv=['x','../distro.lua']; exec(open('parse.py').read().split('E = env()')[0])
rows=json.load(open('rows.json'))
S='/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/pz/pick_data.json'
info={}
for cat,guns in json.load(open(S)):
    for g in guns: info[g[0]]=dict(name=g[1],cal=g[2],cat=cat)
picks=[]
for l in open('/mnt/project-files/zomboid-mod/brita/wave1-picks.txt'):
    if ':' not in l or 'picks' in l: continue
    cat,rest=l.split(':',1)
    for p in rest.split(';'):
        p=re.sub(r'\(.*?\)','',p).strip()
        if p: picks.append((cat.strip(),p))
ALIAS={'AssaultRifle2':'M14','DSHK':'DSHK_Fold','Ruger_No1_308':'Ruger_No1_3006','HEAVY':'CheyTac50'}
built={f[:-5] for f in os.listdir('/mnt/project-files/zomboid-mod/brita/local-pc/guns') if f.endswith('.json')}
BYGUN={}
for r in rows: BYGUN.setdefault(r[1],[]).append(r)
PL=[('gunshop','Gun shops',['GunStoreGuns']),('pawn','Pawn shops',['PawnShopGuns','PawnShopGunsSpecial']),
 ('police','Police, sheriff and prison armouries; police cars',['PoliceGuns']),('swat','SWAT vans',['A26SWAT_Trunk']),
 ('army','Army armouries, military crates and trucks',['ArmyGuns','A26Military_Trunk']),
 ('security','Security offices',['SecurityGuns']),('hunting','Hunting: farms, cabins, hunters’ trucks',['HuntGuns']),
 ('surplus','Survivalists: bunkers, prepper cellars, their cars',['SurplusGuns']),('storage','Storage units (rare)',['FirearmWeapons']),
 ('case','Gun cases in homes, garages, glove boxes',['Case_']),('ccw','Civilian dead: handgun in a purse or bum bag',['CCW_']),
 ('worn','Police and army dead: slung or holstered',['AttachedWeaponDefinitions'])]
import functools
env=functools.lru_cache(None)(env)
_ev=ev
def ev(expr,e): return _ev(expr,dict(e))
REG=['USA','SOV','CZE','KOR','PAC','EUR','ISR']
def status(gid, lists):
    a=ALIAS.get(gid,gid)
    best=None
    for lst,gun,expr,i in BYGUN.get(gid,[])+BYGUN.get(a,[]) if a!=gid else BYGUN.get(gid,[]):
        if gun not in (gid,a): continue
        if gun!=gid and lst.startswith(('Case_','CCW_')): continue
        if not any(lst.startswith(x) or x in lst for x in lists): continue
        if expr is None: return 'home'
        cr=[c for c in CROSS if c in expr]
        regs=[r for r in REG if ev(expr,env(0,r)) and ev(expr,env(0,r))>0]
        if not regs: continue
        st = 'cross' if cr else ('home' if 'USA' in regs else 'foreign')
        rank={'home':3,'cross':2,'foreign':1}
        if best is None or rank[st]>rank[best]: best=st
    return best or 'no'
RAR={'Colt1911':'C','Automag':'P','G17':'C','HK_USP':'S','Colt1903':'S','SW_M500':'R','SW_327':'R','Colt_Service38':'S',
 'AR57_PDW':'R','AUG9':'P','CAR15SMG':'R','MP7':'P','UZI':'R','M2Carbine':'S','AAC_Honey':'P','Bush_AR15_MOE':'S','AK47':'S',
 'F2000':'P','G36C':'P','SCARL':'P','AssaultRifle2':'R','FAL':'R','SCARH':'P','M1':'S','BOAR':'P','M82':'P','M40A3':'R','SV_98':'P',
 'M870_CQB':'C','UTS15':'R','M1014_Wick':'R','SPAS12_Fixed':'R','Winchester1866':'S','Ruger_No1_308':'S','American180':'R',
 'L86':'P','M60':'G','PKM':'G','RPG_7':'G','TenPointTurbo_XLT':'S','DSHK':'G'}
out=[]
for cat,g in picks:
    i=info.get(g,{})
    out.append(dict(id=g,name=i.get('name',g),cal=i.get('cal',''),cat=cat,built=g in built,rar=RAR[g],
                    places={k:status(g,l) for k,_,l in PL}))
hs=dict(id='HEAVY',name='Heavy Sniper Rifle (ours, kept)',cal='.50 BMG',cat='Kept from our set',built=True,rar='P',places={k:status('HEAVY',l) for k,_,l in PL})
json.dump(dict(places=[(k,n) for k,n,_ in PL],guns=out,heavy=hs),open('pagedata.json','w'))
for o in out+[hs]: print(o['id'].ljust(18),o['rar'],' '.join(f"{k}:{v[0]}" for k,v in o['places'].items()))
