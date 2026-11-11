// Sight specs for the 49 Brita/Arsenal optics. Units: MOA (1 mil = 3.438 MOA), +y is DOWN, (0,0) is the aim point.
// fov = true field shown (degrees). win = window for 1x sights (MOA, w x h, shape). mode: 'emit' (only when lit),
// 'etched' (black, glows when lit), 'nv' green phosphor, 'thermal' white-hot.
// conf: 'checked' (looked up this session), 'known' (well-documented, from my notes), 'guess' (no spec found: please check).
const MIL = 3.438;
const RED = '#ff2a1a', AMBER = '#ffb020', GREEN = '#5dff6a';
const S = [];
const add = o => S.push(o);
// ---- shared reticle pieces ----
const ringDot = (ring, dot, w = 1.2) => [{ t: 'ring', r: ring / 2, w }, { t: 'dot', r: dot / 2 }];
const duplex = (fine, lw = 0.25, thick = 2.2) => [{ t: 'cross', gap: 0, lw, thick, from: fine }];
const tree = (rows, mil = MIL) => { const e = []; for (let r = 1; r <= rows; r++) for (let c = -Math.min(r, 6); c <= Math.min(r, 6); c++) if (c) e.push({ t: 'dot', r: 0.16, x: c * mil, y: r * mil, k: 'ink' }); return e };
const milHash = (n, step, long, numEvery, lw = 0.12) => { const e = []; for (let i = 1; i <= n; i++) { const L = i % long ? 0.6 : 1.4; for (const s of [-1, 1]) { e.push({ t: 'line', x1: s * i * step, y1: -L / 2, x2: s * i * step, y2: L / 2, lw, k: 'ink' }); if (s > 0 || i * step < 60) e.push({ t: 'line', x1: -L / 2, y1: s * i * step, x2: L / 2, y2: s * i * step, lw, k: 'ink' }) } if (numEvery && i % numEvery === 0) e.push({ t: 'text', x: i * step, y: -1.8, s: String(i * step / MIL | 0), size: 1.6, k: 'ink' }) } return e };
const pso = (big = 1) => {   // laid out from the PSO-1 reticle scheme (refs sights/Scope_4xPSO/01): units in mils
  const e = [], m = MIL * big, ink = 'ink';
  const chev = (y, w, h) => ({ t: 'poly', pts: [[-w / 2, y + h], [0, y], [w / 2, y + h], [w / 2 - 0.22 * m, y + h], [0, y + 0.3 * m], [-w / 2 + 0.22 * m, y + h]], k: 'lit' });
  [0, 1, 2].forEach(i => e.push(chev(i * 1.6 * m, 1.6 * m, 1.3 * m)));            // aim chevron (tip = aim) + 1100/1200/1300 m below... 3 in total as on the scheme
  e.push({ t: 'line', x1: 0, y1: 5.2 * m, x2: 0, y2: 16 * m, lw: 0.28 * big * 1.2, k: ink });   // vertical post below the chevrons
  // lateral scale: 1 mil ticks up, long ticks down at 5, step up at 10 then out
  e.push({ t: 'line', x1: -10 * m, y1: 0, x2: -1 * m, y2: 0, lw: 0.25, k: ink }, { t: 'line', x1: 1 * m, y1: 0, x2: 10 * m, y2: 0, lw: 0.25, k: ink });
  for (let i = 1; i <= 9; i++) for (const s of [-1, 1]) if (i !== 5) e.push({ t: 'line', x1: s * i * m, y1: 0, x2: s * i * m, y2: -0.45 * m, lw: 0.22, k: ink });
  for (const s of [-1, 1]) e.push({ t: 'line', x1: s * 5 * m, y1: -0.3 * m, x2: s * 5 * m, y2: 0.9 * m, lw: 0.25, k: ink },
    { t: 'line', x1: s * 10 * m, y1: 1.1 * m, x2: s * 10 * m, y2: -1.1 * m, lw: 0.3, k: ink }, { t: 'line', x1: s * 10 * m, y1: -1.1 * m, x2: s * 13.5 * m, y2: -1.1 * m, lw: 0.3, k: ink },
    { t: 'text', x: s * 10 * m, y: 2.6 * m, s: '10', size: 1.1 * m, k: ink });
  // rangefinder for a 1.7 m man: baseline bottom-left, curve rises toward the centre (2 = 200 m) and falls left (10 = 1000 m)
  const bx0 = -14 * m, bx1 = -3.5 * m, by = 13 * m, pts = [], X = d => bx1 - (d - 200) / 800 * (bx1 - bx0);
  for (let d = 200; d <= 1000; d += 25) pts.push([X(d), by - 1.7 / d * 1000 * m]);
  e.push({ t: 'line', x1: bx0, y1: by, x2: bx1, y2: by, lw: 0.25, k: ink }, { t: 'path', pts, lw: 0.3, k: ink });
  for (let i = 0; i <= 5; i++) e.push({ t: 'line', x1: bx0 + i * (bx1 - bx0) / 5, y1: by, x2: bx0 + i * (bx1 - bx0) / 5, y2: by - 0.4 * m, lw: 0.2, k: ink });
  [2, 4, 6, 8, 10].forEach(n => e.push({ t: 'text', x: X(n * 100), y: by - 1.7 / (n * 100) * 1000 * m - 0.7 * m, s: String(n), size: 1.0 * m, k: ink }));
  e.push({ t: 'text', x: bx1 + 0.8 * m, y: by - 0.6 * m, s: '1.7', size: 1.0 * m, k: ink });
  return e;
};
const acogChevron = () => {
  const e = [{ t: 'poly', pts: [[-2.6, 2.4], [0, 0], [2.6, 2.4], [2.0, 2.4], [0, 0.7], [-2.0, 2.4]], k: 'lit' }];
  e.push({ t: 'line', x1: 0, y1: 2.6, x2: 0, y2: 26, lw: 0.2, k: 'ink' });
  [[3, 5.5, 5], [4, 4.1, 9.5], [5, 3.3, 15], [6, 2.75, 21.5]].forEach(([n, w, y]) => { e.push({ t: 'line', x1: -w / 2, y1: y, x2: w / 2, y2: y, lw: 0.3, k: 'ink' }, { t: 'text', x: w / 2 + 1.8, y: y + 0.5, s: String(n), size: 1.5, k: 'ink' }) });
  return e;
};
const post = (from, tipW, baseW, len, k = 'ink') => {      // tapered post whose tip is the aim point
  const s = from === 'bottom' ? 1 : -1;
  return { t: 'poly', pts: [[-tipW / 2, 0], [tipW / 2, 0], [baseW / 2, s * len], [-baseW / 2, s * len]], k };
};
const pointed = (from, baseW, len, k = 'ink') => { const s = from === 'bottom' ? 1 : -1; return { t: 'poly', pts: [[0, 0], [baseW / 2, s * len * 0.12], [baseW / 2, s * len], [-baseW / 2, s * len], [-baseW / 2, s * len * 0.12]], k } };

