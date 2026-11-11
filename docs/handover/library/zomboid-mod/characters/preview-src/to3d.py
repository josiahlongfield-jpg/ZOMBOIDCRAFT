import re
k=open('kits.js').read()
def rk(a,b):
    global k
    assert k.count(a)==1,a; k=k.replace(a,b)
rk("put(J.spine, g(tw + e, th * 0.6 + 0.2, td + e, jn(p, 'b')), 0, th * 0.7, 0);","shell(J.spine, tw + e, th * 0.6 + 0.2, td + e, p, 0, th * 0.7, 0, 'b');")
rk("put(J.spine, g(tw - 1 + e + (o.loose || 0), th * 0.45, td - 0.4 + e + (o.loose || 0), jn(p, o.hem && !o.hemP ? 'tb' : 't')), 0, th * 0.22, 0);","shell(J.spine, tw - 1 + e + (o.loose || 0), th * 0.45, td - 0.4 + e + (o.loose || 0), p, 0, th * 0.22, 0, o.hem && !o.hemP ? 'tb' : 't');")
rk("if (o.hem) put(J.hips, g(P[0] + e + 0.15, o.hem, P[2] + e + 0.15, o.hemP || jn(p, 't')), 0, P[1] / 2 - o.hem / 2 + 0.2, 0);","if (o.hem) { if (o.hemP) put(J.hips, g(P[0] + e + 0.15, o.hem, P[2] + e + 0.15, o.hemP), 0, P[1] / 2 - o.hem / 2 + 0.2, 0); else shell(J.hips, P[0] + e + 0.15, o.hem, P[2] + e + 0.15, p, 0, P[1] / 2 - o.hem / 2 + 0.2, 0, 't'); }")
rk("put(sh, g(U[0] + 0.35 + e, len, U[0] + 0.35 + e, o.sleeve === 'short' ? p : jn(p, 'b')), side * 0.05, S.shoulder + 0.1 - len / 2, 0);","shell(sh, U[0] + 0.35 + e, len, U[0] + 0.35 + e, p, side * 0.05, S.shoulder + 0.1 - len / 2, 0, o.sleeve === 'short' ? '' : 'b');")
rk("put(el, g(F[0] + 0.2 + e, fl, F[0] + 0.2 + e, jn(p, 't')), 0, 0.2 - fl / 2, 0);","shell(el, F[0] + 0.2 + e, fl, F[0] + 0.2 + e, p, 0, 0.2 - fl / 2, 0, 't');")
rk("put(J.hips, g(P[0] + e, P[1] + 0.2, P[2] + e, jn(p, 'b')), 0, 0, 0);","shell(J.hips, P[0] + e, P[1] + 0.2, P[2] + e, p, 0, 0, 0, 'b');")
rk("put(hip, g(T[0] + e + 0.25, L + 0.4, T[0] + e + 0.25, jn(p, 't')), 0, 0.4 - (L + 0.4) / 2, 0);","shell(hip, T[0] + e + 0.25, L + 0.4, T[0] + e + 0.25, p, 0, 0.4 - (L + 0.4) / 2, 0, 't');")
rk("put(hip, g(T[0] + 0.05 + e, T[1] + 0.4, T[0] + 0.05 + e, jn(p, 'tb')), 0, 0.4 - (T[1] + 0.4) / 2, 0);","shell(hip, T[0] + 0.05 + e, T[1] + 0.4, T[0] + 0.05 + e, p, 0, 0.4 - (T[1] + 0.4) / 2, 0, 'tb');")
rk("put(kn, g(N[0] + 0.05 + e, top - bot, N[0] + 0.05 + e, jn(p, 't')), 0, (top + bot) / 2, 0);","shell(kn, N[0] + 0.05 + e, top - bot, N[0] + 0.05 + e, p, 0, (top + bot) / 2, 0, 't');")
rk("put(J.spine, g(tw + 1.0, th * 0.62, td + 1.0, jn(VEST, 'b')), 0, th * 0.68, 0);","shell(J.spine, tw + 1.0, th * 0.62, td + 1.0, VEST, 0, th * 0.68, 0, 'b');")
rk("put(J.spine, g(tw - 0.2, th * 0.4, td + 0.6, jn(VEST, 't')), 0, th * 0.24, 0);","shell(J.spine, tw - 0.2, th * 0.4, td + 0.6, VEST, 0, th * 0.24, 0, 't');")
rk("put(J.hips, g(P[0] + 1.3, P[1] + 0.4, P[2] + 1.3, jn(COAT, 'tb')), 0, 0, 0);","shell(J.hips, P[0] + 1.3, P[1] + 0.4, P[2] + 1.3, COAT, 0, 0, 0, 'tb');")
rk("put(J['hip' + R], g(T[0] + 1.3, 4.2, T[0] + 1.3, jn(COAT, 't')), side * 0.15, 0.6 - 2.1, 0);","shell(J['hip' + R], T[0] + 1.3, 4.2, T[0] + 1.3, COAT, side * 0.15, 0.6 - 2.1, 0, 't');")
rk("put(J['hip' + R], g(T[0] + 1.5, 3.4, T[0] + 1.5, jn(TAN, 't')), side * 0.15, 0.6 - 1.7, 0);","shell(J['hip' + R], T[0] + 1.5, 3.4, T[0] + 1.5, TAN, side * 0.15, 0.6 - 1.7, 0, 't');")
rk("put(J.hips, g(P[0] + 1.6, P[1] + 0.5, P[2] + 1.6, jn(TAN, 'tb')), 0, 0, 0);","shell(J.hips, P[0] + 1.6, P[1] + 0.5, P[2] + 1.6, TAN, 0, 0, 0, 'tb');")
rk("put(J.hips, g(P[0] + 1.0, P[1] + 0.4, P[2] + 1.0, jn(GOWN, 'tb')), 0, 0, 0);","shell(J.hips, P[0] + 1.0, P[1] + 0.4, P[2] + 1.0, GOWN, 0, 0, 0, 'tb');")
rk("put(J['hip' + R], g(T[0] + 1.0, T[1] * 0.78, T[0] + 1.0, jn(GOWN, 't')), side * 0.1, 0.6 - T[1] * 0.39, 0);","shell(J['hip' + R], T[0] + 1.0, T[1] * 0.78, T[0] + 1.0, GOWN, side * 0.1, 0.6 - T[1] * 0.39, 0, 't');")
# mud on footwear, tilted headgear
rk("    put(an, g(FT[0] + 0.6, 0.6, FT[2] + 0.7, gear(sole, { grime: .45, noise: .05 })), 0, -1.1, fz);","    put(an, g(FT[0] + 0.6, 0.6, FT[2] + 0.7, gear(sole, { grime: .45, noise: .05 })), 0, -1.1, fz);\n    mud(an, FT[0] + 0.75, FT[2] + 0.8, -0.9, fz, Math.round(2 + 5 * (KIT.wear ?? 0.6)));")
rk("    put(an, g(FT[0] + 0.8, 0.75, FT[2] + 0.8, gear(o.sole || '#141210', { noise: .05, grime: .1 })), 0, -1.03, fz);","    put(an, g(FT[0] + 0.8, 0.75, FT[2] + 0.8, gear(o.sole || '#141210', { noise: .05, grime: .1 })), 0, -1.03, fz);\n    mud(an, FT[0] + 0.95, FT[2] + 0.95, -0.7, fz, Math.round(3 + 6 * (KIT.wear ?? 0.6)));\n    mud(kn, N[0] + 0.75, N[0] + 0.75, -N[1] + 0.6, 0, Math.round(1 + 3 * (KIT.wear ?? 0.6)));")
rk("""function kitCtx(J, S) {
  const [tw, th, td] = S.torso""","""function kitCtx(J0, S) {
  // headgear and face pieces go on a slightly knocked-askew head layer
  const tilt = new THREE.Group(); J0.head.add(tilt); const W8 = KIT.wear ?? 0.6;
  tilt.rotation.set((rnd() - 0.5) * 0.12 * W8, (rnd() - 0.5) * 0.1 * W8, (rnd() - 0.5) * 0.16 * W8);
  const J = { ...J0, head: tilt };
  const [tw, th, td] = S.torso""")
