"""Fit the Mixamo moves onto Cortana's own skeleton, on Josiah's PC (her skeleton never leaves it).

    python -I retarget.py MIXAMO_NPZ ZSK_PATH OUT_DIR

MIXAMO_NPZ  motion-only Mixamo data (mixamo.npz, made from the FBX files in the cloud)
ZSK_PATH    her model pack: ...\\config\\swordbase\\npc\\cortana.zsk (or the .zsk.part00 pieces next to it)
OUT_DIR     gets cortana_moves.bin (her moves for the game: flat-footed and heel versions of every clip)
            and preview_motion.npz (joint positions only, a few joints, for the preview page)

Needs numpy and scipy only.
"""
import os, sys, struct, zlib, io, json
import numpy as np
from scipy.spatial.transform import Rotation as Rot
from scipy.ndimage import gaussian_filter1d


# ----------------------------------------------------------------------------------------------- her skeleton (local)
def _java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def unscramble(data, model_id):
    x = (0x3C6EF372 ^ _java_hash(model_id)) & 0xFFFFFFFF or 1
    n = (len(data) + 3) // 4
    key = np.empty(n, '>u4')
    for i in range(n):
        x ^= (x << 13) & 0xFFFFFFFF; x ^= x >> 17; x ^= (x << 5) & 0xFFFFFFFF
        key[i] = x
    k = key.tobytes()[:len(data)]
    return (np.frombuffer(data, np.uint8) ^ np.frombuffer(k, np.uint8)).tobytes()


def read_zsk(path, model_id='cortana'):
    if os.path.exists(path + '.part00') or path.endswith('.part00'):
        base = path[:-7] if path.endswith('.part00') else path
        data, i = b'', 0
        while os.path.exists(f'{base}.part{i:02d}'):
            data += open(f'{base}.part{i:02d}', 'rb').read(); i += 1
    else:
        data = open(path, 'rb').read()
    raw = zlib.decompress(unscramble(data, model_id))
    o = [0]
    def rd(fmt):
        v = struct.unpack_from('>' + fmt, raw, o[0]); o[0] += struct.calcsize('>' + fmt); return v
    def utf():
        n, = rd('H'); v = raw[o[0]:o[0] + n].decode('utf-8'); o[0] += n; return v
    assert raw[:4] == b'ZSKN'; o[0] = 4
    assert rd('i')[0] == 1
    ng, = rd('i')
    for _ in range(ng):
        utf(); utf(); utf(); rd('bb'); nv, = rd('i'); o[0] += nv * 27
        nt, = rd('i'); o[0] += nt * 12
    nx, = rd('i')
    for _ in range(nx):
        utf(); n, = rd('i'); o[0] += n
    nb, = rd('i')
    names, parents, rest = [], [], []
    for _ in range(nb):
        names.append(utf()); parents.append(rd('b')[0])
        m = np.eye(4); m[:3, :] = np.array(rd('12f')).reshape(3, 4)
        rest.append(np.linalg.inv(m))
    rest = np.array(rest)
    # make the rotations clean (the inverse bind is rigid, up to float noise)
    U, _, Vt = np.linalg.svd(rest[:, :3, :3]); R = U @ Vt
    return names, np.array(parents), Rot.from_matrix(R), rest[:, :3, 3].copy()


