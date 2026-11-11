"""parts.json for the game (config/zomboidcraft/tarkov/parts.json): every batch-1 part and gun base with its bench
socket, its own slots (where they sit in the frame of the slot holding it, and what fits them), sights' eye points
with their modes (zoom, reticle texture, reticle span), grip holds, muzzle, laser/light, conflicts, magazine size.
Also writes each part's reticles into its export json for pack_part.py."""
import sys,json,os,re,glob
import numpy as np
TK2='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/tk2'
cwd=os.getcwd(); os.chdir(TK2); sys.path.insert(0,TK2)
from db import *
os.chdir(cwd)
CAT=json.load(open('catalog.json')); S=json.load(open('/tmp/claude-0/tk/b1/sights.json'))
K=json.load(open(TK2+'/kits.json'))
SIGHT=('Collimator','CompactCollimator','AssaultScope','OpticScope','SpecialScope')
SOCK={'FlashHider':'muzzle','MuzzleCombo':'muzzle','Silencer':'suppressor','Foregrip':'underbarrel','Bipod':'bipod',
      'TacticalCombo':'side_rail','Flashlight':'side_rail','Stock':'stock','Handguard':'handguard','PistolGrip':'pistol_grip',
      'Barrel':'barrel','Receiver':'receiver','Charge':'charging','Gasblock':'gas_block','Magazine':'magazine'}
MOA={'5a32aa8bc4a2826c6e06d737':85,'57ae0171245977343c27bfcf':85}
# where each part is held: rear or front sight by the slots that take it
rear=set(); front=set()
for f in glob.glob('parts/*.json'):
    r=json.load(open(f))
    for sn,v in (r.get('slots') or {}).items():
        for c in v.get('filter',[]):
            if 'rear' in sn: rear.add(c)
            if 'front' in sn: front.add(c)
def un(m):
    m=np.array(m,float); m[:3,:3]/=np.linalg.norm(m[:3,:3],axis=0)
    if np.linalg.det(m[:3,:3])<0: m[:3,0]*=-1
    return m
def num(name):
    m=re.search(r'mode_0*(\d+)$',name) or re.search(r'linza_0*(\d+)$',name)
    return int(m.group(1)) if m else None
