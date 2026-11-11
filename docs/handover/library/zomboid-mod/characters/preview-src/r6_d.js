
// ---------------------------------------------------------------- WebGL drawing
const canvas = document.getElementById('view'), stage = document.getElementById('stage');
const gl = canvas.getContext('webgl', { antialias: true, preserveDrawingBuffer: true });
if (!gl) { const d = document.createElement('div'); d.className = 'nogl'; d.textContent = 'This view needs WebGL, which this browser has turned off.'; stage.appendChild(d); return; }
const DERIV = !!gl.getExtension('OES_standard_derivatives');
const HIGHP = '#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n';
function shader(type, src) { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; }
function program(vs, fs) { const p = gl.createProgram(); gl.attachShader(p, shader(gl.VERTEX_SHADER, vs)); gl.attachShader(p, shader(gl.FRAGMENT_SHADER, fs)); gl.linkProgram(p); if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p)); return p; }
function locs(p, names) { const o = {}; for (const n of names) { const a = gl.getAttribLocation(p, n); o[n] = a >= 0 ? a : gl.getUniformLocation(p, n); } return o; }
const BACKDROP = `
uniform vec2 uRes; uniform vec3 uBg0; uniform vec3 uBg1;
vec3 backdrop(vec2 sc) { vec2 q = (sc - vec2(0.48, 0.62)) * vec2(uRes.x / uRes.y, 1.0) * 0.85; return mix(uBg0, uBg1, smoothstep(0.0, 1.0, length(q))); }`;
const NOISE = `
float h31(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float vn(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(h31(i), h31(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 0.0)), h31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
             mix(mix(h31(i + vec3(0.0, 0.0, 1.0)), h31(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h31(i + vec3(0.0, 1.0, 1.0)), h31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
float fbm(vec3 p) { float s = 0.0, a = 0.5; for (int k = 0; k < 4; k++) { s += a * vn(p); p = p * 2.03 + 1.7; a *= 0.5; } return s; }
vec2 cells(vec3 p) { vec3 i = floor(p), f = fract(p); float d = 9.0, r = 0.0;
  for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
    vec3 g = vec3(float(x), float(y), float(z)), c = i + g, q = g + vec3(h31(c), h31(c + 19.1), h31(c + 47.3)) - f;
    float e = dot(q, q); if (e < d) { d = e; r = h31(c + 71.7); } }
  return vec2(sqrt(d), r); }`;
