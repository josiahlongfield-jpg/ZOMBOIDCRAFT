"""Builds the Harvestman's rage scream (creature.harvestman.rage) from Josiah's two picks on the Dead Air Harvestman
Rage page (2026-10-30); the game picks one of the two each time it rages ("they can alternate between my two choices").

    python3 tools/audio/build_harvestman_rage.py FREESOUND_DIR

FREESOUND_DIR is a checkout of the sounds-raw branch. Whole recordings, mono, peak -1 dB, silence after them trimmed.
The grab screech (creature.harvestman.screech) is untouched.
"""
import json
import sys
from pathlib import Path

from build_gun3_sounds import write
from build_throw_sounds import load, trim
from build_zombie_sounds import RES, ROOT

PICKS = ["161404", "333832"]   # Alien_Monster_Scream by audione, Demonic Woman Scream by nick121087
RANGE = 96


def main():
    fs_dir = Path(sys.argv[1])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    ours["creature.harvestman.rage"] = write("creature/harvestman/rage", RANGE, [trim(load(fs_dir / fs[i]["file"])) for i in PICKS])
    sj.write_text(json.dumps(ours, indent=2) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Harvestman rage\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| creature.harvestman.rage | [{fs[i]['title']}]({fs[i]['page']}) | {fs[i]['user']} | Freesound, "
                     f"{fs[i].get('license', 'CC0 1.0')} |" for i in PICKS)
    section = ("\n## Harvestman rage\n\nPicked by Josiah Longfield on the Dead Air Harvestman Rage page, built by "
               "tools/audio/build_harvestman_rage.py.\n\n| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
