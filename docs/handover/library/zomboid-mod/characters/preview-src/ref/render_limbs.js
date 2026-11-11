const Q = new URLSearchParams(location.search);
KIT.outfit = Q.get('o') || 'police'; KIT.on = true; KIT.blood = 0.9; KIT.wear = 1; seed = 1;
const src = AI15[KIT.outfit];
const f = buildSkinned(src, src);
await Promise.all(__LOADS);
var SHOOT = { stumps: [] };
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


const cuts = (Q.get('cuts') || 'elR,knL').split(','), mode = Q.get('m') || 'shamble';
PLAYER.x = 30; PLAYER.z = 20; MOVE.look = 0; MOVE.lookV = 0;
zombieMove(f, mode, 0.6, { phase: 0, dt: 0.016 });
const S = STYLES.A;
cuts.forEach((c, i) => {
  addStump(f, c);
  const J = f.userData.J, k = c.slice(0, -1), root = J[c];
  root.parent.remove(root); f.add(root);
  const len = k === 'sh' ? 15 : k === 'hip' ? 16 : k === 'el' ? 8 : 9;
  root.position.set((i % 2 ? 1 : -1) * (12 + (i >> 1) * 6), (k === 'hip' || k === 'kn' ? S.thigh[0] : S.fore[0]) / 2 + 0.3, 8 + (i >> 1) * 5);
  root.rotation.set(0, 0, (i % 2 ? -1 : 1) * Math.PI / 2);
  const end = rag(k === 'el' ? S.fore[0] : k === 'kn' ? S.shin[0] : k === 'hip' ? S.thigh[0] : S.upper[0], k === 'sh' || k === 'hip');
  end.rotation.x = Math.PI; end.position.y = k === 'sh' ? S.shoulder - 0.6 : k === 'hip' ? 0.3 : 0.1; root.add(end);
});
await Promise.all(__LOADS);
const SC = 7.5, CW = 520, RH = 360;
const out = document.getElementById('out'); out.width = CW * 3; out.height = RH;
const x = out.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, out.width, out.height);
rasterFigure(f, x, CW * 0.5, 'front', SC, RH - 15);
rasterFigure(f, x, CW * 1.5, 'side', SC, RH - 15);
f.rotation.y = Math.PI; rasterFigure(f, x, CW * 2.5, 'front', SC, RH - 15);
x.fillStyle = '#c00'; x.font = '16px sans-serif'; x.fillText(mode + ' cuts ' + cuts.join(' '), 6, 18);
document.title = 'done';
