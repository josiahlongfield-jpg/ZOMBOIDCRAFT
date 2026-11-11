import json,struct,numpy as np,io
class GLB:
    def __init__(s):
        s.bin=bytearray(); s.j=dict(asset=dict(version='2.0',generator='zc-halo'),buffers=[],bufferViews=[],accessors=[],meshes=[],nodes=[],materials=[],textures=[],images=[],samplers=[dict(magFilter=9729,minFilter=9987,wrapS=10497,wrapT=10497)],scenes=[dict(nodes=[])],scene=0)
    def view(s,data,target=None):
        while len(s.bin)%4: s.bin+=b'\0'
        off=len(s.bin); s.bin+=data
        v=dict(buffer=0,byteOffset=off,byteLength=len(data))
        if target: v['target']=target
        s.j['bufferViews'].append(v); return len(s.j['bufferViews'])-1
    def acc(s,arr,typ,ctype,target=None,minmax=False,normalized=False):
        arr=np.ascontiguousarray(arr)
        bv=s.view(arr.tobytes(),target)
        a=dict(bufferView=bv,componentType=ctype,count=int(arr.shape[0]),type=typ)
        if normalized: a['normalized']=True
        if minmax: a['min']=arr.min(0).tolist(); a['max']=arr.max(0).tolist()
        s.j['accessors'].append(a); return len(s.j['accessors'])-1
    def image_uri(s,uri):
        s.j['images'].append(dict(uri=uri,mimeType='image/png'))
        s.j['textures'].append(dict(sampler=0,source=len(s.j['images'])-1)); return len(s.j['textures'])-1
    def image(s,png):
        bv=s.view(png); s.j['images'].append(dict(bufferView=bv,mimeType='image/png'))
        s.j['textures'].append(dict(sampler=0,source=len(s.j['images'])-1)); return len(s.j['textures'])-1
    def material(s,name,tex=None,alpha=False):
        m=dict(name=name,pbrMetallicRoughness=dict(metallicFactor=0.0,roughnessFactor=0.8),doubleSided=False)
        if tex is not None: m['pbrMetallicRoughness']['baseColorTexture']=dict(index=tex)
        if alpha: m['alphaMode']='MASK'
        s.j['materials'].append(m); return len(s.j['materials'])-1
    def save(s,path):
        while len(s.bin)%4: s.bin+=b'\0'
        s.j['buffers']=[dict(byteLength=len(s.bin))]
        for k in ('textures','images','materials'):
            if not s.j[k]: del s.j[k]
        if 'images' not in s.j: s.j.pop('samplers',None)
        js=json.dumps(s.j,separators=(',',':')).encode()
        while len(js)%4: js+=b' '
        out=struct.pack('<III',0x46546C67,2,12+8+len(js)+8+len(s.bin))+struct.pack('<II',len(js),0x4E4F534A)+js+struct.pack('<II',len(s.bin),0x004E4942)+bytes(s.bin)
        open(path,'wb').write(out)
