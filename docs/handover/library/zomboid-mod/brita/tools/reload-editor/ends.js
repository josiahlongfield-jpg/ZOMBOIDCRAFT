require('./engine.js'); const fs=require('fs'); const E=globalThis.REngine;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
for (const gid of JSON.parse(fs.readFileSync('kit/index.json'))){ const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); if(kit.field&&kit.field.data) kit.field.arr=new Int8Array(require('zlib').gunzipSync(Buffer.from(kit.field.data,'base64')));
 let line=gid.padEnd(18);
 for (const view of ['fp','tp','ta']) { const sp=JSON.parse(JSON.stringify(kit.spec)); if(view==='ta'&&!sp.gun.some(([,k])=>k.ta)) sp.gun=sp.gun.map(([t])=>[t,{}]);
  const {rig,frames}=E.bake(S,kit,sp,view); let gd=0,bd=0;
  for (const fr of [frames[0],frames[frames.length-1]]) { gd=Math.max(gd,E.v3.len(E.v3.sub(E.m4t(fr.gun),E.m4t(rig.G0))));
   for (const [n,M] of Object.entries(rig.bones)) if(fr.bones[n]) bd=Math.max(bd,E.v3.len(E.v3.sub(E.m4t(fr.bones[n]),E.m4t(M)))); }
  line+=` ${view}: gun ${(gd*1000).toFixed(1)}mm bones ${(bd*1000).toFixed(1)}mm`; }
 console.log(line);}
