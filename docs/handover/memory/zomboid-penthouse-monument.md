---
name: zomboid-penthouse-monument
description: Josiah 2026-10-29 wants hotel penthouses to be "a substantial monument", OK to redesign hotel tower tops; crown options offered, refs requested
metadata:
  type: project
---

Josiah 2026-10-29 01:42: "Did you look at penthouse photos? I want it to be quite a substantial monument. Even if that means redesigning the top of the hotels".

**Why:** the first penthouse (one stepped-in floor + wraparound deck, playtest 6d45f05f8, see [[zomboid-floor-fitout]]) read as a nice flat, not a landmark. The refs used were thin (refs/city/rooftop-penthouse was mostly low setback buildings).

**How to apply:**
- Redesign hotel tops as a 4-6 storey crown, visible across the city and lit at night, with a 3+ storey penthouse inside (triple-height great room, grand stair, mezzanine, observatory).
- Offered on a card: Art Deco tiered crown (recommended), glass crown, copper pyramid + lantern, or mix per tower. Awaiting his pick.
- Asked REFERENCES thread for refs/city/penthouse-crowns/ (Chrysler, Woolworth, Pierre, Waldorf Towers, glass supertall penthouses, sky gardens). Build from those photos ([[zomboid-photo-reference-rule]]).
- Keep slim core, stair headroom rule ([[zomboid-access-rule]]), lift to the top ([[zomboid-lifts-direction]]).
- 01:45 Josiah: "incredible big millionaire penthouses ... each hotel should have a differently laid out one". Plans page with 7 layouts (Sky Mansion, Gallery Loft, Entertainer, Winter Garden, Collector, Spa Retreat, Velvet Room = strip-club themed per Josiah 01:50, non-explicit decor; each 3 levels, top levels step in for terraces): https://claude.ai/artifact/6WuwTNR9LnhUTxME3XKa5a (source scratchpad penthouse-plans.html). Awaiting his picks + crown style + refs.
- 01:52 Josiah: "extend the floor plan however you want, add layers and detail, make it intricate" - penthouses must NOT be basic rectangles: cantilevered glass boxes past the tower face, wings, cut corners, tiers, mezzanines all allowed.

- Josiah 2026-10-29 02:16: penthouses must match the LANDSCAPE thread's level of detail ("advanced detail"); pull as many reference photos as needed.

## Built 2026-10-29 (playtest eaf323c1d)
- world/city/Penthouse.java: hotels n>=12 floors (Floors.hotel sets t[n],t[n-1],t[n-2]=PENTHOUSE) and Penthouse.fits (w>=26,d>=22) else reverts to suites. Tower skips officeFloor/facade/roof above floorY(n-2) when triplex.
- 7 layouts as spec strings (LAYOUTS) on the 40x32 plan; -Dzc.penthouse=N forces one in PackPreview. Grand stair hall 5x8 (lanes A/B, lantern on roof); voids get rims with columns under; bridges carve glass paths if a landing is cut off; terrace doors (corner pieces with no door become planted beds).
- Floors.penthouseLevel + ph_* room kinds + Fit.dress() + finishFloor (marble border/inlay, rugs as FULL carpet blocks: cv_carpet_red is a thin decor layer and makes holes, use of_carpet_red).
- Crown: Chrysler sunburst tiers on core (clad steel/brass/stone by layout), eagles, of_finial, of_spire_a/b/c/tip.
- Preview helpers in /tmp/claude-0/pv: rs.sh (exterior), lv.sh (level cutaway), top.py; tex dir needs *_nrm_gl fallbacks. mu_gh_glass has no normal map -> use of_pool_glass.
- Renders: /mnt/project-files/zomboid-mod/previews/penthouse/v2/. Crown-style card still open; REFERENCES fetching penthouse-crowns photos.

Refs ready 2026-10-29: refs/city/penthouse-crowns/{landmark-crowns 16, glass-penthouse-interiors 8, sky-gardens-pools 7, grand-interiors 7}. Next detail pass uses them: Chrysler-style thin chevron bands instead of the plain stone tier bands, Woolworth verdigris copper roof with dormers and pinnacles for the dome layout, Marina Bay infinity edge for the pools.
