const Q = new URLSearchParams(location.search);
KIT.outfit = Q.get('o') || 'police'; KIT.on = true; KIT.blood = 0.9; KIT.wear = 1; seed = 1;
const src = AI15[KIT.outfit];
var SHOOT = { stumps: [], cut: new Set() };
const LIMB = {
  // what a hit on each joint's part tears off, and which joints go with it
  cutOf: { wrR: 'elR', elR: 'elR', shR: 'shR', anR: 'knR', knR: 'knR', hipR: 'hipR', wrL: 'elL', elL: 'elL', shL: 'shL', anL: 'knL', knL: 'knL', hipL: 'hipL' },
  sub: { el: ['el', 'wr', 'tb'], sh: ['sh', 'el', 'wr', 'tb'], kn: ['kn', 'an'], hip: ['hip', 'kn', 'an'] },
  tough: { el: 10, sh: 15, kn: 12, hip: 18 },
  name: { el: 'forearm', sh: 'arm', kn: 'shin', hip: 'leg' },
  at: { el: 'elbow', sh: 'shoulder', kn: 'knee', hip: 'hip' },
  // limb damage per bullet or pellet; pellets, spread (radians) and how hard the limb is flung
  guns: {
    pistol: { name: 'Pistol', pellets: 1, spread: 0.004, limb: 3, fling: 45 },
    rifle: { name: 'Assault rifle', pellets: 1, spread: 0.003, limb: 5, fling: 60 },
    hunting: { name: 'Hunting rifle', pellets: 1, spread: 0.001, limb: 14, fling: 80 },
    shotgun: { name: 'Shotgun', pellets: 9, spread: 0.022, limb: 3.2, fling: 95 },
  },
};
// a torn end: a dark wet cap, a stub of bone and a few ragged shreds, its open face along -y of the group
function rag(w, bone) {
  const g = new THREE.Group();
  const cap = box(w + 0.5, 1.4, w + 0.5, gear('#3a0a07', { shine: true, noise: .2, grime: 0, clean: true, outline: .5 }));
  cap.position.y = 0.2; g.add(cap);
  if (bone) {
    const b = box(0.9, 1.9, 0.9, gear('#cfc4a4', { noise: .15, grime: 0, clean: true, outline: .4 }));
    b.position.set(0.3, -0.9, 0.15); g.add(b);
  }
  for (let i = 0; i < 4; i++) {
    const s = 0.4 + rnd() * 0.6, sh = box(s, 1 + rnd() * 1.6, 0.3, gear('#4a0d0a', { shine: true, noise: .1, grime: 0, clean: true, outline: .4 }));
    const a = rnd() * Math.PI * 2; sh.position.set(Math.cos(a) * w * 0.45, -0.4, Math.sin(a) * w * 0.45); sh.rotation.y = a; g.add(sh);
  }
  return g;
}

// the stump left on the body, on the parent bone at the joint
function addStump(f, cut) {
  const J = f.userData.J, S = STYLES.A, k = cut.slice(0, -1), side = cut.endsWith('R') ? -1 : 1, joint = J[cut];
  let g;
  if (k === 'el') { g = rag(S.fore[0], true); g.position.copy(joint.position); J['sh' + cut.slice(-1)].add(g); }
  else if (k === 'kn') { g = rag(S.shin[0], true); g.position.copy(joint.position); J['hip' + cut.slice(-1)].add(g); }
  else if (k === 'hip') { g = rag(S.thigh[0], true); g.position.copy(joint.position); J.hips.add(g); }
  else { g = rag(S.upper[0] + 0.4, true); g.position.set(side * (S.torso[0] / 2 + 0.5), S.torso[1] - 1.8, 0); g.rotation.z = side * Math.PI / 2; J.spine.add(g); }
  SHOOT.stumps.push({ g, t: 0 });
}

// ---------- moving on what's left ----------
// One leg gone: it lurches toward the missing side, buckles onto its one knee with a hand reaching for the ground,
// pushes itself back up and hops on the leg it has, arms out for balance. Both gone: it pitches forward, the
// hips drop like a dead weight, the arms come up to break the fall, it lands with a bounce and crawls.
const JKEYS = ['hips', 'spine', 'neck', 'head', 'shR', 'elR', 'wrR', 'shL', 'elL', 'wrL', 'hipR', 'knR', 'anR', 'hipL', 'knL', 'anL'];
function snap(J) {
  const o = {}; for (const k of JKEYS) { const r = J[k].rotation; o[k] = [r.x, r.y, r.z]; }
  const p = J.hips.position; o.pos = [p.x, p.y, p.z]; o.order = J.hips.rotation.order; return o;
}
function put(J, P) { for (const k of JKEYS) J[k].rotation.set(P[k][0], P[k][1], P[k][2]); J.hips.position.set(P.pos[0], P.pos[1], P.pos[2]); J.hips.rotation.order = P.order; }
function mix(A, B, s, keys) {
  const o = { pos: A.pos.map((v, i) => v + (B.pos[i] - v) * s), order: s < 0.5 ? A.order : B.order };
  for (const k of JKEYS) o[k] = (!keys || keys.includes(k)) ? A[k].map((v, i) => v + (B[k][i] - v) * s) : A[k].slice();
  return o;
}
const sm01 = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
const segL = (x, a, b) => sm01((x - a) / (b - a));
function legsGone() { let n = 0; for (const R of ['R', 'L']) if (SHOOT && (SHOOT.cut.has('kn' + R) || SHOOT.cut.has('hip' + R))) n++; return n; }
function goneSide() { return SHOOT.cut.has('knR') || SHOOT.cut.has('hipR') ? 'R' : 'L'; }

