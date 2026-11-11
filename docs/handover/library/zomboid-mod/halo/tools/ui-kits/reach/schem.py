# side-view line schematic of a Reach weapon model (stand-in for the HUD font glyph)
import sys,os,numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from rres import RR
from rmodel import find_tag,model,triangles
from PIL import Image,ImageDraw,ImageFilter,ImageChops
r=RR('/mnt/user-data/uploads/haloreach/maps/20_sword_slayer.map'); nm=r.names()
def schem(tag,out,W=600):
    k,o=find_tag(r,'mode',tag,nm); M=model(r,o,nm); P=triangles(M)
    tris=[]
    for p in P:
        V=p['vb']['p']; 
        for t in p['T']: tris.append(V[t])
    T=np.array(tris)  # (n,3,3) halo: x fwd, y left, z up
    xs=T[...,0]; zs=T[...,2]; ys=T[...,1]
    x0,x1=xs.min(),xs.max(); z0,z1=zs.min(),zs.max()
    s=(W-20)/(x1-x0); H=int((z1-z0)*s)+20
    sil=Image.new('L',(W,H),0); dep=np.full((H,W),-1e9)
    d=ImageDraw.Draw(sil)
    # facing normals for internal edges: draw tris with shade by normal
    sh=Image.new('L',(W,H),0); ds=ImageDraw.Draw(sh)
    order=np.argsort(T[...,1].mean(1))  # paint far (low y? ) first
    for i in order:
        t=T[i]; pts=[(10+(v[0]-x0)*s, H-10-(v[2]-z0)*s) for v in t]
        d.polygon(pts,fill=255)
        n=np.cross(t[1]-t[0],t[2]-t[0]); n/= (np.linalg.norm(n)+1e-9)
        ds.polygon(pts,fill=int(40+200*abs(n[2])))
    # outline from silhouette + shading edges
    e1=ImageChops.subtract(sil,sil.filter(ImageFilter.MinFilter(5)))
    e2=sh.filter(ImageFilter.FIND_EDGES).point(lambda v:255 if v>60 else 0)
    e2=ImageChops.multiply(e2,sil.filter(ImageFilter.MinFilter(3)))
    line=ImageChops.lighter(e1,e2.point(lambda v:v*0.55))
    rgba=Image.new('RGBA',(W,H),(255,255,255,0)); 
    a=ImageChops.lighter(line,sil.point(lambda v:int(v*0.12)))
    rgba.putalpha(a); rgba.save(out); print(out,rgba.size)
for tag,o in [('objects\\weapons\\rifle\\assault_rifle\\assault_rifle','ar'),('objects\\weapons\\pistol\\magnum\\magnum','magnum'),('objects\\weapons\\rifle\\dmr\\dmr','dmr')]:
    schem(tag,'out/schem_%s.png'%o)
