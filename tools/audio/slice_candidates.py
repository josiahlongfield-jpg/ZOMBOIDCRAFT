"""Cuts candidate recordings into short clips for the listen-and-pick page.

    python3 tools/audio/slice_candidates.py RAW_DIR[,RAW_DIR...] OUT_DIR [EVENT_PREFIX,...]

Each RAW_DIR holds a manifest.json: the sounds-raw branch (Freesound) and the private Sonniss
folder (fetch_sonniss.py). For each event in CANDIDATES it splits every listed recording
at its silences, keeps the loudest phrases that fit the event's length range, levels them and
writes OUT_DIR/<event>/<id>_<start>.ogg plus OUT_DIR/clips.json (event -> clips with id, start,
length, title, author, page). A picked clip goes straight into pick_sounds.py PINS as
(id, start, length).
"""
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

# event: (label, min s, max s, phrases per take, [Freesound ids])
CANDIDATES = {
    "zombie.idle": ("Idle groans and breathing (heard when they wander)", 0.8, 4.0, 3, [
        "58626", "426628", "235796", "165872", "473525", "414136", "426409", "426229", "394941",
        "653100", "653098", "516766", "147078", "666618", "612699", "788055", "826999", "482836"]),
    "zombie.alert": ("Spotting you: the scream when one sees you", 0.4, 2.5, 3, [
        "435651", "323819", "496080", "737561", "673427", "320740", "278177", "179345", "457591",
        "743299", "336537"]),
    "zombie.chase": ("Chasing: snarls and grunts while they come at you", 0.4, 2.5, 3, [
        "125405", "554936", "133974", "483592", "181068", "204610", "204611", "370036"]),
    "zombie.attack": ("Biting and grabbing", 0.3, 2.0, 3, [
        "518155", "155973", "530995", "445987", "426637", "213509", "448089", "236331", "655707"]),
    "zombie.hurt": ("Getting hit", 0.2, 1.5, 3, [
        "555420", "555426", "115887", "633193", "851777", "533538", "678437", "555413"]),
    "zombie.death": ("Dying: last gurgles and the body hitting the floor", 0.5, 3.5, 3, [
        "555412", "534602", "445986", "830016", "396046", "640217", "163445", "476437", "417994",
        "395331"]),
    "zombie.step": ("Footsteps: slow, dragging, scuffing feet", 0.4, 2.0, 3, []),
    "door.bash": ("Pounding on a wooden door", 0.3, 1.5, 3, [
        "211450", "362633", "256857", "218890", "268500"]),
    "door.bash_metal": ("Pounding on a metal door", 0.3, 1.8, 3, [
        "268126", "188063", "218888", "218889", "144510"]),
    "door.break": ("A door giving way", 0.6, 3.0, 2, [
        "160213", "262538", "520720", "438331", "536777"]),
    "horde.dormant": ("Dormant horde: a low murmur from a street full of them", 6.0, 14.0, 2, [
        "210826", "666317", "239923", "165872", "338059", "25279"]),
    "horde.angry": ("Horde coming for you: it swells as more of them chase", 4.0, 10.0, 2, [
        "516697", "669845"]),
    # Looting and items: candidates come from EXTRA (Freesound loot_*/item_*, Sonniss s_*)
    "loot.rummage": ("Searching shelves, racks and bins", 0.6, 3.0, 2, []),
    "loot.drawer": ("Opening a drawer or filing cabinet", 0.3, 2.0, 2, []),
    "loot.cupboard": ("Opening a cupboard, wardrobe or appliance door", 0.3, 2.0, 2, []),
    "loot.fridge": ("Opening a fridge, cooler or freezer", 0.4, 2.5, 2, []),
    "loot.register": ("Opening a cash register", 0.3, 2.0, 2, []),
    "loot.box": ("Opening a cardboard box or crate", 0.3, 2.5, 2, []),
    "loot.safe": ("Opening a safe", 0.4, 3.0, 2, []),
    "item.eat": ("Eating", 0.4, 3.0, 2, []),
    "item.drink": ("Drinking", 0.4, 3.0, 2, []),
    "item.bandage": ("Bandaging: cloth and tape tearing", 0.3, 2.5, 2, []),
    "item.hammer.nail": ("Hammering nails into a barricade", 0.2, 1.5, 2, []),
    "item.bag": ("Opening a bag or backpack", 0.3, 2.0, 2, []),
}

MAX_CLIPS = 90   # per moment; the page builder trims each round further

