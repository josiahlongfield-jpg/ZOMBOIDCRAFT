# Only a headshot kills, and armless zombies wriggle (Josiah 27 Oct 08:53): body and limb shots can't finish it, any
# bullet in the head does; on the ground with no arms it wriggles on its belly (the game's ZMove.wriggle). Run on the
# v18 kits.html (patch_deaths applied):
#   python3 patch_headshots.py KITS_HTML OUT_HTML
import re, sys
src, out = sys.argv[1:3]
s = open(src).read()

def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)

rep("""  } else {
    HEADS.hp -= harm;
    if (HEADS.hp <= 0) { const kp = headPellets ? 0 : KILL.part; HEADS.dead = dieNow(t0, SHOOT.gun, kp, KILL.hd[kp], before); said.push(headPellets ? 'the headshot kills it' : 'it dies'); }
  }""", """  } else if (headPellets) {
    // only the head kills: any bullet in it finishes it
    HEADS.dead = dieNow(t0, SHOOT.gun, 0, KILL.hd[0], before); said.push('the headshot finishes it');
  } else {
    // anywhere else wears it down and takes it apart, but it keeps coming
    HEADS.hp = Math.max(1, HEADS.hp - harm);
    if (HEADS.hp <= 1) said.push('still alive: only a headshot finishes it');
  }""")
rep("  function crawl(kind, legless) {", """  function wriggle(kind, legless) {
    // no arms to drag itself with (the game's ZMove.wriggle): it wriggles along on its belly, the hips and legs swinging
    // from side to side with the chest humping up, the head craned and kept on line; it squirms even at rest
    const moving = kind === 'slow' || kind === 'fast';
    const q = moving ? t * (kind === 'fast' ? 0.9 : 0.42) * 2 * PI * 1.1 + ph : t * (kind === 'alert' ? 2.6 : 1.7) + ph;
    const s = sin(q), hump = Math.max(0, sin(2 * q)), big = moving ? 1 : 0.6;
    hips.rotation.set(PI / 2 - (legless ? 0.05 : 0), sin(q + 0.6) * 0.2 * big, s * 0.28 * big);
    hips.position.set(-s * 0.5 * big, S.pelvis[2] / 2 + (legless ? 0.2 : 0.5), -7 + (moving ? hump : 0));
    J.spine.rotation.set(-0.03 - hump * 0.25 * big, -sin(q - 0.4) * 0.15, -sin(q - 0.25) * 0.32 * big);
    const hd = kind === 'alert' ? clamp(lookAt(), -1.4, 1.4) : 0;
    J.neck.rotation.set(-0.55 - hump * 0.08, hd * 0.3, sin(q - 0.9) * 0.12 * big);
    J.head.rotation.set(-0.5 + sin(2 * q - 0.8) * 0.06, hd * 0.45 + sin(q - 2.2) * 0.12, 0.08 + sin(t * 0.8 + ph) * 0.06);
    if (kind === 'grab') {
      // it snaps at your ankle: the chest heaves up and the head lunges and bites
      const c = (t + ph * 0.3) % 1.8, l = seg(c, 0.2, 0.45) * (1 - seg(c, 1.2, 1.7));
      J.spine.rotation.x = -0.3 - 0.3 * l; J.neck.rotation.x = -0.6 - 0.2 * l;
      J.head.rotation.x = -0.45 - 0.15 * l + 0.35 * l * Math.max(0, sin(c * 18));
      hips.position.z += 1.6 * l;
    }
    // what's left of the arms flaps at its sides; the legs (or thigh stumps) kick in turn behind it
    arm('R', -0.2 + sin(q - 0.5) * 0.3, 0.25, 0, 0, 0); arm('L', -0.2 - sin(q - 0.5) * 0.3, 0.25, 0, 0, 0);
    const kR = 0.25 + Math.max(0, s) * 0.8 * big, kL = 0.25 + Math.max(0, -s) * 0.8 * big;
    leg('R', -0.05 - Math.max(0, -s) * 0.35 * big, kR, 0.6, 0.12 + s * 0.1, 0.1);
    leg('L', -0.05 - Math.max(0, s) * 0.35 * big, kL, 0.6, 0.12 - s * 0.1, 0.1);
  }
  function crawl(kind, legless) {""")
rep("    const rest = kind === 'rest', alert = kind === 'alert', grab = kind === 'grab';",
    "    if (SHOOT && ['R', 'L'].every(R => SHOOT.cut.has('sh' + R) || SHOOT.cut.has('el' + R))) return wriggle(kind, legless);\n"
    "    const rest = kind === 'rest', alert = kind === 'alert', grab = kind === 'grab';")
rep("goes over toward its missing leg. Shots that don't kill jerk the part they hit and stagger it back a step.",
    "goes over toward its missing leg. Shots that don't kill jerk the part they hit and stagger it back a step. Only a headshot "
    "kills it now: shoot every limb off and it keeps coming, eyes glowing, crawling on what it has left or, with no arms, wriggling "
    "along on its belly, until you put one in its head.")
open(out, 'w').write(s)
open(out + '.check.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[-1])
print(len(s) // 1024, 'KB')
