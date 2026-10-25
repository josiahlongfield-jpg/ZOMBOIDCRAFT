"""Builds the zombie and door sounds from Josiah's picks on the listen-and-pick page.

    python3 tools/audio/build_zombie_sounds.py RAW_DIR[,RAW_DIR...]

Each RAW_DIR holds a manifest.json: the sounds-raw branch (Freesound) and the private Sonniss
folder (fetch_sonniss.py). Each pick is (Freesound id, start, length) as the page's clip
names give them. Writes assets/zomboidcraft/sounds/zombie/... and door/..., the zombie entries in
assets/zomboidcraft/sounds.json, assets/minecraft/sounds.json (replaces the vanilla zombie, husk
and zombie villager sounds, since all three are walkers) and the zombie section of CREDITS.md.
"""
import json
import random
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets"

# sound folder: (peak dB, picks); voices sit about 6 dB under the first build (Josiah: "the zombies are incredibly
# loud"); picks Josiah kept on the Dead Air Sound Picker, rounds 1 and 2, 2026-10-25
SONNISS_AGGRESSIVE = [("sxa617436b", 1.63, 1.52), ("sxa617436b", 4.1, 1.78), ("sx62e67533", 6.63, 1.25),
                      ("sx01e323ba", 4.8, 1.59), ("sx01e323ba", 3.1, 1.41), ("sxed9a54c5", 0.0, 3.56)]
IDLE = [("612699", 1.34, 4.0), ("612699", 9.34, 3.89), ("612699", 5.34, 4.0), ("788055", 0.98, 1.06),
        ("788055", 5.05, 1.55), ("788055", 9.21, 1.46), ("147078", 3.95, 3.53), ("147078", 0.0, 4.0),
        *SONNISS_AGGRESSIVE, ("sx586138cb", 0.0, 2.5), ("sx586138cb", 2.9, 4.0), ("sx892323af", 0.0, 1.47),
        ("sxe22549bb", 0.0, 2.61), ("sx785376e5", 0.0, 2.96), ("sxe65c6dd2", 0.0, 1.4)]
# Josiah: the round 1 chase picks "are good for hordes but not small groups or individual zombies", so they
# feed the angry horde, and a lone chaser uses the aggressive single voices he kept.
CROWD_CHASE = [("483592", 9.95, 2.5), ("483592", 4.95, 2.5), ("483592", 0.0, 2.5), ("125405", 5.23, 2.5)]
ALERT = [("435651", 0.0, 2.36), ("496080", 3.83, 2.37), ("496080", 0.0, 2.5)]
PICKS = {
    "zombie/idle": (-10.0, IDLE),
    "zombie/alert": (-5.0, ALERT),
    "zombie/chase": (-7.0, SONNISS_AGGRESSIVE),
    "zombie/bite": (-4.0, [("155973", 12.72, 0.74), ("155973", 10.66, 1.65)]),
    "zombie/hurt": (-6.0, [("633193", 0.43, 1.45), ("633193", 2.35, 1.47), ("633193", 4.82, 1.5)]),
    "door/bash": (-1.0, [("211450", 12.7, 1.5), ("211450", 0.0, 1.5), ("211450", 9.7, 1.32)]),
    "door/bash_metal": (-1.0, [("268126", 14.93, 0.92), ("268126", 4.48, 0.88), ("sxfc23509d", 6.46, 1.47),
                                ("sxc350cd86", 0.0, 0.96)]),
    "door/break": (-1.0, [("160213", 0.0, 0.79)]),
}
# Deaths are layered: the last gurgle, then the body hitting the floor.
DEATH_VOICE = ("445986", 0.0, 1.54)
DEATH_FALLS = [(("395331", 0.0, 0.96), 0.55), (("417994", 0.0, 1.2), 0.65)]   # (pick, delay s)

# Dragging, scuffing steps; each take is split into single scuffs.
STEPS = [("221650", 5.11, 2.0), ("221650", 2.69, 1.94), ("669704", 15.85, 0.66), ("451861", 3.37, 0.73),
         ("451861", 14.39, 0.7)]

# Hordes are built from the zombies' own voices (Josiah: "a large bunch of the idle noises with different levels
# ... as long as its not too overpowering"; the angry one the same way). (folder, sources, voices, seconds, peak dB, files)
HORDES = [("horde/dormant", IDLE, 14, 16.0, -14.0, 4), ("horde/angry", CROWD_CHASE + SONNISS_AGGRESSIVE + ALERT + IDLE[:8], 18, 12.0, -10.0, 4)]

# Of the built takes, the ones Josiah approved on the picker (1-based); the rest are dropped and renumbered.
KEEP = {"horde/angry": [2, 4], "zombie/step": [1, 4, 5, 6, 10, 12]}

# minecraft event -> (our folder, attenuation distance)
VANILLA = {}
for mob in ("zombie", "husk", "zombie_villager"):
    VANILLA[f"entity.{mob}.ambient"] = ("zombie/idle", 24)
    VANILLA[f"entity.{mob}.hurt"] = ("zombie/hurt", 16)
    VANILLA[f"entity.{mob}.death"] = ("zombie/death", 16)
    VANILLA[f"entity.{mob}.step"] = ("zombie/step", 16)
VANILLA["entity.zombie.attack_wooden_door"] = ("door/bash", 32)
VANILLA["entity.zombie.attack_iron_door"] = ("door/bash_metal", 32)
VANILLA["entity.zombie.break_wooden_door"] = ("door/break", 32)
# our own events (registered in ModSounds by the build thread)
OURS = {"zombie.alert": ("zombie/alert", 32), "zombie.chase": ("zombie/chase", 24), "zombie.grab": ("zombie/bite", 16),
        "zombie.horde_dormant": ("horde/dormant", 48), "zombie.horde_angry": ("horde/angry", 48)}


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
    ff("-ss", str(start), "-t", str(length), "-i", str(raw[sid] / manifest[sid]["file"]), "-ac", "1", "-ar", "44100",
       "-af", f"afade=t=in:d=0.02,afade=t=out:st={length - fade}:d={fade}", str(dst))


