// Companion test room: her real model and clips (the game's woman.zsk, level 0), driven by a copy of the game's mind
// (NpcMind), skills (NpcSkills), job and routine (NpcWork), feelings about you (NpcBond) and brain (NpcBrain), so all of it
// can be tried here first.
import * as T from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const $ = id => document.getElementById(id);
const rnd = Math.random, clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const gauss = () => { let u = 0, v = 0; while (!u) u = rnd(); while (!v) v = rnd(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); };

// ---------- the model file (same layout the game reads) ----------
async function loadModel() {
  const b64 = await (await fetch('woman.txt')).text(), packed = Uint8Array.from(atob(b64.trim()), c => c.charCodeAt(0));
  const raw = new Uint8Array(await new Response(new Blob([packed]).stream().pipeThrough(new DecompressionStream('deflate'))).arrayBuffer());
  const dv = new DataView(raw.buffer); let o = 8;
  const i32 = () => { const v = dv.getInt32(o); o += 4; return v; }, f32 = () => { const v = dv.getFloat32(o); o += 4; return v; };
  const i8 = () => dv.getInt8(o++), u8 = () => dv.getUint8(o++), i16 = () => { const v = dv.getInt16(o); o += 2; return v; }, u16 = () => { const v = dv.getUint16(o); o += 2; return v; };
  const utf = () => { const n = u16(); const s = new TextDecoder().decode(raw.subarray(o, o + n)); o += n; return s; };
  const groups = [];
  for (let g = 0, ng = i32(); g < ng; g++) {
    const mesh = utf(), piece = utf(), tex = utf(), flags = i8(), level = i8(), n = i32();
    const P = new Float32Array(n * 3), N = new Float32Array(n * 3), UV = new Float32Array(n * 2), J = new Uint16Array(n * 4), W = new Float32Array(n * 4);
    for (let v = 0; v < n; v++) {
      P[v * 3] = f32(); P[v * 3 + 1] = f32(); P[v * 3 + 2] = f32();
      N[v * 3] = i8() / 127; N[v * 3 + 1] = i8() / 127; N[v * 3 + 2] = i8() / 127;
      UV[v * 2] = u16() / 65535; UV[v * 2 + 1] = u16() / 65535;
      for (let k = 0; k < 4; k++) J[v * 4 + k] = u8();
      let s = 0; for (let k = 0; k < 4; k++) { W[v * 4 + k] = u8(); s += W[v * 4 + k]; }
      for (let k = 0; k < 4; k++) W[v * 4 + k] /= s || 1;
    }
    const nt = i32(), I = new Uint32Array(nt * 3);
    for (let i = 0; i < nt * 3; i++) I[i] = i32();
    groups.push({ mesh, piece, tex, flags, level, n, P, N, UV, J, W, I });
  }
  for (let t = 0, nt = i32(); t < nt; t++) { utf(); o += i32(); }
  const bones = [];
  for (let b = 0, nb = i32(); b < nb; b++) {
    const name = utf(), parent = i8(), m = []; for (let k = 0; k < 12; k++) m.push(f32());
    bones.push({ name, parent, inv: new T.Matrix4().set(...m, 0, 0, 0, 1) });
  }
  const face = {};
  for (let f = 0, nf = i32(); f < nf; f++) { const name = utf(), idx = i8(); face[name] = { idx, p: [f32(), f32(), f32()] }; }
  const clips = {};
  for (let c = 0, nc = i32(); c < nc; c++) {
    const name = utf(), frames = i32(), fps = f32(), cb = i32(), d = new Float32Array(frames * cb * 7);
    for (let i = 0; i < frames * cb; i++) { for (let k = 0; k < 4; k++) d[i * 7 + 3 + k] = i16() / 32767; for (let k = 0; k < 3; k++) d[i * 7 + k] = i16() / 1000; }
    clips[name] = { frames, fps, cb, d };
  }
  return { groups, bones, face, clips };
}

// ---------- scene ----------
const stage = $('stage');
const renderer = new T.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.outputColorSpace = T.SRGBColorSpace;
renderer.toneMapping = T.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.3;
renderer.shadowMap.enabled = true; renderer.shadowMap.type = T.PCFSoftShadowMap;
stage.prepend(renderer.domElement);
const scene = new T.Scene();
scene.background = new T.Color(0x07080a);
scene.fog = new T.Fog(0x07080a, 12, 26);
const camera = new T.PerspectiveCamera(45, 1, 0.05, 80);
camera.position.set(2.2, 1.85, 4.1);
const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(0, 1.0, 0); controls.enableDamping = true; controls.maxDistance = 11; controls.minDistance = 0.8;
controls.maxPolarAngle = Math.PI * 0.49;

function canvasTex(size, paint, repeat) {
  const c = document.createElement('canvas'); c.width = c.height = size; const g = c.getContext('2d'); paint(g, size);
  const t = new T.CanvasTexture(c); t.colorSpace = T.SRGBColorSpace; t.wrapS = t.wrapT = T.RepeatWrapping; t.anisotropy = 8;
  if (repeat) t.repeat.set(repeat[0], repeat[1]); return t;
}
const speckle = (g, s, base, n, dark, light) => {
  g.fillStyle = base; g.fillRect(0, 0, s, s);
  for (let i = 0; i < n; i++) { g.fillStyle = rnd() < 0.5 ? dark : light; const r = rnd() * 2.2; g.globalAlpha = rnd() * 0.25; g.fillRect(rnd() * s, rnd() * s, r, r); }
  g.globalAlpha = 1;
};
const floorTex = canvasTex(1024, (g, s) => {
  speckle(g, s, '#3a3934', 26000, '#22211e', '#57554e');
  for (let i = 0; i < 14; i++) { const x = rnd() * s, y = rnd() * s, r = 40 + rnd() * 120; const gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, 'rgba(20,16,10,.35)'); gr.addColorStop(1, 'rgba(20,16,10,0)'); g.fillStyle = gr; g.fillRect(x - r, y - r, r * 2, r * 2); }
  g.strokeStyle = 'rgba(0,0,0,.35)'; g.lineWidth = 2; for (let k = 1; k < 4; k++) { g.beginPath(); g.moveTo(k * s / 4, 0); g.lineTo(k * s / 4, s); g.moveTo(0, k * s / 4); g.lineTo(s, k * s / 4); g.stroke(); }
  g.strokeStyle = 'rgba(0,0,0,.5)'; g.lineWidth = 1; for (let i = 0; i < 6; i++) { g.beginPath(); let x = rnd() * s, y = rnd() * s; g.moveTo(x, y); for (let k = 0; k < 8; k++) { x += (rnd() - .5) * 60; y += (rnd() - .5) * 60; g.lineTo(x, y); } g.stroke(); }
}, [3, 3]);
const wallTex = canvasTex(1024, (g, s) => {
  speckle(g, s, '#4a4c47', 20000, '#2c2e2a', '#5e605a');
  g.fillStyle = 'rgba(0,0,0,.18)'; for (let y = 0; y < s; y += 64) for (let x = (y / 64) % 2 ? 0 : 64; x < s; x += 128) g.fillRect(x, y, 126, 62);
  g.fillStyle = 'rgba(255,255,255,.04)'; for (let y = 0; y < s; y += 64) g.fillRect(0, y, s, 2);
  for (let i = 0; i < 10; i++) { const x = rnd() * s; const gr = g.createLinearGradient(0, s * .3, 0, s); gr.addColorStop(0, 'rgba(40,30,15,0)'); gr.addColorStop(1, 'rgba(40,30,15,.35)'); g.fillStyle = gr; g.fillRect(x, s * .3, 6 + rnd() * 30, s); }
}, [3, 1]);
const crateTex = canvasTex(512, (g, s) => {
  g.fillStyle = '#6a5235'; g.fillRect(0, 0, s, s);
  for (let y = 0; y < s; y += s / 6) { g.fillStyle = `hsl(30,${28 + rnd() * 8}%,${24 + rnd() * 8}%)`; g.fillRect(0, y + 2, s, s / 6 - 4); for (let i = 0; i < 40; i++) { g.strokeStyle = 'rgba(0,0,0,.15)'; g.beginPath(); const yy = y + 4 + rnd() * (s / 6 - 8); g.moveTo(0, yy); g.bezierCurveTo(s / 3, yy + rnd() * 6 - 3, s * .66, yy + rnd() * 6 - 3, s, yy); g.stroke(); } }
  g.strokeStyle = '#3a2a18'; g.lineWidth = 28; g.strokeRect(14, 14, s - 28, s - 28); g.beginPath(); g.moveTo(14, 14); g.lineTo(s - 14, s - 14); g.stroke();
  g.fillStyle = 'rgba(20,20,20,.55)'; g.font = 'bold 44px sans-serif'; g.fillText('FRAGILE', s * .3, s * .58);
});

const room = new T.Group(); scene.add(room);
const W = 14, D = 12, H = 3.2, DOOR = 1.6;
const floorMat = new T.MeshStandardMaterial({ map: floorTex, roughness: 0.92 });
const floor = new T.Mesh(new T.PlaneGeometry(W, D + 10), floorMat); floor.rotation.x = -Math.PI / 2; floor.position.z = -5; floor.receiveShadow = true; room.add(floor);
const wallMat = new T.MeshStandardMaterial({ map: wallTex, roughness: 0.95 });
function wall(w, x, z, ry) { const m = new T.Mesh(new T.BoxGeometry(w, H, 0.2), wallMat); m.position.set(x, H / 2, z); m.rotation.y = ry; m.receiveShadow = m.castShadow = true; room.add(m); return m; }
wall(W, 0, D / 2, 0); wall(D, -W / 2, 0, Math.PI / 2); wall(D, W / 2, 0, Math.PI / 2);
wall((W - DOOR) / 2, -(W + DOOR) / 4, -D / 2, 0); wall((W - DOOR) / 2, (W + DOOR) / 4, -D / 2, 0);
{ const lintel = new T.Mesh(new T.BoxGeometry(DOOR, H - 2.2, 0.2), wallMat); lintel.position.set(0, 2.2 + (H - 2.2) / 2, -D / 2); room.add(lintel);
  const frameMat = new T.MeshStandardMaterial({ color: 0x2a2620, roughness: 0.7, metalness: 0.2 });
  for (const sx of [-1, 1]) { const f = new T.Mesh(new T.BoxGeometry(0.08, 2.2, 0.26), frameMat); f.position.set(sx * DOOR / 2, 1.1, -D / 2); room.add(f); }
  const door = new T.Mesh(new T.BoxGeometry(0.05, 2.1, DOOR - 0.1), new T.MeshStandardMaterial({ color: 0x3d4a3b, roughness: 0.6, metalness: 0.4 }));
  door.position.set(-DOOR / 2 - 0.02, 1.05, -D / 2 - (DOOR - 0.1) / 2 - 0.1); room.add(door); }
// a warning stripe at the doorway, a drain, a few blood smears: it's been used
{ const stripe = canvasTex(256, (g, s) => { for (let i = -s; i < s * 2; i += 40) { g.fillStyle = (i / 40) % 2 ? '#c9a227' : '#151515'; g.beginPath(); g.moveTo(i, 0); g.lineTo(i + 20, 0); g.lineTo(i + 20 - s, s); g.lineTo(i - s, s); g.fill(); } });
  const m = new T.Mesh(new T.PlaneGeometry(DOOR + 0.6, 0.25), new T.MeshStandardMaterial({ map: stripe, roughness: 0.8 })); m.rotation.x = -Math.PI / 2; m.position.set(0, 0.004, -D / 2 + 0.3); room.add(m);
  const blood = canvasTex(256, (g, s) => { g.clearRect(0, 0, s, s); for (let i = 0; i < 40; i++) { g.fillStyle = `rgba(70,8,6,${0.3 + rnd() * 0.5})`; g.beginPath(); g.arc(s / 2 + gauss() * 30, s / 2 + gauss() * 30, rnd() * 18, 0, 7); g.fill(); } });
  for (const [x, z] of [[-3.5, -3], [4.2, 2.6], [0.6, -5.2]]) { const b = new T.Mesh(new T.PlaneGeometry(1.4, 1.4), new T.MeshStandardMaterial({ map: blood, transparent: true, roughness: 0.4, depthWrite: false })); b.rotation.x = -Math.PI / 2; b.rotation.z = rnd() * 6; b.position.set(x, 0.006, z); room.add(b); } }
const obstacles = [];
const crateMat = new T.MeshStandardMaterial({ map: crateTex, roughness: 0.85 });
for (const [x, z, s, r] of [[-5, 3.8, 1, 0.2], [-4.1, 4.3, 0.8, -0.3], [-5, 3.8, 0.7, 0.5, 1], [5.2, -3.6, 1.1, 0.1], [4.3, 4.1, 0.9, 0.7]]) {
  const m = new T.Mesh(new T.BoxGeometry(s, s, s), crateMat); m.castShadow = m.receiveShadow = true;
  const onTop = obstacles.find(o => Math.hypot(o.x - x, o.z - z) < 0.05);
  m.position.set(x, onTop ? onTop.h + s / 2 : s / 2, z); m.rotation.y = r; room.add(m);
  if (!onTop) obstacles.push({ x, z, r: s * 0.75, h: s });
}
// a work table with a lamp, a bucket, a shelf of tins
const tableMat = new T.MeshStandardMaterial({ color: 0x4b3a28, roughness: 0.8 }), metal = new T.MeshStandardMaterial({ color: 0x5a5d60, roughness: 0.5, metalness: 0.7 });
{ const top = new T.Mesh(new T.BoxGeometry(2, 0.06, 0.9), tableMat); top.position.set(4.6, 0.86, -0.4); top.castShadow = top.receiveShadow = true; room.add(top);
  for (const [dx, dz] of [[-0.9, -0.38], [0.9, -0.38], [-0.9, 0.38], [0.9, 0.38]]) { const l = new T.Mesh(new T.BoxGeometry(0.06, 0.84, 0.06), metal); l.position.set(4.6 + dx, 0.42, -0.4 + dz); room.add(l); }
  obstacles.push({ x: 4.6, z: -0.4, r: 1.05, h: 0.9 });
  const shade = new T.Mesh(new T.ConeGeometry(0.16, 0.18, 20, 1, true), new T.MeshStandardMaterial({ color: 0x2f4a3a, side: T.DoubleSide, metalness: 0.4, roughness: 0.4 })); shade.position.set(4.2, 1.3, -0.5); room.add(shade);
  const arm = new T.Mesh(new T.CylinderGeometry(0.012, 0.012, 0.42), metal); arm.position.set(4.2, 1.08, -0.5); room.add(arm);
  const shelf = new T.Group(); shelf.position.set(-6.75, 0, -2); room.add(shelf);
  for (let y = 0.4; y < 2.2; y += 0.55) { const b = new T.Mesh(new T.BoxGeometry(0.4, 0.04, 1.6), metal); b.position.y = y; shelf.add(b);
    for (let k = 0; k < 5; k++) if (rnd() < 0.7) { const tin = new T.Mesh(new T.CylinderGeometry(0.05, 0.05, 0.12, 14), new T.MeshStandardMaterial({ color: new T.Color().setHSL(rnd(), 0.3, 0.35), metalness: 0.5, roughness: 0.4 })); tin.position.set(0.05, y + 0.08, -0.6 + k * 0.3); shelf.add(tin); } }
  for (const z of [-0.8, 0.8]) for (const x of [-0.18, 0.18]) { const p = new T.Mesh(new T.BoxGeometry(0.03, 2.1, 0.03), metal); p.position.set(x, 1.05, z); shelf.add(p); }
  obstacles.push({ x: -6.6, z: -2, r: 0.5, h: 2 }); }
// lights: a cage bulb overhead, the desk lamp, a cold spill through the doorway
const hemi = new T.HemisphereLight(0x8a93a0, 0x1a1712, 0.35); scene.add(hemi);
const bulb = new T.PointLight(0xffd9a0, 22, 16, 1.6); bulb.position.set(0, 2.9, 0.5); bulb.castShadow = true; bulb.shadow.mapSize.set(1024, 1024); bulb.shadow.bias = -0.002; scene.add(bulb);
const bulbMesh = new T.Mesh(new T.SphereGeometry(0.07, 16, 12), new T.MeshBasicMaterial({ color: 0xffe2b0 })); bulbMesh.position.copy(bulb.position); scene.add(bulbMesh);
const cord = new T.Mesh(new T.CylinderGeometry(0.006, 0.006, 0.3), metal); cord.position.set(0, 3.05, 0.5); scene.add(cord);
const lamp = new T.SpotLight(0xffc98a, 8, 6, 0.9, 0.5, 1.5); lamp.position.set(4.2, 1.25, -0.5); lamp.target.position.set(4.6, 0.86, -0.4); scene.add(lamp, lamp.target);
const doorLight = new T.SpotLight(0x8fa6c4, 6, 14, 0.5, 0.6, 1.4); doorLight.position.set(0, 2.6, -9); doorLight.target.position.set(0, 0, -3); scene.add(doorLight, doorLight.target);

