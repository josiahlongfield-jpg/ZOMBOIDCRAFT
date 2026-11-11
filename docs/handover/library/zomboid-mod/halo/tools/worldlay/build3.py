import json,numpy as np
from PIL import Image
W='/tmp/claude-0/halo/worldlay'; O='/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/worldlay'
OV=f'{W}/ov'
PATCH={'m10':(-4900,-3700,4300),'m20':(-300,-5400,3500),'m60':(5200,-6200,3100),'m35':(-4300,3100,4800),'m52':(3900,1700,4300),'m50':(6300,-2500,2900),'m70':(700,6100,4300)}
F=1500
def feather(m,R):
    im=Image.open(f'{OV}/land_{m}.png'); a=np.asarray(im).astype(np.float32); n=a.shape[0]
    yy,xx=np.mgrid[0:n,0:n]; d=np.hypot(xx-n/2,yy-n/2)*8.0
    k=np.clip((R-d)/F,0,1); k=k*k*(3-2*k); a[...,3]*=k
    Image.fromarray(a.astype(np.uint8),'RGBA').save(f'{O}/ov/land_{m}.webp','WEBP',quality=80,method=6)
    return float((a[...,3]>128).sum()*64/1e6)
patches=[]
LC={};LK={}
for m,(x,z,R) in PATCH.items():
    LK[m]=feather(m,R); i=json.load(open(f'{OV}/land_{m}.json')); LC[m]=(i['cx'],i['cz'],x,z)
    patches.append(dict(id=m,img=f'ov/land_{m}.webp',x=x,z=z,size=i['size'],R=R))
D=json.load(open(f'{O}/data.json'))
host={'winter':'m10','swordbase':'m20','package':'m60','spear':'m35','nightfall':'m35','na':'m52','exodus':'m50','aszod':'m70'}
OVN={s['id']:s['img'][3:-5] for s in D['sites']}
FREE={'sabre':(1700,-2000),'countdown':(3000,-2900),'powerhouse':(-1700,-1300),'highlands':(-2600,-7300),'breakpoint':(-7000,-7100),
 'overlook':(-7000,-800),'unearthed':(-1200,4600),'lonewolf':(-1500,7400),'boardwalk':(6900,4300),'tempest':(7300,6600),'forge':(7300,-7400),
 'battlecanyon':(-6800,6600),'ridgeline':(-6000,7500),'breakneck':(2300,3900),'highnoon':(-7300,-5300),'solitary':(-200,-1600),'penance':(5600,2900),'inst04':(6600,-4600)}
WHERE={'winter':'North-west highlands, inside its own Visegrád valley','swordbase':'North, inside its own cliff-walled valley','package':'North-east, the same valley in winter, up in the mountains',
 'spear':'South-west plains, beside Nightfall as in the game','nightfall':'South-west ridges, beside Tip of the Spear as in the game','na':'East, around its own bay','exodus':'North-east coast, the city outskirts',
 'aszod':'South coast','sabre':'Centre-north highlands, between Sword Base and the city','countdown':'Beside the launch facility','powerhouse':'Centre, the dam on the river down from the highlands',
 'highlands':'North, above Sword Base valley','breakpoint':'Far north-west snowfields','overlook':'West, a bluff off Winter Contingency','unearthed':'South, dig site on the plains',
 'lonewolf':'South-west cliffs above Aszod','boardwalk':'East coast seafront south of the city','tempest':'Island off the south-east coast','forge':'Island off the north-east coast',
 'reflection':'Penthouse in a New Alexandria tower','battlecanyon':'South-west canyon','ridgeline':'South-west woods','breakneck':'City outskirts','highnoon':'North-west canyon','solitary':'Centre, prison block','penance':'Under the city power plant','inst04':'East coast cliffs'}
for s in D['sites']:
    sid=s['id']; s['old']=WHERE.get(sid,s['old'])
    oi=json.load(open(f'{OV}/{OVN[sid]}.json'))
    if sid in host:
        cx,cz,x,z=LC[host[sid]]; bx=oi['lo'][0]+oi['w']/2; bz=oi['lo'][1]+oi['d']/2
        s['x']=round(x+bx-cx); s['z']=round(z+bz-cz); s['inland']=True
    elif sid=='reflection':
        na=[t for t in D['sites'] if t['id']=='na'][0]; s['x']=LC['m52'][2]+80; s['z']=LC['m52'][3]-350
    else:
        s['x'],s['z']=FREE[sid]
    s['rot']=90 if sid=='forge' else 0
    s['ldy']={'nightfall':16,'spear':-16,'countdown':14,'sabre':-6}.get(sid,0)
