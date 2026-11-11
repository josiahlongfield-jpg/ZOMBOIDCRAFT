// ---------- falling dead, and the body staying where it fell (Josiah 27 Oct 07:47 and 07:49) ----------
// The game's ZDeath, line for line. No puff of smoke: it drops as a dead weight out of whatever it was doing, the
// way the killing shot and its own momentum carry it. On its feet the knees go first and it starts to tip, slowly
// and then faster: forward for a runner, which slides on along the ground; back for one shot hard from the front;
// toward the missing side for a hopper. On the way down it turns to land on its face or its back, whichever is
// nearer. Nothing braces: the head and arms trail behind, hit the ground just after the body and flop still. On the
// ground already, a crawler or a zombie knocked flat goes slack where it lies, jolted along by the shot. Then the
// body stays, laid on the ground with its own shapes (limbs and head down on the floor, nothing through it),
// missing whatever was shot off it.
const D = { STILL: 0, SHAMBLE: 1, RUN: 2, LUNGE: 3, HOP: 4, CRAWL: 5, DOWN: 6 };
const D_KIND = ['standing', 'shambling', 'running', 'lunging', 'hopping on one leg', 'crawling', 'knocked flat'];
const D_SETTLED = 1.6, D_GROUND = 0.4, D_REST = 0.15, D_LIMB_REST = 0.25;
const D_SPRINGS = [[11, 0.7, 0.08], [13, 0.45, 0], [10, 0.6, 0.12], [11, 0.65, 0.2]];
const dGroup = k => k === 'spine' ? 0 : k === 'neck' || k === 'head' ? 1 : /^(sh|el|wr)/.test(k) ? 2 : 3;
const dOnGround = f => f.kind === D.CRAWL || f.kind === D.DOWN;

function dBlank() {
  const S = STYLES.A, o = { pos: [0, S.thigh[1] + S.shin[1] + S.foot[1] + S.pelvis[1] / 2, 0], order: 'YXZ', fallDir: 0, tilt: 0 };
  for (const k of JKEYS) o[k] = [0, 0, 0];
  return o;
}
function dCopy(P) {
  const o = { pos: P.pos.slice(), order: P.order, fallDir: P.fallDir || 0, tilt: P.tilt || 0 };
  for (const k of JKEYS) o[k] = P[k].slice();
  return o;
}
function dSet(P, k, x, y, z) { P[k][0] = x; P[k][1] = y; P[k][2] = z; }
function dArm(P, right, sx, out, sy, el, wr, wz) {
  const s = right ? -1 : 1, R = right ? 'R' : 'L';
  dSet(P, 'sh' + R, sx, sy * s, out * s); dSet(P, 'el' + R, el, 0, 0); dSet(P, 'wr' + R, wr, 0, wz * s);
}
function dLeg(P, right, a, k, an, out, tw) {
  const s = right ? -1 : 1, R = right ? 'R' : 'L';
  dSet(P, 'hip' + R, a, tw * s, out * s); dSet(P, 'kn' + R, k, 0, 0); dSet(P, 'an' + R, an, 0, 0);
}
function dMix(out, A, B, s) {
  for (const k of JKEYS) for (let i = 0; i < 3; i++) out[k][i] = A[k][i] + (B[k][i] - A[k][i]) * s;
  for (let i = 0; i < 3; i++) out.pos[i] = A.pos[i] + (B.pos[i] - A.pos[i]) * s;
  out.order = s < 0.5 ? A.order : B.order;
  out.tilt = A.tilt + (B.tilt - A.tilt) * s;
  out.fallDir = B.tilt ? B.fallDir : A.fallDir;
}
// sets a pose on the body; going over, the whole body tips by tilt toward fallDir about the hips, after their own turn
const _dq = new THREE.Quaternion(), _dq2 = new THREE.Quaternion(), _dX = new THREE.Vector3(1, 0, 0), _dY = new THREE.Vector3(0, 1, 0);
function dPut(J, P) {
  put(J, P);
  if (P.order !== 'XYZ' && P.tilt) {
    _dq.setFromAxisAngle(_dY, P.fallDir).multiply(_dq2.setFromAxisAngle(_dX, P.tilt)).multiply(_dq2.setFromAxisAngle(_dY, -P.fallDir));
    J.hips.quaternion.premultiply(_dq);
  }
}