const prog = program(`
attribute vec3 aP; attribute vec3 aN; attribute vec3 aC; attribute vec4 aM; attribute vec2 aA;
uniform mat4 uVP; uniform mat4 uModel;
varying vec3 vW; varying vec3 vL; varying vec3 vN; varying vec3 vC; varying vec4 vM; varying vec2 vA;
void main() { vec4 w = uModel * vec4(aP, 1.0); vW = w.xyz; vL = aP; vN = (uModel * vec4(aN, 0.0)).xyz; vC = aC; vM = aM; vA = aA; gl_Position = uVP * w; }`,
(DERIV ? '#extension GL_OES_standard_derivatives : enable\n#define DERIV 1\n' : '') + HIGHP + `
varying vec3 vW; varying vec3 vL; varying vec3 vN; varying vec3 vC; varying vec4 vM; varying vec2 vA;
uniform vec3 uCam; uniform vec3 uKey; uniform vec3 uKeyC; uniform vec3 uRim; uniform vec3 uRimC; uniform vec3 uFill;
uniform float uTorch; uniform float uGain; uniform vec3 uSpot; uniform vec3 uSpotDir; uniform vec2 uCone;
uniform sampler2D uShadow; uniform mat4 uLVP; uniform float uShTexel; uniform float uShOn;
uniform float uFeed; uniform float uPulse; uniform vec3 uEye; uniform vec3 uEyeC;
uniform vec3 uCenter; uniform float uFogR; uniform vec4 uDark;` + BACKDROP + NOISE + `
float unpack(vec4 c) { return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0)); }
float shadowAt(vec3 N) {
  if (uShOn < 0.5) return 1.0;
  vec3 c = (uLVP * vec4(vW + N * 0.025, 1.0)).xyz * 0.5 + 0.5;
  if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
  float s = 0.0;
  for (int k = 0; k < 12; k++) { float a = float(k) * 2.39996, r = sqrt((float(k) + 0.5) / 12.0);
    s += step(c.z - 0.0009, unpack(texture2D(uShadow, c.xy + vec2(cos(a), sin(a)) * r * uShTexel * 6.0))); }
  return s / 12.0;
}
void main() {
  float pid = floor(vM.z + 0.001), pv = vM.z - pid;
  vec3 alb = vC * vC; float metal = vM.x, rough = vM.y, glow = vM.w, ao = vA.x, edge = vA.y;
  vec3 N = normalize(vN); if (!gl_FrontFacing) N = -N;
  vec3 q = vL; float bump = 0.0; vec3 emit = vec3(0.0);
  if (pid > 0.5 && pid < 1.5) {           // armour plate: holes, rust, blood, worn edges
    float fw = 0.0;
#ifdef DERIV
    fw = length(fwidth(q * 15.0));
#endif
    vec2 c = cells(q * 15.0); float hr = 0.1 + 0.11 * c.y;
    if (c.y < 0.36 && fw < 0.7 && c.x < hr) discard;
    float rim = c.y < 0.36 ? 1.0 - smoothstep(hr, hr + 0.14, c.x) : 0.0;
    vec2 c2 = cells(q * 3.0 + 3.7); float rg = c2.x + 0.13 * vn(q * 38.0);
    if (c2.y < 0.11 && rg < 0.2) discard;
    rim = max(rim, c2.y < 0.11 ? 1.0 - smoothstep(0.2, 0.3, rg) : 0.0);
    float n1 = fbm(q * 3.0 + pv * 9.0), rust = smoothstep(0.56, 0.8, n1 + pv * 0.22 - edge * 0.2);
    vec3 rc = mix(vec3(0.09), vec3(0.13), vn(q * 22.0));           // all black: wear shows as dull patches, not rust
    float blood = smoothstep(0.6, 0.78, fbm(q * 4.5 + 11.0)) * (1.0 - rust);
    alb = mix(alb, rc * rc, rust * 0.75);
    alb = mix(alb, vec3(0.1, 0.01, 0.008), blood * 0.85);
    metal = mix(metal, 0.15, max(rust, blood)); rough = mix(rough, 0.85, rust); rough = mix(rough, 0.22, blood * 0.7);
    float scr = smoothstep(0.86, 0.97, vn(q * vec3(55.0, 3.0, 55.0)));
    alb = mix(alb, vec3(0.2), scr * 0.3 * (1.0 - rust));
    alb *= 1.0 - 0.6 * rim;
    alb = mix(alb, vec3(0.16), edge * 0.3 * (1.0 - rust)); rough = mix(rough, 0.3, edge * 0.4);
    bump = 0.004 * fbm(q * 16.0) - 0.006 * rim + 0.0012 * vn(q * 95.0);
  } else if (pid > 1.5 && pid < 2.5) {    // steel: streaks along the piece, grime, worn edges
    float st = vn(q * vec3(3.0, 60.0, 60.0)), rust = smoothstep(0.58, 0.8, fbm(q * 4.0 + pv * 6.0));
    alb *= 0.82 + 0.36 * st;
    alb = mix(alb, vec3(0.008), rust * 0.85);
    alb = mix(alb, vec3(0.005), smoothstep(0.66, 0.8, fbm(q * 6.0 + 4.0)) * 0.7);
    metal = mix(metal, 0.25, rust); rough = mix(rough, 0.8, rust) * (0.85 + 0.3 * st);
    alb = mix(alb, vec3(0.16), edge * 0.35);
    bump = 0.0015 * st + 0.003 * fbm(q * 22.0);
  } else if (pid > 2.5 && pid < 3.5) {    // wet red sinew, glowing when fed
    float f1 = vn(q * vec3(10.0, 10.0, 34.0) + vn(q * 5.0) * 2.0), ridge = 1.0 - abs(2.0 * f1 - 1.0);
    alb = mix(alb * 0.35, alb * 1.35, ridge); rough = mix(0.5, 0.2, ridge);
    emit += vec3(1.0, 0.06, 0.035) * glow * (1.0 + uFeed * (6.0 + 4.0 * uPulse)) * (0.3 + ridge);
    bump = 0.007 * ridge;
  } else if (pid > 3.5 && pid < 4.5) {    // the skull under the face plates: molten, white hot in the cracks, cooler in the hollows
    float n1 = fbm(q * 6.0), cr = 1.0 - smoothstep(0.0, 0.06, abs(vn(q * 18.0 + n1 * 2.0) - 0.5));
    alb = vec3(0.35, 0.12, 0.03) * (0.7 + 0.5 * n1);
    rough = 0.45 + 0.3 * n1; metal = 0.0;
    float heat = (0.5 + 0.3 * n1 + 0.6 * cr) * (0.12 + 0.88 * ao * ao);
    emit += mix(vec3(1.0, 0.3, 0.035), vec3(1.0, 0.75, 0.35), cr * 0.85) * glow * heat * (1.0 + 0.25 * uPulse);
    bump = 0.004 * n1 + 0.0015 * vn(q * 85.0) - 0.003 * cr;
  } else if (pid > 4.5 && pid < 5.5) {    // rubber hose
    alb *= 0.8 + 0.4 * vn(q * 30.0);
  } else if (pid > 5.5 && pid < 6.5) {    // cloth, stained
    float w = vn(q * 85.0); alb *= 0.82 + 0.3 * w; bump = 0.0015 * w;
    alb = mix(alb, vec3(0.1, 0.012, 0.01), smoothstep(0.52, 0.75, fbm(q * 4.0 + pv * 9.0)) * pv);
  } else if (pid > 6.5 && pid < 7.5) {    // dark concrete, damp in patches
    float n1 = fbm(q * 1.2), n2 = vn(q * 26.0), wet = smoothstep(0.42, 0.7, fbm(q * 0.45 + 5.0));
    alb *= 0.62 + 0.5 * n1 + 0.16 * n2; alb *= 1.0 - 0.3 * wet;
    rough = mix(0.78, 0.3, wet);
    bump = 0.0025 * n2 + 0.002 * fbm(q * 9.0);
  } else if (pid > 7.5 && pid < 8.5) {    // drained skin
    float v = fbm(q * 9.0);
    alb *= 0.78 + 0.32 * v;
    alb = mix(alb, vec3(0.15, 0.17, 0.2), smoothstep(0.5, 0.53, vn(q * vec3(24.0, 7.0, 24.0))) * 0.35);
  } else if (pid > 8.5) {                 // eyes
    emit += alb * glow * (1.0 + uFeed * 0.5);
  }
#ifdef DERIV
  vec3 dpx = dFdx(vW), dpy = dFdy(vW); float dhx = dFdx(bump), dhy = dFdy(bump);
  vec3 r1 = cross(dpy, N), r2 = cross(N, dpx); float det = dot(dpx, r1);
  if (abs(det) > 1e-12) N = normalize(abs(det) * N - sign(det) * (dhx * r1 + dhy * r2));
#endif
  vec3 V = normalize(uCam - vW);
  float NdV = max(dot(N, V), 0.0);
  vec3 F0 = mix(vec3(0.04), alb, metal), dif = alb * (1.0 - metal);
  float r2s = max(rough * rough, 0.003), sp = 2.0 / r2s - 2.0, nrm = (sp + 8.0) / 25.13;
  vec3 Fv = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - NdV, 5.0);
  vec3 te = uEye - vW; float de = dot(te, te);
  vec3 eyeL = (dif + F0) * uEyeC * max(dot(N, te * inversesqrt(de)), 0.0) / (1.0 + de * 160.0);
  // studio: key with soft shadow, cool rim behind, fill, and a softbox in the reflections
  float sh = shadowAt(N);
  vec3 L = uKey, H = normalize(L + V); float nl = max(dot(N, L), 0.0);
  vec3 c = (dif * nl + F0 * pow(max(dot(N, H), 0.0), sp) * nrm * nl) * uKeyC * sh;
  L = uRim; H = normalize(L + V); nl = max(dot(N, L), 0.0);
  c += (dif * nl * 0.5 + F0 * pow(max(dot(N, H), 0.0), sp) * nrm * nl) * uRimC;
  vec3 R = reflect(-V, N);
  vec3 env = mix(vec3(0.01), vec3(0.15), smoothstep(-0.3, 0.9, R.y)) + uKeyC * 0.28 * pow(max(dot(R, uKey), 0.0), 2.0 + 24.0 * (1.0 - rough)) * (0.4 + 0.6 * sh);
  c += dif * uFill * (0.35 + 0.65 * (0.5 + 0.5 * N.y)) * ao + Fv * env * ao * (1.0 - 0.55 * rough);
  c += eyeL;
  // torch from the camera
  vec3 toP = vW - uSpot; float dist = length(toP); vec3 dir = toP / dist;
  float cone = smoothstep(uCone.x, uCone.y, dot(dir, uSpotDir));
  L = -dir; H = normalize(L + V); nl = max(dot(N, L), 0.0);
  vec3 tc = (dif * nl + F0 * pow(max(dot(N, H), 0.0), sp) * nrm * nl) * vec3(1.3, 1.15, 0.95) * cone * (4.0 * uGain / (1.0 + 0.045 * dist * dist));
  tc += dif * 0.006 * ao + Fv * 0.012 * ao + eyeL;
  // the room's dark corner swallows the light, but not what glows
  float dk = uDark.w > 0.0 ? mix(0.05, 1.0, smoothstep(uDark.w * 0.3, uDark.w, length((vW - uDark.xyz) * vec3(1.0, 0.45, 1.0)))) : 1.0;
  c = mix(c, tc, uTorch) * mix(dk, 1.0, uTorch * 0.6) + emit;
  c = (c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14);
  c = pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2));
  float fd = max(0.0, length(vW.xz - uCenter.xz) - uFogR);
  c = mix(c, backdrop(gl_FragCoord.xy / uRes), 1.0 - exp(-fd * fd * 0.025));
  gl_FragColor = vec4(c, 1.0);
}`);
const L0 = locs(prog, ['aP', 'aN', 'aC', 'aM', 'aA', 'uVP', 'uModel', 'uCam', 'uKey', 'uKeyC', 'uRim', 'uRimC', 'uFill', 'uTorch', 'uGain', 'uSpot', 'uSpotDir', 'uCone',
  'uShadow', 'uLVP', 'uShTexel', 'uShOn', 'uFeed', 'uPulse', 'uEye', 'uEyeC', 'uCenter', 'uFogR', 'uDark', 'uRes', 'uBg0', 'uBg1']);