// ---------------- iron / 1x ----------------
add({ id: 'GhostRing', name: 'Ghost Ring', kind: 'Irons', zoom: 1, fov: 24, mode: 'iron', conf: 'known',
  reticle: 'Ghost-ring rear aperture (thin ring, big hole, blurred because it is close to the eye) with a front post that has a tritium dot. You look through the ring and put the post top on the target.',
  els: [{ t: 'ghost', r: 260, w: 46 }, { t: 'frontpost', w: 14, h: 70, dot: 3.4 }] });
add({ id: 'Sight_Fiber_Shotgun', name: 'VictOptics Fiber Red Dot Sight', kind: 'Rib sight', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'checked', win: { w: 360, h: 220, shape: 'rib' },
  reticle: 'Fibre-optic circle with a centre dot, on a shotgun rib. No battery: daylight lights the fibre. The maker gives no MOA, so I drew a 40 MOA circle and a 3 MOA dot.',
  src: 'Reseller listing: "red circle with center dot", rib mount, no batteries.', els: ringDot(40, 3, 1.4) });
add({ id: 'Sight_MicroDot', name: 'Trijicon RMR-2', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 470, h: 330, shape: 'rmr' },
  reticle: 'Single 3.25 MOA red dot (RMR Type 2 RM01). Small window with the RMR\'s angled top corners.', els: [{ t: 'dot', r: 1.62 }] });
