// ---------- the same body, wearing a real Minecraft-layout skin ----------
// Each box shows a vertical slice [a, b] of the matching vanilla part of the skin (64x64 layout),
// so any player or zombie skin fits without repainting.
const SK = { head: [0, 0, 8, 8, 8], body: [16, 16, 8, 12, 4], rarm: [40, 16, 4, 12, 4], larm: [32, 48, 4, 12, 4], rleg: [0, 16, 4, 12, 4], lleg: [16, 48, 4, 12, 4] };
function skinBox(mat, w, h, d, part, a = 0, b = 1) {
  const [u, v, W, H, D] = SK[part];
  const x0 = -w / 2, x1 = w / 2, y0 = -h / 2, y1 = h / 2, z0 = -d / 2, z1 = d / 2;
  const pos = [], uv = [], idx = [];
  const q = (A, B, C, E, u0, v0, u1, v1) => {
    const n = pos.length / 3; pos.push(...A, ...B, ...C, ...E);
    uv.push(u0 / 64, v0 / 64, u1 / 64, v0 / 64, u1 / 64, v1 / 64, u0 / 64, v1 / 64);
    idx.push(n, n + 1, n + 2, n, n + 2, n + 3);
  };
  const s0 = v + D + a * H, s1 = v + D + b * H;
  q([x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1], u + D, s0, u + D + W, s1);                 // front
  q([x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0], u + 2 * D + W, s0, u + 2 * D + 2 * W, s1); // back
  q([x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0], u, s0, u + D, s1);                         // right side
  q([x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1], u + D + W, s0, u + 2 * D + W, s1);         // left side
  q([x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1], u + D, v, u + D + W, v + D);               // top
  q([x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0], u + D + W, v, u + D + 2 * W, v + D);       // bottom
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx); g.computeVertexNormals(); g.parameters = { width: w, height: h, depth: d };
  const m = new THREE.Mesh(g, mat); m.castShadow = m.receiveShadow = true; return m;
}
// A Minecraft model cube (y down, front -z) for the outfit pieces, which are authored in Minecraft space.
function mcCube(mat, texSize, u, v, x, y, z, w, h, d, g = [0, 0, 0]) {
  const x0 = x - g[0], y0 = y - g[1], z0 = z - g[2], x1 = x + w + g[0], y1 = y + h + g[1], z1 = z + d + g[2];
  const pos = [], uv = [], idx = [];
  const q = (A, B, C, E, u0, v0, u1, v1) => {
    const n = pos.length / 3; pos.push(...A, ...B, ...C, ...E);
    uv.push(u0 / texSize, v0 / texSize, u1 / texSize, v0 / texSize, u1 / texSize, v1 / texSize, u0 / texSize, v1 / texSize);
    idx.push(n, n + 1, n + 2, n, n + 2, n + 3);
  };
  q([x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0], u + d, v + d, u + d + w, v + d + h);
  q([x1, y0, z1], [x0, y0, z1], [x0, y1, z1], [x1, y1, z1], u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h);
  q([x0, y0, z1], [x0, y0, z0], [x0, y1, z0], [x0, y1, z1], u, v + d, u + d, v + d + h);
  q([x1, y0, z0], [x1, y0, z1], [x1, y1, z1], [x1, y1, z0], u + d + w, v + d, u + 2 * d + w, v + d + h);
  q([x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0], u + d, v, u + d + w, v + d);
  q([x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1], u + d + w, v, u + d + 2 * w, v + d);
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  geo.setIndex(idx); geo.computeVertexNormals();
  const m = new THREE.Mesh(geo, mat); m.castShadow = m.receiveShadow = true; return m;
}
const texOf = src => { const t = new THREE.TextureLoader().load(src); t.magFilter = t.minFilter = THREE.NearestFilter; t.encoding = THREE.sRGBEncoding; t.flipY = false; return t; };
function mcPiece(parent, amat, pc, bonePivot) {
  const g = new THREE.Group(); const piv = pc.pivot, b = pc.box;
  g.position.set(piv[0] - bonePivot[0], piv[1] - bonePivot[1], piv[2] - bonePivot[2]);
  g.rotation.set(...pc.rot.map(a => a * Math.PI / 180), 'ZYX');
  g.add(mcCube(amat, SOLDIER.atlasSize, pc.uv[0], pc.uv[1], b[0] - piv[0], b[1] - piv[1], b[2] - piv[2], b[3] - b[0], b[4] - b[1], b[5] - b[2]));
  parent.add(g);
}
function buildSkinned(skinSrc, atlasSrc) {
  const S = STYLES.A;
  const mat = new THREE.MeshLambertMaterial({ map: texOf(skinSrc), color: KIT.on ? 0x9d978c : 0xffffff, alphaTest: 0.5, side: THREE.DoubleSide });
  const amat = new THREE.MeshLambertMaterial({ map: texOf(atlasSrc), alphaTest: 0.5, side: THREE.DoubleSide });
  const fig = new THREE.Group(); const J = {}; fig.userData = { J, style: 'A', player: true };
  const [tw, th, td] = S.torso, span = th + S.pelvis[1];   // torso + pelvis read the whole body column of the skin
  const legLen = S.thigh[1] + S.shin[1] + S.foot[1];
  J.hips = new THREE.Group(); J.hips.position.y = legLen + S.pelvis[1] / 2; fig.add(J.hips);
  const cut = (top, bot) => [top / span, bot / span];       // distance down from the top of the torso
  J.hips.add(skinBox(mat, S.pelvis[0], S.pelvis[1], S.pelvis[2], 'body', ...cut(th, span)));
  J.spine = new THREE.Group(); J.spine.position.y = S.pelvis[1] / 2; J.hips.add(J.spine);
  J.spine.add(at(skinBox(mat, tw - 1, th * 0.45, td - 0.4, 'body', ...cut(th * 0.55, th)), 0, th * 0.22, 0));
  J.spine.add(at(skinBox(mat, tw, th * 0.6, td, 'body', ...cut(0, th * 0.6)), 0, th * 0.7, 0));
  J.neck = new THREE.Group(); J.neck.position.y = th; J.spine.add(J.neck);
  J.head = new THREE.Group(); J.neck.add(J.head);
  const [hw, hh, hd] = S.head;
  J.head.add(at(skinBox(mat, hw, hh, hd, 'head'), 0, hh / 2, 0));
  // the skin's hat layer and the outfit's head and torso pieces, in Minecraft space scaled onto the new shapes
  const hg = new THREE.Group(); hg.rotation.x = Math.PI; hg.scale.set(hw / 8, hh / 8, hd / 8); J.head.add(hg);
  if (!KIT.on) hg.add(mcCube(mat, 64, 32, 0, -4, -8, -4, 8, 8, 8, [.5, .5, .5]));
  const bg = new THREE.Group(); bg.rotation.x = Math.PI; bg.scale.set(tw / 8, span / 12, td / 4); bg.position.y = th; J.spine.add(bg);
  if (!KIT.on) for (const pc of SOLDIER.pieces) {
    if (pc.bone === 'head') mcPiece(hg, amat, pc, [0, 0, 0]);
    else if (pc.bone === 'body') mcPiece(bg, amat, pc, [0, 0, 0]);
  }
  for (const side of [-1, 1]) {
    const R = side < 0 ? 'R' : 'L', arm = side < 0 ? 'rarm' : 'larm', leg = side < 0 ? 'rleg' : 'lleg';
    const sh = new THREE.Group(); sh.position.set(side * (tw / 2 + S.upper[0] / 2 - 0.1), th - S.shoulder - 0.3, 0); J.spine.add(sh); J['sh' + R] = sh;
    const armLen = S.upper[1] + S.fore[1] + S.hand[1];
    sh.add(at(skinBox(mat, S.upper[0] + 0.5, 2.0, S.upper[0] + 0.5, arm, 0, 2 / armLen), side * 0.05, S.shoulder - 1.0, 0));
    hang(sh, skinBox(mat, S.upper[0], S.upper[1], S.upper[0], arm, 0, S.upper[1] / armLen), 0, S.shoulder, 0);
    const el = new THREE.Group(); el.position.y = S.shoulder - S.upper[1]; sh.add(el); J['el' + R] = el;
    hang(el, skinBox(mat, S.fore[0], S.fore[1], S.fore[0], arm, S.upper[1] / armLen, (S.upper[1] + S.fore[1]) / armLen), 0, 0.2, 0);
    const wr = new THREE.Group(); wr.position.y = -S.fore[1] + 0.2; el.add(wr); J['wr' + R] = wr; J['tb' + R] = new THREE.Group();
    hang(wr, skinBox(mat, S.hand[0], S.hand[1], S.hand[2], arm, (S.upper[1] + S.fore[1]) / armLen, 1), 0, 0, 0.1);
    const hip = new THREE.Group(); hip.position.set(side * S.pelvis[0] * 0.26, -S.pelvis[1] / 2 + 0.4, 0); J.hips.add(hip); J['hip' + R] = hip;
    hang(hip, skinBox(mat, S.thigh[0], S.thigh[1] + 0.4, S.thigh[0], leg, 0, S.thigh[1] / legLen), 0, 0.4, 0);
    const kn = new THREE.Group(); kn.position.y = -S.thigh[1]; hip.add(kn); J['kn' + R] = kn;
    hang(kn, skinBox(mat, S.shin[0], S.shin[1] + 0.3, S.shin[0], leg, S.thigh[1] / legLen, (S.thigh[1] + S.shin[1]) / legLen), 0, 0.3, 0);
    const an = new THREE.Group(); an.position.y = -S.shin[1]; kn.add(an); J['an' + R] = an;
    hang(an, skinBox(mat, S.foot[0], S.foot[1] + 0.6, S.foot[2], leg, (S.thigh[1] + S.shin[1]) / legLen, 1), 0, 0.6, S.foot[2] / 2 - S.shin[0] / 2 - 0.1);
  }
  if (KIT.on) (KITS[KIT.outfit] || soldierKit)(J, S);
  return fig;
}

