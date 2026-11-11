"""All attachments the batch-1 guns can take (SPT slot filters, followed through every part), by kind, with whether
their bundle was pulled. Writes catalog.json: {id: {name, kind, chain, prefab, have, guns:[...]}}"""
import sys,json,os,collections
TK2='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/tk2'
os.chdir(TK2); sys.path.insert(0,TK2)
from db import *
RAW='/tmp/claude-0/-home-user-zomboidcraft/10e3aa49-17b2-5030-8c7e-5f297751f80d/scratchpad/raw1/'
P='EscapeFromTarkov_Data__StreamingAssets__Windows__'
K=json.load(open('kits.json'))
cat={}
for g,v in K.items():
    for c in reach(v['id']):
        e=cat.setdefault(c,{'name':name(c),'short':short(c),'kind':kind(c),'chain':parent_chain(c)[:4],
            'prefab':I[c]['_props'].get('Prefab',{}).get('path',''),'guns':[]})
        e['guns'].append(g)
for c,e in cat.items():
    e['have']=os.path.exists(RAW+P+e['prefab'].replace('/','__')) if e['prefab'] else False
json.dump(cat,open('/tmp/claude-0/tk/b2/catalog.json','w'),indent=0)
n=collections.Counter(); h=collections.Counter()
for e in cat.values(): n[e['kind']]+=1; h[e['kind']]+=e['have']
for k,v in n.most_common(): print('%-24s %4d have %4d'%(k,v,h[k]))
print(len(cat), sum(e['have'] for e in cat.values()))
