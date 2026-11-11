---
name: zomboid-scope-zoom-direction
description: Josiah's aim-zoom rule (2026-11-02): unmagnified sights get no zoom; magnified scopes want picture-in-picture lens zoom with mild outside blur
metadata:
  type: feedback
  modified: 2026-11-02T20:00:12.831Z
---

Josiah 2026-11-02 19:57-19:59 (survivor arms thread): iron sights, red dots and anything without magnification "should stay standard": no FOV zoom when aiming (today stats.aimZoom 0.78-0.88 via Crosshair.java / AbstractClientPlayerMixin). Magnified scopes: he prefers picture-in-picture (world outside stays normal, only the lens zoomed) with a mild blur outside, used as cover to draw the outside cheaper.

**Why:** keep side vision and a natural look; FPS is the top priority.
**How to apply:** weapons work (passed to WEAPONS NEW thread). PiP with Iris+Photon is unproven; test with Photon on first. Fallback = cropped centre zoom inside the lens. First-person gun is drawn at fixed 70 deg, so the arms hold ([[zomboid-arms-posing-tool]]) is unaffected by FOV or zoom.
- 2026-11-02 20:06: irons/red dots 1x merged (AbstractClientPlayerMixin only narrows when Attachments.aimZoom > 0; Crosshair matches), playtest 4e889e4. PiP mock-up (A PiP 4x + blur, B crop fallback) at /mnt/project-files/zomboid-mod/brita/previews/scope-pip-mockup.mp4; awaiting his tweaks before a PC test.
- 20:58: posing tool v7 has "Scope lens size" (ADS only, 25-80%, default 50) -> db poses/current numbers.guns[id].ads.lens (fraction of screen height). Will move to numbers.guns[id].ads.<class>.lens when the arms thread adds sight classes. Magnification stays exact; lens size only changes how much field shows. 14-scope 3D test video rendering from scratchpad pip3d (page.html/run.js, three.js + sights.js reticles at 3x).
- 2026-11-03 02:22: Josiah says assault rifle (AssaultRifle2) and Colt1911 are set in the posing tool. Lens sizes then: AssaultRifle2 0.5 (default), Colt1911 0.52, AAC_Honey 0.8. Template rollout to other guns belongs to the survivor arms thread, not this one.
- 2026-11-04 05:51-05:57: under Photon the whole screen zoomed (my 7c8df1611 fallback). True PiP is impossible with Iris (world drawn once per frame). He chose "Build both behind a setting": look.scopeShaders "crop" (A, default: live 1x view, lens = HUD-time copy of the finished frame blown up, drawn with GUI_OPAQUE_TEXTURED_BACKGROUND via GuiGraphicsBlitInvoker because shader packs likely leave alpha 0, the suspected reason kept frames never showed) or "ring" (B: whole view zoomed, outside 0xEA dark). Key ";" (key.zomboidcraft.scope_style) swaps; U was taken by voice chat mute. Playtest 527c781, awaiting his PC test. Mock-ups: artifact WwzbitsjEnFqZqGJicJLZG.
