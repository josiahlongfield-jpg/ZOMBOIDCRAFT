# Add the three mood faces (idle / alerted / chase) from ai-skin-2 to kits.html, swapped by movement.
import base64
D = '/mnt/project-files/zomboid-mod/skin-template/ai-skin-2/'
u = lambda n: 'data:image/png;base64,' + base64.b64encode(open(D + f'zombie_{n}_1024.png', 'rb').read()).decode()
s = open('kits.html').read()
def rep(a, b):
    global s
    assert s.count(a) == 1, a[:60]
    s = s.replace(a, b)
i = s.index("const AISKIN = '"); j = s.index("';", i) + 2
s = s[:i] + "const AIFACES = { idle: '" + u('idle') + "', alert: '" + u('alert') + "', chase: '" + u('chase') + "' };\nconst AISKIN = AIFACES.idle;" + s[j:]
rep("idle: { speed: 0 },", "idle: { speed: 0 }, alert: { speed: 0.12, aggressive: true },")
rep('''        <button data-v="chase">Chase</button>''', '''        <button data-v="alert">Alerted</button>
        <button data-v="chase">Chase</button>''')
rep('''    <div class="group"><span class="lab">Body skin</span>''', '''    <div class="group"><span class="lab">Face</span>
      <div class="seg" id="face">
        <button data-v="auto" aria-pressed="true">Follows movement</button>
        <button data-v="idle">Idle</button>
        <button data-v="alert">Alerted</button>
        <button data-v="chase">Chase</button>
      </div></div>
    <div class="group"><span class="lab">Body skin</span>''')
rep("[].concat(m.material).forEach(x => { if (x.map && x.map.dispose) x.map.dispose(); x.dispose(); });",
    "[].concat(m.material).forEach(x => { if (x.map && x.map.dispose && !x.map.keep) x.map.dispose(); x.dispose(); });")
rep("fig = buildSkinned(src, src); scene.add(fig); }",
    "fig = buildSkinned(src, src); scene.add(fig); fig.traverse(m => { if (!fig.userData.skinMat && m.material && !Array.isArray(m.material) && m.geometry && m.geometry.attributes) fig.userData.skinMat = m.material; }); fig.userData.face = null; }")
rep("const clock = new THREE.Clock();", """// the face follows the zombie's mood: idle / shamble closed mouth, alerted snarl, chase and attack open mouth
const FACETEX = {}; for (const k in AIFACES) { FACETEX[k] = texOf(AIFACES[k]); FACETEX[k].keep = true; }
const MOOD = { shamble: 'idle', idle: 'idle', crawler: 'idle', down: 'idle', alert: 'alert', chase: 'chase', attack: 'chase' };
state.face = 'auto';
document.getElementById('face').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b)); state.face = b.dataset.v; });
function applyFace() {
  const m = fig.userData.skinMat; if (!m || baseSkin !== 'ai') return;
  const k = state.face === 'auto' ? MOOD[state.anim] || 'idle' : state.face;
  if (fig.userData.face !== k) { m.map = FACETEX[k]; m.needsUpdate = true; fig.userData.face = k; }
}
const clock = new THREE.Clock();""")
rep("  renderLook(t - (frame.last ?? t));", "  applyFace();\n  renderLook(t - (frame.last ?? t));")
rep("your AI skin by default, or today's skins.", "your AI skin by default (its face changes with the movement: closed mouth when idle or shambling, a snarl when alerted, mouth open when chasing or attacking), or today's skins.")
open('kits.html', 'w').write(s)
import re; open('chk_all.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[0])
print(len(s) // 1024, 'KB')
