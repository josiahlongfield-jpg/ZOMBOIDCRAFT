"""Builds the Harvestman's sounds from the Sonniss library, plus its mandible clicks made from scratch.

    python3 tools/audio/build_harvestman_sounds.py SONNISS_DIR

SONNISS_DIR is the private Sonniss folder (fetch_sonniss.py) with its manifest.json. Every take layers recordings
(cut, pitched up or down by playing them faster or slower, filtered) so the creature sounds like nothing else in the
mod: its points are talons on concrete pitched up, its scrape a rusty door, its screech and shrieks monster vocals
raised into a thin, buzzing register, its call a roar heard through a long way of streets. Writes
assets/zomboidcraft/sounds/creature/harvestman/<name>/N.ogg, the creature.harvestman.* entries at the top of
assets/zomboidcraft/sounds.json, and the Harvestman section of CREDITS.md.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/zomboidcraft"
FOLDER = "creature/harvestman"
PEAK = -1.0
RATE = 44100

TALONS, RICOCHET, RUSTY = "step/sxd830e787", "imp/sx3940cdd3", "door/sx8ae98a3f"
HISS, SLURP, SQUEAL = "idle/sx228b8f85", "s_drink/sxd090fb7b", "idle/sx4d1cca75"
GROWL164, PAIN121, GRUNT240 = "scream/sx09af6727", "scream/sxd0688670", "scream/sxe4f4ddbe"
SEA_BEAST, FLUTTER, ROAR009, ORC = "idle/sxdf711c61", "idle/sx28544dc7", "scream/sx1f2b8b99", "idle/sx5023ac6f"
SNAP, SMASH = "gore/sx35c6d124", "gore/sx43683588"
BUZZ = "tremolo=f=34:d=0.45"

# each sound: its attenuation distance (or None), and its takes; a take is its layers and its length (seconds after
# pitching). A layer is (recording, start s, cut s, delay s, gain dB, speed, extra ffmpeg filters).
SOUNDS = {
    "tap": (16, [
        ([(TALONS, 0.13, 0.16, 0, 0, 1.35, "highpass=f=500")], 0.14),
        ([(TALONS, 1.00, 0.16, 0, 0, 1.45, "highpass=f=500")], 0.13),
        ([(TALONS, 2.76, 0.16, 0, 0, 1.30, "highpass=f=450")], 0.14),
        ([(TALONS, 6.21, 0.16, 0, 0, 1.50, "highpass=f=550")], 0.12),
    ]),
    "scrape": (24, [
        ([(RUSTY, 0.0, 1.35, 0, 0, 1.15, "highpass=f=300")], 1.15),
    ]),
    "click": (16, None),  # made from scratch below
    "hiss": (24, [
        ([(HISS, 0.0, 1.45, 0, 0, 0.92, "highpass=f=800," + BUZZ)], 1.5),
        ([(HISS, 0.0, 1.45, 0, 0, 1.12, "highpass=f=900," + BUZZ)], 1.25),
        ([(HISS, 0.0, 1.45, 0, 0, 1.30, "highpass=f=1000," + BUZZ)], 1.1),
    ]),
    "screech": (None, [
        ([(SQUEAL, 0.0, 0.98, 0, 0, 1.25, BUZZ), (GROWL164, 0.0, 1.3, 0.05, -4, 1.5, "highpass=f=400")], 1.0),
        ([(PAIN121, 0.0, 1.1, 0, 0, 1.45, BUZZ), (ORC, 0.08, 1.5, 0.03, -5, 1.3, "highpass=f=300")], 1.15),
    ]),
    "feed": (16, [
        ([(SLURP, 0.5, 3.0, 0, 8, 0.8, "lowpass=f=3000"), (SMASH, 0.0, 1.3, 0.4, -8, 0.7, ""),
          (HISS, 0.0, 1.45, 1.6, -14, 0.6, "lowpass=f=1500")], 3.6),
    ]),
    "clang": (32, [
        ([(RICOCHET, 0.0, 0.5, 0, 0, 1.0, "")], 0.5),
        ([(RICOCHET, 2.62, 0.5, 0, 0, 1.08, "")], 0.46),
        ([(RICOCHET, 4.78, 0.5, 0, 0, 0.95, "")], 0.52),
        ([(RICOCHET, 7.19, 0.5, 0, 0, 1.15, "")], 0.43),
    ]),
    "hurt": (None, [
        ([(GRUNT240, 0.18, 0.75, 0, 0, 1.45, BUZZ), (RICOCHET, 4.78, 0.4, 0, -12, 1.2, "")], 0.55),
        ([(GROWL164, 0.2, 0.7, 0, 0, 1.6, BUZZ), (RICOCHET, 2.62, 0.4, 0, -12, 1.2, "")], 0.45),
        ([(PAIN121, 0.0, 0.85, 0, 0, 1.6, BUZZ), (RICOCHET, 7.19, 0.4, 0, -12, 1.2, "")], 0.55),
    ]),
    "leg_off": (None, [
        ([(SNAP, 0.5, 0.5, 0, 2, 1.0, ""), (SEA_BEAST, 0.1, 2.4, 0.06, -2, 1.3, BUZZ)], 1.9),
    ]),
    "death": (None, [
        ([(FLUTTER, 0.0, 4.9, 0, 0, 1.1, "highpass=f=200"), (SEA_BEAST, 0.1, 3.0, 0.3, -6, 0.9, BUZZ),
          (RICOCHET, 0.0, 0.5, 3.6, -10, 0.8, "")], 4.5),
    ]),
    "call": (None, [
        ([(ROAR009, 0.0, 4.3, 0, 0, 1.3, "lowpass=f=1800,aecho=0.8:0.7:140|310:0.45|0.3")], 3.9),
    ]),
}


def level(tmp, dst):
    """Raises a take to the shared peak and writes it as Ogg Vorbis."""
    vol = subprocess.run(["ffmpeg", "-i", str(tmp), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    m = re.search(r"max_volume: (-?[\d.]+) dB", vol)
    gain = PEAK - float(m.group(1)) if m else 0.0
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp), "-af", f"volume={gain:.2f}dB",
                    "-c:a", "libvorbis", "-q:a", "5", str(dst)], check=True)
    tmp.unlink()


def build(raw, layers, length, dst):
    args, chains = [], []
    for i, (rec, start, cut, delay, gain, speed, extra) in enumerate(layers):
        args += ["-i", str(raw / f"{rec}.mp3")]
        ms = int(delay * 1000)
        chain = (f"[{i}:a]aformat=channel_layouts=mono,aresample={RATE},atrim=start={start}:duration={cut},"
                 f"asetpts=PTS-STARTPTS,afade=t=in:d=0.004,asetrate={int(RATE * speed)},aresample={RATE}")
        if extra:
            chain += "," + extra
        chains.append(chain + f",adelay={ms}|{ms},volume={gain}dB[l{i}]")
    mix = "".join(f"[l{i}]" for i in range(len(layers)))
    fade = min(0.25, length / 3)
    chains.append(f"{mix}amix=inputs={len(layers)}:normalize=0,apad,atrim=0:{length},"
                  f"afade=t=out:st={length - fade}:d={fade}[out]")
    tmp = dst.with_suffix(".tmp.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args, "-filter_complex", ";".join(chains), "-map", "[out]",
                    "-ac", "1", "-ar", str(RATE), str(tmp)], check=True)
    level(tmp, dst)


def clicks(seed, dst):
    """Its mandibles: a short, uneven run of hard clicks, each a ringing chitinous tick."""
    rng = np.random.default_rng(seed)
    n = int(RATE * 0.7)
    out = np.zeros(n)
    t = 0.02
    for _ in range(rng.integers(4, 8)):
        at = int(t * RATE)
        ln = int(RATE * 0.03)
        k = np.arange(ln) / RATE
        f = rng.uniform(2300, 3600)
        tick = (np.sin(2 * np.pi * f * k) + 0.5 * np.sin(2 * np.pi * f * 1.73 * k)) * np.exp(-k * rng.uniform(160, 260))
        tick += rng.normal(0, 1, ln) * np.exp(-k * 900) * 0.6
        out[at:at + ln] += tick[:max(0, min(ln, n - at))] * rng.uniform(0.6, 1.0)
        t += rng.uniform(0.035, 0.11)
    out /= np.abs(out).max()
    pcm = (out * 32000).astype("<i2").tobytes()
    tmp = dst.with_suffix(".tmp.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "-", str(tmp)],
                   input=pcm, check=True)
    level(tmp, dst)


def main():
    raw = Path(sys.argv[1])
    manifest = json.loads((raw / "manifest.json").read_text())
    entries, used = {}, {}
    for name, (dist, takes) in SOUNDS.items():
        out = RES / "sounds" / FOLDER / name
        out.mkdir(parents=True, exist_ok=True)
        count = 3 if takes is None else len(takes)
        for n in range(1, count + 1):
            if takes is None:
                clicks(n * 7919, out / f"{n}.ogg")
            else:
                layers, length = takes[n - 1]
                build(raw, layers, length, out / f"{n}.ogg")
                for rec, *_ in layers:
                    used.setdefault(rec.split("/")[1], set()).add(name)
        rows = []
        for n in range(1, count + 1):
            row = {"name": f"zomboidcraft:{FOLDER}/{name}/{n}"}
            if dist:
                row["attenuation_distance"] = dist
            rows.append(row)
        entries[f"creature.harvestman.{name}"] = {"sounds": rows}

    # the creature's entries first in sounds.json, so they never collide with sounds other work adds at the end
    sounds_json = RES / "sounds.json"
    sounds = json.loads(sounds_json.read_text())
    rest = {k: v for k, v in sounds.items() if not k.startswith("creature.harvestman.")}
    sounds_json.write_text(json.dumps({**entries, **rest}, indent=2) + "\n")

    credits = ROOT / "CREDITS.md"
    text = credits.read_text()
    text = re.sub(r"\n## Harvestman sounds\n.*?(?=\n## |\Z)", "", text, flags=re.S).rstrip("\n")
    table = ["", "## Harvestman sounds", "",
             "Layered, pitched and filtered by tools/audio/build_harvestman_sounds.py; its clicks are made from scratch.", "",
             "| Sound | Recording | By | Licence |", "|---|---|---|---|"]
    for sid, names in sorted(used.items()):
        info = manifest[sid]
        events = ", ".join(f"creature.harvestman.{n}" for n in sorted(names))
        table.append(f"| {events} | {info['title']} ({info['pack']}) | {info['bundle']} | Sonniss royalty-free |")
    credits.write_text(text + "\n" + "\n".join(table) + "\n")
    print(f"{len(entries)} sounds, {len(used)} recordings credited")


if __name__ == "__main__":
    main()