# ----------------------------------------------------------------------------------------------- the bone map
def build_map(H, M):
    """her bone -> (mixamo bone, her aim child, mixamo aim child, lateral pair or None). Aim None = rest orientations
    already agree (pelvis, head, toes)."""
    mp = {'b_pelvis': ('Hips', None, None)}
    sp = [b for b in ('b_spine0', 'b_spine1', 'b_spine2', 'b_spine3') if b in H]
    src = ['Spine', 'Spine1', 'Spine2', 'Spine2'] if len(sp) == 4 else ['Spine', 'Spine1', 'Spine2'][:len(sp)]
    chain = sp + [b for b in ('b_neck0', 'b_neck1', 'b_neck2') if b in H] + ['b_head']
    msrc = {'Spine': 'Spine1', 'Spine1': 'Spine2', 'Spine2': 'Neck'}
    for i, b in enumerate(sp):
        mp[b] = (src[i], chain[i + 1], msrc[src[i]])
    nk = [b for b in ('b_neck0', 'b_neck1', 'b_neck2') if b in H]
    for i, b in enumerate(nk):
        mp[b] = ('Neck', (nk + ['b_head'])[i + 1], 'Head')
    mp['b_head'] = ('Head', None, None)
    for s, S in (('l', 'Left'), ('r', 'Right')):
        lat = ((f'b_{s}_index1', f'b_{s}_pinky1'), (f'{S}HandIndex1', f'{S}HandPinky1'))
        if f'b_{s}_clav' in H:
            mp[f'b_{s}_clav'] = (f'{S}Shoulder', f'b_{s}_upperarm', f'{S}Arm')
        mp[f'b_{s}_upperarm'] = (f'{S}Arm', f'b_{s}_forearm', f'{S}ForeArm')
        mp[f'b_{s}_forearm'] = (f'{S}ForeArm', f'b_{s}_hand', f'{S}Hand')
        mp[f'b_{s}_hand'] = (f'{S}Hand', f'b_{s}_middle1', f'{S}HandMiddle1', lat)
        for fn, F in (('index', 'Index'), ('middle', 'Middle'), ('ring', 'Ring'), ('pinky', 'Pinky'), ('thumb', 'Thumb')):
            hb = [f'b_{s}_{fn}{i}' for i in range(5) if f'b_{s}_{fn}{i}' in H]
            if fn != 'thumb' and f'b_{s}_{fn}0' in H and len(hb) > 3:
                hb = hb[1:]  # a metacarpal: it stays with the hand
            mb = [f'{S}Hand{F}{i}' for i in (1, 2, 3, 4)]
            for k, b in enumerate(hb[:3]):
                mp[b] = (mb[k], hb[k + 1], mb[k + 1], lat) if k + 1 < len(hb) else (mb[k], None, None, lat)
        mp[f'b_{s}_thigh'] = (f'{S}UpLeg', f'b_{s}_calf', f'{S}Leg')
        mp[f'b_{s}_calf'] = (f'{S}Leg', f'b_{s}_foot', f'{S}Foot')
        if f'b_{s}_toe' in H:
            mp[f'b_{s}_foot'] = (f'{S}Foot', f'b_{s}_toe', f'{S}ToeBase')
            mp[f'b_{s}_toe'] = (f'{S}ToeBase', None, None)
        else:
            mp[f'b_{s}_foot'] = (f'{S}Foot', None, None)
    return {k: v for k, v in mp.items() if k in H and v[0] in M}


def frame_of(a, lat):
    a = a / np.linalg.norm(a)
    l = lat - np.dot(lat, a) * a; l /= np.linalg.norm(l)
    return np.stack([a, l, np.cross(a, l)], 1)


def rot_to(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b)
    v = np.cross(a, b); c = float(np.dot(a, b))
    if c < -0.9999:
        ax = np.cross(a, [1, 0, 0]);
        if np.linalg.norm(ax) < 1e-3: ax = np.cross(a, [0, 1, 0])
        return Rot.from_rotvec(ax / np.linalg.norm(ax) * np.pi)
    if np.linalg.norm(v) < 1e-9: return Rot.identity()
    return Rot.from_rotvec(v / np.linalg.norm(v) * np.arctan2(np.linalg.norm(v), c))


