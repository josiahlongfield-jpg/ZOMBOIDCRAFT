# retarget_character.py tests on stand-in rigs (no real models). Order: build.py, contact.py, test.py, test2.py
# run with bpy (pip bpy or blender -b --python FILE); stand-in files go in RT_DIR (default /tmp/rt)
import os
D = os.environ.get('RT_DIR', '/tmp/rt'); os.makedirs(D, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
import bpy, sys, math, importlib.util
from mathutils import Vector
spec = importlib.util.spec_from_file_location('rc', os.path.join(HERE, '..', 'retarget_character.py'))
rc = importlib.util.module_from_spec(spec); spec.loader.exec_module(rc)
res = []
def ok(name, cond, info=''):
    res.append(cond); print(('PASS ' if cond else 'FAIL ') + name, info)

def setup():
    bpy.ops.wm.open_mainfile(filepath=os.path.join(D, 'chief.blend'))
    src = rc.append_cortana(os.path.join(D, 'her.blend'), ['Idle Fidget Bashful|Flat'])
    tgt = bpy.data.objects['Chief_rig']
    return src, tgt, bpy.data.actions['Idle Fidget Bashful|Flat'], bpy.context.scene

def joint(o, n): return o.matrix_world @ o.pose.bones[n].head
def run(legs, hands):
    src, tgt, act, sc = setup()
    mp, _ = rc.guess_map(src, tgt)
    rt = rc.Retarget(src, tgt, mp, legs=legs, hands=hands)
    new, frames = rt.bake(act, f'Bashful|Chief', sc)
    # sample both
    S, T = {}, {}
    src.animation_data.action = act
    tgt.animation_data.action = new
    for f in frames:
        sc.frame_set(f)
        S[f] = {b.name: (joint(src, b.name), src.matrix_world @ src.pose.bones[b.name].matrix) for b in src.data.bones}
        T[f] = {b.name: (joint(tgt, b.name), tgt.matrix_world @ tgt.pose.bones[b.name].matrix) for b in tgt.data.bones}
    return rt, mp, frames, S, T, new

G = None
def sdir(rt, S, f, a, b): return (rt.G @ (S[f][b][0] - S[f][a][0])).normalized() if False else (rt.G.to_3x3() @ (S[f][b][0] - S[f][a][0])).normalized()
def tdir(T, f, a, b): return (T[f][b][0] - T[f][a][0]).normalized()
def qa(a, b):
    x = math.degrees(a.rotation_difference(b).angle); return min(x, 360 - x)
def ang(u, v): return math.degrees(u.angle(v))
M = 'mixamorig:'
pairs = [(('b_l_upperarm', 'b_l_forearm'), (M+'LeftArm', M+'LeftForeArm')), (('b_l_forearm', 'b_l_hand'), (M+'LeftForeArm', M+'LeftHand')),
         (('b_r_upperarm', 'b_r_forearm'), (M+'RightArm', M+'RightForeArm')), (('b_l_thigh', 'b_l_calf'), (M+'LeftUpLeg', M+'LeftLeg')),
         (('b_r_calf', 'b_r_foot'), (M+'RightLeg', M+'RightFoot')), (('b_l_index1', 'b_l_index2'), (M+'LeftHandIndex1', M+'LeftHandIndex2')),
         (('b_r_index2', 'b_r_index3'), (M+'RightHandIndex2', M+'RightHandIndex3')), (('b_neck0', 'b_head'), (M+'Neck', M+'Head'))]

# 1. pure rotation copy (legs fk, hands fk): limb directions agree every frame, incl. rest frame (A-pose copied onto T-pose)
rt, mp, frames, S, T, new = run('fk', 'fk')
print(f'k={rt.k:.3f} ka={rt.ka:.3f} yaw={math.degrees(rt.yaw):.1f}')
ok('leg scale = 1.2-ish', abs(rt.k - (54+55)/(45+45.04) ) < 0.05, f'{rt.k:.3f}')
ok('facing turned 180', abs(abs(math.degrees(rt.yaw)) - 180) < 0.5)
worst = 0
for f in frames:
    for (sa, sb), (ta, tb) in pairs:
        worst = max(worst, ang(sdir(rt, S, f, sa, sb), tdir(T, f, ta, tb)))
ok('limb directions match hers (fk)', worst < 0.5, f'worst {worst:.3f} deg')
# whole-bone world turn from frame 1 (twist included) for head, hands, pelvis
worst = 0
for sb, tb in (('b_head', M+'Head'), ('b_l_hand', M+'LeftHand'), ('b_pelvis', M+'Hips'), ('b_spine3', M+'Spine2'), ('b_r_hand', M+'RightHand')):
    for f in frames:
        ds = (S[f][sb][1].to_quaternion() @ S[1][sb][1].to_quaternion().inverted())
        dt = (T[f][tb][1].to_quaternion() @ T[1][tb][1].to_quaternion().inverted())
        ds = rt.G.to_quaternion() @ ds @ rt.G.to_quaternion().inverted()
        worst = max(worst, qa(ds, dt))
ok('head/hands/pelvis/chest turn like hers (incl. twist)', worst < 0.5, f'worst {worst:.3f} deg')
# pelvis moves scaled
dz_s = S[30]['b_pelvis'][0].z - S[1]['b_pelvis'][0].z; dz_t = T[30][M+'Hips'][0].z - T[1][M+'Hips'][0].z
ok('hips drop scaled by legs', abs(dz_t - rt.k * dz_s) < 1e-4, f'her {dz_s*100:.1f} cm, char {dz_t*100:.2f} cm')
# hand twist lines up at rest: index->pinky direction
li = (T[1][M+'LeftHandIndex1'][0] - T[1][M+'LeftHandPinky1'][0]); ls = rt.G.to_3x3() @ (S[1]['b_l_index1'][0] - S[1]['b_l_pinky1'][0])
ok('hand twist lined up', ang(li, ls) < 3, f'{ang(li, ls):.2f} deg')
fk_slide = max((T[f][M+'LeftFoot'][0] - T[1][M+'LeftFoot'][0]).length for f in frames)
print(f'  (fk legs: foot moves up to {fk_slide*100:.1f} cm)')

# 2. legs ik: feet stay put where hers are planted
rt, mp, frames, S, T, new = run('ik', 'fk')
hs = max((S[f]['b_l_foot'][0] - S[1]['b_l_foot'][0]).length for f in frames)
for side, n in (('L', M+'LeftFoot'), ('R', M+'RightFoot')):
    sl = max((T[f][n][0] - T[1][n][0]).length for f in frames)
    ok(f'{side} foot planted (ik)', sl < 0.002, f'moves {sl*1000:.2f} mm (hers {hs*1000:.2f} mm)')
    ok(f'{side} foot on the floor at rest height', abs(T[1][n][0].z - rt.tgt_rest_w[n].translation.z) < 1e-3)
worst = 0
for f in frames:
    for (sa, sb), (ta, tb) in pairs[:3] + pairs[5:]:
        worst = max(worst, ang(sdir(rt, S, f, sa, sb), tdir(T, f, ta, tb)))
ok('arms still copied exactly with leg ik', worst < 0.5, f'{worst:.3f}')
knee_ok = all((T[f][M+'LeftLeg'][0] - T[f][M+'LeftUpLeg'][0]).dot(Vector((0, 1, 0))) > 0 for f in frames)  # char faces +Y: knees go forward (+Y)
ok('knees bend forward', knee_ok)
fdir = max(qa(rt.G.to_quaternion() @ S[f]['b_l_foot'][1].to_quaternion() @ S[1]['b_l_foot'][1].to_quaternion().inverted() @ rt.G.to_quaternion().inverted(), T[f][M+'LeftFoot'][1].to_quaternion() @ T[1][M+'LeftFoot'][1].to_quaternion().inverted()) for f in frames)
ok('foot turn copied', fdir < 0.5, f'{fdir:.3f}')
rest = max(qa(T[1][n][1].to_quaternion(), rt.tgt_rest_w[n].to_quaternion()) for n in (M+'LeftFoot', M+'RightFoot', M+'Head'))
ok('feet flat and head level as the character stands at her rest frame', rest < 0.5, f'{rest:.3f}')
ok('action keeps fps', abs(new['fps'] - 90) < 1e-6 and new.frame_end == 60)

# 3. hands chest: hands meet where hers meet
rt, mp, frames, S, T, new = run('ik', 'chest')
hd_s = [(S[f]['b_l_hand'][0] - S[f]['b_r_hand'][0]).length for f in frames]
hd_t = [(T[f][M+'LeftHand'][0] - T[f][M+'RightHand'][0]).length for f in frames]
f30 = frames.index(30)
print(f'  hands apart at 30: her {hd_s[f30]*100:.1f} cm, char chest {hd_t[f30]*100:.1f} cm')
rt2, _, _, S2, T2, _ = run('ik', 'fk')
hd_fk = (T2[30][M+'LeftHand'][0] - T2[30][M+'RightHand'][0]).length
print(f'  char with fk arms {hd_fk*100:.1f} cm')
ok('chest hands keep the gap in proportion', abs(hd_t[f30] - rt.ka * hd_s[f30]) < 0.01, f'{hd_t[f30]*100:.1f} vs {rt.ka*hd_s[f30]*100:.1f}')
hrot = max(qa(rt.G.to_quaternion() @ S[f]['b_l_hand'][1].to_quaternion() @ S[1]['b_l_hand'][1].to_quaternion().inverted() @ rt.G.to_quaternion().inverted(), T[f][M+'LeftHand'][1].to_quaternion() @ T[1][M+'LeftHand'][1].to_quaternion().inverted()) for f in frames)
ok('hand turn still copied with chest hands', hrot < 0.5, f'{hrot:.3f}')
print('ALL PASS' if all(res) else f'{res.count(False)} FAILED')
# 4. auto hands on a move where her hands stay apart = same as fk
rt, mp, frames, S, T, new = run('ik', 'auto')
worst = max(ang(sdir(rt, S, f, sa, sb), tdir(T, f, ta, tb)) for f in frames for (sa, sb), (ta, tb) in pairs[:3])
ok('auto hands = plain copy when her hands are apart', worst < 0.5, f'{worst:.3f}')
print('ALL PASS' if all(res) else f'{res.count(False)} FAILED')
