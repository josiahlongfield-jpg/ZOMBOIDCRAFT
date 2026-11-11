require('./engine.js'); const fs=require('fs'); const E=globalThis.REngine; const {m4pt,m4t,v3}=E;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
const [gid,view,s,nost]=process.argv.slice(2); const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); kit.field.arr=new Int8Array(require('zlib').gunzipSync(Buffer.from(kit.field.data,'base64')));
const sp=JSON.parse(JSON.stringify(kit.spec)); if(nost) delete sp.style;
const {rig,frames}=E.bake(S,kit,sp,view);
frames.forEach((fr,f)=>{ if(f%6) return; const P=n=>m4pt(rig.place,m4t(fr.bones[rig.pre(s,n)])); const sh=P('UpperArm'),el=P('Forearm'),wr=P('Hand');
 console.log((f/30).toFixed(1),'sh',sh.map(x=>x.toFixed(2)).join(','),'el',el.map(x=>x.toFixed(2)).join(','),'wr',wr.map(x=>x.toFixed(2)).join(','));});