// how far below the hip the foot hangs, for a thigh at angle a from straight down and a knee bent k
function footDrop(a, k) { const S = STYLES.A; return S.thigh[1] * Math.cos(a) + S.shin[1] * Math.cos(a + k); }
// a bare pose: one leg (and the stump of the other) and arms set by hand, the hips planted over the standing foot
function onePose(J, o) {
  const S = STYLES.A, T = S.thigh[1], SH = S.shin[1], BASE = T + SH + S.foot[1] + S.pelvis[1] / 2;
  const st = o.gone === 'R' ? 'L' : 'R', ss = st === 'R' ? -1 : 1, gs = -ss;
  for (const k of JKEYS) J[k].rotation.set(0, 0, 0);
  J.hips.rotation.order = 'YXZ';
  const leg = (R, a, k, an, out) => { const s = R === 'R' ? -1 : 1; J['hip' + R].rotation.set(a, 0, out * s); J['kn' + R].rotation.set(k, 0, 0); J['an' + R].rotation.set(an, 0, 0); };
  const arm = (R, sx, out, el, wr) => { const s = R === 'R' ? -1 : 1; J['sh' + R].rotation.set(sx, 0.15 * s, out * s); J['el' + R].rotation.set(el, 0, 0); J['wr' + R].rotation.set(wr, 0, 0); };
  const hx = o.hx || 0;
  leg(st, o.a, o.k, -(o.a + o.k + hx) + (o.toe || 0), o.in || -0.08);
  leg(o.gone, o.ga, 0.2, 0, o.gout || 0.2);
  const drop = footDrop(o.a + hx, o.k);
  J.hips.position.set(gs * (o.shift ?? 1.3), BASE - (T + SH - drop) + (o.lift || 0), o.z || 0);
  J.hips.rotation.set(hx, 0, -gs * (o.roll || 0));
  J.spine.rotation.set(o.sx, 0, ss * (o.lean || 0));
  J.neck.rotation.set(o.nx || 0, 0, 0); J.head.rotation.set(o.hd || 0, 0, 0);
  arm(st, o.armS[0], o.armS[1], o.armS[2], o.armS[3]);
  arm(o.gone, o.armG[0], o.armG[1], o.armG[2], o.armG[3]);
}
function hop(J, t, gone, mode, ph) {
  const q = ((t + ph) / 0.78) % 1;
  const crouch = segL(q, 0, 0.35) * (1 - segL(q, 0.35, 0.5)), air = segL(q, 0.38, 0.52) * (1 - segL(q, 0.6, 0.74)), land = segL(q, 0.6, 0.74) * (1 - segL(q, 0.74, 1));
  const reach = mode === 'chase' || mode === 'attack', flail = Math.sin(t * 9) * land;
  onePose(J, {
    gone, a: -0.25 - 0.45 * crouch - 0.2 * land + 0.12 * air, k: 0.3 + 0.95 * crouch + 0.45 * land - 0.25 * air, toe: 0.4 * air,
    ga: -0.3 - 0.25 * air + 0.2 * crouch, lift: 2.0 * air, roll: 0.06, sx: 0.35 + 0.15 * crouch - 0.08 * air, lean: 0.1,
    nx: -0.05, hd: 0.1 + 0.2 * land - 0.1 * air,
    armS: reach ? [-1.6 - 0.2 * air, 0.2, -0.2, -0.1] : [-0.35 - 0.3 * air, 0.7 + 0.3 * air + 0.15 * flail, -0.6, 0.2],
    armG: reach ? [-1.5 - 0.2 * air, 0.3, -0.25, -0.1] : [-0.3 - 0.3 * air, 0.85 + 0.35 * air - 0.15 * flail, -0.5, 0.2],
  });
}
function limbMove(f, mode, t, v) {
  const J = f.userData.J, n = legsGone(), ev = SHOOT && SHOOT.ev, tau = ev ? t - ev.t0 : 99;
  if (n === 0) return zombieMove(f, mode, t, v);
  if (n === 2) {
    // the legless version of whatever it is doing, after the fall
    const keep = LEGS.off; LEGS.off = true; zombieMove(f, mode === 'down' ? 'down' : mode, t, v); LEGS.off = keep;
    J.hips.position.z += ev && ev.kind === 'fall' ? ev.off : 7;
    if (!ev || ev.kind !== 'fall' || tau > 1) return;
    const B = snap(J), A = ev.from;
    if (tau < 0.6) {
      const e = (tau / 0.6) ** 2, fast = sm01(tau / 0.3);
      const P = mix(A, B, e); const arms = mix(A, B, fast);
      for (const k of ['shR', 'elR', 'wrR', 'shL', 'elL', 'wrL', 'neck', 'head']) P[k] = arms[k];
      P.pos[2] = A.pos[2];
      P.head[0] -= 0.35 * fast * (1 - e);
      put(J, P);
    } else {
      const u = (tau - 0.6) / 0.4, bump = Math.sin(Math.PI * Math.min(1, u)) * (1 - Math.min(1, u));
      J.hips.position.y += 1.1 * bump; J.spine.rotation.x -= 0.2 * bump;
      J.elR.rotation.x -= 0.6 * bump; J.elL.rotation.x -= 0.6 * bump; J.head.rotation.x += 0.25 * bump;
    }
    return;
  }
  const gone = goneSide(), ph = (v.phase || 0) * 0.13;
  hop(J, t, gone, mode, ph);
  if (!ev || ev.kind !== 'stumble' || tau > 1.7) return;
  const K3 = snap(J), A = ev.from;
  // buckle toward the missing side, arms flung out
  onePose(J, { gone, a: -0.5, k: 1.2, ga: -0.8, gout: 0.35, shift: 0.9, z: 0.8, hx: 0.15, roll: 0.3, sx: 0.55, lean: -0.15,
    nx: -0.2, hd: -0.3, armS: [-0.9, 1.3, -0.3, 0.2], armG: [-1.1, 1.4, -0.4, 0.2] });
  const K1 = snap(J);
  // down on its one knee, the hand on the standing side reaching for the ground, the other thrown up
  onePose(J, { gone, a: -1.5, k: 2.0, ga: -0.4, gout: 0.25, shift: 0.6, z: 1.5, hx: 0.35, roll: 0.15, sx: 0.85, lean: 0.1,
    nx: -0.3, hd: -0.25, armS: [-1.5, 0.25, -0.15, 0.6], armG: [-2.2, 0.9, -0.8, 0.2] });
  const K2 = snap(J);
  put(J, tau < 0.3 ? mix(A, K1, sm01(tau / 0.3)) : tau < 0.8 ? mix(K1, K2, sm01((tau - 0.3) / 0.5)) : mix(K2, K3, sm01((tau - 0.8) / 0.9)));
  // blending the angles bends the leg on a curve, so set the hips back down onto the standing foot
  if (tau > 0.3) {
    const S = STYLES.A, st = gone === 'R' ? 'L' : 'R', BASE = S.thigh[1] + S.shin[1] + S.foot[1] + S.pelvis[1] / 2;
    const a = J['hip' + st].rotation.x + J.hips.rotation.x, k = J['kn' + st].rotation.x;
    J.hips.position.y = BASE - (S.thigh[1] + S.shin[1] - footDrop(a, k));
  }
}