// ---------- her ----------
const model = await loadModel().catch(e => { $('loading').textContent = 'Could not load her model: ' + e.message; throw e; });
$('loading').remove();
const texFile = { body: 'jpg', eyes: 'jpg', teeth: 'jpg', eyelashes: 'png', sock: 'png', bra: 'png', blackhair: 'png', shirt: 'jpg', outfit: 'jpg', pant: 'jpg', dress: 'png' };
const loader = new T.TextureLoader(), texCache = {};
const texture = key => texCache[key] ??= loader.load(`tex_${key}.${texFile[key] || 'png'}`, t => { t.colorSpace = T.SRGBColorSpace; t.flipY = false; t.anisotropy = renderer.capabilities.getMaxAnisotropy(); t.needsUpdate = true; });
const her = new T.Group(); scene.add(her);
const body = new T.Group(); her.add(body);
const nb = model.bones.length;
const skelBones = model.bones.map(() => { const b = new T.Bone(); b.matrixAutoUpdate = false; b.matrixWorldAutoUpdate = false; return b; });
const skeleton = new T.Skeleton(skelBones, model.bones.map(() => new T.Matrix4()));
const meshes = [];
let bodyMin = 1e9, shoesMin = 1e9;
for (const g of model.groups) {
  const geo = new T.BufferGeometry();
  geo.setAttribute('position', new T.BufferAttribute(g.P, 3)); geo.setAttribute('normal', new T.BufferAttribute(g.N, 3)); geo.setAttribute('uv', new T.BufferAttribute(g.UV, 2));
  geo.setAttribute('skinIndex', new T.BufferAttribute(g.J, 4)); geo.setAttribute('skinWeight', new T.BufferAttribute(g.W, 4)); geo.setIndex(new T.BufferAttribute(g.I, 1));
  const t = texture(g.tex);
  const mat = new T.MeshStandardMaterial({ map: t, roughness: g.mesh === 'body' ? 0.62 : 0.75, side: g.flags & 1 ? T.DoubleSide : T.FrontSide, alphaTest: g.flags & 2 ? 0.45 : 0, alphaToCoverage: !!(g.flags & 2) });
  const m = new T.SkinnedMesh(geo, mat); m.bindMode = 'detached'; m.bind(skeleton, new T.Matrix4()); m.frustumCulled = false; m.castShadow = true; m.receiveShadow = true;
  body.add(m); meshes.push({ g, m });
  for (let i = 1; i < g.n * 3; i += 3) { if (g.mesh === 'body') bodyMin = Math.min(bodyMin, g.P[i]); if (g.piece === 'heels') shoesMin = Math.min(shoesMin, g.P[i]); }
}
const HEEL = Math.max(0, bodyMin - shoesMin);
const boneIx = name => model.bones.findIndex(b => b.name === name);
const B = { head: boneIx('mixamorigHead'), rHand: boneIx('mixamorigRightHand'), rMid: boneIx('mixamorigRightHandMiddle1'), lHand: boneIx('mixamorigLeftHand'), lMid: boneIx('mixamorigLeftHandMiddle1') };
const restPos = i => new T.Vector3().setFromMatrixPosition(model.bones[i].inv.clone().invert());

// her rifle: a carbine built along +z from the grip
const rifle = new T.Group(); scene.add(rifle);
{ const black = new T.MeshStandardMaterial({ color: 0x1b1c1e, roughness: 0.45, metalness: 0.6 }), tan = new T.MeshStandardMaterial({ color: 0x6b5d45, roughness: 0.8 });
  const add = (geo, mat, x, y, z, rx = 0) => { const m = new T.Mesh(geo, mat); m.position.set(x, y, z); m.rotation.x = rx; m.castShadow = true; rifle.add(m); return m; };
  add(new T.BoxGeometry(0.05, 0.08, 0.34), black, 0, 0.05, 0.06);
  add(new T.CylinderGeometry(0.012, 0.012, 0.36, 10), black, 0, 0.065, 0.4, Math.PI / 2);
  add(new T.BoxGeometry(0.06, 0.07, 0.2), tan, 0, 0.055, 0.3);
  add(new T.BoxGeometry(0.035, 0.11, 0.05), black, 0, -0.02, 0.0, -0.25);
  add(new T.BoxGeometry(0.035, 0.13, 0.06), black, 0, -0.03, 0.12, 0.15);
  add(new T.BoxGeometry(0.045, 0.1, 0.22), tan, 0, 0.03, -0.2);
  add(new T.BoxGeometry(0.02, 0.03, 0.12), black, 0, 0.11, 0.06); }

// ---------- animation (the game's NpcSkinAnimator, simplified) ----------
const clipSecs = name => model.clips[name].frames / model.clips[name].fps;
function sample(name, t, loop, P, Q) {
  const c = model.clips[name]; let f = t * c.fps; f = loop ? ((f % c.frames) + c.frames) % c.frames : Math.min(c.frames - 1, f);
  const f0 = Math.floor(f), f1 = loop ? (f0 + 1) % c.frames : Math.min(c.frames - 1, f0 + 1), a = f - f0;
  const qa = new T.Quaternion(), qb = new T.Quaternion();
  for (let b = 0; b < c.cb; b++) {
    const i0 = (f0 * c.cb + b) * 7, i1 = (f1 * c.cb + b) * 7;
    P[b] = (P[b] || new T.Vector3()).set(c.d[i0] * (1 - a) + c.d[i1] * a, c.d[i0 + 1] * (1 - a) + c.d[i1 + 1] * a, c.d[i0 + 2] * (1 - a) + c.d[i1 + 2] * a);
    qa.set(c.d[i0 + 3], c.d[i0 + 4], c.d[i0 + 5], c.d[i0 + 6]).normalize(); qb.set(c.d[i1 + 3], c.d[i1 + 4], c.d[i1 + 5], c.d[i1 + 6]).normalize();
    Q[b] = (Q[b] || new T.Quaternion()).copy(qa).slerp(qb, a);
  }
}
const anim = { cur: 'idle', curT: 0, prev: null, prevT: 0, fade: 1, fireT: 9, jaw: 0, blinkAt: -9, nextBlink: 2, P: [], Q: [], P2: [], Q2: [] };
const skin = model.bones.map(() => new T.Matrix4());
const world = model.bones.map(() => new T.Matrix4());
// mood in her body (NpcSkinAnimator.posture): bones turned about their own place, everything below them with them
const isBelow = (i, of) => { for (let p = model.bones[i].parent; p >= 0; p = model.bones[p].parent) if (p === of) return true; return false; };
const PB = { s1: boneIx('mixamorigSpine1'), s2: boneIx('mixamorigSpine2'), head: B.head, ls: boneIx('mixamorigLeftShoulder'), rs: boneIx('mixamorigRightShoulder') };
const SUB = {}; for (const k in PB) SUB[k] = PB[k] < 0 ? [] : model.bones.map((_, i) => i).filter(i => i === PB[k] || isBelow(i, PB[k]));
const rad = d => d * Math.PI / 180, RX = a => new T.Matrix4().makeRotationX(rad(a)), RY = a => new T.Matrix4().makeRotationY(rad(a)), RZ = a => new T.Matrix4().makeRotationZ(rad(a));
function turnBone(k, rot) {
  const b = PB[k]; if (b < 0) return;
  const at = new T.Vector3().setFromMatrixPosition(world[b]);
  const m = new T.Matrix4().makeTranslation(at.x, at.y, at.z).multiply(rot).multiply(new T.Matrix4().makeTranslation(-at.x, -at.y, -at.z));
  for (const i of SUB[k]) world[i].premultiply(m);
}
function posture(now) {
  const fear = mind.need.fear / 100, morale = mind.need.morale / 100, tired = mind.need.tired / 100, pain = mind.need.pain / 100;
  const slump = clamp((0.45 - morale) * 1.6, 0, 0.7) + tired * 0.35 + pain * 0.35;
  const proud = clamp((morale - 0.75) * 4, 0, 1) * (1 - fear);
  let bow = slump * 12 + fear * 7 - proud * 4, headDown = slump * 14 - proud * 4;
  const dart = fear > 0.4 ? Math.sin(now * 1.7) * Math.sin(now * 0.63 + 2) * 30 * fear : 0;
  let sway = Math.sin(now * 0.8) * 3 * tired;
  const shrug = fear * 9, forward = slump * 8 + fear * 5;
  // how she feels about you: a playful tilt of the head, a softer one, chin up and turned away when jealous; until she
  // has the clips, leaning in for a hug or a kiss
  let tilt = 0, lean = 0, away = 0;
  if (bond.poseT > 0 && bond.pose === 'kiss') { tilt = 12; lean = 9; headDown -= 6; }
  else if (bond.poseT > 0 && bond.pose === 'hug') { lean = 12; tilt = 8; }
  else if (bond.jealous()) { headDown -= 6; away = 18; }
  else if (bond.flirty()) { tilt = 9 + Math.sin(now * 0.9) * 3; sway += Math.sin(now * 1.1) * 2.5; }
  else if (bond.romantic()) { tilt = 6; lean = 3; }
  bow += lean;
  turnBone('s1', RX(bow * 0.5).multiply(RZ(sway))); turnBone('s2', RX(bow * 0.5));
  turnBone('head', RX(headDown).multiply(RY(dart + away)).multiply(RZ(tilt)));
  turnBone('ls', RZ(shrug).multiply(RY(-forward))); turnBone('rs', RZ(-shrug).multiply(RY(forward)));
}
function poseHer(dt, now, want, speed) {
  const lying = work.asleep && want === 'die' && !!lie;
  if (lying && anim.cur !== 'die') { anim.prev = anim.cur; anim.prevT = anim.curT; anim.fade = 0; anim.cur = 'die'; anim.curT = clipSecs('die'); }
  if (want !== anim.cur) {
    const keep = anim.cur.endsWith('walk') && want.endsWith('walk');
    const phase = keep ? anim.curT / clipSecs(anim.cur) : 0;
    anim.prev = anim.cur; anim.prevT = anim.curT; anim.fade = 0; anim.cur = want; anim.curT = keep ? phase * clipSecs(want) : 0;
  }
  const rate = n => n === 'walk' || n === 'rifle_walk' ? clamp(speed / 1.3, 0.7, 1.6) : n === 'run' ? clamp(speed / 3.6, 0.7, 1.4) : 1;
  anim.curT += dt * rate(anim.cur); if (anim.prev) anim.prevT += dt * rate(anim.prev);
  anim.fade = Math.min(1, anim.fade + dt / 0.25);
  sample(anim.cur, anim.curT, anim.cur !== 'die', anim.P, anim.Q);
  if (anim.prev && anim.fade < 1) {
    sample(anim.prev, anim.prevT, anim.prev !== 'die', anim.P2, anim.Q2);
    const k = anim.fade * anim.fade * (3 - 2 * anim.fade);
    for (let b = 0; b < anim.P.length; b++) { anim.P[b].lerpVectors(anim.P2[b], anim.P[b], k); anim.Q[b].copy(anim.Q2[b]).slerp(anim.Q[b], k); }
  }
  const one = new T.Vector3(1, 1, 1);
  for (let b = 0; b < nb; b++) {
    if (b < anim.P.length) world[b].compose(anim.P[b], anim.Q[b], one);
    else world[b].copy(model.bones[b].inv).invert();
  }
  // lying down, her fall's slide forward is taken out so she stays on the bed
  if (lying) for (const w of world) { w.elements[12] -= lie.dx * anim.fade; w.elements[14] -= lie.dz * anim.fade; }
  if (!state.dead && !work.asleep && !['aim', 'fire'].includes(anim.cur)) posture(now);
  for (let b = 0; b < nb; b++) skin[b].multiplyMatrices(world[b], model.bones[b].inv);
  // face: the jaw while she talks, blinks, eyes shut when she's dead
  const talking = mind.talkLeft > 0;
  const target = talking ? (0.35 + 0.65 * Math.abs(Math.sin(now * 11.0) * Math.sin(now * 4.3 + 1))) * 13 : 0;
  anim.jaw += (target - anim.jaw) * Math.min(1, dt * 18);
  if (now > anim.nextBlink) { anim.blinkAt = now; anim.nextBlink = now + 2 + rnd() * 4; }
  const lid = state.dead || work.asleep || bond.poseT > 0 && bond.pose === 'kiss' ? 31 : now - anim.blinkAt < 0.13 ? 31 * Math.sin((now - anim.blinkAt) / 0.13 * Math.PI) : 0;
  for (const [name, ang] of [['jaw', anim.jaw], ['lid_l', lid], ['lid_r', lid]]) {
    const F = model.face[name]; if (!F) continue; const p = F.p;
    skin[F.idx].copy(skin[B.head]).multiply(new T.Matrix4().makeTranslation(p[0], p[1], p[2])).multiply(new T.Matrix4().makeRotationX(ang * Math.PI / 180)).multiply(new T.Matrix4().makeTranslation(-p[0], -p[1], -p[2]));
  }
  for (let b = 0; b < nb; b++) skelBones[b].matrixWorld.copy(skin[b]);
}

// ---------- her mind (a copy of NpcMind) ----------
const NEEDS = [['hunger', 'Hunger'], ['thirst', 'Thirst'], ['tired', 'Tiredness'], ['pain', 'Pain'], ['fear', 'Fear'], ['adrenaline', 'Adrenaline'], ['lonely', 'Loneliness'], ['morale', 'Morale']];
const TRAITS = [['brave', 'cautious', 'brave'], ['kind', 'cold', 'kind'], ['talkative', 'quiet', 'talkative'], ['loyal', 'independent', 'loyal'], ['optimist', 'pessimist', 'optimist']];
const LINES = {
  FOLLOW: [0, "Right behind you.", "Lead the way.", "Okay. I'm with you.", "Coming."],
  WAIT: [0, "I'll hold here.", "Don't be long.", "Waiting. Hurry back.", "I'll be right here."],
  SPOTTED: [25, "Zombies!", "Got movement!", "They're coming!", "Contact!", "Here they come."],
  FLEE: [15, "Too close, too close!", "Back off!", "I need space!", "Get away from me!"],
  RELOAD: [12, "Reloading!", "Changing mags!", "Cover me, reloading!"],
  HURT: [6, "Ah! I'm hit!", "They got me!", "I'm bleeding!", "Ow, damn it!"],
  GUN: [0, "I'll take this.", "This'll do.", "Now we're talking."],
  CLEAR: [30, "That's the last of them.", "Clear. I think.", "Quiet again."],
  HUNGRY: [180, "I'm starving. When did we last eat?", "My stomach's eating itself.", "We need food. Soon.", "I'd kill for a can of beans right now."],
  THIRSTY: [150, "I need water.", "My mouth's like sandpaper.", "Is there anything to drink?", "Water. Please."],
  TIRED: [200, "I can barely keep my eyes open.", "Can we stop somewhere? Just for a bit.", "I'm running on empty.", "If I sit down I'm not getting up."],
  PAIN: [90, "It hurts. It really hurts.", "I need something for this.", "I'm okay. I'm not okay.", "Bandages. Anything."],
  SCARED: [120, "I don't like this.", "Something's wrong here.", "Stay close. Please.", "Did you hear that?"],
  PANIC: [20, "No no no no!", "We have to go, NOW!", "I can't, I can't!", "Run!"],
  WIRED: [120, "My hands won't stop shaking.", "That was too close.", "My heart's going a mile a minute.", "Okay. Okay. Breathe."],
  LONELY: [240, "Hello? Anyone?", "It's too quiet on my own.", "Where did you go?"],
  MISSED: [300, "There you are. I thought you'd left me.", "Don't leave me alone that long again.", "Took your time.", "I was starting to worry."],
  LOW: [300, "What's even the point anymore?", "I keep thinking about before.", "We're not getting out of this, are we?"],
  GOOD: [400, "You know, we might actually make it.", "Not a bad day, all things considered.", "I'm glad I'm not doing this alone."],
  EAT: [20, "Oh, thank god.", "Mm. Better.", "That hit the spot.", "Don't tell me what's in it."],
  WORK: [20], WORK_DONE: [25], LEVEL: [0], ALARM: [0], SLEEPY: [60], WAKE: [60], NIGHTMARE: [0],
};
const REAL_TIME = new Set(['FOLLOW', 'WAIT', 'GUN', 'WORK', 'WORK_DONE', 'LEVEL', 'ALARM']);
const mind = {
  need: {}, trait: {}, diary: [], kills: 0, last: {}, lastAny: -99, lastLine: '', talkLeft: 0, line: '', lastHarv: -1e9, leftNoted: false, overrunNoted: false,
  get(n) { return this.need[n]; }, set(n, v) { this.need[n] = clamp(v, 0, 100); }, add(n, v) { this.set(n, this.need[n] + v); },
  roll() { for (const [t] of TRAITS) this.trait[t] = clamp(gauss() * 0.45, -1, 1); },
  reset() { for (const [n] of NEEDS) this.need[n] = 0; this.need.morale = 60; this.diary = []; this.kills = 0; this.last = {}; this.roll(); },
  jolt(a) { this.add('adrenaline', a); this.add('fear', a * 0.4 * (1 - 0.5 * this.trait.brave)); },
  remember(what, feel, strength) { this.diary.push({ at: clock, tick: worldTicks, what, feel, strength }); while (this.diary.length > 60) { let f = 0; this.diary.forEach((m, i) => { if (m.strength < this.diary[f].strength) f = i; }); this.diary.splice(f, 1); } drawDiary(); },
  mood() {
    const g = n => this.need[n];
    if (g('fear') > 75) return 'terrified'; if (g('pain') > 70) return 'in agony'; if (g('adrenaline') > 70) return 'wired';
    if (g('thirst') > 80) return 'parched'; if (g('hunger') > 80) return 'starving'; if (g('tired') > 80) return 'exhausted';
    if (g('fear') > 45) return 'on edge'; if (g('lonely') > 65) return 'lonely'; if (g('morale') < 25) return 'hopeless';
    if (g('morale') < 40) return 'low'; if (g('morale') > 75) return 'in good spirits'; return 'steady';
  },
  pace() { const g = n => this.need[n]; return clamp(1 - g('pain') / 300 - Math.max(0, g('tired') - 50) / 200 - (g('hunger') > 85 ? 0.12 : 0) + g('adrenaline') / 600, 0.6, 1.15); },
  wobble() { const g = n => this.need[n]; return 1 + g('pain') / 100 + g('tired') / 150 + g('fear') / 120 - Math.min(g('adrenaline'), 40) / 200; },
  flightScale() { return clamp(1 + this.need.fear / 120 - 0.3 * this.trait.brave, 0.6, 2.2); },
  panicking() { return this.need.fear > 88 + 10 * this.trait.brave; },
  traitWords() { const out = []; for (const [t, lo, hi] of TRAITS) { const v = this.trait[t]; if (Math.abs(v) >= 0.25) out.push((Math.abs(v) > 0.65 ? 'very ' : '') + (v > 0 ? hi : lo)); } return out.length ? out.join(', ') : 'even-tempered'; },
  say(kind, text) {
    if (state.dead && kind !== 'HURT') return false;
    const [wait, ...lines] = LINES[kind]; const w = wait / (REAL_TIME.has(kind) ? 1 : rateOf());
    if (kind !== 'ALARM' && kind !== 'REPLY' && clock - this.lastAny < 3 || this.last[kind] != null && clock - this.last[kind] < w) return false;
    let line = text || lines[Math.floor(rnd() * lines.length)]; if (!text && line === this.lastLine && lines.length > 1) line = lines[(lines.indexOf(line) + 1) % lines.length];
    this.last[kind] = clock; this.lastAny = clock; this.lastLine = line; this.line = line; this.talkLeft = (10 + line.length * 2) / 20;
    const d = document.createElement('div'); d.innerHTML = `<span>&lt;${esc(name())}&gt;</span> `; d.append(line); const chat = $('chat'); chat.prepend(d); while (chat.children.length > 40) chat.lastChild.remove();
    return true;
  },
};
const esc = s => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const name = () => $('name').value.trim() || 'Companion';
const rateOf = () => +$('rate').value;
let clock = 0;

