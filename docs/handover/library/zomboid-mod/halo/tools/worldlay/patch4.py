p='/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/worldlay/index.html';s=open(p).read()
def rep(a,b):
    global s
    assert s.count(a)==1,a[:80]; s=s.replace(a,b)
rep('<div class="label">Endless Night · Halo Reach world · layout 02 · a brand new world · draft for your OK</div>','<div class="label">Endless Night · Halo Reach world · layout 03 · 16 km open world · draft for your OK</div>')
a=s.index('<p class="lede">');b=s.index('</p>',a)+4
s=s[:a]+"""<p class="lede">A <b>brand new 16 km open world</b>, separate from your county world, which stays exactly as it is. Each campaign level sits inside <b>its own real surroundings</b>: the valleys, mountains, bays and coasts that Reach shows past the edge of each mission, taken from the game files and drawn here at true scale. So most of the land between the missions is real Reach scenery, not invented ground. Every mission keeps its full layout, but its invisible walls come out, so you can walk off any mission path into the open land. Gold outlines mark the original playable areas, coloured dots are the missions' own enemy squads, and diamonds are new settlements and bases. Drag to pan, scroll or pinch to zoom, and tap a place for details.</p>"""+s[b:]
rep('<button class="chipbtn" data-l="roads" aria-pressed="true">Roads</button>','<button class="chipbtn" data-l="squads" aria-pressed="true">Enemy squads</button>')
rep("""<li><b>One ground.</b> Every level sits on a single world ground. Around each one, a blend band of 150 to 300 m eases the new land from the level's own edge heights to the height of the region around it, so there are no cliffs or steps at a seam.</li>""",
"""<li><b>Real surroundings first.</b> Each mission sits inside its own far scenery, so the first few kilometres around every mission are exactly what Reach shows from inside it. That scenery was built to be seen from far away, so it gets a detail pass where you walk: ground textures at full resolution, rocks, trees, grass and props taken from the missions.</li>
      <li><b>No funnelling.</b> Mission invisible walls, kill zones and soft ceilings are removed, and the surrounding landscape gets solid ground, so every mission can be entered and left from any side.</li>
      <li><b>One ground between.</b> Where two surroundings meet, the land eases from one to the other over about 1.5 km (the faded edges on the map), matching heights so there are no cliffs or steps at a seam.</li>""")
rep("""<li><b>One distant horizon.</b> Each level's painted far-off scenery is removed. A single ring of mountains, built from Reach backdrop terrain, surrounds the whole map.</li>""",
"""<li><b>One distant horizon.</b> Past the 16 km edge, a single ring of mountains and sea, built from the outer parts of the same Reach scenery, surrounds the world.</li>""")
a=s.index('<h2>What fills the rest</h2>');b=s.index('</div>',a)
s=s[:a]+"""<h2>What fills the rest</h2>
    <p class="flow">Real Reach landscape (the missions plus their own surroundings) covers about <b id="landkm"></b> km² of the 268 km² world. The rest is new land in the style of the nearest region:</p>
    <ul class="plain" id="biomes"></ul>
    <h3 style="margin-top:6px">Settlements, mini bases and activities</h3>
    <p class="flow">These are spread through the open land (diamonds on the map): UNSC firebases to retake and hold, Covenant forward bases and dig sites to clear, farmsteads and relay outposts, an evacuation port, a shipbreaker camp and survivor holdouts. The PLANS list (base sites, pod drops, Phantom drops, Noble rescues) goes on top. Zomboid zombie hordes roam everywhere, led by the Covenant squads.</p>
    <ul class="plain" id="kits"></ul>
    <h3 style="margin-top:6px">Grand designs, a starting list</h3>
    <p class="flow">Big new structures built to Reach's own look and detail, from its kit pieces and textures. These are starting points for when we brainstorm.</p>
    <ul class="plain">
      <li><b style="color:#e0605a;font-weight:500">Covenant excavation spire.</b> A Spire-style tower driven into a glassed crater, with a gravity lift, shield gate and Engineer-run dig pit below. It's a multi-stage assault.</li>
      <li><b style="color:#e0605a;font-weight:500">Covenant staging ground.</b> A cliff-top Covenant camp: Phantom landing pads, Shade nests, Wraith pens, supply pods and a holo command tent. It keeps being reinforced until you clear it.</li>
      <li><b style="color:#e0605a;font-weight:500">Downed corvette.</b> A second Covenant corvette crashed across a valley, built from the corvette kit, so the interior can be explored at ground level.</li>
      <li><b style="color:#e0605a;font-weight:500">Glassing front.</b> A scorched band of glassed ground and melted towns on the edge of the map, with a cruiser overhead. It's the most dangerous area.</li>
      <li><b style="color:#6fb3c8;font-weight:500">Orbital defence generator.</b> A huge UNSC power complex dug into a mountain. Its reactor halls, cooling towers and maglev freight line power the city's defences, and it's a headline purge objective.</li>
      <li><b style="color:#6fb3c8;font-weight:500">ONI black site.</b> A hidden base under the mountains, like Sword Base and Halsey's lab: hangar doors in the rock, labs, holding cells and vaults. It's a natural home for the redressed bunkers.</li>
      <li><b style="color:#6fb3c8;font-weight:500">Firebase network.</b> Ridge-top firebases with Pelican pads, sandbag lines and turrets, linked by Warthog roads. They're the bases you take back and upgrade.</li>
      <li><b style="color:#6fb3c8;font-weight:500">Spaceport and evac terminal.</b> A civilian spaceport outside New Alexandria with launch gantries, terminal halls and abandoned queues. It holds survivor rescues and a last stand.</li>
    </ul>
    <p class="mono">Not placed: Noble Actual is the opening cutscene, and its far scenery is part of the horizon. The Pillar of Autumn's cutscene section is not playable.</p>
  """+s[b:]