add({ id: 'Sight_RaceDot', name: 'C-More Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 560, h: 440, shape: 'round-rect' },
  reticle: 'Single 6 MOA red dot (C-More\'s standard dot; 2-16 MOA versions exist). Open window with a thin frame.', els: [{ t: 'dot', r: 3 }] });
add({ id: 'Sight_SRS', name: 'Trijicon SRS Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 640, h: 520, shape: 'round-rect' },
  reticle: 'Single 1.75 MOA red dot, big 38 mm window with a solar panel on top.', els: [{ t: 'dot', r: 0.88 }] });
add({ id: 'Sight_MRO', name: 'Trijicon MRO Reflex Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 560, h: 560, shape: 'round' },
  reticle: 'Single 2 MOA red dot. Round 25 mm window that flares out toward the target, so the view through it looks a touch magnified.', els: [{ t: 'dot', r: 1 }] });
add({ id: 'Sight_SRO', name: 'Trijicon SRO Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 520, h: 440, shape: 'round' },
  reticle: 'Single 2.5 MOA red dot; big round pistol window (1 and 5 MOA versions exist).', els: [{ t: 'dot', r: 1.25 }] });
add({ id: 'Sight_EOTech', name: 'EOTech 553 Sight', kind: 'Holographic', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 600, h: 440, shape: 'rect' },
  reticle: 'EOTech "65 MOA ring with 1 MOA dot" with short ticks at 12, 3, 6 and 9 o\'clock, holographic, so it stays the same size and position wherever your eye is. Rectangular window in a hood.', els: ringDot(65, 1, 1.4).concat([0, 90, 180, 270].map(a => ({ t: 'tick', a, r0: 32.5, r1: 40, lw: 1.2 }))) });
add({ id: 'Sight_XPS', name: 'Eotech HWS XPS2 Holographic Sight', kind: 'Holographic', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 560, h: 410, shape: 'rect' },
  reticle: 'XPS2-0: 68 MOA ring with a 1 MOA dot. Slightly smaller window than the 553.', els: ringDot(68, 1, 1.4).concat([0, 90, 180, 270].map(a => ({ t: 'tick', a, r0: 34, r1: 42, lw: 1.2 }))) });
add({ id: 'Sight_Aimpoint', name: 'Aimpoint Pro Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 540, h: 540, shape: 'tube' },
  reticle: 'Single 2 MOA red dot in a 30 mm tube, so you see the tube\'s round rim.', els: [{ t: 'dot', r: 1 }] });
add({ id: 'Sight_Aimpoint_Dot', name: 'Aimpoint Micro T-2 Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 420, h: 420, shape: 'tube' },
  reticle: 'Single 2 MOA red dot in a small round tube with flip caps.', els: [{ t: 'dot', r: 1 }] });
add({ id: 'Sight_ACRO', name: 'Aimpoint ACRO P-1 Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 400, h: 340, shape: 'round-rect' },
  reticle: 'Single 3.5 MOA red dot in a fully enclosed, squared-off pistol window.', els: [{ t: 'dot', r: 1.75 }] });
add({ id: 'Sight_Mepro_M21', name: 'Meprolight M21 Reflex Sight', kind: 'Reflex', zoom: 1, fov: 20, mode: 'etched-glow', col: AMBER, conf: 'checked', win: { w: 640, h: 600, shape: 'round' },
  reticle: 'No battery: fibre optic by day, tritium by night. Five factory reticles exist (4.3 and 5.5 MOA dot, triangle, 4.3 bullseye, open X). I drew the bullseye, the original US standard: a 4.3 MOA dot in a ring.',
  src: 'Athlon, Soldier Systems, CKS Tactical listings.', els: ringDot(30, 4.3, 1.6) });
add({ id: 'Sight_Mepro_MOR', name: 'Meprolight MOR Reflex Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 540, h: 480, shape: 'round' },
  reticle: 'Single 1.8 MOA red dot. The MOR also carries a red and an IR laser (not part of the sight picture).', els: [{ t: 'dot', r: 0.9 }] });
