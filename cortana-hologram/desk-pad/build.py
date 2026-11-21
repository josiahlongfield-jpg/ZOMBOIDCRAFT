import json,base64
P=json.load(open('out/parts.json'))
pr=[p for p in P if p['printed']]
g=sum(p['grams'] for p in pr); h=sum(max(0.5,p['grams']/18+0.4) for p in pr)
s=open('template.html').read()
s=s.replace('__PARTS__',json.dumps(P)).replace('__GLB__',base64.b64encode(open('out/pad.glb','rb').read()).decode())
s=s.replace('__NPRINT__',str(len(pr))).replace('__GRAMSG__',f'{g:,}').replace('__GRAMS__',f'{g/1000:.1f}').replace('__HOURS__',str(round(h)))
open('cortana-desk-pad.html','w').write(s); print(len(s), g, h)
