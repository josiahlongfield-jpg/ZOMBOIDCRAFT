import os
from PIL import Image,ImageDraw,ImageFont
OUT='fr/final2'; os.makedirs(OUT,exist_ok=True)
F=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',24); F2=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)
def lab(d,xy,t,f=F):
    x,y=xy; d.text((x+2,y+2),t,font=f,fill=(0,0,0)); d.text((x,y),t,font=f,fill=(235,235,235))
fs=sorted(os.listdir('fr/zt'))
for n,f in enumerate(fs):
    a=Image.open('fr/zt/'+f).convert('RGB').resize((960,540)); b=Image.open('fr/zo/'+f).convert('RGB').resize((960,540))
    c=Image.new('RGB',(1920,540)); c.paste(a,(0,0)); c.paste(b,(960,0)); d=ImageDraw.Draw(c)
    lab(d,(20,16),"Tarkov's own arms"); lab(d,(980,16),"Our survivor's arms, driven by the same Tarkov animation")
    lab(d,(20,500),'Kitted M4A1: 3 suppressed shots, mag out, mag in, charging handle. Preview only, not in game.',F2)
    c.save(f'{OUT}/f{n:04d}.png')
print(len(fs))
