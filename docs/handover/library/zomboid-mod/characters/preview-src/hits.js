// ---------- shots move the body (Josiah 27 Oct 07:51) ----------
// The game's ZHit: the part a shot strikes snaps along the shot and swings back, and the rest of the body rocks
// with it a moment later, harder for heavier rounds. A head hit whips the head back or round, a chest hit knocks
// the shoulders back and twists the trunk, an arm hit flings the arm, a leg hit buckles the knee so the body dips.
// It's laid on top of whatever the zombie is doing. The push that moves the whole zombie is the game's shove; here
// it staggers back a step and comes on again. Parts are the hitboxes': head 0, chest 1 (left) and 2 (right), hips
// 3, right upper arm 4, forearm 5, left upper arm 6, forearm 7, right thigh 8, shin 9, left thigh 10, shin 11.
const HIT_RISE = 0.07, HIT_FOLLOW = 0.09, HIT_LAG = 0.05;
const HIT_SHOVE = [1.5, 2.0, 3.0, 4.5];        // how far a shot staggers it back (preview units), handgun to buckshot
var HITS = [];                                  // var: rebuild() runs before this line

// a number from 0 to 1, the same for the same seed and key (the game's ZDeath.rnd)
function drnd(seed, key) {
  let h = (Math.imul(seed, 0x9E3779B9) + Math.imul(key, 0x85EBCA6B)) | 0;
  h ^= h >>> 16; h = Math.imul(h, 0x7FEB352D); h ^= h >>> 15; h = Math.imul(h, 0x846CA68B); h ^= h >>> 16;
  return (h >>> 8) / 16777216;
}
// a kick that rises to 1 at rise seconds and dies away over about five times that
function hitPulse(t, rise) { if (t <= 0) return 0; const u = t / rise; return u * Math.exp(1 - u); }
const hitLive = t => t < 0.6;
function hitAdd(P, k, x, y, z) { P[k][0] += x; P[k][1] += y; P[k][2] += z; }

// one shot's jerk t seconds after it struck part, going heading (radians from the body's front round toward its
// left) with force 0 (handgun) to 3 (buckshot); scale shares it out among the parts one blast struck
function hitApply(P, part, heading, force, t, seed, lying, scale = 1) {
  if (part < 0 || !hitLive(t)) return;
  const r = hitPulse(t, HIT_RISE), r2 = hitPulse(t - HIT_LAG, HIT_FOLLOW);
  if (r === 0) return;
  const a = (0.22 + 0.13 * force) * scale, bx = Math.sin(heading), bz = Math.cos(heading);
  const twist = drnd(seed, 11) < 0.5 ? -1 : 1;
  if (lying) {
    // flat on the ground it can only jolt: the trunk slams, the head knocks, the arms jump
    hitAdd(P, 'spine', a * 0.45 * r, 0, -a * 0.3 * bx * r);
    hitAdd(P, 'neck', a * 0.4 * r2, 0, 0);
    hitAdd(P, 'head', a * 0.6 * r2, a * 0.4 * twist * r2, 0);
    hitAdd(P, 'elR', -a * 0.5 * r2, 0, 0);
    hitAdd(P, 'elL', -a * 0.5 * r2, 0, 0);
    P.pos[0] += bx * a * 2 * r; P.pos[2] += bz * a * 2 * r;
    return;
  }
  if (part === 0) {
    hitAdd(P, 'head', a * 1.3 * bz * r, a * 0.5 * twist * r, -a * 1.3 * bx * r);
    hitAdd(P, 'neck', a * 0.5 * bz * r, 0, -a * 0.5 * bx * r);
    hitAdd(P, 'spine', a * 0.25 * bz * r2, 0, -a * 0.2 * bx * r2);
  } else if (part === 1 || part === 2) {
    const side = part === 2 ? 1 : -1;     // a push on the right side turns it toward its left
    hitAdd(P, 'spine', a * bz * r, a * 0.6 * bz * side * r, -a * 0.7 * bx * r);
    // the head and arms hang back a moment, then whip after
    hitAdd(P, 'head', -a * 0.6 * bz * r2, 0, a * 0.4 * bx * r2);
    hitAdd(P, 'shR', a * 0.5 * bz * r2, 0, a * 0.4 * bx * r2);
    hitAdd(P, 'shL', a * 0.5 * bz * r2, 0, a * 0.4 * bx * r2);
  } else if (part === 3) {
    hitAdd(P, 'hips', a * 0.5 * bz * r, a * 0.3 * twist * r, -a * 0.3 * bx * r);
    hitAdd(P, 'spine', -a * 0.4 * bz * r2, 0, a * 0.3 * bx * r2);
    P.pos[0] += bx * a * 2.5 * r; P.pos[2] += bz * a * 2.5 * r;
  } else if (part <= 7) {
    const right = part < 6, fore = part === 5 || part === 7, R = right ? 'R' : 'L';
    hitAdd(P, 'sh' + R, -a * (fore ? 0.8 : 1.6) * bz * r, 0, a * bx * r);
    hitAdd(P, 'el' + R, -a * (fore ? 0.9 : 0.4) * r, 0, 0);
    hitAdd(P, 'spine', a * 0.25 * bz * r2, a * 0.3 * bz * (right ? 1 : -1) * r2, -a * 0.15 * bx * r2);
  } else {
    const right = part < 10, R = right ? 'R' : 'L';
    hitAdd(P, 'hip' + R, -a * 0.6 * bz * r, 0, a * 0.3 * bx * r);
    hitAdd(P, 'kn' + R, a * 1.2 * r, 0, 0);
    // the knee gives: it dips toward that side and hunches
    P.pos[1] -= a * 2.5 * r2;
    hitAdd(P, 'spine', a * 0.4 * r2, 0, (right ? 1 : -1) * a * 0.25 * r2);
  }
  if (part > 3) {
    // the whole body rocks back a little from the push
    hitAdd(P, 'spine', a * 0.3 * bz * r2, 0, -a * 0.25 * bx * r2);
  }
}

// shots that struck the living zombie this click: the parts (one bit each), which way, how hard
function hitNow(t0, parts, heading, force) {
  if (!parts) return;
  HITS.push({ t0, parts, heading, force, seed: Math.floor(rnd() * 256) });
  if (HITS.length > 4) HITS.shift();
}

// lays the live jerks over the pose the zombie is in now, and staggers it back from the shove
function stepHits(t) {
  for (let i = HITS.length - 1; i >= 0; i--) if (t - HITS[i].t0 > 0.9) HITS.splice(i, 1);
  if (!HITS.length) return;
  const J = fig.userData.J, P = snap(J), lying = P.order === 'XYZ';
  for (const h of HITS) {
    const age = t - h.t0, parts = [];
    for (let b = 0; b < 12; b++) if (h.parts >> b & 1) parts.push(b);
    for (const b of parts) hitApply(P, b, h.heading, h.force, age, h.seed + b, lying, 1 / Math.sqrt(parts.length));
    const s = HIT_SHOVE[h.force] * segL(age, 0, 0.12) * (1 - segL(age, 0.3, 0.9));
    P.pos[0] += Math.sin(h.heading) * s; P.pos[2] += Math.cos(h.heading) * s;
  }
  put(J, P);
}