// the pose tau seconds after it died
function dPose(f, tau) {
  if (tau >= D_SETTLED) return dCopy(f.rest);
  const out = dCopy(f.from);
  if (dOnGround(f)) dSlump(out, f.from, f, tau); else dTopple(out, f.from, f, tau);
  hitApply(out, f.part, f.heading, Math.max(0, f.force), tau, f.seed, dOnGround(f));
  // the last moment eases onto exactly the pose the body keeps
  const s = segL(tau, D_SETTLED - 0.3, D_SETTLED);
  if (s > 0) { dMix(out, out, f.rest, s); out.order = f.rest.order; }
  return out;
}

// ---- going over from its feet
function dTopple(out, from, f, tau) {
  const rest = f.rest, T = f.impact, PI = Math.PI;
  const tilt = PI / 2 * dTipped(f, tau);
  const knees = f.give * segL(tau, 0, f.delay + 0.18);
  // the knees give, then it swings down round its feet: slow at first, then fast
  const drop = 1 - (1 - knees) * Math.cos(tilt), along = Math.sin(tilt);
  const slide = f.slide * segL(tau, 0, T + 0.35);
  const sx = Math.sin(f.dir), sz = Math.cos(f.dir);
  const rx = rest.pos[0] - sx * f.slide, rz = rest.pos[2] - sz * f.slide;
  out.pos[0] = from.pos[0] + (rx - from.pos[0]) * along + sx * slide;
  out.pos[2] = from.pos[2] + (rz - from.pos[2]) * along + sz * slide;
  out.pos[1] = from.pos[1] + (rest.pos[1] - from.pos[1]) * drop + dBounce(tau - T, 0.9 + 0.25 * Math.max(0, f.force));
  out.order = 'YXZ'; out.fallDir = f.dir; out.tilt = tilt;
  // it turns on the way down to land on its face or its back
  const turn = segL(tilt / (PI / 2), 0.1, 0.95);
  for (let c = 0; c < 3; c++) out.hips[c] = from.hips[c] + (rest.hips[c] - from.hips[c]) * turn;
  dLimbs(out, from, f.loose, rest, tau, T);
  // the head and arms hang back as the body goes over, and catch up as it nears the ground
  const near = 1 - segL(dTipped(f, tau), 0.5, 0.85);
  const lag = PI / 2 * (dTipped(f, tau - 0.07) - dTipped(f, tau)) * near;
  const lagArms = PI / 2 * (dTipped(f, tau - 0.12) - dTipped(f, tau)) * near * 0.7;
  const rel = f.dir - out.hips[1], cx = Math.cos(rel), cz = -Math.sin(rel);
  hitAdd(out, 'neck', lag * cx * 0.6, 0, lag * cz * 0.6);
  hitAdd(out, 'head', lag * cx, 0, lag * cz);
  hitAdd(out, 'shR', lagArms * cx * 1.1, 0, lagArms * cz * 0.9);
  hitAdd(out, 'shL', lagArms * cx * 1.1, 0, lagArms * cz * 0.9);
}
// how far over it has gone (0 upright to 1 down), tau seconds in
function dTipped(f, tau) {
  if (tau <= f.delay) return 0;
  const u = Math.min(1, (tau - f.delay) / (f.impact - f.delay));
  return f.start * u + (1 - f.start) * u * u;
}
// the thump on landing: up a little and down again, once more smaller
function dBounce(t, size) {
  if (t <= 0) return 0;
  if (t < 0.16) return size * Math.sin(Math.PI * t / 0.16);
  if (t < 0.27) return size * 0.3 * Math.sin(Math.PI * (t - 0.16) / 0.11);
  return 0;
}

