"""Skins a packed NPC mesh to a bone list (rigs/<id>.json): every vertex gets its two nearest bones and how much of
the first it takes. Distances are to each bone's 'seg' line; the two nearest blend by inverse fourth power, so joints
bend smoothly and a skirt panel between the legs follows both."""
import json
import numpy as np


def load(path):
    return json.load(open(path))['bones']


def seg_dist(P, a, b):
    a, b = np.asarray(a, float), np.asarray(b, float)
    ab = b - a
    t = np.clip(((P - a) @ ab) / (ab @ ab), 0, 1)
    return np.linalg.norm(P - (a + t[:, None] * ab), axis=1)


def weights(P, bones):
    D = np.empty((len(P), len(bones)))
    for i, b in enumerate(bones):
        # 'seg' is a line, or a bent line through several points (a forearm running on into the hand)
        pts = b['seg']
        d = np.min([seg_dist(P, pts[k], pts[k + 1]) for k in range(len(pts) - 1)], axis=0)
        only = b.get('only', {})
        bad = np.zeros(len(P), bool)
        if 'xmin' in only:
            bad |= np.abs(P[:, 0]) < only['xmin']
            # the arms only take their own side
            side = np.sign(b['pivot'][0])
            bad |= np.sign(P[:, 0]) != side
        if 'ymax' in only:
            bad |= P[:, 1] > only['ymax']
        if 'ymin' in only:
            bad |= P[:, 1] < only['ymin']
        if 'rmax' in only:
            # flesh only this close to the bone (keeps a loose skirt off a hand that hangs beside it)
            bad |= d > only['rmax']
        if b['name'].startswith(('l_thigh', 'l_shin')):
            bad |= P[:, 0] < -0.02
        if b['name'].startswith(('r_thigh', 'r_shin')):
            bad |= P[:, 0] > 0.02
        D[:, i] = np.where(bad, 1e3 + d, d)
    order = np.argsort(D, axis=1)[:, :2]
    d0 = np.take_along_axis(D, order[:, :1], 1)[:, 0] + 1e-4
    d1 = np.take_along_axis(D, order[:, 1:2], 1)[:, 0] + 1e-4
    w0 = d0 ** -4 / (d0 ** -4 + d1 ** -4)
    # a second bone that's out of bounds for the vertex gets nothing
    w0 = np.where(d1 > 500, 1.0, w0)
    return order[:, 0], order[:, 1], w0
