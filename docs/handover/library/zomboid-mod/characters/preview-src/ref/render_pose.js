// pose sheet: each movement at several moments, side and front views
const Q = new URLSearchParams(location.search), MODES = (Q.get('m') || 'idle,shamble,alert,chase,attack,crawler').split(','), TS = [0, 0.3, 0.6, 0.9, 1.2];
LEGS.off = Q.get('legless') === '1';
const SC = 7.5, CW = 300, RH = 300;
const out = document.getElementById('out'); out.width = CW * TS.length * 2; out.height = RH * MODES.length;
const ox = out.getContext('2d'); ox.fillStyle = '#fff'; ox.fillRect(0, 0, out.width, out.height);
KIT.outfit = Q.get('o') || 'police'; KIT.on = true; KIT.blood = 0.9; KIT.wear = 1; seed = 1;
const src = AI15[KIT.outfit];
const f = buildSkinned(src, src); if (LEGS.off) makeLegless(f);
await Promise.all(__LOADS);
PLAYER.x = 30; PLAYER.z = 20;
MODES.forEach((m, r) => TS.forEach((t, i) => {
  MOVE.look = Math.atan2(30, 20); MOVE.lookV = 0;
  zombieMove(f, m, t, { phase: 0, dt: 0.016 });
  const c = document.createElement('canvas'); c.width = CW * 2; c.height = RH; const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, CW * 2, RH);
  rasterFigure(f, x, CW * 0.5, 'side', SC, RH - 15);
  const zb = null; rasterFigure(f, x, CW * 1.5, 'front', SC, RH - 15);
  x.fillStyle = '#c00'; x.font = '16px sans-serif'; x.fillText(m + ' t=' + t, 6, 18); x.fillStyle = '#888'; x.fillRect(0, RH - 15 + 0.4 * SC, CW * 2, 1);
  ox.drawImage(c, i * CW * 2, r * RH);
}));
document.title = 'done';
