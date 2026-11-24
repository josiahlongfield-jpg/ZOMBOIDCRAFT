"""Convert Cortana's moves onto another character's OWN skeleton (retargeting), keeping that character's proportions.

Runs in Blender on Josiah's PC (her model and moves stay there). You animate once, on Cortana; this copies how each
joint turns (not where it sits), so every character keeps their own limb lengths, height and build.

    blender.exe -b CHARACTER.blend --python retarget_character.py -- --from cortana_my_animations.blend
                --moves "Idle Fidget Bashful|Flat" --character Chief --out CHARACTER-moves.blend

  --from FILE        Cortana's animation file. Her armature ("Armature") and the named moves are appended into the
                     character file (the copy you save with --out; the files you point at are never changed).
                     Leave out when her armature is already in the open file.
  --source NAME      her armature object's name (default Armature, or the appended copy)
  --target NAME      the character's armature object (default: the only other armature in the file)
  --moves A,B        her action names, comma separated (default: the action on her armature)
  --character NAME   name used for the new actions: "Idle Fidget Bashful|Chief"
  --map FILE.json    bone map {"character bone": "her bone"}; default is a guess from the bone names
  --guess-map FILE   only write the guessed bone map (and what was left out) to FILE and stop, to check or edit it
  --legs ik|fk       ik (default): feet go where hers go, scaled to the character's legs, so planted feet stay
                     planted and nothing floats or sinks. fk: copy the leg rotations only.
  --hands auto|fk|chest
                     auto (default): arm turns are copied, but when her hands come together they are pulled
                     together on the character too (their shoulders and arms differ, so a plain copy leaves a gap).
                     fk: copy the arm turns only. chest: hands always go where hers are relative to her chest.
  --out FILE         save the result here (default: <character file>-moves.blend next to it)

How it works, per frame: every matched bone gets her bone's turn in the world, measured from a lined-up rest pose
(their limbs are first turned to point the way hers do at rest, so a T-pose character still copies her A-pose moves).
The pelvis moves as hers does, scaled by leg length. Bones with no partner (twist bones, extra spine bones) follow
their parent. Root motion (the armature object's x/y and turn) is copied scaled the same way.
"""
import bpy, sys, os, re, json, math, argparse
from mathutils import Matrix, Quaternion, Vector

FINGERS = ('thumb', 'index', 'middle', 'ring', 'pinky')
SKIP = {'twist', 'roll', 'end', 'nub', 'top', 'tip', 'ik', 'pole', 'ctrl', 'mch', 'target', 'helper', 'share', 'null',
        'pin', 'guide', 'socket', 'prop', 'weapon', 'eye', 'eyes', 'jaw', 'brow', 'lip', 'cheek', 'tongue', 'teeth',
        'ear', 'nose', 'lid', 'hair', 'breast', 'skirt', 'cloth', 'cape', 'tail', 'wing'}
DROP = {'def', 'org', 'cc', 'base', 'bip001', 'bip01', 'bip', 'b', 'bone', 'jnt', 'j', 'rig', 'sk', 'mixamorig',
        'mixamorig1', 'mixamorig2', 'valvebiped', 'deform', 'f', 'joint', 'bn'}


# ------------------------------------------------------------------------------------------------- reading bone names
def tokens(name):
    s = name.split(':')[-1].split('|')[-1]
    s = re.sub(r'([a-z])([A-Z])', r'\1_\2', s)
    s = re.sub(r'([A-Za-z])(\d)', r'\1_\2', s)
    s = re.sub(r'(\d)([A-Za-z])', r'\1_\2', s)
    s = s.lower()
    t = [x for x in re.split(r'[^a-z0-9]+', s) if x]
    side = None
    keep = []
    for x in t:
        if x in ('l', 'left', 'lf', 'lft') and side is None:
            side = 'l'
        elif x in ('r', 'right', 'rt', 'rgt') and side is None:
            side = 'r'
        elif x not in DROP:
            keep.append(x)
    # Daz style lShldr / rThigh: camel split gives 'l'+'shldr' already; joined forms like 'lthigh'
    if side is None and keep and re.match(r'^[lr](thigh|shin|foot|toe|hand|forearm|shldr|collar|index|mid|ring|pinky|thumb|carpal)', keep[0]):
        side = keep[0][0]; keep[0] = keep[0][1:]
    return side, keep