function mindTick() {
  const r = rateOf(), m = mind;
  const moving = state.moving, sprinting = state.speed > 2.4;
  const threat = threatAround();
  const pd = player.present ? her.position.distanceTo(player.obj.position) : 1e9;
  m.add('hunger', 100 / (1.5 * 1200) * (sprinting ? 2 : 1) * r);
  m.add('thirst', 100 / 1200 * (sprinting ? 2.5 : 1) * r);
  const resting = !state.following && !moving && threat < 5;
  m.add('tired', work.asleep ? -0.6 * r : resting ? -0.15 * r : 100 / 1200 * (sprinting ? 2.5 : 1) * r);
  const wound = (1 - state.hp / 30) * 100, pain = m.need.pain; m.set('pain', wound > pain ? pain + (wound - pain) * 0.5 : pain - 0.6);
  const target = threat * (1 - 0.35 * m.trait.brave) + (m.need.pain > 60 ? 10 : 0), fear = m.need.fear;
  m.set('fear', target > fear ? fear + (target - fear) * 0.5 : fear - 1.5);
  m.add('adrenaline', -1.5);
  const company = pd < 6, anyone = pd < 12;
  m.add('lonely', (company ? -0.5 : anyone || work.asleep ? 0 : 0.12 * (1 + 0.4 * m.trait.talkative)) * r);
  const aim = 60 + 15 * m.trait.optimist - 0.2 * m.need.hunger - 0.25 * m.need.thirst - 0.15 * m.need.tired - 0.25 * m.need.pain - 0.2 * m.need.fear - 0.2 * m.need.lonely;
  m.set('morale', m.need.morale + clamp(aim - m.need.morale, -1, 1) * (0.06 + 0.03 * m.trait.optimist) * r);
  if (m.need.lonely > 80 && !m.leftNoted) { m.leftNoted = true; m.remember('Left on my own for ages', -0.4, 0.5); } else if (m.need.lonely < 30) m.leftNoted = false;
  if (m.need.fear > 90 && !m.overrunNoted) { m.overrunNoted = true; m.remember('Nearly overrun', -0.6, 0.7); } else if (m.need.fear < 40) m.overrunNoted = false;
  // a game day (twenty minutes) fades her weak memories
  m.fadeT = (m.fadeT || 0) + r; if (m.fadeT >= 1200) { m.fadeT = 0; m.diary.forEach(d => { if (d.strength < 0.6) d.strength -= 0.12; }); m.diary = m.diary.filter(d => d.strength > 0); drawDiary(); }
  if (!work.asleep) speakMind(company);
  watch();
  bondTick();
  wardrobeTick();
}
function threatAround() {
  let t = 0;
  for (const z of zombies) if (z.alive) t += Math.max(0, 1 - z.obj.position.distanceTo(her.position) / 16) * 22;
  if (!state.lights) t += 12;
  if (harv.on) { t += 70 + Math.max(0, 1 - harv.obj.position.distanceTo(her.position) / 40) * 30;
    if (clock - mind.lastHarv > 300 && harv.obj.position.z > -8) { mind.lastHarv = clock; mind.remember('Saw the Harvestman', -0.9, 1); } }
  return Math.min(100, t);
}
let wasLonely = false;
function speakMind(close) {
  const m = mind;
  if (wasLonely && close) { m.say('MISSED'); wasLonely = m.need.lonely > 50; return; }
  wasLonely = m.need.lonely > 50;
  if (m.panicking()) { m.say('PANIC'); return; }
  if (rnd() > 0.06 * (1 + 0.7 * m.trait.talkative) * Math.min(rateOf(), 10)) return;
  let loud = null, most = 0;
  for (const [n] of NEEDS) { const v = n === 'morale' ? 100 - m.need[n] : m.need[n]; if (v > most) { most = v; loud = n; } }
  let kind = null;
  if (loud && most > 60) kind = { hunger: 'HUNGRY', thirst: 'THIRSTY', tired: 'TIRED', pain: 'PAIN', fear: 'SCARED', adrenaline: 'WIRED', lonely: close ? null : 'LONELY', morale: m.need.morale < 30 ? 'LOW' : null }[loud];
  else if (m.need.morale > 78 && close) kind = 'GOOD';
  if (kind) m.say(kind);
}

// ---------- the player ----------
const player = { present: true, hp: 20, target: new T.Vector3(-1.9, 0, 2.4), obj: new T.Group() };
{ const mat = new T.MeshStandardMaterial({ color: 0x6d7f96, roughness: 0.7 }), skinMat = new T.MeshStandardMaterial({ color: 0xb08a6e, roughness: 0.7 });
  const torso = new T.Mesh(new T.CapsuleGeometry(0.2, 0.6, 6, 14), mat); torso.position.y = 1.15; torso.castShadow = true; player.obj.add(torso);
  const head = new T.Mesh(new T.SphereGeometry(0.12, 18, 14), skinMat); head.position.y = 1.68; head.castShadow = true; player.obj.add(head);
  for (const sx of [-0.1, 0.1]) { const leg = new T.Mesh(new T.CapsuleGeometry(0.075, 0.6, 4, 10), new T.MeshStandardMaterial({ color: 0x2b2f36 })); leg.position.set(sx, 0.42, 0); leg.castShadow = true; player.obj.add(leg); }
  const ring = new T.Mesh(new T.RingGeometry(0.34, 0.38, 40), new T.MeshBasicMaterial({ color: 0xe0c25e, transparent: true, opacity: 0.55 })); ring.rotation.x = -Math.PI / 2; ring.position.y = 0.01; player.obj.add(ring);
  player.obj.position.copy(player.target); scene.add(player.obj); }
const marker = new T.Mesh(new T.RingGeometry(0.12, 0.16, 30), new T.MeshBasicMaterial({ color: 0xe0c25e, transparent: true, opacity: 0.8 })); marker.rotation.x = -Math.PI / 2; marker.position.y = 0.012; marker.visible = false; scene.add(marker);

// ---------- zombies and the Harvestman ----------
const zombies = [];
function makeZombie() {
  const g = new T.Group(), hue = 0.22 + rnd() * 0.08;
  const skinM = new T.MeshStandardMaterial({ color: new T.Color().setHSL(hue, 0.18, 0.38), roughness: 0.9 });
  const cloth = new T.MeshStandardMaterial({ color: new T.Color().setHSL(rnd(), 0.25, 0.22), roughness: 1 });
  const pants = new T.MeshStandardMaterial({ color: new T.Color().setHSL(0.6, 0.15, 0.15), roughness: 1 });
  const part = (geo, mat, x, y, z, parent = g) => { const m = new T.Mesh(geo, mat); m.position.set(x, y, z); m.castShadow = true; parent.add(m); return m; };
  const hips = new T.Group(); hips.position.y = 0.95; g.add(hips);
  const chest = new T.Group(); chest.position.y = 0.05; chest.rotation.x = 0.25; hips.add(chest);
  part(new T.BoxGeometry(0.4, 0.55, 0.22), cloth, 0, 0.3, 0, chest);
  const head = part(new T.SphereGeometry(0.12, 14, 12), skinM, 0, 0.7, 0.04, chest); head.scale.set(0.9, 1.1, 1);
  part(new T.SphereGeometry(0.018, 6, 6), new T.MeshBasicMaterial({ color: 0x9fb0a0 }), -0.045, 0.72, 0.15, chest); part(new T.SphereGeometry(0.018, 6, 6), new T.MeshBasicMaterial({ color: 0x9fb0a0 }), 0.045, 0.72, 0.15, chest);
  const arms = [];
  for (const sx of [-0.25, 0.25]) { const sh = new T.Group(); sh.position.set(sx, 0.52, 0); sh.rotation.x = -1.25 + rnd() * 0.3; chest.add(sh); part(new T.CapsuleGeometry(0.05, 0.5, 4, 8), skinM, 0, -0.3, 0, sh); arms.push(sh); }
  const legs = [];
  for (const sx of [-0.1, 0.1]) { const hip = new T.Group(); hip.position.set(sx, 0, 0); hips.add(hip); part(new T.CapsuleGeometry(0.07, 0.72, 4, 8), pants, 0, -0.47, 0, hip); legs.push(hip); }
  return { obj: g, hips, chest, arms, legs };
}
function spawnZombie() {
  const z = makeZombie(); z.obj.position.set((rnd() - 0.5) * 1.0, 0, -9 - rnd() * 2); scene.add(z.obj);
  Object.assign(z, { alive: true, hp: 3, hitT: 0, phase: rnd() * 6, deadT: 0, speed: 0.6 + rnd() * 0.3 }); zombies.push(z);
}
const harv = { on: false, t: 0, obj: new T.Group() };
{ const black = new T.MeshStandardMaterial({ color: 0x0a0a0b, roughness: 0.5, metalness: 0.3 }), red = new T.MeshBasicMaterial({ color: 0xff2a1a });
  const hull = new T.Mesh(new T.SphereGeometry(0.55, 24, 16), black); hull.scale.set(1.2, 0.6, 1.5); hull.position.y = 2.5; harv.obj.add(hull);
  for (let i = 0; i < 6; i++) { const e = new T.Mesh(new T.SphereGeometry(0.035, 8, 8), red); e.position.set(-0.15 + (i % 3) * 0.15, 2.6 + (i < 3 ? 0.06 : -0.04), 0.78); harv.obj.add(e); }
  harv.legs = [];
  for (let i = 0; i < 8; i++) { const side = i < 4 ? -1 : 1, k = i % 4; const leg = new T.Group(); leg.position.set(side * 0.5, 2.45, -0.5 + k * 0.33); harv.obj.add(leg);
    const up = new T.Mesh(new T.CylinderGeometry(0.03, 0.05, 1.6, 8), black); up.position.set(side * 0.55, 0.45, 0); up.rotation.z = side * -1.0; leg.add(up);
    const low = new T.Mesh(new T.CylinderGeometry(0.012, 0.03, 2.8, 8), black); low.position.set(side * 1.35, -0.9, 0); low.rotation.z = side * 0.25; leg.add(low); harv.legs.push(leg); }
  const eyeGlow = new T.PointLight(0xff2010, 0, 6, 2); eyeGlow.position.set(0, 2.6, 1); harv.obj.add(eyeGlow); harv.glow = eyeGlow;
  harv.obj.position.set(0, 0, -16); harv.obj.visible = false; scene.add(harv.obj); }

// ---------- food and water on the floor ----------
const items = [];
function dropItem(kind) {
  const m = kind === 'food'
    ? new T.Mesh(new T.CylinderGeometry(0.05, 0.05, 0.11, 18), new T.MeshStandardMaterial({ color: 0xa04a2a, metalness: 0.6, roughness: 0.35 }))
    : new T.Mesh(new T.CylinderGeometry(0.04, 0.045, 0.26, 16), new T.MeshStandardMaterial({ color: 0x8fb7d6, transparent: true, opacity: 0.75, roughness: 0.1 }));
  const p = player.present ? player.obj.position : her.position;
  m.position.set(p.x + (rnd() - 0.5) * 0.8, kind === 'food' ? 0.055 : 0.13, p.z + 0.6); m.castShadow = true; scene.add(m);
  items.push({ kind, obj: m, hunger: kind === 'food' ? 30 : 0, thirst: kind === 'food' ? 0 : 40, label: kind === 'food' ? 'a can of beans' : 'a bottle of water' });
}

// ---------- home: her bed, the stores chest, planters, a campfire, a boarded window, and places to scavenge outside ----------
const HOME = new T.Vector3(0, 0, 0.5);
const pick = (...a) => a[Math.floor(rnd() * a.length)], rint = n => Math.floor(rnd() * n);
const wood = new T.MeshStandardMaterial({ map: crateTex, roughness: 0.85 }), darkWood = new T.MeshStandardMaterial({ color: 0x3b2c1e, roughness: 0.8 });
const box = (w, h, d, mat, x, y, z, parent = room) => { const m = new T.Mesh(new T.BoxGeometry(w, h, d), mat); m.position.set(x, y, z); m.castShadow = m.receiveShadow = true; parent.add(m); return m; };
// the bed, head against the back wall
const BED = { x: 6.15, z: 2.3, top: 0.51 }, BED_FOOT = new T.Vector3(BED.x - 0.1, 0, BED.z - 1.45);
{ box(0.96, 0.3, 2.0, darkWood, BED.x, 0.2, BED.z);
  for (const [dx, dz] of [[-0.43, -0.95], [0.43, -0.95], [-0.43, 0.95], [0.43, 0.95]]) box(0.06, 0.1, 0.06, metal, BED.x + dx, 0.05, BED.z + dz);
  box(0.9, 0.16, 1.92, new T.MeshStandardMaterial({ color: 0x8a8270, roughness: 0.95 }), BED.x, 0.43, BED.z);
  box(0.96, 0.85, 0.07, darkWood, BED.x, 0.42, BED.z + 1.0);
  box(0.6, 0.1, 0.32, new T.MeshStandardMaterial({ color: 0xb9b2a0, roughness: 1 }), BED.x, 0.56, BED.z + 0.72);
  const blanket = box(0.98, 0.05, 1.1, new T.MeshStandardMaterial({ color: 0x4a5236, roughness: 1 }), BED.x, 0.53, BED.z - 0.4); blanket.rotation.z = 0.03;
  obstacles.push({ x: BED.x, z: BED.z + 0.35, r: 0.5, h: 0.6 }); }
