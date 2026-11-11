import sys;sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
import numpy as np
from rres import RR
from reach import X
from rmodel import find_tag,model,triangles
from rshader import shader_bitmaps,shader_datums
from rbitmap import bitmap
from raster import render
from PIL import Image
r=RR(sys.argv[1]);nm=r.names();k,o=find_tag(r,'mode',sys.argv[2],nm);Mo=model(r,o,nm);P=triangles(Mo)
tex={};cache={}
for i,sd in enumerate(shader_datums(r,o)):
    bl=shader_bitmaps(r,sd,nm)
    if not bl or not bl[0]: continue
    b=bl[0]
    if b not in cache:
        kk,bo=find_tag(r,'bitm',b,nm)
        try: im,info=bitmap(r,bo); cache[b]=np.array(im.convert('RGBA').resize((min(im.width,512),min(im.height,512))))
        except Exception as e: print('tex fail',b,e); cache[b]=None
    tex[i]=cache[b]
V=[];T=[];UV=[];MM=[];base=0
for p in P:
    v=p['vb']['p'];V.append(v);T.append(p['T']+base);UV.append(p['vb']['uv']);MM.append(p['M']);base+=len(v)
V=np.concatenate(V);T=np.concatenate(T);UV=np.concatenate(UV);MM=np.concatenate(MM)
Vv=np.stack([V[:,0],V[:,2],-V[:,1]],1)
c=(Vv.min(0)+Vv.max(0))/2; rad=np.linalg.norm(Vv.max(0)-Vv.min(0))
views=[tuple(map(float,s.split(','))) for s in (sys.argv[4:] or ['0.3,0.25,1.0','-0.6,0.4,-0.8'])]
imgs=[render(Vv,T,None,eye=c+np.array(e)*rad*0.95,tgt=c,W=640,H=480,UV=UV,tex=tex,M=MM) for e in views]
out=Image.new('RGB',(640*len(imgs),480))
for j,i in enumerate(imgs): out.paste(i,(640*j,0))
out.save(sys.argv[3])
