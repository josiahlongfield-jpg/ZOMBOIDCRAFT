# Josiah 03:40: every kit permanently "Badly hurt" (most worn), and the red glowing eyes on every preview.
import re, json
s = open('kits.html').read()
def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)
rep("let skinKey = 'olive_1', fig = null,", "let skinKey = 'pale_3', fig = null,")
i = s.index('    <div class="group"><span class="lab">Damage</span>'); j = s.index('</div></div>', i) + len('</div></div>\n')
s = s[:i] + s[j:]
rep("for (const id of ['outfit', 'skin', 'kit', 'base'])", "for (const id of ['outfit', 'kit', 'base'])")
# only the badly-hurt variants are used now
i = s.index('const SKINS = '); j = s.index(';\n', i)
sk = json.loads(s[i + 14:j]); sk = {o: {'pale_3': v['pale_3']} for o, v in sk.items()}
s = s[:i] + 'const SKINS = ' + json.dumps(sk) + s[j:]
import base64
E = '/home/user/zomboidcraft/src/main/resources/assets/zomboidcraft/textures/entity/walker_eyes_hd/'
em = {o: 'data:image/png;base64,' + base64.b64encode(open(E + f'{o}_pale_3.png', 'rb').read()).decode() for o in sk}
rep("const AI15 = ", "const EYEMASK = " + json.dumps(em) + ";\nconst AI15 = ")
# glowing eyes: find the eye whites on the face front of the skin in use, draw them full-bright red on a layer
# just in front of the face, with a soft additive glow over each eye (as the mod's look.glowingEyes does in game)
rep("// the face follows the zombie's mood", """const EYEGLOW = (() => {
  const c = document.createElement('canvas'); c.width = c.height = 64; const x = c.getContext('2d');
  const g = x.createRadialGradient(32, 32, 0, 32, 32, 32); g.addColorStop(0, 'rgba(255,40,20,.95)'); g.addColorStop(0.3, 'rgba(255,20,10,.4)'); g.addColorStop(1, 'rgba(255,0,0,0)');
  x.fillStyle = g; x.fillRect(0, 0, 64, 64); const t = new THREE.CanvasTexture(c); t.keep = true; return t;
})();
function addEyes(f, src, mask) {
  const im = new Image();
  im.onload = () => {
    if (f !== fig) return;
    const J = f.userData.J, S = STYLES.A, [hw, hh, hd] = S.head, k = im.width / 64, N = Math.round(8 * k);
    const c = document.createElement('canvas'); c.width = c.height = N; const x = c.getContext('2d');
    x.drawImage(im, 8 * k, 8 * k, N, N, 0, 0, N, N);
    const d = x.getImageData(0, 0, N, N), p = d.data, hits = [[0, 0, 0], [0, 0, 0]];
    for (let y = 0; y < N; y++) for (let xx = 0; xx < N; xx++) {
      const o = (y * N + xx) * 4, r = p[o], g = p[o + 1], b = p[o + 2], mx = Math.max(r, g, b), mn = Math.min(r, g, b);
      const eye = mask ? p[o + 3] > 40 : y > N * 0.25 && y < N * 0.6 && mn > 165 && mx - mn < 50 && mx > 195;   // the mod's eye mask, or pale unsaturated pixels in the eye band
      if (eye) { if (!mask) { const t = 0.75 + 0.25 * (mn / 255); p[o] = 255 * t; p[o + 1] = 40 * t; p[o + 2] = 25 * t; } p[o + 3] = 255; const h = hits[xx < N / 2 ? 0 : 1]; h[0] += xx; h[1] += y; h[2]++; }
      else p[o + 3] = 0;
    }
    x.putImageData(d, 0, 0);
    const old = f.userData.eyes; if (old) { old.parent.remove(old); }
    const grp = new THREE.Group(); grp.position.set(0, hh / 2, hd / 2 + 0.03); J.head.add(grp); f.userData.eyes = grp;
    const t = new THREE.CanvasTexture(c); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.keep = false;
    grp.add(new THREE.Mesh(new THREE.PlaneGeometry(hw, hh), new THREE.MeshBasicMaterial({ map: t, transparent: true, alphaTest: 0.5, fog: false, toneMapped: false })));
    for (const h of hits) if (h[2] > 2) {
      const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: EYEGLOW, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, opacity: 0.8, fog: false }));
      sp.position.set((h[0] / h[2] / N - 0.5) * hw, (0.5 - h[1] / h[2] / N) * hh, 0.3); sp.scale.set(2.6, 2.6, 1); grp.add(sp);
    }
  };
  im.src = mask || src;
}
// the face follows the zombie's mood""")
rep("fig.userData.face = null; }", "fig.userData.face = null; addEyes(fig, src, baseSkin === 'today' ? EYEMASK[KIT.outfit] : null); }")
rep("if (fig.userData.face !== k) { m.map = FACETEX[k]; m.needsUpdate = true; fig.userData.face = k; }",
    "if (fig.userData.face !== k) { m.map = FACETEX[k]; m.needsUpdate = true; fig.userData.face = k; addEyes(fig, AIFACES[k]); }")
rep("change the damage level to see holes, peeled flaps, frayed threads, faded and stained panels, raised blood and caked mud build up, or switch to Skin only to compare.",
    "every kit is shown at its most worn (holes, peeled flaps, frayed threads, faded and stained panels, raised blood and caked mud), with the red glowing eyes; switch to Skin only to compare.")
open('kits.html', 'w').write(s)
open('chk_all.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[0])
print(len(s) // 1024, 'KB')
