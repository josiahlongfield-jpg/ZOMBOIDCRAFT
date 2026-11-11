import sys,glob,os,numpy as np,matplotlib;matplotlib.use('Agg');import matplotlib.pyplot as plt
sys.path.insert(0,'/mnt/project-files/zomboid-mod/brita/tools/irons');import sightkit as K
from matplotlib.collections import PolyCollection
D='local/guns3/';ids=sorted(os.path.basename(f)[:-6] for f in glob.glob(D+'*.zmesh'))
for page in range(0,len(ids),30):
    fig,ax=plt.subplots(5,6,figsize=(24,14));ax=ax.ravel()
    for a,g in zip(ax,ids[page:page+30]):
        m=K.read(D+g+'.zmesh','local/'+g);T=np.concatenate([K.positions(r) for n,r in m['parts']])
        a.add_collection(PolyCollection(T[:,:,[2,1]],facecolor=(0.2,0.3,0.6,0.25),edgecolor='none'));a.set_xlim(T[:,:,2].max()+.5,T[:,:,2].min()-.5);a.set_ylim(T[:,:,1].min()-.5,T[:,:,1].max()+.5);a.set_aspect('equal');a.set_title(g,fontsize=9)
    for a in ax[len(ids[page:page+30]):]: a.axis('off')
    plt.tight_layout();plt.savefig(f'w3/sheet3_{page//30}.png',dpi=45);plt.close()
print(len(ids))
