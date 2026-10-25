"""Picks the weapon sounds from the Freesound candidates and installs them in the mod.

    python3 tools/audio/pick_sounds.py RAW_DIR [--list] [--bank DIR]

RAW_DIR is the sounds-raw branch (fetch_freesound.py's output: <category>/<id>.ogg plus
manifest.json). For each sound event it ranks the candidates by how well the title and tags
match, how popular the recording is and whether its length suits the event, then trims the
silence, mixes to mono (Minecraft only positions mono sounds), evens out the level and writes
assets/zomboidcraft/sounds/<event>/<n>.ogg, sounds.json and CREDITS.md. --list only prints the
ranking. PINS below overrides the ranking for an event with chosen Freesound ids. --bank also
copies the finished sounds and their credits to DIR (bank.json) for the preview page.
"""
import json
import math
import re
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/zomboidcraft"

# event: (categories, title must match, title must not match, variants, max seconds, peak dB, extra sounds.json fields)
EVENTS = {
    "weapon.rifle.shot": (["rifle_shot"], r"rifle|ar.?15|m4|m16|ak|gun ?shot|shot|556|5\.56|fire", r"shotgun|pistol|9mm|silenc|suppress|cap gun|toy|laser|reload|cock|empty|dry|burst|auto|machine",
                          3, 2.2, -1.0, {"attenuation_distance": 128}),
    # someone else's shot, heard from down the street
    "weapon.rifle.shot_distant": (["rifle_shot", "rifle_distant"], r"distant|far|echo|shot", r"shotgun|pistol|toy|reload",
                                  2, 3.0, -1.0, {"attenuation_distance": 128}),
    "weapon.rifle.dry": (["dry_fire"], r"dry|empty|click|trigger", r"mouse|keyboard|pen|switch", 2, 0.5, -3.0, {}),
    "weapon.rifle.mag_out": (["rifle_mag"], r"out|release|eject|remov|drop", r"in$|insert", 2, 1.0, -3.0, {}),
    "weapon.rifle.mag_in": (["rifle_mag"], r"insert|in\b|load|seat|magazine", r"eject|release|out", 2, 1.0, -3.0, {}),
    "weapon.rifle.bolt": (["rifle_bolt"], r"charg|bolt|cock|rack|slide|pull", r"door|lock|bike", 2, 1.2, -3.0, {}),
    "weapon.rifle.load_round": (["load_round", "dry_fire", "casing"], r"click|bullet|round|cartridge|casing|shell", r"", 3, 0.35, -6.0, {}),
    "weapon.casing": (["casing"], r"casing|shell|brass|cartridge", r"sea|beach|egg", 3, 1.2, -6.0, {}),
    "weapon.bullet.flesh": (["flesh_impact", "bullet_hit"], r"flesh|body|impact|hit|squish|meat", r"ricochet|metal|wood|glass", 3, 0.8, -2.0, {}),
    "weapon.bullet.ricochet": (["bullet_hit"], r"ricochet|whiz|whizz|impact", r"flesh|body", 2, 1.5, -3.0, {"attenuation_distance": 32}),
    "weapon.melee.swing": (["swing"], r"swing|whoosh|swoosh|swish", r"door|golf club hit|wind", 3, 0.7, -4.0, {}),
    "weapon.melee.swing_heavy": (["swing"], r"swing|whoosh|swoosh|heavy|bat", r"door|wind", 2, 0.9, -3.0, {"pitch": 0.8}),
    "weapon.melee.hit_blunt": (["hit_blunt"], r"bat|punch|hit|smash|body|thud|melon|whack", r"bell|metal|glass", 3, 0.8, -1.5, {}),
    "weapon.melee.hit_blade": (["hit_blade"], r"stab|slash|chop|cut|flesh|knife|machete|sword", r"draw|unsheath|sharpen", 3, 0.8, -1.5, {}),
    "weapon.melee.hit_pan": (["hit_metal"], r"pan|metal|pipe|clang|bang|crowbar", r"door|car", 2, 1.2, -2.0, {}),
    "weapon.melee.stomp": (["stomp", "flesh_impact", "hit_blunt"], r"crush|splat|squish|smash|stomp|gore|skull|melon", r"", 2, 1.0, -1.5, {}),
    "weapon.melee.shove": (["shove", "hit_blunt"], r"push|shove|body|fall|thud|punch|hit", r"door", 2, 0.8, -3.0, {}),
}

