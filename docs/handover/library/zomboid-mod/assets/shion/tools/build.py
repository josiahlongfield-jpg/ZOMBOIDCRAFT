# Shion: her looks (posed like the Mixamo upload, from tgeo) skinned from the Mixamo body, + spec for pack.py
import json,base64,numpy as np,os
from scipy.spatial import cKDTree
S='/tmp/claude-0/-home-user-zomboidcraft/c36db822-1318-52b4-8c86-c6bf032f108a/scratchpad/shion/'
dec=lambda s,t: np.frombuffer(base64.b64decode(s),t)
enc=lambda a: base64.b64encode(np.ascontiguousarray(a).tobytes()).decode()
J=json.load(open('ex/mix.json')); s=1.0
B=J['bones']; nb=len(B); H=B.index('mixamorigHips')
def rigid(a):
    # Mixamo's bones carry the cm->m factor in their inverse binds: keep the turn, scale the offset to metres
    M=np.array(a).reshape(4,4).T; M[:3,3]*=s
    return M.T.reshape(-1).tolist()
J['inv']=[None if a is None else rigid(a) for a in J['inv']]
INPLACE={'Female_Start_Walking','Catwalk_Idle_To_Twist_R','Rifle_Down_To_Aim','Female_Stop_Walking','Rifle_Walk_To_Stop','Rifle_Aim_To_Down'}
for k,c in J['clips'].items():
    d=dec(c['data'],np.float32).reshape(c['frames'],nb,7).copy(); d[:,:,:3]*=s
    if k in INPLACE:
        dl=d[:,H,[0,2]]-d[0,H,[0,2]]; d[:,:,0]-=dl[:,0:1]; d[:,:,2]-=dl[:,1:2]
    c['data']=enc(d.astype(np.float32))
# reference skin: every Mixamo mesh
RP=[];RI=[];RW=[]
for m in J['meshes']:
    n=m['n']; RP.append(dec(m['pos'],np.float32).reshape(n,3)*s); RI.append(dec(m['si'],np.uint8).reshape(n,4)); RW.append(dec(m['sw'],np.float32).reshape(n,4))
RP=np.concatenate(RP).astype(np.float64); RI=np.concatenate(RI).astype(np.int64); RW=np.concatenate(RW).astype(np.float64)
tree=cKDTree(RP)
t=np.array(json.load(open('align.json'))['t'])
G=json.load(open(S+'tgeo.json')); blob=open(S+'tgeo.bin','rb').read(); TEX=json.load(open(S+'site/geo.json'))['tex']
keys=list(G['looks']); uses={}
for k in keys:
    for i in G['looks'][k]: uses.setdefault(i,[]).append(k)
PIECE={'Robotic_Horns':'horns','Hair_Default_LongStrands':'strands','Thigh_Harness':'harness','Thigh_Harness_Belt':'harness','Thigh_Harness_Weapon':'pistol',
       'Robotic_Neck_Collar':'collar','Pants_Strap':'straps','Painter_Earring':'earring','Painter_Belly_Piercings':'piercing','Shoes':'shoes','Shoes_Connector':'shoes',
       'Hair_Default':'hair'}
meshes=[];groups=[];textures={}
for i,m in enumerate(G['meshes']):
    if i not in uses: continue
    n=m['n']
    pos=np.frombuffer(blob,np.float32,n*3,m['pos']).reshape(n,3).astype(np.float64)+t
    nor=np.frombuffer(blob,np.int8,n*3,m['nor']).reshape(n,3).astype(np.float32)/127
    uv=np.frombuffer(blob,np.float32,n*2,m['uv']).reshape(n,2)
    ix=np.frombuffer(blob,np.uint32,m['t']*3,m['idx'])
    d,j=tree.query(pos,k=8); w=1/(d+0.004)**2; w/=w.sum(1,keepdims=True)
    W=np.zeros((n,nb))
    for k in range(8):
        np.add.at(W,(np.repeat(np.arange(n),4),RI[j[:,k]].ravel()),(RW[j[:,k]]*w[:,k:k+1]).ravel())
    si=np.argsort(-W,1)[:,:4]; sw=np.take_along_axis(W,si,1); sw/=np.maximum(sw.sum(1,keepdims=True),1e-9)
    name=f"{m['name'].split('#')[0].split('@')[0].lower()}_{i}"
    meshes.append(dict(name=name,n=n,pos=enc(pos.astype(np.float32)),normal=enc(nor),uv=enc(uv),si=enc(si.astype(np.uint8)),sw=enc(sw.astype(np.float32)),ix=enc(ix)))
    base=m['name'].split('#')[0].split('@')[0]
    piece=PIECE.get(base,'body')
    if len(uses[i])<len(keys): piece+='@'+','.join(uses[i])
    tk=m['mat']+(f"@{m['tile']}" if m['tile'] else '')
    f=TEX[tk]; alpha=f.endswith('.png')
    texkey=tk.replace('Shion_','').replace('@','_').lower()
    textures[texkey]=dict(file=S+'site/'+f,size=2048 if 'Nude' in tk or 'Default' in tk and 'Decal' not in tk or 'Hair' in tk or 'Cloth' in tk else 1024,alpha=alpha)
    small=n<1500
    groups.append(dict(mesh=name,piece=piece,texture=texkey,both=alpha,cutout=alpha,lods=[1.0,1.0 if small else 0.4]))
J['meshes']=meshes
json.dump(J,open('mix_shion.json','w'))
CL={'idle':'Breathing_Idle','walk':'Female_Walk','run':'Running','die':'Dying','aim':'Rifle_Aiming_Idle','fire':'Firing_Rifle_stationary',
    'rifle_walk':'Walk_With_Rifle','walk_start':'Female_Start_Walking','walk_stop':'Female_Stop_Walking','rifle_stop':'Rifle_Walk_To_Stop',
    'aim_up':'Rifle_Down_To_Aim','aim_down':'Rifle_Aim_To_Down','fire_walk':'Firing_Rifle_walking','rifle_idle':'Rifle_Idle',
    'fidget':'Catwalk_Idle_To_Twist_R','pose':'Female_Standing_Pose'}
json.dump(dict(groups=groups,textures=textures,clips=CL,looks=keys),open('shion_spec.json','w'),indent=1)
for g in groups: print(g['mesh'],g['piece'][:70],g['texture'])
