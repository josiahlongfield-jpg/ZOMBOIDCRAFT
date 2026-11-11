k=open('kits.js').read()
def rep(a,b):
    global k
    assert k.count(a)==1,(a,k.count(a)); k=k.replace(a,b)
rep("""  put(J.spine, g(tw + e, th * 0.6 + 0.2, td + e, p), 0, th * 0.7, 0);
  put(J.spine, g(tw - 1 + e + (o.loose || 0), th * 0.45, td - 0.4 + e + (o.loose || 0), p), 0, th * 0.22, 0);
  if (o.hem) put(J.hips, g(P[0] + e + 0.15, o.hem, P[2] + e + 0.15, o.hemP || p), 0, P[1] / 2 - o.hem / 2 + 0.2, 0);""",
"""  put(J.spine, g(tw + e, th * 0.6 + 0.2, td + e, jn(p, 'b')), 0, th * 0.7, 0);
  put(J.spine, g(tw - 1 + e + (o.loose || 0), th * 0.45, td - 0.4 + e + (o.loose || 0), jn(p, o.hem && !o.hemP ? 'tb' : 't')), 0, th * 0.22, 0);
  if (o.hem) put(J.hips, g(P[0] + e + 0.15, o.hem, P[2] + e + 0.15, o.hemP || jn(p, 't')), 0, P[1] / 2 - o.hem / 2 + 0.2, 0);""")
rep("""    put(sh, g(U[0] + 0.35 + e, 2.1, U[0] + 0.35 + e, p), side * 0.05, S.shoulder - 1.0, 0);
    const len = o.sleeve === 'short' ? 3.0 : U[1] + 0.2;
    put(sh, g(U[0] + 0.05 + e, len, U[0] + 0.05 + e, p), 0, S.shoulder + 0.1 - len / 2, 0);""",
"""    // one sleeve from the shoulder to the elbow, flowing into the forearm below
    const len = o.sleeve === 'short' ? 3.0 : U[1] + 0.2;
    put(sh, g(U[0] + 0.35 + e, len, U[0] + 0.35 + e, o.sleeve === 'short' ? p : jn(p, 'b')), side * 0.05, S.shoulder + 0.1 - len / 2, 0);""")
rep("""put(sh, g(U[0] + 0.35 + e, 0.7, U[0] + 0.35 + e, cuff), 0, S.shoulder + 0.1 - len + 0.3, 0); continue; }""",
"""put(sh, g(U[0] + 0.6 + e, 0.7, U[0] + 0.6 + e, cuff), side * 0.05, S.shoulder + 0.1 - len + 0.3, 0); continue; }""")
rep("""    put(el, g(F[0] + e, fl, F[0] + e, p), 0, 0.2 - fl / 2, 0);""","""    put(el, g(F[0] + 0.2 + e, fl, F[0] + 0.2 + e, jn(p, 't')), 0, 0.2 - fl / 2, 0);""")
rep("""  put(J.hips, g(P[0] + e, P[1] + 0.2, P[2] + e, p), 0, 0, 0);""","""  put(J.hips, g(P[0] + e, P[1] + 0.2, P[2] + e, jn(p, 'b')), 0, 0, 0);""")
rep("""      put(hip, g(T[0] + e + 0.25, L + 0.4, T[0] + e + 0.25, p), 0, 0.4 - (L + 0.4) / 2, 0);""","""      put(hip, g(T[0] + e + 0.25, L + 0.4, T[0] + e + 0.25, jn(p, 't')), 0, 0.4 - (L + 0.4) / 2, 0);""")
rep("""    put(hip, g(T[0] + 0.05 + e, T[1] + 0.4, T[0] + 0.05 + e, p), 0, 0.4 - (T[1] + 0.4) / 2, 0);""","""    put(hip, g(T[0] + 0.05 + e, T[1] + 0.4, T[0] + 0.05 + e, jn(p, 'tb')), 0, 0.4 - (T[1] + 0.4) / 2, 0);""")
rep("""    put(kn, g(N[0] + 0.05 + e, top - bot, N[0] + 0.05 + e, p), 0, (top + bot) / 2, 0);""","""    put(kn, g(N[0] + 0.05 + e, top - bot, N[0] + 0.05 + e, jn(p, 't')), 0, (top + bot) / 2, 0);""")
rep("put(J.hips, g(P[0] + 1.3, P[1] + 0.4, P[2] + 1.3, COAT), 0, 0, 0);","put(J.hips, g(P[0] + 1.3, P[1] + 0.4, P[2] + 1.3, jn(COAT, 'tb')), 0, 0, 0);")
rep("put(J['hip' + R], g(T[0] + 1.3, 4.2, T[0] + 1.3, COAT), side * 0.15, 0.6 - 2.1, 0);","put(J['hip' + R], g(T[0] + 1.3, 4.2, T[0] + 1.3, jn(COAT, 't')), side * 0.15, 0.6 - 2.1, 0);")
rep("put(J['hip' + R], g(T[0] + 1.5, 3.4, T[0] + 1.5, TAN), side * 0.15, 0.6 - 1.7, 0);","put(J['hip' + R], g(T[0] + 1.5, 3.4, T[0] + 1.5, jn(TAN, 't')), side * 0.15, 0.6 - 1.7, 0);")
rep("put(J.hips, g(P[0] + 1.6, P[1] + 0.5, P[2] + 1.6, TAN), 0, 0, 0);","put(J.hips, g(P[0] + 1.6, P[1] + 0.5, P[2] + 1.6, jn(TAN, 'tb')), 0, 0, 0);")
rep("put(J.hips, g(P[0] + 1.0, P[1] + 0.4, P[2] + 1.0, GOWN), 0, 0, 0);","put(J.hips, g(P[0] + 1.0, P[1] + 0.4, P[2] + 1.0, jn(GOWN, 'tb')), 0, 0, 0);")
rep("g(T[0] + 1.0, T[1] * 0.78, T[0] + 1.0, GOWN)","g(T[0] + 1.0, T[1] * 0.78, T[0] + 1.0, jn(GOWN, 't'))")
open('kits.js','w').write(k)

