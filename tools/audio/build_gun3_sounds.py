"""Builds the wave-two guns' shots from Josiah's picks on the Dead Air New Guns Two page (2026-10-30).

    python3 tools/audio/build_gun3_sounds.py FREESOUND_DIR SONNISS_DIR GAMEMASTER_DIR

Same inputs as build_throw_sounds.py (whose cutting it shares). Far-off and suppressed far-off shots he didn't pick
are made from the near pick, muffled the way build_gamemaster_sounds.py makes them. The precision rifle keeps its
sounds (Josiah 2026-10-29), and weapon.suppressed_rifle.* keeps WEAPONS' stand-in until he picks one.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_gamemaster_sounds import DISTANT_AF
from build_throw_sounds import RATE, hits, load, pieces, trim
from build_zombie_sounds import RES, ROOT

# a piece: (source, cut, filter). source and cut as in build_throw_sounds.py; filter is an ffmpeg -af chain or None
# Josiah wanted the .50 "much heavier": his pick played lower with the bottom end lifted
HEAVY = "asetrate=44100*0.8,aresample=44100,bass=g=7:f=90"
EVENTS = {
    "weapon.heavy_sniper.shot": ("weapon/heavy_sniper/shot", 192, [("fs:386842", None, HEAVY)]),
    # the Lynx .50 heard from 65 m behind: three shots, each with its echo
    "weapon.heavy_sniper.shot_distant": ("weapon/heavy_sniper/shot_distant", 256, [("sx:c5f1fcc6", ("starts", 6.5), None)]),
    "weapon.heavy_sniper.shot_suppressed": ("weapon/heavy_sniper/shot_suppressed", 64, [("sx:b8e50005", ("span", 0.0, 2.0), None)]),
    # "this needs to be the 308 battle rifle audio": the cannon's single shot with its report
    "weapon.battle_rifle.shot": ("weapon/battle_rifle/shot", 128, [("sx:627539ff", None, None)]),
    "weapon.battle_rifle.shot_suppressed": ("weapon/battle_rifle/shot_suppressed", 48, [("fs:828790", ("starts", 0.7), None)]),
    "weapon.machine_pistol.shot": ("weapon/machine_pistol/shot", 96, [("sx:d53904ec", ("burst", 0.045, None, True), None)]),
    "weapon.machine_pistol.shot_distant": ("weapon/machine_pistol/shot_distant", 96, [("sx:5fec2c29", ("starts", 0.47), None)]),
    # "pitch it down"
    "weapon.machine_pistol.shot_suppressed": ("weapon/machine_pistol/shot_suppressed", 40,
                                              [("fs:242846", ("burst", 0.05, None, True), "asetrate=44100*0.85,aresample=44100"),
                                               ("fs:855656", None, None)]),
    "weapon.pocket_pistol.shot": ("weapon/pocket_pistol/shot", 80, [("fs:330634", None, None)]),
    "weapon.grenade_launcher.shot": ("weapon/grenade_launcher/shot", 96, [("gm:gun_grenade_launcher_shot_01", None, None)]),
    # the Warthog's minigun (his note: "0-5 sec"): one piece per round of the burst, and the barrels running down after
    "weapon.minigun.shot": ("weapon/minigun/shot", 128, [("fs:417148", ("burst", 0.04, 1.85, False), None)]),
    "weapon.minigun.spin_down": ("weapon/minigun/spin_down", 48, [("fs:417148", ("span", 1.85, 5.0), None)]),
}
# made from the near shot: event -> (near event, attenuation distance)
DISTANT = {"weapon.heavy_sniper.shot_suppressed_distant": ("weapon.heavy_sniper.shot_suppressed", 64),
           "weapon.battle_rifle.shot_distant": ("weapon.battle_rifle.shot", 176),
           "weapon.battle_rifle.shot_suppressed_distant": ("weapon.battle_rifle.shot_suppressed", 48),
           "weapon.machine_pistol.shot_suppressed_distant": ("weapon.machine_pistol.shot_suppressed", 40),
           "weapon.pocket_pistol.shot_distant": ("weapon.pocket_pistol.shot", 80),
           "weapon.grenade_launcher.shot_distant": ("weapon.grenade_launcher.shot", 128)}


def cut(a, how):
    """build_throw_sounds' cuts, plus ("starts", length): one piece of that length from each shot in the recording."""
    if how and how[0] == "starts":
        return [trim(a[s:s + int(how[1] * RATE)].copy()) for s in hits(a, how[1] * 0.9)]
    if how and how[0] == "burst":
        # ("burst", gap, end, tail): one shot per piece from a full-auto string (the first `end` s of it); with tail,
        # each piece rings out with the room after the string's last shot, so a single shot doesn't just tick
        _, gap, end, tail = how
        a = a[:int(end * RATE)] if end else a
        starts = hits(a, gap)
        step = int(np.median(np.diff(starts))) if len(starts) > 1 else int(0.1 * RATE)
        ring = a[starts[-1] + step:].copy() * 10 ** (-3 / 20) if tail else None
        out = []
        for s in starts[:-1]:
            shot = a[s:s + step].copy()
            if tail:
                n = min(int(0.01 * RATE), len(ring))
                shot[-n:] = shot[-n:] * np.linspace(1, 0, n) + ring[:n] * np.linspace(0, 1, n)
                shot = np.concatenate([shot, ring[n:]])
            out.append(trim(shot))
        return out
    out = pieces(a, how)
    if how and how[0] == "hits":
        # a shot cut off by the next one in the burst ends on a short fade, not a click
        out = [p for p in out if len(p) > 0.02 * RATE]
    return out


