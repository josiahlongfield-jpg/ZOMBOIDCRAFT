"""Read a GLB back, optional bone pose, linear-blend skin, textured raster. Validates the exported file."""
import sys,json,struct,io,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from raster import render
from PIL import Image
CT={5122:np.int16,5126:np.float32,5125:np.uint32,5123:np.uint16,5121:np.uint8}
NC={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def load(path):
    b=open(path,'rb').read(); jl=struct.unpack_from('<I',b,12)[0]; j=json.loads(b[20:20+jl]); bin_=b[20+jl+8:]
    def acc(i):
        a=j['accessors'][i]; v=j['bufferViews'][a['bufferView']]
        return np.frombuffer(bin_,CT[a['componentType']],a['count']*NC[a['type']],v['byteOffset']).reshape(a['count'],NC[a['type']])
    def img(t):
        im=j['images'][j['textures'][t]['source']]; v=j['bufferViews'][im['bufferView']]
        return np.array(Image.open(io.BytesIO(bin_[v['byteOffset']:v['byteOffset']+v['byteLength']])).convert('RGBA'))
    return j,acc,img
def qm(q):
    x,y,z,w=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def axisq(ax,deg):
    a=np.radians(deg)/2; ax=np.array(ax,float)/np.linalg.norm(ax); return np.r_[ax*np.sin(a),np.cos(a)]
def qmul(a,b):
    x1,y1,z1,w1=a;x2,y2,z2,w2=b
    return np.array([w1*x2+x1*w2+y1*z2-z1*y2,w1*y2-x1*z2+y1*w2+z1*x2,w1*z2+x1*y2-y1*x2+z1*w2,w1*w2-x1*x2-y1*y2-z1*z2])
def scene(path,select=None,pose={},absq=None):
    j,acc,img=load(path)
    nodes=j['nodes']; parent={}
    for i,n in enumerate(nodes):
        for c in n.get('children',[]): parent[c]=i
    world={}
    def W(i):
        if i in world: return world[i]
        n=nodes[i]; q=np.array(n.get('rotation',[0,0,0,1]))
        if n.get('name') in pose: q=qmul(q,axisq(*pose[n['name']]))
        tr=n.get('translation',[0,0,0])
        if absq and n.get('name') in absq: q,tr=absq[n['name']]
        M=np.eye(4); M[:3,:3]=qm(q); M[:3,3]=tr
        if 'matrix' in n: M=np.array(n['matrix'],float).reshape(4,4).T
        if i in parent: M=W(parent[i])@M
        world[i]=M; return M
    JMS=[]
    for skin in j.get('skins',[]):
        ibm=acc(skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
        JMS.append(np.array([W(jn)@ibm[k] for k,jn in enumerate(skin['joints'])]))
    V=[];T=[];UV=[];MM=[];tex={};base=0
    vis=set(); st=list(j['scenes'][0]['nodes'])
    while st:
        i=st.pop(); vis.add(i); st+=nodes[i].get('children',[])
    for ni,n in enumerate(nodes):
        if 'mesh' not in n or ni not in vis: continue
        if select and not select(n['name']): continue
        skin='skin' in n
        if skin: JM=JMS[n['skin']]
        for p in j['meshes'][n['mesh']]['primitives']:
            P=acc(p['attributes']['POSITION']).astype(np.float64)
            if not skin:
                Mw=W(ni); P=P@Mw[:3,:3].T+Mw[:3,3]
            if skin:
                Jn=acc(p['attributes']['JOINTS_0']).astype(int); Wt=acc(p['attributes']['WEIGHTS_0'])
                Ph=np.c_[P,np.ones(len(P))]; out=np.zeros_like(P)
                for c in range(4):
                    out+=Wt[:,c:c+1]*np.einsum('nij,nj->ni',JM[Jn[:,c]],Ph)[:,:3]
                P=out
            V.append(P); T.append(acc(p['indices']).reshape(-1,3).astype(np.int64)+base); UV.append(acc(p['attributes']['TEXCOORD_0']))
            m=p.get('material',0); mt=j['materials'][m]['pbrMetallicRoughness'].get('baseColorTexture')
            if mt is not None and m not in tex: tex[m]=img(mt['index'])
            MM.append(np.full(len(T[-1]),m)); base+=len(P)
    return np.concatenate(V),np.concatenate(T),np.concatenate(UV),np.concatenate(MM),tex
def shot(path,out,select=None,pose={},views=((0,0.15,1),(1,0.2,0.4)),W=520,H=640,zoom=1.0,absq=None,fixc=None):
    V,T,UV,MM,tex=scene(path,select,pose,absq)
    c=(V.min(0)+V.max(0))/2; rad=np.linalg.norm(V.max(0)-V.min(0))*zoom
    if fixc is not None: c,rad=np.array(fixc[0]),fixc[1]
    imgs=[render(V,T,None,eye=c+np.array(e)/np.linalg.norm(e)*rad*1.6,tgt=c,W=W,H=H,UV=UV,tex=tex,M=MM,fov=30) for e in views]
    o=Image.new('RGB',(W*len(imgs),H))
    for k,i in enumerate(imgs): o.paste(i,(W*k,0))
    o.save(out)
if __name__=='__main__':
    shot(sys.argv[1],sys.argv[2])
