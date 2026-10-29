"""Collects candidate sounds (weapons, zombies) from Freesound: CC0 recordings only, as their HQ previews.

    python3 tools/audio/fetch_freesound.py OUT_DIR [CATEGORY,CATEGORY...]

Runs in CI (the build machine can reach Freesound). For each query it takes the most downloaded
CC0 results, checks the licence on the sound's own page, and saves OUT_DIR/<category>/<id>.ogg
plus OUT_DIR/manifest.json (title, author, page, licence, tags, downloads) for picking and credits.
"""
import json
import re
import socket
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

QUERIES = {
    "rifle_shot": ["assault rifle shot", "ar15 gunshot", "m4 rifle shot", "rifle gunshot", "ak47 shot", "556 rifle"],
    "rifle_distant": ["distant gunshot", "gunshot echo", "rifle shot far"],
    "rifle_mag": ["rifle magazine insert", "ar15 magazine", "magazine insert gun", "magazine release", "gun mag out"],
    "rifle_bolt": ["charging handle", "rifle bolt", "gun cock", "rifle slide rack", "bolt release"],
    "dry_fire": ["dry fire", "gun empty click", "trigger click"],
    "casing": ["bullet casing", "shell casing drop", "brass casing"],
    "bullet_hit": ["bullet impact flesh", "bullet hit body", "bullet impact", "ricochet"],
    "swing": ["swing whoosh", "bat swing", "swoosh stick", "whoosh weapon"],
    "hit_blunt": ["baseball bat hit", "punch body", "body hit", "melon smash", "bat hit flesh", "wood hit body"],
    "hit_blade": ["knife stab", "stab flesh", "sword slash flesh", "machete chop", "flesh cut"],
    "hit_metal": ["metal pipe hit", "frying pan hit", "crowbar hit", "metal bang hit"],
    "stomp": ["skull crush", "gore splat", "stomp squish", "head smash"],
    "shove": ["body push", "shove grunt", "body fall"],
    "break": ["wood break", "stick snap", "bat break", "metal snap"],
    "equip": ["gun foley", "weapon equip", "gun handling", "rifle sling"],
    "load_round": ["bullet into magazine", "loading magazine rounds", "cartridge insert", "loading bullets"],
    # Josiah: in-game music should be ambient noise, drones and textures, not composed songs
    "amb_music_drone": ["dark drone", "drone ambient dark", "low drone", "ominous drone", "horror drone", "deep drone rumble",
                        "evolving drone", "cinematic drone"],
    "amb_music_dark": ["dark ambient", "dark ambience texture", "eerie ambience", "creepy atmosphere", "horror ambience",
                       "unsettling atmosphere", "apocalyptic ambience", "desolate ambience"],
    "amb_music_texture": ["ambient texture", "granular texture", "soundscape dark", "drone texture metal", "bowed metal drone",
                          "reverse ambient pad", "dissonant pad", "industrial drone"],
    "amb_music_tension": ["tension drone", "suspense ambience", "tension rising drone", "heartbeat drone", "pulsing drone",
                          "tension bed", "horror tension", "dread atmosphere"],
    "zombie_idle": ["zombie groan", "zombie moan", "undead groan", "zombie breathing", "creature groan low"],
    "zombie_alert": ["zombie scream", "zombie roar", "zombie growl aggressive", "monster screech human"],
    "zombie_chase": ["zombie snarl", "zombie running breath", "zombie growl", "rabid growl human"],
    "zombie_attack": ["zombie bite", "zombie attack", "flesh bite", "zombie eating"],
    "zombie_hurt": ["zombie hurt", "zombie pain", "monster hurt grunt"],
    "zombie_death": ["zombie death", "zombie dying", "death gurgle", "body collapse"],
    "zombie_step": ["shuffling footsteps", "dragging feet", "footsteps shuffle concrete", "limping footsteps"],
    "door_bash": ["door banging", "pounding on door", "door knock heavy fist", "wooden door hit"],
    "door_bash_metal": ["metal door banging", "pounding metal door", "steel door hit"],
    "door_break": ["door break", "wood door smash", "door kicked in", "wood splinter crash"],
    "human_dread": ["death rattle", "choking gurgle", "raspy breathing", "wet breathing", "man moaning in pain", "dying breath", "throat gurgle"],
    "human_scream": ["man scream horror", "screaming in agony", "woman shriek horror", "feral scream"],
    "step_drag": ["foot drag", "dragging foot", "limping walk", "slow footsteps scuff", "shoe scuff", "dragging leg", "staggering footsteps", "zombie footsteps"],
    "horde_dormant": ["crowd murmur distant", "zombie ambience", "groaning crowd", "moaning crowd distant", "undead ambience"],
    "horde_angry": ["angry mob", "crowd screaming", "zombie horde attack", "rioting crowd", "zombies screaming", "monster crowd roar"],
    "loot_rummage": ["rummaging through items", "searching shelf objects", "rummage box", "rummaging drawer"],
    "loot_drawer": ["filing cabinet drawer", "metal drawer open", "desk drawer open", "cabinet drawer slide"],
    "loot_fridge": ["fridge door open", "refrigerator open", "freezer door open", "cooler door"],
    "loot_register": ["cash register drawer", "cash register open", "till drawer"],
    "loot_box": ["cardboard box open", "wooden crate open", "box flaps"],
    "loot_appliance": ["washing machine door", "oven door open", "dryer door", "microwave door"],
    "loot_safe": ["safe door open", "heavy safe door", "vault door creak"],
    "item_eat": ["eating crunch", "chewing food", "eating chips", "biting apple"],
    "item_drink": ["drinking gulp", "soda can open", "bottle cap open", "water bottle drink"],
    "item_bandage": ["bandage wrap", "cloth tear", "tape rip", "fabric rip"],
    "item_hammer": ["hammer nail wood", "hammering nails", "nail into wood"],
    "item_bag": ["backpack zipper", "bag zip", "picking up items foley"],
    # UI: physical, quiet foley rather than menu bleeps
    "ui_click": ["light switch click", "small plastic click", "pen click", "latch click"],
    "ui_cloth": ["cloth rustle short", "jacket rustle", "backpack rustle", "fabric movement foley"],
    "ui_buckle": ["buckle clip", "velcro strap", "backpack strap"],
    "ui_item": ["pick up object foley", "put down object table", "small item handling"],
    "ui_drop": ["drop item floor", "bag drop floor", "object drop wood floor"],
    "ui_sting": ["heartbeat single", "low tension hit", "horror stinger dark"],
    "ui_page": ["page turn", "pencil writing", "notebook paper"],
    # World ambience: weather beds, roof rain heard indoors, and sparse one-shots for an abandoned city
    "amb_rain": ["rain on street city", "steady rain loop", "heavy rain downpour"],
    "amb_roof": ["rain on roof interior", "rain on window inside", "rain on tin roof"],
    "amb_wind": ["wind gust howling", "wind through buildings", "strong wind loop"],
    "amb_thunder": ["thunder close crack", "thunder distant rumble", "thunder roll"],
    "amb_hail": ["hail storm", "hail on roof", "hail on car"],
    "amb_distant": ["car alarm distant", "distant dog barking night", "distant scream", "distant gunshots"],
    "amb_creak": ["house creaking", "wooden floor creak", "building creak metal"],
    "amb_drip": ["water dripping interior", "leaking roof drip", "pipe drip"],
    "amb_crow": ["crow caw", "crows calling distant", "ravens"],
    "amb_night": ["crickets night", "night ambience insects", "owl hoot"],
    # second ambience round, from Josiah's notes: heavier hail, more far-off animals, car and house alarms
    "amb_hail2": ["heavy hail on roof", "hailstorm intense", "hail on metal roof", "large hail"],
    # the weather's storm-tripped alarms: a looping car alarm and an electric burglar bell
    # bullet strikes by surface (Josiah: the old hit sounded cartoonish); WEAPONS plays one per shot
    "imp_ground": ["bullet impact dirt", "bullet hit ground", "bullet impact sand", "bullet impact grass", "bullet hitting dirt",
                   "bullet impact gravel"],
    "imp_concrete": ["bullet impact concrete", "bullet hit concrete", "bullet impact asphalt", "bullet hitting wall concrete",
                     "bullet impact stone", "bullet impact rock", "concrete bullet hole", "ricochet concrete"],
    "imp_brick": ["bullet impact brick", "bullet hit wall", "bullet impact plaster", "bullet impact tile", "bullet hits drywall",
                  "bullet impacts", "gunshot impact wall", "bullet impact"],
    "imp_wood": ["bullet impact wood", "bullet hit wood", "bullet hitting wooden door", "bullet impact tree", "bullet wood splinter"],
    "imp_metal": ["bullet impact metal", "bullet hit metal", "bullet hitting steel", "bullet impact sheet metal", "bullet ping metal"],
    "imp_car": ["bullet hitting car", "bullet impact car door", "bullet hits vehicle", "bullet impact car body", "gunshot car metal",
                "bullet impact metal hollow", "bullet hit car", "car shot"],
    "imp_glass": ["bullet impact glass", "bullet through glass", "bullet hit window", "glass bullet hole", "window shot"],
    "imp_soft": ["bullet impact fabric", "bullet hit cushion", "bullet impact upholstery", "bullet hit cloth", "bullet impact sandbag",
                 "bullet hit pillow", "bullet impact soft", "bullet hit mattress"],
    # flyable jets and the Apache-style helicopter (the aircraft thread's events)
    "air_jet_start": ["jet engine start", "jet engine startup", "turbine spool up", "apu start aircraft", "jet engine spool up",
                      "jet engine shutdown", "turbine spool down"],
    "air_jet_idle": ["jet engine idle", "turbine whine", "jet idle loop", "fighter jet idle", "jet engine running"],
    "air_jet_roar": ["jet engine roar", "fighter jet engine", "jet engine loop", "jet thrust", "jet engine full power"],
    "air_jet_afterburner": ["afterburner", "fighter jet afterburner", "jet takeoff afterburner", "military jet takeoff"],
    "air_jet_distant": ["jet flyover", "fighter jet flyby", "jet passing overhead", "distant jet rumble", "military jet flyby"],
    "air_heli_rotor": ["helicopter rotor", "helicopter loop", "helicopter blades", "helicopter hovering", "helicopter interior"],
    "air_heli_start": ["helicopter start up", "helicopter startup", "helicopter engine start", "helicopter shut down",
                       "helicopter landing", "helicopter take off"],
    "air_heli_distant": ["helicopter flyby", "helicopter distant", "helicopter overhead", "military helicopter flyover"],
    "air_wind": ["cockpit wind", "airflow wind loop", "wind rushing loop", "aircraft cabin wind", "wind noise speed"],
    "air_warning": ["stall warning", "aircraft warning", "cockpit alarm", "master caution", "cockpit warning beep",
                    "aircraft alarm horn"],
    "air_ground": ["tire screech landing", "tyre chirp", "plane landing touchdown", "aircraft catapult", "steam catapult",
                   "arresting cable", "aircraft carrier launch", "aircraft canopy", "hydraulic actuator motor"],
    "air_crash": ["plane crash", "aircraft crash explosion", "huge explosion", "large explosion debris", "helicopter crash"],
    "amb_alarm_car": ["car alarm", "car alarm loop", "car alarm siren", "car alarm whoop", "car alarm going off",
                      "vehicle alarm", "car alarm distant", "car alarm street"],
    "amb_alarm_bell": ["burglar alarm bell", "alarm bell ringing", "electric bell ringing", "fire alarm bell",
                       "school bell ringing", "shop alarm bell", "security alarm bell", "bell alarm loop"],
    "amb_alarm": ["car alarm", "house alarm ringing", "burglar alarm bell", "alarm bell ringing outside"],
    "amb_animal": ["dog howling distant", "coyote howl", "fox scream night", "dogs barking in the distance"],
    # guns, round 2: the M9 pistol and the 870 pump shotgun
    "gun_pistol": ["9mm pistol shot", "handgun gunshot", "pistol gunshot outdoor", "beretta pistol shot"],
    "gun_pistol_foley": ["pistol slide rack", "pistol magazine insert", "handgun reload", "pistol dry fire click"],
    "gun_shotgun": ["shotgun blast", "12 gauge shotgun shot", "pump shotgun firing", "shotgun gunshot outdoor"],
    "gun_shotgun_foley": ["shotgun pump rack", "shotgun shell loading", "pump action shotgun reload", "shotgun dry fire"],
    "gun_distant": ["distant gunshot", "gunshot far away", "shotgun distant", "gunfire echo distance"],
    # guns, round 3: the Remington 700-style bolt-action hunting rifle
    "gun_bolt": ["hunting rifle shot", "30-06 rifle shot", "308 rifle gunshot", "bolt action rifle shot", "deer rifle shot"],
    "gun_bolt_foley": ["bolt action rifle cycling", "rifle bolt open close", "bolt action reload", "rifle magazine insert"],
    # guns, round 4: double-barrel 12 gauge side-by-side and a .38 Special revolver
    "gun_dbl": ["double barrel shotgun shot", "side by side shotgun", "12 gauge shotgun blast", "coach gun shot",
                "double barrel shotgun fire", "sawed off shotgun shot"],
    "gun_dbl_foley": ["double barrel shotgun open", "break action shotgun", "shotgun breech open close",
                      "shotgun shell insert", "double barrel reload", "break open shotgun eject"],
    "gun_revolver": ["revolver shot", "38 special revolver", "revolver gunshot", "357 magnum shot", "revolver fire"],
    "gun_revolver_foley": ["revolver cock hammer", "revolver cylinder open", "revolver cylinder spin", "revolver reload",
                           "revolver dry fire", "revolver eject shells", "revolver cylinder close", "bullet drop into cylinder"],
    "gun_sup_sniper": ["suppressed sniper rifle", "silenced sniper shot", "suppressed rifle gunshot", "silencer rifle shot",
                       "suppressed 308", "subsonic rifle suppressed", "silenced rifle", "suppressed 50 cal",
                       "suppressed barrett", "suppressed 338 lapua", "heavy suppressed rifle", "suppressor big rifle"],
    "gun_bolt_distant": ["distant rifle shot", "rifle shot echo valley", "hunting gunshot far away"],
    # cars: old American saloons and trucks, worn rather than sporty
    "car_start": ["car engine start", "old car starting", "truck engine start", "car ignition start"],
    "car_idle": ["car engine idle loop", "old car idling", "v8 engine idle", "truck idle"],
    "car_horn": ["car horn", "old car horn honk", "truck horn"],
    "car_crash": ["car crash", "car collision impact", "metal crash impact car", "car hitting object"],
    "car_door": ["car door open close", "car door slam", "old car door"],
    "car_stall": ["car engine failing to start", "car won't start", "engine stall sputter"],
    "horde": ["zombie horde", "zombie crowd", "zombies distant", "crowd of zombies moaning"],
    "flesh_impact": ["flesh impact", "body punch hit", "meat slap", "gore impact"],
    # throwables (WEAPONS, Josiah approved 2026-10-27): weapon.throw.* events
    "thr_throw": ["throw whoosh", "object throw", "throwing whoosh", "arm throw swish", "toss object"],
    "thr_bottle": ["bottle smash", "glass bottle break", "beer bottle smash", "bottle shatter", "bottle break ground"],
    "thr_can": ["can clatter", "tin can drop", "empty can bounce", "can kick", "metal can rolling"],
    "thr_brick": ["brick drop", "brick thud", "rock thrown impact", "stone drop ground", "brick hit concrete"],
    "thr_firecracker": ["firecracker", "firecrackers string", "firework banger", "cherry bomb", "firecracker pop"],
    "thr_fuse": ["fuse burning", "lit fuse", "fuse sizzle", "dynamite fuse", "sparkler burning"],
    "thr_flare": ["road flare", "flare burning", "flare ignite", "magnesium burn", "emergency flare", "flare strike"],
    "thr_alarm": ["alarm clock ticking", "clock tick", "alarm clock ring", "bell alarm clock", "mechanical alarm clock"],
    "thr_molotov": ["molotov", "molotov cocktail", "fire whoosh ignite", "petrol ignite", "gasoline fire burst", "fireball whoosh"],
    "thr_grenade": ["grenade pin", "pin pull", "grenade spoon", "smoke grenade", "smoke canister hiss", "flashbang", "stun grenade"],
    "thr_ring": ["tinnitus", "ear ringing", "high pitch ringing", "ringing ears explosion"],
    # round 2: Josiah found none of the first pin pulls or ear ringings fitted
    "thr_pin": ["grenade pin pull", "pulling pin", "cotter pin", "metal pin removal", "split ring", "key ring metal",
                "safety pin metal click", "grenade arm", "grenade spoon release", "metal clip pull", "lever release spring",
                "metal ring pull", "weapon foley metal click", "military gear click"],
    "thr_ring2": ["tinnitus effect", "tinnitus high", "shell shock", "shellshock", "after explosion ringing",
                  "deaf ringing", "concussion ringing", "flashbang ringing", "ears ringing", "sine whine high",
                  "high frequency whine", "head ringing", "bomb aftermath", "dazed muffled"],
    "thr_blast": ["grenade explosion", "explosion close", "explosion", "big explosion", "distant explosion", "explosion far",
                  "explosion rumble", "explosion debris"],
    "thr_fire": ["fire burning", "fire crackle", "burning fire loop", "flames burning", "gasoline fire", "bonfire crackle"],
}
PER_QUERY = 6
# a stalled download once hung a whole runner until the job timed out; no single read may wait longer than this
socket.setdefaulttimeout(30)
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) ZomboidCraft-sound-collector"}


