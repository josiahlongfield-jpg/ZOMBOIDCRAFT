import json, base64, re
from pathlib import Path
T=Path('/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/textures/entity')
A=json.load(open('/home/user/zomboidcraft/tools/mockup/walker_addons.json'))
d=lambda p:'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()
data={'atlasSize':A['size'],'pieces':A['outfits']['soldier'],
      'skins':{k:[d(T/f'walker_hd/soldier_{k}.png'),d(T/f'walker_addons_hd/soldier_{k}.png')] for k in ('olive_1','pale_3','dark_0')}}
s=open('index.html').read()
s=s.replace('// ---------- animation ----------', open('skinned.js').read()+'// ---------- animation ----------',1)
s=s.replace("(() => {\nconst PX = 8","const SOLDIER = "+json.dumps(data)+";\n(() => {\nconst PX = 8",1)
assert 'const SOLDIER' in s
s=s.replace("const fig = buildFigure('A', 'player', 0, 31); scene.add(fig);","""const BLOOD = { olive_1: 0.3, dark_0: 0.05, pale_3: 0.9 };
let skinKey = 'olive_1', fig = null;
function rebuild() { if (fig) scene.remove(fig); KIT.blood = BLOOD[skinKey]; fig = buildSkinned(...SOLDIER.skins[skinKey]); scene.add(fig); }
rebuild();
for (const id of ['skin', 'kit']) document.getElementById(id).addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
  if (id === 'skin') skinKey = b.dataset.v; else KIT.on = b.dataset.v === 'new';
  rebuild();
});""")
s=s.replace('''    <div class="group"><span class="lab">View</span>''','''    <div class="group"><span class="lab">Skin</span>
      <div class="seg" id="skin">
        <button data-v="olive_1" aria-pressed="true">Soldier</button>
        <button data-v="dark_0">Fresh</button>
        <button data-v="pale_3">Badly hurt</button>
      </div></div>
    <div class="group"><span class="lab">Gear</span>
      <div class="seg" id="kit">
        <button data-v="new" aria-pressed="true">New kit</button>
        <button data-v="old">Old pieces</button>
      </div></div>
    <div class="group"><span class="lab">View</span>''')
s=s.replace('<title>Plain Player Body</title>','<title>Soldier Player Body</title>')
s=s.replace('<h1>The player body, plain</h1>','<h1>The zombie soldier, on the player body</h1>')
i=s.index('<p>The player from the first mockup'); j=s.index('</p>',i)+4
s=s[:i]+"<p>Today's zombie soldier skin on the player body from the first mockup, now dressed entirely in 3D pieces made for this body: a woodland camo shirt with sleeves rolled to the forearm, sleeve pockets and rolled cuffs, trousers with cargo pockets, knee patches and bloused cuffs, jungle boots with lugged soles, toe caps, canvas uppers, laces and pull loops, plus a covered helmet with band and chin strap, a flak vest with collar, flaps and zip, suspenders, a pistol belt with buckle, ammo pouches, canteen and first-aid pouch. Every piece is its own block with hard edges and its own colour. Switch to Old pieces to compare. Preview only; nothing is built into the mod.</p>"+s[j:]
open('soldier.html','w').write(s)
open('chk.cjs','w').write(re.findall(r'<script>(.*?)</script>',s,re.S)[0])
