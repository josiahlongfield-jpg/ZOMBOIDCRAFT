"""Builds the bolt-action hunting rifle sounds from Josiah's picks on the Dead Air Hunting Rifle Picker (round 11).

    python3 tools/audio/build_hunting_rifle_sounds.py RAW_DIR[,RAW_DIR...]

Same inputs as build_zombie_sounds.py. Replaces the stand-ins the weapons thread pointed the
weapon.hunting_rifle.* events at with their own files under assets/zomboidcraft/sounds/weapon/hunting_rifle/.
"""
import subprocess

from build_loot_sounds import build
from build_zombie_sounds import RES

# event: (sound folder, peak dB, attenuation distance, picks); Josiah's picks, 2026-10-25
PICKS = {
    "weapon.hunting_rifle.shot": ("weapon/hunting_rifle/shot", -0.3, 128, [("431834", 0.49, 1.57)]),
    # the weapons thread registers the far-off shot with a fixed range of 176
    "weapon.hunting_rifle.shot_distant": ("weapon/hunting_rifle/shot_distant", -0.3, 176, [("541164", 0.0, 2.46)]),
    "weapon.hunting_rifle.dry": ("weapon/hunting_rifle/dry", -8.0, 8, [("674568", 0.0, 0.41)]),
    "weapon.hunting_rifle.mag_out": ("weapon/hunting_rifle/mag_out", -8.0, 8, [("725403", 0.39, 0.88)]),
    "weapon.hunting_rifle.mag_in": ("weapon/hunting_rifle/mag_in", -8.0, 8, [("725397", 1.81, 1.2)]),
    # Josiah's M1903A3 take started on near-silence; trimmed so the lift lands the moment the event fires
    "weapon.hunting_rifle.bolt": ("weapon/hunting_rifle/bolt", -6.0, 16, [("sx376250fd", 0.40, 0.85)]),
}

if __name__ == "__main__":
    build(PICKS, "Hunting rifle sounds", "build_hunting_rifle_sounds.py")
    # a .308 should hit harder than the AR: push the close shot 4 dB into a limiter
    shot = RES / "zomboidcraft/sounds/weapon/hunting_rifle/shot/1.ogg"
    tmp = shot.with_suffix(".tmp.ogg")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(shot), "-af",
                    "volume=4dB,alimiter=limit=0.97:attack=0.5:release=60:level=disabled", "-c:a", "libvorbis",
                    "-q:a", "6", str(tmp)], check=True)
    tmp.replace(shot)
