import sys,os,json,re,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from viewglb import scene
from raster import render
from PIL import Image,ImageDraw
d=json.load(open('forge/pieces.json'))
def cat(t):
    s=t.split('\\')[-1]; p=t.lower()
    if '\\levels\\forge\\' not in p:
        if 'garbage' in p: return None
        if '\\weapons\\' in p or '\\vehicles\\' in p and 'destroyed' not in p: return 'vehicles-weapons'
        return 'props'
    for k,v in [('plat','platforms'),('bridge','bridges-walkways'),('walkway','bridges-walkways'),('ramp','ramps-stairs'),('stair','ramps-stairs'),('bank','ramps-stairs'),('incline','ramps-stairs'),
                ('wall','walls'),('window','walls'),('crenel','walls'),('cover','walls'),('railing','walls'),('door','doors'),
                ('bunker','buildings'),('tower','buildings'),('room','buildings'),('base','buildings'),('pyramid','buildings'),('dish','buildings'),('tunnel','buildings'),('cylinder','buildings'),
                ('brace','columns-braces'),('column','columns-braces'),('strut','columns-braces'),('antenna','columns-braces'),
                ('light','lights'),('rock','natural'),('tree','natural'),('cannon','gadgets'),('lift','gadgets'),('teleport','gadgets'),('grav','gadgets'),('shield','gadgets')]:
        if k in s: return v
    return 'other-forge'
rows=[]; groups={}
for x in d:
    if not x['model']: continue
    c=cat(x['tag'])
    if not c: continue
    f='forge/glb/'+x['model']
    try: V,T,UV,MM,tex=scene(f)
    except Exception as e: print('bad',f,e); continue
    ext=(V.max(0)-V.min(0))
    rows.append(dict(name=x['model'][:-4],category=c,tag=x['tag'],w_m=round(float(ext[0]),2),h_m=round(float(ext[1]),2),d_m=round(float(ext[2]),2),tris=int(len(T))))
    groups.setdefault(c,[]).append((f,rows[-1]))
json.dump(rows,open('forge/sizes.json','w'),indent=1)
os.makedirs('forge/sheets',exist_ok=True)
W,H=360,240
for c,L in groups.items():
    L.sort(key=lambda a:a[1]['name']); cols=5; n=len(L); rws=(n+cols-1)//cols
    out=Image.new('RGB',(W*cols,H*rws+40),(34,38,46)); dr=ImageDraw.Draw(out)
    dr.text((10,12),f'Reach Forge: {c} ({n} pieces). Sizes in blocks (1 block = 1 m), W x H x D',fill=(240,240,240))
    for i,(f,r_) in enumerate(L):
        V,T,UV,MM,tex=scene(f)
        cc=(V.min(0)+V.max(0))/2; ext=V.max(0)-V.min(0); rad=np.linalg.norm(ext)+1e-3
        e=np.array([0.8,0.55,1.0])
        im=render(V,T,None,eye=cc+e/np.linalg.norm(e)*rad*1.45,tgt=cc,W=W,H=H,UV=UV,tex=tex,M=MM,fov=30)
        x0,y0=(i%cols)*W,(i//cols)*H+40
        out.paste(im,(x0,y0))
        dr.text((x0+8,y0+6),r_['name'],fill=(255,255,255))
        dr.text((x0+8,y0+H-18),f"{r_['w_m']:.1f} x {r_['h_m']:.1f} x {r_['d_m']:.1f}",fill=(255,230,120))
    out.save(f'forge/sheets/forge_{c}.jpg',quality=88); print('sheet',c,n,flush=True)
