import numpy as np, json, base64, io
from PIL import Image, ImageFilter
rng=np.random.default_rng(7)
def blur_wrap(a,r):
    h,w=a.shape[:2]
    big=np.tile(a,(3,3) if a.ndim==2 else (3,3,1))
    im=Image.fromarray(((big-big.min())/(np.ptp(big)+1e-9)*255).astype(np.uint8))
    im=im.filter(ImageFilter.GaussianBlur(r))
    b=np.asarray(im).astype(float)/255
    return b[h:2*h,w:2*w]
def noise(n,r): return blur_wrap(rng.random((n,n)),r)
def fbm(n,rs=(1,3,8,24),ws=(.15,.25,.3,.3)):
    a=sum(w*(noise(n,r)-.5)/max(1e-6,np.std(noise(n,r))) for r,w in zip(rs,ws))
    return a
def to_uri(arr,fmt='PNG',q=82):
    im=Image.fromarray(np.clip(arr,0,255).astype(np.uint8))
    b=io.BytesIO()
    if fmt=='JPEG': im.convert('RGB').save(b,'JPEG',quality=q)
    else: im.save(b,'PNG',optimize=True)
    return f"data:image/{fmt.lower()};base64,"+base64.b64encode(b.getvalue()).decode()
N=256
x=np.arange(N)
# --- canvas: olive cotton duck weave, dirt, wear
weave=(np.sin(x[None,:]*np.pi/2)*.5+.5)*.5+(np.sin(x[:,None]*np.pi/2+np.pi/2)*.5+.5)*.5
grain=rng.random((N,N))
f=fbm(N); dirt=noise(N,22); stains=noise(N,40)
base=np.array([66,68,44],float)
c=base[None,None,:]*(0.86+0.16*weave[...,None]+0.08*(grain[...,None]-.5)+0.10*f[...,None])
c*=1-0.28*np.clip((dirt-.55)*4,0,1)[...,None]
c+= np.array([18,12,0])*np.clip((stains-.6)*5,0,1)[...,None]
canvas=c
# --- metal: dark gunmetal, brushed, scratches, rust
m=np.array([58,62,64],float)[None,None,:]*np.ones((N,N,1))
brush=blur_wrap(rng.random((N,N))*np.ones((N,N)),0.5)
streak=np.repeat(rng.random((1,N)),N,0).T  # horizontal streaks
streak=blur_wrap(np.tile(rng.random((N,1)),(1,N)),0.8)
m*= (0.85+0.15*streak[...,None]+0.06*(rng.random((N,N,1))-.5)+0.08*fbm(N)[...,None])
for i in range(28):
    y0,x0=rng.integers(0,N,2);L=rng.integers(8,60);ang=rng.normal(0,.35)
    for t in range(L):
        yy=int(y0+np.sin(ang)*t)%N;xx=int(x0+np.cos(ang)*t)%N
        m[yy,xx]=m[yy,xx]*0.8+np.array([120,122,120])*0.2
rust=noise(N,10);rmask=np.clip((rust-.68)*6,0,1)*np.clip((noise(N,30)-.45)*4,0,1)
m=m*(1-rmask[...,None])+np.array([96,52,28])[None,None,:]*(0.7+0.5*rng.random((N,N,1)))*rmask[...,None]
metal=m
# --- wood counter: dark varnished planks
yy=np.arange(N)[:,None]*np.ones((1,N))
stretch=np.asarray(Image.fromarray((rng.random((N,16))*255).astype(np.uint8)).resize((N,N),Image.BICUBIC).filter(ImageFilter.GaussianBlur(1))).astype(float)/255
ring=np.sin(yy*0.9+stretch*14)*.5+.5
w=np.array([78,52,32],float)[None,None,:]*(0.75+0.25*ring[...,None]+0.1*(rng.random((N,N,1))-.5))
w[(np.arange(N)%64)<2]*=0.45
wood=w
# --- concrete floor, wet
cc=np.array([62,62,60],float)[None,None,:]*(0.8+0.25*fbm(N)[...,None]+0.12*(rng.random((N,N,1))-.5))
wet=np.clip((noise(N,26)-.5)*3,0,1)
cc=cc*(1-0.35*wet[...,None])
concrete=cc
# --- webbing strap (horizontal rib)
rib=(np.sin(np.arange(N)[:,None]*np.pi/1.5)*.5+.5)*np.ones((1,N))
wb=np.array([52,56,36],float)[None,None,:]*(0.8+0.25*rib[...,None]+0.1*(rng.random((N,N,1))-.5)+0.08*fbm(N)[...,None])
webbing=wb
# --- tape/paper
p=np.array([206,193,158],float)[None,None,:]*(0.92+0.06*fbm(N)[...,None]+0.05*(rng.random((N,N,1))-.5))
p*=1-0.18*np.clip((noise(N,18)-.6)*4,0,1)[...,None]
paper=p
out={k:to_uri(v,'JPEG',80) for k,v in dict(canvas=canvas,metal=metal,wood=wood,concrete=concrete,webbing=webbing,paper=paper).items()}
# backdrop
im=Image.open('/mnt/project-files/zomboid-mod/previews/city-street-latest.png').convert('RGB')
im=im.resize((1200,int(1200*im.height/im.width)))
b=io.BytesIO();im.save(b,'JPEG',quality=72);out['backdrop']="data:image/jpeg;base64,"+base64.b64encode(b.getvalue()).decode()
# models
D='/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/'
models={}
for n in ['assault_rifle','rifle_magazine','rifle_ammo','baseball_bat','crowbar','kitchen_knife','machete','hammer','fire_axe','frying_pan','katana','sledgehammer','pipe_wrench','spiked_bat_worn','golf_club']:
    mj=json.load(open(D+f'models/item/weapon/{n}.json'))
    els=[{k:e[k] for k in ('from','to','faces','rotation') if k in e} for e in mj['elements']]
    for e in els:
        for fc in e['faces'].values(): fc.pop('texture',None)
        e['from']=[round(v,4) for v in e['from']];e['to']=[round(v,4) for v in e['to']]
        for fc in e['faces'].values(): fc['uv']=[round(v,4) for v in fc['uv']]
    tex=base64.b64encode(open(D+f'textures/item/weapon/{n}.png','rb').read()).decode()
    models[n]={'elements':els,'gui':mj.get('display',{}).get('gui'),'tex':'data:image/png;base64,'+tex}
out['models']=models
json.dump(out,open('assets.json','w'),separators=(',',':'))
for k,v in out.items(): print(k,len(json.dumps(v))//1024,'KB')
for k in ['canvas','metal','wood','concrete','webbing','paper']:
    Image.open(io.BytesIO(base64.b64decode(out[k].split(',')[1]))).save(f'prev_{k}.png')
