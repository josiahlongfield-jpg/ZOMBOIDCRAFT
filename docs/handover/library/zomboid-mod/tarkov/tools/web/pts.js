// Lens points of the kitted M4A1's rail devices, in their mesh bone's frame (metres), plus the bore direction there
const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const PORT=8937;
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:320,height:200}});
p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${PORT}/index.html`); await p.waitForFunction('window.ready');
await p.evaluate(()=>setup('kit')); await p.waitForTimeout(500);
const out=await p.evaluate(()=>{ const T=window.THREE, W=window.W; window.IK=false; window.ALLLAYERS=true;
  pose(null,0); W.root.updateMatrixWorld(true);
  const all=n=>{const r=[]; W.root.traverse(o=>{ if(o.name===n) r.push(o);}); return r;};
  const one=n=>all(n)[0];
  const under=(o,anc)=>{ for(let x=o;x;x=x.parent) if(x.name===anc) return true; return false; };
  const res={};
  const wp=o=>new T.Vector3().setFromMatrixPosition(o.matrixWorld);
  const muz=one('mod_muzzle'), bar=one('barrel_ar15_370mm');
  const boreW=wp(muz).sub(wp(bar)).normalize();
  const rel=(boneName,nodes)=>{ const bone=one(boneName); const inv=bone.matrixWorld.clone().invert();
    const r={bone:boneName}; for(const [k,n,anc] of nodes){ const o=all(n).find(x=>!anc||under(x,anc)); const pp=wp(o).applyMatrix4(inv); r[k]=[pp.x,pp.y,pp.z].map(v=>+v.toFixed(4)); }
    const q=new T.Quaternion(); bone.getWorldQuaternion(q); const d=boreW.clone().applyQuaternion(q.invert()); r.bore=[d.x,d.y,d.z].map(v=>+v.toFixed(4)); return r; };
  res.laser=rel('tactical_all_morovision_anpeq15_LOD0',[['vis','VIS_000'],['ir','IR_000'],['illum','IL_000']]);
  res.light=rel('flashlight_surefire_m600_ultra_scout_LOD0',[['at','light_001']]);
  const ri=W.root.matrixWorld.clone().invert(); const rq=new T.Quaternion(); W.root.getWorldQuaternion(rq);
  const th=n=>{ const pp=wp(one(n)).applyMatrix4(ri); return [pp.x,pp.y,pp.z].map(v=>+v.toFixed(4)); };
  const d=boreW.clone().applyQuaternion(rq.clone().invert());
  res.third={vis:th('VIS_000'),light:th('light_001'),bore:[d.x,d.y,d.z].map(v=>+v.toFixed(4))};
  return res; });
console.log(JSON.stringify(out,null,1)); await b.close(); srv.close();})();
