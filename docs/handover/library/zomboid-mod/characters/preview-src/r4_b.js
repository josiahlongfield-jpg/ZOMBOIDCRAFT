
// ---------------------------------------------------------------- the creature, from Josiah's renders
// Body frame: +z forward (skull), +y up, +x its left. The armour is a long egg: s runs from the rear tip (-0.1) to the
// collar (1), the angle a round it from the ridge (0) to the keel (±PI), positive toward its left.
const SZ0 = -2.15, SZ1 = 1.15;
const envR = (s, peak, span, drop) => s < peak ? Math.sqrt(Math.max(0, 1 - ((peak - s) / span) ** 2)) : 1 - drop * ((s - peak) / (1 - peak)) ** 2;
function shellPt(a, s) {
  const W = 1.17 * envR(s, 0.68, 0.78, 0.19), T = 0.3 + 0.75 * envR(s, 0.62, 0.72, 0.2), B = 0.3 - 1.28 * envR(s, 0.8, 0.9, 0.1);
  const cy = B + 0.62 * (T - B), z = SZ0 + s * (SZ1 - SZ0), sa = Math.sin(a), ca = Math.cos(a);
  if (ca >= 0) return [W * Math.sign(sa) * Math.pow(Math.abs(sa), 2 / 2.3), cy + (T - cy) * Math.pow(ca, 2 / 2.3), z];
  return [W * Math.sign(sa) * Math.pow(Math.abs(sa), 2 / 1.85), cy - (cy - B) * Math.pow(-ca, 2 / 1.85), z];
}
function shellOff(a, s, off) {
  const p = shellPt(a, s), e = 1e-3;
  let n = cross(sub(shellPt(a, s + e), shellPt(a, s - e)), sub(shellPt(a + e, s), shellPt(a - e, s)));
  n = len(n) < 1e-12 ? [0, 0, -1] : norm(n);
  return [add(p, mul(n, off)), n];
}
const COLLAR = frame([0, 0.02, 1.0], rx(0.38));       // the ringed collars round the skull, tilted down
const RINGS = [0.8, 0.71, 0.62, 0.54];
const SK = 1.42, SKULL_AT = [0, -0.08, 0.8], JAW_AT = [0, -0.12 * SK, -0.03 * SK];   // the skull is drawn SK times its modelled size
const EYES = [[[0.262, 0.07, 0.07], 0.056], [[0.252, -0.045, 0.095], 0.034]];   // big over small, on its left
const HOOD_R = 0.235, HL0 = -0.3, HL1 = 1.0;             // the hood: a ball of four petals, from the nose up to its cup
const PETAL_HINGE = [0, HOOD_R * Math.sin(HL1), HOOD_R * Math.cos(HL1)];
const ANKLE = 0.34;                                     // the ankle stands this high over the point it rests on
const BODY_Y = { hunt: 2.55, feed: 2.85 };
const LEGS = [{ hip: shellOff(1.95, 0.8, 0.02)[0], a: 1.0, b: 1.68, restA: 0.9, restR: 2.56 },  // front
              { hip: shellOff(1.42, 0.27, 0.02)[0], a: 1.3, b: 1.86, restA: 2.49, restR: 3.14 }];    // back
const CORPSE_LEN = 1.85;                                // head centre to toes

