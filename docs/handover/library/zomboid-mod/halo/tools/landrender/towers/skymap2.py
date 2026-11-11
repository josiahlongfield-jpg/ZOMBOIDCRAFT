import json,numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageFilter
S='/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad'
L='/tmp/claude-0/halo/web/land/'
D=np.load(S+'/skyD.npy'); N=1000
OFF = {"m10": (-5257, -4204), "m20": (-167, -5261), "m60": (4936, -5992), "m35": (-4299, 3102),"m52": (3983, 1544), "m50": (6225, -2533), "m70": (655, 5986)}
cs=json.load(open(L+'cs.json')); names=list(OFF)
yy,xx=np.mgrid[0:N,0:N]; wx=xx*16-8000+8; wz=yy*16-8000+8
foot=np.load(L+'out/foot.npy')[::4,::4]
for k,m in enumerate(names):
    cx,cz=cs[m][0]+OFF[m][0],cs[m][2]+OFF[m][1]
    Dc=np.hypot(wx-cx,wz-cz); Rk=np.percentile(Dc[D[k]<20],90) if (D[k]<20).any() else 500
    far=np.maximum(1950,Dc-Rk*0.8)
    D[k]=np.where(D[k]<1950,D[k],far)
D[-1]=np.maximum(0,D[-1]-300)*3.5
B=1400.0
w=np.exp(-(D-D.min(0))/ (B/3)); w/=w.sum(0)
REG=[('m10','Winter Contingency',(150,170,190),'Grey overcast and light snow. Early in the war: quiet skies, a few distant Phantoms.'),
     ('m20','Sword Base',(110,170,230),'Bright day over the bay. Covenant corvettes hang over the base, Longswords fly patrols.'),
     ('m60','The Package',(175,195,215),'Cold, pale dusk over the glacier. Covenant ships over the ice, snow in the wind.'),
     ('m35','Tip of the Spear',(220,170,90),'Hazy day full of battle smoke. UNSC frigates overhead, Longsword strikes.'),
     ('m52','New Alexandria',(210,90,60),'Night. The city burns, Covenant cruisers and the supercarrier above, Banshees and flak.'),
     ('m50','Exodus',(120,120,170),'Storm clouds and rain. Corvettes over the city, evacuation ships lifting off.'),
     ('m70','Pillar of Autumn',(230,140,60),'Dusty late day. Glassing beams on the horizon, a cruiser over the dock.'),
     ('lw','Lone Wolf',(200,70,40),'The end: orange glassed sky, beams close by, ash falling.')]
cols=np.array([r[2] for r in REG],float)
reg=(np.tensordot(w,cols,axes=(0,0)))
H=np.load(L+'out/height.npy')[::4,::4]; fl=np.load(L+'out/flags.npy')[::4,::4]
gy,gx=np.gradient(H); shade=np.clip(0.75+(-gx*0.6-gy*0.8)/16,0.35,1.15)
wet=((fl&4)>0)&(H<-14)
base=np.stack([shade*150]*3,-1)
img=base*0.45+reg*0.55*shade[...,None]
img[wet]=img[wet]*0.35+np.array([20,40,55])*0.65
# blend zone hint: where top weight < 0.8
top=w.max(0); edge=top<0.62
img[edge]=img[edge]*0.85+40
im=Image.fromarray(np.clip(img,0,255).astype(np.uint8)).resize((1200,1200),Image.BILINEAR)
d=ImageDraw.Draw(im)
Bf=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',22); Rf=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',15)
P=lambda x,z:((x+8000)/16000*1200,(z+8000)/16000*1200)
for k,(m,name,c,_) in enumerate(REG):
    if m=='lw': x,z=2300,6900
    else: x,z=cs[m][0]+OFF[m][0],cs[m][2]+OFF[m][1]
    px,py=P(x,z); d.text((px,py),name,fill=(255,255,255),font=Bf,anchor='mm',stroke_width=3,stroke_fill=(20,20,20))
ships=[]
for m in ['m20','m35','m50','m52','m70']:
    p=json.load(open(f'/tmp/claude-0/halo/layout/{m}/placements.json'))
    for e in p:
        k=e['model'].lower()
        if any(s in k for s in ['corvette','cruiser','frigate','civilian_transport','poa']) and 'holo' not in k and 'gantry' not in k and 'highlight' not in k:
            x,y,z=e['position_m']; ships.append((m,k,x+OFF[m][0],z+OFF[m][1]))
seen=set()
for m,k,x,z in ships:
    key=(round(x/30),round(z/30))
    if key in seen: continue
    seen.add(key); px,py=P(x,z)
    big='cruiser' in k or 'poa' in k
    col=(255,90,200) if ('corvette' in k or 'cruiser' in k) and 'halcyon' not in k else (120,220,255)
    r=9 if big else 6
    d.polygon([(px,py-r),(px+r,py),(px,py+r),(px-r,py)],fill=col,outline=(20,20,20))
# legend
d.rectangle([12,1200-112,470,1188],fill=(20,24,28))
d.polygon([(30,1200-90),(39,1200-81),(30,1200-72),(21,1200-81)],fill=(255,90,200)); d.text((48,1200-81),'Covenant ship (corvette, cruiser)',fill=(230,230,230),font=Rf,anchor='lm')
d.polygon([(30,1200-58),(39,1200-49),(30,1200-40),(21,1200-49)],fill=(120,220,255)); d.text((48,1200-49),'UNSC or civilian ship (frigate, transport, Autumn)',fill=(230,230,230),font=Rf,anchor='lm')
d.text((24,1200-22),'Lighter bands: where two skies blend (about 1.5 km)',fill=(230,230,230),font=Rf,anchor='lm')
im.convert('RGB').save(S+'/reachbuild/img/07-sky-regions.jpg',quality=88)
print(len(ships),len(seen))
json.dump([(r[1],r[3]) for r in REG],open(S+'/sky_regions.json','w'))
