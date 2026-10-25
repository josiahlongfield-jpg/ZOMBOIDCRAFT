"""Builds the M9 pistol and 870 shotgun sounds from Josiah's picks on the Dead Air Pistol & Shotgun Picker (round 10).

    python3 tools/audio/build_gun2_sounds.py RAW_DIR[,RAW_DIR...]

Same inputs as build_zombie_sounds.py. Replaces the rifle stand-ins the weapons thread pointed these events
at with their own files under assets/zomboidcraft/sounds/weapon/pistol/ and weapon/shotgun/. The suppressed
pistol takes (pick_sounds.py) are left alone.
"""
import json
import subprocess

from build_loot_sounds import build
from build_zombie_sounds import RES

# event: (sound folder, peak dB, attenuation distance, picks); Josiah's picks, 2026-10-25
PICKS = {
    # no separate far-off pistol was picked, so everyone else hears the same shot, like the rifle
    "weapon.pistol.shot": ("weapon/pistol/shot", -3.0, 96, [("427592", 0.0, 1.34)]),
    "weapon.pistol.shot_distant": ("weapon/pistol/shot_distant", -3.0, 96, [("427592", 0.0, 1.34)]),
    "weapon.pistol.dry": ("weapon/pistol/dry", -8.0, 8, [("674568", 0.0, 0.41)]),
    "weapon.pistol.mag_out": ("weapon/pistol/mag_out", -8.0, 8, [("171209", 0.72, 0.72)]),
    "weapon.pistol.mag_in": ("weapon/pistol/mag_in", -8.0, 8, [("456195", 7.69, 0.76)]),
    "weapon.pistol.slide": ("weapon/pistol/slide", -6.0, 8, [("396331", 0.0, 0.63)]),
    "weapon.shotgun.shot": ("weapon/shotgun/shot", -0.3, 128, [("773873", 0.0, 0.8)]),
    # Josiah: the Pole Position SPAS-12 at 20 m for far off, with its echo
    "weapon.shotgun.shot_distant": ("weapon/shotgun/shot_distant", -0.3, 160, [("sx98739b60", 39.89, 2.2)]),
    "weapon.shotgun.dry": ("weapon/shotgun/dry", -8.0, 8, [("674568", 0.0, 0.41)]),
    "weapon.shotgun.pump": ("weapon/shotgun/pump", -4.0, 16, [("755495", 9.08, 0.55)]),
    "weapon.shotgun.shell_in": ("weapon/shotgun/shell_in", -8.0, 8, [("108793", 0.0, 0.7)]),
}

if __name__ == "__main__":
    build(PICKS, "Pistol and shotgun sounds", "build_gun2_sounds.py")
    # the shotgun's dry click is the pistol's, lower: a heavier hammer on an empty chamber
    path = RES / "zomboidcraft/sounds.json"
    ours = json.loads(path.read_text())
    for sound in ours["weapon.shotgun.dry"]["sounds"]:
        sound["pitch"] = 0.8
    path.write_text(json.dumps(ours, indent=2) + "\n")
    # the far-off shotgun is mostly echo: push it 8 dB into a limiter so it carries like the rifle's
    far = RES / "zomboidcraft/sounds/weapon/shotgun/shot_distant/1.ogg"
    tmp = far.with_suffix(".tmp.ogg")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(far), "-af",
                    "volume=8dB,alimiter=limit=0.97:attack=0.5:release=40:level=disabled", "-c:a", "libvorbis",
                    "-q:a", "6", str(tmp)], check=True)
    tmp.replace(far)
