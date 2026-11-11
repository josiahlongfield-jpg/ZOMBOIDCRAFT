s=open('bodies/index.html').read()
def rep(a,b,cnt=1):
    global s
    assert a in s, a[:80]; s=s.replace(a,b,cnt)
def cut(start, end):
    global s
    i=s.index(start); j=s.index(end,i)
    s=s[:i]+s[j:]
# --- geometry: no belt, collar, jacket details, neck, face blocks, thumbs
rep("""  // belt
  J.hips.add(""","""  if (0) J.hips.add(""")
rep("""  J.spine.add(at(box(tw * 0.55, 0.9, td + 0.6, cloth('shirt')), 0, th + 0.1, 0));""","")
rep("if (o.shirt === 'jacket') { // pockets, zip and a backpack","if (o.shirt === 'jacket' && !BASIC) { // pockets, zip and a backpack")
rep("""  J.neck.add(at(box(S.neck[0], S.neck[1] + 0.6, S.neck[0], skin('neck')), 0, S.neck[1] / 2, 0));
  J.head = new THREE.Group(); J.head.position.y = S.neck[1]; J.neck.add(J.head);""","""  J.head = new THREE.Group(); J.head.position.y = 0; J.neck.add(J.head); // no neck: the head sits on the body""")
cut("  feat(S.brow, 0,","  J.head.add(at(box(hw + 0.5, 1.4,")
rep("""    const th2 = new THREE.Group(); th2.position.set(-side * (S.hand[0] / 2 - 0.1), -0.3, S.hand[2] / 2 - 0.1); wr.add(th2); J['tb' + R] = th2;
    hang(th2, box(S.thumb[0], S.thumb[1], S.thumb[2], skin('hand')), 0, 0, 0).rotation.z = side * 0.25;""","""    J['tb' + R] = new THREE.Group(); // no thumbs""")
rep("const PX = 8;","const PX = 8, BASIC = true;")
# mouth painted on the flat face, since there is no jaw block
rep("""    if (!o.zombie) { ctx.fillStyle = o.hair; ctx.fillRect(W * 0.18, H * 0.27, W * 0.24, 3); ctx.fillRect(W * 0.58, H * 0.27, W * 0.24, 3); }""","""    if (!o.zombie) { ctx.fillStyle = o.hair; ctx.fillRect(W * 0.18, H * 0.27, W * 0.24, 3); ctx.fillRect(W * 0.58, H * 0.27, W * 0.24, 3); }
    ctx.fillStyle = 'rgba(0,0,0,.14)'; ctx.fillRect(W * 0.44, H * 0.48, W * 0.12, H * 0.14);
    ctx.fillStyle = o.zombie ? '#2a1214' : '#8e5a4c'; ctx.fillRect(W * 0.32, H * 0.78, W * 0.36, 3);""")
