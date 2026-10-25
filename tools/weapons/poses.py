"""Third-person holds: how a survivor carries and aims a weapon, as other players see it.

Minecraft's arms are stiff bars that turn at the shoulder, and a held item is fixed to the hand of
the main arm. So a hold is: an angle for the gun arm, plus the item model's thirdperson_righthand
transform that puts the weapon where the hand is in that pose. The off arm isn't fixed: in game it
reaches for a support line on the weapon (a rifle's magazine well and handguard, the end of a bat's
handle) wherever the gun arm has moved it, so bobbing, looking up and down and swinging all keep
both hands on the weapon (see HoldPoses.java).

Everything here is in the player model's own space, in pixels: y points down, the player faces -z,
their right is -x. The right shoulder turns at (-5, 2, 0) and the left at (5, 2, 0).

The generator calls this to get each weapon's third-person transform and writes HoldPoseData.java
for the game and poses.json for the preview page.
"""
import math

import numpy as np
from scipy.optimize import minimize

import model as M

S_R = np.array([-5.0, 2.0, 0.0])
S_L = np.array([5.0, 2.0, 0.0])
HAND_R = -1.0           # the hand's centre line sits 1 px off the arm's turning axis (wide arms)
HAND_L = 1.0
REACH = 9.2             # where the palm is along the arm; the arm is 12 long and starts 2 above the shoulder
REACH_MAX = 10.2        # past this the fingers can't touch

# what the game does between the hand and the item model (ItemInHandLayer)
F = M.rot_matrix('x', -90) @ M.rot_matrix('y', 180)
O = np.array([1.0, 2.0, -10.0])
UPRIGHT = np.diag([-1.0, -1.0, 1.0])       # an item-space "up, muzzle to -z" stood the right way up here

HEAD = (np.array([-4.0, -8.0, -4.0]), np.array([4.0, 0.0, 4.0]))
BODY = (np.array([-4.0, 0.0, -2.0]), np.array([4.0, 12.0, 2.0]))


def Rx(d):
    return M.rot_matrix('x', d)


def Ry(d):
    return M.rot_matrix('y', d)


def euler_zyx(R):
    """(x, y, z) radians with R == Rz(z) @ Ry(y) @ Rx(x), the order ModelPart turns in."""
    y = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    if abs(R[2, 0]) < 0.99999:
        x = math.atan2(R[2, 1], R[2, 2])
        z = math.atan2(R[1, 0], R[0, 0])
    else:
        x = math.atan2(-R[1, 2], R[1, 1])
        z = 0.0
    return np.array([x, y, z])


def R_zyx(x, y, z):
    return M.rot_matrix('z', math.degrees(z)) @ M.rot_matrix('y', math.degrees(y)) @ M.rot_matrix('x', math.degrees(x))


def aim_arm(pivot, target, hand_x):
    """Arm angles (x, y; z = 0) that put the hand's centre line through target. Mirrors HoldPoses.aim."""
    D = np.asarray(target) - pivot
    rxz = math.hypot(D[0], D[2])
    a = -math.sqrt(max(rxz * rxz - hand_x * hand_x, 1e-6))
    x = math.atan2(a, D[1])
    y = math.atan2(a, hand_x) - math.atan2(D[2], D[0])
    return np.array([x, y, 0.0]), math.hypot(D[1], a)


def support_target(pivot, p0, p1, reach=REACH):
    """The point on segment p0-p1 whose distance from the shoulder is closest to reach. Mirrors HoldPoses."""
    best, best_err = p0, 1e9
    for i in range(33):
        p = p0 + (p1 - p0) * (i / 32)
        err = abs(np.linalg.norm(p - pivot) - reach)
        if err < best_err:
            best, best_err = p, err
    return best


class Hold:
    """A weapon placed in the body: R maps the weapon's build frame into model space, scale s px per unit.

    key points are given in the build frame (the grip at `grip`)."""

    def __init__(self, R, s, grip, support, extra=(), hand_off=(0, 0, 0)):
        self.R, self.s = R, s
        # where the fist sits relative to the weapon's grip, in model space: Minecraft's arms are four
        # pixels thick, so a weapon held dead centre in the fist disappears inside the arm
        self.hand_off = np.asarray(hand_off, float)
        self.grip = np.asarray(grip, float)
        self.support = [np.asarray(p, float) for p in support]
        self.extra = [np.asarray(p, float) for p in extra]

    def place(self, G, b):
        return G + self.s * self.R @ (np.asarray(b, float) - self.grip)


def inside(p, box, pad=0.0):
    lo, hi = box
    return np.all(p > lo - pad) and np.all(p < hi + pad)


def solve_grip(hold, G0, wants, clear=(), arm_len=REACH):
    """Moves the grip G from G0 to satisfy soft goals: (build point, model target, weight) pulls, the gun arm
    at its palm length, the off hand able to reach the support line, and points kept out of the body."""
    def cost(G):
        c = 0.0
        _, L = aim_arm(S_R, G + hold.hand_off, HAND_R)
        c += 4 * (L - arm_len) ** 2
        for b, target, w in wants:
            c += w * np.sum((hold.place(G, b) - target) ** 2)
        p0, p1 = (hold.place(G, b) for b in hold.support)
        T = support_target(S_L, p0, p1)
        d = np.linalg.norm(T - S_L)
        c += 6 * max(0.0, d - REACH_MAX) ** 2 + 1 * (d - REACH) ** 2
        for b in clear:
            p = hold.place(G, b)
            for box in (HEAD, BODY):
                lo, hi = box
                pen = np.minimum(p - (lo - 0.3), (hi + 0.3) - p)
                if np.all(pen > 0):
                    c += 20 * pen.min() ** 2
        return c

    res = minimize(cost, np.asarray(G0, float), method='Nelder-Mead', options=dict(xatol=1e-3, fatol=1e-6, maxiter=4000))
    return res.x


def arm_for(hold, G):
    ang, L = aim_arm(S_R, G + hold.hand_off, HAND_R)
    return ang, L


def display_for(hold, G, arm, g_item, s_item):
    """The item model's thirdperson_righthand transform for a weapon held with the gun arm at `arm`
    (radians) and its grip at G. g_item: the grip in item-model coordinates; s_item: item px per unit."""
    Ra = R_zyx(*arm)
    Rd = F.T @ Ra.T @ hold.R
    t = F.T @ Ra.T @ (G - S_R) - O - s_item * Rd @ (np.asarray(g_item) - 8)
    return M.transform(M.euler_xyz(Rd), t, s_item)


def arm_local(hold, G, arm, b):
    """A build-frame point of the held weapon in the gun arm's own frame (what the game moves with the arm)."""
    Ra = R_zyx(*arm)
    return Ra.T @ (hold.place(G, b) - S_R)


def frame(dir_up, side_hint):
    """Rotation taking build +y to dir_up and build +x as close to side_hint as it can."""
    y = np.asarray(dir_up, float)
    y /= np.linalg.norm(y)
    x = np.asarray(side_hint, float)
    x = x - y * (x @ y)
    x /= np.linalg.norm(x)
    z = np.cross(x, y)
    return np.column_stack([x, y, z])


def rotation_between(a, b):
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(a @ b)
    if np.linalg.norm(v) < 1e-9:
        return np.eye(3)
    K = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + K + K @ K * (1 / (1 + c))


def unit(*v):
    v = np.array(v, float)
    return v / np.linalg.norm(v)
