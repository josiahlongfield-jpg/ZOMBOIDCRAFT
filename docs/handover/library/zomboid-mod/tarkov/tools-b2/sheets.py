import json, textwrap
from PIL import Image, ImageDraw, ImageFont
L=json.load(open('loadouts.json'))
F=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',22)
NM={'pm':'PM','glock17':'Glock 17','mp5k':'MP5K','ak102':'AK-102','val':'AS VAL','svds':'SVDS','mdr762':'MDR 7.62','sv98':'SV-98','m870':'870','mp18':'MP-18'}
for g,d in L.items():
    W=Image.new('RGB',(1200,560*3),(42,45,51)); dr=ImageDraw.Draw(W)
    for i,k in enumerate(['kit','a','b']):
        im=Image.open(f'pv/{g}_{k}.png').convert('RGB').crop((0,20,1200,580)); W.paste(im,(0,560*i))
        ch=d[k][1]
        txt=NM[g]+' as it comes' if k=='kit' else NM[g]+' build '+k.upper()+': '+', '.join(f'{s.replace("_"," ")} {n}' for s,n in ch)
        for j,line in enumerate(textwrap.wrap(txt,95)): dr.text((16,560*i+10+26*j),line,fill=(235,235,235),font=F)
    W.save(f'pv/sheet_{g}.jpg',quality=85)
    W.resize((600,840)).save(f'pv/small_{g}.jpg',quality=80)