def filt(a, af):
    if not af:
        return a
    raw = subprocess.run(["ffmpeg", "-v", "error", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-", "-af", af,
                          "-f", "f32le", "-"], input=a.tobytes(), capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def write(folder, rng, takes):
    out = RES / "zomboidcraft/sounds" / folder
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.ogg"):
        old.unlink()
    names = []
    for i, a in enumerate(takes, 1):
        a = a * (10 ** (-1 / 20) / max(np.abs(a).max(), 1e-6))
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", "6", str(out / f"{i}.ogg")], input=a.astype(np.float32).tobytes(), check=True)
        names.append({"name": f"zomboidcraft:{folder}/{i}", "attenuation_distance": rng})
    return {"sounds": names}


def main():
    fs_dir, sx_dir, gm_dir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sx = json.loads((sx_dir / "manifest.json").read_text())
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    credits, near = {}, {}
    for event, (folder, rng, sources) in EVENTS.items():
        takes = []
        for src, how, af in sources:
            kind, sid = src.split(":", 1)
            if kind == "fs":
                info, path = fs[sid], fs_dir / fs[sid]["file"]
                rec = f"[{info['title']}]({info['page']})"
                credits.setdefault(rec, (info["user"], "Freesound, " + info.get("license", "CC0 1.0"), []))
            elif kind == "sx":
                info, path = sx["sx" + sid], sx_dir / sx["sx" + sid]["file"]
                rec = f"{info['title']} ({info['pack']})"
                credits.setdefault(rec, (info["bundle"], "Sonniss royalty-free", []))
            else:
                path = next(gm_dir.rglob(sid + ".wav"))
                rec = f"{sid} (Gamemaster gun packs, bonus sounds)"
                credits.setdefault(rec, ("Gamemaster Audio", "Gamemaster Audio licence (bought)", []))
            if event not in credits[rec][2]:
                credits[rec][2].append(event)
            takes += [filt(p, af) for p in cut(load(path), how)]
        near[event] = takes
        ours[event] = write(folder, rng, takes)
        print(event, len(takes), "takes", [round(len(t) / RATE, 2) for t in takes])
    for event, (src, rng) in DISTANT.items():
        takes = [filt(t, DISTANT_AF) for t in near[src]]
        ours[event] = write(event.replace(".", "/"), rng, takes)
        print(event, len(takes), "takes, from", src)
    sj.write_text(json.dumps(ours, indent=2) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Wave-two guns\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {', '.join(evs)} | {rec} | {by} | {lic} |" for rec, (by, lic, evs) in credits.items())
    section = ("\n## Wave-two guns\n\nPicked by Josiah Longfield on the Dead Air New Guns Two page, cut by "
               "tools/audio/build_gun3_sounds.py (far-off shots are made from the near ones). The Sonniss and Gamemaster "
               "originals are not included.\n\n| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
