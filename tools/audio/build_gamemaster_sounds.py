"""Builds the gun sounds from Josiah's picks on the Dead Air Gamemaster Full picker (round 14).

    python3 tools/audio/build_gamemaster_sounds.py PACK_DIR

PACK_DIR holds Josiah's licensed Gamemaster Audio Gun Sound Pack and Silenced Gun Sounds, unzipped. Those raw
files are licensed to him and never go in this repo; only these finished mono sounds do. Each take is used
whole: mixed to mono so it can sit in the world, and levelled, nothing cut. Replaces the events' old files,
their sounds.json entries (keeping each event's hearing distance) and their rows in CREDITS.md.
"""
import json
import re
import sys
from pathlib import Path

from build_zombie_sounds import RES, ROOT, ff, level

PACKS = {"gun": "Gun Sound Pack", "silenced": "Silenced Gun Sounds"}

# event: (sound folder, peak dB, [(pack, file stem)]); Josiah's picks, 2026-10-26
PICKS = {
    # Josiah: the hunting rifle's pick "can be the assault rifle shot sound" too
    "weapon.rifle.shot": ("weapon/rifle/shot", -0.3, [("gun", "gun_rifle_shot_01")]),
    "weapon.rifle.dry": ("weapon/rifle/dry", -8.0, [("gun", "gun_semi_auto_rifle_dry_fire_03")]),
    "weapon.rifle.mag_out": ("weapon/rifle/mag_out", -6.0, [("gun", "gun_rifle_magazine_unload_04")]),
    "weapon.rifle.mag_in": ("weapon/rifle/mag_in", -6.0, [("gun", "gun_rifle_magazine_load_02")]),
    "weapon.rifle.load_round": ("weapon/rifle/load_round", -8.0, [("gun", "gun_semi_auto_rifle_load_bullet_03")]),
    "weapon.pistol.shot": ("weapon/pistol/shot", -1.0, [("gun", "gun_pistol_shot_03")]),
    "weapon.pistol.shot_suppressed": ("weapon/pistol/shot_suppressed", -2.0, [("silenced", "gun_pistol_shot_silenced_02")]),
    "weapon.pistol.dry": ("weapon/pistol/dry", -8.0, [("gun", "gun_pistol_dry_fire_06")]),
    "weapon.pistol.mag_out": ("weapon/pistol/mag_out", -8.0, [("gun", "gun_pistol_remove_mag_03")]),
    "weapon.pistol.mag_in": ("weapon/pistol/mag_in", -8.0, [("gun", "gun_pistol_insert_mag_05")]),
    "weapon.pistol.slide": ("weapon/pistol/slide", -6.0, [("gun", "gun_pistol_cock_07")]),
    "weapon.shotgun.shot": ("weapon/shotgun/shot", -0.3, [("gun", "gun_shotgun_shot_04")]),
    "weapon.shotgun.dry": ("weapon/shotgun/dry", -8.0, [("gun", "gun_shotgun_dry_fire_02")]),
    "weapon.shotgun.pump": ("weapon/shotgun/pump", -4.0, [("gun", "gun_shotgun_cock_04")]),
    "weapon.shotgun.shell_in": ("weapon/shotgun/shell_in", -8.0, [("gun", "gun_shotgun_load_bullet_08")]),
    "weapon.double_barrel.shot": ("weapon/double_barrel/shot", -0.3, [("gun", "gun_shotgun_shot_02")]),
    "weapon.double_barrel.dry": ("weapon/double_barrel/dry", -8.0, [("gun", "gun_revolver_pistol_dry_fire_02")]),
    "weapon.double_barrel.shell_in": ("weapon/double_barrel/shell_in", -8.0, [("gun", "gun_shotgun_load_bullet_04")]),
    "weapon.hunting_rifle.shot": ("weapon/hunting_rifle/shot", -0.3, [("gun", "gun_rifle_shot_01")]),
    "weapon.hunting_rifle.shot_suppressed": ("weapon/hunting_rifle/shot_suppressed", -2.0,
                                             [("silenced", "gun_silenced_AR_sniper_shot_01")]),
    "weapon.hunting_rifle.dry": ("weapon/hunting_rifle/dry", -8.0, [("gun", "gun_rifle_sniper_dry_fire_01")]),
    "weapon.hunting_rifle.bolt": ("weapon/hunting_rifle/bolt", -6.0, [("gun", "gun_rifle_sniper_cock_03")]),
    "weapon.hunting_rifle.mag_out": ("weapon/hunting_rifle/mag_out", -8.0, [("gun", "gun_rifle_magazine_unload_02")]),
    "weapon.hunting_rifle.mag_in": ("weapon/hunting_rifle/mag_in", -8.0, [("gun", "gun_rifle_magazine_load_01")]),
    "weapon.revolver.shot": ("weapon/revolver/shot", -1.0, [("gun", "gun_revolver_pistol_shot_02")]),
    "weapon.revolver.dry": ("weapon/revolver/dry", -8.0, [("gun", "gun_revolver_pistol_dry_fire_02")]),
    "weapon.revolver.cock": ("weapon/revolver/cock", -8.0, [("gun", "gun_revolver_pistol_cock_01")]),
    "weapon.revolver.open": ("weapon/revolver/open", -8.0, [("gun", "gun_revolver_pistol_cylinder_open_03")]),
    "weapon.revolver.round_in": ("weapon/revolver/round_in", -8.0, [("gun", "gun_revolver_pistol_load_bullet_05")]),
    "weapon.revolver.close": ("weapon/revolver/close", -8.0, [("gun", "gun_revolver_pistol_cylinder_close_01")]),
    "weapon.casing": ("weapon/casing", -6.0, [("silenced", "bullet_shell_bounce_concrete2_05"),
                                              ("silenced", "bullet_shell_bounce_concrete1_05"),
                                              ("silenced", "bullet_shell_bounce_concrete2_06")]),
    # Josiah: these replace the old "zipping" hit; heard by everyone near the zombie, fading with distance
    "weapon.bullet.flesh": ("weapon/bullet/flesh", -2.0, [("silenced", "bullet_impact_body_thump_08"),
                                                          ("silenced", "bullet_impact_ice_06"),
                                                          ("silenced", "bullet_impact_body_flesh_04")]),
    # Josiah: "a subtle swinging sound for melee weapons"
    "weapon.melee.swing": ("weapon/melee/swing", -14.0, [("silenced", "whoosh_swish_small_harsh_01")]),
}

