const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
const jobs=JSON.parse(process.argv[2]);
for(const [url,out,w,h] of jobs){const p=await b.newPage({viewport:{width:w||1280,height:h||720}});
await p.goto('http://127.0.0.1:8765/'+url);await p.waitForFunction(()=>document.title.startsWith('done')||document.title.startsWith('err'),null,{timeout:600000});
console.log(out,await p.title());await p.locator('canvas').screenshot({path:out});await p.close();}
await b.close();})();
