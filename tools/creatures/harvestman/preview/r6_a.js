
<script>
(() => {
'use strict';
// ---------------------------------------------------------------- vectors and rotations
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const len = a => Math.hypot(a[0], a[1], a[2]);
const norm = a => { const l = len(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const lerp = (a, b, t) => a + (b - a) * t;
const lerp3 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const smooth = x => { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); };
const TAU = Math.PI * 2, Y = [0, 1, 0];
// mesh detail: 1 in the preview; the game export asks for less (#q=0.5)
const QUAL = Math.min(1, +(new URLSearchParams(location.hash.slice(1)).get('q') || 1));
// 3x3 rotations, row-major; R = Ry(yaw) * Rx(pitch) * Rz(roll)
const m3mul = (a, b) => { const r = []; for (let i = 0; i < 3; i++) { r[i] = []; for (let j = 0; j < 3; j++) r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j]; } return r; };
const rx = a => { const c = Math.cos(a), s = Math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]; };
const ry = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; };
const rz = a => { const c = Math.cos(a), s = Math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; };
const rot = (yaw, pitch = 0, roll = 0) => m3mul(m3mul(ry(yaw), rx(pitch)), rz(roll));
const ap = (R, v) => [R[0][0] * v[0] + R[0][1] * v[1] + R[0][2] * v[2], R[1][0] * v[0] + R[1][1] * v[1] + R[1][2] * v[2], R[2][0] * v[0] + R[2][1] * v[1] + R[2][2] * v[2]];
const cols = (a, b, c) => [[a[0], b[0], c[0]], [a[1], b[1], c[1]], [a[2], b[2], c[2]]];
const I3 = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
// a frame: origin + rotation
const frame = (o, R) => ({ o, R });
const at = (f, p) => add(f.o, ap(f.R, p));
const child = (f, p, R) => frame(at(f, p), m3mul(f.R, R));
const hash = n => { const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
const h3 = (i, j, k) => hash(i * 157.31 + j * 113.97 + k * 271.13);
function vnoise(x, y, z) {
  const i = Math.floor(x), j = Math.floor(y), k = Math.floor(z), fx = x - i, fy = y - j, fz = z - k;
  const u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy), w = fz * fz * (3 - 2 * fz);
  return lerp(lerp(lerp(h3(i, j, k), h3(i + 1, j, k), u), lerp(h3(i, j + 1, k), h3(i + 1, j + 1, k), u), v),
              lerp(lerp(h3(i, j, k + 1), h3(i + 1, j, k + 1), u), lerp(h3(i, j + 1, k + 1), h3(i + 1, j + 1, k + 1), u), v), w);
}
const quad = (a, c, b, n) => { const r = []; for (let k = 0; k <= n; k++) { const t = k / n, s = 1 - t; r.push(add(add(mul(a, s * s), mul(c, 2 * s * t)), mul(b, t * t))); } return r; };
const cubic = (a, b, c, d, n) => { const r = []; for (let k = 0; k <= n; k++) { const t = k / n, s = 1 - t; r.push(add(add(mul(a, s * s * s), mul(b, 3 * s * s * t)), add(mul(c, 3 * s * t * t), mul(d, t * t * t)))); } return r; };

