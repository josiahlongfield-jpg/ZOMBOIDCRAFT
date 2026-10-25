"""Builds the world ambience from Josiah's picks on the Dead Air Ambience Picker (round 6).

    python3 tools/audio/build_ambience_sounds.py RAW_DIR[,RAW_DIR...]

Same inputs and outputs as build_loot_sounds.py, for the amb.* events (sounds/amb/...). Beds (rain, wind,
night insects and the indoor ones) are cut long, kept stereo and crossfaded end into start so they loop
without a seam; they play around the listener, not from a point. One-shots (thunder, creaks, crows, distant
city sounds) are mono so they can be placed in the world and muffled through walls.
"""
import subprocess

import numpy as np

from build_loot_sounds import build

RATE = 44100
LOOP = 24.0    # seconds per bed
FADE = 2.0     # crossfade from the end back into the start

# event: (sound folder, peak dB, attenuation distance, picks); picks Josiah kept, 2026-10-25
PICKS = {
    "amb.rain": ("amb/rain", -14.0, 16, [("344994", 71.95, 12), ("sx896e0956", 11.95, 12)]),
    "amb.roof": ("amb/roof", -15.0, 16, [("sxaeb7083a", 35.95, 12), ("sxaeb7083a", 47.95, 12),
                                         ("sxd6bce60f", 0.0, 12), ("sx84cb4a64", 0.0, 11.1)]),
    # Josiah: rain on a window, for rooms where the rain is being blown against the glass
    "amb.window": ("amb/window", -15.0, 16, [("669486", 11.77, 10)]),
    "amb.drip": ("amb/drip", -18.0, 12, [("653415", 9.95, 10)]),
    "amb.wind": ("amb/wind", -12.0, 16, [("sxca96f59a", 0.0, 12), ("sx4c951936", 119.95, 12),
                                         ("sxca96f59a", 47.95, 12), ("sx59e37871", 11.95, 12)]),
    "amb.night": ("amb/night", -18.0, 16, [("165526", 11.95, 12), ("353173", 17.04, 12), ("sx86eb2437", 47.95, 12)]),
    "amb.thunder": ("amb/thunder", -1.0, 160, [("sx606208ce", 0.75, 9), ("sx48c0e356", 0.0, 9), ("sx217ebc71", 0.33, 9),
                                               ("sx43ceea26", 0.0, 9), ("sx4344357d", 0.33, 9), ("sx9950ec6c", 37.87, 9),
                                               ("sxd77280eb", 0.6, 9), ("sx4344357d", 9.33, 9)]),
    "amb.creak": ("amb/creak", -10.0, 16, [("502505", 0.0, 4), ("sxe63ba7a0", 0.79, 4), ("sxfe8368b4", 0.0, 4),
                                           ("326460", 1.34, 4), ("334143", 18.42, 4)]),
    "amb.crow": ("amb/crow", -10.0, 48, [("328846", 0.0, 4), ("sx66e42b03", 1.99, 4), ("sx559db0e0", 10.21, 4),
                                         ("214869", 47.0, 4)]),
    "amb.distant": ("amb/distant", -6.0, 96, [("sxa6475097", 0.0, 3), ("sx682cc507", 0.47, 6), ("sx682cc507", 16.32, 4.1)]),
}
BEDS = {"amb.rain", "amb.roof", "amb.window", "amb.drip", "amb.wind", "amb.night"}


def decode(path, start, seconds):
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-ss", str(start), "-t", str(seconds), "-i", str(path), "-ac", "2",
                          "-ar", str(RATE), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(pcm, dtype=np.float32).reshape(-1, 2)


def loop_bed(event, raw, manifest, pick, dst):
    """A seamless stereo loop: as long as the recording allows (up to LOOP), its tail crossfaded into its head."""
    if event not in BEDS:
        return False
    sid, start, _ = pick
    audio = decode(raw[sid] / manifest[sid]["file"], start, LOOP + FADE)
    fade = int(FADE * RATE)
    body = len(audio) - fade
    out = audio[:body].copy()
    ramp = np.linspace(0.0, 1.0, fade, dtype=np.float32)[:, None]
    # equal-power crossfade, so the join doesn't dip
    out[:fade] = audio[:fade] * np.sqrt(ramp) + audio[body:body + fade] * np.sqrt(1 - ramp)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "2", "-i", "-", str(dst)],
                   input=out.tobytes(), check=True)
    return True


if __name__ == "__main__":
    build(PICKS, "World ambience", "build_ambience_sounds.py", loop_bed)
