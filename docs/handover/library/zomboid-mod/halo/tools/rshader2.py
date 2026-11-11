"""Full Reach render-method (shader) decoder: options per category, textures by parameter name, real constants by name."""
import sys,struct
sys.path.insert(0,'/tmp/claude-0/halo')
from reach import X
_RMDF={}
def _blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(X(a)) if c else 0)
def rmdf_cats(r,dat):
    if dat in _RMDF: return _RMDF[dat]
    d=r.d; ro=r.off(X(r.tags[dat&0xffff][2])); c,a=_blk(r,ro,0x10); cats=[]
    for i in range(c):
        e=a+i*24; nmc=r.sid(struct.unpack_from('<I',d,e)[0]); oc,oa=_blk(r,e,4); opts=[]
        for st in (0x1c,0x18,0x20,0x24,0x28,0x14,0x10):
            nn=[r.sid(struct.unpack_from('<I',d,oa+q*st)[0]) for q in range(oc)]
            if all(x and not x.startswith('?') for x in nn): opts=nn;break
        cats.append((nmc,opts))
    _RMDF[dat]=cats; return cats
def sids(r,o,k):
    c,a=_blk(r,o,k); return [r.sid(struct.unpack_from('<I',r.d,a+i*4)[0]) for i in range(c)]
def shader_info(r,nm,datum,at=None,name=None,grp=None):
    d=r.d
    if at is not None: so=at; k=None
    else:
        if datum==0xffffffff or (datum&0xffff)>=len(r.tags): return None
        k=datum&0xffff; grp=r.tags[k][0]; so=r.off(X(r.tags[k][2]))
    rmdf=struct.unpack_from('<I',d,so+0xc)[0]
    oc,oa=_blk(r,so,0x20); opt=[struct.unpack_from('<h',d,oa+i*2)[0] for i in range(oc)]
    cats=rmdf_cats(r,rmdf) if rmdf!=0xffffffff else []
    options={}
    for i,(cn,ops) in enumerate(cats):
        v=opt[i] if i<len(opt) else 0
        options[cn]=ops[v] if 0<=v<len(ops) else v
    pc,pa=_blk(r,so,0x38)
    info=dict(name=name if k is None else nm[k],group=grp,options=options,textures={},reals={})
    if not pc: return info
    p=pa; tk=struct.unpack_from('<I',d,p+0xc)[0]
    if tk==0xffffffff: return info
    to=r.off(X(r.tags[tk&0xffff][2])); info['template']=nm[tk&0xffff]
    tn=sids(r,to,0x6c); rn=sids(r,to,0x48)
    c,a=_blk(r,p,0x10)
    for i in range(c):
        dat=struct.unpack_from('<I',d,a+i*24+0xc)[0]
        if i<len(tn) and dat!=0xffffffff and (dat&0xffff)<len(nm) and not nm[dat&0xffff].startswith('rasterizer'):
            info['textures'][tn[i]]=nm[dat&0xffff]
    c,a=_blk(r,p,0x1c)
    for i in range(c):
        if i<len(rn): info['reals'][rn[i]]=[round(v,5) for v in struct.unpack_from('<4f',d,a+i*16)]
    return info
if __name__=='__main__':
    from rres import RR
    import export,json
    r=RR(sys.argv[1]); nm=r.names()
    o=r.off(X(r.tags[int(sys.argv[2])][2]))
    sds=export.shader_datums(r,o+0x144-0x48)
    from collections import Counter
    c=Counter(); infos=[]
    for s in sds:
        i=shader_info(r,nm,s); infos.append(i)
        if i: c[(i['group'],i['options'].get('blend_mode'),i['options'].get('albedo'),i['options'].get('self_illumination'))]+=1
    for k,v in c.most_common(): print(v,k)
    json.dump(infos,open(sys.argv[3],'w'),indent=1)
