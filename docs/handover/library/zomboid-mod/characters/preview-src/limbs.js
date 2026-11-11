// ---------- shooting limbs off (Josiah 09:42) ----------
// Click the zombie to shoot where you click. Every hit adds the gun's limb damage to the limb it lands on; past
// the limb's toughness it tears off at the joint above the hit, flies along the shot, tumbles and lies on the
// ground, and a torn stump with bone showing stays on the zombie. Losing a leg drops it to a crawl.
var SHOOT;
const LIMB = {
  // what a hit on each joint's part tears off, and which joints go with it
  cutOf: { wrR: 'elR', elR: 'elR', shR: 'shR', anR: 'knR', knR: 'knR', hipR: 'hipR', wrL: 'elL', elL: 'elL', shL: 'shL', anL: 'knL', knL: 'knL', hipL: 'hipL' },
  sub: { el: ['el', 'wr', 'tb'], sh: ['sh', 'el', 'wr', 'tb'], kn: ['kn', 'an'], hip: ['hip', 'kn', 'an'] },
  tough: { el: 10, sh: 15, kn: 12, hip: 18 },
  name: { el: 'forearm', sh: 'arm', kn: 'shin', hip: 'leg' },
  at: { el: 'elbow', sh: 'shoulder', kn: 'knee', hip: 'hip' },
  // limb damage per bullet or pellet; pellets, spread (radians) and how hard the limb is flung
  guns: {
    pistol: { name: 'Pistol', pellets: 1, spread: 0.004, limb: 3, fling: 45 },
    rifle: { name: 'Assault rifle', pellets: 1, spread: 0.003, limb: 5, fling: 60 },
    hunting: { name: 'Hunting rifle', pellets: 1, spread: 0.001, limb: 14, fling: 80 },
    shotgun: { name: 'Shotgun', pellets: 9, spread: 0.022, limb: 3.2, fling: 95 },
  },
};
// limbs, blood and pools live on a stage that turns with the zombie when you drag the view round
const stage = new THREE.Group(); scene.add(stage);
const _inv = new THREE.Quaternion();
const toStage = v => stage.worldToLocal(v.clone());
const dirToStage = d => d.clone().applyQuaternion(_inv.copy(stage.quaternion).invert());
SHOOT = { gun: 'shotgun', hp: {}, cut: new Set(), gibs: [], drops: [], pools: [], stumps: [] };
const BLOODMAT = new THREE.MeshLambertMaterial({ color: 0x4a0806 });
const POOLMAT = new THREE.MeshLambertMaterial({ color: 0x2a0403, transparent: true, opacity: 0.9, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2 });
const shotlog = document.getElementById('shotlog');

function resetLimbs() {
  if (!SHOOT) return;
  while (stage.children.length) stage.remove(stage.children[0]);
  SHOOT.gibs = []; SHOOT.drops = []; SHOOT.pools = []; SHOOT.stumps = []; SHOOT.hp = {}; SHOOT.cut.clear();
  if (shotlog) shotlog.textContent = '';
}

// a torn end: a dark wet cap, a stub of bone and a few ragged shreds, its open face along -y of the group
function rag(w, bone) {
  const g = new THREE.Group();
  const cap = box(w + 0.5, 1.4, w + 0.5, gear('#3a0a07', { shine: true, noise: .2, grime: 0, clean: true, outline: .5 }));
  cap.position.y = 0.2; g.add(cap);
  if (bone) {
    const b = box(0.9, 1.9, 0.9, gear('#cfc4a4', { noise: .15, grime: 0, clean: true, outline: .4 }));
    b.position.set(0.3, -0.9, 0.15); g.add(b);
  }
  for (let i = 0; i < 4; i++) {
    const s = 0.4 + rnd() * 0.6, sh = box(s, 1 + rnd() * 1.6, 0.3, gear('#4a0d0a', { shine: true, noise: .1, grime: 0, clean: true, outline: .4 }));
    const a = rnd() * Math.PI * 2; sh.position.set(Math.cos(a) * w * 0.45, -0.4, Math.sin(a) * w * 0.45); sh.rotation.y = a; g.add(sh);
  }
  return g;
}