# Josiah: reloads on every gun "need to be a little louder"
RELOADS = ("mag_out", "mag_in", "load_round", "slide", "pump", "shell_in", "bolt", "cock", "open", "round_in", "close")
LOUDER = 4.0


def main():
    root = Path(sys.argv[1])
    files = {}
    for p in root.rglob("*.wav"):
        key = ("silenced" if "Silenced" in str(p) else "gun", p.stem)
        files.setdefault(key, p)
    ours_path = RES / "zomboidcraft/sounds.json"
    ours = json.loads(ours_path.read_text())
    used = {}
    for event, (folder, peak, picks) in PICKS.items():
        if event.endswith(RELOADS):
            peak = min(peak + LOUDER, -1.0)
        old = ours.get(event, {}).get("sounds", [{}])
        dist = old[0].get("attenuation_distance", 16) if isinstance(old[0], dict) else 16
        out = RES / "zomboidcraft/sounds" / folder
        out.mkdir(parents=True, exist_ok=True)
        for f in out.glob("*.ogg"):
            f.unlink()
        for n, (pack, stem) in enumerate(picks, 1):
            tmp = out / f"{n}.wav"
            ff("-i", str(files[(pack, stem)]), "-ac", "1", "-ar", "44100", str(tmp))
            level(tmp, out / f"{n}.ogg", peak)
            used.setdefault((pack, stem), set()).add(event)
        ours[event] = {"sounds": [{"name": f"zomboidcraft:{folder}/{n}", "attenuation_distance": dist}
                                 for n in range(1, len(picks) + 1)]}
    ours_path.write_text(json.dumps(ours, indent=2) + "\n")

    credits = ROOT / "CREDITS.md"
    lines = [l for l in credits.read_text().split("\n")
             if not any(l.startswith(f"| {e} |") for e in PICKS)]
    text = re.sub(r"\n## Gamemaster gun sounds\n.*?(?=\n## |\Z)", "", "\n".join(lines), flags=re.S).rstrip("\n")
    rows = [f"| {', '.join(sorted(ev))} | {stem} ({PACKS[pack]}) | Gamemaster Audio | Licensed by Josiah Longfield |"
            for (pack, stem), ev in sorted(used.items(), key=lambda kv: sorted(kv[1])[0])]
    text += ("\n\n## Gamemaster gun sounds\n\nFrom Gamemaster Audio's Gun Sound Pack and Silenced Gun Sounds "
             "(sold on [Sonniss](https://sonniss.com)), licensed by Josiah Longfield for this game. Each take is used "
             "whole, mixed to mono and levelled by tools/audio/build_gamemaster_sounds.py. The raw library is not "
             "included.\n\n| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + "\n".join(rows) + "\n")
    credits.write_text(text)
    print({e: len(p[2]) for e, p in PICKS.items()})


if __name__ == "__main__":
    main()
