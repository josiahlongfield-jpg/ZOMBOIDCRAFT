"""Cortana's move-fixing panel for Blender (open cortana_moves.blend, Scripting tab, open this file, Run Script).

Adds a "Cortana" tab to the 3D view's side panel (press N), with:
  - Fix whole move: pose the selected bones at the current frame the way they should be, then press it. The same change is
    added to every frame of the move, so the motion keeps its life and just sits where you want it.
  - Fix around here: the same, but fading out over the frames either side (Blend frames), for a fix at one moment.
  - Undo the pose change: puts the selected bones back to what the move has at this frame (before you press a fix).
  - Next / previous move: steps through the moves in the file.
  - New move: makes a blank move named "Name|flat", the length you set, starting and ending in the pose she has right
    now (or her rest pose), with every bone keyed on its first and last frame. Animate the middle.
Nothing is uploaded; everything stays on this PC.
"""
import bpy
from mathutils import Quaternion, Vector


def _actions():
    return sorted([a for a in bpy.data.actions if '|' in a.name], key=lambda a: a.name)


def _arm(ctx):
    o = ctx.object
    if o and o.type == 'ARMATURE':
        return o
    return next((o for o in bpy.data.objects if o.type == 'ARMATURE'), None)


def _range(arm):
    act = arm.animation_data.action if arm.animation_data else None
    if act is None:
        return None
    a, b = act.frame_range
    return int(round(a)), int(round(b))


def _apply(ctx, blend):
    arm = _arm(ctx)
    if arm is None or arm.mode != 'POSE':
        return 'Select her armature and go into Pose Mode first.'
    rng = _range(arm)
    if rng is None:
        return 'Pick a move in the Action Editor first.'
    bones = [pb for pb in arm.pose.bones if getattr(pb, 'select', None) or getattr(pb.bone, 'select', False)]
    if not bones:
        return 'Select the bones you changed.'
    scn = ctx.scene
    here = scn.frame_current
    new = {pb.name: (pb.rotation_quaternion.copy(), pb.location.copy()) for pb in bones}
    scn.frame_set(here)  # back to what the move has here
    delta = {}
    for pb in bones:
        q0, l0 = pb.rotation_quaternion.copy(), pb.location.copy()
        q1, l1 = new[pb.name]
        delta[pb.name] = (q0.inverted() @ q1, l1 - l0)
    lo, hi = rng
    # straight on the curves, no frame_set per frame: that re-evaluated her whole subdivided body every frame and
    # froze Blender on the new-body file (Josiah 18 Nov)
    act = arm.animation_data.action
    fcs = getattr(act, 'fcurves', None)
    if fcs is None:   # Blender 5: layered actions keep their curves in the slot's channelbag
        from bpy_extras import anim_utils
        fcs = anim_utils.action_ensure_channelbag_for_slot(act, arm.animation_data.action_slot).fcurves
    def curves(pb, prop, n):
        path = f'pose.bones["{pb.name}"].{prop}'
        out = []
        for i in range(n):
            fc = fcs.find(path, index=i)
            if fc is None:
                fc = fcs.new(path, index=i, group_name=pb.name) if not hasattr(act, 'fcurves') else fcs.new(path, index=i, action_group=pb.name)
                fc.keyframe_points.insert(lo, getattr(pb, prop)[i])
            out.append(fc)
        return out
    cv = {pb.name: (curves(pb, 'rotation_quaternion', 4), curves(pb, 'location', 3)) for pb in bones}
    for pb in bones:
        dq, dl = delta[pb.name]
        qc, lc = cv[pb.name]
        vals = []
        for f in range(lo, hi + 1):
            w = 1.0
            if blend is not None:
                d = abs(f - here)
                if d >= blend:
                    continue
                t = 1 - d / blend
                w = t * t * (3 - 2 * t)
            q = Quaternion([c.evaluate(f) for c in qc]) @ Quaternion().slerp(dq, w)
            l = [lc[i].evaluate(f) + dl[i] * w for i in range(3)]
            vals.append((f, q, l))
        for f, q, l in vals:
            for i in range(4):
                qc[i].keyframe_points.insert(f, q[i], options={'FAST'})
            for i in range(3):
                lc[i].keyframe_points.insert(f, l[i], options={'FAST'})
        for c in qc + lc:
            c.update()
    scn.frame_set(here)
    return None


