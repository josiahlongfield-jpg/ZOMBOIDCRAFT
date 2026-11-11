import json,re,os,base64,shutil
S='/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/'
R=S+'review/'; O=R+'out3/'
d=json.load(open(O+'data.json'))
stats=json.load(open(R+'stats3.json')); SRC=json.load(open(S+'w3/src.json'))
L=open('/mnt/project-files/zomboid-mod/brita/refined-gun-list.txt').read().split('\n')
cat={}
CATS=[]
for line in L[1:]:
    if ':' not in line: continue
    c,rest=line.split(':',1)
    for gid in re.findall(r'(\w+) \(new\)',rest): cat[gid]=c.strip(); 
    if re.search(r'\(new\)',rest): CATS.append(c.strip())
# fixes
for g in d['guns']:
    st=g['stats']
    if g['id']=='KAC_M203': st['out']['magazineSize']=1
    if g['id']=='TXS_804_45LC': st['out']['pellets']=1
    g['cat']=cat[g['id']]
# M2A1 flamethrower row
import copy
gj=json.load(open(S+'look/out/guns3.json')); m2=next(x for x in gj if x['id']=='M2A1')
base=copy.deepcopy(next(g for g in d['guns'] if g['id']=='KAC_M203'))
base.update(id='M2A1',name='M2A1 Flame Thrower',tris=m2['tris'],lo=m2['lo'],hi=m2['hi'],att=m2['att'],tex=m2['tex'],upright=False,cat=cat['M2A1'],slots=[])
st=base['stats']; st.update(id='M2A1',name='M2A1 Flame Thrower',anchor='flamethrower (new)',ref=None,ammo='FlameFuel',modes='Auto')
st['out']={k:None for k in st['out']}; st['out'].update(magazineSize=100,weight=10.0,selectFire=True)
st['anc']={k:None for k in st['anc']}
d['guns'].append(base)
order={c:i for i,c in enumerate(CATS)}
names=[l.split(':')[0].strip() for l in L[1:] if ':' in l]
d['guns'].sort(key=lambda g:(names.index(g['cat']), [x for x in re.findall(r'(\w+) \(new\)',next(l for l in L if l.startswith(g['cat']+':')))].index(g['id'])))
d['cats']=[c for c in names if any(g['cat']==c for g in d['guns'])]
# notes
NEWCAL={'545x39Bullets':'5.45x39','223Bullets':'.223','4gShotgunShells':'4 gauge','10gShotgunShells':'10 gauge','20gShotgunShells':'20 gauge','410gShotgunShells':'.410','FlameFuel':'flamer fuel'}
W2CAL={'762x39Bullets':'7.62x39','3006Bullets':'.30-06','762x54rBullets':'7.62x54R','Bullets38':'.38 Special','Bullets380':'.380'}
CALN={'Bullets9mm':'9mm','Bullets45':'.45 ACP','Bullets44':'.44 Magnum','Bullets45LC':'.45 Colt','Bullets57':'5.7x28','556Bullets':'5.56','308Bullets':'.308','50BMGBullets':'.50 BMG','ShotgunShells':'12 gauge','Bullets22':'.22 LR',**NEWCAL,**W2CAL}
SPEC={
 'KAC_M203':['Arsenal gives the stand-alone M203 no damage or rounds of its own; it fires our 40mm grenade, one at a time, like our grenade launcher.'],
 'M2A1':['A flamethrower is a new kind of weapon: nothing we have works like it. My proposal: a short fire stream (about 8 blocks) that sets zombies alight and burns while you hold the trigger, from a 100-unit fuel tank; it never breaks blocks. Tell me if you want it, or if you want it left out for now.'],
 'TXS_804_45LC':['Arsenal fires .45 Colt bullets from this shotgun-frame gun, so it gets one projectile, not pellets.'],
 'Ots14_4A_03':['Arsenal fires 9mm from it (the real one uses 9x39), with its own built-in suppressor, so it is quiet (heard from 60 blocks) and only takes 10 rounds.'],
 'CheyTac50':['Sits next to our Intervention-style Heavy Sniper as you said. Same numbers as the TAC-50.'],
 'Carbon15_97':['Arsenal fires 5.56 from this pistol-size gun, so it hits like a rifle (12.8) but handles like our SMG.'],
 'MPA57DMG':['A pistol-size 5.7 with full auto in Arsenal (760 a minute).'],
 'B93R':['Arsenal gives the 93R no burst or auto, so it fires single shots here. Tell me if you want its real 3-round burst.'],
 'USAS_12':['Full-auto shotgun, 500 a minute, 7 shells.'],'CAWS':['Full-auto shotgun, about 490 a minute, 7 shells.'],
 'KS23_S':['4 gauge, the biggest shell here: 11 pellets, 3 shells, heavy kick.'],
 'GP100_6':['Arsenal fires .38 Special from it (the real one takes .357 too).'],
 'M29_44Carbine':['A .44 revolver carbine. Set against our magnum (18 a hit) with a longer reach.'],
 'Sako85':['.223 varmint rifle, set against our heavy sniper at 40% of its power (32 a hit), very quiet.'],
 'Viper':['.223 lever action, 11 rounds.'],'Thompson_Center_3006':['Single shot .30-06, like the Ruger No. 1.'],
 'NEF_Handi_410':['Single-shot .410, 2 pellets as Arsenal has it.'],
}
FOLD={'Model55','Bizon2_01','EVO','KRISS','MAC10','UMP9','UMP45','SPAS12'}
notes={}
for g in d['guns']:
    st=g['stats']; o=st['out']; a=st['ammo']; n=[]
    if g['id'] in SPEC: n+=SPEC[g['id']]
    if g['id'] not in('M2A1','KAC_M203'):
        cal=CALN.get(a,a)
        line=f"{cal}, {o['magazineSize']} rounds, {o['damage']:.1f} a hit (set against our {st['anchor']})."
        if o.get('selectFire') and o.get('autoRpm'): line+=f" Full auto at {round(o['autoRpm'])} a minute."
        n.insert(0,line)
        if a in NEWCAL: n.append(f"{NEWCAL[a]} is a new calibre: it needs a round item and a head value. I used the closest one we have (my guess, flagged).")
        if a in W2CAL: n.append(f"{W2CAL[a]} is the new calibre already planned for wave 2, so it shares that round item.")
    if g['id'] in FOLD: n.append("Arsenal has extended and folded-stock versions; this uses the extended stock model.")
    if not g['slots'] and g['id']!='M2A1': n.append('Arsenal gives this gun no attachments.')
    notes[g['id']]=n
