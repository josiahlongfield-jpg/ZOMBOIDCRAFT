---
name: josiah-world-art-direction
description: Josiah's rules for how ZomboidCraft cities/buildings must look (battle-scarred, 3D depth, clutter), from his feedback
metadata:
  type: feedback
  modified: 2026-10-25T23:55:00.000Z
---

(2026-10-24, on the first photo-textured city preview) "This can't look like a fresh, brand new world that has zombies in it. It needs to look battle scarred, worn down and apocalyptic, little details matter." He said the walls looked "very flat and painted on" and the game would "feel like a bad render more than an actual professional job". He wants little accents like plants, debris and broken windows, and says to "use as many textures and assets as you need to obtain the rugged look of the reference images".

**Why:** the reference images (project files zomboid-mod/reference/art-direction-city.png, areas-concepts-a/b.png) are rugged, overgrown and ruined. Flat, uniform facades read as cheap.

**How to apply:**
- Every facade needs real 3D depth: recessed windows, raised frames, sills, ledges and cornices.
- Use texture variants (cracked, missing brick, soot, moss, stains), placed by noise so the same tile doesn't repeat.
- Add damage: holes, rebar, scorch marks. Most windows should be broken or boarded.
- Add overgrowth: hanging ivy, ledge moss, weeds, trees.
- Add street clutter: rubble, trash, paper, glass, puddles, fallen poles.
- Show close-ups before shipping. See [[josiah-preferences]] and [[zomboid-mod-status]].

(2026-10-25) Colour: "use a variety of colouring for the shop fronts and smaller buildings. The world should feel moody but the buildings can break it up a little." So keep the sky, streets and towers grey and wet, but give walk-ups and shopfronts faded, weathered colour (paint, brick, awnings, signs).

(2026-10-25) Light: he loved the cinema marquee's hanging pearlescent froglight. Under his Photon + Photonics it throws a lavender-pink glow onto the wet sidewalk in the rain. He said "we should make use of lighting like this more often and especially in the major cities". Hang these lights under awnings, canopies and entrances, and over doors, in towns and above all in downtown. Keep the count moderate, because each light costs Photonics frame time.

(2026-10-25 23:23, on the bunker core preview) "We will need to make these areas suit the world's moody aesthetic with lighting preferences and the like and roughing up the textures so they don't look as brand new or copy/pasted." Interiors (bunkers, garages, rooms) get the same rule: grime, wear and damage variants placed by noise, no identical repeating rooms, dim moody light (dead lamps, the odd red emergency glow), and previews shown in that dark light rather than flat preview light.

(2026-10-25 23:48) He liked the character thread's "cartoonish but believable, Borderlands-like" style (Soldier Player Body, https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L): each part its own box, dark ink border, lit line under the top edge, lighter tops, darker undersides, flat bold colours with grime. Landscape take: fits props, furniture, trims and add-ons; big walls/floors get no per-block ink edges (grid look).
(2026-10-26 00:02, bodies thread, applies to props) No black lines where pieces join; one flowing piece, thin outline only on outer edges and attachments. tools/city/ink.py does this (rim_mask + JOINS). Sheet: previews/inked-props-test.jpg; rollout awaiting his call.
(2026-10-26 00:12, landscape thread only, NOT a general rule) He wants the prop outline even thinner. ink.py now paints at 8 px per 1/16 (P=8, line half the old width); atlases about 1.4x bigger. Sheet: previews/inked-props-thinner.jpg.
(2026-10-26 01:18) His game will have permanent rain, so glossy, wet-looking roads under Photon are fine; do not make road/pavement textures rougher.
