"""The outdoor lamp pairs (Josiah 2026-11-06: the landmarks want their light, and coloured lights
their colour): every yard light, flood, lot head, beacon, lantern lens and lit sign that a builder
left dark, out under the sky, is lit by world gen. world/county/RoomLights reads the pairs
(data/zomboidcraft/lamps_out.txt, "off on" a line) and swaps a dark one for its lit twin wherever
no roof stands over it. Lamps under a roof are rooms' and stay as RoomLights leaves them.

Smashed, broken and knocked-down lamps have no lit twin, so they stay as they are. Like the street
lamps these aren't in lamps.txt: the blackout doesn't put them out.

    python3 tools/city/outdoor_lamps.py      rewrites lamps_out.txt from blocks.json
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE.parent.parent / "src/main/resources/data/zomboidcraft/lamps_out.txt"

# the dark names of outdoor lights and lit signs: yard and stadium floods, lot and mast heads,
# beacon lamps, lighthouse lenses, the outside sconces and porch lights, canopies, and the neon and
# box signs of shops, diners, theatres, the boardwalk, the mall and the hospital. Not un_ or
# hs_roof_lamp: those are ceiling slabs, the lamp in their underside.
OUTDOOR = re.compile(
    r"^(cp_flood|cp_flood_hang|cp_flood_bar|cp_lt_head|st_floods|st_lot_head|st_lt_head|"
    r"rd_lens|cm_lens_\w+|gs_deck_light|gs_fascia_\w+|gs_price_\w+|gs_store_\w+|gs_neon_\w+|"
    r"ext_sconce|ext_porch_light|fs_lamp_red|cv_lamp_globe|fit_flood|fit_searchlight|rp_jb_roof_lamp|"
    r"tv_beacon|tv_beacon_top|hw_canopy|hw_booth_lamp|bk_post_clock|sh_tower_top|dn_eave_s|dn_tower_\d|"
    r"dn_neon_\w+|tr_mq_\w+|tr_blade_\d+|sb_neon_\w+|sb_marquee|hs_name_\w+|hs_cross_\d+|hs_h_\d+|"
    r"ml_name_\w+|ml_pylon_\d+|ml_sign_\w+|trade_neon_\w+|front_lampbox)$")


def pairs(blocks):
    lit = {k for k, v in blocks.items() if isinstance(v, dict) and v.get("light")}
    out = []
    for k in sorted(blocks):
        if k in lit:
            continue
        for on in (k + "_on", k + "_lit", re.sub(r"_dead$", "_lit", k)):
            if on != k and on in lit and OUTDOOR.match(k if not k.endswith("_dead") else k[:-5]):
                out.append((k, on))
                break
    return out


def main():
    blocks = json.loads((HERE / "blocks.json").read_text())["blocks"]
    got = pairs(blocks)
    OUT.write_text("# outdoor lamps, dark then lit: written by tools/city/outdoor_lamps.py\n"
                   + "".join(f"{a} {b}\n" for a, b in got))
    print(f"{len(got)} outdoor lamp pairs in {OUT}")


if __name__ == "__main__":
    main()
