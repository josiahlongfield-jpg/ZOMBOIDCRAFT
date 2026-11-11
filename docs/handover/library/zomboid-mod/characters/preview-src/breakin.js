// ---------- breaking in (Josiah 27 Oct 10:09): beating on doors and windows, climbing in through a window ----------
// A house window in the game is a one-block sill with an opening over it 0.69 of a block wide and 1.75 tall, through a
// wall a block thick with the glass in the middle, so nothing two blocks tall walks through it. The zombie beats the
// glass in, rakes the teeth out of the frame, hauls itself up onto the sill, slides through on its belly, spills out
// head first onto the floor inside, lies there a moment and gets up. A door it leans into and beats until it gives.
// Hands and feet that touch something reach for it (two-bone reach), so they stay put while the body moves.
const BRK = {
  B: 17.6, floor: 0.4,                        // a block, and where the game's floor sits in the preview
  face: 8.8, rail: 19.65, top: 50.45, half: 6.05,   // the wall's faces (glass at 0), the opening's floor, top and half width
  door: 8.6,                                  // the door's face, ahead of the hips while it beats on it
  winZ: 11.2,                                 // the glass, ahead of the hips while it beats on a window
  cycle: 9.2,                                 // the climb: first blow to standing up inside
  yaw: { bangdoor: 2.05, bangwin: 1.95, climb: 1.62 },
  t0: 0, ev: { glass: 'whole', hit: 0, blow: -1, loop: -1 },
};
const BRK_MODES = { bangdoor: 1, bangwin: 1, climb: 1 };

// rotation matrices the way three.js builds them (XYZ is Rx Ry Rz, YXZ is Ry Rx Rz), row-major 3x3
function brkMul(a, b) { const r = []; for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) r.push(a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]); return r; }
function brkR(x, y, z, order) {
  const cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
  const X = [1, 0, 0, 0, cx, -sx, 0, sx, cx], Y = [cy, 0, sy, 0, 1, 0, -sy, 0, cy], Z = [cz, -sz, 0, sz, cz, 0, 0, 0, 1];
  return order === 'YXZ' ? brkMul(brkMul(Y, X), Z) : brkMul(brkMul(X, Y), Z);
}
const brkAp = (m, v) => [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
const brkApT = (m, v) => [m[0] * v[0] + m[3] * v[1] + m[6] * v[2], m[1] * v[0] + m[4] * v[1] + m[7] * v[2], m[2] * v[0] + m[5] * v[1] + m[8] * v[2]];

// two bones reaching for d (in the upper joint's parent frame; the bones hang down -y at rest). An elbow folds the lower
// bone forward (rotation.x -k), a knee back (+k). Returns the upper joint's [x, z] and the fold k, exact for a joint
// turned x then z with no y.
function brkSolve(d, L1, L2, elbow) {
  const n = Math.hypot(d[0], d[1], d[2]) || 1e-6, len = Math.min(L1 + L2 - 1e-3, Math.max(Math.abs(L1 - L2) + 1e-3, n));
  const e = d.map(v => v * len / n);
  const k = Math.acos(Math.max(-1, Math.min(1, (len * len - L1 * L1 - L2 * L2) / (2 * L1 * L2))));
  const R = L1 + L2 * Math.cos(k), c = Math.asin(Math.max(-1, Math.min(1, e[0] / R)));
  const Q = (elbow ? 1 : -1) * L2 * Math.sin(k);
  const x = Math.atan2(e[2], e[1]) - Math.atan2(Q, -R * Math.cos(c));
  return [Math.atan2(Math.sin(x), Math.cos(x)), c, k];   // kept within a half turn, so easing between poses never spins a limb
}

// where the body's joints are for a pose { pos, hips, spine } (hips turned YXZ, the spine XYZ), in the figure's frame
function brkFrames(P) {
  const S = STYLES.A, Mh = brkR(P.hips[0], P.hips[1], P.hips[2], 'YXZ'), Ms = brkMul(Mh, brkR(P.spine[0], P.spine[1], P.spine[2], 'XYZ'));
  const add = (a, b) => a.map((v, i) => v + b[i]), so = add(brkAp(Mh, [0, S.pelvis[1] / 2, 0]), P.pos);
  return {
    Mh, Ms, hx: P.hips[0], sx: P.hips[0] + P.spine[0],
    sh: s => add(brkAp(Ms, [s * (S.torso[0] / 2 + S.upper[0] / 2 - 0.1), S.torso[1] - S.shoulder - 0.3, 0]), so),
    hp: s => add(brkAp(Mh, [s * S.pelvis[0] * 0.26, -S.pelvis[1] / 2 + 0.4, 0]), P.pos),
  };
}

// one limb's angles from its spec. { t: [x, y, z] } reaches for that point (the wrist, or the ankle) with the hand or
// foot at pitch hw in the world (feet: 0 is flat), or turned by w; otherwise p is the upper bone's pitch in the world
// (0 hangs straight down, -PI/2 points ahead, PI/2 behind, -PI straight up), o spreads it outward, e folds the elbow
// (< 0) or k the knee (> 0), and w turns the hand or a the foot. Returns { u: [x, y, z], l, w }.
function brkLimb(spec, F, R, arm) {
  const S = STYLES.A, s = R === 'R' ? -1 : 1, base = arm ? F.sx : F.hx;
  if (spec.t) {
    const at = arm ? F.sh(s) : F.hp(s), d = brkApT(arm ? F.Ms : F.Mh, spec.t.map((v, i) => v - at[i]));
    const [a, c, k] = arm ? brkSolve(d, S.upper[1] - S.shoulder, S.fore[1] - 0.2, true) : brkSolve(d, S.thigh[1], S.shin[1], false);
    const l = arm ? -k : k, end = base + a + l;
    const w = spec.hw != null ? spec.hw - end : arm ? spec.w || 0 : -end + (spec.a || 0);
    return { u: [a, 0, c], l, w };
  }
  return { u: [spec.p - base, 0, (spec.o || 0) * s], l: arm ? spec.e || 0 : spec.k || 0, w: arm ? spec.w || 0 : spec.a || 0 };
}
// eases between two limb poses the short way round (a reach can come out a whole turn from a keyed angle)
const brkNear = (a, b) => b + Math.round((a - b) / (2 * Math.PI)) * 2 * Math.PI;
const brkMix = (A, B, s) => ({ u: A.u.map((v, i) => v + (brkNear(v, B.u[i]) - v) * s), l: A.l + (brkNear(A.l, B.l) - A.l) * s, w: A.w + (brkNear(A.w, B.w) - A.w) * s });
const brkS = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };

