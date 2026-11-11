import re,json
def parse(path,tag):
    t=open(path,encoding='latin-1').read().replace('\r','')
    t=re.sub(r'/\*.*?\*/','',t,flags=re.S)
    out={}
    for m in re.finditer(r'\bmodel\s+(\w+)\s*\{',t):
        i=m.end(); d=1; j=i
        while d and j<len(t):
            if t[j]=='{': d+=1
            elif t[j]=='}': d-=1
            j+=1
        b=t[i:j-1]
        g=lambda k:(re.search(r'\b'+k+r'\s*=\s*([^,\n]+)',b) or [None,None])[1]
        att={}
        for a in re.finditer(r'attachment\s+(\w+)\s*\{(.*?)\}',b,re.S):
            o=re.search(r'offset\s*=\s*([-\d.\s]+)',a.group(2)); r=re.search(r'rotate\s*=\s*([-\d.\s]+)',a.group(2))
            att[a.group(1)]={'offset':[float(x) for x in o.group(1).split()] if o else [0,0,0],'rotate':[float(x) for x in r.group(1).split()] if r else [0,0,0]}
        sc=g('scale')
        out[m.group(1)]={'file':tag,'mesh':(g('mesh') or '').strip(),'tex':(g('texture') or '').strip() or None,'scale':float(sc) if sc else None,'att':att}
    return out
if __name__=='__main__':
    m=json.load(open('pz/models_all.json'))
    b=parse("/mnt/user-data/uploads/108600/2200148440/mods/Brita's Weapon Pack/media/scripts/Brita_models.txt",'Brita_models.txt')
    n=0
    for k,v in b.items():
        if k not in m: m[k]=v; n+=1
    json.dump(m,open('pz/models_all.json','w')); print('added',n,'total',len(m))
