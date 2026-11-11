const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
const p=await b.newPage({viewport:{width:1280,height:720}});p.on('console',m=>{if(m.text().startsWith('PICK'))console.log(m.text())});
await p.goto('http://127.0.0.1:8766/'+process.argv[2]);await p.waitForFunction(()=>document.title.startsWith('done')||document.title.startsWith('err'),null,{timeout:1800000});
console.log(await p.title());await b.close();})();