// ---------------------------------------------------------------- materials: colour, metal, roughness, pattern (its fraction varies it), glow
const MAT = {
  plate:  { c: [0.12, 0.12, 0.12], metal: 0.85, rough: 0.5, pat: 1, glow: 0 },
  band:   { c: [0.12, 0.12, 0.12], metal: 0.88, rough: 0.42, pat: 1, glow: 0 },
  scale:  { c: [0.11, 0.11, 0.11], metal: 0.9, rough: 0.36, pat: 2, glow: 0 },
  redline:{ c: [0.62, 0.05, 0.04], metal: 0.00, rough: 0.30, pat: 3, glow: 0.07 },
  hollow: { c: [0.02, 0.006, 0.004], metal: 0.0, rough: 0.6, pat: 0, glow: 0 },
  steel:  { c: [0.15, 0.15, 0.15], metal: 0.92, rough: 0.33, pat: 2, glow: 0 },
  dark:   { c: [0.1, 0.1, 0.1], metal: 0.88, rough: 0.42, pat: 2, glow: 0 },
  chrome: { c: [0.2, 0.2, 0.2], metal: 1.00, rough: 0.12, pat: 0, glow: 0 },
  sinew:  { c: [0.58, 0.06, 0.05], metal: 0.00, rough: 0.30, pat: 3, glow: 0.10 },
  sinewD: { c: [0.26, 0.035, 0.03], metal: 0.00, rough: 0.36, pat: 3, glow: 0.05 },
  core:   { c: [0.15, 0.025, 0.02], metal: 0.00, rough: 0.4, pat: 3, glow: 0.035 },
  cable:  { c: [0.055, 0.055, 0.055], metal: 0.00, rough: 0.36, pat: 5, glow: 0 },
  head:   { c: [0.9, 0.4, 0.1], metal: 0.0, rough: 0.5, pat: 4, glow: 1.15 },
  tooth:  { c: [0.9, 0.5, 0.15], metal: 0.0, rough: 0.4, pat: 4, glow: 0.9 },
  eye:    { c: [1.00, 0.10, 0.07], metal: 0.00, rough: 0.30, pat: 9, glow: 9 },
  lens:   { c: [0.08, 0.015, 0.012], metal: 0.30, rough: 0.08, pat: 0, glow: 0.3 },
  maw:    { c: [0.55, 0.06, 0.05], metal: 0.00, rough: 0.28, pat: 3, glow: 0.22 },
  skin:   { c: [0.56, 0.57, 0.52], metal: 0.00, rough: 0.62, pat: 8, glow: 0 },
  shirt:  { c: [0.33, 0.34, 0.37], metal: 0.00, rough: 0.85, pat: 6.6, glow: 0 },
  jeans:  { c: [0.22, 0.27, 0.38], metal: 0.00, rough: 0.85, pat: 6.1, glow: 0 },
  shoe:   { c: [0.13, 0.11, 0.10], metal: 0.00, rough: 0.55, pat: 0, glow: 0 },
  mouth:  { c: [0.10, 0.02, 0.02], metal: 0.00, rough: 0.40, pat: 0, glow: 0 },
  floor:  { c: [0.17, 0.17, 0.18], metal: 0.00, rough: 0.60, pat: 7, glow: 0 },
  pShirt: { c: [0.30, 0.33, 0.27], metal: 0.00, rough: 0.90, pat: 6.05, glow: 0 },
  pPants: { c: [0.2, 0.22, 0.32], metal: 0.00, rough: 0.90, pat: 6.05, glow: 0 },
  pSkin:  { c: [0.78, 0.58, 0.45], metal: 0.00, rough: 0.80, pat: 0, glow: 0 },
  pHair:  { c: [0.22, 0.15, 0.09], metal: 0.00, rough: 0.80, pat: 0, glow: 0 },
};
const tint = (m, k, pat) => ({ ...m, c: mul(m.c, k), pat: pat == null ? m.pat : pat });

