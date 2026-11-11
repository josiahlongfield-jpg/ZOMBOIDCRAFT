// node chk2.js gid...: ported wave-2 reloads vs their template (his saved one): worst hand miss (cm) per view, and the
// worst finger depth inside the gun (mm, from the gun's distance field) at every 3rd frame
require('./engine.js'); const fs = require('fs'), zlib = require('zlib'); const E = globalThis.REngine; const { m4inv, m4mul, m4pt } = E;
const S = JSON.parse(fs.readFileSync('kit/static.json'));
const f32 = b => { const u = Buffer.from(b, 'base64'); return new Float32Array(u.buffer.slice(u.byteOffset, u.byteOffset + u.length)); };
const P = f32(S.P), J = f32(S.J), W = f32(S.W); const nv = P.length / 3; const NB = S.names.length;
const fing = new Set(); for (const [s, sd] of [[0, 'R'], [1, 'L']]) { fing.add(S.names.indexOf(`Bip01_${sd}_Finger0`)); S.FING.forEach((f, k) => fing.add(NB + s * 4 + k)); }
function load(dir, gid) { const kit = JSON.parse(fs.readFileSync(`${dir}/g_${gid}.json`)); if (kit.field && kit.field.data) kit.field.arr = new Int8Array(zlib.gunzipSync(Buffer.from(kit.field.data, 'base64'))); return kit; }
function pen(kit, rig, fr) {
  const F = kit.field; if (!F || !F.arr) return 0; const K = rig.skin(fr.bones); const Gi = m4mul(m4inv(fr.gun), rig.Pi); let worst = 0;
  for (let v = 0; v < nv; v += 2) { let best = -1, bw = 0; for (let j = 0; j < 4; j++) if (W[v * 4 + j] > bw) { bw = W[v * 4 + j]; best = J[v * 4 + j]; } if (!fing.has(best)) continue;
    const p = [0, 0, 0]; for (let j = 0; j < 4; j++) { const w = W[v * 4 + j]; if (!w) continue; const b = J[v * 4 + j]; for (let r = 0; r < 3; r++) p[r] += w * (K[b * 16 + r] * P[v * 3] + K[b * 16 + 4 + r] * P[v * 3 + 1] + K[b * 16 + 8 + r] * P[v * 3 + 2] + K[b * 16 + 12 + r]); }
    const x = m4pt(Gi, p); const ix = Math.round((x[0] - F.lo[0]) / F.vox), iy = Math.round((x[1] - F.lo[1]) / F.vox), iz = Math.round((x[2] - F.lo[2]) / F.vox);
    if (ix < 0 || iy < 0 || iz < 0 || ix >= F.shape[0] || iy >= F.shape[1] || iz >= F.shape[2]) continue;
    const d = F.arr[(ix * F.shape[1] + iy) * F.shape[2] + iz] * F.vox * rig.scale / 16 * 1000; if (d < worst) worst = d; }
  return worst;
}
function run(kit, views) {
  let line = '';
  for (const view of ['fp', 'tp', 'ta']) { const { rig, frames } = E.bake(S, kit, JSON.parse(JSON.stringify(views[view])), view); const m = { L: 0, R: 0 }; let pw = 0;
    frames.forEach((f, i) => { for (const s of 'LR') m[s] = Math.max(m[s], (f.miss || {})[s] || 0); if (i % 3 === 0) pw = Math.min(pw, pen(kit, rig, f)); });
    line += ` ${view} miss ${Math.round(m.L * 100)}/${Math.round(m.R * 100)} fing ${pw.toFixed(1)}`; }
  return line;
}
for (const gid of process.argv.slice(2)) {
  const kit = load('www/kit', gid); const tk = load('www/kit', kit.from);
  const saved = JSON.parse(JSON.parse(fs.readFileSync(`../dbsnap2/reloads/${kit.from}.json`)).json);
  console.log(gid.padEnd(14), run(kit, kit.views)); console.log(('  (' + kit.from + ')').padEnd(14), run(tk, saved));
}
