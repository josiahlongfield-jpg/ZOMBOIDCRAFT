"""Riflescopes whose reticle is a mesh (no _MarkTex): the mark meshes drawn into a lens picture (black on clear),
spanning the mesh's own reach. usage: marks_mesh.py OUTDIR id..."""
import sys,json,os,re
sys.path.insert(0,'/tmp/claude-0/tk/b1'); sys.path.insert(0,'/tmp/claude-0/tk')
import kitexp as k, UnityPy
import numpy as np
from PIL import Image, ImageDraw
CAT=json.load(open('/tmp/claude-0/tk/b2/catalog.json'))
od=sys.argv[1]
for pid in sys.argv[2:]:
    key=CAT[pid]['prefab']
    env=UnityPy.load(*[k.RAW+k.P+x.replace('/','__') for x in k.closure({key}) if 'audio' not in x])
    found=[]
    for o in env.objects:
        if o.type.name=='Mesh':
            m=o.read()
            nl=m.m_Name.lower()
            if (nl.endswith('mark') or '_mark_' in nl and not re.search(r'lod|glass',nl) or nl.endswith('_mark_black') or nl.endswith('_mark_red') or 'crosshair' in nl or 'reticle' in nl) and not re.search(r'lod\d|glass',nl):
                V=[];F=[]
                for l in m.export().splitlines():
                    p=l.split()
                    if not p: continue
                    if p[0]=='v': V.append([float(x) for x in p[1:4]])
                    if p[0]=='f': F.append([int(x.split('/')[0])-1 for x in p[1:]])
                found.append((m.m_Name,np.array(V),F))
    out=[]
    for nm,V,F in found:
        if len(V)==0: continue
        ext=V.max(0)-V.min(0); ax=np.argsort(ext)[::-1][:2]; ax=sorted(ax)
        c=np.zeros(3); X=V[:,ax[0]]-c[ax[0]]; Y=V[:,ax[1]]-c[ax[1]]
        rr=np.hypot(X,Y); R=min(rr.max(),2.5*np.percentile(rr,90))*1.1; N=2048
        im=Image.new('L',(N,N),0); d=ImageDraw.Draw(im)
        P=np.c_[(X/R*0.5+0.5)*N,(0.5-Y/R*0.5)*N]
        for f in F:
            if len(f)>=3: d.polygon([tuple(P[i]) for i in f],fill=255)
        a=np.zeros((N,N,4),np.uint8); a[...,3]=np.asarray(im); a[...,:3]=8
        a[...,:3]=(a[...,:3].astype(float)*a[...,3:4]/255).astype(np.uint8)
        png='%s_m%d.png'%(pid,len(out)); Image.fromarray(a,'RGBA').resize((1024,1024),Image.LANCZOS).save(od+'/'+png)
        out.append({'mat':nm,'tex':nm,'png':png,'mesh':True,'axes':[int(x) for x in ax],'R':float(R),'floats':{}})
    json.dump(out,open(od+'/'+pid+'.json','w'),indent=0)
    print(pid,CAT[pid]['name'],[(x['mat'],x['R']) for x in out],flush=True)
