"""Builds the interface sounds from Josiah's picks on the Dead Air UI Picker (round 5).

    python3 tools/audio/build_ui_sounds.py RAW_DIR[,RAW_DIR...]

Same inputs and outputs as build_loot_sounds.py, for the ui.* events (sounds/ui/...). Quiet physical
foley rather than menu bleeps. Josiah is finding his own drop and skill-up sounds, so those wait.
"""
from build_loot_sounds import build

# event: (sound folder, peak dB, attenuation distance, picks); picks Josiah kept, 2026-10-25
PICKS = {
    "ui.click": ("ui/click", -12.0, 8, [("278205", 0.0, 0.19)]),
    # Josiah: tabs use the light switch on/off takes from the button moment
    "ui.tab": ("ui/tab", -12.0, 8, [("257958", 4.06, 0.16), ("440499", 0.0, 0.49)]),
    # buckle clipping, for putting on a fanny pack, vest or bag
    "ui.wear": ("ui/wear", -9.0, 8, [("649853", 0.0, 0.6), ("350461", 1.95, 0.75)]),
    "ui.item_move": ("ui/item_move", -12.0, 8, [("sxcf8695c4", 10.04, 0.27)]),
    "ui.moodle": ("ui/moodle", -8.0, 8, [("22440", 0.0, 0.8)]),
}

if __name__ == "__main__":
    build(PICKS, "Interface sounds", "build_ui_sounds.py")