def role_of(name):
    """('pelvis'|'spine'|'neck'|'head', None) or (part, side) or (('finger', f), side). None = not a body bone."""
    side, t = tokens(name)
    T = set(t)
    if not t or T & SKIP or any('twist' in x or 'roll' in x for x in t):
        return None
    j = ''.join(x for x in t if not x.isdigit())
    for f in FINGERS:
        if f in T or (f == 'pinky' and ({'little', 'pinkie', 'small'} & T)) or (f == 'middle' and 'mid' in T):
            return (('finger', f), side) if side else None
    if j in ('hand', 'wrist') or 'wrist' in T:
        return ('hand', side) if side else None
    if {'toe', 'toes', 'ball', 'toebase'} & T or j.startswith('toe'):
        return ('toe', side) if side else None
    if {'foot', 'ankle'} & T:
        return ('foot', side) if side else None
    if {'thigh', 'upleg', 'femur'} & T or ({'up', 'leg'} <= T) or ({'upper', 'leg'} <= T) or 'upperleg' in T:
        return ('thigh', side) if side else None
    if {'calf', 'shin', 'knee', 'lowerleg'} & T or ({'lower', 'leg'} <= T) or (T - {'bend'} == {'leg'}):
        return ('calf', side) if side else None
    if {'forearm', 'lowerarm', 'elbow', 'radius', 'ulna'} & T or ({'lower', 'arm'} <= T) or ({'fore', 'arm'} <= T):
        return ('forearm', side) if side else None
    if {'upperarm', 'shldr', 'humerus'} & T or ({'upper', 'arm'} <= T) or (T - {'bend'} == {'arm'}):
        return ('upperarm', side) if side else None
    if {'clavicle', 'clav', 'collar', 'shoulder', 'collarbone'} & T:
        return ('clav', side) if side else None
    if side is None:
        if 'head' in T:
            return ('head', None)
        if 'neck' in T:
            return ('neck', None)
        if {'spine', 'chest', 'torso', 'abdomen', 'waist', 'back', 'ribs', 'upperchest', 'abdomenupper', 'abdomenlower', 'chestupper', 'chestlower'} & T:
            return ('spine', None)
        if {'hips', 'hip', 'pelvis'} & T:
            return ('pelvis', None)
    return None


def depth(b):
    d = 0
    while b.parent:
        b = b.parent; d += 1
    return d


def body_roles(arm):
    """role -> bone name for one armature; spine/neck/fingers as lists from the body outwards."""
    bones = [b for b in arm.data.bones]
    deform = [b for b in bones if b.use_deform]
    if len(deform) >= 10:
        bones = deform
    R = {}
    lists = {}
    for b in sorted(bones, key=depth):
        r = role_of(b.name)
        if r is None:
            continue
        part, side = r
        if part in ('spine', 'neck') or (isinstance(part, tuple)):
            lists.setdefault((part, side), []).append(b.name)
        elif (part, side) not in R:
            R[(part, side)] = b.name
    names = arm.data.bones
    def chain_between(lo, hi):
        """bones strictly between bone lo and bone hi on hi's parent chain (outer first)."""
        out, b = [], names[hi].parent
        while b is not None and b.name != lo:
            out.append(b.name); b = b.parent
        return list(reversed(out)) if b is not None else None
    # pelvis: the deepest bone above both thighs and the head
    need = [R.get(('thigh', 'l')), R.get(('thigh', 'r')), R.get(('head', None))]
    if all(need):
        anc = None
        paths = []
        for n in need:
            p, b = [], names[n]
            while b is not None:
                p.append(b.name); b = b.parent
            paths.append(list(reversed(p)))
        for i in range(min(len(p) for p in paths)):
            if len({p[i] for p in paths}) == 1:
                anc = paths[0][i]
        if anc is not None:
            R[('pelvis', None)] = anc
    if ('pelvis', None) in R and ('head', None) in R:
        mid = chain_between(R[('pelvis', None)], R[('head', None)]) or []
        mid = [m for m in mid if role_of(m) is not None and role_of(m)[0] in ('spine', 'neck')]
        R['spine'] = [m for m in mid if role_of(m)[0] == 'spine']
        R['neck'] = [m for m in mid if role_of(m)[0] == 'neck']
    for (part, side), L in lists.items():
        if isinstance(part, tuple):
            # one chain per finger: keep the main chain (each bone the child of the one before)
            ch = [L[0]]
            for n in L[1:]:
                if names[n].parent is not None and names[n].parent.name == ch[-1]:
                    ch.append(n)
            # a metacarpal stays with the hand: non-thumb chains of 4+ joints drop the first
            # a metacarpal (numbered 0, like her b_l_pinky0) stays with the hand
            num = [x for x in tokens(ch[0])[1] if x.isdigit()]
            if part[1] != 'thumb' and len(ch) >= 4 and num and int(num[0]) == 0:
                ch = ch[1:]
            real = [n for n in ch if names[n].children or len(ch) <= 3]
            if part[1] != 'thumb' and len(real) >= 4:
                real = real[1:]
            R[(part, side)] = real[:3]
    return R


def spread(src, tgt):
    """map a target chain onto a source chain, ends to ends."""
    out = {}
    if not src or not tgt:
        return out
    for j, t in enumerate(tgt):
        i = 0 if len(tgt) == 1 else round(j * (len(src) - 1) / (len(tgt) - 1))
        out[t] = src[i]
    if len(tgt) == 1:
        out[tgt[0]] = src[-1]
    return out


def is_arp(arm):
    b = arm.data.bones
    return 'c_root_master.x' in b and 'c_arm_fk.l' in b and 'c_thigh_fk.l' in b


