
// ---------------------------------------------------------------- the test room: it roams, climbs, hides, drags a body and hears noises, all on its own
// Nothing here is keyframed. Its body slides over the room's surfaces, each foot finds a spot on whatever is under it and
// the joints are solved to reach it; a few needs decide what it wants, and every choice is a destination, a speed and a
// held pose. The body it drags is a loose chain of points that falls, swings and scrapes the floor by itself.
const ROOM = { a: [-8, 0, -6], b: [8, 7, 6] };
const CRATES = [{ a: [4.3, 0, -6], b: [6.4, 1.1, -4.1] }, { a: [-3.2, 0, 3.6], b: [-1.5, 0.85, 5.4] }, { a: [1.2, 0, -1.2], b: [2.3, 0.55, -0.1] }];
const DOOR = { x0: -1.1, x1: 1.1, y1: 2.5 };          // a doorway in the south wall, where you stand
const DARK = { p: [-8, 7, -6], r: 6.5 };             // the corner the light never reaches
const PLAYER_ROOM = [0, 0, 5.3];
const NAV_H = 2.05;                                  // how far its body keeps off walls and ceilings (it hugs them)
const STAND_H = 2.5, CLING_SPREAD = 1.85;            // on the floor it stands tall on legs drawn in; on walls it spreads them
const floorness = c => smooth((c.up[1] - 0.55) / 0.4);
const NAV_IN = { a: add(ROOM.a, [NAV_H, NAV_H, NAV_H]), b: sub(ROOM.b, [NAV_H, NAV_H, NAV_H]) };
// the surfaces it walks on, as the planes its body moves over (NAV_H off each one)
const NAV_FACES = [
  { name: 'the floor', n: [0, 1, 0], k: 1, v: NAV_IN.a[1] }, { name: 'the ceiling', n: [0, -1, 0], k: 1, v: NAV_IN.b[1] },
  { name: 'the left wall', n: [1, 0, 0], k: 0, v: NAV_IN.a[0] }, { name: 'the right wall', n: [-1, 0, 0], k: 0, v: NAV_IN.b[0] },
  { name: 'the back wall', n: [0, 0, 1], k: 2, v: NAV_IN.a[2] }, { name: 'the door wall', n: [0, 0, -1], k: 2, v: NAV_IN.b[2] },
];
const onFace = (p, f) => { const F = NAV_FACES[f], q = [0, 1, 2].map(k => clamp(p[k], NAV_IN.a[k], NAV_IN.b[k])); q[F.k] = F.v; return q; };
// the shortest way over the walls from p on face fp to q on face fq: straight across, over one edge, or over a wall between
function edgePt(p, fa, q, fb) {
  const A = NAV_FACES[fa], B = NAV_FACES[fb], j = 3 - A.k - B.k;
  const dp = Math.abs(p[B.k] - B.v), dq = Math.abs(q[A.k] - A.v), t = dp + dq > 1e-6 ? dp / (dp + dq) : 0.5, e = [0, 0, 0];
  e[A.k] = A.v; e[B.k] = B.v; e[j] = clamp(lerp(p[j], q[j], t), NAV_IN.a[j], NAV_IN.b[j]);
  return e;
}
function navRoute(p, fp, q, fq) {
  if (fp === fq) return [{ p: q, f: fq }];
  if (NAV_FACES[fp].k !== NAV_FACES[fq].k) return [{ p: edgePt(p, fp, q, fq), f: fp }, { p: q, f: fq }];
  let best = null, bd = 1e9;
  for (let m = 0; m < 6; m++) {
    if (NAV_FACES[m].k === NAV_FACES[fp].k) continue;
    const e1 = edgePt(p, fp, q, m), e2 = edgePt(e1, m, q, fq), d = len(sub(e1, p)) + len(sub(e2, e1)) + len(sub(q, e2));
    if (d < bd) { bd = d; best = [{ p: e1, f: fp }, { p: e2, f: m }, { p: q, f: fq }]; }
  }
  return best;
}
// which way is "up" for its body here: the surface under it, blended across the edge as it goes over onto the next
function navUp(p) {
  let s = [0, 0, 0];
  for (const F of NAV_FACES) { const w = smooth(1 - Math.abs(p[F.k] - F.v) / 0.9); if (w > 0) s = add(s, mul(F.n, w)); }
  return len(s) > 1e-6 ? norm(s) : Y;
}
// a ray against the inside of the room and the crates: the spot it hits and that surface's normal
function castRoom(o, d, maxT) {
  let bt = maxT, bn = null;
  for (let k = 0; k < 3; k++) {
    if (Math.abs(d[k]) < 1e-9) continue;
    const t = ((d[k] > 0 ? ROOM.b[k] : ROOM.a[k]) - o[k]) / d[k];
    if (t > 0 && t < bt) { bt = t; bn = [0, 0, 0]; bn[k] = d[k] > 0 ? -1 : 1; }
  }
  for (const c of CRATES) {
    let t0 = -1e9, t1 = 1e9, nk = -1, ns = 0, miss = false;
    for (let k = 0; k < 3; k++) {
      if (Math.abs(d[k]) < 1e-9) { if (o[k] < c.a[k] || o[k] > c.b[k]) miss = true; continue; }
      let ta = (c.a[k] - o[k]) / d[k], tb = (c.b[k] - o[k]) / d[k], s = -1;
      if (ta > tb) { const x = ta; ta = tb; tb = x; s = 1; }
      if (ta > t0) { t0 = ta; nk = k; ns = s; }
      t1 = Math.min(t1, tb);
    }
    if (!miss && nk >= 0 && t0 <= t1 && t0 > 0 && t0 < bt) { bt = t0; bn = [0, 0, 0]; bn[nk] = ns; }
  }
  return bn ? { p: add(o, mul(d, bt)), n: bn } : null;
}
// where a foot lands: along the line from its hip out to its resting spot, or else straight down onto whatever is there
function footSpot(H, P0, up) {
  const d = sub(P0, H), L = len(d) || 1;
  return castRoom(H, mul(d, 1 / L), L * 1.4) || castRoom(add(P0, mul(up, 1.5)), mul(up, -1), 4) || { p: P0, n: up };
}