// ---- going slack where it lies
function dSlump(out, from, f, tau) {
  const rest = f.rest, s = segL(tau, 0, 0.45), jolt = segL(tau, 0, 0.18);
  out.order = 'XYZ'; out.tilt = 0;
  for (let c = 0; c < 3; c++) out.hips[c] = from.hips[c] + (rest.hips[c] - from.hips[c]) * s;
  // shoved along the ground by the shot at once, then the chest drops
  out.pos[0] = from.pos[0] + (rest.pos[0] - from.pos[0]) * jolt;
  out.pos[2] = from.pos[2] + (rest.pos[2] - from.pos[2]) * jolt;
  out.pos[1] = from.pos[1] + (rest.pos[1] - from.pos[1]) * s + dBounce(tau - 0.3, 0.35);
  dLimbs(out, from, f.loose, rest, tau, 0.3);
}

// ---- the limbs: each swings on toward where it's going, overshoots a little and settles, the loose ones looser
function dLimbs(out, from, loose, rest, tau, land) {
  for (const k of JKEYS) {
    if (k === 'hips') continue;
    const g = D_SPRINGS[dGroup(k)], a = dStep(tau, g[0], g[1]), c = dStep(tau - (land - g[2]), g[0], g[1]);
    for (let i = 0; i < 3; i++) out[k][i] = from[k][i] + (loose[k][i] - from[k][i]) * a + (rest[k][i] - loose[k][i]) * c;
  }
}
// how far a spring has gone toward a new place t seconds after it was given it (overshooting past 1)
function dStep(t, w, zeta) {
  if (t <= 0) return 0;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + zeta * w / wd * Math.sin(wd * t));
}

// ---- the plan: which way it goes, how, and how it ends up
function dPlan(f) {
  const PI = Math.PI;
  const push = f.force < 0 ? 0 : 0.45 + 0.35 * f.force;
  let vx = push * Math.sin(f.heading), vz = push * Math.cos(f.heading);
  if (!dOnGround(f)) {
    vz += { [D.SHAMBLE]: 0.35, [D.RUN]: 1.3, [D.LUNGE]: 0.9, [D.HOP]: 0.2 }[f.kind] || 0;
    if (f.kind === D.HOP) vx += f.goneRight ? -0.6 : 0.6;
    vx += (drnd(f.seed, 1) - 0.5) * 0.3;
    vz += (drnd(f.seed, 2) - 0.5) * 0.2;
    if (vx * vx + vz * vz < 0.05) {
      // nothing pushing it: it drops and goes over any way
      const a = drnd(f.seed, 3) * 2 * PI;
      vx = 0.3 * Math.sin(a); vz = 0.3 * Math.cos(a);
    }
  }
  f.dir = Math.atan2(vx, vz);
  const force = Math.max(0, f.force);
  const tm = { [D.RUN]: [0.6, 0, 0.4, 0.3, 5], [D.LUNGE]: [0.64, 0.04, 0.32, 0.3, 3], [D.HOP]: [0.66, 0.08, 0.2, 0.22, 0],
    [D.SHAMBLE]: [0.8, 0.16, 0.12, 0.28, 1] }[f.kind] || [0.85, 0.2, 0.05, 0.32, 0];
  [f.impact, f.delay, f.start, f.give, f.slide] = tm;
  if (!dOnGround(f)) {
    f.impact -= 0.035 * force;
    f.start = Math.min(0.7, f.start + 0.06 * force);
    f.slide += force;
  }
  if (dOnGround(f)) dSlack(f); else dLieDown(f);
  dDrape(f);
  dLoose(f);
}

