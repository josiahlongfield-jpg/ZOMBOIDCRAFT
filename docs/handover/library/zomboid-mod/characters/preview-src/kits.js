// ---------- shared clothing pieces in the soldier-kit style, then one kit per zombie type ----------
// Frames: head (y up from the neck, front +z), spine (top of pelvis up), hips (pelvis centre),
// sh/el/wr (shoulder, elbow, wrist; limbs hang down), hip/kn/an (hip, knee, ankle).
function kitCtx(J0, S) {
  // headgear and face pieces go on a slightly knocked-askew head layer
  const tilt = new THREE.Group(); J0.head.add(tilt); const W8 = KIT.wear ?? 0.6;
  tilt.rotation.set((rnd() - 0.5) * 0.12 * W8, (rnd() - 0.5) * 0.1 * W8, (rnd() - 0.5) * 0.16 * W8);
  const J = { ...J0, head: tilt };
  const [tw, th, td] = S.torso, [hw, hh, hd] = S.head;
  const put = (parent, m, x, y, z, rx = 0, ry = 0, rz = 0) => { m.position.set(x, y, z); m.rotation.set(rx, ry, rz); parent.add(m); return m; };
  const fz = S.foot[2] / 2 - S.shin[0] / 2 - 0.1;
  return { J, S, tw, th, td, hw, hh, hd, P: S.pelvis, U: S.upper, F: S.fore, T: S.thigh, N: S.shin, FT: S.foot, HD: S.hand,
    put, g: (w, h, d, p) => box(w, h, d, p, true), sides: [['R', -1], ['L', 1]], fz, toe: fz + S.foot[2] / 2, BR: hh - 2.5 };
}
const STEEL2 = '#a9aba4', BRASS = '#b08a3a', LEATHER = '#5a3a22', WHITE = '#e4e1d8';
const rib = c => gear(c, { knit: true, grime: .2 });
// loose strips of torn fabric hanging from a hem or cuff at height y
function tatters(K, parent, p, y, half, n) {
  for (let i = 0; i < n; i++) {
    const s = (rnd() * 4) | 0, a = (rnd() - 0.5) * half * 1.4, L = 0.8 + rnd() * 1.8 * (KIT.wear ?? 0.6), wd = 0.5 + rnd() * 0.6;
    const [x, z] = [[a, half], [a, -half], [half, a], [-half, a]][s];
    K.put(parent, K.g(s < 2 ? wd : 0.15, L, s < 2 ? 0.15 : wd, jn(p, 't')), x, y - L / 2 + 0.1, z, (rnd() - 0.5) * 0.5, 0, (rnd() - 0.5) * 0.5);
  }
}
const nTat = () => Math.floor(rnd() * 3.5 * (KIT.wear ?? 0.6));