# ----------------------------------------------------------------------------------------------- retarget one clip
class Fit:
    def __init__(self, her, mx):
        self.names, self.par, self.rq, self.rp = her
        self.H = {n: i for i, n in enumerate(self.names)}
        self.mn = [str(n) for n in mx['names']]
        self.M = {n: i for i, n in enumerate(self.mn)}
        self.mrq = Rot.from_quat(mx['rest_q']); self.mrp = mx['rest_p']
        H, M = self.H, self.M
        # which way she faces in her own model space (left hip minus right hip, crossed with up)
        side = self.rp[H['b_l_thigh']] - self.rp[H['b_r_thigh']]
        fwd = np.cross(side, [0, 1, 0]); fwd[1] = 0; fwd /= np.linalg.norm(fwd)
        self.face = Rot.from_rotvec([0, np.arctan2(fwd[0], fwd[2]), 0])  # +Z (Mixamo) -> her forward
        mrp = self.face.apply(self.mrp); mrq = self.face * self.mrq
        self.mrq_f, self.mrp_f = mrq, mrp
        self.map = build_map(H, M)
        self.A = {}
        for b, e in self.map.items():
            m = e[0]
            if e[1] is None:
                if len(e) > 3 and e[3] is not None and b != 'b_pelvis':
                    # finger tip: use the alignment of its parent joint
                    pb = self.names[self.par[H[b]]]
                    self.A[b] = self.A.get(pb, Rot.identity()); continue
                self.A[b] = Rot.identity(); continue
            dh = self.rp[H[e[1]]] - self.rp[H[b]]
            dm = mrp[M[e[2]]] - mrp[M[m]]
            if len(e) > 3 and e[3] is not None:
                (h1, h2), (m1, m2) = e[3]
                if h1 in H and m1 in M:
                    Fh = frame_of(dh, self.rp[H[h1]] - self.rp[H[h2]])
                    Fm = frame_of(dm, mrp[M[m1]] - mrp[M[m2]])
                    self.A[b] = Rot.from_matrix(Fm @ Fh.T); continue
            self.A[b] = rot_to(dh, dm)
        self.order = sorted(range(len(self.names)), key=self.depth)
        # leg lengths for scale
        def leg_h(P, th, ca, ft):
            return np.linalg.norm(P[th] - P[ca]) + np.linalg.norm(P[ca] - P[ft])
        lh = leg_h(self.rp, H['b_l_thigh'], H['b_l_calf'], H['b_l_foot'])
        lm = leg_h(self.mrp, M['LeftUpLeg'], M['LeftLeg'], M['LeftFoot'])
        self.scale = lh / lm
        self.missing = [n for n in self.names if n not in self.map]
        # a toe point per foot: her toe joint, or a point under the ball of her foot when she has none
        self.toe_local = []
        for s in ('l', 'r'):
            ft = H[f'b_{s}_foot']
            if f'b_{s}_toe' in H:
                v = self.rp[H[f'b_{s}_toe']] - self.rp[ft]
            else:
                v = self.face.apply([0, -0.8 * self.rp[ft][1], 0.14 * self.scale])
            self.toe_local.append((ft, self.rq[ft].inv().apply(v)))
        self.rest_ty = min(self.rp[ft][1] + (self.rq[ft].apply(l))[1] for ft, l in self.toe_local)
        self.feet = [H['b_l_foot'], H['b_r_foot']]

    def depth(self, b):
        d = 0
        while self.par[b] >= 0: b = self.par[b]; d += 1
        return d

    def root_track(self, q, p):
        M = self.M
        hips = p[:, M['Hips']]
        side = p[:, M['LeftUpLeg']] - p[:, M['RightUpLeg']]
        yaw = np.unwrap(np.arctan2(-side[:, 2], side[:, 0]))  # facing angle about +Y, 0 = +Z
        S = gaussian_filter1d(hips[:, [0, 2]], 8, axis=0, mode='nearest')
        Y = gaussian_filter1d(yaw, 8, mode='nearest')
        net = np.linalg.norm(S[-1] - S[0])
        path = np.sum(np.linalg.norm(np.diff(S, axis=0), axis=1))
        travel = net > 0.3 or abs(np.degrees(Y[-1] - Y[0])) > 30 or path > 1.2
        feet = 0.5 * (p[0, M['LeftFoot']] + p[0, M['RightFoot']])
        if travel:
            rx, ry = S - S[0], Y  # undo the facing frame by frame
        else:
            rx, ry = np.zeros_like(S), np.full_like(Y, np.mean(Y))  # stand square to the entity
        return travel, feet[[0, 2]], rx, ry

    def clip(self, q, p, heels):
        """q (F,Bm,4), p (F,Bm,3) Mixamo world -> her model-space (F,B,4), (F,B,3), root (F,3), info"""
        F = len(q); H, M = self.H, self.M
        travel, c0, rx, ry = self.root_track(q, p)
        # into the root's frame: she stays at the origin, the root track carries the travel and turning
        undo = Rot.from_rotvec(np.outer(-ry, [0, 1, 0]))
        off = np.zeros((F, 3)); off[:, 0] = c0[0] + rx[:, 0]; off[:, 2] = c0[1] + rx[:, 1]
        pl = np.empty_like(p)
        for b in range(p.shape[1]):
            pl[:, b] = undo.apply(p[:, b] - off)
        # to her facing
        face = self.face
        D = {}
        for b, e in self.map.items():
            m = M[e[0]]
            Qm = face * undo * Rot.from_quat(q[:, m])
            D[b] = Qm * self.mrq_f[m].inv() * self.A[b]  # world change applied to her rest bone
        nb = len(self.names)
        G = [None] * nb
        outq = np.zeros((F, nb, 4)); outp = np.zeros((F, nb, 3))
        hips = face.apply(pl[:, M['Hips']])
        for b in self.order:
            nm = self.names[b]; pa = self.par[b]
            if nm in D:
                G[b] = D[nm]
            elif pa >= 0 and G[pa] is not None:
                G[b] = self.helper(nm, b, G)
            else:
                G[b] = Rot.from_quat(np.tile([0, 0, 0, 1.0], (F, 1)))
            if nm == 'b_pelvis':
                outp[:, b] = self.rp[b] + self.scale * (hips - self.mrp_f[M['Hips']])
            elif pa >= 0:
                outp[:, b] = outp[:, pa] + G[pa].apply(self.rp[b] - self.rp[pa])
            else:
                outp[:, b] = self.rp[b]
            outq[:, b] = (G[b] * self.rq[b]).as_quat()
        self.G = G
        delta = 0.0
        if not heels:
            delta = self.flatten(outq, outp, G)
        # ground: planted toes at their rest height
        ty = np.min(self.toe_pts(outq, outp)[:, :, 1], axis=1)
        planted = ty < np.percentile(ty, 30) + 0.02
        lift = self.rest_ty - np.median(ty[planted])
        outp[:, :, 1] += lift
        # quaternion signs continuous
        for f in range(1, F):
            flip = (outq[f] * outq[f - 1]).sum(1) < 0
            outq[f, flip] *= -1
        root = np.zeros((F, 3))
        start = Rot.from_rotvec([0, -ry[0], 0])
        rxz = face.apply(start.apply(np.c_[rx[:, 0], np.zeros(F), rx[:, 1]])) * self.scale
        root[:, 0] = rxz[:, 0]; root[:, 1] = rxz[:, 2]; root[:, 2] = ry - ry[0]
        speed = self.speed(outq, outp) if not travel else 0.0
        return outq, outp, root, dict(travel=bool(travel), heel_deg=float(np.degrees(delta)), lift=float(lift), speed=speed)

    def helper(self, nm, b, G):
        pa = self.par[b]; H = self.H
        for s in ('l', 'r'):
            if nm.startswith(f'b_{s}_forearm_helper') or nm.startswith(f'b_{s}_wrist') or nm.startswith(f'b_{s}_arm'):
                fa, hd = H.get(f'b_{s}_forearm'), H.get(f'b_{s}_hand')
                if fa is not None and hd is not None and G[fa] is not None and self.G_ready(hd, G):
                    L = np.linalg.norm(self.rp[hd] - self.rp[fa]) + 1e-9
                    ax = (self.rp[hd] - self.rp[fa]) / L
                    frac = float(np.clip(np.dot(self.rp[b] - self.rp[fa], ax) / L, 0, 1)) if nm != f'b_{s}_wrist' else 0.9
                    return slerp(G[fa], self.Dget(f'b_{s}_hand'), frac if frac > 0.05 else 0.5)
            for a_, c_ in ((f'b_{s}_upperarm', f'b_{s}_forearm'), (f'b_{s}_thigh', f'b_{s}_calf')):
                if nm in (f'b_{s}_elbow_helper', f'b_{s}_knee_helper') and a_ in H and c_ in H:
                    if (nm.endswith('elbow_helper') and 'arm' in a_) or (nm.endswith('knee_helper') and 'thigh' in a_):
                        return slerp(self.Dget(a_), self.Dget(c_), 0.5)
        return G[pa]

    def G_ready(self, b, G):
        return True

    def Dget(self, nm):
        return self._D[nm]

    def toe_pts(self, outq, outp):
        return np.stack([outp[:, ft] + Rot.from_quat(outq[:, ft]).apply(l) for ft, l in self.toe_local], 1)

    def flatten(self, outq, outp, G):
        """take the heel lift out: tip each foot back by the clip's planted heel angle; toes keep their own turn"""
        tp = self.toe_pts(outq, outp)
        deltas = []
        for k, (ft, l) in enumerate(self.toe_local):
            v = tp[:, k] - outp[:, ft]
            pitch = np.arctan2(-v[:, 1], np.linalg.norm(v[:, [0, 2]], axis=1))
            vr = self.rq[ft].apply(l)
            p0 = np.arctan2(-vr[1], np.linalg.norm(vr[[0, 2]]))
            ty = tp[:, k, 1]
            planted = ty < np.min(ty) + 0.03
            deltas.append(np.median(pitch[planted]) - p0)
        delta = float(np.clip(np.mean(deltas), 0, np.radians(40)))
        if delta < np.radians(2): return 0.0
        for k, (ft, l) in enumerate(self.toe_local):
            v = tp[:, k] - outp[:, ft]
            lat = np.cross(v, [0, 1, 0]); lat /= np.linalg.norm(lat, axis=1, keepdims=True) + 1e-9
            R = Rot.from_rotvec(lat * delta)  # turn about the ankle so the toes come up by delta
            if np.mean(R.apply(v)[:, 1]) < np.mean(v[:, 1]):
                R = Rot.from_rotvec(-lat * delta)
            outq[:, ft] = (R * G[ft] * self.rq[ft]).as_quat()
            for b in [b for b in self.order if self.below(b, ft) and b != ft]:
                outp[:, b] = outp[:, ft] + R.apply(outp[:, b] - outp[:, ft])  # toes keep their own turn
        return delta

    def below(self, b, a):
        while b >= 0:
            if b == a: return True
            b = self.par[b]
        return False

    def speed(self, outq, outp):
        """in-place loops: how fast the planted foot slides back = how fast she must walk to keep her feet still"""
        tp = self.toe_pts(outq, outp); v = []
        for k in range(2):
            z = tp[:, k]; ty = z[:, 1]
            planted = ty < np.min(ty) + 0.02
            vel = np.diff(z[:, [0, 2]], axis=0) * 30
            pl = planted[1:] & planted[:-1]
            if pl.sum() > 3: v.append(np.median(np.linalg.norm(vel[pl], axis=1)))
        return float(np.mean(v)) if v else 0.0