add({ id: 'Sight_UH1', name: 'Vortex UH-1 Holographic Sight', kind: 'Holographic', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 620, h: 460, shape: 'hex' },
  reticle: 'EBR-CQB: 65 MOA ring broken into four arcs (gaps at 12, 3, 6 and 9 o\'clock), a 1 MOA centre dot and a small drop dot near the bottom. Six-sided window.',
  els: [{ t: 'seg', r: 32.5, w: 1.4, gap: 14 }, { t: 'dot', r: 0.5 }, { t: 'dot', r: 0.8, y: 14 }] });
add({ id: 'Sight_Nikon', name: 'Nikon Monarch Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 540, h: 540, shape: 'tube' },
  reticle: 'Monarch VSD: the dot size can be changed (1-10 MOA). Drawn at 3 MOA in its 30 mm tube.', els: [{ t: 'dot', r: 1.5 }] });
add({ id: 'Sight_1P87', name: 'Valday 1P87 Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'guess', win: { w: 720, h: 420, shape: 'rect' },
  reticle: 'Wide rectangular window, several switchable reticles. I drew the dot in a circle. Please check: I didn\'t find a reticle spec.', els: ringDot(26, 2, 1.2) });
add({ id: 'Sight_1P63', name: 'Obzor 1P63 Red Dot Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'guess', win: { w: 520, h: 520, shape: 'rect' },
  reticle: 'Tall square window. Reticle drawn from memory as a dot over a chevron. Please check.',
  els: [{ t: 'dot', r: 1 }, { t: 'poly', pts: [[-5, 6], [0, 2], [5, 6], [4.2, 6], [0, 2.9], [-4.2, 6]], k: 'lit' }] });
add({ id: 'Sight_KOBRA', name: 'Kobra Optic', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'known', win: { w: 560, h: 420, shape: 'kobra' },
  reticle: 'EKP-1S-03 Kobra has four switchable reticles: dot, chevron with dot, T-post and circle with dot. I drew the chevron with a dot at its tip, the one most people picture.',
  els: [{ t: 'dot', r: 0.9 }, { t: 'poly', pts: [[-6, 6], [0, 1.4], [6, 6], [5.2, 6], [0, 2.3], [-5.2, 6]], k: 'lit' }] });
add({ id: 'Sight_OKP7_Reflex', name: 'OKP-7 Reflex Sight', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'guess', win: { w: 620, h: 360, shape: 'rect' },
  reticle: 'Low, wide window. Reticle drawn from memory as a red chevron with a dot above its tip. Please check.',
  els: [{ t: 'dot', r: 0.9 }, { t: 'poly', pts: [[-4, 5], [0, 1.6], [4, 5], [3.3, 5], [0, 2.5], [-3.3, 5]], k: 'lit' }] });
add({ id: 'Sight_Phantom_XL', name: 'Phantom XL RD310 Optic', kind: 'Red dot', zoom: 1, fov: 20, mode: 'emit', col: RED, conf: 'guess', win: { w: 760, h: 420, shape: 'rect' },
  reticle: 'Big "machine gun" window. No spec found; one owner says the aiming mark is large and closer to a reticle than a dot. Drawn as a 3 MOA dot in a 40 MOA ring with four ticks. Please check.',
  els: ringDot(40, 3, 1.4).concat([0, 1, 2, 3].map(i => ({ t: 'tick', a: i * 90, r0: 20, r1: 26, lw: 1.2 }))) });
add({ id: 'Sight_PKAS', name: 'PK-AS Dual Black Dot (2x)', kind: 'Collimator', zoom: 1, fov: 20, mode: 'etched-glow', col: RED, conf: 'checked', win: { w: 600, h: 600, shape: 'tube' },
  reticle: 'Etched black 1 MOA dot in an elliptical rangefinding ring, visible with no battery and red when lit. The real PK-AS is 1x, not the 2x in Arsenal\'s name, so I drew it at 1x. Say if you want 2x.',
  src: 'avtomats-in-action, russianoptics.net, steklovata review.', els: [{ t: 'ellipse', rx: 15, ry: 11, w: 0.8 }, { t: 'dot', r: 0.5 }] });
