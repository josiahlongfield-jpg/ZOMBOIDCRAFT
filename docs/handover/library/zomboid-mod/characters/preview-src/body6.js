// round 6 body, from Josiah's armoured render: a rounded shell (in blocks) of the same leaf-blade plates as the legs,
// shingled toward the front, a raised keel of plates down the spine, a tall spike, a spiky fringe, and a pointed hood
// that runs forward over the skull. s runs rear (0) to the front of the shell (1), a round from the spine (0) to the keel (±PI)
const B6 = { z0: -0.95, z1: 0.62, hz: 1.24, c: 0.06 };
const b6e = s => s < 0.5 ? Math.sqrt(Math.max(0, 1 - ((0.5 - s) / 0.5) ** 2)) : 1 - 0.35 * ((s - 0.5) / 0.5) ** 2;
function b6Pt(a, s) {
  const e = b6e(clamp(s, 0, 1)), W = 0.5 * Math.pow(e, 0.9), T = 0.5 * e, Bt = 0.38 * e, z = lerp(B6.z0, B6.z1, s), sa = Math.sin(a), ca = Math.cos(a);
  if (ca >= 0) return [W * Math.sign(sa) * Math.pow(Math.abs(sa), 0.85), B6.c + T * Math.pow(ca, 0.85), z];
  return [W * Math.sign(sa) * Math.pow(Math.abs(sa), 0.8), B6.c - Bt * Math.pow(-ca, 0.8), z];
}
// the hood: t runs from the front of the shell (0) to the point (1), narrowing as it goes and lifting clear of the skull
function b6Hood(a, t) {
  const p0 = b6Pt(a, 1), k = Math.pow(1 - t, 0.85), yc = B6.c + 0.12 * t;
  return [p0[0] * k, yc + (p0[1] - B6.c) * k, lerp(B6.z1, B6.hz, t) - 0.12 * (1 - Math.cos(a)) * t];
}
const b6Off = (f, a, s, off) => {
  const p = f(a, s), e = 1e-3;
  let n = cross(sub(f(a, s + e), f(a, s - e)), sub(f(a + e, s), f(a - e, s)));
  n = len(n) < 1e-12 ? [0, 1, 0] : norm(n);
  if (dot(n, sub(p, [0, B6.c, Math.min(p[2], 0.4)])) < 0) n = mul(n, -1);
  return [add(p, mul(n, off)), n];
};
function buildBody() {
  const m = new Mesh();
  // the red inside, ribbed underneath
  m.mat = MAT.core;
  grid(m, 56, 40, (u, v) => { const a = u * TAU - Math.PI, ca = Math.cos(a); return b6Off(b6Pt, a, lerp(0.01, 1, v), -0.04 + (ca < -0.2 ? 0.015 * Math.sin(v * 70) * (-ca - 0.2) : 0))[0]; }, { wrap: true, flip: true, ao: () => 0.55 });
  // a leaf plate laid on surface f over (a0 ± w/2) from u0 to u1, tip toward u1
  const leaf = (f, th0, w, u0, u1, base, lift, id, mat, ridge = 0.016) => {
    m.mat = tint(mat, 0.78 + 0.38 * hash(id + 1), 2 + 0.9 * hash(id + 2));
    slab(m, 8, 7, (u, v) => {
      const hw = v < 0.32 ? 0.82 + 0.18 * v / 0.32 : 1 - Math.pow((v - 0.32) / 0.68, 1.5), uu = 2 * u - 1;
      return b6Off(f, th0 + uu * hw * w / 2, lerp(u0, u1, v), base + lift * v * v + ridge * Math.pow(1 - Math.abs(uu), 2) * (0.4 + v));
    }, 0.013, { ao: (u, v) => 0.4 + 0.6 * smooth(v * 1.3) });
  };
  // the shell: staggered rings of plates from the rear forward, each tip over the base of the ring in front
  const ROWS = 9, amax = 2.55;
  for (let r = 0; r < ROWS; r++) {
    const s0 = -0.02 + r * 0.112, s1 = s0 + 0.21, N = r === 0 ? 7 : 10, w = 2 * amax / N * 1.3;
    for (let q = 0; q < N; q++) {
      const id = 700 + r * 19 + q, th0 = -amax + (q + 0.5 + (r % 2 ? 0.5 : 0)) * 2 * amax / N;
      if (Math.abs(th0) > amax + 0.05 || Math.abs(th0) < 0.12) continue;
      leaf(b6Pt, th0, w, s0, Math.min(1.02, s1), 0.012 + 0.006 * (q % 2), 0.035 + 0.03 * hash(id + 5), id, MAT.scale);
    }
  }
  // the hood: fanned plates converging on the point
  for (let r = 0; r < 4; r++) {
    const t0 = -0.08 + r * 0.24, t1 = t0 + 0.34, N = 6 - r, am = 2.2 - r * 0.25, w = 2 * am / N * 1.3;
    for (let q = 0; q < N; q++) {
      const id = 880 + r * 13 + q, th0 = -am + (q + 0.5) * 2 * am / N;
      leaf(b6Hood, th0, w, Math.max(0, t0), Math.min(1, t1), 0.03 + 0.01 * r, 0.04, id, MAT.scale);
    }
  }
  // the keel: a raised strip of narrow plates down the spine and over the hood to the point
  for (let k = 0; k < 9; k++) leaf(b6Pt, 0, 0.42, -0.02 + k * 0.118, 0.17 + k * 0.118, 0.05 + 0.004 * k, 0.05, 950 + k, tint(MAT.scale, 0.9, 2), 0.05);
  for (let k = 0; k < 4; k++) leaf(b6Hood, 0, 0.5 - 0.06 * k, k * 0.24 - 0.04, Math.min(1, k * 0.24 + 0.3), 0.08, 0.05, 970 + k, tint(MAT.scale, 0.9, 2), 0.05);
  // the tall spike off the top, and a fringe of short spikes all over, raked forward like the plates
  m.mat = tint(MAT.scale, 0.95, 2.4);
  { const [p, n] = b6Off(b6Pt, 0, 0.55, 0.04); spike(m, p, norm(add(n, [0, 0.4, -0.12])), 0.9, 0.055, { up: [0, 0, 1], shape: [1, 0.6], bend: [0, 0, 0.12], seg: 8, n: 6, pow: 1.15 }); }
  for (let k = 0; k < 90; k++) {
    const hood = k >= 72, a = (hash(k + 300) - 0.5) * (hood ? 3.6 : 5.0), s = hood ? hash(k + 340) * 0.8 : 0.03 + 0.97 * hash(k + 340);
    const [p, n] = b6Off(hood ? b6Hood : b6Pt, a, s, 0.035);
    m.mat = tint(MAT.scale, 0.8 + 0.35 * hash(k + 9), 2.3);
    spike(m, p, norm(add(n, [0, 0, 0.7])), 0.06 + 0.11 * hash(k + 380), 0.016 + 0.008 * hash(k + 7), { seg: 5, n: 3, bend: [0, 0, 0.4] });
  }
  // ribs and hanging sinew under the belly, black cables along the flanks
  m.mat = MAT.dark;
  for (let k = 0; k < 7; k++) {
    const s = 0.18 + k * 0.1, pts = [];
    for (let q = 0; q <= 16; q++) pts.push(b6Off(b6Pt, 2.5 + (TAU - 5.0) * q / 16, s, -0.02 + 0.02 * Math.sin(Math.PI * q / 16))[0]);
    tube(m, pts, 0.022, { seg: 7 });
  }
  for (let k = 0; k < 12; k++) {
    m.mat = k % 3 ? MAT.sinewD : MAT.sinew;
    const a1 = 2.5 + hash(k) * 1.28, s1 = 0.15 + hash(k + 50) * 0.75, a2 = a1 + (hash(k + 90) - 0.5) * 1.0, s2 = clamp(s1 + (hash(k + 130) - 0.5) * 0.3, 0.1, 0.95);
    const P = b6Off(b6Pt, a1, s1, -0.03)[0], Q = b6Off(b6Pt, a2, s2, -0.03)[0], sg = 0.04 + 0.18 * hash(k + 170);
    tube(m, quad(P, add(lerp3(P, Q, 0.5), [0, -1.2 * sg, 0]), Q, 10), 0.008 + 0.008 * hash(k + 210), { seg: 6 });
  }
  m.mat = MAT.cable;
  for (const sd of [-1, 1]) for (let k = 0; k < 2; k++) {
    const P = b6Off(b6Pt, sd * (2.1 + 0.1 * k), 0.92 - 0.05 * k, 0.0)[0], Q = b6Off(b6Pt, sd * (2.3 + 0.1 * hash(k)), 0.35 - 0.08 * k, -0.01)[0];
    tube(m, quad(P, add(lerp3(P, Q, 0.5), [sd * 0.05, -0.15 - 0.06 * k, 0]), Q, 14), 0.02, { seg: 8 });
  }
  // sockets where the legs join
  m.mat = MAT.dark;
  for (const L of LEGS) for (const sd of [-1, 1]) {
    const hp = [sd * L.hip[0], L.hip[1], L.hip[2]], out = norm([sd * L.hn[0], L.hn[1], L.hn[2]]);
    tube(m, [sub(hp, mul(out, 0.13)), add(hp, mul(out, -0.01))], 0.12, { seg: 18, cap1: true });
  }
  // the neck under the hood: a sinew throat and black cables down to the skull
  m.xf = COLLAR;
  m.mat = MAT.core;
  tube(m, [[0, -0.01, -0.2], [0, -0.02, 0.02], [0, -0.03, 0.2], [0, -0.03, 0.34]], (t, th) => lerp(0.3, 0.17, t) * (1 + 0.05 * Math.sin(th * 9 + t * 21)), { seg: 26, cap1: true, ao: t => 0.35 + 0.3 * t });
  m.mat = MAT.cable;
  for (let k = 0; k < 7; k++) {
    const a = k * 0.9 + 0.4, r0 = 0.3, r1 = 0.17;
    tube(m, cubic([r0 * Math.sin(a), r0 * Math.cos(a), -0.08], [r0 * 0.9 * Math.sin(a + 0.4), r0 * 0.9 * Math.cos(a + 0.4), 0.1], [r1 * 1.4 * Math.sin(a + 0.6), r1 * 1.4 * Math.cos(a + 0.6), 0.25], [r1 * Math.sin(a + 0.9), r1 * Math.cos(a + 0.9), 0.36], 12), 0.014, { seg: 7 });
  }
  m.xf = null;
  return m;
}

