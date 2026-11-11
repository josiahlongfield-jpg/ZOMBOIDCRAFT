import base64, re
b=open('build_body.py').read()
A='/mnt/project-files/zomboid-mod/skin-template/ai-skin-1/zombie_boxers_'
d=lambda r:'data:image/png;base64,'+base64.b64encode(open(f'{A}{r}.png','rb').read()).decode()
b=b.replace("const BODY = { kind: 'zombie',","const AISKIN = { 1024: '"+d(1024)+"', 256: '"+d(256)+"', 64: '"+d(64)+"' };\nconst BODY = { kind: 'ai',")
b=b.replace("""function skinCanvas() {""","""function skinCanvas() {
  if (BODY.kind === 'ai') return AISKIN[BODY.res];""")
b=b.replace("""  const sh = document.getElementById('sheet'); if (sh && sh.getContext) { const x = sh.getContext('2d'); x.imageSmoothingEnabled = false; x.clearRect(0, 0, sh.width, sh.height); x.drawImage(cv, 0, 0, sh.width, sh.height); }""",
"""  const sh = document.getElementById('sheet');
  const drawSheet = img => { if (!sh || !sh.getContext) return; const x = sh.getContext('2d'); x.imageSmoothingEnabled = false; x.clearRect(0, 0, sh.width, sh.height); x.drawImage(img, 0, 0, sh.width, sh.height); };
  if (typeof cv === 'string') { if (typeof Image !== 'undefined') { const img = new Image(); img.onload = () => drawSheet(img); img.src = cv; } } else drawSheet(cv);""")
b=b.replace("fig.userData.player = BODY.kind !== 'zombie';","fig.userData.player = BODY.kind === 'survivor';")
b=b.replace("if (dg && dg.style) dg.style.opacity = BODY.kind === 'zombie' ? 1 : 0.35;","if (dg && dg.style) dg.style.opacity = BODY.kind === 'zombie' ? 1 : 0.35;")
b=b.replace("if (BODY.kind === 'zombie') zombieAnim(","if (BODY.kind !== 'survivor') zombieAnim(")
b=b.replace("seg('kind','Body',[('zombie','Zombie'),('survivor','Survivor')],'zombie')","seg('kind','Body',[('ai','Your AI skin'),('zombie','My painted zombie'),('survivor','My survivor')],'ai')")
b=b.replace("open('body.html','w')","open('body_ai.html','w')").replace("open('chk_body.cjs','w')","open('chk_ai.cjs','w')")
b=b.replace("<title>Detailed Body Skin</title>","<title>Detailed Body Skin</title>")
b=b.replace('''s[:i]+"<p>A fresh painted skin''','''s[:i]+"<p><b>Your AI skin</b> is the turnaround you made, cut block by block from the template positions into a standard 64×64 layout skin at 1024×1024 and worn by the new body with the in-game movements. Texture detail shows it at today's 256 and vanilla 64. The other two are my own painted skins for comparison.</p><p>A fresh painted skin''')
open('build_ai.py.tmp','w').write(b)