class CORTANA_OT_fix_all(bpy.types.Operator):
    bl_idname = 'cortana.fix_all'
    bl_label = 'Fix whole move'
    bl_description = 'Add the change you made to the selected bones (at this frame) to every frame of this move'
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, ctx):
        err = _apply(ctx, None)
        if err:
            self.report({'WARNING'}, err)
            return {'CANCELLED'}
        self.report({'INFO'}, 'Applied to the whole move')
        return {'FINISHED'}


class CORTANA_OT_fix_here(bpy.types.Operator):
    bl_idname = 'cortana.fix_here'
    bl_label = 'Fix around here'
    bl_description = 'Add the change to the frames around this one, fading out over Blend frames'
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, ctx):
        err = _apply(ctx, max(1, ctx.scene.cortana_blend))
        if err:
            self.report({'WARNING'}, err)
            return {'CANCELLED'}
        self.report({'INFO'}, 'Applied around this frame')
        return {'FINISHED'}


class CORTANA_OT_reset(bpy.types.Operator):
    bl_idname = 'cortana.reset'
    bl_label = 'Undo the pose change'
    bl_description = "Put the bones back to what the move has at this frame"

    def execute(self, ctx):
        ctx.scene.frame_set(ctx.scene.frame_current)
        return {'FINISHED'}


class CORTANA_OT_step(bpy.types.Operator):
    bl_idname = 'cortana.step'
    bl_label = 'Next move'
    step: bpy.props.IntProperty(default=1)

    def execute(self, ctx):
        arm = _arm(ctx)
        acts = _actions()
        if arm is None or not acts:
            return {'CANCELLED'}
        arm.animation_data_create()
        cur = arm.animation_data.action
        i = acts.index(cur) if cur in acts else -1
        act = acts[(i + self.step) % len(acts)]
        arm.animation_data.action = act
        a, b = act.frame_range
        ctx.scene.frame_start, ctx.scene.frame_end = int(a), int(b)
        ctx.scene.frame_set(int(a))
        return {'FINISHED'}


FPS = 30


class CORTANA_OT_new_move(bpy.types.Operator):
    bl_idname = 'cortana.new_move'
    bl_label = 'New move'
    bl_description = ('Make a blank "Name|flat" move, starting and ending in the pose she has now (or her rest pose), '
                      'every bone keyed on the first and last frame')
    bl_options = {'REGISTER', 'UNDO'}

    def execute(self, ctx):
        scn = ctx.scene
        arm = _arm(ctx)
        if arm is None:
            self.report({'WARNING'}, 'No armature in this file.')
            return {'CANCELLED'}
        name = scn.cortana_new_name.strip()
        if not name or '|' in name:
            self.report({'WARNING'}, 'Type a plain name for the move (no "|"), e.g. Lean On Rail.')
            return {'CANCELLED'}
        full = f'{name}|flat'
        if any(a.name.lower() == full.lower() for a in bpy.data.actions):
            self.report({'WARNING'}, f'"{full}" already exists. Pick another name.')
            return {'CANCELLED'}
        end = 1 + max(1, round(scn.cortana_new_seconds * FPS))
        # the start pose: what she shows right now, read before the action is swapped
        if scn.cortana_new_from == 'REST':
            pose = {pb.name: (Quaternion(), Vector()) for pb in arm.pose.bones}
        else:
            pose = {}
            for pb in arm.pose.bones:
                if pb.rotation_mode == 'QUATERNION':
                    q = pb.rotation_quaternion.copy()
                else:
                    q = pb.rotation_euler.to_quaternion() if pb.rotation_mode != 'AXIS_ANGLE' else \
                        Quaternion(pb.rotation_axis_angle[1:], pb.rotation_axis_angle[0])
                pose[pb.name] = (q, pb.location.copy())
        arm.animation_data_create()
        act = bpy.data.actions.new(full)
        act.use_fake_user = True          # kept in the file even before it's saved anywhere else
        act['fps'] = float(FPS)
        arm.animation_data.action = act
        for pb in arm.pose.bones:
            q, l = pose[pb.name]
            pb.rotation_mode = 'QUATERNION'
            pb.rotation_quaternion = q
            pb.location = l
            pb.scale = (1, 1, 1)
            for f in (1, end):
                pb.keyframe_insert('location', frame=f, group=pb.name)
                pb.keyframe_insert('rotation_quaternion', frame=f, group=pb.name)
        # on the spot: her root (the armature object) stays at the centre facing forward
        arm.rotation_mode = 'XYZ'
        arm.location = (0, 0, 0)
        arm.rotation_euler = (0, 0, 0)
        for f in (1, end):
            arm.keyframe_insert('location', frame=f)
            arm.keyframe_insert('rotation_euler', frame=f)
        # the move's length is fixed to what was asked (the export uses the action's own range)
        act.use_frame_range = True
        act.frame_start, act.frame_end = 1, end
        scn.render.fps = FPS
        scn.frame_start, scn.frame_end = 1, end
        scn.frame_set(1)
        self.report({'INFO'}, f'Made "{full}": {end} frames ({(end - 1) / FPS:.2f} s). Animate the middle frames.')
        return {'FINISHED'}


