# retarget_character.py tests on stand-in rigs (no real models). Order: build.py, contact.py, test.py, test2.py
# run with bpy (pip bpy or blender -b --python FILE); stand-in files go in RT_DIR (default /tmp/rt)
import os
D = os.environ.get('RT_DIR', '/tmp/rt'); os.makedirs(D, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
# stand-in rigs: "her" (Cortana bone names, A-pose, feet held by IK) and a character (Mixamo names, T-pose,
# longer legs, shorter arms, 3 spine bones, a twist bone, cm units at 0.01 scale, turned 180, euler bones)
import bpy, math, random
from mathutils import Vector, Matrix, Euler
random.seed(3)
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene

def rig(name, bones, scale=1.0, rot=0.0, loc=(0,0,0)):
    a = bpy.data.armatures.new(name); o = bpy.data.objects.new(name, a); sc.collection.objects.link(o)
    bpy.context.view_layer.objects.active = o; bpy.ops.object.mode_set(mode='EDIT')
    for n, h, t, p, deform in bones:
        e = a.edit_bones.new(n); e.head = Vector(h); e.tail = Vector(t); e.roll = random.uniform(-3, 3)
        if p: e.parent = a.edit_bones[p]
        e.use_deform = deform
    bpy.ops.object.mode_set(mode='OBJECT')
    o.scale = (scale,)*3; o.rotation_euler.z = rot; o.location = loc
    return o

def limbs(pre, side, s, sh, arm_dir, ua, fa, hl, hipx, hip, knee, ankle, footlen, names, spine_top, pelvis):
    x = 1 if side == 'l' else -1
    N = names(side)
    out = []
    c0 = Vector((0.02*x, 0, sh-0.02)); c1 = Vector((sh_w*x, 0, sh))
    d = Vector((arm_dir[0]*x, 0, arm_dir[1])).normalized()
    u1 = c1 + d*ua; f1 = u1 + d*fa; h1 = f1 + d*hl
    out += [(N['clav'], c0, c1, spine_top, True), (N['upperarm'], c1, u1, N['clav'], True)]
    out += [(N['forearm'], u1, f1, N['upperarm'], True)]
    hp = N['forearm']
    if N.get('twist'):
        out += [(N['twist'], u1 + d*fa*0.5, f1, N['forearm'], True)]; hp = N['twist']
    out += [(N['hand'], f1, h1, hp, True)]
    for fn, off in (('index', -0.02), ('middle', 0.0), ('pinky', 0.025)):
        p = N['hand']; q = h1 + Vector((0, off, 0))
        for i in range(3):
            q2 = q + d*0.03
            out.append((N[fn](i), q, q2, p, True)); p = N[fn](i); q = q2
        if N.get('end'): out.append((N[fn](3), q, q + d*0.02, p, False))
    p = N['hand']; q = f1 + Vector((0, -0.03, -0.01))
    for i in range(3):
        q2 = q + Vector((0.01*x, -0.025, -0.01))
        out.append((N['thumb'](i), q, q2, p, True)); p = N['thumb'](i); q = q2
    th = Vector((hipx*x, 0, hip)); kn = Vector((hipx*x, -0.02*hip, knee)); an = Vector((hipx*x, 0, ankle))
    to = an + Vector((0, -footlen, -ankle*0.7)); te = to + Vector((0, -0.06, 0))
    out += [(N['thigh'], th, kn, pelvis, True), (N['calf'], kn, an, N['thigh'], True), (N['foot'], an, to, N['calf'], True), (N['toe'], to, te, N['foot'], True)]
    return out

# --- her
sh_w = 0.18
def hn(side):
    s = f'b_{side}_'
    return dict(clav=s+'clav', upperarm=s+'upperarm', forearm=s+'forearm', hand=s+'hand', thigh=s+'thigh', calf=s+'calf', foot=s+'foot', toe=s+'toe',
                index=lambda i: f'{s}index{i+1}', middle=lambda i: f'{s}middle{i+1}', pinky=lambda i: f'{s}pinky{i+1}', thumb=lambda i: f'{s}thumb{i+1}')
B = [('b_pedestal', (0,0,0), (0,0.1,0), None, True), ('b_pelvis', (0,0,1.0), (0,0,1.1), 'b_pedestal', True)]
z = 1.1; prev = 'b_pelvis'
for i in range(4):
    B.append((f'b_spine{i}', (0,0,z), (0,0,z+0.1), prev, True)); prev = f'b_spine{i}'; z += 0.1
B += [('b_neck0', (0,0,1.5), (0,0,1.58), 'b_spine3', True), ('b_head', (0,0,1.58), (0,0,1.75), 'b_neck0', True), ('b_jaw', (0,-0.02,1.62), (0,-0.1,1.6), 'b_head', True)]
for s in 'lr':
    B += limbs('', s, 1, 1.47, (1, -1), 0.28, 0.26, 0.08, 0.09, 1.0, 0.55, 0.1, 0.12, hn, 'b_spine3', 'b_pelvis')
B += [('IK_foot_L', (0.09,0,0.1), (0.09,-0.1,0.1), 'b_pedestal', False), ('IK_foot_R', (-0.09,0,0.1), (-0.09,-0.1,0.1), 'b_pedestal', False)]
her = rig('Armature', B)
for s, S in (('l','L'),('r','R')):
    c = her.pose.bones[f'b_{s}_calf'].constraints.new('IK'); c.target = her; c.subtarget = f'IK_foot_{S}'; c.chain_count = 2
    c2 = her.pose.bones[f'b_{s}_foot'].constraints.new('COPY_ROTATION'); c2.target = her; c2.subtarget = f'IK_foot_{S}'
    # keep the foot flat: copy the IK bone's world rotation offset
    c2.mix_mode = 'BEFORE'; c2.target_space = c2.owner_space = 'LOCAL'
    c2.enabled = False

# her move: 1 = rest, 30 = knees bent / hands together in front / head turned, 60 = back
act = bpy.data.actions.new('Idle Fidget Bashful|Flat'); act.use_fake_user = True; act['fps'] = 90.0
her.animation_data_create(); her.animation_data.action = act
P = her.pose.bones
for pb in P: pb.rotation_mode = 'QUATERNION'
def key(f):
    for pb in P:
        pb.keyframe_insert('rotation_quaternion', frame=f); pb.keyframe_insert('location', frame=f)
key(1); key(60)
P['b_pelvis'].location = (0, 0.08, 0)  # bone y = world z for this bone (points up): drop is -y? set below in world
# set pelvis world drop properly
pel = her.data.bones['b_pelvis']
P['b_pelvis'].location = pel.matrix_local.to_3x3().inverted() @ Vector((0.03, 0, -0.08))
P['b_pelvis'].rotation_quaternion = (pel.matrix_local.to_3x3().inverted() @ Matrix.Rotation(0.12, 3, 'Z') @ pel.matrix_local.to_3x3()).to_quaternion()
def wrot(n, ang, ax):
    b = her.data.bones[n]; R = b.matrix_local.to_3x3()
    P[n].rotation_quaternion = (R.inverted() @ Matrix.Rotation(ang, 3, ax) @ R).to_quaternion()
wrot('b_spine1', 0.15, 'X'); wrot('b_spine3', -0.2, 'Y'); wrot('b_head', 0.6, 'Z')
wrot('b_l_upperarm', -0.9, 'Z'); wrot('b_r_upperarm', 0.9, 'Z')
wrot('b_l_forearm', -0.7, 'Z'); wrot('b_r_forearm', 0.7, 'Z')
wrot('b_l_hand', 0.4, 'Y'); wrot('b_r_index2', 0.8, 'X')
key(30)
sc.frame_start, sc.frame_end = 1, 60
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(D, 'her.blend'))

