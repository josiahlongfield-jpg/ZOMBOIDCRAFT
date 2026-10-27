"""Builds the bullet strikes (weapon.bullet.impact.*), from Josiah's picks on the Dead Air Bullet Strikes page (2026-10-27).

    python3 tools/audio/build_impact_sounds.py FREESOUND_DIR SONNISS_DIR

FREESOUND_DIR is a checkout of the sounds-raw branch; SONNISS_DIR holds the Sonniss downloads, which stay out of this
repo. The weapons thread picks the surface a shot hits and plays one of these per shot (pitch 0.92-1.08); recordings
that hold several hits are cut at each hit so every one becomes its own take. Mono, short, peak -1 dB.
Two picks came with their own jobs:
  weapon.bullet.impact.nearby   "VisceralBulletImpacts", cut into its single hits: what people who didn't fire hear
                                when a round lands close to them
  weapon.casing.shotgun         spent shotgun shells bouncing on the ground, for every shotgun
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_zombie_sounds import RES, ROOT

RATE = 44100
OUT = RES / "zomboidcraft/sounds/weapon/bullet/impact"
# event: (folder under sounds/, range, [(source, cut at each hit?)]); source is "fs:<id>" or "sx:<id>"
EVENTS = {
    "weapon.bullet.impact.ground": ("weapon/bullet/impact/ground", 40, [("sx:acb9203c", False)]),
    "weapon.bullet.impact.concrete": ("weapon/bullet/impact/concrete", 40, [("fs:150837", False)]),
    "weapon.bullet.impact.brick": ("weapon/bullet/impact/brick", 40, [("fs:150838", False)]),
    # not picked yet: the only real bullet-into-wood take on the page
    "weapon.bullet.impact.wood": ("weapon/bullet/impact/wood", 40, [("fs:237447", True)]),
    "weapon.bullet.impact.metal": ("weapon/bullet/impact/metal", 48, [("fs:267893", False), ("fs:399550", True)]),
    "weapon.bullet.impact.car": ("weapon/bullet/impact/car", 48, [("sx:72f18110", False), ("sx:af8c9351", False)]),
    "weapon.bullet.impact.glass": ("weapon/bullet/impact/glass", 48, [("sx:fbae0c69", False)]),
    "weapon.bullet.impact.soft": ("weapon/bullet/impact/soft", 24, [("fs:496509", False)]),
    "weapon.bullet.ricochet": ("weapon/bullet/ricochet", 32, [("fs:30932", True)]),
    "weapon.bullet.impact.nearby": ("weapon/bullet/impact/nearby", 24, [("fs:423301", True)]),
    "weapon.casing.shotgun": ("weapon/casing/shotgun", 16, [("fs:620929", False)]),
}


def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def hits(a):
    """Start of each hit: 5 ms peaks crossing 35% of the loudest, at least 0.4 s apart."""
    w = int(0.005 * RATE)
    env = np.array([np.abs(a[i:i + w]).max() for i in range(0, len(a) - w, w)])
    out, last = [], -1e9
    for i, v in enumerate(env):
        if v > 0.35 * env.max() and i * w - last > 0.4 * RATE:
            out.append(max(0, i * w - int(0.01 * RATE)))
            last = i * w
    return out


def trim(a):
    """Drop the silence after the hit and fade the last 30 ms so nothing clicks."""
    loud = np.nonzero(np.abs(a) > np.abs(a).max() * 10 ** (-50 / 20))[0]
    a = a[:loud[-1] + 1] if len(loud) else a
    n = min(len(a), int(0.03 * RATE))
    a[-n:] *= np.linspace(1, 0, n, dtype=np.float32)
    n = min(len(a), int(0.002 * RATE))
    a[:n] *= np.linspace(0, 1, n, dtype=np.float32)
    return a


def main():
    fs_dir, sx_dir = Path(sys.argv[1]), Path(sys.argv[2])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sx = json.loads((sx_dir / "manifest.json").read_text())
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    credits = {}
    for event, (folder, rng, sources) in EVENTS.items():
        out = RES / "zomboidcraft/sounds" / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        takes = []
        for src, cut in sources:
            kind, sid = src.split(":")
            if kind == "fs":
                info, path = fs[sid], fs_dir / fs[sid]["file"]
                rec = f"[{info['title']}]({info['page']})"
                credits.setdefault(rec, (info["user"], "Freesound, " + info.get("license", "CC0 1.0"), []))[2].append(event)
            else:
                info, path = sx["sx" + sid], sx_dir / sx["sx" + sid]["file"]
                rec = f"{info['title']} ({info['pack']})"
                credits.setdefault(rec, (info["bundle"], "Sonniss royalty-free", []))[2].append(event)
            a = load(path)
            starts = hits(a) if cut else [0]
            for i, s in enumerate(starts):
                end = starts[i + 1] if i + 1 < len(starts) else len(a)
                takes.append(trim(a[s:end].copy()))
        names = []
        for i, a in enumerate(takes, 1):
            a *= 10 ** (-1 / 20) / max(np.abs(a).max(), 1e-6)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                            "-c:a", "libvorbis", "-q:a", "6", str(out / f"{i}.ogg")], input=a.tobytes(), check=True)
            names.append({"name": f"zomboidcraft:{folder}/{i}", "attenuation_distance": rng})
        ours[event] = {"sounds": names}
        print(event, len(takes), "takes", [round(len(t) / RATE, 2) for t in takes])
    # Josiah 08:14: the shooter hears the same fleshy hits on zombies they shoot, further off (Guns.hitEntity)
    ours["weapon.bullet.whizz"] = {"sounds": [dict(s, attenuation_distance=48) for s in ours["weapon.bullet.impact.nearby"]["sounds"]]}
    sj.write_text(json.dumps(ours, indent=2) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Bullet strikes\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {', '.join(evs)} | {rec} | {by} | {lic} |" for rec, (by, lic, evs) in credits.items())
    section = ("\n## Bullet strikes\n\nPicked by Josiah Longfield on the Dead Air Bullet Strikes page, cut into single hits by "
               "tools/audio/build_impact_sounds.py. CC-BY recordings are used under Creative Commons Attribution with the "
               "credit given here. The Sonniss originals are not included.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
