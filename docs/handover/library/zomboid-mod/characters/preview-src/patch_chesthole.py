# A chest blown open (Josiah 27 Oct 09:08): point-blank buckshot with most of the blast in the chest blows a hole right
# through it (the game's ChestHole). Adds a "Shotgun, point-blank" gun. Run on the v20 kits.html:
#   python3 patch_chesthole.py KITS_HTML OUT_HTML
import re, sys
src, out = sys.argv[1:3]
s = open(src).read()

def rep(a, b):
    global s
    assert s.count(a) == 1, (s.count(a), a[:70])
    s = s.replace(a, b)

HOLE_JS = r"""
// ---------- a chest blown open (Josiah 27 Oct 09:08): the game's ChestHole ----------
// Point-blank buckshot with most of the blast in the chest blows a hole right through it: every piece of the body and
// its clothes on the chest is cut round the hole (the same rectangles, in the spine's space, as the game), which is
// lined with wet flesh, with broken ribs, the snapped spine at the back, torn lips round both rims and shreds hanging
// in. Only a headshot kills, so it keeps coming.
const HOLE = {
  rects: [[-1.4, 1.2, 4.55, 8.35], [-2.05, 1.85, 5.2, 7.7], [-1.8, 1.6, 4.85, 8.05]], front: 4.0, back: -2.62,
  core: [-1.4, 1.2, 5.2, 7.7], range: 3, share: 0.5, bodyFront: 2.1, bodyBack: -2.1,
  rim: [[-0.1, 4.55, 0, -1], [0.85, 4.6, 0.3, -1], [1.6, 5.0, 0.7, -0.7], [1.85, 5.9, 1, 0], [1.85, 7.0, 1, 0.1],
    [1.6, 7.9, 0.7, 0.7], [0.55, 8.35, 0, 1], [-0.7, 8.35, -0.1, 1], [-1.7, 7.95, -0.7, 0.7], [-2.05, 6.9, -1, 0],
    [-2.05, 5.8, -1, -0.1], [-1.55, 4.8, -0.7, -0.7]],
  ribs: [[-2.05, 5.75, 1, 1], [-2.05, 6.5, 1, 1], [-2.05, 7.25, 1, 1], [1.85, 6.0, -1, 1], [1.85, 6.85, -1, 1],
    [-2.05, 6.15, 1, -1], [1.85, 6.55, -1, -1]],
};
const holeIn = (x, y) => HOLE.rects.some(r => x > r[0] && x < r[1] && y > r[2] && y < r[3]);

// a convex polygon (points: p position, uv) cut by a plane across one axis, keeping the side below `at` or above it
function holeClip(poly, axis, at, below) {
  const out = [], side = v => below ? v.p[axis] <= at : v.p[axis] > at;
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i], b = poly[(i + 1) % poly.length], ia = side(a), ib = side(b);
    if (ia) out.push(a);
    if (ia !== ib) {
      const t = (at - a.p[axis]) / (b.p[axis] - a.p[axis]);
      out.push({ p: a.p.map((x, k) => x + (b.p[k] - x) * t), uv: a.uv && a.uv.map((x, k) => x + (b.uv[k] - x) * t) });
    }
  }
  return out.length >= 3 ? out : null;
}
// what's left of a polygon outside a box (lo, hi), as convex pieces; on its faces counts as outside
function holeSubtract(poly, lo, hi, out) {
  let rest = poly;
  for (let axis = 0; axis < 3 && rest; axis++) {
    const a = holeClip(rest, axis, lo[axis] + 1e-4, true); if (a) out.push(a);
    rest = holeClip(rest, axis, lo[axis] + 1e-4, false); if (!rest) break;
    const b = holeClip(rest, axis, hi[axis] - 1e-4, false); if (b) out.push(b);
    rest = holeClip(rest, axis, hi[axis] - 1e-4, true);
  }
}
// cuts the hole out of a list of polygons (one triangle to start)
function holeCut(polys) {
  for (const r of HOLE.rects) {
    const next = [];
    for (const p of polys) holeSubtract(p, [r[0], r[2], HOLE.back], [r[1], r[3], HOLE.front], next);
    polys = next;
  }
  return polys;
}

// cuts the hole through every piece on the chest, and finds how deep the clothes go over it and round its rims
function holeOpen(f) {
  const J = f.userData.J, bones = new Set(Object.values(J)), meshes = [];
  (function walk(o) { for (const c of o.children) { if (bones.has(c) || c.userData.hole) continue; if (c.isMesh) meshes.push(c); walk(c); } })(J.spine);
  f.updateMatrixWorld(true);
  const inv = J.spine.matrixWorld.clone().invert(), v = new THREE.Vector3(), n = new THREE.Vector3(), cloth = [];
  const info = { front: HOLE.bodyFront, back: HOLE.bodyBack, rimF: HOLE.rim.map(() => HOLE.bodyFront), rimB: HOLE.rim.map(() => HOLE.bodyBack) };
  const rimAt = HOLE.rim.map(p => { const l = Math.hypot(p[2], p[3]); return [p[0] + p[2] / l * 0.3, p[1] + p[3] / l * 0.3]; });
  for (const m of meshes) {
    const toSpine = inv.clone().multiply(m.matrixWorld), fromSpine = toSpine.clone().invert(), nm = new THREE.Matrix3().getNormalMatrix(toSpine);
    const g0 = m.geometry.index ? m.geometry.toNonIndexed() : m.geometry;
    const pos = g0.attributes.position, uv = g0.attributes.uv, nor = g0.attributes.normal;
    if (!pos || !nor) continue;
    const groups = g0.groups.length ? g0.groups : [{ start: 0, count: pos.count, materialIndex: 0 }];
    const P = [], U = [], N = [], G = []; let cut = false;
    for (const gr of groups) {
      const start = P.length / 3;
      for (let i = gr.start; i + 2 < gr.start + gr.count; i += 3) {
        const tri = [0, 1, 2].map(k => { v.fromBufferAttribute(pos, i + k).applyMatrix4(toSpine); return { p: [v.x, v.y, v.z], uv: uv ? [uv.getX(i + k), uv.getY(i + k)] : null }; });
        n.fromBufferAttribute(nor, i).applyMatrix3(nm).normalize();
        const xs = tri.map(q => q.p[0]), ys = tri.map(q => q.p[1]), zs = tri.map(q => q.p[2]);
        const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys), z0 = Math.min(...zs), z1 = Math.max(...zs);
        if (Math.abs(n.z) > 0.9) {
          // what's worn over the middle of the hole sets the lining's depth; what's over each rim point, its lip
          const c = HOLE.core;
          if (x1 > c[0] && x0 < c[1] && y1 > c[2] && y0 < c[3]) {
            if (n.z > 0 && z1 <= HOLE.front) info.front = Math.max(info.front, z1);
            if (n.z < 0 && z0 >= HOLE.back) info.back = Math.min(info.back, z0);
          }
          rimAt.forEach(([px, py], k) => {
            const [a, b, d] = tri.map(q => q.p), den = (b[1] - d[1]) * (a[0] - d[0]) + (d[0] - b[0]) * (a[1] - d[1]);
            if (Math.abs(den) < 1e-9) return;
            const l1 = ((b[1] - d[1]) * (px - d[0]) + (d[0] - b[0]) * (py - d[1])) / den, l2 = ((d[1] - a[1]) * (px - d[0]) + (a[0] - d[0]) * (py - d[1])) / den;
            if (l1 < 0 || l2 < 0 || l1 + l2 > 1) return;
            const z = l1 * a[2] + l2 * b[2] + (1 - l1 - l2) * d[2];
            if (n.z > 0 && z <= HOLE.front) info.rimF[k] = Math.max(info.rimF[k], z);
            if (n.z < 0 && z >= HOLE.back) info.rimB[k] = Math.min(info.rimB[k], z);
          });
        }
        const near = HOLE.rects.some(r => x1 > r[0] && x0 < r[1] && y1 > r[2] && y0 < r[3]) && z1 > HOLE.back && z0 < HOLE.front;
        const polys = near ? holeCut([tri]) : [tri];
        if (near) cut = true;
        for (const p of polys) for (let k = 1; k + 1 < p.length; k++) for (const q of [p[0], p[k], p[k + 1]]) {
          v.set(q.p[0], q.p[1], q.p[2]).applyMatrix4(fromSpine); P.push(v.x, v.y, v.z);
          if (uv) U.push(q.uv[0], q.uv[1]);
          N.push(nor.getX(i), nor.getY(i), nor.getZ(i));
        }
      }
      G.push([start, P.length / 3 - start, gr.materialIndex]);
    }
    if (!cut) continue;
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(P, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(N, 3));
    if (uv) g.setAttribute('uv', new THREE.Float32BufferAttribute(U, 2));
    for (const [st, ct, mi] of G) g.addGroup(st, ct, mi);
    m.geometry = g;
    if (m.material !== fig.userData.skinMat) cloth.push(m.material);
  }
  return Object.assign(info, { cloth });
}

// the inside of the hole: lining, broken ribs, the snapped spine, lips round the rims and shreds hanging in
function holeInside(J, info, seed) {
  const grp = new THREE.Group(); grp.userData.hole = true; J.spine.add(grp);
  const FLESH = gear('#3a0a07', { shine: true, noise: .2, grime: 0, clean: true, outline: .5 }),
    BONE = gear('#cfc4a4', { noise: .15, grime: 0, clean: true, outline: .4 }), SHRED = gear('#4a0d0a', { shine: true, noise: .1, grime: 0, clean: true, outline: .4 });
  const add = (w, h, d, paint, x, y, z, rz = 0, ry = 0) => { const m = box(w, h, d, paint); m.position.set(x, y, z); m.rotation.set(0, ry, rz, 'ZYX'); grp.add(m); return m; };
  const { front, back } = info, depth = front - back, zc = (front + back) / 2;
  // the lining, round the outline of the rectangles
  const xs = [...new Set(HOLE.rects.flatMap(r => [r[0], r[1]]))].sort((a, b) => a - b), ys = [...new Set(HOLE.rects.flatMap(r => [r[2], r[3]]))].sort((a, b) => a - b);
  const cell = (i, j) => i >= 0 && j >= 0 && i + 1 < xs.length && j + 1 < ys.length && holeIn((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2);
  for (let i = 0; i + 1 < xs.length; i++) for (let j = 0; j + 1 < ys.length; j++) {
    if (!cell(i, j)) continue;
    const w = xs[i + 1] - xs[i], h = ys[j + 1] - ys[j], cx = (xs[i] + xs[i + 1]) / 2, cy = (ys[j] + ys[j + 1]) / 2;
    if (!cell(i - 1, j)) add(0.06, h, depth, FLESH, xs[i] - 0.03, cy, zc);
    if (!cell(i + 1, j)) add(0.06, h, depth, FLESH, xs[i + 1] + 0.03, cy, zc);
    if (!cell(i, j - 1)) add(w, 0.06, depth, FLESH, cx, ys[j] - 0.03, zc);
    if (!cell(i, j + 1)) add(w, 0.06, depth, FLESH, cx, ys[j + 1] + 0.03, zc);
  }
  // the game's own little random run, so the bits sit where they do in the game for this seed
  let r = Math.imul(seed, 0x9E3779B9 | 0) + 0x51ED | 0;
  const next = () => (r = Math.imul(r, 1103515245) + 12345 | 0);
  for (const rib of HOLE.ribs) {
    next();
    const len = 0.4 + (r >>> 8 & 0xFF) / 255 * 0.45, turn = ((r >>> 16 & 0xFF) / 255 - 0.5) * 0.5;
    add(len, 0.34, 0.4, BONE, rib[0] + rib[2] * (len / 2 - 0.15), rib[1], rib[3] > 0 ? HOLE.bodyFront - 0.55 : HOLE.bodyBack + 0.6, 0, turn);
  }
  const R0 = HOLE.rects[0];
  add(0.8, 0.85, 0.75, BONE, -0.1, R0[2] + 0.4, HOLE.bodyBack + 0.45);
  add(0.8, 0.75, 0.75, BONE, -0.1, R0[3] - 0.36, HOLE.bodyBack + 0.45, 0, 0.2);
  HOLE.rim.forEach((p, i) => {
    const angle = Math.atan2(p[3], p[2]);
    for (let side = 0; side < 2; side++) {
      if (side === 1 && i % 3 === 1) continue;   // the exit's a little tidier
      next();
      const radial = 0.3 + (r >>> 8 & 0xFF) / 255 * 0.3, along = 0.45 + (r >>> 16 & 0xFF) / 255 * 0.4;
      const a = angle + ((r >>> 24) / 255 - 0.5) * 0.4, z = side === 0 ? info.rimF[i] + 0.06 : info.rimB[i] - 0.06;
      add(radial, along, 0.14, (r >>> 4 & 3) === 0 ? SHRED : FLESH, p[0] + Math.cos(a) * radial * 0.3, p[1] + Math.sin(a) * radial * 0.3, z, a);
    }
  });
  for (let i = 0; i < 2; i++) {
    next();
    const x = -0.75 + i * 1.1 + ((r >>> 8 & 0xFF) / 255 - 0.5) * 0.4, h = 0.5 + (r >>> 16 & 0xFF) / 255 * 0.5;
    const top = holeIn(x, R0[3] - 0.05) ? R0[3] : HOLE.rects[2][3];
    add(0.25 + (r >>> 4 & 7) * 0.03, h, 0.14, SHRED, x, top - h / 2 + 0.1, front - 0.2 - (r >>> 24) / 255 * 0.5);
  }
  return grp;
}

// blows the chest open: the hole, and rags of what it wore and bits of flesh and bone out of its back along the shot
function blowOpen(dir, gun) {
  const J = fig.userData.J;
  SHOOT.chestHole = true;
  const info = holeOpen(fig);
  holeInside(J, info, 1234);
  fig.updateMatrixWorld(true); stage.updateMatrixWorld();
  const centre = J.spine.localToWorld(new THREE.Vector3(-0.1, 6.45, 0));
  const goreMats = [gear('#3a0a07', { shine: true, noise: .2, grime: 0, clean: true, outline: .5 }),
    gear('#cfc4a4', { noise: .15, grime: 0, clean: true, outline: .4 }), gear('#4a0d0a', { shine: true, noise: .1, grime: 0, clean: true, outline: .4 })];
  const c0 = toStage(centre), d0 = dirToStage(dir);
  for (let i = 0; i < 16; i++) {
    const s = 0.8 + rnd() * 1.2, cloth = i < 7 && info.cloth.length ? info.cloth[Math.floor(rnd() * info.cloth.length)] : null;
    const g = new THREE.Group(), m = cloth ? new THREE.Mesh(new THREE.BoxGeometry(s, s * (0.3 + rnd() * 0.3), s), cloth) : box(s, s * (0.5 + rnd() * 0.5), s, goreMats[i % 3]);
    m.castShadow = true; g.add(m); g.position.copy(c0).add(new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(2.5));
    g.quaternion.setFromEuler(new THREE.Euler(rnd() * 6, rnd() * 6, rnd() * 6)); stage.add(g);
    const v = d0.clone().multiplyScalar(gun.fling * (0.5 + rnd() * 0.7)).add(new THREE.Vector3(rnd() - 0.5, rnd() * 0.7, rnd() - 0.5).multiplyScalar(50));
    v.y += 12; g.userData = { v, w: new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(22), rest: 0 };
    SHOOT.gibs.push(g);
  }
  splash(centre, dir, 60);
}

function shoot(e) {"""