# --- the character (separate file), Mixamo names, cm at 0.01, T-pose, longer legs, shorter arms, wider shoulders
for o in list(bpy.data.objects): bpy.data.objects.remove(o)
for a in list(bpy.data.actions): bpy.data.actions.remove(a)
sh_w = 22.0
def mn(side):
    S = 'mixamorig:' + ('Left' if side == 'l' else 'Right')
    return dict(clav=S+'Shoulder', upperarm=S+'Arm', forearm=S+'ForeArm', twist=S+'ForeArmTwist', hand=S+'Hand', thigh=S+'UpLeg', calf=S+'Leg', foot=S+'Foot', toe=S+'ToeBase', end=True,
                index=lambda i: f'{S}HandIndex{i+1}', middle=lambda i: f'{S}HandMiddle{i+1}', pinky=lambda i: f'{S}HandPinky{i+1}', thumb=lambda i: f'{S}HandThumb{i+1}')
C = [('mixamorig:Hips', (0,0,120), (0,0,130), None, True)]
z = 130; prev = 'mixamorig:Hips'
for i, n in enumerate(['Spine', 'Spine1', 'Spine2']):
    C.append(('mixamorig:'+n, (0,0,z), (0,0,z+13), prev, True)); prev = 'mixamorig:'+n; z += 13
C += [('mixamorig:Neck', (0,0,169), (0,0,177), 'mixamorig:Spine2', True), ('mixamorig:Head', (0,0,177), (0,0,195), 'mixamorig:Neck', True), ('mixamorig:HeadTop_End', (0,0,195), (0,0,200), 'mixamorig:Head', False)]
for s in 'lr':
    C += limbs('', s, 100, 166, (1, 0), 25, 23, 7, 10, 120, 66, 11, 14, mn, 'mixamorig:Spine2', 'mixamorig:Hips')
ch = rig('Chief_rig', C, scale=0.01, rot=math.pi, loc=(2, 0, 0))
for pb in ch.pose.bones:
    if 'Arm' in pb.name: pb.rotation_mode = 'XYZ'
bpy.ops.mesh.primitive_cube_add(size=0.2); cube = bpy.context.object; cube.parent = ch
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(D, 'chief.blend'))
print('built')