function buildBody() {
  const m = new Mesh();
  // the red inside, ribbed underneath
  m.mat = MAT.core;
  grid(m, 60, 46, (u, v) => {
    const a = u * TAU - Math.PI, s = lerp(-0.095, 1.0, v), ca = Math.cos(a);
    return shellOff(a, s, -0.09 + (ca < -0.2 ? 0.03 * Math.sin(s * 75) * (-ca - 0.2) : 0))[0];
  }, { wrap: true, flip: true, ao: () => 0.55 });
  // the armour: rows of plates from the rear forward, each trailing edge cut into teeth and lifted over the row behind;
  // the two front rows are wide bands like the collars
  const ROWS = 9, sA = -0.06, sB = 0.97, dsr = (sB - sA) / ROWS;
  const arc = (amax, s) => { let L = 0, p = shellPt(-amax, s); for (let k = 1; k <= 24; k++) { const q = shellPt(-amax + 2 * amax * k / 24, s); L += len(sub(q, p)); p = q; } return L; };
  for (let r = 0; r < ROWS; r++) {
    const band = r === ROWS - 1, s0 = sA + r * dsr - 0.032, s1 = sA + (r + 1) * dsr + (band ? 0.06 : 0.012), sm = Math.min(0.99, (s0 + s1) / 2);
    const amax = r === 0 ? Math.PI : (band ? 2.3 : 2.12), L = arc(amax, sm);
    const n = band ? 2 : Math.max(3, Math.round(L / 0.62)), w = 2 * amax / n, edges = [];
    if (r % 2 && !band && r) { edges.push(-amax); for (let c = 0; c < n; c++) edges.push(-amax + (c + 0.5) * w); edges.push(amax); }
    else for (let c = 0; c <= n; c++) edges.push(-amax + c * w);
    for (let c = 0; c + 1 < edges.length; c++) {
      const id = r * 37 + c * 11 + 3, a0 = edges[c] - 0.045 - 0.07 * hash(id + 40), a1 = edges[c + 1] + 0.045 + 0.07 * hash(id + 41), ds = band ? 0 : 0.02 * (hash(id + 42) - 0.5);
      const wide = (a1 - a0) / (2 * amax) * L, teeth = band ? 10 : clamp(Math.round(wide / (0.13 + 0.1 * hash(id + 43))), 2, 7);
      const tipS = band ? 0.03 : 0.02 + 0.018 * hash(id), lift = band ? 0.05 : 0.045 + 0.05 * hash(id + 1);
      const base = 0.035 + (c % 2) * 0.009 + (band ? 0.04 : 0), tips = [];
      for (let k = 0; k < teeth; k++) tips.push(tipS * (0.15 + 1.7 * Math.pow(hash(id * 3 + k), 1.5)));
      m.mat = tint(band ? MAT.band : MAT.plate, 0.8 + 0.36 * hash(id + 2), 1 + 0.85 * hash(id + 5));
      slab(m, teeth * 2, 5, (u, v, i) => {
        const tip = i % 2 ? tips[i >> 1] : 0, a = lerp(a0, a1, u);
        const s = Math.max(-0.098, s1 + ds - (s1 - s0) * v - tip * v * v * v);
        if (band) { const [p, n] = shellOff(a, Math.min(s, 0.995), base + lift * v * v + 0.018 * Math.pow(Math.abs(2 * u - 1), 4)), fw = Math.max(0, s - 0.995) * 3.3, up = 1 - Math.abs(2 * u - 1); return [add(add(p, [0, 0, fw]), mul(n, fw * (0.18 + 0.2 * up))), n]; }
        return shellOff(a, s, base + lift * v * v + tip * 2.4 * v * v * v + 0.018 * Math.pow(Math.abs(2 * u - 1), 4));
      }, 0.024, { ao: (u, v) => 0.35 + 0.65 * smooth(v * 1.5) });
    }
  }
  // scrap plates laid over the rows at odd angles, so it reads as armour bolted on piece by piece
  for (let k = 0; k < 64; k++) {
    const big = k < 6, id = 900 + k * 7, ac = big ? (k % 2 ? 1 : -1) * (1.0 + 0.35 * hash(id)) : (hash(id) - 0.5) * 3.7, sc = big ? 0.55 + 0.25 * hash(id + 1) : 0.06 + 0.72 * hash(id + 1);
    const w = big ? 0.6 : 0.26 + 0.3 * hash(id + 2), h = big ? 0.5 : 0.2 + 0.24 * hash(id + 3), th = (hash(id + 4) - 0.5) * 1.1, Wr = Math.max(0.5, shellPt(Math.PI / 2, sc)[0]);
    const teeth = clamp(Math.round(w / 0.1), 3, 6), tips = [];
    for (let q = 0; q < teeth; q++) tips.push(0.04 + 0.09 * hash(id * 3 + q));
    const base = (big ? 0.1 : 0.07) + 0.025 * hash(id + 5), lift = big ? 0.22 : 0.035 + 0.06 * hash(id + 6);
    m.mat = tint(MAT.plate, 0.75 + 0.42 * hash(id + 7), 1 + 0.85 * hash(id + 8));
    slab(m, teeth * 2, 4, (u, v, i) => {
      const tip = i % 2 ? tips[i >> 1] : 0, x = (u - 0.5) * w, y = h * 0.5 - v * (h + tip * v * v);
      const xr = x * Math.cos(th) - y * Math.sin(th), yr = x * Math.sin(th) + y * Math.cos(th);
      return shellOff(ac + xr / Wr, clamp(sc + yr / 3.3, -0.09, 0.97), base + lift * v * v + tip * 1.4 * v * v * v);
    }, 0.022, { ao: (u, v) => 0.5 + 0.5 * v });
  }
  // spines: a crest along the ridge, two rows each side, scattered ones, and a fringe round the rear
  const back = [0, 0, -1];
  const horn = (a, s, h, lean, out, r) => {
    const [p, n] = shellOff(a, s, 0.02), side = [Math.sign(a) || 0, 0, 0];
    const dir = norm(add(add(mul(n, Math.cos(lean)), mul(back, Math.sin(lean))), mul(side, out)));
    m.mat = tint(MAT.plate, 0.85 + 0.3 * hash(a * 7 + s * 13), 1 + 0.8 * hash(a * 3 + s * 29));
    spike(m, add(p, mul(n, -0.05)), dir, h, r, { up: [0, 0, 1], shape: [1, 0.3], bend: back, seg: 8, pow: 1.25 });
  };
  for (let r = 1; r < ROWS - 1; r++) { const s = sA + r * dsr + 0.015; horn(0.02 * (hash(r) - 0.5), s, 0.4 + 0.42 * Math.sin(Math.PI * (r + 1) / ROWS) + 0.15 * hash(r + 4), 0.6, 0, 0.15); }
  for (const sd of [-1, 1]) for (let r = 1; r < ROWS - 1; r++) {
    const s = sA + r * dsr + 0.03;
    horn(sd * (0.58 + 0.06 * hash(r + sd)), s, 0.2 + 0.26 * hash(r * 3 + sd), 0.7, 0.25, 0.1);
    if (r % 2) horn(sd * (1.2 + 0.1 * hash(r + 9)), s - 0.02, 0.1 + 0.14 * hash(r * 5 + sd), 0.75, 0.4, 0.045);
  }
  for (let k = 0; k < 26; k++) horn((hash(k + 300) - 0.5) * 3.4, 0.08 + 0.75 * hash(k + 340), 0.07 + 0.13 * hash(k + 380), 0.8, 0.3, 0.035);
  for (let k = 0; k < 9; k++) horn(-2.4 + 4.8 * k / 8, 0.0 + 0.03 * hash(k + 60), 0.22 + 0.18 * hash(k + 70), 1.1, 0.3, 0.06);
  // ribs and hanging sinew under the belly, black cables along the flanks
  m.mat = MAT.dark;
  for (let k = 0; k < 8; k++) {
    const s = 0.2 + k * 0.085, pts = [];
    for (let q = 0; q <= 18; q++) pts.push(shellOff(1.88 + (TAU - 3.76) * q / 18, s, -0.05 + 0.035 * Math.sin(Math.PI * q / 18))[0]);
    tube(m, pts, 0.028, { seg: 7 });
  }
  for (let k = 0; k < 16; k++) {
    m.mat = k % 3 ? MAT.sinewD : MAT.sinew;
    const a1 = 1.95 + hash(k) * 2.38, s1 = 0.12 + hash(k + 50) * 0.8, a2 = a1 + (hash(k + 90) - 0.5) * 1.3, s2 = clamp(s1 + (hash(k + 130) - 0.5) * 0.35, 0.08, 0.95);
    const P = shellOff(a1, s1, -0.06)[0], Q = shellOff(a2, s2, -0.06)[0], sag = 0.08 + 0.4 * hash(k + 170);
    tube(m, quad(P, add(lerp3(P, Q, 0.5), [0, -1.2 * sag, 0]), Q, 10), 0.007 + 0.01 * hash(k + 210), { seg: 6 });
  }
  m.mat = MAT.cable;
  for (const sd of [-1, 1]) for (let k = 0; k < 4; k++) {
    const P = shellOff(sd * (1.85 + 0.1 * k), 0.95 - 0.05 * k, 0.0)[0], Q = shellOff(sd * (2.05 + 0.1 * hash(k)), 0.45 - 0.08 * k, -0.02)[0];
    tube(m, quad(P, add(lerp3(P, Q, 0.5), [sd * 0.08, -0.25 - 0.1 * k, 0]), Q, 14), 0.022, { seg: 8 });
  }
  // sockets where the legs join
  m.mat = MAT.dark;
  for (const L of LEGS) for (const sd of [-1, 1]) {
    const hp = [sd * L.hip[0], L.hip[1], L.hip[2]], out = norm([sd, 0, 0]);
    tube(m, [sub(hp, mul(out, 0.18)), add(hp, mul(out, -0.02))], 0.2, { seg: 18, cap1: true });
  }
  // the collars: four telescoping rings, open and torn at the bottom, a sinew throat inside
  m.xf = COLLAR;
  RINGS.forEach((R0, k) => {
    const zc = 0.04 + 0.17 * k, L = 0.36, id = 500 + k, eL = [], eR = [];
    for (let q = 0; q <= 6; q++) { eL.push(2.4 + 0.3 * hash(id * 7 + q)); eR.push(2.4 + 0.3 * hash(id * 11 + q)); }
    m.mat = tint(MAT.band, 0.88 + 0.22 * hash(id), 1 + 0.6 * hash(id + 1));
    slab(m, 44, 6, (u, v, i, j) => {
      const a = lerp(-eR[j], eL[j], u), z = zc - L / 2 + L * v, R = R0 + 0.05 * smooth((v - 0.55) / 0.45) - 0.012 * (1 - v);
      return [[R * Math.sin(a), R * Math.cos(a), z], norm([Math.sin(a), Math.cos(a), -0.2])];
    }, 0.06, { ao: (u, v) => 0.45 + 0.55 * v });
  });
  m.mat = MAT.core;
  tube(m, [[0, -0.02, -0.3], [0, -0.04, 0.05], [0, -0.06, 0.35], [0, -0.05, 0.6]], (t, th) => lerp(0.66, 0.26, t) * (1 + 0.05 * Math.sin(th * 9 + t * 21)), { seg: 26, cap1: true, ao: t => 0.35 + 0.3 * t });
  m.mat = MAT.cable;
  for (let k = 0; k < 7; k++) {
    const a = k * 0.9 + 0.4, r0 = 0.62, r1 = 0.3;
    tube(m, cubic([r0 * Math.sin(a), r0 * Math.cos(a), -0.1], [r0 * 0.9 * Math.sin(a + 0.4), r0 * 0.9 * Math.cos(a + 0.4), 0.2], [r1 * 1.4 * Math.sin(a + 0.6), r1 * 1.4 * Math.cos(a + 0.6), 0.45], [r1 * Math.sin(a + 0.9), r1 * Math.cos(a + 0.9), 0.62], 12), 0.02, { seg: 7 });
  }
  m.xf = null;
  return m;
}

