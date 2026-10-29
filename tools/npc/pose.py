"""The NPC animator, in Python so poses can be previewed offline. NpcPose.java is a hand port of this: keep the two
in step. Angles are degrees; each bone's turn is Ry * Rx * Rz about its pivot (Rz first), in its parent's frame.
Model space: +z is forward, +y up, +x her left. A downward limb swings forward with negative x."""
import math
import numpy as np

# rest pose fix: the bought model stands in an A-pose; arms come down to the sides
ARM_DOWN = 40.0
# and her feet, planted wide, come in under the hips
LEG_IN = 11.0


def pose(bones, mode, phase, t, aim=0.0, head_yaw=0.0, head_pitch=0.0, kick=0.0):
    """mode: 'idle', 'walk' or 'run', or a (move, run) pair of 0..1 blends as the game passes them.
    Returns ({bone index: (x, y, z) degrees}, hips lift in metres)."""
    idx = {b['name']: i for i, b in enumerate(bones)}
    R = {i: [0.0, 0.0, 0.0] for i in range(len(bones))}
    if isinstance(mode, tuple):
        move, run = mode
    else:
        move = {'idle': 0.0, 'walk': 1.0, 'run': 1.0}[mode]
        run = 1.0 if mode == 'run' else 0.0
    s, c = math.sin(phase), math.cos(phase)
    A = 24 + 16 * run          # thigh swing
    K = 30 + 45 * run          # knee bend
    B = 16 + 22 * run          # arm swing
    lift = 0.0
    # legs
    R[idx['l_thigh']][2] = -LEG_IN
    R[idx['r_thigh']][2] = LEG_IN
    R[idx['l_shin']][2] = LEG_IN * 0.4
    R[idx['r_shin']][2] = -LEG_IN * 0.4
    R[idx['l_thigh']][0] = -A * s * move
    R[idx['r_thigh']][0] = A * s * move
    R[idx['l_shin']][0] = K * max(0.0, s) * move + 4
    R[idx['r_shin']][0] = K * max(0.0, -s) * move + 4
    lift = -0.03 * (1 + run) * abs(s) * move
    R[idx['hips']][1] = 6 * s * move
    R[idx['chest']][1] = -9 * s * move
    R[idx['chest']][0] = 3 * move + 9 * run + 1.2 * math.sin(t * 0.09)
    R[idx['hips']][2] = 1.5 * math.sin(t * 0.031) * (1 - move)
    # arms: down to the sides, swinging against the legs, elbows soft (bent hard when running)
    for side, sign in (('l', 1), ('r', -1)):
        arm, fore = idx[side + '_arm'], idx[side + '_forearm']
        swing = B * s * move * (1 if side == 'l' else -1)
        R[arm][2] = -sign * (ARM_DOWN - 2 * math.sin(t * 0.07) * (1 - move))
        R[arm][0] = swing
        R[fore][0] = -(12 + 55 * run)
    if aim > 0:
        # a long gun at the shoulder: right hand on the grip, left hand forward on the guard
        r_arm, r_fore, l_arm, l_fore = idx['r_arm'], idx['r_forearm'], idx['l_arm'], idx['l_forearm']
        for i, target in ((r_arm, (-78, 24, ARM_DOWN + 4)), (r_fore, (-78, 0, 0)), (l_arm, (-96, -36, -ARM_DOWN - 2)), (l_fore, (-16, 0, 0))):
            for k in range(3):
                R[i][k] += (target[k] - R[i][k]) * aim
        # a shot's kick: the shoulders rock back and the muzzle climbs, gone in a few ticks
        R[r_arm][0] += 6 * kick
        R[l_arm][0] += 6 * kick
        R[idx['chest']][0] -= 3 * kick
        R[idx['chest']][1] += -10 * aim
        R[idx['head']][1] += 10 * aim
    R[idx['head']][1] += head_yaw
    R[idx['head']][0] += head_pitch
    return R, lift


def rot(x, y, z):
    x, y, z = map(math.radians, (x, y, z))
    Rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return Ry @ Rx @ Rz


def matrices(bones, R, lift):
    """Each bone's 4x4 from rest to posed, model space."""
    out = []
    for i, b in enumerate(bones):
        p = np.asarray(b['pivot'], float)
        m = np.eye(4)
        m[:3, :3] = rot(*R[i])
        m[:3, 3] = p - m[:3, :3] @ p
        if b['parent'] < 0:
            m[1, 3] += lift
            out.append(m)
        else:
            out.append(out[b['parent']] @ m)
    return out


def skin(P, N, b0, b1, w, mats):
    M = np.stack(mats)
    P4 = np.c_[P, np.ones(len(P))]
    p0 = np.einsum('vij,vj->vi', M[b0], P4)[:, :3]
    p1 = np.einsum('vij,vj->vi', M[b1], P4)[:, :3]
    n0 = np.einsum('vij,vj->vi', M[b0][:, :3, :3], N)
    n1 = np.einsum('vij,vj->vi', M[b1][:, :3, :3], N)
    Pp = p0 * w[:, None] + p1 * (1 - w[:, None])
    Nn = n0 * w[:, None] + n1 * (1 - w[:, None])
    return Pp, Nn / (np.linalg.norm(Nn, axis=1, keepdims=True) + 1e-9)