// where she lies: the end of her fall clip, its slide forward taken out, turned so her head is on the pillow
const lie = (() => {
  if (!model.clips.die) return null;
  const P = [], Q = [], P0 = [], Q0 = [];
  sample('die', clipSecs('die'), false, P, Q); sample('die', 0, false, P0, Q0);
  const hips = boneIx('mixamorigHips'), toe = boneIx('mixamorigLeftToeBase');
  const dx = P[hips].x - P0[hips].x, dz = P[hips].z - P0[hips].z, back = new T.Vector3(dx, 0, dz);
  const head = P[B.head].clone().sub(back), feet = P[toe >= 0 && toe < P.length ? toe : hips].clone().sub(back);
  const ang = Math.atan2(head.x - feet.x, head.z - feet.z), yaw = -ang;
  const mid = head.clone().add(feet).multiplyScalar(0.5).setY(0).applyAxisAngle(new T.Vector3(0, 1, 0), yaw);
  return { dx, dz, yaw, pos: new T.Vector3(BED.x - mid.x, BED.top, BED.z - 0.15 - mid.z) };
})();
// three planters along the west wall
const CROPS = [['carrot', 'carrots', 0xd9792b], ['potato', 'potatoes', 0xb59a62], ['beet', 'beetroot', 0x8a2340]];
const planters = [];
for (const z of [-0.5, 0.75, 2.0]) {
  const g = new T.Group(); g.position.set(-6.2, 0, z); room.add(g);
  box(0.95, 0.42, 0.95, wood, 0, 0.21, 0, g);
  const soil = box(0.85, 0.04, 0.85, new T.MeshStandardMaterial({ color: 0x5a4330, roughness: 1 }), 0, 0.42, 0, g);
  const plants = new T.Group(); plants.position.y = 0.44; g.add(plants);
  const leafMat = new T.MeshStandardMaterial({ color: 0x5f8a3a, roughness: 0.8 }), fruitMat = new T.MeshStandardMaterial({ color: 0xd9792b, roughness: 0.6 });
  const sprigs = [];
  for (let i = 0; i < 9; i++) {
    const s = new T.Group(); s.position.set(-0.27 + (i % 3) * 0.27, 0, -0.27 + Math.floor(i / 3) * 0.27); s.rotation.y = rnd() * 6; plants.add(s);
    for (let k = 0; k < 4; k++) { const leaf = new T.Mesh(new T.ConeGeometry(0.03, 0.3, 5), leafMat); leaf.position.set(0.04 * Math.cos(k * 1.6), 0.15, 0.04 * Math.sin(k * 1.6)); leaf.rotation.set(0.4 * Math.sin(k * 1.6), 0, -0.4 * Math.cos(k * 1.6)); leaf.castShadow = true; s.add(leaf); }
    const fruit = new T.Mesh(new T.SphereGeometry(0.055, 10, 8), fruitMat); fruit.position.y = 0.03; s.add(fruit); s.fruit = fruit; sprigs.push(s);
  }
  obstacles.push({ x: -6.2, z, r: 0.45, h: 0.45 });
  planters.push({ g, soil, plants, sprigs, fruitMat, crop: null, age: 0, wet: false, stand: new T.Vector3(-5.05, 0, z) });
}
function drawPlanter(p) {
  p.soil.material.color.set(p.wet ? 0x2a1d13 : 0x6a5038); p.plants.visible = !!p.crop;
  if (p.crop) { const k = 0.2 + 0.8 * p.age / 7; p.sprigs.forEach(s => { s.scale.setScalar(k); s.fruit.visible = p.age >= 7; }); p.fruitMat.color.set(p.crop[2]); }
}
// a campfire with a pot on a tripod
const fire = { x: 2.4, z: -3.0, lit: true, flames: [] };
{ const g = new T.Group(); g.position.set(fire.x, 0, fire.z); room.add(g);
  const stone = new T.MeshStandardMaterial({ color: 0x55524c, roughness: 0.95 });
  for (let i = 0; i < 10; i++) { const a = i / 10 * Math.PI * 2; const s = new T.Mesh(new T.DodecahedronGeometry(0.08 + rnd() * 0.04), stone); s.position.set(Math.cos(a) * 0.38, 0.06, Math.sin(a) * 0.38); s.rotation.set(rnd() * 3, rnd() * 3, 0); s.castShadow = true; g.add(s); }
  const logMat = new T.MeshStandardMaterial({ color: 0x2d2118, roughness: 1 });
  for (let i = 0; i < 3; i++) { const l = new T.Mesh(new T.CylinderGeometry(0.04, 0.045, 0.6, 8), logMat); l.rotation.set(0, i * Math.PI / 3, Math.PI / 2); l.position.y = 0.06 + i * 0.03; g.add(l); }
  fire.ember = new T.Mesh(new T.CircleGeometry(0.28, 20), new T.MeshBasicMaterial({ color: 0x5a1a06 })); fire.ember.rotation.x = -Math.PI / 2; fire.ember.position.y = 0.012; g.add(fire.ember);
  const flameMat = new T.MeshBasicMaterial({ color: 0xff8a2a, transparent: true, opacity: 0.8, blending: T.AdditiveBlending, depthWrite: false });
  for (let i = 0; i < 6; i++) { const f = new T.Mesh(new T.ConeGeometry(0.06 + rnd() * 0.04, 0.34, 8, 1, true), flameMat); f.position.set((rnd() - 0.5) * 0.2, 0.2, (rnd() - 0.5) * 0.2); g.add(f); fire.flames.push(f); }
  fire.light = new T.PointLight(0xff8a3a, 5, 7, 1.6); fire.light.position.set(0, 0.5, 0); g.add(fire.light);
  for (let i = 0; i < 3; i++) { const a = i / 3 * Math.PI * 2; const leg = new T.Mesh(new T.CylinderGeometry(0.012, 0.012, 1.0, 6), metal); leg.position.set(Math.cos(a) * 0.3, 0.46, Math.sin(a) * 0.3); leg.lookAt(0, 0.95, 0); leg.rotateX(Math.PI / 2); g.add(leg); }
  const pot = new T.Mesh(new T.CylinderGeometry(0.13, 0.11, 0.17, 18), new T.MeshStandardMaterial({ color: 0x24262a, metalness: 0.6, roughness: 0.5 })); pot.position.y = 0.55; pot.castShadow = true; g.add(pot);
  obstacles.push({ x: fire.x, z: fire.z, r: 0.45, h: 0.3 }); }
function drawFire(t) {
  fire.flames.forEach((f, i) => { f.visible = fire.lit; f.scale.set(1, 0.6 + 0.6 * Math.abs(Math.sin(t * 7 + i * 1.7)), 1); f.rotation.y = t * 2 + i; });
  fire.light.intensity = fire.lit ? 5 + Math.sin(t * 13) * 1.1 + Math.sin(t * 7.3) * 0.8 : 0; fire.ember.material.color.set(fire.lit ? 0x8a2a08 : 0x1c1a18);
}
// the stores: a chest against the east wall, its lid lifts while she's in it
const chest = { x: 6.3, z: -1.9, openT: 0, open: 0 };
{ const g = new T.Group(); g.position.set(chest.x, 0, chest.z); g.rotation.y = -Math.PI / 2; room.add(g);
  box(0.9, 0.42, 0.5, wood, 0, 0.21, 0, g);
  for (const x of [-0.38, 0.38]) box(0.04, 0.44, 0.52, metal, x, 0.22, 0, g);
  chest.lid = new T.Group(); chest.lid.position.set(0, 0.42, -0.25); g.add(chest.lid);
  box(0.9, 0.08, 0.5, wood, 0, 0.04, 0.25, chest.lid); box(0.08, 0.07, 0.03, metal, 0, 0, 0.51, chest.lid);
  obstacles.push({ x: chest.x, z: chest.z, r: 0.45, h: 0.5 }); }
const CHEST_AT = new T.Vector3(chest.x - 1.2, 0, chest.z - 0.15);
// a boarded-up window in the west wall: boards come off as it's beaten, she nails them back
const barricade = { z: -4.2, damage: 2, strength: 0, boards: [] };
{ const g = new T.Group(); g.position.set(-6.88, 0, barricade.z); room.add(g);
  const opening = new T.Mesh(new T.PlaneGeometry(1.1, 1.0), new T.MeshBasicMaterial({ color: 0x0b1018 })); opening.rotation.y = Math.PI / 2; opening.position.y = 1.45; g.add(opening);
  for (const [y, h, w] of [[0.93, 0.06, 1.24], [1.97, 0.06, 1.24]]) box(0.06, h, w, darkWood, 0.01, y, 0, g);
  for (const z of [-0.59, 0.59]) box(0.06, 1.1, 0.06, darkWood, 0.01, 1.45, z, g);
  barricade.sheet = box(0.02, 0.98, 1.08, new T.MeshStandardMaterial({ color: 0x6b6f72, metalness: 0.7, roughness: 0.45 }), 0.025, 1.45, 0, g);
  const plank = new T.MeshStandardMaterial({ color: 0x8a6a45, roughness: 0.85 });
  for (const [y, tilt] of [[1.08, 0.08], [1.33, -0.05], [1.58, 0.06], [1.83, -0.09]]) { const b = box(0.04, 0.17, 1.4, plank, 0.06, y, 0, g); b.rotation.x = tilt; barricade.boards.push(b); }
}
function drawBarricade() { barricade.boards.forEach((b, i) => { b.visible = i >= barricade.damage; b.scale.x = barricade.strength >= 1 ? 2 : 1; }); barricade.sheet.visible = barricade.strength >= 2; }
// outside, past the door: a locker, a crate and a cabinet nobody's emptied yet
const LOOT = { tins: [4, 2, 'tins'], water: [4, 1, 'water'], bandages: [5, 2, 'bandages'], planks: [2, 4, 'planks'], seeds: [3, 3, 'seeds'], raw: [3, 2, 'raw meat'], scrap: [1, 3, 'scrap'] };
const outside = [];
for (const [x, z, name, w, h, d, mat] of [[-2.8, -10.5, 'the locker', 0.6, 1.8, 0.5, metal], [2.9, -12, 'the crate', 0.8, 0.6, 0.6, wood], [-1.6, -14.2, 'the cabinet', 1.0, 0.9, 0.5, darkWood]]) {
  const g = new T.Group(); g.position.set(x, 0, z); scene.add(g);
  box(w, h, d, mat, 0, h / 2, 0, g);
  const door = new T.Group(); door.position.set(-w / 2, 0, d / 2); g.add(door); box(w, h * 0.9, 0.03, darkWood, w / 2, h / 2, 0.015, door);
  outside.push({ g, door, name, loot: [], searched: false, stand: new T.Vector3(x, 0, z + d / 2 + 0.55) });
}
function restockOutside() { for (const o of outside) { o.searched = false; o.door.rotation.y = 0; o.loot = []; for (let i = 0, n = 3 + rint(3); i < n; i++) o.loot.push(pick(...Object.keys(LOOT))); } }

// ---------- her skills (a copy of NpcSkills) ----------
const SKILLS = [['aim', 'Aim'], ['aid', 'First aid'], ['cooking', 'Cooking'], ['carpentry', 'Carpentry'], ['scavenging', 'Scavenging'], ['farming', 'Farming']];
const LEVEL_LINES = { aim: l => l >= 8 ? "I don't miss much anymore." : "My aim's getting better.", aid: () => "I'm getting the hang of patching people up.",
  cooking: l => l >= 8 ? 'Honestly? I could open a restaurant.' : 'I think I\'m getting better at this cooking thing.', carpentry: () => 'My boards are holding better these days.',
  scavenging: () => "I'm starting to know where people hid things.", farming: () => "Turns out I've got a green thumb." };
const xpFor = l => 30 * l * l;
const skills = {
  xp: {},
  level(s) { return Math.min(10, Math.floor(Math.sqrt(this.xp[s] / 30))); },
  progress(s) { const l = this.level(s); return l >= 10 ? 1 : (this.xp[s] - xpFor(l)) / (xpFor(l + 1) - xpFor(l)); },
  roll() { for (const [s] of SKILLS) this.xp[s] = xpFor(rint(3)); this.xp[SKILLS[rint(SKILLS.length)][0]] = xpFor(3 + rint(2)); },
  set(s, l) { this.xp[s] = xpFor(clamp(l, 0, 10)); },
};
function practise(s, amount) {
  const before = skills.level(s); skills.xp[s] += amount; const after = skills.level(s);
  if (after > before) {
    mind.say('LEVEL', LEVEL_LINES[s](after));
    if (after === 5 || after === 10) mind.remember((after === 10 ? 'Mastered ' : 'Got good at ') + SKILLS.find(k => k[0] === s)[1].toLowerCase(), 0.5, 0.7);
    mind.add('morale', 3);
  }
}