// ---------------------------------------------------------------- the body it drags: seven points held together by distances
// 0 head, 1 neck, 2 hips, 3 knees, 4 feet, 5 and 6 hands
const KLEN = [[0, 1, 0.2], [1, 2, 0.73], [2, 3, 0.37], [3, 4, 0.34]], KMIN = [[1, 3, 0.8], [2, 4, 0.44], [0, 2, 0.86]], KRAD = [0.15, 0.08, 0.13, 0.08, 0.07, 0.05, 0.05];
// where it holds a body: the head under the point, back to it, gripped by the beard when it feeds
const GRIP = (() => { const hy = norm([0, 0.97, -0.25]), hz = norm([0, 0.25, 0.97]), h = [0, -0.64, 0.74]; return { head: h, neck: sub(sub(h, mul(hy, 0.2)), mul(hz, 0.04)) }; })();
function makeCorpse(p, yaw) {
  const f = [Math.sin(yaw), 0, Math.cos(yaw)], s = [Math.cos(yaw), 0, -Math.sin(yaw)], y = 0.13;
  const x = [add(p, add(mul(f, 0.2), [0, 0.02, 0])), p, add(p, mul(f, -0.73)), add(p, mul(f, -1.1)), add(p, mul(f, -1.44)), add(p, add(mul(f, -0.5), mul(s, -0.35))), add(p, add(mul(f, -0.5), mul(s, 0.35)))];
  for (const q of x) q[1] = y;
  return { x, o: x.map(q => q.slice()), side: s, pin: 0, husk: 0 };
}
function stepCorpse(K, dt, body) {
  if (dt <= 0) return;
  const n = 3, h = dt / n;
  const pinH = body ? at(body, GRIP.head) : null, pinN = body ? at(body, GRIP.neck) : null;
  for (let sub_ = 0; sub_ < n; sub_++) {
    for (let i = 0; i < 7; i++) {
      const x = K.x[i], v = mul(sub(x, K.o[i]), 0.992);
      K.o[i] = x.slice(); K.x[i] = add(add(x, v), [0, -9.8 * h * h, 0]);
    }
    for (let it = 0; it < 6; it++) {
      for (const [a, b, L] of KLEN) dist(a, b, L, 0);
      for (const [a, b, L] of KMIN) dist(a, b, L, -1);
      // hands hang from the shoulders
      const u = norm(sub(K.x[1], K.x[2])), sd = K.side;
      for (const [hi, s] of [[5, -1], [6, 1]]) {
        const S = add(add(K.x[1], mul(u, -0.09)), mul(sd, s * 0.24)), d = sub(K.x[hi], S), l = len(d);
        if (l > 0.6) K.x[hi] = add(S, mul(d, 0.6 / l));
      }
      if (K.pin > 0 && pinH) { K.x[0] = lerp3(K.x[0], pinH, K.pin); K.x[1] = lerp3(K.x[1], pinN, K.pin); }
      for (let i = 0; i < 7; i++) collide(i);
    }
  }
  // which way its shoulders face: carried along from the last frame, or the creature's when it is held
  const u = norm(sub(K.x[1], K.x[2]));
  let s = K.pin > 0.5 && body ? ap(body.R, [1, 0, 0]) : K.side;
  s = sub(s, mul(u, dot(s, u)));
  K.side = len(s) > 1e-3 ? norm(s) : norm(cross(u, Y));
  function dist(a, b, L, mode) {
    const d = sub(K.x[b], K.x[a]), l = len(d) || 1e-6;
    if (mode < 0 && l >= L) return;
    const c = mul(d, (l - L) / l * 0.5);
    K.x[a] = add(K.x[a], c); K.x[b] = sub(K.x[b], c);
  }
  function collide(i) {
    const r = KRAD[i], x = K.x[i], o = K.o[i];
    for (let k = 0; k < 3; k++) {
      const lo = ROOM.a[k] + r, hi = ROOM.b[k] - r;
      if (x[k] < lo || x[k] > hi) {
        x[k] = clamp(x[k], lo, hi);
        // scraping: the other two directions lose most of their speed against the surface
        for (let j = 0; j < 3; j++) if (j !== k) o[j] = lerp(o[j], x[j], 0.45);
      }
    }
    for (const c of CRATES) {
      if (x[0] < c.a[0] - r || x[0] > c.b[0] + r || x[1] < c.a[1] - r || x[1] > c.b[1] + r || x[2] < c.a[2] - r || x[2] > c.b[2] + r) continue;
      let bk = 0, bd = 1e9, bv = 0;
      for (let k = 0; k < 3; k++) for (const [v, s] of [[c.a[k] - r, -1], [c.b[k] + r, 1]]) { const d = Math.abs(x[k] - v); if (d < bd && !(k === 1 && s < 0)) { bd = d; bk = k; bv = v; } }
      x[bk] = bv;
      for (let j = 0; j < 3; j++) if (j !== bk) o[j] = lerp(o[j], x[j], 0.45);
    }
  }
}
function corpseFrames(K) {
  const H = K.x[0], N = K.x[1], P = K.x[2], u = norm(sub(N, P)), x = norm(sub(K.side, mul(u, dot(K.side, u)))), f = cross(x, u);
  const hy = norm(sub(H, N)), hx = norm(sub(x, mul(hy, dot(x, hy)))), hz = cross(hx, hy);
  return { head: frame(H, cols(hx, hy, hz)), torso: frame(add(N, mul(u, 0.27)), cols(x, u, f)) };
}
function drawCorpse(m, K, fr) {
  const hf = fr.head, f = fr.torso, Hd = p => at(hf, p), W = p => at(f, p);
  const hax = [ap(hf.R, [1, 0, 0]), ap(hf.R, [0, 1, 0]), ap(hf.R, [0, 0, 1])], ax = [ap(f.R, [1, 0, 0]), ap(f.R, [0, 1, 0]), ap(f.R, [0, 0, 1])];
  const skin = K.husk ? tint(MAT.skin, 0.72) : MAT.skin;
  m.mat = skin; ellipsoid(m, Hd([0, 0, 0]), hax, [0.15, 0.185, 0.165], 18, 12);
  ellipsoid(m, Hd([0, -0.045, 0.16]), hax, [0.028, 0.038, 0.03], 8, 6);
  m.mat = MAT.mouth; ellipsoid(m, Hd([0, -0.11, 0.135]), hax, [0.048, 0.038, 0.03], 10, 6);
  m.mat = skin; tube(m, [Hd([0, -0.1, -0.02]), lerp3(Hd([0, -0.2, -0.03]), W([0, -0.2, 0]), 0.5), W([0, -0.32, 0])], 0.06, { seg: 10 });
  m.mat = MAT.shirt; tube(m, [W([0, -0.27, 0]), W([0, -0.5, 0.01]), W([0, -0.8, 0.01]), W([0, -1.0, 0])], t => (0.2 - 0.03 * t + 0.03 * Math.sin(Math.PI * t)) * (K.husk ? 0.86 : 1), { seg: 16, up: ax[0], shape: [1, 0.58], cap0: true });
  m.mat = MAT.jeans; ellipsoid(m, W([0, -1.0, 0]), ax, [0.18, 0.11, 0.11], 14, 8);
  for (const s of [-1, 1]) {
    const S = W([s * 0.24, -0.36, 0]), Hn = K.x[s < 0 ? 5 : 6], E = add(lerp3(S, Hn, 0.5), [0, -0.05, 0]);
    m.mat = MAT.shirt; ellipsoid(m, S, ax, [0.075, 0.075, 0.075], 10, 6); tube(m, [S, E], t => 0.062 - 0.008 * t, { seg: 10 });
    m.mat = skin; tube(m, [E, Hn], t => 0.05 - 0.01 * t, { seg: 10 }); ellipsoid(m, Hn, ax, [0.04, 0.065, 0.03], 8, 6);
    const P = W([s * 0.1, -1.05, 0.02]), Kn = add(K.x[3], mul(ax[0], s * 0.11)), A = add(K.x[4], mul(ax[0], s * 0.1));
    m.mat = MAT.jeans; tube(m, [P, lerp3(P, Kn, 0.5), Kn], 0.078, { seg: 10 }); tube(m, [Kn, lerp3(Kn, A, 0.5), A], t => 0.07 - 0.012 * t, { seg: 10 });
    const leg = norm(sub(A, Kn)), toe = norm(sub(ax[2], mul(leg, dot(ax[2], leg))));
    m.mat = MAT.shoe; ellipsoid(m, add(A, add(mul(leg, 0.04), mul(toe, 0.06))), [ax[0], leg, toe], [0.055, 0.05, 0.12], 10, 6);
  }
}

