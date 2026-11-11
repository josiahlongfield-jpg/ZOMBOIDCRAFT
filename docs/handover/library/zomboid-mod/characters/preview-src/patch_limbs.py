# Shooting limbs off (Josiah 09:42): pick a gun, click the zombie; limbs tear off at the joint above the hit,
# fly along the shot and lie on the ground, torn stumps stay, losing a leg drops it to a crawl.
import re
s = open('kits.html').read()
def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)
rep("function frame() {", open('limbs.js').read() + "function frame() {")
rep("zombieMove(fig, state.anim, t, {", "limbMove(fig, state.anim, t, {")
rep("  applyFace();\n", "  applyFace();\n  stepLimbs(Math.min(0.05, t - (frame.prevL ?? t))); frame.prevL = t;\n")
rep("if (LEGS.off) makeLegless(fig); }", "if (LEGS.off) makeLegless(fig); resetLimbs(); }")
rep('''    <div class="group"><span class="lab">Legs</span>''', '''    <div class="group"><span class="lab">Shoot it: pick a gun, click the zombie</span>
      <div class="seg" id="gun">
        <button data-v="pistol">Pistol</button>
        <button data-v="rifle">Assault rifle</button>
        <button data-v="hunting">Hunting rifle</button>
        <button data-v="shotgun" aria-pressed="true">Shotgun</button>
        <button data-v="reset">Reset limbs</button>
      </div>
      <div id="shotlog" style="font-size:12px;opacity:.85;margin-top:6px;min-height:1.3em" aria-live="polite"></div></div>
    <div class="group"><span class="lab">Legs</span>''')
rep("Legless swaps every zombie to a stump version that does all of these on its arms.",
    "Legless swaps every zombie to a stump version that does all of these on its arms. Shoot it: pick a gun and click a limb. Each hit tears the limb a little more (a shotgun up close usually takes it in one blast, the hunting rifle often does, the assault rifle takes a few hits and the pistol several); when it goes, it breaks off at the joint above the hit, flies off along the shot and lies on the ground, and a torn stump with bone showing stays behind. Losing one leg makes it stumble down onto its other knee, push itself back up and hop on the leg it has left; losing both makes it pitch forward, drop and break the fall on its arms, then crawl. The numbers here are placeholders for the weapon thread to tune.")
open('kits.html', 'w').write(s)
open('chk_all.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[0])
print(len(s) // 1024, 'KB')