d['notes']=notes
# meshes: parts in one file, guns in chunks per category
os.makedirs(O+'m',exist_ok=True); os.makedirs(O+'gt',exist_ok=True); os.makedirs(O+'pt',exist_ok=True)
mods={o['model'] for g in d['guns'] for s in g['slots'] for o in s['opts'] if o['model']}
json.dump({'p:'+m:base64.b64encode(open(R+'out/p/'+m+'.bin','rb').read()).decode() for m in mods},open(O+'parts.json','w'))
for i,c in enumerate(d['cats']):
    json.dump({'g:'+g['id']:base64.b64encode(open(S+'look/out/m/'+g['id']+'.bin','rb').read()).decode() for g in d['guns'] if g['cat']==c},open(O+f'm/c{i}.json','w'))
for g in d['guns']:
    g['chunk']=d['cats'].index(g['cat'])
    shutil.copy(S+'look/out/t/'+g['id']+'_toon.png',O+'gt/'+g['id']+'_toon.png')
tex={d['models'][m]['tex'] for m in mods if m in d['models'] and d['models'][m]['tex']}
for t in tex: shutil.copy(R+'out/pt/'+t+'.png',O+'pt/'+t+'.png')
json.dump(d,open(O+'data.json','w'))
print(len(d['guns']),len(d['cats']),len(tex),'pt')