// ---------------------------------------------------------------- the creature in the room
const R_ = { seed: 1, t: 0 };
const rnd = () => hash(R_.seed++ * 1.618 + 0.37);
const pick = a => a[Math.floor(rnd() * a.length) % a.length];
function makeRoamer() {
  return { pos: [-2, NAV_H, -1], face: 0, up: [0, 1, 0], fwd: norm([0.6, 0, 0.8]), vel: [0, 0, 0], spd: 0, turn: 0, route: [], speed: 1.6, look: null,
    h: NAV_H, hT: NAV_H, pitch: 0.1, tuck: 0, tuckT: 0, rear: 0, rearT: 0, flinch: 0, feet: null, steps: [null, null, null, null], lift: [0, 0, 0, 0],
    phase: 0, still: 0, fid: 2.5, t: 0, rp: [-2, NAV_H, -1], rv: [0, 0, 0] };
}
// its body frame from where it is: up from the surface, nose along its heading, and the body riding on its steps, dipping
// a little and shifting its weight away from each leg as it lifts
function roamerFrame(c) {
  const right = norm(cross(c.up, c.fwd)), fwd = cross(right, c.up), R0 = cols(right, c.up, fwd);
  let dip = 0, roll = 0, pitch = 0;
  for (let li = 0; li < 4; li++) { const l = c.lift[li], s = li < 2 ? -1 : 1; dip += l; roll += s * l; if (!(li & 1)) pitch -= l; }
  const o = add(c.rp, mul(c.up, c.h - NAV_H - 0.03 * dip));
  return { flat: frame(o, R0), body: frame(o, m3mul(R0, rot(0, c.pitch + 0.035 * pitch, 0.045 * roll))) };
}
// turn a heading toward another about the up axis, no faster than rate
function turnToward(v, want, up, maxA) {
  const a = Math.acos(clamp(dot(v, want), -1, 1)), s = Math.sign(dot(cross(v, want), up)) || 1, t = Math.min(a, maxA) * s;
  const side = cross(up, v);
  return { v: norm(add(mul(v, Math.cos(t)), mul(side, Math.sin(t)))), a: t };
}
const STRIDE = 2.2, GAIT_OFF = [0.25, 0, 0.75, 0.5];   // a wave: left rear, left front, right rear, right front
function moveRoamer(c, dt) {
  c.t += dt;
  const e = k => 1 - Math.exp(-dt * k);
  // steer for the next waypoint; only the last one is slowed for, the edges between walls are rolled over without stopping
  while (c.route.length && len(sub(c.route[0].p, c.pos)) < 0.03) { c.pos = c.route[0].p.slice(); c.face = c.route[0].f; c.route.shift(); }
  let dir = null, remain = 0;
  if (c.route.length) {
    const d = sub(c.route[0].p, c.pos); dir = norm(d); remain = len(d);
    for (let i = 1; i < c.route.length; i++) remain += len(sub(c.route[i].p, c.route[i - 1].p));
  }
  let want = 0;
  if (dir && dt > 0) {
    const fl = sub(dir, mul(c.up, dot(dir, c.up))), al = len(fl) > 1e-3 ? dot(norm(fl), c.fwd) : 1;
    want = c.speed * Math.pow(clamp((al + 0.15) / 1.15, 0, 1), 1.6);
    want = Math.min(want, Math.sqrt(2 * 1.5 * remain) + 0.08);
  }
  c.spd += clamp(want - c.spd, -2.6 * dt, 1.8 * dt);
  if (!dir) c.spd = 0;
  let go = c.spd * dt;
  while (go > 0 && c.route.length) {
    const w = c.route[0], d = sub(w.p, c.pos), dl = len(d);
    if (dl <= go) { c.pos = w.p.slice(); c.face = w.f; c.route.shift(); go -= dl; } else { c.pos = add(c.pos, mul(d, go / dl)); c.face = w.f; go = 0; }
  }
  if (!c.route.length) c.spd = Math.min(c.spd, 0.2);
  c.vel = lerp3(c.vel, dir ? mul(dir, c.spd) : [0, 0, 0], e(6));
  c.still = c.spd > 0.05 ? 0 : c.still + dt;
  // the body itself follows its path on a spring, so it rounds the edges between walls instead of snapping round them
  if (dt > 0) { const w = 9, h = Math.min(dt, 1 / 30); for (let t = 0; t < dt - 1e-9; t += h) { const k = Math.min(h, dt - t); c.rv = add(c.rv, mul(sub(mul(sub(c.pos, c.rp), w * w), mul(c.rv, 2 * w)), k)); c.rp = add(c.rp, mul(c.rv, k)); } }
  // up follows the surface under it and where its feet are planted
  let fn = [0, 0, 0];
  if (c.feet) for (let i = 0; i < 4; i++) if (!c.steps[i]) fn = add(fn, c.feet[i].n);
  const upT = len(fn) > 0.5 ? norm(add(mul(navUp(c.pos), 0.65), mul(norm(fn), 0.35))) : navUp(c.pos);
  c.up = norm(lerp3(c.up, upT, e(3.2)));
  c.fwd = sub(c.fwd, mul(c.up, dot(c.fwd, c.up)));
  c.fwd = len(c.fwd) > 1e-3 ? norm(c.fwd) : norm(cross(Math.abs(c.up[0]) > 0.7 ? [0, 0, 1] : [1, 0, 0], c.up));
  // the nose turns smoothly toward where it is going or what it is watching
  let wf = dir ? dir : c.look ? sub(c.look, c.pos) : null, turn = 0;
  if (wf && dt > 0) {
    wf = sub(wf, mul(c.up, dot(wf, c.up)));
    if (len(wf) > 1e-3) {
      wf = norm(wf);
      const a = Math.acos(clamp(dot(c.fwd, wf), -1, 1)), r = turnToward(c.fwd, wf, c.up, Math.min(a * e(3.5), 1.9 * dt));
      c.fwd = r.v; turn = r.a / dt;
    }
  }
  c.turn = lerp(c.turn, turn, e(8));
  // the walk's rhythm runs on distance covered and on turning
  c.phase += dt > 0 ? (c.spd * dt + Math.abs(c.turn) * dt * 1.5) / STRIDE : 0;
  // height, tuck, rearing up and the jolt of a fright, all eased
  c.tuck = lerp(c.tuck, c.tuckT, e(2.5)); c.rear = lerp(c.rear, c.rearT, e(4));
  c.flinch = Math.max(0, c.flinch - dt * 2.4);
  const jolt = Math.sin(Math.min(1, c.flinch) * Math.PI);
  const lift_ = ROOMSIM.mind && ROOMSIM.mind.feeding ? 0 : (STAND_H - NAV_H) * floorness(c);
  c.h = lerp(c.h, c.hT + lift_ - 0.75 * c.tuck + 0.28 * c.rear - 0.28 * jolt, e(5));
  c.pitch = lerp(c.pitch, 0.1 - 0.42 * c.rear - 0.14 * jolt, e(8));
}
// the four feet: each lifts in turn as the rhythm comes round to it and swings to a spot a little ahead of where it rests,
// tracking that spot as it goes; standing still, any foot left out of place is tidied in one at a time
function stepFeet(c, dt, fr) {
  const feeding = ROOMSIM.mind && ROOMSIM.mind.feeding, cyc = (c.spd + 1.5 * Math.abs(c.turn)) / STRIDE, sf = clamp(0.3 + 0.12 * c.spd / 1.8, 0.3, 0.42);
  const target = li => {
    const s = li < 2 ? -1 : 1, i = li & 1, L = LEGS[i], r0 = L.restR * lerp(CLING_SPREAD, 1, floorness(c)) * (feeding ? 0.9 : 1),
      // the lower leg can only fold so far against the thigh, so when the body sinks (tucked in, dipping, crouched) the
      // feet move out far enough that the points still reach
      dmin = L.lc - L.a + 0.45, r = Math.max(r0, Math.sqrt(Math.max(0, dmin * dmin - c.h * c.h)) + 0.65);
    const H = at(fr.body, [s * L.hip[0], L.hip[1], L.hip[2]]);
    let off = ap(fr.flat.R, [s * Math.sin(L.restA) * r, -c.h, Math.cos(L.restA) * r]);
    // land ahead by half a stance: along the way it moves, and round the way it turns
    const st = cyc > 0.02 ? (1 - sf) / (2 * cyc) : 0, ta = clamp(c.turn * st, -0.45, 0.45);
    if (Math.abs(ta) > 1e-4) { const side = cross(c.up, off), hu = mul(c.up, dot(off, c.up)), flat = sub(off, hu), sd = cross(c.up, flat); off = add(hu, add(mul(flat, Math.cos(ta)), mul(sd, Math.sin(ta)))); }
    let lead = mul(c.vel, st); if (len(lead) > 0.95) lead = mul(norm(lead), 0.95);
    const P0 = add(add(fr.flat.o, off), lead);
    let h = footSpot(H, P0, c.up);
    // a surface right by the hip (a crate top, the next wall) is closer than the leg folds: drop straight down instead,
    // and if that is still too close, reach further out until the point lands far enough away
    const reach = L.lc - L.a + 0.3;
    if (len(sub(h.p, H)) < reach) { const h2 = castRoom(add(P0, mul(c.up, 1.5)), mul(c.up, -1), 4); if (h2 && len(sub(h2.p, H)) > len(sub(h.p, H))) h = h2; }
    for (let k = 1; k <= 4 && len(sub(h.p, H)) < reach; k++) {
      const d = sub(P0, fr.flat.o), dh = norm(sub(d, mul(c.up, dot(d, c.up)))), P1 = add(P0, mul(dh, 0.4 * k));
      const h3 = castRoom(add(P1, mul(c.up, 1.5)), mul(c.up, -1), 4); if (h3 && len(sub(h3.p, H)) > len(sub(h.p, H))) h = h3;
    }
    return h;
  };
  const cramped = li => { const s = li < 2 ? -1 : 1, L = LEGS[li & 1]; return len(sub(c.feet[li].p, at(fr.body, [s * L.hip[0], L.hip[1], L.hip[2]]))) < L.lc - L.a + 0.3; };
  if (!c.feet) { c.feet = [0, 1, 2, 3].map(li => { const h = target(li); return { p: h.p, n: h.n }; }); c.prevPhase = c.phase; }
  if (dt <= 0) return;
  let active = 0;
  for (let i = 0; i < 4; i++) {
    const st = c.steps[i];
    if (!st) continue;
    st.u += dt / st.dur;
    if (!st.fixed && st.u < 0.85) { const h = target(i); st.p1 = h.p; st.n1 = h.n; }
    const u = Math.min(1, st.u), e = smooth(u), nm = norm(add(add(st.n0, st.n1), mul(c.up, 2)));
    c.feet[i] = { p: add(lerp3(st.p0, st.p1, e), mul(nm, Math.pow(Math.sin(Math.PI * u), 0.8) * st.lift)), n: norm(lerp3(st.n0, st.n1, e)) };
    c.lift[i] = Math.sin(Math.PI * u);
    if (st.u >= 1) { c.feet[i] = { p: st.p1.slice(), n: st.n1.slice() }; c.steps[i] = null; c.lift[i] = 0; } else active++;
  }
  const lift = (i, dur, fixed) => { const h = fixed ? c.feet[i] : target(i); c.steps[i] = { p0: c.feet[i].p.slice(), n0: c.feet[i].n.slice(), p1: h.p.slice(), n1: h.n.slice(), u: 0, dur, lift: fixed ? 0.8 : 0.55 + 0.25 * Math.min(1, c.spd / 1.6), fixed }; active++; };
  const moving = cyc > 0.05;
  for (let i = 0; i < 4; i++) {
    if (c.steps[i] || (c.rear > 0.3 && !(i & 1))) continue;
    const a = c.prevPhase + GAIT_OFF[i], b = c.phase + GAIT_OFF[i];
    const due = moving && Math.floor(b) > Math.floor(a), far = len(sub(c.feet[i].p, target(i).p));
    if ((due && far > 0.15) || far > 2.3 || (cramped(i) && far > 0.3 && !active)) lift(i, clamp(sf / Math.max(cyc, 0.05), 0.26, 0.75), false);
  }
  c.prevPhase = c.phase;
  // standing: tidy the stance one foot at a time, and now and then a leg lifts and stabs back down where it was
  if (!moving && !active) {
    let worst = -1, wd = 0.35;
    for (let i = 0; i < 4; i++) { if (c.rear > 0.3 && !(i & 1)) continue; const d = len(sub(c.feet[i].p, target(i).p)); if (d > wd || (cramped(i) && d > 0.2 && worst < 0)) { wd = Math.max(d, wd); worst = i; } }
    if (worst >= 0) lift(worst, 0.5, false);
    else if (c.still > 1.2) { c.fid -= dt; if (c.fid <= 0) { lift(Math.floor(rnd() * 4) % 4, 0.75, true); c.fid = 2.5 + rnd() * 3.5; } }
  }
}
const ROOMSIM = { c: makeRoamer(), k: makeCorpse([3.2, 0, 1.8], 2.2), mind: null, pings: [], frame: null };
function resetRoom() { R_.seed = 1; ROOMSIM.c = makeRoamer(); ROOMSIM.k = makeCorpse([3.2, 0, 1.8], 2.2); ROOMSIM.mind = makeMind(); ROOMSIM.pings = []; }
// feet as the rig wants them; when it rears, the front pair are held up and forward instead
function rigFeet(c, fr) {
  const feet = c.feet.map(f => f.p), fn = c.feet.map(f => f.n), lift = c.lift.slice();
  if (c.rear > 0.02) for (const li of [0, 2]) {
    const s = li < 2 ? -1 : 1, up = at(fr.body, [s * 1.2, 1.5, 2.3]);
    feet[li] = lerp3(feet[li], up, smooth(c.rear)); lift[li] = Math.max(lift[li], c.rear); fn[li] = norm(lerp3(fn[li], c.up, c.rear));
  }
  return { feet, fn, lift };
}

