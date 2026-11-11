s=open('skinned.js').read()
def r(a,b):
    global s
    assert s.count(a)==1,a; s=s.replace(a,b)
r("""    // thin dark outline on the outer edges only
""","""    // worn and filthy: a brown-grey wash, faded and dark patches, mud toward the bottom, old stains
    const W8 = KIT.wear ?? 0.6, metal = !!(o.shine || o.clean), fab = !metal && base.length === 7, area = W * H;
    c.fillStyle = `rgba(58,48,32,${0.10 + 0.14 * W8})`; c.fillRect(0, 0, W, H);
    for (let i = 0; i < area * W8 / 500; i++) blob(c, rnd() * W, rnd() * H, 3 + rnd() * 8, rnd() < 0.5 ? `rgba(232,222,196,${0.04 + rnd() * 0.08})` : `rgba(40,32,20,${0.05 + rnd() * 0.12})`, 6);
    for (let i = 0; i < area * W8 / 1400; i++) blob(c, rnd() * W, H * (0.4 + rnd() * 0.6), 5 + rnd() * 10, `rgba(70,52,30,${0.2 + rnd() * 0.25})`, 8);
    if (fab) for (let i = 0; i < area * W8 / 2500; i++) blob(c, rnd() * W, rnd() * H, 4 + rnd() * 8, 'rgba(95,82,40,.16)', 9);
    if (metal) {                                                                              // scratches, chips and rust
      c.fillStyle = 'rgba(255,255,255,.22)'; for (let i = 0; i < 3 + 8 * W8; i++) c.fillRect(rnd() * W, rnd() * H, 2 + rnd() * 8, 1);
      for (let i = 0; i < 1 + area * W8 / 900; i++) blob(c, rnd() * W, rnd() * H, 2 + rnd() * 4, rnd() < 0.5 ? 'rgba(115,62,24,.4)' : 'rgba(10,10,10,.35)', 5);
    }
    // thin dark outline on the outer edges only
""")
r("""    if (KIT.blood && !o.clean) blood(c, W, H, KIT.blood * (o.bloodK ?? 1), true);
  };""","""    // blood: fresh splashes and drips, older brown patches, fine spray, the odd hand smear
    const amt = (KIT.blood + 0.12) * (o.bloodK ?? 1) * (o.clean ? 0.35 : 1);
    blood(c, W, H, amt, true);
    for (let i = 0; i < area * amt / 1200; i++) blob(c, rnd() * W, rnd() * H, 4 + rnd() * 10, 'rgba(70,30,15,.55)', 8);
    c.fillStyle = 'rgba(110,14,10,.85)'; for (let i = 0; i < area * amt / 150; i++) c.fillRect(rnd() * W, rnd() * H, rnd() < 0.3 ? 2 : 1, rnd() < 0.3 ? 2 : 1);
    if (sideF && rnd() < amt * 0.5) { const x = rnd() * W * 0.7, y = rnd() * H * 0.7; c.fillStyle = 'rgba(85,14,10,.6)'; for (let k = 0; k < 4; k++) c.fillRect(x + k * 3, y + k * 0.5, W * 0.3, 2); }
    // torn fabric: frayed holes (the body shows through), claw rakes and a ragged bottom edge
    if (fab && sideF && W >= 16 && H >= 16 && !o.noTear) {
      const nh = Math.round(area * W8 / 3000 * (0.5 + rnd()));
      for (let i = 0; i < nh; i++) {
        const x = 4 + rnd() * (W - 8), y = 6 + rnd() * (H - 12), rr = 2 + rnd() * 4 * W8 + 1;
        blob(c, x, y, rr + 3, 'rgba(232,220,190,.35)', 9);
        if (rnd() < 0.6) blob(c, x, y + rr, rr + 2, 'rgba(90,12,8,.7)', 6);
        c.globalCompositeOperation = 'destination-out'; blob(c, x, y, rr, '#000', 9); c.globalCompositeOperation = 'source-over';
        c.fillStyle = base; for (let k = 0; k < 2; k++) c.fillRect(x - rr, y - rr / 2 + rnd() * rr, rr * 2, 1);
      }
      if (rnd() < W8 * 0.35 && H > 24) {
        const x0 = rnd() * W * 0.6, y0 = rnd() * H * 0.4, L = H * (0.3 + rnd() * 0.3), sl = (rnd() - 0.5) * 0.8;
        for (let k = 0; k < 3; k++) for (let t = 0; t < L; t++) {
          const x = x0 + k * 4 + t * sl, y = y0 + t;
          c.fillStyle = 'rgba(100,14,10,.8)'; c.fillRect(x - 1, y, 4, 1);
          c.globalCompositeOperation = 'destination-out'; c.fillRect(x, y, 2, 1); c.globalCompositeOperation = 'source-over';
        }
      }
      if (!jb && H > 12) {
        c.globalCompositeOperation = 'destination-out';
        for (let x = 0; x < W; x += 2) if (rnd() < 0.5 * W8) { const d = 1 + rnd() * 4 * W8; c.fillRect(x, H - d, 2, d); }
        c.globalCompositeOperation = 'source-over';
      }
    }
  };""")
