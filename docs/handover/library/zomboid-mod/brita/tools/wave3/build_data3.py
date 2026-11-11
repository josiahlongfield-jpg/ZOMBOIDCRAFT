import json,re
S='/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/'
P=json.load(open(S+'review/parts.json')); mwp=json.load(open(S+'review/mwp3.json'))
PM=json.load(open(S+'review/out/parts_models.json')); stats=json.load(open(S+'review/stats3.json'))
guns=json.load(open(S+'look/out/guns3.json')); A=json.load(open(S+'pz/allstats.json'))
board=json.load(open(S+'board/data.json'))
SLOT={'Scope':'Optic','Canon':'Muzzle','Stock':'Light / laser','Sling':'Underbarrel','RecoilPad':'Grip','Clip':'Magazine'}
ORDER=['Optic','Muzzle','Underbarrel','Light / laser','Grip','Magazine']
def f(v):
    try: return float(str(v).split(',')[0])
    except: return 0.0
def optic(name):
    n=name.lower()
    z=re.search(r'\((?:[\d.]+-)?([\d.]+)x',n) or re.search(r'([\d.]+)x\b',n)
    zl=re.search(r'\(([\d.]+)-([\d.]+)x',n)
    zoom=float(z.group(1)) if z else 1.0
    if 'ghost ring' in n: return dict(kind='Irons',reticle='ghost ring aperture',zoom=1.0)
    if 'thermal' in n or 'night vision' in n or 'pvs-4' in n or '1pn58' in n:
        return dict(kind='Night / thermal',reticle=('thermal picture' if 'thermal' in n else 'night-vision picture')+' with a crosshair',zoom=zoom if z else 3.0)
    if 'magnifier' in n: return dict(kind='Holographic + magnifier',reticle='holo ring, 3x magnifier swings aside',zoom=3.0)
    if any(k in n for k in ['eotech','xps','uh-1','holographic']): return dict(kind='Holographic',reticle='holo ring and dot',zoom=1.0)
    if any(k in n for k in ['acog','c79','susat','l2a2','raven','brownells','elcan','pso-1','1p21']):
        r='PSO chevron and range scale' if ('pso' in n or '1p21' in n) else 'lit chevron / post'
        return dict(kind='Prism / combat scope',reticle=r,zoom=zoom,zoomRange=[float(zl.group(1)),float(zl.group(2))] if zl else None)
    if zoom>1.5 or 'scope' in n:
        return dict(kind='Rifle scope',reticle='duplex crosshair, full scope picture',zoom=zoom,zoomRange=[float(zl.group(1)),float(zl.group(2))] if zl else None)
    if zoom>1: return dict(kind='Red dot',reticle='dot',zoom=zoom)
    return dict(kind='Red dot',reticle='dot',zoom=1.0)
def effects(p,g):
    pz=g['pz']; out=[]
    hm=f(p.get('HitChanceModifier'))
    if hm and pz['HitChance']:
        r=(pz['HitChance']/max(1,pz['HitChance']+hm))**0.5; out.append(('Spread',r))
    rd=f(p.get('RecoilDelayModifier'))
    if rd and pz['RecoilDelay']: out.append(('Kick',(pz['RecoilDelay']+rd)/pz['RecoilDelay']))
    at=f(p.get('AimingTimeModifier'))
    if at and pz['AimingTime']: out.append(('Aim settle',(pz['AimingTime']+at)/pz['AimingTime']))
    mr=f(p.get('MaxRangeModifier'))
    if mr and pz['MaxRange']: out.append(('Range',(pz['MaxRange']+mr)/pz['MaxRange']))
    rt=f(p.get('ReloadTimeModifier'))
    if rt and pz['ReloadTime']: out.append(('Reload time',(pz['ReloadTime']+rt)/pz['ReloadTime']))
    dm=f(p.get('DamageModifier'))
    if dm: out.append(('Damage',1+dm/max(.1,(pz['MinDamage']+pz['MaxDamage'])/2)))
    w=f(p.get('WeightModifier'))
    return [{'what':k,'x':round(v,3)} for k,v in out if abs(v-1)>0.005], round(w,2)
SND={b['name']:b for b in board['guns']}
picks={}
for line in open('/mnt/project-files/zomboid-mod/brita/pilot-sound-picks.txt'):
    if ':' in line and '=' in line and not line.startswith(('Attachments','NOTE','Brita')):
        n,rest=line.split(':',1); picks[n.strip()]=dict(x.strip().split('=',1) for x in rest.split(';'))
BOARDNAME={'Colt1911':'Colt M1911','SW_M500':'S&W Model 500','AR57_PDW':'AR-57 PDW','M2Carbine':'M2 Carbine','AAC_Honey':'AAC Honey Badger','AssaultRifle2':'M14','BOAR':'BOAR .50','M870_CQB':'Remington 870 CQB','Winchester1866':'Winchester 1866','American180':'American-180','L86':'L86 LSW','RPG_7':'RPG-7','TenPointTurbo_XLT':'TenPoint Turbo XLT','DSHK':'DShK'}
import sys
UPRIGHT={g['id'] for g in json.load(open(S+'look/out/guns3.json')) if (g['att'].get('muzzle',{}).get('offset',[0,0,1])[2]<0)}
print('upright',sorted(UPRIGHT))
SRC3=json.load(open(S+'w3/src.json'))
out=[]
for g in guns:
    gid=g['id']; st=next((s for s in stats if s['id']==gid),None)
    if st is None: continue
    lines={x[0]:x for x in mwp[gid]}
    slots={}
    for item,p in P.items():
        if item.endswith('_ON'): continue
        ln=lines.get(item)
        fits = gid in p['MountOn'] or SRC3[gid] in p['MountOn'] or (p['PartType']=='Clip' and ln)
        if not fits: continue
        model = ln[1] if ln and ln[1] in PM and ln[1]!='Mag_Invisible' else None
        ga = ln[2] if ln and len(ln)>2 else None; pa = ln[3] if ln and len(ln)>3 else None
        if p['PartType']=='Clip' and not model and gid!='American180': continue
        eff,w=effects(p,st)
        e={'item':item,'name':p['DisplayName'],'model':model,'gunAtt':ga,'partAtt':pa,'effects':eff,'weight':w}
        if p['PartType']=='Scope': e['optic']=optic(p['DisplayName'])
        if item.startswith(('Laser','Light')): e['toggle']=True
        slots.setdefault(SLOT[p['PartType']],[]).append(e)
    snd=[]
    out.append({'id':gid,'name':g['name'],'tris':g['tris'],'lo':g['lo'],'hi':g['hi'],'att':g['att'],'tex':g['tex'],
        'upright':gid in UPRIGHT,'stats':st,'slots':[{'slot':s,'opts':slots[s]} for s in ORDER if s in slots],'sounds':snd})
json.dump({'guns':out,'models':{k:{'tex':v['tex'],'att':v['att'],'scale':v['scale']} for k,v in PM.items()}},open(S+'review/out3/data.json','w'))
for g in out: print(g['id'],[ (s['slot'],len(s['opts'])) for s in g['slots']], len(g['sounds']),'sounds')
