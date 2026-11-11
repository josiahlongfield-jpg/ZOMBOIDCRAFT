// Reload engine (browser + node): same maths as engine.py. Matrices are row-major arrays (3x3: 9, 4x4: 16).
(function (G) {
const D2R = Math.PI / 180;
const v3 = {
  add: (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]], sub: (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]],
  mul: (a, s) => [a[0] * s, a[1] * s, a[2] * s], dot: (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2],
  cross: (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]],
  len: a => Math.hypot(a[0], a[1], a[2]), norm: a => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; },
  lerp: (a, b, u) => [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u],
};
const I3 = () => [1, 0, 0, 0, 1, 0, 0, 0, 1];
function m3mul(a, b) { const o = new Array(9); for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]; return o; }
const m3T = a => [a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]];
const m3v = (a, v) => [a[0] * v[0] + a[1] * v[1] + a[2] * v[2], a[3] * v[0] + a[4] * v[1] + a[5] * v[2], a[6] * v[0] + a[7] * v[1] + a[8] * v[2]];
const m3s = (a, s) => a.map(x => x * s);
const col = (a, j) => [a[j], a[3 + j], a[6 + j]];
const fromCols = (x, y, z) => [x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2]];
function m4(p, R) { return [R[0], R[1], R[2], p[0], R[3], R[4], R[5], p[1], R[6], R[7], R[8], p[2], 0, 0, 0, 1]; }
function m4mul(a, b) { const o = new Array(16); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) o[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j] + a[i * 4 + 3] * b[12 + j]; return o; }
const m4R = M => [M[0], M[1], M[2], M[4], M[5], M[6], M[8], M[9], M[10]];
const m4t = M => [M[3], M[7], M[11]];
const m4pt = (M, v) => [M[0] * v[0] + M[1] * v[1] + M[2] * v[2] + M[3], M[4] * v[0] + M[5] * v[1] + M[6] * v[2] + M[7], M[8] * v[0] + M[9] * v[1] + M[10] * v[2] + M[11]];
function m4inv(m) {
  const inv = new Array(16);
  inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
  inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
  inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
  inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
  inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
  inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
  inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
  inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
  inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
  inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
  inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
  inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
  inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
  inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
  inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
  inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
  const det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
  return inv.map(x => x / det);
}
const rx = a => { a *= D2R; const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, c, -s, 0, s, c]; };
const ry = a => { a *= D2R; const c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 1, 0, -s, 0, c]; };
const rz = a => { a *= D2R; const c = Math.cos(a), s = Math.sin(a); return [c, -s, 0, s, c, 0, 0, 0, 1]; };
const eul = r => { r = r || [0, 0, 0]; return m3mul(m3mul(ry(r[1]), rx(r[0])), rz(r[2])); };
function qmat(q) { const [x, y, z, w] = q; return [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]; }
function r2q(R) {
  const t = R[0] + R[4] + R[8]; let s;
  if (t > 0) { s = Math.sqrt(t + 1) * 2; return [(R[7] - R[5]) / s, (R[2] - R[6]) / s, (R[3] - R[1]) / s, s / 4]; }
  if (R[0] >= R[4] && R[0] >= R[8]) { s = Math.sqrt(1 + R[0] - R[4] - R[8]) * 2; return [s / 4, (R[1] + R[3]) / s, (R[2] + R[6]) / s, (R[7] - R[5]) / s]; }
  if (R[4] >= R[8]) { s = Math.sqrt(1 + R[4] - R[0] - R[8]) * 2; return [(R[1] + R[3]) / s, s / 4, (R[5] + R[7]) / s, (R[2] - R[6]) / s]; }
  s = Math.sqrt(1 + R[8] - R[0] - R[4]) * 2; return [(R[2] + R[6]) / s, (R[5] + R[7]) / s, s / 4, (R[3] - R[1]) / s];
}
function slerp(Ra, Rb, u) {
  const a = r2q(Ra); let b = r2q(Rb); let d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
  if (d < 0) { b = b.map(x => -x); d = -d; }
  d = Math.min(1, d); let q;
  if (d > 0.9995) q = a.map((x, i) => x + u * (b[i] - x));
  else { const th = Math.acos(d), sa = Math.sin((1 - u) * th), sb = Math.sin(u * th), st = Math.sin(th); q = a.map((x, i) => (sa * x + sb * b[i]) / st); }
  const l = Math.hypot(...q); return qmat(q.map(x => x / l));
}
function rot_to(a, b) {
  a = v3.norm(a); b = v3.norm(b); const v = v3.cross(a, b), c = v3.dot(a, b);
  if (c < -0.99999) {
    let p = v3.cross(a, [1, 0, 0]); if (v3.len(p) < 1e-6) p = v3.cross(a, [0, 1, 0]); p = v3.norm(p);
    return [2 * p[0] * p[0] - 1, 2 * p[0] * p[1], 2 * p[0] * p[2], 2 * p[1] * p[0], 2 * p[1] * p[1] - 1, 2 * p[1] * p[2], 2 * p[2] * p[0], 2 * p[2] * p[1], 2 * p[2] * p[2] - 1];
  }
  const vx = [0, -v[2], v[1], v[2], 0, -v[0], -v[1], v[0], 0], vx2 = m3mul(vx, vx), k = 1 / (1 + c);
  return I3().map((x, i) => x + vx[i] + vx2[i] * k);
}
function axis_rot(ax, deg) {
  ax = v3.norm(ax); const a = deg * D2R, K = [0, -ax[2], ax[1], ax[2], 0, -ax[0], -ax[1], ax[0], 0], K2 = m3mul(K, K), s = Math.sin(a), c = 1 - Math.cos(a);
  return I3().map((x, i) => x + s * K[i] + c * K2[i]);
}
function basis(fwd, palm) {
  const x = v3.norm(fwd); let z = v3.mul(palm, -1); z = v3.norm(v3.sub(z, v3.mul(x, v3.dot(z, x)))); const y = v3.cross(z, x);
  return fromCols(x, y, z);
}
const ease = u => { u = Math.min(1, Math.max(0, u)); return u * u * (3 - 2 * u); };
function track(keys, t) {
  if (!keys || !keys.length) return [{}, {}, 0];
  if (t <= keys[0][0]) return [keys[0][1], keys[0][1], 0];
  for (let i = 0; i + 1 < keys.length; i++) { const [ta, a] = keys[i], [tb, b] = keys[i + 1]; if (t <= tb) return [a, b, ease((t - ta) / Math.max(1e-6, tb - ta))]; }
  const l = keys[keys.length - 1][1]; return [l, l, 0];
}
const lerpv = (a, b, u, k, d) => v3.lerp(a[k] || d, b[k] || d, u);
// key with per-view overrides: first person = fp; third person = tp; third person aimed = tp then ta
function resolve(k, view) {
  const o = Object.assign({}, k);
  if (view === 'fp') Object.assign(o, k.fp || {});
  else { Object.assign(o, k.tp || {}); if (view === 'ta') Object.assign(o, k.ta || {}); }
  return o;
}