rep("""<li><b>World size.</b> Drawn here at 8 km across, so levels sit close together with blended land between. A smaller or bigger world is possible; I've sent this as a separate question.</li>""",
"""<li><b>Sword Base and The Package are the same valley in the game</b>, once in summer and once in winter. I've put The Package's snowy copy up in the north-east mountains so both are playable. The other option is to keep one valley and put The Package's unique parts in it: Halsey's lab, the ice caves and the snowbound outpost.</li>
    <li><b>Enemy squads</b> are shown for Long Night of Solace, Winter Contingency and Sword Base. The other missions' squads come off your PC next time it's on, and they'll be drawn the same way.</li>""")
rep("""<li><b>Spots.</b> Any place you want moved: tell me the name and where it should go, for example "put Nightfall at the dam".</li>""","""<li><b>Spots.</b> Any place you want moved: tell me the name and where it should go, for example "put Exodus next to Aszod".</li>""")
# JS
rep("const KITSTYLE={city:['New Alexandria kit','#c9a96a'],farm:['Visegrád farm kit','#b8c46a'],coast:['Aszod coast kit','#6fb3c8'],unsc:['UNSC base kit','#8fb07a']};",
    "const KITSTYLE={unsc:['UNSC bases and outposts','#6fb3c8'],cov:['Covenant bases and sites','#e0605a'],farm:['Farms and holdouts','#b8c46a'],city:['City outskirts','#c9a96a'],coast:['Coast camps','#8fb0c8']};")
rep("const AREA_ORDER=['plains','ridges','highland','alpine','city','coast','sea'];const HW=4096;","const AREA_ORDER=['sea'];const HW=8192;")
rep("L={blend:true,roads:true,ship:true,labels:true,anniv:false}","L={blend:true,squads:true,ship:true,labels:true,anniv:false}")
rep("const PAD={x:763,z:-2786}, SHIP={x:763,z:-2786,rot:-25};","const PAD={x:1563,z:-1786}, SHIP={x:1563,z:-1786,rot:-25};")
rep("D.sites.forEach(s=>load(s.img));D.orbit.forEach(o=>load(o.img));","D.sites.forEach(s=>load(s.img));D.orbit.forEach(o=>load(o.img));D.patches.forEach(p=>load(p.img));$('#landkm').textContent=D.landkm2;")
rep("""<span><i style="background:none;border:1px solid var(--signal)"></i>Blend band</span><span>◆ Old town rebuilt from Reach kit</span><span style="color:var(--signal)">◎ Beam-up pad</span>`;""",
    """<span><i style="background:none;border:1px solid var(--signal)"></i>Original playable area</span><span><i style="background:#e0605a;border-radius:50%"></i>Covenant squad</span><span><i style="background:#6fb3c8;border-radius:50%"></i>UNSC squad</span><span>◇ squad with vehicles</span><span>◆ settlement or base</span><span style="color:var(--signal)">◎ Beam-up pad</span>`;""")
