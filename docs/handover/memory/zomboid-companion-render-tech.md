---
name: zomboid-companion-render-tech
description: How skinned companions (.zsk) are drawn and what Iris/Photon on MC 26.3 do to custom geometry; the shader see-through bug hunt (2026-10-30)
metadata:
  type: project
  modified: 2026-10-30T10:30:17.622Z
---

Skinned companions (Rebecca, woman, Shion) are drawn by NpcSkinDraw via submitCustomGeometry, each triangle as a quad with its last corner repeated.

Facts checked on 26.3 (probe.yml javap + Iris branch 26.3 source):
- SubmitNodeCollection.submitCustomGeometry: RenderType.hasBlending() -> translucentCustomGeometry phase (Iris draws it AFTER the shader pack's deferred pass), else solid. entityCutout, entityCutoutCull and entitySolid all use ColorTargetState.DEFAULT (no blend), so they are solid phase. Models also honour forceSolidModelPhase; custom geometry does not.
- Iris maps ENTITY_CUTOUT and ENTITY_CUTOUT_CULL to ENTITIES_CUTOUT_DIFFUSE (gbuffers_entities).
- Iris (MixinBufferBuilder.fillExtendedData) REPLACES the normal of every QUADS vertex with the quad's face normal while rendering the level, so smooth normals are lost with shaders and a backwards-wound triangle shades wrong. TRIANGLES topology keeps smooth normals.
- Photon 1.4: gbuffer has no alpha; blend off for gbuffers_entities; on MC 26.3 gbuffers_entities_translucent is enabled.

Bug (2026-10-30): Josiah saw Rebecca see-through with Photon (background lighting pattern on her body), fine with shaders off, still a little with TAA off. Root cause NOT confirmed. Playtest 58489160e ships: triangles oriented to their normals at load (NpcSkin.Group.orient), drawn entityCutoutCull, sheets (flag both) get a reversed back copy; config look.companionDraw 0 new default, 1 old both-sides entityCutout, 2 small batches of 2048 tris, 3 entitySolid on non-cutout meshes. Result 10:58: NONE of modes 0-3 fixed Rebecca (so not winding, batching, cut-out or render type; mode-2 batches probably merged anyway via canConsolidateConsecutiveGeometry). Next: asked whether Master Chief (.znpc) and the woman are see-through too. Held mesh guns (same custom-geometry quad trick, entityCutoutCull) are solid.

Related: [[zomboid-npc-models]], [[zomboid-blender-tutorial]], [[zomboid-shion-declined]].