const shProg = program(`
attribute vec3 aP; uniform mat4 uVP; uniform mat4 uModel;
void main() { gl_Position = uVP * uModel * vec4(aP, 1.0); }`, HIGHP + `
vec4 pack(float d) { vec4 e = fract(d * vec4(1.0, 255.0, 65025.0, 16581375.0)); e -= e.yzww * vec4(1.0 / 255.0, 1.0 / 255.0, 1.0 / 255.0, 0.0); return e; }
void main() { gl_FragColor = pack(gl_FragCoord.z); }`);
const LS = locs(shProg, ['aP', 'uVP', 'uModel']);
const bgProg = program(`
attribute vec2 aP; void main() { gl_Position = vec4(aP, 0.999, 1.0); }`, HIGHP + BACKDROP + `
float h21(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() { gl_FragColor = vec4(backdrop(gl_FragCoord.xy / uRes) + (h21(gl_FragCoord.xy) - 0.5) / 255.0, 1.0); }`);
const LB = locs(bgProg, ['aP', 'uRes', 'uBg0', 'uBg1']);
// glows: soft billboards round the eyes
const glowProg = program(`
attribute vec3 aP; attribute vec4 aQ; uniform mat4 uVP; uniform vec3 uR; uniform vec3 uU; varying vec2 vQ; varying float vI;
void main() { vQ = aQ.xy; vI = aQ.w; gl_Position = uVP * vec4(aP + (uR * aQ.x + uU * aQ.y) * aQ.z, 1.0); }`, HIGHP + `
varying vec2 vQ; varying float vI;
void main() { float r = dot(vQ, vQ); if (r > 1.0) discard; gl_FragColor = vec4(vec3(1.0, 0.42, 0.08) * vI * exp(-r * 4.0) * (1.0 - r), 1.0); }`);
const LG = locs(glowProg, ['aP', 'aQ', 'uVP', 'uR', 'uU']);
// breath from the mouth and dust in the air: soft points
const mistProg = program(`
attribute vec3 aP; attribute float aA; uniform mat4 uVP; uniform vec3 uSpot; uniform vec3 uSpotDir; uniform float uTorch; uniform float uPx; uniform float uSize; varying float vA;
void main() { vec4 p = uVP * vec4(aP, 1.0); gl_Position = p;
  float cone = smoothstep(0.85, 0.97, dot(normalize(aP - uSpot), uSpotDir));
  vA = aA * mix(1.0, 0.25 + 1.2 * cone, uTorch); gl_PointSize = uPx * uSize / max(p.w, 0.5); }`, HIGHP + `
varying float vA; uniform vec3 uTint;
void main() { vec2 q = gl_PointCoord - 0.5; float r = dot(q, q) * 4.0; if (r > 1.0) discard; gl_FragColor = vec4(uTint * vA * (1.0 - r) * (1.0 - r), 1.0); }`);
const LM = locs(mistProg, ['aP', 'aA', 'uVP', 'uSpot', 'uSpotDir', 'uTorch', 'uPx', 'uSize', 'uTint']);