// a track: [[time, value], ...]. Arrays glide through their keys (Catmull-Rom); limb specs ease from key to key,
// reaching for a point that slides between two reaching keys
function brkKey(track, t) {
  let i = 0; while (i + 1 < track.length && track[i + 1][0] <= t) i++;
  return i;
}
function brkArr(track, t) {
  const i = brkKey(track, t);
  if (i + 1 >= track.length || t <= track[0][0]) return track[t <= track[0][0] ? 0 : i][1].slice();
  const [t1, p1] = track[i], [t2, p2] = track[i + 1], [t0, p0] = track[Math.max(0, i - 1)], [t3, p3] = track[Math.min(track.length - 1, i + 2)];
  const h = t2 - t1, s = (t - t1) / h, s2 = s * s, s3 = s2 * s;
  return p1.map((v, k) => {
    const m1 = (p2[k] - p0[k]) / Math.max(1e-6, t2 - t0) * h, m2 = (p3[k] - p1[k]) / Math.max(1e-6, t3 - t1) * h;
    return (2 * s3 - 3 * s2 + 1) * v + (s3 - 2 * s2 + s) * m1 + (-2 * s3 + 3 * s2) * p2[k] + (s3 - s2) * m2;
  });
}
function brkSpec(track, t, F, R, arm) {
  const i = brkKey(track, t);
  if (i + 1 >= track.length || t <= track[0][0]) return brkLimb(track[t <= track[0][0] ? 0 : i][1], F, R, arm);
  const [t1, A] = track[i], [t2, B] = track[i + 1], s = brkS((t - t1) / (t2 - t1));
  if (A.t && B.t) {
    const mid = { t: A.t.map((v, k) => v + (B.t[k] - v) * s) };
    if (A.hw != null && B.hw != null) mid.hw = A.hw + (B.hw - A.hw) * s; else { mid.w = (A.w || 0) + ((B.w || 0) - (A.w || 0)) * s; mid.a = (A.a || 0) + ((B.a || 0) - (A.a || 0)) * s; }
    return brkLimb(mid, F, R, arm);
  }
  return brkMix(brkLimb(A, F, R, arm), brkLimb(B, F, R, arm), s);
}
function brkPut(J, P, neck, head, limbs) {
  J.hips.rotation.order = 'YXZ'; J.hips.position.set(P.pos[0], P.pos[1], P.pos[2]); J.hips.rotation.set(P.hips[0], P.hips[1], P.hips[2]);
  J.spine.rotation.set(P.spine[0], P.spine[1], P.spine[2]); J.neck.rotation.set(neck[0], neck[1], neck[2]); J.head.rotation.set(head[0], head[1], head[2]);
  for (const [j, l, w, R, L] of [['sh', 'el', 'wr', 'R', limbs.aR], ['sh', 'el', 'wr', 'L', limbs.aL], ['hip', 'kn', 'an', 'R', limbs.lR], ['hip', 'kn', 'an', 'L', limbs.lL]]) {
    J[j + R].rotation.set(L.u[0], L.u[1], L.u[2]); J[l + R].rotation.set(L.l, 0, 0); J[w + R].rotation.set(L.w, 0, 0);
  }
}