// the skull: a sculpted distance field, long jagged teeth, two red ring eyes beside it on its left
function skullSDF(p) {
  let d = sdE(p, [0, 0.06, -0.03], [0.19, 0.2, 0.24]);
  d = smin(d, sdE(p, [0, -0.05, 0.08], [0.15, 0.12, 0.15]), 0.06);
  d = smin(d, sdE(p, [0, 0.045, 0.15], [0.165, 0.045, 0.07]), 0.03);
  for (const s of [-1, 1]) d = smin(d, sdE(p, [s * 0.12, -0.06, 0.1], [0.05, 0.035, 0.07]), 0.03);
  d = smin(d, sdE(p, [0, -0.13, 0.13], [0.1, 0.06, 0.09]), 0.04);
  for (const s of [-1, 1]) d = smax(d, -sdE(p, [s * 0.07, -0.005, 0.2], [0.05, 0.048, 0.07]), 0.012);
  d = smax(d, -sdE(p, [0, -0.085, 0.222], [0.022, 0.04, 0.06]), 0.008);
  for (const s of [-1, 1]) d = smax(d, -sdE(p, [s * 0.215, -0.03, 0.02], [0.05, 0.07, 0.09]), 0.03);
  d = smax(d, -sdE(p, [-0.09, 0.17, 0.04], [0.06, 0.03, 0.05]), 0.01);               // a broken patch on the crown
  return d + 0.006 * (vnoise(p[0] * 34, p[1] * 34, p[2] * 34) - 0.5) + 0.003 * (vnoise(p[0] * 90, p[1] * 90, p[2] * 90) - 0.5);
}
function buildSkull() {
  const m = new Mesh();
  m.scale = SK;
  m.mat = MAT.bone;
  sdfMesh(m, skullSDF, [-0.24, -0.23, -0.3], [0.24, 0.29, 0.3], 0.0105);
  for (let k = 0; k < 12; k++) {
    const f = (k + 0.5) / 12 * 2.5 - 1.25, root = [0.085 * Math.sin(f), -0.155, 0.13 + 0.085 * Math.cos(f)];
    const L = (0.07 + 0.1 * hash(k + 3)) * (1 - 0.3 * Math.abs(f)), dir = norm([0.2 * (hash(k + 9) - 0.5) + 0.12 * Math.sin(f), -1, 0.15 * Math.cos(f) + 0.1 * (hash(k + 11) - 0.5)]);
    m.mat = tint(MAT.bone, 0.88 + 0.16 * hash(k + 20));
    spike(m, root, dir, L, 0.015 + 0.006 * hash(k + 30), { seg: 6, n: 4, shape: [1, 0.7], up: [Math.sin(f), 0, Math.cos(f)] });
  }
  for (const [c, R] of EYES) {
    const f = norm([0.4, 0.05, 1]), x = norm(cross(Y, f)), y = cross(f, x), ax = [x, y, f];
    m.mat = MAT.dark; tube(m, [sub(c, mul(f, 0.1)), add(c, mul(f, 0.004))], R * 1.5, { seg: 18, cap1: true });
    m.mat = MAT.lens; ellipsoid(m, add(c, mul(f, 0.006)), ax, [R * 1.05, R * 1.05, R * 0.3], 18, 8);
    m.mat = MAT.eye; torus(m, add(c, mul(f, 0.014)), ax, R * 0.78, R * 0.17, 28, 8);
    ellipsoid(m, add(c, mul(f, 0.02)), ax, [R * 0.2, R * 0.2, R * 0.1], 10, 6);
  }
  // cables and sinew from behind the skull into the throat
  m.mat = MAT.cable;
  for (let k = 0; k < 5; k++) { const a = k * 1.3; tube(m, quad([0.12 * Math.sin(a), 0.12 * Math.cos(a) - 0.02, -0.2], [0.3 * Math.sin(a + 0.3), 0.3 * Math.cos(a + 0.3), -0.3], [0.38 * Math.sin(a + 0.5), 0.38 * Math.cos(a + 0.5), -0.25], 8), 0.018, { seg: 6 }); }
  return m;
}
// the jaw, hanging: a jawbone, lower teeth and a beard of bone shards
function buildJaw() {
  const m = new Mesh();
  m.scale = SK;
  m.mat = MAT.bone;
  const pts = [[-0.125, 0.01, -0.02]];
  for (let k = 0; k <= 16; k++) { const f = -1.5 + 3.0 * k / 16; pts.push([0.115 * Math.sin(f), -0.075 - 0.025 * Math.cos(f), 0.03 + 0.165 * Math.cos(f)]); }
  pts.push([0.125, 0.01, -0.02]);
  tube(m, pts, t => 0.017 + 0.012 * Math.sin(Math.PI * t), { seg: 10 });
  for (let k = 0; k < 9; k++) {
    const f = -1.0 + 2.0 * k / 8, root = [0.1 * Math.sin(f), -0.065, 0.03 + 0.15 * Math.cos(f)];
    spike(m, root, norm([0.1 * (hash(k + 40) - 0.5), 1, 0.1]), 0.035 + 0.05 * hash(k + 41), 0.011, { seg: 5, n: 3 });
  }
  for (let k = 0; k < 18; k++) {
    const f = -1.15 + 2.3 * hash(k + 60), row = k % 3, root = [0.1 * Math.sin(f) * (1 - 0.15 * row), -0.09 - 0.01 * row, 0.03 + 0.15 * Math.cos(f) - 0.03 * row];
    const Ls = (0.15 + 0.32 * hash(k + 61)) * (1 - 0.35 * Math.abs(f)), dir = norm([0.3 * Math.sin(f) + 0.3 * (hash(k + 62) - 0.5), -1, 0.25 + 0.3 * (hash(k + 63) - 0.5)]);
    const side = norm(cross(dir, Y)), kink = (hash(k + 64) - 0.5) * 0.06;
    m.mat = tint(MAT.bone, 0.82 + 0.2 * hash(k + 65));
    tube(m, [root, add(add(root, mul(dir, Ls * 0.35)), mul(side, kink)), add(add(root, mul(dir, Ls * 0.7)), mul(side, -kink * 0.6)), add(root, mul(dir, Ls))],
      t => (0.014 + 0.01 * hash(k + 66)) * Math.pow(1 - t, 0.9) + 0.001, { seg: 6, shape: [1, 0.6] });
  }
  return m;
}
// a hood petal (curved, jagged, red inside) round its hinge; two radii so closed petals overlap cleanly
function buildPetal(extra) {
  const m = new Mesh(), teeth = 6, tl = [];
  for (let k = 0; k < teeth; k++) tl.push(0.08 + 0.12 * hash(k + (extra ? 90 : 80)));
  m.mat = tint(MAT.plate, 0.95, 1.3);
  slab(m, teeth * 2, 7, (u, v, i) => {
    const az = lerp(-0.9, 0.9, u), tip = i % 2 ? tl[i >> 1] : 0, lat = lerp(HL1, HL0, v) - tip * v * v * v, r = HOOD_R + extra + 0.008 * v;
    const n = [Math.cos(lat) * Math.sin(az), Math.sin(lat), Math.cos(lat) * Math.cos(az)];
    return [sub(mul(n, r), PETAL_HINGE), n];
  }, 0.022, { matIn: MAT.maw, aoIn: () => 0.6 });
  // little hooks on the inner lip
  m.mat = MAT.bone;
  for (let k = 0; k < 5; k++) {
    const az = -0.7 + 1.4 * k / 4, lat = HL0 + 0.12, n = [Math.cos(lat) * Math.sin(az), Math.sin(lat), Math.cos(lat) * Math.cos(az)];
    spike(m, sub(mul(n, HOOD_R + extra - 0.01), PETAL_HINGE), norm(add(mul(n, -1), [0, 0.5, 0])), 0.06, 0.01, { seg: 5, n: 3 });
  }
  return m;
}
function buildCup() {
  const m = new Mesh();
  m.mat = tint(MAT.plate, 0.9, 1.5);
  slab(m, 26, 4, (u, v) => {
    const az = u * TAU, lat = lerp(Math.PI / 2 - 0.02, HL1 - 0.08, v), n = [Math.cos(lat) * Math.sin(az), Math.sin(lat), Math.cos(lat) * Math.cos(az)];
    return [mul(n, HOOD_R + 0.03), n];
  }, 0.026, { wrap: true, matIn: MAT.maw });
  m.mat = MAT.dark;
  tube(m, [[0, HOOD_R - 0.01, 0], [0, HOOD_R + 0.07, 0]], 0.11, { seg: 16, cap1: true });
  gear(m, [0, HOOD_R + 0.04, 0], Y, [1, 0, 0], 0.15, 0.05, 12);
  return m;
}
// a leg piece along +x from its joint: gear, beam, overlapping toothed sleeves, twin pistons on its outer (+y) side, thorns
function buildLegPiece(Lb, rA, rB, gearR, seed, shin) {
  const m = new Mesh();
  m.mat = MAT.dark; gear(m, [0, 0, 0], [0, 0, 1], [1, 0, 0], gearR, 0.12, 15);
  m.mat = MAT.steel;
  const core = []; for (let k = 0; k <= 10; k++) core.push([0.03 + (Lb - 0.06) * k / 10, 0, 0]);
  tube(m, core, t => lerp(rA, rB, t), { seg: 12, up: [0, 1, 0], shape: [1.15, 0.9] });
  const x0 = shin ? Lb * 0.3 : gearR * 0.8, x1 = Lb - (shin ? 0.16 : 0.1), nb = shin ? Math.max(3, Math.round((x1 - x0) / 0.2)) : 3;
  for (let k = 0; k < nb; k++) {
    const a = shin ? x0 + (x1 - x0) * k / nb : [x0, Lb * 0.45, Lb * 0.78][k], b = shin ? a + (x1 - x0) / nb + 0.06 : a + 0.2, big = shin && k >= nb * 0.3;
    const ra = lerp(rA, rB, a / Lb) + 0.02, rb = lerp(rA, rB, b / Lb) + (big ? 0.055 : 0.035), teeth = big ? 8 : 6, tl = [];
    for (let q = 0; q < teeth; q++) tl.push((big ? 0.035 : 0.018) + 0.03 * hash(seed + k * 13 + q));
    m.mat = tint(MAT.steel, 0.82 + 0.3 * hash(seed + k), 2 + 0.85 * hash(seed + k + 7));
    slab(m, teeth * 2, 3, (u, v, i) => {
      const th = u * TAU, tip = i % 2 ? tl[i >> 1] : 0, w = v * v * v, x = lerp(a, b, v) + tip * w, r = lerp(ra, rb, v * v) + tip * 0.45 * w;
      const c = Math.cos(th), s = Math.sin(th);
      return [[x, r * c * 1.12, r * s * 0.95], norm([-(rb - ra) / (b - a), c, s])];
    }, 0.014, { wrap: true, ao: (u, v) => 0.55 + 0.45 * v });
  }
  const py = Math.max(rA, rB) * 1.2 + 0.08;
  for (const z of [-0.068, 0.068]) {
    m.mat = MAT.dark; tube(m, [[Lb * 0.1, py, z], [Lb * 0.56, py, z]], 0.045, { seg: 12, cap0: true, cap1: true });
    m.mat = MAT.chrome; tube(m, [[Lb * 0.54, py, z], [Lb * 0.9, py - 0.02, z]], 0.024, { seg: 10, cap1: true });
  }
  m.mat = MAT.chrome; tube(m, [[Lb * 0.08, -Math.max(rA, rB) - 0.06, 0], [Lb * 0.92, -Math.min(rA, rB) - 0.05, 0]], 0.022, { seg: 8 });
  m.mat = MAT.dark;
  box(m, [Lb * 0.08, -Math.max(rA, rB) * 0.6 - 0.03, 0], AX, [0.06, Math.max(rA, rB) + 0.06, 0.08]); box(m, [Lb * 0.92, -Math.min(rA, rB) * 0.6 - 0.025, 0], AX, [0.06, Math.min(rA, rB) + 0.05, 0.08]);
  box(m, [Lb * 0.1, py * 0.55, 0], AX, [0.07, py * 0.9, 0.2]);
  box(m, [Lb * 0.9, (py - 0.02) * 0.55, 0], AX, [0.06, py * 0.9, 0.15]);
  box(m, [Lb * 0.56, py, 0], AX, [0.05, 0.05, 0.16]);
  m.mat = MAT.steel;
  for (let k = 0; k < 6; k++) {
    const x = Lb * (0.18 + 0.13 * k), r = lerp(rA, rB, x / Lb) + 0.04;
    spike(m, [x, -r * 0.9, 0], norm([0.7, -1, 0]), 0.06 + 0.06 * hash(seed + 40 + k), 0.02, { seg: 5, n: 3 });
  }
  m.mat = MAT.sinew;
  tube(m, quad([0.12, -0.03, -rA - 0.03], [Lb * 0.5, -0.22, -rA - 0.06], [Lb - 0.12, -0.03, -rB - 0.03], 12), 0.016, { seg: 6 });
  m.mat = MAT.cable;
  tube(m, quad([0.15, 0.04, rA + 0.04], [Lb * 0.45, -0.1, rA + 0.1], [Lb - 0.2, 0.0, rB + 0.04], 12), 0.02, { seg: 7 });
  return m;
}
// a foot, from the ankle: gear, a spike to the floor, three hooked talons forward and one back, each segmented and serrated
function buildFoot() {
  const m = new Mesh();
  m.mat = MAT.dark; gear(m, [0, 0, 0], [0, 0, 1], [1, 0, 0], 0.155, 0.1, 12);
  m.mat = MAT.steel;
  tube(m, [[0, -0.02, 0], [0, -0.13, 0]], t => 0.1 - 0.02 * t, { seg: 14, cap1: true });
  tube(m, [[0, -0.1, 0], [0, -ANKLE + 0.005, 0]], t => 0.075 * Math.pow(1 - t, 0.9) + 0.002, { seg: 10 });
  for (let k = 0; k < 4; k++) { const y = -0.15 - 0.04 * k; torus(m, [0, y, 0], [[1, 0, 0], [0, 0, 1], [0, -1, 0]], 0.07 * (1 - (k + 1) * 0.2), 0.012, 14, 6); }
  [[0, 0.98], [0.72, 0.82], [-0.72, 0.82], [Math.PI, 0.6]].forEach(([an, L], q) => {
    const d = [Math.cos(an), 0, Math.sin(an)];
    const pts = cubic(add(mul(d, 0.05), [0, -0.1, 0]), add(mul(d, 0.32 * L), [0, 0.1, 0]), add(mul(d, 0.8 * L), [0, 0.07, 0]), add(mul(d, L), [0, -ANKLE + 0.008, 0]), 22);
    m.mat = tint(MAT.steel, 0.9 + 0.12 * hash(q + 7), 2 + 0.5 * hash(q + 8));
    tube(m, pts, t => (0.085 * Math.pow(1 - t, 0.75) + 0.003) * (1 + 0.24 * smooth((t * 5 % 1 - 0.55) / 0.4)), { seg: 10, up: Y, shape: [1, 0.74] });
    for (let k = 1; k <= 5; k++) {
      const t = k / 6.3, i = Math.round(t * 22), p = pts[i], T = norm(sub(pts[i + 1], pts[i - 1])), up = norm(sub(Y, mul(T, dot(Y, T))));
      spike(m, add(p, mul(up, 0.06 * Math.pow(1 - t, 0.75))), norm(add(up, mul(T, -0.5))), 0.06 * (1 - t * 0.5), 0.017, { seg: 5, n: 3 });
    }
  });
  return m;
}
// the player, for scale
function buildPlayer() {
  const m = new Mesh(), s = 0.9 / 16;
  m.mat = MAT.pSkin; box(m, [0, 28 * s, 0], AX, [8 * s, 8 * s, 8 * s]);
  m.mat = MAT.pHair; box(m, [0, 31.2 * s, -0.4 * s], AX, [8.3 * s, 1.8 * s, 8.3 * s]);
  m.mat = MAT.pShirt; box(m, [0, 18 * s, 0], AX, [8 * s, 12 * s, 4 * s]);
  for (const sd of [-1, 1]) {
    m.mat = MAT.pShirt; box(m, [sd * 6 * s, 21 * s, 0], AX, [4 * s, 6 * s, 4 * s]);
    m.mat = MAT.pSkin; box(m, [sd * 6 * s, 15 * s, 0], AX, [4 * s, 6 * s, 4 * s]);
    m.mat = MAT.pPants; box(m, [sd * 2 * s, 6 * s, 0], AX, [4 * s, 12 * s, 4 * s]);
  }
  return m;
}
function buildFloor() {
  const m = new Mesh();
  m.mat = MAT.floor;
  grid(m, 48, 48, (u, v) => [lerp(-34, 34, u), 0, lerp(-34, 34, v)], { flip: true });
  return m;
}
const MESHES = {
  body: buildBody(), skull: buildSkull(), jaw: buildJaw(), petalA: buildPetal(0), petalB: buildPetal(0.016), cup: buildCup(),
  thighF: buildLegPiece(LEGS[0].a, 0.12, 0.1, 0.26, 11, false), shinF: buildLegPiece(LEGS[0].b, 0.1, 0.075, 0.21, 23, true),
  thighR: buildLegPiece(LEGS[1].a, 0.12, 0.1, 0.26, 37, false), shinR: buildLegPiece(LEGS[1].b, 0.1, 0.075, 0.21, 51, true),
  foot: buildFoot(), player: buildPlayer(), floor: buildFloor(),
};
