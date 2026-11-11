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
      let off = v3.mul(v3.norm(v), Math.min(0.06, ext * 0.9));
      off = v3.add(off, v3.mul(up, Math.min(0.03, Math.max(0, v3.dot(v, up)) * 0.12)));
      return off;
    });
    const offs = smoothVecs(want, 4);
    const sol = offs.map(o => {
      let dv = v3.norm(v3.add(v0, o));
      const ang = Math.acos(Math.max(-1, Math.min(1, v3.dot(dv, v3.norm(v0)))));
      const lim = 20 * D2R; if (ang > lim) { const k = lim / ang; dv = v3.norm(v3.lerp(v3.norm(v0), dv, k)); }
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
        let c = 1.0 * (1 - Math.cos(phi)) - 0.35 * v3.dot(e, outward);
        const dev = Math.acos(Math.max(-1, Math.min(1, v3.dot(v3.norm(v3.sub(w, el)), wantF))));
        c += 1.5 * dev * dev + 8 * Math.max(0, dev - 0.7) ** 2;
        if (eRest) c += 8 * home * (1 - v3.dot(e, eRest));
        if (eStyle) c += 1.5 * (1 - home) * (1 - v3.dot(e, eStyle));
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
      return { Rc: so.Rc, sh: v3.add(so.sh, sho), edir };
    });
  }
  return out;
}
