"""Every sight's reticles (_MarkTex of its lens materials, premultiplied) and those materials' floats.
usage: marks_all.py OUTDIR id...  -> OUTDIR/<id>_<n>.png + OUTDIR/<id>.json [{mat, tex, png, floats, colors}]"""
import sys,json,os
sys.path.insert(0,'/tmp/claude-0/tk/b1'); sys.path.insert(0,'/tmp/claude-0/tk')
import kitexp as k, UnityPy
import numpy as np
from PIL import Image
CAT=json.load(open('/tmp/claude-0/tk/b2/catalog.json'))
od=sys.argv[1]; os.makedirs(od,exist_ok=True)
for pid in sys.argv[2:]:
    if os.path.exists(od+'/'+pid+'.json'): continue
    key=CAT[pid]['prefab']
    env=UnityPy.load(*[k.RAW+k.P+x.replace('/','__') for x in k.closure({key}) if 'audio' not in x])
    out=[]; seen=set()
    for o in env.objects:
        if o.type.name!='Material': continue
        m=o.read(); te=dict((n,v) for n,v in m.m_SavedProperties.m_TexEnvs)
        if '_MarkTex' not in te or not te['_MarkTex'].m_Texture.path_id: continue
        fl={n:v for n,v in m.m_SavedProperties.m_Floats}; co={n:[c.r,c.g,c.b,c.a] for n,c in m.m_SavedProperties.m_Colors}
        tx=te['_MarkTex']
        try: t=tx.m_Texture.read(); im=t.image; tn=t.m_Name
        except Exception as e: print('fail',pid,m.m_Name,e); continue
        if (m.m_Name,tn) in seen: continue
        seen.add((m.m_Name,tn))
        a=np.asarray(im.convert('RGBA')).astype(float); a[...,:3]*=a[...,3:4]/255
        png='%s_%d.png'%(pid,len(out)); Image.fromarray(a.astype(np.uint8),'RGBA').save(od+'/'+png)
        out.append({'mat':m.m_Name,'tex':tn,'png':png,'size':list(im.size),'floats':fl,'colors':co,
                    'scale':[tx.m_Scale.x,tx.m_Scale.y],'offset':[tx.m_Offset.x,tx.m_Offset.y]})
    json.dump(out,open(od+'/'+pid+'.json','w'),indent=0)
    print(pid,CAT[pid]['name'],[(x['mat'],x['tex'],x['floats'].get('_MarkScale')) for x in out],flush=True)
