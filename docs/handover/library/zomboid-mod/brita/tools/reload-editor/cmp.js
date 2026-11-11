require('./engine.js'); const fs=require('fs'); const E=globalThis.REngine;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
const f32=b=>{const u=Buffer.from(b,'base64');return new Float32Array(u.buffer.slice(u.byteOffset,u.byteOffset+u.length));};
for (const gid of process.argv.slice(2)) {
  const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); const ref=JSON.parse(fs.readFileSync(`../reload/d_${gid}.json`));
  for (const view of ['fp','tp']) {
    const t0=Date.now(); const {rig,frames}=E.bake(S,kit,kit.spec,view); const ms=Date.now()-t0;
    let worst=0, wg=0;
    frames.forEach((fr,i)=>{ const a=rig.skin(fr.bones), b=f32(ref.views[view][i].b);
      for(let k=0;k<a.length;k++) worst=Math.max(worst,Math.abs(a[k]-b[k]));
      for(const [n,m] of Object.entries(ref.views[view][i].g)){ const g=fr.g[n]; if(!g){wg=99;continue;} for(let k=0;k<16;k++) wg=Math.max(wg,Math.abs(g[k]-m[k])); }
      for(const n of Object.keys(fr.g)) if(!ref.views[view][i].g[n]) wg=Math.max(wg,98);
    });
    console.log(gid, view, frames.length, ref.views[view].length, 'skin', worst.toExponential(2), 'parts', wg.toExponential(2), ms+'ms');
  }
}
