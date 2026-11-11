import json,re,collections
D='pdb/database/'
I=json.load(open(D+'templates/items.json')); L=json.load(open(D+'locales/global/en.json'))
H={h['Id']:h['Price'] for h in json.load(open(D+'templates/handbook.json'))['Items']}
OUT=re.compile(r'boss|halloween|xmass|tagilla|sanitar|pestily|glorious|claus|dedmoroz|jack_o_lantern|capsuperman|facecover_pipe|welding|glasses_pixel|beard|mustache|killa|kaban|reshala|glukhar|kollontay|shturman|zryach|knight|birdeye|big.?pipe|partisan|santa|cultist|gorilla|ubey|ghost|halloween|crown|kek|jack|death|skull_ring|mark of|twitch|rivals|arena cup|\(arena\)|prestige|unheard|_dev|_test|deadly|zombie|born to die|luxury|not nice|say nothing|louise|oni\)|demon|smile|stop me|scars|\(skull|muertos|\(fear|lucha|red nose|shroud|gus |scratches|target\)',re.I)
nm=lambda t:L.get(t+' Name',I[t]['_props'].get('Name'))
par=lambda t:I[I[t]['_parent']]['_name']
CAT={'Headwear':'headwear','Headphones':'earpiece','FaceCover':'face cover','Visors':'eyewear','ArmBand':'armband','Armor':'armour','Vest':'rig','Backpack':'backpack','ArmorPlate':'plate','ArmoredEquipment':'helmet part','Mount':'helmet part','NightVision':'night vision','SpecialScope':'night vision'}
def slotinfo(p):
    soft=[];plates=[];extra=0;w=0
    for s in p.get('Slots',[]):
        f=(s['_props'].get('filters') or [{}])[0]; d=f.get('Plate')
        if s['_name'].lower().startswith(('soft_armor','collar','groin','helmet_','shoulder','left_side','right_side')) or (d in I and par(d)=='BuiltInInserts'):
            if d in I:
                q=I[d]['_props']; soft.append(dict(zone=s['_name'],cls=int(q['armorClass']),dur=q['MaxDurability'],mat=q['ArmorMaterial'],parts=q.get('armorColliders')))
                w+=q.get('Weight',0)
        elif 'plate' in s['_name'].lower():
            dp=I.get(d,{}).get('_props',{})
            acc=[int(I[x]['_props'].get('armorClass',0)) for x in f.get('Filter',[]) if x in I]
            plates.append(dict(slot=s['_name'],default=nm(d) if d in I else None,cls=int(dp['armorClass']) if dp else None,accepts=[min(acc),max(acc)] if acc else None,parts=f.get('armorPlateColliders')))
            if d in I: extra+=H.get(d,0); w+=dp.get('Weight',0)
    return soft,plates,extra,w
res=[]
for t,it in I.items():
    if it['_type']!='Item': continue
    p=it['_props']; path=(p.get('Prefab') or {}).get('path','')
    m=re.match(r'assets/content/items/equipment/(?:([^/]+)/)?(?:[^/]+/)?([^/]+)\.bundle',path)
    if not m: continue
    cat=CAT.get(par(t))
    if not cat: continue
    folder=m.group(1) or m.group(2); name=nm(t)
    if OUT.search(folder) or OUT.search(path) or OUT.search(name or '') or OUT.search(it['_name']): continue
    soft,plates,extra,iw=slotinfo(p)
    r=dict(id=t,name=name,cat=cat,folder=folder,bundle=path.split('/')[-1],size=[p['Width'],p['Height']],
           weight=round(p.get('Weight',0)+iw,3),price=(H.get(t) or 0)+extra,trar=p.get('RarityPvE'))
    if p.get('Grids'): r['grids']=[[g['_props']['cellsH'],g['_props']['cellsV']] for g in p['Grids']]; r['cells']=sum(a*b for a,b in r['grids'])
    if p.get('armorClass') not in (None,0,'0',''): r['cls']=int(p['armorClass']); r['dur']=p.get('MaxDurability'); r['mat']=p.get('ArmorMaterial'); r['parts']=p.get('armorColliders') or p.get('headSegments') or p.get('armorZone')
    if soft: r['soft']=soft
    if plates: r['plates']=plates
    for k,o in (('speedPenaltyPercent','speed'),('mousePenalty','turn'),('weaponErgonomicPenalty','ergo'),('DeafStrength','deaf'),('BlocksEarpiece','blocksEar'),('BlocksEyewear','blocksEyes'),('BlocksHeadwear','blocksHead'),('BlocksFaceCover','blocksFace'),('BlocksArmorVest','blocksArmour'),('ArmorType','armourType')):
        v=p.get(k)
        if v not in (None,0,False,'','None'): r[o]=v
    if p.get('Slots') and cat in('headwear','helmet part'): r['mods']=[s['_name'] for s in p['Slots'] if not s['_name'].lower().startswith('helmet_')]
    res.append(r)