// ---------------------------------------------------------------- what it wants and what it does about it
// needs drift over time; when a plan runs out it weighs its options by how it feels and picks one, with some chance in it.
// A plan is a list of steps: go somewhere, wait, look, tuck in, grab, drag, feed, drop, rear up.
function makeMind() { return { hunger: 0.62, restless: 0.35, wary: 0.12, curious: 0.4, alert: 0, feeding: false, plan: [], act: 'Looking around', why: 'It has only just come in.', last: '', log: [] }; }
const navPt = (p, f) => onFace(p, f);
const floorPt = (x, z) => navPt([x, 0, z], 0);
const randOn = f => { const F = NAV_FACES[f], p = [0, 1, 2].map(k => lerp(NAV_IN.a[k], NAV_IN.b[k], 0.1 + 0.8 * rnd())); p[F.k] = F.v; return p; };
const nearestFace = p => { let bf = 0, bd = 1e9; for (let f = 0; f < 6; f++) { const d = Math.abs(p[NAV_FACES[f].k] - NAV_FACES[f].v); if (d < bd) { bd = d; bf = f; } } return bf; };
// step makers
const S_go = (p, f, speed, look) => ({ kind: 'go', p, f, speed, look });
const S_wait = (t, look) => ({ kind: 'wait', t, look });
const S_face = look => ({ kind: 'face', look });
const S_tuck = v => ({ kind: 'set', fn: c => { c.tuckT = v; } });
const S_say = (act, why) => ({ kind: 'set', fn: () => { const m = ROOMSIM.mind; m.act = act; m.why = why; } });
const S_do = fn => ({ kind: 'set', fn });
function corpseOnFloor() { const k = ROOMSIM.k; return k.pin === 0 && k.x[1][1] < 0.6; }
function grabSteps(speedTo) {
  const c = ROOMSIM.c, k = ROOMSIM.k;
  // stand so its point is over the head, facing along the body's length
  const head = k.x[0], neck = k.x[1], along = norm([head[0] - neck[0], 0, head[2] - neck[2]]);
  const stand = floorPt(head[0] - along[0] * 0.74, head[2] - along[2] * 0.74);
  return [S_go(stand, 0, speedTo), S_face(add(head, mul(along, 2))), { kind: 'grab', t: 0 }];
}
const PLANS = {
  wander() {
    const p = randOn(0);
    return ['Wandering', ROOMSIM.mind.restless > 0.4 ? 'Restless, it picks a spot on the floor and creeps over.' : 'Nothing is pulling at it, so it drifts across the floor.', [S_go(p, 0, 1.6), S_wait(1.2 + rnd() * 2)]];
  },
  perch() {
    const f = pick([2, 3, 4, 5]), p = randOn(f), F = NAV_FACES[f]; p[1] = lerp(NAV_IN.a[1], NAV_IN.b[1], 0.65 + 0.35 * rnd());
    const p2 = randOn(f); p2[1] = p[1]; p2[F.k] = F.v;
    return ['Climbing ' + F.name, 'It wants to be up off the floor, where it can see the whole room.', [S_go(p, f, 1.9), S_wait(2 + rnd() * 3, PLAYER_ROOM), S_go(p2, f, 1.0), S_wait(2 + rnd() * 2, PLAYER_ROOM)]];
  },
  ceiling() {
    const a = randOn(1), b = randOn(1);
    return ['Crossing the ceiling', ROOMSIM.mind.restless > 0.4 ? 'Bored of the floor, it goes up and hangs upside down.' : 'It goes up to hang upside down, out of reach.', [S_go(a, 1, 1.8), S_wait(1.5 + rnd() * 2), S_go(b, 1, 1.1), S_wait(2 + rnd() * 2, PLAYER_ROOM)]];
  },
  hide() {
    // high on the back wall, just out of the corner, so it lies flat to the wall and spreads its legs onto the ceiling and the side wall
    const p = navPt([NAV_IN.a[0] + 1.0, NAV_IN.b[1] - 0.95, -9], 4);
    return ['Hiding in the dark corner', 'It feels exposed, so it presses itself into the corner the light never reaches.', [S_go(p, 1, 2.1), S_tuck(1), S_wait(7 + rnd() * 6, PLAYER_ROOM), S_do(m => { ROOMSIM.mind.wary *= 0.4; }), S_tuck(0)]];
  },
  listen() {
    const c = ROOMSIM.c, looks = [0, 1, 2].map(() => add(c.pos, [rnd() * 10 - 5, rnd() * 3 - 1, rnd() * 10 - 5]));
    return ['Listening', 'It goes still and turns slowly, listening.', [S_wait(1.6, looks[0]), S_wait(1.6, looks[1]), S_wait(1.6, looks[2])]];
  },
  feed() {
    const lair = floorPt(-5.2, -3.3);
    return ['Fetching the body to feed', ROOMSIM.k.husk ? 'Hungry again, it goes back for the husk to pick at what is left.' : 'Hungry, it goes for the body and drags it back to its corner to feed.', [
      ...grabSteps(1.7), S_say('Dragging the body away', 'It hauls the body back to the dark corner, where it feels safe to feed.'),
      S_go(lair, 0, 1.15), S_face(floorPt(0, 0)), { kind: 'feed', t: 9 + rnd() * 4 },
      S_say('Leaving the husk', 'Fed, it lets the drained body drop.'), { kind: 'drop' }, S_do(() => { const m = ROOMSIM.mind; m.hunger = 0.04; ROOMSIM.k.husk = 1; m.restless = Math.max(m.restless, 0.5); }), S_wait(1.5)]];
  },
  stalk() {
    const c = ROOMSIM.c, P = PLAYER_ROOM, dir = norm([c.pos[0] - P[0], 0, c.pos[2] - P[2]]), near = floorPt(P[0] + dir[0] * 3.4, P[2] + dir[2] * 3.4);
    const bold = ROOMSIM.mind.curious > 0.42 && ROOMSIM.mind.wary < 0.35;
    return ['Stalking you', 'Curious about you, it creeps closer, slowly, watching.', [
      S_go(near, 0, 0.75), S_wait(2 + rnd() * 2, add(P, [0, 1.5, 0])),
      ...(bold ? [S_say('Rearing up at you', 'Bold enough now, it rears up to see what you do.'), S_do(c2 => { c2.rearT = 1; }), S_wait(2.2, add(P, [0, 1.5, 0])), S_do(c2 => { c2.rearT = 0; })] : []),
      S_do(() => { const m = ROOMSIM.mind; m.curious *= 0.3; m.wary = Math.min(1, m.wary + 0.45); })]];
  },
  leave() {
    const P = PLAYER_ROOM, drop = floorPt(P[0] + 0.6, P[2] - 4.6);
    return ['Leaving the body for you', 'It drags the husk out to where you will see it, then backs off.', [
      ...grabSteps(1.7), S_go(drop, 0, 1.0), { kind: 'drop' }, S_say('Backing away', 'It leaves the body where you will find it.'),
      S_go(floorPt(drop[0] - 1.5, drop[2] - 3), 0, 0.8, add(P, [0, 1.5, 0])), S_do(() => { ROOMSIM.mind.curious *= 0.3; })]];
  },
};
function choose(m) {
  const k = ROOMSIM.k, player = state.player;
  const w = {
    wander: 0.2 + 0.45 * m.restless, perch: 0.3 + 0.45 * m.restless, ceiling: 0.26 + 0.45 * m.restless,
    hide: 0.08 + 0.95 * m.wary, listen: 0.18 + 0.25 * (1 - m.restless),
    feed: corpseOnFloor() ? 1.3 * m.hunger * (k.husk ? 0.6 : 1) : 0,
    stalk: player ? 0.95 * m.curious * (1 - m.wary) : 0,
    leave: player && corpseOnFloor() && k.husk && len(sub(k.x[1], PLAYER_ROOM)) > 5.5 ? 0.6 * m.curious : 0,
  };
  if (w[m.last] != null) w[m.last] *= 0.25;
  let tot = 0; for (const a in w) tot += w[a] * w[a];
  let r = rnd() * tot;
  for (const a in w) { r -= w[a] * w[a]; if (r <= 0) return a; }
  return 'wander';
}
function startPlan(name) {
  const m = ROOMSIM.mind, [act, why, steps] = PLANS[name](), c = ROOMSIM.c;
  // whatever it was holding, it lets go of before doing something else
  if (ROOMSIM.k.pin > 0) steps.unshift({ kind: 'drop' });
  c.tuckT = 0; c.rearT = 0; c.hT = NAV_H;
  m.plan = steps; m.act = act; m.why = why; m.last = name; m.feeding = false;
  m.log.push(R_.t.toFixed(1) + ' ' + name);
}
// a noise: it jumps, looks, and then decides what to make of it from how it feels right now
function hearNoise(p, n) {
  const c = ROOMSIM.c, m = ROOMSIM.mind, k = ROOMSIM.k, d = len(sub(p, c.pos));
  const loud = clamp(1.4 - d / 14, 0.3, 1);
  c.flinch = Math.max(c.flinch, loud * (0.6 + 0.6 * (1 - m.alert)));
  m.alert = Math.min(1.5, m.alert + 0.4 * loud);
  // the same tap lands differently depending on its mood: repeated noise wears thin or puts it on edge
  m.curious = clamp(m.curious + 0.18 * (1 - m.alert * 0.6), 0, 1); m.wary = clamp(m.wary + 0.14 * m.alert, 0, 1);
  const where = NAV_FACES[nearestFace(add(p, mul(n, NAV_H)))].name, look = p;
  if (m.feeding) {
    if (m.alert < 1.1 || rnd() < 0.6) { c.look = look; m.why = 'It heard you on ' + where + ', glanced over and carried on feeding.'; return; }
  }
  const w = {
    freeze: 0.5 + 0.3 * m.alert, investigate: (m.curious * (1 - m.wary) * 1.3 + m.hunger * 0.3) * (m.alert > 1.2 ? 0.5 : 1),
    retreat: m.wary * 1.1 + m.alert * 0.45, threat: d < 7 ? 0.45 * (1 - m.wary) + 0.2 * m.alert : 0.05, ignore: 0.12 + 0.3 * (1 - m.alert),
  };
  let tot = 0; for (const a in w) tot += w[a]; let r = rnd() * tot, pickA = 'freeze';
  for (const a in w) { r -= w[a]; if (r <= 0) { pickA = a; break; } }
  if (pickA === 'ignore') { c.look = look; m.why = 'It heard something on ' + where + ', but it is not interested.'; return; }
  if (k.pin > 0 && k.pin <= 0.5) k.pin = 0;
  m.plan = k.pin > 0.5 && pickA !== 'freeze' ? [{ kind: 'drop' }] : [];
  m.feeding = false; c.tuckT = 0; c.rearT = 0; c.hT = NAV_H;
  const navAt = navPt(add(p, mul(n, NAV_H)), nearestFace(add(p, mul(n, NAV_H)))), fAt = nearestFace(navAt);
  if (pickA === 'freeze') { m.act = 'Frozen, listening'; m.why = 'Something made a noise on ' + where + '. It locks still and listens.'; m.plan.push(S_wait(2.5 + rnd() * 2, look)); }
  else if (pickA === 'investigate') { m.act = 'Going to look'; m.why = 'A noise on ' + where + '. Curious, it creeps over to see what made it.'; m.plan.push(S_face(look), S_wait(0.6, look), S_go(navAt, fAt, 1.2, look), S_tuck(0.35), S_wait(2.5 + rnd() * 2, look), S_tuck(0)); }
  else if (pickA === 'retreat') { m.act = 'Pulling back'; m.why = 'A noise on ' + where + ' puts it on edge. It backs off into the dark.'; m.plan.push(...PLANS.hide()[2]); m.wary = Math.min(1, m.wary + 0.2); }
  else if (pickA === 'threat') { m.act = 'Rearing at the noise'; m.why = 'Close by on ' + where + '. It turns on the noise and rears up.'; m.plan.push(S_face(look), S_do(c2 => { c2.rearT = ROOMSIM.c.face === 0 ? 1 : 0.5; }), S_wait(1.8, look), S_do(c2 => { c2.rearT = 0; })); }
  m.last = pickA;
  ROOMSIM.c.route = [];
}
function runPlan(dt) {
  const c = ROOMSIM.c, m = ROOMSIM.mind, k = ROOMSIM.k;
  m.hunger = Math.min(1, m.hunger + dt * 0.007);
  const moving = len(c.vel) > 0.1;
  m.restless = clamp(m.restless + dt * (moving ? -0.035 : 0.03), 0, 1);
  m.wary = Math.max(0, m.wary - dt * 0.012); m.alert = Math.max(0, m.alert - dt * 0.12);
  m.curious = Math.min(1, m.curious + dt * (state.player ? 0.01 : 0));
  if (!m.plan.length) startPlan(choose(m));
  const st = m.plan[0];
  if (!st.on) {
    st.on = true; st.age = 0;
    if (st.kind === 'go') { c.route = navRoute(c.pos, c.face, st.p, st.f); c.speed = st.speed; c.look = st.look || null; }
    if (st.kind === 'wait' || st.kind === 'face') c.look = st.look || c.look;
    if (st.kind === 'set') st.fn(c);
    if (st.kind === 'feed') m.feeding = true;
    if (st.kind === 'drop') { k.pin = 0; m.feeding = false; c.hT = NAV_H; }
  }
  st.age += dt;
  let done = false;
  if (st.kind === 'go') done = !c.route.length;
  else if (st.kind === 'wait') done = st.age >= st.t;
  else if (st.kind === 'face') { const d = sub(st.look, c.pos), want = norm(sub(d, mul(c.up, dot(d, c.up)))); done = dot(want, c.fwd) > 0.96 || st.age > 2.5; }
  else if (st.kind === 'set' || st.kind === 'drop') done = true;
  else if (st.kind === 'grab') {
    // it dips onto the body, the scythes go in, and it rises with it
    const u = st.age / 1.7;
    c.hT = NAV_H - 0.5 * Math.sin(Math.PI * Math.min(1, u));
    k.pin = smooth((u - 0.25) / 0.5);
    done = u >= 1;
  } else if (st.kind === 'feed') { c.hT = 2.41; done = st.age >= st.t; if (done) { c.hT = NAV_H; m.feeding = false; } }
  if (done) m.plan.shift();
}
// one frame of the room
function stepRoom(dt) {
  const R = ROOMSIM;
  if (!R.mind) R.mind = makeMind();
  R_.t += dt;
  if (dt > 0) runPlan(dt);
  moveRoamer(R.c, dt);
  const fr = roamerFrame(R.c);
  stepFeet(R.c, dt, fr);
  stepCorpse(R.k, dt, R.k.pin > 0 ? fr.body : null);
  for (const p of R.pings) p.age += dt;
  R.pings = R.pings.filter(p => p.age < 1.4);
  R.frame = fr;
}
function poseRoom(dyn) {
  const R = ROOMSIM, c = R.c, m = R.mind, fr = R.frame || roamerFrame(c), k = R.k, held = k.pin > 0.5;
  const rf = rigFeet(c, fr), kf = corpseFrames(k);
  const r = harvestman({ frame: fr.body, up: c.up, t: c.t, motion: 'idle', feeding: !!(m && m.feeding), feet: rf.feet, footN: rf.fn, footLift: rf.lift,
    victim: held ? kf : null, grip: held, beardGrip: !!(m && m.feeding) }, dyn);
  drawCorpse(dyn, k, kf);
  // where you tapped: a ring spreading over the surface
  for (const p of R.pings) {
    const u = p.age / 1.4, t1 = norm(cross(p.n, Math.abs(p.n[1]) > 0.9 ? [1, 0, 0] : Y)), t2 = cross(p.n, t1);
    dyn.mat = { ...MAT.eye, c: [0.85, 0.88, 0.95], glow: 2.2 * (1 - u) * (1 - u) };
    torus(dyn, add(p.p, mul(p.n, 0.03)), [t1, t2, p.n], 0.15 + 1.6 * u, 0.018, 28, 4);
  }
  return r;
}
// a tap on the screen: the ray from the camera through that spot, into the room
function tapRoom(eye, dir) {
  let o = eye;
  const inside = [0, 1, 2].every(k => eye[k] > ROOM.a[k] && eye[k] < ROOM.b[k]);
  if (!inside) {
    let t0 = -1e9, t1 = 1e9;
    for (let k = 0; k < 3; k++) {
      if (Math.abs(dir[k]) < 1e-9) { if (eye[k] < ROOM.a[k] || eye[k] > ROOM.b[k]) return null; continue; }
      let ta = (ROOM.a[k] - eye[k]) / dir[k], tb = (ROOM.b[k] - eye[k]) / dir[k]; if (ta > tb) { const x = ta; ta = tb; tb = x; }
      t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
    }
    if (t0 > t1 || t1 < 0) return null;
    o = add(eye, mul(dir, Math.max(0, t0) + 0.02));
  }
  const h = castRoom(o, dir, 80);
  if (!h) return null;
  ROOMSIM.pings.push({ p: h.p, n: h.n, age: 0 });
  hearNoise(h.p, h.n);
  return h;
}

