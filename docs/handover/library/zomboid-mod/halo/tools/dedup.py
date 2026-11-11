"""Drop Firefight GLBs that duplicate an already-shipped campaign BSP (same name + same size); note in dup.json."""
import os,json,re
H='/tmp/claude-0/halo'
camp={}
for m in os.listdir(f'{H}/layout'):
    if not re.match(r'm\d',m): continue
    try: j=json.load(open(f'{H}/layout/{m}/manifest.json'))
    except Exception: continue
    for b in j['bsps']:
        if b.get('glb'): camp[b['short']]=(m,b.get('glb_mb'))
for m in sorted(os.listdir(f'{H}/outbox')):
    if not m.startswith('ff'): continue
    d=f'{H}/outbox/{m}'
    try: j=json.load(open(f'{H}/layout/{m}/manifest.json'))
    except Exception: continue
    dup={}
    for b in j['bsps']:
        s=b['short']
        if b.get('glb') and s in camp and camp[s][1]==b.get('glb_mb'):
            dup[b['glb']]=f'{camp[s][0]}/{b["glb"]}'
            for f in os.listdir(d):
                if f==b['glb'] or f.startswith(b['glb']+'.part'):
                    try: os.remove(f'{d}/{f}'); print('drop',m,f)
                    except FileNotFoundError: pass
    if dup:
        old={}
        p=f'{d}/dup.json'
        json.dump(dup,open(p,'w'),indent=1)
