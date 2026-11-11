---
name: zomboid-trailer-cinematic
description: ZomboidCraft demo trailer plan page and the /zc cinematic filming command (code locations, how it works)
metadata:
  type: project
  modified: 2026-10-27T23:40:56.843Z
---

Josiah 2026-10-27 23:30 asked for a demo trailer: shot list + script and a filming command (TRAILER thread, branch claude/project-thread-x18hye, merged to playtest 96d69ae8).

- Trailer page: https://claude.ai/artifact/D4e7E1SfHppWv6RfL5ZUBh (source /mnt/project-files/zomboid-mod/trailer/trailer-shot-list.html). 75 s, 13 shots, title cards, edit notes. He films with OBS/ShadowPlay + Replay Mod.
- /zc cinematic = src/main/java/com/zomboidcraft/Cinematic.java (server) + client/camera/CinematicView.java. VIEW attachment (targetOnly sync) bits CLEAN/SMOOTH; CLEAN wraps every VanillaHudElements id (reflection, not MISC_OVERLAYS, chat shows while ChatScreen open) plus our field_kit_body/field_kit_hud/skills/flight_hud, and Interactables (drawsOwn + dot() early return). Ghost = Sight.canSee false + abilities.invulnerable. Freeze = setNoAi + persistent cinematic_frozen attachment (thawed on load). WeatherDirector.hold stops the weather timer. Time hold runs both gamerule advance_time and doDaylightCycle silently. ZombieSpawner.spawnAhead for the horde.
- If a new HUD element is added, register it before CinematicView (it registers last in ZomboidCraftClient) and add its id to CinematicView.OURS.

Related: [[zomboid-commands-thread]], [[zomboid-playtest-build]].
