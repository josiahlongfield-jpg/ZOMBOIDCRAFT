import * as THREE from 'three';

// 3D add-on pieces for the walker model, per outfit. Units are model pixels; the model faces +z.
// Each piece gets a small noisy texture in a colour sampled from the skin, with some blood.
const PX = 4;  // texture pixels per unit, like the HD skins

function rng(seed) { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }

function tex(col, w, h, d, opts = {}) {
  const r = rng(opts.seed ?? 7);
  // one canvas per face size is overkill; use a square swatch big enough for the largest face
  const size = Math.max(4, Math.ceil(Math.max(w, h, d) * PX));
  const c = document.createElement('canvas'); c.width = c.height = size;
  const g = c.getContext('2d');
  const img = g.createImageData(size, size);
  for (let i = 0; i < size * size; i++) {
    const y = Math.floor(i / size);
    let f = 0.85 + r() * 0.22 - (opts.grad ? 0.25 * y / size : 0);
    if (opts.rib && (i % size) % 2 === 0) f *= 0.82;
    let [R, G, B] = col.map(v => v * f);
    if (opts.tip && y < size * opts.tip) { R = 70 + r() * 30; G = 12; B = 10; }   // bloody tips (top rows face down)
    if (r() < (opts.blood ?? 0.015) * 0.5) { const k = 0.4 + r() * 0.3; R = R * 0.3 + 120 * k; G *= 0.2; B *= 0.2; }
    img.data.set([R, G, B, 255], i * 4);
  }
  // a few soaked stains
  g.putImageData(img, 0, 0);
  for (let k = 0; k < Math.min(1, opts.stains ?? 0); k++) {
    g.fillStyle = `rgba(${60 + r() * 40},10,8,${0.25 + r() * 0.2})`;
    const sx = r() * size, sy = r() * size, sr = size * (0.06 + r() * 0.08);
    g.beginPath(); g.ellipse(sx, sy, sr, sr * (0.6 + r() * 0.8), r() * 3, 0, 7); g.fill();
  }
  const t = new THREE.CanvasTexture(c);
  t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

const mats = new Map();
function mat(col, o = {}) {
  const key = col.join(',') + JSON.stringify(o);
  if (!mats.has(key)) mats.set(key, new THREE.MeshLambertMaterial({ map: tex(col, 8, 8, 8, o) }));
  return mats.get(key);
}

// A cube from its min corner and size, in the parent's local space, optionally rotated about its own centre.
function piece(parent, col, x, y, z, w, h, d, o = {}) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(col, o));
  m.position.set(x + w / 2, y + h / 2, z + d / 2);
  if (o.rot) m.rotation.set(...o.rot);
  m.castShadow = true;
  parent.add(m);
  return m;
}

const BLACK = [30, 30, 32], SILVER = [190, 192, 196], GOLD = [200, 160, 60];

// Boots with toes, torn hem flaps and a hanging jaw: every zombie gets these.
function common(P, c, outfit) {
  for (const leg of [P.rleg, P.lleg]) {
    // leg box in pivot space: x -2..2, y -12..0
    piece(leg, c.shoe, -2.25, -12, -2.25, 4.5, 2.4, 5.1, { stains: 0, blood: 0.02 });
  }
  // torn flaps hanging off the shirt hem
  const r = rng(outfit.length * 31);
  for (let i = 0; i < 3; i++) {
    const x = -3.5 + r() * 6, front = r() < 0.7;
    piece(P.body, c.cloth, x, -14.2 - r(), front ? 2.0 : -2.3, 1.2 + r(), 2.4 + r() * 1.5, 0.3,
      { rot: [front ? 0.12 : -0.12, 0, (r() - 0.5) * 0.5], blood: 0.1, stains: 1 });
  }
  // lower jaw hanging open under the mouth
  piece(P.head, c.skin, -2.3, -0.9, 2.6, 4.6, 0.9, 1.7, { rot: [0.3, 0, 0], tip: 0.5, blood: 0.1, stains: 0 });
}

function hoodie(P, c) {
  const h = P.head, cl = c.cloth, o = { blood: 0.03, stains: 2 };
  // the hood: a shell around the head with a gap, open at the front, sagging onto the shoulders
  piece(h, cl, -5, 8.3, -5, 10, 0.9, 9.4, o);                 // top
  piece(h, cl, -5, -1.8, -5.2, 10, 10.1, 0.9, o);             // back, down to the shoulders
  piece(h, cl, -5.2, -1, -5, 0.9, 9.3, 9.4, o);               // sides
  piece(h, cl, 4.3, -1, -5, 0.9, 9.3, 9.4, o);
  piece(h, cl, -4.4, 7.2, 4.0, 8.8, 1.2, 0.8, o);             // rim over the forehead
  piece(h, cl, -5.0, -0.6, 4.0, 1.2, 8.6, 0.8, o);            // rim at the cheeks
  piece(h, cl, 3.8, -0.6, 4.0, 1.2, 8.6, 0.8, o);
  const b = P.body;  // body box in pivot space: x -4..4, y -12..0, z -2..2
  piece(b, cl.map(v => v * 0.85), -3, -10.5, 2.0, 6, 2.8, 0.6, o);     // kangaroo pocket
  piece(b, [200, 200, 196], -1.6, -4.2, 2.1, 0.35, 3.4, 0.35, { blood: 0.2, stains: 0 });  // drawstrings
  piece(b, [200, 200, 196], 1.25, -3.8, 2.1, 0.35, 3.0, 0.35, { blood: 0.2, stains: 0 });
  for (const [arm, x0] of [[P.rarm, -3.3], [P.larm, -1.3]]) piece(arm, cl.map(v => v * 0.8), x0, -10, -2.3, 4.6, 1.3, 4.6, { rib: true, stains: 1 });
}