const S = STYLES.A, SC = 6, CW = 230, RH = 300;
const rows = [
  ['stumble', 'knR', [0, 0.15, 0.3, 0.55, 0.8, 1.1, 1.4, 1.7]],
  ['hop', 'knR', [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7]],
  ['fall', 'knR,hipL', [0, 0.15, 0.3, 0.45, 0.6, 0.7, 0.85, 1.2]],
];
const out = document.getElementById('out'); out.width = CW * 8 * 2; out.height = RH * rows.length;
const ox = out.getContext('2d'); ox.fillStyle = '#fff'; ox.fillRect(0, 0, out.width, out.height);
for (let r = 0; r < rows.length; r++) {
  const [kind, cuts, ts] = rows[r];
  seed = 1; const f = buildSkinned(src, src); await Promise.all(__LOADS);
  PLAYER.x = 30; PLAYER.z = 20; MOVE.look = 0; MOVE.lookV = 0;
  const v = { phase: 0, dt: 0.016 };
  zombieMove(f, 'shamble', 0.4, v);
  const from = snap(f.userData.J);
  SHOOT.cut = new Set(cuts.split(','));
  for (const c of SHOOT.cut) {
    addStump(f, c); const J = f.userData.J, k = c.slice(0, -1), R = c.slice(-1);
    J[c].visible = false; for (const j of LIMB.sub[k]) J[j + R] = new THREE.Group();
  }
  SHOOT.ev = kind === 'hop' ? null : { kind: kind === 'fall' ? 'fall' : 'stumble', t0: 10, from, off: from.pos[2] + 7 };
  await Promise.all(__LOADS);
  ts.forEach((tt, i) => {
    limbMove(f, 'shamble', 10 + tt, v);
    const c = document.createElement('canvas'); c.width = CW * 2; c.height = RH; const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, CW * 2, RH);
    rasterFigure(f, x, CW * 0.5, 'side', SC, RH - 15);
    rasterFigure(f, x, CW * 1.5, 'front', SC, RH - 15);
    x.fillStyle = '#c00'; x.font = '15px sans-serif'; x.fillText(kind + ' ' + tt, 6, 18); x.fillStyle = '#888'; x.fillRect(0, RH - 15 + 0.4 * SC, CW * 2, 1);
    ox.drawImage(c, i * CW * 2, r * RH);
  });
}
document.title = 'done';