// ---------------- magnified ----------------
add({ id: 'Sight_3xEOTech', name: '3X Magnifier w/EOtech', kind: 'Holo + magnifier', zoom: 3, fov: 7, mode: 'emit', col: RED, conf: 'known',
  reticle: 'The EOTech ring and dot seen through a 3x magnifier. The ring covers 3x more of the target and the edges darken. Our current game lets you swing the magnifier aside with X.', els: ringDot(65, 1, 1.4) });
add({ id: 'Sight_Raven', name: 'Raven Ultra Compact Prism Scope (3x)', kind: 'Prism', zoom: 3, fov: 7.5, mode: 'etched-glow', col: RED, conf: 'guess',
  reticle: 'Monstrum Raven 3x32: etched reticle, lit red or green. The pattern isn\'t published, so I drew a typical circle-dot with three drop dots. Please check.',
  els: ringDot(20, 1.5, 0.6).concat([1, 2, 3].map(i => ({ t: 'dot', r: 0.7, y: 4 + i * 4, k: 'ink' }))).concat([{ t: 'line', x1: 0, y1: 10.5, x2: 0, y2: 40, lw: 0.4, k: 'ink' }]) });
add({ id: 'Sight_C79', name: 'Elcan C79 Optical Sight (3.4x28)', kind: 'Prism', zoom: 3.4, fov: 7.3, mode: 'etched-glow', col: AMBER, conf: 'guess',
  reticle: 'C79: tapered post rising to the aim point, tritium-lit tip, thin horizontal line, short drop bars on the post for 400/600/800 m. Drawn from memory, please check.',
  els: [post('bottom', 0.6, 5, 120), { t: 'line', x1: -110, y1: 0, x2: -6, y2: 0, lw: 0.4, k: 'ink' }, { t: 'line', x1: 6, y1: 0, x2: 110, y2: 0, lw: 0.4, k: 'ink' }, { t: 'dot', r: 0.8, y: 0.3, k: 'lit' }]
    .concat([[4, 6], [6, 11], [8, 18]].map(([n, y]) => ({ t: 'line', x1: -4, y1: y, x2: 4, y2: y, lw: 0.6, k: 'ink' }))).concat([[4, 6], [6, 11], [8, 18]].map(([n, y]) => ({ t: 'text', x: 7, y: y + 0.6, s: String(n), size: 2, k: 'ink' }))) });
add({ id: 'Sight_NO32', name: 'Enfield No 32 MK I Scope (3.5x27)', kind: 'Rifle scope', zoom: 3.5, fov: 8.5, mode: 'etched', conf: 'known',
  reticle: 'WW2 British sniper scope: pointed post up from the bottom to the aim point, with one thin horizontal crosswire.',
  els: [pointed('bottom', 3.4, 300), { t: 'line', x1: -300, y1: 0, x2: 300, y2: 0, lw: 0.35, k: 'ink' }] });
add({ id: 'Sight_PU', name: 'Mosin Nagant PU Scope (3.5x21)', kind: 'Rifle scope', zoom: 3.5, fov: 4.5, mode: 'etched', conf: 'known',
  reticle: 'Soviet PU "three-post": pointed centre post from the bottom; thick side posts come in from left and right, level with the tip.',
  els: [pointed('bottom', 3.2, 200), { t: 'poly', pts: [[-200, 0], [-10, 0], [-10, 2.6], [-200, 2.6]], k: 'ink' }, { t: 'poly', pts: [[10, 0], [200, 0], [200, 2.6], [10, 2.6]], k: 'ink' }] });
add({ id: 'Sight_4xACOG', name: 'Acog (4X32)', kind: 'Prism', zoom: 4, fov: 7, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: 'TA31 red chevron (fibre by day, tritium at night), tip = aim point. Below it a drop line with 300-600 m bars; each bar is the width of a man\'s shoulders (19 in) at that range, so it doubles as a rangefinder.',
  els: acogChevron() });