rep("\nfunction shoot(e) {", HOLE_JS)
# the point-blank shotgun
rep("""  shotgun: { name: 'Shotgun', pellets: 8, spread: Math.PI / 180 * 2.5, dmg: 3.5, head: 2.5, tear: 2.0, burst: 10, fling: 95, falloff: 10, range: 40 },
};""", """  shotgun: { name: 'Shotgun', pellets: 8, spread: Math.PI / 180 * 2.5, dmg: 3.5, head: 2.5, tear: 2.0, burst: 10, fling: 95, falloff: 10, range: 40 },
  // a blast from right up close: the pellets still bunched, at full strength
  pointblank: { name: 'Shotgun, point-blank', pellets: 8, spread: Math.PI / 180 * 1.0, dmg: 3.5, head: 2.5, tear: 2.0, burst: 10, fling: 95, close: true },
};""")
rep("""        <button data-v="shotgun" aria-pressed="true">Shotgun</button>
""", """        <button data-v="shotgun" aria-pressed="true">Shotgun</button>
        <button data-v="pointblank">Shotgun, point-blank</button>
""")
# count the pellets in the chest from point-blank range
rep("""let head = 0, headPellets = 0, headAt = null, headDir = null, harm = 0, body = 0, any = null;""",
    """let head = 0, headPellets = 0, headAt = null, headDir = null, harm = 0, body = 0, any = null, chest = 0, chestDir = null;""")
