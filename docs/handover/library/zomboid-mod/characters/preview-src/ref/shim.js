// minimal THREE stand-in: scene graph + geometry data only; drawing is done by the software rasteriser below
(function () {
  const M4 = {
    id: () => [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1],
    mul(a, b) { const r = new Array(16); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) { let s = 0; for (let k = 0; k < 4; k++) s += a[i*4+k] * b[k*4+j]; r[i*4+j] = s; } return r; },
    trs(p, e, s) {
      const cx = Math.cos(e.x), sx = Math.sin(e.x), cy = Math.cos(e.y), sy = Math.sin(e.y), cz = Math.cos(e.z), sz = Math.sin(e.z);
      const Rx = [1,0,0,0, 0,cx,-sx,0, 0,sx,cx,0, 0,0,0,1], Ry = [cy,0,sy,0, 0,1,0,0, -sy,0,cy,0, 0,0,0,1], Rz = [cz,-sz,0,0, sz,cz,0,0, 0,0,1,0, 0,0,0,1];
      const ord = e.order || 'XYZ', R0 = { X: Rx, Y: Ry, Z: Rz };
      let R = M4.mul(M4.mul(R0[ord[0]], R0[ord[1]]), R0[ord[2]]);
      R = M4.mul(R, [s.x,0,0,0, 0,s.y,0,0, 0,0,s.z,0, 0,0,0,1]);
      R[3] = p.x; R[7] = p.y; R[11] = p.z; return R;
    },
    ap: (m, v) => [m[0]*v[0]+m[1]*v[1]+m[2]*v[2]+m[3], m[4]*v[0]+m[5]*v[1]+m[6]*v[2]+m[7], m[8]*v[0]+m[9]*v[1]+m[10]*v[2]+m[11]],
  };
  class V3 { constructor(x = 0, y = 0, z = 0) { this.x = x; this.y = y; this.z = z; } set(x, y, z) { this.x = x; this.y = y; this.z = z; return this; } copy(v) { return this.set(v.x, v.y, v.z); } lerp() { return this; } clone() { return new V3(this.x, this.y, this.z); } add(v) { this.x += v.x; this.y += v.y; this.z += v.z; return this; } }
  class Eu { constructor() { this.x = this.y = this.z = 0; this.order = 'XYZ'; } set(x, y, z, o) { this.x = x; this.y = y; this.z = z; if (o) this.order = o; return this; } }
  class O3 {
    constructor() { this.position = new V3(); this.rotation = new Eu(); this.scale = new V3(1, 1, 1); this.children = []; this.parent = null; this.userData = {}; this.visible = true; }
    add(...o) { for (const c of o) { if (c.parent) c.parent.remove(c); c.parent = this; this.children.push(c); } return this; }
    remove(c) { const i = this.children.indexOf(c); if (i >= 0) this.children.splice(i, 1); c.parent = null; return this; }
    traverse(f) { f(this); for (const c of this.children.slice()) c.traverse(f); }
    lookAt() {}
  }
  class Mesh extends O3 { constructor(g, m) { super(); this.geometry = g; this.material = m; this.isMesh = true; } }
  class BoxGeometry { constructor(w, h, d) { this.parameters = { width: w, height: h, depth: d }; this.box = true; } dispose() {} }
  class BufferGeometry { constructor() { this.attributes = {}; } setAttribute(n, a) { this.attributes[n] = a; } setIndex(i) { this.index = i; } computeVertexNormals() {} dispose() {} }
  class BA { constructor(a, n) { this.array = a; this.itemSize = n; } }
  class Mat { constructor(o = {}) { Object.assign(this, { alphaTest: 0, opacity: 1 }, o); if (typeof this.color === 'number') this.color = new Col(this.color); } dispose() {} }
  class Col { constructor(c = 0xffffff) { this.n = typeof c === 'number' ? c : 0xffffff; } }
  class Tex { constructor(img) { this.image = img; this.repeat = { set() {} }; } dispose() {} }
  const LOADS = [];
  class TL { load(src) { const i = new Image(); LOADS.push(new Promise(r => { i.onload = r; i.onerror = r; })); i.src = src; return new Tex(i); } }
  class Light extends O3 { constructor() { super(); this.shadow = { mapSize: { set() {} }, camera: {} }; } }
  window.THREE = {
    Vector3: V3, Object3D: O3, Group: O3, Scene: O3, Mesh, Sprite: O3, LineSegments: O3, BoxGeometry, PlaneGeometry: BoxGeometry, BufferGeometry,
    BufferAttribute: BA, Float32BufferAttribute: BA, MeshLambertMaterial: Mat, MeshBasicMaterial: Mat, MeshPhongMaterial: Mat, SpriteMaterial: Mat, LineBasicMaterial: Mat,
    Color: Col, CanvasTexture: Tex, TextureLoader: TL, DirectionalLight: Light, PointLight: Light, HemisphereLight: Light, AmbientLight: Light,
    Fog: class {}, FogExp2: class {}, Clock: class { getDelta() { return 0; } getElapsedTime() { return 0; } },
    NearestFilter: 1, LinearMipmapLinearFilter: 2, sRGBEncoding: 3, RepeatWrapping: 4, DoubleSide: 2, FrontSide: 0, AdditiveBlending: 5, ACESFilmicToneMapping: 6, NoToneMapping: 0, PCFSoftShadowMap: 7,
  };
  window.__M4 = M4; window.__LOADS = LOADS;
})();
