# Reload animator: hand keys (on the gun or on the body), two-bone arm reach, moving / grabbed / dropped parts.
# Writes one data file per gun for the three.js page (first person + third person), same maths as the game poses.
import sys, os, io, json, base64, numpy as np
FP = '/tmp/claude-0/-home-user-zomboidcraft/7dc1898d-1e2b-5f6e-821d-93dc18f2b5c4/scratchpad/fpport'
sys.path.insert(0, FP); sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
_cwd = os.getcwd(); os.chdir(FP)
import gameview as gv, full_export as fe, clips as clipmod
os.chdir(_cwd)
from sel import gun_tris, select
POSES = '/mnt/project-files/zomboid-mod/brita/poses/game-v2'
TOOLG = FP + '/tool/guns'
FIST = json.load(open(FP + '/tool/arms.json'))['fists']
MODEL = 'pz_survivor_f'
NAMES, PAR, INV, CLIPS = clipmod.load(FP + '/../f.zsk', MODEL)
_cwd = os.getcwd(); os.chdir(FP)
_, INV_, BP, BJ, BW, TR_ARM, SIDE = gv.pack(MODEL)
os.chdir(_cwd)
BODY = fe.body; TR_BODY = BODY['tris']; BUV = BODY['uv']
NB = len(NAMES); SPINE = NAMES.index('Bip01_Spine1')
FING = gv.FING

# ---------------------------------------------------------------- small maths
def qmat(q): return gv.qmat(q)
def mat(p, R): m = np.eye(4); m[:3, :3] = R; m[:3, 3] = p; return m
def rx(a): a = np.radians(a); c, s = np.cos(a), np.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def ry(a): a = np.radians(a); c, s = np.cos(a), np.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rz(a): a = np.radians(a); c, s = np.cos(a), np.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
def eul(r): r = r or [0, 0, 0]; return ry(r[1]) @ rx(r[0]) @ rz(r[2])       # pitch, yaw, roll (deg)
def rot_to(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b); v = np.cross(a, b); c = np.dot(a, b)
    if c < -0.99999:
        p = np.cross(a, [1, 0, 0]); p = p if np.linalg.norm(p) > 1e-6 else np.cross(a, [0, 1, 0]); p /= np.linalg.norm(p)
        return 2 * np.outer(p, p) - np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)
def axis_rot(ax, deg):
    ax = np.asarray(ax, float); ax = ax / np.linalg.norm(ax); a = np.radians(deg); K = np.array([[0, -ax[2], ax[1]], [ax[2], 0, -ax[0]], [-ax[1], ax[0], 0]])
    return np.eye(3) + np.sin(a) * K + (1 - np.cos(a)) * K @ K
