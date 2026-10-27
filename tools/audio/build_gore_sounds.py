"""Builds the head-burst sound from the Sonniss gore packs.

    python3 tools/audio/build_gore_sounds.py SONNISS_DIR

SONNISS_DIR is the private Sonniss folder (fetch_sonniss.py) with its manifest.json. A head bursting is a wet smash
over a bone crack: each take layers two gore recordings, cut to their hit, mono, faded out and levelled. Writes
assets/zomboidcraft/sounds/weapon/bullet/head_burst/N.ogg, the weapon.bullet.head_burst entry in
assets/zomboidcraft/sounds.json and its rows in CREDITS.md.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/zomboidcraft"
EVENT = "weapon.bullet.head_burst"
FOLDER = "weapon/bullet/head_burst"
PEAK = -1.0
# each take: layers of (Sonniss id, start s, length s, delay s, gain dB), and its length
TAKES = [
    ([("sx43683588", 0.0, 1.3, 0.0, 0.0), ("sx35c6d124", 0.5, 0.5, 0.0, 8.0)], 1.3),         # juicy smash + snap
    ([("sx2ed9c623", 0.15, 0.8, 0.0, 0.0), ("sx43683588", 0.0, 0.9, 0.02, -6.0)], 0.95),     # beheading + smash
    ([("sx35587252", 0.42, 0.8, 0.0, 0.0), ("sxfaa3436e", 0.0, 0.6, 0.0, -3.0)], 0.85),      # body crunch + deep wound
]


def build(raw, layers, length, dst):
    args, chains = [], []
    for i, (sid, start, cut, delay, gain) in enumerate(layers):
        args += ["-i", str(raw / f"gore/{sid}.mp3")]
        ms = int(delay * 1000)
        chains.append(f"[{i}:a]aformat=channel_layouts=mono,aresample=44100,atrim=start={start}:duration={cut},"
                      f"asetpts=PTS-STARTPTS,adelay={ms}|{ms},volume={gain}dB[l{i}]")
    mix = "".join(f"[l{i}]" for i in range(len(layers)))
    fade = min(0.25, length / 3)
    chains.append(f"{mix}amix=inputs={len(layers)}:normalize=0,atrim=0:{length},"
                  f"afade=t=out:st={length - fade}:d={fade}[out]")
    tmp = dst.with_suffix(".tmp.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args, "-filter_complex", ";".join(chains), "-map", "[out]",
                    "-ac", "1", "-ar", "44100", str(tmp)], check=True)
    vol = subprocess.run(["ffmpeg", "-i", str(tmp), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    m = re.search(r"max_volume: (-?[\d.]+) dB", vol)
    gain = PEAK - float(m.group(1)) if m else 0.0
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp), "-af", f"volume={gain:.2f}dB",
                    "-c:a", "libvorbis", "-q:a", "5", str(dst)], check=True)
    tmp.unlink()


def main():
    raw = Path(sys.argv[1])
    manifest = json.loads((raw / "manifest.json").read_text())
    out = RES / "sounds" / FOLDER
    out.mkdir(parents=True, exist_ok=True)
    for n, (layers, length) in enumerate(TAKES, 1):
        build(raw, layers, length, out / f"{n}.ogg")

    sounds_json = RES / "sounds.json"
    sounds = json.loads(sounds_json.read_text())
    sounds[EVENT] = {"sounds": [{"name": f"zomboidcraft:{FOLDER}/{n}", "attenuation_distance": 64}
                                for n in range(1, len(TAKES) + 1)]}
    sounds_json.write_text(json.dumps(sounds, indent=2) + "\n")

    # credits: one row per recording used, under the weapon table
    used = sorted({sid for layers, _ in TAKES for sid, *_ in layers})
    rows = []
    for sid in used:
        info = manifest[sid]
        rows.append(f"| {EVENT} | {info['title']} ({info['pack']}) | {info['bundle']} | Sonniss royalty-free |")
    credits = ROOT / "CREDITS.md"
    lines = [l for l in credits.read_text().splitlines() if not l.startswith(f"| {EVENT} |")]
    at = max(i for i, l in enumerate(lines) if l.startswith("| weapon."))
    lines[at + 1:at + 1] = rows
    credits.write_text("\n".join(lines) + "\n")
    print(f"{len(TAKES)} takes, {len(rows)} credit rows")


if __name__ == "__main__":
    main()
