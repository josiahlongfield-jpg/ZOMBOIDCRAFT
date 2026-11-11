// ---------- zombie movement v2: every joint (spine, neck, head, shoulders, elbows, wrists, hips, knees, ankles) ----------
// Angles are in three.js space on the body above: front is +z, the character's right is -x.
// Shoulder/hip x < 0 swings a limb forward, elbow x < 0 bends the forearm forward, knee x > 0 bends the shin back,
// spine/neck/head x > 0 leans forward. Outward arm spread is +out (mirrored per side).
const LEGS = { off: false };
const PLAYER = { x: 0, z: 30, on: false };
const MOVE = { look: 0, lookV: 0, twitch: 0 };
function makeLegless(f) {
  // thighs end in short ragged stumps; shins and feet are gone
  const J = f.userData.J, S = STYLES.A;
  for (const R of ['R', 'L']) {
    J['kn' + R].visible = false;
    const hip = J['hip' + R]; hip.scale.y = 0.34;
    const cap = box(S.thigh[0] + 0.5, 1.6, S.thigh[0] + 0.5, gear('#3a0a07', { shine: true, noise: .2, grime: 0, clean: true, outline: .5 }));
    cap.position.set(0, -S.thigh[1] + 0.2, 0); hip.add(cap);
    const bone = box(1.0, 2.2, 1.0, gear('#cfc4a4', { noise: .15, grime: 0, clean: true, outline: .4 }));
    bone.position.set(0.4, -S.thigh[1] - 1.2, 0.2); hip.add(bone);
    for (let i = 0; i < 4; i++) {
      const s = 0.5 + rnd() * 0.7, sh = box(s, 2 + rnd() * 2.5, 0.3, gear('#4a0d0a', { shine: true, noise: .1, grime: 0, clean: true, outline: .4 }));
      const a = rnd() * Math.PI * 2; sh.position.set(Math.cos(a) * 1.6, -S.thigh[1] - 0.6, Math.sin(a) * 1.6); sh.rotation.y = a; hip.add(sh);
    }
  }
}
function zombieMove(fig, mode, t, v) {
  const J = fig.userData.J, S = STYLES.A, PI = Math.PI, sin = Math.sin, cos = Math.cos, ph = v.phase || 0;
  const clamp = (x, a, b) => Math.max(a, Math.min(b, x)), sm = x => x * x * (3 - 2 * x), seg = (x, a, b) => sm(clamp((x - a) / (b - a), 0, 1));
  for (const k in J) { J[k].rotation.set(0, 0, 0); }
  const T = S.thigh[1], SH = S.shin[1], BASE = S.thigh[1] + S.shin[1] + S.foot[1] + S.pelvis[1] / 2;
  const arm = (R, sx, out, sy, el, wr, wz = 0) => {
    const s = R === 'R' ? -1 : 1;
    J['sh' + R].rotation.set(sx, sy * s, out * s); J['el' + R].rotation.set(el, 0, 0); J['wr' + R].rotation.set(wr, 0, wz * s);
  };
  const leg = (R, a, k, an, out = 0, tw = 0) => {
    const s = R === 'R' ? -1 : 1;
    J['hip' + R].rotation.set(a, tw * s, out * s); J['kn' + R].rotation.set(k, 0, 0); J['an' + R].rotation.set(an, 0, 0);
  };
  const drop = (a, k) => T * cos(a) + SH * cos(a + k);          // how far below the hip the ankle hangs
  const plant = (aR, kR, aL, kL, extra = 0) => BASE - (T + SH - Math.max(drop(aR, kR), drop(aL, kL))) - extra;
  // where the player is, in the zombie's own frame (for the alert head turn)
  const lookAt = () => {
    const dx = PLAYER.x - fig.position.x, dz = PLAYER.z - fig.position.z;
    let a = Math.atan2(dx, dz) - fig.rotation.y; a = Math.atan2(sin(a), cos(a));
    // eyes snap first, the neck follows a moment later, then the body: a spring with a little overshoot
    const acc = (a - MOVE.look) * 60 - MOVE.lookV * 9; MOVE.lookV += acc * v.dt; MOVE.look += MOVE.lookV * v.dt;
    return MOVE.look;
  };
  const hips = J.hips; hips.rotation.order = 'YXZ'; hips.position.set(0, BASE, 0);
  if (LEGS.off) return crawl(mode === 'chase' ? 'fast' : mode === 'attack' ? 'grab' : mode === 'idle' ? 'rest' : mode === 'alert' ? 'alert' : mode === 'down' ? 'down' : 'slow', true);
  if (mode === 'crawler') return crawl('slow', false);
  if (mode === 'down') return crawl('down', false);

  if (mode === 'idle' || mode === 'alert') {
    // the feet stay planted and the legs still; only the body above the hips sways, the head lolls behind it,
    // and the arms hang like dead weight, swinging a beat after the shoulders move
    const w = t * 0.9 + ph, sw = sin(w), alert = mode === 'alert';
    const amp = alert ? 0.5 : 1;
    const kR = 0.16, kL = 0.2;
    leg('R', -kR * 0.45, kR, -kR * 0.55, 0.02); leg('L', -kL * 0.45, kL, -kL * 0.55, 0.04, 0.12);
    hips.position.y = plant(-kR * 0.45, kR, -kL * 0.45, kL);
    J.spine.rotation.set(0.16 + sin(w * 0.5 + 0.8) * 0.07 * amp, sin(w * 0.5) * 0.08 * amp, -sw * 0.13 * amp);
    J.neck.rotation.set(0.04, 0, -sin(w - 1.1) * 0.08 * amp);
    J.head.rotation.set(0.1 + sin(t * 0.7 + ph) * 0.06, 0, 0.22 + sin(w - 1.6) * 0.1 * amp);
    const lag = sin(w - 1.3);
    arm('R', 0.04 + sin(w * 0.5 - 1) * 0.05, 0.06 - lag * 0.08 * amp, 0.1, -0.28 - sin(t * 0.6 + ph) * 0.05, 0.15, 0.1);
    arm('L', 0.08 + sin(w * 0.5 + 1) * 0.05, 0.08 + lag * 0.08 * amp, 0.1, -0.35 - sin(t * 0.5 + ph) * 0.05, 0.2, 0.1);
    if (alert) {
      // the head whips round to the player; the neck and then the spine follow (feet stay planted), and the arms start to lift
      const a = lookAt(), hd = clamp(a, -1.5, 1.5);
      J.head.rotation.y = hd * 0.45; J.neck.rotation.y = hd * 0.3; J.spine.rotation.y += hd * 0.35;
      J.head.rotation.z = 0.1 * Math.sign(hd) * -1 + sin(t * 9) * 0.015;     // twitchy tilt as it locks on
      J.head.rotation.x = 0.05; J.neck.rotation.x = -0.1; J.spine.rotation.x = 0.2;
      const lift = 0.55 + sin(t * 1.3 + ph) * 0.08;
      arm('R', -lift, 0.12, 0.25, -0.55, -0.25, 0); arm('L', -lift * 0.8, 0.12, 0.25, -0.65, -0.3, 0);
      MOVE.on = true;
    }
    return;
  }
  if (mode === 'shamble' || mode === 'chase') {
    const chase = mode === 'chase', f = chase ? 1.25 : 0.5, p = t * f * 2 * PI + ph;
    // gait: the right leg steps properly, the left one drags (shorter swing, little knee lift, the foot scuffs)
    const AR = chase ? 0.72 : 0.42, AL = chase ? 0.62 : 0.26;
    const aR = -AR * sin(p) + (chase ? -0.1 : 0.02), aL = AL * sin(p) + (chase ? -0.1 : 0.06);
    const swR = Math.max(0, cos(p)), swL = Math.max(0, -cos(p));                        // swinging forward
    const kR = 0.12 + swR * (chase ? 1.25 : 0.7), kL = 0.12 + swL * (chase ? 1.0 : 0.22);
    const anR = -(aR + kR) * (1 - swR) + swR * 0.35, anL = -(aL + kL) * (1 - swL) + swL * (chase ? 0.3 : -0.15);
    leg('R', aR, kR, anR, 0.02, 0); leg('L', aL, kL, anL, 0.05, 0.18);
    // the whole body rides the gait: weight shifts over the planted foot, the hips roll and twist, the spine
    // counter-twists, and the head trails a beat behind
    const roll = sin(p), twist = sin(p);
    hips.position.x = roll * (chase ? 0.5 : 0.85); hips.rotation.z = roll * (chase ? 0.06 : 0.11); hips.rotation.y = twist * (chase ? 0.14 : 0.2);
    J.hipR.rotation.z -= hips.rotation.z; J.hipL.rotation.z -= hips.rotation.z;
    hips.position.y = plant(aR, kR, aL, kL) - (chase ? 0.4 : 0.15);
    hips.position.z = chase ? 0.8 : 0;
    const lean = chase ? 0.5 : 0.32;
    J.spine.rotation.set(lean + sin(2 * p) * (chase ? 0.06 : 0.04), -twist * (chase ? 0.18 : 0.25), -roll * (chase ? 0.05 : 0.09));
    J.neck.rotation.set(chase ? -0.25 : 0.1, twist * 0.08, -roll * 0.05);
    J.head.rotation.set((chase ? -0.2 : 0.16) + sin(2 * p - 0.9) * (chase ? 0.08 : 0.06), twist * 0.06, (chase ? 0.06 : 0.2) + sin(p - 1.2) * 0.07);
    if (chase) {
      // (the spine's lean tips the shoulders forward, so the arms rise by the same angle to stay level)
      // arms thrust straight out ahead at shoulder height, bobbing with each stride, hands clawing open and shut
      const bob = sin(2 * p) * 0.08, claw = sin(t * 7 + ph);
      arm('R', -1.5 - lean * 0.95 - bob + sin(p) * 0.1, -0.04, 0.12, -0.12 - Math.max(0, claw) * 0.2, -0.15 + claw * 0.25, 0.1);
      arm('L', -1.44 - lean * 0.95 - bob - sin(p) * 0.1, -0.02, 0.12, -0.16 - Math.max(0, -claw) * 0.2, -0.15 - claw * 0.25, 0.1);
    } else {
      // dead arms swing from the twist with a lag; one hangs lower and loose, the other half-reaches
      const sR = sin(p + PI - 0.7), sL = sin(p - 0.7);
      arm('R', -0.12 + sR * 0.22, 0.05 + Math.max(0, -roll) * 0.08, 0.15, -0.3 - Math.max(0, sR) * 0.25, 0.25 + sR * 0.12, 0.1);
      arm('L', -0.85 + sL * 0.15, 0.1, 0.2, -0.55 + sL * 0.1, -0.1 + sL * 0.1, 0.05);
    }
    return;
  }
  if (mode === 'attack') {
    // wind up, lunge onto the front foot grabbing with both hands, pull in and bite three times, shove off and reset
    const c = (t + ph * 0.3) % 2.0, wind = seg(c, 0, 0.35) * (1 - seg(c, 0.35, 0.5)), lunge = seg(c, 0.35, 0.55) * (1 - seg(c, 1.5, 2.0));
    const bite = c > 0.6 && c < 1.45 ? Math.max(0, sin((c - 0.6) / 0.85 * 3 * 2 * PI)) : 0, pull = seg(c, 0.55, 0.8) * (1 - seg(c, 1.45, 1.8));
    const aR = -0.62 * lunge + 0.08 * wind, kR = 0.25 + 0.45 * lunge, aL = 0.38 * lunge - 0.05 * wind, kL = 0.18 + 0.25 * lunge;
    leg('R', aR, kR, -(aR + kR), 0.04); leg('L', aL, kL, -(aL + kL) + 0.25 * lunge, 0.06, 0.15);
    hips.position.y = plant(aR, kR, aL, kL); hips.position.z = 2.2 * lunge - 0.6 * wind; hips.rotation.y = -0.15 * lunge + 0.1 * wind;
    J.spine.rotation.set(0.12 - 0.2 * wind + 0.5 * lunge + 0.12 * bite, 0.1 * lunge, 0);
    J.neck.rotation.set(-0.1 * wind - 0.15 * lunge + 0.25 * bite, 0, 0);
    J.head.rotation.set(0.1 - 0.35 * wind - 0.15 * lunge + 0.45 * bite, 0, 0.12 * (1 - lunge) + sin(t * 20) * 0.03 * bite);
    // arms: wide and high in the wind-up, slam forward together, then close in and drag the target to the mouth
    const sx = -1.1 - 1.0 * wind * (1 - lunge) - 0.9 * lunge + 0.35 * pull, out = 0.55 * wind * (1 - lunge) + 0.25 * lunge * (1 - pull) - 0.1 * pull;
    arm('R', sx, out, 0.3 * pull, -0.25 - 0.15 * lunge - 0.95 * pull, -0.4 * lunge + 0.3 * pull, 0.2 * pull);
    arm('L', sx + 0.08, out, 0.3 * pull, -0.3 - 0.15 * lunge - 0.95 * pull, -0.4 * lunge + 0.3 * pull, 0.2 * pull);
    return;
  }

  function crawl(kind, legless) {
    // face down (or on its back when downed). hips.rotation.x = PI/2 lays the body flat with its front to the
    // ground; rotation.y then rolls it about its own long axis.
    hips.rotation.order = 'XYZ';
    const td = S.torso[2], ground = (legless ? 1.0 : 1.4) + td / 2;
    if (kind === 'down') {
      const tt = t + ph;
      hips.rotation.set(-PI / 2, 0, 0); hips.position.set(0, S.pelvis[2] / 2 + 0.2, 6);
      J.spine.rotation.set(-0.08, 0, 0); J.neck.rotation.set(0.35 + sin(tt * 0.9) * 0.1, 0, 0);
      J.head.rotation.set(0.25 + sin(tt * 1.7) * 0.15, sin(tt * 0.8) * 0.5, sin(tt * 1.1) * 0.12);
      arm('R', -1.2 + sin(tt * 1.9) * 0.35, 0.25, 0.2, -0.6 + sin(tt * 2.3) * 0.4, sin(tt * 3) * 0.3);
      arm('L', -1.0 + cos(tt * 1.7) * 0.35, 0.3, 0.2, -0.5 + cos(tt * 2.1) * 0.4, cos(tt * 2.7) * 0.3);
      if (!legless) { leg('R', -0.5 + sin(tt * 1.3) * 0.25, 0.9 + sin(tt * 1.3) * 0.3, 0.3, 0.1); leg('L', -0.1, 0.25, 0.1, 0.12); }
      return;
    }
    const rest = kind === 'rest', alert = kind === 'alert', grab = kind === 'grab';
    const f = kind === 'fast' ? 0.9 : kind === 'slow' ? 0.42 : 0, p = t * f * 2 * PI + ph;
    const r = sin(p), pullR = Math.max(0, r), pullL = Math.max(0, -r);
    hips.rotation.set(PI / 2 - (legless ? 0.05 : 0), r * (f ? 0.14 : 0.03), 0);
    hips.position.set(0, ground - S.pelvis[2] / 2 + S.torso[2] / 2 - 2.1, -7 + (f ? (sin(2 * p) * 0.5 + 0.5) * (legless ? 1.2 : 0.8) : 0));
    hips.position.y = S.pelvis[2] / 2 + (legless ? 0.2 : 0.5);
    // the chest heaves up on each pull (spine arches back), the neck cranes so the face stays on what it's chasing
    const heave = f ? Math.abs(r) : 0;
    J.spine.rotation.set(-0.08 - heave * (legless ? 0.28 : 0.16), -r * 0.12, 0);
    J.neck.rotation.set(-0.55 - heave * 0.1, 0, 0);
    J.head.rotation.set(-0.55 + sin(2 * p) * 0.06, r * 0.12, 0.08 + sin(t * 0.8 + ph) * 0.06);
    if (f) {
      // one arm reaches far ahead and plants, palm down; the other drags the body up to it, elbow out
      const reach = (s, pull) => {
        const ext = 1 - pull;                         // reaching when not pulling
        return [-(2.0 + 0.85 * ext) , 0.25 + 0.3 * pull, 0.2, -0.15 - 1.15 * pull, 0.9 * ext + 0.4 * pull];
      };
      const aR = reach(1, pullR), aL = reach(-1, pullL);
      arm('R', aR[0], aR[1], aR[2], aR[3], aR[4]); arm('L', aL[0], aL[1], aL[2], aL[3], aL[4]);
    } else if (alert) {
      // pushes up on both arms, chest off the ground, and the head turns to find the player
      const a = lookAt(), hd = clamp(a, -1.4, 1.4);
      J.spine.rotation.x = -0.45; J.neck.rotation.set(-0.5, hd * 0.35, 0); J.head.rotation.set(-0.35, hd * 0.5, -0.12 * Math.sign(hd));
      arm('R', -1.75, 0.2, 0.25, -0.5, 1.2); arm('L', -1.75, 0.2, 0.25, -0.5, 1.2);
    } else if (grab) {
      // lunges forward off its elbows and swipes both hands at an ankle, then drags it in
      const c = (t + ph * 0.3) % 1.8, l = seg(c, 0.2, 0.45) * (1 - seg(c, 1.2, 1.7)), pull = seg(c, 0.5, 0.9) * (1 - seg(c, 1.2, 1.7));
      J.spine.rotation.x = -0.25 - 0.35 * l; J.neck.rotation.x = -0.5 - 0.2 * l; J.head.rotation.x = -0.4 - 0.2 * l + 0.3 * pull * Math.max(0, sin(c * 18));
      hips.position.z += 2.5 * l;
      arm('R', -2.3 - 0.6 * l + 0.8 * pull, 0.35 - 0.2 * l, 0.2, -0.4 + 0.2 * l - 1.0 * pull, 0.3 + 0.5 * pull);
      arm('L', -2.2 - 0.6 * l + 0.8 * pull, 0.35 - 0.2 * l, 0.2, -0.5 + 0.2 * l - 1.0 * pull, 0.3 + 0.5 * pull);
    } else {
      // resting on its forearms, swaying its head, fingers twitching
      J.spine.rotation.x = -0.2; J.neck.rotation.x = -0.45; J.head.rotation.set(-0.4, sin(t * 0.6 + ph) * 0.35, sin(t * 0.9 + ph) * 0.1);
      arm('R', -2.1 + sin(t * 0.7) * 0.05, 0.35, 0.3, -1.3, 0.5 + sin(t * 5) * 0.08); arm('L', -2.0, 0.4, 0.3, -1.4 + sin(t * 0.8) * 0.05, 0.6);
    }
    if (!legless) {
      // legs trail behind: knees bend in turn as the feet push, toes dragging
      const kR = f ? 0.25 + pullL * 0.7 : 0.2, kL = f ? 0.25 + pullR * 0.7 : 0.35;
      leg('R', -0.05 - (f ? pullL * 0.3 : 0), kR, 0.6, 0.12, 0.1); leg('L', -0.05 - (f ? pullR * 0.3 : 0), kL, 0.6, 0.12, 0.1);
    }
  }
}
