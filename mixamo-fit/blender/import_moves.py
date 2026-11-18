"""Puts Cortana's moves on her armature in Blender, one action per move and version ('Walk Forward|flat', ...).

Run inside Blender (her glb already imported, nothing else needed):
  blender --python import_moves.py -- MOVES.bin HER.glb
or from Blender's Scripting tab with ARGS edited below. Nothing is uploaded; everything stays on this PC.
The armature object carries the root motion (location x/y and turn about Z); the bones carry the pose.
"""
import os, sys
import bpy
from mathutils import Matrix
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from moves_common import C, CI, read_moves, glb_rest, game_matrix

ARGS = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
MOVES = ARGS[0] if ARGS else r'C:\Dev\Zomboidcraft-Desktop\additions\cortana-mixamo\fit\out-kept\cortana_moves.bin'
GLB = ARGS[1] if len(ARGS) > 1 else None

if GLB and not any(o.type == 'ARMATURE' for o in bpy.data.objects):
    bpy.ops.import_scene.gltf(filepath=GLB)
arm = next(o for o in bpy.data.objects if o.type == 'ARMATURE')
bones, clips = read_moves(MOVES)
rest_game = glb_rest(GLB) if GLB else None

# K: per bone, from the game's rest frame to Blender's bone frame (the importer may re-orient bones)
K, missing = {}, []
for b in bones:
    bb = arm.data.bones.get(b)
    if bb is None:
        missing.append(b); continue
    R_bl = bb.matrix_local
    R_g = rest_game[b] if rest_game and b in rest_game else CI @ R_bl
    K[b] = R_g.inverted() @ CI @ R_bl
    err = (C @ R_g).translation - R_bl.translation
    if err.length > 1e-3:
        print('warning: rest of', b, 'differs by', round(err.length, 4))
if missing:
    print('bones not in the armature (skipped):', missing)

order = [b for b in bones if b in K]
idx = {b: i for i, b in enumerate(bones)}
parent = {b: (arm.data.bones[b].parent.name if arm.data.bones[b].parent else None) for b in order}
arm.animation_data_create()
for c in clips:
    act = bpy.data.actions.new(f"{c['name']}|{c['tag']}")
    act.use_fake_user = True
    act['fps'], act['speed'], act['travel'] = c['fps'], c['speed'], int(c['travel'])
    arm.animation_data.action = act
    F = len(c['root'])
    for f in range(F):
        P = {}
        for b in order:
            i = idx[b]
            P[b] = C @ game_matrix(c['q'][f, i], c['p'][f, i]) @ K[b]
        for b in order:
            pb = arm.pose.bones[b]
            bb = arm.data.bones[b]
            pa = parent[b]
            if pa and pa in P:
                local_rest = bb.parent.matrix_local.inverted() @ bb.matrix_local
                basis = local_rest.inverted() @ P[pa].inverted() @ P[b]
            else:
                basis = bb.matrix_local.inverted() @ P[b]
            pb.matrix_basis = basis
            pb.keyframe_insert('location', frame=f + 1, group=b)
            pb.keyframe_insert('rotation_quaternion', frame=f + 1, group=b)
        x, z, yaw = c['root'][f]
        # game (x, z) on the floor, yaw about +Y -> Blender (x, -z) and a turn about +Z
        arm.location = (x, -z, 0)
        arm.rotation_mode = 'XYZ'
        arm.rotation_euler = (0, 0, yaw)
        arm.keyframe_insert('location', frame=f + 1)
        arm.keyframe_insert('rotation_euler', frame=f + 1)
    print('imported', act.name, F, 'frames')
bpy.context.scene.render.fps = 30
print('done:', len(clips), 'actions. Pick one in the Action Editor; save the .blend; run export_moves.py when finished.')
