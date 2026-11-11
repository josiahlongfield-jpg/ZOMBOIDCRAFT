require('./engine.js'); const fs=require('fs'); const E=globalThis.REngine; const {m4inv,m4mul,m4pt}=E;
const S=JSON.parse(fs.readFileSync('kit/static.json'));
const f32=b=>{const u=Buffer.from(b,'base64');return new Float32Array(u.buffer.slice(u.byteOffset,u.byteOffset+u.length));};
const P=f32(S.P),J=f32(S.J),W=f32(S.W); const nv=P.length/3;
const [gid,view]=process.argv.slice(2); const kit=JSON.parse(fs.readFileSync(`kit/g_${gid}.json`)); const F=kit.field; F.arr=new Int8Array(require('zlib').gunzipSync(Buffer.from(F.data,'base64')));
console.log('center val', F.arr[((11)*F.shape[1]+26)*F.shape[2]+36], 'corner', F.arr[0]);
const sp=JSON.parse(JSON.stringify(kit.spec)); if(view==='ta'&&!sp.gun.some(([,k])=>k.ta)) sp.gun=sp.gun.map(([t])=>[t,{}]);
const t0=Date.now();const {rig,frames}=E.bake(S,kit,sp,view);console.log('ms',Date.now()-t0);
const NB=S.names.length; const fidx={}; for(const [s,sd] of [[0,'R'],[1,'L']]){fidx[sd+'thumb']=S.names.indexOf(`Bip01_${sd}_Finger0`); S.FING.forEach((f,k)=>fidx[sd+f]=NB+s*4+k);}
for (const fi of [0, Math.floor(frames.length/2)]) { const fr=frames[fi]; const K=rig.skin(fr.bones); const Gi=m4mul(m4inv(fr.gun), rig.Pi);
 const res={};
 for(let v=0;v<nv;v++){ let best=-1,bw=0; for(let j=0;j<4;j++) if(W[v*4+j]>bw){bw=W[v*4+j];best=J[v*4+j];}
  const name=Object.keys(fidx).find(n=>fidx[n]===best); if(!name) continue;
  const p=[0,0,0]; for(let j=0;j<4;j++){const w=W[v*4+j]; if(!w) continue; const b=J[v*4+j]; for(let r=0;r<3;r++) p[r]+=w*(K[b*16+r]*P[v*3]+K[b*16+4+r]*P[v*3+1]+K[b*16+8+r]*P[v*3+2]+K[b*16+12+r]);}
  const x=m4pt(Gi,p); const ix=Math.round((x[0]-F.lo[0])/F.vox),iy=Math.round((x[1]-F.lo[1])/F.vox),iz=Math.round((x[2]-F.lo[2])/F.vox);
  if(ix<0||iy<0||iz<0||ix>=F.shape[0]||iy>=F.shape[1]||iz>=F.shape[2]) continue;
  const d=F.arr[(ix*F.shape[1]+iy)*F.shape[2]+iz]*F.vox*rig.scale/16; res[name]=Math.min(res[name]??1,d);}
 console.log(gid,view,fi,Object.entries(res).map(([k,v])=>k+':'+(v*1000).toFixed(0)+'mm').join(' '));}
