# Tarkov operator HUD mock (purge direction): near-empty; real Tarkov sprites, Rajdhani standing in for Bender.
import os,sys,glob
from PIL import Image,ImageDraw,ImageFont,ImageFilter
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import body as B
P='/mnt/user-data/uploads/additions/tarkov-ui/png/'
FD='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/fonts/'
Z=float(os.environ.get('HUDZ','1')); W,H=int(1280*Z),int(720*Z)
def sp(n): return Image.open(glob.glob(P+'S_%s__*'%n)[0]).convert('RGBA')
def F(sz,b=True): return ImageFont.truetype(FD+('Rajdhani-SemiBold.ttf' if b else 'Rajdhani-Medium.ttf'),int(sz*Z))
def up(im,k): return im.resize((max(1,int(im.width*k)),max(1,int(im.height*k))),Image.LANCZOS)
def shadow_text(cv,xy,t,f,fill,anchor='la',sh=(0,0,0,170)):
    L=Image.new('RGBA',cv.size,(0,0,0,0)); d=ImageDraw.Draw(L)
    d.text((xy[0]+1*Z,xy[1]+1*Z),t,font=f,fill=sh,anchor=anchor); L=L.filter(ImageFilter.GaussianBlur(1.2*Z))
    cv.alpha_composite(L); ImageDraw.Draw(cv).text(xy,t,font=f,fill=fill,anchor=anchor)
def stamina(cv,st=0.62,arm=0.85):
    # two thin bars bottom centre, only while draining
    d=ImageDraw.Draw(cv); cx=W//2
    for i,(v,w_,h_) in enumerate(((st,250,4),(arm,170,3))):
        y=int((676+i*9)*Z); w2=int(w_*Z/2)
        d.rectangle((cx-w2,y,cx+w2,y+int(h_*Z)),fill=(0,0,0,110))
        d.rectangle((cx-w2,y,cx-w2+int(2*w2*v),y+int(h_*Z)),fill=(205,205,195,215))
def status(cv,names=('light_bleeding','fracture','pain')):
    x=int(18*Z); y=int(678*Z)
    for n in names:
        im=up(sp('effect_'+n),1.25*Z); cv.alpha_composite(im,(x,y)); x+=im.width+int(5*Z)
def gunline(cv,name='M4A1',mode='Full auto',mag=24,carried=180):
    f1,f2,f3=F(20),F(15,False),F(26)
    xr=W-int(22*Z); y=int(632*Z)
    shadow_text(cv,(xr,y),name,f1,(222,222,212,235),anchor='ra')
    shadow_text(cv,(xr,y+int(22*Z)),mode,f2,(170,170,160,225),anchor='ra')
    shadow_text(cv,(xr,y+int(40*Z)),'%d / %d'%(mag,carried),f3,(222,222,212,235),anchor='ra')
PARTS=[('head','HEAD',35,35),('chest','THORAX',62,85),('stomach','STOMACH',70,70),('leftarm','LEFT ARM',60,60),('rightarm','RIGHT ARM',22,60),('leftleg','LEFT LEG',0,65),('rightleg','RIGHT LEG',65,65)]
def col(v,m):
    if v<=0: return (40,40,40)
    r=v/m
    return (150,190,150) if r>0.75 else (205,175,80) if r>0.4 else (200,80,60)
def tab(cv):
    k=0.62*Z; bd=B.body({n:col(v,m) for n,_,v,m in PARTS}); bd=up(bd,k)
    x0,y0=int(110*Z),int(110*Z)
    shade=Image.new('RGBA',cv.size,(0,0,0,0)); ImageDraw.Draw(shade).rectangle((x0-int(60*Z),y0-int(50*Z),x0+bd.width+int(60*Z),y0+bd.height+int(40*Z)),fill=(8,10,10,170)); cv.alpha_composite(shade)
    cv.alpha_composite(bd,(x0,y0))
    d=ImageDraw.Draw(cv); fh,fv=F(11),F(12,False)
    bb=sp('health_bar_border'); fill=sp('health_bar_fill')
    for n,lab,v,m in PARTS:
        bx,by=B.LAY[n]; im=B.part(n); cx=x0+int((bx+im.width/2)*k); cy=y0+int((by+im.height/2)*k)
        cy=max(cy,y0+int(12*Z))
        d.text((cx,cy-int(9*Z)),lab,font=fh,fill=(200,200,190,230),anchor='mm')
        bw=up(bb,0.6*Z); cv.alpha_composite(bw,(cx-bw.width//2,cy))
        fw=int((bw.width-4*Z)*max(0,v)/m)
        if fw>0: d.rectangle((cx-bw.width//2+int(2*Z),cy+int(2*Z),cx-bw.width//2+int(2*Z)+fw,cy+bw.height-int(2*Z)),fill=col(v,m)+(230,))
        d.text((cx,cy+bw.height+int(6*Z)),'%d/%d'%(v,m),font=fv,fill=(200,200,190,230),anchor='mm')
        if v<=0: cv.alpha_composite(up(sp('body_part_broken_cross'),1.6*Z),(cx+bw.width//2+int(3*Z),cy))
    tot=sum(v for *_,v,m in PARTS); tm=sum(m for *_,v,m in PARTS)
    d.text((x0+bd.width//2,y0+bd.height+int(18*Z)),'%d/%d'%(tot,tm),font=F(22),fill=(222,222,212,240),anchor='mm')
def render(out,tabview=False,bg=None):
    cv=Image.new('RGBA',(W,H),(0,0,0,0)) if bg is None else bg.convert('RGBA').resize((W,H))
    stamina(cv); status(cv); gunline(cv)
    if tabview: tab(cv)
    cv.save(out); return cv
if __name__=='__main__':
    bg=Image.open(sys.argv[1]) if len(sys.argv)>1 and os.path.exists(sys.argv[1]) else None
    render(sys.argv[2] if len(sys.argv)>2 else 'op_hud.png',bg=bg)
    render(sys.argv[3] if len(sys.argv)>3 else 'op_hud_tab.png',tabview=True,bg=bg)