// meshes to the GPU; the moving bits go up again every frame into reused buffers
function toGPU(mesh, pool, usage) {
  const out = [];
  mesh.chunks.forEach((c, i) => {
    if (!c.ix.length) return;
    const b = pool && pool[i] ? pool[i] : { vb: gl.createBuffer(), ib: gl.createBuffer() };
    if (pool) pool[i] = b;
    gl.bindBuffer(gl.ARRAY_BUFFER, b.vb); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(c.v), usage);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, b.ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(c.ix), usage);
    out.push({ vb: b.vb, ib: b.ib, n: c.ix.length });
  });
  return out;
}
const GPU = {};
for (const k in MESHES) GPU[k] = toGPU(MESHES[k], null, gl.STATIC_DRAW);
document.documentElement.dataset.tris = Object.entries(MESHES).map(([k, m]) => k + ':' + m.chunks.reduce((s, c) => s + c.ix.length / 3, 0)).join(' ');
for (const k in MESHES) MESHES[k].chunks = null;
const dynPool = { hunt: [], feed: [], room: [] };
const ROOMM = buildRoom(), ROOMG = { floor: toGPU(ROOMM.floor, null, gl.STATIC_DRAW), ceiling: toGPU(ROOMM.ceiling, null, gl.STATIC_DRAW), props: toGPU(ROOMM.props, null, gl.STATIC_DRAW), walls: ROOMM.walls.map(w => ({ g: toGPU(w.m, null, gl.STATIC_DRAW), n: w.n, pt: w.pt })) };
const quadBuf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, quadBuf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
const glowBuf = gl.createBuffer(), mistBuf = gl.createBuffer(), dustBuf = gl.createBuffer();
const MIST = 46, mistArr = new Float32Array(MIST * 4), DUST = 280, dustArr = new Float32Array(DUST * 4), dustSeed = [];
for (let i = 0; i < DUST; i++) dustSeed.push([hash(i) * 24 - 12, hash(i + 500) * 4.2 + 0.1, hash(i + 900) * 16 - 8, hash(i + 1300) * 6.28]);
// the shadow map: depth from the key light, packed into colour
const SHN = 2048, shTex = gl.createTexture();
gl.bindTexture(gl.TEXTURE_2D, shTex); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, SHN, SHN, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
for (const [p, v] of [[gl.TEXTURE_MIN_FILTER, gl.NEAREST], [gl.TEXTURE_MAG_FILTER, gl.NEAREST], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, p, v);
const shFB = gl.createFramebuffer(), shRB = gl.createRenderbuffer();
gl.bindFramebuffer(gl.FRAMEBUFFER, shFB); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, shTex, 0);
gl.bindRenderbuffer(gl.RENDERBUFFER, shRB); gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT16, SHN, SHN); gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, shRB);
const shOK = gl.checkFramebufferStatus(gl.FRAMEBUFFER) === gl.FRAMEBUFFER_COMPLETE;
gl.bindFramebuffer(gl.FRAMEBUFFER, null);

// ---------------------------------------------------------------- camera
function perspective(fovy, aspect, near, far) { const f = 1 / Math.tan(fovy / 2), nf = 1 / (near - far); return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) * nf, -1, 0, 0, 2 * far * near * nf, 0]; }
function ortho(l, r, b, t, n, f) { return [2 / (r - l), 0, 0, 0, 0, 2 / (t - b), 0, 0, 0, 0, -2 / (f - n), 0, -(r + l) / (r - l), -(t + b) / (t - b), -(f + n) / (f - n), 1]; }
function lookAt(e, c) { const z = norm(sub(e, c)), x = norm(cross(Y, z)), y = cross(z, x); return [x[0], y[0], z[0], 0, x[1], y[1], z[1], 0, x[2], y[2], z[2], 0, -dot(x, e), -dot(y, e), -dot(z, e), 1]; }
function m4mul(a, b) { const r = new Array(16); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + j] * b[i * 4 + k]; r[i * 4 + j] = s; } return r; }
const m4of = f => [f.R[0][0], f.R[1][0], f.R[2][0], 0, f.R[0][1], f.R[1][1], f.R[2][1], 0, f.R[0][2], f.R[1][2], f.R[2][2], 0, f.o[0], f.o[1], f.o[2], 1];
const I4 = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const HOME = { hunt: [-3.9, 0], feed: [3.9, 0] };
const VIEW = { both: { d: 18, ty: 1.8 }, hunt: { d: 9.6, ty: 1.8 }, feed: { d: 9.6, ty: 1.75 }, room: { d: 22, ty: 2.2 } };
const ANGLES = { q: { yaw: 0.62, pitch: 0.06 }, side: { yaw: Math.PI / 2, pitch: 0.05 }, front: { yaw: 0, pitch: 0.06 } };
const cam = { yaw: 0, pitch: 0.1, d: 9.6, t: [0, 1.85, 0], goal: null };
const state = { view: 'hunt', motion: 'idle', light: 'studio', player: true, angle: 'q', follow: false };
const walkers = { hunt: makeWalker(HOME.hunt, 0.35, false), feed: makeWalker(HOME.feed, -0.35, true) };
const heading = () => state.view === 'both' || state.view === 'room' ? 0 : walkers[state.view].yaw;
function focus() {
  if (state.view === 'both') return [0, VIEW.both.ty, 0.3];
  if (state.view === 'room') { const f = ROOMSIM.frame; return state.follow && f ? f.body.o.slice() : [0, VIEW.room.ty, 0]; }
  const w = walkers[state.view];
  return [w.x + Math.sin(w.yaw) * 0.45, VIEW[state.view].ty, w.z + Math.cos(w.yaw) * 0.45];
}
function fitScale() { const r = canvas.clientWidth / Math.max(1, canvas.clientHeight); return state.view === 'both' || state.view === 'room' ? Math.max(1, 1.5 / r) : Math.max(1, 1.2 / r); }
function aim(instant) {
  const A = ANGLES[state.angle];
  const room = state.view === 'room', d = room && state.follow ? 11 : VIEW[state.view].d;
  const g = { t: focus(), d: d * fitScale(), yaw: A ? heading() + A.yaw : cam.yaw, pitch: A ? (room ? A.pitch + 0.42 : A.pitch) : cam.pitch };
  if (instant) { cam.t = g.t; cam.d = g.d; cam.yaw = g.yaw; cam.pitch = g.pitch; cam.goal = null; } else cam.goal = g;
}

