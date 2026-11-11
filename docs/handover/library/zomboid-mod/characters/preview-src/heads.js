// ---------- head bursts and the game's hitboxes (Josiah 27 Oct 06:39) ----------
// Shots now land as they do in the game: on the hit capsules fitted to the drawn body for the pose it is in
// (BodyCaps, the same tables the mod uses), with the game's gun numbers. A big enough hit to the head bursts it:
// one hunting rifle headshot, or three or more pellets of buckshot in the head up close. The head is gone, a torn
// neck is left, skull bits and the hat fly along the shot, then it dies the way every Minecraft mob dies: it tips
// over onto its side and vanishes in a puff.
const CAPS = __CAPS__;
const CAP_NOTES = __NOTES__;
const SC = 17.6, CAP_Y = 0.4;              // preview units a block, and where the game's feet sit in the preview
const MIRROR = [0, 2, 1, 3, 6, 7, 4, 5, 10, 11, 8, 9];
const PART_CUT = { 4: 'shR', 5: 'elR', 6: 'shL', 7: 'elL', 8: 'hipR', 9: 'knR', 10: 'hipL', 11: 'knL' };
const PART_NAME = ['head', 'chest', 'chest', 'hips', 'right arm', 'right forearm', 'left arm', 'left forearm',
  'right thigh', 'right shin', 'left thigh', 'left shin'];
// the game's numbers: damage a bullet or pellet, headshot multiplier, limb tearing and head bursting
const GUNS = {
  pistol: { name: 'Pistol', pellets: 1, spread: 0.004, dmg: 7, head: 3, tear: 0.8, burst: 0, fling: 45 },
  rifle: { name: 'Assault rifle', pellets: 1, spread: 0.003, dmg: 12, head: 3, tear: 1.0, burst: 0, fling: 60 },
  hunting: { name: 'Hunting rifle', pellets: 1, spread: 0.001, dmg: 30, head: 3, tear: 1.4, burst: 4, fling: 80 },
  shotgun: { name: 'Shotgun', pellets: 8, spread: Math.PI / 180 * 2.5, dmg: 3.5, head: 2.5, tear: 2.0, burst: 10, fling: 95, falloff: 10, range: 40 },
};
const TOUGH = { el: 20, sh: 28, kn: 24, hip: 34 }, HEAD_BURST = 240, HEALTH = 20, BODY_SHOTS = 0.33;
const POSE_NAME = { IDLE: 'standing', SHAMBLE: 'shambling', ALERT: 'alerted', CHASE: 'chasing', ATTACK_1: 'attack, wind-up',
  ATTACK_2: 'attack, lunge', ATTACK_3: 'attack, biting', ATTACK_4: 'attack, letting go', DOWN: 'knocked down',
  CRAWL_REST: 'crawler resting', CRAWL_SLOW: 'crawling', CRAWL_FAST: 'crawling fast', CRAWL_ALERT: 'crawler alerted',
  CRAWL_GRAB_1: 'crawler lunging', CRAWL_GRAB_2: 'crawler dragging you in', HOP: 'hopping on one leg',
  HOP_REACH: 'hopping, reaching for you', STAND1: 'on one leg', STAND1_REACH: 'on one leg, reaching' };
var HEADS = { show: false, key: '', pose: null, dead: null, hp: HEALTH, puffs: [] };   // var: rebuild() runs before this line
const capsView = new THREE.Group(); scene.add(capsView);
const CAP_COLOURS = [0xe64a3c, 0xf0c83c, 0xf0c83c, 0xf0c83c, 0x468cff, 0x46dcff, 0x468cff, 0x46dcff, 0x46c85a, 0xaaf05a, 0x46c85a, 0xaaf05a];
const capMats = CAP_COLOURS.map(c => new THREE.MeshBasicMaterial({ color: c, transparent: true, opacity: 0.32, depthTest: false, depthWrite: false, fog: false }));
const hbnote = document.getElementById('hbnote');

function resetHeads() {
  if (!HEADS) return;
  for (const p of HEADS.puffs) scene.remove(p.m);
  HEADS.dead = null; HEADS.hp = HEALTH; HEADS.key = ''; HEADS.puffs = []; SHOOT.headGone = false;
}