def r2q(R):
    t = np.trace(R)
    if t > 0: s = np.sqrt(t + 1) * 2; return np.array([(R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s, s / 4])
    i = np.argmax(np.diag(R))
    if i == 0: s = np.sqrt(1 + R[0, 0] - R[1, 1] - R[2, 2]) * 2; return np.array([s / 4, (R[0, 1] + R[1, 0]) / s, (R[0, 2] + R[2, 0]) / s, (R[2, 1] - R[1, 2]) / s])
    if i == 1: s = np.sqrt(1 + R[1, 1] - R[0, 0] - R[2, 2]) * 2; return np.array([(R[0, 1] + R[1, 0]) / s, s / 4, (R[1, 2] + R[2, 1]) / s, (R[0, 2] - R[2, 0]) / s])
    s = np.sqrt(1 + R[2, 2] - R[0, 0] - R[1, 1]) * 2; return np.array([(R[0, 2] + R[2, 0]) / s, (R[1, 2] + R[2, 1]) / s, s / 4, (R[1, 0] - R[0, 1]) / s])
def slerp(Ra, Rb, u):
    a, b = r2q(Ra), r2q(Rb)
    if np.dot(a, b) < 0: b = -b
    d = np.clip(np.dot(a, b), -1, 1)
    if d > 0.9995: q = a + u * (b - a)
    else: th = np.arccos(d); q = (np.sin((1 - u) * th) * a + np.sin(u * th) * b) / np.sin(th)
    return qmat(q / np.linalg.norm(q))
def basis(fwd, palm, s):
    """hand rotation from where its fingers point (local x) and where its palm faces (local -z)"""
    x = np.array(fwd, float); x /= np.linalg.norm(x); z = -np.array(palm, float); z -= x * np.dot(z, x); z /= np.linalg.norm(z)
    y = np.cross(z, x); return np.stack([x, y, z], 1)
def ease(u): u = min(1, max(0, u)); return u * u * (3 - 2 * u)

def track(keys, t):
    """keys: [(time, value)] -> (a, b, u) neighbours with eased blend"""
    if t <= keys[0][0]: return keys[0][1], keys[0][1], 0.0
    for (ta, a), (tb, b) in zip(keys, keys[1:]):
        if t <= tb: return a, b, ease((t - ta) / max(1e-6, tb - ta))
    return keys[-1][1], keys[-1][1], 0.0
def lerpv(a, b, u, k, d):
    return (1 - u) * np.array(a.get(k, d), float) + u * np.array(b.get(k, d), float)

# ---------------------------------------------------------------- one view (first or third person) of one gun
class Rig:
    def __init__(self, gid, view):
        self.gid, self.view = gid, view
        P = json.load(open(f'{POSES}/{gid}.json'))
        p = P['hip'] if view == 'fp' else P['tpready']
        g = p['gun']; self.scale = g['scale']
        self.Rg0 = qmat(g['q']); self.tg0 = np.array(g['t'])
        self.grip = np.array(json.load(open(f'{TOOLG}/{gid}.json'))['grip'], float)
        if view == 'fp':
            self.bones = {k: mat(np.array(v[:3]), qmat(v[3:])) for k, v in p['bones'].items()}
            self.pre = lambda s, part: f'{s}_{part}'
            self.place = mat(np.array(gv.ARM), np.eye(3))                 # hand frame -> eye
        else:
            c = CLIPS[p['clip']]; self.clipm = [mat(c['p'][0, b], qmat(c['q'][0, b])) for b in range(NB)]
            self.bones = {k: mat(np.array(v[:3]), qmat(v[3:])) for k, v in p['bones'].items()}
            self.pre = lambda s, part: f'Bip01_{s}_{part}'
            self.place = self.clipm[SPINE]                                   # Spine1 frame -> her model space
        self.H0 = {s: self.bones[self.pre(s, 'Hand')] for s in 'RL'}
        self.palm0 = {s: self.palm(self.H0[s], s) for s in 'RL'}
    def palm(self, H, s): return H[:3, 3] + H[:3, :3] @ np.array(FIST[0 if s == 'R' else 1])
    # px -> rig for a gun at (R, t)
    def G(self, R, t):
        return mat(t, R * self.scale) @ mat(np.array([-0.5] * 3), np.eye(3)) @ mat(np.zeros(3), np.eye(3) / 16)
    def gun_key(self, k):
        """(R, t) of the gun for one key: relative (r, d about the grip, gun axes) or abs (grip, fwd, up in her model space / eye)"""
        k = {**k, **k.get(self.view, {})}
        G0 = self.G(self.Rg0, self.tg0); gw = (G0 @ np.append(self.grip, 1))[:3]
        if 'abs' in k:
            A = k['abs']; Pi = np.linalg.inv(self.place)
            z = -np.array(A['fwd'], float); z /= np.linalg.norm(z); y = np.array(A['up'], float); y -= z * np.dot(y, z); y /= np.linalg.norm(y)
            R = Pi[:3, :3] @ np.stack([np.cross(y, z), y, z], 1) @ eul(k.get('r'))
            gw2 = (Pi @ np.append(A['grip'], 1))[:3] + R @ np.array(k.get('d', [0, 0, 0]))
        else:
            R = self.Rg0 @ eul(k.get('r')); gw2 = gw + self.Rg0 @ np.array(k.get('d', [0, 0, 0]))
        t = gw2 - (self.G(R, np.zeros(3)) @ np.append(self.grip, 1))[:3]
        return R, t, gw2
    def gun_at(self, key_a, key_b, u):
        Ra, ta, ga = self.gun_key(key_a); Rb, tb, gb = self.gun_key(key_b)
        R = slerp(Ra, Rb, u); g = (1 - u) * ga + u * gb
        t = g - (self.G(R, np.zeros(3)) @ np.append(self.grip, 1))[:3]
        return R, t
    def hand_key(self, s, k, R, t):
        """world hand matrix for one key; R,t = current gun"""
        k = dict(k); k.update(k.get(self.view, {}))
        H0 = self.H0[s]; Gm = self.G(R, t); G0 = self.G(self.Rg0, self.tg0)
        kind = k.get('k', 'rest')
        if kind in ('rest', 'gun'):
            Rh = R @ eul(k.get('r')) @ self.Rg0.T @ H0[:3, :3]
            if 'fwd' in k: Rh = R @ basis(k['fwd'], k['palm'], s) @ eul(k.get('r2'))
            if kind == 'gun' and 'p' in k: palm = (Gm @ np.append(k['p'], 1))[:3]
            else:
                ppx = np.linalg.inv(G0) @ np.append(self.palm0[s], 1)
                ppx[:3] += np.array(k.get('dpx', [0, 0, 0]))
                palm = (Gm @ ppx)[:3]
            palm = palm + R @ np.array(k.get('d', [0, 0, 0]))
        else:                                                    # on her body: her model space (third) / eye (first)
            Pi = np.linalg.inv(self.place)
            Rh = Pi[:3, :3] @ eul(k.get('r')) @ self.place[:3, :3] @ H0[:3, :3]
            if 'fwd' in k: Rh = Pi[:3, :3] @ basis(k['fwd'], k['palm'], s) @ eul(k.get('r2'))
            palm = (Pi @ np.append(k['p'], 1))[:3] if 'p' in k else self.palm0[s] + Pi[:3, :3] @ np.array(k.get('d', [0, 0, 0]))
        wrist = palm - Rh @ np.array(FIST[0 if s == 'R' else 1])
        return mat(wrist, Rh), k.get('curl', 0.0)
    def pose_arm(self, s, Ht, curl, out):
        """two-bone reach from the shoulder, then the hand and its fingers carried along"""
        n = lambda part: self.pre(s, part)
        U, F, H = self.bones[n('UpperArm')], self.bones[n('Forearm')], self.bones[n('Hand')]
        sh, el, wr = U[:3, 3], F[:3, 3], H[:3, 3]
        a, b = np.linalg.norm(el - sh), np.linalg.norm(wr - el)
        tgt = Ht[:3, 3]; to = tgt - sh; d = np.clip(np.linalg.norm(to), abs(a - b) + 1e-3, a + b - 1e-3); dirv = to / np.linalg.norm(to)
        bend = (el - sh) - dirv * np.dot(el - sh, dirv)
        if np.linalg.norm(bend) < 1e-6: bend = np.array([0, -1, 0.0])
        bend /= np.linalg.norm(bend)
        cs = np.clip((a * a + d * d - b * b) / (2 * a * d), -1, 1); el2 = sh + dirv * a * cs + bend * a * np.sqrt(1 - cs * cs)
        wr2 = sh + dirv * d
        Ru = rot_to(el - sh, el2 - sh)
        U2 = mat(sh, Ru @ U[:3, :3])
        f0 = Ru @ (wr - el)
        Rf = rot_to(f0, wr2 - el2) @ Ru
        Fr = Rf @ F[:3, :3]
        # share the hand's twist about the forearm line with the forearm (half), so the wrist does not wring
        ax = (wr2 - el2) / np.linalg.norm(wr2 - el2)
        Hr_free = Rf @ H[:3, :3]; Hr = Ht[:3, :3]
        rel = Hr @ Hr_free.T
        q = r2q(rel); tw = 2 * np.degrees(np.arctan2(np.dot(q[:3], ax), q[3]))
        tw = (tw + 180) % 360 - 180
        Fr = axis_rot(ax, tw * 0.5) @ Fr
        F2 = mat(el2, Fr)
        H2 = mat(wr2, Hr)
        out[n('UpperArm')] = U2; out[n('Forearm')] = F2; out[n('Hand')] = H2
        D = H2 @ np.linalg.inv(H)
        for part in ['Finger0', 'Finger1'] + [f'Finger1_{f}' for f in FING]:
            if n(part) not in self.bones: continue
            M = D @ self.bones[n(part)]
            if part.startswith('Finger1') and curl:
                M = mat(M[:3, 3], axis_rot(M[:3, 1], curl) @ M[:3, :3])
            out[n(part)] = M
    def skin(self, bones):
        """skin matrices (n+8) for the pack: first person = arm pieces only (others to the shoulder), third = whole body"""
        m = [None] * (NB + 8)
        if self.view == 'fp':
            sh = {}
            for s, sd in enumerate('RL'):
                sh[s] = bones[f'{sd}_UpperArm'][:3, 3]
                for part in ['UpperArm', 'Forearm', 'Hand', 'Finger0', 'Finger1']:
                    bi = NAMES.index(f'Bip01_{sd}_{part}'); m[bi] = bones[f'{sd}_{part}'] @ INV_[bi]
                f1 = NAMES.index(f'Bip01_{sd}_Finger1')
                for k in range(4): m[NB + s * 4 + k] = bones.get(f'{sd}_Finger1_{FING[k]}', bones[f'{sd}_Finger1']) @ INV_[f1]
            for bi in range(NB + 8):
                if m[bi] is None: z = np.zeros((4, 4)); z[:3, 3] = sh[SIDE[bi]]; z[3, 3] = 1; m[bi] = z
            return [self.place @ x for x in m]
        S = self.place
        for b in range(NB):
            m[b] = (S @ bones[NAMES[b]] if NAMES[b] in bones else self.clipm[b]) @ INV[b]
        for s, sd in enumerate('RL'):
            f1 = NAMES.index(f'Bip01_{sd}_Finger1')
            for k in range(4): m[NB + s * 4 + k] = S @ bones.get(f'Bip01_{sd}_Finger1_{FING[k]}', bones[f'Bip01_{sd}_Finger1']) @ INV[f1]
        return m

# ---------------------------------------------------------------- the whole clip
GRAV = 9.8
WARN = {}
def run(gid, spec, fps=30):
    parts_raw, tex, png = gun_tris(gid)
    groups = {}
    taken = [np.zeros(len(p['pos']), bool) for p in parts_raw]
    for name, g in spec.get('parts', {}).items():
        if 'clone' in g: continue
        ms = select(parts_raw, g['sel'])
        for i, m in enumerate(ms): m &= ~taken[i]; taken[i] |= m
        groups[name] = ms
    groups['_body'] = [~t for t in taken]
    geo = {}
    for name, ms in groups.items():
        pos = np.concatenate([p['pos'][m] for p, m in zip(parts_raw, ms)]).reshape(-1, 3)
        uv = np.concatenate([p['uv'][m] for p, m in zip(parts_raw, ms)]).reshape(-1, 2)
        geo[name] = (pos, uv)
    for name, g in spec.get('parts', {}).items():
        if 'clone' in g: geo[name] = geo[g['clone']]
    extra = {k: build_prims(v['prims']) for k, v in spec.get('extra', {}).items()}   # procedural pieces (shells, rounds)
    out = {'gid': gid, 'fps': fps, 'dur': spec['dur'], 'tex': base64.b64encode(png).decode(), 'groups': {}, 'views': {}}
    for name, (pos, uv) in geo.items():
        out['groups'][name] = {'p': b64(pos), 'u': b64(uv)}
    for name, (pos, col) in extra.items():
        out['groups'][name] = {'p': b64(pos), 'c': b64(col)}; XGEO[name] = pos.mean(0)
    nf = int(round(spec['dur'] * fps)) + 1
    for view in ('fp', 'tp'):
        rig = Rig(gid, view)
        state = {}
        for name, g in list(spec.get('parts', {}).items()) + list(spec.get('extra', {}).items()):
            state[name] = {'parent': 'hidden' if g.get('hidden') else 'gun', 'rel': np.eye(4), 'last': None, 'vel': np.zeros(3)}
        state['_body'] = {'parent': 'gun', 'rel': np.eye(4)}
        evs = sorted(spec.get('events', []), key=lambda e: e[0]); ei = 0
        frames = []
        grabbed = {}
        for f in range(nf):
            t = f / fps
            a, b, u = track(spec.get('gun', [(0, {})]), t); R, tt = rig.gun_at(a, b, u)
            Gm = rig.G(R, tt)
            hands = {}
            for s in 'RL':
                keys = spec.get(s, [(0, {'k': 'rest'})])
                a, b, u = track(keys, t)
                Ha, ca = rig.hand_key(s, a, R, tt); Hb, cb = rig.hand_key(s, b, R, tt)
                Ht = mat((1 - u) * Ha[:3, 3] + u * Hb[:3, 3], slerp(Ha[:3, :3], Hb[:3, :3], u))
                hands[s] = (Ht, (1 - u) * ca + u * cb)
            bones = dict(rig.bones)
            for s in 'RL': rig.pose_arm(s, hands[s][0], hands[s][1], bones)
            Hn = {s: bones[rig.pre(s, 'Hand')] for s in 'RL'}
            for s in 'RL':
                miss = np.linalg.norm(Hn[s][:3, 3] - hands[s][0][:3, 3])
                if miss > 0.02: WARN.setdefault((view, s), []).append((round(t, 2), round(float(miss), 3)))
            # local part motion on the gun (px, about a pivot)
            local = lambda name: local_t(spec, name, t)
            while ei < len(evs) and evs[ei][0] <= t + 1e-9:
                e = evs[ei]; ei += 1; st = state[e[1]]; cur = st.get('world')
                if e[2] == 'grab':
                    W = cur if cur is not None else Gm @ local(e[1])
                    st['parent'] = e[3]; st['rel'] = np.linalg.inv(Hn[e[3]]) @ W; grabbed[e[1]] = (e[3], st['rel'])
                elif e[2] == 'show':
                    hand, src = e[3], e[4]
                    st['parent'] = hand
                    if src == '@':                       # sits in the hand as it will when seated at time e[5]
                        ts = e[5]; Hs, Gs = target_at(rig, spec, hand, ts)
                        st['rel'] = np.linalg.inv(Hs) @ Gs @ local_t(spec, e[1], ts)
                        e = e[:5]
                    else: st['rel'] = grabbed[src][1] if src in grabbed else np.eye(4)
                    if len(e) > 5: st['rel'] = st['rel'] @ mat(np.array(e[5]), np.eye(3))
                elif e[2] == 'drop':
                    st['parent'] = 'free'; st['spin'] = np.array(e[3] if len(e) > 3 else [200, 0, 90])
                    v = st['vel'] * 0.35; sp = np.linalg.norm(v)
                    st['vel'] = (v / sp * min(sp, 0.8) if sp > 1e-6 else v) + rig.place[:3, :3].T @ np.array([0, -0.3, 0])
                elif e[2] == 'seat':
                    st['parent'] = 'gun'
                elif e[2] == 'hide':
                    st['parent'] = 'hidden'
            mats = {}
            for name, st in state.items():
                if st['parent'] == 'gun': W = Gm @ local(name)
                elif st['parent'] in ('L', 'R'): W = Hn[st['parent']] @ st['rel']
                elif st['parent'] == 'free':
                    W = st['world'].copy(); dt = 1 / fps
                    st['vel'] = st['vel'] + rig.place[:3, :3].T @ np.array([0, -GRAV, 0]) * dt
                    c = (W @ np.append(geo_centre(geo, name), 1))[:3]
                    Rspin = axis_rot([1, 0, 0], st['spin'][0] * dt) @ axis_rot([0, 0, 1], st['spin'][2] * dt)
                    W = mat(c + st['vel'] * dt, np.eye(3)) @ mat(np.zeros(3), Rspin) @ mat(-c, np.eye(3)) @ W
                else: W = None
                if W is not None and st.get('world') is not None and st['parent'] != 'free':
                    st['vel'] = (W[:3, 3] - st['world'][:3, 3]) * fps
                st['world'] = W
                if W is not None: mats[name] = (rig.place @ W).T.flatten().tolist()
            skin = rig.skin(bones)
            frames.append({'g': {k: [round(x, 5) for x in v] for k, v in mats.items()}, 'b': b64(np.stack([m.T for m in skin]).reshape(-1))})
        out['views'][view] = frames
    return out
def target_at(rig, spec, s, t):
    a, b, u = track(spec.get('gun', [(0, {})]), t); R, tt = rig.gun_at(a, b, u)
    a, b, u = track(spec.get(s, [(0, {'k': 'rest'})]), t)
    Ha, _ = rig.hand_key(s, a, R, tt); Hb, _ = rig.hand_key(s, b, R, tt)
    return mat((1 - u) * Ha[:3, 3] + u * Hb[:3, 3], slerp(Ha[:3, :3], Hb[:3, :3], u)), rig.G(R, tt)
def local_t(spec, name, t):
    mv = spec.get('moves', {}).get(name)
    if not mv: return np.eye(4)
    a, b, u = track(mv, t)
    d = lerpv(a, b, u, 'd', [0, 0, 0]); r = lerpv(a, b, u, 'r', [0, 0, 0]); sc = lerpv(a, b, u, 's', [1, 1, 1])
    piv = np.array(spec.get('parts', {}).get(name, spec.get('extra', {}).get(name, {})).get('pivot', a.get('pivot', [0, 0, 0])), float)
    return mat(d, np.eye(3)) @ mat(piv, np.eye(3)) @ mat(np.zeros(3), eul(list(r)) @ np.diag(sc)) @ mat(-piv, np.eye(3))
def geo_centre(geo, name):
    if name in geo: return geo[name][0].mean(0)
    if name in XGEO: return XGEO[name]
    return np.zeros(3)
XGEO = {}
def frame_of(axis):
    z = np.array(axis, float); z /= np.linalg.norm(z); a = np.array([1, 0, 0]) if abs(z[0]) < 0.9 else np.array([0, 1, 0])
    x = np.cross(a, z); x /= np.linalg.norm(x); y = np.cross(z, x); return np.stack([x, y, z], 1)
def build_prims(prims):
    """box: c, s (size), axis (its z), col; cyl: a, b, r, col, n"""
    T = []; C = []
    for p in prims:
        col = np.array(p['col'], float) / 255
        if p['t'] == 'box':
            R = frame_of(p.get('axis', [0, 0, 1])) if 'axis' in p else np.eye(3); h = np.array(p['s']) / 2; c = np.array(p['c'])
            V = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]) * h
            V = V @ R.T + c
            F = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
            for a, b, cc, d in F: T += [V[[a, b, cc]], V[[a, cc, d]]]; C += [col, col]
        else:
            a, b = np.array(p['a'], float), np.array(p['b'], float); r = p['r']; n = p.get('n', 10)
            R = frame_of(b - a); ang = np.linspace(0, 2 * np.pi, n + 1)[:-1]
            ring = np.stack([np.cos(ang), np.sin(ang), np.zeros(n)], 1) * r @ R.T
            for i in range(n):
                j = (i + 1) % n
                T += [np.array([a + ring[i], a + ring[j], b + ring[j]]), np.array([a + ring[i], b + ring[j], b + ring[i]])]
                T += [np.array([a, a + ring[j], a + ring[i]]), np.array([b, b + ring[i], b + ring[j]])]; C += [col] * 4
    T = np.array(T).reshape(-1, 3); C = np.repeat(np.array(C), 3, 0)
    return T, C

def b64(a): return base64.b64encode(np.ascontiguousarray(a, dtype='<f4').tobytes()).decode()
def statics(path):
    """the body mesh, once: rest positions, joints, weights, uv, first-person arm tris, full body tris"""
    d = {'P': b64(BP), 'J': b64(BJ.astype(np.float32)), 'W': b64(BW), 'uv': b64(BUV), 'trA': b64(TR_ARM.reshape(-1).astype(np.float32)),
         'trB': b64(np.asarray(TR_BODY).reshape(-1).astype(np.float32)), 'nb': NB + 8}
    json.dump(d, open(path, 'w'))
