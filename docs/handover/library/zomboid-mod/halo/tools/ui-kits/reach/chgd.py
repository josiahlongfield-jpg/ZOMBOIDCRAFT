import sys,os,struct,json
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from chdt import C
c=C(sys.argv[1]); d=c.d
k=[k for (g,n),k in c.byname.items() if g=='chgd'][0]
o=c.tago(k); print('chgd',c.nm[k])
G={}
for hg in c.items(o,0,0x534):
    cols=[]
    for i in range(32):
        a=c.f(hg+4+i*16,'f')[0]; rgb=c.f(hg+8+i*16,'3f'); cols.append([round(a,3)]+[round(x,3) for x in rgb])
    G['colors']=cols
    for i,cl in enumerate(cols): print(i,cl,'#%02x%02x%02x'%tuple(int(max(0,min(1,x))*255) for x in cl[1:]))
    G['attrs']=[]
    for a in c.items(hg,0x2dc,0x10c):
        e=dict(res=c.f(a+0x94,'2I'),hudoff=c.f(a+0x8c,'2f'),msoff=c.f(a+0x9c,'2f'),msr=c.f(a+0xa8,'2f'),hscale=c.f(a+0xd0,'4f'),notif=c.f(a+0xe0,'2f'),flags=d[a])
        G['attrs'].append(e); print(e)
    print('shield color',c.f(hg+0x498,'3f'),c.f(hg+0x4a8,'3f'),'health',c.f(hg+0x4e4,'3f'),c.f(hg+0x4f4,'3f'))
    G['shield']=c.f(hg+0x498,'3f'); G['health']=c.f(hg+0x4e4,'3f')
    for q in (0x3c0,0x420,0x430): print(hex(q),c.ref(hg+q))
    break
print('blip scales',c.f(o+0x68,'5f'))
json.dump(G,open(sys.argv[2],'w'),indent=1)
