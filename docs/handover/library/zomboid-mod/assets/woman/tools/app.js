import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';

const MAT_TEX = {body:'body.jpg',blackhair:'blackhair.webp',bra:'bra.webp',dress:'dress.webp',eyelashes:'eyelashes.webp',hair:'hair.webp',
  hair1:'hair1.webp',hair2:'hair2.webp',longhair:'longhair.webp',outfit:'outfit.jpg',pant:'pant.jpg',shirt:'shirt.jpg',sock:'sock.webp',teeth:'teeth.jpg',eyes:'eye_basecolor.jpg'};
const ALPHA = new Set(['blackhair','bra','dress','eyelashes','hair','hair1','hair2','longhair','sock']);

export async function load(el, onProgress){
  const renderer = new THREE.WebGLRenderer({antialias:true, alpha:true, preserveDrawingBuffer:true});
  renderer.setPixelRatio(Math.min(devicePixelRatio,2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
  el.appendChild(renderer.domElement);
  const scene = new THREE.Scene();
  const cam = new THREE.PerspectiveCamera(30, 1, 0.05, 100);
  const controls = new OrbitControls(cam, renderer.domElement);
  controls.enableDamping = true; controls.minDistance = 0.6; controls.maxDistance = 14;
  scene.add(new THREE.HemisphereLight(0xdfe6f0, 0x2a2622, 1.1));
  const key = new THREE.DirectionalLight(0xfff1e0, 2.2); key.position.set(-2.5, 5, 4); scene.add(key);
  const rim = new THREE.DirectionalLight(0x9fb4d8, 1.2); rim.position.set(3, 4, -4); scene.add(rim);
  const floor = new THREE.Mesh(new THREE.CircleGeometry(1.6, 64), new THREE.MeshStandardMaterial({color:0x26282c, roughness:1}));
  floor.rotation.x = -Math.PI/2; scene.add(floor);

  const txt = await (await fetch('model.txt')).text(); onProgress && onProgress(0.6);
  const bin = Uint8Array.from(atob(txt), c => c.charCodeAt(0));
  const hl = new DataView(bin.buffer).getUint32(0, true);
  const head = JSON.parse(new TextDecoder().decode(bin.subarray(4, 4 + hl)));
  const data = bin.slice(4 + hl).buffer;  // copied so every block starts 4-aligned
  const view = (T, off, n) => new T(data, off, n);

  const bones = head.bones.map(b => { const o = new THREE.Bone(); o.name = b.n; o.position.fromArray(b.t); o.quaternion.fromArray(b.q); o.scale.fromArray(b.s); return o; });
  // outer: our framing (scale, centre); rootGroup: the FBX root's own turn and scale, which the binds were made with
  const spinner = new THREE.Group(); scene.add(spinner);   // turntable, about the floor centre
  const outer = new THREE.Group(); spinner.add(outer);
  const rootGroup = new THREE.Group(); outer.add(rootGroup);
  new THREE.Matrix4().fromArray(head.root).decompose(rootGroup.position, rootGroup.quaternion, rootGroup.scale);
  head.bones.forEach((b, i) => (b.p < 0 ? rootGroup : bones[b.p]).add(bones[i]));
  rootGroup.updateMatrixWorld(true);
  const rest = bones.map(b => b.quaternion.clone());
  const restPos = bones.map(b => b.position.clone());

  const loader = new THREE.TextureLoader(); const texCache = {};
  const tex = f => texCache[f] || (texCache[f] = loader.load('tex/' + f, t => { t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = renderer.capabilities.getMaxAnisotropy(); }));
  const mats = {};
  const mat = name => mats[name] || (mats[name] = new THREE.MeshStandardMaterial({ name, map: tex(MAT_TEX[name]), roughness: name === 'eyes' ? 0.15 : 0.7, metalness: 0,
    side: ALPHA.has(name) ? THREE.DoubleSide : THREE.FrontSide, transparent: false, alphaTest: ALPHA.has(name) ? 0.45 : 0, alphaToCoverage: ALPHA.has(name) }));

  const meshes = {};
  for (const m of head.meshes) {
    const g = new THREE.BufferGeometry();
    const pq = view(Uint16Array, m.pos, m.n * 3), pos = new Float32Array(m.n * 3);
    for (let i = 0; i < m.n * 3; i++) { const k = i % 3; pos[i] = m.mn[k] + pq[i] / 65535 * m.sz[k]; }
    const uq = view(Uint16Array, m.uv, m.n * 2), uv = new Float32Array(m.n * 2);
    for (let i = 0; i < m.n * 2; i++) { const k = i % 2; uv[i] = m.umn[k] + uq[i] / 65535 * m.usz[k]; }
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
    g.setAttribute('skinIndex', new THREE.BufferAttribute(new Uint16Array(view(Uint8Array, m.si, m.n * 4)), 4));
    // byte weights rarely sum to exactly 1, which pulls each vertex a different way; renormalise as floats
    const wq = view(Uint8Array, m.sw, m.n * 4), w = new Float32Array(m.n * 4);
    for (let i = 0; i < m.n * 4; i += 4) { const t = wq[i] + wq[i+1] + wq[i+2] + wq[i+3] || 1; for (let k = 0; k < 4; k++) w[i+k] = wq[i+k] / t; }
    g.setAttribute('skinWeight', new THREE.BufferAttribute(w, 4));
    g.setIndex(new THREE.BufferAttribute(m.ix32 ? view(Uint32Array, m.ix, m.tris * 3) : view(Uint16Array, m.ix, m.tris * 3), 1));
    g.computeVertexNormals();
    const inv = view(Float32Array, m.inv, bones.length * 16);
    const sk = new THREE.Skeleton(bones, bones.map((_, j) => new THREE.Matrix4().fromArray(inv, j * 16)));
    const mesh = new THREE.SkinnedMesh(g, mat(m.mat));
    mesh.bind(sk, new THREE.Matrix4().fromArray(m.bind));
    mesh.frustumCulled = false;
    rootGroup.add(mesh); meshes[m.name] = mesh;
  }
  // the FBX's default bone pose is not the pose she was skinned in, so rebuild the rest pose from the inverse binds
  rootGroup.updateMatrixWorld(true);
  const bound = new Array(bones.length), I = new THREE.Matrix4();
  for (const mesh of Object.values(meshes)) mesh.skeleton.boneInverses.forEach((bi, j) => {
    if (!bound[j] && !bi.equals(I)) bound[j] = mesh.matrixWorld.clone().multiply(mesh.bindMatrix.clone().invert()).multiply(bi.clone().invert());
  });
  bones.forEach((b, j) => {  // parents come before children
    b.parent.updateMatrixWorld(true);
    if (bound[j]) new THREE.Matrix4().copy(b.parent.matrixWorld).invert().multiply(bound[j]).decompose(b.position, b.quaternion, b.scale);
    b.updateMatrixWorld(true); rest[j].copy(b.quaternion); restPos[j].copy(b.position);
  });
  // frame her
  outer.updateMatrixWorld(true);
  const box = new THREE.Box3(), v = new THREE.Vector3(), body = meshes.body;
  for (let i = 0; i < body.geometry.attributes.position.count; i += 7) { body.getVertexPosition(i, v); box.expandByPoint(v.applyMatrix4(body.matrixWorld)); }
  const s = 1.75 / (box.max.y - box.min.y); outer.scale.setScalar(s);
  outer.position.set(-(box.min.x + box.max.x) / 2 * s, -box.min.y * s, -(box.min.z + box.max.z) / 2 * s);
  outer.updateMatrixWorld(true);
  controls.target.set(0, 0.95, 0); cam.position.set(0, 1.15, 3.6);

  const B = Object.fromEntries(bones.map((b, i) => [b.name, i]));
  function resize(){ const w = el.clientWidth, h = el.clientHeight; renderer.setSize(w, h, false); cam.aspect = w / h; cam.updateProjectionMatrix(); }
  new ResizeObserver(resize).observe(el); resize();

  // pose helpers: turn a bone about a WORLD axis (degrees), keeping its parent where it is
  const tmpQ = new THREE.Quaternion(), pw = new THREE.Quaternion(), bw = new THREE.Quaternion();
  function turn(name, axis, deg){
    const b = bones[B[name]]; if (!b) return;
    b.updateWorldMatrix(true, false);
    b.parent.getWorldQuaternion(pw); b.getWorldQuaternion(bw);
    // the root group may be scaled/moved but not turned, so world axes are model axes
    tmpQ.setFromAxisAngle(new THREE.Vector3(...axis).normalize(), deg * Math.PI / 180);
    b.quaternion.copy(pw.invert().multiply(tmpQ.multiply(bw)));
    b.updateMatrixWorld(true);
  }
  // point a bone at a world direction (the line from it to its child), by the smallest turn
  const va = new THREE.Vector3(), vb = new THREE.Vector3();
  function aim(name, child, dir){
    const b = bones[B[name]], c = bones[B[child]]; if (!b || !c) return;
    b.updateWorldMatrix(true, true);
    b.getWorldPosition(va); c.getWorldPosition(vb); vb.sub(va).normalize();
    const q = new THREE.Quaternion().setFromUnitVectors(vb, new THREE.Vector3(...dir).normalize());
    b.parent.getWorldQuaternion(pw); b.getWorldQuaternion(bw);
    b.quaternion.copy(pw.invert().multiply(q.multiply(bw))); b.updateMatrixWorld(true);
  }
  function reset(){ bones.forEach((b, i) => { b.quaternion.copy(rest[i]); b.position.copy(restPos[i]); }); outer.updateMatrixWorld(true); }
  const footY = () => Math.min(...['foot_l','foot_r','ball_l','ball_r','calf_l','calf_r'].map(n => bones[B[n]].getWorldPosition(new THREE.Vector3()).y));
  const baseY = outer.position.y; const restFoot = Math.min(...['foot_l','foot_r','ball_l','ball_r'].map(n => bones[B[n]].getWorldPosition(new THREE.Vector3()).y));
  function ground(){ outer.position.y = baseY; outer.updateMatrixWorld(true); outer.position.y = baseY + restFoot - footY(); outer.updateMatrixWorld(true); }
  const api = { spinner, ground, outer, THREE, renderer, scene, cam, controls, meshes, bones, B, mats, turn, aim, reset, rootGroup, tex, mat, onFrame: null, restPos };
  const clock = new THREE.Clock();
  renderer.setAnimationLoop(() => { const t = clock.getElapsedTime(); if (api.onFrame) api.onFrame(t); controls.update(); renderer.render(scene, cam); });
  onProgress && onProgress(1);
  return api;
}