def arp_roles(arm):
    """Auto-Rig Pro: its body bones are driven by constraints from the controls, so the controls are what we animate
    (with the arms and legs switched to FK)."""
    b = arm.data.bones
    has = lambda n: n in b
    R = {('pelvis', None): 'c_root_master.x', ('head', None): 'c_head.x'}
    R['spine'] = [n for n in ('c_spine_01.x', 'c_spine_02.x', 'c_spine_03.x', 'c_spine_04.x', 'c_spine_05.x') if has(n)]
    R['neck'] = [n for n in ('c_subneck_1.x', 'c_neck.x') if has(n)]
    for s in 'lr':
        for part, n in (('clav', 'c_shoulder'), ('upperarm', 'c_arm_fk'), ('forearm', 'c_forearm_fk'), ('hand', 'c_hand_fk'),
                        ('thigh', 'c_thigh_fk'), ('calf', 'c_leg_fk'), ('foot', 'c_foot_fk'), ('toe', 'c_toes_fk')):
            if has(f'{n}.{s}'):
                R[(part, s)] = f'{n}.{s}'
        for f in FINGERS:
            R[(('finger', f), s)] = [f'c_{f}{i}.{s}' for i in (1, 2, 3) if has(f'c_{f}{i}.{s}')]
    return {k: v for k, v in R.items() if v and (isinstance(v, list) or has(v))}


def arp_fk(arm):
    """switch Auto-Rig Pro arms and legs to FK (ik_fk_switch 1 = FK), returns what it changed."""
    done = []
    for n in ('c_hand_ik.l', 'c_hand_ik.r', 'c_foot_ik.l', 'c_foot_ik.r'):
        pb = arm.pose.bones.get(n)
        if pb is not None and 'ik_fk_switch' in pb.keys():
            pb['ik_fk_switch'] = 1.0
            done.append(n)
    return done


def guess_map(src, tgt):
    S, T = body_roles(src), (arp_roles(tgt) if is_arp(tgt) else body_roles(tgt))
    mp = {}
    for k, tb in T.items():
        if k in ('spine', 'neck'):
            mp.update(spread(S.get(k, []), tb))
        elif isinstance(k, tuple) and isinstance(k[0], tuple):
            sb = S.get(k, [])
            for a, b in zip(tb, sb):
                mp[a] = b
        elif k in S:
            mp[tb] = S[k]
    # one neck bone on the character, several on her: copy her middle one
    if len(T.get('neck', [])) == 1 and len(S.get('neck', [])) > 1:
        mp[T['neck'][0]] = S['neck'][len(S['neck']) // 2]
    if is_arp(tgt):
        left = [n for n in sorted(set(sum([v if isinstance(v, list) else [v] for v in T.values()], []))) if n not in mp]
    else:
        left = [b.name for b in tgt.data.bones if b.name not in mp and (b.use_deform or len([x for x in tgt.data.bones if x.use_deform]) < 10)]
    return mp, left


def roles_from_map(mp, tgt):
    """the character's roles, read from which of her bones each one copies."""
    T = {}
    tb = tgt.data.bones
    for t in sorted(mp, key=lambda n: depth(tb[n])):
        r = role_of(mp[t])
        if r is None:
            continue
        part, side = r
        if part in ('spine', 'neck') or isinstance(part, tuple):
            T.setdefault((part, side) if isinstance(part, tuple) else part, []).append(t)
        else:
            T.setdefault((part, side), t)
    return T


# ------------------------------------------------------------------------------------------------- small maths
def rot_w(M):
    return M.to_3x3().normalized().to_quaternion()


def yaw_of(arm, l, r):
    mw = arm.matrix_world
    v = (mw @ arm.data.bones[l].head_local) - (mw @ arm.data.bones[r].head_local)
    return math.atan2(v.y, v.x)


def chain_len(arm, a, b, c):
    mw = arm.matrix_world
    p = [mw @ arm.data.bones[n].head_local for n in (a, b, c)]
    return (p[1] - p[0]).length + (p[2] - p[1]).length


def two_bone(A, K, F, T, l1, l2):
    """turns for the upper (R1) and lower (R2, applied after R1) bones so the end F reaches T, knee kept in its plane."""
    dv = T - A
    d = max(1e-6, min(dv.length, (l1 + l2) * 0.9999, ))
    d = max(d, abs(l1 - l2) * 1.0001 + 1e-6)
    u = dv.normalized()
    pole = K - A
    pole = pole - u * pole.dot(u)
    if pole.length < 1e-6:
        pole = (F - K) - u * (F - K).dot(u)
        if pole.length < 1e-6:
            pole = u.orthogonal()
    v = pole.normalized()
    ca = max(-1.0, min(1.0, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)))
    K2 = A + u * (l1 * ca) + v * (l1 * math.sqrt(max(0.0, 1 - ca * ca)))
    R1 = (K - A).rotation_difference(K2 - A)
    F1 = K2 + R1 @ (F - K)
    T2 = A + u * d
    R2 = (F1 - K2).rotation_difference(T2 - K2)
    return R1, R2


