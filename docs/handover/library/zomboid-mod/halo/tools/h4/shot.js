const {chromium}=require('playwright');
(async()=>{
 const mode=process.argv[2]||'holo', out=process.argv[3]||'/tmp/claude-0/web/shot.png';
 const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const p=await b.newPage({viewport:{width:960,height:540}});
 p.on('console',m=>console.log('console',m.text())); p.on('pageerror',e=>console.log('err',e.message));
 await p.goto('http://localhost:8765/index.html?mode='+mode);
 await p.waitForFunction('window.READY===true',null,{timeout:60000});
 await p.waitForTimeout(1500);
 const views=JSON.parse(process.argv[4]||'[{"yaw":0},{"yaw":1.57}]');
 const fs=require('fs');
 for(let i=0;i<views.length;i++){ const u=await p.evaluate(([v])=>window.frame(v.t||1.0,v),[views[i]]); fs.writeFileSync(out.replace('.png','_'+i+'.png'),Buffer.from(u.split(',')[1],'base64')); }
 console.log(await p.evaluate('JSON.stringify(window.BOX)'));
 await b.close();
})();