add({ id: 'Sight_4xACOG_ECO', name: 'Acog Eco (4X32)', kind: 'Prism', zoom: 4, fov: 7, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: 'Same 4x32 body and red chevron reticle as the ACOG above, on the ECOS mount. Say if you want it to have the red crosshair version instead, so the two differ.', els: acogChevron() });
add({ id: 'Sight_SUSAT', name: 'SUSAT Rifle Scope (4x25.5)', kind: 'Prism', zoom: 4, fov: 10, mode: 'etched-glow', col: AMBER, conf: 'known',
  reticle: 'L9A1 SUSAT: a single dagger-shaped post up from the bottom, its tip lit with tritium. No crosshair.', els: [pointed('bottom', 6, 300), { t: 'dot', r: 0.8, y: 1.2, k: 'lit' }] });
add({ id: 'Sight_L2', name: 'TRILUX L2A2 Rifle Scope (4x25.5)', kind: 'Prism', zoom: 4, fov: 10, mode: 'etched-glow', col: AMBER, conf: 'known',
  reticle: 'L2A2: an upside-down pointer hanging from the top, tip on the aim point, lit with tritium.', els: [pointed('top', 6, 300), { t: 'dot', r: 0.8, y: -1.2, k: 'lit' }] });
add({ id: 'Sight_Brownells', name: 'Brownells Retro Carry Handle Scope (4x21)', kind: 'Rifle scope', zoom: 4, fov: 6, mode: 'etched', conf: 'checked',
  reticle: 'Retro 4x21 carry-handle scope: fine duplex (thick outer posts, fine centre cross). Range is set with the BDC turret, not the reticle.', src: 'Brownells listings ("fine duplex", BDC turret).', els: duplex(40) });
add({ id: 'Sight_LUX', name: 'Hi-Lux Malcom M82G2 Sniper Scope (2.5x20)', kind: 'Rifle scope', zoom: 2.5, fov: 11, mode: 'etched', conf: 'checked',
  reticle: 'Copy of the WW2 Lyman Alaskan M82: flat-topped post up from the bottom plus a thin horizontal crosswire.', src: 'Hi-Lux, CMP and Creedmoor listings (crosswire and post).',
  els: [post('bottom', 1.4, 1.4, 400), { t: 'line', x1: -400, y1: 0, x2: 400, y2: 0, lw: 0.4, k: 'ink' }] });
add({ id: 'Sight_M8', name: 'Leupold M8 Rifle Scope (4x28)', kind: 'Rifle scope', zoom: 4, fov: 5.7, mode: 'etched', conf: 'known', reticle: 'Leupold Duplex.', els: duplex(36) });
add({ id: 'Sight_6xELCAN', name: 'Elcan SpecterDR Scope (1.5-6x32)', kind: 'Prism', zoom: 6, fov: 4, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: 'SpecterDR at 6x: crosshair with thick outer posts and an open centre, a lit red centre dot, and short drop bars below for 5.56 out to about 800 m. Flip a lever for 1.5x.',
  els: [{ t: 'cross', gap: 3, lw: 0.25, thick: 2, from: 22 }, { t: 'dot', r: 0.9, k: 'lit' }].concat([4, 8, 13, 19].map((y, i) => ({ t: 'line', x1: -(3.2 - i * 0.5), y1: y, x2: 3.2 - i * 0.5, y2: y, lw: 0.35, k: 'ink' }))) });
add({ id: 'Sight_6xHensoldt', name: 'Hensoldt ZF (6x42)', kind: 'Rifle scope', zoom: 6, fov: 4, mode: 'etched', conf: 'guess',
  reticle: 'No spec found for this exact scope. Hensoldt\'s military ZFs use mil graded reticles, so I drew a mil-dot crosshair with heavy outer posts. Please check.',
  els: [{ t: 'cross', gap: 0, lw: 0.25, thick: 2.2, from: 5 * MIL }].concat([1, 2, 3, 4].flatMap(i => [[i, 0], [-i, 0], [0, i], [0, -i]]).map(([a, b]) => ({ t: 'dot', r: 0.42, x: a * MIL, y: b * MIL, k: 'ink' }))) });
