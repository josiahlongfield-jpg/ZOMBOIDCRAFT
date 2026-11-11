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
import json as _j
SRC=_j.load(open(S+'/w3/src.json'))
PILOT=[('AMT1911', 'pistol', 'G17'), ('B93R', 'pistol', 'G17'), ('M9', 'pistol', 'G17'), ('G21', 'pistol', 'G17'), ('G34', 'pistol', 'G17'), ('VP70', 'pistol', 'G17'), ('MP45', 'pistol', 'G17'), ('XD4', 'pistol', 'G17'), ('Makarov', 'pistol', 'G17'), ('FN_57', 'pistol', 'G17'), ('MPA57DMG', 'pistol', 'G17'), ('DEagle', 'magnum', 'M29_44'), ('Judge513_45LC', 'magnum', 'M29_44'), ('GP100_6', 'magnum', 'M29_44'), ('M29_44Carbine', 'magnum', 'M29_44'), ('M1911_Carbine', 'smg', 'MP5_Stock'), ('KG9', 'smg', 'MP5_Stock'), ('Model55', 'smg', 'MP5_Stock'), ('Bizon2_01', 'smg', 'MP5_Stock'), ('Carbon15_97', 'smg', 'MP5_Stock'), ('M635S', 'smg', 'MP5_Stock'), ('EVO', 'smg', 'MP5_Stock'), ('KRISS', 'smg', 'MP5_Stock'), ('MAC10', 'smg', 'MP5_Stock'), ('UMP9', 'smg', 'MP5_Stock'), ('UMP45', 'smg', 'MP5_Stock'), ('UZI_Micro', 'smg', 'MP5_Stock'), ('AK12_New', 'guns', 'M4A1'), ('AssaultRifle', 'guns', 'M4A1'), ('AKM_Custom', 'guns', 'M4A1'), ('AK74_Custom', 'guns', 'M4A1'), ('ADAR', 'guns', 'M4A1'), ('MK47', 'guns', 'M4A1'), ('Bush_XM15', 'guns', 'M4A1'), ('Bush_XM15_Custom', 'guns', 'M4A1'), ('AK74', 'guns', 'M4A1'), ('AK74_Alpha', 'guns', 'M4A1'), ('AN94', 'guns', 'M4A1'), ('M723', 'guns', 'M4A1'), ('AR18', 'guns', 'M4A1'), ('AUG_A3', 'guns', 'M4A1'), ('FAMAS', 'guns', 'M4A1'), ('CAR15_Survival', 'guns', 'M4A1'), ('G33', 'guns', 'M4A1'), ('L85A2', 'guns', 'M4A1'), ('M4', 'guns', 'M4A1'), ('Ots14_4A_03', 'guns', 'M4A1'), ('Vz58', 'guns', 'M4A1'), ('XM8', 'guns', 'M4A1'), ('SKS', 'guns', 'M4A1'), ('RPK16', 'guns', 'M4A1'), ('M249E3', 'guns', 'M4A1'), ('RPD', 'guns', 'M4A1'), ('G21LMG', 'guns', 'M4A1'), ('AK308', 'battleRifle', 'FAL'), ('Galil', 'battleRifle', 'FAL'), ('M14EBR', 'battleRifle', 'FAL'), ('MAS49', 'battleRifle', 'FAL'), ('OTS_03_SVU', 'battleRifle', 'FAL'), ('TAC50', 'heavySniper', 'M82'), ('CheyTac50', 'heavySniper', 'M82'), ('Sako85', 'heavySniper', 'M82'), ('L96', 'heavySniper', 'M82'), ('M40', 'heavySniper', 'M82'), ('WA2000', 'heavySniper', 'M82'), ('M1903', 'heavySniper', 'M82'), ('TXS_804_45LC', 'shotgun', 'M870'), ('VIPER_G2', 'shotgun', 'M870'), ('ROYAL_SXS', 'shotgun', 'M870'), ('Colt1855', 'shotgun', 'M870'), ('KS23_S', 'shotgun', 'M870'), ('M870_Wingmaster', 'shotgun', 'M870'), ('M870_MCS', 'shotgun', 'M870'), ('Coachgun', 'shotgun', 'M870'), ('M1208', 'shotgun', 'M870'), ('SPAS_15', 'shotgun', 'M870'), ('USAS_12', 'shotgun', 'M870'), ('CAWS', 'shotgun', 'M870'), ('A400', 'shotgun', 'M870'), ('M1887', 'shotgun', 'M870'), ('TOZ106', 'shotgun', 'M870'), ('NEF_Handi_410', 'shotgun', 'M870'), ('SPAS12', 'shotgun', 'M870'), ('Viper', 'leverAction', 'Win_1894'), ('Thompson_Center_3006', 'leverAction', 'Win_1894'), ('AR7', 'rimfire', 'Ruger_1022'), ('KAC_M203', 'grenadeLauncher', None)]
def num(p,k,d=0.0):
    v=str(p.get(k,'')).split(',')[0].split('/*')[0].strip()
    try: return float(v)
    except: return d
def clamp(x,lo,hi): return max(lo,min(hi,x))
rows=[]
for gid,anc,ref in PILOT:
    g=A[SRC[gid]]; o=ours(anc); r=A[ref] if ref else g
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
json.dump(rows,open(S+'/review/stats3.json','w'),indent=1)
for x in rows:
    o=x['out'];print(f"{x['id']:18s} {x['anchor']:15s} dmg {o['damage']:6} rng {o['range']:6} ads {o['aimSpread']:5} hip {o['hipSpread']:5} kick {o['recoil']:5} delay {o['fireDelayTicks']:3} rpm {o.get('autoRpm') if o['selectFire'] else '-':>7} noise {o['shotNoise']:6} mag {o['magazineSize']:4} wt {o['weight']:5} dur {o['durability']:5} jam x{o['jamX']} reload x{o['reloadX']} {x['notes']}")
