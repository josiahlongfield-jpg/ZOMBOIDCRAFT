import bpy, sys, os, json, hashlib, struct
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import *
r=open_blend()
P=r.pose.bones['Properties_Skin_Default']; A=r.pose.bones['Properties_Accessories']

def trim_upper_mask(cut=0.01):
    # the robotic upper-body mask also hides her forearms and hands (they sat under the robot hands);
    # keep it only where the torso suit pieces actually cover her skin
    o=bpy.data.objects['Shion_Nude_Body']; g=o.vertex_groups['Mask_Shion_Robotic_UpperBody']
    cov=[]
    for n in ('Robotic_Body','Robotic_Belly','Robotic_Breasts','Robotic_ChestPiece','Robotic_Neck'):
        c=bpy.data.objects['Shion_'+n]; M=np.array(c.matrix_world)
        v=np.array([x.co for x in c.data.vertices]); cov.append(v@M[:3,:3].T+M[:3,3])
    cov=np.concatenate(cov); M=np.array(o.matrix_world)
    body=np.array([x.co for x in o.data.vertices])@M[:3,:3].T+M[:3,3]
    drop=[]
    for i in range(0,len(body),512):
        d=np.sqrt(((body[i:i+512,None,:]-cov[None])**2).sum(-1)).min(1)
        drop+=[int(i+j) for j in np.nonzero(d>cut)[0]]
    ing=set(v.index for v in o.data.vertices if any(e.group==g.index and e.weight>0 for e in v.groups))
    drop=[i for i in drop if i in ing]
    g.remove(drop); print('upper mask kept',len(ing)-len(drop),'dropped',len(drop),flush=True)
trim_upper_mask()
D0={k:P[k] for k in P.keys() if isinstance(P[k],(int,float,bool))}
A0={k:A[k] for k in A.keys() if isinstance(A[k],(int,float,bool))}
ALLON=dict(Horns=True,Long_Hair=True,Thigh_Harness=True,Weapon=True,Neck_Collar=True,Belt_Buckle=True,Pants_Straps=True,Head_Leds=True,Hair=True)
HUMAN=dict(Thight_Robotic=False, Robotic_Knee=False)
NOLIMB=('Robotic_Hands','Robotic_LowerArm','Robotic_LowerArm_Jacket','Robotic_Legs_Feet','Robotic_Legs_Lower','Robotic_Legs_Lower_Knee','Robotic_Legs_Upper')  # Josiah: human arms, legs and feet in every look
LOOKS={
 'default':   dict(),
 'open':      dict(Suit_Jacket=1),
 'nojacket':  dict(Suit_Jacket=0),
 'shorts':    dict(Pants=0, acc=dict(Pants=1)),
 'gym':       dict(Suit_Jacket=0, Pants=0, acc=dict(SportsBra=2, Pants=2)),
 'bare':      dict(Suit_Jacket=0, Pants=0, Robotic_Upper_Body=False, Robotic_Belly=False, Thight_Robotic=False, Robotic_Knee=False, Robotic_Breasts_Cover=0, Shoes=False, Thigh_Harness=False, Weapon=False, Horns=False, Pants_Straps=False, Belt_Buckle=False, acc=dict(SportsBra=2, Pants=2)),
}
EXTRA_ACC=dict(Garterbelt=2, BellyPiercing=True, EarPiercing=True)
ATTACH=('Robotic_Horns','Hair_Default_LongStrands','Thigh_Harness','Thigh_Harness_Belt','Thigh_Harness_Weapon','Robotic_Neck_Collar','Pants_Strap','Painter_Earring','Painter_Belly_Piercings','Shoes','Shoes_Connector')
only=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else list(LOOKS)
out={'looks':{}, 'meshes':[]}
blob=bytearray(); seen={}
def put(arr):
    global blob
    b=arr.tobytes(); off=len(blob); blob+=b; blob+=b'\0'*((4-len(b)%4)%4); return off