rep("""    else { body++; splash(at, dir, 8); }
  }""", """    else { body++; splash(at, dir, 8); }
    if ((part === 1 || part === 2) && gun.pellets > 1 && (gun.close || at.distanceTo(camera.position) / SC <= HOLE.range)) { chest++; chestDir = chestDir || dir; }
  }""")
rep("""  const t0 = clock.getElapsedTime();
  if (headPellets && head >= HEAD_BURST && !SHOOT.headGone) {""", """  if (chest > gun.pellets * HOLE.share && !SHOOT.chestHole) {
    blowOpen(chestDir, gun); said.push(pel(chest) + 'chest: BLOWN OPEN, a hole right through it');
  }
  const t0 = clock.getElapsedTime();
  if (headPellets && head >= HEAD_BURST && !SHOOT.headGone) {""")
# a point-blank blast shoves as hard as any buckshot
rep("""{ pistol: 0, rifle: 1, hunting: 2, shotgun: 3 }[SHOOT.gun]""", """{ pistol: 0, rifle: 1, hunting: 2, shotgun: 3, pointblank: 3 }[SHOOT.gun]""")
rep("""({ pistol: 0, rifle: 1, hunting: 2, shotgun: 3 }[gunKey] ?? 1)""", """({ pistol: 0, rifle: 1, hunting: 2, shotgun: 3, pointblank: 3 }[gunKey] ?? 1)""")
# a fresh zombie has its chest whole
rep("""function rebuild() { if (fig) {""", """function rebuild() { if (typeof SHOOT === 'object' && SHOOT) SHOOT.chestHole = false; if (fig) {""")
rep("""wriggling along on its belly, until you put one in its head.""",
    """wriggling along on its belly, until you put one in its head. Shotgun, point-blank: most of the blast in the chest blows a hole right through it (you can see out the back), and it still keeps coming.""")
open(out, 'w').write(s)
open(out + '.check.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[-1])
print(len(s) // 1024, 'KB')
