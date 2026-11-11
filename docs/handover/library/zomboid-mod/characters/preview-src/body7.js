// round 6d body, from Josiah's armoured render: a dart, low at the nose and rising to a tail that ends in a long spine.
// From the front it reads as a downward arrowhead: long creased blade plates sweep out and forward from a tall centre keel
// in a herringbone, jagged spines crown the back, and a beard of shards hangs under the point. In blocks.
// s runs from the tail (0) to the nose (1); a round the body from the keel (0) to the belly (±PI), positive to its left.
const B7 = { zt: -1.55, zn: 0.95 };
const b7env = s => s < 0.5 ? Math.pow(Math.max(0, s / 0.5), 0.7) : Math.pow(Math.max(0, 1 - (s - 0.5) / 0.5), 0.85);
const b7c = s => 0.05 + 0.5 * Math.pow(1 - s, 2.2) - 0.45 * smooth((s - 0.6) / 0.4);   // centre line: up to the tail, down to the nose
function b7Pt(a, s) {
  s = clamp(s, 0, 1);
  const e = b7env(s), W = 0.5 * e, T = 0.22 * e, Bt = 0.25 * e, c = b7c(s), z = lerp(B7.zt, B7.zn, s), sa = Math.sin(a), ca = Math.cos(a);
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
      return [add(add(P[j], mul(side, uu * hw)), mul(n, off + lift * v * v + crease * (1 - Math.abs(uu)) * (0.5 + 0.5 * v) + 0.012 * Math.abs(uu) * v)), n];
    }, 0.012, { ao: (u, v) => 0.4 + 0.6 * smooth(v * 1.4) });
  };
  // the herringbone: rows from the tail forward, each side's blade rooted at the keel and swept out and forward; rear
  // blades sweep out wide toward the flanks, the front ones run long toward the nose. Each row's points lie over the next.
  const ROWS = 10;
  for (let r = 0; r < ROWS; r++) {
    const k = r / (ROWS - 1), sb = 0.1 + 0.76 * k;
    for (const sd of [-1, 1]) {
      const id = 1500 + r * 17 + (sd > 0 ? 5 : 0);
      const A = lerp(2.15, 0.75, k) * (0.94 + 0.12 * hash(id)), Ls = lerp(0.1, 0.24, k), wid = lerp(0.085, 0.11, k);
      blade(v => [sd * (0.07 + A * Math.pow(v, 0.9)), Math.min(1, sb + Ls * v)], wid, 0.012 + 0.004 * (r % 2), 0.035 + 0.03 * hash(id + 3), id, MAT.scale);
      // a shorter blade under it, lower on the flank, so the sides are covered too
      if (r % 2 === 0 && k < 0.85) blade(v => [sd * (0.95 + lerp(1.6, 1.0, k) * v), Math.min(1, sb + 0.04 + Ls * 0.7 * v)], wid * 0.85, 0.006, 0.03, id + 9, MAT.scale, 0.022);
    }
  }
  // belly plates: a few long ones under the front, pointing to the nose
  for (const sd of [-1, 1]) for (let r = 0; r < 4; r++) blade(v => [sd * (2.1 + 0.35 * r - 0.5 * v), 0.42 + 0.12 * r + 0.18 * v], 0.08, 0.004, 0.02, 1700 + r * 3 + (sd > 0 ? 1 : 0), tint(MAT.scale, 0.8, 2), 0.02);
  // the keel: a tall narrow blade from the nose along the spine, rising into the tail spine
  const kp = [], KN = 40;
  for (let k = 0; k <= KN; k++) { const s = lerp(0.02, 1.0, k / KN), [p] = b7Off(0, s, 0.02); kp.push(p); }
  m.mat = tint(MAT.scale, 0.85, 2.4);
  tube(m, kp, t => 0.012 + 0.07 * Math.pow(Math.sin(Math.PI * clamp(t * 1.05, 0, 1)), 0.6), { seg: 10, up: Y, shape: [1, 0.22] });
  m.mat = MAT.redline; tube(m, kp.map(p => add(p, [0, 0.005, 0])), t => 0.004 + 0.006 * Math.sin(Math.PI * t), { seg: 6 });
  // the tail spine: long and thin, swept up and back like a mast
  m.mat = tint(MAT.scale, 0.9, 2.4);
  spike(m, b7Pt(0, 0.01), norm([0, 0.8, -0.6]), 1.05, 0.06, { up: [0, 0, 1], shape: [1, 0.55], bend: [0, 0.2, 0.1], seg: 8, n: 8, pow: 1.05 });
  // the crown of jagged spines on the back, swept up and back, and short ones scattered between the blades
  for (const sd of [-1, 1]) for (let k = 0; k < 9; k++) {
    const id = 1800 + k * 7 + (sd > 0 ? 3 : 0), s = 0.12 + 0.36 * hash(id), a = sd * (0.35 + 1.0 * hash(id + 1));
    const [p, n] = b7Off(a, s, 0.02), L = 0.16 + 0.28 * hash(id + 2) * (1 - 0.5 * Math.abs(a) / 1.35);
    m.mat = tint(MAT.scale, 0.75 + 0.35 * hash(id + 4), 2.4);
    spike(m, p, norm(add(add(n, [0, 0.55, -0.55]), [sd * 0.25 * hash(id + 5), 0, 0])), L, 0.03 + 0.02 * hash(id + 6), { seg: 6, n: 4, shape: [1, 0.5], up: [0, 0, 1], bend: [sd * 0.1, 0.1, -0.2] });
    if (k % 3 === 0) spike(m, p, norm(add(n, [sd * 0.5, 0.3, -0.3])), L * 0.6, 0.02, { seg: 5, n: 3 });
  }
  for (let k = 0; k < 40; k++) {
    const a = (hash(k + 300) - 0.5) * 4.6, s = 0.08 + 0.85 * hash(k + 340), [p, n] = b7Off(a, s, 0.03);
    m.mat = tint(MAT.scale, 0.8 + 0.3 * hash(k + 9), 2.3);
    spike(m, p, norm(add(n, [0, 0.2, -0.3])), 0.05 + 0.08 * hash(k + 380), 0.012 + 0.006 * hash(k + 7), { seg: 5, n: 3 });
  }
  // the beard: shards hanging under the point
  for (let k = 0; k < 14; k++) {
    const id = 1900 + k, a = Math.PI + (hash(id) - 0.5) * 2.4, s = 0.86 + 0.13 * hash(id + 1), [p, n] = b7Off(a, s, 0.0);
    m.mat = tint(MAT.scale, 0.7 + 0.35 * hash(id + 2), 2.4);
    spike(m, p, norm(add([0, -1, 0.35], mul(n, 0.4))), 0.1 + 0.18 * hash(id + 3), 0.018 + 0.01 * hash(id + 4), { seg: 5, n: 3, shape: [1, 0.5], up: [0, 0, 1] });
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
  // sockets where the legs join
  m.mat = MAT.dark;
  for (const L of LEGS) for (const sd of [-1, 1]) {
    const hp = [sd * L.hip[0], L.hip[1], L.hip[2]], out = norm([sd * L.hn[0], L.hn[1], L.hn[2]]);
    tube(m, [sub(hp, mul(out, 0.16)), add(hp, mul(out, -0.01))], 0.12, { seg: 18, cap1: true });
  }
  return m;
}

