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
    # Until Josiah picks their own: the same mechanism's pick on the gun that has one (the magnum's empties falling out,
    # the sawn-off's barrels dropping), in place of a pistol slide and a rifle magazine
    "weapon.revolver.eject": ("weapon/revolver/eject", -8.0, [("silenced", s) for s in (
        "bullet_shell_bounce_general_06", "bullet_shell_bounce_metal1_01", "bullet_shell_bounce_general_05",
        "bullet_shell_bounce_general_01", "bullet_shell_bounce_metal1_02", "bullet_shell_bounce_general_02")]),
    "weapon.double_barrel.open": ("weapon/double_barrel/open", -8.0, [("gun", "gun_pistol_general_handling_05")]),
    "weapon.casing": ("weapon/casing", -6.0, [("silenced", "bullet_shell_bounce_concrete2_05"),
                                              ("silenced", "bullet_shell_bounce_concrete1_05"),
                                              ("silenced", "bullet_shell_bounce_concrete2_06")]),
    # Josiah: these replace the old "zipping" hit; heard by everyone near the zombie, fading with distance
    "weapon.bullet.flesh": ("weapon/bullet/flesh", -2.0, [("silenced", "bullet_impact_body_thump_08"),
                                                          ("silenced", "bullet_impact_ice_06"),
                                                          ("silenced", "bullet_impact_body_flesh_04")]),
    # Josiah: "a subtle swinging sound for melee weapons"
    "weapon.melee.swing": ("weapon/melee/swing", -14.0, [("silenced", "whoosh_swish_small_harsh_01")]),
    # Round 16, wave-one guns (Josiah kept the stand-ins for weapon.sawn_off.close and weapon.lever_action.dry)
    "weapon.rimfire.shot": ("weapon/rimfire/shot", -1.0, [("gun", "gun_semi_auto_rifle_shot_06")]),
    "weapon.rimfire.shot_suppressed": ("weapon/rimfire/shot_suppressed", -2.0, [("silenced", "gun_silenced_pistol1_shot_02")]),
    "weapon.rimfire.dry": ("weapon/rimfire/dry", -8.0, [("gun", "gun_semi_auto_rifle_dry_fire_04")]),
    "weapon.rimfire.mag_out": ("weapon/rimfire/mag_out", -8.0, [("gun", "gun_semi_auto_rifle_magazine_unload_02")]),
    "weapon.rimfire.mag_in": ("weapon/rimfire/mag_in", -8.0, [("gun", "gun_semi_auto_rifle_magazine_load_05")]),
    "weapon.rimfire.cock": ("weapon/rimfire/cock", -6.0, [("gun", "gun_rifle_cock_03")]),
    "weapon.smg.shot": ("weapon/smg/shot", -1.0, [("gun", "gun_submachine_auto_shot_05")]),
    "weapon.smg.shot_suppressed": ("weapon/smg/shot_suppressed", -2.0, [("silenced", "gun_semi_auto_shot_silenced1_0_last_tail")]),
    "weapon.smg.dry": ("weapon/smg/dry", -8.0, [("gun", "gun_submachine_auto_dry_fire_01")]),
    "weapon.smg.mag_out": ("weapon/smg/mag_out", -8.0, [("gun", "gun_submachine_auto_magazine_unload_03")]),
    "weapon.smg.mag_in": ("weapon/smg/mag_in", -8.0, [("gun", "gun_submachine_auto_magazine_load_01")]),
    "weapon.smg.cock": ("weapon/smg/cock", -6.0, [("gun", "gun_submachine_auto_cock_03")]),
    "weapon.sawn_off.shot": ("weapon/sawn_off/shot", -0.3, [("gun", "gun_shotgun_shot_04")]),
    "weapon.sawn_off.dry": ("weapon/sawn_off/dry", -8.0, [("gun", "gun_revolver_pistol_dry_fire_03")]),
    "weapon.sawn_off.open": ("weapon/sawn_off/open", -8.0, [("gun", "gun_pistol_general_handling_05")]),
    "weapon.sawn_off.shell_in": ("weapon/sawn_off/shell_in", -8.0, [("gun", "gun_shotgun_load_bullet_08")]),
    "weapon.magnum.shot": ("weapon/magnum/shot", -0.3, [("gun", "gun_revolver_pistol_shot_03")]),
    "weapon.magnum.dry": ("weapon/magnum/dry", -8.0, [("gun", "gun_revolver_pistol_dry_fire_04")]),
    "weapon.magnum.cock": ("weapon/magnum/cock", -8.0, [("gun", "gun_revolver_pistol_cock_03")]),
    "weapon.magnum.open": ("weapon/magnum/open", -8.0, [("gun", "gun_revolver_pistol_cylinder_open_02")]),
    "weapon.magnum.eject": ("weapon/magnum/eject", -8.0, [("silenced", s) for s in (
        "bullet_shell_bounce_general_06", "bullet_shell_bounce_metal1_01", "bullet_shell_bounce_general_05",
        "bullet_shell_bounce_general_01", "bullet_shell_bounce_metal1_02", "bullet_shell_bounce_general_02")]),
    "weapon.magnum.round_in": ("weapon/magnum/round_in", -8.0, [("gun", "gun_pistol_load_bullet_04")]),
    "weapon.magnum.close": ("weapon/magnum/close", -8.0, [("gun", "gun_revolver_pistol_cylinder_close_01")]),
    "weapon.lever_action.shot": ("weapon/lever_action/shot", -0.3, [("gun", "gun_rifle_sniper_shot_04")]),
    "weapon.lever_action.lever": ("weapon/lever_action/lever", -6.0, [("gun", "gun_rifle_cock_01")]),
    "weapon.lever_action.round_in": ("weapon/lever_action/round_in", -8.0, [("gun", "gun_rifle_load_bullet_05")]),
    "weapon.crossbow.shot": ("weapon/crossbow/shot", -3.0, [("silenced", "bow_crossbow_arrow_shoot_type1_04")]),
    "weapon.crossbow.cock": ("weapon/crossbow/cock", -8.0, [("silenced", "bow_crossbow_arrow_draw_stretch1_01")]),
    "weapon.crossbow.hit": ("weapon/crossbow/hit", -2.0, [("silenced", "bullet_impact_body_flesh_04")]),
}
# Heavier or harsher than the take on its own: played lower (pitch < 1) and, for the sawn-off, with a touch of drive
EXTRA = {"weapon.magnum.shot": {"pitch": 0.88}, "weapon.sawn_off.shot": {"pitch": 0.94}}
DRIVE = {"weapon.sawn_off.shot": "acrusher=level_in=1.6:level_out=0.7:bits=10:mix=0.25:mode=log,"}
# Far-off shots made from the near one: muffled and quieter, smeared by close reflections. Every reflection is under
# 60 ms so it blurs into the one report; longer echoes (once 140/330/610 ms) were heard as the shot firing three times
DISTANT = {"weapon.rifle.shot_distant": "weapon.rifle.shot", "weapon.pistol.shot_distant": "weapon.pistol.shot",
           "weapon.hunting_rifle.shot_distant": "weapon.hunting_rifle.shot",
           "weapon.hunting_rifle.shot_suppressed_distant": "weapon.hunting_rifle.shot_suppressed",
           "weapon.revolver.shot_distant": "weapon.revolver.shot", "weapon.double_barrel.shot_distant": "weapon.double_barrel.shot",
           "weapon.rimfire.shot_distant": "weapon.rimfire.shot", "weapon.smg.shot_distant": "weapon.smg.shot",
           "weapon.sawn_off.shot_distant": "weapon.sawn_off.shot", "weapon.magnum.shot_distant": "weapon.magnum.shot",
           "weapon.lever_action.shot_distant": "weapon.lever_action.shot"}
