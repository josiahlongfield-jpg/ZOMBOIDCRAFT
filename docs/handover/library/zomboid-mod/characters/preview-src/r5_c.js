
// two-bone leg, knee bending up and out, in the vertical plane through hip and foot
function ikLeg(H, E, a, b) {
  const h = [E[0] - H[0], 0, E[2] - H[2]], dx = len(h), e1 = dx > 1e-5 ? mul(h, 1 / dx) : [1, 0, 0], dy = E[1] - H[1];
  const dc = clamp(Math.hypot(dx, dy), Math.abs(a - b) + 0.02, a + b - 0.02);
  const beta = Math.acos(clamp((a * a + dc * dc - b * b) / (2 * a * dc), -1, 1)), th = Math.atan2(dy, dx) + beta;
  const K = add(H, add(mul(e1, a * Math.cos(th)), mul(Y, a * Math.sin(th))));
  return { K, E: add(K, mul(norm(sub(E, K)), b)), n: norm(cross(e1, Y)) };
}
function restFoot(ground, s, i, feed) {
  // feeding, it stands taller on legs drawn in under it
  const L = LEGS[i], r = L.restR * (feed ? 0.9 : 1);
  return at(ground, [s * Math.sin(L.restA) * r, 0, Math.cos(L.restA) * r]);
}
const sag = (P, Q, d, n = 12) => quad(P, add(lerp3(P, Q, 0.5), [0, -2 * d, 0]), Q, n);
// the creature's look from its state: o = { x, z, yaw, feeding, t, motion, feet[4] | null, swing }; rigid parts come back
// as [mesh, frame], the moving bits (cables, arms, the body it feeds on) are built into dyn in world space
function harvestman(o, dyn) {
  const T = o.t, feed = o.feeding, live = o.motion !== 'still', walking = o.motion === 'walk';
  const bob = live ? (walking ? 0.05 * Math.sin(T * 3.4) : 0.03 * Math.sin(T * 1.2)) : 0;
  const sway = live ? 0.02 * Math.sin(T * 0.8) : 0, beat = live ? Math.sin(T * (feed ? 7.5 : 1.4)) : 0;
  const body = frame([o.x, (feed ? BODY_Y.feed : BODY_Y.hunt) + bob, o.z], rot(o.yaw + sway * 0.5, (feed ? 0.0 : 0.1) + (walking ? 0.025 * Math.sin(T * 1.7) : 0), sway));
  const ground = frame([o.x, 0, o.z], rot(o.yaw)), parts = [['body', body]];
  const collar = child(body, COLLAR.o, COLLAR.R);
  // the skull twitches now and then while it hunts; feeding, it bows over what it holds and works its jaw
  let hy = 0, hp = 0, hr = 0;
  if (live && !feed) { const k = Math.floor(T / 2.1), u = T - k * 2.1, s = u < 0.06 ? u / 0.06 : u < 0.8 ? 1 : Math.max(0, 1 - (u - 0.8) / 0.14); hy = (hash(k) - 0.5) * 0.4 * s; hp = (hash(k + 9) - 0.5) * 0.2 * s; hr = (hash(k + 17) - 0.5) * 0.5 * s; }
  if (feed) hp = FEED_PITCH + 0.02 * beat;
  const skull = child(collar, SKULL_AT, rot(hy, hp, hr));
  const jaw = child(skull, JAW_AT, rx(feed ? FEED_JAW + 0.03 * beat : 0.32 + (live ? 0.08 * Math.sin(T * 2.3) : 0)));
  parts.push(['skull', skull], ['jaw', jaw]);
  // feeding: a body hangs from its mouth by the face, toes dragging, leaning in
  let head = null, torso = null;
  const fwd = ap(ground.R, [0, 0, 1]), side = ap(body.R, [1, 0, 0]);
  if (feed) {
    head = child(skull, mul(FEED.at, SK), FEED.R);
    const N = at(head, [0, -0.2, -0.04]), sw = o.swing || [0, 0];
    const tilt = Math.acos(clamp((N[1] - 0.04) / CORPSE_LEN, 0, 1)) + sw[0];
    const u = norm(add(sub(mul(Y, Math.cos(tilt)), mul(fwd, Math.sin(tilt))), mul(ap(ground.R, [1, 0, 0]), sw[1] * 0.5)));
    const f = norm(sub(mul(fwd, -1), mul(u, dot(mul(fwd, -1), u))));
    torso = frame(add(N, mul(u, 0.27)), cols(cross(u, f), u, f));
    // sinew from the jaw latched into the face
    dyn.mat = MAT.sinew;
    for (const s of [-1, 1]) tube(dyn, quad(at(jaw, mul([s * 0.09, -0.06, 0.1], SK)), at(jaw, mul([s * 0.13, -0.12, 0.18], SK)), at(head, [s * 0.12, -0.02, 0.06]), 8), 0.012, { seg: 6 });
  }
  // two small scythe arms under the front: folded like a mantis while it hunts, feeding they reach round the body and
  // hook behind its neck, pushing the head up into the mouth
  for (const s of [-1, 1]) {
    const S = at(body, [s * 0.2, -0.28, 0.42]);
    let E, W, tip;
    if (feed) {
      W = at(torso, [s * 0.2, -0.5, -0.16]); tip = at(head, [s * 0.05, 0.02, -0.16]);
      E = add(lerp3(S, W, 0.5), add(mul(side, s * 0.36), [0, -0.1, 0]));
    } else {
      const tw = live ? 0.05 * Math.sin(T * 2.6 + s) : 0;
      E = at(body, [s * 0.3, -0.72, 0.72 + tw]); W = at(body, [s * 0.28, -0.42, 0.96 + tw]); tip = at(body, [s * 0.26, -0.98, 1.12 + tw]);
    }
    dyn.mat = MAT.steel; tube(dyn, [S, lerp3(S, E, 0.5), E], 0.034, { seg: 9 });
    dyn.mat = MAT.dark; gear(dyn, E, mul(side, s), sub(E, S), 0.06, 0.04, 10);
    dyn.mat = MAT.steel; tube(dyn, [E, lerp3(E, W, 0.5), W], 0.028, { seg: 9 });
    const pn = norm(cross(sub(W, E), sub(tip, W))), C = add(lerp3(W, tip, 0.5), mul(norm(sub(W, E)), 0.15));
    dyn.mat = tint(MAT.steel, 1.05, 2.2);
    tube(dyn, quad(W, C, tip, 12), t => 0.055 * Math.pow(1 - t, 0.8) + 0.002, { seg: 8, up: pn, shape: [0.22, 1] });
  }
  // four long legs
  for (let s = -1; s <= 1; s += 2) for (let i = 0; i < 2; i++) {
    const L = LEGS[i], li = (s < 0 ? 0 : 2) + i;
    const H = at(body, [s * L.hip[0], L.hip[1], L.hip[2]]);
    const F = o.feet ? o.feet[li] : restFoot(ground, s, i, feed);
    const ik = ikLeg(H, F, L.a, L.b), K = ik.K, E = ik.E, n = ik.n;
    const tx = norm(sub(K, H)), sx = norm(sub(E, K));
    const thigh = frame(H, cols(tx, cross(n, tx), n)), shin = frame(K, cols(sx, cross(n, sx), n));
    parts.push([i ? 'thighR' : 'thighF', thigh], [i ? 'shinR' : 'shinF', shin]);
    // black cables and red sinew sagging from the belly to the leg
    const B1 = at(body, [s * (L.hip[0] - 0.12), L.hip[1] - 0.22, L.hip[2] + (i ? 0.14 : -0.14)]), B2 = at(body, [s * (L.hip[0] - 0.2), L.hip[1] - 0.32, L.hip[2] + (i ? 0.05 : -0.05)]);
    dyn.mat = MAT.cable;
    tube(dyn, sag(B1, at(thigh, [L.a * 0.55, -0.06, 0.05]), 0.18, 14), 0.02, { seg: 8 });
    tube(dyn, sag(B2, at(shin, [0.35, -0.05, -0.05]), 0.3, 16), 0.017, { seg: 8 });
    dyn.mat = MAT.sinew;
    tube(dyn, sag(add(B2, [0, 0.04, 0]), at(thigh, [L.a * 0.8, -0.08, -0.04]), 0.16, 12), 0.012, { seg: 6 });
  }
  if (feed) buildCorpse(dyn, head, torso, T, live);
  return { parts, eyes: EYES.map(([c, r]) => [at(skull, mul(c, SK)), r * SK]), eyeLight: at(skull, mul([0.24, 0.02, 0.2], SK)), mouth: feed ? null : at(skull, mul([0, -0.24, 0.22], SK)), belly: body.o[1] - 0.4 };
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
