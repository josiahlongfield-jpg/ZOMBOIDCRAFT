const eng=process.argv[2]; require('./'+eng); const fs=require('fs'); const E=globalThis.REngine;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
const ids=process.argv.slice(3);
for (const gid of ids){ const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); kit.field.arr=new Int8Array(require('zlib').gunzipSync(Buffer.from(kit.field.data,'base64')));
 let line=gid.padEnd(16);
 for (const view of ['fp','tp']) { const {rig,frames}=E.bake(S,kit,kit.spec,view); let worst=0;
  for (const s of 'RL') for (const fg of rig.fingerList(s)) { const rel=frames.map(fr=>E.m3mul(E.m3T(E.m4R(fr.bones[rig.pre(s,'Hand')])),E.m4R(fr.bones[fg.name]||fr.bones[rig.pre(s,'Hand')])));
   const ang=(A,B)=>{const M=E.m3mul(E.m3T(A),B);return Math.acos(Math.max(-1,Math.min(1,(M[0]+M[4]+M[8]-1)/2)))*57.3;};
   for(let i=1;i<rel.length-1;i++){ const mid=E.slerp(rel[i-1],rel[i+1],0.5); worst=Math.max(worst, ang(mid,rel[i])); } }
  line+=` ${view} jerk ${worst.toFixed(1)}deg`; }
 console.log(line);}
