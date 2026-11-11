---
name: zomboid-carrier-direction
description: Josiah's aircraft-carrier ask for ZomboidCraft (true-scale carrier at its own naval wharf, true-size F-22/F-15-style jets, military interior and zombies)
metadata:
  type: feedback
  modified: 2026-10-26T07:00:52.205Z
---

Josiah 2026-10-26 02:57, landscape thread, after liking the cargo ships: "I'd like one of the ships to be changed into a massive military plane carrier ship that has F-22 raptors and F-15 Eagles on its runway. If needs be, make its own wharf as I want this to be very sizeable because the jets on the ships runway should be properly scaled also to look as though the player could fly them."
02:57: "The interior can have military zombies, barracks, gun rooms and all that, lots of detail. The exterior can have various weaponry as well (visual only)."

**Why:** he wants it imposing and believable at player scale, and jets that could later be flown.

**Status 2026-10-26 ~05:00:** built and merged to playtest (carrier, wharf, two jet models, soldier zombies). Tech: [[zomboid-carrier-tech]]. Flying the jets is a later feature.

**Bought jets (06:27-07:07):** he'll buy low-poly models to replace Corvid/Gyrfalcon. Candidate: CGTrader F-22 (https://www.cgtrader.com/3d-models/aircraft/military-aircraft/lockheed-martin-f-22-raptor-73212c3c-8419-4d38-81b5-443f1ca23646), Royalty Free (remix/commercial ok, no file redistribution, no AI training): I said it works; upload zip (GLB/FBX + textures), F-15 too. If it lacks a cockpit he may buy a cockpit model for me to merge in; or I model a simple one. On arrival: originals only in project files (never the public repo, [[zomboid-cars-plan]]), wreck.py/pack.py kind "jet" + floors, made-up names/markings, CREDITS.md. Not uploaded yet.
Uploaded 07:25 as /mnt/project-files/jets.zip: F-22 (OBJ, 12.5k tris, units x4.1 = metres), Apache AH-64D (OBJ, no .mtl, 4 PBR textures, metres), Su-27 "with interior" (SketchUp OBJ, 545k tris, metres). All three have cockpits. **Decided 07:49:** the F-22 replaces Corvid, the Su-27 replaces the F-15-style Gyrfalcon, and one Apache sits on a marked helipad on the carrier deck. Army bases unchanged.

**How to apply (plan posted 03:03, no objection yet):**
- True supercarrier size: about 333 x 77 flight deck, deck ~20 above water, island ~25 above the deck, on its own naval wharf (PORT variant "naval" or its own place) with fence, gate, cranes, tugs.
- Jets at 1:1 (about 19 long, 14 span, 5 tall): real 3D models like the cars (first pass built from scratch; bought low-poly models from Josiah will replace them), parked as props on deck and in the hangar; flying is a later feature.
- In game no real names (rule from [[zomboid-loot-rarity-direction]]): the jets look like the F-22 and F-15 but carry made-up names; the carrier gets a fictional name and hull number. Told him real carriers can't fly those jets; 03:05 he said he knows (F-35/F-18 are the carrier jets) but prefers the F-22 and F-15. Do not raise it again.
- Deck: catapults, arresting wires, elevators, tractors; visual-only weapons (CIWS-style guns, missile launchers, MG posts).
- Inside: hangar bay, barracks, armouries with rolled loot, mess, galley, sick bay, magazines, engine rooms, island bridge and flight control. Military zombies (soldier/sailor outfits) aboard.
- All custom blocks, lots of detail ([[zomboid-building-detail-direction]]). Previews standard + Photon rain before the jar.

Related: [[zomboid-ship-tech]], [[zomboid-cars-plan]], [[zomboid-bunkers-direction]].

**Detail pass asked (2026-10-26 08:36, after visiting both in game):** "they need a hell of a lot more detail. i like the bones, but the internals need way more detail. id like you to look up images of aircraft carrier external, internal rooms and rebuild the internals to suit that as well as cargo ship internal and external. will need to add lighting systems as well". Keep the hull shells and layout bones; rebuild carrier and cargo-ship interiors (and add exterior detail) from real reference rooms, with proper lighting (fixtures, night red, emergency lanterns, floods). Commons/most image hosts are blocked from the cloud container, so references come from text research.

**Power (decided 2026-10-26 08:54):** ships board with FAILING power: main lights dead or flickering, red night lights and battery battle lanterns lit, sick bay and engine control on an emergency generator. A generator you start yourself was the rejected/later option. Plan doc: https://claude.ai/artifact/F8eWMK5bzijxZNxbf7btLi (order: shared kit + lights, carrier passageways + hangar, mess/berthing, island/gallery/magazines/machinery, carrier exterior, cargo ship).