def r6(v): return [round(float(x),6) for x in v]
out={}
for f in sorted(glob.glob('parts/*.json')):
    r=json.load(open(f)); pid=r['id']
    if 'fail' in r: continue
    e=CAT.get(pid); kind=e['kind'] if e else 'Weapon'
    p={'name':name(pid),'short':short(pid),'kind':kind}
    if kind in SIGHT: p['socket']='optic'
    elif kind=='IronSight': p['socket']='rear_sight' if pid in rear or 'rear' in name(pid).lower() else 'front_sight'
    elif kind in SOCK: p['socket']=SOCK[kind]
    p['slots']={sn:{'at':v['at'],'filter':v.get('filter',[]),'required':v.get('required',False)} for sn,v in r['slots'].items()}
    props=I[pid]['_props']
    p['conflicts']=[c for c in props.get('ConflictingItems',[]) if c in CAT]
    if kind=='Magazine':
        try: p['capacity']=props['Cartridges'][0]['_max_count']
        except Exception: pass
    if r.get('fireport'): p['fireport']=r['fireport']
    # grip holds: the wrapped hold (a grip's own, " 1") and the cradled one (a forend's)
    H={h['name']:h for h in r['holds']}
    wrap=H.get('Base HumanLPalm 1') or H.get('Base HumanLPalm'); cradle=H.get('Base HumanLPalm') or H.get('Base HumanLPalm 1')
    if wrap: p['hold']={'palm':wrap['at'],'fingers':wrap['fingers']}
    if cradle and cradle is not wrap: p['cradle']={'palm':cradle['at'],'fingers':cradle['fingers']}
    # laser and light: the first visible laser's spot (its beam's way) and the first white light's
    W=[np.array(m) for m in r['world']]; nodes=r['nodes']
    def kids(i): return [j for j,n in enumerate(nodes) if n['parent']==i]
    def dev(i, beam):
        b=[j for j in kids(i) if nodes[j]['name'] in beam] or [i]
        M=un(W[b[0]]); return {'at':r6(un(W[i])[:3,3]),'dir':r6(M[:3,2])}
    for i,n in enumerate(nodes):
        if n['name'].startswith('VIS_') and 'laser' not in p: p['laser']=dev(i,('beam',))
        if re.match(r'^light(_\d+)?$',n['name']) and 'light' not in p and any(nodes[j]['name']=='Spotlight' for j in kids(i)): p['light']=dev(i,('Spotlight',))
    # sights
    reticles={}
    if kind in SIGHT and pid in S:
        sc=S[pid]['scopes']; marks=json.load(open('marks/%s.json'%pid)) if os.path.exists('marks/%s.json'%pid) else []
        for k,mk in enumerate(marks): mk['key']='r%d'%k; reticles[mk['key']]=mk['png']
        aims=sorted(r['aims'],key=lambda a:a['name'])
        p['aims']=[]
        for ai,a in enumerate(aims):
            s=sc[ai] if ai<len(sc) else {'zooms':[1],'modes':1}
            zooms=s['zooms']
            if len(aims)>1:
                big=max(zooms)>1
                cand=[m for m in marks if ('collimator' in m['mat'].lower())!=big] if any('collimator' in m['mat'].lower() for m in marks) else (marks if ai==0 else [])
            else: cand=marks
            modes=[]
            for mi,z in enumerate(zooms):
                mk=None
                if cand:
                    nums=[m for m in cand if num(m['mat']) is not None]
                    if nums:
                        mk=next((m for m in nums if num(m['mat'])==mi), None) or sorted(nums,key=lambda m:num(m['mat']))[mi%len(nums)]
                    elif re.search(r'hhs|g33',e['prefab']) and len(cand)>1:
                        mk=next((m for m in cand if ('g33' in m['mat'])==(z>1)),cand[0])
                    elif 'x' in ''.join(m['mat'] for m in cand) and len(cand)==len(zooms) and re.search(r'\d+x$',cand[0]['mat']):
                        mk=sorted(cand,key=lambda m:float(re.search(r'(\d+)x$',m['mat']).group(1).replace('225','2.25')))[mi]
                    else: mk=cand[mi%len(cand)] if len(cand)==len(zooms) else cand[0]
                mode={'zoom':float(z)}
                if mk:
                    mode['reticle']=mk['key']
                    if z<=1:
                        sc_=mk['floats'].get('_MarkScale') or 1.0
                        mode['moa']=MOA.get(pid, 152.4 if 'eotech' in e['prefab'] and 'xps' in e['prefab'] else round(122/sc_,1))
                        if kind in('AssaultScope','OpticScope') and not ('collimator' in mk['mat'].lower()): mode['moa']=600.0
                if not modes or modes[-1]!=mode: modes.append(mode)
            p['aims'].append({'at':a['at'],'modes':modes,'variable':bool(S[pid]['variable'] and len(modes)==2)})
    elif r['aims']:
        p['aims']=[{'at':a['at'],'modes':[{'zoom':1.0}]} for a in sorted(r['aims'],key=lambda a:a['name'])]
    r['reticles']=reticles; json.dump(r,open(f,'w'))
    p['model']='tkp_'+pid if r['meshes'] and kind!='Weapon' else None
    out[pid]=p
# the guns' own bases
json.dump(out,open('parts_game.json','w'),separators=(',',':'))
import collections
print(len(out), collections.Counter(p.get('socket','-') for p in out.values()))
print('aims',sum(1 for p in out.values() if p.get('aims')),'holds',sum(1 for p in out.values() if p.get('hold')),'lasers',sum('laser' in p for p in out.values()),'lights',sum('light' in p for p in out.values()))
print(os.path.getsize('parts_game.json')//1024,'KiB')
