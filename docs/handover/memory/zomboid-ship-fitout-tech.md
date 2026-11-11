---
name: zomboid-ship-fitout-tech
description: Ship fit-out kit (tools/city/shipkit.py, fit_ blocks) used to rebuild carrier/cargo interiors from photos; orientation, lamp variants, wheel UV trick, Carrier.java helpers
metadata:
  type: project
  modified: 2026-10-26T10:13:13.042Z
---

Started 2026-10-26 (landscape thread) for Josiah's "a hell of a lot more detail" ask on the carrier and cargo ship. Plan doc: https://claude.ai/artifact/F8eWMK5bzijxZNxbf7btLi. Steps 1-5 shipped in playtest by 2026-10-26 11:20 (kit, passages, hangar, berthing, mess, galley, sick bay, bridge, Pri-Fly, machinery, engine control room, exterior: island catwalk on level 3, number bulbs, visors, searchlights, hull louvres/side doors, bow number at x0=246). Left: 3b wardroom/cabins and 6 cargo ship (waiting for photos). Photos: /mnt/project-files/zomboid-mod/refs/ships/ (see [[zomboid-photo-reference-rule]]).

**Kit**: tools/city/shipkit.py (prefix fit_, ~120 blocks), registered in photo_textures.py; run shipkit.py then gen_blocks.py. Painters at top, textures() seeds 8000+, blocks() below.
- `lamp(name, boxes, ..., glass, lit, dead, flick)` makes name_on / name / name_flicker. Consoles (fit_console, fit_overhead) use it with the screen as the glass.
- Facing models face north, back at z=16 (facing=X means the FRONT faces X). Consoles under a window face away from it. Axis runs along z; beams take the axis of the run they cross (beam(run,"x") for runs along x).
- Small cut-out discs (hand wheels): zero-thickness box plus an 8th item `FULL` (uv [0,0,16,16] per face), otherwise auto-UV crops the texture.
- fit_deckplate is a plain cube spec ({"tex": ...}).

**Exterior helpers**: hullFitting(x,y,s,block) sets a flat fitting on the plating where free; onSide() overwrites. The vessel_scupper mouths read as big brown squares from outboard (candidate to shrink).

**Carrier.java helpers**: free()/over() set only inside the hull and on air (NOT valid in the island: use p.setIfAir there); tube(axis, lit), red(), lantern(f), console(name, f, lit), beam(run, axis); passage(), overheads(), fit(); machinery() + controlRoom() (ECR booth over the throttle board, on the emergency generator so lit).

**Power rule** (Josiah 08:54): main lights dead or flickering, red night lights and battle lanterns lit, sick bay and engine control on the emergency generator.

**Gotcha**: a galley loop stepping the wrong way hung the preview build forever; jstack the SitePreview pid to find it.

Previews: scratch scripts (fitbuild.sh, fitcat.py, ship/pv.sh) live in the session scratchpad and vanish with the container. Sheets in /mnt/project-files/zomboid-mod/previews/ (carrier-fitout-1.jpg, carrier-vs-photos.jpg, carrier-living-spaces.jpg).

Related: [[zomboid-carrier-tech]], [[zomboid-ship-tech]].