r("  const g = (w, h, d, p) => box(w, h, d, p);","  const g = (w, h, d, p) => box(w, h, d, p, true);")
open('skinned.js','w').write(s)

k=open('kits.js').read()
import re
k=re.sub(r"\n    hair\(K, [^\n]*\);","",k)
assert "    hair(K" not in k
def rk(a,b):
    global k
    assert k.count(a)==1,a; k=k.replace(a,b)
rk("put, g: (w, h, d, p, a) => box(w, h, d, p, a),","put, g: (w, h, d, p) => box(w, h, d, p, true),")
rk("const rib = c => gear(c, { knit: true, grime: .2 });","""const rib = c => gear(c, { knit: true, grime: .2 });
// loose strips of torn fabric hanging from a hem or cuff at height y
function tatters(K, parent, p, y, half, n) {
  for (let i = 0; i < n; i++) {
    const s = (rnd() * 4) | 0, a = (rnd() - 0.5) * half * 1.4, L = 0.8 + rnd() * 1.8 * (KIT.wear ?? 0.6), wd = 0.5 + rnd() * 0.6;
    const [x, z] = [[a, half], [a, -half], [half, a], [-half, a]][s];
    K.put(parent, K.g(s < 2 ? wd : 0.15, L, s < 2 ? 0.15 : wd, jn(p, 't')), x, y - L / 2 + 0.1, z, (rnd() - 0.5) * 0.5, 0, (rnd() - 0.5) * 0.5);
  }
}
const nTat = () => Math.floor(rnd() * 3.5 * (KIT.wear ?? 0.6));""")
rk("""    if (o.sleeve === 'short') { put(sh, g(U[0] + 0.6 + e, 0.7, U[0] + 0.6 + e, cuff), side * 0.05, S.shoulder + 0.1 - len + 0.3, 0); continue; }
    const fl = o.sleeve === 'rolled' ? F[1] * 0.4 : F[1] - 0.2;""","""    if (o.sleeve === 'short') {
      put(sh, g(U[0] + 0.6 + e, 0.7, U[0] + 0.6 + e, cuff), side * 0.05, S.shoulder + 0.1 - len + 0.3, 0);
      tatters(K, sh, p, S.shoulder + 0.1 - len, (U[0] + 0.6 + e) / 2, nTat()); continue;
    }
    if (o.sleeve === 'long' && R === 'R' && (KIT.wear ?? 0.6) > 0.6 && rnd() < 0.6) {          // forearm sleeve ripped away at the elbow
      tatters(K, sh, p, S.shoulder + 0.1 - len, (U[0] + 0.35 + e) / 2, 3); continue;
    }
    const fl = o.sleeve === 'rolled' ? F[1] * 0.4 : F[1] - 0.2;""")
rk("""    put(el, g(F[0] + 0.45 + e, o.sleeve === 'rolled' ? 1.0 : 0.8, F[0] + 0.45 + e, cuff), 0, 0.2 - fl + 0.3, 0);""","""    put(el, g(F[0] + 0.45 + e, o.sleeve === 'rolled' ? 1.0 : 0.8, F[0] + 0.45 + e, cuff), 0, 0.2 - fl + 0.3, 0);
    tatters(K, el, p, 0.2 - fl - 0.1, (F[0] + 0.45 + e) / 2, nTat());""")
rk("""      put(hip, g(T[0] + e + 0.45, 0.6, T[0] + e + 0.45, hem), 0, 0.4 - L - 0.1, 0);""","""      put(hip, g(T[0] + e + 0.45, 0.6, T[0] + e + 0.45, hem), 0, 0.4 - L - 0.1, 0);
      tatters(K, hip, p, 0.4 - L - 0.4, (T[0] + e + 0.45) / 2, nTat());""")
rk("""    put(kn, g(N[0] + 0.35 + e, 0.7, N[0] + 0.35 + e, hem), 0, bot + 0.3, 0);""","""    put(kn, g(N[0] + 0.35 + e, 0.7, N[0] + 0.35 + e, hem), 0, bot + 0.3, 0);
    tatters(K, kn, p, bot - 0.05, (N[0] + 0.35 + e) / 2, nTat());""")
open('kits.js','w').write(k)

b=open('build_all.py').read()
b=b.replace("const BLOOD = { olive_1: 0.3, dark_0: 0.05, pale_3: 0.9 };","const BLOOD = { olive_1: 0.3, dark_0: 0.05, pale_3: 0.9 }, WEAR = { olive_1: 0.7, dark_0: 0.4, pale_3: 1.0 };")
b=b.replace("KIT.blood = BLOOD[skinKey];","KIT.blood = BLOOD[skinKey]; KIT.wear = WEAR[skinKey];")
b=b.replace("each wearing its own kit in the soldier style","each wearing its own kit in the soldier style, filthy, torn and bloodied")
b=b.replace("change the damage level to see the blood build up","change the damage level to see the dirt, tears, rips and blood build up")
open('build_all.py','w').write(b)
print('ok')
