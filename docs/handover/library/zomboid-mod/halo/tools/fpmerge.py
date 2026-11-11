import sys,json,struct,glob,os,pickle,numpy as np;sys.path.insert(0,'/tmp/claude-0/halo')
from animexp import loadglb
from jmad import RR,graph,blk
def merge(arms,weapon,parent):
    A=loadglb(arms); W=loadglb(weapon); j=A.j; w=W.j
    while len(A.bin)%4: A.bin+=b'\0'
    off=len(A.bin); A.bin+=W.bin
    o={k:len(j.get(k,[])) for k in ('bufferViews','accessors','meshes','nodes','materials','textures','images','skins')}
    for v in w['bufferViews']: v=dict(v); v['byteOffset']=v.get('byteOffset',0)+off; j['bufferViews'].append(v)
    for a in w['accessors']: a=dict(a); a['bufferView']+=o['bufferViews']; j['accessors'].append(a)
    for im in w.get('images',[]): im=dict(im); im['bufferView']+=o['bufferViews']; j.setdefault('images',[]).append(im)
    for t in w.get('textures',[]): t=dict(t); t['source']+=o['images']; t['sampler']=0; j.setdefault('textures',[]).append(t)
    for m in w.get('materials',[]):
        m=json.loads(json.dumps(m))
        for key in ('baseColorTexture','metallicRoughnessTexture'):
            if key in m.get('pbrMetallicRoughness',{}): m['pbrMetallicRoughness'][key]['index']+=o['textures']
        for key in ('normalTexture','emissiveTexture','occlusionTexture'):
            if key in m: m[key]['index']+=o['textures']
        j.setdefault('materials',[]).append(m)
    for m in w['meshes']:
        m=json.loads(json.dumps(m))
        for p in m['primitives']:
            p['attributes']={k:v+o['accessors'] for k,v in p['attributes'].items()}
            if 'indices' in p: p['indices']+=o['accessors']
            if 'material' in p: p['material']+=o['materials']
        j['meshes'].append(m)
    for s in w.get('skins',[]):
        s=dict(s); s['joints']=[x+o['nodes'] for x in s['joints']]
        if 'skeleton' in s: s['skeleton']+=o['nodes']
        if 'inverseBindMatrices' in s: s['inverseBindMatrices']+=o['accessors']
        j['skins'].append(s)
    for n in w['nodes']:
        n=dict(n)
        if 'children' in n: n['children']=[c+o['nodes'] for c in n['children']]
        if 'mesh' in n: n['mesh']+=o['meshes']
        if 'skin' in n: n['skin']+=o['skins']
        j['nodes'].append(n)
    names={n.get('name'):i for i,n in enumerate(j['nodes'][:o['nodes']])}
    wroots=[x+o['nodes'] for x in w['scenes'][0]['nodes']]
    for r_ in wroots:
        n=j['nodes'][r_]
        if 'mesh' in n: j['scenes'][0]['nodes'].append(r_)
        else: j['nodes'][names[parent]].setdefault('children',[]).append(r_)
    return A