rep("$('#kpis').innerHTML=`<span>New world <b>8.2 × 8.2 km</b></span>","$('#kpis').innerHTML=`<span>New world <b>16.4 × 16.4 km</b></span>")
rep("<span>Real Reach ground <b>${km2.toFixed(1)} km²</b></span><span>New land between <b>${(67-km2).toFixed(0)} km²</b></span>","<span>Mission ground <b>${km2.toFixed(1)} km²</b></span><span>Real Reach landscape <b>${D.landkm2} km²</b></span><span>Mission squads mapped <b>${D.squads.length}</b></span>")
rep("view.s=Math.min(r.width,r.height)/8900;","view.s=Math.min(r.width,r.height)/17400;")
rep("""  ctx.fillStyle='#3a462c';ctx.fillRect(-HW,-HW,2*HW,2*HW);
  const A={};C.areas.forEach(a=>A[a.id]=a);
  AREA_ORDER.forEach(id=>{if(!A[id])return;ctx.fillStyle=B[id];poly(A[id].p);ctx.fill()});""",
"""  ctx.fillStyle='#34402c';ctx.fillRect(-HW,-HW,2*HW,2*HW);
  ctx.fillStyle=B.sea;C.areas.forEach(a=>{poly(a.p);ctx.fill()});""")
rep("""  if(mode=='old'){ctx.fillStyle='rgba(120,120,120,.28)';C.urban.forEach(u=>{ctx.beginPath();ctx.arc(u.x,u.z,u.r,0,7);ctx.fill()})}""",
"""  D.patches.forEach(p=>{const im=imgs[p.img];if(im&&im.naturalWidth)ctx.drawImage(im,p.x-p.size/2,p.z-p.size/2,p.size,p.size)});""")
rep("""    if(L.blend&&!fast){order.forEach(st=>{const im""","""    if(L.blend&&!fast){order.filter(st=>!st.inland).forEach(st=>{const im""")
rep("""    // kit towns""","""    if(L.squads){const r=Math.max(9,2.6/s);D.squads.forEach(q=>{ctx.fillStyle=q.side=='cov'?'#e0605a':'#6fb3c8';ctx.strokeStyle='rgba(8,11,13,.9)';ctx.lineWidth=Math.max(2,.8/s);
      if(q.v){const k=r*1.7;ctx.beginPath();ctx.moveTo(q.x,q.z-k);ctx.lineTo(q.x+k,q.z);ctx.lineTo(q.x,q.z+k);ctx.lineTo(q.x-k,q.z);ctx.closePath();ctx.stroke();ctx.fill()}else{ctx.beginPath();ctx.arc(q.x,q.z,r,0,7);ctx.stroke();ctx.fill()}})}
    // kit towns""")
rep("D.kit.forEach(k=>{const c=KITSTYLE[k.style][1];const r=Math.max(40,5/s);","D.kit.forEach(k=>{const c=KITSTYLE[k.style][1];const r=Math.max(80,6/s);")
rep("if(s>.11)D.kit.forEach(k=>lab(k.name,k.x,k.z,12,KITSTYLE[k.style][1],14));","if(s>.05)D.kit.forEach(k=>lab(k.name,k.x,k.z,12,KITSTYLE[k.style][1],16));")
rep("vis().forEach(st=>{const big=st.w*st.d>500000;if(!big&&s<.07&&st.id!==sel)return;","vis().forEach(st=>{const big=st.w*st.d>500000;if(!big&&s<.035&&st.id!==sel)return;")
# tooltip for squads
rep("""  if(!ptrs.has(e.pointerId)){const st=hit(e.clientX-r.left,e.clientY-r.top);""","""  if(!ptrs.has(e.pointerId)){const q=L.squads&&sq(e.clientX-r.left,e.clientY-r.top);if(q){tip.style.display='block';tip.textContent=`${q.n.replace(/^sq_/,'')} · ${q.c} ${q.ch.join(', ')}${q.v?' · '+q.v.replace(/_/g,' '):''}`;tip.style.left=(e.clientX-r.left+14)+'px';tip.style.top=(e.clientY-r.top+14)+'px';return}
    const st=hit(e.clientX-r.left,e.clientY-r.top);""")
rep("function fmt(n){","function sq(px,py){const [x,z]=S2W(px,py),rr=Math.max(14,7/view.s);let b=null,bd=rr;D.squads.forEach(q=>{const d=Math.hypot(q.x-x,q.z-z);if(d<bd){bd=d;b=q}});return b}\nfunction fmt(n){")
open(p,'w').write(s)