# rarity tiers
T=['Common','Scarce','Rare','Prized','Grail']
by=collections.defaultdict(list)
for r in res: by[r['cat']].append(r)
for c,rs in by.items():
    ps=sorted(r['price'] for r in rs)
    for r in rs:
        pc=ps.index(r['price'])/max(1,len(ps)-1)
        b={'Common':0,'Rare':1,'Superrare':2}.get(r['trar'])
        if b is None: tier=0 if pc<.3 else 1 if pc<.55 else 2 if pc<.8 else 3 if pc<.95 else 4
        elif b==0: tier=0 if pc<.5 else 1
        elif b==1: tier=1 if pc<.5 else 2
        else: tier=3 if pc<.85 else 4
        mx=max([r.get('cls') or 0]+[s['cls'] for s in r.get('soft',[])]+[x['cls'] or 0 for x in r.get('plates',[])])
        if mx>=6: tier=max(tier,4)
        elif mx>=5: tier=max(tier,3)
        r['rarity']=T[tier]; r['maxcls']=mx
json.dump(res,open('gear/tarkov_gear.json','w'),indent=1)
print(len(res),collections.Counter(r['cat'] for r in res))
print(collections.Counter((r['cat'],r['rarity']) for r in res))
# where found + use
import re as _r
def where(r):
    n=r['name'].lower(); c=r['cat']; tr=r['trar']
    if _r.search(r'police|kolpak|psh-97|kirasa|paca|security|untar|emercom|riot',n): return 'Police stations, riot vans, checkpoints'
    if _r.search(r'firefighter|shpm',n): return 'Fire stations'
    if _r.search(r'medpack|med pack',n): return 'Hospitals, ambulances, field medic posts'
    if _r.search(r'tourist|pilgrim|transfer|hunter|umka|boonie|ushanka|beanie|pompon|bandana|ski hat|cowboy|leather cap|basmach|sling bag|duffle|transformer|vertx|daypack|day pack|redfox|cap \(|baseball',n) and c in('backpack','rig','headwear'): return 'Houses, outdoor and sports shops, campsites, civilian zombies'
    if c in('eyewear',) and not _r.search(r'ballistic|ess|6b34|npp|tactical|condor|crossbow|rydex|shadow',n): return 'Houses, clothing shops, petrol stations'
    if c=='armband': return 'Military sites and fallen-unit stories (team colours)'
    if r['maxcls']>=5 or c=='night vision' or r['rarity'] in('Prized','Grail'): return 'Military bases, airfields, crashed choppers, the fallen unit before you'
    return 'Military checkpoints, army surplus, gun shops, soldier zombies'
def use(r):
    c=r['cat']; u=[]
    if r.get('grids'): u.append('%d pockets / %d cells'%(len(r['grids']),r['cells']))
    if r.get('soft') or r.get('cls'):
        cls=r.get('cls') or max(s['cls'] for s in r['soft']); u.append('armour class %d'%cls)
    if r.get('plates'): u.append('%d plate slots'%len(r['plates']))
    if c=='earpiece': u.append('louder footsteps and zombie groans, gunfire cut to a safe level')
    if c=='night vision': u.append('night vision on a helmet mount')
    if c=='armband': u.append('team colour so squadmates spot you (friendly fire)')
    if c=='face cover':
        n=r['name'].lower()
        if 'gas' in n or 'respirator' in n: u.append('filters spore haze and smoke')
        elif r.get('cls'): pass
        else: u.append('warmth, hides face')
    if c=='eyewear' and not r.get('cls'): u.append('stops blood splash to the eyes')
    if r.get('deaf'): u.append('muffles hearing (%s)'%r['deaf'])
    return ', '.join(u)
for r in res: r['where']=where(r); r['use']=use(r)
json.dump(res,open('gear/tarkov_gear.json','w'),indent=1)
print(collections.Counter(r['where'] for r in res))
