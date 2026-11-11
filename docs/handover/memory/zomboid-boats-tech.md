---
name: zomboid-boats-tech
description: Boats are .zcar vehicles (kind "boat") made procedurally by tools/cars/boats.py; how they float, are placed, searched and previewed
metadata:
  type: project
  modified: 2026-10-29T03:42:53.372Z
---

Since 2026-10-29 (LANDSCAPE, commit b4084608) boats are real 3D models, not block hulls (Boats.java deleted). Done after Josiah's consistent-detail rule ([[zomboid-consistent-detail-rule]]).
- tools/cars/boats.py: no bought pack. Hull lofted from station sections (Hull class: control-point splines for plan/sheer/keel; kinds v, round, flat), decks()/zones for cockpits, fittings kit (blob, tube, lathe, fin, windshield, canopy, outboard, sterndrive, rail, cleat, seat, trunk). Atlas 1024: hull charts per side (64 px/m, y 3 m down to keel), decal strip (transom name, dash, portlights, cowl, companionway), 33 material tiles (periodic 2 m at 32 px/m). States usable/abandoned/wrecked (lists 4.5 deg, glass gone, canvas shredded, holes)/burnt (burnt down to the waterline; skiff's alloy hull doesn't burn).
- Models: boat_bowrider, boat_console, boat_cruiser, boat_sloop, boat_skiff (lengths 7.1/7.7/10.6/9.7/5.1 m). `python3 tools/cars/boats.py [--only id] [--preview DIR]` rewrites .zcar + models.json rows. pack.py keeps kind jet and boat rows.
- Model space: y = 0 is the keel, waterline WL = 0.89. Place with Placer.vehicle(x, SEA (top water block), z, yaw, "=boat_x@state"); sunk = SEA-1/-2 with @wrecked. Yaw 0 = bow south (+z).
- Java: CarModels.Model.boat(); street picks exclude boats; hull blocks replace water only in row 0; CarProp.use searches "Lockers" (default trunk loot), no seats. Voxels keeps "@state" through spacing.
- Preview: /tmp/claude-0/boats/yard.py ids@state -> mk.sh -> m_boats; mesh.py needs --cars. Preview water/shadows make stripes look jagged (not the texture).
- Working boats (2026-10-29): boat_lobster 10.7 m, boat_trawler 18.9 m, boat_tug 23.6 m (refs/port/fishing-trawler, harbour-tug). Big hulls set per-spec hull_px/hull_top so the hull chart fits the atlas; `trap` makes 'cooler' a trap mesh; `steel=True` (trawler, tug) means burnt keeps the charred structure instead of burning to the waterline.
- Ports: Ships.java frame (bow +x, stern x=0, keel z=0): boat(model, length, sunk) -> p.vehicle(length/2, SEA, 0, -90, ...); Ships.trawler/lobster/sailboat/skiff/tug(sunk). Only the barge is still block-built.
- Refs: marina-boats photos queued with REFERENCES (2026-10-29); refine when they land.
