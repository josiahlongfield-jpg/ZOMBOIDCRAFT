---
name: endless-night-loadout-command
description: Josiah 2026-11-09 10:31 asked for a command to select weapons for Spartans and Tarkov operators; proposed /en loadout design, awaiting his scope pick
metadata:
  type: project
---
Ask (Josiah 10:31, weapons thread): "a new command to be able to select the weapons for both spartans and tarkov operatives".
Proposed 10:3x: /en loadout opens a two-tab picker. Operator tab = primary + sidearm via the UI thread's Gunsmith screen ([[endless-night-gunsmith]]; asked UI thread cmsg_...7gZePr for entry point / soldier.json). Spartan tab = primary, secondary, grenades from Reach weapons, which are NOT yet playable (models/FP clips/sounds pulled only, [[zomboid-halo-weapons]]). Cheats-only testing shortcut, because loadouts otherwise change only in menus or at base (Josiah 09:43).
Card options: (rec) command + start making Halo weapons playable, previews first; Tarkov-only command; base-only no cheat shortcut. Build nothing until he picks. New command must go on /mnt/project-files/endless-night/commands/COMMANDS.md via the "Commands list and world settings" thread ([[zomboid-commands-list]]).
UI thread 10:32: reuse com.endlessnight.client.hud.GunsmithScreen (branch claude/project-thread-j1d6bj, not in playtest yet): `Screen s = GunsmithScreen.open(parent, true); if (s != null) mc.gui.setScreen(s);` (null without art kit). Saves primary+sidearm to config/endlessnight/loadout.json. Server GunsmithNet refuses in-world changes unless GunsmithNet.atBase(player) (Forge bases thread hooks it); a cheats-on command would need an exception there.
- 10:33 Josiah picked "Command + start Halo weapons" (build picker w/ Tarkov now + make Reach weapons playable, previews first).
- UI thread 10:34: GunsmithNet.anywhere (Predicate<ServerPlayer>, default isCreative) lets in-world changes through; widen with .or(permission check) in my init.
