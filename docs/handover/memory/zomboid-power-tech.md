---
name: zomboid-power-tech
description: Harbor Point Generating Station tech (PowerStation/PowerHalls/PowerYards, pw_ kit, preview coords, checks); built 2026-10-28
metadata:
  type: project
---
Harbor Point Generating Station ("Grayhaven Light and Power Company MCMXXVI") on Grayhaven island, plan place "power", lot forced to x 3800 in Layout (3x2 lot, facing 3 = front at world west). Built 2026-10-28 by the LANDSCAPE thread. No ref photos yet: redo when photos land ([[zomboid-photo-reference-rule]]).

- PowerStation.java (extends Grounds): ground/front plaza/gate shack/litter, helpers brick(), ceiling(), shaft(x,z) (stair cells kept out of slab/walkway/ceiling fills), fence().
- PowerHalls.java: 3-storey office (lobby, control room with boards/benchboard/mimic, crew holdout, floors 2-3) + stair at (22,24); turbine hall (3 units, unit 2 opened with rotor on cribbing, 50-ton crane, steam header); boiler house (4 boilers, levels 11/22 on bunk_walkway, tripper floor 34, stair at (119,56)); 4 brick stacks y 48..112 with a ladder on stack 0; machine shop. Office roof ladder -> hall roof; boiler house level-11 door -> shop roof.
- PowerYards.java: coal tower + gallery + belts, coal pile and dozer, two oil tanks in a berm, switchyard (transformers, gantries, breakers, bus), relay house, line towers, perimeter fences.
- Kit tools/city/power.py (pw_, 275 blocks, registered after broadcast in photo_textures.py). add() auto-turns axis models drawn along x (P.along_z + shape). gen_blocks' axis rule counts boxes spanning x: big discs on an axis model must be split in halves (pw_rotor).
- Garage doors need a solid lintel row above (sash planes over a door top make walkcheck pockets).
- Preview: /tmp/claude-0/plan/power/go.sh SEED [m] (SitePreview landmark power 213 133). Mesh: x≈lz, z≈132-lx, y+12. Checks clean on seeds 1-3 (pockets only stack ladder + guard shack furniture, headroom 0, orientcheck 0, palcheck clean).