for look0 in only:
  for shoes in (True,False):
      look=look0+('|1' if shoes else '|0')
      spec=dict(LOOKS[look0]); acc=spec.pop('acc',{}); spec['Shoes']=shoes
      for k,v in D0.items(): P[k]=v
      for k,v in A0.items(): A[k]=v
      for k,v in ALLON.items(): P[k]=v
      for k,v in HUMAN.items(): P[k]=v
      for k,v in spec.items(): P[k]=v
      for k,v in EXTRA_ACC.items(): A[k]=v
      for k,v in acc.items(): A[k]=v
      r.update_tag(); bpy.context.scene.frame_set(1); bpy.context.view_layer.update()
      dg=bpy.context.evaluated_depsgraph_get(); dg.update()
      names=[]; nat=[]
      for o in bpy.data.objects:
          if o.type!='MESH' or banned(o.name) or not o.parent or o.parent.name!='RIG-Shion': continue
          oe=o.evaluated_get(dg)
          base=o.name.replace('Shion_','')
          natural=not (oe.hide_render or o.hide_render)
          if not natural and base not in ATTACH: continue
          if base in NOLIMB: continue
          if not shoes and base in ('Shoes','Shoes_Connector'): continue
          me=oe.to_mesh()
          me.calc_loop_triangles()
          n=len(me.vertices)
          if n==0 or len(me.loop_triangles)==0: oe.to_mesh_clear(); continue
          M=np.array(o.matrix_world)
          co=np.empty(n*3,np.float32); me.vertices.foreach_get('co',co); co=co.reshape(-1,3)
          co=co@M[:3,:3].T+M[:3,3]
          nt=len(me.loop_triangles)
          tl=np.empty(nt*3,np.int32); me.loop_triangles.foreach_get('loops',tl)
          tv=np.empty(nt*3,np.int32); me.loop_triangles.foreach_get('vertices',tv)
          tm=np.empty(nt,np.int32); me.loop_triangles.foreach_get('material_index',tm)
          nl=len(me.loops)
          uvl=me.uv_layers.active
          uv=np.zeros(nl*2,np.float32)
          if uvl: uvl.data.foreach_get('uv',uv)
          uv=uv.reshape(-1,2)
          ln=np.empty(nl*3,np.float32); me.loops.foreach_get('normal',ln) if hasattr(me.loops[0],'normal') else None
          try: me.corner_normals.foreach_get('vector',ln)
          except Exception: pass
          ln=ln.reshape(-1,3)@M[:3,:3].T
          ln/=np.linalg.norm(ln,axis=1,keepdims=True)+1e-9
          # unique corners by (vertex, uv rounded)
          key=np.round(uv[tl]*65536).astype(np.int64)
          kk=np.stack([tv.astype(np.int64),key[:,0],key[:,1]],1)
          uk,inv=np.unique(kk,axis=0,return_inverse=True); inv=inv.reshape(-1)
          first=np.zeros(len(uk),np.int64); first[inv]=np.arange(len(tl))
          V=co[tv[first]]; N=ln[tl[first]]; U=uv[tl[first]]
          I=inv.reshape(-1,3).astype(np.uint32)
          mats=[m.name if m else '' for m in me.materials]
          oe.to_mesh_clear()
          for mi in np.unique(tm):
              sel=I[tm==mi]
              mat=mats[mi] if mi<len(mats) else ''
              tiles=[0]
              if mat=='Shion_Body_Nude':
                  tu=np.floor(U[sel].mean(axis=1)[:,0]).astype(int) if False else np.floor(U[sel][:,:,0].mean(axis=1)).astype(int)
                  tiles=sorted(set(tu.tolist()))
              for t in tiles:
                  s=sel if mat!='Shion_Body_Nude' else sel[np.floor(U[sel][:,:,0].mean(axis=1)).astype(int)==t]
                  used=np.unique(s); remap=np.full(len(V),-1,np.int64); remap[used]=np.arange(len(used))
                  v=V[used][:,[0,2,1]]*np.array([1,1,-1],np.float32)
                  nn=N[used][:,[0,2,1]]*np.array([1,1,-1],np.float32)
                  u=U[used].copy(); u[:,0]-=t
                  idx=remap[s].astype(np.uint32).reshape(-1)
                  h=hashlib.md5(v.tobytes()+idx.tobytes()+mat.encode()).hexdigest()
                  nm=o.name.replace('Shion_','')+('' if len(np.unique(tm))==1 else '#'+mat.replace('Shion_',''))+(f'@{1001+t}' if mat=='Shion_Body_Nude' else '')
                  if h not in seen:
                      seen[h]=len(out['meshes'])
                      out['meshes'].append(dict(name=nm,mat=mat,tile=1001+t if mat=='Shion_Body_Nude' else 0,n=len(used),t=len(idx)//3,
                          pos=put(v.astype(np.float32)),nor=put(np.round(nn*127).astype(np.int8)),uv=put(u.astype(np.float32)),idx=put(idx)))
                  names.append(seen[h])
                  if natural: nat.append(seen[h])
      out['looks'][look]=names; out.setdefault('natural',{})[look]=nat
      print('LOOK',look,len(names),[out['meshes'][i]['name'] for i in names],flush=True)
open(os.path.join(HERE,'geo.bin'),'wb').write(bytes(blob))
out['boots']={k:(LOOKS[k].get('Shoes',True)) for k in only}
json.dump(out,open(os.path.join(HERE,'geo.json'),'w'))
print('bytes',len(blob))