# plain shirt: no stripes
rep("""      else { ctx.fillStyle = '#4e5638'; ctx.fillRect(0, 0, W, H); ctx.fillStyle = 'rgba(0,0,0,.18)'; for (let y = 0; y < H; y += 6) ctx.fillRect(0, y, W, 1); }""","""      else { ctx.fillStyle = '#4e5638'; ctx.fillRect(0, 0, W, H); }""")
# --- page: one figure, player only
i=s.index('<div class="wrap">'); j=s.index('<script src=')
s=s[:i]+'''<div class="wrap">
  <header>
    <div class="eyebrow">ZomboidCraft · character bodies · preview only</div>
    <h1>The player body, plain</h1>
    <p>The player from the first mockup, exactly: same proportions and the same joint movement, with the elbows and knees bending as it walks. The head sits straight on the body with no neck, there are no face blocks and no thumbs, and it wears a plain basic skin. Nothing has been built into the mod.</p>
  </header>
  <div class="stage">
    <canvas id="main" aria-label="3D view of the plain player body"></canvas>
    <div class="hint">Drag to turn it</div>
  </div>
  <div class="controls">
    <div class="group"><span class="lab">Movement</span>
      <div class="seg" id="anim">
        <button data-v="walk" aria-pressed="true">Walk</button>
        <button data-v="stand">Stand</button>
      </div></div>
    <div class="group"><span class="lab">View</span>
      <div class="seg" id="view">
        <button data-v="body" aria-pressed="true">Whole body</button>
        <button data-v="close">Upper body</button>
      </div></div>
  </div>
</div>

'''+s[j:]
# replace main JS section from "// ---------- main lineup ----------" to end of IIFE
i=s.index('// ---------- main lineup ----------'); j=s.index('})();\n</script>')
s=s[:i]+'''// ---------- one figure ----------
const canvas = document.getElementById('main');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.outputEncoding = THREE.sRGBEncoding; renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
const scene = makeScene(0x161a18);
const camera = new THREE.PerspectiveCamera(30, 2, 1, 1000);
const fig = buildFigure('A', 'player', 0, 31); scene.add(fig);
const state = { anim: 'walk', close: false, yaw: 0.6 };
let drag = null;
canvas.addEventListener('pointerdown', e => { drag = { x: e.clientX, yaw: state.yaw }; canvas.setPointerCapture(e.pointerId); });
canvas.addEventListener('pointermove', e => { if (drag) state.yaw = drag.yaw + (e.clientX - drag.x) * 0.01; });
canvas.addEventListener('pointerup', () => drag = null);
for (const id of ['anim', 'view']) document.getElementById(id).addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  e.currentTarget.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
  if (id === 'anim') state.anim = b.dataset.v; else state.close = b.dataset.v === 'close';
});
const cp = new THREE.Vector3(0, 26, 110), cl = new THREE.Vector3(0, 17, 0), tp = new THREE.Vector3(), tl = new THREE.Vector3();
const clock = new THREE.Clock(); const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
function frame() {
  const t = reduce ? 1.2 : clock.getElapsedTime();
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== Math.round(w * renderer.getPixelRatio()) || canvas.height !== Math.round(h * renderer.getPixelRatio())) { renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); }
  if (state.close) { tp.set(0, 30, 52); tl.set(0, 25, 0); } else { tp.set(0, 24, w < 600 ? 140 : 100); tl.set(0, 16, 0); }
  cp.lerp(tp, 0.08); cl.lerp(tl, 0.08); camera.position.copy(cp); camera.lookAt(cl);
  fig.rotation.y = state.yaw;
  animate(fig, state.anim, t, 0.4);
  renderer.render(scene, camera);
  requestAnimationFrame(frame);
}
frame();
'''+s[j:]
s=s.replace('<title>ZomboidCraft Bodies</title>','<title>Plain Player Body</title>')
s=s.replace('#main { display: block; width: 100%; height: min(62vh, 560px);','#main { display: block; width: 100%; height: min(70vh, 640px);')
# shoulders flush with the top of the torso, arms a little shorter
rep("""    sh.add(at(box(S.upper[0] + 0.6, 2.2, S.upper[0] + 0.5, cloth('shirt'), true), side * 0.1, S.shoulder - 0.4, 0));""","""    sh.add(at(box(S.upper[0] + 0.5, 2.0, S.upper[0] + 0.5, cloth('shirt'), true), side * 0.05, S.shoulder - 1.0, 0)); // shoulder cap, flush with the torso top""")
rep("head: [6.4, 7.2, 6.6], upper: [2.7, 6.2], fore: [2.5, 5.6],","head: [6.4, 7.2, 6.6], upper: [2.7, 5.7], fore: [2.5, 5.1],")
rep("const sh = new THREE.Group(); sh.position.set(side * (tw / 2 + S.upper[0] / 2 - 0.2), th - S.shoulder, 0);","const sh = new THREE.Group(); sh.position.set(side * (tw / 2 + S.upper[0] / 2 - 0.1), th - S.shoulder - 0.3, 0);")
open('player/index.html','w').write(s)
