const {chromium}=require('playwright');
const S=process.argv[2];
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1300,height:1000}});
p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8767/brita-review.html');await p.waitForTimeout(4000);
await p.getByRole('button',{name:'Pause turning'}).click();
const n=await p.locator('#rackbar button').count();
for(let i=0;i<n;i++){
  await p.locator('#rackbar button').nth(i).click(); await p.waitForTimeout(500);
  const mag=p.locator('#sl_Magazine'); if(await mag.count()){const o=await mag.locator('option').nth(1); if(await o.count()) await mag.selectOption({index:1});}
  await p.getByRole('button',{name:'Side view'}).click(); await p.waitForTimeout(700);
  await p.locator('#view').screenshot({path:S+'/review/w2/'+i+'.png'});
}
await p.screenshot({path:S+'/review/w2/page.png',fullPage:true});
await b.close();})();