// ---- the climb, keyed by hand. Body: hips [x, y, z] and their turn, the spine, the neck and head ----
const PI2 = Math.PI / 2;
const CLIMB = {
  root: [
    [0.00, [0, 16.3, -11.0, 0.06, 0, 0]], [0.25, [0, 16.1, -11.3, 0.02, 0, 0]], [0.45, [0, 16.0, -10.8, 0.1, 0, 0]],
    [0.60, [0, 16.1, -11.0, 0.08, 0, 0]], [0.78, [0, 16.1, -11.3, 0.02, 0, 0]], [0.95, [0, 16.0, -10.8, 0.1, 0, 0]],
    [1.10, [0, 16.2, -11.2, 0.04, 0, 0]], [1.38, [0, 16.0, -10.9, 0.12, 0, 0.04]], [1.60, [0, 15.8, -11.1, 0.15, 0, 0]],
    [1.90, [0, 14.8, -11.5, 0.25, 0, 0]],
    // it springs and hauls its chest onto the sill, gets a knee up, then slides through on its belly
    [2.25, [0, 19.0, -11.0, 0.7, 0, 0]], [2.60, [0, 21.6, -10.6, 1.35, 0, 0.05]], [2.95, [0, 21.9, -9.6, 1.5, 0, -0.08]],
    [3.30, [0, 21.7, -6.0, 1.55, 0, 0.06]], [3.70, [0, 21.6, -1.0, 1.6, 0, -0.05]], [4.10, [0, 21.5, 4.0, 1.75, 0, 0.04]],
    // over the inner edge head first: the hands hit the floor, the arms fold and it lands on its face
    [4.45, [0, 20.8, 8.0, 2.15, 0, 0]], [4.70, [0, 15.0, 13.5, 2.25, 0, 0.1]], [4.90, [0, 5.5, 18.8, 1.75, 0, 0.06]],
    [5.05, [0, 2.6, 21.0, PI2, 0, 0]], [5.20, [0, 3.3, 21.0, 1.55, 0, 0]], [5.35, [0, 2.6, 21.1, PI2, 0, 0]],
    [6.10, [0, 2.6, 21.1, PI2, 0, 0]],
    // pushes up, gets onto its hands and knees, one knee and one foot, and stands
    [6.50, [0, 3.6, 20.8, 1.45, 0, 0]], [6.90, [0, 8.6, 17.7, 1.25, 0, 0.04]], [7.25, [0, 10.5, 18.7, 0.35, 0, -0.05]],
    [7.65, [0, 16.2, 19.7, 0.1, 0, 0]], [8.00, [0, 16.5, 19.8, 0.06, 0, 0]], [9.20, [0, 16.5, 19.8, 0.06, 0, 0]],
  ],
  spine: [
    [0.00, [0.25, 0.1, 0]], [0.25, [0.12, 0.35, 0.05]], [0.45, [0.3, -0.15, 0]], [0.60, [0.26, -0.05, 0]], [0.78, [0.12, -0.35, -0.05]],
    [0.95, [0.32, 0.15, 0]], [1.10, [0.22, 0.1, 0]], [1.18, [0.3, 0.25, 0]], [1.38, [0.45, -0.25, 0]], [1.60, [0.55, 0, 0]],
    [1.90, [0.7, 0, 0]], [2.25, [0.7, 0, 0]], [2.60, [0.15, 0, 0.05]], [2.95, [0.05, 0.08, 0]], [3.30, [0, -0.1, 0.05]],
    [3.70, [0, 0.1, -0.05]], [4.10, [0.35, 0, 0]], [4.45, [0.3, 0, 0]], [4.70, [0, 0, 0]], [4.90, [-0.25, 0, 0]],
    [5.05, [-0.05, 0, 0]], [6.10, [-0.1, 0, 0]], [6.50, [-0.45, 0, 0]], [6.90, [0.2, 0, 0]], [7.25, [0.35, 0.1, 0]],
    [7.65, [0.25, 0, 0]], [8.00, [0.18, 0.2, 0]], [9.20, [0.18, 0.2, 0]],
  ],
  head: [   // neck x y z, head x y z
    [0.00, [0, 0, 0, 0.1, 0, 0.15]], [0.25, [-0.1, 0, 0, 0, 0.1, 0.1]], [0.45, [0.05, 0, 0, 0.1, 0, 0.1]], [0.78, [-0.1, 0, 0, 0, -0.1, -0.1]],
    [0.95, [0.05, 0, 0, 0.12, 0, -0.1]], [1.10, [-0.1, 0, 0, -0.1, 0, 0]],
    [1.60, [-0.2, 0, 0, -0.1, 0, 0]], [2.25, [-0.4, 0, 0, -0.3, 0, 0]], [2.60, [-0.6, 0, 0, -0.5, 0, 0.1]], [3.30, [-0.5, 0, 0, -0.45, 0, -0.1]],
    [4.10, [-0.4, 0, 0, -0.3, 0, 0]], [4.45, [-0.6, 0, 0, -0.5, 0, 0]], [4.90, [-0.2, 0, 0, 0, 0, 0]], [5.05, [-0.35, 0.35, 0, -0.2, 0.5, 0]],
    [5.60, [-0.3, 0.2, 0, -0.3, 0.3, 0.1]], [6.10, [-0.4, 0, 0, -0.4, -0.2, 0]], [6.50, [-0.5, 0, 0, -0.4, 0, 0]], [7.00, [-0.2, 0, 0, -0.2, 0, 0]],
    [7.70, [0, 0, 0, 0.1, 0, 0.2]], [8.10, [0, 0.3, 0, 0, 0.5, 0.1]], [9.20, [0, 0.3, 0, 0, 0.5, 0.1]],
  ],
  // the wrist reaching for a point, or the upper arm's pitch in the world
  aR: [
    [0.00, { p: -1.6, o: 0.1, e: -1.2, w: 0.2 }], [0.25, { p: -3.0, o: 0.25, e: -1.7, w: 0.3 }],
    [0.45, { t: [-2.2, 30.0, -3.0], hw: -1.4 }], [0.60, { t: [-2.4, 28.5, -4.6], hw: -1.1 }],
    [0.78, { t: [-3.6, 20.6, -6.5], hw: -PI2 }], [0.95, { t: [-3.6, 20.6, -6.5], hw: -PI2 }], [1.05, { t: [-3.8, 24.0, -6.0], hw: -1.2 }],
    [1.18, { t: [-5.0, 27.0, -2.6], hw: -PI2 }], [1.38, { t: [4.0, 22.6, -2.4], hw: -PI2 }], [1.48, { t: [0, 25.0, -3.0], hw: -1.2 }],
    [1.60, { t: [-3.6, 20.6, -2.0], hw: -PI2 }], [2.45, { t: [-3.6, 20.6, -2.0], hw: -PI2 }],
    [2.70, { t: [-3.6, 25.0, 3.0], hw: -1.2 }], [2.85, { t: [-3.4, 20.6, 5.0], hw: -PI2 }], [3.55, { t: [-3.4, 20.6, 5.0], hw: -PI2 }],
    [3.75, { p: -0.8, o: 0.15, e: -0.2, w: 0.2 }], [4.45, { p: -0.3, o: 0.2, e: -0.15, w: 0.1 }],
    [4.72, { t: [-4.5, 1.4, 27.0], hw: -PI2 }], [6.20, { t: [-4.5, 1.4, 27.0], hw: -PI2 }],
    [6.40, { t: [-4.8, 1.4, 28.0], hw: -PI2 }], [6.70, { t: [-4.8, 1.4, 28.0], hw: -PI2 }], [6.90, { t: [-4.8, 1.4, 26.6], hw: -PI2 }],
    [7.10, { t: [-4.8, 1.4, 26.6], hw: -PI2 }], [7.35, { p: -0.4, o: 0.15, e: -0.5, w: 0.2 }], [7.70, { p: -0.15, o: 0.08, e: -0.3, w: 0.15 }],
    [9.20, { p: -0.15, o: 0.08, e: -0.3, w: 0.15 }],
  ],
  aL: [
    [0.00, { t: [3.5, 20.6, -6.5], hw: -PI2 }], [0.60, { t: [3.5, 20.6, -6.5], hw: -PI2 }], [0.78, { p: -3.0, o: 0.25, e: -1.7, w: 0.3 }],
    [0.95, { t: [1.8, 31.0, -3.0], hw: -1.6 }], [1.10, { t: [1.8, 29.0, -4.8], hw: -1.1 }], [1.30, { t: [3.6, 20.6, -6.5], hw: -PI2 }],
    [1.50, { t: [3.6, 20.6, -6.5], hw: -PI2 }], [1.62, { t: [3.6, 20.6, -2.0], hw: -PI2 }], [2.55, { t: [3.6, 20.6, -2.0], hw: -PI2 }],
    [2.78, { t: [3.6, 25.0, 3.5], hw: -1.2 }], [2.95, { t: [3.4, 20.6, 5.0], hw: -PI2 }], [3.45, { t: [3.4, 20.6, 5.0], hw: -PI2 }],
    [3.65, { p: -0.8, o: 0.15, e: -0.2, w: 0.2 }], [4.45, { p: -0.35, o: 0.2, e: -0.2, w: 0.1 }],
    [4.76, { t: [4.5, 1.4, 26.0], hw: -PI2 }], [6.30, { t: [4.5, 1.4, 26.0], hw: -PI2 }],
    [6.50, { t: [4.8, 1.4, 28.0], hw: -PI2 }], [6.75, { t: [4.8, 1.4, 28.0], hw: -PI2 }], [6.95, { t: [4.8, 1.4, 26.6], hw: -PI2 }],
    [7.15, { t: [4.8, 1.4, 26.6], hw: -PI2 }], [7.40, { p: -0.5, o: 0.15, e: -0.6, w: 0.2 }], [7.70, { p: -0.3, o: 0.1, e: -0.4, w: 0.2 }],
    [8.20, { p: -0.6, o: 0.1, e: -0.5, w: 0.1 }], [9.20, { p: -0.6, o: 0.1, e: -0.5, w: 0.1 }],
  ],
  // the ankle reaching for a point (the foot flat, or tipped by a), or the thigh's pitch in the world
  lR: [
    [0.00, { t: [-1.9, 1.8, -12.6] }], [1.90, { t: [-1.9, 1.8, -12.6] }], [2.10, { t: [-1.9, 2.4, -12.4], a: 0.6 }],
    [2.25, { p: 0.12, o: 0.05, k: 0.4, a: 0.6 }], [2.60, { p: -0.05, o: 0.05, k: 0.5, a: 0.5 }], [2.80, { p: -0.4, o: 0.08, k: 1.5, a: 0.3 }],
    [2.95, { p: -1.45, o: 0.1, k: 2.9, a: 0.4 }], [3.30, { p: 1.6, o: 0.1, k: 0.35, a: 0.4 }], [3.70, { p: 1.62, o: 0.06, k: 0.25, a: 0.5 }],
    [4.10, { p: 1.62, o: 0.08, k: 0.3, a: 0.5 }], [4.45, { p: 1.75, o: 0.1, k: 0.3, a: 0.4 }], [4.70, { p: 2.05, o: 0.12, k: 0.45, a: 0.4 }],
    [4.90, { p: 1.9, o: 0.12, k: 1.4, a: 0.3 }], [5.05, { p: 1.62, o: 0.1, k: 1.8, a: 0.4 }], [5.25, { p: 1.6, o: 0.1, k: 1.0, a: 0.5 }],
    [6.50, { p: 1.58, o: 0.1, k: 1.0, a: 0.6 }], [6.90, { p: -0.52, o: 0.06, k: 2.09, a: 0.9 }], [7.25, { p: 0.0, o: 0.05, k: 1.57, a: 0.9 }],
    [7.65, { t: [-1.9, 1.8, 19.2] }], [9.20, { t: [-1.9, 1.8, 19.2] }],
  ],
  lL: [
    [0.00, { t: [1.9, 1.8, -14.4] }], [1.90, { t: [1.9, 1.8, -14.4] }], [2.15, { t: [1.9, 2.4, -14.2], a: 0.6 }],
    [2.30, { p: 0.2, o: 0.08, k: 0.5, a: 0.6 }], [2.65, { p: 0.05, o: 0.06, k: 0.7, a: 0.5 }], [3.00, { p: 0.5, o: 0.08, k: 1.0, a: 0.4 }],
    [3.30, { p: 1.5, o: 0.1, k: 0.5, a: 0.4 }], [3.70, { p: 1.6, o: 0.08, k: 0.3, a: 0.5 }], [4.10, { p: 1.62, o: 0.1, k: 0.35, a: 0.5 }],
    [4.45, { p: 1.78, o: 0.12, k: 0.35, a: 0.4 }], [4.70, { p: 2.1, o: 0.14, k: 0.5, a: 0.4 }], [4.90, { p: 2.0, o: 0.14, k: 1.2, a: 0.3 }],
    [5.08, { p: 1.65, o: 0.12, k: 1.7, a: 0.4 }], [5.30, { p: 1.62, o: 0.12, k: 1.05, a: 0.5 }], [6.50, { p: 1.6, o: 0.12, k: 1.0, a: 0.6 }],
    [6.90, { p: -0.45, o: 0.08, k: 2.0, a: 0.9 }], [7.25, { t: [1.9, 1.8, 24.2] }], [7.65, { t: [1.9, 1.8, 22.0] }], [9.20, { t: [1.9, 1.8, 22.0] }],
  ],
};