open('kits.js','w').write(k)

s=open('skinned.js').read()
def r2(a,b):
    global s
    assert s.count(a)==1,a; s=s.replace(a,b)
for old,new in [
 ("put(k.spine, g(tw + 0.4, th * 0.6 + 0.2, td + 0.4, gear('#556b34', { ...BDU, join: 'b' })), 0, th * 0.7, 0);","shell(k.spine, tw + 0.4, th * 0.6 + 0.2, td + 0.4, gear('#556b34', BDU), 0, th * 0.7, 0, 'b');"),
 ("put(k.spine, g(tw - 0.6, th * 0.45, td, gear('#556b34', { ...BDU, join: 't' })), 0, th * 0.22, 0);","shell(k.spine, tw - 0.6, th * 0.45, td, gear('#556b34', BDU), 0, th * 0.22, 0, 't');"),
 ("put(k.hips, g(S.pelvis[0] + 0.4, S.pelvis[1] + 0.2, S.pelvis[2] + 0.4, gear('#556b34', { ...BDU, join: 'b' })), 0, 0, 0);","shell(k.hips, S.pelvis[0] + 0.4, S.pelvis[1] + 0.2, S.pelvis[2] + 0.4, gear('#556b34', BDU), 0, 0, 0, 'b');"),
 ("put(sh, g(S.upper[0] + 0.75, S.upper[1] + 0.2, S.upper[0] + 0.75, gear('#556b34', { ...BDU, join: 'b' })), side * 0.05, S.shoulder - S.upper[1] / 2 + 0.1, 0);","shell(sh, S.upper[0] + 0.75, S.upper[1] + 0.2, S.upper[0] + 0.75, gear('#556b34', BDU), side * 0.05, S.shoulder - S.upper[1] / 2 + 0.1, 0, 'b');"),
 ("put(el, g(S.fore[0] + 0.6, S.fore[1] * 0.4, S.fore[0] + 0.6, gear('#556b34', { ...BDU, join: 't' })), 0, 0.2 - S.fore[1] * 0.2, 0);","shell(el, S.fore[0] + 0.6, S.fore[1] * 0.4, S.fore[0] + 0.6, gear('#556b34', BDU), 0, 0.2 - S.fore[1] * 0.2, 0, 't');"),
 ("put(hip, g(S.thigh[0] + 0.45, S.thigh[1] + 0.4, S.thigh[0] + 0.45, gear('#556b34', { ...BDU, join: 'tb' })), 0, 0.4 - (S.thigh[1] + 0.4) / 2, 0);","shell(hip, S.thigh[0] + 0.45, S.thigh[1] + 0.4, S.thigh[0] + 0.45, gear('#556b34', BDU), 0, 0.4 - (S.thigh[1] + 0.4) / 2, 0, 'tb');"),
 ("put(kn, g(S.shin[0] + 0.45, shinTop - shinBot, S.shin[0] + 0.45, gear('#556b34', { ...BDU, join: 't' })), 0, (shinTop + shinBot) / 2, 0);","shell(kn, S.shin[0] + 0.45, shinTop - shinBot, S.shin[0] + 0.45, gear('#556b34', BDU), 0, (shinTop + shinBot) / 2, 0, 't');"),
]: r2(old,new)
# gear: fade the colour itself, no painted dirt/blood/tears overlays unless KIT.paintFx
r2("""    const jt = (o.join || '').includes('t'), jb = (o.join || '').includes('b'), sideF = f !== 'py' && f !== 'ny';
    if (o.plaid) plaid(c, W, H, base, o.plaid[0], o.plaid[1]); else { c.fillStyle = base; c.fillRect(0, 0, W, H); }""",
"""    const jt = (o.join || '').includes('t'), jb = (o.join || '').includes('b'), sideF = f !== 'py' && f !== 'ny';
    // the colour itself is worn: sun-faded and dulled toward a dusty grey-brown
    const Wf = KIT.wear ?? 0.6, B = o.noFade || base.length !== 7 || KIT.paintFx ? base : mixHex(base, '#8f8670', (o.shine || o.clean) ? 0.06 + 0.08 * Wf : 0.1 + 0.22 * Wf);
    if (o.plaid) plaid(c, W, H, B, mixHex(o.plaid[0], '#8f8670', 0.15 * Wf), mixHex(o.plaid[1], '#8f8670', 0.2 * Wf)); else { c.fillStyle = B; c.fillRect(0, 0, W, H); }""")
