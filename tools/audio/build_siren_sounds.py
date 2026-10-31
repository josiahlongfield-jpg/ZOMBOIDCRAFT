"""Builds the emergency vehicle siren and the car alarm from Josiah's picks on the Sirens and Car Alarms page (2026-10-31).

    python3 tools/audio/build_siren_sounds.py FREESOUND_DIR SONNISS_DIR

VEHICLES replays each sound on a timer while the siren is on or the alarm is going off, so every take is a loop that
joins end to start without a seam:

    car.siren   three of Coll Anderson's emergency sirens (a whooping run, a yelp, a distant whoop). Each recording's
                bursts and the gaps between them are repeated to exactly 6 s, ending in its own gap.
    car.alarm   erkanozan's car alarm in a parking structure: six of its horn pulses, 4 s.

The loops join in the quiet between bursts, with a 30 ms crossfade, so there is no click.
"""
import json
import sys
from pathlib import Path

import numpy as np

from build_gun3_sounds import write
from build_throw_sounds import RATE, load
from build_zombie_sounds import ROOT, RES

SIREN_LENGTH = 6.0
ALARM_PULSES = 6
X = int(0.03 * RATE)


def bursts(a):
    """(start, end) of each loud stretch, in samples: 10 ms windows above a third of the peak, gaps under 60 ms ignored."""
    h = int(0.01 * RATE)
    env = np.array([np.sqrt(np.mean(a[i:i + h] ** 2)) for i in range(0, len(a) - h, h)])
    loud = env > env.max() / 3
    out, start = [], None
    for i, on in enumerate(loud):
        if on and start is None:
            start = i
        elif not on and start is not None:
            out.append([start, i])
            start = None
    if start is not None:
        out.append([start, len(loud)])
    merged = [out[0]]
    for s, e in out[1:]:
        if s - merged[-1][1] < 6:
            merged[-1][1] = e
        else:
            merged.append([s, e])
    return [(s * h, e * h) for s, e in merged]


def unit(a, tail=None):
    """One repeat: from just before the first burst to the end of the last, plus a gap like the ones between them (or
    `tail` seconds when there is only one burst)."""
    b = bursts(a)
    start = max(0, b[0][0] - int(0.01 * RATE))
    gaps = [b[i + 1][0] - b[i][1] for i in range(len(b) - 1)]
    gap = int(np.mean(gaps)) if gaps and tail is None else int((tail or 0.3) * RATE)
    return a[start:min(len(a), b[-1][1] + gap)]


def tile(u, length):
    """Repeats u to exactly `length` samples (whole repeats, the last one's gap trimmed or padded), then fades the end
    into the start so the loop is seamless."""
    n = max(1, int(round(length / len(u))))
    out = np.concatenate([u] * n)
    out = out[:length] if len(out) >= length else np.concatenate([out, np.zeros(length - len(out), np.float32)])
    out = out.copy()
    out[:X] = out[:X] * np.linspace(0, 1, X) + out[-X:] * np.linspace(1, 0, X)
    return out[:-X] if len(out) > X else out


def alarm(a):
    """Six whole pulses: the period is where the recording best matches itself near the gap between onsets."""
    b = bursts(a)
    period = int(np.median([b[i + 1][0] - b[i][0] for i in range(len(b) - 1)]))
    s = max(0, b[0][0] - int(0.01 * RATE))
    ref = a[s:s + int(0.3 * RATE)]
    best, score = period, -1.0
    for lag in range(period - int(0.05 * RATE), period + int(0.05 * RATE), 2):
        seg = a[s + lag:s + lag + len(ref)]
        if len(seg) < len(ref):
            break
        c = float(np.dot(ref, seg) / (np.linalg.norm(ref) * np.linalg.norm(seg) + 1e-9))
        if c > score:
            best, score = lag, c
    pulses = [a[s + i * best:s + (i + 1) * best] for i in range(len(b))]
    pulses = [p for p in pulses if len(p) == best]
    seq = [pulses[i % len(pulses)] for i in range(ALARM_PULSES)]
    print(f"  alarm pulse {best / RATE:.3f} s, match {score:.3f}, {len(pulses)} pulses in the recording")
    return tile(np.concatenate(seq), best * ALARM_PULSES + X)


SIRENS = ["sx:sx2af963b9", "sx:sxf6c175d6", "sx:sx5dbe55e2"]
ALARM = "fs:51747"


def main():
    fs_dir, sx_dir = Path(sys.argv[1]), Path(sys.argv[2])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sx = json.loads((sx_dir / "manifest.json").read_text())
    credits = {}

    def source(src, event):
        kind, sid = src.split(":", 1)
        if kind == "fs":
            info = fs[sid]
            credits[f"[{info['title']}]({info['page']})"] = (event, info["user"], "Freesound, " + info.get("license", "CC0 1.0"))
            return load(fs_dir / info["file"])
        info = sx[sid]
        credits[f"{info['title']} ({info['pack']})"] = (event, info["bundle"], "Sonniss royalty-free")
        return load(sx_dir / info["file"])

    takes = []
    for src in SIRENS:
        u = unit(source(src, "car.siren"), tail=0.45 if src.endswith("5dbe55e2") else None)
        takes.append(tile(u, int(SIREN_LENGTH * RATE) + X))
        print(f"  siren {src}: repeat {len(u) / RATE:.2f} s")
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    ours["car.siren"] = write("car/siren", 160, takes)
    ours["car.alarm"] = write("car/alarm", 160, [alarm(source(ALARM, "car.alarm"))])
    sj.write_text(json.dumps(ours, indent=2, ensure_ascii=False) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Sirens and car alarms\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {ev} | {rec} | {by} | {lic} |" for rec, (ev, by, lic) in credits.items())
    section = ("\n## Sirens and car alarms\n\nPicked by Josiah Longfield on the Sirens and Car Alarms page, cut into loops by "
               "tools/audio/build_siren_sounds.py. The Sonniss originals are not included.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
