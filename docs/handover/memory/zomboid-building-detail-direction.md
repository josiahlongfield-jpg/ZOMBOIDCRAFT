---
name: zomboid-building-detail-direction
description: Josiah's 2026-10-25 detail directives: B42-level interiors and exteriors, custom furniture everywhere, no vanilla blocks, push toward realism in a Minecraft build style
metadata:
  type: feedback
---

(2026-10-25 13:14, thread "Build the ZomboidCraft landscape") Josiah asked for "very special care to the structures, not only externally but internally". Use 3D asset packs or block textures to replace the "basic look blocks". Interiors should be highly detailed and bigger if needed, with "unobstructed pathing". Exteriors should be "meticulously made" and "cinematic and terrifying", with "vast looting systems in every building (on theme)", taking inspiration from the latest Project Zomboid interiors.

**Why:** vanilla-looking stand-ins (barrel cupboards, cauldron toilets, stair sofas, black-terracotta TVs) break the feel. He wants the density of Zomboid B42 interiors, where every room is full and every container is searchable.

**How to apply:**
- Replace vanilla stand-ins with custom furniture blocks: detailed box models plus CC0 photo textures through the tools/city blocks.json pipeline. Every container (cupboard, fridge, wardrobe, desk, cabinet) is a searchable fitting.
- Loot follows Zomboid's room-and-container model: the room type and the container type pick the table, and stores stock on theme.
- Houses and large buildings can be bigger. Halls, doors and stairs must be clear to walk through, with furniture against the walls.
- Exteriors must be meticulous. Set a cinematic, terrifying mood with story rooms (barricades, bodies, fires).
- He okayed adding asset packs and textures. Mind the project-file storage limit; see [[zomboid-texture-library]].
- See [[josiah-world-art-direction]] and [[zomboid-landscape-direction]].

(2026-10-25 19:37) On the lived-in house previews: "They will make a great layout for the basic houses that we can use to fill in the suburbs." He also wants some mansions with a more "classy" interior and exterior design (later; not started). First: detailed exteriors for the basic house styles.

(2026-10-25 21:05) He approved the eight exterior examples (playtest 8083e3e): "those examples will look great when we mix it our textures and aesthetic". Treat them as the suburb base; later passes layer in more battle-scarred damage. Classy mansions still to come.

(2026-10-25 21:59) "Let's make sure we have plenty of detail too, I have no issue with not having any vanilla minecraft blocks so that we can really immerse the buildings, structures, bunkers etc. go all out". So:
- New structures (bunkers first) use custom blocks only.
- The existing generators use about 230 distinct vanilla ids. Iron bars, fences, barrels, planks, stone bricks and concrete are the most used.
- Swap those out for custom blocks over time, most visible first. Terrain (grass, dirt, water, trees) is a separate question.
(22:06) "Definitely don't shy away from building your own drawers, tables, various things to fill rooms and all that, or grab asset packs that will fit our aesthetic. The minecraft world can still be filled with more believable items." Make custom furniture and props freely, and use CC0 packs that fit.
(22:22) Josiah wants custom furniture, internals and detail "throughout the entire game": steer far from the basic block look, as close to realism as possible while keeping a Minecraft build style. He asked whether characters must stay Minecraft figures. I said no, and recommended realistic box-built bodies with hi-res skins and new animations rather than smooth meshes. 22:25 he chose a Zomboid-like look; see zomboid-characters-direction.md.

(2026-10-26 11:27, landscape thread) "once youve finished the aircraft carrier id like for you to focus on all the buildings of all types including city buildings, exterior and interior, cover everything". Plan doc: https://claude.ai/artifact/9DYoHXjFGEPZtwW4Tg4sef (about 60 types, state today, order). Proposed order: room planner + main street shops, town civic, downtown, roadside, suburbs (mansions, trailers), farm, military, port + cargo ship. Order card sent 11:50 (main street recommended). Building photos requested from the photo thread in that order (refs/town|city|roadside|houses|farm|military|port/<topic>/). Audit: shops/civic/offices/walk-ups sparse inside; Farmstead and Camp are the most vanilla.
