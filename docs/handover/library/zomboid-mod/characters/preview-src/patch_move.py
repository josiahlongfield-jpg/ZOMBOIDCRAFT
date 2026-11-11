# Movement v2 (Josiah 03:55): new idle sway, whole-body shamble, alert head turn to the player, arms-straight chase,
# new attack, better crawl, and a legless version of every zombie.
import re
s = open('kits.html').read()
def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)
rep("// ---------- animation ----------", open('move.js').read() + "// ---------- animation ----------")
rep("addEyes(fig, src, baseSkin === 'today' ? EYEMASK[KIT.outfit] : null); }", "addEyes(fig, src, baseSkin === 'today' ? EYEMASK[KIT.outfit] : null); if (LEGS.off) makeLegless(fig); }")
rep('''        <button data-v="crawler">Crawler</button>''', '''        <button data-v="crawler">Crawl</button>''')
rep('''    <div class="group"><span class="lab">Face</span>''', '''    <div class="group"><span class="lab">Legs</span>
      <div class="seg" id="legs">
        <button data-v="full" aria-pressed="true">Full body</button>
        <button data-v="off">Legless</button>
      </div></div>
    <div class="group"><span class="lab">Face</span>''')
rep("const clock = new THREE.Clock();", """document.getElementById('legs').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b)); LEGS.off = b.dataset.v === 'off'; rebuild(); });
// a stand-in player who wanders in front of the zombie, so the alert head turn has someone to find
const pl = new THREE.Group(); scene.add(pl);
{ const m = (w, h, d, c, y) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: c })); b.position.y = y; b.castShadow = true; pl.add(b); };
  m(3, 13, 2.4, 0x2b3440, 6.5); m(7, 9, 4, 0x3c4a3a, 17.5); m(5.6, 6, 5.6, 0x8a6f58, 25); m(2.4, 9, 2.4, 0x3c4a3a, 17); }
const torch = new THREE.PointLight(0xfff0c8, 0.8, 60, 2); torch.position.set(0, 20, -3); pl.add(torch);
const clock = new THREE.Clock();""")
rep("const oi = Object.keys(SKINS).indexOf(KIT.outfit); zombieAnim(fig, state.anim, t, { phase: (oi * 37 % 40) / 40 * 6, side: oi % 2 ? -1 : 1 });",
    """const oi = Object.keys(SKINS).indexOf(KIT.outfit);
  const pa = Math.sin(t * 0.35) * 1.35, alertish = state.anim === 'alert'; pl.visible = alertish;
  PLAYER.x = Math.sin(pa) * 34; PLAYER.z = Math.cos(pa) * 34; pl.position.set(PLAYER.x, 0, PLAYER.z); pl.rotation.y = pa + Math.PI / 2 * Math.sign(Math.cos(t * 0.35));
  zombieMove(fig, state.anim, t, { phase: (oi * 37 % 40) / 40 * 6, dt: Math.min(0.05, t - (frame.prev ?? t)) }); frame.prev = t;""")
rep("The movements are the walkers' in-game animations (shamble, chase with arms up, the attack swipe, idle, crawler, knocked down) running on the new body.",
    "The movements are new and use every joint: a swaying idle, a slow shamble the whole body rides (weight shifting, hips rolling and twisting, spine countering, head trailing, one leg dragging), an alert where the head snaps round to a wandering player and the neck, spine and hips follow, a lurching chase with the arms thrust straight out, a lunge-grab-and-bite attack, a proper hand-over-hand crawl, and knocked down. Legless swaps every zombie to a stump version that does all of these on its arms.")
open('kits.html', 'w').write(s)
open('chk_all.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[0])
print(len(s) // 1024, 'KB')