function hasFK(o) {
  if (o == null) return false; if (typeof o === 'number') return Math.abs(o) > 1e-6;
  if (Array.isArray(o)) return o.some(hasFK); return Object.values(o).some(hasFK);
}
function lerpFK(a, b, u) {
  if (!hasFK(a) && !hasFK(b)) return null;
  const one = (x, y) => {
    if (typeof x === 'number' || typeof y === 'number') return (1 - u) * (+x || 0) + u * (+y || 0);
    if (Array.isArray(x) || Array.isArray(y)) { const n = Math.max((x || []).length, (y || []).length); return Array.from({ length: n }, (_, i) => (1 - u) * ((x || [])[i] || 0) + u * ((y || [])[i] || 0)); }
    const o = {}; for (const k of new Set([...Object.keys(x || {}), ...Object.keys(y || {})])) o[k] = one((x || {})[k], (y || {})[k]); return o;
  };
  return one(a || {}, b || {});
}
function addFK(a, b, w) {
  if (!hasFK(b)) return a; if (!hasFK(a)) return lerpFK(null, b, w);
  const one = (x, y) => {
    if (typeof x === 'number' || typeof y === 'number') return (+x || 0) + w * (+y || 0);
    if (Array.isArray(x) || Array.isArray(y)) { const n = Math.max((x || []).length, (y || []).length); return Array.from({ length: n }, (_, i) => ((x || [])[i] || 0) + w * ((y || [])[i] || 0)); }
    const o = {}; for (const k of new Set([...Object.keys(x || {}), ...Object.keys(y || {})])) o[k] = one((x || {})[k], (y || {})[k]); return o;
  };
  return one(a, b);
}
// A hand's keys may hold 'mid' keys (the editor puts one wherever the other hand has a key): they don't bend the
// movement between the real keys, they only add his changes on top, fading in from the key before and out by the next.
function handTrack(keys, t) {
  keys = keys && keys.length ? keys : [[0, { k: 'rest' }]];
  const real = keys.filter(k => (k[1].k || 'rest') !== 'mid');
  const [a, b, u] = track(real.length ? real : [[0, { k: 'rest' }]], t), mids = [];
  keys.forEach((k, i) => {
    if (k[1].k !== 'mid') return; const tm = k[0], tp = i > 0 ? keys[i - 1][0] : tm, tn = i + 1 < keys.length ? keys[i + 1][0] : tm; let w = 0;
    if (Math.abs(t - tm) < 1e-9) w = 1; else if (t < tm && t >= tp && tm > tp) w = ease((t - tp) / (tm - tp)); else if (t > tm && t <= tn && tn > tm) w = ease((tn - t) / (tn - tm));
    if (w > 1e-6) mids.push([w, k[1]]);
  });
  return [a, b, u, mids];
}
const midPlain = m => !m.dpx && !m.r && !m.curl && !m.flat && !m.sw && !m.sho && !hasFK(m.fk);
// the hand's target at time t (gun at R, tt): [hand matrix, curl, elbow swing, shoulder offset, flat, joint turns]
function handAt(rig, spec, view, s, t, R, tt) {
  const [a, b, u, mids] = handTrack(spec[s], t);
  const [Ha, ca, sa, oa, fa, ka] = rig.hand_key(s, a, R, tt), [Hb, cb, sb, ob, fb, kb] = rig.hand_key(s, b, R, tt);
  const ue = spec.solver === 'natural' ? u * u * (3 - 2 * u) : u;   // fingers ease between keys (no snaps)
  let Rh = slerp(m4R(Ha), m4R(Hb), u), wr = v3.lerp(m4t(Ha), m4t(Hb), u);
  let curl = (1 - ue) * ca + ue * cb, sw = (1 - u) * sa + u * sb, sho = v3.lerp(oa, ob, u), flat = (1 - ue) * fa + ue * fb, fk = lerpFK(ka, kb, ue);
  for (const [w, m0] of mids) {
    const m = resolve(m0, view);
    if (m.dpx || m.r) {
      const Gm = rig.G(R, tt); let palm = v3.add(wr, m3v(Rh, rig.fist(s)));
      if (m.dpx) palm = m4pt(Gm, v3.add(m4pt(m4inv(Gm), palm), v3.mul(m.dpx, w)));
      if (m.r) Rh = m3mul(m3mul(m3mul(R, eul(v3.mul(m.r, w))), m3T(R)), Rh);
      wr = v3.sub(palm, m3v(Rh, rig.fist(s)));
    }
    curl += w * (m.curl || 0); sw += w * (m.sw || 0); flat += w * (m.flat || 0);
    if (m.sho) sho = v3.add(sho, v3.mul(m3v(m4R(rig.Pi), m.sho), w));
    if (hasFK(m.fk)) fk = addFK(fk, m.fk, w);
  }
  const plainK = k => { const r = resolve(k, view); return (r.k || 'rest') === 'rest' && !r.dpx && !r.d && !r.r && !r.curl && !r.fwd && !r.flat && !hasFK(r.fk); };
  const rest = plainK(a) && (u < 1e-9 || plainK(b)) && mids.every(([, m]) => midPlain(resolve(m, view)));
  return { h: [m4(wr, Rh), curl, sw, sho, Math.max(0, Math.min(1, flat)), fk], rest };
}
class Rig {
  constructor(S, R, view) {
    this.S = S; this.view = view; this.scale = R.scale; this.Rg0 = R.Rg0; this.tg0 = R.tg0; this.grip = R.grip; this.place = R.place;
    this.Pi = m4inv(R.place); this.bones = R.bones; this.clipm = R.clipm;
    this.pre = view === 'fp' ? (s, p) => `${s}_${p}` : (s, p) => `Bip01_${s}_${p}`;
    this.H0 = {}; this.palm0 = {};
    for (const s of 'RL') { this.H0[s] = this.bones[this.pre(s, 'Hand')]; this.palm0[s] = this.palm(this.H0[s], s); }
    this.G0 = this.G(this.Rg0, this.tg0); this.G0i = m4inv(this.G0);
  }
  fist(s) { return this.S.FIST[s === 'R' ? 0 : 1]; }
  palm(H, s) { return v3.add(m4t(H), m3v(m4R(H), this.fist(s))); }
  G(R, t) { // T(t) R*scale T(-0.5) S(1/16)
    const Rs = m3s(R, this.scale / 16), o = m3v(m3s(R, this.scale), [-0.5, -0.5, -0.5]);
    return m4(v3.add(t, o), Rs);
  }
  gun_key(k0) {
    const k = resolve(k0, this.view); const gw = m4pt(this.G0, this.grip); let R, gw2;
    if (k.abs) {
      const A = k.abs; const z = v3.norm(v3.mul(A.fwd, -1)); let y = A.up.slice(); y = v3.norm(v3.sub(y, v3.mul(z, v3.dot(y, z))));
      R = m3mul(m3mul(m4R(this.Pi), fromCols(v3.cross(y, z), y, z)), eul(k.r));
      gw2 = v3.add(m4pt(this.Pi, A.grip), m3v(R, k.d || [0, 0, 0]));
    } else { R = m3mul(this.Rg0, eul(k.r)); gw2 = v3.add(gw, m3v(this.Rg0, k.d || [0, 0, 0])); }
    const t = v3.sub(gw2, m4pt(this.G(R, [0, 0, 0]), this.grip));
    return [R, t, gw2];
  }
  gun_at(a, b, u) {
    const [Ra, , ga] = this.gun_key(a), [Rb, , gb] = this.gun_key(b);
    const R = slerp(Ra, Rb, u), g = v3.lerp(ga, gb, u);
    return [R, v3.sub(g, m4pt(this.G(R, [0, 0, 0]), this.grip))];
  }
  hand_key(s, k0, R, t) {
    const k = resolve(k0, this.view); const H0 = this.H0[s], Gm = this.G(R, t); const kind = k.k || 'rest'; let Rh, palm;
    if (kind === 'rest' || kind === 'gun') {
      Rh = m3mul(m3mul(m3mul(R, eul(k.r)), m3T(this.Rg0)), m4R(H0));
      if (k.fwd) Rh = m3mul(m3mul(R, basis(k.fwd, k.palm)), eul(k.r2));
      if (kind === 'gun' && k.p) palm = m4pt(Gm, k.p);
      else { const ppx = v3.add(m4pt(this.G0i, this.palm0[s]), k.dpx || [0, 0, 0]); palm = m4pt(Gm, ppx); }
      palm = v3.add(palm, m3v(R, k.d || [0, 0, 0]));
    } else {
      const PiR = m4R(this.Pi);
      Rh = m3mul(m3mul(m3mul(PiR, eul(k.r)), m4R(this.place)), m4R(H0));
      if (k.fwd) Rh = m3mul(m3mul(PiR, basis(k.fwd, k.palm)), eul(k.r2));
      palm = k.p ? m4pt(this.Pi, k.p) : v3.add(this.palm0[s], m3v(PiR, k.d || [0, 0, 0]));
    }
    const wrist = v3.sub(palm, m3v(Rh, this.fist(s)));
    return [m4(wrist, Rh), k.curl || 0, k.sw || 0, m3v(m4R(this.Pi), k.sho || [0, 0, 0]), k.flat || 0, k.fk || null];
  }
  pose_arm(s, Ht, curl, out, sw = 0, sho = null) {
    const n = p => this.pre(s, p); const U = this.bones[n('UpperArm')], F = this.bones[n('Forearm')], H = this.bones[n('Hand')];
    const sh0 = m4t(U), shift = sho || [0, 0, 0], sh = v3.add(sh0, shift), el = v3.add(m4t(F), shift), wr = v3.add(m4t(H), shift);
    const a = v3.len(v3.sub(el, sh)), b = v3.len(v3.sub(wr, el));
    const tgt = m4t(Ht), to = v3.sub(tgt, sh); const d = Math.min(Math.max(v3.len(to), Math.abs(a - b) + 1e-3), a + b - 1e-3); const dir = v3.norm(to);
    let bend = v3.sub(v3.sub(el, sh), v3.mul(dir, v3.dot(v3.sub(el, sh), dir)));
    // where the posed elbow gives a clear direction it decides; where it nearly lines up with the reach it can flip
    // sides between frames, so the elbow keeps to the side it was on the frame before
    const raw = v3.len(bend), pe = this.cont && this.cont['e' + s];
    if (raw < 1e-6) bend = [0, -1, 0]; bend = v3.norm(bend);
    if (pe) { let pp = v3.sub(pe, v3.mul(dir, v3.dot(pe, dir))); if (v3.len(pp) > 1e-6) { pp = v3.norm(pp); const w = Math.min(1, raw / (0.45 * a)); bend = v3.norm(v3.add(v3.mul(bend, w), v3.mul(pp, 1 - w))); } }
    if (sw) bend = m3v(axis_rot(dir, sw), bend);   // elbow swung about the shoulder-wrist line
    const C = this.cont, ov = C && C.ov && C.ov[s][C.f];
    if (ov) { const pp = v3.sub(ov.b, v3.mul(dir, v3.dot(ov.b, dir))); if (v3.len(pp) > 1e-6) bend = v3.norm(pp); }   // smoothed over time
    const cs = Math.min(1, Math.max(-1, (a * a + d * d - b * b) / (2 * a * d)));
    const el2 = v3.add(v3.add(sh, v3.mul(dir, a * cs)), v3.mul(bend, a * Math.sqrt(1 - cs * cs))), wr2 = v3.add(sh, v3.mul(dir, d));
    const Ru = rot_to(v3.sub(el, sh), v3.sub(el2, sh));
    const U2 = m4(sh, m3mul(Ru, m4R(U)));
    if (this.cont) this.cont['e' + s] = v3.norm(v3.sub(el2, sh));
    const f0 = m3v(Ru, v3.sub(wr, el)); const Rf = m3mul(rot_to(f0, v3.sub(wr2, el2)), Ru);
    let Fr = m3mul(Rf, m4R(F));
    const ax = v3.norm(v3.sub(wr2, el2)); const Hfree = m3mul(Rf, m4R(H)), Hr = m4R(Ht);
    const q = r2q(m3mul(Hr, m3T(Hfree))); let tw = 2 * Math.atan2(v3.dot(q.slice(0, 3), ax), q[3]) / D2R;
    tw = ((tw + 180) % 360 + 360) % 360 - 180;
    if (this.cont) { const p = this.cont['t' + s]; if (p !== undefined) { while (tw - p > 180) tw -= 360; while (tw - p < -180) tw += 360; } this.cont['t' + s] = tw; }   // no half-turn flips between frames
    if (C && C.rec) C.rec[s][C.f] = { b: bend, tw };
    if (ov) tw = ov.tw;
    Fr = m3mul(axis_rot(ax, tw * 0.5), Fr);
    const F2 = m4(el2, Fr), H2 = m4(wr2, Hr);
    out[n('UpperArm')] = U2; out[n('Forearm')] = F2; out[n('Hand')] = H2;
    const Dm = m4mul(H2, m4inv(H));
    for (const part of ['Finger0', 'Finger1', ...this.S.FING.map(f => 'Finger1_' + f)]) {
      const B = this.bones[n(part)]; if (!B) continue; let M = m4mul(Dm, B);
      if (part.startsWith('Finger1') && curl) M = m4(m4t(M), m3mul(axis_rot(col(m4R(M), 1), curl), m4R(M)));
      out[n(part)] = M;
    }
  }
  // whole-arm pose from a solved shoulder (clavicle turned) and elbow; the hand lands exactly on its target
  pose_arm_nat(s, Ht, curl, out, so) {
    const n = p => this.pre(s, p); const U = this.bones[n('UpperArm')], F = this.bones[n('Forearm')], H = this.bones[n('Hand')];
    const sh0 = m4t(U), Rc = so.Rc, sh = so.sh;
    const restAbout = M => m4(v3.add(sh, m3v(Rc, v3.sub(m4t(M), sh0))), m3mul(Rc, m4R(M)));
    const Ur = restAbout(U), Fr0 = restAbout(F), Hr0 = restAbout(H);
    const el = m4t(Fr0), wr = m4t(Hr0), a = v3.len(v3.sub(el, sh)), b = v3.len(v3.sub(wr, el));
    const tgt = m4t(Ht), to = v3.sub(tgt, sh); const d = Math.min(Math.max(v3.len(to), Math.abs(a - b) + 1e-3), a + b - 1e-3); const u = v3.norm(to);
    const cs = Math.min(1, Math.max(-1, (a * a + d * d - b * b) / (2 * a * d))), sn = Math.sqrt(1 - cs * cs);
    let e = v3.sub(so.edir, v3.mul(u, v3.dot(so.edir, u))); if (v3.len(e) < 1e-6) e = [0, -1, 0]; e = v3.norm(e);
    const el2 = v3.add(v3.add(sh, v3.mul(u, a * cs)), v3.mul(e, a * sn)), wr2 = v3.add(sh, v3.mul(u, d));
    const Ru = rot_to(v3.sub(el, sh), v3.sub(el2, sh));
    const U2 = m4(sh, m3mul(Ru, m4R(Ur)));
    const f0 = m3v(Ru, v3.sub(wr, el)); const Rf = m3mul(rot_to(f0, v3.sub(wr2, el2)), Ru);
    let Fr = m3mul(Rf, m4R(Fr0));
    const ax = v3.norm(v3.sub(wr2, el2)); const Hfree = m3mul(Rf, m4R(Hr0)), Hr = m4R(Ht);
    const q = r2q(m3mul(Hr, m3T(Hfree))); let tw = 2 * Math.atan2(v3.dot(q.slice(0, 3), ax), q[3]) / D2R;
    tw = ((tw + 180) % 360 + 360) % 360 - 180;
    if (this.cont) { const p = this.cont['t' + s]; if (p !== undefined) { while (tw - p > 180) tw -= 360; while (tw - p < -180) tw += 360; } this.cont['t' + s] = tw; }   // no half-turn flips between frames
    Fr = m3mul(axis_rot(ax, tw * 0.6), Fr);                     // most of the turn is the forearm rolling, the rest the wrist
    const F2 = m4(el2, Fr), H2 = m4(wr2, Hr);
    out[n('UpperArm')] = U2; out[n('Forearm')] = F2; out[n('Hand')] = H2;
    const C = this.bones[n('Clavicle')]; if (C) out[n('Clavicle')] = m4(m4t(C), m3mul(Rc, m4R(C)));
    const Dm = m4mul(H2, m4inv(H));
    for (const part of ['Finger0', 'Finger1', ...this.S.FING.map(f => 'Finger1_' + f)]) {
      const B = this.bones[n(part)]; if (!B) continue; let M = m4mul(Dm, B);
      if (part.startsWith('Finger1') && curl) M = m4(m4t(M), m3mul(axis_rot(col(m4R(M), 1), curl), m4R(M)));
      out[n(part)] = M;
    }
  }
  // ---- Josiah's own joint turns on a key (editor "Arm joints" and "Fingers"): each joint turns itself and everything
  // below it, in order from the collarbone out to the fingertips. Degrees; positive = up / forward / more bent.
  userPose(s, bones, fk) {
    if (!hasFK(fk)) return;
    const n = p => this.pre(s, p), PiR = m4R(this.Pi), fp = this.view === 'fp';
    const UP = m3v(PiR, [0, 1, 0]), FWD = m3v(PiR, [0, 0, fp ? -1 : 1]);
    const fingers = this.fingerList(s).map(f => f.name);
    const chain = ['Clavicle', 'UpperArm', 'Forearm', 'Hand'].map(n).concat(fingers);
    const turn = (from, pivot, ax, deg) => {
      if (!deg || v3.len(ax) < 1e-6) return; const Rr = axis_rot(ax, deg);
      for (const name of chain.slice(chain.indexOf(from))) { const B = bones[name]; if (!B) continue; bones[name] = m4(v3.add(pivot, m3v(Rr, v3.sub(m4t(B), pivot))), m3mul(Rr, m4R(B))); }
    };
    const P = p => m4t(bones[n(p)]), perp = (a, b) => { const c = v3.cross(a, b); return v3.len(c) < 1e-6 ? [0, 0, 0] : v3.norm(c); };
    const v = (x, i) => (x && x[i]) || 0;
    if (bones[n('Clavicle')] && fk.cl) {
      const c = v3.sub(P('UpperArm'), P('Clavicle'));
      turn(n('Clavicle'), P('Clavicle'), perp(c, UP), v(fk.cl, 0)); turn(n('Clavicle'), P('Clavicle'), perp(v3.sub(P('UpperArm'), P('Clavicle')), FWD), v(fk.cl, 1));
    }
    if (fk.sh) {
      const d = () => v3.sub(P('Forearm'), P('UpperArm'));
      turn(n('UpperArm'), P('UpperArm'), perp(d(), UP), v(fk.sh, 0));
      turn(n('UpperArm'), P('UpperArm'), perp(d(), FWD), v(fk.sh, 1));
      turn(n('UpperArm'), P('UpperArm'), d(), v(fk.sh, 2));
    }
    if (fk.el) {
      const u = v3.sub(P('Forearm'), P('UpperArm')), f = v3.sub(P('Hand'), P('Forearm'));
      let ax = perp(u, f); if (!v3.len(ax)) ax = perp(u, UP);
      turn(n('Forearm'), P('Forearm'), ax, fk.el);
    }
    if (fk.tw) turn(n('Forearm'), P('Forearm'), v3.sub(P('Hand'), P('Forearm')), fk.tw);
    if (fk.wr) {
      turn(n('Hand'), P('Hand'), col(m4R(bones[n('Hand')]), 2), v(fk.wr, 0));
      turn(n('Hand'), P('Hand'), col(m4R(bones[n('Hand')]), 1), v(fk.wr, 1));
    }
    const fl = fk.f || {}, keys = ['th', 'i', 'm', 'r', 'p'], names = [fingers[0], ...fingers.slice(2)];
    keys.forEach((key, j) => {
      const a = fl[key], name = names[j], B = bones[name]; if (!a || !B) return;
      const one = [name], R0 = m4R(B), piv = m4t(B);
      let R = R0;
      if (a[0]) R = m3mul(axis_rot(col(R, 1), a[0]), R);
      if (a[1]) R = m3mul(axis_rot(col(R, 2), a[1]), R);
      if (a[2]) R = m3mul(axis_rot(col(R, 0), a[2]), R);
      bones[name] = m4(piv, R); void one;
    });
  }
  // ---- fingers: open flat (a slap), and kept out of the gun (reload only; his static poses are untouched)
  fingerList(s) {
    const sd = s, S = this.S, fp = this.view === 'fp', bn = p => fp ? `${sd}_${p}` : `Bip01_${sd}_${p}`, NB = S.names.length, si = s === 'R' ? 0 : 1;
    return [{ name: bn('Finger0'), skin: S.names.indexOf(`Bip01_${sd}_Finger0`), thumb: true }, { name: bn('Finger1'), skin: S.names.indexOf(`Bip01_${sd}_Finger1`) },
      ...S.FING.map((f, k) => ({ name: bn('Finger1_' + f), skin: NB + si * 4 + k }))];
  }
  meshCache() {
    if (this._mc) return this._mc;
    const S = this.S, dec = b => { const bin = typeof atob === 'function' ? Uint8Array.from(atob(b), c => c.charCodeAt(0)) : null; return new Float32Array(bin.buffer); };
    const P = dec(S.P), J = dec(S.J), W = dec(S.W), nv = P.length / 3, groups = {};
    for (let v = 0; v < nv; v++) { let b = -1, bw = 0; for (let j = 0; j < 4; j++) if (W[v * 4 + j] > bw) { bw = W[v * 4 + j]; b = J[v * 4 + j]; } (groups[b] = groups[b] || []).push(v); }
    return (this._mc = { P, J, W, groups });
  }
  flatten(s, bones, amt) {
    if (amt <= 1e-3) return;
    const S = this.S, inv = this.view === 'fp' ? S.INV_ : S.INV, ih = S.names.indexOf(`Bip01_${s}_Hand`);
    const Hb = m4R(m4inv(inv[ih])), Hn = m4R(bones[this.pre(s, 'Hand')]);
    for (const fg of this.fingerList(s)) {
      const B = bones[fg.name]; if (!B) continue;
      const bi = fg.thumb ? fg.skin : S.names.indexOf(`Bip01_${s}_Finger1`);
      // open the finger by uncurling about its own curl axis, stopping at straight (never bent backwards)
      const rel = m3mul(m3T(Hb), m4R(m4inv(inv[bi]))), Rf = m3mul(Hn, rel), R = m4R(B), ay = col(R, 1);
      const proj = x => v3.norm(v3.sub(x, v3.mul(ay, v3.dot(x, ay))));
      const fx = proj(col(R, 0)), tx = proj(col(Rf, 0));
      let ang = Math.atan2(v3.dot(v3.cross(fx, tx), ay), v3.dot(fx, tx)) / D2R;
      const a = (fg.thumb ? 0.6 : 0.92) * amt;
      bones[fg.name] = m4(m4t(B), m3mul(axis_rot(ay, ang * a), R));
    }
  }
  unclip(s, bones, Gm, F, amt, fixed) {
    if (fixed) {   // apply finger angles already solved (and smoothed over time)
      for (const fg of this.fingerList(s)) { const a = fixed[fg.name], B = bones[fg.name]; if (!a || !B) continue; const R = m4R(B);
        bones[fg.name] = m4(m4t(B), m3mul(m3mul(axis_rot(col(R, 1), a[0] * amt), axis_rot(col(R, 2), a[1] * amt)), R)); }
      return;
    }
    const found = {};
    if (!F || !F.arr) return found;
    const mc = this.meshCache(), K = this.skin(bones), Gi = m4inv(Gm), sc = this.scale / 16, margin = 0.003;
    const dist = x => { const ix = Math.round((x[0] - F.lo[0]) / F.vox), iy = Math.round((x[1] - F.lo[1]) / F.vox), iz = Math.round((x[2] - F.lo[2]) / F.vox);
      if (ix < 0 || iy < 0 || iz < 0 || ix >= F.shape[0] || iy >= F.shape[1] || iz >= F.shape[2]) return 1; return F.arr[(ix * F.shape[1] + iy) * F.shape[2] + iz] * F.vox * sc; };
    const xf = (b, p) => [0, 1, 2].map(r => K[b * 16 + r] * p[0] + K[b * 16 + 4 + r] * p[1] + K[b * 16 + 8 + r] * p[2] + K[b * 16 + 12 + r]);
    const P = mc.P, J = mc.J, W = mc.W;
    for (const fg of this.fingerList(s)) {
      const B = bones[fg.name], vs = mc.groups[fg.skin]; if (!B || !vs) continue;
      const pts = vs.map(v => { const p = [P[v * 3], P[v * 3 + 1], P[v * 3 + 2]]; let full = [0, 0, 0], own = [0, 0, 0], w = 0;
        for (let j = 0; j < 4; j++) { const ww = W[v * 4 + j]; if (!ww) continue; const q = xf(J[v * 4 + j], p); full = v3.add(full, v3.mul(q, ww)); if (J[v * 4 + j] === fg.skin) { own = v3.add(own, v3.mul(q, ww)); w += ww; } }
        return { full: m4pt(this.Pi, full), own: m4pt(this.Pi, own), w }; });
      const o = m4t(B), R = m4R(B), ay = col(R, 1), az = col(R, 2);
      const pen = (Rr, gapw = 0) => { let c = 0, dm = 1; for (const q of pts) { const ownR = v3.add(v3.mul(o, q.w), m3v(Rr, v3.sub(q.own, v3.mul(o, q.w)))), x = v3.add(v3.sub(q.full, q.own), ownR);
        const d = dist(m4pt(Gi, x)); dm = Math.min(dm, d); if (d < margin) c += (margin - d) ** 2; } return c * 1e6 + gapw * 1e6 * Math.max(0, dm - margin - 0.003) ** 2; };
      // pts' own part is w-weighted: rotate it about the bone origin scaled by its weight
      found[fg.name] = [0, 0];
      const p0 = pen(I3()); if (p0 < 1e-3 && !this._near(pts, Gi, dist)) continue;
      const gw = this._near(pts, Gi, dist) ? 0.5 : 0;
      let best = [0, 0], bc = Infinity;
      const zs = fg.thumb ? [-60, -45, -30, -15, 0, 15, 30, 45, 60] : [0];
      for (let ty = fg.thumb ? -80 : -60; ty <= 60; ty += 2) for (const tz of zs) {
        const Rr = m3mul(axis_rot(ay, ty), axis_rot(az, tz)), c = pen(Rr, gw) + 0.0004 * (ty * ty + tz * tz);
        if (c < bc) { bc = c; best = [ty, tz]; } }
      found[fg.name] = best;
    }
    return found;
  }
  _near(pts, Gi, dist) { let dm = 1; for (const q of pts) dm = Math.min(dm, dist(m4pt(Gi, q.full))); return dm < 0.05; }
  skin(bones) { // column-major Float32Array (n+8)*16 for the page
    const S = this.S, NB = S.names.length, out = new Float32Array((NB + 8) * 16);
    const put = (i, M) => { for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) out[i * 16 + c * 4 + r] = M[r * 4 + c]; };
    if (this.view === 'fp') {
      const sh = {}; const done = new Set();
      ['R', 'L'].forEach((sd, s) => {
        sh[s] = m4t(bones[`${sd}_UpperArm`]);
        for (const p of ['UpperArm', 'Forearm', 'Hand', 'Finger0', 'Finger1']) { const bi = S.names.indexOf(`Bip01_${sd}_${p}`); put(bi, m4mul(this.place, m4mul(bones[`${sd}_${p}`], S.INV_[bi]))); done.add(bi); }
        const f1 = S.names.indexOf(`Bip01_${sd}_Finger1`);
        for (let k = 0; k < 4; k++) { put(NB + s * 4 + k, m4mul(this.place, m4mul(bones[`${sd}_Finger1_${S.FING[k]}`] || bones[`${sd}_Finger1`], S.INV_[f1]))); done.add(NB + s * 4 + k); }
      });
      for (let bi = 0; bi < NB + 8; bi++) if (!done.has(bi)) { const z = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]; const p = sh[S.SIDE[bi]]; z[3] = p[0]; z[7] = p[1]; z[11] = p[2]; put(bi, m4mul(this.place, z)); }
      return out;
    }
    const P = this.place;
    for (let b = 0; b < NB; b++) put(b, m4mul(bones[S.names[b]] ? m4mul(P, bones[S.names[b]]) : this.clipm[b], S.INV[b]));
    ['R', 'L'].forEach((sd, s) => {
      const f1 = S.names.indexOf(`Bip01_${sd}_Finger1`);
      for (let k = 0; k < 4; k++) put(NB + s * 4 + k, m4mul(P, m4mul(bones[`Bip01_${sd}_Finger1_${S.FING[k]}`] || bones[`Bip01_${sd}_Finger1`], S.INV[f1])));
    });
    return out;
  }
}

