---
name: zomboid-irons-tech
description: Flip-up iron sights placement (Josiah 2026-10-30): clear of everything, hide lists under optics, crossbow has none, mesh cleanups
metadata:
  type: project
---

Josiah 2026-10-30: irons must sit on the rail clear (front and back) of anything else, no collisions; side-on approved for assault, precision, hunting rifle and heavy sniper. "The crossbow doesn't need the iron sights" (crossbow guns.json "irons": null, out of Attachments.FLIP_IRONS).

- guns.json irons.flip: front/rear [x,y,z] (y = rail tooth top - ~0.13, like the optics), plus "hide": optics that sit where the folded irons lie; kit.py swaps the folded irons for EMPTY when one is fitted (gun.irons_hidden). aperture y = rear y + 0.917 (leaf sits higher on its base).
- att_irons_*_up meshes include their base; logos painted out via pack spec patches; rear_up shifted x -0.04 to line up with the folded base. Positions hold across every colour (same geometry).
- Mesh cleanups: pbr_assault_rifle lost its own built-in folded sights (the "block in front of the rear sight"); heavy_sniper_mesh lost floating scope bits; own_long_scope repacked with its eyecup and front cap (keep z 6.8-17.4, then z +0.377).
- Checking: ray-cast highest surface under each sight foot (vertex profiles mislead on long faces), part intrusion, optics vs folded sights, sightline. GunLookTest irons() shots irons-{gun}-{fp-hip,ads}.
- weapons.py regen ~10 min; revert rounding-only *_magazine.json after. Don't `pkill -f` with the pattern in your own command line (kills the shell).
See [[zomboid-attachment-colours-tech]], [[zomboid-mesh-attach-tech]].
