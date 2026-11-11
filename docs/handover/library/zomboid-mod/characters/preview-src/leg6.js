// a leg segment sheathed in shingled blade plates: leaf-shaped scales in staggered rings, each tip lying over the base of
// the ring below so they shed toward the point. kind: 'thigh' (hip to knee, knee cap and piston), 'shin' (cog knee down
// to the ankle), 'tars' (cog ankle down to a long point)
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
      m.mat = tint(MAT.plate, 0.78 + 0.4 * hash(id + 1), 2 + 0.9 * hash(id + 2));
      slab(m, 8, 7, (u, v) => {
        const hw = v < 0.32 ? 0.82 + 0.18 * v / 0.32 : 1 - Math.pow((v - 0.32) / 0.68, 1.5), uu = 2 * u - 1;
        const th = th0 + uu * hw * w / 2, x = xs + v * L, rr = R(Math.min(x, Lb - 0.01)) + 0.012 + lift * v * v + 0.016 * Math.pow(1 - Math.abs(uu), 2) * (0.4 + v);
        const c = Math.cos(th), s = Math.sin(th);
        return [[x, rr * c, rr * s], norm([-0.25, c, s])];
      }, 0.011, { ao: (u, v) => 0.4 + 0.6 * smooth(v * 1.3) });
    }
  }
  // red strands running along the seams, black cable down the inside
  m.mat = MAT.sinew;
  for (const th of [1.95, -1.95]) {
    const pts = []; for (let k = 0; k <= 24; k++) { const x = x0 + (x1 - x0 + (tars ? 0.1 : 0)) * k / 24, rr = R(x) + 0.05 + 0.01 * Math.sin(k * 1.3); pts.push([x, rr * Math.cos(th), rr * Math.sin(th)]); }
    tube(m, pts, 0.0085, { seg: 6 });
  }
  if (thigh) {
    // a domed knee cap over the cog, and a piston with a chrome rod under the thigh
    m.mat = tint(MAT.plate, 1.05, 2.6);
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