// ---------------------------------------------------------------- the room itself
// the room keeps its old grey, rusty metal (the creature's own metal is all black)
const ROOM_STEEL = { ...MAT.steel, c: [0.45, 0.43, 0.4] }, ROOM_DARK = { ...MAT.dark, c: [0.30, 0.29, 0.29] };
function buildRoom() {
  const A = ROOM.a, B = ROOM.b, out = { floor: new Mesh(), ceiling: new Mesh(), walls: [], props: new Mesh() };
  MAT.wall = { c: [0.25, 0.25, 0.26], metal: 0, rough: 0.72, pat: 7.3, glow: 0 };
  MAT.crate = { c: [0.31, 0.24, 0.17], metal: 0, rough: 0.82, pat: 6, glow: 0 };
  // darker where surfaces meet
  const aoAt = p => { let a = 1; for (let k = 0; k < 3; k++) for (const v of [A[k], B[k]]) { const d = Math.abs(p[k] - v); if (d > 0.01) a *= 0.62 + 0.38 * smooth(d / 1.4); } return a; };
  const sheet = (m, nu, nv, f) => grid(m, nu, nv, f, { ao: (u, v) => aoAt(f(u, v)) });
  out.floor.mat = MAT.floor; sheet(out.floor, 32, 24, (u, v) => [lerp(A[0], B[0], u), 0, lerp(A[2], B[2], v)]);
  out.ceiling.mat = MAT.wall; sheet(out.ceiling, 32, 24, (u, v) => [lerp(A[0], B[0], u), B[1], lerp(A[2], B[2], v)]);
  const wall = (n, pt, build) => { const m = new Mesh(); m.mat = MAT.wall; build(m); out.walls.push({ m, n, pt }); };
  wall([1, 0, 0], [A[0], 0, 0], m => sheet(m, 24, 12, (u, v) => [A[0], lerp(A[1], B[1], v), lerp(A[2], B[2], u)]));
  wall([-1, 0, 0], [B[0], 0, 0], m => sheet(m, 24, 12, (u, v) => [B[0], lerp(A[1], B[1], v), lerp(A[2], B[2], u)]));
  wall([0, 0, 1], [0, 0, A[2]], m => sheet(m, 32, 12, (u, v) => [lerp(A[0], B[0], u), lerp(A[1], B[1], v), A[2]]));
  wall([0, 0, -1], [0, 0, B[2]], m => {
    sheet(m, 14, 12, (u, v) => [lerp(A[0], DOOR.x0, u), lerp(A[1], B[1], v), B[2]]);
    sheet(m, 14, 12, (u, v) => [lerp(DOOR.x1, B[0], u), lerp(A[1], B[1], v), B[2]]);
    sheet(m, 3, 8, (u, v) => [lerp(DOOR.x0, DOOR.x1, u), lerp(DOOR.y1, B[1], v), B[2]]);
    // the door frame goes with its wall, so it is left out with it
    m.mat = ROOM_STEEL;
    for (const x of [DOOR.x0, DOOR.x1]) box(m, [x, DOOR.y1 / 2, B[2] - 0.06], AX, [0.14, DOOR.y1, 0.2], 0.6);
    box(m, [0, DOOR.y1 + 0.07, B[2] - 0.06], AX, [2 * DOOR.x1 + 0.28, 0.14, 0.2], 0.6);
  });
  const P = out.props;
  for (const c of CRATES) {
    const ctr = lerp3(c.a, c.b, 0.5), sz = sub(c.b, c.a);
    P.mat = MAT.crate; box(P, ctr, AX, sz, 0.8);
    // dark battens round the edges
    P.mat = tint(MAT.crate, 0.55);
    for (const sx of [-1, 1]) for (const sz2 of [-1, 1]) box(P, add(ctr, [sx * (sz[0] / 2 - 0.05), 0, sz2 * (sz[2] / 2 - 0.05)]), AX, [0.12, sz[1] + 0.02, 0.12], 0.6);
    for (const sy of [-1, 1]) for (const sz2 of [-1, 1]) box(P, add(ctr, [0, sy * (sz[1] / 2 - 0.05), sz2 * (sz[2] / 2 - 0.05)]), AX, [sz[0] + 0.02, 0.12, 0.12], 0.6);
  }
  // door frame, pipes along the back wall and a dead strip light
  P.mat = tint(ROOM_STEEL, 0.8);
  tube(P, [[A[0] + 0.2, 6.55, A[2] + 0.22], [B[0] - 0.2, 6.55, A[2] + 0.22]], 0.09, { seg: 10 });
  tube(P, [[A[0] + 0.2, 6.25, A[2] + 0.22], [B[0] - 0.2, 6.25, A[2] + 0.22]], 0.05, { seg: 8 });
  tube(P, [[B[0] - 0.24, 0, A[2] + 0.24], [B[0] - 0.24, 6.55, A[2] + 0.24]], 0.09, { seg: 10 });
  for (let x = -7; x <= 7; x += 2) box(P, [x, 6.45, A[2] + 0.1], AX, [0.08, 0.36, 0.2], 0.5);
  P.mat = ROOM_DARK; box(P, [1.5, B[1] - 0.06, 0.5], AX, [2.4, 0.1, 0.24], 0.5);
  return out;
}