def level(src, dst, peak):
    ff("-i", str(src), "-af", f"volume={peak - peak_of(src):.2f}dB", "-c:a", "libvorbis", "-q:a", "5", str(dst))
    src.unlink()


def merge_vanilla(entries):
    """Writes these overrides into assets/minecraft/sounds.json, keeping the ones other build scripts own."""
    path = RES / "minecraft/sounds.json"
    current = json.loads(path.read_text()) if path.exists() else {}
    current.update(entries)
    path.write_text(json.dumps(current, indent=2) + "\n")


def main():
    manifest, raw = {}, {}
    for d in sys.argv[1].split(","):
        for sid, info in json.loads((Path(d) / "manifest.json").read_text()).items():
            manifest[sid], raw[sid] = info, Path(d)
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
        level(mix, out / f"{n}.ogg", -6.0)
        used.setdefault(fall[0], set()).add("zombie/death")
    voice.unlink()
    counts["zombie/death"] = len(DEATH_FALLS)

    out = sounds / "zombie/step"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.ogg"):
        old.unlink()
    n = 0
    for pick in STEPS:
        take = out / "take.wav"
        cut(raw, manifest, pick, take)
        log = subprocess.run(["ffmpeg", "-i", str(take), "-af", "silencedetect=noise=-30dB:d=0.08", "-f", "null", "-"],
                             capture_output=True, text=True).stderr
        ends = [0.0] + [float(x) for x in re.findall(r"silence_end: ([\d.]+)", log)]
        spans = [(max(0.0, t - 0.03), 0.9) for t in ends if t < pick[2] - 0.25][:3]
        for start, length in spans:
            n += 1
            one = out / f"{n}.wav"
            cut(raw, manifest, (pick[0], pick[1] + start, min(length, pick[2] - start)), one)
            level(one, out / f"{n}.ogg", -10.0)
        take.unlink()
        used.setdefault(pick[0], set()).add("zombie/step")
    counts["zombie/step"] = n

    rng = random.Random(42)
    for folder, sources, voices, seconds, peak, files in HORDES:
        out = sounds / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        parts = []
        for k, pick in enumerate(sources):
            part = out / f"src{k}.wav"
            cut(raw, manifest, pick, part)
            parts.append(part)
            used.setdefault(pick[0], set()).add(folder)
        for n in range(1, files + 1):
            # each voice: a random take, start time, level, pitch and distance (low-pass), all mixed to mono
            args, chains = [], []
            for v in range(voices):
                args += ["-i", str(rng.choice(parts))]
                delay = int(rng.uniform(0, seconds - 1.0) * 1000)
                gain = rng.uniform(-22, -6)
                rate = rng.uniform(0.82, 1.05)
                cutoff = int(rng.uniform(700, 4000))
                chains.append(f"[{v}]asetrate={int(44100 * rate)},aresample=44100,lowpass=f={cutoff},"
                              f"volume={gain:.1f}dB,adelay={delay}[v{v}]")
            mix = "".join(f"[v{v}]" for v in range(voices))
            graph = ";".join(chains) + f";{mix}amix=inputs={voices}:duration=longest:normalize=0," \
                    f"atrim=0:{seconds},afade=t=in:d=1.5,afade=t=out:st={seconds - 2}:d=2"
            wav = out / f"{n}.wav"
            ff(*args, "-filter_complex", graph, "-ac", "1", str(wav))
            level(wav, out / f"{n}.ogg", peak)
        for part in parts:
            part.unlink()
        counts[folder] = files

    for folder, keep in KEEP.items():
        out = sounds / folder
        for n in range(1, counts[folder] + 1):
            if n not in keep:
                (out / f"{n}.ogg").unlink()
        for new, n in enumerate(keep, 1):
            (out / f"{n}.ogg").rename(out / f"{new}.ogg")
        counts[folder] = len(keep)

    def entries(folder, dist):
        return [{"name": f"zomboidcraft:{folder}/{n}", "attenuation_distance": dist} for n in range(1, counts[folder] + 1)]

    vanilla = {ev: {"replace": True, "subtitle": "subtitles." + ev, "sounds": entries(f, d)} for ev, (f, d) in VANILLA.items()}
    (RES / "minecraft").mkdir(exist_ok=True)
    merge_vanilla(vanilla)

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
        if "bundle" in info:
            rows.append(f"| {', '.join(sorted(folders))} | {title} ({info['pack']}) | {info['bundle']} | Sonniss royalty-free |")
        else:
            rows.append(f"| {', '.join(sorted(folders))} | [{title}]({info['page']}) | {user} | CC0 1.0 |")
    section = ("## Zombie and door sounds\n\nCC0 (public domain) recordings from [Freesound](https://freesound.org) and "
               "royalty-free sounds from the [Sonniss GameAudioGDC](https://sonniss.com/gameaudiogdc) bundles, picked by ear, "
               "cut, mixed to mono and levelled by tools/audio/build_zombie_sounds.py. The horde sounds are mixed from the "
               "zombie voices below.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + "\n".join(rows) + "\n")
    credits = ROOT / "CREDITS.md"
    text = credits.read_text()
    text = re.sub(r"\n## Zombie and door sounds\n.*?(?=\n## |\Z)", "", text, flags=re.S).rstrip("\n") + "\n\n" + section
    credits.write_text(text)
    print({k: v for k, v in counts.items()})


if __name__ == "__main__":
    main()
