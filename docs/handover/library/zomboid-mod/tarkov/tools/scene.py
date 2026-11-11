import UnityPy,json,struct,os,sys
from UnityPy.helpers.MeshHelper import MeshHandler
R='/mnt/user-data/uploads/raw/'
OUT='/tmp/claude-0/tk/web/data/'
os.makedirs(OUT,exist_ok=True)
TEXDONE={}
def tex_png(t):
    n=t.m_Name
    if n in TEXDONE: return TEXDONE[n]
    try:
        im=t.image
        if max(im.size)>1024: im=im.resize((im.size[0]//2,im.size[1]//2))
        p=n+'.png'; im.save(OUT+p); TEXDONE[n]=p; return p
    except Exception as ex:
        print('tex fail',n,ex); TEXDONE[n]=None; return None
def mat_info(mp):
    if mp.path_id==0: return None
    try: m=mp.read()
    except Exception as ex: return None
    info={'name':m.m_Name}
    for k,v in m.m_SavedProperties.m_TexEnvs:
        if k in('_MainTex','_BumpMap','_SpecMap','_GlossMap') and v.m_Texture.path_id:
            try: info[k]=tex_png(v.m_Texture.read())
            except Exception as ex: pass
    for k,v in m.m_SavedProperties.m_Colors:
        if k in ('_Color',): info[k]=[v.r,v.g,v.b,v.a]
    return info
MESHDONE={}
def mesh_bin(m,key):
    if key in MESHDONE: return MESHDONE[key]
    h=MeshHandler(m); h.process()
    V=h.m_Vertices; N=h.m_Normals; U=h.m_UV0
    n=h.m_VertexCount
    subs=h.get_triangles()
    buf=bytearray()
    def fl(arr,k):
        out=[]
        for i in range(n):
            v=arr[i] if arr is not None and i<len(arr) else (0,)*k
            out+=list(v)[:k]
        return struct.pack('<%df'%(n*k),*out)
    # Unity left-handed -> right-handed: negate x
    vv=[(-v[0],v[1],v[2]) for v in V]
    nn=[(-v[0],v[1],v[2]) for v in N] if N else None
    uu=[(v[0],v[1]) for v in U] if U else None
    head={'n':n,'subs':[]}
    buf+=fl(vv,3); buf+=fl(nn,3); buf+=fl(uu,2)
    if h.m_BoneWeights:
        buf+=fl(h.m_BoneIndices,4); buf+=fl(h.m_BoneWeights,4); head['skin']=True
    for s in subs:
        idx=[]
        for tri in s: idx+= [tri[0],tri[2],tri[1]]  # flip winding
        head['subs'].append(len(idx)); buf+=struct.pack('<%dI'%len(idx),*idx)
    p='%s.bin'%key; open(OUT+p,'wb').write(buf); head['file']=p
    if h.m_BoneWeights:
        head['bind']=[list(bp.e00 for _ in [0]) and [bp.e00,bp.e01,bp.e02,bp.e03,bp.e10,bp.e11,bp.e12,bp.e13,bp.e20,bp.e21,bp.e22,bp.e23,bp.e30,bp.e31,bp.e32,bp.e33] for bp in m.m_BindPose]
    MESHDONE[key]=head; return head
def export_root(env,rootT,tag):
    nodes=[]
    def walk(tp,parent):
        t=tp.read() if hasattr(tp,'read') else tp
        go=t.m_GameObject.read()
        q=t.m_LocalRotation; p=t.m_LocalPosition; s=t.m_LocalScale
        node={'name':go.m_Name,'parent':parent,'p':[-p.x,p.y,p.z],'q':[q.x,-q.y,-q.z,q.w],'s':[s.x,s.y,s.z],'active':go.m_IsActive}
        mf=mr=None
        for c in go.m_Component:
            co=c.component if hasattr(c,'component') else c
            try:
                o=co.deref()
                if o.type.name=='MeshFilter': mf=co.read()
                if o.type.name in('MeshRenderer','SkinnedMeshRenderer'): mr=co.read(); node['rtype']=o.type.name
            except Exception: pass
        try:
          if mr is not None and node.get('rtype')=='SkinnedMeshRenderer':
            m=mr.m_Mesh.read(); node['mesh']=mesh_bin(m,tag+'_'+m.m_Name+'_%d'%mr.m_Mesh.path_id)
            node['mats']=[mat_info(x) for x in mr.m_Materials]
          elif mf is not None and mf.m_Mesh.path_id:
            m=mf.m_Mesh.read(); node['mesh']=mesh_bin(m,tag+'_'+m.m_Name+'_%d'%mf.m_Mesh.path_id)
            node['mats']=[mat_info(x) for x in mr.m_Materials] if mr else []
        except Exception as ex:
            node['meshfail']=str(ex)[:80]
        i=len(nodes); nodes.append(node)
        for c in t.m_Children: walk(c,i)
    walk(rootT,-1)
    return nodes
def roots(env):
    out={}
    for o in env.objects:
        if o.type.name=='Transform':
            t=o.read()
            if t.m_Father.path_id==0: out[t.m_GameObject.read().m_Name]=t
    return out