// ---------------------------------------------------------------- controls
function press(sel, attr, val) { document.querySelectorAll(sel).forEach(b => b.setAttribute('aria-pressed', String(b.dataset[attr] === val))); }
const pointers = new Map(); let pinch0 = 0, d0 = 0, tap0 = null;
canvas.addEventListener('pointerdown', e => { canvas.setPointerCapture(e.pointerId); tap0 = pointers.size ? null : { x: e.clientX, y: e.clientY, t: performance.now() }; pointers.set(e.pointerId, [e.clientX, e.clientY]); if (pointers.size === 2) { const [a, b] = [...pointers.values()]; pinch0 = Math.hypot(a[0] - b[0], a[1] - b[1]); d0 = cam.d; } cam.goal = null; });
canvas.addEventListener('pointermove', e => {
  if (!pointers.has(e.pointerId)) return;
  const p = pointers.get(e.pointerId), dx = e.clientX - p[0], dy = e.clientY - p[1]; pointers.set(e.pointerId, [e.clientX, e.clientY]);
  if (tap0 && Math.hypot(e.clientX - tap0.x, e.clientY - tap0.y) > 7) tap0 = null;
  if (pointers.size === 1 && (dx || dy) && !tap0) { cam.yaw -= dx * 0.008; cam.pitch = clamp(cam.pitch + dy * 0.006, -0.05, 1.3); if (state.angle) { state.angle = null; press('[data-angle]', 'angle', ''); } }
  else if (pointers.size === 2) { const [a, b] = [...pointers.values()], q = Math.hypot(a[0] - b[0], a[1] - b[1]); if (pinch0 > 0) cam.d = clamp(d0 * pinch0 / q, 3, 34); }
});
const up = e => {
  if (e.type === 'pointerup' && tap0 && pointers.size === 1 && state.view === 'room' && performance.now() - tap0.t < 450) noiseAt(e.clientX, e.clientY);
  tap0 = null; pointers.delete(e.pointerId); pinch0 = 0;
};
// the ray from the camera through a spot on the screen
function noiseAt(cx, cy) {
  const rc = canvas.getBoundingClientRect(), x = (cx - rc.left) / rc.width * 2 - 1, y = 1 - (cy - rc.top) / rc.height * 2;
  const eye = add(cam.t, [cam.d * Math.cos(cam.pitch) * Math.sin(cam.yaw), cam.d * Math.sin(cam.pitch), cam.d * Math.cos(cam.pitch) * Math.cos(cam.yaw)]);
  const f = norm(sub(cam.t, eye)), r = norm(cross(f, Y)), u = cross(r, f), tf = Math.tan(0.33), asp = rc.width / Math.max(1, rc.height);
  tapRoom(eye, norm(add(f, add(mul(r, x * tf * asp), mul(u, y * tf)))));
}
canvas.addEventListener('pointerup', up); canvas.addEventListener('pointercancel', up);
canvas.addEventListener('wheel', e => { e.preventDefault(); cam.d = clamp(cam.d * Math.exp(e.deltaY * 0.0012), 3, 34); if (cam.goal) cam.goal.d = cam.d; }, { passive: false });
document.querySelectorAll('[data-view]').forEach(b => b.addEventListener('click', () => { state.view = b.dataset.view; press('[data-view]', 'view', state.view); tags(); aim(false); }));
const tf_ = document.getElementById('t-follow');
tf_.addEventListener('click', () => { state.follow = !state.follow; tf_.setAttribute('aria-pressed', String(state.follow)); aim(false); });
document.getElementById('t-reset').addEventListener('click', () => { resetRoom(); aim(false); });
document.querySelectorAll('[data-angle]').forEach(b => b.addEventListener('click', () => { state.angle = b.dataset.angle; press('[data-angle]', 'angle', state.angle); aim(false); }));
document.querySelectorAll('[data-motion]').forEach(b => b.addEventListener('click', () => { state.motion = b.dataset.motion; press('[data-motion]', 'motion', state.motion); for (const w of Object.values(walkers)) resetWalker(w); if (state.angle) aim(false); }));
document.querySelectorAll('[data-light]').forEach(b => b.addEventListener('click', () => { state.light = b.dataset.light; press('[data-light]', 'light', state.light); }));
const tp = document.getElementById('t-player');
tp.addEventListener('click', () => { state.player = !state.player; tp.setAttribute('aria-pressed', String(state.player)); });
function tags() {
  const room = state.view === 'room';
  document.getElementById('tagL').hidden = state.view === 'feed' || room; document.getElementById('tagR').hidden = state.view === 'hunt' || room;
  document.getElementById('mind').hidden = !room; document.getElementById('roombar').hidden = !room;
  document.getElementById('hint').textContent = room ? 'Tap to make a noise · drag to turn · pinch to zoom' : 'Drag to turn · scroll or pinch to zoom';
}
// what it is thinking, under the view
const MIND_EL = { act: document.getElementById('mind-act'), why: document.getElementById('mind-why'), bars: [...document.querySelectorAll('#mind [data-need]')] };
let mindShown = '';
function showMind() {
  const m = ROOMSIM.mind; if (!m) return;
  const key = m.act + '|' + m.why;
  if (key !== mindShown) { MIND_EL.act.textContent = m.act; MIND_EL.why.textContent = m.why; mindShown = key; }
  for (const b of MIND_EL.bars) b.style.width = Math.round(clamp(m[b.dataset.need], 0, 1) * 100) + '%';
}

