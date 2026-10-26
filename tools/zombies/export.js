// Export the preview's body, kit pieces and stumps as bone-local quads plus a packed kit atlas.
const Q = new URLSearchParams(location.search), OUT = Q.get('o') || 'police', OI = Object.keys(AI15).indexOf(OUT);
const M4 = __M4;
const inv = m => { // affine inverse
  const a = m, r = [a[0], a[4], a[8], a[1], a[5], a[9], a[2], a[6], a[10]];
  const det = a[0] * (a[5] * a[10] - a[6] * a[9]) - a[1] * (a[4] * a[10] - a[6] * a[8]) + a[2] * (a[4] * a[9] - a[5] * a[8]);
  const c = [
    (a[5] * a[10] - a[6] * a[9]) / det, (a[2] * a[9] - a[1] * a[10]) / det, (a[1] * a[6] - a[2] * a[5]) / det,
    (a[6] * a[8] - a[4] * a[10]) / det, (a[0] * a[10] - a[2] * a[8]) / det, (a[2] * a[4] - a[0] * a[6]) / det,
    (a[4] * a[9] - a[5] * a[8]) / det, (a[1] * a[8] - a[0] * a[9]) / det, (a[0] * a[5] - a[1] * a[4]) / det];
  const t = [a[3], a[7], a[11]];
  return [c[0], c[1], c[2], -(c[0] * t[0] + c[1] * t[1] + c[2] * t[2]), c[3], c[4], c[5], -(c[3] * t[0] + c[4] * t[1] + c[5] * t[2]),
    c[6], c[7], c[8], -(c[6] * t[0] + c[7] * t[1] + c[8] * t[2]), 0, 0, 0, 1];
};
KIT.outfit = OUT; KIT.on = true; KIT.blood = 0.9; KIT.wear = 1.0; seed = 7 + OI * 101;
const src = AI15[OUT];
const fig = buildSkinned(src, src); rotZero(fig.userData.J);
const J = fig.userData.J, BN = ['hips', 'spine', 'neck', 'head', 'shR', 'elR', 'wrR', 'shL', 'elL', 'wrL', 'hipR', 'knR', 'anR', 'hipL', 'knL', 'anL'];
const before = new Set(); fig.traverse(o => before.add(o));
makeLegless(fig); J.hipR.scale.set(1, 1, 1); J.hipL.scale.set(1, 1, 1); J.knR.visible = J.knL.visible = true;
const world = new Map();
(function w(o, P) { const m = M4.mul(P, M4.trs(o.position, o.rotation, o.scale)); world.set(o, m); o.children.forEach(c => w(c, m)); })(fig, M4.id());
const boneOf = o => { let p = o.parent; while (p) { const i = BN.findIndex(n => J[n] === p); if (i >= 0) return i; p = p.parent; } return -1; };
const bones = BN.map(n => { const o = J[n], pi = BN.findIndex(k => J[k] === o.parent); return { name: n, parent: pi, pos: [o.position.x, o.position.y, o.position.z] }; });
const boneInv = BN.map(n => inv(world.get(J[n])));
const boxes = [], body = [];
fig.traverse(o => {
  if (!o.isMesh) return;
  const b = boneOf(o); if (b < 0) return;
  const L = M4.mul(boneInv[b], world.get(o));
  if (o.geometry.box) boxes.push({ o, b, L, stump: !before.has(o) });
  else body.push({ o, b, L });
});
// faces of the kit boxes (px nx py ny pz nz: TL, TR, BL), dropping faces buried against another box of the same bone
const FACES = (w, h, d) => [[[w, h, d], [w, h, -d], [w, -h, d]], [[-w, h, -d], [-w, h, d], [-w, -h, -d]], [[-w, h, -d], [w, h, -d], [-w, h, d]],
  [[-w, -h, d], [w, -h, d], [-w, -h, -d]], [[-w, h, d], [w, h, d], [-w, -h, d]], [[w, h, -d], [-w, h, -d], [w, -h, -d]]];
