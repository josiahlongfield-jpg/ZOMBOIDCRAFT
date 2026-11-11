---
name: zomboid-photon-lpv-haze
description: Josiah's constant haze 2026-11-06..08: SOLVED, Rainmeter Glass skin blur over the windowed game (F2 clean, F11 fixes); not mod, Photon or DH
metadata:
  type: project
  modified: 2026-11-06T12:21:55.790Z
---

2026-11-06 12:18 Josiah reported a constant haze in rain (not fog). It started after the patched pack photon_v1.4_zomboid.zip (08:00) and a new playtest jar. The "Get the Tarkov operator into the game" thread is looking at the jar side.

- Photon voxelizes only light block ids 32-79 (include/lighting/lpv/voxelization.glsl). Before the patch our lamps had no id, so they never entered the light volume (LPV); they only cast the vanilla amber lightmap. After the patch, about 600 of our lit blocks (windows, signs, lamps) feed the LPV.
- LPV_VL ("Light sources illuminate the fog") is on by default at LPV_VL_INTENSITY_OVERWORLD 1.00. With his rain fog (MIE rain 0.040), Bloomy Fog 0.6 and BLOCKLIGHT_I 1.25, the fog can glow citywide.
- His settings are in shaderpacks/photon_v1.4_zomboid.zip.txt (a copy of photon_v1.4.zip.txt). They have no LPV_VL keys, so they are at the defaults.
- Test given: switch to the plain photon_v1.4.zip. Proposed fix if that clears it: LPV_VL_INTENSITY_OVERWORLD about 0.30 in the zomboid pack's .txt (Iris writes the .txt; keep his other keys).

RESULT 12:46: the haze is the same on the plain photon_v1.4.zip, so the patch is CLEARED. Handed to the Tarkov operator thread, the only client render changes in today's jar. Suggested a shaders-off (K) test.

2026-11-08 01:01 still hazy on plain Photon. The mod's weather code (GroundFog, WeatherFx) is unchanged since 10-31. Diagnosis given: his Photon fog values versus Photon defaults. Bloomy Fog is ON 0.6 (default OFF), Mie rain 0.040 (default 0.030), Mie falloff start/half-life 20/20 (default 7/7), aerial boost 2 (default 1); all of this is permanent under endless rain. Told him to turn Bloomy Fog off first, and offered to edit the .txt (back it up first) when his PC is reachable (device bridge was down).

Related: [[zomboid-landmark-lighting-tech]], [[photon-shader-settings]].
2026-11-08 07:22 Josiah: the dayFog/haze/maxRainFx tests and Photon fog values did NOT fix it; it began when he installed Tarkov items. WEATHER suspect: Tarkov operator first-person render (384982dea, 11-06 11:29, local .zsk meshes) or a Tarkov item mesh with a see-through part over the camera. Tests sent: F1, empty hotbar slot, move operator*.zsk out of config/zomboidcraft/npc.
2026-11-08 07:30: F1 and empty-slot tests changed nothing. operator.zsk + operator_m4a1.zsk MOVED (not deleted) to C:\Dev\Zomboidcraft-Desktop\additions\haze-test\ for the test; move them back into ...\MEDIEVAL GLOOM (1)\config\zomboidcraft\npc\ after. Next test if still hazy: shaders off (K).
SOLVED 2026-11-08 08:39: NOT the mod/Photon/DH. Haze shows on screen but F2 screenshots are clean; F11 true fullscreen removes it. Cause: Rainmeter installed 2026-11-06 with the "Glass\Dashboard" frosted-glass skin (right screen edge) bleeding the blurred desktop over the windowed game. Fix: play fullscreen or exit Rainmeter / drop that skin. LESSON: for "constant haze nothing changes", ask for an F2 vs Windows screenshot and an F11 test first.
2026-11-08 08:42 SOLVED: a Rainmeter Glass skin on his desktop blurs over windowed Minecraft; fullscreen removes it. Not the mod, Photon or Tarkov. Drop haze leads.