// where a body that went over from its feet ends up: flat on its face or its back, limbs flung
function dLieDown(f) {
  const PI = Math.PI, S = STYLES.A, p = f.rest = dBlank();
  // face down if it went over forward-ish, on its back if backward; sideways it rolls whichever way is nearer
  f.prone = Math.abs(f.dir) < PI / 2 + (drnd(f.seed, 4) - 0.5) * 0.5;
  const spin = f.prone ? 0 : f.dir > 0 ? -PI : PI;
  const roll = (drnd(f.seed, 5) - 0.5) * 0.5;
  p.fallDir = f.dir; p.tilt = PI / 2;
  dSet(p, 'hips', 0, f.dir + spin + roll, 0);
  const reach = ({ [D.RUN]: 13, [D.LUNGE]: 12, [D.HOP]: 10, [D.SHAMBLE]: 9.5 }[f.kind] || 8.5) + 0.6 * Math.max(0, f.force) + f.slide;
  p.pos = [f.from.pos[0] + Math.sin(f.dir) * reach, S.pelvis[2] / 2, f.from.pos[2] + Math.cos(f.dir) * reach];
  const bend = (drnd(f.seed, 6) - 0.5) * 0.3, turn = drnd(f.seed, 7) < 0.5 ? -1 : 1;
  dSet(p, 'spine', -0.05, 0, bend);
  const arms = Math.floor(drnd(f.seed, 8) * 4), legs = Math.floor(drnd(f.seed, 9) * 3);
  // arms flung up lie that way by swinging out to the side (along the floor), not forward (through it)
  if (f.prone) {
    // the cheek on the floor
    dSet(p, 'neck', 0, turn * 0.45, 0); dSet(p, 'head', 0, turn * 0.95, 0);
    const faceRight = turn < 0;
    for (const right of [true, false]) {
      if (arms === 1) dArm(p, right, -0.15, right === faceRight ? 2.5 : 0.2, 0, right === faceRight ? -0.75 : -0.3, 0.2, 0);   // up by its face
      else if (arms === 2) dArm(p, right, -0.15, 2.3, 0, -1.0, 0.3, 0);                                                          // both up
      else if (arms === 3) dArm(p, right, -0.2, right === faceRight ? 1.25 : 0.3, 0, -0.55, 0.2, 0);
      else dArm(p, right, -0.15, 0.18, 0, -0.25, 0.2, 0);                                                                         // by its sides
      const bent = legs === 2 && right !== faceRight;
      dLeg(p, right, -0.1, bent ? 0.9 : 0.12, 1.0, bent ? 0.75 : right ? 0.12 : 0.18, 0);
    }
  } else {
    dSet(p, 'neck', 0.05, turn * 0.3, 0); dSet(p, 'head', 0, turn * 0.65, 0);
    for (const right of [true, false]) {
      if (arms === 1) dArm(p, right, 0.15, 1.3, 0, -0.5, 0.2, 0);               // flung wide
      else if (arms === 2) dArm(p, right, 0.15, 2.5, 0, -0.8, 0.2, 0);          // over its head
      else if (arms === 3) dArm(p, right, 0.15, right ? 1.2 : 0.35, 0, -0.45, 0.2, 0);
      else dArm(p, right, 0.15, 0.3, 0, -0.3, 0.2, 0);
      const bent = legs === 2 && right;
      dLeg(p, right, bent ? -0.45 : 0.08, bent ? 0.95 : 0.08, -0.35, bent ? 0.65 : right ? 0.16 : 0.2, 0.5);
    }
  }
}

// where a body already on the ground ends up: slack, face down (a crawler) or on its back, shoved along
function dSlack(f) {
  const PI = Math.PI, S = STYLES.A, p = f.rest = dBlank();
  p.order = 'XYZ';
  f.prone = f.kind === D.CRAWL;
  const push = f.force < 0 ? 0 : 0.45 + 0.35 * f.force;
  const sx = Math.sin(f.heading) * push, sz = Math.cos(f.heading) * push;
  const turn = drnd(f.seed, 7) < 0.5 ? -1 : 1;
  const roll = (drnd(f.seed, 5) - 0.5) * 0.3 + sx * 0.15, yaw = (drnd(f.seed, 6) - 0.5) * 0.3;
  const arms = Math.floor(drnd(f.seed, 8) * 3);
  if (f.prone) {
    dSet(p, 'hips', PI / 2 - (f.legless ? 0.05 : 0), roll, yaw);
    dSet(p, 'neck', -0.1, turn * 0.45, 0); dSet(p, 'head', -0.15, turn * 0.95, 0);
    for (const right of [true, false]) {
      if (arms === 0 || arms === 1 && right) dArm(p, right, -2.55, 0.5, 0.2, -0.9, 0.3, 0);   // still reaching ahead
      else dArm(p, right, -0.15, 0.2, 0, -0.3, 0.2, 0);
      dLeg(p, right, -0.08, 0.15, 1.0, right ? 0.1 : 0.14, 0.1);
    }
  } else {
    dSet(p, 'hips', -PI / 2, roll, yaw);
    dSet(p, 'neck', 0.05, turn * 0.3, 0); dSet(p, 'head', 0, turn * 0.65, 0);
    for (const right of [true, false]) {
      dArm(p, right, 0.15, arms === 0 || arms === 1 && right ? 1.0 : 0.35, 0, -0.4, 0.2, 0);
      dLeg(p, right, 0.05, 0.12, -0.3, right ? 0.15 : 0.2, 0.4);
    }
  }
  // (where it lay: the game's crawl sits 7 back, or 6 on to its back; here wherever it was)
  p.pos = [f.from.pos[0] + sx * 3, S.pelvis[2] / 2, f.from.pos[2] + sz * 3];
}

