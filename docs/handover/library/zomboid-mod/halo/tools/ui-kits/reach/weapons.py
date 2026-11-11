import sys,os,json,struct
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from chdt import C
c=C('/mnt/user-data/uploads/haloreach/maps/20_sword_slayer.map')
out={}
for (g,n),k in sorted(c.byname.items(), key=lambda x:x[0][1]):
    if g!='weap': continue
    o=c.tago(k)
    lv=c.f(o+0x2ca,'h')[0]; rng=c.f(o+0x2cc,'2f'); hud=c.ref(o+0x3c4); alt=c.ref(o+0x3d4)
    scope=c.f(o+0x490,'2f'); zt=c.f(o+0x4a0,'f')[0]
    out[n]=dict(zoom_levels=lv,magnification=[round(x,3) for x in rng],hud=hud[1] if hud else None,alt_hud=alt[1] if alt else None,fp_scope_size=[round(x,3) for x in scope],zoom_time=round(zt,3))
json.dump(out,open('out/weapons_zoom.json','w'),indent=1)
for n,v in out.items():
    if 'weapons' in n: print(n.split('\\')[-1],v)
