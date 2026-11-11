import os,json,time
T='/mnt/project-files/zomboid-mod/halo/transit/world'
L=[]
for m in sorted(os.listdir(T)):
    for f in sorted(os.listdir(f'{T}/{m}')):
        p=f'{T}/{m}/{f}'
        if time.time()-os.path.getmtime(p)<40: continue
        L.append({"stagedPath":p,"devicePath":f"C:\\Dev\\Zomboidcraft-Desktop\\additions\\halo-reach\\world\\{m}\\{f}"})
json.dump(L[:50],open('/tmp/claude-0/halo/cur.json','w')); print(json.dumps(L[:50]))
