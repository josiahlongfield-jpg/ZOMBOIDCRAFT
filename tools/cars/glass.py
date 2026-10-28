"""
A car's windows for the game, one per glass pane of the model: where each is (a flat rectangle the
bullets are tested against and the holes are drawn on), what kind it is, and how many bullet holes it
takes before it shatters. pack.py adds them to models.json per variant (a variant's crumpled body or
hanging door moves its glass; its broken panes are null), with each mesh's glass triangles' window, so
the game can leave out a shattered window or wind one down.

Kinds: 0 the windscreen (laminated: it holds on to a lot of holes), 1 a side window (winds down),
2 the rear window, 3 anything else (roof, a bus's upper lights). Holes go with the pane's size.
"""
import numpy as np

WINDSCREEN, SIDE, REAR, OTHER = 0, 1, 2, 3


def kind(c, n):
    if abs(n[0]) > 0.6:
        return SIDE
    if n[1] > 0.8:
        return OTHER
    if n[2] > 0.3 and c[2] > 0:
        return WINDSCREEN
    if n[2] < -0.3 and c[2] < 0:
        return REAR
    return OTHER


def holes(k, area):
    """How many holes before it gives: laminated windscreens hang together, toughened side and rear glass doesn't."""
    if k == WINDSCREEN:
        return int(np.clip(round(4 + 4 * area), 5, 10))
    if k == SIDE:
        return int(np.clip(round(6 * area), 1, 5))
    return int(np.clip(round(4 * area), 2, 6))


def windows(car, b, centre):
    """The windows of one built variant (wreck.build_variant), in centred model space; None where it's broken out."""
    out = []
    height = b['tris'][..., 1].max()
    for k in range(car.npanes):
        sel = b['pane'] == k
        if not sel.any():
            out.append(None)
            continue
        T = b['tris'][sel] - centre
        V = T.reshape(-1, 3)
        mean = V.mean(0)
        _, _, vt = np.linalg.svd(V - mean, full_matrices=False)
        a, bb, n = vt[0], vt[1], vt[2]
        # facing out of the car: away from the middle of the cabin
        if n @ (mean - np.array([0, 0.6 * height, 0])) < 0:
            n = -n
        da, db = (V - mean) @ a, (V - mean) @ bb
        c = mean + a * (da.min() + da.max()) / 2 + bb * (db.min() + db.max()) / 2
        ha, hb = a * np.ptp(da) / 2, bb * np.ptp(db) / 2
        nr = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
        area = float(np.linalg.norm(nr, axis=1).sum() / 2)
        k_ = kind(c, n)
        out.append([round(float(x), 3) for x in (*c, *n, *ha, *hb)] + [k_, holes(k_, area)])
    return out


def glass_tris(b, meshes):
    """For each mesh (as pack.py writes it: a mask of triangles), the window of each of its glass triangles in order."""
    return [[int(p) for p in b['pane'][np.where(m & b['glass'])[0]]] for m in meshes]