add({ id: 'Sight_Malcom', name: 'Hi-Lux Malcom M8 Sniper Scope (8x31)', kind: 'Rifle scope', zoom: 8, fov: 3, mode: 'etched', conf: 'checked',
  reticle: 'Long 1920s-style target scope: a plain fine crosshair.', src: 'Hi-Lux manual and product page ("fine crosswire").', els: [{ t: 'cross', gap: 0, lw: 0.15, thick: 0.15, from: 999 }] });
add({ id: 'Sight_VX3', name: 'Leupold VX-3 Rifle Scope (4.5-14x44)', kind: 'Rifle scope', zoom: 14, fov: 1.4, mode: 'etched', conf: 'known', reticle: 'Leupold Duplex, drawn at 14x.', els: duplex(10, 0.12, 0.8) });
add({ id: 'Scope_20x', name: 'Nikon M5 Rifle Scope (5-20x44)', kind: 'Rifle scope', zoom: 20, fov: 1.0, mode: 'etched', conf: 'checked',
  reticle: 'Nikon\'s M5 comes as 5-20x50 with the MK1-MOA reticle (there\'s no 5-20x44 M5). I drew MK1-MOA: fine crosshair with 2 MOA hash marks and floating dots for holds. Drawn at 20x.',
  src: 'Nikon M5 manual and listings.', els: [{ t: 'cross', gap: 0, lw: 0.08, thick: 0.6, from: 16 }].concat(milHash(7, 2, 5, 0, 0.06)) });
const p4 = () => { const m = MIL, e = [{ t: 'dot', r: 0.12, k: 'lit' }];
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, -1]]) e.push({ t: 'line', x1: dx * 5.2 * m, y1: dy * 5.2 * m, x2: dx * 60, y2: dy * 60, lw: 0.9, k: 'ink' });
  for (const [dx, dy, to] of [[1, 0, 5.2], [-1, 0, 5.2], [0, -1, 5.2], [0, 1, 9]]) { e.push({ t: 'line', x1: dx * 0.9 * m, y1: dy * 0.9 * m, x2: dx * to * m, y2: dy * to * m, lw: 0.07, k: 'ink' });
    for (let i = 1; i * 0.5 < to; i++) { const L = (i % 2 ? 0.22 : 0.45) * m, p = i * 0.5 * m; e.push(dx ? { t: 'line', x1: dx * p, y1: -L, x2: dx * p, y2: L, lw: 0.06, k: 'ink' } : { t: 'line', x1: -L, y1: dy * p, x2: L, y2: dy * p, lw: 0.06, k: 'ink' }) } }
  e.push({ t: 'line', x1: -1 * m, y1: 5 * m, x2: 1 * m, y2: 5 * m, lw: 0.07, k: 'ink' });           // long bar at 5 mil below
  e.push({ t: 'line', x1: -0.7 * m, y1: 0, x2: 0.7 * m, y2: 0, lw: 0.03, k: 'lit' }, { t: 'line', x1: 0, y1: -0.7 * m, x2: 0, y2: 0.7 * m, lw: 0.03, k: 'lit' }); // fine lit centre cross
  return e };
add({ id: 'Sight_G28_Scope', name: 'Schmidt & Bender PM II Rifle Scope (3-20x50)', kind: 'Rifle scope', zoom: 20, fov: 1.0, mode: 'etched-glow', col: RED, conf: 'checked',
  reticle: 'G28 version of the PM II, a Bundeswehr reticle based on S&B\'s P4. Heavy posts top, left and right from 5 mils out. A fine cross with marks every half mil, a longer bar 5 mils below, and a small lit cross in the centre.', src: 'optics-world.nl G28 OEM listing; P4 reticle drawing (refs Sight_G28_Scope/02).',
  els: p4() });
