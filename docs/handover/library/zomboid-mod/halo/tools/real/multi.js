// usage: node multi.js <url with cams=...> <out prefix>   -> saves <prefix>_<i>.jpg for each camera
const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
const p=await b.newPage({viewport:{width:1280,height:720}});p.on('pageerror',e=>console.log('E:',e.message));
await p.goto('http://127.0.0.1:8766/'+process.argv[2]);await p.waitForFunction(()=>document.title.startsWith('done')||document.title.startsWith('err'),null,{timeout:1800000});
console.log(await p.title());console.log('EXS',JSON.stringify(await p.evaluate(()=>window.EXS||[])));const S=await p.evaluate(()=>window.SHOTS||[]);
S.forEach((d,i)=>fs.writeFileSync(process.argv[3]+'_'+i+'.jpg',Buffer.from(d.split(',')[1],'base64')));console.log('saved',S.length);await b.close();})();
