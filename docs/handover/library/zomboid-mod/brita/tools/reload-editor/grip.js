require('./engine.js'); const fs=require('fs'); const E=globalThis.REngine; const {m4inv,m4mul,m4t,m4R,v3}=E;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
for (const gid of JSON.parse(fs.readFileSync('kit/index.json'))){ const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); if(kit.field&&kit.field.data) kit.field.arr=new Int8Array(require('zlib').gunzipSync(Buffer.from(kit.field.data,'base64')));
 let line=gid.padEnd(18);
 for (const view of ['fp','tp']) { const {rig,frames}=E.bake(S,kit,kit.spec,view); let w={R:0,L:0};
  frames.forEach((fr,f)=>{ const t=f===frames.length-1?kit.spec.dur:f/30;
   for (const s of 'RL'){ const [a,b,u]=E.track(kit.spec[s]||[[0,{k:'rest'}]],t); const pl=k=>{const r=E.resolve(k,view);return (r.k||'rest')==='rest'&&!r.dpx&&!r.d&&!r.r&&!r.curl&&!r.fwd&&!r.flat;}; if(!(pl(a)&&(u<1e-9||pl(b)))) continue;
    const rel=m4mul(m4inv(fr.gun),fr.bones[rig.pre(s,'Hand')]), rel0=m4mul(rig.G0i,rig.bones[rig.pre(s,'Hand')]);
    let d=v3.len(v3.sub(E.m4pt(fr.gun,m4t(rel)),E.m4pt(fr.gun,m4t(rel0))));
    for (const fg of rig.fingerList(s)) { const B=fr.bones[fg.name], B0=rig.bones[fg.name]; if(!B) continue; const r1=m4mul(m4inv(fr.gun),B), r0=m4mul(rig.G0i,B0); for(const p of [[0.03,0,0],[0.05,0,0]]) d=Math.max(d,v3.len(v3.sub(E.m4pt(fr.gun,E.m4pt(r1,p)),E.m4pt(fr.gun,E.m4pt(r0,p))))); }
    w[s]=Math.max(w[s],d);} });
  line+=` ${view} R ${(w.R*1000).toFixed(1)}mm L ${(w.L*1000).toFixed(1)}mm`; }
 console.log(line);}
