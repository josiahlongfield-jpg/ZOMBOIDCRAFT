"""Writes Cortana's (edited) actions back out as her game moves file.

Run inside Blender on the .blend with her armature and the 'Name|flat' / 'Name|mine' actions:
  blender her_moves.blend --background --python export_moves.py -- ORIGINAL.bin HER.glb OUT.bin [--only Idle,Wave]
Bones and settings come from ORIGINAL.bin; every action named like a move replaces it (others are kept as they were).
"Name|mine" is Josiah's own redo of a move: it replaces "Name" (and wins over a "Name|flat" in the same file).
A move taken from a |mine action is written with tag 'mine' (the game knows it's his, e.g. his Idle over the old one).
Only barefoot moves are written (Josiah 23 Nov: all his moves are barefoot); the game makes each heels version itself.
Install OUT.bin as config\\swordbase\\npc\\cortana\\moves.bin (game closed). Stays on this PC.
"""
import os, sys
import bpy
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from moves_common import C, CI, read_moves, write_moves, glb_rest

ARGS = sys.argv[sys.argv.index('--') + 1:]
ORIG, GLB, OUT = ARGS[0], ARGS[1], ARGS[2]
# optional: --only Idle,Wave  writes just those moves from Blender; every other move stays exactly as it is in ORIGINAL.bin
ONLY = None
if '--only' in ARGS:
    ONLY = {n.strip().lower() for n in ARGS[ARGS.index('--only') + 1].split(',') if n.strip()}
    print('only these moves from Blender:', sorted(ONLY))
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

# ---- her face (Josiah 24 Nov): the face_ shape keys he keys on head__default go out beside the moves, in
# moves_face.json (moves.bin itself stays bones only: the game reads it). One track per dial he moved, a value per frame,
# 0..1, the same numbers as her face dials. Where a move's face comes from, first found wins:
#   1. the move's own action, if it also has a slot for her head's shape keys (Blender 4.4+ slotted actions)
#   2. an action named "<Name>|face"
#   3. with --only and a single move: whatever is playing on her head's shape keys now (what he was just keying)
FACE_DIALS = ("smile", "frown", "browUL", "browUR", "browIn", "browDn", "wide", "round", "lipIn", "squint", "blinkL",
              "blinkR", "upUp", "tuck", "press")
head = next((o for o in bpy.data.objects if o.type == 'MESH' and o.name.split('.')[0].replace('__default', '') == 'head'
             and o.data.shape_keys is not None), None)
FACE = {}


def face_track(act, name, a, F):
    if head is None:
        return
    key = head.data.shape_keys
    blocks = {d: key.key_blocks.get('face_' + d) for d in FACE_DIALS}
    if not any(blocks.values()):
        return
    ad = key.animation_data or key.animation_data_create()
    was_act = ad.action
    was_slot = getattr(ad, 'action_slot', None)
    src = None
    for slot in getattr(act, 'slots', []):
        if getattr(slot, 'target_id_type', '') == 'KEY':
            src = (act, slot)
            break
    if src is None and bpy.data.actions.get(name + '|face') is not None:
        src = (bpy.data.actions[name + '|face'], None)
    if src is None and ONLY is not None and len(ONLY) == 1 and was_act is not None:
        src = (was_act, was_slot)
    if src is None:
        return
    try:
        ad.action = src[0]
        if src[1] is not None and hasattr(ad, 'action_slot'):
            ad.action_slot = src[1]
        vals = {d: [] for d, kb in blocks.items() if kb is not None}
        for f in range(F):
            scene.frame_set(int(a) + f)
            for d in vals:
                vals[d].append(round(float(blocks[d].value), 4))
        used = {d: v for d, v in vals.items() if max(abs(x) for x in v) > 1e-4}
        if used:
            FACE[name.strip().lower()] = dict(name=name.strip(), frames=F, dials=used)
            print('face for', name, 'from', src[0].name, ':', ', '.join(sorted(used)))
    finally:
        ad.action = was_act
        if was_slot is not None and hasattr(ad, 'action_slot'):
            try:
                ad.action_slot = was_slot
            except Exception:
                pass


changed = 0
# the game makes heels from the barefoot move (with --only, the other moves' heel versions are kept as they were)
clips = [c for c in clips if c['tag'] != 'heel' or (ONLY is not None and c['name'].lower() not in ONLY)]
for c in clips:
    if c['tag'] == 'heel' or (ONLY is not None and c['name'].lower() not in ONLY):
        continue
    act = bpy.data.actions.get(f"{c['name']}|mine") or bpy.data.actions.get(f"{c['name']}|{c['tag']}")
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
             travel=bool(act.get('travel', c['travel'])), _from_blender=True,
             tag='mine' if act.name.endswith('|mine') else 'flat')   # the game tells his own redo from the old move
    changed += 1
    face_track(act, c['name'], a, F)
    print('exported', act.name, F, 'frames')
# brand-new moves made in Blender: any action named "Name|flat" or "Name|heel" that isn't in her moves yet
known = {c['name'].lower() for c in clips}
added = 0
for act in sorted(bpy.data.actions, key=lambda a: a.name):
    if '|' not in act.name:
        continue
    name, tag = act.name.rsplit('|', 1)
    tag = tag.strip().lower()   # '|Flat' and '|flat' are the same
    if tag == 'heel':
        continue   # heels versions are made by the game
    if tag not in ('flat', 'mine') or not name.strip():
        print('skipped (name must end in |flat or |mine):', act.name)
        continue
    if name.strip().lower() in known:
        continue
    if ONLY is not None and name.strip().lower() not in ONLY:
        continue
    if tag == 'flat' and bpy.data.actions.get(f"{name}|mine"):
        continue   # his own version of this new move wins
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
    known.add(name.strip().lower())
    clips.append(dict(name=name.strip(), tag='mine' if tag == 'mine' else 'flat', fps=float(act.get('fps', 30.0)), speed=float(act.get('speed', 0.0)),
                      travel=travel, root=root, q=q, p=p, _from_blender=True))
    added += 1
    face_track(act, name.strip(), a, F)
    print('new move', act.name, F, 'frames')
print(added, 'new moves added')
write_moves(OUT, bones, clips)
print('wrote', OUT, '(', changed, 'moves from Blender,', len(clips), 'in all )')
# her face tracks: the ones already beside ORIGINAL.bin, with this export's on top; a move exported now without a face
# loses its old one (he took it off)
import json
face_in = os.path.join(os.path.dirname(os.path.abspath(ORIG)), 'moves_face.json')
face_out = os.path.join(os.path.dirname(os.path.abspath(OUT)), 'moves_face.json')
old = {}
if os.path.exists(face_in):
    try:
        old = {m['name'].strip().lower(): m for m in json.load(open(face_in, encoding='utf-8')).get('moves', [])}
    except Exception as e:
        print('old moves_face.json unreadable, starting fresh:', e)
written = {c['name'].strip().lower() for c in clips if c.get('_from_blender')}
for k in written:
    old.pop(k, None)
old.update(FACE)
moves_out = []
for m in old.values():
    moves_out.append(dict(name=m['name'], frames=m['frames'], dials=[dict(dial=d, v=v) for d, v in m['dials'].items()]
                          if isinstance(m['dials'], dict) else m['dials']))
json.dump(dict(version=1, moves=moves_out), open(face_out, 'w', encoding='utf-8'))
print('wrote', face_out, '(', len(moves_out), 'moves with a face:', ', '.join(m['name'] for m in moves_out), ')')
