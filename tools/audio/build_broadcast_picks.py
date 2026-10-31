"""Builds the broadcast and far-off event sounds from Josiah's picks on the Dead Air Broadcasts page (2026-10-31).

    python3 tools/audio/build_broadcast_picks.py FREESOUND_DIR SONNISS_DIR

Run after build_broadcast_sounds.py (whose made-here alert tones he kept for the header and end-of-message bursts) and
build_alarm_sounds.py (his bell box and alarm panel picks are those stand-ins). The rest come from recordings:

    radio.eas.attention     DylanTheFish's alarm, its first 8 s (played after the header bursts)
    radio.static            the CB static, cut into 1.6 s washes, one under each caption
    radio.tune              the AM band static, cut into 1.2 s turns of the dial
    world.meta.gunfire      a distant sub-machine gun firefight, whole
    world.meta.scream       a woman screaming in pain, whole
    world.alarm.house.siren the burglar alarm siren, cut to seven whole wails (4 s) that loop without a seam
"""
import json
import sys
from pathlib import Path

import numpy as np

from build_gun3_sounds import write
from build_throw_sounds import RATE, fade, load, pieces, trim
from build_zombie_sounds import ROOT, RES

# event: (folder, range, source, cut)
EVENTS = {
    "radio.eas.attention": ("radio/eas/attention", 16, "fs:440307", ("span", 0.0, 8.0)),
    "radio.static": ("radio/static", 12, "sx:sx1690b8ff", ("chunks", 0.5, 25.5, 1.6)),
    "radio.tune": ("radio/tune", 8, "fs:38693", ("chunks", 0.3, 28.5, 1.2)),
    "world.meta.gunfire": ("world/meta/gunfire", 320, "fs:417690", None),
    "world.meta.scream": ("world/meta/scream", 220, "sx:sxe2cbd7dc", None),
    "world.alarm.house.siren": ("world/alarm/house/siren", 160, "fs:717414", ("loop", 5.0, 4.0)),
}


def loop(a, start, about):
    """Whole periods of a repeating wail: the lag near `about` s where the sound best matches itself, its end faded
    into its start over 30 ms so it repeats with no click."""
    s, n = int(start * RATE), int(0.5 * RATE)
    want = int(about * RATE)
    ref = a[s:s + n]
    best, score = want, -1.0
    for lag in range(want - int(0.3 * RATE), want + int(0.3 * RATE), 4):
        seg = a[s + lag:s + lag + n]
        c = float(np.dot(ref, seg) / (np.linalg.norm(ref) * np.linalg.norm(seg) + 1e-9))
        if c > score:
            best, score = lag, c
    x = int(0.03 * RATE)
    piece = a[s:s + best].copy()
    tail = a[s + best:s + best + x]
    piece[:x] = piece[:x] * np.linspace(0, 1, x) + tail * np.linspace(1, 0, x)
    print(f"  loop {best / RATE:.3f} s, match {score:.3f}")
    return [piece]


def main():
    fs_dir, sx_dir = Path(sys.argv[1]), Path(sys.argv[2])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sx = json.loads((sx_dir / "manifest.json").read_text())
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    credits = {}
    for event, (folder, rng, src, how) in EVENTS.items():
        kind, sid = src.split(":", 1)
        if kind == "fs":
            info, path = fs[sid], fs_dir / fs[sid]["file"]
            credits[f"[{info['title']}]({info['page']})"] = (event, info["user"], "Freesound, " + info.get("license", "CC0 1.0"))
        else:
            info, path = sx[sid], sx_dir / sx[sid]["file"]
            credits[f"{info['title']} ({info['pack']})"] = (event, info["bundle"], "Sonniss royalty-free")
        a = load(path)
        if how and how[0] == "loop":
            takes = loop(a, how[1], how[2])
        else:
            takes = pieces(a, how)
            if how and how[0] == "chunks":
                # drop near-silent pieces
                loud = np.median([np.sqrt(np.mean(t ** 2)) for t in takes])
                takes = [t for t in takes if np.sqrt(np.mean(t ** 2)) > 0.4 * loud]
        ours[event] = write(folder, rng, takes)
        print(event, len(takes), "takes")
    sj.write_text(json.dumps(ours, indent=2, ensure_ascii=False) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Broadcasts and far-off events\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {ev} | {rec} | {by} | {lic} |" for rec, (ev, by, lic) in credits.items())
    made = ("| radio.eas.header, radio.eas.eom, world.alarm.house.bell, block.alarm_panel.* | Made here: the EAS header bursts and "
            "two-tone signal to the published spec, the bell box and the keypad | ZomboidCraft | Own work |")
    section = ("\n## Broadcasts and far-off events\n\nPicked by Josiah Longfield on the Dead Air Broadcasts page, cut by "
               "tools/audio/build_broadcast_picks.py. The Sonniss originals are not included.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n" + made + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
