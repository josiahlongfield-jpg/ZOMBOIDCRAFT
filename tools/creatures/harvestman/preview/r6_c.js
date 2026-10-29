const FORCE_OPEN = new URLSearchParams(location.hash.slice(1)).get('open');

// two-bone leg (hip to ankle), knee bending up and out, in the vertical plane through hip and foot
function ikLeg(H, E, a, b, up = Y) {
  const d = sub(E, H), dy = dot(d, up), h = sub(d, mul(up, dy)), dx = len(h), e1 = dx > 1e-5 ? mul(h, 1 / dx) : norm(cross(up, [0.31, 0.2, 0.93]));
  const dc = clamp(Math.hypot(dx, dy), Math.abs(a - b) + 0.02, a + b - 0.02);
  const beta = Math.acos(clamp((a * a + dc * dc - b * b) / (2 * a * dc), -1, 1)), th = Math.atan2(dy, dx) + beta;
  const K = add(H, add(mul(e1, a * Math.cos(th)), mul(up, a * Math.sin(th))));
  return { K, E: add(K, mul(norm(sub(E, K)), b)), n: norm(cross(e1, up)) };
}
// two-bone leg bent toward a pole: the room uses it with the pole up and out from the body, so knees never fold inward
function ikPole(H, E, a, b, pole) {
  const d = sub(E, H), dl = clamp(len(d), Math.abs(a - b) + 0.02, a + b - 0.02), dn = norm(d);
  const cb = clamp((a * a + dl * dl - b * b) / (2 * a * dl), -1, 1), sb = Math.sqrt(1 - cb * cb);
  let bp = sub(pole, mul(dn, dot(pole, dn))); bp = len(bp) > 1e-4 ? norm(bp) : norm(cross(dn, [0.3, 0.9, 0.2]));
  const K = add(H, add(mul(dn, a * cb), mul(bp, a * sb)));
  return { K, E: add(K, mul(norm(sub(add(H, mul(dn, dl)), K)), b)), n: norm(cross(dn, bp)) };
}
function restFoot(ground, s, i, feed) {
  // feeding, it stands taller on legs drawn in under it
  const L = LEGS[i], r = L.restR * (feed ? 0.9 : 1);
  return at(ground, [s * Math.sin(L.restA) * r, 0, Math.cos(L.restA) * r]);
}
const sag = (P, Q, d, n = 12) => quad(P, add(lerp3(P, Q, 0.5), [0, -2 * d, 0]), Q, n);
// the creature's look from its state: o = { x, z, yaw, feeding, t, motion, feet[4] | null, swing }; rigid parts come back
// as [mesh, frame], the moving bits (cables, arms, the body it feeds on) are built into dyn in world space. The test room
// passes its own body frame instead (o.frame, o.up: walls and ceilings too), each foot's surface (o.footN, o.footLift),
// the body it holds (o.victim: head and torso frames) and whether the scythes and the beard grip it (o.grip, o.beardGrip)
function harvestman(o, dyn) {
  const T = o.t, feed = o.feeding, live = o.motion !== 'still', walking = o.motion === 'walk';
  const bob = live ? (walking ? 0.05 * Math.sin(T * 3.4) : 0.03 * Math.sin(T * 1.2)) : 0;
  const sway = live ? 0.02 * Math.sin(T * 0.8) : 0, beat = live ? Math.sin(T * (feed ? 7.5 : 1.4)) : 0;
  const up = o.up || Y, grip = o.grip != null ? o.grip : feed, beardGrip = o.beardGrip != null ? o.beardGrip : feed;
  const body = o.frame ? frame(add(o.frame.o, mul(up, bob)), m3mul(o.frame.R, rot(sway * 0.5, walking ? 0.025 * Math.sin(T * 1.7) : 0, sway)))
    : frame([o.x, (feed ? BODY_Y.feed : BODY_Y.hunt) + bob, o.z], rot(o.yaw + sway * 0.5, (feed ? 0.0 : 0.1) + (walking ? 0.025 * Math.sin(T * 1.7) : 0), sway));
  const ground = o.frame ? o.frame : frame([o.x, 0, o.z], rot(o.yaw)), parts = [['body', body]];
  const collar = child(body, COLLAR.o, COLLAR.R);
  // the skull twitches now and then while it hunts; feeding, it bows over what it holds and works its jaw
  let hy = 0, hp = 0, hr = 0;
  if (live && !feed) { const k = Math.floor(T / 2.1), u = T - k * 2.1, s = u < 0.06 ? u / 0.06 : u < 0.8 ? 1 : Math.max(0, 1 - (u - 0.8) / 0.14); hy = (hash(k) - 0.5) * 0.4 * s; hp = (hash(k + 9) - 0.5) * 0.2 * s; hr = (hash(k + 17) - 0.5) * 0.5 * s; }
  if (feed) hp = FEED_PITCH + 0.02 * beat;
  // the face plates: shut while it hunts, peeled back now and then in a scream, and open the whole time it feeds
  let open = feed ? 1 : 0;
  open = 0;
  const scream = 0;
  if (scream > 0) { hp -= 0.25 * scream; hy *= 1 - scream; hr = (hr * (1 - scream)) + 0.04 * scream * Math.sin(T * 40); }
  const skull = child(collar, SKULL_AT, rot(hy, hp, hr));
  const jaw = child(skull, JAW_AT, rx(feed ? FEED_JAW + 0.03 * beat : 0.32 + 0.85 * scream + (live ? 0.08 * Math.sin(T * 2.3) * (1 - scream) : 0)));
  // round 6c: no visible head; the armoured front is the face, and the skull frame only places what it feeds on
  // feeding: a body hangs from its mouth by the face, toes dragging, leaning in
  let head = null, torso = null;
  const fwd = ap(ground.R, [0, 0, 1]), side = ap(body.R, [1, 0, 0]);
  if (o.victim) { head = o.victim.head; torso = o.victim.torso; }
  else if (feed) {
    // the victim hangs with its back to it, dragged up by the scythes through its chest until its head is right under
    // the point; the beard only reaches down over the top half of the head, and the body hangs below, toes dragging
    const hz = norm([0, 0.25, 0.97]), hy = norm([0, 0.97, -0.25]);
    head = child(body, [0, -0.64, 0.74], cols(cross(hy, hz), hy, hz));
    const N = at(head, [0, -0.2, -0.04]), sw = o.swing || [0, 0];
    const tilt = Math.acos(clamp((N[1] - 0.04) / CORPSE_LEN, 0, 1)) + sw[0];
    const u = norm(add(sub(mul(Y, Math.cos(tilt)), mul(fwd, Math.sin(tilt))), mul(ap(ground.R, [1, 0, 0]), sw[1] * 0.5)));
    const f = norm(sub(fwd, mul(u, dot(fwd, u))));
    torso = frame(add(N, mul(u, 0.27)), cols(cross(u, f), u, f));
  }
  // the beard: shards hanging under the point; feeding, they reach down and close round the head like teeth
  for (let k = 0; k < 16; k++) {
    const id = 1900 + k, a = Math.PI + (hash(id) - 0.5) * 2.6, sb = 0.86 + 0.13 * hash(id + 1), L = 0.12 + 0.18 * hash(id + 3), r = 0.018 + 0.01 * hash(id + 4);
    const [p0, n0] = b7Off(a, sb, 0), P = at(body, p0), n = ap(body.R, n0);
    let C, Q;
    if (beardGrip && head) {
      const th = (k / 16) * TAU + 0.2, grip = 0.01 * beat;
      Q = at(head, [Math.sin(th) * (0.155 - grip), 0.01 + 0.04 * hash(id + 5), Math.cos(th) * (0.17 - grip)]);
      C = add(lerp3(P, Q, 0.5), mul(sub(lerp3(P, Q, 0.5), at(head, [0, 0, 0])), 0.6));
    } else {
      const dir = norm(add(ap(body.R, [0, -1, 0.35]), mul(n, 0.4))), tw = live ? 0.02 * Math.sin(T * 3 + k) : 0;
      Q = add(P, mul(dir, L)); C = add(lerp3(P, Q, 0.5), mul(n, 0.02 + tw));
    }
    dyn.mat = tint(MAT.scale, 0.7 + 0.35 * hash(id + 2), 2.4);
    tube(dyn, quad(P, C, Q, 8), t => r * Math.pow(1 - t, 1.1) + 0.002, { seg: 5, shape: [1, 0.5] });
  }
  // two small scythe arms under the front: folded like a mantis while it hunts, feeding they hook into the victim's chest
  for (const s of [-1, 1]) {
    const S = at(body, [s * 0.2, -0.28, 0.42]);
    let E, W, tip;
    if (grip) {
      // the same fold, closed up to drag the victim in: the elbow still juts forward, the forearm folds back under it and
      // the blade drives forward through its back and out of its chest, points down
      W = at(body, [s * 0.2, -0.96, 0.47]); tip = at(body, [s * 0.13, -1.25, 0.98]);
      const sw2 = sub(W, S), d2 = len(sw2), dir = mul(sw2, 1 / d2), al = (0.54 * 0.54 - 0.385 * 0.385 + d2 * d2) / (2 * d2);
      const fp = norm(sub(fwd, mul(dir, dot(fwd, dir))));
      E = add(add(add(S, mul(dir, al)), mul(fp, Math.sqrt(Math.max(0, 0.54 * 0.54 - al * al)) * 0.97)), mul(side, s * 0.13));
    } else {
      const tw = live ? 0.05 * Math.sin(T * 2.6 + s) : 0;
      E = at(body, [s * 0.3, -0.72, 0.72 + tw]); W = at(body, [s * 0.28, -0.42, 0.96 + tw]); tip = at(body, [s * 0.26, -0.98, 1.12 + tw]);
    }
    dyn.mat = MAT.steel; tube(dyn, [S, lerp3(S, E, 0.5), E], 0.034, { seg: 9 });
    dyn.mat = MAT.dark; gear(dyn, E, mul(side, s), sub(E, S), 0.06, 0.04, 10);
    dyn.mat = MAT.steel; tube(dyn, [E, lerp3(E, W, 0.5), W], 0.028, { seg: 9 });
    const ch = sub(tip, W), bul = feed ? norm(sub(Y, mul(norm(ch), dot(Y, norm(ch))))) : norm(sub(W, E));
    const pn = norm(cross(feed ? bul : sub(W, E), ch)), C = add(lerp3(W, tip, 0.5), mul(bul, feed ? 0.08 : 0.15));
    dyn.mat = tint(MAT.steel, 1.05, 2.2);
    tube(dyn, quad(W, C, tip, 12), t => 0.055 * Math.pow(1 - t, 0.8) + 0.002, { seg: 8, up: pn, shape: [0.22, 1] });
    if (grip && torso) {
      // the wounds where the blade goes in through the back and comes out of the chest
      const ax = [ap(torso.R, [1, 0, 0]), ap(torso.R, [0, 1, 0]), ap(torso.R, [0, 0, 1])], bl = quad(W, C, tip, 40);
      dyn.mat = MAT.maw;
      for (const zs of [-1, 1]) {
        let best = null, bd = 1e9;
        for (const q of bl) { const d = sub(q, torso.o), lz = dot(d, ax[2]); if (Math.abs(lz - zs * 0.115) < bd) { bd = Math.abs(lz - zs * 0.115); best = d; } }
        ellipsoid(dyn, at(torso, [dot(best, ax[0]), dot(best, ax[1]), zs * 0.113]), ax, zs < 0 ? [0.04, 0.055, 0.012] : [0.045, 0.06, 0.012], 10, 6);
      }
    }
  }
  // four long legs
  for (let s = -1; s <= 1; s += 2) for (let i = 0; i < 2; i++) {
    const L = LEGS[i], li = (s < 0 ? 0 : 2) + i;
    const H = at(body, [s * L.hip[0], L.hip[1], L.hip[2]]);
    let F = o.feet ? o.feet[li] : restFoot(ground, s, i, feed);
    // a foot planted closer to the hip than the folded leg can reach: the point lands further out on the same surface
    { const dmin = L.lc - L.a + 0.2; if (len(sub(F, H)) < dmin) {
      const sn = o.footN ? o.footN[li] : up, hh = dot(sub(H, F), sn), Hp = sub(H, mul(sn, hh)), r = Math.sqrt(Math.max(0, dmin * dmin - hh * hh));
      let dr = sub(F, Hp); if (len(dr) < 1e-3) dr = sub(sub(H, body.o), mul(sn, dot(sub(H, body.o), sn)));
      F = add(Hp, mul(norm(dr), r));
    } }
    // the ankle sits a little in from the point, so the last segment falls steeper than the shin; it tucks in as the
    // leg lifts and flexes a little all the time, which keeps the legs from moving like stilts
    // two joints: hip to knee, then one long lower section curving smoothly down to the point
    const lowL = L.lc;
    // the knee rises toward the side its foot is on: the thigh is short against the long lower leg, so it always leans back
    // from the foot, and a knee pushed out over the foot keeps it leaning out from the hull instead of in across it. When
    // the foot is out level with the hip (reaching to the next wall or the ceiling), that side lines up with the leg itself,
    // so the knee also leans away from the surface the body is on, which never lines up with it at the same time
    const ho = sub(H, body.o), fo = sub(F, body.o), fh = sub(fo, mul(up, dot(fo, up)));
    const out = len(fh) > 0.05 ? norm(fh) : norm(sub(ho, mul(up, dot(ho, up)))), dn = norm(sub(F, H)), p0 = norm(add(mul(up, 0.4), out));
    const perp = v => sub(v, mul(dn, dot(v, dn)));
    const ik = ikPole(H, F, L.a, lowL, add(perp(p0), mul(perp(up), 0.6)));
    const K = ik.K, n = ik.n, P = ik.E;
    const tx = norm(sub(K, H)), sx = norm(sub(P, K));
    const thigh = frame(H, cols(tx, cross(n, tx), n)), shin = frame(K, cols(sx, cross(n, sx), n));
    parts.push([i ? 'thighR' : 'thighF', thigh], [i ? 'lowR' : 'lowF', shin]);
    // black cables and red sinew sagging from the belly to the leg (left out of the game export: the game draws its own)
    if (o.noCables) continue;
    const B1 = at(body, [s * (L.hip[0] - 0.12), L.hip[1] - 0.22, L.hip[2] + (i ? 0.14 : -0.14)]), B2 = at(body, [s * (L.hip[0] - 0.2), L.hip[1] - 0.32, L.hip[2] + (i ? 0.05 : -0.05)]);
    dyn.mat = MAT.cable;
    tube(dyn, sag(B1, at(thigh, [L.a * 0.55, -0.06, 0.05]), 0.18, 14), 0.02, { seg: 8 });
    tube(dyn, sag(B2, at(shin, [0.35, -0.05, -0.05]), 0.3, 16), 0.017, { seg: 8 });
    dyn.mat = MAT.sinew;
    tube(dyn, sag(add(B2, [0, 0.04, 0]), at(thigh, [L.a * 0.8, -0.08, -0.04]), 0.16, 12), 0.012, { seg: 6 });
  }
  if (feed && !o.victim) buildCorpse(dyn, head, torso, T, live);
  return { parts, open, eyes: [], eyeLight: [0, -50, 0], mouth: feed ? null : at(body, [0, -0.3, 0.8]), belly: body.o[1] - 0.4 };
}
// the body it feeds on: the face held in its mouth, the head tipped back, hanging from the neck with toes on the floor
function buildCorpse(m, hf, f, T, live) {
  const Hd = p => at(hf, p), hax = [ap(hf.R, [1, 0, 0]), ap(hf.R, [0, 1, 0]), ap(hf.R, [0, 0, 1])];
  m.mat = MAT.skin; ellipsoid(m, Hd([0, 0, 0]), hax, [0.15, 0.185, 0.165], 18, 12);
  ellipsoid(m, Hd([0, -0.045, 0.16]), hax, [0.028, 0.038, 0.03], 8, 6);
  m.mat = MAT.mouth; ellipsoid(m, Hd([0, -0.11, 0.135]), hax, [0.048, 0.038, 0.03], 10, 6);
  const W = p => at(f, p), ax = [ap(f.R, [1, 0, 0]), ap(f.R, [0, 1, 0]), ap(f.R, [0, 0, 1])];
  m.mat = MAT.skin; tube(m, [Hd([0, -0.1, -0.02]), lerp3(Hd([0, -0.2, -0.03]), W([0, -0.2, 0]), 0.5), W([0, -0.32, 0])], 0.06, { seg: 10 });
  m.mat = MAT.shirt; tube(m, [W([0, -0.27, 0]), W([0, -0.5, 0.01]), W([0, -0.8, 0.01]), W([0, -1.0, 0])], t => 0.2 - 0.03 * t + 0.03 * Math.sin(Math.PI * t), { seg: 16, up: ax[0], shape: [1, 0.58], cap0: true });
  m.mat = MAT.jeans; ellipsoid(m, W([0, -1.0, 0]), ax, [0.18, 0.11, 0.11], 14, 8);
  for (const s of [-1, 1]) {
    // arms hang toward the floor, whatever angle the body is at
    const S = W([s * 0.24, -0.36, 0]), sw = live ? 0.04 * Math.sin(T * 1.3 + s) : 0, E = add(S, [sw, -0.32, s * 0.02]), Hn = add(E, [sw * 1.5, -0.3, 0.03]);
    m.mat = MAT.shirt; ellipsoid(m, S, ax, [0.075, 0.075, 0.075], 10, 6); tube(m, [S, E], t => 0.062 - 0.008 * t, { seg: 10 });
    m.mat = MAT.skin; tube(m, [E, Hn], t => 0.05 - 0.01 * t, { seg: 10 }); ellipsoid(m, add(Hn, [0, -0.05, 0]), ax, [0.04, 0.065, 0.03], 8, 6);
    const lt = s < 0 ? -0.1 : 0.02, P = W([s * 0.1, -1.05, 0.02]), K = W([s * 0.11, -1.42, 0.07 + lt * 0.4]), A = W([s * 0.1, -1.76, 0.02 + lt]);
    m.mat = MAT.jeans; tube(m, [P, lerp3(P, K, 0.5), K], 0.078, { seg: 10 }); tube(m, [K, lerp3(K, A, 0.5), A], t => 0.07 - 0.012 * t, { seg: 10 });
    m.mat = MAT.shoe; ellipsoid(m, add(A, ap(f.R, [0, -0.04, 0.06])), ax, [0.055, 0.05, 0.12], 10, 6);
  }
  // what it drains drips off the jaw and the chin
  if (live) for (let k = 0; k < 3; k++) {
    const per = 1.2 + k * 0.33, u = ((T + k * 0.53) % per) / per, src = Hd([[-0.12, -0.15, 0.08], [0.11, -0.15, 0.08], [0, -0.19, 0.05]][k]);
    const y = src[1] - 9.8 * Math.pow(u * per * 0.55, 2) * 0.5;
    if (y > 0.02) { m.mat = MAT.maw; ellipsoid(m, [src[0], y, src[2]], AX, [0.014, 0.03, 0.014], 6, 4); }
  }
}

