"""Builds the game's music from Josiah's picks on the Dead Air ambient pages (round 2, 2026-10-26).

    python3 tools/audio/build_music.py RAW_DIR

RAW_DIR holds the picked recordings at full quality (the Sonniss originals stay out of this repo; the Freesound one
is CC0). No songs: Josiah wants ambient noise under the world, not compositions. Each piece is made to loop without a
seam by crossfading its own end into its start, so it plays as one unbroken track:
  music.bed         Low Rumble (lennartgreen, Freesound, CC0): always there, very soft
  music.night       Eerie Night Ambience (Apple Hill Studios): at night
  music.chase       bass_bed_heavy_001 (3maze), seconds 2-23 looped while something's chasing you,
  music.chase_end   and the same recording from 23 s to its end, its own fade-out, once the chase is over
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_zombie_sounds import RES, ROOT

RATE = 44100
# event: (folder, source file, start s, end s or None, loop crossfade s (0: not a loop), peak dB)
PIECES = {
    "music.bed": ("music/bed", "low_rumble_638914.mp3", 0.0, None, 3.0, -6.0),
    "music.night": ("music/night", "Eerie_Night_Ambience.wav", 0.0, None, 8.0, -3.0),
    "music.chase": ("music/chase", "bass_bed_heavy_001.wav", 2.0, 23.0, 1.5, -2.0),
    "music.chase_end": ("music/chase_end", "bass_bed_heavy_001.wav", 23.0, None, 0.0, -2.0),
}
CREDITS = [
    ("music.bed", "[Low Rumble - loopable ambiance](https://freesound.org/people/lennartgreen/sounds/638914/)",
     "lennartgreen", "CC0 1.0"),
    ("music.night", "Eerie Night Ambience (Apple Hill Studios - Horror Background Ambiences)", "Sonniss GameAudioGDC 2020",
     "Sonniss royalty-free"),
    ("music.chase, music.chase_end", "bass_bed_heavy_001 (3maze - DRONEA)", "Sonniss GameAudioGDC 2019",
     "Sonniss royalty-free"),
]


def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "2", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).copy()


def seamless(a, fade):
    """The piece minus its first `fade` seconds, its last `fade` seconds blended into those, so the end runs
    straight back into where it restarts (equal-power curves keep the level steady through the join)."""
    n = int(fade * RATE)
    t = np.linspace(0, np.pi / 2, n, dtype=np.float32)[:, None]
    out = a[n:].copy()
    out[-n:] = a[-n:] * np.cos(t) + a[:n] * np.sin(t)
    return out


def main():
    raw = Path(sys.argv[1])
    ours_path = RES / "zomboidcraft/sounds.json"
    ours = json.loads(ours_path.read_text())
    for event, (folder, src, start, end, fade, peak) in PIECES.items():
        a = load(raw / src)
        a = a[int(start * RATE):int(end * RATE) if end else len(a)]
        if fade:
            a = seamless(a, fade)
        a *= 10 ** (peak / 20) / max(np.abs(a).max(), 1e-6)
        out = RES / "zomboidcraft/sounds" / folder
        out.mkdir(parents=True, exist_ok=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "2", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", "5", str(out / "1.ogg")], input=a.tobytes(), check=True)
        ours[event] = {"sounds": [{"name": f"zomboidcraft:{folder}/1", "stream": True}]}
        print(event, round(len(a) / RATE, 2), "s")
    ours_path.write_text(json.dumps(ours, indent=2) + "\n")

    credits = ROOT / "CREDITS.md"
    text = credits.read_text()
    head, sep, rest = text.partition("\n## Music\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {e} | {rec} | {by} | {lic} |" for e, rec, by, lic in CREDITS)
    section = ("\n## Music\n\nAmbient beds picked by Josiah Longfield, made to loop seamlessly by "
               "tools/audio/build_music.py. The Sonniss originals are not included.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    credits.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
