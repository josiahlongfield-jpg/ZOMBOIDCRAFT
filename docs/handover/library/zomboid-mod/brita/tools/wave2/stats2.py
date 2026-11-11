# First-person stats for the Brita pilot guns: each gun is set against one of our guns (its anchor) through a matching
# Zomboid gun (its reference), so a Brita gun that Zomboid makes 20% stronger than the reference ends up 20% stronger
# than our anchor. Crit is dropped (the head is the crit).
import json,math,sys
S=sys.argv[1]
A=json.load(open(S+'/pz/allstats.json')); O=json.load(open(S+'/ours_stats.json'))
base=O['guns(assault_rifle)']
def ours(k):
    d={x:base[x] for x in base}; d.update(O.get(k,{}))
    if k=='shotgun': d.update(damage='3.5',headshotMultiplier='2.5',hipSpread='2.0',aimSpread='0.6',recoil='2.2',shotNoise='120.0',aimZoom='0.85',range='40.0',fireDelayTicks='15',magazineSize='5',pellets='8')
    if k=='crossbow': d.update(damage='30.0',headshotMultiplier='3.0',hipSpread='1.8',aimSpread='0.2',recoil='0.4',shotNoise='8.0',aimZoom='0.85',range='60.0',fireDelayTicks='10',magazineSize='1')
    return {x:(v=='true' if v in('true','false') else float(v.rstrip('dDfF'))) for x,v in d.items()}
WEIGHT={'pistol':.95,'magnum':1.35,'smg':2.5,'guns':3.0,'battleRifle':3.9,'heavySniper':12.0,'shotgun':3.4,'leverAction':3.0,'rimfire':2.3,'grenadeLauncher':5.3,'crossbow':3.2}
DUR={'pistol':1200,'magnum':1600,'smg':1500,'guns':1600,'battleRifle':1700,'heavySniper':2000,'shotgun':1400,'leverAction':1700,'rimfire':1500,'grenadeLauncher':900,'crossbow':1400}
NAME={'pistol':'pistol','magnum':'magnum','smg':'SMG','guns':'assault rifle','battleRifle':'battle rifle','heavySniper':'heavy sniper','shotgun':'pump shotgun','leverAction':'lever-action','rimfire':'rimfire rifle','grenadeLauncher':'grenade launcher','crossbow':'crossbow'}
PILOT=[('Automag','magnum','M29_44'),('G17','pistol','G17'),('HK_USP','pistol','G17'),('Colt1903','pistol','G17'),
 ('SW_327','magnum','M29_44'),('Colt_Service38','magnum','M29_44'),('AUG9','smg','MP5_Stock'),('CAR15SMG','smg','MP5_Stock'),
 ('MP7','smg','MP5_Stock'),('UZI','smg','MP5_Stock'),('Bush_AR15_MOE','guns','M4A1'),('AK47','guns','M4A1'),('F2000','guns','M4A1'),
 ('G36C','guns','M4A1'),('SCARL','guns','M4A1'),('FAL','battleRifle','FAL'),('SCARH','battleRifle','FAL'),('M1','battleRifle','FAL'),
 ('M82','heavySniper','M82'),('M40A3','heavySniper','M82'),('SV_98','heavySniper','M82'),('UTS15','shotgun','M870'),
 ('M1014_Wick','shotgun','M870'),('SPAS12_Fixed','shotgun','M870'),('Ruger_No1_308','leverAction','Win_1894'),
 ('M60','guns','M4A1'),('PKM','guns','M4A1')]
def num(p,k,d=0.0):
    v=str(p.get(k,'')).split(',')[0].split('/*')[0].strip()
    try: return float(v)
    except: return d