# Vanilla stand-ins, used for any event with no candidate (and alongside the grab).
FALLBACK = {
    "weapon.rifle.shot": [{"name": "minecraft:entity.generic.explode", "type": "event", "pitch": 1.9, "volume": 0.7}],
    "weapon.rifle.shot_distant": [{"name": "minecraft:entity.generic.explode", "type": "event", "pitch": 1.6, "volume": 0.5}],
    "weapon.rifle.dry": [{"name": "minecraft:block.lever.click", "type": "event", "pitch": 1.6, "volume": 0.6}],
    "weapon.rifle.mag_out": [{"name": "minecraft:item.crossbow.loading_end", "type": "event", "pitch": 1.4}],
    "weapon.rifle.mag_in": [{"name": "minecraft:item.crossbow.loading_middle", "type": "event", "pitch": 1.2}],
    "weapon.rifle.bolt": [{"name": "minecraft:item.crossbow.loading_end", "type": "event", "pitch": 0.9}],
    "weapon.rifle.load_round": [{"name": "minecraft:block.chain.place", "type": "event", "pitch": 1.8, "volume": 0.5}],
    "weapon.casing": [{"name": "minecraft:block.chain.fall", "type": "event", "pitch": 1.9, "volume": 0.4}],
    "weapon.bullet.flesh": [{"name": "minecraft:entity.player.hurt", "type": "event", "pitch": 0.7}],
    "weapon.bullet.ricochet": [{"name": "minecraft:block.stone.hit", "type": "event", "pitch": 1.6}],
    "weapon.melee.swing": [{"name": "minecraft:entity.player.attack.sweep", "type": "event", "volume": 0.5}],
    "weapon.melee.swing_heavy": [{"name": "minecraft:entity.player.attack.sweep", "type": "event", "pitch": 0.7, "volume": 0.6}],
    "weapon.melee.hit_blunt": [{"name": "minecraft:entity.player.attack.strong", "type": "event"}],
    "weapon.melee.hit_blade": [{"name": "minecraft:entity.player.attack.crit", "type": "event"}],
    "weapon.melee.hit_pan": [{"name": "minecraft:block.anvil.land", "type": "event", "pitch": 1.8, "volume": 0.4}],
    "weapon.melee.stomp": [{"name": "minecraft:block.slime_block.fall", "type": "event", "pitch": 0.6}],
    "weapon.melee.shove": [{"name": "minecraft:entity.player.attack.knockback", "type": "event"}],
}

