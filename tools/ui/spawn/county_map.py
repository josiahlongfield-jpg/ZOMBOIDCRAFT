import json, sys
d = json.load(open(sys.argv[1])); out = sys.argv[2]
B = 8192; W = 760
def sx(x): return (x + B) / (2 * B) * W
def pts(poly): return " ".join(f"{sx(x):.1f},{sx(z):.1f}" for x, z in poly)
P = {p["id"]: (p["x"], p["z"]) for p in d["P"]}
def line(seq):
    out = []
    for q in seq:
        if isinstance(q, str):
            if q in P: out.append(P[q])
        else: out.append(tuple(q))
    return out
o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {W}" width="{W}" height="{W}">']
o.append(f'<rect width="{W}" height="{W}" fill="#1c1e1f"/>')
# terrain
o.append(f'<polygon points="{pts(d["MOUNTAINS"])}" fill="#1d1f1e"/>')
for f in d["FOREST"]:
    o.append(f'<polygon points="{pts(f)}" fill="#18231b" opacity="0.95"/>')
for f in (d.get("FARM") or []):
    if isinstance(f, list) and f and isinstance(f[0], list):
        o.append(f'<polygon points="{pts(f)}" fill="#1f1e18"/>')
o.append(f'<polygon points="{pts(d["SPRAWL"])}" fill="#1c1d1d"/>')
# water
o.append(f'<polygon points="{pts(d["SEA_S"])}" fill="#0a1117"/>')
for r in d["RIVERS"]:
    w = max(2.6, r["w"] / (2 * B) * W)
    o.append(f'<polyline points="{pts(line(r["pts"]))}" fill="none" stroke="#0a1117" stroke-width="{w:.1f}" stroke-linejoin="round" stroke-linecap="round"/>')
for l in d["LAKES"]:
    o.append(f'<ellipse cx="{sx(l["x"]):.1f}" cy="{sx(l["z"]):.1f}" rx="{l["rx"]/(2*B)*W:.1f}" ry="{l["rz"]/(2*B)*W:.1f}" fill="#0a1117"/>')
for i in d["ISLANDS"]:
    o.append(f'<ellipse cx="{sx(i["x"]):.1f}" cy="{sx(i["z"]):.1f}" rx="{i["rx"]/(2*B)*W:.1f}" ry="{i["rz"]/(2*B)*W:.1f}" fill="#202122"/>')
o.append(f'<polygon points="{pts(d["ISLE"])}" fill="#2a2b2e"/>')
# coast edge
o.append(f'<polygon points="{pts(d["SEA_S"])}" fill="none" stroke="#3a4a52" stroke-width="0.8" opacity="0.7"/>')
# roads
for r in d["ROADS"]:
    big = r.get("cls") == "i"
    o.append(f'<polyline points="{pts(line(r["pts"]))}" fill="none" stroke="{"#6b6455" if big else "#3f3d38"}" stroke-width="{1.6 if big else 0.9}" stroke-linejoin="round" stroke-linecap="round"/>')
# rail
for r in d["RAIL"]:
    rp = line(r["pts"]) + ([line(r["pts"])[0]] if r.get("closed") else [])
    o.append(f'<polyline points="{pts(rp)}" fill="none" stroke="#4a4740" stroke-width="0.8" stroke-dasharray="3 2"/>')
# fence
fl = d.get("FENCE_LINE")
o.append(f'<rect x="{sx(-7950):.1f}" y="{sx(-7950):.1f}" width="{sx(7950)-sx(-7950):.1f}" height="{sx(7950)-sx(-7950):.1f}" fill="none" stroke="#6e2626" stroke-width="1" stroke-dasharray="6 4" opacity="0.8"/>')
o.append("</svg>")
open(out, "w").write("\n".join(o))
print("ok", len("".join(o)))
