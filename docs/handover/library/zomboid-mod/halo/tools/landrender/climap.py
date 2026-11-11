import numpy as np,json
from PIL import Image,ImageDraw,ImageFilter,ImageFont
S=2048;sc=S/16000
def P(x,z): return ((x+8000)*sc,(z+8000)*sc)
yy,xx=np.mgrid[0:S,0:S];xn=xx/S*2-1;zn=yy/S*2-1
rng=np.random.default_rng(3)
noise=Image.fromarray((rng.random((64,64))*255).astype('u1')).resize((S,S),Image.BICUBIC);nz=np.array(noise)/255-0.5
t=zn-0.3*xn+0.18*nz
stops=[(-1.3,(236,241,246)),(-0.85,(222,229,236)),(-0.7,(150,160,150)),(-0.55,(104,128,84)),(0.0,(92,124,70)),(0.35,(120,128,78)),(0.55,(160,146,96)),(0.68,(196,170,118)),(1.4,(214,186,132))]
img=np.zeros((S,S,3))
for (a,ca),(b,cb) in zip(stops,stops[1:]):
    m=(t>=a)&(t<b);f=((t-a)/(b-a))[...,None]
    img[m]=(np.array(ca)*(1-f)+np.array(cb)*f)[m]
img[t>=stops[-1][0]]=stops[-1][1];img[t<stops[0][0]]=stops[0][1]
base=Image.fromarray(img.astype('u1'))
sea=Image.new('L',(S,S),0);d=ImageDraw.Draw(sea)
d.polygon([P(*p) for p in [(-2700,-8000),(8000,-8000),(8000,8000),(7300,8000),(6900,6200),(6200,4400),(5700,600),(5300,-1500),(4300,-3300),(2600,-3700),(1300,-3300),(-600,-3500),(-1900,-3900),(-2500,-5000)]],fill=255)
sea=sea.filter(ImageFilter.GaussianBlur(10))
base.paste(Image.new('RGB',(S,S),(46,84,104)),mask=sea)
off={"m10":(-5257,-4204),"m20":(-167,-5261),"m60":(4936,-5992),"m35":(-4299,3102),"m52":(3983,1544),"m50":(6225,-2533),"m70":(655,5986)}
cs=json.load(open('cs.json'))
mk=Image.new('L',(666,666),0);ImageDraw.Draw(mk).ellipse([60,60,606,606],fill=255);mk=mk.filter(ImageFilter.GaussianBlur(45))
for m,(ox,oz) in off.items():
    c=cs[m];im=Image.open(f'top_{m}.png').convert('RGB');a=np.array(im).astype(int)
    void=(np.abs(a-np.array([143,156,168])).sum(2)<30)  # clear colour = no ground
    mm=np.array(mk).copy();mm[void]=0
    x,z=P(c[0]+ox-2600,c[2]+oz-2600);base.paste(im,(int(x),int(z)),Image.fromarray(mm))
d=ImageDraw.Draw(base)
F=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',34);Fs=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',28)
def lab(x,z,s,f=F,fill=(255,255,255)):
    X,Z=P(x,z);w=d.textlength(s,font=f);d.rectangle([X-w/2-8,Z-22,X+w/2+8,Z+22],fill=(16,22,26));d.text((X-w/2,Z-19),s,font=f,fill=fill)
names={"m10":"Winter Contingency","m20":"Sword Base","m60":"The Package","m35":"Tip of the Spear","m52":"New Alexandria","m50":"Exodus","m70":"Pillar of Autumn"}
for m,(ox,oz) in off.items():
    c=cs[m];lab(c[0]+ox,c[2]+oz,names[m])
lw=(2300,6900);X,Z=P(*lw);d.regular_polygon((X,Z,26),5,fill=(255,200,60),outline=(20,20,20));lab(lw[0]+200,lw[1]+330,"Lone Wolf",Fs,(255,215,110))
for s,(x,z) in [("COLD GLACIER COAST",(3000,-7500)),("SEA",(7300,2000)),("TEMPERATE HILLS AND FARMLAND",(-1500,-200)),("DRY GRASS AND SCRUB",(-2400,5600)),("DESERT",(-1200,7400))]:
    lab(x,z,s,Fs,(220,235,240))
base.save('climate_map.png');base.convert('RGB').resize((1400,1400),Image.LANCZOS).save('/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/reachbuild/img/04-climate-map.jpg',quality=86)
