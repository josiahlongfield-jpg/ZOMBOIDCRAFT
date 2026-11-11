---
name: zomboid-landscape-review-plan
description: LANDSCAPE's plan doc for auto shops, world stories, alarms, erosion, farm animals, substations (PZ review s8/9/12/13/14); ANSWERED 2026-10-31 04:32, auto shops first
metadata:
  type: project
  modified: 2026-10-31T04:20:57.464Z
---

Doc "Auto shops and the world systems: plan to discuss": https://claude.ai/artifact/FqHpQgo523mi17HVt3TBYq (sent 2026-10-31). Nothing gets built until Josiah answers. Follows [[zomboid-match-zomboid-rule]]; part of [[zomboid-pz-review]].

Proposed build order: auto shops (VEHICLES waiting on bays, spec in [[zomboid-highway-places]] / [[zomboid-car-maintenance-plan]]), substations (LOOT needs district map), farm pens (SURVIVAL), world stories, house alarms, erosion last.

Auto shop counts: hamlets 1-bay village garage; Seabright/Millbrook 3 bays + 1 hoist; Kingsbridge/Fairview 4 bays, 2 hoists, tyre centre; city 3 dealer service centres (6 bays, 4 hoists) + ~20 corner garages; scrapyard + truck depot; motor pools at Camp Black Pine and army base. Bay 4x7 inside, 4.5 headroom, door 3x3.

His 9 questions, with my picks: (1) all 7 gas stations get a bay; (2) city 3 centres + ~20 corner garages; (3) scrapyard by Foundry Row, truck depot off I-88 near Millbrook interchange; (4) Zomboid's story list first (~70, no adult ones) vs review's 40; (5) bell boxes only on alarmed houses; (6) no disarm vs Electrical cut; (7) cleared claimed base stops eroding; (8) all B42 animal kinds (no horses/goats in B42; horse ranches get cattle); (9) power never returns.

Substation districts proposed: 4 towns + 3 hamlets + ~6 city regions + Grayhaven Island on Harbor Point = ~14; reuse pw_ switchyard parts ([[zomboid-power-tech]]).

Owners (coordinator 2026-10-31 04:21): SURVIVAL thread owns B42 animals and their behaviour; LOOT owns the power grid shutoff/timing/generators. LANDSCAPE only builds pens/coops/troughs + places herds, and builds substations + the district map. Asked both threads (04:22) for entity ids, trough/coop hooks, herd spawn method, district map hand-over format and failure hook.




JOSIAH'S ANSWERS (2026-10-31 04:32, in the doc): (1) service bays only at the BIGGER gas stations; (2) city 3 service centres + ~20 corner garages; (3) scrapyard by Foundry Row, truck depot off I-88 near Millbrook interchange OK; (4) Zomboid's story list; (5) bell boxes on alarmed houses only; (6) high Electrical skill can cut an alarm short; (7) NO EROSION anywhere, not adding it yet; (8) farm animals START SMALL: chickens + one larger animal (I picked cows); (9) substations CAN be restored but not easily; power, like water, gets a sandbox toggle for always-on and an adjustable how-long-it-stays-on. RULE: the air-raid siren is ONLY for the world event, never a house alarm (house alarms need their own sound).




Cross-thread contracts (power, animals, hoist, loot, key board, alarm sounds, sandbox wiring): [[zomboid-landscape-contracts]].
BROADCAST HOOKS (2026-10-31 05:36, AUDIO's broadcasts plan 98TrFVLzMteUKAXQKM6v9Z): (a) coordinator gave LANDSCAPE the Camp Black Pine bunker + keypad door opening with code 417702: DISCUSS DESIGN WITH JOSIAH FIRST, tell AUDIO when live; no coded doors exist in the game yet. (b) Seabright's church is now "Our Lady of the Sea" (sign ourlady, BRICK) for Father Ruiz. (c) Shipyard dry dock has no office: told AUDIO to say pump house or main office. (d) LOOT asks: Kingsbridge water tower (towerNear in countySeat) gets a hand pump at its feet, a ladder chained partway up, a crate/locker on the tank deck; send LOOT positions/block ids, LOOT does lock, key, stock.