// the limbs as it goes down, before it lands: the legs buckled, the spine folding the way it falls, the head
// lolling and the arms hanging
function dLoose(f) {
  const p = f.loose = dCopy(f.rest);
  if (dOnGround(f)) return;
  const c = Math.cos(f.dir), s = Math.sin(f.dir);
  dSet(p, 'spine', 0.3 * c, 0, -0.25 * s); dSet(p, 'neck', 0.2, 0, -0.1 * s); dSet(p, 'head', 0.25, 0, -0.15 * s);
  for (const right of [true, false]) dArm(p, right, 0.1, 0.2, 0.1, -0.3, 0.2, 0);
  // a runner's legs fold under it mid-stride; standing, both knees go
  const run = f.kind === D.RUN || f.kind === D.LUNGE, a = run ? -0.75 : -0.65, k = run ? 1.5 : 1.45;
  dLeg(p, true, a, k, -(a + k) * 0.6, 0.08, 0);
  dLeg(p, false, run ? -0.1 : a + 0.1, run ? 1.1 : k - 0.15, -(a + k) * 0.6, 0.1, 0.1);
}

// ---- laying it on the ground with the body's own shapes
let D_SHAPES = null;
// the body's shapes by the joint that carries them (what's been shot off isn't there to lie on)
function dShapes() {
  const J = fig.userData.J, owner = new Map(), out = {};
  for (const k of JKEYS) { owner.set(J[k], k); out[k] = []; }
  J.hips.traverse(o => {
    if (!o.isMesh || !o.geometry) return;
    let k = null, vis = true;
    for (let a = o; a && a !== fig; a = a.parent) { if (!a.visible) vis = false; if (k === null && owner.has(a)) k = owner.get(a); }
    if (!vis || k === null) return;
    if (!o.geometry.boundingBox) o.geometry.computeBoundingBox();
    out[k].push(o);
  });
  return out;
}
// sets the resting body down onto the ground: the hips so the trunk lies on it, then the head and each limb swung
// down until it touches
function dDrape(f) {
  const J = fig.userData.J, p = f.rest;
  D_SHAPES = dShapes();
  fig.updateMatrixWorld(true);
  p.pos[1] += D_GROUND + D_REST - dLowest(p, ['hips', 'spine']);
  if (D_SHAPES.head.length) dSwing(p, 'neck', 'head', 1.2, ['head', 'neck'], D_GROUND + D_REST, 0.55);
  for (const [a, b, c] of [['shR', 'elR', 'wrR'], ['shL', 'elL', 'wrL'], ['hipR', 'knR', 'anR'], ['hipL', 'knL', 'anL']]) {
    if (!D_SHAPES[a].length) continue;
    dSwing(p, a, null, 0, [a, b, c], D_GROUND + D_LIMB_REST, 1.0);
    // then the forearm or shin down too, bending only the way the joint bends
    if (D_SHAPES[b].length || D_SHAPES[c].length) {
      const arm = a.startsWith('sh');
      dSwing(p, b, null, 0, [b, c], D_GROUND + D_LIMB_REST, 1.2, arm ? -2.3 : 0, arm ? 0 : 2.3);
    }
  }
  // whatever still couldn't turn clear of the floor props the body up on it
  const low = dLowest(p, JKEYS);
  if (low < D_GROUND) p.pos[1] += D_GROUND - low;
  D_SHAPES = null;
}
// swings joint i's x (and with it a share of joint j's) until the given parts just touch the ground at target: the
// nearest such turn within max radians either way, else the turn that leaves them nearest without going through
function dSwing(p, i, j, share, keys, target, max, lo = -10, hi = 10) {
  const a0 = p[i][0], b0 = j ? p[j][0] : 0;
  const from = Math.max(-max, lo - a0), to = Math.min(max, hi - a0);
  if (from >= to) return;
  const at = d => { p[i][0] = a0 + d; if (j) p[j][0] = b0 + d * share; return dLowest(p, keys) - target; };
  const n = 36;
  let best = 0, bestH = NaN, cross = NaN, prevA = 0, prevH = 0;
  for (let k = 0; k <= n; k++) {
    const d = from + (to - from) * k / n, h = at(d);
    if (k > 0 && (h > 0) !== (prevH > 0)) {
      // a crossing between the last two: the nearest one to where it was meant to lie
      let l = prevA, hl = prevH, r = d;
      for (let q = 0; q < 10; q++) { const m = (l + r) / 2, hm = at(m); if ((hm > 0) === (hl > 0)) l = m; else r = m; }
      const c = (l + r) / 2;
      if (isNaN(cross) || Math.abs(c) < Math.abs(cross)) cross = c;
    }
    const better = isNaN(bestH) || (h >= 0) !== (bestH >= 0) ? h >= 0 || isNaN(bestH)
      : h >= 0 ? h < bestH || h === bestH && Math.abs(d) < Math.abs(best) : h > bestH;
    if (better) { best = d; bestH = h; }
    prevA = d; prevH = h;
  }
  const d = isNaN(cross) ? best : cross;
  p[i][0] = a0 + d; if (j) p[j][0] = b0 + d * share;
}
// the lowest point of the given parts in the body's own space, posed as p
const _dm = new THREE.Matrix4(), _dinv = new THREE.Matrix4();
function dLowest(p, keys) {
  const J = fig.userData.J;
  dPut(J, p); J.hips.updateMatrixWorld(true);
  _dinv.copy(fig.matrixWorld).invert();
  let low = Infinity;
  for (const k of keys) for (const m of D_SHAPES[k]) {
    _dm.multiplyMatrices(_dinv, m.matrixWorld);
    const e = _dm.elements, b = m.geometry.boundingBox;
    const cx = (b.min.x + b.max.x) / 2, cy = (b.min.y + b.max.y) / 2, cz = (b.min.z + b.max.z) / 2;
    const ex = (b.max.x - b.min.x) / 2, ey = (b.max.y - b.min.y) / 2, ez = (b.max.z - b.min.z) / 2;
    const y = e[1] * cx + e[5] * cy + e[9] * cz + e[13] - (Math.abs(e[1]) * ex + Math.abs(e[5]) * ey + Math.abs(e[9]) * ez);
    if (y < low) low = y;
  }
  return low;
}

// it dies now: how it goes down, from what it was doing (on how many legs it had before the shot) and the shot
function dieNow(t0, gunKey, part, heading, n = legsGone()) {
  const J = fig.userData.J, from = snap(J), mode = state.anim;
  from.fallDir = 0; from.tilt = 0;
  // dead: the eyes go out
  if (fig.userData.eyes) fig.userData.eyes.visible = false;
  const kind = LEGS.off || n === 2 ? (mode === 'down' ? D.DOWN : D.CRAWL) : n === 1 ? D.HOP
    : mode === 'crawler' ? D.CRAWL : mode === 'down' ? D.DOWN : mode === 'attack' ? D.LUNGE
    : mode === 'chase' ? D.RUN : mode === 'shamble' ? D.SHAMBLE : D.STILL;
  const f = { kind, heading: heading ?? 0, force: heading == null ? -1 : ({ pistol: 0, rifle: 1, hunting: 2, shotgun: 3 }[gunKey] ?? 1),
    part, seed: Math.floor(rnd() * 256), goneRight: n === 1 && goneSide() === 'R', legless: !!LEGS.off, from };
  dPlan(f);
  put(J, from); J.hips.updateMatrixWorld(true);
  HITS.length = 0;
  return { t0, f };
}
