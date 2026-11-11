
// ---------------------------------------------------------------- the creature: a small armoured body slung between four long legs
// Body frame: +z forward (skull), +y up, +x its left. The armour is drawn at design size and shrunk by BS: s runs from the
// rear tip (-0.1) to the collar (1), the angle a round it from the ridge (0) to the keel (±PI), positive toward its left.
const BS = 0.5, SZ0 = -2.45, SZ1 = 1.15;
// rises from a pointed rear to a peak, then falls off a little toward the collar
const envR = (s, peak, span, drop) => s < peak ? Math.pow(Math.max(0, 1 - ((peak - s) / span) ** 2), 0.9) : 1 - drop * ((s - peak) / (1 - peak)) ** 2;
function shellPt(a, s) {
  const W = 0.92 * envR(s, 0.64, 0.74, 0.22), T = 0.22 + 0.6 * envR(s, 0.6, 0.7, 0.22), B = 0.22 - 0.95 * envR(s, 0.72, 0.82, 0.14);
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
// from here on in blocks
const FRONT_Y = (shellPt(0, 1)[1] + shellPt(Math.PI, 1)[1]) / 2 * BS;
const COLLAR = frame([0, FRONT_Y, SZ1 * BS - 0.04], rx(0.3));   // the ringed collars round the skull, tilted down
const RINGS = [0.37, 0.34, 0.31, 0.285];
const SK = 1.15, SKULL_AT = [0, -0.33, 0.12], JAW_AT = [0, -0.12 * SK, -0.03 * SK];   // the skull is drawn SK times its modelled size
const EYES = [[[0.262, 0.07, 0.07], 0.056], [[0.252, -0.045, 0.095], 0.034]];   // big over small, on its left
const BODY_Y = { hunt: 2.5, feed: 2.41 };
// four long legs from the upper flanks: a thigh up to a high knee, a shin down to the ankle, a long armoured point
// round 6: a third joint, an ankle, between the knee and the point (c = ankle to tip)
// the lower leg is one rigid piece curved smoothly from knee to point: CURVE is how far it turns end to end (radians),
// plus two slight bends (KINK each) at a third and two thirds of the way down; lc is the straight line from knee to
// point that the IK works with
const CURVE = 0.35, KINK = 0.22, KINK_W = 0.08, ARCS = {};
function legArc(S) {
  if (ARCS[S]) return ARCS[S];
  const N = 480, ds = S / N, sm = (s, s0) => smooth((s - s0 + KINK_W) / (2 * KINK_W));
  const th = s => CURVE * s / S + KINK * (sm(s, S / 3) + sm(s, 2 * S / 3));
  const P = [[0, 0]], T = [th(0)];
  for (let k = 1; k <= N; k++) {
    const t = th((k - 0.5) * ds), q = P[k - 1];
    P.push([q[0] + Math.cos(t) * ds, q[1] - Math.sin(t) * ds]); T.push(th(k * ds));
  }
  const e = P[N];
  return (ARCS[S] = { P, T, ds, N, lc: Math.hypot(e[0], e[1]), psi: -Math.atan2(e[1], e[0]) });
}
const LEGS = [{ s: 0.72, a0: 1.3, a: 1.28, b: 2.15, c: 1.6, restA: 0.74, restR: 1.75 },     // front
              { s: 0.42, a0: 1.42, a: 1.28, b: 2.2, c: 1.65, restA: 2.36, restR: 1.85 }]    // back
  // the legs hang from ball joints in sockets on the lower edge of the hull, clear of it (left side; the right mirrors it)
  .map((L, i) => ({ ...L, hip: i ? [0.55, 0.12, -0.45] : [0.46, -0.08, 0.06], hn: norm([0.7, -0.7, i ? -0.1 : 0.1]), lc: legArc(L.b + L.c).lc }));
const CORPSE_LEN = 1.54;                                // neck to toes
// feeding: how far the head bows and the jaw drops, and where a victim's head sits in the mouth (skull space, unscaled)
const FEED_PITCH = -0.4, FEED_JAW = 1.3;
const FEED = (() => {
  const c = Math.cos(FEED_JAW), s = Math.sin(FEED_JAW), jt = [0, -0.1, 0.2];
  const tip = add([0, -0.12, -0.03], [0, c * jt[1] - s * jt[2], s * jt[1] + c * jt[2]]), upper = [0, -0.21, 0.17];
  const v = sub(upper, tip), out = norm([0, -v[2], v[1]]), mc = lerp3(upper, tip, 0.5);
  const z = mul(out, -1), y = norm(sub(Y, mul(z, dot(Y, z)))), x = cross(y, z);
  return { at: add(mc, mul(out, 0.02)), R: cols(x, y, z) };
})();

// round 6d body, from Josiah's armoured render: a dart, low at the nose and rising to a tail that ends in a long spine.
// From the front it reads as a downward arrowhead: long creased blade plates sweep out and forward from a tall centre keel
// in a herringbone, jagged spines crown the back, and a beard of shards hangs under the point. In blocks.
// s runs from the tail (0) to the nose (1); a round the body from the keel (0) to the belly (±PI), positive to its left.
const B7 = { zt: -1.55, zn: 0.95 };
const b7env = s => s < 0.5 ? Math.pow(Math.max(0, s / 0.5), 0.7) : Math.pow(Math.max(0, 1 - (s - 0.5) / 0.5), 0.85);
const b7c = s => 0.1 + 0.55 * Math.pow(1 - s, 2.2) - 0.5 * smooth((s - 0.42) / 0.58);   // centre line: up to the tail, down to the nose
function b7Pt(a, s) {
  s = clamp(s, 0, 1);
  const e = b7env(s), W = 0.52 * e, T = 0.26 * e, Bt = 0.22 * e, c = b7c(s), z = lerp(B7.zt, B7.zn, s), sa = Math.sin(a), ca = Math.cos(a);
  if (ca >= 0) return [W * Math.sign(sa) * Math.pow(Math.abs(sa), 0.75), c + T * Math.pow(ca, 0.7), z];
  return [W * Math.sign(sa) * Math.pow(Math.abs(sa), 0.8), c - Bt * Math.pow(-ca, 0.85), z];
}
function b7Off(a, s, off) {
  const p = b7Pt(a, s), e = 1e-3, sc = clamp(s, 0.02, 0.98);
  let n = cross(sub(b7Pt(a, sc + e), b7Pt(a, sc - e)), sub(b7Pt(a + e, sc), b7Pt(a - e, sc)));
  n = len(n) < 1e-12 ? [0, 1, 0] : norm(n);
  if (dot(n, sub(p, [0, b7c(sc), lerp(B7.zt, B7.zn, sc)])) < 0) n = mul(n, -1);
  return [add(p, mul(n, off)), n];
}
function buildBody() {
  const m = new Mesh();
  // the red inside, ribbed underneath: it shows in the gaps between the plates
  m.mat = MAT.core;
  grid(m, 56, 44, (u, v) => { const a = u * TAU - Math.PI, ca = Math.cos(a); return b7Off(a, lerp(0.005, 0.995, v), -0.03 + (ca < -0.3 ? 0.012 * Math.sin(v * 70) * (-ca - 0.3) : 0))[0]; }, { wrap: true, flip: true, ao: () => 0.5 });
  // a long blade plate along a path on the shell, from its root (v = 0) to its point (v = 1): a fold down its middle, the
  // point lifted off the shell so it lies over the plate in front
  const blade = (path, wid, off, lift, id, mat, crease = 0.03) => {
    m.mat = tint(mat, 0.75 + 0.4 * hash(id + 1), 2 + 0.9 * hash(id + 2));
    const P = [], N = [], T = [], K = 16;
    for (let k = 0; k <= K; k++) { const [a, s] = path(k / K), [p, n] = b7Off(a, s, 0); P.push(p); N.push(n); }
    for (let k = 0; k <= K; k++) T.push(norm(sub(P[Math.min(K, k + 1)], P[Math.max(0, k - 1)])));
    slab(m, 6, K, (u, v, i, j) => {
      const hw = wid * (v < 0.22 ? 0.55 + 0.45 * v / 0.22 : Math.pow(1 - (v - 0.22) / 0.78, 1.15)), uu = 2 * u - 1;
      const n = N[j], side = norm(cross(n, T[j]));
      return [add(add(P[j], mul(side, uu * hw)), mul(n, off + lift * Math.pow(v, 2.4) + crease * (1 - Math.abs(uu)) * (0.5 + 0.5 * v) + 0.012 * Math.abs(uu) * v)), n];
    }, 0.012, { ao: (u, v) => 0.4 + 0.6 * smooth(v * 1.4), edge: 0 });
  };
  // the herringbone: rows from the tail forward, each side's blade rooted at the keel and swept out and forward; rear
  // blades sweep out wide toward the flanks, the front ones run long toward the nose. Each row's points lie over the next.
  const ROWS = 15;
  for (let r = 0; r < ROWS; r++) {
    const k = r / (ROWS - 1), sb = 0.06 + 0.82 * k;
    for (const sd of [-1, 1]) {
      const id = 1500 + r * 17 + (sd > 0 ? 5 : 0);
      const A = lerp(2.35, 0.85, k) * (0.94 + 0.12 * hash(id)), Ls = lerp(0.1, 0.26, k), wid = lerp(0.17, 0.25, k);
      blade(v => [sd * (0.07 + A * Math.pow(v, 0.9)), Math.min(1, sb + Ls * v)], wid, 0.014 + 0.005 * (r % 2), 0.11 + 0.06 * hash(id + 3), id, tint(MAT.scale, 0.85, 2), 0.05);
      // a shorter blade under it, lower on the flank, so the sides are covered too
      if (k < 0.9) blade(v => [sd * (1.0 + lerp(1.55, 0.9, k) * v), Math.min(1, sb + 0.03 + Ls * 0.7 * v)], wid * 0.8, 0.006, 0.09, id + 9, tint(MAT.scale, 0.75, 2), 0.035);
    }
  }
  // belly plates: a few long ones under the front, pointing to the nose
  for (const sd of [-1, 1]) for (let r = 0; r < 4; r++) blade(v => [sd * (2.1 + 0.35 * r - 0.5 * v), 0.42 + 0.12 * r + 0.18 * v], 0.08, 0.004, 0.02, 1700 + r * 3 + (sd > 0 ? 1 : 0), tint(MAT.scale, 0.8, 2), 0.02);
  // the keel: a tall narrow blade from the nose along the spine to the tail point
  const kp = [], KN = 40;
  for (let k = 0; k <= KN; k++) { const s = lerp(0.02, 1.0, k / KN), [p] = b7Off(0, s, 0.02); kp.push(p); }
  m.mat = tint(MAT.scale, 0.6, 2.4);
  tube(m, kp, t => 0.015 + 0.1 * Math.pow(Math.sin(Math.PI * clamp(t * 1.05, 0, 1)), 0.6), { seg: 10, up: Y, shape: [1, 0.3] });
  m.mat = MAT.redline; tube(m, kp.map(p => add(p, [0, 0.1, 0])), t => 0.003 + 0.003 * Math.sin(Math.PI * t), { seg: 6 });
  // the spine row: blade spines standing up along the keel from the head to the tail, small at the head, tallest mid-back,
  // small again toward the tail; thin from the front, broad from the side
  for (let k = 0; k < 15; k++) {
    const u = k / 14, sk = lerp(0.95, 0.06, u), [p, n] = b7Off(0, sk, 0.06), h = 0.07 + 0.62 * Math.pow(Math.sin(Math.PI * clamp(u * 1.05 - 0.02, 0, 1)), 1.6);
    m.mat = tint(MAT.scale, 0.8 + 0.25 * hash(k + 2000), 2.4);
    spike(m, p, norm(add(n, [0, 0.15, -0.45])), h, 0.035 + 0.06 * h, { up: [0, 0, 1], shape: [1, 0.28], bend: [0, 0, -0.25], seg: 8, n: 6, pow: 1.15 });
  }
  // the crown of jagged spines on the back, swept up and back, and short ones scattered between the blades
  for (const sd of [-1, 1]) for (let k = 0; k < 7; k++) {
    const id = 1800 + k * 7 + (sd > 0 ? 3 : 0), s = 0.1 + 0.42 * hash(id), a = sd * (0.3 + 1.05 * hash(id + 1));
    const [p, n] = b7Off(a, s, 0.03), L = 0.22 + 0.3 * hash(id + 2) * (1 - 0.4 * Math.abs(a) / 1.35);
    const dir = norm(add(add(n, [0, 0.7, -0.45]), [sd * 0.35 * (hash(id + 5) - 0.3), 0, 0]));
    m.mat = tint(MAT.scale, 0.5 + 0.3 * hash(id + 4), 2.4);
    spike(m, p, dir, L, 0.08 + 0.05 * hash(id + 6), { seg: 6, n: 4, shape: [1, 0.3], up: [1, 0, 0], bend: [sd * 0.15 * hash(id + 8), 0.05, -0.25], pow: 1.25 });
    if (k % 2 === 0) spike(m, add(p, [sd * 0.05, 0, -0.04]), norm(add(dir, [sd * 0.6, -0.1, 0.1])), L * 0.55, 0.05, { seg: 5, n: 3, shape: [1, 0.35], up: [1, 0, 0], pow: 1.25 });
  }
  for (let k = 0; k < 40; k++) {
    const a = (hash(k + 300) - 0.5) * 4.6, s = 0.08 + 0.85 * hash(k + 340), [p, n] = b7Off(a, s, 0.03);
    m.mat = tint(MAT.scale, 0.8 + 0.3 * hash(k + 9), 2.3);
    spike(m, p, norm(add(n, [0, 0.2, -0.3])), 0.05 + 0.08 * hash(k + 380), 0.012 + 0.006 * hash(k + 7), { seg: 5, n: 3 });
  }
  m.mat = tint(MAT.scale, 0.85, 2.4);
  spike(m, b7Pt(0, 0.995), norm([0, -0.9, 0.5]), 0.3, 0.05, { up: [0, 0, 1], shape: [1, 0.5], seg: 8, n: 5 });
  // ribs and hanging sinew under the belly, black cables along the flanks
  m.mat = MAT.dark;
  for (let k = 0; k < 6; k++) {
    const s = 0.22 + k * 0.09, pts = [];
    for (let q = 0; q <= 16; q++) pts.push(b7Off(2.6 + (TAU - 5.2) * q / 16, s, -0.01 + 0.015 * Math.sin(Math.PI * q / 16))[0]);
    tube(m, pts, 0.018, { seg: 7 });
  }
  for (let k = 0; k < 10; k++) {
    m.mat = k % 3 ? MAT.sinewD : MAT.sinew;
    const a1 = 2.6 + hash(k) * 1.08, s1 = 0.2 + hash(k + 50) * 0.6, a2 = a1 + (hash(k + 90) - 0.5) * 0.9, s2 = clamp(s1 + (hash(k + 130) - 0.5) * 0.25, 0.15, 0.9);
    const P = b7Off(a1, s1, -0.02)[0], Q = b7Off(a2, s2, -0.02)[0], sg = 0.04 + 0.14 * hash(k + 170);
    tube(m, quad(P, add(lerp3(P, Q, 0.5), [0, -1.2 * sg, 0]), Q, 10), 0.007 + 0.007 * hash(k + 210), { seg: 6 });
  }
  // the hip joints under the belly: an armoured mount comes down from the hull to a socket cup, and the leg's ball turns in it
  for (const L of LEGS) for (const sd of [-1, 1]) {
    const hp = [sd * L.hip[0], L.hip[1], L.hip[2]], dn = norm([sd * L.hn[0], L.hn[1], L.hn[2]]);
    const top = [sd * L.hip[0] * 0.7, L.hip[1] + 0.34, L.hip[2]], ax = norm(sub(hp, top));
    const t1 = norm(cross(ax, [0, 0, 1])), t2 = cross(ax, t1);
    m.mat = MAT.dark; tube(m, [top, sub(hp, mul(ax, 0.1))], t => 0.085 - 0.02 * t, { seg: 14 });
    m.mat = MAT.steel; for (const u of [0.35, 0.6]) torus(m, lerp3(top, hp, u), [t1, t2, ax], 0.075, 0.014, 16, 6);
    // a shield plate over the mount, in the same scale as the legs
    m.mat = tint(MAT.scale, 0.9, 2.4);
    slab(m, 8, 6, (u, v) => {
      const th = (u - 0.5) * 2.2 + (sd > 0 ? 0 : Math.PI), r = 0.115 + 0.02 * v, c = Math.cos(th), s = Math.sin(th);
      const p = add(lerp3(top, hp, 0.05 + 0.7 * v), add(mul(t1, r * c), mul(t2, r * s)));
      return [p, norm(add(mul(t1, c), mul(t2, s)))];
    }, 0.014, { ao: (u, v) => 0.5 + 0.5 * v });
    // the socket cup, open toward the leg, and the ball
    m.mat = MAT.dark; ellipsoid(m, sub(hp, mul(ax, 0.04)), [t1, t2, ax], [0.17, 0.17, 0.12], 16, 10);
    m.mat = MAT.steel; torus(m, add(hp, mul(ax, 0.02)), [t1, t2, ax], 0.16, 0.024, 20, 6);
    m.mat = MAT.dark; for (let k = 0; k < 6; k++) { const a = k * TAU / 6, q = add(add(hp, mul(ax, 0.03)), add(mul(t1, 0.16 * Math.cos(a)), mul(t2, 0.16 * Math.sin(a)))); ellipsoid(m, q, AX, [0.022, 0.022, 0.022], 6, 4); }
    m.mat = MAT.steel; ellipsoid(m, add(hp, mul(dn, 0.02)), AX, [0.115, 0.115, 0.115], 16, 10);
    m.mat = MAT.redline; torus(m, add(hp, mul(ax, 0.045)), [t1, t2, ax], 0.118, 0.01, 24, 5);
  }
  return m;
}

// the skull: a sculpted distance field, dark as old iron, a broad hollow mouth of long teeth, two red ring eyes on its left
function skullSDF(p) {
  let d = sdE(p, [0, 0.06, -0.03], [0.19, 0.2, 0.24]);
  d = smin(d, sdE(p, [0, -0.05, 0.08], [0.165, 0.12, 0.16]), 0.06);
  d = smin(d, sdE(p, [0, 0.045, 0.15], [0.17, 0.045, 0.07]), 0.03);
  for (const s of [-1, 1]) d = smin(d, sdE(p, [s * 0.13, -0.06, 0.1], [0.055, 0.035, 0.07]), 0.03);
  d = smin(d, sdE(p, [0, -0.13, 0.12], [0.135, 0.06, 0.105]), 0.04);
  d = smax(d, -sdE(p, [0, -0.2, 0.1], [0.105, 0.055, 0.1]), 0.015);                 // the palate, hollowed out
  for (const s of [-1, 1]) d = smax(d, -sdE(p, [s * 0.07, -0.005, 0.2], [0.05, 0.048, 0.07]), 0.012);
  d = smax(d, -sdE(p, [0, -0.085, 0.222], [0.022, 0.04, 0.06]), 0.008);
  for (const s of [-1, 1]) d = smax(d, -sdE(p, [s * 0.215, -0.03, 0.02], [0.05, 0.07, 0.09]), 0.03);
  d = smax(d, -sdE(p, [-0.09, 0.17, 0.04], [0.06, 0.03, 0.05]), 0.01);               // a broken patch on the crown
  return d + 0.006 * (vnoise(p[0] * 34, p[1] * 34, p[2] * 34) - 0.5) + 0.003 * (vnoise(p[0] * 90, p[1] * 90, p[2] * 90) - 0.5);
}
function buildSkull() {
  const m = new Mesh();
  m.scale = SK;
  m.mat = MAT.head;
  sdfMesh(m, skullSDF, [-0.24, -0.23, -0.3], [0.24, 0.29, 0.3], 0.0105);
  m.mat = MAT.hollow; ellipsoid(m, [0, -0.15, 0.08], AX, [0.1, 0.03, 0.1], 16, 8);   // the black hollow of the mouth
  for (let k = 0; k < 14; k++) {
    const f = (k + 0.5) / 14 * 2.7 - 1.35, root = [0.122 * Math.sin(f), -0.17, 0.115 + 0.095 * Math.cos(f)];
    const L = (0.08 + 0.11 * hash(k + 3)) * (1 - 0.22 * Math.abs(f)), dir = norm([0.2 * (hash(k + 9) - 0.5) + 0.1 * Math.sin(f), -1, 0.12 * Math.cos(f) + 0.1 * (hash(k + 11) - 0.5)]);
    m.mat = tint(MAT.tooth, 0.85 + 0.2 * hash(k + 20));
    spike(m, root, dir, L, 0.015 + 0.006 * hash(k + 30), { seg: 6, n: 4, shape: [1, 0.7], up: [Math.sin(f), 0, Math.cos(f)] });
  }
  // deep dark sockets and a black throat, so the glow reads as a face
  m.mat = MAT.hollow;
  for (const sd of [-1, 1]) ellipsoid(m, [sd * 0.07, -0.005, 0.175], AX, [0.042, 0.04, 0.05], 14, 8);
  ellipsoid(m, [0, -0.085, 0.2], AX, [0.016, 0.03, 0.03], 8, 6);
  // cables and sinew from behind the skull into the throat
  m.mat = MAT.cable;
  for (let k = 0; k < 5; k++) { const a = k * 1.3; tube(m, quad([0.12 * Math.sin(a), 0.12 * Math.cos(a) - 0.02, -0.2], [0.3 * Math.sin(a + 0.3), 0.3 * Math.cos(a + 0.3), -0.3], [0.34 * Math.sin(a + 0.5), 0.34 * Math.cos(a + 0.5), -0.25], 8), 0.018, { seg: 6 }); }
  return m;
}
// the face: five blade plates closed over the skull like a pointed bud, tips meeting at the snout. Each is hinged at its back
// edge and peels back when it screams or feeds, showing the glowing skull. Built in skull units, drawn SK times.
const axisRot = (k, g) => { const c = Math.cos(g), s = Math.sin(g), t = 1 - c, [x, y, z] = k; return [[t * x * x + c, t * x * y - s * z, t * x * z + s * y], [t * x * y + s * z, t * y * y + c, t * y * z - s * x], [t * x * z - s * y, t * y * z + s * x, t * z * z + c]]; };
const FACE_C = [0, 0.0, 0.03], FACE_PHI1 = 1.8, FACE_OPEN = 1.15;
const faceDir = (th, ph) => [Math.sin(ph) * Math.sin(th), Math.sin(ph) * Math.cos(th), Math.cos(ph)];
const faceR = v => 0.34 + 0.2 * v * v;
const FACE = [[0, 0.92], [1.28, 0.88], [-1.28, 0.88], [2.5, 0.78], [-2.5, 0.78]].map(([th, hw], k) => {
  const H = add(FACE_C, mul(faceDir(th, FACE_PHI1), faceR(0))), axis = [Math.cos(th), -Math.sin(th), 0];
  // open whichever way swings the tip out from the snout
  const tip = add(FACE_C, mul(faceDir(th, 0.15), faceR(1))), out = g => { const q = add(H, ap(axisRot(axis, g), sub(tip, H))); return Math.hypot(q[0], q[1]); };
  return { th, hw, H, axis, sign: out(0.8) > out(-0.8) ? 1 : -1, k };
});
function buildFacePlate(F) {
  const m = new Mesh(); m.scale = SK;
  const leaf = (th0, hw, base, lift, ridge, id, mat, v0 = 0, v1 = 1) => {
    m.mat = tint(mat, 0.82 + 0.3 * hash(id + 1), 2 + 0.9 * hash(id + 2));
    slab(m, 10, 9, (u, v) => {
      const vv = lerp(v0, v1, v), w = vv < 0.3 ? 0.85 + 0.15 * vv / 0.3 : 1 - Math.pow((vv - 0.3) / 0.7, 1.4), uu = 2 * u - 1, ph = lerp(FACE_PHI1, 0.06, vv);
      const d = faceDir(th0 + uu * w * hw / Math.max(0.35, Math.sin(ph)) * 0.8, ph), r = faceR(vv) + base + lift * vv * vv + ridge * Math.pow(1 - Math.abs(uu), 2) * (0.3 + vv);
      return [sub(add(FACE_C, mul(d, r)), F.H), d];
    }, 0.016, { ao: (u, v) => 0.45 + 0.55 * smooth(v * 1.3) });
  };
  leaf(F.th, F.hw, 0.0, 0.02, 0.025, 1200 + F.k * 7, tint(MAT.scale, 0.72, 2));
  // a narrower raised plate down its middle and a pair of short spikes, like the keel on its back
  leaf(F.th, F.hw * 0.42, 0.022, 0.03, 0.035, 1250 + F.k * 7, tint(MAT.scale, 0.9, 2), 0.05, 0.92);
  m.mat = tint(MAT.scale, 0.95, 2.4);
  for (const v of [0.3, 0.55]) { const d = faceDir(F.th, lerp(FACE_PHI1, 0.06, v)); spike(m, sub(add(FACE_C, mul(d, faceR(v) + 0.05)), F.H), norm(add(d, [0, 0, 0.9])), 0.07 - 0.02 * v, 0.014, { seg: 5, n: 3, bend: [0, 0, 0.3] }); }
  // the hinge
  m.mat = MAT.dark; tube(m, [mul(F.axis, -0.06), mul(F.axis, 0.06)], 0.018, { seg: 10, cap0: true, cap1: true });
  return m;
}
// the jaw: a broad jawbone, lower teeth, a red tongue and a beard of shards
function buildJaw() {
  const m = new Mesh();
  m.scale = SK;
  m.mat = MAT.head;
  const pts = [[-0.15, 0.01, -0.02]];
  for (let k = 0; k <= 16; k++) { const f = -1.5 + 3.0 * k / 16; pts.push([0.14 * Math.sin(f), -0.075 - 0.025 * Math.cos(f), 0.03 + 0.17 * Math.cos(f)]); }
  pts.push([0.15, 0.01, -0.02]);
  tube(m, pts, t => 0.018 + 0.013 * Math.sin(Math.PI * t), { seg: 10 });
  m.mat = MAT.maw; ellipsoid(m, [0, -0.075, 0.08], AX, [0.09, 0.02, 0.1], 14, 8);
  for (let k = 0; k < 11; k++) {
    const f = -1.1 + 2.2 * k / 10, root = [0.125 * Math.sin(f), -0.065, 0.03 + 0.155 * Math.cos(f)];
    m.mat = tint(MAT.tooth, 0.85 + 0.2 * hash(k + 44));
    spike(m, root, norm([0.1 * (hash(k + 40) - 0.5), 1, 0.1]), 0.04 + 0.055 * hash(k + 41), 0.012, { seg: 5, n: 3 });
  }
  for (let k = 0; k < 18; k++) {
    const f = -1.15 + 2.3 * hash(k + 60), row = k % 3, root = [0.12 * Math.sin(f) * (1 - 0.15 * row), -0.09 - 0.01 * row, 0.03 + 0.15 * Math.cos(f) - 0.03 * row];
    const Ls = (0.13 + 0.26 * hash(k + 61)) * (1 - 0.35 * Math.abs(f)), dir = norm([0.3 * Math.sin(f) + 0.3 * (hash(k + 62) - 0.5), -1, 0.25 + 0.3 * (hash(k + 63) - 0.5)]);
    const side = norm(cross(dir, Y)), kink = (hash(k + 64) - 0.5) * 0.06;
    m.mat = tint(MAT.head, 0.85 + 0.3 * hash(k + 65));
    tube(m, [root, add(add(root, mul(dir, Ls * 0.35)), mul(side, kink)), add(add(root, mul(dir, Ls * 0.7)), mul(side, -kink * 0.6)), add(root, mul(dir, Ls))],
      t => (0.014 + 0.01 * hash(k + 66)) * Math.pow(1 - t, 0.9) + 0.001, { seg: 6, shape: [1, 0.6] });
  }
  return m;
}
// a leg bone along +x from its joint: gear, beam, toothed sleeves, a piston on its +y side, thorns underneath.
// A shin bows out a little and runs down into a long barbed point.
// a leg segment sheathed in shingled blade plates: leaf-shaped scales in staggered rings, each tip lying over the base of
// the ring below so they shed toward the point. kind: 'thigh' (hip to knee, knee cap and piston), 'shin' (cog knee down
// to the ankle), 'tars' (cog ankle down to a long point)
// bends a straight leg (built along +x) along the lower-leg path of length S, bowing out toward +y, then turns it so
// the line from its root to its point lies along +x again
function curveLeg(m, S) {
  const A = legArc(S), c0 = Math.cos(A.psi), s0 = Math.sin(A.psi);
  for (const ch of m.chunks) for (let k = 0; k < ch.v.length; k += VS) {
    const v = ch.v, x = v[k], y = v[k + 1];
    // the centre line point and turn at x (straight on past either end), then the offset along its outward normal
    const u = clamp(x / A.ds, 0, A.N), i0 = Math.min(A.N - 1, Math.floor(u)), f = u - i0, ex = x - u * A.ds;
    const t = lerp(A.T[i0], A.T[i0 + 1], f), ct = Math.cos(t), st = Math.sin(t);
    const cx = lerp(A.P[i0][0], A.P[i0 + 1][0], f) + ct * ex, cy = lerp(A.P[i0][1], A.P[i0 + 1][1], f) - st * ex;
    const px = cx + y * st, py = cy + y * ct;
    const nx = v[k + 3] * ct + v[k + 4] * st, ny = -v[k + 3] * st + v[k + 4] * ct;
    v[k] = px * c0 - py * s0; v[k + 1] = px * s0 + py * c0;
    v[k + 3] = nx * c0 - ny * s0; v[k + 4] = nx * s0 + ny * c0;
  }
  return m;
}
function buildLeg(Lb, rA, rB, gearR, seed, kind) {
  const m = new Mesh(), tars = kind === 'tars', thigh = kind === 'thigh', tipAt = tars ? Lb * 0.62 : Lb;
  const R = x => !tars || x < tipAt ? lerp(rA, rB, clamp(x / tipAt, 0, 1)) : rB * Math.pow(Math.max(0, 1 - (x - tipAt) / (Lb - tipAt)), 0.8) + 0.003;
  if (!thigh) { m.mat = MAT.dark; gear(m, [0, 0, 0], [0, 0, 1], [1, 0, 0], gearR, 0.1, 14); m.mat = MAT.steel; torus(m, [0, 0, 0], [[1, 0, 0], [0, 1, 0], [0, 0, 1]], gearR * 0.45, 0.025, 14, 6); }
  m.mat = MAT.dark;
  const core = []; for (let k = 0; k <= 36; k++) core.push([0.02 + (Lb - 0.02) * k / 36, 0, 0]);
  tube(m, core, t => R(0.02 + (Lb - 0.02) * t) * 0.92, { seg: 12, cap0: true });
  // the shingles
  const x0 = thigh ? 0.14 : gearR * 0.7, x1 = tars ? Lb - 0.2 : Lb - 0.02, Ls = thigh ? 0.44 : 0.4, step = Ls * 0.58;
  const rows = Math.max(2, Math.round((x1 - x0 - Ls) / step) + 1), N = 4;
  for (let r = 0; r < rows; r++) {
    const xs = x0 + r * (x1 - x0 - Ls) / Math.max(1, rows - 1);
    for (let q = 0; q < N; q++) {
      const id = seed * 31 + r * 7 + q, th0 = (q + (r % 2) * 0.5) * TAU / N + 0.25 * (hash(id) - 0.5) * 0.4, w = TAU / N * 1.32;
      const L = Ls * (0.9 + 0.2 * hash(id + 3)), lift = 0.024 + 0.022 * hash(id + 5);
      m.mat = tint(MAT.scale, 0.8 + 0.35 * hash(id + 1), 2 + 0.9 * hash(id + 2));
      slab(m, 8, 7, (u, v) => {
        const hw = v < 0.32 ? 0.82 + 0.18 * v / 0.32 : 1 - Math.pow((v - 0.32) / 0.68, 1.5), uu = 2 * u - 1;
        const th = th0 + uu * hw * w / 2, x = xs + v * L, rr = R(Math.min(x, Lb - 0.01)) + 0.012 + lift * v * v + 0.016 * Math.pow(1 - Math.abs(uu), 2) * (0.4 + v);
        const c = Math.cos(th), s = Math.sin(th);
        return [[x, rr * c, rr * s], norm([-0.25, c, s])];
      }, 0.011, { ao: (u, v) => 0.4 + 0.6 * smooth(v * 1.3) });
    }
  }
  // red strands running along the seams, black cable down the inside
  m.mat = MAT.redline;
  for (const th of [thigh ? 2.1 : -2.1]) {
    const pts = []; for (let k = 0; k <= 24; k++) { const x = x0 + (x1 - x0 + (tars ? 0.1 : 0)) * k / 24, rr = R(x) + 0.05 + 0.01 * Math.sin(k * 1.3); pts.push([x, rr * Math.cos(th), rr * Math.sin(th)]); }
    tube(m, pts, 0.0065, { seg: 6 });
  }
  if (thigh) {
    // a domed knee cap over the cog, and a piston with a chrome rod under the thigh
    m.mat = tint(MAT.scale, 1.05, 2.6);
    slab(m, 12, 8, (u, v) => {
      const th = (u - 0.5) * 2.6, ph = v * 1.45, rr = gearR + 0.06;
      return [[Lb + 0.02 + Math.sin(ph) * rr * 0.75 - 0.18 * (1 - v), Math.cos(th) * (rr * 0.6 + rr * 0.55 * Math.cos(ph)) + 0.02, Math.sin(th) * (rr * 0.75)], norm([Math.sin(ph), Math.cos(th) * Math.cos(ph), Math.sin(th) * 0.6])];
    }, 0.018, { ao: () => 0.85 });
    for (let k = 0; k < 4; k++) { m.mat = MAT.dark; box(m, [Lb - 0.12 - k * 0.07, gearR * 0.62 + 0.06, 0], AX, [0.035, 0.05, 0.16 - k * 0.02]); }
    const py = x => -R(x) - 0.07;
    m.mat = MAT.dark; tube(m, [[Lb * 0.14, py(Lb * 0.14), 0], [Lb * 0.55, py(Lb * 0.55), 0]], 0.04, { seg: 12, cap0: true, cap1: true });
    m.mat = MAT.chrome; tube(m, [[Lb * 0.53, py(Lb * 0.55), 0], [Lb * 0.9, py(Lb * 0.9), 0]], 0.02, { seg: 10, cap1: true });
    m.mat = MAT.dark; box(m, [Lb * 0.9, py(Lb * 0.9) / 2, 0], AX, [0.05, -py(Lb * 0.9), 0.07]);
    m.mat = MAT.cable; tube(m, quad([0.12, 0.03, rA + 0.04], [Lb * 0.5, -0.12, rA + 0.1], [Lb - 0.1, 0, R(Lb) + 0.05], 12), 0.017, { seg: 7 });
  } else {
    m.mat = MAT.cable; tube(m, quad([gearR, -0.02, -rA - 0.03], [Lb * 0.4, -0.12, -rA - 0.07], [Lb * 0.75, -0.03, -R(Lb * 0.75) - 0.04], 12), 0.014, { seg: 7 });
  }
  if (tars) {
    // the bare point, with an ankle ring and a couple of barbs hooked back up
    m.mat = MAT.dark; torus(m, [x1 + 0.02, 0, 0], [[0, 1, 0], [0, 0, 1], [1, 0, 0]], R(x1 + 0.02) + 0.02, 0.014, 18, 6);
    m.mat = tint(MAT.steel, 0.9, 2.4); tube(m, [[x1 - 0.02, 0, 0], [Lb, 0, 0]], t => R(lerp(x1 - 0.02, Lb, t)) * 1.02 + 0.002, { seg: 10 });
    for (let k = 0; k < 3; k++) {
      const x = x1 + (Lb - x1) * (0.15 + 0.22 * k), r = R(x), th = k * 2.4 + 0.5, c = Math.cos(th), s = Math.sin(th);
      spike(m, [x, r * c * 0.8, r * s * 0.8], norm([-0.9, c, s]), 0.07 * (1 - 0.15 * k), 0.015, { seg: 5, n: 4, bend: [-0.6, 0, 0] });
    }
  }
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
  body: buildBody(), skull: buildSkull(), jaw: buildJaw(),
  // two joints: the upper leg, a cog knee, then one long shingled section that closes to the point
  thighF: buildLeg(LEGS[0].a, 0.1, 0.085, 0.15, 11, 'thigh'), lowF: curveLeg(buildLeg(LEGS[0].b + LEGS[0].c, 0.085, 0.055, 0.13, 23, 'tars'), LEGS[0].b + LEGS[0].c),
  thighR: buildLeg(LEGS[1].a, 0.1, 0.085, 0.15, 37, 'thigh'), lowR: curveLeg(buildLeg(LEGS[1].b + LEGS[1].c, 0.085, 0.055, 0.13, 51, 'tars'), LEGS[1].b + LEGS[1].c),
  face0: buildFacePlate(FACE[0]), face1: buildFacePlate(FACE[1]), face2: buildFacePlate(FACE[2]), face3: buildFacePlate(FACE[3]), face4: buildFacePlate(FACE[4]),
  player: buildPlayer(), floor: buildFloor(),
};
