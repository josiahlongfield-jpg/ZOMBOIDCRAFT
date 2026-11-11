const {chromium}=require('playwright');const fs=require('fs');
const J=JSON.parse(process.argv[2]);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
for(const [w,qs,clip,t,out,cap] of J){const p=await b.newPage({viewport:{width:960,height:540}});p.on('pageerror',e=>console.log('err',e.message));
await p.goto(`http://127.0.0.1:8765/index.html?f=fp/spartan_fp_${w}.glb&fov=49${qs}`);await p.waitForFunction('window.READY===true',null,{timeout:180000});
const u=await p.evaluate(([n,t,c])=>window.frame(n,t,c),[clip,t,cap]);fs.writeFileSync(out,Buffer.from(u.split(',')[1],'base64'));await p.close();}
await b.close();})();
