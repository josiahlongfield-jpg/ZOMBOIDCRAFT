import numpy as np, glob, os
ROOT = '/mnt/user-data/uploads/Desktop/additions/sightpack/'
def read(path):
    V, VT, tris, uvs, mats, grps = [], [], [], [], [], []
    mat = grp = ''
    for l in open(path, errors='ignore'):
        if l.startswith('v '): V.append([float(x) for x in l.split()[1:4]])
        elif l.startswith('vt '): VT.append([float(x) for x in l.split()[1:3]])
        elif l.startswith('usemtl'): mat = l.split(None, 1)[1].strip()
        elif l.startswith(('g ', 'o ')): grp = l.split(None, 1)[1].strip()
        elif l.startswith('f '):
            ps = [p.split('/') for p in l.split()[1:]]
            vi = [int(p[0]) - 1 if int(p[0]) > 0 else len(V) + int(p[0]) for p in ps]
            ti = [int(p[1]) - 1 if len(p) > 1 and p[1] else -1 for p in ps]
            for k in range(1, len(vi) - 1):
                tris.append([vi[0], vi[k], vi[k + 1]]); uvs.append([ti[0], ti[k], ti[k + 1]]); mats.append(mat); grps.append(grp)
    V = np.array(V); VT = np.array(VT) if VT else np.zeros((1, 2))
    return V[np.array(tris)], VT[np.array(uvs)], np.array(mats), np.array(grps)
def objs():
    return sorted(glob.glob(ROOT + '*/OBJ/*.obj'))
