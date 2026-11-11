import re
s=open('index.html').read()
sk=open('skinned.js').read()
sk=sk.replace("const texOf = src => { const t = new THREE.TextureLoader().load(src);","const texOf = src => { const t = typeof src === 'string' ? new THREE.TextureLoader().load(src) : new THREE.CanvasTexture(src);")
assert 'CanvasTexture(src)' in sk
s=s.replace('// ---------- animation ----------', sk+open('damage.js').read()+open('zanim.js').read()+open('paintskin.js').read()+'// ---------- animation ----------',1)
s=s.replace("(() => {\nconst PX = 8","const SOLDIER = { atlasSize: 256, pieces: [] };\n(() => {\nconst PX = 8",1)
s=s.replace("const scene = makeScene(0x161a18);","const scene = makeScene(0x0c0e0d);")
s=s.replace("scene.add(new THREE.HemisphereLight(0xb9c3c9, 0x2e2a22, 0.95));","scene.add(new THREE.HemisphereLight(0x5f6870, 0x15130f, 0.6));")
s=s.replace("const key = new THREE.DirectionalLight(0xffe2bd, 1.05);","const key = new THREE.DirectionalLight(0xb9c2cc, 0.55);")
s=s.replace("const rim = new THREE.DirectionalLight(0x8fb0ff, 0.55); rim.position.set(40, 30, -60); scene.add(rim);","const rim = new THREE.DirectionalLight(0x6c84b0, 0.4); rim.position.set(40, 30, -60); scene.add(rim);\n  const lamp = new THREE.PointLight(0xff8a3c, 0.9, 120, 2); lamp.position.set(-35, 28, 25); scene.add(lamp);")
s=s.replace("x.fillStyle = '#2a2c2a'; x.fillRect(0, 0, 512, 512);","x.fillStyle = '#1a1c1b'; x.fillRect(0, 0, 512, 512);")
s=s.replace("const fig = buildFigure('A', 'player', 0, 31); scene.add(fig);","""KIT.on = false;
const BODY = { kind: 'zombie', tone: 'olive', dmg: 1, res: 1024 }, cache = {};
let fig = null, sheetSrc = null;
function skinCanvas() {
  const k = BODY.kind + BODY.tone + BODY.dmg;
  if (!cache[k]) cache[k] = paintSkin(16, BODY.kind, BODY.tone, BODY.dmg);
  const full = cache[k];
  if (BODY.res === 1024) return full;
  const lo = document.createElement('canvas'); lo.width = lo.height = BODY.res; const x = lo.getContext('2d');
  x.imageSmoothingEnabled = true; x.imageSmoothingQuality = 'high'; x.drawImage(full, 0, 0, BODY.res, BODY.res); return lo;
}
function rebuild() {
  if (fig) { scene.remove(fig); fig.traverse(m => { if (!m.geometry) return; m.geometry.dispose(); [].concat(m.material).forEach(x => { if (x.map && x.map.dispose) x.map.dispose(); x.dispose(); }); }); }
  const cv = skinCanvas(); fig = buildSkinned(cv, cv); fig.userData.player = BODY.kind !== 'zombie'; scene.add(fig);
  const sh = document.getElementById('sheet'); if (sh && sh.getContext) { const x = sh.getContext('2d'); x.imageSmoothingEnabled = false; x.clearRect(0, 0, sh.width, sh.height); x.drawImage(cv, 0, 0, sh.width, sh.height); }
  const dg = document.getElementById('dmg'); if (dg && dg.style) dg.style.opacity = BODY.kind === 'zombie' ? 1 : 0.35;
}
rebuild();
for (const id of ['kind', 'tone', 'dmg', 'res']) document.getElementById(id).addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
  BODY[id] = id === 'dmg' || id === 'res' ? +b.dataset.v : b.dataset.v;
  rebuild();
});
"""+open('photon.js').read().replace("setLook(true);","setLook(true);\ndocument.getElementById('look').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b)); setLook(b.dataset.v === 'photon'); });"))
s=s.replace("  renderer.render(scene, camera);\n  requestAnimationFrame(frame);","  renderLook(t - (frame.last ?? t)); frame.last = t;\n  requestAnimationFrame(frame);",1)
s=s.replace("  animate(fig, state.anim, t, 0.4);","  if (BODY.kind === 'zombie') zombieAnim(fig, state.anim, t, { phase: 1.2, side: 1 }); else animate(fig, state.anim === 'idle' || state.anim === 'down' ? 'stand' : 'walk', t, 0.4);",1)
s=s.replace("const state = { anim: 'walk',","const state = { anim: 'shamble',",1)
assert 'renderLook(t' in s and 'zombieAnim(fig' in s and "anim: 'shamble'" in s
s=s.replace('''        <button data-v="walk" aria-pressed="true">Walk</button>
        <button data-v="stand">Stand</button>''','''        <button data-v="shamble" aria-pressed="true">Shamble</button>
        <button data-v="chase">Chase</button>
        <button data-v="attack">Attack</button>
        <button data-v="idle">Idle</button>
        <button data-v="crawler">Crawler</button>
        <button data-v="down">Knocked down</button>''',1)