// the climb at time u into it
function brkClimb(J, u, ph) {
  const r = brkArr(CLIMB.root, u), P = { pos: r.slice(0, 3), hips: r.slice(3, 6), spine: brkArr(CLIMB.spine, u) }, hn = brkArr(CLIMB.head, u);
  const F = brkFrames(P), sin = Math.sin;
  const L = { aR: brkSpec(CLIMB.aR, u, F, 'R', true), aL: brkSpec(CLIMB.aL, u, F, 'L', true), lR: brkSpec(CLIMB.lR, u, F, 'R', false), lL: brkSpec(CLIMB.lL, u, F, 'L', false) };
  // the feet scrabble at the wall while it hauls itself up; it twitches where it lies
  const scr = brkS((u - 2.2) / 0.15) * (1 - brkS((u - 2.95) / 0.15));
  L.lR.u[0] += scr * sin(u * 31) * 0.22; L.lL.u[0] += scr * sin(u * 29 + 1.3) * 0.22; L.lR.l += scr * Math.max(0, sin(u * 31)) * 0.3; L.lL.l += scr * Math.max(0, sin(u * 29 + 1.3)) * 0.3;
  const lie = brkS((u - 5.3) / 0.2) * (1 - brkS((u - 6.1) / 0.2));
  L.lR.l += lie * Math.max(0, sin(u * 7.3)) * 0.25; L.aL.w += lie * sin(u * 13) * 0.15; L.aR.w += lie * sin(u * 11 + 2) * 0.15;
  hn[5] += sin(u * 9 + ph) * 0.03;
  brkPut(J, P, hn.slice(0, 3), hn.slice(3, 6), L);
  BRK.ev.glass = u < 0.45 ? 'whole' : u < 0.95 ? 'cracked' : u < 1.33 ? 'teeth' : 'clear';
}

