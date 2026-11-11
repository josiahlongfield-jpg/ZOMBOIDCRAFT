---
name: zomboid-commands-list
description: Where the ZomboidCraft command reference lives and the rule that every thread sends new commands to the COMMANDS thread
metadata:
  type: project
  modified: 2026-10-26T07:37:21.166Z
---

Josiah 2026-10-26: one thread, "COMMANDS", holds the only list of every ZomboidCraft command (/zc ... and others) with a brief description. Every thread that adds a command (or keybind / /zc config setting worth knowing) must message that thread with a one-line description.

The list is /mnt/project-files/zomboid-mod/commands/COMMANDS.md, grouped by area. Source of truth in code: ZcCommand.java (survival, roll, car, weather, config, city, /skills) and world/gen/CountyCommand.java (places, goto, bunker, bunkermap) on branch playtest.

**How to apply:** tell every new ZomboidCraft thread this rule, alongside [[zomboid-playtest-build]].

SWITCH 2026-11-09 (Josiah 07:51): Reach-world work (zombies, Halo/Tarkov guns, HUD, perf, world settings) moves to repo ENDLESS-NIGHT. Its commands list is /mnt/project-files/endless-night/commands/COMMANDS.md (started from the in-scope sections). New Reach commands go there; the zomboid-mod list stays as the frozen county record. Rules: /mnt/project-files/endless-night/SWITCH.md.
Josiah 2026-11-09 09:06: removals he marks apply ONLY to the Endless Night list; never delete anything from the ZomboidCraft list (county world keeps everything). EN list drops: Your character, /skills, Weapons, Vehicles, Zombies except horde, Trains, World and places, World events, World settings, companion keys. Commands there are /en, HUD /enhud.
