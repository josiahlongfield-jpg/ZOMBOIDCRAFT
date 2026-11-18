"""Writes Cortana's (edited) actions back out as her game moves file.

Run inside Blender on the .blend with her armature and the 'Name|flat' / 'Name|heel' actions:
  blender her_moves.blend --background --python export_moves.py -- ORIGINAL.bin HER.glb OUT.bin
Bones and settings come from ORIGINAL.bin; every action named like a move replaces it (others are kept as they were).
Install OUT.bin as config\\swordbase\\npc\\cortana\\moves.bin (game closed). Stays on this PC.
"""
import os, sys
import bpy
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from moves_common import C, CI, read_moves, write_moves, glb_rest

ARGS = sys.argv[sys.argv.index('--') + 1:]
ORIG, GLB, OUT = ARGS[0], ARGS[1], ARGS[2]
arm = next(o for o in bpy.data.objects if o.type == 'ARMATURE')
bones, clips = read_moves(ORIG)
rest_game = glb_rest(GLB)
K = {}
for b in bones:
    bb = arm.data.bones.get(b)
    if bb is not None:
        R_g = rest_game.get(b, CI @ bb.matrix_local)
        K[b] = R_g.inverted() @ CI @ bb.matrix_local
scene = bpy.context.scene
changed = 0
for c in clips:
    act = bpy.data.actions.get(f"{c['name']}|{c['tag']}")
    if act is None:
        continue
    arm.animation_data.action = act
    a, b_ = act.frame_range
    F = int(round(b_ - a)) + 1
    q = np.zeros((F, len(bones), 4)); p = np.zeros((F, len(bones), 3)); root = np.zeros((F, 3))
    q[..., 3] = 1
    for f in range(F):
        scene.frame_set(int(a) + f)
        for i, b in enumerate(bones):
            if b not in K:
                q[f, i] = c['q'][min(f, len(c['q']) - 1), i]; p[f, i] = c['p'][min(f, len(c['p']) - 1), i]
                continue
            G = CI @ arm.pose.bones[b].matrix @ K[b].inverted()
            r = G.to_quaternion()
            q[f, i] = (r.x, r.y, r.z, r.w); p[f, i] = tuple(G.translation)
        root[f] = (arm.location.x, -arm.location.y, arm.rotation_euler.z)
    c.update(q=q, p=p, root=root, fps=float(act.get('fps', c['fps'])), speed=float(act.get('speed', c['speed'])),
             travel=bool(act.get('travel', c['travel'])))
    changed += 1
    print('exported', act.name, F, 'frames')
# brand-new moves made in Blender: any action named "Name|flat" or "Name|heel" that isn't in her moves yet
known = {f"{c['name']}|{c['tag']}" for c in clips}
added = 0
for act in sorted(bpy.data.actions, key=lambda a: a.name):
    if '|' not in act.name or act.name in known:
        continue
    name, tag = act.name.rsplit('|', 1)
    if tag not in ('flat', 'heel') or not name.strip():
        print('skipped (name must end in |flat or |heel):', act.name)
        continue
    arm.animation_data.action = act
    a, b_ = act.frame_range
    F = int(round(b_ - a)) + 1
    q = np.zeros((F, len(bones), 4)); p = np.zeros((F, len(bones), 3)); root = np.zeros((F, 3))
    q[..., 3] = 1
    for f in range(F):
        scene.frame_set(int(a) + f)
        for i, b in enumerate(bones):
            pb = arm.pose.bones.get(b)
            if pb is None or b not in K:
                continue
            G = CI @ pb.matrix @ K[b].inverted()
            r = G.to_quaternion()
            q[f, i] = (r.x, r.y, r.z, r.w); p[f, i] = tuple(G.translation)
        root[f] = (arm.location.x, -arm.location.y, arm.rotation_euler.z)
    travel = bool(act.get('travel', np.hypot(*(root[-1, :2] - root[0, :2])) > 0.2))
    clips.append(dict(name=name.strip(), tag=tag, fps=float(act.get('fps', 30.0)), speed=float(act.get('speed', 0.0)),
                      travel=travel, root=root, q=q, p=p))
    added += 1
    print('new move', act.name, F, 'frames')
print(added, 'new moves added')
write_moves(OUT, bones, clips)
print('wrote', OUT, '(', changed, 'moves from Blender,', len(clips), 'in all )')
