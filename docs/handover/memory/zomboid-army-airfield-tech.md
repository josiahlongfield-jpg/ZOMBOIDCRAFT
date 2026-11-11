---
name: zomboid-army-airfield-tech
description: Caldwell Army Airfield build (LANDSCAPE 2026-10-30): ArmyField frame/tiles/gates, ArmyFieldSite, ArmyFieldBase layout, mf_ kit incl. smooth barrel-vault pieces, preview
metadata:
  type: project
  modified: 2026-10-30T03:52:27.213Z
---

Built 2026-10-30 (commit 200caa61, playtest 9129335d), first site of [[zomboid-nw-military-plan]].
- Frame ArmyField.java (like Airport): plan spot "airfield" (-1300,-6900), world = (RX+ax, RZ+az). Runway ax -600..599, |az|<=15, overrun 40; taxiway az -82; connectors -585/0/585; ramp ax -400..520, az -218..-92; fence X0 -720..X1 719, LAND_Z0 -380..Z1 89; tiles 240 squares via FrameSite.tiles.
- GATES: Layout breaks plan roads at places, ending at the mid of a tile side. FrameSite.tiles replays that for every plan road and stores the gates in the variant "armyfield@RX,RZ@ax,az,side;..." (seed 12345: south gate ~-120,89; west gate -720,-260). ArmyFieldSite opens the fence there, adds a booth (Camp.booth), a barrier and a gravel spur to the perimeter road. Layout.fixed special-cases "airfield"; LandmarkSite dispatches "armyfield@".
- ArmyFieldBase (piece built once, copied per tile): revetments REV_X 40, 8 bays of 22, back wall az -154, bin walls 4 high; heli pads from ax 270 (rows az -196/-150), heli hub +1.84 z offset at yaw 180; vault hangars at ax -340/-270, 40 wide, az -272..-221, walls 8, rise 12, door 14; shops between; lattice tower at (-160,-262) deck 15 (cp_tw_ pieces). Site now extends FrameSite (see [[zomboid-tank-ranges-tech]]); tiles are uniform 240 squares; ops x -140..-101; fuel bladders, sheds (fire_truck), mast, Hesco, watchtowers, floodMasts, light towers.
- mf_ kit = tools/city/armyfield.py (registered in photo_textures.py): mf_bin/_top/_end, mf_crib, mf_psp, mf_membrane, mf_vault_<a>_<d> (81 panels, ridge along z, a=slope index -90..90 by 22.5, d=depth -8..8 by 2) and mf_vault_end_<a>_<d> (with gable cladding under the panel). ArmyFieldBase.arc(cx, cy, r, xLo, xHi) picks pieces like Round.ring; REUSE it for any curved roof (Camp.quonset is still stepped blocks, could switch).
- Preview: /tmp/claude-0/plan/mf/go.sh NAME RX "RZ[@gates]" W D, mk.sh (draws cars). E.g. west 380 330 300 260 (hangars/tower), east -10 230 530 170 (revetments/helis). Checker /tmp/claude-0/afchk/AfChk.java lists tiles + roads from the real county.
- Sheet: /mnt/project-files/zomboid-mod/army-airfield/airfield-preview.jpg.
