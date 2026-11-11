// Exports the Tarkov operator (TP) or the kitted M4A1 first-person rig (FP) as matrices for pack_zsk.py
const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const MODE=process.argv[2], OUT=process.argv[3];
const root=__dirname; const PORT=+(process.env.PORT||8931);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:320,height:200}});
p.on('pageerror',e=>console.log('ERR',e.message)); p.on('console',m=>console.log('PAGE',m.text()));
await p.goto(`http://localhost:${PORT}/index.html`); await p.waitForFunction('window.ready');
let out;
if(MODE==='tp'){
  const cfg=JSON.parse(process.env.CFG);
  await p.evaluate(l=>setupBody(l),cfg.parts); await p.evaluate(k=>dressBody({kit:k,gun:true}),cfg.kit);
  out=await p.evaluate((cfg)=>{ const T=window.THREE, BW=window.BW;
    const M=m=>Array.from(m.elements);
    const vis=o=>{ for(let x=o;x;x=x.parent){ if(!x.visible) return false; } return true; };
    BW.root.updateMatrixWorld(true);
    const bones=BW.nodes.map(n=>({name:n.name,parent:n.parent}));
    const rest=BW.nodes.map(n=>M(n.obj.matrixWorld));
    const kit=[]; for(const [key,K] of Object.entries(window.KITB||{})){ K.root.updateMatrixWorld(true); K.nodes.forEach((n,i)=>{ if(n.mesh && !n.mesh.skin && vis(n.obj)) kit.push({key,i,world:M(n.obj.matrixWorld)}); }); }
    // gun nodes relative to its root (fixed: the first-person idle hold)
    const W=window.W; W.root.updateMatrixWorld(true); const wi=new T.Matrix4().copy(W.root.matrixWorld).invert();
    const gun=[]; W.nodes.forEach((n,i)=>{ if(n.mesh && !n.mesh.skin && vis(n.obj)) gun.push({i,local:M(wi.clone().multiply(n.obj.matrixWorld))}); });
    const clips={};
    for(const [name,li,as] of cfg.clips){ const L=window.BANIM[name][li]; const frames=[];
      for(let f=0;f<L.nf;f++){ poseBody(name,li,f); BW.root.updateMatrixWorld(true); W.root.updateMatrixWorld(true);
        const fr=BW.nodes.map(n=>M(n.obj.matrixWorld)); fr.push(M(W.root.matrixWorld)); frames.push(fr); }
      clips[as]={fps:(L.nf-1)/L.dur, frames}; }
    return {bones,rest,kit,gun,clips};
  },cfg);
} else {
  const cfg=JSON.parse(process.env.CFG);
  await p.evaluate(()=>setup('kit')); await p.waitForTimeout(500);
  out=await p.evaluate((cfg)=>{ const T=window.THREE, W=window.W, H=window.HANDS; window.IK=false; window.ALLLAYERS=true;
    const M=m=>Array.from(m.elements);
    const vis=o=>{ for(let x=o;x;x=x.parent){ if(!x.visible) return false; } return true; };
    const camNode=W.byPath['Camera_animated'];
    const camInv=()=>{ const p=new T.Vector3(), cq=new T.Quaternion(); camNode.getWorldPosition(p); camNode.getWorldQuaternion(cq);
      const f=new T.Vector3(0,-1,0).applyQuaternion(cq), u=new T.Vector3(0,0,1).applyQuaternion(cq); const sd=new T.Vector3().crossVectors(f,u).normalize();
      f.applyAxisAngle(sd,cfg.pitch); u.applyAxisAngle(sd,cfg.pitch);
      const z=f.clone().negate().normalize(); const x=new T.Vector3().crossVectors(u,z).normalize(); const y=new T.Vector3().crossVectors(z,x);
      const cw=new T.Matrix4().makeBasis(x,y,z).setPosition(p); return cw.invert(); };
    const hb=H.skeleton.bones; const meshNodes=[]; W.nodes.forEach((n,i)=>{ if(n.mesh && !n.mesh.skin && vis(n.obj)) meshNodes.push(i); });
    const bones=hb.map(o=>({name:o.name,kind:'hand'})).concat(meshNodes.map(i=>({name:W.nodes[i].name,kind:'mesh',node:i})));
    const objs=hb.concat(meshNodes.map(i=>W.nodes[i].obj));
    const aim=W.root.getObjectByName('mod_aim_camera'); const muzzle=(()=>{let r=null; W.root.traverse(o=>{ if(!r&&o.name==='mod_muzzle') r=o;}); return r;})();
    const snap=()=>{ W.root.updateMatrixWorld(true); const ci=camInv(); return objs.map(o=>M(ci.clone().multiply(o.matrixWorld))); };
    pose(null,0); const rest=snap(); const ci0=camInv();
    const boneInv=H.skeleton.boneInverses.map(M);
    const handWorld0=hb.map(o=>M(o.matrixWorld)); const cam0=M(ci0);
    const aim0=aim?M(ci0.clone().multiply(aim.matrixWorld)):null; const muz0=muzzle?M(ci0.clone().multiply(muzzle.matrixWorld)):null;
    const clips={};
    for(const name of cfg.clips){ const layers=window.ANIMS?null:null; const nf=Math.max(...window.clipsNF(name)); const frames=[];
      for(let f=0;f<nf;f++){ pose(name,f); frames.push(snap()); } clips[name]={fps:30, frames}; }
    return {bones,rest,boneInv,handWorld0,cam0,aim0,muz0,clips};
  },cfg);
}
fs.writeFileSync(OUT,JSON.stringify(out)); console.log('wrote',OUT,Object.keys(out.clips).map(k=>k+':'+out.clips[k].frames.length).join(' '));
await b.close(); srv.close();})();
