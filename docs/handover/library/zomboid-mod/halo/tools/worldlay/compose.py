"""Compose 16 km world preview from land patches (feathered) + site overlays."""
import json,numpy as np,sys
from PIL import Image,ImageFilter,ImageDraw
W='/tmp/claude-0/halo/worldlay'; O=f'{W}/ov'
HW=8192; PX=8.0; N=int(2*HW/PX)
def feather(m,R,F):
    im=Image.open(f'{O}/land_{m}.png'); a=np.asarray(im).astype(np.float32)
    n=a.shape[0]; yy,xx=np.mgrid[0:n,0:n]; c=n/2
    dist=np.hypot(xx-c,yy-c)*PX
    k=np.clip((R-dist)/F,0,1); k=k*k*(3-2*k)
    a[...,3]*=k
    return Image.fromarray(a.astype(np.uint8),'RGBA')
def compose(L,sites,out):
    can=Image.new('RGBA',(N,N),(14,34,48,255))
    for m,x,z,R,F in L:
        im=feather(m,R,F); info=json.load(open(f'{O}/land_{m}.json'))
        # land image centre = (cx,cz) in map space; place so that map play-centre lands at world (x,z)
        px=int((x+HW)/PX-im.width/2); pz=int((z+HW)/PX-im.height/2)
        can.alpha_composite(im,(max(px,0),max(pz,0)),(max(-px,0),max(-pz,0)))
    d=ImageDraw.Draw(can)
    for t,x,z in sites:
        u,v=(x+HW)/PX,(z+HW)/PX; d.ellipse((u-6,v-6,u+6,v+6),outline=(217,164,65,255),width=2); d.text((u+8,v-6),t,fill=(255,255,255,255))
    can.convert('RGB').save(out,quality=85)
if __name__=='__main__':
    C=json.load(open(sys.argv[1])); compose(C['land'],C['sites'],sys.argv[2])
