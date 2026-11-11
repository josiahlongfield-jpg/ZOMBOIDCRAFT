# The base-body look with arms straight out, as one clean FBX (textures embedded) for Mixamo.
import bpy, sys, os, json, math
from mathutils import Vector, Matrix
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import *
r=open_blend()
P=r.pose.bones['Properties_Skin_Default']; A=r.pose.bones['Properties_Accessories']
for k,v in dict(Suit_Jacket=0, Pants=0, Robotic_Upper_Body=False, Robotic_Belly=False, Thight_Robotic=False, Robotic_Knee=False, Robotic_Breasts_Cover=0, Shoes=False, Thigh_Harness=False, Weapon=False, Horns=False, Pants_Straps=False, Belt_Buckle=False).items(): P[k]=v
for k,v in dict(SportsBra=2, Pants=2).items(): A[k]=v
pr=r.pose.bones['Properties']
for s in ('left','right'): pr[f'ik_{s}_arm']=0.0
def upd():
    r.update_tag(); bpy.context.view_layer.update()
upd()
def aim(name, target):
    pb=r.pose.bones[name]; mw=r.matrix_world
    h=mw@pb.head; t=mw@pb.tail; d=(t-h).normalized()
    q=d.rotation_difference(target)
    M=Matrix.Translation(h)@q.to_matrix().to_4x4()@Matrix.Translation(-h)
    pb.matrix=mw.inverted()@M@mw@pb.matrix
    upd()
for side,sx in (('L',1),('R',-1)):
    t=Vector((sx,0,0))
    for b in ('FK-UpperArm','FK-Forearm','FK-Hand'):
        n=f'{b}.{side}'
        if n in r.pose.bones: aim(n,t)
    print(side,'hand at',(r.matrix_world@r.pose.bones[f'DEF-Hand.{side}'].head)[:] if f'DEF-Hand.{side}' in r.pose.bones else '?')
dg=bpy.context.evaluated_depsgraph_get()
site=os.path.join(HERE,'site'); J=json.load(open(os.path.join(site,'geo.json')))
new=[]
for o in list(bpy.data.objects):
    if o.type!='MESH' or banned(o.name) or not o.parent or o.parent.name!='RIG-Shion': continue
    oe=o.evaluated_get(dg)
    if oe.hide_render or o.hide_render: continue
    # Mixamo gets the least: body, sports bra and shorts, eyes, teeth, nails (hair and the rest get her skin weights afterwards)
    if not any(k in o.name for k in ('Nude_Body','SportsBra','Short_Pants','Default_Eye','Teeth','Tongue','Nails')): continue
    me=bpy.data.meshes.new_from_object(oe, preserve_all_data_layers=False, depsgraph=dg)
    me.transform(o.matrix_world)
    for ca in list(me.color_attributes): me.color_attributes.remove(ca)
    ob=bpy.data.objects.new(o.name.replace('Shion_','').replace('Nude_','').lower(), me)
    bpy.context.scene.collection.objects.link(ob); new.append(ob)
    for i,m in enumerate(me.materials):
        if not m: continue
        key=m.name if m.name!='Shion_Body_Nude' else None
        nm=bpy.data.materials.new('mx_'+m.name.replace('Body_Nude','Skin')); nm.use_nodes=True
        bsdf=nm.node_tree.nodes.get('Principled BSDF')
        f=J['tex'].get(m.name) or (J['tex'].get('Shion_Body_Nude@1001') if m.name=='Shion_Body_Nude' else None)
        if f:
            tn=nm.node_tree.nodes.new('ShaderNodeTexImage'); tn.image=bpy.data.images.load(os.path.join(site,f))
            nm.node_tree.links.new(tn.outputs['Color'],bsdf.inputs['Base Color'])
        me.materials[i]=nm
    # body: Mixamo reads one texture per material; fold the three tiles into u 0-1 per tile material
    if any(m and m.name.startswith('mx_Shion_Skin') for m in me.materials):
        import bmesh
        bm=bmesh.new(); bm.from_mesh(me); uvl=bm.loops.layers.uv.active
        base=me.materials[0]
        tilemats={}
        for t in (1001,1002,1003):
            nm=bpy.data.materials.new(f'mx_body_{t}'); nm.use_nodes=True
            tn=nm.node_tree.nodes.new('ShaderNodeTexImage'); tn.image=bpy.data.images.load(os.path.join(site,J['tex'][f'Shion_Body_Nude@{t}']))
            nm.node_tree.links.new(tn.outputs['Color'],nm.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
            me.materials.append(nm); tilemats[t]=len(me.materials)-1
        for f in bm.faces:
            if me.materials[f.material_index].name.startswith('mx_Shion_Skin'):
                u=sum(l[uvl].uv.x for l in f.loops)/len(f.loops); t=1001+int(math.floor(u))
                for l in f.loops: l[uvl].uv.x-=math.floor(u)
                f.material_index=tilemats.get(t,tilemats[1001])
        bm.to_mesh(me); bm.free()
for o in bpy.data.objects:
    o.select_set(o in new)
zmin=min(min((v.co.z for v in o.data.vertices),default=0) for o in new)
for o in new: o.location.z-=zmin
print('objects',[o.name for o in new],'lifted',-zmin)
out=os.path.join(HERE,'mixamo'); os.makedirs(out,exist_ok=True)
bpy.ops.export_scene.fbx(filepath=os.path.join(out,'shion_tpose.fbx'),use_selection=True,object_types={'MESH'},path_mode='COPY',embed_textures=True,
    apply_unit_scale=True,bake_space_transform=False,add_leaf_bones=False,mesh_smooth_type='FACE')
print('done')
