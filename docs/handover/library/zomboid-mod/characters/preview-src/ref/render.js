// ---- reference sheets: each outfit as a front / back / side turnaround on the skin-template layout ----
KITS.none = () => {};
const Sx = 1800 / 34.4, Gy = 1938, CX = { front: 560, back: 1536, side: 2560 };
const JOBS = (new URLSearchParams(location.search).get('o') || 'soldier').split(',');
const tone = new URLSearchParams(location.search).get('t') || 'olive_1';
const out = document.getElementById('out'); out.width = 3072 * JOBS.length; out.height = 2048;
const ox = out.getContext('2d'); ox.fillStyle = '#ffffff'; ox.fillRect(0, 0, out.width, out.height);
for (let i = 0; i < JOBS.length; i++) {
  const o = JOBS[i]; KIT.outfit = o; KIT.on = true; KIT.blood = 0.3; KIT.wear = 0.7; seed = 1;
  const src = new URLSearchParams(location.search).get('ai') ? AI15[new URLSearchParams(location.search).get('ai')] : SKINS[o === 'none' ? 'patient' : o][tone];
  const f = buildSkinned(src, src); rotZero(f.userData.J);
  await Promise.all(__LOADS);
  const c = document.createElement('canvas'); c.width = 3072; c.height = 2048; const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, 3072, 2048);
  for (const v of ['front', 'back', 'side']) rasterFigure(f, x, CX[v], v, Sx, Gy);
  ox.drawImage(c, 3072 * i, 0);
}
document.title = 'done';
