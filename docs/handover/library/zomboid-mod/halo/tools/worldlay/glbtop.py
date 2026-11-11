"""Top-down true-scale render of GLB render geometry (backdrop landscapes)."""
import sys,json,struct,os,numpy as np
sys.path.insert(0,'/tmp/claude-0/halo')
from survey import samples
from PIL import Image
CT={5120:np.int8,5121:np.uint8,5122:np.int16,5123:np.uint16,5125:np.uint32,5126:np.float32}
NC={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def readglb(p):
    b=open(p,'rb').read(); jl=struct.unpack_from('<I',b,12)[0]; J=json.loads(b[20:20+jl]); o=20+jl
    bl=struct.unpack_from('<I',b,o)[0]; B=memoryview(b)[o+8:o+8+bl]
    def acc(i):
        a=J['accessors'][i]; v=J['bufferViews'][a['bufferView']]; n=NC[a['type']]; dt=CT[a['componentType']]
        st=v.get('byteStride',0); off=v.get('byteOffset',0)+a.get('byteOffset',0); cnt=a['count']
        isz=np.dtype(dt).itemsize*n
        if st and st!=isz:
            raw=np.frombuffer(B,np.uint8,count=st*(cnt-1)+isz,offset=off)
            idx=np.arange(cnt)[:,None]*st+np.arange(isz)[None]
            return raw[idx].copy().view(dt).reshape(cnt,n)
        return np.frombuffer(B,dt,count=cnt*n,offset=off).reshape(cnt,n)
    return J,acc
def quat(q):
    x,y,z,w=q; return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def nodemat(n):
    if 'matrix' in n: return np.array(n['matrix']).reshape(4,4).T
    M=np.eye(4); R=quat(n.get('rotation',[0,0,0,1])); S=np.diag(n.get('scale',[1,1,1]))
    M[:3,:3]=R@S; M[:3,3]=n.get('translation',[0,0,0]); return M
def tris(p):
    J,acc=readglb(p); out=[]; cache={}
    def mesh(mi):
        if mi in cache: return cache[mi]
        res=[]
        for pr in J['meshes'][mi]['primitives']:
            if pr.get('mode',4)!=4: continue
            V=acc(pr['attributes']['POSITION']).astype(np.float32)
            I=acc(pr['indices']).reshape(-1).astype(np.int64) if 'indices' in pr else np.arange(len(V))
            res.append((V,I.reshape(-1,3)))
        cache[mi]=res; return res
    def walk(ni,M):
        n=J['nodes'][ni]; M=M@nodemat(n)
        if 'mesh' in n:
            for V,T in mesh(n['mesh']):
                W=V@M[:3,:3].T+M[:3,3]; out.append((W.astype(np.float32),T))
        for c in n.get('children',[]): walk(c,M)
    for r in J['scenes'][J.get('scene',0)]['nodes']: walk(r,np.eye(4))
    return out
if __name__=='__main__':
    p=sys.argv[1]; G=tris(p)
    V=np.concatenate([v for v,t in G]); print(len(G),'parts',sum(len(t) for v,t in G),'tris',V.min(0).round(),V.max(0).round())
