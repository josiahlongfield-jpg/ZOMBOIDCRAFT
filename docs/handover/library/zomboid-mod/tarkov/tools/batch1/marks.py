"""The reticle (_MarkTex of the sight's lens material) of a scope bundle, premultiplied: python3 marks.py <bundle key> <out.png>"""
import sys,json,re
sys.path.insert(0,'/tmp/claude-0/tk/b1')
import kitexp as k, UnityPy
import numpy as np
from PIL import Image
key,out=sys.argv[1],sys.argv[2]
env=UnityPy.load(*[k.RAW+k.P+x.replace('/','__') for x in k.closure({key})])
found=[]
for o in env.objects:
    if o.type.name=='Material':
        m=o.read()
        for n,v in m.m_SavedProperties.m_TexEnvs:
            if n=='_MarkTex' and v.m_Texture.path_id:
                try: t=v.m_Texture.read(); found.append((m.m_Name,t.m_Name,t.image))
                except Exception as e: print('fail',m.m_Name,e)
for mn,tn,im in found: print(mn,tn,im.size,im.mode)
if found:
    mn,tn,im=found[0]; a=np.asarray(im.convert('RGBA')).astype(float)
    a[...,:3]*=a[...,3:4]/255; Image.fromarray(a.astype(np.uint8),'RGBA').save(out); print('saved',out)
