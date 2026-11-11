import sys,struct
sys.path.insert(0,'/tmp/claude-0/h4')
from h4 import H4
r=H4('m90.map'); N=r.names(); S=r.strings()
for k,(g,dat,a) in enumerate(r.tags):
    if 'cortana' in N[k] and g in ('mat ','mats','rmsh','rm  ') or (g and 'cortana' in N[k] and g.startswith('ma')):
        print(k,g,N[k])
