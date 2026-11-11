---
name: zomboid-ui-pz-pieces
description: UI thread's share of the PZ review (G menu every option, action queue, car parts view, level-up card, death card, Sandbox page); mock-ups artifact and status
metadata:
  type: project
  modified: 2026-10-31T03:57:07.185Z
---

Assigned 2026-10-31 03:46 from [[zomboid-pz-review]] (sections 16, 2, 18, 20), under [[zomboid-match-zomboid-rule]].

Mock-ups sent 2026-10-31: https://claude.ai/artifact/6zFda6ycGpfmpPiJGmcG2M (source in the UI thread scratchpad zui-mock/). GO from Josiah 2026-10-31 05:02 for everything except the moodle restyle (moodles stay). Build order: car figure (efb79586a, in VEHICLES' MechanicsScreen), Sandbox page, death card, level-up card, G menu + queue. NO PRESETS (Josiah 05:06; COMMANDS removed them). COMMANDS: all 269 settings in data/zomboidcraft/sandbox/zomboid.json, TEXT kind, coming CHOICE settings greyed, Map.* settings owned by UI; table /mnt/project-files/zomboid-mod/commands/world-settings.md.
- G menu: a Zomboid-style list (not a wheel) opened by G beside the green dot, every right-click option, greyed lines naming the missing skill/tool.
- Action queue: Zomboid's (no cap, walking/hits cancel all) plus a visible queue list (addition).
- Car parts view: Zomboid's Mechanics window parts, condition hidden below the part's Mechanics level, drawn as a detailed top-down x-ray car (every part its own shape; Josiah 04:51 asked for more detail, v2 done). VEHICLES owns the parts data and MechanicsScreen; offered to draw the figure in it.
- Moodles: Josiah 2026-10-31 04:51: moodles keep their CURRENT look, just cover every need (full Zomboid set). AGREED 2026-10-31, SURVIVAL owns MoodleHud in wave 1 (Zomboid stages, one Sick plate, every vanilla potion plate removed). UI stays off MoodleHud until it merges. SURVIVAL also regrouped kit/SkillsPage like Zomboid (Passive, Agility, Combat, Firearm, Crafting, Farming, Survivalist) with wheel scroll (+1 line in KitScreen.mouseScrolled); build the level-up lines on top of that version. Health page hooks: survival/Nutrition.
- Level-up card (addition), death card (Zomboid's survived time + kills, plus cause/place and map cross).
- Sandbox page: 12 Zomboid groups + ZomboidCraft group, no presets; confirmed rules shown as Fixed. SPLIT (coordinator suggestion 2026-10-31, proposed to COMMANDS): COMMANDS owns the settings data, presets, per-world store and /zc sandbox; UI only draws the page from COMMANDS' registry + set/sync payloads.
- Sandbox API (COMMANDS, branch claude/project-thread-wu4jl1, 5e827569f, 2026-10-31): package com.zomboidcraft.sandbox. Sandbox.groups()/options(group)/option(id); SandboxOption kind TOGGLE/NUMBER/CHOICE/TEXT/FIXED, coming(), owner, client, defaultValue(), check(raw). Sandbox.value(id). Host sends SandboxNet.Set(id,v); server answers Sync. /zc sandbox -> SandboxNet.Open -> replace client.sandbox.SandboxClient.opener with the screen opener (pause menu calls it too). New world: Sandbox.forNewWorld(overrides) before the integrated server starts.
Then follow [[zomboid-video-preview-rule]].

Status 2026-10-31 05:40: all built on claude/project-thread-j1d6bj (death card fix 9029f8ee6); clips in /mnt/project-files/zomboid-mod/ui/ sent, awaiting Go. VEHICLES' screen in playtest (3cf153419), nothing else blocks the merge.
- 2026-11-01 04:40 Josiah wants Zomboid's EXACT moodle icons ("so they fit the game properly"), superseding "keep the current look". Copied via folder access to /mnt/project-files/zomboid-mod/survival/pz-b42-data/ui/Moodles/{128,48} (36 icons + BGsolid/BGoutline) + Translate/EN/Moodles.json. Local-only: load from config/zomboidcraft on his PC, never repo/jar. MoodleHud is SURVIVAL's.
- 2026-11-01 04:40 Josiah: with Zomboid's icons, NO wording beside the moodles ("the images explain themself"). Built: client/hud/PzMoodles (key->icon table, tinted BGsolid per stage, reads config/zomboidcraft/moodles once; falls back to our sprites) + MoodleHud hides names unless Left Alt peek. Icons copied to his profile MEDIEVAL GLOOM (1)/config/zomboidcraft/moodles.