// the table the game uses for what the zombie is doing now (as the server picks it), and whether it is mirrored
function capsPose(t, v) {
  const mode = state.anim, n = legsGone(), ph = v.phase || 0;
  if (LEGS.off || n === 2) {
    if (mode === 'down') return ['DOWN', false];
    if (mode === 'attack') return [(t + ph * 0.3) % 1.8 < 1 ? 'CRAWL_GRAB_1' : 'CRAWL_GRAB_2', false];
    return [mode === 'chase' ? 'CRAWL_FAST' : mode === 'idle' ? 'CRAWL_REST' : mode === 'alert' ? 'CRAWL_ALERT' : 'CRAWL_SLOW', false];
  }
  if (n === 1) return [mode === 'chase' || mode === 'attack' ? 'HOP_REACH' : 'HOP', goneSide() === 'L'];
  if (mode === 'crawler') return ['CRAWL_SLOW', false];
  if (mode === 'down') return ['DOWN', false];
  if (mode === 'attack') return ['ATTACK_' + (1 + Math.min(3, Math.floor((t + ph * 0.3) % 2.0 / 0.5))), false];
  return [{ idle: 'IDLE', alert: 'ALERT', shamble: 'SHAMBLE', chase: 'CHASE' }[mode], false];
}

// parts there's nothing left of to hit
function goneParts() {
  let g = 0;
  const c = SHOOT.cut;
  if (c.has('elR') || c.has('shR')) g |= 1 << 5; if (c.has('shR')) g |= 1 << 4;
  if (c.has('elL') || c.has('shL')) g |= 1 << 7; if (c.has('shL')) g |= 1 << 6;
  if (c.has('knR') || c.has('hipR')) g |= 1 << 9; if (c.has('hipR')) g |= 1 << 8;
  if (c.has('knL') || c.has('hipL')) g |= 1 << 11; if (c.has('hipL')) g |= 1 << 10;
  if (LEGS.off) g |= 0xF00;
  if (SHOOT.headGone) g |= 1;
  return g;
}

// the current table's capsules in the preview's figure space: [part, a, b, r]
function capsules() {
  const [name, mirror] = HEADS.pose, T = CAPS[name], gone = goneParts(), out = [];
  for (let k = 0; k < T.length; k += 8) {
    const part = mirror ? MIRROR[T[k]] : T[k];
    if (gone >> part & 1) continue;
    const s = mirror ? -1 : 1;
    out.push([part, new THREE.Vector3(s * T[k + 1] * SC, T[k + 2] * SC + CAP_Y, T[k + 3] * SC),
      new THREE.Vector3(s * T[k + 4] * SC, T[k + 5] * SC + CAP_Y, T[k + 6] * SC), T[k + 7] * SC]);
  }
  return out;
}

function drawCaps() {
  const key = HEADS.pose.join() + goneParts();
  if (key === HEADS.key) return;
  HEADS.key = key;
  while (capsView.children.length) { const m = capsView.children.pop(); m.geometry.dispose(); }
  const up = new THREE.Vector3(0, 1, 0);
  for (const [part, a, b, r] of capsules()) {
    const len = a.distanceTo(b), mid = a.clone().add(b).multiplyScalar(0.5);
    const cyl = new THREE.Mesh(new THREE.CylinderGeometry(r, r, len, 14, 1, true), capMats[part]);
    cyl.position.copy(mid); if (len > 1e-4) cyl.quaternion.setFromUnitVectors(up, b.clone().sub(a).normalize());
    cyl.renderOrder = 10; capsView.add(cyl);
    for (const p of [a, b]) {
      const s = new THREE.Mesh(new THREE.SphereGeometry(r, 14, 10), capMats[part]); s.position.copy(p); s.renderOrder = 10; capsView.add(s);
    }
  }
}

// a ray (in figure space, d unit) against a capsule: how far along it enters, or Infinity
function rayCapsule(o, d, a, b, r) {
  const ba = b.clone().sub(a), oa = o.clone().sub(a);
  const baba = ba.dot(ba), bard = ba.dot(d), baoa = ba.dot(oa), rdoa = d.dot(oa), oaoa = oa.dot(oa);
  const A = baba - bard * bard;
  if (A > 1e-9) {
    const B = baba * rdoa - baoa * bard, C = baba * oaoa - baoa * baoa - r * r * baba, h = B * B - A * C;
    if (h < 0) return Infinity;
    const t = (-B - Math.sqrt(h)) / A, y = baoa + t * bard;
    if (y > 0 && y < baba) return t > 0 ? t : Infinity;
  }
  let best = Infinity;
  for (const c of [a, b]) {
    const oc = o.clone().sub(c), bb = d.dot(oc), cc = oc.dot(oc) - r * r, h = bb * bb - cc;
    if (h >= 0) { const t = -bb - Math.sqrt(h); if (t > 0 && t < best) best = t; }
  }
  return best;
}

