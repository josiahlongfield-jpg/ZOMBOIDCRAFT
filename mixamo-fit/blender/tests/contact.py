# retarget_character.py tests on stand-in rigs (no real models). Order: build.py, contact.py, test.py, test2.py
# run with bpy (pip bpy or blender -b --python FILE); stand-in files go in RT_DIR (default /tmp/rt)
import os
D = os.environ.get('RT_DIR', '/tmp/rt'); os.makedirs(D, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
# add "Hands Together|Flat" to her: hands meet in front of her belly at frame 20 (posed with temporary IK, then baked)
import bpy
from mathutils import Vector
bpy.ops.wm.open_mainfile(filepath=os.path.join(D, 'her.blend'))
if 'Hands Together|Flat' in bpy.data.actions: bpy.data.actions.remove(bpy.data.actions['Hands Together|Flat'])
if 'IK_meet' in bpy.data.objects['Armature'].data.bones: raise SystemExit('rebuild first')
her = bpy.data.objects['Armature']; sc = bpy.context.scene
bpy.context.view_layer.objects.active = her; bpy.ops.object.mode_set(mode='EDIT')
e = her.data.edit_bones.new('IK_meet'); e.head = (0, -0.22, 1.12); e.tail = (0, -0.3, 1.12); e.parent = her.data.edit_bones['b_pedestal']; e.use_deform = False
bpy.ops.object.mode_set(mode='OBJECT')
act = bpy.data.actions.new('Hands Together|Flat'); act.use_fake_user = True
her.animation_data.action = act
P = her.pose.bones
for pb in P: pb.rotation_quaternion = (1,0,0,0); pb.location = (0,0,0)
for pb in P: pb.keyframe_insert('rotation_quaternion', frame=1)
cs = []
for s, x in (('l', 0.015), ('r', -0.015)):
    c = P[f'b_{s}_hand'].constraints.new('IK'); c.target = her; c.subtarget = 'IK_meet'; c.chain_count = 3; cs.append((P[f'b_{s}_hand'], c))
sc.frame_set(1)
M = {n: P[n].matrix.copy() for n in ('b_l_upperarm','b_l_forearm','b_l_hand','b_r_upperarm','b_r_forearm','b_r_hand')}
for pb, c in cs: pb.constraints.remove(c)
for n in ('b_l_upperarm','b_l_forearm','b_l_hand','b_r_upperarm','b_r_forearm','b_r_hand'):
    P[n].matrix_basis = her.convert_space(pose_bone=P[n], matrix=M[n], from_space='POSE', to_space='LOCAL')
for n in M: P[n].keyframe_insert('rotation_quaternion', frame=20)
sc.frame_set(1); sc.frame_set(20)
d = (P['b_l_hand'].tail - P['b_r_hand'].tail).length
print('her hand tips apart at 20:', round(d*100, 2), 'cm')
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(D, 'her.blend'))
