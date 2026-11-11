---
name: zomboid-cockpit-lights-tech
description: How the jet/heli cockpit lights work (cockpit_glow.py, cockpit_pages.py, .zglow, CockpitGlow.java) and how to re-pick regions
metadata:
  type: project
  modified: 2026-10-26T13:00:37.078Z
---

Josiah 2026-10-26 12:26 asked for button lights and screen light in the F-22 / Su-27 / Apache cockpits, lights only, geometry untouched. Shipped in playtest 2026-10-26 (~13:30).

- tools/cars/cockpit_glow.py renders each seat's view (z-buffer, pitch 30 or 60 deg down, 1400x1000, FOV 110) and MODELS lists rectangles on those views: `keys` (seat, pitch, rect, strength) light legends (relative to the panel's own 99th-percentile brightness) in the tint (NVIS green; Su-27 RED) and small saturated spots as lamps; `screens` (seat, pitch, rect, page, face?) or ('quad', TL, TR, BR, BL, page) put a page quad on the display face.
- Debug views: `--preview DIR` writes night composites per seat/pitch. A scratch rasterizer for picking rects lived at /tmp/claude-0/glow/raster.py + zoom.py (rebuild from cockpit_glow.render if gone).
- tools/cars/cockpit_pages.py: 14 page painters (tsd, adi, engines, stores, cni, icaw, hud_jet, hud_flanker, flight, tsd_heli, weapons, tapes, eufd, flir). Made-up readouts only.
- Output cars/<model>.zglow: zlib then pack.scramble (same key as the .zcar, it holds bought-atlas texels). Groups: 0 keys, then one per screen (kind 1 display, 2 HUD).
- CockpitGlow.java: on while car.isVehicle() and variant usable/abandoned; keys fade 0.8 s, screens flicker on 0.3 s apart, HUD last; eyes / beaconBeam render types like CarLights; cabin light from the dash spot when dark. Drawn at lod <= 1.
- Preview gotcha: legend triangles are copies of whole panel faces, so in a z-buffer preview they hide coplanar screen quads; the game blends both additively (render keys and screens as separate passes).

See [[zomboid-carrier-tech]] for the aircraft models.