# Hand-picked Freesound ids per event, once the ranking has been checked. An id can be given as
# (id, start seconds, length) to cut one action out of a longer take, such as a full reload.
# Cut points come from the transients in each take (loudness over 10 ms windows).
PINS = {
    # Josiah: take 3 for both the shooter's own shot and the shot heard from far off, a bit louder
    "weapon.rifle.shot": [("421710", 0.0, 2.2)],
    "weapon.rifle.shot_distant": ["421710"],
    "weapon.rifle.dry": [("232867", 4.45, 0.4), ("232867", 7.5, 0.4)],
    # an M16 reloaded from empty, and a second rifle reload, each cut into its three actions
    "weapon.rifle.mag_out": [("725397", 0.15, 0.9), ("815879", 0.0, 0.5)],
    "weapon.rifle.mag_in": [("725397", 2.05, 1.0), ("815879", 0.5, 0.75)],
    "weapon.rifle.bolt": [("725397", 4.05, 1.2), ("815879", 1.3, 0.6)],
    "weapon.rifle.load_round": ["855580", ("431830", 6.48, 0.3), "94122"],
    "weapon.casing": [("205126", 0.4, 1.2), ("205126", 2.85, 1.2), ("210102", 4.05, 1.2)],
    "weapon.bullet.flesh": [("423301", 0.12, 0.6), ("423301", 1.15, 0.6), "511194"],
    "weapon.bullet.ricochet": [("148840", 0.0, 0.45), "392975"],
    "weapon.melee.swing": ["766542", "352719", "367182"],
    "weapon.melee.swing_heavy": [("784302", 0.0, 0.85), ("784302", 0.6, 0.9), ("201216", 5.6, 0.9)],
    "weapon.melee.hit_blunt": ["276600", ("365574", 4.92, 0.6), ("365574", 0.8, 0.7)],
    "weapon.melee.hit_blade": ["395373", "411743", ("635049", 6.6, 0.55)],
    "weapon.melee.hit_pan": ["401706", "529567"],
    "weapon.melee.stomp": [("739191", 4.45, 0.8), "703115"],
    "weapon.melee.shove": ["582294", ("573691", 0.25, 0.7)],
}


# Extra loudness in dB for an event, pushed through a limiter so the peak stays put.
BOOST = {"weapon.rifle.shot": 3.0, "weapon.rifle.shot_distant": 3.0}


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True).stdout.strip()
    try:
        return float(out)
    except ValueError:
        return 0.0


def score(sid, info, cats, want, avoid, max_len, raw):
    if info["category"] not in cats:
        return None
    text = (info["title"] + " " + " ".join(info.get("tags", []))).lower()
    if avoid and re.search(avoid, info["title"].lower()):
        return None
    hits = len(re.findall(want, text)) if want else 0
    if want and not hits:
        return None
    length = duration(raw / info["file"])
    if length <= 0.05:
        return None
    downloads = int(re.sub(r"\D", "", info.get("downloads") or "0") or 0)
    s = 2.0 * min(hits, 3) + math.log10(downloads + 10)
    if length > max_len * 3:
        s -= 3     # a long take: usable, but trimmed hard
    if cats.index(info["category"]) > 0:
        s -= 1.5
    return s, length


def process(src, dst, max_len, peak, start=0.0, length=None, boost=0.0):
    """Trim leading silence, mono, cut to length with a fade, then set the peak level."""
    max_len = min(max_len, length or max_len)
    fade = min(0.15, max_len / 4)
    chain = (f"atrim=start={start},asetpts=PTS-STARTPTS,"
             f"silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.01,"
             f"atrim=0:{max_len},afade=t=out:st={max(0.0, max_len - fade)}:d={fade}")
    tmp = dst.with_suffix(".tmp.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-ac", "1", "-ar", "44100", "-af", chain, str(tmp)],
                   check=True)
    vol = subprocess.run(["ffmpeg", "-i", str(tmp), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    m = re.search(r"max_volume: (-?[\d.]+) dB", vol)
    gain = peak - float(m.group(1)) if m else 0.0
    chain = f"volume={gain:.2f}dB"
    if boost:
        chain += f",volume={boost:.1f}dB,alimiter=limit={10 ** (peak / 20):.3f}:attack=1:release=60:level=disabled"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp), "-af", chain,
                    "-c:a", "libvorbis", "-q:a", "5", str(dst)], check=True)
    tmp.unlink()