// ---------- our own soldier kit, made for this body ----------
// Every piece is its own block with its own painted texture: hard dark edges, a lit top, stitching on
// webbing, metal buckles, so each one reads separately from the uniform underneath.
function gear(base, o = {}) {
  const paint = (c, W, H, f) => {
    // join: 't' / 'b' marks the top / bottom of a piece that continues into the next one (a sleeve into
    // its forearm, a thigh into its shin), so no edge line or grime band is drawn there
    const jt = (o.join || '').includes('t'), jb = (o.join || '').includes('b'), sideF = f !== 'py' && f !== 'ny';
    // the colour itself is worn: sun-faded and dulled toward a dusty grey-brown
    const Wf = KIT.wear ?? 0.6, B = o.noFade || base.length !== 7 || KIT.paintFx ? base : grade(base, Wf, (o.shine || o.clean) ? 0.7 : 1);
    if (o.plaid) plaid(c, W, H, B, grade(o.plaid[0], Wf, 1), grade(o.plaid[1], Wf, 1)); else { c.fillStyle = B; c.fillRect(0, 0, W, H); }
    if (o.camo) for (let i = 0; i < (W * H) / 90; i++) blob(c, rnd() * W, rnd() * H, 4 + rnd() * 7, o.camo[(rnd() * o.camo.length) | 0], 6);
    if (o.denim) { c.fillStyle = 'rgba(255,255,255,.08)'; for (let y = 0; y < H; y++) for (let x = y % 4; x < W; x += 4) c.fillRect(x, y, 1, 1); }
    if (o.weave) { c.fillStyle = 'rgba(0,0,0,.13)'; for (let y = 0; y < H; y += 3) c.fillRect(0, y, W, 1); }
    if (o.knit) { c.fillStyle = 'rgba(0,0,0,.18)'; for (let x = 0; x < W; x += 3) c.fillRect(x, 0, 1, H); }
    if (o.straw) { for (let y = 0; y < H; y += 3) for (let x = (y / 3 % 2) * 3; x < W; x += 6) { c.fillStyle = 'rgba(90,60,20,.35)'; c.fillRect(x, y, 3, 1); c.fillStyle = 'rgba(255,240,180,.2)'; c.fillRect(x + 3, y + 1, 3, 1); } }
    if (o.dots) { c.fillStyle = o.dots; for (let y = 2; y < H; y += 6) for (let x = (y % 12 ? 2 : 5); x < W; x += 6) c.fillRect(x, y, 2, 2); }
    if (o.diag) { c.fillStyle = o.diag[0]; for (let k = -H; k < W; k += o.diag[1]) for (let y = 0; y < H; y++) c.fillRect(k + y, y, o.diag[2] || 3, 1); }
    if (o.vstripe) { c.fillStyle = o.vstripe[0]; for (let x = 2; x < W; x += o.vstripe[1]) c.fillRect(x, 0, o.vstripe[2] || 1, H); }
    if (o.strands) for (let x = 0; x < W; x += 2) { c.fillStyle = rnd() < 0.5 ? `rgba(0,0,0,${rnd() * 0.3})` : `rgba(255,255,255,${rnd() * 0.12})`; c.fillRect(x, 0, 1, H); }
    noise(c, W, H, o.noise ?? 0.08, 2);
    if (f === 'py') { c.fillStyle = 'rgba(255,255,240,.04)'; c.fillRect(0, 0, W, H); }      // lit from above
    if (f === 'ny') { c.fillStyle = 'rgba(0,0,0,.35)'; c.fillRect(0, 0, W, H); }
    if (o.mid && f !== 'py' && f !== 'ny') { c.fillStyle = o.mid; c.fillRect(0, Math.round(H / 3), W, Math.round(H / 3)); }   // reflective strip
    if (o.shine && f !== 'ny') { c.fillStyle = 'rgba(255,255,255,.08)'; c.fillRect(Math.round(W * 0.18), 2, Math.max(2, Math.round(W * 0.12)), H - 4); }
    if (o.text && f === (o.textFace || 'pz')) {
      c.fillStyle = o.textColor || '#fff'; c.textAlign = 'center'; c.textBaseline = 'middle';
      c.font = `bold ${Math.max(6, Math.floor(Math.min(H * 0.6, W * 1.5 / o.text.length)))}px sans-serif`; c.fillText(o.text, W / 2, H / 2 + 1);
    }
    if (o.stitch && W > 10 && H > 10) {                                                     // stitching just inside the edge
      c.fillStyle = o.stitch;
      if (sideF) {
        for (let x = 4; x < W - 4; x += 4) { if (!jt) c.fillRect(x, 3, 2, 1); if (!jb) c.fillRect(x, H - 4, 2, 1); }
        for (let y = 4; y < H - 4; y += 4) { c.fillRect(3, y, 1, 2); c.fillRect(W - 4, y, 1, 2); }
      } else if (!(f === 'py' && jt) && !(f === 'ny' && jb)) {
        for (let x = 4; x < W - 4; x += 4) { c.fillRect(x, 3, 2, 1); c.fillRect(x, H - 4, 2, 1); }
        for (let y = 4; y < H - 4; y += 4) { c.fillRect(3, y, 1, 2); c.fillRect(W - 4, y, 1, 2); }
      }
    }
    // worn and filthy: a brown-grey wash, faded and dark patches, mud toward the bottom, old stains
    const W8 = KIT.wear ?? 0.6, metal = !!(o.shine || o.clean), fab = !metal && base.length === 7, area = W * H;
    if (KIT.paintFx) {
    c.fillStyle = `rgba(58,48,32,${0.10 + 0.14 * W8})`; c.fillRect(0, 0, W, H);
    for (let i = 0; i < area * W8 / 500; i++) blob(c, rnd() * W, rnd() * H, 3 + rnd() * 8, rnd() < 0.5 ? `rgba(232,222,196,${0.04 + rnd() * 0.08})` : `rgba(40,32,20,${0.05 + rnd() * 0.12})`, 6);
    for (let i = 0; i < area * W8 / 1400; i++) blob(c, rnd() * W, H * (0.4 + rnd() * 0.6), 5 + rnd() * 10, `rgba(70,52,30,${0.2 + rnd() * 0.25})`, 8);
    if (fab) for (let i = 0; i < area * W8 / 2500; i++) blob(c, rnd() * W, rnd() * H, 4 + rnd() * 8, 'rgba(95,82,40,.16)', 9);
    if (metal) {                                                                              // scratches, chips and rust
      c.fillStyle = 'rgba(255,255,255,.22)'; for (let i = 0; i < 3 + 8 * W8; i++) c.fillRect(rnd() * W, rnd() * H, 2 + rnd() * 8, 1);
      for (let i = 0; i < 1 + area * W8 / 900; i++) blob(c, rnd() * W, rnd() * H, 2 + rnd() * 4, rnd() < 0.5 ? 'rgba(115,62,24,.4)' : 'rgba(10,10,10,.35)', 5);
    }
    }
    // thin dark outline on the outer edges only
    c.fillStyle = `rgba(0,0,0,${o.outline ?? 0.6})`;
    if (o.noOutline) {} else if (sideF) {
      if (!jt) c.fillRect(0, 0, W, 1);
      if (!jb) c.fillRect(0, H - 1, W, 1);
      c.fillRect(0, 0, 1, H); c.fillRect(W - 1, 0, 1, H);
      if (!jt) { c.fillStyle = 'rgba(255,255,255,.04)'; c.fillRect(1, 1, W - 2, 1); }
    } else if (!(f === 'py' && jt) && !(f === 'ny' && jb)) {
      c.fillRect(0, 0, W, 1); c.fillRect(0, H - 1, W, 1); c.fillRect(0, 0, 1, H); c.fillRect(W - 1, 0, 1, H);
    }
    if (o.scuff) for (let i = 0; i < 3; i++) { c.fillStyle = 'rgba(200,190,160,.18)'; c.fillRect(rnd() * W, rnd() * H, 3 + rnd() * 6, 1); }
    // grime: a gradient on single pieces, an even wash on pieces that flow into a neighbour
    if (o.join) { c.fillStyle = `rgba(30,22,10,${(o.grime ?? 0.3) * 0.45})`; c.fillRect(0, 0, W, H); }
    else grime(c, W, H, o.grime ?? 0.3);
    if (!KIT.paintFx) return;
    // blood: fresh splashes and drips, older brown patches, fine spray, the odd hand smear
    const amt = (KIT.blood + 0.12) * (o.bloodK ?? 1) * (o.clean ? 0.35 : 1);
    blood(c, W, H, amt, true);
    for (let i = 0; i < area * amt / 1200; i++) blob(c, rnd() * W, rnd() * H, 4 + rnd() * 10, 'rgba(70,30,15,.55)', 8);
    c.fillStyle = 'rgba(110,14,10,.85)'; for (let i = 0; i < area * amt / 150; i++) c.fillRect(rnd() * W, rnd() * H, rnd() < 0.3 ? 2 : 1, rnd() < 0.3 ? 2 : 1);
    if (sideF && rnd() < amt * 0.5) { const x = rnd() * W * 0.7, y = rnd() * H * 0.7; c.fillStyle = 'rgba(85,14,10,.6)'; for (let k = 0; k < 4; k++) c.fillRect(x + k * 3, y + k * 0.5, W * 0.3, 2); }
    // torn fabric: frayed holes (the body shows through), claw rakes and a ragged bottom edge
    if (fab && sideF && W >= 16 && H >= 16 && !o.noTear) {
      const nh = Math.round(area * W8 / 3000 * (0.5 + rnd()));
      for (let i = 0; i < nh; i++) {
        const x = 4 + rnd() * (W - 8), y = 6 + rnd() * (H - 12), rr = 2 + rnd() * 4 * W8 + 1;
        blob(c, x, y, rr + 3, 'rgba(232,220,190,.35)', 9);
        if (rnd() < 0.6) blob(c, x, y + rr, rr + 2, 'rgba(90,12,8,.7)', 6);
        c.globalCompositeOperation = 'destination-out'; blob(c, x, y, rr, '#000', 9); c.globalCompositeOperation = 'source-over';
        c.fillStyle = base; for (let k = 0; k < 2; k++) c.fillRect(x - rr, y - rr / 2 + rnd() * rr, rr * 2, 1);
      }
      if (rnd() < W8 * 0.35 && H > 24) {
        const x0 = rnd() * W * 0.6, y0 = rnd() * H * 0.4, L = H * (0.3 + rnd() * 0.3), sl = (rnd() - 0.5) * 0.8;
        for (let k = 0; k < 3; k++) for (let t = 0; t < L; t++) {
          const x = x0 + k * 4 + t * sl, y = y0 + t;
          c.fillStyle = 'rgba(100,14,10,.8)'; c.fillRect(x - 1, y, 4, 1);
          c.globalCompositeOperation = 'destination-out'; c.fillRect(x, y, 2, 1); c.globalCompositeOperation = 'source-over';
        }
      }
      if (!jb && H > 12) {
        c.globalCompositeOperation = 'destination-out';
        for (let x = 0; x < W; x += 2) if (rnd() < 0.5 * W8) { const d = 1 + rnd() * 4 * W8; c.fillRect(x, H - d, 2, d); }
        c.globalCompositeOperation = 'source-over';
      }
    }
  };
  paint.with = extra => gear(base, { ...o, ...extra }); paint.base = base; paint.o = o;
  return paint;
}
const jn = (p, join) => (p.with ? p.with({ join }) : p);
const KIT = { blood: 0.3, on: true, outfit: 'soldier', wear: 0.6, paintFx: false };
const OD = '#4a5030', WEB = '#6a6844', STEEL = '#8d8f88', BLACK = '#1d1c1a';
const CAMO = ['#2f3a1e', '#5b4a2c', '#161812', '#556b34'];
function soldierKit(J, S) {
  seed = 4242;
  const [tw, th, td] = S.torso, [hw, hh, hd] = S.head, k = J;
  const put = (parent, m, x, y, z, rx = 0, ry = 0, rz = 0) => { m.position.set(x, y, z); m.rotation.set(rx, ry, rz); parent.add(m); return m; };
  const g = (w, h, d, p) => box(w, h, d, p, true);
  // M1 / PASGT style helmet with a woodland cover, band and chin strap, worn low so the rim sits just above the eyes
  const HY = -1.1;
  put(k.head, g(hw + 1.3, 3.2, hd + 1.3, gear('#4c5530', { camo: CAMO, noise: .1 })), 0, hh + 0.2 + HY, 0);
  put(k.head, g(hw + 0.7, 1.0, hd + 0.7, gear('#4c5530', { camo: CAMO })), 0, hh + 2.1 + HY, 0);
  put(k.head, g(hw + 1.9, 0.7, hd + 1.9, gear('#3c4426', { camo: CAMO })), 0, hh - 1.45 + HY, 0);       // flared rim
  put(k.head, g(hw + 1.45, 0.8, hd + 1.45, gear('#2b2f1d', { weave: true, grime: .1 })), 0, hh * 0.9 + HY, 0); // band
  put(k.head, g(1.6, 1.4, 0.4, gear('#9a8f62', { grime: .2 })), hw * 0.25, hh * 0.92 + HY, hd / 2 + 0.95, -0.1); // card in the band
  for (const sx of [-1, 1]) {
    put(k.head, g(0.35, hh * 0.62, 0.4, gear(WEB, { grime: .2 })), sx * (hw / 2 + 0.2), hh * 0.42, hd * 0.15, 0, 0, sx * 0.05);
    put(k.head, g(0.6, 0.6, 0.6, gear(STEEL, { noise: .15, grime: .1 })), sx * (hw / 2 + 0.25), hh * 0.7 + HY, hd * 0.15);
  }
  put(k.head, g(hw * 0.7, 0.35, 0.4, gear(WEB, { grime: .2 })), 0, 0.15, hd * 0.25);                 // strap under the chin
  // flak vest: chest and waist panels, raised collar, front flaps and zip
  put(k.spine, g(tw + 1.0, th * 0.62, td + 1.1, gear(OD, { stitch: 'rgba(20,22,10,.6)', scuff: true })), 0, th * 0.69, 0);
  put(k.spine, g(tw, th * 0.36, td + 0.7, gear(OD, { stitch: 'rgba(20,22,10,.6)', scuff: true })), 0, th * 0.29, 0);
  put(k.spine, g(tw + 0.4, 1.0, td + 1.4, gear('#3f4428', { weave: true })), 0, th + 0.25, -0.1);
  put(k.spine, g(0.5, th * 0.9, 0.3, gear('#2a2b22', { noise: .05 })), 0, th * 0.52, td / 2 + 0.7);
  for (const sx of [-1, 1]) {
    put(k.spine, g(2.4, 2.0, 0.6, gear('#434929', { stitch: 'rgba(20,22,10,.6)' })), sx * 1.5, th * 0.42, td / 2 + 0.75);
    put(k.spine, g(0.7, 0.5, 0.3, gear(BLACK, { noise: .05, grime: 0 })), sx * 1.5, th * 0.5, td / 2 + 1.1); // snaps
    // LBE suspenders over the vest, front and back, with a steel ring
    put(k.spine, g(0.9, th * 0.92, 0.35, gear(WEB, { weave: true, stitch: 'rgba(30,28,10,.5)' })), sx * 2.9, th * 0.55, td / 2 + 1.05);
    put(k.spine, g(0.9, th * 0.92, 0.35, gear(WEB, { weave: true, stitch: 'rgba(30,28,10,.5)' })), sx * 2.9, th * 0.55, -td / 2 - 0.75);
    put(k.spine, g(0.8, 0.8, 0.3, gear(STEEL, { noise: .15, grime: .1 })), sx * 2.9, th * 0.78, td / 2 + 1.25);
  }
  // shoulder pads on the vest
  for (const R of ['R', 'L']) put(k['sh' + R], g(S.upper[0] + 1.1, 0.9, S.upper[0] + 1.3, gear(OD, { stitch: 'rgba(20,22,10,.6)' })), 0, S.shoulder - 0.2, 0);
  // pistol belt with buckle, two ammo pouches, a canteen and a first-aid pouch
  const by = S.pelvis[1] / 2 - 0.3;
  put(k.hips, g(S.pelvis[0] + 1.2, 1.3, S.pelvis[2] + 1.2, gear(WEB, { weave: true, stitch: 'rgba(30,28,10,.5)' })), 0, by, 0);
  put(k.hips, g(1.6, 1.1, 0.35, gear(STEEL, { noise: .15, grime: .1 })), 0, by, S.pelvis[2] / 2 + 0.75);
  for (const sx of [-1, 1]) {
    put(k.hips, g(2.0, 2.2, 1.2, gear('#575636', { stitch: 'rgba(30,28,10,.5)', scuff: true })), sx * 2.2, by - 0.6, S.pelvis[2] / 2 + 1.0);
    put(k.hips, g(2.1, 0.7, 1.35, gear('#4d4c30', { weave: true })), sx * 2.2, by + 0.5, S.pelvis[2] / 2 + 1.0); // lid
    put(k.hips, g(0.5, 0.5, 0.2, gear(STEEL, { grime: .1 })), sx * 2.2, by + 0.3, S.pelvis[2] / 2 + 1.75);
  }
  put(k.hips, g(1.9, 2.6, 1.5, gear('#3a3f26', { weave: true, stitch: 'rgba(20,22,10,.6)' })), -S.pelvis[0] / 2 + 0.6, by - 1.2, -S.pelvis[2] / 2 - 0.8); // canteen cover
  put(k.hips, g(0.8, 0.6, 0.8, gear(BLACK, { grime: .1 })), -S.pelvis[0] / 2 + 0.6, by + 0.35, -S.pelvis[2] / 2 - 0.8);
  put(k.hips, g(1.6, 1.6, 1.0, gear('#4f5232', { stitch: 'rgba(20,22,10,.6)' })), S.pelvis[0] / 2 - 0.4, by - 0.9, -S.pelvis[2] / 2 - 0.6);
  // BDU uniform in woodland camo: shirt and sleeves rolled to mid-forearm, trousers with cargo pockets and knee patches
  const BDU = { camo: CAMO, stitch: 'rgba(15,18,8,.55)', noise: .09 };
  const BDU2 = { camo: CAMO, weave: true, noise: .09 };
  shell(k.spine, tw + 0.4, th * 0.6 + 0.2, td + 0.4, gear('#556b34', BDU), 0, th * 0.7, 0, 'b');
  shell(k.spine, tw - 0.6, th * 0.45, td, gear('#556b34', BDU), 0, th * 0.22, 0, 't');
  shell(k.hips, S.pelvis[0] + 0.4, S.pelvis[1] + 0.2, S.pelvis[2] + 0.4, gear('#556b34', BDU), 0, 0, 0, 'b');
  put(k.hips, g(0.4, S.pelvis[1] * 0.8, 0.3, gear('#3a4726', { weave: true })), 0, -0.2, S.pelvis[2] / 2 + 0.3);   // fly seam
  for (const R of ['R', 'L']) {
    const side = R === 'R' ? -1 : 1, sh = k['sh' + R], el = k['el' + R], hip = k['hip' + R], kn = k['kn' + R], an = k['an' + R];
    // sleeve, shoulder seam and the rolled cuff
    shell(sh, S.upper[0] + 0.75, S.upper[1] + 0.2, S.upper[0] + 0.75, gear('#556b34', BDU), side * 0.05, S.shoulder - S.upper[1] / 2 + 0.1, 0, 'b');
    put(sh, g(1.6, 1.0, 0.3, gear('#556b34', BDU2)), side * 0.0, S.shoulder - 2.2, S.upper[0] / 2 + 0.45);        // sleeve pocket
    shell(el, S.fore[0] + 0.6, S.fore[1] * 0.4, S.fore[0] + 0.6, gear('#556b34', BDU), 0, 0.2 - S.fore[1] * 0.2, 0, 't');
    put(el, g(S.fore[0] + 0.85, 1.0, S.fore[0] + 0.85, gear('#4a5c2c', BDU2)), 0, 0.2 - S.fore[1] * 0.4, 0);        // rolled cuff
    // trouser leg: thigh with cargo pocket and flap, knee patch, shin down into the boot
    shell(hip, S.thigh[0] + 0.45, S.thigh[1] + 0.4, S.thigh[0] + 0.45, gear('#556b34', BDU), 0, 0.4 - (S.thigh[1] + 0.4) / 2, 0, 'tb');
    put(hip, g(0.7, 2.8, 2.5, gear('#4f6330', BDU)), side * (S.thigh[0] / 2 + 0.5), -S.thigh[1] * 0.45, 0);
    put(hip, g(0.85, 0.8, 2.7, gear('#45572a', BDU2)), side * (S.thigh[0] / 2 + 0.55), -S.thigh[1] * 0.45 + 1.6, 0);
    put(hip, g(0.4, 0.4, 0.3, gear(BLACK, { grime: 0 })), side * (S.thigh[0] / 2 + 0.95), -S.thigh[1] * 0.45 + 1.4, 0);
    put(hip, g(S.thigh[0] - 0.4, 1.9, 0.35, gear('#4a5c2c', BDU)), 0, -S.thigh[1] + 1.0, S.thigh[0] / 2 + 0.35);  // knee patch
    const shinTop = 0.3, shinBot = -S.shin[1] + 3.0;
    shell(kn, S.shin[0] + 0.45, shinTop - shinBot, S.shin[0] + 0.45, gear('#556b34', BDU), 0, (shinTop + shinBot) / 2, 0, 't');
    put(kn, g(S.shin[0] + 0.9, 0.7, S.shin[0] + 0.9, gear('#4a5c2c', BDU2)), 0, shinBot + 0.15, 0);                 // bloused over the boot
    // jungle boot: black leather foot and heel on a thick lugged sole, toe cap, green canvas upper,
    // leather trim, lace ladder and a pull loop at the back
    const fz = S.foot[2] / 2 - S.shin[0] / 2 - 0.1, toe = fz + S.foot[2] / 2;
    put(an, g(S.foot[0] + 0.8, 0.75, S.foot[2] + 0.8, gear('#0d0c0b', { noise: .05, grime: 0 })), 0, -1.03, fz);  // sole
    put(an, g(S.foot[0] + 0.85, 0.3, S.foot[2] + 0.85, (c, W, H) => { c.fillStyle = '#0a0908'; c.fillRect(0, 0, W, H); c.fillStyle = '#26231f'; for (let x = 2; x < W; x += 5) c.fillRect(x, 0, 2, H); }), 0, -1.25, fz); // lugs
    put(an, g(S.foot[0] + 0.5, 1.5, S.foot[2] + 0.4, gear(BLACK, { scuff: true, grime: .12 })), 0, 0.05, fz);
    put(an, g(S.foot[0] + 0.65, 1.0, 1.5, gear('#141312', { scuff: true, grime: .05 })), 0, -0.2, toe - 0.6);    // toe cap
    put(an, g(S.foot[0] + 0.7, 1.2, 1.3, gear('#141312', { grime: .05 })), 0, -0.1, fz - S.foot[2] / 2 + 0.5); // heel counter
    put(an, g(1.4, 0.35, 2.3, gear('#2a2b20', { weave: true, grime: .1 })), 0, 0.85, fz + 0.5, -0.25);          // tongue
    put(kn, g(S.shin[0] + 0.6, 3.1, S.shin[0] + 0.6, gear('#3d4527', { weave: true, stitch: 'rgba(10,10,6,.6)' })), 0, -S.shin[1] + 1.45, 0);
    put(kn, g(S.shin[0] + 0.7, 0.8, S.shin[0] + 0.7, gear(BLACK, { grime: .05 })), 0, -S.shin[1] + 0.3, 0);       // leather trim
    put(kn, g(1.1, 3.0, 0.3, (c, W, H) => { c.fillStyle = '#121110'; c.fillRect(0, 0, W, H); c.fillStyle = '#6d6650'; for (let y = 2; y < H; y += 5) { c.fillRect(1, y, W - 2, 2); c.fillStyle = '#a7a7a0'; c.fillRect(0, y, 2, 2); c.fillRect(W - 2, y, 2, 2); c.fillStyle = '#6d6650'; } }), 0, -S.shin[1] + 1.5, S.shin[0] / 2 + 0.4);
    put(kn, g(0.8, 1.0, 0.35, gear(BLACK, { grime: 0 })), 0, -S.shin[1] + 3.2, -S.shin[0] / 2 - 0.4);            // pull loop
  }
}