const atacr = () => [{ t: 'cross', gap: 0.6, lw: 0.05, thick: 0.05, from: 999 }, { t: 'dot', r: 0.12, k: 'lit' }].concat(milHash(25, 0.2 * MIL, 5, 0, 0.04)).concat(tree(5));
add({ id: 'Scope_ATACR7_F1', name: 'ATACR 7 F1 Scope (35x56)', kind: 'Rifle scope', zoom: 35, fov: 0.6, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: 'Nightforce ATACR 7-35x56 F1, Mil-XT: very fine cross with 0.2 mil marks, a floating lit centre dot, and a holdover "tree" of dots below. First focal plane, so the marks are right at any zoom.', els: atacr() });
add({ id: 'Scope_ATACR7_F1r', name: 'ATACR 7 F1 Scope (35x56) w/rail', kind: 'Rifle scope', zoom: 35, fov: 0.6, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: 'Same scope and Mil-XT reticle as above; this one has a rail on top for a mini red dot.', els: atacr() });
// ---------------- night / thermal ----------------
add({ id: 'Sight_Thermal', name: 'AN/PAS-13C Thermal Sight', kind: 'Thermal', zoom: 3, fov: 6, mode: 'thermal', conf: 'known',
  reticle: 'US Army thermal weapon sight: white-hot image (warm = white). Crosshair with an open centre, an aim dot and range ticks, drawn in the display.',
  els: [{ t: 'cross', gap: 2, lw: 0.35, thick: 0.35, from: 999 }, { t: 'dot', r: 0.5 }].concat([6, 12, 20].map(y => ({ t: 'line', x1: -1.5, y1: y, x2: 1.5, y2: y, lw: 0.35 }))) });
add({ id: 'Sight_Thermal_AK', name: '1PN58 Night Vision Scope', kind: 'Night vision', zoom: 3, fov: 13, mode: 'nv', conf: 'known',
  reticle: 'Soviet image-intensifier sight for the AK: green, grainy view with a PSO-style chevron reticle and a rangefinder curve.', els: pso(1.6) });
add({ id: 'Sight_PVS4', name: 'AN/PVS-4 Night Vision Scope', kind: 'Night vision', zoom: 3.6, fov: 14, mode: 'nv', conf: 'checked',
  reticle: 'Vietnam/Cold War US starlight scope: green, grainy view with a thin full-width crosshair that glows brighter than the image.', src: 'Through-the-scope photo (refs Sight_PVS4/02).',
  els: [{ t: 'cross', gap: 0, lw: 0.6, thick: 0.6, from: 999, k: 'glow' }] });
add({ id: 'Sight_Armasight_NV', name: 'Armasight Apollo Pro LR640 Thermal Scope', kind: 'Thermal', zoom: 3, fov: 6, mode: 'thermal', conf: 'known',
  reticle: 'Real Apollo Pro is a clip-on that sits in front of a day scope and has no reticle of its own. For a stand-alone sight I gave it a simple fine crosshair over a white-hot image. Please check.',
  els: [{ t: 'cross', gap: 0, lw: 0.2, thick: 0.2, from: 999 }] });
add({ id: 'Sight_Burris_NV', name: 'Burris Thermal Rifle Scope', kind: 'Thermal', zoom: 3, fov: 6, mode: 'thermal', conf: 'checked',
  reticle: 'Burris BTS: thermal scope with 10 selectable reticles. Drawn with a crosshair, open centre and dot.', src: 'Burris BTS listings.',
  els: [{ t: 'cross', gap: 3, lw: 0.4, thick: 0.4, from: 999 }, { t: 'dot', r: 0.6 }] });
add({ id: 'Scope_4xPSO', name: 'PSO-1 Scope (4x24)', kind: 'Prism', zoom: 4, fov: 6, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: 'SVD/Dragunov PSO-1: aim chevron at the centre and three smaller chevrons below for 1100-1300 m. A mil scale runs left and right, and a curve bottom-left reads range off a man\'s height (2-10 = 200-1000 m). The reticle lights red.', els: pso(1) });
add({ id: 'Scope_12xPSO', name: '1P21 Rifle Scope (4-12x42)', kind: 'Prism', zoom: 12, fov: 2, mode: 'etched-glow', col: RED, conf: 'known',
  reticle: '1P21 "Minuta" variable SVD scope: PSO-style chevrons, mil scale and rangefinder, drawn at 12x.', els: pso(0.5) });
if (typeof module !== 'undefined') module.exports = S;