// ---------------------------------------------------------------- meshes: smooth surfaces with a material on every vertex
// vertex: position, normal, colour, metal, rough, pattern, glow, ao, edge wear
const VS = 15;
class Mesh {
  constructor() { this.chunks = []; this.mat = MAT.plate; this.xf = null; this.scale = 1; this.start(); }
  start() { this.v = []; this.ix = []; this.chunks.push({ v: this.v, ix: this.ix }); }
  room(n) { if (this.v.length / VS + n > 65000) this.start(); return this.v.length / VS; }
  vert(p, n, ao = 1, edge = 0) {
    if (this.scale !== 1) p = mul(p, this.scale);
    if (this.xf) { p = at(this.xf, p); n = ap(this.xf.R, n); }
    const m = this.mat;
    this.v.push(p[0], p[1], p[2], n[0], n[1], n[2], m.c[0], m.c[1], m.c[2], m.metal, m.rough, m.pat, m.glow, ao, edge);
  }
}
// a grid surface f(u, v, i, j) -> point; normals from the grid unless o.n gives them; wrap joins u round, wrapV joins v round
function grid(m, nu, nv, f, o = {}) {
  const wu = !!o.wrap, wv = !!o.wrapV, cu = wu ? nu : nu + 1, cv = wv ? nv : nv + 1, P = new Array(cu * cv);
  for (let j = 0; j < cv; j++) for (let i = 0; i < cu; i++) P[j * cu + i] = f(i / nu, j / nv, i, j);
  const id = (i, j) => (wv ? ((j % cv) + cv) % cv : clamp(j, 0, nv)) * cu + (wu ? ((i % cu) + cu) % cu : clamp(i, 0, nu));
  const base = m.room(cu * cv);
  for (let j = 0; j < cv; j++) for (let i = 0; i < cu; i++) {
    let n;
    if (o.n) n = o.n(i / nu, j / nv, i, j);
    else {
      n = cross(sub(P[id(i + 1, j)], P[id(i - 1, j)]), sub(P[id(i, j + 1)], P[id(i, j - 1)]));
      if (dot(n, n) < 1e-18) { const jj = j === 0 ? 1 : j - 1; n = cross(sub(P[id(i + 1, jj)], P[id(i - 1, jj)]), sub(P[id(i, jj + 1)], P[id(i, jj - 1)])); }
      n = norm(n); if (o.flip) n = mul(n, -1);
    }
    m.vert(P[j * cu + i], n, o.ao ? o.ao(i / nu, j / nv, i, j) : 1, o.edge ? o.edge(i / nu, j / nv, i, j) : 0);
  }
  const vi = (i, j) => base + id(i, j);
  for (let j = 0; j < nv; j++) for (let i = 0; i < nu; i++) {
    const a = vi(i, j), b = vi(i + 1, j), c = vi(i + 1, j + 1), d = vi(i, j + 1);
    if (o.flip) m.ix.push(a, c, b, a, d, c); else m.ix.push(a, b, c, a, c, d);
  }
}
// a thick sheet: f(u, v, i, j) -> [point, outward normal]; outer face, inner face (o.matIn), and the four edge walls
function slab(m, nu, nv, f, t, o = {}) {
  if (QUAL < 1) { nu = Math.max(2, Math.round(nu * QUAL)); nv = Math.max(1, Math.round(nv * QUAL)); }
  const wrap = !!o.wrap, cu = wrap ? nu : nu + 1, P = [], Nn = [];
  for (let j = 0; j <= nv; j++) for (let i = 0; i < cu; i++) { const r = f(i / nu, j / nv, i, j); P.push(r[0]); Nn.push(r[1]); }
  const id = (i, j) => j * cu + (wrap ? ((i % cu) + cu) % cu : clamp(i, 0, nu));
  const ic = nu >> 1, jc = nv >> 1;
  const inv = dot(cross(sub(P[id(ic + 1, jc)], P[id(ic - 1, jc)]), sub(P[id(ic, Math.min(nv, jc + 1))], P[id(ic, Math.max(0, jc - 1))])), Nn[id(ic, jc)]) < 0;
  const off = (i, j, s) => add(P[id(i, j)], mul(Nn[id(i, j)], s * t / 2));
  const rim = (i, j) => o.edge === 0 ? 0 : (!wrap && (i === 0 || i === nu)) || j === 0 || j === nv ? 1 : 0;
  const mOut = m.mat;
  grid(m, nu, nv, (u, v, i, j) => off(i, j, 1), { wrap, flip: inv, ao: o.ao, edge: (u, v, i, j) => rim(i, j) });
  m.mat = o.matIn || mOut;
  grid(m, nu, nv, (u, v, i, j) => off(i, j, -1), { wrap, flip: !inv, ao: o.aoIn || (() => 0.4) });
  m.mat = mOut;
  const wall = (n, pick, flip, w) => grid(m, n, 1, (u, v, i, k) => { const q = pick(i); return off(q[0], q[1], 1 - 2 * k); }, { wrap: w, flip, edge: () => (o.edge === 0 ? 0.25 : 1) });
  wall(nu, i => [i, nv], inv, wrap);
  wall(nu, i => [i, 0], !inv, wrap);
  if (!wrap) { wall(nv, j => [0, j], inv, false); wall(nv, j => [nu, j], !inv, false); }
}
// a tube along a path: radius r(t along, angle), an optional squashed section [across N, across B]; o.up keeps N toward it
function tube(m, pts, rad, o = {}) {
  if (QUAL < 1 && pts.length > 3) { const k = Math.max(1, Math.round(1 / QUAL)), q = pts.filter((p, i) => i % k === 0); if (q[q.length - 1] !== pts[pts.length - 1]) q.push(pts[pts.length - 1]); pts = q; }
  const K = pts.length, seg = Math.max(4, Math.round((o.seg || 10) * Math.min(1, QUAL * 1.3))), sh = o.shape || [1, 1];
  const T = pts.map((p, k) => norm(sub(pts[Math.min(K - 1, k + 1)], pts[Math.max(0, k - 1)])));
  const N = [], B = [], S = [0];
  for (let k = 0; k < K; k++) {
    if (k) S.push(S[k - 1] + len(sub(pts[k], pts[k - 1])));
    const ref = o.up || (k ? N[k - 1] : (Math.abs(T[0][1]) < 0.95 ? Y : [1, 0, 0]));
    let n = sub(ref, mul(T[k], dot(ref, T[k])));
    if (dot(n, n) < 1e-10) n = k ? N[k - 1] : norm(cross(T[k], Math.abs(T[k][0]) < 0.9 ? [1, 0, 0] : [0, 0, 1]));
    n = norm(n); N.push(n); B.push(cross(T[k], n));
  }
  const L = S[K - 1] || 1, R = typeof rad === 'function' ? rad : () => rad;
  const ring = (k, th) => { const r = Math.max(1e-4, R(S[k] / L, th)); return add(pts[k], add(mul(N[k], Math.cos(th) * r * sh[0]), mul(B[k], Math.sin(th) * r * sh[1]))); };
  grid(m, seg, K - 1, (u, v, i, j) => ring(j, u * TAU), { wrap: true, ao: o.ao ? (u, v, i, j) => o.ao(S[j] / L) : null });
  const cap = (k, s) => {
    const base = m.room(seg + 1); m.vert(pts[k], mul(T[k], s));
    for (let i = 0; i < seg; i++) m.vert(ring(k, i / seg * TAU), mul(T[k], s));
    for (let i = 0; i < seg; i++) { const a = base + 1 + i, b = base + 1 + (i + 1) % seg; if (s > 0) m.ix.push(base, a, b); else m.ix.push(base, b, a); }
  };
  if (o.cap0) cap(0, -1);
  if (o.cap1) cap(K - 1, 1);
}
// box faces: +x -x +y -y +z -z, corners TL TR BR BL seen from outside
const FACES = [
  [[1, 0, 0], [[.5, .5, .5], [.5, .5, -.5], [.5, -.5, -.5], [.5, -.5, .5]]],
  [[-1, 0, 0], [[-.5, .5, -.5], [-.5, .5, .5], [-.5, -.5, .5], [-.5, -.5, -.5]]],
  [[0, 1, 0], [[-.5, .5, -.5], [.5, .5, -.5], [.5, .5, .5], [-.5, .5, .5]]],
  [[0, -1, 0], [[-.5, -.5, .5], [.5, -.5, .5], [.5, -.5, -.5], [-.5, -.5, -.5]]],
  [[0, 0, 1], [[-.5, .5, .5], [.5, .5, .5], [.5, -.5, .5], [-.5, -.5, .5]]],
  [[0, 0, -1], [[.5, .5, -.5], [-.5, .5, -.5], [-.5, -.5, -.5], [.5, -.5, -.5]]],
];
function box(m, c, ax, s, edge = 0.5) {
  for (const [nl, cs] of FACES) {
    const n = add(add(mul(ax[0], nl[0]), mul(ax[1], nl[1])), mul(ax[2], nl[2])), base = m.room(4);
    for (const q of cs) m.vert(add(c, add(add(mul(ax[0], q[0] * s[0]), mul(ax[1], q[1] * s[1])), mul(ax[2], q[2] * s[2]))), n, 1, edge);
    m.ix.push(base, base + 3, base + 2, base, base + 2, base + 1);
  }
}
const AX = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
function ellipsoid(m, c, ax, r, nu = 16, nv = 10) {
  if (QUAL < 1) { nu = Math.max(6, Math.round(nu * QUAL)); nv = Math.max(4, Math.round(nv * QUAL)); }
  const loc = (u, v) => { const th = u * TAU, ph = v * Math.PI; return [Math.sin(ph) * Math.cos(th), Math.cos(ph), Math.sin(ph) * Math.sin(th)]; };
  const W = l => add(add(mul(ax[0], l[0]), mul(ax[1], l[1])), mul(ax[2], l[2]));
  grid(m, nu, nv, (u, v) => { const l = loc(u, v); return add(c, W([l[0] * r[0], l[1] * r[1], l[2] * r[2]])); },
    { wrap: true, n: (u, v) => { const l = loc(u, v); return norm(W([l[0] / r[0], l[1] / r[1], l[2] / r[2]])); } });
}
// a ring round ax[2]
function torus(m, c, ax, R, r, nu = 24, nv = 8) {
  if (QUAL < 1) { nu = Math.max(8, Math.round(nu * QUAL)); nv = Math.max(4, Math.round(nv * QUAL)); }
  grid(m, nv, nu, (u, v) => { const a = v * TAU, b = u * TAU, d = add(mul(ax[0], Math.cos(a)), mul(ax[1], Math.sin(a))); return add(c, add(mul(d, R + r * Math.cos(b)), mul(ax[2], r * Math.sin(b)))); }, { wrap: true, wrapV: true, flip: true });
}
// a toothed gear on axle ax: teeth, hub, boss and bolt heads
function gear(m, c, ax, ref, r, th, teeth) {
  const u = norm(sub(ref, mul(ax, dot(ref, ax)))), w = cross(ax, u), ring = [];
  for (let k = 0; k < teeth; k++) for (const [f, rr] of [[0, 0.84], [0.17, 1], [0.5, 1], [0.67, 0.84]]) ring.push([(k + f) / teeth * TAU, rr * r]);
  const P = (a, rr, h) => add(c, add(add(mul(u, Math.cos(a) * rr), mul(w, Math.sin(a) * rr)), mul(ax, h)));
  const n = ring.length;
  for (const s of [1, -1]) {
    const base = m.room(n + 1), nn = mul(ax, s);
    m.vert(P(0, 0, s * th / 2), nn, 0.8, 0);
    for (const [a, rr] of ring) m.vert(P(a, rr, s * th / 2), nn, 1, 0.7);
    for (let i = 0; i < n; i++) { const a = base + 1 + i, b = base + 1 + (i + 1) % n; if (s > 0) m.ix.push(base, a, b); else m.ix.push(base, b, a); }
  }
  for (let i = 0; i < n; i++) {
    const [a0, r0] = ring[i], [a1, r1] = ring[(i + 1) % n], t0 = P(a0, r0, th / 2), t1 = P(a1, r1, th / 2), b0 = P(a0, r0, -th / 2), b1 = P(a1, r1, -th / 2);
    const nn = norm(cross(sub(t1, t0), ax)), base = m.room(4);
    m.vert(t0, nn, 1, 1); m.vert(t1, nn, 1, 1); m.vert(b1, nn, 1, 1); m.vert(b0, nn, 1, 1);
    m.ix.push(base, base + 2, base + 1, base, base + 3, base + 2);
  }
  tube(m, [add(c, mul(ax, -th * 0.95)), add(c, mul(ax, th * 0.95))], r * 0.42, { seg: 16, cap0: true, cap1: true });
  tube(m, [add(c, mul(ax, -th * 1.3)), add(c, mul(ax, th * 1.3))], r * 0.2, { seg: 12, cap0: true, cap1: true });
  for (const s of [1, -1]) for (let k = 0; k < 6; k++) {
    const a = k / 6 * TAU + 0.3, q = P(a, r * 0.64, s * th * 0.55);
    box(m, q, [u, w, ax].map(v => v), [r * 0.13, r * 0.13, th * 0.25], 0.8);
  }
}
// a curved, tapering horn from base along dir, bending toward o.bend
function spike(m, base, dir, h, r, o = {}) {
  const tip = add(add(base, mul(dir, h)), mul(o.bend || [0, 0, 0], h * 0.3)), mid = add(base, mul(dir, h * 0.55));
  tube(m, quad(base, mid, tip, o.n || 6), t => r * Math.pow(1 - t, o.pow || 1.1) + 0.0012, { seg: o.seg || 7, up: o.up, shape: o.shape });
}
// a surface from a distance field (surface nets): smooth normals and a little occlusion from the field
function sdfMesh(m, sdf, lo, hi, res) {
  const nx = Math.ceil((hi[0] - lo[0]) / res), ny = Math.ceil((hi[1] - lo[1]) / res), nz = Math.ceil((hi[2] - lo[2]) / res);
  const sx = nx + 1, sy = ny + 1, val = new Float32Array(sx * sy * (nz + 1));
  const P = (i, j, k) => [lo[0] + i * res, lo[1] + j * res, lo[2] + k * res];
  for (let k = 0; k <= nz; k++) for (let j = 0; j <= ny; j++) for (let i = 0; i <= nx; i++) val[(k * sy + j) * sx + i] = sdf(P(i, j, k));
  const V = (i, j, k) => val[(k * sy + j) * sx + i];
  const C = (i, j, k) => (k * ny + j) * nx + i, vid = new Int32Array(nx * ny * nz).fill(-1), verts = [];
  const E = [[0, 0, 0, 1, 0, 0], [0, 1, 0, 1, 1, 0], [0, 0, 1, 1, 0, 1], [0, 1, 1, 1, 1, 1], [0, 0, 0, 0, 1, 0], [1, 0, 0, 1, 1, 0], [0, 0, 1, 0, 1, 1], [1, 0, 1, 1, 1, 1], [0, 0, 0, 0, 0, 1], [1, 0, 0, 1, 0, 1], [0, 1, 0, 0, 1, 1], [1, 1, 0, 1, 1, 1]];
  for (let k = 0; k < nz; k++) for (let j = 0; j < ny; j++) for (let i = 0; i < nx; i++) {
    let acc = [0, 0, 0], cnt = 0;
    for (const e of E) {
      const a = V(i + e[0], j + e[1], k + e[2]), b = V(i + e[3], j + e[4], k + e[5]);
      if ((a < 0) !== (b < 0)) { acc = add(acc, lerp3(P(i + e[0], j + e[1], k + e[2]), P(i + e[3], j + e[4], k + e[5]), a / (a - b))); cnt++; }
    }
    if (cnt) { vid[C(i, j, k)] = verts.length; verts.push(mul(acc, 1 / cnt)); }
  }
  const base = m.room(verts.length), g = res * 0.5;
  for (const p of verts) {
    const n = norm([sdf(add(p, [g, 0, 0])) - sdf(sub(p, [g, 0, 0])), sdf(add(p, [0, g, 0])) - sdf(sub(p, [0, g, 0])), sdf(add(p, [0, 0, g])) - sdf(sub(p, [0, 0, g]))]);
    let occ = 0; for (const [d, w] of [[0.015, 0.5], [0.035, 0.3], [0.07, 0.2]]) occ += w * Math.max(0, d - sdf(add(p, mul(n, d)))) / d;
    m.vert(p, n, clamp(1 - occ * 1.4, 0.12, 1), 0);
  }
  const q4 = (a, b, c, d, flip) => { if (a < 0 || b < 0 || c < 0 || d < 0) return; a += base; b += base; c += base; d += base; if (flip) m.ix.push(a, d, c, a, c, b); else m.ix.push(a, b, c, a, c, d); };
  for (let k = 1; k < nz; k++) for (let j = 1; j < ny; j++) for (let i = 0; i < nx; i++) { const a = V(i, j, k), b = V(i + 1, j, k); if ((a < 0) !== (b < 0)) q4(vid[C(i, j - 1, k - 1)], vid[C(i, j, k - 1)], vid[C(i, j, k)], vid[C(i, j - 1, k)], !(a < 0)); }
  for (let k = 1; k < nz; k++) for (let j = 0; j < ny; j++) for (let i = 1; i < nx; i++) { const a = V(i, j, k), b = V(i, j + 1, k); if ((a < 0) !== (b < 0)) q4(vid[C(i - 1, j, k - 1)], vid[C(i - 1, j, k)], vid[C(i, j, k)], vid[C(i, j, k - 1)], !(a < 0)); }
  for (let k = 0; k < nz; k++) for (let j = 1; j < ny; j++) for (let i = 1; i < nx; i++) { const a = V(i, j, k), b = V(i, j, k + 1); if ((a < 0) !== (b < 0)) q4(vid[C(i - 1, j - 1, k)], vid[C(i, j - 1, k)], vid[C(i, j, k)], vid[C(i - 1, j, k)], !(a < 0)); }
}
const sdE = (p, c, r) => { const q = [(p[0] - c[0]) / r[0], (p[1] - c[1]) / r[1], (p[2] - c[2]) / r[2]]; return (Math.hypot(q[0], q[1], q[2]) - 1) * Math.min(r[0], r[1], r[2]); };
const smin = (a, b, k) => { const h = clamp(0.5 + 0.5 * (b - a) / k, 0, 1); return lerp(b, a, h) - k * h * (1 - h); };
const smax = (a, b, k) => -smin(-a, -b, k);
