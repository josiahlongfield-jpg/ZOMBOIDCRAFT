---
name: endless-night-no-minecraft-effects
description: Josiah 2026-11-11 11:49 RULE: remove every Minecraft effect (melee sounds, red hit flash, damage tilt, all MC art/anims); everything must be Reach. Plan doc + picks M1-M5
metadata:
  type: feedback
---
Josiah 2026-11-11 11:49 (PLAN thread): "remove any of the minecraft effects like melee sounds, the run flash when you damage an enemy, the damage tick on a player, any and all minecraft style art or animations. everything needs to be reach".
**Why:** Endless Night must feel like Halo Reach throughout. **How to apply:** no vanilla sound/particle/overlay/screen/anim; swap for Reach's own (from his MCC files) or switch off.
Plan doc: https://claude.ai/artifact/4XVCdmR8SnyBPfppKS4hWZ (Docs 1c8d1bba-0b81-46a3-8b66-cba9f69bbbda, body 493c94fe-6f6a); picks M1 safety-net mute of all minecraft: sounds/particles, M2 operators keep Tarkov sounds, M3 Reach death/respawn, M4 drop drown/burn/freeze, M5 Reach chat+scoreboard. Awaiting answers.
Audit facts (playtest e31161f): Hud.java already hides vanilla bars/hotbar/tooltip/effects; InfectedRenderer.java:124 still sets the red hurt overlay (OverlayTexture.pack(0, hurt?3:10)); no handling yet for bobHurt, DeathScreen, block outline, toasts, loading screens, player hurt/death sounds, attack sounds, footsteps per material. All sounds pass SoundEngineMixin (good place for the minecraft: mute).
Related: [[zomboid-no-vanilla-rule]], [[endless-night-switch]], [[endless-night-mcc-menus]].
