"""Builds the flyable jets' and helicopter's sounds (aircraft.*), from Josiah's picks on the Dead Air Aircraft page
(2026-10-27) and, where he said "just put the others in for now", the closest fits from the same page.

    python3 tools/audio/build_aircraft_sounds.py FREESOUND_DIR SONNISS_DIR

FREESOUND_DIR is a checkout of the sounds-raw branch; SONNISS_DIR holds the Sonniss downloads (manifest.json + air_*/),
which stay out of this repo. Everything is mono so the game can place it in 3D. Loops are made seamless by crossfading
their own end into their start (build_music.seamless), so the flight code can hold them for as long as it likes.
How they play (AirSound, the flight thread): jet.start runs once and hands over to jet.idle at 50 s (idle is the same
recording's 40-50 s, so the join is the engine carrying on); after taxiing or flying with the engine still on it drops
straight back to idle. jet.distant and heli.distant are what people on the ground hear as one goes over; the flight
code moves the loop with the aircraft and bends its pitch for the Doppler shift.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_music import seamless
from build_zombie_sounds import RES, ROOT

RATE = 44100
OUT = RES / "zomboidcraft/sounds/aircraft"
# file under sounds/aircraft: (source, start s, end s or None, loop crossfade s (0: one-shot), peak dB, filter or None)
# source is "fs:<id>" (Freesound) or "sx:<id>" (Sonniss)
PIECES = {
    # Josiah's picks
    "jet/start": ("fs:322178", 0.0, 50.0, 0.0, -1.0, None),         # F16 start up: the whole spool-up to idle
    "jet/idle": ("fs:322178", 38.0, 50.0, 2.0, -1.0, None),         # its 40-50 s, looped (heard in the cockpit)
    "jet/roar": ("fs:187734", 0.0, None, 2.0, -1.0, None),          # Idle F-22, outside the jet, louder up close
    "jet/distant": ("fs:829068", 0.8, 5.5, 1.2, -1.0, None),        # close afterburner scream, heard from the ground
    "heli/distant": ("sx:d397a2b4", 3.0, 14.0, 2.0, -1.0, None),    # Chinook close flyby, its nearest seconds
    # stand-ins chosen from the same page until he picks
    "jet/shutdown": ("fs:322178", 52.0, None, 0.0, -1.0, None),     # the end of his start-up take: the engine winding down
    "jet/afterburner": ("fs:104883", 8.0, 32.0, 2.0, -1.0, None),   # StoneyJ afterburner, the steady burn
    "heli/start": ("fs:479510", 0.0, 20.0, 0.0, -1.0, None),        # helicopter starting: turbine light-off, rotor winds up
    "heli/rotor": ("sx:0819e504", 38.0, 70.0, 2.0, -1.0, None),     # Black Hawk UH-60M at idle, steady blades
    "heli/turbine": ("sx:0819e504", 38.0, 70.0, 2.0, -3.0, "highpass=f=1800,highpass=f=1800"),  # the same, whine only
    "heli/shutdown": ("sx:0819e504", 128.0, 160.0, 0.0, -1.0, None),  # Black Hawk rotors running down
    "wind": ("fs:852822", 20.0, 80.0, 4.0, -3.0, None),             # howling wind, rising with speed in game
    "warn/stall": ("sx:063ca1fd", 12.0, 20.0, 0.5, -3.0, None),     # Beechcraft Baron stall warning horn
    "warn/caution": ("fs:845147", 0.0, None, 0.0, -3.0, None),      # cockpit chime
    "touchdown": ("sx:f9c353b4", 0.7, 2.6, 0.0, -1.0, None),        # tyres biting the tarmac
    "catapult": ("fs:438632", 20.0, 31.0, 0.0, -1.0, None),         # steam catapult, one full stroke
    "hook": ("sx:6ea366d3", 0.0, 6.0, 0.0, -1.0, None),             # Black Hawk brake ratchet as the wire bites (stand-in)
    "crash": ("fs:483292", 0.5, 13.0, 0.0, -1.0, None),             # large explosion with falling debris
    "canopy": ("fs:479492", 0.0, 7.0, 0.0, -2.0, None),             # jet fighter canopy closing
}


def load(path, filt):
    cmd = ["ffmpeg", "-v", "error", "-i", str(path)] + (["-af", filt] if filt else []) + [
        "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 1).copy()


def main():
    fs_dir, sx_dir = Path(sys.argv[1]), Path(sys.argv[2])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sx = json.loads((sx_dir / "manifest.json").read_text())
    credits = {}
    for name, (src, start, end, fade, peak, filt) in PIECES.items():
        kind, sid = src.split(":")
        if kind == "fs":
            info, path = fs[sid], fs_dir / fs[sid]["file"]
            rec = f"[{info['title']}]({info['page']})"
            credits.setdefault(rec, (info["user"], "Freesound, " + info.get("license", "CC0 1.0"), []))[2].append(name)
        else:
            info, path = sx["sx" + sid], sx_dir / sx["sx" + sid]["file"]
            rec = f"{info['title']} ({info['pack']})"
            credits.setdefault(rec, (info["bundle"], "Sonniss royalty-free", []))[2].append(name)
        a = load(path, filt)
        a = a[int(start * RATE):int(end * RATE) if end else len(a)]
        if fade:
            a = seamless(a, fade)
        else:
            n = int(0.01 * RATE)  # no clicks where a one-shot was cut out of a longer take
            a[:n] *= np.linspace(0, 1, n, dtype=np.float32)[:, None]
            a[-n * 5:] *= np.linspace(1, 0, n * 5, dtype=np.float32)[:, None]
        a *= 10 ** (peak / 20) / max(np.abs(a).max(), 1e-6)
        dst = OUT / f"{name}.ogg"
        dst.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", "5", str(dst)], input=a.tobytes(), check=True)
        print(name, round(len(a) / RATE, 2), "s", "loop" if fade else "")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Aircraft sounds\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {', '.join('aircraft/' + n for n in names)} | {rec} | {by} | {lic} |"
                     for rec, (by, lic, names) in credits.items())
    section = ("\n## Aircraft sounds\n\nJets and helicopter, picked by Josiah Longfield on the Dead Air Aircraft page, cut and "
               "looped by tools/audio/build_aircraft_sounds.py. The Sonniss originals are not included.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
