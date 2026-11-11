// software rasteriser: orthographic views of a THREE-shim figure with a z-buffer and flat cel shading
function rasterFigure(fig, ctx, cx, view, S, G, opt = {}) {
  const M4 = __M4, W = ctx.canvas.width, H = ctx.canvas.height;
  const img = ctx.getImageData(0, 0, W, H), px = img.data;
  const zb = opt.zb || new Float32Array(W * H).fill(-1e9);
  const proj = p => view === 'front' ? [p[0], p[1], p[2]] : view === 'back' ? [-p[0], p[1], -p[2]] : [-p[2], p[1], p[0]];
  const toScr = q => [cx + q[0] * S, G - (q[1] - 0.4) * S, q[2]];
  const pixCache = window.__PIX || (window.__PIX = new Map());
  const pixels = im => {
    if (pixCache.has(im)) return pixCache.get(im);
    const w = im.width || im.naturalWidth, h = im.height || im.naturalHeight; const c = document.createElement('canvas'); c.width = w; c.height = h;
    const x = c.getContext('2d'); x.drawImage(im, 0, 0); const r = { w, h, d: x.getImageData(0, 0, w, h).data }; pixCache.set(im, r); return r;
  };
  const L = (() => { const v = [-0.45, 0.75, 0.55], n = Math.hypot(...v); return v.map(a => a / n); })();
  const tint = opt.tint ?? true;
  function face(Mw, A, B, E, mat, u0, v0, u1, v1) {
    if (!mat || !mat.map || !mat.map.image) return;
    const a = M4.ap(Mw, A), b = M4.ap(Mw, B), e = M4.ap(Mw, E);
    const ab = [b[0]-a[0], b[1]-a[1], b[2]-a[2]], ae = [e[0]-a[0], e[1]-a[1], e[2]-a[2]];
    let n = [-(ab[1]*ae[2]-ab[2]*ae[1]), -(ab[2]*ae[0]-ab[0]*ae[2]), -(ab[0]*ae[1]-ab[1]*ae[0])]; const nl = Math.hypot(...n) || 1; n = n.map(x => x / nl);
    const pa = toScr(proj(a)), pb = toScr(proj(b)), pe = toScr(proj(e)), nv = proj(n);
    if (nv[2] <= 1e-4) return;                                   // back face
    const shade = Math.min(1.08, 0.62 + 0.46 * Math.max(0, n[0]*L[0] + n[1]*L[1] + n[2]*L[2]));
    const T = pixels(mat.map.image), col = tint && mat.color ? mat.color.n : 0xffffff;
    const cr = (col >> 16) / 255 * shade, cg = (col >> 8 & 255) / 255 * shade, cb = (col & 255) / 255 * shade;
    const ux = pb[0]-pa[0], uy = pb[1]-pa[1], vx = pe[0]-pa[0], vy = pe[1]-pa[1], det = ux*vy - uy*vx; if (Math.abs(det) < 1e-6) return;
    const xs = [pa[0], pb[0], pe[0], pb[0]+vx], ys = [pa[1], pb[1], pe[1], pb[1]+vy];
    const x0 = Math.max(0, Math.floor(Math.min(...xs))), x1 = Math.min(W - 1, Math.ceil(Math.max(...xs))), y0 = Math.max(0, Math.floor(Math.min(...ys))), y1 = Math.min(H - 1, Math.ceil(Math.max(...ys)));
    const at = mat.alphaTest > 0;
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const dx = x + 0.5 - pa[0], dy = y + 0.5 - pa[1], s = (dx*vy - dy*vx) / det, t = (ux*dy - uy*dx) / det;
      if (s < 0 || s >= 1 || t < 0 || t >= 1) continue;
      const z = pa[2] + s * (pb[2]-pa[2]) + t * (pe[2]-pa[2]), k = y * W + x;
      if (z <= zb[k] + 1e-4) continue;
      const tu = Math.min(T.w - 1, Math.floor((u0 + s * (u1 - u0)) * T.w)), tv = Math.min(T.h - 1, Math.floor((v0 + t * (v1 - v0)) * T.h)), ti = (tv * T.w + tu) * 4;
      if (at && T.d[ti + 3] < 128) continue;
      zb[k] = z; const o = k * 4;
      px[o] = Math.min(255, T.d[ti] * cr); px[o+1] = Math.min(255, T.d[ti+1] * cg); px[o+2] = Math.min(255, T.d[ti+2] * cb); px[o+3] = 255;
    }
  }
  const walk = (o, P) => {
    if (!o.visible) return;
    const Mw = M4.mul(P, M4.trs(o.position, o.rotation, o.scale));
    if (o.isMesh && o.geometry) {
      const g = o.geometry;
      if (g.box) {
        const { width: w0, height: h0, depth: d0 } = g.parameters, w = w0 / 2, h = h0 / 2, d = d0 / 2, m = [].concat(o.material);
        const F = [ // px nx py ny pz nz: TL, TR, BL
          [[w, h, d], [w, h, -d], [w, -h, d]], [[-w, h, -d], [-w, h, d], [-w, -h, -d]], [[-w, h, -d], [w, h, -d], [-w, h, d]],
          [[-w, -h, d], [w, -h, d], [-w, -h, -d]], [[-w, h, d], [w, h, d], [-w, -h, d]], [[w, h, -d], [-w, h, -d], [w, -h, -d]]];
        F.forEach((f, i) => face(Mw, f[0], f[1], f[2], m[i] || m[0], 0, 0, 1, 1));
      } else if (g.attributes && g.attributes.position) {
        const P3 = g.attributes.position.array, UV = g.attributes.uv.array;
        for (let q = 0; q < P3.length / 12; q++) {
          const v = i => [P3[(q*4+i)*3], P3[(q*4+i)*3+1], P3[(q*4+i)*3+2]];
          face(Mw, v(0), v(1), v(3), o.material, UV[q*8], UV[q*8+1], UV[q*8+2], UV[q*8+5]);
        }
      }
    }
    for (const c of o.children) walk(c, Mw);
  };
  walk(fig, M4.id());
  ctx.putImageData(img, 0, 0);
}
