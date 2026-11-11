// ---------- worn clothing built from 3D pieces ----------
// A garment shell is four fabric panels (plus a top cap where it doesn't continue into the next piece).
// Each panel is a grid of cells: cloth, faded, stained, blood-soaked, or missing (a real hole the body
// shows through). Cells of one kind merge into blocks, so a whole panel is still only a few boxes, and
// the ink line is drawn only where cloth meets a hole or the panel's own outer edge.
const mixHex = (a, b, t) => {
  const p = s => [1, 3, 5].map(i => parseInt(s.slice(i, i + 2), 16)), A = p(a), B = p(b);
  return '#' + A.map((v, i) => Math.round(v + (B[i] - v) * t).toString(16).padStart(2, '0')).join('');
};
// the moody grade every piece goes through: colour drained toward grey, darkened toward a sooty brown
const grade = (c, w = 0.6, k = 1) => {
  if (!c || c.length !== 7) return c;
  const n = parseInt(c.slice(1), 16), R = n >> 16, G = n >> 8 & 255, B = n & 255, L = 0.3 * R + 0.59 * G + 0.11 * B, ds = (0.42 + 0.18 * w) * k;
  const g = [R, G, B].map(v => Math.round(v + (L - v) * ds)).map(v => v.toString(16).padStart(2, '0')).join('');
  return mixHex('#' + g, '#1f1b15', (0.26 + 0.2 * w) * k);
};
const CELL = { CLOTH: 1, HOLE: 0, STAIN: 2, FADE: 3, BLOOD: 4 };
function cellColour(base, type) {
  if (type === CELL.STAIN) return mixHex(base, '#2a2014', 0.45);
  if (type === CELL.FADE) return mixHex(base, '#77705e', 0.3);
  if (type === CELL.BLOOD) return mixHex(base, '#2e0806', 0.7);
  return base;
}
// painter for one merged block: the outer face gets the fabric and ink only on the given edge runs
function blockPaint(base, po, edges) {
  const cloth = gear(base, { ...po, noOutline: true });
  const inner = gear(mixHex(base, '#000000', 0.25), { noise: .05, noOutline: true, grime: 0 });
  return (c, W, H, f) => {
    if (f !== 'pz') return (f === 'nz' ? inner : cloth)(c, W, H, f === 'nz' ? f : 'pz');
    cloth(c, W, H, f);
    c.fillStyle = 'rgba(0,0,0,.6)';
    for (const [a, b] of edges.t) c.fillRect(a * W, 0, (b - a) * W, 1);
    for (const [a, b] of edges.b) c.fillRect(a * W, H - 1, (b - a) * W, 1);
    for (const [a, b] of edges.l) c.fillRect(0, a * H, 1, (b - a) * H);
    for (const [a, b] of edges.r) c.fillRect(W - 1, a * H, 1, (b - a) * H);
  };
}
function panel(fg, fw, h, t, base, po, jt, jb, base0) {
  const W8 = KIT.wear ?? 0.6, nc = Math.max(2, Math.round(fw / 0.7)), nr = Math.max(2, Math.round(h / 0.7)), cw = fw / nc, rh = h / nr;
  const G = Array.from({ length: nr }, () => Array(nc).fill(CELL.CLOTH)), area = fw * h, holes = [];
  const splat = (n, type, r0, r1) => {
    for (let i = 0; i < n; i++) {
      const cx = rnd() * nc, cy = rnd() * nr, r = r0 + rnd() * (r1 - r0);
      if (type === CELL.HOLE) { if (cy < 1.2 && !jt) continue; holes.push([cx, cy, r]); }
      for (let y = 0; y < nr; y++) for (let x = 0; x < nc; x++) {
        if ((x + 0.5 - cx) ** 2 + ((y + 0.5 - cy) * 1.15) ** 2 > r * r * (0.7 + rnd() * 0.6)) continue;
        if (type === CELL.HOLE && (x === 0 || x === nc - 1)) continue;                     // keep the corners standing
        G[y][x] = type;
      }
    }
  };
  const count = k => Math.floor(area * k + rnd());
  splat(count(0.025), CELL.FADE, 1.0, 2.6);
  splat(count(0.05 * W8), CELL.STAIN, 0.7, 2.0);
  splat(count(0.06 * (KIT.blood + 0.12)), CELL.BLOOD, 0.7, 1.9);
  splat(count(0.04 * W8), CELL.HOLE, 0.6, 1.5);
  if (!jb) for (let x = 0; x < nc; x++) if (rnd() < 0.3 * W8) G[nr - 1][x] = CELL.HOLE;   // ragged bottom edge
  // merge equal cells into rectangles
  const used = G.map(r => r.map(() => false));
  for (let y = 0; y < nr; y++) for (let x = 0; x < nc; x++) {
    const ty = G[y][x]; if (used[y][x] || ty === CELL.HOLE) continue;
    let n = 1; while (x + n < nc && G[y][x + n] === ty && !used[y][x + n]) n++;
    let m = 1; while (y + m < nr && G[y + m].slice(x, x + n).every((v, i) => v === ty && !used[y + m][x + i])) m++;
    for (let j = 0; j < m; j++) for (let i = 0; i < n; i++) used[y + j][x + i] = true;
    const hole = (yy, xx) => yy < 0 ? !jt : yy >= nr ? !jb : xx < 0 || xx >= nc ? true : G[yy][xx] === CELL.HOLE;
    const runs = (len, test) => { const out = []; let s = -1; for (let i = 0; i <= len; i++) { const on = i < len && test(i); if (on && s < 0) s = i; if (!on && s >= 0) { out.push([s / len, i / len]); s = -1; } } return out; };
    const edges = { t: runs(n, i => hole(y - 1, x + i)), b: runs(n, i => hole(y + m, x + i)), l: runs(m, j => hole(y + j, x - 1)), r: runs(m, j => hole(y + j, x + n)) };
    const mesh = box(n * cw, m * rh, t, blockPaint(cellColour(base, ty), po, edges), true);
    mesh.position.set(-fw / 2 + (x + n / 2) * cw, h / 2 - (y + m / 2) * rh, -t / 2); fg.add(mesh);
  }
  // frayed threads poking into the holes, and torn flaps peeling outward from some of them
  const thread = gear(mixHex(grade(base0, W8), '#8a8270', 0.3), { noise: .05, grime: 0, outline: .3 });
  let threads = 0;
  for (let y = 0; y < nr; y++) for (let x = 0; x < nc; x++) {
    if (G[y][x] !== CELL.HOLE || threads > 10 || rnd() > 0.35) continue;
    const L = 0.25 + rnd() * 0.4, cxp = -fw / 2 + (x + 0.5) * cw, cyp = h / 2 - (y + 0.5) * rh;
    const m = box(0.12, L, 0.08, thread); m.position.set(cxp + (rnd() - 0.5) * cw, cyp + rh / 2 - L / 2, -0.05); m.rotation.z = (rnd() - 0.5) * 0.8; fg.add(m); threads++;
  }
  for (const [cx, cy, r] of holes) {
    if (rnd() > 0.45 * W8) continue;
    const hinge = new THREE.Group(), fwid = Math.min(fw * 0.6, r * cw * 1.4), L = r * rh * (1 + rnd() * 0.6);
    hinge.position.set(-fw / 2 + cx * cw, h / 2 - (cy - r * 0.7) * rh, 0); hinge.rotation.set(-(0.35 + rnd() * 0.9), 0, (rnd() - 0.5) * 0.6); fg.add(hinge);
    const fl = box(fwid, L, 0.12, gear(mixHex(base, '#000000', 0.08), { ...po, outline: .6 }), true); fl.position.set(0, -L / 2, 0.06); hinge.add(fl);
  }
  // raised blood: clots stuck to the cloth and drips running down, some hanging past the edge
  const bl = (c) => gear(c, { shine: true, noise: .05, grime: 0, clean: true, outline: .45 });
  const nb = count(0.05 * (KIT.blood + 0.1));
  for (let i = 0; i < nb; i++) {
    const s = 0.35 + rnd() * 0.8, xx = (rnd() - 0.5) * (fw - s), yy = (rnd() - 0.5) * (h - s);
    const m = box(s, s * (0.6 + rnd() * 0.8), 0.07, bl(rnd() < 0.5 ? '#4a0b08' : '#2c0806')); m.position.set(xx, yy, 0.03); fg.add(m);
    if (rnd() < 0.6) { const L = 0.6 + rnd() * 2.2; const d = box(0.22, L, 0.06, bl('#3e0a07')); d.position.set(xx + (rnd() - 0.5) * s * 0.6, yy - L / 2, 0.035); fg.add(d); }
  }
}
function shell(parent, w, h, d, p, x, y, z, join = '') {
  const base = p.base || '#777777', po = { ...(p.o || {}), join: undefined }, t = 0.22, jt = join.includes('t'), jb = join.includes('b');
  const W8 = KIT.wear ?? 0.6, faded = grade(base, W8);
  const grp = new THREE.Group(); grp.position.set(x, y, z); parent.add(grp);
  if (!jt) { const cap = box(w, 0.2, d, gear(faded, { ...po, noFade: true }), true); cap.position.y = h / 2 - 0.1; grp.add(cap); }
  const sides = [[w, 0, d / 2], [w, Math.PI, d / 2], [d - 2 * t, Math.PI / 2, w / 2], [d - 2 * t, -Math.PI / 2, w / 2]];
  for (const [fw, ry, off] of sides) {
    const fg = new THREE.Group(); fg.rotation.y = ry; fg.position.set(Math.sin(ry) * off, 0, Math.cos(ry) * off); grp.add(fg);
    panel(fg, fw, h, t, mixHex(faded, rnd() < 0.5 ? '#000000' : '#ffffff', rnd() * 0.06), { ...po, noFade: true }, jt, jb, base);
  }
  return grp;
}
// caked mud on boots and shoes
function mud(parent, sx, sz, cy, cz, n) {
  for (let i = 0; i < n; i++) {
    const s = 0.3 + rnd() * 0.5, side = (rnd() * 4) | 0, a = rnd() - 0.5;
    const [x, z] = [[a * sx, sz / 2], [a * sx, -sz / 2], [sx / 2, a * sz], [-sx / 2, a * sz]][side];
    const m = box(side < 2 ? s : 0.18, s * (0.5 + rnd() * 0.6), side < 2 ? 0.18 : s, gear(rnd() < 0.5 ? '#4a3a24' : '#3a2e1c', { noise: .15, grime: 0, clean: true, outline: .45 }));
    m.position.set(x, cy + (rnd() - 0.5) * 0.6, cz + z); parent.add(m);
  }
}
