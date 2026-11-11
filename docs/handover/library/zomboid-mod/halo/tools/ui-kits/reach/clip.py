import sys,os,json,time; sys.argv=['x']; sys.path.insert(0,'.')
import hudrender as h, numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
V='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/vid/'
tr=json.load(open(V+'track.json')); fs=sorted(os.listdir(V+'f30'))
F='/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/fonts/SairaSemiCondensed-Medium.ttfx.ttf'
f=ImageFont.truetype(F,24); fs_=ImageFont.truetype(F,18)
os.makedirs(V+'clip',exist_ok=True)
s_prev=0.0; cache={}
T=420
for i,(t,o,inn,dg) in enumerate(tr):
    if t<8.9:
        wn='sniper_rifle'
        if dg>=25: s=min(1.0,dg/68.0)
        else: s=max(0.0,s_prev-1.15/30)
    else:
        wn='magnum'; s=min(1.0,max(0.0,(o-22)/55.0)) if o>0 else s_prev
    s_prev=s
    key=(wn,round(s,3))
    if key not in cache:
        X=h.crosshair_layer(wn,spread=s); cx,cy=h.W/2,h.H/2
        cache[key]=X.crop((int(cx-120),int(cy-120),int(cx+120),int(cy+120)))
    ref=Image.open(V+'f30/'+fs[i]).convert('RGBA')
    bg=ref.filter(ImageFilter.GaussianBlur(14)); bg.alpha_composite(cache[key])
    fr=Image.new('RGB',(2*T+30,T+80),(16,18,22)); d=ImageDraw.Draw(fr)
    d.text((10,8),'Your game',font=f,fill=(230,230,230)); d.text((T+20,8),'Ours, driven by the same spread',font=f,fill=(230,230,230))
    fr.paste(ref.convert('RGB').resize((T,T),Image.LANCZOS),(10,45)); fr.paste(bg.convert('RGB').resize((T,T),Image.LANCZOS),(T+20,45))
    d.text((T+20,T+50),'%s   spread %.2f'%('Sniper' if wn=='sniper_rifle' else 'Magnum',s),font=fs_,fill=(150,200,255))
    fr.save(V+'clip/%04d.png'%i)
print('frames',len(tr),'unique renders',len(cache))
