# baked colours (+ the source alpha for cut-out sheets) -> site/tex, and the geometry as base64 text halves
import json, os, base64
from PIL import Image
H=os.path.dirname(os.path.abspath(__file__)); T=f'{H}/in/textures'; O=f'{H}/site'
os.makedirs(f'{O}/tex',exist_ok=True)
ALPHA={'Shion_Hair_Default':(f'{T}/Shion_Hair_Default_Alpha.png','L'),'Shion_Default_Decal':(f'{T}/Shion_Default_Decal_Alpha.png','L'),
       'Shion_Default_Head_Decal':(f'{T}/Shion_Default_Decal_Alpha.png','L'),'Shion_Default_EyeInner':(f'{T}/Shion_EyeInner_Diffuse.png','A')}
SIZE={'Shion_Hair_Default':2048,'Shion_Body_Default':2048,'Shion_Cloth_Default':2048}
J=json.load(open(f'{H}/geo.json')); out={}
for mat in sorted(set(m['mat'] for m in J['meshes'])):
    if mat=='Shion_Body_Nude':
        for t in (1001,1002,1003):
            Image.open(f'{H}/tex/body_{t}.png').convert('RGB').resize((2048,2048),Image.LANCZOS).save(f'{O}/tex/body_{t}.jpg',quality=88)
            out[f'{mat}@{t}']=f'tex/body_{t}.jpg'
        continue
    src=f'{H}/tex/{mat}.png'
    RAW={'Shion_Bikini':'Shion_Bikini_Diffuse.png','Shion_Short_Pants':'Shion_Default_Pants_BaseColor.png','Shion_Short_Pants_Alt':'Shion_Default_Pants_BaseColor.png','Shion_Body_ToeNails':'Shion_ToeNails_Diffuse.png'}
    if not os.path.exists(src) and mat in RAW: src=f'{T}/{RAW[mat]}'
    if not os.path.exists(src): print('missing',mat); continue
    s=SIZE.get(mat,1024)
    im=Image.open(src).convert('RGB').resize((s,s),Image.LANCZOS)
    if mat in ALPHA:
        p,ch=ALPHA[mat]; a=Image.open(p); a=(a.getchannel('A') if ch=='A' else a.convert('L')).resize((s,s),Image.LANCZOS)
        im.putalpha(a); im.save(f'{O}/tex/{mat}.png',optimize=True); out[mat]=f'tex/{mat}.png'
    else:
        im.save(f'{O}/tex/{mat}.jpg',quality=88); out[mat]=f'tex/{mat}.jpg'
J['tex']=out
json.dump(J,open(f'{O}/geo.json','w'))
b=base64.b64encode(open(f'{H}/geo.bin','rb').read()).decode(); h=len(b)//2
open(f'{O}/geo_a.txt','w').write(b[:h]); open(f'{O}/geo_b.txt','w').write(b[h:])
print(out)
