"""Gives Cortana's foot IK a floor (Josiah 24 Nov: "a floor so her feet always have a ground to stand on").

Run in Blender on his animation file after the Cortana helper's foot IK is set up (IK_foot_L / IK_foot_R under b_pedestal).
Adds a Floor constraint, first in each IK_foot's stack, so the ankle can never sink below its standing height above
b_pedestal: at rest nothing changes, a foot can still lift, tilt and turn, and lowering her hips bends her knees instead.
Nothing else is touched (no bones, poses, keys or moves). Run again to do nothing; remove_floor() takes it off.
"""
import bpy

NAME = "Cortana floor"


def _arm():
    return next((o for o in bpy.data.objects if o.type == 'ARMATURE' and 'IK_foot_L' in o.data.bones), None)


def add_floor():
    arm = _arm()
    if arm is None:
        return "No armature with IK_foot_L here: set up her foot IK first."
    bb = arm.data.bones
    if 'b_pedestal' not in bb:
        return "No b_pedestal bone."
    out = []
    for side in ('L', 'R'):
        pb = arm.pose.bones['IK_foot_' + side]
        if any(c.name == NAME for c in pb.constraints):
            out.append(f'{side}: already has a floor')
            continue
        # how high the ankle stands above the pedestal at rest (Blender is Z up)
        h = (bb['IK_foot_' + side].head_local - bb['b_pedestal'].head_local).z
        c = pb.constraints.new('FLOOR')
        c.name = NAME
        c.target = arm
        c.subtarget = 'b_pedestal'
        c.floor_location = 'FLOOR_Z'
        c.offset = h
        c.use_rotation = False
        pb.constraints.move(len(pb.constraints) - 1, 0)
        out.append(f'{side}: floor at {h * 100:.1f} cm ankle height')
    return '; '.join(out)


def remove_floor():
    arm = _arm()
    if arm is None:
        return "No armature with IK_foot_L here."
    n = 0
    for side in ('L', 'R'):
        pb = arm.pose.bones['IK_foot_' + side]
        for c in [c for c in pb.constraints if c.name == NAME]:
            pb.constraints.remove(c)
            n += 1
    return f'removed {n} floor constraints'


if __name__ == '__main__':
    print(add_floor())
