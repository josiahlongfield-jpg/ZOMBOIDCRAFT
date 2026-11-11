---
name: zomboid-open-plan-rule
description: Josiah wants big open floorplans in towers, not corridor mazes; seats must never face a wall; built 2026-10-29 as Floorplan.airy
metadata:
  type: feedback
  modified: 2026-10-29T08:45:23.378Z
---

Josiah 2026-10-29 08:29 (screenshot of a tower corridor with seats facing a room wall): "is there now way we can open these areas right up? have sofas pointing at a wall doesnt make sense. ideally we want nice big open floorplan".

**Why:** ring corridors with offices round the windows and rooms wrapped round the core read as cramped mazes, and seating facing a wall makes no sense.

**How to apply:** for public and work floors, use open plan: one big floor out to the glass, only a few glass-walled rooms, and only the essential rooms by the core. Bedrooms, flats, dorms, care-home rooms and separate firms keep walls. Never point a sofa or chair at a wall close up.

Built (playtest 55bdbc74f):
- `Floors.Fitter.airy(Type)` sets `Floorplan.airy`, which drops the corridor ring.
- `windows()` caps window rooms at 3-4 and makes them glass.
- `coreThen()` caps core rooms at the essentials and glasses the work rooms.
- `Floorplan.cut(..., cap)` stops a strip early.
- `Floorplan.place()` turns a seat round, or skips it, if a WALL, CORE or DOOR is 1-2 cells ahead.
- Glass room walls only go mid-run between plain WALL cells, which keeps the window check at 0.

Related: [[zomboid-floor-fitout]], [[zomboid-floor-purpose-rule]], [[zomboid-consistent-detail-rule]].

Refs ready 2026-10-29 20:06 for future floor work: refs/city/tower-interiors/{open-plan-offices, law-firm-floors, executive-offices, medical-dental, newsroom-callcentre, government-offices, hotel-rooms, hotel-amenities, cafeteria-gym}; more penthouse refs in refs/city/penthouse-crowns/{art-deco-crowns, observatory-domes, great-rooms, rooftop-pools-night, car-galleries, lounges-cellars, rooftop-terraces}. Thread paused after the request (Josiah 19:04).