function local_t(spec, name, t) {
  const mv = (spec.moves || {})[name]; if (!mv || !mv.length) return m4([0, 0, 0], I3());
  const [a, b, u] = track(mv, t);
  const d = lerpv(a, b, u, 'd', [0, 0, 0]), r = lerpv(a, b, u, 'r', [0, 0, 0]), sc = lerpv(a, b, u, 's', [1, 1, 1]);
  const info = (spec.parts || {})[name] || (spec.extraInfo || {})[name] || {};
  const piv = info.pivot || a.pivot || [0, 0, 0];
  const RS = m3mul(eul(r), [sc[0], 0, 0, 0, sc[1], 0, 0, 0, sc[2]]);
  return m4mul(m4mul(m4mul(m4(d, I3()), m4(piv, I3())), m4([0, 0, 0], RS)), m4(v3.mul(piv, -1), I3()));
}
function target_at(rig, spec, s, t) {
  let [a, b, u] = track(spec.gun || [[0, {}]], t); const [R, tt] = rig.gun_at(a, b, u);
  return [handAt(rig, spec, rig.view, s, t, R, tt).h[0], rig.G(R, tt)];
}
// ---------------------------------------------------------------- natural whole-arm solver
// The shoulder rides the clavicle (forward and up as the reach grows), the elbow picks the most natural place on its
// circle (hangs with gravity, a little out, keeps the wrist straight, near its posed spot when the hand is home, never
// through her body or the gun), and both are smoothed over time so the whole arm moves together.
function bodyCaps(rig, view) {
  const PiR = m4R(rig.Pi), dn = m3v(PiR, [0, -1, 0]), bk = m3v(PiR, view === 'fp' ? [0, 0, 1] : [0, 0, -1]);
  if (view === 'fp') {
    const sR = m4t(rig.bones['R_UpperArm']), sL = m4t(rig.bones['L_UpperArm']), mid = v3.lerp(sR, sL, 0.5), eye = m4pt(rig.Pi, [0, 0, 0]);
    return [{ a: v3.add(v3.add(mid, v3.mul(dn, 0.12)), v3.mul(bk, 0.06)), b: v3.add(v3.add(mid, v3.mul(dn, 0.55)), v3.mul(bk, 0.06)), r: 0.11 },
            { a: eye, b: eye, r: 0.1 }];
  }
  const S = rig.S, P = n => m4pt(rig.Pi, m4t(rig.clipm[S.names.indexOf(n)])), up = v3.mul(dn, -1);
  const pel = P('Bip01_Pelvis'), nk = P('Bip01_Neck'), hd = P('Bip01_Head');
  return [{ a: v3.add(pel, v3.mul(up, 0.08)), b: v3.sub(nk, v3.mul(up, 0.06)), r: 0.115 }, { a: v3.add(hd, v3.mul(up, 0.07)), b: v3.add(hd, v3.mul(up, 0.07)), r: 0.1 }];
}
function segDist(p, a, b) { const ab = v3.sub(b, a), l2 = v3.dot(ab, ab); const t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, v3.dot(v3.sub(p, a), ab) / l2)); return v3.len(v3.sub(p, v3.add(a, v3.mul(ab, t)))); }
function smoothVecs(arr, passes = 3) {
  let a = arr.map(v => v.slice());
  for (let k = 0; k < passes; k++) a = a.map((v, i) => { const p = a[Math.max(0, i - 1)], q = a[Math.min(a.length - 1, i + 1)]; return [0, 1, 2].map(j => (p[j] + 2 * v[j] + q[j]) / 4); });
  return a;
}
function natSolve(rig, kit, view, pre, spec) {
  const PiR = m4R(rig.Pi), down = m3v(PiR, [0, -1, 0]), up = v3.mul(down, -1);
  const caps = bodyCaps(rig, view), F = kit.field, sc = rig.scale / 16;
  const style = (spec.style || {})[view] || {};
  const out = { R: [], L: [] };
  for (const s of 'RL') {
    const n = p => rig.pre(s, p); const U = rig.bones[n('UpperArm')], Fb = rig.bones[n('Forearm')], H = rig.bones[n('Hand')];
    const sh0 = m4t(U), el0 = m4t(Fb), wr0 = m4t(H), a = v3.len(v3.sub(el0, sh0)), b = v3.len(v3.sub(wr0, el0)), L = a + b;
    const outward = m3v(PiR, view === 'fp' ? [s === 'R' ? 1 : -1, 0, 0] : [s === 'R' ? -1 : 1, 0, 0]);
    const C = rig.bones[n('Clavicle')];
    const piv = C ? m4t(C) : v3.add(v3.add(sh0, v3.mul(outward, -0.14)), v3.mul(down, 0.02));
    const v0 = v3.sub(sh0, piv), r0 = v3.len(v0);
    const hax = m3v(m3T(m4R(H)), v3.norm(v3.sub(wr0, el0)));       // the posed hand's own forearm line
    const restReach = v3.len(v3.sub(wr0, sh0));
    const sv = style[s] && style[s].elbow;                            // his elbow habit, in the hand's own frame
    const want = pre.map(p => {
      const w = m4t(p.H[s]), v = v3.sub(w, sh0), ext = Math.max(0, v3.len(v) - Math.max(0.86 * L, restReach));
      let off = v3.mul(v3.norm(v), Math.min(view === 'fp' ? 0.04 : 0.1, ext * 0.95));
      off = v3.add(off, v3.mul(up, Math.min(0.03, Math.max(0, v3.dot(v, up)) * 0.12)));
      return off;
    });
    const offs = smoothVecs(want, 4);
    const sol = offs.map(o => {
      let dv = v3.norm(v3.add(v0, o));
      const ang = Math.acos(Math.max(-1, Math.min(1, v3.dot(dv, v3.norm(v0)))));
      const lim = (view === 'fp' ? 10 : 28) * D2R; if (ang > lim) { const k = lim / ang; dv = v3.norm(v3.lerp(v3.norm(v0), dv, k)); }
      return { Rc: rot_to(v0, v3.mul(dv, r0)), sh: v3.add(piv, v3.mul(dv, r0)) };
    });
    let prev = null; const edirs = [];
    pre.forEach((p, f) => {
      const Ht = p.H[s], w = m4t(Ht), Rh = m4R(Ht), sh = sol[f].sh;
      const to = v3.sub(w, sh), d = Math.min(Math.max(v3.len(to), Math.abs(a - b) + 1e-3), L - 1e-3), u = v3.norm(to);
      const cs = (a * a + d * d - b * b) / (2 * a * d), sn = Math.sqrt(Math.max(0, 1 - cs * cs)), c0 = v3.add(sh, v3.mul(u, a * cs));
      let p0 = v3.sub(down, v3.mul(u, v3.dot(down, u))); if (v3.len(p0) < 1e-4) p0 = v3.sub(outward, v3.mul(u, v3.dot(outward, u))); p0 = v3.norm(p0);
      const q0 = v3.cross(u, p0);
      const perp = x => { const y = v3.sub(x, v3.mul(u, v3.dot(x, u))); return v3.len(y) < 1e-6 ? null : v3.norm(y); };
      const home = Math.exp(-v3.len(v3.sub(w, wr0)) / 0.07);
      const eRest = perp(v3.sub(el0, sh0)), ePrev = prev && perp(prev), eStyle = sv ? perp(m3v(Rh, sv)) : null;
      const wantF = m3v(Rh, hax), Gi = p.Gi;
      const cost = phi => {
        const e = v3.add(v3.mul(p0, Math.cos(phi)), v3.mul(q0, Math.sin(phi))), el = v3.add(c0, v3.mul(e, a * sn));
        let c = 1.0 * (1 - Math.cos(phi)) - (view === 'fp' ? 0.1 : 0.35) * v3.dot(e, outward);
        if (view === 'fp') {   // first person: the elbow hangs below the wrist, never winged up beside the camera
          const elev = v3.dot(v3.sub(el, w), up); if (elev > -0.18) c += 300 * (elev + 0.18) ** 2;
          const eh = v3.dot(v3.sub(el, sh), up); if (eh > -0.3) c += 300 * (eh + 0.3) ** 2;
        }
        const dev = Math.acos(Math.max(-1, Math.min(1, v3.dot(v3.norm(v3.sub(w, el)), wantF))));
        c += 1.5 * dev * dev + 8 * Math.max(0, dev - 0.7) ** 2;
        if (eRest) c += 8 * home * (1 - v3.dot(e, eRest));
        if (eStyle && view !== 'fp') c += 1.5 * (1 - home) * (1 - v3.dot(e, eStyle));
        if (ePrev) c += 6 * (1 - v3.dot(e, ePrev));
        let pen = 0;
        for (let k = 1; k <= 8; k++) {
          const t = k / 8, pt = k <= 4 ? v3.lerp(sh, el, 0.2 + t * 0.8) : v3.lerp(el, w, (t - 0.5) * 1.4), r = k <= 4 ? 0.045 : 0.035;
          for (const cp of caps) { const dd = segDist(pt, cp.a, cp.b); if (dd < cp.r + r) pen += (cp.r + r - dd) ** 2; }
          if (F && k < 8) {
            const px = m4pt(Gi, pt), ix = Math.round((px[0] - F.lo[0]) / F.vox), iy = Math.round((px[1] - F.lo[1]) / F.vox), iz = Math.round((px[2] - F.lo[2]) / F.vox);
            if (ix >= 0 && iy >= 0 && iz >= 0 && ix < F.shape[0] && iy < F.shape[1] && iz < F.shape[2]) {
              const fd = F.arr[(ix * F.shape[1] + iy) * F.shape[2] + iz] * F.vox * sc; if (fd < r) pen += (r - fd) ** 2;
            }
          }
        }
        return c + 3000 * pen;
      };
      let best = 0, bc = Infinity;
      for (let i = 0; i < 72; i++) { const ph = i * 5 * D2R - Math.PI, c = cost(ph); if (c < bc) { bc = c; best = ph; } }
      for (let i = -5; i <= 5; i++) { const ph = best + i * D2R, c = cost(ph); if (c < bc) { bc = c; best = ph; } }
      const e = v3.add(v3.mul(p0, Math.cos(best)), v3.mul(q0, Math.sin(best)));
      edirs.push(e); prev = e;
    });
    const sm = smoothVecs(edirs, 3);
    out[s] = sol.map((so, f) => {
      let edir = sm[f]; const sw = pre[f].sw[s], sho = pre[f].sho[s];
      if (sw) { const u = v3.norm(v3.sub(m4t(pre[f].H[s]), so.sh)); edir = m3v(axis_rot(u, sw), edir); }
      // the first and last frames are exactly his posed arm: ease the solved shoulder/elbow into it at both ends
      const x = Math.min(1, Math.min(f, sol.length - 1 - f) / 10), k = x * x * (3 - 2 * x);
      const Rc = slerp(I3(), so.Rc, k), shp = v3.lerp(sh0, so.sh, k), ed = v3.lerp(v3.sub(el0, sh0), v3.mul(edir, v3.len(v3.sub(el0, sh0))), k);
      return { Rc, sh: v3.add(shp, sho), edir: ed };
    });
  }
  return out;
}
// bake the whole clip for one view: per frame {g: {part: colmajor16 in view space}, skin: Float32Array, miss: {R,L}, tgt: {R,L} palm targets}
function bake(S, kit, spec, view, fps = 30) {
  const rig = new Rig(S, kit.rigs[view], view);
  const centre = name => { let g = kit.groups[name]; if (g && g.clone) g = kit.groups[g.clone]; return g ? g.c0 : [0, 0, 0]; };
  const state = {};
  for (const [name, g] of Object.entries(spec.parts || {})) state[name] = { parent: g.hidden ? 'hidden' : 'gun', rel: null, world: null, vel: [0, 0, 0] };
  for (const [name, g] of Object.entries(spec.extraInfo || {})) state[name] = { parent: g.hidden ? 'hidden' : 'gun', rel: null, world: null, vel: [0, 0, 0] };
  state._body = { parent: 'gun', world: null, vel: [0, 0, 0] };
  const evs = (spec.events || []).slice().sort((a, b) => a[0] - b[0]); let ei = 0;
  const grabbed = {}; const frames = []; const x = spec.dur * fps, fl = Math.floor(x), nf = (Math.abs(x - fl - 0.5) < 1e-9 ? (fl % 2 ? fl + 1 : fl) : Math.round(x)) + 1;
  const pre = [];
  const mkPre = (f, off) => {
    const t = f === nf - 1 ? spec.dur : f / fps;   // the last frame is exactly the end pose
    let [a, b, u] = track(spec.gun || [[0, {}]], t); let [R, tt] = rig.gun_at(a, b, u); if (off) tt = v3.add(tt, off); const Gm = rig.G(R, tt);
    const hands = {}, restK = {};
    for (const s of 'RL') {
      const ha = handAt(rig, spec, view, s, t, R, tt), af = ((spec.armfit || {})[s] || {});
      restK[s] = ha.rest;
      hands[s] = [ha.h[0], ha.h[1], ha.h[2] + (af.sw || 0), v3.add(ha.h[3], m3v(m4R(rig.Pi), af.sho || [0, 0, 0])), ha.h[4], ha.h[5]];
    }
    return { t, R, tt, Gm, Gi: m4inv(Gm), hands, restK, H: { R: hands.R[0], L: hands.L[0] }, sw: { R: hands.R[2], L: hands.L[2] }, sho: { R: hands.R[3], L: hands.L[3] } };
  };
  for (let f = 0; f < nf; f++) pre.push(mkPre(f, null));
  // a hand back on its set grip is exactly his grip: fixes fade out before it gets there (pistol grip hand excepted)
  const fixW = {}, pushW = {};
  for (const s of 'RL') {
    const keep = spec.pistol && s === 'R', rest = pre.map(p => p.restK[s]);
    pushW[s] = rest.map((r, f) => { let d = 99; for (let k = 0; k < nf; k++) if (rest[k]) d = Math.min(d, Math.abs(k - f)); const x = Math.min(1, d / 6); return x * x * (3 - 2 * x); });
    fixW[s] = rest.map((r, f) => { if (keep) return 1; let d = 99; for (let k = 0; k < nf; k++) if (rest[k]) d = Math.min(d, Math.abs(k - f)); const x = Math.min(1, d / 6); return x * x * (3 - 2 * x); });
  }
  if (spec.solver === 'natural') {
    // The gun lives in her hands: when a hand holding (or working) the gun can't reach, the gun comes in toward that
    // shoulder instead of the arm overstretching. Shifts are smoothed so the gun drifts, never pops.
    const lim = {};
    for (const s of 'RL') { const n = p => rig.pre(s, p), sh = m4t(rig.bones[n('UpperArm')]), el = m4t(rig.bones[n('Forearm')]), wr = m4t(rig.bones[n('Hand')]);
      lim[s] = { sh, L: v3.len(v3.sub(el, sh)) + v3.len(v3.sub(wr, el)), r0: v3.len(v3.sub(wr, sh)) }; }
    const offs = pre.map((p, f) => {
      let off = [0, 0, 0], cur = p;
      for (let it = 0; it < 4; it++) {
        let worst = null, we = 0.005;
        for (const s of 'RL') { const w = m4t(cur.H[s]), d = v3.len(v3.sub(w, lim[s].sh)) - (cur.restK[s] ? Math.max(lim[s].L * 0.985 + (view === 'fp' ? 0.005 : 0.015), lim[s].r0 + 0.003) : Math.max(lim[s].L * 0.985 + (view === 'fp' ? 0.015 : 0.045), lim[s].r0 + 0.005)); if (d > we) { we = d; worst = s; } }
        if (!worst) break;
        const probe = mkPre(f, v3.add(off, [0.01, 0, 0]));
        if (v3.len(v3.sub(m4t(probe.H[worst]), m4t(cur.H[worst]))) < 0.005) break;      // that hand isn't on the gun
        const dir = v3.norm(v3.sub(lim[worst].sh, m4t(cur.H[worst])));
        off = v3.add(off, v3.mul(dir, we + 0.005)); cur = mkPre(f, off);
      }
      return off;
    });
    let sm = offs.map((o, i) => { let m = o; for (let k = -6; k <= 6; k++) { const q = offs[Math.max(0, Math.min(offs.length - 1, i + k))]; if (v3.len(q) > v3.len(m)) m = q; } return m; });
    sm = smoothVecs(sm, 6);
    sm = sm.map((o, f) => { const x = Math.min(1, Math.min(f, nf - 1 - f) / 8), w = x * x * (3 - 2 * x); return v3.mul(o, w); });   // exact start/end pose
    for (let f = 0; f < nf; f++) if (v3.len(sm[f]) > 1e-5) pre[f] = mkPre(f, sm[f]);
    // whatever is still out of reach (a pouch below a far-forward rest, say): the hand goes as far as the arm allows
    for (const s of 'RL') {
      const ex = pre.map(p => { const v = v3.sub(m4t(p.H[s]), lim[s].sh), d = v3.len(v) - Math.max(lim[s].L * 0.985 + (view === 'fp' ? 0.02 : 0.05), lim[s].r0 + 0.005); return d > 0 ? v3.mul(v3.norm(v), -d) : [0, 0, 0]; });
      const exs = smoothVecs(ex.map((o, i) => { let m = o; for (let k = -4; k <= 4; k++) { const q = ex[Math.max(0, Math.min(ex.length - 1, i + k))]; if (v3.len(q) > v3.len(m)) m = q; } return m; }), 4);
      pre.forEach((p, f) => { if (globalThis.__dbgc && v3.len(exs[f]) > 1e-4) console.log('clamp', s, f, v3.len(exs[f]).toFixed(4)); if (f === 0 || f === nf - 1 || v3.len(exs[f]) < 1e-5) return; const H = p.H[s], Hn = m4(v3.add(m4t(H), exs[f]), m4R(H)); p.H[s] = Hn; p.hands[s] = [Hn, ...p.hands[s].slice(1)]; });
    }
  }
  if (spec.solver === 'natural' && kit.field && kit.field.arr) {
    // hands never sink into the gun during the reload: each hand (palm + fingers, as posed) is pushed out of the grip
    const F = kit.field, mc = rig.meshCache(), S = rig.S, inv = view === 'fp' ? S.INV_ : S.INV, sc = rig.scale / 16, m = 0.002;
    const at = (ix, iy, iz) => (ix < 0 || iy < 0 || iz < 0 || ix >= F.shape[0] || iy >= F.shape[1] || iz >= F.shape[2]) ? 15 : F.arr[(ix * F.shape[1] + iy) * F.shape[2] + iz];
    const sdf = x => { const ix = Math.round((x[0] - F.lo[0]) / F.vox), iy = Math.round((x[1] - F.lo[1]) / F.vox), iz = Math.round((x[2] - F.lo[2]) / F.vox);
      return [at(ix, iy, iz) * F.vox * sc, v3.norm([at(ix + 1, iy, iz) - at(ix - 1, iy, iz) + 1e-6, at(ix, iy + 1, iz) - at(ix, iy - 1, iz), at(ix, iy, iz + 1) - at(ix, iy, iz - 1)])]; };
    for (const s of 'RL') {
      const H0 = rig.bones[rig.pre(s, 'Hand')], H0i = m4inv(H0), ih = S.names.indexOf(`Bip01_${s}_Hand`);
      const loc = [];   // hand-space points of the palm and fingers in his posed grip
      const add = (b, M) => { for (const v of (mc.groups[b] || [])) if (v % 2 === 0) loc.push(m4pt(m4mul(H0i, M), [mc.P[v * 3], mc.P[v * 3 + 1], mc.P[v * 3 + 2]])); };
      add(ih, m4mul(H0, inv[ih]));
      for (const fg of rig.fingerList(s)) { const B = rig.bones[fg.name]; if (B) add(fg.skin, m4mul(B, inv[fg.thumb ? fg.skin : S.names.indexOf(`Bip01_${s}_Finger1`)])); }
      const push = pre.map(p => {
        const H = p.H[s], Gi = p.Gi, GR = m4R(p.Gm); let off = [0, 0, 0];
        for (let it = 0; it < 6; it++) {
          let acc = [0, 0, 0], n = 0;
          for (const q of loc) { const x = v3.add(m4pt(H, q), off), [d, g] = sdf(m4pt(Gi, x)); if (d < m) { acc = v3.add(acc, v3.mul(v3.norm(m3v(GR, g)), m - d)); n++; } }
          if (!n) break; off = v3.add(off, v3.mul(acc, 1 / Math.max(1, n * 0.5)));
          if (v3.len(off) > 0.04) { off = v3.mul(v3.norm(off), 0.04); break; }
        }
        return off;
      });
      let sm = smoothVecs(push, 4);
      sm = sm.map((o, f) => { const x = Math.min(1, Math.min(f, nf - 1 - f) / 8), w = x * x * (3 - 2 * x); return v3.mul(o, w); });
      sm = sm.map((o, f) => v3.mul(o, pushW[s][f]));
      pre.forEach((p, f) => { if (v3.len(sm[f]) < 1e-5) return; const Hn = m4(v3.add(m4t(p.H[s]), sm[f]), m4R(p.H[s])); p.H[s] = Hn; p.hands[s] = [Hn, ...p.hands[s].slice(1)]; });
    }
  }
  const nat = spec.solver === 'natural' ? natSolve(rig, kit, view, pre, spec) : null;
  let fing = null;
  if (nat && spec.autoFingers && kit.field && kit.field.arr) {   // off unless asked: his fingers stay exactly as set (Josiah 2026-11-04 06:35, they twitched)
    // solve the finger angles for every frame first, then smooth them over time so the fingers never twitch
    const raw = { R: [], L: [] };
    for (let f = 0; f < nf; f++) {
      const bones = Object.assign({}, rig.bones);
      for (const s of 'RL') { rig.pose_arm_nat(s, pre[f].hands[s][0], pre[f].hands[s][1], bones, nat[s][f]); rig.flatten(s, bones, pre[f].hands[s][4] || 0); }
      for (const s of 'RL') raw[s].push(rig.unclip(s, bones, pre[f].Gm, kit.field, 1));
    }
    fing = { R: [], L: [] };
    for (const s of 'RL') {
      const names = rig.fingerList(s).map(fg => fg.name);
      const sm = {};
      for (const n of names) {
        const ser = raw[s].map(r => (spec.pistol && s === 'R' && n === rig.fingerList(s)[0].name) ? [0, 0] : (r[n] || [0, 0]));   // pistol thumb stays in its grip spot
        // hold the strongest correction nearby (no flicker when a finger skims the surface), then blur
        const held = ser.map((v, i) => { let m = v; for (let k = -4; k <= 4; k++) { const q = ser[Math.max(0, Math.min(ser.length - 1, i + k))]; if (Math.hypot(q[0], q[1]) > Math.hypot(m[0], m[1])) m = q; } return m; });
        sm[n] = smoothVecs(held.map(v => [v[0], v[1], 0]), 6);
      }
      for (let f = 0; f < nf; f++) fing[s].push(Object.fromEntries(names.map(n => [n, v3.mul(sm[n][f], fixW[s][f])])));
    }
  }
  rig.cont = {};   // frame-to-frame memory: forearm twist and elbow side never flip
  if (!nat) {
    // the plain arm solver: a dry run records each frame's elbow side and forearm roll, then both are smoothed over
    // time (never at the first and last frames) so the arm can't snap or twist between frames
    rig.cont = { rec: { R: [], L: [] } };
    for (let f = 0; f < nf; f++) { rig.cont.f = f; const tmp = Object.assign({}, rig.bones); const { hands } = pre[f]; for (const s of 'RL') rig.pose_arm(s, hands[s][0], hands[s][1], tmp, hands[s][2], hands[s][3]); }
    const ov = {};
    for (const s of 'RL') {
      const rec = rig.cont.rec[s], sb = smoothVecs(rec.map(r => r.b), 5), st = smoothVecs(rec.map(r => [r.tw, 0, 0]), 5);
      ov[s] = rec.map((r, f) => { const x = Math.min(1, Math.min(f, nf - 1 - f) / 6), w = x * x * (3 - 2 * x); return { b: v3.norm(v3.lerp(r.b, sb[f], w)), tw: r.tw + (st[f][0] - r.tw) * w }; });
    }
    rig.cont = { ov };
  }
  for (let f = 0; f < nf; f++) {
    if (rig.cont) rig.cont.f = f;
    const { t, R, Gm, hands } = pre[f];
    const bones = Object.assign({}, rig.bones);
    for (const s of 'RL') {
      if (nat) { rig.pose_arm_nat(s, hands[s][0], hands[s][1], bones, nat[s][f]); rig.flatten(s, bones, hands[s][4] || 0); }
      else rig.pose_arm(s, hands[s][0], hands[s][1], bones, hands[s][2], hands[s][3]);
    }
    if (nat && kit.field && kit.field.arr) { const x = Math.min(1, Math.min(f, nf - 1 - f) / 5), ramp = x * x * (3 - 2 * x); for (const s of 'RL') if (fing) rig.unclip(s, bones, Gm, kit.field, ramp, fing[s][f]); }
    for (const s of 'RL') rig.userPose(s, bones, hands[s][5]);
    const Hn = { R: bones[rig.pre('R', 'Hand')], L: bones[rig.pre('L', 'Hand')] };
    const miss = {}, tgt = {};
    for (const s of 'RL') { miss[s] = v3.len(v3.sub(m4t(Hn[s]), m4t(hands[s][0]))); tgt[s] = m4pt(rig.place, rig.palm(hands[s][0], s)); }
    while (ei < evs.length && evs[ei][0] <= t + 1e-9) {
      let e = evs[ei++]; const st = state[e[1]]; if (!st) continue; const cur = st.world;
      if (e[2] === 'grab') {
        const W = cur || m4mul(Gm, local_t(spec, e[1], t)); st.parent = e[3]; st.rel = m4mul(m4inv(Hn[e[3]]), W); grabbed[e[1]] = [e[3], st.rel];
      } else if (e[2] === 'show') {
        const hand = e[3], src = e[4]; st.parent = hand;
        if (src === '@') { const ts = e[5]; const [Hs, Gs] = target_at(rig, spec, hand, ts); st.rel = m4mul(m4mul(m4inv(Hs), Gs), local_t(spec, e[1], ts)); e = e.slice(0, 5); }
        else st.rel = grabbed[src] ? grabbed[src][1] : m4([0, 0, 0], I3());
        if (e.length > 5 && Array.isArray(e[5])) st.rel = m4mul(st.rel, m4(e[5], I3()));
      } else if (e[2] === 'drop') {
        st.parent = 'free'; st.spin = e[3] || [200, 0, 90];
        const v = v3.mul(st.vel, 0.35), sp = v3.len(v);
        st.vel = v3.add(sp > 1e-6 ? v3.mul(v, Math.min(sp, 0.8) / sp) : v, m3v(m3T(m4R(rig.place)), [0, -0.3, 0]));
      } else if (e[2] === 'seat') st.parent = 'gun';
      else if (e[2] === 'hide') st.parent = 'hidden';
    }
    const g = {};
    for (const [name, st] of Object.entries(state)) {
      let W = null;
      if (st.parent === 'gun') W = m4mul(Gm, local_t(spec, name, t));
      else if (st.parent === 'L' || st.parent === 'R') W = m4mul(Hn[st.parent], st.rel);
      else if (st.parent === 'free' && st.world) {
        const dt = 1 / fps; st.vel = v3.add(st.vel, v3.mul(m3v(m3T(m4R(rig.place)), [0, -9.8, 0]), dt));
        const c = m4pt(st.world, centre(name));
        const Rs = m3mul(axis_rot([1, 0, 0], st.spin[0] * dt), axis_rot([0, 0, 1], st.spin[2] * dt));
        W = m4mul(m4mul(m4mul(m4(v3.add(c, v3.mul(st.vel, dt)), I3()), m4([0, 0, 0], Rs)), m4(v3.mul(c, -1), I3())), st.world);
      }
      if (W && st.world && st.parent !== 'free') st.vel = v3.mul(v3.sub(m4t(W), m4t(st.world)), fps);
      st.world = W;
      if (W) { const M = m4mul(rig.place, W); g[name] = [0, 1, 2, 3].flatMap(c => [M[c], M[4 + c], M[8 + c], M[12 + c]]); }
    }
    frames.push({ g, bones, miss, tgt, gun: Gm, gunR: R, ht: { R: hands.R[0], L: hands.L[0] } });
  }
  return { rig, frames };
}
G.REngine = { bake, handTrack, hasFK, Rig, m4, m4mul, m4inv, m4pt, m4R, m4t, m3mul, m3T, m3v, eul, basis, slerp, r2q, qmat, v3, resolve, track, local_t, col, fromCols };
})(typeof window !== 'undefined' ? window : globalThis);
