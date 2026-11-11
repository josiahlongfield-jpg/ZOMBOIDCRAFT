---
name: zomboid-survivor-lead-rule
description: RULE (Josiah 2026-11-01 08:06): the Minecraft player model must never drive the Zomboid survivor body; plus render-order facts for survivors on 26.3
metadata:
  type: feedback
  modified: 2026-11-01T08:08:46.582Z
---

Josiah 2026-11-01 08:06: "we need to fix and overwrite anywhere that the minecraft player model effects the zomboid models".

**Why:** the survivor's rifle idle and aim copied the Minecraft hold (her hands reached for where HoldPoses put the gun), which looked wrong.
**How to apply:** Zomboid's clips lead the pose. The vanilla arm follows her hand so held items draw there, never the other way. Long guns are laid from her shooting-hand grip toward her other hand, and the other hand takes the real foregrip (Survivors.after, commit bdc2c5c2d). CarHands (steering wheel) still reaches, because that's car geometry. Josiah 08:40: no sign of the Minecraft character model anywhere, and first-person arms must match the chosen sex. Done in playtest c4bccb17d: SurvivorArms draws her arms from her pack (all bones but the arms collapsed to the shoulders, IK to the grips) for RifleArms, CarArms (wheel, aircraft, mounted gun) and vanilla renderPlayerHand (FirstPersonSurvivorHandMixin). Nothing is drawn while her skin composes. Placement is untested in game.

Facts (26.3, checked by javap probe):
- LivingEntityRenderer.submit only queues the model. setupAnim runs at draw time (ModelFeatureRenderer.prepareModel), after the layers have submitted.
- LivingEntityRendererSurvivorMixin calls setupAnim at submit HEAD for survivor players. Without it the body was invisible.
- Part submits (submitModelPart) read the ModelPart state at draw time. The first-person rifle arms therefore use their own baked PlayerModel (combat/ArmModels).

Related: [[zomboid-survivor-clothes-tech]], [[zomboid-playtest-build]].

STATE 2026-11-02 07:35: branch claude/project-thread-asz16x at c0a0f1ed (CI green), NOT in playtest; awaiting Josiah's look at preview run 6 (additions\survivor-shots\f) and his "merge". In it: hands take Zomboid's clip grip turned onto the real gun (RifleView passes want.q()); palm middle measured from the hand mesh (SurvivorPack.fist, PZ hand bone sits back in the wrist); long gun's support hand keeps the clip wrist; no square belt on survivors (PZ Belt has no model); appearance preview faces forward. Preview test SurvivorShotsTest runs on his PC via Remote Control (zc-preview setup, pack watcher; pass with his mods + Photon on). Appearance-page Minecraft head never reproduced, even with Photon on. CAUSE FOUND 2026-11-04: SpawnPicker keeps a new survivor in SPECTATOR until they wake; vanilla draws a spectator as a bare head and skips layers, and Survivors skipped spectators. Fix 356c43361 (playtest-based, cherry-picked to branch 5435164dc): InventoryScreenMixin clears avatar.isSpectator for your own figure while a screen is open and notes it as Survivors.screenFigure; Survivors.hiddenSpectator lets only that figure draw. Also cleared isInvisible (d16edf159). Confirmed working by Josiah 2026-11-06. Then (his 11-06 ask) page figure stands still + drag-to-turn via SpinFigure.draw(...lift...) overload, playtest e05afa33f; my branch's own older drag code in AppearanceScreen is superseded (playtest's version is authoritative). Folder staging of the shots gets blocked after a Remote Control session ends; he can open them himself.