# ------------------------------------------------------------------------------------------------- the converter
class Retarget:
    def __init__(self, src, tgt, mp, legs='ik', hands='auto'):
        self.src, self.tgt, self.mp, self.legs, self.hands = src, tgt, dict(mp), legs, hands
        S, T = body_roles(src), roles_from_map(mp, tgt)
        self.trole = {t: role_of(s) for t, s in mp.items()}
        inv = {}
        for t, s in self.mp.items():
            inv.setdefault(s, t)
        self.S, self.T = S, T
        tb = tgt.data.bones
        self.order = sorted([b.name for b in tb], key=lambda n: depth(tb[n]))
        self.rest = {b.name: b.matrix_local.copy() for b in tb}
        self.rel = {b.name: (b.parent.matrix_local.inverted() @ b.matrix_local) for b in tb if b.parent}
        Tb = tgt.matrix_world.copy()
        self.Tb, self.Tbi, self.Tq = Tb, Tb.inverted(), rot_w(Tb)
        # facing: turn her so she faces the way the character faces
        sl, sr = S.get(('thigh', 'l')), S.get(('thigh', 'r'))
        tl, tr = T.get(('thigh', 'l')), T.get(('thigh', 'r'))
        self.yaw = yaw_of(tgt, tl, tr) - yaw_of(src, sl, sr) if all((sl, sr, tl, tr)) else 0.0
        self.G = Matrix.Rotation(self.yaw, 4, 'Z')
        # size: hip height goes with leg length, reach with arm length
        self.k = 1.0
        if all((sl, tl)) and S.get(('calf', 'l')) and S.get(('foot', 'l')) and T.get(('calf', 'l')) and T.get(('foot', 'l')):
            self.k = chain_len(tgt, tl, T[('calf', 'l')], T[('foot', 'l')]) / chain_len(src, sl, S[('calf', 'l')], S[('foot', 'l')])
        self.ka = self.k
        su, tu = S.get(('upperarm', 'l')), T.get(('upperarm', 'l'))
        if su and tu and S.get(('forearm', 'l')) and S.get(('hand', 'l')) and T.get(('forearm', 'l')) and T.get(('hand', 'l')):
            self.ka = chain_len(tgt, tu, T[('forearm', 'l')], T[('hand', 'l')]) / chain_len(src, su, S[('forearm', 'l')], S[('hand', 'l')])
        self.t_pelvis = T.get(('pelvis', None))
        self.s_pelvis = self.mp.get(self.t_pelvis)
        # rest, in the world (her turned to face the character's way)
        self.Sb = None
        self.src_rest_w = {s: self.G @ src.matrix_world @ src.data.bones[s].matrix_local for s in set(self.mp.values())}
        self.tgt_rest_w = {t: Tb @ self.rest[t] for t in self.order}
        self._line_up()

    def _aim(self, t):
        """(target child joint, source child joint) to aim bone t with, or None."""
        s = self.mp[t]
        r = self.trole.get(t)
        part = r[0] if r else None
        if part in ('pelvis', 'head', 'foot', 'toe'):
            return None
        sb = self.src.data.bones
        def under(x, anc):
            b = sb[x].parent
            while b is not None:
                if b.name == anc:
                    return True
                b = b.parent
            return False
        cands, q = [], list(self.tgt.data.bones[t].children)
        while q:
            c = q.pop(0)
            if c.name in self.mp and self.mp[c.name] != s:
                if under(self.mp[c.name], s):
                    cands.append(c.name)
                continue
            q.extend(c.children)
        if part == 'hand':
            mids = [c for c in cands if self.trole.get(c) and self.trole[c][0] == ('finger', 'middle')]
            if not mids:   # fingers hang off another bone (Auto-Rig Pro): aim at the middle finger anyway
                mids = (self.T.get((('finger', 'middle'), r[1])) or [])[:1]
            cands = mids[:1]
        elif len(cands) > 1:
            pref = [c for c in cands if self.trole.get(c) and self.trole[c][0] in ('spine', 'neck', 'head')]
            cands = pref[:1] if pref else []
        if len(cands) != 1:
            return None
        return cands[0], self.mp[cands[0]]

    def _line_up(self):
        """turn the character's rest limbs to point the way hers do (A-pose vs T-pose), parents first."""
        tb = self.tgt.data.bones
        align = {}
        self.off = {}
        self.aims = {}
        for t in self.order:
            p = tb[t].parent
            inh = align[p.name] if p is not None else Quaternion()
            r = self.trole.get(t)
            if t in self.mp and r and r[0] in ('pelvis', 'head', 'foot'):
                inh = Quaternion()  # these stand as the character's own rest does (feet flat, head level)
            cur = inh.copy()
            if t in self.mp:
                aim = self._aim(t)
                self.aims[t] = aim
                if aim is not None:
                    tc, sc = aim
                    dt = inh @ (self.tgt_rest_w[tc].translation - self.tgt_rest_w[t].translation)
                    ds = self.src_rest_w[sc].translation - self.src_rest_w[self.mp[t]].translation
                    if dt.length > 1e-4 and ds.length > 1e-4:
                        cur = dt.rotation_difference(ds) @ inh
                        r = self.trole.get(t)
                        if r and r[0] == 'hand':
                            cur = self._twist(t, cur, ds) or cur
            align[t] = cur
            if t in self.mp:
                tw = cur @ rot_w(self.tgt_rest_w[t])
                self.off[t] = rot_w(self.src_rest_w[self.mp[t]]).inverted() @ tw
        self.align = align

    def _twist(self, t, cur, axis):
        side = self.trole[t][1]
        ti, tp = self.T.get((('finger', 'index'), side), []), self.T.get((('finger', 'pinky'), side), [])
        if not ti or not tp or ti[0] not in self.mp or tp[0] not in self.mp:
            return None
        lt = cur @ (self.tgt_rest_w[ti[0]].translation - self.tgt_rest_w[tp[0]].translation)
        ls = self.src_rest_w[self.mp[ti[0]]].translation - self.src_rest_w[self.mp[tp[0]]].translation
        a = axis.normalized()
        lt = lt - a * lt.dot(a); ls = ls - a * ls.dot(a)
        if lt.length < 1e-5 or ls.length < 1e-5:
            return None
        return lt.rotation_difference(ls) @ cur

    # --- sampling her
    def sample(self, act, scene):
        src = self.src
        ad = src.animation_data or src.animation_data_create()
        was = ad.action
        ad.action = act
        if hasattr(ad, 'action_slot') and getattr(ad, 'action_slot', None) is None:
            for sl in act.slots:
                if sl.target_id_type == 'OBJECT':
                    ad.action_slot = sl
                    break
        a, b = act.frame_range
        ka, kb = act.curve_frame_range
        b = max(b, kb)
        frames = list(range(int(round(a)), int(round(b)) + 1))
        need = set(self.mp.values())
        out = []
        scene.frame_set(frames[0])
        Sb = src.matrix_world.copy()
        for f in frames:
            scene.frame_set(f)
            W = {s: self.G @ Sb @ src.pose.bones[s].matrix for s in need}
            mw = src.matrix_world
            d = self.G @ (mw.translation - Sb.translation)
            dyaw = mw.to_euler().z - Sb.to_euler().z
            out.append((W, d, dyaw))
        ad.action = was
        return frames, out

    # --- one target frame
    def solve(self, rw, ppos_w):
        M = {}
        for t in self.order:
            b = self.tgt.data.bones[t]
            base = (M[b.parent.name] @ self.rel[t]) if b.parent else self.rest[t].copy()
            if t in rw:
                pos = base.translation
                if t == self.t_pelvis and ppos_w is not None:
                    pos = self.Tbi @ ppos_w
                M[t] = Matrix.Translation(pos) @ (self.Tq.inverted() @ rw[t]).to_matrix().to_4x4()
            else:
                M[t] = base
        return M

    def desired(self, W):
        """the world turn every matched bone should have, and where the pelvis goes (before feet/hands)."""
        rw = {t: W[s].to_quaternion() @ self.off[t] for t, s in self.mp.items()}
        ppos = None
        if self.t_pelvis and self.s_pelvis:
            ppos = self.tgt_rest_w[self.t_pelvis].translation + self.k * (W[self.s_pelvis].translation - self.src_rest_w[self.s_pelvis].translation)
        return rw, ppos

    def reach(self, rw, W, wp):
        """turn legs (and hands, when theirs meet) so feet and hands land where they should. wp(bone) = joint now."""
        changed = False
        for side in ('l', 'r'):
            if self.legs == 'ik':
                c = [self.T.get((x, side)) for x in ('thigh', 'calf', 'foot')]
                if all(c) and all(x in self.mp for x in c):
                    s_foot = self.mp[c[2]]
                    goal = self.tgt_rest_w[c[2]].translation + self.k * (W[s_foot].translation - self.src_rest_w[s_foot].translation)
                    self._ik(rw, c, goal, wp)
                    changed = True
            if self.hands in ('chest', 'auto'):
                c = [self.T.get((x, side)) for x in ('upperarm', 'forearm', 'hand')]
                sp = self.T.get('spine') or []
                if all(c) and all(x in self.mp for x in c) and sp:
                    ch = sp[-1]
                    s_hand, s_ch = self.mp[c[2]], self.mp[ch]
                    goal = wp(ch) + self.ka * (W[s_hand].translation - W[s_ch].translation)
                    w = 1.0
                    if self.hands == 'auto':
                        w = self._near(W, side)
                        goal = wp(c[2]).lerp(goal, w)
                    if w > 1e-4:
                        self._ik(rw, c, goal, wp)
                        changed = True
        return changed

    def frame(self, W):
        rw, ppos = self.desired(W)
        M = self.solve(rw, ppos)
        if self.reach(rw, W, lambda n: self.Tb @ M[n].translation):
            M = self.solve(rw, ppos)
        return M

    def bases(self, M):
        out = {}
        tb = self.tgt.data.bones
        for t in self.order:
            b = tb[t]
            rel = self.rel[t] if b.parent else self.rest[t]
            P = M[b.parent.name] if b.parent else Matrix()
            out[t] = rel.inverted() @ P.inverted() @ M[t]
        return out

    def exact(self, W, B, iters=8):
        """for rigs whose bones are moved by constraints/drivers (Auto-Rig Pro): pose the real rig, look where each
        matched bone really ended up, and correct it, a few times. B = starting bases {bone: Matrix}; returns
        (bases, worst miss in degrees)."""
        tgt, P = self.tgt, self.tgt.pose.bones
        vl = bpy.context.view_layer
        mapped = sorted(self.mp, key=lambda n: depth(tgt.data.bones[n]))
        rw0, ppos = self.desired(W)
        rw = dict(rw0)
        def put():
            for t in mapped:
                P[t].matrix_basis = B[t]
            vl.update()
        def fix():
            worst = 0.0
            for t in mapped:
                A = tgt.matrix_world @ P[t].matrix
                Aq = rot_w(A)
                dq = Aq.inverted() @ rw[t]
                a = math.degrees(dq.angle); worst = max(worst, min(a, 360 - a))
                Bq = B[t].to_quaternion() @ dq
                loc = B[t].translation.copy()
                if t == self.t_pelvis and ppos is not None:
                    PR = A @ B[t].inverted()   # parent and rest, as they really are now
                    loc = loc + PR.to_3x3().inverted() @ (ppos - A.translation)
                B[t] = Matrix.Translation(loc) @ Bq.normalized().to_matrix().to_4x4()
            return worst
        def settle():
            w = 180.0
            for _ in range(iters):
                put()
                w = fix()
                if w < 0.02:
                    break
            put()
            return w
        settle()
        wp = lambda n: (tgt.matrix_world @ P[n].matrix).translation
        if self.reach(rw, W, wp):
            settle()
        worst = fix()   # how far off the pose is now (this last tiny correction is kept)
        return B, worst

    def _near(self, W, side):
        """1 when her hands are together (or nearly), 0 when they are well apart, smooth in between."""
        d = None
        for part in ('hand', ('finger', 'middle'), ('finger', 'index')):
            a, b = self.S.get((part, 'l')), self.S.get((part, 'r'))
            if isinstance(a, list):
                a, b = (a[-1] if a else None), (b[-1] if b else None)
            if a in W and b in W:
                x = (W[a].translation - W[b].translation).length
                d = x if d is None else min(d, x)
        if d is None:
            return 0.0
        lo, hi = 0.10, 0.25  # metres apart on her: wrists or finger tips
        x = max(0.0, min(1.0, (hi - d) / (hi - lo)))
        return x * x * (3 - 2 * x)

    def _ik(self, rw, c, goal, wp):
        A, K, F = wp(c[0]), wp(c[1]), wp(c[2])
        R1, R2 = two_bone(A, K, F, goal, (K - A).length, (F - K).length)
        rw[c[0]] = R1 @ rw[c[0]]
        rw[c[1]] = R2 @ R1 @ rw[c[1]]

    # --- write the character's action
    def needs_exact(self):
        """True when the character's bones are moved by constraints or drivers (so our own maths alone can't place them)."""
        if any(len(pb.constraints) for pb in self.tgt.pose.bones):
            return True
        ad = self.tgt.animation_data
        return bool(ad and len(ad.drivers)) or bool(self.tgt.data.animation_data and len(self.tgt.data.animation_data.drivers))

    def bake(self, act_src, name, scene, exact=None):
        frames, smp = self.sample(act_src, scene)
        tgt = self.tgt
        tb = tgt.data.bones
        n = len(frames)
        exact = self.needs_exact() if exact is None else exact
        keyed = sorted(self.mp, key=lambda x: depth(tb[x])) if exact else self.order
        vals = {t: ([], []) for t in keyed}
        prevq = {}
        root = []
        self.worst = 0.0
        hidden, ad0 = [], (tgt.animation_data.action if tgt.animation_data else None)
        if exact:
            # only the rigs need working out: switch everything else off while we do (turned back on below)
            for o in bpy.data.objects:
                if o.type != 'ARMATURE' and not o.hide_viewport:
                    o.hide_viewport = True; hidden.append(o)
            if tgt.animation_data:
                tgt.animation_data.action = None
        try:
            Bprev = None
            for W, d, dyaw in smp:
                if exact:
                    B0 = Bprev if Bprev is not None else self.bases(self.frame(W))
                    Bs, miss = self.exact(W, {t: B0[t].copy() for t in keyed})
                    self.worst = max(self.worst, miss)
                    Bprev = Bs
                else:
                    Bs = self.bases(self.frame(W))
                for t in keyed:
                    B = Bs[t]
                    q = B.to_quaternion().normalized()
                    if t in prevq and q.dot(prevq[t]) < 0:
                        q.negate()
                    prevq[t] = q
                    vals[t][0].append(q); vals[t][1].append(B.translation.copy())
                root.append((d * self.k, dyaw))
        finally:
            for o in hidden:
                o.hide_viewport = False
            if exact:
                for t in keyed:
                    tgt.pose.bones[t].matrix_basis = Matrix()
                if tgt.animation_data:
                    tgt.animation_data.action = ad0
        act = bpy.data.actions.new(name)
        act.use_fake_user = True
        for k in ('fps', 'speed', 'travel'):
            if k in act_src.keys():
                act[k] = act_src[k]
        act['retarget_from'] = act_src.name
        act.use_frame_range = True
        act.frame_start, act.frame_end = frames[0], frames[-1]
        ad = tgt.animation_data or tgt.animation_data_create()
        ad.action = act
        if hasattr(act, 'slots'):
            if ad.action_slot is None:
                sl = act.slots.new(id_type='OBJECT', name=tgt.name)
                ad.action_slot = sl
            from bpy_extras import anim_utils
            fcs = anim_utils.action_ensure_channelbag_for_slot(act, ad.action_slot).fcurves
            new = lambda path, i, g: fcs.new(path, index=i, group_name=g)
        else:
            fcs = act.fcurves
            new = lambda path, i, g: fcs.new(path, index=i, action_group=g)
        def put(path, i, g, series):
            fc = new(path, i, g)
            fc.keyframe_points.add(n)
            co = [0.0] * (2 * n)
            co[0::2] = frames
            co[1::2] = series
            fc.keyframe_points.foreach_set('co', co)
            fc.keyframe_points.foreach_set('interpolation', [1] * n)  # linear: one key per frame
            fc.update()
        for t in keyed:
            pb = tgt.pose.bones[t]
            qs, ls = vals[t]
            mode = pb.rotation_mode
            if mode == 'QUATERNION':
                for i in range(4):
                    put(f'pose.bones["{t}"].rotation_quaternion', i, t, [q[i] for q in qs])
            elif mode == 'AXIS_ANGLE':
                aa = []
                for q in qs:
                    ax, an = q.to_axis_angle(); aa.append((an, ax.x, ax.y, ax.z))
                for i in range(4):
                    put(f'pose.bones["{t}"].rotation_axis_angle', i, t, [v[i] for v in aa])
            else:
                eu, prev = [], None
                for q in qs:
                    e = q.to_euler(mode, prev) if prev is not None else q.to_euler(mode)
                    eu.append(e); prev = e
                for i in range(3):
                    put(f'pose.bones["{t}"].rotation_euler', i, t, [e[i] for e in eu])
            if t == self.t_pelvis or not tb[t].use_connect:
                if exact and t != self.t_pelvis and max(l.length for l in ls) < 1e-6:
                    continue
                for i in range(3):
                    put(f'pose.bones["{t}"].location', i, t, [l[i] for l in ls])
        if any(d.length > 1e-5 or abs(y) > 1e-5 for d, y in root):
            l0, r0 = tgt.location.copy(), tgt.rotation_euler.z
            for i in range(2):
                put('location', i, 'Object Transforms', [l0[i] + d[i] for d, y in root])
            put('rotation_euler', 2, 'Object Transforms', [r0 + y for d, y in root])
        return act, frames