// ---------- her job, her day and her nights (a copy of NpcWork) ----------
const JOBS = [['none', 'No job: keeps you company'], ['guard', 'Guard'], ['repair', 'Carpenter'], ['farm', 'Farmer'], ['cook', 'Cook'], ['scavenge', 'Scavenger'], ['medic', 'Medic'], ['haul', 'Hauler']];
const KIND_LABEL = { planks: 'Planks', raw: 'Raw meat', cooked: 'Hot meals', tins: 'Tins', produce: 'Veg', water: 'Water', seeds: 'Seeds', bandages: 'Bandages', scrap: 'Scrap' };
const FOOD_OF = { cooked: { hunger: 40, thirst: 0, label: 'a hot meal' }, produce: { hunger: 18, thirst: 4, label: 'some veg' }, tins: { hunger: 30, thirst: 0, label: 'a can of beans' }, raw: { hunger: 12, thirst: 0, label: 'raw meat' }, water: { hunger: 0, thirst: 40, label: 'a bottle of water' } };
const ACTION_WORDS = { sleep: 'asleep', sit: 'sitting', kneel: 'kneeling', hammer: 'hammering', dig: 'digging', search: 'searching', lookout: 'keeping watch', cooking: 'cooking' };
const work = { job: 'none', night: false, task: null, sleeping: false, asleep: false, slept: 0, nightmareAt: -1, action: '', label: '', grumbled: {}, lastAlarm: -1e9, lastOut: -1e9, run: 0, post: 0, treated: {}, eatCd: 0, pack: {} };
const stores = {}, pack = work.pack;
for (const k in KIND_LABEL) { stores[k] = 0; pack[k] = 0; }
const stockUp = () => { for (const [k, n] of [['planks', 6], ['raw', 4], ['tins', 3], ['water', 4], ['seeds', 6], ['bandages', 2]]) stores[k] += n; };
const packEmpty = () => Object.values(pack).every(n => !n), packKinds = () => Object.values(pack).filter(n => n > 0).length;
const ticksToS = t => t / 20;
let worldTicks = 0;
// the same clock as the wristwatch: tick 0 is 09:00; her day counts from 06:00
const dayTick = () => (((worldTicks + 3000) % 24000) + 24000) % 24000;
function phase() {
  const t = dayTick();
  if (!work.night) return t < 11000 ? 'work' : t < 15000 ? 'free' : 'sleep';
  return t >= 13000 && t < 23000 ? 'work' : (t >= 23000 || t < 3000) ? 'free' : 'sleep';
}
const PHASE_START = { day: { work: 0, free: 11000, sleep: 15000 }, night: { work: 13000, free: 23000, sleep: 3000 } };
function skipTo(p) { const want = PHASE_START[work.night ? 'night' : 'day'][p]; worldTicks += (((want - dayTick()) % 24000) + 24000) % 24000 + 20; }
function watchTime() { const minutes = Math.floor(worldTicks * 1440 / 24000) + 9 * 60; return `Day ${Math.floor(minutes / 1440) + 1}, ${String(Math.floor(minutes / 60) % 24).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`; }
function grumble(text) { if (clock - (work.grumbled[text] ?? -1e9) > 300) { work.grumbled[text] = clock; mind.say('WORK', text); } }
function newTask(at, secs, action, reach, done, label, face) { return { at, secs, action, reach, done, label, face, t: 0, travel: 0 }; }
function fetchTask(kind, n, moan) {
  if (stores[kind] <= 0) { if (moan) grumble(moan); return null; }
  return newTask(CHEST_AT, 1, 'search', 0.5, () => { const k = Math.min(n, stores[kind]); stores[kind] -= k; pack[kind] += k; }, 'getting ' + KIND_LABEL[kind].toLowerCase() + ' from the stores', CHEST_AT.clone().setX(chest.x));
}
function depositTask(after) { return newTask(CHEST_AT, 1, 'search', 0.5, () => { for (const k in pack) { stores[k] += pack[k]; pack[k] = 0; } after?.(); }, 'putting things in the stores', CHEST_AT.clone().setX(chest.x)); }
function haul(keepBandages) {
  const loose = items.filter(it => it.obj.position.distanceTo(HOME) < 20).sort((a, b) => a.obj.position.distanceTo(her.position) - b.obj.position.distanceTo(her.position))[0];
  if (!loose) return packEmpty() || keepBandages && Object.keys(pack).every(k => k === 'bandages' || !pack[k]) ? null : depositTask();
  return newTask(loose.obj.position, 0.4, 'kneel', 0.6, () => { if (!items.includes(loose)) return; pack[loose.kind === 'food' ? 'tins' : 'water']++; scene.remove(loose.obj); items.splice(items.indexOf(loose), 1); }, 'picking up ' + loose.label);
}
function plan() {
  switch (work.job) {
    case 'guard': {
      const posts = [[new T.Vector3(0, 0, -D / 2 + 1.2), new T.Vector3(0, 0, -20), 'watching the door'], [new T.Vector3(-5.9, 0, barricade.z), new T.Vector3(-20, 0, barricade.z), 'watching the window']];
      const [at, face, label] = posts[work.post++ % posts.length];
      return newTask(at, 20, 'lookout', 0.5, null, label, face);
    }
    case 'repair': {
      if (barricade.damage <= 0) return packEmpty() ? null : depositTask();
      if (!pack.planks) return fetchTask('planks', 6, "We're out of planks. I can't fix anything without them.");
      const carp = skills.level('carpentry');
      return newTask(new T.Vector3(-5.9, 0, barricade.z), ticksToS(Math.max(30, 140 - carp * 10)), 'hammer', 0.5, () => {
        if (barricade.damage <= 0 || !pack.planks) return;
        pack.planks--; barricade.damage = 0; barricade.strength = Math.max(barricade.strength, carp >= 8 ? 2 : carp >= 4 ? 1 : 0); drawBarricade();
        practise('carpentry', 6); mind.say('WORK_DONE', pick("That'll hold.", 'Good as new. Well, nearly.', 'Patched.'));
      }, 'fixing the barricade', new T.Vector3(-20, 0, barricade.z));
    }
    case 'farm': {
      const farming = skills.level('farming');
      const ripe = planters.find(p => p.crop && p.age >= 7), dry = planters.find(p => !p.wet && !(p.crop && p.age >= 7)), empty = planters.find(p => !p.crop && p.wet);
      const face = p => p.g.position.clone();
      if (!ripe && !dry && !empty && !packEmpty() && !pack.seeds && !pack.water) return depositTask();
      if (ripe) return newTask(ripe.stand, 3, 'kneel', 0.5, () => {
        if (!ripe.crop || ripe.age < 7) return;
        pack.produce += (ripe.crop[0] === 'beet' ? 2 : 3) + rint(3) + (rint(10) < farming ? 1 : 0);
        if (rnd() < 0.6 + farming * 0.03) pack.seeds++;
        ripe.crop = null; ripe.age = 0; drawPlanter(ripe); practise('farming', 6);
      }, 'harvesting ' + ripe.crop[1], face(ripe));
      if (dry) {
        if (!pack.water) { const f = fetchTask('water', 4, "Everything's drying out. I need water bottles or a rain barrel."); if (f) return f; }
        else return newTask(dry.stand, 2, 'kneel', 0.5, () => { if (dry.wet || !pack.water) return; pack.water--; dry.wet = true; drawPlanter(dry); practise('farming', 2); }, 'watering a planter', face(dry));
      }
      if (empty) {
        if (!pack.seeds) return fetchTask('seeds', 8, 'No seeds left to plant.');
        return newTask(empty.stand, 2, 'dig', 0.5, () => { if (empty.crop || !pack.seeds) return; pack.seeds--; empty.crop = pick(...CROPS); empty.age = 0; drawPlanter(empty); practise('farming', 3); }, 'planting', face(empty));
      }
      return packEmpty() ? null : depositTask();
    }
    case 'cook': {
      if (!fire.lit) {
        if (pack.raw || stores.raw) grumble("I can't cook without a fire. A campfire or a stove, anything.");
        return packEmpty() ? null : depositTask();
      }
      if (pack.raw) {
        const cooking = skills.level('cooking');
        return newTask(new T.Vector3(fire.x - 1.0, 0, fire.z + 0.5), ticksToS(Math.max(60, 200 - cooking * 12)), 'cooking', 0.5, () => {
          const n = pack.raw; let extra = 0; for (let k = 0; k < n; k++) if (rint(25) < cooking) extra++;
          pack.raw = 0; pack.cooked += n + extra; practise('cooking', 3 + n + extra);
          mind.say('WORK_DONE', pick("Food's up!", "Dinner. Don't complain.", 'Hot food, come and get it.'));
        }, 'cooking at the campfire', new T.Vector3(fire.x, 0, fire.z));
      }
      if (!packEmpty()) return depositTask();
      return fetchTask('raw', 6, "There's nothing in the stores to cook.");
    }
    case 'scavenge': {
      if (work.run >= 3 || packKinds() >= 9) {
        const haul = packKinds();
        return depositTask(() => { work.run = 0; if (haul > 0) { mind.say('WORK_DONE', pick("I'm back. Got a few things.", 'Back in one piece. Look what I found.', 'Made it back. Stores are a bit fuller.')); mind.remember('Scavenging run, came back with ' + haul + ' lots of things', 0.3, 0.3); } });
      }
      const left = outside.filter(o => !o.searched);
      if (!left.length) { if (!packEmpty()) return depositTask(() => { work.run = 0; }); grumble("I've picked everything round here clean. We'll have to go further out."); return null; }
      const best = left.reduce((a, b) => a.stand.distanceTo(her.position) <= b.stand.distanceTo(her.position) ? a : b);
      if (work.run === 0 && clock - work.lastOut > 120) { work.lastOut = clock; mind.say('WORK', pick('Heading out. Back soon.', 'Going for a look round. Keep the door shut.', "Supply run. Don't wait up.")); }
      const scav = skills.level('scavenging');
      return newTask(best.stand, ticksToS(Math.max(40, 140 - scav * 9)), 'search', 0.5, () => {
        best.searched = true; best.door.rotation.y = -1.9; work.run++;
        let stacks = 2 + Math.floor(scav / 3); best.loot.sort((a, b) => LOOT[b][0] - LOOT[a][0]);
        while (stacks-- > 0 && best.loot.length) { const k = best.loot.shift(); pack[k] += LOOT[k][1]; }
        practise('scavenging', 4);
      }, 'searching ' + best.name + ' outside', best.g.position);
    }
    case 'medic': {
      const aid = skills.level('aid');
      const you = player.present && player.hp < 15 && clock - (work.treated.you ?? -1e9) > 30;
      const self = !you && state.hp < 18 && clock - (work.treated.me ?? -1e9) > 30;
      if (!you && !self) { if (!pack.bandages && stores.bandages) return fetchTask('bandages', 3); return haul(true); }
      return newTask(you ? player.obj.position : her.position.clone(), ticksToS(Math.max(30, 100 - aid * 6)), 'kneel', you ? 1.4 : 0.5, () => {
        const bandage = pack.bandages > 0; if (bandage) pack.bandages--;
        const heal = (bandage ? 6 : 2) + aid * 0.6;
        if (you) { if (player.obj.position.distanceTo(her.position) > 3) return; player.hp = Math.min(20, player.hp + heal); work.treated.you = clock; mind.say('WORK_DONE', pick('Hold still.', "This'll sting.", 'There. Try not to do that again.')); }
        else { state.hp = Math.min(30, state.hp + heal); work.treated.me = clock; }
        practise('aid', bandage ? 6 : 3);
      }, you ? 'patching you up' : 'patching herself up', you ? player.obj.position : null);
    }
    case 'haul': return haul(false);
  }
  return null;
}
// walking between the room and outside goes through the doorway
function via(from, to) {
  const inside = p => p.z > -D / 2;
  if (inside(from) === inside(to)) return to;
  const near = new T.Vector3(0, 0, inside(from) ? -D / 2 + 0.7 : -D / 2 - 0.9), far = new T.Vector3(0, 0, inside(from) ? -D / 2 - 0.9 : -D / 2 + 0.7);
  return Math.abs(from.x) < 0.3 && Math.abs(from.z - near.z) < 0.35 || Math.abs(from.x) < 0.45 && Math.abs(from.z + D / 2) < 0.7 ? far : near;
}
function doWork(dt) {
  if (!work.task) { work.task = plan(); if (!work.task) { work.label = 'nothing to do right now'; return {}; } }
  const tk = work.task; work.label = tk.label;
  if (Math.hypot(her.position.x - tk.at.x, her.position.z - tk.at.z) > tk.reach) {
    tk.t = 0; tk.travel += dt;
    if (tk.travel > 40) { work.task = null; return {}; }
    return { moveTo: via(her.position, tk.at), speed: 1.3 * mind.pace() };
  }
  work.action = tk.action; tk.t += dt;
  if (tk.at === CHEST_AT) chest.openT = 0.6;
  if (tk.t >= tk.secs) { work.task = null; tk.done?.(); }
  return { face: tk.face || tk.at };
}
function evening(dt) {
  const m = mind, hungry = m.need.hunger > 30, thirsty = m.need.thirst > 30;
  if (hungry || thirsty) {
    const kinds = (hungry ? ['cooked', 'produce', 'tins', 'raw'] : []).concat(thirsty ? ['water'] : []);
    let k = kinds.find(k => pack[k] > 0);
    if (!k) {
      const sk = kinds.find(k => stores[k] > 0);
      if (sk) {
        if (her.position.distanceTo(CHEST_AT) > 0.5) { work.label = 'getting something to eat'; return { moveTo: via(her.position, CHEST_AT), speed: 1.3 * m.pace() }; }
        stores[sk]--; pack[sk]++; chest.openT = 0.6; k = sk;
      }
    }
    if (k && (work.eatCd -= dt) <= 0) { work.eatCd = 1; pack[k]--; m.ate(FOOD_OF[k]); m.say('EAT'); work.label = 'eating'; return {}; }
  }
  const spot = player.present ? player.obj.position : HOME;
  if (her.position.distanceTo(spot) > 3.5) { work.label = player.present ? 'coming to sit with you' : 'heading home'; return { moveTo: via(her.position, spot), speed: 1.3 * m.pace() }; }
  work.action = 'sit'; work.label = player.present ? 'sitting with you' : 'sitting at home';
  return { face: player.present ? player.obj.position : null };
}
function sleepy() {
  if (mind.need.fear > 40 || nearestZombie(12) || state.following) return false;
  return phase() === 'sleep' || mind.need.tired > 90;
}
function startSleep() {
  work.sleeping = true; work.slept = 0; work.nightmareAt = -1; work.task = null;
  // a bad day: a strong, bad memory from the last day brings a nightmare half the time
  for (const d of mind.diary) if (d.feel < -0.5 && d.strength > 0.6 && worldTicks - d.tick < 24000 && rnd() < 0.5) { work.nightmareAt = (60 + rnd() * 180) / rateOf(); break; }
}
function wakeUp() { if (!work.asleep) return; work.asleep = false; her.position.copy(BED_FOOT); state.yaw = state.yawTarget = Math.PI; }
function stopSleep() {
  if (work.asleep) { wakeUp(); if (mind.need.tired < 20) mind.say('WAKE', pick('Morning. I think.', "Okay. I'm up.", 'Still raining, then.')); }
  work.sleeping = false;
}
// awake at home she watches the doorway; the first zombie in, she shouts which side and wakes whoever's asleep
function watch() {
  if (state.following || state.dead || clock - work.lastAlarm < 45) return;
  const seen = zombies.find(z => { if (!z.alive) return false; const d = z.obj.position.distanceTo(her.position); return z.obj.position.distanceTo(HOME) < 28 && (work.asleep ? d < 6 : d < 18); });
  if (!seen) return;
  work.lastAlarm = clock;
  if (work.asleep) { wakeUp(); mind.jolt(30); }
  const dx = seen.obj.position.x - HOME.x, dz = seen.obj.position.z - HOME.z, side = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 'east' : 'west') : (dz > 0 ? 'south' : 'north');
  mind.say('ALARM', pick(`Zombies, ${side} side!`, `Movement to the ${side}!`, `They're at the ${side}! Get up!`, `Heads up, ${side} side!`));
  mind.remember(`Raised the alarm, zombies on the ${side} side`, -0.2, 0.3);
}

// ---------- how she feels about you (a copy of NpcBond) ----------
const ACTS = [['compliment', 'Compliment her'], ['flirt', 'Flirt'], ['gift', 'Give flowers'], ['hug', 'Hug her'], ['hold', 'Hold her hand'], ['kiss', 'Kiss her']];
Object.assign(LINES, {
  FLIRT: [150, "You keep looking at me like that, we'll never get anything done.", 'Admit it, you like having me around.', 'If we survive this, you owe me a proper date.', "You're cute when you're concentrating.", 'Is it weird that this is the best time I\'ve had in months?'],
  ROMANCE: [200, "I'm glad it's you I'm stuck with.", "Whatever happens, I'm with you.", 'Stay close tonight?', 'You make all this feel almost normal.', 'I used to think nothing good could happen anymore.'],
  JEALOUS: [120, "Who's your new friend?", "Don't mind me. I'll just be over here.", 'She seems... nice.', 'You two look cosy.'],
  SAVED: [60, 'Thanks. That one was close.', "My hero. Don't let it go to your head.", 'I owe you one.'],
  REPLY: [0],
});
REAL_TIME.add('REPLY');
const bond = {
  trust: 35, affection: 25, attraction: 15, together: false, holding: false, pose: '', poseT: 0, last: {}, lastPlayful: -1e9, lastTender: -1e9, jealousUntil: -1e9, pushes: 0, jealousCount: 0, closeCall: false,
  roll() { this.trust = clamp(35 + 12 * mind.trait.loyal, 10, 60); this.affection = clamp(25 + 10 * mind.trait.kind, 10, 45); this.attraction = clamp(15 + gauss() * 12, 0, 45);
    this.together = this.holding = false; this.pose = ''; this.poseT = 0; this.last = {}; this.lastPlayful = this.lastTender = this.jealousUntil = -1e9; this.pushes = 0; },
  add(t, a, r) { this.trust = clamp(this.trust + t, 0, 100); this.affection = clamp(this.affection + a, 0, 100); this.attraction = clamp(this.attraction + r, 0, 100); },
  calm() { return !work.asleep && !state.dead && mind.need.fear < 35 && mind.need.pain < 50 && mind.need.morale >= 40; },
  near(d) { return player.present && player.obj.position.distanceTo(her.position) < d; },
  flirty() { return this.calm() && this.near(6) && this.affection >= 35 && (this.attraction >= 55 || this.attraction >= 30 && clock - this.lastPlayful < 120); },
  romantic() { return this.together && this.calm() && this.near(5) && (this.holding || clock - this.lastTender < 180 || work.action === 'sit'); },
  jealous() { return this.together && clock < this.jealousUntil; },
};
function bondWords() {
  const { trust: t, affection: a, attraction: r, together } = bond;
  const tw = t < 20 ? "doesn't trust you" : t < 45 ? 'is wary of you' : t < 75 ? 'trusts you' : 'would trust you with her life';
  const aw = a < 20 ? 'dislikes you' : a < 40 ? 'is warming to you' : a < 60 ? 'likes you' : a < 80 ? 'is fond of you' : 'cares about you deeply';
  const rw = together ? 'is with you' : r >= 70 && a >= 60 ? 'is falling for you' : r >= 55 ? 'has a crush on you' : r >= 35 ? "thinks you're cute" : null;
  return 'She ' + tw + ', ' + aw + (rw ? ', and ' + rw : '') + '.';
}
function moodWithYou() {
  const base = mind.mood();
  if (!['steady', 'in good spirits', 'lonely', 'low'].includes(base)) return base;
  return bond.jealous() ? 'jealous' : bond.romantic() ? 'romantic' : bond.flirty() ? 'flirty' : base;
}
const reply = line => mind.say('REPLY', line);
function holdPose(kind, secs) { bond.pose = kind; bond.poseT = secs; }
// a second companion who can hang around you (for jealousy)
const rival = new T.Group();
{ const mat = new T.MeshStandardMaterial({ color: 0x8a6d7a, roughness: 0.7 }), skinMat = new T.MeshStandardMaterial({ color: 0xc49a80, roughness: 0.7 });
  const torso = new T.Mesh(new T.CapsuleGeometry(0.18, 0.55, 6, 14), mat); torso.position.y = 1.1; torso.castShadow = true; rival.add(torso);
  const head = new T.Mesh(new T.SphereGeometry(0.11, 18, 14), skinMat); head.position.y = 1.6; rival.add(head);
  for (const sx of [-0.09, 0.09]) { const leg = new T.Mesh(new T.CapsuleGeometry(0.07, 0.55, 4, 10), new T.MeshStandardMaterial({ color: 0x2b2f36 })); leg.position.set(sx, 0.4, 0); rival.add(leg); }
  rival.visible = false; scene.add(rival); }
