"""Builds a gun as the game does (TarkovRig: kit bones, hidden swapped kit pieces, parts at their slot frames) from the
real .zsk files, writing scene.json for ../web/zskview.html."""
import sys, struct, zlib, io, json, os, numpy as np
sys.path.insert(0, '/home/user/zomboidcraft/tools/npc')
from pack import scramble
from scipy.spatial.transform import Rotation as Rot
import fit
NPC='/mnt/project-files/zomboid-mod/tarkov/local-pc/npc/'; PK='/tmp/claude-0/tk/b2/pk/'
def read_zsk(path, model, out, pre):
    d = zlib.decompress(scramble(open(path, 'rb').read(), model)); b = io.BytesIO(d)
    u = lambda f: struct.unpack('>' + f, b.read(struct.calcsize('>' + f)))
    utf = lambda: b.read(u('H')[0]).decode()
    assert b.read(4) == b'ZSKN' and u('i')[0] == 1
    G = []
    for _ in range(u('i')[0]):
        mesh, piece, tex = utf(), utf(), utf(); fl, lv, n = u('bbi')
        rec = np.frombuffer(b.read(n * 27), dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('u', '>u2', 2), ('j', 'i1', 4), ('w', 'u1', 4)])
        t = u('i')[0]; T = np.frombuffer(b.read(t * 12), '>i4').reshape(-1, 3)
        G.append((mesh, piece, tex, rec, T, lv))
    texs = {}
    for _ in range(u('i')[0]):
        k = utf(); data = b.read(u('i')[0]); ext = 'jpg' if data[:2] == b'\xff\xd8' else 'png'
        fn=f'{pre}{k}.{ext}'; open(f'{out}/{fn}', 'wb').write(data); texs[k] = fn
    nb = u('i')[0]; inv = []; names = []
    for _ in range(nb):
        names.append(utf()); u('b'); m = np.array(u('12f')).reshape(3, 4); M = np.eye(4); M[:3] = m; inv.append(M)
    u('i'); clips = {}
    for _ in range(u('i')[0]):
        nm = utf(); fr, fps, bn = u('ifi')
        clips[nm] = np.frombuffer(b.read(fr * bn * 14), dtype=[('q', '>i2', 4), ('p', '>i2', 3)]).reshape(fr, bn)
    return G, texs, names, inv, clips
def at(a):
    M=np.eye(4); M[:3,:3]=Rot.from_quat(np.array(a[3:7])/np.linalg.norm(a[3:7])).as_matrix(); M[:3,3]=a[:3]; return M
def posed(G, skin, texs, keep=lambda g: True):
    objs=[]
    for g in G:
        mesh, piece, tex, rec, T, lv = g
        if lv>0 or not keep(g): continue
        P = np.c_[rec['p'], np.ones(len(rec))]; W = rec['w'] / 255.; out_p = np.zeros((len(rec), 3))
        for k in range(4):
            Mk = np.array([skin[j] for j in rec['j'][:, k]])
            out_p += W[:, k:k + 1] * np.einsum('vij,vj->vi', Mk, P)[:, :3]
        U = rec['u'] / 65535.
        objs.append({'tex': texs[tex], 'p': out_p[T.reshape(-1)].round(4).reshape(-1).tolist(), 'u': U[T.reshape(-1)].round(4).reshape(-1).tolist()})
    return objs
def build_scene(g, fitted, out, clip='idle_armed'):
    os.makedirs(out, exist_ok=True)
    spec=json.load(open(f'/tmp/claude-0/tk/b1/local/guns/tarkov_{g}.json'))
    model=spec['fp']; G,texs,names,inv,clips=read_zsk(NPC+model+'.zsk', model, out, 'g_')
    r=clips[clip][0]; pose=[]
    for i in range(len(names)):
        q=r[i]['q']/32767.; M=np.eye(4); M[:3,:3]=Rot.from_quat(q/np.linalg.norm(q)).as_matrix(); M[:3,3]=r[i]['p']/1000.; pose.append(M)
    skin=[pose[i]@inv[i] for i in range(len(names))]
    nodes,bys,complete=fit.build(g,fitted)
    kit={k['path']:k for k in spec['tarkov']['kit']}; byp={n['path']:n for n in nodes}
    bone={n:i for i,n in enumerate(names)}
    frames={}
    for n in nodes:
        k=kit.get(n['path']); f=None
        if k and k.get('bone') in bone: f=pose[bone[k['bone']]]@np.linalg.inv(at(k['at']))
        par=byp.get(n['parent']) if n['parent'] is not None else None
        onkit=par is None or par['kit']
        if f is None or not onkit:
            pf=frames.get(par['path']) if par else None
            port=fit.P.get(par['item'],{}).get('slots',{}).get(n['slot']) if par else None
            if pf is not None and port and port.get('at'): f=pf@at(port['at'])
        if f is not None: frames[n['path']]=f
    hide=set()
    for k in spec['tarkov']['kit']:
        n=byp.get(k['path'])
        if n and n['kit'] or not k.get('bones'): continue
        hide|={bone[b] for b in k['bones'] if b in bone}
    objs=posed(G,skin,texs,lambda gg: gg[1]=='gun' and int(gg[3]['j'][0][0]) not in hide)
    drawn=[]
    for n in nodes:
        if n['kit']: continue
        p=fit.P.get(n['item']); f=frames.get(n['path'])
        if not p or not p.get('model') or f is None: drawn.append((n['path'],'NOFRAME' if f is None else 'NOMODEL')); continue
        zp=PK+p['model']+'.zsk'
        if not os.path.exists(zp): drawn.append((n['path'],'NOZSK')); continue
        PG,pt,pn,pinv,_=read_zsk(zp,p['model'],out,p['model']+'_')
        objs+=posed(PG,[f@pinv[0]],pt); drawn.append((n['path'],p.get('short')))
    json.dump(objs,open(f'{out}/scene.json','w'))
    return complete,drawn,[(n['path'],fit.P.get(n['item'],{}).get('short'),n['kit']) for n in nodes]
if __name__=='__main__':
    g=sys.argv[1]; f=json.loads(sys.argv[2]) if len(sys.argv)>2 else fit.kit_sockets(g)
    print(build_scene(g,f,sys.argv[3] if len(sys.argv)>3 else f'/tmp/claude-0/tk/b2/pv/{g}'))
