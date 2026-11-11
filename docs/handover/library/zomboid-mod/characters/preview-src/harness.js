class V { constructor(x=0,y=0,z=0){this.x=x;this.y=y;this.z=z;} set(x,y,z){this.x=x;this.y=y;this.z=z;return this;} copy(o){this.x=o.x;this.y=o.y;this.z=o.z;return this;} lerp(o,k){this.x+=(o.x-this.x)*k;this.y+=(o.y-this.y)*k;this.z+=(o.z-this.z)*k;return this;} setScalar(s){return this.set(s,s,s);} }
class O { constructor(){this.position=new V();this.rotation=new V();this.scale=new V(1,1,1);this.children=[];this.userData={};this.parent=null;} add(...c){c.forEach(x=>{x.parent=this;this.children.push(x)});return this;} remove(c){this.children=this.children.filter(x=>x!==c);} traverse(f){f(this);this.children.forEach(c=>c.traverse(f));} lookAt(){} }
for (const k of ['x','y','z']) {}
const ctx = new Proxy({}, { get: (t,p)=> (p==='createLinearGradient'||p==='createRadialGradient')? ()=>({addColorStop(){}}) : p==='getImageData' ? (x,y,w,h)=>({data:new Uint8ClampedArray(w*h*4).fill(230)}) : (p in t ? t[p] : ()=>{}), set:(t,p,v)=>{t[p]=v;return true;} });
const el = () => ({ addEventListener(){}, setPointerCapture(){}, querySelectorAll:()=>[], clientWidth: 800, clientHeight: 500, style: {}, width: 0, height: 0, getContext: () => ctx, textContent:'' });
global.document = { createElement: el, getElementById: el, querySelectorAll: () => [] };
global.Image = class { set src(v) { this._s = v; setTimeout(() => { this.width = this.height = 1024; this.onload && this.onload(); }, 0); } };
global.matchMedia = () => ({ matches: false }); global.window = { devicePixelRatio: 1 }; global.devicePixelRatio = 1;
let frames = 0; global.requestAnimationFrame = f => { if (frames++ < 3) f(); };
class Geo { constructor(){this.attributes={};} setAttribute(k,v){this.attributes[k]=v;} setIndex(){} computeVertexNormals(){} dispose(){} }
global.THREE = {
  Vector3: V, Group: O, Object3D: O, Scene: class extends O {}, Mesh: class extends O { constructor(g,m){super();this.geometry=g;this.material=m;} },
  BufferGeometry: Geo, Float32BufferAttribute: class {}, BoxGeometry: class extends Geo { constructor(w,h,d){super();this.parameters={width:w,height:h,depth:d};} },
  PlaneGeometry: Geo, MeshLambertMaterial: class { constructor(o){Object.assign(this,o);} dispose(){} }, CanvasTexture: class { constructor(){this.repeat=new V();} dispose(){} },
  TextureLoader: class { load(){ return {}; } }, Color: class {}, Fog: class {}, HemisphereLight: O, PointLight: O, LineSegments: class extends O { constructor(g,m){super();this.geometry=g;this.material=m;} }, LineBasicMaterial: class { dispose(){} }, MeshBasicMaterial: class { constructor(o){Object.assign(this,o);} dispose(){} }, MeshPhongMaterial: class { constructor(o){Object.assign(this,o);} dispose(){} }, FogExp2: class {}, Sprite: class extends O {}, SpriteMaterial: class { constructor(o){Object.assign(this,o);} dispose(){} }, AdditiveBlending: 2, BufferAttribute: class { constructor(a){this.array=a;} }, Vector2: class {}, ACESFilmicToneMapping: 4, NoToneMapping: 0,
  DirectionalLight: class extends O { constructor(){super();this.shadow={mapSize:new V(),camera:{}};} },
  WebGLRenderer: class { constructor(){this.shadowMap={};} setPixelRatio(){} getPixelRatio(){return 1;} setSize(){} render(){} },
  PerspectiveCamera: class extends O { updateProjectionMatrix(){} }, Clock: class { getElapsedTime(){return 1.3;} getDelta(){return .016;} get elapsedTime(){return 1.3;} },
  NearestFilter:0, LinearMipmapLinearFilter:0, sRGBEncoding:0, RepeatWrapping:0, FrontSide:0, DoubleSide:2, PCFSoftShadowMap:0,
};
require(process.argv[2]); console.log('ran', frames, 'frames ok');
