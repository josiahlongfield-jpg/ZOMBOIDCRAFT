import sys, os
sys.path.insert(0, '/tmp/claude-0/armory'); import outfit as O
SIX = {'helmet': 'MARK V [B] UA/HUL', 'chest': 'UA/MULTI-THREAT', 'left_shoulder': 'UA/MULTI-THREAT', 'right_shoulder': 'FJ/PARA',
       'wrist': 'TACTICAL/TACPAD', 'utility': 'TACTICAL/HARD CASE', 'knees': 'FJ/PARA'}
JOHN = {'helmet': 'MJOLNIR Mk. V', 'left_shoulder': 'MJOLNIR Mk. V', 'right_shoulder': 'MJOLNIR Mk. V'}
jobs = [('six_ready', SIX, 'A_HR_Rifle_Ready', 900, 1200), ('six_shoulder', SIX, 'A_HR_Rifle_On_Shoulder', 900, 1200),
        ('six_crossed', SIX, 'A_HR_CrossedArms', 900, 1200), ('john_atease', JOHN, 'A_HR_Rifle_Buff', 900, 1200)]
import anim
names = sorted(f[:-7] for f in os.listdir(anim.BASE + 'Animations/') if f.endswith('.uasset'))
jobs += [('pose_' + n[5:], SIX, n, 300, 400) for n in names]
os.makedirs('r', exist_ok=True)
for n, sel, pose, w, h in jobs:
    if os.path.exists('r/%s.png' % n): continue
    O.render(sel, pose, 'r/%s.png' % n, W=w, H=h); print(n, flush=True)
