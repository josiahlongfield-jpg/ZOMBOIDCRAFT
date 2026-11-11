import os, re, shutil, sys
src, dst = sys.argv[1], sys.argv[2]
ROW = re.compile(r"^\| (\S+) \| \[.+?\]\((https:\S+?)\) \|")
added = {}
for root, dirs, files in os.walk(src):
    if "sources.md" not in files: continue
    rel = os.path.relpath(root, src); out = os.path.join(dst, rel)
    srows = [l for l in open(os.path.join(root, "sources.md"), encoding="utf-8").read().splitlines() if ROW.match(l)]
    if not os.path.exists(os.path.join(out, "sources.md")):
        shutil.copytree(root, out, dirs_exist_ok=True); added[rel] = len(srows); continue
    old = open(os.path.join(out, "sources.md"), encoding="utf-8").read()
    have = {m.group(2) for l in old.splitlines() if (m := ROW.match(l))}
    n = max([int(f[:2]) for f in os.listdir(out) if f[:2].isdigit()] or [0])
    new = []
    for l in srows:
        name, url = ROW.match(l).groups()
        if url in have: continue
        n += 1; nn = f"{n:02d}" + name[2:]
        shutil.copy(os.path.join(root, name), os.path.join(out, nn))
        new.append(l.replace(f"| {name} |", f"| {nn} |", 1))
    if new:
        open(os.path.join(out, "sources.md"), "w", encoding="utf-8").write(old.rstrip("\n") + "\n" + "\n".join(new) + "\n")
    added[rel] = len(new)
for k in sorted(added): print(added[k], k)
print("TOTAL", sum(added.values()))