s=open('skinned.js').read()
def r2(a,b):
    global s
    assert s.count(a)==1,a; s=s.replace(a,b)
r2("put(k.spine, g(tw + 0.4, th * 0.6 + 0.2, td + 0.4, gear('#556b34', BDU)), 0, th * 0.7, 0);","put(k.spine, g(tw + 0.4, th * 0.6 + 0.2, td + 0.4, gear('#556b34', { ...BDU, join: 'b' })), 0, th * 0.7, 0);")
r2("put(k.spine, g(tw - 0.6, th * 0.45, td, gear('#556b34', BDU)), 0, th * 0.22, 0);","put(k.spine, g(tw - 0.6, th * 0.45, td, gear('#556b34', { ...BDU, join: 't' })), 0, th * 0.22, 0);")
r2("put(k.hips, g(S.pelvis[0] + 0.4, S.pelvis[1] + 0.2, S.pelvis[2] + 0.4, gear('#556b34', BDU)), 0, 0, 0);","put(k.hips, g(S.pelvis[0] + 0.4, S.pelvis[1] + 0.2, S.pelvis[2] + 0.4, gear('#556b34', { ...BDU, join: 'b' })), 0, 0, 0);")
r2("""    put(sh, g(S.upper[0] + 0.45, S.upper[1] + 0.2, S.upper[0] + 0.45, gear('#556b34', BDU)), 0, S.shoulder - S.upper[1] / 2 + 0.1, 0);""","""    put(sh, g(S.upper[0] + 0.75, S.upper[1] + 0.2, S.upper[0] + 0.75, gear('#556b34', { ...BDU, join: 'b' })), side * 0.05, S.shoulder - S.upper[1] / 2 + 0.1, 0);""")
r2("""    put(sh, g(1.6, 1.0, 0.3, gear('#556b34', BDU2)), side * 0.0, S.shoulder - 2.2, S.upper[0] / 2 + 0.3);         // sleeve pocket
    put(sh, g(S.upper[0] + 0.75, 2.1, S.upper[0] + 0.75, gear('#556b34', BDU)), side * 0.05, S.shoulder - 1.0, 0); // shoulder""","""    put(sh, g(1.6, 1.0, 0.3, gear('#556b34', BDU2)), side * 0.0, S.shoulder - 2.2, S.upper[0] / 2 + 0.45);        // sleeve pocket""")
r2("put(el, g(S.fore[0] + 0.4, S.fore[1] * 0.4, S.fore[0] + 0.4, gear('#556b34', BDU)), 0, 0.2 - S.fore[1] * 0.2, 0);","put(el, g(S.fore[0] + 0.6, S.fore[1] * 0.4, S.fore[0] + 0.6, gear('#556b34', { ...BDU, join: 't' })), 0, 0.2 - S.fore[1] * 0.2, 0);")
r2("put(hip, g(S.thigh[0] + 0.45, S.thigh[1] + 0.4, S.thigh[0] + 0.45, gear('#556b34', BDU)), 0, 0.4 - (S.thigh[1] + 0.4) / 2, 0);","put(hip, g(S.thigh[0] + 0.45, S.thigh[1] + 0.4, S.thigh[0] + 0.45, gear('#556b34', { ...BDU, join: 'tb' })), 0, 0.4 - (S.thigh[1] + 0.4) / 2, 0);")
r2("put(kn, g(S.shin[0] + 0.45, shinTop - shinBot, S.shin[0] + 0.45, gear('#556b34', BDU)), 0, (shinTop + shinBot) / 2, 0);","put(kn, g(S.shin[0] + 0.45, shinTop - shinBot, S.shin[0] + 0.45, gear('#556b34', { ...BDU, join: 't' })), 0, (shinTop + shinBot) / 2, 0);")
open('skinned.js','w').write(s)
print('ok')
