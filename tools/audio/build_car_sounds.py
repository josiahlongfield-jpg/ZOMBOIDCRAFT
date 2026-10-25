"""Builds the car sounds from Josiah's picks on the Dead Air Car Picker (round 8).

    python3 tools/audio/build_car_sounds.py RAW_DIR[,RAW_DIR...]

Same inputs as build_zombie_sounds.py. Writes assets/zomboidcraft/sounds/car/..., the car.* entries in
assets/zomboidcraft/sounds.json and the vehicle section of CREDITS.md. The idle is a seamless MONO loop, so
it can sit on the car in the world and be pitched up with speed. No stall recording turned up, so the stall
is the cranking from the start take, cut off before the engine catches.
"""
import subprocess

import numpy as np

from build_loot_sounds import build

RATE = 44100
FADE = 1.5   # seconds of the idle's tail crossfaded into its head

# event: (sound folder, peak dB, attenuation distance, picks); Josiah's picks, 2026-10-25
PICKS = {
    "car.start": ("car/start", -3.0, 32, [("50898", 0.0, 4.91)]),
    "car.stall": ("car/stall", -3.0, 32, [("50898", 0.05, 1.25)]),
    "car.idle": ("car/idle", -6.0, 32, [("sxe48535c9", 0.0, 10.0)]),
    "car.horn": ("car/horn", -1.0, 96, [("546528", 0.0, 2.5)]),
    "car.crash": ("car/crash", -1.0, 48, [("151624", 0.0, 0.67)]),
    # Josiah: NachtmahrTV's take for opening, nmscher's for shutting
    "car.door.open": ("car/door_open", -6.0, 16, [("556690", 0.0, 2.0)]),
    "car.door.close": ("car/door_close", -6.0, 16, [("86232", 5.21, 0.43)]),
}


def loop_idle(event, raw, manifest, pick, dst):
    """The idle as a seamless mono loop: its tail crossfaded into its head."""
    if event != "car.idle":
        return False
    sid, start, length = pick
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-ss", str(start), "-t", str(length), "-i",
                          str(raw[sid] / manifest[sid]["file"]), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    audio = np.frombuffer(pcm, dtype=np.float32)
    fade = int(FADE * RATE)
    body = len(audio) - fade
    out = audio[:body].copy()
    ramp = np.linspace(0.0, 1.0, fade, dtype=np.float32)
    out[:fade] = audio[:fade] * np.sqrt(ramp) + audio[body:body + fade] * np.sqrt(1 - ramp)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-", str(dst)],
                   input=out.tobytes(), check=True)
    return True


if __name__ == "__main__":
    build(PICKS, "Vehicle sounds", "build_car_sounds.py", loop_idle)
