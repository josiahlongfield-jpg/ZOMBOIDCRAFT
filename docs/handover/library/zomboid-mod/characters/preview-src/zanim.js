// ---------- the in-game walker animation (WalkerModel.setupAnim + vanilla zombie arms), on the new body ----------
// Minecraft angles carry over with x unchanged and y/z negated (its model space is y down, front -z).
// Time runs in game ticks (20 a second); the walk cycle advances by the walk speed every tick.
const ZMODES = {
  shamble: { speed: 0.45 }, chase: { speed: 0.85, aggressive: true }, attack: { speed: 0, aggressive: true, swing: true },
  idle: { speed: 0 }, crawler: { speed: 0.35, lie: 'crawler' }, down: { speed: 0, lie: 'back' },
};
function zombieAnim(fig, mode, t, v) {
  const J = fig.userData.J; rotZero(J);
  const S = STYLES.A, M = ZMODES[mode] || ZMODES.shamble, PI = Math.PI, sin = Math.sin, cos = Math.cos;
  const baseY = S.thigh[1] + S.shin[1] + S.foot[1] + S.pelvis[1] / 2;
  J.hips.position.set(0, baseY, 0); J.hips.rotation.set(0, 0, 0);
  const time = t * 20, ph = v.phase, side = v.side, speed = M.speed, walk = time * speed, step = walk * 0.6662;
  const cyc = t % 1.6, swing = M.swing && cyc < 0.45 ? cyc / 0.45 : 0;
  const rA = [0, 0, 0], lA = [0, 0, 0], rL = [0, 0, 0], lL = [0, 0, 0], hd = [0, 0, 0], bd = [0, 0, 0];
  let rF = 0, lF = 0, rS = 0, lS = 0;
  // vanilla humanoid legs, then the stiff dragging gait
  rL[0] = cos(step) * 1.4 * speed; lL[0] = cos(step + PI) * 1.4 * speed;
  rL[0] *= side > 0 ? 0.55 : 0.8; lL[0] *= side > 0 ? 0.8 : 0.55;
  hd[0] = 0.15; hd[2] = 0.22 * side + sin(time * 0.05 + ph) * 0.06;
  bd[2] = sin(step) * 0.07 * speed;
  if (M.aggressive) {
    const f = sin(swing * PI), g = sin((1 - (1 - swing) ** 2) * PI), h = -PI / 1.5;
    rA[0] = h + f * 1.2 - g * 0.4; lA[0] = h + f * 1.2 - g * 0.4; rA[1] = -(0.1 - f * 0.6); lA[1] = 0.1 - f * 0.6;
    rA[2] += cos(time * 0.09) * 0.05 + 0.05; lA[2] -= cos(time * 0.09) * 0.05 + 0.05;
    rA[0] += sin(time * 0.067) * 0.05; lA[0] -= sin(time * 0.067) * 0.05;
    rA[0] += sin(time * 0.13 + ph) * 0.08; lA[0] += cos(time * 0.11 + ph) * 0.08;
  } else {
    rA[0] = 0.1 + cos(step + PI) * 0.3 * speed; lA[0] = -0.05 + cos(step) * 0.25 * speed;
    rA[2] = 0.08 + sin(time * 0.06 + ph) * 0.03; lA[2] = -0.08 - sin(time * 0.05 + ph) * 0.03;
  }
  const lead = side > 0 ? 0.75 : 0.4, drag = side > 0 ? 0.4 : 0.75, stride = Math.min(1, speed * 2.5);
  // the knee folds while its leg swings forward (hip angle falling), so the foot lifts and clears the ground,
  // and straightens as the foot plants and the leg pushes back under the body
  rS = 0.1 + Math.max(0, sin(step)) * (lead + 0.25) * stride; lS = 0.1 + Math.max(0, -sin(step)) * (drag + 0.25) * stride;
  const grab = sin(swing * PI);
  if (M.aggressive) {
    rF = -0.25 - 0.5 * (1 - grab) + sin(time * 0.17 + ph) * 0.12; lF = -0.35 - 0.5 * (1 - grab) + cos(time * 0.15 + ph) * 0.12;
    rA[0] -= grab * 0.35; lA[0] -= grab * 0.35;
  } else {
    rF = -0.3 - Math.max(0, cos(step)) * 0.35 * stride; lF = -0.4 - Math.max(0, -cos(step)) * 0.35 * stride;
  }
  const dip = Math.abs(cos(step)) * 0.45 * stride;                    // lowest with the legs spread, highest as they pass
  J.hips.position.y -= dip;
  if (M.lie === 'crawler') {
    const reach = sin(step) * 0.55 * Math.max(speed, 0.15), pull = (sin(step) + 1) * 0.5, tt = time + ph * 40;
    rA[0] = -2.7 + reach; lA[0] = -2.7 - reach; rA[2] = 0.25; lA[2] = -0.25; rA[1] = lA[1] = 0;
    hd[0] = -1.05; rL[0] = 0.08 + sin(tt * 0.05) * 0.05; lL[0] = -0.04;
    rF = -0.9 * (1 - pull); lF = -0.9 * pull; rS = 0.25 + sin(tt * 0.05) * 0.08; lS = 0.1; bd[2] = 0;
    J.hips.rotation.x = PI / 2; J.hips.position.set(0, S.torso[2] / 2 + 0.4, -S.pelvis[1] - 1 + pull * 0.6);
  } else if (M.lie === 'back') {
    const tt = time + ph * 40;
    rA[0] = -1.35 + sin(tt * 0.17) * 0.3; lA[0] = -1.2 + cos(tt * 0.15) * 0.3; rA[2] = 0.15; lA[2] = -0.15; rA[1] = lA[1] = 0;
    hd[0] = -0.25; rL[0] = -0.3 + sin(tt * 0.11) * 0.15; lL[0] = 0.05;
    rF = -0.6 + sin(tt * 0.21) * 0.4; lF = -0.5 + cos(tt * 0.19) * 0.4; rS = 0.5 + sin(tt * 0.11) * 0.2; lS = 0.2; bd[2] = 0;
    J.hips.rotation.x = -PI / 2; J.hips.position.set(0, S.torso[2] / 2 + 0.4, S.pelvis[1] + 1);
  }
  const set = (j, r) => j.rotation.set(r[0], -r[1], -r[2]);
  set(J.shR, rA); set(J.shL, lA); set(J.hipR, rL); set(J.hipL, lL); set(J.head, hd); set(J.spine, bd);
  J.elR.rotation.x = rF; J.elL.rotation.x = lF; J.knR.rotation.x = rS; J.knL.rotation.x = lS;
}
