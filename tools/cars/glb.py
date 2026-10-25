"""Minimal glTF binary (.glb) reader: world-space triangles per node plus the base colour image."""
import json, struct, io, numpy as np
from PIL import Image
CT={5120:np.int8,5121:np.uint8,5122:np.int16,5123:np.uint16,5125:np.uint32,5126:np.float32}
NC={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def _mat(n):
    if 'matrix' in n: return np.array(n['matrix']).reshape(4,4).T
    t=np.array(n.get('translation',[0,0,0])); r=n.get('rotation',[0,0,0,1]); s=np.array(n.get('scale',[1,1,1]))
    x,y,z,w=r
    R=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
    M=np.eye(4); M[:3,:3]=R*s; M[:3,3]=t; return M
def load(path):
    """Returns list of parts: dict(name, P (n,3,3) world, UV (n,3,2)), and the base colour image."""
    b=open(path,'rb').read(); n=struct.unpack('<I',b[12:16])[0]; j=json.loads(b[20:20+n])
    off=20+n; bl=struct.unpack('<I',b[off:off+4])[0]; bin_=b[off+8:off+8+bl]
    def acc(i):
        a=j['accessors'][i]; bv=j['bufferViews'][a['bufferView']]; dt=CT[a['componentType']]; nc=NC[a['type']]
        start=bv.get('byteOffset',0)+a.get('byteOffset',0); stride=bv.get('byteStride',0)
        isz=np.dtype(dt).itemsize*nc
        if stride and stride!=isz:
            raw=np.frombuffer(bin_,np.uint8,count=stride*(a['count']-1)+isz,offset=start)
            idx=np.arange(a['count'])[:,None]*stride+np.arange(isz)
            return raw[idx].copy().view(dt).reshape(a['count'],nc)
        return np.frombuffer(bin_,dt,count=a['count']*nc,offset=start).reshape(a['count'],nc)
    img=None
    if j.get('images'):
        im=j['images'][0]; bv=j['bufferViews'][im['bufferView']]
        img=Image.open(io.BytesIO(bin_[bv.get('byteOffset',0):bv.get('byteOffset',0)+bv['byteLength']])).convert('RGBA')
    parts=[]
    def walk(ni,M):
        nd=j['nodes'][ni]; W=M@_mat(nd)
        if 'mesh' in nd:
            for p in j['meshes'][nd['mesh']]['primitives']:
                pos=acc(p['attributes']['POSITION']).astype(np.float64)
                uv=acc(p['attributes']['TEXCOORD_0']).astype(np.float64) if 'TEXCOORD_0' in p['attributes'] else np.zeros((len(pos),2))
                ind=acc(p['indices']).ravel() if 'indices' in p else np.arange(len(pos))
                wp=(np.c_[pos,np.ones(len(pos))]@W.T)[:,:3]
                if np.linalg.det(W[:3,:3])<0: ind=ind.reshape(-1,3)[:,::-1].ravel()
                parts.append({'name':nd.get('name',''),'P':wp[ind].reshape(-1,3,3),'UV':uv[ind].reshape(-1,3,2)})
        for c in nd.get('children',[]): walk(c,W)
    sc=j['scenes'][j.get('scene',0)]
    for r in sc['nodes']: walk(r,np.eye(4))
    return parts,img
