import sys,os,json
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from chdt import C
c=C(sys.argv[1])
def col(v): return 'L'+'abcd'[v] if v<4 else ('G%d'%(v-18) if v>=18 else 'U%d'%v)
for name in sys.argv[2:]:
    res=c.parse(name); print('=====',name,len(res))
    json.dump(res,open('out/'+name.replace('\\','_')+'.json','w'),indent=0)
    for w in res:
        p=w['place'][0] if w['place'] else None
        print('W %-10s sp%d in%d/%d anc%s off%s org%s sc%s tpl=%s'%(w['name'],w['special'],w['inp'],w['rng'],p and p['anchor'],p and p['offset'],p and p['mirror'],p and tuple(round(x,2) for x in p['scale']),w.get('place_tpl','')))
        for kind in ('bitmaps','texts'):
            for b in w[kind]:
                p=b['place'][0] if b['place'] else None; rd=b['render'][0] if b['render'] else None
                print('   %s %-14s in%d/%d anc%s off%s org%s sc%s sh%s col%s %s spr%s %s'%(kind[0],b['name'],b['inp'],b['rng'],p and p['anchor'],p and p['offset'],p and p['mirror'],p and tuple(round(x,2) for x in p['scale']),rd and rd['shader'],rd and [col(x) for x in rd['outc'][:3]],(b.get('bitmap') or '').replace('ui\\chud\\',''),b.get('sprite'),b.get('string','')+' f%s'%b.get('font','')), [a['anim'].split('\\')[-1] for a in b['anim']][:3])