def slerp(Ra, Rb, w):
    return Ra * Rot.from_rotvec((Ra.inv() * Rb).as_rotvec() * w)


# ----------------------------------------------------------------------------------------------- main
PREVIEW = ['b_pelvis', 'b_spine1', 'b_spine3', 'b_neck0', 'b_head', 'b_l_clav', 'b_l_upperarm', 'b_l_forearm', 'b_l_hand',
           'b_l_middle1', 'b_l_index1', 'b_l_thumb2', 'b_r_clav', 'b_r_upperarm', 'b_r_forearm', 'b_r_hand', 'b_r_middle1',
           'b_r_index1', 'b_r_thumb2', 'b_l_thigh', 'b_l_calf', 'b_l_foot', 'b_l_toe', 'b_r_thigh', 'b_r_calf',
           'b_r_foot', 'b_r_toe']


def run(mx_path, her, out_dir, only=None):
    mx = np.load(mx_path)
    fit = Fit(her, mx)
    print('scale', round(fit.scale, 3), 'facing', np.round(fit.face.as_rotvec(), 3), 'unmapped', len(fit.missing))
    names = [str(n) for n in mx['clip_names']]
    os.makedirs(out_dir, exist_ok=True)
    buf = io.BytesIO(); W = lambda f, *v: buf.write(struct.pack('>' + f, *v))
    def utf(s):
        b = s.encode(); W('H', len(b)); buf.write(b)
    jobs = [(i, n, h) for i, n in enumerate(names) for h in (False, True) if only is None or n in only]
    buf.write(b'ZMOV'); W('i', 1); W('i', len(fit.names))
    for n in fit.names: utf(n)
    W('i', len(jobs))
    prev, info = {}, {}
    pv = [fit.H[n] for n in PREVIEW if n in fit.H]
    for i, n, heels in jobs:
        q, p = mx[f'{i}/q'], mx[f'{i}/p']
        fit._D = {}
        # helper bones need the mapped turns first
        fit._D = _mapped(fit, q, p)
        oq, op, root, inf = fit.clip(q, p, heels)
        tag = 'heel' if heels else 'flat'
        utf(n); utf(tag); W('i', len(oq)); W('f', 30.0); W('f', inf['speed']); W('b', 1 if inf['travel'] else 0)
        buf.write(root.astype('>f4').tobytes())
        qq = np.round(oq * 32767).astype('>i2'); pp = np.round(np.clip(op, -32, 32) * 1000).astype('>i2')
        buf.write(np.concatenate([qq, pp], -1).astype('>i2').tobytes())
        prev[f'{n}|{tag}|p'] = op[:, pv].astype(np.float16); prev[f'{n}|{tag}|root'] = root.astype(np.float32)
        info[f'{n}|{tag}'] = inf
        print(f'{n:42s} {tag} {len(oq):5d}f travel={inf["travel"]!s:5} heel={inf["heel_deg"]:5.1f} lift={inf["lift"]:+.3f} speed={inf["speed"]:.2f}', flush=True)
    open(os.path.join(out_dir, 'cortana_moves.bin'), 'wb').write(zlib.compress(buf.getvalue(), 6))
    par = [PREVIEW.index(fit.names[fit.par[b]]) if fit.par[b] >= 0 and fit.names[fit.par[b]] in PREVIEW else -1 for b in pv]
    # parents of the preview joints by nearest preview ancestor
    par = []
    for b in pv:
        a = fit.par[b]
        while a >= 0 and fit.names[a] not in PREVIEW: a = fit.par[a]
        par.append(PREVIEW.index(fit.names[a]) if a >= 0 else -1)
    np.savez_compressed(os.path.join(out_dir, 'preview_motion.npz'), joints=np.array([fit.names[b] for b in pv]),
                        parents=np.array(par), info=json.dumps(info), **prev)
    print('wrote', out_dir)


def _mapped(fit, q, p):
    """the world turns of the mapped bones (the helpers between them blend these)"""
    F = len(q); M = fit.M
    travel, c0, rx, ry = fit.root_track(q, p)
    undo = Rot.from_rotvec(np.outer(-ry, [0, 1, 0]))
    D = {}
    for b, e in fit.map.items():
        m = M[e[0]]
        D[b] = fit.face * undo * Rot.from_quat(q[:, m]) * fit.mrq_f[m].inv() * fit.A[b]
    return D


if __name__ == '__main__':
    mxp, zsk, out = sys.argv[1:4]
    run(mxp, read_zsk(zsk), out, set(sys.argv[4].split(',')) if len(sys.argv) > 4 else None)
