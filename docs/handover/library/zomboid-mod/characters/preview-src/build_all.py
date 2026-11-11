import json, base64, re
from pathlib import Path
T=Path('/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/textures/entity/walker_hd')
O='civilian construction doctor farmer firefighter hoodie jogger nurse office patient police prisoner security soldier survivor'.split()
d=lambda p:'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()
skins={o:{k:d(T/f'{o}_{k}.png') for k in ('olive_1','dark_0','pale_3')} for o in O}
s=open('index.html').read()
s=s.replace('// ---------- animation ----------', open('skinned.js').read()+open('kits.js').read()+open('damage.js').read()+open('zanim.js').read()+'// ---------- animation ----------',1)
AI='data:image/png;base64,'+base64.b64encode(open('/mnt/project-files/zomboid-mod/skin-template/ai-skin-1/zombie_boxers_1024.png','rb').read()).decode()
s=s.replace("(() => {\nconst PX = 8","const AISKIN = '"+AI+"';\nconst SKINS = "+json.dumps(skins)+";\nconst SOLDIER = { atlasSize: 256, pieces: [] };\n(() => {\nconst PX = 8",1)
s=s.replace("const scene = makeScene(0x161a18);","const scene = makeScene(0x0c0e0d);")
s=s.replace("scene.add(new THREE.HemisphereLight(0xb9c3c9, 0x2e2a22, 0.95));","scene.add(new THREE.HemisphereLight(0x5f6870, 0x15130f, 0.6));")
s=s.replace("const key = new THREE.DirectionalLight(0xffe2bd, 1.05);","const key = new THREE.DirectionalLight(0xb9c2cc, 0.55);")
s=s.replace("const rim = new THREE.DirectionalLight(0x8fb0ff, 0.55); rim.position.set(40, 30, -60); scene.add(rim);","const rim = new THREE.DirectionalLight(0x6c84b0, 0.4); rim.position.set(40, 30, -60); scene.add(rim);\n  const lamp = new THREE.PointLight(0xff8a3c, 0.9, 120, 2); lamp.position.set(-35, 28, 25); scene.add(lamp);")
s=s.replace("scene.fog = new THREE.Fog(bg, 110, 260);","scene.fog = new THREE.Fog(bg, 80, 210);")
s=s.replace("x.fillStyle = '#2a2c2a'; x.fillRect(0, 0, 512, 512);","x.fillStyle = '#1a1c1b'; x.fillRect(0, 0, 512, 512);")
s=s.replace("x.fillStyle = 'rgba(200,170,80,.55)'; x.fillRect(250, 0, 10, 512);","x.fillStyle = 'rgba(150,130,70,.3)'; x.fillRect(250, 0, 10, 512); for (let i = 0; i < 5; i++) blob(x, rnd() * 512, rnd() * 512, 50, 'rgba(120,140,160,.08)', 14);")
s=s.replace("const fig = buildFigure('A', 'player', 0, 31); scene.add(fig);","""const BLOOD = { olive_1: 0.3, dark_0: 0.05, pale_3: 0.9 }, WEAR = { olive_1: 0.7, dark_0: 0.4, pale_3: 1.0 };
let skinKey = 'olive_1', fig = null, baseSkin = 'ai';
function rebuild() { if (fig) { scene.remove(fig); fig.traverse(m => { if (!m.geometry) return; m.geometry.dispose(); [].concat(m.material).forEach(x => { if (x.map && x.map.dispose) x.map.dispose(); x.dispose(); }); }); } KIT.blood = BLOOD[skinKey]; KIT.wear = WEAR[skinKey]; const src = baseSkin === 'ai' ? AISKIN : SKINS[KIT.outfit][skinKey]; fig = buildSkinned(src, src); scene.add(fig); }
KIT.outfit = 'civilian'; rebuild();
for (const id of ['outfit', 'skin', 'kit', 'base']) document.getElementById(id).addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
  if (id === 'skin') skinKey = b.dataset.v; else if (id === 'base') baseSkin = b.dataset.v; else if (id === 'kit') KIT.on = b.dataset.v === 'new'; else KIT.outfit = b.dataset.v;
  rebuild();
});""")
names={'civilian':'Civilian','construction':'Construction','doctor':'Doctor','farmer':'Farmer','firefighter':'Firefighter','hoodie':'Hoodie','jogger':'Jogger','nurse':'Nurse','office':'Office','patient':'Patient','police':'Police','prisoner':'Prisoner','security':'Security','soldier':'Soldier','survivor':'Survivor'}
btns='\n'.join(f'        <button data-v="{o}" aria-pressed="{str(o=="civilian").lower()}">{names[o]}</button>' for o in O)
s=s.replace('''  <div class="controls">''','''  <div class="controls">
    <div class="group" style="flex-basis:100%"><span class="lab">Outfit</span>
      <div class="seg" id="outfit">
'''+btns+'''
      </div></div>''',1)