// the stump left on the body, on the parent bone at the joint
function addStump(f, cut) {
  const J = f.userData.J, S = STYLES.A, k = cut.slice(0, -1), side = cut.endsWith('R') ? -1 : 1, joint = J[cut];
  let g;
  if (k === 'el') { g = rag(S.fore[0], true); g.position.copy(joint.position); J['sh' + cut.slice(-1)].add(g); }
  else if (k === 'kn') { g = rag(S.shin[0], true); g.position.copy(joint.position); J['hip' + cut.slice(-1)].add(g); }
  else if (k === 'hip') { g = rag(S.thigh[0], true); g.position.copy(joint.position); J.hips.add(g); }
  else { g = rag(S.upper[0] + 0.4, true); g.position.set(side * (S.torso[0] / 2 + 0.5), S.torso[1] - 1.8, 0); g.rotation.z = side * Math.PI / 2; J.spine.add(g); }
  SHOOT.stumps.push({ g, t: 0 });
}

// the limb itself comes away whole, frozen in the pose it was in, with a torn end of its own
function detach(f, cut, dir, gun, at) {
  const J = f.userData.J, S = STYLES.A, k = cut.slice(0, -1), R = cut.slice(-1), root = J[cut];
  f.updateMatrixWorld(true);
  const gib = new THREE.Group(); scene.add(gib);
  root.matrixWorld.decompose(gib.position, gib.quaternion, gib.scale);
  root.parent.remove(root); root.position.set(0, 0, 0); root.quaternion.identity(); root.scale.set(1, 1, 1); gib.add(root);
  stage.updateMatrixWorld(); stage.attach(gib);
  const end = rag(k === 'el' ? S.fore[0] : k === 'kn' ? S.shin[0] : k === 'hip' ? S.thigh[0] : S.upper[0], k === 'sh' || k === 'hip');
  end.rotation.x = Math.PI; end.position.y = k === 'sh' ? S.shoulder - 0.6 : k === 'hip' ? 0.3 : 0.1; root.add(end);
  for (const j of LIMB.sub[k]) J[j + R] = new THREE.Group();   // the animation keeps posing stand-ins, not the flying limb
  const v = dirToStage(dir).setY(0).normalize().multiplyScalar(gun.fling * (0.8 + rnd() * 0.4)); v.y = 25 + rnd() * 25;
  gib.userData = { v, w: new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(14), rest: 0 };
  SHOOT.gibs.push(gib);
  splash(at, dir, 26);
}

function splash(atWorld, dirWorld, n) {
  stage.updateMatrixWorld();
  const at = toStage(atWorld), dir = dirToStage(dirWorld);
  for (let i = 0; i < n; i++) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(0.35, 0.35, 0.35), BLOODMAT); m.position.copy(at); stage.add(m);
    const v = dir.clone().multiplyScalar(18 + rnd() * 30).add(new THREE.Vector3(rnd() - 0.5, rnd() * 0.8, rnd() - 0.5).multiplyScalar(36));
    SHOOT.drops.push({ m, v });
  }
}

function pool(x, z, r) {
  if (SHOOT.pools.length > 220) stage.remove(SHOOT.pools.shift());
  const p = new THREE.Mesh(new THREE.CircleGeometry(r, 7), POOLMAT); p.rotation.x = -Math.PI / 2; p.rotation.z = rnd() * 6;
  p.position.set(x, 0.08 + SHOOT.pools.length * 0.0004, z); stage.add(p); SHOOT.pools.push(p);
}

