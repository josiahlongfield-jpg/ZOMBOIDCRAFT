"""Applies Josiah's edits from the Dead Air Loop Bench page to the shipped sounds.

    python3 tools/audio/apply_bench_edits.py tools/audio/bench_edits/2026-10-28.json

Each edit names a sound by its path under assets/zomboidcraft/sounds (no .ogg) and gives what he set on the page:
start and end (seconds), loop with a crossfade, fade in and out, and volume. The sound is rendered exactly as the
page plays it (render() in loop-bench.html) and re-encoded in place, keeping its channels and sample rate.

A sound he turned all the way down (-18 dB, the bottom of the slider) is one he wants gone: its file is deleted and
it is taken out of every event in both sounds.json files. An event left with no sounds stays registered and silent.

Edits are made against the files as they were on the page, so run this once per edits file, not again.
"""
import json
import subprocess
import sys

import numpy as np

from build_zombie_sounds import RES, ff

SND = RES / "zomboidcraft/sounds"
REMOVE_DB = -18.0


def decode(path):
    probe = json.loads(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=channels,sample_rate",
                                       "-of", "json", str(path)], capture_output=True, text=True, check=True).stdout)
    ch, sr = int(probe["streams"][0]["channels"]), int(probe["streams"][0]["sample_rate"])
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, ch).copy(), sr


def render(a, sr, e):
    """The page's render(): trim, then a loop's end crossfaded (equal power) into its start, or plain linear fades; then volume."""
    s = max(0, round(e["start"] * sr))
    t = min(len(a), round(e["end"] * sr))
    if t - s < 256:
        t = min(len(a), s + 256)
    span = t - s
    n = min(round(e["xfade"] * sr), span // 2) if e["loop"] else 0
    length = span - n
    o = a[s + n:s + n + length].copy()
    if n > 1:
        th = (np.arange(n) / (n - 1) * np.pi / 2)[:, None]
        o[length - n:] = a[s + span - n:s + span] * np.cos(th) + a[s:s + n] * np.sin(th)
    elif not e["loop"]:
        fi, fo = min(length, round(e["fadeIn"] * sr)), min(length, round(e["fadeOut"] * sr))
        if fi:
            o[:fi] *= (np.arange(fi) / fi)[:, None]
        if fo:
            o[length - fo:] *= (np.arange(fo)[::-1] / fo)[:, None]
    return o * 10 ** (e["gainDb"] / 20)


def unlist(names):
    """Takes the named sounds out of every event in both sounds.json files."""
    for sj in (RES / "zomboidcraft/sounds.json", RES / "minecraft/sounds.json"):
        data = json.loads(sj.read_text())
        for body in data.values():
            body["sounds"] = [s for s in body.get("sounds", [])
                              if (s if isinstance(s, str) else s.get("name")) not in names]
        sj.write_text(json.dumps(data, indent=2) + "\n")


def main():
    edits = json.loads(open(sys.argv[1]).read())
    gone = set()
    for e in edits:
        path = SND / (e["path"] + ".ogg")
        if e["gainDb"] <= REMOVE_DB:
            path.unlink(missing_ok=True)
            gone.add("zomboidcraft:" + e["path"])
            print("removed", e["path"])
            continue
        a, sr = decode(path)
        o = render(a, sr, e)
        peak = float(np.abs(o).max())
        # turned up past full scale: a fast limiter holds the peaks just under it instead of letting them crackle
        af = ["-af", "alimiter=limit=0.97:attack=1:release=40:level=disabled"] if peak > 0.97 else []
        if af:
            print(f"  {e['path']} peaked at {20 * np.log10(peak):+.1f} dB, limited")
        tmp = path.with_suffix(".raw")
        o.astype(np.float32).tofile(tmp)
        ff("-f", "f32le", "-ar", str(sr), "-ac", str(a.shape[1]), "-i", str(tmp), *af, "-c:a", "libvorbis", "-q:a", "5", str(path))
        tmp.unlink()
        print("edited", e["path"], f"{len(a) / sr:.2f} -> {len(o) / sr:.2f} s")
    if gone:
        unlist(gone)


if __name__ == "__main__":
    main()
