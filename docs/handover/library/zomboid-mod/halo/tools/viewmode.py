import sys;sys.path.insert(0,'/tmp/claude-0/halo')
import numpy as np
from rres import RR
from rmodel import find_tag,model,triangles
from raster import render
r=RR(sys.argv[1]);nm=r.names();k,o=find_tag(r,'mode',sys.argv[2],nm);Mo=model(r,o,nm);P=triangles(Mo)
V=[];T=[];C=[];base=0
pal=np.array([(200,90,80),(90,160,220),(220,200,90),(120,200,120),(200,120,220),(230,150,60),(100,220,220),(180,180,180),(250,250,250),(150,100,60)]*4)
for p in P:
    v=p['vb']['p'];V.append(v);T.append(p['T']+base);C.append(pal[p['M']%len(pal)]);base+=len(v)
V=np.concatenate(V);T=np.concatenate(T);C=np.concatenate(C)
# halo: x fwd, y left, z up -> view y up
Vv=np.stack([V[:,0],V[:,2],-V[:,1]],1)
c=(Vv.min(0)+Vv.max(0))/2; rad=np.linalg.norm(Vv.max(0)-Vv.min(0))
imgs=[render(Vv,T,C,eye=c+np.array(e)*rad*1.3,tgt=c,W=640,H=420) for e in [(0.3,0.25,1.0),(-0.6,0.4,-0.8)]]
W=sum(i.width for i in imgs);from PIL import Image
out=Image.new('RGB',(W,420));xx=0
for i in imgs: out.paste(i,(xx,0));xx+=i.width
out.save(sys.argv[3])
