"""Mixamo motion-only FBX -> model-space bone rotations/positions per frame (metres, Y up, facing +Z).
    python3 -I extract.py IN_DIR OUT.npz
Output keys: names, parents, rest_q (B,4 xyzw), rest_p (B,3), and per clip '<i>/q' (F,B,4), '<i>/p' (F,B,3), clip_names, fps."""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fbx
from scipy.spatial.transform import Rotation as Rot

KT = 46186158000  # FBX ticks per second


def props(m):
    d = {}
    p70 = m.one('Properties70')
    for p in (p70.kids if p70 else []):
        d[p.props[0]] = p.props[4:]
    return d


def euler(v):  # FBX eEulerXYZ: R = Rz Ry Rx
    return Rot.from_euler('xyz', v, degrees=True)


def load(path):
    root, _ = fbx.load(path)
    obj = root.one('Objects')
    gs = props(root.one('GlobalSettings'))
    unit = gs.get('UnitScaleFactor', [1.0])[0] / 100.0  # to metres
    models, curves, cnodes = {}, {}, {}
    for k in obj.kids:
        if k.name == 'Model':
            models[k.props[0]] = (k.props[1].split('\x00')[0].replace('mixamorig:', ''), props(k))
        elif k.name == 'AnimationCurve':
            curves[k.props[0]] = (np.asarray(k.one('KeyTime').props[0], np.int64) / KT,
                                  np.asarray(k.one('KeyValueFloat').props[0], float))
        elif k.name == 'AnimationCurveNode':
            cnodes[k.props[0]] = {}
    par, mprop = {}, {}
    cn_curve = {}
    for c in root.one('Connections').kids:
        t, a, b = c.props[0], c.props[1], c.props[2]
        if t == 'OO' and a in models:
            par[a] = b if b in models else None
        elif t == 'OP' and a in cnodes and b in models:
            mprop[(b, c.props[3])] = a
        elif t == 'OP' and a in curves and b in cnodes:
            cn_curve[(b, c.props[3])] = a
    ids = [i for i in models]
    # order parents first
    order, seen = [], set()
    def visit(i):
        if i in seen: return
        if par.get(i): visit(par[i])
        seen.add(i); order.append(i)
    for i in ids: visit(i)
    names = [models[i][0] for i in order]
    parents = [order.index(par[i]) if par.get(i) else -1 for i in order]
    tmax = 0
    for (tt, vv) in curves.values():
        if len(tt): tmax = max(tmax, tt[-1])
    F = int(round(tmax * 30)) + 1
    ts = np.arange(F) / 30.0
    B = len(order)
    lq = np.zeros((F, B, 4)); lt = np.zeros((F, B, 3))
    for bi, i in enumerate(order):
        pr = models[i][1]
        T0 = np.array(pr.get('Lcl Translation', [0, 0, 0]), float)
        R0 = np.array(pr.get('Lcl Rotation', [0, 0, 0]), float)
        pre = euler(pr.get('PreRotation', [0, 0, 0]))
        post = euler(pr.get('PostRotation', [0, 0, 0]))
        def track(prop, base):
            out = np.tile(base, (F, 1))
            cnid = mprop.get((i, prop))
            if cnid is None: return out
            for ax, ch in enumerate(('d|X', 'd|Y', 'd|Z')):
                cid = cn_curve.get((cnid, ch))
                if cid is None: continue
                tt, vv = curves[cid]
                out[:, ax] = np.interp(ts, tt, vv) if len(tt) > 1 else vv[0]
            return out
        T = track('Lcl Translation', T0)
        R = track('Lcl Rotation', R0)
        rr = pre * euler(R) * post.inv()
        lq[:, bi] = rr.as_quat(); lt[:, bi] = T * unit
    # forward kinematics
    gq = np.zeros_like(lq); gp = np.zeros_like(lt)
    for b in range(B):
        p = parents[b]
        if p < 0:
            gq[:, b] = lq[:, b]; gp[:, b] = lt[:, b]
        else:
            Rp = Rot.from_quat(gq[:, p])
            gq[:, b] = (Rp * Rot.from_quat(lq[:, b])).as_quat()
            gp[:, b] = gp[:, p] + Rp.apply(lt[:, b])
    # rest (Lcl Rotation 0, default translation)
    rq = np.zeros((B, 4)); rp = np.zeros((B, 3))
    for bi, i in enumerate(order):
        pr = models[i][1]
        l = euler(pr.get('PreRotation', [0, 0, 0])) * euler(pr.get('PostRotation', [0, 0, 0])).inv()
        t = np.array(pr.get('Lcl Translation', [0, 0, 0]), float) * unit
        p = parents[bi]
        if p < 0: rq[bi] = l.as_quat(); rp[bi] = t
        else:
            Rp = Rot.from_quat(rq[p]); rq[bi] = (Rp * l).as_quat(); rp[bi] = rp[p] + Rp.apply(t)
    return names, parents, rq, rp, gq.astype(np.float32), gp.astype(np.float32)


if __name__ == '__main__':
    src, out = sys.argv[1], sys.argv[2]
    data, cnames, ref = {}, [], None
    for f in sorted(os.listdir(src)):
        if not f.lower().endswith('.fbx'): continue
        names, parents, rq, rp, q, p = load(os.path.join(src, f))
        if ref is None:
            ref = names; data.update(names=np.array(names), parents=np.array(parents), rest_q=rq, rest_p=rp)
        assert names == ref, f
        i = len(cnames); cnames.append(f[:-4])
        data[f'{i}/q'] = q; data[f'{i}/p'] = p
        print(f'{f[:-4]:45s} {len(q):4d} frames  hips {p[0,0]}->{p[-1,0]}', flush=True)
    data['clip_names'] = np.array(cnames); data['fps'] = 30.0
    np.savez_compressed(out, **data)
