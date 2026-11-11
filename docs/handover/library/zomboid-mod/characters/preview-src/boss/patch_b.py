import re,sys
p='boss_b.js'; s=open(p).read()
def rep(a,b,n=1):
    global s
    c=s.count(a)
    assert c==n,(c,a[:60])
    s=s.replace(a,b)
# boss materials: green where the hunter is red, brighter
rep("const BS = 0.5, SZ0", """// THE BOSS (#75): the military's unfinished weapon. Green where the hunters are red, heavier armour, a glowing caged belly.
for (const k of ['redline', 'sinew', 'sinewD', 'core', 'maw']) { const c = MAT[k].c; MAT[k] = { ...MAT[k], c: [c[1] * 0.6, c[0], c[2] * 1.6] }; }
MAT.redline = { ...MAT.redline, glow: 0.22 }; MAT.core = { ...MAT.core, glow: 0.06 };
MAT.eye = { ...MAT.eye, c: [0.12, 1.0, 0.2] };
MAT.belly = { c: [0.05, 0.5, 0.1], metal: 0, rough: 0.25, pat: 3, glow: 0.5 };
const BS = 0.5, SZ0""")
# legs: thicker, two green seams, heavier shingles
rep("const x0 = thigh ? 0.14 : gearR * 0.7, x1 = tars ? Lb - 0.2 : Lb - 0.02, Ls = thigh ? 0.44 : 0.4, step = Ls * 0.58;",
    "const x0 = thigh ? 0.2 : gearR * 0.7, x1 = tars ? Lb - 0.2 : Lb - 0.02, Ls = thigh ? 0.5 : 0.48, step = Ls * 0.5;")
rep("const rows = Math.max(2, Math.round((x1 - x0 - Ls) / step) + 1), N = 4;", "const rows = Math.max(2, Math.round((x1 - x0 - Ls) / step) + 1), N = 5;")
rep("const L = Ls * (0.9 + 0.2 * hash(id + 3)), lift = 0.024 + 0.022 * hash(id + 5);", "const L = Ls * (0.9 + 0.2 * hash(id + 3)), lift = 0.04 + 0.03 * hash(id + 5);")
rep("for (const th of [thigh ? 2.1 : -2.1]) {", "for (const th of [2.1, -2.1]) {")
rep("rr = R(x) + 0.05 + 0.01 * Math.sin(k * 1.3); pts.push", "rr = R(x) + 0.075 + 0.01 * Math.sin(k * 1.3); pts.push")
rep("tube(m, pts, 0.0065, { seg: 6 });", "tube(m, pts, 0.011, { seg: 6 });")
# thigh: a smooth armour sleeve over the root that runs right into the hip socket, and a blocky knee housing
rep("  if (thigh) {\n    // a domed knee cap", """  if (thigh) {
    // the root: a plain armoured sleeve from inside the hip ball out over the first third, so the leg visibly comes out of
    // its socket (Josiah: the legs must meet the joints), ringed where it turns, a green slot down its top
    m.mat = tint(MAT.steel, 0.9, 2.4);
    tube(m, [[-0.12, 0, 0], [Lb * 0.18, 0, 0], [Lb * 0.36, 0, 0]], t => rA * (1.32 - 0.18 * t), { seg: 16, cap0: true });
    m.mat = MAT.dark; for (const x of [0.02, Lb * 0.2, Lb * 0.36]) torus(m, [x, 0, 0], [[0, 1, 0], [0, 0, 1], [1, 0, 0]], rA * 1.3, 0.02, 18, 6);
    m.mat = MAT.redline; tube(m, [[0.06, rA * 1.3, 0], [Lb * 0.34, rA * 1.18, 0]], 0.013, { seg: 6 });
    // the knee housing: a heavy squared block over the cog, which the lower leg turns under
    m.mat = tint(MAT.scale, 1.0, 2.6); box(m, [Lb + 0.02, gearR * 0.35, 0], AX, [0.36, 0.3, 0.3]);
    m.mat = MAT.dark; box(m, [Lb + 0.05, gearR * 0.35 + 0.16, 0], AX, [0.3, 0.05, 0.26]);
    // a domed knee cap""")
# hip sockets: bigger, glowing ring
rep("m.mat = MAT.redline; torus(m, add(hp, mul(ax, 0.045)), [t1, t2, ax], 0.118, 0.01, 24, 5);",
    "m.mat = MAT.redline; torus(m, add(hp, mul(ax, 0.045)), [t1, t2, ax], 0.135, 0.022, 24, 6); torus(m, add(hp, mul(ax, 0.07)), [t1, t2, ax], 0.185, 0.012, 24, 5);")
# crown: more and taller spines
rep("for (const sd of [-1, 1]) for (let k = 0; k < 7; k++) {", "for (const sd of [-1, 1]) for (let k = 0; k < 11; k++) {")
rep("L = 0.22 + 0.3 * hash(id + 2) * (1 - 0.4 * Math.abs(a) / 1.35);", "L = 0.32 + 0.42 * hash(id + 2) * (1 - 0.4 * Math.abs(a) / 1.35);")
rep("h = 0.07 + 0.62 * Math.pow(", "h = 0.1 + 0.85 * Math.pow(")
# the belly: a big glowing sac under the rear, caged by heavy black ribs
rep("  // ribs and hanging sinew under the belly, black cables along the flanks\n  m.mat = MAT.dark;\n  for (let k = 0; k < 6; k++) {\n    const s = 0.22 + k * 0.09, pts = [];\n    for (let q = 0; q <= 16; q++) pts.push(b7Off(2.6 + (TAU - 5.2) * q / 16, s, -0.01 + 0.015 * Math.sin(Math.PI * q / 16))[0]);\n    tube(m, pts, 0.018, { seg: 7 });\n  }",
"""  // the boss's belly: a glowing sac slung under the rear half, held in a cage of heavy ribs
  { const c0 = b7Pt(Math.PI, 0.42), top = b7Pt(0, 0.42), cy = (c0[1] + top[1]) / 2;
    m.mat = MAT.belly; ellipsoid(m, [0, c0[1] + 0.02, -0.18], AX, [0.42, 0.3, 0.62], 24, 16);
    m.mat = MAT.dark;
    for (let k = 0; k < 9; k++) {
      const z = -0.72 + k * 0.135, f = Math.sqrt(Math.max(0, 1 - ((z + 0.18) / 0.66) ** 2)), pts = [];
      for (let q = 0; q <= 16; q++) { const a = Math.PI * (0.08 + 0.84 * q / 16); pts.push([Math.cos(a) * (0.45 * f + 0.02), c0[1] + 0.02 - Math.sin(a) * (0.33 * f + 0.02), z]); }
      tube(m, pts, 0.022, { seg: 7 });
    }
    tube(m, [[0, c0[1] - 0.32, -0.8], [0, c0[1] - 0.34, -0.18], [0, c0[1] - 0.3, 0.42]], 0.03, { seg: 8 });
  }""")
open(p,'w').write(s)
