// node armfit0.js kitfile: a ported first-person reload starts and ends exactly in his posed hold. The template's whole-
// reload shoulder fit (armfit, set for his own gun's reach) can leave this gun's arm short of its posed grip at the
// first and last frame, so the fit is eased (in the hand frame) until both ends land on the posed grips.
require('./engine.js'); const fs = require('fs'), zlib = require('zlib'); const E = globalThis.REngine; const { v3, m4t, m4R, m3v, m3T } = E;
const S = JSON.parse(fs.readFileSync('kit/static.json'));
const file = process.argv[2]; const kit = JSON.parse(fs.readFileSync(file)); const raw = kit.field && kit.field.data;
if (raw) kit.field.arr = new Int8Array(zlib.gunzipSync(Buffer.from(raw, 'base64')));
const sp = kit.views.fp; const log = [];
for (let it = 0; it < 8; it++) {
  const { rig, frames } = E.bake(S, kit, JSON.parse(JSON.stringify(sp)), 'fp'); let moved = false;
  for (const s of 'LR') {
    const af = (sp.armfit || {})[s]; if (!af || !af.sho) continue;
    const f = frames[0], g = frames[frames.length - 1]; const m = Math.max(f.miss[s] || 0, g.miss[s] || 0);
    if (it === 0) log.push(`${s} start ${Math.round((f.miss[s] || 0) * 100)}cm`);
    if (m < 0.008) continue;
    const pick = (f.miss[s] || 0) >= (g.miss[s] || 0) ? f : g;
    const d = v3.sub(m4t(pick.ht[s]), m4t(pick.bones[rig.pre(s, 'Hand')]));
    const dl = m3v(m3T(m4R(rig.Pi)), d); af.sho = af.sho.map((x, i) => Math.round((x + dl[i]) * 10000) / 10000); moved = true;
  }
  if (!moved) break;
}
const { frames } = E.bake(S, kit, JSON.parse(JSON.stringify(sp)), 'fp');
delete kit.field.arr; fs.writeFileSync(file, JSON.stringify(kit));
console.log(kit.gid, 'armfit0', log.join(' ') || '-', '-> ends', ['L', 'R'].map(s => Math.round(Math.max(frames[0].miss[s] || 0, frames[frames.length - 1].miss[s] || 0) * 100)).join('/'), JSON.stringify(sp.armfit));
