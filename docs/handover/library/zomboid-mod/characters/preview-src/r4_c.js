
// two-bone leg, knee bending up and out, in the vertical plane through hip and ankle
function ikLeg(H, E, a, b) {
  const h = [E[0] - H[0], 0, E[2] - H[2]], dx = len(h), e1 = dx > 1e-5 ? mul(h, 1 / dx) : [1, 0, 0], dy = E[1] - H[1];
  const dc = clamp(Math.hypot(dx, dy), Math.abs(a - b) + 0.02, a + b - 0.02);
  const beta = Math.acos(clamp((a * a + dc * dc - b * b) / (2 * a * dc), -1, 1)), th = Math.atan2(dy, dx) + beta;
  const K = add(H, add(mul(e1, a * Math.cos(th)), mul(Y, a * Math.sin(th))));
  return { K, E: add(K, mul(norm(sub(E, K)), b)), n: norm(cross(e1, Y)) };
}
function restFoot(ground, s, i, feed) {
  // feeding, it stands up tall on legs drawn in under it
  const L = LEGS[i], r = L.restR * (feed ? 0.88 : 1);
  return at(ground, [s * Math.sin(L.restA) * r, 0, Math.cos(L.restA) * r]);
}
const sag = (P, Q, d, n = 12) => quad(P, add(lerp3(P, Q, 0.5), [0, -2 * d, 0]), Q, n);
// the creature's look from its state: o = { x, z, yaw, feeding, t, motion, feet[4] | null, swing }; rigid parts come back
// as [mesh, frame], the moving bits (cables, neck, arms, the corpse) are built into dyn in world space
function harvestman(o, dyn) {
  const T = o.t, feed = o.feeding, live = o.motion !== 'still', walking = o.motion === 'walk';
  const bob = live ? (walking ? 0.05 * Math.sin(T * 3.4) : 0.03 * Math.sin(T * 1.2)) : 0;
  const sway = live ? 0.02 * Math.sin(T * 0.8) : 0, beat = live ? Math.sin(T * (feed ? 7.5 : 1.4)) : 0;
  const body = frame([o.x, (feed ? BODY_Y.feed : BODY_Y.hunt) + bob, o.z], rot(o.yaw + sway * 0.5, (feed ? -0.04 : 0.05) + (walking ? 0.025 * Math.sin(T * 1.7) : 0), sway));
  const ground = frame([o.x, 0, o.z], rot(o.yaw)), parts = [['body', body]];
  const collar = child(body, COLLAR.o, COLLAR.R);
  // the skull twitches now and then while it hunts
  let hy = 0, hp = 0, hr = 0;
  if (live && !feed) { const k = Math.floor(T / 2.1), u = T - k * 2.1, s = u < 0.06 ? u / 0.06 : u < 0.8 ? 1 : Math.max(0, 1 - (u - 0.8) / 0.14); hy = (hash(k) - 0.5) * 0.4 * s; hp = (hash(k + 9) - 0.5) * 0.2 * s; hr = (hash(k + 17) - 0.5) * 0.5 * s; }
  const skull = child(collar, SKULL_AT, rot(hy, hp, hr));
  const jaw = child(skull, JAW_AT, rx(feed ? 0.16 : 0.42 + (live ? 0.08 * Math.sin(T * 2.3) : 0)));
  parts.push(['skull', skull], ['jaw', jaw]);
  // the hood at the end of its neck under the jaw: open, or closed over the top half of a face
  const R0 = at(collar, [0, -0.4, 0.5]), sw = o.swing || [0, 0];
  let Rh, N, corpse = null;
  if (feed) {
    N = add(R0, [0, -0.1, 0]);
    const cy = N[1] - HOOD_R - 0.05, tilt = Math.acos(clamp((cy - 0.05) / CORPSE_LEN, 0, 1));
    Rh = rot(o.yaw, tilt + Math.max(0, sw[0]), sw[1] * 0.5);
  } else {
    N = add(R0, [live ? 0.05 * Math.sin(T * 0.9) : 0, -0.22, live ? 0.04 * Math.sin(T * 1.25) : 0]);
    Rh = rot(o.yaw + (live ? 0.25 * Math.sin(T * 0.7) : 0), 0.15 + (live ? 0.1 * Math.sin(T * 1.1) : 0), 0);
  }
  const hf = frame(sub(N, ap(Rh, [0, HOOD_R + 0.05, 0])), Rh);
  if (feed) corpse = hf;
  parts.push(['cup', hf]);
  for (let k = 0; k < 4; k++) {
    const op = feed ? -0.02 - 0.015 * beat : 0.95 + (live ? 0.12 * Math.sin(T * 1.4 + k) : 0);
    parts.push([k % 2 ? 'petalB' : 'petalA', child(child(hf, [0, 0, 0], ry(k * Math.PI / 2)), PETAL_HINGE, rx(-op))]);
  }
  dyn.mat = MAT.sinew;
  const nk = add(lerp3(R0, N, 0.5), mul(ap(body.R, [0, 0, 1]), 0.06));
  tube(dyn, quad(R0, nk, N, 8), t => lerp(0.11, 0.085, t) * (1 + 0.06 * Math.sin(t * 30)), { seg: 12 });
  if (feed) { dyn.mat = MAT.maw; tube(dyn, quad(at(jaw, [0, -0.04, 0.12]), at(jaw, [0, -0.2, 0.2]), lerp3(R0, N, 0.4), 8), 0.035, { seg: 8 }); }
  // two small sickle arms under the front; feeding, they hook into the shoulders
  const side = ap(body.R, [1, 0, 0]);
  for (const s of [-1, 1]) {
    const S = at(body, [s * 0.42, -0.66, 0.9]);
    let E, W, tip;
    if (corpse) {
      W = at(corpse, [s * 0.27, -0.42, 0.14]); tip = at(corpse, [s * 0.22, -0.5, -0.04]);
      E = add(lerp3(S, W, 0.5), add(mul(side, s * 0.22), [0, -0.05, 0]));
    } else {
      const tw = live ? 0.06 * Math.sin(T * 2.6 + s) : 0;
      E = at(body, [s * 0.58, -1.0, 1.3 + tw]); W = at(body, [s * 0.52, -1.33, 1.52 + tw]); tip = at(body, [s * 0.48, -1.45, 1.08]);
    }
    dyn.mat = MAT.steel; tube(dyn, [S, lerp3(S, E, 0.5), E], 0.042, { seg: 9 });
    dyn.mat = MAT.dark; gear(dyn, E, mul(side, s), sub(E, S), 0.075, 0.05, 10);
    dyn.mat = MAT.steel; tube(dyn, [E, lerp3(E, W, 0.5), W], 0.033, { seg: 9 });
    const pn = norm(cross(sub(W, E), sub(tip, W))), C = add(lerp3(W, tip, 0.5), mul(norm(sub(W, E)), 0.16));
    dyn.mat = tint(MAT.steel, 1.05, 2.2);
    tube(dyn, quad(W, C, tip, 12), t => 0.06 * Math.pow(1 - t, 0.8) + 0.002, { seg: 8, up: pn, shape: [0.22, 1] });
  }
  // four steel legs
  for (let s = -1; s <= 1; s += 2) for (let i = 0; i < 2; i++) {
    const L = LEGS[i], li = (s < 0 ? 0 : 2) + i;
    const H = at(body, [s * L.hip[0], L.hip[1], L.hip[2]]);
    const F = o.feet ? o.feet[li] : restFoot(ground, s, i, feed);
    const ik = ikLeg(H, add(F, [0, ANKLE, 0]), L.a, L.b), K = ik.K, E = ik.E, n = ik.n;
    const tx = norm(sub(K, H)), sx = norm(sub(E, K)), outw = norm([E[0] - o.x, 0, E[2] - o.z]);
    const thigh = frame(H, cols(tx, cross(n, tx), n)), shin = frame(K, cols(sx, cross(n, sx), n));
    parts.push([i ? 'thighR' : 'thighF', thigh], [i ? 'shinR' : 'shinF', shin], ['foot', frame(E, cols(outw, Y, cross(outw, Y)))]);
    // black cables and red sinew sagging from the belly to the leg
    const B1 = at(body, [s * (L.hip[0] - 0.2), L.hip[1] - 0.3, L.hip[2] + (i ? 0.3 : -0.3)]), B2 = at(body, [s * (L.hip[0] - 0.35), L.hip[1] - 0.45, L.hip[2] + (i ? 0.1 : -0.1)]);
    dyn.mat = MAT.cable;
    tube(dyn, sag(B1, at(thigh, [L.a * 0.62, -0.08, 0.07]), 0.32, 14), 0.026, { seg: 8 });
    tube(dyn, sag(B2, at(shin, [0.3, -0.07, -0.06]), 0.5, 16), 0.022, { seg: 8 });
    dyn.mat = MAT.sinew;
    tube(dyn, sag(add(B2, [0, 0.05, 0]), at(thigh, [L.a * 0.85, -0.1, -0.05]), 0.25, 12), 0.016, { seg: 6 });
    tube(dyn, sag(B1, at(thigh, [L.a * 0.35, -0.1, 0]), 0.12, 10), 0.012, { seg: 6 });
  }
  if (corpse) buildCorpse(dyn, corpse, T, live);
  return { parts, eyes: EYES.map(([c, r]) => [at(skull, mul(c, SK)), r * SK]), eyeLight: at(skull, mul([0.24, 0.02, 0.2], SK)), mouth: feed ? null : at(skull, mul([0, -0.22, 0.22], SK)), hoodY: hf.o[1] };
}
// held by the head in the hood, eyes and brow inside it, mouth below; dragged, arms hanging, toes on the floor
function buildCorpse(m, f, T, live) {
  const W = p => at(f, p), D = v => ap(f.R, v), ax = [D([1, 0, 0]), D([0, 1, 0]), D([0, 0, 1])];
  m.mat = MAT.skin; ellipsoid(m, W([0, 0, 0.0]), ax, [0.15, 0.185, 0.165], 18, 12);
  ellipsoid(m, W([0, -0.045, 0.16]), ax, [0.028, 0.038, 0.03], 8, 6);
  m.mat = MAT.mouth; ellipsoid(m, W([0, -0.11, 0.135]), ax, [0.048, 0.038, 0.03], 10, 6);
  m.mat = MAT.skin; tube(m, [W([0, -0.12, -0.01]), W([0, -0.3, 0])], 0.06, { seg: 10 });
  m.mat = MAT.shirt; tube(m, [W([0, -0.27, 0]), W([0, -0.5, 0.01]), W([0, -0.8, 0.01]), W([0, -1.0, 0])], t => 0.2 - 0.03 * t + 0.03 * Math.sin(Math.PI * t), { seg: 16, up: ax[0], shape: [1, 0.58], cap0: true });
  m.mat = MAT.jeans; ellipsoid(m, W([0, -1.0, 0]), ax, [0.18, 0.11, 0.11], 14, 8);
  for (const s of [-1, 1]) {
    // arms hang toward the floor, whatever angle the body is dragged at
    const S = W([s * 0.24, -0.36, 0]), sw = live ? 0.04 * Math.sin(T * 1.3 + s) : 0, E = add(S, [sw, -0.32, s * 0.02]), Hd = add(E, [sw * 1.5, -0.3, 0.03]);
    m.mat = MAT.shirt; ellipsoid(m, S, ax, [0.075, 0.075, 0.075], 10, 6); tube(m, [S, E], t => 0.062 - 0.008 * t, { seg: 10 });
    m.mat = MAT.skin; tube(m, [E, Hd], t => 0.05 - 0.01 * t, { seg: 10 }); ellipsoid(m, add(Hd, [0, -0.05, 0]), ax, [0.04, 0.065, 0.03], 8, 6);
    const lt = s < 0 ? -0.1 : 0.02, P = W([s * 0.1, -1.05, 0.02]), K = W([s * 0.11, -1.42, 0.07 + lt * 0.4]), A = W([s * 0.1, -1.76, 0.02 + lt]);
    m.mat = MAT.jeans; tube(m, [P, lerp3(P, K, 0.5), K], 0.078, { seg: 10 }); tube(m, [K, lerp3(K, A, 0.5), A], t => 0.07 - 0.012 * t, { seg: 10 });
    m.mat = MAT.shoe; ellipsoid(m, add(A, ap(f.R, [0, -0.04, 0.06])), ax, [0.055, 0.05, 0.12], 10, 6);
  }
  // what it drains drips off the hood's lip and the chin
  if (live) for (let k = 0; k < 3; k++) {
    const per = 1.2 + k * 0.33, u = ((T + k * 0.53) % per) / per, src = W([[-0.12, -0.12, 0.2], [0.1, -0.12, 0.2], [0, -0.16, 0.12]][k]);
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
    if (pick >= 0) w.steps[pick] = { from: w.feet[pick].slice(), to: [to[0], 0, to[2]], u: 0, dur: 0.55, lift: 0.75 };
  }
  // idle: now and then one leg lifts high and stabs back down where it was
  if (motion === 'idle') {
    const k = Math.floor(w.t / 3.1), u = w.t - k * 3.1, li = Math.floor(hash(k * 3.1 + (w.feeding ? 5 : 0)) * 4);
    for (let i = 0; i < 4; i++) { const r = R[i]; w.feet[i] = [r[0], 0, r[2]]; }
    if (u < 0.7) { const r = R[li], a = Math.sin(Math.PI * u / 0.7); w.feet[li] = [r[0], 0.55 * Math.pow(a, 0.6), r[2]]; }
  }
  // the carried body swings like a pendulum behind the movement
  const acc = motion === 'walk' ? [0.16 - 0.2 * Math.sin(w.t * 3.4), 0.16 * Math.cos(w.t * 1.7)] : [0, 0];
  for (let k = 0; k < 2; k++) { w.swingV[k] += (-6 * w.swing[k] - 1.2 * w.swingV[k] + acc[k] * 6) * dt; w.swing[k] += w.swingV[k] * dt; }
  if (motion === 'idle') { w.swing[0] = 0.05 * Math.sin(w.t * 1.3); w.swing[1] = 0.04 * Math.sin(w.t * 1.1 + 1); }
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
  return { top, span: Math.max(x1 - x0, z1 - z0), body: r.hoodY - HOOD_R };
}
const fmt = v => v.toFixed(1) + ' blocks';
for (const [k, f] of [['h', false], ['f', true]]) { const m = measure(f); document.getElementById(k + '-tall').textContent = fmt(m.top); document.getElementById(k + '-span').textContent = fmt(m.span); document.getElementById(k + '-body').textContent = fmt(m.body); }
