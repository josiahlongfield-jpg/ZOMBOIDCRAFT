import json,numpy as np,sys
m=sys.argv[1]
exec(open('/tmp/claude-0/-home-user-zomboidcraft/bb435080-348c-5e82-b018-9d51a3f60885/scratchpad/towers.py').read().split("print(len(rows))")[0].replace("m=sys.argv[1]",""))
C=json.load(open(sys.argv[2])); out=sys.argv[3]; pre=sys.argv[4]
jobs=[];meta=[]
for k,(name,x,z,hw,hd) in enumerate(C):
    sel=[r for r in rows if (r[0] or '').startswith('inst') and abs((r[2][0]+r[3][0])/2-x)<hw and abs((r[2][2]+r[3][2])/2-z)<hd and max(r[3][0]-r[2][0],r[3][2]-r[2][2])<2*max(hw,hd)+40]
    lo=np.min([r[2] for r in sel],0); hi=np.max([r[3] for r in sel],0); c=(lo+hi)/2; h=hi[1]-lo[1]
    d=np.array([0.75,0.18,0.62]); d/=np.linalg.norm(d)
    rad=np.linalg.norm(hi-lo)/2; dist=rad/np.tan(np.radians(17))*1.0
    eye=c+d*dist
    url=f"view4.html?f=bd/{m}.glb&n={','.join(r[0] for r in sel)}&e={eye[0]:.0f},{eye[1]:.0f},{eye[2]:.0f}&t={c[0]:.0f},{c[1]:.0f},{c[2]:.0f}&fov=36&fogd=0.00005&fogc=0.2,0.24,0.3&lk=1.5&w=600&h=800"
    jobs.append([url,f"{pre}{k:02d}.png",600,800])
    meta.append(dict(name=name,x=float(c[0]),z=float(c[2]),base=float(lo[1]),top=float(hi[1]),h=float(h),w=float(hi[0]-lo[0]),d=float(hi[2]-lo[2]),parts=len(sel),
                     tris=int(sum(sum(acc[p['indices']]['count']//3 for p in j['meshes'][r[1]]['primitives']) for r in sel))))
    print(k,name,{a:round(b) if isinstance(b,float) else b for a,b in meta[-1].items()}, len(url))
json.dump(jobs,open(out,'w')); json.dump(meta,open(out.replace('.json','_meta.json'),'w'))
