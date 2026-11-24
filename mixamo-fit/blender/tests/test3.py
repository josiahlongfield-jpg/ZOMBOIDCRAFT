# retarget_character.py exact mode: a character whose in-between bone is moved by a constraint (like Auto-Rig Pro),
# so plain maths places the hand wrong and the exact pass must fix it. Run after build.py.
import os
D = os.environ.get('RT_DIR', '/tmp/rt'); os.makedirs(D, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
import bpy, math, importlib.util, time
spec = importlib.util.spec_from_file_location('rc', os.path.join(HERE, '..', 'retarget_character.py'))
rc = importlib.util.module_from_spec(spec); spec.loader.exec_module(rc)
M = 'mixamorig:'
res = []
def ok(n, c, i=''):
    res.append(c); print(('PASS ' if c else 'FAIL ') + n, i)
def qa(a, b):
    x = math.degrees(a.rotation_difference(b).angle); return min(x, 360 - x)
out = {}
for exact in (False, True):
    bpy.ops.wm.open_mainfile(filepath=os.path.join(D, 'chief.blend'))
    src = rc.append_cortana(os.path.join(D, 'her.blend'), ['Idle Fidget Bashful|Flat'])
    tgt = bpy.data.objects['Chief_rig']; sc = bpy.context.scene
    for s in ('Left', 'Right'):
        c = tgt.pose.bones[f'{M}{s}ForeArmTwist'].constraints.new('COPY_ROTATION')
        c.target = tgt; c.subtarget = M + 'Hips'; c.influence = 0.5
    mp, _ = rc.guess_map(src, tgt)
    rt = rc.Retarget(src, tgt, mp, 'ik', 'fk')
    ok(f'needs exact detected', rt.needs_exact())
    t0 = time.time()
    new, fr = rt.bake(bpy.data.actions['Idle Fidget Bashful|Flat'], 'B|Chief', sc, exact=exact)
    dt = time.time() - t0
    src.animation_data.action = bpy.data.actions['Idle Fidget Bashful|Flat']; tgt.animation_data.action = new
    worst, slide = 0, 0
    G = rt.G.to_quaternion()
    for f in fr:
        sc.frame_set(f)
        for sb, tb in (('b_l_hand', M + 'LeftHand'), ('b_r_hand', M + 'RightHand'), ('b_head', M + 'Head'), ('b_l_foot', M + 'LeftFoot')):
            want = G @ (src.matrix_world @ src.pose.bones[sb].matrix).to_quaternion() @ rt.off[tb]
            worst = max(worst, qa(want, (tgt.matrix_world @ tgt.pose.bones[tb].matrix).to_quaternion()))
        p = tgt.matrix_world @ tgt.pose.bones[M + 'LeftFoot'].head
        if f == fr[0]: p0 = p.copy()
        slide = max(slide, (p - p0).length)
    out[exact] = worst
    print(f'exact={exact}: worst bone turn miss {worst:.3f} deg, foot moves {slide*1000:.2f} mm, residual {rt.worst:.4f}, {dt:.2f} s')
    if exact:
        ok('exact: hands land within 0.5 deg despite the constraint', worst < 0.5)
        ok('exact: planted foot stays put', slide < 0.002)
ok('plain maths alone misses (so the test means something)', out[False] > 5)
print('ALL PASS' if all(res) else f'{res.count(False)} FAILED')