let rivalT = 0;
function bondTick() {
  const m = mind;
  if (m.need.fear > 60) bond.closeCall = true;
  if (!player.present) { bond.holding = false; return; }
  const d = player.obj.position.distanceTo(her.position);
  if (d < 6 && !work.asleep) bond.add(0.004 * rateOf(), 0.008 * (1 + 0.3 * m.trait.kind) * rateOf(), 0);
  if (m.need.lonely > 80) bond.add(-0.004 * rateOf(), -0.01 * rateOf(), 0);
  if (bond.holding && (d > 4 || !state.following || nearestZombie(12))) { bond.holding = false; if (nearestZombie(12)) reply('I need both hands for this.'); }
  if (bond.together && bond.affection < 35) { bond.together = bond.holding = false; m.remember("It's over with you", -0.7, 0.9); m.add('morale', -10); m.say('ROMANCE', "I can't do this anymore. We're done."); }
  const rivalClose = bond.together && rival.visible && d < 16 && rival.position.distanceTo(player.obj.position) < 3;
  bond.jealousCount = rivalClose ? bond.jealousCount + 1 : Math.max(0, bond.jealousCount - 1);
  if (bond.jealousCount >= 20) { bond.jealousCount = 0; bond.jealousUntil = clock + 60; bond.add(0, -1, 0); m.say('JEALOUS'); }
  if (bond.closeCall && m.need.fear < 25 && d < 5 && bond.trust >= 50 && (bond.together || bond.affection >= 60) && !work.asleep) {
    bond.closeCall = false; holdPose('hug', 3); bond.lastTender = clock; m.add('fear', -10);
    m.say('ROMANCE', pick("C'mere. I need a minute.", "Don't let go yet.", 'That was too close. Just... hold on to me.'));
  } else if (m.need.fear < 25 && d >= 5) bond.closeCall = false;
  const chatty = 1 + 0.6 * m.trait.talkative;
  if (bond.romantic() && rnd() < 0.03 * chatty * Math.min(rateOf(), 10)) m.say('ROMANCE');
  else if (bond.flirty() && rnd() < 0.04 * chatty * Math.min(rateOf(), 10)) m.say('FLIRT');
}
function act(kind) {
  const m = mind;
  if (state.dead) return;
  if (work.asleep) { reply('Zzz...'); return; }
  if (!player.present) return;
  const since = clock - (bond.last[kind] ?? -1e9); bond.last[kind] = clock; const again = since < 45;
  if (m.need.fear > 60 && kind !== 'hug') { reply(pick('Not now!', 'Are you serious? Now?', 'Later. If there is a later.')); return; }
  if (kind === 'compliment') {
    if (again) { reply('You said that already.'); return; }
    bond.add(0.5, 2 * (1 - bond.affection / 150), bond.attraction > 25 ? 1 : 0.5); m.add('morale', 1.5); bond.pushes = 0;
    reply(bond.attraction < 30 ? pick('Thanks. I guess.', 'Uh... thanks.', "Flattery won't get you extra rations.") : bond.attraction < 60 ? pick("That's sweet.", "You're not so bad yourself.", 'Okay, that was nice.')
      : pick("Stop it. You're making me blush.", "Keep that up and I'll start believing you.", "You always know what to say, don't you?"));
  } else if (kind === 'flirt') {
    if (again) { bond.pushes++; bond.add(0, 0, -0.5); reply('Easy, tiger.'); }
    else if (bond.attraction >= 30 && bond.affection >= 30) { bond.add(0, 1, 3); bond.lastPlayful = clock; bond.pushes = 0;
      reply(bond.attraction < 60 ? pick("Is that your best line? It's the end of the world, try harder.", 'Smooth. Real smooth.', 'Careful. I might start to like you.')
        : pick("Keep talking like that and I'll forget there are zombies out there.", "You're trouble, you know that?", 'Only if you buy me dinner. A can of peaches will do.')); }
    else if (bond.affection >= 30) reply(pick('Nice try.', 'Ha. No.'));
    else { bond.pushes++; bond.add(0, -1, 0); reply('Not happening.'); }
  } else if (kind === 'gift') {
    const fresh = since > 300; bond.add(0.5, fresh ? 4 : 1, fresh ? 2 : 0.5); bond.lastPlayful = clock; bond.pushes = 0;
    reply(pick('Flowers? Out here? ...Thank you.', 'Where did you even find these?', "Okay, that's actually really sweet."));
  } else if (kind === 'hug') {
    if (bond.together || bond.affection >= 45 || m.need.fear > 40 && bond.trust >= 40) {
      holdPose('hug', 3); bond.lastTender = clock; bond.pushes = 0; bond.add(0.5, again ? 0.3 : 1.5, bond.together ? 0.5 : 0);
      m.add('fear', -20); m.add('morale', 3); m.add('lonely', -15); reply(pick("C'mere.", '...Okay. Just for a second.', 'Thanks. I needed that.'));
    } else { bond.pushes++; bond.add(0, -0.5, 0); reply('Whoa. Personal space.'); }
  } else if (kind === 'hold') {
    if (bond.holding) { bond.holding = false; reply('Okay.'); }
    else if (bond.together || bond.affection >= 60 && bond.attraction >= 45) {
      bond.holding = true; bond.lastTender = clock; bond.pushes = 0; if (!state.following) setFollow(true, true);
      reply(pick("Don't let go.", 'Your hands are freezing.', 'Okay. Lead the way.'));
    } else reply("Let's not.");
  } else if (kind === 'kiss') {
    if (bond.together) { holdPose('kiss', 2); bond.lastTender = clock; bond.pushes = 0; bond.add(0.3, again ? 0.3 : 2, again ? 0 : 1); m.add('morale', again ? 1 : 5); m.add('lonely', -20);
      reply(pick('Mm. Again later.', '...Wow.', "Don't get used to it. Okay, get used to it.")); }
    else if (bond.affection >= 70 && bond.attraction >= 65 && bond.trust >= 50) { bond.together = true; holdPose('kiss', 2); bond.lastTender = clock; bond.pushes = 0; bond.add(2, 5, 3); m.add('morale', 10);
      m.remember('First kiss with you', 0.9, 1); reply(pick('...About time.', "I was wondering when you'd do that.", 'So. That happened.')); }
    else if (bond.attraction >= 45 && bond.affection >= 45) reply(pick('Not yet. Ask me again sometime.', 'Slow down. I like you, but slow down.'));
    else { bond.pushes++; bond.add(0, -2, -1); reply(pick('Whoa. No.', "I don't see you like that.")); }
  }
  if (bond.pushes >= 3) { bond.pushes = 0; bond.add(-3, -2, -2); m.remember("You won't take no for an answer", -0.4, 0.5); mind.lastAny = -99; reply('I said no. Drop it.'); }
}

// ---------- what she wears (a copy of NpcWardrobe) ----------
// her pieces: practical, pretty, cosy; her bra counts as a top, and it, her underwear and hair always stay on
const PIECE = { sweater: ['sweater', 0.2, 0, 0.5], dress: ['slip dress', -0.2, 0.6, 0], skirt: ['skirt', 0.1, 0.3, 0.1], shorts: ['denim shorts', 0.3, 0.1, 0],
  stockings: ['stockings', 0, 0.15, 0.2], boots: ['boots', 0.5, 0, 0.1], heels: ['heels', -0.5, 0.4, 0] };
const PIECES_ALL = Object.keys(PIECE);
const clashes = (a, b) => a !== b && (a === 'dress' && ['sweater', 'skirt', 'shorts'].includes(b) || b === 'dress' && ['sweater', 'skirt', 'shorts'].includes(a)
  || a === 'skirt' && b === 'shorts' || a === 'shorts' && b === 'skirt' || a === 'boots' && b === 'heels' || a === 'heels' && b === 'boots');
const OUTFITS = [['work', 'work clothes', ['sweater', 'shorts', 'stockings', 'boots']], ['skirt', 'sweater and skirt', ['sweater', 'skirt', 'stockings', 'boots']],
  ['dress', 'the slip dress', ['dress', 'heels']], ['dress_boots', 'the slip dress and boots', ['dress', 'stockings', 'boots']],
  ['date', 'skirt and heels', ['sweater', 'skirt', 'stockings', 'heels']]].map(([k, label, p]) => ({ k, label, pieces: new Set(p) }));