// beating on a door or the glass of a window: one blow a second (the game's), arms in turn, every fourth both at once,
// leaning in with the body and the head thrown into it, the free hand pawing at it
function brkBang(J, t, ph, win) {
  const tt = t + ph * 0.37, n = Math.floor(tt), b = tt - n, right = n % 2 === 0, both = n % 4 === 3;
  const wind = brkS((b - 0.05) / 0.35) * (1 - brkS((b - 0.42) / 0.13)), hit = brkS((b - 0.5) / 0.06) * (1 - brkS((b - 0.6) / 0.25));
  const sin = Math.sin, Z = win ? BRK.winZ - 2.8 : BRK.door - 2.9;
  const P = win ? {
    pos: [0, 16.0 - 0.3 * hit, 0.3 * hit + (both ? 0.4 * hit : 0)], hips: [0.1 + 0.03 * hit, 0, 0],
    spine: [0.32 - 0.12 * wind + 0.1 * hit + (both ? 0.06 * hit : 0), both ? 0 : (right ? 1 : -1) * 0.22 * wind, 0],
  } : {
    pos: [0, 16.0 - 0.3 * hit, -1.6 + 0.3 * hit + (both ? 0.5 * hit : 0)], hips: [0.06 + 0.02 * hit, 0, 0],
    spine: [0.3 - 0.12 * wind + 0.08 * hit + (both ? 0.06 * hit : 0), both ? 0 : (right ? 1 : -1) * 0.25 * wind, 0],
  };
  const F = brkFrames(P);
  // the free hand leans on it and paws: knuckles on the door, the palm smearing down the glass
  const rest = R => {
    const s = R === 'R' ? -1 : 1, paw = sin(tt * 3.1 + (R === 'R' ? 0 : 1.7));
    return win ? { t: [s * 3.2, 27.5 + 1.4 * paw, BRK.winZ - 2.7], hw: -1.5 } : { t: [s * 3.0, 25.5 + 0.8 * paw, BRK.door - 2.75], hw: -1.25 + 0.1 * paw };
  };
  const blow = R => {
    const s = R === 'R' ? -1 : 1, up = both ? { p: -3.0, o: 0.15, e: -1.3, w: 0.3 } : { p: -2.9, o: 0.3, e: -1.9, w: 0.4 };
    const at = { t: [s * (both ? 2.8 : 2.4), (win ? 30.5 : 30.0) + (both ? 1 : 0), Z], hw: win ? -1.5 : -1.25 };
    const back = { t: [s * 2.8, win ? 28.5 : 27.0, Z - 0.9], hw: win ? -1.3 : -0.9 };
    const track = [[0, rest(R)], [0.4, up], [0.55, at], [0.7, back], [1.0, rest(R)]];
    // the swing speeds up into the hit
    const u = b < 0.4 || b > 0.55 ? b : 0.4 + 0.15 * ((b - 0.4) / 0.15) ** 1.8;
    return brkSpec(track, u, F, R, true);
  };
  const L = {
    aR: right || both ? blow('R') : brkLimb(rest('R'), F, 'R', true), aL: !right || both ? blow('L') : brkLimb(rest('L'), F, 'L', true),
    lR: brkLimb({ t: win ? [-1.9, 1.8, -1.4] : [-1.9, 1.8, -0.2] }, F, 'R', false), lL: brkLimb({ t: win ? [1.9, 1.8, -3.4] : [1.9, 1.8, -4.2] }, F, 'L', false),
  };
  const butt = both ? hit : 0, tilt = right ? -1 : 1;
  const neck = [0.05 - 0.15 * wind + 0.08 * hit + 0.15 * butt, 0, 0], head = [(win ? -0.05 : 0.1) - 0.1 * wind + 0.1 * hit + 0.2 * butt, sin(tt * 0.7) * 0.1, 0.12 * tilt * wind + sin(tt * 9 + ph) * 0.02];
  brkPut(J, P, neck, head, L);
  BRK.ev.hit = hit; BRK.ev.blow = n;
}