const _box = new THREE.Box3(), _q = new THREE.Quaternion(), _ax = new THREE.Vector3(), _up = new THREE.Vector3(0, 1, 0);
function stepLimbs(dt) {
  if (!SHOOT) return;
  stage.rotation.y = state.yaw; stage.updateMatrixWorld();
  if (dt <= 0) return;
  for (const g of SHOOT.gibs) {
    const u = g.userData;
    if (u.rest > 1) continue;
    if (u.rest > 0) {
      // settling: ease the limb flat along the ground, then stop
      _ax.copy(_up).applyQuaternion(g.quaternion); const flat = _ax.clone().setY(0);
      if (flat.lengthSq() > 1e-4) { _q.setFromUnitVectors(_ax, flat.normalize()); g.quaternion.premultiply(_q.slerp(new THREE.Quaternion(), 1 - Math.min(1, dt * 6))); }
      u.rest += dt * 1.5;
    } else {
      u.v.y -= 160 * dt; g.position.addScaledVector(u.v, dt);
      const a = u.w.length(); if (a > 0) { _q.setFromAxisAngle(_ax.copy(u.w).divideScalar(a), a * dt); g.quaternion.premultiply(_q); }
    }
    _box.setFromObject(g);
    if (_box.min.y < 0) {
      g.position.y -= _box.min.y;
      if (u.rest === 0) {
        if (u.v.y < -20) pool(g.position.x, g.position.z, 1.2 + rnd());
        u.v.y = Math.abs(u.v.y) * 0.22; u.v.x *= 0.55; u.v.z *= 0.55; u.w.multiplyScalar(0.5);
        if (u.v.length() < 9) u.rest = 0.01;
      }
    }
  }
  for (let i = SHOOT.drops.length - 1; i >= 0; i--) {
    const d = SHOOT.drops[i]; d.v.y -= 160 * dt; d.m.position.addScaledVector(d.v, dt);
    if (d.m.position.y < 0.1) { pool(d.m.position.x, d.m.position.z, 0.4 + rnd() * 0.9); stage.remove(d.m); SHOOT.drops.splice(i, 1); }
  }
  // fresh stumps drip for a few seconds
  for (const s of SHOOT.stumps) {
    s.t += dt;
    if (s.t < 6 && rnd() < dt * 6 && s.g.parent) {
      const p = new THREE.Vector3(); s.g.getWorldPosition(p);
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.5, 0.3), BLOODMAT); m.position.copy(toStage(p)); stage.add(m);
      SHOOT.drops.push({ m, v: new THREE.Vector3((rnd() - 0.5) * 4, -2, (rnd() - 0.5) * 4) });
    }
  }
}

// ---------- moving on what's left ----------
// One leg gone: it lurches toward the missing side, buckles onto its one knee with a hand reaching for the ground,
// pushes itself back up and hops on the leg it has, arms out for balance. Both gone: it pitches forward, the
// hips drop like a dead weight, the arms come up to break the fall, it lands with a bounce and crawls.
const JKEYS = ['hips', 'spine', 'neck', 'head', 'shR', 'elR', 'wrR', 'shL', 'elL', 'wrL', 'hipR', 'knR', 'anR', 'hipL', 'knL', 'anL'];
function snap(J) {
  const o = {}; for (const k of JKEYS) { const r = J[k].rotation; o[k] = [r.x, r.y, r.z]; }
  const p = J.hips.position; o.pos = [p.x, p.y, p.z]; o.order = J.hips.rotation.order; return o;
}
function put(J, P) { for (const k of JKEYS) J[k].rotation.set(P[k][0], P[k][1], P[k][2]); J.hips.position.set(P.pos[0], P.pos[1], P.pos[2]); J.hips.rotation.order = P.order; }
function mix(A, B, s, keys) {
  const o = { pos: A.pos.map((v, i) => v + (B.pos[i] - v) * s), order: s < 0.5 ? A.order : B.order };
  for (const k of JKEYS) o[k] = (!keys || keys.includes(k)) ? A[k].map((v, i) => v + (B[k][i] - v) * s) : A[k].slice();
  return o;
}
const sm01 = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
const segL = (x, a, b) => sm01((x - a) / (b - a));
function legsGone() { let n = 0; for (const R of ['R', 'L']) if (SHOOT && (SHOOT.cut.has('kn' + R) || SHOOT.cut.has('hip' + R))) n++; return n; }
function goneSide() { return SHOOT.cut.has('knR') || SHOOT.cut.has('hipR') ? 'R' : 'L'; }