const SLEEP = new Set(['dress']);
const ALL_PIECES = ['stockings', 'heels', 'sweater', 'shorts', 'skirt', 'dress', 'boots'];
const same = (a, b) => a.size === b.size && [...a].every(x => b.has(x));
const covered = s => (s.has('sweater') || s.has('dress')) && (s.has('dress') || s.has('skirt') || s.has('shorts'));
LINES.OUTFIT = [45];
REAL_TIME.add('OUTFIT');
const wardrobe = {
  worn: new Set(OUTFITS[0].pieces), why: "it's what she had on", taste: {}, his: {}, kept: null, keepUntil: -1, bootsUntil: -1, askedUntil: -1, lastChange: -1e9, lastAsk: -1e9,
  next: null, changeT: 0, heelRun: 0, checkIn: 0,
  roll() { for (const p of PIECES_ALL) { this.taste[p] = clamp(gauss() * 0.5, -1, 1); this.his[p] = 0; } this.kept = null; this.keepUntil = this.bootsUntil = this.askedUntil = -1; },
  asking() { return clock < this.askedUntil; },
};
wardrobe.roll();
const danger = () => mind.need.fear > 40 || !!nearestZombie(16);
const workingNow = () => !state.following && work.job !== 'none' && phase() === 'work';
const practical = s => clamp([...s].reduce((v, p) => v + PIECE[p][1], 0), 0, 1);
const avg = (by, s) => s.size ? [...s].reduce((v, p) => v + by[p], 0) / s.size : 0;
function setScore(s, reason) {
  const w = workingNow(), practicalW = w ? 2.5 : state.following ? 1.5 : 0.3;
  const prettyW = bond.romantic() ? 1.2 : bond.flirty() ? 0.6 : 0, cosyW = mind.need.morale < 40 || mind.need.lonely > 60 ? 0.8 : 0;
  let pretty = 0, cosy = 0; for (const p of s) { pretty += PIECE[p][2]; cosy += PIECE[p][3]; }
  const heels = s.has('heels');
  const parts = [avg(wardrobe.taste, s) * 1.6, avg(wardrobe.his, s) * (0.6 + bond.affection / 80), practical(s) * practicalW, Math.min(1, pretty) * prettyW, Math.min(1, cosy) * cosyW,
    wardrobe.kept && same(s, wardrobe.kept) && clock < wardrobe.keepUntil && (!w || practical(s) >= 0.4) ? 2.5 : 0];
  const words = ['she likes it', 'you like it on her', w ? "she's working" : "she's out with you", "she's feeling romantic", 'she wants something cosy', 'you picked it'];
  let v = (same(s, wardrobe.worn) ? 0.5 : 0) - (heels && clock < wardrobe.bootsUntil ? 3 : 0), top = 0;
  parts.forEach((p, i) => { v += p; if (p > parts[top]) top = i; });
  if (reason) reason.why = heels && clock < wardrobe.bootsUntil ? 'her feet still hurt' : words[top];
  return v;
}
function putOn(s, why) { wardrobe.worn = new Set(s); wardrobe.why = why; hidden.clear(); for (const p of ALL_PIECES) if (!wardrobe.worn.has(p)) hidden.add(p); applyOutfit(); drawWardrobe(); }
function changeInto(s, why) { wardrobe.next = new Set(s); wardrobe.why = why; wardrobe.changeT = 2; wardrobe.lastChange = clock; }
function changeLine(was, now) {
  let pretty = 0; for (const p of now) pretty += PIECE[p][2];
  const line = workingNow() && practical(now) >= 0.6 ? pick("Boots on. Let's get to work.", 'Work clothes. Joy.', 'Right. Dressed for it.')
    : same(was, SLEEP) ? pick('Okay. Dressed. Human again.', 'Morning outfit, done.')
    : pretty >= 0.7 ? pick("Felt like dressing up. Don't laugh.", "End of the world's no excuse to look bad.", "Might as well wear it while there's someone to see it.")
    : !now.has('heels') && was.has('heels') ? pick('Bye, heels. Hello, feet.', 'Much better.')
    : pick("That's better.", 'Changed. Feels good to be out of those.', "Okay, that'll do.");
  mind.say('OUTFIT', line);
}
function maybeAsk(s) {
  if (!player.present || player.obj.position.distanceTo(her.position) > 8 || bond.affection < 30 || same(s, OUTFITS[0].pieces) || clock - wardrobe.lastAsk < 600) return;
  if (rnd() > 0.45 + 0.25 * mind.trait.talkative + (bond.together ? 0.2 : 0)) return;
  wardrobe.lastAsk = clock; wardrobe.askedUntil = clock + 90; mind.lastAny = -99;
  reply(pick('So? What do you think?', 'Be honest. Does this work?', 'Too much for the apocalypse?', 'Well? Say something.'));
}
function wardrobeTick() {
  wardrobe.heelRun = wardrobe.worn.has('heels') && state.speed > 2.4 && !work.asleep ? wardrobe.heelRun + 1 : 0;
  if (wardrobe.heelRun === 3) { wardrobe.bootsUntil = clock + 1200; wardrobe.taste.heels = clamp(wardrobe.taste.heels - 0.05, -1, 1);
    mind.say('OUTFIT', pick('Running in heels. Brilliant plan, me.', 'These heels were a mistake.', "Next time I'm wearing boots. Remind me.")); }
  if (wardrobe.changeT > 0) return;
  if (work.asleep) { if (!same(wardrobe.worn, SLEEP)) putOn(SLEEP, "she's in bed"); return; }
  const waking = same(wardrobe.worn, SLEEP);
  if (++wardrobe.checkIn >= 30 || waking) {
    wardrobe.checkIn = 0;
    if (danger() || !waking && clock - wardrobe.lastChange < 120) return;
    const r = {}; let best = wardrobe.worn, bestS = waking ? -1e9 : setScore(wardrobe.worn, r), bestWhy = waking ? '' : r.why;
    for (const o of OUTFITS) { const q = {}, s = setScore(o.pieces, q); if (s > bestS) { bestS = s; best = o.pieces; bestWhy = q.why; } }
    if (!same(best, wardrobe.worn)) { const was = wardrobe.worn; changeInto(best, bestWhy); changeLine(was, best); maybeAsk(best); }
  }
}
function willing(liking) {
  return rnd() < 0.4 + bond.affection / 250 + bond.trust / 400 + liking * 0.4 + 0.1 * mind.trait.loyal + (bond.together ? 0.15 : 0);
}
function keepSet(s, why) { wardrobe.kept = new Set(s); wardrobe.keepUntil = clock + 600; changeInto(s, why); }
// your side: like / dislike / suggest an outfit / put a piece on / take a piece off
function aboutOutfit(kind, k) {
  if (work.asleep) { reply('Zzz...'); return; }
  if (danger()) { reply(pick('Not now!', 'Seriously? Now?')); return; }
  const asked = wardrobe.asking(); wardrobe.askedUntil = -1;
  const worn = wardrobe.worn, t = avg(wardrobe.taste, worn), wk = workingNow();
  if (kind === 'like') {
    for (const p of worn) wardrobe.his[p] = clamp(wardrobe.his[p] + 0.25, -1, 1); bond.add(asked ? 0.5 : 0.2, asked ? 2 : 1, asked ? 1 : 0.5); mind.add('morale', asked ? 4 : 2);
    reply(bond.attraction >= 55 ? pick("I was hoping you'd say that.", 'Yeah? Keep looking, then.', 'Good. I wore it for you. A bit.') : pick('Yeah? Thanks.', "Good. I wasn't sure.", 'Okay. Keeping it.'));
  } else if (kind === 'dislike') {
    for (const p of worn) wardrobe.his[p] = clamp(wardrobe.his[p] - 0.25, -1, 1); if (!asked) bond.add(0, -0.5, 0);
    if (t > 0.4 && (mind.trait.loyal < 0 || bond.trust < 40)) reply(pick('Well, I like it.', "Good thing I'm not dressing for you, then.", 'Noted. Ignored, but noted.'));
    else { wardrobe.keepUntil = -1; wardrobe.lastChange = -1e9; reply(pick("Fine. I'll change later.", 'Ouch. Okay.', 'Really? ...Okay, noted.')); }
  } else if (kind === 'suggest') {
    const o = OUTFITS.find(x => x.k === k); if (same(o.pieces, worn)) { reply("I'm already wearing it."); return; }
    if (wk && practical(o.pieces) < 0.4) { reply(pick('Not for work. Later, maybe.', "In that? I've got planks to haul.")); return; }
    const like = avg(wardrobe.taste, o.pieces);
    if (willing(like)) { for (const p of o.pieces) wardrobe.his[p] = clamp(wardrobe.his[p] + 0.15, -1, 1); keepSet(o.pieces, 'you suggested it');
      reply(like > 0.3 ? pick('Ooh, good call.', 'I was thinking the same.') : pick('Okay, give me a second.', 'Fine. For you.', 'If you insist.')); }
    else reply(like < -0.3 ? pick('That one? No.', "I don't even like that one.") : pick("Nah. I like what I've got on.", 'Not today.', 'Maybe later.'));
  } else if (kind === 'on') {
    if (worn.has(k)) return; const label = PIECE[k][0];
    const s = new Set([...worn].filter(p => !clashes(k, p))); s.add(k);
    if (!s.has('sweater') && !s.has('dress')) s.add('sweater');
    if (!covered(s)) s.add(wardrobe.taste.skirt >= wardrobe.taste.shorts ? 'skirt' : 'shorts');
    if (wk && (k === 'heels' || k === 'dress')) { reply(pick("Not while I'm working.", `In ${label}? I've got work to do.`)); return; }
    const like = wardrobe.taste[k];
    if (!willing(like)) { reply(like < -0.3 ? pick(`I don't like the ${label}.`, `Not the ${label}. No.`) : pick('Nah, not right now.', 'Maybe later.', "I'm good, thanks.")); return; }
    wardrobe.his[k] = clamp(wardrobe.his[k] + 0.15, -1, 1); const more = s.size > worn.size + 1 || ![...worn].every(p => s.has(p)); keepSet(s, 'you asked for the ' + label);
    reply(like > 0.3 ? pick(`Ooh, yes. The ${label}.`, 'Good call.') : more ? pick('Okay. Let me swap a few things.', 'Sure. Give me a second.') : pick('Okay.', 'Sure, why not.', 'Alright.'));
  } else if (kind === 'off') {
    if (!worn.has(k)) return; const label = PIECE[k][0];
    const s = new Set(worn); s.delete(k);
    if (!covered(s) && !(bond.together || bond.trust >= 60 && bond.affection >= 50) || !covered(s) && !willing(-0.5)) { reply(pick("I'm not walking around in my underwear.", 'Nope. That stays on.', 'Not happening.')); return; }
    if (wk && k === 'boots') { reply('I need those for work.'); return; }
    if (!willing(-wardrobe.taste[k])) { reply(pick(`I like the ${label}. They stay.`, "Nah, I'm keeping it.", "No, I'm comfortable.")); return; }
    keepSet(s, 'you asked her to take off the ' + label);
    reply(k === 'heels' ? 'Gladly. My feet thank you.' : pick('Sure.', 'Okay, off it comes.', 'Fine by me.'));
  }
  drawWardrobe();
}
function drawWardrobe() {
  const el = $('wearing'); if (!el) return;
  const words = s => [...s].map(p => PIECE[p][0]).join(', ') || 'her underwear';
  el.textContent = `Wearing ${words(wardrobe.worn)}, and her bra (${wardrobe.changeT > 0 && wardrobe.next ? 'changing into ' + words(wardrobe.next) : wardrobe.why}).` + (wardrobe.asking() ? ' She asked what you think.' : '');
  $('tastes').textContent = 'Her taste: ' + PIECES_ALL.map(p => `${PIECE[p][0]}: ${wardrobe.taste[p] > 0.3 ? 'loves' : wardrobe.taste[p] < -0.3 ? 'dislikes' : 'fine'}`).join(', ') + '.';
  const box = $('pieceBtns'); if (!box) return; box.innerHTML = '';
  for (const p of PIECES_ALL) { const b = document.createElement('button'); const on = wardrobe.worn.has(p); b.textContent = (on ? 'Take off ' : 'Put on ') + PIECE[p][0];
    b.setAttribute('aria-pressed', String(on)); b.onclick = () => aboutOutfit(on ? 'off' : 'on', p); box.append(b); }
}
// ---------- her brain (a copy of NpcBrain) ----------
const state = { hp: 30, dead: false, deadT: 0, following: true, armed: true, lights: true, moving: false, speed: 0, yaw: Math.PI, rounds: 30, reload: 0, aimT: 0, cooldown: 0, goal: 'idle', fleeTo: null, target: null, forage: null, shooting: false };
her.position.set(0, 0, 0.5);
function steer(from, to, maxStep) {
  const d = new T.Vector3().subVectors(to, from); d.y = 0; const len = d.length(); if (len < 1e-4) return from.clone();
  d.divideScalar(len);
  for (const o of obstacles) { const away = new T.Vector3(from.x - o.x, 0, from.z - o.z); const dd = away.length(); if (dd < o.r + 0.6) d.addScaledVector(away.normalize(), (o.r + 0.6 - dd) * 1.6); }
  d.normalize();
  const next = from.clone().addScaledVector(d, Math.min(maxStep, len));
  return keepInside(next);
}
function keepInside(p) {
  const inDoor = Math.abs(p.x) < DOOR / 2 - 0.25;
  p.x = clamp(p.x, -W / 2 + 0.35, W / 2 - 0.35);
  if (!inDoor) { if (p.z < -D / 2 + 0.35 && p.z > -D / 2 - 0.4) p.z = -D / 2 + 0.35; }
  p.z = Math.min(p.z, D / 2 - 0.35);
  if (p.z < -D / 2 - 0.4 && !inDoor && p.z > -D / 2 - 0.6) p.z = -D / 2 - 0.6;
  return p;
}
function nearestZombie(range) {
  let best = null, bd = range;
  for (const z of zombies) if (z.alive) { const d = z.obj.position.distanceTo(her.position); if (d < bd) { bd = d; best = z; } }
  return best;
}
function think(dt) {
  work.action = ''; work.label = '';
  if (state.dead) { state.moving = false; state.speed = 0; return 'die'; }
  const m = mind, pace = m.pace(), asleep = work.asleep;
  let goal = null, moveTo = null, speed = 0, face = null, clip = 'idle';
  // flee (asleep she sees nothing: a zombie close by or her own alarm wakes her first)
  const range = (state.armed && !m.panicking() ? 3 : 9) * m.flightScale();
  const close = asleep ? null : nearestZombie(range);
  if (close) {
    if (state.goal !== 'flee') { m.jolt(12); m.say(m.panicking() ? 'PANIC' : 'FLEE'); }
    const away = new T.Vector3().subVectors(her.position, close.obj.position); away.y = 0; away.normalize();
    moveTo = keepInside(her.position.clone().addScaledVector(away, 5)); speed = 3.4 * pace; goal = 'flee'; work.label = 'getting away';
  } else if (!asleep && state.armed && nearestZombie(22)) {
    const z = nearestZombie(22); goal = 'shoot'; face = z.obj.position; work.label = 'shooting';
    if (state.goal !== 'shoot') { m.say('SPOTTED'); state.aimT = 0; }
    state.aimT += dt;
    if (state.reload > 0) { state.reload -= dt; if (state.reload <= 0) state.rounds = 30; }
    else if (state.aimT > 0.4 && (state.cooldown -= dt) <= 0) {
      anim.fireT = 0; flash.intensity = 6; m.add('adrenaline', 3);
      const aim = skills.level('aim'), d = z.obj.position.distanceTo(her.position), err = Math.abs(gauss()) * 0.02 * m.wobble() * (1.25 - 0.06 * aim);
      practise('aim', 1);
      if (err * d < 0.28) { z.hp -= rnd() < 0.12 + 0.025 * aim ? 3 : 1; z.hitT = 0.15; if (z.hp <= 0) { z.alive = false; z.deadT = 0; m.killed(); practise('aim', 3); } }
      state.cooldown = 0.2 + rnd() * 0.3;
      if (--state.rounds <= 0) { state.reload = 2.5; m.say('RELOAD'); }
    }
    clip = anim.fireT < clipSecs('fire') ? 'fire' : 'aim';
  } else if (state.goal === 'shoot') { if (!zombies.some(z => z.alive)) m.say('CLEAR'); }
  if (goal && work.sleeping) stopSleep();
  // a hug or a kiss: she stays with you, facing you, till it's done
  if (!goal && bond.poseT > 0 && player.present) {
    bond.poseT -= dt; goal = 'pose'; face = player.obj.position; work.label = bond.pose === 'hug' ? 'hugging you' : 'kissing you';
    if (her.position.distanceTo(player.obj.position) > 0.75) { moveTo = player.obj.position.clone(); speed = 1.3; }
  }
  // changing: she kneels a moment, and halfway through the clothes swap
  if (!goal && wardrobe.changeT > 0) {
    const was = wardrobe.changeT; wardrobe.changeT -= dt; goal = 'change'; work.action = 'kneel'; work.label = 'changing';
    if (was > 1 && wardrobe.changeT <= 1 && wardrobe.next) { putOn(wardrobe.next, wardrobe.why); wardrobe.next = null; }
  } else if (goal && wardrobe.changeT > 0 && wardrobe.next) { putOn(wardrobe.next, wardrobe.why); wardrobe.next = null; wardrobe.changeT = 0; }
  // sleep: in her hours at home, or whenever she's dead on her feet and it's quiet
  if (!goal) {
    if (work.sleeping) { const keep = work.asleep ? (phase() === 'sleep' || m.need.tired > 5) && !nearestZombie(6) : sleepy(); if (!keep) stopSleep(); }
    else if (rnd() < dt && sleepy()) startSleep();
    if (work.sleeping) {
      goal = 'sleep';
      if (!work.asleep) {
        if (her.position.distanceTo(BED_FOOT) > 0.45) { moveTo = via(her.position, BED_FOOT); speed = 1.3 * pace; work.label = 'going to bed'; }
        else { work.asleep = true; m.say('SLEEPY', pick("I'm turning in.", 'Wake me if anything happens.', 'Night.', "Don't let me sleep through the end of the world.")); }
      }
      if (work.asleep) {
        work.action = 'sleep'; work.label = 'asleep'; work.slept += dt;
        if (work.nightmareAt > 0 && work.slept >= work.nightmareAt) {
          work.nightmareAt = -1; wakeUp(); m.add('fear', 40); m.add('morale', -4);
          m.say('NIGHTMARE', pick('No! ...Oh. A dream. Just a dream.', 'I was back there. I was back there again.', 'Sorry. Bad dream. Go back to sleep.'));
          m.remember('Woke from a nightmare', -0.4, 0.4);
        }
      }
    }
  }
  // forage
  if (!goal && !work.asleep && (m.need.hunger >= 35 || m.need.thirst >= 35)) {
    let best = null, bd = 12;
    for (const it of items) { const wanted = m.need.hunger >= 35 && it.hunger > 0 || m.need.thirst >= 35 && it.thirst > 0; const d = it.obj.position.distanceTo(her.position); if (wanted && d < bd) { bd = d; best = it; } }
    if (best) { goal = 'forage'; moveTo = best.obj.position.clone(); moveTo.y = 0; speed = 1.4 * pace; work.label = 'going for ' + best.label;
      if (bd < 0.7) { m.ate(best); scene.remove(best.obj); items.splice(items.indexOf(best), 1); m.say('EAT'); goal = 'eat'; speed = 0; moveTo = null; } }
  }
  // her job, in her work hours, when she's staying home
  if (!goal && !state.following && work.job !== 'none' && phase() === 'work') {
    const w = doWork(dt); goal = 'work'; moveTo = w.moveTo || null; speed = w.speed || 0; face = w.face || null;
  } else if (work.task && goal !== 'work') work.task = null;
  // evenings, bedtime till she's tired enough, or a day with no job: eat from the stores, then sit with you
  if (!goal && !state.following && (phase() !== 'work' || work.job === 'none')) {
    const w = evening(dt); goal = 'evening'; moveTo = w.moveTo || null; speed = w.speed || 0; face = w.face || null;
  }
  // follow
  if (!goal && state.following && player.present && bond.holding) {
    // hand in hand she keeps a step to your right, at your pace
    const py = player.obj.rotation.y, side = player.obj.position.clone().add(new T.Vector3(-Math.cos(py) * 0.7, 0, Math.sin(py) * 0.7));
    const d = her.position.distanceTo(side);
    if (d > 0.25) { goal = 'follow'; moveTo = via(her.position, side); speed = d > 2 ? 2.4 : 1.7; }
    work.label = 'holding your hand';
  }
  if (!goal && state.following && player.present) {
    const d = her.position.distanceTo(player.obj.position);
    if (d > 5 || state.goal === 'follow' && d > 2.5) { goal = 'follow'; moveTo = via(her.position, player.obj.position); speed = (d > 10 ? 3.0 : 1.3) * pace; }
    work.label = bond.holding ? 'holding your hand' : 'with you';
  }
  if (!goal) { goal = 'idle'; if (player.present) face = player.obj.position; }
  state.goal = goal;
  if (work.asleep && lie) {
    her.position.copy(lie.pos); state.yaw = state.yawTarget = lie.yaw; her.rotation.y = state.yaw; state.speed = 0; state.moving = false;
    return 'die';
  }
  if (her.position.y !== 0) her.position.y = 0;
  if (moveTo && speed > 0) {
    const next = steer(her.position, moveTo, speed * dt); const moved = next.distanceTo(her.position);
    if (moved > 1e-4) { const dir = new T.Vector3().subVectors(next, her.position); state.yawTarget = Math.atan2(dir.x, dir.z); }
    her.position.copy(next); state.speed = moved / dt; state.moving = moved > 1e-3;
  } else { state.speed = 0; state.moving = false; }
  if (face && !state.moving) { const dir = new T.Vector3().subVectors(face, her.position); state.yawTarget = Math.atan2(dir.x, dir.z); }
  if (state.yawTarget != null) { let dy = state.yawTarget - state.yaw; dy = Math.atan2(Math.sin(dy), Math.cos(dy)); state.yaw += dy * Math.min(1, dt * 8); }
  her.rotation.y = state.yaw;
  if (goal !== 'shoot') clip = state.speed > 2.6 ? 'run' : state.speed > 0.35 ? (state.armed ? 'rifle_walk' : 'walk') : 'idle';
  return clip;
}
mind.killed = function () { this.kills++; this.add('morale', 1.5); if (this.kills === 1) this.remember('Killed my first zombie', 0.3, 0.8); else if (this.kills % 10 === 0) this.remember(this.kills + ' zombies down', 0.3, 0.4); };
mind.ate = function (it) { this.add('hunger', -it.hunger); this.add('thirst', -it.thirst); this.add('morale', 2); if (player.present && her.position.distanceTo(player.obj.position) < 6) { this.add('lonely', -10); bond.add(0.5, 1.5, 0); this.remember('Shared a meal with you (' + it.label + ')', 0.4, 0.35); } };
function hurt(amount) {
  if (state.dead) return;
  state.hp = Math.max(0, state.hp - amount); wakeUp(); mind.say('HURT'); mind.jolt(25);
  if (state.hp <= 0) { state.dead = true; state.deadT = 0; mind.remember('Died here', -1, 1); return; }
  if (amount >= 6 || state.hp < 30 * 0.35) { mind.add('morale', -8); mind.remember(state.hp < 6 ? 'Nearly died' : 'Badly hurt', -0.8, 0.9); }
}
const flash = new T.PointLight(0xffc070, 0, 5, 2); scene.add(flash);

// ---------- UI ----------
const needEls = {}, traitEls = {};
for (const [n, label] of NEEDS) {
  const row = document.createElement('div'); row.className = 'meter need';
  row.innerHTML = `<label for="n_${n}">${label}</label><input type="range" id="n_${n}" min="0" max="100" step="1"><output id="o_${n}"></output>`;
  $('needs').append(row); const inp = row.querySelector('input');
  inp.addEventListener('input', () => { mind.set(n, +inp.value); }); needEls[n] = { inp, out: row.querySelector('output') };
}
for (const [t, lo, hi] of TRAITS) {
  const row = document.createElement('div'); row.className = 'trait';
  row.innerHTML = `<span>${lo}</span><input type="range" id="t_${t}" min="-1" max="1" step="0.05" aria-label="${lo} to ${hi}"><span>${hi}</span>`;
  $('traits').append(row); const inp = row.querySelector('input'); inp.addEventListener('input', () => { mind.trait[t] = +inp.value; }); traitEls[t] = inp;
}
const syncTraits = () => { for (const [t] of TRAITS) traitEls[t].value = mind.trait[t]; };
const hidden = new Set();
$('oLike').onclick = () => aboutOutfit('like'); $('oDislike').onclick = () => aboutOutfit('dislike');
{ const sel = $('oSuggest'); sel.innerHTML = '<option value="">Suggest...</option>' + OUTFITS.map(o => `<option value="${o.k}">${o.label}</option>`).join('');
  sel.onchange = () => { if (sel.value) aboutOutfit('suggest', sel.value); sel.value = ''; }; }