// the movement for a break-in mode, at the preview's time t (the climb runs from when its button was pressed)
function breakIn(J, mode, t, v) {
  const ph = v.phase || 0;
  if (mode === 'climb') {
    const u = t - BRK.t0, loop = Math.floor(u / BRK.cycle);
    BRK.ev.loop = loop; BRK.ev.u = u - loop * BRK.cycle;
    return brkClimb(J, BRK.ev.u, ph);
  }
  return brkBang(J, t, ph, mode === 'bangwin');
}

// ---- the props: the door and the window it's at, a block each in the game's sizes, in the zombie's frame ----
// painted like the town's houses: weathered clapboard, chipped white trim, an old panel door, dirty glass
function brkPaint(kind) {
  return (c, W, H, f) => {
    const side = f !== 'py' && f !== 'ny';
    if (kind === 'siding') {
      c.fillStyle = '#6f7766'; c.fillRect(0, 0, W, H);
      if (side) for (let y = 0; y < H; y += Math.max(4, Math.round(H / 18))) { c.fillStyle = 'rgba(0,0,0,.28)'; c.fillRect(0, y, W, 2); c.fillStyle = 'rgba(255,255,240,.06)'; c.fillRect(0, y + 2, W, 1); }
      noise(c, W, H, 0.12, 2);
      for (let i = 0; i < W * H / 260; i++) blob(c, rnd() * W, rnd() * H, 2 + rnd() * 6, rnd() < 0.6 ? 'rgba(40,34,22,.2)' : 'rgba(210,205,180,.12)', 5);
      if (side) { const g = c.createLinearGradient(0, H * 0.7, 0, H); g.addColorStop(0, 'rgba(40,34,20,0)'); g.addColorStop(1, 'rgba(40,34,20,.45)'); c.fillStyle = g; c.fillRect(0, 0, W, H); }
    } else if (kind === 'trim') {
      c.fillStyle = '#c9c3ae'; c.fillRect(0, 0, W, H); noise(c, W, H, 0.1, 2);
      for (let i = 0; i < W * H / 120; i++) blob(c, rnd() * W, rnd() * H, 1 + rnd() * 3, rnd() < 0.5 ? 'rgba(90,70,48,.45)' : 'rgba(60,52,40,.3)', 3);
    } else if (kind === 'door') {
      c.fillStyle = '#5a4130'; c.fillRect(0, 0, W, H); noise(c, W, H, 0.14, 2);
      if (f === 'pz' || f === 'nz') {
        const m = Math.round(W * 0.12);
        for (const [x0, y0, x1, y1] of [[m, m, W / 2 - m / 2, H * 0.45], [W / 2 + m / 2, m, W - m, H * 0.45], [m, H * 0.52, W / 2 - m / 2, H - m], [W / 2 + m / 2, H * 0.52, W - m, H - m]]) {
          c.fillStyle = 'rgba(0,0,0,.3)'; c.fillRect(x0, y0, x1 - x0, 2); c.fillRect(x0, y0, 2, y1 - y0);
          c.fillStyle = 'rgba(255,240,210,.1)'; c.fillRect(x0, y1 - 2, x1 - x0, 2); c.fillRect(x1 - 2, y0, 2, y1 - y0);
        }
        for (let i = 0; i < W * H / 300; i++) blob(c, rnd() * W, rnd() * H, 2 + rnd() * 5, 'rgba(176,160,130,.35)', 4);     // the last of the paint
        for (let i = 0; i < 14; i++) { c.fillStyle = 'rgba(20,12,8,.5)'; c.fillRect(W * (0.2 + rnd() * 0.6), H * (0.3 + rnd() * 0.3), 1, 3 + rnd() * 10); }  // gouges
      }
    } else if (kind === 'floor') {
      c.fillStyle = '#4a3b2c'; c.fillRect(0, 0, W, H);
      for (let x = 0; x < W; x += Math.max(4, Math.round(W / 10))) { c.fillStyle = 'rgba(0,0,0,.3)'; c.fillRect(x, 0, 1, H); }
      noise(c, W, H, 0.12, 2);
    }
    c.fillStyle = 'rgba(0,0,0,.45)'; c.fillRect(0, 0, W, 1); c.fillRect(0, H - 1, W, 1); c.fillRect(0, 0, 1, H); c.fillRect(W - 1, 0, 1, H);
  };
}
function brkProps() {
  const B = BRK.B, F = BRK.floor, g = new THREE.Group(), P = {};
  const put = (parent, w, h, d, paint, x, y, z) => { const m = box(w, h, d, paint); m.position.set(x, y, z); parent.add(m); return m; };
  const siding = brkPaint('siding'), trim = brkPaint('trim'), wood = brkPaint('door'), floorP = brkPaint('floor');
  // the door: a block wide and two tall, three pixels thick, in its casing in a wall
  const door = new THREE.Group(); g.add(door); P.door = door;
  const z0 = BRK.door, slab = new THREE.Group(); slab.position.set(0, F, z0 + 1.65); door.add(slab); P.slab = slab;
  put(slab, B - 0.4, 2 * B - 0.4, 3.3, wood, 0, B, 0);
  put(slab, 0.9, 0.9, 1.2, gear('#8c7a4a', { shine: true, noise: .15, grime: .4, outline: .5 }), -B / 2 + 2.2, 17.6, -2.1);
  for (const s of [-1, 1]) put(door, 1.8, 2 * B + 1.8, 2.2, trim, s * (B / 2 + 0.9), F + B + 0.9, z0 - 0.4);
  put(door, B + 3.6, 1.8, 2.2, trim, 0, F + 2 * B + 0.9, z0 - 0.4);
  P.doorSides = [-1, 1].map(s => put(door, B, 3 * B, B, siding, s * B, F + 1.5 * B, z0 + B / 2));
  put(door, B, B, B, siding, 0, F + 2.5 * B, z0 + B / 2);
  // the window: the sill block, a wall block either side (the one nearest the camera cut away), the frame with its full-
  // depth jambs and rails, the meeting rail, two panes of glass, the cracks, the teeth left in the frame
  const win = new THREE.Group(); g.add(win); P.win = win;
  put(win, B, B, B, siding, 0, F + B / 2, 0);
  P.sides = [-1, 1].map(s => put(win, B, 3 * B, B, siding, s * B, F + 1.5 * B, 0));
  put(win, B, B * 0.6, B, siding, 0, F + 3 * B + B * 0.3, 0);
  P.jambs = [-1, 1].map(s => put(win, 2.75, 2 * B, B, trim, s * (BRK.half + 1.375), F + 2 * B, 0));
  put(win, B, 1.65, B, trim, 0, F + B + 0.825, 0);
  put(win, B, 2.75, B, trim, 0, F + 3 * B - 1.375, 0);
  put(win, 2 * BRK.half, 1.6, 1.6, trim, 0, F + 2 * B - 0.2, 0);
  put(win, B + 2, 1.2, 3.2, trim, 0, F + B - 0.3, -B / 2 - 1.2);                         // the outside sill nosing
  const glassMat = new THREE.MeshLambertMaterial({ color: 0x9fb4b8, transparent: true, opacity: 0.32, depthWrite: false, side: THREE.DoubleSide });
  P.glassMat = glassMat;
  const pane = (h, y) => { const m = new THREE.Mesh(new THREE.BoxGeometry(2 * BRK.half, h, 0.4), glassMat); m.position.set(0, y, 0); win.add(m); return m; };
  const mid = F + 2 * B - 0.2;   // the meeting rail between the two sashes
  P.panes = [pane(mid - 0.8 - BRK.rail, (mid - 0.8 + BRK.rail) / 2), pane(BRK.top - mid - 0.8, (BRK.top + mid + 0.8) / 2)];
  // cracks: a star of lines from where the first blow landed
  const cc = document.createElement('canvas'); cc.width = 128; cc.height = 320; const cx = cc.getContext('2d');
  cx.strokeStyle = 'rgba(235,240,240,.85)'; cx.lineWidth = 1.2;
  const star = (x0, y0, n, len) => { for (let i = 0; i < n; i++) { let a = i / n * Math.PI * 2 + rnd() * 0.4, x = x0, y = y0; cx.beginPath(); cx.moveTo(x, y); for (let k = 0; k < 5; k++) { a += (rnd() - 0.5) * 0.6; x += Math.cos(a) * len / 5; y += Math.sin(a) * len / 5; cx.lineTo(x, y); } cx.stroke(); } for (let r = 6; r < len; r += 9 + rnd() * 6) { cx.beginPath(); cx.arc(x0, y0, r, 0, Math.PI * 2); cx.globalAlpha = 0.35; cx.stroke(); cx.globalAlpha = 1; } };
  star(70, 160, 11, 90); star(40, 95, 7, 50);
  const crackTex = new THREE.CanvasTexture(cc); crackTex.keep = true;
  P.cracks = new THREE.Mesh(new THREE.PlaneGeometry(2 * BRK.half, BRK.top - BRK.rail), new THREE.MeshBasicMaterial({ map: crackTex, transparent: true, depthWrite: false, side: THREE.DoubleSide }));
  P.cracks.position.set(0, (BRK.top + BRK.rail) / 2, -0.25); win.add(P.cracks);
  // the teeth left standing in the frame once it's broken
  P.teeth = new THREE.Group(); win.add(P.teeth);
  for (let i = 0; i < 9; i++) {
    const along = i / 8, w = 1 + rnd() * 2.2, h = 1.5 + rnd() * 4, m = new THREE.Mesh(new THREE.BoxGeometry(w, h, 0.35), glassMat);
    const edge = i % 3; if (edge === 0) m.position.set(-BRK.half + 1 + along * 10, BRK.rail + h / 2, 0); else if (edge === 1) m.position.set(-BRK.half + w / 2, BRK.rail + 6 + along * 22, 0); else m.position.set(BRK.half - w / 2, BRK.top - 4 - along * 20, 0);
    m.rotation.z = (rnd() - 0.5) * 0.6; P.teeth.add(m);
  }
  // the floor inside, so it's clear where it lands
  P.floor = put(win, 3 * B, 0.4, 2.2 * B, floorP, 0, 0.2, B / 2 + 1.1 * B);
  P.shards = [];
  return { g, P };
}