DISTANT_AF = "lowpass=f=1100,aecho=0.8:0.7:17|29|43|58:0.32|0.25|0.18|0.12,apad=pad_dur=0.8"
# The SMG and .22 picks are single shots cut from an automatic string (50-90 ms, no room): each gets its own recording's
# tail, as the pack means them to be played, so a shot rings out instead of ticking (tail level in dB)
TAILS = {"weapon.smg.shot": ("gun", "gun_submachine_auto_shot_00_tail_only_01", 0.0),
         "weapon.rimfire.shot": ("gun", "gun_semi_auto_rifle_shot_00_tail_only_01", -4.0)}

# Josiah: reloads on every gun "need to be a little louder"
RELOADS = ("mag_out", "mag_in", "load_round", "slide", "pump", "shell_in", "bolt", "cock", "open", "round_in", "close", "eject", "lever")
LOUDER = 4.0


def render(files, event, pack, stem, af, dst):
    """One pick to a mono wav through af, with its gun's tail under it if it has one."""
    if event in TAILS:
        tpack, tstem, tdb = TAILS[event]
        ff("-i", str(files[(pack, stem)]), "-i", str(files[(tpack, tstem)]), "-filter_complex",
           f"[0]aformat=channel_layouts=stereo[s];[1]aformat=channel_layouts=stereo,volume={tdb}dB[t];"
           f"[s][t]amix=inputs=2:duration=longest:normalize=0,{af}", "-ac", "1", "-ar", "44100", str(dst))
    else:
        ff("-i", str(files[(pack, stem)]), "-af", af, "-ac", "1", "-ar", "44100", str(dst))


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
            render(files, event, pack, stem, DRIVE.get(event, "") + "anull", tmp)
            level(tmp, out / f"{n}.ogg", peak)
            used.setdefault((pack, stem), set()).add(event)
            if event in TAILS:
                used.setdefault(TAILS[event][:2], set()).add(event)
        ours[event] = {"sounds": [dict({"name": f"zomboidcraft:{folder}/{n}", "attenuation_distance": dist}, **EXTRA.get(event, {}))
                                 for n in range(1, len(picks) + 1)]}
    for event, near in DISTANT.items():
        folder, _, picks = PICKS[near]
        out = RES / "zomboidcraft/sounds" / (folder + "_distant")
        out.mkdir(parents=True, exist_ok=True)
        for f in out.glob("*.ogg"):
            f.unlink()
        pack, stem = picks[0]
        tmp = out / "1.wav"
        render(files, near, pack, stem, DRIVE.get(near, "") + DISTANT_AF, tmp)
        level(tmp, out / "1.ogg", -4.0)
        used.setdefault((pack, stem), set()).add(event)
        old = ours.get(event, {}).get("sounds", [{}])
        dist = old[0].get("attenuation_distance", 128) if isinstance(old[0], dict) else 128
        ours[event] = {"sounds": [dict({"name": f"zomboidcraft:{folder}_distant/1", "attenuation_distance": dist}, **EXTRA.get(near, {}))]}
    ours_path.write_text(json.dumps(ours, indent=2) + "\n")

    credits = ROOT / "CREDITS.md"
    lines = [l for l in credits.read_text().split("\n")
             if not any(l.startswith(f"| {e} |") for e in list(PICKS) + list(DISTANT))]
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