# squads
SQ=[]
for m,site,hostm in (('m10','winter','m10'),('m20','swordbase','m20'),('m45','sabre',None)):
    R=json.load(open(f'{W}/sq_{m}.json'))
    if hostm: cx,cz,x,z=LC[hostm]
    else:
        oi=json.load(open(f'{OV}/m45g.json')); cx=oi['lo'][0]+oi['w']/2; cz=oi['lo'][1]+oi['d']/2; x,z=FREE['sabre']
    for q in R['squads']:
        if not q['points'] or not q['cells']: continue
        if q['name'].startswith('sq_test') or '_fx_' in q['name']: continue
        ch=[c for cell in q['cells'] for c in cell['chars'] if c]
        veh=[cell['vehicle'] for cell in q['cells'] if cell['vehicle']]
        n=sum(max(0,cell['count']) for cell in q['cells'])
        p=q['points'][0]['pos']
        wx,wz=x+p[0]-cx,z+p[2]-cz
        if m=='m45' and (abs(p[0]-cx)>1500 or abs(p[2]-cz)>1500): continue  # space squads
        side='unsc' if any(k in c for c in ch for k in('marine','trooper','spartan','odst','civilian','pilot','militia','human')) or any(k in q['name'] for k in('unsc','kat','jorge','carter','emile','jun','noble','marine')) else 'cov'
        SQ.append(dict(n=q['name'],x=round(wx),z=round(wz),c=n,ch=sorted(set(ch))[:4],v=veh[0] if veh else None,side=side,map=m))
D['patches']=patches; D['squads']=SQ; D['landkm2']=round(sum(LK.values()))
D['kit']=[dict(id=f'k{i}',name=n,style=st,x=x,z=z) for i,(n,st,x,z) in enumerate([
 ('UNSC firebase','unsc',-2300,-3200),('UNSC firebase','unsc',2600,-4200),('UNSC firebase','unsc',-1200,1400),('UNSC firebase','unsc',3600,4900),
 ('Covenant forward base','cov',-6600,600),('Covenant forward base','cov',1000,-7400),('Covenant forward base','cov',-2900,6400),('Covenant forward base','cov',6600,-4000),
 ('Covenant dig site','cov',-500,3300),('Glassed crater','cov',-6800,-1900),
 ('Farmstead','farm',-2700,-1000),('Farmstead','farm',-6900,-5700),('Farmstead','farm',-2000,2700),('Plains farm','farm',-6900,4400),
 ('Relay outpost','unsc',-4300,-200),('Relay outpost','unsc',3000,-7600),('Evac port','city',1900,600),('Highway town','city',3300,-1500),
 ('Fishing village','coast',4600,7000),('Shipbreaker camp','coast',-2300,5100),('Supply depot','unsc',-4400,-6900),('Survivor holdout','farm',3700,-3600)])]
SEA=[[[8192,-8192],[6600,-8192],[7700,-6000],[7700,-4300],[7550,-1500],[7900,600],[7900,2600],[7400,3800],[5800,5000],[4100,6400],[3300,8192],[8192,8192]],
     [[-2600,8192],[-1900,7300],[-1300,6500],[-500,5300],[100,5500],[-150,7000],[300,8192]]]
D['world']=dict(size=16384,areas=[dict(id='sea',p=p) for p in SEA],islands=[dict(id='forge-i',x=7300,z=-7400,rx=520,rz=800),dict(id='tempest-i',x=7300,z=6600,rx=360,rz=260),dict(id='bw',x=6900,z=4300,rx=10,rz=10)],
  lakes=[],rivers=[dict(id='river',w=120,p=[[-1400,-2600],[-1700,-1300],[-1000,-300],[300,600],[1400,1300],[2400,1900]])],roads=[],rail=[],urban=[],places=[])
json.dump(D,open(f'{O}/data.json','w'),separators=(',',':'))
print(len(SQ),'squads', sum(1 for q in SQ if q['v']),'with vehicles')
for s in D['sites']: print(s['id'],s['x'],s['z'])
