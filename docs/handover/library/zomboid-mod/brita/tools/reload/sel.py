import sys,json,numpy as np
from PIL import Image,ImageDraw
from comps import load,components
def gun_tris(g):
    hdr,png,tex,parts,rest=load(g); out=[]
    for name,rec in parts:
        pos=(np.stack([rec['x'],rec['y'],rec['z']],-1)/1000.0).reshape(-1,3,3)
        uv=(np.stack([rec['u'],rec['v']],-1)/65535.0).reshape(-1,3,2)
        lab=components(pos)
        out.append(dict(part=name,pos=pos,uv=uv,comp=lab,raw=rec))
    return out,tex,png
def select(parts,spec):
    """spec: list of rules {part, comps:[...], box:[x0,y0,z0,x1,y1,z1], not:bool}; returns list of bool masks per part"""
    masks=[np.zeros(len(p['pos']),bool) for p in parts]
    for r in spec:
        for i,p in enumerate(parts):
            if 'part' in r and r['part']!=p['part']: continue
            m=np.ones(len(p['pos']),bool)
            if 'comps' in r: m&=np.isin(p['comp'],r['comps'])
            if 'box' in r:
                b=np.array(r['box']); c=p['pos'].mean(1); m&=((c>=b[:3])&(c<=b[3:])).all(1)
            if r.get('not'): masks[i]&=~m
            else: masks[i]|=m
    return masks
def view(parts,groups,out,S=50):
    P=np.concatenate([p['pos'] for p in parts]); lo=P.reshape(-1,3).min(0); hi=P.reshape(-1,3).max(0)
    gid=[np.zeros(len(p['pos']),int) for p in parts]
    for k,ms in enumerate(groups):
        for i,m in enumerate(ms): gid[i][m]=k+1
    G=np.concatenate(gid)
    cols=[(110,110,110),(230,60,60),(60,200,60),(60,120,240),(240,200,40),(200,60,220),(40,220,220)]
    ims=[]
    for ax,(a,b) in {'side':(2,1),'top':(2,0)}.items():
        W=int((hi[a]-lo[a])*S)+20;H=int((hi[b]-lo[b])*S)+20
        im=Image.new('RGB',(W,H),(25,25,25));d=ImageDraw.Draw(im)
        dep=3-a-b; order=np.argsort(P[:,:,dep].mean(1)*(1 if ax=='side' else 1))
        for t in order:
            c=cols[G[t]%len(cols)]
            n=np.cross(P[t,1]-P[t,0],P[t,2]-P[t,0]); ln=np.linalg.norm(n)+1e-9; sh=0.55+0.45*abs(n[dep])/ln
            d.polygon([(10+(p[a]-lo[a])*S,H-10-(p[b]-lo[b])*S) for p in P[t]],fill=tuple(int(x*sh) for x in c))
        ims.append(im)
    W=max(i.width for i in ims); out_im=Image.new('RGB',(W,sum(i.height for i in ims)+10),(0,0,0)); y=0
    for i in ims: out_im.paste(i,(0,y)); y+=i.height+10
    out_im.save(out)
if __name__=='__main__':
    g=sys.argv[1]; spec=json.loads(sys.argv[2]); parts,_,_=gun_tris(g)
    view(parts,[select(parts,s) for s in spec],sys.argv[3] if len(sys.argv)>3 else f'sel_{g}.png')
