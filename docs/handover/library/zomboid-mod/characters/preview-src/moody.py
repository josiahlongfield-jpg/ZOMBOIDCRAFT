s=open('skinned.js').read()
def r(a,b,src='s'):
    global s
    assert s.count(a)==1,a; s=s.replace(a,b)
r("mixHex(base, '#8f8670', (o.shine || o.clean) ? 0.06 + 0.08 * Wf : 0.1 + 0.22 * Wf)","grade(base, Wf, (o.shine || o.clean) ? 0.7 : 1)")
r("plaid(c, W, H, B, mixHex(o.plaid[0], '#8f8670', 0.15 * Wf), mixHex(o.plaid[1], '#8f8670', 0.2 * Wf))","plaid(c, W, H, B, grade(o.plaid[0], Wf, 1), grade(o.plaid[1], Wf, 1))")
r("if (f === 'py') { c.fillStyle = 'rgba(255,255,240,.12)'; c.fillRect(0, 0, W, H); }","if (f === 'py') { c.fillStyle = 'rgba(255,255,240,.04)'; c.fillRect(0, 0, W, H); }")
r("if (o.shine && f !== 'ny') { c.fillStyle = 'rgba(255,255,255,.22)';","if (o.shine && f !== 'ny') { c.fillStyle = 'rgba(255,255,255,.08)';")
r("if (!jt) { c.fillStyle = 'rgba(255,255,255,.10)'; c.fillRect(1, 1, W - 2, 1); }","if (!jt) { c.fillStyle = 'rgba(255,255,255,.04)'; c.fillRect(1, 1, W - 2, 1); }")
r("const mat = new THREE.MeshLambertMaterial({ map: texOf(skinSrc), alphaTest: 0.5, side: THREE.DoubleSide });","const mat = new THREE.MeshLambertMaterial({ map: texOf(skinSrc), color: KIT.on ? 0x9d978c : 0xffffff, alphaTest: 0.5, side: THREE.DoubleSide });")
open('skinned.js','w').write(s)

d=open('damage.js').read()
def rd(a,b):
    global d
    assert d.count(a)==1,a; d=d.replace(a,b)
rd("const CELL =","""// the moody grade every piece goes through: colour drained toward grey, darkened toward a sooty brown
const grade = (c, w = 0.6, k = 1) => {
  if (!c || c.length !== 7) return c;
  const n = parseInt(c.slice(1), 16), R = n >> 16, G = n >> 8 & 255, B = n & 255, L = 0.3 * R + 0.59 * G + 0.11 * B, ds = (0.42 + 0.18 * w) * k;
  const g = [R, G, B].map(v => Math.round(v + (L - v) * ds)).map(v => v.toString(16).padStart(2, '0')).join('');
  return mixHex('#' + g, '#1f1b15', (0.26 + 0.2 * w) * k);
};
const CELL =""")
rd("if (type === CELL.FADE) return mixHex(base, '#cfc6aa', 0.28);","if (type === CELL.FADE) return mixHex(base, '#77705e', 0.3);")
rd("if (type === CELL.STAIN) return mixHex(base, '#4a3b26', 0.38);","if (type === CELL.STAIN) return mixHex(base, '#2a2014', 0.45);")
rd("if (type === CELL.BLOOD) return mixHex(base, '#4a0d09', 0.62);","if (type === CELL.BLOOD) return mixHex(base, '#2e0806', 0.7);")
rd("faded = mixHex(base, '#8f8670', 0.1 + 0.22 * W8);","faded = grade(base, W8);")
rd("const thread = gear(mixHex(base0, '#e8dcc0', 0.35),","const thread = gear(mixHex(grade(base0, W8), '#8a8270', 0.3),")
rd("bl(rnd() < 0.5 ? '#5e0e0a' : '#3e0b07')","bl(rnd() < 0.5 ? '#4a0b08' : '#2c0806')")
rd("bl('#560c08')","bl('#3e0a07')")
open('damage.js','w').write(d)

b=open('build_all.py').read()
b=b.replace("""s=s.replace("const fig = buildFigure""","""s=s.replace("const scene = makeScene(0x161a18);","const scene = makeScene(0x0c0e0d);")
s=s.replace("scene.add(new THREE.HemisphereLight(0xb9c3c9, 0x2e2a22, 0.95));","scene.add(new THREE.HemisphereLight(0x5f6870, 0x15130f, 0.6));")
s=s.replace("const key = new THREE.DirectionalLight(0xffe2bd, 1.05);","const key = new THREE.DirectionalLight(0xb9c2cc, 0.55);")
s=s.replace("const rim = new THREE.DirectionalLight(0x8fb0ff, 0.55); rim.position.set(40, 30, -60); scene.add(rim);","const rim = new THREE.DirectionalLight(0x6c84b0, 0.4); rim.position.set(40, 30, -60); scene.add(rim);\\n  const lamp = new THREE.PointLight(0xff8a3c, 0.9, 120, 2); lamp.position.set(-35, 28, 25); scene.add(lamp);")
s=s.replace("scene.fog = new THREE.Fog(bg, 110, 260);","scene.fog = new THREE.Fog(bg, 80, 210);")
s=s.replace("x.fillStyle = '#2a2c2a'; x.fillRect(0, 0, 512, 512);","x.fillStyle = '#1a1c1b'; x.fillRect(0, 0, 512, 512);")
s=s.replace("x.fillStyle = 'rgba(200,170,80,.55)'; x.fillRect(250, 0, 10, 512);","x.fillStyle = 'rgba(150,130,70,.3)'; x.fillRect(250, 0, 10, 512); for (let i = 0; i < 5; i++) blob(x, rnd() * 512, rnd() * 512, 50, 'rgba(120,140,160,.08)', 14);")
s=s.replace("const fig = buildFigure""",1)
open('build_all.py','w').write(b)
print('ok')
