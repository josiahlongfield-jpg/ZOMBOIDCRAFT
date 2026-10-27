"""Makes shipped sounds louder by a share, for Josiah's "raise X by N%" asks, without clipping: a gain through a
fast limiter that holds peaks just under full scale, raised until the sound's measured loudness (EBU R128) has
gone up by N% (20 log10(1 + N/100) dB), so it really sounds that much louder even when its peak was already near
the top. Run once per ask; LOG lists every one so far (re-running applies them again).

    python3 tools/audio/louder.py FOLDER_UNDER_SOUNDS PERCENT
"""
import math
import subprocess
import sys
from pathlib import Path

from build_zombie_sounds import RES

# what has been raised so far, and when
LOG = [
    ("weapon/hunting_rifle/shot_suppressed", 20),     # Josiah 2026-10-27 10:18: suppressed sniper shot +20%
]


def loudness(path, af=None):
    cmd = ["ffmpeg", "-v", "info", "-i", str(path)] + ["-af", (af + "," if af else "") + "ebur128", "-f", "null", "/dev/null"]
    out = subprocess.run(cmd, capture_output=True, text=True).stderr
    return float([line for line in out.splitlines() if line.strip().startswith("I:")][-1].split()[1])


def main():
    folder, percent = sys.argv[1], float(sys.argv[2])
    want = 20 * math.log10(1 + percent / 100)
    for f in sorted((RES / "zomboidcraft/sounds" / folder).glob("*.ogg")):
        before = loudness(f)
        gain, af = 1.0, ""
        while gain < 4:
            gain += 0.02
            af = f"volume={gain:.2f},alimiter=limit=0.966:attack=1:release=60:level=disabled"
            if loudness(f, af) - before >= want:
                break
        tmp = f.with_suffix(".tmp.ogg")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(f), "-af", af,
                        "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "6", str(tmp)], check=True)
        tmp.replace(f)
        print(f.name, f"{before:.1f} -> {loudness(f):.1f} LUFS (gain x{gain:.2f})")


if __name__ == "__main__":
    main()