const _inv4 = new THREE.Matrix4();
function shoot(e) {
  if (HEADS.dead) return;
  const gun = GUNS[SHOOT.gun], r = canvas.getBoundingClientRect();
  _ndc.set((e.clientX - r.left) / r.width * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
  rc.setFromCamera(_ndc, camera);
  fig.updateMatrixWorld(true); _inv4.copy(fig.matrixWorld).invert();
  const caps = capsules(), before = legsGone(), from = snap(fig.userData.J);
  const dealt = {}, count = {}, first = {}; let head = 0, headPellets = 0, headAt = null, headDir = null, harm = 0, body = 0, any = null;
  for (let p = 0; p < gun.pellets; p++) {
    const dir = rc.ray.direction.clone();
    if (gun.spread > 0) {
      const a = rnd() * Math.PI * 2, s = Math.tan(gun.spread * Math.sqrt(rnd()));
      const right = dir.clone().cross(_up).normalize(), up = right.clone().cross(dir).normalize();
      dir.addScaledVector(right, Math.cos(a) * s).addScaledVector(up, Math.sin(a) * s).normalize();
    }
    const o = rc.ray.origin.clone().applyMatrix4(_inv4), d = dir.clone().transformDirection(_inv4);
    let best = Infinity, part = -1;
    for (const [pt, a, b, rr] of caps) { const t = rayCapsule(o, d, a, b, rr); if (t < best) { best = t; part = pt; } }
    if (part < 0) continue;
    any = dir;
    const at = o.clone().addScaledVector(d, best).applyMatrix4(fig.matrixWorld);
    // buckshot weakens past 10 blocks, down to a quarter at its range
    let dmg = gun.dmg;
    if (gun.falloff) dmg *= 1 - 0.75 * Math.max(0, Math.min(1, (at.distanceTo(camera.position) / SC - gun.falloff) / (gun.range - gun.falloff)));
    if (part === 0) {
      head += dmg * gun.head * gun.burst; headPellets++; harm += dmg * gun.head; headAt = headAt || at; headDir = headDir || dir;
      splash(at, dir, 10);
      continue;
    }
    const cut = PART_CUT[part], leg = part >= 8;
    harm += dmg * (leg ? 0.7 : 1) * BODY_SHOTS;
    if (cut) { dealt[cut] = (dealt[cut] || 0) + dmg * gun.tear; count[cut] = (count[cut] || 0) + 1; first[cut] = first[cut] || { at, dir, part }; }
    else { body++; splash(at, dir, 8); }
  }
  if (!any) { shotlog.textContent = gun.name + ': missed (no hitbox there)'; return; }
  const said = [], pel = n => gun.pellets > 1 ? n + (n === 1 ? ' pellet' : ' pellets') + ' in the ' : '';
  if (headPellets) {
    if (head >= HEAD_BURST) said.push(pel(headPellets) + (gun.pellets > 1 ? 'head' : 'headshot') + ': HEAD BURST');
    else said.push(pel(headPellets) + (gun.pellets > 1 ? 'head' : 'headshot') + (gun.burst > 0 ? ' (' + Math.round(100 * head / HEAD_BURST) + '% of a burst)' : ''));
  }
  if (body) said.push(gun.pellets > 1 ? body + ' in the body' : 'body hit');
  // the upper cut wins when a blast hits both halves of one limb
  for (const cut of Object.keys(dealt).sort((a, b) => LIMB.sub[b.slice(0, -1)].length - LIMB.sub[a.slice(0, -1)].length)) {
    const k = cut.slice(0, -1), R = cut.slice(-1), side = R === 'R' ? 'right' : 'left';
    if (SHOOT.cut.has(cut) || (k === 'el' && SHOOT.cut.has('sh' + R)) || (k === 'kn' && SHOOT.cut.has('hip' + R))) continue;
    SHOOT.hp[cut] = (SHOOT.hp[cut] || 0) + dealt[cut];
    const where = PART_NAME[first[cut].part].replace(/^(right|left) /, '');
    const pellets = gun.pellets > 1 ? pel(count[cut]) : '';
    if (SHOOT.hp[cut] >= TOUGH[k]) {
      SHOOT.cut.add(cut);
      if (k === 'sh') SHOOT.cut.delete('el' + R);
      if (k === 'hip') SHOOT.cut.delete('kn' + R);
      addStump(fig, cut);
      detach(fig, cut, first[cut].dir, { fling: gun.fling }, first[cut].at);
      said.push(pellets + side + ' ' + where + ': torn off at the ' + LIMB.at[k]);
    } else {
      splash(first[cut].at, first[cut].dir, 10);
      said.push(pellets + side + ' ' + where + ' (' + Math.round(100 * SHOOT.hp[cut] / TOUGH[k]) + '% torn)');
    }
  }
  const t0 = clock.getElapsedTime();
  if (headPellets && head >= HEAD_BURST && !SHOOT.headGone) {
    burst(headAt, headDir, gun);
    HEADS.dead = { t0 }; said.push('it drops dead');
  } else {
    HEADS.hp -= harm;
    if (HEADS.hp <= 0) { HEADS.dead = { t0 }; said.push(headPellets ? 'the headshot kills it' : 'it dies'); }
  }
  const after = legsGone();
  if (!HEADS.dead) {
    if (after === 2 && before < 2) { SHOOT.ev = { kind: 'fall', t0, from, off: from.pos[2] + 7 }; said.push('it falls'); }
    else if (after === 1 && before === 0) { SHOOT.ev = { kind: 'stumble', t0, from }; said.push('it stumbles'); }
  }
  shotlog.textContent = gun.name + ': ' + said.join(', ');
}

// the average colour of the face on the skin, tinted as the body is drawn
function skinColour() {
  if (fig.userData.skinRGB) return fig.userData.skinRGB;
  const m = fig.userData.skinMat, im = m && m.map && m.map.image, col = new THREE.Color(0x8a8f78);
  try {
    const k = im.width / 64, c = document.createElement('canvas'); c.width = c.height = 8; const x = c.getContext('2d');
    x.drawImage(im, 8 * k, 8 * k, 8 * k, 8 * k, 0, 0, 8, 8);
    const p = x.getImageData(0, 0, 8, 8).data; let r = 0, g = 0, b = 0, n = 0;
    for (let i = 0; i < p.length; i += 4) if (p[i + 3] > 128) { r += p[i]; g += p[i + 1]; b += p[i + 2]; n++; }
    if (n) col.setRGB(r / n / 255, g / n / 255, b / n / 255).convertSRGBToLinear().multiply(m.color);
  } catch (err) { /* keep the stand-in colour */ }
  return fig.userData.skinRGB = col;
}

// the head bursts: gone, a torn neck, skull bits and the hat thrown along the shot, blood everywhere
function burst(at, dir, gun) {
  const J = fig.userData.J, skinMat = fig.userData.skinMat;
  SHOOT.headGone = true;
  fig.updateMatrixWorld(true); stage.updateMatrixWorld();
  const centre = new THREE.Vector3(); J.head.localToWorld(centre.set(0, STYLES.A.head[1] / 2, 0));
  // the hat and hair (the outfit's pieces on the head) come off in one piece
  const hat = new THREE.Group(), keep = [];
  for (const c of J.head.children) {
    let skin = c === fig.userData.eyes || c.material === skinMat;
    c.traverse(m => { if (m.material === skinMat) skin = true; });
    if (!skin) keep.push(c);
  }
  if (keep.length) {
    J.head.matrixWorld.decompose(hat.position, hat.quaternion, hat.scale); scene.add(hat);
    for (const c of keep) hat.add(c);
    stage.attach(hat);
    const v = dirToStage(dir).setY(0).normalize().multiplyScalar(gun.fling * (0.5 + rnd() * 0.3)); v.y = 45 + rnd() * 20;
    hat.userData = { v, w: new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(16), rest: 0 };
    SHOOT.gibs.push(hat);
  }
  J.head.visible = false;
  // the torn neck where the head was
  const neck = rag(3.2, true); neck.position.set(0, 0.6, 0); neck.rotation.z = Math.PI; J.neck.add(neck);
  SHOOT.stumps.push({ g: neck, t: -3 });
  // skull, scalp and brains, flung along the shot
  const skin = new THREE.MeshLambertMaterial({ color: skinColour() });
  const goreMats = [gear('#3a0a07', { shine: true, noise: .2, grime: 0, clean: true, outline: .5 }),
    gear('#cfc4a4', { noise: .15, grime: 0, clean: true, outline: .4 }), gear('#4a0d0a', { shine: true, noise: .1, grime: 0, clean: true, outline: .4 })];
  const c0 = toStage(centre), d0 = dirToStage(dir);
  for (let i = 0; i < 15; i++) {
    const s = i < 8 ? 0.9 + rnd() * 0.9 : 0.5 + rnd() * 0.7;
    const g = new THREE.Group(), m = new THREE.Mesh(new THREE.BoxGeometry(s, s * (0.5 + rnd() * 0.5), s), i < 8 ? skin : goreMats[i % 3]);
    m.castShadow = true; g.add(m); g.position.copy(c0).add(new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(2.5));
    g.quaternion.setFromEuler(new THREE.Euler(rnd() * 6, rnd() * 6, rnd() * 6)); stage.add(g);
    const v = d0.clone().multiplyScalar(gun.fling * (0.5 + rnd() * 0.6)).add(new THREE.Vector3(rnd() - 0.5, rnd() * 0.9, rnd() - 0.5).multiplyScalar(60));
    v.y += 20; g.userData = { v, w: new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(24), rest: 0 };
    SHOOT.gibs.push(g);
  }
  splash(centre, dir, 70);
  splash(centre, new THREE.Vector3(0, 1, 0).add(dir.clone().multiplyScalar(0.3)).normalize(), 24);
}

const POOF = new THREE.MeshBasicMaterial({ color: 0xd8d8d0, transparent: true, opacity: 0.8, depthWrite: false });
function stepHeads(t, v) {
  const dt = Math.min(0.05, t - (stepHeads.prev ?? t)); stepHeads.prev = t;
  const d = HEADS.dead;
  if (d) {
    // a Minecraft death: tips onto its side over about half a second, and after a second it's gone in a puff
    const tau = t - d.t0, f = Math.min(1, Math.sqrt(Math.max(0, tau * 20 - 1) / 20 * 1.6));
    fig.rotation.z = f * Math.PI / 2;
    if (tau >= 1 && fig.visible) {
      fig.visible = false; fig.updateMatrixWorld(true);
      for (const s of SHOOT.stumps) s.t = 99;
      const c = new THREE.Vector3(); fig.userData.J.spine.getWorldPosition(c);
      for (let i = 0; i < 20; i++) {
        const m = new THREE.Mesh(new THREE.BoxGeometry(1.2, 1.2, 1.2), POOF.clone());
        m.position.copy(c).add(new THREE.Vector3(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5).multiplyScalar(12)); scene.add(m);
        HEADS.puffs.push({ m, v: new THREE.Vector3((rnd() - 0.5) * 4, 4 + rnd() * 6, (rnd() - 0.5) * 4), life: 0 });
      }
    }
    if (tau >= 4.5) { rebuild(); return; }
  }
  for (let i = HEADS.puffs.length - 1; i >= 0; i--) {
    const p = HEADS.puffs[i]; p.life += dt; p.m.position.addScaledVector(p.v, dt); p.m.material.opacity = 0.8 * (1 - p.life);
    if (p.life >= 1) { scene.remove(p.m); p.m.geometry.dispose(); p.m.material.dispose(); HEADS.puffs.splice(i, 1); }
  }
  HEADS.pose = capsPose(t, v);
  capsView.visible = HEADS.show && !d;
  capsView.position.copy(fig.position); capsView.rotation.copy(fig.rotation);
  if (HEADS.show) drawCaps();
  if (hbnote) {
    const n = CAP_NOTES[HEADS.pose[0]];
    const text = !HEADS.show ? '' : d ? 'Dead: no hitboxes' : 'Hitboxes for ' + POSE_NAME[HEADS.pose[0]] + ': they catch ' + n[0] + '% of shots that touch the drawn body, ' + n[1] + '% on the right part';
    if (hbnote.textContent !== text) hbnote.textContent = text;
  }
}

document.getElementById('hitboxes').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
  HEADS.show = b.dataset.v === 'on'; HEADS.key = '';
});