for (const B of boxes) { const g = B.o.geometry.parameters; B.half = [g.width / 2, g.height / 2, g.depth / 2]; B.inv = inv(B.L); }
const inside = (B, p) => { const q = M4.ap(B.inv, p); return Math.abs(q[0]) < B.half[0] - 1e-3 && Math.abs(q[1]) < B.half[1] - 1e-3 && Math.abs(q[2]) < B.half[2] - 1e-3; };
console.log('boxes', boxes.length, 'body', body.length);
const quads = [], faceImgs = [];
let dropped = 0, clear = 0;
const alphaOf = new Map();
const opaqueness = c => { if (alphaOf.has(c)) return alphaOf.get(c); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; let n = 0; for (let i = 3; i < d.length; i += 4) if (d[i] > 127) n++; const r = n / (d.length / 4); alphaOf.set(c, r); return r; };
for (const B of boxes) {
  const [w, h, d] = B.half, mats = [].concat(B.o.material), small = Math.max(w, h, d) * 2 < 1.0 || (w * h * d * 8) < 0.12;
  FACES(w, h, d).forEach((f, i) => {
    const m = mats[i] || mats[0]; if (!m.map) return;
    const tl = M4.ap(B.L, f[0]), tr = M4.ap(B.L, f[1]), bl = M4.ap(B.L, f[2]), br = [tr[0] + bl[0] - tl[0], tr[1] + bl[1] - tl[1], tr[2] + bl[2] - tl[2]];
    const c = [(tl[0] + br[0]) / 2, (tl[1] + br[1]) / 2, (tl[2] + br[2]) / 2];
    const e1 = [tr[0] - tl[0], tr[1] - tl[1], tr[2] - tl[2]], e2 = [bl[0] - tl[0], bl[1] - tl[1], bl[2] - tl[2]];
    let n = [-(e1[1] * e2[2] - e1[2] * e2[1]), -(e1[2] * e2[0] - e1[0] * e2[2]), -(e1[0] * e2[1] - e1[1] * e2[0])]; const nl = Math.hypot(...n) || 1; n = n.map(x => x / nl);
    const probe = [c[0] + n[0] * 0.02, c[1] + n[1] * 0.02, c[2] + n[2] * 0.02];
    if (boxes.some(o => o !== B && o.b === B.b && o.stump === B.stump && inside(o, probe))) { dropped++; return; }
    const img = m.map.image, op = m.alphaTest > 0 ? opaqueness(img) : 1;
    if (op < 0.02) { clear++; return; }
    quads.push({ b: B.b, p: [...tl, ...tr, ...br, ...bl], n, img, small, stump: B.stump, cut: m.alphaTest > 0 && op < 0.999 });
  });
}
console.log('faces', quads.length, dropped, clear);
// pack the face images into one atlas (shelf packing, tallest first), 1px padding with edge pixels repeated
const uniq = [...new Set(quads.map(q => q.img))].sort((a, b) => b.height - a.height || b.width - a.width);
const AW = 2048; let x = 0, y = 0, rowH = 0; const atPos = new Map();
for (const im of uniq) { const w = im.width + 2, h = im.height + 2; if (x + w > AW) { x = 0; y += rowH; rowH = 0; } atPos.set(im, [x + 1, y + 1]); x += w; rowH = Math.max(rowH, h); }
let AH = 64; while (AH < y + rowH) AH *= 2;
const atlas = document.createElement('canvas'); atlas.width = AW; atlas.height = AH; const ax = atlas.getContext('2d'); ax.imageSmoothingEnabled = false;
for (const im of uniq) { const [px, py] = atPos.get(im), w = im.width, h = im.height;
  ax.drawImage(im, 0, 0, w, 1, px, py - 1, w, 1); ax.drawImage(im, 0, h - 1, w, 1, px, py + h, w, 1);
  ax.drawImage(im, 0, 0, 1, h, px - 1, py, 1, h); ax.drawImage(im, w - 1, 0, 1, h, px + w, py, 1, h); ax.drawImage(im, px, py); }
const kit = quads.map(q => { const [px, py] = atPos.get(q.img); return { b: q.b, p: q.p.map(v => +v.toFixed(4)), n: q.n.map(v => +v.toFixed(4)), uv: [px / AW, py / AH, (px + q.img.width) / AW, (py + q.img.height) / AH], small: q.small ? 1 : 0, stump: q.stump ? 1 : 0, cut: q.cut ? 1 : 0 }; });
// the body: the skinned boxes' quads with their skin UVs (0..1 over the 64-layout skin)
const bodyQ = [];
for (const B of body) {
  const P = B.o.geometry.attributes.position.array, U = B.o.geometry.attributes.uv.array;
  for (let q = 0; q < P.length / 12; q++) {
    const v = i => M4.ap(B.L, [P[(q * 4 + i) * 3], P[(q * 4 + i) * 3 + 1], P[(q * 4 + i) * 3 + 2]]);
    const a = v(0), b2 = v(1), c2 = v(2), d2 = v(3);
    const e1 = [b2[0] - a[0], b2[1] - a[1], b2[2] - a[2]], e2 = [d2[0] - a[0], d2[1] - a[1], d2[2] - a[2]];
    let n = [-(e1[1] * e2[2] - e1[2] * e2[1]), -(e1[2] * e2[0] - e1[0] * e2[2]), -(e1[0] * e2[1] - e1[1] * e2[0])]; const nl = Math.hypot(...n) || 1; n = n.map(x => x / nl);
    bodyQ.push({ b: B.b, p: [...a, ...b2, ...c2, ...d2].map(v => +v.toFixed(4)), n: n.map(v => +v.toFixed(4)), uv: [U[q * 8], U[q * 8 + 1], U[q * 8 + 2], U[q * 8 + 5]] });
  }
}
window.__RESULT = { outfit: OUT, bones, body: bodyQ, kit, aw: AW, ah: AH, atlas: atlas.toDataURL('image/png'), stats: { boxes: boxes.length, quads: kit.length, dropped, clear, faces: uniq.length } };
document.title = 'done';