seg=lambda id,lab,opts,on: f'''    <div class="group"><span class="lab">{lab}</span>
      <div class="seg" id="{id}">
'''+'\n'.join(f'        <button data-v="{v}" aria-pressed="{str(v==on).lower()}">{t}</button>' for v,t in opts)+'\n      </div></div>\n'
s=s.replace('''  <div class="controls">''','''  <div class="controls">
'''+seg('kind','Body',[('zombie','Zombie'),('survivor','Survivor')],'zombie')+seg('tone','Skin tone',[('pale','Pale'),('olive','Olive'),('dark','Dark')],'olive')+seg('dmg','Decay',[('0','Fresh'),('1','Rotting'),('2','Mangled')],'1')+seg('res','Texture detail',[('64','Vanilla 64'),('256','Today 256'),('1024','Max 1024')],'1024')+seg('look','Lighting',[('photon','Photon-style'),('plain','Plain')],'photon'),1)
s=s.replace('''    <div class="hint">Drag to turn it</div>
  </div>''','''    <div class="hint">Drag to turn it · Close view for the face</div>
  </div>
  <div class="sheetwrap"><span class="lab">The painted skin (standard 64×64 layout)</span><canvas id="sheet" width="512" height="512" aria-label="The flat skin texture"></canvas></div>''',1)
s=s.replace('#main { display: block;','.sheetwrap { display: grid; gap: 8px; margin-top: 18px; } .sheetwrap .lab { font: 500 11.5px var(--mono); letter-spacing: .12em; text-transform: uppercase; color: var(--muted); }\n#sheet { width: min(100%, 512px); height: auto; image-rendering: pixelated; background: repeating-conic-gradient(#1a1d1b 0 25%, #121412 0 50%) 0 0 / 16px 16px; border: 1px solid var(--line); border-radius: 6px; }\n.stage::after { content: ""; position: absolute; inset: 0; pointer-events: none; background: radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,.45)); }\n#main { display: block;')
s=s.replace('<title>Plain Player Body</title>','<title>Detailed Body Skin</title>')
s=s.replace('<h1>The player body, plain</h1>','<h1>The most detailed body skin, moody</h1>')
i=s.index('<p>The player from the first mockup'); j=s.index('</p>',i)+4
s=s[:i]+"<p>A fresh painted skin for the new body, with no 3D clothing: a dark, comic-inked style with mottled, dirty skin, cel-shaded form, sunken milky eyes, cracked lips and broken teeth, veins, bruising and lividity pooling in the hands and feet, bite wounds, torn flesh and bone showing as it decays. The Survivor shows the same detail on a living body. It is painted at 16 texture pixels per skin pixel (1024×1024) in the standard skin layout; Texture detail shows how it holds up at today's 256 and at vanilla 64. Preview only; nothing is built into the mod.</p>"+s[j:]
open('body.html','w').write(s)
open('chk_body.cjs','w').write(re.findall(r'<script>(.*?)</script>',s,re.S)[0])
print(len(s)//1024,'KB')
