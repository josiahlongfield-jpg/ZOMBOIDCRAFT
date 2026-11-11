// ---------- Photon-style look: an approximation of Josiah's Photon settings, not the shader itself ----------
// ACES tone mapping at lower exposure, saturation 0.8 / contrast 1.1, thick blue-grey Mie haze, rain at half
// opacity, bloom 0.7, a wet road with puddles, a sodium streetlamp and a hanging pearlescent froglight.
const LOOK = { photon: true };
const look = new THREE.Group(); scene.add(look);
const basic = c => new THREE.MeshBasicMaterial({ color: c });
const lam = (c, o = {}) => new THREE.MeshLambertMaterial({ color: c, ...o });
function solid(w, h, d, mat, x, y, z) { const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat); m.position.set(x, y, z); m.castShadow = m.receiveShadow = true; look.add(m); return m; }
// backdrop: a dark shopfront wall with a couple of dim windows and an awning beam
solid(260, 90, 4, lam(0x1a1c1b), 0, 45, -48);
for (const [x, c] of [[-70, 0x2a2416], [-18, 0x141a1f], [48, 0x241c22]]) solid(26, 22, 1, basic(c), x, 30, -45.6);
solid(70, 2.5, 3, lam(0x221f1b), 40, 52, -30); solid(2.5, 2.5, 20, lam(0x221f1b), 40, 52, -39);
// sodium streetlamp
solid(2.2, 50, 2.2, lam(0x202322), -44, 25, 8); solid(14, 1.6, 1.6, lam(0x202322), -38, 49.5, 8);
solid(5, 1.8, 3.4, lam(0x2a2c2a), -32, 48.4, 8); solid(4, 0.6, 2.6, basic(0xffc07a), -32, 47.4, 8);
const sodium = new THREE.PointLight(0xff9a48, 2.2, 150, 2); sodium.position.set(-32, 45, 8); look.add(sodium);
// pearlescent froglight hanging from the awning on a chain
solid(0.5, 10, 0.5, lam(0x3a3a3a), 40, 46, -30); solid(5, 5, 5, basic(0xe9c9ff), 40, 38.5, -30);
const frog = new THREE.PointLight(0xd7a0ff, 2.0, 110, 2); frog.position.set(40, 35, -27); look.add(frog);
const moon = new THREE.DirectionalLight(0xdbe5ff, 0.35); moon.position.set(30, 80, 40); look.add(moon);
// glossy black puddles that catch the lamps
const puddleMat = new THREE.MeshPhongMaterial({ color: 0x0b0e11, specular: 0x8090a0, shininess: 140, transparent: true, opacity: 0.88 });
for (const [x, z, sx, sz] of [[-14, 18, 26, 12], [22, 4, 18, 22], [-34, -12, 20, 10], [10, 34, 14, 8]]) {
  const p = new THREE.Mesh(new THREE.PlaneGeometry(sx, sz), puddleMat); p.rotation.x = -Math.PI / 2; p.position.set(x, 0.06, z); p.receiveShadow = true; look.add(p);
}
// rain: falling streaks recycled from the top
const DROPS = 1400, rainPos = new Float32Array(DROPS * 6);
for (let i = 0; i < DROPS; i++) { const x = (rnd() - 0.5) * 160, y = rnd() * 100, z = (rnd() - 0.5) * 140; rainPos.set([x, y, z, x + 0.25, y + 2.6, z], i * 6); }
const rainGeo = new THREE.BufferGeometry(); rainGeo.setAttribute('position', new THREE.BufferAttribute(rainPos, 3));
const rain = new THREE.LineSegments(rainGeo, new THREE.LineBasicMaterial({ color: 0xa6b2bd, transparent: true, opacity: 0.32 })); look.add(rain);
// bloom: soft additive glow around the lamps and the rain near them (sprites, so it works without post-processing)
function glowTex(rgb) {
  const c = document.createElement('canvas'); c.width = c.height = 128; const x = c.getContext('2d');
  const g = x.createRadialGradient(64, 64, 0, 64, 64, 64); g.addColorStop(0, `rgba(${rgb},.9)`); g.addColorStop(0.25, `rgba(${rgb},.35)`); g.addColorStop(1, `rgba(${rgb},0)`);
  x.fillStyle = g; x.fillRect(0, 0, 128, 128); return new THREE.CanvasTexture(c);
}
for (const [rgb, x, y, z, s] of [['255,170,90', -32, 46.5, 8, 30], ['255,150,70', -32, 46, 8, 70], ['225,175,255', 40, 38.5, -30, 26], ['215,160,255', 40, 38, -30, 60]]) {
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex(rgb), blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0.55, fog: false }));
  sp.position.set(x, y, z); sp.scale.set(s, s, 1); look.add(sp);
}
const composer = null, bloomSize = '';
let wetFloor = null, dryFloor = null, floorMesh = null;
for (const m of scene.children) if (m.material && m.material.map && m.rotation && m.rotation.x < -1.5) floorMesh = m;
if (floorMesh) { dryFloor = floorMesh.material; wetFloor = new THREE.MeshPhongMaterial({ map: dryFloor.map, specular: 0x2c3238, shininess: 60 }); }
function setLook(on) {
  LOOK.photon = on; look.visible = on;
  renderer.toneMapping = on ? THREE.ACESFilmicToneMapping : THREE.NoToneMapping; renderer.toneMappingExposure = on ? 0.82 : 1;
  scene.fog = on ? new THREE.FogExp2(0x262c32, 0.0072) : new THREE.Fog(0x0c0e0d, 80, 210);
  scene.background = new THREE.Color(on ? 0x20262c : 0x0c0e0d);
  if (floorMesh) floorMesh.material = on ? wetFloor : dryFloor;
  canvas.style.filter = on ? 'saturate(.8) contrast(1.1)' : '';
  scene.traverse(m => { if (m.material) [].concat(m.material).forEach(x => { x.needsUpdate = true; }); });
}
function renderLook(dt) {
  if (LOOK.photon) {
    const a = rainGeo.attributes.position.array, fall = (reduce ? 0 : 95) * Math.min(dt, 0.05);
    for (let i = 0; i < DROPS; i++) { const k = i * 6 + 1; a[k] -= fall; a[k + 3] -= fall; if (a[k] < 0) { a[k] += 100; a[k + 3] += 100; } }
    rainGeo.attributes.position.needsUpdate = true;
  }
  if (LOOK.photon && composer) {
    const sz = canvas.width + 'x' + canvas.height;
    if (sz !== bloomSize) { if (composer.setPixelRatio) composer.setPixelRatio(renderer.getPixelRatio()); composer.setSize(canvas.clientWidth, canvas.clientHeight); bloomSize = sz; }
    composer.render();
  } else renderer.render(scene, camera);
}
setLook(true);
