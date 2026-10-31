"""Run inside Blender (Scripting tab > Open > this file > Run Script), on the character posed exactly as you upload her
to Mixamo. Writes face.json next to the .blend: which vertices follow her jaw and each upper eyelid, and how much, and
where those bones hinge. Mixamo keeps only the body's bones, so this is how her mouth and blinks come back in game
(pack.py ... face.json puts them back as three extra bones under her head, which the game turns as she talks and blinks).

Set the names below to her bones (the name shown when the bone is selected). Leave a lid as "" if she has none: she
just won't blink. "auto" looks for a bone with jaw / lid in its name.
"""
import json
import os

import bpy

JAW = "auto"
LID_L = "auto"
LID_R = "auto"


def find(arm, want, words, side):
    names = [b.name for b in arm.data.bones]
    if want and want != "auto":
        if want not in names:
            raise SystemExit(f'No bone named "{want}" in {arm.name}. Check the spelling (capitals matter).')
        return want
    if want == "":
        return None
    for n in names:
        low = n.lower()
        if 'low' in low or 'bot' in low:
            continue
        if any(w in low for w in words) and (side is None or any(low.endswith(s) or s + "." in low or s + "_" in low
                                                                 for s in side)):
            return n
    return None


def to_game(world, scale):
    # Blender is Z up, -Y forward; the FBX the game reads is Y up, +Z forward, in metres
    return [world.x * scale, world.z * scale, -world.y * scale]


def main():
    arm = next((o for o in bpy.context.selected_objects if o.type == 'ARMATURE'), None) or \
        next((o for o in bpy.context.scene.objects if o.type == 'ARMATURE' and o.visible_get()), None)
    if arm is None:
        raise SystemExit("No armature found. Select her skeleton (click it in the Outliner) and run again.")
    jaw = find(arm, JAW, ("jaw",), None)
    if jaw is None:
        raise SystemExit(f'No jaw bone found in {arm.name}. Set JAW = "..." at the top of this script to its name.')
    lid_l = find(arm, LID_L, ("lid",), ("l", "left"))
    lid_r = find(arm, LID_R, ("lid",), ("r", "right"))
    print(f"Skeleton {arm.name}: jaw {jaw}, left lid {lid_l}, right lid {lid_r}")
    scale = bpy.context.scene.unit_settings.scale_length
    pose = arm.pose.bones

    def pivot(name):
        return to_game(arm.matrix_world @ pose[name].head, scale)

    depsgraph = bpy.context.evaluated_depsgraph_get()
    meshes = {}
    for o in bpy.context.scene.objects:
        if o.type != 'MESH' or not o.visible_get():
            continue
        if not any(m.type == 'ARMATURE' and m.object == arm for m in o.modifiers):
            continue
        groups = {g.name: g.index for g in o.vertex_groups}
        want = [groups.get(jaw), groups.get(lid_l) if lid_l else None, groups.get(lid_r) if lid_r else None]
        ev = o.evaluated_get(depsgraph)
        me = ev.to_mesh()
        if len(me.vertices) != len(o.data.vertices):
            ev.to_mesh_clear()
            print(f"  SKIPPED {o.name}: a modifier changes its vertex count (turn off Subdivision and the like, then run again)")
            continue
        pos, w, idx, all_pos = [], [], [], []
        for v, src in zip(me.vertices, o.data.vertices):
            p = to_game(o.matrix_world @ v.co, scale)
            all_pos.append(p)
            ws = [0.0, 0.0, 0.0]
            for g in src.groups:
                for k, gi in enumerate(want):
                    if gi is not None and g.group == gi:
                        ws[k] = round(g.weight, 4)
            if sum(ws) > 0:
                idx.append(v.index)
                pos.append([round(c, 6) for c in p])
                w.append(ws)
        ev.to_mesh_clear()
        lo = [min(p[i] for p in all_pos) for i in range(3)] if all_pos else [0, 0, 0]
        hi = [max(p[i] for p in all_pos) for i in range(3)] if all_pos else [0, 0, 0]
        meshes[o.name] = {'idx': idx, 'pos': pos, 'w': w, 'box': [lo, hi]}
        print(f"  {o.name}: {len(idx)} vertices follow the jaw or lids")
    if not any(m['idx'] for m in meshes.values()):
        raise SystemExit(f'No vertices are weighted to {jaw}. Her face needs to be skinned to the jaw bone '
                         '(weight paint the jaw and chin onto it) before this can work.')
    head = next((b.name for b in arm.data.bones if b.name.lower().endswith('head')), jaw)
    face = {'pivots': {'jaw': pivot(jaw), 'eye_l': pivot(lid_l or jaw), 'eye_r': pivot(lid_r or jaw), 'head': pivot(head)},
            'meshes': meshes}
    out = os.path.join(os.path.dirname(bpy.data.filepath) or os.path.expanduser('~'), 'face.json')
    with open(out, 'w') as f:
        json.dump(face, f)
    print("Wrote " + out)


main()
