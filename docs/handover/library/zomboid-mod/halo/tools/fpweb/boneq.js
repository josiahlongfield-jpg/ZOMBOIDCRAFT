const {chromium}=require('playwright');
const [w,clip,t,fov,qs]=process.argv.slice(2);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage();
await p.goto(`http://127.0.0.1:8765/index.html?f=fp/spartan_fp_${w}.glb&fov=${fov||49}${qs||""}`);await p.waitForFunction('window.READY===true',null,{timeout:180000});
const r=await p.evaluate(([clip,t])=>{const T=window.__T,G=window.__G,C=window.__C;window.frame(clip,+t);G.updateMatrixWorld(true);C.updateMatrixWorld();
 const res={};const PR={};G.traverse(o=>{if(!o.isSkinnedMesh||!o.visible)return;let a=o,vis=true;while(a){if(!a.visible)vis=false;a=a.parent;}if(!vis)return;
  const pos=o.geometry.attributes.position,si=o.geometry.attributes.skinIndex,sw=o.geometry.attributes.skinWeight;const v=new T.Vector3();
  for(let i=0;i<pos.count;i++){o.getVertexPosition(i,v);v.applyMatrix4(o.matrixWorld);const pr=v.clone().project(C);
   let bi=0,bw=-1;for(let k=0;k<4;k++){const ww=sw.getComponent(i,k);if(ww>bw){bw=ww;bi=si.getComponent(i,k);}}
   const bn=o.skeleton.bones[bi].name;PR[o.name]=PR[o.name]||{mat:o.material.name,b:{}};PR[o.name].b[bn]=(PR[o.name].b[bn]||0)+1;const key=o.name+'|'+bn;res[key]=res[key]||[0,0,9,9];res[key][0]++;
   if(pr.z<1&&pr.z>-1&&Math.abs(pr.x)<1&&Math.abs(pr.y)<1){res[key][1]++;res[key][2]=Math.min(res[key][2],pr.x);res[key][3]=Math.min(res[key][3],pr.y);}}});
 return {res,PR};},[clip,t]);
console.log(JSON.stringify(r.PR,(k,v)=>v));for(const [k,v] of Object.entries(r.res))if(v[1])console.log(k,v[0],'inview',v[1],'minx',v[2].toFixed(2),'miny',v[3].toFixed(2));
await b.close();})();