BUDGET = 20 * 60   # seconds; stop and save what we have
START = time.time()


def over_budget():
    return time.time() - START > BUDGET


def get(url, binary=False):
    for attempt in range(2):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
                data = r.read()
                return data if binary else data.decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            print("  retry", url, e)
            time.sleep(3 + attempt * 4)
    return None


# Real bullet strikes are scarce under CC0, so these groups also take Attribution (CC-BY) sounds, credited in CREDITS.md
ATTRIBUTION_OK = ("imp_",)


def licence_filter(cat):
    if cat.startswith(ATTRIBUTION_OK):
        return 'license:("Creative Commons 0" OR "Attribution")'
    return 'license:"Creative Commons 0"'


def search(q, cat=""):
    f = licence_filter(cat)
    params = [
        {"q": q, "f": f, "s": "Downloads (most first)"},
        {"q": q, "f": f, "s": "num_downloads desc"},
        {"q": q, "f": f},
    ]
    for p in params:
        html = get("https://freesound.org/search/?" + urllib.parse.urlencode(p))
        if not html:
            continue
        ids = []
        for m in re.finditer(r'href="/people/([^/"]+)/sounds/(\d+)/"', html):
            if (m.group(1), m.group(2)) not in ids:
                ids.append((m.group(1), m.group(2)))
        if ids:
            return ids, html
    return [], html or ""


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    only = sys.argv[2].split(",") if len(sys.argv) > 2 else list(QUERIES)
    manifest = {}
    dumped = False
    home = get("https://freesound.org/")
    print("freesound home:", "ok" if home else "unreachable", len(home or ""))
    for cat in only:
        queries = QUERIES[cat]
        (out / cat).mkdir(exist_ok=True)
        for q in queries:
            if over_budget():
                print("time budget used up")
                break
            ids, html = search(q, cat)
            print(f"{cat} / {q}: {len(ids)} results")
            if not ids and not dumped:
                (out / "search_debug.html").write_text(html[:200000])
                dumped = True
            taken = 0
            for user, sid in ids:
                if taken >= (10 if cat.startswith(ATTRIBUTION_OK) else PER_QUERY):
                    break
                if sid in manifest:
                    continue
                page_url = f"https://freesound.org/people/{user}/sounds/{sid}/"
                page = get(page_url)
                if not page:
                    continue
                cc0 = re.search(r"publicdomain/zero|Creative Commons 0", page)
                by = re.search(r"creativecommons\.org/licenses/by/(\d\.\d)", page)
                if not cc0 and not (by and cat.startswith(ATTRIBUTION_OK)):
                    print("  skip (licence)", sid)
                    continue
                licence = "CC0 1.0" if cc0 else f"CC-BY {by.group(1)}"
                m = re.search(r'https://cdn\.freesound\.org/previews/\d+/\d+_\d+-hq\.ogg', page) \
                    or re.search(r'https://cdn\.freesound\.org/previews/\d+/\d+_\d+-hq\.mp3', page)
                if not m:
                    print("  no preview", sid)
                    continue
                data = get(m.group(0), binary=True)
                if not data:
                    continue
                ext = m.group(0).rsplit(".", 1)[1]
                (out / cat / f"{sid}.{ext}").write_bytes(data)
                title = re.search(r'<meta property="og:title" content="([^"]*)"', page)
                desc = re.search(r'<meta property="og:description" content="([^"]*)"', page)
                tags = re.findall(r'href="/browse/tags/([^/"]+)/"', page)
                dl = re.search(r'([\d,]+)\s*downloads', page)
                manifest[sid] = {"category": cat, "query": q, "file": f"{cat}/{sid}.{ext}", "user": user,
                                 "title": title.group(1) if title else "", "description": (desc.group(1) if desc else "")[:300],
                                 "tags": sorted(set(tags))[:20], "downloads": dl.group(1) if dl else "",
                                 "page": page_url, "license": licence}
                taken += 1
                (out / "manifest.json").write_text(json.dumps(manifest, indent=1))
                time.sleep(0.5)
            time.sleep(1)
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print(len(manifest), "sounds")


if __name__ == "__main__":
    main()