// ---------------------------------------------------------------- walking: one leg at a time, each point planted until its rest spot runs ahead
function makeWalker(home, yaw, feeding) {
  return { home, yaw0: yaw, feeding, x: home[0], z: home[1], yaw, ang: 0, feet: null, steps: [], swing: [0, 0], swingV: [0, 0], t: 0 };
}
function resetWalker(w) { w.x = w.home[0]; w.z = w.home[1]; w.yaw = w.yaw0; w.ang = 0; w.feet = null; w.steps = []; w.swing = [0, 0]; w.swingV = [0, 0]; }
function stepWalker(w, dt, motion) {
  w.t += dt;
  const ground = () => frame([w.x, 0, w.z], rot(w.yaw));
  const rests = () => { const g = ground(), r = []; for (let s = -1; s <= 1; s += 2) for (let i = 0; i < 2; i++) r.push(restFoot(g, s, i, w.feeding)); return r; };
  if (!w.feet) { w.feet = rests().map(p => p.slice()); w.steps = w.feet.map(() => null); }
  if (dt <= 0) return;
  let vx = 0, vz = 0;
  if (motion === 'walk') {
    const R = 1.6, om = 0.24, px = w.x, pz = w.z;
    w.ang += om * dt;
    w.x = w.home[0] + R * Math.sin(w.ang); w.z = w.home[1] + R * Math.cos(w.ang) - R;
    vx = (w.x - px) / dt; vz = (w.z - pz) / dt;
    w.yaw = Math.atan2(vx, vz);
  } else if (Math.abs(w.x - w.home[0]) > 1e-3 || Math.abs(w.z - w.home[1]) > 1e-3) resetWalker(w), w.feet = rests().map(p => p.slice()), w.steps = w.feet.map(() => null);
  const R = rests();
  let busy = false;
  for (let i = 0; i < 4; i++) {
    const st = w.steps[i];
    if (!st) continue;
    st.u += dt / st.dur;
    const u = Math.min(1, st.u), e = smooth(u);
    w.feet[i] = [lerp(st.from[0], st.to[0], e), Math.pow(Math.sin(Math.PI * u), 0.7) * st.lift, lerp(st.from[2], st.to[2], e)];
    if (st.u >= 1) { w.feet[i] = st.to.slice(); w.steps[i] = null; } else busy = true;
  }
  if (motion === 'walk' && !busy) {
    let pick = -1, most = 0.5, to = null;
    for (let i = 0; i < 4; i++) {
      const target = add(R[i], [vx * 1.2, 0, vz * 1.2]), far = Math.hypot(w.feet[i][0] - target[0], w.feet[i][2] - target[2]);
      if (far > most) { most = far; pick = i; to = target; }
    }
    if (pick >= 0) w.steps[pick] = { from: w.feet[pick].slice(), to: [to[0], 0, to[2]], u: 0, dur: 0.6, lift: 0.9 };
  }
  // idle: now and then one leg lifts high and stabs back down where it was
  if (motion === 'idle') {
    const k = Math.floor(w.t / 3.1), u = w.t - k * 3.1, li = Math.floor(hash(k * 3.1 + (w.feeding ? 5 : 0)) * 4);
    for (let i = 0; i < 4; i++) { const r = R[i]; w.feet[i] = [r[0], 0, r[2]]; }
    if (u < 0.7) { const r = R[li], a = Math.sin(Math.PI * u / 0.7); w.feet[li] = [r[0], 0.8 * Math.pow(a, 0.6), r[2]]; }
  }
  // the held body swings like a pendulum behind the movement
  const acc = motion === 'walk' ? [0.16 - 0.2 * Math.sin(w.t * 3.4), 0.16 * Math.cos(w.t * 1.7)] : [0, 0];
  for (let k = 0; k < 2; k++) { w.swingV[k] += (-6 * w.swing[k] - 1.2 * w.swingV[k] + acc[k] * 6) * dt; w.swing[k] += w.swingV[k] * dt; }
  if (motion === 'idle') { w.swing[0] = 0.04 * Math.sin(w.t * 1.3); w.swing[1] = 0.04 * Math.sin(w.t * 1.1 + 1); }
}
function pose(w, motion, dyn) {
  return harvestman({ x: w.x, z: w.z, yaw: w.yaw, feeding: w.feeding, t: w.t, motion, feet: motion === 'still' ? null : w.feet, swing: motion === 'still' ? [0, 0] : w.swing }, dyn);
}

// ---------------------------------------------------------------- measurements for the cards
function measure(feeding) {
  const dyn = new Mesh(), r = harvestman({ x: 0, z: 0, yaw: 0, feeding, t: 0, motion: 'still', feet: null }, dyn);
  let top = 0, x0 = 1e9, x1 = -1e9, z0 = 1e9, z1 = -1e9;
  for (const [name, f] of r.parts) for (const ch of MESHES[name].chunks) for (let q = 0; q < ch.v.length; q += VS * 3) {
    const p = at(f, [ch.v[q], ch.v[q + 1], ch.v[q + 2]]);
    top = Math.max(top, p[1]); x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]); z0 = Math.min(z0, p[2]); z1 = Math.max(z1, p[2]);
  }
  return { top, span: Math.max(x1 - x0, z1 - z0), body: r.belly };
}
const fmt = v => v.toFixed(1) + ' blocks';
for (const [k, f] of [['h', false], ['f', true]]) { const m = measure(f); document.getElementById(k + '-tall').textContent = fmt(m.top); document.getElementById(k + '-span').textContent = fmt(m.span); document.getElementById(k + '-body').textContent = fmt(m.body); }
