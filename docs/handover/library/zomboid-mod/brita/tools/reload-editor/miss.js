require('./engine.js'); const fs=require('fs'); const E=globalThis.REngine;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
const ids=process.argv.slice(2).length?process.argv.slice(2):JSON.parse(fs.readFileSync('kit/index.json'));
for (const gid of ids){ const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); if(kit.field&&kit.field.data) kit.field.arr=new Int8Array(require('zlib').gunzipSync(Buffer.from(kit.field.data,'base64'))); let line=gid.padEnd(18);
 for (const solver of ['natural',undefined]) for (const view of ['fp','tp','ta']) {
  const sp=JSON.parse(JSON.stringify(kit.spec)); if(solver) sp.solver=solver; else delete sp.solver;
  if(view==='ta'&&!sp.gun.some(([,k])=>k.ta)) sp.gun=sp.gun.map(([t])=>[t,{}]);
  const {frames}=E.bake(S,kit,sp,view); let m={L:0,R:0};
  frames.forEach(f=>{for(const s of 'LR') m[s]=Math.max(m[s],(f.miss||{})[s]||0)});
  line+=` ${solver?'N':'o'}${view}:${Math.round(m.L*100)}/${Math.round(m.R*100)}`;}
 console.log(line);}
