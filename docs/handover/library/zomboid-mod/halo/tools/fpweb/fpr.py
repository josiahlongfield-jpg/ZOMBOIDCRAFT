import sys,os,numpy as np;sys.path.insert(0,'/tmp/claude-0/halo')
from viewglb import load,scene
from raster import render
from PIL import Image,ImageDraw
D='/mnt/user-data/uploads/halo-reach/animations/first-person/'
def clips(path):
    j,acc,img=load(path); return j,acc
def sample(j,acc,a,t):
    out={}
    for c in a['channels']:
        s=a['samplers'][c['sampler']]; inp=acc(s['input'])[:,0]; v=acc(s['output']).astype(np.float64)
        if j['accessors'][s['output']]['componentType']==5122: v=np.maximum(v/32767.0,-1)
        n=j['nodes'][c['target']['node']]['name']
        if t<=inp[0]: x=v[0]
        elif t>=inp[-1]: x=v[len(inp)-1]
        else:
            k=np.searchsorted(inp,t)-1; u=(t-inp[k])/(inp[k+1]-inp[k]); a0,b0=v[k],v[k+1]
            if c['target']['path']=='rotation' and np.dot(a0,b0)<0: b0=-b0
            x=a0*(1-u)+b0*u
            if c['target']['path']=='rotation': x=x/np.linalg.norm(x)
        e=out.setdefault(n,{}); e[c['target']['path']]=np.array(x,float)
    return out
def absq_for(j,pose):
    r={}
    for n in j['nodes']:
        nm=n.get('name')
        if nm in pose:
            q=pose[nm].get('rotation',np.array(n.get('rotation',[0,0,0,1])))
            t=pose[nm].get('translation',np.array(n.get('translation',[0,0,0])))
            r[nm]=(q,t)
    return r
def frame(path,pose_abs,arms='arms__default',flair=None,W=960,H=540,fov=55,label=None):
    keep=lambda nm: (nm==arms) or (flair and nm==flair) or not (nm.startswith('arms__') or nm.startswith('flair_forearm__'))
    V,T,UV,MM,tex=scene(path,select=keep,absq=pose_abs)
    im=render(V,T,None,eye=np.array([0,0,0.0]),tgt=np.array([0,0,1.0]),W=W,H=H,UV=UV,tex=tex,M=MM,fov=fov)
    if label: ImageDraw.Draw(im).text((14,10),label,fill=(235,235,235))
    return im
if __name__=='__main__':
    p=D+'spartan_fp_assault_rifle.glb'; j,acc=clips(p)
    a=[a for a in j['animations'] if a['name']=='first_person:idle'][0]
    frame(p,absq_for(j,sample(j,acc,a,0))).save('/tmp/claude-0/fp/t.png')