def clamp(x,lo,hi): return max(lo,min(hi,x))
rows=[]
for gid,anc,ref in PILOT:
    g=A[gid]; o=ours(anc); r=A[ref] if ref else g
    R=lambda k: (num(g,k)/num(r,k)) if num(r,k) else 1.0
    avg=lambda p:(num(p,'MinDamage')+num(p,'MaxDamage'))/2
    s=dict(o); notes=[]
    if avg(r): s['damage']=o['damage']*avg(g)/avg(r)
    s['range']=o['range']*R('MaxRange') if anc!='grenadeLauncher' else o['range']
    acc=clamp((num(r,'HitChance')/max(1,num(g,'HitChance')))**0.5,0.6,1.8)
    s['aimSpread']=o['aimSpread']*acc; s['hipSpread']=o['hipSpread']*acc
    rd=R('RecoilDelay') if num(g,'RecoilDelay') else 1.0
    s['recoil']=o['recoil']*clamp(rd*((s['damage']/o['damage']) if o['damage'] else 1)**0.3,0.5,2.2)
    s['fireDelayTicks']=max(1,round(o['fireDelayTicks']*rd))
    s['bloomRecover']=o['bloomRecover']*R('AimingTime')
    s['shotNoise']=o['shotNoise']*R('SoundRadius')
    s['magazineSize']=int(num(g,'MaxAmmo'))
    modes=(g.get('FireMode','')+' '+g.get('FireModePossibilities','')).lower()
    s['selectFire']='auto' in modes
    if s['selectFire']:
        baseRpm=o['autoRpm'] if o['selectFire'] else ours('smg')['autoRpm']*num(A['MP5_Stock'],'RecoilDelay')/max(1,num(g,'RecoilDelay'))
        s['autoRpm']=baseRpm*(1/rd if o['selectFire'] and rd else 1)
        if False: s['autoRpm']=600.0; notes.append('Zomboid gives it no recoil delay (tripod), so 600 rounds a minute, its real rate')
    if 'pellets' in o: s['pellets']=round(o['pellets']*R('ProjectileCount'))
    w=WEIGHT[anc]*R('Weight'); dur=round(DUR[anc]*clamp(R('ConditionMax'),0.7,1.4)/10)*10
    jam=R('JamGunChance') if num(r,'JamGunChance') else 1.0
    rel=R('ReloadTime') if ref else 1.0
    rows.append(dict(id=gid,name=g.get('DisplayName','').strip().rstrip(','),anchor=NAME[anc],ref=(A[ref].get('DisplayName','').strip().rstrip(',') if ref else None),
        ammo=g.get('AmmoType','').split(',')[0].strip().replace('Base.',''),
        pz={k:num(g,k) for k in ['MinDamage','MaxDamage','MaxRange','HitChance','AimingTime','RecoilDelay','ReloadTime','MaxAmmo','SoundRadius','ProjectileCount','Weight','ConditionMax','JamGunChance']},
        pzref=({k:num(r,k) for k in ['MinDamage','MaxDamage','MaxRange','HitChance','AimingTime','RecoilDelay','ReloadTime','MaxAmmo','SoundRadius','ProjectileCount','Weight','ConditionMax','JamGunChance']} if ref else None),
        anc={k:o[k] for k in ['damage','headshotMultiplier','range','aimSpread','hipSpread','recoil','fireDelayTicks','bloomRecover','shotNoise','magazineSize','selectFire','autoRpm']+(['pellets'] if 'pellets' in o else [])}|{'weight':WEIGHT[anc],'durability':DUR[anc]},
        out={k:(round(v,2) if isinstance(v,float) else v) for k,v in s.items() if k in ['damage','headshotMultiplier','range','aimSpread','hipSpread','recoil','fireDelayTicks','bloomRecover','shotNoise','magazineSize','selectFire','autoRpm','pellets']}|{'weight':round(w,2),'durability':dur,'jamX':round(jam,2),'reloadX':round(rel,2)},
        modes=g.get('FireModePossibilities','').strip() or g.get('FireMode','').strip(),notes=notes))
json.dump(rows,open(S+'/review/stats2.json','w'),indent=1)
for x in rows:
    o=x['out'];print(f"{x['id']:18s} {x['anchor']:15s} dmg {o['damage']:6} rng {o['range']:6} ads {o['aimSpread']:5} hip {o['hipSpread']:5} kick {o['recoil']:5} delay {o['fireDelayTicks']:3} rpm {o.get('autoRpm') if o['selectFire'] else '-':>7} noise {o['shotNoise']:6} mag {o['magazineSize']:4} wt {o['weight']:5} dur {o['durability']:5} jam x{o['jamX']} reload x{o['reloadX']} {x['notes']}")