// how far below the hip the foot hangs, for a thigh at angle a from straight down and a knee bent k
function footDrop(a, k) { const S = STYLES.A; return S.thigh[1] * Math.cos(a) + S.shin[1] * Math.cos(a + k); }
// a bare pose: one leg (and the stump of the other) and arms set by hand, the hips planted over the standing foot
function onePose(J, o) {
  const S = STYLES.A, T = S.thigh[1], SH = S.shin[1], BASE = T + SH + S.foot[1] + S.pelvis[1] / 2;
  const st = o.gone === 'R' ? 'L' : 'R', ss = st === 'R' ? -1 : 1, gs = -ss;
  for (const k of JKEYS) J[k].rotation.set(0, 0, 0);
  J.hips.rotation.order = 'YXZ';
  const leg = (R, a, k, an, out) => { const s = R === 'R' ? -1 : 1; J['hip' + R].rotation.set(a, 0, out * s); J['kn' + R].rotation.set(k, 0, 0); J['an' + R].rotation.set(an, 0, 0); };
  const arm = (R, sx, out, el, wr) => { const s = R === 'R' ? -1 : 1; J['sh' + R].rotation.set(sx, 0.15 * s, out * s); J['el' + R].rotation.set(el, 0, 0); J['wr' + R].rotation.set(wr, 0, 0); };
  const hx = o.hx || 0;
  leg(st, o.a, o.k, -(o.a + o.k + hx) + (o.toe || 0), o.in || -0.08);
  leg(o.gone, o.ga, 0.2, 0, o.gout || 0.2);
  const drop = footDrop(o.a + hx, o.k);
  J.hips.position.set(gs * (o.shift ?? 1.3), BASE - (T + SH - drop) + (o.lift || 0), o.z || 0);
  J.hips.rotation.set(hx, 0, -gs * (o.roll || 0));
  J.spine.rotation.set(o.sx, 0, ss * (o.lean || 0));
  J.neck.rotation.set(o.nx || 0, 0, 0); J.head.rotation.set(o.hd || 0, 0, 0);
  arm(st, o.armS[0], o.armS[1], o.armS[2], o.armS[3]);
  arm(o.gone, o.armG[0], o.armG[1], o.armG[2], o.armG[3]);
}
function hop(J, t, gone, mode, ph) {
  const q = ((t + ph) / 0.78) % 1;
  const crouch = segL(q, 0, 0.35) * (1 - segL(q, 0.35, 0.5)), air = segL(q, 0.38, 0.52) * (1 - segL(q, 0.6, 0.74)), land = segL(q, 0.6, 0.74) * (1 - segL(q, 0.74, 1));
  const reach = mode === 'chase' || mode === 'attack', flail = Math.sin(t * 9) * land;
  onePose(J, {
    gone, a: -0.25 - 0.45 * crouch - 0.2 * land + 0.12 * air, k: 0.3 + 0.95 * crouch + 0.45 * land - 0.25 * air, toe: 0.4 * air,
    ga: -0.3 - 0.25 * air + 0.2 * crouch, lift: 2.0 * air, roll: 0.06, sx: 0.35 + 0.15 * crouch - 0.08 * air, lean: 0.1,
    nx: -0.05, hd: 0.1 + 0.2 * land - 0.1 * air,
    armS: reach ? [-1.6 - 0.2 * air, 0.2, -0.2, -0.1] : [-0.35 - 0.3 * air, 0.7 + 0.3 * air + 0.15 * flail, -0.6, 0.2],
    armG: reach ? [-1.5 - 0.2 * air, 0.3, -0.25, -0.1] : [-0.3 - 0.3 * air, 0.85 + 0.35 * air - 0.15 * flail, -0.5, 0.2],
  });
}
function limbMove(f, mode, t, v) {
  const J = f.userData.J, n = legsGone(), ev = SHOOT && SHOOT.ev, tau = ev ? t - ev.t0 : 99;
  if (n === 0) return zombieMove(f, mode, t, v);
  if (n === 2) {
    // the legless version of whatever it is doing, after the fall
    const keep = LEGS.off; LEGS.off = true; zombieMove(f, mode === 'down' ? 'down' : mode, t, v); LEGS.off = keep;
    J.hips.position.z += ev && ev.kind === 'fall' ? ev.off : 7;
    if (!ev || ev.kind !== 'fall' || tau > 1) return;
    const B = snap(J), A = ev.from;
    if (tau < 0.6) {
      const e = (tau / 0.6) ** 2, fast = sm01(tau / 0.3);
      const P = mix(A, B, e); const arms = mix(A, B, fast);
      for (const k of ['shR', 'elR', 'wrR', 'shL', 'elL', 'wrL', 'neck', 'head']) P[k] = arms[k];
      P.pos[2] = A.pos[2];
      P.head[0] -= 0.35 * fast * (1 - e);
      put(J, P);
    } else {
      const u = (tau - 0.6) / 0.4, bump = Math.sin(Math.PI * Math.min(1, u)) * (1 - Math.min(1, u));
      J.hips.position.y += 1.1 * bump; J.spine.rotation.x -= 0.2 * bump;
      J.elR.rotation.x -= 0.6 * bump; J.elL.rotation.x -= 0.6 * bump; J.head.rotation.x += 0.25 * bump;
    }
    return;
  }
  const gone = goneSide(), ph = (v.phase || 0) * 0.13;
  hop(J, t, gone, mode, ph);
  if (!ev || ev.kind !== 'stumble' || tau > 1.7) return;
  const K3 = snap(J), A = ev.from;
  // buckle toward the missing side, arms flung out
  onePose(J, { gone, a: -0.5, k: 1.2, ga: -0.8, gout: 0.35, shift: 0.9, z: 0.8, hx: 0.15, roll: 0.3, sx: 0.55, lean: -0.15,
    nx: -0.2, hd: -0.3, armS: [-0.9, 1.3, -0.3, 0.2], armG: [-1.1, 1.4, -0.4, 0.2] });
  const K1 = snap(J);
  // down on its one knee, the hand on the standing side reaching for the ground, the other thrown up
  onePose(J, { gone, a: -1.5, k: 2.0, ga: -0.4, gout: 0.25, shift: 0.6, z: 1.5, hx: 0.35, roll: 0.15, sx: 0.85, lean: 0.1,
    nx: -0.3, hd: -0.25, armS: [-1.5, 0.25, -0.15, 0.6], armG: [-2.2, 0.9, -0.8, 0.2] });
  const K2 = snap(J);
  put(J, tau < 0.3 ? mix(A, K1, sm01(tau / 0.3)) : tau < 0.8 ? mix(K1, K2, sm01((tau - 0.3) / 0.5)) : mix(K2, K3, sm01((tau - 0.8) / 0.9)));
  // blending the angles bends the leg on a curve, so set the hips back down onto the standing foot
  if (tau > 0.3) {
    const S = STYLES.A, st = gone === 'R' ? 'L' : 'R', BASE = S.thigh[1] + S.shin[1] + S.foot[1] + S.pelvis[1] / 2;
    const a = J['hip' + st].rotation.x + J.hips.rotation.x, k = J['kn' + st].rotation.x;
    J.hips.position.y = BASE - (S.thigh[1] + S.shin[1] - footDrop(a, k));
  }
}

