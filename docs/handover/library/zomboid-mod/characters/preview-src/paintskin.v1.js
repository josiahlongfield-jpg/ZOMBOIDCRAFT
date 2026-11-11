// ---------- a hand-painted style skin, generated at up to 16 texture pixels per skin pixel ----------
// Standard 64x64 layout, so it fits the same body (and any Minecraft skin slot). Moody comic style:
// mottled, dirty skin with cel-shaded form, inked features and wounds, bruising and lividity.
const TONES = {
  zombie: {
    pale:  { skin: '#a3a593', shade: '#626a5c', light: '#c4c4b0', livid: '#5e4f6e', rot: '#55613a', hair: '#4b443b' },
    olive: { skin: '#868f72', shade: '#4d5643', light: '#a8ae90', livid: '#584866', rot: '#3f4a26', hair: '#2e2620' },
    dark:  { skin: '#5f5143', shade: '#352c24', light: '#7c6c5c', livid: '#3f3043', rot: '#3a3a20', hair: '#1c1814' },
  },
  survivor: {
    pale:  { skin: '#d9b39a', shade: '#9c7462', light: '#ecd0bc', livid: '#8a6070', rot: '#8a7a5a', hair: '#4a3324' },
    olive: { skin: '#b8875f', shade: '#7a5638', light: '#d4a67e', livid: '#6e4a50', rot: '#7a6a4a', hair: '#2a1d15' },
    dark:  { skin: '#6e4632', shade: '#3e2618', light: '#8e6046', livid: '#4a2c30', rot: '#4a3a2a', hair: '#141010' },
  },
};
const INK = 'rgba(22,14,12,.88)';
function paintSkin(N, kind, toneKey, dmg) {
  seed = 4100 + dmg * 17 + (kind === 'zombie' ? 0 : 900) + toneKey.length;
  const cv = document.createElement('canvas'); cv.width = cv.height = 64 * N;
  const c = cv.getContext('2d'), T = TONES[kind][toneKey], Z = kind === 'zombie', D = Z ? dmg : 0;
  const reg = (x, y, w, h, fn) => { c.save(); c.translate(x * N, y * N); c.scale(N, N); c.beginPath(); c.rect(0, 0, w, h); c.clip(); fn(w, h); c.restore(); };
  // ---- unit-space brushes (1 unit = 1 skin pixel) ----
  const soft = (x, y, r, col, a = 1) => { const g = c.createRadialGradient(x, y, 0, x, y, r); g.addColorStop(0, col); g.addColorStop(1, 'rgba(0,0,0,0)'); c.globalAlpha = a; c.fillStyle = g; c.fillRect(x - r, y - r, r * 2, r * 2); c.globalAlpha = 1; };
  const ell = (x, y, rx, ry, col, rot = 0) => { c.beginPath(); c.ellipse(x, y, rx, ry, rot, 0, Math.PI * 2); c.fillStyle = col; c.fill(); };
  const ring = (x, y, rx, ry, w = 0.09, col = INK, rot = 0, a0 = 0, a1 = Math.PI * 2) => { c.beginPath(); c.ellipse(x, y, rx, ry, rot, a0, a1); c.lineWidth = w; c.strokeStyle = col; c.stroke(); };
  const line = (pts, w = 0.09, col = INK) => { c.beginPath(); c.moveTo(pts[0], pts[1]); for (let i = 2; i < pts.length; i += 2) c.lineTo(pts[i], pts[i + 1]); c.lineWidth = w; c.strokeStyle = col; c.lineCap = 'round'; c.lineJoin = 'round'; c.stroke(); };
  const curve = (x0, y0, cx, cy, x1, y1, w = 0.09, col = INK) => { c.beginPath(); c.moveTo(x0, y0); c.quadraticCurveTo(cx, cy, x1, y1); c.lineWidth = w; c.strokeStyle = col; c.lineCap = 'round'; c.stroke(); };
  const blobPath = (x, y, rx, ry, jag = 0.35, n = 14) => { c.beginPath(); for (let i = 0; i <= n; i++) { const a = i / n * Math.PI * 2, k = 1 + (rnd() - 0.5) * jag; c[i ? 'lineTo' : 'moveTo'](x + Math.cos(a) * rx * k, y + Math.sin(a) * ry * k); } c.closePath(); };
  // mottled skin with cel-shaded edges; lividity pools toward the bottom of a zombie limb
  function skin(w, h, o = {}) {
    c.fillStyle = T.skin; c.fillRect(0, 0, w, h);
    for (let i = 0; i < w * h * 0.5; i++) soft(rnd() * w, rnd() * h, 0.6 + rnd() * 1.8, rnd() < 0.5 ? T.light : T.shade, 0.18 + rnd() * 0.12);
    if (Z) for (let i = 0; i < w * h * 0.12; i++) soft(rnd() * w, rnd() * h, 0.5 + rnd() * 1.4, rnd() < 0.5 ? T.livid : T.rot, 0.2 + rnd() * 0.2);
    if (o.livid) { const g = c.createLinearGradient(0, h * (1 - o.livid), 0, h); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, Z ? 'rgba(70,40,80,.55)' : 'rgba(90,50,40,.2)'); c.fillStyle = g; c.fillRect(0, 0, w, h); }
    if (o.edges) for (const x of o.edges) { const g = c.createLinearGradient(x - 0.9, 0, x + 0.9, 0); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(0.5, 'rgba(10,8,12,.32)'); g.addColorStop(1, 'rgba(0,0,0,0)'); c.fillStyle = g; c.fillRect(x - 0.9, 0, 1.8, h); }
    // pores and grime specks
    for (let i = 0; i < w * h * 3; i++) { c.fillStyle = rnd() < 0.6 ? 'rgba(20,14,10,.18)' : 'rgba(255,245,230,.08)'; c.fillRect(rnd() * w, rnd() * h, 0.07, 0.07); }
    for (let i = 0; i < w * h * (Z ? 0.25 : 0.06); i++) soft(rnd() * w, rnd() * h, 0.4 + rnd() * 1.2, 'rgba(40,30,18,.9)', 0.25);
  }
  function vein(x, y, a, len, depth = 3) {
    let px = x, py = y; c.beginPath(); c.moveTo(px, py);
    for (let i = 0; i < 6; i++) { a += (rnd() - 0.5) * 0.7; px += Math.cos(a) * len / 6; py += Math.sin(a) * len / 6; c.lineTo(px, py); if (depth && rnd() < 0.3) vein(px, py, a + (rnd() - 0.5) * 1.6, len * 0.5, depth - 1); }
    c.lineWidth = 0.05 + depth * 0.025; c.strokeStyle = Z ? 'rgba(52,34,70,.65)' : 'rgba(80,90,130,.25)'; c.lineCap = 'round'; c.stroke();
  }
  // an open wound: torn dark rim, wet red flesh, muscle streaks, optional bone, inked edge and a drip
  function wound(x, y, rx, ry, o = {}) {
    blobPath(x, y, rx * 1.35, ry * 1.4, 0.5); c.fillStyle = 'rgba(60,20,30,.55)'; c.fill();             // bruised surround
    blobPath(x, y, rx, ry, 0.45); c.fillStyle = '#5a1410'; c.fill();
    c.save(); blobPath(x, y, rx * 0.8, ry * 0.75, 0.4); c.clip();
    c.fillStyle = '#8e2a24'; c.fillRect(x - rx, y - ry, rx * 2, ry * 2);
    for (let i = 0; i < 8; i++) line([x - rx + rnd() * rx * 2, y - ry, x - rx + rnd() * rx * 2, y + ry], 0.08, 'rgba(170,60,50,.6)');
    if (o.bone) { ell(x, y, rx * 0.55, ry * 0.25, '#ddd3b8', o.boneRot || 0); ring(x, y, rx * 0.55, ry * 0.25, 0.07, INK, o.boneRot || 0); soft(x - rx * 0.2, y - ry * 0.05, rx * 0.25, 'rgba(255,255,240,.7)', 0.6); }
    if (o.fat) for (let i = 0; i < 4; i++) ell(x + (rnd() - 0.5) * rx, y + (rnd() - 0.5) * ry, 0.18, 0.12, '#cdb36a');
    soft(x + rx * 0.2, y + ry * 0.3, rx * 0.6, 'rgba(30,5,4,.9)', 0.6);
    c.restore();
    for (let i = 0; i < 3; i++) soft(x - rx * 0.3 + rnd() * rx * 0.4, y - ry * 0.3, 0.15, 'rgba(255,220,210,.9)', 0.5);   // wet glints
    blobPath(x, y, rx, ry, 0.45); c.lineWidth = 0.1; c.strokeStyle = INK; c.stroke();
    if (o.drip !== false) for (let i = 0; i < 2; i++) { const dx = x + (rnd() - 0.5) * rx, L = 0.8 + rnd() * 2.2; line([dx, y + ry * 0.7, dx + (rnd() - 0.5) * 0.2, y + ry + L], 0.16, 'rgba(70,10,8,.85)'); ell(dx, y + ry + L, 0.12, 0.16, 'rgba(70,10,8,.9)'); }
  }
  const scratch = (x, y, a, L) => { for (let k = 0; k < 3; k++) { const ox = x + k * 0.35 * Math.sin(a), oy = y - k * 0.35 * Math.cos(a); line([ox, oy, ox + Math.cos(a) * L, oy + Math.sin(a) * L], 0.12, 'rgba(110,20,16,.85)'); line([ox, oy, ox + Math.cos(a) * L, oy + Math.sin(a) * L], 0.04, 'rgba(30,6,4,.9)'); } };
  function bite(x, y, s = 1) {
    soft(x, y, 1.4 * s, 'rgba(70,30,60,.8)', 0.6);
    for (const [cy, sg] of [[-0.45, 1], [0.45, -1]]) for (let i = -3; i <= 3; i++) { const tx = x + i * 0.28 * s, ty = y + cy * s + sg * Math.abs(i) * 0.07 * s; ell(tx, ty, 0.11 * s, 0.17 * s, '#4a0c09'); ring(tx, ty, 0.11 * s, 0.17 * s, 0.04); }
  }
  const rot = (x, y, r) => { soft(x, y, r, T.rot, 0.8); soft(x, y, r * 0.5, 'rgba(25,22,10,.9)', 0.7); for (let i = 0; i < 6; i++) ell(x + (rnd() - 0.5) * r, y + (rnd() - 0.5) * r, 0.08, 0.08, 'rgba(200,190,120,.6)'); };

  // ================= head: right 0-8 | front 8-16 | left 16-24 | back 24-32 =================
  reg(0, 8, 32, 8, (w, h) => {
    skin(w, h, { edges: [8, 16, 24], livid: Z ? 0.25 : 0 });
    const cx = 12;
    // form: brow shadow, eye sockets, cheekbones, hollow cheeks, jaw shadow
    soft(cx, 2.9, 3.6, 'rgba(20,14,16,.45)', 0.55);
    soft(cx - 2.6, 4.7, 0.9, T.light, 0.6); soft(cx + 2.6, 4.7, 0.9, T.light, 0.6);
    soft(cx - 2.4, 5.6, 1.1, 'rgba(15,10,12,.8)', Z ? 0.55 : 0.25); soft(cx + 2.4, 5.6, 1.1, 'rgba(15,10,12,.8)', Z ? 0.55 : 0.25);
    soft(cx, 7.8, 3.8, 'rgba(10,8,8,.8)', 0.35);
    // forehead creases
    for (const y of [1.5, 2.05]) curve(cx - 2.1, y + 0.1, cx, y - 0.25, cx + 2.1, y + 0.1, 0.05, 'rgba(25,15,12,.45)');
    // eye sockets and eyes
    for (const s of [-1, 1]) {
      const ex = cx + s * 1.72, ey = 3.85;
      soft(ex, ey, 1.35, Z ? 'rgba(40,20,45,.95)' : 'rgba(70,40,40,.6)', Z ? 0.85 : 0.35);
      if (Z && D >= 2 && s > 0) {                                                              // gouged-out eye
        blobPath(ex, ey, 0.75, 0.5, 0.3); c.fillStyle = '#1a0605'; c.fill(); soft(ex, ey + 0.2, 0.4, '#6a1410', 0.8);
        blobPath(ex, ey, 0.75, 0.5, 0.3); c.lineWidth = 0.1; c.strokeStyle = INK; c.stroke();
        line([ex, ey + 0.5, ex + 0.1, ey + 2.6], 0.15, 'rgba(70,10,8,.9)');
        continue;
      }
      ell(ex, ey, 0.82, 0.42, Z ? '#c9c6a0' : '#e8e2d6');
      if (Z) for (let i = 0; i < 4; i++) line([ex + s * (0.3 + rnd() * 0.45), ey + (rnd() - 0.5) * 0.4, ex + s * 0.15, ey], 0.03, 'rgba(150,40,40,.7)');
      ell(ex - s * 0.05, ey + 0.02, 0.3, 0.3, Z ? '#9aa198' : (toneKey === 'pale' ? '#5d7a8f' : '#5a3a22'));
      soft(ex - s * 0.05, ey, 0.32, Z ? 'rgba(235,235,215,.8)' : 'rgba(0,0,0,0)', 0.6);         // milky film
      ell(ex - s * 0.05, ey + 0.02, Z ? 0.06 : 0.13, Z ? 0.06 : 0.13, '#0d0a0a');
      if (!Z) ell(ex - s * 0.05 + 0.1, ey - 0.08, 0.06, 0.06, '#ffffff');
      ring(ex, ey, 0.82, 0.42, 0.07);
      curve(ex - 0.95, ey - 0.15, ex, ey - 0.75, ex + 0.95, ey - 0.15, 0.13);                  // heavy upper lid
      curve(ex - 0.7, ey + 0.5, ex, ey + 0.82, ex + 0.75, ey + 0.45, 0.05, 'rgba(25,15,12,.55)');  // bags
      // brows: thick, low, inked
      line([ex - s * 0.95 + (s < 0 ? 0 : 0), 2.95 + (s * 0.05), ex + s * 0.1, 2.7, ex + s * 0.95, 2.9], 0.3, Z ? 'rgba(60,52,45,.75)' : T.hair);
      line([ex - 0.95, 2.82, ex, 2.58, ex + 0.95, 2.78], 0.05);
    }
    // nose: side shadow, bridge highlight, nostrils, inked underside
    if (Z && D >= 2) {
      blobPath(cx, 5.0, 0.55, 0.45, 0.3); c.fillStyle = '#1a0806'; c.fill(); ell(cx, 4.95, 0.1, 0.32, '#cfc6aa');
      blobPath(cx, 5.0, 0.55, 0.45, 0.3); c.lineWidth = 0.1; c.strokeStyle = INK; c.stroke();
    } else {
      soft(cx + 0.45, 4.6, 0.6, 'rgba(15,10,10,.7)', 0.55); line([cx, 3.7, cx + 0.05, 4.9], 0.16, 'rgba(255,245,230,.25)');
      ell(cx - 0.32, 5.25, 0.17, 0.11, '#1a0e0c'); ell(cx + 0.32, 5.25, 0.17, 0.11, '#1a0e0c');
      curve(cx - 0.6, 5.1, cx, 5.55, cx + 0.6, 5.1, 0.08); line([cx + 0.35, 3.9, cx + 0.5, 4.9], 0.05, 'rgba(25,15,12,.5)');
    }
    // mouth
    if (Z) {
      blobPath(cx, 6.35, 1.25, 0.55, 0.25); c.fillStyle = '#160605'; c.fill();
      for (let i = 0; i < 8; i++) { if (rnd() < 0.2) continue; const tx = cx - 1.05 + i * 0.3; c.fillStyle = rnd() < 0.3 ? '#9a8a5a' : '#cbb987'; c.fillRect(tx, 5.92, 0.24, 0.32 + rnd() * 0.1); c.strokeStyle = INK; c.lineWidth = 0.035; c.strokeRect(tx, 5.92, 0.24, 0.36); }
      for (let i = 0; i < 6; i++) { if (rnd() < 0.3) continue; const tx = cx - 0.8 + i * 0.3; c.fillStyle = '#b8a674'; c.fillRect(tx, 6.5, 0.24, 0.3); c.strokeStyle = INK; c.lineWidth = 0.035; c.strokeRect(tx, 6.5, 0.24, 0.3); }
      blobPath(cx, 6.35, 1.25, 0.55, 0.25); c.lineWidth = 0.12; c.strokeStyle = INK; c.stroke();
      curve(cx - 1.3, 5.85, cx, 5.55, cx + 1.3, 5.85, 0.2, 'rgba(95,90,100,.8)');               // grey cracked lips
      line([cx + 1.0, 6.6, cx + 1.1, 7.6, cx + 1.0, 8.0], 0.18, 'rgba(70,10,8,.85)');            // drool of blood
      line([cx - 0.6, 6.85, cx - 0.65, 7.9], 0.12, 'rgba(70,10,8,.75)');
      if (D >= 1) {                                                                             // torn cheek showing molars
        const tx0 = cx - 2.55, ty = 6.1;
        blobPath(tx0, ty, 0.75, 0.5, 0.4); c.fillStyle = '#3a0b08'; c.fill();
        for (let i = 0; i < 3; i++) { c.fillStyle = '#bfae7c'; c.fillRect(tx0 - 0.5 + i * 0.32, ty - 0.2, 0.26, 0.3); }
        for (let i = 0; i < 5; i++) line([tx0 - 0.6 + i * 0.3, ty - 0.45, tx0 - 0.6 + i * 0.3, ty + 0.45], 0.03, 'rgba(160,60,50,.7)');
        blobPath(tx0, ty, 0.75, 0.5, 0.4); c.lineWidth = 0.1; c.strokeStyle = INK; c.stroke();
      }
    } else {
      curve(cx - 1.0, 6.25, cx, 6.45, cx + 1.0, 6.2, 0.1); soft(cx, 6.0, 0.8, 'rgba(90,40,40,.6)', 0.35); soft(cx, 6.65, 0.7, 'rgba(255,240,230,.5)', 0.3);
      for (let i = 0; i < 160; i++) { c.fillStyle = 'rgba(30,20,16,.35)'; c.fillRect(cx - 2.6 + rnd() * 5.2, 5.8 + rnd() * 2.1, 0.06, 0.06); }   // stubble
    }
    curve(cx - 0.6, 7.25, cx, 7.5, cx + 0.6, 7.25, 0.05, 'rgba(25,15,12,.5)');                  // chin
    curve(cx - 1.8, 5.6, cx - 1.5, 6.4, cx - 1.25, 6.5, 0.05, 'rgba(25,15,12,.45)'); curve(cx + 1.8, 5.6, cx + 1.5, 6.4, cx + 1.25, 6.5, 0.05, 'rgba(25,15,12,.45)');   // nasolabial folds
    // ears on the side faces
    for (const [ex, torn] of [[3.6, false], [20.4, Z && D >= 1]]) {
      ell(ex, 4.3, 0.95, 1.55, T.shade); soft(ex, 4.3, 0.9, T.light, 0.4);
      ring(ex, 4.3, 0.95, 1.55, 0.1); ring(ex + 0.1, 4.2, 0.5, 0.95, 0.06, INK, 0, -1.2, 2.2); ell(ex + 0.1, 4.5, 0.2, 0.3, 'rgba(20,10,10,.8)');
      if (torn) { blobPath(ex, 3.3, 0.9, 0.6, 0.5); c.fillStyle = '#4a0f0b'; c.fill(); c.lineWidth = 0.08; c.strokeStyle = INK; c.stroke(); }
    }
    // hair: hairline on the front, above the ears, and down the back; patchy and greasy on a zombie
    const hairBand = (x0, x1, top, bottom) => {
      for (let x = x0; x < x1; x += 0.07) {
        const yb = bottom(x) + (rnd() - 0.5) * 0.25; if (Z && rnd() < 0.06) continue;
        line([x, top, x + (rnd() - 0.5) * 0.2, yb], 0.11, rnd() < 0.5 ? T.hair : (Z ? '#5a5348' : '#5a4030'));
      }
      for (let i = 0; i < (x1 - x0) * 6; i++) line([x0 + rnd() * (x1 - x0), top + rnd(), x0 + rnd() * (x1 - x0), top + rnd() * 2], 0.04, 'rgba(255,240,220,.18)');
    };
    hairBand(8, 16, 0, x => (Z ? 0.7 : 1.0) + Math.abs(x - 12) * 0.12 + (Z && Math.abs(x - 12.6) < 1.2 ? -0.6 : 0));
    hairBand(0, 8, 0, x => x > 5.6 ? 2.7 : 2.4 + (x < 2 ? 2.5 : 0));
    hairBand(16, 24, 0, x => x < 18.4 ? 2.7 : 2.4 + (x > 22 ? 2.5 : 0));
    hairBand(24, 32, 0, () => 5.4);
    if (Z && D >= 1) { wound(14.2, 1.5, 1.1, 0.3, { drip: true }); rot(4.6, 1.6, 0.9); }
    if (Z && D >= 2) scratch(9.0, 2.0, 1.15, 2.2);
    if (!Z) { line([13.4, 1.4, 14.4, 2.0], 0.07, 'rgba(120,40,30,.6)'); }
    curve(25, 7.3, 28, 7.7, 31, 7.3, 0.05, 'rgba(25,15,12,.4)');                                 // nape crease
  });
  reg(8, 0, 8, 8, (w, h) => { c.fillStyle = T.hair; c.fillRect(0, 0, w, h); for (let i = 0; i < 700; i++) line([rnd() * w, rnd() * h, rnd() * w, rnd() * h].map((v, k) => k > 1 ? v : v), 0.05, rnd() < 0.5 ? 'rgba(0,0,0,.35)' : 'rgba(255,240,220,.12)'); if (Z) { soft(5, 3, 1.6, T.skin, 0.9); wound(5.2, 3.2, 0.8, 0.5, { drip: false }); } });
  reg(16, 0, 8, 8, (w, h) => { skin(w, h); soft(4, 4, 4, 'rgba(10,8,8,.8)', 0.5); });

  // ================= torso: right 0-4 | front 4-12 | left 12-16 | back 16-24 =================
  const SH = 9.1;                                                              // shorts start (the pelvis slice)
  reg(16, 20, 24, 12, (w, h) => {
    skin(w, h, { edges: [4, 12, 16], livid: Z ? 0.3 : 0 });
    const fx = 8, bx = 20;
    soft(fx, 0.4, 0.5, 'rgba(15,10,10,.8)', 0.6);
    for (const s of [-1, 1]) {
      curve(fx + s * 0.35, 0.75, fx + s * 1.8, 0.35, fx + s * 3.7, 0.85, 0.12); soft(fx + s * 2, 1.2, 0.8, 'rgba(15,10,10,.6)', 0.4);   // collarbones
      curve(fx + s * 0.4, 3.8, fx + s * 2.2, 4.3, fx + s * 3.6, 3.3, 0.1); soft(fx + s * 2.2, 2.7, 1.5, T.light, 0.35); soft(fx + s * 2.2, 4.4, 1.0, 'rgba(15,10,10,.7)', 0.4);   // chest
      ell(fx + s * 2.1, 3.35, 0.16, 0.12, 'rgba(70,40,40,.7)');
      if (Z) for (const y of [4.6, 5.3, 6.0, 6.7]) { curve(fx + s * 1.4, y, fx + s * 2.7, y - 0.35, fx + s * 3.9, y + 0.1, 0.06, 'rgba(25,15,12,.5)'); soft(fx + s * 2.7, y + 0.35, 0.5, 'rgba(15,10,10,.7)', 0.3); }   // ribs
      curve(bx + s * 1.0, 1.6, bx + s * 2.6, 2.0, bx + s * 2.9, 4.4, 0.08, 'rgba(25,15,12,.6)'); soft(bx + s * 2.2, 3.0, 1.0, T.light, 0.3);   // shoulder blades
    }
    line([fx, 1.0, fx, 5.0], 0.05, 'rgba(25,15,12,.35)');
    for (const s of [-1, 1]) line([fx + s * 0.9, 5.4, fx + s * 1.0, 8.4], 0.05, 'rgba(25,15,12,.3)');
    ring(fx, 7.6, 0.2, 0.25, 0.08); soft(fx, 7.6, 0.25, 'rgba(10,6,6,.9)', 0.7);
    for (let y = 0.6; y < 8.6; y += 0.7) { line([bx - 0.25, y, bx + 0.25, y], 0.06, 'rgba(25,15,12,.45)'); soft(bx, y + 0.3, 0.25, T.light, 0.4); }   // spine
    if (Z) { for (let i = 0; i < 4; i++) vein(fx + (rnd() - 0.5) * 6, 2 + rnd() * 5, rnd() * 6.28, 2.2); }
    if (Z && D >= 1) { bite(13.8, 5.6, 1.1); wound(14.0, 5.6, 0.9, 0.6, { fat: true }); scratch(17.2, 1.2, 1.2, 3.0); }
    if (Z && D >= 2) {
      wound(6.6, 4.3, 2.1, 0.75, { bone: true, boneRot: -0.35 }); line([5.2, 4.0, 5.6, 4.6], 0.12, '#ddd3b8'); line([7.4, 3.9, 7.8, 4.5], 0.12, '#ddd3b8');
      for (const [x, y] of [[10.2, 2.5], [9.3, 6.4], [21.4, 5.2]]) { ell(x, y, 0.22, 0.22, '#1a0605'); ring(x, y, 0.3, 0.3, 0.08, 'rgba(60,15,10,.8)'); line([x, y + 0.2, x + 0.05, y + 1.4], 0.1, 'rgba(70,10,8,.8)'); }
      rot(2.2, 7.5, 1.1);
    }
    if (!Z) { soft(3.6, 6.5, 0.9, 'rgba(80,60,110,.7)', 0.45); scratch(18.5, 2.2, 0.9, 1.6); }
    // tattered boxer shorts
    c.fillStyle = Z ? '#4c5664' : '#56637a'; c.fillRect(0, SH, w, h - SH);
    for (let x = 0.3; x < w; x += 0.9) { c.fillStyle = 'rgba(200,190,170,.18)'; c.fillRect(x, SH, 0.25, h - SH); }
    for (let i = 0; i < 60; i++) soft(rnd() * w, SH + rnd() * (h - SH), 0.5 + rnd(), 'rgba(40,30,18,.9)', 0.3);
    c.fillStyle = '#b8b2a2'; c.fillRect(0, SH, w, 0.6); for (let x = 0; x < w; x += 0.25) line([x, SH + 0.05, x, SH + 0.55], 0.03, 'rgba(0,0,0,.25)');
    line([0, SH, w, SH], 0.09); line([0, SH + 0.6, w, SH + 0.6], 0.07);
    curve(fx - 0.2, SH + 0.8, fx + 0.6, SH + 1.6, fx + 0.1, h, 0.07);
    if (Z) { for (let i = 0; i < 4; i++) soft(rnd() * w, SH + 1 + rnd() * 1.8, 0.6, 'rgba(60,10,8,.9)', 0.7); blobPath(15, SH + 1.8, 0.7, 0.45, 0.6); c.fillStyle = T.skin; c.fill(); c.lineWidth = 0.07; c.strokeStyle = INK; c.stroke(); }
  });
  reg(20, 16, 8, 4, (w, h) => { skin(w, h); soft(4, 0, 2.5, 'rgba(15,10,10,.7)', 0.3); });
  reg(28, 16, 8, 4, (w, h) => { c.fillStyle = '#3e4652'; c.fillRect(0, 0, w, h); });

  // ================= arms: side 0-4 | front 4-8 | side 8-12 | back 12-16 (hand from 9.4 down) =================
  const HAND = 9.4, ELB = 4.95;
  const arm = (rx, ry, right) => reg(rx, ry, 16, 12, (w, h) => {
    skin(w, h, { edges: [4, 8, 12], livid: Z ? 0.35 : 0.1 });
    const out = right ? 2 : 10;
    curve(out - 1.6, 2.6, out, 3.0, out + 1.6, 2.6, 0.08, 'rgba(25,15,12,.55)'); soft(out, 1.4, 1.4, T.light, 0.4);        // deltoid
    for (const x of [6, 14]) soft(x, 2.8, 1.4, T.light, 0.25);
    ring(14, ELB + 0.1, 0.5, 0.3, 0.05, 'rgba(25,15,12,.6)', 0, 0.3, 2.8); ring(14, ELB + 0.3, 0.7, 0.35, 0.04, 'rgba(25,15,12,.45)', 0, 0.3, 2.8);   // elbow
    soft(14, ELB, 0.7, 'rgba(70,40,40,.6)', 0.3); line([5.2, ELB, 6.8, ELB + 0.05], 0.05, 'rgba(25,15,12,.45)');
    line([4.3, HAND - 0.05, 7.7, HAND], 0.05, 'rgba(25,15,12,.4)');
    if (Z) for (let i = 0; i < 3; i++) vein(5 + rnd() * 2, ELB + 0.6 + rnd(), 1.4 + (rnd() - 0.5) * 0.6, 3.2);
    else for (let i = 0; i < 2; i++) vein(5.5 + rnd(), ELB + 1.5, 1.5, 2.4, 1);
    // mitten hand: knuckle ridge, a soft mitt seam where the thumb sits, darker filthy fingertips
    c.fillStyle = 'rgba(0,0,0,.12)'; c.fillRect(0, HAND, w, h - HAND);
    for (let x = 0.2; x < w; x += 1) { ring(x + 0.5, HAND + 1.1, 0.42, 0.22, 0.05, 'rgba(25,15,12,.55)', 0, Math.PI, Math.PI * 2); soft(x + 0.5, HAND + 0.95, 0.3, T.light, 0.35); }
    curve(right ? 4.3 : 7.7, HAND + 0.3, right ? 4.7 : 7.3, HAND + 1.3, right ? 4.5 : 7.5, h, 0.06, 'rgba(25,15,12,.55)');
    const g = c.createLinearGradient(0, h - 0.9, 0, h); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, Z ? 'rgba(25,15,20,.75)' : 'rgba(60,40,30,.35)'); c.fillStyle = g; c.fillRect(0, h - 0.9, w, 0.9);
    for (let i = 0; i < 40; i++) soft(rnd() * w, HAND + rnd() * 2.6, 0.35, Z ? 'rgba(60,10,8,.9)' : 'rgba(40,30,18,.9)', 0.35);
    if (Z && right && D >= 1) { bite(6, 7.2, 0.9); wound(6.1, 7.2, 1.2, 0.7, { bone: true }); }
    if (Z && !right) scratch(4.6, 6.0, 1.0, 2.6);
    if (Z && !right && D >= 2) { wound(10.5, 1.9, 1.3, 1.0, { fat: true }); rot(14.5, 8.0, 1.0); }
    if (!Z && !right) {                                                                         // a grubby bandage round the forearm
      c.fillStyle = '#bdb6a2'; c.fillRect(0, 6.3, w, 1.6);
      for (let x = 0; x < w; x += 1.3) line([x, 6.3, x + 1.0, 7.9], 0.05, 'rgba(0,0,0,.25)');
      soft(6, 7.0, 0.6, 'rgba(120,20,16,.9)', 0.7); line([0, 6.3, w, 6.3], 0.08); line([0, 7.9, w, 7.9], 0.08);
    }
  });
  arm(40, 20, true); arm(32, 52, false);
  for (const [x, y] of [[44, 16], [36, 48]]) reg(x, y, 4, 4, (w, h) => { skin(w, h); soft(2, 2, 2, T.light, 0.3); });
  for (const [x, y] of [[48, 16], [40, 48]]) reg(x, y, 4, 4, (w, h) => { skin(w, h); c.fillStyle = Z ? 'rgba(30,18,24,.6)' : 'rgba(60,40,30,.3)'; c.fillRect(0, 0, w, h); });

  // ================= legs: side 0-4 | front 4-8 | side 8-12 | back 12-16 (foot from 10.9) =================
  const KNEE = 5.7, FOOT = 10.9;
  const leg = (rx, ry, right) => reg(rx, ry, 16, 12, (w, h) => {
    skin(w, h, { edges: [4, 8, 12], livid: Z ? 0.55 : 0.15 });
    ell(6, KNEE, 0.95, 0.75, T.light); soft(6, KNEE, 0.9, T.light, 0.4); ring(6, KNEE + 0.1, 0.95, 0.7, 0.07, 'rgba(25,15,12,.6)', 0, 0.2, Math.PI - 0.2);   // kneecap
    line([14 - 0.7, KNEE, 14 + 0.7, KNEE], 0.05, 'rgba(25,15,12,.5)'); soft(14, KNEE, 0.8, 'rgba(15,10,10,.6)', 0.35);
    line([6, KNEE + 1.2, 6.1, FOOT - 0.6], 0.2, 'rgba(255,245,230,.16)');
    soft(14, 7.6, 1.6, T.light, 0.25); curve(12.6, 6.6, 13.2, 8.4, 13.6, 9.6, 0.05, 'rgba(25,15,12,.4)');   // calf
    if (!Z) for (let i = 0; i < 160; i++) { const x = rnd() * w, y = 2 + rnd() * 8.5; line([x, y, x + 0.05, y + 0.3], 0.03, 'rgba(40,25,18,.35)'); }
    // boxer leg hem
    c.fillStyle = Z ? '#4c5664' : '#56637a'; c.fillRect(0, 0, w, 1.6);
    for (let x = 0.3; x < w; x += 0.9) { c.fillStyle = 'rgba(200,190,170,.18)'; c.fillRect(x, 0, 0.25, 1.6); }
    c.beginPath(); c.moveTo(0, 1.6); for (let x = 0; x <= w; x += 0.35) c.lineTo(x, 1.5 + (Z ? rnd() * 0.5 : rnd() * 0.12)); c.lineWidth = 0.08; c.strokeStyle = INK; c.stroke();
    // bare feet: toes, dark nails, caked dirt
    const g = c.createLinearGradient(0, FOOT - 1.5, 0, h); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(35,25,15,.8)'); c.fillStyle = g; c.fillRect(0, FOOT - 1.5, w, h - FOOT + 1.5);
    line([4, FOOT, 8, FOOT], 0.05, 'rgba(25,15,12,.45)');
    for (let i = 0; i < 5; i++) { const tx = 4.35 + i * 0.75; ring(tx, h - 0.35, 0.33, 0.3, 0.05, 'rgba(25,15,12,.7)', 0, Math.PI, Math.PI * 2); ell(tx, h - 0.42, 0.15, 0.1, Z ? '#1d1414' : '#a68a78'); }
    for (let i = 0; i < 50; i++) soft(rnd() * w, FOOT - 1 + rnd() * 2, 0.4, 'rgba(45,32,18,.95)', 0.45);
    if (Z && !right && D >= 1) { bite(14, 7.4, 0.9); wound(14, 7.4, 1.1, 0.8, {}); }
    if (Z && right) { soft(6, KNEE, 1, 'rgba(110,20,16,.9)', 0.6); scratch(1.0, 3.0, 1.3, 2.2); }
    if (Z && right && D >= 2) { wound(2.1, 7.6, 1.0, 1.6, { bone: true, boneRot: 1.5 }); }
    if (!Z && right) { soft(6, KNEE, 0.7, 'rgba(140,50,40,.9)', 0.5); }
  });
  leg(0, 20, true); leg(16, 52, false);
  for (const [x, y] of [[4, 16], [20, 48]]) reg(x, y, 4, 4, (w, h) => { c.fillStyle = Z ? '#4c5664' : '#56637a'; c.fillRect(0, 0, w, h); });
  for (const [x, y] of [[8, 16], [24, 48]]) reg(x, y, 4, 4, (w, h) => { skin(w, h); c.fillStyle = 'rgba(30,22,14,.75)'; c.fillRect(0, 0, w, h); });

  // final film grain over the painted pixels
  const id = c.getImageData ? c.getImageData(0, 0, cv.width, cv.height) : null;
  if (id && id.data) {
    const a = id.data;
    for (let i = 0; i < a.length; i += 4) if (a[i + 3]) { const v = (rnd() - 0.5) * 14; a[i] += v; a[i + 1] += v; a[i + 2] += v; }
    c.putImageData(id, 0, 0);
  }
  return cv;
}
