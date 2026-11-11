THREE.Vector3.prototype.addScaledVector = function (v, s) { this.x += v.x * s; this.y += v.y * s; this.z += v.z * s; return this; };
THREE.BoxGeometry.prototype.dispose = function () {};
var SHOOT = { stumps: [], cut: new Set() };
var state = { yaw: 1.62, anim: 'climb' };
const f = buildSkinned(AI15.civilian, AI15.civilian); await Promise.all(__LOADS);
const props = brkProps(); await Promise.all(__LOADS);
let errs = 0, maxShards = 0, rested = 0;
for (const mode of ['bangdoor', 'bangwin', 'climb', 'shamble', 'climb']) {
  BRK.t0 = 0;
  for (let i = 0; i < 1200; i++) {
    const t = i / 60;
    try { zombieMove(f, mode, t, { phase: 1.3, dt: 1 / 60 }); brkStep(props, mode, 1 / 60); }
    catch (e) { errs++; if (errs < 4) console.log('ERR', mode, t, e.stack); }
    maxShards = Math.max(maxShards, props.P.shards.length);
  }
  rested = props.P.shards.filter(s => s.rest).length;
  const J = f.userData.J, bad = Object.keys(J).filter(k => ['x', 'y', 'z'].some(a => !isFinite(J[k].rotation[a])));
  console.log(mode, 'visible', props.g.visible, 'door', props.P.door.visible, 'win', props.P.win.visible, 'shards', props.P.shards.length, 'rested', rested, 'nonfinite joints', bad.join(','), 'hips', J.hips.position.x.toFixed(1), J.hips.position.y.toFixed(1), J.hips.position.z.toFixed(1));
}
console.log('errors', errs, 'max shards', maxShards);
document.title = 'done';
