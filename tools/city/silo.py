"""The silo shelter's kit (sl_), from refs/farm/silos: the farming family's shelter under an old silo.

- sl_padlock: the clue, a new brass padlock on a new galvanised hasp, screwed to the steel door
  they cut into the foot of the old drum. Its plate stands a pixel proud of its cell's back, so it
  sits on the face of the closed door in the next block.

    python3 tools/city/silo.py   # writes the blocks into blocks.json (textures: existing ext_galv, cm_brass)
"""
import json
from pathlib import Path

HERE = Path(__file__).parent


def blocks():
    out = {}
    out["sl_padlock"] = {
        "model": "boxes", "title": "Padlock", "sound": "METAL", "rotate": "facing", "decor": True,
        "boxes": [
            [5.5, 6, 15.6, 10.5, 12, 16.6, "ext_galv"],      # the hasp's plate on the door
            [7, 6.5, 14.8, 9, 8, 15.6, "ext_galv"],          # the staple through it
            [6.9, 6.2, 14.4, 7.6, 8.4, 15.0, "ext_galv"],    # the shackle's legs
            [8.4, 6.2, 14.4, 9.1, 8.4, 15.0, "ext_galv"],
            [6.9, 8.4, 14.4, 9.1, 9.0, 15.0, "ext_galv"],
            [6.3, 2.6, 14.0, 9.7, 6.2, 15.2, "cm_brass"],   # the padlock's body, brass, still bright
        ],
    }
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    kept = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("sl_") or k in made}
    kept.update({k: v for k, v in made.items() if k not in kept})
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} silo blocks in {path}")


if __name__ == "__main__":
    main()