s=s.replace('''    <div class="group"><span class="lab">View</span>''','''    <div class="group"><span class="lab">Body skin</span>
      <div class="seg" id="base">
        <button data-v="ai" aria-pressed="true">Your AI skin</button>
        <button data-v="today">Today's skins</button>
      </div></div>
    <div class="group"><span class="lab">Damage</span>
      <div class="seg" id="skin">
        <button data-v="olive_1" aria-pressed="true">Some</button>
        <button data-v="dark_0">Fresh</button>
        <button data-v="pale_3">Badly hurt</button>
      </div></div>
    <div class="group"><span class="lab">Clothes</span>
      <div class="seg" id="kit">
        <button data-v="new" aria-pressed="true">3D kit</button>
        <button data-v="old">Skin only</button>
      </div></div>
    <div class="group"><span class="lab">View</span>''')
assert 'id="outfit"' in s and 'id="skin"' in s
s=s.replace('''  rebuild();
});''','''  rebuild();
});
'''+open('photon.js').read(),1)
assert 'setLook(true)' in s
s=s.replace("  renderer.render(scene, camera);\n  requestAnimationFrame(frame);","  renderLook(t - (frame.last ?? t)); frame.last = t;\n  requestAnimationFrame(frame);",1)
assert 'renderLook(t' in s
pp='https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/'
s=s.replace('<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>','<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>\n'+''.join(f'<script src="{pp}{f}"></script>\n' for f in []))
s=s.replace('.stage { position: relative;','.stage::after { content: ""; position: absolute; inset: 0; pointer-events: none; background: radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,.45)); opacity: var(--vig, 1); }\n.stage { position: relative;')
s=s.replace('''    <div class="group"><span class="lab">View</span>''','''    <div class="group"><span class="lab">Lighting</span>
      <div class="seg" id="look">
        <button data-v="photon" aria-pressed="true">Photon-style</button>
        <button data-v="plain">Plain</button>
      </div></div>
    <div class="group"><span class="lab">View</span>''',1)
s=s.replace("setLook(true);","setLook(true);\ndocument.getElementById('look').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b)); setLook(b.dataset.v === 'photon'); });",1)
s=s.replace('''        <button data-v="walk" aria-pressed="true">Walk</button>
        <button data-v="stand">Stand</button>''','''        <button data-v="shamble" aria-pressed="true">Shamble</button>
        <button data-v="chase">Chase</button>
        <button data-v="attack">Attack</button>
        <button data-v="idle">Idle</button>
        <button data-v="crawler">Crawler</button>
        <button data-v="down">Knocked down</button>''',1)
s=s.replace("const state = { anim: 'walk',","const state = { anim: 'shamble',",1)
s=s.replace("  animate(fig, state.anim, t, 0.4);","  const oi = Object.keys(SKINS).indexOf(KIT.outfit); zombieAnim(fig, state.anim, t, { phase: (oi * 37 % 40) / 40 * 6, side: oi % 2 ? -1 : 1 });",1)
assert 'zombieAnim(fig' in s and 'data-v="crawler"' in s and "anim: 'shamble'" in s
s=s.replace('<title>Plain Player Body</title>','<title>Zombie Outfit Kits</title>')
s=s.replace('<h1>The player body, plain</h1>','<h1>Every zombie type, dressed in 3D kits</h1>')
i=s.index('<p>The player from the first mockup'); j=s.index('</p>',i)+4
s=s[:i]+"<p>All fifteen zombie types on the player body, each wearing its own kit in the soldier style, worn out and torn, with the damage built from real 3D pieces: every garment, hat, belt, boot and accessory is its own chunky block with hard dark edges, a lit top and its own colour, with the face, hands and any bare skin coming from the body skin underneath: your AI skin by default, or today's skins. The movements are the walkers' in-game animations (shamble, chase with arms up, the attack swipe, idle, crawler, knocked down) running on the new body. Pick an outfit, change the damage level to see holes, peeled flaps, frayed threads, faded and stained panels, raised blood and caked mud build up, or switch to Skin only to compare. Photon-style lighting imitates your Photon settings (haze, rain, bloom, wet road, a sodium streetlamp and a froglight); it is an approximation in the browser, not the shader itself. Preview only; nothing is built into the mod.</p>"+s[j:]
open('kits.html','w').write(s)
open('chk_all.cjs','w').write(re.findall(r'<script>(.*?)</script>',s,re.S)[0])
print(len(s)//1024,'KB')