// shirt or jacket: chest, waist, optional hem over the belt, shoulders and sleeves
// sleeve: long | rolled | short | none; e is how far the layer stands off the body
function shirt(K, p, o = {}) {
  const { J, S, tw, th, td, P, U, F, put, g, sides } = K, e = o.e ?? 0.4, cuff = o.cuff || p;
  shell(J.spine, tw + e, th * 0.6 + 0.2, td + e, p, 0, th * 0.7, 0, 'b');
  shell(J.spine, tw - 1 + e + (o.loose || 0), th * 0.45, td - 0.4 + e + (o.loose || 0), p, 0, th * 0.22, 0, o.hem && !o.hemP ? 'tb' : 't');
  if (o.hem) { if (o.hemP) put(J.hips, g(P[0] + e + 0.15, o.hem, P[2] + e + 0.15, o.hemP), 0, P[1] / 2 - o.hem / 2 + 0.2, 0); else shell(J.hips, P[0] + e + 0.15, o.hem, P[2] + e + 0.15, p, 0, P[1] / 2 - o.hem / 2 + 0.2, 0, 't'); }
  if (o.sleeve === 'none') return;
  for (const [R, side] of sides) {
    const sh = J['sh' + R], el = J['el' + R];
    // one sleeve from the shoulder to the elbow, flowing into the forearm below
    const len = o.sleeve === 'short' ? 3.0 : U[1] + 0.2;
    shell(sh, U[0] + 0.35 + e, len, U[0] + 0.35 + e, p, side * 0.05, S.shoulder + 0.1 - len / 2, 0, o.sleeve === 'short' ? '' : 'b');
    if (o.sleeve === 'short') {
      put(sh, g(U[0] + 0.6 + e, 0.7, U[0] + 0.6 + e, cuff), side * 0.05, S.shoulder + 0.1 - len + 0.3, 0);
      tatters(K, sh, p, S.shoulder + 0.1 - len, (U[0] + 0.6 + e) / 2, nTat()); continue;
    }
    if (o.sleeve === 'long' && R === 'R' && (KIT.wear ?? 0.6) > 0.6 && rnd() < 0.6) {          // forearm sleeve ripped away at the elbow
      tatters(K, sh, p, S.shoulder + 0.1 - len, (U[0] + 0.35 + e) / 2, 3); continue;
    }
    const fl = o.sleeve === 'rolled' ? F[1] * 0.4 : F[1] - 0.2;
    shell(el, F[0] + 0.2 + e, fl, F[0] + 0.2 + e, p, 0, 0.2 - fl / 2, 0, 't');
    put(el, g(F[0] + 0.45 + e, o.sleeve === 'rolled' ? 1.0 : 0.8, F[0] + 0.45 + e, cuff), 0, 0.2 - fl + 0.3, 0);
    tatters(K, el, p, 0.2 - fl - 0.1, (F[0] + 0.45 + e) / 2, nTat());
  }
}
// trousers: pelvis, thighs, shins and a hem; len long | shorts; bottom is where the hem stops on the shin
function pants(K, p, o = {}) {
  const { J, P, T, N, put, g, sides } = K, e = o.e ?? 0.4, hem = o.hemP || p;
  shell(J.hips, P[0] + e, P[1] + 0.2, P[2] + e, p, 0, 0, 0, 'b');
  for (const [R, side] of sides) {
    const hip = J['hip' + R], kn = J['kn' + R];
    if (o.len === 'shorts') {
      const L = T[1] * 0.5;
      shell(hip, T[0] + e + 0.25, L + 0.4, T[0] + e + 0.25, p, 0, 0.4 - (L + 0.4) / 2, 0, 't');
      put(hip, g(T[0] + e + 0.45, 0.6, T[0] + e + 0.45, hem), 0, 0.4 - L - 0.1, 0);
      tatters(K, hip, p, 0.4 - L - 0.4, (T[0] + e + 0.45) / 2, nTat());
      if (o.stripe) put(hip, g(0.3, L + 0.2, 1.0, gear(o.stripe, { grime: .1 })), side * (T[0] / 2 + e / 2 + 0.3), 0.4 - L / 2, 0);
      continue;
    }
    shell(hip, T[0] + 0.05 + e, T[1] + 0.4, T[0] + 0.05 + e, p, 0, 0.4 - (T[1] + 0.4) / 2, 0, 'tb');
    const top = 0.3, bot = o.bottom ?? (-N[1] + 0.8);
    shell(kn, N[0] + 0.05 + e, top - bot, N[0] + 0.05 + e, p, 0, (top + bot) / 2, 0, 't');
    put(kn, g(N[0] + 0.35 + e, 0.7, N[0] + 0.35 + e, hem), 0, bot + 0.3, 0);
    tatters(K, kn, p, bot - 0.05, (N[0] + 0.35 + e) / 2, nTat());
    if (o.stripe) {                                                      // stripe down the outside seam
      put(hip, g(0.3, T[1] + 0.2, 0.8, gear(o.stripe, { grime: .1 })), side * ((T[0] + e) / 2 + 0.15), 0.3 - T[1] / 2, 0);
      put(kn, g(0.3, top - bot - 0.2, 0.8, gear(o.stripe, { grime: .2 })), side * ((N[0] + e) / 2 + 0.15), (top + bot) / 2, 0);
    }
    if (o.crease) {
      put(hip, g(0.25, T[1], 0.2, gear(o.crease, { grime: 0, outline: .2 })), 0, 0.2 - T[1] / 2, (T[0] + e) / 2 + 0.08);
      put(kn, g(0.25, top - bot - 0.4, 0.2, gear(o.crease, { grime: 0, outline: .2 })), 0, (top + bot) / 2, (N[0] + e) / 2 + 0.08);
    }
    if (o.cargo) {
      put(hip, g(0.7, 2.8, 2.5, o.cargo), side * (T[0] / 2 + e / 2 + 0.35), -T[1] * 0.45, 0);
      put(hip, g(0.85, 0.8, 2.7, o.cargo), side * (T[0] / 2 + e / 2 + 0.4), -T[1] * 0.45 + 1.6, 0);
      put(hip, g(0.4, 0.4, 0.3, gear(BLACK, { grime: 0 })), side * (T[0] / 2 + e / 2 + 0.8), -T[1] * 0.45 + 1.4, 0);
    }
  }
  if (o.backPockets) for (const sx of [-1, 1]) put(J.hips, g(1.9, 1.8, 0.25, o.backPockets), sx * 1.6, -0.2, -(P[2] + e) / 2 - 0.12);
}
function belt(K, c, buckle, o = {}) {
  const { J, P, put, g } = K, y = P[1] / 2 - 0.35, w = o.w ?? 1.0;
  put(J.hips, g(P[0] + 0.85, w, P[2] + 0.85, gear(c, { stitch: o.stitch, shine: o.shine, grime: .15 })), 0, y, 0);
  put(J.hips, g(1.5, w + 0.1, 0.35, gear(buckle, { noise: .15, grime: .05, shine: true })), 0, y, P[2] / 2 + 0.55);
  return y;
}
function sneakers(K, c, o = {}) {
  const { J, FT, N, put, g, sides, fz, toe } = K, sole = o.sole || '#e6e2d6', acc = o.accent || '#2c4f8f';
  for (const [R] of sides) {
    const an = J['an' + R], kn = J['kn' + R];
    put(an, g(FT[0] + 0.6, 0.6, FT[2] + 0.7, gear(sole, { grime: .45, noise: .05 })), 0, -1.1, fz);
    mud(an, FT[0] + 0.75, FT[2] + 0.8, -0.9, fz, Math.round(2 + 5 * (KIT.wear ?? 0.6)));
    put(an, g(FT[0] + 0.4, 1.5, FT[2] + 0.35, gear(c, { stitch: 'rgba(0,0,0,.25)', scuff: true, grime: .2 })), 0, -0.15, fz);
    put(an, g(FT[0] + 0.5, 0.75, 1.4, gear(sole, { grime: .35 })), 0, -0.5, toe - 0.55);                  // rubber toe bumper
    put(an, g(FT[0] + 0.55, 0.65, 2.4, gear(acc, { grime: .15 })), 0, -0.25, fz - 0.3);                   // side stripe
    put(an, g(1.4, 0.3, 2.4, laces('#f1efe8')), 0, 0.62, fz + 0.5, -0.12);
    put(an, g(FT[0] + 0.55, 1.4, 1.3, gear(c, { grime: .2 })), 0, 0.2, fz - FT[2] / 2 + 0.6);             // heel collar
    put(an, g(0.9, 0.8, 0.3, gear(acc, { grime: .1 })), 0, 0.95, fz - FT[2] / 2 + 0.05);                  // heel tab
    if (o.sock) put(kn, g(N[0] + 0.35, 1.1, N[0] + 0.35, rib(o.sock)), 0, -N[1] + 0.9, 0);
  }
}
function laces(c) {
  return (x, W, H) => { x.fillStyle = '#1b1a18'; x.fillRect(0, 0, W, H); x.fillStyle = c; for (let y = 2; y < H; y += 5) x.fillRect(1, y, W - 2, 2); x.fillStyle = 'rgba(0,0,0,.5)'; x.fillRect(0, 0, 1, H); x.fillRect(W - 1, 0, 1, H); };
}
// leather boots on a thick sole; h is how far the shaft climbs the shin
function boots(K, c, o = {}) {
  const { J, FT, N, put, g, sides, fz, toe } = K, h = o.h ?? 2.6, dark = o.dark || hex(c, 0.7);
  for (const [R] of sides) {
    const an = J['an' + R], kn = J['kn' + R];
    put(an, g(FT[0] + 0.8, 0.75, FT[2] + 0.8, gear(o.sole || '#141210', { noise: .05, grime: .1 })), 0, -1.03, fz);
    mud(an, FT[0] + 0.95, FT[2] + 0.95, -0.7, fz, Math.round(3 + 6 * (KIT.wear ?? 0.6)));
    mud(kn, N[0] + 0.75, N[0] + 0.75, -N[1] + 0.6, 0, Math.round(1 + 3 * (KIT.wear ?? 0.6)));
    put(an, g(FT[0] + 0.85, 0.3, FT[2] + 0.85, (x, W, H) => { x.fillStyle = '#0a0908'; x.fillRect(0, 0, W, H); x.fillStyle = '#26231f'; for (let i = 2; i < W; i += 5) x.fillRect(i, 0, 2, H); }), 0, -1.25, fz);
    put(an, g(FT[0] + 0.5, 1.6, FT[2] + 0.4, gear(c, { scuff: true, grime: .2, shine: o.shine })), 0, 0.0, fz);
    put(an, g(FT[0] + 0.65, 1.05, 1.5, gear(dark, { scuff: true, grime: .1, shine: o.shine })), 0, -0.2, toe - 0.6);
    put(an, g(FT[0] + 0.7, 1.2, 1.3, gear(dark, { grime: .1 })), 0, -0.1, fz - FT[2] / 2 + 0.5);
    put(kn, g(N[0] + 0.6, h, N[0] + 0.6, gear(o.shaft || c, { stitch: 'rgba(0,0,0,.35)', grime: .15, shine: o.shine })), 0, -N[1] + h / 2 - 0.1, 0);
    put(kn, g(N[0] + 0.85, 0.6, N[0] + 0.85, gear(o.collar || dark, { grime: .05 })), 0, -N[1] + h - 0.1, 0);
    if (o.band) put(kn, g(N[0] + 0.75, 0.7, N[0] + 0.75, gear(o.band, { grime: .2 })), 0, -N[1] + h - 0.9, 0);
    if (o.laces) {
      put(an, g(1.4, 0.35, 2.3, gear(dark, { grime: .1 })), 0, 0.85, fz + 0.5, -0.25);
      put(kn, g(1.1, h - 0.4, 0.3, laces(o.laces)), 0, -N[1] + h / 2 - 0.2, N[0] / 2 + 0.4);
    }
    if (o.loop) put(kn, g(0.8, 1.0, 0.35, gear(dark, { grime: 0 })), 0, -N[1] + h + 0.1, -N[0] / 2 - 0.4);
  }
}
function dressShoes(K, c) {
  const { J, FT, put, g, sides, fz, toe } = K;
  for (const [R] of sides) {
    const an = J['an' + R];
    put(an, g(FT[0] + 0.45, 0.4, FT[2] + 0.55, gear('#100f0e', { grime: .1 })), 0, -1.2, fz);
    put(an, g(FT[0] + 0.3, 1.5, FT[2] + 0.3, gear(c, { shine: true, grime: .1, stitch: 'rgba(255,255,255,.08)' })), 0, -0.25, fz);
    put(an, g(FT[0] + 0.2, 0.9, 1.1, gear(hex(c, 1.15), { shine: true, grime: .05 })), 0, -0.55, toe - 0.25);   // polished toe
    put(an, g(FT[0] + 0.25, 0.7, 1.3, gear('#0c0b0a', { grime: 0 })), 0, -1.15, fz - FT[2] / 2 + 0.6);         // heel block
    put(an, g(1.3, 0.25, 1.6, laces('#2a2622')), 0, 0.55, fz + 0.6, -0.1);
  }
}
// hair for heads without a full hat; under = only the back and sides that show below a hat
function hair(K, c, style = 'short', under = false) {
  const { J, hw, hh, hd, put, g } = K, p = gear(c, { strands: true, grime: .05, noise: .05, bloodK: .4 });
  const k = J.head;
  if (!under && style !== 'buzz') put(k, g(hw + 0.5, 1.1, hd + 0.5, p), 0, hh + 0.35, 0);
  if (style === 'buzz') put(k, g(hw + 0.25, 0.45, hd + 0.25, gear(c, { noise: .2, grime: 0, bloodK: .3 })), 0, hh + 0.12, 0);
  if (style === 'messy' && !under) for (let i = 0; i < 6; i++) put(k, g(1.2 + rnd(), 0.6 + rnd() * 0.6, 1.2 + rnd(), p), (rnd() - 0.5) * hw * 0.8, hh + 0.9, (rnd() - 0.5) * hd * 0.8, 0, rnd(), (rnd() - 0.5) * 0.5);
  const long = style === 'long', bh = long ? hh + 1.6 : style === 'buzz' ? hh * 0.4 : hh * 0.55;
  put(k, g(hw + (style === 'buzz' ? 0.25 : 0.5), bh, long ? 0.9 : 0.6, p), 0, hh - bh / 2 + 0.1, -hd / 2 - (long ? 0.35 : 0.2));
  for (const sx of [-1, 1]) {
    const sl = long ? hh * 0.75 : style === 'buzz' ? hh * 0.2 : hh * 0.3;
    put(k, g(style === 'buzz' ? 0.3 : 0.55, sl, hd * 0.7, p), sx * (hw / 2 + 0.2), hh - sl / 2 + 0.1, -hd * 0.12);
  }
  if (!under && style !== 'buzz') put(k, g(hw + 0.5, 0.9, 0.55, p), 0, hh - 0.25, hd / 2 + 0.15);   // fringe
  if (style === 'bun') { put(k, g(2.4, 2.2, 2.2, p), 0, hh + 0.4, -hd / 2 - 0.7); put(k, g(2.0, 0.5, 1.8, gear('#222', { grime: 0 })), 0, hh - 0.6, -hd / 2 - 0.5); }
  if (style === 'pony') {
    put(k, g(1.5, 0.6, 1.3, gear('#d23c6e', { grime: 0 })), 0, hh - 0.6, -hd / 2 - 0.45);
    put(k, g(1.6, 4.6, 1.3, p), 0, hh - 3.1, -hd / 2 - 0.9, 0.25);
  }
}
function watch(K, R, strap, dial = '#e8e6dc', o = {}) {
  const { J, F, put, g } = K, side = R === 'R' ? -1 : 1, el = J['el' + R], y = -F[1] + 0.85;
  put(el, g(F[0] + 0.45, 0.75, F[0] + 0.45, gear(strap, { grime: .05, stitch: o.stitch, shine: o.metal })), 0, y, 0);
  put(el, g(0.45, o.big ? 1.3 : 1.05, o.big ? 1.6 : 1.3, gear(o.bezel || STEEL2, { shine: true, grime: 0, clean: true })), side * (F[0] / 2 + 0.3), y, 0);
  put(el, g(0.15, o.big ? 0.95 : 0.75, o.big ? 1.2 : 0.95, (x, W, H) => {
    x.fillStyle = dial; x.fillRect(0, 0, W, H); x.fillStyle = o.hands || '#1a1a1a';
    if (o.digital) { x.fillRect(W * 0.2, H * 0.4, W * 0.6, 2); x.fillRect(W * 0.2, H * 0.6, W * 0.4, 2); }
    else { x.fillRect(W / 2 - 1, H * 0.2, 2, H * 0.32); x.fillRect(W / 2, H / 2 - 1, W * 0.3, 2); }
  }), side * (F[0] / 2 + 0.56), y, 0);
}
function gloves(K, c, o = {}) {
  const { J, HD, put, g, sides } = K;
  for (const [R] of sides) {
    const wr = J['wr' + R], len = o.fingerless ? HD[1] * 0.55 : HD[1] + 0.15;
    put(wr, g(HD[0] + 0.35, len, HD[2] + 0.35, gear(c, { stitch: 'rgba(0,0,0,.3)', scuff: true, grime: .2 })), 0, -len / 2 + 0.05, 0.1);
    put(wr, g(HD[0] + 0.7, o.gauntlet ? 2.0 : 0.9, HD[2] + 0.7, gear(o.cuff || hex(c, 0.8), { grime: .1 })), 0, o.gauntlet ? 0.8 : 0.35, 0.1);
  }
}
function glasses(K, frame = '#1b1a18', lens = 'rgba(170,200,220,.0)') {
  const { J, hw, hh, hd, put, g } = K, y = hh * 0.47;
  const rim = (x, W, H) => { x.fillStyle = frame; x.fillRect(0, 0, W, H); x.clearRect(2, 2, W - 4, H - 4); if (lens !== 'none') { x.fillStyle = 'rgba(255,255,255,.35)'; x.fillRect(3, 3, 2, 2); } };
  for (const sx of [-1, 1]) {
    put(J.head, g(1.7, 1.2, 0.15, rim, true), sx * 1.25, y, hd / 2 + 0.2);
    put(J.head, g(0.2, 0.25, hd * 0.6, gear(frame, { grime: 0, clean: true })), sx * (hw / 2 + 0.1), y + 0.35, hd * 0.2);
  }
  put(J.head, g(0.9, 0.25, 0.2, gear(frame, { grime: 0, clean: true })), 0, y + 0.3, hd / 2 + 0.2);
}
function lanyard(K, strap, card = WHITE, text = 'ID') {
  const { J, th, td, put, g } = K;
  for (const sx of [-1, 1]) put(J.spine, g(0.35, th * 0.48, 0.2, gear(strap, { grime: .05 })), sx * 1.05, th * 0.78, td / 2 + 0.42, 0, 0, sx * 0.28);
  put(J.spine, g(0.6, 0.5, 0.3, gear(STEEL2, { grime: 0, shine: true })), 0, th * 0.55, td / 2 + 0.45);
  put(J.spine, g(1.7, 2.2, 0.25, gear(card, { grime: .05, text, textColor: '#2b3a55', stitch: 'rgba(40,60,90,.4)' })), 0, th * 0.42, td / 2 + 0.48);
}
function tie(K, c, o = {}) {
  const { J, th, td, put, g } = K, z = td / 2 + 0.45;
  for (const sx of [-1, 1]) put(J.spine, g(1.7, 1.0, 0.3, gear(o.collar || WHITE, { grime: .1 })), sx * 0.95, th - 0.35, z - 0.1, 0, 0, -sx * 0.5);   // collar points
  put(J.spine, g(1.0, 0.9, 0.45, gear(c, o.p || { grime: .05 })), 0, th - 0.65, z);                                                           // knot
  put(J.spine, g(1.0, th * 0.35, 0.3, gear(c, o.p || { grime: .05 })), 0, th * 0.73, z, 0, 0, o.skew || 0);
  put(J.spine, g(1.35, th * 0.28, 0.3, gear(c, o.p || { grime: .1 })), (o.skew || 0) * -2, th * 0.42, z, 0, 0, o.skew || 0);
}
function buttons(K, n, c, z, x = 0) {
  const { J, th, put, g } = K;
  put(J.spine, g(0.9, th * 0.92, 0.2, gear(K.placket || '#00000000', { grime: .1, outline: .3 })), x, th * 0.5, z);
  for (let i = 0; i < n; i++) put(J.spine, g(0.45, 0.45, 0.25, gear(c, { grime: 0, noise: .02, clean: true })), x, th * (0.12 + 0.8 * i / (n - 1)), z + 0.12);
}
function patch(bg, fg, text, o = {}) { return gear(bg, { text, textColor: fg, stitch: 'rgba(255,255,255,.25)', grime: .1, ...o }); }