const rc = new THREE.Raycaster(), _ndc = new THREE.Vector2();
function visible(o) { for (; o; o = o.parent) if (!o.visible) return false; return true; }
function shoot(e) {
  const gun = LIMB.guns[SHOOT.gun], r = canvas.getBoundingClientRect();
  _ndc.set((e.clientX - r.left) / r.width * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
  rc.setFromCamera(_ndc, camera);
  const J = fig.userData.J, jmap = new Map(Object.entries(J).map(([k, v]) => [v, k]));
  const hits = {}, first = {}, before = legsGone(), from = snap(J);
  let head = 0, body = 0, any = null;
  for (let p = 0; p < gun.pellets; p++) {
    const dir = rc.ray.direction.clone();
    if (gun.spread > 0) {
      const a = rnd() * Math.PI * 2, s = gun.spread * Math.sqrt(rnd());
      const right = dir.clone().cross(_up).normalize(), up = right.clone().cross(dir).normalize();
      dir.addScaledVector(right, Math.cos(a) * s).addScaledVector(up, Math.sin(a) * s).normalize();
    }
    const ray = new THREE.Raycaster(rc.ray.origin, dir); ray.camera = camera;
    const hit = ray.intersectObject(fig, true).find(h => h.object.isMesh && visible(h.object));
    if (!hit) continue;
    any = dir;
    let o = hit.object; while (o && !jmap.has(o)) o = o.parent;
    const key = o ? jmap.get(o) : 'spine';
    const cut = LIMB.cutOf[key];
    if (cut) { hits[cut] = (hits[cut] || 0) + 1; first[cut] = first[cut] || { at: hit.point, dir }; }
    else if (key === 'head' || key === 'neck') { head++; splash(hit.point, dir, 14); }
    else { body++; splash(hit.point, dir, 8); }
  }
  if (!any) { shotlog.textContent = gun.name + ': missed'; return; }
  const said = [];
  if (head) said.push('headshot');
  if (body) said.push(gun.pellets > 1 ? body + ' in the body' : 'body hit');
  // the upper cut wins when a blast hits both halves of one limb
  for (const cut of Object.keys(hits).sort((a, b) => LIMB.sub[b.slice(0, -1)].length - LIMB.sub[a.slice(0, -1)].length)) {
    const k = cut.slice(0, -1), R = cut.slice(-1), side = R === 'R' ? 'right' : 'left';
    if (SHOOT.cut.has(cut) || (k === 'el' && SHOOT.cut.has('sh' + R)) || (k === 'kn' && SHOOT.cut.has('hip' + R))) continue;
    SHOOT.hp[cut] = (SHOOT.hp[cut] || 0) + hits[cut] * gun.limb;
    const pellets = gun.pellets > 1 ? hits[cut] + ' pellets in the ' : '';
    if (SHOOT.hp[cut] >= LIMB.tough[k]) {
      SHOOT.cut.add(cut);
      if (k === 'sh') SHOOT.cut.delete('el' + R);
      if (k === 'hip') SHOOT.cut.delete('kn' + R);
      addStump(fig, cut);
      detach(fig, cut, first[cut].dir, gun, first[cut].at);
      said.push(pellets + side + ' ' + LIMB.name[k] + ': torn off at the ' + LIMB.at[k]);
    } else {
      splash(first[cut].at, first[cut].dir, 10);
      said.push(pellets + side + ' ' + LIMB.name[k] + ' (' + Math.round(100 * SHOOT.hp[cut] / LIMB.tough[k]) + '% torn)');
    }
  }
  const after = legsGone(), t0 = clock.getElapsedTime();
  if (after === 2 && before < 2) { SHOOT.ev = { kind: 'fall', t0, from, off: from.pos[2] + 7 }; said.push('it falls'); }
  else if (after === 1 && before === 0) { SHOOT.ev = { kind: 'stumble', t0, from }; said.push('it stumbles'); }
  shotlog.textContent = gun.name + ': ' + said.join(', ');
}

document.getElementById('gun').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  if (b.dataset.v === 'reset') { rebuild(); return; }
  e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
  SHOOT.gun = b.dataset.v;
});
let press = null;
canvas.addEventListener('pointerdown', e => { press = { x: e.clientX, y: e.clientY }; });
canvas.addEventListener('pointerup', e => { if (press && Math.hypot(e.clientX - press.x, e.clientY - press.y) < 6) shoot(e); press = null; });
