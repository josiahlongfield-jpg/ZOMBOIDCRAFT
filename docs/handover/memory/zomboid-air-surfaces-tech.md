---
name: zomboid-air-surfaces-tech
description: Moving jet control surfaces (surfaces.py + AirSurfaces.java), weathercock flight term, F-22 bezels/ICP and tinted HUD, carrier jet parking rule
metadata:
  type: project
  modified: 2026-10-27T03:52:41.816Z
---

Built 2026-10-27 for Josiah's 03:21 ask (wings/flaps react to flying, F-22 HUD/detail) on branch claude/project-thread-dcbfp2.

- tools/cars/surfaces.py [SRC] writes assets/zomboidcraft/cars/<id>.surfaces.json: body solid-triangle count + per surface kind, side (+1 = +x = left), pivot, unit axis (positive turn = trailing edge down / rudder TE left / LEF leading edge down), runs (start,count) into the opaque body mesh. F-22 parts come from the bought OBJ's object names (Elevator, Flap, Aileron, Rudder, Slat, Engine01 lower / Engine02 upper nozzle flaps) matched by centroid key, so SRC is needed for corvid. Su-27 parts are loose pieces of the decimated zcar inside boxes (stab, flaperon, rudder); it has no separate LEF or airbrake piece. The F-22's surfaces are welded to the wing in the zcar, so box/piece search fails there. Sign test uses the trailing-edge point, not the centroid (F-22 stab centroid sits forward of its spigot).
- AirSurfaces.java: control laws per jet, 75 deg/s actuators, no hydraulics below rpm 0.35. Own jet uses AirControls.command (stick), others use the body rates from qO->q. Applied through CarRenderer's per-triangle Turn loop at lod 0 only; running aircraft use 3x LOD distances so the surfaces show.
- Flight.tickJet: weathercock yaw toward sideslip (1.6*slip*authority), nose eases onto the flight path with the stick released, lateral slip damping 2.0 -> 1.2.
- F-22 "no HUD" was never reproduced offline (it renders in the preview). Fix shipped: HUD drawn blended (entityTranslucentEmissive) on a 30% dark coating so it reads in daylight. CockpitBezels.java draws sharp MFD bezels with OSBs and the ICP keypad (face at z 5.75-5.77 under the HUD), legends lit in keys colour, and covers the glow keys under them.
- Carrier.java: all jets park at yaw 270 (nose to the bow); inTaxiLane keeps tractors out of a 9-block-wide run 45 blocks ahead. Old carriers keep their old jets until regenerated.
Related: [[zomboid-cockpit-live-tech]], [[zomboid-flight-direction]], [[zomboid-carrier-tech]].