// ---------- one kit per outfit ----------
const KITS = {
  civilian(J, S) {
    seed = 101; const K = kitCtx(J, S), { put, g, th, td, T, P } = K;
    const TEE = gear('#c46f6a', { stitch: 'rgba(80,20,20,.35)', noise: .07 });
    const JEANS = gear('#3d5a85', { denim: true, stitch: 'rgba(214,160,60,.55)', noise: .07 });
    shirt(K, TEE, { sleeve: 'short', hem: 1.1, cuff: gear('#b35f5a', { weave: true }) });
    put(J.spine, g(2.8, 0.55, 0.3, rib('#a9524d')), 0, th - 0.35, td / 2 + 0.3);                    // crew neck rib
    put(J.spine, g(3.2, 2.6, 0.2, (x, W, H) => { gear('#c46f6a', { grime: .1, noise: .05 })(x, W, H, 'pz'); x.fillStyle = '#efe4c9'; x.fillRect(W * .2, H * .25, W * .6, H * .5); x.fillStyle = '#2a4f7a'; x.fillRect(W * .3, H * .35, W * .4, H * .3); }), 0, th * 0.66, (td + 0.4) / 2 + 0.08);  // faded print
    pants(K, JEANS, { backPockets: gear('#36507a', { denim: true, stitch: 'rgba(214,160,60,.6)' }), hemP: gear('#34507a', { denim: true }) });
    put(J.hipL, g(T[0] - 0.6, 1.3, 0.25, (x, W, H) => { x.fillStyle = '#7e95b5'; x.fillRect(0, 0, W, H); x.fillStyle = '#efeadf'; for (let y = 2; y < H; y += 3) x.fillRect(2, y, W - 4, 1); }), 0, -T[1] + 1.0, (T[0] + 0.45) / 2 + 0.1);   // ripped knee
    for (const sx of [-1, 1]) put(J.hips, g(0.25, 1.6, 0.2, gear('#2c4466', { grime: 0 })), sx * 2.3, -0.1, (P[2] + 0.4) / 2 + 0.06, 0, 0, sx * 0.4);   // front pocket seams
    belt(K, '#5b3b24', '#b9a46a', { stitch: 'rgba(0,0,0,.3)' });
    sneakers(K, '#e9e6de', { accent: '#2f5e9e' });
    watch(K, 'L', '#5a3a22', '#efe9d6', { stitch: 'rgba(255,230,190,.3)' });
  },
  construction(J, S) {
    seed = 202; const K = kitCtx(J, S), { put, g, tw, th, td, P, hw, hh, hd, BR } = K;
    shirt(K, gear('#5d7899', { weave: true, stitch: 'rgba(20,30,50,.4)' }), { sleeve: 'rolled', cuff: gear('#506a8a', { weave: true }) });
    // hi-vis vest with silver reflective bands, open at the front
    const VEST = gear('#f06a12', { weave: true, stitch: 'rgba(90,30,0,.4)', noise: .05 });
    shell(J.spine, tw + 1.0, th * 0.62, td + 1.0, VEST, 0, th * 0.68, 0, 'b');
    shell(J.spine, tw - 0.2, th * 0.4, td + 0.6, VEST, 0, th * 0.24, 0, 't');
    const BAND = gear('#c9c9bf', { mid: 'rgba(255,255,255,.35)', grime: .2, noise: .03 });
    put(J.spine, g(tw + 1.25, 0.8, td + 1.25, BAND), 0, th * 0.55, 0);
    put(J.spine, g(tw + 0.4, 0.8, td + 0.85, BAND), 0, th * 0.18, 0);
    for (const sx of [-1, 1]) for (const zz of [1, -1]) put(J.spine, g(0.85, th * 0.6, 0.25, BAND), sx * 2.3, th * 0.7, zz * (td / 2 + 0.62));
    put(J.spine, g(0.45, th * 0.95, 0.3, gear('#3a2a1c', { grime: .1 })), 0, th * 0.5, td / 2 + 0.6);                 // open zip line
    pants(K, gear('#41597c', { denim: true, stitch: 'rgba(214,160,60,.55)' }), { bottom: -K.N[1] + 2.4 });
    // leather tool belt: hammer in its loop, nail pouches with tools poking out, tape measure
    const by = belt(K, '#7a4b28', '#9a9c95', { w: 1.3, stitch: 'rgba(255,220,170,.3)' });
    for (const sx of [-1, 1]) {
      put(J.hips, g(2.3, 2.7, 1.5, gear('#8a5a32', { stitch: 'rgba(255,220,170,.35)', scuff: true })), sx * 2.5, by - 1.4, P[2] / 2 + 0.6, 0, sx * -0.3);
      put(J.hips, g(0.4, 1.4, 0.4, gear(sx < 0 ? '#c2352b' : '#e3c13b', { grime: .1 })), sx * 2.3, by - 0.2, P[2] / 2 + 0.6);   // screwdriver handles
    }
    put(J.hips, g(0.65, 3.6, 0.65, gear('#a87a46', { weave: true, grime: .2 })), P[0] / 2 + 0.75, by - 1.4, -0.4);              // hammer handle
    put(J.hips, g(0.9, 0.85, 2.4, gear('#4a4c4a', { shine: true, grime: .2 })), P[0] / 2 + 0.75, by + 0.4, -0.4);
    put(J.hips, g(1.5, 1.5, 0.9, gear('#e3c13b', { grime: .2, text: '25', textColor: '#111' })), -P[0] / 2 + 0.4, by - 1.0, -P[2] / 2 - 0.6);
    boots(K, '#9a6a36', { h: 2.8, laces: '#e0c48a', loop: true, dark: '#6a4422', sole: '#2a1f16' });
    gloves(K, '#c9a46a', { cuff: '#a8844e' });
    // hard hat: shell, crown ridge, all-round brim with a front peak, a sticker
    const HAT = gear('#f2c418', { shine: true, grime: .25, noise: .04, bloodK: .6 });
    put(J.head, g(hw + 1.2, 2.6, hd + 1.2, HAT), 0, BR + 1.55, 0);
    put(J.head, g(hw - 0.2, 0.9, hd + 0.4, HAT), 0, BR + 3.2, 0);
    put(J.head, g(1.0, 0.7, hd + 1.5, gear('#e0b410', { shine: true, grime: .1 })), 0, BR + 3.55, 0);
    put(J.head, g(hw + 1.9, 0.4, hd + 1.9, HAT), 0, BR + 0.35, 0);
    put(J.head, g(hw + 0.4, 0.4, 1.4, HAT), 0, BR + 0.3, hd / 2 + 1.5, 0.12);
    put(J.head, g(0.2, 1.1, 1.6, patch('#2a5a9a', '#fff', 'OK')), hw / 2 + 0.68, BR + 1.6, 0.3);
  },
  doctor(J, S) {
    seed = 303; const K = kitCtx(J, S), { put, g, tw, th, td, P, T, hw, hh, hd } = K;
    pants(K, gear('#4d4f55', { weave: true, noise: .05 }), { crease: '#5d6068' });
    dressShoes(K, '#1d1a18');
    // white lab coat over a blue shirt and tie: open V at the neck, lapels, pockets, tails over the thighs
    const COAT = gear('#e8e7e1', { stitch: 'rgba(120,120,110,.4)', noise: .05, grime: .35 });
    shirt(K, COAT, { e: 1.0, sleeve: 'long', cuff: gear('#dcdbd4', { weave: true }) });
    const fz = (td + 1.0) / 2;
    put(J.spine, g(2.4, th * 0.4, 0.2, gear('#8fb0d8', { weave: true, grime: .05 })), 0, th * 0.8, fz + 0.05);
    tie(K, '#7a2430', { p: { diag: ['#c9a24a', 8, 2], grime: .05 }, collar: '#9cbbe0' });
    for (const sx of [-1, 1]) put(J.spine, g(1.1, th * 0.45, 0.35, COAT), sx * 1.55, th * 0.76, fz + 0.15, 0, 0, sx * 0.32);   // lapels
    put(J.spine, g(0.25, th * 0.55, 0.15, gear('#9a9a92', { grime: .1 })), 0, th * 0.27, fz + 0.06);
    for (let i = 0; i < 3; i++) put(J.spine, g(0.45, 0.45, 0.25, gear('#cfcfc6', { clean: true, grime: 0 })), 0.35, th * (0.1 + 0.15 * i), fz + 0.12);
    put(J.spine, g(1.9, 1.6, 0.25, COAT), -2.0, th * 0.68, fz + 0.12);                                                         // breast pocket
    [['#1c3f8a', -2.5], ['#c22a2a', -2.0], ['#1a1a1a', -1.6]].forEach(([c, x]) => put(J.spine, g(0.3, 1.2, 0.3, gear(c, { grime: 0, shine: true, clean: true })), x, th * 0.68 + 1.0, fz + 0.12));
    put(J.spine, g(1.8, 0.65, 0.2, patch('#f4f2ea', '#24324e', 'DR. HALE', { stitch: null })), 2.0, th * 0.74, fz + 0.12);
    shell(J.hips, P[0] + 1.3, P[1] + 0.4, P[2] + 1.3, COAT, 0, 0, 0, 'tb');
    for (const [R, side] of K.sides) {
      shell(J['hip' + R], T[0] + 1.3, 4.2, T[0] + 1.3, COAT, side * 0.15, 0.6 - 2.1, 0, 't');
      put(J['hip' + R], g(2.0, 1.6, 0.25, COAT), side * 0.2, -1.4, (T[0] + 1.3) / 2 + 0.12);
    }
    // stethoscope round the neck, steel chest piece and ear tips
    const TUBE = gear('#1f1f22', { shine: true, grime: 0, bloodK: .5 });
    put(J.spine, g(tw * 0.62, 0.45, 0.45, TUBE), 0, th + 0.15, -td / 2 + 0.6);
    for (const sx of [-1, 1]) put(J.spine, g(0.45, th * 0.42, 0.45, TUBE), sx * 1.75, th * 0.78, fz + 0.4, 0, 0, sx * 0.08);
    put(J.spine, g(1.3, 1.3, 0.55, gear(STEEL2, { shine: true, grime: 0, clean: true })), 1.95, th * 0.52, fz + 0.5);
    put(J.spine, g(0.7, 0.7, 0.5, gear(STEEL2, { shine: true, grime: 0, clean: true })), -1.95, th * 0.55, fz + 0.45);
    glasses(K, '#3a2c22');
    watch(K, 'L', '#a9aba4', '#f1efe6', { metal: true });
  },
  farmer(J, S) {
    seed = 404; const K = kitCtx(J, S), { put, g, tw, th, td, P, hw, hh, hd, BR } = K;
    shirt(K, gear('#a32c24', { plaid: ['#3b1410', '#d06a5c'], noise: .06 }), { sleeve: 'rolled', cuff: gear('#8e251f', { plaid: ['#3b1410', '#d06a5c'] }) });
    // denim bib overalls: bib with pocket, straps over the shoulders crossing at the back, brass hardware
    const DEN = gear('#3e5f8c', { denim: true, stitch: 'rgba(214,160,60,.6)', noise: .07 });
    pants(K, DEN, { e: 0.6, bottom: -K.N[1] + 2.4, backPockets: gear('#38567f', { denim: true, stitch: 'rgba(214,160,60,.6)' }) });
    put(J.spine, g(tw - 1.6, th * 0.52, 0.45, DEN), 0, th * 0.5, td / 2 + 0.45);
    put(J.spine, g(tw - 0.4, th * 0.3, td + 0.8, DEN), 0, th * 0.14, 0);
    put(J.spine, g(2.2, 1.8, 0.25, gear('#3a5884', { denim: true, stitch: 'rgba(214,160,60,.7)' })), -0.5, th * 0.55, td / 2 + 0.75);
    put(J.spine, g(0.35, 1.5, 0.35, gear('#e2c13a', { clean: true, grime: 0 })), -0.9, th * 0.55 + 1.1, td / 2 + 0.75);        // pencil
    for (const sx of [-1, 1]) {
      put(J.spine, g(1.0, th * 0.42, 0.3, DEN), sx * 2.15, th * 0.85, td / 2 + 0.55);
      put(J.spine, g(1.0, 0.7, td + 1.2, DEN), sx * 2.15, th + 0.05, 0);
      put(J.spine, g(1.0, th * 0.95, 0.3, DEN), sx * 1.4, th * 0.55, -td / 2 - 0.45, 0, 0, sx * 0.4);
      put(J.spine, g(1.1, 1.0, 0.35, gear(BRASS, { shine: true, grime: .1 })), sx * 2.15, th * 0.72, td / 2 + 0.75);
      put(J.hips, g(0.3, 0.8, 0.8, gear(BRASS, { shine: true, grime: .1 })), sx * (P[0] / 2 + 0.45), P[1] / 2 - 0.1, 0);
    }
    put(J.spine, g(2.6, 1.1, 0.4, gear('#2b4d86', { dots: '#e8e4da', grime: .1 })), 0, th - 0.3, td / 2 + 0.5);            // bandana knot
    put(J.hips, g(2.0, 1.4, 0.8, gear('#c9a46a', { stitch: 'rgba(0,0,0,.3)', grime: .2 })), -1.6, -0.6, -(P[2] + 0.6) / 2 - 0.3, 0.2);   // glove in the back pocket
    boots(K, '#5a3a22', { h: 3.0, laces: '#c9a46a', loop: true, sole: '#1c140e' });
    // straw hat: woven crown, leather band, wide brim
    const STRAW = gear('#cfae62', { straw: true, noise: .04, grime: .25, bloodK: .5 });
    put(J.head, g(hw + 0.6, 2.4, hd + 0.6, STRAW), 0, BR + 1.6, 0);
    put(J.head, g(hw - 0.6, 0.6, hd - 0.4, STRAW), 0, BR + 3.0, 0);
    put(J.head, g(hw + 0.75, 0.7, hd + 0.75, gear('#5a3a22', { stitch: 'rgba(255,220,170,.3)', grime: .1 })), 0, BR + 0.85, 0);
    put(J.head, g(hw + 5.2, 0.35, hd + 5.2, STRAW), 0, BR + 0.35, 0);
    watch(K, 'R', '#3a2a1c', '#e8dfc6');
  },
  firefighter(J, S) {
    seed = 505; const K = kitCtx(J, S), { put, g, tw, th, td, P, T, N, F, U, hw, hh, hd, BR } = K;
    const TAN = gear('#8f7c4c', { weave: true, stitch: 'rgba(40,30,10,.45)', noise: .06 });
    const REFL = gear('#d8cc3a', { mid: 'rgba(225,228,224,.95)', grime: .25, noise: .03 });
    // turnout coat with reflective bands, storm flap and clasps, raised collar, tails over the trousers
    pants(K, TAN, { e: 1.0, bottom: -N[1] + 3.6 });
    for (const R of ['R', 'L']) put(J['kn' + R], g(N[0] + 1.25, 0.8, N[0] + 1.25, REFL), 0, -N[1] + 4.4, 0);
    shirt(K, TAN, { e: 1.1, sleeve: 'long', cuff: gear('#3a3324', { knit: true }) });
    put(J.spine, g(tw + 1.35, 0.85, td + 1.35, REFL), 0, th * 0.62, 0);
    put(J.spine, g(tw + 0.35, 0.85, td + 1.0, REFL), 0, th * 0.08, 0);
    put(J.spine, g(tw + 0.8, 1.5, td + 1.9, gear('#7d6b40', { weave: true })), 0, th + 0.4, -0.1);
    put(J.spine, g(1.5, th * 0.95, 0.45, TAN), 0.4, th * 0.48, (td + 1.1) / 2 + 0.15);
    for (let i = 0; i < 4; i++) put(J.spine, g(0.9, 0.4, 0.3, gear('#2a2a2a', { shine: true, grime: 0, clean: true })), 0.4, th * (0.15 + 0.22 * i), (td + 1.1) / 2 + 0.45);
    for (const [R, side] of K.sides) {
      put(J['el' + R], g(F[0] + 1.35, 0.8, F[0] + 1.35, REFL), 0, -F[1] * 0.45, 0);
      put(J['sh' + R], g(U[0] + 1.25, 0.8, U[0] + 1.25, REFL), 0, S.shoulder - U[1] + 0.9, 0);
      shell(J['hip' + R], T[0] + 1.5, 3.4, T[0] + 1.5, TAN, side * 0.15, 0.6 - 1.7, 0, 't');
      put(J['hip' + R], g(T[0] + 1.65, 0.8, T[0] + 1.65, REFL), side * 0.15, -2.4, 0);
    }
    shell(J.hips, P[0] + 1.6, P[1] + 0.5, P[2] + 1.6, TAN, 0, 0, 0, 'tb');
    // right-angle flashlight clipped to the chest, air pack on the back with harness straps
    put(J.spine, g(1.2, 2.2, 1.0, gear('#e2b51a', { grime: .2 })), -2.3, th * 0.72, (td + 1.1) / 2 + 0.55);
    put(J.spine, g(0.9, 0.9, 0.3, gear('#f4f0d0', { grime: 0, clean: true })), -2.3, th * 0.8, (td + 1.1) / 2 + 1.1);
    put(J.spine, g(tw - 1.4, th * 0.85, 0.5, gear('#1d1c1a', { grime: .1 })), 0, th * 0.5, -(td + 1.1) / 2 - 0.25);
    put(J.spine, g(2.7, th * 0.72, 2.7, gear('#b8bab2', { shine: true, grime: .2, mid: 'rgba(216,204,58,.8)' })), 0, th * 0.55, -(td + 1.1) / 2 - 1.8);
    put(J.spine, g(1.0, 1.2, 1.0, gear('#2a2a2a', { shine: true, grime: 0 })), 0, th * 0.55 - th * 0.36 - 0.4, -(td + 1.1) / 2 - 1.8);
    for (const sx of [-1, 1]) put(J.spine, g(1.0, th * 0.85, 0.4, gear('#1d1c1a', { stitch: 'rgba(255,255,255,.15)', grime: .1 })), sx * 2.5, th * 0.55, (td + 1.1) / 2 + 0.25);
    put(J.hipL, g(2.4, 2.6, 1.3, gear('#1b1b1b', { shine: true, grime: .15 })), 1.9, -0.6, 0.6);                               // face mask on its hip clip
    put(J.hipL, g(1.8, 1.4, 0.2, gear('#7a8a8e', { shine: true, grime: 0, clean: true })), 2.0, -0.3, 1.3);
    boots(K, '#151413', { h: 4.6, band: '#d8cc3a', shine: true, sole: '#0b0a09', collar: '#1d1c1a' });
    gloves(K, '#6b4a2a', { gauntlet: true, cuff: '#4a3420' });
    // traditional helmet: dome with combs, long rear brim, leather front shield with company number
    const HELM = gear('#1f1e1c', { shine: true, grime: .25, noise: .04 });
    put(J.head, g(hw + 1.3, 2.8, hd + 1.3, HELM), 0, BR + 1.7, 0);
    put(J.head, g(hw - 0.4, 0.8, hd + 0.2, HELM), 0, BR + 3.4, 0);
    for (const sx of [-1.4, 0, 1.4]) put(J.head, g(0.6, 0.55, hd + 1.4, HELM), sx, BR + 3.8, 0);
    put(J.head, g(hw + 2.2, 0.4, hd + 2.2, HELM), 0, BR + 0.3, 0);
    put(J.head, g(hw + 1.6, 0.4, 2.8, HELM), 0, BR + 0.0, -hd / 2 - 1.8, -0.25);
    put(J.head, g(2.6, 2.6, 0.4, patch('#7a1a14', '#e8c35a', '12', { stitch: 'rgba(232,195,90,.6)' })), 0, BR + 1.9, hd / 2 + 0.9, -0.08);
    put(J.head, g(1.1, 0.9, 0.6, gear(BRASS, { shine: true, grime: .1 })), 0, BR + 3.4, hd / 2 + 0.5);
  },
  hoodie(J, S) {
    seed = 606; const K = kitCtx(J, S), { put, g, tw, th, td, hw, hh, hd } = K;
    const HOOD = gear('#3b3d42', { stitch: 'rgba(0,0,0,.35)', noise: .07 });
    shirt(K, HOOD, { e: 0.8, sleeve: 'long', hem: 1.2, hemP: rib('#33353a'), cuff: rib('#33353a') });
    const fz = (td + 0.8) / 2;
    put(J.spine, g(tw - 1.8, th * 0.26, 0.4, gear('#36383d', { stitch: 'rgba(0,0,0,.4)' })), 0, th * 0.27, fz + 0.12);         // kangaroo pocket
    put(J.spine, g(3.6, 1.0, 0.2, patch('#36383d', '#b9b2a2', 'GRAYHAVEN', { stitch: null })), 0, th * 0.72, fz + 0.06);
    for (const sx of [-1, 1]) {
      put(J.spine, g(0.25, 2.9, 0.25, gear('#d9d4c6', { grime: .2 })), sx * 1.1, th - 1.5, fz + 0.3);
      put(J.spine, g(0.4, 0.6, 0.4, gear(STEEL2, { grime: 0, clean: true })), sx * 1.1, th - 3.1, fz + 0.3);
    }
    // hood up: back, crown and sides with a thick front rim around the face
    put(J.head, g(hw + 1.4, hh + 0.6, 1.0, HOOD), 0, hh / 2 + 0.3, -hd / 2 - 0.5);
    put(J.head, g(hw + 1.4, 1.0, hd + 1.4, HOOD), 0, hh + 0.55, -0.1);
    for (const sx of [-1, 1]) {
      put(J.head, g(0.7, hh + 0.2, hd + 0.4, HOOD), sx * (hw / 2 + 0.35), hh / 2 + 0.2, 0);
      put(J.head, g(0.9, hh, 0.6, gear('#323438', { knit: true })), sx * (hw / 2 + 0.3), hh / 2 + 0.2, hd / 2 + 0.35);
    }
    put(J.head, g(hw + 1.6, 1.0, 0.6, gear('#323438', { knit: true })), 0, hh + 0.4, hd / 2 + 0.35);
    pants(K, gear('#232428', { denim: true, stitch: 'rgba(160,160,160,.3)' }), { backPockets: gear('#202125', { denim: true, stitch: 'rgba(160,160,160,.3)' }) });
    sneakers(K, '#1c1c1e', { sole: '#efece4', accent: '#efece4' });
    watch(K, 'L', '#111', '#1a1a1a', { digital: true, hands: '#9be05a', bezel: '#222' });
  },
  jogger(J, S) {
    seed = 707; const K = kitCtx(J, S), { put, g, tw, th, td, U, N, hw, hh, hd } = K;
    const TANK = gear('#2aa39a', { stitch: 'rgba(255,255,255,.35)', noise: .05 });
    shirt(K, TANK, { sleeve: 'none', e: 0.35 });
    for (const sx of [-1, 1]) {
      put(J.spine, g(0.3, th * 0.95, td + 0.6, gear('#f0efe8', { grime: .1 })), sx * (tw / 2 + 0.25), th * 0.5, 0);                 // white side piping
      put(J.spine, g(1.4, th * 0.3, td + 0.5, TANK), sx * 2.6, th * 0.85, 0);                                                     // racerback straps
    }
    pants(K, gear('#1c1c20', { weave: true }), { len: 'shorts', stripe: '#c9cbc4', hemP: gear('#26262b', {}) });
    put(J.hips, g(2.6, 0.5, 0.2, patch('#1c1c20', '#c9cbc4', 'RUN', { stitch: null })), 2.0, -0.6, (K.P[2] + 0.4) / 2 + 0.12);
    sneakers(K, '#f2f0ea', { accent: '#ff3d7f', sole: '#ffffff', sock: '#f4f3ee' });
    // phone armband with earbud wire, chunky sports watch, terry headband and ponytail
    put(J.shR, g(U[0] + 0.6, 1.8, U[0] + 0.6, gear('#1b1b1d', { stitch: 'rgba(255,255,255,.2)', grime: .05 })), 0, S.shoulder - 2.6, 0);
    put(J.shR, g(0.35, 2.4, 1.3, gear('#0f1013', { shine: true, grime: 0, clean: true })), -(U[0] / 2 + 0.45), S.shoulder - 2.6, 0);
    put(J.spine, g(0.15, th * 0.7, 0.15, gear('#f2f2f2', { grime: 0, outline: .2, clean: true })), -2.4, th * 0.7, td / 2 + 0.4, 0, 0, 0.35);
    for (const sx of [-1, 1]) put(J.head, g(0.6, 0.6, 0.6, gear('#f6f6f6', { grime: 0, clean: true })), sx * (hw / 2 + 0.15), hh * 0.45, 0.3);
    watch(K, 'L', '#1b1b1d', '#c6f24a', { big: true, digital: true, bezel: '#2a2a2c' });
    put(J.head, g(hw + 0.55, 0.95, hd + 0.55, gear('#ff3d7f', { knit: true, grime: .1 })), 0, hh * 0.72, 0);
  },
  nurse(J, S) {
    seed = 808; const K = kitCtx(J, S), { put, g, tw, th, td, hw, hh, hd } = K;
    const SCRUB = gear('#7fa9cf', { weave: true, stitch: 'rgba(30,60,90,.35)', noise: .05 });
    shirt(K, SCRUB, { sleeve: 'short', hem: 1.4, cuff: gear('#719bc2', { weave: true }) });
    const fz = (td + 0.4) / 2;
    for (const sx of [-1, 1]) put(J.spine, g(0.5, 2.0, 0.25, gear('#6a93b9', { grime: .05 })), sx * 0.7, th - 1.0, fz + 0.1, 0, 0, sx * 0.4);   // V-neck trim
    put(J.spine, g(2.0, 1.8, 0.25, gear('#7aa3c9', { stitch: 'rgba(30,60,90,.45)' })), 1.9, th * 0.62, fz + 0.12);                       // chest pocket
    [['#1c3f8a', 1.4], ['#222', 1.9], ['#e0c13a', 2.3]].forEach(([c, x]) => put(J.spine, g(0.3, 1.1, 0.3, gear(c, { grime: 0, shine: true, clean: true })), x, th * 0.62 + 1.0, fz + 0.12));
    put(J.spine, g(0.4, 0.4, 0.3, gear(STEEL2, { shine: true, clean: true, grime: 0 })), -1.9, th * 0.75, fz + 0.15);                   // fob watch pin
    put(J.spine, g(1.2, 1.2, 0.35, gear(STEEL2, { shine: true, grime: 0, clean: true })), -1.9, th * 0.75 - 1.0, fz + 0.2);
    put(J.spine, g(0.95, 0.95, 0.15, gear('#f2efe2', { grime: 0, clean: true })), -1.9, th * 0.75 - 1.0, fz + 0.4);
    pants(K, SCRUB, { e: 0.55 });
    put(J.hips, g(0.25, 1.6, 0.25, gear('#eae6d8', { grime: .1 })), 0.5, K.P[1] / 2 - 1.2, (K.P[2] + 0.55) / 2 + 0.15);              // drawstring
    put(J.hips, g(0.25, 1.4, 0.25, gear('#eae6d8', { grime: .1 })), -0.4, K.P[1] / 2 - 1.1, (K.P[2] + 0.55) / 2 + 0.15);
    sneakers(K, '#f1f0eb', { accent: '#c7cdd2', sole: '#f7f7f4' });
    lanyard(K, '#2c5aa0', WHITE, 'RN');
    // surgical mask pulled down under the chin, ear loops; hair up in a bun
    put(J.head, g(hw * 0.72, 1.5, 0.4, gear('#a9d3e0', { weave: true, grime: .05 })), 0, 0.55, hd / 2 + 0.25);
    for (const sx of [-1, 1]) put(J.head, g(0.15, 0.15, hd * 0.55, gear('#f2f2f2', { grime: 0, outline: .2 })), sx * (hw / 2 + 0.08), hh * 0.25, hd * 0.2, sx * 0.3);
    watch(K, 'R', '#e8e6df', '#ffffff');
  },
  office(J, S) {
    seed = 909; const K = kitCtx(J, S), { put, g, tw, th, td, P } = K;
    const SHIRT = gear('#ecebe6', { weave: true, stitch: 'rgba(120,120,110,.3)', noise: .04, grime: .35 });
    shirt(K, SHIRT, { sleeve: 'long', cuff: gear('#e2e1db', { stitch: 'rgba(120,120,110,.4)' }) });
    put(J.hipR, g(3.2, 1.2, P[2] + 0.9, SHIRT), 0.4, 1.3, 0, 0, 0, 0.12);                                                  // one side pulled loose
    const fz = (td + 0.4) / 2;
    for (let i = 0; i < 5; i++) put(J.spine, g(0.4, 0.4, 0.2, gear('#d8d6cc', { grime: 0, clean: true })), 0.25, th * (0.1 + 0.15 * i), fz + 0.1);
    put(J.spine, g(1.8, 1.6, 0.2, SHIRT), -1.9, th * 0.66, fz + 0.1);
    put(J.spine, g(0.3, 1.1, 0.3, gear('#1a1a1a', { shine: true, clean: true, grime: 0 })), -2.3, th * 0.66 + 0.8, fz + 0.15);
    tie(K, '#7e1f28', { p: { diag: ['#c7b48a', 7, 2], grime: .05 }, skew: 0.12 });
    pants(K, gear('#3b3d43', { weave: true, noise: .04 }), { crease: '#4b4e55' });
    belt(K, '#141312', '#c9cbc4', { shine: true });
    dressShoes(K, '#16110e');
    glasses(K, '#111');
    watch(K, 'L', '#b8964a', '#f3ecd6', { metal: true, bezel: '#c8a656' });
  },
  patient(J, S) {
    seed = 1010; const K = kitCtx(J, S), { put, g, tw, th, td, T, N, F, HD, hw, hh, hd, P } = K;
    const GOWN = gear('#cfdde4', { dots: '#5c86a8', stitch: 'rgba(60,90,110,.3)', noise: .04, grime: .35 });
    shirt(K, GOWN, { e: 0.8, sleeve: 'short', cuff: gear('#b9cdd8', { weave: true }) });
    shell(J.hips, P[0] + 1.0, P[1] + 0.4, P[2] + 1.0, GOWN, 0, 0, 0, 'tb');
    for (const [R, side] of K.sides) shell(J['hip' + R], T[0] + 1.0, T[1] * 0.78, T[0] + 1.0, GOWN, side * 0.1, 0.6 - T[1] * 0.39, 0, 't');
    for (const y of [0.85, 0.5, 0.15]) {                                                                                  // ties down the open back
      put(J.spine, g(1.4, 0.35, 0.3, gear('#b9cdd8', { grime: .1 })), 0, th * y, -(td + 0.8) / 2 - 0.12);
      put(J.spine, g(0.3, 1.0, 0.25, gear('#b9cdd8', { grime: .1 })), 0.5, th * y - 0.5, -(td + 0.8) / 2 - 0.15, 0, 0, 0.3);
    }
    put(J.spine, g(tw - 0.6, 0.6, td + 1.2, gear('#b9cdd8', { weave: true })), 0, th + 0.1, 0);
    // hospital wristband, IV cannula taped to the back of the hand, grip socks
    put(J.elL, g(F[0] + 0.4, 0.6, F[0] + 0.4, (x, W, H) => { x.fillStyle = '#f4f4f0'; x.fillRect(0, 0, W, H); x.fillStyle = '#222'; for (let i = 3; i < W - 3; i += 2) if (rnd() < 0.6) x.fillRect(i, 1, 1, H - 2); }), 0, -F[1] + 0.8, 0);
    put(J.wrR, g(1.4, 1.2, 0.3, gear('#efefe8', { grime: .1, weave: true })), 0, -1.0, HD[2] / 2 + 0.2);
    put(J.wrR, g(0.5, 0.5, 0.4, gear('#7fc2e8', { grime: 0, shine: true, clean: true })), 0.3, -0.9, HD[2] / 2 + 0.4);
    for (const R of ['R', 'L']) {
      put(J['an' + R], g(K.FT[0] + 0.35, 1.9, K.FT[2] + 0.3, gear('#8b8f92', { knit: true, grime: .3, dots: '#f2f2f2' })), 0, -0.45, K.fz);
      put(J['kn' + R], g(N[0] + 0.35, 1.4, N[0] + 0.35, rib('#7f8386')), 0, -N[1] + 0.5, 0);
    }
  },
  police(J, S) {
    seed = 1111; const K = kitCtx(J, S), { put, g, tw, th, td, P, U, hw, hh, hd, BR } = K;
    const NAVY = gear('#1f2a44', { weave: true, stitch: 'rgba(0,0,0,.4)', noise: .05 });
    shirt(K, NAVY, { sleeve: 'long', cuff: gear('#1b2540', { weave: true }) });
    const fz = (td + 0.4) / 2;
    buttons(K, 5, '#c9cbc4', fz + 0.02, 0);
    for (const sx of [-1, 1]) {
      put(J.spine, g(2.2, 1.9, 0.25, NAVY), sx * 2.0, th * 0.66, fz + 0.12);
      put(J.spine, g(2.35, 0.8, 0.35, gear('#1b2540', { stitch: 'rgba(255,255,255,.1)' })), sx * 2.0, th * 0.66 + 0.95, fz + 0.18);
    }
    put(J.spine, g(1.4, 1.7, 0.3, gear('#c9a648', { shine: true, grime: .05, bloodK: .3, text: '★', textColor: '#7a5e1e' })), 2.0, th * 0.66 + 2.0, fz + 0.2);   // badge
    put(J.spine, g(1.8, 0.5, 0.2, patch('#d8d6cc', '#1b2540', 'COOPER', { stitch: null })), -2.0, th * 0.66 + 1.65, fz + 0.15);
    tie(K, '#121725', { collar: '#1f2a44' });
    put(J.spine, g(0.9, 0.25, 0.3, gear('#c9a648', { shine: true, clean: true, grime: 0 })), 0, th * 0.55, td / 2 + 0.8);       // tie bar
    for (const [R, side] of K.sides) {
      put(J['sh' + R], g(1.2, 0.35, U[0] + 0.8, gear('#1b2540', { stitch: 'rgba(255,255,255,.15)' })), 0, S.shoulder + 0.15, 0);   // epaulettes
      put(J['sh' + R], g(0.25, 2.0, 1.7, patch('#2a3b66', '#e0c55a', 'POLICE', { textFace: side < 0 ? 'nx' : 'px' })), side * ((U[0] + 0.45) / 2 + 0.1), S.shoulder - 1.9, 0);
    }
    // radio with shoulder mic and coiled cord
    put(J.spine, g(1.1, 1.4, 0.7, gear('#151515', { grime: .05 })), -2.3, th * 0.86, fz + 0.4);
    put(J.spine, g(0.25, th * 0.5, 0.25, gear('#151515', { grime: 0 })), -2.9, th * 0.55, fz + 0.3, 0, 0, -0.1);
    pants(K, NAVY, { stripe: '#7e9bc9' });
    // duty belt: holster with pistol grip, magazine pouches, cuff case, flashlight, baton
    const by = belt(K, '#0f0f10', '#c9cbc4', { w: 1.5, shine: true });
    const BLK = gear('#111112', { shine: true, grime: .1 });
    put(J.hips, g(1.3, 3.2, 2.2, BLK), -P[0] / 2 - 0.7, by - 1.5, 0.2);
    put(J.hips, g(1.0, 1.8, 1.4, gear('#232323', { grime: 0 })), -P[0] / 2 - 0.7, by + 0.6, 0.0, -0.2);
    put(J.hips, g(2.0, 1.8, 0.8, BLK), 1.6, by - 0.6, P[2] / 2 + 0.85);
    put(J.hips, g(1.5, 1.2, 0.9, BLK), P[0] / 2 + 0.2, by - 0.4, -0.8);
    put(J.hips, g(0.8, 2.6, 0.8, BLK), P[0] / 2 + 0.6, by - 1.2, 1.0);
    put(J.hips, g(0.55, 5.2, 0.55, gear('#18181a', { shine: true, grime: .05 })), P[0] / 2 + 0.6, by - 2.6, -2.0, 0, 0, 0.1);
    boots(K, '#121212', { h: 2.4, shine: true, sole: '#0a0a0a' });
    // peaked cap: band, wide crown, gloss visor, badge
    put(J.head, g(hw + 0.85, 1.7, hd + 0.85, gear('#141414', { grime: .05, weave: true })), 0, BR + 0.95, 0);
    put(J.head, g(hw + 1.8, 1.0, hd + 2.0, gear('#1f2a44', { stitch: 'rgba(0,0,0,.4)', noise: .05 })), 0, BR + 2.2, 0.2);
    put(J.head, g(hw + 0.4, 0.3, 2.3, gear('#0b0b0b', { shine: true, grime: 0 })), 0, BR + 0.15, hd / 2 + 1.0, 0.25);
    put(J.head, g(1.3, 1.4, 0.3, gear('#c9a648', { shine: true, grime: 0, bloodK: .3 })), 0, BR + 1.4, hd / 2 + 0.55);
    watch(K, 'L', '#2a2a2a', '#f0eee6');
  },
  prisoner(J, S) {
    seed = 1212; const K = kitCtx(J, S), { put, g, tw, th, td, P, F } = K;
    const ORANGE = gear('#e0631e', { weave: true, stitch: 'rgba(80,30,0,.4)', noise: .06, grime: .35 });
    shirt(K, ORANGE, { sleeve: 'short', cuff: gear('#c85718', { weave: true }) });
    pants(K, ORANGE, { hemP: gear('#c85718', { weave: true }) });
    const fz = (td + 0.4) / 2;
    put(J.spine, g(tw + 0.6, 0.8, td + 1.0, gear('#cd5a1a', { weave: true })), 0, th + 0.05, 0);
    put(J.spine, g(2.0, 0.9, 0.2, gear('#ecebe4', { weave: true, grime: .2 })), 0, th - 0.5, fz + 0.06);                    // undershirt at the neck
    put(J.spine, g(0.4, th * 0.9, 0.25, gear('#8a3a10', { grime: .1 })), 0, th * 0.48, fz + 0.1);                           // front zip
    put(J.spine, g(2.6, 1.2, 0.25, patch('#f2efe6', '#111', '4471', { stitch: 'rgba(0,0,0,.25)' })), -2.0, th * 0.7, fz + 0.12);
    put(J.spine, g(tw - 0.6, 1.8, 0.25, patch('#e0631e', '#111', 'COUNTY JAIL', { textFace: 'nz', stitch: null })), 0, th * 0.72, -fz - 0.12);
    put(J.hipL, g(0.25, 1.4, 2.6, patch('#e0631e', '#111', 'DOC', { textFace: 'px', stitch: null })), (K.T[0] + 0.45) / 2 + 0.12, -2.0, 0);
    // white canvas slip-ons, a broken handcuff with a dangling chain on the right wrist
    sneakers(K, '#ecebe4', { accent: '#d6d4cc', sole: '#f2f1ec' });
    put(J.elR, g(F[0] + 0.75, 0.8, F[0] + 0.75, gear('#9a9c96', { shine: true, grime: .2 })), 0, -F[1] + 0.7, 0);
    for (let i = 0; i < 3; i++) put(J.elR, g(0.3, 0.75, 0.55, gear('#8d8f88', { shine: true, grime: .2 })), -(F[0] / 2 + 0.55), -F[1] + 0.3 - i * 0.65, 0, 0, i % 2 ? 1.57 : 0);
  },
  security(J, S) {
    seed = 1313; const K = kitCtx(J, S), { put, g, tw, th, td, P, U, hw, hh, hd, BR } = K;
    const SHIRT = gear('#a9c2da', { weave: true, stitch: 'rgba(30,50,70,.35)', noise: .05 });
    shirt(K, SHIRT, { sleeve: 'short', cuff: gear('#9bb4cc', { weave: true }) });
    const fz = (td + 0.4) / 2;
    buttons(K, 5, '#e8e6de', fz + 0.02, 0);
    for (const sx of [-1, 1]) put(J.spine, g(2.0, 1.7, 0.25, gear('#a3bcd4', { stitch: 'rgba(30,50,70,.45)' })), sx * 2.0, th * 0.64, fz + 0.12);
    put(J.spine, g(1.3, 1.5, 0.3, gear('#b9bbb4', { shine: true, grime: .05, bloodK: .3 })), 2.0, th * 0.64 + 1.8, fz + 0.2);
    put(J.spine, g(tw - 0.4, 1.6, 0.25, patch('#141414', '#f0f0ea', 'SECURITY', { textFace: 'nz', stitch: null })), 0, th * 0.75, -fz - 0.12);
    for (const [R, side] of K.sides) {
      put(J['sh' + R], g(1.2, 0.35, U[0] + 0.8, gear('#141414', { grime: .05 })), 0, S.shoulder + 0.15, 0);
      put(J['sh' + R], g(0.25, 1.6, 1.6, patch('#141414', '#d9c060', 'SEC', { textFace: side < 0 ? 'nx' : 'px' })), side * ((U[0] + 0.45) / 2 + 0.1), S.shoulder - 1.5, 0);
    }
    lanyard(K, '#141414', '#f2f0e6', 'STAFF');
    pants(K, gear('#18191b', { weave: true }), { crease: '#26282b' });
    const by = belt(K, '#111', '#b9bbb4', { w: 1.2, shine: true });
    put(J.hips, g(0.8, 3.0, 0.8, gear('#1a1a1a', { shine: true, grime: .05 })), -P[0] / 2 - 0.4, by - 1.4, 0.6);          // flashlight
    put(J.hips, g(1.1, 1.8, 0.7, gear('#151515', { grime: .05 })), -P[0] / 2 - 0.4, by - 0.9, -1.0);                      // radio
    put(J.hips, g(0.3, 1.4, 0.3, gear('#151515', { grime: 0 })), -P[0] / 2 - 0.4, by + 0.7, -1.2);
    put(J.hips, g(1.0, 1.0, 0.25, gear('#b9bbb4', { shine: true, clean: true, grime: 0 })), P[0] / 2 + 0.3, by - 0.8, 1.0); // key ring
    [['#c9a648', 0], ['#b9bbb4', 0.5], ['#8a6a3a', -0.5]].forEach(([c, dx]) => put(J.hips, g(0.35, 1.1, 0.2, gear(c, { shine: true, clean: true, grime: 0 })), P[0] / 2 + 0.3 + dx * 0.6, by - 1.8, 1.05, 0, 0, dx * 0.6));
    boots(K, '#141414', { h: 1.8, shine: true, laces: '#2a2a2a', sole: '#0a0a0a' });
    // black ball cap with SECURITY across the front
    put(J.head, g(hw + 0.6, 2.2, hd + 0.6, gear('#141414', { stitch: 'rgba(255,255,255,.12)', noise: .05 })), 0, BR + 1.2, 0);
    put(J.head, g(hw - 1.2, 1.2, 0.2, patch('#141414', '#f0f0ea', 'SECURITY', { stitch: null })), 0, BR + 1.2, (hd + 0.6) / 2 + 0.05);
    put(J.head, g(0.7, 0.4, 0.7, gear('#141414', { grime: 0 })), 0, BR + 2.45, 0);
    put(J.head, g(hw - 0.4, 0.35, 2.6, gear('#111', { stitch: 'rgba(255,255,255,.15)', grime: .05 })), 0, BR + 0.2, hd / 2 + 1.2, 0.12);
    watch(K, 'L', '#1a1a1a', '#1a1a1a', { digital: true, hands: '#e8e6dc' });
  },
  survivor(J, S) {
    seed = 1414; const K = kitCtx(J, S), { put, g, tw, th, td, P, F, U, hw, hh, hd, BR } = K;
    // waxed field jacket with pockets, duct-taped elbow and magazines taped to the forearm as armour
    const JACK = gear('#5d5236', { weave: true, stitch: 'rgba(20,16,6,.5)', scuff: true, noise: .07 });
    shirt(K, JACK, { e: 0.9, sleeve: 'long', hem: 1.3, cuff: gear('#4d4430', { weave: true }) });
    const fz = (td + 0.9) / 2;
    put(J.spine, g(tw + 1.0, 1.4, td + 1.8, gear('#4d4430', { weave: true })), 0, th + 0.35, -0.1);
    put(J.spine, g(0.45, th * 0.95, 0.3, gear('#2a2418', { grime: .1 })), 0.4, th * 0.48, fz + 0.1);
    for (const sx of [-1, 1]) {
      put(J.spine, g(2.2, 2.0, 0.4, JACK), sx * 2.0, th * 0.66, fz + 0.15);
      put(J.spine, g(2.35, 0.8, 0.5, gear('#51472f', { stitch: 'rgba(20,16,6,.5)' })), sx * 2.0, th * 0.66 + 1.05, fz + 0.2);
      put(J.spine, g(2.4, 2.0, 0.4, JACK), sx * 2.0, th * 0.18, fz + 0.15);
    }
    const TAPE = gear('#a6a8a2', { shine: true, grime: .3, noise: .1 });
    put(J.elR, g(F[0] + 1.3, 1.2, F[0] + 1.3, TAPE), 0, -0.2, 0);
    put(J.elL, g(F[0] + 1.3, F[1] * 0.55, F[0] + 1.3, gear('#c23b2b', { vstripe: ['#f0e3b0', 6, 3], shine: true, grime: .2, text: 'NEWS', textFace: 'px', textColor: '#111' })), 0, -F[1] * 0.45, 0);
    for (const y of [-F[1] * 0.25, -F[1] * 0.7]) put(J.elL, g(F[0] + 1.5, 0.6, F[0] + 1.5, TAPE), 0, y, 0);
    put(J.spine, g(tw * 0.78, 1.0, td + 1.9, gear('#9c2a22', { dots: '#ecdcc2', grime: .2 })), 0, th + 0.3, 0);                  // bandana
    put(J.spine, g(2.6, 1.8, 0.35, gear('#9c2a22', { dots: '#ecdcc2', grime: .2 })), 0, th - 0.9, fz + 0.6);
    pants(K, gear('#7a6c4c', { weave: true, stitch: 'rgba(30,24,10,.45)' }), { bottom: -K.N[1] + 2.4, cargo: gear('#6e6144', { weave: true, stitch: 'rgba(30,24,10,.5)' }) });
    const by = belt(K, '#3a2a1c', '#8d8f88', { stitch: 'rgba(255,220,170,.25)' });
    put(J.hips, g(0.9, 3.0, 0.6, gear('#5a3a22', { stitch: 'rgba(255,220,170,.3)', scuff: true })), -P[0] / 2 - 0.4, by - 1.6, 0.7);   // knife sheath
    put(J.hips, g(0.7, 1.4, 0.7, gear('#1b1a18', { weave: true, grime: .1 })), -P[0] / 2 - 0.4, by + 0.5, 0.7);
    boots(K, '#6a4a2c', { h: 2.8, laces: '#b8392b', loop: true, sole: '#1c140e' });
    gloves(K, '#232220', { fingerless: true });
    watch(K, 'R', '#1a1a1a', '#9fb39a', { digital: true });
    // backpack: main bag, lid, front pocket, side bottle, straps, bedroll and a frying pan
    const PACK = gear('#3f4a3a', { weave: true, stitch: 'rgba(0,0,0,.4)', scuff: true });
    const bz = -fz - 1.7;
    put(J.spine, g(tw - 0.8, th * 0.72, 3.2, PACK), 0, th * 0.52, bz);
    put(J.spine, g(tw - 0.5, 1.4, 3.6, gear('#36402f', { weave: true, stitch: 'rgba(0,0,0,.4)' })), 0, th * 0.9, bz + 0.1);
    put(J.spine, g(tw - 2.4, th * 0.34, 1.0, PACK), 0, th * 0.42, bz - 2.0);
    put(J.spine, g(1.3, 2.6, 1.3, gear('#4b6a8a', { shine: true, grime: .2 })), tw / 2 - 0.1, th * 0.38, bz);
    put(J.spine, g(tw + 0.8, 2.2, 2.2, gear('#6a5a3a', { weave: true, grime: .3 })), 0, th * 0.08, bz + 0.1);
    for (const sx of [-1, 1]) put(J.spine, g(tw + 1.0, 0.5, 2.4, gear('#2a2418', { grime: .1 })), 0, th * 0.08 + sx * 0.6, bz + 0.1);
    put(J.spine, g(0.35, 2.4, 2.4, gear('#2a2a2a', { shine: true, grime: .3 })), -tw / 2 + 0.2, th * 0.55, bz);
    put(J.spine, g(0.35, 0.5, 2.0, gear('#3a2a1c', { grime: .2 })), -tw / 2 + 0.2, th * 0.55 + 2.0, bz);
    for (const sx of [-1, 1]) {
      put(J.spine, g(1.0, th * 0.9, 0.4, gear('#2a2c26', { stitch: 'rgba(255,255,255,.12)', grime: .1 })), sx * 2.4, th * 0.55, fz + 0.3);
      put(J.spine, g(1.0, 0.6, td + 1.6, gear('#2a2c26', { grime: .1 })), sx * 2.4, th + 0.1, -0.3);
    }
    put(J.spine, g(tw * 0.62, 0.45, 0.3, gear('#2a2c26', { grime: .1 })), 0, th * 0.66, fz + 0.5);
    put(J.head, g(hw + 0.6, 2.6, hd + 0.6, gear('#2e3a2c', { knit: true, grime: .2, bloodK: .6 })), 0, BR + 1.65, 0);    // beanie
    put(J.head, g(hw - 0.8, 0.8, hd - 0.8, gear('#2e3a2c', { knit: true, grime: .1 })), 0, BR + 3.3, 0);
    put(J.head, g(hw + 0.9, 1.2, hd + 0.9, rib('#273225')), 0, BR + 0.7, 0);
  },
};