function police(P, c) {
  const h = P.head, b = P.body, navy = c.hat, o = { blood: 0.03, stains: 1 };
  piece(h, navy, -4.5, 5.2, -4.5, 9, 3.3, 9, o);              // cap crown sitting on the head
  piece(h, BLACK, -4.6, 5.0, -4.6, 9.2, 0.8, 9.2, { stains: 0 });  // band
  piece(h, GOLD, -0.7, 6.0, 4.4, 1.4, 1.5, 0.4, { stains: 0, blood: 0 });  // cap badge
  piece(b, BLACK, -4.4, -12.2, -2.4, 8.8, 1.6, 4.8, { stains: 0, blood: 0.02 });  // duty belt
  piece(b, SILVER, -0.8, -12.0, 2.35, 1.6, 1.2, 0.3, { stains: 0, blood: 0 });
  piece(b, BLACK, -5.4, -14.5, -1.0, 1.3, 3.6, 2.2, { stains: 0 });                 // holster on the right hip
  piece(b, [60, 50, 40], -5.3, -12.2, -0.6, 1.1, 1.6, 1.4, { stains: 0, blood: 0 }); // pistol grip
  piece(b, BLACK, 1.4, -12.6, 2.3, 1.3, 1.6, 0.9, { stains: 0 });                   // pouches
  piece(b, BLACK, 2.9, -12.6, 2.3, 1.3, 1.6, 0.9, { stains: 0 });
  piece(b, BLACK, 2.6, -5.0, 2.0, 1.0, 1.8, 0.7, { stains: 0 });                     // radio on the chest
  piece(b, GOLD, -3.1, -4.6, 2.0, 1.3, 1.5, 0.35, { stains: 0, blood: 0 });          // badge (zombie's left = +x... viewer's right)
  piece(b, navy, -2.6, -1.0, 2.0, 2.2, 1.0, 0.45, { rot: [0, 0, 0.35] });            // collar points
  piece(b, navy, 0.4, -1.0, 2.0, 2.2, 1.0, 0.45, { rot: [0, 0, -0.35] });
  for (const [arm, x0] of [[P.rarm, -3.2], [P.larm, -0.8]]) piece(arm, navy, x0, 1.9, -2.2, 4.0, 0.5, 4.4, o);  // epaulettes
}

function doctor(P, c) {
  const b = P.body, white = c.cloth, o = { blood: 0.03, stains: 1 };
  // coat tails hanging below the waist, open at the front
  piece(b, white, -4.35, -18.5, 2.0, 3.9, 7, 0.5, { ...o, rot: [0.06, 0, 0] });
  piece(b, white, 0.45, -18.5, 2.0, 3.9, 7, 0.5, { ...o, rot: [0.06, 0, 0] });
  piece(b, white, -4.35, -18.5, -2.5, 8.7, 7, 0.5, { ...o, rot: [-0.06, 0, 0] });
  piece(b, white, -4.4, -18.5, -2.3, 0.5, 7, 4.6, o);
  piece(b, white, 3.9, -18.5, -2.3, 0.5, 7, 4.6, o);
  // lapels
  piece(b, white, -3.0, -5.5, 2.0, 1.6, 5.5, 0.45, { rot: [0, 0, -0.28], stains: 0 });
  piece(b, white, 1.4, -5.5, 2.0, 1.6, 5.5, 0.45, { rot: [0, 0, 0.28], stains: 0 });
  // stethoscope round the neck
  piece(b, BLACK, -3.2, -0.4, -2.3, 6.4, 0.45, 0.45, { stains: 0, blood: 0 });
  piece(b, BLACK, -3.4, -0.4, -2.3, 0.45, 0.45, 4.6, { stains: 0, blood: 0 });
  piece(b, BLACK, 2.95, -0.4, -2.3, 0.45, 0.45, 4.6, { stains: 0, blood: 0 });
  piece(b, BLACK, -2.6, -5.0, 2.15, 0.4, 4.8, 0.4, { stains: 0, blood: 0 });
  piece(b, BLACK, 2.2, -4.0, 2.15, 0.4, 3.8, 0.4, { stains: 0, blood: 0 });
  piece(b, SILVER, -3.0, -5.8, 2.1, 1.2, 1.0, 0.4, { stains: 0, blood: 0 });
  piece(b, white, 1.5, -6.2, 2.2, 2.0, 1.8, 0.4, { stains: 1 });                    // chest pocket
  piece(b, [40, 60, 150], 2.6, -5.0, 2.45, 0.35, 1.3, 0.35, { stains: 0, blood: 0 }); // pen
}

export const OUTFITS = { hoodie, police, doctor };

export function addAddons(P, outfit, colours) {
  common(P, colours, outfit);
  OUTFITS[outfit]?.(P, colours);
}

// Average colour of a rectangle of the skin, in 64x64 UV units.
export function sampler(image) {
  const c = document.createElement('canvas'); c.width = image.width; c.height = image.height;
  const g = c.getContext('2d'); g.drawImage(image, 0, 0);
  const k = image.width / 64;
  return (u0, v0, u1, v1) => {
    const d = g.getImageData(u0 * k, v0 * k, (u1 - u0) * k, (v1 - v0) * k).data;
    let r = 0, gg = 0, b = 0, n = 0;
    for (let i = 0; i < d.length; i += 4) if (d[i + 3] > 0 && !(d[i] > d[i + 1] * 1.4 && d[i] > d[i + 2] * 1.4) /* skip blood */) { r += d[i]; gg += d[i + 1]; b += d[i + 2]; n++; }
    return n ? [r / n, gg / n, b / n] : [128, 128, 128];
  };
}