# Extra candidates by category in any manifest (Freesound or Sonniss): event -> [(category, title/pack regex)]
EXTRA = {
    "zombie.idle": [("idle", r"zombie|groan|creature|breath|undead|death vocal|shudder")],
    "zombie.alert": [("scream", r"roar|growl|attack|serial|shock|scream"), ("idle", r"aggressive|rage")],
    "zombie.chase": [("idle", r"aggressive|rage|attack|hiss|ragging"), ("scream", r"struggle|grunt")],
    "zombie.attack": [("gore", r"bite|flesh|rip|pry|crunch")],
    "zombie.step": [("step_drag", r"."), ("step", r"drag|stagger|scrape|shuffl|slow|doormat|scuff|tackle")],
    "door.bash": [("crowd", r"kicking wood door"), ("door", r"knock|impact|designed")],
    "door.bash_metal": [("door", r"metal|rusty|ronk|gate")],
    "horde.dormant": [("horde_dormant", r"."), ("crowd", r"murmur|moan|begging|unrest")],
    "horde.angry": [("horde_angry", r"."), ("crowd", r"approach|battle cry|agony scream|fighting|group scream|war unit")],
    "loot.rummage": [("loot_rummage", r"."), ("s_rummage", r".")],
    "loot.drawer": [("loot_drawer", r"."), ("s_drawer", r".")],
    "loot.cupboard": [("s_door_small", r"."), ("loot_appliance", r"door|cupboard|cabinet|wardrobe|oven|washer|dryer|microwave")],
    "loot.fridge": [("loot_fridge", r".")],
    "loot.register": [("loot_register", r".")],
    "loot.box": [("loot_box", r"."), ("s_box", r".")],
    "loot.safe": [("loot_safe", r".")],
    "item.eat": [("item_eat", r"."), ("s_eat", r".")],
    "item.drink": [("item_drink", r"."), ("s_drink", r".")],
    "item.bandage": [("item_bandage", r"."), ("s_tear", r"cloth|fabric|tape|rip|tear|bandage|shirt")],
    "item.hammer.nail": [("item_hammer", r"."), ("s_hammer", r".")],
    "item.bag": [("item_bag", r"."), ("s_zip", r".")],
}


def run(*cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def phrases(path, min_len, max_len):
    """Non-silent stretches of a recording as (start, length), loudest first."""
    total = float(run("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                      str(path)).stdout.strip() or 0)
    log = run("ffmpeg", "-i", str(path), "-af", "silencedetect=noise=-38dB:d=0.25", "-f", "null", "-").stderr
    starts = [float(x) for x in re.findall(r"silence_start: (-?[\d.]+)", log)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", log)]
    spans, t = [], 0.0
    for s, e in zip(starts, ends + [total]):
        if s > t:
            spans.append((t, s - t))
        t = e
    if t < total:
        spans.append((t, total - t))
    out = []
    for s, n in spans:
        while n >= min_len:
            out.append((max(0.0, s - 0.05), min(n + 0.1, max_len)))
            s, n = s + max_len, n - max_len
    scored = []
    for s, n in out[:12]:
        v = run("ffmpeg", "-ss", f"{s}", "-t", f"{n}", "-i", str(path), "-af", "volumedetect", "-f", "null", "-").stderr
        m = re.search(r"mean_volume: (-?[\d.]+)", v)
        scored.append((float(m.group(1)) if m else -99, s, n))
    scored.sort(reverse=True)
    return [(round(s, 2), round(n, 2)) for _, s, n in scored]


def main():
    out = Path(sys.argv[2])
    manifest, base = {}, {}
    for d in sys.argv[1].split(","):
        d = Path(d)
        for sid, info in json.loads((d / "manifest.json").read_text()).items():
            manifest[sid], base[sid] = info, d
    clips = {}
    only = tuple(sys.argv[3].split(",")) if len(sys.argv) > 3 else ("",)
    for event, (label, lo, hi, per, ids) in CANDIDATES.items():
        if not event.startswith(only):
            continue
        ids = list(ids)
        listed = set(ids)
        for cat, rx in EXTRA.get(event, []):
            ids += [sid for sid, i in manifest.items() if i["category"] == cat and sid not in ids
                    and re.search(rx, (i["title"] + " " + i.get("pack", "")).lower())]
        (out / event).mkdir(parents=True, exist_ok=True)
        rows = []
        for sid in ids:
            info = manifest.get(sid)
            if not info:
                print("missing", sid)
                continue
            src = base[sid] / info["file"]
            user = unquote(info["user"])
            if len(rows) >= MAX_CLIPS:
                break
            for start, length in phrases(src, lo, hi)[:per if sid in listed else min(per, 2)]:
                name = f"{sid}_{start:.2f}.ogg"
                fade = min(0.12, length / 4)
                tmp = out / event / "tmp.wav"
                run("ffmpeg", "-v", "error", "-y", "-ss", f"{start}", "-t", f"{length}", "-i", str(src), "-ac", "1",
                    "-ar", "44100", "-af", f"afade=t=in:d=0.02,afade=t=out:st={length - fade}:d={fade}", str(tmp))
                peak = re.search(r"max_volume: (-?[\d.]+)", run("ffmpeg", "-i", str(tmp), "-af", "volumedetect",
                                                               "-f", "null", "-").stderr)
                gain = -1.5 - float(peak.group(1)) if peak else 0
                run("ffmpeg", "-v", "error", "-y", "-i", str(tmp), "-af", f"volume={gain:.2f}dB", "-c:a", "libvorbis",
                    "-q:a", "4", str(out / event / name))
                tmp.unlink(missing_ok=True)
                rows.append({"file": f"{event}/{name}", "id": sid, "start": start, "length": length,
                             "title": info["title"].removesuffix(f" by {user}"), "user": user, "page": info["page"],
                             "source": info.get("bundle", "Freesound")})
        clips[event] = {"label": label, "clips": rows[:MAX_CLIPS]}
        print(event, len(rows), "clips")
    (out / "clips.json").write_text(json.dumps(clips, indent=1))


if __name__ == "__main__":
    main()
