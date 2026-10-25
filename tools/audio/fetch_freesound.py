"""Collects candidate weapon sounds from Freesound: CC0 recordings only, as their HQ previews.

    python3 tools/audio/fetch_freesound.py OUT_DIR

Runs in CI (the build machine can reach Freesound). For each query it takes the most downloaded
CC0 results, checks the licence on the sound's own page, and saves OUT_DIR/<category>/<id>.ogg
plus OUT_DIR/manifest.json (title, author, page, licence, tags, downloads) for picking and credits.
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

QUERIES = {
    "rifle_shot": ["assault rifle shot", "ar15 gunshot", "m4 rifle shot", "rifle gunshot", "ak47 shot", "556 rifle"],
    "rifle_distant": ["distant gunshot", "gunshot echo", "rifle shot far"],
    "rifle_mag": ["rifle magazine insert", "ar15 magazine", "magazine insert gun", "magazine release", "gun mag out"],
    "rifle_bolt": ["charging handle", "rifle bolt", "gun cock", "rifle slide rack", "bolt release"],
    "dry_fire": ["dry fire", "gun empty click", "trigger click"],
    "casing": ["bullet casing", "shell casing drop", "brass casing"],
    "bullet_hit": ["bullet impact flesh", "bullet hit body", "bullet impact", "ricochet"],
    "swing": ["swing whoosh", "bat swing", "swoosh stick", "whoosh weapon"],
    "hit_blunt": ["baseball bat hit", "punch body", "body hit", "melon smash", "bat hit flesh", "wood hit body"],
    "hit_blade": ["knife stab", "stab flesh", "sword slash flesh", "machete chop", "flesh cut"],
    "hit_metal": ["metal pipe hit", "frying pan hit", "crowbar hit", "metal bang hit"],
    "stomp": ["skull crush", "gore splat", "stomp squish", "head smash"],
    "shove": ["body push", "shove grunt", "body fall"],
    "break": ["wood break", "stick snap", "bat break", "metal snap"],
    "equip": ["gun foley", "weapon equip", "gun handling", "rifle sling"],
}
PER_QUERY = 6
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) ZomboidCraft-sound-collector"}


def get(url, binary=False):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40) as r:
                data = r.read()
                return data if binary else data.decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            print("  retry", url, e)
            time.sleep(3 + attempt * 4)
    return None


def search(q):
    params = [
        {"q": q, "f": 'license:"Creative Commons 0"', "s": "Downloads (most first)"},
        {"q": q, "f": 'license:"Creative Commons 0"', "s": "num_downloads desc"},
        {"q": q, "f": 'license:"Creative Commons 0"'},
    ]
    for p in params:
        html = get("https://freesound.org/search/?" + urllib.parse.urlencode(p))
        if not html:
            continue
        ids = []
        for m in re.finditer(r'href="/people/([^/"]+)/sounds/(\d+)/"', html):
            if (m.group(1), m.group(2)) not in ids:
                ids.append((m.group(1), m.group(2)))
        if ids:
            return ids, html
    return [], html or ""


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    manifest = {}
    dumped = False
    for cat, queries in QUERIES.items():
        (out / cat).mkdir(exist_ok=True)
        for q in queries:
            ids, html = search(q)
            print(f"{cat} / {q}: {len(ids)} results")
            if not ids and not dumped:
                (out / "search_debug.html").write_text(html[:200000])
                dumped = True
            taken = 0
            for user, sid in ids:
                if taken >= PER_QUERY:
                    break
                if sid in manifest:
                    continue
                page_url = f"https://freesound.org/people/{user}/sounds/{sid}/"
                page = get(page_url)
                if not page:
                    continue
                if not re.search(r"publicdomain/zero|Creative Commons 0", page):
                    print("  skip (not CC0)", sid)
                    continue
                m = re.search(r'https://cdn\.freesound\.org/previews/\d+/\d+_\d+-hq\.ogg', page) \
                    or re.search(r'https://cdn\.freesound\.org/previews/\d+/\d+_\d+-hq\.mp3', page)
                if not m:
                    print("  no preview", sid)
                    continue
                data = get(m.group(0), binary=True)
                if not data:
                    continue
                ext = m.group(0).rsplit(".", 1)[1]
                (out / cat / f"{sid}.{ext}").write_bytes(data)
                title = re.search(r'<meta property="og:title" content="([^"]*)"', page)
                desc = re.search(r'<meta property="og:description" content="([^"]*)"', page)
                tags = re.findall(r'href="/browse/tags/([^/"]+)/"', page)
                dl = re.search(r'([\d,]+)\s*downloads', page)
                manifest[sid] = {"category": cat, "query": q, "file": f"{cat}/{sid}.{ext}", "user": user,
                                 "title": title.group(1) if title else "", "description": (desc.group(1) if desc else "")[:300],
                                 "tags": sorted(set(tags))[:20], "downloads": dl.group(1) if dl else "",
                                 "page": page_url, "license": "CC0 1.0"}
                taken += 1
                time.sleep(0.5)
            time.sleep(1)
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print(len(manifest), "sounds")


if __name__ == "__main__":
    main()