class CORTANA_PT_panel(bpy.types.Panel):
    bl_label = 'Cortana'
    bl_idname = 'CORTANA_PT_panel'
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = 'Cortana'

    def draw(self, ctx):
        col = self.layout.column(align=True)
        arm = _arm(ctx)
        act = arm.animation_data.action if arm and arm.animation_data else None
        col.label(text=f"Move: {act.name if act else '(none)'}")
        row = col.row(align=True)
        row.operator('cortana.step', text='< Previous').step = -1
        row.operator('cortana.step', text='Next >').step = 1
        col.separator()
        col.label(text='Pose the selected bones, then:')
        col.operator('cortana.fix_all', icon='ANIM')
        col.prop(ctx.scene, 'cortana_blend')
        col.operator('cortana.fix_here', icon='KEYFRAME')
        col.separator()
        col.operator('cortana.reset', icon='LOOP_BACK')
        col.separator()
        col.label(text='Make a new move:')
        col.prop(ctx.scene, 'cortana_new_name')
        col.prop(ctx.scene, 'cortana_new_seconds')
        col.prop(ctx.scene, 'cortana_new_from', text='Start from')
        col.operator('cortana.new_move', icon='ADD')


CLASSES = (CORTANA_OT_new_move, CORTANA_OT_fix_all, CORTANA_OT_fix_here, CORTANA_OT_reset, CORTANA_OT_step, CORTANA_PT_panel)


def register():
    bpy.types.Scene.cortana_blend = bpy.props.IntProperty(name='Blend frames', default=8, min=1, max=120)
    bpy.types.Scene.cortana_new_name = bpy.props.StringProperty(name='Name', default='',
                                                                description='Plain English: she reads it to know when to use the move')
    bpy.types.Scene.cortana_new_seconds = bpy.props.FloatProperty(name='Seconds', default=2.0, min=0.2, max=60.0, step=10,
                                                                  precision=1)
    bpy.types.Scene.cortana_new_from = bpy.props.EnumProperty(name='Start from', items=[
        ('NOW', 'Pose shown now', 'The pose she has at this frame of the move you are on'),
        ('REST', 'Rest pose', "Her skeleton's rest pose")], default='NOW')
    for c in CLASSES:
        bpy.utils.register_class(c)


def unregister():
    for c in reversed(CLASSES):
        bpy.utils.unregister_class(c)
    for p in ('cortana_blend', 'cortana_new_name', 'cortana_new_seconds', 'cortana_new_from'):
        if hasattr(bpy.types.Scene, p):
            delattr(bpy.types.Scene, p)


if __name__ == '__main__':
    try:
        unregister()
    except Exception:
        pass
    register()
