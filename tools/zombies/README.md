# New zombie bodies

The game draws exactly what the character preview (kits.html) shows. These scripts turn the
preview into game data:

1. `export.js` runs inside the preview page (headless Chromium, see `run.js`) and writes, for each
   outfit, `out/<outfit>.json` (bones, body quads with skin UVs, kit quads with atlas UVs, stump and
   small-detail flags) and `out/<outfit>.png` (the worn kit atlas).
2. `pack.py` writes `assets/zomboidcraft/zbody/<outfit>.bin` (read by `ZBodyMesh`), the kit atlas,
   the 1024 px per-type skin and the eye mask under `textures/entity/zbody/`.
2b. `cull.py` then strips clothing faces hidden inside the body or another closed box on the same bone (about 7%; never body, stump or cut faces). Run it after every pack.
2c. `bake_low.py` bakes the far-off body: `zbody/<outfit>_low.bin` (body boxes with their own atlas `textures/entity/zbody/low/<outfit>.png`, the skin tint and the clothes painted on by rays from outside, plus clothing at least 2 wide or hanging off the body) — about 280 quads against 2,100. Run it after cull.py.
3. `check.py` re-renders an export flat as a quick look.

The preview's own sources (kits.js, move.js, the patch scripts) live in the project files under
`zomboid-mod/characters/preview-src/`. Movements are hand-ported to `ZMove.java`; keep the two in step.
