import sys,json,numpy as np
from PIL import Image, ImageFilter
S=sys.argv[1]; M=f'{S}/pzmods/108600'
c=json.load(open(f'{S}/pzout/chars.json'))
def mesh(g,kind):
    if kind=='van':
        b=c['female' if g=='f' else 'male']['parts']['body']; b=b[0] if isinstance(b,list) else b
    else: b=json.load(open(f'{S}/pzout/tomb_{g}.json'))
    return np.array(b['pos']).reshape(-1,3), np.array(b['uv']).reshape(-1,2)
def seams(P,U):
    key={}
    for p,u in zip(np.round(P,4),U): key.setdefault(tuple(p),set()).add((round(u[0],4),round(u[1],4)))
    pairs=[]
    for k,s in key.items():
        s=list(s)
        for i in range(1,len(s)):
            if max(abs(s[0][0]-s[i][0]),abs(s[0][1]-s[i][1]))>0.02: pairs.append((s[0],s[i]))
    return np.array(pairs)
texs={'f':{'vanilla':f'{S}/pzdata/media/textures/Body/FemaleBody01.png',
 'detailed':f'{M}/3578569664/mods/DetailedPlayerTextureFemaleB42/42/media/textures/Body/FemaleBody01.png',
 'evilsink':f'{M}/3408780732/mods/Evils Ink/common/media/textures/Body/FemaleBody01.png',
 'tomb':f"{M}/3429790870/mods/Tomb's Player Body - Textures/media/textures/Body/FemaleBody01.png",
 'goth':f"{M}/3531765630/mods/Tomb's Player Body - Goth Textures/media/textures/Body/FemaleBody01.png"},
 'm':{'vanilla':f'{S}/pzdata/media/textures/Body/MaleBody01.png',
 'tomb':f"{M}/3429790870/mods/Tomb's Player Body - Textures/media/textures/Body/MaleBody01.png",
 'goth':f"{M}/3531765630/mods/Tomb's Player Body - Goth Textures/media/textures/Body/MaleBody01.png"}}
for g in 'fm':
    sp={k:seams(*mesh(g,k)) for k in ('van','tomb')}
    print(g,{k:len(v) for k,v in sp.items()})
    for tn,tp in texs[g].items():
        im=np.asarray(Image.open(tp).convert('RGB').resize((256,256),Image.LANCZOS).filter(ImageFilter.GaussianBlur(1))).astype(float)
        row=[]
        for k,pr in sp.items():
            a=(np.clip(pr[:,0]*255,0,255)).astype(int); b=(np.clip(pr[:,1]*255,0,255)).astype(int)
            d=np.abs(im[a[:,1],a[:,0]]-im[b[:,1],b[:,0]]).mean()
            row.append(f'{k}={d:.1f}')
        print('  ',tn,Image.open(tp).size,' '.join(row))