def main():
    raw = Path(sys.argv[1])
    list_only = "--list" in sys.argv
    manifest = json.loads((raw / "manifest.json").read_text())
    sounds_json = {}
    credits = []
    bank = {}
    used = set()
    for event, (cats, want, avoid, variants, max_len, peak, extra) in EVENTS.items():
        ranked = []
        for sid, info in manifest.items():
            r = score(sid, info, cats, want, avoid, max_len, raw)
            if r:
                ranked.append((r[0], sid, r[1]))
        ranked.sort(reverse=True)
        cuts = {}
        if event in PINS:
            pins = [p + (0.0, None)[len(p) - 1:] if isinstance(p, tuple) else (p, 0.0, None) for p in PINS[event]]
            cuts = {f"{sid}@{t}": (t, n) for sid, t, n in pins}
            ranked = [(99, f"{sid}@{t}", duration(raw / manifest[sid]["file"])) for sid, t, n in pins]
        print(f"\n{event}: {len(ranked)} candidates")
        for s, key, length in ranked[:8]:
            print(f"  {s:5.2f}  {key:>8}  {length:5.2f}s  {manifest[key.split('@')[0]]['title'][:60]}")
        if list_only:
            continue
        # don't reuse a recording across events unless there's nothing else
        if event in PINS:
            picks = ranked
        else:
            picks = [r for r in ranked if r[1] not in used][:variants] or ranked[:variants]
        folder = ASSETS / "sounds" / event.replace(".", "/")
        if folder.exists():
            for old in folder.glob("*.ogg"):
                old.unlink()
        entries = []
        for n, (s, key, length) in enumerate(picks, 1):
            sid = key.split("@")[0]
            folder.mkdir(parents=True, exist_ok=True)
            process(raw / manifest[sid]["file"], folder / f"{n}.ogg", max_len, peak, *cuts.get(key, (0.0, None)),
                    boost=BOOST.get(event, 0.0))
            entries.append({"name": f"zomboidcraft:{event.replace('.', '/')}/{n}", **extra})
            used.update({key, sid})
            info = manifest[sid]
            user = unquote(info["user"])
            title = info["title"].removesuffix(f" by {user}")
            row = f"| {event} | [{title}]({info['page']}) | {user} | CC0 1.0 |"
            if row not in credits:
                credits.append(row)
            bank.setdefault(event, {"sounds": [], "pitch": extra.get("pitch", 1)})["sounds"].append(
                {"file": f"sounds/{event.replace('.', '/')}/{n}.ogg", "title": title, "user": user, "page": info["page"]})
        sounds_json[event] = {"sounds": entries or FALLBACK[event]}
    if list_only:
        return
    sounds_json["zombie.grab"] = {"sounds": [
        {"name": "minecraft:entity.zombie.ambient", "type": "event", "pitch": 0.8},
        {"name": "minecraft:entity.zombie.attack_wooden_door", "type": "event", "pitch": 1.4, "volume": 0.5}]}
    # keep other tools' events (zombies, doors) and credit sections
    old = json.loads((ASSETS / "sounds.json").read_text()) if (ASSETS / "sounds.json").exists() else {}
    sounds_json = {**{k: v for k, v in old.items() if not k.startswith("weapon.")}, **sounds_json}
    (ASSETS / "sounds.json").write_text(json.dumps(sounds_json, indent=2) + "\n")
    old_credits = (ROOT / "CREDITS.md").read_text() if (ROOT / "CREDITS.md").exists() else ""
    rest = old_credits[old_credits.index("\n## "):] if "\n## " in old_credits else ""
    (ROOT / "CREDITS.md").write_text(
        "# Credits\n\nWeapon sounds are CC0 (public domain) recordings from [Freesound](https://freesound.org), "
        "trimmed, mixed to mono and levelled by tools/audio/pick_sounds.py. Thank you to the recordists.\n\n"
        "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + "\n".join(credits) + "\n" + rest)
    if "--bank" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--bank") + 1])
        for event in bank:
            dst = out / "sounds" / event.replace(".", "/")
            dst.mkdir(parents=True, exist_ok=True)
            for f in (ASSETS / "sounds" / event.replace(".", "/")).glob("*.ogg"):
                shutil.copy(f, dst / f.name)
        (out / "sounds" / "bank.json").write_text(json.dumps(bank, indent=1))
    print(f"\n{len(sounds_json)} sound events written")


if __name__ == "__main__":
    main()