r2("""    const W8 = KIT.wear ?? 0.6, metal = !!(o.shine || o.clean), fab = !metal && base.length === 7, area = W * H;
    c.fillStyle""","""    const W8 = KIT.wear ?? 0.6, metal = !!(o.shine || o.clean), fab = !metal && base.length === 7, area = W * H;
    if (KIT.paintFx) {
    c.fillStyle""")
r2("""    // thin dark outline on the outer edges only
    c.fillStyle = `rgba(0,0,0,${o.outline ?? 0.6})`;
    if (sideF) {""","""    }
    // thin dark outline on the outer edges only
    c.fillStyle = `rgba(0,0,0,${o.outline ?? 0.6})`;
    if (o.noOutline) {} else if (sideF) {""")
r2("""    // blood: fresh splashes and drips""","""    if (!KIT.paintFx) return;
    // blood: fresh splashes and drips""")
r2("""  paint.with = extra => gear(base, { ...o, ...extra });""","""  paint.with = extra => gear(base, { ...o, ...extra }); paint.base = base; paint.o = o;""")
r2("const KIT = { blood: 0.3, on: true, outfit: 'soldier' };","const KIT = { blood: 0.3, on: true, outfit: 'soldier', wear: 0.6, paintFx: false };")
open('skinned.js','w').write(s)

b=open('build_all.py').read()
b=b.replace("open('skinned.js').read()+open('kits.js').read()","open('skinned.js').read()+open('kits.js').read()+open('damage.js').read()")
b=b.replace("filthy, torn and bloodied","worn out and torn, with the damage built from real 3D pieces")
b=b.replace("change the damage level to see the dirt, tears, rips and blood build up","change the damage level to see holes, peeled flaps, frayed threads, faded and stained panels, raised blood and caked mud build up")
open('build_all.py','w').write(b)
print('ok')
