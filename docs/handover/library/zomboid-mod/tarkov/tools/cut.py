import os,subprocess
from PIL import Image,ImageDraw,ImageFont
OUT='fr/final'; os.makedirs(OUT,exist_ok=True)
try: F=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',26); F2=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',20)
except: F=F2=ImageFont.load_default()
def lab(d,xy,t,f=None):
    f=f or F; x,y=xy; d.text((x+2,y+2),t,font=f,fill=(0,0,0)); d.text((x,y),t,font=f,fill=(235,235,235))
n=0
def put(im):
    global n; im.save(f'{OUT}/f{n:04d}.png'); n+=1
names=[('brita','Brita M4 (in the game now)'),('plain','Tarkov M4A1, plain'),('kit','Tarkov M4A1, fully kitted')]
for i in range(180):
    c=Image.new('RGB',(1280,720))
    for r,(v,t) in enumerate(names):
        c.paste(Image.open(f'fr/turn-{v}/f{i:04d}.png').crop((0,240,1280,480)),(0,240*r))
    d=ImageDraw.Draw(c)
    for r,(v,t) in enumerate(names): lab(d,(24,240*r+14),t)
    put(c)
t1=n
for v,t in [('plain','Tarkov M4A1 plain: Tarkov hands, shots, reload'),('kit','Tarkov M4A1 kitted: suppressed shots, reload')]:
    fs=sorted(os.listdir(f'fr/fp-{v}'))
    for f in fs:
        c=Image.open(f'fr/fp-{v}/{f}').convert('RGB'); d=ImageDraw.Draw(c); lab(d,(24,20),t); lab(d,(24,56),'Pilot preview, not in game. Hands are Tarkov placeholders.',F2); put(c)
print(n,t1)
