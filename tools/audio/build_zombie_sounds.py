"""Builds the zombie and door sounds from Josiah's picks on the listen-and-pick page.

    python3 tools/audio/build_zombie_sounds.py RAW_DIR

RAW_DIR is the sounds-raw branch. Each pick is (Freesound id, start, length) as the page's clip
names give them. Writes assets/zomboidcraft/sounds/zombie/... and door/..., the zombie entries in
assets/zomboidcraft/sounds.json, assets/minecraft/sounds.json (replaces the vanilla zombie, husk
and zombie villager sounds, since all three are walkers) and the zombie section of CREDITS.md.
"""
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets"

# sound folder: (peak dB, picks); picks Josiah kept on the Dead Air Sound Picker, 2026-10-25
PICKS = {
    "zombie/idle": (-4.0, [("612699", 1.34, 4.0), ("612699", 9.34, 3.89), ("612699", 5.34, 4.0), ("788055", 0.98, 1.06),
                           ("788055", 5.05, 1.55), ("788055", 9.21, 1.46), ("147078", 3.95, 3.53), ("147078", 0.0, 4.0)]),
    "zombie/alert": (-1.0, [("435651", 0.0, 2.36), ("496080", 3.83, 2.37), ("496080", 0.0, 2.5)]),
    "zombie/chase": (-2.0, [("483592", 9.95, 2.5), ("483592", 4.95, 2.5), ("483592", 0.0, 2.5), ("125405", 5.23, 2.5)]),
    "zombie/bite": (-1.5, [("155973", 12.72, 0.74), ("155973", 10.66, 1.65)]),
    "zombie/hurt": (-2.0, [("633193", 0.43, 1.45), ("633193", 2.35, 1.47), ("633193", 4.82, 1.5)]),
    "door/bash": (-1.0, [("211450", 12.7, 1.5), ("211450", 0.0, 1.5), ("211450", 9.7, 1.32)]),
    "door/bash_metal": (-1.0, [("268126", 14.93, 0.92), ("268126", 4.48, 0.88)]),
    "door/break": (-1.0, [("160213", 0.0, 0.79)]),
}
# Deaths are layered: the last gurgle, then the body hitting the floor.
DEATH_VOICE = ("445986", 0.0, 1.54)
DEATH_FALLS = [(("395331", 0.0, 0.96), 0.55), (("417994", 0.0, 1.2), 0.65)]   # (pick, delay s)

# minecraft event -> (our folder, attenuation distance)
VANILLA = {}
for mob in ("zombie", "husk", "zombie_villager"):
    VANILLA[f"entity.{mob}.ambient"] = ("zombie/idle", 24)
    VANILLA[f"entity.{mob}.hurt"] = ("zombie/hurt", 16)
    VANILLA[f"entity.{mob}.death"] = ("zombie/death", 16)
VANILLA["entity.zombie.attack_wooden_door"] = ("door/bash", 32)
VANILLA["entity.zombie.attack_iron_door"] = ("door/bash_metal", 32)
VANILLA["entity.zombie.break_wooden_door"] = ("door/break", 32)
# our own events (registered in ModSounds by the build thread)
OURS = {"zombie.alert": ("zombie/alert", 32), "zombie.chase": ("zombie/chase", 24), "zombie.grab": ("zombie/bite", 16)}


def ff(*args):
    return subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def peak_of(path):
    log = subprocess.run(["ffmpeg", "-i", str(path), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    m = re.search(r"max_volume: (-?[\d.]+) dB", log)
    return float(m.group(1)) if m else 0.0


def cut(raw, manifest, pick, dst):
    sid, start, length = pick
    fade = min(0.12, length / 4)
    ff("-ss", str(start), "-t", str(length), "-i", str(raw / manifest[sid]["file"]), "-ac", "1", "-ar", "44100",
       "-af", f"afade=t=in:d=0.02,afade=t=out:st={length - fade}:d={fade}", str(dst))


def level(src, dst, peak):
    ff("-i", str(src), "-af", f"volume={peak - peak_of(src):.2f}dB", "-c:a", "libvorbis", "-q:a", "5", str(dst))
    src.unlink()


def main():
    raw = Path(sys.argv[1])
    manifest = json.loads((raw / "manifest.json").read_text())
    sounds = RES / "zomboidcraft/sounds"
    used = {}
    counts = {}

    for folder, (peak, picks) in PICKS.items():
        out = sounds / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        for n, pick in enumerate(picks, 1):
            tmp = out / f"{n}.wav"
            cut(raw, manifest, pick, tmp)
            level(tmp, out / f"{n}.ogg", peak)
            used.setdefault(pick[0], set()).add(folder)
        counts[folder] = len(picks)

    out = sounds / "zombie/death"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.ogg"):
        old.unlink()
    voice = out / "voice.wav"
    cut(raw, manifest, DEATH_VOICE, voice)
    used.setdefault(DEATH_VOICE[0], set()).add("zombie/death")
    for n, (fall, delay) in enumerate(DEATH_FALLS, 1):
        body = out / "body.wav"
        cut(raw, manifest, fall, body)
        mix = out / f"{n}.wav"
        ms = int(delay * 1000)
        ff("-i", str(voice), "-i", str(body), "-filter_complex",
           f"[1]adelay={ms}[b];[0][b]amix=inputs=2:duration=longest:normalize=0", str(mix))
        body.unlink()
        level(mix, out / f"{n}.ogg", -2.0)
        used.setdefault(fall[0], set()).add("zombie/death")
    voice.unlink()
    counts["zombie/death"] = len(DEATH_FALLS)

    def entries(folder, dist):
        return [{"name": f"zomboidcraft:{folder}/{n}", "attenuation_distance": dist} for n in range(1, counts[folder] + 1)]

    vanilla = {ev: {"replace": True, "subtitle": "subtitles." + ev, "sounds": entries(f, d)} for ev, (f, d) in VANILLA.items()}
    (RES / "minecraft").mkdir(exist_ok=True)
    (RES / "minecraft/sounds.json").write_text(json.dumps(vanilla, indent=2) + "\n")

    ours_path = RES / "zomboidcraft/sounds.json"
    ours = json.loads(ours_path.read_text())
    for ev, (f, d) in OURS.items():
        ours[ev] = {"sounds": entries(f, d)}
    ours_path.write_text(json.dumps(ours, indent=2) + "\n")

    rows = []
    for sid, folders in used.items():
        info = manifest[sid]
        user = unquote(info["user"])
        title = info["title"].removesuffix(f" by {user}")
        rows.append(f"| {', '.join(sorted(folders))} | [{title}]({info['page']}) | {user} | CC0 1.0 |")
    section = ("## Zombie and door sounds\n\nCC0 (public domain) recordings from [Freesound](https://freesound.org), "
               "picked by ear, cut, mixed to mono and levelled by tools/audio/build_zombie_sounds.py.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + "\n".join(rows) + "\n")
    credits = ROOT / "CREDITS.md"
    text = credits.read_text()
    text = re.sub(r"\n## Zombie and door sounds\n.*?(?=\n## |\Z)", "", text, flags=re.S).rstrip("\n") + "\n\n" + section
    credits.write_text(text)
    print({k: v for k, v in counts.items()})


if __name__ == "__main__":
    main()
