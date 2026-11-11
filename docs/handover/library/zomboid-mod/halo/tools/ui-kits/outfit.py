import sys, json
sys.path[:0] = ['/tmp/claude-0/halo', '/tmp/claude-0/arm', '/tmp/claude-0/mcc/anim']
import orender
N = json.load(open('/mnt/project-files/zomboid-mod/halo/armory/armory_names.json'))
PM = json.load(open('/tmp/claude-0/arm/pmcg.json'))
SLOTS = ['helmet', 'left_shoulder', 'right_shoulder', 'chest', 'wrist', 'utility', 'knees']
def pairs(slot, perm):
    for e in PM[slot]:
        if e['name'] == perm: return e['pairs']
    raise KeyError((slot, perm))
def outfit(sel):
    """sel: slot -> display name (or index). Returns region -> permutation for orender."""
    o = dict(orender.BASE)
    for slot in SLOTS:
        v = sel.get(slot, 0)
        item = N[slot][v] if isinstance(v, int) else next(x for x in N[slot] if x['name'] == v)
        for rg, pn in pairs(slot, item['perm']): o[rg] = pn
    return o
def render(sel, pose, out, W=600, H=800, col=True, rad=2.6, cy=None):
    import mccpose, posed
    if col: mccpose.G = '/tmp/claude-0/arm/mp_col.glb'
    w = '/tmp/claude-0/halo/exp/noble/w_ar.glb' if 'Rifle' in pose else None
    V, T, UV, MM, tex = mccpose.scene(outfit(sel), pose, 0, w)
    lo, hi = V.min(0), V.max(0); c = (lo + hi) / 2; c[1] = cy if cy else max(c[1], 0.95)
    posed.shot(V, T, UV, MM, tex, out, W=W, H=H, c=tuple(c), rad=rad)
if __name__ == '__main__':
    render({'helmet': 'MARK V [B] UA/HUL', 'chest': 'UA/MULTI-THREAT', 'left_shoulder': 'UA/MULTI-THREAT', 'right_shoulder': 'FJ/PARA',
            'wrist': 'TACTICAL/TACPAD', 'utility': 'TACTICAL/HARD CASE', 'knees': 'FJ/PARA'}, 'A_HR_CrossedArms', '/tmp/claude-0/armory/t.png')