// URL hash for the offline check: #v=hunt&a=side&m=walk&l=torch&t=3.2&yaw=..&pitch=..&d=..
const H0 = new URLSearchParams(location.hash.slice(1));
if (H0.get('v')) { state.view = H0.get('v'); press('[data-view]', 'view', state.view); }
if (H0.get('a')) { state.angle = H0.get('a') === 'free' ? null : H0.get('a'); press('[data-angle]', 'angle', state.angle || ''); }
if (H0.get('m')) { state.motion = H0.get('m'); press('[data-motion]', 'motion', state.motion); }
if (H0.get('l')) { state.light = H0.get('l'); press('[data-light]', 'light', state.light); }
if (H0.get('p') === '0') { state.player = false; tp.setAttribute('aria-pressed', 'false'); }
if (H0.get('follow') === '1') { state.follow = true; tf_.setAttribute('aria-pressed', 'true'); }
tags();
let fitted = false;
const lockT = H0.get('tx') != null;
const PLAYER_AT = { both: [0, 0, 2.6, Math.PI], hunt: [-3.2, 0, 0.5, -2.0], feed: [-3.2, 0, 0.5, -2.0] };

// ---------------------------------------------------------------- frame loop
function resize() {
  const dpr = Math.min(2, window.devicePixelRatio || 1), w = Math.round(canvas.clientWidth * dpr), h = Math.round(canvas.clientHeight * dpr);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  if (!fitted && canvas.clientWidth > 0) {
    fitted = true; aim(true);
    if (H0.get('yaw')) cam.yaw = +H0.get('yaw');
    if (H0.get('pitch')) cam.pitch = +H0.get('pitch');
    if (H0.get('d')) cam.d = +H0.get('d');
    if (lockT) cam.t = [+H0.get('tx'), +H0.get('ty'), +H0.get('tz')];
  }
}
function bindMesh(L, c) {
  gl.bindBuffer(gl.ARRAY_BUFFER, c.vb); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, c.ib);
  const S = VS * 4;
  gl.vertexAttribPointer(L.aP, 3, gl.FLOAT, false, S, 0);
  if (L.aN != null) { gl.vertexAttribPointer(L.aN, 3, gl.FLOAT, false, S, 12); gl.vertexAttribPointer(L.aC, 3, gl.FLOAT, false, S, 24); gl.vertexAttribPointer(L.aM, 4, gl.FLOAT, false, S, 36); gl.vertexAttribPointer(L.aA, 2, gl.FLOAT, false, S, 52); }
}
const attrs = L => [L.aP, L.aN, L.aC, L.aM, L.aA].filter(a => typeof a === 'number' && a >= 0);
let tt = 0;
function draw(dt) {
  resize();
  tt += dt;
  const motion = state.motion;
  const room = state.view === 'room';
  if (room) { stepRoom(motion === 'still' ? 0 : dt); showMind(); }
  else for (const w of Object.values(walkers)) stepWalker(w, motion === 'still' ? 0 : dt, motion);
  if (cam.goal) {
    cam.goal.t = focus();
    const k = dt > 0 ? 1 - Math.exp(-dt * 5) : 1;
    let dy = cam.goal.yaw - cam.yaw; dy = Math.atan2(Math.sin(dy), Math.cos(dy));
    cam.t = lerp3(cam.t, cam.goal.t, k); cam.d = lerp(cam.d, cam.goal.d, k); cam.yaw += dy * k; cam.pitch = lerp(cam.pitch, cam.goal.pitch, k);
    if (Math.abs(cam.d - cam.goal.d) < 0.01 && Math.abs(dy) < 0.002) cam.goal = null;
  } else if (state.view !== 'both' && !lockT) cam.t = lerp3(cam.t, focus(), dt > 0 ? 1 - Math.exp(-dt * (room ? 2 : 3)) : 1);
  // the creatures this frame
  const scene = [];
  if (room) {
    const dyn = new Mesh(), r = poseRoom(dyn), m = ROOMSIM.mind;
    r.dyn = toGPU(dyn, dynPool.room, gl.DYNAMIC_DRAW); r.key = 'room';
    r.feed = m && m.feeding ? 1 : 0; r.pulse = 0.5 + 0.5 * Math.sin(ROOMSIM.c.t * 7.5);
    scene.push(r);
  }
  for (const key of ['hunt', 'feed']) {
    if (state.view !== 'both' && state.view !== key) continue;
    const dyn = new Mesh(), r = pose(walkers[key], motion, dyn), w = walkers[key];
    r.dyn = toGPU(dyn, dynPool[key], gl.DYNAMIC_DRAW); r.key = key;
    r.feed = w.feeding ? 1 : 0; r.pulse = motion === 'still' ? 0.5 : 0.5 + 0.5 * Math.sin(w.t * 7.5);
    scene.push(r);
  }
  let player = null;
  if (state.player) {
    const pa = PLAYER_AT[state.view];
    if (room) player = frame(PLAYER_ROOM, rot(Math.PI));
    else if (state.view === 'both') player = frame([pa[0], 0, pa[2]], rot(pa[3]));
    else { const w = walkers[state.view], g = frame([w.x, 0, w.z], rot(w.yaw)); player = frame(at(g, [pa[0], 0, pa[2]]), rot(w.yaw + pa[3])); }
  }
  // camera and lights: the key comes from over the camera's left shoulder, the rim from behind on the right
  const eye = add(cam.t, [cam.d * Math.cos(cam.pitch) * Math.sin(cam.yaw), cam.d * Math.sin(cam.pitch), cam.d * Math.cos(cam.pitch) * Math.cos(cam.yaw)]);
  const aspect = canvas.width / Math.max(1, canvas.height);
  const VP = m4mul(perspective(0.66, aspect, 0.1, 90), lookAt(eye, cam.t));
  const fwd = norm(sub(cam.t, eye)), right = norm(cross(fwd, Y)), upv = cross(right, fwd);
  const fh = norm([fwd[0], 0, fwd[2]]), rh = norm(cross(fh, Y));
  const key = norm(add(add(mul(rh, -0.55), [0, 0.95, 0]), mul(fh, -0.6))), rim = norm(add(add(mul(rh, 0.7), [0, 0.45, 0]), mul(fh, 0.85)));
  const torch = state.light === 'torch' ? 1 : 0;
  const center = room ? [0, 2, 0] : state.view === 'both' ? [0, 1.5, 0] : [walkers[state.view].x, 1.5, walkers[state.view].z];
  const ext = room ? 12.5 : state.view === 'both' ? 11 : 7.5;
  const LVP = m4mul(ortho(-ext, ext, -ext, ext, 2, 50), lookAt(add(center, mul(key, 25)), center));
  const spot = add(add(eye, mul(right, 0.45)), mul(upv, -0.35)), spotDir = norm(sub(add(cam.t, [0, 0.1, 0]), spot));
  const each = fn => { for (const r of scene) { for (const [name, f] of r.parts) fn(GPU[name], m4of(f), r); fn(r.dyn, I4, r); } if (player) fn(GPU.player, m4of(player), null); if (room) fn(ROOMG.props, I4, null); };
  // the room: walls and ceiling between the camera and the creature are left out, so you look in
  const roomParts = room ? [ROOMG.floor, ...(eye[1] < ROOM.b[1] - 0.01 ? [ROOMG.ceiling] : []), ...ROOMG.walls.filter(w => dot(sub(eye, w.pt), w.n) > 0).map(w => w.g)] : [];
  // shadow pass
  if (shOK && !torch) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, shFB); gl.viewport(0, 0, SHN, SHN);
    gl.clearColor(1, 1, 1, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST); gl.disable(gl.BLEND); gl.disable(gl.CULL_FACE);
    gl.useProgram(shProg); gl.enableVertexAttribArray(LS.aP);
    gl.uniformMatrix4fv(LS.uVP, false, LVP);
    each((chunks, M) => { gl.uniformMatrix4fv(LS.uModel, false, M); for (const c of chunks) { bindMesh(LS, c); gl.drawElements(gl.TRIANGLES, c.n, gl.UNSIGNED_SHORT, 0); } });
    gl.disableVertexAttribArray(LS.aP);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }
  gl.viewport(0, 0, canvas.width, canvas.height);
  const bg0 = torch ? [0.012, 0.012, 0.016] : [0.22, 0.22, 0.235], bg1 = torch ? [0.004, 0.004, 0.006] : [0.03, 0.031, 0.036];
  gl.clearColor(bg1[0], bg1[1], bg1[2], 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  // the backdrop
  gl.disable(gl.DEPTH_TEST); gl.useProgram(bgProg);
  gl.uniform2f(LB.uRes, canvas.width, canvas.height); gl.uniform3fv(LB.uBg0, bg0); gl.uniform3fv(LB.uBg1, bg1);
  gl.bindBuffer(gl.ARRAY_BUFFER, quadBuf); gl.enableVertexAttribArray(LB.aP); gl.vertexAttribPointer(LB.aP, 2, gl.FLOAT, false, 8, 0);
  gl.drawArrays(gl.TRIANGLES, 0, 3); gl.disableVertexAttribArray(LB.aP);
  // the floor, the creatures, the player
  gl.enable(gl.DEPTH_TEST); gl.useProgram(prog);
  for (const a of attrs(L0)) gl.enableVertexAttribArray(a);
  gl.uniformMatrix4fv(L0.uVP, false, VP); gl.uniform3fv(L0.uCam, eye);
  const kd = room ? 0.62 : 1;
  gl.uniform3fv(L0.uKey, key); gl.uniform3fv(L0.uKeyC, mul([2.5, 2.38, 2.2], kd)); gl.uniform3fv(L0.uRim, rim); gl.uniform3fv(L0.uRimC, mul([1.3, 1.45, 1.75], kd)); gl.uniform3fv(L0.uFill, mul([0.2, 0.2, 0.22], kd));
  gl.uniform4fv(L0.uDark, room ? [DARK.p[0], DARK.p[1], DARK.p[2], DARK.r] : [0, 0, 0, 0]);
  gl.uniform1f(L0.uTorch, torch); gl.uniform3fv(L0.uSpot, spot); gl.uniform3fv(L0.uSpotDir, spotDir);
  const wide = state.view === 'both'; gl.uniform2f(L0.uCone, wide ? 0.83 : 0.88, wide ? 0.95 : 0.975); gl.uniform1f(L0.uGain, clamp((1 + 0.045 * cam.d * cam.d) / 4, 1, 4));
  gl.uniformMatrix4fv(L0.uLVP, false, LVP); gl.uniform1f(L0.uShTexel, 1 / SHN); gl.uniform1f(L0.uShOn, shOK && !torch ? 1 : 0);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, shTex); gl.uniform1i(L0.uShadow, 0);
  gl.uniform3fv(L0.uCenter, center); gl.uniform1f(L0.uFogR, room ? 40 : state.view === 'both' ? 9 : 5.5);
  gl.uniform2f(L0.uRes, canvas.width, canvas.height); gl.uniform3fv(L0.uBg0, bg0); gl.uniform3fv(L0.uBg1, bg1);
  const setC = r => { gl.uniform1f(L0.uFeed, r ? r.feed : 0); gl.uniform1f(L0.uPulse, r ? r.pulse : 0); gl.uniform3fv(L0.uEye, r ? r.eyeLight : [0, -50, 0]); gl.uniform3fv(L0.uEyeC, r ? [0.05 + 0.9 * r.open, 0.012 + 0.34 * r.open, 0.006 + 0.06 * r.open] : [0, 0, 0]); };
  setC(null); gl.uniformMatrix4fv(L0.uModel, false, I4);
  for (const part of room ? roomParts : [GPU.floor]) for (const c of part) { bindMesh(L0, c); gl.drawElements(gl.TRIANGLES, c.n, gl.UNSIGNED_SHORT, 0); }
  let cur;
  each((chunks, M, r) => { if (r !== cur) { setC(r); cur = r; } gl.uniformMatrix4fv(L0.uModel, false, M); for (const c of chunks) { bindMesh(L0, c); gl.drawElements(gl.TRIANGLES, c.n, gl.UNSIGNED_SHORT, 0); } });
  for (const a of attrs(L0)) gl.disableVertexAttribArray(a);
  // glows, breath and dust, added on top
  gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
  const gv = [];
  for (const r of scene) for (const [p0, rr] of r.eyes) {
    const p = add(p0, mul(norm(sub(eye, p0)), 0.08));
    for (const [sz, I] of [[rr * 5.5, 0.2], [rr * 2.2, 0.3]]) for (const [x, y] of [[-1, -1], [1, -1], [1, 1], [-1, -1], [1, 1], [-1, 1]]) gv.push(p[0], p[1], p[2], x, y, sz, I);
  }
  if (gv.length) {
    gl.useProgram(glowProg); gl.uniformMatrix4fv(LG.uVP, false, VP); gl.uniform3fv(LG.uR, right); gl.uniform3fv(LG.uU, upv);
    gl.bindBuffer(gl.ARRAY_BUFFER, glowBuf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(gv), gl.DYNAMIC_DRAW);
    gl.enableVertexAttribArray(LG.aP); gl.vertexAttribPointer(LG.aP, 3, gl.FLOAT, false, 28, 0);
    gl.enableVertexAttribArray(LG.aQ); gl.vertexAttribPointer(LG.aQ, 4, gl.FLOAT, false, 28, 12);
    gl.drawArrays(gl.TRIANGLES, 0, gv.length / 7);
    gl.disableVertexAttribArray(LG.aP); gl.disableVertexAttribArray(LG.aQ);
  }
  gl.useProgram(mistProg);
  gl.uniformMatrix4fv(LM.uVP, false, VP); gl.uniform3fv(LM.uSpot, spot); gl.uniform3fv(LM.uSpotDir, spotDir); gl.uniform1f(LM.uTorch, torch); gl.uniform1f(LM.uPx, canvas.height);
  const pts = (buf, arr, n, size, tint) => {
    gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, arr, gl.DYNAMIC_DRAW);
    gl.enableVertexAttribArray(LM.aP); gl.vertexAttribPointer(LM.aP, 3, gl.FLOAT, false, 16, 0);
    gl.enableVertexAttribArray(LM.aA); gl.vertexAttribPointer(LM.aA, 1, gl.FLOAT, false, 16, 12);
    gl.uniform1f(LM.uSize, size); gl.uniform3fv(LM.uTint, tint); gl.drawArrays(gl.POINTS, 0, n);
    gl.disableVertexAttribArray(LM.aP); gl.disableVertexAttribArray(LM.aA);
  };
  for (let i = 0; i < DUST; i++) { const s = dustSeed[i]; dustArr.set([s[0] + 0.25 * Math.sin(tt * 0.13 + s[3]), s[1] + 0.2 * Math.sin(tt * 0.09 + s[3] * 2), s[2] + 0.25 * Math.cos(tt * 0.11 + s[3]), torch ? 0.9 : 0.12], i * 4); }
  pts(dustBuf, dustArr, DUST, 0.04, [0.86, 0.82, 0.74]);
  if (torch) for (const r of scene) if (r.mouth) {
    const m0 = r.mouth;
    for (let i = 0; i < MIST; i++) { const ph = (tt * 0.25 + i / MIST) % 1, a = hash(i * 1.7) * TAU, rr = 0.03 + ph * 0.45; mistArr.set([m0[0] + Math.cos(a) * rr, m0[1] - ph * 1.3 + 0.05 * Math.sin(tt + i), m0[2] + Math.sin(a) * rr, 0.2 * Math.sin(Math.PI * ph)], i * 4); }
    pts(mistBuf, mistArr, MIST, 0.22, [0.55, 0.6, 0.66]);
  }
  gl.depthMask(true); gl.disable(gl.BLEND);
}
// offline check: run the walk forward to a set time, draw once
const T0 = H0.get('t');
if (T0 != null) {
  const tEnd = +T0; let t = 0;
  if (state.view === 'room') {
    if (H0.get('act')) { ROOMSIM.mind = makeMind(); startPlan(H0.get('act')); }
    const nz = H0.get('noise') ? H0.get('noise').split('@') : null;
    while (t < tEnd) {
      const dt = Math.min(1 / 30, tEnd - t);
      if (nz && t <= +nz[1] && t + dt > +nz[1]) { const q = nz[0].split(',').map(Number); ROOMSIM.pings.push({ p: q, n: [0, 1, 0], age: 0 }); hearNoise(q, Y); }
      stepRoom(dt); t += dt;
    }
    showMind(); window.__log = ROOMSIM.mind.log.join(' | ') + ' || pos ' + ROOMSIM.c.pos.map(v => v.toFixed(2)).join(',');
  } else while (t < tEnd) { const dt = Math.min(1 / 30, tEnd - t); for (const w of Object.values(walkers)) stepWalker(w, state.motion === 'still' ? 0 : dt, state.motion); t += dt; }
  resize(); draw(0); document.title = 'done';
  return;
}
let last = performance.now();
const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
function loop(now) { const dt = Math.min(0.05, (now - last) / 1000); last = now; draw(reduce && state.motion !== 'walk' && state.view !== 'room' ? 0 : dt); requestAnimationFrame(loop); }
requestAnimationFrame(loop);
})();
</script>
