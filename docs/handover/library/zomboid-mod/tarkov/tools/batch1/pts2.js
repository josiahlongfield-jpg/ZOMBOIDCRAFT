// Lens point of a batch-1 gun's light/laser: GUN=b1_pm.json ANIM=.. BASE=.. node pts2.js '{"light":["bone","lensNode"],"laser":[...]}'
const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const PORT=8960+Math.floor(Math.random()*30);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:320,height:200}});
p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${PORT}/index.html`); await p.waitForFunction('window.ready');
await p.evaluate(([a,bc])=>{window.ANIMFILE=a; window.BASECLIP=bc;},[process.env.ANIM,process.env.BASE]);
await p.evaluate(g=>setup(g),process.env.GUN); await p.waitForTimeout(500);
const out=await p.evaluate((want)=>{ const T=window.THREE, W=window.W; window.IK=false; window.ALLLAYERS=true; pose(null,0); W.root.updateMatrixWorld(true);
  const one=n=>{let r=null; W.root.traverse(o=>{ if(!r&&o.name===n) r=o;}); return r;};
  const wp=o=>new T.Vector3().setFromMatrixPosition(o.matrixWorld);
  const boreW=wp(one('mod_muzzle')).sub(wp(one('weapon'))).normalize();
  const ri=W.root.matrixWorld.clone().invert(); const rq=new T.Quaternion(); W.root.getWorldQuaternion(rq);
  const res={};
  for(const [k,[bone,lens]] of Object.entries(want)){ const B=one(bone), L=one(lens); const inv=B.matrixWorld.clone().invert();
    const at=wp(L).applyMatrix4(inv); const q=new T.Quaternion(); B.getWorldQuaternion(q); const d=boreW.clone().applyQuaternion(q.invert());
    const th=wp(L).applyMatrix4(ri);
    res[k]={bone, at:[at.x,at.y,at.z].map(v=>+v.toFixed(4)), dir:[d.x,d.y,d.z].map(v=>+v.toFixed(4)), third:[th.x,th.y,th.z].map(v=>+v.toFixed(4))}; }
  return res; }, JSON.parse(process.argv[2]));
console.log(JSON.stringify(out)); await b.close(); srv.close();})();
