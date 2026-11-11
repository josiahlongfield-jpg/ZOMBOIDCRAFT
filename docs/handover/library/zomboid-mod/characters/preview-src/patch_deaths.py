# Death falls, bodies that stay, and hit jerks (Josiah 27 Oct 07:47-07:51, preview asked 08:33): no more tip-over and
# puff. A killed zombie falls the game's way (deaths.js, the game's ZDeath) and its body stays; shots that don't kill
# jerk the part they hit and stagger it back (hits.js, the game's ZHit). Run on the v17 kits.html (patch_heads applied):
#   python3 patch_deaths.py KITS_HTML OUT_HTML
import re, sys
src, out = sys.argv[1:3]
s = open(src).read()
js = open('hits.js').read() + open('deaths.js').read()

def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)

rep("function frame() {", js + "function frame() {")
rep("if (!HEADS.dead) { limbMove(fig, state.anim, t, mv); applyFace(); }",
    "if (!HEADS.dead) { limbMove(fig, state.anim, t, mv); stepHits(t); applyFace(); }")
rep("if (!HEADS) return;", "if (!HEADS) return;\n  if (HITS) HITS.length = 0;")
# what each shot struck, and which way it went through the body (radians from its front round toward its left)
rep("const dealt = {}, count = {}, first = {};", "const dealt = {}, count = {}, first = {}, KILL = { parts: 0, part: -1, hd: {} };")
rep("    any = dir;\n",
    "    any = dir;\n"
    "    KILL.parts |= 1 << part; if (KILL.part < 0) KILL.part = part; if (KILL.hd[part] == null) KILL.hd[part] = Math.atan2(d.x, d.z);\n")
rep("HEADS.dead = { t0 }; said.push('it drops dead');", "HEADS.dead = dieNow(t0, SHOOT.gun, 0, KILL.hd[0], before); said.push('it drops dead');")
rep("if (HEADS.hp <= 0) { HEADS.dead = { t0 };",
    "if (HEADS.hp <= 0) { const kp = headPellets ? 0 : KILL.part; HEADS.dead = dieNow(t0, SHOOT.gun, kp, KILL.hd[kp], before);")
rep("  const after = legsGone();\n",
    "  const after = legsGone();\n"
    "  if (!HEADS.dead) hitNow(t0, KILL.parts, KILL.hd[KILL.part], { pistol: 0, rifle: 1, hunting: 2, shotgun: 3 }[SHOOT.gun]);\n"
    "  else said.push(['it crumples where it stood', 'it drops mid-shamble', 'it goes down running and slides on', 'it falls out of its lunge',\n"
    "    'it topples off its one leg', 'it goes slack where it crawled', 'it goes still where it lay'][HEADS.dead.f.kind]);\n")
# the fall, then the body stays where it fell
i = s.index("    // a Minecraft death:")
k = s.index("    if (tau >= 4.5) { rebuild(); return; }\n", i) + len("    if (tau >= 4.5) { rebuild(); return; }\n")
s = s[:i] + """    // it falls the game's way and the body stays where it lands (Reset stands up a new one)
    const tau = t - d.t0;
    dPut(fig.userData.J, dPose(d.f, tau));
    if (tau > D_SETTLED && !d.said) { d.said = true; shotlog.textContent += '. The body stays where it fell (Reset stands up a new one)'; }
""" + s[k:]
rep("and it drops dead, tipping onto its side and vanishing in a puff the way every Minecraft mob dies;", "and it drops dead;")
rep("red head, yellow body, blue arms, green legs.",
    "red head, yellow body, blue arms, green legs. No more puff of smoke: a killed zombie falls the way the shot and its own momentum "
    "take it, the knees going first, the head and arms trailing, turning to land on its face or its back, and the body stays where it "
    "lands, missing whatever was shot off. Try killing it standing, shambling, chasing, mid-attack, hopping on one leg and crawling, and "
    "from different sides (turn it round): a runner pitches forward and slides, a hard hit from the front throws it on its back, a hopper "
    "goes over toward its missing leg. Shots that don't kill jerk the part they hit and stagger it back a step.")
open(out, 'w').write(s)
open(out + '.check.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[-1])
print(len(s) // 1024, 'KB')
