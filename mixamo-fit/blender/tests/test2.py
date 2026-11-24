# retarget_character.py tests on stand-in rigs (no real models). Order: build.py, contact.py, test.py, test2.py
# run with bpy (pip bpy or blender -b --python FILE); stand-in files go in RT_DIR (default /tmp/rt)
import os
D = os.environ.get('RT_DIR', '/tmp/rt'); os.makedirs(D, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
import bpy, importlib.util
spec = importlib.util.spec_from_file_location('rc', os.path.join(HERE, '..', 'retarget_character.py'))
rc = importlib.util.module_from_spec(spec); spec.loader.exec_module(rc)
M='mixamorig:'
for hands in ('fk', 'chest', 'auto'):
    bpy.ops.wm.open_mainfile(filepath=os.path.join(D, 'chief.blend'))
    src = rc.append_cortana(os.path.join(D, 'her.blend'), ['Hands Together|Flat']); tgt = bpy.data.objects['Chief_rig']; sc = bpy.context.scene
    mp, _ = rc.guess_map(src, tgt); rt = rc.Retarget(src, tgt, mp, 'ik', hands)
    new, fr = rt.bake(bpy.data.actions['Hands Together|Flat'], 'HT|Chief', sc)
    tgt.animation_data.action = new; sc.frame_set(20)
    d = (tgt.matrix_world @ tgt.pose.bones[M+'LeftHand'].tail - tgt.matrix_world @ tgt.pose.bones[M+'RightHand'].tail).length
    print(f'hands={hands}: character hand tips apart {d*100:.1f} cm')
