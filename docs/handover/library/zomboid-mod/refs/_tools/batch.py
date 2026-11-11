import sys; sys.exit("DISABLED (Josiah 2026-10-31): Wikimedia is only used from his PC via pc_refs.py over Remote Control. Never run this from the cloud.")
# Usage: python3 -I batch.py spec.json outroot [budget_mb]
import json, sys, time, re, os, urllib.request, urllib.parse, html
UA = "ZomboidCraftRefBot/1.0 (https://github.com/josiahlongfield-jpg/zomboidcraft; personal reference collection) python-urllib"
OK = ("public domain","pd","cc0","no restrictions","cc by","cc-by","cc by-sa","cc-by-sa","attribution")
FAILS=[0]
def get(url, tries=4):
    if FAILS[0]>=3: raise SystemExit("THROTTLED: Wikimedia keeps answering 429, stopping")
    for i in range(tries):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                FAILS[0]=0; return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 429:
                w = int(e.headers.get("retry-after","20") or 20)+3; print(f"  429 wait {w}", flush=True); time.sleep(w); continue
            if e.code >= 500: time.sleep(5); continue
            raise
        except Exception as e:
            print("  net err", e, flush=True); time.sleep(8)
    FAILS[0]+=1
    return None
def search(q, n):
    p={"action":"query","format":"json","generator":"search","gsrnamespace":"6","gsrsearch":q,"gsrlimit":str(n),
       "prop":"imageinfo","iiprop":"url|extmetadata|size|mime","iiurlwidth":"1024"}
    b=get("https://commons.wikimedia.org/w/api.php?"+urllib.parse.urlencode(p))
    if not b: return []
    pages=json.loads(b).get("query",{}).get("pages",{}).values()
    return sorted(pages,key=lambda x:x.get("index",0))
def strip(s): return html.unescape(re.sub(r"<[^>]+>","",s or "")).strip().replace("\n"," ")
def slug(t): return re.sub(r"[^a-z0-9]+","-",t.lower())[:60].strip("-")
def dirsize(root):
    return sum(os.path.getsize(os.path.join(d,f)) for d,_,fs in os.walk(root) for f in fs)
def write(srcp,folder,rows):
    with open(srcp,"w") as f:
        f.write(f"# {folder}\n\nReference photos from Wikimedia Commons (1024 px thumbnails). Free licences only; CC BY / CC BY-SA need credit if ever redistributed. Photos are reference only, not shipped in the mod.\n\n| File | Source | Author | Licence | Description |\n|---|---|---|---|---|\n")
        f.write("\n".join(rows)+"\n")
spec=json.load(open(sys.argv[1])); root=sys.argv[2]; budget=float(sys.argv[3] if len(sys.argv)>3 else 600)*1e6
for folder, cfg in spec.items():
    out=os.path.join(root,folder); os.makedirs(out,exist_ok=True)
    srcp=os.path.join(out,"sources.md")
    have=set(); rows=[]
    if os.path.exists(srcp):
        for l in open(srcp):
            m=re.match(r"\| (\S+) \| \[(.+?)\]\((https:\S+?)\) \|",l)
            if m: have.add(m.group(3)); rows.append(l.rstrip("\n"))
    target=cfg.get("n",12); count=len(rows)
    print(f"== {folder} (have {count})", flush=True)
    for q in cfg["q"]:
        if count>=target: break
        res=search(q, 15); time.sleep(5)
        per_q=0
        for pg in res:
            if count>=target or per_q>=cfg.get("per_q",4): break
            ii=pg.get("imageinfo",[{}])[0]; m=ii.get("extmetadata",{})
            mime=ii.get("mime",""); 
            if mime not in ("image/jpeg","image/png","image/tiff"): continue
            if ii.get("width",0)<800: continue
            lic=strip(m.get("LicenseShortName",{}).get("value","")); 
            if not any(lic.lower().startswith(k) for k in OK): continue
            if "nc" in lic.lower().split("-") or "nd" in lic.lower().split("-"): continue
            page=ii.get("descriptionurl"); 
            if page in have: continue
            url=ii.get("thumburl") or ii.get("url")
            data=get(url); time.sleep(5)
            if not data or not (data[:3]==b"\xff\xd8\xff" or data[:8]==b"\x89PNG\r\n\x1a\n"): continue
            ext=".jpg" if data[:2]==b"\xff\xd8" else ".png"
            count+=1; per_q+=1; have.add(page)
            name=f"{count:02d}-{slug(pg['title'][5:].rsplit('.',1)[0])}{ext}"
            open(os.path.join(out,name),"wb").write(data)
            artist=strip(m.get("Artist",{}).get("value",""))[:80].replace("|","/")
            desc=strip(m.get("ImageDescription",{}).get("value",""))[:140].replace("|","/")
            rows.append(f"| {name} | [{pg['title'][5:][:70].replace('|','/')}]({page}) | {artist} | {lic} | {desc} |")
            print("  +",name,lic,flush=True)
            write(srcp,folder,rows)
        if dirsize(root)>budget: print("BUDGET HIT",flush=True); break
    write(srcp,folder,rows)
    if dirsize(root)>budget: print("BUDGET HIT, stopping",flush=True); break
print("DONE", dirsize(root)/1e6, "MB", flush=True)
