// Which side of each triangle can be seen, and from how many views: renders a triangle soup with
// a triangle-id colour from many cameras in headless Chromium (WebGL2) and counts, per triangle,
// the views that saw its front (counter-clockwise) side and its back side.
//   node seen.js TRIS.f32 VIEWS.json OUT.i32 [size]
// TRIS.f32: float32 x,y,z per corner, 3 corners per triangle. VIEWS.json: [{eye:[x,y,z], at:[x,y,z],
// fov:deg}].
// OUT.i32: front counts for every triangle, then back counts.
const http = require('http');
const fs = require('fs');
const { chromium } = require('playwright');

const [trisPath, viewsPath, outPath, sizeArg] = process.argv.slice(2);
const SIZE = +(sizeArg || 1024);
const tris = fs.readFileSync(trisPath);
const views = fs.readFileSync(viewsPath);

const page = `<!doctype html><canvas id=c width=${SIZE} height=${SIZE}></canvas><script>
async function run() {
  const pos = new Float32Array(await (await fetch('/tris')).arrayBuffer());
  const views = await (await fetch('/views')).json();
  const n = pos.length / 9;
  const gl = document.getElementById('c').getContext('webgl2', { antialias: false });
  const vs = \`#version 300 es
    in vec3 p; uniform mat4 m; flat out int id;
    void main() { id = gl_VertexID / 3; gl_Position = m * vec4(p, 1.0); }\`;
  const fs = \`#version 300 es
    precision highp float; flat in int id; out vec4 o;
    void main() { int k = id + 1;
      o = vec4(float(k & 255), float((k >> 8) & 255), float((k >> 16) & 255), gl_FrontFacing ? 255.0 : 0.0) / 255.0; }\`;
  const prog = gl.createProgram();
  for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    gl.attachShader(prog, s);
  }
  gl.linkProgram(prog); gl.useProgram(prog);
  const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, pos, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.bindAttribLocation(prog, 0, 'p'); gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 0, 0);
  const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
  const tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex);
  gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, ${SIZE}, ${SIZE});
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
  const depth = gl.createRenderbuffer(); gl.bindRenderbuffer(gl.RENDERBUFFER, depth);
  gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT24, ${SIZE}, ${SIZE});
  gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, depth);
  gl.viewport(0, 0, ${SIZE}, ${SIZE}); gl.enable(gl.DEPTH_TEST); gl.disable(gl.CULL_FACE);
  const loc = gl.getUniformLocation(prog, 'm');
  const front = new Int32Array(n), back = new Int32Array(n);
  const px = new Uint8Array(${SIZE} * ${SIZE} * 4);
  const seenF = new Int32Array(n).fill(-1), seenB = new Int32Array(n).fill(-1);
  for (let v = 0; v < views.length; v++) {
    const V = views[v];
    gl.uniformMatrix4fv(loc, false, matrix(V.eye, V.at, V.fov || 50, V.near || 0.05, V.far || 400));
    gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLES, 0, n * 3);
    gl.readPixels(0, 0, ${SIZE}, ${SIZE}, gl.RGBA, gl.UNSIGNED_BYTE, px);
    for (let i = 0; i < px.length; i += 4) {
      const k = px[i] | (px[i + 1] << 8) | (px[i + 2] << 16);
      if (k === 0) continue;
      const t = k - 1;
      if (px[i + 3] > 127) { if (seenF[t] !== v) { seenF[t] = v; front[t]++; } }
      else if (seenB[t] !== v) { seenB[t] = v; back[t]++; }
    }
  }
  const out = new Int32Array(n * 2); out.set(front, 0); out.set(back, n);
  const r = await fetch('/out', { method: 'POST', body: out.buffer });
  return r.status;
}
function matrix(eye, at, fov, near, far) {
  const f = sub(eye, at); norm(f);
  let up = Math.abs(f[1]) > 0.98 ? [0, 0, 1] : [0, 1, 0];
  const s = cross(up, f); norm(s); const u = cross(f, s);
  const view = [s[0], u[0], f[0], 0, s[1], u[1], f[1], 0, s[2], u[2], f[2], 0,
    -dot(s, eye), -dot(u, eye), -dot(f, eye), 1];
  const k = 1 / Math.tan(fov * Math.PI / 360);
  const proj = [k, 0, 0, 0, 0, k, 0, 0, 0, 0, (far + near) / (near - far), -1, 0, 0, 2 * far * near / (near - far), 0];
  const m = new Float32Array(16);
  for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
    let sum = 0; for (let k2 = 0; k2 < 4; k2++) sum += proj[k2 * 4 + r] * view[c * 4 + k2]; m[c * 4 + r] = sum;
  }
  return m;
}
function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
function norm(a) { const l = Math.hypot(a[0], a[1], a[2]); a[0] /= l; a[1] /= l; a[2] /= l; }
</script>`;

(async () => {
  let result = null;
  const server = http.createServer((req, res) => {
    if (req.url === '/') { res.setHeader('content-type', 'text/html'); res.end(page); }
    else if (req.url === '/tris') res.end(tris);
    else if (req.url === '/views') res.end(views);
    else if (req.url === '/out') {
      const parts = [];
      req.on('data', (d) => parts.push(d));
      req.on('end', () => { result = Buffer.concat(parts); res.end('ok'); });
    } else { res.statusCode = 404; res.end(); }
  });
  await new Promise((ok) => server.listen(0, '127.0.0.1', ok));
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--js-flags=--max-old-space-size=8192'] });
  const p = await b.newPage();
  p.on('pageerror', (e) => console.log('err:', e.message));
  await p.goto(`http://127.0.0.1:${server.address().port}/`);
  const status = await p.evaluate(() => run());
  await b.close();
  server.close();
  if (status !== 200 || !result) throw new Error('no result');
  fs.writeFileSync(outPath, result);
  console.log('views', JSON.parse(views).length, 'triangles', tris.length / 36);
})();
