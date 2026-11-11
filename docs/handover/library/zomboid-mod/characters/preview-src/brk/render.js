const Q = new URLSearchParams(location.search);
KIT.outfit = Q.get('o') || 'civilian'; KIT.on = true; KIT.blood = 0.6; KIT.wear = 0.8; seed = 1;
var SHOOT = { stumps: [], cut: new Set() };
var state = { yaw: 1.57 };
const mode = Q.get('m') || 'climb', view = Q.get('v') && !isNaN(+Q.get('v')) ? +Q.get('v') : Q.get('v') || 'side';
const ts = (Q.get('ts') || '0,0.45,0.95,1.38,1.6,1.9,2.25,2.6,2.95,3.3,3.7,4.1,4.45,4.6,4.72,4.85,4.95,5.05,5.6,6.5,6.9,7.1,7.25,7.45,7.65,8.2').split(',').map(Number);
const src = AI15[KIT.outfit];
const f = buildSkinned(src, src); await Promise.all(__LOADS);
const props = brkProps(); await Promise.all(__LOADS);
const P = props.P;
P.door.visible = mode === 'bangdoor'; P.win.visible = mode !== 'bangdoor'; P.win.position.z = mode === 'bangwin' ? BRK.winZ : 0;
const nearP = view === 'side' || (typeof view === 'number' && Math.sin(view) > 0);   // the camera's on the +x side: cut that wall and jamb away
P.sides[1].visible = P.jambs[1].visible = P.doorSides[1].visible = !nearP; P.sides[0].visible = P.jambs[0].visible = nearP || view === 'front' || view === 'back';
const SC = +(Q.get('s') || 4), CW = +(Q.get('cw') || 280), RH = +(Q.get('rh') || 300), PER = +(Q.get('per') || 6);
const out = document.getElementById('out'); out.width = CW * Math.min(PER, ts.length); out.height = RH * Math.ceil(ts.length / PER);
const ox = out.getContext('2d'); ox.fillStyle = '#fff'; ox.fillRect(0, 0, out.width, out.height);
const cxOf = +(Q.get('cx') || (mode === 'climb' ? 0.62 : 0.5));
ts.forEach((tt, i) => {
  BRK.t0 = 0; zombieMove(f, mode, tt, { phase: 0, dt: 0.016 });
  const g = BRK.ev.glass; P.panes.forEach(p => p.visible = mode !== 'climb' || g === 'whole' || g === 'cracked'); P.teeth.visible = mode === 'climb' && g === 'teeth';
  if (mode === 'bangdoor') { P.slab.position.z = BRK.door + 1.65 + 0.25 * BRK.ev.hit; }
  const c = document.createElement('canvas'); c.width = CW; c.height = RH; const x = c.getContext('2d'); x.fillStyle = '#f4f4f0'; x.fillRect(0, 0, CW, RH);
  const zb = new Float32Array(CW * RH).fill(-1e9);
  rasterFigure(props.g, x, CW * cxOf, view, SC, RH - 12, { zb });
  rasterFigure(f, x, CW * cxOf, view, SC, RH - 12, { zb });
  x.fillStyle = '#c00'; x.font = '13px sans-serif'; x.fillText(mode + ' ' + tt + (mode === 'climb' ? ' ' + g : ''), 5, 15);
  ox.drawImage(c, (i % PER) * CW, Math.floor(i / PER) * RH);
});
document.title = 'done';
