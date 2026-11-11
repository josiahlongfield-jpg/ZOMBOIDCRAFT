---
name: zomboid-blender-skills-ref
description: What we took from github.com/arjun988/blender-skills (Josiah's modelling reference, 2026-11-04) and how it changes modelling work
metadata:
  type: reference
  modified: 2026-11-04T21:54:07.006Z
---

Josiah shared https://github.com/arjun988/blender-skills on 2026-11-04, so we would model more accurately.

**What the repo is:** an MIT pack of 94 markdown "skills". There are no scripts, only notes and a Next.js site. Many files are thin; for example, apocalypse-worlds/wasteland.md is one line. It is built to drive Blender through BlenderMCP, an addon on localhost:9876. That only works on his PC, so from the cloud side it would need a Remote Control session. Clone it fresh if needed; it isn't kept anywhere.

**Methods adopted for modelling work** (see [[zomboid-photo-reference-rule]] and [[josiah-world-art-direction]]):
- Before building from photos, write a short reference analysis: silhouette, proportions, camera, light, 5-8 palette hexes, unique features and mood.
- Build in tiers:
  1. Silhouette blockout.
  2. Panel breaks and depth (ribs, recesses, chamfers).
  3. Greebles and hero features.
- After each tier, render a screenshot next to the reference, write a gap list (at most 5 items, worst first: camera, silhouette, boxy, materials, light, features, micro), fix, and repeat up to 3 times.
- Rate the result Match, Close or Miss per category, and pass at 8 of 10.
- Hard-surface bevel tiers: primary edges 5-20 mm, panel lines 1-3 mm, micro 0.5-1 mm. Panel lines go on construction seams.
- Characters:
  - Proportions are 7.5-8 heads (chin 1, nipples 2, navel 3, crotch 4, knee 6), with arms reaching mid-thigh.
  - Deformation loops go at the eyes (3), mouth (6-8), jaw line, shoulders and elbows/knees (3).
  - Keep poles away from mouth corners and the inner eye.
- Weights: automatic, normalise, then fix shoulders and hips. Test with T-pose, arms up, crouch, blink and jaw open. Limit to 4 influences, which is why three.js warns about more than 4.
- Budgets: standard NPC 20-40k tris, hero 50-100k. LOD steps 100/50/25/10%. The woman's 170k lod0 is over budget.
- Inverted-hull outline = Solidify -0.02, flipped normals, black material. This is what the OH_Outline meshes on fan models are; skip them when packing.
- Finish each model with a validation report covering scale and applied transforms, normals, tris against budget, material count, and screenshots from front, side, top and 3/4.

**2026-11-04 21:56, Josiah:**
- "take note of the discipline side". The reference analysis and the screenshot gap loop are now STANDING PRACTICE for all modelling.
- He'll install BlenderMCP when he's back on his PC. Setup:
  1. Install the ahujasid/blender-mcp addon.py in Blender and click Connect in the N panel. It listens on port 9876.
  2. Install uv.
  3. In his local Claude Code, run `claude mcp add blender cmd /c uvx --python 3.11 blender-mcp`.
- Use it through a Remote Control session.
