# Usage: python pc_refs.py spec.json OUTDIR
import json, sys, time, re, os, urllib.request, urllib.parse, urllib.error, html
UA = "ZomboidCraftRefBot/1.0 (https://github.com/josiahlongfield-jpg/zomboidcraft; personal reference collection) python-urllib"
OK = ("public domain","pd","cc0","no restrictions","cc by","cc-by","cc by-sa","cc-by-sa","attribution")
def get(url):
    for i in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r: return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 429: w = int(e.headers.get("retry-after", "20") or 20) + 2; print("  429, waiting", w); time.sleep(w); continue
            if e.code >= 500: time.sleep(5); continue
            return None
        except Exception as e: print("  net error", e); time.sleep(5)
    return None
def strip(s): return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip().replace("\n", " ").replace("|", "/")
def slug(t): return re.sub(r"[^a-z0-9]+", "-", t.lower())[:60].strip("-")
spec = json.load(open(sys.argv[1], encoding="utf-8")); root = sys.argv[2]
for folder, cfg in spec.items():
    out = os.path.join(root, *folder.split("/")); os.makedirs(out, exist_ok=True)
    rows = []; have = set(); target = cfg.get("n", 8)
    print("==", folder, flush=True)
    for q in cfg["q"]:
        if len(rows) >= target: break
        p = {"action":"query","format":"json","generator":"search","gsrnamespace":"6","gsrsearch":q+" filetype:bitmap","gsrlimit":"15",
             "prop":"imageinfo","iiprop":"url|extmetadata|size|mime","iiurlwidth":"1024"}
        b = get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(p)); time.sleep(1)
        if not b: continue
        pages = sorted(json.loads(b).get("query", {}).get("pages", {}).values(), key=lambda x: x.get("index", 0))
        per = 0
        for pg in pages:
            if len(rows) >= target or per >= 3: break
            ii = pg.get("imageinfo", [{}])[0]; m = ii.get("extmetadata", {})
            if ii.get("mime") not in ("image/jpeg", "image/png", "image/tiff") or ii.get("width", 0) < 800: continue
            lic = strip(m.get("LicenseShortName", {}).get("value", ""))
            if not any(lic.lower().startswith(k) for k in OK) or "nc" in lic.lower().split("-") or "nd" in lic.lower().split("-"): continue
            page = ii.get("descriptionurl")
            if page in have: continue
            data = get(ii.get("thumburl") or ii.get("url")); time.sleep(1)
            if not data or not (data[:3] == b"\xff\xd8\xff" or data[:8] == b"\x89PNG\r\n\x1a\n"): continue
            have.add(page); per += 1
            name = f"{len(rows)+1:02d}-{slug(pg['title'][5:].rsplit('.',1)[0])}" + (".jpg" if data[:2] == b"\xff\xd8" else ".png")
            open(os.path.join(out, name), "wb").write(data)
            rows.append(f"| {name} | [{strip(pg['title'][5:])[:70]}]({page}) | {strip(m.get('Artist',{}).get('value',''))[:80]} | {lic} | {strip(m.get('ImageDescription',{}).get('value',''))[:140]} |")
            print("  +", name, flush=True)
    with open(os.path.join(out, "sources.md"), "w", encoding="utf-8") as f:
        f.write(f"# {folder}\n\nReference photos from Wikimedia Commons (1024 px thumbnails), free licences only.\n\n| File | Source | Author | Licence | Description |\n|---|---|---|---|---|\n" + "\n".join(rows) + "\n")
print("DONE")