putOn(OUTFITS[0].pieces, "it's what she had on");
Object.assign(window, { putOn, aboutOutfit, wardrobe, camera, controls, hemi, her });
function applyOutfit() { for (const { g, m } of meshes) m.visible = g.piece === 'body' || !hidden.has(g.piece); body.position.y = hidden.has('heels') && hidden.has('boots') ? -HEEL : 0; }
function setFollow(on, quiet) {
  state.following = on; wakeUp(); work.sleeping = false; work.task = null;
  $('bFollow').setAttribute('aria-pressed', String(on)); $('bFollow').textContent = on ? 'Following' : 'Staying home';
  if (quiet) return;
  if (!on && work.job !== 'none') mind.say('WAIT', pick('Back to work, then.', "I'll get on with it.", 'Right. Home it is.')); else mind.say(on ? 'FOLLOW' : 'WAIT');
}
function toggleFollow() { setFollow(!state.following); }
$('bFollow').onclick = toggleFollow;
addEventListener('keydown', e => { if ((e.key === 'g' || e.key === 'G') && document.activeElement?.tagName !== 'INPUT') toggleFollow(); });
$('bGun').onclick = () => { state.armed = !state.armed; $('bGun').setAttribute('aria-pressed', String(state.armed)); if (state.armed) { state.rounds = 30; mind.say('GUN'); } };
$('bLights').onclick = () => { state.lights = !state.lights; $('bLights').setAttribute('aria-pressed', String(state.lights)); bulb.intensity = state.lights ? 22 : 0; lamp.intensity = state.lights ? 8 : 0; bulbMesh.material.color.set(state.lights ? 0xffe2b0 : 0x333333); hemi.intensity = state.lights ? 0.35 : 0.06; };
$('eZombie').onclick = () => spawnZombie();
$('eHorde').onclick = () => { for (let i = 0; i < 5; i++) setTimeout(spawnZombie, i * 500); };
$('eHarv').onclick = () => { harv.on = true; harv.t = 0; harv.obj.visible = true; harv.obj.position.set(0, 0, -16); };
$('eHurt').onclick = () => hurt(6);
$('eHeal').onclick = () => { if (!state.dead) state.hp = Math.min(30, state.hp + 12); };
$('eFood').onclick = () => dropItem('food');
$('eWater').onclick = () => dropItem('water');
$('eLeave').onclick = () => {
  if (player.present) { if (state.following) toggleFollow(); player.leaving = true; player.target.set(0, 0, -15); $('eLeave').textContent = 'Come back'; }
  else { player.present = true; player.obj.visible = true; player.obj.position.set(0, 0, -15); player.target.set(0.8, 0, -2); $('eLeave').textContent = 'Leave her alone'; }
};
$('eRevive').onclick = () => newCompanion();
$('eBash').onclick = () => { barricade.damage = Math.min(4, barricade.damage + 2); drawBarricade(); };
$('eYouHurt').onclick = () => { if (player.present) player.hp = Math.max(2, player.hp - 8); };
$('eRestock').onclick = () => restockOutside();
for (const [j, label] of JOBS) { const o = document.createElement('option'); o.value = j; o.textContent = label; $('job').append(o); }
$('job').onchange = () => { work.job = $('job').value; work.task = null; if (work.job !== 'none' && state.following) setFollow(false); };
$('bShift').onclick = () => { work.night = !work.night; work.task = null; $('bShift').textContent = work.night ? 'Night shift' : 'Day shift'; $('bShift').setAttribute('aria-pressed', String(work.night)); };
$('bFire').onclick = () => { fire.lit = !fire.lit; $('bFire').setAttribute('aria-pressed', String(fire.lit)); $('bFire').textContent = fire.lit ? 'Campfire lit' : 'Campfire out'; };
$('skipWork').onclick = () => skipTo('work'); $('skipFree').onclick = () => skipTo('free'); $('skipSleep').onclick = () => skipTo('sleep');
$('bStock').onclick = () => stockUp(); $('bEmpty').onclick = () => { for (const k in stores) stores[k] = 0; };
const skillEls = {};
for (const [s, label] of SKILLS) {
  const row = document.createElement('div'); row.className = 'meter skill';
  row.innerHTML = `<label for="k_${s}">${label}</label><input type="range" id="k_${s}" min="0" max="10" step="1"><output></output><div class="xp"><i></i></div>`;
  $('skills').append(row); const inp = row.querySelector('input');
  inp.addEventListener('input', () => skills.set(s, +inp.value)); skillEls[s] = { inp, out: row.querySelector('output'), bar: row.querySelector('.xp i') };
}
const bondEls = {};
for (const [k, label] of [['trust', 'Trust'], ['affection', 'Affection'], ['attraction', 'Attraction']]) {
  const row = document.createElement('div'); row.className = 'meter';
  row.innerHTML = `<label for="b_${k}">${label}</label><input type="range" id="b_${k}" min="0" max="100" step="1"><output></output>`;
  $('bond').append(row); const inp = row.querySelector('input'); inp.addEventListener('input', () => { bond[k] = +inp.value; }); bondEls[k] = { inp, out: row.querySelector('output') };
}
for (const [k, label] of ACTS) { const b = document.createElement('button'); b.textContent = label; if (k === 'hold') b.id = 'aHold'; b.onclick = () => act(k); $('acts').append(b); }
$('eRival').onclick = () => { rival.visible = !rival.visible; rivalT = 0; $('eRival').setAttribute('aria-pressed', String(rival.visible)); };
$('bTogether').onclick = () => { bond.together = !bond.together; if (!bond.together) bond.holding = false; };
const storeEls = {};
for (const k in KIND_LABEL) { const d = document.createElement('div'); d.className = 'stat'; d.innerHTML = `<small>${KIND_LABEL[k]}</small><b>0</b>`; $('stores').append(d); storeEls[k] = d.querySelector('b'); }
$('bRoll').onclick = () => { mind.roll(); syncTraits(); };
$('name').addEventListener('input', () => { $('hudName').textContent = name(); });

function drawDiary() {
  const ol = $('diary'); ol.innerHTML = '';
  for (const d of [...mind.diary].reverse()) {
    const li = document.createElement('li'); const ago = clock - d.at;
    const col = d.feel > 0.2 ? 'var(--good)' : d.feel < -0.2 ? 'var(--bad)' : 'var(--warn)';
    li.innerHTML = `<i style="background:${col};opacity:${0.35 + 0.65 * d.strength}"></i><span></span><small>${ago < 60 ? 'just now' : Math.round(ago / 60) + ' min ago'}</small>`;
    li.querySelector('span').textContent = d.what; ol.append(li);
  }
}
function drawPanel() {
  for (const [n] of NEEDS) { const e = needEls[n]; if (document.activeElement !== e.inp) e.inp.value = mind.need[n]; e.out.textContent = Math.round(mind.need[n]); }
  $('hudMood').textContent = state.dead ? 'dead' : moodWithYou(); $('hudTraits').textContent = mind.traitWords();
  $('sPace').textContent = Math.round(mind.pace() * 100) + '%'; $('sWobble').textContent = mind.wobble().toFixed(1) + 'x';
  $('sFlee').textContent = mind.panicking() ? 'Panicking' : ((state.armed ? 3 : 9) * mind.flightScale()).toFixed(1) + ' m';
  for (const k of ['trust', 'affection', 'attraction']) { const e = bondEls[k]; if (document.activeElement !== e.inp) e.inp.value = bond[k]; e.out.textContent = Math.round(bond[k]); }
  $('bondWords').textContent = bondWords() + (bond.together ? ' You\'re together.' : '');
  $('aHold').textContent = bond.holding ? 'Let go of her hand' : 'Hold her hand';
  drawWardrobe();
  $('sHp').textContent = `${Math.round(state.hp)} / 30`; $('sYou').textContent = player.present ? `${Math.round(player.hp)} / 20` : 'away';
  for (const [s] of SKILLS) { const e = skillEls[s]; if (document.activeElement !== e.inp) e.inp.value = skills.level(s); e.out.textContent = skills.level(s); e.bar.style.width = Math.round(skills.progress(s) * 100) + '%'; }
  for (const k in KIND_LABEL) storeEls[k].textContent = stores[k];
  const carried = Object.keys(pack).filter(k => pack[k] > 0).map(k => `${pack[k]} ${KIND_LABEL[k].toLowerCase()}`);
  $('pack').textContent = 'Her pack: ' + (carried.length ? carried.join(', ') : 'empty');
  const ph = phase(), jobName = JOBS.find(j => j[0] === work.job)[1].split(':')[0];
  $('clockTime').textContent = watchTime();
  $('clockPhase').textContent = (ph === 'work' ? 'work hours' : ph === 'free' ? 'free time' : 'bedtime') + ' on the ' + (work.night ? 'night' : 'day') + ' shift';
  const placeholder = work.action && work.action !== 'sleep' ? ' (idle clip for now)' : work.action === 'sleep' ? ' (lying placeholder)' : '';
  $('hudDoing').textContent = state.dead ? '' : (state.following ? 'Following' : work.job === 'none' ? 'Home' : jobName) + ' · ' + (work.label || (ACTION_WORDS[work.action] ?? 'standing about')) + placeholder;
  $('barricadeNote').textContent = `Window barricade: ${4 - barricade.damage} of 4 boards up, ${['planks', 'double planks', 'planks over a metal sheet'][barricade.strength]}.`;
}
function newCompanion() {
  wakeUp(); work.sleeping = false; work.task = null;
  mind.reset(); skills.roll(); bond.roll(); wardrobe.roll(); putOn(OUTFITS[0].pieces, "it's what she had on"); syncTraits(); state.hp = 30; state.dead = false; state.rounds = 30; anim.cur = 'idle'; anim.prev = null;
  for (const z of zombies) scene.remove(z.obj); zombies.length = 0;
  her.position.set(0, 0, 0.5); mind.remember('Met you', 0.3, 0.9); drawDiary(); $('chat').innerHTML = ''; mind.say('FOLLOW');
}

// bubble over her head
const bubble = document.createElement('div'); bubble.className = 'bubble'; bubble.hidden = true; stage.append(bubble);

// clicking the floor walks you there
const ray = new T.Raycaster(), ndc = new T.Vector2(); let downAt = null;
renderer.domElement.addEventListener('pointerdown', e => { downAt = [e.clientX, e.clientY]; });
renderer.domElement.addEventListener('pointerup', e => {
  if (!downAt || Math.hypot(e.clientX - downAt[0], e.clientY - downAt[1]) > 5 || !player.present) return;
  const r = renderer.domElement.getBoundingClientRect(); ndc.set((e.clientX - r.left) / r.width * 2 - 1, -(e.clientY - r.top) / r.height * 2 + 1);
  ray.setFromCamera(ndc, camera); const hit = ray.intersectObject(floor)[0];
  if (hit) { player.target.copy(keepInside(hit.point.clone().setY(0))); marker.position.set(player.target.x, 0.012, player.target.z); marker.visible = true; }
});

function resize() { const w = stage.clientWidth, h = stage.clientHeight; renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); }
new ResizeObserver(resize).observe(stage); resize();

// the home as you find it: one planter ripe, one growing and dry, one empty; the window half beaten in
planters[0].crop = CROPS[0]; planters[0].age = 7; planters[0].wet = true; planters[1].crop = CROPS[1]; planters[1].age = 3;
planters.forEach(drawPlanter); drawBarricade(); restockOutside(); stockUp();
newCompanion();
let lastT = performance.now(), mindAcc = 0, panelAcc = 0;
const handPos = new T.Vector3(), lPos = new T.Vector3(), headPos = new T.Vector3();
const rRest = [restPos(B.rHand), restPos(B.rMid)], lRest = [restPos(B.lHand), restPos(B.lMid)];
// ?sim=N runs N steps of her world per drawn frame (for checking her day on a slow machine)
const SIM_STEPS = clamp(+(new URLSearchParams(location.search).get('sim') || 1), 1, 50);
function frame(nowMs) {
  const dt = Math.min(0.05, (nowMs - lastT) / 1000); lastT = nowMs;
  for (let i = 0; i < SIM_STEPS; i++) step(SIM_STEPS > 1 ? 0.05 : dt);
  // camera follows her loosely
  const was = controls.target.clone(); controls.target.lerp(new T.Vector3(her.position.x, 1.0, her.position.z), Math.min(1, dt * 2));
  camera.position.add(controls.target.clone().sub(was)); controls.update();
  // her line, over her head
  if (mind.talkLeft > 0 || clock - mind.lastAny < 2.5) {
    headPos.setFromMatrixPosition(skin[B.head].clone().multiply(model.bones[B.head].inv.clone().invert())).applyMatrix4(body.matrixWorld).add(new T.Vector3(0, 0.38, 0));
    const p = headPos.project(camera); const r = renderer.domElement;
    if (p.z < 1) { bubble.hidden = false; bubble.textContent = mind.line; bubble.style.left = ((p.x + 1) / 2 * r.clientWidth) + 'px'; bubble.style.top = ((1 - p.y) / 2 * r.clientHeight) + 'px'; } else bubble.hidden = true;
  } else bubble.hidden = true;
  panelAcc += dt; if (panelAcc > 0.2) { panelAcc = 0; drawPanel(); }
  renderer.render(scene, camera);
  requestAnimationFrame(frame);
}
function step(dt) {
  clock += dt;
  // the clock runs at the chosen speed; planters that are watered grow a stage an hour and may dry out
  const before = worldTicks; worldTicks += 20 * rateOf() * dt;
  if (Math.floor(worldTicks / 1200) !== Math.floor(before / 1200)) for (const p of planters) if (p.crop && p.wet && p.age < 7) { p.age++; if (rnd() < 0.35) p.wet = false; drawPlanter(p); }
  drawFire(clock);
  chest.openT -= dt; chest.open += ((chest.openT > 0 ? 1 : 0) - chest.open) * Math.min(1, dt * 6); chest.lid.rotation.x = -1.2 * chest.open;
  mindAcc += dt; while (mindAcc >= 1) { mindAcc -= 1; mindTick(); }
  mind.talkLeft = Math.max(0, mind.talkLeft - dt);
  // you
  if (player.present) {
    const p = player.obj.position, to = new T.Vector3().subVectors(player.target, p); to.y = 0; const d = to.length();
    if (d > 0.05) { const step = Math.min(d, 1.6 * dt); p.addScaledVector(to.normalize(), step); player.obj.rotation.y = Math.atan2(to.x, to.z); } else marker.visible = false;
    if (player.leaving && p.z < -14) { player.present = false; player.leaving = false; player.obj.visible = false; }
  }
  // the other companion keeps a step to your left
  if (rival.visible && player.present) { const py = player.obj.rotation.y; const want = player.obj.position.clone().add(new T.Vector3(Math.cos(py) * 0.9, 0, -Math.sin(py) * 0.9)); rival.position.lerp(want, Math.min(1, dt * 3)); rival.rotation.y = py; }
  // her
  if (state.dead) state.deadT += dt;
  const clip = window.forceClip || think(dt);
  poseHer(dt, clock, clip, state.speed);
  her.updateMatrixWorld(true);
  // her rifle: grip in the right palm, barrel toward the left hand (both hands are on it in the rifle clips)
  rifle.visible = state.armed && !state.dead && !work.asleep;
  if (rifle.visible) {
    const hm = skin[B.rHand], lm = skin[B.lHand];
    handPos.copy(rRest[0]).lerp(rRest[1], 0.45).applyMatrix4(hm); lPos.copy(lRest[0]).lerp(lRest[1], 0.5).applyMatrix4(lm);
    // in the rifle clips both hands are on it, barrel toward the left; otherwise it hangs along her right hand
    const twoHand = ['aim', 'fire', 'rifle_walk'].includes(anim.cur);
    const dir = twoHand ? new T.Vector3().subVectors(lPos, handPos).normalize()
      : rRest[1].clone().sub(rRest[0]).transformDirection(hm).normalize();
    const grip = handPos.clone().addScaledVector(dir, 0.13).applyMatrix4(body.matrixWorld);
    const dirW = dir.clone().transformDirection(body.matrixWorld);
    rifle.position.copy(grip); const up = new T.Vector3(0, 1, 0); const x = new T.Vector3().crossVectors(up, dirW).normalize(); const y = new T.Vector3().crossVectors(dirW, x);
    rifle.quaternion.setFromRotationMatrix(new T.Matrix4().makeBasis(x, y, dirW));
    if (flash.intensity > 0) { flash.position.copy(grip).addScaledVector(dirW, 0.6); flash.intensity = Math.max(0, flash.intensity - dt * 60); }
  }
  // zombies shamble at her, hit when close, fall when shot
  for (const z of zombies) {
    const o = z.obj;
    if (!z.alive) { z.deadT += dt; o.rotation.x = Math.max(-Math.PI / 2, o.rotation.x - dt * 4); o.position.y = Math.max(0.12 - (z.deadT > 6 ? z.deadT - 6 : 0) * 0.3, o.position.y - dt * 0.5); if (z.deadT > 9) { scene.remove(o); z.gone = true; } continue; }
    const tgt = state.dead ? (player.present ? player.obj.position : her.position) : her.position;
    const to = new T.Vector3().subVectors(tgt, o.position); to.y = 0; const d = to.length();
    if (d > 0.75) { const next = steer(o.position, tgt, z.speed * dt); o.position.copy(next); }
    else if (!state.dead && (z.hitT2 = (z.hitT2 || 0) + dt) > 1.3) { z.hitT2 = 0; hurt(2 + Math.floor(rnd() * 3)); }
    o.rotation.y = Math.atan2(to.x, to.z);
    z.phase += dt * z.speed * 5; z.legs[0].rotation.x = Math.sin(z.phase) * 0.45; z.legs[1].rotation.x = -Math.sin(z.phase) * 0.45;
    z.chest.rotation.z = Math.sin(z.phase * 0.5) * 0.12; z.hips.position.y = 0.95 + Math.abs(Math.sin(z.phase)) * 0.03;
    z.arms.forEach((a, i) => a.rotation.z = Math.sin(z.phase + i) * 0.1);
    if (z.hitT > 0) { z.hitT -= dt; z.chest.rotation.x = 0.25 - z.hitT * 3; }
  }
  for (let i = zombies.length - 1; i >= 0; i--) if (zombies[i].gone) zombies.splice(i, 1);
  // the Harvestman: comes to the doorway, watches, backs off
  if (harv.on) {
    harv.t += dt; const o = harv.obj.position;
    if (harv.t < 6) o.z = -16 + harv.t / 6 * 8.6; else if (harv.t > 22) o.z -= dt * 2.5;
    harv.glow.intensity = 1.5 + Math.sin(harv.t * 3) * 0.5;
    harv.legs.forEach((l, i) => l.rotation.x = Math.sin(harv.t * 2.4 + i) * (harv.t < 6 || harv.t > 22 ? 0.25 : 0.04));
    if (harv.t > 30) { harv.on = false; harv.obj.visible = false; }
  }
}
requestAnimationFrame(frame);