// runs the props each frame: shown for their movement, the near wall cut away, the door shaking under the blows,
// the glass cracking, shattering and raked out of the frame, the shards falling and lying where they land
function brkStep(props, mode, dt) {
  const { g, P } = props, on = !!BRK_MODES[mode];
  g.visible = on; if (!on) return;
  P.door.visible = mode === 'bangdoor'; P.win.visible = mode !== 'bangdoor';
  P.win.position.z = mode === 'bangwin' ? BRK.winZ : 0;
  // cut away the wall block and the jamb nearest the camera so the body inside the opening shows
  const nearX = Math.sin(state.yaw) > 0 ? -1 : 1;
  P.sides.forEach((m, i) => { m.visible = (i === 0 ? -1 : 1) !== nearX; });
  P.jambs.forEach((m, i) => { m.visible = (i === 0 ? -1 : 1) !== nearX; });
  if (mode === 'bangdoor') { P.slab.rotation.x = -0.012 * BRK.ev.hit; P.slab.position.z = BRK.door + 1.65 + 0.25 * BRK.ev.hit; }
  const glass = mode === 'climb' ? BRK.ev.glass : 'cracked';
  if (props.loop !== BRK.ev.loop || mode !== props.mode) { for (const s of P.shards) { P.win.remove(s.m); s.m.geometry.dispose(); } P.shards = []; props.loop = BRK.ev.loop; props.broke = false; }
  props.mode = mode;
  P.panes.forEach(p => p.visible = glass === 'whole' || glass === 'cracked');
  P.cracks.visible = glass === 'cracked';
  P.teeth.visible = glass === 'teeth';
  if (mode === 'climb' && !props.broke && (glass === 'teeth' || glass === 'clear')) {
    props.broke = true;
    for (let i = 0; i < 34; i++) {
      const s = 0.5 + rnd() * 1.6, m = new THREE.Mesh(new THREE.BoxGeometry(s, s * (0.6 + rnd()), 0.3), P.glassMat);
      m.position.set((rnd() - 0.5) * 2 * BRK.half, BRK.rail + 4 + rnd() * 24, 0); P.win.add(m);
      P.shards.push({ m, v: new THREE.Vector3((rnd() - 0.5) * 14, rnd() * 8, 10 + rnd() * 26 - (rnd() < 0.25 ? 40 : 0)), w: (rnd() - 0.5) * 18, rest: false, s });
    }
  }
  for (const s of P.shards) {
    if (s.rest) continue;
    s.v.y -= 160 * dt; s.m.position.addScaledVector(s.v, dt); s.m.rotation.x += s.w * dt; s.m.rotation.y += s.w * 0.7 * dt;
    const inWall = Math.abs(s.m.position.z) < BRK.face && Math.abs(s.m.position.x) < BRK.half + 2.75;
    const ground = inWall ? BRK.rail + 0.2 : BRK.floor + 0.2;
    if (s.m.position.y <= ground) { s.m.position.y = ground; s.rest = true; s.m.rotation.set(Math.PI / 2, 0, rnd() * 6); }
  }
}