# ------------------------------------------------------------------------------------------------- checks for the report
def report(rt, act, scene, frames):
    """how far the character's feet wander over the move (planted feet should stay put), plus the sizes used."""
    tgt = rt.tgt
    tgt.animation_data.action = act
    start, far = {}, {'l': 0.0, 'r': 0.0}
    for f in frames[:: max(1, len(frames) // 120)] + [frames[-1]]:
        scene.frame_set(f)
        for side in ('l', 'r'):
            fb = rt.T.get(('foot', side))
            if fb:
                p = tgt.matrix_world @ tgt.pose.bones[fb].head
                start.setdefault(side, p.copy())
                far[side] = max(far[side], (p - start[side]).length)
    return [f'size: legs x{rt.k:.3f}, arms x{rt.ka:.3f}; turned {math.degrees(rt.yaw):.1f} deg to face their way',
            f'feet: furthest from where they started  L {far["l"] * 100:.1f} cm  R {far["r"] * 100:.1f} cm']


# ------------------------------------------------------------------------------------------------- command line
def armatures():
    return [o for o in bpy.data.objects if o.type == 'ARMATURE']


def append_cortana(path, moves, body=False):
    """append her armature (and with body=True the meshes riding on it) and the named moves."""
    with bpy.data.libraries.load(path, link=False) as (src, dst):
        names = [n for n in src.objects] if body else [n for n in src.objects if n == 'Armature']
        dst.objects = list(names)
        want = set(moves or [])
        dst.actions = [a for a in src.actions if a in want] if want else []
    if 'Armature' not in names:
        raise SystemExit(f'no object named Armature in {path}')
    obj = dst.objects[names.index('Armature')]
    objs = [o for o in dst.objects if o is not None]
    if obj is None or obj.type != 'ARMATURE':
        raise SystemExit(f'no object named Armature in {path}')
    def rides(o):
        while o is not None:
            if o is obj:
                return True
            o = o.parent
        return False
    coll = bpy.data.collections.new('Cortana (for comparison)')
    bpy.context.scene.collection.children.link(coll)
    for o in objs:
        if o is obj or (body and rides(o) and o.type in ('MESH', 'EMPTY', 'CURVE')):
            coll.objects.link(o)
        else:
            bpy.data.objects.remove(o)
    return obj


def main(argv):
    ap = argparse.ArgumentParser(prog='retarget_character.py')
    ap.add_argument('--from', dest='src_file')
    ap.add_argument('--source')
    ap.add_argument('--target')
    ap.add_argument('--moves')
    ap.add_argument('--character', default='character')
    ap.add_argument('--map')
    ap.add_argument('--guess-map')
    ap.add_argument('--legs', default='ik', choices=('ik', 'fk'))
    ap.add_argument('--hands', default='auto', choices=('auto', 'fk', 'chest'))
    ap.add_argument('--out')
    ap.add_argument('--no-body', dest='body', action='store_false', help="don't bring her body in for the side-by-side look")
    ap.add_argument('--beside', type=float, default=0.9, help='metres to her side to stand Cortana for the side-by-side look')
    a = ap.parse_args(argv)
    moves = [m.strip() for m in a.moves.split(',')] if a.moves else []
    before = set(armatures())
    if a.src_file:
        src = append_cortana(a.src_file, moves, body=a.body and not a.guess_map)
    else:
        src = bpy.data.objects[a.source or 'Armature']
    others = [o for o in armatures() if o is not src and (o in before or not a.src_file)]
    if a.target:
        tgt = bpy.data.objects[a.target]
    else:
        if len(others) != 1:
            raise SystemExit('which armature is the character? use --target, one of: ' + ', '.join(o.name for o in others))
        tgt = others[0]
    mp, left = guess_map(src, tgt)
    if a.map:
        mp = json.load(open(a.map))
        left = [b.name for b in tgt.data.bones if b.name not in mp]
    bad = [f'{t} -> {s}' for t, s in mp.items() if t not in tgt.data.bones or s not in src.data.bones]
    if bad:
        raise SystemExit('bone map names not found: ' + '; '.join(bad))
    print(f'bone map: {len(mp)} of the character\'s bones matched to hers')
    for t, s in sorted(mp.items(), key=lambda x: depth(tgt.data.bones[x[0]])):
        print(f'  {t:32s} <- {s}')
    if left:
        print('  left to follow their parent:', ', '.join(left))
    if a.guess_map:
        json.dump(mp, open(a.guess_map, 'w'), indent=1)
        json.dump({'not_matched': left}, open(os.path.splitext(a.guess_map)[0] + '-left.json', 'w'), indent=1)
        print('wrote', a.guess_map)
        return
    acts = [bpy.data.actions[m] for m in moves] if moves else [src.animation_data.action]
    if is_arp(tgt):
        print('Auto-Rig Pro: arms and legs switched to FK in the saved copy:', ', '.join(arp_fk(tgt)) or 'no switches found')
    rt = Retarget(src, tgt, mp, legs=a.legs, hands=a.hands)
    if rt.needs_exact():
        print('this rig moves its bones with constraints/drivers: each frame is checked on the real rig and corrected')
    scene = bpy.context.scene
    for act in acts:
        base = act.name.split('|')[0]
        new, frames = rt.bake(act, f'{base}|{a.character}', scene)
        print(f'made {new.name}: frames {frames[0]}-{frames[-1]}' + (f'; worst bone off by {rt.worst:.2f} deg' if rt.worst else ''))
        for l in report(rt, new, scene, frames):
            print('  ' + l)
    # leave both playing the first move, side by side, for a look (her rig stands to one side, turned to face their way)
    stand = bpy.data.objects.new('Cortana stands here', None)
    bpy.context.scene.collection.objects.link(stand)
    side = (tgt.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    stand.matrix_world = Matrix.Translation(tgt.matrix_world.translation + side * a.beside) @ rt.G
    src.parent = stand
    src.animation_data.action = acts[0]
    # play it at the move's own speed and length
    sc = bpy.context.scene
    if 'fps' in acts[0].keys():
        sc.render.fps, sc.render.fps_base = int(round(float(acts[0]['fps']))), 1.0
    sc.frame_start, sc.frame_end = int(acts[0].frame_range[0]), int(max(acts[0].frame_range[1], acts[0].curve_frame_range[1]))
    acts[0].use_fake_user = True
    tgt.animation_data.action = bpy.data.actions.get(f"{acts[0].name.split('|')[0]}|{a.character}")
    out = a.out or (os.path.splitext(bpy.data.filepath)[0] + '-moves.blend' if bpy.data.filepath else None)
    if out:
        if bpy.data.filepath and os.path.abspath(out) == os.path.abspath(bpy.data.filepath):
            raise SystemExit('--out is the character file itself; save to a new file')
        bpy.ops.wm.save_as_mainfile(filepath=out, copy=True)
        print('saved', out)


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
