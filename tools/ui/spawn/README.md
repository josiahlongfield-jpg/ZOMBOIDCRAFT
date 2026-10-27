# Spawn picker map

`textures/gui/spawn/county.png` is the county drawn from the plan page. Redraw it when the plan changes:

    node tools/county/plan_dump.js PLAN.html /tmp/plan.json
    python3 tools/ui/spawn/county_map.py /tmp/plan.json /tmp/county.svg
    node tools/ui/spawn/render.js /tmp/county.svg src/main/resources/assets/zomboidcraft/textures/gui/spawn/county.png

The picker puts its pins at (x + 8192) / 16384 of the image, so the image must cover -8192..8192 on both axes.
